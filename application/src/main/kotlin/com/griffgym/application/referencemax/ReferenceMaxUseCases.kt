package com.griffgym.application.referencemax

import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ReferenceMax
import com.griffgym.domain.model.Weight
import com.griffgym.domain.repository.ReferenceMaxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class GetReferenceMaxesUseCase @Inject constructor(
    private val referenceMaxRepository: ReferenceMaxRepository,
) {
    operator fun invoke(): Flow<List<ReferenceMax>> =
        referenceMaxRepository.observeReferenceMaxes().map { maxes ->
            maxes.sortedBy { ExerciseCategory.bigThree.indexOf(it.category) }
        }
}

class UpdateReferenceMaxUseCase @Inject constructor(
    private val referenceMaxRepository: ReferenceMaxRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(category: ExerciseCategory, input: String): Result<Unit> {
        if (!category.isBigThree) {
            return Result.failure(IllegalArgumentException("$category has no reference max"))
        }
        val weight = Weight.parse(input)
            ?: return Result.failure(IllegalArgumentException("'$input' is not a valid weight"))
        if (weight.isZero) {
            return Result.failure(IllegalArgumentException("A reference max cannot be zero"))
        }
        referenceMaxRepository.updateReferenceMax(category, weight, LocalDate.now(clock))
        return Result.success(Unit)
    }
}
