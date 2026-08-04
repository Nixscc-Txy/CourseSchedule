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
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPrev) {
                Text("◀ 上一周", fontSize = 14.sp)
            }
            Text(
                text = "第 $currentWeek 周",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row {
                TextButton(onClick = onNext) {
                    Text("下一周 ▶", fontSize = 14.sp)
                }
                TextButton(onClick = onSettings) {
                    Text("⚙", fontSize = 18.sp)
                }
            }
        }
    }
}
