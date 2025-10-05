package dev.patrickgold.florisboard.app.settings.formatting

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes
import dev.patrickgold.florisboard.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import kotlinx.coroutines.launch

private const val PREFS_FILE = "formatting_secure_prefs"
private const val KEY_BASE_URL = "formatting_base_url"
private const val KEY_API_KEY = "formatting_api_key"
private const val KEY_ENABLE = "formatting_enable_voice"

@Composable
fun FormattingBackendScreen() = FlorisScreen {
    title = "Formatting Backend"
    previewFieldVisible = false

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf(loadBaseUrl(context) ?: "") }
    var apiKey by remember { mutableStateOf(loadApiKey(context) ?: "") }
    var enabled by remember { mutableStateOf(loadEnabled(context)) }

    content {
        Text("Transcripts will be sent to the configured backend when enabled.")
        OutlinedTextField(
            value = baseUrl,
            onValueChange = {
                baseUrl = it
                saveBaseUrl(context, it)
            },
            singleLine = true,
            placeholder = { Text("https://example.com") },
            label = { Text("Backend Base URL") },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                saveApiKey(context, it)
            },
            singleLine = true,
            label = { Text("Client API Key") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Text("Enable Formatting for Voice Typing")
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                saveEnabled(context, it)
                if (it) Toast.makeText(context, "Transcripts will be sent to your configured backend.", Toast.LENGTH_SHORT).show()
            },
            colors = SwitchDefaults.colors()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val client = FormatServiceClient(
                baseUrlProvider = { loadBaseUrl(context) },
                apiKeyProvider = { loadApiKey(context) },
            )
            scope.launch {
                val ok = withContext(Dispatchers.IO) { client.healthCheck() }
                Toast.makeText(context, if (ok) "Health OK" else "Health failed", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("Test Settings")
        }
    }
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


