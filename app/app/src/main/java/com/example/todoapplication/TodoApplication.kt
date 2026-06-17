package com.example.todoapplication

import android.app.Application
import com.example.todoapplication.data.repository.GamificationManager
import com.example.todoapplication.data.repository.ThemeController
import com.example.todoapplication.di.ServiceLocator

/**
 * Application gốc — khởi tạo ServiceLocator (manual DI) và các singleton dữ liệu cục bộ
 * một lần khi app khởi động.
 */
class TodoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        ThemeController.init(this)
        GamificationManager.init(this)
    }
}
