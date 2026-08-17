package com.glownote.mobile.data

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

data class RemoteRead(
    val snapshot: RemoteSnapshot,
    val exists: Boolean,
    val migratedFromLegacy: Boolean = false,
)

class WebDavClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun readSnapshot(settings: AppSettings): RemoteRead {
        validateScheme(settings.webdav.url)
        val current = readAt(settings, fileUrl(settings, versionedPath(settings.webdav.path)))
        if (current != null) return RemoteRead(current, exists = true)

        val legacy = readAt(settings, fileUrl(settings, settings.webdav.path))
        return if (legacy != null) {
            RemoteRead(legacy, exists = false, migratedFromLegacy = true)
        } else {
            RemoteRead(RemoteSnapshot(), exists = false)
        }
    }

    suspend fun writeSnapshot(
        settings: AppSettings,
        remote: RemoteSnapshot,
        records: List<HighlightRecord>,
        clientId: String,
        submissionId: String,
    ) {
        validateScheme(settings.webdav.url)
        ensureCollections(settings)
        val body = glowJson.encodeToString(
            RemoteSnapshot.serializer(),
            RemoteSnapshot(
                version = 2,
                revision = remote.revision + 1,
                updatedAt = nowIso(),
                writerClientId = clientId,
                submissionId = submissionId,
                notionLease = remote.notionLease,
                highlights = records,
            ),
        )
        val requestBuilder = Request.Builder()
            .url(fileUrl(settings, versionedPath(settings.webdav.path)))
            .put(body.toRequestBody(JSON_MEDIA_TYPE))
        authenticated(requestBuilder, settings)
        execute(requestBuilder.build()).use { response ->
            if (!response.isSuccessful) error("WebDAV PUT failed: ${response.code}")
        }
    }

    suspend fun testConnection(settings: AppSettings) {
        require(settings.webdav.url.isNotBlank()) { "WebDAV URL is required" }
        validateScheme(settings.webdav.url)
        ensureCollections(settings)

        val configured = settings.webdav.path.trim().trim('/').ifBlank { "highlight-extension/sync.json" }
        val parent = configured.substringBeforeLast('/', "")
        val probeName = "hns-webdav-test-${createId()}.json"
        val probePath = listOf(parent, probeName).filter { it.isNotBlank() }.joinToString("/")
        val probeSettings = settings.copy(webdav = settings.webdav.copy(path = probePath))
        val probeUrl = fileUrl(probeSettings, probePath)
        val nonce = createId()
        val requestBuilder = Request.Builder()
            .url(probeUrl)
            .put("{\"nonce\":\"$nonce\"}".toRequestBody(JSON_MEDIA_TYPE))
        authenticated(requestBuilder, settings)
        execute(requestBuilder.build()).use { response ->
            if (!response.isSuccessful) error("WebDAV test PUT failed: ${response.code}")
        }

        try {
            val readRequestBuilder = Request.Builder()
                .url(probeUrl.newBuilder().addQueryParameter("hns_read", createId()).build())
                .get()
            authenticated(readRequestBuilder, settings)
            execute(readRequestBuilder.build()).use { response ->
                if (!response.isSuccessful) error("WebDAV test GET failed: ${response.code}")
                val body = response.body?.string().orEmpty()
                if (!body.contains(nonce)) error("WebDAV test read returned unexpected content")
            }
        } finally {
            val deleteBuilder = Request.Builder().url(probeUrl).delete()
            authenticated(deleteBuilder, settings)
            execute(deleteBuilder.build()).use { response ->
                if (!response.isSuccessful && response.code != 404) {
                    error("WebDAV test cleanup failed: ${response.code}")
                }
            }
        }
    }

    private fun readAt(settings: AppSettings, url: HttpUrl): RemoteSnapshot? {
        val requestBuilder = Request.Builder()
            .url(url.newBuilder().addQueryParameter("hns_read", createId()).build())
            .get()
        authenticated(requestBuilder, settings)
        execute(requestBuilder.build()).use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) error("WebDAV GET failed: ${response.code}")
            val body = response.body?.string().orEmpty()
            return glowJson.decodeFromString(RemoteSnapshot.serializer(), body)
        }
    }

    private fun fileUrl(settings: AppSettings, path: String): HttpUrl {
        val base = settings.webdav.url.trimEnd('/').toHttpUrlOrNull()
            ?: error("WebDAV URL must be a valid URL")
        return base.newBuilder()
            .addPathSegments(path.trim('/'))
            .build()
    }

    private fun ensureCollections(settings: AppSettings) {
        val configuredPath = versionedPath(settings.webdav.path).trim('/')
        val parent = configuredPath.substringBeforeLast('/', "")
        if (parent.isBlank()) return

        val base = settings.webdav.url.trimEnd('/').toHttpUrlOrNull()
            ?: error("WebDAV URL must be a valid URL")
        var current = base
        for (part in parent.split('/').filter { it.isNotBlank() }) {
            current = current.newBuilder().addPathSegment(part).build()
            val requestBuilder = Request.Builder().url(current).method("MKCOL", null)
            authenticated(requestBuilder, settings)
            execute(requestBuilder.build()).use { response ->
                if (response.code != 201 && response.code != 405 && response.code != 301) {
                    error("WebDAV MKCOL failed: ${response.code}")
                }
            }
        }
    }

    private fun authenticated(builder: Request.Builder, settings: AppSettings) {
        if (settings.webdav.username.isNotBlank()) {
            builder.header(
                "Authorization",
                Credentials.basic(settings.webdav.username, settings.webdav.password),
            )
        }
        builder.header("Cache-Control", "no-cache")
    }

    private fun execute(request: Request) = httpClient.newCall(request).execute()

    private fun validateScheme(value: String) {
        val parsed = value.toHttpUrlOrNull() ?: error("WebDAV URL must be a valid URL")
        require(parsed.scheme == "https") {
            "WebDAV sync requires HTTPS. Use an HTTPS WebDAV URL."
        }
    }

    private fun versionedPath(path: String): String {
        val clean = path.trim().ifBlank { "/highlight-extension/sync.json" }
        return when {
            clean.endsWith("-v2.json", ignoreCase = true) -> clean
            clean.endsWith(".json", ignoreCase = true) -> clean.replace(Regex("\\.json$", RegexOption.IGNORE_CASE), "-v2.json")
            else -> "$clean.v2"
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
