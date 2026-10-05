package com.user.jarvis

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

class AimAssistOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var crosshairView: ImageView
    private lateinit var layoutParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        crosshairView = ImageView(this).apply {
            // Draw a simple green crosshair programmatically to avoid adding drawables
            setImageResource(android.R.drawable.ic_menu_add)
            setColorFilter(Color.GREEN)
            scaleX = 1.5f
            scaleY = 1.5f
        }

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
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.CENTER

        // Allow dragging the crosshair
        crosshairView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(crosshairView, layoutParams)
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(crosshairView, layoutParams)

            val manager = GamingModeManager.getInstance(this)
            manager.isAimAssistOn = true

            android.util.Log.i("AimAssistOverlayService", "Aim assist overlay started successfully.")

            val updateIntent = Intent("com.user.jarvis.GAMING_MODE_UPDATED")
            sendBroadcast(updateIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            val manager = GamingModeManager.getInstance(this)
            manager.isAimAssistOn = false

            android.util.Log.e("AimAssistOverlayService", "Failed to start aim assist overlay. Permission failure logged.", e)

            val updateIntent = Intent("com.user.jarvis.GAMING_MODE_UPDATED")
            sendBroadcast(updateIntent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::crosshairView.isInitialized) {
            try {
                windowManager.removeView(crosshairView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val manager = GamingModeManager.getInstance(this)
        manager.isAimAssistOn = false

        android.util.Log.i("AimAssistOverlayService", "Aim assist overlay stopped.")

        val updateIntent = Intent("com.user.jarvis.GAMING_MODE_UPDATED")
        sendBroadcast(updateIntent)
    }
}
