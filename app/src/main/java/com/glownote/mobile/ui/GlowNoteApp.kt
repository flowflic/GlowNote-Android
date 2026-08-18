package com.glownote.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ActionMode
import android.view.InputDevice
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import java.util.IdentityHashMap
import com.glownote.mobile.BuildConfig
import com.glownote.mobile.data.browserDeepLinkToWebUrl
import com.glownote.mobile.data.normalizeWebUrl
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glownote.mobile.annotation.AnnotationScript
import com.glownote.mobile.data.AppSettings
import com.glownote.mobile.data.ArticleGroup
import com.glownote.mobile.data.BrowserBookmark
import com.glownote.mobile.data.BrowserHistoryEntry
import com.glownote.mobile.data.HighlightRecord
import com.glownote.mobile.data.PageHighlightClick
import com.glownote.mobile.data.PageSelection
import com.glownote.mobile.data.ReaderSettings
import com.glownote.mobile.data.SearchEngine
import com.glownote.mobile.data.SearchHistoryEntry
import com.glownote.mobile.data.WebDavSettings
import com.glownote.mobile.data.glowJson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

private val Ink = Color(0xFF17202A)
private val Muted = Color(0xFF6B6A66)
private val Paper = Color(0xFFFFFCF7)
private val HighlightColors = listOf(
    "yellow" to Color(0xFFF5E2A4),
    "red" to Color(0xFFFF8A80),
    "blue" to Color(0xFF82B1FF),
    "green" to Color(0xFFB9F6CA),
    "orange" to Color(0xFFFFD180),
)
private val HeatmapColors = listOf(
    Color(0xFFF0F5F8),
    Color(0xFFD7EAF4),
    Color(0xFFA8D1E6),
    Color(0xFF6FAED0),
    Color(0xFF1E6FA8),
)

private const val TAB_PREVIEW_WIDTH_PX = 600
private const val TAB_PREVIEW_HEIGHT_PX = 760

private enum class BrowserPanel {
    HISTORY,
    BOOKMARKS,
}

private fun copyHighlightText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("GlowNote 高亮", text))
    Toast.makeText(context, "已复制高亮文本", Toast.LENGTH_SHORT).show()
}

private val ClipboardWebUrlPattern = Regex(
    """https?://[^\s<>\"'，。！？、]+""",
    RegexOption.IGNORE_CASE,
)

private fun readClipboardWebUrl(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    if (!clipboard.hasPrimaryClip()) return null
    if (clipboard.primaryClipDescription?.label?.toString() == "GlowNote 高亮") return null
    val item = clipboard.primaryClip?.getItemAt(0) ?: return null
    val clipboardText = item.coerceToText(context)?.toString().orEmpty().ifBlank {
        item.uri?.toString().orEmpty()
    }
    val candidate = ClipboardWebUrlPattern.find(clipboardText)?.value
        ?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '。', '，', '！', '？', '；')
        ?: return null
    return normalizeWebUrl(candidate)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChromeBrowserScreen(state: GlowNoteUiState, viewModel: GlowNoteViewModel) {
    var address by remember(state.activeBrowserTabId, state.currentUrl) { mutableStateOf(state.currentUrl) }
    var showAnnotationPanel by remember { mutableStateOf(false) }
    var showTabOverview by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var browserPanel by remember { mutableStateOf<BrowserPanel?>(null) }
    var showSearchHistory by remember { mutableStateOf(false) }
    var showSearchEngineDialog by remember { mutableStateOf(false) }
    var clearSelectionRequest by remember { mutableStateOf(0) }
    var readerModeRequest by remember { mutableStateOf(0) }
    var goBackRequest by remember { mutableStateOf(0) }
    var activeWebViewCanGoBack by remember(state.activeBrowserTabId) { mutableStateOf(false) }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val activeTab = state.browserTabs.firstOrNull { it.id == state.activeBrowserTabId }
    val tabPreviews = remember { mutableStateMapOf<String, Bitmap>() }
    LaunchedEffect(state.browserTabs) {
        val activeIds = state.browserTabs.mapTo(hashSetOf()) { it.id }
        tabPreviews.keys.toList()
            .filterNot { it in activeIds }
            .forEach { tabPreviews.remove(it) }
    }
    val isNewTab = activeTab?.isNewTab == true
    val activeTabIndex = state.browserTabs.indexOfFirst { it.id == state.activeBrowserTabId }
    val previousTab = when {
        activeTabIndex > 0 -> state.browserTabs.getOrNull(activeTabIndex - 1)
        activeTabIndex == 0 -> state.browserTabs.getOrNull(1)
        else -> null
    }
    val submitAddress = {
        showSearchHistory = false
        viewModel.openAddressOrSearch(address)
        keyboardController?.hide()
        Unit
    }
    val clearPageSelection = { clearSelectionRequest += 1 }
    val goHome = {
        clearPageSelection()
        showMoreMenu = false
        browserPanel = null
        showSearchHistory = false
        viewModel.openBrowserHome()
        Unit
    }
    val addTab = {
        clearPageSelection()
        showTabOverview = false
        showMoreMenu = false
        browserPanel = null
        showSearchHistory = false
        viewModel.addBrowserTab()
        Unit
    }
    val goBackFromBrowser = {
        when {
            state.draft != null -> {
                clearPageSelection()
                viewModel.dismissDraft()
            }
            activeWebViewCanGoBack -> {
                goBackRequest += 1
            }
            previousTab != null -> {
                clearPageSelection()
                viewModel.switchBrowserTab(previousTab.id)
            }
            !isNewTab -> {
                clearPageSelection()
                viewModel.openBrowserHome()
            }
            else -> {
                viewModel.navigateTo(AppScreen.LIBRARY)
            }
        }
        Unit
    }
    val sharePage = {
        val tab = state.browserTabs.firstOrNull { it.id == state.activeBrowserTabId }
        shareWebPage(
            context = context,
            url = state.currentUrl.ifBlank { tab?.url.orEmpty() },
        )
        Unit
    }
    val openHistory = {
        showMoreMenu = false
        browserPanel = BrowserPanel.HISTORY
        Unit
    }
    val openBookmarks = {
        showMoreMenu = false
        browserPanel = BrowserPanel.BOOKMARKS
        Unit
    }
    val openSearchEngineDialog = {
        showMoreMenu = false
        showTabOverview = false
        showSearchEngineDialog = true
        Unit
    }
    LaunchedEffect(state.draft?.url, state.draft?.selectedText) {
        // A plain highlight opens the compact action bar. A highlight that
        // already has a note or tags keeps the direct edit flow.
        val currentDraft = state.draft
        showAnnotationPanel = currentDraft?.id != null &&
            (currentDraft.note.isNotBlank() || currentDraft.tagInput.isNotBlank())
    }
    BackHandler {
        when {
            showSearchEngineDialog -> showSearchEngineDialog = false
            showSearchHistory -> showSearchHistory = false
            showMoreMenu -> showMoreMenu = false
            browserPanel != null -> browserPanel = null
            showTabOverview -> showTabOverview = false
            state.draft != null -> {
                clearPageSelection()
                viewModel.dismissDraft()
            }
            else -> goBackFromBrowser()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChromeBrowserBar(
                address = address,
                onAddressChange = { address = it },
                onSubmitAddress = submitAddress,
                onBack = goBackFromBrowser,
                onNewTab = addTab,
                onShowTabs = { showTabOverview = true },
                onMore = { showMoreMenu = true },
                isReaderMode = activeTab?.isReaderMode == true,
                onReader = {
                    showMoreMenu = false
                    readerModeRequest += 1
                },
                tabCount = state.browserTabs.size,
                showAddressField = !isNewTab,
            )
            if (state.status.isNotBlank()) {
                Text(
                    state.status,
                    color = if (state.statusIsError) Color(0xFFB3261E) else Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.fillMaxSize()) {
                state.browserTabs.forEach { tab ->
                    key(tab.id, tab.isNewTab) {
                        if (tab.isNewTab) {
                            if (tab.id == state.activeBrowserTabId) {
                                NewTabPage(
                                    recentSearches = if (showSearchHistory) state.searchHistory else emptyList(),
                                    onFocus = {
                                        showSearchHistory = true
                                    },
                                    onSubmit = { value ->
                                        showSearchHistory = false
                                        keyboardController?.hide()
                                        viewModel.openAddressOrSearch(value)
                                    },
                                    onSearchHistoryItem = { entry ->
                                        showSearchHistory = false
                                        keyboardController?.hide()
                                        viewModel.openAddressOrSearch(entry.query)
                                    },
                                )
                            }
                        } else {
                            WebPageView(
                                tabId = tab.id,
                                isActive = tab.id == state.activeBrowserTabId,
                                url = tab.url,
                                records = state.records.filter { it.normalizedUrl == com.glownote.mobile.data.normalizeUrl(tab.url) },
                                onReady = {},
                                clearSelectionRequest = clearSelectionRequest,
                                readerModeRequest = readerModeRequest,
                                readerSettings = state.settings.reader,
                                goBackRequest = goBackRequest,
                                scrollToHighlightRequest = state.pendingHighlightJump,
                                onLoaded = { loadedTabId, url, title ->
                                    if (loadedTabId == state.activeBrowserTabId) address = url
                                    viewModel.onPageLoaded(loadedTabId, url, title)
                                },
                                onSelection = viewModel::onSelection,
                                onHighlightClick = viewModel::onHighlightClicked,
                                onReaderModeChanged = { changedTabId, enabled ->
                                    if (changedTabId == state.activeBrowserTabId) {
                                        viewModel.setReaderMode(changedTabId, enabled)
                                    }
                                },
                                onReaderSettingsChanged = { changedTabId, settings ->
                                    if (changedTabId == state.activeBrowserTabId) {
                                        viewModel.saveReaderSettings(settings)
                                    }
                                },
                                onPageTap = {
                                    clearPageSelection()
                                    viewModel.dismissDraft()
                                },
                                onOpenNewWindow = viewModel::openBrowserTab,
                                onNavigationStateChanged = { changedTabId, canGoBack ->
                                    if (changedTabId == state.activeBrowserTabId) {
                                        activeWebViewCanGoBack = canGoBack
                                    }
                                },
                                onPreviewCaptured = { capturedTabId, preview ->
                                    tabPreviews[capturedTabId] = preview
                                },
                            )
                        }
                    }
                }
                state.draft?.let { draft ->
                    if (showAnnotationPanel) {
                        AnnotationPanel(
                            draft = draft,
                            onSave = { color, note, tagInput ->
                                clearPageSelection()
                                viewModel.saveDraft(color, note, tagInput)
                            },
                            onDelete = if (draft.id == null) null else {
                                {
                                    clearPageSelection()
                                    viewModel.deleteDraft()
                                }
                            },
                            onCopy = { copyHighlightText(context, draft.selectedText) },
                        )
                    } else {
                        AnnotationToolbar(
                            selectionTop = draft.selectionTop,
                            selectionBottom = draft.selectionBottom,
                            selectedColor = draft.id?.let { draft.color },
                            onHighlight = { color ->
                                clearPageSelection()
                                viewModel.saveDraft(color, draft.note, draft.tagInput)
                            },
                            onAnnotate = { showAnnotationPanel = true },
                            onDelete = if (draft.id == null) null else {
                                {
                                    clearPageSelection()
                                    viewModel.deleteDraft()
                                }
                            },
                        )
                    }
                }
            }
        }
        if (showTabOverview) {
            BrowserTabOverview(
                tabs = state.browserTabs,
                previews = tabPreviews,
                activeTabId = state.activeBrowserTabId,
                onHome = goHome,
                onNewTab = addTab,
                onMore = { showMoreMenu = true },
                onSelect = { id ->
                    clearPageSelection()
                    showTabOverview = false
                    viewModel.switchBrowserTab(id)
                },
                onClose = { id ->
                    clearPageSelection()
                    tabPreviews.remove(id)
                    viewModel.closeBrowserTab(id)
                },
            )
        }
        browserPanel?.let { panel ->
            BrowserCollectionPanel(
                panel = panel,
                history = state.browserHistory,
                bookmarks = state.bookmarks,
                onClose = { browserPanel = null },
                onOpen = { url, title ->
                    browserPanel = null
                    viewModel.openArticle(url, title)
                },
                onRemoveBookmark = viewModel::removeBookmark,
                onClearHistory = viewModel::clearBrowserHistory,
            )
        }
        if (showMoreMenu) {
            BrowserMoreMenu(
                isBookmarked = state.bookmarks.any { it.url == state.currentUrl },
                searchEngine = SearchEngine.fromId(state.settings.searchEngine),
                onDismiss = { showMoreMenu = false },
                onShare = {
                    showMoreMenu = false
                    sharePage()
                },
                onHistory = openHistory,
                onToggleBookmark = {
                    showMoreMenu = false
                    viewModel.toggleBookmark()
                },
                onBookmarks = openBookmarks,
                onSearchEngine = openSearchEngineDialog,
            )
        }
        if (showSearchEngineDialog) {
            SearchEngineDialog(
                selected = SearchEngine.fromId(state.settings.searchEngine),
                onDismiss = { showSearchEngineDialog = false },
                onSelect = { engine ->
                    viewModel.setSearchEngine(engine)
                    showSearchEngineDialog = false
                },
            )
        }
    }
}

private fun shareWebPage(context: Context, url: String) {
    if (url.isBlank()) return
    val shareText = url
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(shareIntent, "分享网页"))
}

@Composable
private fun ChromeBrowserBar(
    address: String,
    onAddressChange: (String) -> Unit,
    onSubmitAddress: () -> Unit,
    onBack: () -> Unit,
    onNewTab: () -> Unit,
    onShowTabs: () -> Unit,
    onMore: () -> Unit,
    isReaderMode: Boolean,
    onReader: () -> Unit,
    tabCount: Int,
    showAddressField: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF8F6EE),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChromeIconButton(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回上一页",
                onClick = onBack,
            )
            if (showAddressField) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    color = Color(0xFFECEAE2),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = address,
                            onValueChange = onAddressChange,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 15.dp, top = 11.dp, bottom = 11.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = { onSubmitAddress() },
                                onDone = { onSubmitAddress() },
                            ),
                            decorationBox = { innerTextField ->
                                if (address.isBlank()) {
                                    Text("输入网页地址或搜索内容", color = Muted, fontSize = 14.sp, maxLines = 1)
                                }
                                innerTextField()
                            },
                        )
                        IconButton(
                            onClick = onReader,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = "阅读模式与阅读设置",
                                tint = if (isReaderMode) MaterialTheme.colorScheme.primary else Ink,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            ChromeIconButton(
                imageVector = Icons.Default.Add,
                contentDescription = "新建标签页",
                onClick = onNewTab,
            )
            IconButton(onClick = onShowTabs, modifier = Modifier.size(40.dp)) {
                Surface(
                    modifier = Modifier.size(24.dp),
                    color = Color.Transparent,
                    shape = RoundedCornerShape(5.dp),
                    border = BorderStroke(2.dp, Ink),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tabCount.toString(),
                            color = Ink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            ChromeIconButton(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "更多功能",
                onClick = onMore,
            )
        }
    }
}

@Composable
private fun NewTabPage(
    recentSearches: List<SearchHistoryEntry>,
    onFocus: () -> Unit,
    onSubmit: (String) -> Unit,
    onSearchHistoryItem: (SearchHistoryEntry) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchFieldFocused by remember { mutableStateOf(false) }
    val searchFieldShape = RoundedCornerShape(30.dp)
    LaunchedEffect(searchFieldFocused) {
        if (searchFieldFocused) {
            delay(50)
            keyboardController?.show()
        }
    }
    val submitQuery = {
        val value = query.trim()
        if (value.isNotBlank()) {
            onSubmit(value)
            keyboardController?.hide()
        }
        Unit
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-70).dp)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "GlowNote",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 42.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.height(22.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        searchFieldFocused = it.isFocused
                        if (it.isFocused) onFocus()
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = { submitQuery() },
                    onDone = { submitQuery() },
                ),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(searchFieldShape)
                            .background(Color.White)
                            .border(BorderStroke(1.dp, Color(0xFFE1E3E6)), searchFieldShape)
                            .clickable {
                                focusRequester.requestFocus()
                            }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Muted,
                            modifier = Modifier.size(23.dp),
                        )
                        Spacer(Modifier.width(11.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isBlank()) {
                                Text(
                                    text = "搜索或输入网址",
                                    color = Muted,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    }
                },
            )
            if (recentSearches.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFFE1E3E6)),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "最近搜索",
                            color = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 17.dp, vertical = 7.dp),
                        )
                        recentSearches.take(10).forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .clickable { onSearchHistoryItem(entry) }
                                    .padding(horizontal = 17.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = Muted,
                                    modifier = Modifier.size(19.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = entry.query,
                                    color = Ink,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChromeIconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = Ink,
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
private fun BrowserTabOverview(
    tabs: List<BrowserTab>,
    previews: Map<String, Bitmap>,
    activeTabId: String,
    onHome: () -> Unit,
    onNewTab: () -> Unit,
    onMore: () -> Unit,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F3EC),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChromeIconButton(
                    imageVector = Icons.Default.Home,
                    contentDescription = "回到主页",
                    onClick = onHome,
                )
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text("打开的标签页", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${tabs.size} 个页面", color = Muted, fontSize = 12.sp)
                }
                ChromeIconButton(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建标签页",
                    onClick = onNewTab,
                )
                ChromeIconButton(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多功能",
                    onClick = onMore,
                )
            }
            Divider(color = Color(0xFFE5DED1))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tabs.chunked(2).forEach { rowTabs ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowTabs.forEach { tab ->
                            BrowserTabCard(
                                modifier = Modifier.weight(1f),
                                tab = tab,
                                preview = previews[tab.id],
                                isActive = tab.id == activeTabId,
                                canClose = tabs.size > 1,
                                onSelect = { onSelect(tab.id) },
                                onClose = { onClose(tab.id) },
                            )
                        }
                        if (rowTabs.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .clickable(onClick = onNewTab),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, Color(0xFFD7CFC1)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(7.dp))
                        Text("新建标签页", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserTabCard(
    modifier: Modifier = Modifier,
    tab: BrowserTab,
    preview: Bitmap?,
    isActive: Boolean,
    canClose: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(278.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect),
        color = if (isActive) Color(0xFFE8F0F8) else Color(0xFFE8E8DF),
        border = BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else Color(0xFFE2D9CC),
        ),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(start = 9.dp, end = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    color = if (tab.isNewTab) Color(0xFFDDE7D0) else Color.White,
                    shape = RoundedCornerShape(9.dp),
                ) {
                    Icon(
                        imageVector = if (tab.isNewTab) Icons.Default.Search else Icons.Default.Language,
                        contentDescription = null,
                        tint = if (tab.isNewTab) Color(0xFF4E752A) else Color(0xFF4D5B65),
                        modifier = Modifier.padding(5.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tab.title.ifBlank { tab.url.hostForDisplay().ifBlank { "新标签页" } },
                    color = Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (canClose) {
                    IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭标签页", tint = Muted, modifier = Modifier.size(19.dp))
                    }
                }
            }
            when {
                preview != null -> {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "${tab.title.ifBlank { "网页" }}网页预览",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }

                tab.isNewTab -> {
                    TabPreviewPlaceholder(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Search,
                        label = "搜索或输入网址",
                        tint = Color(0xFF4E752A),
                    )
                }

                else -> {
                    TabPreviewPlaceholder(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Language,
                        label = "正在加载网页预览…",
                        tint = Color(0xFF4D5B65),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabPreviewPlaceholder(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 5.dp, end = 5.dp, bottom = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF9F8F4)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BrowserMoreMenu(
    isBookmarked: Boolean,
    searchEngine: SearchEngine,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onHistory: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onSearchEngine: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 68.dp, end = 8.dp)
                .width(210.dp)
                .clickable(onClick = {}),
            color = Paper,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0xFFE2D9CC)),
            shadowElevation = 18.dp,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "更多功能",
                        color = Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭更多功能")
                    }
                }
                BrowserMoreMenuRow(Icons.Default.Share, "分享网页", onShare)
                BrowserMoreMenuRow(Icons.Default.Search, "搜索引擎：${searchEngine.label}", onSearchEngine)
                BrowserMoreMenuRow(Icons.Default.History, "查看浏览历史", onHistory)
                BrowserMoreMenuRow(
                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    label = if (isBookmarked) "取消收藏" else "添加到收藏",
                    onClick = onToggleBookmark,
                )
                BrowserMoreMenuRow(Icons.Default.BookmarkBorder, "书签管理", onBookmarks)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SearchEngineDialog(
    selected: SearchEngine,
    onDismiss: () -> Unit,
    onSelect: (SearchEngine) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择搜索引擎") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SearchEngine.values().forEach { engine ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(engine) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = engine == selected,
                            onClick = { onSelect(engine) },
                        )
                        Text(engine.label, color = Ink, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun BrowserMoreMenuRow(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector, contentDescription = null, tint = Ink, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Ink, fontSize = 15.sp)
    }
}

@Composable
private fun BrowserCollectionPanel(
    panel: BrowserPanel,
    history: List<BrowserHistoryEntry>,
    bookmarks: List<BrowserBookmark>,
    onClose: () -> Unit,
    onOpen: (String, String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val isHistory = panel == BrowserPanel.HISTORY
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Paper,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回网页")
                }
                Text(
                    text = if (isHistory) "浏览历史" else "书签管理",
                    color = Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (isHistory && history.isNotEmpty()) {
                    IconButton(onClick = onClearHistory) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "清空浏览历史", tint = Muted)
                    }
                }
            }
            Divider(color = Color(0xFFE5DED1))
            val hasEntries = if (isHistory) history.isNotEmpty() else bookmarks.isNotEmpty()
            if (!hasEntries) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (isHistory) Icons.Default.History else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = Color(0xFFB7B1A8),
                        modifier = Modifier.size(42.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (isHistory) "还没有浏览记录" else "还没有收藏网页",
                        color = Muted,
                        fontSize = 15.sp,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isHistory) {
                        history.forEach { entry ->
                            BrowserSavedPageRow(
                                icon = Icons.Default.History,
                                title = entry.title.ifBlank { entry.url.hostForDisplay() },
                                url = entry.url,
                                onClick = { onOpen(entry.url, entry.title) },
                            )
                        }
                    } else {
                        bookmarks.forEach { entry ->
                            BrowserSavedPageRow(
                                icon = Icons.Default.Bookmark,
                                title = entry.title.ifBlank { entry.url.hostForDisplay() },
                                url = entry.url,
                                onClick = { onOpen(entry.url, entry.title) },
                                onRemove = { onRemoveBookmark(entry.url) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserSavedPageRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    url: String,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title.ifBlank { "网页" },
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = url,
                color = Muted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "删除收藏", tint = Muted, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
fun GlowNoteApp(viewModel: GlowNoteViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var clipboardUrlPrompt by remember { mutableStateOf<String?>(null) }
    val activeBrowserTab = state.browserTabs.firstOrNull { it.id == state.activeBrowserTabId }
    val showBottomNavigation = state.screen != AppScreen.BROWSER || activeBrowserTab?.isNewTab != false
    LaunchedEffect(Unit) {
        clipboardUrlPrompt = readClipboardWebUrl(context)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomNavigation) {
                GlowNoteNavigation(state.screen, viewModel::navigateTo)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.screen) {
                AppScreen.LIBRARY -> LibraryScreen(state, viewModel)
                AppScreen.BROWSER -> ChromeBrowserScreen(state, viewModel)
                AppScreen.ARTICLE_DETAIL -> ArticleDetailScreen(state, viewModel)
                AppScreen.SETTINGS -> SettingsScreen(state, viewModel)
            }
        }
    }
    clipboardUrlPrompt?.let { url ->
        AlertDialog(
            onDismissRequest = { clipboardUrlPrompt = null },
            title = { Text("发现网页链接") },
            text = { Text("剪贴板中检测到网页链接，是否直接打开？\n\n$url") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardUrlPrompt = null
                        viewModel.openArticle(url)
                    },
                ) {
                    Text("打开")
                }
            },
            dismissButton = {
                TextButton(onClick = { clipboardUrlPrompt = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun GlowNoteNavigation(screen: AppScreen, onNavigate: (AppScreen) -> Unit) {
    NavigationBar(containerColor = Paper) {
        NavigationBarItem(
            selected = screen == AppScreen.LIBRARY || screen == AppScreen.ARTICLE_DETAIL,
            onClick = { onNavigate(AppScreen.LIBRARY) },
            icon = { Icon(Icons.Default.MenuBook, contentDescription = "文章") },
            label = { Text("文章") },
        )
        NavigationBarItem(
            selected = screen == AppScreen.BROWSER,
            onClick = { onNavigate(AppScreen.BROWSER) },
            icon = { Icon(Icons.Default.Language, contentDescription = "网页") },
            label = { Text("网页") },
        )
        NavigationBarItem(
            selected = screen == AppScreen.SETTINGS,
            onClick = { onNavigate(AppScreen.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
            label = { Text("设置") },
        )
    }
}

@Composable
private fun LibraryScreen(state: GlowNoteUiState, viewModel: GlowNoteViewModel) {
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun closeSearch() {
        showSearch = false
        query = ""
        keyboardController?.hide()
    }

    BackHandler(enabled = showSearch) { closeSearch() }

    LaunchedEffect(showSearch) {
        if (showSearch) searchFocusRequester.requestFocus()
    }

    val searchQuery = query.trim().removePrefix("#").trim()
    val visibleArticles = if (searchQuery.isBlank()) {
        state.articles
    } else {
        state.articles.mapNotNull { article ->
            val matchingRecords = article.records.filter { record ->
                record.selectedText.contains(searchQuery, ignoreCase = true) ||
                    record.note.contains(searchQuery, ignoreCase = true) ||
                    (record.tags + record.noteTags).any { tag ->
                        tag.contains(searchQuery, ignoreCase = true)
                    }
            }
            matchingRecords.takeIf { it.isNotEmpty() }?.let {
                article.copy(records = it)
            }
        }
    }
    val matchingRecordCount = visibleArticles.sumOf { it.records.size }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            TabletLibraryLayout(
                state = state,
                viewModel = viewModel,
                visibleArticles = visibleArticles,
                searchQuery = searchQuery,
                matchingRecordCount = matchingRecordCount,
                showSearch = showSearch,
                query = query,
                onQueryChange = { query = it },
                onToggleSearch = { if (showSearch) closeSearch() else showSearch = true },
                searchFocusRequester = searchFocusRequester,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            ) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "GLOWNOTE",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "你的阅读轨迹",
                    color = Ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = viewModel::syncNow) {
                Icon(Icons.Default.Sync, contentDescription = "同步")
            }
            IconButton(
                onClick = {
                    if (showSearch) closeSearch() else showSearch = true
                },
            ) {
                Icon(
                    imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (showSearch) "关闭搜索" else "搜索高亮、批注和标签",
                )
            }
        }

        if (showSearch) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester),
                singleLine = true,
                placeholder = { Text("搜索高亮、批注或标签") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = if (query.isNotBlank()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清空搜索")
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            )
        }

        Spacer(Modifier.height(16.dp))
        AnnotationHeatmap(state.records)

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("文章", state.articles.size.toString(), Modifier.weight(1f))
            StatCard("高亮", state.records.size.toString(), Modifier.weight(1f))
            StatCard("同步", if (state.settings.webdav.enabled) "开启" else "关闭", Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("文章列表", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (searchQuery.isNotBlank()) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${visibleArticles.size} 篇 · $matchingRecordCount 条命中",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                )
            }
        }

        if (state.status.isNotBlank()) {
            StatusLine(state.status, state.statusIsError)
        }

        Spacer(Modifier.height(10.dp))
        if (state.articles.isEmpty()) {
            EmptyLibraryCard { viewModel.navigateTo(AppScreen.BROWSER) }
        } else if (searchQuery.isNotBlank() && visibleArticles.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Paper),
                border = BorderStroke(1.dp, Color(0xFFE6DED1)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "没有找到与“${query.trim()}”匹配的高亮、批注或标签",
                    color = Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                )
            }
        } else {
            visibleArticles.forEach { article ->
                ArticleCard(
                    article = article,
                    onView = { viewModel.openArticleDetail(article) },
                    onOpenWeb = { viewModel.openArticle(article.url, article.title) },
                    onStar = { viewModel.toggleArticleStar(article) },
                    onDelete = { viewModel.deleteArticle(article) },
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TabletLibraryLayout(
    state: GlowNoteUiState,
    viewModel: GlowNoteViewModel,
    visibleArticles: List<ArticleGroup>,
    searchQuery: String,
    matchingRecordCount: Int,
    showSearch: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    searchFocusRequester: FocusRequester,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val selectedArticle = visibleArticles.firstOrNull { it.key == state.selectedArticleKey }
        ?: visibleArticles.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "GLOWNOTE",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "你的阅读轨迹",
                    color = Ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = viewModel::syncNow) {
                Icon(Icons.Default.Sync, contentDescription = "同步")
            }
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (showSearch) "关闭搜索" else "搜索高亮、批注和标签",
                )
            }
        }

        if (showSearch) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester),
                singleLine = true,
                placeholder = { Text("搜索高亮、批注或标签") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotBlank()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "清空搜索")
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            )
        }

        if (state.status.isNotBlank()) {
            StatusLine(state.status, state.statusIsError)
        }

        Spacer(Modifier.height(12.dp))
        AnnotationHeatmap(state.records)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("文章", state.articles.size.toString(), Modifier.weight(1f))
            StatCard("高亮", state.records.size.toString(), Modifier.weight(1f))
            StatCard("同步", if (state.settings.webdav.enabled) "开启" else "关闭", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 360.dp)
                    .fillMaxHeight(),
                color = Paper,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0xFFE6DED1)),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("文章列表", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "${visibleArticles.size} 篇"
                            } else {
                                "${visibleArticles.size} 篇 · $matchingRecordCount 条命中"
                            },
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Divider(color = Color(0xFFE6DED1))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        when {
                            state.articles.isEmpty() -> EmptyLibraryCard { viewModel.navigateTo(AppScreen.BROWSER) }
                            searchQuery.isNotBlank() && visibleArticles.isEmpty() -> {
                                Text(
                                    text = "没有找到与“${query.trim()}”匹配的高亮、批注或标签",
                                    color = Muted,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                )
                            }
                            else -> visibleArticles.forEach { article ->
                                ArticleCard(
                                    article = article,
                                    onView = { viewModel.selectArticle(article) },
                                    onOpenWeb = { viewModel.openArticle(article.url, article.title) },
                                    onStar = { viewModel.toggleArticleStar(article) },
                                    onDelete = { viewModel.deleteArticle(article) },
                                    selected = selectedArticle?.key == article.key,
                                    compact = true,
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                color = Paper,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0xFFE6DED1)),
            ) {
                if (selectedArticle == null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = Muted, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("选择左侧文章查看批注", color = Muted, fontSize = 15.sp)
                    }
                } else {
                    ArticleDetailPane(
                        state = state,
                        viewModel = viewModel,
                        article = selectedArticle,
                        modifier = Modifier.fillMaxSize(),
                        showBackButton = false,
                    )
                }
            }
        }
    }
}

private fun recordCreatedDate(record: HighlightRecord, zoneId: ZoneId): LocalDate? {
    val timestamp = record.createdAt.trim()
    if (timestamp.isBlank()) return null
    return runCatching {
        Instant.parse(timestamp).atZone(zoneId).toLocalDate()
    }.getOrElse {
        runCatching { LocalDate.parse(timestamp.take(10)) }.getOrNull()
    }
}

private fun heatmapLevel(count: Int, maxCount: Int): Int {
    if (count <= 0 || maxCount <= 0) return 0
    if (maxCount == 1) return 1
    return when {
        count * 4 >= maxCount * 3 -> 4
        count * 2 >= maxCount -> 3
        count * 4 >= maxCount -> 2
        else -> 1
    }
}

@Composable
private fun AnnotationHeatmap(records: List<HighlightRecord>) {
    val month = YearMonth.now()
    val zoneId = ZoneId.systemDefault()
    var isExpanded by remember(month) { mutableStateOf(false) }
    var selectedDate by remember(month) { mutableStateOf<LocalDate?>(null) }
    val counts = records
        .mapNotNull { record -> recordCreatedDate(record, zoneId) }
        .filter { date -> YearMonth.from(date) == month }
        .groupingBy { it }
        .eachCount()
    val maxCount = counts.values.maxOrNull() ?: 0
    val totalCount = counts.values.sum()
    val selectedCount = selectedDate?.let { counts[it] ?: 0 }
    val cells = mutableListOf<Int?>()
    repeat(month.atDay(1).dayOfWeek.value - 1) { cells += null }
    for (day in 1..month.lengthOfMonth()) cells += day

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0F8)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("高亮&批注热力图", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.height(2.dp))
                    Text("${month.year}年${month.monthValue}月 · 每天记录频率", fontSize = 12.sp, color = Muted)
                }
                Text(
                    text = if (totalCount == 0) "本月暂无记录" else "本月 $totalCount 条",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "收起热力图" else "展开热力图",
                    )
                }
            }
            if (isExpanded) {
                selectedDate?.let { date ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${date.monthValue}月${date.dayOfMonth}日 · ${selectedCount ?: 0} 条批注",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Text(
                            text = label,
                            color = Muted,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                cells.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        for (column in 0 until 7) {
                            val day = week.getOrNull(column)
                            if (day == null) {
                                Spacer(Modifier.weight(1f).height(29.dp))
                            } else {
                                val date = month.atDay(day)
                                val count = counts[date] ?: 0
                                val level = heatmapLevel(count, maxCount)
                                Surface(
                                    color = HeatmapColors[level],
                                    border = if (selectedDate == date) {
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else null,
                                    shape = RoundedCornerShape(7.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(29.dp)
                                        .clickable {
                                            selectedDate = if (selectedDate == date) null else date
                                        },
                                ) {}
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("少", color = Muted, fontSize = 10.sp)
                    Spacer(Modifier.width(4.dp))
                    HeatmapColors.drop(1).forEachIndexed { index, color ->
                        Surface(
                            color = color,
                            shape = RoundedCornerShape(3.dp),
                            modifier = Modifier.size(11.dp),
                        ) {}
                        if (index < HeatmapColors.lastIndex - 1) Spacer(Modifier.width(3.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("多", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Paper,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE6DED1)),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(label, fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun EmptyLibraryCard(onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = BorderStroke(1.dp, Color(0xFFE6DED1)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp))
            Text("还没有文章", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text("打开任意网页，选择一段文字开始记录。", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(15.dp))
            Button(onClick = onOpen) { Text("去打开网页") }
        }
    }
}

@Composable
private fun ArticleCard(
    article: ArticleGroup,
    onView: () -> Unit,
    onOpenWeb: () -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
    selected: Boolean = false,
    compact: Boolean = false,
) {
    val cardShape = RoundedCornerShape(22.dp)
    val revealWidth = 76.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val revealWidthPx = with(density) { revealWidth.toPx() }
    val offsetX = remember(article.key) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun settleCard() {
        scope.launch {
            offsetX.animateTo(if (offsetX.value <= -revealWidthPx / 2f) -revealWidthPx else 0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape),
    ) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFFF4E7D4), cardShape)
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    onDelete()
                    scope.launch { offsetX.animateTo(0f) }
                },
                modifier = Modifier.size(64.dp),
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "删除文章", tint = Color(0xFFB3261E), modifier = Modifier.size(21.dp))
            }
        }

        Card(
            onClick = onView,
            colors = CardDefaults.cardColors(
                containerColor = if (selected) Color(0xFFE8F0F8) else Paper,
            ),
            border = BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else Color(0xFFE6DED1),
            ),
            shape = cardShape,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(article.key, revealWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            scope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-revealWidthPx, 0f))
                            }
                        },
                        onDragEnd = ::settleCard,
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f) }
                        },
                    )
                },
        ) {
            Column(
                Modifier.padding(
                    horizontal = if (compact) 12.dp else 15.dp,
                    vertical = if (compact) 10.dp else 13.dp,
                ),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = article.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 14.sp else 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = onStar,
                        modifier = Modifier.size(if (compact) 40.dp else 48.dp),
                    ) {
                        Icon(
                            imageVector = if (article.starred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "收藏",
                            tint = if (article.starred) Color(0xFFD99629) else Muted,
                        )
                    }
                }
                Spacer(Modifier.height(if (compact) 7.dp else 9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFFF4E7D4), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "${article.records.size} 个高亮",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            fontSize = if (compact) 11.sp else 12.sp,
                            color = Color(0xFF7D5125),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (article.tags.isNotEmpty()) {
                        Text(
                            text = article.tags.joinToString("  ") { "#$it" },
                            color = Muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    IconButton(onClick = onOpenWeb, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "打开网页", tint = Muted, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleDetailScreen(state: GlowNoteUiState, viewModel: GlowNoteViewModel) {
    val article = state.articles.firstOrNull { it.key == state.selectedArticleKey }
    if (article == null) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text("文章不存在或已被删除", color = Muted)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { viewModel.navigateTo(AppScreen.LIBRARY) }) { Text("返回文章") }
        }
        return
    }

    ArticleDetailPane(
        state = state,
        viewModel = viewModel,
        article = article,
        modifier = Modifier.fillMaxSize(),
        showBackButton = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleDetailPane(
    state: GlowNoteUiState,
    viewModel: GlowNoteViewModel,
    article: ArticleGroup,
    modifier: Modifier,
    showBackButton: Boolean,
) {
    val context = LocalContext.current
    var isEditingTitle by remember(article.key) { mutableStateOf(false) }
    var titleDraft by remember(article.key, article.title) { mutableStateOf(article.title) }
    var titleHasFocus by remember(article.key) { mutableStateOf(false) }
    var shareRecord by remember(article.key) { mutableStateOf<HighlightRecord?>(null) }
    val titleFocusRequester = remember(article.key) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun commitArticleTitle() {
        val nextTitle = titleDraft.trim()
        if (nextTitle.isNotBlank() && nextTitle != article.title) {
            viewModel.updateArticleTitle(article, nextTitle)
        }
        if (nextTitle.isBlank()) titleDraft = article.title
        isEditingTitle = false
        keyboardController?.hide()
    }

    LaunchedEffect(isEditingTitle) {
        if (isEditingTitle) {
            titleHasFocus = false
            titleFocusRequester.requestFocus()
        }
    }

    if (showBackButton) {
        BackHandler {
            if (isEditingTitle) {
                commitArticleTitle()
            } else {
                viewModel.navigateTo(AppScreen.LIBRARY)
            }
        }
    }

    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(if (showBackButton) 14.dp else 18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBackButton) {
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.LIBRARY) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回文章")
                    }
                }
                Text("文章批注", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.openArticle(article.url, article.title) }) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "打开网页")
                }
            }
            if (isEditingTitle) {
                BasicTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                titleHasFocus = true
                            } else if (titleHasFocus) {
                                commitArticleTitle()
                            }
                        },
                    singleLine = false,
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitArticleTitle() }),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            innerTextField()
                        }
                    },
                )
            } else {
                Text(
                    text = article.title,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            titleDraft = article.title
                            isEditingTitle = true
                        },
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(article.url, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Surface(color = Color(0xFFE8F0F8), shape = RoundedCornerShape(12.dp)) {
                Text("${article.records.size} 条高亮与批注", modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = Ink, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
            article.records.forEach { record ->
                val swatch = HighlightColors.firstOrNull { it.first == record.color }?.second ?: HighlightColors.first().second
                SwipeableHighlightCard(
                    record = record,
                    swatch = swatch,
                    onEdit = { viewModel.editHighlight(record) },
                    onCopy = { copyHighlightText(context, record.selectedText) },
                    onDelete = { viewModel.deleteHighlight(record.id) },
                    onJump = { viewModel.openHighlight(record) },
                    onShare = { shareRecord = record },
                )
                Spacer(Modifier.height(11.dp))
            }
            Spacer(Modifier.height(18.dp))
        }
        state.draft?.let { draft ->
            val currentIndex = article.records.indexOfFirst { it.id == draft.id }
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissDraft,
                sheetState = sheetState,
                containerColor = Paper,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = {
                    Surface(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(38.dp)
                            .height(4.dp),
                        color = Color(0xFFD1C8BC),
                        shape = RoundedCornerShape(50),
                    ) {}
                },
            ) {
                ArticleAnnotationDrawer(
                    draft = draft,
                    swatch = HighlightColors.firstOrNull { it.first == draft.color }?.second
                        ?: HighlightColors.first().second,
                    currentIndex = currentIndex,
                    totalCount = article.records.size,
                    onPrevious = if (currentIndex > 0) {
                        { viewModel.editHighlight(article.records[currentIndex - 1]) }
                    } else null,
                    onNext = if (currentIndex in 0 until article.records.lastIndex) {
                        { viewModel.editHighlight(article.records[currentIndex + 1]) }
                    } else null,
                    onSave = { color, note, tagInput -> viewModel.saveDraft(color, note, tagInput) },
                    onDelete = draft.id?.let { id -> { viewModel.deleteHighlight(id) } },
                    onCopy = { copyHighlightText(context, draft.selectedText) },
                )
            }
        }
        shareRecord?.let { record ->
            ShareCardSheet(
                record = record,
                onDismiss = { shareRecord = null },
            )
        }
    }
}

@Composable
private fun SwipeableHighlightCard(
    record: HighlightRecord,
    swatch: Color,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onJump: () -> Unit,
    onShare: () -> Unit,
) {
    val cardShape = RoundedCornerShape(18.dp)
    val revealWidth = 112.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val revealWidthPx = with(density) { revealWidth.toPx() }
    val offsetX = remember(record.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun settleCard() {
        scope.launch {
            offsetX.animateTo(if (offsetX.value <= -revealWidthPx / 2f) -revealWidthPx else 0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape),
    ) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFFF4E7D4), cardShape)
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    onCopy()
                    scope.launch { offsetX.animateTo(0f) }
                },
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "复制高亮", tint = Muted, modifier = Modifier.size(20.dp))
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "删除高亮", tint = Color(0xFFB3261E), modifier = Modifier.size(20.dp))
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(record.id, revealWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            scope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-revealWidthPx, 0f))
                            }
                        },
                        onDragEnd = ::settleCard,
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f) }
                        },
                    )
                }
                .clickable(onClick = onEdit),
            color = Paper,
            shape = cardShape,
            border = BorderStroke(1.dp, Color(0xFFE6DED1)),
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(swatch),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "“${record.selectedText}”",
                                color = Ink,
                                fontSize = 15.sp,
                                lineHeight = 23.sp,
                                maxLines = 7,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = onShare,
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "分享摘录",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = onJump,
                                modifier = Modifier
                                    .size(38.dp)
                                    .padding(start = 4.dp),
                            ) {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "跳转原文",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (record.note.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                color = Color(0xFFFFF5DD),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                MarkdownContent(
                                    value = record.note,
                                    modifier = Modifier.padding(10.dp),
                                    textColor = Color(0xFF55431E),
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                )
                            }
                        }
                        val tags = (record.tags + record.noteTags).distinct()
                        if (tags.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(tags.joinToString("  ") { "#$it" }, color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleAnnotationDrawer(
    draft: AnnotationDraft,
    swatch: Color,
    currentIndex: Int,
    totalCount: Int,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onSave: (String, String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onCopy: (() -> Unit)?,
) {
    var color by remember(draft.id, draft.selectedText) { mutableStateOf(draft.color) }
    var note by remember(draft.id, draft.selectedText) { mutableStateOf(draft.note) }
    var tagInput by remember(draft.id, draft.selectedText) { mutableStateOf(draft.tagInput) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var dragOffset by remember(draft.id) { mutableStateOf(0f) }
    var didNavigateInGesture by remember(draft.id) { mutableStateOf(false) }
    val swipeThresholdPx = with(density) { 72.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp, max = 620.dp)
            .offset { IntOffset(dragOffset.roundToInt(), 0) }
            .pointerInput(draft.id, onPrevious != null, onNext != null) {
                detectHorizontalDragGestures(
                    onDragStart = { didNavigateInGesture = false },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset = (dragOffset + dragAmount).coerceIn(
                            if (onNext == null) 0f else -with(density) { 120.dp.toPx() },
                            if (onPrevious == null) 0f else with(density) { 120.dp.toPx() },
                        )
                    },
                    onDragEnd = {
                        val direction = when {
                            !didNavigateInGesture && dragOffset <= -swipeThresholdPx && onNext != null -> 1
                            !didNavigateInGesture && dragOffset >= swipeThresholdPx && onPrevious != null -> -1
                            else -> 0
                        }
                        dragOffset = 0f
                        if (direction != 0) {
                            didNavigateInGesture = true
                            if (direction > 0) onNext?.invoke() else onPrevious?.invoke()
                        }
                    },
                    onDragCancel = {
                        dragOffset = 0f
                    },
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (currentIndex >= 0) "批注 ${currentIndex + 1} / $totalCount" else "编辑批注",
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "左右滑动切换",
                color = Muted,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = swatch.copy(alpha = 0.18f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(swatch),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "“${draft.selectedText}”",
                    color = Ink,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HighlightColors.forEach { (name, highlightColor) ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(highlightColor)
                        .border(
                            width = if (color == name) 3.dp else 1.dp,
                            color = if (color == name) MaterialTheme.colorScheme.primary else Color.White,
                            shape = CircleShape,
                        )
                        .clickable { color = name },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            label = { Text("批注（可用 #标签）") },
            placeholder = { Text("写下你为什么要留下这段话…") },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = tagInput,
            onValueChange = { tagInput = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("标签（逗号分隔）") },
            placeholder = { Text("例如：agent, 重点") },
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onCopy != null) {
                IconButton(onClick = onCopy, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(19.dp))
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除", modifier = Modifier.size(19.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { onSave(color, note, tagInput) },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = "保存", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun StatusLine(message: String, isError: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        color = if (isError) Color(0xFFFFE5E0) else Color(0xFFE4F3E7),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isError) Icons.Default.Close else Icons.Default.CloudDone,
                contentDescription = null,
                tint = if (isError) Color(0xFFB3261E) else Color(0xFF2F7544),
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(message, color = if (isError) Color(0xFF8A1C15) else Color(0xFF2F6040), fontSize = 12.sp)
        }
    }
}

@Composable
private fun BrowserScreen(state: GlowNoteUiState, viewModel: GlowNoteViewModel) {
    var address by remember(state.activeBrowserTabId, state.currentUrl) { mutableStateOf(state.currentUrl) }
    var showAnnotationPanel by remember { mutableStateOf(false) }
    var clearSelectionRequest by remember { mutableStateOf(0) }
    var readerModeRequest by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val submitAddress = {
        viewModel.openAddressOrSearch(address)
        keyboardController?.hide()
        Unit
    }
    val clearPageSelection = { clearSelectionRequest += 1 }
    LaunchedEffect(state.draft?.url, state.draft?.selectedText) {
        // A plain highlight opens the compact action bar. A highlight that
        // already has a note or tags keeps the direct edit flow.
        val currentDraft = state.draft
        showAnnotationPanel = currentDraft?.id != null &&
            (currentDraft.note.isNotBlank() || currentDraft.tagInput.isNotBlank())
    }
    BackHandler {
        if (state.draft != null) {
            clearPageSelection()
            viewModel.dismissDraft()
        } else {
            viewModel.navigateTo(AppScreen.LIBRARY)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                if (state.draft != null) {
                    clearPageSelection()
                    viewModel.dismissDraft()
                } else {
                    viewModel.navigateTo(AppScreen.LIBRARY)
                }
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("网页地址") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = { submitAddress() },
                    onDone = { submitAddress() },
                ),
            )
            IconButton(onClick = submitAddress) {
                Icon(Icons.Default.OpenInNew, contentDescription = "打开")
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.browserTabs.forEach { tab ->
                Surface(
                    modifier = Modifier
                        .widthIn(min = 128.dp, max = 190.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .clickable { viewModel.switchBrowserTab(tab.id) },
                    color = if (tab.id == state.activeBrowserTabId) Color(0xFFE8F0F8) else Paper,
                    border = BorderStroke(1.dp, if (tab.id == state.activeBrowserTabId) MaterialTheme.colorScheme.primary else Color(0xFFE6DED1)),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tab.title.ifBlank { tab.url.hostForDisplay() },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            color = Ink,
                        )
                        if (state.browserTabs.size > 1) {
                            IconButton(
                                onClick = { viewModel.closeBrowserTab(tab.id) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "关闭标签页", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            IconButton(onClick = viewModel::addBrowserTab, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, contentDescription = "新建标签页")
            }
        }
        if (state.status.isNotBlank()) {
            Text(
                state.status,
                color = if (state.statusIsError) Color(0xFFB3261E) else Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxSize()) {
            state.browserTabs.forEach { tab ->
                key(tab.id) {
                    WebPageView(
                        tabId = tab.id,
                        isActive = tab.id == state.activeBrowserTabId,
                        url = tab.url,
                        records = state.records.filter { it.normalizedUrl == com.glownote.mobile.data.normalizeUrl(tab.url) },
                        onReady = {},
                        clearSelectionRequest = clearSelectionRequest,
                        readerModeRequest = readerModeRequest,
                        readerSettings = state.settings.reader,
                        goBackRequest = 0,
                        scrollToHighlightRequest = state.pendingHighlightJump,
                        onLoaded = { loadedTabId, url, title ->
                            if (loadedTabId == state.activeBrowserTabId) address = url
                            viewModel.onPageLoaded(loadedTabId, url, title)
                        },
                        onSelection = viewModel::onSelection,
                        onHighlightClick = viewModel::onHighlightClicked,
                        onReaderModeChanged = { changedTabId, enabled ->
                            if (changedTabId == state.activeBrowserTabId) {
                                viewModel.setReaderMode(changedTabId, enabled)
                            }
                        },
                        onReaderSettingsChanged = { changedTabId, settings ->
                            if (changedTabId == state.activeBrowserTabId) {
                                viewModel.saveReaderSettings(settings)
                            }
                        },
                        onPageTap = {
                            clearPageSelection()
                            viewModel.dismissDraft()
                        },
                        onOpenNewWindow = viewModel::openBrowserTab,
                        onNavigationStateChanged = { _, _ -> },
                    )
                }
            }
            state.draft?.let { draft ->
                if (showAnnotationPanel) {
                    AnnotationPanel(
                        draft = draft,
                        onSave = { color, note, tagInput ->
                            clearPageSelection()
                            viewModel.saveDraft(color, note, tagInput)
                        },
                        onDelete = if (draft.id == null) null else {
                            {
                                clearPageSelection()
                                viewModel.deleteDraft()
                            }
                        },
                        onCopy = { copyHighlightText(context, draft.selectedText) },
                    )
                } else {
                    AnnotationToolbar(
                        selectionTop = draft.selectionTop,
                        selectionBottom = draft.selectionBottom,
                        selectedColor = draft.id?.let { draft.color },
                        onHighlight = { color ->
                            clearPageSelection()
                            viewModel.saveDraft(color, draft.note, draft.tagInput)
                        },
                        onAnnotate = { showAnnotationPanel = true },
                        onDelete = if (draft.id == null) null else {
                            {
                                clearPageSelection()
                                viewModel.deleteDraft()
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun captureWebPreview(view: WebView): Bitmap? {
    val sourceWidth = view.width
    val sourceHeight = view.height
    if (!view.isAttachedToWindow || sourceWidth <= 0 || sourceHeight <= 0 || view.url.isNullOrBlank()) {
        return null
    }
    val scale = TAB_PREVIEW_WIDTH_PX.toFloat() / sourceWidth.toFloat()
    val capturedSourceHeight = minOf(
        sourceHeight,
        (TAB_PREVIEW_HEIGHT_PX.toFloat() / scale).roundToInt(),
    )
    val targetHeight = (capturedSourceHeight * scale).roundToInt().coerceAtLeast(1)
    return runCatching {
        Bitmap.createBitmap(
            TAB_PREVIEW_WIDTH_PX,
            targetHeight,
            Bitmap.Config.ARGB_8888,
        ).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFFFFFFFF.toInt())
            canvas.save()
            canvas.scale(scale, scale)
            canvas.clipRect(0f, 0f, sourceWidth.toFloat(), capturedSourceHeight.toFloat())
            view.draw(canvas)
            canvas.restore()
        }
    }.getOrNull()
}

@Composable
private fun WebPageView(
    tabId: String,
    isActive: Boolean,
    url: String,
    records: List<HighlightRecord>,
    onReady: (WebView) -> Unit,
    clearSelectionRequest: Int,
    readerModeRequest: Int,
    readerSettings: ReaderSettings = ReaderSettings(),
    goBackRequest: Int,
    scrollToHighlightRequest: HighlightJumpRequest?,
    onLoaded: (String, String, String) -> Unit,
    onSelection: (String, PageSelection) -> Unit,
    onHighlightClick: (String, PageHighlightClick) -> Unit,
    onReaderModeChanged: (String, Boolean) -> Unit,
    onReaderSettingsChanged: (String, ReaderSettings) -> Unit,
    onPageTap: () -> Unit,
    onOpenNewWindow: (String) -> Unit,
    onNavigationStateChanged: (String, Boolean) -> Unit,
    onPreviewCaptured: (String, Bitmap) -> Unit = { _, _ -> },
) {
    val latestSelection = rememberUpdatedState(onSelection)
    val latestHighlightClick = rememberUpdatedState(onHighlightClick)
    val latestPageTap = rememberUpdatedState(onPageTap)
    val latestLoaded = rememberUpdatedState(onLoaded)
    val latestRecords = rememberUpdatedState(records)
    val latestReaderSettings = rememberUpdatedState(readerSettings)
    val latestScrollToHighlightRequest = rememberUpdatedState(scrollToHighlightRequest)
    val latestReaderModeChanged = rememberUpdatedState(onReaderModeChanged)
    val latestReaderSettingsChanged = rememberUpdatedState(onReaderSettingsChanged)
    val latestOpenNewWindow = rememberUpdatedState(onOpenNewWindow)
    val latestNavigationStateChanged = rememberUpdatedState(onNavigationStateChanged)
    val latestPreviewCaptured = rememberUpdatedState(onPreviewCaptured)
    var lastClearSelectionRequest by remember { mutableStateOf(clearSelectionRequest) }
    var lastReaderModeRequest by remember { mutableStateOf(readerModeRequest) }
    var lastGoBackRequest by remember { mutableStateOf(goBackRequest) }
    var lastScrollToHighlightToken by remember { mutableStateOf<Long?>(null) }
    var lastAppliedRecords by remember { mutableStateOf<List<HighlightRecord>?>(null) }
    var lastAppliedReaderSettings by remember { mutableStateOf<ReaderSettings?>(null) }
    var pageNavigationInProgress by remember { mutableStateOf(false) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            AnnotationWebView(context).apply {
                visibility = if (isActive) View.VISIBLE else View.GONE
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                // Keep the layout viewport tied to the phone width. Overview
                // mode can asynchronously zoom the page back to a wide
                // desktop canvas after the first render.
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
                settings.textZoom = 100
                setInitialScale((resources.displayMetrics.density * 100f).toInt())
                val defaultUserAgent = WebSettings.getDefaultUserAgent(context)
                settings.userAgentString = if (defaultUserAgent.contains("Mobile", ignoreCase = true)) {
                    defaultUserAgent
                } else {
                    defaultUserAgent.replace("Safari/", "Mobile Safari/")
                }
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.setSupportMultipleWindows(true)
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message,
                    ): Boolean {
                        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                        val child = AnnotationWebView(view.context)
                        child.settings.javaScriptEnabled = true
                        child.settings.domStorageEnabled = true
                        child.settings.userAgentString = view.settings.userAgentString
                        child.settings.javaScriptCanOpenWindowsAutomatically = false
                        var handled = false

                        fun openNewWindow(rawUrl: String?) {
                            val safe = rawUrl?.let(::normalizeWebUrl) ?: return
                            if (handled) return
                            handled = true
                            latestOpenNewWindow.value(safe)
                            child.post {
                                runCatching {
                                    child.stopLoading()
                                    child.destroy()
                                }
                            }
                        }

                        child.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                childView: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                if (request.isForMainFrame) {
                                    openNewWindow(request.url.toString())
                                    return true
                                }
                                return false
                            }

                            override fun onPageStarted(
                                childView: WebView,
                                pageUrl: String,
                                favicon: Bitmap?,
                            ) {
                                super.onPageStarted(childView, pageUrl, favicon)
                                openNewWindow(pageUrl)
                            }
                        }
                        transport.webView = child
                        resultMsg.sendToTarget()
                        return true
                    }
                }
                setBackgroundColor(0xFFF7F3EC.toInt())
                isFocusableInTouchMode = true
                val nativeWebView = this
                var lastEditableHandoffPayload: String? = null
                var lastEditableHandoffAt = 0L
                addJavascriptInterface(
                    GlowNoteWebBridge(
                        onSelectionCallback = { latestSelection.value(tabId, it) },
                        onHighlightClickCallback = { latestHighlightClick.value(tabId, it) },
                        onReaderModeChangedCallback = { latestReaderModeChanged.value(tabId, it) },
                        onReaderSettingsChangedCallback = { latestReaderSettingsChanged.value(tabId, it) },
                        onPageTapCallback = { latestPageTap.value() },
                        onOpenNewWindowCallback = { latestOpenNewWindow.value(it) },
                        onEditableFocusCallback = { payload ->
                            if (!nativeWebView.isAttachedToWindow) return@GlowNoteWebBridge
                            val now = android.os.SystemClock.uptimeMillis()
                            if (payload == lastEditableHandoffPayload && now - lastEditableHandoffAt < 1000L) {
                                return@GlowNoteWebBridge
                            }
                            val rect = runCatching { JSONObject(payload) }.getOrNull() ?: return@GlowNoteWebBridge
                            val scale = nativeWebView.scale.takeIf { it > 0f } ?: 1f
                            val x = ((rect.optDouble("left") + rect.optDouble("width") / 2.0) * scale)
                                .toFloat()
                            val y = ((rect.optDouble("top") + rect.optDouble("height") / 2.0) * scale)
                                .toFloat()
                            if (x <= 0f || y <= 0f || x >= nativeWebView.width || y >= nativeWebView.height) {
                                return@GlowNoteWebBridge
                            }
                            lastEditableHandoffPayload = payload
                            lastEditableHandoffAt = now
                            nativeWebView.post {
                                if (!nativeWebView.isAttachedToWindow) return@post
                                val downTime = android.os.SystemClock.uptimeMillis()
                                val down = MotionEvent.obtain(
                                    downTime,
                                    downTime,
                                    MotionEvent.ACTION_DOWN,
                                    x,
                                    y,
                                    0,
                                ).also { it.source = InputDevice.SOURCE_TOUCHSCREEN }
                                val up = MotionEvent.obtain(
                                    downTime,
                                    downTime + 40L,
                                    MotionEvent.ACTION_UP,
                                    x,
                                    y,
                                    0,
                                ).also { it.source = InputDevice.SOURCE_TOUCHSCREEN }
                                nativeWebView.dispatchTouchEvent(down)
                                nativeWebView.dispatchTouchEvent(up)
                                down.recycle()
                                up.recycle()
                                nativeWebView.requestFocusFromTouch()
                                val inputMethodManager = nativeWebView.context
                                    .getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                inputMethodManager?.showSoftInput(
                                    nativeWebView,
                                    InputMethodManager.SHOW_IMPLICIT,
                                )
                            }
                        },
                    ),
                    "AndroidWebViewBridge",
                )
                webViewClient = object : WebViewClient() {
                    fun schedulePreviewCapture(view: WebView) {
                        view.postDelayed({
                            captureWebPreview(view)?.let { preview ->
                                latestPreviewCaptured.value(tabId, preview)
                            }
                        }, 280L)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        if (request.isForMainFrame) {
                            val deepLinkUrl = browserDeepLinkToWebUrl(request.url.toString())
                            if (deepLinkUrl != null) {
                                pageNavigationInProgress = true
                                view.loadUrl(deepLinkUrl)
                                return true
                            }
                        }
                        if (request.isForMainFrame) {
                            pageNavigationInProgress = true
                        }
                        return false
                    }

                    override fun onPageStarted(view: WebView, pageUrl: String, favicon: Bitmap?) {
                        super.onPageStarted(view, pageUrl, favicon)
                        pageNavigationInProgress = true
                        lastScrollToHighlightToken = null
                        latestNavigationStateChanged.value(tabId, view.canGoBack())
                        latestReaderModeChanged.value(tabId, false)
                        // Install the viewport before the page finishes its
                        // first layout. This matters for pages that omit a
                        // viewport tag: adding it only at onPageFinished is
                        // too late for their responsive breakpoints.
                        view.evaluateJavascript(AnnotationScript.bootstrap, null)
                    }

                    override fun onPageFinished(view: WebView, pageUrl: String) {
                        pageNavigationInProgress = false
                        // Some pages omit the viewport tag and WebView then
                        // uses a desktop-sized layout viewport. Add one
                        // before installing the annotation listeners.
                        view.evaluateJavascript(AnnotationScript.bootstrap, null)
                        view.evaluateJavascript(AnnotationScript.normalizeMobileNavigation, null)
                        val currentReaderSettings = latestReaderSettings.value
                        view.evaluateJavascript(
                            AnnotationScript.applyReaderSettings(currentReaderSettings),
                            null,
                        )
                        lastAppliedReaderSettings = currentReaderSettings
                        val currentRecords = latestRecords.value
                        val pendingJump = latestScrollToHighlightRequest.value
                        if (pendingJump != null) {
                            view.evaluateJavascript(
                                AnnotationScript.applyAndScroll(currentRecords, pendingJump.id),
                                null,
                            )
                            lastScrollToHighlightToken = pendingJump.token
                        } else {
                            view.evaluateJavascript(AnnotationScript.apply(currentRecords), null)
                        }
                        lastAppliedRecords = currentRecords
                        latestNavigationStateChanged.value(tabId, view.canGoBack())
                        latestLoaded.value(tabId, pageUrl, view.title.orEmpty())
                        schedulePreviewCapture(view)
                    }

                    override fun doUpdateVisitedHistory(
                        view: WebView,
                        pageUrl: String,
                        isReload: Boolean,
                    ) {
                        super.doUpdateVisitedHistory(view, pageUrl, isReload)
                        // GitHub's Turbo navigation and many modern sites use
                        // history.pushState without a full document load.
                        // WebViewClient.onPageFinished is not guaranteed for
                        // those transitions, so keep both the address bar and
                        // the back affordance in sync here as well.
                        latestNavigationStateChanged.value(tabId, view.canGoBack())
                        // This callback also fires when goBack() restores a
                        // history entry without starting a new document. Do
                        // not gate the URL update on pageNavigationInProgress;
                        // that flag is intentionally true while that restore
                        // is in flight.
                        latestLoaded.value(tabId, pageUrl, view.title.orEmpty())
                        schedulePreviewCapture(view)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) {
                            pageNavigationInProgress = false
                        }
                    }
                }
                onReady(this)
                pageNavigationInProgress = true
                loadUrl(url)
            }
        },
        update = { view ->
            view.visibility = if (isActive) View.VISIBLE else View.GONE
            if (isActive && clearSelectionRequest != lastClearSelectionRequest) {
                view.evaluateJavascript(AnnotationScript.clearSelection(), null)
                lastClearSelectionRequest = clearSelectionRequest
            }
            if (!isActive) {
                lastReaderModeRequest = readerModeRequest
            } else if (readerModeRequest != lastReaderModeRequest) {
                view.evaluateJavascript(AnnotationScript.toggleReaderMode, null)
                lastReaderModeRequest = readerModeRequest
            }
            if (isActive && goBackRequest != lastGoBackRequest) {
                if (view.canGoBack()) {
                    pageNavigationInProgress = true
                    view.goBack()
                }
                lastGoBackRequest = goBackRequest
            }
            latestNavigationStateChanged.value(tabId, view.canGoBack())
            val currentUrl = view.url.orEmpty()
            val currentWebUrl = normalizeWebUrl(currentUrl)
            val targetWebUrl = normalizeWebUrl(url)
            val samePage = currentUrl == url || (
                currentWebUrl != null &&
                    targetWebUrl != null &&
                    currentWebUrl == targetWebUrl
                )
            if (!pageNavigationInProgress) {
                if (currentUrl.isBlank() || currentUrl == "about:blank") {
                    pageNavigationInProgress = true
                    view.loadUrl(url)
                } else if (!samePage) {
                    pageNavigationInProgress = true
                    view.loadUrl(url)
                }
            }
            // Applying marks rewrites text nodes. Doing that on every Compose
            // recomposition destroys the live WebView selection immediately
            // after the JS bridge reports it (for example when the draft
            // toolbar is shown). Only touch the DOM when the inputs changed.
            if (readerSettings != lastAppliedReaderSettings) {
                view.evaluateJavascript(AnnotationScript.applyReaderSettings(readerSettings), null)
                lastAppliedReaderSettings = readerSettings
            }
            if (records != lastAppliedRecords) {
                view.evaluateJavascript(AnnotationScript.apply(records), null)
                lastAppliedRecords = records
            }
            val pendingJump = scrollToHighlightRequest
            if (isActive && pendingJump != null && pendingJump.token != lastScrollToHighlightToken) {
                view.evaluateJavascript(AnnotationScript.scrollToHighlight(pendingJump.id), null)
                lastScrollToHighlightToken = pendingJump.token
            }
        },
        onRelease = { view ->
            view.removeJavascriptInterface("AndroidWebViewBridge")
            view.stopLoading()
            view.destroy()
        },
    )
}

/**
 * WebView's native selection ActionMode owns the floating Copy/Share/Search
 * toolbar. GlowNote uses the DOM selection reported by JavaScript instead, so
 * the native toolbar must not compete with the in-app annotation sheet.
 */
@Suppress("DEPRECATION")
private class AnnotationWebView(context: Context) : WebView(context) {
    private companion object {
        // Android's floating ActionMode only supports a bounded hide duration.
        // Re-apply the hide before it expires while the WebView keeps the mode
        // alive for its selection handles and range.
        const val NATIVE_TOOLBAR_HIDE_MS = 3_000L
        const val NATIVE_TOOLBAR_REHIDE_MS = 2_500L
    }

    private val actionModeHandler = Handler(Looper.getMainLooper())
    private val actionModeHiders = IdentityHashMap<ActionMode, Runnable>()

    private fun hideNativeToolbar(mode: ActionMode?): ActionMode? {
        if (mode == null) return null
        mode.hide(NATIVE_TOOLBAR_HIDE_MS)
        if (!actionModeHiders.containsKey(mode)) {
            val hider = object : Runnable {
                override fun run() {
                    if (actionModeHiders[mode] !== this) return
                    mode.hide(NATIVE_TOOLBAR_HIDE_MS)
                    actionModeHandler.postDelayed(this, NATIVE_TOOLBAR_REHIDE_MS)
                }
            }
            actionModeHiders[mode] = hider
            actionModeHandler.postDelayed(hider, NATIVE_TOOLBAR_REHIDE_MS)
        }
        return mode
    }

    private fun releaseNativeToolbar(mode: ActionMode) {
        actionModeHiders.remove(mode)?.let(actionModeHandler::removeCallbacks)
    }

    private fun wrapActionModeCallback(callback: ActionMode.Callback): ActionMode.Callback {
        return object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val created = callback.onCreateActionMode(mode, menu)
                if (created) {
                    // Keep WebView's ActionMode alive for selection, but remove
                    // its Copy/Share/Search items from the visible menu.
                    menu.clear()
                    hideNativeToolbar(mode)
                }
                return created
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                val prepared = callback.onPrepareActionMode(mode, menu)
                menu.clear()
                hideNativeToolbar(mode)
                return prepared
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
                callback.onActionItemClicked(mode, item)

            override fun onDestroyActionMode(mode: ActionMode) {
                releaseNativeToolbar(mode)
                callback.onDestroyActionMode(mode)
            }

            override fun onGetContentRect(
                mode: ActionMode,
                view: View,
                outRect: Rect,
            ) {
                (callback as? ActionMode.Callback2)?.onGetContentRect(mode, view, outRect)
            }
        }
    }

    override fun startActionMode(callback: ActionMode.Callback): ActionMode? =
        hideNativeToolbar(super.startActionMode(wrapActionModeCallback(callback)))

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? =
        hideNativeToolbar(super.startActionMode(wrapActionModeCallback(callback), type))

    override fun startActionModeForChild(
        originalView: View,
        callback: ActionMode.Callback,
    ): ActionMode? = hideNativeToolbar(
        super.startActionModeForChild(originalView, wrapActionModeCallback(callback)),
    )

    override fun startActionModeForChild(
        originalView: View,
        callback: ActionMode.Callback,
        type: Int,
    ): ActionMode? = hideNativeToolbar(
        super.startActionModeForChild(originalView, wrapActionModeCallback(callback), type),
    )
}

private class GlowNoteWebBridge(
    private val onSelectionCallback: (PageSelection) -> Unit,
    private val onHighlightClickCallback: (PageHighlightClick) -> Unit,
    private val onReaderModeChangedCallback: (Boolean) -> Unit,
    private val onReaderSettingsChangedCallback: (ReaderSettings) -> Unit,
    private val onPageTapCallback: () -> Unit,
    private val onOpenNewWindowCallback: (String) -> Unit,
    private val onEditableFocusCallback: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastHighlightId: String = ""
    private var lastHighlightAt: Long = 0L
    private var lastNewWindowUrl: String = ""
    private var lastNewWindowAt: Long = 0L

    @JavascriptInterface
    fun onSelection(payload: String) {
        val selection = runCatching {
            glowJson.decodeFromString(PageSelection.serializer(), payload)
        }.getOrNull() ?: return
        mainHandler.post { onSelectionCallback(selection) }
    }

    @JavascriptInterface
    fun onHighlightClick(payload: String) {
        val click = runCatching {
            glowJson.decodeFromString(PageHighlightClick.serializer(), payload)
        }.getOrElse {
            PageHighlightClick(id = payload)
        }
        val id = click.id
        if (id.isBlank()) return
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (id == lastHighlightId && now - lastHighlightAt < 500L) return
            lastHighlightId = id
            lastHighlightAt = now
        }
        mainHandler.post { onHighlightClickCallback(click) }
    }

    @JavascriptInterface
    fun onReaderModeChanged(value: String) {
        mainHandler.post {
            onReaderModeChangedCallback(value.equals("true", ignoreCase = true))
        }
    }

    @JavascriptInterface
    fun onReaderSettingsChanged(payload: String) {
        val settings = runCatching {
            glowJson.decodeFromString(ReaderSettings.serializer(), payload)
        }.getOrNull() ?: return
        mainHandler.post { onReaderSettingsChangedCallback(settings) }
    }

    @JavascriptInterface
    fun onPageTap() {
        mainHandler.post { onPageTapCallback() }
    }

    @JavascriptInterface
    fun onOpenNewWindow(url: String) {
        val safeUrl = url.trim()
        if (safeUrl.isBlank()) return
        val now = System.currentTimeMillis()
        if (safeUrl == lastNewWindowUrl && now - lastNewWindowAt < 800L) return
        lastNewWindowUrl = safeUrl
        lastNewWindowAt = now
        mainHandler.post { onOpenNewWindowCallback(safeUrl) }
    }

    @JavascriptInterface
    fun onEditableFocus(payload: String) {
        mainHandler.post { onEditableFocusCallback(payload) }
    }
}

@Composable
private fun BoxScope.AnnotationToolbar(
    selectionTop: Float,
    selectionBottom: Float,
    selectedColor: String?,
    onHighlight: (String) -> Unit,
    onAnnotate: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var toolbarHeightPx by remember { mutableStateOf(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val measuredToolbarHeight = with(density) { toolbarHeightPx.toDp() }
        val toolbarHeight = if (measuredToolbarHeight > 0.dp) measuredToolbarHeight else 48.dp
        val minOffset = 8.dp
        val maxOffset = (maxHeight - toolbarHeight - 8.dp).coerceAtLeast(minOffset)
        val hasSelectionPosition = selectionTop > 0f || selectionBottom > 0f
        val selectionTopOffset = selectionTop.coerceAtLeast(0f).dp
        val selectionBottomOffset = selectionBottom.coerceAtLeast(0f).dp
        val belowOffset = selectionBottomOffset + 8.dp
        val aboveOffset = selectionTopOffset - toolbarHeight - 8.dp
        val toolbarOffset = if (!hasSelectionPosition) {
            minOffset
        } else if (belowOffset + toolbarHeight <= maxHeight) {
            belowOffset.coerceIn(minOffset, maxOffset)
        } else if (aboveOffset >= minOffset) {
            aboveOffset.coerceIn(minOffset, maxOffset)
        } else {
            belowOffset.coerceIn(minOffset, maxOffset)
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = toolbarOffset)
                .wrapContentWidth()
                .onSizeChanged { toolbarHeightPx = it.height },
            color = Paper,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HighlightColors.forEach { (name, color) ->
                    IconButton(
                        onClick = { onHighlight(name) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        val isSelected = selectedColor == name
                        Surface(
                            modifier = Modifier.size(if (isSelected) 24.dp else 22.dp),
                            shape = CircleShape,
                            color = color,
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Circle,
                                contentDescription = "${name}高亮",
                                tint = color,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                IconButton(onClick = onAnnotate, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.EditNote, contentDescription = "批注", tint = Ink, modifier = Modifier.size(22.dp))
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "删除高亮",
                            tint = Color(0xFFB3261E),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.AnnotationPanel(
    draft: AnnotationDraft,
    onSave: (String, String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onCopy: (() -> Unit)?,
) {
    var color by remember(draft.id, draft.selectedText) { mutableStateOf(draft.color) }
    var note by remember(draft.id, draft.selectedText) { mutableStateOf(draft.note) }
    var tagInput by remember(draft.id, draft.selectedText) { mutableStateOf(draft.tagInput) }
    var cardHeightPx by remember(draft.id, draft.selectedText) { mutableStateOf(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val measuredCardHeight = with(density) { cardHeightPx.toDp() }
        val cardHeight = if (measuredCardHeight > 0.dp) measuredCardHeight else 250.dp
        val minOffset = 8.dp
        val maxOffset = (maxHeight - cardHeight - 8.dp).coerceAtLeast(minOffset)
        val hasSelectionPosition = draft.selectionTop > 0f || draft.selectionBottom > 0f
        val selectionTop = draft.selectionTop.coerceAtLeast(0f).dp
        val selectionBottom = draft.selectionBottom.coerceAtLeast(0f).dp
        val belowOffset = selectionBottom + 8.dp
        val aboveOffset = selectionTop - cardHeight - 8.dp
        val cardOffset = if (!hasSelectionPosition) {
            maxOffset
        } else if (belowOffset + cardHeight <= maxHeight) {
            belowOffset.coerceIn(minOffset, maxOffset)
        } else if (aboveOffset >= minOffset) {
            aboveOffset.coerceIn(minOffset, maxOffset)
        } else {
            belowOffset.coerceIn(minOffset, maxOffset)
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .offset(y = cardOffset)
                .onSizeChanged { cardHeightPx = it.height }
                .navigationBarsPadding()
                .imePadding(),
            color = Paper,
            shadowElevation = 18.dp,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HighlightColors.forEach { (name, swatch) ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (color == name) 3.dp else 1.dp,
                                    color = if (color == name) MaterialTheme.colorScheme.primary else Color.White,
                                    shape = CircleShape,
                                )
                                .clickable { color = name },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    label = { Text("批注（可用 #标签）") },
                    placeholder = { Text("写下你为什么要留下这段话…") },
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("标签（逗号分隔）") },
                    placeholder = { Text("例如：agent, 重点") },
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onCopy != null) {
                        IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(19.dp))
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除", modifier = Modifier.size(19.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { onSave(color, note, tagInput) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "保存批注", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: GlowNoteUiState, viewModel: GlowNoteViewModel) {
    var enabled by remember(state.settings) { mutableStateOf(state.settings.webdav.enabled) }
    var url by remember(state.settings) { mutableStateOf(state.settings.webdav.url) }
    var username by remember(state.settings) { mutableStateOf(state.settings.webdav.username) }
    var password by remember(state.settings) { mutableStateOf(state.settings.webdav.password) }
    var path by remember(state.settings) { mutableStateOf(state.settings.webdav.path) }

    LaunchedEffect(Unit) {
        viewModel.checkForAppUpdate()
    }

    val draftSettings = {
        AppSettings(
            webdav = WebDavSettings(
                enabled = enabled,
                url = url.trim(),
                username = username,
                password = password,
                path = path.trim().ifBlank { "/highlight-extension/sync.json" },
            ),
            reader = state.settings.reader,
            lastSyncAt = state.settings.lastSyncAt,
            lastSyncError = state.settings.lastSyncError,
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("SETTINGS", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("同步与连接", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text("和桌面 GlowNote 共享文章、高亮与批注。密码会保存在 Android Keystore 加密区。", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Paper),
            border = BorderStroke(1.dp, Color(0xFFE6DED1)),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(17.dp)) {
                Text("应用更新", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(3.dp))
                Text("当前版本 v${BuildConfig.VERSION_NAME} · 更新源 GitHub Releases", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(13.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = viewModel::checkForAppUpdate,
                        enabled = !state.appUpdate.isChecking && !state.appUpdate.isDownloading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (state.appUpdate.isChecking) "检查中…" else "检查更新")
                    }
                    if (state.appUpdate.latest != null) {
                        Button(
                            onClick = viewModel::installAppUpdate,
                            enabled = !state.appUpdate.isDownloading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (state.appUpdate.isDownloading) "下载中…" else "安装更新")
                        }
                    }
                }
                state.appUpdate.latest?.let { latest ->
                    Spacer(Modifier.height(9.dp))
                    Text("发现 v${latest.versionName} · ${latest.assetName}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
                if (state.appUpdate.message.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        state.appUpdate.message,
                        color = if (state.appUpdate.statusIsError) Color(0xFFB3261E) else Muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(15.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Paper),
            border = BorderStroke(1.dp, Color(0xFFE6DED1)),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("WebDAV 同步", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("使用与 Chrome 扩展相同的 sync-v2.json", color = Muted, fontSize = 12.sp)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Spacer(Modifier.height(13.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://cloud.example.com/dav") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("同步文件路径") },
                    placeholder = { Text("/highlight-extension/sync.json") },
                )
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("用户名（可选）") },
                )
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("密码（可选）") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(7.dp))
                Text("扩展要求 HTTPS；Android 客户端也会按同一规则连接。服务器保存的是明文 JSON。", color = Color(0xFF8D652E), fontSize = 12.sp)
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { viewModel.testWebDav(draftSettings()) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("测试连接")
                    }
                    Button(onClick = { viewModel.saveSettings(draftSettings()) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("保存设置")
                    }
                }
            }
        }

        Spacer(Modifier.height(15.dp))
        if (state.settings.lastSyncAt.isNotBlank()) {
            Text("上次同步：${state.settings.lastSyncAt}", color = Muted, fontSize = 12.sp)
        }
        if (state.status.isNotBlank()) StatusLine(state.status, state.statusIsError)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = viewModel::syncNow, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (state.isSyncing) "同步中…" else "立即同步")
        }
        Spacer(Modifier.height(18.dp))
    }
}

private fun String.hostForDisplay(): String = runCatching {
    android.net.Uri.parse(this).host.orEmpty().ifBlank { this }
}.getOrDefault(this)
