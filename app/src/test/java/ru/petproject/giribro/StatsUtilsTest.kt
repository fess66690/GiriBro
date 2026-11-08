package ru.petproject.giribro

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.petproject.giribro.data.asDuration
import ru.petproject.giribro.data.asTonnage
import ru.petproject.giribro.util.bestStreak
import ru.petproject.giribro.util.computeStreak
import ru.petproject.giribro.util.formatClock
import ru.petproject.giribro.util.formatClockPadded
import ru.petproject.giribro.util.pluralRu
import java.time.LocalDate

class StatsUtilsTest {

    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun `empty dates and non positive goal produce zero streak`() {
        assertEquals(0, computeStreak(emptyList(), goal = 3, today = today))
        assertEquals(0, computeStreak(listOf(today), goal = 0, today = today))
        assertEquals(0, bestStreak(emptyList(), goal = 3))
        assertEquals(0, bestStreak(listOf(today), goal = -1))
    }

    @Test
    fun `duplicate workouts in one week count toward the goal`() {
        val dates = listOf(
            today,
            today.minusDays(1),
            today.minusWeeks(1),
            today.minusWeeks(1).minusDays(1)
        )

        assertEquals(2, computeStreak(dates, goal = 2, today = today))
        assertEquals(2, bestStreak(dates, goal = 2))
    }

    @Test
    fun `best streak finds the longest consecutive block`() {
        val dates = listOf(
            today.minusWeeks(8),
            today.minusWeeks(8).minusDays(1),
            today.minusWeeks(7),
            today.minusWeeks(7).minusDays(1),
            today.minusWeeks(7).minusDays(2),
            today.minusWeeks(2),
            today.minusWeeks(2).minusDays(1)
        )

        assertEquals(2, bestStreak(dates, goal = 2))
    }

    @Test
    fun `timer clocks never go negative`() {
        assertEquals("0:00", formatClock(-5))
        assertEquals("00:00", formatClockPadded(-5))
        assertEquals("1:05", formatClock(65))
        assertEquals("01:05", formatClockPadded(65))
    }

    @Test
    fun `russian plural picks correct form`() {
        assertEquals("подъём", pluralRu(1, "подъём", "подъёма", "подъёмов"))
        assertEquals("подъёма", pluralRu(2, "подъём", "подъёма", "подъёмов"))
        assertEquals("подъёмов", pluralRu(5, "подъём", "подъёма", "подъёмов"))
        assertEquals("подъёмов", pluralRu(11, "подъём", "подъёма", "подъёмов"))
        assertEquals("подъём", pluralRu(21, "подъём", "подъёма", "подъёмов"))
        assertEquals("подъёма", pluralRu(22, "подъём", "подъёма", "подъёмов"))
    }

    @Test
    fun `duration and tonnage formatting`() {
        assertEquals("1:05", 65.asDuration())
        assertEquals("1:01:05", 3665.asDuration())
        assertEquals("500 кг", 500.0.asTonnage())
        assertEquals("1.50 т", 1500.0.asTonnage())
    }
}
