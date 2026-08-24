package com.example.courseschedule.data

import com.example.courseschedule.model.Course
import com.example.courseschedule.model.ScheduleData

/**
 * 教务系统"矩阵式"课表解析器。
 *
 * 网格结构 (与 tools/matrix_xls_to_json.py 对应):
 *   - 行 2..6 为时间段: 2=1-2节, 3=3-4节, 4=5-6节, 5=7-8节, 6=9-10节
 *   - 列 2..8 为星期一..星期日 (列索引 - 1 = dayOfWeek)
 *   - 单元格内可含多门课程, 换行分隔, 每条格式:
 *      课程名/(节次)周次/校区 教室/教师/教学班/学分
 *      例如: Java程序设计/(1-2节)1-16周/中心校区 教学楼412/李老师/Java程序设计-0005/4.0
 */
object MatrixScheduleParser {

    /** 柔和色板 (避免鲜艳刺眼; 深色模式下 UI 还会自动降亮) */
    private val COLORS = listOf(
        "#AED9F0", "#F9D9B8", "#BDE8D0", "#FAD9A8", "#E4CEF8",
        "#9FD4F5", "#F7E7B0", "#D6EEC2", "#C6ECE3", "#F5C8D6",
        "#F4E2D1", "#DAE7F0", "#F2CFC2", "#CCE8B8"
    )

    /** 行索引 -> 节次区间 */
    private val ROW_SLOTS = mapOf(
        2 to (1 to 2), 3 to (3 to 4), 4 to (5 to 6), 5 to (7 to 8), 6 to (9 to 10)
    )

    private val SLOT_REGEX = Regex("""\((\d+)(?:-(\d+))?节?\)""")
    private val WEEK_RANGE_REGEX = Regex("""(\d+)[-—](\d+)""")
    private val CAMPUS_REGEX = Regex("""^\S*校区\s*""")

    fun parse(grid: List<List<String>>): ScheduleData {
        val entries = mutableListOf<Course>()
        for ((rowIdx, row) in grid.withIndex()) {
            val fallback = ROW_SLOTS[rowIdx] ?: continue
            for (col in 2 until row.size) {
                val dayOfWeek = col - 1
                val cell = row[col]
                if (cell.isBlank()) continue
                for (line in cell.split('\n')) {
                    parseEntry(line, dayOfWeek, fallback)?.let { entries.add(it) }
                }
            }
        }

        // 同一课程同一颜色
        val colorOf = HashMap<String, String>()
        for (e in entries) colorOf.putIfAbsent(e.name, COLORS[colorOf.size % COLORS.size])

        val courses = entries.map { e ->
            Course(
                name = e.name,
                teacher = e.teacher,
                classroom = e.classroom,
                dayOfWeek = e.dayOfWeek,
                startSlot = e.startSlot,
                endSlot = e.endSlot,
                weeks = e.weeks,
                color = colorOf.getValue(e.name)
            )
        }

        val maxWeek = courses.flatMap { it.weeks }.maxOrNull() ?: 16
        return ScheduleData(maxWeek, courses)
    }

    /**
     * 解析周次字符串, 支持:
     *   1-16周 / 1-2周,4周 / 10-12周(双),13-16周 / 1-15周(单) / 13周
     */
    fun parseWeeks(text: String?): List<Int> {
        if (text.isNullOrBlank()) return emptyList()
        val t = text.trim().replace("周", "").replace(" ", "")
        val weeks = sortedSetOf<Int>()
        for (raw in t.split(',', '，')) {
            val part = raw.trim()
            if (part.isEmpty()) continue
            val oddOnly = part.contains("单")
            val evenOnly = part.contains("双")
            val clean = part.replace("(单)", "").replace("(双)", "")
                .replace("单", "").replace("双", "").trim()
            val m = WEEK_RANGE_REGEX.find(clean)
            if (m != null) {
                val start = m.groupValues[1].toInt()
                val end = m.groupValues[2].toInt()
                for (w in start..end) {
                    if (oddOnly && w % 2 == 0) continue
                    if (evenOnly && w % 2 == 1) continue
                    weeks.add(w)
                }
            } else {
                clean.toIntOrNull()?.let { weeks.add(it) }
            }
        }
        return weeks.toList()
    }

    /** 解析单条课程字符串; 无法解析返回 null */
    fun parseEntry(text: String, dayOfWeek: Int, fallback: Pair<Int, Int>): Course? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val parts = t.split('/')
        if (parts.size < 4) return null

        val name = parts[0].trim()

        val startSlot: Int
        val endSlot: Int
        val weekText: String
        val m = SLOT_REGEX.find(parts[1])
        if (m != null) {
            startSlot = m.groupValues[1].toInt()
            endSlot = m.groupValues.getOrNull(2)?.toIntOrNull() ?: startSlot
            weekText = parts[1].substring(m.range.last + 1)
        } else {
            startSlot = fallback.first
            endSlot = fallback.second
            weekText = parts[1]
        }

        val weeks = parseWeeks(weekText)
        if (weeks.isEmpty()) return null

        // 地点: "中心校区 教学楼213" -> "教学楼213"
        val classroom = parts[2].trim().replace(CAMPUS_REGEX, "")
        val teacher = if (parts.size > 3) parts[3].trim() else ""

        return Course(
            name = name,
            teacher = teacher,
            classroom = classroom,
            dayOfWeek = dayOfWeek,
            startSlot = startSlot,
            endSlot = endSlot,
            weeks = weeks,
            color = ""
        )
    }
}
