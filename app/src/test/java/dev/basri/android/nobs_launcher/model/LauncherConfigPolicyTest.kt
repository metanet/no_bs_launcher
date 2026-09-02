package dev.basri.android.nobs_launcher.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherConfigPolicyTest {
    @Test
    fun defaultConfigurationShowsPanelsWithNoFavoritesOrShortcuts() {
        assertEquals("", LauncherConfig.DEFAULT.welcomeText)
        assertEquals(true, LauncherConfig.DEFAULT.showLocation)
        assertEquals(true, LauncherConfig.DEFAULT.showVpnStatus)
        assertEquals(true, LauncherConfig.DEFAULT.showSystemStats)
        assertEquals(false, LauncherConfig.DEFAULT.firstRunComplete)
        assertEquals(emptyList<String>(), LauncherConfig.DEFAULT.favoriteItemIds)
        assertEquals(emptyList<WebShortcut>(), LauncherConfig.DEFAULT.shortcuts)
    }

    @Test
    fun favoriteMutationAppendsOnceAndRemovalPreservesOtherOrder() {
        val initial = config(listOf(APP_YOUTUBE, WEB_ONE))

        val added = LauncherConfigPolicy.setFavorite(initial, APP_NETFLIX, favorite = true)
        val duplicate = LauncherConfigPolicy.setFavorite(added, WEB_ONE, favorite = true)
        val removed = LauncherConfigPolicy.setFavorite(duplicate, WEB_ONE, favorite = false)

        assertEquals(listOf(APP_YOUTUBE, APP_NETFLIX), removed.favoriteItemIds)
    }

    @Test
    fun normalizeDropsDuplicatesMissingAppsAndOrphanedShortcuts() {
        val initial = config(
            favorites = listOf(APP_YOUTUBE, WEB_ONE, WEB_MISSING, APP_GONE, WEB_ONE),
            shortcuts = listOf(shortcut(UUID_ONE), shortcut(UUID_TWO)),
        )

        val normalized = LauncherConfigPolicy.normalize(initial, setOf("app.youtube"))

        assertEquals(listOf(APP_YOUTUBE, WEB_ONE), normalized.favoriteItemIds)
        assertEquals(initial.shortcuts, normalized.shortcuts)
    }

    @Test
    fun upsertRetainsIdentityAndFavoritePosition() {
        val original = shortcut(UUID_ONE, name = "Old", url = "https://old.example")
        val initial = config(listOf(WEB_ONE, APP_YOUTUBE), listOf(original))
        val edited = original.copy(name = "New", url = "https://new.example", faviconFileName = null)

        val updated = LauncherConfigPolicy.upsertShortcut(initial, edited)

        assertEquals(listOf(edited), updated.shortcuts)
        assertEquals(listOf(WEB_ONE, APP_YOUTUBE), updated.favoriteItemIds)
    }

    @Test
    fun removeShortcutAlsoRemovesFavoriteReference() {
        val initial = config(
            favorites = listOf(APP_YOUTUBE, WEB_ONE, APP_NETFLIX),
            shortcuts = listOf(shortcut(UUID_ONE), shortcut(UUID_TWO)),
        )

        val updated = LauncherConfigPolicy.removeShortcut(initial, UUID_ONE)

        assertEquals(listOf(APP_YOUTUBE, APP_NETFLIX), updated.favoriteItemIds)
        assertEquals(listOf(shortcut(UUID_TWO)), updated.shortcuts)
    }

    @Test
    fun moveReturnsAReorderedCopyAndInvalidMovesDoNothing() {
        val ids = listOf(APP_YOUTUBE, WEB_ONE, APP_NETFLIX)

        assertEquals(
            listOf(WEB_ONE, APP_NETFLIX, APP_YOUTUBE),
            LauncherConfigPolicy.move(ids, fromIndex = 0, toIndex = 2),
        )
        assertEquals(ids, LauncherConfigPolicy.move(ids, -1, 1))
        assertEquals(ids, LauncherConfigPolicy.move(ids, 0, 3))
        assertEquals(ids, LauncherConfigPolicy.move(ids, 1, 1))
    }

    private fun config(
        favorites: List<String> = emptyList(),
        shortcuts: List<WebShortcut> = emptyList(),
    ) = LauncherConfig(
        firstRunComplete = true,
        wifiLabel = "Kahveci House",
        locationLabel = "London",
        favoriteItemIds = favorites,
        shortcuts = shortcuts,
    )

    private fun shortcut(
        uuid: String,
        name: String = uuid,
        url: String = "https://example.com/$uuid",
    ) = WebShortcut(uuid, name, url)

    private companion object {
        const val APP_YOUTUBE = "app:app.youtube"
        const val APP_NETFLIX = "app:app.netflix"
        const val APP_GONE = "app:app.gone"
        const val UUID_ONE = "11111111-1111-4111-8111-111111111111"
        const val UUID_TWO = "22222222-2222-4222-8222-222222222222"
        const val UUID_MISSING = "33333333-3333-4333-8333-333333333333"
        const val WEB_ONE = "web:$UUID_ONE"
        const val WEB_MISSING = "web:$UUID_MISSING"
    }
}
