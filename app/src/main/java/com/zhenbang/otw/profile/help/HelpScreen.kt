package com.zhenbang.otw.profile.help // Or your preferred package structure

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zhenbang.otw.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import java.net.URLEncoder // <-- Import URL Encoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    navController: NavController
) {
    val context = LocalContext.current
    var subject by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    var showConfirmationDialog by remember { mutableStateOf(false) }

    val supportEmail = "tiongfattsiong02@gmail.com"
    val TAG = "HelpScreen"

    fun sendEmailViaClient() {
        if (description.isBlank()) {
            Toast.makeText(context, "Please describe your issue.", Toast.LENGTH_SHORT).show()
            return
        }
        if (isSending) return
        isSending = true

        val currentUser = FirebaseAuth.getInstance().currentUser
        val userInfo =
            "User ID: ${currentUser?.uid ?: "Not Logged In"}\nEmail: ${currentUser?.email ?: "N/A"}\n\n"
        val appVersionInfo = try {
            "App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        } catch (e: Exception) {
            "App Version: N/A"
        }
        val deviceInfo =
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid Version: ${Build.VERSION.RELEASE}\n$appVersionInfo\n\n---\n\n"

        val finalSubject = subject.trim().ifBlank { "Feedback" }
        val finalBody = userInfo + deviceInfo + description.trim()

        try {
            val encodedSubject = URLEncoder.encode(finalSubject, "UTF-8")
            val encodedBody = URLEncoder.encode(finalBody, "UTF-8")
            val mailtoUriString = "mailto:$supportEmail?subject=$encodedSubject&body=$encodedBody"
            val mailtoUri = Uri.parse(mailtoUriString)
            val emailIntent = Intent(Intent.ACTION_SENDTO, mailtoUri)

            context.startActivity(emailIntent)

            subject = ""
            description = ""
            showConfirmationDialog = true

            Toast.makeText(context, "Opening email app...", Toast.LENGTH_SHORT).show()

        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No email app found.", e)
            Toast.makeText(context, "No email app found.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating/starting email intent", e)
            Toast.makeText(context, "Could not open email app.", Toast.LENGTH_LONG).show()
        } finally {
            isSending = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Support") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { // Navigates back
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Contact Support",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Please describe the issue you are experiencing. Include steps to reproduce it if possible. Your user and device information will be automatically included.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = isSending
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Describe your issue *") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp),
                readOnly = isSending,
                isError = description.isBlank() && isSending
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = ::sendEmailViaClient,
                enabled = !isSending,
                modifier = Modifier.align(Alignment.End)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        Modifier.size(ButtonDefaults.IconSize),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                }
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(if (isSending) "Preparing..." else "Send Feedback")
            }
        } // End Column
    } // End Scaffold

    // --- Confirmation Dialog ---
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false }, // Allow dismissing by clicking outside
            title = { Text("Feedback Submitted") },
            text = { Text("Thank you for your feedback! We will reply within three working days.") },
            confirmButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}
