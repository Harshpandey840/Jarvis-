package com.user.jarvis

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

class GaugeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#101018")
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FFFF") // Cyan
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(15f, 0f, 0f, Color.parseColor("#8000FFFF"))
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2000FFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4000FFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 30f
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88AABB")
        textAlign = Paint.Align.CENTER
        textSize = 20f
        typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }

    private var targetProgress: Float = 0f
    private var animatedProgress: Float = 0f
    private var progressAnimator: ValueAnimator? = null

    var progress: Float = 0f // 0 to 1
        set(value) {
            field = value.coerceIn(0f, 1f)
            targetProgress = field
            animateProgress()
        }

    var label: String = "DATA"
        set(value) {
            field = value
            invalidate()
        }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun animateProgress() {
        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(animatedProgress, targetProgress).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animatedProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (Math.min(width, height) / 2f) - 20f

        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // Draw thin outer technical ring
        canvas.drawCircle(cx, cy, radius + 10f, outlinePaint)

        // Draw inner ticks
        val tickRadius = radius - 15f
        for (i in 0..20) {
            val angle = Math.toRadians(150.0 + (i * (240.0 / 20.0)))
            val startX = cx + tickRadius * cos(angle).toFloat()
            val startY = cy + tickRadius * sin(angle).toFloat()
            val endX = cx + (tickRadius - 5f) * cos(angle).toFloat()
            val endY = cy + (tickRadius - 5f) * sin(angle).toFloat()
            canvas.drawLine(startX, startY, endX, endY, tickPaint)
        }

        // Background track (240 degrees)
        canvas.drawArc(rect, 150f, 240f, false, bgPaint)

        // Progress arc
        val sweepAngle = 240f * animatedProgress

        // Color transition based on value
        progressPaint.color = when {
            animatedProgress > 0.85f -> Color.parseColor("#FF3333") // Red for high usage
            animatedProgress > 0.65f -> Color.parseColor("#FF8800") // Orange for medium
            else -> Color.parseColor("#00FFFF") // Cyan for normal
        }
        progressPaint.setShadowLayer(15f, 0f, 0f, progressPaint.color)

        canvas.drawArc(rect, 150f, sweepAngle, false, progressPaint)

        // Percentage text
        if (label.contains("--") || label.contains("UNAVAILABLE")) {
            canvas.drawText("--", cx, cy + 10f, textPaint)
        } else {
            val pct = (animatedProgress * 100).toInt()
            canvas.drawText("$pct%", cx, cy + 10f, textPaint)
        }

        // Label text below
        canvas.drawText(label.replace(" --", ""), cx, cy + 40f, labelPaint)
    }
}
