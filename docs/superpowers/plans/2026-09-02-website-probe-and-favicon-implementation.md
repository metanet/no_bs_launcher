# Website Probe and Favicon Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reject unreachable web shortcuts at Save time and discover each reachable page's declared favicon before falling back to `/favicon.ico`.

**Architecture:** A synchronous, bounded `WebsiteHttpClient` returns a final URL and optional HTML prefix; `WebShortcutService` runs it on a private executor, persists only reachable inputs, and passes pure-parser icon candidates to `FaviconRepository`. The activity owns progress/error UI and cancels pending persistence when its dialog closes. Network, parsing, persistence, bitmap storage, and UI are independently testable.

**Tech Stack:** Kotlin/JVM 17, OkHttp 5.4.0, Android bitmap APIs, SharedPreferences, JUnit 4, Espresso, Gradle Android plugin.

**Source-control constraint:** Basri authorized plan execution but did not authorize a new commit. Complete and verify the work, leave it uncommitted, and report the diff for approval.

---

### Task 1: Bounded website reachability client

**Files:**
- Create: `app/src/main/java/dev/basri/android/nobs_launcher/data/WebsiteHttpClient.kt`
- Create: `app/src/test/java/dev/basri/android/nobs_launcher/data/WebsiteHttpClientTest.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [x] **Step 1: Write failing response-classification tests**

Define the intended result boundary in tests:

```kotlin
sealed interface WebsiteProbeResult {
    data class Reachable(val finalUrl: String, val html: String?) : WebsiteProbeResult
    data object Inaccessible : WebsiteProbeResult
}

fun interface WebsiteProbeGateway {
    fun probe(url: String): WebsiteProbeResult
}
```

Use an injected OkHttp `Call.Factory` to assert that `200`, `204`, `401`, and
`403` are reachable; `404`, `429`, and `500` are inaccessible; relative HTTPS
redirects resolve against the current URL; missing locations, more than five
redirects, and non-HTTP(S) redirects fail; and every response body is closed.

- [x] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests '*WebsiteHttpClientTest'
```

Expected: test compilation fails because `WebsiteHttpClient` and its result
types do not exist.

- [x] **Step 3: Implement bounded GET probing with a private client**

Add OkHttp 5.4.0 to the version catalog and app runtime. Implement
`WebsiteHttpClient` with constructor-injected `Call.Factory` and monotonic clock.
The default `OkHttpClient` explicitly uses `CookieJar.NO_COOKIES`,
`Authenticator.NONE` for HTTP and proxy auth, no cache, no automatic HTTP/SSL
redirects, disabled connection-failure retries, four-second connect/read
timeouts, and a twelve-second call timeout. Before each manually followed
redirect, set the `Call.timeout()` to the remaining overall budget so all calls
share one hard deadline. Send an explicit launcher user agent and HTML Accept.

Follow at most five HTTP(S) redirects and return `Inaccessible` for all rejected
statuses and caught transport/timeout failures. Wrap every `Response` in `use`
so success, redirect, authentication, non-HTML, and error bodies always close.

For `text/html` and `application/xhtml+xml` `2xx` responses, read no more than
256 KiB and decode the prefix as UTF-8. Do not read authentication error bodies.

- [x] **Step 4: Add bounds, timeout, and header tests; verify GREEN**

Test declared and streamed bodies larger than 256 KiB, non-HTML content,
request method, Accept/User-Agent headers, explicit no-cookie/no-auth client
configuration, four-second phase timeouts, the shared redirect deadline, and a
real stalling loopback socket that returns within a short configured call
timeout.

Run:

```bash
./gradlew testDebugUnitTest --tests '*WebsiteHttpClientTest'
```

Expected: all website client tests pass.

### Task 2: Declared favicon discovery and candidate fetching

**Files:**
- Create: `app/src/main/java/dev/basri/android/nobs_launcher/data/FaviconDiscovery.kt`
- Create: `app/src/test/java/dev/basri/android/nobs_launcher/data/FaviconDiscoveryTest.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/data/FaviconHttpFetcher.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/data/FaviconRepository.kt`
- Modify: `app/src/test/java/dev/basri/android/nobs_launcher/data/FaviconHttpFetcherTest.kt`
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/FaviconRepositoryTest.kt`

- [x] **Step 1: Write failing pure discovery tests**

Specify this API:

```kotlin
object FaviconDiscovery {
    fun candidates(finalPageUrl: String, html: String?): List<String>
}
```

Assert case-insensitive/link-attribute-order parsing; `icon`, `shortcut icon`,
and Apple touch categorization; relative, root-relative, and protocol-relative
resolution against the final page; rejection of malformed/non-HTTP(S) links;
deduplication; five-declared-candidate limit; and one final origin
`/favicon.ico` fallback.

- [x] **Step 2: Run discovery tests and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests '*FaviconDiscoveryTest'
```

Expected: compilation fails because `FaviconDiscovery` does not exist.

- [x] **Step 3: Implement the bounded parser**

Scan only `<link ...>` tags and their quoted or unquoted attributes, normalize
`rel` tokens with `Locale.ROOT`, decode common numeric and named attribute
entities, resolve `href` through `URI.resolve`, and produce distinct standard
icons, then Apple touch icons, then the final-origin fallback.

- [x] **Step 4: Convert the favicon fetcher to direct candidate URLs**

Introduce:

```kotlin
fun interface FaviconBytesFetcher {
    fun fetch(iconUrl: String): ByteArray?
}
```

Make `FaviconHttpFetcher` implement it and fetch the exact supplied candidate
instead of rewriting it to `/favicon.ico`. Use an independently configured
private OkHttp client with the same no-cookie, no-authenticator, no-cache,
no-retry, and manual-redirect policy as the page probe. Preserve the existing
four-second per-call timeout plus byte/redirect/scheme bounds, and close every
response with `use`. Update its unit tests so declared asset paths are fetched
unchanged and the privacy/cleanup configuration remains enforced.

- [x] **Step 5: Make the repository try candidates until one decodes**

Change the gateway to:

```kotlin
fun fetchAndStore(
    shortcut: WebShortcut,
    candidates: List<String>,
    onComplete: (String?) -> Unit,
)
```

On its existing background executor, fetch candidates in order and call
`store(...)` until one produces a private PNG. Extend the connected test with
an invalid first candidate and valid second bitmap, and assert request order,
stored 256px output, and deletion.

- [x] **Step 6: Run focused unit and connected tests; verify GREEN**

Run:

```bash
./gradlew testDebugUnitTest --tests '*FaviconDiscoveryTest' --tests '*FaviconHttpFetcherTest'
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.FaviconRepositoryTest
```

Expected: all parser, HTTP, candidate ordering, storage, and cleanup tests pass.

### Task 3: Probe-before-persist service workflow

**Files:**
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/data/WebShortcutService.kt`
- Modify: `app/src/test/java/dev/basri/android/nobs_launcher/data/WebShortcutServiceTest.kt`

- [x] **Step 1: Write failing service tests**

Add `SaveShortcutResult.WebsiteInaccessible` and an asynchronous callback API.
Tests use a direct executor and fake probe/favicons to prove:

- local invalid input never probes or persists;
- inaccessible new/edit input never persists, deletes, or fetches an icon;
- `401`/`403`-equivalent reachable results persist normally;
- reachable input preserves UUID/favorite order and sends discovered candidates;
- cancelling before probe completion prevents persistence;
- successful persistence notifies Save before late favicon attachment;
- stale icon completion still cannot overwrite a newer URL/config change.

- [x] **Step 2: Run service tests and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests '*WebShortcutServiceTest'
```

Expected: compilation/assertion failures against the synchronous save API.

- [x] **Step 3: Implement cancellable asynchronous Save**

Return a `SaveShortcutRequest` containing an atomic cancelled flag. Validate
locally, execute `WebsiteProbeGateway.probe` off-main, check cancellation before
the atomic store update, and report exactly one terminal result. On reachable
success, calculate candidates from the final URL/HTML, preserve current
favorite ordering, delete an old icon only after persistence, and start favicon
storage when the URL changed or no icon exists.

- [x] **Step 4: Run service tests and both unit variants; verify GREEN**

Run:

```bash
./gradlew testDebugUnitTest --tests '*WebShortcutServiceTest'
./gradlew testDebugUnitTest testReleaseUnitTest
```

Expected: all service and complete unit suites pass.

### Task 4: Non-blocking editor progress and errors

**Files:**
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/ui/WebShortcutsActivity.kt`
- Modify: `app/src/main/res/layout/dialog_web_shortcut.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/HomeAndSettingsFlowTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/ManifestPrivacyTest.kt`

- [x] **Step 1: Write failing connected UI tests**

Use a bounded raw loopback HTTP server in the instrumentation process. Delay a
reachable response to assert `Checking website...`, disabled Save/name/URL, and
enabled Cancel. Release it, then assert the dialog closes and normalized
shortcut persists. For a closed loopback port, assert the editor remains open,
controls re-enable, URL shows `Website is not accessible.`, and no state is
persisted. Verify a failed edit preserves its old URL/icon/favorite.

- [x] **Step 2: Run the focused connected class and verify RED**

Run:

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.HomeAndSettingsFlowTest
```

Expected: new progress/error assertions fail because Save still persists
synchronously without probing.

- [x] **Step 3: Implement editor state and cancellation**

Add a hidden progress row to the dialog. On Save, clear errors, mark controls
checking, call the async service, and retain its request. Dispatch callbacks to
the main thread; show local validation or reachability errors without closing,
show persistence failure as a toast, and dismiss only on Saved. Cancel the
request from dialog dismissal. Ignore late UI callbacks when the dialog or
activity is gone.

Set `android:usesCleartextTraffic="true"` so the already-supported `http://`
shortcut scheme can be probed, and extend the manifest privacy assertion to
document this deliberate boundary without adding permissions.

- [x] **Step 4: Run the connected class and verify GREEN**

Run:

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.HomeAndSettingsFlowTest
```

Expected: all existing and new management/Home/Settings cases pass without TV
IME state leakage.

### Task 5: Release metadata, documentation, and full verification

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `docs/INSTALL_AND_ROLLBACK.md`
- Modify: `docs/EXECUTION_LOG.md`
- Modify: `docs/superpowers/specs/2026-09-02-website-probe-and-favicon-design.md`
- Modify: `docs/superpowers/plans/2026-09-02-website-probe-and-favicon-implementation.md`

- [x] **Step 1: Update release metadata and user documentation**

Bump to version `0.3.1`, `versionCode=5`. Explain Save-time reachability,
accepted authentication responses, declared-icon discovery/fallback, checking
UI, error behavior, network bounds, and deliberate HTTP cleartext support.

- [x] **Step 2: Record device preconditions before testing**

Append repository state, exact intended commands, installed version/config
evidence, connected serial, and current power state to `docs/EXECUTION_LOG.md`
before device mutation. The device reported Awake at the start of execution;
leave it Awake after all evidence is collected unless Basri gives a newer power
instruction.

- [x] **Step 3: Run the complete verification gate**

Run:

```bash
./gradlew lintDebug lintRelease testDebugUnitTest testReleaseUnitTest connectedCheck assembleDebug assembleRelease
scripts/build_release_apk.sh
```

Expected: zero lint/test failures; all connected tests pass on Box R; debug and
signed/shrunk release APKs build; `apksigner` verifies the release certificate.

- [x] **Step 4: Install in place and run physical release smoke**

Run:

```bash
scripts/install_release_apk.sh 192.168.1.154:5555
```

Verify version 0.3.1/code 5, unchanged first-install timestamp, preserved labels,
favorites, and existing shortcuts. Through the visible TV UI, confirm a closed
loopback URL shows the accessibility error and persists nothing. Add a temporary
reachable HTTPS shortcut whose page declares/serves a raster icon, verify its
tile is not the globe fallback, open its exact browser URL, then remove it and
confirm icon/metadata/favorite cleanup. Remove only test artifacts.

- [x] **Step 5: Restore device state and stop before committing**

Cold-launch Home for final config/crash inspection and leave the device Awake,
matching its observed start state. Do not send power, mute, unmute, or volume
commands. Mark this plan complete, run `git diff --check`, and leave all changes
uncommitted for Basri's approval.
