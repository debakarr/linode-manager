package com.linode.manager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Area line chart for Linode stats (24 h of 5-minute samples). Shows the
 * latest, average and peak values so the chart is readable without axes.
 */
@Composable
fun MetricChart(
    values: List<Double>,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val pts = values.takeLast(288)
    if (pts.size < 2) {
        Text("Not enough data yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val max = pts.max().coerceAtLeast(0.0001)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Now", format(pts.last()))
            Stat("Average", format(pts.average()))
            Stat("Peak", format(pts.max()))
        }
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            for (i in 0..3) {
                val y = size.height * i / 3f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            val stepX = size.width / (pts.size - 1)
            val line = Path()
            pts.forEachIndexed { i, v ->
                val x = i * stepX
                val y = size.height - (v / max * size.height * 0.92).toFloat()
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            val area =
                Path().apply {
                    addPath(line)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f))))
            drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Text(
            "Last 24 hours",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
