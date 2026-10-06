package com.user.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class GaugeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#151522")
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FFFF") // Cyan
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(10f, 0f, 0f, Color.parseColor("#8000FFFF"))
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 24f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A0A0AA")
        textAlign = Paint.Align.CENTER
        textSize = 18f
    }

    var progress: Float = 0f // 0 to 1
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var label: String = "DATA"
        set(value) {
            field = value
            invalidate()
        }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (Math.min(width, height) / 2f) - 15f

        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // Background track (240 degrees)
        canvas.drawArc(rect, 150f, 240f, false, bgPaint)

        // Progress arc
        val sweepAngle = 240f * progress
        canvas.drawArc(rect, 150f, sweepAngle, false, progressPaint)

        // Percentage text
        val pct = (progress * 100).toInt()
        canvas.drawText("$pct%", cx, cy + 5f, textPaint)

        // Label text below
        canvas.drawText(label, cx, cy + 30f, labelPaint)
    }
}
