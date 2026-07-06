package com.salesapp.mobile.ui.routes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.R
import com.salesapp.mobile.data.models.DeliveryRoute
import com.salesapp.mobile.databinding.ItemRouteBinding
import com.salesapp.mobile.ui.common.Chips

class RouteActions(
    val onEdit: (DeliveryRoute) -> Unit,
    val onDelete: (DeliveryRoute) -> Unit,
    val onActivate: (DeliveryRoute) -> Unit,
)

class RouteAdapter(private val actions: RouteActions) :
    ListAdapter<DeliveryRoute, RouteAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemRouteBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: DeliveryRoute) {
            b.tvName.text = r.name
            if (r.description.isNullOrBlank()) b.tvDescription.visibility = View.GONE
            else { b.tvDescription.visibility = View.VISIBLE; b.tvDescription.text = r.description }
            b.tvMeta.text = "${r.customerCount} customers"
            Chips.active(b.tvStatus, r.isActive)

            b.btnEdit.setOnClickListener { actions.onEdit(r) }
            if (r.isActive) {
                b.btnToggle.setImageResource(R.drawable.ic_delete)
                b.btnToggle.contentDescription = "Delete"
                b.btnToggle.setOnClickListener { actions.onDelete(r) }
            } else {
                b.btnToggle.setImageResource(R.drawable.ic_history)
                b.btnToggle.contentDescription = "Activate"
                b.btnToggle.setOnClickListener { actions.onActivate(r) }
            }
            b.root.setOnClickListener { actions.onEdit(r) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DeliveryRoute>() {
            override fun areItemsTheSame(a: DeliveryRoute, b: DeliveryRoute) = a.id == b.id
            override fun areContentsTheSame(a: DeliveryRoute, b: DeliveryRoute) = a == b
        }
    }
}
