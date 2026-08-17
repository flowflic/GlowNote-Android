package com.glownote.mobile.data

import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.net.URLEncoder
import java.util.UUID

val glowJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
}

fun nowIso(): String = Instant.now().toString()

fun createId(): String = UUID.randomUUID().toString()

@Serializable
data class WebDavSettings(
    val enabled: Boolean = false,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "/highlight-extension/sync.json",
)

@Serializable
data class ReaderSettings(
    val fontSizeSp: Int = 18,
    val lineSpacing: Float = 1.8f,
    val pageSpacingDp: Int = 20,
    val background: String = "warm",
)

fun ReaderSettings.sanitized(): ReaderSettings = copy(
    fontSizeSp = fontSizeSp.coerceIn(14, 28),
    lineSpacing = if (lineSpacing.isFinite()) lineSpacing.coerceIn(1.4f, 2.4f) else 1.8f,
    pageSpacingDp = pageSpacingDp.coerceIn(12, 48),
    background = background.takeIf { it in READER_BACKGROUND_IDS } ?: "warm",
)

private val READER_BACKGROUND_IDS = setOf("warm", "almond", "green", "blue", "gray")

enum class SearchEngine(
    val id: String,
    val label: String,
    val searchTitle: String,
) {
    GOOGLE("google", "谷歌", "Google 搜索"),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "DuckDuckGo 搜索"),
    BING("bing", "Bing", "Bing 搜索"),
    BAIDU("baidu", "百度", "百度搜索");

    fun buildSearchUrl(query: String): String {
        // Keep this pure-Java so the URL builder is also testable in the JVM
        // unit-test environment, where android.net.Uri is not mocked.
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name()).replace("+", "%20")
        return when (this) {
            GOOGLE -> "https://www.google.com/search?q=$encodedQuery"
            DUCKDUCKGO -> "https://duckduckgo.com/?q=$encodedQuery"
            BING -> "https://www.bing.com/search?q=$encodedQuery"
            BAIDU -> "https://www.baidu.com/s?wd=$encodedQuery"
        }
    }

    companion object {
        fun fromId(id: String): SearchEngine = values().firstOrNull {
            it.id.equals(id, ignoreCase = true)
        } ?: GOOGLE
    }
}

@Serializable
data class AppSettings(
    val webdav: WebDavSettings = WebDavSettings(),
    val reader: ReaderSettings = ReaderSettings(),
    val searchEngine: String = SearchEngine.GOOGLE.id,
    val lastSyncAt: String = "",
    val lastSyncError: String = "",
)

@Serializable
data class BrowserHistoryEntry(
    val url: String,
    val title: String = "",
    val visitedAt: String = nowIso(),
)

@Serializable
data class SearchHistoryEntry(
    val query: String,
    val url: String,
    val searchedAt: String = nowIso(),
)

@Serializable
data class BrowserBookmark(
    val url: String,
    val title: String = "",
    val savedAt: String = nowIso(),
)

fun defaultSync(webdavDirty: Boolean = true): JsonObject = buildJsonObject {
    put("webdavDirty", webdavDirty)
    put("webdavLastError", "")
    put("webdavBaseSignature", "")
    put("notionDirty", true)
    put("notionAttempts", 0)
    put("notionNextAttemptAt", "")
    put("notionLastError", "")
    put("notionPageId", "")
    put("notionDeferred", false)
}

@Serializable
data class HighlightRecord(
    val id: String,
    val url: String,
    val normalizedUrl: String = normalizeUrl(url),
    val title: String = "",
    val selectedText: String = "",
    val color: String = "yellow",
    val note: String = "",
    val noteTags: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val starred: Boolean = false,
    val conflictOf: String = "",
    val anchor: JsonObject = buildJsonObject { },
    val createdAt: String = nowIso(),
    val updatedAt: String = createdAt,
    val deletedAt: String = "",
    val sync: JsonObject = defaultSync(),
)

@Serializable
data class RemoteSnapshot(
    val version: Int = 2,
    val revision: Long = 0,
    val updatedAt: String = "",
    val writerClientId: String = "",
    val submissionId: String = "",
    val notionLease: JsonObject? = null,
    val highlights: List<HighlightRecord> = emptyList(),
)

@Serializable
data class ContentSignature(
    val url: String,
    val normalizedUrl: String,
    val title: String,
    val selectedText: String,
    val color: String,
    val note: String,
    val noteTags: List<String>,
    val tags: List<String>,
    val starred: Boolean,
    val anchor: JsonObject,
    val deletedAt: String,
)

@Serializable
data class PageSelection(
    val text: String,
    val url: String,
    val title: String,
    val selectionTop: Float = 0f,
    val selectionBottom: Float = 0f,
    val textOffset: Int = -1,
)

@Serializable
data class PageHighlightClick(
    val id: String,
    val selectionTop: Float = 0f,
    val selectionBottom: Float = 0f,
)

data class ArticleGroup(
    val key: String,
    val url: String,
    val title: String,
    val records: List<HighlightRecord>,
    val tags: List<String>,
    val starred: Boolean,
    val latest: String,
)

fun normalizeUrl(value: String): String {
    val raw = value.trim()
    if (raw.isBlank()) return ""
    return try {
        val parsed = Uri.parse(raw)
        if (parsed.scheme.isNullOrBlank() || parsed.host.isNullOrBlank()) return raw

        val builder = parsed.buildUpon().fragment(null)
        if (parsed.host.equals("mp.weixin.qq.com", ignoreCase = true)) {
            val path = parsed.path.orEmpty()
            if (Regex("^/s/[^/]+/?$").matches(path)) {
                builder.clearQuery()
            } else if (path == "/s") {
                val keys = listOf("__biz", "mid", "idx")
                if (keys.all { parsed.getQueryParameter(it) != null }) {
                    builder.clearQuery()
                    keys.forEach { key -> builder.appendQueryParameter(key, parsed.getQueryParameter(key)) }
                }
            }
        }
        builder.build().toString()
    } catch (_: Exception) {
        raw
    }
}

/**
 * Converts app-only browser deep links into URLs that the embedded WebView can
 * load. Zhihu sometimes replaces an article URL with a zhihu:// link after
 * the page is opened from search results.
 */
fun browserDeepLinkToWebUrl(value: String): String? {
    val parsed = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return null
    if (!parsed.scheme.equals("zhihu", ignoreCase = true)) return null
    if (!parsed.host.equals("articles", ignoreCase = true)) return null
    val articleId = parsed.pathSegments.firstOrNull()
        ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
        ?: return null
    return "https://zhuanlan.zhihu.com/p/$articleId"
}

internal fun navigationUrlWithoutFragment(value: String): String = value.substringBefore('#')

fun normalizeWebUrl(value: String): String? {
    val raw = value.trim()
    if (raw.isBlank()) return null
    val candidate = browserDeepLinkToWebUrl(raw)
        ?: raw.let { if (it.contains("://")) it else "https://$it" }
    val parsed = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
    if (parsed.scheme !in listOf("http", "https") || parsed.host.isNullOrBlank()) return null
    // This value is used for actual navigation, not only as a storage key.
    // Keep the complete query string: WeChat article links use parameters such
    // as `sn` and `chksm` as access/signature context, and dropping them makes
    // the otherwise valid article URL return "参数错误". `normalizeUrl()` is
    // still used separately when records need a stable comparison key.
    return navigationUrlWithoutFragment(candidate)
}

fun normalizeTags(values: List<String>): List<String> = values
    .flatMap { it.split(",") }
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .distinct()

private val tagPattern = Regex("(^|[^\\p{L}\\p{N}_])#([\\p{L}\\p{N}_-]{1,40})")

fun extractTags(note: String): List<String> = tagPattern.findAll(note)
    .map { it.groupValues[2] }
    .distinctBy { it.lowercase() }
    .toList()

fun syncString(sync: JsonObject, key: String): String =
    sync[key]?.jsonPrimitive?.contentOrNull.orEmpty()

fun syncBoolean(sync: JsonObject, key: String, default: Boolean = false): Boolean =
    sync[key]?.jsonPrimitive?.booleanOrNull ?: default

fun withSync(sync: JsonObject, vararg updates: Pair<String, JsonElement>): JsonObject = buildJsonObject {
    sync.forEach { (key, value) -> put(key, value) }
    updates.forEach { (key, value) -> put(key, value) }
}

fun contentSignature(record: HighlightRecord): String = glowJson.encodeToString(
    ContentSignature.serializer(),
    ContentSignature(
        url = record.url,
        normalizedUrl = record.normalizedUrl,
        title = record.title,
        selectedText = record.selectedText,
        color = record.color,
        note = record.note,
        noteTags = normalizeTags(record.noteTags).sorted(),
        tags = normalizeTags(record.tags).sorted(),
        starred = record.starred,
        anchor = record.anchor,
        deletedAt = record.deletedAt,
    ),
)

fun withWebDavDirty(record: HighlightRecord, dirty: Boolean): HighlightRecord = record.copy(
    sync = withSync(
        record.sync,
        "webdavDirty" to JsonPrimitive(dirty),
        "webdavLastError" to JsonPrimitive(""),
    ),
)

fun withWebDavSynced(record: HighlightRecord): HighlightRecord = record.copy(
    sync = withSync(
        record.sync,
        "webdavDirty" to JsonPrimitive(false),
        "webdavLastError" to JsonPrimitive(""),
        "webdavBaseSignature" to JsonPrimitive(contentSignature(record)),
    ),
)

fun withWebDavError(record: HighlightRecord, message: String): HighlightRecord = record.copy(
    sync = withSync(
        record.sync,
        "webdavDirty" to JsonPrimitive(true),
        "webdavLastError" to JsonPrimitive(message),
    ),
)
