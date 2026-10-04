package com.user.jarvis

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.Executors

class HandLandmarkerManager(
    private val context: Context,
    private val listener: LandmarkListener
) {
    interface LandmarkListener {
        fun onLandmarks(landmarks: List<NormalizedLandmark>, imageWidth: Int, imageHeight: Int)
        fun onError(error: String)
    }

    private var handLandmarker: HandLandmarker? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null

    init {
        setupHandLandmarker()
    }

    private fun setupHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result: HandLandmarkerResult, inputImage: com.google.mediapipe.framework.image.MPImage ->
                    if (result.landmarks().isNotEmpty()) {
                        listener.onLandmarks(result.landmarks()[0], inputImage.width, inputImage.height)
                    }
                }
                .setErrorListener { error: RuntimeException ->
                    listener.onError(error.message ?: "Unknown error")
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            listener.onError("HandLandmarker failed to initialize: ${e.message}")
        }
    }

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        // Ensure lifecycle context is provided. Here we assume it's castable,
        // in production might need a more robust way to get lifecycle
        val lifecycleOwner = context as? LifecycleOwner ?: return

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(executor) { imageProxy ->
            processImageProxy(imageProxy)
        }

        try {
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalyzer!!
            )
        } catch (e: Exception) {
            listener.onError("Camera bind failed: ${e.message}")
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val handLandmarker = handLandmarker ?: return

        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        imageProxy.close()

        val mpImage = BitmapImageBuilder(bitmapBuffer).build()
        val timestamp = System.currentTimeMillis()

        try {
            handLandmarker.detectAsync(mpImage, timestamp)
        } catch (e: Exception) {
            Log.e("HandLandmarkerManager", "Error recognizing hand", e)
        }
    }

    fun stopCamera() {
        imageAnalyzer?.clearAnalyzer()
        imageAnalyzer?.let {
            cameraProvider?.unbind(it)
        }
    }

    fun close() {
        try {
            handLandmarker?.close()
        } catch (e: Exception) {
            Log.e("HandLandmarkerManager", "Error closing HandLandmarker", e)
        }
    }
}