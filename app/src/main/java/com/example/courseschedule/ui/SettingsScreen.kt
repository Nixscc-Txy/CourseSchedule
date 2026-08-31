package com.example.courseschedule.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.data.CourseConflict
import com.example.courseschedule.model.Course
import com.example.courseschedule.viewmodel.ScheduleViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 设置界面 (独立页面, 非弹窗)。
 * 后续新功能(导入课表、学期设置等)统一放这里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentStartDate: LocalDate?,
    currentThemeMode: String,
    importSummary: ScheduleViewModel.ImportSummary?,
    pendingSelectedKeys: Set<String>,
    isImportReady: Boolean,
    error: String?,
    onSave: (LocalDate, String) -> Unit,
    onBack: () -> Unit,
    onImportFile: (Uri) -> Unit,
    onConfirmImport: () -> Unit,
    onSelectPendingCourse: (CourseConflict, Course) -> Unit,
    onDismissImport: () -> Unit,
    onErrorShown: () -> Unit
) {
    var dateStr by remember {
        mutableStateOf(currentStartDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: "")
    }
    var dateError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(currentThemeMode) }

    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(onImportFile) }

    // 导入失败时弹出提示
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← 返回", fontSize = 15.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ------------------------------------------------ 课表
            Text("课表", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📥 导入课表（教务系统 .xls / .xlsx）")
            }
            Text(
                "从教务系统下载课表文件（Excel/微信发的 .xls 或 .xlsx 均可）后点此导入，自动替换当前课表；导错了重新导入正确文件即可。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // ------------------------------------------------ 学期开学日期
            Text("学期开学日期", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = dateStr,
                onValueChange = {
                    dateStr = it
                    dateError = false
                },
                label = { Text("如 2026-03-09") },
                isError = dateError,
                supportingText = if (dateError) { { Text("格式错误，请使用 yyyy-MM-dd") } } else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            TextButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("从日历选择")
            }

            HorizontalDivider()

            // ------------------------------------------------ 主题
            Text("主题模式", style = MaterialTheme.typography.titleSmall)
            val themes = listOf(
                "system" to "跟随系统",
                "light" to "浅色",
                "dark" to "深色"
            )
            for ((value, label) in themes) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTheme = value }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedTheme == value,
                        onClick = { selectedTheme = value }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ------------------------------------------------ 保存
            Button(
                onClick = {
                    val date = try {
                        LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    } catch (_: Exception) {
                        dateError = true
                        null
                    }
                    if (date != null) {
                        onSave(date, selectedTheme)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        dateError = false
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 导入确认弹窗
    importSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = onDismissImport,
            title = { Text("确认导入课表", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("解析成功：共 ${summary.courseCount} 条课程，${summary.totalWeeks} 周。")
                    if (summary.conflicts.isEmpty()) {
                        Text("未发现同一时间的多门课程。")
                    } else {
                        Text("发现 ${summary.conflicts.size} 组时间冲突，请选择你实际选的课程：")
                        summary.conflicts.forEach { conflict ->
                            Text(
                                "${dayName(conflict.dayOfWeek)} 第${conflict.startSlot}-${conflict.endSlot}节（第${formatWeekRange(conflict.weeks)}周）",
                                fontWeight = FontWeight.Bold
                            )
                            conflict.courses.forEach { course ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectPendingCourse(conflict, course) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = course.selectionKey() in pendingSelectedKeys,
                                        onClick = { onSelectPendingCourse(conflict, course) }
                                    )
                                    Column(modifier = Modifier.padding(start = 4.dp)) {
                                        Text(course.name)
                                        Text(
                                            listOfNotNull(
                                                course.teacher.takeIf { it.isNotBlank() }?.let { "教师：$it" },
                                                course.teachingClass.takeIf { it.isNotBlank() }?.let { "教学班：$it" },
                                                course.classroom.takeIf { it.isNotBlank() }?.let { "教室：$it" }
                                            ).joinToString("  "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = isImportReady, onClick = {
                    onConfirmImport()
                    Toast.makeText(context, "课表已更新", Toast.LENGTH_SHORT).show()
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = onDismissImport) { Text("取消") }
            }
        )
    }
}

private fun dayName(dayOfWeek: Int): String {
    return listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrElse(dayOfWeek - 1) { "未知日期" }
}

/** 周次列表格式化为 "1-8,10-12" 这样的区间表示 */
private fun formatWeekRange(weeks: List<Int>): String {
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
