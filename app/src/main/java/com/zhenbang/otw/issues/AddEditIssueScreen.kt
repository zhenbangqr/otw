package com.zhenbang.otw.issues // Or wherever you placed IssueViewModel

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel // Import viewModel
import androidx.navigation.NavController
import com.zhenbang.otw.database.Issue // Import your Issue entity

@Composable
fun AddEditIssueScreen(
    navController: NavController,
    departmentId: Int,
    issueViewModel: IssueViewModel, // Pass the IssueViewModel
    issueId: Int // -1 for new issue, otherwise ID of issue to edit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    // Observe the issue to edit based on issueId
    // Use derivedStateOf to prevent potential recomposition loops if issueToEdit changes reference but not content
    val issueToEditState by issueViewModel.getIssueById(issueId).collectAsState(initial = null)
    val issueToEdit = remember(issueToEditState) { issueToEditState }


    // Pre-fill fields if editing an existing issue
    LaunchedEffect(issueToEdit) {
        // Only pre-fill if issueToEdit is not null and issueId indicates editing
        if (issueToEdit != null && issueId != -1) {
            title = issueToEdit.issueTitle
            description = issueToEdit.issueDescription
        } else {
            // Reset fields if navigating back to 'Add New' from 'Edit' or if issue data is cleared
            title = ""
            description = ""
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp) // Adjust padding as needed
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Go Back")
                }
                Text(
                    text = if (issueId == -1) "New Issue" else "Edit Issue",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 16.dp)
                )
                // Show delete button only when editing
                if (issueId != -1) {
                    IconButton(onClick = {
                        issueToEdit?.let { issueToDelete ->
                            issueViewModel.deleteIssue(issueToDelete)
                            navController.popBackStack() // Go back after delete
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Issue", tint = Color.Red)
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (title.isNotBlank()) { // Description can be optional, adjust if needed
                    val currentTimestamp = System.currentTimeMillis()
                    val issue = Issue(
                        // If editing, use the existing issueId, otherwise 0 for Room to generate
                        issueId = if (issueId != -1) issueId else 0,
                        departmentId = departmentId,
                        issueTitle = title,
                        issueDescription = description,
                        // Let ViewModel handle timestamp logic during upsert
                        // Provide existing timestamp for comparison if editing
                        creationTimestamp = issueToEdit?.creationTimestamp ?: currentTimestamp
                    )
                    issueViewModel.upsertIssue(issue) // Use the upsert method
                    navController.popBackStack() // Go back after saving
                }
                // Optional: Add user feedback (e.g., Toast/Snackbar) if fields are blank
            }) {
                Icon(Icons.Filled.Check, contentDescription = "Save Issue")
            }
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                // Style similar to AddEditTaskScreen
                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    // Add other color customizations if needed
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Issue Title", fontWeight = FontWeight.Bold, fontSize = 28.sp ) }, // Adjust size
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    colors = textFieldColors,
                    maxLines = 3 // Example: Limit title lines
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) // Separator

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Describe the issue...", fontSize = 18.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f), // Allow description to take remaining space
                    textStyle = TextStyle(
                        fontSize = 18.sp
                    ),
                    colors = textFieldColors
                )
            }
        }
    )
}