package ru.petproject.giribro.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Короткие подсказки при первом запуске release-сборки. */
@Composable
fun OnboardingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добро пожаловать в ГириБро") },
        text = {
            Column {
                OnboardingStep("1. Дневник", "Записывайте подходы, вес и замеры. Всё хранится только на телефоне.")
                Spacer(Modifier.height(10.dp))
                OnboardingStep("2. Таймер", "Интервальный таймер и таймер по подходам работают даже при выключенном экране.")
                Spacer(Modifier.height(10.dp))
                OnboardingStep(
                    "3. Счётчик",
                    "Камера необязательна: ручные «+1 / −1» всегда доступны. Счёт тренировочный, не судейский."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Начать") }
        }
    )
}

@Composable
private fun OnboardingStep(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
