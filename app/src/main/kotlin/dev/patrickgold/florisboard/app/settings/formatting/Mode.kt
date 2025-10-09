package dev.patrickgold.florisboard.app.settings.formatting

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a prompt mode for custom text formatting
 */
@Serializable
data class Mode(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val template: String,
    val isBuiltIn: Boolean = false
) {
    companion object {
        // Built-in mode IDs
        const val BUILTIN_DEFAULT_ID = "builtin-default"
        const val BUILTIN_CASUAL_ID = "builtin-casual"
        const val BUILTIN_TECHNICAL_ID = "builtin-technical"
        
        // Built-in modes
        val BUILTIN_DEFAULT = Mode(
            id = BUILTIN_DEFAULT_ID,
            title = "Default",
            template = "", // Empty template means use backend default
            isBuiltIn = true
        )
        
        val BUILTIN_CASUAL = Mode(
            id = BUILTIN_CASUAL_ID,
            title = "Casual Summary",
            template = "Summarize the following in a casual, friendly tone:\n\n{{transcript}}",
            isBuiltIn = true
        )
        
        val BUILTIN_TECHNICAL = Mode(
            id = BUILTIN_TECHNICAL_ID,
            title = "Technical Notes",
            template = "Format the following as technical notes with clear structure and proper terminology:\n\n{{transcript}}",
            isBuiltIn = true
        )
        
        val ALL_BUILTIN_MODES = listOf(BUILTIN_DEFAULT, BUILTIN_CASUAL, BUILTIN_TECHNICAL)
    }
    
    /**
     * Returns true if this mode uses the backend's default prompt
     */
    fun isDefaultMode(): Boolean = id == BUILTIN_DEFAULT_ID || template.isEmpty()
    
    /**
     * Returns the template with auto-appended transcript placeholder if missing
     */
    fun getProcessedTemplate(): String {
        return if (template.contains("{{transcript}}")) {
            template
        } else {
            "$template\n\nTranscript: {{transcript}}"
        }
    }
    
    /**
     * Returns a preview of the template with sample transcript
     */
    fun getPreview(): String {
        val sampleTranscript = "Hello world, this is a test"
        return getProcessedTemplate().replace("{{transcript}}", sampleTranscript)
    }
}
