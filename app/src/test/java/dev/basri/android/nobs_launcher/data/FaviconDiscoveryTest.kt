package dev.basri.android.nobs_launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaviconDiscoveryTest {
    @Test
    fun parsesLinkAttributesCaseInsensitivelyAndPrioritizesStandardIcons() {
        val html = """
            <html><head>
              <LINK HREF='/apple.png' REL='apple-touch-icon'>
              <link sizes="32x32" href="icons/first.png" rel="ICON">
              <link rel='Shortcut Icon' href='//cdn.example.net/shortcut.ico'>
              <link rel="apple-touch-icon-precomposed" href="/apple-precomposed.png">
            </head></html>
        """.trimIndent()

        assertEquals(
            listOf(
                "https://example.com/news/icons/first.png",
                "https://cdn.example.net/shortcut.ico",
                "https://example.com/apple.png",
                "https://example.com/apple-precomposed.png",
                "https://example.com/favicon.ico",
            ),
            FaviconDiscovery.candidates("https://example.com/news/page.html", html),
        )
    }

    @Test
    fun supportsUnquotedAttributesAndDecodesCommonNamedAndNumericEntities() {
        val html = """
            <link href='/icon.png?light=1&amp;size=32' rel=icon>
            <link rel=icon href='icons/&#x69;con-&#50;.png'>
            <link rel="apple-touch-icon" href="/touch&#46;png">
        """.trimIndent()

        assertEquals(
            listOf(
                "https://example.com/icon.png?light=1&size=32",
                "https://example.com/path/icons/icon-2.png",
                "https://example.com/touch.png",
                "https://example.com/favicon.ico",
            ),
            FaviconDiscovery.candidates("https://example.com/path/index.html", html),
        )
    }

    @Test
    fun ignoresNonLinkTagsMalformedAndNonHttpLinks() {
        val html = """
            <meta rel="icon" href="/meta.png">
            <link rel="stylesheet" href="/style.css">
            <link rel="icon" href="javascript:alert(1)">
            <link rel="icon" href="file:///tmp/icon.png">
            <link rel="icon" href="http://[broken">
            <link rel="icon">
            <link rel="icon" href="https://assets.example/icon.png">
        """.trimIndent()

        assertEquals(
            listOf(
                "https://assets.example/icon.png",
                "https://example.com/favicon.ico",
            ),
            FaviconDiscovery.candidates("https://example.com/page", html),
        )
    }

    @Test
    fun deduplicatesAndLimitsDeclaredCandidatesBeforeAppendingFallback() {
        val html = """
            <link rel="apple-touch-icon" href="/apple.png">
            <link rel="icon" href="/one.png">
            <link rel="icon" href="/two.png">
            <link rel="icon" href="/one.png">
            <link rel="icon" href="/three.png">
            <link rel="shortcut icon" href="/four.png">
            <link rel="icon" href="/five.png">
            <link rel="icon" href="/six.png">
        """.trimIndent()

        assertEquals(
            listOf(
                "https://example.com:8443/one.png",
                "https://example.com:8443/two.png",
                "https://example.com:8443/three.png",
                "https://example.com:8443/four.png",
                "https://example.com:8443/five.png",
                "https://example.com:8443/favicon.ico",
            ),
            FaviconDiscovery.candidates("https://example.com:8443/page", html),
        )
    }

    @Test
    fun deduplicatesAcrossIconCategoriesBeforeApplyingTheGlobalLimit() {
        val html = """
            <link rel="apple-touch-icon" href="/shared.png">
            <link rel="icon" href="/shared.png">
            <link rel="icon" href="/two.png">
            <link rel="icon" href="/three.png">
            <link rel="icon" href="/four.png">
            <link rel="apple-touch-icon" href="/five.png">
        """.trimIndent()

        assertEquals(
            listOf(
                "https://example.com/shared.png",
                "https://example.com/two.png",
                "https://example.com/three.png",
                "https://example.com/four.png",
                "https://example.com/five.png",
                "https://example.com/favicon.ico",
            ),
            FaviconDiscovery.candidates("https://example.com/page", html),
        )
    }

    @Test
    fun usesFinalPageOriginForFallbackAndHandlesMissingOrInvalidHtml() {
        assertEquals(
            listOf("http://example.com:8080/favicon.ico"),
            FaviconDiscovery.candidates("http://example.com:8080/deep/page", null),
        )
        assertEquals(
            listOf("https://example.com/favicon.ico"),
            FaviconDiscovery.candidates("https://example.com/page", "<link rel='icon' href=''>"),
        )
        assertEquals(emptyList<String>(), FaviconDiscovery.candidates("file:///tmp/page", null))
        assertEquals(emptyList<String>(), FaviconDiscovery.candidates("not a URL", ""))
    }

    @Test
    fun scansOnlyLinkTags() {
        val html = """
            <!-- <link rel="icon" href="/commented.png"> -->
            <script>const fake = '<link rel="icon" href="/script.png">';</script>
            <link-preview rel="icon" href="/custom-element.png">
            <link_preview rel="icon" href="/underscore-element.png">
            <link.preview rel="icon" href="/dotted-element.png">
            <link rel="icon" href="/real.png">
        """.trimIndent()

        assertEquals(
            listOf("https://example.com/real.png", "https://example.com/favicon.ico"),
            FaviconDiscovery.candidates("https://example.com/page", html),
        )
    }

    @Test
    fun ignoresRawTextAdversariesAndTreatsTruncatedRegionsAsTheRemainder() {
        val prefix = "<link rel='icon' href='/before.png'>"
        val fakeTail = "<link rel='icon' href='/fake.png'>"
        val truncatedRegions = listOf(
            "<!-- $fakeTail",
            "<script data-comparison='a > b'>$fakeTail",
            "<style media='screen > print'>$fakeTail",
        )

        truncatedRegions.forEach { truncated ->
            assertEquals(
                listOf(
                    "https://example.com/before.png",
                    "https://example.com/favicon.ico",
                ),
                FaviconDiscovery.candidates("https://example.com/page", prefix + truncated),
            )
        }

        val closedRawText = """
            <script>const fake = '<link rel="icon" href="/script.png">';</script-not>
            still raw text <link rel="icon" href="/also-script.png"></script>
            <style>.x::after { content: '<link rel="icon" href="/style.png">'; }</style>
            <link rel="icon" href="/after.png">
        """.trimIndent()
        assertEquals(
            listOf("https://example.com/after.png", "https://example.com/favicon.ico"),
            FaviconDiscovery.candidates("https://example.com/page", closedRawText),
        )
    }

    @Test
    fun quotedGreaterThanDoesNotTerminateTheLinkTag() {
        assertEquals(
            listOf("https://example.com/icon.png", "https://example.com/favicon.ico"),
            FaviconDiscovery.candidates(
                "https://example.com/page",
                "<link data-comparison='a > b' href='/icon.png' rel='icon'>",
            ),
        )
    }

    @Test
    fun duplicateAttributesUseTheFirstValueLikeHtml() {
        val html = """
            <link rel="icon" href="/first.png" href="/second.png">
            <link rel="stylesheet" rel="icon" href="/must-not-appear.png">
        """.trimIndent()

        assertEquals(
            listOf("https://example.com/first.png", "https://example.com/favicon.ico"),
            FaviconDiscovery.candidates("https://example.com/page", html),
        )
    }

    @Test
    fun manuallyScansBooleanQuotedAndUnquotedAttributesWithFirstWinsSemantics() {
        val html = """
            <link rel href=/boolean-must-not-appear.png rel=icon>
            <link REL = ICON HREF = /icon.png?size=32&amp;theme=dark>
        """.trimIndent()

        assertEquals(
            listOf(
                "https://example.com/icon.png?size=32&theme=dark",
                "https://example.com/favicon.ico",
            ),
            FaviconDiscovery.candidates("https://example.com/page", html),
        )
    }

    @Test
    fun longMalformedAttributeRunCompletesWithinALinearTimeBudget() {
        val html = "<link ${"attribute".repeat(5_000)}>"
        val startedAt = System.nanoTime()

        val candidates = FaviconDiscovery.candidates("https://example.com/page", html)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L

        assertEquals(listOf("https://example.com/favicon.ico"), candidates)
        assertTrue("attribute scan took ${elapsedMillis}ms", elapsedMillis < 2_000L)
    }

    @Test
    fun suppressesLiteralMarkupInHtmlTextContainers() {
        val fakeLink = "<link rel='icon' href='/fake.png'>"
        val closedContainers = listOf(
            "title",
            "textarea",
            "xmp",
            "iframe",
            "noembed",
            "noframes",
        ).joinToString(separator = "") { tag -> "<$tag>$fakeLink</$tag>" }

        assertEquals(
            listOf("https://example.com/real.png", "https://example.com/favicon.ico"),
            FaviconDiscovery.candidates(
                "https://example.com/page",
                "$closedContainers<link rel='icon' href='/real.png'>",
            ),
        )
        assertEquals(
            listOf("https://example.com/before.png", "https://example.com/favicon.ico"),
            FaviconDiscovery.candidates(
                "https://example.com/page",
                "<link rel='icon' href='/before.png'><plaintext>$fakeLink</plaintext>" +
                    "<link rel='icon' href='/after.png'>",
            ),
        )
    }
}
