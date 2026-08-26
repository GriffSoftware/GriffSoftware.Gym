package com.griffgym.infrastructure.repository

import androidx.room.withTransaction
import com.griffgym.domain.model.CycleStatus
import com.griffgym.domain.model.GeneratedProgram
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.TrainingCycle
import com.griffgym.domain.model.TrainingCycleSummary
import com.griffgym.domain.model.TrainingProgram
import com.griffgym.domain.repository.TrainingCycleRepository
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.dao.ReferenceMaxDao
import com.griffgym.infrastructure.database.dao.TrainingCycleDao
import com.griffgym.infrastructure.database.dao.TrainingProgramDao
import com.griffgym.infrastructure.database.entity.ProgramProgressEntity
import com.griffgym.infrastructure.database.entity.TrainingCycleEntity
import com.griffgym.infrastructure.mapper.toDomain
import com.griffgym.infrastructure.mapper.toEntity
import com.griffgym.infrastructure.mapper.toReferenceMaxes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTrainingCycleRepository @Inject constructor(
    private val database: GriffGymDatabase,
    private val cycleDao: TrainingCycleDao,
    private val programDao: TrainingProgramDao,
    private val referenceMaxDao: ReferenceMaxDao,
    private val programWriter: GeneratedProgramWriter,
) : TrainingCycleRepository {

    override fun observeCurrentCycle(): Flow<TrainingCycle?> =
        cycleDao.observeCurrent().map { it?.toDomain() }

    override suspend fun getCurrentCycle(): TrainingCycle? = cycleDao.getCurrent()?.toDomain()

    override suspend fun getCycle(id: Long): TrainingCycle? = cycleDao.getById(id)?.toDomain()

    /**
     * Two queries combined rather than one join, because they answer two different questions
     * and change for different reasons: the cycle rows move when a cycle starts or finishes,
     * the week counts move every time a session is completed. Room re-runs each only when its
     * own tables are touched.
     *
     * A cycle with no week rows still appears, with an empty plan, rather than dropping out
     * of the lifter's history because of a join.
     */
    override fun observeCycleSummaries(): Flow<List<TrainingCycleSummary>> = combine(
        cycleDao.observeAll(),
        cycleDao.observeWeekProgress(),
    ) { cycles, weeks ->
        val byCycle = weeks.groupBy { it.cycleId }
        cycles.map { cycle ->
            TrainingCycleSummary(
                cycle = cycle.toDomain(),
                weeks = byCycle[cycle.id].orEmpty().map { it.toDomain() },
            )
        }
    }

    override suspend fun getCycleSummary(cycleId: Long): TrainingCycleSummary? {
        val cycle = cycleDao.getById(cycleId) ?: return null
        return TrainingCycleSummary(
            cycle = cycle.toDomain(),
            weeks = cycleDao.getWeekProgress(cycleId).map { it.toDomain() },
        )
    }

    override suspend fun getCycleProgram(cycleId: Long): TrainingProgram? =
        programDao.getProgramOfCycle(cycleId)?.toDomain()

    /**
     * One transaction covering everything that has to be true at once for a cycle to exist:
     * the previous cycle closed, the previous plan stood down, the new cycle row, its whole
     * plan with the pointer at week 1 day I, and the maxes it was built from — written both
     * onto the cycle as its permanent snapshot and into the live reference max table.
     *
     * The maxes are not a follow-up step. If the process died between the plan and them, the
     * next launch would find a program and conclude setup had already happened, stranding
     * the lifter on a plan whose numbers they can never see or edit. All of it or none of it.
     */
    override suspend fun startCycle(
        program: GeneratedProgram,
        referenceMaxes: ReferenceMaxSnapshot,
        date: LocalDate,
        startedAt: Instant,
    ): TrainingCycle = database.withTransaction {
        val previous = cycleDao.getCurrent()
        if (previous != null && previous.status == CycleStatus.ACTIVE) {
            // Normally already closed by the last completed workout. Closing it here as well
            // covers the upgraded installation whose program had run out before cycles
            // existed, and keeps "exactly one open cycle" true by construction.
            cycleDao.markCompleted(previous.id, startedAt)
        }
        programDao.deactivateAllPrograms()

        val cycleId = cycleDao.insert(
            TrainingCycleEntity(
                cycleNumber = (previous?.cycleNumber ?: 0) + 1,
                status = CycleStatus.ACTIVE,
                startedAt = startedAt,
                completedAt = null,
                squatKg = referenceMaxes.squat.kilograms,
                benchPressKg = referenceMaxes.benchPress.kilograms,
                deadliftKg = referenceMaxes.deadlift.kilograms,
                createdAt = startedAt,
            ),
        )

        programWriter.write(program = program, cycleId = cycleId, createdAt = startedAt)

        referenceMaxDao.upsertAll(referenceMaxes.toReferenceMaxes(date).map { it.toEntity() })

        checkNotNull(cycleDao.getById(cycleId)) { "Cycle $cycleId vanished inside its own transaction" }
            .toDomain()
    }

    override suspend fun completeCurrentCycle(completedAt: Instant): TrainingCycle? =
        database.withTransaction {
            // The pointer is cleared first and unconditionally: "there is no next workout" is
            // the fact being recorded, and it holds whether or not a cycle row is there to
            // close. Leaving it dangling would send the lifter back into a workout they have
            // already finished.
            programDao.getActiveProgramRow()?.let { program ->
                programDao.upsertProgress(
                    ProgramProgressEntity(programId = program.id, currentWorkoutTemplateId = null),
                )
            }

            val current = cycleDao.getCurrent() ?: return@withTransaction null
            if (current.status == CycleStatus.ACTIVE) {
                cycleDao.markCompleted(current.id, completedAt)
            }

            cycleDao.getById(current.id)?.toDomain()
        }
}
