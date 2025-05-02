package com.zhenbang.otw.departments

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn // Keep LazyColumn import
import androidx.compose.foundation.lazy.items // Keep items import for LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel // Keep viewModel import
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.database.Department
import kotlinx.coroutines.launch
import android.widget.Toast

// Composable responsible for displaying the workspace content (departments)
@Composable
fun WorkspaceContent(
    modifier: Modifier = Modifier,
    departmentViewModel: DepartmentViewModel, // Needs the ViewModel
    isGridView: Boolean, // Pass grid/list state
    onNavigateToDepartmentDetails: (departmentId: Int, departmentName: String) -> Unit, // Navigation callback
    isSortAscending: Boolean // Pass sorting state
) {
    val context = LocalContext.current
    // Observe user-specific departments from ViewModel
    val userDepartments by departmentViewModel.userDepartments.collectAsState()
    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email // Get email
    val coroutineScope = rememberCoroutineScope() // Scope for dialog actions

    // Dialog states managed here
    var showAddDeptDialog by rememberSaveable { mutableStateOf(false) }
    var departmentName by rememberSaveable { mutableStateOf("") }
    var imageUrl by rememberSaveable { mutableStateOf("") }

    // --- Sorting Logic ---
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

    Box(modifier = modifier.fillMaxSize()) { // Use Box to allow FAB positioning
        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .padding(8.dp)
            ) {
                items(sortedDepartments) { department ->
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable {
                                onNavigateToDepartmentDetails(department.departmentId, department.departmentName)
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
                    .padding(8.dp)
            ) {
                items(sortedDepartments) { department ->
                    Card(
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable {
                                onNavigateToDepartmentDetails(department.departmentId, department.departmentName)
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

        // FAB for adding departments - positioned within the Box
        FloatingActionButton(
            onClick = { showAddDeptDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd) // Position FAB
                .padding(16.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Department")
        }
    }


    // Add Department Dialog (remains the same)
    if (showAddDeptDialog) {
        AlertDialog(
            onDismissRequest = { showAddDeptDialog = false },
            title = { Text("Add Department") },
            text = {
                Column {
                    TextField(value = departmentName, onValueChange = { departmentName = it }, label = { Text("Department Name") })
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(value = imageUrl, onValueChange = { imageUrl = it }, label = { Text("Image URL (Optional)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val finalName = departmentName.trim()
                    val finalImageUrl = imageUrl.trim().ifEmpty { null } // Use null if blank
                    if (finalName.isNotEmpty()) {
                        if (currentUserEmail != null) {
                            coroutineScope.launch { // Use coroutineScope
                                departmentViewModel.insertDepartment(
                                    departmentName = finalName,
                                    imageUrl = finalImageUrl,
                                    creatorEmail = currentUserEmail // Pass email
                                )
                                departmentName = "" // Clear fields after adding
                                imageUrl = ""
                                showAddDeptDialog = false // Close dialog
                            }
                        } else {
                            Log.e("WorkspaceContent", "Cannot add department: User email is null.")
                            coroutineScope.launch {
                                Toast.makeText(context, "Error: Could not get user email.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        coroutineScope.launch {
                            Toast.makeText(context, "Department name cannot be empty.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                    enabled = departmentName.isNotBlank() // Enable only if name is not blank
                ) { Text("Add") }
            },
            dismissButton = { Button(onClick = { showAddDeptDialog = false }) { Text("Cancel") } }
        )
    }
}

// Extracted Grid Item
@Composable
private fun DepartmentGridItem(department: Department, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent) // Or use themed color
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp), // Add padding inside card
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = department.imageUrl,
                contentDescription = department.departmentName,
                modifier = Modifier
                    .size(100.dp) // Adjust size as needed
                    .clip(RoundedCornerShape(12.dp))
                // Add placeholder/error handling if needed
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = department.departmentName,
                fontSize = 14.sp, // Adjust font size
                maxLines = 1 // Ensure single line for grid consistency
            )
        }
    }
}

// Extracted List Item
@Composable
private fun DepartmentListItem(department: Department, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(vertical = 4.dp) // Adjust vertical padding
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent) // Or use themed color
    ) {
        Row(
            modifier = Modifier.padding(8.dp), // Padding inside the row
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = department.imageUrl,
                contentDescription = department.departmentName,
                modifier = Modifier
                    .size(60.dp) // Adjust size
                    .clip(RoundedCornerShape(8.dp))
                // Add placeholder/error handling if needed
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = department.departmentName,
                fontSize = 16.sp,
                style = MaterialTheme.typography.titleMedium // Use theme typography
            )
        }
    }
}