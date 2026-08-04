package com.example.courseschedule.data

import android.content.Context
import com.example.courseschedule.model.Course
import org.json.JSONArray
import org.json.JSONObject

object CourseRepository {

    data class ScheduleData(
        val totalWeeks: Int,
        val courses: List<Course>
    )

    fun load(context: Context): ScheduleData {
        val json = context.assets.open("schedule.json").bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        val totalWeeks = obj.getInt("totalWeeks")
        val courses = mutableListOf<Course>()
        val arr = obj.getJSONArray("courses")
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val weeksArr = c.getJSONArray("weeks")
            val weeks = (0 until weeksArr.length()).map { weeksArr.getInt(it) }
            courses.add(
                Course(
                    name = c.getString("name"),
                    teacher = c.optString("teacher", ""),
                    classroom = c.optString("classroom", ""),
                    dayOfWeek = c.getInt("dayOfWeek"),
                    startSlot = c.getInt("startSlot"),
                    endSlot = c.getInt("endSlot"),
                    weeks = weeks,
                    color = c.optString("color", "#B0BEC5")
                )
            )
        }
        return ScheduleData(totalWeeks, courses)
    }
}
