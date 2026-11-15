package ru.petproject.giribro.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Настройки автоматического резервного копирования. */
data class BackupState(
    val autoBackupEnabled: Boolean = false,
    val treeUri: String? = null,
    val folderName: String? = null,
    val lastBackupAt: Long = 0L,
    val passwordEnabled: Boolean = false
)

/**
 * Хранит выбранную папку, дату последней копии и пароль для автокопий.
 *
 * Пароль не лежит в SharedPreferences открытым текстом: он зашифрован ключом
 * Android Keystore. Это позволяет делать автокопии без запроса пароля каждый раз.
 * На другом устройстве этот пароль не восстановить — для импорта его нужно знать.
 */
class BackupRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("giribro_backup", Context.MODE_PRIVATE)
    private val passwordStore = BackupPasswordStore(appContext)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private fun load(): BackupState = BackupState(
        autoBackupEnabled = runCatching {
            prefs.getBoolean(KEY_AUTO_ENABLED, false)
        }.getOrDefault(false),
        treeUri = prefs.getString(KEY_TREE_URI, null),
        folderName = prefs.getString(KEY_FOLDER_NAME, null),
        lastBackupAt = runCatching {
            prefs.getLong(KEY_LAST_BACKUP_AT, 0L)
        }.getOrDefault(0L),
        passwordEnabled = passwordStore.hasPassword()
    )

    /**
     * Запоминает папку и забирает persistable-разрешение. Без него запись
     * в выбранную папку перестанет работать после перезапуска приложения.
     */
    fun setAutoBackup(uri: Uri, folderName: String?) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, flags)
        }
        val displayName = folderName
            ?: runCatching { DocumentFile.fromTreeUri(appContext, uri)?.name }.getOrNull()
            ?: uri.lastPathSegment

        prefs.edit()
            .putBoolean(KEY_AUTO_ENABLED, true)
            .putString(KEY_TREE_URI, uri.toString())
            .putString(KEY_FOLDER_NAME, displayName)
            .apply()
        _state.update {
            it.copy(
                autoBackupEnabled = true,
                treeUri = uri.toString(),
                folderName = displayName
            )
        }
    }

    fun disableAutoBackup() {
        prefs.edit()
            .putBoolean(KEY_AUTO_ENABLED, false)
            .remove(KEY_TREE_URI)
            .remove(KEY_FOLDER_NAME)
            .apply()
        _state.update {
            it.copy(autoBackupEnabled = false, treeUri = null, folderName = null)
        }
    }

    fun recordBackup(at: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_BACKUP_AT, at).apply()
        _state.update { it.copy(lastBackupAt = at) }
    }

    fun setPassword(password: String?): Boolean {
        passwordStore.save(password)
        val stored = if (password == null) false else passwordStore.read() != null
        _state.update { it.copy(passwordEnabled = stored) }
        return stored
    }

    fun readPassword(): String? = passwordStore.read()

    fun autoBackupTreeUri(): Uri? = _state.value.treeUri?.let(Uri::parse)

    private companion object {
        const val KEY_AUTO_ENABLED = "auto_enabled"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_FOLDER_NAME = "folder_name"
        const val KEY_LAST_BACKUP_AT = "last_backup_at"
    }
}

/**
 * Шифрует пароль ключом Android Keystore.
 *
 * Пароль нужен только для создания автокопий. Для импорта пользователь вводит
 * его вручную, поэтому переносить хранилище на другое устройство не требуется.
 */
private class BackupPasswordStore(context: Context) {

    private val prefs = context.getSharedPreferences("giribro_backup_secret", Context.MODE_PRIVATE)

    fun hasPassword(): Boolean = prefs.contains(KEY_PASSWORD)

    fun save(password: String?) {
        if (password == null) {
            prefs.edit().remove(KEY_PASSWORD).apply()
            return
        }
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs.edit().putString(KEY_PASSWORD, value).apply()
        }.onFailure {
            // Если Keystore недоступен, лучше не сохранять пароль вовсе,
            // чем оставить автокопии без защиты.
            prefs.edit().remove(KEY_PASSWORD).apply()
        }
    }

    fun read(): String? {
        val value = prefs.getString(KEY_PASSWORD, null) ?: return null
        return runCatching {
            val parts = value.split(':', limit = 2)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.secretKey
            ?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "giribro_backup_password"
        const val KEY_PASSWORD = "password"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
