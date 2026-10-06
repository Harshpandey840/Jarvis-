package com.user.jarvis

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

class JarvisCoreView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State {
        IDLE, LISTENING, PROCESSING, SPEAKING, SUCCESS, ERROR
    }

    var currentState = State.IDLE
        set(value) {
            field = value
            updateStateColors()
            invalidate()
        }

    private var activeColor = Color.parseColor("#00FFFF")
    private var activeGlowColor = Color.parseColor("#4000FFFF")

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = activeGlowColor
        style = Paint.Style.STROKE
        strokeWidth = 20f
        setShadowLayer(40f, 0f, 0f, activeGlowColor)
    }

    private val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = activeColor
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8000FFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val secondaryGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#208800FF") // subtle purple
        style = Paint.Style.STROKE
        strokeWidth = 10f
        setShadowLayer(50f, 0f, 0f, Color.parseColor("#408800FF"))
    }

    private var rotationAngle = 0f
    private var pulseScale = 1f
    private var particleAngles = FloatArray(15) { (Math.random() * 360).toFloat() }
    private var particleRadii = FloatArray(15) { (Math.random() * 0.4 + 0.1).toFloat() }
    private var particleSpeeds = FloatArray(15) { (Math.random() * 2 + 0.5).toFloat() }

    var externalPulse = 1f
        set(value) {
            field = value
            invalidate()
        }

    private var animator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun updateStateColors() {
        when(currentState) {
            State.ERROR -> {
                activeColor = Color.parseColor("#FF3333")
                activeGlowColor = Color.parseColor("#40FF3333")
            }
            State.SUCCESS -> {
                activeColor = Color.parseColor("#00FF88")
                activeGlowColor = Color.parseColor("#4000FF88")
            }
            else -> {
                activeColor = Color.parseColor("#00FFFF")
                activeGlowColor = Color.parseColor("#4000FFFF")
            }
        }
        solidPaint.color = activeColor
        glowPaint.color = activeGlowColor
        glowPaint.setShadowLayer(40f, 0f, 0f, activeGlowColor)

        tickPaint.color = if (currentState == State.ERROR) {
            Color.parseColor("#80FF3333")
        } else if (currentState == State.SUCCESS) {
            Color.parseColor("#8000FF88")
        } else {
            Color.parseColor("#8000FFFF")
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimations()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        pulseAnimator?.cancel()
    }

    private fun startAnimations() {
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 10000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        pulseAnimator = ValueAnimator.ofFloat(0.95f, 1.05f).apply {
            duration = 2500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = (Math.min(width, height) / 2f) - 40f

        val currentScale = pulseScale * externalPulse

        val rotationSpeedMultiplier = when(currentState) {
            State.IDLE -> 0.5f
            State.LISTENING -> 2.5f
            State.PROCESSING -> 6f
            State.SPEAKING -> 1.5f
            State.SUCCESS, State.ERROR -> 3f
        }

        val activeRotation = (rotationAngle * rotationSpeedMultiplier) % 360f

        // Update and Draw Particles
        for (i in 0 until 15) {
            particleAngles[i] = (particleAngles[i] + particleSpeeds[i] * rotationSpeedMultiplier) % 360f
            val rad = Math.toRadians(particleAngles[i].toDouble())
            val pX = cx + (maxRadius * particleRadii[i] * currentScale) * cos(rad).toFloat()
            val pY = cy + (maxRadius * particleRadii[i] * currentScale) * sin(rad).toFloat()

            val pAlpha = if (currentState == State.PROCESSING) 200 else 100
            val pPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = activeColor
                alpha = pAlpha
                style = Paint.Style.FILL
            }
            canvas.drawCircle(pX, pY, 3f * currentScale, pPaint)
        }

        // Draw secondary subtle glow
        canvas.drawCircle(cx, cy, maxRadius * 0.95f * pulseScale, secondaryGlowPaint)

        // Main glow
        glowPaint.strokeWidth = 15f * currentScale
        canvas.drawCircle(cx, cy, maxRadius * 0.9f * currentScale, glowPaint)

        // Segmented outer rings
        val outerRect = RectF(cx - maxRadius, cy - maxRadius, cx + maxRadius, cy + maxRadius)
        canvas.save()
        canvas.rotate(activeRotation, cx, cy)
        val outerSegments = if (currentState == State.PROCESSING) 8 else 4
        val outerSweep = if (currentState == State.PROCESSING) 30f else 60f
        val outerStep = 360 / outerSegments

        for (i in 0 until outerSegments) {
            canvas.drawArc(outerRect, (i * outerStep).toFloat(), outerSweep, false, solidPaint)
        }
        canvas.restore()

        // Inner scanner ticks
        val tickRadius = maxRadius * 0.8f
        canvas.save()
        canvas.rotate(-activeRotation * 0.7f, cx, cy)
        for (i in 0 until 72) {
            val angle = Math.toRadians((i * 5).toDouble())
            val startX = cx + (tickRadius * 0.95f * currentScale) * cos(angle).toFloat()
            val startY = cy + (tickRadius * 0.95f * currentScale) * sin(angle).toFloat()
            val endX = cx + (tickRadius * currentScale) * cos(angle).toFloat()
            val endY = cy + (tickRadius * currentScale) * sin(angle).toFloat()

            if (i % 6 == 0) {
                tickPaint.strokeWidth = 5f
                val longStartX = cx + (tickRadius * 0.85f * currentScale) * cos(angle).toFloat()
                val longStartY = cy + (tickRadius * 0.85f * currentScale) * sin(angle).toFloat()
                canvas.drawLine(longStartX, longStartY, endX, endY, tickPaint)
            } else {
                tickPaint.strokeWidth = 2f
                canvas.drawLine(startX, startY, endX, endY, tickPaint)
            }
        }
        canvas.restore()

        // Inner solid ring
        val innerRadius = maxRadius * 0.65f
        solidPaint.strokeWidth = 3f
        canvas.drawCircle(cx, cy, innerRadius * currentScale, solidPaint)

        // Inner animated arcs
        val innerRect = RectF(cx - innerRadius*0.9f, cy - innerRadius*0.9f, cx + innerRadius*0.9f, cy + innerRadius*0.9f)
        canvas.save()
        canvas.rotate(activeRotation * 1.8f, cx, cy)
        solidPaint.strokeWidth = 6f
        canvas.drawArc(innerRect, 0f, 120f, false, solidPaint)
        canvas.drawArc(innerRect, 180f, 120f, false, solidPaint)
        solidPaint.strokeWidth = 4f
        canvas.restore()

        // Core center
        if (currentState == State.LISTENING || currentState == State.SPEAKING) {
            glowPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, maxRadius * 0.25f * currentScale, glowPaint)
            glowPaint.style = Paint.Style.STROKE
        } else if (currentState == State.PROCESSING) {
            glowPaint.style = Paint.Style.FILL
            // Draw a smaller, tighter pulsing core
            canvas.drawCircle(cx, cy, maxRadius * 0.15f * (1f + (pulseScale - 1f)*2f), glowPaint)
            glowPaint.style = Paint.Style.STROKE
        } else {
            canvas.drawCircle(cx, cy, maxRadius * 0.15f * currentScale, solidPaint)
        }
    }
}
