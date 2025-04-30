package com.zhenbang.otw.departments

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.zhenbang.otw.database.Department
import com.zhenbang.otw.database.Task
import com.zhenbang.otw.issues.AddEditIssueScreen
import com.zhenbang.otw.issues.IssueViewModel
import com.zhenbang.otw.tasks.AddEditTaskScreen
import com.zhenbang.otw.tasks.TaskDetailScreen
import com.zhenbang.otw.tasks.TaskViewModel
import com.zhenbang.otw.ui.theme.OnTheWayTheme
import com.zhenbang.otw.database.Issue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.saveable.rememberSaveable

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
    val departments = departmentViewModel.allDepartments.collectAsState(initial = emptyList())
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var departmentName by rememberSaveable { mutableStateOf("") }
    var imageUrl by rememberSaveable { mutableStateOf("") }
    var isGridView by rememberSaveable { mutableStateOf(true) }

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
                horizontalArrangement = Arrangement.End
            ) {
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
                items(departments.value) { department ->
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
                items(departments.value) { department ->
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
                    TextField(
                        value = departmentName,
                        onValueChange = { departmentName = it },
                        label = { Text("Department Name") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("Image URL (Optional)") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (departmentName.isNotEmpty()) {
                        departmentViewModel.insertDepartment(
                            departmentName = departmentName,
                            imageUrl = imageUrl.trim()
                        )
                        departmentName = ""
                        imageUrl = ""
                        showDialog = false
                    }
                }) {
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

    // Task ViewModel and Tasks
    val taskViewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory(context))
    val tasks by taskViewModel.getTasksByDepartmentId(departmentId)
        .collectAsState(initial = emptyList())

    // Issue ViewModel and Issues
    val issueViewModel: IssueViewModel = viewModel(factory = IssueViewModel.Factory(context))
    val issues by issueViewModel.getIssuesByDepartmentId(departmentId)
        .collectAsState(initial = emptyList()) // Collect issues

    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var editedDepartmentName by rememberSaveable { mutableStateOf(departmentName) }
    var editedImageUrl by rememberSaveable { mutableStateOf(departmentState?.imageUrl ?: "") }
    var showDeleteConfirmationDialog by rememberSaveable { mutableStateOf(false) }

    val onTaskCompleted: (Task, Boolean) -> Unit = { task, isCompleted ->
        taskViewModel.updateTask(task, isCompleted)
    }

//    LaunchedEffect(departmentState) {
//        departmentState?.let {
//            if (editedDepartmentName != it.departmentName) { // Avoid unnecessary updates if only state reference changed
//                editedDepartmentName = it.departmentName
//            }
//            if (editedImageUrl != (it.imageUrl ?: "")) {
//                editedImageUrl = it.imageUrl ?: ""
//            }
//        }
//    }

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
                Text(if (selectedTab == "Issues") "Add New Issue" else "Add New Task")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                    onNavigateToEditTask = { task ->
                        navController.navigate(Screen.AddEditTask.createRoute(departmentId, task.taskId))
                    },
                    modifier = Modifier.fillMaxHeight()
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
                                if (editedDepartmentName.isNotBlank()) {
                                    departmentState?.let {
                                        val updatedDepartment = it.copy(
                                            departmentName = editedDepartmentName.trim(),
                                            imageUrl = editedImageUrl.trim()
                                        )
                                        departmentViewModel.updateDepartment(updatedDepartment)
                                    }
                                    showEditDialog = false
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
                            departmentState?.let { departmentViewModel.deleteDepartment(it) }
                            navController.popBackStack()
                            showDeleteConfirmationDialog = false
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
    onNavigateToTaskDetail: (Task) -> Unit,
    onNavigateToEditTask: (Task) -> Unit,
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
                        Text(text, style = MaterialTheme.typography.bodyMedium) // Use theme typography
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

                )
                "Tasks" -> TaskList(
                    tasks = tasks,
                    onNavigateToDetail = onNavigateToTaskDetail, // Use passed callback
                    onEditTask = onNavigateToEditTask,         // Use passed callback
                    onTaskCompletedChanged = onTaskCompletedChanged,
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
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
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
                    // Optional: Add explicit edit or delete icons if preferred over clicking row
                    /*
                    IconButton(onClick = { onNavigateToEdit(issue) }) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit Issue", modifier = Modifier.size(20.dp))
                    }
                    */
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
    onEditTask: (Task) -> Unit,
    onTaskCompletedChanged: (Task, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 80.dp
    if (tasks.isEmpty()) {
        Text(
            text = "No tasks assigned yet. Tap 'Add New Task' to create one.",
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    } else {
        Column(modifier = modifier) {
            tasks.forEach { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = task.isCompleted,
                        onCheckedChange = { isChecked ->
                            onTaskCompletedChanged(task, isChecked)
                        }
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
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { onEditTask(task) }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit Task",
                            modifier = Modifier.size(20.dp))
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



@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    OnTheWayTheme {
        DepartmentDetailsScreen(
            navController = rememberNavController(),
            departmentId = 1,
            departmentName = "Sample Department"
        )
    }
}