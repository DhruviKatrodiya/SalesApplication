package com.salesapp.mobile.ui.dashboard

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.salesapp.mobile.R
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.repo.DashboardRepository
import com.salesapp.mobile.databinding.FragmentDashboardBinding
import kotlinx.coroutines.launch

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private var _b: FragmentDashboardBinding? = null
    private val b get() = _b!!
    private val repo = DashboardRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentDashboardBinding.bind(view)
        b.swipe.setOnRefreshListener { load() }
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val result = runCatching { repo.stats() }
            b.swipe.isRefreshing = false
            b.statsContainer.removeAllViews()
            b.statsContainer.addView(header("Welcome, ${Session.currentUser?.fullName ?: "salesperson"}"))
            result.fold(
                onSuccess = { s ->
                    b.statsContainer.addView(stat("Active items", s.activeItems.toString()))
                    b.statsContainer.addView(stat("Low-stock items", s.lowStock.toString()))
                    b.statsContainer.addView(stat("Customers", s.customers.toString()))
                    b.statsContainer.addView(stat("Pending orders", s.pendingOrders.toString()))
                    b.statsContainer.addView(stat("Outstanding balance", "₹%.2f".format(s.outstanding)))
                    b.statsContainer.addView(stat("Pending stock requests", s.pendingRequests.toString()))
                },
                onFailure = { b.statsContainer.addView(header("Could not load stats: ${it.message}")) },
            )
        }
    }

    private fun header(text: String) = TextView(requireContext()).apply {
        this.text = text
        setTextColor(resources.getColor(R.color.text_primary, null))
        textSize = 18f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(8, 8, 8, 16)
    }

    private fun stat(label: String, value: String): View {
        val ll = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(40, 36, 40, 36)
        }
        ll.addView(TextView(requireContext()).apply {
            text = label; setTextColor(resources.getColor(R.color.text_secondary, null)); textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        ll.addView(TextView(requireContext()).apply {
            text = value; setTextColor(resources.getColor(R.color.primary, null)); textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        return MaterialCardView(requireContext()).apply {
            radius = 24f; cardElevation = 2f
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 12); layoutParams = lp
            addView(ll)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
