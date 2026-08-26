package com.griffgym.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.presentation.components.GriffGymBadge
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.components.MetricColumn
import com.griffgym.presentation.components.StatusBadge
import com.griffgym.presentation.components.WorkoutUiStatus
import com.griffgym.presentation.theme.GriffGymTheme

@Composable
fun HistoryRoute(
    onOpenSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state = state, onOpenSession = onOpenSession, modifier = modifier)
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = margin, end = margin, top = margin, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(GriffGymTheme.dimens.sectionSpacing),
    ) {
        item(key = "title") {
            Text(
                text = "HISTORY",
                style = GriffGymTheme.typography.displayLarge,
                color = colors.textPrimary,
            )
        }

        if (state.sessions.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "No sessions logged yet. Finish a workout and it shows up here.",
                    style = GriffGymTheme.typography.body,
                    color = colors.textTertiary,
                )
            }
        }

        items(state.sessions, key = { it.sessionId }) { session ->
            GriffGymCard(onClick = { onOpenSession(session.sessionId) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = session.date,
                            style = GriffGymTheme.typography.labelSmall,
                            color = colors.textTertiary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = session.title,
                            style = GriffGymTheme.typography.title,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = session.subtitle,
                            style = GriffGymTheme.typography.bodySmall,
                            color = colors.textTertiary,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        StatusBadge(session.status)
                        if (session.isDeload) {
                            Spacer(Modifier.height(6.dp))
                            GriffGymBadge("DELOAD", color = colors.bench)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                HairLine()
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricColumn("VOLUME", session.volume, valueColor = colors.primary)
                    MetricColumn("DURATION", session.duration)
                    MetricColumn("SETS", session.sets)
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 700, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    GriffGymTheme {
        HistoryScreen(
            state = HistoryUiState(
                isLoading = false,
                sessions = listOf(
                    HistoryItem(
                        sessionId = 1,
                        date = "24 AUG 2026",
                        title = "WEEK 3, DAY I",
                        subtitle = "Squat Focus / Bench Volume",
                        status = WorkoutUiStatus.COMPLETED,
                        volume = "12.4t",
                        duration = "1h 12min",
                        sets = "11 / 11",
                        isDeload = false,
                    ),
                ),
            ),
            onOpenSession = {},
        )
    }
}
