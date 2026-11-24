package rs.snoreguard.mvp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class ClipWaveformView(context: Context, private val peaks: IntArray) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = resources.displayMetrics.density * 2 }
    var progress = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    init { contentDescription = "Talasni prikaz lokalnog WAV snimka; nije kalibrisana glasnoća." }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (peaks.isEmpty()) return
        val step = width.toFloat() / peaks.size
        val middle = height / 2f
        for (i in peaks.indices) {
            paint.color = context.getColor(if (i.toFloat() / peaks.size < progress) R.color.primary else R.color.text_secondary)
            val amplitude = (peaks[i] / 32768f * (middle - 4f)).coerceAtLeast(1f)
            val x = step * (i + 0.5f)
            canvas.drawLine(x, middle - amplitude, x, middle + amplitude, paint)
        }
    }
}
