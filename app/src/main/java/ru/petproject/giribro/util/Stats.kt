package ru.petproject.giribro.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val RU = Locale("ru")
private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMMM", RU)
private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE", RU)

fun LocalDate.ruDayMonth(): String = format(dayMonthFormatter)

fun LocalDate.ruWeekday(): String =
    format(weekdayFormatter).replaceFirstChar { it.titlecase(RU) }

fun weekStart(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Сколько недель подряд выполнена недельная цель. */
fun computeStreak(dates: List<LocalDate>, goal: Int, today: LocalDate = LocalDate.now()): Int {
    if (goal <= 0 || dates.isEmpty()) return 0

    val perWeek = HashMap<LocalDate, Int>()
    dates.forEach { date ->
        val key = weekStart(date)
        perWeek[key] = (perWeek[key] ?: 0) + 1
    }

    var cursor = weekStart(today)
    if ((perWeek[cursor] ?: 0) < goal) cursor = cursor.minusWeeks(1)

    var streak = 0
    while ((perWeek[cursor] ?: 0) >= goal) {
        streak++
        cursor = cursor.minusWeeks(1)
    }
    return streak
}

/** Лучшая серия за всё время. */
fun bestStreak(dates: List<LocalDate>, goal: Int): Int {
    if (goal <= 0 || dates.isEmpty()) return 0
    val perWeek = dates.groupingBy { weekStart(it) }.eachCount()
    val weeks = perWeek.filterValues { it >= goal }.keys.sorted()
    var best = 0
    var current = 0
    var previous: LocalDate? = null
    weeks.forEach { week ->
        current = if (previous != null && previous!!.plusWeeks(1) == week) current + 1 else 1
        best = maxOf(best, current)
        previous = week
    }
    return best
}

fun formatClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", safe / 60, safe % 60)
}

/** Большие часы таймера: «05:09» вместо «5:09». */
fun formatClockPadded(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
}

fun pluralRu(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..19 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
