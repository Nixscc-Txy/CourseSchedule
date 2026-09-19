package com.example.courseschedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onPrev, enabled = currentWeek > 1) {
                    Text("◀ 上一周", fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "第 $currentWeek 周",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (canJumpToToday && currentWeek == todayWeek) {
                        Text(
                            "本周",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
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
            // 翻到别的周之后, 一键回到今天所在的周 (否则只能一周周点回来)
            if (canJumpToToday && currentWeek != todayWeek) {
                TextButton(
                    onClick = onBackToToday,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("⤴ 回到本周（第 $todayWeek 周）", fontSize = 13.sp)
                }
            }
        }
    }
}
