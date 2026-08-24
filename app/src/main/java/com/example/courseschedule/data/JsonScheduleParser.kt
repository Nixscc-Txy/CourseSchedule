package com.example.courseschedule.data

import com.example.courseschedule.model.Course
import com.example.courseschedule.model.ScheduleData
import org.json.JSONObject

/** schedule.json 格式解析 (与内置 assets 文件相同的格式) */
object JsonScheduleParser {

    fun parse(text: String): ScheduleData {
        val obj = JSONObject(text)
        val totalWeeks = obj.getInt("totalWeeks")
        val arr = obj.getJSONArray("courses")
        val courses = mutableListOf<Course>()
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

    fun toJson(data: ScheduleData): String {
        val obj = JSONObject()
        obj.put("totalWeeks", data.totalWeeks)
        val arr = org.json.JSONArray()
        for (c in data.courses) {
            val o = JSONObject()
            o.put("name", c.name)
            o.put("teacher", c.teacher)
            o.put("classroom", c.classroom)
            o.put("dayOfWeek", c.dayOfWeek)
            o.put("startSlot", c.startSlot)
            o.put("endSlot", c.endSlot)
            val weeks = org.json.JSONArray()
            c.weeks.forEach { weeks.put(it) }
            o.put("weeks", weeks)
            o.put("color", c.color)
            arr.put(o)
        }
        obj.put("courses", arr)
        return obj.toString(2)
    }
}
