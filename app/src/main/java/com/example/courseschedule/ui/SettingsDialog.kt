package com.example.courseschedule.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    currentStartDate: LocalDate?,
    currentThemeMode: String,
    onSave: (startDate: LocalDate, themeMode: String) -> Unit,
    onDismiss: () -> Unit
) {
    var dateStr by remember { mutableStateOf(currentStartDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: "") }
    var dateError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(currentThemeMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Semester start date
                Text("学期开学日期", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = {
                        dateStr = it
                        dateError = false
                    },
                    label = { Text("如 2026-03-09") },
                    isError = dateError,
                    supportingText = if (dateError) {{ Text("格式错误，请使用 yyyy-MM-dd") }} else null,
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

                // Theme
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val date = try {
                    LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                } catch (_: Exception) {
                    dateError = true
                    null
                }
                if (date != null) {
                    onSave(date, selectedTheme)
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

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
