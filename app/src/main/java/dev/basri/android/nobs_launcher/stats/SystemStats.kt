package dev.basri.android.nobs_launcher.stats

data class CpuCounters(
    val idleTicks: Long,
    val totalTicks: Long,
)

data class CpuIdleCounters(
    val idleMicros: Long,
    val capturedAtMillis: Long,
)

data class NetworkCounters(
    val receivedBytes: Long,
    val transmittedBytes: Long,
    val capturedAtMillis: Long,
)

data class NetworkRates(
    val receivedBytesPerSecond: Long,
    val transmittedBytesPerSecond: Long,
)

data class RawSystemStats(
    val totalMemoryBytes: Long,
    val availableMemoryBytes: Long,
    val totalStorageBytes: Long,
    val availableStorageBytes: Long,
    val cpuCount: Int,
    val cpuMaxFrequencyHz: Long?,
    val cpuCounters: CpuCounters?,
    val cpuIdleCounters: CpuIdleCounters?,
    val networkCounters: NetworkCounters?,
)

data class SystemStatsDisplay(
    val memory: String,
    val cpu: String,
    val storage: String,
    val network: String,
)
