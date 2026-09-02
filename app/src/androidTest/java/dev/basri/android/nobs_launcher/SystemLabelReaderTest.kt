package dev.basri.android.nobs_launcher

import android.Manifest
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dev.basri.android.nobs_launcher.status.SystemLabelReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemLabelReaderTest {
    @get:Rule
    val wifiPermission: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun boxReadsAvailableWifiAndTimezoneLocationFromAndroid() {
        val reader = SystemLabelReader(context)

        val locationEnabled = context.getSystemService(LocationManager::class.java).isLocationEnabled
        assertEquals(if (locationEnabled) "Kahveci House" else null, reader.wifiName())
        assertTrue(reader.locationName().orEmpty().isNotBlank())
    }
}
