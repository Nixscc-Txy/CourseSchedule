package com.example.courseschedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.model.Course
import java.time.LocalDate

@Composable
fun WeekViewScreen(
    courses: List<Course>,
    totalWeeks: Int,
    currentWeek: Int,
    startDate: LocalDate?,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    val colors = MaterialTheme.colorScheme
    val today = java.time.LocalDate.now()
    val todayDay = today.dayOfWeek.value // 1=Mon..7=Sun

    // Calculate dates for each day of the current week
    val weekDates: List<LocalDate> = remember(currentWeek, startDate) {
        if (startDate != null) {
            val monday = startDate.plusDays(((currentWeek - 1) * 7).toLong())
            (0..6).map { monday.plusDays(it.toLong()) }
        } else {
            emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        WeekSelector(
            currentWeek = currentWeek,
            totalWeeks = totalWeeks,
            onPrev = onPrevWeek,
            onNext = onNextWeek,
            onSettings = onOpenSettings
        )

        // 课表整体放在一张圆角白色卡片里, 浮在浅灰背景上 (iOS 分组风格)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
            ) {
                DayHeaderRow(todayDay, weekDates)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    val timeBlocks = listOf(
                        "1-2" to 1,
                        "3-4" to 3,
                        "5-6" to 5,
                        "7-8" to 7,
                        "9-10" to 9,
                        "11-12" to 11
                    )

                    for ((label, slotStart) in timeBlocks) {
                        TimeSlotRow(
                            timeLabel = label,
                            slotStart = slotStart,
                            slotEnd = slotStart + 1,
                            courses = courses,
                            todayDay = todayDay,
                            onCourseClick = { selectedCourse = it }
                        )
                    }
                }
            }
        }
    }

    selectedCourse?.let { course ->
        val weekText = formatWeeks(course.weeks)
        val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val dayName = dayNames.getOrElse(course.dayOfWeek - 1) { "未知" }
        val timeText = "$dayName 第${course.startSlot}-${course.endSlot}节"

        CourseDetailDialog(
            courseName = course.name,
            teacher = course.teacher,
            classroom = course.classroom,
            timeSlot = timeText,
            weeks = weekText,
            onDismiss = { selectedCourse = null }
        )
    }
}

@Composable
private fun DayHeaderRow(todayDay: Int, weekDates: List<LocalDate>) {
    val colors = MaterialTheme.colorScheme
    val monthText = weekDates.firstOrNull()?.let {
        "${it.monthValue}月"
    } ?: ""

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Top-left: current month
            Box(
                modifier = Modifier.width(36.dp).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    monthText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant
                )
            }

            val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            for ((i, day) in days.withIndex()) {
                val dayNum = i + 1
                val isToday = dayNum == todayDay
                val dateStr = weekDates.getOrNull(i)?.dayOfMonth?.toString() ?: ""
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .then(if (isToday) Modifier.background(Color(0x26FF9500)) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            day,
                            fontSize = 12.sp,
                            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (isToday) Color(0xFFFF9500) else colors.onSurfaceVariant
                        )
                        if (dateStr.isNotEmpty()) {
                            Text(
                                dateStr,
                                fontSize = 10.sp,
                                color = if (isToday) Color(0xFFFF9500) else colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        // 表头下极淡的分隔线
        HorizontalDivider(thickness = 0.5.dp, color = colors.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun TimeSlotRow(
    timeLabel: String,
    slotStart: Int,
    slotEnd: Int,
    courses: List<Course>,
    todayDay: Int,
    onCourseClick: (Course) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(timeLabel, fontSize = 10.sp, color = colors.onSurfaceVariant)
        }

        for (day in 1..7) {
            val cellCourses = courses.filter {
                it.dayOfWeek == day &&
                it.startSlot <= slotEnd &&
                it.endSlot >= slotStart
            }
            val primary = cellCourses.firstOrNull()

            val isToday = day == todayDay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (isToday) Modifier.background(Color(0x1AFF9500)) else Modifier)
            ) {
                if (primary != null) {
                    CourseCell(
                        name = primary.name,
                        classroom = if (primary.classroom.startsWith("未排")) "" else primary.classroom,
                        color = parseColor(primary.color),
                        onClick = { onCourseClick(primary) }
                    )
                } else {
                    EmptyCell()
                }
            }
        }
    }
}

private fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFFB0BEC5)
    }
}

private fun formatWeeks(weeks: List<Int>): String {
    if (weeks.isEmpty()) return ""
    val sorted = weeks.sorted()
    val ranges = mutableListOf<String>()
    var start = sorted.first()
    var end = start
    for (i in 1 until sorted.size) {
        if (sorted[i] == end + 1) {
            end = sorted[i]
        } else {
            ranges.add(if (start == end) "$start" else "$start-$end")
            start = sorted[i]
            end = start
        }
    }
    ranges.add(if (start == end) "$start" else "$start-$end")
    return "${ranges.joinToString(", ")}周"
}
