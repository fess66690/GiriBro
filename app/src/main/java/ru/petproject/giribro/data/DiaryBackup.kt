package ru.petproject.giribro.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Содержимое резервной копии дневника. */
data class DiaryBackup(
    val exportedAt: Long,
    val workouts: List<Workout>,
    val settings: Settings?,
    val bodyMeasurements: List<BodyMeasurement> = emptyList()
)

/** Формат файла резервной копии: версионированный JSON без привязки к Room. */
object DiaryBackupJson {

    const val FORMAT = "giribro-diary"

    /** v2 добавила замеры тела; копии v1 читаются как дневник без замеров. */
    const val VERSION = 2
    const val MIME_TYPE = "application/json"
    const val MAX_FILE_BYTES = 10 * 1024 * 1024

    fun encode(
        workouts: List<Workout>,
        settings: Settings,
        bodyMeasurements: List<BodyMeasurement> = emptyList(),
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

        val measurements = JSONArray()
        bodyMeasurements
            .sortedWith(
                compareByDescending<BodyMeasurement> { it.date }
                    .thenByDescending { it.createdAt }
            )
            .forEach { measurement ->
                measurements.put(
                    JSONObject()
                        .put("date", measurement.date.toString())
                        .put("weightKg", measurement.weightKg)
                        .put("createdAt", measurement.createdAt)
                        .put("note", measurement.note)
                        .apply {
                            measurement.waistCm?.let { put("waistCm", it) }
                            measurement.chestCm?.let { put("chestCm", it) }
                            measurement.hipsCm?.let { put("hipsCm", it) }
                            measurement.armCm?.let { put("armCm", it) }
                            measurement.thighCm?.let { put("thighCm", it) }
                        }
                )
            }

        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
            .put("workouts", items)
            .put("bodyMeasurements", measurements)
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
     * Читает копию целиком и проверяет каждую тренировку и каждый замер до того,
     * как приложение заменит текущие данные. При любой проблеме бросает
     * [BackupFormatException].
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

        val measurementItems = when {
            !root.has("bodyMeasurements") || root.isNull("bodyMeasurements") -> JSONArray()
            else -> root.optJSONArray("bodyMeasurements")
                ?: throw BackupFormatException("В копии повреждён список замеров.")
        }
        val bodyMeasurements = buildList {
            for (i in 0 until measurementItems.length()) {
                val item = measurementItems.optJSONObject(i)
                    ?: throw BackupFormatException("В копии повреждён замер №${i + 1}.")

                val date = runCatching {
                    LocalDate.parse(item.optString("date"))
                }.getOrElse {
                    throw BackupFormatException(
                        "В замере №${i + 1} указана некорректная дата."
                    )
                }

                val measurement = BodyMeasurement(
                    date = date,
                    weightKg = item.optDouble("weightKg", Double.NaN),
                    waistCm = item.optionalSize("waistCm", date),
                    chestCm = item.optionalSize("chestCm", date),
                    hipsCm = item.optionalSize("hipsCm", date),
                    armCm = item.optionalSize("armCm", date),
                    thighCm = item.optionalSize("thighCm", date),
                    note = item.optString("note", ""),
                    createdAt = item.optLong("createdAt", 0L)
                )
                BodyMeasurementValidator.validate(measurement)?.let { problem ->
                    throw BackupFormatException("Замер от $date: $problem")
                }
                add(measurement)
            }
        }

        return DiaryBackup(
            exportedAt = root.optLong("exportedAt", 0L),
            workouts = workouts,
            settings = root.optJSONObject("settings")?.let(::decodeSettings),
            bodyMeasurements = bodyMeasurements
        )
    }

    fun suggestedFileName(today: LocalDate = LocalDate.now()): String =
        "giribro-backup-$today.json"

    private fun JSONObject.optionalSize(key: String, date: LocalDate): Double? {
        if (!has(key) || isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        if (!value.isFinite()) {
            throw BackupFormatException("В замере от $date указано некорректное значение обхвата.")
        }
        return value
    }

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
