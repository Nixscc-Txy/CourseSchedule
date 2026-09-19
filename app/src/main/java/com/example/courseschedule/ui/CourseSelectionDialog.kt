package com.example.courseschedule.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courseschedule.data.ConflictCluster
import com.example.courseschedule.model.Course
import com.example.courseschedule.viewmodel.ScheduleViewModel

/**
 * 平行选修选择弹窗, 两种场景共用:
 *  - 导入了新文件, 确认并挑出自己要上的课;
 *  - 当前课表里还有没解决的时间冲突 (内置课表本身就是班级课表, 自带平行选修), 补选。
 */
@Composable
fun CourseSelectionDialog(
    prompt: ScheduleViewModel.SelectionPrompt,
    clusterAnswers: Map<Int, String?>,
    optionalSelectedKeys: Set<String>,
    onDecideConflict: (Int, Course?) -> Unit,
    onToggleOptionalCourse: (Course) -> Unit,
    onSetAllOptionalCourses: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var showAllCourses by remember { mutableStateOf(false) }
    val undecided = prompt.conflicts.indices.count { it !in clusterAnswers }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (prompt.fromImport) "确认导入课表" else "选择你要上的课",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    if (prompt.fromImport)
                        "解析成功：共 ${prompt.courseCount} 条课程，${prompt.totalWeeks} 周。"
                    else
                        "当前课表共 ${prompt.courseCount} 条课程，${prompt.totalWeeks} 周。"
                )

                if (prompt.conflicts.isEmpty()) {
                    Text("未发现同一时间的多门课程。")
                } else {
                    Text(
                        text = if (undecided > 0)
                            "有 ${prompt.conflicts.size} 组课在同一时段同时开，还有 $undecided 组没选。请挑出你自己要上的课："
                        else
                            "有 ${prompt.conflicts.size} 组课在同一时段同时开，已全部选好。",
                        color = if (undecided > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "选完这里每个时段就只会显示你那一门，不会再叠加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    prompt.conflicts.forEachIndexed { index, cluster ->
                        ConflictClusterBlock(
                            cluster = cluster,
                            answer = clusterAnswers[index],
                            answered = index in clusterAnswers,
                            onDecide = { course -> onDecideConflict(index, course) }
                        )
                    }
                }

                if (prompt.optionalCourses.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    val selectedCount = prompt.optionalCourses.count {
                        it.selectionKey() in optionalSelectedKeys
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAllCourses = !showAllCourses },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "其他课程 ${selectedCount}/${prompt.optionalCourses.size} 门保留",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (showAllCourses) "收起 ▲" else "展开 ▼",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "不冲突的课默认全部保留，不用上的可以取消勾选。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showAllCourses) {
                        Row {
                            TextButton(onClick = { onSetAllOptionalCourses(true) }) { Text("全选") }
                            TextButton(onClick = { onSetAllOptionalCourses(false) }) { Text("全不选") }
                        }
                        prompt.optionalCourses.forEach { course ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleOptionalCourse(course) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = course.selectionKey() in optionalSelectedKeys,
                                    onCheckedChange = { onToggleOptionalCourse(course) }
                                )
                                Column(modifier = Modifier.padding(start = 4.dp)) {
                                    Text(course.name, style = MaterialTheme.typography.bodyMedium)
                                    val subtitle = courseSubtitle(course)
                                    if (subtitle.isNotEmpty()) {
                                        Text(
                                            subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = undecided == 0, onClick = onConfirm) {
                Text(
                    when {
                        undecided > 0 -> "还有 $undecided 组待选"
                        prompt.fromImport -> "导入"
                        else -> "保存"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 一组平行选修。[cluster.slots] 可能有多个时段 (同一批课在几天同时开),
 * 用户只回答一次, 答案自动套用到该簇覆盖的所有时段。
 */
@Composable
private fun ConflictClusterBlock(
    cluster: ConflictCluster,
    answer: String?,
    answered: Boolean,
    onDecide: (Course?) -> Unit
) {
    Text(
        clusterSlotText(cluster),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 6.dp)
    )
    cluster.courses.forEach { course ->
        ChoiceRow(
            selected = answer == course.selectionKey(),
            title = course.name,
            subtitle = courseSubtitle(course),
            onClick = { onDecide(course) }
        )
    }
    // 这一组可能都是别人的选修课, 允许明确"我都不上"
    ChoiceRow(
        selected = answered && answer == null,
        title = "这组我都不上",
        subtitle = "",
        onClick = { onDecide(null) }
    )
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(title)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** "周三 第7-8节、周五 第1-2节（第1-12周）" */
private fun clusterSlotText(cluster: ConflictCluster): String {
    val slots = cluster.slots.joinToString("、") {
        "${dayName(it.dayOfWeek)} 第${it.startSlot}-${it.endSlot}节"
    }
    return "$slots（第${formatWeekRange(cluster.weeks)}周）"
}

internal fun courseSubtitle(course: Course): String {
    return listOfNotNull(
        course.teacher.takeIf { it.isNotBlank() }?.let { "教师：$it" },
        course.teachingClass.takeIf { it.isNotBlank() }?.let { "教学班：$it" },
        course.classroom.takeIf { it.isNotBlank() }?.let { "教室：$it" }
    ).joinToString("  ")
}

internal fun dayName(dayOfWeek: Int): String {
    return listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrElse(dayOfWeek - 1) { "未知日期" }
}

/** 周次列表格式化为 "1-8,10-12" 这样的区间表示 */
internal fun formatWeekRange(weeks: List<Int>): String {
    val sorted = weeks.sorted()
    if (sorted.isEmpty()) return ""
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
    return ranges.joinToString(",")
}
