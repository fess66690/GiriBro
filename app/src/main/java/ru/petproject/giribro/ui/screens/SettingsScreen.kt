package ru.petproject.giribro.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.petproject.giribro.AppViewModel
import ru.petproject.giribro.BuildConfig
import ru.petproject.giribro.data.DiaryBackupJson
import ru.petproject.giribro.data.NormAgeGroup
import ru.petproject.giribro.data.NormBook
import ru.petproject.giribro.data.NormGender
import ru.petproject.giribro.data.ThemeMode
import ru.petproject.giribro.data.asBodyNumber
import ru.petproject.giribro.ui.components.SectionTitle
import ru.petproject.giribro.util.pluralRu
import ru.petproject.giribro.util.ruDayMonth
import ru.petproject.giribro.vision.FixationPreset
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: (() -> Unit)? = null,
    onOpenCameraCount: (() -> Unit)? = null
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val backupState by vm.backupState.collectAsStateWithLifecycle()
    val backupBusy by vm.backupBusy.collectAsStateWithLifecycle()
    val pendingImport by vm.pendingImport.collectAsStateWithLifecycle()
    val passwordRequired by vm.backupPasswordRequired.collectAsStateWithLifecycle()
    val passwordError by vm.backupPasswordError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var importPassword by remember { mutableStateOf("") }

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
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun requestAutoBackupPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val autoFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            vm.enableAutoBackup(uri)
            requestAutoBackupPermission()
        }
    }

    LaunchedEffect(passwordRequired) {
        if (passwordRequired) importPassword = ""
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text("Настройки", style = MaterialTheme.typography.headlineMedium)
            }
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
            SettingsCard("Счётчик повторов по камере") {
                Text(
                    "Фронтальная камера считает толчок, длинный цикл и рывок по скелету. " +
                        "В режиме подхода «Запись» видео сохраняется только в выбранную " +
                        "вами папку; в режиме «Счёт» видео не записывается.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text("Порог фиксации", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FixationPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = settings.fixationPreset == preset,
                            onClick = { vm.setFixationPreset(preset) },
                            label = { Text("${preset.label} · ${preset.seconds} с") }
                        )
                    }
                }
                Text(
                    settings.fixationPreset.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                if (onOpenCameraCount != null) {
                    OutlinedButton(onClick = onOpenCameraCount) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Открыть счётчик")
                    }
                }
            }
        }

        item {
            SettingsCard("Профиль спортсмена") {
                Text(
                    "Пол, возрастная группа и вес подставляются в нормативы ЕВСК.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text("Пол", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    NormGender.entries.forEach { gender ->
                        FilterChip(
                            selected = profile.gender == gender,
                            onClick = { vm.setAthleteGender(gender) },
                            label = { Text(gender.label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Возрастная группа", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    NormAgeGroup.entries.forEach { ageGroup ->
                        FilterChip(
                            selected = profile.ageGroup == ageGroup,
                            onClick = { vm.setAthleteAgeGroup(ageGroup) },
                            label = { Text(ageGroup.label) }
                        )
                    }
                }
                Text(
                    "У юношеской группы в таблицах показываются юношеские разряды.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                var weightText by remember(profile.weightKg) {
                    mutableStateOf(profile.weightKg?.asBodyNumber() ?: "")
                }
                val parsedWeight = NormBook.parseWeight(weightText)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { value ->
                            if (value.length <= 6 && value.all { it.isDigit() || it == ',' || it == '.' }) {
                                weightText = value
                            }
                        },
                        label = { Text("Вес, кг") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { vm.setAthleteWeight(parsedWeight) },
                        enabled = weightText.isBlank() || parsedWeight != null
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }

        item {
            SettingsCard("Резервная копия") {
                Text(
                    "Сохраните тренировки, замеры тела, профиль и настройки в JSON-файл " +
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
                    "Восстановление заменит текущий дневник, замеры, профиль и настройки " +
                        "данными из файла.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Пароль на файл", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (backupState.passwordEnabled) {
                                "Включён: новые копии шифруются AES-GCM."
                            } else {
                                "Выключен: копии сохраняются как обычный JSON."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { showPasswordDialog = true },
                        enabled = !backupBusy
                    ) {
                        Text(if (backupState.passwordEnabled) "Изменить" else "Задать")
                    }
                }
                Text(
                    "Пароль хранится на устройстве в защищённом виде, чтобы автокопия " +
                        "работала без ввода каждый раз. Для импорта на другом устройстве " +
                        "его нужно помнить: восстановить забытый пароль нельзя.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Автокопия", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (backupState.autoBackupEnabled) {
                                "Папка: ${backupState.folderName ?: "выбрана"}"
                            } else {
                                "Раз в неделю в выбранную папку."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = backupState.autoBackupEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                autoFolderLauncher.launch(null)
                            } else {
                                vm.disableAutoBackup()
                            }
                        }
                    )
                }
                if (backupState.autoBackupEnabled) {
                    val lastBackup = formatBackupDate(backupState.lastBackupAt)
                    Text(
                        if (lastBackup != null) "Последняя копия: $lastBackup"
                        else "Копия ещё не создавалась",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = vm::runAutoBackupNow,
                            enabled = !backupBusy
                        ) {
                            Text(if (backupBusy) "Сохраняем…" else "Копия сейчас")
                        }
                        OutlinedButton(
                            onClick = { autoFolderLauncher.launch(null) },
                            enabled = !backupBusy
                        ) {
                            Text("Сменить папку")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { autoFolderLauncher.launch(null) },
                        enabled = !backupBusy
                    ) {
                        Text("Выбрать папку")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Напоминание придёт, если последняя копия старше 30 дней.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsCard("О приложении") {
                Text(
                    "ГириБро · ${BuildConfig.VERSION_NAME}",
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
                        "Текущий дневник, замеры, профиль и настройки будут заменены. " +
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

    if (showPasswordDialog) {
        BackupPasswordDialog(
            hasPassword = backupState.passwordEnabled,
            onDismiss = { showPasswordDialog = false },
            onSave = { password ->
                vm.setBackupPassword(password)
                showPasswordDialog = false
            },
            onRemove = {
                vm.setBackupPassword(null)
                showPasswordDialog = false
            }
        )
    }

    if (passwordRequired) {
        AlertDialog(
            onDismissRequest = { if (!backupBusy) vm.cancelBackupPassword() },
            title = { Text("Копия защищена паролем") },
            text = {
                Column {
                    Text("Введите пароль, который был задан при сохранении копии.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text("Пароль") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError != null,
                        supportingText = {
                            passwordError?.let { Text(it) }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.submitBackupPassword(importPassword) },
                    enabled = !backupBusy
                ) {
                    Text(if (backupBusy) "Проверяем…" else "Открыть")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = vm::cancelBackupPassword,
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

@Composable
private fun BackupPasswordDialog(
    hasPassword: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRemove: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPassword) "Изменить пароль" else "Задать пароль") },
        text = {
            Column {
                Text(
                    "Новые резервные копии будут шифроваться этим паролем. " +
                        "Старые файлы останутся как были."
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = { Text("Пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = repeated,
                    onValueChange = {
                        repeated = it
                        error = null
                    },
                    label = { Text("Повторите пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Не короче 6 символов. Забытый пароль восстановить нельзя.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasPassword) {
                    TextButton(onClick = onRemove) { Text("Убрать пароль") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        password.length < 6 -> error = "Пароль должен быть не короче 6 символов."
                        password != repeated -> error = "Пароли не совпадают."
                        else -> onSave(password)
                    }
                }
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

private fun formatBackupDate(at: Long): String? =
    at.takeIf { it > 0L }?.let {
        Instant.ofEpochMilli(it)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .ruDayMonth()
    }
