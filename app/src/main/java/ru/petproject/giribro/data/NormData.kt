package ru.petproject.giribro.data

/*
 * Разрядные нормативы по гиревому спорту.
 *
 * Данные ЕВСК: 2026–2029 (приказ Минспорта России №299 от 03.04.2026) и
 * 2022–2025 (приказ №6 от 11.01.2022). Это локальная копия таблиц, без сети.
 * Источник: https://giriapp.ru/norm/
 */

/** Пол спортсмена в таблице нормативов. */
enum class NormGender(val label: String) {
    MALE("Мужчины"),
    FEMALE("Женщины")
}

/** Дисциплина, для которой действует таблица. */
enum class NormDiscipline(val label: String) {
    JERK("Толчок"),
    LONG_CYCLE("Толчок ДЦ"),
    SNATCH("Рывок"),
    BIATHLON("Двоеборье")
}

/** Группа разрядов: звания, взрослые разряды и юношеские. */
enum class NormTier {
    MASTERS,
    RANKS,
    YOUTH
}

/** Колонка таблицы нормативов. */
enum class NormRank(
    val label: String,
    val tableLabel: String,
    val tier: NormTier
) {
    MSMC("МСМК", "МСМК", NormTier.MASTERS),
    MS("МС", "МС", NormTier.MASTERS),
    KMS("КМС", "КМС", NormTier.MASTERS),
    R1("I разряд", "I", NormTier.RANKS),
    R2("II разряд", "II", NormTier.RANKS),
    R3("III разряд", "III", NormTier.RANKS),
    Y1("I юношеский", "Iю", NormTier.YOUTH),
    Y2("II юношеский", "IIю", NormTier.YOUTH),
    Y3("III юношеский", "IIIю", NormTier.YOUTH)
}

/** Одна весовая категория и нормативы в ней. null означает «норматива нет». */
data class NormCategory(
    val weight: String,
    val msmc: Int? = null,
    val ms: Int? = null,
    val kms: Int? = null,
    val r1: Int? = null,
    val r2: Int? = null,
    val r3: Int? = null,
    val y1: Int? = null,
    val y2: Int? = null,
    val y3: Int? = null
) {
    /** Числовая граница категории: «73+» → 73. */
    val weightValue: Int get() = weight.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
    val isPlus: Boolean get() = weight.endsWith("+")

    fun value(rank: NormRank): Int? = when (rank) {
        NormRank.MSMC -> msmc
        NormRank.MS -> ms
        NormRank.KMS -> kms
        NormRank.R1 -> r1
        NormRank.R2 -> r2
        NormRank.R3 -> r3
        NormRank.Y1 -> y1
        NormRank.Y2 -> y2
        NormRank.Y3 -> y3
    }

    val hasYouth: Boolean get() = y1 != null || y2 != null || y3 != null
}

/** Нормативы одного пола: вес гири для групп разрядов и строки весовых категорий. */
data class NormGenderData(
    val weightMasters: Int,
    val weightRanks: Int,
    val weightYouth: Int? = null,
    val categories: List<NormCategory>
) {
    val hasYouthValues: Boolean get() = categories.any { it.hasYouth }

    fun weightFor(tier: NormTier): Int? = when (tier) {
        NormTier.MASTERS -> weightMasters
        NormTier.RANKS -> weightRanks
        NormTier.YOUTH -> weightYouth
    }
}

/** Одна дисциплина в выбранном периоде ЕВСК. */
data class NormDisciplineData(
    val discipline: NormDiscipline,
    val name: String,
    val unit: String,
    val time: String,
    val male: NormGenderData,
    val female: NormGenderData
) {
    fun gender(gender: NormGender): NormGenderData =
        if (gender == NormGender.MALE) male else female
}

/** Период действия норм. */
data class NormPeriodData(
    val id: String,
    val label: String,
    val source: String,
    val hasYouth: Boolean,
    val disciplines: List<NormDisciplineData>
) {
    fun discipline(discipline: NormDiscipline): NormDisciplineData? =
        disciplines.firstOrNull { it.discipline == discipline }
}

/** Локальный справочник нормативов и правила выбора весовой категории. */
object NormBook {

    val periods: List<NormPeriodData> = listOf(
        NormPeriodData(
            id = "2026",
            label = "2026–2029",
            source = "ЕВСК 2026–2029 · Приказ Минспорта России №299 от 03.04.2026",
            hasYouth = true,
            disciplines = listOf(
                NormDisciplineData(
                    discipline = NormDiscipline.JERK,
                    name = "Толчок",
                    unit = "очков",
                    time = "10 мин",
                    male = NormGenderData(
                        weightMasters = 32,
                        weightRanks = 24,
                        weightYouth = 16,
                        categories = listOf(
                            NormCategory(weight = "48", y1 = 80, y2 = 70, y3 = 60),
                            NormCategory(weight = "53", y1 = 85, y2 = 75, y3 = 65),
                            NormCategory(weight = "58", r1 = 75, r2 = 65, r3 = 55, y1 = 90, y2 = 80, y3 = 70),
                            NormCategory(weight = "63", msmc = 95, ms = 78, kms = 55, r1 = 85, r2 = 75, r3 = 60, y1 = 100, y2 = 90, y3 = 80),
                            NormCategory(weight = "68", msmc = 105, ms = 92, kms = 70, r1 = 90, r2 = 80, r3 = 65, y1 = 105, y2 = 95, y3 = 85),
                            NormCategory(weight = "73", msmc = 117, ms = 105, kms = 81, r1 = 95, r2 = 85, r3 = 70, y1 = 110, y2 = 100, y3 = 90),
                            NormCategory(weight = "73+", r1 = 100, r2 = 90, r3 = 75, y1 = 120, y2 = 105, y3 = 95),
                            NormCategory(weight = "78", msmc = 125, ms = 112, kms = 90, r1 = 105, r2 = 95, r3 = 80),
                            NormCategory(weight = "85", msmc = 135, ms = 120, kms = 97, r1 = 110, r2 = 100, r3 = 85),
                            NormCategory(weight = "85+", r1 = 115, r2 = 105, r3 = 90),
                            NormCategory(weight = "95", msmc = 141, ms = 130, kms = 102, r1 = 120, r2 = 110, r3 = 95),
                            NormCategory(weight = "95+", msmc = 146, ms = 137, kms = 109, r1 = 130, r2 = 115, r3 = 100)
                        )
                    ),
                    female = NormGenderData(
                        weightMasters = 24,
                        weightRanks = 16,
                        weightYouth = 16,
                        categories = listOf(
                            NormCategory(weight = "48", r1 = 48, r2 = 40, r3 = 30, y1 = 22, y2 = 18, y3 = 15),
                            NormCategory(weight = "53", r1 = 53, r2 = 45, r3 = 35, y1 = 25, y2 = 20, y3 = 18),
                            NormCategory(weight = "58", msmc = 60, ms = 50, kms = 40, r1 = 60, r2 = 50, r3 = 40, y1 = 30, y2 = 25, y3 = 20),
                            NormCategory(weight = "58+", r1 = 65, r2 = 55, r3 = 45, y1 = 35, y2 = 30, y3 = 25),
                            NormCategory(weight = "63", msmc = 68, ms = 57, kms = 45, r1 = 69, r2 = 58, r3 = 48, y1 = 40, y2 = 35, y3 = 30),
                            NormCategory(weight = "63+", r1 = 73, r2 = 65, r3 = 53, y1 = 45, y2 = 38, y3 = 33),
                            NormCategory(weight = "68", msmc = 75, ms = 65, kms = 50, r1 = 76, r2 = 68, r3 = 60),
                            NormCategory(weight = "68+", msmc = 81, ms = 72, kms = 55, r1 = 80, r2 = 70, r3 = 62)
                        )
                    )
                ),
                NormDisciplineData(
                    discipline = NormDiscipline.LONG_CYCLE,
                    name = "Толчок ДЦ",
                    unit = "подъёмов",
                    time = "10 мин",
                    male = NormGenderData(
                        weightMasters = 32,
                        weightRanks = 24,
                        weightYouth = 16,
                        categories = listOf(
                            NormCategory(weight = "48", y1 = 40, y2 = 35, y3 = 30),
                            NormCategory(weight = "53", y1 = 48, y2 = 42, y3 = 36),
                            NormCategory(weight = "58", r1 = 60, r2 = 50, r3 = 40, y1 = 55, y2 = 48, y3 = 40),
                            NormCategory(weight = "63", msmc = 59, ms = 44, kms = 35, r1 = 65, r2 = 54, r3 = 43, y1 = 61, y2 = 51, y3 = 42),
                            NormCategory(weight = "68", msmc = 72, ms = 56, kms = 45, r1 = 70, r2 = 58, r3 = 46, y1 = 66, y2 = 56, y3 = 46),
                            NormCategory(weight = "73", msmc = 76, ms = 62, kms = 48, r1 = 74, r2 = 62, r3 = 50, y1 = 71, y2 = 61, y3 = 51),
                            NormCategory(weight = "73+", r1 = 78, r2 = 66, r3 = 55, y1 = 74, y2 = 64, y3 = 54),
                            NormCategory(weight = "78", msmc = 79, ms = 66, kms = 52, r1 = 82, r2 = 69, r3 = 57),
                            NormCategory(weight = "85", msmc = 82, ms = 69, kms = 55, r1 = 85, r2 = 73, r3 = 60),
                            NormCategory(weight = "85+", r1 = 90, r2 = 75, r3 = 62),
                            NormCategory(weight = "95", msmc = 84, ms = 72, kms = 57, r1 = 90, r2 = 79, r3 = 65),
                            NormCategory(weight = "95+", msmc = 88, ms = 75, kms = 62, r1 = 94, r2 = 82, r3 = 70)
                        )
                    ),
                    female = NormGenderData(
                        weightMasters = 24,
                        weightRanks = 16,
                        weightYouth = 16,
                        categories = listOf(
                            NormCategory(weight = "48", r1 = 45, r2 = 35, r3 = 25, y1 = 20, y2 = 17, y3 = 14),
                            NormCategory(weight = "53", r1 = 50, r2 = 42, r3 = 35, y1 = 27, y2 = 23, y3 = 18),
                            NormCategory(weight = "58", msmc = 50, ms = 40, kms = 30, r1 = 55, r2 = 50, r3 = 40, y1 = 30, y2 = 25, y3 = 20),
                            NormCategory(weight = "58+", r1 = 60, r2 = 52, r3 = 43, y1 = 35, y2 = 30, y3 = 25),
                            NormCategory(weight = "63", msmc = 59, ms = 44, kms = 35, r1 = 64, r2 = 55, r3 = 46, y1 = 40, y2 = 35, y3 = 30),
                            NormCategory(weight = "63+", r1 = 66, r2 = 57, r3 = 48, y1 = 45, y2 = 38, y3 = 33),
                            NormCategory(weight = "68", msmc = 62, ms = 52, kms = 40, r1 = 68, r2 = 58, r3 = 49),
                            NormCategory(weight = "68+", msmc = 72, ms = 56, kms = 45, r1 = 70, r2 = 60, r3 = 53)
                        )
                    )
                ),
                NormDisciplineData(
                    discipline = NormDiscipline.SNATCH,
                    name = "Рывок",
                    unit = "подъёмов",
                    time = "10 мин",
                    male = NormGenderData(
                        weightMasters = 32,
                        weightRanks = 24,
                        weightYouth = 16,
                        categories = listOf(
                            NormCategory(weight = "48", y1 = 100, y2 = 80, y3 = 60),
                            NormCategory(weight = "53", y1 = 110, y2 = 85, y3 = 65),
                            NormCategory(weight = "58", r1 = 100, r2 = 80, r3 = 60, y1 = 120, y2 = 90, y3 = 70),
                            NormCategory(weight = "63", msmc = 140, ms = 115, kms = 80, r1 = 110, r2 = 90, r3 = 70, y1 = 130, y2 = 100, y3 = 80),
                            NormCategory(weight = "68", msmc = 155, ms = 130, kms = 90, r1 = 120, r2 = 100, r3 = 90, y1 = 140, y2 = 110, y3 = 90),
                            NormCategory(weight = "73", msmc = 170, ms = 145, kms = 100, r1 = 130, r2 = 110, r3 = 90, y1 = 150, y2 = 120, y3 = 100),
                            NormCategory(weight = "73+", r1 = 140, r2 = 115, r3 = 100, y1 = 160, y2 = 130, y3 = 110),
                            NormCategory(weight = "78", msmc = 180, ms = 150, kms = 105, r1 = 150, r2 = 120, r3 = 105),
                            NormCategory(weight = "85", msmc = 185, ms = 160, kms = 110, r1 = 160, r2 = 130, r3 = 110),
                            NormCategory(weight = "85+", r1 = 170, r2 = 140, r3 = 115),
                            NormCategory(weight = "95", msmc = 190, ms = 165, kms = 115, r1 = 180, r2 = 150, r3 = 120),
                            NormCategory(weight = "95+", msmc = 195, ms = 170, kms = 120, r1 = 190, r2 = 155, r3 = 130)
                        )
                    ),
                    female = NormGenderData(
                        weightMasters = 24,
                        weightRanks = 16,
                        weightYouth = 16,
                        categories = listOf(
                            NormCategory(weight = "48", r1 = 80, r2 = 60, r3 = 45, y1 = 43, y2 = 33, y3 = 23),
                            NormCategory(weight = "53", r1 = 90, r2 = 70, r3 = 50, y1 = 45, y2 = 35, y3 = 25),
                            NormCategory(weight = "58", msmc = 146, ms = 120, kms = 66, r1 = 100, r2 = 80, r3 = 60, y1 = 55, y2 = 45, y3 = 35),
                            NormCategory(weight = "58+", r1 = 110, r2 = 90, r3 = 70, y1 = 65, y2 = 55, y3 = 45),
                            NormCategory(weight = "63", msmc = 159, ms = 126, kms = 73, r1 = 115, r2 = 95, r3 = 75, y1 = 70, y2 = 60, y3 = 50),
                            NormCategory(weight = "63+", r1 = 120, r2 = 100, r3 = 80, y1 = 75, y2 = 65, y3 = 55),
                            NormCategory(weight = "68", msmc = 167, ms = 135, kms = 79, r1 = 125, r2 = 115, r3 = 85),
                            NormCategory(weight = "68+", msmc = 181, ms = 146, kms = 86, r1 = 130, r2 = 105, r3 = 90)
                        )
                    )
                )
            )
        ),
        NormPeriodData(
            id = "2022",
            label = "2022–2025",
            source = "ЕВСК 2022–2025 · Приказ Минспорта России №6 от 11.01.2022",
            hasYouth = false,
            disciplines = listOf(
                NormDisciplineData(
                    discipline = NormDiscipline.BIATHLON,
                    name = "Двоеборье",
                    unit = "очков",
                    time = "10 мин",
                    male = NormGenderData(
                        weightMasters = 32,
                        weightRanks = 24,
                        categories = listOf(
                            NormCategory(weight = "58", r1 = 100, r2 = 80, r3 = 60),
                            NormCategory(weight = "63", msmc = 192, ms = 126, kms = 75, r1 = 110, r2 = 90, r3 = 70),
                            NormCategory(weight = "68", msmc = 210, ms = 146, kms = 83, r1 = 120, r2 = 95, r3 = 75),
                            NormCategory(weight = "73", msmc = 222, ms = 162, kms = 95, r1 = 130, r2 = 105, r3 = 80),
                            NormCategory(weight = "73+", r1 = 140, r2 = 110, r3 = 85),
                            NormCategory(weight = "78", r1 = 145, r2 = 115, r3 = 90),
                            NormCategory(weight = "85", msmc = 234, ms = 178, kms = 117, r1 = 150, r2 = 120, r3 = 95),
                            NormCategory(weight = "85+", r1 = 155, r2 = 125, r3 = 100),
                            NormCategory(weight = "95", msmc = 240, ms = 183, kms = 120, r1 = 160, r2 = 130, r3 = 110),
                            NormCategory(weight = "95+", msmc = 246, ms = 190, kms = 126, r1 = 170, r2 = 140, r3 = 120)
                        )
                    ),
                    female = NormGenderData(
                        weightMasters = 24,
                        weightRanks = 16,
                        categories = listOf(
                            NormCategory(weight = "48", r1 = 80, r2 = 60, r3 = 45),
                            NormCategory(weight = "53", r1 = 90, r2 = 70, r3 = 50),
                            NormCategory(weight = "58", r1 = 100, r2 = 80, r3 = 60),
                            NormCategory(weight = "58+", r1 = 110, r2 = 90, r3 = 70),
                            NormCategory(weight = "63", r1 = 115, r2 = 95, r3 = 75),
                            NormCategory(weight = "63+", r1 = 125, r2 = 100, r3 = 85),
                            NormCategory(weight = "68", r1 = 125, r2 = 115, r3 = 85),
                            NormCategory(weight = "68+", r1 = 130, r2 = 105, r3 = 90)
                        )
                    )
                ),
                NormDisciplineData(
                    discipline = NormDiscipline.LONG_CYCLE,
                    name = "Толчок ДЦ",
                    unit = "подъёмов",
                    time = "10 мин",
                    male = NormGenderData(
                        weightMasters = 32,
                        weightRanks = 24,
                        categories = listOf(
                            NormCategory(weight = "58", r1 = 60, r2 = 50, r3 = 40),
                            NormCategory(weight = "63", msmc = 59, ms = 44, kms = 35, r1 = 65, r2 = 54, r3 = 43),
                            NormCategory(weight = "68", msmc = 72, ms = 56, kms = 45, r1 = 70, r2 = 58, r3 = 46),
                            NormCategory(weight = "73", msmc = 76, ms = 62, kms = 48, r1 = 74, r2 = 62, r3 = 50),
                            NormCategory(weight = "73+", r1 = 78, r2 = 66, r3 = 55),
                            NormCategory(weight = "78", msmc = 79, ms = 66, kms = 52, r1 = 82, r2 = 69, r3 = 57),
                            NormCategory(weight = "85", msmc = 82, ms = 69, kms = 55, r1 = 85, r2 = 73, r3 = 60),
                            NormCategory(weight = "85+", r1 = 90, r2 = 75, r3 = 62),
                            NormCategory(weight = "95", msmc = 84, ms = 72, kms = 57, r1 = 90, r2 = 79, r3 = 65),
                            NormCategory(weight = "95+", msmc = 88, ms = 75, kms = 62, r1 = 94, r2 = 82, r3 = 70)
                        )
                    ),
                    female = NormGenderData(
                        weightMasters = 24,
                        weightRanks = 16,
                        categories = listOf(
                            NormCategory(weight = "58", msmc = 50, ms = 40, kms = 30, r1 = 60, r2 = 52, r3 = 43),
                            NormCategory(weight = "58+", r1 = 64, r2 = 55, r3 = 46),
                            NormCategory(weight = "63", msmc = 59, ms = 44, kms = 35, r1 = 68, r2 = 58, r3 = 49),
                            NormCategory(weight = "63+", r1 = 68, r2 = 58, r3 = 49),
                            NormCategory(weight = "68", msmc = 62, ms = 52, kms = 40, r1 = 68, r2 = 58, r3 = 49),
                            NormCategory(weight = "68+", msmc = 72, ms = 56, kms = 45, r1 = 70, r2 = 60, r3 = 50)
                        )
                    )
                ),
                NormDisciplineData(
                    discipline = NormDiscipline.SNATCH,
                    name = "Рывок",
                    unit = "подъёмов",
                    time = "10 мин",
                    male = NormGenderData(
                        weightMasters = 32,
                        weightRanks = 24,
                        categories = listOf(
                            NormCategory(weight = "63"),
                            NormCategory(weight = "68"),
                            NormCategory(weight = "73"),
                            NormCategory(weight = "85"),
                            NormCategory(weight = "95"),
                            NormCategory(weight = "95+")
                        )
                    ),
                    female = NormGenderData(
                        weightMasters = 24,
                        weightRanks = 16,
                        categories = listOf(
                            NormCategory(weight = "48", r1 = 80, r2 = 60, r3 = 45),
                            NormCategory(weight = "53", r1 = 90, r2 = 70, r3 = 50),
                            NormCategory(weight = "58", msmc = 146, ms = 120, kms = 66, r1 = 100, r2 = 80, r3 = 60),
                            NormCategory(weight = "58+", r1 = 110, r2 = 90, r3 = 70),
                            NormCategory(weight = "63", msmc = 159, ms = 126, kms = 73, r1 = 115, r2 = 95, r3 = 75),
                            NormCategory(weight = "63+", r1 = 125, r2 = 100, r3 = 85),
                            NormCategory(weight = "68", msmc = 167, ms = 135, kms = 79, r1 = 125, r2 = 115, r3 = 85),
                            NormCategory(weight = "68+", msmc = 181, ms = 146, kms = 86, r1 = 130, r2 = 105, r3 = 90)
                        )
                    )
                )
            )
        ),
    )

    fun period(id: String): NormPeriodData =
        periods.firstOrNull { it.id == id } ?: periods.first()

    /**
     * Повторяет логику сайта: сначала ищется ближайшая обычная категория
     * (вес не больше границы), затем — старшая «+»-категория.
     */
    fun weightCategory(weight: Double, data: NormGenderData): NormCategory? {
        if (!weight.isFinite() || weight <= 0.0 || data.categories.isEmpty()) return null

        val plain = data.categories
            .filterNot { it.isPlus }
            .sortedBy { it.weightValue }
        plain.firstOrNull { weight <= it.weightValue }?.let { return it }

        val plus = data.categories
            .filter { it.isPlus }
            .sortedByDescending { it.weightValue }
        return plus.firstOrNull() ?: plain.lastOrNull()
    }

    /** Вес можно вводить с точкой или запятой. */
    fun parseWeight(text: String): Double? =
        text.trim()
            .replace(',', '.')
            .toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
}
