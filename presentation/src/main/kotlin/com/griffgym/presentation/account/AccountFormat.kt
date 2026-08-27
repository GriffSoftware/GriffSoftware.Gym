package com.griffgym.presentation.account

import com.griffgym.domain.model.BackupStage
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Presentation vocabulary for the cloud features. The domain stays free of UI wording. */
internal object AccountFormat {

    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val DAY_AND_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /**
     * "Today, 18:42" — the only form of "is my training safe?" that reads at a glance.
     *
     * Relative for the two days a lifter can hold in their head, absolute after that;
     * a bare timestamp makes everybody do subtraction, and "3 days ago" stops being useful
     * the moment it matters.
     *
     * Callers must only ever pass [com.griffgym.domain.model.CloudSyncStatus.lastSyncedAt],
     * which the domain sets on a *completed* sync. A time taken from a sync that started
     * would be the app claiming a backup it does not have.
     */
    fun lastSync(instant: Instant, clock: Clock): String {
        val zone = clock.zone
        val moment = instant.atZone(zone)
        val today = LocalDate.now(clock)

        val day = when (moment.toLocalDate()) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> moment.format(DAY_AND_MONTH)
        }
        return "$day, ${moment.format(TIME)}"
    }

    /**
     * Plain language for what the upload is doing. "Uploading cycles" is something a lifter
     * can wait for; a stage name out of an enum is not.
     */
    fun backupStage(stage: BackupStage): String = when (stage) {
        BackupStage.PREPARING -> "Preparing data"
        BackupStage.UPLOADING_REFERENCE_MAXES -> "Uploading reference maxes"
        BackupStage.UPLOADING_CYCLES -> "Uploading cycles"
        BackupStage.UPLOADING_WORKOUTS -> "Uploading workouts"
        BackupStage.VERIFYING -> "Verifying backup"
        BackupStage.DONE -> "Backup complete"
    }
}
