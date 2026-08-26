package com.griffgym.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.griffgym.domain.model.Rpe
import com.griffgym.presentation.theme.GriffGymTheme

/**
 * RPE entry, 1.0 to 10.0 in half steps.
 *
 * Highlighted amber whenever the value parses: intensity is the number the lifter
 * actually reasons about between sets.
 */
@Composable
fun RpeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
) {
    val parsed = Rpe.parse(value)
    NumericInput(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = "RPE",
        enabled = enabled,
        allowDecimal = true,
        isError = value.isNotBlank() && parsed == null,
        highlighted = parsed != null,
        imeAction = imeAction,
    )
}

/** The `–` / value / `+` stepper used by the calculator. */
@Composable
fun NumericStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..30,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepperButton(
            symbol = "–",
            enabled = value > range.first,
            onClick = { onValueChange((value - 1).coerceIn(range)) },
        )
        Text(
            text = value.toString(),
            style = GriffGymTheme.typography.dataLarge,
            color = GriffGymTheme.colors.textPrimary,
        )
        StepperButton(
            symbol = "+",
            enabled = value < range.last,
            onClick = { onValueChange((value + 1).coerceIn(range)) },
        )
    }
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = GriffGymTheme.colors
    val shape = GriffGymTheme.shapes.input
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(GriffGymTheme.dimens.touchTarget)
            .clip(shape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outlineStrong, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = GriffGymTheme.typography.dataLarge,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
        )
    }
}

/** A boxed field label in the design's caps style, used above inputs on the calculator. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = GriffGymTheme.typography.label,
        color = GriffGymTheme.colors.textTertiary,
    )
}
