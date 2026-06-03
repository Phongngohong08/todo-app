package com.example.todoapplication.data.repository

import com.example.todoapplication.data.api.ApiService
import com.example.todoapplication.data.local.*
import com.example.todoapplication.data.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TodoRepository(
    private val apiService: ApiService,
    private val taskDao: TaskDao,
    private val userDao: UserDao
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

    suspend fun addTask(task: Task) {
        taskDao.insertTask(task.toEntity())
    }

    suspend fun deleteTask(taskId: String) {
        taskDao.deleteTaskById(taskId)
    }
    
    // Add more methods as needed for sync, login etc.
}
