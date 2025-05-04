package com.zhenbang.otw.tasks

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddTask
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.database.SubTask
import com.zhenbang.otw.database.Task
import com.zhenbang.otw.database.TaskAssignment
import com.zhenbang.otw.departments.Screen
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.itemsIndexed

@Composable
fun TaskDetailScreen(navController: NavController, taskViewModel: TaskViewModel, taskId: Int) {
    val task: Task? by taskViewModel.getTaskById(taskId).collectAsState(initial = null)
    val assignedUsers: List<TaskAssignment> by taskViewModel.getAssignedUsersForTask(taskId)
        .collectAsState(initial = emptyList())
    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
    val subTasks: List<SubTask> by taskViewModel.getSubTasksByTaskId(taskId)
        .collectAsState(initial = emptyList())
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
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(32.dp),
            contentPadding = PaddingValues(bottom = 50.dp)
        ) {
            if (task != null) {
                item {
                    Text(text = "${task?.taskTitle}", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(text = "${task?.taskDescription}", fontSize = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
                }

                if (subTasks.isNotEmpty()) {
                    itemsIndexed(subTasks) { index, subTask ->
                        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), thickness = 2.dp, color = Color.LightGray)
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = "${if (subTask.isCompleted) "✓" else "X"} ${index + 1}. ${subTask.subTaskTitle}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (subTask.isCompleted) Color.Green else Color.Red,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Text(
                                text = subTask.subTaskDesc,
                                fontSize = 15.sp,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), thickness = 3.dp, color = Color.Black)
                }

                if (assignedUsers.isNotEmpty()) {
                    items(assignedUsers) { assignment ->
                        Column {
                            Text(
                                text = assignment.userEmail,
                                modifier = Modifier.padding(8.dp)
                            )
                            HorizontalDivider(thickness = 2.dp, color = Color.LightGray)
                        }
                    }
                } else {
                    item {
                        Text(text = "No one is assigned to this task yet.")
                    }
                }
            } else {
                item {
                    Text(text = "Loading task details...")
                }
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

    val deptUsers by taskViewModel.getDeptUsersByDepartmentId(departmentId)
        .collectAsState(initial = emptyList())
    var showAssignDialog by rememberSaveable { mutableStateOf(false) }
    var selectedUsersToAssign =
        remember { mutableStateListOf<String>() }
    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

    var showSubTaskDialog by rememberSaveable { mutableStateOf(false) }
    var editingSubTaskIndex by rememberSaveable { mutableStateOf(-1) }
    var subTaskTitle by rememberSaveable { mutableStateOf("") }
    var subTaskDesc by rememberSaveable { mutableStateOf("") }
    var subTasks = remember { mutableStateListOf<SubTask>() }

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

                    subTasks.clear()
                    val fetchedSubTasks = taskViewModel.getSubTasksByTaskId(taskId).firstOrNull() ?: emptyList()
                    subTasks.addAll(fetchedSubTasks)
                }
            } else {
                selectedUsersToAssign.clear()
                subTasks.clear()
            }
        } ?: run {
            selectedUsersToAssign.clear()
            subTasks.clear()
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
                IconButton(onClick = { showSubTaskDialog = true }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Task")
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
                            taskViewModel.updateTask(task, selectedUsersToAssign, subTasks)
                        } else {
                            taskViewModel.insertTask(task, selectedUsersToAssign, subTasks)
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
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 60.dp)
        ) {
            item {
                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Title", fontWeight = FontWeight.Bold, fontSize = 36.sp) },
                    modifier = Modifier.fillMaxWidth(),
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
                        .fillMaxWidth(),
                    textStyle = TextStyle(
                        fontSize = 18.sp
                    ),
                    colors = textFieldColors
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            items(subTasks) { subTask ->
                val index = subTasks.indexOf(subTask)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 10.dp), thickness = 2.dp, color = Color.LightGray)
                Row(
                    verticalAlignment = Alignment.Top, // Changed to Top
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clickable {
                            editingSubTaskIndex = index
                            subTaskTitle = subTask.subTaskTitle
                            subTaskDesc = subTask.subTaskDesc
                            showSubTaskDialog = true
                        },

                    ) {
                    Column {
                        Checkbox(
                            checked = subTask.isCompleted,
                            onCheckedChange = { isChecked ->
                                val updatedSubTask = subTask.copy(isCompleted = isChecked)
                                subTasks[index] = updatedSubTask
                                taskViewModel.updateSubTaskCompletion(updatedSubTask, isChecked)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${index + 1}. ${subTask.subTaskTitle}")
                            IconButton(onClick = { subTasks.removeAt(index) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete Subtask",
                                    tint = Color.Red
                                )
                            }
                        }
                        Text(text = subTask.subTaskDesc, modifier = Modifier.padding(end = 12.dp, bottom = 10.dp))
                    }
                }
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    thickness = 3.dp,
                    color = Color.Black
                )
            }
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

        if (showSubTaskDialog) {
            AlertDialog(
                onDismissRequest = {
                    showSubTaskDialog = false
                    editingSubTaskIndex = -1
                    subTaskTitle = ""
                    subTaskDesc = ""
                },
                title = { Text(if (editingSubTaskIndex == -1) "Add Sub-Task" else "Edit Sub-Task") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = subTaskTitle,
                            onValueChange = { subTaskTitle = it },
                            placeholder = { Text("Sub-Task Title") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = subTaskDesc,
                            onValueChange = { subTaskDesc = it },
                            placeholder = { Text("Sub-Task Description") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (subTaskTitle.isNotBlank() && subTaskDesc.isNotBlank()) {
                                if (editingSubTaskIndex != -1) {
                                    // Edit existing sub-task
                                    val updatedSubTask = subTasks[editingSubTaskIndex].copy(
                                        subTaskTitle = subTaskTitle,
                                        subTaskDesc = subTaskDesc
                                    )
                                    subTasks[editingSubTaskIndex] = updatedSubTask
                                } else {
                                    // Add new sub-task
                                    val newSubTask = SubTask(
                                        taskId = taskId,
                                        subTaskTitle = subTaskTitle,
                                        subTaskDesc = subTaskDesc,
                                        isCompleted = false,
                                    )
                                    subTasks.add(newSubTask)
                                }
                                showSubTaskDialog = false
                                editingSubTaskIndex = -1
                                subTaskTitle = ""
                                subTaskDesc = ""
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Button(onClick = {
                        showSubTaskDialog = false
                        editingSubTaskIndex = -1
                        subTaskTitle = ""
                        subTaskDesc = ""
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}