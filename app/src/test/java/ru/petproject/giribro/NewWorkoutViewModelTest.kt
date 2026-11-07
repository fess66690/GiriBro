package ru.petproject.giribro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.petproject.giribro.data.Section
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.SetEntry
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.ui.screens.NewWorkoutViewModel
import java.time.LocalDate

class NewWorkoutViewModelTest {

    private val source = Workout(
        id = 42L,
        date = LocalDate.of(2026, 10, 20),
        sections = listOf(
            Section(
                SectionType.JERK,
                listOf(
                    SetEntry(24, 20, 120, restSec = 60),
                    SetEntry(24, 18, 100, restSec = 90)
                )
            )
        ),
        note = "тест",
        createdAt = 123L
    )

    @Test
    fun `loading for edit keeps id and created time`() {
        val vm = NewWorkoutViewModel()
        vm.load(source)

        val result = vm.toWorkout()
        assertEquals(42L, result.id)
        assertEquals(123L, result.createdAt)
        assertEquals(source.date, result.date)
        assertEquals("тест", result.note)
        assertEquals(
            listOf(60, 90),
            result.sections.single().sets.map { it.restSec }
        )
        assertEquals(120, result.sections.single().sets.first().durationSec)
    }

    @Test
    fun `repeat clears id and puts today date`() {
        val vm = NewWorkoutViewModel()
        vm.load(source, repeat = true)

        val result = vm.toWorkout()
        assertEquals(0L, result.id)
        assertNotEquals(123L, result.createdAt)
        assertEquals(LocalDate.now(), result.date)
        assertEquals(2, result.sections.single().sets.size)
    }

    @Test
    fun `rest is changed for one set and clamped`() {
        val vm = NewWorkoutViewModel()
        vm.load(source)
        val key = vm.draft.value.sections.single().key

        vm.changeRest(key, 0, -999)
        assertEquals(
            0,
            vm.draft.value.sections.single().sets.first().restSec
        )
        assertEquals(90, vm.draft.value.sections.single().sets.last().restSec)

        vm.changeRest(key, 0, 9999)
        assertEquals(
            SetEntry.MAX_REST_SEC,
            vm.draft.value.sections.single().sets.first().restSec
        )
    }

    @Test
    fun `new set inherits rest of previous set`() {
        val vm = NewWorkoutViewModel()
        vm.load(source)
        val key = vm.draft.value.sections.single().key

        vm.addSet(key, SetEntry(24, 16, 80))

        val sets = vm.draft.value.sections.single().sets
        assertEquals(3, sets.size)
        assertEquals(90, sets.last().restSec)
    }

    @Test
    fun `new workout starts without id`() {
        val vm = NewWorkoutViewModel()
        assertTrue(vm.toWorkout().id == 0L)
        assertEquals(LocalDate.now(), vm.toWorkout().date)
    }
}
