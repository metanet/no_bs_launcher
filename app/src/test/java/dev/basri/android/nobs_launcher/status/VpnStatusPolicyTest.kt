package dev.basri.android.nobs_launcher.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnStatusPolicyTest {
    @Test
    fun activeVpnUsesProviderNeutralLabel() {
        assertEquals("VPN connected", VpnStatusPolicy.label(vpnActive = true))
    }

    @Test
    fun inactiveVpnHasNoLabel() {
        assertNull(VpnStatusPolicy.label(vpnActive = false))
    }
}
