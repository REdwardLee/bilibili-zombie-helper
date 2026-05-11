// Android UI — 纯展示层，业务逻辑全部来自 shared 模块
package com.yourapp.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourapp.android.di.AppViewModel
import com.yourapp.domain.Task

@Composable
fun AppUI() {
    val vm: AppViewModel = viewModel()
    val tasks by vm.tasks.collectAsState()
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("YourApp", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        Row {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("New task...") }
            )
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        vm.addTask(input)
                        input = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Add")
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(tasks, key = { it.id }) { task ->
                TaskItem(task, onToggle = { vm.toggleTask(it) }, onDelete = { vm.deleteTask(it) })
            }
        }
    }
}

@Composable
fun TaskItem(task: Task, onToggle: (String) -> Unit, onDelete: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = { onToggle(task.id) }
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                task.title,
                modifier = Modifier.weight(1f),
                textDecoration = if (task.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
            )
            TextButton(onClick = { onDelete(task.id) }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
