package com.example.courseschedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import androidx.core.graphics.ColorUtils
import android.widget.RemoteViews
import com.example.courseschedule.MainActivity
import com.example.courseschedule.R
import com.example.courseschedule.data.CourseRepository
import com.example.courseschedule.data.ScheduleSelection
import com.example.courseschedule.data.SettingsManager
import com.example.courseschedule.data.TimeUtils
import com.example.courseschedule.data.isDarkWidget
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

            // 配色先套上: 默认跟随主体主题, 也能在设置里单独指定浅色/深色。
            // 放在 try 外面, 课表加载失败时至少配色是对的。
            val palette = paletteFor(context)
            views.setInt(R.id.widget_container, "setBackgroundResource", palette.backgroundRes)
            views.setTextColor(R.id.widget_title, palette.title)
            views.setTextColor(R.id.widget_empty, palette.empty)
            views.setTextColor(R.id.widget_early8, palette.early8)

            try {
                val schedule = CourseRepository.load(context)
                val today = LocalDate.now()
                val todayDay = today.dayOfWeek.value
                val week = SettingsManager.getWeekFor(context, today, schedule.totalWeeks)
                val now = LocalTime.now()

                val visibleCourses = ScheduleSelection.visibleCourses(context, schedule)

                // 今天还没上完的课 (跨多个时段的连堂课用重叠匹配, 不会被丢掉)
                val upcoming = coursesOn(visibleCourses, todayDay, week)
                    .filter { it.third.isAfter(now) }
                    .take(2)

                val dateStr = "${today.monthValue}月${today.dayOfMonth}日"
                views.setTextViewText(
                    R.id.widget_title,
                    "$dateStr ${dayNames.getOrElse(todayDay - 1) { "" }} · 第${week}周"
                )

                // 明天的课: 今天上完了就顶上来显示, 否则只用来判断"明天有早八"。
                // 星期天时"明天"已经属于下一周了, 用 getWeekFor 算它落在第几周。
                val tomorrow = today.plusDays(1)
                val tomorrowDay = tomorrow.dayOfWeek.value
                val tomorrowCourses = coursesOn(
                    visibleCourses, tomorrowDay,
                    SettingsManager.getWeekFor(context, tomorrow, schedule.totalWeeks)
                ).take(2)

                if (upcoming.isEmpty()) {
                    // 当天没课或已经上完: 说清楚状态, 并把明天的前两节顶上来
                    views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_empty,
                        if (tomorrowCourses.isEmpty()) "当天无更多课程"
                        else "当天无更多课程 · 明天（${dayNames.getOrElse(tomorrowDay - 1) { "" }}）"
                    )
                    // 明天那两节已经显示在下面了, 不再重复提醒早八
                    views.setViewVisibility(R.id.widget_early8, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_empty, View.GONE)
                    val first = tomorrowCourses.firstOrNull()
                    if (first != null && first.first.startSlot <= 2) {
                        views.setViewVisibility(R.id.widget_early8, View.VISIBLE)
                        views.setTextViewText(R.id.widget_early8, "明天有早八")
                    } else {
                        views.setViewVisibility(R.id.widget_early8, View.GONE)
                    }
                }

                val shown = if (upcoming.isEmpty()) tomorrowCourses else upcoming
                setCourse(views, shown.getOrNull(0), R.id.course1_layout, R.id.course1_name, R.id.course1_info, palette)
                setCourse(views, shown.getOrNull(1), R.id.course2_layout, R.id.course2_name, R.id.course2_info, palette)
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

        /**
         * 某一天要上的课, 按开始时间排序, 每门带上实际起止时间。
         * 用重叠匹配, 跨多个时段的课 (如 1-4 节连上) 也不会被丢掉。
         */
        private fun coursesOn(
            courses: List<Course>, dayOfWeek: Int, week: Int
        ): List<Triple<Course, LocalTime, LocalTime>> =
            courses
                .filter { it.dayOfWeek == dayOfWeek && week in it.weeks }
                .mapNotNull { course ->
                    TimeUtils.timesFor(course.startSlot, course.endSlot)
                        ?.let { (start, end) -> Triple(course, start, end) }
                }
                .sortedBy { it.second }

        /** 小组件的一套配色 */
        private class Palette(
            val dark: Boolean,
            val backgroundRes: Int,
            val title: Int,
            val empty: Int,
            val early8: Int,
            val blockName: Int,
            val blockInfo: Int
        )

        /**
         * 小组件用哪套配色。
         * follow (默认) 时跟主体主题走; 主体若是 system, 再落到系统深色开关上。
         */
        private fun paletteFor(context: Context): Palette {
            val systemDark = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dark = isDarkWidget(
                SettingsManager.getWidgetThemeMode(context),
                SettingsManager.getThemeMode(context),
                systemDark
            )
            return if (dark) {
                Palette(
                    dark = true,
                    backgroundRes = R.drawable.widget_background_dark,
                    title = 0xFFE0E0E0.toInt(),
                    empty = 0xFF888888.toInt(),
                    early8 = 0xFFFFAB40.toInt(),
                    blockName = 0xFFF2F2F7.toInt(),
                    blockInfo = 0xFFB8B8BE.toInt()
                )
            } else {
                // 浅色下课程块保留课程本身的柔和色, 文字换成深色 (和 App 里 CourseCell 一个思路)
                Palette(
                    dark = false,
                    backgroundRes = R.drawable.widget_background_light,
                    title = 0xFF1C1B1F.toInt(),
                    empty = 0xFF6E6E73.toInt(),
                    early8 = 0xFFE65100.toInt(),
                    blockName = 0xFF1C1B1F.toInt(),
                    blockInfo = 0xFF6E6E73.toInt()
                )
            }
        }

        private fun setCourse(
            views: RemoteViews,
            item: Triple<Course, LocalTime, LocalTime>?,
            layoutId: Int, nameId: Int, infoId: Int,
            palette: Palette
        ) {
            if (item == null) {
                views.setViewVisibility(layoutId, View.GONE)
                return
            }
            val (course, start, end) = item
            views.setViewVisibility(layoutId, View.VISIBLE)
            views.setTextViewText(nameId, course.name)
            views.setTextColor(nameId, palette.blockName)
            views.setTextColor(infoId, palette.blockInfo)
            // 标注是第几节, 再跟实际上课时间和教室
            val slot = if (course.startSlot == course.endSlot) {
                "第${course.startSlot}节"
            } else {
                "第${course.startSlot}-${course.endSlot}节"
            }
            val room = course.classroom.trim()
            views.setTextViewText(
                infoId,
                if (room.isEmpty()) "$slot $start-$end" else "$slot $start-$end  $room"
            )
            val base = try {
                android.graphics.Color.parseColor(course.color)
            } catch (_: Exception) {
                0xFFB0BEC5.toInt()
            }
            // 深色底下把课程色往深里压, 不然浅色块配浅色字看不清
            val blockColor =
                if (palette.dark) ColorUtils.blendARGB(base, 0xFF1C1C1E.toInt(), 0.55f) else base
            views.setInt(layoutId, "setBackgroundColor", blockColor)
        }
    }
}
