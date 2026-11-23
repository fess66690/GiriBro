package ru.petproject.giribro.timer

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import ru.petproject.giribro.R
import ru.petproject.giribro.logging.AppLog

/** Однократное воспроизведение сигнала последних пяти секунд для проверки звука. */
object CountdownSignal {

    private const val TAG = "CountdownSignal"

    /** Возвращает false, если устройство в беззвучном режиме. */
    fun play(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio?.ringerMode == AudioManager.RINGER_MODE_SILENT) return false
        runCatching {
            MediaPlayer.create(context.applicationContext, R.raw.greenwich_signal)?.apply {
                setOnCompletionListener { player -> runCatching { player.release() } }
                start()
            }
        }.onFailure { error -> AppLog.w(TAG, "Не удалось воспроизвести сигнал", error) }
        return true
    }
}
