package com.zhenbang.otw.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.data.local.Issue
import com.zhenbang.otw.ui.viewmodel.IssueViewModel

@Composable
fun AddEditIssueScreen(
    navController: NavController,
    departmentId: Int,
    issueViewModel: IssueViewModel,
    issueId: Int
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
    val issueToEditState by issueViewModel.getIssueById(issueId).collectAsState(initial = null)
    val issueToEdit = remember(issueToEditState) { issueToEditState }

    LaunchedEffect(issueToEdit) {
        if (issueToEdit != null && issueId != -1) {
            title = issueToEdit.issueTitle
            description = issueToEdit.issueDescription
        } else {
            title = ""
            description = ""
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp)
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
                if (issueId != -1) {
                    IconButton(onClick = {
                        issueToEdit?.let { issueToDelete ->
                            issueViewModel.deleteIssue(issueToDelete)
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Issue", tint = Color.Red)
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (title.isNotBlank()) {
                    val currentTimestamp = System.currentTimeMillis()
                    val issue = Issue(
                        issueId = if (issueId != -1) issueId else 0,
                        departmentId = departmentId,
                        issueTitle = title,
                        issueDescription = description,

                        creationTimestamp = issueToEdit?.creationTimestamp ?: currentTimestamp,
                        creatorEmail = currentUserEmail
                    )
                    issueViewModel.upsertIssue(issue)
                    navController.popBackStack()
                }
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
                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Issue Title", fontWeight = FontWeight.Bold, fontSize = 28.sp ) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    colors = textFieldColors,
                    maxLines = 3
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Describe the issue...", fontSize = 18.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    textStyle = TextStyle(
                        fontSize = 18.sp
                    ),
                    colors = textFieldColors
                )
            }
        }
    )
}