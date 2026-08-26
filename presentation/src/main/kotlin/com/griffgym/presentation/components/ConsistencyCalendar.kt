package com.griffgym.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.griffgym.presentation.theme.GriffGymTheme
import java.time.LocalDate
import java.time.YearMonth

@Immutable
data class CalendarDay(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val sessionId: Long?,
    val hasPersonalRecord: Boolean,
)

/**
 * A month of training as a contribution grid: amber for a logged session, charcoal for
 * rest. Tapping a trained day opens that session.
 */
@Composable
fun ConsistencyCalendar(
    days: List<CalendarDay>,
    onDayClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = GriffGymTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        days.chunked(DAYS_IN_WEEK).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { day -> DayCell(day, onDayClick, Modifier.weight(1f)) }
                repeat(DAYS_IN_WEEK - week.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendSwatch("WORKOUT LOGGED", filled = true)
            LegendSwatch("PR ACHIEVED", filled = false)
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, onDayClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    val colors = GriffGymTheme.colors
    val trained = day.sessionId != null
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(
                when {
                    trained -> colors.primary
                    day.inCurrentMonth -> colors.surfaceLowest
                    else -> colors.background
                },
            )
            .then(
                if (day.hasPersonalRecord) {
                    Modifier.border(2.dp, colors.bench)
                } else {
                    Modifier
                },
            )
            .then(
                if (trained) {
                    Modifier.clickable { onDayClick(day.sessionId!!) }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = GriffGymTheme.typography.dataSmall,
            color = when {
                trained -> colors.onPrimary
                day.inCurrentMonth -> colors.textSecondary
                else -> colors.textTertiary
            },
        )
    }
}

@Composable
private fun LegendSwatch(label: String, filled: Boolean) {
    val colors = GriffGymTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .then(
                    if (filled) {
                        Modifier.background(colors.primary)
                    } else {
                        Modifier.border(2.dp, colors.bench)
                    },
                ),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = GriffGymTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }
}

private const val DAYS_IN_WEEK = 7

/**
 * Lays out [month] as whole Monday-to-Sunday rows, padding both ends with the neighbouring
 * days so the grid never has holes.
 */
fun buildCalendarDays(
    month: YearMonth,
    trainedDays: Map<LocalDate, Pair<Long, Boolean>>,
): List<CalendarDay> {
    val firstOfMonth = month.atDay(1)
    val leadingDays = (firstOfMonth.dayOfWeek.value - 1)
    val start = firstOfMonth.minusDays(leadingDays.toLong())
    val totalCells = ((leadingDays + month.lengthOfMonth() + 6) / DAYS_IN_WEEK) * DAYS_IN_WEEK

    return (0 until totalCells).map { offset ->
        val date = start.plusDays(offset.toLong())
        val entry = trainedDays[date]
        CalendarDay(
            date = date,
            inCurrentMonth = YearMonth.from(date) == month,
            sessionId = entry?.first,
            hasPersonalRecord = entry?.second == true,
        )
    }
}

@Preview(widthDp = 358, backgroundColor = 0xFF2A2721, showBackground = true)
@Composable
private fun ConsistencyCalendarPreview() {
    GriffGymTheme {
        val month = YearMonth.of(2026, 8)
        ConsistencyCalendar(
            days = buildCalendarDays(
                month = month,
                trainedDays = mapOf(
                    month.atDay(3) to (1L to false),
                    month.atDay(5) to (2L to true),
                    month.atDay(8) to (3L to false),
                ),
            ),
            onDayClick = {},
        )
    }
}
