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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.data.SlotTime
import com.example.courseschedule.data.TimeUtils
import com.example.courseschedule.model.Course
import java.time.LocalDate

@Composable
fun WeekViewScreen(
    courses: List<Course>,
    totalWeeks: Int,
    currentWeek: Int,
    todayWeek: Int,
    today: LocalDate,
    startDate: LocalDate?,
    unresolvedConflictSlots: Int,
    scheduleEmpty: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onBackToToday: () -> Unit,
    onOpenSettings: () -> Unit,
    onResolveConflicts: () -> Unit
) {
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    val colors = MaterialTheme.colorScheme
    val todayDay = today.dayOfWeek.value // 1=Mon..7=Sun

    // 颜色只解析一次, 不放在每个格子里反复 parseColor
    val colorCache = remember(courses) {
        courses.map { it.color }.distinct().associateWith { parseColor(it) }
    }

    // Calculate dates for each day of the current week
    val weekDates: List<LocalDate> = remember(currentWeek, startDate) {
        if (startDate == null) {
            emptyList()
        } else {
            (0..6).map { startDate.plusDays(((currentWeek - 1) * 7 + it).toLong()) }
        }
    }

    // 按 [时段行][星期] 一次性分好组, 避免每次重组做 42 次全量过滤
    val grid: List<List<List<Course>>> = remember(courses) {
        TimeUtils.slotTimes.map { slot ->
            (1..7).map { day ->
                courses.filter {
                    it.dayOfWeek == day &&
                        it.startSlot <= slot.endSlot &&
                        it.endSlot >= slot.startSlot
                }
            }
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
            todayWeek = todayWeek,
            canJumpToToday = startDate != null,
            onPrev = onPrevWeek,
            onNext = onNextWeek,
            onBackToToday = onBackToToday,
            onSettings = onOpenSettings
        )

        if (scheduleEmpty) {
            // 出厂就是空课表, 没有引导的话一片空格子看着像坏了
            EmptySchedule(onOpenSettings)
        } else {
            if (unresolvedConflictSlots > 0) {
                ConflictHint(unresolvedConflictSlots, onResolveConflicts)
            }

            if (startDate == null) {
                StartDateHint(onOpenSettings)
            }

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
                        TimeUtils.slotTimes.forEachIndexed { rowIndex, slot ->
                            TimeSlotRow(
                                slot = slot,
                                coursesByDay = grid[rowIndex],
                                todayDay = todayDay,
                                colorCache = colorCache,
                                onCourseClick = { selectedCourse = it }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedCourse?.let { course ->
        val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val dayName = dayNames.getOrElse(course.dayOfWeek - 1) { "未知" }
        val clock = TimeUtils.timesFor(course.startSlot, course.endSlot)
            ?.let { (start, end) -> " $start-$end" }
            .orEmpty()

        CourseDetailDialog(
            courseName = course.name,
            teacher = course.teacher,
            teachingClass = course.teachingClass,
            classroom = course.classroom,
            timeSlot = "$dayName 第${course.startSlot}-${course.endSlot}节$clock",
            weeks = formatWeeks(course.weeks),
            onDismiss = { selectedCourse = null }
        )
    }
}

/** 空课表引导: 出厂就是空的, 得告诉用户下一步做什么 */
@Composable
private fun EmptySchedule(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "还没有课表",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "从教务系统下载课表文件（.xls / .xlsx），在设置里导入即可。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onOpenSettings) { Text("去导入课表") }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "导入的是班级课表也没关系：App 会让你挑出自己实际要上的课，选完每个时段只剩你那一门。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 课表里还有没选的时间冲突时 (同一时段叠着多门课) 提示用户去选。
 * 内置课表本身就是班级课表, 自带平行选修, 不提示的话用户只会看到莫名其妙的叠加格子。
 */
@Composable
private fun ConflictHint(slotCount: Int, onResolve: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "有 $slotCount 处时间冲突还没选，先叠加显示了。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onResolve) { Text("去选择") }
        }
    }
}

/** 没设开学日期时当前周恒为 1、日期也不显示, 主动告诉用户去设置 */
@Composable
private fun StartDateHint(onOpenSettings: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "还没设开学日期，没法自动定位到本周。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenSettings) { Text("去设置") }
        }
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
                modifier = Modifier.width(46.dp).padding(vertical = 10.dp),
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
    slot: SlotTime,
    coursesByDay: List<List<Course>>,
    todayDay: Int,
    colorCache: Map<String, Color>,
    onCourseClick: (Course) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
    ) {
        Column(
            modifier = Modifier
                .width(46.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                TimeUtils.slotLabel(slot),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant
            )
            Text(
                slot.startTime.toString(),
                fontSize = 9.sp,
                color = colors.onSurfaceVariant
            )
        }

        for (day in 1..7) {
            val isToday = day == todayDay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (isToday) Modifier.background(Color(0x1AFF9500)) else Modifier)
            ) {
                val cellCourses = coursesByDay[day - 1]
                if (cellCourses.isEmpty()) {
                    EmptyCell()
                } else {
                    CourseCell(
                        courses = cellCourses,
                        colorFor = { colorCache[it] ?: FALLBACK_COLOR },
                        onClick = onCourseClick
                    )
                }
            }
        }
    }
}

private val FALLBACK_COLOR = Color(0xFFB0BEC5)

private fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        FALLBACK_COLOR
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
