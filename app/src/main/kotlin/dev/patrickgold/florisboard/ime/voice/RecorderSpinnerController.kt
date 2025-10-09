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

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.animation.DecelerateInterpolator
import android.content.Context
import android.widget.ProgressBar
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogInfo

/**
 * Controller for managing the inline recorder toolbar spinner and cancel button.
 * Handles smooth animations and state transitions for the Gboard-style UI.
 */
class RecorderSpinnerController(
    private val recorderSpinner: ProgressBar,
    private val recorderCancel: View,
    private val recorderIcon: View,
    private val speakNowLabel: View,
    private val recorderBackground: View,
    private val recorderLanguageLabel: android.widget.TextView,
    private val context: Context,
) {
    companion object {
        private const val ANIMATION_DURATION_MS = 150L
    }

    private var isProcessing = false
    private var currentRequestId: String? = null
    private var currentLanguageCode: String? = null

    /**
     * Show spinner and cancel button for processing state
     */
    fun showSpinner(requestId: String) {
        if (isProcessing) return
        
        currentRequestId = requestId
        isProcessing = true
        
        flogInfo(LogTopic.IMS_EVENTS) { "RecorderSpinnerController.showSpinner() - requestId: $requestId" }
        
        // Update label text
        speakNowLabel.contentDescription = "Processing voice input"
        // Update the actual text displayed
        if (speakNowLabel is android.widget.TextView) {
            speakNowLabel.text = "Processing"
        }
        
        // Announce accessibility event
        announceAccessibilityEvent("Formatting voice input")
        
        // Hide microphone icon and show cancel button
        recorderIcon.visibility = View.GONE
        recorderLanguageLabel.visibility = View.GONE
        recorderCancel.visibility = View.VISIBLE
        
        // Show spinner with fade-in animation
        recorderSpinner.visibility = View.VISIBLE
        recorderSpinner.alpha = 0f
        recorderSpinner.animate()
            .alpha(1f)
            .setDuration(ANIMATION_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        // Show cancel button with scale + fade animation
        recorderCancel.alpha = 0f
        recorderCancel.scaleX = 0.8f
        recorderCancel.scaleY = 0.8f
        
        val cancelAnimator = AnimatorSet()
        cancelAnimator.playTogether(
            ObjectAnimator.ofFloat(recorderCancel, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(recorderCancel, "scaleX", 0.8f, 1f),
            ObjectAnimator.ofFloat(recorderCancel, "scaleY", 0.8f, 1f)
        )
        cancelAnimator.duration = ANIMATION_DURATION_MS
        cancelAnimator.interpolator = DecelerateInterpolator()
        cancelAnimator.start()
    }

    /**
     * Hide spinner and cancel button
     */
    fun hideSpinner() {
        if (!isProcessing) return
        
        flogInfo(LogTopic.IMS_EVENTS) { "RecorderSpinnerController.hideSpinner() - requestId: $currentRequestId" }
        
        isProcessing = false
        currentRequestId = null
        
        // Update label text
        speakNowLabel.contentDescription = "Speak now"
        // Update the actual text displayed
        if (speakNowLabel is android.widget.TextView) {
            speakNowLabel.text = "Speak now"
        }
        
        // Announce accessibility event
        announceAccessibilityEvent("Formatting complete")
        
        // Hide spinner with fade-out animation
        recorderSpinner.animate()
            .alpha(0f)
            .setDuration(ANIMATION_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                recorderSpinner.visibility = View.GONE
            }
            .start()
        
        // Hide cancel button with scale + fade animation
        val cancelAnimator = AnimatorSet()
        cancelAnimator.playTogether(
            ObjectAnimator.ofFloat(recorderCancel, "alpha", 1f, 0f),
            ObjectAnimator.ofFloat(recorderCancel, "scaleX", 1f, 0.8f),
            ObjectAnimator.ofFloat(recorderCancel, "scaleY", 1f, 0.8f)
        )
        cancelAnimator.duration = ANIMATION_DURATION_MS
        cancelAnimator.interpolator = DecelerateInterpolator()
        cancelAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                recorderCancel.visibility = View.GONE
                // Show microphone icon and language label again
                recorderIcon.visibility = View.VISIBLE
                recorderLanguageLabel.visibility = View.VISIBLE
            }
        })
        cancelAnimator.start()
    }

    /**
     * Show recording state (stop icon)
     */
    fun showRecordingState() {
        flogInfo(LogTopic.IMS_EVENTS) { "RecorderSpinnerController.showRecordingState()" }
        
        // Show background circle with red color
        recorderBackground.visibility = View.VISIBLE
        recorderBackground.setBackgroundResource(R.drawable.recorder_circle_bg_recording)
        
        // Change icon to stop (could be implemented with drawable state or icon swap)
        recorderIcon.contentDescription = "Stop recording"
        speakNowLabel.contentDescription = "Recording... Tap to stop"
        // Update the actual text displayed
        if (speakNowLabel is android.widget.TextView) {
            speakNowLabel.text = "Recording... Tap to stop"
        }
    }

    /**
     * Show idle state (mic icon)
     */
    fun showIdleState() {
        flogInfo(LogTopic.IMS_EVENTS) { "RecorderSpinnerController.showIdleState()" }
        
        // Ensure spinner is hidden
        if (isProcessing) {
            hideSpinner()
        }
        
        // Hide background circle
        recorderBackground.visibility = View.GONE
        
        // Change icon back to mic
        recorderIcon.contentDescription = "Start voice recording"
        speakNowLabel.contentDescription = "Press to start recording"
        // Update the actual text displayed
        if (speakNowLabel is android.widget.TextView) {
            speakNowLabel.text = "Press to start recording"
        }
        
        // Announce accessibility event
        announceAccessibilityEvent("Press to start recording")
    }
    
    /**
     * Show idle state without collapsing toolbar (for stopping recording)
     */
    fun showIdleStateKeepToolbar() {
        flogInfo(LogTopic.IMS_EVENTS) { "RecorderSpinnerController.showIdleStateKeepToolbar()" }
        
        // Ensure spinner is hidden
        if (isProcessing) {
            hideSpinner()
        }
        
        // Hide background circle
        recorderBackground.visibility = View.GONE
        
        // Change icon back to mic
        recorderIcon.contentDescription = "Start voice recording"
        speakNowLabel.contentDescription = "Press to start recording"
        // Update the actual text displayed
        if (speakNowLabel is android.widget.TextView) {
            speakNowLabel.text = "Press to start recording"
        }
        
        // Announce accessibility event
        announceAccessibilityEvent("Press to start recording")
    }
    
    /**
     * Announce accessibility event
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
            flogInfo(LogTopic.IMS_EVENTS) { "Failed to send accessibility event: ${e.message}" }
        }
    }

    /**
     * Check if currently processing
     */
    fun isProcessing(): Boolean = isProcessing

    /**
     * Get current request ID
     */
    fun getCurrentRequestId(): String? = currentRequestId

    /**
     * Update language label
     */
    fun updateLanguageLabel(languageCode: String) {
        currentLanguageCode = languageCode
        recorderLanguageLabel.text = languageCode.uppercase()
        flogInfo(LogTopic.IMS_EVENTS) { "RecorderSpinnerController.updateLanguageLabel() - language: $languageCode" }
    }
    
    /**
     * Get current language code
     */
    fun getCurrentLanguageCode(): String? = currentLanguageCode
}
