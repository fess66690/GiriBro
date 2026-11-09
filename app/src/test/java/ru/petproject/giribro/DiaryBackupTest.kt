package ru.petproject.giribro

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.petproject.giribro.data.BackupFormatException
import ru.petproject.giribro.data.DiaryBackupJson
import ru.petproject.giribro.data.Section
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.SetEntry
import ru.petproject.giribro.data.Settings
import ru.petproject.giribro.data.StatsPeriod
import ru.petproject.giribro.data.ThemeMode
import ru.petproject.giribro.data.Workout
import java.time.LocalDate

class DiaryBackupTest {

    private val date = LocalDate.of(2026, 11, 4)

    private fun workout(
        date: LocalDate = this.date,
        note: String = "Тяжёлая тренировка",
        createdAt: Long = 123L
    ) = Workout(
        date = date,
        sections = listOf(
            Section(
                SectionType.JERK,
                listOf(SetEntry(24, 20, 120, restSec = 60))
            ),
            Section(
                SectionType.SNATCH,
                listOf(SetEntry(24, 15, 0, restSec = 90))
            )
        ),
        note = note,
        createdAt = createdAt
    )

    private val settings = Settings(
        themeMode = ThemeMode.LIGHT,
        weeklyGoal = 5,
        defaultWeight = 32,
        statsPeriod = StatsPeriod.YEAR
    )

    @Test
    fun `backup survives encode and decode`() {
        val workouts = listOf(
            workout(),
            workout(date.minusDays(1), note = "Разгрузка", createdAt = 100L)
        )

        val backup = DiaryBackupJson.decode(
            DiaryBackupJson.encode(workouts, settings, exportedAt = 456L)
        )

        assertEquals(456L, backup.exportedAt)
        assertEquals(settings, backup.settings)
        assertEquals(2, backup.workouts.size)
        assertEquals(date, backup.workouts.first().date)
        assertEquals("Тяжёлая тренировка", backup.workouts.first().note)
        assertEquals(workouts.first().sections, backup.workouts.first().sections)
    }

    @Test
    fun `empty diary is a valid backup`() {
        val backup = DiaryBackupJson.decode(DiaryBackupJson.encode(emptyList(), settings))
        assertTrue(backup.workouts.isEmpty())
        assertEquals(settings, backup.settings)
    }

    @Test
    fun `utf8 bom is tolerated`() {
        val raw = "\uFEFF" + DiaryBackupJson.encode(listOf(workout()), settings)
        assertEquals(1, DiaryBackupJson.decode(raw).workouts.size)
    }

    @Test
    fun `missing settings are allowed`() {
        val root = JSONObject(DiaryBackupJson.encode(listOf(workout()), settings))
        root.remove("settings")

        assertNull(DiaryBackupJson.decode(root.toString()).settings)
    }

    @Test
    fun `settings are clamped when decoding`() {
        val raw = JSONObject(DiaryBackupJson.encode(emptyList(), settings))
            .put(
                "settings",
                JSONObject()
                    .put("themeMode", "UNKNOWN")
                    .put("weeklyGoal", 99)
                    .put("defaultWeight", 1)
                    .put("statsPeriod", "UNKNOWN")
            )
            .toString()

        val restored = DiaryBackupJson.decode(raw).settings
        assertEquals(ThemeMode.DARK, restored?.themeMode)
        assertEquals(14, restored?.weeklyGoal)
        assertEquals(4, restored?.defaultWeight)
        assertEquals(StatsPeriod.MONTH, restored?.statsPeriod)
    }

    @Test
    fun `foreign and broken files are rejected`() {
        assertThrows(BackupFormatException::class.java) {
            DiaryBackupJson.decode("")
        }
        assertThrows(BackupFormatException::class.java) {
            DiaryBackupJson.decode("not json")
        }
        assertThrows(BackupFormatException::class.java) {
            DiaryBackupJson.decode("""{"format":"other","version":1,"workouts":[]}""")
        }
        assertThrows(BackupFormatException::class.java) {
            DiaryBackupJson.decode("""{"format":"giribro-diary","version":99,"workouts":[]}""")
        }
        assertThrows(BackupFormatException::class.java) {
            DiaryBackupJson.decode("""{"format":"giribro-diary","version":1}""")
        }
    }

    @Test
    fun `invalid workout blocks the whole import`() {
        val raw = JSONObject(DiaryBackupJson.encode(listOf(workout()), settings))
            .put(
                "workouts",
                JSONObject(DiaryBackupJson.encode(listOf(workout()), settings))
                    .getJSONArray("workouts")
                    .put(
                        JSONObject()
                            .put("date", "not-a-date")
                            .put("sections", emptyArray<Any>())
                    )
            )
            .toString()

        assertThrows(BackupFormatException::class.java) {
            DiaryBackupJson.decode(raw)
        }
    }

    @Test
    fun `unknown exercise type is rejected instead of silently dropped`() {
        val raw = JSONObject(DiaryBackupJson.encode(listOf(workout()), settings))
            .put(
                "workouts",
                JSONObject(DiaryBackupJson.encode(listOf(workout()), settings))
                    .getJSONArray("workouts")
                    .put(
                        JSONObject()
                            .put("date", date.toString())
                            .put(
                                "sections",
                                org.json.JSONArray().put(
                                    JSONObject()
                                        .put("type", "UNKNOWN")
                                        .put(
                                            "sets",
                                            org.json.JSONArray().put(
                                                JSONObject().put("w", 24).put("r", 20)
                                            )
                                        )
                                )
                            )
                    )
            )
            .toString()

        assertThrows(BackupFormatException::class.java) {
            DiaryBackupJson.decode(raw)
        }
    }

    @Test
    fun `long legacy note does not block import`() {
        val longNote = "я".repeat(2_000)
        val backup = DiaryBackupJson.decode(
            DiaryBackupJson.encode(listOf(workout(note = longNote)), settings)
        )

        assertEquals(longNote, backup.workouts.single().note)
    }

    @Test
    fun `invalid set value blocks import`() {
        val raw = JSONObject(DiaryBackupJson.encode(listOf(workout()), settings))
            .put(
                "workouts",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("date", date.toString())
                        .put(
                            "sections",
                            org.json.JSONArray().put(
                                JSONObject()
                                    .put("type", SectionType.JERK.name)
                                    .put(
                                        "sets",
                                        org.json.JSONArray().put(
                                            JSONObject().put("w", 24).put("r", 0)
                                        )
                                    )
                            )
                        )
                )
            )
            .toString()

        assertThrows(BackupFormatException::class.java) {
            DiaryBackupJson.decode(raw)
        }
    }

    @Test
    fun `suggested file name contains date`() {
        assertEquals(
            "giribro-backup-2026-11-09.json",
            DiaryBackupJson.suggestedFileName(LocalDate.of(2026, 11, 9))
        )
    }
}
