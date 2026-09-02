package dev.basri.android.nobs_launcher.data

import dev.basri.android.nobs_launcher.model.HomeItemId
import dev.basri.android.nobs_launcher.model.ShortcutInput
import dev.basri.android.nobs_launcher.model.WebShortcut
import dev.basri.android.nobs_launcher.model.WebShortcutPolicy

object WebShortcutCodec {
    private const val FIELD_SEPARATOR = '\t'

    fun encode(shortcuts: List<WebShortcut>): String = shortcuts
        .asSequence()
        .filter { HomeItemId.webUuid(it.itemId) != null }
        .distinctBy(WebShortcut::uuid)
        .joinToString("\n") { shortcut ->
            listOf(
                shortcut.uuid,
                shortcut.name,
                shortcut.url,
                shortcut.faviconFileName.orEmpty(),
            ).joinToString(FIELD_SEPARATOR.toString(), transform = ::escape)
        }

    fun decode(value: String): List<WebShortcut> = value
        .lineSequence()
        .mapNotNull(::decodeRecord)
        .distinctBy(WebShortcut::uuid)
        .toList()

    private fun decodeRecord(record: String): WebShortcut? {
        if (record.isEmpty()) return null
        val encodedFields = record.split(FIELD_SEPARATOR)
        if (encodedFields.size != FIELD_COUNT) return null
        val fields = encodedFields.map { decodeField(it) ?: return null }
        val uuid = fields[0]
        if (HomeItemId.webUuid(HomeItemId.web(uuid)) == null) return null
        if (WebShortcutPolicy.validate(fields[1], fields[2]) !is ShortcutInput.Valid) return null
        val favicon = fields[3].ifEmpty { null }
        if (favicon != null && !SAFE_FILE_NAME.matches(favicon)) return null
        return WebShortcut(
            uuid = uuid,
            name = fields[1],
            url = fields[2],
            faviconFileName = favicon,
        )
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(
                when (character) {
                    '%' -> "%25"
                    '\t' -> "%09"
                    '\n' -> "%0A"
                    '\r' -> "%0D"
                    else -> character
                },
            )
        }
    }

    private fun decodeField(value: String): String? = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            if (value[index] != '%') {
                append(value[index])
                index += 1
                continue
            }
            if (index + 2 >= value.length) return null
            when (value.substring(index, index + 3).uppercase()) {
                "%25" -> append('%')
                "%09" -> append('\t')
                "%0A" -> append('\n')
                "%0D" -> append('\r')
                else -> return null
            }
            index += 3
        }
    }

    private const val FIELD_COUNT = 4
    private val SAFE_FILE_NAME = Regex("[A-Za-z0-9._-]+")
}
