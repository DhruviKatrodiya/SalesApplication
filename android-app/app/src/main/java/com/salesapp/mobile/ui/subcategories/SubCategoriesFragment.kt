package com.salesapp.mobile.ui.subcategories

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.salesapp.mobile.R
import com.salesapp.mobile.data.models.Category
import com.salesapp.mobile.data.models.SubCategory
import com.salesapp.mobile.data.repo.CategoryRepository
import com.salesapp.mobile.data.repo.SubCategoryRepository
import com.salesapp.mobile.databinding.FragmentSubcategoriesBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SubCategoriesFragment : Fragment(R.layout.fragment_subcategories) {

    private var _b: FragmentSubcategoriesBinding? = null
    private val b get() = _b!!
    private val repo = SubCategoryRepository()
    private val categoryRepo = CategoryRepository()
    private lateinit var adapter: SubCategoryAdapter
    private var searchJob: Job? = null
    private var search: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentSubcategoriesBinding.bind(view)

        adapter = SubCategoryAdapter(
            SubCategoryActions(
                onEdit = { showEditor(it) },
                onDelete = { confirmDelete(it) },
                onActivate = { activate(it) },
            )
        )
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.swipe.setOnRefreshListener { load() }
        b.fab.setOnClickListener { showEditor(null) }

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
            val result = runCatching { repo.list(search = search, active = "all") }
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

    private fun showEditor(existing: SubCategory?) {
        // Sub-categories need a parent category, so load the active list first.
        lifecycleScope.launch {
            val cats = runCatching { categoryRepo.list(active = "active").items }.getOrElse {
                toast("Could not load categories: ${it.message}"); return@launch
            }
            if (cats.isEmpty()) { toast("Create a category first."); return@launch }
            renderEditor(existing, cats)
        }
    }

    private fun renderEditor(existing: SubCategory?, cats: List<Category>) {
        val view = layoutInflater.inflate(R.layout.dialog_subcategory, null)
        val ac = view.findViewById<MaterialAutoCompleteTextView>(R.id.acCategory)
        val etName = view.findViewById<TextInputEditText>(R.id.etName)
        val etDesc = view.findViewById<TextInputEditText>(R.id.etDescription)

        val names = cats.map { it.name }
        ac.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names))
        var selected: Category? = existing?.let { ex -> cats.firstOrNull { it.id == ex.categoryId } }
        selected?.let { ac.setText(it.name, false) }
        ac.setOnItemClickListener { _, _, pos, _ -> selected = cats[pos] }

        etName.setText(existing?.name)
        etDesc.setText(existing?.description)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "New sub-category" else "Edit sub-category")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val cat = selected
                val name = etName.text?.toString()?.trim().orEmpty()
                if (cat == null) { toast("Select a category."); return@setPositiveButton }
                if (name.isEmpty()) { toast("Name is required."); return@setPositiveButton }
                save(existing, cat.id, name, etDesc.text?.toString()?.trim())
            }
            .show()
    }

    private fun save(existing: SubCategory?, categoryId: Int, name: String, desc: String?) {
        lifecycleScope.launch {
            val result =
                if (existing == null) repo.create(categoryId, name, desc)
                else repo.update(existing.id, categoryId, name, desc).map { 0 }
            result.fold(
                onSuccess = { toast(if (existing == null) "Added." else "Updated."); load() },
                onFailure = { toast("Save failed: ${it.message}") },
            )
        }
    }

    private fun confirmDelete(s: SubCategory) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete sub-category")
            .setMessage("Deactivate \"${s.name}\"?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    runCatching { repo.deactivate(s.id) }
                        .onSuccess { toast("Deleted."); load() }
                        .onFailure { toast("Delete failed: ${it.message}") }
                }
            }
            .show()
    }

    private fun activate(s: SubCategory) {
        lifecycleScope.launch {
            runCatching { repo.activate(s.id) }
                .onSuccess { toast("Activated."); load() }
                .onFailure { toast("Activate failed: ${it.message}") }
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
