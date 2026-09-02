package dev.basri.android.nobs_launcher.model

import java.net.URI
import java.util.Locale

data class WebShortcut(
    val uuid: String,
    val name: String,
    val url: String,
    val faviconFileName: String? = null,
) {
    val itemId: String
        get() = HomeItemId.web(uuid)
}

enum class ShortcutField {
    NAME,
    URL,
}

enum class ShortcutError {
    REQUIRED,
    TOO_LONG,
    INVALID,
}

sealed interface ShortcutInput {
    data class Valid(
        val name: String,
        val url: String,
    ) : ShortcutInput

    data class Invalid(
        val field: ShortcutField,
        val error: ShortcutError,
    ) : ShortcutInput
}

object WebShortcutPolicy {
    const val MAX_NAME_LENGTH = 80
    const val MAX_URL_LENGTH = 2_048

    private val explicitSchemePattern = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    private val schemeLikePattern = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val hostAndPortPattern = Regex(
        "^(?:localhost|[^/?#:]+\\.[^/?#:]+|\\[[^]]+]):[0-9]+(?:[/?#].*)?$",
    )

    fun validate(name: String, url: String): ShortcutInput {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            return ShortcutInput.Invalid(ShortcutField.NAME, ShortcutError.REQUIRED)
        }
        if (normalizedName.length > MAX_NAME_LENGTH) {
            return ShortcutInput.Invalid(ShortcutField.NAME, ShortcutError.TOO_LONG)
        }

        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) {
            return ShortcutInput.Invalid(ShortcutField.URL, ShortcutError.REQUIRED)
        }
        if (trimmedUrl.length > MAX_URL_LENGTH) {
            return ShortcutInput.Invalid(ShortcutField.URL, ShortcutError.TOO_LONG)
        }

        val candidate = when {
            explicitSchemePattern.containsMatchIn(trimmedUrl) -> trimmedUrl
            schemeLikePattern.containsMatchIn(trimmedUrl) &&
                !hostAndPortPattern.matches(trimmedUrl) -> {
                return ShortcutInput.Invalid(ShortcutField.URL, ShortcutError.INVALID)
            }
            else -> "https://$trimmedUrl"
        }
        val uri = runCatching { URI(candidate) }.getOrNull()
            ?: return ShortcutInput.Invalid(ShortcutField.URL, ShortcutError.INVALID)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (!uri.isAbsolute || scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            return ShortcutInput.Invalid(ShortcutField.URL, ShortcutError.INVALID)
        }
        val normalizedUrl = scheme + candidate.substring(uri.scheme.length)
        if (normalizedUrl.length > MAX_URL_LENGTH) {
            return ShortcutInput.Invalid(ShortcutField.URL, ShortcutError.TOO_LONG)
        }
        return ShortcutInput.Valid(normalizedName, normalizedUrl)
    }
}
