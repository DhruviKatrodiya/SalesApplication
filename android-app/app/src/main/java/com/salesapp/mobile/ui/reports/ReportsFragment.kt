package com.salesapp.mobile.ui.reports

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.salesapp.mobile.R
import com.salesapp.mobile.data.models.ReportSummary
import com.salesapp.mobile.data.repo.ReportRepository
import com.salesapp.mobile.databinding.FragmentReportsBinding
import kotlinx.coroutines.launch

class ReportsFragment : Fragment(R.layout.fragment_reports) {

    private var _b: FragmentReportsBinding? = null
    private val b get() = _b!!
    private val repo = ReportRepository()

    private val types = listOf("Monthly", "Daily", "Yearly", "By customer")
    private var type = "Monthly"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentReportsBinding.bind(view)
        b.acType.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, types))
        b.acType.setText(type, false)
        b.acType.setOnItemClickListener { _, _, pos, _ -> type = types[pos]; refreshInputs() }
        // Seed a sensible default year (current) without Date APIs banned only in workflow scripts.
        b.etYear.setText(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString())
        b.etMonth.setText((java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1).toString())
        refreshInputs()
        b.btnRun.setOnClickListener { run() }
    }

    private fun refreshInputs() {
        val byCustomer = type == "By customer"
        b.tilYear.visibility = if (byCustomer) View.GONE else View.VISIBLE
        b.tilMonth.visibility = if (type == "Daily") View.VISIBLE else View.GONE
    }

    private fun run() {
        val year = b.etYear.text?.toString()?.toIntOrNull()
        val month = b.etMonth.text?.toString()?.toIntOrNull()
        b.rowsContainer.removeAllViews()
        lifecycleScope.launch {
            try {
                when (type) {
                    "Monthly" -> { requireYear(year); showSummary(repo.monthly(year!!, null)) }
                    "Yearly" -> { requireYear(year); showSummary(repo.yearly(year!!)) }
                    "Daily" -> {
                        requireYear(year)
                        if (month == null || month !in 1..12) { toast("Enter a month 1-12."); return@launch }
                        showSummary(repo.daily(year!!, month))
                    }
                    "By customer" -> showByCustomer()
                }
            } catch (e: Exception) {
                toast(e.message ?: "Report failed.")
            }
        }
    }

    private fun requireYear(year: Int?) {
        if (year == null || year < 2000) throw IllegalArgumentException("Enter a valid year.")
    }

    private fun showSummary(s: ReportSummary) {
        b.tvSummary.text = buildString {
            appendLine("Period: ${s.period}")
            appendLine("Orders: ${s.totalOrders}   (pending ${s.pendingOrders}, delivered ${s.deliveredOrders})")
            appendLine("Total: ₹%.2f".format(s.totalAmount))
            appendLine("Paid: ₹%.2f".format(s.totalPaid))
            append("Outstanding: ₹%.2f".format(s.totalRemaining))
        }
        if (s.rows.isEmpty()) b.rowsContainer.addView(line("No breakdown rows."))
        else s.rows.forEach { r ->
            b.rowsContainer.addView(card(
                r.label,
                "${r.orderCount} orders  ·  ₹%.2f total  ·  ₹%.2f paid  ·  ₹%.2f due".format(r.totalAmount, r.paidAmount, r.remainingAmount)
            ))
        }
    }

    private fun showByCustomer() {
        lifecycleScope.launch {
            val rows = runCatching { repo.byCustomer() }.getOrElse { toast("Failed: ${it.message}"); return@launch }
            b.tvSummary.text = "Per-customer summary (${rows.size} customers)"
            b.rowsContainer.removeAllViews()
            if (rows.isEmpty()) b.rowsContainer.addView(line("No data."))
            else rows.forEach { r ->
                b.rowsContainer.addView(card(
                    r.customerName,
                    "${r.totalOrders} orders (pending ${r.pendingOrders}, delivered ${r.deliveredOrders})\n" +
                        "₹%.2f total  ·  ₹%.2f paid  ·  ₹%.2f due".format(r.totalAmount, r.paidAmount, r.remainingAmount)
                ))
            }
        }
    }

    private fun card(title: String, body: String): View {
        val ll = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }
        ll.addView(TextView(requireContext()).apply { text = title; setTextColor(resources.getColor(R.color.text_primary, null)); textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        ll.addView(TextView(requireContext()).apply { text = body; setTextColor(resources.getColor(R.color.text_secondary, null)); textSize = 13f; setPadding(0, 4, 0, 0) })
        val card = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            radius = 24f; cardElevation = 2f
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 8, 0, 0); layoutParams = lp
            addView(ll)
        }
        return card
    }

    private fun line(text: String) = TextView(requireContext()).apply {
        this.text = text; setTextColor(resources.getColor(R.color.text_secondary, null)); setPadding(0, 12, 0, 12)
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
