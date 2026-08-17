package com.glownote.mobile.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

private const val MarkdownUrlAnnotation = "GlowNoteMarkdownUrl"
private val MarkdownLinkColor = Color(0xFF1E6FA8)
private val MarkdownQuoteBackground = Color(0xFFFFF8E6)
private val MarkdownQuoteBar = Color(0xFFF5E2A4)

private sealed interface MarkdownBlock

private data class MarkdownParagraph(val text: String) : MarkdownBlock

private data class MarkdownQuote(val lines: List<String>) : MarkdownBlock

private data class MarkdownListBlock(val lists: List<MarkdownList>) : MarkdownBlock

private object MarkdownBlankLine : MarkdownBlock

private data class MarkdownList(
    val ordered: Boolean,
    val items: List<MarkdownListItem>,
)

private data class MarkdownListItem(
    val content: String,
    val task: Boolean?,
    val children: List<MarkdownList>,
)

private data class ParsedListLine(
    val content: String,
    val indent: Int,
    val ordered: Boolean,
    val task: Boolean?,
)

private class MutableMarkdownList(val ordered: Boolean) {
    val items = mutableListOf<MutableMarkdownListItem>()
}

private class MutableMarkdownListItem(
    val content: String,
    val task: Boolean?,
) {
    val children = mutableListOf<MutableMarkdownList>()
}

private data class ListFrame(
    val indent: Int,
    val list: MutableMarkdownList,
    val container: MutableList<MutableMarkdownList>,
    var lastItem: MutableMarkdownListItem? = null,
)

private data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val link: Boolean = false,
)

private data class ParsedInlineLink(
    val label: String,
    val url: String,
    val end: Int,
)

private data class StandaloneImage(
    val alt: String,
    val url: String,
)

/**
 * Renders the same note-oriented Markdown subset used by the desktop plugin.
 * The editor deliberately stays a plain text field; this composable is only
 * used for saved note output.
 */
@Composable
internal fun MarkdownContent(
    value: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFF55431E),
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 20.sp,
) {
    if (value.isBlank()) return

    val blocks = remember(value) { parseMarkdownBlocks(value) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownParagraph -> {
                    val image = parseStandaloneImage(block.text)
                    if (image == null) {
                        MarkdownInlineText(
                            value = block.text,
                            textColor = textColor,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                        )
                    } else {
                        MarkdownImage(
                            image = image,
                            textColor = textColor,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                        )
                    }
                }

                is MarkdownQuote -> MarkdownQuoteBlock(
                    lines = block.lines,
                    textColor = textColor,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                )

                is MarkdownListBlock -> MarkdownLists(
                    lists = block.lists,
                    textColor = textColor,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                )

                MarkdownBlankLine -> Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun MarkdownInlineText(
    value: String,
    textColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(value, textColor) {
        buildInlineAnnotatedString(value, textColor)
    }
    ClickableText(
        text = annotated,
        modifier = modifier.fillMaxWidth(),
        style = TextStyle(
            color = textColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
        ),
        onClick = { offset ->
            annotated
                .getStringAnnotations(MarkdownUrlAnnotation, offset, offset)
                .firstOrNull()
                ?.item
                ?.let { url ->
                    runCatching { uriHandler.openUri(url) }
                }
        },
    )
}

@Composable
private fun MarkdownQuoteBlock(
    lines: List<String>,
    textColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MarkdownQuoteBackground,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(MarkdownQuoteBar),
            )
            Spacer(Modifier.width(9.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                lines.forEach { line ->
                    MarkdownInlineText(
                        value = line,
                        textColor = textColor,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownLists(
    lists: List<MarkdownList>,
    textColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    parentOrderedPath: List<Int> = emptyList(),
    depth: Int = 0,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (depth == 0) 0.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        lists.forEach { list ->
            list.items.forEachIndexed { index, item ->
                val orderedPath = if (list.ordered) {
                    parentOrderedPath + (index + 1)
                } else {
                    parentOrderedPath
                }
                val marker = when {
                    item.task == true -> "✓"
                    item.task == false -> "□"
                    list.ordered -> orderedPath.joinToString(".") + if (orderedPath.size == 1) "." else ""
                    else -> "•"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = marker,
                        color = if (item.task != null) MarkdownLinkColor else textColor,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        modifier = Modifier.widthIn(min = if (list.ordered) 28.dp else 20.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        MarkdownInlineText(
                            value = item.content,
                            textColor = textColor,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                        )
                        if (item.children.isNotEmpty()) {
                            MarkdownLists(
                                lists = item.children,
                                textColor = textColor,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                parentOrderedPath = orderedPath,
                                depth = depth + 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownImage(
    image: StandaloneImage,
    textColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, image.url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                java.net.URL(image.url).openStream().use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
    }
    if (bitmap == null) {
        MarkdownInlineText(
            value = image.alt.ifBlank { image.url },
            textColor = textColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
        )
    } else {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = image.alt.ifBlank { "图片" },
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}

private fun buildInlineAnnotatedString(value: String, textColor: Color): AnnotatedString =
    buildAnnotatedString {
        appendInlineMarkdown(this, value, InlineStyle(), textColor)
    }

private fun appendInlineMarkdown(
    builder: AnnotatedString.Builder,
    source: String,
    style: InlineStyle,
    textColor: Color,
) {
    var index = 0
    while (index < source.length) {
        if (source[index] == '\\' && index + 1 < source.length) {
            appendStyled(builder, source[index + 1].toString(), style, textColor)
            index += 2
            continue
        }

        val image = parseInlineLink(source, index, image = true)
        if (image != null) {
            appendLinkedText(
                builder = builder,
                value = image.label.ifBlank { "图片" },
                url = image.url,
                style = style,
                textColor = textColor,
                prefix = "图片：",
            )
            index = image.end
            continue
        }

        val link = parseInlineLink(source, index, image = false)
        if (link != null) {
            appendLinkedText(
                builder = builder,
                value = link.label,
                url = link.url,
                style = style,
                textColor = textColor,
            )
            index = link.end
            continue
        }

        val marker = INLINE_MARKERS.firstOrNull { candidate ->
            source.startsWith(candidate, index) && canOpenMarker(source, index, candidate)
        }
        if (marker != null) {
            val closing = findClosingMarker(source, marker, index + marker.length)
            if (closing > index + marker.length) {
                val nextStyle = when (marker) {
                    "~~" -> style.copy(strike = !style.strike)
                    "**", "__" -> style.copy(bold = !style.bold)
                    else -> style.copy(italic = !style.italic)
                }
                appendInlineMarkdown(
                    builder = builder,
                    source = source.substring(index + marker.length, closing),
                    style = nextStyle,
                    textColor = textColor,
                )
                index = closing + marker.length
                continue
            }
        }

        appendStyled(builder, source[index].toString(), style, textColor)
        index += 1
    }
}

private fun appendLinkedText(
    builder: AnnotatedString.Builder,
    value: String,
    url: String,
    style: InlineStyle,
    textColor: Color,
    prefix: String = "",
) {
    builder.pushStringAnnotation(MarkdownUrlAnnotation, url)
    appendStyled(
        builder = builder,
        value = prefix + value,
        style = style.copy(link = true),
        textColor = textColor,
    )
    builder.pop()
}

private fun appendStyled(
    builder: AnnotatedString.Builder,
    value: String,
    style: InlineStyle,
    textColor: Color,
) {
    val decoration = when {
        style.strike && style.link -> TextDecoration.combine(
            listOf(TextDecoration.LineThrough, TextDecoration.Underline),
        )
        style.strike -> TextDecoration.LineThrough
        style.link -> TextDecoration.Underline
        else -> null
    }
    builder.withStyle(
        SpanStyle(
            color = if (style.link) MarkdownLinkColor else textColor,
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = decoration,
        ),
    ) {
        append(value)
    }
}

private fun parseMarkdownBlocks(value: String): List<MarkdownBlock> {
    val lines = value.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0
    while (index < lines.size) {
        val listLine = parseListLine(lines[index])
        if (listLine != null) {
            val items = mutableListOf<ParsedListLine>()
            while (index < lines.size) {
                val parsed = parseListLine(lines[index]) ?: break
                items += parsed
                index += 1
            }
            blocks += MarkdownListBlock(buildListTree(items))
            continue
        }

        val quote = QUOTE_PATTERN.matchEntire(lines[index])
        if (quote != null) {
            val quotedLines = mutableListOf<String>()
            while (index < lines.size) {
                val parsed = QUOTE_PATTERN.matchEntire(lines[index]) ?: break
                quotedLines += parsed.groupValues[1]
                index += 1
            }
            blocks += MarkdownQuote(quotedLines)
            continue
        }

        if (lines[index].isBlank()) {
            blocks += MarkdownBlankLine
        } else {
            blocks += MarkdownParagraph(lines[index])
        }
        index += 1
    }
    return blocks
}

private fun buildListTree(items: List<ParsedListLine>): List<MarkdownList> {
    val root = mutableListOf<MutableMarkdownList>()
    val stack = mutableListOf<ListFrame>()

    fun pushList(parsed: ParsedListLine, container: MutableList<MutableMarkdownList>): ListFrame {
        val list = MutableMarkdownList(parsed.ordered)
        container += list
        return ListFrame(parsed.indent, list, container).also(stack::add)
    }

    items.forEach { parsed ->
        while (stack.isNotEmpty() && parsed.indent < stack.last().indent) {
            stack.removeAt(stack.lastIndex)
        }
        var frame = stack.lastOrNull()
        if (frame == null) {
            frame = pushList(parsed, root)
        } else if (parsed.indent > frame.indent) {
            frame = pushList(parsed, frame.lastItem?.children ?: frame.container)
        } else if (frame.list.ordered != parsed.ordered) {
            stack.removeAt(stack.lastIndex)
            frame = pushList(parsed, frame.container)
        }
        val item = MutableMarkdownListItem(parsed.content, parsed.task)
        frame.list.items += item
        frame.lastItem = item
    }

    return root.map(::freezeList)
}

private fun freezeList(list: MutableMarkdownList): MarkdownList = MarkdownList(
    ordered = list.ordered,
    items = list.items.map { item ->
        MarkdownListItem(
            content = item.content,
            task = item.task,
            children = item.children.map(::freezeList),
        )
    },
)

private fun parseListLine(line: String): ParsedListLine? {
    val match = LIST_PATTERN.matchEntire(line) ?: return null
    val marker = match.groupValues[2]
    val ordered = marker.first().isDigit()
    var content = match.groupValues[3]
    var task: Boolean? = null
    if (!ordered) {
        val taskMatch = TASK_PATTERN.matchEntire(content)
        if (taskMatch != null) {
            task = taskMatch.groupValues[1].equals("x", ignoreCase = true)
            content = taskMatch.groupValues[2]
        }
    }
    return ParsedListLine(
        content = content,
        indent = match.groupValues[1].replace("\t", "    ").length,
        ordered = ordered,
        task = task,
    )
}

private fun parseStandaloneImage(value: String): StandaloneImage? {
    val parsed = parseInlineLink(value, 0, image = true) ?: return null
    return if (parsed.end == value.length) {
        StandaloneImage(parsed.label, parsed.url)
    } else {
        null
    }
}

private fun parseInlineLink(source: String, start: Int, image: Boolean): ParsedInlineLink? {
    val prefixLength = if (image) 2 else 1
    if (start + prefixLength > source.length) return null
    if (image && !source.startsWith("![", start)) return null
    if (!image && source[start] != '[') return null
    val labelStart = start + prefixLength
    val closingLabel = source.indexOf(']', labelStart)
    if (closingLabel <= labelStart || closingLabel + 1 >= source.length || source[closingLabel + 1] != '(') return null
    val closingDestination = findClosingParenthesis(source, closingLabel + 1)
    if (closingDestination <= closingLabel + 2) return null
    val rawDestination = source.substring(closingLabel + 2, closingDestination).trim()
    val destination = if (rawDestination.startsWith('<') && rawDestination.endsWith('>')) {
        rawDestination.substring(1, rawDestination.length - 1).trim()
    } else {
        rawDestination.takeWhile { !it.isWhitespace() }
    }
    val safeUrl = safeMarkdownUrl(destination, image) ?: return null
    return ParsedInlineLink(
        label = source.substring(labelStart, closingLabel),
        url = safeUrl,
        end = closingDestination + 1,
    )
}

private fun findClosingParenthesis(source: String, start: Int): Int {
    var depth = 0
    var index = start
    while (index < source.length) {
        if (!isEscaped(source, index)) {
            when (source[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        index += 1
    }
    return -1
}

private fun safeMarkdownUrl(value: String, image: Boolean): String? {
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    val allowed = if (image) setOf("http", "https") else setOf("http", "https", "mailto")
    return value.trim().takeIf { scheme in allowed }
}

private fun isEscaped(source: String, index: Int): Boolean {
    var slashCount = 0
    var cursor = index - 1
    while (cursor >= 0 && source[cursor] == '\\') {
        slashCount += 1
        cursor -= 1
    }
    return slashCount % 2 == 1
}

private fun canOpenMarker(source: String, index: Int, marker: String): Boolean {
    if (marker != "_") return true
    val previous = source.getOrNull(index - 1) ?: return true
    return !previous.isLetterOrDigit() && previous != '_'
}

private fun canCloseMarker(source: String, index: Int, marker: String): Boolean {
    if (marker != "_") return true
    val next = source.getOrNull(index + 1) ?: return true
    return !next.isLetterOrDigit() && next != '_'
}

private fun findClosingMarker(source: String, marker: String, start: Int): Int {
    var index = start
    while (index <= source.length - marker.length) {
        if (source.startsWith(marker, index) && !isEscaped(source, index) && canCloseMarker(source, index, marker)) {
            return index
        }
        index += 1
    }
    return -1
}

private val INLINE_MARKERS = listOf("~~", "**", "__", "*", "_")
private val LIST_PATTERN = Regex("^([ \\t]*)([-*+]|\\d+[.)])\\s+(.+)$")
private val TASK_PATTERN = Regex("^\\[([ xX])\\]\\s+(.+)$")
private val QUOTE_PATTERN = Regex("^\\s*>\\s?(.*)$")
