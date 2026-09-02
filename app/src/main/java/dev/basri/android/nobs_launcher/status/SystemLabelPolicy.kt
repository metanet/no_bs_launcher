package dev.basri.android.nobs_launcher.status

object SystemLabelPolicy {
    fun normalizeWifiSsid(value: String?): String? {
        val trimmed = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (trimmed.equals(UNKNOWN_SSID, ignoreCase = true)) return null
        val unquoted = if (
            trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"'
        ) {
            trimmed.substring(1, trimmed.lastIndex).trim()
        } else {
            trimmed
        }
        return unquoted.takeIf(String::isNotEmpty)
    }

    fun locationFromTimeZone(timeZoneId: String?): String? = timeZoneId
        ?.trim()
        ?.substringAfterLast('/')
        ?.replace('_', ' ')
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private const val UNKNOWN_SSID = "<unknown ssid>"
}
