package com.example.courseschedule.data

import java.time.LocalTime

data class SlotTime(val startSlot: Int, val endSlot: Int, val startTime: LocalTime, val endTime: LocalTime)

object TimeUtils {
    val slotTimes = listOf(
        SlotTime(1, 2, LocalTime.of(8, 30), LocalTime.of(10, 5)),
        SlotTime(3, 4, LocalTime.of(10, 25), LocalTime.of(12, 0)),
        SlotTime(5, 6, LocalTime.of(14, 0), LocalTime.of(15, 35)),
        SlotTime(7, 8, LocalTime.of(15, 55), LocalTime.of(17, 30)),
        SlotTime(9, 10, LocalTime.of(19, 0), LocalTime.of(20, 35)),
        SlotTime(11, 12, LocalTime.of(20, 50), LocalTime.of(22, 25)),
    )

    fun getTimeForSlot(startSlot: Int, endSlot: Int): SlotTime? {
        return slotTimes.find { it.startSlot == startSlot && it.endSlot == endSlot }
    }

    fun timeDisplay(slot: SlotTime): String {
        return "${slot.startTime} - ${slot.endTime}"
    }
}
