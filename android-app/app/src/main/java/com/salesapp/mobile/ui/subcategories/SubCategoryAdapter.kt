package com.salesapp.mobile.ui.subcategories

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.R
import com.salesapp.mobile.data.models.SubCategory
import com.salesapp.mobile.databinding.ItemSubcategoryBinding
import com.salesapp.mobile.ui.common.Chips

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
            b.tvMeta.text = "${s.itemCount} items"
            Chips.active(b.tvStatus, s.isActive)

            b.btnEdit.setOnClickListener { actions.onEdit(s) }
            if (s.isActive) {
                b.btnToggle.setImageResource(R.drawable.ic_delete)
                b.btnToggle.contentDescription = "Delete"
                b.btnToggle.setOnClickListener { actions.onDelete(s) }
            } else {
                b.btnToggle.setImageResource(R.drawable.ic_history)
                b.btnToggle.contentDescription = "Activate"
                b.btnToggle.setOnClickListener { actions.onActivate(s) }
            }
            b.root.setOnClickListener { actions.onEdit(s) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SubCategory>() {
            override fun areItemsTheSame(a: SubCategory, b: SubCategory) = a.id == b.id
            override fun areContentsTheSame(a: SubCategory, b: SubCategory) = a == b
        }
    }
}
