package com.example.courseschedule.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.CourseRepository
import com.example.courseschedule.data.ScheduleImporter
import com.example.courseschedule.data.SettingsManager
import com.example.courseschedule.model.Course
import com.example.courseschedule.model.ScheduleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    data class ImportSummary(val courseCount: Int, val totalWeeks: Int)

    private var schedule: ScheduleData = CourseRepository.load(application)

    private val _totalWeeks = MutableStateFlow(schedule.totalWeeks)
    val totalWeeks: StateFlow<Int> = _totalWeeks

    private val _currentWeek = MutableStateFlow(1)
    val currentWeek: StateFlow<Int> = _currentWeek

    private val _courses = MutableStateFlow(emptyList<Course>())
    val courses: StateFlow<List<Course>> = _courses

    private val _startDate = MutableStateFlow<LocalDate?>(null)
    val startDate: StateFlow<LocalDate?> = _startDate

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode

    private var pendingImport: ScheduleData? = null
    private val _importSummary = MutableStateFlow<ImportSummary?>(null)
    val importSummary: StateFlow<ImportSummary?> = _importSummary

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        _startDate.value = SettingsManager.getStartDate(application)
        _themeMode.value = SettingsManager.getThemeMode(application)
        val week = SettingsManager.getCurrentWeek(application, schedule.totalWeeks)
        setWeek(week)
    }

    fun setWeek(week: Int) {
        val w = week.coerceIn(1, _totalWeeks.value)
        _currentWeek.value = w
        updateCoursesForWeek(w)
    }

    fun nextWeek() {
        setWeek(_currentWeek.value + 1)
    }

    fun prevWeek() {
        setWeek(_currentWeek.value - 1)
    }

    fun saveSettings(startDate: LocalDate, themeMode: String) {
        viewModelScope.launch {
            SettingsManager.setStartDate(getApplication(), startDate)
            SettingsManager.setThemeMode(getApplication(), themeMode)
            _startDate.value = startDate
            _themeMode.value = themeMode
            val week = SettingsManager.getCurrentWeek(getApplication(), _totalWeeks.value)
            setWeek(week)
        }
    }

    // ------------------------------------------------------------ 课表导入

    /** 解析所选文件, 弹出确认 (不立即应用) */
    fun importSchedule(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = try {
                val bytes = getApplication<Application>().contentResolver
                    .openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("无法读取所选文件")
                ScheduleImporter.parse(bytes)
            } catch (e: Exception) {
                _error.value = "导入失败：${e.message ?: "未知错误"}"
                return@launch
            }
            if (data.courses.isEmpty()) {
                _error.value = "导入失败：未解析到任何课程，请确认文件是教务系统导出的课表"
                return@launch
            }
            pendingImport = data
            _importSummary.value = ImportSummary(data.courses.size, data.totalWeeks)
        }
    }

    /** 确认导入: 保存到 App 私有存储并立即生效 */
    fun confirmImport() {
        val data = pendingImport ?: return
        ScheduleImporter.save(getApplication(), data)
        pendingImport = null
        _importSummary.value = null
        reload()
    }

    fun dismissImport() {
        pendingImport = null
        _importSummary.value = null
    }

    fun consumeError() {
        _error.value = null
    }

    private fun reload() {
        schedule = CourseRepository.load(getApplication())
        _totalWeeks.value = schedule.totalWeeks
        setWeek(_currentWeek.value)
    }

    private fun updateCoursesForWeek(week: Int) {
        _courses.value = schedule.courses.filter { week in it.weeks }
    }
}
