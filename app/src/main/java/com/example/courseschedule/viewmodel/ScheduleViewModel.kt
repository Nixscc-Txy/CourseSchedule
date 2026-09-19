package com.example.courseschedule.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.ConflictCluster
import com.example.courseschedule.data.CourseRepository
import com.example.courseschedule.data.ScheduleImporter
import com.example.courseschedule.data.ScheduleSelection
import com.example.courseschedule.data.SettingsManager
import com.example.courseschedule.model.Course
import com.example.courseschedule.model.ScheduleData
import com.example.courseschedule.widget.CourseWidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * 需要用户回答的一组平行选修。
     *
     * [fromImport] = true 表示刚解析完一个导入的文件 (确认后要写盘);
     * false 表示只是给当前课表里遗留的时间冲突补选 (确认后只更新选择)。
     */
    data class SelectionPrompt(
        val courseCount: Int,
        val totalWeeks: Int,
        /**
         * 班级课表里别人要上、你未必选的课。
         * 同一批课在多天的同一时段同时开设时已经合并成一个簇, 每个簇只回答一次。
         */
        val conflicts: List<ConflictCluster>,
        /** 不参与冲突、默认全部保留的课程 (按身份去重, 可逐门取消勾选) */
        val optionalCourses: List<Course>,
        val fromImport: Boolean
    )

    private var schedule: ScheduleData = CourseRepository.load(application)

    private val _totalWeeks = MutableStateFlow(schedule.totalWeeks)
    val totalWeeks: StateFlow<Int> = _totalWeeks

    /** 用户当前正在看的周次 (可能被手动翻到别的周) */
    private val _currentWeek = MutableStateFlow(1)
    val currentWeek: StateFlow<Int> = _currentWeek

    /** 今天真实所在的周次, 用来判断"是不是在看本周" */
    private val _todayWeek = MutableStateFlow(1)
    val todayWeek: StateFlow<Int> = _todayWeek

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today

    private val _courses = MutableStateFlow(emptyList<Course>())
    val courses: StateFlow<List<Course>> = _courses

    private val _startDate = MutableStateFlow<LocalDate?>(null)
    val startDate: StateFlow<LocalDate?> = _startDate

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode

    private var pendingImport: ScheduleData? = null
    private val _selectionPrompt = MutableStateFlow<SelectionPrompt?>(null)
    val selectionPrompt: StateFlow<SelectionPrompt?> = _selectionPrompt

    /** 当前显示出来的课表里, 同一时段仍然叠着多门课的位置数 (0 = 没有遗留冲突) */
    private val _unresolvedConflictSlots = MutableStateFlow(0)
    val unresolvedConflictSlots: StateFlow<Int> = _unresolvedConflictSlots

    /** 整份课表一门课都没有 (出厂是空课表, 或用户点了「清空课表」) */
    private val _scheduleEmpty = MutableStateFlow(schedule.courses.isEmpty())
    val scheduleEmpty: StateFlow<Boolean> = _scheduleEmpty

    /**
     * 导入确认中: 每个冲突簇的回答, 值为课程身份键; 值为 null 表示"这组我都不上"。
     * 下标不在 map 里 = 还没回答。
     */
    private val _clusterAnswers = MutableStateFlow<Map<Int, String?>>(emptyMap())
    val clusterAnswers: StateFlow<Map<Int, String?>> = _clusterAnswers

    /** 导入确认中: 冲突之外的课程里, 用户保留的那些身份键 */
    private val _optionalSelectedKeys = MutableStateFlow<Set<String>>(emptySet())
    val optionalSelectedKeys: StateFlow<Set<String>> = _optionalSelectedKeys

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        _startDate.value = SettingsManager.getStartDate(application)
        _themeMode.value = SettingsManager.getThemeMode(application)
        refreshToday()
        refreshUnresolvedConflicts()
        setWeek(_todayWeek.value)
    }

    /** App 可能跨天挂在后台, 回到前台时重新算"今天是第几周" */
    fun refreshToday() {
        _today.value = LocalDate.now()
        _todayWeek.value = SettingsManager.getWeekFor(
            getApplication(), _today.value, _totalWeeks.value
        )
    }

    fun goToToday() {
        refreshToday()
        setWeek(_todayWeek.value)
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
            refreshToday()
            setWeek(_todayWeek.value)
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
            val conflicts = ScheduleSelection.clusterConflicts(ScheduleSelection.findConflicts(data))
            val conflictingKeys = conflicts.flatMap { it.courses }.map { it.selectionKey() }.toSet()
            val optional = data.courses
                .filter { it.selectionKey() !in conflictingKeys }
                .distinctBy { it.selectionKey() }

            _optionalSelectedKeys.value = optional.map { it.selectionKey() }.toSet()
            _clusterAnswers.value = emptyMap()
            pendingImport = data
            _selectionPrompt.value =
                SelectionPrompt(data.courses.size, data.totalWeeks, conflicts, optional, fromImport = true)
        }
    }

    /**
     * 当前课表里还有没解决的时间冲突时 (例如内置课表本身就是班级课表), 让用户当场补选,
     * 不需要重新导入文件。已经选过的那几组会预填好。
     */
    fun resolveConflicts() {
        val data = schedule
        val conflicts = ScheduleSelection.clusterConflicts(ScheduleSelection.findConflicts(data))
        if (conflicts.isEmpty()) return

        val conflictingKeys = conflicts.flatMap { it.courses }.map { it.selectionKey() }.toSet()
        val optional = data.courses
            .filter { it.selectionKey() !in conflictingKeys }
            .distinctBy { it.selectionKey() }

        // 之前选过的组直接填上, 用户只需要处理剩下没选的
        val saved = ScheduleSelection.loadSelectedKeys(getApplication(), data).orEmpty()
        _clusterAnswers.value = conflicts.mapIndexedNotNull { index, cluster ->
            cluster.courses.firstOrNull { it.selectionKey() in saved }?.let { index to it.selectionKey() }
        }.toMap()
        _optionalSelectedKeys.value = optional.map { it.selectionKey() }.toSet()
        pendingImport = null
        _selectionPrompt.value =
            SelectionPrompt(data.courses.size, data.totalWeeks, conflicts, optional, fromImport = false)
    }

    /** 重算"当前显示的课表里还有几处叠加的冲突" */
    private fun refreshUnresolvedConflicts() {
        val visible = ScheduleSelection.visibleCourses(getApplication(), schedule)
        val conflicts = ScheduleSelection.clusterConflicts(
            ScheduleSelection.findConflicts(ScheduleData(schedule.totalWeeks, visible))
        )
        _unresolvedConflictSlots.value = conflicts.sumOf { it.slots.size }
    }

    /**
     * 清空课表: 删掉导入的文件和选课记录, 回到内置的空课表。
     * 现在没有"恢复出厂课表"可言 —— 内置课表本身也是空的。
     */
    fun clearSchedule() {
        ScheduleImporter.clear(getApplication())
        ScheduleSelection.clear(getApplication())
        CourseWidgetReceiver.refresh(getApplication())
        _currentWeek.value = 1
        reload()
    }

    /**
     * 回答第 [clusterIndex] 组平行选修: [course] 为用户实际选的那门, 传 null 表示这组他都不上。
     * 每个簇只保存一个答案, 所以不会出现"后一组把前一组的选择覆盖掉"的情况。
     */
    fun decideConflict(clusterIndex: Int, course: Course?) {
        val prompt = _selectionPrompt.value ?: return
        if (clusterIndex !in prompt.conflicts.indices) return
        _clusterAnswers.value = _clusterAnswers.value + (clusterIndex to course?.selectionKey())
    }

    /** 逐门勾选/取消"其他课程" */
    fun toggleOptionalCourse(course: Course) {
        val key = course.selectionKey()
        _optionalSelectedKeys.value =
            if (key in _optionalSelectedKeys.value) _optionalSelectedKeys.value - key
            else _optionalSelectedKeys.value + key
    }

    fun setAllOptionalCourses(selected: Boolean) {
        val keys = _selectionPrompt.value?.optionalCourses?.map { it.selectionKey() }?.toSet() ?: return
        _optionalSelectedKeys.value =
            if (selected) _optionalSelectedKeys.value + keys
            else _optionalSelectedKeys.value - keys
    }

    fun isPendingImportReady(): Boolean {
        val prompt = _selectionPrompt.value ?: return false
        return prompt.conflicts.indices.all { it in _clusterAnswers.value }
    }

    /**
     * 确认。导入场景下会把新课表写盘; 补选场景下 [pendingImport] 为 null, 只更新选择记录,
     * 作用在"当前这份课表"上 (内置 assets 或已经导入过的文件)。
     */
    fun confirmImport() {
        if (!isPendingImportReady()) return
        val data = pendingImport ?: schedule
        val selected = _optionalSelectedKeys.value + _clusterAnswers.value.values.filterNotNull()
        ScheduleSelection.saveSelectedKeys(getApplication(), data, selected)
        pendingImport?.let { ScheduleImporter.save(getApplication(), it) }
        CourseWidgetReceiver.refresh(getApplication())
        pendingImport = null
        _selectionPrompt.value = null
        _optionalSelectedKeys.value = emptySet()
        _clusterAnswers.value = emptyMap()
        reload()
    }

    fun dismissImport() {
        pendingImport = null
        _selectionPrompt.value = null
        _clusterAnswers.value = emptyMap()
    }

    fun consumeError() {
        _error.value = null
    }

    private fun reload() {
        schedule = CourseRepository.load(getApplication())
        _totalWeeks.value = schedule.totalWeeks
        _scheduleEmpty.value = schedule.courses.isEmpty()
        refreshToday()
        refreshUnresolvedConflicts()
        setWeek(_currentWeek.value)
    }

    private fun updateCoursesForWeek(week: Int) {
        _courses.value = ScheduleSelection.visibleCourses(getApplication(), schedule)
            .filter { week in it.weeks }
    }
}
