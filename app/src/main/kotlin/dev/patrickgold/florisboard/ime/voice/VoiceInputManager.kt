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
import dev.patrickgold.florisboard.app.settings.formatting.loadApiKey
import dev.patrickgold.florisboard.app.settings.formatting.loadBaseUrl
import dev.patrickgold.florisboard.app.settings.formatting.loadEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

class VoiceInputManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var formattingJob: Job? = null
    private var isRecording = false

    fun isRecording(): Boolean = isRecording

    fun toggleVoiceInput() {
        if (isRecording) {
            stopVoiceInput()
        } else {
            startVoiceInput()
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
        // Always start recognition regardless of formatting toggle
        val keyboardManager by context.keyboardManager()
        CoroutineScope(Dispatchers.Main).launch {
            keyboardManager.activeState.batchEdit { it.isVoiceOverlayVisible = true }
            flogInfo(LogTopic.IMS_EVENTS) { "Voice overlay set visible (main)" }
        }
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
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            sr.startListening(intent)
        }
        context.showShortToastSync("Listening…")
    }

    private fun restartListening() {
        flogInfo(LogTopic.IMS_EVENTS) { "VoiceInputManager.restartListening()" }
        // Don't call stopListening() - just start listening again directly
        // The recognizer will handle the transition automatically
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
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
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopVoiceInput() {
        flogInfo(LogTopic.IMS_EVENTS) { "VoiceInputManager.stopVoiceInput()" }
        isRecording = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        formattingJob?.cancel()
        formattingJob = null
        val keyboardManager by context.keyboardManager()
        CoroutineScope(Dispatchers.Main).launch {
            keyboardManager.activeState.batchEdit { it.isVoiceOverlayVisible = false }
        }
    }

    private fun handleTranscript(raw: String) {
        val editorInstance by context.editorInstance()
        val requestId = UUID.randomUUID().toString()
        // show lightweight feedback
        context.showShortToastSync("Formatting…")
        val client = FormatServiceClient(
            baseUrlProvider = { loadBaseUrl(context) },
            apiKeyProvider = { loadApiKey(context) },
        )
        formattingJob?.cancel()
        formattingJob = scope.launch(Dispatchers.IO) {
            val useFormatting = loadEnabled(context)
            val formatted = if (useFormatting) {
                try {
                    client.formatTranscript(raw, requestId).formattedText
                } catch (auth: FormatServiceClient.AuthException) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Invalid formatting key", Toast.LENGTH_SHORT).show() }
                    raw
                } catch (_: Throwable) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Formatting failed — using raw transcript", Toast.LENGTH_SHORT).show() }
                    raw
                }
            } else raw
            withContext(Dispatchers.Main) {
                editorInstance.commitText(formatted)
            }
        }
    }
}


