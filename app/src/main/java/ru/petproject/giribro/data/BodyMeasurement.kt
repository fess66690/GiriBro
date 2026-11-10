package ru.petproject.giribro.data

import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Замер тела: обязательный вес и необязательные обхваты.
 *
 * Обхваты хранятся в сантиметрах с точностью до десятых, вес — в килограммах.
 * Пустые обхваты означают, что пользователь их не измерял.
 */
data class BodyMeasurement(
    val id: Long = 0L,
    val date: LocalDate,
    val weightKg: Double,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val hipsCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Заполненные обхваты в порядке отображения: подпись и значение. */
    val sizes: List<Pair<String, Double>>
        get() = listOfNotNull(
            waistCm?.let { "Талия" to it },
            chestCm?.let { "Грудь" to it },
            hipsCm?.let { "Бёдра" to it },
            armCm?.let { "Плечо" to it },
            thighCm?.let { "Бедро" to it }
        )
}

/** Число без лишнего нуля после запятой: 82.0 → «82», 82.5 → «82.5». */
fun Double.asBodyNumber(): String {
    val rounded = (this * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", rounded)
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}

fun Double.asBodyWeight(): String = "${asBodyNumber()} кг"

fun Double.asBodySize(): String = "${asBodyNumber()} см"

/** Изменение веса со знаком: «−1.2 кг», «+0.4 кг» или «0 кг». */
fun Double.asSignedBodyWeight(): String {
    val sign = when {
        this > 0.0 -> "+"
        this < 0.0 -> "−"
        else -> ""
    }
    return "$sign${kotlin.math.abs(this).asBodyNumber()} кг"
}
