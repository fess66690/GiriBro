package ru.petproject.giribro.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepCounterTest {

    private val competition = FixationPreset.COMPETITION
    private val jerk = ExerciseProfile.of(RepExercise.JERK, competition)
    private val longCycle = ExerciseProfile.of(RepExercise.LONG_CYCLE, competition)
    private val snatch = ExerciseProfile.of(RepExercise.SNATCH, competition)

    @Test
    fun `jerk counts three fixations`() {
        val counter = RepCounter(jerk)
        var now = 1_000L

        repeat(3) {
            now = feed(counter, now, durationMs = 1_000, leftUp = false, rightUp = false)
            now = feed(counter, now, durationMs = 900, leftUp = true, rightUp = true)
        }

        val snapshot = counter.process(frame(now, leftUp = false, rightUp = false)).snapshot
        assertEquals(3, snapshot.total)
        assertEquals(3, snapshot.left)
        assertEquals(3, snapshot.right)
        assertEquals(0, snapshot.shortFixations)
    }

    @Test
    fun `jerk ignores mid amplitude`() {
        val counter = RepCounter(jerk)
        var now = 1_000L

        // Кисти выше плеча, но ниже головы: раньше это считалось верхней точкой.
        now = feedAt(counter, now, durationMs = 2_000, leftY = 0.20f, rightY = 0.20f)

        assertEquals(0, counter.currentSnapshot().total)
        assertEquals(0, counter.currentSnapshot().shortFixations)
    }

    @Test
    fun `short fixation is reported but not counted`() {
        val counter = RepCounter(jerk)
        var now = 1_000L

        now = feed(counter, now, durationMs = 300, leftUp = true, rightUp = true)
        now = feed(counter, now, durationMs = 1_000, leftUp = false, rightUp = false)
        val update = counter.process(frame(now, leftUp = false, rightUp = false))

        assertEquals(0, update.snapshot.total)
        assertEquals(1, update.snapshot.shortFixations)
    }

    @Test
    fun `snatch counts hands independently`() {
        val counter = RepCounter(snatch)
        var now = 1_000L

        repeat(2) {
            now = feed(counter, now, durationMs = 1_000, leftUp = false, rightUp = false)
            now = feed(counter, now, durationMs = 900, leftUp = true, rightUp = false)
        }
        now = feed(counter, now, durationMs = 1_000, leftUp = false, rightUp = false)
        now = feed(counter, now, durationMs = 900, leftUp = false, rightUp = true)

        val snapshot = counter.process(frame(now, leftUp = false, rightUp = false)).snapshot
        assertEquals(3, snapshot.total)
        assertEquals(2, snapshot.left)
        assertEquals(1, snapshot.right)
    }

    @Test
    fun `snatch counts one rep when both hands rise together`() {
        val counter = RepCounter(snatch)
        var now = 1_000L

        now = feedAt(counter, now, durationMs = 900, leftY = TOP_Y, rightY = TOP_Y)

        val snapshot = counter.currentSnapshot()
        assertEquals(1, snapshot.total)
        assertEquals(1, snapshot.left + snapshot.right)
    }

    @Test
    fun `snatch blocks idle hand until working hand descends`() {
        val counter = RepCounter(snatch)
        var now = 1_000L

        // Левая работает, правая висит.
        now = feedAt(counter, now, durationMs = 900, leftY = TOP_Y, rightY = HANG_Y)
        assertEquals(1, counter.currentSnapshot().total)
        assertEquals(1, counter.currentSnapshot().left)
        assertEquals(0, counter.currentSnapshot().right)

        // Правая поднялась, пока левая ещё вверху: второй повтор не должен появиться.
        now = feedAt(counter, now, durationMs = 900, leftY = TOP_Y, rightY = TOP_Y)
        assertEquals(1, counter.currentSnapshot().total)

        // Левая ушла вниз, теперь может работать правая.
        now = feedAt(counter, now, durationMs = 1_000, leftY = HANG_Y, rightY = HANG_Y)
        now = feedAt(counter, now, durationMs = 900, leftY = HANG_Y, rightY = TOP_Y)
        assertEquals(2, counter.currentSnapshot().total)
        assertEquals(1, counter.currentSnapshot().right)
    }

    @Test
    fun `long cycle counts only after returning to hang`() {
        val counter = RepCounter(longCycle)
        var now = 1_000L

        // Замок — первый повтор. Подтверждение в ДЦ короткое.
        now = feedAt(counter, now, durationMs = 400, leftY = TOP_Y, rightY = TOP_Y)
        assertEquals(1, counter.currentSnapshot().total)

        // Гири на груди: это не вис, поэтому новый повтор невозможен.
        now = feedAt(counter, now, durationMs = 1_100, leftY = RACK_Y, rightY = RACK_Y)
        now = feedAt(counter, now, durationMs = 400, leftY = TOP_Y, rightY = TOP_Y)
        assertEquals(1, counter.currentSnapshot().total)

        // Сброс в вис и новый цикл.
        now = feedAt(counter, now, durationMs = 1_100, leftY = HANG_Y, rightY = HANG_Y)
        now = feedAt(counter, now, durationMs = 400, leftY = TOP_Y, rightY = TOP_Y)
        assertEquals(2, counter.currentSnapshot().total)
    }

    @Test
    fun `flicker at the top does not double count`() {
        val counter = RepCounter(jerk)
        var now = 1_000L

        now = feed(counter, now, durationMs = 1_000, leftUp = false, rightUp = false)
        now = feed(counter, now, durationMs = 850, leftUp = true, rightUp = true)
        // Кратковременный провал меньше grace-паузы: фиксация не должна начаться заново.
        now = feed(counter, now, durationMs = 50, leftUp = false, rightUp = false)
        now = feed(counter, now, durationMs = 500, leftUp = true, rightUp = true)

        assertEquals(1, counter.process(frame(now, true, true)).snapshot.total)
    }

    @Test
    fun `lost pose keeps counters and reports no tracking`() {
        val counter = RepCounter(jerk)
        var now = 1_000L
        now = feed(counter, now, durationMs = 1_000, leftUp = true, rightUp = true)

        val update = counter.process(emptyFrame(now))
        assertEquals(1, update.snapshot.total)
        assertFalse(update.snapshot.tracking)
    }

    @Test
    fun `manual corrections keep totals consistent`() {
        val counter = RepCounter(jerk)
        counter.manualAdd()
        counter.manualAdd()
        counter.manualRemove()

        val snapshot = counter.process(frame(2_000, false, false)).snapshot
        assertEquals(1, snapshot.total)
        assertEquals(1, snapshot.left)
        assertEquals(1, snapshot.right)
    }

    @Test
    fun `snatch manual correction targets requested hand`() {
        val counter = RepCounter(snatch)
        counter.manualAdd(Side.RIGHT)
        counter.manualAdd(Side.RIGHT)
        counter.manualAdd(Side.LEFT)

        val snapshot = counter.process(frame(2_000, false, false)).snapshot
        assertEquals(3, snapshot.total)
        assertEquals(1, snapshot.left)
        assertEquals(2, snapshot.right)
    }

    @Test
    fun `tracking is true when body is visible`() {
        val counter = RepCounter(jerk)
        val snapshot = counter.process(frame(1_000, false, false)).snapshot
        assertTrue(snapshot.tracking)
    }

    private fun feed(
        counter: RepCounter,
        start: Long,
        durationMs: Long,
        leftUp: Boolean,
        rightUp: Boolean
    ): Long = feedAt(
        counter = counter,
        start = start,
        durationMs = durationMs,
        leftY = if (leftUp) TOP_Y else HANG_Y,
        rightY = if (rightUp) TOP_Y else HANG_Y
    )

    private fun feedAt(
        counter: RepCounter,
        start: Long,
        durationMs: Long,
        leftY: Float,
        rightY: Float
    ): Long {
        var now = start
        val end = start + durationMs
        while (now < end) {
            counter.process(frameAt(now, leftY, rightY))
            now += FRAME_MS
        }
        return now
    }

    private fun emptyFrame(timestampMs: Long): PoseFrame = PoseFrame(
        timestampMs = timestampMs,
        landmarks = List(PoseFrame.LANDMARK_COUNT) {
            PoseLandmark(0.5f, 0.5f, 0f, visibility = 0.1f)
        },
        imageWidth = 480,
        imageHeight = 640
    )

    private fun frame(
        timestampMs: Long,
        leftUp: Boolean,
        rightUp: Boolean
    ): PoseFrame = frameAt(
        timestampMs = timestampMs,
        leftY = if (leftUp) TOP_Y else HANG_Y,
        rightY = if (rightUp) TOP_Y else HANG_Y
    )

    /** Кадр с произвольной высотой кистей: 0,08 — замок, 0,20 — середина, 0,66 — вис. */
    private fun frameAt(
        timestampMs: Long,
        leftY: Float,
        rightY: Float
    ): PoseFrame {
        val landmarks = MutableList(PoseFrame.LANDMARK_COUNT) {
            PoseLandmark(0.5f, 0.5f, 0f, visibility = 0.9f)
        }
        val world = MutableList(PoseFrame.LANDMARK_COUNT) {
            PoseLandmark(0f, 0f, 0f, visibility = 0.9f)
        }

        fun set(index: Int, x: Float, y: Float, wx: Float, wy: Float) {
            landmarks[index] = PoseLandmark(x, y, 0f, 0.95f)
            world[index] = PoseLandmark(wx, wy, 0f, 0.95f)
        }

        fun worldY(normalizedY: Float): Float = SHOULDER_WORLD_Y +
            (SHOULDER_Y - normalizedY) * WORLD_SCALE

        set(PoseIndex.NOSE, 0.5f, 0.12f, 0f, 1.75f)
        set(PoseIndex.LEFT_SHOULDER, 0.42f, SHOULDER_Y, -0.18f, SHOULDER_WORLD_Y)
        set(PoseIndex.RIGHT_SHOULDER, 0.58f, SHOULDER_Y, 0.18f, SHOULDER_WORLD_Y)
        set(PoseIndex.LEFT_HIP, 0.45f, 0.62f, -0.12f, 0.98f)
        set(PoseIndex.RIGHT_HIP, 0.55f, 0.62f, 0.12f, 0.98f)

        fun setArm(elbowIndex: Int, wristIndex: Int, wristY: Float, x: Float) {
            val elbowY = SHOULDER_Y + (wristY - SHOULDER_Y) * 0.5f
            set(elbowIndex, x, elbowY, x, worldY(elbowY))
            set(wristIndex, x, wristY, x, worldY(wristY))
        }
        setArm(PoseIndex.LEFT_ELBOW, PoseIndex.LEFT_WRIST, leftY, -0.18f)
        setArm(PoseIndex.RIGHT_ELBOW, PoseIndex.RIGHT_WRIST, rightY, 0.18f)

        return PoseFrame(
            timestampMs = timestampMs,
            landmarks = landmarks,
            world = world,
            imageWidth = 480,
            imageHeight = 640
        )
    }

    private companion object {
        const val FRAME_MS = 50L
        const val TOP_Y = 0.08f
        const val RACK_Y = 0.34f
        const val HANG_Y = 0.66f
        const val SHOULDER_Y = 0.32f
        const val SHOULDER_WORLD_Y = 1.42f
        const val WORLD_SCALE = 2.2f
    }
}
