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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zhenbang.otw.database.Task

@Composable
fun TaskDetailScreen(navController: NavController, taskViewModel: TaskViewModel, taskId: Int) {
    val task: Task? by taskViewModel.getTaskById(taskId).collectAsState(initial = null)

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Go Back")
                }
                Text(
                    text = "Task",
                    style = typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(32.dp),
        ) {
            if (task != null) {
                Text(text = "${task?.taskTitle}", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(18.dp))
                Text(text = "${task?.taskDescription}", fontSize = 18.sp)
            } else {
                Text(text = "Loading task details...")
            }
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
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Go Back")
                }
                Text(
                    text = if (taskId == -1) "New Task" else "Edit Task",
                    style = typography.titleLarge,
                    modifier = Modifier.weight(1f)
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
        },
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
            ) {
                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Title", fontWeight = FontWeight.Bold, fontSize = 36.sp ) },
                    modifier = Modifier
                        .fillMaxWidth(),
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    ),
                    colors = textFieldColors
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Enter your task here.", fontSize = 18.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    textStyle = TextStyle(
                        fontSize = 18.sp
                    ),
                    colors = textFieldColors
                )
            }
        }
    )
}