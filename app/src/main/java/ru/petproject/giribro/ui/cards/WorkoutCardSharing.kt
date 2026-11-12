package ru.petproject.giribro.ui.cards

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Подготовка PNG-карточки для системного «Поделиться» и сохранения в файл. */
object WorkoutCardSharing {

    private const val MIME_TYPE = "image/png"
    private const val CACHE_DIR = "cards"

    fun shareIntent(context: Context, bitmap: Bitmap, fileName: String): Intent {
        val uri = writeToCache(context, bitmap, fileName)
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Моя тренировка в ГириБро")
            clipData = ClipData.newUri(
                context.contentResolver,
                "Карточка тренировки",
                uri
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun saveToDocument(context: Context, bitmap: Bitmap, uri: Uri): Boolean {
        val stream = context.contentResolver.openOutputStream(uri) ?: return false
        return stream.use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun writeToCache(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IOException("Не удалось закодировать PNG")
            }
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
