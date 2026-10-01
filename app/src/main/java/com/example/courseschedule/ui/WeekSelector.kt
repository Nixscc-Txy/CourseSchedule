package com.example.courseschedule.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WeekSelector(
    currentWeek: Int,
    totalWeeks: Int,
    todayWeek: Int,
    canJumpToToday: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBackToToday: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 高度写死: 表头里的内容会随周次变(「本周」↔「回到本周」),
                // 高度一旦跟着变, 下面的课程区就会被顶下去 —— 滑动落位时看着就是一顿。
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPrev, enabled = currentWeek > 1) {
                Text("◀ 上一周", fontSize = 14.sp)
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "第 $currentWeek 周",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                // 「本周」和「回到本周」共用这一格 —— 二者互斥, 所以表头永远不会变高,
                // 回到本周也就不用再另占一行把课表往下压了。
                when {
                    !canJumpToToday -> Unit
                    currentWeek == todayWeek -> Text(
                        "本周",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    else -> Text(
                        "⤴ 回到本周",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onBackToToday)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onNext, enabled = currentWeek < totalWeeks) {
                    Text("下一周 ▶", fontSize = 14.sp)
                }
                TextButton(onClick = onSettings) {
                    Text("⚙", fontSize = 18.sp)
                }
            }
        }
    }
}
