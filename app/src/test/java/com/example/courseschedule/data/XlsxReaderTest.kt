package com.example.courseschedule.data

import com.example.courseschedule.model.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * XlsxReader 单元测试: 用代码合成一个与教务系统导出结构一致的 .xlsx,
 * 覆盖: 表头定位、富文本断行(\r\n)容忍、同一单元格多门课程(空格粘连)拆分、冲突识别。
 */
class XlsxReaderTest {

    private fun buildXlsx(sheetXml: String, sharedXml: String): ByteArray {
        val baos = ByteArrayOutputStream()
        val zip = ZipOutputStream(baos)
        fun add(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        add("[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
               |<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/></Types>""".trimMargin())
        add("xl/workbook.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
               |<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
               |<sheets><sheet name="Table 1" sheetId="1" r:id="rId1"/></sheets></workbook>""".trimMargin())
        add("xl/_rels/workbook.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
               |<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
               |<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
               |<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
               |</Relationships>""".trimMargin())
        add("xl/sharedStrings.xml", sharedXml)
        add("xl/worksheets/sheet1.xml", sheetXml)
        zip.close()
        return baos.toByteArray()
    }

    /** 按顺序生成 si, text 中可含富文本标记 [\r] 表示 \r\n 断行 */
    private fun sharedXml(vararg texts: String): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${texts.size}" uniqueCount="${texts.size}">""")
        for (t in texts) {
            sb.append("<si>")
            if (t.contains("[\\r]")) {
                val parts = t.split("[\\r]")
                for (i in parts.indices) {
                    sb.append("<r><rPr><sz val=\"7.0\"/></rPr><t xml:space=\"preserve\">")
                    sb.append(parts[i])
                    sb.append(if (i < parts.size - 1) "\r\n" else "")
                    sb.append("</t></r>")
                }
            } else {
                sb.append("<t xml:space=\"preserve\">").append(t).append("</t>")
            }
            sb.append("</si>")
        }
        sb.append("</sst>")
        return sb.toString()
    }

    private fun sheetXml(vararg rows: String): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetData>""")
        for (r in rows) sb.append(r)
        sb.append("""</sheetData></worksheet>""")
        return sb.toString()
    }

    private fun row(r: Int, cells: String) = """<row r="$r">$cells</row>"""

    private fun c(ref: String, si: Int? = null, text: String? = null): String = when {
        si != null -> """<c r="$ref" t="s"><v>$si</v></c>"""
        text != null -> """<c r="$ref" t="inlineStr"><is><t xml:space="preserve">$text</t></is></c>"""
        else -> """<c r="$ref"/>"""
    }

    @Test
    fun `读取xlsx网格并解析课程`() {
        // si: 0=标题 1=节次 2..8=星期一..星期日 9=一 10=二 11=三
        val shared = sharedXml(
            "2026-2027年第1学期",
            "节次",
            "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日",
            "一", "二", "三"
        )
        val sheet = sheetXml(
            row(1, c("A1", si = 0)),
            row(2, c("A2", si = 1) + c("C2", si = 2) + c("D2", si = 3) + c("E2", si = 4) + c("F2", si = 5) + c("G2", si = 6) + c("H2", si = 7) + c("I2", si = 8)),
            row(3, c("B3", si = 9) + c("C3", text = "英语/(1-2节)1-16周/教学楼101/张老师/英语-0001/4.0") + c("D3", text = "日语/(1-2节)1-16周/教学楼102/李老师/日语-0001/4.0")),
            row(4, c("B4", si = 10)),
            row(5, c("B5", si = 11) + c("D5", text = "Java编程/(5-6节)9-12周/教学楼301/赵老师/Java编程-0001/4/32"))
        )

        val grid = XlsxReader.readSheetGrid(buildXlsx(sheet, shared))

        assertTrue("网格至少 7 行", grid.size >= 7)
        assertTrue("行至少 9 列", grid[0].size >= 9)
        // 第 2 列(索引2) = 星期一
        assertTrue(grid[2][2].contains("英语"))
        assertTrue(grid[2][3].contains("日语"))
        // 第 5 行(5-6节) 星期二
        assertTrue(grid[4][3].contains("Java编程"))

        val data = MatrixScheduleParser.parse(grid)
        assertEquals(3, data.courses.size)
        val english = data.courses.first { it.name == "英语" }
        assertEquals(1, english.dayOfWeek)
        assertEquals(1, english.startSlot)
        assertEquals(2, english.endSlot)
        assertEquals((1..16).toList(), english.weeks)
        val java = data.courses.first { it.name == "Java编程" }
        assertEquals(2, java.dayOfWeek)
        assertEquals(5, java.startSlot)
        assertEquals(listOf(9, 10, 11, 12), java.weeks)
    }

    @Test
    fun `富文本断行不影响周次解析`() {
        val shared = sharedXml(
            "标题", "节次",
            "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日",
            "一",
            "Java程序设计/1-1[\\r]6周/ 教学楼 412/李老师/Java程序设计-0005/4.0"
        )
        val sheet = sheetXml(
            row(1, c("A1", si = 0)),
            row(2, c("A2", si = 1) + c("C2", si = 2) + c("D2", si = 3) + c("E2", si = 4) + c("F2", si = 5) + c("G2", si = 6) + c("H2", si = 7) + c("I2", si = 8)),
            row(3, c("B3", si = 9) + c("C3", si = 10))
        )

        val data = MatrixScheduleParser.parse(XlsxReader.readSheetGrid(buildXlsx(sheet, shared)))

        assertEquals(1, data.courses.size)
        assertEquals("Java程序设计", data.courses[0].name)
        assertEquals((1..16).toList(), data.courses[0].weeks)
        assertEquals(1, data.courses[0].dayOfWeek)
    }

    @Test
    fun `同一单元格多门课程空格粘连时拆分`() {
        // 单双周两门课被导出器用空格粘在同一单元格 (如 "…/2/16 高数/2-16周(双)/…")
        val shared = sharedXml(
            "标题", "节次",
            "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日",
            "二",
            "高数/1-15周(单)/ 教学楼 201/王老师/高数-0001/学时:16/2/16 高数/2-16周(双)/ 教学楼 201/王老师/高数-0001A/学时:16/2/16"
        )
        val sheet = sheetXml(
            row(1, c("A1", si = 0)),
            row(2, c("A2", si = 1) + c("C2", si = 2) + c("D2", si = 3) + c("E2", si = 4) + c("F2", si = 5) + c("G2", si = 6) + c("H2", si = 7) + c("I2", si = 8)),
            row(3, c("B3", si = 8)),
            row(4, c("B4", si = 9) + c("C4", si = 10)),
            row(5, c("B5", si = 8)),
            row(6, c("B6", si = 8)),
            row(7, c("B7", si = 8))
        )

        val data = MatrixScheduleParser.parse(XlsxReader.readSheetGrid(buildXlsx(sheet, shared)))

        assertEquals("同一单元格应拆成两门课", 2, data.courses.size)
        val odd = data.courses.first { it.weeks.first() % 2 == 1 }
        val even = data.courses.first { it.weeks.first() % 2 == 0 }
        assertEquals("高数", odd.name)
        assertEquals(1, odd.dayOfWeek)
        assertEquals(3, odd.startSlot)
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), odd.weeks)
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), even.weeks)
        // 同一门课自身不当冲突
        assertTrue(ScheduleSelection.findConflicts(data).isEmpty())
    }

    @Test
    fun `xlsx同样识别时间冲突`() {
        val shared = sharedXml(
            "标题", "节次",
            "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日",
            "一",
            "英语/(1-2节)1-16周/教学楼101/张老师/英语-0001/4.0 日语/(1-2节)1-16周/教学楼102/李老师/日语-0001/4.0"
        )
        val sheet = sheetXml(
            row(1, c("A1", si = 0)),
            row(2, c("A2", si = 1) + c("C2", si = 2) + c("D2", si = 3) + c("E2", si = 4) + c("F2", si = 5) + c("G2", si = 6) + c("H2", si = 7) + c("I2", si = 8)),
            row(3, c("B3", si = 9) + c("C3", si = 10)),
            row(4, c("B4", si = 9)),
            row(5, c("B5", si = 9)),
            row(6, c("B6", si = 9)),
            row(7, c("B7", si = 9))
        )

        val data = MatrixScheduleParser.parse(XlsxReader.readSheetGrid(buildXlsx(sheet, shared)))
        val conflicts = ScheduleSelection.findConflicts(data)

        assertEquals(1, conflicts.size)
        assertEquals(setOf("英语", "日语"), conflicts[0].courses.map { it.name }.toSet())
    }

    @Test
    fun `非xlsx文件抛异常`() {
        try {
            XlsxReader.readSheetGrid("这不是zip文件，只是普通文本而已".toByteArray())
            assertTrue("应当抛出异常", false)
        } catch (_: Exception) {
            // expected
        }
    }
}
