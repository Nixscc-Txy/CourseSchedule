package com.example.courseschedule.data

import android.content.Context
import com.example.courseschedule.model.ScheduleData

object CourseRepository {

    /** 优先加载"导入的课表", 没有则回退到内置 assets 课表 */
    fun load(context: Context): ScheduleData {
        val imported = ScheduleImporter.readImported(context)
        if (imported != null) {
            return try {
                JsonScheduleParser.parse(imported)
            } catch (_: Exception) {
                loadFromAssets(context)
            }
        }
        return loadFromAssets(context)
    }

    private fun loadFromAssets(context: Context): ScheduleData {
        return try {
            val json = context.assets.open("schedule.json").bufferedReader().use { it.readText() }
            JsonScheduleParser.parse(json)
        } catch (_: Exception) {
            ScheduleData(16, emptyList())
        }
    }
}
