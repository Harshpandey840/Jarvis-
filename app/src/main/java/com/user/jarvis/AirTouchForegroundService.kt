package com.user.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

class AirTouchForegroundService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private var handLandmarkerManager: HandLandmarkerManager? = null
    private var cursorOverlayManager: CursorOverlayManager? = null

    companion object {
        const val CHANNEL_ID = "AirTouchChannel"
        const val NOTIFICATION_ID = 101

        fun start(context: Context) {
            val intent = Intent(context, AirTouchForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AirTouchForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AirTouchService", "Service starting")
        startForeground(NOTIFICATION_ID, createNotification())
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        cursorOverlayManager = CursorOverlayManager(applicationContext)
        cursorOverlayManager?.show()
        val gestureEngine = com.user.jarvis.gestures.GestureEngine(applicationContext)

        // Pass "this" (the service) as the context and LifecycleOwner
        handLandmarkerManager = HandLandmarkerManager(this, object : HandLandmarkerManager.LandmarkListener {
            override fun onLandmarks(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>, imageWidth: Int, imageHeight: Int) {
                gestureEngine.processLandmarks(landmarks, imageWidth, imageHeight, cursorOverlayManager)
            }
            override fun onError(error: String) {
                Log.e("AirTouchService", "Hand tracking error: $error")
            }
        })
        handLandmarkerManager?.startCamera()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("AirTouchService", "Service destroying")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        handLandmarkerManager?.stopCamera()
        handLandmarkerManager?.close()
        handLandmarkerManager = null

        cursorOverlayManager?.hide()
        cursorOverlayManager = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AirTouch Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs the AirTouch camera pipeline"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis AirTouch")
            .setContentText("Gesture control active")
            .setSmallIcon(android.R.drawable.presence_online) // Use a better icon if available
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
