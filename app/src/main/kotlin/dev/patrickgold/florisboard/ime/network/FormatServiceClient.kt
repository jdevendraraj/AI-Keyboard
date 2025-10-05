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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

interface IFormatServiceClient {
    suspend fun formatTranscript(transcript: String, requestId: String?): FormatResponse
    suspend fun healthCheck(): Boolean
}

class FormatServiceClient(
    private val baseUrlProvider: () -> String?,
    private val apiKeyProvider: () -> String?,
    connectTimeoutSeconds: Long = 3,
    readTimeoutSeconds: Long = 6,
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
                .post(body)
                .build()

            executeWithRetry(req) { respBody ->
                json.decodeFromString(FormatResponse.serializer(), respBody)
            }
        }

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider()?.trimEnd('/') ?: return@withContext false
        val url = "$baseUrl/health"
        val req = Request.Builder().url(url).get().build()
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

    class AuthException : Exception()
}


