package dev.basri.android.nobs_launcher.model

import dev.basri.android.nobs_launcher.data.AppCandidate
import dev.basri.android.nobs_launcher.data.LaunchKind
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeItemSectionsPolicyTest {
    @Test
    fun appsAndShortcutsShareFavoriteOrder() {
        val alphaWeb = web(UUID_ONE, "Alpha")
        val betaApp = app("app.beta", "Beta")
        val gammaWeb = web(UUID_TWO, "Gamma")

        val sections = HomeItemSectionsPolicy.compose(
            items = listOf(alphaWeb, betaApp, gammaWeb),
            favoriteItemIds = listOf(gammaWeb.id, betaApp.id),
        )

        assertEquals(listOf(gammaWeb, betaApp), sections.favorites)
        assertEquals(listOf(alphaWeb), sections.remaining)
    }

    @Test
    fun missingAndDuplicateFavoriteIdsAreIgnored() {
        val app = app("app.alpha", "Alpha")
        val web = web(UUID_ONE, "Web")

        val sections = HomeItemSectionsPolicy.compose(
            items = listOf(app, web),
            favoriteItemIds = listOf("app:missing.package", web.id, web.id),
        )

        assertEquals(listOf(web), sections.favorites)
        assertEquals(listOf(app), sections.remaining)
    }

    @Test
    fun remainingItemsSortByLabelThenTypeThenStableId() {
        val appSame = app("app.same", "same")
        val webSecond = web(UUID_TWO, "Same")
        val webFirst = web(UUID_ONE, "SAME")
        val zulu = app("app.zulu", "Zulu")

        val sections = HomeItemSectionsPolicy.compose(
            items = listOf(zulu, webSecond, appSame, webFirst),
            favoriteItemIds = emptyList(),
        )

        assertEquals(listOf(appSame, webFirst, webSecond, zulu), sections.remaining)
    }

    @Test
    fun duplicateCatalogIdsUseFirstItemAndDoNotRepeat() {
        val original = app("app.alpha", "Alpha")
        val duplicate = app("app.alpha", "Other label")

        val sections = HomeItemSectionsPolicy.compose(
            items = listOf(original, duplicate),
            favoriteItemIds = listOf(original.id),
        )

        assertEquals(listOf(original), sections.favorites)
        assertEquals(emptyList<HomeItem>(), sections.remaining)
    }

    private fun app(packageName: String, label: String): HomeItem.App = HomeItem.App(
        AppCandidate(
            packageName = packageName,
            label = label,
            kind = LaunchKind.TV,
            activityName = "$packageName.MainActivity",
        ),
    )

    private fun web(uuid: String, label: String): HomeItem.Web = HomeItem.Web(
        WebShortcut(
            uuid = uuid,
            name = label,
            url = "https://example.com/$uuid",
        ),
    )

    private companion object {
        const val UUID_ONE = "11111111-1111-4111-8111-111111111111"
        const val UUID_TWO = "22222222-2222-4222-8222-222222222222"
    }
}
