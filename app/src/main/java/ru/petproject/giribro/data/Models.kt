package ru.petproject.giribro.data

import java.time.LocalDate
import kotlin.math.roundToInt
import java.util.Locale

/** Виды работы в гиревом дневнике. */
enum class SectionType(
    val label: String,
    val short: String,
    /** Идёт ли объём в общий тоннаж. */
    val countsTonnage: Boolean,
    /** Сколько гирь участвует: толчок и ДЦ — две. */
    val kettlebells: Int,
    /** Дополнительный коэффициент (у длинного цикла — за сброс и заброс). */
    val extraCoefficient: Double
) {
    WARMUP("Разминка", "Разминка", false, 1, 1.0),
    JERK("Толчок", "Толчок", true, 2, 1.0),
    SNATCH("Рывок", "Рывок", true, 1, 1.0),
    LONG_CYCLE("Длинный цикл", "ДЦ", true, 2, 1.3),
    SWINGS("Махи и прочее", "Махи", false, 1, 1.0),
    GPP("ОФП", "ОФП", false, 1, 1.0),
    COOLDOWN("Заминка", "Заминка", false, 1, 1.0);

    /** Итоговый множитель объёма. */
    val coefficient: Double get() = kettlebells * extraCoefficient

    /** Человеческая подпись множителя: «×2», «×2 · 1.6». */
    val multiplierLabel: String
        get() = buildString {
            if (kettlebells > 1) append("×$kettlebells")
            if (extraCoefficient != 1.0) {
                if (isNotEmpty()) append(" · ")
                append(extraCoefficient)
            }
        }

    /** Пояснение для конструктора. */
    val note: String?
        get() = when {
            kettlebells > 1 && extraCoefficient != 1.0 -> "две гири · коэффициент $extraCoefficient"
            kettlebells > 1 -> "две гири"
            extraCoefficient != 1.0 -> "коэффициент $extraCoefficient"
            else -> null
        }

    val isMain: Boolean get() = this in main

    companion object {
        val main = listOf(JERK, SNATCH, LONG_CYCLE, SWINGS)
        val extra = listOf(WARMUP, GPP, COOLDOWN)
        val ordered = listOf(JERK, SNATCH, LONG_CYCLE, SWINGS, WARMUP, GPP, COOLDOWN)
    }
}

/** Цель подхода в счётчике повторений. */
enum class CounterGoal(val label: String) {
    /** Достичь заданного времени. */
    TIME("Время"),

    /** Выполнить заданное число повторов. */
    REPS("Повторы")
}

/** Как показывать время/прогресс в счётчике. */
enum class CounterTimerFormat(val label: String) {
    /** Счёт вверх от нуля. */
    STOPWATCH("Секундомер"),

    /** Счёт вниз от цели: время или оставшиеся повторы. */
    COUNTDOWN("Обратный отсчёт")
}

/** Что делать с камерой во время подхода. */
enum class SetCaptureMode {
    /** Камера не используется. */
    NONE,

    /** Полноэкранная камера со счётом и записью видео в выбранную папку. */
    RECORD,

    /** Полноэкранная камера со счётом без записи видео. */
    COUNT;

    companion object {
        /** Неизвестное или отсутствующее значение старых копий — без камеры. */
        fun fromName(name: String?): SetCaptureMode =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** Быстрые значения для одного гиревого упражнения. Задаются в настройках. */
data class SetQuickValues(
    val weights: List<Int>,
    val reps: List<Int>,
    val durationsSec: List<Int>
) {
    companion object {
        const val MAX_VALUES = 12
        const val MAX_NAME_LENGTH = 64
        const val MAX_GPP_EXERCISES = 40

        /** Значения по умолчанию для толчка, ДЦ и рывка. */
        val defaults: Map<SectionType, SetQuickValues> = mapOf(
            SectionType.JERK to SetQuickValues(
                weights = listOf(16, 20, 24, 28, 32),
                reps = listOf(10, 20, 30, 40, 50),
                durationsSec = listOf(30, 60, 120, 180, 300, 600)
            ),
            SectionType.LONG_CYCLE to SetQuickValues(
                weights = listOf(16, 20, 24, 28, 32),
                reps = listOf(10, 20, 30, 40, 50),
                durationsSec = listOf(30, 60, 120, 180, 300, 600)
            ),
            SectionType.SNATCH to SetQuickValues(
                weights = listOf(16, 20, 24, 28, 32),
                reps = listOf(20, 40, 80, 100, 200),
                durationsSec = listOf(60, 120, 240, 360, 600)
            )
        )

        fun forType(type: SectionType): SetQuickValues =
            defaults[type] ?: defaults.getValue(SectionType.JERK)

        /** Оставляет только корректные значения и ограничивает длину списков. */
        fun sanitize(values: SetQuickValues): SetQuickValues = SetQuickValues(
            weights = values.weights.filter { it in 1..200 }.distinct().take(MAX_VALUES),
            reps = values.reps.filter { it in 1..999 }.distinct().take(MAX_VALUES),
            durationsSec = values.durationsSec
                .filter { it in 1..86_400 }
                .distinct()
                .take(MAX_VALUES)
        )

        fun sanitizeNames(names: List<String>): List<String> = names
            .map { it.trim().take(MAX_NAME_LENGTH) }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(MAX_GPP_EXERCISES)
    }
}

data class SetEntry(
    val weightKg: Int,
    val reps: Int,
    /** Время подхода в секундах; 0 — не заполнено. */
    val durationSec: Int = 0,
    /** Отдых после этого подхода, секунды. У последнего подхода не используется. */
    val restSec: Int = DEFAULT_REST_SEC,
    /** Режим съёмки подхода; по умолчанию камера не используется. */
    val captureMode: SetCaptureMode = SetCaptureMode.NONE,
    /** Произвольное название упражнения в разделах без гирь (ОФП и т.п.). */
    val exerciseName: String = ""
) {
    val volumeKg: Double get() = weightKg.toDouble() * reps

    companion object {
        const val DEFAULT_REST_SEC = 60
        const val MAX_REST_SEC = 600
    }
}

data class Section(
    val type: SectionType,
    val sets: List<SetEntry>
) {
    val volumeKg: Double get() = sets.sumOf { it.volumeKg } * type.coefficient
    val reps: Int get() = sets.sumOf { it.reps }
    val durationSec: Int get() = sets.sumOf { it.durationSec }
    val tonnageKg: Double get() = if (type.countsTonnage) volumeKg else 0.0
    val bestSet: SetEntry? get() = sets.maxByOrNull { it.volumeKg }
}

data class Workout(
    val id: Long = 0L,
    val date: LocalDate,
    val sections: List<Section>,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val tonnageKg: Double get() = sections.sumOf { it.tonnageKg }
    val volumeKg: Double get() = sections.sumOf { it.volumeKg }
    val reps: Int get() = sections.sumOf { it.reps }
    /** Суммарное время всех подходов, включая разминку и ОФП. */
    val durationSec: Int get() = sections.sumOf { it.durationSec }
    val mainSections: List<Section> get() = sections.filter { it.type.isMain }
    /** Время под нагрузкой только в основных упражнениях. */
    val mainDurationSec: Int get() = mainSections.sumOf { it.durationSec }
}

fun Double.asTonnage(): String =
    if (this >= 1000.0) String.format(Locale.US, "%.2f т", this / 1000.0)
    else "${this.roundToInt()} кг"

fun Double.asShortTonnage(): String =
    if (this >= 1000.0) String.format(Locale.US, "%.1f т", this / 1000.0)
    else "${this.roundToInt()} кг"

fun Int.asDuration(): String {
    val h = this / 3600
    val m = (this % 3600) / 60
    val s = this % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
