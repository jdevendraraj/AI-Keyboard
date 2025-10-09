package dev.patrickgold.florisboard.app.settings.formatting

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class ModesStorageTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear any existing data
        saveCustomModes(context, emptyList())
        saveSelectedModeId(context, null)
    }

    @Test
    fun testBuiltInModes() {
        val allModes = getAllModes(context)
        
        // Should have 3 built-in modes
        assertEquals(3, allModes.size)
        
        // Check built-in mode IDs
        val builtInIds = allModes.filter { it.isBuiltIn }.map { it.id }
        assertTrue(builtInIds.contains(Mode.BUILTIN_DEFAULT_ID))
        assertTrue(builtInIds.contains(Mode.BUILTIN_CASUAL_ID))
        assertTrue(builtInIds.contains(Mode.BUILTIN_TECHNICAL_ID))
        
        // Check built-in mode properties
        val defaultMode = allModes.find { it.id == Mode.BUILTIN_DEFAULT_ID }
        assertNotNull(defaultMode)
        assertEquals("Default", defaultMode!!.title)
        assertTrue(defaultMode.isDefaultMode())
        
        val casualMode = allModes.find { it.id == Mode.BUILTIN_CASUAL_ID }
        assertNotNull(casualMode)
        assertEquals("Casual Summary", casualMode!!.title)
        assertTrue(casualMode.template.contains("{{transcript}}"))
    }

    @Test
    fun testCustomModeCrud() {
        // Create a custom mode
        val customMode = Mode(
            title = "Test Mode",
            template = "Test template: {{transcript}}"
        )
        
        // Add mode
        val addedMode = addCustomMode(context, customMode)
        assertEquals("Test Mode", addedMode.title)
        assertEquals("Test template: {{transcript}}", addedMode.template)
        assertFalse(addedMode.isBuiltIn)
        
        // Load modes and verify
        val allModes = getAllModes(context)
        assertEquals(4, allModes.size) // 3 built-in + 1 custom
        assertTrue(allModes.any { it.id == addedMode.id })
        
        // Update mode
        val updatedMode = addedMode.copy(title = "Updated Test Mode")
        updateCustomMode(context, updatedMode)
        
        val updatedModes = getAllModes(context)
        val foundUpdated = updatedModes.find { it.id == addedMode.id }
        assertNotNull(foundUpdated)
        assertEquals("Updated Test Mode", foundUpdated!!.title)
        
        // Delete mode
        val deleted = deleteCustomMode(context, addedMode.id)
        assertTrue(deleted)
        
        val finalModes = getAllModes(context)
        assertEquals(3, finalModes.size) // Back to just built-in modes
        assertFalse(finalModes.any { it.id == addedMode.id })
    }

    @Test
    fun testSelectedModePersistence() {
        val customMode = Mode(
            title = "Selected Mode",
            template = "Selected: {{transcript}}"
        )
        val addedMode = addCustomMode(context, customMode)
        
        // Select mode
        saveSelectedModeId(context, addedMode.id)
        assertEquals(addedMode.id, loadSelectedModeId(context))
        
        // Get selected mode
        val selectedMode = getSelectedMode(context)
        assertNotNull(selectedMode)
        assertEquals(addedMode.id, selectedMode!!.id)
        assertEquals("Selected Mode", selectedMode.title)
        
        // Clear selection
        saveSelectedModeId(context, null)
        assertNull(loadSelectedModeId(context))
        assertNull(getSelectedMode(context))
    }

    @Test
    fun testModeSelectionAfterDeletion() {
        val customMode = Mode(
            title = "To Be Deleted",
            template = "Delete me: {{transcript}}"
        )
        val addedMode = addCustomMode(context, customMode)
        
        // Select the mode
        saveSelectedModeId(context, addedMode.id)
        assertEquals(addedMode.id, loadSelectedModeId(context))
        
        // Delete the mode
        deleteCustomMode(context, addedMode.id)
        
        // Selection should be cleared
        assertNull(loadSelectedModeId(context))
        assertNull(getSelectedMode(context))
    }

    @Test
    fun testIsBuiltInMode() {
        assertTrue(isBuiltInMode(Mode.BUILTIN_DEFAULT_ID))
        assertTrue(isBuiltInMode(Mode.BUILTIN_CASUAL_ID))
        assertTrue(isBuiltInMode(Mode.BUILTIN_TECHNICAL_ID))
        assertFalse(isBuiltInMode("custom-mode-id"))
    }

    @Test
    fun testGetModeById() {
        val customMode = Mode(
            title = "Find Me",
            template = "Find: {{transcript}}"
        )
        val addedMode = addCustomMode(context, customMode)
        
        // Find by ID
        val foundMode = getModeById(context, addedMode.id)
        assertNotNull(foundMode)
        assertEquals(addedMode.id, foundMode!!.id)
        assertEquals("Find Me", foundMode.title)
        
        // Find built-in mode
        val builtInMode = getModeById(context, Mode.BUILTIN_DEFAULT_ID)
        assertNotNull(builtInMode)
        assertEquals(Mode.BUILTIN_DEFAULT_ID, builtInMode!!.id)
        assertTrue(builtInMode.isBuiltIn)
        
        // Find non-existent mode
        val notFound = getModeById(context, "non-existent-id")
        assertNull(notFound)
    }

    @Test
    fun testModeTemplateProcessing() {
        val modeWithPlaceholder = Mode(
            title = "With Placeholder",
            template = "Process: {{transcript}}"
        )
        
        val modeWithoutPlaceholder = Mode(
            title = "Without Placeholder",
            template = "Just process this"
        )
        
        // Test with placeholder
        val processedWith = modeWithPlaceholder.getProcessedTemplate()
        assertEquals("Process: {{transcript}}", processedWith)
        
        // Test without placeholder (should auto-append)
        val processedWithout = modeWithoutPlaceholder.getProcessedTemplate()
        assertEquals("Just process this\n\nTranscript: {{transcript}}", processedWithout)
        
        // Test preview
        val preview = modeWithPlaceholder.getPreview()
        assertEquals("Process: Hello world, this is a test", preview)
    }

    @Test
    fun testDefaultModeDetection() {
        val defaultMode = Mode.BUILTIN_DEFAULT
        val emptyTemplateMode = Mode(
            title = "Empty Template",
            template = ""
        )
        val casualMode = Mode.BUILTIN_CASUAL
        
        assertTrue(defaultMode.isDefaultMode())
        assertTrue(emptyTemplateMode.isDefaultMode())
        assertFalse(casualMode.isDefaultMode())
    }
}
