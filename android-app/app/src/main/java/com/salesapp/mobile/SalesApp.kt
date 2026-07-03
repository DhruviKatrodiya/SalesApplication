package com.salesapp.mobile

import android.app.Application
import com.salesapp.mobile.data.Db

class SalesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create the local SQLite database + schema and seed the default user (first launch).
        Db.init(this)
    }
}
