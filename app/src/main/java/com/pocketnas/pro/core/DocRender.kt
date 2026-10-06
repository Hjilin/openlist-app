package com.pocketnas.pro.core

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * 文档在线渲染（纯原生，无 WebView）。
 *
 * - Markdown：轻量块解析（标题 / 列表 / 引用 / 代码块 / 段落）
 * - CSV：行列解析（基础引号处理）
 * - docx / xlsx：直接解 ZIP 提取文本（docx=word/document.xml 的 <w:t>，
 *   xlsx=sharedStrings + sheet 单元格）
 * - PDF：系统 PdfRenderer 渲染页面 Bitmap（翻页浏览）
 * - 其余（txt / json / log / srt / ini / conf）：等宽文本展示
 */
object DocRender {

    sealed class MdBlock {
        data class Heading(val level: Int, val text: String) : MdBlock()
        data class ListItem(val text: String) : MdBlock()
        data class Quote(val text: String) : MdBlock()
        data class Code(val text: String) : MdBlock()
        data class Para(val text: String) : MdBlock()
    }

    /** 轻量 Markdown 块解析（井号标题、短横/星号列表、尖括号引用、三反引号代码块、段落） */
    fun parseMarkdown(src: String): List<MdBlock> {
        val out = mutableListOf<MdBlock>()
        var inCode = false
        val codeBuf = StringBuilder()
        for (raw in src.lines()) {
            val line = raw.trimEnd()
            when {
                line.trimStart().startsWith("```") -> {
                    if (inCode) {
                        out += MdBlock.Code(codeBuf.toString().trim())
                        codeBuf.clear()
                    }
                    inCode = !inCode
                }
                inCode -> codeBuf.appendLine(line)
                line.startsWith("### ") -> out += MdBlock.Heading(3, line.removePrefix("### ").trim())
                line.startsWith("## ") -> out += MdBlock.Heading(2, line.removePrefix("## ").trim())
                line.startsWith("# ") -> out += MdBlock.Heading(1, line.removePrefix("# ").trim())
                line.trimStart().startsWith("- ") ->
                    out += MdBlock.ListItem(line.trimStart().removePrefix("- ").trim())
                line.trimStart().startsWith("* ") ->
                    out += MdBlock.ListItem(line.trimStart().removePrefix("* ").trim())
                line.trimStart().startsWith("> ") ->
                    out += MdBlock.Quote(line.trimStart().removePrefix("> ").trim())
                line.isBlank() -> {}
                else -> out += MdBlock.Para(line.trim())
            }
        }
        if (inCode && codeBuf.isNotBlank()) out += MdBlock.Code(codeBuf.toString().trim())
        return out
    }

    /** CSV 基础解析：处理引号包裹的逗号 */
    fun parseCsv(text: String, maxRows: Int = 200): List<List<String>> {
        return text.lines().filter { it.isNotBlank() }.take(maxRows).map { line -> parseCsvLine(line) }
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuote -> {
                    if (c == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ } else inQuote = false
                    } else sb.append(c)
                }
                c == '"' -> inQuote = true
                c == ',' -> { out += sb.toString().trim(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString().trim()
        return out
    }

    /** docx：解 word/document.xml 提取段落文本 */
    fun extractDocx(bytes: ByteArray): List<String> {
        return try {
            val zin = ZipInputStream(ByteArrayInputStream(bytes))
            var xml: String? = null
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.name == "word/document.xml") {
                    xml = zin.readBytes().toString(Charsets.UTF_8)
                    break
                }
            }
            zin.close()
            val doc = xml ?: return emptyList()
            val paras = Regex("<w:p[ >]").split(doc).drop(1)
            paras.map { p ->
                Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(p).joinToString("") { it.groupValues[1] }
            }.filter { it.isNotBlank() }.take(400)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** xlsx：sharedStrings + sheet1 单元格文本（限制 200 行） */
    fun extractXlsx(bytes: ByteArray): List<List<String>> {
        return try {
            val zin = ZipInputStream(ByteArrayInputStream(bytes))
            var shared: String? = null
            var sheet: String? = null
            while (true) {
                val e = zin.nextEntry ?: break
                when (e.name) {
                    "xl/sharedStrings.xml" -> shared = zin.readBytes().toString(Charsets.UTF_8)
                    "xl/worksheets/sheet1.xml" -> sheet = zin.readBytes().toString(Charsets.UTF_8)
                }
            }
            zin.close()
            val strings = Regex("<si>.*?</si>").findAll(shared ?: "").map { si ->
                Regex("<t[^>]*>([^<]*)</t>").findAll(si.value).joinToString("") { it.groupValues[1] }
            }.toList()
            val cells = Regex("<c[^>]*>.*?</c>|/>").findAll(sheet ?: "").toList()
            val rows = mutableListOf<List<String>>()
            var cur = mutableListOf<String>()
            var lastRef = ""
            for (m in cells) {
                val c = m.value
                if (c.startsWith("<c")) {
                    val ref = Regex("r=\"([A-Z]+)\\d+\"").find(c)?.groupValues?.get(1) ?: ""
                    if (ref.isNotEmpty() && lastRef.isNotEmpty() && ref != lastRef) {
                        rows += cur.toList(); cur = mutableListOf()
                    }
                    lastRef = ref
                    val t = Regex("t=\"(\\w+)\"").find(c)?.groupValues?.get(1)
                    val v = Regex("<v>([^<]*)</v>").find(c)?.groupValues?.get(1)
                    cur += when {
                        t == "s" -> v?.toIntOrNull()?.let { strings.getOrNull(it) } ?: ""
                        else -> v ?: ""
                    }
                }
            }
            if (cur.isNotEmpty()) rows += cur.toList()
            rows.filter { it.any { cell -> cell.isNotBlank() } }.take(200)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** docx 结构化块（标题 / 加粗段落 / 表格），用于主流排版渲染 */
    data class DocxRun(val text: String, val bold: Boolean = false)
    data class DocxPara(val runs: List<DocxRun>, val style: String = "normal") // style: h1/h2/h3/normal
    sealed class DocxBlock {
        data class Para(val para: DocxPara) : DocxBlock()
        data class Table(val rows: List<List<String>>) : DocxBlock()
    }

    /** docx：结构化解析（标题层级 / 加粗 / 表格），供主流排版渲染 */
    fun parseDocx(bytes: ByteArray): List<DocxBlock> {
        return try {
            val zin = ZipInputStream(ByteArrayInputStream(bytes))
            var xml: String? = null
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.name == "word/document.xml") { xml = zin.readBytes().toString(Charsets.UTF_8); break }
            }
            zin.close()
            val doc = xml ?: return emptyList()
            val out = mutableListOf<DocxBlock>()

            // 表格块整体提取（w:tbl ... </w:tbl>）
            val tblRe = Regex("<w:tbl>.*?</w:tbl>", RegexOption.DOT_MATCHES_ALL)
            val tables = tblRe.findAll(doc).map { it.value }.toMutableList()

            // 按段落 w:p 切分，遇到表格则穿插
            var body = doc
            val paraRe = Regex("<w:p[ >].*?</w:p>", RegexOption.DOT_MATCHES_ALL)
            val tokens = mutableListOf<Pair<String, String>>() // ("p", xml) / ("tbl", xml)
            var idx = 0
            for (m in paraRe.findAll(doc)) {
                val start = m.range.first
                if (start > idx) {
                    // 段落之间的片段里找表格
                    val seg = doc.substring(idx, start)
                    for (t in tblRe.findAll(seg)) {
                        tokens += "tbl" to t.value
                    }
                }
                tokens += "p" to m.value
                idx = m.range.last + 1
            }
            for (t in tblRe.findAll(doc.substring(idx))) { tokens += "tbl" to t.value }

            for ((kind, xmlBlock) in tokens) {
                if (kind == "tbl") {
                    val rows = mutableListOf<List<String>>()
                    for (tr in Regex("<w:tr[ >].*?</w:tr>", RegexOption.DOT_MATCHES_ALL).findAll(xmlBlock)) {
                        val cells = mutableListOf<String>()
                        for (tc in Regex("<w:tc[ >].*?</w:tc>", RegexOption.DOT_MATCHES_ALL).findAll(tr.value)) {
                            val txt = Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(tc.value)
                                .joinToString("") { it.groupValues[1] }.trim()
                            cells += txt
                        }
                        if (cells.any { it.isNotBlank() }) rows += cells
                    }
                    if (rows.isNotEmpty()) out += DocxBlock.Table(rows.take(30))
                } else {
                    // 段落：样式 + runs
                    val styleRe = Regex("<w:pStyle w:val=\"(\\w+)\"").find(xmlBlock)
                    val style = when (styleRe?.groupValues?.get(1)) {
                        "Heading1" -> "h1"; "Heading2" -> "h2"; "Heading3" -> "h3"
                        else -> "normal"
                    }
                    val runs = mutableListOf<DocxRun>()
                    for (r in Regex("<w:r[ >].*?</w:r>", RegexOption.DOT_MATCHES_ALL).findAll(xmlBlock)) {
                        val rv = r.value
                        val bold = Regex("<w:b\\s*/>|<w:b/>").containsMatchIn(rv)
                        val txt = Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(rv)
                            .joinToString("") { it.groupValues[1] }
                        if (txt.isNotBlank()) runs += DocxRun(txt, bold)
                    }
                    if (runs.isNotEmpty()) out += DocxBlock.Para(DocxPara(runs, style))
                }
            }
            out.take(400)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** PDF 页面渲染（等比缩放到 [maxEdge] 内，默认 900px） */
    fun renderPdfPage(pdf: PdfRenderer, index: Int, maxEdge: Int = 900): Bitmap {
        val page = pdf.openPage(index)
        try {
            val w = page.width
            val h = page.height
            val scale = (maxEdge.toFloat() / maxOf(w, h)).coerceIn(0.4f, 2.0f)
            val bmp = Bitmap.createBitmap(
                (w * scale).toInt().coerceAtLeast(1),
                (h * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        } finally {
            page.close()
        }
    }

    /** 打开 PDF 渲染器（调用方负责 close） */
    fun openPdf(file: java.io.File): PdfRenderer? {
        return try {
            PdfRenderer(
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            )
        } catch (_: Exception) {
            null
        }
    }
}
