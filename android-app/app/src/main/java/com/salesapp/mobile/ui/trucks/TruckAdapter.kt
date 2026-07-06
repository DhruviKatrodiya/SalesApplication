package com.salesapp.mobile.ui.trucks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.R
import com.salesapp.mobile.data.models.Truck
import com.salesapp.mobile.databinding.ItemTruckBinding
import com.salesapp.mobile.ui.common.Chips

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
            b.tvMeta.text = "${t.itemCount} items  •  ${t.totalUnits} units"
            Chips.active(b.tvStatus, t.isActive)

            b.btnStock.setOnClickListener { actions.onStock(t) }
            b.btnEdit.setOnClickListener { actions.onEdit(t) }
            if (t.isActive) {
                b.btnToggle.setImageResource(R.drawable.ic_delete)
                b.btnToggle.contentDescription = "Delete"
                b.btnToggle.setOnClickListener { actions.onDelete(t) }
            } else {
                b.btnToggle.setImageResource(R.drawable.ic_history)
                b.btnToggle.contentDescription = "Activate"
                b.btnToggle.setOnClickListener { actions.onActivate(t) }
            }
            b.root.setOnClickListener { actions.onStock(t) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Truck>() {
            override fun areItemsTheSame(a: Truck, b: Truck) = a.id == b.id
            override fun areContentsTheSame(a: Truck, b: Truck) = a == b
        }
    }
}
