package com.example.courseschedule.data

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * 极简 .xlsx 读取器 (纯 JDK/Android API, 无第三方依赖)。
 *
 * 只读取第一个工作表, 支持:
 *  - 共享字符串 (xl/sharedStrings.xml, 含富文本 <r><t> 片段)
 *  - 行内字符串 (t="inlineStr") 与数字单元格
 *  - 通过 workbook.xml + workbook.xml.rels 定位工作表
 *
 * 输出网格与 XlsReader 相同约定: 行索引 0 起, 第 2..6 行是 1-2 节 .. 9-10 节,
 * 第 2 列(=索引2) 起依次为 星期一..星期日 (dayOfWeek = 列-1)。
 * 单元格内多门课程以换行分隔 (与教务系统 .xls 导出一致)。
 */
object XlsxReader {

    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

    /** 同一单元格中连续课程条目的起点: 课程名/[(节次)]周次/ 模式 */
    private val ENTRY_SPLIT = Regex("""(?<!\S)(?=[^\s/][^/]*/(?:\(\d+(?:-\d+)?节?\))?[^/]*周)""")

    fun readSheetGrid(bytes: ByteArray): List<List<String>> {
        val entries = readZipEntries(bytes)
        val shared = parseSharedStrings(entries["xl/sharedStrings.xml"])
        val sheetPath = resolveFirstSheetPath(entries)
        val sheetXml = entries[sheetPath] ?: entries.values.firstOrNull { it.size > 0 }
            ?: throw IllegalArgumentException("xlsx 中找不到工作表数据")
        return parseSheetGrid(String(sheetXml, Charsets.UTF_8), shared)
    }

    // ---------------------------------------------------------------- zip

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        val zin = ZipInputStream(ByteArrayInputStream(bytes))
        while (true) {
            val entry = zin.nextEntry ?: break
            val buf = zin.readBytes()
            if (!entry.isDirectory) out[entry.name] = buf
        }
        zin.close()
        if (out.isEmpty()) throw IllegalArgumentException("不是有效的 .xlsx 文件（缺少数据内容）")
        return out
    }

    // ---------------------------------------------------------------- xml

    private fun parseXml(xml: String): Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        return f.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    private fun textOf(element: Element): String = element.textContent ?: ""

    private fun parseSharedStrings(xmlBytes: ByteArray?): Map<Int, String> {
        if (xmlBytes == null) return emptyMap()
        val xml = String(xmlBytes, Charsets.UTF_8)
        if (!xml.contains("<si")) return emptyMap()
        val doc = parseXml(xml)
        val out = HashMap<Int, String>()
        val siNodes = doc.getElementsByTagNameNS(MAIN_NS, "si")
        for (i in 0 until siNodes.length) {
            val si = siNodes.item(i) as? Element ?: continue
            // 富文本: 拼接所有 <t> 片段; 导出器会在片段末尾插入 \r\n 断行,
            // 统一替换为空格 (解析器对空格与换行均兼容)
            val text = concatT(si).replace("\r\n", " ").replace("\r", " ").replace("\n", " ")
                .trimEnd()
            out[i] = text
        }
        return out
    }

    private fun concatT(parent: Element): String {
        val sb = StringBuilder()
        val ts = parent.getElementsByTagNameNS(MAIN_NS, "t")
        for (i in 0 until ts.length) {
            val t = ts.item(i) as? Element ?: continue
            sb.append(t.textContent ?: "")
        }
        return sb.toString()
    }

    /** 解析 workbook 中第一个 sheet, 通过 rels 找到工作表 xml 路径 */
    private fun resolveFirstSheetPath(entries: Map<String, ByteArray>): String {
        val workbook = entries["xl/workbook.xml"]
        val rels = entries["xl/_rels/workbook.xml.rels"]
        if (workbook == null) throw IllegalArgumentException("xlsx 中缺少 workbook.xml")
        if (rels == null) return "xl/worksheets/sheet1.xml"

        val wDoc = parseXml(String(workbook, Charsets.UTF_8))
        val sheet = wDoc.getElementsByTagNameNS(MAIN_NS, "sheet").item(0) as? Element
            ?: throw IllegalArgumentException("xlsx 中没有工作表")
        val rid = sheet.getAttribute("r:id").ifBlank { sheet.getAttribute("id") }
        if (rid.isBlank()) return "xl/worksheets/sheet1.xml"

        val rDoc = parseXml(String(rels, Charsets.UTF_8))
        val relsNodes = rDoc.getElementsByTagNameNS(
            "http://schemas.openxmlformats.org/package/2006/relationships", "Relationship"
        )
        for (i in 0 until relsNodes.length) {
            val rel = relsNodes.item(i) as? Element ?: continue
            if (rel.getAttribute("Id") == rid && rel.getAttribute("Type").contains("worksheet")) {
                val target = rel.getAttribute("Target").trimStart('/')
                return if (target.startsWith("xl/")) target else "xl/$target"
            }
        }
        return "xl/worksheets/sheet1.xml"
    }

    // ---------------------------------------------------------------- sheet

    private fun parseSheetGrid(xml: String, shared: Map<Int, String>): List<List<String>> {
        val doc = parseXml(xml)
        val rows = doc.getElementsByTagNameNS(MAIN_NS, "row")

        val gridByRow = HashMap<Int, HashMap<Int, String>>()
        var maxIdx = 0
        for (i in 0 until rows.length) {
            val row = rows.item(i) as? Element ?: continue
            val rowIdx = row.getAttribute("r").toIntOrNull() ?: (i + 1)
            val cells = HashMap<Int, String>()
            val cellNodes = row.getElementsByTagNameNS(MAIN_NS, "c")
            for (j in 0 until cellNodes.length) {
                val c = cellNodes.item(j) as? Element ?: continue
                val col = colIndex(c.getAttribute("r")) ?: j
                val text = cellText(c, shared)
                if (text.isNotBlank()) cells[col] = text
            }
            gridByRow[rowIdx] = cells
            if (rowIdx > maxIdx) maxIdx = rowIdx
        }

        val rawGrid = ArrayList<HashMap<Int, String>>(maxIdx + 1)
        for (i in 0..maxIdx) rawGrid.add(gridByRow[i] ?: HashMap())
        return normalize(rawGrid)
    }

    /** 单元格文本: 共享字符串 / 行内字符串 / 数字 */
    private fun cellText(c: Element, shared: Map<Int, String>): String {
        val type = c.getAttribute("t")
        val v = c.getElementsByTagNameNS(MAIN_NS, "v").item(0) as? Element
        val text = when (type) {
            "s" -> v?.let { shared[it.textContent.trim().toIntOrNull() ?: -1] } ?: ""
            "inlineStr" -> {
                val isNode = c.getElementsByTagNameNS(MAIN_NS, "is").item(0) as? Element
                if (isNode != null) concatT(isNode) else ""
            }
            "str" -> v?.textContent ?: ""
            else -> v?.textContent?.trim() ?: ""
        }
        if (text.isBlank()) return ""

        // 同一单元格多门课程: 按"课程名/周次/"模式切分为多条, 以换行连接
        val entries = ENTRY_SPLIT.split(text)
        val joined = if (entries.size <= 1) text else entries.filter { it.isNotBlank() }.joinToString("\n")
        return joined.trim()
    }

    private fun colIndex(ref: String): Int? {
        if (ref.isEmpty()) return null
        var idx = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') idx = idx * 26 + (ch - 'A' + 1)
            else break
        }
        return if (idx > 0) idx - 1 else null
    }

    /**
     * 归一化为与 XlsReader 相同的网格约定:
     * 找到 "星期一" 表头行, 其后 5 行是 1-2 .. 9-10 节 (网格行 2..6), 列 2..8 = 周一..周日。
     * 找不到表头时按原样输出 (行号-1)。
     */
    private fun normalize(rawGrid: List<HashMap<Int, String>>): List<List<String>> {
        var headerRow = -1
        var dayCol = -1
        for (r in rawGrid.indices) {
            for ((col, text) in rawGrid[r]) {
                if (text.trim() == "星期一") {
                    headerRow = r
                    dayCol = col
                    break
                }
            }
            if (headerRow >= 0) break
        }

        if (headerRow < 0) {
            // 兜底: 按 XML 行号-1 原样输出
            val out = ArrayList<List<String>>()
            var maxCol = 0
            for (r in rawGrid.indices) maxCol = maxOf(maxCol, rawGrid[r].keys.maxOrNull() ?: 0)
            for (r in 1 until rawGrid.size) {
                val row = ArrayList<String>(maxCol + 1)
                for (c in 0..maxCol) row.add(rawGrid[r][c] ?: "")
                out.add(row)
            }
            return out
        }

        val result = ArrayList<ArrayList<String>>(7)
        for (i in 0 until 7) {
            val row = ArrayList<String>(9)
            for (c in 0 until 9) row.add("")
            result.add(row)
        }
        for (slot in 0 until 5) {
            val srcRow = rawGrid.getOrNull(headerRow + 1 + slot) ?: continue
            for (day in 1..7) {
                val srcCol = dayCol + day - 1
                val text = srcRow[srcCol] ?: ""
                if (text.isNotBlank()) result[2 + slot][1 + day] = text
            }
        }
        return result
    }
}
