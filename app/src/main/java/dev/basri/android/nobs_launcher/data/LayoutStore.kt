package dev.basri.android.nobs_launcher.data

import android.content.Context
import dev.basri.android.nobs_launcher.model.LauncherConfig

class LayoutStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): LauncherConfig = LauncherConfig(
        firstRunComplete = preferences.getBoolean(KEY_FIRST_RUN_COMPLETE, false),
        wifiLabel = preferences.getString(KEY_WIFI_LABEL, "").orEmpty(),
        locationLabel = preferences.getString(KEY_LOCATION_LABEL, "").orEmpty(),
        selectedPackages = LayoutCodec.decode(
            preferences.getString(KEY_SELECTED_PACKAGES, "").orEmpty(),
        ),
        welcomeText = preferences.getString(KEY_WELCOME_TEXT, "").orEmpty(),
        showLocation = preferences.getBoolean(KEY_SHOW_LOCATION, true),
        showVpnStatus = preferences.getBoolean(KEY_SHOW_VPN_STATUS, true),
        showSystemStats = preferences.getBoolean(KEY_SHOW_SYSTEM_STATS, true),
    )

    fun save(config: LauncherConfig): Boolean = preferences.edit()
        .putBoolean(KEY_FIRST_RUN_COMPLETE, config.firstRunComplete)
        .putString(KEY_WIFI_LABEL, config.wifiLabel.trim())
        .putString(KEY_LOCATION_LABEL, config.locationLabel.trim())
        .putString(KEY_SELECTED_PACKAGES, LayoutCodec.encode(config.selectedPackages))
        .putString(KEY_WELCOME_TEXT, config.welcomeText.trim())
        .putBoolean(KEY_SHOW_LOCATION, config.showLocation)
        .putBoolean(KEY_SHOW_VPN_STATUS, config.showVpnStatus)
        .putBoolean(KEY_SHOW_SYSTEM_STATS, config.showSystemStats)
        .commit()

    fun clear(): Boolean = preferences.edit().clear().commit()

    companion object {
        const val PREFERENCES_NAME = "nobs_launcher_layout"
        private const val KEY_FIRST_RUN_COMPLETE = "first_run_complete"
        private const val KEY_WIFI_LABEL = "wifi_label"
        private const val KEY_LOCATION_LABEL = "location_label"
        private const val KEY_SELECTED_PACKAGES = "selected_packages"
        private const val KEY_WELCOME_TEXT = "welcome_text"
        private const val KEY_SHOW_LOCATION = "show_location"
        private const val KEY_SHOW_VPN_STATUS = "show_vpn_status"
        private const val KEY_SHOW_SYSTEM_STATS = "show_system_stats"
    }
}
