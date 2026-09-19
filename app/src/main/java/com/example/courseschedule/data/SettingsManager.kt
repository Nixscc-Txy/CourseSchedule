package com.example.courseschedule.data

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object SettingsManager {
    private const val PREFS_NAME = "schedule_settings"
    private const val KEY_START_DATE = "semester_start_date"
    private const val KEY_THEME_MODE = "theme_mode"

    fun getStartDate(context: Context): LocalDate? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val str = prefs.getString(KEY_START_DATE, null) ?: return null
        return try { LocalDate.parse(str) } catch (_: Exception) { null }
    }

    fun setStartDate(context: Context, date: LocalDate) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_START_DATE, date.toString()).apply()
    }

    fun getCurrentWeek(context: Context, totalWeeks: Int): Int {
        return getWeekFor(context, LocalDate.now(), totalWeeks)
    }

    /** 指定日期落在第几周 (小组件算"明天"的课表时也用它) */
    fun getWeekFor(context: Context, date: LocalDate, totalWeeks: Int): Int {
        val start = getStartDate(context) ?: return 1
        val days = ChronoUnit.DAYS.between(start, date)
        return ((days / 7).toInt() + 1).coerceIn(1, totalWeeks)
    }

    fun getThemeMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, "system") ?: "system"
    }

    fun setThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_MODE, mode).apply()
    }
}
