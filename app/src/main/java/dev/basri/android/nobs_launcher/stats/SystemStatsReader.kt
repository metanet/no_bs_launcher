package dev.basri.android.nobs_launcher.stats

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.io.File

class SystemStatsReader(context: Context) {
    private val activityManager = context.applicationContext
        .getSystemService(ActivityManager::class.java)

    fun read(): RawSystemStats {
        val memory = readMemory()
        val storage = readStorage()
        val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(0)
        return RawSystemStats(
            totalMemoryBytes = memory?.totalMem ?: 0L,
            availableMemoryBytes = memory?.availMem ?: 0L,
            totalStorageBytes = storage?.first ?: 0L,
            availableStorageBytes = storage?.second ?: 0L,
            cpuCount = cpuCount,
            cpuMaxFrequencyHz = readMaxCpuFrequency(cpuCount),
            cpuCounters = readCpuCounters(),
            networkCounters = readNetworkCounters(),
        )
    }

    private fun readMemory(): ActivityManager.MemoryInfo? = runCatching {
        ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    }.getOrNull()

    private fun readStorage(): Pair<Long, Long>? = runCatching {
        val statFs = StatFs(Environment.getDataDirectory().absolutePath)
        statFs.totalBytes to statFs.availableBytes
    }.getOrNull()

    private fun readCpuCounters(): CpuCounters? = runCatching {
        File(PROC_STAT).useLines { lines ->
            lines.firstOrNull()?.let(SystemStatsParsers::parseCpuCounters)
        }
    }.getOrNull()

    private fun readMaxCpuFrequency(cpuCount: Int): Long? = (0 until cpuCount)
        .mapNotNull { cpuIndex ->
            CPU_FREQUENCY_FILES.firstNotNullOfOrNull { fileName ->
                runCatching {
                    File("/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/$fileName")
                        .readText()
                        .trim()
                        .toLongOrNull()
                        ?.times(KILOHERTZ_TO_HERTZ)
                }.getOrNull()
            }
        }
        .maxOrNull()

    private fun readNetworkCounters(): NetworkCounters? {
        val received = TrafficStats.getTotalRxBytes()
        val transmitted = TrafficStats.getTotalTxBytes()
        if (received == TrafficStats.UNSUPPORTED.toLong() ||
            transmitted == TrafficStats.UNSUPPORTED.toLong()
        ) {
            return null
        }
        return NetworkCounters(
            receivedBytes = received.coerceAtLeast(0),
            transmittedBytes = transmitted.coerceAtLeast(0),
            capturedAtMillis = SystemClock.elapsedRealtime(),
        )
    }

    private companion object {
        const val PROC_STAT = "/proc/stat"
        const val KILOHERTZ_TO_HERTZ = 1_000L
        val CPU_FREQUENCY_FILES = listOf("cpuinfo_max_freq", "scaling_max_freq")
    }
}
