package ru.petproject.giribro.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.petproject.giribro.vision.FixationPreset
import java.time.LocalDate

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Период, за который считается статистика. */
enum class StatsPeriod(
    val label: String,
    val caption: String?,
    private val days: Int?
) {
    WEEK("7 дней", "за 7 дней", 7),
    MONTH("30 дней", "за 30 дней", 30),
    QUARTER("3 мес.", "за 3 месяца", 90),
    YEAR("Год", "за год", 365),
    ALL("Всё", null, null);

    /** Начало периода включительно; null — без ограничения по дате. */
    fun startDate(today: LocalDate): LocalDate? =
        days?.let { today.minusDays((it - 1).toLong()) }
}

data class Settings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val weeklyGoal: Int = 3,
    val defaultWeight: Int = 24,
    val statsPeriod: StatsPeriod = StatsPeriod.MONTH,
    val fixationPreset: FixationPreset = FixationPreset.COMPETITION,
    /** Быстрые значения веса/повторов/времени для каждого гиревого упражнения. */
    val quickValues: Map<SectionType, SetQuickValues> = SetQuickValues.defaults,
    /** Названия упражнений ОФП, которые вводил пользователь. */
    val gppExercises: List<String> = emptyList()
)

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("giribro_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<AthleteProfile> = _profile.asStateFlow()

    var demoSeeded: Boolean
        get() = runCatching { prefs.getBoolean(KEY_DEMO_SEEDED, false) }.getOrDefault(false)
        set(value) = prefs.edit().putBoolean(KEY_DEMO_SEEDED, value).apply()

    /** Папка SAF для видео подходов; не попадает в резервные копии. */
    var videoFolderUri: String?
        get() = runCatching { prefs.getString(KEY_VIDEO_FOLDER_URI, null) }.getOrNull()
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_VIDEO_FOLDER_URI)
            else editor.putString(KEY_VIDEO_FOLDER_URI, value)
            editor.apply()
        }

    private fun load(): Settings = Settings(
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.DARK.name)!!)
        }.getOrDefault(ThemeMode.DARK),
        weeklyGoal = readInt(KEY_WEEKLY_GOAL, 3).coerceIn(1, 14),
        defaultWeight = readInt(KEY_DEFAULT_WEIGHT, 24).coerceIn(4, 48),
        statsPeriod = runCatching {
            StatsPeriod.valueOf(prefs.getString(KEY_STATS_PERIOD, StatsPeriod.MONTH.name)!!)
        }.getOrDefault(StatsPeriod.MONTH),
        fixationPreset = FixationPreset.fromName(
            runCatching { prefs.getString(KEY_FIXATION_PRESET, null) }.getOrNull()
        ),
        quickValues = QUICK_EXERCISES.associateWith { type ->
            loadQuickValues(type)
        },
        gppExercises = SetQuickValues.sanitizeNames(
            runCatching {
                prefs.getStringSet(KEY_GPP_EXERCISES, emptySet()).orEmpty()
            }.getOrDefault(emptySet()).toList()
        )
    )

    private fun loadQuickValues(type: SectionType): SetQuickValues {
        val fallback = SetQuickValues.forType(type)
        val raw = runCatching {
            prefs.getString(KEY_QUICK_PREFIX + type.name, null)
        }.getOrNull() ?: return fallback
        val parts = raw.split('|')
        if (parts.size != 3) return fallback
        fun parse(values: String, limit: Int): List<Int> = values
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..limit }
            .distinct()
            .take(SetQuickValues.MAX_VALUES)
        return SetQuickValues.sanitize(
            SetQuickValues(
                weights = parse(parts[0], 200),
                reps = parse(parts[1], 999),
                durationsSec = parse(parts[2], 86_400)
            )
        )
    }

    private fun encodeQuickValues(values: SetQuickValues): String = listOf(
        values.weights.joinToString(","),
        values.reps.joinToString(","),
        values.durationsSec.joinToString(",")
    ).joinToString("|")

    /** Сохраняет быстрые значения для одного упражнения. */
    fun setQuickValues(type: SectionType, values: SetQuickValues) {
        val safe = SetQuickValues.sanitize(values)
        val editor = prefs.edit()
            .putString(KEY_QUICK_PREFIX + type.name, encodeQuickValues(safe))
        _state.value = _state.value.copy(
            quickValues = _state.value.quickValues + (type to safe)
        )
        editor.apply()
    }

    /** Запоминает введённое название упражнения ОФП для подсказок. */
    fun addGppExercise(name: String) {
        val value = name.trim().take(SetQuickValues.MAX_NAME_LENGTH)
        if (value.isEmpty()) return
        val current = _state.value.gppExercises
        if (current.any { it.equals(value, ignoreCase = true) }) return
        val updated = (listOf(value) + current).take(SetQuickValues.MAX_GPP_EXERCISES)
        prefs.edit().putStringSet(KEY_GPP_EXERCISES, updated.toSet()).apply()
        _state.value = _state.value.copy(gppExercises = updated)
    }

    private fun loadProfile(): AthleteProfile {
        val gender = runCatching {
            NormGender.valueOf(prefs.getString(KEY_ATHLETE_GENDER, NormGender.MALE.name)!!)
        }.getOrDefault(NormGender.MALE)
        val ageGroup = runCatching {
            NormAgeGroup.valueOf(
                prefs.getString(KEY_ATHLETE_AGE_GROUP, NormAgeGroup.ADULT.name)!!
            )
        }.getOrDefault(NormAgeGroup.ADULT)
        val rawWeight = runCatching { prefs.getFloat(KEY_ATHLETE_WEIGHT, Float.NaN) }
            .getOrDefault(Float.NaN)
        val weight = rawWeight.toDouble().takeIf { it.isFinite() && it > 0.0 }
            ?.coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG)
        return AthleteProfile(gender = gender, ageGroup = ageGroup, weightKg = weight)
    }

    private fun readInt(key: String, defaultValue: Int): Int =
        runCatching { prefs.getInt(key, defaultValue) }.getOrDefault(defaultValue)

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun setWeeklyGoal(goal: Int) {
        val value = goal.coerceIn(1, 14)
        prefs.edit().putInt(KEY_WEEKLY_GOAL, value).apply()
        _state.value = _state.value.copy(weeklyGoal = value)
    }

    fun setDefaultWeight(weight: Int) {
        val value = weight.coerceIn(4, 48)
        prefs.edit().putInt(KEY_DEFAULT_WEIGHT, value).apply()
        _state.value = _state.value.copy(defaultWeight = value)
    }

    fun setStatsPeriod(period: StatsPeriod) {
        prefs.edit().putString(KEY_STATS_PERIOD, period.name).apply()
        _state.value = _state.value.copy(statsPeriod = period)
    }

    fun setFixationPreset(preset: FixationPreset) {
        prefs.edit().putString(KEY_FIXATION_PRESET, preset.name).apply()
        _state.value = _state.value.copy(fixationPreset = preset)
    }

    fun setAthleteGender(gender: NormGender) {
        prefs.edit().putString(KEY_ATHLETE_GENDER, gender.name).apply()
        _profile.value = _profile.value.copy(gender = gender)
    }

    fun setAthleteAgeGroup(ageGroup: NormAgeGroup) {
        prefs.edit().putString(KEY_ATHLETE_AGE_GROUP, ageGroup.name).apply()
        _profile.value = _profile.value.copy(ageGroup = ageGroup)
    }

    fun setAthleteWeight(weightKg: Double?) {
        val value = weightKg
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG)
        val editor = prefs.edit()
        if (value == null) {
            editor.remove(KEY_ATHLETE_WEIGHT)
        } else {
            editor.putFloat(KEY_ATHLETE_WEIGHT, value.toFloat())
        }
        editor.apply()
        _profile.value = _profile.value.copy(weightKg = value)
    }

    /** Применяет профиль из резервной копии, не трогая настройки копирования. */
    fun applyProfile(profile: AthleteProfile) {
        val value = profile.copy(
            weightKg = profile.weightKg
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG)
        )
        val editor = prefs.edit()
            .putString(KEY_ATHLETE_GENDER, value.gender.name)
            .putString(KEY_ATHLETE_AGE_GROUP, value.ageGroup.name)
        if (value.weightKg == null) {
            editor.remove(KEY_ATHLETE_WEIGHT)
        } else {
            editor.putFloat(KEY_ATHLETE_WEIGHT, value.weightKg.toFloat())
        }
        editor.apply()
        _profile.value = value
    }

    /** Применяет настройки из резервной копии одним пакетом. */
    fun apply(settings: Settings) {
        val value = settings.copy(
            weeklyGoal = settings.weeklyGoal.coerceIn(1, 14),
            defaultWeight = settings.defaultWeight.coerceIn(4, 48),
            quickValues = QUICK_EXERCISES.associateWith { type ->
                SetQuickValues.sanitize(
                    settings.quickValues[type] ?: SetQuickValues.forType(type)
                )
            },
            gppExercises = SetQuickValues.sanitizeNames(settings.gppExercises)
        )
        val editor = prefs.edit()
            .putString(KEY_THEME, value.themeMode.name)
            .putInt(KEY_WEEKLY_GOAL, value.weeklyGoal)
            .putInt(KEY_DEFAULT_WEIGHT, value.defaultWeight)
            .putString(KEY_STATS_PERIOD, value.statsPeriod.name)
            .putString(KEY_FIXATION_PRESET, value.fixationPreset.name)
        QUICK_EXERCISES.forEach { type ->
            val values = value.quickValues.getValue(type)
            editor.putString(KEY_QUICK_PREFIX + type.name, encodeQuickValues(values))
        }
        editor.putStringSet(KEY_GPP_EXERCISES, value.gppExercises.toSet())
        editor.apply()
        _state.value = value
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_WEEKLY_GOAL = "weekly_goal"
        const val KEY_DEFAULT_WEIGHT = "default_weight"
        const val KEY_STATS_PERIOD = "stats_period"
        const val KEY_FIXATION_PRESET = "fixation_preset"
        const val KEY_QUICK_PREFIX = "quick_set_"
        const val KEY_GPP_EXERCISES = "gpp_exercises"
        val QUICK_EXERCISES = listOf(
            SectionType.JERK,
            SectionType.LONG_CYCLE,
            SectionType.SNATCH
        )
        const val KEY_ATHLETE_GENDER = "athlete_gender"
        const val KEY_ATHLETE_AGE_GROUP = "athlete_age_group"
        const val KEY_ATHLETE_WEIGHT = "athlete_weight"
        const val KEY_DEMO_SEEDED = "demo_seeded"
        const val KEY_VIDEO_FOLDER_URI = "video_folder_uri"
        const val MIN_WEIGHT_KG = 20.0
        const val MAX_WEIGHT_KG = 300.0
    }
}
