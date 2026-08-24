package com.example.courseschedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 使用真实的教务系统导出文件验证 OLE2/BIFF8 读取 + 矩阵解析。
 * 测试资源: app/src/test/resources/schedule_fixture.xls
 */
class XlsReaderTest {

    private fun fixtureBytes(): ByteArray {
        val stream = javaClass.getResourceAsStream("/schedule_fixture.xls")
            ?: throw IllegalStateException("缺少测试资源 schedule_fixture.xls")
        return stream.use { it.readBytes() }
    }

    @Test
    fun `读取真实 xls 的单元格文本`() {
        val grid = XlsReader.readSheetGrid(fixtureBytes())
        assertTrue("网格至少 7 行", grid.size >= 7)
        assertTrue("网格至少 9 列", grid[0].size >= 9)

        // 行2(1-2节) 列3(星期二) 应有 Java程序设计
        assertTrue(grid[2][3].contains("Java程序设计"))
        // 行2 列5(星期四) 应含两门课(换行分隔)
        assertTrue(grid[2][5].contains("数字音视频处理"))
        assertTrue(grid[2][5].contains("创意广告设计实践"))
        // 行5(7-8节) 列4(星期三) 应含三门课
        assertTrue(grid[5][4].contains("微信公众平台开发"))
        assertTrue(grid[5][4].contains("HTML5"))
        assertTrue(grid[5][4].contains("游戏开发基础"))
    }

    @Test
    fun `真实 xls 完整解析为 31 条课程`() {
        val grid = XlsReader.readSheetGrid(fixtureBytes())
        val data = MatrixScheduleParser.parse(grid)

        assertEquals(16, data.totalWeeks)
        assertEquals(31, data.courses.size)

        // 抽查: Python程序设计 5-6节 在星期三 教学楼311
        val python56 = data.courses.filter { it.name == "Python程序设计" && it.startSlot == 5 }
        assertEquals(1, python56.size)
        assertEquals(3, python56[0].dayOfWeek)
        assertEquals("教学楼311", python56[0].classroom)
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14), python56[0].weeks)

        // 抽查: HTML5 周五 1-2节 拆成两条 (1-2周,4周 和 5-12周)
        val html5Fri = data.courses.filter { it.name == "HTML5" && it.dayOfWeek == 5 && it.startSlot == 1 }
        assertEquals(2, html5Fri.size)
        assertTrue(html5Fri.any { it.weeks == listOf(1, 2, 4) })
        assertTrue(html5Fri.any { it.weeks == (5..12).toList() })

        // 抽查: 创意广告设计 单双周两条
        val ad = data.courses.filter { it.name == "创意广告设计" }
        assertEquals(2, ad.size)
        assertTrue(ad[0].weeks.all { it % 2 == 1 })
        assertTrue(ad[1].weeks.all { it % 2 == 0 })

        // 所有课程字段合法
        for (c in data.courses) {
            assertTrue(c.name.isNotBlank())
            assertTrue(c.classroom.isNotBlank())
            assertTrue(c.weeks.isNotEmpty())
            assertTrue(c.dayOfWeek in 1..7)
            assertTrue(c.startSlot in 1..12)
            assertTrue(c.endSlot >= c.startSlot)
            assertTrue(c.weeks.max() <= data.totalWeeks)
        }
    }

    @Test
    fun `非 xls 文件抛异常`() {
        try {
            XlsReader.readSheetGrid("这不是一个xls文件，只是普通文本".toByteArray())
            assertTrue("应当抛出异常", false)
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
