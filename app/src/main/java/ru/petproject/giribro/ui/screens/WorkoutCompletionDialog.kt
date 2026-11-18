package ru.petproject.giribro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.data.WorkoutDraft
import ru.petproject.giribro.data.asTonnage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Тренировка по завершении таймера сохраняется сегодняшним числом. */
internal fun WorkoutDraft.asTodayWorkout(): Workout = Workout(
    date = LocalDate.now(),
    sections = sections,
    note = note,
    createdAt = System.currentTimeMillis()
)

/** Короткий текст для системного «Поделиться». */
internal fun workoutShareText(draft: WorkoutDraft): String {
    val workout = draft.asTodayWorkout()
    val date = workout.date.format(
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
    )
    return buildString {
        append("Тренировка ГириБро — ").append(date)
        if (workout.note.isNotBlank()) append("\n").append(workout.note)
        workout.sections.filter { it.sets.isNotEmpty() }.forEach { section ->
            append("\n").append(section.type.label).append(": ")
            append(
                section.sets.joinToString(", ") { set ->
                    buildString {
                        append(set.weightKg).append(" кг × ").append(set.reps)
                        if (set.durationSec > 0) append(" · ").append(set.durationSec).append(" с")
                    }
                }
            )
        }
        append("\nПовторы: ").append(workout.reps)
        if (workout.tonnageKg > 0.0) {
            append("\nТоннаж: ").append(workout.tonnageKg.asTonnage())
        }
        append("\nВремя: ").append(workout.durationSec).append(" с")
    }
}

/**
 * Выбор действия после завершения тренировки: сохранить, поделиться,
 * отредактировать или отменить.
 */
@Composable
fun WorkoutCompletionDialog(
    draft: WorkoutDraft,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit
) {
    val workout = draft.asTodayWorkout()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Тренировка завершена") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Что сделать с тренировкой от ${workout.date.format(
                        DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
                    )}?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Подходы: ${workout.sections.sumOf { it.sets.size }} · " +
                        "Повторы: ${workout.reps}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (workout.tonnageKg > 0.0) {
                    Text(
                        "Тоннаж: ${workout.tonnageKg.asTonnage()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "«Сохранить» добавит тренировку в дневник и метрики за сегодня.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Text("Сохранить")
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                    Text("Поделиться")
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Text("Редактировать")
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Отменить")
                }
            }
        }
    )
}
