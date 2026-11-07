package ru.petproject.giribro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.petproject.giribro.AppViewModel
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.StatsPeriod
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.data.asDuration
import ru.petproject.giribro.data.asShortTonnage
import ru.petproject.giribro.data.asTonnage
import ru.petproject.giribro.ui.components.SectionTitle
import ru.petproject.giribro.ui.components.StatTile
import ru.petproject.giribro.ui.components.sectionColor
import ru.petproject.giribro.util.bestStreak
import ru.petproject.giribro.util.pluralRu
import ru.petproject.giribro.util.ruDayMonth
import ru.petproject.giribro.util.weekStart
import java.time.LocalDate

private data class ChartBucket(val start: LocalDate, val end: LocalDate)

private data class ChartData(
    val title: String,
    val leftLabel: String,
    val rightLabel: String,
    val values: List<Double>
)

private data class TypeStat(
    val type: SectionType,
    val volume: Double,
    val durationSec: Int
)

private fun buildChart(
    period: StatsPeriod,
    workouts: List<Workout>,
    today: LocalDate
): ChartData {
    val buckets: List<ChartBucket>
    val title: String
    val leftLabel: String

    when (period) {
        StatsPeriod.WEEK -> {
            buckets = (6 downTo 0).map { back ->
                val day = today.minusDays(back.toLong())
                ChartBucket(day, day)
            }
            title = "7 дней"
            leftLabel = "7 дн. назад"
        }
        StatsPeriod.MONTH -> {
            buckets = (9 downTo 0).map { index ->
                val end = today.minusDays((index * 3).toLong())
                ChartBucket(end.minusDays(2), end)
            }
            title = "30 дней"
            leftLabel = "30 дн. назад"
        }
        StatsPeriod.QUARTER -> {
            val monday = weekStart(today)
            buckets = (11 downTo 0).map { back ->
                val start = monday.minusWeeks(back.toLong())
                ChartBucket(start, start.plusDays(6))
            }
            title = "12 недель"
            leftLabel = "12 нед. назад"
        }
        StatsPeriod.YEAR, StatsPeriod.ALL -> {
            val first = today.withDayOfMonth(1)
            buckets = (11 downTo 0).map { back ->
                val start = first.minusMonths(back.toLong())
                ChartBucket(start, start.withDayOfMonth(start.lengthOfMonth()))
            }
            title = if (period == StatsPeriod.ALL) "Последние 12 месяцев" else "12 месяцев"
            leftLabel = "12 мес. назад"
        }
    }

    val values = buckets.map { bucket ->
        workouts
            .filter { !it.date.isBefore(bucket.start) && !it.date.isAfter(bucket.end) }
            .sumOf { it.tonnageKg }
    }
    return ChartData(title, leftLabel, "сейчас", values)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(vm: AppViewModel) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val period = settings.statsPeriod
    val today = LocalDate.now()
    val periodStart = period.startDate(today)
    val filtered = workouts.filter { workout ->
        !workout.date.isAfter(today) &&
            (periodStart == null || !workout.date.isBefore(periodStart))
    }

    val totalTonnage = filtered.sumOf { it.tonnageKg }
    val totalReps = filtered.sumOf { it.reps }
    val totalTime = filtered.sumOf { it.durationSec }
    val averageTime = if (filtered.isEmpty()) 0 else totalTime / filtered.size
    val bestWorkout = filtered.maxByOrNull { it.tonnageKg }
    val streak = bestStreak(workouts.map { it.date }, settings.weeklyGoal)

    val chart = buildChart(period, filtered, today)
    val maxBar = chart.values.maxOfOrNull { it } ?: 0.0

    val byType = SectionType.ordered.mapNotNull { type ->
        val sections = filtered.flatMap { it.sections }.filter { it.type == type }
        val volume = sections.sumOf { it.volumeKg }
        val time = sections.sumOf { it.durationSec }
        if (volume > 0.0 || time > 0) TypeStat(type, volume, time) else null
    }.sortedByDescending { it.volume }
    val maxVolume = byType.maxOfOrNull { it.volume } ?: 0.0

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("Статистика", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatsPeriod.entries.forEach { option ->
                        FilterChip(
                            selected = option == period,
                            onClick = { vm.setStatsPeriod(option) },
                            label = { Text(option.label) }
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                ),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "ПОДНЯТО ${period.caption?.uppercase() ?: "ВСЕГО"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        totalTonnage.asTonnage(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${filtered.size} ${pluralRu(filtered.size, "тренировка", "тренировки", "тренировок")} · " +
                            "$totalReps ${pluralRu(totalReps, "подъём", "подъёма", "подъёмов")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Под нагрузкой",
                    value = if (totalTime > 0) totalTime.asDuration() else "—",
                    caption = period.caption ?: "за всё время",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "В среднем",
                    value = if (averageTime > 0) averageTime.asDuration() else "—",
                    caption = "на тренировку",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionTitle(chart.title)
                        Text(
                            maxBar.asShortTonnage() + " макс.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        chart.values.forEach { tonnage ->
                            val fraction = if (maxBar > 0.0) tonnage / maxBar else 0.0
                            val height = (fraction * 116).coerceAtLeast(if (tonnage > 0) 4.0 else 2.0)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(height.dp)
                                    .background(
                                        if (tonnage >= maxBar && maxBar > 0) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        },
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            chart.leftLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            chart.rightLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("По упражнениям")
                    Spacer(Modifier.height(12.dp))
                    if (byType.isEmpty()) {
                        Text(
                            "Данных пока нет",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        byType.forEachIndexed { index, stat ->
                            val color = sectionColor(stat.type)
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        stat.type.label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        stat.volume.asTonnage(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = color
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (maxVolume > 0) (stat.volume / maxVolume).toFloat() else 0f
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp),
                                    color = color,
                                    trackColor = color.copy(alpha = 0.15f)
                                )
                                if (stat.durationSec > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${stat.durationSec.asDuration()} под нагрузкой",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (index != byType.lastIndex) Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("Рекорды")
                    Spacer(Modifier.height(10.dp))
                    RecordRow(
                        icon = Icons.Outlined.Scale,
                        title = "Лучшая тренировка",
                        value = bestWorkout?.let {
                            "${it.tonnageKg.asTonnage()} · ${it.date.ruDayMonth()}"
                        } ?: "—"
                    )
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    RecordRow(
                        icon = Icons.Outlined.LocalFireDepartment,
                        title = "Лучшая серия",
                        value = if (streak > 0) {
                            "$streak ${pluralRu(streak, "неделя", "недели", "недель")} подряд"
                        } else {
                            "—"
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    RecordRow(
                        icon = Icons.Outlined.EmojiEvents,
                        title = "Средний тоннаж",
                        value = if (filtered.isEmpty()) "—" else (totalTonnage / filtered.size).asTonnage()
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordRow(icon: ImageVector, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
