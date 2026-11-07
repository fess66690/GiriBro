package ru.petproject.giribro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.petproject.giribro.data.StatsPeriod
import java.time.LocalDate

class StatsPeriodTest {

    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun `week covers seven days including today`() {
        assertEquals(today.minusDays(6), StatsPeriod.WEEK.startDate(today))
    }

    @Test
    fun `month covers thirty days`() {
        assertEquals(today.minusDays(29), StatsPeriod.MONTH.startDate(today))
    }

    @Test
    fun `quarter covers ninety days`() {
        assertEquals(today.minusDays(89), StatsPeriod.QUARTER.startDate(today))
    }

    @Test
    fun `year covers three hundred sixty five days`() {
        assertEquals(today.minusDays(364), StatsPeriod.YEAR.startDate(today))
    }

    @Test
    fun `all time has no lower bound`() {
        assertNull(StatsPeriod.ALL.startDate(today))
    }
}
