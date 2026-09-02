package dev.basri.android.nobs_launcher.data

import java.net.URI
import java.util.Locale

object FaviconDiscovery {
    fun candidates(finalPageUrl: String, html: String?): List<String> {
        val pageUri = parseHttpUri(finalPageUrl) ?: return emptyList()
        val standard = linkedSetOf<String>()
        val apple = linkedSetOf<String>()

        html?.let { source ->
            scanLinkTags(source) linkTag@{ tag ->
                val attributes = parseAttributes(tag)
                val relTokens = attributes["rel"]
                    ?.trim()
                    ?.lowercase(Locale.ROOT)
                    ?.split(ASCII_WHITESPACE)
                    ?.filter(String::isNotEmpty)
                    .orEmpty()
                val href = attributes["href"]?.trim().orEmpty()
                if (href.isEmpty()) return@linkTag

                val target = when {
                    relTokens.any { it == "apple-touch-icon" || it == "apple-touch-icon-precomposed" } -> apple
                    "icon" in relTokens -> standard
                    else -> return@linkTag
                }
                resolveHttpUri(pageUri, href)?.let(target::add)
            }
        }

        val fallback = originFavicon(pageUri) ?: return emptyList()
        return buildList {
            (standard.asSequence() + apple.asSequence())
                .distinct()
                .take(MAX_DECLARED_CANDIDATES)
                .forEach(::add)
            if (fallback !in this) add(fallback)
        }
    }

    private fun scanLinkTags(html: String, onLinkTag: (String) -> Unit) {
        var cursor = 0
        while (cursor < html.length) {
            val tagStart = html.indexOf('<', cursor)
            if (tagStart < 0) return
            if (html.regionMatches(tagStart, COMMENT_START, 0, COMMENT_START.length)) {
                val commentEnd = html.indexOf(COMMENT_END, tagStart + COMMENT_START.length)
                if (commentEnd < 0) return
                cursor = commentEnd + COMMENT_END.length
                continue
            }

            val tag = parseTag(html, tagStart)
            if (tag == null) {
                cursor = tagStart + 1
                continue
            }
            val tagEnd = findTagEnd(html, tag.nameEnd) ?: return
            val hasNameDelimiter =
                tag.nameEnd < html.length && html[tag.nameEnd].isTagDelimiter()
            if (!tag.closing && hasNameDelimiter && tag.name in RAW_TEXT_TAGS) {
                cursor = findRawTextEnd(html, tagEnd + 1, tag.name) ?: return
                continue
            }
            if (!tag.closing && hasNameDelimiter && tag.name == PLAINTEXT_TAG_NAME) return
            if (!tag.closing && hasNameDelimiter && tag.name == LINK_TAG_NAME) {
                onLinkTag(html.substring(tagStart, tagEnd + 1))
            }
            cursor = tagEnd + 1
        }
    }

    private fun parseTag(html: String, tagStart: Int): Tag? {
        var cursor = tagStart + 1
        val closing = cursor < html.length && html[cursor] == '/'
        if (closing) cursor += 1
        if (cursor >= html.length || !html[cursor].isAsciiLetter()) return null
        val nameStart = cursor
        while (cursor < html.length && html[cursor].isTagNameCharacter()) cursor += 1
        return Tag(
            name = html.substring(nameStart, cursor).lowercase(Locale.ROOT),
            nameEnd = cursor,
            closing = closing,
        )
    }

    private fun findTagEnd(html: String, fromIndex: Int): Int? {
        var quote: Char? = null
        for (index in fromIndex until html.length) {
            val character = html[index]
            if (quote == null) {
                when (character) {
                    '\'', '"' -> quote = character
                    '>' -> return index
                }
            } else if (character == quote) {
                quote = null
            }
        }
        return null
    }

    private fun findRawTextEnd(html: String, fromIndex: Int, name: String): Int? {
        var cursor = fromIndex
        while (cursor < html.length) {
            val candidate = html.indexOf('<', cursor)
            if (candidate < 0) return null
            val nameStart = candidate + 2
            val nameEnd = nameStart + name.length
            if (
                candidate + 1 < html.length &&
                html[candidate + 1] == '/' &&
                nameEnd <= html.length &&
                html.regionMatches(nameStart, name, 0, name.length, ignoreCase = true) &&
                (nameEnd == html.length || html[nameEnd].isTagDelimiter())
            ) {
                val tagEnd = findTagEnd(html, nameEnd) ?: return null
                return tagEnd + 1
            }
            cursor = candidate + 1
        }
        return null
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    private fun Char.isTagNameCharacter(): Boolean =
        isAsciiLetter() || this in '0'..'9' || this == '-' || this == ':'

    private fun Char.isTagDelimiter(): Boolean = this == '>' || this == '/' || this.isHtmlSpace()

    private fun Char.isHtmlSpace(): Boolean =
        this == ' ' || this == '\t' || this == '\n' || this == '\u000C' || this == '\r'

    private fun parseAttributes(tag: String): Map<String, String> {
        val attributes = linkedMapOf<String, String>()
        var cursor = 1
        while (cursor < tag.length && !tag[cursor].isTagDelimiter()) cursor += 1

        while (cursor < tag.length) {
            while (cursor < tag.length && tag[cursor].isHtmlSpace()) cursor += 1
            if (cursor >= tag.length || tag[cursor] == '>') break
            if (tag[cursor] == '/') {
                cursor += 1
                continue
            }

            val nameStart = cursor
            while (cursor < tag.length && !tag[cursor].isAttributeNameDelimiter()) cursor += 1
            if (cursor == nameStart) {
                cursor += 1
                continue
            }
            val name = tag.substring(nameStart, cursor).lowercase(Locale.ROOT)
            while (cursor < tag.length && tag[cursor].isHtmlSpace()) cursor += 1

            var value = ""
            if (cursor < tag.length && tag[cursor] == '=') {
                cursor += 1
                while (cursor < tag.length && tag[cursor].isHtmlSpace()) cursor += 1
                if (cursor < tag.length && (tag[cursor] == '\'' || tag[cursor] == '"')) {
                    val quote = tag[cursor]
                    cursor += 1
                    val valueStart = cursor
                    while (cursor < tag.length && tag[cursor] != quote) cursor += 1
                    value = tag.substring(valueStart, cursor)
                    if (cursor < tag.length) cursor += 1
                } else {
                    val valueStart = cursor
                    while (
                        cursor < tag.length &&
                        !tag[cursor].isHtmlSpace() &&
                        tag[cursor] != '>'
                    ) {
                        cursor += 1
                    }
                    value = tag.substring(valueStart, cursor)
                }
            }
            if (name !in attributes) attributes[name] = decodeEntities(value)
        }
        return attributes
    }

    private fun Char.isAttributeNameDelimiter(): Boolean =
        isHtmlSpace() || this == '=' || this == '/' || this == '>'

    private fun decodeEntities(value: String): String = ENTITY.replace(value) { match ->
        val entity = match.groupValues[1]
        when {
            entity.startsWith("#x", ignoreCase = true) -> codePoint(entity.drop(2), 16, match.value)
            entity.startsWith('#') -> codePoint(entity.drop(1), 10, match.value)
            else -> when (entity.lowercase(Locale.ROOT)) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> match.value
            }
        }
    }

    private fun codePoint(value: String, radix: Int, fallback: String): String = runCatching {
        String(Character.toChars(value.toInt(radix)))
    }.getOrDefault(fallback)

    private fun resolveHttpUri(base: URI, href: String): String? = runCatching {
        base.resolve(href)
    }.getOrNull()
        ?.takeIf(::isHttpUri)
        ?.takeIf { resolved -> sameOrigin(base, resolved) }
        ?.toASCIIString()

    private fun parseHttpUri(value: String): URI? = runCatching { URI(value) }
        .getOrNull()
        ?.takeIf(::isHttpUri)

    private fun isHttpUri(uri: URI): Boolean =
        uri.scheme?.lowercase(Locale.ROOT) in ALLOWED_SCHEMES && !uri.host.isNullOrBlank()

    private fun sameOrigin(first: URI, second: URI): Boolean =
        first.scheme.equals(second.scheme, ignoreCase = true) &&
            first.host.equals(second.host, ignoreCase = true) &&
            effectivePort(first) == effectivePort(second)

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }

    private fun originFavicon(pageUri: URI): String? = runCatching {
        URI(pageUri.scheme, null, pageUri.host, pageUri.port, "/favicon.ico", null, null)
            .toASCIIString()
    }.getOrNull()

    private const val MAX_DECLARED_CANDIDATES = 5
    private const val COMMENT_START = "<!--"
    private const val COMMENT_END = "-->"
    private const val LINK_TAG_NAME = "link"
    private const val PLAINTEXT_TAG_NAME = "plaintext"
    private val ALLOWED_SCHEMES = setOf("http", "https")
    private val RAW_TEXT_TAGS = setOf(
        "script",
        "style",
        "title",
        "textarea",
        "xmp",
        "iframe",
        "noembed",
        "noframes",
    )
    private val ASCII_WHITESPACE = Regex("[\\t\\n\\f\\r ]+")
    private val ENTITY = Regex("&(#x[0-9a-f]+|#[0-9]+|amp|lt|gt|quot|apos);", RegexOption.IGNORE_CASE)

    private data class Tag(
        val name: String,
        val nameEnd: Int,
        val closing: Boolean,
    )
}
