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

/** 冲突涉及的某一个时段 */
data class ConflictSlot(val dayOfWeek: Int, val startSlot: Int, val endSlot: Int)

/**
 * 需要用户回答一次的一组平行选修课: 从其中选一门, 或者一门都不选。
 * [slots] 可能包含多个时段 (如周三 7-8 节和周五 1-2 节开的是同一批课)。
 */
data class ConflictCluster(
    val courses: List<Course>,
    val slots: List<ConflictSlot>,
    val weeks: List<Int>
)

object ScheduleSelection {
    private const val PREFS_NAME = "schedule_selection"
    private const val KEY_SIGNATURE = "schedule_signature"
    private const val KEY_SELECTED_KEYS = "selected_course_keys"

    /**
     * 找出同一天、节次重叠、且至少有一周重叠的课程组合, 按课程身份合并为冲突组。
     *
     * 同一时间段(同一节次窗口)里不同周次的子组合会合并成一个组, 避免同一个物理
     * 时段被拆成多个"需要重复选择"的弹窗条目; 同一门课的拆条记录(同身份)也只
     * 会作为一个可选项出现。
     */
    fun findConflicts(schedule: ScheduleData): List<CourseConflict> {
        val groups = mutableListOf<CourseConflict>()

        for (day in 1..7) {
            val dayCourses = schedule.courses.filter { it.dayOfWeek == day }
            if (dayCourses.size < 2) continue

            // 1) 按周累计"两个身份在重叠节次"的冲突边: (身份A, 身份B) -> 周次列表
            val edgeWeeks = linkedMapOf<Pair<String, String>, MutableList<Int>>()
            for (week in 1..schedule.totalWeeks) {
                val active = dayCourses.filter { week in it.weeks }.groupBy { it.selectionKey() }
                val keys = active.keys.toList()
                for (i in keys.indices) {
                    for (j in i + 1 until keys.size) {
                        val a = active.getValue(keys[i])
                        val b = active.getValue(keys[j])
                        val overlaps = a.any { first ->
                            b.any { second ->
                                first.startSlot <= second.endSlot &&
                                    second.startSlot <= first.endSlot
                            }
                        }
                        if (overlaps) {
                            val edge = if (keys[i] < keys[j]) keys[i] to keys[j] else keys[j] to keys[i]
                            edgeWeeks.getOrPut(edge) { mutableListOf() }.add(week)
                        }
                    }
                }
            }
            if (edgeWeeks.isEmpty()) continue

            // 2) 并查集: 有边的身份连通成一个冲突组 (共享同一物理时段)
            val parent = HashMap<String, String>()
            fun find(x: String): String {
                var v = x
                while (parent.getOrDefault(v, v) != v) v = parent.getValue(v)
                return v
            }
            fun union(a: String, b: String) {
                val ra = find(a)
                val rb = find(b)
                if (ra != rb) parent[ra] = rb
            }
            edgeWeeks.keys.forEach { (a, b) -> union(a, b) }

            // 3) 每个连通分量 -> 一个冲突组
            val componentWeeks = HashMap<String, MutableList<Int>>() // root -> 全部冲突周
            val componentKeys = HashMap<String, MutableList<String>>() // root -> 身份列表
            for ((edge, weeks) in edgeWeeks) {
                val root = find(edge.first)
                componentWeeks.getOrPut(root) { mutableListOf() }.addAll(weeks)
                val keys = componentKeys.getOrPut(root) { mutableListOf() }
                if (edge.first !in keys) keys.add(edge.first)
                if (edge.second !in keys) keys.add(edge.second)
            }

            for ((root, keys) in componentKeys) {
                val sortedKeys = keys.sorted()
                // 每个身份取一个代表记录 (同一天可能有多条同身份记录)
                val entries = dayCourses.groupBy { it.selectionKey() }
                val courses = sortedKeys.mapNotNull { key ->
                    entries[key]?.first()
                }
                if (courses.size < 2) continue
                val weeks = componentWeeks.getValue(root).distinct().sorted()
                groups.add(
                    CourseConflict(
                        dayOfWeek = day,
                        startSlot = courses.minOf { it.startSlot },
                        endSlot = courses.maxOf { it.endSlot },
                        courses = courses,
                        weeks = weeks
                    )
                )
            }
        }

        return groups.sortedWith(compareBy({ it.dayOfWeek }, { it.startSlot }, { it.courses.first().name }))
    }

    /**
     * 把 [findConflicts] 的结果按"共享同一门课"合并成若干独立的冲突簇。
     *
     * 班级课表里平行的选修(如英语/日语, 或本例的 HTML5/微信/游戏)往往在多个时段同时开,
     * 于是会生成多个可选项完全相同的冲突组。同一门课的身份键是全局唯一的 (一门课就是一
     * 门课, 它在哪几个时段上就哪几个时段上), 如果让用户逐组选择, 后一组的操作会把前一组
     * 的选择一起改掉, 界面上的单选也会莫名其妙地翻到别的选项。
     *
     * 合并后每门课只属于一个簇, 用户对每个簇只回答一次, 不可能自相矛盾。
     *
     * ponytail: 只按"是否有共同课程"连通。若出现 周三{A,B} 与 周五{B,C} 这种链式结构,
     * 会被并成一个簇, 用户无法表达"A+C"的组合。真实课表里平行选修的可选项通常完全相同,
     * 这种链式情况罕见; 真遇到再改成按簇内分组求解。
     */
    fun clusterConflicts(conflicts: List<CourseConflict>): List<ConflictCluster> {
        if (conflicts.isEmpty()) return emptyList()

        val parent = HashMap<String, String>()
        fun find(x: String): String {
            var v = x
            while (parent.getOrDefault(v, v) != v) v = parent.getValue(v)
            return v
        }
        fun union(a: String, b: String) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        for (conflict in conflicts) {
            val keys = conflict.courses.map { it.selectionKey() }
            keys.forEach { parent.getOrPut(it) { it } }
            keys.drop(1).forEach { union(keys[0], it) }
        }

        val byRoot = linkedMapOf<String, MutableList<CourseConflict>>()
        for (conflict in conflicts) {
            val root = find(conflict.courses.first().selectionKey())
            byRoot.getOrPut(root) { mutableListOf() }.add(conflict)
        }

        return byRoot.values.map { group ->
            ConflictCluster(
                courses = group.flatMap { it.courses }
                    .distinctBy { it.selectionKey() }
                    .sortedBy { it.name },
                slots = group.map { ConflictSlot(it.dayOfWeek, it.startSlot, it.endSlot) }
                    .distinct()
                    .sortedWith(compareBy({ it.dayOfWeek }, { it.startSlot })),
                weeks = group.flatMap { it.weeks }.distinct().sorted()
            )
        }.sortedWith(compareBy({ it.slots.first().dayOfWeek }, { it.slots.first().startSlot }))
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

    /** 清空选课结果 (清空课表时一并调用) */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
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
