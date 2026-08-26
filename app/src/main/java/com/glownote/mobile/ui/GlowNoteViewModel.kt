package com.glownote.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glownote.mobile.BuildConfig
import com.glownote.mobile.data.AppSettings
import com.glownote.mobile.data.AppInstallResult
import com.glownote.mobile.data.AppUpdateClient
import com.glownote.mobile.data.AppUpdateState
import com.glownote.mobile.data.ArticleGroup
import com.glownote.mobile.data.BrowserBookmark
import com.glownote.mobile.data.BrowserHistoryEntry
import com.glownote.mobile.data.GlowNoteRepository
import com.glownote.mobile.data.HighlightRecord
import com.glownote.mobile.data.LocalStore
import com.glownote.mobile.data.PageHighlightClick
import com.glownote.mobile.data.PageSelection
import com.glownote.mobile.data.ReaderSettings
import com.glownote.mobile.data.SearchEngine
import com.glownote.mobile.data.SearchHistoryEntry
import com.glownote.mobile.data.WebDavClient
import com.glownote.mobile.data.normalizeWebUrl
import com.glownote.mobile.data.createId
import com.glownote.mobile.data.normalizeUrl
import com.glownote.mobile.data.normalizeTags
import com.glownote.mobile.data.nowIso
import com.glownote.mobile.data.sanitized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class AppScreen {
    LIBRARY,
    BROWSER,
    ARTICLE_DETAIL,
    SETTINGS,
}

data class BrowserTab(
    val id: String,
    val url: String,
    val title: String = "",
    val isNewTab: Boolean = false,
    val isReaderMode: Boolean = false,
)

private const val DEFAULT_BROWSER_TAB_ID = "default-browser-tab"

data class AnnotationDraft(
    val id: String? = null,
    val url: String,
    val title: String,
    val selectedText: String,
    val color: String = "yellow",
    val note: String = "",
    val tagInput: String = "",
    val selectionTop: Float = 0f,
    val selectionBottom: Float = 0f,
    val textOffset: Int = -1,
)

data class HighlightJumpRequest(
    val id: String,
    val token: Long,
)

data class GlowNoteUiState(
    val screen: AppScreen = AppScreen.LIBRARY,
    val records: List<HighlightRecord> = emptyList(),
    val articles: List<ArticleGroup> = emptyList(),
    val browserHistory: List<BrowserHistoryEntry> = emptyList(),
    val searchHistory: List<SearchHistoryEntry> = emptyList(),
    val bookmarks: List<BrowserBookmark> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val currentUrl: String = "",
    val currentTitle: String = "",
    val browserTabs: List<BrowserTab> = listOf(
        BrowserTab(DEFAULT_BROWSER_TAB_ID, "", isNewTab = true),
    ),
    val activeBrowserTabId: String = DEFAULT_BROWSER_TAB_ID,
    val pendingHighlightJump: HighlightJumpRequest? = null,
    val selectedArticleKey: String? = null,
    val draft: AnnotationDraft? = null,
    val isSyncing: Boolean = false,
    val appUpdate: AppUpdateState = AppUpdateState(),
    val status: String = "",
    val statusIsError: Boolean = false,
)

class GlowNoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GlowNoteRepository(
        store = LocalStore(application.applicationContext),
        webDav = WebDavClient(),
    )
    private val appUpdateClient = AppUpdateClient()
    private val _ui = MutableStateFlow(GlowNoteUiState())
    val ui: StateFlow<GlowNoteUiState> = _ui.asStateFlow()
    private var syncJob: Job? = null
    private var readerSettingsStatusJob: Job? = null
    private var lastOpenedWindowUrl: String = ""
    private var lastOpenedWindowAt: Long = 0L

    init {
        viewModelScope.launch { refresh() }
        viewModelScope.launch { loadBrowserCollections() }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshInternal()
        }
    }

    fun navigateTo(screen: AppScreen) {
        _ui.update {
            it.copy(
                screen = screen,
                pendingHighlightJump = if (screen == AppScreen.BROWSER) it.pendingHighlightJump else null,
                selectedArticleKey = if (screen == AppScreen.ARTICLE_DETAIL) it.selectedArticleKey else null,
                draft = if (screen == AppScreen.BROWSER) it.draft else null,
            )
        }
        if (screen == AppScreen.SETTINGS) refresh()
    }

    fun openArticleDetail(article: ArticleGroup) {
        _ui.update {
            it.copy(
                screen = AppScreen.ARTICLE_DETAIL,
                pendingHighlightJump = null,
                selectedArticleKey = article.key,
                draft = null,
            )
        }
    }

    fun selectArticle(article: ArticleGroup) {
        _ui.update {
            it.copy(
                screen = AppScreen.LIBRARY,
                selectedArticleKey = article.key,
                draft = null,
                status = "",
                statusIsError = false,
            )
        }
    }

    fun openArticle(url: String, title: String = "", highlightId: String? = null) {
        val safe = normalizeWebUrl(url) ?: return setStatus("请输入有效的 http(s) 网页地址", true)
        _ui.update {
            val activeId = it.activeBrowserTabId.ifBlank { DEFAULT_BROWSER_TAB_ID }
            val existingTab = it.browserTabs.firstOrNull { tab -> tab.id == activeId }
            val keepReaderMode = existingTab?.url == safe && existingTab.isReaderMode
            val tabs = if (it.browserTabs.any { tab -> tab.id == activeId }) {
                it.browserTabs.map { tab ->
                    if (tab.id == activeId) tab.copy(
                        url = safe,
                        title = title.ifBlank { tab.title },
                        isNewTab = false,
                        isReaderMode = keepReaderMode,
                    ) else tab
                }
            } else {
                listOf(BrowserTab(activeId, safe, title, isNewTab = false))
            }
            it.copy(
                screen = AppScreen.BROWSER,
                currentUrl = safe,
                currentTitle = title,
                browserTabs = tabs,
                activeBrowserTabId = activeId,
                pendingHighlightJump = highlightId?.let { id ->
                    HighlightJumpRequest(id = id, token = it.pendingHighlightJump?.token?.plus(1) ?: 1L)
                },
                selectedArticleKey = null,
                draft = null,
                status = "",
                statusIsError = false,
            )
        }
        rememberBrowserHistory(safe, title)
    }

    /**
     * Opens a browser-created new window in a separate GlowNote tab. WebView
     * does not have a native window manager of its own, so target=_blank and
     * window.open must be handed back to the browser tab state explicitly.
     */
    fun openBrowserTab(url: String, title: String = "") {
        val safe = normalizeWebUrl(url) ?: return setStatus("请输入有效的 http(s) 网页地址", true)
        val now = System.currentTimeMillis()
        // target=_blank can be reported by both the JavaScript anchor
        // fallback and WebChromeClient.onCreateWindow. Treat those callbacks
        // as one user action when they arrive together.
        if (safe == lastOpenedWindowUrl && now - lastOpenedWindowAt < 1500L) return
        lastOpenedWindowUrl = safe
        lastOpenedWindowAt = now
        val tab = BrowserTab(
            id = createId(),
            url = safe,
            title = title,
            isNewTab = false,
        )
        _ui.update {
            it.copy(
                screen = AppScreen.BROWSER,
                browserTabs = it.browserTabs + tab,
                activeBrowserTabId = tab.id,
                currentUrl = safe,
                currentTitle = title,
                pendingHighlightJump = null,
                selectedArticleKey = null,
                draft = null,
                status = "",
                statusIsError = false,
            )
        }
        rememberBrowserHistory(safe, title)
    }

    fun openBrowserHome() {
        _ui.update {
            val activeId = it.activeBrowserTabId.ifBlank { DEFAULT_BROWSER_TAB_ID }
            val tabs = if (it.browserTabs.any { tab -> tab.id == activeId }) {
                it.browserTabs.map { tab ->
                    if (tab.id == activeId) {
                        tab.copy(url = "", title = "", isNewTab = true)
                    } else {
                        tab
                    }
                }
            } else {
                listOf(BrowserTab(activeId, "", isNewTab = true))
            }
            it.copy(
                screen = AppScreen.BROWSER,
                browserTabs = tabs,
                activeBrowserTabId = activeId,
                pendingHighlightJump = null,
                currentUrl = "",
                currentTitle = "",
                selectedArticleKey = null,
                draft = null,
                status = "",
                statusIsError = false,
            )
        }
    }

    fun openAddressOrSearch(value: String) {
        val input = value.trim()
        if (input.isBlank()) return
        if (looksLikeWebAddress(input)) {
            openArticle(input)
        } else {
            val engine = SearchEngine.fromId(_ui.value.settings.searchEngine)
            val searchUrl = engine.buildSearchUrl(input)
            rememberSearch(input, searchUrl)
            openArticle(
                url = searchUrl,
                title = engine.searchTitle,
            )
        }
    }

    fun setSearchEngine(engine: SearchEngine) {
        val nextSettings = _ui.value.settings.copy(searchEngine = engine.id)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.saveSettings(nextSettings) }
            }.onSuccess {
                _ui.update {
                    it.copy(
                        settings = nextSettings,
                        status = "搜索引擎已切换为 ${engine.label}",
                        statusIsError = false,
                    )
                }
            }.onFailure { error ->
                setStatus(error.message ?: "保存搜索引擎设置失败", true)
            }
        }
    }

    fun toggleBookmark() {
        val state = _ui.value
        val url = state.currentUrl.trim()
        if (url.isBlank()) {
            setStatus("请先打开网页再添加收藏", true)
            return
        }
        val existing = state.bookmarks.any { it.url == url }
        val next = if (existing) {
            state.bookmarks.filterNot { it.url == url }
        } else {
            listOf(BrowserBookmark(url = url, title = state.currentTitle)) + state.bookmarks
        }
        _ui.update { it.copy(bookmarks = next) }
        persistBrowserBookmarks(next)
        setStatus(if (existing) "已取消收藏" else "已添加收藏", false)
    }

    fun removeBookmark(url: String) {
        val next = _ui.value.bookmarks.filterNot { it.url == url }
        _ui.update { it.copy(bookmarks = next) }
        persistBrowserBookmarks(next)
    }

    fun clearBrowserHistory() {
        _ui.update { it.copy(browserHistory = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) { repository.saveBrowserHistory(emptyList()) }
    }

    fun addBrowserTab() {
        val tab = BrowserTab(id = createId(), url = "", isNewTab = true)
        _ui.update {
            it.copy(
                screen = AppScreen.BROWSER,
                browserTabs = it.browserTabs + tab,
                activeBrowserTabId = tab.id,
                currentUrl = tab.url,
                currentTitle = "",
                pendingHighlightJump = null,
                draft = null,
                status = "",
                statusIsError = false,
            )
        }
    }

    fun switchBrowserTab(id: String) {
        val tab = _ui.value.browserTabs.firstOrNull { it.id == id } ?: return
        _ui.update {
            it.copy(
                screen = AppScreen.BROWSER,
                activeBrowserTabId = tab.id,
                currentUrl = tab.url,
                currentTitle = tab.title,
                pendingHighlightJump = null,
                draft = null,
                status = "",
                statusIsError = false,
            )
        }
    }

    fun closeBrowserTab(id: String) {
        val state = _ui.value
        if (state.browserTabs.size <= 1) return
        val index = state.browserTabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val remaining = state.browserTabs.filterNot { it.id == id }
        val nextActive = if (state.activeBrowserTabId == id) {
            remaining.getOrNull((index - 1).coerceAtLeast(0)) ?: remaining.first()
        } else {
            remaining.firstOrNull { it.id == state.activeBrowserTabId } ?: remaining.first()
        }
        _ui.update {
            it.copy(
                browserTabs = remaining,
                activeBrowserTabId = nextActive.id,
                currentUrl = nextActive.url,
                currentTitle = nextActive.title,
                pendingHighlightJump = null,
                draft = null,
            )
        }
    }

    fun onPageLoaded(tabId: String, url: String, title: String) {
        val safe = normalizeWebUrl(url) ?: return
        _ui.update { state ->
            val updatedTabs = state.browserTabs.map { tab ->
                if (tab.id == tabId) tab.copy(
                    url = safe,
                    title = title.ifBlank { tab.title },
                    isNewTab = false,
                    isReaderMode = false,
                ) else tab
            }
            if (state.activeBrowserTabId == tabId) {
                state.copy(
                    browserTabs = updatedTabs,
                    currentUrl = safe,
                    currentTitle = title.ifBlank { state.currentTitle },
                )
            } else {
                state.copy(browserTabs = updatedTabs)
            }
        }
        rememberBrowserHistory(safe, title)
    }

    fun setReaderMode(tabId: String, enabled: Boolean) {
        _ui.update { state ->
            val updatedTabs = state.browserTabs.map { tab ->
                if (tab.id == tabId) tab.copy(isReaderMode = enabled) else tab
            }
            if (state.activeBrowserTabId == tabId) {
                state.copy(browserTabs = updatedTabs)
            } else {
                state.copy(browserTabs = updatedTabs)
            }
        }
    }

    fun saveReaderSettings(next: ReaderSettings) {
        val sanitized = next.sanitized()
        val nextSettings = _ui.value.settings.copy(reader = sanitized)
        val savedMessage = "阅读设置已保存"
        readerSettingsStatusJob?.cancel()
        _ui.update {
            it.copy(
                settings = nextSettings,
                status = savedMessage,
                statusIsError = false,
            )
        }
        readerSettingsStatusJob = viewModelScope.launch {
            delay(3_000L)
            _ui.update { state ->
                if (state.status == savedMessage && !state.statusIsError) {
                    state.copy(status = "")
                } else {
                    state
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.saveSettings(nextSettings) }
            }.onFailure { error ->
                readerSettingsStatusJob?.cancel()
                setStatus(error.message ?: "保存阅读设置失败", true)
            }
        }
    }

    fun openHighlight(record: HighlightRecord) {
        openArticle(record.url, record.title, record.id)
    }

    fun onSelection(tabId: String, selection: PageSelection) {
        val safe = normalizeWebUrl(selection.url) ?: return
        val text = selection.text.trim()
        if (text.length < 2) return
        _ui.update {
            if (it.activeBrowserTabId != tabId) return@update it
            it.copy(
                screen = AppScreen.BROWSER,
                currentUrl = safe,
                currentTitle = selection.title.ifBlank { it.currentTitle },
                draft = AnnotationDraft(
                    url = safe,
                    title = selection.title.ifBlank { it.currentTitle },
                    selectedText = text,
                    selectionBottom = selection.selectionBottom,
                    selectionTop = selection.selectionTop,
                    textOffset = selection.textOffset,
                ),
            )
        }
    }

    fun onHighlightClicked(tabId: String, click: PageHighlightClick) {
        val state = _ui.value
        val record = state.records.firstOrNull { it.id == click.id }
        if (record == null) return
        _ui.update {
            if (it.activeBrowserTabId != tabId) return@update it
            it.copy(
                draft = annotationDraft(record).copy(
                    selectionTop = click.selectionTop,
                    selectionBottom = click.selectionBottom,
                ),
            )
        }
    }

    fun editHighlight(record: HighlightRecord) {
        _ui.update { it.copy(draft = annotationDraft(record)) }
    }

    fun updateArticleTitle(article: ArticleGroup, title: String) {
        val nextTitle = title.trim()
        if (nextTitle.isBlank()) return
        viewModelScope.launch {
            val updatedCount = withContext(Dispatchers.IO) {
                repository.updateArticleTitle(article.key, nextTitle)
            }
            refreshInternal()
            _ui.update {
                it.copy(
                    status = if (updatedCount > 0) "文章标题已更新" else it.status,
                    statusIsError = false,
                )
            }
            if (_ui.value.settings.webdav.enabled) syncNow()
        }
    }

    fun dismissDraft() {
        _ui.update { it.copy(draft = null) }
    }

    fun saveDraft(color: String, note: String, tagInput: String = "") {
        val draft = _ui.value.draft ?: return
        val tags = normalizeTags(tagInput.split(",").map { it.removePrefix("#") })
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (draft.id == null) {
                    repository.createHighlight(
                        url = draft.url,
                        title = draft.title,
                        selectedText = draft.selectedText,
                        color = color,
                        note = note,
                        tagValues = tags,
                        textOffset = draft.textOffset,
                    )
                } else {
                    repository.updateHighlight(draft.id, color, note, tags)
                }
            }
            _ui.update { it.copy(draft = null, status = "批注已保存", statusIsError = false) }
            refreshInternal()
            if (_ui.value.settings.webdav.enabled) syncNow()
        }
    }

    fun deleteDraft() {
        _ui.value.draft?.id?.let(::deleteHighlight)
    }

    fun deleteHighlight(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteHighlight(id) }
            _ui.update { it.copy(draft = null, status = "批注已删除", statusIsError = false) }
            refreshInternal()
            if (_ui.value.settings.webdav.enabled) syncNow()
        }
    }

    fun deleteArticle(article: ArticleGroup) {
        if (article.records.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                article.records.forEach { record -> repository.deleteHighlight(record.id) }
            }
            _ui.update {
                it.copy(
                    selectedArticleKey = if (it.selectedArticleKey == article.key) null else it.selectedArticleKey,
                    draft = null,
                    status = "文章及批注已删除",
                    statusIsError = false,
                )
            }
            refreshInternal()
            if (_ui.value.settings.webdav.enabled) syncNow()
        }
    }

    fun toggleArticleStar(article: ArticleGroup) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                article.records.forEach { record ->
                    if (record.starred != !article.starred) repository.toggleStarred(record.id)
                }
            }
            refreshInternal()
            if (_ui.value.settings.webdav.enabled) syncNow()
        }
    }

    fun saveSettings(next: AppSettings) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.saveSettings(next) }
            }.onSuccess {
                _ui.update { it.copy(settings = next, status = "设置已保存", statusIsError = false) }
                if (next.webdav.enabled) syncNow()
            }.onFailure { error -> setStatus(error.message ?: "保存设置失败", true) }
        }
    }

    fun checkForAppUpdate() {
        val current = _ui.value.appUpdate
        if (current.isChecking || current.isDownloading) return
        _ui.update {
            it.copy(
                appUpdate = current.copy(
                    isChecking = true,
                    latest = null,
                    message = "正在检查更新…",
                    statusIsError = false,
                ),
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    appUpdateClient.checkForUpdate(BuildConfig.VERSION_NAME)
                }
            }.onSuccess { latest ->
                _ui.update {
                    it.copy(
                        appUpdate = AppUpdateState(
                            latest = latest,
                            message = latest?.let { info -> "发现新版本 v${info.versionName}" } ?: "当前已是最新版本",
                        ),
                    )
                }
            }.onFailure { error ->
                _ui.update {
                    it.copy(
                        appUpdate = AppUpdateState(
                            message = error.message ?: "检查更新失败",
                            statusIsError = true,
                        ),
                    )
                }
            }
        }
    }

    fun installAppUpdate() {
        val info = _ui.value.appUpdate.latest ?: return setStatus("请先检查更新", true)
        if (_ui.value.appUpdate.isDownloading) return
        _ui.update {
            it.copy(
                appUpdate = it.appUpdate.copy(
                    isDownloading = true,
                    message = "正在下载 v${info.versionName}…",
                    statusIsError = false,
                ),
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    appUpdateClient.downloadApk(info, getApplication<Application>().cacheDir)
                }
            }.onSuccess { apkFile ->
                runCatching {
                    appUpdateClient.launchInstaller(getApplication(), apkFile)
                }.onSuccess { result ->
                    val message = when (result) {
                        AppInstallResult.LAUNCHED_INSTALLER -> "已打开系统安装器"
                        AppInstallResult.PERMISSION_REQUIRED -> "请允许 GlowNote 安装未知应用后，再点击安装"
                    }
                    _ui.update {
                        it.copy(
                            appUpdate = it.appUpdate.copy(
                                isDownloading = false,
                                message = message,
                            ),
                        )
                    }
                }.onFailure { error ->
                    _ui.update {
                        it.copy(
                            appUpdate = it.appUpdate.copy(
                                isDownloading = false,
                                message = error.message ?: "无法打开安装器",
                                statusIsError = true,
                            ),
                        )
                    }
                }
            }.onFailure { error ->
                _ui.update {
                    it.copy(
                        appUpdate = it.appUpdate.copy(
                            isDownloading = false,
                            message = error.message ?: "下载更新失败",
                            statusIsError = true,
                        ),
                    )
                }
            }
        }
    }

    fun testWebDav(settings: AppSettings) {
        viewModelScope.launch {
            _ui.update { it.copy(status = "正在测试 WebDAV…", statusIsError = false) }
            runCatching {
                withContext(Dispatchers.IO) { repository.testWebDav(settings) }
            }.onSuccess {
                setStatus("WebDAV 连接正常", false)
            }.onFailure { error -> setStatus(error.message ?: "WebDAV 测试失败", true) }
        }
    }

    fun syncOnAppForeground() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) { repository.settings() }
            _ui.update { it.copy(settings = settings) }
            if (settings.webdav.enabled) {
                runSync(settings, "正在自动同步…")
            }
        }
    }

    fun syncNow() {
        val settings = _ui.value.settings
        if (!settings.webdav.enabled) {
            setStatus("请先在设置中启用 WebDAV", true)
            return
        }
        startSync(settings, "正在同步…")
    }

    private fun startSync(settings: AppSettings, progressMessage: String) {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch { runSync(settings, progressMessage) }
    }

    private suspend fun runSync(settings: AppSettings, progressMessage: String) {
        _ui.update { it.copy(isSyncing = true, status = progressMessage, statusIsError = false) }
        runCatching {
            withContext(Dispatchers.IO) { repository.sync(settings) }
        }.onSuccess { result ->
            refreshInternal()
            _ui.update {
                it.copy(
                    isSyncing = false,
                    status = if (result.usedCache) {
                        "同步完成 · 使用本地缓存 · ${result.downloaded} 篇记录"
                    } else {
                        "同步完成 · ${result.downloaded} 篇远程记录"
                    },
                    statusIsError = false,
                )
            }
        }.onFailure { error ->
            _ui.update {
                it.copy(isSyncing = false, status = error.message ?: "同步失败", statusIsError = true)
            }
        }
    }

    fun recordsForCurrentPage(): List<HighlightRecord> = _ui.value.records.filter {
        it.normalizedUrl == normalizeUrl(_ui.value.currentUrl)
    }

    private fun rememberBrowserHistory(url: String, title: String) {
        if (url.isBlank()) return
        val state = _ui.value
        val previous = state.browserHistory
        val oldTitle = previous.firstOrNull { it.url == url }?.title.orEmpty()
        val next = listOf(
            BrowserHistoryEntry(url = url, title = title.ifBlank { oldTitle }),
        ) + previous.filterNot { it.url == url }.take(49)
        _ui.update { it.copy(browserHistory = next) }
        viewModelScope.launch(Dispatchers.IO) { repository.saveBrowserHistory(next) }
    }

    private fun rememberSearch(query: String, url: String) {
        val state = _ui.value
        val next = listOf(SearchHistoryEntry(query = query, url = url)) +
            state.searchHistory.filterNot { it.query.equals(query, ignoreCase = true) }.take(9)
        _ui.update { it.copy(searchHistory = next) }
        viewModelScope.launch(Dispatchers.IO) { repository.saveSearchHistory(next) }
    }

    private fun persistBrowserBookmarks(bookmarks: List<BrowserBookmark>) {
        viewModelScope.launch(Dispatchers.IO) { repository.saveBrowserBookmarks(bookmarks) }
    }

    private suspend fun refreshInternal() {
        val records = withContext(Dispatchers.IO) { repository.listVisibleRecords() }
        val settings = withContext(Dispatchers.IO) { repository.settings() }
        _ui.update {
            it.copy(
                records = records,
                articles = com.glownote.mobile.data.groupArticles(records),
                settings = settings,
            )
        }
    }

    private suspend fun loadBrowserCollections() {
        val history = withContext(Dispatchers.IO) { repository.browserHistory() }
        val searches = withContext(Dispatchers.IO) { repository.searchHistory() }
        val bookmarks = withContext(Dispatchers.IO) { repository.browserBookmarks() }
        _ui.update {
            it.copy(
                browserHistory = history,
                searchHistory = searches.take(10),
                bookmarks = bookmarks,
            )
        }
    }

    private fun setStatus(message: String, error: Boolean) {
        _ui.update { it.copy(status = message, statusIsError = error) }
    }

    fun clearStatusIf(expected: String) {
        _ui.update {
            if (it.status == expected) {
                it.copy(status = "", statusIsError = false)
            } else {
                it
            }
        }
    }

    private fun annotationDraft(record: HighlightRecord): AnnotationDraft = AnnotationDraft(
        id = record.id,
        url = record.url,
        title = record.title,
        selectedText = record.selectedText,
        color = record.color,
        note = record.note,
        tagInput = (record.tags + record.noteTags).distinct().joinToString(", "),
        textOffset = record.anchor["textOffset"]?.jsonPrimitive?.intOrNull ?: -1,
    )

    private fun looksLikeWebAddress(value: String): Boolean {
        return value.contains("://") ||
            value.startsWith("www.", ignoreCase = true) ||
            (!value.contains(' ') && value.contains('.'))
    }
}
