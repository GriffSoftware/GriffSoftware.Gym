package com.griffgym.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.griffgym.presentation.theme.GriffGymTheme
import java.time.LocalDate

@Immutable
data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<ChartPoint>,
)

@Immutable
data class ChartPoint(val date: LocalDate, val value: Double)

/**
 * Estimated 1RM over time for the big three.
 *
 * Drawn on a Compose [Canvas] rather than pulled in from a charting library: three
 * polylines and two grid rules do not justify the dependency, and hand-drawing keeps the
 * flat, gradient-free look the rest of the design insists on.
 *
 * All series share one vertical scale so the lines stay comparable, and one horizontal
 * scale spanning the full logged date range so a gap in training reads as a gap.
 */
@Composable
fun OneRmChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 190.dp,
) {
    val colors = GriffGymTheme.colors
    val allPoints = series.flatMap { it.points }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            series.forEach { LegendEntry(it.label, it.color) }
        }
        Spacer(Modifier.height(14.dp))

        if (allPoints.size < 2) {
            EmptyChartPlaceholder(height)
        } else {
            val minValue = allPoints.minOf { it.value }
            val maxValue = allPoints.maxOf { it.value }
            val minDay = allPoints.minOf { it.date.toEpochDay() }
            val maxDay = allPoints.maxOf { it.date.toEpochDay() }
            // A flat block of identical estimates would divide by zero; pad the range instead.
            val valueSpan = (maxValue - minValue).takeIf { it > 0.5 } ?: 1.0
            val daySpan = (maxDay - minDay).takeIf { it > 0L } ?: 1L

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
            ) {
                val padding = 8.dp.toPx()
                val usableHeight = size.height - padding * 2
                val usableWidth = size.width

                repeat(3) { index ->
                    val y = padding + usableHeight * (index + 1) / 4f
                    drawLine(
                        color = colors.outline,
                        start = Offset(0f, y),
                        end = Offset(usableWidth, y),
                        strokeWidth = 1f,
                    )
                }

                series.forEach { line ->
                    if (line.points.size < 2) return@forEach
                    val path = androidx.compose.ui.graphics.Path()
                    line.points.sortedBy { it.date }.forEachIndexed { index, point ->
                        val x = usableWidth * (point.date.toEpochDay() - minDay) / daySpan.toFloat()
                        val ratio = ((point.value - minValue) / valueSpan).toFloat()
                        val y = padding + usableHeight * (1f - ratio)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = line.color,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = com.griffgym.presentation.format.Format.month(
                        LocalDate.ofEpochDay(minDay),
                    ),
                    style = GriffGymTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
                Text(
                    text = com.griffgym.presentation.format.Format.month(
                        LocalDate.ofEpochDay(maxDay),
                    ),
                    style = GriffGymTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun EmptyChartPlaceholder(height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .dashedBorder(GriffGymTheme.colors.outline),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "COMPLETE TWO SESSIONS TO SEE PROGRESSION",
            style = GriffGymTheme.typography.labelSmall,
            color = GriffGymTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun LegendEntry(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = GriffGymTheme.typography.labelSmall,
            color = GriffGymTheme.colors.textSecondary,
        )
    }
}

@Preview(widthDp = 358, backgroundColor = 0xFF2A2721, showBackground = true)
@Composable
private fun OneRmChartPreview() {
    GriffGymTheme {
        val base = LocalDate.of(2026, 1, 5)
        OneRmChart(
            series = listOf(
                ChartSeries(
                    "SQUAT",
                    GriffGymTheme.colors.squat,
                    List(6) { ChartPoint(base.plusWeeks(it.toLong()), 200.0 + it * 4) },
                ),
                ChartSeries(
                    "DEADLIFT",
                    GriffGymTheme.colors.deadlift,
                    List(6) { ChartPoint(base.plusWeeks(it.toLong()), 220.0 + it * 3) },
                ),
                ChartSeries(
                    "BENCH",
                    GriffGymTheme.colors.bench,
                    List(6) { ChartPoint(base.plusWeeks(it.toLong()), 160.0 + it * 2) },
                ),
            ),
            modifier = Modifier.background(GriffGymTheme.colors.surface),
        )
    }
}
