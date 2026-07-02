package com.salesapp.mobile.ui.inventory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.data.models.Item
import com.salesapp.mobile.databinding.ItemInventoryBinding

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
            val unit = i.unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
            val price = "₹%.2f".format(i.unitPrice)
            val inactive = if (i.isActive) "" else "  •  Inactive"
            b.tvMeta.text = "Stock: ${i.stockQuantity}$unit  •  Truck: ${i.dispatchStock}  •  $price$inactive"
            b.btnMenu.setOnClickListener { showMenu(it, i) }
            b.root.setOnClickListener { actions.onEdit(i) }
        }

        private fun showMenu(anchor: View, i: Item) {
            PopupMenu(anchor.context, anchor).apply {
                menu.add("Edit")
                menu.add("Price history")
                menu.add("Movements")
                if (i.isActive) menu.add("Delete") else menu.add("Activate")
                setOnMenuItemClickListener { m ->
                    when (m.title) {
                        "Edit" -> actions.onEdit(i)
                        "Price history" -> actions.onPriceHistory(i)
                        "Movements" -> actions.onMovements(i)
                        "Delete" -> actions.onDelete(i)
                        "Activate" -> actions.onActivate(i)
                    }
                    true
                }
                show()
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(a: Item, b: Item) = a.id == b.id
            override fun areContentsTheSame(a: Item, b: Item) = a == b
        }
    }
}
