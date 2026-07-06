package com.salesapp.mobile.ui.inventory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.R
import com.salesapp.mobile.data.models.Item
import com.salesapp.mobile.databinding.ItemInventoryBinding
import com.salesapp.mobile.ui.common.Chips

class InventoryActions(
    val onEdit: (Item) -> Unit,
    val onDelete: (Item) -> Unit,
    val onActivate: (Item) -> Unit,
    val onPriceHistory: (Item) -> Unit,
    val onMovements: (Item) -> Unit,
)

class InventoryAdapter(private val actions: InventoryActions) :
    ListAdapter<Item, InventoryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemInventoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemInventoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(i: Item) {
            b.tvName.text = i.name
            b.tvPath.text = "${i.categoryName} › ${i.subCategoryName}"
            b.tvPrice.text = "₹%.2f".format(i.unitPrice)
            b.tvUnit.text = i.unit?.takeIf { it.isNotBlank() }?.let { "Unit: $it" } ?: ""

            pill(b.tvStock, i.stockQuantity, i.stockQuantity <= LOW_STOCK)
            pill(b.tvTruck, i.dispatchStock, i.dispatchStock <= 0)
            Chips.active(b.tvStatus, i.isActive)

            b.btnHistory.setOnClickListener { actions.onPriceHistory(i) }
            b.btnMovements.setOnClickListener { actions.onMovements(i) }
            b.btnEdit.setOnClickListener { actions.onEdit(i) }
            if (i.isActive) {
                b.btnToggle.setImageResource(R.drawable.ic_delete)
                b.btnToggle.contentDescription = "Delete"
                b.btnToggle.setOnClickListener { actions.onDelete(i) }
            } else {
                b.btnToggle.setImageResource(R.drawable.ic_history)
                b.btnToggle.contentDescription = "Activate"
                b.btnToggle.setOnClickListener { actions.onActivate(i) }
            }
            b.root.setOnClickListener { actions.onEdit(i) }
        }

        private fun pill(tv: android.widget.TextView, value: Int, low: Boolean) {
            tv.text = value.toString()
            tv.setBackgroundResource(if (low) R.drawable.pill_red else R.drawable.pill_green)
            tv.setTextColor(ContextCompat.getColor(tv.context,
                if (low) R.color.chip_danger_fg else R.color.chip_success_fg))
        }
    }

    companion object {
        private const val LOW_STOCK = 10
        private val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(a: Item, b: Item) = a.id == b.id
            override fun areContentsTheSame(a: Item, b: Item) = a == b
        }
    }
}
