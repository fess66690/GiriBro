package ru.petproject.giribro.logging

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import ru.petproject.giribro.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Контакты автора, показываемые в настройках. */
object AuthorContacts {
    const val EMAIL = "fess66690@yandex.ru"
    const val TELEGRAM_URL = "https://t.me/Vany_Solncev"

    fun emailIntent(): Intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL")).apply {
        putExtra(Intent.EXTRA_SUBJECT, "ГириБро — вопрос разработчику")
    }

    fun telegramIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL))
}

/** Подготовка письма с техническими логами для системного почтового клиента. */
object LogSharing {

    private const val LOG_DIR = "logs"
    private const val MIME_ZIP = "application/zip"

    /** Имя файла, которое предлагается системному диалогу сохранения. */
    fun suggestedFileName(): String =
        "giribro-logs-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.zip"

    /**
     * Возвращает ACTION_SEND с архивом логов. Само письмо приложение не отправляет:
     * пользователь подтверждает отправку уже в выбранном почтовом клиенте.
     */
    fun sendLogsIntent(context: Context): Intent {
        val archive = createArchive(context)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            archive
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME_ZIP
            putExtra(Intent.EXTRA_EMAIL, arrayOf(AuthorContacts.EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "ГириБро ${BuildConfig.VERSION_NAME} — логи")
            putExtra(Intent.EXTRA_TEXT, emailBody())
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Логи ГириБро", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Сохраняет архив логов в файл, выбранный пользователем через SAF. */
    fun saveLogs(context: Context, target: Uri) {
        val archive = createArchive(context)
        val output = context.contentResolver.openOutputStream(target)
            ?: throw IOException("Не удалось открыть выбранный файл")
        output.use { stream -> archive.inputStream().use { it.copyTo(stream) } }
    }

    /** Текст письма: диагностика видна даже если архив не открыт. */
    fun emailBody(): String = buildString {
        append("Логи приложения ГириБро.\n")
        append("Версия: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
        append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        append("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        append("Отправьте письмо без изменений — логи приложены архивом.")
    }

    private fun createArchive(context: Context): File {
        val dir = File(context.cacheDir, LOG_DIR).apply { mkdirs() }
        val archive = File(dir, suggestedFileName())
        val files = AppLog.logFiles()
        ZipOutputStream(FileOutputStream(archive)).use { output ->
            var added = false
            files.filter { it.exists() }.forEach { file ->
                output.putNextEntry(ZipEntry(file.name))
                val text = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
                output.write(LogSanitizer.clean(text).toByteArray(Charsets.UTF_8))
                output.closeEntry()
                added = true
            }
            if (!added) {
                output.putNextEntry(ZipEntry("readme.txt"))
                output.write("Логи пока пусты.".toByteArray(Charsets.UTF_8))
                output.closeEntry()
            }
        }
        return archive
    }
}
