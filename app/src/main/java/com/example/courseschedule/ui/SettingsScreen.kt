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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 设置界面 (独立页面, 非弹窗)。
 * 课表导入的确认弹窗不在这里, 而是由 MainActivity 统一渲染 [CourseSelectionDialog],
 * 这样"导入时确认"和"事后补选冲突"可以共用同一个弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentStartDate: LocalDate?,
    currentThemeMode: String,
    error: String?,
    onSave: (LocalDate, String) -> Unit,
    onBack: () -> Unit,
    onImportFile: (Uri) -> Unit,
    onClearSchedule: () -> Unit,
    onErrorShown: () -> Unit
) {
    var dateStr by remember {
        mutableStateOf(currentStartDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: "")
    }
    var dateError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(currentThemeMode) }
    var showClearConfirm by remember { mutableStateOf(false) }

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
            OutlinedButton(
                onClick = { showClearConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("🗑 清空课表")
            }

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

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空课表", fontWeight = FontWeight.Bold) },
            text = {
                Text("会删掉已导入的课表和你的选课结果，课表变为空白。此操作无法撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearSchedule()
                    Toast.makeText(context, "课表已清空", Toast.LENGTH_SHORT).show()
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
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
}
