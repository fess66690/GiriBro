package ru.petproject.giribro.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.petproject.giribro.data.BackupRepository
import ru.petproject.giribro.data.BodyMeasurementRepository
import ru.petproject.giribro.data.DiaryBackupJson
import ru.petproject.giribro.data.SettingsRepository
import ru.petproject.giribro.data.WorkoutRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Результат одной попытки резервного копирования. */
sealed interface BackupOutcome {
    data class Saved(val at: Long) : BackupOutcome
    data object Skipped : BackupOutcome
    data object NotConfigured : BackupOutcome
    data class Failed(val message: String) : BackupOutcome
}

/**
 * Собирает и записывает резервные копии.
 *
 * Автокопия перезаписывает один файл в выбранной пользователем папке. Так на
 * устройстве не копится бесконечная история, а облачный провайдер (если папка
 * находится в нём) сам хранит версии файла.
 */
class BackupService(
    context: Context,
    private val workouts: WorkoutRepository,
    private val bodyMeasurements: BodyMeasurementRepository,
    private val settings: SettingsRepository,
    private val backups: BackupRepository
) {

    private val appContext = context.applicationContext

    suspend fun autoBackup(force: Boolean = false, now: Long = System.currentTimeMillis()): BackupOutcome =
        withContext(Dispatchers.IO) {
            val state = backups.state.value
            if (!state.autoBackupEnabled) return@withContext BackupOutcome.NotConfigured
            val treeUri = state.treeUri?.let(Uri::parse) ?: return@withContext BackupOutcome.NotConfigured
            if (!force && state.lastBackupAt > 0L &&
                now - state.lastBackupAt < AUTO_INTERVAL_MS
            ) {
                return@withContext BackupOutcome.Skipped
            }

            val password = if (state.passwordEnabled) {
                backups.readPassword() ?: return@withContext BackupOutcome.Failed(
                    "Не удалось прочитать пароль автокопии. Задайте его заново в настройках."
                )
            } else {
                null
            }

            try {
                val text = encode(password)
                writeTreeFile(treeUri, AUTO_FILE_NAME, text)
                backups.recordBackup(now)
                BackupOutcome.Saved(now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupOutcome.Failed(
                    e.message?.takeIf { it.isNotBlank() }
                        ?: "Не удалось сохранить автокопию."
                )
            }
        }

    /** Ручная копия в конкретный файл, выбранный пользователем. */
    suspend fun manualBackup(
        uri: Uri,
        password: String?,
        now: Long = System.currentTimeMillis()
    ): BackupOutcome = withContext(Dispatchers.IO) {
        try {
            val text = encode(password)
            writeDocument(uri, text)
            backups.recordBackup(now)
            BackupOutcome.Saved(now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupOutcome.Failed(
                e.message?.takeIf { it.isNotBlank() }
                    ?: "Не удалось сохранить резервную копию."
            )
        }
    }

    /** Пора напомнить о копии, если последняя старше 30 дней. */
    suspend fun shouldRemind(now: Long = System.currentTimeMillis()): Boolean =
        withContext(Dispatchers.IO) {
            val last = backups.state.value.lastBackupAt
            if (last > 0L) {
                now - last >= REMINDER_AGE_MS
            } else {
                // Ни одной копии ещё не было. Напоминаем, когда дневник
                // существует достаточно долго, а не сразу после установки.
                val first = workouts.all().minOfOrNull { it.createdAt } ?: return@withContext false
                now - first >= REMINDER_AGE_MS
            }
        }

    fun formatBackupDate(at: Long): String? =
        if (at <= 0L) null else DATE_FORMAT.format(
            Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        )

    private suspend fun encode(password: String?): String = DiaryBackupJson.encode(
        workouts = workouts.all(),
        settings = settings.state.value,
        bodyMeasurements = bodyMeasurements.all(),
        profile = settings.profile.value,
        password = password
    )

    private fun writeTreeFile(treeUri: Uri, fileName: String, text: String) {
        val tree = DocumentFile.fromTreeUri(appContext, treeUri)
            ?: error("Не удалось открыть выбранную папку.")
        if (!tree.canWrite()) {
            error("Нет доступа к выбранной папке. Выберите её заново.")
        }

        val file = tree.findFile(fileName)
            ?: tree.createFile(DiaryBackupJson.MIME_TYPE, fileName)
            ?: error("Не удалось создать файл в выбранной папке.")
        writeDocument(file.uri, text)
    }

    private fun writeDocument(uri: Uri, text: String) {
        val bytes = (text + "\n").toByteArray(Charsets.UTF_8)
        val output = appContext.contentResolver.openOutputStream(uri, "wt")
            ?: appContext.contentResolver.openOutputStream(uri, "w")
            ?: error("Не удалось открыть файл для записи.")
        output.use { it.write(bytes) }
    }

    companion object {
        const val AUTO_FILE_NAME = "giribro-autobackup.json"
        const val AUTO_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        const val REMINDER_AGE_MS = 30L * 24 * 60 * 60 * 1000
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy")
    }
}
