package com.griffgym.presentation.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.presentation.components.CardHeader
import com.griffgym.presentation.components.ChartPoint
import com.griffgym.presentation.components.ChartSeries
import com.griffgym.presentation.components.ConsistencyCalendar
import com.griffgym.presentation.components.GriffGymBadge
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.components.MetricColumn
import com.griffgym.presentation.components.OneRmChart
import com.griffgym.presentation.components.buildCalendarDays
import com.griffgym.presentation.theme.GriffGymTheme
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun StatsRoute(
    onOpenSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(state = state, onOpenSession = onOpenSession, modifier = modifier)
}

@Composable
fun StatsScreen(
    state: StatsUiState,
    onOpenSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    val margin = GriffGymTheme.dimens.screenMargin

    if (state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.primary)
        }
        return
    }

    // The visible month is pure view state: it survives rotation but has no business
    // meaning, so it belongs in the composition rather than the ViewModel.
    var visibleMonth by rememberSaveable(stateSaver = YearMonthSaver) {
        mutableStateOf(YearMonth.now())
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = margin, end = margin, top = margin, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(GriffGymTheme.dimens.sectionSpacing),
    ) {
        item(key = "title") {
            Column {
                Text(
                    text = "PROGRESS",
                    style = GriffGymTheme.typography.displayLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = "STATISTICS",
                    style = GriffGymTheme.typography.displayLarge,
                    color = colors.textPrimary,
                )
            }
        }

        item(key = "totals") {
            GriffGymCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricColumn("SESSIONS LOGGED", state.totalSessions.toString())
                    MetricColumn(
                        "TOTAL VOLUME",
                        state.totalVolume,
                        valueColor = colors.primary,
                    )
                }
            }
        }

        item(key = "progression") {
            GriffGymCard {
                CardHeader(title = "BIG 3 — 1RM PROGRESSION")
                Spacer(Modifier.height(16.dp))
                OneRmChart(
                    series = state.progression.map { series ->
                        ChartSeries(
                            label = series.label,
                            color = colorFor(series.category),
                            points = series.points.map { ChartPoint(it.date, it.estimated) },
                        )
                    },
                )
            }
        }

        item(key = "records") {
            GriffGymCard {
                CardHeader(
                    title = "PERSONAL RECORDS",
                    action = {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
                Spacer(Modifier.height(6.dp))
                state.personalRecords.forEach { record ->
                    PersonalRecordRow(record)
                    HairLine()
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Records come from sets logged in the app. " +
                        "Reference maxes are planning numbers and never count as a record.",
                    style = GriffGymTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }

        item(key = "consistency") {
            GriffGymCard {
                CardHeader(
                    title = "TRAINING CONSISTENCY",
                    action = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.ChevronLeft,
                                contentDescription = "Previous month",
                                tint = colors.textSecondary,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable { visibleMonth = visibleMonth.minusMonths(1) },
                            )
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = "Next month",
                                tint = colors.textSecondary,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable { visibleMonth = visibleMonth.plusMonths(1) },
                            )
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${visibleMonth.month.name} ${visibleMonth.year}",
                    style = GriffGymTheme.typography.label,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                ConsistencyCalendar(
                    days = buildCalendarDays(
                        month = visibleMonth,
                        trainedDays = state.trainedDays.mapValues {
                            it.value.sessionId to it.value.hasPersonalRecord
                        },
                    ),
                    onDayClick = onOpenSession,
                )
            }
        }
    }
}

@Composable
private fun PersonalRecordRow(record: PersonalRecordItem) {
    val colors = GriffGymTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = record.label,
                style = GriffGymTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = record.actual ?: record.estimated ?: "—",
                style = GriffGymTheme.typography.dataLarge,
                color = if (record.actual != null) colors.primary else colors.textSecondary,
            )
            val caption = when {
                record.actual != null && record.actualDate != null -> record.actualDate
                record.estimatedSource != null -> record.estimatedSource
                record.estimated != null -> "estimated"
                else -> "no logged sets yet"
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                style = GriffGymTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
        }
        GriffGymBadge(
            text = if (record.actual != null) "1RM" else "e1RM",
            filled = record.actual != null,
            color = if (record.actual != null) colors.primary else colors.textTertiary,
        )
    }
}

@Composable
private fun colorFor(category: ExerciseCategory) = when (category) {
    ExerciseCategory.SQUAT -> GriffGymTheme.colors.squat
    ExerciseCategory.DEADLIFT -> GriffGymTheme.colors.deadlift
    ExerciseCategory.BENCH_PRESS -> GriffGymTheme.colors.bench
    ExerciseCategory.ACCESSORY -> GriffGymTheme.colors.textTertiary
}

private val YearMonthSaver: Saver<YearMonth, String> = Saver(
    save = { it.toString() },
    restore = { YearMonth.parse(it) },
)

@Preview(widthDp = 390, heightDp = 900, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun StatsScreenPreview() {
    GriffGymTheme {
        val base = LocalDate.now().minusWeeks(6)
        StatsScreen(
            state = StatsUiState(
                isLoading = false,
                totalSessions = 12,
                totalVolume = "84.2t",
                progression = listOf(
                    ProgressionSeries(
                        ExerciseCategory.SQUAT,
                        "SQUAT",
                        List(6) { ProgressionPoint(base.plusWeeks(it.toLong()), 200.0 + it * 4) },
                    ),
                    ProgressionSeries(
                        ExerciseCategory.DEADLIFT,
                        "DEADLIFT",
                        List(6) { ProgressionPoint(base.plusWeeks(it.toLong()), 220.0 + it * 3) },
                    ),
                    ProgressionSeries(
                        ExerciseCategory.BENCH_PRESS,
                        "BENCH",
                        List(6) { ProgressionPoint(base.plusWeeks(it.toLong()), 160.0 + it * 2) },
                    ),
                ),
                personalRecords = listOf(
                    PersonalRecordItem(
                        ExerciseCategory.SQUAT,
                        "SQUAT",
                        "200 kg",
                        "12 MAY 2026",
                        "212 kg",
                        null,
                    ),
                    PersonalRecordItem(
                        ExerciseCategory.DEADLIFT,
                        "DEADLIFT",
                        null,
                        null,
                        "225 kg",
                        "from 205 kg x 3",
                    ),
                    PersonalRecordItem(
                        ExerciseCategory.BENCH_PRESS,
                        "BENCH PRESS",
                        null,
                        null,
                        null,
                        null,
                    ),
                ),
            ),
            onOpenSession = {},
        )
    }
}
