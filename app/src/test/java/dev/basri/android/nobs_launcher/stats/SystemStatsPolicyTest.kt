package dev.basri.android.nobs_launcher.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemStatsPolicyTest {
    @Test
    fun cpuidleCpuUsageUsesIdleTimeAndLogicalCpuCapacity() {
        assertEquals(
            50,
            SystemStatsPolicy.cpuidleCpuUsagePercent(
                previous = CpuIdleCounters(idleMicros = 1_000_000, capturedAtMillis = 1_000),
                current = CpuIdleCounters(idleMicros = 3_000_000, capturedAtMillis = 2_000),
                cpuCount = 4,
            ),
        )
        assertEquals(
            0,
            SystemStatsPolicy.cpuidleCpuUsagePercent(
                previous = CpuIdleCounters(idleMicros = 1_000_000, capturedAtMillis = 1_000),
                current = CpuIdleCounters(idleMicros = 9_000_000, capturedAtMillis = 2_000),
                cpuCount = 4,
            ),
        )
    }

    @Test
    fun cpuidleCpuUsageRejectsInvalidDeltasAndCapacity() {
        val baseline = CpuIdleCounters(idleMicros = 5_000, capturedAtMillis = 1_000)

        assertNull(
            SystemStatsPolicy.cpuidleCpuUsagePercent(
                baseline,
                CpuIdleCounters(idleMicros = 4_000, capturedAtMillis = 2_000),
                cpuCount = 4,
            ),
        )
        assertNull(
            SystemStatsPolicy.cpuidleCpuUsagePercent(
                baseline,
                CpuIdleCounters(idleMicros = 6_000, capturedAtMillis = 1_000),
                cpuCount = 4,
            ),
        )
        assertNull(
            SystemStatsPolicy.cpuidleCpuUsagePercent(
                baseline,
                CpuIdleCounters(idleMicros = 6_000, capturedAtMillis = 2_000),
                cpuCount = 0,
            ),
        )
    }

    @Test
    fun cpuUsageUsesBusyAndTotalDeltas() {
        val previous = CpuCounters(idleTicks = 60, totalTicks = 100)
        val current = CpuCounters(idleTicks = 110, totalTicks = 200)

        assertEquals(50, SystemStatsPolicy.cpuUsagePercent(previous, current))
    }

    @Test
    fun procStatParserIncludesIdleWaitInIdleAndAllFieldsInTotal() {
        assertEquals(
            CpuCounters(idleTicks = 45, totalTicks = 145),
            SystemStatsParsers.parseCpuCounters("cpu 10 20 30 40 5 6 7 8 9 10"),
        )
        assertNull(SystemStatsParsers.parseCpuCounters("intr 1 2 3"))
        assertNull(SystemStatsParsers.parseCpuCounters("cpu malformed"))
    }

    @Test
    fun cpuUsageRejectsResetOrNonAdvancingCounters() {
        assertNull(
            SystemStatsPolicy.cpuUsagePercent(
                CpuCounters(idleTicks = 60, totalTicks = 100),
                CpuCounters(idleTicks = 50, totalTicks = 90),
            ),
        )
        assertNull(
            SystemStatsPolicy.cpuUsagePercent(
                CpuCounters(idleTicks = 60, totalTicks = 100),
                CpuCounters(idleTicks = 60, totalTicks = 100),
            ),
        )
    }

    @Test
    fun networkRatesUseElapsedTimeAndHandleCounterReset() {
        val previous = NetworkCounters(1_000, 2_000, 1_000)
        val current = NetworkCounters(3_048, 3_024, 2_000)

        assertEquals(
            NetworkRates(receivedBytesPerSecond = 2_048, transmittedBytesPerSecond = 1_024),
            SystemStatsPolicy.networkRates(previous, current),
        )
        assertEquals(
            NetworkRates(0, 0),
            SystemStatsPolicy.networkRates(current, NetworkCounters(10, 20, 3_000)),
        )
    }

    @Test
    fun bytesUseCompactBinaryUnits() {
        assertEquals("512 B", SystemStatsPolicy.formatBytes(512))
        assertEquals("1.0 KB", SystemStatsPolicy.formatBytes(1_024))
        assertEquals("9.5 MB", SystemStatsPolicy.formatBytes(9_961_472))
        assertEquals("10 MB", SystemStatsPolicy.formatBytes(10_485_760))
        assertEquals("2.0 GB", SystemStatsPolicy.formatBytes(2_147_483_648))
    }

    @Test
    fun displayContainsCapacityUsageAndRates() {
        val previous = RawSystemStats(
            totalMemoryBytes = 2_147_483_648,
            availableMemoryBytes = 1_073_741_824,
            totalStorageBytes = 8_589_934_592,
            availableStorageBytes = 4_294_967_296,
            cpuCount = 4,
            cpuMaxFrequencyHz = 1_800_000_000,
            cpuCounters = CpuCounters(60, 100),
            cpuIdleCounters = CpuIdleCounters(idleMicros = 1_000_000, capturedAtMillis = 1_000),
            networkCounters = NetworkCounters(1_000, 2_000, 1_000),
        )
        val current = previous.copy(
            availableMemoryBytes = 536_870_912,
            availableStorageBytes = 2_147_483_648,
            cpuCounters = CpuCounters(110, 200),
            networkCounters = NetworkCounters(3_048, 3_024, 2_000),
        )

        assertEquals(
            SystemStatsDisplay(
                memory = "1.5 / 2.0 GB · 75%",
                cpu = "4 cores · 1.8 GHz · 50%",
                storage = "6.0 / 8.0 GB · 75%",
                networkIngress = "2.0 KB/s",
                networkEgress = "1.0 KB/s",
            ),
            SystemStatsPolicy.display(previous, current),
        )
    }

    @Test
    fun displayPrefersProcCountersAndFallsBackToCpuidleUtilization() {
        val previous = RawSystemStats(
            totalMemoryBytes = 1,
            availableMemoryBytes = 1,
            totalStorageBytes = 1,
            availableStorageBytes = 1,
            cpuCount = 4,
            cpuMaxFrequencyHz = null,
            cpuCounters = CpuCounters(idleTicks = 90, totalTicks = 100),
            cpuIdleCounters = CpuIdleCounters(idleMicros = 1_000_000, capturedAtMillis = 1_000),
            networkCounters = null,
        )
        val current = previous.copy(
            cpuCounters = CpuCounters(idleTicks = 165, totalTicks = 200),
            cpuIdleCounters = CpuIdleCounters(idleMicros = 3_000_000, capturedAtMillis = 2_000),
        )

        assertEquals("4 cores · 25%", SystemStatsPolicy.display(previous, current).cpu)
        assertEquals(
            "4 cores · 50%",
            SystemStatsPolicy.display(
                previous.copy(cpuCounters = null),
                current.copy(cpuCounters = null),
            ).cpu,
        )
    }

    @Test
    fun displayDistinguishesMeasuringFromUnavailableCpuUtilization() {
        val capacityOnly = RawSystemStats(
            totalMemoryBytes = 0,
            availableMemoryBytes = 0,
            totalStorageBytes = 0,
            availableStorageBytes = 0,
            cpuCount = 4,
            cpuMaxFrequencyHz = 2_000_000_000,
            cpuCounters = null,
            cpuIdleCounters = null,
            networkCounters = null,
        )
        val measuring = capacityOnly.copy(
            cpuIdleCounters = CpuIdleCounters(idleMicros = 1_000, capturedAtMillis = 1_000),
        )

        assertEquals(
            "4 cores · 2.0 GHz · measuring…",
            SystemStatsPolicy.display(previous = null, current = measuring).cpu,
        )
        assertEquals(
            "4 cores · 2.0 GHz · utilization unavailable",
            SystemStatsPolicy.display(previous = null, current = capacityOnly).cpu,
        )
        val measuringNetwork = capacityOnly.copy(
            networkCounters = NetworkCounters(1_000, 2_000, 1_000),
        )
        assertEquals(
            "measuring…",
            SystemStatsPolicy.display(previous = null, current = measuringNetwork).networkIngress,
        )
        assertEquals(
            "measuring…",
            SystemStatsPolicy.display(previous = null, current = measuringNetwork).networkEgress,
        )
    }

    @Test
    fun unavailableSourcesAreDescribedHonestly() {
        val display = SystemStatsPolicy.display(
            previous = null,
            current = RawSystemStats(
                totalMemoryBytes = 0,
                availableMemoryBytes = 0,
                totalStorageBytes = 0,
                availableStorageBytes = 0,
                cpuCount = 0,
                cpuMaxFrequencyHz = null,
                cpuCounters = null,
                cpuIdleCounters = null,
                networkCounters = null,
            ),
        )

        assertEquals("unavailable", display.memory)
        assertEquals("capacity unavailable · utilization unavailable", display.cpu)
        assertEquals("unavailable", display.storage)
        assertEquals("unavailable", display.networkIngress)
        assertEquals("unavailable", display.networkEgress)
    }
}
