package com.salesapp.mobile.ui.customers

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.salesapp.mobile.R
import com.salesapp.mobile.data.models.Customer
import com.salesapp.mobile.data.models.CustomerSearchResult
import com.salesapp.mobile.data.models.DeliveryRoute
import com.salesapp.mobile.data.repo.CustomerRepository
import com.salesapp.mobile.data.repo.RouteRepository
import com.salesapp.mobile.databinding.FragmentCustomersBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CustomersFragment : Fragment(R.layout.fragment_customers) {

    private var _b: FragmentCustomersBinding? = null
    private val b get() = _b!!
    private val repo = CustomerRepository()
    private val routeRepo = RouteRepository()
    private lateinit var adapter: CustomerAdapter
    private var searchJob: Job? = null
    private var search: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentCustomersBinding.bind(view)
        adapter = CustomerAdapter(CustomerActions(
            onEdit = { showEditor(it) },
            onDetails = { showDetails(it) },
            onDelete = { confirmDelete(it) },
            onActivate = { activate(it) },
        ))
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.swipe.setOnRefreshListener { load() }
        b.fab.setOnClickListener { showEditor(null) }
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
            val result = runCatching { repo.list(name = search, active = "all") }
            b.progress.visibility = View.GONE; b.swipe.isRefreshing = false
            result.fold(
                onSuccess = { adapter.submitList(it.items); b.tvEmpty.visibility = if (it.items.isEmpty()) View.VISIBLE else View.GONE },
                onFailure = { toast("Load failed: ${it.message}") },
            )
        }
    }

    private fun showEditor(existing: Customer?) {
        lifecycleScope.launch {
            val routes = runCatching { routeRepo.list(active = "active").items }.getOrElse {
                toast("Could not load routes: ${it.message}"); emptyList()
            }
            renderEditor(existing, routes)
        }
    }

    private fun renderEditor(existing: Customer?, routes: List<DeliveryRoute>) {
        val view = layoutInflater.inflate(R.layout.dialog_customer, null)
        val etName = view.findViewById<TextInputEditText>(R.id.etName)
        val etPhone = view.findViewById<TextInputEditText>(R.id.etPhone)
        val etEmail = view.findViewById<TextInputEditText>(R.id.etEmail)
        val etAddress = view.findViewById<TextInputEditText>(R.id.etAddress)
        val acRoute = view.findViewById<MaterialAutoCompleteTextView>(R.id.acRoute)

        val noRoute = "— None —"
        val labels = listOf(noRoute) + routes.map { it.name }
        acRoute.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
        var selectedRoute: DeliveryRoute? = existing?.let { ex -> routes.firstOrNull { it.id == ex.routeId } }
        acRoute.setText(selectedRoute?.name ?: noRoute, false)
        acRoute.setOnItemClickListener { _, _, pos, _ -> selectedRoute = if (pos == 0) null else routes[pos - 1] }

        etName.setText(existing?.name)
        etPhone.setText(existing?.phone)
        etEmail.setText(existing?.email)
        etAddress.setText(existing?.address)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "New customer" else "Edit customer")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) { toast("Name is required."); return@setPositiveButton }
                save(
                    existing, name,
                    etPhone.text?.toString()?.trim(), etEmail.text?.toString()?.trim(),
                    etAddress.text?.toString()?.trim(), selectedRoute?.id,
                )
            }
            .show()
    }

    private fun save(existing: Customer?, name: String, phone: String?, email: String?, address: String?, routeId: Int?) {
        lifecycleScope.launch {
            val result =
                if (existing == null) repo.create(name, phone, email, address, routeId).map { Unit }
                else repo.update(existing.id, name, phone, email, address, routeId)
            result.fold(
                onSuccess = { toast(if (existing == null) "Customer added." else "Customer updated."); load() },
                onFailure = { toast("Save failed: ${it.message}") },
            )
        }
    }

    private fun showDetails(c: Customer) {
        lifecycleScope.launch {
            val d = runCatching { repo.details(c.id) }.getOrNull()
            if (d == null) { toast("Could not load details."); return@launch }
            renderDetails(d)
        }
    }

    private fun renderDetails(d: CustomerSearchResult) {
        val view = layoutInflater.inflate(R.layout.dialog_customer_details, null)
        val c = d.customer
        view.findViewById<TextView>(R.id.tvContact).text = buildString {
            c.phone?.let { appendLine("Phone: $it") }
            c.email?.let { appendLine("Email: $it") }
            c.address?.let { appendLine("Address: $it") }
            c.routeName?.let { append("Route: $it") }
        }.ifBlank { "No contact details." }

        view.findViewById<TextView>(R.id.tvTotals).text = buildString {
            appendLine("Status: ${d.overallPaymentStatus}")
            appendLine("Total ordered: ₹%.2f".format(d.totalOrdered))
            appendLine("Total paid: ₹%.2f".format(d.totalPaid))
            appendLine("Outstanding: ₹%.2f".format(d.totalRemaining))
            if (d.advanceBalance > 0) appendLine("Advance balance: ₹%.2f".format(d.advanceBalance))
            append("Pending: ${d.pendingOrders}   Delivered: ${d.deliveredOrders}")
        }

        val container = view.findViewById<LinearLayout>(R.id.orderContainer)
        if (d.orders.isEmpty()) container.addView(line("No orders yet."))
        else d.orders.forEach { o ->
            container.addView(line(
                "• ${o.orderNumber}  ·  ${o.status.label}/${o.paymentStatus.label}\n" +
                    "   ₹%.2f  (paid ₹%.2f, due ₹%.2f)".format(o.totalAmount, o.paidAmount, o.remainingAmount)
            ))
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(c.name)
            .setView(view)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun line(text: String) = TextView(requireContext()).apply {
        this.text = text
        setTextColor(resources.getColor(R.color.text_secondary, null))
        textSize = 13f
        setPadding(0, 8, 0, 8)
    }

    private fun confirmDelete(c: Customer) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete customer").setMessage("Deactivate \"${c.name}\"?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    runCatching { repo.deactivate(c.id) }.onSuccess { toast("Deleted."); load() }
                        .onFailure { toast("Delete failed: ${it.message}") }
                }
            }.show()
    }

    private fun activate(c: Customer) {
        lifecycleScope.launch {
            runCatching { repo.activate(c.id) }.onSuccess { toast("Activated."); load() }
                .onFailure { toast("Activate failed: ${it.message}") }
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
