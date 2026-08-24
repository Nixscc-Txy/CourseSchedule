package com.example.courseschedule.model

/** 课表数据：总周数 + 课程列表 */
data class ScheduleData(
    val totalWeeks: Int,
    val courses: List<Course>
)
