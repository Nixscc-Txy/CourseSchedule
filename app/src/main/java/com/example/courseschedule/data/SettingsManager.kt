package com.example.courseschedule.data

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object SettingsManager {
    private const val PREFS_NAME = "schedule_settings"
    private const val KEY_START_DATE = "semester_start_date"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_WIDGET_THEME = "widget_theme"

    /** 小组件配色默认跟随主体 (App 的主题设置) */
    const val WIDGET_THEME_FOLLOW = "follow"

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

    /** 桌面小组件的配色: follow / light / dark */
    fun getWidgetThemeMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WIDGET_THEME, WIDGET_THEME_FOLLOW) ?: WIDGET_THEME_FOLLOW
    }

    fun setWidgetThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_WIDGET_THEME, mode).apply()
    }
}

/**
 * 小组件到底用不用深色。纯函数, 方便单测。
 *
 * - [widgetTheme] = follow 时看主体主题设置; 主体再是 system 就落到系统深色开关
 * - light / dark 则直接说了算, 与主体无关
 */
fun isDarkWidget(widgetTheme: String, appThemeMode: String, systemDark: Boolean): Boolean {
    return when (widgetTheme) {
        "light" -> false
        "dark" -> true
        else -> when (appThemeMode) {
            "light" -> false
            "dark" -> true
            else -> systemDark
        }
    }
}
