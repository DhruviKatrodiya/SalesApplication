package com.salesapp.mobile.ui.routes

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
import com.salesapp.mobile.data.models.DeliveryRoute
import com.salesapp.mobile.data.repo.RouteRepository
import com.salesapp.mobile.databinding.FragmentRoutesBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RoutesFragment : Fragment(R.layout.fragment_routes) {

    private var _b: FragmentRoutesBinding? = null
    private val b get() = _b!!
    private val repo = RouteRepository()
    private lateinit var adapter: RouteAdapter
    private var searchJob: Job? = null
    private var search: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentRoutesBinding.bind(view)
        adapter = RouteAdapter(RouteActions(
            onEdit = { showEditor(it) }, onDelete = { confirmDelete(it) }, onActivate = { activate(it) }))
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

    private fun showEditor(existing: DeliveryRoute?) {
        val view = layoutInflater.inflate(R.layout.dialog_category, null)  // reuses generic name+description dialog
        val etName = view.findViewById<TextInputEditText>(R.id.etName)
        val etDesc = view.findViewById<TextInputEditText>(R.id.etDescription)
        etName.setText(existing?.name); etDesc.setText(existing?.description)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "New route" else "Edit route")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) { toast("Name is required."); return@setPositiveButton }
                save(existing, name, etDesc.text?.toString()?.trim())
            }
            .show()
    }

    private fun save(existing: DeliveryRoute?, name: String, desc: String?) {
        lifecycleScope.launch {
            runCatching { if (existing == null) repo.create(name, desc) else repo.update(existing.id, name, desc) }
                .onSuccess { toast(if (existing == null) "Route added." else "Route updated."); load() }
                .onFailure { toast("Save failed: ${it.message}") }
        }
    }

    private fun confirmDelete(r: DeliveryRoute) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete route").setMessage("Deactivate \"${r.name}\"? Customers keep their assignment.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    runCatching { repo.deactivate(r.id) }.onSuccess { toast("Deleted."); load() }
                        .onFailure { toast("Delete failed: ${it.message}") }
                }
            }.show()
    }

    private fun activate(r: DeliveryRoute) {
        lifecycleScope.launch {
            runCatching { repo.activate(r.id) }.onSuccess { toast("Activated."); load() }
                .onFailure { toast("Activate failed: ${it.message}") }
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
