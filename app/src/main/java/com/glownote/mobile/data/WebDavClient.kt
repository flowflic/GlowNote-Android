package com.glownote.mobile.data

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.delay
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class RemoteRead(
    val snapshot: RemoteSnapshot,
    val exists: Boolean,
    val migratedFromLegacy: Boolean = false,
    val validator: WebDavValidator = WebDavValidator(),
    val notModified: Boolean = false,
)

data class WebDavValidator(
    val etag: String = "",
    val lastModified: String = "",
)

private data class SnapshotRead(
    val snapshot: RemoteSnapshot? = null,
    val exists: Boolean = false,
    val validator: WebDavValidator = WebDavValidator(),
    val notModified: Boolean = false,
)

class WebDavClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun readSnapshot(settings: AppSettings, cached: WebDavCache? = null): RemoteRead {
        validateScheme(settings.webdav.url)
        val cachedForEndpoint = cached?.takeIf {
            it.endpointKey == cacheKey(settings) && it.hasValidator()
        }
        val current = readAt(
            settings,
            fileUrl(settings, versionedPath(settings.webdav.path)),
            cachedForEndpoint?.let { WebDavValidator(it.etag, it.lastModified) },
        )
        if (current.notModified && cachedForEndpoint != null) {
            return RemoteRead(
                snapshot = cachedForEndpoint.toRemoteSnapshot(),
                exists = true,
                validator = current.validator,
                notModified = true,
            )
        }
        if (current.snapshot != null) {
            return RemoteRead(
                snapshot = current.snapshot,
                exists = true,
                validator = current.validator,
            )
        }

        val legacy = readAt(settings, fileUrl(settings, settings.webdav.path))
        return if (legacy.snapshot != null) {
            RemoteRead(
                snapshot = legacy.snapshot,
                exists = false,
                migratedFromLegacy = true,
                validator = legacy.validator,
            )
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
        execute(requestBuilder.build(), "PUT").use { response ->
            if (!response.isSuccessful) error("WebDAV PUT failed: ${response.code}")
        }
    }

    suspend fun ensureCollections(settings: AppSettings) {
        validateScheme(settings.webdav.url)
        ensureCollectionsInternal(settings)
    }

    fun cacheKey(settings: AppSettings): String = listOf(
        settings.webdav.url.trim().trimEnd('/'),
        settings.webdav.path.trim().trim('/'),
        settings.webdav.username.trim(),
    ).joinToString("\n")

    suspend fun testConnection(settings: AppSettings) {
        require(settings.webdav.url.isNotBlank()) { "WebDAV URL is required" }
        validateScheme(settings.webdav.url)
        ensureCollectionsInternal(settings)

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
        execute(requestBuilder.build(), "test PUT").use { response ->
            if (!response.isSuccessful) error("WebDAV test PUT failed: ${response.code}")
        }

        try {
            val readRequestBuilder = Request.Builder()
                .url(probeUrl.newBuilder().addQueryParameter("hns_read", createId()).build())
                .get()
            authenticated(readRequestBuilder, settings)
            executeWithRetry(readRequestBuilder.build(), "test GET") { response ->
                if (!response.isSuccessful) error("WebDAV test GET failed: ${response.code}")
                val body = readBody(response, "test GET")
                if (!body.contains(nonce)) error("WebDAV test read returned unexpected content")
            }
        } finally {
            val deleteBuilder = Request.Builder().url(probeUrl).delete()
            authenticated(deleteBuilder, settings)
            execute(deleteBuilder.build(), "test DELETE").use { response ->
                if (!response.isSuccessful && response.code != 404) {
                    error("WebDAV test cleanup failed: ${response.code}")
                }
            }
        }
    }

    private suspend fun readAt(
        settings: AppSettings,
        url: HttpUrl,
        cachedValidator: WebDavValidator? = null,
    ): SnapshotRead {
        val requestBuilder = Request.Builder()
            .url(url.newBuilder().addQueryParameter("hns_read", createId()).build())
            .get()
        if (!cachedValidator?.etag.isNullOrBlank()) {
            requestBuilder.header("If-None-Match", cachedValidator?.etag.orEmpty())
        }
        if (!cachedValidator?.lastModified.isNullOrBlank()) {
            requestBuilder.header("If-Modified-Since", cachedValidator?.lastModified.orEmpty())
        }
        authenticated(requestBuilder, settings)
        return executeWithRetry(requestBuilder.build(), "GET") { response ->
            if (response.code == 304) {
                return@executeWithRetry SnapshotRead(
                    exists = true,
                    validator = responseValidator(response, cachedValidator),
                    notModified = true,
                )
            }
            if (response.code == 404) return@executeWithRetry SnapshotRead()
            if (!response.isSuccessful) error("WebDAV GET failed: ${response.code}")
            val body = readBody(response, "GET")
            SnapshotRead(
                snapshot = glowJson.decodeFromString(RemoteSnapshot.serializer(), body),
                exists = true,
                validator = responseValidator(response),
            )
        }
    }

    private fun responseValidator(
        response: okhttp3.Response,
        fallback: WebDavValidator? = null,
    ): WebDavValidator = WebDavValidator(
        etag = response.header("ETag").orEmpty().ifBlank { fallback?.etag.orEmpty() },
        lastModified = response.header("Last-Modified").orEmpty().ifBlank { fallback?.lastModified.orEmpty() },
    )

    private fun fileUrl(settings: AppSettings, path: String): HttpUrl {
        val base = settings.webdav.url.trimEnd('/').toHttpUrlOrNull()
            ?: error("WebDAV URL must be a valid URL")
        return base.newBuilder()
            .addPathSegments(path.trim('/'))
            .build()
    }

    private suspend fun ensureCollectionsInternal(settings: AppSettings) {
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
            executeWithRetry(requestBuilder.build(), "MKCOL") { response ->
                if (!isMkcolSuccessStatus(response.code)) {
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

    private fun execute(request: Request, operation: String): okhttp3.Response = try {
        httpClient.newCall(request).execute()
    } catch (error: SocketTimeoutException) {
        val wrapped = SocketTimeoutException("WebDAV $operation timeout")
        wrapped.initCause(error)
        throw wrapped
    } catch (error: IOException) {
        throw IOException("WebDAV $operation network error: ${error.message}", error)
    }

    private fun readBody(response: okhttp3.Response, operation: String): String = try {
        response.body?.string().orEmpty()
    } catch (error: SocketTimeoutException) {
        val wrapped = SocketTimeoutException("WebDAV $operation timeout")
        wrapped.initCause(error)
        throw wrapped
    } catch (error: IOException) {
        throw IOException("WebDAV $operation network error: ${error.message}", error)
    }

    private suspend fun <T> executeWithRetry(
        request: Request,
        operation: String,
        block: (okhttp3.Response) -> T,
    ): T {
        var lastError: IOException? = null
        repeat(MAX_SAFE_REQUEST_ATTEMPTS) { index ->
            try {
                return execute(request, operation).use(block)
            } catch (error: IOException) {
                lastError = error
                if (index == MAX_SAFE_REQUEST_ATTEMPTS - 1 || !isTransient(error)) throw error
                delay(SAFE_REQUEST_BACKOFF_MS[index])
            }
        }
        throw lastError ?: IOException("WebDAV $operation failed")
    }

    private fun isTransient(error: IOException): Boolean =
        error is SocketTimeoutException ||
            error is InterruptedIOException ||
            error is ConnectException ||
            error is NoRouteToHostException

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
        private const val MAX_SAFE_REQUEST_ATTEMPTS = 2
        private val SAFE_REQUEST_BACKOFF_MS = longArrayOf(250L)
    }
}

private fun WebDavCache.toRemoteSnapshot(): RemoteSnapshot = RemoteSnapshot(
    version = 2,
    revision = revision,
    updatedAt = updatedAt,
    writerClientId = writerClientId,
    submissionId = submissionId,
    notionLease = notionLease,
)

internal fun isMkcolSuccessStatus(code: Int): Boolean =
    code == 200 || code == 201 || code == 204 || code == 301 || code == 405
