package ru.petproject.giribro.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.time.LocalDate
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Содержимое резервной копии дневника. */
data class DiaryBackup(
    val exportedAt: Long,
    val workouts: List<Workout>,
    val settings: Settings?,
    val bodyMeasurements: List<BodyMeasurement> = emptyList(),
    val profile: AthleteProfile? = null
)

/** Формат файла резервной копии: версионированный JSON без привязки к Room. */
object DiaryBackupJson {

    const val FORMAT = "giribro-diary"

    /**
     * v2 добавила замеры тела, v3 — профиль спортсмена и необязательное
     * шифрование AES-GCM. Копии v1/v2 читаются без изменений.
     */
    const val VERSION = 3
    const val MIME_TYPE = "application/json"
    const val MAX_FILE_BYTES = 10 * 1024 * 1024

    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val MIN_PBKDF2_ITERATIONS = 10_000
    private const val MAX_PBKDF2_ITERATIONS = 1_000_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    fun encode(
        workouts: List<Workout>,
        settings: Settings,
        bodyMeasurements: List<BodyMeasurement> = emptyList(),
        exportedAt: Long = System.currentTimeMillis(),
        profile: AthleteProfile? = null,
        password: String? = null
    ): String {
        val plain = encodePlain(
            workouts = workouts,
            settings = settings,
            bodyMeasurements = bodyMeasurements,
            exportedAt = exportedAt,
            profile = profile
        )
        return if (password.isNullOrEmpty()) plain else encrypt(plain, password)
    }

    private fun encodePlain(
        workouts: List<Workout>,
        settings: Settings,
        bodyMeasurements: List<BodyMeasurement>,
        exportedAt: Long,
        profile: AthleteProfile?
    ): String {
        val items = JSONArray()
        workouts
            .sortedWith(compareByDescending<Workout> { it.date }.thenByDescending { it.createdAt })
            .forEach { workout ->
                items.put(
                    JSONObject()
                        .put("date", workout.date.toString())
                        .put("createdAt", workout.createdAt)
                        .put("note", workout.note)
                        .put("sections", JSONArray(WorkoutJson.encode(workout.sections)))
                )
            }

        val measurements = JSONArray()
        bodyMeasurements
            .sortedWith(
                compareByDescending<BodyMeasurement> { it.date }
                    .thenByDescending { it.createdAt }
            )
            .forEach { measurement ->
                measurements.put(
                    JSONObject()
                        .put("date", measurement.date.toString())
                        .put("weightKg", measurement.weightKg)
                        .put("createdAt", measurement.createdAt)
                        .put("note", measurement.note)
                        .apply {
                            measurement.waistCm?.let { put("waistCm", it) }
                            measurement.chestCm?.let { put("chestCm", it) }
                            measurement.hipsCm?.let { put("hipsCm", it) }
                            measurement.armCm?.let { put("armCm", it) }
                            measurement.thighCm?.let { put("thighCm", it) }
                        }
                )
            }

        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
            .put("workouts", items)
            .put("bodyMeasurements", measurements)
            .put(
                "settings",
                JSONObject()
                    .put("themeMode", settings.themeMode.name)
                    .put("weeklyGoal", settings.weeklyGoal)
                    .put("defaultWeight", settings.defaultWeight)
                    .put("statsPeriod", settings.statsPeriod.name)
            )
            .apply {
                profile?.let { put("profile", encodeProfile(it)) }
            }
            .toString(2)
    }

    /**
     * Читает копию целиком и проверяет каждую тренировку и каждый замер до того,
     * как приложение заменит текущие данные. При любой проблеме бросает
     * [BackupFormatException].
     *
     * Для зашифрованной копии нужно передать [password]. Наличие защиты можно
     * проверить заранее через [isEncrypted], чтобы спросить пароль до чтения.
     */
    fun decode(raw: String, password: String? = null): DiaryBackup {
        val text = raw.removePrefix("\uFEFF").trim()
        if (text.isEmpty()) throw BackupFormatException("Файл резервной копии пуст.")

        val outer = parseRoot(text)
        if (!outer.optBoolean("encrypted", false)) {
            return decodeRoot(outer)
        }

        if (password.isNullOrEmpty()) {
            throw BackupFormatException("Копия защищена паролем.")
        }
        val inner = parseRoot(decrypt(outer, password))
        if (inner.optBoolean("encrypted", false)) {
            throw BackupFormatException("Копия повреждена: вложенное шифрование.")
        }
        return decodeRoot(inner)
    }

    fun isEncrypted(raw: String): Boolean {
        val text = raw.removePrefix("\uFEFF").trim()
        if (text.isEmpty()) return false
        return runCatching {
            val root = JSONObject(text)
            root.optString("format") == FORMAT && root.optBoolean("encrypted", false)
        }.getOrDefault(false)
    }

    private fun parseRoot(text: String): JSONObject {
        val root = runCatching { JSONObject(text) }.getOrElse {
            throw BackupFormatException("Файл не похож на резервную копию ГириБро.")
        }
        if (root.optString("format") != FORMAT) {
            throw BackupFormatException("Это не резервная копия ГириБро.")
        }
        return root
    }

    private fun decodeRoot(root: JSONObject): DiaryBackup {
        val version = root.optInt("version", -1)
        when {
            version <= 0 -> throw BackupFormatException("В копии не указана версия формата.")
            version > VERSION -> throw BackupFormatException(
                "Копия создана более новой версией приложения. Обновите ГириБро."
            )
        }

        val items = root.optJSONArray("workouts")
            ?: throw BackupFormatException("В копии нет списка тренировок.")

        val workouts = buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i)
                    ?: throw BackupFormatException("В копии повреждена тренировка №${i + 1}.")

                val date = runCatching {
                    LocalDate.parse(item.optString("date"))
                }.getOrElse {
                    throw BackupFormatException(
                        "В тренировке №${i + 1} указана некорректная дата."
                    )
                }

                val sectionsJson = item.optJSONArray("sections")
                    ?: throw BackupFormatException("В тренировке от $date нет упражнений.")
                val sections = runCatching {
                    WorkoutJson.decode(sectionsJson.toString(), strict = true)
                }.getOrElse { error ->
                    throw BackupFormatException(
                        "Тренировка от $date повреждена: ${error.message ?: "неизвестная ошибка"}."
                    )
                }

                val workout = Workout(
                    date = date,
                    sections = sections,
                    note = item.optString("note", ""),
                    createdAt = item.optLong("createdAt", 0L)
                )
                // Длину заметки при импорте не ограничиваем: копия могла быть
                // сделана старой версией, где лимит был другим.
                WorkoutValidator.validate(workout.sections, note = "")?.let { problem ->
                    throw BackupFormatException("Тренировка от $date: $problem")
                }
                add(workout)
            }
        }

        val measurementItems = when {
            !root.has("bodyMeasurements") || root.isNull("bodyMeasurements") -> JSONArray()
            else -> root.optJSONArray("bodyMeasurements")
                ?: throw BackupFormatException("В копии повреждён список замеров.")
        }
        val bodyMeasurements = buildList {
            for (i in 0 until measurementItems.length()) {
                val item = measurementItems.optJSONObject(i)
                    ?: throw BackupFormatException("В копии повреждён замер №${i + 1}.")

                val date = runCatching {
                    LocalDate.parse(item.optString("date"))
                }.getOrElse {
                    throw BackupFormatException(
                        "В замере №${i + 1} указана некорректная дата."
                    )
                }

                val measurement = BodyMeasurement(
                    date = date,
                    weightKg = item.optDouble("weightKg", Double.NaN),
                    waistCm = item.optionalSize("waistCm", date),
                    chestCm = item.optionalSize("chestCm", date),
                    hipsCm = item.optionalSize("hipsCm", date),
                    armCm = item.optionalSize("armCm", date),
                    thighCm = item.optionalSize("thighCm", date),
                    note = item.optString("note", ""),
                    createdAt = item.optLong("createdAt", 0L)
                )
                BodyMeasurementValidator.validate(measurement)?.let { problem ->
                    throw BackupFormatException("Замер от $date: $problem")
                }
                add(measurement)
            }
        }

        return DiaryBackup(
            exportedAt = root.optLong("exportedAt", 0L),
            workouts = workouts,
            settings = root.optJSONObject("settings")?.let(::decodeSettings),
            bodyMeasurements = bodyMeasurements,
            profile = root.optJSONObject("profile")?.let(::decodeProfile)
        )
    }

    fun suggestedFileName(today: LocalDate = LocalDate.now()): String =
        "giribro-backup-$today.json"

    private fun encodeProfile(profile: AthleteProfile): JSONObject = JSONObject()
        .put("gender", profile.gender.name)
        .put("ageGroup", profile.ageGroup.name)
        .apply {
            profile.weightKg?.let { put("weightKg", it) }
        }

    private fun decodeProfile(obj: JSONObject): AthleteProfile {
        val gender = enumOrDefault(obj.optString("gender"), NormGender.MALE)
        val ageGroup = enumOrDefault(obj.optString("ageGroup"), NormAgeGroup.ADULT)
        val weight = if (!obj.has("weightKg") || obj.isNull("weightKg")) {
            null
        } else {
            val value = obj.optDouble("weightKg", Double.NaN)
            if (!value.isFinite() || value <= 0.0 || value > 400.0) {
                throw BackupFormatException("В профиле указан некорректный вес.")
            }
            value
        }
        return AthleteProfile(gender = gender, ageGroup = ageGroup, weightKg = weight)
    }

    private fun encrypt(plain: String, password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("encrypted", true)
            .put("cipher", CIPHER)
            .put("kdf", KDF)
            .put("iterations", PBKDF2_ITERATIONS)
            .put("salt", base64(salt))
            .put("iv", base64(cipher.iv))
            .put("payload", base64(encrypted))
            .toString(2)
    }

    private fun decrypt(envelope: JSONObject, password: String): String {
        if (envelope.optString("cipher") != CIPHER) {
            throw BackupFormatException("Копия зашифрована неподдерживаемым способом.")
        }
        if (envelope.optString("kdf") != KDF) {
            throw BackupFormatException("Копия использует неподдерживаемый пароль.")
        }
        val iterations = envelope.optInt("iterations", 0)
        if (iterations !in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) {
            throw BackupFormatException("Копия повреждена: неверные параметры пароля.")
        }

        val salt = decodeBase64(envelope.optString("salt"), "соль")
        val iv = decodeBase64(envelope.optString("iv"), "вектор")
        val payload = decodeBase64(envelope.optString("payload"), "данные")
        if (salt.size !in 8..64 || iv.size !in 12..16) {
            throw BackupFormatException("Копия повреждена: неверные параметры шифрования.")
        }

        val key = deriveKey(password, salt, iterations)
        return try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw BackupPasswordException("Неверный пароль или копия повреждена.")
        } catch (e: Exception) {
            throw BackupFormatException("Не удалось расшифровать копию. Проверьте пароль.")
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKey {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(KDF)
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun base64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun decodeBase64(value: String, what: String): ByteArray {
        if (value.isBlank()) {
            throw BackupFormatException("В копии отсутствует $what.")
        }
        return runCatching { Base64.getDecoder().decode(value) }.getOrElse {
            throw BackupFormatException("Копия повреждена: не удалось прочитать $what.")
        }
    }

    private fun JSONObject.optionalSize(key: String, date: LocalDate): Double? {
        if (!has(key) || isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        if (!value.isFinite()) {
            throw BackupFormatException("В замере от $date указано некорректное значение обхвата.")
        }
        return value
    }

    private fun decodeSettings(obj: JSONObject): Settings = Settings(
        themeMode = enumOrDefault(obj.optString("themeMode"), ThemeMode.DARK),
        weeklyGoal = obj.optInt("weeklyGoal", 3).coerceIn(1, 14),
        defaultWeight = obj.optInt("defaultWeight", 24).coerceIn(4, 48),
        statsPeriod = enumOrDefault(obj.optString("statsPeriod"), StatsPeriod.MONTH)
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String, default: T): T =
        runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
}

open class BackupFormatException(message: String) : IllegalArgumentException(message)

/** Неверный пароль от зашифрованной копии. */
class BackupPasswordException(message: String) : BackupFormatException(message)
