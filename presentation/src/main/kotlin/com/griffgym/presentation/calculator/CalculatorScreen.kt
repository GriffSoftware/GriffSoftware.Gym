package com.griffgym.presentation.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griffgym.presentation.components.FieldLabel
import com.griffgym.presentation.components.GriffGymCard
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.GriffGymSecondaryButton
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.components.NumericInput
import com.griffgym.presentation.components.NumericStepper
import com.griffgym.presentation.theme.GriffGymTheme

@Composable
fun CalculatorRoute(
    modifier: Modifier = Modifier,
    viewModel: CalculatorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CalculatorScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
fun CalculatorScreen(
    state: CalculatorUiState,
    onEvent: (CalculatorUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    val margin = GriffGymTheme.dimens.screenMargin

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = margin, end = margin, top = margin, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(GriffGymTheme.dimens.sectionSpacing),
    ) {
        item(key = "form") {
            GriffGymCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "CALCULATE 1RM",
                        style = GriffGymTheme.typography.headline,
                        color = colors.textPrimary,
                    )
                    GriffGymSecondaryButton(
                        text = "USE MAXES",
                        onClick = { onEvent(CalculatorUiEvent.OpenMaxPicker) },
                        color = colors.textSecondary,
                    )
                }

                Spacer(Modifier.height(18.dp))
                LabelledField(label = "Weight (kg)") {
                    NumericInput(
                        value = state.weightInput,
                        onValueChange = { onEvent(CalculatorUiEvent.WeightChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "0",
                        textStyle = GriffGymTheme.typography.dataLarge,
                    )
                }

                Spacer(Modifier.height(14.dp))
                LabelledField(label = "Reps") {
                    NumericStepper(
                        value = state.reps,
                        onValueChange = { onEvent(CalculatorUiEvent.RepsChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = state.error,
                        style = GriffGymTheme.typography.bodySmall,
                        color = colors.error,
                    )
                }

                Spacer(Modifier.height(20.dp))
                GriffGymPrimaryButton(
                    text = "CALCULATE",
                    onClick = { onEvent(CalculatorUiEvent.Calculate) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "result") {
            GriffGymCard(accentBar = colors.primary) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "ESTIMATED 1 REP MAX",
                    modifier = Modifier.fillMaxWidth(),
                    style = GriffGymTheme.typography.label,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = state.result?.oneRepMax ?: "—",
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
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Based on ${CalculatorViewModel.FORMULA_NAME} formula. " +
                        "Accuracy decreases rapidly past 10 reps.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    style = GriffGymTheme.typography.body,
                    color = if (state.result?.isReliable == false) colors.error else colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
            }
        }

        if (state.percentages.isNotEmpty()) {
            item(key = "percentages") {
                GriffGymCard(contentPadding = 0.dp) {
                    Text(
                        text = "TRAINING PERCENTAGES",
                        modifier = Modifier.padding(16.dp),
                        style = GriffGymTheme.typography.headline,
                        color = colors.textPrimary,
                    )
                    HairLine()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        PercentHeader("% OF 1RM", 1.1f)
                        PercentHeader("WEIGHT (KG)", 1.2f)
                        PercentHeader("TARGET REPS", 1.2f)
                    }
                    state.percentages.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            PercentCell(
                                row.percent,
                                1.1f,
                                if (row.isMax) colors.primary else colors.textPrimary,
                            )
                            PercentCell(row.weight, 1.2f, colors.textPrimary)
                            PercentCell(row.reps, 1.2f, colors.textSecondary)
                        }
                        HairLine()
                    }
                }
            }
        }
    }

    if (state.showMaxPicker) {
        UseMaxesDialog(
            state = state,
            onSelect = { onEvent(CalculatorUiEvent.UseReferenceMax(it)) },
            onDismiss = { onEvent(CalculatorUiEvent.DismissMaxPicker) },
        )
    }
}

@Composable
private fun LabelledField(label: String, content: @Composable () -> Unit) {
    val colors = GriffGymTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outlineStrong)
            .padding(12.dp),
    ) {
        FieldLabel(label)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PercentHeader(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = GriffGymTheme.typography.labelSmall,
        color = GriffGymTheme.colors.textTertiary,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PercentCell(
    text: String,
    weight: Float,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = GriffGymTheme.typography.data.copy(textAlign = TextAlign.Start),
        color = color,
    )
}

@Composable
private fun UseMaxesDialog(
    state: CalculatorUiState,
    onSelect: (com.griffgym.domain.model.ExerciseCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = GriffGymTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .border(1.dp, colors.outlineStrong)
                .padding(20.dp),
        ) {
            Text(
                text = "USE A REFERENCE MAX",
                style = GriffGymTheme.typography.label,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(14.dp))
            state.referenceMaxes.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option.category) }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = option.label,
                        style = GriffGymTheme.typography.body,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "${option.weight} kg",
                        style = GriffGymTheme.typography.data,
                        color = colors.primary,
                    )
                }
                HairLine()
            }
            Spacer(Modifier.height(16.dp))
            GriffGymSecondaryButton(
                text = "CLOSE",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 900, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun CalculatorScreenPreview() {
    GriffGymTheme {
        CalculatorScreen(
            state = CalculatorUiState(
                weightInput = "100",
                reps = 5,
                result = CalculatorResult("116.7", isReliable = true),
                percentages = listOf(
                    PercentageRow("100%", "116.7", "1", isMax = true),
                    PercentageRow("95%", "110.9", "2", isMax = false),
                    PercentageRow("90%", "105", "3 - 4", isMax = false),
                    PercentageRow("85%", "99.2", "5 - 6", isMax = false),
                    PercentageRow("80%", "93.4", "7 - 8", isMax = false),
                ),
            ),
            onEvent = {},
        )
    }
}
