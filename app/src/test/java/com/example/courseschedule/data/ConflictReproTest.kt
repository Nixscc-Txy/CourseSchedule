package com.example.courseschedule.data

import com.example.courseschedule.model.Course
import com.example.courseschedule.model.ScheduleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 冲突识别回归测试:
 *  - 真实教务系统导出文件(fixture)端到端
 *  - 同一物理时段不同周次的子组合应合并为一个冲突组
 *  - 同一门课因周次拆成多条记录时, 只作为一个可选项
 */
class ConflictReproTest {

    private fun fixtureCourses(): ScheduleData {
        val stream = javaClass.getResourceAsStream("/schedule_fixture.xls")
            ?: throw IllegalStateException("缺少测试资源 schedule_fixture.xls")
        val grid = XlsReader.readSheetGrid(stream.use { it.readBytes() })
        return MatrixScheduleParser.parse(grid)
    }

    @Test
    fun `真实xls的周三7-8节应合并为一个冲突组共三门课`() {
        val data = fixtureCourses()

        val conflicts = ScheduleSelection.findConflicts(data)

        val wed78 = conflicts.filter { it.dayOfWeek == 3 && it.startSlot == 7 }
        assertEquals("周三7-8节应只有一个冲突组(不同周次子组合需合并)", 1, wed78.size)
        val group = wed78[0]
        assertEquals(
            setOf("HTML5", "微信公众平台开发", "游戏开发基础"),
            group.courses.map { it.name }.toSet()
        )
        // HTML5/微信/游戏 三门课支离破碎的周次合并后应是完整集合
        assertTrue(group.weeks.contains(1))
        assertTrue(group.weeks.contains(12))
    }

    @Test
    fun `真实xls的周五1-2节应合并为一个冲突组`() {
        val data = fixtureCourses()

        val conflicts = ScheduleSelection.findConflicts(data)
        val fri12 = conflicts.filter { it.dayOfWeek == 5 && it.startSlot == 1 }

        assertEquals("周五1-2节应只有一个冲突组", 1, fri12.size)
        assertEquals(3, fri12[0].courses.size)
    }

    @Test
    fun `不重叠周次不识别为冲突`() {
        val data = ScheduleData(16, listOf(
            course("英语", "张老师", "英语-0001", "教室A", 2, listOf(1, 8)),
            course("日语", "李老师", "日语-0001", "教室B", 2, listOf(9, 16))
        ))
        assertTrue(ScheduleSelection.findConflicts(data).isEmpty())
    }

    @Test
    fun `同一课程不同教室识别为冲突`() {
        val data = ScheduleData(16, listOf(
            course("英语", "张老师", "英语-0001", "教学楼101", 2, listOf(1, 16)),
            course("英语", "张老师", "英语-0002", "教学楼102", 2, listOf(1, 16))
        ))
        val conflicts = ScheduleSelection.findConflicts(data)
        assertEquals(1, conflicts.size)
        assertEquals(2, conflicts[0].courses.size)
    }

    @Test
    fun `同课程同教师同教室视为同一门课不报冲突`() {
        val data = ScheduleData(16, listOf(
            course("高数", "李老师", "高数-0001", "教学楼201", 2, listOf(1, 16)),
            course("高数", "李老师", "高数-0002", "教学楼201", 2, listOf(1, 16))
        ))
        assertTrue(ScheduleSelection.findConflicts(data).isEmpty())
    }

    @Test
    fun `同课程同教师同教室不同节次不报冲突`() {
        val data = ScheduleData(16, listOf(
            course("高数", "李老师", "高数-0001", "教学楼201", 2, listOf(1, 16)).copy(startSlot = 1, endSlot = 2),
            course("高数", "李老师", "高数-0001", "教学楼201", 2, listOf(1, 16)).copy(startSlot = 3, endSlot = 4)
        ))
        assertTrue(ScheduleSelection.findConflicts(data).isEmpty())
    }

    @Test
    fun `同一时段同一课程拆成两条应合并为一个选项`() {
        // 教务系统把同一门课按周次拆成两条(教学班号带A后缀), 二者周次互补
        val data = ScheduleData(16, listOf(
            course("HTML5", "赵老师", "HTML5-0001", "教学楼306", 5, listOf(1, 2, 4)),
            course("HTML5", "赵老师", "HTML5-0001A", "教学楼306", 5, listOf(5, 6, 7, 8, 9, 10, 11, 12))
        ))

        val conflicts = ScheduleSelection.findConflicts(data)

        assertTrue("同一门课自己的两条记录不应相互冲突", conflicts.isEmpty())
    }

    @Test
    fun `同一时段两批独立冲突且周次不重叠应分为两组`() {
        val data = ScheduleData(16, listOf(
            course("英语", "张老师", "英语-0001", "教室A", 2, listOf(1, 8)),
            course("日语", "李老师", "日语-0001", "教室B", 2, listOf(1, 8)),
            course("法语", "王老师", "法语-0001", "教室C", 2, listOf(9, 16)),
            course("德语", "赵老师", "德语-0001", "教室D", 2, listOf(9, 16))
        ))

        val conflicts = ScheduleSelection.findConflicts(data)

        assertEquals(2, conflicts.size)
        val firstHalf = conflicts.first { it.courses.any { c -> c.name == "英语" } }
        assertTrue(firstHalf.weeks.all { it <= 8 })
        val secondHalf = conflicts.first { it.courses.any { c -> c.name == "法语" } }
        assertTrue(secondHalf.weeks.all { it >= 9 })
    }

    private fun course(
        name: String,
        teacher: String,
        teachingClass: String,
        classroom: String,
        dayOfWeek: Int,
        weeks: List<Int>
    ) = Course(
        name = name,
        teacher = teacher,
        teachingClass = teachingClass,
        classroom = classroom,
        dayOfWeek = dayOfWeek,
        startSlot = 1,
        endSlot = 2,
        weeks = weeks,
        color = "#FFFFFF"
    )
}
