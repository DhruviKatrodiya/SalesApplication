package com.salesapp.mobile.ui.categories

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
import com.salesapp.mobile.data.models.Category
import com.salesapp.mobile.data.repo.CategoryRepository
import com.salesapp.mobile.databinding.FragmentCategoriesBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CategoriesFragment : Fragment(R.layout.fragment_categories) {

    private var _b: FragmentCategoriesBinding? = null
    private val b get() = _b!!
    private val repo = CategoryRepository()
    private lateinit var adapter: CategoryAdapter
    private var searchJob: Job? = null
    private var search: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentCategoriesBinding.bind(view)

        adapter = CategoryAdapter(
            CategoryActions(
                onEdit = { showEditor(it) },
                onDelete = { confirmDelete(it) },
                onActivate = { activate(it) },
            )
        )
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.swipe.setOnRefreshListener { load() }
        b.fab.setOnClickListener { showEditor(null) }

        b.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { applySearch(); true } else false
        }
        // Debounced live search.
        b.etSearch.addTextChangedListener(
            afterTextChanged = {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch { delay(350); applySearch() }
            }
        )

        load()
    }

    private fun applySearch() {
        search = b.etSearch.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
        load()
    }

    private fun load() {
        b.progress.visibility = if (b.swipe.isRefreshing) View.GONE else View.VISIBLE
        lifecycleScope.launch {
            val result = runCatching { repo.list(search = search, active = "all", page = 1, pageSize = 500) }
            b.progress.visibility = View.GONE
            b.swipe.isRefreshing = false
            result.fold(
                onSuccess = { paged ->
                    adapter.submitList(paged.items)
                    b.tvEmpty.visibility = if (paged.items.isEmpty()) View.VISIBLE else View.GONE
                },
                onFailure = { toast("Load failed: ${it.message}") },
            )
        }
    }

    private fun showEditor(existing: Category?) {
        val view = layoutInflater.inflate(R.layout.dialog_category, null)
        val etName = view.findViewById<TextInputEditText>(R.id.etName)
        val etDesc = view.findViewById<TextInputEditText>(R.id.etDescription)
        etName.setText(existing?.name)
        etDesc.setText(existing?.description)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "New category" else "Edit category")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) { toast("Name is required."); return@setPositiveButton }
                val desc = etDesc.text?.toString()?.trim()
                save(existing, name, desc)
            }
            .show()
    }

    private fun save(existing: Category?, name: String, desc: String?) {
        lifecycleScope.launch {
            val result = runCatching {
                if (existing == null) repo.create(name, desc) else repo.update(existing.id, name, desc)
            }
            result.fold(
                onSuccess = { toast(if (existing == null) "Category added." else "Category updated."); load() },
                onFailure = { toast("Save failed: ${it.message}") },
            )
        }
    }

    private fun confirmDelete(c: Category) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete category")
            .setMessage("Deactivate \"${c.name}\"? It will be hidden but not permanently removed.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    runCatching { repo.deactivate(c.id) }
                        .onSuccess { toast("Deleted."); load() }
                        .onFailure { toast("Delete failed: ${it.message}") }
                }
            }
            .show()
    }

    private fun activate(c: Category) {
        lifecycleScope.launch {
            runCatching { repo.activate(c.id) }
                .onSuccess { toast("Activated."); load() }
                .onFailure { toast("Activate failed: ${it.message}") }
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
