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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import org.florisboard.lib.snygg.ui.SnyggBox

/**
 * Gboard-style inline voice recording toolbar that replaces the smartbar during recording.
 * Shows circular back button, "Speak now" label, and circular mic button with spinner/cancel overlay.
 */
@Composable
fun InlineVoiceToolbar(
    isRecording: Boolean,
    isProcessing: Boolean,
    onBackClick: () -> Unit,
    onRecorderClick: () -> Unit,
    onCancelClick: () -> Unit,
    onLanguageUpdate: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var spinnerController by remember { mutableStateOf<RecorderSpinnerController?>(null) }
    var previousIsRecording by remember { mutableStateOf(false) }
    
    // Update language label when controller is available
    LaunchedEffect(spinnerController) {
        spinnerController?.let { controller ->
            val selectedLanguage = dev.patrickgold.florisboard.app.settings.formatting.loadLanguage(context)
            val languageCode = if (selectedLanguage == "auto") {
                java.util.Locale.getDefault().language.uppercase()
            } else {
                selectedLanguage.uppercase()
            }
            controller.updateLanguageLabel(languageCode)
            flogInfo(LogTopic.IMS_EVENTS) { "InlineVoiceToolbar: Initialized language label with: $languageCode" }
        }
    }
    
    // Handle external language updates
    LaunchedEffect(onLanguageUpdate) {
        onLanguageUpdate?.let { callback ->
            spinnerController?.let { controller ->
                val selectedLanguage = dev.patrickgold.florisboard.app.settings.formatting.loadLanguage(context)
                val languageCode = if (selectedLanguage == "auto") {
                    java.util.Locale.getDefault().language.uppercase()
                } else {
                    selectedLanguage.uppercase()
                }
                controller.updateLanguageLabel(languageCode)
                callback(languageCode)
                flogInfo(LogTopic.IMS_EVENTS) { "InlineVoiceToolbar: Updated language label via callback: $languageCode" }
            }
        }
    }
    
    // Periodically check for language changes when toolbar is visible
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000) // Check every second
            spinnerController?.let { controller ->
                val selectedLanguage = dev.patrickgold.florisboard.app.settings.formatting.loadLanguage(context)
                val languageCode = if (selectedLanguage == "auto") {
                    java.util.Locale.getDefault().language.uppercase()
                } else {
                    selectedLanguage.uppercase()
                }
                // Only update if language has changed
                if (controller.getCurrentLanguageCode() != languageCode) {
                    controller.updateLanguageLabel(languageCode)
                    flogInfo(LogTopic.IMS_EVENTS) { "InlineVoiceToolbar: Language changed to: $languageCode" }
                }
            }
        }
    }
    
    SnyggBox(
        elementName = FlorisImeUi.Smartbar.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight),
    ) {
        AndroidView(
            factory = { ctx ->
                val inflater = LayoutInflater.from(ctx)
                val view = inflater.inflate(R.layout.view_inline_recorder_toolbar, null)
                
                // Ensure proper layout parameters
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                // Initialize spinner controller
                val spinner = view.findViewById<android.widget.ProgressBar>(R.id.recorder_spinner)
                val cancel = view.findViewById<View>(R.id.recorder_cancel)
                val icon = view.findViewById<View>(R.id.recorder_icon)
                val label = view.findViewById<View>(R.id.toolbar_speak_now)
                val background = view.findViewById<View>(R.id.recorder_background)
                val languageLabel = view.findViewById<android.widget.TextView>(R.id.recorder_language_label)
                
                // Debug: Log the view setup
                flogInfo(LogTopic.IMS_EVENTS) { "InlineVoiceToolbar: Setting up views - spinner: ${spinner != null}, cancel: ${cancel != null}, icon: ${icon != null}, label: ${label != null}, background: ${background != null}, languageLabel: ${languageLabel != null}" }
                
                spinnerController = RecorderSpinnerController(spinner, cancel, icon, label, background, languageLabel, ctx)
                
                // Set up click listeners
                view.findViewById<View>(R.id.toolbar_back_btn).setOnClickListener { onBackClick() }
                view.findViewById<View>(R.id.recorder_icon).setOnClickListener { onRecorderClick() }
                view.findViewById<View>(R.id.recorder_cancel).setOnClickListener { onCancelClick() }
                
                // Handle window insets to prevent status bar overlap
                ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    v.setPadding(
                        v.paddingLeft,
                        systemBars.top,
                        v.paddingRight,
                        v.paddingBottom
                    )
                    insets
                }
                
                view
            },
            update = { view ->
                // Update UI state based on current state
                spinnerController?.let { controller ->
                    when {
                        isProcessing -> {
                            controller.showSpinner("current_request")
                        }
                        isRecording -> {
                            controller.showRecordingState()
                        }
                        else -> {
                            // Check if we're transitioning from recording to idle
                            if (previousIsRecording && !isRecording) {
                                controller.showIdleStateKeepToolbar()
                            } else {
                                controller.showIdleState()
                            }
                        }
                    }
                }
                // Update previous state
                previousIsRecording = isRecording
            }
        )
    }
    
    // Clean up on disposal
    DisposableEffect(Unit) {
        onDispose {
            spinnerController = null
        }
    }
}