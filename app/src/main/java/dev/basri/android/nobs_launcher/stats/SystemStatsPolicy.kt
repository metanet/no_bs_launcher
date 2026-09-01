package dev.basri.android.nobs_launcher.stats

import java.util.Locale
import kotlin.math.roundToInt

object SystemStatsPolicy {
    fun cpuUsagePercent(previous: CpuCounters?, current: CpuCounters?): Int? {
        if (previous == null || current == null) return null
        val totalDelta = current.totalTicks - previous.totalTicks
        val idleDelta = current.idleTicks - previous.idleTicks
        if (totalDelta <= 0 || idleDelta < 0) return null
        val busyDelta = (totalDelta - idleDelta).coerceAtLeast(0)
        return ((busyDelta.toDouble() / totalDelta) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    fun cpuidleCpuUsagePercent(
        previous: CpuIdleCounters?,
        current: CpuIdleCounters?,
        cpuCount: Int,
    ): Int? {
        if (previous == null || current == null || cpuCount <= 0) return null
        val idleDeltaMicros = current.idleMicros - previous.idleMicros
        val elapsedMillis = current.capturedAtMillis - previous.capturedAtMillis
        if (idleDeltaMicros < 0 || elapsedMillis <= 0) return null
        val capacityMicros = elapsedMillis * 1_000.0 * cpuCount
        val busyMicros = (capacityMicros - idleDeltaMicros).coerceAtLeast(0.0)
        return ((busyMicros / capacityMicros) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    fun networkRates(
        previous: NetworkCounters?,
        current: NetworkCounters?,
    ): NetworkRates? {
        if (previous == null || current == null) return null
        val elapsedMillis = current.capturedAtMillis - previous.capturedAtMillis
        if (elapsedMillis <= 0) return null
        val receivedDelta = current.receivedBytes - previous.receivedBytes
        val transmittedDelta = current.transmittedBytes - previous.transmittedBytes
        if (receivedDelta < 0 || transmittedDelta < 0) return NetworkRates(0, 0)
        return NetworkRates(
            receivedBytesPerSecond = receivedDelta * 1_000L / elapsedMillis,
            transmittedBytesPerSecond = transmittedDelta * 1_000L / elapsedMillis,
        )
    }

    fun display(previous: RawSystemStats?, current: RawSystemStats): SystemStatsDisplay {
        val cpuUsage = cpuUsagePercent(previous?.cpuCounters, current.cpuCounters)
            ?: cpuidleCpuUsagePercent(
                previous?.cpuIdleCounters,
                current.cpuIdleCounters,
                current.cpuCount,
            )
        val rates = networkRates(previous?.networkCounters, current.networkCounters)
        return SystemStatsDisplay(
            memory = formatCapacityUse(current.totalMemoryBytes, current.availableMemoryBytes),
            cpu = formatCpu(
                usagePercent = cpuUsage,
                hasUtilizationSource = current.cpuCounters != null || current.cpuIdleCounters != null,
                cpuCount = current.cpuCount,
                maxFrequencyHz = current.cpuMaxFrequencyHz,
            ),
            storage = formatCapacityUse(current.totalStorageBytes, current.availableStorageBytes),
            network = when {
                current.networkCounters == null -> UNAVAILABLE
                rates == null -> MEASURING
                else -> "↓ ${formatBytes(rates.receivedBytesPerSecond)}/s · " +
                    "↑ ${formatBytes(rates.transmittedBytesPerSecond)}/s"
            },
        )
    }

    fun formatBytes(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0)
        if (safeBytes < KIBIBYTE) return "$safeBytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = safeBytes.toDouble()
        var unitIndex = -1
        while (value >= KIBIBYTE && unitIndex < units.lastIndex) {
            value /= KIBIBYTE
            unitIndex += 1
        }
        val pattern = if (value < 10.0) "%.1f %s" else "%.0f %s"
        return String.format(Locale.US, pattern, value, units[unitIndex])
    }

    private fun formatCapacityUse(totalBytes: Long, availableBytes: Long): String {
        if (totalBytes <= 0) return UNAVAILABLE
        val usedBytes = totalBytes - availableBytes.coerceIn(0, totalBytes)
        val percent = ((usedBytes.toDouble() / totalBytes) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
        val used = formatBytes(usedBytes)
        val total = formatBytes(totalBytes)
        val totalUnit = total.substringAfterLast(' ')
        val compactUsed = if (used.endsWith(" $totalUnit")) {
            used.removeSuffix(" $totalUnit")
        } else {
            used
        }
        return "$compactUsed / $total · $percent%"
    }

    private fun formatCpu(
        usagePercent: Int?,
        hasUtilizationSource: Boolean,
        cpuCount: Int,
        maxFrequencyHz: Long?,
    ): String {
        val usage = when {
            usagePercent != null -> "$usagePercent%"
            hasUtilizationSource -> MEASURING
            else -> "utilization unavailable"
        }
        val capacity = buildList {
            if (cpuCount > 0) add("$cpuCount ${if (cpuCount == 1) "core" else "cores"}")
            maxFrequencyHz?.takeIf { it > 0 }?.let { add(formatFrequency(it)) }
        }.ifEmpty { listOf("capacity unavailable") }
        return (listOf(usage) + capacity).joinToString(" · ")
    }

    private fun formatFrequency(hertz: Long): String {
        val gigahertz = hertz.toDouble() / 1_000_000_000.0
        return if (gigahertz >= 1.0) {
            String.format(Locale.US, "%.1f GHz", gigahertz)
        } else {
            String.format(Locale.US, "%.0f MHz", hertz.toDouble() / 1_000_000.0)
        }
    }

    private const val KIBIBYTE = 1_024.0
    private const val UNAVAILABLE = "unavailable"
    private const val MEASURING = "measuring…"
}
