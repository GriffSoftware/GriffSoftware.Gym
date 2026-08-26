package com.griffgym.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.GriffGymSecondaryButton
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.components.NumericInput
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * Last look before the block is written. Every row stays editable here, because the
 * summary is where a lifter notices they typed 105 instead of 150.
 */
@Composable
fun OnboardingSummaryScreen(
    state: OnboardingSummaryUiState,
    onEvent: (OnboardingUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    val margin = GriffGymTheme.dimens.screenMargin

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = margin, vertical = 24.dp),
    ) {
        OnboardingStepIndicator(
            stepNumber = OnboardingLifts.size,
            stepCount = OnboardingLifts.size,
            trailingLabel = "READY TO BUILD",
        )

        Spacer(Modifier.height(24.dp))
        OnboardingHeading(
            title = "YOUR NUMBERS",
            description = "Every prescribed load in the six week block is a percentage of " +
                "these. You can change them later from Home.",
        )

        Spacer(Modifier.height(24.dp))
        GriffGymCard(contentPadding = 0.dp) {
            Text(
                text = "REFERENCE MAXES",
                modifier = Modifier.padding(16.dp),
                style = GriffGymTheme.typography.headline,
                color = colors.textPrimary,
            )
            HairLine()
            state.lifts.forEachIndexed { index, lift ->
                SummaryRow(
                    lift = lift,
                    onValueChange = {
                        onEvent(OnboardingUiEvent.SummaryValueChanged(lift.category, it))
                    },
                    imeAction = if (index == state.lifts.lastIndex) ImeAction.Done else ImeAction.Next,
                )
                if (index != state.lifts.lastIndex) HairLine()
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.error,
                style = GriffGymTheme.typography.bodySmall,
                color = colors.error,
            )
        }

        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GriffGymSecondaryButton(
                text = "BACK",
                onClick = onBack,
                modifier = Modifier.weight(1f),
                enabled = !state.isBuilding,
            )
            GriffGymPrimaryButton(
                text = if (state.isBuilding) "BUILDING…" else "BUILD MY PROGRAM",
                onClick = { onEvent(OnboardingUiEvent.Build) },
                modifier = Modifier.weight(1.8f),
                enabled = state.canBuild,
            )
        }
    }
}

@Composable
private fun SummaryRow(
    lift: SummaryLiftUiState,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction,
) {
    val colors = GriffGymTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = lift.label,
            modifier = Modifier.weight(1f),
            style = GriffGymTheme.typography.title,
            color = colors.textPrimary,
        )
        NumericInput(
            value = lift.input,
            onValueChange = onValueChange,
            modifier = Modifier.width(110.dp),
            placeholder = "0",
            isError = !lift.isValid,
            imeAction = imeAction,
            textStyle = GriffGymTheme.typography.dataLarge,
        )
        Text(
            text = "kg",
            modifier = Modifier.padding(start = 8.dp),
            style = GriffGymTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
    }
}

private fun previewSummary(
    isBuilding: Boolean = false,
    error: String? = null,
    deadlift: String = "225",
) = OnboardingSummaryUiState(
    lifts = listOf(
        SummaryLiftUiState(ExerciseCategory.SQUAT, "SQUAT", "210"),
        SummaryLiftUiState(ExerciseCategory.BENCH_PRESS, "BENCH PRESS", "170"),
        SummaryLiftUiState(
            category = ExerciseCategory.DEADLIFT,
            label = "DEADLIFT",
            input = deadlift,
            error = if (deadlift.isBlank()) "Enter a weight above 0" else null,
        ),
    ),
    isBuilding = isBuilding,
    error = error,
)

@Preview(widthDp = 390, heightDp = 900, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun OnboardingSummaryScreenPreview() {
    GriffGymTheme {
        OnboardingSummaryScreen(state = previewSummary(), onEvent = {}, onBack = {})
    }
}

@Preview(widthDp = 390, heightDp = 900, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun OnboardingSummaryScreenIncompletePreview() {
    GriffGymTheme {
        OnboardingSummaryScreen(
            state = previewSummary(deadlift = "", error = "Could not build your program. Try again."),
            onEvent = {},
            onBack = {},
        )
    }
}
