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
