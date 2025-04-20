package com.zhenbang.otw.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zhenbang.otw.database.Task

@Composable
fun TaskDetailScreen(navController: NavController, taskViewModel: TaskViewModel, taskId: Int) {
    val task: Task? by taskViewModel.getTaskById(taskId).collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (task != null) {
            Text(text = "Task Details")
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Title: ${task?.taskTitle}")
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Description: ${task?.taskDescription}")
        } else {
            Text(text = "Loading task details...")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.popBackStack() }) {
            Text("Back to Task List")
        }
    }
}

@Composable
fun AddEditTaskScreen(
    navController: NavController,
    departmentId: Int,
    taskViewModel: TaskViewModel,
    taskId: Int
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val taskToEdit by taskViewModel.getTaskById(taskId).collectAsState(initial = null)

    LaunchedEffect(taskToEdit) {
        taskToEdit?.let {
            title = it.taskTitle
            description = it.taskDescription
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (title.isNotBlank() && description.isNotBlank()) {
                    val task = Task(
                        taskId = if (taskId != -1) taskId else 0,
                        departmentId = departmentId,
                        taskTitle = title,
                        taskDescription = description
                    )
                    taskViewModel.insertTask(task)
                    navController.popBackStack()
                }
            }) {
                Icon(Icons.Filled.Check, contentDescription = "Save Task")
            }
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (taskId == -1) "New Task" else "Edit Task",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )

                    if (taskId != -1) {
                        IconButton(onClick = {
                            taskToEdit?.let { taskToDelete ->
                                taskViewModel.deleteTask(taskToDelete)
                                navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Task")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    )
}