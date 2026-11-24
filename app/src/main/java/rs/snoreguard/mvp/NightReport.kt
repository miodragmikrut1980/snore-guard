package rs.snoreguard.mvp

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import org.json.JSONObject
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NightReport {
    fun metrics(item: JSONObject, end: Long = item.optLong("end")): NightAnalytics.Result? {
        if (item.optString("detectorVersion") != "breaths_v1") return null
        val start = item.optLong("start")
        if (start <= 0 || end <= start) return null
        return NightAnalytics.calculate(item.optInt("analysedFrames", -1),
            item.optInt("snoreFrames", -1), item.optInt("episodes", -1),
            item.optInt("hopMs"), end - start)
    }
    fun decimal(value: Double): String = String.format(Locale.US, "%.1f", value)

    /** One bounded A4 page per finished session; no audio or free-text notes included. */
    fun write(output: OutputStream, nights: List<JSONObject>, clips: List<SnoreClip>) {
        require(nights.isNotEmpty())
        val document = PdfDocument()
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("sr-RS"))
        try {
            nights.take(7).forEachIndexed { index, item ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                val canvas = page.canvas
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 13f }
                var y = 46f
                fun line(text: String, size: Float = 13f) {
                    paint.textSize = size
                    canvas.drawText(text, 36f, y, paint)
                    y += size + 10f
                }
                val start = item.optLong("start")
                val end = item.optLong("end")
                line("SnoreGuard | Izveštaj praćenja", 22f)
                line("Sesija " + (index + 1) + " / " + nights.take(7).size)
                line(date.format(Date(start)) + " – " + date.format(Date(end)))
                line("Trajanje praćenja: " + decimal((end - start).coerceAtLeast(0L) / 3600000.0) + " h")
                line("Detektor: " + if (item.optString("detectorVersion") == "breaths_v1") "breaths_v1" else "starija verzija")
                line("Osetljivost: " + Sensitivity.values().firstOrNull { it.name == item.optString("sensitivity") }?.name.orEmpty())
                line("Detektovani događaji: " + item.optInt("episodes"))
                line("Serije alarma: " + item.optInt("alerts") + " | Poslata obaveštenja: " +
                    if (item.has("notifications")) item.optInt("notifications").toString() else "nema podatka")
                val result = metrics(item)
                if (result != null) {
                    line("Analizirani zvuk: " + decimal(result.analysedHours) + " h")
                    line("Pokrivenost analize: " + decimal(result.coverage * 100) + "%")
                    line("Događaji po analiziranom satu: " + decimal(result.eventsPerHour))
                    line("Pozitivni AI prozori: " + decimal(result.positivePercent) + "%")
                    line("Indeks hrkanja (0–100): " + (result.score?.toString() ?: "nedovoljno podataka"))
                } else line("Normalizovani pokazatelji nisu dostupni.")
                if (item.has("inferenceGaps5s")) line("AI zastoji preko 5 s: " + item.optInt("inferenceGaps5s"))
                val matching = clips.filter { it.recordedAt in start..end }
                val review = NightAnalytics.reviews(matching.map { it.label })
                line("Sačuvani WAV isečci: " + matching.size)
                line("Ocene: hrkanje " + review.snore + ", lažni " + review.falseAlarm +
                    ", nejasni " + review.uncertain + ", neocenjeni " + review.unreviewed)
                val histogram = item.optJSONObject("hourly")
                if (histogram != null) {
                    line("Događaji po satu", 15f)
                    val labels = NightHours.labelsForWindow(item.optInt("windowStart",1320).coerceIn(0,1439),
                        item.optInt("windowEnd",480).coerceIn(0,1439))
                    labels.chunked(6).forEach { group ->
                        line(group.joinToString("    ") { it + "h: " + histogram.optInt(it) }, 11f)
                    }
                }
                y += 10f
                line("Indeks = udeo pozitivnih AI prozora, nije intenzitet zvuka.", 11f)
                line("Praćenje nije merenje trajanja sna niti dijagnoza apneje.", 11f)
                line("Poslata obaveštenja ne dokazuju vibraciju sata ni efekat.", 11f)
                line("WAV audio i privatne beleške nisu uključeni u PDF.", 11f)
                document.finishPage(page)
            }
            document.writeTo(output)
        } finally { document.close() }
    }
}
