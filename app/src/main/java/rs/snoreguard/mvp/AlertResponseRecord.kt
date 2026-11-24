package rs.snoreguard.mvp

import org.json.JSONObject

/** Compact backward-compatible session field. Missing means not measured. */
object AlertResponseRecord {
    val columns = listOf("started", "quiet", "returned", "continued", "inconclusive",
        "pending", "smartSuppressed")
    fun encode(s: AlertResponseTracker.Snapshot): JSONObject = JSONObject()
        .put("started", s.started).put("quiet", s.quiet).put("returned", s.returned)
        .put("continued", s.continued).put("inconclusive", s.inconclusive)
        .put("pending", s.pending).put("smartSuppressed", s.smartSuppressed)

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
        return s
    }
    fun finishInterrupted(item: JSONObject) {
        val s = read(item) ?: return
        if (s.pending > 0) item.put("alertResponse", encode(s.copy(
            inconclusive = s.inconclusive + s.pending, pending = 0)))
    }
}
