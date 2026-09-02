package dev.basri.android.nobs_launcher.data

import android.content.Context
import dev.basri.android.nobs_launcher.model.HomeItemId
import dev.basri.android.nobs_launcher.model.LauncherConfig

interface LauncherConfigStore {
    fun load(): LauncherConfig

    fun save(config: LauncherConfig): Boolean

    fun update(transform: (LauncherConfig) -> LauncherConfig?): Boolean {
        val current = load()
        val updated = transform(current) ?: return false
        return updated == current || save(updated)
    }
}

class LayoutStore(context: Context) : LauncherConfigStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(): LauncherConfig = synchronized(PROCESS_LOCK) {
        loadUnlocked()
    }

    override fun save(config: LauncherConfig): Boolean = synchronized(PROCESS_LOCK) {
        saveUnlocked(config)
    }

    override fun update(transform: (LauncherConfig) -> LauncherConfig?): Boolean =
        synchronized(PROCESS_LOCK) {
            val current = loadUnlocked()
            val updated = transform(current) ?: return@synchronized false
            updated == current || saveUnlocked(updated)
        }

    private fun loadUnlocked(): LauncherConfig {
        val hasUnifiedFavorites = preferences.contains(KEY_FAVORITE_ITEM_IDS)
        val favoriteItemIds = if (hasUnifiedFavorites) {
            HomeItemIdCodec.decode(preferences.getString(KEY_FAVORITE_ITEM_IDS, "").orEmpty())
        } else {
            LayoutCodec.decode(preferences.getString(KEY_SELECTED_PACKAGES, "").orEmpty())
                .map(HomeItemId::app)
        }
        val config = LauncherConfig(
            firstRunComplete = preferences.getBoolean(KEY_FIRST_RUN_COMPLETE, false),
            favoriteItemIds = favoriteItemIds,
            shortcuts = WebShortcutCodec.decode(
                preferences.getString(KEY_WEB_SHORTCUTS, "").orEmpty(),
            ),
            welcomeText = preferences.getString(KEY_WELCOME_TEXT, "").orEmpty(),
            showWifiName = preferences.getBoolean(KEY_SHOW_WIFI_NAME, true),
            showLocation = preferences.getBoolean(KEY_SHOW_LOCATION, true),
            showVpnStatus = preferences.getBoolean(KEY_SHOW_VPN_STATUS, true),
            showSystemStats = preferences.getBoolean(KEY_SHOW_SYSTEM_STATS, true),
        )
        if (!hasUnifiedFavorites) {
            saveUnlocked(config)
        }
        return config
    }

    private fun saveUnlocked(config: LauncherConfig): Boolean = preferences.edit()
        .putBoolean(KEY_FIRST_RUN_COMPLETE, config.firstRunComplete)
        .putString(KEY_FAVORITE_ITEM_IDS, HomeItemIdCodec.encode(config.favoriteItemIds))
        .putString(KEY_WEB_SHORTCUTS, WebShortcutCodec.encode(config.shortcuts))
        .putString(KEY_WELCOME_TEXT, config.welcomeText.trim())
        .putBoolean(KEY_SHOW_WIFI_NAME, config.showWifiName)
        .putBoolean(KEY_SHOW_LOCATION, config.showLocation)
        .putBoolean(KEY_SHOW_VPN_STATUS, config.showVpnStatus)
        .putBoolean(KEY_SHOW_SYSTEM_STATS, config.showSystemStats)
        .remove(KEY_WIFI_LABEL)
        .remove(KEY_LOCATION_LABEL)
        .remove(KEY_SELECTED_PACKAGES)
        .commit()

    fun hasRequestedWifiPermission(): Boolean = synchronized(PROCESS_LOCK) {
        preferences.getBoolean(KEY_WIFI_PERMISSION_REQUESTED, false)
    }

    fun markWifiPermissionRequested(): Boolean = synchronized(PROCESS_LOCK) {
        preferences.edit().putBoolean(KEY_WIFI_PERMISSION_REQUESTED, true).commit()
    }

    fun clear(): Boolean = synchronized(PROCESS_LOCK) {
        preferences.edit().clear().commit()
    }

    companion object {
        private val PROCESS_LOCK = Any()
        const val PREFERENCES_NAME = "nobs_launcher_layout"
        internal const val KEY_FAVORITE_ITEM_IDS = "favorite_item_ids"
        internal const val KEY_WEB_SHORTCUTS = "web_shortcuts"
        internal const val KEY_SELECTED_PACKAGES = "selected_packages"
        private const val KEY_FIRST_RUN_COMPLETE = "first_run_complete"
        private const val KEY_WIFI_LABEL = "wifi_label"
        private const val KEY_LOCATION_LABEL = "location_label"
        private const val KEY_WELCOME_TEXT = "welcome_text"
        private const val KEY_SHOW_WIFI_NAME = "show_wifi_name"
        private const val KEY_SHOW_LOCATION = "show_location"
        private const val KEY_SHOW_VPN_STATUS = "show_vpn_status"
        private const val KEY_SHOW_SYSTEM_STATS = "show_system_stats"
        private const val KEY_WIFI_PERMISSION_REQUESTED = "wifi_permission_requested"
    }
}
