package dev.patrickgold.florisboard.app.settings.formatting

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.patrickgold.florisboard.ime.network.FormatServiceClient
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes
import dev.patrickgold.florisboard.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch

private const val PREFS_FILE = "formatting_secure_prefs"
private const val KEY_BASE_URL = "formatting_base_url"
private const val KEY_API_KEY = "formatting_api_key"
private const val KEY_ENABLE = "formatting_enable_voice"
private const val KEY_LANGUAGE = "formatting_voice_language"

@Composable
fun FormattingBackendScreen() = FlorisScreen {
    title = "Formatting Backend"
    previewFieldVisible = true

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf(loadBaseUrl(context) ?: "") }
    var apiKey by remember { mutableStateOf(loadApiKey(context) ?: "") }
    var enabled by remember { mutableStateOf(loadEnabled(context)) }
    var selectedLanguage by remember { mutableStateOf(loadLanguage(context)) }
    
    var showBaseUrlDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    
    val languageOptions = listOf(
        "auto" to "Auto-detect",
        "en-US" to "English (US)",
        "en-GB" to "English (UK)",
        "es-ES" to "Spanish (Spain)",
        "es-MX" to "Spanish (Mexico)",
        "fr-FR" to "French",
        "de-DE" to "German",
        "it-IT" to "Italian",
        "pt-BR" to "Portuguese (Brazil)",
        "pt-PT" to "Portuguese (Portugal)",
        "ru-RU" to "Russian",
        "ja-JP" to "Japanese",
        "ko-KR" to "Korean",
        "zh-CN" to "Chinese (Simplified)",
        "zh-TW" to "Chinese (Traditional)",
        "ar-SA" to "Arabic",
        "hi-IN" to "Hindi",
        "tr-TR" to "Turkish",
        "pl-PL" to "Polish",
        "nl-NL" to "Dutch",
    )

    content {
        PreferenceGroup(title = "Backend Configuration") {
            Preference(
                title = "Backend Base URL",
                summary = baseUrl.ifEmpty { "Not configured" },
                onClick = { showBaseUrlDialog = true }
            )
            Preference(
                title = "Client API Key",
                summary = if (apiKey.isEmpty()) "Not configured" else "••••••••",
                onClick = { showApiKeyDialog = true }
            )
        }

        PreferenceGroup(title = "Voice Recognition") {
            Preference(
                title = "Recognition Language",
                summary = languageOptions.find { it.first == selectedLanguage }?.second ?: "Auto-detect",
                onClick = { showLanguageDialog = true }
            )
        }

        PreferenceGroup(title = "Formatting") {
            Preference(
                title = "Enable Formatting",
                summary = if (enabled) "Enabled - transcripts sent to backend" else "Disabled",
                onClick = {
                    enabled = !enabled
                    saveEnabled(context, enabled)
                }
            )
        }

        PreferenceGroup(title = "Testing") {
            Preference(
                title = "Test Connection",
                summary = "Verify backend health",
                onClick = {
                    scope.launch {
            val client = FormatServiceClient(
                baseUrlProvider = { loadBaseUrl(context) },
                apiKeyProvider = { loadApiKey(context) },
            )
                        val ok = withContext(Dispatchers.IO) {
                            try {
                                client.healthCheck()
                            } catch (e: Exception) {
                                false
                            }
                        }
                        Toast.makeText(
                            context,
                            if (ok) "✓ Connection successful" else "✗ Connection failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }
    
    // Dialogs
    if (showBaseUrlDialog) {
        TextInputDialog(
            title = "Backend Base URL",
            initialValue = baseUrl,
            placeholder = "https://example.com",
            onDismiss = { showBaseUrlDialog = false },
            onConfirm = {
                baseUrl = it
                saveBaseUrl(context, it)
                showBaseUrlDialog = false
            }
        )
    }
    
    if (showApiKeyDialog) {
        TextInputDialog(
            title = "Client API Key",
            initialValue = apiKey,
            placeholder = "Your API key",
            isPassword = true,
            onDismiss = { showApiKeyDialog = false },
            onConfirm = {
                apiKey = it
                saveApiKey(context, it)
                showApiKeyDialog = false
            }
        )
    }
    
    if (showLanguageDialog) {
        ListSelectionDialog(
            title = "Select Language",
            options = languageOptions,
            selectedValue = selectedLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelect = { code ->
                selectedLanguage = code
                saveLanguage(context, code)
                showLanguageDialog = false
            }
        )
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initialValue: String,
    placeholder: String,
    isPassword: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                visualTransformation = if (isPassword) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ListSelectionDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options) { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(code) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = code == selectedValue,
                            onClick = { onSelect(code) }
                        )
                        Text(
                            text = name,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun prefs(context: Context): EncryptedSharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    ) as EncryptedSharedPreferences
}

fun loadBaseUrl(context: Context): String? = prefs(context).getString(KEY_BASE_URL, null)
fun saveBaseUrl(context: Context, v: String?) { prefs(context).edit().putString(KEY_BASE_URL, v).apply() }

fun loadApiKey(context: Context): String? = prefs(context).getString(KEY_API_KEY, null)
fun saveApiKey(context: Context, v: String?) { prefs(context).edit().putString(KEY_API_KEY, v).apply() }

fun loadEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLE, false)
fun saveEnabled(context: Context, v: Boolean) { prefs(context).edit().putBoolean(KEY_ENABLE, v).apply() }

fun loadLanguage(context: Context): String = prefs(context).getString(KEY_LANGUAGE, "auto") ?: "auto"
fun saveLanguage(context: Context, v: String) { prefs(context).edit().putString(KEY_LANGUAGE, v).apply() }


