package com.user.jarvis

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

class ParticleSphereView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0FF") // Light particle color
        style = Paint.Style.FILL
    }

    private val points = mutableListOf<Point3D>()
    private var rotationAngle = 0f
    private var animator: ValueAnimator? = null

    // Scale factor controlled externally (e.g. by RMS audio levels)
    var pulseScale = 1f
        set(value) {
            field = value
            invalidate()
        }

    data class Point3D(val x: Float, val y: Float, val z: Float)

    init {
        generatePoints()
    }

    private fun generatePoints() {
        val numLat = 18 // number of latitude rings
        val numLon = 36 // number of longitude points per ring

        for (i in 0..numLat) {
            val lat = Math.PI * i / numLat
            val sinLat = sin(lat).toFloat()
            val cosLat = cos(lat).toFloat()

            for (j in 0 until numLon) {
                val lon = 2 * Math.PI * j / numLon
                val x = sinLat * cos(lon).toFloat()
                val y = sinLat * sin(lon).toFloat()
                val z = cosLat

                // Add some subtle pseudo-random noise to make it feel organic
                val noiseX = (Math.random() - 0.5f).toFloat() * 0.02f
                val noiseY = (Math.random() - 0.5f).toFloat() * 0.02f
                val noiseZ = (Math.random() - 0.5f).toFloat() * 0.02f

                points.add(Point3D(x + noiseX, y + noiseY, z + noiseZ))
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    private fun startAnimation() {
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 15000 // 15 seconds for a full rotation (slow & elegant)
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        // Leave some padding
        val radius = (Math.min(cx, cy) - 20f) * pulseScale

        val angleRad = Math.toRadians(rotationAngle.toDouble())
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()

        for (p in points) {
            // Rotate around Y axis
            val rotatedX = p.x * cosA - p.z * sinA
            val rotatedZ = p.x * sinA + p.z * cosA

            // Slight tilt (rotate around X axis)
            val tiltAngle = 0.35f
            val cosT = cos(tiltAngle.toDouble()).toFloat()
            val sinT = sin(tiltAngle.toDouble()).toFloat()

            val y2 = p.y * cosT - rotatedZ * sinT
            val z2 = p.y * sinT + rotatedZ * cosT

            val projectedX = cx + rotatedX * radius
            val projectedY = cy + y2 * radius

            // Depth determines alpha and size
            val depth = (z2 + 1f) / 2f // roughly 0 to 1
            val alpha = (50 + 205 * depth.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
            val pointSize = (1.5f + 3.0f * depth.coerceIn(0f, 1f)) * pulseScale

            paint.alpha = alpha
            canvas.drawCircle(projectedX, projectedY, pointSize, paint)
        }
    }
}
