package com.example.todoapplication

import android.app.Application
import com.example.todoapplication.data.local.AppDatabase

class MyApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        AppDatabase.getDatabase(applicationContext)
    }
}