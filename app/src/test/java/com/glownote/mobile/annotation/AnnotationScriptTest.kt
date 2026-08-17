package com.glownote.mobile.annotation

import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationScriptTest {
    @Test
    fun readerModeRestoresTextSelection() {
        val script = AnnotationScript.toggleReaderMode

        assertTrue(script.contains("-webkit-user-select:text!important"))
        assertTrue(script.contains("user-select:text!important"))
        assertTrue(script.contains("html[data-glownote-reader] body"))
        assertTrue(script.contains("#glownote-reader-root *"))
    }

    @Test
    fun pageTapDoesNotClearLiveTextSelection() {
        val script = AnnotationScript.bootstrap

        assertTrue(script.contains("function hasLiveTextSelection()"))
        assertTrue(script.contains("if (hasLiveTextSelection()) return;"))
    }

    @Test
    fun readerModeTargetsWechatArticleContentAndDropsHiddenNodes() {
        val script = AnnotationScript.toggleReaderMode

        assertTrue(script.contains("#js_content"))
        assertTrue(script.contains(".rich_media_content"))
        assertTrue(script.contains("function hiddenInSource(element)"))
        assertTrue(script.contains("display\\s*:\\s*none"))
    }

    @Test
    fun readerModeOwnsItsSettingsMenu() {
        val script = AnnotationScript.toggleReaderMode

        assertTrue(script.contains("installReaderSettingsMenu(header)"))
        assertTrue(script.contains("data-glownote-reader-settings-button"))
        assertTrue(script.contains("data-glownote-reader-control=\"fontSizeSp\""))
        assertTrue(script.contains("data-glownote-reader-control=\"lineSpacing\""))
        assertTrue(script.contains("data-glownote-reader-control=\"pageSpacingDp\""))
        assertTrue(script.contains("onReaderSettingsChanged"))
    }

    @Test
    fun readerFontSizeDoesNotControlHeadings() {
        val script = AnnotationScript.toggleReaderMode

        assertTrue(script.contains("#glownote-reader-root h1{margin:0 0 8px!important;font-size:clamp(28px,9vw,54px)"))
        assertTrue(script.contains("#glownote-reader-content p,#glownote-reader-content li{margin:0 0 1.1em!important;font-size:var(--glownote-reader-font-size,18px)!important;line-height:var(--glownote-reader-line-height,1.8)"))
    }

    @Test
    fun readerFontSizeOverridesPageParagraphStyles() {
        val script = AnnotationScript.toggleReaderMode

        assertTrue(script.contains("#glownote-reader-content{font-size:var(--glownote-reader-font-size,18px)!important;"))
        assertTrue(script.contains("#glownote-reader-content p,#glownote-reader-content li{"))
        assertTrue(script.contains("font-size:var(--glownote-reader-font-size,18px)!important"))
    }
}
