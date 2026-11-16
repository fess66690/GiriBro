package ru.petproject.giribro.vision

/**
 * Одна точка скелета. Координаты нормализованы (0..1), `y` растёт вниз.
 *
 * [world] — те же точки в метрах, если модель их вернула. Они не зависят от
 * дистанции до камеры, поэтому углы считаем по ним.
 */
data class PoseLandmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 1f
) {
    val isReliable: Boolean
        get() = visibility >= VISIBILITY_THRESHOLD &&
            x.isFinite() && y.isFinite() && z.isFinite()

    companion object {
        const val VISIBILITY_THRESHOLD = 0.5f
    }
}

/** Кадр позы, уже отвязанный от Android и MediaPipe. */
data class PoseFrame(
    val timestampMs: Long,
    val landmarks: List<PoseLandmark>,
    val world: List<PoseLandmark> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
) {
    fun point(index: Int): PoseLandmark? =
        landmarks.getOrNull(index)?.takeIf { it.isReliable }

    /** Для углов предпочитаем мировые координаты, иначе — нормализованные. */
    fun measurePoint(index: Int): PoseLandmark? =
        world.getOrNull(index)?.takeIf { it.isReliable } ?: point(index)

    val hasPose: Boolean
        get() = landmarks.size >= LANDMARK_COUNT &&
            landmarks.any { it.isReliable }

    companion object {
        const val LANDMARK_COUNT = 33
    }
}

/** Индексы точек MediaPipe Pose. */
object PoseIndex {
    const val NOSE = 0
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
}
