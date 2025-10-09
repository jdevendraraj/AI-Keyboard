/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import dev.patrickgold.florisboard.lib.devtools.flogError
import java.util.UUID

/**
 * Controller for managing inline voice recording state and UI transitions.
 * Handles the toolbar swap, spinner display, and cancellation logic.
 * Implements a finite state machine for IDLE/RECORDING/PROCESSING/CANCELLING states.
 */
class InlineVoiceRecorderController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val PROCESSING_TIMEOUT_MS = 10_000L // 10 seconds
    }

    /**
     * States for the inline voice recorder
     */
    enum class RecorderState {
        IDLE,           // Normal keyboard toolbar
        RECORDING,      // User is recording audio
        PROCESSING,     // Audio sent to backend, waiting for response
        CANCELLING      // User cancelled processing
    }

    // State management
    private val _currentState = MutableStateFlow(RecorderState.IDLE)
    val currentState: StateFlow<RecorderState> = _currentState.asStateFlow()

    // UI State - derived from current state
    val isInlineRecordingActive: StateFlow<Boolean> = _currentState.map { 
        it != RecorderState.IDLE 
    }.stateIn(scope, SharingStarted.Eagerly, false)
    
    // Keep toolbar visible even in idle state when transitioning from recording
    private val _keepToolbarVisible = MutableStateFlow(false)
    val keepToolbarVisible: StateFlow<Boolean> = _keepToolbarVisible.asStateFlow()

    val isProcessing: StateFlow<Boolean> = _currentState.map { 
        it == RecorderState.PROCESSING 
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val isRecording: StateFlow<Boolean> = _currentState.map { 
        it == RecorderState.RECORDING 
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _currentRequestId = MutableStateFlow<String?>(null)
    val currentRequestId: StateFlow<String?> = _currentRequestId.asStateFlow()

    // Processing job for cancellation
    private var processingJob: Job? = null

    /**
     * Start inline recording mode - transitions to RECORDING state
     */
    fun startInlineRecording(): String {
        val requestId = UUID.randomUUID().toString()
        _currentRequestId.value = requestId
        _currentState.value = RecorderState.RECORDING
        _keepToolbarVisible.value = true
        
        // Announce recording start for accessibility
        announceAccessibilityEvent("Voice recording started")
        
        flogInfo(LogTopic.IMS_EVENTS) { 
            "InlineVoiceRecorderController.startInlineRecording() - requestId: $requestId, state: ${_currentState.value}" 
        }
        
        return requestId
    }

    /**
     * Stop recording and start processing - transitions to PROCESSING state
     */
    fun startProcessing(requestId: String) {
        if (_currentRequestId.value != requestId) {
            flogError(LogTopic.IMS_EVENTS) { 
                "startProcessing called with mismatched requestId: $requestId vs ${_currentRequestId.value}" 
            }
            return
        }
        
        if (_currentState.value != RecorderState.RECORDING) {
            flogError(LogTopic.IMS_EVENTS) { 
                "startProcessing called in wrong state: ${_currentState.value}" 
            }
            return
        }
        
        _currentState.value = RecorderState.PROCESSING
        
        // Announce processing start for accessibility
        announceAccessibilityEvent("Processing voice input")
        
        // Set up timeout
        processingJob = scope.launch {
            try {
                kotlinx.coroutines.delay(PROCESSING_TIMEOUT_MS)
                // Timeout reached
                flogInfo(LogTopic.IMS_EVENTS) { 
                    "Processing timeout reached for requestId: $requestId" 
                }
                cancelProcessing()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Processing was cancelled or completed
                flogInfo(LogTopic.IMS_EVENTS) { 
                    "Processing job cancelled for requestId: $requestId" 
                }
            }
        }
        
        flogInfo(LogTopic.IMS_EVENTS) { 
            "InlineVoiceRecorderController.startProcessing() - requestId: $requestId, state: ${_currentState.value}" 
        }
    }

    /**
     * Cancel current processing - transitions to CANCELLING then IDLE
     */
    fun cancelProcessing() {
        val requestId = _currentRequestId.value
        val currentState = _currentState.value
        
        if (currentState != RecorderState.PROCESSING) {
            flogError(LogTopic.IMS_EVENTS) { 
                "cancelProcessing called in wrong state: $currentState" 
            }
            return
        }
        
        _currentState.value = RecorderState.CANCELLING
        processingJob?.cancel()
        processingJob = null
        
        // Announce cancellation for accessibility
        announceAccessibilityEvent("Voice processing cancelled")
        
        // Transition to IDLE after a brief moment
        scope.launch {
            kotlinx.coroutines.delay(100) // Brief delay for UI feedback
            _currentState.value = RecorderState.IDLE
            _currentRequestId.value = null
        }
        
        flogInfo(LogTopic.IMS_EVENTS) { 
            "InlineVoiceRecorderController.cancelProcessing() - requestId: $requestId, state: ${_currentState.value}" 
        }
    }

    /**
     * Complete processing successfully - transitions to IDLE
     */
    fun completeProcessing() {
        val requestId = _currentRequestId.value
        val currentState = _currentState.value
        
        if (currentState != RecorderState.PROCESSING) {
            flogError(LogTopic.IMS_EVENTS) { 
                "completeProcessing called in wrong state: $currentState" 
            }
            return
        }
        
        processingJob?.cancel()
        processingJob = null
        
        _currentState.value = RecorderState.IDLE
        _currentRequestId.value = null
        
        // Announce completion for accessibility
        announceAccessibilityEvent("Voice processing completed")
        
        flogInfo(LogTopic.IMS_EVENTS) { 
            "InlineVoiceRecorderController.completeProcessing() - requestId: $requestId, state: ${_currentState.value}" 
        }
    }

    /**
     * Cancel recording and restore toolbar - transitions to IDLE
     * Can be called from RECORDING or PROCESSING states
     */
    fun cancelRecording() {
        val requestId = _currentRequestId.value
        val currentState = _currentState.value
        
        processingJob?.cancel()
        processingJob = null
        
        _currentState.value = RecorderState.IDLE
        _currentRequestId.value = null
        _keepToolbarVisible.value = false
        
        // Announce cancellation for accessibility
        announceAccessibilityEvent("Voice recording cancelled")
        
        flogInfo(LogTopic.IMS_EVENTS) { 
            "InlineVoiceRecorderController.cancelRecording() - requestId: $requestId, from state: $currentState" 
        }
    }

    /**
     * Transition to idle state (keep toolbar visible)
     */
    fun transitionToIdle() {
        val currentRequestId = _currentRequestId.value
        val currentState = _currentState.value
        
        processingJob?.cancel()
        processingJob = null
        
        _currentRequestId.value = null
        _currentState.value = RecorderState.IDLE
        // Keep toolbar visible when transitioning to idle from recording
        _keepToolbarVisible.value = true
        
        // Announce completion for accessibility
        announceAccessibilityEvent("Voice recording completed")
        
        flogInfo(LogTopic.IMS_EVENTS) { 
            "InlineVoiceRecorderController.transitionToIdle() - requestId: $currentRequestId, from state: $currentState" 
        }
    }

    /**
     * Clear keep toolbar visible flag (when user dismisses toolbar)
     */
    fun clearKeepToolbarVisible() {
        _keepToolbarVisible.value = false
        flogInfo(LogTopic.IMS_EVENTS) { 
            "InlineVoiceRecorderController.clearKeepToolbarVisible()" 
        }
    }
    
    /**
     * Get current processing job for external cancellation
     */
    fun getProcessingJob(): Job? = processingJob

    /**
     * Check if we can start recording (only from IDLE state)
     */
    fun canStartRecording(): Boolean = _currentState.value == RecorderState.IDLE

    /**
     * Check if we can stop recording (only from RECORDING state)
     */
    fun canStopRecording(): Boolean = _currentState.value == RecorderState.RECORDING

    /**
     * Check if we can cancel processing (only from PROCESSING state)
     */
    fun canCancelProcessing(): Boolean = _currentState.value == RecorderState.PROCESSING

    /**
     * Announce accessibility events for state transitions
     */
    private fun announceAccessibilityEvent(message: String) {
        try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (accessibilityManager?.isEnabled == true) {
                val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                event.text.add(message)
                accessibilityManager.sendAccessibilityEvent(event)
            }
        } catch (e: Exception) {
            flogError(LogTopic.IMS_EVENTS) { "Failed to send accessibility event: ${e.message}" }
        }
    }
}
