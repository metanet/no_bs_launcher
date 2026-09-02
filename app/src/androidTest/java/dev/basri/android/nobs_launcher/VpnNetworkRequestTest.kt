package dev.basri.android.nobs_launcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.basri.android.nobs_launcher.status.buildVpnNetworkRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnNetworkRequestTest {
    @Test
    fun vpnRequestDoesNotRequireTheDefaultNotVpnCapability() {
        val description = buildVpnNetworkRequest().toString()

        assertTrue(description, "VPN" in description)
        assertFalse(description, "NOT_VPN" in description)
    }
}
