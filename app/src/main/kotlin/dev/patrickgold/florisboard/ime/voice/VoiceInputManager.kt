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
        private const val NETWORK_TIMEOUT_MS = 30_000L // 30 seconds
    }
    private var speechRecognizer: SpeechRecognizer? = null
    private var formattingJob: Job? = null
    private var audioRecorder: AudioRecorder? = null
    private var recordingFile: File? = null
    
    private val _isRecordingFlow = MutableStateFlow(false)
    val isRecordingFlow = _isRecordingFlow.asStateFlow()
    
    private val _isProcessingFlow = MutableStateFlow(false)
    val isProcessingFlow = _isProcessingFlow.asStateFlow()
    
    private var isRecording: Boolean
        get() = _isRecordingFlow.value
        set(value) {
            _isRecordingFlow.value = value
        }
    
    private var isProcessing: Boolean
        get() = _isProcessingFlow.value
        set(value) {
            _isProcessingFlow.value = value
        }

    fun toggleVoiceInput() {
        if (isRecording) {
            stopRecording()
        } else {
            startVoiceInput()
        }
    }
    
    private fun showProcessingSpinner() {
        isProcessing = true
        flogInfo(LogTopic.IMS_EVENTS) { "Processing spinner shown" }
    }
    
    private fun hideProcessingSpinner() {
        isProcessing = false
        flogInfo(LogTopic.IMS_EVENTS) { "Processing spinner hidden" }
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
        isRecording = false
        
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
        try {
            val audioFile = audioRecorder?.stopRecording()
            if (audioFile != null && audioFile.exists()) {
                flogInfo(LogTopic.IMS_EVENTS) { "Chirp recording stopped, file: ${audioFile.name}" }
                handleChirpTranscription(audioFile)
            } else {
                flogInfo(LogTopic.IMS_EVENTS) { "No audio file recorded" }
                Toast.makeText(context, "No audio recorded", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            flogInfo(LogTopic.IMS_EVENTS) { "Failed to stop Chirp recording: ${e.message}" }
            Toast.makeText(context, "Failed to stop recording", Toast.LENGTH_SHORT).show()
        } finally {
            audioRecorder?.cleanup()
            audioRecorder = null
            recordingFile = null
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
            isRecording = true
            flogInfo(LogTopic.IMS_EVENTS) { "Started Chirp audio recording" }
            context.showShortToastSync("Recording for Chirp…")
        } catch (e: Exception) {
            flogInfo(LogTopic.IMS_EVENTS) { "Failed to start Chirp recording: ${e.message}" }
            isRecording = false
            Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startDeviceRecording() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            context.showShortToastSync("Speech recognition not available")
            return
        }
        
        // Clean up previous recognizer instance without hiding overlay
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isRecording = true
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
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
                            isRecording = false
                            Toast.makeText(context, "Voice input error $error", Toast.LENGTH_SHORT).show()
                        }
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            // No speech detected - just restart listening
                            if (isRecording) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    kotlinx.coroutines.delay(100)
                                    if (isRecording) {
                                        restartListening()
                                    }
                                }
                            }
                        }
                        else -> {
                            // Other errors - try to recreate the recognizer
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
                override fun onResults(results: Bundle) {
                    val list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = list?.firstOrNull().orEmpty()
                    flogInfo(LogTopic.IMS_EVENTS) { "Voice results len=${transcript.length}" }
                    
                    if (transcript.isNotEmpty()) {
                        handleTranscript(transcript)
                    }
                    
                    // Automatically restart listening to keep recording continuous
                    if (isRecording) {
                        CoroutineScope(Dispatchers.Main).launch {
                            kotlinx.coroutines.delay(100)
                            if (isRecording) {
                                restartListening()
                            }
                        }
                    }
                }
                override fun onPartialResults(partialResults: Bundle) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val selectedLanguage = dev.patrickgold.florisboard.app.settings.formatting.loadLanguage(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                // Use selected language or auto-detect
                if (selectedLanguage != "auto") {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage)
                } else {
                    // Don't set language to let Google auto-detect
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                }
            }
            sr.startListening(intent)
        }
        context.showShortToastSync("Listening…")
    }

    private fun restartListening() {
        flogInfo(LogTopic.IMS_EVENTS) { "VoiceInputManager.restartListening()" }
        // Don't call stopListening() - just start listening again directly
        // The recognizer will handle the transition automatically
        val selectedLanguage = dev.patrickgold.florisboard.app.settings.formatting.loadLanguage(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            if (selectedLanguage != "auto") {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage)
            } else {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            }
        }
        try {
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

    private fun recreateRecognizer() {
        flogInfo(LogTopic.IMS_EVENTS) { "VoiceInputManager.recreateRecognizer()" }
        speechRecognizer?.destroy()
        speechRecognizer = null
        
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
                            isRecording = false
                            Toast.makeText(context, "Voice input error $error", Toast.LENGTH_SHORT).show()
                        }
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            if (isRecording) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    kotlinx.coroutines.delay(100)
                                    if (isRecording) {
                                        restartListening()
                                    }
                                }
                            }
                        }
                        else -> {
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
                override fun onResults(results: Bundle) {
                    val list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = list?.firstOrNull().orEmpty()
                    flogInfo(LogTopic.IMS_EVENTS) { "Voice results len=${transcript.length}" }
                    
                    if (transcript.isNotEmpty()) {
                        handleTranscript(transcript)
                    }
                    
                    if (isRecording) {
                        CoroutineScope(Dispatchers.Main).launch {
                            kotlinx.coroutines.delay(100)
                            if (isRecording) {
                                restartListening()
                            }
                        }
                    }
                }
                override fun onPartialResults(partialResults: Bundle) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        
        val selectedLanguage = dev.patrickgold.florisboard.app.settings.formatting.loadLanguage(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            if (selectedLanguage != "auto") {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage)
            } else {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            }
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopVoiceInput() {
        flogInfo(LogTopic.IMS_EVENTS) { "VoiceInputManager.stopVoiceInput()" }
        isRecording = false
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
                flogInfo(LogTopic.IMS_EVENTS) { "handleChirpTranscription() - Inside coroutine, about to call transcribeWithChirp with enableFormatting: $enableFormatting, language: $selectedLanguage" }
                
                val response = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                    client.transcribeWithChirp(audioFile, requestId, enableFormatting, selectedLanguage)
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
                
                // Hide spinner
                withContext(Dispatchers.Main) {
                    hideProcessingSpinner()
                }
            }
        }
        
        // Store the job so it can be cancelled when the user explicitly stops voice input
        formattingJob = chirpJob
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
                val formatted = if (useFormatting) {
                    withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                        try {
                            client.formatTranscript(raw, requestId).formattedText
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


