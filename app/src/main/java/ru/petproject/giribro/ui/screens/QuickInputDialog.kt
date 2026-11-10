package ru.petproject.giribro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.petproject.giribro.data.QuickWorkoutParser
import ru.petproject.giribro.data.Section

@Composable
internal fun QuickInputCard(onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Быстрый ввод",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Вставьте список упражнений — подходы добавятся сами",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpen) {
                Text("Открыть")
            }
        }
    }
}

@Composable
internal fun QuickInputDialog(
    defaultWeight: Int,
    onDismiss: () -> Unit,
    onConfirm: (List<Section>) -> Unit
) {
    val example = remember(defaultWeight) {
        buildString {
            appendLine("Толчок $defaultWeight 5x10")
            appendLine("Рывок $defaultWeight 10+10+8 1:30")
            append("Махи 24 3x20 отдых 1:00")
        }
    }
    var text by rememberSaveable { mutableStateOf("") }
    var errors by remember { mutableStateOf(emptyList<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Быстрый ввод") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Одна строка — упражнение, вес и подходы. Упражнение можно указать " +
                        "один раз с двоеточием, следующие строки продолжат его.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        example,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    "Поддерживаются 5x10, 5 по 10, 10+10+8, время 1:30 или 90с, " +
                        "отдых 1:00 или 60с.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        text = example
                        errors = emptyList()
                    }
                ) {
                    Text("Вставить пример")
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        errors = emptyList()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Текст тренировки") },
                    placeholder = { Text("Толчок 16 5x10") },
                    minLines = 5,
                    maxLines = 10
                )
                if (errors.isNotEmpty()) {
                    Text(
                        "Проверьте текст:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    errors.forEach { error ->
                        Text(
                            "• $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val result = QuickWorkoutParser.parse(text)
                    if (result.isValid) {
                        onConfirm(result.sections)
                    } else {
                        errors = result.errors
                    }
                },
                enabled = text.isNotBlank()
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
