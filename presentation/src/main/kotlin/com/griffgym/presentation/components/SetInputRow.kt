package com.griffgym.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.griffgym.presentation.theme.GriffGymTheme

/** One editable row of the set table, driven entirely by immutable state from above. */
@Immutable
data class SetRowState(
    val setLogId: Long,
    val index: Int,
    val weight: String,
    val reps: String,
    val rpe: String,
    val completed: Boolean,
    val hasNotes: Boolean,
    val weightInvalid: Boolean = false,
    val repsInvalid: Boolean = false,
    val rpeInvalid: Boolean = false,
)

/** Column weights shared by the header, the target row and every set row. */
private const val WEIGHT_SET = 1.4f
private const val WEIGHT_KG = 2.2f
private const val WEIGHT_REPS = 1.9f
private const val WEIGHT_RPE = 1.9f
private val ActionWidth = 40.dp

@Composable
fun SetTableHeader(modifier: Modifier = Modifier) {
    val colors = GriffGymTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HeaderCell("SET", WEIGHT_SET)
        HeaderCell("KG", WEIGHT_KG)
        HeaderCell("REPS", WEIGHT_REPS)
        HeaderCell("RPE", WEIGHT_RPE)
        Spacer(Modifier.width(ActionWidth))
        Spacer(Modifier.width(ActionWidth))
    }
    HairLine(color = colors.surfaceVariant)
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = GriffGymTheme.typography.label,
        color = GriffGymTheme.colors.textTertiary,
        textAlign = TextAlign.Center,
    )
}

/**
 * The prescription snapshotted when the session started, shown above the editable rows so
 * the lifter always sees what the plan asked for next to what actually happened.
 */
@Composable
fun TargetRow(
    weight: String,
    reps: String,
    rpe: String,
    modifier: Modifier = Modifier,
) {
    val colors = GriffGymTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceLowest)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "TARGET",
            modifier = Modifier.weight(WEIGHT_SET),
            style = GriffGymTheme.typography.labelSmall,
            color = GriffGymTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        TargetCell(weight, WEIGHT_KG)
        TargetCell(reps, WEIGHT_REPS)
        TargetCell(rpe, WEIGHT_RPE)
        Spacer(Modifier.width(ActionWidth))
        Spacer(Modifier.width(ActionWidth))
    }
}

@Composable
private fun RowScope.TargetCell(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = GriffGymTheme.typography.dataSmall,
        color = GriffGymTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
fun SetInputRow(
    state: SetRowState,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onRpeChange: (String) -> Unit,
    onToggleCompleted: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    isLastRow: Boolean = false,
) {
    val colors = GriffGymTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = state.index.toString(),
            modifier = Modifier.weight(WEIGHT_SET),
            style = GriffGymTheme.typography.dataLarge,
            color = if (state.completed) colors.primary else colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        NumericInput(
            value = state.weight,
            onValueChange = onWeightChange,
            modifier = Modifier.weight(WEIGHT_KG),
            placeholder = "KG",
            enabled = !readOnly,
            isError = state.weightInvalid,
        )
        NumericInput(
            value = state.reps,
            onValueChange = onRepsChange,
            modifier = Modifier.weight(WEIGHT_REPS),
            placeholder = "REPS",
            enabled = !readOnly,
            allowDecimal = false,
            isError = state.repsInvalid,
        )
        RpeInput(
            value = state.rpe,
            onValueChange = onRpeChange,
            modifier = Modifier.weight(WEIGHT_RPE),
            enabled = !readOnly,
            imeAction = if (isLastRow) ImeAction.Done else ImeAction.Next,
        )
        CompleteToggle(
            completed = state.completed,
            enabled = !readOnly,
            onClick = onToggleCompleted,
        )
        SquareAction(
            onClick = onOpenDetails,
            tint = if (state.hasNotes) colors.primary else colors.textSecondary,
        ) {
            Icon(
                imageVector = Icons.Filled.EditNote,
                contentDescription = "Set details",
                tint = if (state.hasNotes) colors.primary else colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Ticking a set is what turns typed numbers into volume and 1RM data. */
@Composable
private fun CompleteToggle(completed: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = GriffGymTheme.colors
    val shape = GriffGymTheme.shapes.input
    Box(
        modifier = Modifier
            .size(ActionWidth, GriffGymTheme.dimens.inputHeight)
            .clip(shape)
            .background(if (completed) colors.primary else colors.surfaceVariant)
            .border(1.dp, if (completed) colors.primary else colors.outlineStrong, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = if (completed) "Mark set as not done" else "Mark set as done",
            tint = if (completed) colors.onPrimary else colors.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SquareAction(
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    val colors = GriffGymTheme.colors
    val shape = GriffGymTheme.shapes.input
    Box(
        modifier = Modifier
            .size(ActionWidth, GriffGymTheme.dimens.inputHeight)
            .clip(shape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outlineStrong, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Preview(widthDp = 390, backgroundColor = 0xFF2A2721, showBackground = true)
@Composable
private fun SetInputRowPreview() {
    GriffGymTheme {
        androidx.compose.foundation.layout.Column(
            Modifier
                .background(GriffGymTheme.colors.surface)
                .padding(12.dp),
        ) {
            SetTableHeader()
            Spacer(Modifier.height(8.dp))
            TargetRow(weight = "192.5", reps = "3", rpe = "8")
            Spacer(Modifier.height(10.dp))
            SetInputRow(
                state = SetRowState(
                    setLogId = 1,
                    index = 1,
                    weight = "192.5",
                    reps = "3",
                    rpe = "8",
                    completed = true,
                    hasNotes = false,
                ),
                onWeightChange = {},
                onRepsChange = {},
                onRpeChange = {},
                onToggleCompleted = {},
                onOpenDetails = {},
            )
        }
    }
}
