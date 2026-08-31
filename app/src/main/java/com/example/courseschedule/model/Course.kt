package com.example.courseschedule.model

data class Course(
    val name: String,
    val teacher: String,
    val teachingClass: String = "",
    val classroom: String,
    val dayOfWeek: Int,      // 1=周一 ... 7=周日
    val startSlot: Int,      // 1-12
    val endSlot: Int,        // 1-12
    val weeks: List<Int>,    // 上课周次列表
    val color: String,       // "#RRGGBB"
    val isOnline: Boolean = false
) {
    /**
     * 课程身份键: 课程名 + 教师 + 教室。
     *
     * 同一门课可能因周次/节次不同被导出为多条记录 (例如教学班号带 A/B 后缀的拆条,
     * 或不同周次换教室), 用身份键将其合并为同一个可选项, 选择一门课后该课的所有
     * 记录都保留。不同课程名、教师或教室视为不同选项。
     */
    fun selectionKey(): String {
        return listOf(name, teacher, classroom).joinToString("\u001F")
    }
}
