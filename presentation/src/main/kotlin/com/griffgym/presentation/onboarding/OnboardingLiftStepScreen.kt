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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.presentation.components.GriffGymBadge
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.GriffGymSecondaryButton
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.components.LabelledField
import com.griffgym.presentation.components.NumericInput
import com.griffgym.presentation.components.WeightAndRepsFields
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * One lift, one screen.
 *
 * Most lifters know a recent hard set rather than a true single, so the calculator is the
 * default and the direct entry is one tap away — not the other way round.
 */
@Composable
fun OnboardingLiftStepScreen(
    state: LiftStepUiState,
    onEvent: (OnboardingUiEvent) -> Unit,
    onBack: () -> Unit,
    onConfirmed: () -> Unit,
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
        OnboardingStepIndicator(stepNumber = state.stepNumber, stepCount = state.stepCount)

        Spacer(Modifier.height(24.dp))
        OnboardingHeading(
            title = "YOUR ${state.label} MAX",
            description = "Enter a set you know you can hit and we will estimate the one rep " +
                "max, or type it in directly.",
        )

        if (state.confirmedOneRepMax != null) {
            Spacer(Modifier.height(12.dp))
            GriffGymBadge(text = "SAVED ${state.confirmedOneRepMax} KG", filled = false)
        }

        Spacer(Modifier.height(20.dp))
        EntryModeToggle(
            mode = state.mode,
            onModeChange = { onEvent(OnboardingUiEvent.ModeChanged(state.category, it)) },
        )

        Spacer(Modifier.height(16.dp))
        GriffGymCard {
            when (state.mode) {
                OneRepMaxEntryMode.CALCULATOR -> {
                    WeightAndRepsFields(
                        weight = state.weightInput,
                        onWeightChange = {
                            onEvent(OnboardingUiEvent.WeightChanged(state.category, it))
                        },
                        reps = state.reps,
                        onRepsChange = {
                            onEvent(OnboardingUiEvent.RepsChanged(state.category, it))
                        },
                    )
                    Spacer(Modifier.height(18.dp))
                    HairLine()
                    EstimateReadout(
                        value = state.pendingOneRepMax,
                        isReliable = state.isEstimateReliable,
                    )
                }

                OneRepMaxEntryMode.DIRECT -> {
                    LabelledField(label = "One rep max (kg)") {
                        NumericInput(
                            value = state.oneRepMaxInput,
                            onValueChange = {
                                onEvent(OnboardingUiEvent.OneRepMaxChanged(state.category, it))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "0",
                            isError = state.error != null,
                            imeAction = ImeAction.Done,
                            textStyle = GriffGymTheme.typography.dataLarge,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "The heaviest single you are confident in today.",
                        style = GriffGymTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(10.dp))
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
            )
            GriffGymPrimaryButton(
                text = state.pendingOneRepMax?.let { "USE $it KG" } ?: "CONTINUE",
                onClick = {
                    onEvent(OnboardingUiEvent.Confirm(state.category))
                    onConfirmed()
                },
                modifier = Modifier.weight(1.4f),
                enabled = state.canConfirm,
            )
        }
    }
}

/** Two mutually exclusive ways to answer the same question. */
@Composable
private fun EntryModeToggle(
    mode: OneRepMaxEntryMode,
    onModeChange: (OneRepMaxEntryMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GriffGymSecondaryButton(
            text = "FROM A SET",
            onClick = { onModeChange(OneRepMaxEntryMode.CALCULATOR) },
            modifier = Modifier.weight(1f),
            color = if (mode == OneRepMaxEntryMode.CALCULATOR) colors.primary else colors.textTertiary,
        )
        GriffGymSecondaryButton(
            text = "I KNOW MY 1RM",
            onClick = { onModeChange(OneRepMaxEntryMode.DIRECT) },
            modifier = Modifier.weight(1f),
            color = if (mode == OneRepMaxEntryMode.DIRECT) colors.primary else colors.textTertiary,
        )
    }
}

@Composable
private fun EstimateReadout(value: String?, isReliable: Boolean) {
    val colors = GriffGymTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "ESTIMATED 1 REP MAX",
            style = GriffGymTheme.typography.label,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value ?: "—",
                style = GriffGymTheme.typography.dataHuge,
                color = colors.primary,
            )
            Text(
                text = "kg",
                style = GriffGymTheme.typography.dataLarge,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        if (!isReliable) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Past ten reps the estimate drifts. Use a heavier set if you can.",
                modifier = Modifier.fillMaxWidth(),
                style = GriffGymTheme.typography.bodySmall,
                color = colors.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun previewStep(
    mode: OneRepMaxEntryMode,
    pending: String?,
    weight: String = "160",
    oneRepMax: String = "",
    confirmed: String? = null,
) = LiftStepUiState(
    category = ExerciseCategory.SQUAT,
    label = "SQUAT",
    stepNumber = 1,
    stepCount = 3,
    mode = mode,
    weightInput = weight,
    reps = 5,
    oneRepMaxInput = oneRepMax,
    pendingOneRepMax = pending,
    isEstimateReliable = true,
    confirmedOneRepMax = confirmed,
    error = null,
)

@Preview(widthDp = 390, heightDp = 900, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun OnboardingLiftStepCalculatorPreview() {
    GriffGymTheme {
        OnboardingLiftStepScreen(
            state = previewStep(OneRepMaxEntryMode.CALCULATOR, pending = "186.7"),
            onEvent = {},
            onBack = {},
            onConfirmed = {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 900, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun OnboardingLiftStepDirectPreview() {
    GriffGymTheme {
        OnboardingLiftStepScreen(
            state = previewStep(
                mode = OneRepMaxEntryMode.DIRECT,
                pending = "210",
                oneRepMax = "210",
                confirmed = "210",
            ),
            onEvent = {},
            onBack = {},
            onConfirmed = {},
        )
    }
}
