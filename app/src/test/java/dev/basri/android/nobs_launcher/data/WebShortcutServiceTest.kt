package dev.basri.android.nobs_launcher.data

import dev.basri.android.nobs_launcher.model.HomeItemId
import dev.basri.android.nobs_launcher.model.LauncherConfig
import dev.basri.android.nobs_launcher.model.WebShortcut
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebShortcutServiceTest {
    @Test
    fun invalidInputCompletesLocallyWithoutProbePersistenceOrFaviconWork() {
        val store = FakeStore(config())
        val probe = FakeProbe(WebsiteProbeResult.Reachable("https://unused.example", null))
        val executor = QueuedExecutor()
        val favicons = FakeFavicons()
        val results = mutableListOf<SaveShortcutResult>()
        val service = service(store, favicons, probe, executor)

        service.save(null, "Site", "file:///bad", onComplete = results::add)

        assertTrue(results.single() is SaveShortcutResult.Invalid)
        assertEquals(0, executor.tasks.size)
        assertEquals(emptyList<String>(), probe.urls)
        assertEquals(0, store.updateCalls)
        assertNoFaviconWork(favicons)
    }

    @Test
    fun inaccessibleNewInputDoesNotPersistDeleteOrFetch() {
        val store = FakeStore(config())
        val favicons = FakeFavicons()
        val results = mutableListOf<SaveShortcutResult>()
        val service = service(store, favicons, FakeProbe(WebsiteProbeResult.Inaccessible))

        service.save(null, "Site", "example.com", onComplete = results::add)

        assertEquals(listOf(SaveShortcutResult.WebsiteInaccessible), results)
        assertEquals(emptyList<WebShortcut>(), store.current.shortcuts)
        assertEquals(0, store.updateCalls)
        assertNoFaviconWork(favicons)
    }

    @Test
    fun inaccessibleEditLeavesExistingMetadataFavoriteAndIconUntouched() {
        val original = shortcut(url = "https://old.example", icon = "old.png")
        val initial = config(
            favorites = listOf("app:before", HomeItemId.web(original.uuid), "app:after"),
            shortcuts = listOf(original),
        )
        val store = FakeStore(initial)
        val favicons = FakeFavicons()
        val results = mutableListOf<SaveShortcutResult>()
        val service = service(store, favicons, FakeProbe(WebsiteProbeResult.Inaccessible))

        service.save(original.uuid, "Edited", "new.example", onComplete = results::add)

        assertEquals(listOf(SaveShortcutResult.WebsiteInaccessible), results)
        assertEquals(initial, store.current)
        assertEquals(0, store.updateCalls)
        assertNoFaviconWork(favicons)
    }

    @Test
    fun authenticationEquivalentReachableResultsPersistNormally() {
        listOf("401", "403").forEach { status ->
            val store = FakeStore(config())
            val favicons = FakeFavicons()
            val results = mutableListOf<SaveShortcutResult>()
            val service = service(
                store,
                favicons,
                FakeProbe(WebsiteProbeResult.Reachable("https://auth$status.example/login", null)),
            )

            service.save(null, "Auth $status", "auth$status.example", onComplete = results::add)

            assertTrue(results.single() is SaveShortcutResult.Saved)
            assertEquals("https://auth$status.example", store.current.shortcuts.single().url)
            assertEquals(
                listOf("https://auth$status.example/favicon.ico"),
                favicons.requests.single().candidates,
            )
        }
    }

    @Test
    fun reachableEditPreservesUuidAndFavoriteOrderAndSendsDiscoveredCandidates() {
        val original = shortcut(url = "https://old.example", icon = "old.png")
        val favorites = listOf("app:before", HomeItemId.web(original.uuid), "app:after")
        val store = FakeStore(config(favorites = favorites, shortcuts = listOf(original)))
        val favicons = FakeFavicons()
        val results = mutableListOf<SaveShortcutResult>()
        val html = """
            <html><head>
              <link rel="icon" href="/assets/main.png">
              <link rel="apple-touch-icon" href="touch.png">
            </head></html>
        """.trimIndent()
        val service = service(
            store,
            favicons,
            FakeProbe(WebsiteProbeResult.Reachable("https://final.example/path/page", html)),
        )

        service.save(original.uuid, "Edited", "start.example", onComplete = results::add)

        val saved = (results.single() as SaveShortcutResult.Saved).shortcut
        assertEquals(original.uuid, saved.uuid)
        assertEquals("Edited", saved.name)
        assertEquals("https://start.example", saved.url)
        assertEquals(null, saved.faviconFileName)
        assertEquals(favorites, store.current.favoriteItemIds)
        assertEquals(saved, store.current.shortcuts.single())
        assertEquals(listOf("old.png"), favicons.deleted)
        assertEquals(
            listOf(
                "https://final.example/assets/main.png",
                "https://final.example/path/touch.png",
                "https://final.example/favicon.ico",
            ),
            favicons.requests.single().candidates,
        )
    }

    @Test
    fun cancelWhileProbeIsCompletingPreventsPersistenceAndCallback() {
        val store = FakeStore(config())
        val favicons = FakeFavicons()
        val executor = QueuedExecutor()
        val results = mutableListOf<SaveShortcutResult>()
        lateinit var request: SaveShortcutRequest
        val probe = object : WebsiteProbeGateway {
            override fun probe(url: String): WebsiteProbeResult {
                request.cancel()
                return WebsiteProbeResult.Reachable("https://example.com", null)
            }
        }
        val service = service(store, favicons, probe, executor)
        request = service.save(null, "Site", "example.com", onComplete = results::add)

        executor.runNext()

        assertEquals(emptyList<SaveShortcutResult>(), results)
        assertEquals(0, store.updateCalls)
        assertEquals(emptyList<WebShortcut>(), store.current.shortcuts)
        assertNoFaviconWork(favicons)
    }

    @Test
    fun cancellationThatReturnsBeforeQueuedWorkPreventsEvenTheProbe() {
        val store = FakeStore(config())
        val probe = FakeProbe(WebsiteProbeResult.Reachable("https://example.com", null))
        val executor = QueuedExecutor()
        val results = mutableListOf<SaveShortcutResult>()
        val request = service(store, FakeFavicons(), probe, executor)
            .save(null, "Site", "example.com", onComplete = results::add)

        request.cancel()
        executor.runNext()

        assertEquals(emptyList<String>(), probe.urls)
        assertEquals(0, store.updateCalls)
        assertEquals(emptyList<SaveShortcutResult>(), results)
    }

    @Test
    fun successfulPersistenceNotifiesSavedBeforeFaviconWorkAndLateAttachment() {
        val store = FakeStore(config())
        val events = mutableListOf<String>()
        val favicons = FakeFavicons(onFetch = { events += "fetch" })
        val service = service(
            store,
            favicons,
            FakeProbe(WebsiteProbeResult.Reachable("https://example.com/page", null)),
        )

        service.save(
            existingUuid = null,
            name = "Site",
            url = "example.com",
            onIconUpdated = { events += "icon-updated" },
            onComplete = { result ->
                events += if (result is SaveShortcutResult.Saved) "saved" else "failed"
            },
        )

        assertEquals(listOf("saved", "fetch"), events)
        assertEquals(null, store.current.shortcuts.single().faviconFileName)

        favicons.complete("fresh.png")

        assertEquals(listOf("saved", "fetch", "icon-updated"), events)
        assertEquals("fresh.png", store.current.shortcuts.single().faviconFileName)
    }

    @Test
    fun callbackExceptionsDoNotSuppressFaviconFetchAttachmentOrEscapeCompletion() {
        val store = FakeStore(config())
        val favicons = FakeFavicons()
        val service = service(
            store,
            favicons,
            FakeProbe(WebsiteProbeResult.Reachable("https://example.com/page", null)),
        )

        service.save(
            existingUuid = null,
            name = "Site",
            url = "example.com",
            onIconUpdated = { error("client icon callback failed") },
            onComplete = { error("client save callback failed") },
        )

        assertEquals(1, favicons.requests.size)
        favicons.complete("fresh.png")
        assertEquals("fresh.png", store.current.shortcuts.single().faviconFileName)
    }

    @Test
    fun staleFaviconCompletionIsDeletedAndCannotOverwriteNewerUrl() {
        val store = FakeStore(config())
        val favicons = FakeFavicons()
        val service = service(
            store,
            favicons,
            FakeProbe(WebsiteProbeResult.Reachable("https://first.example", null)),
        )

        service.save(null, "First", "first.example", onComplete = {})
        store.current = store.current.copy(
            shortcuts = listOf(shortcut(uuid = UUID_NEW, url = "https://second.example")),
        )
        favicons.complete("stale.png")

        assertEquals("https://second.example", store.current.shortcuts.single().url)
        assertEquals(null, store.current.shortcuts.single().faviconFileName)
        assertEquals(listOf("stale.png"), favicons.deleted)
    }

    @Test
    fun unchangedUrlWithAnExistingIconDoesNotFetchOrDeleteIt() {
        val original = shortcut(icon = "existing.png")
        val store = FakeStore(config(shortcuts = listOf(original)))
        val favicons = FakeFavicons()
        val results = mutableListOf<SaveShortcutResult>()
        val service = service(
            store,
            favicons,
            FakeProbe(
                WebsiteProbeResult.Reachable(
                    "https://example.com/redirected",
                    "<link rel=icon href=/new.png>",
                ),
            ),
        )

        service.save(original.uuid, "Renamed", original.url, onComplete = results::add)

        assertTrue(results.single() is SaveShortcutResult.Saved)
        assertEquals("existing.png", store.current.shortcuts.single().faviconFileName)
        assertNoFaviconWork(favicons)
    }

    @Test
    fun unchangedUrlWithoutAnIconStartsFaviconDiscovery() {
        val original = shortcut(icon = null)
        val store = FakeStore(config(shortcuts = listOf(original)))
        val favicons = FakeFavicons()
        val service = service(
            store,
            favicons,
            FakeProbe(
                WebsiteProbeResult.Reachable(
                    "https://example.com/final",
                    "<link rel=icon href=declared.png>",
                ),
            ),
        )

        service.save(original.uuid, "Renamed", original.url, onComplete = {})

        assertEquals(
            listOf("https://example.com/declared.png", "https://example.com/favicon.ico"),
            favicons.requests.single().candidates,
        )
    }

    @Test
    fun failedAtomicPersistenceReportsSaveFailedWithoutDeletingOrFetching() {
        val original = shortcut(url = "https://old.example", icon = "old.png")
        val store = FakeStore(config(shortcuts = listOf(original)), saveAllowed = false)
        val favicons = FakeFavicons()
        val results = mutableListOf<SaveShortcutResult>()
        val service = service(
            store,
            favicons,
            FakeProbe(WebsiteProbeResult.Reachable("https://new.example", null)),
        )

        service.save(original.uuid, "Edited", "new.example", onComplete = results::add)

        assertEquals(listOf(SaveShortcutResult.SaveFailed), results)
        assertEquals(listOf(original), store.current.shortcuts)
        assertNoFaviconWork(favicons)
    }

    @Test
    fun thrownProbeFailureReportsSaveFailedExactlyOnce() {
        val results = mutableListOf<SaveShortcutResult>()
        val service = service(
            FakeStore(config()),
            FakeFavicons(),
            WebsiteProbeGateway { error("boom") },
        )

        service.save(null, "Site", "example.com", onComplete = results::add)

        assertEquals(listOf(SaveShortcutResult.SaveFailed), results)
    }

    @Test
    fun executorRejectionReportsSaveFailedExactlyOnce() {
        val results = mutableListOf<SaveShortcutResult>()
        val store = FakeStore(config())
        val service = service(
            store,
            FakeFavicons(),
            FakeProbe(WebsiteProbeResult.Reachable("https://example.com", null)),
            Executor { throw RejectedExecutionException("stopped") },
        )

        service.save(null, "Site", "example.com", onComplete = results::add)

        assertEquals(listOf(SaveShortcutResult.SaveFailed), results)
        assertEquals(0, store.updateCalls)
    }

    @Test
    fun executorThatRunsThenThrowsStillReportsOnlyOneTerminalResult() {
        val results = mutableListOf<SaveShortcutResult>()
        val executor = Executor { task ->
            task.run()
            throw RejectedExecutionException("misbehaving executor")
        }
        val service = service(
            FakeStore(config()),
            FakeFavicons(),
            FakeProbe(WebsiteProbeResult.Reachable("https://example.com", null)),
            executor,
        )

        service.save(null, "Site", "example.com", onComplete = results::add)

        assertEquals(1, results.size)
        assertTrue(results.single() is SaveShortcutResult.Saved)
    }

    @Test
    fun faviconAttachmentPreservesAConcurrentConfigMutation() {
        val store = FakeStore(config())
        val favicons = FakeFavicons()
        val service = service(
            store,
            favicons,
            FakeProbe(WebsiteProbeResult.Reachable("https://example.com", null)),
        )

        service.save(null, "Site", "example.com", onComplete = {})
        store.beforeNextUpdate = { current -> current.copy(welcomeText = "Concurrent update") }
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
        val service = service(
            store,
            favicons,
            FakeProbe(WebsiteProbeResult.Reachable(original.url, null)),
        )

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

    private fun service(
        store: FakeStore,
        favicons: FakeFavicons,
        probe: WebsiteProbeGateway,
        executor: Executor = DIRECT_EXECUTOR,
    ) = WebShortcutService(
        store = store,
        favicons = favicons,
        websiteProbe = probe,
        executor = executor,
        uuidFactory = { UUID_NEW },
    )

    private fun assertNoFaviconWork(favicons: FakeFavicons) {
        assertEquals(emptyList<String>(), favicons.deleted)
        assertEquals(0, favicons.requests.size)
    }

    private class FakeStore(
        initial: LauncherConfig,
        var saveAllowed: Boolean = true,
    ) : LauncherConfigStore {
        var current = initial
        var updateCalls = 0
        var beforeNextUpdate: ((LauncherConfig) -> LauncherConfig)? = null

        override fun load(): LauncherConfig = current

        override fun save(config: LauncherConfig): Boolean {
            if (!saveAllowed) return false
            current = config
            return true
        }

        override fun update(transform: (LauncherConfig) -> LauncherConfig?): Boolean {
            updateCalls += 1
            if (!saveAllowed) return false
            beforeNextUpdate?.let { mutation ->
                beforeNextUpdate = null
                current = mutation(current)
            }
            val updated = transform(current) ?: return false
            current = updated
            return true
        }
    }

    private class FakeProbe(
        private val result: WebsiteProbeResult,
    ) : WebsiteProbeGateway {
        val urls = mutableListOf<String>()

        override fun probe(url: String): WebsiteProbeResult {
            urls += url
            return result
        }
    }

    private class QueuedExecutor : Executor {
        val tasks = mutableListOf<Runnable>()

        override fun execute(command: Runnable) {
            tasks += command
        }

        fun runNext() {
            tasks.removeAt(0).run()
        }
    }

    private data class FaviconRequest(
        val shortcut: WebShortcut,
        val candidates: List<String>,
    )

    private class FakeFavicons(
        private val onFetch: () -> Unit = {},
    ) : FaviconGateway {
        val requests = mutableListOf<FaviconRequest>()
        val deleted = mutableListOf<String>()
        private var callback: ((String?) -> Unit)? = null

        override fun fetchAndStore(
            shortcut: WebShortcut,
            candidates: List<String>,
            onComplete: (String?) -> Unit,
        ) {
            onFetch()
            requests += FaviconRequest(shortcut, candidates)
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
        val DIRECT_EXECUTOR = Executor(Runnable::run)
    }
}
