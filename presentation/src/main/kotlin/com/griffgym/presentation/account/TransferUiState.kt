package com.griffgym.presentation.account

import androidx.compose.runtime.Immutable
import com.griffgym.domain.model.BackupStage

/**
 * Where a transfer of the lifter's whole training history currently stands.
 *
 * Shared by the backup and restore screens because the two are the same promise in opposite
 * directions, and both have exactly one outcome that must never be misrepresented: a
 * failure has to look like a failure, not like a screen that finished quietly.
 */
enum class TransferStatus {
    RUNNING,
    FAILED,
    DONE,
}

/** One plain-language line of an upload, and whether it has happened yet. */
@Immutable
data class TransferStepUi(
    val label: String,
    val state: StepState,
)

enum class StepState {
    PENDING,
    ACTIVE,
    DONE,
}

@Immutable
data class BackupProgressUiState(
    val steps: List<TransferStepUi> = BackupSteps.initial(),
    val status: TransferStatus = TransferStatus.RUNNING,
    /** Overall, across every stage — never a per-stage number dressed up as the whole job. */
    val fraction: Float = 0f,
    /** Plain, non-technical. Null unless [status] is [TransferStatus.FAILED]. */
    val error: String? = null,
) {
    val isRunning: Boolean get() = status == TransferStatus.RUNNING
}

sealed interface BackupProgressUiEvent {
    data object Retry : BackupProgressUiEvent

    /** Carry on with the app, honestly local-only, and back up later from Account. */
    data object ContinueWithoutBackup : BackupProgressUiEvent
}

/**
 * The stages a first backup goes through, in order.
 *
 * [BackupStage.DONE] is deliberately absent: it is the absence of work left, not a step
 * somebody waits through, and showing it would leave a permanently unfinished-looking row
 * on a screen whose whole job is to look finished.
 */
internal object BackupSteps {

    val ORDER: List<BackupStage> = listOf(
        BackupStage.PREPARING,
        BackupStage.UPLOADING_REFERENCE_MAXES,
        BackupStage.UPLOADING_CYCLES,
        BackupStage.UPLOADING_WORKOUTS,
        BackupStage.VERIFYING,
    )

    fun initial(): List<TransferStepUi> = ORDER.map {
        TransferStepUi(AccountFormat.backupStage(it), StepState.PENDING)
    }

    fun at(stage: BackupStage): List<TransferStepUi> {
        val reached = ORDER.indexOf(stage)
        return ORDER.mapIndexed { index, item ->
            TransferStepUi(
                label = AccountFormat.backupStage(item),
                state = when {
                    // DONE is not in ORDER, so its index is -1 and everything reads as done.
                    reached < 0 || index < reached -> StepState.DONE
                    index == reached -> StepState.ACTIVE
                    else -> StepState.PENDING
                },
            )
        }
    }

    /**
     * How far through the whole upload the lifter is, counting the current stage's own
     * progress when the repository reports it. Stages are weighted equally: guessing that
     * workouts take longer than reference maxes would be a fiction, and an honest bar that
     * pauses is better than a smooth one that lies.
     */
    fun fraction(stage: BackupStage, stageFraction: Float): Float {
        val reached = ORDER.indexOf(stage)
        if (reached < 0) return 1f
        return (reached + stageFraction.coerceIn(0f, 1f)) / ORDER.size
    }
}
