package com.example.courseschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
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
            val startDate by vm.startDate.collectAsState()
            val themeMode by vm.themeMode.collectAsState()

            CourseScheduleTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    WeekViewScreen(
                        courses = courses,
                        totalWeeks = vm.totalWeeks,
                        currentWeek = currentWeek,
                        startDate = startDate,
                        themeMode = themeMode,
                        onPrevWeek = { vm.prevWeek() },
                        onNextWeek = { vm.nextWeek() },
                        onSaveSettings = { date, mode -> vm.saveSettings(date, mode) }
                    )
                }
            }
        }
    }
}
