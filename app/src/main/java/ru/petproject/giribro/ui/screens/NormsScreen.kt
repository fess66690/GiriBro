package ru.petproject.giribro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.petproject.giribro.AppViewModel
import ru.petproject.giribro.data.CompetitionCalendar
import ru.petproject.giribro.data.CompetitionEvent
import ru.petproject.giribro.data.CompetitionKind
import ru.petproject.giribro.data.NormBook
import ru.petproject.giribro.data.NormCategory
import ru.petproject.giribro.data.NormDiscipline
import ru.petproject.giribro.data.NormDisciplineData
import ru.petproject.giribro.data.NormGender
import ru.petproject.giribro.data.NormGenderData
import ru.petproject.giribro.data.NormPeriodData
import ru.petproject.giribro.data.NormRank
import ru.petproject.giribro.data.NormTier
import ru.petproject.giribro.data.asBodyNumber
import ru.petproject.giribro.data.asBodyWeight
import ru.petproject.giribro.ui.components.EmptyHint
import ru.petproject.giribro.ui.components.SectionTitle
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val weightColumnWidth = 58.dp
private val valueColumnWidth = 52.dp
private val tableRowHeight = 40.dp
private val tableHeaderHeight = 52.dp
private val calendarMonthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))

private const val NORMS_TAB = "norms"
private const val CALENDAR_TAB = "calendar"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NormsScreen(vm: AppViewModel) {
    val measurements by vm.bodyMeasurements.collectAsStateWithLifecycle()
    val latestWeight = remember(measurements) {
        measurements.maxByOrNull { it.date }?.weightKg
    }

    var tab by rememberSaveable { mutableStateOf(NORMS_TAB) }
    var periodId by rememberSaveable { mutableStateOf(NormBook.periods.first().id) }
    var disciplineName by rememberSaveable {
        mutableStateOf(NormDiscipline.JERK.name)
    }
    var genderName by rememberSaveable {
        mutableStateOf(NormGender.MALE.name)
    }
    var weightText by rememberSaveable { mutableStateOf("") }

    val period = NormBook.period(periodId)
    val discipline = period.disciplines.firstOrNull {
        it.discipline.name == disciplineName
    } ?: period.disciplines.first()
    val gender = NormGender.entries.firstOrNull { it.name == genderName } ?: NormGender.MALE
    val genderData = discipline.gender(gender)
    val weight = NormBook.parseWeight(weightText)
    val category = weight?.let { NormBook.weightCategory(it, genderData) }
    val showYouth = period.hasYouth && genderData.hasYouthValues

    when (tab) {
        CALENDAR_TAB -> CalendarContent(
            tab = tab,
            onTabChange = { tab = it }
        )
        else -> NormsContent(
            tab = tab,
            onTabChange = { tab = it },
            period = period,
            discipline = discipline,
            gender = gender,
            genderData = genderData,
            showYouth = showYouth,
            weightText = weightText,
            weight = weight,
            category = category,
            latestWeight = latestWeight,
            onPeriodChange = { newPeriod ->
                periodId = newPeriod.id
                disciplineName = newPeriod.disciplines.first().discipline.name
            },
            onDisciplineChange = { disciplineName = it.name },
            onGenderChange = { genderName = it.name },
            onWeightChange = { weightText = it },
            onUseLatestWeight = {
                latestWeight?.let { weightText = it.asBodyNumber() }
            }
        )
    }
}

@Composable
private fun NormsHeader(tab: String, onTabChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Нормы", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Разрядные нормативы ЕВСК и календарь ВФГС",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tab == NORMS_TAB,
                onClick = { onTabChange(NORMS_TAB) },
                label = { Text("Нормативы") }
            )
            FilterChip(
                selected = tab == CALENDAR_TAB,
                onClick = { onTabChange(CALENDAR_TAB) },
                label = { Text("Календарь") }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NormsContent(
    tab: String,
    onTabChange: (String) -> Unit,
    period: NormPeriodData,
    discipline: NormDisciplineData,
    gender: NormGender,
    genderData: NormGenderData,
    showYouth: Boolean,
    weightText: String,
    weight: Double?,
    category: NormCategory?,
    latestWeight: Double?,
    onPeriodChange: (NormPeriodData) -> Unit,
    onDisciplineChange: (NormDisciplineData) -> Unit,
    onGenderChange: (NormGender) -> Unit,
    onWeightChange: (String) -> Unit,
    onUseLatestWeight: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            NormsHeader(tab = tab, onTabChange = onTabChange)
        }

        item {
            NormsControls(
                period = period,
                discipline = discipline,
                gender = gender,
                weightText = weightText,
                weight = weight,
                latestWeight = latestWeight,
                onPeriodChange = onPeriodChange,
                onDisciplineChange = onDisciplineChange,
                onGenderChange = onGenderChange,
                onWeightChange = onWeightChange,
                onUseLatestWeight = onUseLatestWeight
            )
        }

        item {
            NormSummary(
                discipline = discipline,
                gender = gender,
                category = category,
                weight = weight,
                showYouth = showYouth
            )
        }

        item {
            NormTableCard(
                discipline = discipline,
                gender = gender,
                genderData = genderData,
                category = category,
                showYouth = showYouth
            )
        }

        item {
            Text(
                period.source,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NormsControls(
    period: NormPeriodData,
    discipline: NormDisciplineData,
    gender: NormGender,
    weightText: String,
    weight: Double?,
    latestWeight: Double?,
    onPeriodChange: (NormPeriodData) -> Unit,
    onDisciplineChange: (NormDisciplineData) -> Unit,
    onGenderChange: (NormGender) -> Unit,
    onWeightChange: (String) -> Unit,
    onUseLatestWeight: () -> Unit
) {
    val weightError = weightText.isNotBlank() && weight == null

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("Период")
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NormBook.periods.forEach { option ->
                    FilterChip(
                        selected = option.id == period.id,
                        onClick = { onPeriodChange(option) },
                        label = { Text(option.label) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Пол")
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NormGender.entries.forEach { option ->
                    FilterChip(
                        selected = option == gender,
                        onClick = { onGenderChange(option) },
                        label = { Text(option.label) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Дисциплина")
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                period.disciplines.forEach { option ->
                    FilterChip(
                        selected = option.discipline == discipline.discipline,
                        onClick = { onDisciplineChange(option) },
                        label = { Text(option.name) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = weightText,
                onValueChange = { value ->
                    val filtered = value.filter { it.isDigit() || it == ',' || it == '.' }
                    if (filtered.length <= 6) onWeightChange(filtered)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Мой вес") },
                placeholder = { Text("например, 73 или 73,5") },
                supportingText = {
                    Text(
                        when {
                            weightError -> "Введите вес числом больше нуля"
                            weightText.isBlank() -> "Подсветим вашу весовую категорию"
                            else -> "Вес можно вводить с запятой или точкой"
                        }
                    )
                },
                isError = weightError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    if (weightText.isNotEmpty()) {
                        IconButton(onClick = { onWeightChange("") }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Очистить вес"
                            )
                        }
                    }
                }
            )

            if (latestWeight != null) {
                Spacer(Modifier.height(4.dp))
                AssistChip(
                    onClick = onUseLatestWeight,
                    label = { Text("Из замеров: ${latestWeight.asBodyWeight()}") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Scale,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun NormSummary(
    discipline: NormDisciplineData,
    gender: NormGender,
    category: NormCategory?,
    weight: Double?,
    showYouth: Boolean
) {
    if (weight == null || category == null) {
        EmptyHint(
            title = if (weight == null) "Введите вес" else "Нет данных",
            text = if (weight == null) {
                "Укажите свой вес — подсветим подходящую весовую категорию " +
                    "и покажем нормативы для неё."
            } else {
                "Для этой дисциплины нет таблицы нормативов."
            }
        )
        return
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "ВАША ВЕСОВАЯ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    category.weight,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.padding(bottom = 5.dp)) {
                    Text(
                        "${gender.label} · ${discipline.name}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "${discipline.unit} за ${discipline.time}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            RankGrid(category = category, showYouth = showYouth)
            Spacer(Modifier.height(8.dp))
            Text(
                "Строка ${category.weight} кг подсвечена в таблице ниже.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RankGrid(category: NormCategory, showYouth: Boolean) {
    val tiers = if (showYouth) {
        listOf(NormTier.MASTERS, NormTier.RANKS, NormTier.YOUTH)
    } else {
        listOf(NormTier.MASTERS, NormTier.RANKS)
    }

    tiers.forEach { tier ->
        val ranks = NormRank.entries.filter { it.tier == tier }
        val color = normTierColor(tier)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ranks.forEach { rank ->
                Column(
                    Modifier
                        .weight(1f)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        rank.tableLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        maxLines = 1
                    )
                    Text(
                        category.value(rank)?.toString() ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun NormTableCard(
    discipline: NormDisciplineData,
    gender: NormGender,
    genderData: NormGenderData,
    category: NormCategory?,
    showYouth: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("Таблица нормативов")
                Text(
                    "${gender.label} · ${discipline.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                buildString {
                    append("Звания: ${genderData.weightMasters} кг")
                    append(" · Разряды: ${genderData.weightRanks} кг")
                    if (showYouth) append(" · Юнош.: ${genderData.weightYouth} кг")
                    append(" · ${discipline.unit} за ${discipline.time}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            NormTable(
                data = genderData,
                mine = category,
                showYouth = showYouth
            )
            if (showYouth) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Проведите по таблице влево — там юношеские разряды.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NormTable(
    data: NormGenderData,
    mine: NormCategory?,
    showYouth: Boolean
) {
    val scroll = rememberScrollState()
    val ranks = NormRank.entries.filter { it.tier != NormTier.YOUTH || showYouth }

    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(weightColumnWidth)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(tableHeaderHeight)
                    .padding(start = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    "Вес",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            data.categories.forEachIndexed { index, category ->
                WeightCell(
                    weight = category.weight,
                    isMine = category.weight == mine?.weight,
                    index = index
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .horizontalScroll(scroll)
        ) {
            Row {
                ranks.forEach { rank ->
                    RankHeaderCell(
                        rank = rank,
                        kettlebellWeight = data.weightFor(rank.tier)
                    )
                }
            }
            data.categories.forEachIndexed { index, category ->
                val isMine = category.weight == mine?.weight
                Row(Modifier.height(tableRowHeight)) {
                    ranks.forEach { rank ->
                        ValueCell(
                            value = category.value(rank),
                            tier = rank.tier,
                            isMine = isMine,
                            index = index
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightCell(weight: String, isMine: Boolean, index: Int) {
    Row(
        Modifier
            .width(weightColumnWidth)
            .height(tableRowHeight)
            .background(rowBackground(isMine, index)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    if (isMine) MaterialTheme.colorScheme.primary else Color.Transparent
                )
        )
        Text(
            weight,
            modifier = Modifier.padding(start = 7.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isMine) FontWeight.Bold else FontWeight.Medium,
            color = if (isMine) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1
        )
    }
}

@Composable
private fun RankHeaderCell(rank: NormRank, kettlebellWeight: Int?) {
    Column(
        Modifier
            .width(valueColumnWidth)
            .height(tableHeaderHeight)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            rank.tableLabel,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = normTierColor(rank.tier),
            maxLines = 1
        )
        if (kettlebellWeight != null) {
            Text(
                "$kettlebellWeight кг",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                color = normTierColor(rank.tier).copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ValueCell(value: Int?, tier: NormTier, isMine: Boolean, index: Int) {
    Box(
        Modifier
            .width(valueColumnWidth)
            .height(tableRowHeight)
            .background(rowBackground(isMine, index)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            value?.toString() ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isMine) FontWeight.SemiBold else FontWeight.Normal,
            color = if (value == null) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            } else {
                normTierColor(tier)
            },
            maxLines = 1
        )
    }
}

@Composable
private fun rowBackground(isMine: Boolean, index: Int): Color = when {
    isMine -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    index % 2 == 1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)
    else -> Color.Transparent
}

@Composable
private fun normTierColor(tier: NormTier): Color {
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    return when (tier) {
        NormTier.MASTERS -> if (light) Color(0xFFC62828) else Color(0xFFFF8A80)
        NormTier.RANKS -> if (light) Color(0xFF2E7D32) else Color(0xFF7DDC9A)
        NormTier.YOUTH -> if (light) Color(0xFF9A6700) else Color(0xFFFFC46B)
    }
}

@Composable
private fun CalendarContent(
    tab: String,
    onTabChange: (String) -> Unit
) {
    val today = LocalDate.now()
    var upcomingOnly by rememberSaveable {
        mutableStateOf(CompetitionCalendar.upcoming(today).isNotEmpty())
    }
    val visibleEvents = if (upcomingOnly) {
        CompetitionCalendar.upcoming(today)
    } else {
        CompetitionCalendar.events
    }
    val nearest = CompetitionCalendar.nearest(today)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            NormsHeader(tab = tab, onTabChange = onTabChange)
        }

        item {
            CalendarIntro(nearest = nearest)
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !upcomingOnly,
                        onClick = { upcomingOnly = false },
                        label = { Text("Все") }
                    )
                    FilterChip(
                        selected = upcomingOnly,
                        onClick = { upcomingOnly = true },
                        label = { Text("Предстоящие") }
                    )
                }
                Text(
                    if (upcomingOnly) {
                        "${visibleEvents.size} из ${CompetitionCalendar.events.size}"
                    } else {
                        "${visibleEvents.size}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (visibleEvents.isEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Предстоящих соревнований нет",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "В календаре ${CompetitionCalendar.YEAR} года больше нет предстоящих стартов.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { upcomingOnly = false }) {
                        Text("Показать весь календарь")
                    }
                }
            }
        } else {
            CompetitionCalendar.byMonth(visibleEvents).forEach { (month, events) ->
                item(key = "month-${month}") {
                    MonthHeader(month)
                }
                items(events, key = { it.id }) { event ->
                    CompetitionCard(event = event, today = today)
                }
            }
        }

        item {
            Text(
                CompetitionCalendar.SOURCE,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "УТМ и учебно-тренировочные сборы не включены.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CalendarIntro(nearest: CompetitionEvent?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "КАЛЕНДАРЬ ${CompetitionCalendar.YEAR}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Соревнования ВФГС",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(6.dp))
            if (nearest != null) {
                Text(
                    "Ближайшее: ${nearest.dateLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    nearest.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    nearest.place,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "В календаре 2026 года больше нет предстоящих стартов.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth) {
    Text(
        month.atDay(1).format(calendarMonthFormatter).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun CompetitionCard(event: CompetitionEvent, today: LocalDate) {
    val isPast = event.end.isBefore(today)
    val color = competitionColor(event.kind)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPast) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.alpha(if (isPast) 0.62f else 1f)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(color, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        event.kind.label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    event.dateLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isPast) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    textAlign = TextAlign.End
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                event.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Place,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    event.place,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (event.note != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    event.note,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun competitionColor(kind: CompetitionKind): Color {
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    return when (kind) {
        CompetitionKind.CHAMPIONSHIP ->
            if (light) Color(0xFF9A6700) else Color(0xFFFFC46B)
        CompetitionKind.CHAMPIONSHIP_YOUTH ->
            if (light) Color(0xFF6A3FB5) else Color(0xFFC9A6FF)
        CompetitionKind.CUP ->
            if (light) Color(0xFFB34A00) else Color(0xFFFF9E80)
        CompetitionKind.ALL_RUSSIAN ->
            if (light) Color(0xFF4C6500) else Color(0xFFD4FF4F)
        CompetitionKind.INTERNATIONAL ->
            if (light) Color(0xFF005A8C) else Color(0xFF7FD1FF)
        CompetitionKind.SPARTAKIADA ->
            if (light) Color(0xFF356C1F) else Color(0xFF9CCC65)
        CompetitionKind.STUDENT, CompetitionKind.REGIONAL ->
            if (light) Color(0xFF4A5142) else Color(0xFFB9C0AC)
    }
}
