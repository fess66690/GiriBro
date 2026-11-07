package ru.petproject.giribro.ui.screens

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.petproject.giribro.data.Section
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.SetEntry
import ru.petproject.giribro.data.Workout
import java.time.LocalDate

class NewWorkoutViewModel : ViewModel() {

    data class DraftSection(
        val key: Long,
        val type: SectionType,
        val sets: List<SetEntry> = emptyList()
    )

    data class Draft(
        val date: LocalDate = LocalDate.now(),
        val note: String = "",
        val sections: List<DraftSection> = emptyList(),
        /** Подготовка перед стартом таймера, секунды. */
        val prepSec: Int = DEFAULT_PREP_SEC
    ) {
        val tonnageKg: Double
            get() = sections.sumOf { section ->
                if (section.type.countsTonnage) {
                    section.sets.sumOf { it.volumeKg } * section.type.coefficient
                } else {
                    0.0
                }
            }

        val volumeKg: Double
            get() = sections.sumOf { section ->
                section.sets.sumOf { it.volumeKg } * section.type.coefficient
            }

        val reps: Int get() = sections.sumOf { section -> section.sets.sumOf { it.reps } }

        val durationSec: Int get() = sections.sumOf { section -> section.sets.sumOf { it.durationSec } }

        val isValid: Boolean get() = sections.any { it.sets.isNotEmpty() }

        val timedSetsCount: Int
            get() = sections.sumOf { section -> section.sets.count { it.durationSec > 0 } }
    }

    private var nextKey = 0L
    private var editingId: Long? = null
    private var editingCreatedAt: Long? = null

    private val _draft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = _draft.asStateFlow()

    /**
     * Заполняет конструктор готовой тренировкой.
     * [repeat] — режим повтора: дата ставится сегодняшняя, а id не сохраняется.
     */
    fun load(source: Workout, repeat: Boolean = false) {
        nextKey = 0L
        editingId = if (repeat) null else source.id
        editingCreatedAt = if (repeat) null else source.createdAt
        _draft.value = Draft(
            date = if (repeat) LocalDate.now() else source.date,
            note = source.note,
            sections = source.sections.map { section ->
                DraftSection(
                    key = nextKey++,
                    type = section.type,
                    sets = section.sets
                )
            }
        )
    }

    fun addSection(type: SectionType) {
        _draft.update {
            it.copy(sections = it.sections + DraftSection(nextKey++, type))
        }
    }

    fun removeSection(key: Long) {
        _draft.update { draft ->
            draft.copy(sections = draft.sections.filterNot { it.key == key })
        }
    }

    /** Меняет отдых после конкретного подхода. */
    fun changeRest(key: Long, setIndex: Int, delta: Int) {
        _draft.update { draft ->
            draft.copy(
                sections = draft.sections.map { section ->
                    if (section.key != key) return@map section
                    section.copy(
                        sets = section.sets.mapIndexed { index, set ->
                            if (index != setIndex) return@mapIndexed set
                            set.copy(
                                restSec = (set.restSec + delta)
                                    .coerceIn(0, SetEntry.MAX_REST_SEC)
                            )
                        }
                    )
                }
            )
        }
    }

    fun setPrep(seconds: Int) {
        _draft.update { it.copy(prepSec = seconds.coerceIn(0, MAX_PREP_SEC)) }
    }

    fun addSet(key: Long, entry: SetEntry) {
        _draft.update { draft ->
            draft.copy(
                sections = draft.sections.map { section ->
                    if (section.key == key) {
                        // Новый подход по умолчанию повторяет паузу предыдущего:
                        // так удобно держать один ритм, а потом точечно менять.
                        val rest = section.sets.lastOrNull()?.restSec ?: entry.restSec
                        section.copy(sets = section.sets + entry.copy(restSec = rest))
                    } else {
                        section
                    }
                }
            )
        }
    }

    fun removeSet(key: Long, index: Int) {
        _draft.update { draft ->
            draft.copy(
                sections = draft.sections.map { section ->
                    if (section.key == key) {
                        section.copy(sets = section.sets.filterIndexed { i, _ -> i != index })
                    } else {
                        section
                    }
                }
            )
        }
    }

    fun setDate(date: LocalDate) = _draft.update { it.copy(date = date) }

    fun setNote(note: String) = _draft.update { it.copy(note = note) }

    fun toWorkout(): Workout {
        val draft = _draft.value
        return Workout(
            id = editingId ?: 0L,
            date = draft.date,
            sections = draft.sections
                .filter { it.sets.isNotEmpty() }
                .map { Section(it.type, it.sets) },
            note = draft.note.trim(),
            createdAt = editingCreatedAt ?: System.currentTimeMillis()
        )
    }

    companion object {
        const val DEFAULT_PREP_SEC = 15
        const val MAX_PREP_SEC = 120
    }
}
