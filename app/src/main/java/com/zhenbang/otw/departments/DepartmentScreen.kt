package com.zhenbang.otw.departments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.zhenbang.otw.tasks.AddEditTaskScreen
import com.zhenbang.otw.tasks.TaskDetailScreen
import com.zhenbang.otw.tasks.TaskViewModel
import com.zhenbang.otw.ui.theme.OnTheWayTheme

sealed class Screen(val route: String) {
    object DepartmentList : Screen("department_list")
    object DepartmentDetails : Screen("department_details/{departmentId}/{departmentName}") {
        fun createRoute(departmentId: Int, departmentName: String) =
            "department_details/$departmentId/${
                departmentName.replace(
                    "/",
                    "%2F"
                )
            }" // encode slashes
    }

    object TaskDetail : Screen("task_details/{taskId}") {
        fun createRoute(taskId: Int) = "task_details/$taskId"
    }

    object AddEditTask : Screen("add_edit_task/{departmentId}/{taskId}") {
        fun createRoute(departmentId: Int) = "add_edit_task/$departmentId/-1"
        fun createRoute(departmentId: Int, taskId: Int) = "add_edit_task/$departmentId/$taskId"
    }
}

@Composable
fun DepartmentNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.DepartmentList.route) {
        composable(Screen.DepartmentList.route) {
            DepartmentListScreen(navController = navController)
        }
        composable(
            route = Screen.DepartmentDetails.route,
            arguments = listOf(
                navArgument("departmentId") { type = NavType.IntType },
                navArgument("departmentName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val departmentId = backStackEntry.arguments?.getInt("departmentId") ?: 0
            val departmentName =
                backStackEntry.arguments?.getString("departmentName")?.replace("%2F", "/")
                    ?: "" // decode slashes
            DepartmentDetailsScreen(
                navController = navController,
                departmentId = departmentId,
                departmentName = departmentName,
            )
        }
        composable(
            route = Screen.TaskDetail.route,
            arguments = listOf(navArgument("taskId") { type = NavType.IntType })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getInt("taskId") ?: 0
            val context = LocalContext.current
            val taskViewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory(context))
            TaskDetailScreen(
                navController = navController,
                taskViewModel = taskViewModel,
                taskId = taskId
            )
        }
        composable(
            route = Screen.AddEditTask.route,
            arguments = listOf(
                navArgument("departmentId") { type = NavType.IntType },
                navArgument("taskId") { type = NavType.IntType })
        ) { backStackEntry ->
            val departmentId = backStackEntry.arguments?.getInt("departmentId") ?: 0
            val taskId = backStackEntry.arguments?.getInt("taskId") ?: -1
            val context = LocalContext.current
            val taskViewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory(context))
            AddEditTaskScreen(
                navController = navController,
                departmentId = departmentId,
                taskViewModel = taskViewModel,
                taskId = taskId
            )
        }
    }
}

@Composable
fun DepartmentListScreen(navController: NavController) {
    val context = LocalContext.current
    val departmentViewModel: DepartmentViewModel =
        viewModel(factory = DepartmentViewModel.Factory(context))
    val departmentsFlow = departmentViewModel.allDepartments
    var showDialog by remember { mutableStateOf(false) }
    var departmentName by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(true) }
    var isSortAscending by remember { mutableStateOf(true) }

    val sortedDepartments by departmentsFlow.collectAsState(initial = emptyList()).let { state ->
        remember(state.value, isSortAscending) {
            mutableStateOf(
                if (isSortAscending) {
                    state.value.sortedBy { it.departmentName.lowercase() }
                } else {
                    state.value.sortedByDescending { it.departmentName.lowercase() }
                }
            )
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
                            // Updated Status
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
                            // Updated Status
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
    val departmentViewModel: DepartmentViewModel =
        viewModel(factory = DepartmentViewModel.Factory(context))
    val departmentState: Department? by departmentViewModel.getDepartmentById(departmentId)
        .collectAsState(initial = null)
    val taskViewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory(context))
    val tasks by taskViewModel.getTasksByDepartmentId(departmentId)
        .collectAsState(initial = emptyList())
    var selectedTab by remember { mutableStateOf("Issues") }
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editedDepartmentName by remember { mutableStateOf(departmentName) }
    var editedImageUrl by remember { mutableStateOf(departmentState?.imageUrl ?: "") }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    val onTaskCompleted: (Task, Boolean) -> Unit = { task, isCompleted ->
        taskViewModel.updateTask(task, isCompleted)
    }

    LaunchedEffect(departmentState?.imageUrl) {
        editedImageUrl = departmentState?.imageUrl ?: ""
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
            if (selectedTab == "Issues") {
                Button(
                    onClick = { /* Issue */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 36.dp)
                        .border(BorderStroke(2.dp, Color.Gray), RoundedCornerShape(50.dp)),
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Add New Issue")
                }
            } else if (selectedTab == "Tasks") {
                Button(
                    onClick = { navController.navigate(Screen.AddEditTask.createRoute(departmentId)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 36.dp)
                        .border(BorderStroke(2.dp, Color.Gray), RoundedCornerShape(50.dp)),
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Add New Task")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Box {
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
                modifier = Modifier.padding(16.dp)
            ) {
                TabbedContentSection(
                    tasks = tasks,
                    navController = navController,
                    onTabSelected = { selectedTab = it },
                    departmentId = departmentId,
                    onTaskCompletedChanged = onTaskCompleted
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
    tasks: List<Task>,
    navController: NavController,
    onTabSelected: (String) -> Unit,
    departmentId: Int,
    onTaskCompletedChanged: (Task, Boolean) -> Unit
) {
    var selectedTab by remember { mutableStateOf("Issues") }

    Column {
        Row(
            modifier = Modifier
                .border(BorderStroke(2.dp, Color.Gray), RoundedCornerShape(50.dp))
                .height(48.dp)
                .clip(RoundedCornerShape(50.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selectedTab == "Issues") Color.LightGray.copy(alpha = 0.5f) else Color.Transparent)
                    .clickable {
                        selectedTab = "Issues"
                        onTabSelected("Issues")
                    }
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedTab == "Issues") {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text("Issues")
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selectedTab == "Tasks") Color.LightGray.copy(alpha = 0.5f) else Color.Transparent)
                    .clickable {
                        selectedTab = "Tasks"
                        onTabSelected("Tasks")
                    }
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedTab == "Tasks") {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text("Tasks")
                }
            }
        }

        Box(modifier = Modifier.padding(top = 8.dp)) {
            when (selectedTab) {
                "Issues" -> Text(text = "Issues Tab Content")
                "Tasks" -> TaskList(tasks = tasks, onNavigateToDetail = { task ->
                    navController.navigate(Screen.TaskDetail.createRoute(task.taskId))
                }, onEditTask = { task ->
                    navController.navigate(
                        Screen.AddEditTask.createRoute(
                            departmentId,
                            task.taskId
                        )
                    )
                },
                    onTaskCompletedChanged = onTaskCompletedChanged
                )
            }
        }
    }
}

@Composable
fun IssueList(issues: List<String>, onNavigateToDetail: (String) -> Unit) {
    LazyColumn {
        items(issues) { issue ->
            Text(
                text = "Issue: $issue",
                modifier = Modifier
                    .padding(16.dp)
                    .clickable { onNavigateToDetail(issue) }
            )
        }
    }
}

@Composable
fun TaskList(
    tasks: List<Task>,
    onNavigateToDetail: (Task) -> Unit,
    onEditTask: (Task) -> Unit,
    onTaskCompletedChanged: (Task, Boolean) -> Unit
) {
    LazyColumn {
        items(tasks) { task ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .clickable { onNavigateToDetail(task) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = { isChecked ->
                        onTaskCompletedChanged(task, isChecked)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.taskTitle
                    )
                    Text(
                        text = task.taskDescription
                    )
                }
                IconButton(onClick = { onEditTask(task) }) {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit Task")
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

/*
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
*/


@Preview(showBackground = true)
@Composable
fun GreetingaPreview() {
    OnTheWayTheme {
        DepartmentListScreen(
            navController = rememberNavController(),
        )
    }
}
