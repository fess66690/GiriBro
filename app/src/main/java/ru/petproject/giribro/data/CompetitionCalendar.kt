package ru.petproject.giribro.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RU = Locale("ru")
private val dayMonthShort = DateTimeFormatter.ofPattern("d MMM", RU)
private val monthShort = DateTimeFormatter.ofPattern("MMM", RU)
private val monthYear = DateTimeFormatter.ofPattern("LLLL yyyy", RU)

/** Тип соревнования: используется для бейджа и цвета в календаре. */
enum class CompetitionKind(val label: String) {
    CHAMPIONSHIP("Чемпионат"),
    CHAMPIONSHIP_YOUTH("Первенство"),
    CUP("Кубок"),
    ALL_RUSSIAN("Всероссийские"),
    INTERNATIONAL("Международные"),
    SPARTAKIADA("Спартакиада"),
    STUDENT("Студенческие"),
    REGIONAL("Межрегиональные")
}

/** Одно событие календаря ВФГС. */
data class CompetitionEvent(
    val id: Int,
    val title: String,
    val start: LocalDate,
    val end: LocalDate,
    val place: String,
    val kind: CompetitionKind,
    val note: String? = null
) {
    /** Например: «13–16 нояб.» или «29 апр. — 12 мая». */
    fun dateLabel(): String = when {
        start == end -> start.format(dayMonthShort)
        start.year == end.year && start.month == end.month ->
            "${start.dayOfMonth}–${end.dayOfMonth} ${end.format(monthShort)}"
        else -> "${start.format(dayMonthShort)} — ${end.format(dayMonthShort)}"
    }

    /** Заголовок группы месяцев: «Ноябрь 2026». */
    fun monthLabel(): String =
        start.format(monthYear).replaceFirstChar { it.uppercaseChar() }
}

/**
 * Календарь соревнований ВФГС на 2026 год.
 *
 * В список включены спортивные соревнования из официального календарного
 * плана. УТМ (учебно-тренировочные мероприятия) не входят в календарь
 * соревнований и здесь не показываются.
 *
 * Источник: https://vfgs.ru/assets/files/calendar/Kalendar_VFGS_-red_17_03.pdf
 */
object CompetitionCalendar {

    const val YEAR = 2026
    const val SOURCE = "Календарный план ВФГС на 2026 год · редакция 17.03.2026"

    val events: List<CompetitionEvent> = listOf(
        CompetitionEvent(
            45, "Межрегиональные соревнования",
            LocalDate.of(2026, 2, 6), LocalDate.of(2026, 2, 8),
            "Челябинск, Челябинская область", CompetitionKind.REGIONAL
        ),
        CompetitionEvent(
            17, "Первенство России (юноши, девушки 14–18 лет)",
            LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 23),
            "Пермь, Пермский край", CompetitionKind.CHAMPIONSHIP_YOUTH
        ),
        CompetitionEvent(
            18, "Всероссийские соревнования памяти Б. Смагулова, Н. Шатова, И. Мартыневича",
            LocalDate.of(2026, 2, 26), LocalDate.of(2026, 3, 2),
            "Омск, Омская область", CompetitionKind.ALL_RUSSIAN
        ),
        CompetitionEvent(
            19, "Всероссийские соревнования (отбор на юношескую Спартакиаду)",
            LocalDate.of(2026, 3, 6), LocalDate.of(2026, 3, 9),
            "Самара, Самарская область", CompetitionKind.ALL_RUSSIAN
        ),
        CompetitionEvent(
            20, "Первенство России (юниоры, юниорки 19–23 года)",
            LocalDate.of(2026, 3, 14), LocalDate.of(2026, 3, 18),
            "Орёл, Орловская область", CompetitionKind.CHAMPIONSHIP_YOUTH
        ),
        CompetitionEvent(
            27, "Международные соревнования «Мемориал Н. Жеребцова»",
            LocalDate.of(2026, 3, 26), LocalDate.of(2026, 3, 30),
            "Казань, Республика Татарстан", CompetitionKind.INTERNATIONAL
        ),
        CompetitionEvent(
            28, "Всероссийские соревнования «Памяти кавалера ордена Мужества старшего лейтенанта Прокопенкова И.В.»",
            LocalDate.of(2026, 3, 30), LocalDate.of(2026, 4, 3),
            "Улан-Удэ, Республика Бурятия", CompetitionKind.ALL_RUSSIAN
        ),
        CompetitionEvent(
            21, "Чемпионат ПФО",
            LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 5),
            "Оренбург, Оренбургская область", CompetitionKind.CHAMPIONSHIP
        ),
        CompetitionEvent(
            24, "Чемпионат СФО, ДФО",
            LocalDate.of(2026, 4, 8), LocalDate.of(2026, 4, 12),
            "Белово, Кемеровская область — Кузбасс", CompetitionKind.CHAMPIONSHIP
        ),
        CompetitionEvent(
            25, "Чемпионат УФО",
            LocalDate.of(2026, 4, 8), LocalDate.of(2026, 4, 12),
            "Губкинский, ЯНАО", CompetitionKind.CHAMPIONSHIP
        ),
        CompetitionEvent(
            23, "Чемпионат СЗФО",
            LocalDate.of(2026, 4, 9), LocalDate.of(2026, 4, 13),
            "Санкт-Петербург", CompetitionKind.CHAMPIONSHIP
        ),
        CompetitionEvent(
            26, "Чемпионат ЮФО, СКФО",
            LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 14),
            "ст. Ленинградская, Краснодарский край", CompetitionKind.CHAMPIONSHIP
        ),
        CompetitionEvent(
            22, "Чемпионат ЦФО",
            LocalDate.of(2026, 4, 13), LocalDate.of(2026, 4, 16),
            "Смоленск, Смоленская область", CompetitionKind.CHAMPIONSHIP
        ),
        CompetitionEvent(
            29, "Первенство мира (юноши, девушки 14–18 лет)",
            LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 24),
            "Бишкек, Киргизия", CompetitionKind.INTERNATIONAL
        ),
        CompetitionEvent(
            30, "Международные соревнования «Белые ночи»",
            LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 4),
            "Санкт-Петербург", CompetitionKind.INTERNATIONAL
        ),
        CompetitionEvent(
            32, "Международные соревнования «Чемпионат и первенство Азии»",
            LocalDate.of(2026, 5, 12), LocalDate.of(2026, 5, 17),
            "Казахстан", CompetitionKind.INTERNATIONAL
        ),
        CompetitionEvent(
            31, "Всероссийские соревнования памяти Данилова",
            LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 18),
            "Томск, Томская область", CompetitionKind.ALL_RUSSIAN
        ),
        CompetitionEvent(
            33, "Чемпионат России",
            LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 7),
            "Новосибирск, Новосибирская область", CompetitionKind.CHAMPIONSHIP
        ),
        CompetitionEvent(
            34, "Финал Спартакиады (юношеская)",
            LocalDate.of(2026, 8, 26), LocalDate.of(2026, 8, 28),
            "Сочи, Краснодарский край", CompetitionKind.SPARTAKIADA
        ),
        CompetitionEvent(
            35, "Международные соревнования «Иссык-Кульские спортивные игры»",
            LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 6),
            "Чолпон-Ата, Киргизия", CompetitionKind.INTERNATIONAL
        ),
        CompetitionEvent(
            36, "Кубок России",
            LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14),
            "Ростов-на-Дону, Ростовская область", CompetitionKind.CUP
        ),
        CompetitionEvent(
            39, "Чемпионат мира, первенство мира (юниоры, юниорки)",
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 6),
            "Индия", CompetitionKind.INTERNATIONAL
        ),
        CompetitionEvent(
            40, "Всероссийские соревнования на призы народной газеты «Хыпар»",
            LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 18),
            "Чебоксары, Чувашская Республика", CompetitionKind.ALL_RUSSIAN
        ),
        CompetitionEvent(
            37, "Всероссийские соревнования (юноши, девушки 14–16, 17–18 лет)",
            LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 26),
            "Рыбинск, Ярославская область", CompetitionKind.ALL_RUSSIAN
        ),
        CompetitionEvent(
            41, "Международные соревнования на призы ЗМС Леонова",
            LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 13),
            "Улан-Удэ, Республика Бурятия", CompetitionKind.INTERNATIONAL,
            note = "В ЕКП пока не включено"
        ),
        CompetitionEvent(
            42, "Всероссийские соревнования на призы ЗМС Леонова",
            LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 16),
            "Улан-Удэ, Республика Бурятия", CompetitionKind.ALL_RUSSIAN
        ),
        CompetitionEvent(
            43, "Всероссийские соревнования «Имени Героя Советского Союза, генерал-полковника Востротина В.А.»",
            LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20),
            "Москва", CompetitionKind.ALL_RUSSIAN
        ),
        CompetitionEvent(
            38, "Всероссийские соревнования среди студентов",
            LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 30),
            "Омск, Омская область", CompetitionKind.STUDENT
        ),
        CompetitionEvent(
            44, "Всероссийские соревнования «Кубок Губернатора Калужской области»",
            LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 6),
            "Калуга, Калужская область", CompetitionKind.CUP
        )
    ).sortedBy { it.start }

    fun upcoming(today: LocalDate = LocalDate.now()): List<CompetitionEvent> =
        events.filter { !it.end.isBefore(today) }

    fun past(today: LocalDate = LocalDate.now()): List<CompetitionEvent> =
        events.filter { it.end.isBefore(today) }

    fun nearest(today: LocalDate = LocalDate.now()): CompetitionEvent? =
        upcoming(today).minByOrNull { it.start }

    /** События по месяцам; месяцы идут по возрастанию даты. */
    fun byMonth(events: List<CompetitionEvent>): Map<YearMonth, List<CompetitionEvent>> =
        events.sortedBy { it.start }.groupBy { YearMonth.from(it.start) }
}
