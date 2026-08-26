package com.example.courseschedule.data

import android.content.Context
import com.example.courseschedule.model.Course
import com.example.courseschedule.model.ScheduleData

/** A group of parallel classes that cannot be taken at the same time. */
data class CourseConflict(
    val dayOfWeek: Int,
    val startSlot: Int,
    val endSlot: Int,
    val courses: List<Course>,
    val weeks: List<Int>
)

object ScheduleSelection {
    private const val PREFS_NAME = "schedule_selection"
    private const val KEY_SIGNATURE = "schedule_signature"
    private const val KEY_SELECTED_KEYS = "selected_course_keys"

    fun findConflicts(schedule: ScheduleData): List<CourseConflict> {
        val weeksBySignature = linkedMapOf<String, MutableList<Int>>()
        val conflictBySignature = linkedMapOf<String, CourseConflict>()

        for (day in 1..7) {
            for (week in 1..schedule.totalWeeks) {
                val nodes = schedule.courses
                    .filter { it.dayOfWeek == day && week in it.weeks }
                    .groupBy { it.selectionKey() }
                    .map { (key, courses) -> key to courses }
                val conflicting = mutableListOf<Pair<Int, Int>>()

                for (left in nodes.indices) {
                    for (right in left + 1 until nodes.size) {
                        val overlaps = nodes[left].second.any { first ->
                            nodes[right].second.any { second ->
                                first.startSlot <= second.endSlot &&
                                    second.startSlot <= first.endSlot
                            }
                        }
                        if (overlaps) conflicting.add(left to right)
                    }
                }

                if (conflicting.isEmpty()) continue

                val allKeys = conflicting.flatMap { listOf(nodes[it.first].first, nodes[it.second].first) }
                    .distinct()
                    .sorted()
                val courses = allKeys.mapNotNull { key -> nodes.firstOrNull { it.first == key }?.second?.first() }
                val startSlot = courses.minOf { it.startSlot }
                val endSlot = courses.maxOf { it.endSlot }
                val signature = "$day|$startSlot|$endSlot|${allKeys.joinToString("|")}"
                weeksBySignature.getOrPut(signature) { mutableListOf() }.add(week)
                conflictBySignature.putIfAbsent(
                    signature,
                    CourseConflict(day, startSlot, endSlot, courses, emptyList())
                )
            }
        }

        return conflictBySignature.map { (signature, conflict) ->
            conflict.copy(weeks = weeksBySignature.getValue(signature).distinct().sorted())
        }.sortedWith(compareBy({ it.dayOfWeek }, { it.startSlot }))
    }

    fun visibleCourses(context: Context, schedule: ScheduleData): List<Course> {
        val selected = loadSelectedKeys(context, schedule) ?: return schedule.courses
        return schedule.courses.filter { it.selectionKey() in selected }
    }

    fun loadSelectedKeys(context: Context, schedule: ScheduleData): Set<String>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_SIGNATURE, null) != signature(schedule)) return null
        return prefs.getStringSet(KEY_SELECTED_KEYS, null)?.toSet()
    }

    fun saveSelectedKeys(context: Context, schedule: ScheduleData, selectedKeys: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SIGNATURE, signature(schedule))
            .putStringSet(KEY_SELECTED_KEYS, selectedKeys)
            .apply()
    }

    private fun signature(schedule: ScheduleData): String {
        return schedule.courses
            .map {
                listOf(it.selectionKey(), it.dayOfWeek, it.startSlot, it.endSlot, it.weeks)
                    .joinToString("|")
            }
            .sorted()
            .joinToString("\n")
            .hashCode()
            .toString()
    }
}
