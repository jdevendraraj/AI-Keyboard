package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.android.showShortToastSync
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.ime.network.FormatServiceClient
import dev.patrickgold.florisboard.app.settings.formatting.loadEnabled
import dev.patrickgold.florisboard.app.settings.formatting.loadApiKey
import dev.patrickgold.florisboard.app.settings.formatting.loadBaseUrl
import dev.patrickgold.florisboard.app.settings.formatting.loadLanguage
import dev.patrickgold.florisboard.app.settings.formatting.loadVoiceSource
import dev.patrickgold.florisboard.app.settings.formatting.getSelectedMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID

class VoiceInputManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val NETWORK_TIMEOUT_MS = 1_800_000L // 30 minutes for Chirp API
    }
    private var speechRecognizer: SpeechRecognizer? = null
    private var formattingJob: Job? = null
    private var audioRecorder: AudioRecorder? = null
    private var recordingFile: File? = null
    
    // Inline voice recording controller
    val inlineRecorderController = InlineVoiceRecorderController(context, scope)
    
    // Legacy flows for backward compatibility - now derived from inline controller
    val isRecordingFlow = inlineRecorderController.isRecording
    val isProcessingFlow = inlineRecorderController.isProcessing
    
    // Legacy properties for backward compatibility
    private var isRecording: Boolean
        get() = inlineRecorderController.isRecording.value
        set(value) {
            // This is now managed by the state machine
            flogInfo(LogTopic.IMS_EVENTS) { "isRecording setter called with $value - this is now managed by state machine" }
        }
    
    private var isProcessing: Boolean
        get() = inlineRecorderController.isProcessing.value
        set(value) {
            // This is now managed by the state machine
            flogInfo(LogTopic.IMS_EVENTS) { "isProcessing setter called with $value - this is now managed by state machine" }
        }

    fun toggleVoiceInput() {
        val currentState = inlineRecorderController.currentState.value
        when (currentState) {
            InlineVoiceRecorderController.RecorderState.IDLE -> {
                startInlineVoiceInput()
            }
            InlineVoiceRecorderController.RecorderState.RECORDING -> {
                val requestId = inlineRecorderController.currentRequestId.value
                if (requestId != null) {
                    stopInlineRecording(requestId)
                }
            }
            InlineVoiceRecorderController.RecorderState.PROCESSING -> {
                // Do nothing - user should use cancel button
            }
            InlineVoiceRecorderController.RecorderState.CANCELLING -> {
                // Do nothing - already cancelling
            }
        }
    }

    /**
     * Start inline voice recording - replaces toolbar with recording UI
     */
    fun startInlineVoiceInput(): String? {
        flogInfo(LogTopic.IMS_EVENTS) { "VoiceInputManager.startInlineVoiceInput()" }
        
        // Runtime mic permission guard
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            context.showShortToastSync("Microphone permission required. Enable in Settings.")
            flogInfo(LogTopic.IMS_EVENTS) { "RECORD_AUDIO not granted" }
            return null
        }
        
        val requestId = inlineRecorderController.startInlineRecording()
        val voiceSource = loadVoiceSource(context)
        flogInfo(LogTopic.IMS_EVENTS) { "Voice source: $voiceSource" }
        
        // Ensure legacy voice overlay is not visible when using inline recording
        val keyboardManager by context.keyboardManager()
        CoroutineScope(Dispatchers.Main).launch {
            keyboardManager.activeState.batchEdit { it.isVoiceOverlayVisible = false }
            flogInfo(LogTopic.IMS_EVENTS) { "Cleared legacy voice overlay visibility for inline recording" }
        }
        
        // Language label will be updated automatically by InlineVoiceToolbar
        
        // Delay heavy operations to let UI update first
        scope.launch(Dispatchers.Main) {
            try {
                delay(100) // Give UI time to recompose and show toolbar
                flogInfo(LogTopic.IMS_EVENTS) { "Starting delayed voice recording setup" }
                
                // Check if we're still in recording state (user might have cancelled)
                if (!inlineRecorderController.isRecording.value) {
                    flogInfo(LogTopic.IMS_EVENTS) { "Recording was cancelled during delay, aborting" }
                    return@launch
                }
                
        if (voiceSource == "chirp") {
                    try {
            startChirpRecording()
                    } catch (e: Exception) {
                        flogInfo(LogTopic.IMS_EVENTS) { "Failed to start inline Chirp recording: ${e.message}" }
                        inlineRecorderController.cancelRecording()
                        Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
                    }
        } else {
                    flogInfo(LogTopic.IMS_EVENTS) { "About to start device recording" }
            startDeviceRecording()
                }
            } catch (e: Exception) {
                flogInfo(LogTopic.IMS_EVENTS) { "Error in delayed voice recording setup: ${e.message}" }
                inlineRecorderController.cancelRecording()
                Toast.makeText(context, "Failed to start voice recording", Toast.LENGTH_SHORT).show()
            }
        }
        
        return requestId
    }

    /**
     * Stop inline recording and start processing
     */
    fun stopInlineRecording(requestId: String) {
        flogInfo(LogTopic.IMS_EVENTS) { "VoiceInputManager.stopInlineRecording() - requestId: $requestId" }
        
        val voiceSource = loadVoiceSource(context)
        if (voiceSource == "chirp") {
            stopChirpRecordingInline(requestId)
        } else {
            cleanupRecognizer()
            // Transition to idle state instead of cancelling
            inlineRecorderController.transitionToIdle()
        }
    }

    /**
     * Cancel inline recording and restore toolbar
     */
    fun cancelInlineRecording() {
        flogInfo(LogTopic.IMS_EVENTS) { "VoiceInputManager.cancelInlineRecording()" }
        
        val requestId = inlineRecorderController.currentRequestId.value
        inlineRecorderController.cancelRecording()
        
        // Stop any ongoing recording
        isRecording = false
        cleanupRecognizer()
        audioRecorder?.cleanup()
        audioRecorder = null
        recordingFile = null
        
        // Cancel any pending formatting job
        formattingJob?.cancel()
        formattingJob = null
        
        // Ensure legacy voice overlay is not visible
        val keyboardManager by context.keyboardManager()
        CoroutineScope(Dispatchers.Main).launch {
            keyboardManager.activeState.batchEdit { it.isVoiceOverlayVisible = false }
            flogInfo(LogTopic.IMS_EVENTS) { "Cleared legacy voice overlay visibility on cancel" }
        }
    }

    /**
     * Cancel inline processing
     */
    fun cancelInlineProcessing() {
        flogInfo(LogTopic.IMS_EVENTS) { "VoiceInputManager.cancelInlineProcessing()" }
        
        val requestId = inlineRecorderController.currentRequestId.value
        if (requestId != null) {
            // Cancel the network request
            val client = FormatServiceClient(
                baseUrlProvider = { loadBaseUrl(context) },
                apiKeyProvider = { loadApiKey(context) },
            )
            client.cancel(requestId)
        }
        
        inlineRecorderController.cancelProcessing()
        
        // Cancel formatting job
        formattingJob?.cancel()
        formattingJob = null
    }
    
    private fun showProcessingSpinner() {
        // Processing state is now managed by the state machine
        flogInfo(LogTopic.IMS_EVENTS) { "Processing spinner shown - state managed by state machine" }
    }
    
    private fun hideProcessingSpinner() {
        // Processing state is now managed by the state machine
        flogInfo(LogTopic.IMS_EVENTS) { "Processing spinner hidden - state managed by state machine" }
    }
    
    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            flogInfo(LogTopic.IMS_EVENTS) { "Error stopping recognizer: ${e.message}" }
        }
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            flogInfo(LogTopic.IMS_EVENTS) { "Error destroying recognizer: ${e.message}" }
        }
        speechRecognizer = null
    }
    
    private fun stopRecording() {
        flogInfo(LogTopic.IMS_EVENTS) { "VoiceInputManager.stopRecording() - stopping recording only" }
        
        val voiceSource = loadVoiceSource(context)
        if (voiceSource == "chirp") {
            stopChirpRecording()
            // DON'T cancel formattingJob here - let the transcription complete
        } else {
            cleanupRecognizer()
            // Only cancel formatting job for device recording, not Chirp
            formattingJob?.cancel()
            formattingJob = null
        }
        // Don't close the voice panel - just stop recording
    }
    
    private fun stopChirpRecording() {
        scope.launch {
        try {
            val audioFile = audioRecorder?.stopRecording()
            if (audioFile != null && audioFile.exists()) {
                flogInfo(LogTopic.IMS_EVENTS) { "Chirp recording stopped, file: ${audioFile.name}" }
                handleChirpTranscription(audioFile)
            } else {
                flogInfo(LogTopic.IMS_EVENTS) { "No audio file recorded" }
                    withContext(Dispatchers.Main) {
                Toast.makeText(context, "No audio recorded", Toast.LENGTH_SHORT).show()
                    }
            }
        } catch (e: Exception) {
            flogInfo(LogTopic.IMS_EVENTS) { "Failed to stop Chirp recording: ${e.message}" }
                withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to stop recording", Toast.LENGTH_SHORT).show()
                }
        } finally {
            audioRecorder?.cleanup()
            audioRecorder = null
            recordingFile = null
            }
        }
    }

    private fun stopChirpRecordingInline(requestId: String) {
        scope.launch {
        try {
            val audioFile = audioRecorder?.stopRecording()
            if (audioFile != null && audioFile.exists()) {
                flogInfo(LogTopic.IMS_EVENTS) { "Chirp recording stopped inline, file: ${audioFile.name}" }
                handleChirpTranscriptionInline(audioFile, requestId)
            } else {
                flogInfo(LogTopic.IMS_EVENTS) { "No audio file recorded inline" }
                inlineRecorderController.cancelProcessing()
            }
        } catch (e: Exception) {
            flogInfo(LogTopic.IMS_EVENTS) { "Failed to stop Chirp recording inline: ${e.message}" }
            inlineRecorderController.cancelProcessing()
        } finally {
            audioRecorder?.cleanup()
            audioRecorder = null
            recordingFile = null
            }
        }
    }

    fun startVoiceInput() {
        flogInfo(LogTopic.IMS_EVENTS) { "VoiceInputManager.startVoiceInput()" }
        // Runtime mic permission guard
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            context.showShortToastSync("Microphone permission required. Enable in Settings.")
            flogInfo(LogTopic.IMS_EVENTS) { "RECORD_AUDIO not granted" }
            return
        }
        
        val voiceSource = loadVoiceSource(context)
        flogInfo(LogTopic.IMS_EVENTS) { "Voice source: $voiceSource" }
        
        // Always start recognition regardless of formatting toggle
        val keyboardManager by context.keyboardManager()
        CoroutineScope(Dispatchers.Main).launch {
            keyboardManager.activeState.batchEdit { it.isVoiceOverlayVisible = true }
            flogInfo(LogTopic.IMS_EVENTS) { "Voice overlay set visible (main)" }
        }
        
        if (voiceSource == "chirp") {
            startChirpRecording()
        } else {
            startDeviceRecording()
        }
    }
    
    private fun startChirpRecording() {
        try {
            audioRecorder = AudioRecorder(context)
            recordingFile = audioRecorder?.startRecording()
            flogInfo(LogTopic.IMS_EVENTS) { "Started Chirp audio recording" }
            context.showShortToastSync("Recording for Chirp…")
        } catch (e: Exception) {
            flogInfo(LogTopic.IMS_EVENTS) { "Failed to start Chirp recording: ${e.message}" }
            Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
            // Clean up on failure
            audioRecorder?.cleanup()
            audioRecorder = null
            recordingFile = null
        }
    }
    
    private fun startDeviceRecording() {
        flogInfo(LogTopic.IMS_EVENTS) { "startDeviceRecording() - checking recognition availability" }
        
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            context.showShortToastSync("Speech recognition not available")
            return
        }
        
        flogInfo(LogTopic.IMS_EVENTS) { "startDeviceRecording() - recognition available, starting coroutine" }
        
        // Move SpeechRecognizer creation to a coroutine to avoid blocking
        scope.launch(Dispatchers.Main) {
            try {
                flogInfo(LogTopic.IMS_EVENTS) { "startDeviceRecording() - inside coroutine, cleaning up previous recognizer" }
                
                // Check if we're still in recording state
                if (!inlineRecorderController.isRecording.value) {
                    flogInfo(LogTopic.IMS_EVENTS) { "Recording was cancelled, aborting SpeechRecognizer creation" }
                    return@launch
                }
                
                // Clean up previous recognizer instance
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
                
                flogInfo(LogTopic.IMS_EVENTS) { "startDeviceRecording() - creating SpeechRecognizer" }
                
                // Create SpeechRecognizer on main thread (required by Android)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
                    flogInfo(LogTopic.IMS_EVENTS) { "startDeviceRecording() - SpeechRecognizer created, setting up listener" }
                    
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    flogInfo(LogTopic.IMS_EVENTS) { "Voice error=$error" }
                    // Handle different error types
                    when (error) {
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            // Critical errors - stop recording
                                    CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "Voice input error $error", Toast.LENGTH_SHORT).show()
                                    }
                        }
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            // No speech detected - just restart listening
                            if (inlineRecorderController.isRecording.value) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    kotlinx.coroutines.delay(100)
                                    if (inlineRecorderController.isRecording.value) {
                                        restartListening()
                                    }
                                }
                            }
                        }
                        else -> {
                            // Other errors - try to recreate the recognizer
                            if (inlineRecorderController.isRecording.value) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    kotlinx.coroutines.delay(200)
                                    if (inlineRecorderController.isRecording.value) {
                                        recreateRecognizer()
                                    }
                                }
                            }
                        }
                    }
                }
                override fun onResults(results: Bundle) {
                    val list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = list?.firstOrNull().orEmpty()
                    flogInfo(LogTopic.IMS_EVENTS) { "Voice results len=${transcript.length}" }
                    
                    if (transcript.isNotEmpty()) {
                        handleTranscript(transcript)
                    }
                    
                    // Automatically restart listening to keep recording continuous
                    if (inlineRecorderController.isRecording.value) {
                        CoroutineScope(Dispatchers.Main).launch {
                            kotlinx.coroutines.delay(100)
                            if (inlineRecorderController.isRecording.value) {
                                restartListening()
                            }
                        }
                    }
                }
                override fun onPartialResults(partialResults: Bundle) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
                    
                    flogInfo(LogTopic.IMS_EVENTS) { "startDeviceRecording() - listener set, starting background setup" }
                    
                    // Start listening on main thread (required by Android)
                    try {
                        flogInfo(LogTopic.IMS_EVENTS) { "startDeviceRecording() - creating intent" }
                        
            val selectedLanguage = dev.patrickgold.florisboard.app.settings.formatting.loadLanguage(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                // Use selected language or auto-detect
                if (selectedLanguage != "auto") {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage)
                } else {
                    // Don't set language to let Google auto-detect
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toString())
                }
            }
                        
                        flogInfo(LogTopic.IMS_EVENTS) { "startDeviceRecording() - starting listening" }
            sr.startListening(intent)
                        
                        flogInfo(LogTopic.IMS_EVENTS) { "startDeviceRecording() - listening started successfully" }
                        context.showShortToastSync("Listening…")
                        
                    } catch (e: Exception) {
                        flogInfo(LogTopic.IMS_EVENTS) { "Failed to start device recording: ${e.message}" }
                        Toast.makeText(context, "Failed to start voice recognition", Toast.LENGTH_SHORT).show()
                        inlineRecorderController.cancelRecording()
                    }
                }
                
            } catch (e: Exception) {
                flogInfo(LogTopic.IMS_EVENTS) { "Failed to create SpeechRecognizer: ${e.message}" }
                Toast.makeText(context, "Failed to start voice recognition", Toast.LENGTH_SHORT).show()
                inlineRecorderController.cancelRecording()
            }
        }
    }

    private fun restartListening() {
        flogInfo(LogTopic.IMS_EVENTS) { "VoiceInputManager.restartListening()" }
        
        // Restart listening on main thread (required by Android)
        scope.launch(Dispatchers.Main) {
            try {
        val selectedLanguage = dev.patrickgold.florisboard.app.settings.formatting.loadLanguage(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            if (selectedLanguage != "auto") {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage)
            } else {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toString())
            }
        }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            flogInfo(LogTopic.IMS_EVENTS) { "Failed to restart listening: ${e.message}" }
            // If restart fails, try creating a new recognizer
            if (isRecording) {
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(200)
                    if (isRecording) {
                        recreateRecognizer()
                        }
                    }
                }
            }
        }
    }

    private fun recreateRecognizer() {
        flogInfo(LogTopic.IMS_EVENTS) { "VoiceInputManager.recreateRecognizer()" }
        
        // Move SpeechRecognizer recreation to a coroutine to avoid blocking
        scope.launch(Dispatchers.Main) {
            try {
        speechRecognizer?.destroy()
        speechRecognizer = null
        
                // Create SpeechRecognizer on main thread (required by Android)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    flogInfo(LogTopic.IMS_EVENTS) { "Voice error=$error" }
                    when (error) {
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                    CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "Voice input error $error", Toast.LENGTH_SHORT).show()
                                    }
                        }
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            if (inlineRecorderController.isRecording.value) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    kotlinx.coroutines.delay(100)
                                    if (inlineRecorderController.isRecording.value) {
                                        restartListening()
                                    }
                                }
                            }
                        }
                        else -> {
                            if (inlineRecorderController.isRecording.value) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    kotlinx.coroutines.delay(200)
                                    if (inlineRecorderController.isRecording.value) {
                                        recreateRecognizer()
                                    }
                                }
                            }
                        }
                    }
                }
                override fun onResults(results: Bundle) {
                    val list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = list?.firstOrNull().orEmpty()
                    flogInfo(LogTopic.IMS_EVENTS) { "Voice results len=${transcript.length}" }
                    
                    if (transcript.isNotEmpty()) {
                        handleTranscript(transcript)
                    }
                    
                    if (inlineRecorderController.isRecording.value) {
                        CoroutineScope(Dispatchers.Main).launch {
                            kotlinx.coroutines.delay(100)
                            if (inlineRecorderController.isRecording.value) {
                                restartListening()
                            }
                        }
                    }
                }
                override fun onPartialResults(partialResults: Bundle) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
                    
                    // Start listening with a small delay
                    scope.launch(Dispatchers.IO) {
                        try {
                            delay(100)
        
        val selectedLanguage = dev.patrickgold.florisboard.app.settings.formatting.loadLanguage(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            if (selectedLanguage != "auto") {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage)
            } else {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toString())
            }
        }
                            sr.startListening(intent)
                            
                        } catch (e: Exception) {
                            flogInfo(LogTopic.IMS_EVENTS) { "Failed to recreate recognizer: ${e.message}" }
                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(context, "Voice recognition failed", Toast.LENGTH_SHORT).show()
                                inlineRecorderController.cancelRecording()
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                flogInfo(LogTopic.IMS_EVENTS) { "Failed to create SpeechRecognizer: ${e.message}" }
                Toast.makeText(context, "Voice recognition failed", Toast.LENGTH_SHORT).show()
                inlineRecorderController.cancelRecording()
            }
        }
    }

    fun stopVoiceInput() {
        flogInfo(LogTopic.IMS_EVENTS) { "VoiceInputManager.stopVoiceInput()" }
        cleanupRecognizer()
        formattingJob?.cancel()
        formattingJob = null
        // Hide spinner if showing
        hideProcessingSpinner()
        val keyboardManager by context.keyboardManager()
        CoroutineScope(Dispatchers.Main).launch {
            keyboardManager.activeState.batchEdit { it.isVoiceOverlayVisible = false }
        }
    }

    private fun handleChirpTranscription(audioFile: File) {
        flogInfo(LogTopic.IMS_EVENTS) { "handleChirpTranscription() - Starting transcription for file: ${audioFile.name}" }
        val editorInstance by context.editorInstance()
        val requestId = UUID.randomUUID().toString()
        
        // Show spinner and progress indicator
        showProcessingSpinner()
        context.showShortToastSync("Transcribing…")
        
        val baseUrl = loadBaseUrl(context)
        val apiKey = loadApiKey(context)
        flogInfo(LogTopic.IMS_EVENTS) { "handleChirpTranscription() - Base URL: $baseUrl, API Key: ${apiKey?.take(8)}..." }
        flogInfo(LogTopic.IMS_EVENTS) { "handleChirpTranscription() - Base URL is null: ${baseUrl == null}, API Key is null: ${apiKey == null}" }
        
        val client = FormatServiceClient(
            baseUrlProvider = { loadBaseUrl(context) },
            apiKeyProvider = { loadApiKey(context) },
        )
        
        flogInfo(LogTopic.IMS_EVENTS) { "handleChirpTranscription() - About to launch coroutine" }
        
        // DON'T cancel existing job - let Chirp transcriptions run independently
        // Only cancel if user explicitly stops voice input
        val chirpJob = scope.launch(Dispatchers.IO) {
            try {
                val enableFormatting = loadEnabled(context)
                val selectedLanguage = loadLanguage(context)
                val selectedMode = getSelectedMode(context)
                val promptTemplate = if (selectedMode?.isDefaultMode() == false) selectedMode.template else null
                val modeTitle = selectedMode?.title
                
                flogInfo(LogTopic.IMS_EVENTS) { "handleChirpTranscription() - Inside coroutine, about to call transcribeWithChirp with enableFormatting: $enableFormatting, language: $selectedLanguage, modeTitle: $modeTitle, hasCustomPrompt: ${promptTemplate != null}" }
                
                val response = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                    client.transcribeWithChirp(audioFile, requestId, enableFormatting, selectedLanguage, promptTemplate, modeTitle)
                }
                
                if (response != null) {
                    flogInfo(LogTopic.IMS_EVENTS) { "handleChirpTranscription() - Received response from transcribeWithChirp" }
                    withContext(Dispatchers.Main) {
                        editorInstance.commitText(response.formattedText)
                        flogInfo(LogTopic.IMS_EVENTS) { "Chirp transcription completed: ${response.formattedText.take(50)}..." }
                    }
                } else {
                    flogInfo(LogTopic.IMS_EVENTS) { "Chirp transcription timeout" }
                    withContext(Dispatchers.Main) { 
                        Toast.makeText(context, "Transcription timeout — please try again", Toast.LENGTH_SHORT).show() 
                    }
                }
            } catch (auth: FormatServiceClient.AuthException) {
                withContext(Dispatchers.Main) { 
                    Toast.makeText(context, "Chirp auth failed", Toast.LENGTH_SHORT).show() 
                }
                flogInfo(LogTopic.IMS_EVENTS) { "Chirp auth failed" }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    flogInfo(LogTopic.IMS_EVENTS) { "Chirp transcription cancelled" }
                } else {
                    flogInfo(LogTopic.IMS_EVENTS) { "Chirp transcription failed: ${e.message}" }
                    withContext(Dispatchers.Main) { 
                        Toast.makeText(context, "Chirp transcription failed", Toast.LENGTH_SHORT).show() 
                    }
                }
            } finally {
                // Clean up audio file
                try {
                    if (audioFile.exists()) {
                        audioFile.delete()
                    }
                } catch (e: Exception) {
                    flogInfo(LogTopic.IMS_EVENTS) { "Failed to delete audio file: ${e.message}" }
                }
                
                // Complete processing - state machine will handle UI updates
                withContext(Dispatchers.Main) {
                    inlineRecorderController.completeProcessing()
                }
            }
        }
        
        // Store the job so it can be cancelled when the user explicitly stops voice input
        formattingJob = chirpJob
    }

    private fun handleChirpTranscriptionInline(audioFile: File, requestId: String) {
        flogInfo(LogTopic.IMS_EVENTS) { "handleChirpTranscriptionInline() - Starting inline transcription for file: ${audioFile.name}, requestId: $requestId" }
        val editorInstance by context.editorInstance()
        
        // Start inline processing
        inlineRecorderController.startProcessing(requestId)
        
        val baseUrl = loadBaseUrl(context)
        val apiKey = loadApiKey(context)
        flogInfo(LogTopic.IMS_EVENTS) { "handleChirpTranscriptionInline() - Base URL: $baseUrl, API Key: ${apiKey?.take(8)}..." }
        
        val client = FormatServiceClient(
            baseUrlProvider = { loadBaseUrl(context) },
            apiKeyProvider = { loadApiKey(context) },
        )
        
        // Create inline formatting job
        val inlineJob = scope.launch(Dispatchers.IO) {
            try {
                val enableFormatting = loadEnabled(context)
                val selectedLanguage = loadLanguage(context)
                val selectedMode = getSelectedMode(context)
                val promptTemplate = if (selectedMode?.isDefaultMode() == false) selectedMode.template else null
                val modeTitle = selectedMode?.title
                
                flogInfo(LogTopic.IMS_EVENTS) { "handleChirpTranscriptionInline() - Calling transcribeWithChirp with enableFormatting: $enableFormatting, language: $selectedLanguage, modeTitle: $modeTitle, hasCustomPrompt: ${promptTemplate != null}" }
                
                val response = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                    client.transcribeWithChirp(audioFile, requestId, enableFormatting, selectedLanguage, promptTemplate, modeTitle)
                }
                
                if (response != null) {
                    flogInfo(LogTopic.IMS_EVENTS) { "handleChirpTranscriptionInline() - Received response from transcribeWithChirp" }
                    withContext(Dispatchers.Main) {
                        editorInstance.commitText(response.formattedText)
                        flogInfo(LogTopic.IMS_EVENTS) { "Inline Chirp transcription completed: ${response.formattedText.take(50)}..." }
                        inlineRecorderController.completeProcessing()
                    }
                } else {
                    flogInfo(LogTopic.IMS_EVENTS) { "Inline Chirp transcription timeout" }
                    withContext(Dispatchers.Main) { 
                        Toast.makeText(context, "Transcription timeout — please try again", Toast.LENGTH_SHORT).show()
                        inlineRecorderController.cancelProcessing()
                    }
                }
            } catch (auth: FormatServiceClient.AuthException) {
                withContext(Dispatchers.Main) { 
                    Toast.makeText(context, "Chirp auth failed", Toast.LENGTH_SHORT).show()
                    inlineRecorderController.cancelProcessing()
                }
                flogInfo(LogTopic.IMS_EVENTS) { "Inline Chirp auth failed" }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    flogInfo(LogTopic.IMS_EVENTS) { "Inline Chirp transcription cancelled" }
                    // Don't call cancelProcessing on cancellation - it's already handled
                } else {
                    flogInfo(LogTopic.IMS_EVENTS) { "Inline Chirp transcription failed: ${e.message}" }
                    withContext(Dispatchers.Main) { 
                        Toast.makeText(context, "Chirp transcription failed", Toast.LENGTH_SHORT).show()
                        inlineRecorderController.cancelProcessing()
                    }
                }
            } finally {
                // Clean up audio file
                try {
                    if (audioFile.exists()) {
                        audioFile.delete()
                    }
                } catch (e: Exception) {
                    flogInfo(LogTopic.IMS_EVENTS) { "Failed to delete audio file: ${e.message}" }
                }
            }
        }
        
        // Store the job for potential cancellation
        formattingJob = inlineJob
    }

    private fun handleTranscript(raw: String) {
        val editorInstance by context.editorInstance()
        val requestId = UUID.randomUUID().toString()
        
        // Show spinner and start processing
        showProcessingSpinner()
        context.showShortToastSync("Formatting…")
        
        val client = FormatServiceClient(
            baseUrlProvider = { loadBaseUrl(context) },
            apiKeyProvider = { loadApiKey(context) },
        )
        formattingJob?.cancel()
        formattingJob = scope.launch(Dispatchers.IO) {
            try {
                val useFormatting = loadEnabled(context)
                val selectedMode = getSelectedMode(context)
                val promptTemplate = if (selectedMode?.isDefaultMode() == false) selectedMode.template else null
                val modeTitle = selectedMode?.title
                
                val formatted = if (useFormatting) {
                    withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                        try {
                            client.formatTranscript(raw, requestId, promptTemplate, modeTitle).formattedText
                        } catch (auth: FormatServiceClient.AuthException) {
                            withContext(Dispatchers.Main) { 
                                Toast.makeText(context, "Invalid formatting key", Toast.LENGTH_SHORT).show() 
                            }
                            raw
                        } catch (_: Throwable) {
                            withContext(Dispatchers.Main) { 
                                Toast.makeText(context, "Formatting failed — using raw transcript", Toast.LENGTH_SHORT).show() 
                            }
                            raw
                        }
                    } ?: run {
                        withContext(Dispatchers.Main) { 
                            Toast.makeText(context, "Formatting timeout — using raw transcript", Toast.LENGTH_SHORT).show() 
                        }
                        raw
                    }
                } else raw
                
                withContext(Dispatchers.Main) {
                    editorInstance.commitText(formatted)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    hideProcessingSpinner()
                }
            }
        }
    }

}


