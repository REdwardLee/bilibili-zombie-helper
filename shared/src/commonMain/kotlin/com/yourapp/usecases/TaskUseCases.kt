// 用例层 — 业务逻辑的核心
// 平台无关，UI 层直接调用
package com.yourapp.usecases

import com.yourapp.data.TaskRepository
import com.yourapp.domain.Task
import kotlinx.coroutines.flow.Flow

/**
 * 获取所有任务流
 * UI 层订阅此 Flow，自动刷新
 */
class GetTasksUseCase(private val repository: TaskRepository) {
    operator fun invoke(): Flow<List<Task>> = repository.getAllTasks()
}

/**
 * 添加新任务
 */
class AddTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(title: String, description: String = "") {
        val task = Task(
            id = generateId(),
            title = title,
            description = description,
            createdAt = currentTimestamp()
        )
        repository.addTask(task)
    }
}

/**
 * 切换任务完成状态
 */
class ToggleTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskId: String) {
        repository.toggleTaskComplete(taskId)
    }
}

/**
 * 删除任务
 */
class DeleteTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskId: String) {
        repository.deleteTask(taskId)
    }
}

// 平台无关的工具函数
internal expect fun generateId(): String
internal expect fun currentTimestamp(): Long
