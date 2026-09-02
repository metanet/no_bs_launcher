package dev.basri.android.nobs_launcher.data

import dev.basri.android.nobs_launcher.model.HomeItemId
import dev.basri.android.nobs_launcher.model.LauncherConfig
import dev.basri.android.nobs_launcher.model.ShortcutInput
import dev.basri.android.nobs_launcher.model.WebShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebShortcutServiceTest {
    @Test
    fun invalidEditDoesNotChangeMetadataOrIcon() {
        val original = shortcut(url = "https://old.example", icon = "old.png")
        val store = FakeStore(config(shortcuts = listOf(original)))
        val favicons = FakeFavicons()
        val service = WebShortcutService(store, favicons) { UUID_NEW }

        val result = service.save(original.uuid, "Edited", "file:///bad")

        assertTrue(result is SaveShortcutResult.Invalid)
        assertEquals(listOf(original), store.current.shortcuts)
        assertEquals(emptyList<String>(), favicons.deleted)
        assertEquals(0, favicons.requests.size)
    }

    @Test
    fun failedMetadataSaveLeavesPreviousRecordAndIconUntouched() {
        val original = shortcut(url = "https://old.example", icon = "old.png")
        val store = FakeStore(config(shortcuts = listOf(original)), saveAllowed = false)
        val favicons = FakeFavicons()

        val result = WebShortcutService(store, favicons) { UUID_NEW }
            .save(original.uuid, "Edited", "https://new.example")

        assertEquals(SaveShortcutResult.SaveFailed, result)
        assertEquals(listOf(original), store.current.shortcuts)
        assertEquals(emptyList<String>(), favicons.deleted)
        assertEquals(0, favicons.requests.size)
    }

    @Test
    fun validUrlChangePersistsFallbackThenDeletesOldIconAndAttachesFreshIcon() {
        val original = shortcut(url = "https://old.example", icon = "old.png")
        val store = FakeStore(
            config(
                favorites = listOf(HomeItemId.web(original.uuid)),
                shortcuts = listOf(original),
            ),
        )
        val favicons = FakeFavicons()
        val service = WebShortcutService(store, favicons) { UUID_NEW }

        val result = service.save(original.uuid, "Edited", "new.example/path")

        assertTrue(result is SaveShortcutResult.Saved)
        assertEquals(null, store.current.shortcuts.single().faviconFileName)
        assertEquals(listOf("old.png"), favicons.deleted)
        assertEquals("https://new.example/path", favicons.requests.single().url)
        favicons.complete("fresh.png")
        assertEquals("fresh.png", store.current.shortcuts.single().faviconFileName)
        assertEquals(listOf(HomeItemId.web(original.uuid)), store.current.favoriteItemIds)
    }

    @Test
    fun staleFaviconCompletionIsDeletedAndCannotOverwriteNewerUrl() {
        val store = FakeStore(config())
        val favicons = FakeFavicons()
        val service = WebShortcutService(store, favicons) { UUID_NEW }

        service.save(null, "First", "first.example")
        store.current = store.current.copy(
            shortcuts = listOf(shortcut(uuid = UUID_NEW, url = "https://second.example")),
        )
        favicons.complete("stale.png")

        assertEquals("https://second.example", store.current.shortcuts.single().url)
        assertEquals(null, store.current.shortcuts.single().faviconFileName)
        assertEquals(listOf("stale.png"), favicons.deleted)
    }

    @Test
    fun faviconAttachmentCannotOverwriteAConfigMutationThatLandsBeforeItsSave() {
        val store = FakeStore(config())
        val favicons = FakeFavicons()
        val service = WebShortcutService(store, favicons) { UUID_NEW }

        service.save(null, "Site", "example.com")
        store.beforeNextSave = { current -> current.copy(welcomeText = "Concurrent update") }
        favicons.complete("fresh.png")

        assertEquals("Concurrent update", store.current.welcomeText)
        assertEquals("fresh.png", store.current.shortcuts.single().faviconFileName)
    }

    @Test
    fun removePersistsBeforeDeletingIconAndFavoriteReference() {
        val original = shortcut(icon = "old.png")
        val store = FakeStore(
            config(
                favorites = listOf(HomeItemId.web(original.uuid)),
                shortcuts = listOf(original),
            ),
        )
        val favicons = FakeFavicons()
        val service = WebShortcutService(store, favicons) { UUID_NEW }

        assertTrue(service.remove(original.uuid))
        assertEquals(emptyList<WebShortcut>(), store.current.shortcuts)
        assertEquals(emptyList<String>(), store.current.favoriteItemIds)
        assertEquals(listOf("old.png"), favicons.deleted)

        store.current = config(
            favorites = listOf(HomeItemId.web(original.uuid)),
            shortcuts = listOf(original),
        )
        store.saveAllowed = false
        favicons.deleted.clear()
        assertFalse(service.remove(original.uuid))
        assertEquals(listOf(original), store.current.shortcuts)
        assertEquals(emptyList<String>(), favicons.deleted)
    }

    private class FakeStore(
        initial: LauncherConfig,
        var saveAllowed: Boolean = true,
    ) : LauncherConfigStore {
        var current = initial
        var beforeNextSave: ((LauncherConfig) -> LauncherConfig)? = null

        override fun load(): LauncherConfig = current

        override fun save(config: LauncherConfig): Boolean {
            if (!saveAllowed) return false
            beforeNextSave?.let { mutation ->
                beforeNextSave = null
                current = mutation(current)
            }
            current = config
            return true
        }

        override fun update(transform: (LauncherConfig) -> LauncherConfig?): Boolean {
            if (!saveAllowed) return false
            beforeNextSave?.let { mutation ->
                beforeNextSave = null
                current = mutation(current)
            }
            val updated = transform(current) ?: return false
            current = updated
            return true
        }
    }

    private class FakeFavicons : FaviconGateway {
        val requests = mutableListOf<WebShortcut>()
        val deleted = mutableListOf<String>()
        private var callback: ((String?) -> Unit)? = null

        override fun fetchAndStore(shortcut: WebShortcut, onComplete: (String?) -> Unit) {
            requests += shortcut
            callback = onComplete
        }

        override fun delete(fileName: String) {
            deleted += fileName
        }

        fun complete(fileName: String?) {
            val pending = checkNotNull(callback)
            callback = null
            pending(fileName)
        }
    }

    private fun config(
        favorites: List<String> = emptyList(),
        shortcuts: List<WebShortcut> = emptyList(),
    ) = LauncherConfig(
        firstRunComplete = true,
        wifiLabel = "",
        locationLabel = "",
        favoriteItemIds = favorites,
        shortcuts = shortcuts,
    )

    private fun shortcut(
        uuid: String = UUID_EXISTING,
        url: String = "https://example.com",
        icon: String? = null,
    ) = WebShortcut(uuid, "Site", url, icon)

    private companion object {
        const val UUID_EXISTING = "11111111-1111-4111-8111-111111111111"
        const val UUID_NEW = "22222222-2222-4222-8222-222222222222"
    }
}
