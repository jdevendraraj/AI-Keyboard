package dev.patrickgold.florisboard.app.settings.formatting

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS_FILE = "formatting_modes_secure_prefs"
private const val KEY_MODES = "formatting_modes"
private const val KEY_SELECTED_MODE_ID = "selected_mode_id"

private val json = Json { ignoreUnknownKeys = true }

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

/**
 * Load all custom modes from storage
 */
fun loadCustomModes(context: Context): List<Mode> {
    return try {
        val modesJson = prefs(context).getString(KEY_MODES, "[]") ?: "[]"
        json.decodeFromString<List<Mode>>(modesJson)
    } catch (e: Exception) {
        // If parsing fails, return empty list
        emptyList()
    }
}

/**
 * Save custom modes to storage
 */
fun saveCustomModes(context: Context, modes: List<Mode>) {
    try {
        val modesJson = json.encodeToString(modes)
        prefs(context).edit()
            .putString(KEY_MODES, modesJson)
            .apply()
    } catch (e: Exception) {
        // Log error but don't crash
        android.util.Log.e("ModesStorage", "Failed to save modes", e)
    }
}

/**
 * Load the currently selected mode ID
 */
fun loadSelectedModeId(context: Context): String? {
    return prefs(context).getString(KEY_SELECTED_MODE_ID, null)
}

/**
 * Save the currently selected mode ID
 */
fun saveSelectedModeId(context: Context, modeId: String?) {
    prefs(context).edit()
        .putString(KEY_SELECTED_MODE_ID, modeId)
        .apply()
}

/**
 * Get all modes (built-in + custom)
 */
fun getAllModes(context: Context): List<Mode> {
    val customModes = loadCustomModes(context)
    return Mode.ALL_BUILTIN_MODES + customModes
}

/**
 * Get the currently selected mode
 */
fun getSelectedMode(context: Context): Mode? {
    val selectedId = loadSelectedModeId(context)
    return getAllModes(context).find { it.id == selectedId }
}

/**
 * Add a new custom mode
 */
fun addCustomMode(context: Context, mode: Mode): Mode {
    val customModes = loadCustomModes(context).toMutableList()
    customModes.add(mode)
    saveCustomModes(context, customModes)
    return mode
}

/**
 * Update an existing custom mode
 */
fun updateCustomMode(context: Context, mode: Mode): Mode {
    val customModes = loadCustomModes(context).toMutableList()
    val index = customModes.indexOfFirst { it.id == mode.id }
    if (index >= 0) {
        customModes[index] = mode
        saveCustomModes(context, customModes)
    }
    return mode
}

/**
 * Delete a custom mode
 */
fun deleteCustomMode(context: Context, modeId: String): Boolean {
    val customModes = loadCustomModes(context).toMutableList()
    val removed = customModes.removeAll { it.id == modeId }
    if (removed) {
        saveCustomModes(context, customModes)
        
        // If the deleted mode was selected, clear selection
        val selectedId = loadSelectedModeId(context)
        if (selectedId == modeId) {
            saveSelectedModeId(context, null)
        }
    }
    return removed
}

/**
 * Check if a mode ID is built-in
 */
fun isBuiltInMode(modeId: String): Boolean {
    return Mode.ALL_BUILTIN_MODES.any { it.id == modeId }
}

/**
 * Get a mode by ID
 */
fun getModeById(context: Context, modeId: String): Mode? {
    return getAllModes(context).find { it.id == modeId }
}
