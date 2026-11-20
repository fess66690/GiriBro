package ru.petproject.giribro.data

import ru.petproject.giribro.util.pluralRu
import ru.petproject.giribro.util.ruDayMonth
import ru.petproject.giribro.util.ruWeekday
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Одна строка упражнения на фото-карточке тренировки. */
data class WorkoutCardSection(
    val type: SectionType,
    val title: String,
    val details: String,
    val volume: String
)

/**
 * Готовый текст и данные для отрисовки карточки.
 *
 * Отделён от Android-graphics, чтобы правила отбора и форматирования можно
 * было проверить обычными JVM-тестами.
 */
data class WorkoutCardContent(
    val date: String,
    val weekday: String,
    val year: String,
    val title: String,
    val tonnageValue: String,
    val tonnageUnit: String,
    val reps: String,
    val duration: String,
    val sectionsCount: String,
    val sections: List<WorkoutCardSection>,
    val hiddenSections: Int,
    val note: String?,
    val fileName: String
) {
    companion object {
        const val MAX_SECTIONS = 5
        const val MAX_SECTIONS_WITH_NOTE = 4

        private val fileDate = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

        fun from(workout: Workout, maxSections: Int = MAX_SECTIONS): WorkoutCardContent {
            val note = workout.note.trim().takeIf { it.isNotEmpty() }
            val limit = if (note == null) {
                maxSections
            } else {
                minOf(maxSections, MAX_SECTIONS_WITH_NOTE)
            }
            val visibleSections = workout.sections.take(limit)
            val hidden = (workout.sections.size - visibleSections.size).coerceAtLeast(0)

            val title = workout.mainSections
                .map { it.type.short }
                .ifEmpty { listOf("Тренировка") }
                .joinToString(" · ")

            val tons = workout.tonnageKg >= 1000.0
            val tonnageValue = if (tons) {
                String.format(Locale.US, "%.2f", workout.tonnageKg / 1000.0)
                    .trimEnd('0')
                    .trimEnd('.')
            } else {
                workout.tonnageKg.roundToInt().toString()
            }

            return WorkoutCardContent(
                date = workout.date.ruDayMonth(),
                weekday = workout.date.ruWeekday(),
                year = workout.date.year.toString(),
                title = title,
                tonnageValue = tonnageValue,
                tonnageUnit = if (tons) "т" else "кг",
                reps = workout.reps.toString(),
                duration = if (workout.durationSec > 0) {
                    workout.durationSec.asDuration()
                } else {
                    "—"
                },
                sectionsCount = workout.sections.size.toString(),
                sections = visibleSections.map { section ->
                    WorkoutCardSection(
                        type = section.type,
                        title = section.type.label,
                        details = buildString {
                            if (section.type == SectionType.GPP) {
                                val names = section.sets
                                    .map { it.exerciseName.trim() }
                                    .filter { it.isNotEmpty() }
                                    .distinct()
                                if (names.isNotEmpty()) {
                                    append(names.joinToString(", "))
                                    append(" · ")
                                }
                            }
                            append(section.sets.size)
                            append(" ")
                            append(
                                pluralRu(
                                    section.sets.size,
                                    "подход",
                                    "подхода",
                                    "подходов"
                                )
                            )
                            append(" · ")
                            append(section.reps)
                            append(" ")
                            append(
                                pluralRu(
                                    section.reps,
                                    "подъём",
                                    "подъёма",
                                    "подъёмов"
                                )
                            )
                            if (section.durationSec > 0) {
                                append(" · ")
                                append(section.durationSec.asDuration())
                            }
                        },
                        volume = section.volumeKg.asShortTonnage()
                    )
                },
                hiddenSections = hidden,
                note = note,
                fileName = "giribro-${workout.date.format(fileDate)}-${workout.id}.png"
            )
        }
    }
}
