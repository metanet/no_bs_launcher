package dev.basri.android.nobs_launcher.data

object LayoutCodec {
    private val packagePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

    fun encode(packages: List<String>): String = packages
        .distinct()
        .joinToString("\n")

    fun decode(value: String): List<String> = value
        .lineSequence()
        .map(String::trim)
        .filter(packagePattern::matches)
        .distinct()
        .toList()
}
