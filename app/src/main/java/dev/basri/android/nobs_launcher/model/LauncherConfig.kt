package dev.basri.android.nobs_launcher.model

data class LauncherConfig(
    val firstRunComplete: Boolean,
    val favoriteItemIds: List<String>,
    val shortcuts: List<WebShortcut> = emptyList(),
    val welcomeText: String = "",
    val showWifiName: Boolean = true,
    val showLocation: Boolean = true,
    val showVpnStatus: Boolean = true,
    val showSystemStats: Boolean = true,
) {
    val selectedPackages: List<String>
        get() = favoriteItemIds.mapNotNull(HomeItemId::appPackage)

    companion object {
        val DEFAULT = LauncherConfig(
            firstRunComplete = false,
            favoriteItemIds = emptyList(),
        )
    }
}

object LauncherConfigPolicy {
    fun setVisible(
        config: LauncherConfig,
        packageName: String,
        visible: Boolean,
    ): LauncherConfig = setFavorite(config, HomeItemId.app(packageName), visible)

    fun setFavorite(
        config: LauncherConfig,
        itemId: String,
        favorite: Boolean,
    ): LauncherConfig {
        if (!HomeItemId.isValid(itemId)) return config
        val ordered = config.favoriteItemIds.distinct().toMutableList()
        if (favorite && itemId !in ordered) {
            ordered += itemId
        }
        if (!favorite) {
            ordered.removeAll { it == itemId }
        }
        return config.copy(favoriteItemIds = ordered)
    }

    fun move(itemIds: List<String>, fromIndex: Int, toIndex: Int): List<String> {
        if (fromIndex !in itemIds.indices || toIndex !in itemIds.indices || fromIndex == toIndex) {
            return itemIds.toList()
        }
        return itemIds.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }

    fun normalize(config: LauncherConfig): LauncherConfig {
        val shortcutIds = config.shortcuts.mapTo(mutableSetOf(), WebShortcut::itemId)
        return config.copy(
            favoriteItemIds = config.favoriteItemIds
                .distinct()
                .filter { itemId -> HomeItemId.appPackage(itemId) != null || itemId in shortcutIds },
            shortcuts = config.shortcuts.distinctBy(WebShortcut::uuid),
        )
    }

    fun upsertShortcut(config: LauncherConfig, shortcut: WebShortcut): LauncherConfig {
        if (HomeItemId.webUuid(shortcut.itemId) == null) return config
        val existingIndex = config.shortcuts.indexOfFirst { it.uuid == shortcut.uuid }
        val updated = config.shortcuts.toMutableList().apply {
            if (existingIndex >= 0) {
                this[existingIndex] = shortcut
            } else {
                add(shortcut)
            }
        }
        return config.copy(shortcuts = updated.distinctBy(WebShortcut::uuid))
    }

    fun removeShortcut(config: LauncherConfig, uuid: String): LauncherConfig = config.copy(
        favoriteItemIds = config.favoriteItemIds.filterNot { it == HomeItemId.web(uuid) },
        shortcuts = config.shortcuts.filterNot { it.uuid == uuid },
    )
}
