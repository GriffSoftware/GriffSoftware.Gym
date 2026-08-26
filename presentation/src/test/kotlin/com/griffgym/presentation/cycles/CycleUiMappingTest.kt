package com.griffgym.presentation.cycles

import com.griffgym.domain.model.CycleComparison
import com.griffgym.domain.model.CycleStatus
import com.griffgym.domain.model.CycleWeekProgress
import com.griffgym.domain.model.ReferenceMaxSnapshot
import com.griffgym.domain.model.TrainingCycle
import com.griffgym.domain.model.TrainingCycleSummary
import com.griffgym.domain.model.Weight
import com.griffgym.presentation.components.CycleWeekState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * What the cycles screens actually say.
 *
 * The six week bar is the one place the whole block is readable at a glance, so which
 * segment is amber, which are ticked and which say DELOAD is worth pinning down.
 */
class CycleUiMappingTest {

    @Test
    fun `the current week is the first unfinished one and the only amber segment`() {
        val states = summary(completedWorkouts = 7).toWeekUiModels()

        assertEquals(
            listOf(
                CycleWeekState.COMPLETED,
                CycleWeekState.COMPLETED,
                CycleWeekState.CURRENT,
                CycleWeekState.UPCOMING,
                CycleWeekState.UPCOMING,
                CycleWeekState.UPCOMING,
            ),
            states.map { it.state },
        )
        assertEquals(1, states.count { it.state == CycleWeekState.CURRENT })
    }

    @Test
    fun `week six is marked as the deload wherever it appears`() {
        val states = summary(completedWorkouts = 0).toWeekUiModels()

        assertEquals(listOf(6), states.filter { it.isDeload }.map { it.weekNumber })
    }

    @Test
    fun `a finished cycle has no current week, even if a session was skipped`() {
        // The lifter stopped mid-block and started the next one; nothing here is still ahead.
        val states = summary(completedWorkouts = 14, status = CycleStatus.COMPLETED)
            .toWeekUiModels()

        assertEquals(0, states.count { it.state == CycleWeekState.CURRENT })
        assertEquals(6, states.count { it.state == CycleWeekState.COMPLETED })
    }

    @Test
    fun `progress reads as a week while there is one, and as a total once there is not`() {
        assertEquals("WEEK 3 OF 6", summary(completedWorkouts = 7).progressLabel())
        assertEquals("6 OF 6 WEEKS DONE", summary(completedWorkouts = 18).progressLabel())
        assertEquals("7/18 WORKOUTS", summary(completedWorkouts = 7).workoutsLabel())
    }

    @Test
    fun `a history row carries the snapshot the block was built from`() {
        val item = cycle(number = 2, status = CycleStatus.COMPLETED)
            .toHistoryItem(weeksLabel = "6/6 WEEKS")

        assertEquals("CYCLE 2", item.label)
        assertEquals("SQ 200 · DL 220 · BP 150", item.referenceMaxesLabel)
        assertEquals("6/6 WEEKS", item.weeksLabel)
    }

    @Test
    fun `a lift that was held reads as KEPT, not as a zero that failed to load`() {
        val comparison = CycleComparison(
            previous = cycle(number = 1),
            current = cycle(
                number = 2,
                maxes = ReferenceMaxSnapshot(Weight.of(205.0), Weight.of(150.0), Weight.of(212.5)),
            ),
        ).toUiState()

        assertEquals("VS CYCLE 1", comparison.title)
        assertEquals(listOf("+5 KG", "-7.5 KG", "KEPT"), comparison.lifts.map { it.change })
        assertEquals(listOf(true, true, false), comparison.lifts.map { it.isChanged })
        assertEquals(listOf("SQ", "DL", "BP"), comparison.lifts.map { it.code })
    }

    private fun summary(
        completedWorkouts: Int,
        status: CycleStatus = CycleStatus.ACTIVE,
    ): TrainingCycleSummary {
        var remaining = completedWorkouts
        return TrainingCycleSummary(
            cycle = cycle(number = 3, status = status),
            weeks = (1..6).map { week ->
                val done = remaining.coerceIn(0, 3)
                remaining -= done
                CycleWeekProgress(
                    weekNumber = week,
                    label = if (week == 6) "DELOAD" else "W$week",
                    isDeload = week == 6,
                    plannedWorkouts = 3,
                    completedWorkouts = done,
                )
            },
        )
    }

    private fun cycle(
        number: Int,
        status: CycleStatus = CycleStatus.ACTIVE,
        maxes: ReferenceMaxSnapshot = ReferenceMaxSnapshot(
            squat = Weight.of(200.0),
            benchPress = Weight.of(150.0),
            deadlift = Weight.of(220.0),
        ),
    ) = TrainingCycle(
        id = number.toLong(),
        cycleNumber = number,
        status = status,
        startedAt = STARTED_AT,
        completedAt = if (status == CycleStatus.COMPLETED) FINISHED_AT else null,
        referenceMaxes = maxes,
        createdAt = STARTED_AT,
    )

    private companion object {
        val STARTED_AT: Instant = Instant.parse("2026-02-16T09:00:00Z")
        val FINISHED_AT: Instant = Instant.parse("2026-03-30T19:00:00Z")
    }
}
