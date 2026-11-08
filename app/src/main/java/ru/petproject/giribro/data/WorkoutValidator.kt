package ru.petproject.giribro.data

/**
 * Проверка тренировки перед сохранением.
 *
 * Основные ограничения дублируются в конструкторе, но проверка здесь нужна как
 * последний барьер: данные могли прийти из старой версии, восстановленной копии
 * или программного вызова.
 */
object WorkoutValidator {

    const val MAX_WEIGHT_KG = 200
    const val MAX_REPS = 999
    const val MAX_DURATION_SEC = 24 * 60 * 60
    const val MAX_NOTE_LENGTH = 1000

    fun validate(workout: Workout): String? = validate(workout.sections, workout.note)

    fun validate(sections: List<Section>, note: String): String? {
        val filledSections = sections.filter { it.sets.isNotEmpty() }
        if (filledSections.isEmpty()) return "Добавьте хотя бы один подход."

        if (note.length > MAX_NOTE_LENGTH) {
            return "Заметка слишком длинная: максимум $MAX_NOTE_LENGTH символов."
        }

        filledSections.forEach { section ->
            section.sets.forEachIndexed { index, set ->
                validateSet(set)?.let { problem ->
                    return "${section.type.label}, подход ${index + 1}: $problem."
                }
            }
        }
        return null
    }

    fun validateSet(set: SetEntry): String? = when {
        set.weightKg !in 0..MAX_WEIGHT_KG ->
            "вес должен быть от 0 до $MAX_WEIGHT_KG кг"
        set.reps !in 1..MAX_REPS ->
            "повторы должны быть от 1 до $MAX_REPS"
        set.durationSec !in 0..MAX_DURATION_SEC ->
            "время подхода должно быть не больше 24 часов"
        set.restSec !in 0..SetEntry.MAX_REST_SEC ->
            "отдых должен быть от 0 до ${SetEntry.MAX_REST_SEC} секунд"
        else -> null
    }
}
