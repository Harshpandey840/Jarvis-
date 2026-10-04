package com.user.jarvis

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.hypot

class GestureRecognizer(private val context: Context) {

    private val actionDispatcher = GestureActionDispatcher(context)
    private var lastGestureTime = 0L

    private var currentGestureState = ""
    private var gestureStartTime = 0L
    private val GESTURE_HOLD_DURATION_MS = 500L

    private var lastHandX = 0f
    private var swipeStartTime = 0L
    private val SWIPE_THRESHOLD = 0.2f

    fun processLandmarks(
        landmarks: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int,
        cursorManager: CursorOverlayManager?
    ) {
        if (landmarks.size < 21) return

        val indexTip = landmarks[8]
        val thumbTip = landmarks[4]
        val middleTip = landmarks[12]

        // Update cursor position if cursor is enabled
        cursorManager?.updatePosition(1f - indexTip.x(), indexTip.y())

        val currentTime = System.currentTimeMillis()
        val cooldownMs = AirTouchSettings.getGestureCooldown(context).toLong()
        if (currentTime - lastGestureTime < cooldownMs) return

        // Calculate distances
        val pinchDist = hypot((indexTip.x() - thumbTip.x()).toDouble(), (indexTip.y() - thumbTip.y()).toDouble())

        val thumbBase = landmarks[2]
        val indexBase = landmarks[5]
        val middleBase = landmarks[9]
        val ringBase = landmarks[13]
        val pinkyBase = landmarks[17]

        val ringTip = landmarks[16]
        val pinkyTip = landmarks[20]

        val isIndexExtended = indexTip.y() < indexBase.y()
        val isMiddleExtended = middleTip.y() < middleBase.y()
        val isRingExtended = ringTip.y() < ringBase.y()
        val isPinkyExtended = pinkyTip.y() < pinkyBase.y()

        val isThumbExtended = thumbTip.x() > thumbBase.x() + 0.05f || thumbTip.x() < thumbBase.x() - 0.05f // simplify

        val currentGesture = when {
            pinchDist < 0.05 && AirTouchSettings.isGestureEnabled(context, "PINCH") -> "PINCH"
            isIndexExtended && isMiddleExtended && isRingExtended && isPinkyExtended && AirTouchSettings.isGestureEnabled(context, "OPEN_PALM") -> "OPEN_PALM"
            !isIndexExtended && !isMiddleExtended && !isRingExtended && !isPinkyExtended && isThumbExtended && AirTouchSettings.isGestureEnabled(context, "THUMBS_UP") -> "THUMBS_UP"
            !isIndexExtended && !isMiddleExtended && !isRingExtended && !isPinkyExtended && AirTouchSettings.isGestureEnabled(context, "FIST") -> "FIST"
            isIndexExtended && isMiddleExtended && !isRingExtended && !isPinkyExtended && AirTouchSettings.isGestureEnabled(context, "TWO_FINGERS") -> "TWO_FINGERS"
            pinchDist < 0.05 && isMiddleExtended && isRingExtended && isPinkyExtended && AirTouchSettings.isGestureEnabled(context, "OK") -> "OK"
            else -> "UNKNOWN"
        }

        // Handle swipes
        if (currentGesture == "OPEN_PALM" || currentGesture == "TWO_FINGERS") {
            if (currentTime - swipeStartTime > 1000) {
                 lastHandX = indexBase.x()
                 swipeStartTime = currentTime
            } else {
                 val diff = indexBase.x() - lastHandX
                 if (diff > SWIPE_THRESHOLD) {
                     Log.d("GestureRecognizer", "Swipe Right")
                     actionDispatcher.dispatchScrollDown()
                     lastGestureTime = currentTime
                     swipeStartTime = 0L
                     return
                 } else if (diff < -SWIPE_THRESHOLD) {
                     Log.d("GestureRecognizer", "Swipe Left")
                     actionDispatcher.dispatchScrollUp()
                     lastGestureTime = currentTime
                     swipeStartTime = 0L
                     return
                 }
            }
        } else {
            swipeStartTime = 0L
        }

        if (currentGesture == currentGestureState) {
            if (currentGesture != "UNKNOWN" && currentTime - gestureStartTime >= GESTURE_HOLD_DURATION_MS) {
                // Trigger action
                val actionName = AirTouchSettings.getGestureAction(context, currentGesture, getDefaultAction(currentGesture))
                Log.d("GestureRecognizer", "$currentGesture -> $actionName")

                when (currentGesture) {
                    "PINCH" -> actionDispatcher.dispatchClick() // Pinch always clicks, or we could make it configurable too
                    "TWO_FINGERS" -> actionDispatcher.dispatchScrollDown() // Hardcoded scroll down for two fingers, we handle swipe in the swipe block
                    else -> actionDispatcher.dispatchAction(actionName)
                }
                lastGestureTime = currentTime
                currentGestureState = "" // Reset
            }
        } else {
            currentGestureState = currentGesture
            gestureStartTime = currentTime
        }

    }

    private fun getDefaultAction(gesture: String): String {
        return when (gesture) {
            "FIST" -> "BACK"
            "OPEN_PALM" -> "HOME"
            "THUMBS_UP" -> "HOME"
            "OK" -> "RECENTS"
            else -> "HOME"
        }
    }
}