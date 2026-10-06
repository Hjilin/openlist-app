package com.pocketnas.pro.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pocketnas.pro.core.DocRender

/**
 * 全屏文档查看器（对标主流阅读器）：
 * - docx：标题层级 + 加粗段落 + 表格 排版渲染
 * - xlsx：表格网格渲染
 * - pdf：整页宽渲染 + 翻页
 * - markdown / csv / text / code：滚动文本/排版渲染
 */
@Composable
fun DocViewerDialog(
    name: String,
    kind: String,
    docxBlocks: List<DocRender.DocxBlock>? = null,
    xlsxRows: List<List<String>>? = null,
    pdfFile: java.io.File? = null,
    markdownText: String? = null,
    plainText: String? = null,
    onClose: () -> Unit,
    onDownload: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var pdfPage by remember { mutableStateOf(0) }
        var pdfPageCount by remember { mutableStateOf(0) }
        var pdfBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        val isPdf = kind == "pdf" || (pdfFile != null)
        if (isPdf && pdfBitmap == null) {
            LaunchedEffect(pdfFile) {
                val f = pdfFile
                if (f != null) {
                    val r = DocRender.openPdf(f)
                    if (r != null) {
                        pdfPageCount = r.pageCount
                        pdfBitmap = DocRender.renderPdfPage(r, 0)
                        r.close()
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onClose) { Text("✕") }
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onDownload) { Text("下载") }
                }

                // 内容区
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isPdf -> {
                            val bmp = pdfBitmap
                            if (bmp != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (pdfPageCount > 1) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            TextButton(
                                                enabled = pdfPage > 0,
                                                onClick = {
                                                    val f = pdfFile ?: return@TextButton
                                                    val r = DocRender.openPdf(f) ?: return@TextButton
                                                    pdfPage--
                                                    pdfBitmap = DocRender.renderPdfPage(r, pdfPage)
                                                    r.close()
                                                },
                                            ) { Text("上一页") }
                                            Text("${pdfPage + 1} / $pdfPageCount", style = MaterialTheme.typography.labelLarge)
                                            TextButton(
                                                enabled = pdfPage < pdfPageCount - 1,
                                                onClick = {
                                                    val f = pdfFile ?: return@TextButton
                                                    val r = DocRender.openPdf(f) ?: return@TextButton
                                                    pdfPage++
                                                    pdfBitmap = DocRender.renderPdfPage(r, pdfPage)
                                                    r.close()
                                                },
                                            ) { Text("下一页") }
                                        }
                                    }
                                    Spacer(Modifier.height(24.dp))
                                }
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }
                        docxBlocks != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                            ) {
                                docxBlocks.forEach { block ->
                                    when (block) {
                                        is DocRender.DocxBlock.Para -> {
                                            val p = block.para
                                            val style = when (p.style) {
                                                "h1" -> MaterialTheme.typography.headlineSmall
                                                "h2" -> MaterialTheme.typography.titleLarge
                                                "h3" -> MaterialTheme.typography.titleMedium
                                                else -> MaterialTheme.typography.bodyLarge
                                            }
                                            Text(
                                                buildAnnotatedPara(p),
                                                style = style,
                                                modifier = Modifier.padding(vertical = 4.dp),
                                            )
                                        }
                                        is DocRender.DocxBlock.Table -> {
                                            DocTableGrid(block.rows)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                        xlsxRows != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                            ) {
                                xlsxRows.forEachIndexed { idx, row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (idx == 0) MaterialTheme.colorScheme.primaryContainer
                                                else if (idx % 2 == 0) MaterialTheme.colorScheme.surface
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                    ) {
                                        row.forEach { cell ->
                                            Text(
                                                cell,
                                                style = if (idx == 0) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                                else MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 3,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                        markdownText != null -> {
                            val blocks = remember(markdownText) { DocRender.parseMarkdown(markdownText) }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                            ) {
                                blocks.forEach { b ->
                                    when (b) {
                                        is DocRender.MdBlock.Heading -> Text(
                                            b.text,
                                            style = when (b.level) {
                                                1 -> MaterialTheme.typography.headlineSmall
                                                2 -> MaterialTheme.typography.titleLarge
                                                else -> MaterialTheme.typography.titleMedium
                                            },
                                            modifier = Modifier.padding(vertical = 3.dp),
                                        )
                                        is DocRender.MdBlock.ListItem -> Row(modifier = Modifier.padding(vertical = 1.dp)) {
                                            Text("•  ", style = MaterialTheme.typography.bodyLarge)
                                            Text(b.text, style = MaterialTheme.typography.bodyLarge)
                                        }
                                        is DocRender.MdBlock.Quote -> Text(
                                            b.text,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(8.dp),
                                        )
                                        is DocRender.MdBlock.Code -> Text(
                                            b.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(8.dp),
                                        )
                                        is DocRender.MdBlock.Para -> Text(
                                            b.text,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.padding(vertical = 2.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                        plainText != null -> {
                            Text(
                                plainText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                            )
                        }
                        else -> {
                            Text(
                                "暂不支持该文档格式预览",
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** docx 段落内富文本：加粗 run 用 Bold 字体 */
private fun buildAnnotatedPara(p: DocRender.DocxPara): androidx.compose.ui.text.AnnotatedString {
    val b = androidx.compose.ui.text.AnnotatedString.Builder()
    p.runs.forEach { run ->
        val style = if (run.bold) {
            androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)
        } else {
            androidx.compose.ui.text.SpanStyle()
        }
        b.withStyle(style) { b.append(run.text) }
    }
    return b.toAnnotatedString()
}

/** 文档内表格：行渲染（表头高亮） */
@Composable
private fun DocTableGrid(rows: List<List<String>>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { idx, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (idx == 0) MaterialTheme.colorScheme.primaryContainer
                        else if (idx % 2 == 0) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                row.forEach { cell ->
                    Text(
                        cell,
                        style = if (idx == 0) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        else MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
