package ru.petproject.giribro.vision

import ru.petproject.giribro.data.SectionType

/** Упражнения, для которых работает автоподсчёт. */
enum class RepExercise(
    val label: String,
    val short: String,
    val sectionType: SectionType
) {
    JERK("Толчок", "Толчок", SectionType.JERK),
    LONG_CYCLE("Длинный цикл", "ДЦ", SectionType.LONG_CYCLE),
    SNATCH("Рывок", "Рывок", SectionType.SNATCH);

    companion object {
        /** Упражнение для раздела тренировки; `null`, если автоподсчёт не работает. */
        fun fromSection(type: SectionType): RepExercise? =
            entries.firstOrNull { it.sectionType == type }
    }
}

/**
 * Порог фиксации. 0,8 с — соревновательный стандарт, 0,4 с — тренировочный
 * режим с запасом, 1,2 с — строгая фиксация для отработки.
 */
enum class FixationPreset(
    val seconds: Float,
    val label: String,
    val hint: String
) {
    TRAINING(0.4f, "Тренировочный", "0,4 с — быстрый счёт"),
    COMPETITION(0.8f, "Соревновательный", "0,8 с — как у судьи"),
    STRICT(1.2f, "Строгий", "1,2 с — запас по фиксации");

    companion object {
        fun fromName(name: String?): FixationPreset =
            entries.firstOrNull { it.name == name } ?: COMPETITION
    }
}

/**
 * Настройки распознавания для конкретного упражнения.
 *
 * Пороги заданы в долях длины корпуса, а не в пикселях: так они не зависят от
 * расстояния до телефона и его камеры.
 */
data class ExerciseProfile(
    val exercise: RepExercise,
    val fixationSec: Float,
    /** Толчок и ДЦ считаются по двум рукам, рывок — по каждой отдельно. */
    val bothHands: Boolean,
    val minRepIntervalSec: Float,
    /** Насколько кисть должна подняться выше плеча. */
    val wristAboveShoulderRatio: Float,
    /** Минимальный угол локтя, чтобы считать руку выпрямленной. */
    val elbowStraightDeg: Float,
    /** Допустимый наклон корпуса от вертикали. */
    val maxTorsoTiltDeg: Float,
    /** Сколько ждать, прежде чем считать, что рука реально ушла вниз. */
    val topGraceMs: Long,
    /** Пауза после повтора, чтобы не посчитать его дважды. */
    val releaseMs: Long
) {
    val fixationMs: Long get() = (fixationSec * 1000f).toLong()
    val minRepIntervalMs: Long get() = (minRepIntervalSec * 1000f).toLong()

    companion object {
        fun of(exercise: RepExercise, preset: FixationPreset): ExerciseProfile =
            when (exercise) {
                RepExercise.JERK -> ExerciseProfile(
                    exercise = exercise,
                    fixationSec = preset.seconds,
                    bothHands = true,
                    minRepIntervalSec = 1.0f,
                    wristAboveShoulderRatio = 0.06f,
                    elbowStraightDeg = 160f,
                    maxTorsoTiltDeg = 25f,
                    topGraceMs = 220,
                    releaseMs = 250
                )

                RepExercise.LONG_CYCLE -> ExerciseProfile(
                    exercise = exercise,
                    fixationSec = preset.seconds,
                    bothHands = true,
                    minRepIntervalSec = 1.0f,
                    wristAboveShoulderRatio = 0.06f,
                    elbowStraightDeg = 160f,
                    maxTorsoTiltDeg = 25f,
                    topGraceMs = 220,
                    releaseMs = 250
                )

                RepExercise.SNATCH -> ExerciseProfile(
                    exercise = exercise,
                    fixationSec = preset.seconds,
                    bothHands = false,
                    minRepIntervalSec = 0.9f,
                    wristAboveShoulderRatio = 0.05f,
                    elbowStraightDeg = 155f,
                    maxTorsoTiltDeg = 30f,
                    topGraceMs = 220,
                    releaseMs = 250
                )
            }
    }
}

enum class Side { LEFT, RIGHT, BOTH }
