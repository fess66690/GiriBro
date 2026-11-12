package ru.petproject.giribro.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Демонстрационные тренировки, чтобы приложение не встречало пользователя пустым экраном.
 * Заполняются один раз при первом запуске.
 */
object DemoData {

    /** Несколько замеров тела, чтобы новый экран сразу показывал динамику. */
    fun measurements(today: LocalDate = LocalDate.now()): List<BodyMeasurement> {
        val samples = listOf(
            DemoSample(42, 85.0, 91.0, 106.0, 102.0, 36.5, 59.5, "Утро, натощак"),
            DemoSample(35, 84.6, 90.2, 105.6, 101.8, 36.4, 59.2, ""),
            DemoSample(28, 84.2, 89.4, 105.2, 101.4, 36.3, 59.0, "После разгрузочной недели"),
            DemoSample(21, 83.7, 88.5, 104.8, 101.0, 36.2, 58.8, ""),
            DemoSample(14, 83.1, 87.6, 104.5, 100.6, 36.1, 58.7, ""),
            DemoSample(7, 82.6, 86.7, 104.2, 100.3, 36.0, 58.6, "Утро, натощак"),
            DemoSample(1, 82.1, 86.0, 104.0, 100.0, 36.0, 58.5, "")
        )
        return samples.map { sample ->
            val date = today.minusDays(sample.daysAgo)
            BodyMeasurement(
                date = date,
                weightKg = sample.weightKg,
                waistCm = sample.waistCm,
                chestCm = sample.chestCm,
                hipsCm = sample.hipsCm,
                armCm = sample.armCm,
                thighCm = sample.thighCm,
                note = sample.note,
                createdAt = date.toEpochDay() * 86_400_000L + 8 * 3_600_000L
            )
        }
    }

    fun workouts(today: LocalDate = LocalDate.now()): List<Workout> {
        val result = mutableListOf<Workout>()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        for (back in 9 downTo 0) {
            val weekStart = monday.minusWeeks(back.toLong())
            val planned = listOf(
                weekStart to workout(weekStart, 0, heavy = back % 3 == 0),
                weekStart.plusDays(2) to workout(weekStart.plusDays(2), 1, heavy = false),
                weekStart.plusDays(4) to workout(weekStart.plusDays(4), 2, heavy = back % 3 == 1)
            )
            planned.forEach { (date, model) ->
                if (!date.isAfter(today)) {
                    result += model.copy(date = date)
                }
            }
        }
        return result
    }

    private fun workout(date: LocalDate, index: Int, heavy: Boolean): Workout {
        val base = if (heavy) 32 else 24
        val jerkSets = when (index) {
            0 -> listOf(
                SetEntry(base, 25, 130),
                SetEntry(base, 22, 115),
                SetEntry(base, 20, 105),
                SetEntry(base, 18, 95)
            )
            1 -> listOf(
                SetEntry(24, 20, 105),
                SetEntry(24, 20, 105),
                SetEntry(24, 18, 95),
                SetEntry(24, 16, 85)
            )
            else -> listOf(
                SetEntry(16, 30, 150),
                SetEntry(16, 30, 150),
                SetEntry(16, 25, 125)
            )
        }
        val snatchSets = when (index) {
            0 -> listOf(SetEntry(base, 20, 100), SetEntry(base, 18, 90), SetEntry(base, 15, 75))
            1 -> listOf(SetEntry(24, 18, 90), SetEntry(24, 16, 80), SetEntry(24, 14, 70))
            else -> listOf(SetEntry(16, 25, 125), SetEntry(16, 22, 110))
        }

        val sections = buildList {
            add(Section(SectionType.WARMUP, listOf(SetEntry(16, 20, 60), SetEntry(16, 20, 60))))
            add(Section(SectionType.JERK, jerkSets))
            if (index != 1) {
                add(Section(SectionType.SNATCH, snatchSets))
            } else {
                add(
                    Section(
                        SectionType.LONG_CYCLE,
                        listOf(SetEntry(24, 12, 90), SetEntry(24, 10, 75), SetEntry(24, 8, 60))
                    )
                )
            }
            add(
                Section(
                    SectionType.SWINGS,
                    listOf(SetEntry(24, 20, 60), SetEntry(24, 20, 60))
                )
            )
            add(Section(SectionType.GPP, listOf(SetEntry(0, 40, 120), SetEntry(0, 30, 90))))
            add(Section(SectionType.COOLDOWN, listOf(SetEntry(16, 20, 90))))
        }

        return Workout(
            date = date,
            sections = sections,
            note = when (index) {
                0 -> "Толчок по схеме 2×24/25+22+20+18"
                1 -> "ДЦ и махи, средняя нагрузка"
                else -> "Лёгкая техника, рывок"
            },
            createdAt = date.toEpochDay() * 86_400_000L + index * 3_600_000L
        )
    }

    private data class DemoSample(
        val daysAgo: Long,
        val weightKg: Double,
        val waistCm: Double,
        val chestCm: Double,
        val hipsCm: Double,
        val armCm: Double,
        val thighCm: Double,
        val note: String
    )
}
