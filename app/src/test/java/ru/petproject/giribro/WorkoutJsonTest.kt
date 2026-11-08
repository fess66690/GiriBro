package ru.petproject.giribro

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.petproject.giribro.data.Section
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.SetEntry
import ru.petproject.giribro.data.WorkoutJson

class WorkoutJsonTest {

    @Test
    fun `sections survive encode and decode`() {
        val sections = listOf(
            Section(
                SectionType.WARMUP,
                listOf(SetEntry(16, 20, 60, restSec = 45))
            ),
            Section(
                SectionType.JERK,
                listOf(
                    SetEntry(24, 20, 120, restSec = 60),
                    SetEntry(24, 15, 90, restSec = 90)
                )
            ),
            Section(
                SectionType.LONG_CYCLE,
                listOf(SetEntry(32, 10, 75, restSec = 120))
            )
        )

        assertEquals(sections, WorkoutJson.decode(WorkoutJson.encode(sections)))
    }

    @Test
    fun `empty workout encodes to empty array`() {
        assertEquals("[]", WorkoutJson.encode(emptyList()))
        assertTrue(WorkoutJson.decode("[]").isEmpty())
    }

    @Test
    fun `legacy section rest becomes fallback for sets`() {
        val raw = JSONArray()
            .put(
                JSONObject()
                    .put("type", SectionType.JERK.name)
                    .put("rest", 75)
                    .put(
                        "sets",
                        JSONArray().put(
                            JSONObject()
                                .put("w", 24)
                                .put("r", 20)
                        )
                    )
            )
            .toString()

        val section = WorkoutJson.decode(raw).single()
        assertEquals(75, section.sets.single().restSec)
    }

    @Test
    fun `per set rest overrides legacy rest`() {
        val raw = JSONArray()
            .put(
                JSONObject()
                    .put("type", SectionType.SNATCH.name)
                    .put("rest", 75)
                    .put(
                        "sets",
                        JSONArray().put(
                            JSONObject()
                                .put("w", 24)
                                .put("r", 20)
                                .put("rest", 30)
                        )
                    )
            )
            .toString()

        assertEquals(30, WorkoutJson.decode(raw).single().sets.single().restSec)
    }

    @Test
    fun `rest values are clamped while decoding`() {
        val raw = JSONArray()
            .put(
                JSONObject()
                    .put("type", SectionType.JERK.name)
                    .put(
                        "sets",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("w", 24)
                                    .put("r", 20)
                                    .put("rest", -10)
                            )
                            .put(
                                JSONObject()
                                    .put("w", 24)
                                    .put("r", 15)
                                    .put("rest", 9999)
                            )
                    )
            )
            .toString()

        val sets = WorkoutJson.decode(raw).single().sets
        assertEquals(0, sets[0].restSec)
        assertEquals(SetEntry.MAX_REST_SEC, sets[1].restSec)
    }

    @Test
    fun `unknown section type and empty section are skipped`() {
        val raw = JSONArray()
            .put(
                JSONObject()
                    .put("type", "UNKNOWN")
                    .put(
                        "sets",
                        JSONArray().put(JSONObject().put("w", 24).put("r", 20))
                    )
            )
            .put(JSONObject().put("type", SectionType.JERK.name).put("sets", JSONArray()))
            .toString()

        assertTrue(WorkoutJson.decode(raw).isEmpty())
    }

    @Test
    fun `malformed json does not throw`() {
        assertTrue(WorkoutJson.decode("not a json").isEmpty())
        assertTrue(WorkoutJson.decode("{}").isEmpty())
        assertTrue(WorkoutJson.decode("").isEmpty())
    }
}
