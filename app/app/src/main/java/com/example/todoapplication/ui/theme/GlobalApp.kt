package com.example.todoapplication.ui.theme

import androidx.compose.runtime.compositionLocalOf
import com.example.todoapplication.manager.AlarmScheduler
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.repository.TodoRepository

val LocalTodoRepository = compositionLocalOf<TodoRepository> {
    error("TodoRepository not provided")
}

val LocalScheduler = compositionLocalOf<AlarmScheduler> {
    error("Scheduler not provided")
}