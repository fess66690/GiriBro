package ru.petproject.giribro.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * CameraX-анализатор, который превращает кадр в [PoseFrame] через MediaPipe.
 *
 * Модель лежит в `assets`, сеть не используется. Кадры не сохраняются: bitmap
 * живёт только до получения результата.
 */
class MediaPipePoseSource(
    context: Context,
    private val onFrame: (PoseFrame) -> Unit,
    private val onError: (Throwable) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {

    private val landmarker: PoseLandmarker
    private val busy = AtomicBoolean(false)
    private var lastTimestamp = 0L

    @Volatile
    private var pendingWidth = 0

    @Volatile
    private var pendingHeight = 0

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(Delegate.CPU)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setOutputSegmentationMasks(false)
            .setResultListener { result, _ ->
                try {
                    val landmarks = result.landmarks().firstOrNull().orEmpty().map { point ->
                        PoseLandmark(
                            x = point.x(),
                            y = point.y(),
                            z = point.z(),
                            visibility = point.visibility().orElse(1f)
                        )
                    }
                    val world = result.worldLandmarks().firstOrNull().orEmpty().map { point ->
                        PoseLandmark(
                            x = point.x(),
                            y = point.y(),
                            z = point.z(),
                            visibility = point.visibility().orElse(1f)
                        )
                    }
                    onFrame(
                        PoseFrame(
                            timestampMs = result.timestampMs(),
                            landmarks = landmarks,
                            world = world,
                            imageWidth = pendingWidth,
                            imageHeight = pendingHeight
                        )
                    )
                } finally {
                    busy.set(false)
                }
            }
            .setErrorListener { error ->
                busy.set(false)
                onError(error)
            }
            .build()
        landmarker = PoseLandmarker.createFromOptions(context, options)
    }

    override fun analyze(image: ImageProxy) {
        if (!busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        val timestamp = max(
            SystemClock.uptimeMillis(),
            lastTimestamp + 1
        )
        lastTimestamp = timestamp
        try {
            val bitmap = image.toBitmapRgba()
                ?: throw IllegalStateException("кадр не в формате RGBA_8888")
            val upright = bitmap.rotate(image.imageInfo.rotationDegrees)
            pendingWidth = upright.width
            pendingHeight = upright.height
            val mpImage = BitmapImageBuilder(upright).build()
            landmarker.detectAsync(mpImage, timestamp)
        } catch (error: Throwable) {
            busy.set(false)
            onError(error)
        } finally {
            image.close()
        }
    }

    override fun close() {
        runCatching { landmarker.close() }
    }

    private fun ImageProxy.toBitmapRgba(): Bitmap? {
        val plane = planes.firstOrNull() ?: return null
        if (plane.pixelStride != 4) return null
        val width = width
        val height = height
        val rowStride = plane.rowStride
        val buffer = plane.buffer
        val rowBytes = width * 4
        val bytes = ByteArray(rowBytes * height)

        if (rowStride == rowBytes) {
            buffer.position(0)
            buffer.get(bytes, 0, bytes.size)
        } else {
            val row = ByteArray(rowStride)
            for (y in 0 until height) {
                val offset = y * rowStride
                if (offset >= buffer.limit()) break
                buffer.position(offset)
                val length = min(rowStride, buffer.remaining())
                buffer.get(row, 0, length)
                System.arraycopy(row, 0, bytes, y * rowBytes, rowBytes)
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        }
    }

    private fun Bitmap.rotate(degrees: Int): Bitmap {
        if (degrees % 360 == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (rotated !== this) recycle()
        return rotated
    }

    companion object {
        const val MODEL_ASSET = "pose_landmarker_lite.task"
    }
}
