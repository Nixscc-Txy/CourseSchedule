package com.example.courseschedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.courseschedule.MainActivity
import com.example.courseschedule.R
import com.example.courseschedule.data.CourseRepository
import com.example.courseschedule.data.SettingsManager
import com.example.courseschedule.data.ScheduleSelection
import com.example.courseschedule.data.TimeUtils
import com.example.courseschedule.model.Course
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class CourseWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, manager, id)
    }

    companion object {
        private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        private val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = android.content.ComponentName(context, CourseWidgetReceiver::class.java)
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) updateWidget(context, manager, id)
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            try {
                val schedule = CourseRepository.load(context)
                val week = SettingsManager.getCurrentWeek(context, schedule.totalWeeks)
                val today = LocalDate.now()
                val todayDay = today.dayOfWeek.value
                val now = LocalTime.now()

                val visibleCourses = ScheduleSelection.visibleCourses(context, schedule)
                val upcoming = visibleCourses
                    .filter { it.dayOfWeek == todayDay && week in it.weeks }
                    .mapNotNull { c ->
                        TimeUtils.getTimeForSlot(c.startSlot, c.endSlot)?.let {
                            Triple(c, it.startTime, it.endTime)
                        }
                    }
                    .filter { it.third.isAfter(now) }
                    .sortedBy { it.second }
                    .take(2)

                val dateStr = "${today.monthValue}月${today.dayOfMonth}日"
                views.setTextViewText(R.id.widget_title,
                    "$dateStr ${dayNames.getOrElse(todayDay - 1) { "" }} · 第${week}周")

                if (upcoming.isEmpty()) {
                    views.setViewVisibility(R.id.course1_layout, android.view.View.GONE)
                    views.setViewVisibility(R.id.course2_layout, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
                    setCourse(views, upcoming.getOrNull(0),
                        R.id.course1_layout, R.id.course1_name, R.id.course1_info)
                    setCourse(views, upcoming.getOrNull(1),
                        R.id.course2_layout, R.id.course2_name, R.id.course2_info)
                }

                // Tomorrow early-8 check
                var tomorrowDay = todayDay + 1
                if (tomorrowDay > 7) tomorrowDay = 1
                val hasEarly8 = visibleCourses.any {
                    it.dayOfWeek == tomorrowDay && week in it.weeks && it.startSlot == 1
                }
                if (hasEarly8) {
                    views.setViewVisibility(R.id.widget_early8, android.view.View.VISIBLE)
                    views.setTextViewText(R.id.widget_early8, "明天有早八")
                } else {
                    views.setViewVisibility(R.id.widget_early8, android.view.View.GONE)
                }
            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_title, "课表加载失败")
            }

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_container, pending)

            manager.updateAppWidget(widgetId, views)
        }

        private fun setCourse(
            views: RemoteViews,
            item: Triple<Course, LocalTime, LocalTime>?,
            layoutId: Int, nameId: Int, infoId: Int
        ) {
            if (item != null) {
                val (course, start, end) = item
                views.setViewVisibility(layoutId, android.view.View.VISIBLE)
                views.setTextViewText(nameId, course.name)
                views.setTextViewText(infoId,
                    "${start.format(timeFmt)}-${end.format(timeFmt)}  ${course.classroom}")
                try {
                    val color = android.graphics.Color.parseColor(course.color)
                    views.setInt(layoutId, "setBackgroundColor", color)
                } catch (_: Exception) {
                    views.setInt(layoutId, "setBackgroundColor", 0xFFE0E0E0.toInt())
                }
            } else {
                views.setViewVisibility(layoutId, android.view.View.GONE)
            }
        }
    }
}
