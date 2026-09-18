package com.user.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceSecurityService : Service(), LifecycleOwner {

    companion object {
        const val CHANNEL_ID = "jarvis_face_security_channel"
        const val NOTIFICATION_ID = 2
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val handler = Handler(Looper.getMainLooper())
    private var cameraProvider: ProcessCameraProvider? = null
    private var attemptCount = 0
    private lateinit var faceEmbedder: FaceEmbedder

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        createNotificationChannel()
        faceEmbedder = FaceEmbedder(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Security check chal raha hai...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        attemptCount = 0
        val referencePath = FaceSecurityPrefs.getReferenceFacePath(this)
        if (referencePath == null || faceEmbedder.lastError != null) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }
        startCameraCheck(referencePath)
        return START_NOT_STICKY
    }

    private fun startCameraCheck(referencePath: String) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                    handleFrame(imageProxy, referencePath)
                }

                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) {
                stopSelfCleanly()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleFrame(imageProxy: ImageProxy, referencePath: String) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        val detector = FaceDetection.getClient(options)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    imageProxy.close()
                    return@addOnSuccessListener
                }
                val bitmap = imageProxyToBitmap(imageProxy)
                imageProxy.close()
                if (bitmap == null) return@addOnSuccessListener

                val box = faces[0].boundingBox
                val cropped = try {
                    Bitmap.createBitmap(
                        bitmap,
                        box.left.coerceIn(0, bitmap.width - 1),
                        box.top.coerceIn(0, bitmap.height - 1),
                        box.width().coerceIn(1, bitmap.width - box.left.coerceIn(0, bitmap.width - 1)),
                        box.height().coerceIn(1, bitmap.height - box.top.coerceIn(0, bitmap.height - 1))
                    )
                } catch (e: Exception) { null }

                if (cropped == null) return@addOnSuccessListener

                val referenceBitmap = BitmapFactory.decodeFile(referencePath)
                if (referenceBitmap == null) {
                    stopSelfCleanly()
                    return@addOnSuccessListener
                }

                val score = faceEmbedder.compare(referenceBitmap, cropped)
                stopCameraOnly()

                if (score != null && score >= 0.75f) {
                    updateNotification("Chehra match ho gaya")
                    stopSelfCleanly()
                } else {
                    attemptCount++
                    if (attemptCount < 2) {
                        updateNotification("Dobara check kar raha hoon...")
                        handler.postDelayed({ startCameraCheck(referencePath) }, 2500L)
                    } else {
                        lockPhoneNow()
                        stopSelfCleanly()
                    }
                }
            }
            .addOnFailureListener {
                imageProxy.close()
            }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun lockPhoneNow() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, JarvisDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                dpm.lockNow()
            }
        } catch (e: Exception) { }
    }

    private fun stopCameraOnly() {
        try { cameraProvider?.unbindAll() } catch (e: Exception) { }
    }

    private fun stopSelfCleanly() {
        stopCameraOnly()
        faceEmbedder.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis Security")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Jarvis Security", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        handler.removeCallbacksAndMessages(null)
        stopCameraOnly()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
