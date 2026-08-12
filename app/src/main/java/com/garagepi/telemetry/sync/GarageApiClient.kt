package com.garagepi.telemetry.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

@Serializable
data class SessionCreateRequest(
    val source: String = "android",
    val kind: String = "trip",
    val vehicle_name: String = "ioniq5",
    val meta: Map<String, String> = emptyMap(),
)

@Serializable
data class SessionCreateResponse(val id: String, val vehicle_id: String? = null)

@Serializable
data class ReadingPayload(val ts: String, val pid: String, val value: Double)

@Serializable
data class ReadingsBatchRequest(val readings: List<ReadingPayload>)

/**
 * Client for garage-telemetry-api's ingest contract (docs/ANDROID_CONTRACT.md
 * in that repo). Same endpoints garagepi's sync.py uses, so a "SPEED"
 * reading from the Pi and a "010D" reading from this app land in the same
 * `readings` series server-side.
 */
class GarageApiClient(private val baseUrl: String, private val apiKey: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    fun createSession(request: SessionCreateRequest): SessionCreateResponse {
        val body = json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE)
        val resp = execute("POST", "/v1/sessions", body)
        return json.decodeFromString(resp)
    }

    fun uploadReadings(sessionId: String, readings: List<ReadingPayload>) {
        val body = json.encodeToString(ReadingsBatchRequest(readings)).toRequestBody(JSON_MEDIA_TYPE)
        execute("POST", "/v1/sessions/$sessionId/readings", body)
    }

    fun closeSession(sessionId: String) {
        val body = "{}".toRequestBody(JSON_MEDIA_TYPE)
        execute("POST", "/v1/sessions/$sessionId/close", body)
    }

    /**
     * Verifies the endpoint is reachable and the key is accepted, without writing anything.
     * `/health` needs no auth, so a second authenticated call is made to prove the key —
     * otherwise a bad key would still report success.
     */
    fun checkConnection(): String {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/health")
            .get()
            .header("Authorization", "Bearer $apiKey")
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $text")
            return text.ifBlank { "ok" }
        }
    }

    private fun execute(method: String, path: String, body: okhttp3.RequestBody): String {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .method(method, body)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} $path: $text")
            }
            return text
        }
    }
}
