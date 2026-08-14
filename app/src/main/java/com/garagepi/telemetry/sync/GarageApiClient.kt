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
 * in that repo). Same endpoints garagepi's sync.py uses. Local decoder names are
 * mapped through [PidMap] before upload so they land in the same `readings.pid`
 * series as the Pi.
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
     * Verifies the host is up *and* the key is accepted.
     *
     * `/health` is unauthenticated, so a second call is required. Prefer `GET /v1/auth`.
     * Older APIs without that route are probed with `POST .../close` on a nonexistent
     * session: 404 means the key was accepted without writing a row.
     */
    fun checkConnection(): String {
        val (healthCode, healthBody) = call("GET", "/health")
        if (healthCode !in 200..299) {
            throw IOException("HTTP $healthCode /health: $healthBody")
        }

        val (authCode, authBody) = call("GET", "/v1/auth")
        when (authCode) {
            in 200..299 -> return authBody.ifBlank { "ok" }
            401 -> throw IOException("HTTP 401: invalid API key")
            404 -> return probeKeyWithoutWrite()
            else -> throw IOException("HTTP $authCode /v1/auth: $authBody")
        }
    }

    /** 401 = bad key; 404/200 = key accepted (session missing or already gone). */
    private fun probeKeyWithoutWrite(): String {
        val (code, body) = call(
            "POST",
            "/v1/sessions/$PROBE_SESSION_ID/close",
            "{}".toRequestBody(JSON_MEDIA_TYPE),
        )
        when (code) {
            401 -> throw IOException("HTTP 401: invalid API key")
            404, 200 -> return "ok"
            else -> throw IOException("HTTP $code close-probe: $body")
        }
    }

    private fun execute(method: String, path: String, body: okhttp3.RequestBody): String {
        val (code, text) = call(method, path, body)
        if (code !in 200..299) {
            throw IOException("HTTP $code $path: $text")
        }
        return text
    }

    private fun call(method: String, path: String, body: okhttp3.RequestBody? = null): Pair<Int, String> {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .method(method, body)
            .header("Authorization", "Bearer $apiKey")
            .apply { if (body != null) header("Content-Type", "application/json") }
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            return response.code to text
        }
    }

    companion object {
        /** Well-formed UUID that should not exist; used only as an auth probe. */
        const val PROBE_SESSION_ID = "00000000-0000-4000-8000-000000000000"
    }
}
