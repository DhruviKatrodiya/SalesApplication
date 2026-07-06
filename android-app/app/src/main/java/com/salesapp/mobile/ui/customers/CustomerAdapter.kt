package com.salesapp.mobile.ui.customers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.R
import com.salesapp.mobile.data.models.Customer
import com.salesapp.mobile.databinding.ItemCustomerBinding
import com.salesapp.mobile.ui.common.Chips

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
            b.tvMeta.text = c.routeName?.let { "Route: $it" } ?: "No route"
            Chips.active(b.tvStatus, c.isActive)

            b.btnDetails.setOnClickListener { actions.onDetails(c) }
            b.btnEdit.setOnClickListener { actions.onEdit(c) }
            if (c.isActive) {
                b.btnToggle.setImageResource(R.drawable.ic_delete)
                b.btnToggle.contentDescription = "Delete"
                b.btnToggle.setOnClickListener { actions.onDelete(c) }
            } else {
                b.btnToggle.setImageResource(R.drawable.ic_history)
                b.btnToggle.contentDescription = "Activate"
                b.btnToggle.setOnClickListener { actions.onActivate(c) }
            }
            b.root.setOnClickListener { actions.onDetails(c) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Customer>() {
            override fun areItemsTheSame(a: Customer, b: Customer) = a.id == b.id
            override fun areContentsTheSame(a: Customer, b: Customer) = a == b
        }
    }
}
