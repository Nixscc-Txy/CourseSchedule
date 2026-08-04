package com.example.courseschedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.CourseRepository
import com.example.courseschedule.data.SettingsManager
import com.example.courseschedule.model.Course
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val schedule = CourseRepository.load(application)
    val totalWeeks: Int = schedule.totalWeeks

    private val _currentWeek = MutableStateFlow(1)
    val currentWeek: StateFlow<Int> = _currentWeek

    private val _courses = MutableStateFlow(emptyList<Course>())
    val courses: StateFlow<List<Course>> = _courses

    private val _startDate = MutableStateFlow<LocalDate?>(null)
    val startDate: StateFlow<LocalDate?> = _startDate

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode

    init {
        _startDate.value = SettingsManager.getStartDate(application)
        _themeMode.value = SettingsManager.getThemeMode(application)
        val week = SettingsManager.getCurrentWeek(application, totalWeeks)
        setWeek(week)
    }

    fun setWeek(week: Int) {
        if (week in 1..totalWeeks) {
            _currentWeek.value = week
            updateCoursesForWeek(week)
        }
    }

    fun nextWeek() {
        val next = _currentWeek.value + 1
        if (next <= totalWeeks) setWeek(next)
    }

    fun prevWeek() {
        val prev = _currentWeek.value - 1
        if (prev >= 1) setWeek(prev)
    }

    fun saveSettings(startDate: LocalDate, themeMode: String) {
        viewModelScope.launch {
            SettingsManager.setStartDate(getApplication(), startDate)
            SettingsManager.setThemeMode(getApplication(), themeMode)
            _startDate.value = startDate
            _themeMode.value = themeMode
            val week = SettingsManager.getCurrentWeek(getApplication(), totalWeeks)
            setWeek(week)
        }
    }

    private fun updateCoursesForWeek(week: Int) {
        _courses.value = schedule.courses.filter { week in it.weeks }
    }
}
