package dev.basri.android.nobs_launcher.data

import dev.basri.android.nobs_launcher.model.HomeItemId
import dev.basri.android.nobs_launcher.model.LauncherConfigPolicy
import dev.basri.android.nobs_launcher.model.ShortcutInput
import dev.basri.android.nobs_launcher.model.WebShortcut
import dev.basri.android.nobs_launcher.model.WebShortcutPolicy
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors

interface FaviconGateway {
    fun fetchAndStore(
        shortcut: WebShortcut,
        candidates: List<String>,
        onComplete: (String?) -> Unit,
    )

    fun delete(fileName: String)
}

sealed interface SaveShortcutResult {
    data class Invalid(val validation: ShortcutInput.Invalid) : SaveShortcutResult

    data object WebsiteInaccessible : SaveShortcutResult

    data object SaveFailed : SaveShortcutResult

    data class Saved(val shortcut: WebShortcut) : SaveShortcutResult
}

class SaveShortcutRequest internal constructor() {
    private val stateLock = Any()

    @Volatile
    private var state = State.ACTIVE
    private var cancelAction: (() -> Unit)? = null

    fun cancel() {
        val action = synchronized(stateLock) {
            if (state != State.ACTIVE) return
            state = State.CANCELLED
            cancelAction.also { cancelAction = null }
        }
        runCatching { action?.invoke() }
    }

    internal fun isActive(): Boolean = state == State.ACTIVE

    internal fun <T : Any> runIfActive(block: () -> T): T? = synchronized(stateLock) {
        if (state == State.ACTIVE) block() else null
    }

    internal fun registerCancelAction(action: () -> Unit) {
        val cancelImmediately = synchronized(stateLock) {
            if (state == State.ACTIVE) {
                cancelAction = action
                false
            } else {
                true
            }
        }
        if (cancelImmediately) runCatching(action)
    }

    internal fun complete(
        result: SaveShortcutResult,
        onComplete: (SaveShortcutResult) -> Unit,
    ) {
        val shouldNotify = synchronized(stateLock) {
            if (state != State.ACTIVE) {
                false
            } else {
                state = State.COMPLETED
                cancelAction = null
                true
            }
        }
        if (shouldNotify) {
            try {
                onComplete(result)
            } catch (_: Exception) {
                // Client callbacks cannot change the completed request or interrupt follow-up work.
            }
        }
    }

    private enum class State {
        ACTIVE,
        CANCELLED,
        COMPLETED,
    }
}

class WebShortcutService(
    private val store: LauncherConfigStore,
    private val favicons: FaviconGateway,
    private val websiteProbe: WebsiteProbeGateway = WebsiteHttpClient(),
    private val executor: Executor = SAVE_EXECUTOR,
    private val uuidFactory: () -> String = { UUID.randomUUID().toString() },
) {
    /**
     * Validates input on the caller thread, then probes and persists on the configured save
     * [executor]. Local-validation and executor-rejection results may invoke [onComplete] on the
     * caller thread; normal terminal results invoke it on the save executor. [onIconUpdated] runs
     * later on the favicon gateway's completion thread. UI clients must dispatch either callback
     * to the main thread before touching views. Client callback exceptions are contained.
     */
    fun save(
        existingUuid: String?,
        name: String,
        url: String,
        onIconUpdated: () -> Unit = {},
        onComplete: (SaveShortcutResult) -> Unit,
    ): SaveShortcutRequest {
        val request = SaveShortcutRequest()
        val validation = WebShortcutPolicy.validate(name, url)
        if (validation is ShortcutInput.Invalid) {
            request.complete(SaveShortcutResult.Invalid(validation), onComplete)
            return request
        }
        validation as ShortcutInput.Valid

        try {
            executor.execute {
                try {
                    saveReachable(
                        request = request,
                        existingUuid = existingUuid,
                        validation = validation,
                        onIconUpdated = onIconUpdated,
                        onComplete = onComplete,
                    )
                } catch (_: Exception) {
                    request.complete(SaveShortcutResult.SaveFailed, onComplete)
                }
            }
        } catch (_: Exception) {
            request.complete(SaveShortcutResult.SaveFailed, onComplete)
        }
        return request
    }

    fun remove(uuid: String): Boolean {
        var shortcut: WebShortcut? = null
        val saved = store.update { config ->
            shortcut = config.shortcuts.firstOrNull { it.uuid == uuid }
                ?: return@update null
            LauncherConfigPolicy.removeShortcut(config, uuid)
        }
        val removedShortcut = shortcut
        if (!saved || removedShortcut == null) return false
        removedShortcut.faviconFileName?.let(favicons::delete)
        return true
    }

    private fun saveReachable(
        request: SaveShortcutRequest,
        existingUuid: String?,
        validation: ShortcutInput.Valid,
        onIconUpdated: () -> Unit,
        onComplete: (SaveShortcutResult) -> Unit,
    ) {
        if (!request.isActive()) return
        val probeResult = websiteProbe.probe(
            validation.url,
            CancellationRegistration(request::registerCancelAction),
        )
        if (!request.isActive()) return
        if (probeResult is WebsiteProbeResult.Inaccessible) {
            request.complete(SaveShortcutResult.WebsiteInaccessible, onComplete)
            return
        }
        probeResult as WebsiteProbeResult.Reachable

        val persistence = request.runIfActive { persist(existingUuid, validation) } ?: return
        if (persistence is PersistenceResult.Failed) {
            request.complete(SaveShortcutResult.SaveFailed, onComplete)
            return
        }
        persistence as PersistenceResult.Persisted

        deletePreviousIcon(persistence.previousIcon)
        request.complete(SaveShortcutResult.Saved(persistence.shortcut), onComplete)
        startFaviconFetch(
            persistence = persistence,
            candidates = FaviconDiscovery.candidates(probeResult.finalUrl, probeResult.html),
            onIconUpdated = onIconUpdated,
        )
    }

    private fun persist(
        existingUuid: String?,
        validation: ShortcutInput.Valid,
    ): PersistenceResult {
        var persisted: PersistenceResult.Persisted? = null
        val saved = store.update { config ->
            val existing = existingUuid?.let { uuid ->
                config.shortcuts.firstOrNull { it.uuid == uuid }
                    ?: return@update null
            }
            val uuid = existing?.uuid ?: uuidFactory()
            if (HomeItemId.webUuid(HomeItemId.web(uuid)) == null) return@update null
            val urlChanged = existing == null || existing.url != validation.url
            val shortcut = WebShortcut(
                uuid = uuid,
                name = validation.name,
                url = validation.url,
                faviconFileName = existing?.faviconFileName?.takeUnless { urlChanged },
            )
            persisted = PersistenceResult.Persisted(
                shortcut = shortcut,
                previousIcon = existing?.faviconFileName?.takeIf { urlChanged },
                shouldFetchIcon = urlChanged || existing?.faviconFileName == null,
            )
            LauncherConfigPolicy.upsertShortcut(config, shortcut)
        }
        return persisted?.takeIf { saved } ?: PersistenceResult.Failed
    }

    private fun deletePreviousIcon(previousIcon: String?) {
        previousIcon ?: return
        runCatching { favicons.delete(previousIcon) }
    }

    private fun startFaviconFetch(
        persistence: PersistenceResult.Persisted,
        candidates: List<String>,
        onIconUpdated: () -> Unit,
    ) {
        if (!persistence.shouldFetchIcon) return
        favicons.fetchAndStore(persistence.shortcut, candidates) { fetchedFileName ->
            attachFetchedIcon(persistence.shortcut, fetchedFileName)
            try {
                onIconUpdated()
            } catch (_: Exception) {
                // Icon persistence is complete; a client refresh failure must not escape the worker.
            }
        }
    }

    private fun attachFetchedIcon(shortcut: WebShortcut, fetchedFileName: String?) {
        if (fetchedFileName == null) return
        val attached = runCatching {
            store.update { current ->
                val storedShortcut = current.shortcuts.firstOrNull { it.uuid == shortcut.uuid }
                if (storedShortcut?.url != shortcut.url) return@update null
                val withIcon = storedShortcut.copy(faviconFileName = fetchedFileName)
                LauncherConfigPolicy.upsertShortcut(current, withIcon)
            }
        }.getOrDefault(false)
        if (!attached) {
            favicons.delete(fetchedFileName)
        }
    }

    private sealed interface PersistenceResult {
        data object Failed : PersistenceResult

        data class Persisted(
            val shortcut: WebShortcut,
            val previousIcon: String?,
            val shouldFetchIcon: Boolean,
        ) : PersistenceResult
    }

    private companion object {
        val SAVE_EXECUTOR: Executor by lazy { Executors.newSingleThreadExecutor() }
    }
}
