package com.glownote.mobile.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

class LocalStore(context: Context) {
    private val preferences = context.getSharedPreferences("glownote-local", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val credentialCipher = CredentialCipher()

    suspend fun readRecords(): List<HighlightRecord> = mutex.withLock {
        val raw = preferences.getString(KEY_RECORDS, "[]") ?: "[]"
        runCatching {
            glowJson.decodeFromString(ListSerializer(HighlightRecord.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun writeRecords(records: List<HighlightRecord>) = mutex.withLock {
        val raw = glowJson.encodeToString(ListSerializer(HighlightRecord.serializer()), records)
        preferences.edit().putString(KEY_RECORDS, raw).apply()
    }

    suspend fun readBrowserHistory(): List<BrowserHistoryEntry> = mutex.withLock {
        val raw = preferences.getString(KEY_BROWSER_HISTORY, "[]") ?: "[]"
        runCatching {
            glowJson.decodeFromString(ListSerializer(BrowserHistoryEntry.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun writeBrowserHistory(entries: List<BrowserHistoryEntry>) = mutex.withLock {
        val raw = glowJson.encodeToString(ListSerializer(BrowserHistoryEntry.serializer()), entries)
        preferences.edit().putString(KEY_BROWSER_HISTORY, raw).apply()
    }

    suspend fun readSearchHistory(): List<SearchHistoryEntry> = mutex.withLock {
        val raw = preferences.getString(KEY_SEARCH_HISTORY, "[]") ?: "[]"
        runCatching {
            glowJson.decodeFromString(ListSerializer(SearchHistoryEntry.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun writeSearchHistory(entries: List<SearchHistoryEntry>) = mutex.withLock {
        val raw = glowJson.encodeToString(ListSerializer(SearchHistoryEntry.serializer()), entries)
        preferences.edit().putString(KEY_SEARCH_HISTORY, raw).apply()
    }

    suspend fun readBrowserBookmarks(): List<BrowserBookmark> = mutex.withLock {
        val raw = preferences.getString(KEY_BROWSER_BOOKMARKS, "[]") ?: "[]"
        runCatching {
            glowJson.decodeFromString(ListSerializer(BrowserBookmark.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun writeBrowserBookmarks(entries: List<BrowserBookmark>) = mutex.withLock {
        val raw = glowJson.encodeToString(ListSerializer(BrowserBookmark.serializer()), entries)
        preferences.edit().putString(KEY_BROWSER_BOOKMARKS, raw).apply()
    }

    suspend fun readSettings(): AppSettings = mutex.withLock { readSettingsUnsafe() }

    suspend fun writeSettings(settings: AppSettings) = mutex.withLock {
        writeSettingsUnsafe(settings)
    }

    suspend fun readWebDavCache(): WebDavCache? = mutex.withLock {
        val raw = preferences.getString(KEY_WEBDAV_CACHE, "").orEmpty()
        if (raw.isBlank()) return@withLock null
        runCatching {
            glowJson.decodeFromString(WebDavCache.serializer(), raw)
        }.getOrNull()
    }

    suspend fun writeWebDavCache(cache: WebDavCache) = mutex.withLock {
        val raw = glowJson.encodeToString(WebDavCache.serializer(), cache)
        preferences.edit().putString(KEY_WEBDAV_CACHE, raw).apply()
    }

    suspend fun clearWebDavCache() = mutex.withLock {
        preferences.edit().remove(KEY_WEBDAV_CACHE).apply()
    }

    suspend fun readClientId(): String = mutex.withLock {
        val current = preferences.getString(KEY_CLIENT_ID, "").orEmpty()
        if (current.isNotBlank()) return@withLock current
        val created = UUID.randomUUID().toString()
        preferences.edit().putString(KEY_CLIENT_ID, created).apply()
        created
    }

    private fun readSettingsUnsafe(): AppSettings {
        val raw = preferences.getString(KEY_SETTINGS, "").orEmpty()
        if (raw.isBlank()) return AppSettings()
        return runCatching {
            val decoded = glowJson.decodeFromString(AppSettings.serializer(), raw)
            decoded.copy(
                reader = decoded.reader.sanitized(),
                webdav = decoded.webdav.copy(
                    password = credentialCipher.decrypt(decoded.webdav.password),
                ),
            )
        }.getOrDefault(AppSettings())
    }

    private fun writeSettingsUnsafe(settings: AppSettings) {
        val protected = settings.copy(
            webdav = settings.webdav.copy(password = credentialCipher.encrypt(settings.webdav.password)),
        )
        val raw = glowJson.encodeToString(AppSettings.serializer(), protected)
        preferences.edit().putString(KEY_SETTINGS, raw).apply()
    }

    companion object {
        private const val KEY_RECORDS = "records"
        private const val KEY_BROWSER_HISTORY = "browserHistory"
        private const val KEY_SEARCH_HISTORY = "searchHistory"
        private const val KEY_BROWSER_BOOKMARKS = "browserBookmarks"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_CLIENT_ID = "clientId"
        private const val KEY_WEBDAV_CACHE = "webdavCache"
    }
}
