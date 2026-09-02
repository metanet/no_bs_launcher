package dev.basri.android.nobs_launcher.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebShortcutPolicyTest {
    @Test
    fun missingSchemeDefaultsToHttpsAndInputIsTrimmed() {
        assertEquals(
            ShortcutInput.Valid(
                name = "Basri's TV",
                url = "https://example.com/path?view=tv",
            ),
            WebShortcutPolicy.validate(
                name = "  Basri's TV  ",
                url = "  example.com/path?view=tv  ",
            ),
        )
    }

    @Test
    fun explicitHttpAndHttpsSchemesAreAcceptedAndLowercased() {
        assertEquals(
            ShortcutInput.Valid("HTTP", "http://example.com/a"),
            WebShortcutPolicy.validate("HTTP", "HTTP://example.com/a"),
        )
        assertEquals(
            ShortcutInput.Valid("HTTPS", "https://example.com/b"),
            WebShortcutPolicy.validate("HTTPS", "HTTPS://example.com/b"),
        )
    }

    @Test
    fun hostAndPortWithoutSchemeDefaultsToHttps() {
        assertEquals(
            ShortcutInput.Valid("Local", "https://example.com:8443/status"),
            WebShortcutPolicy.validate("Local", "example.com:8443/status"),
        )
    }

    @Test
    fun unsafeSchemesAndMissingHostsAreRejected() {
        val rejected = listOf(
            "file:///tmp/x",
            "content://provider/item",
            "intent://example.com/#Intent;end",
            "javascript:alert(1)",
            "custom://example.com/path",
            "https:///missing-host",
            "/relative/path",
            "https://user:secret@example.com/private",
        )

        rejected.forEach { url ->
            assertTrue(
                "Expected URL to be rejected: $url",
                WebShortcutPolicy.validate("Site", url) is ShortcutInput.Invalid,
            )
        }
    }

    @Test
    fun blankAndOversizedFieldsAreRejected() {
        assertEquals(
            ShortcutInput.Invalid(ShortcutField.NAME, ShortcutError.REQUIRED),
            WebShortcutPolicy.validate("  ", "example.com"),
        )
        assertEquals(
            ShortcutInput.Invalid(ShortcutField.NAME, ShortcutError.TOO_LONG),
            WebShortcutPolicy.validate("x".repeat(81), "example.com"),
        )
        assertEquals(
            ShortcutInput.Invalid(ShortcutField.URL, ShortcutError.REQUIRED),
            WebShortcutPolicy.validate("Site", "  "),
        )
        assertEquals(
            ShortcutInput.Invalid(ShortcutField.URL, ShortcutError.TOO_LONG),
            WebShortcutPolicy.validate("Site", "x".repeat(2049)),
        )
    }

    @Test
    fun appAndWebIdsAreTypeSeparatedAndValidated() {
        assertEquals("app:com.example.tv", HomeItemId.app("com.example.tv"))
        assertEquals("web:123e4567-e89b-12d3-a456-426614174000", HomeItemId.web(UUID))
        assertEquals("com.example.tv", HomeItemId.appPackage("app:com.example.tv"))
        assertEquals(UUID, HomeItemId.webUuid("web:$UUID"))
        assertEquals(null, HomeItemId.appPackage("web:$UUID"))
        assertEquals(null, HomeItemId.webUuid("app:com.example.tv"))
        assertEquals(false, HomeItemId.isValid("not-an-item"))
    }

    private companion object {
        const val UUID = "123e4567-e89b-12d3-a456-426614174000"
    }
}
