package com.user.jarvis.gestures

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.user.jarvis.AirTouchSettings
import com.user.jarvis.CursorOverlayManager
import com.user.jarvis.GestureActionDispatcher
import com.user.jarvis.gestures.models.Direction
import com.user.jarvis.gestures.models.GestureAction
import com.user.jarvis.gestures.models.HandShape
import kotlin.math.hypot

class GestureEngine(private val context: Context) {

    private val actionDispatcher = GestureActionDispatcher(context)
    private val repository = GestureRepository(context)

    private var lastGestureTime = 0L
    private var currentHandShapeState: HandShape = HandShape.UNKNOWN
    private var shapeStartTime = 0L
    private val GESTURE_HOLD_DURATION_MS = 600L
    private val SWIPE_THRESHOLD = 0.15f
    private val VELOCITY_TIME_WINDOW_MS = 500L
    private var cooldownMs = 1000L

    private data class PointTime(val x: Float, val y: Float, val time: Long)
    private val pointHistory = mutableListOf<PointTime>()

    fun processLandmarks(
        landmarks: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int,
        cursorManager: CursorOverlayManager?
    ) {
        if (landmarks.size < 21) return

        cooldownMs = AirTouchSettings.getGestureCooldown(context).toLong()

        val indexTip = landmarks[8]
        val thumbTip = landmarks[4]
        val middleTip = landmarks[12]
        val ringTip = landmarks[16]
        val pinkyTip = landmarks[20]

        val thumbBase = landmarks[2]
        val indexBase = landmarks[5]
        val middleBase = landmarks[9]
        val ringBase = landmarks[13]
        val pinkyBase = landmarks[17]
        val wrist = landmarks[0]

        // Update cursor position if cursor is enabled (mirrored on X)
        cursorManager?.updatePosition(1f - indexTip.x(), indexTip.y())

        val currentTime = System.currentTimeMillis()

        if (currentTime - lastGestureTime < cooldownMs) {
            pointHistory.clear() // Prevent tracking while in cooldown
            return
        }

        // Track wrist movement for direction
        pointHistory.add(PointTime(wrist.x(), wrist.y(), currentTime))
        pointHistory.removeAll { currentTime - it.time > VELOCITY_TIME_WINDOW_MS }

        // Analyze Hand Shape
        val pinchDist = hypot((indexTip.x() - thumbTip.x()).toDouble(), (indexTip.y() - thumbTip.y()).toDouble())

        val isIndexExtended = indexTip.y() < indexBase.y()
        val isMiddleExtended = middleTip.y() < middleBase.y()
        val isRingExtended = ringTip.y() < ringBase.y()
        val isPinkyExtended = pinkyTip.y() < pinkyBase.y()
        val isThumbExtended = thumbTip.x() > thumbBase.x() + 0.05f || thumbTip.x() < thumbBase.x() - 0.05f

        val detectedShape = when {
            pinchDist < 0.05 -> HandShape.PINCH
            isIndexExtended && isMiddleExtended && !isRingExtended && !isPinkyExtended && !isThumbExtended -> HandShape.PEACE
            isIndexExtended && !isMiddleExtended && !isRingExtended && isPinkyExtended -> HandShape.ROCK_ON
            isIndexExtended && isMiddleExtended && isRingExtended && !isPinkyExtended -> HandShape.THREE_FINGERS
            isIndexExtended && isMiddleExtended && isRingExtended && isPinkyExtended -> HandShape.PALM
            isIndexExtended && !isMiddleExtended && !isRingExtended && !isPinkyExtended -> HandShape.POINTING
            else -> HandShape.UNKNOWN
        }

        // Detect Direction from history
        var currentDirection = Direction.NONE
        if (pointHistory.size > 5) {
            val oldest = pointHistory.first()
            val newest = pointHistory.last()

            val dx = newest.x - oldest.x
            val dy = newest.y - oldest.y

            if (kotlin.math.abs(dx) > SWIPE_THRESHOLD || kotlin.math.abs(dy) > SWIPE_THRESHOLD) {
                if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                    currentDirection = if (dx > 0) Direction.RIGHT else Direction.LEFT // Note: Right/Left may need mirroring based on camera
                } else {
                    currentDirection = if (dy > 0) Direction.DOWN else Direction.UP
                }
            }
        }

        // Evaluate state
        if (detectedShape != HandShape.UNKNOWN && detectedShape == currentHandShapeState) {
            if (currentTime - shapeStartTime >= GESTURE_HOLD_DURATION_MS || currentDirection != Direction.NONE) {
                // We have a stable shape, and either it's been held, or a movement was detected
                val actionToTrigger = repository.getActionFor(detectedShape, currentDirection)

                if (actionToTrigger != GestureAction.NONE) {
                    Log.d("GestureEngine", "Triggered: $detectedShape + $currentDirection -> $actionToTrigger")
                    actionDispatcher.dispatchAction(actionToTrigger.name)

                    lastGestureTime = currentTime
                    currentHandShapeState = HandShape.UNKNOWN
                    shapeStartTime = 0L
                    pointHistory.clear()
                } else if (currentDirection == Direction.NONE && currentTime - shapeStartTime >= GESTURE_HOLD_DURATION_MS) {
                     // Check if there's a NONE direction action
                     val defaultAction = repository.getActionFor(detectedShape, Direction.NONE)
                     if (defaultAction != GestureAction.NONE) {
                         Log.d("GestureEngine", "Triggered Static: $detectedShape -> $defaultAction")
                         actionDispatcher.dispatchAction(defaultAction.name)

                         lastGestureTime = currentTime
                         currentHandShapeState = HandShape.UNKNOWN
                         shapeStartTime = 0L
                         pointHistory.clear()
                     }
                }
            }
        } else {
            currentHandShapeState = detectedShape
            shapeStartTime = currentTime
        }
    }
}