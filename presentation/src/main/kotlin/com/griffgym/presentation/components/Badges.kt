package com.griffgym.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.WorkoutStatus
import com.griffgym.presentation.format.Format
import com.griffgym.presentation.theme.GriffGymTheme

/** A rectangular label. Filled when the state deserves attention, outlined otherwise. */
@Composable
fun GriffGymBadge(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    color: Color = GriffGymTheme.colors.primary,
) {
    val shape = GriffGymTheme.shapes.badge
    val base = Modifier
        .clip(shape)
        .then(
            if (filled) {
                Modifier.background(color)
            } else {
                Modifier.border(1.dp, color.copy(alpha = 0.6f), shape)
            },
        )
        .padding(horizontal = 6.dp, vertical = 3.dp)

    Text(
        text = text,
        modifier = modifier.then(base),
        style = GriffGymTheme.typography.labelSmall,
        color = if (filled) GriffGymTheme.colors.onPrimary else color,
    )
}

/**
 * Marks the role of a movement inside the day. Only the top set is filled amber — that is
 * the set the whole session is built around.
 */
@Composable
fun ExerciseTypeBadge(type: ExerciseType, modifier: Modifier = Modifier) {
    val colors = GriffGymTheme.colors
    GriffGymBadge(
        text = Format.exerciseType(type),
        modifier = modifier,
        filled = type == ExerciseType.TOP,
        color = when (type) {
            ExerciseType.TOP -> colors.primary
            ExerciseType.DELOAD -> colors.bench
            else -> colors.textTertiary
        },
    )
}

/** READY / IN PROGRESS / COMPLETED on the hero card and the log header. */
@Composable
fun StatusBadge(status: WorkoutUiStatus, modifier: Modifier = Modifier) {
    val colors = GriffGymTheme.colors
    GriffGymBadge(
        text = status.label,
        modifier = modifier,
        filled = status == WorkoutUiStatus.COMPLETED,
        color = when (status) {
            WorkoutUiStatus.READY -> colors.textTertiary
            WorkoutUiStatus.IN_PROGRESS -> colors.primary
            WorkoutUiStatus.COMPLETED -> colors.primary
            WorkoutUiStatus.CANCELLED -> colors.error
        },
    )
}

enum class WorkoutUiStatus(val label: String) {
    READY("READY"),
    IN_PROGRESS("IN PROGRESS"),
    COMPLETED("COMPLETED"),
    CANCELLED("CANCELLED");

    companion object {
        fun from(status: WorkoutStatus): WorkoutUiStatus = when (status) {
            WorkoutStatus.IN_PROGRESS -> IN_PROGRESS
            WorkoutStatus.COMPLETED -> COMPLETED
            WorkoutStatus.CANCELLED -> CANCELLED
        }
    }
}
