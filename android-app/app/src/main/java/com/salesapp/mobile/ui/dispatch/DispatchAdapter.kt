package com.salesapp.mobile.ui.dispatch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.R
import com.salesapp.mobile.data.models.Dispatch
import com.salesapp.mobile.databinding.ItemDispatchBinding
import com.salesapp.mobile.ui.common.Chips

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
            b.tvTruck.text = d.truckLabel
            b.tvDate.text = d.dispatchDate?.take(10) ?: ""
            b.tvItems.text = if (d.items.isEmpty()) "No items"
            else d.items.joinToString("  ·  ") { "${it.itemName} ×${it.quantity}" }
            Chips.active(b.tvStatus, d.isActive)
            if (d.isActive) {
                b.btnToggle.setImageResource(R.drawable.ic_delete)
                b.btnToggle.contentDescription = "Delete"
                b.btnToggle.setOnClickListener { actions.onDelete(d) }
            } else {
                b.btnToggle.setImageResource(R.drawable.ic_history)
                b.btnToggle.contentDescription = "Activate"
                b.btnToggle.setOnClickListener { actions.onActivate(d) }
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
