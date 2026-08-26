package com.griffgym.presentation.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.griffgym.presentation.components.GriffGymBadge
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.GriffGymSecondaryButton
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.format.Format
import com.griffgym.presentation.theme.GriffGymTheme

@Composable
fun WorkoutDialogs(state: WorkoutUiState, onEvent: (WorkoutUiEvent) -> Unit) {
    state.setDetails?.let { details ->
        SetDetailsDialog(
            details = details,
            onNotesChange = { onEvent(WorkoutUiEvent.SetNotesChanged(it)) },
            onSave = { onEvent(WorkoutUiEvent.SaveSetNotes) },
            onRemove = { onEvent(WorkoutUiEvent.RemoveSet) },
            onDismiss = { onEvent(WorkoutUiEvent.DismissSetDetails) },
            readOnly = state.readOnly,
        )
    }

    state.exercisePicker?.let { picker ->
        ExercisePickerDialog(
            picker = picker,
            onQueryChange = { onEvent(WorkoutUiEvent.ExerciseQueryChanged(it)) },
            onSelect = { onEvent(WorkoutUiEvent.AddExercise(it)) },
            onDismiss = { onEvent(WorkoutUiEvent.DismissExercisePicker) },
        )
    }

    if (state.confirmFinish) {
        ConfirmDialog(
            title = "FINISH WORKOUT",
            message = "The session is closed and the program moves to the next unit. " +
                "Sets that were never ticked off do not count towards volume.",
            confirmLabel = "FINISH",
            onConfirm = { onEvent(WorkoutUiEvent.ConfirmFinish) },
            onDismiss = { onEvent(WorkoutUiEvent.DismissFinish) },
        )
    }

    if (state.confirmCancel) {
        ConfirmDialog(
            title = "CANCEL WORKOUT",
            message = "The session is kept in history as cancelled and the program stays " +
                "on this unit, so you can start it again later.",
            confirmLabel = "CANCEL WORKOUT",
            onConfirm = { onEvent(WorkoutUiEvent.ConfirmCancel) },
            onDismiss = { onEvent(WorkoutUiEvent.DismissCancel) },
        )
    }
}

@Composable
private fun SetDetailsDialog(
    details: SetDetailsState,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    readOnly: Boolean,
) {
    val colors = GriffGymTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            Text(
                text = "${details.exerciseName.uppercase()} · SET ${details.setIndex}",
                style = GriffGymTheme.typography.label,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Notes",
                style = GriffGymTheme.typography.title,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            NotesField(
                value = details.notes,
                onValueChange = onNotesChange,
                enabled = !readOnly,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (details.canRemove && !readOnly) {
                    GriffGymSecondaryButton(
                        text = "REMOVE SET",
                        onClick = onRemove,
                        color = colors.error,
                        modifier = Modifier.weight(1f),
                    )
                }
                GriffGymPrimaryButton(
                    text = if (readOnly) "CLOSE" else "SAVE",
                    onClick = { if (readOnly) onDismiss() else onSave() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NotesField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    val colors = GriffGymTheme.colors
    val selectionColors = remember(colors.primary) {
        TextSelectionColors(
            handleColor = colors.primary,
            backgroundColor = colors.primary.copy(alpha = 0.3f),
        )
    }
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = GriffGymTheme.typography.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .background(colors.surfaceLowest)
                .border(1.dp, colors.outlineStrong)
                .padding(12.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = "How did the set feel?",
                        style = GriffGymTheme.typography.body,
                        color = colors.textTertiary,
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun ExercisePickerDialog(
    picker: ExercisePickerState,
    onQueryChange: (String) -> Unit,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = GriffGymTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            Text(
                text = "ADD EXERCISE",
                style = GriffGymTheme.typography.label,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(12.dp))
            NotesFieldSingleLine(value = picker.query, onValueChange = onQueryChange)
            Spacer(Modifier.height(12.dp))
            HairLine()
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(picker.filtered, key = { it.id }) { exercise ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(exercise.id) }
                            .padding(vertical = 14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = exercise.name,
                                style = GriffGymTheme.typography.body,
                                color = colors.textPrimary,
                            )
                            GriffGymBadge(
                                text = Format.categoryShort(exercise.category),
                                color = colors.textTertiary,
                            )
                        }
                    }
                    HairLine()
                }
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

@Composable
private fun NotesFieldSingleLine(value: String, onValueChange: (String) -> Unit) {
    val colors = GriffGymTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = GriffGymTheme.typography.body.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.primary),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceLowest)
            .border(1.dp, colors.outlineStrong)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = "Search",
                    style = GriffGymTheme.typography.body,
                    color = colors.textTertiary,
                )
            }
            inner()
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = GriffGymTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            Text(
                text = title,
                style = GriffGymTheme.typography.headline,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = GriffGymTheme.typography.body,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GriffGymSecondaryButton(
                    text = "BACK",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                GriffGymPrimaryButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DialogSurface(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = GriffGymTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(1.dp, colors.outlineStrong)
            .padding(20.dp),
        content = content,
    )
}
