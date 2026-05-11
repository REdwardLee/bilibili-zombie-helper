// Android DI / ViewModel — 唯一接触平台特定实现的地方
package com.yourapp.android.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.data.TaskRepository
import com.yourapp.domain.Task
import com.yourapp.usecases.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {

    // TODO: 换成真实 Repository（Room / DataStore / 网络）
    private val repository: TaskRepository = InMemoryTaskRepository()

    private val getTasks = GetTasksUseCase(repository)
    private val addTask = AddTaskUseCase(repository)
    private val toggleTask = ToggleTaskUseCase(repository)
    private val deleteTask = DeleteTaskUseCase(repository)

    val tasks: StateFlow<List<Task>> = getTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTask(title: String) {
        viewModelScope.launch { addTask(title) }
    }

    fun toggleTask(id: String) {
        viewModelScope.launch { toggleTask(id) }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch { deleteTask(id) }
    }
}

// 临时内存实现，后续替换为真实存储
class InMemoryTaskRepository : TaskRepository {
    private val _tasks = mutableListOf<Task>()
    private var _flow: kotlinx.coroutines.flow.MutableStateFlow<List<Task>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())

    override fun getAllTasks() = _flow
    override suspend fun getTaskById(id: String) = _tasks.find { it.id == id }
    override suspend fun addTask(task: Task) {
        _tasks.add(task)
        _flow.value = _tasks.toList()
    }
    override suspend fun updateTask(task: Task) {
        val idx = _tasks.indexOfFirst { it.id == task.id }
        if (idx != -1) _tasks[idx] = task
        _flow.value = _tasks.toList()
    }
    override suspend fun deleteTask(id: String) {
        _tasks.removeAll { it.id == id }
        _flow.value = _tasks.toList()
    }
    override suspend fun toggleTaskComplete(id: String) {
        val task = _tasks.find { it.id == id } ?: return
        updateTask(task.copy(isCompleted = !task.isCompleted))
    }
}
