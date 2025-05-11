// Package declaration (adjust if you place it elsewhere)
package com.zhenbang.otw.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Use AutoMirrored for back arrow
// Remove Icons.Filled.Check import if no longer needed elsewhere
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

// Simple data class to hold language names
data class LanguageDisplay(
    val nativeName: String,
    val englishName: String // Or secondary display name
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(
    navController: NavController
) {
    val availableLanguages = listOf(
        LanguageDisplay("English", "English")
    )
    val selectedLanguageKey by remember { mutableStateOf("English") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Language") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { // Navigate back
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 8.dp) // Add some padding below the top bar
        ) {
            items(availableLanguages) { language ->
                LanguageItem(
                    languageDisplay = language,
                    isSelected = language.nativeName == selectedLanguageKey, // Compare based on a unique key/name
                    onClick = {
                        // In the future, update selectedLanguageKey state here
                        // and likely save the preference.
                        // For now, clicking does nothing.
                    }
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
fun LanguageItem(
    languageDisplay: LanguageDisplay,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Radio Button on the left
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = languageDisplay.nativeName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = languageDisplay.englishName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}