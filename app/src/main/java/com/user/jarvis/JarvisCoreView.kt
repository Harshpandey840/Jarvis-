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
        IDLE, LISTENING, PROCESSING, SPEAKING
    }

    var currentState = State.IDLE
        set(value) {
            field = value
            invalidate()
        }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4000FFFF") // Cyan glow
        style = Paint.Style.STROKE
        strokeWidth = 20f
        setShadowLayer(30f, 0f, 0f, Color.parseColor("#4000FFFF"))
    }

    private val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FFFF") // Cyan solid
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8000FFFF") // Semi-transparent cyan
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private var rotationAngle = 0f
    private var pulseScale = 1f
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
            duration = 2000
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
        val maxRadius = (Math.min(width, height) / 2f) - 30f

        val currentScale = pulseScale * externalPulse

        val rotationSpeedMultiplier = when(currentState) {
            State.IDLE -> 1f
            State.LISTENING -> 2.5f
            State.PROCESSING -> 5f
            State.SPEAKING -> 1.5f
        }

        val activeRotation = (rotationAngle * rotationSpeedMultiplier) % 360f

        glowPaint.strokeWidth = 15f * currentScale
        canvas.drawCircle(cx, cy, maxRadius * 0.9f * currentScale, glowPaint)

        val outerRect = RectF(cx - maxRadius, cy - maxRadius, cx + maxRadius, cy + maxRadius)
        canvas.save()
        canvas.rotate(activeRotation, cx, cy)
        for (i in 0 until 4) {
            canvas.drawArc(outerRect, (i * 90).toFloat(), 60f, false, solidPaint)
        }
        canvas.restore()

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
                val longStartX = cx + (tickRadius * 0.9f * currentScale) * cos(angle).toFloat()
                val longStartY = cy + (tickRadius * 0.9f * currentScale) * sin(angle).toFloat()
                canvas.drawLine(longStartX, longStartY, endX, endY, tickPaint)
            } else {
                tickPaint.strokeWidth = 2f
                canvas.drawLine(startX, startY, endX, endY, tickPaint)
            }
        }
        canvas.restore()

        val innerRadius = maxRadius * 0.65f
        canvas.drawCircle(cx, cy, innerRadius * currentScale, solidPaint)

        val innerRect = RectF(cx - innerRadius*0.9f, cy - innerRadius*0.9f, cx + innerRadius*0.9f, cy + innerRadius*0.9f)
        canvas.save()
        canvas.rotate(activeRotation * 1.5f, cx, cy)
        solidPaint.strokeWidth = 8f
        canvas.drawArc(innerRect, 0f, 120f, false, solidPaint)
        canvas.drawArc(innerRect, 180f, 120f, false, solidPaint)
        solidPaint.strokeWidth = 4f
        canvas.restore()

        if (currentState == State.LISTENING || currentState == State.SPEAKING) {
            glowPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, maxRadius * 0.2f * currentScale, glowPaint)
            glowPaint.style = Paint.Style.STROKE
        } else {
            canvas.drawCircle(cx, cy, maxRadius * 0.1f * currentScale, solidPaint)
        }
    }
}
