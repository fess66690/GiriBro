package ru.petproject.giribro.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Незаконченная тренировка, которая переживает смерть процесса.
 *
 * В черновике нет id тренировки: после сохранения он не нужен. Для режима
 * редактирования сохраняются исходные id и createdAt, чтобы не создать дубль.
 */
data class WorkoutDraft(
    val date: LocalDate,
    val note: String = "",
    val sections: List<Section> = emptyList(),
    val prepSec: Int = 15,
    val editingId: Long? = null,
    val editingCreatedAt: Long? = null
) {
    val hasContent: Boolean
        get() = note.isNotBlank() || sections.any { it.sets.isNotEmpty() }

    val setsCount: Int get() = sections.sumOf { it.sets.size }
    val reps: Int get() = sections.sumOf { it.reps }
    val tonnageKg: Double get() = sections.sumOf { it.tonnageKg }
}

object WorkoutDraftJson {

    const val VERSION = 1

    fun encode(draft: WorkoutDraft): String = JSONObject()
        .put("version", VERSION)
        .put("date", draft.date.toString())
        .put("note", draft.note)
        .put("prepSec", draft.prepSec.coerceIn(0, 120))
        .put("sections", JSONArray(WorkoutJson.encode(draft.sections)))
        .apply {
            draft.editingId?.takeIf { it > 0L }?.let { put("editingId", it) }
            draft.editingCreatedAt?.takeIf { it > 0L }?.let { put("editingCreatedAt", it) }
        }
        .toString()

    fun decode(raw: String): WorkoutDraft? = runCatching {
        val root = JSONObject(raw)
        if (root.optInt("version", 0) !in 1..VERSION) return null
        val date = LocalDate.parse(root.getString("date"))
        if (date.year !in 1900..2200) return null
        val sections = WorkoutJson.decode(root.optJSONArray("sections")?.toString() ?: "[]")
        WorkoutDraft(
            date = date,
            note = root.optString("note", ""),
            sections = sections,
            prepSec = root.optInt("prepSec", 15).coerceIn(0, 120),
            editingId = root.optLong("editingId", 0L).takeIf { it > 0L },
            editingCreatedAt = root.optLong("editingCreatedAt", 0L).takeIf { it > 0L }
        )
    }.getOrNull()
}

/** Хранит единственный черновик в SharedPreferences. */
class WorkoutDraftStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("giribro_draft", Context.MODE_PRIVATE)

    fun load(): WorkoutDraft? =
        prefs.getString(KEY_DRAFT, null)?.let(WorkoutDraftJson::decode)

    fun save(draft: WorkoutDraft) {
        if (!draft.hasContent) {
            clear()
            return
        }
        prefs.edit().putString(KEY_DRAFT, WorkoutDraftJson.encode(draft)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_DRAFT).apply()
    }

    private companion object {
        const val KEY_DRAFT = "workout"
    }
}
