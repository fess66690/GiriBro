package ru.petproject.giribro.timer

import ru.petproject.giribro.data.SetEntry
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.vision.RepExercise

/** Откуда взялся план: обычный интервальный таймер или конкретная тренировка. */
enum class TimerPlanSource { INTERVAL, WORKOUT }

enum class TimerStepKind { PREP, WORK, REST }

/**
 * Один отрезок таймера. Для тренировки в [label] лежит название упражнения,
 * [round] и [rounds] — номер подхода внутри упражнения.
 *
 * [manual] — подход без заданного времени: таймер ждёт нажатия «Выполнено»,
 * а [task] показывает задание (вес и повторы).
 *
 * [countReps] — этот подход пользователь выбрал для съёмки камерой,
 * [exercise] задаёт, какое упражнение считать.
 */
data class TimerStep(
    val kind: TimerStepKind,
    val seconds: Int,
    val label: String,
    val round: Int = 0,
    val rounds: Int = 0,
    val manual: Boolean = false,
    val task: String = "",
    val countReps: Boolean = false,
    val exercise: RepExercise? = null
) {
    val phase: TimerPhase
        get() = when (kind) {
            TimerStepKind.PREP -> TimerPhase.PREP
            TimerStepKind.WORK -> TimerPhase.WORK
            TimerStepKind.REST -> TimerPhase.REST
        }
}

/** Последовательность фаз, которую проигрывает foreground-сервис. */
data class TimerPlan(
    val title: String,
    val source: TimerPlanSource,
    val steps: List<TimerStep>
) {
    val totalSec: Int get() = steps.sumOf { it.seconds }
    val workSec: Int get() = steps.filter { it.kind == TimerStepKind.WORK }.sumOf { it.seconds }
    val restSec: Int get() = steps.filter { it.kind == TimerStepKind.REST }.sumOf { it.seconds }
    val prepSec: Int get() = steps.firstOrNull { it.kind == TimerStepKind.PREP }?.seconds ?: 0
    val workCount: Int get() = steps.count { it.kind == TimerStepKind.WORK }
    val manualCount: Int get() = steps.count { it.kind == TimerStepKind.WORK && it.manual }
}

/** Обычный таймер «работа / отдых / раунды» превращаем в такую же последовательность. */
fun TimerConfig.toPlan(): TimerPlan {
    val totalRounds = rounds.coerceAtLeast(1)
    val steps = buildList {
        if (prepSec > 0) {
            add(TimerStep(TimerStepKind.PREP, prepSec, "Подготовка"))
        }
        for (round in 1..totalRounds) {
            val counted = round in countRounds
            add(
                TimerStep(
                    kind = TimerStepKind.WORK,
                    seconds = workSec.coerceAtLeast(1),
                    label = "Работа",
                    round = round,
                    rounds = totalRounds,
                    countReps = counted,
                    exercise = if (counted) exercise else null
                )
            )
            if (round < totalRounds && restSec > 0) {
                add(
                    TimerStep(
                        kind = TimerStepKind.REST,
                        seconds = restSec,
                        label = "Отдых",
                        round = round,
                        rounds = totalRounds
                    )
                )
            }
        }
    }
    return TimerPlan("Интервальный таймер", TimerPlanSource.INTERVAL, steps)
}

/**
 * Собирает таймер по тренировке: подходы идут по порядку упражнений.
 * Отдых берётся у завершённого подхода, поэтому пауза после каждого подхода
 * может быть своей. Подход без времени становится ручным заданием.
 */
fun Workout.toTimerPlan(prepSec: Int = 15): TimerPlan {
    val workSections = sections.filter { it.sets.isNotEmpty() }
    val totalWork = workSections.sumOf { it.sets.size }
    val steps = mutableListOf<TimerStep>()

    if (totalWork > 0 && prepSec > 0) {
        steps += TimerStep(TimerStepKind.PREP, prepSec, "Подготовка")
    }

    var completedWork = 0
    workSections.forEach { section ->
        val sets = section.sets
        sets.forEachIndexed { index, set ->
            completedWork++
            val label = buildString {
                append(section.type.label)
                if (set.weightKg > 0) append(" · ${set.weightKg} кг")
            }
            val manual = set.durationSec <= 0
            // Подходы на гирях можно сразу снимать камерой: упражнение известно
            // из раздела тренировки.
            val exercise = RepExercise.fromSection(section.type)
            steps += TimerStep(
                kind = TimerStepKind.WORK,
                seconds = if (manual) 0 else set.durationSec,
                label = label,
                round = index + 1,
                rounds = sets.size,
                manual = manual,
                task = set.taskLabel(),
                countReps = exercise != null,
                exercise = exercise
            )
            if (completedWork < totalWork && set.restSec > 0) {
                steps += TimerStep(
                    kind = TimerStepKind.REST,
                    seconds = set.restSec,
                    label = "Отдых",
                    round = index + 1,
                    rounds = sets.size
                )
            }
        }
    }

    return TimerPlan("Тренировка", TimerPlanSource.WORKOUT, steps)
}

private fun SetEntry.taskLabel(): String =
    if (weightKg > 0) "$weightKg кг × $reps" else "$reps повторений"
