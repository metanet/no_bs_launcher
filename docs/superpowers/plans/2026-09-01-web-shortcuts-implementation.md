# Web Shortcuts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add locally persisted, editable website shortcuts that behave like installed apps on the launcher Home screen and open in the default browser.

**Architecture:** Replace package-name-only favorites with stable `HomeItem` IDs while retaining an atomic SharedPreferences migration from legacy package favorites. Keep URL validation, shortcut codecs, ordering, and mutations in pure Kotlin policies; isolate Android UI, browser intents, favicon HTTP/bitmap/file work, and private icon loading behind focused classes. A dedicated TV-friendly shortcut management activity owns Add/Edit/Remove, while Home uses one mixed item adapter and type-specific long-press menus.

**Tech Stack:** Kotlin 2.2, Android SDK 23-36, SharedPreferences, RecyclerView, View Binding, `HttpURLConnection`, Android `BitmapFactory`, JUnit 4, AndroidX Test/Espresso.

**Source-control constraint:** Do not commit or amend any task. Basri must separately authorize commits.

---

## File map

- Create `model/HomeItem.kt`: stable IDs and installed-app/web-shortcut variants.
- Create `model/WebShortcut.kt`: persisted shortcut record and editor validation.
- Modify `model/LauncherConfig.kt`: favorite IDs, shortcut mutations, normalization, move policy.
- Create `data/HomeItemIdCodec.kt`: strict newline encoding for stable IDs.
- Create `data/WebShortcutCodec.kt`: escaped tab/newline-safe shortcut records.
- Modify `data/LayoutStore.kt`: new keys and atomic legacy migration.
- Replace `model/HomeAppSections.kt`: mixed-item favorite and remaining composition.
- Create `data/FaviconHttpFetcher.kt`: bounded direct-origin HTTP byte retrieval.
- Create `data/FaviconRepository.kt`: bitmap validation, scaling, atomic private PNG storage, async fetch.
- Create `data/WebShortcutService.kt`: transactional metadata mutation and stale-fetch protection.
- Modify `ui/AppGridAdapter.kt`: render and interact with unified items.
- Modify `ui/HomeActivity.kt`: browser launch, shortcut menus, mixed favorites, move mode.
- Create `ui/WebShortcutsActivity.kt`: shortcut list, Add/Edit dialog, confirmation, async favicon refresh.
- Create `ui/WebShortcutsAdapter.kt`: TV-focusable shortcut rows.
- Modify `ui/SettingsActivity.kt`: open shortcut management.
- Create `res/layout/activity_web_shortcuts.xml` and `item_web_shortcut.xml`: management screen.
- Create `res/drawable/ic_web_shortcut.xml`: generic globe fallback.
- Modify resources, manifest, and Gradle version metadata.
- Expand unit and connected tests for every new policy and user flow.

### Task 1: Stable item IDs, shortcut model, and validation

**Files:**
- Create: `app/src/main/java/dev/basri/android/nobs_launcher/model/HomeItem.kt`
- Create: `app/src/main/java/dev/basri/android/nobs_launcher/model/WebShortcut.kt`
- Create: `app/src/test/java/dev/basri/android/nobs_launcher/model/WebShortcutPolicyTest.kt`

- [x] **Step 1: Write failing validation and stable-ID tests**

```kotlin
@Test fun missingSchemeDefaultsToHttps() {
    assertEquals(
        ShortcutInput.Valid("Basri", "https://example.com/path"),
        WebShortcutPolicy.validate("  Basri  ", "example.com/path"),
    )
}

@Test fun unsafeSchemesAndMissingHostsAreRejected() {
    listOf("file:///tmp/x", "javascript:alert(1)", "https:///missing")
        .forEach { assertTrue(WebShortcutPolicy.validate("Site", it) is ShortcutInput.Invalid) }
}

@Test fun IDsAreTypeSeparated() {
    assertEquals("app:com.example.tv", HomeItemId.app("com.example.tv"))
    assertEquals("web:1234", HomeItemId.web("1234"))
}
```

- [x] **Step 2: Run the focused test and confirm RED**

Run: `./gradlew testDebugUnitTest --tests '*WebShortcutPolicyTest'`
Expected: compilation failure because the shortcut model and policies do not exist.

- [x] **Step 3: Implement the minimal domain types and policy**

```kotlin
data class WebShortcut(
    val uuid: String,
    val name: String,
    val url: String,
    val faviconFileName: String? = null,
) { val itemId: String get() = HomeItemId.web(uuid) }

sealed interface ShortcutInput {
    data class Valid(val name: String, val url: String) : ShortcutInput
    data class Invalid(val field: Field, val reason: Reason) : ShortcutInput
}

sealed interface HomeItem {
    val id: String
    val label: String
    data class App(val candidate: AppCandidate) : HomeItem
    data class Web(val shortcut: WebShortcut) : HomeItem
}
```

`WebShortcutPolicy.validate` trims input, enforces an 80-character nonblank name and a 2048-character URL, prepends `https://` only when no scheme token exists, parses with `java.net.URI`, and accepts only absolute HTTP(S) URIs with a host.

- [x] **Step 4: Run both unit variants and confirm GREEN**

Run: `./gradlew testDebugUnitTest testReleaseUnitTest --tests '*WebShortcutPolicyTest'`
Expected: all `WebShortcutPolicyTest` methods pass.

### Task 2: Codecs and atomic legacy migration

**Files:**
- Create: `app/src/main/java/dev/basri/android/nobs_launcher/data/HomeItemIdCodec.kt`
- Create: `app/src/main/java/dev/basri/android/nobs_launcher/data/WebShortcutCodec.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/data/LayoutStore.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/model/LauncherConfig.kt`
- Create: `app/src/test/java/dev/basri/android/nobs_launcher/data/WebShortcutCodecTest.kt`
- Modify: `app/src/test/java/dev/basri/android/nobs_launcher/model/LauncherConfigPolicyTest.kt`
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/HomeAndSettingsFlowTest.kt`

- [x] **Step 1: Write failing codec, mutation, normalization, and migration tests**

```kotlin
@Test fun shortcutCodecRoundTripsEscapesUnicodeAndWhitespace() {
    val value = WebShortcut("id-1", "Basri\tTV %", "https://example.com/a?x=1%202", "id-1.png")
    assertEquals(listOf(value), WebShortcutCodec.decode(WebShortcutCodec.encode(listOf(value))))
}

@Test fun malformedShortcutRecordsAreSkipped() {
    assertEquals(emptyList<WebShortcut>(), WebShortcutCodec.decode("broken%QZ\trow"))
}

@Test fun normalizationKeepsValidAppsAndShortcutsOnly() {
    val config = LauncherConfig.DEFAULT.copy(
        favoriteItemIds = listOf("app:a.tv", "web:one", "web:missing", "app:gone"),
        shortcuts = listOf(WebShortcut("one", "One", "https://one.example")),
    )
    assertEquals(listOf("app:a.tv", "web:one"), LauncherConfigPolicy.normalize(config, setOf("a.tv")).favoriteItemIds)
}
```

The connected migration test writes only legacy `selected_packages`, loads `LayoutStore`, asserts exact `app:` order, and asserts the legacy key is removed only after the new favorite key exists.

- [x] **Step 2: Run focused tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests '*WebShortcutCodecTest' --tests '*LauncherConfigPolicyTest'`
Expected: compilation failures for the new codec/config APIs.

- [x] **Step 3: Implement escaped records, unified config, and migration**

Use strict stable-ID patterns `app:<valid package>` and `web:<UUID token>`. Encode shortcut fields with `%25`, `%09`, `%0A`, and `%0D`, separated by tabs and records by newline; reject a whole malformed record without affecting valid records.

```kotlin
data class LauncherConfig(
    val firstRunComplete: Boolean,
    val wifiLabel: String,
    val locationLabel: String,
    val favoriteItemIds: List<String>,
    val shortcuts: List<WebShortcut> = emptyList(),
    val welcomeText: String = "",
    val showLocation: Boolean = true,
    val showVpnStatus: Boolean = true,
    val showSystemStats: Boolean = true,
)
```

`LayoutStore.load` reads `favorite_item_ids` when present. Otherwise it converts the legacy decoded packages to `HomeItemId.app`, writes the complete new config and removes `selected_packages` in one synchronous `commit`; if that commit fails, the legacy value remains available on the next load.

- [x] **Step 4: Run unit and connected migration tests and confirm GREEN**

Run: `./gradlew testDebugUnitTest testReleaseUnitTest && ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.HomeAndSettingsFlowTest`
Expected: both unit variants and the migration-inclusive connected class pass.

### Task 3: Mixed sections, favorites, and move policy

**Files:**
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/model/HomeAppSections.kt`
- Modify: `app/src/test/java/dev/basri/android/nobs_launcher/model/HomeAppSectionsPolicyTest.kt`
- Modify: `app/src/test/java/dev/basri/android/nobs_launcher/model/LauncherConfigPolicyTest.kt`

- [x] **Step 1: Write failing mixed-order tests**

```kotlin
@Test fun appsAndShortcutsShareFavoriteOrderAndRemainingAlphabeticalOrder() {
    val web = HomeItem.Web(WebShortcut("one", "Alpha", "https://example.com"))
    val app = HomeItem.App(app("tv.beta", "Beta"))
    val sections = HomeItemSectionsPolicy.compose(listOf(app, web), listOf(app.id, web.id))
    assertEquals(listOf(app, web), sections.favorites)
}

@Test fun equalLabelsUseTypeThenStableIdTieBreakers() {
    val items = listOf(web("z", "Same"), app("tv.a", "same"), web("a", "Same"))
    assertEquals(listOf(app("tv.a", "same"), web("a", "Same"), web("z", "Same")),
        HomeItemSectionsPolicy.compose(items, emptyList()).remaining)
}
```

- [x] **Step 2: Run focused tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests '*HomeAppSectionsPolicyTest' --tests '*LauncherConfigPolicyTest'`
Expected: failures because composition remains app/package-only.

- [x] **Step 3: Implement mixed sections and ID-based mutations**

`HomeItemSectionsPolicy.compose(items, favoriteItemIds)` associates by stable ID, preserves valid distinct favorite order, and sorts remaining items by lowercase label, app-before-web type rank, then stable ID. `LauncherConfigPolicy.setFavorite`, `move`, `upsertShortcut`, and `removeShortcut` operate on IDs; removal also returns the favicon filename for cleanup.

- [x] **Step 4: Run both unit variants and confirm GREEN**

Run: `./gradlew testDebugUnitTest testReleaseUnitTest`
Expected: all unit tests pass.

### Task 4: Safe one-time favicon storage

**Files:**
- Create: `app/src/main/java/dev/basri/android/nobs_launcher/data/FaviconHttpFetcher.kt`
- Create: `app/src/main/java/dev/basri/android/nobs_launcher/data/FaviconRepository.kt`
- Create: `app/src/main/java/dev/basri/android/nobs_launcher/data/WebShortcutService.kt`
- Create: `app/src/test/java/dev/basri/android/nobs_launcher/data/FaviconHttpFetcherTest.kt`
- Create: `app/src/androidTest/java/dev/basri/android/nobs_launcher/FaviconRepositoryTest.kt`

- [x] **Step 1: Write failing HTTP bound and private-file tests**

Use injected fake `HttpURLConnection` instances to assert `/favicon.ico`, 4-second connect/read timeouts, disabled automatic redirects, maximum five HTTP(S) redirects, rejection of non-2xx responses, content lengths and streams over 256 KiB, and no cookie request header. The connected repository test supplies valid and invalid image bytes and asserts successful images become at-most-256px private PNGs while invalid bytes produce no file.

- [x] **Step 2: Run focused tests and confirm RED**

Run: `./gradlew testDebugUnitTest --tests '*FaviconHttpFetcherTest'`
Expected: compilation failure because the fetcher does not exist.

- [x] **Step 3: Implement the bounded fetcher, repository, and service**

```kotlin
class FaviconHttpFetcher(
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val maxBytes: Int = 256 * 1024,
) {
    fun fetch(siteUrl: String): ByteArray?
}
```

Build the request URL from the validated scheme, host, and port with path `/favicon.ico`. Manually follow at most five redirects whose resolved URI is HTTP(S), read at most `maxBytes + 1`, and always disconnect. `FaviconRepository` decodes with `BitmapFactory`, scales down without upscaling, writes `<uuid>-<url-hash>.tmp`, fsyncs/closes, then renames to `.png`. `WebShortcutService` persists metadata first, deletes the old icon after a valid URL change, performs fetches on a single background executor, and attaches a fetched filename only when the current shortcut still has the same URL; stale files are deleted.

- [x] **Step 4: Run unit and connected favicon tests and confirm GREEN**

Run: `./gradlew testDebugUnitTest testReleaseUnitTest && ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.FaviconRepositoryTest`
Expected: all focused favicon cases pass.

### Task 5: TV shortcut management screen

**Files:**
- Create: `app/src/main/java/dev/basri/android/nobs_launcher/ui/WebShortcutsActivity.kt`
- Create: `app/src/main/java/dev/basri/android/nobs_launcher/ui/WebShortcutsAdapter.kt`
- Create: `app/src/main/res/layout/activity_web_shortcuts.xml`
- Create: `app/src/main/res/layout/item_web_shortcut.xml`
- Create: `app/src/main/res/drawable/ic_web_shortcut.xml`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/ui/SettingsActivity.kt`
- Modify: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/HomeAndSettingsFlowTest.kt`

- [x] **Step 1: Write failing connected management tests**

Test that Settings opens **Web shortcuts**, Add shows Name/URL fields, invalid schemes keep the editor visible with a URL error, valid input persists normalized metadata and a visible row, Edit retains UUID/favorite order and changes the URL, Remove defaults to cancel and confirmation removes metadata/favorite/icon, and D-pad focus can reach list actions and Add.

- [x] **Step 2: Run the connected class and confirm RED**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.HomeAndSettingsFlowTest`
Expected: view lookup failures because the management controls do not exist.

- [x] **Step 3: Implement management UI and focus behavior**

`WebShortcutsActivity` displays a RecyclerView plus Add and Back buttons. Add/Edit use an `AlertDialog` containing single-line name and URL `EditText`s; Save calls `WebShortcutPolicy.validate`, sets field errors without closing on invalid input, calls `WebShortcutService`, refreshes immediately after metadata save, and refreshes again after favicon completion. Each row has icon, name, URL, Edit, and Remove. Removal uses a confirmation dialog with a negative Cancel button and deletes returned private icon files only after config persistence succeeds.

- [x] **Step 4: Run the connected management tests and confirm GREEN**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.HomeAndSettingsFlowTest`
Expected: all management flows pass.

### Task 6: Unified Home grid and browser behavior

**Files:**
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/ui/AppGridAdapter.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/ui/HomeActivity.kt`
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/HomeAndSettingsFlowTest.kt`

- [x] **Step 1: Write failing connected Home tests**

Seed one app and one shortcut in mixed favorite order. Assert tile order and separator behavior; shortcut click emits exactly `ACTION_VIEW` with its normalized URI; nonfavorite menu is Add/Edit/Remove; favorite menu is Move/Remove favorite/Edit/Remove; mixed left/right move persists stable IDs and shifts neighboring items; shortcut removal confirmation deletes it without invoking package uninstall; existing app menus and `ACTION_DELETE` remain unchanged.

- [x] **Step 2: Run the connected Home class and confirm RED**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.HomeAndSettingsFlowTest`
Expected: mixed Home assertions fail because the adapter remains app-only.

- [x] **Step 3: Implement unified rendering, actions, focus, and move mode**

`AppGridAdapter` accepts `HomeItem`, uses IDs for position/focus/moving state and stable RecyclerView IDs, loads an app drawable or private favicon, and falls back to `ic_web_shortcut` for web items. `HomeActivity` composes catalog apps plus stored shortcuts, opens apps through `AppCatalog`, opens web URLs through implicit `ACTION_VIEW`, exposes type-specific menus, launches the editor with the stable web ID, confirms shortcut removal, and persists favorite/move order by item ID. Failed app or browser launches reuse the existing temporary error banner.

- [x] **Step 4: Run connected Home tests and confirm GREEN**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.HomeAndSettingsFlowTest`
Expected: all existing and new Home/settings flows pass.

### Task 7: Privacy, release metadata, and static verification

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/ManifestPrivacyTest.kt`
- Modify: `app/build.gradle.kts`
- Modify: `docs/INSTALL_AND_ROLLBACK.md`

- [x] **Step 1: Write the failing privacy/version assertions**

Update the exact permission allowlist expectation to include `Manifest.permission.INTERNET` and add assertions that the debug package reports version name `0.3.0-debug` and version code `4`.

- [x] **Step 2: Run focused connected tests and confirm RED**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.ManifestPrivacyTest`
Expected: permission/version assertions fail against 0.2.1 code 3 without INTERNET.

- [x] **Step 3: Add only the approved normal permission and bump version**

Add `<uses-permission android:name="android.permission.INTERNET" />`, set `versionCode = 4`, `versionName = "0.3.0"`, and update install/rollback documentation with the new artifact/version while retaining guarded in-place installation.

- [x] **Step 4: Run lint, unit suites, connected suite, and builds**

Run: `./gradlew lintDebug lintRelease testDebugUnitTest testReleaseUnitTest connectedCheck assembleDebug assembleRelease`
Expected: all tasks succeed with no lint errors or test failures and both APKs are produced.

### Task 8: Signed release, update-in-place, and real-device smoke test

**Files:**
- Modify: `docs/EXECUTION_LOG.md`

- [x] **Step 1: Record pre-install state before mutating the device**

Append the current package version, selected/favorite configuration evidence obtainable through tests/UI, connected serial `192.168.1.154:5555`, and the verification commands to the execution log before installation.

- [x] **Step 2: Build and cryptographically verify the signed release**

Run: `scripts/build_release_apk.sh`
Expected: lint, full unit and connected tests, debug/release assemblies, R8/resource shrinking, `apksigner` verification, and a certificate SHA-256 digest all succeed.

- [x] **Step 3: Install in place and verify migration**

Run: `scripts/install_release_apk.sh 192.168.1.154:5555`
Expected: `adb install -r` succeeds, the installed package reports `0.3.0`, the certificate matches the existing install, and previously configured panels/favorites remain visible and ordered.

- [x] **Step 4: Exercise a temporary real shortcut and clean it up**

Using D-pad-compatible `adb shell input keyevent` and focused UI inspection, create a uniquely named temporary shortcut to an HTTPS site, verify a web tile appears with either its stored favicon or the generic globe, monitor and open the exact `ACTION_VIEW` URL in the device browser, return Home, add/remove it from favorites, remove the shortcut with confirmation, and verify its row, Home tile, favorite ID, and private icon file are gone. Do not issue mute, volume, or power commands.

- [x] **Step 5: Complete the final release handoff and power down**

Launch the installed Home activity, confirm it is resumed with `dumpsys activity`, append exact commands/results and any independently observed limitations to `docs/EXECUTION_LOG.md`, commit the authorized finished work, then honor Basri's latest instruction by sending one power key only after confirming the device is Awake. Verify standby/asleep without sending audio or volume input.

### Task 9: Review remediation

- [x] **Step 1: Preserve independently saved shortcuts when Settings saves**

Add a connected regression that creates or removes a shortcut, returns to the
already-open Settings activity, presses Save, and verifies current shortcut and
web-favorite state survives. Save only Settings-owned fields and app-favorite
changes onto a freshly loaded configuration.

- [x] **Step 2: Make async favicon attachment an atomic mutation**

Add a deterministic interleaving test in which another configuration change
lands during favicon attachment. Provide one process-local atomic mutation path
for whole-config read/modify/write operations and use it for shortcut and Home
mutations so neither update is lost.

- [x] **Step 3: Bound bitmap decode memory and verify real deletion**

Decode favicon data with a bounds-derived sample size near the 256px output,
fall back on decode failures, and extend the connected repository test to cover
a large compressible source plus deletion of the stored PNG and temp file.

- [x] **Step 4: Prove TV D-pad management behavior**

Add connected key-driven coverage that reaches Add and row actions using D-pad
input and verifies shortcut-removal confirmation initially focuses Cancel.

- [x] **Step 5: Repeat release verification and final handoff**

Run the full lint/unit/connected/build/signing gate, install 0.3.0 in place,
repeat a release shortcut smoke test, commit the authorized finished work, and
turn the device off without sending audio or volume commands.
