package com.user.jarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AirTouchAccessibilityService : AccessibilityService() {

    private var cursorX: Float = 0f
    private var cursorY: Float = 0f

    companion object {
        var instance: AirTouchAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AirTouchService", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't actively need to process events for this feature right now,
        // we are primarily dispatching actions.
    }

    override fun onInterrupt() {
        Log.d("AirTouchService", "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun updateCursorPosition(x: Float, y: Float) {
        cursorX = x
        cursorY = y
    }

    fun performClick() {
        Log.d("AirTouchService", "Performing click at $cursorX, $cursorY")
        if (cursorX == 0f && cursorY == 0f) return // Unknown position

        val path = Path()
        path.moveTo(cursorX, cursorY)
        val builder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, 50)
        builder.addStroke(strokeDescription)

        val gesture = builder.build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d("AirTouchService", "Click completed successfully")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.d("AirTouchService", "Click cancelled")
            }
        }, null)
    }

    fun performScroll(direction: Int) {
        val displayMetrics = resources.displayMetrics
        val middleX = displayMetrics.widthPixels / 2f
        val startY = displayMetrics.heightPixels / 2f

        // direction 1 for scroll down (swipe up), -1 for scroll up (swipe down)
        val endY = startY - (direction * 400f)

        val path = Path()
        path.moveTo(middleX, startY)
        path.lineTo(middleX, endY)

        val builder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, 300)
        builder.addStroke(strokeDescription)

        dispatchGesture(builder.build(), null, null)
    }
}