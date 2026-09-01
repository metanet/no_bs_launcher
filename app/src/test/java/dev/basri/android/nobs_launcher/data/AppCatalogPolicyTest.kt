package dev.basri.android.nobs_launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppCatalogPolicyTest {
    @Test
    fun tvCandidateWinsOverMobileCandidate() {
        val result = AppCatalogPolicy.select(
            candidates = listOf(
                AppCandidate("pkg.video", "Video", LaunchKind.MOBILE, "MobileActivity"),
                AppCandidate("pkg.video", "Video TV", LaunchKind.TV, "TvActivity"),
            ),
            selfPackage = "dev.basri.android.nobs_launcher",
        )

        assertEquals("TvActivity", result.single().activityName)
        assertEquals(LaunchKind.TV, result.single().kind)
    }

    @Test
    fun ownPackageIsExcluded() {
        val result = AppCatalogPolicy.select(
            candidates = listOf(
                AppCandidate(
                    "dev.basri.android.nobs_launcher",
                    "No bullshit launcher",
                    LaunchKind.TV,
                    "HomeActivity",
                ),
                AppCandidate("com.netflix.ninja", "Netflix", LaunchKind.TV, "MainActivity"),
            ),
            selfPackage = "dev.basri.android.nobs_launcher",
        )

        assertEquals(listOf("com.netflix.ninja"), result.map(AppCandidate::packageName))
    }

    @Test
    fun labelsSortCaseInsensitively() {
        val result = AppCatalogPolicy.select(
            candidates = listOf(
                AppCandidate("app.youtube", "YouTube", LaunchKind.TV, "YouTubeActivity"),
                AppCandidate("app.disney", "disney+", LaunchKind.TV, "DisneyActivity"),
                AppCandidate("app.netflix", "Netflix", LaunchKind.TV, "NetflixActivity"),
            ),
            selfPackage = "dev.basri.android.nobs_launcher",
        )

        assertEquals(listOf("disney+", "Netflix", "YouTube"), result.map(AppCandidate::label))
    }

    @Test
    fun duplicateTvCandidatesProduceOneTilePerPackage() {
        val result = AppCatalogPolicy.select(
            candidates = listOf(
                AppCandidate("app.video", "Video", LaunchKind.TV, "FirstActivity"),
                AppCandidate("app.video", "Video", LaunchKind.TV, "SecondActivity"),
            ),
            selfPackage = "dev.basri.android.nobs_launcher",
        )

        assertEquals(1, result.size)
        assertEquals("FirstActivity", result.single().activityName)
    }
}
