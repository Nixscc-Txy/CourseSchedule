package com.example.courseschedule.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@Composable
fun CourseDetailDialog(
    courseName: String,
    teacher: String,
    classroom: String,
    timeSlot: String,
    weeks: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = courseName, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                if (teacher.isNotEmpty()) {
                    Text("教师：$teacher", style = MaterialTheme.typography.bodyMedium)
                }
                if (classroom.isNotEmpty()) {
                    Text("教室：$classroom", style = MaterialTheme.typography.bodyMedium)
                }
                Text("时间：$timeSlot", style = MaterialTheme.typography.bodyMedium)
                Text("周次：$weeks", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
