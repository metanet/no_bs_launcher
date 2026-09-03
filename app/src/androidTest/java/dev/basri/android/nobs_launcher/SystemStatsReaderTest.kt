package dev.basri.android.nobs_launcher

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.basri.android.nobs_launcher.stats.SystemStatsPolicy
import dev.basri.android.nobs_launcher.stats.SystemStatsReader
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemStatsReaderTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun deviceExposesSaneCapacityAndBestEffortRuntimeStats() {
        val reader = SystemStatsReader(context)
        val first = reader.read()
        Thread.sleep(250)
        val second = reader.read()
        val display = SystemStatsPolicy.display(first, second)

        assertTrue(first.totalMemoryBytes > 0)
        assertTrue(first.availableMemoryBytes in 1..first.totalMemoryBytes)
        assertTrue(first.totalStorageBytes > 0)
        assertTrue(first.availableStorageBytes in 0..first.totalStorageBytes)
        assertTrue(first.cpuCount > 0)
        first.cpuCounters?.let { counters ->
            assertTrue(counters.idleTicks >= 0)
            assertTrue(counters.totalTicks >= counters.idleTicks)
            assertNull(first.cpuIdleCounters)
        }
        first.cpuIdleCounters?.let { counters ->
            assertTrue(counters.idleMicros >= 0)
            assertTrue(counters.capturedAtMillis > 0)
        }
        val cpuUtilization = display.cpu.substringAfterLast(" · ")
        if (cpuUtilization.endsWith('%')) {
            assertTrue(cpuUtilization.removeSuffix("%").toInt() in 0..100)
        } else {
            assertTrue(
                cpuUtilization == "measuring…" || cpuUtilization == "utilization unavailable",
            )
        }
        first.networkCounters?.let {
            assertTrue(it.receivedBytes >= 0)
            assertTrue(it.transmittedBytes >= 0)
        }
    }
}
