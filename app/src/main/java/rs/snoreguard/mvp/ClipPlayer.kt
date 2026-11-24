package rs.snoreguard.mvp

import android.app.Activity
import android.app.AlertDialog
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.io.File
import java.util.Locale

/** Dialog owns its playback and releases on close/pause. No playback during monitoring. */
class ClipPlayer(private val activity: Activity, file: File) {
    private val handler = Handler(Looper.getMainLooper())
    private var media: MediaPlayer? = null
    private var dialog: AlertDialog? = null
    private val waveform = ClipWaveformView(activity, WaveformPeaks.read(file))
    private val seek = SeekBar(activity)
    private val time = TextView(activity)
    private val play = Button(activity)
    private var dragging = false
    private var closed = false
    private val tick = object : Runnable {
        override fun run() {
            val m = media ?: return
            if (closed) return
            try {
                if (!dragging) updatePosition(m.currentPosition, m.duration)
                play.text = if (m.isPlaying) "Pauza" else "Pusti"
                handler.postDelayed(this, 120)
            } catch (_: Exception) { close() }
        }
    }
    init {
        val m = MediaPlayer()
        try {
            m.setDataSource(file.absolutePath)
            m.prepare()
            media = m
            seek.max = m.duration.coerceAtLeast(1)
            updatePosition(0, m.duration)
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) updatePosition(progress, m.duration)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) { dragging = true }
                override fun onStopTrackingTouch(bar: SeekBar?) {
                    try { m.seekTo(seek.progress) } catch (_: Exception) { close() }
                    dragging = false
                }
            })
            play.text = "Pusti"; play.isAllCaps = false
            play.setOnClickListener {
                try {
                    if (m.isPlaying) m.pause() else {
                        if (m.currentPosition >= m.duration - 50) m.seekTo(0)
                        m.start()
                    }
                } catch (_: Exception) { close() }
            }
            m.setOnCompletionListener { if (!closed) { updatePosition(m.duration, m.duration); play.text = "Pusti ponovo" } }
            m.setOnErrorListener { _, _, _ -> close(); true }
            val pad = (16 * activity.resources.displayMetrics.density).toInt()
            val panel = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                addView(waveform, LinearLayout.LayoutParams(-1, pad * 6))
                addView(seek); addView(time); addView(play)
            }
            dialog = AlertDialog.Builder(activity).setTitle("Preslušavanje WAV snimka")
                .setView(panel).setNegativeButton("Zatvori", null).create()
            dialog?.setOnDismissListener { close() }
            dialog?.show()
            handler.post(tick)
        } catch (e: Exception) { try { m.release() } catch (_: Exception) {}; media = null; throw e }
    }
    private fun updatePosition(pos: Int, duration: Int) {
        seek.progress = pos
        waveform.progress = pos.toFloat() / duration.coerceAtLeast(1)
        time.text = String.format(Locale.US, "%.1f / %.1f s", pos / 1000.0, duration / 1000.0)
    }
    fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacksAndMessages(null)
        val m = media; media = null
        try { m?.release() } catch (_: Exception) {}
        dialog?.dismiss(); dialog = null
    }
}
