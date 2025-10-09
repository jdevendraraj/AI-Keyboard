package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Records audio for Chirp cloud transcription.
 * Records in WAV PCM16 mono 16kHz format for best compatibility.
 */
class AudioRecorder(private val context: Context) {
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private val tempDir = File(context.cacheDir, "voice_recording")
    
    init {
        tempDir.mkdirs()
    }
    
    fun startRecording(): File {
        if (isRecording) {
            throw IllegalStateException("Already recording")
        }
        
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        ) * 2
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord failed to initialize")
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to create AudioRecord", e)
            throw e
        }
        
        val audioFile = File(tempDir, "recording_${UUID.randomUUID()}.wav")
        isRecording = true
        
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                audioRecord?.startRecording()
                val outputStream = FileOutputStream(audioFile)
                
                // Write WAV header
                writeWavHeader(outputStream, sampleRate, 1, 16)
                
                val buffer = ByteArray(bufferSize)
                while (isRecording && isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
                
                outputStream.close()
                Log.d("AudioRecorder", "Recording saved to: ${audioFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Recording failed", e)
                audioFile.delete()
                throw e
            } finally {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            }
        }
        
        return audioFile
    }
    
    suspend fun stopRecording(): File? {
        if (!isRecording) {
            return null
        }
        
        isRecording = false
        recordingJob?.cancel()
        
        // Wait a bit for the recording to finish
        delay(100)
        
        // Find the most recent recording file
        val files = tempDir.listFiles { _, name -> name.startsWith("recording_") && name.endsWith(".wav") }
        return files?.maxByOrNull { it.lastModified() }
    }
    
    fun cleanup() {
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        // Clean up old recording files
        tempDir.listFiles()?.forEach { file ->
            if (file.lastModified() < System.currentTimeMillis() - 300000) { // 5 minutes
                file.delete()
            }
        }
    }
    
    private fun writeWavHeader(outputStream: FileOutputStream, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = 0 // Will be updated when recording stops
        val fileSize = 36 + dataSize
        
        // RIFF header
        outputStream.write("RIFF".toByteArray())
        outputStream.write(intToLittleEndian(fileSize))
        outputStream.write("WAVE".toByteArray())
        
        // fmt chunk
        outputStream.write("fmt ".toByteArray())
        outputStream.write(intToLittleEndian(16)) // fmt chunk size
        outputStream.write(shortToLittleEndian(1)) // audio format (PCM)
        outputStream.write(shortToLittleEndian(channels))
        outputStream.write(intToLittleEndian(sampleRate))
        outputStream.write(intToLittleEndian(byteRate))
        outputStream.write(shortToLittleEndian(blockAlign))
        outputStream.write(shortToLittleEndian(bitsPerSample))
        
        // data chunk
        outputStream.write("data".toByteArray())
        outputStream.write(intToLittleEndian(dataSize))
    }
    
    private fun intToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
    
    private fun shortToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    }
}
