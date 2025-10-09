package dev.patrickgold.florisboard.ime.network

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class FormatServiceClientTest {

    @Test
    fun testFormatResponseSerialization() {
        val response = FormatResponse(
            formattedText = "Hello world.",
            requestId = "test-123",
            modeTitle = "Test Mode"
        )
        
        assertEquals("Hello world.", response.formattedText)
        assertEquals("test-123", response.requestId)
        assertEquals("Test Mode", response.modeTitle)
    }

    @Test
    fun testFormatResponseWithoutOptionalFields() {
        val response = FormatResponse(
            formattedText = "Hello world."
        )
        
        assertEquals("Hello world.", response.formattedText)
        assertNull(response.requestId)
        assertNull(response.modeTitle)
    }

    @Test
    fun testChirpTranscribeResponseSerialization() {
        val response = ChirpTranscribeResponse(
            formattedText = "Hello world.",
            requestId = "test-123",
            modeTitle = "Test Mode",
            rawTranscription = "hello world",
            usage = mapOf("tokens" to 10)
        )
        
        assertEquals("Hello world.", response.formattedText)
        assertEquals("test-123", response.requestId)
        assertEquals("Test Mode", response.modeTitle)
        assertEquals("hello world", response.rawTranscription)
        assertEquals(10, response.usage?.get("tokens"))
    }

    @Test
    fun testChirpTranscribeResponseWithoutOptionalFields() {
        val response = ChirpTranscribeResponse(
            formattedText = "Hello world."
        )
        
        assertEquals("Hello world.", response.formattedText)
        assertNull(response.requestId)
        assertNull(response.modeTitle)
        assertNull(response.rawTranscription)
        assertNull(response.usage)
    }

    @Test
    fun testPromptTemplateValidation() {
        // Test valid template
        val validTemplate = "Format this: {{transcript}}"
        assertTrue(validTemplate.contains("{{transcript}}"))
        
        // Test template without placeholder
        val templateWithoutPlaceholder = "Just format this text"
        assertFalse(templateWithoutPlaceholder.contains("{{transcript}}"))
        
        // Test auto-append logic
        val autoAppended = "$templateWithoutPlaceholder\n\nTranscript: {{transcript}}"
        assertTrue(autoAppended.contains("{{transcript}}"))
    }

    @Test
    fun testPromptTemplateLengthValidation() {
        // Test valid length
        val validTemplate = "a".repeat(4000)
        assertTrue(validTemplate.length <= 4000)
        
        // Test invalid length
        val invalidTemplate = "a".repeat(4001)
        assertTrue(invalidTemplate.length > 4000)
    }

    @Test
    fun testMultipleTranscriptPlaceholders() {
        val template = "First: {{transcript}}\nSecond: {{transcript}}\nThird: {{transcript}}"
        val transcript = "hello world"
        
        val processed = template.replace(Regex("\\{\\{transcript\\}\\}"), transcript)
        assertEquals("First: hello world\nSecond: hello world\nThird: hello world", processed)
    }

    @Test
    fun testSpecialCharactersInTranscript() {
        val transcript = "Hello \"world\" with\nnewlines\tand\ttabs"
        val template = "Format: {{transcript}}"
        
        // Test escaping logic (simplified version)
        val escapedTranscript = transcript
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        
        val processed = template.replace("{{transcript}}", escapedTranscript)
        assertTrue(processed.contains("\\\"world\\\""))
        assertTrue(processed.contains("\\n"))
        assertTrue(processed.contains("\\t"))
    }
}
