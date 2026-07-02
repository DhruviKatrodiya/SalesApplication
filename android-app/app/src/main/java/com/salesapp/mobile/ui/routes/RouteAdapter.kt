package com.salesapp.mobile.ui.routes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.data.models.DeliveryRoute
import com.salesapp.mobile.databinding.ItemRouteBinding

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
            val status = if (r.isActive) "" else "  •  Inactive"
            b.tvMeta.text = "${r.customerCount} customers$status"
            b.btnMenu.setOnClickListener { showMenu(it, r) }
            b.root.setOnClickListener { actions.onEdit(r) }
        }

        private fun showMenu(anchor: View, r: DeliveryRoute) {
            PopupMenu(anchor.context, anchor).apply {
                menu.add("Edit")
                if (r.isActive) menu.add("Delete") else menu.add("Activate")
                setOnMenuItemClickListener { m ->
                    when (m.title) {
                        "Edit" -> actions.onEdit(r); "Delete" -> actions.onDelete(r); "Activate" -> actions.onActivate(r)
                    }
                    true
                }
                show()
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DeliveryRoute>() {
            override fun areItemsTheSame(a: DeliveryRoute, b: DeliveryRoute) = a.id == b.id
            override fun areContentsTheSame(a: DeliveryRoute, b: DeliveryRoute) = a == b
        }
    }
}
