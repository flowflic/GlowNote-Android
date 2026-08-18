package com.glownote.mobile.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.glownote.mobile.data.HighlightRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

/** The six card directions selected from the numbered visual exploration. */
internal enum class ShareCardStyle(
    val number: String,
    val label: String,
) {
    EDITORIAL("1", "暖象牙文学"),
    JADE_VERTICAL("2", "薄荷竖页"),
    MIST_BLUE("4", "雾蓝日历"),
    BOTANICAL("7", "植物图书卡"),
    COLOR_BLOCK("8", "当代色块"),
    WASHI("9", "和纸静读"),
}

private const val SHARE_CARD_WIDTH = 1080
private const val CARD_SIDE = 96f
private const val CARD_LINE = 1f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareCardSheet(
    record: HighlightRecord,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedStyle by remember(record.id) { mutableStateOf(ShareCardStyle.EDITORIAL) }
    var isSharing by remember(record.id) { mutableStateOf(false) }
    var errorMessage by remember(record.id) { mutableStateOf("") }
    val preview by produceState<Bitmap?>(
        initialValue = null,
        record.id,
        record.title,
        record.selectedText,
        record.note,
        record.createdAt,
        selectedStyle,
    ) {
        value = withContext(Dispatchers.Default) {
            ShareCardRenderer.render(record, selectedStyle)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFFFFCF7),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
        ) {
            Text("分享摘录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "选择卡片风格，图片高度会随摘录和批注内容自动延伸",
                color = Color(0xFF6B6A66),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 250.dp, max = 430.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF1ECE5)),
                contentAlignment = Alignment.Center,
            ) {
                if (preview == null) {
                    Text("正在生成预览…", color = Color(0xFF6B6A66), fontSize = 13.sp)
                } else {
                    Image(
                        bitmap = preview!!.asImageBitmap(),
                        contentDescription = "摘录卡片预览",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShareCardStyle.values().forEach { style ->
                    OutlinedButton(
                        onClick = {
                            selectedStyle = style
                            errorMessage = ""
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (style == selectedStyle) 1.5.dp else 1.dp,
                            color = if (style == selectedStyle) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color(0xFFD6CEC4)
                            },
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 7.dp,
                        ),
                    ) {
                        Text(style.label, fontSize = 12.sp)
                    }
                }
            }
            if (errorMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(errorMessage, color = Color(0xFFB3261E), fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Divider(color = Color(0xFFE7DED2))
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (isSharing) return@Button
                    isSharing = true
                    errorMessage = ""
                    runCatching {
                        val uri = ShareCardRenderer.writeToCache(context, record, selectedStyle)
                        ShareCardRenderer.shareImage(context, uri)
                    }.onFailure { error ->
                        errorMessage = error.message ?: "分享卡片失败"
                        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                    isSharing = false
                },
                enabled = preview != null && !isSharing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isSharing) "正在准备…" else "分享这张卡片")
            }
        }
    }
}

internal object ShareCardRenderer {
    fun render(record: HighlightRecord, style: ShareCardStyle, width: Int = SHARE_CARD_WIDTH): Bitmap {
        val plan = buildPlan(record, style, width)
        val bitmap = Bitmap.createBitmap(plan.width, plan.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        when (style) {
            ShareCardStyle.EDITORIAL -> drawEditorial(canvas, plan)
            ShareCardStyle.JADE_VERTICAL -> drawJade(canvas, plan)
            ShareCardStyle.MIST_BLUE -> drawMistBlue(canvas, plan)
            ShareCardStyle.WASHI -> drawWashi(canvas, plan)
            ShareCardStyle.BOTANICAL -> drawBotanicalCard(canvas, plan)
            ShareCardStyle.COLOR_BLOCK -> drawColorBlock(canvas, plan)
        }
        return bitmap
    }

    fun writeToCache(context: Context, record: HighlightRecord, style: ShareCardStyle): android.net.Uri {
        val directory = File(context.cacheDir, "share").apply {
            if (!exists() && !mkdirs()) error("无法创建分享缓存目录")
        }
        val safeRecordId = record.id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48)
        val file = File(directory, "share-card-${safeRecordId}-${style.number}.png")
        file.outputStream().use { output ->
            render(record, style).compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareImage(context: Context, uri: android.net.Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("GlowNote 摘录卡片", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享摘录卡片"))
    }

    private data class CardPlan(
        val width: Int,
        val height: Int,
        val style: ShareCardStyle,
        val title: String,
        val titleLayout: StaticLayout,
        val titleX: Float,
        val titleY: Float,
        val date: String,
        val quote: StaticLayout,
        val quoteX: Float,
        val quoteY: Float,
        val note: StaticLayout?,
        val noteX: Float,
        val noteY: Float,
        val source: StaticLayout,
        val sourceX: Float,
        val sourceY: Float,
        val footer: StaticLayout,
        val footerX: Float,
        val footerY: Float,
    )

    private fun buildPlan(record: HighlightRecord, style: ShareCardStyle, width: Int): CardPlan {
        val title = record.title.trim().ifBlank { "GlowNote 摘录" }
        val date = formatDate(record.createdAt)
        val quoteText = record.selectedText.trim().ifBlank { "未填写摘录" }
        val noteText = record.note.trim().toPlainCardText()
        val side = if (style == ShareCardStyle.JADE_VERTICAL) 96f else CARD_SIDE
        val quoteX = when (style) {
            ShareCardStyle.JADE_VERTICAL -> 96f
            ShareCardStyle.WASHI -> 118f
            ShareCardStyle.COLOR_BLOCK -> 350f
            else -> side
        }
        val quoteWidth = when (style) {
            ShareCardStyle.JADE_VERTICAL -> width - 192
            ShareCardStyle.WASHI -> width - 236
            ShareCardStyle.COLOR_BLOCK -> width - 430
            else -> width - (side * 2).toInt()
        }.coerceAtLeast(280)
        val titleX = when (style) {
            ShareCardStyle.EDITORIAL -> 116f
            ShareCardStyle.JADE_VERTICAL -> 100f
            ShareCardStyle.WASHI -> 92f
            ShareCardStyle.COLOR_BLOCK -> 350f
            else -> side
        }
        val titleY = when (style) {
            ShareCardStyle.EDITORIAL -> 205f
            ShareCardStyle.JADE_VERTICAL -> 190f
            ShareCardStyle.WASHI -> 176f
            ShareCardStyle.COLOR_BLOCK -> 180f
            else -> 160f
        }
        val titleWidth = when (style) {
            ShareCardStyle.EDITORIAL -> width - 430
            ShareCardStyle.JADE_VERTICAL -> width - 192
            ShareCardStyle.WASHI -> width - 360
            ShareCardStyle.COLOR_BLOCK -> width - 430
            else -> width - (side * 2).toInt()
        }.coerceAtLeast(280)
        val titleLayout = layout(
            title,
            textPaint(
                color = when (style) {
                    ShareCardStyle.JADE_VERTICAL -> 0xFF164E32
                    ShareCardStyle.WASHI -> 0xFF2D2B28
                    ShareCardStyle.COLOR_BLOCK -> 0xFF314C9B
                    else -> 0xFF3C3027
                },
                size = when (style) {
                    ShareCardStyle.EDITORIAL -> 62f
                    ShareCardStyle.JADE_VERTICAL -> 52f
                    ShareCardStyle.WASHI -> 46f
                    ShareCardStyle.COLOR_BLOCK -> 32f
                    else -> 34f
                },
                typeface = Typeface.create("serif", Typeface.BOLD),
            ),
            titleWidth,
            Layout.Alignment.ALIGN_NORMAL,
            1.16f,
        )
        val baseQuoteY = when (style) {
            ShareCardStyle.EDITORIAL -> 410f
            ShareCardStyle.JADE_VERTICAL -> 470f
            ShareCardStyle.MIST_BLUE -> 430f
            ShareCardStyle.WASHI -> 650f
            ShareCardStyle.BOTANICAL -> 330f
            ShareCardStyle.COLOR_BLOCK -> 330f
        }
        val titleBottom = titleY + titleLayout.height
        val quoteY = max(
            baseQuoteY,
            titleBottom + when (style) {
                ShareCardStyle.EDITORIAL -> 62f
                ShareCardStyle.JADE_VERTICAL -> 64f
                ShareCardStyle.WASHI -> 72f
                ShareCardStyle.COLOR_BLOCK -> 58f
                else -> 48f
            },
        )
        val quotePaint = textPaint(
            color = when (style) {
                ShareCardStyle.JADE_VERTICAL, ShareCardStyle.BOTANICAL -> 0xFF123D2B
                ShareCardStyle.MIST_BLUE -> 0xFF263849
                ShareCardStyle.WASHI, ShareCardStyle.COLOR_BLOCK -> 0xFF292824
                else -> 0xFF382E27
            },
            size = when (style) {
                ShareCardStyle.JADE_VERTICAL -> 54f
                ShareCardStyle.MIST_BLUE -> 54f
                ShareCardStyle.WASHI -> 50f
                ShareCardStyle.BOTANICAL -> 56f
                ShareCardStyle.COLOR_BLOCK -> 58f
                else -> 60f
            },
            typeface = Typeface.create("serif", Typeface.NORMAL),
        )
        val quote = layout(quoteText, quotePaint, quoteWidth, Layout.Alignment.ALIGN_NORMAL, 1.52f)
        var flowY = quoteY + quote.height + 38f
        val note = if (noteText.isBlank()) {
            null
        } else {
            val noteLayout = layout(
                "我的批注\n$noteText",
                textPaint(
                    color = when (style) {
                        ShareCardStyle.MIST_BLUE -> 0xFF526B7C
                        ShareCardStyle.JADE_VERTICAL, ShareCardStyle.BOTANICAL -> 0xFF496A58
                        ShareCardStyle.WASHI -> 0xFF5A554D
                        else -> 0xFF6B5140
                    },
                    size = 31f,
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL),
                ),
                quoteWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1.35f,
            )
            flowY += 10f
            noteLayout
        }
        val noteY = if (note == null) 0f else flowY.also { flowY += note.height + 42f }
        val source = layout(
            "《$title》",
            textPaint(
                color = when (style) {
                    ShareCardStyle.MIST_BLUE -> 0xFF355D8B
                    ShareCardStyle.JADE_VERTICAL, ShareCardStyle.BOTANICAL -> 0xFF527C65
                    ShareCardStyle.WASHI -> 0xFF4A453E
                    ShareCardStyle.COLOR_BLOCK -> 0xFFB54F29
                    else -> 0xFF7D736C
                },
                size = 34f,
                typeface = Typeface.create("serif", Typeface.NORMAL),
            ),
            quoteWidth,
            Layout.Alignment.ALIGN_NORMAL,
            1.25f,
        )
        val sourceY = flowY
        flowY += source.height + 42f
        val footer = layout(
            "摘录于 $date  ·  GlowNote",
            textPaint(
                color = when (style) {
                    ShareCardStyle.MIST_BLUE -> 0xFF5F7892
                    ShareCardStyle.JADE_VERTICAL, ShareCardStyle.BOTANICAL -> 0xFF66816F
                    ShareCardStyle.WASHI -> 0xFF777168
                    ShareCardStyle.COLOR_BLOCK -> 0xFF3B4D91
                    else -> 0xFF8F8278
                },
                size = 27f,
                typeface = Typeface.create("sans-serif", Typeface.NORMAL),
            ),
            quoteWidth,
            Layout.Alignment.ALIGN_NORMAL,
            1.2f,
        )
        val footerX = when (style) {
            ShareCardStyle.JADE_VERTICAL -> 96f
            ShareCardStyle.WASHI -> 118f
            ShareCardStyle.COLOR_BLOCK -> 350f
            else -> side
        }
        val minHeight = when (style) {
            ShareCardStyle.JADE_VERTICAL, ShareCardStyle.MIST_BLUE -> 1060
            ShareCardStyle.WASHI -> 1040
            ShareCardStyle.BOTANICAL -> 1040
            ShareCardStyle.COLOR_BLOCK -> 980
            else -> 980
        }
        val height = max(minHeight.toFloat(), flowY + footer.height + 150f).toInt()
        return CardPlan(
            width = width,
            height = height,
            style = style,
            title = title,
            titleLayout = titleLayout,
            titleX = titleX,
            titleY = titleY,
            date = date,
            quote = quote,
            quoteX = quoteX,
            quoteY = quoteY,
            note = note,
            noteX = quoteX,
            noteY = noteY,
            source = source,
            sourceX = quoteX,
            sourceY = sourceY,
            footer = footer,
            footerX = footerX,
            footerY = height - footer.height - 62f,
        )
    }

    private fun drawEditorial(canvas: Canvas, plan: CardPlan) {
        val paper = fill(0xFFF8F4EC)
        canvas.drawColor(paper.color)
        val border = stroke(0xFFD6C9B9, 2f)
        canvas.drawRect(34f, 34f, plan.width - 34f, plan.height - 34f, border)
        canvas.drawRect(46f, 46f, plan.width - 46f, plan.height - 46f, stroke(0xFFE4D9CA, 1f))
        canvas.drawLine(116f, 170f, 210f, 170f, stroke(0xFFC66F45, 5f))
        drawLayout(canvas, plan.titleLayout, plan.titleX, plan.titleY)
        drawLayout(canvas, plan.quote, plan.quoteX, plan.quoteY)
        plan.note?.let {
            val notePaint = fill(0xFFFFF0DD)
            canvas.drawRoundRect(RectF(plan.noteX, plan.noteY - 18f, plan.noteX + it.width, plan.noteY + it.height + 22f), 16f, 16f, notePaint)
            drawLayout(canvas, it, plan.noteX + 22f, plan.noteY)
        }
        canvas.drawLine(plan.sourceX, plan.sourceY - 24f, plan.sourceX + 330f, plan.sourceY - 24f, stroke(0xFFC66F45, 3f))
        drawLayout(canvas, plan.source, plan.sourceX, plan.sourceY)
        drawLayout(canvas, plan.footer, plan.footerX, plan.footerY)
    }

    private fun drawJade(canvas: Canvas, plan: CardPlan) {
        canvas.drawColor(0xFFEAF4EE.toInt())
        canvas.drawRect(96f, 116f, 312f, 126f, fill(0xFF226A45))
        drawLayout(canvas, plan.titleLayout, plan.titleX, plan.titleY)
        drawText(
            canvas,
            "第一部",
            plan.titleX,
            plan.titleY + plan.titleLayout.height + 42f,
            textPaint(0xFF537A66, 28f, Typeface.create("serif", Typeface.NORMAL)),
        )
        drawLayout(canvas, plan.quote, plan.quoteX, plan.quoteY)
        plan.note?.let { drawLayout(canvas, it, plan.noteX, plan.noteY) }
        canvas.drawLine(plan.sourceX, plan.sourceY - 22f, plan.sourceX + 300f, plan.sourceY - 22f, stroke(0xFF9EBBAD, 2f))
        drawLayout(canvas, plan.source, plan.sourceX, plan.sourceY)
        canvas.drawLine(96f, plan.footerY - 34f, plan.width - 96f, plan.footerY - 34f, stroke(0xFF9EBBAD, 2f))
        drawLayout(canvas, plan.footer, plan.footerX, plan.footerY)
        val mountain = Path().apply {
            moveTo(600f, plan.height.toFloat())
            lineTo(730f, plan.height - 120f)
            lineTo(810f, plan.height - 72f)
            lineTo(932f, plan.height - 150f)
            lineTo(plan.width.toFloat(), plan.height - 90f)
            lineTo(plan.width.toFloat(), plan.height.toFloat())
            close()
        }
        canvas.drawPath(mountain, fill(0x1A5F9775))
    }

    private fun drawMistBlue(canvas: Canvas, plan: CardPlan) {
        canvas.drawColor(0xFFF1F6FB.toInt())
        drawCenteredLayout(canvas, plan.titleLayout, plan.width / 2f, 170f)
        drawCenteredText(canvas, "摘录", plan.width / 2f, 330f, textPaint(0xFF6C8299, 29f, Typeface.create("serif", Typeface.NORMAL)))
        canvas.drawLine(170f, 390f, 910f, 390f, stroke(0x6687A7C5, 2f))
        drawCenteredLayout(canvas, plan.quote, plan.width / 2f, plan.quoteY)
        plan.note?.let {
            canvas.drawLine(430f, plan.noteY - 20f, 650f, plan.noteY - 20f, stroke(0x6687A7C5, 2f))
            drawCenteredLayout(canvas, it, plan.width / 2f, plan.noteY)
        }
        drawCenteredLayout(canvas, plan.source, plan.width / 2f, plan.sourceY)
        drawSoftLandscape(canvas, plan)
        drawCenteredLayout(canvas, plan.footer, plan.width / 2f, plan.footerY)
    }

    private fun drawWashi(canvas: Canvas, plan: CardPlan) {
        canvas.drawColor(0xFFF3EFE5.toInt())
        canvas.drawRect(48f, 48f, plan.width - 48f, plan.height - 48f, stroke(0xFFD8D0C2, 2f))
        drawLayout(canvas, plan.titleLayout, plan.titleX, plan.titleY)
        drawText(
            canvas,
            "摘录于  GlowNote",
            plan.titleX,
            plan.titleY + plan.titleLayout.height + 48f,
            textPaint(0xFF5E5951, 26f, Typeface.create("serif", Typeface.NORMAL)),
        )
        val headerRuleY = plan.quoteY - 38f
        drawSeal(canvas, 146f, headerRuleY - 58f, 0xFFC85B4C)
        canvas.drawLine(116f, headerRuleY, plan.width - 116f, headerRuleY, stroke(0xFF9C958C, 2f))
        drawLayout(canvas, plan.quote, plan.quoteX, plan.quoteY)
        plan.note?.let {
            canvas.drawLine(plan.noteX, plan.noteY - 22f, plan.noteX + it.width, plan.noteY - 22f, stroke(0xFFB6AEA2, 2f))
            drawLayout(canvas, it, plan.noteX, plan.noteY)
        }
        canvas.drawLine(plan.sourceX, plan.sourceY - 24f, plan.width - 116f, plan.sourceY - 24f, stroke(0xFF9C958C, 2f))
        drawLayout(canvas, plan.source, plan.sourceX, plan.sourceY)
        canvas.drawLine(plan.sourceX, plan.footerY - 34f, plan.width - 116f, plan.footerY - 34f, stroke(0xFFB6AEA2, 2f))
        drawLayout(canvas, plan.footer, plan.footerX, plan.footerY)
    }

    private fun drawBotanicalCard(canvas: Canvas, plan: CardPlan) {
        canvas.drawColor(0xFFF0F3E9.toInt())
        canvas.drawRoundRect(RectF(54f, 54f, plan.width - 54f, plan.height - 54f), 32f, 32f, stroke(0xFF9DAD8C, 2f))
        drawText(canvas, "书摘卡片", 116f, 168f, textPaint(0xFF31523A, 29f, Typeface.create("serif", Typeface.NORMAL)))
        canvas.drawLine(116f, 208f, 160f, 208f, stroke(0xFF31523A, 3f))
        drawLayout(canvas, plan.quote, plan.quoteX, plan.quoteY)
        plan.note?.let {
            canvas.drawLine(plan.noteX, plan.noteY - 22f, plan.noteX + 320f, plan.noteY - 22f, stroke(0xFF9DAD8C, 2f))
            drawLayout(canvas, it, plan.noteX, plan.noteY)
        }
        drawLayout(canvas, plan.source, plan.sourceX, plan.sourceY)
        drawBotanicalLineArt(canvas, plan)
        canvas.drawLine(116f, plan.footerY - 34f, 520f, plan.footerY - 34f, stroke(0xFFB6C1A6, 2f))
        drawLayout(canvas, plan.footer, plan.footerX, plan.footerY)
    }

    private fun drawColorBlock(canvas: Canvas, plan: CardPlan) {
        canvas.drawColor(0xFFFFF8ED.toInt())
        canvas.drawRect(0f, 0f, 264f, plan.height.toFloat(), fill(0xFF314C9B))
        canvas.drawRoundRect(RectF(760f, 76f, 1000f, 220f), 18f, 18f, fill(0xFFE36D3D))
        drawText(canvas, "摘录", 818f, 166f, textPaint(0xFFFFF8ED, 38f, Typeface.create("sans-serif", Typeface.BOLD)))
        drawVerticalText(canvas, "摘录卡片", 100f, 210f, textPaint(0xFFFFF8ED, 40f, Typeface.create("sans-serif", Typeface.BOLD)))
        drawText(canvas, plan.title.take(12), 350f, 206f, textPaint(0xFF314C9B, 32f, Typeface.create("serif", Typeface.NORMAL)))
        drawLayout(canvas, plan.quote, plan.quoteX, plan.quoteY)
        plan.note?.let {
            canvas.drawRoundRect(RectF(plan.noteX, plan.noteY - 18f, plan.noteX + it.width + 34f, plan.noteY + it.height + 18f), 16f, 16f, fill(0xFFFFE5D5))
            drawLayout(canvas, it, plan.noteX + 16f, plan.noteY)
        }
        canvas.drawLine(plan.sourceX, plan.sourceY - 24f, plan.width - 96f, plan.sourceY - 24f, stroke(0xFFCFD1DE, 2f))
        drawLayout(canvas, plan.source, plan.sourceX, plan.sourceY)
        drawLayout(canvas, plan.footer, plan.footerX, plan.footerY)
    }

    private fun drawSoftLandscape(canvas: Canvas, plan: CardPlan) {
        val top = plan.height - 240f
        val far = Path().apply {
            moveTo(0f, top + 80f)
            cubicTo(190f, top - 20f, 320f, top + 120f, 490f, top + 48f)
            cubicTo(650f, top - 24f, 820f, top + 120f, plan.width.toFloat(), top + 20f)
            lineTo(plan.width.toFloat(), plan.height.toFloat())
            lineTo(0f, plan.height.toFloat())
            close()
        }
        canvas.drawPath(far, fill(0x265E82A6))
        val near = Path().apply {
            moveTo(0f, top + 156f)
            cubicTo(180f, top + 80f, 350f, top + 200f, 560f, top + 118f)
            cubicTo(760f, top + 56f, 890f, top + 180f, plan.width.toFloat(), top + 112f)
            lineTo(plan.width.toFloat(), plan.height.toFloat())
            lineTo(0f, plan.height.toFloat())
            close()
        }
        canvas.drawPath(near, fill(0x40506E91))
        canvas.drawLine(0f, plan.height - 64f, plan.width.toFloat(), plan.height - 64f, stroke(0x556C88A0, 2f))
    }

    private fun drawBotanicalLineArt(canvas: Canvas, plan: CardPlan) {
        val paint = stroke(0x66849B73, 3f)
        val baseX = plan.width - 250f
        val baseY = plan.height - 58f
        val stem = Path().apply {
            moveTo(baseX, baseY)
            cubicTo(baseX - 12f, baseY - 150f, baseX + 56f, baseY - 260f, baseX + 22f, baseY - 390f)
        }
        canvas.drawPath(stem, paint)
        for (index in 0..5) {
            val x = baseX + (index % 2) * 28f - 40f
            val y = baseY - 82f - index * 48f
            canvas.drawOval(RectF(x - 6f, y - 30f, x + 58f, y + 8f), paint)
            canvas.drawLine(x + 12f, y - 12f, baseX + 16f, y + 12f, paint)
        }
        canvas.drawCircle(baseX + 22f, baseY - 410f, 18f, paint)
    }

    private fun drawSeal(canvas: Canvas, x: Float, y: Float, color: Number) {
        val paint = stroke(color, 4f)
        canvas.drawCircle(x, y, 34f, paint)
        canvas.drawLine(x - 18f, y - 8f, x + 18f, y - 8f, paint)
        canvas.drawLine(x - 8f, y - 22f, x - 8f, y + 20f, paint)
        canvas.drawLine(x + 8f, y - 20f, x + 8f, y + 22f, paint)
    }

    private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawCenteredLayout(canvas: Canvas, layout: StaticLayout, centerX: Float, y: Float) {
        drawLayout(canvas, layout, centerX - layout.width / 2f, y)
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, baseline: Float, paint: Paint) {
        canvas.drawText(text, x, baseline, paint)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, centerX: Float, baseline: Float, paint: Paint) {
        canvas.drawText(text, centerX - paint.measureText(text) / 2f, baseline, paint)
    }

    private fun drawRightAlignedText(canvas: Canvas, text: String, right: Float, baseline: Float, paint: Paint) {
        canvas.drawText(text, right - paint.measureText(text), baseline, paint)
    }

    private fun drawVerticalText(canvas: Canvas, text: String, x: Float, top: Float, paint: Paint) {
        var y = top
        text.replace("\n", "").forEach { character ->
            canvas.drawText(character.toString(), x, y, paint)
            y += paint.textSize * 1.18f
        }
    }

    private fun layout(
        text: String,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment,
        multiplier: Float,
    ): StaticLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, width)
        .setAlignment(alignment)
        .setIncludePad(false)
        .setLineSpacing(0f, multiplier)
        .build()

    private fun textPaint(color: Number, size: Float, typeface: Typeface): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toInt()
        textSize = size
        this.typeface = typeface
        isSubpixelText = true
        isAntiAlias = true
    }

    private fun fill(color: Number): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt() }

    private fun stroke(color: Number, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toInt()
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun String.toPlainCardText(): String = replace(Regex("`([^`]*)`"), "$1")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("(^|\\s)[*_>#-]+"), "$1")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun formatDate(value: String): String {
        val parsed = runCatching { Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate() }
            .getOrElse { runCatching { LocalDate.parse(value.take(10)) }.getOrDefault(LocalDate.now()) }
        return parsed.format(DateTimeFormatter.ofPattern("yyyy / MM / dd"))
    }
}
