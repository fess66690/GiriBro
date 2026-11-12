package ru.petproject.giribro.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.data.WorkoutCardContent
import ru.petproject.giribro.ui.cards.WorkoutCardRenderer
import ru.petproject.giribro.ui.cards.WorkoutCardSharing

/** Просмотр и шаринг готовой PNG-карточки тренировки. */
@Composable
fun WorkoutCardDialog(
    workout: Workout,
    onDismiss: () -> Unit
) {
    val card = remember(workout) { WorkoutCardContent.from(workout) }
    val bitmap by produceState<Bitmap?>(initialValue = null, card) {
        value = withContext(Dispatchers.Default) {
            WorkoutCardRenderer.render(card)
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val image = bitmap
        if (uri != null && image != null) {
            saving = true
            scope.launch {
                val saved = runCatching {
                    withContext(Dispatchers.IO) {
                        WorkoutCardSharing.saveToDocument(context, image, uri)
                    }
                }.getOrDefault(false)
                saving = false
                Toast.makeText(
                    context,
                    if (saved) "Карточка сохранена" else "Не удалось сохранить карточку",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun share() {
        val image = bitmap
        if (image == null || sharing) return
        sharing = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    WorkoutCardSharing.shareIntent(context, image, card.fileName)
                }
            }
            sharing = false
            result.onSuccess { intent ->
                runCatching {
                    context.startActivity(Intent.createChooser(intent, "Поделиться карточкой"))
                }.onFailure {
                    Toast.makeText(
                        context,
                        "Не удалось открыть меню «Поделиться»",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure {
                Toast.makeText(
                    context,
                    "Не удалось подготовить карточку",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Карточка тренировки",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "PNG · 1080×1350",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Закрыть")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 5f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0F110D)),
                    contentAlignment = Alignment.Center
                ) {
                    val image = bitmap
                    if (image == null) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    } else {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = "Фото-карточка тренировки",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { share() },
                        modifier = Modifier.weight(1f),
                        enabled = bitmap != null && !sharing
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(if (sharing) "Готовим…" else "Поделиться", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { saveLauncher.launch(card.fileName) },
                        modifier = Modifier.weight(1f),
                        enabled = bitmap != null && !saving
                    ) {
                        Icon(
                            Icons.Outlined.Save,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(if (saving) "Сохраняем…" else "Сохранить", maxLines = 1)
                    }
                }
            }
        }
    }
}
