package dev.basri.android.nobs_launcher.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemStatsPolicyTest {
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
                cpu = "50% · 4 cores · 1.8 GHz",
                storage = "6.0 / 8.0 GB · 75%",
                network = "↓ 2.0 KB/s · ↑ 1.0 KB/s",
            ),
            SystemStatsPolicy.display(previous, current),
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
                networkCounters = null,
            ),
        )

        assertEquals("unavailable", display.memory)
        assertEquals("usage unavailable · capacity unavailable", display.cpu)
        assertEquals("unavailable", display.storage)
        assertEquals("unavailable", display.network)
    }
}
