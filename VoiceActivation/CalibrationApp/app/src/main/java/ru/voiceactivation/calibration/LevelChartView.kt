package ru.voiceactivation.calibration

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class LevelChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val signal = ArrayDeque<Float>()
    private val noise = ArrayDeque<Float>()
    private val threshold = ArrayDeque<Float>()
    private val signalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff1565c0.toInt(); strokeWidth = 4f; style = Paint.Style.STROKE }
    private val noisePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff2e7d32.toInt(); strokeWidth = 3f; style = Paint.Style.STROKE }
    private val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffc62828.toInt(); strokeWidth = 3f; style = Paint.Style.STROKE }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffdddddd.toInt(); strokeWidth = 1f }

    fun addFrame(signalDb: Float, noiseDb: Float, thresholdDb: Float) {
        synchronized(signal) {
            signal.addLast(signalDb); noise.addLast(noiseDb); threshold.addLast(thresholdDb)
            while (signal.size > 180) signal.removeFirst()
            while (noise.size > 180) noise.removeFirst()
            while (threshold.size > 180) threshold.removeFirst()
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 12f
        val right = width - 12f
        val top = 12f
        val bottom = height - 12f
        for (i in 0..4) {
            val y = top + (bottom - top) * i / 4f
            canvas.drawLine(left, y, right, y, gridPaint)
        }
        val s: List<Float>
        val n: List<Float>
        val t: List<Float>
        synchronized(signal) { s = signal.toList(); n = noise.toList(); t = threshold.toList() }
        if (s.size < 2) return
        fun draw(values: List<Float>, paint: Paint) {
            val path = Path()
            val count = values.size
            values.forEachIndexed { i, value ->
                val v = value.coerceIn(-100f, 0f)
                val x = left + (right - left) * i / max(1, count - 1)
                val y = bottom - (v + 100f) / 100f * (bottom - top)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)
        }
        draw(s, signalPaint)
        draw(n, noisePaint)
        draw(t, thresholdPaint)
    }
}
