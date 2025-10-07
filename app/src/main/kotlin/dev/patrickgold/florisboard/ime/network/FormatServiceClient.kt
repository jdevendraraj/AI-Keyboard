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

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun formatTranscript(transcript: String, requestId: String?): FormatResponse =
        withContext(Dispatchers.IO) {
            val baseUrl = baseUrlProvider()?.trimEnd('/') ?: throw IllegalStateException("No base URL configured")
            val apiKey = apiKeyProvider() ?: throw IllegalStateException("No API key configured")

            val bodyObj = FormatRequest(transcript = transcript, requestId = requestId)
            val body = json.encodeToString(bodyObj).toRequestBody(jsonMedia)

            val url = "$baseUrl/api/format"
            val req = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-client-key", apiKey)
                .addHeader("ngrok-skip-browser-warning", "true")
                .post(body)
                .build()

            executeWithRetry(req) { respBody ->
                json.decodeFromString(FormatResponse.serializer(), respBody)
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
            executeWithRetryChirp(req) { respBody ->
                Log.d("FormatServiceClient", "transcribeWithChirp - Received response, parsing...")
                json.decodeFromString(ChirpTranscribeResponse.serializer(), respBody)
            }
        }

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider()?.trimEnd('/') ?: return@withContext false
        val url = "$baseUrl/health"
        val req = Request.Builder()
            .url(url)
            .addHeader("ngrok-skip-browser-warning", "true")
            .get()
            .build()
        return@withContext try {
            client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (t: Throwable) {
            false
        }
    }

    private suspend fun <T> executeWithRetry(
        request: Request,
        parser: (String) -> T,
    ): T {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < 2) {
            try {
                client.newCall(request).execute().use { resp ->
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
            }
        }
        throw lastError ?: RuntimeException("Unknown error")
    }

    private suspend fun <T> executeWithRetryChirp(
        request: Request,
        parser: (String) -> T,
    ): T {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < 2) {
            try {
                chirpClient.newCall(request).execute().use { resp ->
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
            }
        }
        throw lastError ?: RuntimeException("Unknown error")
    }

    class AuthException : Exception()
}


