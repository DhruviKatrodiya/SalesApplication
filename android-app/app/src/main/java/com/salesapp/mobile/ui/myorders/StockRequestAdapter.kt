package com.salesapp.mobile.ui.myorders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salesapp.mobile.data.models.StockRequest
import com.salesapp.mobile.data.models.StockRequestStatus
import com.salesapp.mobile.databinding.ItemStockRequestBinding
import com.salesapp.mobile.ui.common.Chips

class StockRequestActions(
    val onFulfill: (StockRequest) -> Unit,
    val onDone: (StockRequest) -> Unit,
    val onCancel: (StockRequest) -> Unit,
    val onEdit: (StockRequest) -> Unit,
    val onPayments: (StockRequest) -> Unit,
    val onAddPayment: (StockRequest) -> Unit,
    val onSettle: (StockRequest) -> Unit,
    val onApplyAdvance: (StockRequest) -> Unit,
    val onDelete: (StockRequest) -> Unit,
    val onActivate: (StockRequest) -> Unit,
)

class StockRequestAdapter(private val actions: StockRequestActions) :
    ListAdapter<StockRequest, StockRequestAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStockRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemStockRequestBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: StockRequest) {
            b.tvNumber.text = r.requestNumber
            Chips.requestStatus(b.tvStatus, if (r.isActive) r.status.label else "Inactive")
            Chips.paymentStatus(b.tvPayStatus, r.paymentStatus.label)
            b.tvItems.text = if (r.items.isEmpty()) "" else r.items.joinToString("  ·  ") { "${it.itemName} ×${it.quantity}" }
            b.tvAmounts.text = "Total ₹%.2f  ·  Due ₹%.2f".format(r.totalAmount, r.remainingAmount)
            b.btnPayments.setOnClickListener { actions.onPayments(r) }
            b.btnMenu.setOnClickListener { showMenu(it, r) }
        }

        private fun showMenu(anchor: View, r: StockRequest) {
            PopupMenu(anchor.context, anchor).apply {
                when (r.status) {
                    StockRequestStatus.Pending -> { menu.add("Fulfill"); menu.add("Edit"); menu.add("Cancel") }
                    StockRequestStatus.Fulfilled -> { menu.add("Mark done"); menu.add("Cancel") }
                    else -> {}
                }
                menu.add("Payments"); menu.add("Add payment"); menu.add("Settle"); menu.add("Apply advance")
                if (r.isActive) menu.add("Delete") else menu.add("Activate")
                setOnMenuItemClickListener { m ->
                    when (m.title) {
                        "Fulfill" -> actions.onFulfill(r)
                        "Mark done" -> actions.onDone(r)
                        "Cancel" -> actions.onCancel(r)
                        "Edit" -> actions.onEdit(r)
                        "Payments" -> actions.onPayments(r)
                        "Add payment" -> actions.onAddPayment(r)
                        "Settle" -> actions.onSettle(r)
                        "Apply advance" -> actions.onApplyAdvance(r)
                        "Delete" -> actions.onDelete(r)
                        "Activate" -> actions.onActivate(r)
                    }
                    true
                }
                show()
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<StockRequest>() {
            override fun areItemsTheSame(a: StockRequest, b: StockRequest) = a.id == b.id
            override fun areContentsTheSame(a: StockRequest, b: StockRequest) = a == b
        }
    }
}
