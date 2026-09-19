package com.example.courseschedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.courseschedule.model.Course

/**
 * 一个格子里的课程。同一时段有多门时(班级课表里别人的选修课)全部显示出来、
 * 各自可点, 不再只显示第一门而把后面的悄悄丢掉。
 */
@Composable
fun CourseCell(
    courses: List<Course>,
    colorFor: (String) -> Color,
    onClick: (Course) -> Unit,
    modifier: Modifier = Modifier
) {
    if (courses.size == 1) {
        CourseBlock(courses[0], colorFor, onClick, modifier.fillMaxSize(), compact = false)
        return
    }
    Column(modifier.fillMaxSize().padding(2.dp)) {
        courses.forEach { course ->
            CourseBlock(
                course = course,
                colorFor = colorFor,
                onClick = onClick,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                compact = true
            )
        }
    }
}

@Composable
private fun CourseBlock(
    course: Course,
    colorFor: (String) -> Color,
    onClick: (Course) -> Unit,
    modifier: Modifier,
    compact: Boolean
) {
    // 深色模式下把格子颜色降亮 (向深色背景靠拢), 文字反色, 避免刺眼
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = colorFor(course.color)
    val bg = if (isDark) lerp(base, Color(0xFF1C1C1E), 0.55f) else base
    val nameColor = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1B1F)
    val roomColor = if (isDark) Color(0xFFB8B8BE) else Color(0xFF6E6E73)

    Box(
        modifier = modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(if (compact) 6.dp else 8.dp))
            .background(bg)
            .clickable { onClick(course) }
            .padding(horizontal = 2.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = course.name,
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                // 单课格子给到 3 行: 12sp 字号下每行只放得下约 3 个汉字,
                // 2 行会把「微信公众平台开发」「Python程序设计」这类名字截断
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                color = nameColor,
                lineHeight = if (compact) 12.sp else 14.sp
            )
            val room = course.classroom.takeUnless { it.startsWith("未排") }.orEmpty()
            if (!compact && room.isNotEmpty()) {
                Text(
                    text = room,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = roomColor,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

@Composable
fun EmptyCell(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(1.dp)
    )
}
