package com.glownote.mobile.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun noteTagsAreExtractedWithoutDuplicates() {
        assertEquals(listOf("阅读", "idea"), extractTags("先记录 #阅读，再记一个 #idea，最后重复 #阅读"))
    }

    @Test
    fun tagsKeepExistingValuesAndSplitCommaSeparatedInput() {
        assertEquals(listOf("one", "two", "three"), normalizeTags(listOf("one,two", "two", " three ")))
    }

    @Test
    fun syncPatchPreservesExtensionSpecificFields() {
        val original = buildJsonObject {
            put("webdavDirty", false)
            put("notionPageId", "page-123")
            put("notionTemplateVersion", 18)
        }
        val patched = withSync(original, "webdavDirty" to JsonPrimitive(true))
        assertTrue(patched["webdavDirty"]!!.toString() == "true")
        assertEquals("page-123", syncString(patched, "notionPageId"))
        assertEquals("18", patched["notionTemplateVersion"]!!.toString())
    }

    @Test
    fun recordsRoundTripWithUnknownSyncFields() {
        val record = HighlightRecord(
            id = "record-1",
            url = "https://example.com/read",
            normalizedUrl = "https://example.com/read",
            selectedText = "A sentence",
            note = "#idea",
            noteTags = listOf("idea"),
            tags = listOf("idea"),
            sync = buildJsonObject {
                put("webdavDirty", false)
                put("notionBlockIds", buildJsonObject { put("intro", "block-1") })
            },
        )
        val encoded = glowJson.encodeToString(ListSerializer(HighlightRecord.serializer()), listOf(record))
        val decoded = glowJson.decodeFromString(ListSerializer(HighlightRecord.serializer()), encoded).single()
        assertEquals("record-1", decoded.id)
        assertEquals("block-1", decoded.sync["notionBlockIds"]!!.jsonObject["intro"]!!.toString().trim('"'))
        assertFalse(syncBoolean(decoded.sync, "webdavDirty", true))
    }

    @Test
    fun readerSettingsRoundTripAndLegacyDefaults() {
        val settings = AppSettings(
            reader = ReaderSettings(
                fontSizeSp = 25,
                lineSpacing = 2.1f,
                pageSpacingDp = 44,
                background = "blue",
            ),
        )
        val decoded = glowJson.decodeFromString(
            AppSettings.serializer(),
            glowJson.encodeToString(AppSettings.serializer(), settings),
        )
        assertEquals(settings.reader, decoded.reader)

        val legacy = glowJson.decodeFromString(
            AppSettings.serializer(),
            """{"webdav":{"enabled":false}}""",
        )
        assertEquals(ReaderSettings(), legacy.reader)
        assertEquals(SearchEngine.GOOGLE.id, legacy.searchEngine)
    }

    @Test
    fun searchEnginesBuildExpectedUrls() {
        assertEquals(
            "https://www.google.com/search?q=claude%20code",
            SearchEngine.GOOGLE.buildSearchUrl("claude code"),
        )
        assertEquals(
            "https://duckduckgo.com/?q=claude%20code",
            SearchEngine.DUCKDUCKGO.buildSearchUrl("claude code"),
        )
        assertEquals(
            "https://www.bing.com/search?q=claude%20code",
            SearchEngine.BING.buildSearchUrl("claude code"),
        )
        assertEquals(
            "https://www.baidu.com/s?wd=claude%20code",
            SearchEngine.BAIDU.buildSearchUrl("claude code"),
        )
    }

    @Test
    fun webNavigationPreservesSignedWechatArticleParameters() {
        val url = "https://mp.weixin.qq.com/s?__biz=MjM5ODYwMjI2MA==&mid=2649803028&idx=1&sn=ec45ae12b64c5baf8a27a4f1121abe5d&chksm=bfa565e60fa0f18f3a4134ac516081013430a3fdf1d7234f217eea8c64adacbc2ab64318c718&mpshare=1&scene=1&srcid=0805inr9lyPCm8Xn7osUUS8x"

        val navigable = navigationUrlWithoutFragment("$url#section")
        assertEquals(url, navigable)
        assertTrue(navigable.contains("__biz=MjM5ODYwMjI2MA=="))
        assertTrue(navigable.contains("sn=ec45ae12b64c5baf8a27a4f1121abe5d"))
        assertTrue(navigable.contains("chksm=bfa565e60fa0f18f3a4134ac516081013430a3fdf1d7234f217eea8c64adacbc2ab64318c718"))
    }

    @Test
    fun readerSettingsAreClampedToReadableValues() {
        val sanitized = ReaderSettings(
            fontSizeSp = 100,
            lineSpacing = Float.NaN,
            pageSpacingDp = 0,
            background = "neon",
        ).sanitized()
        assertEquals(28, sanitized.fontSizeSp)
        assertEquals(1.8f, sanitized.lineSpacing)
        assertEquals(12, sanitized.pageSpacingDp)
        assertEquals("warm", sanitized.background)

        assertEquals("white", ReaderSettings(background = "almond").sanitized().background)
        assertEquals("dark", ReaderSettings(background = "gray").sanitized().background)
    }
}
