package com.example.courseschedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CourseCell(
    name: String,
    classroom: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(1.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 3.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.Black,
                lineHeight = 15.sp
            )
            if (classroom.isNotEmpty()) {
                Text(
                    text = classroom,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.DarkGray,
                    lineHeight = 13.sp
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
