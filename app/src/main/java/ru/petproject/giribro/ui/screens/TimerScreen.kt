package ru.petproject.giribro.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.petproject.giribro.data.SetCaptureMode
import ru.petproject.giribro.timer.TimerConfig
import ru.petproject.giribro.timer.TimerPhase
import ru.petproject.giribro.timer.TimerService
import ru.petproject.giribro.ui.components.FullScreenTimer
import ru.petproject.giribro.ui.components.SectionTitle
import ru.petproject.giribro.util.formatClock
import ru.petproject.giribro.vision.RepExercise

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerScreen(
    onBack: () -> Unit = {},
    onOpenCounter: ((RepExercise) -> Unit)? = null,
    onAutoOpenCamera: ((RepExercise, SetCaptureMode, Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val state by TimerService.state.collectAsStateWithLifecycle()

    var work by rememberSaveable { mutableIntStateOf(120) }
    var rest by rememberSaveable { mutableIntStateOf(180) }
    var rounds by rememberSaveable { mutableIntStateOf(5) }
    var prep by rememberSaveable { mutableIntStateOf(15) }
    var exerciseName by rememberSaveable { mutableStateOf(RepExercise.JERK.name) }
    var countedRounds by rememberSaveable(
        stateSaver = listSaver(save = { it.toList() }, restore = { it.toSet() })
    ) { mutableStateOf(emptySet<Int>()) }
    val exercise = RepExercise.entries.firstOrNull { it.name == exerciseName }
        ?: RepExercise.JERK
    val counted = countedRounds.filter { it in 1..rounds }.toSet()

    val config = TimerConfig(
        workSec = work,
        restSec = rest,
        rounds = rounds,
        prepSec = prep,
        countRounds = counted,
        exercise = exercise
    )
    val active = state.phase == TimerPhase.PREP ||
        state.phase == TimerPhase.WORK ||
        state.phase == TimerPhase.REST

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!TimerService.start(context, config)) {
            Toast.makeText(context, "Не удалось запустить таймер", Toast.LENGTH_SHORT).show()
        }
    }

    if (active || state.phase == TimerPhase.DONE) {
        FullScreenTimer(
            state = state,
            onBack = onBack,
            onTogglePause = {
                if (state.running) TimerService.pause(context) else TimerService.resume(context)
            },
            onComplete = { TimerService.complete(context) },
            onStop = { TimerService.stop(context) },
            onRestart = {
                val plan = state.plan
                if (plan == null) {
                    start()
                } else if (!TimerService.startPlan(
                        context,
                        plan,
                        state.workoutDraftJson
                    )
                ) {
                    Toast.makeText(
                        context,
                        "Не удалось запустить таймер",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onOpenCounter = onOpenCounter,
            onAutoOpenCamera = onAutoOpenCamera
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Таймер", style = MaterialTheme.typography.headlineMedium)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimerConfigurator(
                        work = work,
                        rest = rest,
                        rounds = rounds,
                        prep = prep,
                        onWork = { work = (work + it).coerceIn(15, 900) },
                        onRest = { rest = (rest + it).coerceIn(15, 900) },
                        onRounds = { rounds = (rounds + it).coerceIn(1, 30) },
                        onPrep = { prep = (prep + it).coerceIn(0, 120) },
                        totalSec = config.totalSec
                    )

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = { start() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Начать", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(20.dp)) {
                    SectionTitle("Какие подходы снимать")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Отметьте раунды, которые посчитает камера. На старте подхода " +
                            "на экране таймера появится кнопка «Считать».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Упражнение", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        RepExercise.entries.forEach { item ->
                            FilterChip(
                                selected = exercise == item,
                                onClick = { exerciseName = item.name },
                                label = { Text(item.short) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Раунды", style = MaterialTheme.typography.labelLarge)
                        Row {
                            TextButton(
                                onClick = { countedRounds = (1..rounds).toSet() }
                            ) { Text("Все") }
                            TextButton(
                                onClick = { countedRounds = emptySet() }
                            ) { Text("Сброс") }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        (1..rounds).forEach { round ->
                            FilterChip(
                                selected = round in counted,
                                onClick = {
                                    countedRounds = if (round in countedRounds) {
                                        countedRounds - round
                                    } else {
                                        countedRounds + round
                                    }
                                },
                                label = { Text("$round") }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (counted.isEmpty()) {
                            "Съёмка выключена"
                        } else {
                            "Снимаем: ${counted.sorted().joinToString(", ")} · ${exercise.label}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("Как это работает")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Таймер переживает сворачивание приложения: работает в фоне и показывает " +
                            "раунд, фазу и остаток времени в уведомлении. Звук и вибрация отмечают " +
                            "смену работы и отдыха.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerConfigurator(
    work: Int,
    rest: Int,
    rounds: Int,
    prep: Int,
    onWork: (Int) -> Unit,
    onRest: (Int) -> Unit,
    onRounds: (Int) -> Unit,
    onPrep: (Int) -> Unit,
    totalSec: Int
) {
    Column(Modifier.fillMaxWidth()) {
        StepRow(
            label = "Работа",
            value = formatClock(work),
            onMinus = { onWork(-15) },
            onPlus = { onWork(15) }
        )
        StepRow(
            label = "Отдых",
            value = formatClock(rest),
            onMinus = { onRest(-15) },
            onPlus = { onRest(15) }
        )
        StepRow(
            label = "Раунды",
            value = "$rounds",
            onMinus = { onRounds(-1) },
            onPlus = { onRounds(1) }
        )
        StepRow(
            label = "Подготовка",
            value = formatClock(prep),
            onMinus = { onPrep(-5) },
            onPlus = { onPrep(5) }
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Общее время",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatClock(totalSec),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StepRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onMinus) {
            Text("−", style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.width(84.dp),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onPlus) {
            Text("+", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
