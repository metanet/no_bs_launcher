package dev.basri.android.nobs_launcher.data

import dev.basri.android.nobs_launcher.model.HomeItemId
import dev.basri.android.nobs_launcher.model.LauncherConfigPolicy
import dev.basri.android.nobs_launcher.model.ShortcutInput
import dev.basri.android.nobs_launcher.model.WebShortcut
import dev.basri.android.nobs_launcher.model.WebShortcutPolicy
import java.util.UUID

interface FaviconGateway {
    fun fetchAndStore(shortcut: WebShortcut, onComplete: (String?) -> Unit)

    fun delete(fileName: String)
}

sealed interface SaveShortcutResult {
    data class Invalid(val validation: ShortcutInput.Invalid) : SaveShortcutResult

    data object SaveFailed : SaveShortcutResult

    data class Saved(val shortcut: WebShortcut) : SaveShortcutResult
}

class WebShortcutService(
    private val store: LauncherConfigStore,
    private val favicons: FaviconGateway,
    private val uuidFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun save(
        existingUuid: String?,
        name: String,
        url: String,
        onIconUpdated: () -> Unit = {},
    ): SaveShortcutResult {
        val validation = WebShortcutPolicy.validate(name, url)
        if (validation is ShortcutInput.Invalid) return SaveShortcutResult.Invalid(validation)
        validation as ShortcutInput.Valid

        var savedShortcut: WebShortcut? = null
        var previousIcon: String? = null
        var urlChanged = false
        val saved = store.update { config ->
            val existing = existingUuid?.let { uuid ->
                config.shortcuts.firstOrNull { it.uuid == uuid }
                    ?: return@update null
            }
            val uuid = existing?.uuid ?: uuidFactory()
            if (HomeItemId.webUuid(HomeItemId.web(uuid)) == null) return@update null
            urlChanged = existing == null || existing.url != validation.url
            previousIcon = existing?.faviconFileName?.takeIf { urlChanged }
            val shortcut = WebShortcut(
                uuid = uuid,
                name = validation.name,
                url = validation.url,
                faviconFileName = existing?.faviconFileName?.takeUnless { urlChanged },
            )
            savedShortcut = shortcut
            LauncherConfigPolicy.upsertShortcut(config, shortcut)
        }
        val shortcut = savedShortcut
        if (!saved || shortcut == null) return SaveShortcutResult.SaveFailed

        if (urlChanged) {
            previousIcon?.let(favicons::delete)
            favicons.fetchAndStore(shortcut) { fetchedFileName ->
                attachFetchedIcon(shortcut, fetchedFileName)
                onIconUpdated()
            }
        }
        return SaveShortcutResult.Saved(shortcut)
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

    private fun attachFetchedIcon(shortcut: WebShortcut, fetchedFileName: String?) {
        if (fetchedFileName == null) return
        val attached = store.update { current ->
            val storedShortcut = current.shortcuts.firstOrNull { it.uuid == shortcut.uuid }
            if (storedShortcut?.url != shortcut.url) return@update null
            val withIcon = storedShortcut.copy(faviconFileName = fetchedFileName)
            LauncherConfigPolicy.upsertShortcut(current, withIcon)
        }
        if (!attached) {
            favicons.delete(fetchedFileName)
        }
    }
}
