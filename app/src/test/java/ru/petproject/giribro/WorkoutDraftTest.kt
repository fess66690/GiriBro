package ru.petproject.giribro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.petproject.giribro.data.Section
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.SetEntry
import ru.petproject.giribro.data.WorkoutDraft
import ru.petproject.giribro.data.WorkoutDraftJson
import java.time.LocalDate

class WorkoutDraftTest {

    private val date = LocalDate.of(2026, 11, 14)

    private val draft = WorkoutDraft(
        date = date,
        note = "Черновик",
        sections = listOf(
            Section(
                SectionType.JERK,
                listOf(
                    SetEntry(24, 20, 120, restSec = 60),
                    SetEntry(24, 18, 100, restSec = 90)
                )
            )
        ),
        prepSec = 25,
        editingId = 42L,
        editingCreatedAt = 123L
    )

    @Test
    fun `draft survives json round trip`() {
        val restored = WorkoutDraftJson.decode(WorkoutDraftJson.encode(draft))

        assertEquals(draft, restored)
    }

    @Test
    fun `empty draft has no content`() {
        assertFalse(WorkoutDraft(date = date).hasContent)
        assertTrue(draft.hasContent)
        assertEquals(2, draft.setsCount)
        assertEquals(38, draft.reps)
    }

    @Test
    fun `empty section does not make draft non-empty`() {
        val empty = WorkoutDraft(
            date = date,
            sections = listOf(Section(SectionType.WARMUP, emptyList()))
        )

        assertFalse(empty.hasContent)
    }

    @Test
    fun `broken and future drafts are ignored`() {
        assertNull(WorkoutDraftJson.decode(""))
        assertNull(WorkoutDraftJson.decode("not-json"))
        assertNull(
            WorkoutDraftJson.decode(
                """{"version":99,"date":"2026-11-14","sections":[]}"""
            )
        )
        assertNull(
            WorkoutDraftJson.decode(
                """{"version":1,"date":"not-a-date","sections":[]}"""
            )
        )
    }

    @Test
    fun `preparation time is clamped when decoding`() {
        val raw = """{"version":1,"date":"2026-11-14","sections":[],"prepSec":9999}"""
        val restored = WorkoutDraftJson.decode(raw)

        assertEquals(120, restored?.prepSec)
    }
}
