package com.salesapp.mobile.ui.trucks

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.salesapp.mobile.R
import com.salesapp.mobile.data.models.Truck
import com.salesapp.mobile.data.repo.TruckRepository
import com.salesapp.mobile.databinding.FragmentTrucksBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TrucksFragment : Fragment(R.layout.fragment_trucks) {

    private var _b: FragmentTrucksBinding? = null
    private val b get() = _b!!
    private val repo = TruckRepository()
    private lateinit var adapter: TruckAdapter
    private var searchJob: Job? = null
    private var search: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentTrucksBinding.bind(view)
        adapter = TruckAdapter(TruckActions(
            onEdit = { showEditor(it) },
            onStock = { showStock(it) },
            onDelete = { confirmDelete(it) },
            onActivate = { activate(it) },
        ))
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.swipe.setOnRefreshListener { load() }
        b.fab.setOnClickListener { showEditor(null) }
        b.etSearch.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) { applySearch(); true } else false
        }
        b.etSearch.addTextChangedListener(afterTextChanged = {
            searchJob?.cancel(); searchJob = lifecycleScope.launch { delay(350); applySearch() }
        })
        load()
    }

    private fun applySearch() {
        search = b.etSearch.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }; load()
    }

    private fun load() {
        b.progress.visibility = if (b.swipe.isRefreshing) View.GONE else View.VISIBLE
        lifecycleScope.launch {
            val result = runCatching { repo.list(search = search, active = "all") }
            b.progress.visibility = View.GONE; b.swipe.isRefreshing = false
            result.fold(
                onSuccess = { adapter.submitList(it.items); b.tvEmpty.visibility = if (it.items.isEmpty()) View.VISIBLE else View.GONE },
                onFailure = { toast("Load failed: ${it.message}") },
            )
        }
    }

    private fun showEditor(existing: Truck?) {
        val view = layoutInflater.inflate(R.layout.dialog_truck, null)
        val etName = view.findViewById<TextInputEditText>(R.id.etName)
        etName.setText(existing?.name)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "New truck" else "Edit truck")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) { toast("Name is required."); return@setPositiveButton }
                save(existing, name)
            }
            .show()
    }

    private fun save(existing: Truck?, name: String) {
        lifecycleScope.launch {
            val result =
                if (existing == null) repo.create(name).map { Unit } else repo.update(existing.id, name)
            result.fold(
                onSuccess = { toast(if (existing == null) "Truck added." else "Truck updated."); load() },
                onFailure = { toast(it.message ?: "Save failed.") },
            )
        }
    }

    private fun showStock(t: Truck) {
        lifecycleScope.launch {
            val stock = runCatching { repo.stock(t.id) }.getOrNull()
            if (stock == null) { toast("Could not load stock."); return@launch }
            val text = if (stock.isEmpty()) "No stock loaded on this truck."
            else stock.joinToString("\n") { s ->
                val unit = s.unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                "• ${s.name}:  ${s.quantity}$unit  @ ₹%.2f".format(s.unitPrice)
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("${t.name} — stock")
                .setMessage(text)
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun confirmDelete(t: Truck) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete truck").setMessage("Deactivate \"${t.name}\"?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    repo.deactivate(t.id).fold(
                        onSuccess = { toast("Deleted."); load() },
                        onFailure = { toast(it.message ?: "Delete failed.") },
                    )
                }
            }.show()
    }

    private fun activate(t: Truck) {
        lifecycleScope.launch {
            runCatching { repo.activate(t.id) }.onSuccess { toast("Activated."); load() }
                .onFailure { toast("Activate failed: ${it.message}") }
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
