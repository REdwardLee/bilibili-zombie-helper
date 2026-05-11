package com.yourapp.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.text.style.TextDecoration
import com.yourapp.desktop.di.AppController
import com.yourapp.domain.Task

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "YourApp",
        state = rememberWindowState(width = 480.dp, height = 800.dp)
    ) {
        MaterialTheme {
            DesktopApp()
        }
    }
}

@Composable
fun DesktopApp() {
    val controller = remember { AppController() }
    val tasks by controller.tasks.collectAsState()
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
                        controller.addTask(input)
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
                TaskCard(task,
                    onToggle = { controller.toggleTask(it) },
                    onDelete = { controller.deleteTask(it) }
                )
            }
        }
    }
}

@Composable
fun TaskCard(task: Task, onToggle: (String) -> Unit, onDelete: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = { onToggle(task.id) }
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                task.title,
                modifier = Modifier.weight(1f),
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
            )
            TextButton(onClick = { onDelete(task.id) }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
