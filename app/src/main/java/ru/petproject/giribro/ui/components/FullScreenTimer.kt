package ru.petproject.giribro.ui.components

import android.content.Context
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.petproject.giribro.timer.TimerPhase
import ru.petproject.giribro.timer.TimerState
import ru.petproject.giribro.timer.TimerStep
import ru.petproject.giribro.timer.TimerStepKind
import ru.petproject.giribro.util.formatClockPadded
import ru.petproject.giribro.util.pluralRu

private val Ink = Color(0xFF0B0B0B)
private val TimerGreen = Color(0xFF2FD06B)
private val TimerBar = Color(0xFF070707)
private val StopGray = Color(0xFF3A3A3A)

private data class TimerPalette(
    val background: Color,
    val ink: Color
)

/**
 * Большой полноэкранный таймер в стиле референса: цветной фон, огромная фаза,
 * обратный отсчёт, «далее» и чёрная панель управления.
 */
@Composable
fun FullScreenTimer(
    state: TimerState,
    onBack: () -> Unit,
    onTogglePause: () -> Unit,
    onComplete: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = when (state.phase) {
        TimerPhase.PREP -> TimerPalette(Color(0xFFF2C41B), Ink)
        TimerPhase.WORK -> TimerPalette(Color(0xFFC9F24A), Ink)
        TimerPhase.REST -> TimerPalette(Color(0xFF9FD8EA), Ink)
        TimerPhase.DONE -> TimerPalette(Color(0xFFC9F24A), Ink)
        TimerPhase.IDLE -> TimerPalette(Color(0xFFF2C41B), Ink)
    }
    val ink = palette.ink
    val done = state.phase == TimerPhase.DONE
    val manual = state.isManual
    val view = LocalView.current

    // Экран таймера должен занимать весь дисплей: убираем клавиатуру,
    // которая могла остаться открытой после ввода подхода или заметки.
    LaunchedEffect(view) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }
    val phaseTitle = when {
        done -> "Готово"
        manual -> "Задание"
        else -> state.phaseLabel
    }
    val task = state.step?.task.orEmpty().ifBlank { state.step?.label.orEmpty() }
    val detail = currentDetail(state)

    Box(
        modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ink.copy(alpha = 0.12f))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = ink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Назад",
                        color = ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = 0.12f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Свернуть",
                        tint = ink,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(0.22f))

                Text(
                    phaseTitle.uppercase(),
                    color = ink,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))

                if (manual) {
                    Text(
                        task.uppercase(),
                        color = ink,
                        fontSize = 54.sp,
                        lineHeight = 58.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        detail,
                        color = ink.copy(alpha = 0.68f),
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "ВРЕМЯ НЕ ЗАДАНО · ОТМЕТЬ ВЫПОЛНЕНИЕ",
                        color = ink.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        if (done) "00:00" else formatClockPadded(state.remainingSec),
                        color = ink,
                        fontSize = 108.sp,
                        lineHeight = 112.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-3).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        detail,
                        color = ink.copy(alpha = 0.68f),
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    if (!state.running && state.isActive) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "ПАУЗА",
                            color = ink.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }

                Spacer(Modifier.weight(0.9f))

                if (done) {
                    Text(
                        "ТРЕНИРОВКА ЗАВЕРШЕНА",
                        color = ink.copy(alpha = 0.8f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "ХОРОШАЯ РАБОТА",
                        color = ink.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    NextStepInfo(state)
                }
                Spacer(Modifier.height(22.dp))
            }

            TimerControls(
                state = state,
                onTogglePause = onTogglePause,
                onComplete = onComplete,
                onStop = onStop,
                onRestart = onRestart
            )
        }
    }
}

@Composable
private fun NextStepInfo(state: TimerState) {
    val next = state.nextStep
    if (next == null) {
        Text(
            "ДАЛЕЕ: ФИНИШ",
            color = Ink.copy(alpha = 0.8f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
        return
    }

    val title = when (next.kind) {
        TimerStepKind.PREP -> "ПОДГОТОВКА"
        TimerStepKind.WORK -> if (next.manual) "ЗАДАНИЕ" else "РАБОТА"
        TimerStepKind.REST -> "ОТДЫХ"
    }
    val duration = if (!(next.kind == TimerStepKind.WORK && next.manual) && next.seconds > 0) {
        " " + formatClockPadded(next.seconds)
    } else {
        ""
    }
    val subtitle = nextSubtitle(next)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "ДАЛЕЕ: $title$duration",
            color = Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle.uppercase(),
                color = Ink.copy(alpha = 0.62f),
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun nextSubtitle(step: TimerStep): String = when {
    step.kind == TimerStepKind.WORK && step.task.isNotBlank() -> step.task
    step.kind == TimerStepKind.WORK && step.rounds > 1 ->
        "раунд ${step.round} из ${step.rounds}"
    step.kind == TimerStepKind.REST && step.round > 0 -> "после подхода ${step.round}"
    else -> ""
}

private fun currentDetail(state: TimerState): String {
    if (state.phase == TimerPhase.DONE) {
        return state.plan?.title?.uppercase() ?: "ТРЕНИРОВКА"
    }
    val parts = mutableListOf<String>()
    if (state.phase == TimerPhase.PREP) {
        state.plan?.title?.let(parts::add)
    } else {
        state.step?.label?.takeIf { it.isNotBlank() }?.let(parts::add)
    }
    if (state.counterLabel.isNotEmpty()) parts += state.counterLabel
    return parts.joinToString(" · ").uppercase()
}

@Composable
private fun TimerControls(
    state: TimerState,
    onTogglePause: () -> Unit,
    onComplete: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit
) {
    val done = state.phase == TimerPhase.DONE
    val manual = state.isManual
    val remaining = state.remainingWork
    val remainingLabel = pluralRu(
        remaining,
        "ОСТ. ПОДХОД",
        "ОСТ. ПОДХОДА",
        "ОСТ. ПОДХОДОВ"
    )

    Row(
        Modifier
            .fillMaxWidth()
            .background(TimerBar)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "$remaining",
                color = TimerGreen,
                fontSize = 38.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                remainingLabel,
                color = TimerGreen.copy(alpha = 0.75f),
                fontSize = 9.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                maxLines = 1
            )
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            when {
                done -> Button(
                    onClick = onRestart,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TimerGreen,
                        contentColor = Ink
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier.height(60.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ещё раз", fontWeight = FontWeight.Bold)
                }

                manual -> Button(
                    onClick = onComplete,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TimerGreen,
                        contentColor = Ink
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    modifier = Modifier.height(60.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Выполнено",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                else -> FilledIconButton(
                    onClick = onTogglePause,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = TimerGreen,
                        contentColor = Ink
                    )
                ) {
                    Icon(
                        if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.running) "Пауза" else "Продолжить",
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            FilledIconButton(
                onClick = onStop,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = StopGray,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = if (done) "Сбросить" else "Стоп",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
