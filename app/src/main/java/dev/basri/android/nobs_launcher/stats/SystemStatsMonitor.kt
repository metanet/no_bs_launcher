package dev.basri.android.nobs_launcher.stats

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class SystemStatsMonitor(
    context: Context,
    private val onStatsChanged: (SystemStatsDisplay) -> Unit,
    private val sampleIntervalMillis: Long = DEFAULT_SAMPLE_INTERVAL_MILLIS,
) {
    private val reader = SystemStatsReader(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var executor: ScheduledExecutorService? = null
    private var previous: RawSystemStats? = null
    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        previous = null
        executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "nobs-system-stats").apply { isDaemon = true }
        }.also { worker ->
            worker.scheduleWithFixedDelay(
                ::sample,
                0L,
                sampleIntervalMillis,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    fun stop() {
        if (!started) return
        started = false
        executor?.shutdownNow()
        executor = null
        previous = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun sample() {
        if (!started) return
        val current = reader.read()
        val display = SystemStatsPolicy.display(previous, current)
        previous = current
        mainHandler.post {
            if (started) onStatsChanged(display)
        }
    }

    private companion object {
        const val DEFAULT_SAMPLE_INTERVAL_MILLIS = 2_000L
    }
}
