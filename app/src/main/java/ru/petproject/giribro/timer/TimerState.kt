package ru.petproject.giribro.timer

import ru.petproject.giribro.vision.RepExercise

/** Конфигурация обычного интервального таймера. */
data class TimerConfig(
    val workSec: Int = 120,
    val restSec: Int = 180,
    val rounds: Int = 5,
    val prepSec: Int = 15,
    /** Номера раундов (с 1), которые надо снимать камерой. */
    val countRounds: Set<Int> = emptySet(),
    val exercise: RepExercise = RepExercise.JERK
) {
    val totalSec: Int get() = prepSec + rounds * workSec + (rounds - 1) * restSec
}

enum class TimerPhase { IDLE, PREP, WORK, REST, DONE }

data class TimerState(
    val phase: TimerPhase = TimerPhase.IDLE,
    val remainingSec: Int = 0,
    val stepIndex: Int = -1,
    val running: Boolean = false,
    val plan: TimerPlan? = null,
    /**
     * Черновик тренировки, из которой запущен план. Нужен после завершения,
     * чтобы предложить сохранить/поделиться/отредактировать результат.
     */
    val workoutDraftJson: String = ""
) {
    val step: TimerStep? get() = plan?.steps?.getOrNull(stepIndex)

    val phaseLabel: String
        get() = when (phase) {
            TimerPhase.IDLE -> "Готов"
            TimerPhase.PREP -> "Подготовка"
            TimerPhase.WORK -> "Работа"
            TimerPhase.REST -> "Отдых"
            TimerPhase.DONE -> "Готово"
        }

    val round: Int get() = step?.round ?: 0

    val totalRounds: Int get() = step?.rounds ?: 0

    val isActive: Boolean
        get() = phase == TimerPhase.PREP ||
            phase == TimerPhase.WORK ||
            phase == TimerPhase.REST

    /** Подход без времени: ждём явного подтверждения. */
    val isManual: Boolean get() = isActive && step?.manual == true

    /** Сколько рабочих подходов ещё осталось, включая текущий. */
    val remainingWork: Int
        get() {
            if (!isActive) return 0
            val steps = plan?.steps ?: return 0
            return steps.drop(stepIndex.coerceAtLeast(0))
                .count { it.kind == TimerStepKind.WORK }
        }

    val nextStep: TimerStep? get() = plan?.steps?.getOrNull(stepIndex + 1)

    /** «подход 2 из 4» для тренировки, «раунд 2 из 5» для обычного таймера. */
    val counterLabel: String
        get() {
            if (round <= 0 || totalRounds <= 0) return ""
            return if (plan?.source == TimerPlanSource.WORKOUT) {
                "подход $round из $totalRounds"
            } else {
                "раунд $round из $totalRounds"
            }
        }
}
