package com.salesapp.mobile.ui.dispatch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.data.models.Dispatch
import com.salesapp.mobile.databinding.ItemDispatchBinding

class DispatchActions(
    val onDelete: (Dispatch) -> Unit,
    val onActivate: (Dispatch) -> Unit,
)

class DispatchAdapter(private val actions: DispatchActions) :
    ListAdapter<Dispatch, DispatchAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDispatchBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemDispatchBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(d: Dispatch) {
            b.tvTruck.text = d.truckLabel + if (!d.isActive) "  •  Inactive" else ""
            b.tvDate.text = d.dispatchDate?.take(10) ?: ""
            b.tvItems.text = if (d.items.isEmpty()) "No items"
            else d.items.joinToString("  ·  ") { "${it.itemName} ×${it.quantity}" }
            b.btnMenu.setOnClickListener { showMenu(it, d) }
        }

        private fun showMenu(anchor: View, d: Dispatch) {
            PopupMenu(anchor.context, anchor).apply {
                if (d.isActive) menu.add("Delete") else menu.add("Activate")
                setOnMenuItemClickListener { m ->
                    when (m.title) { "Delete" -> actions.onDelete(d); "Activate" -> actions.onActivate(d) }
                    true
                }
                show()
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Dispatch>() {
            override fun areItemsTheSame(a: Dispatch, b: Dispatch) = a.id == b.id
            override fun areContentsTheSame(a: Dispatch, b: Dispatch) = a == b
        }
    }
}
