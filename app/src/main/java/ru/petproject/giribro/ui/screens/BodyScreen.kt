package ru.petproject.giribro.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.petproject.giribro.AppViewModel
import ru.petproject.giribro.data.BodyMeasurement
import ru.petproject.giribro.data.BodyMeasurementValidator
import ru.petproject.giribro.data.asBodyNumber
import ru.petproject.giribro.data.asBodySize
import ru.petproject.giribro.data.asBodyWeight
import ru.petproject.giribro.data.asSignedBodyWeight
import ru.petproject.giribro.ui.components.EmptyHint
import ru.petproject.giribro.ui.components.SectionTitle
import ru.petproject.giribro.ui.components.StatTile
import ru.petproject.giribro.util.pluralRu
import ru.petproject.giribro.util.ruDayMonth
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val bodyShortMonth = DateTimeFormatter.ofPattern("MMM", Locale("ru"))
private val bodyDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("ru"))

@Composable
fun BodyScreen(vm: AppViewModel) {
    val measurements by vm.bodyMeasurements.collectAsStateWithLifecycle()
    val saving by vm.bodySaving.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }

    var editorOpen by remember { mutableStateOf(false) }
    var editedMeasurement by remember { mutableStateOf<BodyMeasurement?>(null) }
    var deleteCandidate by remember { mutableStateOf<BodyMeasurement?>(null) }

    val latest = measurements.firstOrNull()
    val previous = measurements.getOrNull(1)
    val chartEntries = measurements.take(12).reversed()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { BodyHeader(today) }

            if (latest == null) {
                item {
                    EmptyHint(
                        title = "Пока нет замеров",
                        text = "Добавьте вес и обхваты — здесь появится динамика и история."
                    )
                }
            } else {
                item { LatestMeasurementCard(latest, previous) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val delta = previous?.let { latest.weightKg - it.weightKg }
                        StatTile(
                            label = "Изменение",
                            value = delta?.asSignedBodyWeight() ?: "—",
                            caption = if (previous == null) "первый замер" else "к прошлому",
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "Всего",
                            value = "${measurements.size}",
                            caption = pluralRu(
                                measurements.size,
                                "замер",
                                "замера",
                                "замеров"
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (chartEntries.size >= 2) {
                    item { WeightChartCard(chartEntries) }
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    SectionTitle("История замеров")
                }
                items(measurements, key = { it.id }) { measurement ->
                    MeasurementRow(
                        measurement = measurement,
                        onClick = {
                            editedMeasurement = measurement
                            editorOpen = true
                        },
                        onDelete = { deleteCandidate = measurement }
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                editedMeasurement = null
                editorOpen = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Замер") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }

    if (editorOpen) {
        BodyMeasurementEditorDialog(
            initial = editedMeasurement,
            saving = saving,
            onDismiss = { if (!saving) editorOpen = false },
            onSave = { measurement ->
                vm.saveBodyMeasurement(measurement) {
                    editorOpen = false
                    editedMeasurement = null
                }
            }
        )
    }

    deleteCandidate?.let { measurement ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Удалить замер?") },
            text = {
                Text(
                    "Замер от ${measurement.date.ruDayMonth()} будет удалён. " +
                        "Отменить действие нельзя."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteBodyMeasurement(measurement)
                        deleteCandidate = null
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun BodyHeader(today: LocalDate) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(
                text = "ТЕЛО",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Вес и замеры · ${today.ruDayMonth()}",
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
                Icons.Outlined.Scale,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LatestMeasurementCard(
    latest: BodyMeasurement,
    previous: BodyMeasurement?
) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        colors = CardDefaults.cardColors(containerColor = primary.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "ПОСЛЕДНИЙ ЗАМЕР",
                style = MaterialTheme.typography.labelMedium,
                color = primary
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    latest.weightKg.asBodyWeight(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    latest.date.ruDayMonth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }

            val delta = previous?.let { latest.weightKg - it.weightKg }
            Text(
                text = when {
                    delta == null -> "Первый замер"
                    abs(delta) < 0.05 -> "Без изменений к прошлому замеру"
                    else -> "${delta.asSignedBodyWeight()} к прошлому замеру"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    delta < -0.05 -> primary
                    delta > 0.05 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            val sizes = latest.sizes
            if (sizes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    sizes.joinToString(" · ") { (label, value) ->
                        "$label ${value.asBodySize()}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (latest.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    latest.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeightChartCard(entries: List<BodyMeasurement>) {
    val weights = entries.map { it.weightKg }
    val minWeight = weights.minOrNull() ?: 0.0
    val maxWeight = weights.maxOrNull() ?: 0.0
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("Динамика веса")
                Text(
                    "${minWeight.asBodyWeight()} – ${maxWeight.asBodyWeight()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                val padX = 8.dp.toPx()
                val padY = 10.dp.toPx()
                val usableWidth = size.width - padX * 2
                val usableHeight = size.height - padY * 2

                repeat(3) { index ->
                    val y = padY + usableHeight * index / 2f
                    drawLine(
                        color = gridColor,
                        start = Offset(padX, y),
                        end = Offset(size.width - padX, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val flat = maxWeight - minWeight < 0.05
                val points = weights.mapIndexed { index, value ->
                    val x = if (weights.size == 1) {
                        size.width / 2f
                    } else {
                        padX + usableWidth * index / (weights.size - 1).toFloat()
                    }
                    val fraction = if (flat) {
                        0.5f
                    } else {
                        ((value - minWeight) / (maxWeight - minWeight)).toFloat()
                    }
                    Offset(x, padY + usableHeight * (1f - fraction))
                }

                if (points.size > 1) {
                    val fill = Path().apply {
                        moveTo(points.first().x, size.height - padY)
                        lineTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, size.height - padY)
                        close()
                    }
                    drawPath(fill, color = lineColor.copy(alpha = 0.12f))

                    val line = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = line,
                        color = lineColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                points.forEach { point ->
                    drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = point)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    entries.first().date.ruDayMonth(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    entries.last().date.ruDayMonth(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MeasurementRow(
    measurement: BodyMeasurement,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    "${measurement.date.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    measurement.date.format(bodyShortMonth),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    measurement.weightKg.asBodyWeight(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val sizes = measurement.sizes
                if (sizes.isNotEmpty()) {
                    Text(
                        sizes.joinToString(" · ") { (label, value) ->
                            "$label ${value.asBodyNumber()}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (measurement.note.isNotBlank()) {
                    Text(
                        measurement.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Удалить замер",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodyMeasurementEditorDialog(
    initial: BodyMeasurement?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (BodyMeasurement) -> Unit
) {
    var date by remember(initial?.id) { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var weight by remember(initial?.id) {
        mutableStateOf(initial?.weightKg?.asBodyNumber() ?: "")
    }
    var waist by remember(initial?.id) {
        mutableStateOf(initial?.waistCm?.asBodyNumber() ?: "")
    }
    var chest by remember(initial?.id) {
        mutableStateOf(initial?.chestCm?.asBodyNumber() ?: "")
    }
    var hips by remember(initial?.id) {
        mutableStateOf(initial?.hipsCm?.asBodyNumber() ?: "")
    }
    var arm by remember(initial?.id) {
        mutableStateOf(initial?.armCm?.asBodyNumber() ?: "")
    }
    var thigh by remember(initial?.id) {
        mutableStateOf(initial?.thighCm?.asBodyNumber() ?: "")
    }
    var note by remember(initial?.id) { mutableStateOf(initial?.note ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    val weightValue = parseBodyDecimal(weight)
    val weightValid = weightValue != null &&
        weightValue >= BodyMeasurementValidator.MIN_WEIGHT_KG &&
        weightValue <= BodyMeasurementValidator.MAX_WEIGHT_KG
    val weightError = weight.isNotBlank() && !weightValid

    val waistInvalid = isSizeInvalid(waist)
    val chestInvalid = isSizeInvalid(chest)
    val hipsInvalid = isSizeInvalid(hips)
    val armInvalid = isSizeInvalid(arm)
    val thighInvalid = isSizeInvalid(thigh)
    val dateError = date.isAfter(LocalDate.now())
    val noteError = note.length > BodyMeasurementValidator.MAX_NOTE_LENGTH

    val valid = weightValid &&
        !waistInvalid &&
        !chestInvalid &&
        !hipsInvalid &&
        !armInvalid &&
        !thighInvalid &&
        !dateError &&
        !noteError

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(if (initial == null) "Новый замер" else "Изменить замер")
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(date.format(bodyDateFormatter))
                }
                if (dateError) {
                    Text(
                        "Дата не может быть в будущем.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Вес, кг") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = weightError,
                    supportingText = if (weightError) {
                        {
                            Text(
                                "От ${BodyMeasurementValidator.MIN_WEIGHT_KG.toInt()} до " +
                                    "${BodyMeasurementValidator.MAX_WEIGHT_KG.toInt()} кг"
                            )
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                MeasurementInput(
                    value = waist,
                    onValueChange = { waist = it },
                    label = "Талия, см",
                    isError = waistInvalid
                )
                MeasurementInput(
                    value = chest,
                    onValueChange = { chest = it },
                    label = "Грудь, см",
                    isError = chestInvalid
                )
                MeasurementInput(
                    value = hips,
                    onValueChange = { hips = it },
                    label = "Бёдра, см",
                    isError = hipsInvalid
                )
                MeasurementInput(
                    value = arm,
                    onValueChange = { arm = it },
                    label = "Плечо, см",
                    isError = armInvalid
                )
                MeasurementInput(
                    value = thigh,
                    onValueChange = { thigh = it },
                    label = "Бедро, см",
                    isError = thighInvalid
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Заметка") },
                    minLines = 2,
                    maxLines = 4,
                    isError = noteError,
                    supportingText = {
                        Text(
                            if (noteError) {
                                "Максимум ${BodyMeasurementValidator.MAX_NOTE_LENGTH} символов"
                            } else {
                                "${note.length}/${BodyMeasurementValidator.MAX_NOTE_LENGTH}"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (weightError || waistInvalid || chestInvalid || hipsInvalid ||
                    armInvalid || thighInvalid
                ) {
                    Text(
                        "Проверьте выделенные поля.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = save@{
                    val weightNumber = weightValue ?: return@save
                    onSave(
                        BodyMeasurement(
                            id = initial?.id ?: 0L,
                            date = date,
                            weightKg = roundToOneDecimal(weightNumber),
                            waistCm = parseOptionalSize(waist),
                            chestCm = parseOptionalSize(chest),
                            hipsCm = parseOptionalSize(hips),
                            armCm = parseOptionalSize(arm),
                            thighCm = parseOptionalSize(thigh),
                            note = note.trim(),
                            createdAt = initial?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                },
                enabled = valid && !saving
            ) {
                Text(if (saving) "Сохраняем…" else "Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Отмена") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun MeasurementInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = isError,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun parseBodyDecimal(raw: String): Double? =
    raw.trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() }

private fun parseOptionalSize(raw: String): Double? {
    if (raw.isBlank()) return null
    return parseBodyDecimal(raw)?.let(::roundToOneDecimal)
}

private fun isSizeInvalid(raw: String): Boolean {
    if (raw.isBlank()) return false
    val value = parseBodyDecimal(raw) ?: return true
    return value < BodyMeasurementValidator.MIN_SIZE_CM ||
        value > BodyMeasurementValidator.MAX_SIZE_CM
}

private fun roundToOneDecimal(value: Double): Double =
    kotlin.math.round(value * 10.0) / 10.0
