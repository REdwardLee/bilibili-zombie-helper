package com.yourapp.desktop.di

import com.yourapp.data.TaskRepository
import com.yourapp.domain.Task
import com.yourapp.usecases.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AppController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val repository: TaskRepository = InMemoryTaskRepository()
    private val getTasks = GetTasksUseCase(repository)
    private val addTask = AddTaskUseCase(repository)
    private val toggleTask = ToggleTaskUseCase(repository)
    private val deleteTask = DeleteTaskUseCase(repository)

    val tasks: StateFlow<List<Task>> = getTasks()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTask(title: String) {
        scope.launch { addTask(title) }
    }

    fun toggleTask(id: String) {
        scope.launch { toggleTask(id) }
    }

    fun deleteTask(id: String) {
        scope.launch { deleteTask(id) }
    }
}

class InMemoryTaskRepository : TaskRepository {
    private val _tasks = mutableListOf<Task>()
    private val _flow = MutableStateFlow<List<Task>>(emptyList())

    override fun getAllTasks() = _flow.asStateFlow()
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
