package ru.petproject.giribro.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Содержимое резервной копии дневника. */
data class DiaryBackup(
    val exportedAt: Long,
    val workouts: List<Workout>,
    val settings: Settings?
)

/** Формат файла резервной копии: версионированный JSON без привязки к Room. */
object DiaryBackupJson {

    const val FORMAT = "giribro-diary"
    const val VERSION = 1
    const val MIME_TYPE = "application/json"
    const val MAX_FILE_BYTES = 10 * 1024 * 1024

    fun encode(
        workouts: List<Workout>,
        settings: Settings,
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val items = JSONArray()
        workouts
            .sortedWith(compareByDescending<Workout> { it.date }.thenByDescending { it.createdAt })
            .forEach { workout ->
                items.put(
                    JSONObject()
                        .put("date", workout.date.toString())
                        .put("createdAt", workout.createdAt)
                        .put("note", workout.note)
                        .put("sections", JSONArray(WorkoutJson.encode(workout.sections)))
                )
            }

        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
            .put("workouts", items)
            .put(
                "settings",
                JSONObject()
                    .put("themeMode", settings.themeMode.name)
                    .put("weeklyGoal", settings.weeklyGoal)
                    .put("defaultWeight", settings.defaultWeight)
                    .put("statsPeriod", settings.statsPeriod.name)
            )
            .toString(2)
    }

    /**
     * Читает копию целиком и проверяет каждую тренировку до того, как приложение
     * заменит текущие данные. При любой проблеме бросает [BackupFormatException].
     */
    fun decode(raw: String): DiaryBackup {
        val text = raw.removePrefix("\uFEFF").trim()
        if (text.isEmpty()) throw BackupFormatException("Файл резервной копии пуст.")

        val root = runCatching { JSONObject(text) }.getOrElse {
            throw BackupFormatException("Файл не похож на резервную копию ГириБро.")
        }

        if (root.optString("format") != FORMAT) {
            throw BackupFormatException("Это не резервная копия ГириБро.")
        }

        val version = root.optInt("version", -1)
        when {
            version <= 0 -> throw BackupFormatException("В копии не указана версия формата.")
            version > VERSION -> throw BackupFormatException(
                "Копия создана более новой версией приложения. Обновите ГириБро."
            )
        }

        val items = root.optJSONArray("workouts")
            ?: throw BackupFormatException("В копии нет списка тренировок.")

        val workouts = buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i)
                    ?: throw BackupFormatException("В копии повреждена тренировка №${i + 1}.")

                val date = runCatching {
                    LocalDate.parse(item.optString("date"))
                }.getOrElse {
                    throw BackupFormatException(
                        "В тренировке №${i + 1} указана некорректная дата."
                    )
                }

                val sectionsJson = item.optJSONArray("sections")
                    ?: throw BackupFormatException("В тренировке от $date нет упражнений.")
                val sections = runCatching {
                    WorkoutJson.decode(sectionsJson.toString(), strict = true)
                }.getOrElse { error ->
                    throw BackupFormatException(
                        "Тренировка от $date повреждена: ${error.message ?: "неизвестная ошибка"}."
                    )
                }

                val workout = Workout(
                    date = date,
                    sections = sections,
                    note = item.optString("note", ""),
                    createdAt = item.optLong("createdAt", 0L)
                )
                // Длину заметки при импорте не ограничиваем: копия могла быть
                // сделана старой версией, где лимит был другим.
                WorkoutValidator.validate(workout.sections, note = "")?.let { problem ->
                    throw BackupFormatException("Тренировка от $date: $problem")
                }
                add(workout)
            }
        }

        return DiaryBackup(
            exportedAt = root.optLong("exportedAt", 0L),
            workouts = workouts,
            settings = root.optJSONObject("settings")?.let(::decodeSettings)
        )
    }

    fun suggestedFileName(today: LocalDate = LocalDate.now()): String =
        "giribro-backup-$today.json"

    private fun decodeSettings(obj: JSONObject): Settings = Settings(
        themeMode = enumOrDefault(obj.optString("themeMode"), ThemeMode.DARK),
        weeklyGoal = obj.optInt("weeklyGoal", 3).coerceIn(1, 14),
        defaultWeight = obj.optInt("defaultWeight", 24).coerceIn(4, 48),
        statsPeriod = enumOrDefault(obj.optString("statsPeriod"), StatsPeriod.MONTH)
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String, default: T): T =
        runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
}

class BackupFormatException(message: String) : IllegalArgumentException(message)
