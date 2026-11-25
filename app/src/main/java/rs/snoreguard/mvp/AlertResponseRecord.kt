package rs.snoreguard.mvp

import org.json.JSONObject

/** Compact backward-compatible session field. Missing means not measured. */
object AlertResponseRecord {
    val columns = listOf("started", "quiet", "returned", "continued", "inconclusive",
        "pending", "smartSuppressed")
    val timingColumns = listOf("returns", "sumQuietMs", "sumAfterAlarmMs", "minQuietMs", "maxQuietMs",
        "noReturn10m", "interrupted", "pending")
    private fun encodeTiming(t: AlertResponseTracker.Timing): JSONObject = JSONObject()
        .put("returns", t.returns).put("sumQuietMs", t.sumQuietMs)
        .put("sumAfterAlarmMs", t.sumAfterAlarmMs).put("minQuietMs", t.minQuietMs)
        .put("maxQuietMs", t.maxQuietMs).put("noReturn10m", t.noReturn10m)
        .put("interrupted", t.interrupted).put("pending", t.pending)
    fun encode(s: AlertResponseTracker.Snapshot): JSONObject = JSONObject()
        .put("started", s.started).put("quiet", s.quiet).put("returned", s.returned)
        .put("continued", s.continued).put("inconclusive", s.inconclusive)
        .put("pending", s.pending).put("smartSuppressed", s.smartSuppressed)
        .apply { s.timing?.let { put("timing", encodeTiming(it)) } }

    fun read(item: JSONObject?): AlertResponseTracker.Snapshot? {
        val obj = item?.optJSONObject("alertResponse") ?: return null
        val values = columns.map { key ->
            val number = obj.opt(key) as? Number ?: return null
            val value = number.toDouble()
            if (!value.isFinite() || value !in 0.0..100_000.0 || value % 1.0 != 0.0) return null
            value.toInt()
        }
        val s = AlertResponseTracker.Snapshot(values[0], values[1], values[2], values[3],
            values[4], values[5], values[6])
        if (s.pending !in 0..1 || s.started != s.assessed + s.inconclusive + s.pending ||
            s.smartSuppressed > s.started) return null
        if (!obj.has("timing")) return s
        val raw = obj.optJSONObject("timing") ?: return null
        val v = timingColumns.map { key ->
            val n = (raw.opt(key) as? Number)?.toDouble() ?: return null
            if (!n.isFinite() || n !in 0.0..63_000_000_000.0 || n % 1 != 0.0) return null
            n.toLong()
        }
        if (listOf(v[0], v[5], v[6], v[7]).any { it > 100_000 } ||
            v[7] > 1 || v[0] + v[5] + v[6] + v[7] != s.settled.toLong() ||
            v[3] > v[4] || v[4] > 600_000 ||
            v[1] !in (v[3] * v[0])..(v[4] * v[0]) ||
            v[2] !in v[1]..(630_000L * v[0]) ||
            (v[0] == 0L && (v[3] != 0L || v[4] != 0L)) ||
            (v[0] > 0L && v[3] < 10_000L)) return null
        return s.copy(timing = AlertResponseTracker.Timing(v[0].toInt(), v[1], v[2],
            v[3], v[4], v[5].toInt(), v[6].toInt(), v[7].toInt()))
    }
    fun finishInterrupted(item: JSONObject) {
        val s = read(item) ?: return
        val timing = s.timing?.let { it.copy(interrupted = it.interrupted + it.pending, pending = 0) }
        if (s.pending > 0 || s.timing?.pending == 1) item.put("alertResponse", encode(s.copy(
            inconclusive = s.inconclusive + s.pending, pending = 0, timing = timing)))
    }
}
