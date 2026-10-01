package com.example.courseschedule.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 教学楼 / 教室号的拆分与拼回: 编辑弹窗用它把教室一个字段拆成两个输入框 */
class ClassroomTextTest {

    @Test
    fun `常见教室拆成教学楼与教室号`() {
        assertEquals("致远楼" to "213", splitClassroom("致远楼213"))
        assertEquals("教学楼" to "412", splitClassroom("教学楼412"))
        assertEquals("知行楼" to "201", splitClassroom("知行楼201"))
    }

    @Test
    fun `前面带空格也拆得对`() {
        assertEquals("致远楼" to "213", splitClassroom("  致远楼213  "))
    }

    @Test
    fun `纯数字算教室号没有教学楼`() {
        assertEquals("" to "213", splitClassroom("213"))
    }

    @Test
    fun `没有数字时整串算教学楼`() {
        assertEquals("未排" to "", splitClassroom("未排"))
        assertEquals("致远楼" to "", splitClassroom("致远楼"))
    }

    @Test
    fun `字母开头的教室`() {
        assertEquals("A" to "101", splitClassroom("A101"))
    }

    @Test
    fun `空字符串不炸`() {
        assertEquals("" to "", splitClassroom(""))
        assertEquals("" to "", splitClassroom("   "))
    }

    @Test
    fun `拼回时去掉两端空格`() {
        assertEquals("致远楼213", joinClassroom("致远楼", "213"))
        assertEquals("致远楼213", joinClassroom(" 致远楼 ", " 213 "))
        assertEquals("致远楼", joinClassroom("致远楼", ""))
        assertEquals("", joinClassroom("", ""))
    }

    @Test
    fun `拆分再拼回等于原值`() {
        for (raw in listOf("致远楼213", "教学楼412", "213", "未排", "A101")) {
            val (building, room) = splitClassroom(raw)
            assertEquals(raw, joinClassroom(building, room))
        }
    }
}
