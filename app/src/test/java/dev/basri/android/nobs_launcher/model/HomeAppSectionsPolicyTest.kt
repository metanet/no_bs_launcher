package dev.basri.android.nobs_launcher.model

import dev.basri.android.nobs_launcher.data.AppCandidate
import dev.basri.android.nobs_launcher.data.LaunchKind
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAppSectionsPolicyTest {
    @Test
    fun favoritesKeepStoredOrderAndRemainingAppsAreAlphabetical() {
        val zulu = app("app.zulu", "Zulu")
        val alpha = app("app.alpha", "alpha")
        val beta = app("app.beta", "Beta")

        val sections = HomeAppSectionsPolicy.compose(
            catalogApps = listOf(zulu, beta, alpha),
            favoritePackages = listOf(zulu.packageName, beta.packageName),
        )

        assertEquals(listOf(zulu, beta), sections.favorites)
        assertEquals(listOf(alpha), sections.remaining)
    }

    @Test
    fun missingAndDuplicateFavoritePackagesAreIgnored() {
        val alpha = app("app.alpha", "Alpha")
        val beta = app("app.beta", "Beta")

        val sections = HomeAppSectionsPolicy.compose(
            catalogApps = listOf(alpha, beta),
            favoritePackages = listOf("app.missing", beta.packageName, beta.packageName),
        )

        assertEquals(listOf(beta), sections.favorites)
        assertEquals(listOf(alpha), sections.remaining)
    }

    @Test
    fun remainingAppsUsePackageNameAsCaseInsensitiveLabelTieBreaker() {
        val second = app("app.second", "same")
        val first = app("app.first", "Same")

        val sections = HomeAppSectionsPolicy.compose(
            catalogApps = listOf(second, first),
            favoritePackages = emptyList(),
        )

        assertEquals(listOf(first, second), sections.remaining)
    }

    @Test
    fun allCatalogAppsCanBeFavoritesWithoutRemainder() {
        val alpha = app("app.alpha", "Alpha")

        val sections = HomeAppSectionsPolicy.compose(
            catalogApps = listOf(alpha),
            favoritePackages = listOf(alpha.packageName),
        )

        assertEquals(listOf(alpha), sections.favorites)
        assertEquals(emptyList<AppCandidate>(), sections.remaining)
    }

    private fun app(packageName: String, label: String) = AppCandidate(
        packageName = packageName,
        label = label,
        kind = LaunchKind.TV,
        activityName = "$packageName.MainActivity",
    )
}
