package ru.petproject.giribro.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.petproject.giribro.AppViewModel
import ru.petproject.giribro.data.DiaryBackupJson
import ru.petproject.giribro.data.ThemeMode
import ru.petproject.giribro.ui.components.SectionTitle
import ru.petproject.giribro.util.pluralRu
import ru.petproject.giribro.util.ruDayMonth
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val backupBusy by vm.backupBusy.collectAsStateWithLifecycle()
    val pendingImport by vm.pendingImport.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DiaryBackupJson.MIME_TYPE)
    ) { uri ->
        uri?.let(vm::exportBackup)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(vm::loadBackup)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        }

        item {
            SettingsCard("Оформление") {
                listOf(
                    ThemeMode.SYSTEM to "Как в системе",
                    ThemeMode.DARK to "Тёмная",
                    ThemeMode.LIGHT to "Светлая"
                ).forEach { (mode, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.setTheme(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.themeMode == mode,
                            onClick = { vm.setTheme(mode) }
                        )
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        item {
            SettingsCard("Цель на неделю") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Тренировок в неделю",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.setWeeklyGoal(settings.weeklyGoal - 1) }) {
                        Text("−", style = MaterialTheme.typography.headlineMedium)
                    }
                    Text(
                        "${settings.weeklyGoal}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { vm.setWeeklyGoal(settings.weeklyGoal + 1) }) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Text(
                    "Серия считается по неделям, где выполнено не меньше этой цели.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsCard("Основной вес гирь") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(12, 16, 20, 24, 28, 32).forEach { weight ->
                        FilterChip(
                            selected = settings.defaultWeight == weight,
                            onClick = { vm.setDefaultWeight(weight) },
                            label = { Text("$weight кг") }
                        )
                    }
                }
                Text(
                    "Подставляется по умолчанию при добавлении подхода.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsCard("Резервная копия") {
                Text(
                    "Сохраните тренировки, замеры тела и настройки в JSON-файл " +
                        "или восстановите их из копии.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            exportLauncher.launch(DiaryBackupJson.suggestedFileName())
                        },
                        enabled = !backupBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (backupBusy) "Подождите…" else "Сохранить копию")
                    }
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf(
                                    DiaryBackupJson.MIME_TYPE,
                                    "text/plain",
                                    "application/octet-stream"
                                )
                            )
                        },
                        enabled = !backupBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Восстановить")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Восстановление заменит текущий дневник, замеры и настройки данными из файла.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsCard("О приложении") {
                Text(
                    "ГириБро · 0.1.0",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Гиревой дневник: тоннаж, подъёмы, серия, интервальный таймер, " +
                        "вес и замеры тела, статистика, нормативы ЕВСК, календарь ВФГС " +
                        "и фото-карточки тренировок. Данные хранятся локально на устройстве.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(
                    "Чего пока нет: облачной синхронизации, ленты товарищей, импорта " +
                        "из Polar/GPX и генератора программ. Это следующий этап.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsCard("Документы") {
                TextButton(
                    onClick = { showPrivacyPolicy = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Политика конфиденциальности")
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text("Очистить дневник", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    pendingImport?.let { backup ->
        val count = backup.workouts.size
        val word = pluralRu(count, "тренировка", "тренировки", "тренировок")
        val bodyCount = backup.bodyMeasurements.size
        val bodyWord = pluralRu(bodyCount, "замер", "замера", "замеров")
        val savedAt = backup.exportedAt
            .takeIf { it > 0 }
            ?.let {
                Instant.ofEpochMilli(it)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .ruDayMonth()
            }
        val source = if (savedAt != null) ", сохранена $savedAt" else ""
        AlertDialog(
            onDismissRequest = { if (!backupBusy) vm.cancelImport() },
            title = { Text("Восстановить копию?") },
            text = {
                Text(
                    "В копии: $count $word и $bodyCount $bodyWord$source. " +
                        "Текущий дневник, замеры и настройки будут заменены. " +
                        "Отменить это действие нельзя."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.confirmImport() },
                    enabled = !backupBusy
                ) {
                    Text(if (backupBusy) "Восстанавливаем…" else "Восстановить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { vm.cancelImport() },
                    enabled = !backupBusy
                ) { Text("Отмена") }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить дневник?") },
            text = {
                Text(
                    "Все тренировки и замеры тела будут удалены с этого устройства. " +
                        "Отменить действие нельзя."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearAll()
                        showClearDialog = false
                    }
                ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showPrivacyPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(title)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
