package ru.petproject.giribro.data

/**
 * Разбор компактного текста для быстрого ввода тренировки.
 *
 * Формат строки:
 * `[упражнение] вес [кг] подходы x повторы [время] [отдых время]`
 *
 * Примеры:
 * - `Толчок 16 5x10`
 * - `Рывок 16 10+10+8 1:30`
 * - `Махи 24 3x20 отдых 1:00`
 * - `дц 24 5x10`
 *
 * Если упражнение указано отдельной строкой с двоеточием, следующие строки
 * продолжают его:
 * ```
 * Толчок:
 * 16 5x10
 * 16 5x8 1:30
 * ```
 */
object QuickWorkoutParser {

    const val MAX_LINES = 60
    const val MAX_SETS = 300

    data class Result(
        val sections: List<Section>,
        val errors: List<String>
    ) {
        val isValid: Boolean get() = errors.isEmpty() && sections.isNotEmpty()
        val setsCount: Int get() = sections.sumOf { it.sets.size }
    }

    private data class ParsedLine(
        val weightKg: Int,
        val reps: List<Int>,
        val durationSec: Int,
        val restSec: Int
    )

    private sealed interface LineResult {
        data class Success(val value: ParsedLine) : LineResult
        data class Failure(val message: String) : LineResult
    }

    private val sectionAliases: List<Pair<String, SectionType>> = listOf(
        "длинный цикл" to SectionType.LONG_CYCLE,
        "long cycle" to SectionType.LONG_CYCLE,
        "разминка" to SectionType.WARMUP,
        "заминка" to SectionType.COOLDOWN,
        "толчок" to SectionType.JERK,
        "рывок" to SectionType.SNATCH,
        "махи" to SectionType.SWINGS,
        "свинг" to SectionType.SWINGS,
        "дц" to SectionType.LONG_CYCLE,
        "мах" to SectionType.SWINGS,
        "офп" to SectionType.GPP
    ).sortedByDescending { it.first.length }

    fun parse(text: String): Result {
        val lines = text.lines()
            .mapIndexed { index, line -> index + 1 to line.trim() }
            .filter { (_, line) ->
                line.isNotEmpty() &&
                    !line.startsWith("#") &&
                    !line.startsWith("//")
            }

        if (lines.isEmpty()) {
            return Result(emptyList(), listOf("Введите хотя бы одну строку."))
        }
        if (lines.size > MAX_LINES) {
            return Result(emptyList(), listOf("Слишком много строк: максимум $MAX_LINES."))
        }

        val errors = mutableListOf<String>()
        val parsed = mutableListOf<Pair<SectionType, ParsedLine>>()
        var currentType: SectionType? = null

        lines.forEach { (lineNumber, rawLine) ->
            val original = rawLine.substringBefore(" #").substringBefore(" //").trim()
            if (original.isEmpty()) return@forEach

            var line = original
                .trimStart('-', '•', '·', '*')
                .trim()
                .let(::normalize)
            if (line.isEmpty()) return@forEach

            val alias = sectionAliases.firstOrNull { (name, _) ->
                line.startsWith(name, ignoreCase = true) &&
                    (line.length == name.length || !line[name.length].isLetterOrDigit())
            }
            if (alias != null) {
                currentType = alias.second
                line = line.substring(alias.first.length)
                    .trim()
                    .removePrefix(":")
                    .trim()
                if (line.isEmpty()) return@forEach
            }

            val type = currentType
            if (type == null) {
                errors += "Строка $lineNumber: укажите упражнение, например «Толчок 16 5x10»."
                return@forEach
            }

            when (val result = parseLine(line)) {
                is LineResult.Failure -> {
                    errors += "Строка $lineNumber: ${result.message}."
                }
                is LineResult.Success -> {
                    val value = result.value
                    val problem = value.reps.firstNotNullOfOrNull { reps ->
                        WorkoutValidator.validateSet(
                            SetEntry(
                                weightKg = value.weightKg,
                                reps = reps,
                                durationSec = value.durationSec,
                                restSec = value.restSec
                            )
                        )
                    }
                    if (problem != null) {
                        errors += "Строка $lineNumber: $problem."
                        return@forEach
                    }

                    val totalSets = parsed.sumOf { it.second.reps.size }
                    if (totalSets + value.reps.size > MAX_SETS) {
                        errors += "Слишком много подходов: максимум $MAX_SETS."
                        return@forEach
                    }
                    parsed += type to value
                }
            }
        }

        if (errors.isNotEmpty()) return Result(emptyList(), errors)
        if (parsed.isEmpty()) return Result(emptyList(), listOf("Не найдено ни одного подхода."))

        val grouped = linkedMapOf<SectionType, MutableList<SetEntry>>()
        parsed.forEach { (type, line) ->
            val sets = grouped.getOrPut(type) { mutableListOf() }
            line.reps.forEach { reps ->
                sets += SetEntry(
                    weightKg = line.weightKg,
                    reps = reps,
                    durationSec = line.durationSec,
                    restSec = line.restSec
                )
            }
        }

        return Result(
            sections = grouped.map { (type, sets) -> Section(type, sets) },
            errors = emptyList()
        )
    }

    private fun normalize(value: String): String = value
        .replace('–', '-')
        .replace('—', '-')
        .replace(Regex("(?i)\\s*(кг|kg)\\b"), "")
        .replace(Regex("(?i)\\s+по\\s+"), "x")
        .replace(Regex("(\\d)\\s*[хХ×xX*]\\s*(\\d)"), "$1x$2")
        .replace(Regex("\\s*\\+\\s*"), "+")
        .replace(Regex("\\s*:\\s*"), ":")
        .replace(Regex("(?i)(\\d+)\\s*(с|сек|секунд)\\b"), "$1с")
        .replace(Regex("(?i)(\\d+)\\s*м\\s*(\\d+)\\s*с?\\b"), "$1:$2")
        .replace(Regex("[()\\[\\]]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun parseLine(line: String): LineResult {
        val tokens = line.split(' ').filter { it.isNotEmpty() }
        if (tokens.size < 2) {
            return LineResult.Failure("нужны вес и схема подходов, например «16 5x10»")
        }

        val weight = tokens[0].toIntOrNull()
            ?: return LineResult.Failure("вес должен быть целым числом")
        val reps = parseScheme(tokens[1])
            ?: return LineResult.Failure("подходы укажите как «5x10» или «10+10+8»")

        var durationSec: Int? = null
        var restSec = SetEntry.DEFAULT_REST_SEC
        var index = 2

        while (index < tokens.size) {
            val token = tokens[index].lowercase()
            when {
                token == "отдых" -> {
                    val next = tokens.getOrNull(index + 1)
                        ?: return LineResult.Failure("после «отдых» укажите время")
                    restSec = parseTime(next)
                        ?: return LineResult.Failure("отдых укажите как «1:00» или «60с»")
                    index += 2
                }
                token.startsWith("отдых=") || token.startsWith("отдых:") -> {
                    val timeText = if (token.startsWith("отдых=")) {
                        token.substringAfter('=')
                    } else {
                        token.substringAfter(':')
                    }
                    restSec = parseTime(timeText)
                        ?: return LineResult.Failure("отдых укажите как «1:00» или «60с»")
                    index++
                }
                else -> {
                    if (durationSec != null) {
                        return LineResult.Failure("лишнее значение «${tokens[index]}»")
                    }
                    durationSec = parseTime(token)
                        ?: return LineResult.Failure("время укажите как «1:30» или «90с»")
                    index++
                }
            }
        }

        return LineResult.Success(
            ParsedLine(
                weightKg = weight,
                reps = reps,
                durationSec = durationSec ?: 0,
                restSec = restSec
            )
        )
    }

    private fun parseScheme(scheme: String): List<Int>? {
        val normalized = scheme.lowercase()
        val multiple = Regex("^(\\d{1,2})x(\\d{1,3})$").matchEntire(normalized)
        if (multiple != null) {
            val count = multiple.groupValues[1].toInt()
            val reps = multiple.groupValues[2].toInt()
            if (count !in 1..99) return null
            return List(count) { reps }
        }

        if (normalized.contains('+')) {
            val parts = normalized.split('+')
            if (parts.any { !it.matches(Regex("\\d{1,3}")) }) return null
            return parts.map { it.toInt() }
        }

        return normalized.toIntOrNull()?.let { listOf(it) }
    }

    private fun parseTime(value: String): Int? {
        val token = value.lowercase().removeSuffix("с")
        val parts = token.split(':')
        return when (parts.size) {
            1 -> token.toIntOrNull()?.takeIf { it >= 0 }
            2 -> {
                val minutes = parts[0].toIntOrNull() ?: return null
                val seconds = parts[1].toIntOrNull() ?: return null
                if (minutes < 0 || seconds !in 0..59) return null
                minutes * 60 + seconds
            }
            3 -> {
                val hours = parts[0].toIntOrNull() ?: return null
                val minutes = parts[1].toIntOrNull() ?: return null
                val seconds = parts[2].toIntOrNull() ?: return null
                if (hours < 0 || minutes !in 0..59 || seconds !in 0..59) return null
                hours * 3600 + minutes * 60 + seconds
            }
            else -> null
        }
    }
}
