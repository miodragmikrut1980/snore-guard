package rs.snoreguard.mvp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SleepTimelineView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var night: SleepTimeline.Night? = null
    private var start = 0L
    private var end = 0L
    private var snores = emptyList<Long>()
    private var alerts = emptyList<Long>()
    fun show(value: SleepTimeline.Night?, from: Long = 0L, to: Long = 0L,
             events: List<Long> = emptyList(), notifications: List<Long> = emptyList()) {
        night = value; start = from; end = to; snores = events; alerts = notifications
        visibility = if (value == null) GONE else VISIBLE
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = night ?: return
        val from = minOf(start, n.start)
        val to = maxOf(end, n.end)
        if (to <= from) return
        val d = resources.displayMetrics.density
        val left = 60*d; val right = width - 8*d
        if (right <= left) return
        fun x(ms: Long): Float = left + ((ms-from).toDouble()/(to-from)).coerceIn(0.0,1.0).toFloat()*(right-left)
        val kinds = SleepTimeline.Kind.values()
        val colors = intArrayOf(0xfffbbf24.toInt(),0xffa78bfa.toInt(),0xff60a5fa.toInt(),
            0xff34d399.toInt(),0xff94a3b8.toInt(),0xff64748b.toInt())
        paint.textSize = 10*d
        kinds.forEachIndexed { index, kind ->
            val y = (16 + index*22)*d
            paint.color = 0xffcbd5e1.toInt(); canvas.drawText(kind.label, 0f, y+3*d, paint)
            paint.color = 0xff334155.toInt(); canvas.drawLine(left,y,right,y,paint)
            paint.color = colors[index]
            n.stages.filter { it.kind == kind && it.start >= n.start && it.end <= n.end && it.end > it.start }
                .forEach { canvas.drawRect(x(it.start),y-5*d,x(it.end),y+5*d,paint) }
        }
        val lanes = listOf(Triple("Hrkanje", snores, 0xfff59e0b.toInt()), Triple("Obav.", alerts, 0xfff87171.toInt()))
        lanes.forEachIndexed { i, lane ->
            val y = (153+i*22)*d
            paint.color = 0xffcbd5e1.toInt(); canvas.drawText(lane.first,0f,y+3*d,paint)
            paint.color = 0xff334155.toInt(); canvas.drawLine(left,y,right,y,paint)
            paint.color = lane.third
            lane.second.forEach { canvas.drawLine(x(it),y-7*d,x(it),y+7*d,paint) }
        }
        val fmt = SimpleDateFormat("HH:mm", Locale.ROOT)
        paint.color = 0xffcbd5e1.toInt()
        canvas.drawText(fmt.format(Date(from)),left,201*d,paint)
        val label = fmt.format(Date(to)); canvas.drawText(label,right-paint.measureText(label),201*d,paint)
    }
}
