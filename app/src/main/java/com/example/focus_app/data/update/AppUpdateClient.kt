package com.example.focus_app.data.update

import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object AppUpdateConfig {
    const val MANIFEST_URL = "https://brucehere.com/updates/android/latest.json"
    val trustedHost: String = requireNotNull(MANIFEST_URL.toHttpUrlOrNull()).host
}

internal data class AppRelease(
    val applicationId: String,
    val versionCode: Long,
    val versionName: String,
    val downloadPageUrl: String,
    val releaseNotes: String,
    val minSdk: Int
)

internal fun parseAppRelease(json: String): AppRelease {
    val root = JsonParser.parseString(json).asJsonObject
    fun text(name: String): String {
        val value = requireNotNull(root.get(name)) { "Missing $name" }.asJsonPrimitive
        require(value.isString) { "Invalid $name" }
        return value.asString.trim()
    }
    fun number(name: String): Long {
        val value = requireNotNull(root.get(name)) { "Missing $name" }.asJsonPrimitive
        require(value.isNumber) { "Invalid $name" }
        return requireNotNull(value.asString.toLongOrNull()) { "Invalid $name" }
    }
    val applicationId = text("applicationId").also { require(it.isNotBlank()) }
    val code = number("versionCode").also { require(it in 1..2_100_000_000L) }
    val name = text("versionName").also { require(it.length in 1..40) }
    val page = requireNotNull(text("downloadPageUrl").toHttpUrlOrNull())
    require(page.isHttps && page.host == AppUpdateConfig.trustedHost && page.port == 443 &&
        page.username.isEmpty() && page.password.isEmpty()) { "Invalid download page" }
    val notes = if (root.has("releaseNotes")) text("releaseNotes") else ""
    require(notes.length <= 4_000)
    val minSdk = if (root.has("minSdk")) number("minSdk").also { require(it in 1..Int.MAX_VALUE) }.toInt() else 26
    return AppRelease(applicationId, code, name, page.toString(), notes, minSdk)
}

/** This client only downloads the small version manifest. APK downloads belong to the browser. */
internal class AppUpdateClient(
    private val endpoint: String = AppUpdateConfig.MANIFEST_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
) {
    suspend fun fetch(): AppRelease = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(endpoint)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache").build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val release = response.use {
                        if (!it.isSuccessful) throw IOException("Update HTTP ${it.code}")
                        val body = it.body ?: throw IOException("Empty update response")
                        val subtype = body.contentType()?.subtype.orEmpty()
                        if (subtype != "json" && !subtype.endsWith("+json")) throw IOException("Expected JSON")
                        val source = body.source()
                        source.request(65_537)
                        if (source.buffer.size > 65_536) throw IOException("Update response too large")
                        parseAppRelease(source.readUtf8())
                    }
                    if (continuation.isActive) continuation.resume(release)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
}
