package ru.petproject.giribro.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.SetEntry
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.data.WorkoutDraft
import ru.petproject.giribro.data.WorkoutDraftStore
import ru.petproject.giribro.data.asDuration
import ru.petproject.giribro.data.asTonnage
import ru.petproject.giribro.timer.TimerPhase
import ru.petproject.giribro.timer.TimerPlan
import ru.petproject.giribro.timer.TimerPlanSource
import ru.petproject.giribro.timer.TimerService
import ru.petproject.giribro.timer.TimerState
import ru.petproject.giribro.timer.toTimerPlan
import ru.petproject.giribro.ui.components.FullScreenTimer
import ru.petproject.giribro.ui.components.SectionBadge
import ru.petproject.giribro.ui.components.sectionColor
import ru.petproject.giribro.util.formatClock
import ru.petproject.giribro.util.ruDayMonth
import ru.petproject.giribro.util.ruWeekday
import ru.petproject.giribro.util.pluralRu
import ru.petproject.giribro.vision.RepExercise
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewWorkoutScreen(
    defaultWeight: Int,
    initialWorkout: Workout? = null,
    repeat: Boolean = false,
    restoreDraft: Boolean = true,
    saving: Boolean = false,
    onSave: (Workout, () -> Unit) -> Unit,
    onClose: () -> Unit,
    onOpenCounter: ((RepExercise) -> Unit)? = null,
    vm: NewWorkoutViewModel = viewModel()
) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val timerState by TimerService.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val draftStore = remember(context.applicationContext) {
        WorkoutDraftStore(context.applicationContext)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var addSetFor by remember { mutableStateOf<Long?>(null) }
    var quickInputOpen by rememberSaveable { mutableStateOf(false) }
    var timerOpen by rememberSaveable { mutableStateOf(false) }
    var draftReady by rememberSaveable { mutableStateOf(!restoreDraft) }
    // Не сохраняем этот флаг в instance state: после смерти процесса он должен
    // снова прочитать черновик, а после поворота экрана — нет.
    var draftChecked by remember { mutableStateOf(false) }
    var pendingDraft by remember { mutableStateOf<WorkoutDraft?>(null) }

    LaunchedEffect(initialWorkout?.id, repeat) {
        if (initialWorkout != null) vm.load(initialWorkout, repeat)
    }

    LaunchedEffect(restoreDraft) {
        if (!restoreDraft) {
            draftReady = true
        } else if (vm.snapshot() != null) {
            // ViewModel пережил поворот экрана: предлагать черновик снова не нужно.
            draftReady = true
        } else {
            val saved = draftStore.load()?.takeIf { it.hasContent }
            if (saved == null) {
                draftReady = true
            } else {
                draftReady = false
                pendingDraft = saved
            }
        }
        draftChecked = true
    }

    // Автосохранение нового черновика. Для редактирования исходник уже есть
    // в дневнике, поэтому второй черновик не нужен.
    LaunchedEffect(draft, draftReady, draftChecked) {
        if (!restoreDraft || !draftReady || !draftChecked) return@LaunchedEffect
        val snapshot = vm.snapshot()
        if (snapshot == null) draftStore.clear() else draftStore.save(snapshot)
    }

    fun saveWorkout() {
        if (!draft.isValid || saving) return
        onSave(vm.toWorkout()) {
            if (restoreDraft) draftStore.clear()
        }
    }

    val plan = remember(draft.sections, draft.prepSec) {
        vm.toWorkout().toTimerPlan(draft.prepSec)
    }
    val activeWorkoutTimer = timerState.takeIf {
        it.plan?.source == TimerPlanSource.WORKOUT &&
            (it.isActive || it.phase == TimerPhase.DONE)
    }
    val lastWorkSectionKey = draft.sections.lastOrNull { it.sets.isNotEmpty() }?.key
    val title = when {
        repeat -> "Повтор тренировки"
        initialWorkout != null -> "Редактирование"
        else -> "Новая тренировка"
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun startWorkoutTimer() {
        if (plan.workCount <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!TimerService.startPlan(context, plan)) {
            Toast.makeText(context, "Не удалось запустить таймер", Toast.LENGTH_SHORT).show()
            return
        }
        timerOpen = true
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { saveWorkout() },
                    enabled = draft.isValid && !saving
                ) {
                    Text(if (saving) "Сохраняем…" else "Сохранить")
                }
            }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    onClick = { showDatePicker = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Дата",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${draft.date.ruDayMonth()}, ${draft.date.ruWeekday()}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            "Изменить",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                WorkoutTimerCard(
                    plan = plan,
                    prepSec = draft.prepSec,
                    active = activeWorkoutTimer,
                    canStart = plan.workCount > 0,
                    onPrepChange = vm::setPrep,
                    onStart = ::startWorkoutTimer,
                    onOpen = { timerOpen = true },
                    onStop = {
                        TimerService.stop(context)
                        timerOpen = false
                    }
                )
            }

            items(draft.sections, key = { it.key }) { section ->
                SectionEditor(
                    type = section.type,
                    sets = section.sets,
                    showRestAfterLastSet = section.key != lastWorkSectionKey,
                    onAddSet = { addSetFor = section.key },
                    onRemoveSet = { index -> vm.removeSet(section.key, index) },
                    onRestChange = { index, delta -> vm.changeRest(section.key, index, delta) },
                    onRemoveSection = { vm.removeSection(section.key) }
                )
            }

            item {
                QuickInputCard(onOpen = { quickInputOpen = true })
            }

            item {
                AddSectionCard(onAdd = vm::addSection)
            }

            item {
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = vm::setNote,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Заметка") },
                    placeholder = { Text("Например: толчок 2×24/25+22+20") },
                    minLines = 2,
                    maxLines = 4
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "ИТОГ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        buildString {
                            append(draft.tonnageKg.asTonnage())
                            append(" · ${draft.reps} ")
                            append(pluralRu(draft.reps, "подъём", "подъёма", "подъёмов"))
                            if (draft.durationSec > 0) {
                                append(" · ")
                                append(draft.durationSec.asDuration())
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (draft.sections.isNotEmpty()) {
                        draft.validationError?.let { problem ->
                            Text(
                                problem,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Button(
                    onClick = { saveWorkout() },
                    enabled = draft.isValid && !saving
                ) {
                    Text(if (saving) "Сохраняем…" else "В дневник")
                }
            }
        }
        }

        if (timerOpen && activeWorkoutTimer != null) {
            FullScreenTimer(
                state = activeWorkoutTimer,
                onBack = { timerOpen = false },
                onTogglePause = {
                    if (activeWorkoutTimer.running) TimerService.pause(context)
                    else TimerService.resume(context)
                },
                onComplete = { TimerService.complete(context) },
                onStop = {
                    TimerService.stop(context)
                    timerOpen = false
                },
                onRestart = {
                    activeWorkoutTimer.plan?.let { activePlan ->
                        if (!TimerService.startPlan(context, activePlan)) {
                            Toast.makeText(
                                context,
                                "Не удалось запустить таймер",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onOpenCounter = onOpenCounter
            )
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = draft.date
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            vm.setDate(
                                Instant.ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                            )
                        }
                        showDatePicker = false
                    }
                ) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    pendingDraft?.let { saved ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Продолжить черновик?") },
            text = {
                Text(
                    buildString {
                        append("Черновик от ")
                        append(saved.date.ruDayMonth())
                        append(": ${saved.sections.size} ")
                        append(
                            pluralRu(
                                saved.sections.size,
                                "упражнение",
                                "упражнения",
                                "упражнений"
                            )
                        )
                        append(", ${saved.reps} ")
                        append(pluralRu(saved.reps, "подъём", "подъёма", "подъёмов"))
                        append(". Продолжить с этого места?")
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.restore(saved)
                        pendingDraft = null
                        draftReady = true
                    }
                ) { Text("Продолжить") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        draftStore.clear()
                        vm.reset()
                        pendingDraft = null
                        draftReady = true
                    }
                ) { Text("Начать заново") }
            }
        )
    }

    addSetFor?.let { key ->
        val section = draft.sections.firstOrNull { it.key == key }
        if (section != null) {
            AddSetDialog(
                type = section.type,
                defaultWeight = defaultWeight,
                onDismiss = { addSetFor = null },
                onConfirm = { entry ->
                    vm.addSet(key, entry)
                    addSetFor = null
                }
            )
        }
    }

    if (quickInputOpen) {
        QuickInputDialog(
            defaultWeight = defaultWeight,
            onDismiss = { quickInputOpen = false },
            onConfirm = { sections ->
                vm.addSections(sections)
                quickInputOpen = false
            }
        )
    }
}

@Composable
private fun SectionEditor(
    type: SectionType,
    sets: List<SetEntry>,
    showRestAfterLastSet: Boolean,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onRestChange: (setIndex: Int, delta: Int) -> Unit,
    onRemoveSection: () -> Unit
) {
    val accent = sectionColor(type)
    val volume = sets.sumOf { it.volumeKg } * type.coefficient
    val reps = sets.sumOf { it.reps }
    val duration = sets.sumOf { it.durationSec }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionBadge(type)
                Spacer(Modifier.width(8.dp))
                if (sets.isNotEmpty()) {
                    Text(
                        buildString {
                            append(volume.asTonnage())
                            if (type.multiplierLabel.isNotEmpty()) {
                                append(" · ")
                                append(type.multiplierLabel)
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemoveSection, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Удалить упражнение",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (sets.isEmpty()) {
                Text(
                    "Подходов пока нет",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Spacer(Modifier.height(6.dp))
                sets.forEachIndexed { index, set ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${set.weightKg} кг × ${set.reps}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (set.durationSec > 0) set.durationSec.asDuration() else "без времени",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            (set.volumeKg * type.coefficient).asTonnage(),
                            style = MaterialTheme.typography.labelLarge,
                            color = accent
                        )
                        IconButton(
                            onClick = { onRemoveSet(index) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Удалить подход",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val showRest = index < sets.lastIndex || showRestAfterLastSet
                    if (showRest) {
                        SetRestStepper(
                            label = if (index < sets.lastIndex) {
                                "Отдых после подхода ${index + 1}"
                            } else {
                                "Отдых перед следующим упражнением"
                            },
                            restSec = set.restSec,
                            onMinus = { onRestChange(index, -15) },
                            onPlus = { onRestChange(index, 15) }
                        )
                    }
                }
                Text(
                    "$reps ${pluralRu(reps, "повторение", "повторения", "повторений")}" +
                        if (duration > 0) " · ${duration.asDuration()} под нагрузкой" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            TextButton(onClick = onAddSet) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Добавить подход")
            }
        }
    }
}

@Composable
private fun WorkoutTimerCard(
    plan: TimerPlan,
    prepSec: Int,
    active: TimerState?,
    canStart: Boolean,
    onPrepChange: (Int) -> Unit,
    onStart: () -> Unit,
    onOpen: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Таймер тренировки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (active != null) {
                    Text(
                        if (active.phase == TimerPhase.DONE) "завершён" else "идёт",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (active != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        append(active.step?.label ?: active.plan?.title ?: "Таймер")
                        if (active.isManual) {
                            append(" · без времени")
                        } else if (active.isActive) {
                            append(" · ")
                            append(formatClock(active.remainingSec))
                        }
                        if (active.counterLabel.isNotEmpty()) {
                            append(" · ")
                            append(active.counterLabel)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpen) { Text("Открыть") }
                    OutlinedButton(onClick = onStop) { Text("Сбросить") }
                }
            } else if (!canStart) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Добавь хотя бы один подход — таймер проведёт по подходам. Время можно " +
                        "не указывать: такой подход нужно будет отметить кнопкой «Выполнено».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        append("Подходов: ")
                        append(plan.workCount)
                        if (plan.workSec > 0) {
                            append(" · работа ")
                            append(formatClock(plan.workSec))
                        }
                        if (plan.manualCount > 0) {
                            append(" · без времени: ")
                            append(plan.manualCount)
                        }
                        append(" · отдых ")
                        append(formatClock(plan.restSec))
                        if (plan.manualCount == 0) {
                            append(" · всего ")
                            append(formatClock(plan.totalSec))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                InlineStepper(
                    label = "Подготовка",
                    value = formatClock(prepSec),
                    onMinus = { onPrepChange(prepSec - 5) },
                    onPlus = { onPrepChange(prepSec + 5) }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Запустить таймер")
                }
            }
        }
    }
}

@Composable
private fun SetRestStepper(
    label: String,
    restSec: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onMinus, modifier = Modifier.size(28.dp)) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            if (restSec == 0) "0:00" else restSec.asDuration(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 48.dp),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onPlus, modifier = Modifier.size(28.dp)) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun InlineStepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onMinus, modifier = Modifier.size(32.dp)) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.widthIn(min = 56.dp),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onPlus, modifier = Modifier.size(32.dp)) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddSectionCard(onAdd: (SectionType) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Добавить упражнение",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SectionType.ordered.forEach { type ->
                    FilterChip(
                        selected = false,
                        onClick = { onAdd(type) },
                        label = { Text(type.label) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddSetDialog(
    type: SectionType,
    defaultWeight: Int,
    onDismiss: () -> Unit,
    onConfirm: (SetEntry) -> Unit
) {
    var weightText by remember { mutableStateOf(defaultWeight.toString()) }
    var repsText by remember { mutableStateOf("20") }
    var minutesText by remember { mutableStateOf("") }
    var secondsText by remember { mutableStateOf("") }

    val weight = weightText.toIntOrNull()
    val reps = repsText.toIntOrNull()
    val minutes = minutesText.toIntOrNull() ?: 0
    val seconds = secondsText.toIntOrNull() ?: 0
    val durationSec = minutes * 60 + seconds
    val valid = weight != null && weight in 0..200 && reps != null && reps in 1..999 &&
        minutes in 0..999 && seconds in 0..599

    val timePresets = listOf(
        30 to "30 с",
        60 to "1 мин",
        90 to "1:30",
        120 to "2 мин",
        180 to "3 мин",
        300 to "5 мин",
        600 to "10 мин"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подход · ${type.label}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Вес, кг") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { repsText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Повторы") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Минуты") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = secondsText,
                        onValueChange = { secondsText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Секунды") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    "Быстрый вес",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(8, 12, 16, 20, 24, 28, 32, 36, 40).forEach { value ->
                        FilterChip(
                            selected = weightText == value.toString(),
                            onClick = { weightText = value.toString() },
                            label = { Text("$value") }
                        )
                    }
                }

                Text(
                    "Быстрые повторы",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(10, 15, 20, 25, 30).forEach { value ->
                        FilterChip(
                            selected = repsText == value.toString(),
                            onClick = { repsText = value.toString() },
                            label = { Text("$value") }
                        )
                    }
                }

                Text(
                    "Быстрое время",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    timePresets.forEach { (presetSec, label) ->
                        FilterChip(
                            selected = durationSec == presetSec &&
                                (minutesText.isNotEmpty() || secondsText.isNotEmpty()),
                            onClick = {
                                minutesText = (presetSec / 60).toString()
                                secondsText = (presetSec % 60).toString()
                            },
                            label = { Text(label) }
                        )
                    }
                }

                if (weight != null && reps != null) {
                    Text(
                        buildString {
                            append("Объём: ")
                            append((weight.toDouble() * reps * type.coefficient).asTonnage())
                            if (durationSec > 0) {
                                append(" · ")
                                append(durationSec.asDuration())
                                append(" под нагрузкой")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                type.note?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(SetEntry(weight ?: 0, reps ?: 0, durationSec)) },
                enabled = valid
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
