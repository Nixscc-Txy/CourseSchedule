package com.example.courseschedule

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.courseschedule.ui.CourseSelectionDialog
import com.example.courseschedule.ui.SettingsScreen
import com.example.courseschedule.ui.WeekViewScreen
import com.example.courseschedule.ui.theme.CourseScheduleTheme
import com.example.courseschedule.viewmodel.ScheduleViewModel

class MainActivity : ComponentActivity() {

    private val vm: ScheduleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val courses by vm.courses.collectAsState()
            val currentWeek by vm.currentWeek.collectAsState()
            val todayWeek by vm.todayWeek.collectAsState()
            val today by vm.today.collectAsState()
            val totalWeeks by vm.totalWeeks.collectAsState()
            val startDate by vm.startDate.collectAsState()
            val themeMode by vm.themeMode.collectAsState()
            val selectionPrompt by vm.selectionPrompt.collectAsState()
            val clusterAnswers by vm.clusterAnswers.collectAsState()
            val optionalSelectedKeys by vm.optionalSelectedKeys.collectAsState()
            val unresolvedConflictSlots by vm.unresolvedConflictSlots.collectAsState()
            val scheduleEmpty by vm.scheduleEmpty.collectAsState()
            val error by vm.error.collectAsState()

            var showSettings by rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = showSettings) { showSettings = false }

            val context = LocalContext.current

            CourseScheduleTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showSettings) {
                        SettingsScreen(
                            currentStartDate = startDate,
                            currentThemeMode = themeMode,
                            error = error,
                            onSave = { date, mode ->
                                vm.saveSettings(date, mode)
                                showSettings = false
                            },
                            onBack = { showSettings = false },
                            onImportFile = { uri: Uri -> vm.importSchedule(uri) },
                            onClearSchedule = { vm.clearSchedule() },
                            onErrorShown = { vm.consumeError() }
                        )
                    } else {
                        WeekViewScreen(
                            courses = courses,
                            totalWeeks = totalWeeks,
                            currentWeek = currentWeek,
                            todayWeek = todayWeek,
                            today = today,
                            startDate = startDate,
                            unresolvedConflictSlots = unresolvedConflictSlots,
                            scheduleEmpty = scheduleEmpty,
                            onPrevWeek = { vm.prevWeek() },
                            onNextWeek = { vm.nextWeek() },
                            onBackToToday = { vm.goToToday() },
                            onOpenSettings = { showSettings = true },
                            onResolveConflicts = { vm.resolveConflicts() }
                        )
                    }
                }

                // 导入确认 / 事后补选冲突共用同一个弹窗, 所以放在这里统一渲染,
                // 不放在设置页里 —— 否则从课表点"去选择"还得先跳到设置页。
                selectionPrompt?.let { prompt ->
                    CourseSelectionDialog(
                        prompt = prompt,
                        clusterAnswers = clusterAnswers,
                        optionalSelectedKeys = optionalSelectedKeys,
                        onDecideConflict = { index, course -> vm.decideConflict(index, course) },
                        onToggleOptionalCourse = { vm.toggleOptionalCourse(it) },
                        onSetAllOptionalCourses = { vm.setAllOptionalCourses(it) },
                        onConfirm = {
                            val wasImport = prompt.fromImport
                            vm.confirmImport()
                            Toast.makeText(
                                context,
                                if (wasImport) "课表已更新" else "已保存，冲突课程只显示你选的那门",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onDismiss = { vm.dismissImport() }
                    )
                }
            }
        }
    }

    /** App 可能跨天挂在后台, 回前台重新算"今天是第几周" */
    override fun onResume() {
        super.onResume()
        vm.refreshToday()
    }
}
