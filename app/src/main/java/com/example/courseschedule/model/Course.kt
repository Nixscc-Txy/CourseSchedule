package com.example.courseschedule.model

data class Course(
    val name: String,
    val teacher: String,
    val classroom: String,
    val dayOfWeek: Int,      // 1=周一 ... 7=周日
    val startSlot: Int,      // 1-12
    val endSlot: Int,        // 1-12
    val weeks: List<Int>,    // 上课周次列表
    val color: String,       // "#RRGGBB"
    val isOnline: Boolean = false
)
