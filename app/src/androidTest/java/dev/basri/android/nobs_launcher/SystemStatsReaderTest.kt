package dev.basri.android.nobs_launcher

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.basri.android.nobs_launcher.stats.SystemStatsPolicy
import dev.basri.android.nobs_launcher.stats.SystemStatsReader
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SystemStatsReaderTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun boxExposesSaneCapacityAndPermissionFreeRuntimeStats() {
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
        val idleTimeFiles = File("/sys/devices/system/cpu")
            .walkTopDown()
            .maxDepth(4)
            .filter { it.isFile && it.parentFile?.name?.startsWith("state") == true && it.name == "time" }
            .toList()
        assertTrue("Box must expose cpuidle state counters", idleTimeFiles.isNotEmpty())
        assertTrue(
            "Box cpuidle state counters must be readable by an untrusted app",
            idleTimeFiles.all { file -> runCatching { file.readText().trim().toLong() }.isSuccess },
        )
        assertTrue(first.cpuIdleCounters?.idleMicros?.let { it > 0 } == true)
        assertTrue(
            second.cpuIdleCounters?.idleMicros?.let { current ->
                current > first.cpuIdleCounters!!.idleMicros
            } == true,
        )
        assertTrue(display.cpu.substringAfterLast(" · ").removeSuffix("%").toInt() in 0..100)
        first.networkCounters?.let {
            assertTrue(it.receivedBytes >= 0)
            assertTrue(it.transmittedBytes >= 0)
        }
    }
}
