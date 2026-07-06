package com.salesapp.mobile.ui.dashboard

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.salesapp.mobile.R
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.data.repo.DashboardRepository
import com.salesapp.mobile.databinding.FragmentDashboardBinding
import com.salesapp.mobile.ui.main.MainActivity
import kotlinx.coroutines.launch

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private var _b: FragmentDashboardBinding? = null
    private val b get() = _b!!
    private val repo = DashboardRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentDashboardBinding.bind(view)
        b.tvSubtitle.text = "Welcome, ${Session.currentUser?.fullName ?: "salesperson"}"
        b.swipe.setOnRefreshListener { load() }

        b.btnNewOrder.setOnClickListener { (activity as? MainActivity)?.goTo(R.id.nav_orders) }
        b.btnDispatch.setOnClickListener { (activity as? MainActivity)?.goTo(R.id.nav_dispatch) }
        b.btnAddCustomer.setOnClickListener { (activity as? MainActivity)?.goTo(R.id.nav_customers) }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val result = runCatching { repo.stats() }
            b.swipe.isRefreshing = false
            result.fold(
                onSuccess = { s ->
                    b.tvError.visibility = View.GONE
                    b.vItems.text = s.activeItems.toString()
                    b.vLowStock.text = s.lowStock.toString()
                    b.vCustomers.text = s.customers.toString()
                    b.vPending.text = s.pendingOrders.toString()
                    b.vSales.text = money(s.totalSales)
                    b.vOutstanding.text = money(s.outstanding)
                },
                onFailure = {
                    b.tvError.visibility = View.VISIBLE
                    b.tvError.text = "Could not load stats: ${it.message}"
                },
            )
        }
    }

    private fun money(v: Double): String = "₹%,.0f".format(v)

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
