package com.zhenbang.otw.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.database.Task
import com.zhenbang.otw.database.TaskAssignment
import com.zhenbang.otw.departments.Screen
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@Composable
fun TaskDetailScreen(navController: NavController, taskViewModel: TaskViewModel, taskId: Int) {
    val task: Task? by taskViewModel.getTaskById(taskId).collectAsState(initial = null)
    val assignedUsers: List<TaskAssignment> by taskViewModel.getAssignedUsersForTask(taskId)
        .collectAsState(initial = emptyList())
    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
    val canEdit by taskViewModel.canEditTask(taskId, currentUserEmail)
        .collectAsState(initial = false)

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
        },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(onClick = {
                    // Navigate to the AddEditTaskScreen in edit mode
                    navController.navigate(
                        Screen.AddEditTask.createRoute(
                            task?.departmentId ?: -1,
                            taskId
                        )
                    )
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Task")
                }
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
                Text(
                    text = "${task?.taskDescription}",
                    fontSize = 18.sp,
                    modifier = Modifier.height(500.dp)
                )
                HorizontalDivider(thickness = 4.dp, color = Color.Black)
                if (assignedUsers.isNotEmpty()) {
                    Column {
                        assignedUsers.forEach { assignment ->
                            Text(
                                text = assignment.userEmail,
                                modifier = Modifier.padding(8.dp)
                            ) // Display the user's email
                            HorizontalDivider(thickness = 2.dp, color = Color.LightGray)
                        }
                    }
                } else {
                    Text(text = "No one is assigned to this task yet.")
                }

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
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    val taskToEdit by taskViewModel.getTaskById(taskId).collectAsState(initial = null)

    // State to hold the list of users in the department
    val deptUsers by taskViewModel.getDeptUsersByDepartmentId(departmentId) // Use taskViewModel
        .collectAsState(initial = emptyList())
    var showAssignDialog by rememberSaveable { mutableStateOf(false) } // New state for the dialog
    var selectedUsersToAssign =
        remember { mutableStateListOf<String>() } // Emails of users to assign
    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

    var assignSearchQuery by rememberSaveable { mutableStateOf("") }
    val filteredDeptUsers = remember(deptUsers, assignSearchQuery) {
        deptUsers.filter { it.userEmail.contains(assignSearchQuery, ignoreCase = true) }
    }

    LaunchedEffect(taskToEdit) {
        taskToEdit?.let { task ->
            title = task.taskTitle
            description = task.taskDescription
            if (taskId != -1) {
                // Fetch assigned users when editing
                taskViewModel.viewModelScope.launch {
                    val assignedTaskAssignments =
                        taskViewModel.getAssignedUsersForTask(taskId).firstOrNull() ?: emptyList()
                    val assignedEmails = assignedTaskAssignments.map { it.userEmail }
                    selectedUsersToAssign.clear()
                    selectedUsersToAssign.addAll(assignedEmails)
                }
            } else {
                selectedUsersToAssign.clear()
            }
        } ?: run {
            selectedUsersToAssign.clear()
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
                if (taskId != -1 && taskToEdit?.creatorEmail == currentUserEmail) {
                    IconButton(onClick = {
                        taskToEdit?.let { taskToDelete ->
                            taskViewModel.deleteTask(taskToDelete)
                            navController.popBackStack()
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Task")
                    }
                }
                IconButton(onClick = {
                    if (title.isNotBlank() && description.isNotBlank()) {
                        val task = Task(
                            taskId = if (taskId != -1) taskId else 0,
                            taskTitle = title,
                            taskDescription = description,
                            departmentId = departmentId,
                            creatorEmail = currentUserEmail
                        )
                        if (taskId != -1) {
                            taskViewModel.updateTask(task, selectedUsersToAssign)
                        } else {
                            taskViewModel.insertTask(task, selectedUsersToAssign)
                        }
                        navController.popBackStack()
                    }
                }) {
                    Icon(Icons.Filled.Check, contentDescription = "Save Task")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAssignDialog = true }) {
                Icon(Icons.Filled.GroupAdd, contentDescription = "Assign People")
            }
        }
    ) { paddingValues ->
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
                placeholder = { Text("Title", fontWeight = FontWeight.Bold, fontSize = 36.sp) },
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

        if (showAssignDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAssignDialog = false
                    selectedUsersToAssign.clear() // Clear selection on dismiss
                },
                title = { Text("Assign People to Task") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = assignSearchQuery,
                            onValueChange = { assignSearchQuery = it },
                            placeholder = { Text("Search email") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (deptUsers.isEmpty()) {
                            Text("No users in this department to assign.")
                        } else if (filteredDeptUsers.isEmpty()) {
                            Text("No matching users found.")
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxHeight(0.7f)) {
                                items(filteredDeptUsers) { user ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (selectedUsersToAssign.contains(user.userEmail)) {
                                                    selectedUsersToAssign.remove(user.userEmail)
                                                } else {
                                                    selectedUsersToAssign.add(user.userEmail)
                                                }
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = selectedUsersToAssign.contains(user.userEmail),
                                            onCheckedChange = { isChecked ->
                                                if (isChecked) {
                                                    selectedUsersToAssign.add(user.userEmail)
                                                } else {
                                                    selectedUsersToAssign.remove(user.userEmail)
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(user.userEmail) // Access userEmail from the user object
                                    }
                                    Divider()
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAssignDialog = false
                        },
                        enabled = selectedUsersToAssign.isNotEmpty()
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    Button(onClick = { showAssignDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}