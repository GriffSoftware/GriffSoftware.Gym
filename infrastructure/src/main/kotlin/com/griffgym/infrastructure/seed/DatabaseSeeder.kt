package com.griffgym.infrastructure.seed

import androidx.room.withTransaction
import com.griffgym.domain.model.StrengthBlockTemplate
import com.griffgym.infrastructure.database.GriffGymDatabase
import com.griffgym.infrastructure.database.entity.ExerciseEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes sure the exercise catalogue exists.
 *
 * That is all the seeder does now. Reference maxes and the training block are the lifter's
 * own numbers and are created by onboarding, not invented on their behalf — an empty
 * database is a lifter who has not set up yet, not a lifter who trains someone else's plan.
 *
 * Installations from before onboarding shipped already hold their maxes and program on
 * disk and are untouched by this: seeding only ever adds catalogue rows that are missing,
 * inside one transaction, so it stays idempotent across upgrades and restarts.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val database: GriffGymDatabase,
) {

    suspend fun seedIfNeeded() {
        database.withTransaction { seedExercises() }
    }

    private suspend fun seedExercises() {
        val dao = database.exerciseDao()
        val missing = StrengthBlockTemplate.template.exercises
            .filter { dao.getByName(it.name) == null }
            .map { ExerciseEntity(name = it.name, category = it.category) }
        if (missing.isNotEmpty()) dao.insertAll(missing)
    }
}
