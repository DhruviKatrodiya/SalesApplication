package com.salesapp.mobile.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.core.view.GravityCompat
import com.salesapp.mobile.R
import com.salesapp.mobile.data.Db
import com.salesapp.mobile.data.Session
import com.salesapp.mobile.databinding.ActivityMainBinding
import com.salesapp.mobile.ui.categories.CategoriesFragment
import com.salesapp.mobile.ui.customers.CustomersFragment
import com.salesapp.mobile.ui.dashboard.DashboardFragment
import com.salesapp.mobile.ui.dispatch.DispatchFragment
import com.salesapp.mobile.ui.inventory.InventoryFragment
import com.salesapp.mobile.ui.myorders.MyOrdersFragment
import com.salesapp.mobile.ui.orders.OrdersFragment
import com.salesapp.mobile.ui.profile.ProfileFragment
import com.salesapp.mobile.ui.reports.ReportsFragment
import com.salesapp.mobile.ui.routes.RoutesFragment
import com.salesapp.mobile.ui.subcategories.SubCategoriesFragment
import com.salesapp.mobile.ui.trucks.TrucksFragment
import com.salesapp.mobile.ui.login.LoginActivity
import com.salesapp.mobile.ui.settings.ConnectionSettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Session.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }

        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)

        val toggle = ActionBarDrawerToggle(
            this, b.drawerLayout, b.toolbar, R.string.nav_open, R.string.nav_close
        )
        b.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Fill the drawer header with the signed-in user.
        val header = b.navView.getHeaderView(0)
        header.findViewById<android.widget.TextView>(R.id.tvUserName)?.text =
            Session.currentUser?.fullName
        header.findViewById<android.widget.TextView>(R.id.tvUserEmail)?.text =
            Session.currentUser?.email

        b.navView.setNavigationItemSelectedListener { onNavItem(it) }

        if (savedInstanceState == null) {
            b.navView.setCheckedItem(R.id.nav_dashboard)
            show(DashboardFragment(), "Dashboard")
        }
    }

    private fun onNavItem(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_categories -> show(CategoriesFragment(), "Categories")
            R.id.nav_subcategories -> show(SubCategoriesFragment(), "Sub-categories")
            R.id.nav_dashboard -> show(DashboardFragment(), "Dashboard")
            R.id.nav_inventory -> show(InventoryFragment(), "Inventory")
            R.id.nav_dispatch -> show(DispatchFragment(), "Dispatch")
            R.id.nav_trucks -> show(TrucksFragment(), "Trucks")
            R.id.nav_customers -> show(CustomersFragment(), "Customers")
            R.id.nav_routes -> show(RoutesFragment(), "Routes")
            R.id.nav_orders -> show(OrdersFragment(), "Customer Orders")
            R.id.nav_my_orders -> show(MyOrdersFragment(), "My Orders")
            R.id.nav_reports -> show(ReportsFragment(), "Reports")
            R.id.nav_profile -> show(ProfileFragment(), "Profile")
            R.id.nav_connection -> startActivity(Intent(this, ConnectionSettingsActivity::class.java))
            R.id.nav_logout -> logout()
        }
        b.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun show(fragment: Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .commit()
        supportActionBar?.title = title
    }

    private fun logout() {
        Session.signOut(this)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onBackPressed() {
        if (b.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            b.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
