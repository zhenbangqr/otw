package com.zhenbang.otw.ui.screen // Or your UI package

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.zhenbang.otw.data.local.SummaryHistoryEntity
import com.zhenbang.otw.ui.viewmodel.HistoryViewModel
import com.zhenbang.otw.ui.viewmodel.HistoryViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
) {

    val context = LocalContext.current
    val application = context.applicationContext as Application // Get Application context

    // Provide the factory when calling viewModel()
    val historyViewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModelFactory(application) // Create the ViewModel instance
    )

    val historyList by historyViewModel.summaryHistory.collectAsState()

    // --- State for Dialogs ---
    var showDeleteConfirmationDialog by remember { mutableStateOf<SummaryHistoryEntity?>(null) }
    var itemToEdit by remember { mutableStateOf<SummaryHistoryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("AI Summary History") }, navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            })
        }) { paddingValues ->
        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No summary history found.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = historyList, key = { it.id }) { historyItem ->
                    HistoryItemCard(
                        item = historyItem,
                        onEditClick = { itemToEdit = historyItem }, // Pass item to edit state
                        onDeleteClick = {
                            showDeleteConfirmationDialog = historyItem
                        } // Pass item to delete state
                    )
                }
            }
        }

        // --- Dialog Composable Calls ---
        // Delete Confirmation Dialog
        if (showDeleteConfirmationDialog != null) {
            DeleteConfirmationDialog(
                itemToDelete = showDeleteConfirmationDialog!!,
                onConfirmDelete = {
                    historyViewModel.deleteHistoryItem(showDeleteConfirmationDialog!!)
                    showDeleteConfirmationDialog = null // Close dialog
                },
                onDismiss = { showDeleteConfirmationDialog = null } // Close dialog
            )
        }

        // Edit Summary Dialog
        if (itemToEdit != null) {
            EditSummaryDialog(itemToEdit = itemToEdit!!, onConfirmEdit = { editedText ->
                historyViewModel.updateHistoryItemSummary(itemToEdit!!, editedText)
                itemToEdit = null // Close dialog
            }, onDismiss = { itemToEdit = null } // Close dialog
            )
        }
    }
}


@Composable
fun HistoryItemCard(
    item: SummaryHistoryEntity, onEditClick: () -> Unit, // Callback for edit button
    onDeleteClick: () -> Unit // Callback for delete button)
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Date Range
            Text(
                "Timeframe: ${dateFormatter.format(Date(item.startDateMillis))} - ${
                    dateFormatter.format(
                        Date(item.endDateMillis)
                    )
                }", style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Saved Time
            Text(
                "Saved: ${dateFormatter.format(Date(item.savedTimestampMillis))} ${
                    timeFormatter.format(
                        Date(item.savedTimestampMillis)
                    )
                }",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            // AI Summary
            Text(
                "Summary:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = item.aiSummaryResponse,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5, // Show limited lines, expandable if needed
                overflow = TextOverflow.Ellipsis
            )
            // --- NEW: Edit and Delete Buttons ---
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End // Align buttons to the end
            ) {
                // Edit Button
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Summary")
                }
                Spacer(modifier = Modifier.width(8.dp)) // Space between buttons
                // Delete Button
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete Summary",
                        tint = MaterialTheme.colorScheme.error // Use error color for delete
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    itemToDelete: SummaryHistoryEntity, // Pass the item for context if needed, or just ID
    onConfirmDelete: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Deletion") },
        text = { Text("Are you sure you want to delete the summary for this timeframe?") },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) // Destructive action color
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        })
}

@Composable
fun EditSummaryDialog(
    itemToEdit: SummaryHistoryEntity,
    onConfirmEdit: (editedText: String) -> Unit,
    onDismiss: () -> Unit
) {
    // State for the TextField, keyed to the item to reset when item changes
    var editedSummaryText by rememberSaveable(itemToEdit) {
        mutableStateOf(itemToEdit.aiSummaryResponse)
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit Summary") }, text = {
        // Use a TextField for editing
        OutlinedTextField(
            value = editedSummaryText,
            onValueChange = { editedSummaryText = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp), // Allow vertical expansion
            label = { Text("AI Summary") })
    }, confirmButton = {
        Button(
            onClick = { onConfirmEdit(editedSummaryText) },
            // Enable Save only if text is not blank (optional)
            enabled = editedSummaryText.isNotBlank()
        ) {
            Text("Save")
        }
    }, dismissButton = {
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
    })
}
