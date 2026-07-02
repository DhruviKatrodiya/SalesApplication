package com.salesapp.mobile.ui.inventory

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.salesapp.mobile.R
import com.salesapp.mobile.data.models.Item
import com.salesapp.mobile.data.models.SubCategory
import com.salesapp.mobile.data.repo.ItemRepository
import com.salesapp.mobile.data.repo.SubCategoryRepository
import com.salesapp.mobile.databinding.FragmentInventoryBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InventoryFragment : Fragment(R.layout.fragment_inventory) {

    private var _b: FragmentInventoryBinding? = null
    private val b get() = _b!!
    private val repo = ItemRepository()
    private val subRepo = SubCategoryRepository()
    private lateinit var adapter: InventoryAdapter
    private var searchJob: Job? = null
    private var search: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentInventoryBinding.bind(view)

        adapter = InventoryAdapter(
            InventoryActions(
                onEdit = { showEditor(it) },
                onDelete = { confirmDelete(it) },
                onActivate = { activate(it) },
                onPriceHistory = { showPriceHistory(it) },
                onMovements = { showMovements(it) },
            )
        )
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.swipe.setOnRefreshListener { load() }
        b.fab.setOnClickListener { showEditor(null) }
        b.cbLowStock.setOnCheckedChangeListener { _, _ -> load() }

        b.etSearch.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) { applySearch(); true } else false
        }
        b.etSearch.addTextChangedListener(afterTextChanged = {
            searchJob?.cancel()
            searchJob = lifecycleScope.launch { delay(350); applySearch() }
        })

        load()
    }

    private fun applySearch() {
        search = b.etSearch.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
        load()
    }

    private fun load() {
        b.progress.visibility = if (b.swipe.isRefreshing) View.GONE else View.VISIBLE
        lifecycleScope.launch {
            val result = runCatching {
                repo.list(item = search, lowStock = b.cbLowStock.isChecked, active = "all")
            }
            b.progress.visibility = View.GONE
            b.swipe.isRefreshing = false
            result.fold(
                onSuccess = {
                    adapter.submitList(it.items)
                    b.tvEmpty.visibility = if (it.items.isEmpty()) View.VISIBLE else View.GONE
                },
                onFailure = { toast("Load failed: ${it.message}") },
            )
        }
    }

    private fun showEditor(existing: Item?) {
        lifecycleScope.launch {
            val subs = runCatching { subRepo.list(active = "active").items }.getOrElse {
                toast("Could not load sub-categories: ${it.message}"); return@launch
            }
            if (subs.isEmpty()) { toast("Create a sub-category first."); return@launch }
            renderEditor(existing, subs)
        }
    }

    private fun renderEditor(existing: Item?, subs: List<SubCategory>) {
        val view = layoutInflater.inflate(R.layout.dialog_item, null)
        val ac = view.findViewById<MaterialAutoCompleteTextView>(R.id.acSubCategory)
        val etName = view.findViewById<TextInputEditText>(R.id.etName)
        val etSku = view.findViewById<TextInputEditText>(R.id.etSku)
        val etUnit = view.findViewById<TextInputEditText>(R.id.etUnit)
        val etStock = view.findViewById<TextInputEditText>(R.id.etStock)
        val etPrice = view.findViewById<TextInputEditText>(R.id.etPrice)

        val labels = subs.map { "${it.categoryName} › ${it.name}" }
        ac.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
        var selected: SubCategory? = existing?.let { ex -> subs.firstOrNull { it.id == ex.subCategoryId } }
        selected?.let { s -> ac.setText("${s.categoryName} › ${s.name}", false) }
        ac.setOnItemClickListener { _, _, pos, _ -> selected = subs[pos] }

        etName.setText(existing?.name)
        etSku.setText(existing?.sku)
        etUnit.setText(existing?.unit)
        etStock.setText(existing?.stockQuantity?.toString() ?: "0")
        etPrice.setText(existing?.unitPrice?.toString() ?: "0")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "New item" else "Edit item")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val sub = selected
                val name = etName.text?.toString()?.trim().orEmpty()
                if (sub == null) { toast("Select a sub-category."); return@setPositiveButton }
                if (name.isEmpty()) { toast("Name is required."); return@setPositiveButton }
                val stock = etStock.text?.toString()?.trim()?.toIntOrNull() ?: 0
                val price = etPrice.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
                save(
                    existing, sub.id, name,
                    etSku.text?.toString()?.trim(), etUnit.text?.toString()?.trim(), stock, price,
                )
            }
            .show()
    }

    private fun save(
        existing: Item?, subCategoryId: Int, name: String, sku: String?, unit: String?,
        stock: Int, price: Double,
    ) {
        lifecycleScope.launch {
            val result =
                if (existing == null) repo.create(subCategoryId, name, sku, unit, stock, price).map { Unit }
                else repo.update(existing.id, subCategoryId, name, sku, unit, stock, price)
            result.fold(
                onSuccess = { toast(if (existing == null) "Item added." else "Item updated."); load() },
                onFailure = { toast("Save failed: ${it.message}") },
            )
        }
    }

    private fun showPriceHistory(i: Item) {
        lifecycleScope.launch {
            val h = runCatching { repo.priceHistory(i.id) }.getOrNull()
            if (h == null) { toast("Could not load price history."); return@launch }
            val view = layoutInflater.inflate(R.layout.dialog_price_history, null)
            view.findViewById<TextView>(R.id.tvSummary).text = buildString {
                appendLine("Stock: ${h.stockQuantity}")
                appendLine("Stock value: ₹%.2f".format(h.stockValue))
                appendLine("Avg cost: ₹%.2f".format(h.avgCost))
                appendLine("Oldest price: ₹%.2f".format(h.oldestPrice))
                append("Latest price: ₹%.2f".format(h.latestPrice))
            }
            val container = view.findViewById<LinearLayout>(R.id.batchContainer)
            if (h.batches.isEmpty()) {
                container.addView(line("No batches recorded."))
            } else {
                h.batches.forEach { batch ->
                    val src = batch.sourceRequestNumber ?: "Opening"
                    container.addView(
                        line("• ₹%.2f  —  %d/%d left  (%s)".format(
                            batch.purchasePrice, batch.remaining, batch.initialQuantity, src))
                    )
                }
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("${i.name} — price history")
                .setView(view)
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun showMovements(i: Item) {
        lifecycleScope.launch {
            val paged = runCatching { repo.movements(i.id) }.getOrNull()
            if (paged == null) { toast("Could not load movements."); return@launch }
            val text = if (paged.items.isEmpty()) "No movements yet."
            else paged.items.joinToString("\n") { m ->
                val rev = if (m.reversed) " (reversed)" else ""
                "${m.type}  ${m.quantity} @ ₹%.2f  — ${m.refType}${m.source?.let { " · $it" } ?: ""}$rev".format(m.unitCost)
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("${i.name} — movements")
                .setMessage(text)
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun line(text: String) = TextView(requireContext()).apply {
        this.text = text
        setTextColor(resources.getColor(R.color.text_secondary, null))
        textSize = 13f
        setPadding(0, 6, 0, 6)
    }

    private fun confirmDelete(i: Item) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete item")
            .setMessage("Deactivate \"${i.name}\"?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    repo.deactivate(i.id).fold(
                        onSuccess = { toast("Deleted."); load() },
                        onFailure = { toast(it.message ?: "Delete failed.") },
                    )
                }
            }
            .show()
    }

    private fun activate(i: Item) {
        lifecycleScope.launch {
            runCatching { repo.activate(i.id) }
                .onSuccess { toast("Activated."); load() }
                .onFailure { toast("Activate failed: ${it.message}") }
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
