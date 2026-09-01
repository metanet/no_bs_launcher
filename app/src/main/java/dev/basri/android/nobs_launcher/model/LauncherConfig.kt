package dev.basri.android.nobs_launcher.model

data class LauncherConfig(
    val firstRunComplete: Boolean,
    val wifiLabel: String,
    val locationLabel: String,
    val selectedPackages: List<String>,
    val welcomeText: String = "",
    val showLocation: Boolean = true,
    val showVpnStatus: Boolean = true,
    val showSystemStats: Boolean = true,
) {
    companion object {
        val DEFAULT = LauncherConfig(
            firstRunComplete = false,
            wifiLabel = "",
            locationLabel = "",
            selectedPackages = emptyList(),
        )
    }
}

object LauncherConfigPolicy {
    fun setVisible(
        config: LauncherConfig,
        packageName: String,
        visible: Boolean,
    ): LauncherConfig {
        val ordered = config.selectedPackages.distinct().toMutableList()
        if (visible && packageName !in ordered) {
            ordered += packageName
        }
        if (!visible) {
            ordered.removeAll { it == packageName }
        }
        return config.copy(selectedPackages = ordered)
    }

    fun move(packages: List<String>, fromIndex: Int, toIndex: Int): List<String> {
        if (fromIndex !in packages.indices || toIndex !in packages.indices || fromIndex == toIndex) {
            return packages.toList()
        }
        return packages.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }

    fun normalize(
        config: LauncherConfig,
        installedPackages: Set<String>,
    ): LauncherConfig = config.copy(
        selectedPackages = config.selectedPackages
            .distinct()
            .filter(installedPackages::contains),
    )
}
