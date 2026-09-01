package dev.basri.android.nobs_launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutCodecTest {
    @Test
    fun orderedPackagesRoundTrip() {
        val packages = listOf(
            "com.google.android.youtube.tv",
            "org.jellyfin.androidtv",
            "com.streamvault.app",
        )

        assertEquals(packages, LayoutCodec.decode(LayoutCodec.encode(packages)))
    }

    @Test
    fun blankInputReturnsAnEmptyList() {
        assertEquals(emptyList<String>(), LayoutCodec.decode("\n  \n"))
    }

    @Test
    fun invalidAndDuplicatePackageNamesAreDiscarded() {
        val encoded = """
            com.netflix.ninja
            not a package
            com.netflix.ninja
            org.jellyfin.androidtv
            /system/app
        """.trimIndent()

        assertEquals(
            listOf("com.netflix.ninja", "org.jellyfin.androidtv"),
            LayoutCodec.decode(encoded),
        )
    }

    @Test
    fun encodingDropsDuplicatesWithoutChangingFirstSeenOrder() {
        val encoded = LayoutCodec.encode(
            listOf("com.netflix.ninja", "org.jellyfin.androidtv", "com.netflix.ninja"),
        )

        assertEquals("com.netflix.ninja\norg.jellyfin.androidtv", encoded)
    }
}
