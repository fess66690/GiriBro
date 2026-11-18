package ru.petproject.giribro.vision

import kotlin.math.max

sealed interface RepEvent {
    data class Counted(
        val side: Side,
        val total: Int,
        val fixationSec: Float,
        val confident: Boolean
    ) : RepEvent

    data class ShortFixation(
        val side: Side,
        val heldSec: Float
    ) : RepEvent
}

data class RepSnapshot(
    val total: Int = 0,
    val left: Int = 0,
    val right: Int = 0,
    val shortFixations: Int = 0,
    val tracking: Boolean = false,
    val fixationProgress: Float = 0f,
    val activeSides: Set<Side> = emptySet(),
    val lastFixationSec: Float = 0f,
    val lastRepConfident: Boolean = true
)

data class RepUpdate(
    val snapshot: RepSnapshot,
    val events: List<RepEvent> = emptyList()
)

/**
 * Конечный автомат подсчёта повторов. Не знает ни про Android, ни про камеру:
 * на вход идут кадры скелета, на выход — события и снимок состояния.
 *
 * Верхняя точка — это **полный замок**: кисть выше головы и локоть почти
 * прямой. Просто «кисть чуть выше плеча» больше не считается: именно из-за
 * такого порога толчок срабатывал в середине подъёма.
 *
 * Дальше поведение зависит от техники:
 * - [RepTechnique.SHORT_LOCKOUT] — короткое подтверждение замка, следующий
 *   повтор после ухода рук вниз (без требования виса);
 * - [RepTechnique.FULL_CYCLE] — короткое подтверждение замка, следующий
 *   повтор только после того, как гири вернулись в вис;
 * - [RepTechnique.ALTERNATING] — руки поочерёдно, вторая заблокирована, пока
 *   первая не опустится.
 */
class RepCounter(profile: ExerciseProfile) {

    private class SideState {
        var inTop = false
        var topSince = 0L
        var lastTopAt = 0L
        var armed = true
        var lastRepAt = 0L
    }

    var profile: ExerciseProfile = profile
        private set

    private val shared = SideState()
    private val left = SideState()
    private val right = SideState()

    /** Рука, которая сейчас «держит» рывок; вторая в это время не считается. */
    private var lockedSide: Side? = null
    private var lockDownSince = 0L

    private var totalCount = 0
    private var leftCount = 0
    private var rightCount = 0
    private var shortCount = 0
    private var lastFixationSec = 0f
    private var lastConfident = true
    private var lastCountedSide: Side? = null
    private var lastSnapshot = RepSnapshot()

    /** Последний снимок без нового кадра — для ручных поправок. */
    fun currentSnapshot(): RepSnapshot = lastSnapshot

    fun updateProfile(newProfile: ExerciseProfile) {
        if (newProfile == profile) return
        profile = newProfile
        reset()
    }

    fun reset() {
        shared.reset()
        left.reset()
        right.reset()
        lockedSide = null
        lockDownSince = 0L
        totalCount = 0
        leftCount = 0
        rightCount = 0
        shortCount = 0
        lastFixationSec = 0f
        lastConfident = true
        lastCountedSide = null
        lastSnapshot = RepSnapshot()
    }

    fun process(frame: PoseFrame): RepUpdate {
        val events = mutableListOf<RepEvent>()
        val now = frame.timestampMs

        val torsoLength = PoseGeometry.torsoLength(frame)
        val torso = torsoMetrics(frame)
        if (!frame.hasPose || torsoLength == null || torso == null) {
            return finish(
                snapshot = snapshot(tracking = false),
                events = events
            )
        }

        val activeSides = mutableSetOf<Side>()
        var progress = 0f

        if (profile.bothHands) {
            val leftEval = evaluateSide(frame, torsoLength, torso, Side.LEFT)
            val rightEval = evaluateSide(frame, torsoLength, torso, Side.RIGHT)
            val bothVisible = leftEval != null && rightEval != null
            val top = when {
                bothVisible -> leftEval!!.top && rightEval!!.top
                leftEval != null -> leftEval.top
                rightEval != null -> rightEval.top
                else -> null
            }
            if (top == null) {
                return finish(
                    snapshot = snapshot(tracking = false),
                    events = events
                )
            }
            // В длинном цикле новую фиксацию разрешаем только из виса.
            val canRearm = if (profile.rearmRequiresHang) {
                listOfNotNull(leftEval, rightEval).all { it.hang }
            } else {
                true
            }
            val confident = bothVisible
            updateState(
                state = shared,
                top = top,
                now = now,
                side = Side.BOTH,
                confident = confident,
                events = events,
                canRearm = canRearm
            )
            progress = stateProgress(shared, now)
            if (top || shared.inTop) activeSides.add(Side.BOTH)
        } else {
            val evaluations = listOfNotNull(
                evaluateSide(frame, torsoLength, torso, Side.LEFT)?.let { Side.LEFT to it },
                evaluateSide(frame, torsoLength, torso, Side.RIGHT)?.let { Side.RIGHT to it }
            ).toMap()

            if (evaluations.isEmpty()) {
                lockedSide = null
                lockDownSince = 0L
                return finish(
                    snapshot = snapshot(tracking = false),
                    events = events
                )
            }

            if (profile.alternateSides) {
                updateAlternating(evaluations, now, events, activeSides) { p ->
                    progress = max(progress, p)
                }
            } else {
                evaluations.forEach { (side, evaluation) ->
                    val state = if (side == Side.LEFT) left else right
                    updateState(
                        state = state,
                        top = evaluation.top,
                        now = now,
                        side = side,
                        confident = true,
                        events = events
                    )
                    progress = max(progress, stateProgress(state, now))
                    if (evaluation.top || state.inTop) activeSides.add(side)
                }
            }
        }

        return finish(
            snapshot = snapshot(
                tracking = true,
                progress = progress,
                activeSides = activeSides
            ),
            events = events
        )
    }

    /**
     * Рывок: выбираем одну «рабочую» руку и глушим вторую, пока рабочая не
     * опустится вниз. Так одиночный подъём не превращается в два повтора.
     */
    private fun updateAlternating(
        evaluations: Map<Side, SideEvaluation>,
        now: Long,
        events: MutableList<RepEvent>,
        activeSides: MutableSet<Side>,
        onProgress: (Float) -> Unit
    ) {
        var primary = lockedSide

        if (primary != null) {
            val primaryEval = evaluations[primary]
            when {
                primaryEval == null -> {
                    // Рабочая рука пропала из кадра — снимаем блокировку.
                    lockedSide = null
                    lockDownSince = 0L
                    primary = null
                }

                !primaryEval.top && primaryEval.hang -> {
                    if (lockDownSince == 0L) lockDownSince = now
                    if (now - lockDownSince >= profile.releaseMs) {
                        lockedSide = null
                        lockDownSince = 0L
                        primary = null
                    }
                }

                else -> lockDownSince = 0L
            }
        }

        if (primary == null) {
            val raised = evaluations.filter { it.value.top }.keys
            primary = when {
                raised.size == 1 -> raised.first()
                raised.size > 1 -> raised.maxBy { evaluations.getValue(it).riseRatio }
                else -> null
            }
            if (primary != null) {
                lockedSide = primary
                lockDownSince = 0L
            }
        }

        evaluations.forEach { (side, evaluation) ->
            val state = if (side == Side.LEFT) left else right
            val isPrimary = side == primary
            updateState(
                state = state,
                top = isPrimary && evaluation.top,
                now = now,
                side = side,
                confident = isPrimary,
                events = events,
                canRearm = isPrimary || evaluation.hang,
                emitShort = isPrimary
            )
            if (isPrimary) {
                onProgress(stateProgress(state, now))
                if (evaluation.top || state.inTop) activeSides.add(side)
            }
        }
    }

    private fun finish(snapshot: RepSnapshot, events: List<RepEvent>): RepUpdate {
        lastSnapshot = snapshot
        return RepUpdate(snapshot = snapshot, events = events)
    }

    /** Ручная поправка: +1 повтор. Для рывка [side] задаёт руку. */
    fun manualAdd(side: Side? = null) {
        when {
            profile.bothHands -> {
                totalCount++
                leftCount++
                rightCount++
            }

            side == null || side == Side.BOTH -> {
                val target = lastCountedSide ?: Side.LEFT
                if (target == Side.LEFT) leftCount++ else rightCount++
                totalCount++
                lastCountedSide = target
            }

            side == Side.LEFT -> {
                leftCount++
                totalCount++
                lastCountedSide = Side.LEFT
            }

            side == Side.RIGHT -> {
                rightCount++
                totalCount++
                lastCountedSide = Side.RIGHT
            }
        }
        syncManualSnapshot()
    }

    /** Ручная поправка: −1 повтор. */
    fun manualRemove(side: Side? = null) {
        when {
            profile.bothHands -> {
                if (totalCount <= 0) return
                totalCount--
                leftCount = (leftCount - 1).coerceAtLeast(0)
                rightCount = (rightCount - 1).coerceAtLeast(0)
            }

            side == Side.LEFT -> {
                if (leftCount <= 0) return
                leftCount--
                totalCount = (totalCount - 1).coerceAtLeast(0)
            }

            side == Side.RIGHT -> {
                if (rightCount <= 0) return
                rightCount--
                totalCount = (totalCount - 1).coerceAtLeast(0)
            }

            else -> {
                if (totalCount <= 0) return
                totalCount--
                if (leftCount >= rightCount) {
                    leftCount = (leftCount - 1).coerceAtLeast(0)
                } else {
                    rightCount = (rightCount - 1).coerceAtLeast(0)
                }
            }
        }
        syncManualSnapshot()
    }

    private fun syncManualSnapshot() {
        lastSnapshot = lastSnapshot.copy(
            total = totalCount,
            left = leftCount,
            right = rightCount,
            shortFixations = shortCount
        )
    }

    private fun updateState(
        state: SideState,
        top: Boolean,
        now: Long,
        side: Side,
        confident: Boolean,
        events: MutableList<RepEvent>,
        canRearm: Boolean = true,
        emitShort: Boolean = true
    ) {
        if (top) {
            if (!state.inTop) {
                // После паузы рука снова может набирать фиксацию.
                if (!state.armed && now - state.lastTopAt >= profile.releaseMs && canRearm) {
                    state.armed = true
                }
                state.inTop = true
                state.topSince = now
            }
            state.lastTopAt = now
            val heldMs = now - state.topSince
            val sinceRep = now - state.lastRepAt
            if (state.armed &&
                heldMs >= profile.effectiveFixationMs &&
                sinceRep >= profile.minRepIntervalMs
            ) {
                state.armed = false
                state.lastRepAt = now
                val heldSec = heldMs / 1000f
                lastFixationSec = heldSec
                lastConfident = confident
                lastCountedSide = side
                when (side) {
                    Side.BOTH -> {
                        totalCount++
                        leftCount++
                        rightCount++
                    }

                    Side.LEFT -> {
                        leftCount++
                        totalCount++
                    }

                    Side.RIGHT -> {
                        rightCount++
                        totalCount++
                    }
                }
                events += RepEvent.Counted(
                    side = side,
                    total = totalCount,
                    fixationSec = heldSec,
                    confident = confident
                )
            }
        } else {
            if (state.inTop && now - state.lastTopAt >= profile.topGraceMs) {
                val heldMs = state.lastTopAt - state.topSince
                if (emitShort && state.armed && heldMs >= MIN_SHORT_FIXATION_MS) {
                    shortCount++
                    events += RepEvent.ShortFixation(
                        side = side,
                        heldSec = heldMs / 1000f
                    )
                }
                state.inTop = false
                state.topSince = 0L
            }
            if (!state.inTop && !state.armed && canRearm &&
                now - state.lastTopAt >= profile.releaseMs
            ) {
                state.armed = true
            }
        }
    }

    private fun stateProgress(state: SideState, now: Long): Float {
        if (!state.inTop) return 0f
        if (!state.armed) return 1f
        return ((now - state.topSince).toFloat() / profile.effectiveFixationMs).coerceIn(0f, 1f)
    }

    private fun snapshot(
        tracking: Boolean,
        progress: Float = 0f,
        activeSides: Set<Side> = emptySet()
    ): RepSnapshot = RepSnapshot(
        total = totalCount,
        left = leftCount,
        right = rightCount,
        shortFixations = shortCount,
        tracking = tracking,
        fixationProgress = if (tracking) progress else 0f,
        activeSides = activeSides,
        lastFixationSec = lastFixationSec,
        lastRepConfident = lastConfident
    )

    private data class TorsoMetrics(
        val tiltDeg: Float
    )

    private data class SideEvaluation(
        val top: Boolean,
        val hang: Boolean,
        val elbowDeg: Float,
        val riseRatio: Float
    )

    private fun torsoMetrics(frame: PoseFrame): TorsoMetrics? {
        val leftShoulder = frame.point(PoseIndex.LEFT_SHOULDER)
        val rightShoulder = frame.point(PoseIndex.RIGHT_SHOULDER)
        val leftHip = frame.point(PoseIndex.LEFT_HIP)
        val rightHip = frame.point(PoseIndex.RIGHT_HIP)
        val shoulder = when {
            leftShoulder != null && rightShoulder != null ->
                PoseGeometry.midpoint(leftShoulder, rightShoulder)

            else -> leftShoulder ?: rightShoulder
        } ?: return null
        val hip = when {
            leftHip != null && rightHip != null -> PoseGeometry.midpoint(leftHip, rightHip)
            else -> leftHip ?: rightHip
        } ?: return null
        val tilt = PoseGeometry.torsoTiltDeg(hip, shoulder, aspectOf(frame))
        return TorsoMetrics(tiltDeg = tilt)
    }

    private fun evaluateSide(
        frame: PoseFrame,
        torsoLength: Float,
        torso: TorsoMetrics,
        side: Side
    ): SideEvaluation? {
        val shoulderIndex =
            if (side == Side.LEFT) PoseIndex.LEFT_SHOULDER else PoseIndex.RIGHT_SHOULDER
        val elbowIndex =
            if (side == Side.LEFT) PoseIndex.LEFT_ELBOW else PoseIndex.RIGHT_ELBOW
        val wristIndex =
            if (side == Side.LEFT) PoseIndex.LEFT_WRIST else PoseIndex.RIGHT_WRIST

        val shoulder = frame.point(shoulderIndex) ?: return null
        val elbow = frame.point(elbowIndex) ?: return null
        val wrist = frame.point(wristIndex) ?: return null

        val measureShoulder = frame.measurePoint(shoulderIndex) ?: shoulder
        val measureElbow = frame.measurePoint(elbowIndex) ?: elbow
        val measureWrist = frame.measurePoint(wristIndex) ?: wrist
        val elbowDeg = PoseGeometry.angleDeg(measureShoulder, measureElbow, measureWrist)
            ?: return null

        // Отрицательная разница означает, что кисть ниже плеча.
        val rise = shoulder.y - wrist.y
        val riseRatio = rise / torsoLength

        // Полный замок: кисть выше головы. Если голова не видна — резервный
        // порог в долях корпуса, но он теперь намного строже прежних 6 %.
        val nose = frame.point(PoseIndex.NOSE)
        val aboveHead = nose != null && wrist.y <= nose.y - HEAD_MARGIN
        val wristHighEnough = aboveHead || riseRatio >= profile.wristAboveShoulderRatio
        val elbowStraight = elbowDeg >= profile.elbowStraightDeg
        val torsoOk = torso.tiltDeg <= profile.maxTorsoTiltDeg

        // Вис: кисть заметно ниже плеча. Используется для ДЦ и для разблокировки
        // второй руки в рывке.
        val hang = wrist.y >= shoulder.y + HANG_MARGIN

        return SideEvaluation(
            top = wristHighEnough && elbowStraight && torsoOk,
            hang = hang,
            elbowDeg = elbowDeg,
            riseRatio = riseRatio
        )
    }

    private fun aspectOf(frame: PoseFrame): Float =
        if (frame.imageHeight > 0) {
            frame.imageWidth.toFloat() / frame.imageHeight
        } else {
            1f
        }

    private fun SideState.reset() {
        inTop = false
        topSince = 0L
        lastTopAt = 0L
        armed = true
        lastRepAt = 0L
    }

    companion object {
        private const val MIN_SHORT_FIXATION_MS = 150L
        /** Насколько кисть должна быть выше носа, чтобы считать замок. */
        private const val HEAD_MARGIN = 0.01f
        /** Насколько кисть должна быть ниже плеча, чтобы считать вис. */
        private const val HANG_MARGIN = 0.05f
    }
}
