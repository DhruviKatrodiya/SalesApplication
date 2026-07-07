package com.salesapp.mobile.ui.dispatch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.salesapp.mobile.R
import com.salesapp.mobile.data.models.Dispatch
import com.salesapp.mobile.data.models.Item
import com.salesapp.mobile.data.repo.DispatchRepository
import com.salesapp.mobile.data.repo.ItemRepository
import com.salesapp.mobile.data.repo.TruckRepository
import com.salesapp.mobile.databinding.FragmentDispatchBinding
import kotlinx.coroutines.launch

class DispatchFragment : Fragment(R.layout.fragment_dispatch) {

    private var _b: FragmentDispatchBinding? = null
    private val b get() = _b!!
    private val repo = DispatchRepository()
    private val trucksRepo = TruckRepository()
    private val itemsRepo = ItemRepository()
    private lateinit var adapter: DispatchAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentDispatchBinding.bind(view)
        adapter = DispatchAdapter(DispatchActions(
            onEdit = { showEditDispatch(it) },
            onDelete = { confirmDelete(it) },
            onActivate = { activate(it) },
        ))
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.swipe.setOnRefreshListener { load() }
        b.fab.setOnClickListener { showNewDispatch() }
        load()
    }

    private fun load() {
        b.progress.visibility = if (b.swipe.isRefreshing) View.GONE else View.VISIBLE
        lifecycleScope.launch {
            val result = runCatching { repo.list(active = "all") }
            b.progress.visibility = View.GONE; b.swipe.isRefreshing = false
            result.fold(
                onSuccess = { adapter.submitList(it.items); b.tvEmpty.visibility = if (it.items.isEmpty()) View.VISIBLE else View.GONE },
                onFailure = { toast("Load failed: ${it.message}") },
            )
        }
    }

    private class Row(val ac: MaterialAutoCompleteTextView, val qty: TextInputEditText, var itemId: Int?)

    private fun showNewDispatch() = openDispatchDialog(null)
    private fun showEditDispatch(d: Dispatch) = openDispatchDialog(d)

    private fun openDispatchDialog(existing: Dispatch?) {
        lifecycleScope.launch {
            val items = runCatching { itemsRepo.list(active = "active").items }.getOrElse { toast("Load items failed: ${it.message}"); return@launch }
            if (items.isEmpty()) { toast("Add an item first."); return@launch }
            val trucks = runCatching { trucksRepo.list(active = "active").items.map { it.name } }.getOrDefault(emptyList())
            renderDispatchDialog(existing, items, trucks)
        }
    }

    private fun renderDispatchDialog(existing: Dispatch?, items: List<Item>, truckNames: List<String>) {
        val view = layoutInflater.inflate(R.layout.dialog_dispatch, null)
        val acTruck = view.findViewById<MaterialAutoCompleteTextView>(R.id.acTruck)
        val etNotes = view.findViewById<TextInputEditText>(R.id.etNotes)
        val container = view.findViewById<LinearLayout>(R.id.lineContainer)

        acTruck.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, truckNames))
        when {
            existing != null -> acTruck.setText(existing.truckLabel, false)
            truckNames.isNotEmpty() -> acTruck.setText(truckNames.first(), false)
        }
        etNotes.setText(existing?.notes)

        val itemLabels = items.map { "${it.name} (stock ${it.stockQuantity})" }
        val rows = mutableListOf<Row>()
        fun addRow(itemId: Int?, qty: Int?) {
            val row = LayoutInflater.from(requireContext()).inflate(R.layout.row_dispatch_line, container, false)
            val ac = row.findViewById<MaterialAutoCompleteTextView>(R.id.acItem)
            val etQty = row.findViewById<TextInputEditText>(R.id.etQty)
            ac.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, itemLabels))
            val r = Row(ac, etQty, itemId)
            itemId?.let { id -> items.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { ac.setText(itemLabels[it], false) } }
            qty?.let { etQty.setText(it.toString()) }
            ac.setOnItemClickListener { _, _, pos, _ -> r.itemId = items[pos].id }
            row.findViewById<View>(R.id.btnRemove).setOnClickListener { container.removeView(row); rows.remove(r) }
            rows.add(r); container.addView(row)
        }
        if (existing == null || existing.items.isEmpty()) addRow(null, null)
        else existing.items.forEach { addRow(it.itemId, it.quantity) }
        view.findViewById<View>(R.id.btnAddLine).setOnClickListener { addRow(null, null) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "New dispatch" else "Edit dispatch")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (existing == null) "Record" else "Save") { _, _ ->
                val lines = rows.mapNotNull { r ->
                    val id = r.itemId ?: return@mapNotNull null
                    val q = r.qty.text?.toString()?.toIntOrNull() ?: return@mapNotNull null
                    if (q <= 0) return@mapNotNull null
                    id to q
                }
                if (lines.isEmpty()) { toast("Add at least one item."); return@setPositiveButton }
                val truck = acTruck.text?.toString()?.trim()
                val notes = etNotes.text?.toString()?.trim()
                lifecycleScope.launch {
                    val result = if (existing == null) repo.create(truck, notes, lines).map { }
                    else repo.update(existing.id, truck, notes, lines)
                    result.fold(
                        onSuccess = { toast(if (existing == null) "Dispatch recorded." else "Dispatch updated. Stock adjusted."); load() },
                        onFailure = { toast(it.message ?: "Failed.") },
                    )
                }
            }
            .show()
    }

    private fun confirmDelete(d: Dispatch) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete dispatch")
            .setMessage("Reverse this dispatch? Its stock returns to the godown.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    repo.delete(d.id).fold(onSuccess = { toast("Deleted."); load() }, onFailure = { toast(it.message ?: "Failed.") })
                }
            }.show()
    }

    private fun activate(d: Dispatch) {
        lifecycleScope.launch {
            repo.activate(d.id).fold(onSuccess = { toast("Reactivated."); load() }, onFailure = { toast(it.message ?: "Failed.") })
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
