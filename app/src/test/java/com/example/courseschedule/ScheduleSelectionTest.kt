package com.example.courseschedule.data

import com.example.courseschedule.model.Course
import com.example.courseschedule.model.ScheduleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleSelectionTest {
    @Test
    fun `同一时段不同教学班识别为冲突`() {
        val data = ScheduleData(16, listOf(
            course("英语", "张老师", "英语-0001", listOf(1, 2)),
            course("日语", "李老师", "日语-0001", listOf(1, 2))
        ))

        val conflicts = ScheduleSelection.findConflicts(data)

        assertEquals(1, conflicts.size)
        assertEquals(setOf("英语", "日语"), conflicts[0].courses.map { it.name }.toSet())
    }

    @Test
    fun `不重叠周次不识别为冲突`() {
        val data = ScheduleData(16, listOf(
            course("英语", "张老师", "英语-0001", listOf(1, 3)),
            course("日语", "李老师", "日语-0001", listOf(2, 4))
        ))

        assertTrue(ScheduleSelection.findConflicts(data).isEmpty())
    }

    @Test
    fun `节次部分重叠也识别为冲突`() {
        val data = ScheduleData(16, listOf(
            course("英语", "张老师", "英语-0001", listOf(1)).copy(endSlot = 4),
            course("日语", "李老师", "日语-0001", listOf(1)).copy(startSlot = 3)
        ))

        assertEquals(1, ScheduleSelection.findConflicts(data).size)
    }

    @Test
    fun `同一时段但周次完全错开不要求选择`() {
        val data = ScheduleData(16, listOf(
            course("英语", "张老师", "英语-0001", (1..8).toList()),
            course("日语", "李老师", "日语-0001", (9..16).toList())
        ))

        assertTrue(ScheduleSelection.findConflicts(data).isEmpty())
    }

    @Test
    fun `同一批选修在多天同一时段合并成一个簇`() {
        // 回归: 班级课表里 HTML5/微信/游戏 三个平行选修在周三 7-8 节和周五 1-2 节都开。
        // 以前按"每组单独回答"处理, 在第 2 组点"我都不上"会把第 1 组选的 HTML5 一起删掉,
        // 第 1 组的单选还会莫名其妙翻成"都不上"。合并成一个簇后每门课只属于一个簇。
        val data = ScheduleData(16, listOf(
            slot("HTML5", day = 3, startSlot = 7, endSlot = 8),
            slot("微信", day = 3, startSlot = 7, endSlot = 8),
            slot("游戏", day = 3, startSlot = 7, endSlot = 8),
            slot("HTML5", day = 5, startSlot = 1, endSlot = 2),
            slot("微信", day = 5, startSlot = 1, endSlot = 2),
            slot("游戏", day = 5, startSlot = 1, endSlot = 2)
        ))

        assertEquals(2, ScheduleSelection.findConflicts(data).size)

        val clusters = ScheduleSelection.clusterConflicts(ScheduleSelection.findConflicts(data))

        assertEquals(1, clusters.size)
        assertEquals(setOf("HTML5", "微信", "游戏"), clusters[0].courses.map { it.name }.toSet())
        assertEquals(
            listOf(3 to 7, 5 to 1),
            clusters[0].slots.map { it.dayOfWeek to it.startSlot }
        )
    }

    @Test
    fun `可选项没有交集的冲突保持独立`() {
        val data = ScheduleData(16, listOf(
            slot("英语", day = 1, startSlot = 1, endSlot = 2),
            slot("日语", day = 1, startSlot = 1, endSlot = 2),
            slot("体育A", day = 4, startSlot = 3, endSlot = 4),
            slot("体育B", day = 4, startSlot = 3, endSlot = 4)
        ))

        val clusters = ScheduleSelection.clusterConflicts(ScheduleSelection.findConflicts(data))

        assertEquals(2, clusters.size)
        assertEquals(
            listOf(setOf("英语", "日语"), setOf("体育A", "体育B")),
            clusters.map { c -> c.courses.map { it.name }.toSet() }
        )
    }

    @Test
    fun `没有冲突时簇列表为空`() {
        val data = ScheduleData(16, listOf(slot("英语", day = 1, startSlot = 1, endSlot = 2)))

        assertTrue(ScheduleSelection.clusterConflicts(ScheduleSelection.findConflicts(data)).isEmpty())
    }

    private fun slot(name: String, day: Int, startSlot: Int, endSlot: Int) = Course(
        name = name,
        teacher = "老师",
        teachingClass = "$name-0001",
        classroom = "教室",
        dayOfWeek = day,
        startSlot = startSlot,
        endSlot = endSlot,
        weeks = (1..16).toList(),
        color = "#FFFFFF"
    )

    private fun course(
        name: String,
        teacher: String,
        teachingClass: String,
        weeks: List<Int>
    ) = Course(
        name = name,
        teacher = teacher,
        teachingClass = teachingClass,
        classroom = "教室",
        dayOfWeek = 2,
        startSlot = 1,
        endSlot = 2,
        weeks = weeks,
        color = "#FFFFFF"
    )
}
