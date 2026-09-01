package dev.basri.android.nobs_launcher.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherConfigPolicyTest {
    @Test
    fun defaultConfigurationShowsAllInformationPanelsWithBlankWelcome() {
        assertEquals("", LauncherConfig.DEFAULT.welcomeText)
        assertEquals(true, LauncherConfig.DEFAULT.showLocation)
        assertEquals(true, LauncherConfig.DEFAULT.showVpnStatus)
        assertEquals(true, LauncherConfig.DEFAULT.showSystemStats)
        assertEquals(false, LauncherConfig.DEFAULT.firstRunComplete)
        assertEquals(emptyList<String>(), LauncherConfig.DEFAULT.selectedPackages)
    }

    @Test
    fun visibilitySettingsAreIndependent() {
        val updated = LauncherConfig.DEFAULT.copy(
            showLocation = false,
            showVpnStatus = true,
            showSystemStats = false,
        )

        assertEquals(false, updated.showLocation)
        assertEquals(true, updated.showVpnStatus)
        assertEquals(false, updated.showSystemStats)
    }

    @Test
    fun selectingANewPackageAppendsIt() {
        val initial = LauncherConfig(true, "Kahveci House", "London", listOf("app.youtube"))

        val updated = LauncherConfigPolicy.setVisible(initial, "app.jellyfin", true)

        assertEquals(listOf("app.youtube", "app.jellyfin"), updated.selectedPackages)
    }

    @Test
    fun selectingAnExistingPackageDoesNotDuplicateIt() {
        val initial = LauncherConfig(true, "Kahveci House", "London", listOf("app.youtube"))

        val updated = LauncherConfigPolicy.setVisible(initial, "app.youtube", true)

        assertEquals(listOf("app.youtube"), updated.selectedPackages)
    }

    @Test
    fun hidingAPackageRemovesItWithoutReorderingOthers() {
        val initial = LauncherConfig(
            true,
            "Kahveci House",
            "London",
            listOf("app.youtube", "app.music", "app.netflix"),
        )

        val updated = LauncherConfigPolicy.setVisible(initial, "app.music", false)

        assertEquals(listOf("app.youtube", "app.netflix"), updated.selectedPackages)
    }

    @Test
    fun normalizeDropsDuplicatesAndPackagesThatAreNoLongerInstalled() {
        val initial = LauncherConfig(
            true,
            "Kahveci House",
            "London",
            listOf("app.youtube", "app.removed", "app.youtube", "app.netflix"),
        )

        val normalized = LauncherConfigPolicy.normalize(
            initial,
            setOf("app.youtube", "app.netflix", "app.new"),
        )

        assertEquals(listOf("app.youtube", "app.netflix"), normalized.selectedPackages)
    }

    @Test
    fun moveReturnsAReorderedCopy() {
        val packages = listOf("app.youtube", "app.music", "app.netflix", "app.jellyfin")

        val moved = LauncherConfigPolicy.move(packages, fromIndex = 0, toIndex = 2)

        assertEquals(
            listOf("app.music", "app.netflix", "app.youtube", "app.jellyfin"),
            moved,
        )
        assertEquals(listOf("app.youtube", "app.music", "app.netflix", "app.jellyfin"), packages)
    }

    @Test
    fun invalidMoveLeavesOrderUnchanged() {
        val packages = listOf("app.youtube", "app.music")

        assertEquals(packages, LauncherConfigPolicy.move(packages, -1, 1))
        assertEquals(packages, LauncherConfigPolicy.move(packages, 0, 2))
        assertEquals(packages, LauncherConfigPolicy.move(packages, 1, 1))
    }
}
