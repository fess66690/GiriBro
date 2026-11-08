package ru.petproject.giribro

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.petproject.giribro.data.Section
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.SetEntry
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.data.WorkoutValidator
import java.time.LocalDate

class WorkoutValidatorTest {

    private fun workout(
        sections: List<Section> = listOf(
            Section(SectionType.JERK, listOf(SetEntry(24, 20, 120, restSec = 60)))
        ),
        note: String = ""
    ) = Workout(
        date = LocalDate.of(2026, 11, 4),
        sections = sections,
        note = note
    )

    @Test
    fun `valid workout passes`() {
        assertNull(WorkoutValidator.validate(workout()))
    }

    @Test
    fun `empty workout is rejected`() {
        assertNotNull(WorkoutValidator.validate(workout(sections = emptyList())))
        assertNotNull(
            WorkoutValidator.validate(
                workout(sections = listOf(Section(SectionType.JERK, emptyList())))
            )
        )
    }

    @Test
    fun `zero weight is allowed for bodyweight work`() {
        assertNull(
            WorkoutValidator.validate(
                workout(
                    sections = listOf(
                        Section(SectionType.GPP, listOf(SetEntry(0, 30, 90)))
                    )
                )
            )
        )
    }

    @Test
    fun `negative or too large weight is rejected`() {
        assertNotNull(
            WorkoutValidator.validate(
                workout(sections = listOf(Section(SectionType.JERK, listOf(SetEntry(-1, 20)))))
            )
        )
        assertNotNull(
            WorkoutValidator.validate(
                workout(sections = listOf(Section(SectionType.JERK, listOf(SetEntry(201, 20)))))
            )
        )
    }

    @Test
    fun `reps must be positive`() {
        assertNotNull(
            WorkoutValidator.validate(
                workout(sections = listOf(Section(SectionType.JERK, listOf(SetEntry(24, 0)))))
            )
        )
        assertNotNull(
            WorkoutValidator.validate(
                workout(sections = listOf(Section(SectionType.JERK, listOf(SetEntry(24, 1000)))))
            )
        )
    }

    @Test
    fun `rest and duration are clamped by rules`() {
        assertNotNull(
            WorkoutValidator.validate(
                workout(
                    sections = listOf(
                        Section(
                            SectionType.JERK,
                            listOf(SetEntry(24, 20, 0, restSec = SetEntry.MAX_REST_SEC + 1))
                        )
                    )
                )
            )
        )
        assertNotNull(
            WorkoutValidator.validate(
                workout(
                    sections = listOf(
                        Section(
                            SectionType.JERK,
                            listOf(
                                SetEntry(
                                    24,
                                    20,
                                    WorkoutValidator.MAX_DURATION_SEC + 1
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `note length is limited`() {
        assertNull(
            WorkoutValidator.validate(
                workout(note = "я".repeat(WorkoutValidator.MAX_NOTE_LENGTH))
            )
        )
        assertNotNull(
            WorkoutValidator.validate(
                workout(note = "я".repeat(WorkoutValidator.MAX_NOTE_LENGTH + 1))
            )
        )
    }
}
