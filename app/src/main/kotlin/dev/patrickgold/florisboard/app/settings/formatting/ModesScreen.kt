package dev.patrickgold.florisboard.app.settings.formatting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import kotlinx.coroutines.launch

@Composable
fun ModesScreen() = FlorisScreen {
    title = "Prompt Modes"
    previewFieldVisible = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    var modes by remember { mutableStateOf(getAllModes(context)) }
    var selectedModeId by remember { mutableStateOf(loadSelectedModeId(context)) }
    var modeToDelete by remember { mutableStateOf<Mode?>(null) }
    var showErrorDialog by remember { mutableStateOf<String?>(null) }

    // Refresh modes when screen becomes visible
    LaunchedEffect(Unit) {
        try {
            modes = getAllModes(context)
            selectedModeId = loadSelectedModeId(context) // Also refresh selected mode
        } catch (e: Exception) {
            android.util.Log.e("ModesScreen", "Failed to load modes", e)
            showErrorDialog = "Failed to load modes: ${e.message}"
        }
    }

    // Refresh modes when returning from editor
    LaunchedEffect(navController.currentBackStackEntry) {
        modes = getAllModes(context)
        selectedModeId = loadSelectedModeId(context)
    }

    floatingActionButton {
        FloatingActionButton(
            onClick = { navController.navigate(Routes.Settings.ModeEditor()) }
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create Mode")
        }
    }

    content {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            modes.forEach { mode ->
                ModeItem(
                    mode = mode,
                    isSelected = mode.id == selectedModeId,
                    onSelect = {
                        selectedModeId = mode.id
                        saveSelectedModeId(context, mode.id)
                    },
                    onEdit = {
                        navController.navigate(Routes.Settings.ModeEditor(mode.id))
                    },
                    onDelete = {
                        modeToDelete = mode
                    }
                )
            }
        }
    }

    // Delete confirmation dialog
    modeToDelete?.let { mode ->
        AlertDialog(
            onDismissRequest = { modeToDelete = null },
            title = { Text("Delete Mode") },
            text = {
                Text("Are you sure you want to delete \"${mode.title}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                deleteCustomMode(context, mode.id)
                                modes = getAllModes(context)
                                // If the deleted mode was selected, switch to default mode
                                if (selectedModeId == mode.id) {
                                    val defaultMode = Mode.BUILTIN_DEFAULT
                                    selectedModeId = defaultMode.id
                                    saveSelectedModeId(context, defaultMode.id)
                                }
                                modeToDelete = null
                            } catch (e: Exception) {
                                android.util.Log.e("ModesScreen", "Failed to delete mode", e)
                                showErrorDialog = "Failed to delete mode: ${e.message}"
                            }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { modeToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Error dialog
    showErrorDialog?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { showErrorDialog = null },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun ModeItem(
    mode: Mode,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection radio button
            IconButton(
                onClick = onSelect,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Mode content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect() }
                    .padding(horizontal = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (mode.isBuiltIn) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Built-in",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                if (mode.template.isNotEmpty()) {
                    Text(
                        text = mode.template,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        text = "Uses backend default prompt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Action buttons (only for custom modes)
            if (!mode.isBuiltIn) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
