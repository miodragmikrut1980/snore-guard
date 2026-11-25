package rs.snoreguard.mvp

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Required rationale entry point for Android Health Connect permissions. */
class SleepPrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        setContentView(TextView(this).apply {
            setPadding(pad, pad, pad, pad)
            textSize = 17f
            text = "SnoreGuard — privatnost podataka o snu\n\n" +
                "Dozvola READ_SLEEP omogućava samo čitanje sesija i faza sna preko Health Connect, na tvoj zahtev. " +
                "Podaci o snu prikazuju se lokalno, ne šalju se na server, ne čuvaju se u istoriji SnoreGuarda i ne ulaze u njegov backup. " +
                "Aplikacija ne menja podatke u Health Connect niti koristi faze sna za automatsko slanje alarma. " +
                "Dozvolu možeš opozvati u Android podešavanjima Health Connect. " +
                "Podaci izvora, uključujući Huawei Health, zavise od njihove sinhronizacije. Ovo nije medicinski uređaj."
        })
    }
}
