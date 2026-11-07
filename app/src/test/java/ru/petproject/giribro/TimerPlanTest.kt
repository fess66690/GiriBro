package ru.petproject.giribro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.petproject.giribro.data.Section
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.SetEntry
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.timer.TimerConfig
import ru.petproject.giribro.timer.TimerPlanSource
import ru.petproject.giribro.timer.TimerStepKind
import ru.petproject.giribro.timer.toPlan
import ru.petproject.giribro.timer.toTimerPlan
import java.time.LocalDate

class TimerPlanTest {

    private fun workout(vararg sections: Section) = Workout(
        date = LocalDate.of(2026, 11, 4),
        sections = sections.toList()
    )

    @Test
    fun `interval config keeps work and rest sequence`() {
        val config = TimerConfig(workSec = 120, restSec = 180, rounds = 3, prepSec = 15)
        val plan = config.toPlan()

        assertEquals(TimerPlanSource.INTERVAL, plan.source)
        assertEquals(6, plan.steps.size)
        assertEquals(
            listOf(
                TimerStepKind.PREP,
                TimerStepKind.WORK,
                TimerStepKind.REST,
                TimerStepKind.WORK,
                TimerStepKind.REST,
                TimerStepKind.WORK
            ),
            plan.steps.map { it.kind }
        )
        assertEquals(config.totalSec, plan.totalSec)
        assertEquals(360, plan.workSec)
        assertEquals(360, plan.restSec)
        assertEquals(15, plan.prepSec)
    }

    @Test
    fun `workout plan keeps per-set rest and turns untimed sets into tasks`() {
        val plan = workout(
            Section(
                SectionType.JERK,
                listOf(
                    SetEntry(24, 20, 120, restSec = 60),
                    SetEntry(24, 15, 90, restSec = 90)
                )
            ),
            Section(
                SectionType.SNATCH,
                listOf(
                    SetEntry(24, 20, 0, restSec = 45),
                    SetEntry(24, 18, 60)
                )
            )
        ).toTimerPlan(prepSec = 10)

        assertEquals(TimerPlanSource.WORKOUT, plan.source)
        assertEquals(8, plan.steps.size)
        assertEquals(475, plan.totalSec)
        assertEquals(270, plan.workSec)
        assertEquals(195, plan.restSec)
        assertEquals(10, plan.prepSec)
        assertEquals(4, plan.workCount)
        assertEquals(1, plan.manualCount)

        assertEquals(TimerStepKind.PREP, plan.steps[0].kind)
        assertEquals("Толчок · 24 кг", plan.steps[1].label)
        assertEquals("24 кг × 20", plan.steps[1].task)
        assertEquals(1, plan.steps[1].round)
        assertEquals(2, plan.steps[1].rounds)
        assertEquals(TimerStepKind.REST, plan.steps[2].kind)
        assertEquals(60, plan.steps[2].seconds)
        assertEquals("Толчок · 24 кг", plan.steps[3].label)
        assertEquals(2, plan.steps[3].round)
        assertEquals(90, plan.steps[4].seconds)

        // У рывка первый подход без времени: это ручное задание с отдыхом 45 секунд.
        assertEquals(TimerStepKind.WORK, plan.steps[5].kind)
        assertTrue(plan.steps[5].manual)
        assertEquals(0, plan.steps[5].seconds)
        assertEquals("Рывок · 24 кг", plan.steps[5].label)
        assertEquals("24 кг × 20", plan.steps[5].task)
        assertEquals(TimerStepKind.REST, plan.steps[6].kind)
        assertEquals(45, plan.steps[6].seconds)
        assertEquals(TimerStepKind.WORK, plan.steps.last().kind)
        assertEquals(60, plan.steps.last().seconds)
    }

    @Test
    fun `workout without timings becomes manual steps`() {
        val plan = workout(
            Section(SectionType.JERK, listOf(SetEntry(24, 20)))
        ).toTimerPlan(prepSec = 0)

        assertEquals(0, plan.workSec)
        assertEquals(1, plan.workCount)
        assertEquals(1, plan.manualCount)
        assertTrue(plan.steps.single().manual)
        assertEquals(TimerStepKind.WORK, plan.steps.single().kind)
    }

    @Test
    fun `workout without preparation starts with work`() {
        val plan = workout(
            Section(SectionType.JERK, listOf(SetEntry(24, 20, 120)))
        ).toTimerPlan(prepSec = 0)

        assertEquals(TimerStepKind.WORK, plan.steps.first().kind)
        assertEquals(1, plan.steps.size)
    }
}
