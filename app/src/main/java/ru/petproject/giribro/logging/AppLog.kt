package ru.petproject.giribro.logging

import android.content.Context
import android.os.Build
import android.util.Log
import ru.petproject.giribro.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Локальный журнал технических событий.
 *
 * Пишет короткие служебные сообщения в Logcat и в файл во внутренней памяти
 * приложения. В файл попадают только диагностические сведения: версия,
 * модель устройства, сообщения об ошибках и стек-трейсы. Данные дневника,
 * профиль, заметки и настройки сюда не пишутся.
 */
object AppLog {

    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "giribro.log"
    private const val MAX_FILE_BYTES = 512 * 1024
    private const val ARCHIVE_COUNT = 3

    private val initialized = AtomicBoolean(false)
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var directory: File? = null

    /** Вызывается один раз при запуске приложения. */
    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val dir = File(context.cacheDir, LOG_DIR)
        runCatching {
            dir.mkdirs()
            directory = dir
            synchronized(lock) {
                rotateIfNeeded()
                writeEntry(
                    priority = Log.INFO,
                    tag = "GiriBro",
                    message = "Запуск ${BuildConfig.VERSION_NAME} " +
                        "(${BuildConfig.VERSION_CODE}), Android ${Build.VERSION.SDK_INT}, " +
                        Build.MODEL
                )
            }
            installCrashHandler()
        }.onFailure { error ->
            // Файловый лог недоступен — хотя бы оставим ошибку в Logcat.
            Log.w("GiriBro", "Не удалось включить файловый лог", error)
        }
    }

    fun d(tag: String, message: String) = log(Log.DEBUG, tag, message, null)

    fun i(tag: String, message: String) = log(Log.INFO, tag, message, null)

    fun w(tag: String, message: String) = log(Log.WARN, tag, message, null)

    fun w(tag: String, message: String, error: Throwable?) =
        log(Log.WARN, tag, message, error)

    fun e(tag: String, message: String) = log(Log.ERROR, tag, message, null)

    fun e(tag: String, message: String, error: Throwable?) =
        log(Log.ERROR, tag, message, error)

    /** Файлы текущего и архивных логов, от newest к oldest. */
    fun logFiles(): List<File> {
        val dir = directory ?: return emptyList()
        return buildList {
            val current = File(dir, LOG_FILE)
            if (current.exists()) add(current)
            for (index in 1..ARCHIVE_COUNT) {
                val archive = File(dir, "$LOG_FILE.$index")
                if (archive.exists()) add(archive)
            }
        }
    }

    private fun log(priority: Int, tag: String, message: String, error: Throwable?) {
        when (priority) {
            Log.DEBUG -> if (BuildConfig.DEBUG) Log.d(tag, message, error)
            Log.INFO -> if (BuildConfig.DEBUG) Log.i(tag, message, error)
            Log.WARN -> Log.w(tag, message, error)
            Log.ERROR -> Log.e(tag, message, error)
        }
        runCatching {
            synchronized(lock) {
                rotateIfNeeded()
                writeEntry(priority, tag, message, error)
            }
        }.onFailure {
            // Логирование не должно ронять приложение.
        }
    }

    private fun writeEntry(priority: Int, tag: String, message: String, error: Throwable? = null) {
        val dir = directory ?: return
        val line = buildString {
            append(timeFormat.format(Date())).append(' ')
            append(priorityMark(priority)).append('/').append(tag).append(": ")
            append(message.replace('\r', ' ').replace('\n', ' '))
            if (error != null) {
                append('\n')
                append(error.stackTraceToString().replace("\r", ""))
            }
            append('\n')
        }
        FileOutputStream(File(dir, LOG_FILE), true).use { it.write(line.toByteArray()) }
    }

    private fun priorityMark(priority: Int): Char = when (priority) {
        Log.DEBUG -> 'D'
        Log.INFO -> 'I'
        Log.WARN -> 'W'
        Log.ERROR -> 'E'
        else -> 'V'
    }

    /** Перед записью сдвигаем файлы: giribro.log → .1 → .2 → .3. */
    private fun rotateIfNeeded() {
        val dir = directory ?: return
        val current = File(dir, LOG_FILE)
        if (!current.exists() || current.length() < MAX_FILE_BYTES) return

        File(dir, "$LOG_FILE.$ARCHIVE_COUNT").takeIf { it.exists() }?.delete()
        for (index in (ARCHIVE_COUNT - 1) downTo 1) {
            val from = File(dir, "$LOG_FILE.$index")
            if (from.exists()) from.renameTo(File(dir, "$LOG_FILE.${index + 1}"))
        }
        current.renameTo(File(dir, "$LOG_FILE.1"))
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is CrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(previous))
    }

    private class CrashHandler(
        private val previous: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching {
                e("GiriBro", "Необработанное исключение в потоке ${thread.name}", error)
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
