package com.example.courseschedule.ui

import android.content.ActivityNotFoundException
import android.content.Intent
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
import com.example.courseschedule.data.AppUpdate
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 用浏览器/已装的 GitHub App 打开一个链接。
 * 不用 resolveActivity 预判 —— API 30+ 那需要额外的 <queries> 声明, 直接 try 更省事。
 */
private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "没有可打开链接的应用，请手动访问 $url", Toast.LENGTH_LONG).show()
    }
}

/** 检查更新的界面状态 */
private sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Newer(val latest: AppUpdate.Latest, val current: String) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

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
    currentWidgetTheme: String,
    error: String?,
    onSave: (LocalDate, String, String) -> Unit,
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
    var selectedWidgetTheme by remember { mutableStateOf(currentWidgetTheme) }
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    val scope = rememberCoroutineScope()
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

            HorizontalDivider()

            // ------------------------------------------------ 小组件颜色
            Text("小组件颜色", style = MaterialTheme.typography.titleSmall)
            val widgetThemes = listOf(
                "follow" to "跟随主体",
                "light" to "浅色",
                "dark" to "深色"
            )
            for ((value, label) in widgetThemes) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedWidgetTheme = value }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedWidgetTheme == value,
                        onClick = { selectedWidgetTheme = value }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label)
                }
            }
            Text(
                "桌面小组件单独配色。默认「跟随主体」，即和上面选的主题一致。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                        onSave(date, selectedTheme, selectedWidgetTheme)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }

            HorizontalDivider()

            // ------------------------------------------------ 关于 / 更新
            Text("关于", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("当前版本", modifier = Modifier.weight(1f))
                Text(
                    AppUpdate.versionLabel(context),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = {
                    updateState = UpdateUiState.Checking
                    scope.launch {
                        updateState = when (val result = AppUpdate.check(context)) {
                            is AppUpdate.CheckResult.Newer ->
                                UpdateUiState.Newer(result.latest, result.current)
                            is AppUpdate.CheckResult.UpToDate -> {
                                Toast.makeText(
                                    context,
                                    "已是最新版本 ${result.current}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                UpdateUiState.Idle
                            }
                            is AppUpdate.CheckResult.Failed -> UpdateUiState.Failed(result.message)
                        }
                    }
                },
                enabled = updateState != UpdateUiState.Checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (updateState == UpdateUiState.Checking) "检查中…" else "检查更新")
            }
            // 国内访问 api.github.com 经常不通, 所以"打开发布页"永远留一条路
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { openUrl(context, AppUpdate.RELEASES_URL) }) {
                    Text("打开发布页", fontSize = 13.sp)
                }
            }
            Text(
                "「检查更新」只读一次 GitHub 的发布信息（本 App 唯一联网的地方，只读取、不上传任何数据）。" +
                    "发现新版本时给出下载入口，覆盖安装即可 —— 签名没变，课表和设置都不会丢。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

    when (val state = updateState) {
        is UpdateUiState.Newer -> AlertDialog(
            onDismissRequest = { updateState = UpdateUiState.Idle },
            title = { Text("发现新版本", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "当前 ${state.current}　→　最新 ${state.latest.tag}",
                        fontWeight = FontWeight.Bold
                    )
                    if (state.latest.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            state.latest.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "点「去下载」打开下载页，装完覆盖安装即可，课表和设置都不会丢。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    updateState = UpdateUiState.Idle
                    openUrl(context, state.latest.pageUrl)
                }) { Text("去下载") }
            },
            dismissButton = {
                TextButton(onClick = { updateState = UpdateUiState.Idle }) { Text("稍后") }
            }
        )

        is UpdateUiState.Failed -> AlertDialog(
            onDismissRequest = { updateState = UpdateUiState.Idle },
            title = { Text("检查更新失败", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${state.message}\n\n国内访问 GitHub 接口经常不通。" +
                        "可以直接打开发布页自己看一眼有没有新版。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    updateState = UpdateUiState.Idle
                    openUrl(context, AppUpdate.RELEASES_URL)
                }) { Text("打开发布页") }
            },
            dismissButton = {
                TextButton(onClick = { updateState = UpdateUiState.Idle }) { Text("关闭") }
            }
        )

        else -> Unit
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
