package com.griffgym.application.onboarding

import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.GeneratedProgram
import com.griffgym.domain.model.StrengthBlockTemplate
import com.griffgym.domain.model.TrainingBlockGenerator
import com.griffgym.domain.model.TrainingTemplate
import com.griffgym.domain.model.Weight
import javax.inject.Inject

/**
 * Builds the lifter's six week block from their reference maxes.
 *
 * The calculation itself lives in [TrainingBlockGenerator]; this use case exists to name
 * the business action, to pin the canonical template and to be injectable — the same split
 * `CalculateEstimated1RmUseCase` uses for the Epley formula.
 */
class GenerateTrainingBlockUseCase @Inject constructor() {

    operator fun invoke(
        referenceMaxes: Map<ExerciseCategory, Weight>,
        template: TrainingTemplate = StrengthBlockTemplate.template,
    ): GeneratedProgram = TrainingBlockGenerator.generate(template, referenceMaxes)
}
