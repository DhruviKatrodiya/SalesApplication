package com.salesapp.mobile.ui.subcategories

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.data.models.SubCategory
import com.salesapp.mobile.databinding.ItemSubcategoryBinding

class SubCategoryActions(
    val onEdit: (SubCategory) -> Unit,
    val onDelete: (SubCategory) -> Unit,
    val onActivate: (SubCategory) -> Unit,
)

class SubCategoryAdapter(private val actions: SubCategoryActions) :
    ListAdapter<SubCategory, SubCategoryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSubcategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemSubcategoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(s: SubCategory) {
            b.tvName.text = s.name
            b.tvCategory.text = s.categoryName
            val status = if (s.isActive) "" else "  •  Inactive"
            b.tvMeta.text = "${s.itemCount} items$status"
            b.btnMenu.setOnClickListener { showMenu(it, s) }
            b.root.setOnClickListener { actions.onEdit(s) }
        }

        private fun showMenu(anchor: View, s: SubCategory) {
            PopupMenu(anchor.context, anchor).apply {
                menu.add("Edit")
                if (s.isActive) menu.add("Delete") else menu.add("Activate")
                setOnMenuItemClickListener { item ->
                    when (item.title) {
                        "Edit" -> actions.onEdit(s)
                        "Delete" -> actions.onDelete(s)
                        "Activate" -> actions.onActivate(s)
                    }
                    true
                }
                show()
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SubCategory>() {
            override fun areItemsTheSame(a: SubCategory, b: SubCategory) = a.id == b.id
            override fun areContentsTheSame(a: SubCategory, b: SubCategory) = a == b
        }
    }
}
