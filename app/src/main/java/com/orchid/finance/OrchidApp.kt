package com.orchid.finance

import android.app.Application
import com.orchid.finance.data.AppDatabase
import com.orchid.finance.data.Repository

class OrchidApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: Repository by lazy { Repository(database) }
}
