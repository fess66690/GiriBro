package ru.petproject.giribro

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.petproject.giribro.data.AthleteProfile
import ru.petproject.giribro.data.BackupFormatException
import ru.petproject.giribro.data.BackupPasswordException
import ru.petproject.giribro.data.BodyMeasurement
import ru.petproject.giribro.data.DiaryBackupJson
import ru.petproject.giribro.data.NormAgeGroup
import ru.petproject.giribro.data.NormGender
import ru.petproject.giribro.data.Section
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.SetEntry
import ru.petproject.giribro.data.Settings
import ru.petproject.giribro.data.StatsPeriod
import ru.petproject.giribro.data.ThemeMode
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.vision.FixationPreset
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
        statsPeriod = StatsPeriod.YEAR,
        fixationPreset = FixationPreset.STRICT
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
        assertTrue(backup.bodyMeasurements.isEmpty())
        assertEquals(settings, backup.settings)
    }

    @Test
    fun `body measurements survive encode and decode`() {
        val measurements = listOf(
            BodyMeasurement(
                date = date,
                weightKg = 82.5,
                waistCm = 86.0,
                chestCm = 104.0,
                hipsCm = 100.0,
                armCm = 36.0,
                thighCm = 58.5,
                note = "Утро, натощак",
                createdAt = 789L
            ),
            BodyMeasurement(
                date = date.minusWeeks(1),
                weightKg = 83.1,
                waistCm = 87.0,
                createdAt = 700L
            )
        )

        val backup = DiaryBackupJson.decode(
            DiaryBackupJson.encode(
                workouts = listOf(workout()),
                settings = settings,
                bodyMeasurements = measurements,
                exportedAt = 456L
            )
        )

        assertEquals(measurements, backup.bodyMeasurements)
    }

    @Test
    fun `version 1 backup without body measurements is accepted`() {
        val root = JSONObject(DiaryBackupJson.encode(listOf(workout()), settings))
            .put("version", 1)
        root.remove("bodyMeasurements")

        val backup = DiaryBackupJson.decode(root.toString())

        assertEquals(1, backup.workouts.size)
        assertTrue(backup.bodyMeasurements.isEmpty())
    }

    @Test
    fun `version 2 backup without profile is accepted`() {
        val root = JSONObject(DiaryBackupJson.encode(listOf(workout()), settings))
            .put("version", 2)
        root.remove("profile")

        val backup = DiaryBackupJson.decode(root.toString())

        assertEquals(1, backup.workouts.size)
        assertNull(backup.profile)
    }

    @Test
    fun `athlete profile survives encode and decode`() {
        val profile = AthleteProfile(
            gender = NormGender.FEMALE,
            ageGroup = NormAgeGroup.YOUTH,
            weightKg = 61.5
        )

        val backup = DiaryBackupJson.decode(
            DiaryBackupJson.encode(
                workouts = emptyList(),
                settings = settings,
                profile = profile
            )
        )

        assertEquals(profile, backup.profile)
    }

    @Test
    fun `encrypted backup round trip hides content and needs the password`() {
        val profile = AthleteProfile(
            gender = NormGender.MALE,
            ageGroup = NormAgeGroup.ADULT,
            weightKg = 82.0
        )
        val encoded = DiaryBackupJson.encode(
            workouts = listOf(workout()),
            settings = settings,
            bodyMeasurements = listOf(
                BodyMeasurement(date = date, weightKg = 82.5, createdAt = 789L)
            ),
            profile = profile,
            password = "kettlebell-2026"
        )

        assertTrue(DiaryBackupJson.isEncrypted(encoded))
        assertTrue(!encoded.contains("Тяжёлая тренировка"))
        assertTrue(!encoded.contains("bodyMeasurements"))

        val backup = DiaryBackupJson.decode(encoded, "kettlebell-2026")
        assertEquals(1, backup.workouts.size)
        assertEquals(settings, backup.settings)
        assertEquals(profile, backup.profile)
        assertEquals(82.5, backup.bodyMeasurements.single().weightKg, 0.001)
    }

    @Test
    fun `wrong password is reported separately from broken files`() {
        val encoded = DiaryBackupJson.encode(
            workouts = emptyList(),
            settings = settings,
            password = "right-password"
        )

        assertThrows(BackupPasswordException::class.java) {
            DiaryBackupJson.decode(encoded, "wrong-password")
        }
        assertThrows(BackupFormatException::class.java) {
            DiaryBackupJson.decode(encoded)
        }
    }

    @Test
    fun `invalid body measurement blocks the whole import`() {
        val raw = JSONObject(DiaryBackupJson.encode(emptyList(), settings))
            .put(
                "bodyMeasurements",
                JSONArray().put(
                    JSONObject()
                        .put("date", date.toString())
                        .put("weightKg", 0.0)
                )
            )
            .toString()

        assertThrows(BackupFormatException::class.java) {
            DiaryBackupJson.decode(raw)
        }
    }

    @Test
    fun `broken body measurements list blocks the whole import`() {
        val raw = JSONObject(DiaryBackupJson.encode(emptyList(), settings))
            .put("bodyMeasurements", "не список")
            .toString()

        assertThrows(BackupFormatException::class.java) {
            DiaryBackupJson.decode(raw)
        }
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
        assertEquals(FixationPreset.COMPETITION, restored?.fixationPreset)
    }

    @Test
    fun `old backup without fixation preset falls back to competition`() {
        val root = JSONObject(DiaryBackupJson.encode(emptyList(), settings))
        root.getJSONObject("settings").remove("fixationPreset")

        val restored = DiaryBackupJson.decode(root.toString()).settings

        assertEquals(FixationPreset.COMPETITION, restored?.fixationPreset)
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
