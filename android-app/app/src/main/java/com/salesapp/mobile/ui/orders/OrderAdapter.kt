package com.salesapp.mobile.ui.orders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.data.models.Order
import com.salesapp.mobile.data.models.OrderSource
import com.salesapp.mobile.data.models.OrderStatus
import com.salesapp.mobile.databinding.ItemOrderBinding
import com.salesapp.mobile.ui.common.Chips

class OrderActions(
    val onEdit: (Order) -> Unit,
    val onItems: (Order) -> Unit,
    val onPayments: (Order) -> Unit,
    val onAddPayment: (Order) -> Unit,
    val onSettle: (Order) -> Unit,
    val onApplyAdvance: (Order) -> Unit,
    val onStatus: (Order) -> Unit,
    val onCancel: (Order) -> Unit,
    val onDelete: (Order) -> Unit,
    val onActivate: (Order) -> Unit,
)

class OrderAdapter(private val actions: OrderActions) :
    ListAdapter<Order, OrderAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemOrderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(o: Order) {
            b.tvOrderNo.text = o.orderNumber
            val src = if (o.source == OrderSource.Dispatch) "Dispatch${o.truckName?.let { " · $it" } ?: ""}" else "Inventory"
            b.tvCustomer.text = "${o.customerName}  ·  $src"
            Chips.orderStatus(b.tvStatus, o.status.label)
            Chips.paymentStatus(b.tvPayStatus, o.paymentStatus.label)
            b.tvAmounts.text = "Total ₹%.2f  ·  Due ₹%.2f".format(o.totalAmount, o.remainingAmount)
            b.btnPayments.setOnClickListener { actions.onPayments(o) }
            b.btnItems.setOnClickListener { actions.onItems(o) }
            b.btnMenu.setOnClickListener { showMenu(it, o) }
            b.root.setOnClickListener { actions.onItems(o) }
        }

        private fun showMenu(anchor: View, o: Order) {
            PopupMenu(anchor.context, anchor).apply {
                val cancelled = o.status == OrderStatus.Cancelled
                menu.add("Line items")
                menu.add("Payments")
                if (!cancelled) {
                    menu.add("Add payment")
                    menu.add("Settle")
                    menu.add("Apply advance")
                    menu.add("Change status")
                    menu.add("Edit")
                    menu.add("Cancel order")
                }
                if (o.isActive) menu.add("Delete") else menu.add("Activate")
                setOnMenuItemClickListener { m ->
                    when (m.title) {
                        "Line items" -> actions.onItems(o)
                        "Payments" -> actions.onPayments(o)
                        "Add payment" -> actions.onAddPayment(o)
                        "Settle" -> actions.onSettle(o)
                        "Apply advance" -> actions.onApplyAdvance(o)
                        "Change status" -> actions.onStatus(o)
                        "Edit" -> actions.onEdit(o)
                        "Cancel order" -> actions.onCancel(o)
                        "Delete" -> actions.onDelete(o)
                        "Activate" -> actions.onActivate(o)
                    }
                    true
                }
                show()
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Order>() {
            override fun areItemsTheSame(a: Order, b: Order) = a.id == b.id
            override fun areContentsTheSame(a: Order, b: Order) = a == b
        }
    }
}
