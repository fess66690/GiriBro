package ru.petproject.giribro.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateEpochDay: Long,
    val createdAt: Long,
    val note: String,
    val sectionsJson: String
)

object WorkoutJson {

    fun encode(sections: List<Section>): String {
        val root = JSONArray()
        sections.forEach { section ->
            val sets = JSONArray()
            section.sets.forEach { entry ->
                val setJson = JSONObject()
                    .put("w", entry.weightKg)
                    .put("r", entry.reps)
                    .put("d", entry.durationSec)
                    // Отдых после конкретного подхода — так разные паузы сохраняются.
                    .put("rest", entry.restSec.coerceIn(0, SetEntry.MAX_REST_SEC))
                // Старые копии не должны разрастаться лишними полями.
                if (entry.captureMode != SetCaptureMode.NONE) {
                    setJson.put("cm", entry.captureMode.name)
                }
                if (entry.exerciseName.isNotBlank()) {
                    setJson.put(
                        "n",
                        entry.exerciseName.take(SetQuickValues.MAX_NAME_LENGTH)
                    )
                }
                sets.put(setJson)
            }
            root.put(
                JSONObject()
                    .put("type", section.type.name)
                    // Старое поле оставляем как запасной вариант для старых версий.
                    .put(
                        "rest",
                        section.sets.firstOrNull()?.restSec ?: SetEntry.DEFAULT_REST_SEC
                    )
                    .put("sets", sets)
            )
        }
        return root.toString()
    }

    /**
     * Разбирает JSON секций.
     *
     * [strict] нужен для импорта резервной копии: там нельзя молча терять
     * неизвестные упражнения или повреждённые подходы. Для обычного чтения из
     * Room режим остаётся мягким, чтобы одна плохая запись не роняла дневник.
     */
    fun decode(raw: String, strict: Boolean = false): List<Section> = runCatching {
        val root = JSONArray(raw)
        buildList {
            for (i in 0 until root.length()) {
                val obj = root.optJSONObject(i)
                    ?: if (strict) error("повреждено упражнение №${i + 1}") else continue
                val rawType = obj.optString("type")
                val type = runCatching {
                    SectionType.valueOf(rawType)
                }.getOrNull()
                    ?: if (strict) error("неизвестное упражнение «$rawType»") else continue

                val setsJson = obj.optJSONArray("sets")
                    ?: if (strict) error("у упражнения «${type.label}» нет подходов") else JSONArray()
                // В старых записях отдых хранился на упражнении, а не на подходе.
                val legacyRest = obj.optInt("rest", SetEntry.DEFAULT_REST_SEC)
                    .coerceIn(0, SetEntry.MAX_REST_SEC)
                val sets = buildList {
                    for (j in 0 until setsJson.length()) {
                        val set = setsJson.optJSONObject(j)
                            ?: if (strict) {
                                error("повреждён подход №${j + 1} в «${type.label}»")
                            } else {
                                continue
                            }
                        add(
                            SetEntry(
                                weightKg = set.optInt("w"),
                                reps = set.optInt("r"),
                                durationSec = set.optInt("d", 0),
                                restSec = set.optInt("rest", legacyRest)
                                    .coerceIn(0, SetEntry.MAX_REST_SEC),
                                captureMode = SetCaptureMode.fromName(set.optString("cm")),
                                exerciseName = set.optString("n")
                                    .take(SetQuickValues.MAX_NAME_LENGTH)
                            )
                        )
                    }
                }
                if (sets.isEmpty() && strict) {
                    error("у упражнения «${type.label}» нет подходов")
                }
                if (sets.isNotEmpty()) {
                    add(Section(type = type, sets = sets))
                }
            }
        }
    }.getOrElse { error ->
        if (strict) throw error else emptyList()
    }
}

fun WorkoutEntity.toModel(): Workout = Workout(
    id = id,
    date = java.time.LocalDate.ofEpochDay(dateEpochDay),
    sections = WorkoutJson.decode(sectionsJson),
    note = note,
    createdAt = createdAt
)

/** Безопасное преобразование для чтения из базы: одна повреждённая запись не роняет дневник. */
fun WorkoutEntity.toModelOrNull(): Workout? = runCatching { toModel() }.getOrNull()

fun Workout.toEntity(): WorkoutEntity = WorkoutEntity(
    id = id,
    dateEpochDay = date.toEpochDay(),
    createdAt = createdAt,
    note = note,
    sectionsJson = WorkoutJson.encode(sections)
)
