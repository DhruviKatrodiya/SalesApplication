package com.salesapp.mobile.ui.customers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.data.models.Customer
import com.salesapp.mobile.databinding.ItemCustomerBinding

class CustomerActions(
    val onEdit: (Customer) -> Unit,
    val onDetails: (Customer) -> Unit,
    val onDelete: (Customer) -> Unit,
    val onActivate: (Customer) -> Unit,
)

class CustomerAdapter(private val actions: CustomerActions) :
    ListAdapter<Customer, CustomerAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCustomerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemCustomerBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(c: Customer) {
            b.tvName.text = c.name
            b.tvPhone.text = c.phone ?: "—"
            val route = c.routeName?.let { "Route: $it" } ?: "No route"
            val status = if (c.isActive) "" else "  •  Inactive"
            b.tvMeta.text = "$route$status"
            b.btnMenu.setOnClickListener { showMenu(it, c) }
            b.root.setOnClickListener { actions.onDetails(c) }
        }

        private fun showMenu(anchor: View, c: Customer) {
            PopupMenu(anchor.context, anchor).apply {
                menu.add("Details")
                menu.add("Edit")
                if (c.isActive) menu.add("Delete") else menu.add("Activate")
                setOnMenuItemClickListener { m ->
                    when (m.title) {
                        "Details" -> actions.onDetails(c)
                        "Edit" -> actions.onEdit(c)
                        "Delete" -> actions.onDelete(c)
                        "Activate" -> actions.onActivate(c)
                    }
                    true
                }
                show()
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Customer>() {
            override fun areItemsTheSame(a: Customer, b: Customer) = a.id == b.id
            override fun areContentsTheSame(a: Customer, b: Customer) = a == b
        }
    }
}
