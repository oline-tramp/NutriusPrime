package com.example.kthealthsync.ui.search.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun CircularImpactIndicator(
    title: String,
    current: Double,
    added: Double,
    max: Double,
    unit: String,
    color: Color
) {
    val safeMax = if (max > 0) max else 1.0
    val currentSweep = ((current / safeMax) * 360f).toFloat().coerceIn(0f, 360f)
    val addedSweep = ((added / safeMax) * 360f).toFloat().coerceIn(0f, 360f - currentSweep)
    val totalPct = if (added > 0) ((added / safeMax) * 100).toInt() else 0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(88.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        val addedText = if (added % 1.0 == 0.0) added.toInt().toString() else String.format(Locale.US, "%.1f", added)
        Text(
            text = "+$addedText",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(50.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 5.dp.toPx()
                // 1. Track: remainder until daily norm (dark unfilled background)
                drawArc(
                    color = Color(0xFF2C2C2C),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(strokeW)
                )
                // 2. Current: already eaten today (solid bright color)
                if (currentSweep > 0.5f) {
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = currentSweep,
                        useCenter = false,
                        style = Stroke(strokeW, cap = if (currentSweep >= 360f) StrokeCap.Square else StrokeCap.Round)
                    )
                }
                // 3. Added: added by this food (semi-transparent color)
                if (addedSweep > 0.5f) {
                    drawArc(
                        color = color.copy(alpha = 0.5f),
                        startAngle = -90f + currentSweep,
                        sweepAngle = addedSweep,
                        useCenter = false,
                        style = Stroke(strokeW, cap = if (currentSweep + addedSweep >= 360f) StrokeCap.Square else StrokeCap.Round)
                    )
                }
            }
            Text(
                text = "+$totalPct%",
                style = MaterialTheme.typography.bodySmall,
                color = if (added > 0) color else color.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        val maxText = if (max % 1.0 == 0.0) max.toInt().toString() else String.format(Locale.US, "%.1f", max).trimEnd('0').trimEnd('.')
        Text(
            text = "z $maxText $unit",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}
