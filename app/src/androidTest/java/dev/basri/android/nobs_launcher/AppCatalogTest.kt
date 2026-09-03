package dev.basri.android.nobs_launcher

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.basri.android.nobs_launcher.data.AppCatalog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppCatalogTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun asyncCatalogDeliversOnMainAndCanceledRequestsStaySilent() {
        val catalog = AppCatalog.shared(context)
        val delivered = CountDownLatch(1)
        var deliveredOnMain = false

        catalog.invalidate()
        catalog.loadApps {
            deliveredOnMain = Looper.myLooper() == Looper.getMainLooper()
            delivered.countDown()
        }

        assertTrue(delivered.await(5, TimeUnit.SECONDS))
        assertTrue(deliveredOnMain)

        var canceledCallbackRan = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            catalog.loadApps { canceledCallbackRan = true }.cancel()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertFalse(canceledCallbackRan)
    }
}
