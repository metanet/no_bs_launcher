package dev.basri.android.nobs_launcher.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemLabelPolicyTest {
    @Test
    fun wifiSsidRemovesFrameworkQuotesAndRejectsUnavailableValues() {
        assertEquals("Kahveci House", SystemLabelPolicy.normalizeWifiSsid("\"Kahveci House\""))
        assertEquals("Cafe", SystemLabelPolicy.normalizeWifiSsid(" Cafe "))
        assertEquals("4b61687665", SystemLabelPolicy.normalizeWifiSsid("4b61687665"))
        assertNull(SystemLabelPolicy.normalizeWifiSsid(null))
        assertNull(SystemLabelPolicy.normalizeWifiSsid(""))
        assertNull(SystemLabelPolicy.normalizeWifiSsid("<unknown ssid>"))
        assertNull(SystemLabelPolicy.normalizeWifiSsid("  <UNKNOWN SSID>  "))
    }

    @Test
    fun locationUsesTheMostSpecificSystemTimeZoneComponent() {
        assertEquals("London", SystemLabelPolicy.locationFromTimeZone("Europe/London"))
        assertEquals("New York", SystemLabelPolicy.locationFromTimeZone("America/New_York"))
        assertEquals("Indianapolis", SystemLabelPolicy.locationFromTimeZone("America/Indiana/Indianapolis"))
        assertEquals("GMT", SystemLabelPolicy.locationFromTimeZone("GMT"))
        assertNull(SystemLabelPolicy.locationFromTimeZone("  "))
    }
}
