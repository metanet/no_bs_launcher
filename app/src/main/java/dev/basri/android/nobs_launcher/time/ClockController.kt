package dev.basri.android.nobs_launcher.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockController(
    context: Context,
    private val clockView: TextView,
    private val dateView: TextView,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var started = false

    private val minuteTick = object : Runnable {
        override fun run() {
            updateNow()
            scheduleNextMinute()
        }
    }

    private val timeChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateNow()
            scheduleNextMinute()
        }
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                timeChangedReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(timeChangedReceiver, filter)
        }
        updateNow()
        scheduleNextMinute()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { appContext.unregisterReceiver(timeChangedReceiver) }
        handler.removeCallbacks(minuteTick)
    }

    private fun updateNow() {
        val locale = Locale.getDefault()
        val now = Date()
        val clockPattern = if (DateFormat.is24HourFormat(appContext)) "HH:mm" else "h:mm"
        clockView.text = SimpleDateFormat(clockPattern, locale).format(now)
        dateView.text = SimpleDateFormat("EEEE, d MMMM", locale).format(now)
    }

    private fun scheduleNextMinute() {
        handler.removeCallbacks(minuteTick)
        if (!started) return
        val delay = MILLIS_PER_MINUTE - (System.currentTimeMillis() % MILLIS_PER_MINUTE) + 50L
        handler.postDelayed(minuteTick, delay)
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
