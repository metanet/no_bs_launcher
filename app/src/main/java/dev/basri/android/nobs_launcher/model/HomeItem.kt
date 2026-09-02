package dev.basri.android.nobs_launcher.model

import dev.basri.android.nobs_launcher.data.AppCandidate

sealed interface HomeItem {
    val id: String
    val label: String

    data class App(
        val candidate: AppCandidate,
    ) : HomeItem {
        override val id: String = HomeItemId.app(candidate.packageName)
        override val label: String = candidate.label
    }

    data class Web(
        val shortcut: WebShortcut,
    ) : HomeItem {
        override val id: String = shortcut.itemId
        override val label: String = shortcut.name
    }
}

object HomeItemId {
    private const val APP_PREFIX = "app:"
    private const val WEB_PREFIX = "web:"
    private val packagePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    private val uuidPattern = Regex(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
    )

    fun app(packageName: String): String = "$APP_PREFIX$packageName"

    fun web(uuid: String): String = "$WEB_PREFIX$uuid"

    fun appPackage(itemId: String): String? = itemId
        .takeIf { it.startsWith(APP_PREFIX) }
        ?.removePrefix(APP_PREFIX)
        ?.takeIf(packagePattern::matches)

    fun webUuid(itemId: String): String? = itemId
        .takeIf { it.startsWith(WEB_PREFIX) }
        ?.removePrefix(WEB_PREFIX)
        ?.takeIf(uuidPattern::matches)

    fun isValid(itemId: String): Boolean = appPackage(itemId) != null || webUuid(itemId) != null
}
