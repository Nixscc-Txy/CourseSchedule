package com.example.courseschedule.data

import java.time.LocalTime

data class SlotTime(val startSlot: Int, val endSlot: Int, val startTime: LocalTime, val endTime: LocalTime)

/**
 * 作息表: 周视图左侧时间列和桌面小组件共用这一份定义, 不要在界面里另抄一套。
 */
object TimeUtils {
    val slotTimes = listOf(
        SlotTime(1, 2, LocalTime.of(8, 30), LocalTime.of(10, 5)),
        SlotTime(3, 4, LocalTime.of(10, 25), LocalTime.of(12, 0)),
        SlotTime(5, 6, LocalTime.of(14, 0), LocalTime.of(15, 35)),
        SlotTime(7, 8, LocalTime.of(15, 55), LocalTime.of(17, 30)),
        SlotTime(9, 10, LocalTime.of(19, 0), LocalTime.of(20, 35)),
        SlotTime(11, 12, LocalTime.of(20, 50), LocalTime.of(22, 25)),
    )

    /** "1-2" */
    fun slotLabel(slot: SlotTime): String = "${slot.startSlot}-${slot.endSlot}"

    /**
     * 课程的实际上课起止时间: 取与 [startSlot, endSlot] 重叠的首尾时段。
     * 跨多个时段的课 (如 1-4 节连上) 也能正确取到 08:30-12:00, 找不到时段则返回 null。
     */
    fun timesFor(startSlot: Int, endSlot: Int): Pair<LocalTime, LocalTime>? {
        val hit = slotTimes.filter { it.startSlot <= endSlot && it.endSlot >= startSlot }
        return if (hit.isEmpty()) null else hit.first().startTime to hit.last().endTime
    }
}
