package dev.patrickgold.florisboard.ime.network

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * A small OkHttp-based client for the formatting backend.
 * Note: Do not log full transcripts; only log a short preview and requestId.
 */
@Serializable
data class FormatResponse(
    @SerialName("formattedText") val formattedText: String,
    @SerialName("requestId") val requestId: String? = null,
)

@Serializable
data class ChirpTranscribeResponse(
    @SerialName("formattedText") val formattedText: String,
    @SerialName("requestId") val requestId: String? = null,
    @SerialName("rawTranscription") val rawTranscription: String? = null,
    @SerialName("usage") val usage: Map<String, Int>? = null,
)

interface IFormatServiceClient {
    suspend fun formatTranscript(transcript: String, requestId: String?): FormatResponse
    suspend fun transcribeWithChirp(audioFile: File, requestId: String, enableFormatting: Boolean = true, language: String = "auto"): ChirpTranscribeResponse
    suspend fun healthCheck(): Boolean
}

class FormatServiceClient(
    private val baseUrlProvider: () -> String?,
    private val apiKeyProvider: () -> String?,
    connectTimeoutSeconds: Long = 3,
    readTimeoutSeconds: Long = 6,
    chirpTimeoutSeconds: Long = 10,
) : IFormatServiceClient {

    @Serializable
    private data class FormatRequest(
        val transcript: String,
        val requestId: String? = null,
    )

    // Response defined top-level

    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .build()
        
    private val chirpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(chirpTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    // Store active calls for cancellation
    private val activeCalls = mutableMapOf<String, okhttp3.Call>()

    /**
     * Cancel an active request by requestId
     */
    fun cancel(requestId: String) {
        activeCalls[requestId]?.cancel()
        activeCalls.remove(requestId)
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun formatTranscript(transcript: String, requestId: String?): FormatResponse =
        withContext(Dispatchers.IO) {
            val baseUrl = baseUrlProvider()?.trimEnd('/') ?: throw IllegalStateException("No base URL configured")
            val apiKey = apiKeyProvider() ?: throw IllegalStateException("No API key configured")

            Log.d("FormatServiceClient", "=== FORMAT TRANSCRIPT START ===")
            Log.d("FormatServiceClient", "Base URL: $baseUrl")
            Log.d("FormatServiceClient", "API Key: ${apiKey.take(8)}...")
            Log.d("FormatServiceClient", "Transcript length: ${transcript.length}")
            Log.d("FormatServiceClient", "Request ID: $requestId")

            val bodyObj = FormatRequest(transcript = transcript, requestId = requestId)
            val body = json.encodeToString(bodyObj).toRequestBody(jsonMedia)

            val url = "$baseUrl/api/format"
            Log.d("FormatServiceClient", "Full URL: $url")
            
            val req = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-client-key", apiKey)
                .addHeader("ngrok-skip-browser-warning", "true")
                .post(body)
                .build()

            Log.d("FormatServiceClient", "Request built, executing...")

            executeWithRetry(req, requestId) { respBody ->
                Log.d("FormatServiceClient", "Response received, parsing...")
                Log.d("FormatServiceClient", "Response body: $respBody")
                val result = json.decodeFromString(FormatResponse.serializer(), respBody)
                Log.d("FormatServiceClient", "Parsed result: $result")
                Log.d("FormatServiceClient", "=== FORMAT TRANSCRIPT END ===")
                result
            }
        }

    override suspend fun transcribeWithChirp(audioFile: File, requestId: String, enableFormatting: Boolean, language: String): ChirpTranscribeResponse =
        withContext(Dispatchers.IO) {
            val baseUrl = baseUrlProvider()?.trimEnd('/') ?: throw IllegalStateException("No base URL configured")
            val apiKey = apiKeyProvider() ?: throw IllegalStateException("No API key configured")

            Log.d("FormatServiceClient", "transcribeWithChirp - baseUrl: $baseUrl, apiKey: ${apiKey.take(8)}..., file: ${audioFile.name}, requestId: $requestId, enableFormatting: $enableFormatting, language: $language")

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("requestId", requestId)
                .addFormDataPart("enableFormatting", enableFormatting.toString())
                .build()

            val url = "$baseUrl/api/transcribe-chirp?language=$language"
            Log.d("FormatServiceClient", "transcribeWithChirp - URL: $url")
            
            val req = Request.Builder()
                .url(url)
                .addHeader("x-client-key", apiKey)
                .addHeader("Accept", "application/json")
                .addHeader("ngrok-skip-browser-warning", "true")
                .post(requestBody)
                .build()

            Log.d("FormatServiceClient", "transcribeWithChirp - About to execute request")
            executeWithRetryChirp(req, requestId) { respBody ->
                Log.d("FormatServiceClient", "transcribeWithChirp - Received response, parsing...")
                json.decodeFromString(ChirpTranscribeResponse.serializer(), respBody)
            }
        }

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider()?.trimEnd('/') ?: return@withContext false
        val url = "$baseUrl/health"
        
        Log.d("FormatServiceClient", "=== HEALTH CHECK START ===")
        Log.d("FormatServiceClient", "Base URL: $baseUrl")
        Log.d("FormatServiceClient", "Full URL: $url")
        
        val req = Request.Builder()
            .url(url)
            .addHeader("ngrok-skip-browser-warning", "true")
            .get()
            .build()
            
        Log.d("FormatServiceClient", "Request built, executing...")
        
        return@withContext try {
            client.newCall(req).execute().use { resp ->
                Log.d("FormatServiceClient", "Response received:")
                Log.d("FormatServiceClient", "  Status Code: ${resp.code}")
                Log.d("FormatServiceClient", "  Message: ${resp.message}")
                Log.d("FormatServiceClient", "  Headers: ${resp.headers}")
                
                val body = resp.body?.string() ?: ""
                Log.d("FormatServiceClient", "  Body: $body")
                
                val isSuccessful = resp.isSuccessful
                Log.d("FormatServiceClient", "  Is Successful: $isSuccessful")
                Log.d("FormatServiceClient", "=== HEALTH CHECK END ===")
                
                isSuccessful
            }
        } catch (t: Throwable) {
            Log.e("FormatServiceClient", "Health check failed with exception", t)
            Log.e("FormatServiceClient", "Exception message: ${t.message}")
            Log.e("FormatServiceClient", "Exception type: ${t.javaClass.simpleName}")
            Log.e("FormatServiceClient", "=== HEALTH CHECK END (ERROR) ===")
            false
        }
    }

    private suspend fun <T> executeWithRetry(
        request: Request,
        requestId: String?,
        parser: (String) -> T,
    ): T {
        var attempt = 0
        var lastError: Throwable? = null
        Log.d("FormatServiceClient", "Starting executeWithRetry, max attempts: 2")
        
        while (attempt < 2) {
            try {
                Log.d("FormatServiceClient", "Attempt ${attempt + 1}/2")
                val call = client.newCall(request)
                
                // Store the call for potential cancellation
                if (requestId != null) {
                    activeCalls[requestId] = call
                }
                
                call.execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    Log.d("FormatServiceClient", "Response - Code: ${resp.code}, Body: $body")
                    
                    if (resp.isSuccessful) {
                        Log.d("FormatServiceClient", "Request successful, parsing response")
                        return parser(body)
                    }
                    if (resp.code in 500..599) {
                        Log.e("FormatServiceClient", "Server error: ${resp.code}")
                        throw RuntimeException("Server error: ${resp.code}")
                    } else if (resp.code == 401) {
                        Log.e("FormatServiceClient", "Authentication error: ${resp.code}")
                        throw AuthException()
                    } else {
                        Log.e("FormatServiceClient", "HTTP error: ${resp.code}")
                        throw RuntimeException("HTTP ${resp.code}")
                    }
                }
            } catch (t: Throwable) {
                lastError = t
                attempt += 1
                Log.e("FormatServiceClient", "Attempt ${attempt} failed: ${t.message}")
                if (attempt >= 2) break
                Log.d("FormatServiceClient", "Retrying in 200ms...")
                // small backoff
                delay(200)
            } finally {
                // Remove the call from active calls
                if (requestId != null) {
                    activeCalls.remove(requestId)
                }
            }
        }
        Log.e("FormatServiceClient", "All attempts failed, throwing last error")
        throw lastError ?: RuntimeException("Unknown error")
    }

    private suspend fun <T> executeWithRetryChirp(
        request: Request,
        requestId: String,
        parser: (String) -> T,
    ): T {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < 2) {
            try {
                val call = chirpClient.newCall(request)
                
                // Store the call for potential cancellation
                activeCalls[requestId] = call
                
                call.execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        return parser(body)
                    }
                    if (resp.code in 500..599) {
                        throw RuntimeException("Server error: ${resp.code}")
                    } else if (resp.code == 401) {
                        throw AuthException()
                    } else {
                        throw RuntimeException("HTTP ${resp.code}")
                    }
                }
            } catch (t: Throwable) {
                lastError = t
                attempt += 1
                if (attempt >= 2) break
                // small backoff
                delay(200)
            } finally {
                // Remove the call from active calls
                activeCalls.remove(requestId)
            }
        }
        throw lastError ?: RuntimeException("Unknown error")
    }

    class AuthException : Exception()
}


