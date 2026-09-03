package dev.basri.android.nobs_launcher

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.basri.android.nobs_launcher.data.LauncherServices
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherServicesTest {
    @Test
    fun faviconRepositoryIsApplicationScoped() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertSame(
            LauncherServices.favicons(context),
            LauncherServices.favicons(context.createDeviceProtectedStorageContext()),
        )
    }
}
