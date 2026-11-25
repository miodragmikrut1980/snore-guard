package rs.snoreguard.mvp

import android.annotation.TargetApi
import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.HealthConnectException
import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.ReadRecordsResponse
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.SleepSessionRecord
import android.os.OutcomeReceiver
import java.time.Instant

/** Platform Health Connect, Android 14+. Read-only; no network or persistent copy. */
@TargetApi(34)
object HuaweiSleepReader {
    const val PERMISSION = "android.permission.health.READ_SLEEP"
    fun read(context: Context, start: Long, end: Long,
             callback: (List<SleepTimeline.Night>?, String?) -> Unit) {
        val manager = context.getSystemService(HealthConnectManager::class.java)
        if (manager == null) { callback(null, "Health Connect nije dostupan."); return }
        val collected = mutableListOf<SleepTimeline.Night>()
        val seen = mutableSetOf<Long>()
        val range = TimeInstantRangeFilter.Builder()
            .setStartTime(Instant.ofEpochMilli((start - 86_400_000L).coerceAtLeast(0L)))
            .setEndTime(Instant.ofEpochMilli(end)).build()
        fun page(token: Long) {
            try {
                val builder = ReadRecordsRequestUsingFilters.Builder(SleepSessionRecord::class.java)
                    .setTimeRangeFilter(range).setPageSize(100)
                if (token != -1L) builder.setPageToken(token)
                manager.readRecords(builder.build(), context.mainExecutor,
                    object : OutcomeReceiver<ReadRecordsResponse<SleepSessionRecord>, HealthConnectException> {
                        override fun onResult(result: ReadRecordsResponse<SleepSessionRecord>) {
                            try {
                                result.records.forEach { record ->
                                    val from = record.startTime.toEpochMilli()
                                    val to = record.endTime.toEpochMilli()
                                    if (to > from && from < end && to > start) {
                                        val stages = record.stages.map { stage ->
                                            val kind = when (stage.type) {
                                                1, 3, 7 -> SleepTimeline.Kind.AWAKE
                                                2 -> SleepTimeline.Kind.SLEEPING
                                                4 -> SleepTimeline.Kind.LIGHT
                                                5 -> SleepTimeline.Kind.DEEP
                                                6 -> SleepTimeline.Kind.REM
                                                else -> SleepTimeline.Kind.UNKNOWN
                                            }
                                            SleepTimeline.Stage(stage.startTime.toEpochMilli(), stage.endTime.toEpochMilli(), kind)
                                        }
                                        collected.add(SleepTimeline.Night(from, to,
                                            record.metadata.dataOrigin.packageName, stages))
                                    }
                                }
                                val next = result.nextPageToken
                                if (next != -1L) {
                                    if (seen.size >= 20 || !seen.add(next)) {
                                        callback(null, "Previše rezultata ili neispravna paginacija."); return
                                    }
                                    page(next)
                                } else callback(collected.sortedByDescending { SleepTimeline.overlap(start, end, it) }, null)
                            } catch (_: Exception) { callback(null, "Ne mogu da obradim podatke o snu.") }
                        }
                        override fun onError(error: HealthConnectException) {
                            callback(null, "Čitanje nije uspelo. Proveri dozvolu, sinhronizaciju i dostupnost istorije (kod " + error.errorCode + ").")
                        }
                    })
            } catch (_: Exception) { callback(null, "Health Connect nije dostupan ili dozvola nije odobrena.") }
        }
        page(-1L)
    }
}
