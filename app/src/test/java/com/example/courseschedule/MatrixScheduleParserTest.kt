package com.example.courseschedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixScheduleParserTest {

    @Test
    fun `parseWeeks 基本区间`() {
        assertEquals((1..16).toList(), MatrixScheduleParser.parseWeeks("1-16周"))
        assertEquals(listOf(1, 2, 4), MatrixScheduleParser.parseWeeks("1-2周,4周"))
        assertEquals(listOf(13), MatrixScheduleParser.parseWeeks("13周"))
        assertEquals(listOf(5, 6, 7, 8, 11, 12), MatrixScheduleParser.parseWeeks("5-8周,11-12周"))
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14), MatrixScheduleParser.parseWeeks("2-9周,11-14周"))
    }

    @Test
    fun `parseWeeks 单双周`() {
        assertEquals(listOf(10, 12, 13, 14, 15, 16), MatrixScheduleParser.parseWeeks("10-12周(双),13-16周"))
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), MatrixScheduleParser.parseWeeks("1-15周(单)"))
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), MatrixScheduleParser.parseWeeks("2-16周(双)"))
        assertEquals(listOf(14, 16), MatrixScheduleParser.parseWeeks("14-16周(双)"))
    }

    @Test
    fun `parseWeeks 空与非法`() {
        assertEquals(emptyList<Int>(), MatrixScheduleParser.parseWeeks(""))
        assertEquals(emptyList<Int>(), MatrixScheduleParser.parseWeeks(null))
        assertEquals(emptyList<Int>(), MatrixScheduleParser.parseWeeks("abc"))
    }

    @Test
    fun `parseEntry 完整字段`() {
        val c = MatrixScheduleParser.parseEntry(
            "Java程序设计/(1-2节)1-16周/中心校区 教学楼412/李老师/Java程序设计-0005/4.0",
            dayOfWeek = 2,
            fallback = 1 to 2
        )
        assertTrue(c != null)
        assertEquals("Java程序设计", c!!.name)
        assertEquals("李老师", c.teacher)
        assertEquals("教学楼412", c.classroom) // 去掉"中心校区 "前缀
        assertEquals(2, c.dayOfWeek)
        assertEquals(1, c.startSlot)
        assertEquals(2, c.endSlot)
        assertEquals((1..16).toList(), c.weeks)
    }

    @Test
    fun `parseEntry 单双周与多行`() {
        val c = MatrixScheduleParser.parseEntry(
            "创意广告设计实践/(1-2节)10-12周(双),13-16周/中心校区 教学楼213/张老师/创意广告设计实践-0002/1.0",
            dayOfWeek = 1,
            fallback = 1 to 2
        )
        assertTrue(c != null)
        assertEquals(listOf(10, 12, 13, 14, 15, 16), c!!.weeks)
    }

    @Test
    fun `parseEntry 缺字段返回 null`() {
        assertNull(MatrixScheduleParser.parseEntry("", 1, 1 to 2))
        assertNull(MatrixScheduleParser.parseEntry("只有课程名", 1, 1 to 2))
        assertNull(MatrixScheduleParser.parseEntry("课程/(1-2节)abc/教室/教师", 1, 1 to 2)) // 周次非法
    }

    @Test
    fun `parse 完整矩阵`() {
        val grid = listOf(
            listOf("", "", "", "", "", "", "", "", ""),
            listOf("节次", "", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"),
            listOf(
                "上午", "一",
                "创意广告设计实践/(1-2节)10-12周(双),13-16周/中心校区 教学楼213/张老师/创意广告设计实践-0002/1.0",
                "Java程序设计/(1-2节)1-16周/中心校区 教学楼412/李老师/Java程序设计-0005/4.0",
                "",
                "数字音视频处理/(1-2节)1-4周/中心校区 教学楼306/王老师/数字音视频处理-0002/3.0\n数字音视频处理/(1-2节)5-12周/中心校区 教学楼306/王老师/数字音视频处理-0002A/3.0",
                "HTML5/(1-2节)1-2周,4周/中心校区 教学楼306/赵老师/HTML5-0001/3.0",
                "",
                ""
            ),
            listOf("上午", "二", "", "", "创意广告设计/(3-4节)1-15周(单)/中心校区 教学楼213/张老师/创意广告设计-0002/2.0", "", "", "", ""),
            listOf("下午", "三", "", "", "", "", "", "", ""),
            listOf("下午", "四", "", "", "", "", "", "", ""),
            listOf("晚上", "五", "", "", "", "", "", "", ""),
            listOf("注", "内容顺序为：课程<>周次<>校区<>地点<>教师<>教学班<>学分", "", "", "", "", "", "", "")
        )

        val data = MatrixScheduleParser.parse(grid)
        assertEquals(6, data.courses.size)
        assertEquals(16, data.totalWeeks)

        val java = data.courses.first { it.name == "Java程序设计" }
        assertEquals("教学楼412", java.classroom)
        assertEquals(2, java.dayOfWeek)
        assertTrue(java.color.startsWith("#"))

        val sameColor = data.courses.filter { it.name == "数字音视频处理" }.map { it.color }.distinct()
        assertEquals(1, sameColor.size) // 同一课程同色

        val html5 = data.courses.first { it.name == "HTML5" }
        assertEquals(listOf(1, 2, 4), html5.weeks) // 1-2周,4周

        val ad = data.courses.first { it.name == "创意广告设计" }
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), ad.weeks) // 单周
    }
}
