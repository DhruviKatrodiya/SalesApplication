package com.salesapp.mobile.ui.categories

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.R
import com.salesapp.mobile.databinding.ItemCategoryBinding
import com.salesapp.mobile.data.models.Category
import com.salesapp.mobile.ui.common.Chips

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
            b.tvMeta.text = "${c.subCategoryCount} sub-categories"
            Chips.active(b.tvStatus, c.isActive)

            b.btnEdit.setOnClickListener { actions.onEdit(c) }
            if (c.isActive) {
                b.btnToggle.setImageResource(R.drawable.ic_delete)
                b.btnToggle.contentDescription = "Delete"
                b.btnToggle.setOnClickListener { actions.onDelete(c) }
            } else {
                b.btnToggle.setImageResource(R.drawable.ic_history)
                b.btnToggle.contentDescription = "Activate"
                b.btnToggle.setOnClickListener { actions.onActivate(c) }
            }
            b.root.setOnClickListener { actions.onEdit(c) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(a: Category, b: Category) = a.id == b.id
            override fun areContentsTheSame(a: Category, b: Category) = a == b
        }
    }
}
