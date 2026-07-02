package com.salesapp.mobile.ui.orders

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.salesapp.mobile.R
import com.salesapp.mobile.data.models.Customer
import com.salesapp.mobile.data.models.Item
import com.salesapp.mobile.data.models.Order
import com.salesapp.mobile.data.models.OrderSource
import com.salesapp.mobile.data.models.OrderStatus
import com.salesapp.mobile.data.models.ReceivedStatus
import com.salesapp.mobile.data.models.Truck
import com.salesapp.mobile.data.repo.CustomerRepository
import com.salesapp.mobile.data.repo.ItemRepository
import com.salesapp.mobile.data.repo.OrderLine
import com.salesapp.mobile.data.repo.OrderRepository
import com.salesapp.mobile.data.repo.PaymentRepository
import com.salesapp.mobile.data.repo.TruckRepository
import com.salesapp.mobile.databinding.FragmentOrdersBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class OrdersFragment : Fragment(R.layout.fragment_orders) {

    private var _b: FragmentOrdersBinding? = null
    private val b get() = _b!!
    private val orders = OrderRepository()
    private val payments = PaymentRepository()
    private val customersRepo = CustomerRepository()
    private val trucksRepo = TruckRepository()
    private val itemsRepo = ItemRepository()

    private lateinit var adapter: OrderAdapter
    private var searchJob: Job? = null
    private var search: String? = null
    private var statusFilter: OrderStatus? = null

    private val statusOptions = listOf<OrderStatus?>(null) + OrderStatus.entries

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentOrdersBinding.bind(view)
        adapter = OrderAdapter(OrderActions(
            onEdit = { showEditor(it) },
            onItems = { showItems(it) },
            onPayments = { showPayments(it) },
            onAddPayment = { showAddPayment(it) },
            onSettle = { confirmSettle(it) },
            onApplyAdvance = { applyAdvance(it) },
            onStatus = { showStatus(it) },
            onCancel = { confirmCancel(it) },
            onDelete = { confirmDelete(it) },
            onActivate = { activate(it) },
        ))
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.swipe.setOnRefreshListener { load() }
        b.fab.setOnClickListener { showEditor(null) }

        val labels = statusOptions.map { it?.label ?: "All statuses" }
        b.acStatus.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
        b.acStatus.setText("All statuses", false)
        b.acStatus.setOnItemClickListener { _, _, pos, _ -> statusFilter = statusOptions[pos]; load() }

        b.etSearch.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) { applySearch(); true } else false
        }
        b.etSearch.addTextChangedListener(afterTextChanged = {
            searchJob?.cancel(); searchJob = lifecycleScope.launch { delay(350); applySearch() }
        })
        load()
    }

    private fun applySearch() {
        search = b.etSearch.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }; load()
    }

    private fun load() {
        b.progress.visibility = if (b.swipe.isRefreshing) View.GONE else View.VISIBLE
        lifecycleScope.launch {
            val result = runCatching {
                orders.list(orderNumber = search, customer = search, status = statusFilter, active = "all")
            }
            b.progress.visibility = View.GONE; b.swipe.isRefreshing = false
            result.fold(
                onSuccess = { adapter.submitList(it.items); b.tvEmpty.visibility = if (it.items.isEmpty()) View.VISIBLE else View.GONE },
                onFailure = { toast("Load failed: ${it.message}") },
            )
        }
    }

    // ---------- Create / edit ----------

    private class LineRow(val root: View, val ac: MaterialAutoCompleteTextView, val qty: TextInputEditText, val price: TextInputEditText, var itemId: Int?)

    private fun showEditor(existing: Order?) {
        lifecycleScope.launch {
            val custs = runCatching { customersRepo.list(active = "active").items }.getOrElse { toast("Load customers failed: ${it.message}"); return@launch }
            if (custs.isEmpty()) { toast("Add a customer first."); return@launch }
            val trucks = runCatching { trucksRepo.list(active = "active").items }.getOrDefault(emptyList())
            val items = runCatching { itemsRepo.list(active = "active").items }.getOrElse { toast("Load items failed: ${it.message}"); return@launch }
            if (items.isEmpty()) { toast("Add an item first."); return@launch }
            val existingLines = existing?.let { runCatching { orders.getItems(it.id) }.getOrDefault(emptyList()) } ?: emptyList()
            renderEditor(existing, custs, trucks, items, existingLines.map { Triple(it.itemId, it.quantity, it.unitPrice) })
        }
    }

    private fun renderEditor(
        existing: Order?, custs: List<Customer>, trucks: List<Truck>, items: List<Item>,
        initialLines: List<Triple<Int, Int, Double>>,
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_order, null)
        val acCustomer = view.findViewById<MaterialAutoCompleteTextView>(R.id.acCustomer)
        val acSource = view.findViewById<MaterialAutoCompleteTextView>(R.id.acSource)
        val tilTruck = view.findViewById<TextInputLayout>(R.id.tilTruck)
        val acTruck = view.findViewById<MaterialAutoCompleteTextView>(R.id.acTruck)
        val etDelivery = view.findViewById<TextInputEditText>(R.id.etDeliveryDate)
        val etNotes = view.findViewById<TextInputEditText>(R.id.etNotes)
        val container = view.findViewById<LinearLayout>(R.id.lineContainer)
        val tvTotal = view.findViewById<android.widget.TextView>(R.id.tvTotal)

        acCustomer.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, custs.map { it.name }))
        var selectedCustomer: Customer? = existing?.let { ex -> custs.firstOrNull { it.id == ex.customerId } }
        selectedCustomer?.let { acCustomer.setText(it.name, false) }
        acCustomer.setOnItemClickListener { _, _, pos, _ -> selectedCustomer = custs[pos] }

        val sources = listOf(OrderSource.Inventory, OrderSource.Dispatch)
        acSource.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, sources.map { it.label }))
        var selectedSource = existing?.source ?: OrderSource.Inventory
        acSource.setText(selectedSource.label, false)

        acTruck.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, trucks.map { it.name }))
        var selectedTruck: Truck? = existing?.let { ex -> trucks.firstOrNull { it.id == ex.truckId } }
        selectedTruck?.let { acTruck.setText(it.name, false) }
        acTruck.setOnItemClickListener { _, _, pos, _ -> selectedTruck = trucks[pos] }
        fun refreshTruckVisibility() { tilTruck.visibility = if (selectedSource == OrderSource.Dispatch) View.VISIBLE else View.GONE }
        refreshTruckVisibility()
        acSource.setOnItemClickListener { _, _, pos, _ -> selectedSource = sources[pos]; refreshTruckVisibility() }

        etDelivery.setText(existing?.deliveryDate?.take(10))
        etDelivery.setOnClickListener { pickDate { etDelivery.setText(it) } }
        etNotes.setText(existing?.notes)

        val rows = mutableListOf<LineRow>()
        val itemLabels = items.map { "${it.name} (stock ${it.stockQuantity})" }
        fun recomputeTotal() {
            val total = rows.sumOf { r ->
                val qty = r.qty.text?.toString()?.toIntOrNull() ?: 0
                val item = items.firstOrNull { it.id == r.itemId }
                val price = r.price.text?.toString()?.toDoubleOrNull() ?: item?.unitPrice ?: 0.0
                qty * price
            }
            tvTotal.text = "Total: ₹%.2f".format(total)
        }
        fun addRow(itemId: Int?, qty: Int?, price: Double?) {
            val row = LayoutInflater.from(requireContext()).inflate(R.layout.row_order_line, container, false)
            val ac = row.findViewById<MaterialAutoCompleteTextView>(R.id.acItem)
            val etQty = row.findViewById<TextInputEditText>(R.id.etQty)
            val etPrice = row.findViewById<TextInputEditText>(R.id.etPrice)
            ac.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, itemLabels))
            val lr = LineRow(row, ac, etQty, etPrice, itemId)
            itemId?.let { id -> items.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { ac.setText(itemLabels[it], false) } }
            qty?.let { etQty.setText(it.toString()) }
            price?.let { etPrice.setText(it.toString()) }
            ac.setOnItemClickListener { _, _, pos, _ ->
                lr.itemId = items[pos].id
                if (etPrice.text.isNullOrBlank()) etPrice.setText(items[pos].unitPrice.toString())
                recomputeTotal()
            }
            etQty.addTextChangedListener(afterTextChanged = { recomputeTotal() })
            etPrice.addTextChangedListener(afterTextChanged = { recomputeTotal() })
            row.findViewById<View>(R.id.btnRemove).setOnClickListener {
                container.removeView(row); rows.remove(lr); recomputeTotal()
            }
            rows.add(lr); container.addView(row)
        }
        if (initialLines.isEmpty()) addRow(null, null, null)
        else initialLines.forEach { addRow(it.first, it.second, it.third) }
        recomputeTotal()
        view.findViewById<View>(R.id.btnAddLine).setOnClickListener { addRow(null, null, null) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "New order" else "Edit ${existing.orderNumber}")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val cust = selectedCustomer
                if (cust == null) { toast("Select a customer."); return@setPositiveButton }
                if (selectedSource == OrderSource.Dispatch && selectedTruck == null) { toast("Select a truck."); return@setPositiveButton }
                val lines = rows.mapNotNull { r ->
                    val id = r.itemId ?: return@mapNotNull null
                    val q = r.qty.text?.toString()?.toIntOrNull() ?: return@mapNotNull null
                    if (q <= 0) return@mapNotNull null
                    OrderLine(id, q, r.price.text?.toString()?.toDoubleOrNull())
                }
                if (lines.isEmpty()) { toast("Add at least one valid item line."); return@setPositiveButton }
                saveOrder(existing, cust.id, selectedSource, selectedTruck?.id, etDelivery.text?.toString()?.trim(), etNotes.text?.toString()?.trim(), lines)
            }
            .show()
    }

    private fun saveOrder(existing: Order?, customerId: Int, source: OrderSource, truckId: Int?, delivery: String?, notes: String?, lines: List<OrderLine>) {
        lifecycleScope.launch {
            val result =
                if (existing == null) orders.create(customerId, source, truckId, null, delivery, notes, lines).map { Unit }
                else orders.update(existing.id, customerId, source, truckId, null, delivery, notes, null, lines)
            result.fold(
                onSuccess = { toast(if (existing == null) "Order created." else "Order updated."); load() },
                onFailure = { toast(it.message ?: "Save failed.") },
            )
        }
    }

    private fun pickDate(onPicked: (String) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            onPicked("%04d-%02d-%02d".format(y, m + 1, d))
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ---------- Line items / received status ----------

    private fun showItems(o: Order) {
        lifecycleScope.launch {
            val lines = runCatching { orders.getItems(o.id) }.getOrElse { toast("Load failed: ${it.message}"); return@launch }
            if (lines.isEmpty()) { toast("No line items."); return@launch }
            val labels = lines.map { "${it.itemName}  ×${it.quantity}  ₹%.2f  [${it.receivedStatus.label}]".format(it.lineTotal) }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("${o.orderNumber} — items")
                .setItems(labels) { _, which -> if (o.status != OrderStatus.Cancelled) changeReceived(o, lines[which].id) }
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun changeReceived(o: Order, orderItemId: Int) {
        val opts = ReceivedStatus.entries
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Received status")
            .setItems(opts.map { it.label }.toTypedArray()) { _, which ->
                lifecycleScope.launch {
                    orders.updateReceivedStatus(o.id, orderItemId, opts[which]).fold(
                        onSuccess = { toast("Updated.") },
                        onFailure = { toast(it.message ?: "Update failed.") },
                    )
                }
            }
            .show()
    }

    // ---------- Payments ----------

    private fun showPayments(o: Order) {
        lifecycleScope.launch {
            val list = runCatching { payments.byOrder(o.id) }.getOrElse { toast("Load failed: ${it.message}"); return@launch }
            val text = if (list.isEmpty()) "No payments recorded."
            else list.joinToString("\n") { p ->
                "₹%.2f  ·  ${p.method ?: "—"}${p.note?.let { " · $it" } ?: ""}".format(p.amount)
            }
            MaterialAlertDialogBuilder(requireContext()).setTitle("${o.orderNumber} — payments").setMessage(text).setPositiveButton("Close", null).show()
        }
    }

    private fun showAddPayment(o: Order) {
        val view = layoutInflater.inflate(R.layout.dialog_payment, null)
        view.findViewById<android.widget.TextView>(R.id.tvDue).text = "Due: ₹%.2f".format(o.remainingAmount)
        val etAmount = view.findViewById<TextInputEditText>(R.id.etAmount)
        val acMethod = view.findViewById<MaterialAutoCompleteTextView>(R.id.acMethod)
        val etNote = view.findViewById<TextInputEditText>(R.id.etNote)
        val methods = listOf("Cash", "UPI", "Bank", "Cheque", "Other")
        acMethod.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, methods))
        acMethod.setText("Cash", false)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add payment")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val amount = etAmount.text?.toString()?.toDoubleOrNull()
                if (amount == null || amount <= 0) { toast("Enter a valid amount."); return@setPositiveButton }
                lifecycleScope.launch {
                    payments.add(o.id, amount, acMethod.text?.toString(), etNote.text?.toString()?.trim()).fold(
                        onSuccess = { toast("Payment recorded."); load() },
                        onFailure = { toast(it.message ?: "Failed.") },
                    )
                }
            }
            .show()
    }

    private fun confirmSettle(o: Order) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Settle order")
            .setMessage("Record the remaining ₹%.2f as fully paid?".format(o.remainingAmount))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Settle") { _, _ ->
                lifecycleScope.launch {
                    payments.settle(o.id).fold(onSuccess = { toast("Settled."); load() }, onFailure = { toast(it.message ?: "Failed.") })
                }
            }.show()
    }

    private fun applyAdvance(o: Order) {
        lifecycleScope.launch {
            payments.applyAdvance(o.id, null).fold(
                onSuccess = { toast("Advance applied."); load() },
                onFailure = { toast(it.message ?: "Failed.") },
            )
        }
    }

    // ---------- Status / cancel / delete ----------

    private fun showStatus(o: Order) {
        val opts = OrderStatus.entries.filter { it != OrderStatus.Cancelled }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change status")
            .setItems(opts.map { it.label }.toTypedArray()) { _, which ->
                lifecycleScope.launch {
                    orders.updateStatus(o.id, opts[which]).fold(
                        onSuccess = { toast("Status updated."); load() },
                        onFailure = { toast(it.message ?: "Failed.") },
                    )
                }
            }.show()
    }

    private fun confirmCancel(o: Order) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancel order")
            .setMessage("Cancel ${o.orderNumber}? Stock is returned and any amount paid becomes the customer's advance.")
            .setNegativeButton("No", null)
            .setPositiveButton("Cancel order") { _, _ ->
                lifecycleScope.launch {
                    orders.cancel(o.id).fold(onSuccess = { toast("Order cancelled."); load() }, onFailure = { toast(it.message ?: "Failed.") })
                }
            }.show()
    }

    private fun confirmDelete(o: Order) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete order").setMessage("Hide ${o.orderNumber}? (soft delete)")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch { runCatching { orders.deactivate(o.id) }.onSuccess { toast("Deleted."); load() }.onFailure { toast(it.message ?: "Failed.") } }
            }.show()
    }

    private fun activate(o: Order) {
        lifecycleScope.launch { runCatching { orders.activate(o.id) }.onSuccess { toast("Activated."); load() }.onFailure { toast(it.message ?: "Failed.") } }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
