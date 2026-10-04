package com.user.jarvis

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView

class CursorOverlayManager(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var cursorView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isShowing = false

    private var screenWidth = 0
    private var screenHeight = 0

    // Smoothing factor
    private var currentX = 0f
    private var currentY = 0f
    private var alpha = 0.3f // Lower means smoother but slower

    fun setSmoothing(newAlpha: Float) {
        alpha = newAlpha
    }

    fun show() {
        if (isShowing) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val metrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        currentX = screenWidth / 2f
        currentY = screenHeight / 2f

        val smoothingValue = AirTouchSettings.getCursorSmoothing(context)
        alpha = smoothingValue / 100f

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentX.toInt()
            y = currentY.toInt()
        }

        cursorView = FrameLayout(context).apply {
            val icon = ImageView(context).apply {
                setImageResource(android.R.drawable.presence_online) // Simple dot indicator
                layoutParams = FrameLayout.LayoutParams(40, 40)
            }
            addView(icon)
        }

        try {
            windowManager?.addView(cursorView, layoutParams)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updatePosition(normalizedX: Float, normalizedY: Float) {
        if (!isShowing || layoutParams == null || cursorView == null) return

        val targetX = normalizedX * screenWidth
        val targetY = normalizedY * screenHeight

        // Exponential moving average for smoothing
        currentX = alpha * targetX + (1 - alpha) * currentX
        currentY = alpha * targetY + (1 - alpha) * currentY

        // Update layout params on UI thread
        cursorView?.post {
            layoutParams?.x = currentX.toInt()
            layoutParams?.y = currentY.toInt()
            AirTouchAccessibilityService.instance?.updateCursorPosition(currentX, currentY)
            windowManager?.updateViewLayout(cursorView, layoutParams)
        }
    }

    fun hide() {
        if (!isShowing) return
        try {
            windowManager?.removeView(cursorView)
            isShowing = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}