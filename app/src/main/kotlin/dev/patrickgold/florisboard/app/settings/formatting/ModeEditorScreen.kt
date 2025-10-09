package dev.patrickgold.florisboard.app.settings.formatting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import kotlinx.coroutines.launch

@Composable
fun ModeEditorScreen(modeId: String?) = FlorisScreen {
    title = if (modeId == null) "Create Mode" else "Edit Mode"
    previewFieldVisible = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // Load existing mode if editing
    val existingMode = remember(modeId) {
        if (modeId != null) getModeById(context, modeId) else null
    }

    var title by remember { mutableStateOf(existingMode?.title ?: "") }
    var template by remember { mutableStateOf(existingMode?.template ?: "") }
    var showPreview by remember { mutableStateOf(false) }
    var showPrivacyNotice by remember { mutableStateOf(false) }
    var hasShownPrivacyNotice by remember { mutableStateOf(false) }

    // Show privacy notice on first create/edit
    LaunchedEffect(Unit) {
        if (!hasShownPrivacyNotice && existingMode == null) {
            showPrivacyNotice = true
            hasShownPrivacyNotice = true
        }
    }

    val isTitleValid = title.trim().isNotEmpty()
    val isTemplateValid = template.trim().isNotEmpty()
    val canSave = isTitleValid && isTemplateValid

    content {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                placeholder = { Text("e.g., Casual Summary") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth(),
                isError = !isTitleValid && title.isNotEmpty()
            )

            // Template field
            OutlinedTextField(
                value = template,
                onValueChange = { template = it },
                label = { Text("Prompt Template") },
                placeholder = { Text("Enter your custom prompt template...") },
                minLines = 4,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                modifier = Modifier.fillMaxWidth(),
                isError = !isTemplateValid && template.isNotEmpty()
            )

            // Character counter
            Text(
                text = "${template.length}/4000",
                style = MaterialTheme.typography.bodySmall,
                color = if (template.length > 4000) MaterialTheme.colorScheme.error 
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )

            // Helper text
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Template Instructions:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "• Place `{{transcript}}` where the transcribed text should be inserted",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Text(
                        text = "• If `{{transcript}}` is missing, it will be auto-appended at the end",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Text(
                        text = "• Example: \"Translate to English: {{transcript}}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Preview button
            Button(
                onClick = { showPreview = true },
                enabled = template.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Preview Template")
            }

            // Save button
            Button(
                onClick = {
                    scope.launch {
                        val newMode = Mode(
                            id = existingMode?.id ?: "",
                            title = title.trim(),
                            template = template.trim(),
                            isBuiltIn = false
                        )
                        
                        if (existingMode == null) {
                            addCustomMode(context, newMode)
                        } else {
                            updateCustomMode(context, newMode)
                        }
                        
                        navController.popBackStack()
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (existingMode == null) "Create Mode" else "Save Changes")
            }
        }
    }

    // Preview dialog
    if (showPreview) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text("Template Preview") },
            text = {
                Column {
                    Text(
                        text = "This is how your template will look with sample text:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = Mode(
                                title = "",
                                template = template.trim()
                            ).getPreview(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreview = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Privacy notice dialog
    if (showPrivacyNotice) {
        AlertDialog(
            onDismissRequest = { showPrivacyNotice = false },
            title = { Text("Privacy Notice") },
            text = {
                Text(
                    text = "Custom prompts are stored locally on this device and sent with each transcription to your configured backend and the LLM provider. Do not include secrets, personal information, or sensitive data in prompts.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyNotice = false }) {
                    Text("I Understand")
                }
            }
        )
    }
}
