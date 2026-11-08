package ru.petproject.giribro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.petproject.giribro.data.Section
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.SetEntry
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.data.WorkoutValidator
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

    @Test
    fun `empty draft is not valid and explains why`() {
        val vm = NewWorkoutViewModel()
        val draft = vm.draft.value

        assertFalse(draft.isValid)
        assertNotNull(draft.validationError)
    }

    @Test
    fun `draft with a set is valid`() {
        val vm = NewWorkoutViewModel()
        vm.addSection(SectionType.JERK)
        val key = vm.draft.value.sections.single().key
        vm.addSet(key, SetEntry(24, 20, 120))

        assertTrue(vm.draft.value.isValid)
        assertNull(vm.draft.value.validationError)
    }

    @Test
    fun `invalid set makes draft invalid`() {
        val vm = NewWorkoutViewModel()
        vm.addSection(SectionType.JERK)
        val key = vm.draft.value.sections.single().key
        vm.addSet(key, SetEntry(24, 0, 120))

        assertFalse(vm.draft.value.isValid)
        assertNotNull(vm.draft.value.validationError)
    }

    @Test
    fun `too long note makes draft invalid`() {
        val vm = NewWorkoutViewModel()
        vm.addSection(SectionType.JERK)
        val key = vm.draft.value.sections.single().key
        vm.addSet(key, SetEntry(24, 20, 120))
        vm.setNote("я".repeat(WorkoutValidator.MAX_NOTE_LENGTH + 1))

        assertFalse(vm.draft.value.isValid)
        assertNotNull(vm.draft.value.validationError)
    }

    @Test
    fun `empty sections are dropped when converting to workout`() {
        val vm = NewWorkoutViewModel()
        vm.addSection(SectionType.WARMUP)
        vm.addSection(SectionType.JERK)
        val jerkKey = vm.draft.value.sections.last().key
        vm.addSet(jerkKey, SetEntry(24, 20, 120))
        vm.setNote("  заметка  ")

        val workout = vm.toWorkout()
        assertEquals(1, workout.sections.size)
        assertEquals(SectionType.JERK, workout.sections.single().type)
        assertEquals("заметка", workout.note)
    }

    @Test
    fun `preparation time is clamped`() {
        val vm = NewWorkoutViewModel()

        vm.setPrep(-100)
        assertEquals(0, vm.draft.value.prepSec)

        vm.setPrep(10_000)
        assertEquals(NewWorkoutViewModel.MAX_PREP_SEC, vm.draft.value.prepSec)
    }
}
