package rs.snoreguard.mvp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Lightweight offline chart. No external chart libraries, screenshots or audio.
 * The parent supplies exact counts, and no data is never inferred as zero.
 */
class SnoreBarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.primary) }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.card_outline)
        strokeWidth = 1f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_secondary)
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_primary)
        textSize = 12f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private var titles = emptyList<String>()
    private var amounts = emptyList<Int>()
    private var hasData = false

    fun showSeries(labels: List<String>, values: List<Int>, available: Boolean = true, unit: String = "detektovanih događaja") {
        require(labels.size == values.size)
        if (titles == labels && amounts == values && hasData == available) return
        titles = labels.toList()
        amounts = values.map { it.coerceAtLeast(0) }
        hasData = available
        contentDescription = if (available) {
            labels.indices.joinToString("; ") { "${labels[it]}: ${amounts[it]} $unit" }
        } else "Nema satnih podataka za ovu sesiju"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 15f * density
        val right = width - 15f * density
        val top = 28f * density
        val bottom = height - 33f * density
        if (bottom <= top || right <= left) return
        canvas.drawLine(left, bottom, right, bottom, baselinePaint)
        if (!hasData || titles.isEmpty()) {
            canvas.drawText("Nema satnih podataka", width / 2f, height / 2f, valuePaint)
            return
        }
        val maxValue = max(1, amounts.maxOrNull() ?: 0)
        val slot = (right - left) / titles.size
        val barWidth = slot * 0.53f
        val graphHeight = bottom - top
        titles.indices.forEach { i ->
            val cx = left + slot * (i + 0.5f)
            val count = amounts[i]
            val barTop = bottom - graphHeight * count / maxValue
            if (count > 0) {
                canvas.drawRoundRect(
                    RectF(cx - barWidth / 2, barTop, cx + barWidth / 2, bottom),
                    4f * density, 4f * density, barPaint
                )
                canvas.drawText(count.toString(), cx, max(13f * density, barTop - 5f * density), valuePaint)
            }
            canvas.drawText(titles[i], cx, bottom + 17f * density, labelPaint)
        }
    }
}
