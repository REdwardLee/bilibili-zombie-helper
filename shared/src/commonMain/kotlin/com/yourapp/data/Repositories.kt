// Repository 接口 — 定义数据契约，实现由平台注入
package com.yourapp.data

import com.yourapp.domain.Task
import com.yourapp.domain.User
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun getAllTasks(): Flow<List<Task>>
    suspend fun getTaskById(id: String): Task?
    suspend fun addTask(task: Task)
    suspend fun updateTask(task: Task)
    suspend fun deleteTask(id: String)
    suspend fun toggleTaskComplete(id: String)
}

interface UserRepository {
    suspend fun getCurrentUser(): User?
    suspend fun saveUser(user: User)
}
