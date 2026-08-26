package com.griffgym.presentation.format

import com.griffgym.domain.model.ExerciseCategory
import com.griffgym.domain.model.ExerciseType
import com.griffgym.domain.model.TrainingVolume
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Presentation-only vocabulary: the domain stays free of UI wording. */
object Format {

    private val ROMAN = listOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")
    private val DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private val SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM", Locale.ENGLISH)
    private val MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

    fun roman(day: Int): String = ROMAN.getOrNull(day) ?: day.toString()

    /** "WEEK 3, DAY I" — the header the log screen leads with. */
    fun weekAndDay(week: Int, day: Int): String = "WEEK $week, DAY ${roman(day)}"

    /** "Week 3 Day I" — the softer variant used on the Home hero card. */
    fun weekAndDayTitle(week: Int, day: Int): String = "Week $week Day ${roman(day)}"

    fun date(date: LocalDate): String = date.format(DATE).uppercase(Locale.ENGLISH)

    fun shortDate(date: LocalDate): String = date.format(SHORT_DATE)

    fun month(date: LocalDate): String = date.format(MONTH).uppercase(Locale.ENGLISH)

    fun duration(duration: Duration?): String {
        if (duration == null) return "—"
        val minutes = duration.toMinutes()
        return if (minutes < 60) "${minutes}min" else "${minutes / 60}h ${minutes % 60}min"
    }

    fun volume(volume: TrainingVolume): String {
        val tonnes = volume.kilograms / 1000.0
        return if (tonnes >= 1.0) String.format(Locale.ENGLISH, "%.1ft", tonnes) else "$volume kg"
    }

    fun categoryShort(category: ExerciseCategory): String = when (category) {
        ExerciseCategory.SQUAT -> "SQ"
        ExerciseCategory.DEADLIFT -> "DL"
        ExerciseCategory.BENCH_PRESS -> "BP"
        ExerciseCategory.ACCESSORY -> "ACC"
    }

    fun categoryLabel(category: ExerciseCategory): String = when (category) {
        ExerciseCategory.SQUAT -> "SQUAT"
        ExerciseCategory.DEADLIFT -> "DEADLIFT"
        ExerciseCategory.BENCH_PRESS -> "BENCH PRESS"
        ExerciseCategory.ACCESSORY -> "ACCESSORY"
    }

    fun exerciseType(type: ExerciseType): String = when (type) {
        ExerciseType.TOP -> "TOP"
        ExerciseType.BACK_OFF -> "BACK-OFF"
        ExerciseType.VOLUME -> "VOLUME"
        ExerciseType.LIGHT -> "LIGHT"
        ExerciseType.DELOAD -> "DELOAD"
        ExerciseType.ACCESSORY -> "ACCESSORY"
    }
}
