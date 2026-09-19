package com.example.courseschedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.courseschedule.MainActivity
import com.example.courseschedule.R
import com.example.courseschedule.data.CourseRepository
import com.example.courseschedule.data.ScheduleSelection
import com.example.courseschedule.data.SettingsManager
import com.example.courseschedule.data.TimeUtils
import com.example.courseschedule.model.Course
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CourseWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context, manager)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_DAY_ROLLOVER) refresh(context)
    }

    companion object {
        private const val REQ_DAY_ROLLOVER = 1001
        private const val ACTION_DAY_ROLLOVER = "com.example.courseschedule.DAY_ROLLOVER"

        private val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        fun refresh(context: Context) {
            updateAll(context, AppWidgetManager.getInstance(context))
        }

        private fun updateAll(context: Context, manager: AppWidgetManager) {
            val ids = manager.getAppWidgetIds(ComponentName(context, CourseWidgetReceiver::class.java))
            if (ids.isEmpty()) return

            val views = buildViews(context)
            for (id in ids) manager.updateAppWidget(id, views)

            scheduleDayRollover(context)
        }

        /**
         * 在下一个零点后刷新一次。
         * 小组件的 updatePeriodMillis 最长 30 分钟才轮到一次, 光靠它过了午夜还会显示昨天的课;
         * 用非精确闹钟 (不需要 SCHEDULE_EXACT_ALARM 权限) 在跨天时主动刷一次并续订下一次。
         */
        private fun scheduleDayRollover(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val nextMidnight = LocalDate.now().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli() + 60_000

            val pending = PendingIntent.getBroadcast(
                context,
                REQ_DAY_ROLLOVER,
                Intent(context, CourseWidgetReceiver::class.java).setAction(ACTION_DAY_ROLLOVER),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.set(AlarmManager.RTC, nextMidnight, pending)
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            try {
                val schedule = CourseRepository.load(context)
                val today = LocalDate.now()
                val todayDay = today.dayOfWeek.value
                val week = SettingsManager.getWeekFor(context, today, schedule.totalWeeks)
                val now = LocalTime.now()

                val visibleCourses = ScheduleSelection.visibleCourses(context, schedule)
                val upcoming = visibleCourses
                    .filter { it.dayOfWeek == todayDay && week in it.weeks }
                    .mapNotNull { course ->
                        // 用重叠匹配, 跨多个时段的课 (如 1-4 节连上) 也不会被丢掉
                        TimeUtils.timesFor(course.startSlot, course.endSlot)
                            ?.let { (start, end) -> Triple(course, start, end) }
                    }
                    .filter { it.third.isAfter(now) }
                    .sortedBy { it.second }
                    .take(2)

                val dateStr = "${today.monthValue}月${today.dayOfMonth}日"
                views.setTextViewText(
                    R.id.widget_title,
                    "$dateStr ${dayNames.getOrElse(todayDay - 1) { "" }} · 第${week}周"
                )

                if (upcoming.isEmpty()) {
                    views.setViewVisibility(R.id.course1_layout, View.GONE)
                    views.setViewVisibility(R.id.course2_layout, View.GONE)
                    views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_empty, View.GONE)
                    setCourse(views, upcoming.getOrNull(0), R.id.course1_layout, R.id.course1_name, R.id.course1_info)
                    setCourse(views, upcoming.getOrNull(1), R.id.course2_layout, R.id.course2_name, R.id.course2_info)
                }

                // 明天的早八: 星期天时"明天"已经属于下一周了
                val tomorrow = today.plusDays(1)
                val tomorrowWeek = SettingsManager.getWeekFor(context, tomorrow, schedule.totalWeeks)
                val hasEarly8 = visibleCourses.any {
                    it.dayOfWeek == tomorrow.dayOfWeek.value &&
                        tomorrowWeek in it.weeks &&
                        it.startSlot <= 2
                }
                if (hasEarly8) {
                    views.setViewVisibility(R.id.widget_early8, View.VISIBLE)
                    views.setTextViewText(R.id.widget_early8, "明天有早八")
                } else {
                    views.setViewVisibility(R.id.widget_early8, View.GONE)
                }
            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_title, "课表加载失败")
            }

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pending)

            return views
        }

        private fun setCourse(
            views: RemoteViews,
            item: Triple<Course, LocalTime, LocalTime>?,
            layoutId: Int, nameId: Int, infoId: Int
        ) {
            if (item == null) {
                views.setViewVisibility(layoutId, View.GONE)
                return
            }
            val (course, start, end) = item
            views.setViewVisibility(layoutId, View.VISIBLE)
            views.setTextViewText(nameId, course.name)
            views.setTextViewText(infoId, "$start-$end  ${course.classroom}")
            try {
                val color = android.graphics.Color.parseColor(course.color)
                views.setInt(layoutId, "setBackgroundColor", color)
            } catch (_: Exception) {
                views.setInt(layoutId, "setBackgroundColor", 0xFFE0E0E0.toInt())
            }
        }
    }
}
