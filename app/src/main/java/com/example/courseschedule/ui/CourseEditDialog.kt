package com.example.courseschedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.courseschedule.model.Course
import com.example.courseschedule.model.joinClassroom
import com.example.courseschedule.model.splitClassroom

/**
 * 改课程信息: 课名 / 教师 / 教学楼 / 教室号。
 *
 * 教室原本是一个字符串 ("致远楼213"), 这里拆成教学楼和教室号两个输入框, 保存时再拼回去
 * (见 splitClassroom / joinClassroom), 这样不用给数据格式加字段, 老课表也照常读。
 */
@Composable
fun CourseEditDialog(
    course: Course,
    onSave: (name: String, teacher: String, classroom: String) -> Unit,
    onDismiss: () -> Unit
) {
    val (building0, room0) = remember(course) { splitClassroom(course.classroom) }
    var name by remember(course) { mutableStateOf(course.name) }
    var teacher by remember(course) { mutableStateOf(course.teacher) }
    var building by remember(course) { mutableStateOf(building0) }
    var room by remember(course) { mutableStateOf(room0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更改课程信息", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("任课教师") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = building,
                    onValueChange = { building = it },
                    label = { Text("教学楼") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("教室号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), teacher.trim(), joinClassroom(building, room)) }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
