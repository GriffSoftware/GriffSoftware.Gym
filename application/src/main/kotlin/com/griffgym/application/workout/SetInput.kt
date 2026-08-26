package com.griffgym.application.workout

import com.griffgym.domain.model.Rpe
import com.griffgym.domain.model.SetResult
import com.griffgym.domain.model.Weight
import javax.inject.Inject

/** Raw, unvalidated text straight from the log screen. */
data class SetInput(
    val weight: String,
    val reps: String,
    val rpe: String,
    val notes: String? = null,
)

enum class SetField { WEIGHT, REPS, RPE }

sealed interface SetValidation {
    data class Valid(val result: SetResult) : SetValidation
    data class Invalid(val invalidFields: Set<SetField>) : SetValidation
}

/**
 * Turns the text a lifter types into a [SetResult].
 *
 * Lives here rather than in the ViewModel because the rules — comma or dot as a decimal
 * separator, RPE clamped to 1..10 in half steps, a completed set needing both a load and
 * reps — are business rules, not view concerns.
 */
class ValidateSetInputUseCase @Inject constructor() {

    operator fun invoke(input: SetInput, requireComplete: Boolean): SetValidation {
        val invalid = mutableSetOf<SetField>()

        val weightText = input.weight.trim()
        val weight = when {
            weightText.isEmpty() -> null.also { if (requireComplete) invalid += SetField.WEIGHT }
            else -> Weight.parse(weightText) ?: null.also { invalid += SetField.WEIGHT }
        }

        val repsText = input.reps.trim()
        val reps = when {
            repsText.isEmpty() -> null.also { if (requireComplete) invalid += SetField.REPS }
            else -> repsText.toIntOrNull()?.takeIf { it > 0 } ?: null.also { invalid += SetField.REPS }
        }

        val rpeText = input.rpe.trim()
        val rpe = when {
            rpeText.isEmpty() -> null
            else -> Rpe.parse(rpeText) ?: null.also { invalid += SetField.RPE }
        }

        if (invalid.isNotEmpty()) return SetValidation.Invalid(invalid)

        return SetValidation.Valid(
            SetResult(
                weight = weight,
                reps = reps,
                rpe = rpe,
                completed = requireComplete,
                notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }
}
