package com.salesapp.mobile.ui.trucks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.data.models.Truck
import com.salesapp.mobile.databinding.ItemTruckBinding

class TruckActions(
    val onEdit: (Truck) -> Unit,
    val onStock: (Truck) -> Unit,
    val onDelete: (Truck) -> Unit,
    val onActivate: (Truck) -> Unit,
)

class TruckAdapter(private val actions: TruckActions) :
    ListAdapter<Truck, TruckAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTruckBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemTruckBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(t: Truck) {
            b.tvName.text = t.name
            val status = if (t.isActive) "" else "  •  Inactive"
            b.tvMeta.text = "${t.itemCount} items  •  ${t.totalUnits} units$status"
            b.btnMenu.setOnClickListener { showMenu(it, t) }
            b.root.setOnClickListener { actions.onStock(t) }
        }

        private fun showMenu(anchor: View, t: Truck) {
            PopupMenu(anchor.context, anchor).apply {
                menu.add("View stock")
                menu.add("Edit")
                if (t.isActive) menu.add("Delete") else menu.add("Activate")
                setOnMenuItemClickListener { m ->
                    when (m.title) {
                        "View stock" -> actions.onStock(t)
                        "Edit" -> actions.onEdit(t)
                        "Delete" -> actions.onDelete(t)
                        "Activate" -> actions.onActivate(t)
                    }
                    true
                }
                show()
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Truck>() {
            override fun areItemsTheSame(a: Truck, b: Truck) = a.id == b.id
            override fun areContentsTheSame(a: Truck, b: Truck) = a == b
        }
    }
}
