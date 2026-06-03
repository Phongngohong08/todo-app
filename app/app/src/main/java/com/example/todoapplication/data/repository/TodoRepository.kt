package com.example.todoapplication.data.repository

import com.example.todoapplication.data.api.ApiService
import com.example.todoapplication.data.local.*
import com.example.todoapplication.data.model.AlarmItem
import com.example.todoapplication.data.model.CreateTaskInput
import com.example.todoapplication.data.model.UpdateTaskInput
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.data.model.toTask
import com.example.todoapplication.manager.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import retrofit2.Response
import java.time.LocalDateTime
import java.util.UUID

class TodoRepository(
    private val apiService: ApiService,
    private val taskDao: TaskDao,
    private val userDao: UserDao,
    private val alarmScheduler: AlarmScheduler,
    private val sessionManager: SessionManager
) {
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun refreshTasks() {
        try {
            val response = apiService.listTasks()
            if (response.isSuccessful) {
                response.body()?.let { tasks ->
                    taskDao.clearAllTasks()
                    taskDao.insertTasks(tasks.map { it.toEntity() })
                }
            }
        } catch (e: Exception) {
            // Handle error
        }
    }

    suspend fun addTask(input: CreateTaskInput,time: LocalDateTime): Response<Task> {
        val response = apiService.createTask(input)
        val task = input.toTask(sessionManager.getUserId())
        taskDao.insertTask(task.toEntity())
        val item = AlarmItem(
            time = time,
            message = input.title,
        )
        alarmScheduler.schedule(item)
        return response
    }

    suspend fun updateTask(taskId: String, input: UpdateTaskInput,time: LocalDateTime): Response<Task> {
        val response = apiService.updateTask(taskId, input)

        if (response.isSuccessful) {
            response.body()?.let { task ->
                taskDao.updateTask(task.toEntity())
            }
        }
        return response
    }

    suspend fun deleteTask(taskId: String) {
        taskDao.deleteTaskById(taskId)
    }

    suspend fun clearAllTasks() {
        taskDao.clearAllTasks()
    }

    suspend fun fetchTasksIfEmpty() {
        val count = taskDao.getTaskCount()
        if (count == 0) {
            try {
                val response = apiService.listTasks()
                if (response.isSuccessful) {
                    response.body()?.let { tasks ->
                        taskDao.insertTasks(tasks.map { it.toEntity() })
                    }
                }
            } catch (e: Exception) {
                // Log error
            }
        }
    }
    
    // Add more methods as needed for sync, login etc.
}
