package ru.petproject.giribro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.petproject.giribro.AppViewModel
import ru.petproject.giribro.data.CompetitionCalendar
import ru.petproject.giribro.data.asBodyWeight
import ru.petproject.giribro.ui.components.SectionTitle
import ru.petproject.giribro.ui.components.StatTile
import ru.petproject.giribro.util.pluralRu
import java.time.LocalDate

@Composable
fun ProfileScreen(
    vm: AppViewModel,
    onOpenBody: () -> Unit,
    onOpenNorms: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val measurements by vm.bodyMeasurements.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val today = remember { LocalDate.now() }
    val nearestEvent = remember(today) { CompetitionCalendar.nearest(today) }
    val latestWeight = measurements.firstOrNull()?.weightKg

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ProfileHeader() }

        item {
            ProfileOverview(
                workoutCount = workouts.size,
                measurementCount = measurements.size,
                latestWeight = latestWeight
            )
        }

        item { SectionTitle("Разделы") }

        item {
            ProfileNavCard(
                icon = Icons.Outlined.Scale,
                title = "Замеры тела",
                subtitle = if (latestWeight != null) {
                    "Последний вес: ${latestWeight.asBodyWeight()}"
                } else {
                    "Вес, обхваты и динамика"
                },
                onClick = onOpenBody
            )
        }

        item {
            ProfileNavCard(
                icon = Icons.Outlined.EmojiEvents,
                title = "Нормативы",
                subtitle = "ЕВСК 2026–2029 · разряды и звания",
                onClick = onOpenNorms
            )
        }

        item {
            ProfileNavCard(
                icon = Icons.Outlined.CalendarMonth,
                title = "Календарь",
                subtitle = nearestEvent?.let {
                    "Ближайшее: ${it.dateLabel()} · ${it.place}"
                } ?: "Соревнования ВФГС ${CompetitionCalendar.YEAR}",
                onClick = onOpenCalendar
            )
        }

        item {
            ProfileNavCard(
                icon = Icons.Outlined.Settings,
                title = "Настройки",
                subtitle = "Тема, цель недели, резервная копия",
                onClick = onOpenSettings
            )
        }

        item {
            LocalDataNote(weeklyGoal = settings.weeklyGoal)
        }
    }
}

@Composable
private fun ProfileHeader() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(
                text = "ПРОФИЛЬ",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Данные, нормативы и настройки",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            Modifier
                .size(42.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ProfileOverview(
    workoutCount: Int,
    measurementCount: Int,
    latestWeight: Double?
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "ГириБро",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Версия 0.1.0 · данные только на устройстве",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Тренировки",
                    value = "$workoutCount",
                    caption = pluralRu(
                        workoutCount,
                        "запись",
                        "записи",
                        "записей"
                    ),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Замеры",
                    value = "$measurementCount",
                    caption = pluralRu(
                        measurementCount,
                        "замер",
                        "замера",
                        "замеров"
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            if (latestWeight != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Последний вес: ${latestWeight.asBodyWeight()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProfileNavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LocalDataNote(weeklyGoal: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                RoundedCornerShape(20.dp)
            )
            .padding(18.dp)
    ) {
        SectionTitle("Конфиденциальность")
        Spacer(Modifier.height(6.dp))
        Text(
            "Тренировки, замеры и настройки хранятся только в памяти этого " +
                "устройства. Приложение не отправляет их в сеть.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Цель недели: $weeklyGoal ${pluralRu(weeklyGoal, "тренировка", "тренировки", "тренировок")}.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
