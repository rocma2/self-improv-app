package com.ascend.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascend.app.domain.Point

@Composable
private fun AxisLabels(points: List<Point>) {
    Row(Modifier.fillMaxWidth()) {
        Text(points.firstOrNull()?.label ?: "", fontSize = 11.sp, color = C.muted)
        Spacer(Modifier.weight(1f))
        Text(points.lastOrNull()?.label ?: "", fontSize = 11.sp, color = C.muted)
    }
}

@Composable
fun EmptyChart(text: String) {
    Text(text, color = C.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().height(60.dp))
}

/** Bars, optional dashed target line, optional second stacked series (e.g. bike). */
@Composable
fun BarChart(points: List<Point>, color: Color, target: Double? = null, stacked: List<Point>? = null, stackColor: Color = C.cyan) {
    if (points.all { it.value == 0.0 } && (stacked?.all { it.value == 0.0 } != false)) { EmptyChart("No data yet. Log something!"); return }
    val totals = points.indices.map { i -> points[i].value + (stacked?.getOrNull(i)?.value ?: 0.0) }
    val max = maxOf(totals.maxOrNull() ?: 1.0, target ?: 0.0, 1.0)
    Column {
        Text("max %.0f".format(max), fontSize = 11.sp, color = C.muted)
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val n = points.size
            val slot = size.width / n
            val bw = slot * 0.6f
            for (i in 0 until n) {
                val x = i * slot + (slot - bw) / 2
                val h1 = (points[i].value / max * size.height).toFloat()
                val h2 = ((stacked?.getOrNull(i)?.value ?: 0.0) / max * size.height).toFloat()
                if (h2 > 0) drawRoundRect(stackColor.copy(alpha = 0.45f), Offset(x, size.height - h1 - h2), Size(bw, h2), CornerRadius(4f, 4f))
                if (h1 > 0) drawRoundRect(color, Offset(x, size.height - h1), Size(bw, h1), CornerRadius(4f, 4f))
            }
            target?.let {
                val y = (size.height - it / max * size.height).toFloat()
                drawLine(C.gold, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
        }
        AxisLabels(points)
    }
}

/** Line chart. [format] renders y values for the min/max labels. */
@Composable
fun LineChart(points: List<Point>, color: Color, format: (Double) -> String, target: Double? = null) {
    if (points.size < 2) { EmptyChart("Needs at least 2 entries."); return }
    val values = points.map { it.value } + listOfNotNull(target)
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 0 } ?: 1.0
    Column {
        Text("${format(min)} – ${format(max)}", fontSize = 11.sp, color = C.muted)
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            fun y(v: Double) = (size.height - (v - min) / span * (size.height - 12f) - 6f).toFloat()
            val step = size.width / (points.size - 1)
            target?.let {
                drawLine(C.gold, Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
            val path = Path()
            points.forEachIndexed { i, p -> if (i == 0) path.moveTo(0f, y(p.value)) else path.lineTo(i * step, y(p.value)) }
            drawPath(path, color, style = Stroke(width = 5f))
            points.forEachIndexed { i, p -> drawCircle(color, 6f, Offset(i * step, y(p.value))) }
        }
        AxisLabels(points)
    }
}
