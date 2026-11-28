package rs.snoreguard.mvp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToLong

class StatsDashboardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {
    private val mint = context.getColor(R.color.primary)
    private val main = context.getColor(R.color.text_primary)
    private val muted = context.getColor(R.color.text_secondary)
    private val amber = context.getColor(R.color.warning)
    private val outline = context.getColor(R.color.card_outline)
    private var expanded = false
    private var lastSignature: List<Any?>? = null
    init { orientation = VERTICAL }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun decimal(n: Double) = String.format(Locale.forLanguageTag("sr-Latn-RS"), "%.1f", n)
    private fun percent(n: Double?): String = when {
        n == null -> "—"
        n > 0 && n < .1 -> "<0,1%"
        n % 1.0 == 0.0 -> n.toInt().toString() + "%"
        else -> decimal(n) + "%"
    }
    private fun duration(seconds: Double?): String {
        if (seconds == null || !seconds.isFinite() || seconds < 0) return "—"
        val s = seconds.roundToLong()
        val h = s / 3600; val m = s % 3600 / 60; val remainder = s % 60
        return if (h > 0) "${h} h ${m} min" else if (m > 0) "${m} min ${remainder} s" else "${remainder} s"
    }
    private fun surface(color: Int = context.getColor(R.color.card)): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(18).toFloat(); setStroke(dp(1), outline) }
    private fun label(s: String, size: Float, color: Int = main, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = true
        }
    private fun addText(parent: LinearLayout, s: String, size: Float, color: Int = main,
                        bold: Boolean = false, top: Int = 0): TextView {
        val t = label(s,size,color,bold)
        parent.addView(t, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) })
        return t
    }
    private fun card(title: String): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL; background = surface()
            setPadding(dp(16),dp(15),dp(16),dp(15))
        }
        addView(panel,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        addText(panel,title,17f,main,true)
        return panel
    }
    private fun tileRow(a: Pair<String,String>, b: Pair<String,String>) {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL; setBaselineAligned(false) }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        listOf(a,b).forEachIndexed { i, v ->
            val tile = LinearLayout(context).apply {
                orientation = VERTICAL; background = surface()
                setPadding(dp(12),dp(12),dp(12),dp(12))
            }
            row.addView(tile,LayoutParams(0,LayoutParams.MATCH_PARENT,1f).apply { if (i == 1) leftMargin = dp(8) })
            addText(tile,v.first,24f,main,true)
            addText(tile,v.second,12f,muted,top=4)
        }
    }
    private fun metricRow(parent: LinearLayout, name: String, value: String, color: Int = main) {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        parent.addView(row,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT).apply { topMargin = dp(9) })
        row.addView(label(name,14f,muted), LayoutParams(0,LayoutParams.WRAP_CONTENT,1f))
        row.addView(label(value,15f,color,true), LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT).apply { leftMargin=dp(8) })
    }
    private fun bar(parent: LinearLayout, r: AlertResponseTracker.Snapshot) {
        if (r.assessed == 0) return
        val line = LinearLayout(context).apply { orientation=HORIZONTAL; background=surface(outline); clipToOutline=true }
        parent.addView(line,LayoutParams(LayoutParams.MATCH_PARENT,dp(9)).apply { topMargin=dp(12) })
        listOf(r.quiet to mint,r.returned to amber,r.continued to 0xffF87171.toInt()).forEach { (count,color) ->
            if (count > 0) line.addView(View(context).apply { setBackgroundColor(color) },LayoutParams(0,LayoutParams.MATCH_PARENT,count.toFloat()))
        }
    }

    fun render(events: Int?, result: NightAnalytics.Result?, response: AlertResponseTracker.Snapshot?,
               reviews: NightAnalytics.Review) {
        val signature = listOf(events,result,response,reviews)
        if (signature == lastSignature) return
        lastSignature=signature
        removeAllViews()
        addText(this,"Pregled noći",19f,main,true)
        tileRow((events?.toString() ?: "—") to "Detektovani hrkaji",
            (result?.let { decimal(it.eventsPerHour) } ?: "—") to "Hrkanja po analiziranom satu")
        tileRow((result?.let { duration(it.analysedHours*3600) } ?: "—") to "Analizirani zvuk",
            (result?.let { percent(it.coverage*100) } ?: "—") to "Pokrivenost analize")

        val reaction=card("Reakcija nakon alarma")
        if (response == null) addText(reaction,"Nema podataka za ovu noć.",14f,muted,top=8)
        else if (response.assessed == 0) {
            addText(reaction,"Nema pouzdano procenjenih serija.",15f,muted,top=8)
            metricRow(reaction,"Započete serije",response.started.toString())
        } else {
            val hero=LinearLayout(context).apply { orientation=HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
            reaction.addView(hero,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT).apply { topMargin=dp(12) })
            val ring=ResponseRing(context,response.settledPercent ?: 0.0,percent(response.settledPercent),mint,main,outline)
            hero.addView(ring,LayoutParams(dp(100),dp(100)))
            val summary=LinearLayout(context).apply { orientation=VERTICAL }
            hero.addView(summary,LayoutParams(0,LayoutParams.WRAP_CONTENT,1f).apply { leftMargin=dp(12) })
            addText(summary,"${response.settled} od ${response.assessed}",22f,main,true)
            addText(summary,"Smirivanje u prvih 30 s",14f,muted,top=4)
            bar(reaction,response)
            metricRow(reaction,"Bez povratka do 60 s od alarma",response.quiet.toString(),mint)
            metricRow(reaction,"Povratak u prvih 60 s",response.returned.toString(),amber)
            metricRow(reaction,"Nije se smirilo u prvih 30 s",response.continued.toString(),0xffF87171.toInt())
        }
        addText(reaction,"Posmatrana reakcija na obaveštenje; vibracija sata i njen efekat nisu potvrđeni.",12f,muted,top=12)

        val timing=card("Vreme do ponovnog hrkanja")
        val t=response?.timing
        if (t == null) addText(timing,"Dostupno za nove sesije od v0.9.2.",14f,muted,top=8)
        else {
            addText(timing,duration(t.meanQuietSeconds),30f,mint,true,8)
            addText(timing,if(t.returns>0) "Prosečan period tišine · ${t.returns} povratka" else "Nema izmerenog povratka",13f,muted)
            if(t.returns>0) {
                metricRow(timing,"Najkraće",duration(t.minQuietMs/1000.0))
                metricRow(timing,"Najduže",duration(t.maxQuietMs/1000.0))
            }
            metricRow(timing,"Bez povratka tokom 10 min",t.noReturn10m.toString())
            if(t.interrupted+t.pending>0) metricRow(timing,"Nepotpuno / u toku",(t.interrupted+t.pending).toString(),amber)
        }
        val technical=card("Detalji analize")
        val toggle=Button(context).apply {
            isAllCaps=false; setTextColor(main); background=surface()
            text=if(expanded) "Sakrij tehničke detalje" else "Prikaži tehničke detalje"
        }
        technical.addView(toggle,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT).apply { topMargin=dp(8) })
        val details=LinearLayout(context).apply { orientation=VERTICAL; visibility=if(expanded) VISIBLE else GONE }
        technical.addView(details,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT))
        toggle.setOnClickListener {
            expanded=!expanded
            details.visibility=if(expanded) VISIBLE else GONE
            toggle.text=if(expanded) "Sakrij tehničke detalje" else "Prikaži tehničke detalje"
        }
        metricRow(details,"Pozitivni AI prozori",percent(result?.positivePercent))
        metricRow(details,"Indeks (zaokruženo)",result?.score?.let { "$it / 100" } ?: "—")
        addText(details,"Indeks je zaokružen udeo pozitivnih AI prozora, ne jačina hrkanja. Vrednost 0 može značiti udeo manji od 0,5%.",12f,muted,top=8)
        if(response!=null) {
            metricRow(details,"Procenjene / započete serije","${response.assessed} / ${response.started}")
            metricRow(details,"Bez povratka do 60 s",percent(response.quietPercent))
            metricRow(details,"Nepouzdane / u toku","${response.inconclusive} / ${response.pending}")
            metricRow(details,"Preskočena dodatna upozorenja",response.smartSuppressed.toString())
            metricRow(details,"Prosečno od alarma do povratka",duration(t?.meanAfterAlarmSeconds))
            if(t!=null) metricRow(details,"Povratak do 10 min",t.returns.toString())
            addText(details,"Smirivanje = najmanje 10 s bez detekcije u prvih 30 s. Procena početnog odgovora traje 60 s.",12f,muted,top=8)
        }
        addText(details,"Ocene svih sačuvanih snimaka",15f,main,true,12)
        metricRow(details,"Hrkanje / lažni alarm","${reviews.snore} / ${reviews.falseAlarm}")
        metricRow(details,"Nejasni / neocenjeni","${reviews.uncertain} / ${reviews.unreviewed}")
        metricRow(details,"Potvrđeni među jasno ocenjenim",reviews.confirmedPercent?.let { "$it% (n=${reviews.reviewed})" } ?: "—")
        addText(details,"Ocene nisu klinička tačnost AI modela. Praćenje zvuka nije dijagnoza apneje.",12f,muted,top=8)
    }

    private class ResponseRing(context: Context, private val value: Double, private val display: String,
        private val accent: Int, private val textColor: Int, private val track: Int): View(context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        init { contentDescription="Smirivanje: $display" }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val d=resources.displayMetrics.density
            val pad=7*d
            val r=RectF(pad,pad,width-pad,height-pad)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=7*d; paint.strokeCap=Paint.Cap.ROUND
            paint.color=track; canvas.drawOval(r,paint)
            if(value>0) { paint.color=accent; canvas.drawArc(r,-90f,(value.coerceIn(0.0,100.0)*3.6).toFloat(),false,paint) }
            paint.style=Paint.Style.FILL; paint.color=textColor; paint.textAlign=Paint.Align.CENTER
            paint.typeface=Typeface.DEFAULT_BOLD; paint.textSize=23*d
            while(paint.measureText(display)>width-24*d && paint.textSize>12*d) paint.textSize-=d
            canvas.drawText(display,width/2f,height/2f-(paint.ascent()+paint.descent())/2,paint)
        }
    }
}
