package com.zhenbang.otw.departments

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository
import com.zhenbang.otw.database.Department
import com.zhenbang.otw.database.Issue
import com.zhenbang.otw.database.Task
import com.zhenbang.otw.issues.IssueViewModel
import com.zhenbang.otw.tasks.TaskViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch


sealed class Screen(val route: String) {
    object DepartmentList : Screen("department_list")
    object DepartmentDetails : Screen("department_details/{departmentId}/{departmentName}") {
        fun createRoute(departmentId: Int, departmentName: String) =
            "department_details/$departmentId/${departmentName.replace("/", "%2F")}" // encode slashes
    }

    object TaskDetail : Screen("task_details/{taskId}") {
        fun createRoute(taskId: Int) = "task_details/$taskId"
    }

    object AddEditTask : Screen("add_edit_task/{departmentId}/{taskId}") {
        fun createRoute(departmentId: Int) = "add_edit_task/$departmentId/-1" // Add new task
        fun createRoute(departmentId: Int, taskId: Int) = "add_edit_task/$departmentId/$taskId" // Edit existing task
    }

    // Add routes for Issues
    object AddEditIssue : Screen("add_edit_issue/{departmentId}/{issueId}") {
        fun createRoute(departmentId: Int) = "add_edit_issue/$departmentId/-1" // Add new issue
        fun createRoute(departmentId: Int, issueId: Int) = "add_edit_issue/$departmentId/$issueId" // Edit existing issue
    }

    // Optional: If you want a separate detail screen for issues
    // object IssueDetail : Screen("issue_details/{issueId}") {
    //    fun createRoute(issueId: Int) = "issue_details/$issueId"
    // }
}

@Composable
fun DepartmentListScreen(navController: NavController) {
    val context = LocalContext.current
    val departmentViewModel: DepartmentViewModel =
        viewModel(factory = DepartmentViewModel.Factory(context))
    val userDepartments by departmentViewModel.userDepartments.collectAsState()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var departmentName by rememberSaveable { mutableStateOf("") }
    var imageUrl by rememberSaveable { mutableStateOf("") }
    var isGridView by rememberSaveable { mutableStateOf(true) }
    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
    val coroutineScope = rememberCoroutineScope()

    var isSortAscending by rememberSaveable { mutableStateOf(true) }
    // Modify the displayed list based on the sorting state
    val sortedDepartments = remember(userDepartments, isSortAscending) {
        if (isSortAscending) {
            userDepartments.sortedBy { it.departmentName.lowercase() }
        } else {
            userDepartments.sortedByDescending { it.departmentName.lowercase() }
        }
    }

    LaunchedEffect(currentUserEmail) {
        if (currentUserEmail != null) {
            departmentViewModel.loadDepartmentsForCurrentUser(currentUserEmail)
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
                    text = "Workspace",
                    style = typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Box(contentAlignment = Alignment.CenterEnd) {
                    IconButton(onClick = { }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 83.dp)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isSortAscending = !isSortAscending }) {
                        Icon(Icons.Filled.Sort, contentDescription = "Sort")
                    }
                    Text(text = "Sort")
                }
                IconButton(onClick = { isGridView = !isGridView }) {
                    Icon(
                        imageVector = if (isGridView) Icons.Filled.GridView else Icons.Filled.List,
                        contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view"
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Department")
            }
        }
    ) { paddingValues ->
        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(8.dp)
            ) {
                items(sortedDepartments) { department ->
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable {
                                navController.navigate(
                                    Screen.DepartmentDetails.createRoute(
                                        department.departmentId,
                                        department.departmentName
                                    )
                                )
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = department.imageUrl,
                                contentDescription = department.departmentName,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )

                            Text(
                                text = department.departmentName, fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(8.dp)
            ) {
                items(sortedDepartments) { department ->
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable {
                                navController.navigate(
                                    Screen.DepartmentDetails.createRoute(
                                        department.departmentId,
                                        department.departmentName
                                    )
                                )
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            AsyncImage(
                                model = department.imageUrl,
                                contentDescription = department.departmentName,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )

                            Text(
                                text = department.departmentName,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add Department") },
            text = {
                Column {
                    OutlinedTextField(
                        value = departmentName,
                        onValueChange = { departmentName = it },
                        label = { Text("Department Name") },
                        isError = departmentName.isBlank(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("Image URL (Optional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = departmentName.trim()
                        val finalImageUrl = imageUrl.trim().ifEmpty { null } // Use null if blank

                        if (finalName.isNotEmpty()) {
                            if (currentUserEmail != null) {
                                coroutineScope.launch {
                                    departmentViewModel.insertDepartment(
                                        departmentName = finalName,
                                        imageUrl = finalImageUrl,
                                        creatorEmail = currentUserEmail
                                    )
                                    showDialog = false // Close dialog after success
                                }
                            } else {
                                // Handle case where email is null (user not logged in?)
                                Log.e("DepartmentListScreen", "Cannot add department: User email is null.")
                                coroutineScope.launch {
                                    Toast.makeText(context, "Error: Could not get user email. Please log in.", Toast.LENGTH_LONG).show()
                                }
                                // Keep dialog open or close it based on desired UX
                                // showDialog = false
                            }
                        } else {
                            // Optionally show message if name is empty
                            coroutineScope.launch {
                                Toast.makeText(context, "Department name cannot be empty.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                Button(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DepartmentDetailsScreen(
    navController: NavController,
    departmentId: Int,
    departmentName: String,
) {
    val context = LocalContext.current
    // Department ViewModel
    val departmentViewModel: DepartmentViewModel = viewModel(factory = DepartmentViewModel.Factory(context))
    val departmentState: Department? by departmentViewModel.getDepartmentById(departmentId)
        .collectAsState(initial = null)
    val selectedTab by departmentViewModel.selectedTabFlow.collectAsState()
    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
    val coroutineScope = rememberCoroutineScope()

    // Task ViewModel and Tasks
    val taskViewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory(context))
    val tasks by taskViewModel.getTasksByDepartmentId(departmentId)
        .collectAsState(initial = emptyList())

    // *** Issue ViewModel and Issues ***
    val issueViewModel: IssueViewModel = viewModel(factory = IssueViewModel.Factory(context))
    val issues by issueViewModel.getIssuesByDepartmentId(departmentId)
        .collectAsState(initial = emptyList()) // Collect issues

    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var editedDepartmentName by rememberSaveable { mutableStateOf(departmentName) }
    var editedImageUrl by rememberSaveable { mutableStateOf(departmentState?.imageUrl ?: "") }
    var showDeleteConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    var showInviteDialog by rememberSaveable { mutableStateOf(false) }
    var inviteEmail by rememberSaveable { mutableStateOf("") }
    var inviteErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showRemoveDialog by rememberSaveable { mutableStateOf(false) } // New state for remove dialog
    val authRepository: AuthRepository = remember { FirebaseAuthRepository() }

    // State to hold the list of users in the department
    val deptUsers by departmentViewModel.getDeptUsersByDepartmentId(departmentId)
        .collectAsState(initial = emptyList())
    var selectedUsersToRemove = remember { mutableStateListOf<String>() } // Emails of users to remove

    val onTaskCompleted: (Task, Boolean) -> Unit = { task, isCompleted ->
        taskViewModel.updateTaskCompletion(task, isCompleted)
    }

    val getAssignedUsersForTask: (Int) -> List<String> = remember {
        { taskId ->
            // Use taskViewModel to get the assigned users.
            val assignedUsers = mutableStateOf<List<String>>(emptyList()) // Use a State to hold the result

            coroutineScope.launch { // Launch a coroutine
                //  Use .firstOrNull() to convert the Flow to a List
                val result = taskViewModel.getAssignedUsersForTask(taskId).firstOrNull()?.map { it.userEmail } ?: emptyList()
                assignedUsers.value = result // Update the State
            }
            assignedUsers.value //return the value of the state
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
                    text = "Forum",
                    style = typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Box(contentAlignment = Alignment.CenterEnd) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Invite People") },
                            onClick = {
                                showInviteDialog = true
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove People") }, // New menu item
                            onClick = {
                                showRemoveDialog = true
                                showMenu = false
                                selectedUsersToRemove.clear() // Clear previous selections
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Department") },
                            onClick = {
                                showEditDialog = true
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Department") },
                            onClick = {
                                showDeleteConfirmationDialog = true
                                showMenu = false
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Button(
                onClick = {
                    if (selectedTab == "Issues") {
                        // Navigate to AddEditIssueScreen for a new issue
                        navController.navigate(Screen.AddEditIssue.createRoute(departmentId))
                    } else if (selectedTab == "Tasks") {
                        // Navigate to AddEditTaskScreen for a new task
                        navController.navigate(Screen.AddEditTask.createRoute(departmentId))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 36.dp) // Adjust as needed
                    .border(BorderStroke(1.dp, Color.Gray), RoundedCornerShape(50.dp)), // Thinner border
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent, // Keep transparent
                    contentColor = Color.Black // Keep black text
                )
            ) {
                // Dynamically change button text based on selected tab
                Text(if (selectedTab == "Issues") "Add New Issue" else "Add New Task")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Box(modifier = Modifier.height(240.dp)) {
                AsyncImage(
                    model = departmentState?.imageUrl,
                    contentDescription = departmentName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        text = departmentName,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        style = typography.headlineMedium.copy(color = Color.White),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                TabbedContentSection(
                    issues = issues,
                    tasks = tasks,
                    currentSelectedTab = selectedTab,
                    onTabSelected = { newTab ->
                        departmentViewModel.selectTab(newTab)
                    },
                    departmentId = departmentId,
                    onTaskCompletedChanged = onTaskCompleted,
                    onNavigateToEditIssue = { issue ->
                        navController.navigate(Screen.AddEditIssue.createRoute(departmentId, issue.issueId))
                    },
                    onNavigateToTaskDetail = { task ->
                        navController.navigate(Screen.TaskDetail.createRoute(task.taskId))
                    },
                    currentUserEmail = currentUserEmail, // Pass the current user's email
                    assignedUsersForTask = getAssignedUsersForTask,
                    modifier = Modifier.fillMaxHeight()
                )
            }

            if (showInviteDialog) {
                AlertDialog(
                    onDismissRequest = { showInviteDialog = false },
                    title = { Text("Invite People to ${departmentState?.departmentName}") },
                    text = {
                        Column {
                            TextField(
                                value = inviteEmail,
                                onValueChange = {
                                    inviteEmail = it
                                    inviteErrorMessage = null
                                },
                                label = { Text("Enter Email to Invite") },
                                isError = inviteErrorMessage != null,
                                supportingText = {
                                    if (inviteErrorMessage != null) {
                                        Text(
                                            text = inviteErrorMessage!!,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (inviteEmail.isNotBlank()) {
                                    val trimmedEmail = inviteEmail.trim()
                                    if (trimmedEmail == departmentState?.creatorEmail) {
                                        inviteErrorMessage = "Cannot invite the department creator."
                                    } else if (deptUsers.any { it.userEmail == trimmedEmail }) {
                                        inviteErrorMessage = "$trimmedEmail is already in the department."
                                    } else {
                                        coroutineScope.launch {
                                            val result = authRepository.getUserByEmail(trimmedEmail)
                                            result.onSuccess { userProfile ->
                                                if (userProfile?.email != null) {
                                                    departmentViewModel.insertDeptUser(
                                                        departmentId,
                                                        userProfile.email
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        "${userProfile.email} invited to the department.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    inviteEmail = ""
                                                    showInviteDialog = false
                                                } else {
                                                    inviteErrorMessage = "User with this email not found."
                                                }
                                            }.onFailure { error ->
                                                inviteErrorMessage = "Failed to check user: ${error.message}"
                                            }
                                        }
                                    }
                                } else {
                                    inviteErrorMessage = "Please enter an email address."
                                }
                            },
                            enabled = inviteEmail.isNotBlank()
                        ) {
                            Text("Invite")
                        }
                    },
                    dismissButton = {
                        Button(onClick = {
                            showInviteDialog = false
                            inviteEmail = "" // Clear email on cancel
                            inviteErrorMessage = null // Clear any previous error
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showRemoveDialog) {
                AlertDialog(
                    onDismissRequest = { showRemoveDialog = false },
                    title = { Text("Remove People from ${departmentState?.departmentName}") },
                    text = {
                        Column {
                            if (deptUsers.isEmpty()) {
                                Text("No users in this department.")
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxHeight(0.7f)) {
                                    items(deptUsers.filter { it.userEmail != departmentState?.creatorEmail }) { deptUser ->
                                        // Assuming DeptUser has a userEmail property
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (selectedUsersToRemove.contains(deptUser.userEmail)) {
                                                        selectedUsersToRemove.remove(deptUser.userEmail)
                                                    } else {
                                                        selectedUsersToRemove.add(deptUser.userEmail)
                                                    }
                                                }
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = selectedUsersToRemove.contains(deptUser.userEmail),
                                                onCheckedChange = { isChecked ->
                                                    if (isChecked) {
                                                        selectedUsersToRemove.add(deptUser.userEmail)
                                                    } else {
                                                        selectedUsersToRemove.remove(deptUser.userEmail)
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(deptUser.userEmail) // Display the user's email
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
                                selectedUsersToRemove.forEach { emailToRemove ->
                                    departmentViewModel.deleteDeptUser(departmentId, emailToRemove)
                                }
                                selectedUsersToRemove.clear()
                                showRemoveDialog = false
                                Toast.makeText(
                                    context,
                                    "Selected users removed from the department.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            enabled = selectedUsersToRemove.isNotEmpty()
                        ) {
                            Text("Remove")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showRemoveDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showEditDialog) {
                AlertDialog(
                    onDismissRequest = { showEditDialog = false },
                    title = { Text("Edit Department") },
                    text = {
                        Column {
                            TextField(
                                value = editedDepartmentName,
                                onValueChange = { editedDepartmentName = it },
                                label = { Text("Department Name") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(
                                value = editedImageUrl,
                                onValueChange = { editedImageUrl = it },
                                label = { Text("Image URL (Optional)") }
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (editedDepartmentName.isNotBlank() && departmentState?.creatorEmail == currentUserEmail) {
                                    departmentState?.let {
                                        val updatedDepartment = it.copy(
                                            departmentName = editedDepartmentName.trim(),
                                            imageUrl = editedImageUrl.trim()
                                        )
                                        departmentViewModel.updateDepartment(updatedDepartment)
                                    }
                                    showEditDialog = false
                                } else if (editedDepartmentName.isBlank()) {
                                    coroutineScope.launch {
                                        Toast.makeText(context, "Department name cannot be empty.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    coroutineScope.launch {
                                        Toast.makeText(context, "Only Department Creator can Edit.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = editedDepartmentName.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showEditDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        if (showDeleteConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmationDialog = false },
                title = { Text("Confirm Delete") },
                text = { Text("Are you sure you want to delete this department?") },
                confirmButton = {
                    Button(
                        onClick = {
                            if (departmentState?.creatorEmail == currentUserEmail) {
                                departmentState?.let { departmentViewModel.deleteDepartment(it) }
                                navController.popBackStack()
                                showDeleteConfirmationDialog = false
                            } else {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Only Department Creator can Delete.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    Button(onClick = { showDeleteConfirmationDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun TabbedContentSection(
    issues: List<Issue>,
    tasks: List<Task>,
    currentSelectedTab: String,
    onTabSelected: (String) -> Unit,
    departmentId: Int, // Keep needed params
    onTaskCompletedChanged: (Task, Boolean) -> Unit,
    onNavigateToEditIssue: (Issue) -> Unit,
    onNavigateToTaskDetail: (Task) -> Unit, // Receive needed callbacks
    currentUserEmail: String?,  // Receive the user email
    assignedUsersForTask: (Int) -> List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .border(BorderStroke(1.dp, Color.Gray), RoundedCornerShape(50.dp)) // Thinner border
                .height(48.dp)
                .clip(RoundedCornerShape(50.dp))
                .fillMaxWidth()
        )
        {
            @Composable
            fun RowScope.TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .background(if (isSelected) Color.LightGray.copy(alpha = 0.5f) else Color.Transparent)
                        .clickable(onClick = onClick)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp) // Smaller check
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(text, style = typography.bodyMedium) // Use theme typography
                    }
                }
            }

            // Buttons use currentSelectedTab for state, onTabSelected for callback
            TabButton(text = "Issues", isSelected = currentSelectedTab == "Issues") { onTabSelected("Issues") }
            TabButton(text = "Tasks", isSelected = currentSelectedTab == "Tasks") { onTabSelected("Tasks") }
        }

        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
        ) {
            when (currentSelectedTab) {
                "Issues" -> IssueList(
                    issues = issues,
                    onNavigateToEdit = onNavigateToEditIssue,
                    // Pass modifier to allow filling the Box
//                    modifier = Modifier.fillMaxSize() // *** MODIFIED ***
                )
                "Tasks" -> TaskList(
                    tasks = tasks,
                    onNavigateToDetail = onNavigateToTaskDetail, // Use passed callback
                    onTaskCompletedChanged = onTaskCompletedChanged,
                    currentUserEmail = currentUserEmail, // Pass it down
                    assignedUsersForTask = assignedUsersForTask,
                    // Pass modifier to allow filling the Box
//                    modifier = Modifier.fillMaxSize() // *** MODIFIED ***
                )
            }
        }
    }
}

@Composable
fun IssueList(
    issues: List<Issue>,
    onNavigateToEdit: (Issue) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 80.dp
    if (issues.isEmpty()) {
        Text(
            text = "No issues reported yet. Tap 'Add New Issue' to create one.",
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = typography.bodyMedium
        )
    } else {
        Column(modifier = modifier) {
            issues.forEach { issue ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable { onNavigateToEdit(issue) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = issue.issueTitle,
                            style = typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = issue.issueDescription,
                            style = typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = Color.LightGray.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun TaskList(
    tasks: List<Task>,
    onNavigateToDetail: (Task) -> Unit,
    onTaskCompletedChanged: (Task, Boolean) -> Unit,
    currentUserEmail: String?, // Add current user's email as a parameter
    assignedUsersForTask: (Int) -> List<String>,
    modifier: Modifier = Modifier // *** MODIFIED *** Accept modifier
) {
    val itemHeight = 80.dp
    if (tasks.isEmpty()) {
        // Display this text when there are no tasks
        Text(
            text = "No tasks assigned yet. Tap 'Add New Task' to create one.", // <-- This message is for empty tasks
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = typography.bodyMedium // Ensure MaterialTheme is imported
        )
    } else {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
        ) {
            tasks.forEach { task ->
                val isCreatorOrAssigned = remember(currentUserEmail) {
                    currentUserEmail != null && (task.creatorEmail == currentUserEmail ||
                            assignedUsersForTask(task.taskId).contains(currentUserEmail))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = task.isCompleted,
                        onCheckedChange = if (isCreatorOrAssigned) { isChecked ->
                            onTaskCompletedChanged(task, isChecked)
                        } else null,
                        enabled = isCreatorOrAssigned,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToDetail(task) }
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.Center
                    ){
                        Text(
                            text = task.taskTitle,
                            style = typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = task.taskDescription.ifBlank { "" },
                            style = typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(2.dp),
                    thickness = 1.dp,
                    color = Color.LightGray
                )
            }
        }
    }
}