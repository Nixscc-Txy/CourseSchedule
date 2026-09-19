package com.example.courseschedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class TimeUtilsTest {

    @Test
    fun `标准时段取到对应起止时间`() {
        assertEquals(
            LocalTime.of(8, 30) to LocalTime.of(10, 5),
            TimeUtils.timesFor(1, 2)
        )
    }

    @Test
    fun `跨多个时段的连堂课取首尾时间`() {
        // 1-4 节连上: 08:30 上课, 12:00 下课。
        // 旧实现按 (startSlot, endSlot) 精确匹配, 这种情况返回 null, 小组件会把这门课整个丢掉。
        assertEquals(
            LocalTime.of(8, 30) to LocalTime.of(12, 0),
            TimeUtils.timesFor(1, 4)
        )
    }

    @Test
    fun `只占某个时段里的单节也能取到`() {
        assertEquals(
            LocalTime.of(10, 25) to LocalTime.of(12, 0),
            TimeUtils.timesFor(3, 3)
        )
    }

    @Test
    fun `没有对应作息时返回 null`() {
        assertNull(TimeUtils.timesFor(21, 22))
    }
}
