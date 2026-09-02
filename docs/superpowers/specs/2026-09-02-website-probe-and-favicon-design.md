# Website Reachability and Favicon Discovery Design

## Goal

When a user saves a web shortcut, No bullshit launcher must verify that the
configured HTTP(S) destination is reachable before changing persisted state.
For reachable HTML pages, the launcher must discover the icon declared by the
page rather than relying only on `/favicon.ico`.

## Existing limitation

The current favicon fetcher constructs only the site's origin-level
`/favicon.ico` URL. It never requests the configured page and therefore cannot
see `<link rel="icon">` or Apple touch icon declarations. Sites without a valid
raster image at `/favicon.ico` correctly fall back to the bundled globe even
when they declare a usable icon elsewhere.

The current editor also persists valid URL syntax immediately. DNS failures,
connection failures, TLS failures, timeouts, and HTTP errors are discovered
only later by the asynchronous favicon request and never prevent the shortcut
from being saved.

## Save workflow

1. Validate and normalize Name and Web address with the existing local policy.
2. Disable Save and editable fields, show `Checking website...`, and run all
   network work outside the main thread.
3. Send a bounded `GET` request to the normalized destination. Do not use
   `HEAD`, because otherwise reachable sites frequently reject or mishandle it.
4. Follow at most five redirects. Every initial and redirected URL must use
   HTTP or HTTPS.
5. Treat final `2xx`, `401`, and `403` responses as reachable. A `401` or `403`
   proves that the configured server responded even though it requires
   authentication or denies anonymous access.
6. Treat missing redirect locations, redirect loops beyond the limit, other
   `4xx` responses, `5xx` responses, DNS/connection/TLS errors, and timeouts as
   inaccessible.
7. On an inaccessible result, re-enable the editor, keep it open, and attach
   `Website is not accessible.` to the URL field. Existing shortcut metadata,
   favorite position, and favicon remain unchanged.
8. On a reachable result, atomically persist the new or edited shortcut. An
   edit retains its UUID and favorite position.
9. Dismiss the editor after metadata persistence succeeds. Favicon discovery
   failure does not fail an otherwise reachable shortcut.

Only one save attempt may be active for a dialog. Closing the dialog or
activity makes a late callback harmless. A failed persistence operation after
a successful probe shows the existing save-failure message.

## Network and response bounds

The launcher uses private OkHttp clients configured with no cookie jar, no
HTTP or proxy authenticator, no response cache, and no automatic redirects.
The probe uses four-second connect and read timeouts plus a hard twelve-second
call deadline across all manually followed redirects. This avoids Android
`HttpURLConnection`'s per-address timeout and VM-global cookie/authentication
behavior. Requests send a launcher-specific user agent.

The probe reads at most 256 KiB of a successful HTML response, which is
sufficient for `<head>` metadata without allowing an unbounded page allocation.
A successful response remains reachable if its body is absent or non-HTML. If
HTML exceeds the discovery limit, only its first 256 KiB are scanned so normal
`<head>` icon declarations remain discoverable without an unbounded read.

Redirect resolution uses the current response URL so relative redirect and
icon paths work correctly. Redirects to non-HTTP(S) schemes are rejected.
Because HTTP shortcuts are supported, the app permits cleartext traffic for
user-configured HTTP destinations and their declared HTTP icons; HTTPS remains
unchanged and no request is made until the user presses Save.

## Favicon discovery

For a reachable HTML response, scan `<link>` elements case-insensitively and
classify HTTP(S) URLs from `icon`/`shortcut icon` separately from Apple touch
icon declarations. This covers common icon markup without adding a large HTML
parser dependency. Resolve relative and protocol-relative `href` values against
the final page URL, not the originally entered URL.

Candidate order is:

1. Declared `rel=icon` raster candidates in document order.
2. Declared Apple touch icon candidates in document order.
3. The final page origin's `/favicon.ico` fallback.

Deduplicate URLs while preserving order. Fetch at most five declared candidates
plus the origin fallback, and retain the existing 256 KiB download, redirect, scheme,
dimension, pixel-count, sampled-decode, and 256px output limits. Store the first
candidate that Android can decode as a raster image. SVG and malformed images
are skipped safely; if no candidate works, persist no icon and display the
bundled globe.

The stored icon remains app-private and is attached only if the shortcut still
exists with the same URL. URL edits and shortcut removal retain the existing
atomic cleanup behavior.

## Component boundaries

- `WebsiteHttpClient` owns the bounded private OkHttp page request, redirects,
  reachability classification, final URL, and optional bounded HTML bytes.
- `FaviconDiscovery` is a pure parser that converts final page URL plus HTML
  into ordered, deduplicated icon candidates and appends the origin fallback.
- `FaviconHttpFetcher` uses the same per-client privacy policy to download one
  exact candidate URL within byte, redirect, scheme, and timeout limits.
- `FaviconRepository` tries candidates in order, decodes/stores the first valid
  raster icon, and keeps private-file limits and cleanup.
- `WebShortcutService` coordinates local validation, asynchronous probe,
  atomic persistence, favicon attachment, and stale-callback rejection.
- `WebShortcutsActivity` owns progress/error presentation and prevents duplicate
  Save actions while a probe is active.

These boundaries allow reachability, parsing, favicon storage, persistence,
and TV UI behavior to be tested independently.

## Privacy and portability

The launcher contacts only the user-configured page and icon URLs declared by
that page, plus the same origin's `/favicon.ico` fallback. It does not use a
third-party favicon service, send cookies, execute page JavaScript, create a
WebView, refresh icons in the background, or depend on Box R/Xiaomi APIs.

## Verification

Unit tests must cover:

- accepted `2xx`, `401`, and `403` responses;
- rejected `404`, other `4xx`, `5xx`, timeout, DNS/connection/TLS failure;
- relative redirects, unsafe redirects, and redirect limits;
- bounded HTML reads and non-HTML/oversized response behavior;
- icon link attribute order/casing, relative and protocol-relative URLs,
  `shortcut icon`, Apple touch icons, malformed URLs, deduplication, and root
  fallback;
- first-decodable candidate selection and preservation of bitmap bounds;
- no persistence on probe failure;
- atomic persistence and stale callback behavior on success and edit.

Connected tests must verify that Save shows a checking state, an unreachable
URL keeps the editor open with an error, a reachable URL persists and dismisses
the editor, and a discovered raster icon is stored, rendered, and deleted.

Downloaded favicons must be enlarged while preserving their aspect ratio. On
Home, every artwork type uses the same centered 16:9-height content band so a
square favicon cannot become taller than neighboring TV app banners; labels and
tile bounds remain aligned. Web shortcuts management keeps its existing square
icon viewport. The rendering fix applies to already-cached small favicons
without a file migration or a new download.

The final gate is debug/release lint, both unit suites, the complete connected
suite, debug/release builds, signed APK verification, guarded in-place install,
and a real Box R smoke test using one temporary reachable and one temporary
unreachable shortcut. Restore the device's observed initial power state and do
not change power, audio, or volume unless restoration requires it.
