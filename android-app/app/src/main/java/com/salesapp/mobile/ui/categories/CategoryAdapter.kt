package com.salesapp.mobile.ui.categories

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.databinding.ItemCategoryBinding
import com.salesapp.mobile.data.models.Category

/** Actions raised from a row's overflow menu. */
class CategoryActions(
    val onEdit: (Category) -> Unit,
    val onDelete: (Category) -> Unit,
    val onActivate: (Category) -> Unit,
)

class CategoryAdapter(private val actions: CategoryActions) :
    ListAdapter<Category, CategoryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(c: Category) {
            b.tvName.text = c.name
            if (c.description.isNullOrBlank()) {
                b.tvDescription.visibility = View.GONE
            } else {
                b.tvDescription.visibility = View.VISIBLE
                b.tvDescription.text = c.description
            }
            val status = if (c.isActive) "" else "  •  Inactive"
            b.tvMeta.text = "${c.subCategoryCount} sub-categories$status"

            b.btnMenu.setOnClickListener { anchor -> showMenu(anchor, c) }
            b.root.setOnClickListener { actions.onEdit(c) }
        }

        private fun showMenu(anchor: View, c: Category) {
            PopupMenu(anchor.context, anchor).apply {
                menu.add("Edit")
                if (c.isActive) menu.add("Delete") else menu.add("Activate")
                setOnMenuItemClickListener { item ->
                    when (item.title) {
                        "Edit" -> actions.onEdit(c)
                        "Delete" -> actions.onDelete(c)
                        "Activate" -> actions.onActivate(c)
                    }
                    true
                }
                show()
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(a: Category, b: Category) = a.id == b.id
            override fun areContentsTheSame(a: Category, b: Category) = a == b
        }
    }
}
