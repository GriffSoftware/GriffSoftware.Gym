package com.griffgym.presentation.home

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.presentation.components.CardHeader
import com.griffgym.presentation.components.DeloadBadgeLabel
import com.griffgym.presentation.components.accentFor
import com.griffgym.presentation.components.GriffGymBadge
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.components.ReferenceMaxRow
import com.griffgym.presentation.components.TrainingSummaryRow
import com.griffgym.presentation.components.VolumeBar
import com.griffgym.presentation.components.VolumeTrendChart
import com.griffgym.presentation.format.Format
import com.griffgym.presentation.theme.GriffGymTheme

@Composable
fun HomeRoute(
    onOpenWorkout: (Long) -> Unit,
    onOpenCycles: () -> Unit,
    onReviewCycle: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.navigation.collect { event ->
            when (event) {
                is HomeNavigation.OpenWorkout -> onOpenWorkout(event.sessionId)
            }
        }
    }

    HomeScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenCycles = onOpenCycles,
        onReviewCycle = onReviewCycle,
        modifier = modifier,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeUiEvent) -> Unit,
    onOpenCycles: () -> Unit,
    onReviewCycle: () -> Unit,
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
        contentPadding = PaddingValues(margin),
        verticalArrangement = Arrangement.spacedBy(GriffGymTheme.dimens.sectionSpacing),
    ) {
        state.hero?.let { hero ->
            item(key = "hero") {
                HeroCard(
                    hero = hero,
                    onEvent = onEvent,
                    onOpenCycles = onOpenCycles,
                    onReviewCycle = onReviewCycle,
                )
            }
        }

        item(key = "volume") {
            GriffGymCard {
                CardHeader(title = "Volume Trend")
                Spacer(Modifier.height(16.dp))
                VolumeTrendChart(
                    bars = state.volumeTrend.map {
                        VolumeBar(
                            label = it.label,
                            ratio = it.ratio,
                            highlighted = it.isToday,
                            hasData = it.trained,
                        )
                    },
                )
            }
        }

        item(key = "reference") {
            GriffGymCard {
                CardHeader(
                    title = "1RM Reference",
                    titleColor = colors.textSecondary,
                    action = {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                Spacer(Modifier.height(14.dp))
                state.referenceMaxes.forEachIndexed { index, item ->
                    ReferenceMaxRow(
                        code = item.code,
                        weight = item.weight,
                        accent = colors.accentFor(item.category),
                        onClick = { onEvent(HomeUiEvent.EditReferenceMax(item.category)) },
                    )
                    if (index != state.referenceMaxes.lastIndex) Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Tap a lift to update its reference max.",
                    style = GriffGymTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }
    }

    state.editingReferenceMax?.let { editor ->
        ReferenceMaxDialog(
            editor = editor,
            onInputChange = { onEvent(HomeUiEvent.ReferenceMaxInputChanged(it)) },
            onConfirm = { onEvent(HomeUiEvent.ConfirmReferenceMax) },
            onDismiss = { onEvent(HomeUiEvent.DismissReferenceMaxEditor) },
        )
    }
}

@Composable
private fun HeroCard(
    hero: HeroCardState,
    onEvent: (HomeUiEvent) -> Unit,
    onOpenCycles: () -> Unit,
    onReviewCycle: () -> Unit,
) {
    val colors = GriffGymTheme.colors
    val isCycleComplete = hero.mode == HeroMode.CYCLE_COMPLETE
    val hasWorkout = hero.mode == HeroMode.READY || hero.mode == HeroMode.IN_PROGRESS

    GriffGymCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                when {
                    isCycleComplete -> GriffGymBadge("CYCLE COMPLETE", filled = true)
                    // The deload week says outright how light it is, so nobody spends a
                    // recovery week wondering whether the numbers are a mistake.
                    hero.isDeload -> GriffGymBadge(DeloadBadgeLabel, filled = true)
                    hero.mode == HeroMode.IN_PROGRESS ->
                        GriffGymBadge("IN PROGRESS", color = colors.primary)
                    hero.mode == HeroMode.NO_PROGRAM ->
                        GriffGymBadge("NO PROGRAM", color = colors.textTertiary)
                    else -> GriffGymBadge("READY", color = colors.textTertiary)
                }

                hero.cycleLabel?.let { label ->
                    Spacer(Modifier.height(8.dp))
                    CycleContextLabel(label = label, onClick = onOpenCycles)
                }

                Spacer(Modifier.height(if (hero.cycleLabel != null) 2.dp else 8.dp))
                Text(
                    text = if (hasWorkout) {
                        Format.weekAndDayTitle(hero.weekNumber, hero.dayNumber)
                    } else if (isCycleComplete) {
                        "Cycle complete"
                    } else {
                        "Nothing planned"
                    },
                    style = GriffGymTheme.typography.headline,
                    color = colors.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = hero.title,
                    style = GriffGymTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }

            when {
                hasWorkout -> GriffGymPrimaryButton(
                    text = if (hero.mode == HeroMode.IN_PROGRESS) "CONTINUE" else "START",
                    icon = Icons.Filled.PlayArrow,
                    onClick = {
                        onEvent(
                            if (hero.mode == HeroMode.IN_PROGRESS) {
                                HomeUiEvent.ContinueWorkout
                            } else {
                                HomeUiEvent.StartWorkout
                            },
                        )
                    },
                    contentPaddingHorizontal = 14.dp,
                )

                // The one thing left to do at the end of a cycle. Without it this card was a
                // dead end: a badge saying "done" and nowhere to go.
                isCycleComplete -> GriffGymPrimaryButton(
                    text = "REVIEW CYCLE",
                    onClick = onReviewCycle,
                    contentPaddingHorizontal = 14.dp,
                )
            }
        }

        if (hero.exercises.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HairLine()
            Spacer(Modifier.height(4.dp))
            hero.exercises.forEach { exercise ->
                TrainingSummaryRow(
                    name = exercise.name,
                    badge = exercise.badge,
                    scheme = exercise.scheme,
                    badgeFilled = exercise.isTopSet,
                )
            }
        }
    }
}

/** "CYCLE 3 ›" — small, quiet context that doubles as the way into the cycles screen. */
@Composable
private fun CycleContextLabel(label: String, onClick: () -> Unit) {
    val colors = GriffGymTheme.colors
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = GriffGymTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(14.dp),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun HomeScreenPreview() {
    GriffGymTheme {
        HomeScreen(state = PreviewHomeState, onEvent = {}, onOpenCycles = {}, onReviewCycle = {})
    }
}

@Preview(widthDp = 390, heightDp = 400, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun HomeScreenDeloadPreview() {
    GriffGymTheme {
        HomeScreen(
            state = PreviewHomeState.copy(
                hero = PreviewHomeState.hero?.copy(
                    weekNumber = 6,
                    dayNumber = 1,
                    title = "Deload — Squat / Bench",
                    isDeload = true,
                    exercises = listOf(
                        HeroExercise("Przysiad", "DELOAD", isTopSet = false, scheme = "3x3x105kg"),
                        HeroExercise("Ławka", "DELOAD", isTopSet = false, scheme = "3x5x85kg"),
                    ),
                ),
            ),
            onEvent = {},
            onOpenCycles = {},
            onReviewCycle = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 400, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun HomeScreenCycleCompletePreview() {
    GriffGymTheme {
        HomeScreen(
            state = PreviewHomeState.copy(
                hero = HeroCardState(
                    weekNumber = 0,
                    dayNumber = 0,
                    title = "Every week of this cycle is behind you. " +
                        "Decide where the next one starts.",
                    isDeload = false,
                    mode = HeroMode.CYCLE_COMPLETE,
                    exercises = emptyList(),
                    cycleLabel = "CYCLE 3",
                ),
            ),
            onEvent = {},
            onOpenCycles = {},
            onReviewCycle = {},
        )
    }
}

internal val PreviewHomeState = HomeUiState(
    isLoading = false,
    hero = HeroCardState(
        weekNumber = 3,
        dayNumber = 1,
        title = "Squat Focus / Bench Volume",
        isDeload = false,
        mode = HeroMode.READY,
        cycleLabel = "CYCLE 3",
        exercises = listOf(
            HeroExercise("Przysiad", "TOP", isTopSet = true, scheme = "1x3x192.5kg"),
            HeroExercise("Przysiad", "BACK-OFF", isTopSet = false, scheme = "3x3x180kg"),
            HeroExercise("Ławka", "VOLUME", isTopSet = false, scheme = "4x6x127.5kg"),
        ),
    ),
    volumeTrend = listOf(
        VolumeTrendDay("M", 0.4f, isToday = false, trained = true),
        VolumeTrendDay("T", 0.0f, isToday = false, trained = false),
        VolumeTrendDay("W", 0.7f, isToday = false, trained = true),
        VolumeTrendDay("T", 1.0f, isToday = true, trained = true),
        VolumeTrendDay("F", 0.0f, isToday = false, trained = false),
        VolumeTrendDay("S", 0.0f, isToday = false, trained = false),
        VolumeTrendDay("S", 0.5f, isToday = false, trained = true),
    ),
    referenceMaxes = listOf(
        ReferenceMaxItem(ExerciseCategory.SQUAT, "SQ", "210"),
        ReferenceMaxItem(ExerciseCategory.DEADLIFT, "DL", "225"),
        ReferenceMaxItem(ExerciseCategory.BENCH_PRESS, "BP", "170"),
    ),
)
