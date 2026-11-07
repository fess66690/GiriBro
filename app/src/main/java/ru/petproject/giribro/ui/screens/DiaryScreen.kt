package ru.petproject.giribro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.petproject.giribro.AppViewModel
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.data.asDuration
import ru.petproject.giribro.data.asShortTonnage
import ru.petproject.giribro.data.asTonnage
import ru.petproject.giribro.ui.components.EmptyHint
import ru.petproject.giribro.ui.components.SectionBadge
import ru.petproject.giribro.ui.components.SectionTitle
import ru.petproject.giribro.ui.components.StatTile
import ru.petproject.giribro.ui.components.sectionColor
import ru.petproject.giribro.util.computeStreak
import ru.petproject.giribro.util.pluralRu
import ru.petproject.giribro.util.ruDayMonth
import ru.petproject.giribro.util.ruWeekday
import ru.petproject.giribro.util.weekStart
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale("ru"))

@Composable
fun DiaryScreen(
    vm: AppViewModel,
    onAddWorkout: () -> Unit,
    onEditWorkout: (Workout) -> Unit,
    onRepeatWorkout: (Workout) -> Unit
) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val today = remember { LocalDate.now() }
    val monday = remember(today) { weekStart(today) }

    val weekWorkouts = workouts.filter { !it.date.isBefore(monday) && !it.date.isAfter(today) }
    val monthWorkouts = workouts.filter {
        it.date.year == today.year && it.date.month == today.month
    }
    val weekTonnage = weekWorkouts.sumOf { it.tonnageKg }
    val monthTonnage = monthWorkouts.sumOf { it.tonnageKg }
    val totalTonnage = workouts.sumOf { it.tonnageKg }
    val streak = computeStreak(workouts.map { it.date }, settings.weeklyGoal, today)

    var selected by remember { mutableStateOf<Workout?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "ГИРИБРО",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 4.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${today.ruDayMonth()}, ${today.ruWeekday().lowercase(Locale("ru"))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Г", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                StreakCard(
                    streak = streak,
                    weekCount = weekWorkouts.size,
                    goal = settings.weeklyGoal,
                    weekTonnage = weekTonnage
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        label = "За месяц",
                        value = "${monthWorkouts.size}",
                        caption = "трен. · ${monthTonnage.asShortTonnage()}",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Всего",
                        value = "${workouts.size}",
                        caption = "трен. · ${totalTonnage.asShortTonnage()}",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Подъёмы",
                        value = "${workouts.sumOf { it.reps }}",
                        caption = "за всё время",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("Последние тренировки")
            }

            if (workouts.isEmpty()) {
                item {
                    EmptyHint(
                        title = "Пока пусто",
                        text = "Запиши первую тренировку — тоннаж, подъёмы и серия посчитаются сами."
                    )
                }
            } else {
                items(workouts, key = { it.id }) { workout ->
                    WorkoutRow(workout) { selected = workout }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddWorkout,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Тренировка") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }

    selected?.let { workout ->
        WorkoutDetailsDialog(
            workout = workout,
            onDismiss = { selected = null },
            onDelete = {
                vm.deleteWorkout(workout)
                selected = null
            },
            onEdit = {
                selected = null
                onEditWorkout(workout)
            },
            onRepeat = {
                selected = null
                onRepeatWorkout(workout)
            }
        )
    }
}

@Composable
private fun StreakCard(streak: Int, weekCount: Int, goal: Int, weekTonnage: Double) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        colors = CardDefaults.cardColors(
            containerColor = primary.copy(alpha = 0.10f)
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "СЕРИЯ",
                        style = MaterialTheme.typography.labelMedium,
                        color = primary
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$streak",
                            style = MaterialTheme.typography.displayLarge,
                            color = primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            pluralRu(streak, "неделя", "недели", "недель"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                    Text(
                        "подряд по $goal ${pluralRu(goal, "тренировке", "тренировкам", "тренировкам")} в неделю",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "ЭТА НЕДЕЛЯ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$weekCount / $goal",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { (weekCount.toFloat() / goal).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = primary,
                trackColor = primary.copy(alpha = 0.18f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Тоннаж недели: ${weekTonnage.asTonnage()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkoutRow(workout: Workout, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier
                    .size(46.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "${workout.date.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    workout.date.format(shortMonth),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                val title = workout.mainSections
                    .map { it.type.short }
                    .ifEmpty { listOf("Тренировка") }
                    .joinToString(" · ")
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    buildString {
                        append("${workout.reps} ")
                        append(pluralRu(workout.reps, "подъём", "подъёма", "подъёмов"))
                        append(" · ${workout.sections.size} ")
                        append(pluralRu(workout.sections.size, "блок", "блока", "блоков"))
                        if (workout.durationSec > 0) {
                            append(" · ")
                            append(workout.durationSec.asDuration())
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                workout.tonnageKg.asShortTonnage(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun WorkoutDetailsDialog(
    workout: Workout,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onRepeat: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${workout.date.ruDayMonth()}, ${workout.date.ruWeekday()}")
                Text(
                    workout.tonnageKg.asTonnage() + " · ${workout.reps} " +
                        pluralRu(workout.reps, "подъём", "подъёма", "подъёмов") +
                        if (workout.durationSec > 0) " · ${workout.durationSec.asDuration()}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                workout.sections.forEach { section ->
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionBadge(section.type)
                            Text(
                                buildString {
                                    append(section.volumeKg.asTonnage())
                                    if (section.type.multiplierLabel.isNotEmpty()) {
                                        append(" · ")
                                        append(section.type.multiplierLabel)
                                    }
                                    if (section.durationSec > 0) {
                                        append(" · ")
                                        append(section.durationSec.asDuration())
                                    }
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = sectionColor(section.type)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            section.sets.joinToString(" · ") { entry ->
                                buildString {
                                    append("${entry.weightKg}×${entry.reps}")
                                    if (entry.durationSec > 0) {
                                        append(" (")
                                        append(entry.durationSec.asDuration())
                                        append(")")
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (workout.note.isNotBlank()) {
                    HorizontalDivider()
                    Text(
                        workout.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRepeat,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Повторить", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Изменить", maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(4.dp))
                Text("Удалить", color = MaterialTheme.colorScheme.error)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
