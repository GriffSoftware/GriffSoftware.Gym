package com.griffgym.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.griffgym.domain.model.ExerciseType
import com.griffgym.presentation.theme.GriffGymTheme

@Immutable
data class ExerciseCardState(
    val exerciseLogId: Long,
    val position: Int,
    val name: String,
    val type: ExerciseType,
    val targetWeight: String,
    val targetReps: String,
    val targetRpe: String,
    val hasTarget: Boolean,
    val sets: List<SetRowState>,
)

/**
 * One movement of the session: numbered header with its role badge, the prescription, and
 * the editable set table.
 */
@Composable
fun ExerciseCard(
    state: ExerciseCardState,
    onWeightChange: (Long, String) -> Unit,
    onRepsChange: (Long, String) -> Unit,
    onRpeChange: (Long, String) -> Unit,
    onToggleCompleted: (Long) -> Unit,
    onOpenSetDetails: (Long) -> Unit,
    onAddSet: (Long) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    GriffGymCard(modifier = modifier, contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${state.position}. ${state.name.uppercase()}",
                    style = GriffGymTheme.typography.headline,
                    color = GriffGymTheme.colors.textPrimary,
                )
                Spacer(Modifier.padding(start = 8.dp))
                ExerciseTypeBadge(state.type)
            }
        }

        Spacer(Modifier.height(14.dp))
        SetTableHeader()
        Spacer(Modifier.height(8.dp))

        if (state.hasTarget) {
            TargetRow(
                weight = state.targetWeight,
                reps = state.targetReps,
                rpe = state.targetRpe,
            )
            Spacer(Modifier.height(10.dp))
        }

        state.sets.forEachIndexed { index, set ->
            SetInputRow(
                state = set,
                onWeightChange = { onWeightChange(set.setLogId, it) },
                onRepsChange = { onRepsChange(set.setLogId, it) },
                onRpeChange = { onRpeChange(set.setLogId, it) },
                onToggleCompleted = { onToggleCompleted(set.setLogId) },
                onOpenDetails = { onOpenSetDetails(set.setLogId) },
                readOnly = readOnly,
                isLastRow = index == state.sets.lastIndex,
            )
            if (index != state.sets.lastIndex) Spacer(Modifier.height(8.dp))
        }

        if (!readOnly) {
            Spacer(Modifier.height(12.dp))
            GriffGymDashedButton(
                text = "ADD SET",
                onClick = { onAddSet(state.exerciseLogId) },
                icon = Icons.Filled.Add,
                modifier = Modifier.fillMaxWidth(),
                color = GriffGymTheme.colors.textSecondary,
                textStyle = GriffGymTheme.typography.label,
            )
        }
    }
}

@Preview(widthDp = 390, backgroundColor = 0xFF14120C, showBackground = true)
@Composable
private fun ExerciseCardPreview() {
    GriffGymTheme {
        ExerciseCard(
            state = ExerciseCardState(
                exerciseLogId = 1,
                position = 1,
                name = "Przysiad",
                type = ExerciseType.TOP,
                targetWeight = "192.5",
                targetReps = "3",
                targetRpe = "8",
                hasTarget = true,
                sets = listOf(
                    SetRowState(1, 1, "192.5", "3", "8", completed = true, hasNotes = false),
                    SetRowState(2, 2, "192.5", "3", "", completed = false, hasNotes = true),
                ),
            ),
            onWeightChange = { _, _ -> },
            onRepsChange = { _, _ -> },
            onRpeChange = { _, _ -> },
            onToggleCompleted = {},
            onOpenSetDetails = {},
            onAddSet = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
