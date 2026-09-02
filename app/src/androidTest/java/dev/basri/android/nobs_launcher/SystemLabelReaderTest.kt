package dev.basri.android.nobs_launcher

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dev.basri.android.nobs_launcher.status.SystemLabelReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.TimeZone

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
    fun readsAvailableWifiAndTimezoneLocationFromAndroid() {
        val reader = SystemLabelReader(context)

        reader.wifiName()?.let { wifiName ->
            assertTrue(wifiName.isNotBlank())
            assertTrue(!wifiName.equals("<unknown ssid>", ignoreCase = true))
            assertTrue(!(wifiName.startsWith('"') && wifiName.endsWith('"')))
        }
        val expectedLocation = TimeZone.getDefault().id
            .substringAfterLast('/')
            .replace('_', ' ')
            .trim()
        assertEquals(expectedLocation, reader.locationName())
    }
}
