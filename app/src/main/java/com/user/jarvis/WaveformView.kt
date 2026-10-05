package com.user.jarvis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FFFF")
        style = Paint.Style.FILL
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val bars = FloatArray(10) { 0.1f }

    var rms: Float = 0f
        set(value) {
            field = value
            updateBars()
        }

    private fun updateBars() {
        for (i in 0 until bars.size - 1) {
            bars[i] = bars[i + 1]
        }
        bars[bars.size - 1] = (rms / 10f).coerceIn(0.1f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val barWidth = width / (bars.size * 2f)
        val space = barWidth

        val cy = height / 2f

        var startX = space / 2f
        for (bar in bars) {
            val barHeight = (height * bar) / 2f
            canvas.drawLine(startX, cy - barHeight, startX, cy + barHeight, paint)
            startX += barWidth + space
        }
    }
}
