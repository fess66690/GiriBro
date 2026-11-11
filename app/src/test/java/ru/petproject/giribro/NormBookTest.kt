package ru.petproject.giribro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.petproject.giribro.data.CompetitionCalendar
import ru.petproject.giribro.data.NormBook
import ru.petproject.giribro.data.NormDiscipline
import ru.petproject.giribro.data.NormGender
import ru.petproject.giribro.data.NormPeriodData
import ru.petproject.giribro.data.NormRank
import java.time.LocalDate

class NormBookTest {

    private val current: NormPeriodData = NormBook.period("2026")
    private val previous: NormPeriodData = NormBook.period("2022")

    @Test
    fun `current period has all three disciplines and youth columns`() {
        assertEquals(
            listOf(NormDiscipline.JERK, NormDiscipline.LONG_CYCLE, NormDiscipline.SNATCH),
            current.disciplines.map { it.discipline }
        )
        assertTrue(current.hasYouth)

        val jerk = current.discipline(NormDiscipline.JERK)
        assertNotNull(jerk)
        assertEquals(12, jerk!!.male.categories.size)
        assertEquals(8, jerk.female.categories.size)
        assertEquals(32, jerk.male.weightMasters)
        assertEquals(24, jerk.male.weightRanks)
        assertEquals(16, jerk.male.weightYouth)
        assertTrue(jerk.male.hasYouthValues)
    }

    @Test
    fun `previous period has biathlon and no youth columns`() {
        assertTrue(previous.disciplines.any { it.discipline == NormDiscipline.BIATHLON })
        assertEquals(3, previous.disciplines.size)
        assertTrue(!previous.hasYouth)
        assertTrue(previous.disciplines.none { it.male.hasYouthValues || it.female.hasYouthValues })
    }

    @Test
    fun `current tables contain values for every weight class`() {
        current.disciplines.forEach { discipline ->
            listOf(discipline.male, discipline.female).forEach { gender ->
                assertTrue(gender.categories.isNotEmpty())
                gender.categories.forEach { category ->
                    assertTrue(
                        "${discipline.name}, ${category.weight} кг",
                        NormRank.entries.any { category.value(it) != null }
                    )
                }
            }
        }
    }

    @Test
    fun `weight matching follows the site rule`() {
        val male = current.discipline(NormDiscipline.JERK)!!.male
        assertEquals("48", NormBook.weightCategory(45.0, male)?.weight)
        assertEquals("73", NormBook.weightCategory(73.0, male)?.weight)
        // На сайте обычная категория приоритетнее «+»-категории.
        assertEquals("78", NormBook.weightCategory(74.0, male)?.weight)
        assertEquals("85", NormBook.weightCategory(80.0, male)?.weight)
        assertEquals("95", NormBook.weightCategory(95.0, male)?.weight)
        assertEquals("95+", NormBook.weightCategory(99.9, male)?.weight)

        val female = current.discipline(NormDiscipline.JERK)!!.female
        assertEquals("53", NormBook.weightCategory(50.0, female)?.weight)
        assertEquals("63", NormBook.weightCategory(60.0, female)?.weight)
        assertEquals("68+", NormBook.weightCategory(70.0, female)?.weight)
    }

    @Test
    fun `weight parser accepts comma and rejects junk`() {
        assertEquals(73.5, NormBook.parseWeight(" 73,5 ")!!, 0.001)
        assertEquals(82.0, NormBook.parseWeight("82")!!, 0.001)
        assertNull(NormBook.parseWeight(""))
        assertNull(NormBook.parseWeight("abc"))
        assertNull(NormBook.parseWeight("0"))
        assertNull(NormBook.parseWeight("-5"))
    }

    @Test
    fun `gender lookup returns matching table`() {
        val snatch = current.discipline(NormDiscipline.SNATCH)!!
        assertEquals(snatch.male, snatch.gender(NormGender.MALE))
        assertEquals(snatch.female, snatch.gender(NormGender.FEMALE))
    }
}

class CompetitionCalendarTest {

    @Test
    fun `calendar contains all competitions sorted by date`() {
        assertEquals(29, CompetitionCalendar.events.size)
        assertEquals(
            CompetitionCalendar.events.sortedBy { it.start },
            CompetitionCalendar.events
        )
        assertTrue(CompetitionCalendar.events.all { it.start.year == CompetitionCalendar.YEAR })
        assertTrue(CompetitionCalendar.events.all { !it.end.isBefore(it.start) })
        assertTrue(CompetitionCalendar.events.all { it.title.isNotBlank() && it.place.isNotBlank() })
    }

    @Test
    fun `upcoming and nearest are calculated for a fixed date`() {
        val today = LocalDate.of(2026, 11, 10)
        val upcoming = CompetitionCalendar.upcoming(today)
        assertEquals(5, upcoming.size)
        assertEquals("Международные соревнования на призы ЗМС Леонова", upcoming.first().title)
        assertEquals(upcoming.first(), CompetitionCalendar.nearest(today))
        assertEquals(24, CompetitionCalendar.past(today).size)
    }

    @Test
    fun `month grouping is ordered`() {
        val months = CompetitionCalendar.byMonth(CompetitionCalendar.events).keys.toList()
        assertEquals(months.sorted(), months)
        assertEquals(10, months.size)
    }

    @Test
    fun `date labels handle one month and crossed months`() {
        val oneMonth = CompetitionCalendar.events.first { it.id == 42 }
        assertTrue(oneMonth.dateLabel().startsWith("13–16"))
        val crossed = CompetitionCalendar.events.first { it.id == 18 }
        assertTrue(crossed.dateLabel().contains("—"))
    }
}
