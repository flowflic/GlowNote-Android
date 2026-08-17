package com.glownote.mobile.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.LinkedHashMap

data class SyncResult(
    val uploaded: Int,
    val downloaded: Int,
    val attempts: Int,
)

class GlowNoteRepository(
    private val store: LocalStore,
    private val webDav: WebDavClient,
) {
    private val syncMutex = Mutex()

    suspend fun settings(): AppSettings = store.readSettings()

    suspend fun saveSettings(settings: AppSettings) {
        store.writeSettings(settings.copy(webdav = settings.webdav.copy(path = cleanPath(settings.webdav.path))))
    }

    suspend fun listVisibleRecords(): List<HighlightRecord> = store.readRecords()
        .filter { it.deletedAt.isBlank() }
        .sortedByDescending { it.updatedAt }

    suspend fun recordsForUrl(url: String): List<HighlightRecord> = store.readRecords()
        .filter { it.deletedAt.isBlank() && it.normalizedUrl == normalizeUrl(url) }
        .sortedBy { it.createdAt }

    suspend fun browserHistory(): List<BrowserHistoryEntry> = store.readBrowserHistory()

    suspend fun saveBrowserHistory(entries: List<BrowserHistoryEntry>) {
        store.writeBrowserHistory(entries)
    }

    suspend fun searchHistory(): List<SearchHistoryEntry> = store.readSearchHistory()

    suspend fun saveSearchHistory(entries: List<SearchHistoryEntry>) {
        store.writeSearchHistory(entries)
    }

    suspend fun browserBookmarks(): List<BrowserBookmark> = store.readBrowserBookmarks()

    suspend fun saveBrowserBookmarks(entries: List<BrowserBookmark>) {
        store.writeBrowserBookmarks(entries)
    }

    suspend fun createHighlight(
        url: String,
        title: String,
        selectedText: String,
        color: String,
        note: String,
        tagValues: List<String> = emptyList(),
        textOffset: Int = -1,
    ): HighlightRecord {
        val timestamp = nowIso()
        val noteTags = extractTags(note)
        val record = HighlightRecord(
            id = createId(),
            url = url,
            normalizedUrl = normalizeUrl(url),
            title = title,
            selectedText = selectedText,
            color = color,
            note = note.trim(),
            noteTags = noteTags,
            tags = (normalizeTags(tagValues) + noteTags).distinct(),
            anchor = buildJsonObject {
                if (textOffset >= 0) put("textOffset", textOffset)
            },
            createdAt = timestamp,
            updatedAt = timestamp,
            sync = defaultSync(),
        )
        val records = store.readRecords().toMutableList()
        records += record
        store.writeRecords(records)
        return record
    }

    suspend fun updateHighlight(id: String, color: String, note: String, tags: List<String>? = null): HighlightRecord? {
        val records = store.readRecords().toMutableList()
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return null
        val current = records[index]
        val newNote = note.trim()
        val noteTags = extractTags(newNote)
        val oldNoteTags = current.noteTags.map { it.lowercase() }.toSet()
        val manualTags = tags?.let(::normalizeTags)
            ?: current.tags.filterNot { it.lowercase() in oldNoteTags }
        val updated = current.copy(
            color = color,
            note = newNote,
            noteTags = noteTags,
            tags = (manualTags + noteTags).distinct(),
            updatedAt = nowIso(),
            sync = withWebDavDirty(current, dirty = true).sync,
        )
        records[index] = updated
        store.writeRecords(records)
        return updated
    }

    suspend fun updateArticleTitle(articleKey: String, title: String): Int {
        val nextTitle = title.trim()
        if (nextTitle.isBlank()) return 0
        val normalizedKey = normalizeUrl(articleKey)
        val records = store.readRecords()
        var updatedCount = 0
        val updatedAt = nowIso()
        val updatedRecords = records.map { record ->
            val recordKey = normalizeUrl(record.url.ifBlank { record.normalizedUrl })
            if (recordKey != normalizedKey || record.title == nextTitle) {
                record
            } else {
                updatedCount += 1
                record.copy(
                    title = nextTitle,
                    updatedAt = updatedAt,
                    sync = withWebDavDirty(record, dirty = true).sync,
                )
            }
        }
        if (updatedCount > 0) store.writeRecords(updatedRecords)
        return updatedCount
    }

    suspend fun toggleStarred(id: String): HighlightRecord? {
        val records = store.readRecords().toMutableList()
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return null
        val current = records[index]
        val updated = current.copy(
            starred = !current.starred,
            updatedAt = nowIso(),
            sync = withWebDavDirty(current, dirty = true).sync,
        )
        records[index] = updated
        store.writeRecords(records)
        return updated
    }

    suspend fun deleteHighlight(id: String): HighlightRecord? {
        val records = store.readRecords().toMutableList()
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return null
        val current = records[index]
        val updated = current.copy(
            deletedAt = nowIso(),
            updatedAt = nowIso(),
            sync = withWebDavDirty(current, dirty = true).sync,
        )
        records[index] = updated
        store.writeRecords(records)
        return updated
    }

    suspend fun testWebDav(settings: AppSettings) {
        webDav.testConnection(settings)
    }

    suspend fun sync(settings: AppSettings): SyncResult = syncMutex.withLock {
        require(settings.webdav.enabled) { "WebDAV sync is disabled" }
        var lastError: Throwable? = null
        try {
            val clientId = store.readClientId()
            for (attempt in 1..MAX_SYNC_ATTEMPTS) {
                val remote = webDav.readSnapshot(settings)
                val localBeforeMerge = store.readRecords()
                val merged = mergeRecords(localBeforeMerge, remote.snapshot.highlights)
                store.writeRecords(merged)

                val uploadRecords = merged.map(::withWebDavSynced)
                val submissionId = createId()
                webDav.writeSnapshot(settings, remote.snapshot, uploadRecords, clientId, submissionId)
                val confirmed = webDav.readSnapshot(settings)
                if (confirmed.snapshot.submissionId == submissionId) {
                    markUploadedRecords(uploadRecords)
                    val currentSettings = store.readSettings()
                    store.writeSettings(
                        currentSettings.copy(
                            lastSyncAt = nowIso(),
                            lastSyncError = "",
                        ),
                    )
                    return@withLock SyncResult(
                        uploaded = uploadRecords.size,
                        downloaded = remote.snapshot.highlights.size,
                        attempts = attempt,
                    )
                }

                // Another client won the read-after-write race. The next
                // attempt imports that snapshot before submitting again.
                lastError = IllegalStateException("WebDAV snapshot changed before confirmation")
            }
            throw lastError ?: IllegalStateException("WebDAV sync failed")
        } catch (error: Throwable) {
            val currentSettings = store.readSettings()
            store.writeSettings(currentSettings.copy(lastSyncError = error.message.orEmpty()))
            throw error
        }
    }

    suspend fun articleGroups(): List<ArticleGroup> = groupArticles(listVisibleRecords())

    private suspend fun markUploadedRecords(uploadRecords: List<HighlightRecord>) {
        val uploadedById = uploadRecords.associateBy { it.id }
        val current = store.readRecords()
        val marked = current.map { record ->
            val uploaded = uploadedById[record.id]
            if (uploaded != null
                && uploaded.updatedAt == record.updatedAt
                && contentSignature(uploaded) == contentSignature(record)
            ) {
                withWebDavSynced(record)
            } else {
                record
            }
        }
        store.writeRecords(marked)
    }

    private fun mergeRecords(
        localRecords: List<HighlightRecord>,
        remoteRecords: List<HighlightRecord>,
    ): List<HighlightRecord> {
        val merged = LinkedHashMap<String, HighlightRecord>()
        localRecords.forEach { merged[it.id] = it }

        remoteRecords.forEach { remoteRaw ->
            val remote = withWebDavSynced(remoteRaw)
            val local = merged[remote.id]
            if (local == null) {
                merged[remote.id] = remote
                return@forEach
            }

            val sameContent = contentSignature(local) == contentSignature(remote)
            if (sameContent) {
                merged[remote.id] = local.copy(
                    sync = withSync(
                        local.sync,
                        "webdavDirty" to JsonPrimitive(false),
                        "webdavLastError" to JsonPrimitive(""),
                        "webdavBaseSignature" to JsonPrimitive(contentSignature(remote)),
                    ),
                )
                return@forEach
            }

            val localDirty = syncBoolean(local.sync, "webdavDirty", default = true)
            val baseSignature = syncString(local.sync, "webdavBaseSignature")
            val remoteChangedSinceBase = baseSignature.isBlank() || contentSignature(remote) != baseSignature
            if (localDirty && remoteChangedSinceBase) {
                val localWins = compareVersions(local, remote) >= 0
                val winner = if (localWins) local else remote
                val loser = if (localWins) remote else local
                merged[remote.id] = winner
                if (loser.deletedAt.isBlank()) {
                    val conflict = conflictCopy(remote.id, loser)
                    if (!merged.containsKey(conflict.id)) merged[conflict.id] = conflict
                }
            } else {
                merged[remote.id] = if (compareVersions(local, remote) >= 0) local else remote
            }
        }

        return merged.values.sortedWith(
            compareByDescending<HighlightRecord> { it.starred }
                .thenByDescending { it.updatedAt },
        )
    }

    private fun conflictCopy(originalId: String, loser: HighlightRecord): HighlightRecord {
        val hash = stableHash(contentSignature(loser))
        return loser.copy(
            id = "${originalId}__conflict_$hash",
            conflictOf = originalId,
            tags = (loser.tags + "sync-conflict").distinct(),
            createdAt = nowIso(),
            updatedAt = nowIso(),
            deletedAt = "",
            sync = defaultSync(),
        )
    }

    private fun compareVersions(left: HighlightRecord, right: HighlightRecord): Int {
        val time = left.updatedAt.compareTo(right.updatedAt)
        if (time != 0) return time
        return contentSignature(left).compareTo(contentSignature(right))
    }

    private fun stableHash(value: String): String {
        var hash = 2166136261L
        value.forEach { char ->
            hash = (hash xor char.code.toLong()) * 16777619L and 0xffffffffL
        }
        return hash.toString(16).padStart(8, '0')
    }

    private fun cleanPath(value: String): String {
        val path = value.trim().ifBlank { "/highlight-extension/sync.json" }
        require(!path.split('/').any { it == ".." }) { "WebDAV path cannot contain .." }
        return if (path.startsWith('/')) path else "/$path"
    }

    companion object {
        private const val MAX_SYNC_ATTEMPTS = 4
    }
}

fun groupArticles(records: List<HighlightRecord>): List<ArticleGroup> {
    val groups = LinkedHashMap<String, MutableList<HighlightRecord>>()
    records.forEach { record ->
        groups.getOrPut(normalizeUrl(record.url.ifBlank { record.normalizedUrl })) { mutableListOf() }
            .add(record)
    }
    return groups.map { (key, items) ->
        val sorted = items.sortedBy { it.createdAt }
        val latest = items.maxByOrNull { it.updatedAt }
        ArticleGroup(
            key = key,
            url = latest?.url.orEmpty().ifBlank { sorted.firstOrNull()?.url.orEmpty() },
            title = latest?.title.orEmpty().ifBlank { "Untitled article" },
            records = sorted,
            tags = items.flatMap { it.tags }.distinct().sorted(),
            starred = items.any { it.starred },
            latest = latest?.updatedAt.orEmpty(),
        )
    }.sortedWith(
        compareByDescending<ArticleGroup> { it.starred }.thenByDescending { it.latest },
    )
}
