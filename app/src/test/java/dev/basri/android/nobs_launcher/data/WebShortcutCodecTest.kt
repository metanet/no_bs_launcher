package dev.basri.android.nobs_launcher.data

import dev.basri.android.nobs_launcher.model.WebShortcut
import org.junit.Assert.assertEquals
import org.junit.Test

class WebShortcutCodecTest {
    @Test
    fun shortcutCodecRoundTripsTabsNewlinesPercentUnicodeAndWhitespace() {
        val shortcuts = listOf(
            WebShortcut(
                uuid = UUID_ONE,
                name = " Basri\tTV\n% Türkçe ",
                url = "https://example.com/a?x=one%20two&y=✓",
                faviconFileName = "$UUID_ONE-icon.png",
            ),
            WebShortcut(
                uuid = UUID_TWO,
                name = "Second",
                url = "http://example.org",
            ),
        )

        assertEquals(shortcuts, WebShortcutCodec.decode(WebShortcutCodec.encode(shortcuts)))
    }

    @Test
    fun malformedRecordsAreSkippedWithoutLosingValidRecords() {
        val valid = WebShortcut(UUID_ONE, "Valid", "https://example.com")
        val encoded = listOf(
            "too\tfew\tfields",
            "bad%QZ\tname\thttps://example.com\t",
            WebShortcutCodec.encode(listOf(valid)),
            "not-a-uuid\tName\thttps://example.com\ticon.png",
            "$UUID_TWO\tBlank URL\t\t",
            "$UUID_THREE\tWrong scheme\tfile:///tmp/private\t",
        ).joinToString("\n")

        assertEquals(listOf(valid), WebShortcutCodec.decode(encoded))
    }

    @Test
    fun itemIdCodecKeepsOnlyDistinctStrictIds() {
        val encoded = HomeItemIdCodec.encode(
            listOf(
                "app:com.example.tv",
                "web:$UUID_ONE",
                "app:com.example.tv",
                "invalid",
            ),
        )

        assertEquals(
            listOf("app:com.example.tv", "web:$UUID_ONE"),
            HomeItemIdCodec.decode(encoded),
        )
    }

    private companion object {
        const val UUID_ONE = "123e4567-e89b-12d3-a456-426614174000"
        const val UUID_TWO = "223e4567-e89b-12d3-a456-426614174000"
        const val UUID_THREE = "323e4567-e89b-12d3-a456-426614174000"
    }
}
