package com.example.courseschedule

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.courseschedule.ui.SettingsScreen
import com.example.courseschedule.ui.WeekViewScreen
import com.example.courseschedule.ui.theme.CourseScheduleTheme
import com.example.courseschedule.viewmodel.ScheduleViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ScheduleViewModel = viewModel()
            val courses by vm.courses.collectAsState()
            val currentWeek by vm.currentWeek.collectAsState()
            val totalWeeks by vm.totalWeeks.collectAsState()
            val startDate by vm.startDate.collectAsState()
            val themeMode by vm.themeMode.collectAsState()
            val importSummary by vm.importSummary.collectAsState()
            val error by vm.error.collectAsState()

            var showSettings by rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = showSettings) { showSettings = false }

            CourseScheduleTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showSettings) {
                        SettingsScreen(
                            currentStartDate = startDate,
                            currentThemeMode = themeMode,
                            importSummary = importSummary,
                            error = error,
                            onSave = { date, mode ->
                                vm.saveSettings(date, mode)
                                showSettings = false
                            },
                            onBack = { showSettings = false },
                            onImportFile = { uri: Uri -> vm.importSchedule(uri) },
                            onConfirmImport = { vm.confirmImport() },
                            onDismissImport = { vm.dismissImport() },
                            onErrorShown = { vm.consumeError() }
                        )
                    } else {
                        WeekViewScreen(
                            courses = courses,
                            totalWeeks = totalWeeks,
                            currentWeek = currentWeek,
                            startDate = startDate,
                            onPrevWeek = { vm.prevWeek() },
                            onNextWeek = { vm.nextWeek() },
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
}
