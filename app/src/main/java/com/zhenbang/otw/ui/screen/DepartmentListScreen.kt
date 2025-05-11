package com.zhenbang.otw.ui.screen

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.data.local.Department
import com.zhenbang.otw.ui.viewmodel.DepartmentViewModel
import kotlinx.coroutines.launch

@Composable
fun WorkspaceContent(
    modifier: Modifier = Modifier,
    departmentViewModel: DepartmentViewModel,
    isGridView: Boolean,
    onNavigateToDepartmentDetails: (departmentId: Int, departmentName: String) -> Unit,
    isSortAscending: Boolean
) {
    val context = LocalContext.current

    val userDepartments by departmentViewModel.userDepartments.collectAsState()
    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
    val coroutineScope = rememberCoroutineScope()

    var showAddDeptDialog by rememberSaveable { mutableStateOf(false) }
    var departmentName by rememberSaveable { mutableStateOf("") }
    var imageUrl by rememberSaveable { mutableStateOf("") }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val gridColumnCount = if (screenWidthDp >= 600) 6 else 3

    val sortedDepartments = remember(userDepartments, isSortAscending) {
        Log.d("WorkspaceContent", "Recalculating sorted list. Asc: $isSortAscending")
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

    Box(modifier = modifier.fillMaxSize()) {
        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumnCount),
                modifier = Modifier
                    .padding(8.dp)
            ) {
                items(sortedDepartments) { department ->
                    Card(
                        modifier = Modifier
                            .padding(12.dp)
                            .clickable {
                                onNavigateToDepartmentDetails(
                                    department.departmentId,
                                    department.departmentName
                                )
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = department.imageUrl,
                                    contentDescription = department.departmentName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                            Text(
                                text = department.departmentName,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(8.dp)
            ) {
                items(sortedDepartments) { department ->
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable {
                                onNavigateToDepartmentDetails(
                                    department.departmentId,
                                    department.departmentName
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

        FloatingActionButton(
            onClick = { showAddDeptDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Department")
        }
    }

    if (showAddDeptDialog) {
        AlertDialog(
            onDismissRequest = { showAddDeptDialog = false },
            title = { Text("Add Department") },
            text = {
                Column {
                    TextField(
                        value = departmentName,
                        onValueChange = { departmentName = it },
                        label = { Text("Department Name") })
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("Image URL (Optional)") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = departmentName.trim()
                        val finalImageUrl = imageUrl.trim().ifEmpty { null }
                        if (finalName.isNotEmpty()) {
                            if (currentUserEmail != null) {
                                coroutineScope.launch {
                                    departmentViewModel.insertDepartment(
                                        departmentName = finalName,
                                        imageUrl = finalImageUrl,
                                        creatorEmail = currentUserEmail
                                    )
                                    departmentName = ""
                                    imageUrl = ""
                                    showAddDeptDialog = false
                                }
                            } else {
                                Log.e(
                                    "WorkspaceContent",
                                    "Cannot add department: User email is null."
                                )
                                coroutineScope.launch {
                                    Toast.makeText(
                                        context,
                                        "Error: Could not get user email.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                Toast.makeText(
                                    context,
                                    "Department name cannot be empty.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = departmentName.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = { Button(onClick = { showAddDeptDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DepartmentGridItem(department: Department, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = department.imageUrl,
                contentDescription = department.departmentName,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = department.departmentName,
                fontSize = 14.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DepartmentListItem(department: Department, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = department.imageUrl,
                contentDescription = department.departmentName,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = department.departmentName,
                fontSize = 16.sp,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}