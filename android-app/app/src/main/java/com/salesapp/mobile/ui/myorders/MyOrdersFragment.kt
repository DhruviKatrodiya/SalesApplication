package com.salesapp.mobile.ui.myorders

import android.os.Bundle
import android.view.LayoutInflater
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
import com.salesapp.mobile.data.models.StockRequest
import com.salesapp.mobile.data.repo.ItemRepository
import com.salesapp.mobile.data.repo.OrderLine
import com.salesapp.mobile.data.repo.StockRequestPaymentRepository
import com.salesapp.mobile.data.repo.StockRequestRepository
import com.salesapp.mobile.databinding.FragmentMyOrdersBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MyOrdersFragment : Fragment(R.layout.fragment_my_orders) {

    private var _b: FragmentMyOrdersBinding? = null
    private val b get() = _b!!
    private val repo = StockRequestRepository()
    private val payments = StockRequestPaymentRepository()
    private val itemsRepo = ItemRepository()
    private lateinit var adapter: StockRequestAdapter
    private var searchJob: Job? = null
    private var search: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentMyOrdersBinding.bind(view)
        adapter = StockRequestAdapter(StockRequestActions(
            onFulfill = { r -> confirm("Fulfill request", "Add its quantities to inventory?") { perform { repo.fulfill(r.id) } } },
            onDone = { r -> confirm("Mark done", "Close this fulfilled request?") { perform { repo.done(r.id) } } },
            onCancel = { r -> confirm("Cancel request", "Cancel ${r.requestNumber}?") { perform { repo.cancel(r.id) } } },
            onEdit = { showEditor(it) },
            onPayments = { showPayments(it) },
            onAddPayment = { showAddPayment(it) },
            onSettle = { r -> confirm("Settle", "Record the remaining ₹%.2f as paid?".format(r.remainingAmount)) { perform { payments.settle(r.id) } } },
            onApplyAdvance = { r -> lifecycleScope.launch { payments.applyAdvance(r.id, null).fold(onSuccess = { toast("Advance applied."); load() }, onFailure = { toast(it.message ?: "Failed.") }) } },
            onDelete = { r -> confirm("Delete", "Hide ${r.requestNumber}?") { performBool { repo.deactivate(r.id) } } },
            onActivate = { r -> lifecycleScope.launch { runCatching { repo.activate(r.id) }.onSuccess { toast("Activated."); load() }.onFailure { toast(it.message ?: "Failed.") } } },
        ))
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.swipe.setOnRefreshListener { load() }
        b.fab.setOnClickListener { showEditor(null) }
        b.etSearch.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEARCH) { applySearch(); true } else false }
        b.etSearch.addTextChangedListener(afterTextChanged = {
            searchJob?.cancel(); searchJob = lifecycleScope.launch { delay(350); applySearch() }
        })
        load()
    }

    private fun applySearch() { search = b.etSearch.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }; load() }

    private fun load() {
        b.progress.visibility = if (b.swipe.isRefreshing) View.GONE else View.VISIBLE
        lifecycleScope.launch {
            val result = runCatching { repo.list(requestNumber = search, item = search, active = "all") }
            b.progress.visibility = View.GONE; b.swipe.isRefreshing = false
            result.fold(
                onSuccess = { adapter.submitList(it.items); b.tvEmpty.visibility = if (it.items.isEmpty()) View.VISIBLE else View.GONE },
                onFailure = { toast("Load failed: ${it.message}") },
            )
        }
    }

    // ---- create / edit ----

    private class LineRow(val ac: MaterialAutoCompleteTextView, val qty: TextInputEditText, val price: TextInputEditText, var itemId: Int?)

    private fun showEditor(existing: StockRequest?) {
        if (existing != null && existing.status != com.salesapp.mobile.data.models.StockRequestStatus.Pending) {
            toast("Only pending requests can be edited."); return
        }
        lifecycleScope.launch {
            val items = runCatching { itemsRepo.list(active = "active").items }.getOrElse { toast("Load items failed: ${it.message}"); return@launch }
            if (items.isEmpty()) { toast("Add an item first."); return@launch }
            renderEditor(existing, items)
        }
    }

    private fun renderEditor(existing: StockRequest?, items: List<Item>) {
        val view = layoutInflater.inflate(R.layout.dialog_stock_request, null)
        val etNotes = view.findViewById<TextInputEditText>(R.id.etNotes)
        val container = view.findViewById<LinearLayout>(R.id.lineContainer)
        val tvTotal = view.findViewById<TextView>(R.id.tvTotal)
        etNotes.setText(existing?.notes)

        val itemLabels = items.map { "${it.name} (stock ${it.stockQuantity})" }
        val rows = mutableListOf<LineRow>()
        fun recompute() {
            val total = rows.sumOf { r ->
                val q = r.qty.text?.toString()?.toIntOrNull() ?: 0
                val item = items.firstOrNull { it.id == r.itemId }
                val price = r.price.text?.toString()?.toDoubleOrNull() ?: item?.unitPrice ?: 0.0
                q * price
            }
            tvTotal.text = "Total: ₹%.2f".format(total)
        }
        fun addRow(itemId: Int?, qty: Int?, price: Double?) {
            val row = LayoutInflater.from(requireContext()).inflate(R.layout.row_order_line, container, false)
            val ac = row.findViewById<MaterialAutoCompleteTextView>(R.id.acItem)
            val etQty = row.findViewById<TextInputEditText>(R.id.etQty)
            val etPrice = row.findViewById<TextInputEditText>(R.id.etPrice)
            ac.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, itemLabels))
            val lr = LineRow(ac, etQty, etPrice, itemId)
            itemId?.let { id -> items.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { ac.setText(itemLabels[it], false) } }
            qty?.let { etQty.setText(it.toString()) }
            price?.let { etPrice.setText(it.toString()) }
            ac.setOnItemClickListener { _, _, pos, _ -> lr.itemId = items[pos].id; if (etPrice.text.isNullOrBlank()) etPrice.setText(items[pos].unitPrice.toString()); recompute() }
            etQty.addTextChangedListener(afterTextChanged = { recompute() })
            etPrice.addTextChangedListener(afterTextChanged = { recompute() })
            row.findViewById<View>(R.id.btnRemove).setOnClickListener { container.removeView(row); rows.remove(lr); recompute() }
            rows.add(lr); container.addView(row)
        }
        val initial = existing?.items ?: emptyList()
        if (initial.isEmpty()) addRow(null, null, null) else initial.forEach { addRow(it.itemId, it.quantity, it.unitPrice) }
        recompute()
        view.findViewById<View>(R.id.btnAddLine).setOnClickListener { addRow(null, null, null) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "New request" else "Edit ${existing.requestNumber}")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val lines = rows.mapNotNull { r ->
                    val id = r.itemId ?: return@mapNotNull null
                    val q = r.qty.text?.toString()?.toIntOrNull() ?: return@mapNotNull null
                    if (q <= 0) return@mapNotNull null
                    OrderLine(id, q, r.price.text?.toString()?.toDoubleOrNull())
                }
                if (lines.isEmpty()) { toast("Add at least one valid line."); return@setPositiveButton }
                lifecycleScope.launch {
                    val result = if (existing == null) repo.create(etNotes.text?.toString()?.trim(), lines).map { Unit }
                    else repo.update(existing.id, etNotes.text?.toString()?.trim(), lines)
                    result.fold(onSuccess = { toast("Saved."); load() }, onFailure = { toast(it.message ?: "Save failed.") })
                }
            }
            .show()
    }

    // ---- payments ----

    private fun showPayments(r: StockRequest) {
        lifecycleScope.launch {
            val list = runCatching { payments.byRequest(r.id) }.getOrElse { toast("Load failed: ${it.message}"); return@launch }
            if (list.isEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("${r.requestNumber} — payments").setMessage("No payments recorded yet.").setPositiveButton("Close", null).show()
                return@launch
            }
            val labels = list.map { p ->
                "₹%.2f · ${p.method ?: "—"} · ${p.paymentDate?.take(10) ?: ""}${p.note?.let { " · $it" } ?: ""}".format(p.amount)
            }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("${r.requestNumber} — payments")
                .setItems(labels) { _, which ->
                    val p = list[which]
                    if (p.method == "Advance" || p.method == "AdvanceTransfer") {
                        toast("Advance entries can't be deleted.")
                    } else {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Delete payment")
                            .setMessage("Remove this ₹%.2f payment?".format(p.amount))
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete") { _, _ ->
                                lifecycleScope.launch {
                                    payments.deletePayment(p.id).fold(
                                        onSuccess = { toast("Payment removed."); load() },
                                        onFailure = { toast(it.message ?: "Delete failed.") },
                                    )
                                }
                            }.show()
                    }
                }
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun showAddPayment(r: StockRequest) {
        val view = layoutInflater.inflate(R.layout.dialog_payment, null)
        view.findViewById<TextView>(R.id.tvDue).text = "Due: ₹%.2f".format(r.remainingAmount)
        val etAmount = view.findViewById<TextInputEditText>(R.id.etAmount)
        val acMethod = view.findViewById<MaterialAutoCompleteTextView>(R.id.acMethod)
        val etNote = view.findViewById<TextInputEditText>(R.id.etNote)
        acMethod.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, listOf("Cash", "UPI", "Bank", "Cheque", "Other")))
        acMethod.setText("Cash", false)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add payment").setView(view).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val amount = etAmount.text?.toString()?.toDoubleOrNull()
                if (amount == null || amount <= 0) { toast("Enter a valid amount."); return@setPositiveButton }
                lifecycleScope.launch {
                    payments.add(r.id, amount, acMethod.text?.toString(), etNote.text?.toString()?.trim())
                        .fold(onSuccess = { toast("Payment recorded."); load() }, onFailure = { toast(it.message ?: "Failed.") })
                }
            }.show()
    }

    // ---- helpers ----

    private inline fun confirm(title: String, message: String, crossinline op: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title).setMessage(message)
            .setNegativeButton("No", null)
            .setPositiveButton("Yes") { _, _ -> op() }
            .show()
    }

    private inline fun perform(crossinline block: suspend () -> Result<*>) {
        lifecycleScope.launch {
            block().fold(onSuccess = { toast("Done."); load() }, onFailure = { toast(it.message ?: "Failed.") })
        }
    }

    private inline fun performBool(crossinline block: suspend () -> Boolean) {
        lifecycleScope.launch { runCatching { block() }.onSuccess { toast("Done."); load() }.onFailure { toast(it.message ?: "Failed.") } }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
