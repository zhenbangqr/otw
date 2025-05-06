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

data class ThemeDisplay(
    val Theme: String,
    val ThemeDes: String // Or secondary display name
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThemeScreen(
    navController: NavController
) {
    val availableTheme = listOf(
        ThemeDisplay("Default", "Default")
    )
    val selectedThemeKey by remember { mutableStateOf("Default") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Chat Theme") },
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
            items(availableTheme) { theme ->
                ThemeItem(
                    themeDisplay = theme,
                    isSelected = theme.Theme == selectedThemeKey, // Compare based on a unique key/name
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
fun ThemeItem(
    themeDisplay: ThemeDisplay,
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
                text = themeDisplay.Theme,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = themeDisplay.ThemeDes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}