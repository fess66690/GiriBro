package ru.petproject.giribro.vision

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/** Геометрия скелета без зависимостей от Android. */
internal object PoseGeometry {

    fun distance(a: PoseLandmark, b: PoseLandmark, aspect: Float = 1f): Float {
        val dx = (a.x - b.x) * aspect
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun midpoint(a: PoseLandmark, b: PoseLandmark): PoseLandmark = PoseLandmark(
        x = (a.x + b.x) / 2f,
        y = (a.y + b.y) / 2f,
        z = (a.z + b.z) / 2f,
        visibility = minOf(a.visibility, b.visibility)
    )

    /**
     * Угол в точке [b] между лучами [b]→[a] и [b]→[c]. 180° — прямая.
     * Возвращает `null`, если точки совпали и угол не определён.
     */
    fun angleDeg(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Float? {
        val ba = floatArrayOf(a.x - b.x, a.y - b.y, a.z - b.z)
        val bc = floatArrayOf(c.x - b.x, c.y - b.y, c.z - b.z)
        val lenBa = sqrt(ba[0] * ba[0] + ba[1] * ba[1] + ba[2] * ba[2])
        val lenBc = sqrt(bc[0] * bc[0] + bc[1] * bc[1] + bc[2] * bc[2])
        if (lenBa < 1e-5f || lenBc < 1e-5f) return null
        val dot = ba[0] * bc[0] + ba[1] * bc[1] + ba[2] * bc[2]
        val cosine = (dot / (lenBa * lenBc)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine).toDouble()).toFloat()
    }

    /** Наклон линии таз→плечи от вертикали. 0° — корпус вертикален. */
    fun torsoTiltDeg(hip: PoseLandmark, shoulder: PoseLandmark, aspect: Float = 1f): Float {
        val dx = (shoulder.x - hip.x) * aspect
        // В координатах кадра y растёт вниз, поэтому «вверх» — это отрицательный dy.
        val dy = shoulder.y - hip.y
        if (abs(dx) < 1e-5f && abs(dy) < 1e-5f) return 0f
        val degrees = Math.toDegrees(atan2(abs(dx), -dy).toDouble()).toFloat()
        return degrees.coerceIn(0f, 180f)
    }

    /**
     * Длина корпуса в нормализованных координатах с поправкой на пропорции
     * кадра. Нужна, чтобы порог «кисть выше плеча» не зависел от дистанции.
     */
    fun torsoLength(frame: PoseFrame): Float? {
        val leftShoulder = frame.point(PoseIndex.LEFT_SHOULDER) ?: return null
        val rightShoulder = frame.point(PoseIndex.RIGHT_SHOULDER) ?: return null
        val leftHip = frame.point(PoseIndex.LEFT_HIP) ?: return null
        val rightHip = frame.point(PoseIndex.RIGHT_HIP) ?: return null
        val aspect = if (frame.imageHeight > 0) {
            frame.imageWidth.toFloat() / frame.imageHeight
        } else {
            1f
        }
        val length = distance(midpoint(leftShoulder, rightShoulder), midpoint(leftHip, rightHip), aspect)
        return length.takeIf { it > 1e-4f }
    }

    fun meanVisibility(a: PoseLandmark?, b: PoseLandmark?): Float =
        listOfNotNull(a, b).map { it.visibility }.average().toFloat()
}
