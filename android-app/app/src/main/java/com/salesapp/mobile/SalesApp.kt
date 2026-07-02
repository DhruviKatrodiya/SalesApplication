package com.salesapp.mobile

import android.app.Application
import com.salesapp.mobile.data.Db

class SalesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Load saved SQL Server connection details (if the user has configured them).
        Db.loadConfig(this)
    }
}
