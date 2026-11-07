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
                sets.put(
                    JSONObject()
                        .put("w", entry.weightKg)
                        .put("r", entry.reps)
                        .put("d", entry.durationSec)
                        // Отдых после конкретного подхода — так разные паузы сохраняются.
                        .put("rest", entry.restSec.coerceIn(0, SetEntry.MAX_REST_SEC))
                )
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

    fun decode(raw: String): List<Section> = runCatching {
        val root = JSONArray(raw)
        buildList {
            for (i in 0 until root.length()) {
                val obj = root.optJSONObject(i) ?: continue
                val type = runCatching {
                    SectionType.valueOf(obj.optString("type"))
                }.getOrNull() ?: continue

                val setsJson = obj.optJSONArray("sets") ?: JSONArray()
                // В старых записях отдых хранился на упражнении, а не на подходе.
                val legacyRest = obj.optInt("rest", SetEntry.DEFAULT_REST_SEC)
                    .coerceIn(0, SetEntry.MAX_REST_SEC)
                val sets = buildList {
                    for (j in 0 until setsJson.length()) {
                        val set = setsJson.optJSONObject(j) ?: continue
                        add(
                            SetEntry(
                                weightKg = set.optInt("w"),
                                reps = set.optInt("r"),
                                durationSec = set.optInt("d", 0),
                                restSec = set.optInt("rest", legacyRest)
                                    .coerceIn(0, SetEntry.MAX_REST_SEC)
                            )
                        )
                    }
                }
                if (sets.isNotEmpty()) {
                    add(Section(type = type, sets = sets))
                }
            }
        }
    }.getOrDefault(emptyList())
}

fun WorkoutEntity.toModel(): Workout = Workout(
    id = id,
    date = java.time.LocalDate.ofEpochDay(dateEpochDay),
    sections = WorkoutJson.decode(sectionsJson),
    note = note,
    createdAt = createdAt
)

fun Workout.toEntity(): WorkoutEntity = WorkoutEntity(
    id = id,
    dateEpochDay = date.toEpochDay(),
    createdAt = createdAt,
    note = note,
    sectionsJson = WorkoutJson.encode(sections)
)
