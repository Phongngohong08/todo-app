package com.example.todoapplication.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.todoapplication.data.model.Task
import com.example.todoapplication.data.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val name: String,
    val createdAt: String,
    val updatedAt: String
)

fun User.toEntity() = UserEntity(id, email, name, createdAt, updatedAt)
fun UserEntity.toDomain() = User(id, email, name, createdAt, updatedAt)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String?,
    val priority: String,
    val dueDate: String?,
    val estimatedDuration: Int,
    val preferredTimeStart: String?,
    val preferredTimeEnd: String?,
    val status: String,
    val createdAt: String,
    val updatedAt: String
)

fun Task.toEntity() = TaskEntity(
    id, userId, title, description, priority, dueDate,
    estimatedDuration, preferredTimeStart, preferredTimeEnd,
    status, createdAt, updatedAt
)

fun TaskEntity.toDomain() = Task(
    id, userId, title, description, priority, dueDate,
    estimatedDuration, preferredTimeStart, preferredTimeEnd,
    status, createdAt, updatedAt
)
