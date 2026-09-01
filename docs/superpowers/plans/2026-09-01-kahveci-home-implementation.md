# Kahveci Home Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build, sign, install, and verify a private cross-vendor Android TV Home launcher with a selectable and manually ordered app grid.

**Architecture:** A single native Kotlin Android application uses platform Activities and Android Views with `RecyclerView`. Pure policy classes own app selection, ordering, catalog deduplication, and VPN-label decisions; Android adapters own package discovery, preferences, connectivity callbacks, and activity launching. The production APK has no Internet, location, analytics, accessibility, notification-listener, or broad package-query capability.

**Tech Stack:** Kotlin 2.2.0, Android Gradle Plugin 8.10.1, Gradle 8.12, compile/target SDK 36, min SDK 23, AndroidX RecyclerView 1.4.0, JUnit 4, AndroidX Test, Espresso.

**Source-control constraint:** Do not commit or amend. Basri has not authorized commits, so the usual per-task commit steps are intentionally omitted.

---

## File map

### Build and policy

- `settings.gradle.kts`: plugin and dependency repositories; one `:app` module.
- `build.gradle.kts`: pinned Android and Kotlin plugins.
- `gradle.properties`: deterministic Gradle and AndroidX settings.
- `gradle/libs.versions.toml`: production and test dependencies.
- `app/build.gradle.kts`: SDK levels, tests, shrinking, and optional release signing properties.
- `app/proguard-rules.pro`: retain Home activity and model names required by Android.
- `app/src/main/AndroidManifest.xml`: Home intent, limited queries, and `ACCESS_NETWORK_STATE` only.

### Core Kotlin

- `app/src/main/java/dev/basri/android/kahveci_home/model/LauncherConfig.kt`: immutable configuration and pure selection/order policy.
- `app/src/main/java/dev/basri/android/kahveci_home/data/LayoutCodec.kt`: deterministic newline encoding for ordered package names.
- `app/src/main/java/dev/basri/android/kahveci_home/data/LayoutStore.kt`: app-private SharedPreferences adapter.
- `app/src/main/java/dev/basri/android/kahveci_home/data/AppCatalog.kt`: standard PackageManager query adapter and pure TV-over-mobile deduplication.
- `app/src/main/java/dev/basri/android/kahveci_home/status/NordVpnPolicy.kt`: pure confirmed-provider decision.
- `app/src/main/java/dev/basri/android/kahveci_home/status/VpnStatusMonitor.kt`: lifecycle-bound ConnectivityManager callback.
- `app/src/main/java/dev/basri/android/kahveci_home/time/ClockController.kt`: minute-boundary clock/date updates.

### UI Kotlin and resources

- `app/src/main/java/dev/basri/android/kahveci_home/ui/AppGridAdapter.kt`: four-column Home tiles.
- `app/src/main/java/dev/basri/android/kahveci_home/ui/ManageAppsAdapter.kt`: selectable app rows.
- `app/src/main/java/dev/basri/android/kahveci_home/ui/HomeActivity.kt`: Home routing, launch, focus, and move mode.
- `app/src/main/java/dev/basri/android/kahveci_home/ui/ManageAppsActivity.kt`: first-run setup, labels, show/hide, and Android Settings link.
- `app/src/main/res/layout/activity_home.xml`: approved 30/70 Calm Grid composition.
- `app/src/main/res/layout/activity_manage_apps.xml`: label fields, app list, Settings, and Done controls.
- `app/src/main/res/layout/item_app_tile.xml`: banner/icon plus always-visible app label.
- `app/src/main/res/layout/item_manage_app.xml`: app icon, label, and selected check state.
- `app/src/main/res/drawable/*.xml`: gradient, focus, move-mode, tile, and icon fallbacks.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and foreground/background resources: local vector app identity.
- `app/src/main/res/values/{colors,dimens,strings,styles}.xml`: TV-readable design tokens and text.

### Tests and delivery

- `app/src/test/java/dev/basri/android/kahveci_home/model/LauncherConfigPolicyTest.kt`
- `app/src/test/java/dev/basri/android/kahveci_home/data/LayoutCodecTest.kt`
- `app/src/test/java/dev/basri/android/kahveci_home/data/AppCatalogPolicyTest.kt`
- `app/src/test/java/dev/basri/android/kahveci_home/status/NordVpnPolicyTest.kt`
- `app/src/androidTest/java/dev/basri/android/kahveci_home/ManifestPrivacyTest.kt`
- `app/src/androidTest/java/dev/basri/android/kahveci_home/HomeAndManageFlowTest.kt`
- `scripts/build_release_apk.sh`: reads signing material without printing secrets and verifies the release archive.
- `scripts/install_release_apk.sh`: guarded `adb install -r` plus package/signature checks.
- `docs/INSTALL_AND_ROLLBACK.md`: default-Home activation, reboot test, and stock-launcher restoration.

---

### Task 1: Build skeleton and privacy-first manifest

**Files:** Build files, Gradle wrapper, manifest, base values, icon resources, and `.gitignore` from the file map.

- [x] **Step 1: Create deterministic build configuration**

Pin AGP 8.10.1, Kotlin 2.2.0, RecyclerView 1.4.0, JUnit 4.13.2, AndroidX Test JUnit 1.2.1, Espresso 3.6.1, and Test Runner 1.6.2. Configure Java/Kotlin 17 bytecode, compile/target 36, min 23, `testInstrumentationRunner`, resource shrinking for release, and no dynamic dependency versions.

- [x] **Step 2: Declare the Home activity and narrow package visibility**

The manifest must contain exactly one production permission:

```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

It must query only TV/mobile launch intents and NordVPN:

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
    <package android:name="com.nordvpn.android" />
</queries>
```

These are the only package-visibility declarations. The manifest must not request `android.permission.QUERY_ALL_PACKAGES`.

`HomeActivity` must be exported with `MAIN`, `HOME`, `DEFAULT`, and `LEANBACK_LAUNCHER`. Set `android:allowBackup="false"`, landscape orientation, no recents duplication, and a TV banner.

- [x] **Step 3: Generate and validate the wrapper**

Run:

```bash
/Users/basri/dev/oss/StreamVault-IPTV/gradlew -p /Users/basri/dev/personal/kahveci_home wrapper --gradle-version 8.12
./gradlew tasks
```

Expected: wrapper files are generated and `tasks` exits 0.

### Task 2: Configuration and ordering policy through TDD

**Files:** `LauncherConfig.kt`, `LauncherConfigPolicyTest.kt`.

- [x] **Step 1: Write failing tests**

Tests cover append-on-select, remove-on-hide, duplicate removal, normalization against installed packages, move left/right/up/down through a linear ordered list, and cancel preserving the original list. The core test API is:

```kotlin
@Test
fun selectingANewPackageAppendsIt() {
    val initial = LauncherConfig(true, "Kahveci House", "London", listOf("youtube"))
    val updated = LauncherConfigPolicy.setVisible(initial, "jellyfin", true)
    assertEquals(listOf("youtube", "jellyfin"), updated.selectedPackages)
}

@Test
fun moveReturnsAReorderedCopy() {
    val packages = listOf("youtube", "music", "netflix", "jellyfin")
    assertEquals(
        listOf("music", "netflix", "youtube", "jellyfin"),
        LauncherConfigPolicy.move(packages, fromIndex = 0, toIndex = 2),
    )
}
```

- [x] **Step 2: Run RED**

Run `./gradlew testDebugUnitTest --tests '*LauncherConfigPolicyTest'`.

Expected: compilation fails because `LauncherConfig` and `LauncherConfigPolicy` do not exist.

- [x] **Step 3: Implement minimum pure policy**

```kotlin
data class LauncherConfig(
    val firstRunComplete: Boolean,
    val wifiLabel: String,
    val locationLabel: String,
    val selectedPackages: List<String>,
)

object LauncherConfigPolicy {
    fun setVisible(config: LauncherConfig, packageName: String, visible: Boolean): LauncherConfig {
        val ordered = config.selectedPackages.distinct().toMutableList()
        if (visible && packageName !in ordered) ordered += packageName
        if (!visible) ordered.removeAll { it == packageName }
        return config.copy(selectedPackages = ordered)
    }

    fun move(packages: List<String>, fromIndex: Int, toIndex: Int): List<String> {
        if (fromIndex !in packages.indices || toIndex !in packages.indices || fromIndex == toIndex) {
            return packages.toList()
        }
        return packages.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    fun normalize(config: LauncherConfig, installedPackages: Set<String>): LauncherConfig =
        config.copy(selectedPackages = config.selectedPackages.distinct().filter(installedPackages::contains))
}
```

- [x] **Step 4: Run GREEN and full unit suite**

Run `./gradlew testDebugUnitTest`.

Expected: all configuration tests pass.

### Task 3: Ordered persistence and app catalog through TDD

**Files:** `LayoutCodec.kt`, `LayoutStore.kt`, `AppCatalog.kt`, their two unit test files.

- [x] **Step 1: Write LayoutCodec failing tests**

Prove round trip, blank input, invalid package rejection, and duplicate preservation prevention. Package validation is `[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+`.

- [x] **Step 2: Run RED, implement codec, run GREEN**

The implementation is newline-based because Android package names cannot contain newlines:

```kotlin
object LayoutCodec {
    private val packagePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

    fun encode(packages: List<String>): String = packages.distinct().joinToString("\n")

    fun decode(value: String): List<String> = value.lineSequence()
        .map(String::trim)
        .filter(packagePattern::matches)
        .distinct()
        .toList()
}
```

Run `./gradlew testDebugUnitTest --tests '*LayoutCodecTest'` before and after implementation.

- [x] **Step 3: Implement SharedPreferences LayoutStore**

Use a single private file named `kahveci_home_layout`. Missing state returns `firstRunComplete=false`, blank labels, and an empty selected list. Every load normalizes decoded packages. Every save uses `commit()` off the UI hot path so first-run completion is durable before Home starts.

- [x] **Step 4: Write AppCatalog policy failing tests**

Prove that TV candidates win over mobile candidates for the same package, the launcher excludes its own package, labels sort case-insensitively, and only one tile is produced per package.

```kotlin
@Test
fun tvCandidateWinsOverMobileCandidate() {
    val result = AppCatalogPolicy.select(
        candidates = listOf(
            AppCandidate("pkg.video", "Video", LaunchKind.MOBILE, "MobileActivity"),
            AppCandidate("pkg.video", "Video TV", LaunchKind.TV, "TvActivity"),
        ),
        selfPackage = "dev.basri.android.kahveci_home",
    )
    assertEquals("TvActivity", result.single().activityName)
}
```

- [x] **Step 5: Run RED, implement catalog, run GREEN**

`AndroidPackageSource` queries `ResolveInfo` using API-appropriate `PackageManager.ResolveInfoFlags`, loads labels and banner/icon drawables, and produces candidates. `AppCatalogPolicy` groups by package and chooses `TV` before `MOBILE`.

Run `./gradlew testDebugUnitTest` and expect all tests to pass.

### Task 4: Privacy-preserving NordVPN status through TDD

**Files:** `NordVpnPolicy.kt`, `VpnStatusMonitor.kt`, `NordVpnPolicyTest.kt`.

- [x] **Step 1: Write failing policy tests**

```kotlin
@Test
fun showsLabelOnlyWhenVpnIsActiveAndNordVpnIsInstalled() {
    assertEquals("NordVPN connected", NordVpnPolicy.label(true, true))
    assertNull(NordVpnPolicy.label(true, false))
    assertNull(NordVpnPolicy.label(false, true))
}
```

- [x] **Step 2: Run RED, implement, run GREEN**

The pure policy requires an active VPN and an installed NordVPN package. The Android monitor tracks networks with `TRANSPORT_VPN`, checks package visibility for `com.nordvpn.android`, registers a `NetworkCallback`, and emits null when either condition is false. Android's public SDK does not expose another app's VPN owner, so the documented behavior assumes NordVPN is the only VPN used on these boxes. It never uses hidden APIs, process heuristics, notification access, or IP geolocation.

Run `./gradlew testDebugUnitTest` and expect all tests to pass.

### Task 5: Build the approved TV UI and move behavior test-first

**Files:** all layout, drawable, value, adapter, and `ClockController` files.

- [x] **Step 1: Add failing instrumentation assertions for stable view IDs**

The Home layout test requires visible IDs `clock`, `date`, `location`, `vpn_status`, `manage_apps`, `app_grid`, and `empty_state`. The Manage layout requires `wifi_label`, `location_label`, `available_apps`, `open_android_settings`, and `done`.

- [x] **Step 2: Run RED**

Run `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.kahveci_home.HomeAndManageFlowTest`.

Expected: instrumentation compilation fails because Activities and resources do not exist.

- [x] **Step 3: Implement layouts and adapters**

Use a horizontal root with a 30 percent information panel and 70 percent content panel. The Home `RecyclerView` uses `GridLayoutManager(context, 4)`. Each tile is focusable, uses the app banner or icon, always shows its label, and has normal/focused/move-mode selector states. The Manage list shows every catalog entry and its selected check state.

- [x] **Step 4: Implement ClockController**

Use `Handler(Looper.getMainLooper())` to update at the next minute boundary. Register time/date/timezone/locale broadcasts only while started; unregister and remove callbacks while stopped. Format from the device locale and 24-hour preference.

- [x] **Step 5: Run focused instrumentation GREEN**

Run the focused instrumentation class and expect every layout/focus assertion to pass.

### Task 6: Implement first-run, selection, launching, and move mode

**Files:** `HomeActivity.kt`, `ManageAppsActivity.kt`, adapters, and `HomeAndManageFlowTest.kt`.

- [x] **Step 1: Extend failing flow tests**

Cover:

- Incomplete first-run state routes Home to Manage.
- Completing Manage persists labels and selected packages.
- New apps are unchecked.
- Home renders only selected installed packages in stored order.
- Manage remains reachable with an empty selection.
- Long-press enters move mode, OK persists, and Back restores the original order.
- A failed launch leaves Home resumed and displays `Unable to open <label>`.

- [x] **Step 2: Run RED**

Run the focused instrumentation class and confirm the new behavioral assertions fail rather than error from test setup.

- [x] **Step 3: Implement ManageAppsActivity**

Load catalog and config, update the working config on each checkbox press, append newly selected packages, remove hidden packages, edit local labels, and persist on Done. In first-run mode, Back stays in setup. Later Manage sessions allow Back without saving. The Android Settings button launches `Settings.ACTION_SETTINGS`.

- [x] **Step 4: Implement HomeActivity**

On resume, normalize selection against the live catalog, persist pruning, bind the status panel and grid, and retain focus by package name. Tile click resolves and starts the TV launch intent. Tile long-click snapshots order and enters move mode. D-pad swaps through the ordered list; OK saves; Back restores the snapshot.

- [x] **Step 5: Run GREEN and full automated suites**

Run:

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

Expected: zero unit or instrumentation failures.

### Task 7: Enforce privacy and release quality

**Files:** `ManifestPrivacyTest.kt`, ProGuard rules, build configuration.

- [x] **Step 1: Write failing merged-manifest permission test**

The test reads `PackageInfo.requestedPermissions` and asserts the exact set equals `{android.permission.ACCESS_NETWORK_STATE}`. It also asserts `ApplicationInfo.FLAG_ALLOW_BACKUP` is absent and the Home intent resolves to `HomeActivity`.

- [x] **Step 2: Prove RED with a temporary forbidden permission**

Temporarily add `android.permission.INTERNET` in `app/src/debug/AndroidManifest.xml`, run only `ManifestPrivacyTest`, and confirm the exact-permission assertion fails by reporting INTERNET. Delete the temporary debug manifest immediately after observing the expected failure.

- [x] **Step 3: Remove all extra permissions/dependencies and run GREEN**

Retain only RecyclerView and AndroidX test dependencies. Run the full instrumentation suite.

- [ ] **Step 4: Run lint, full tests, and builds**

```bash
./gradlew lintDebug lintRelease
./gradlew test
./gradlew connectedCheck
./gradlew assembleDebug assembleRelease
```

Expected: every command exits 0 with no introduced lint error or failed test.

### Task 8: Signing, installation, default Home, E2E, and rollback

**Files:** release scripts and `docs/INSTALL_AND_ROLLBACK.md`.

- [ ] **Step 1: Create private release signing material**

Generate a dedicated RSA-4096 key outside the repository, store its password in the macOS login keychain without printing it, and record only the keystore path and alias in ignored local configuration. Do not overwrite an existing key. Capture the SHA-256 certificate fingerprint in the installation document.

- [ ] **Step 2: Build and verify signed release**

Run `scripts/build_release_apk.sh`. It must run the full verification gate before the release build, then verify the APK with `apksigner verify --verbose --print-certs` and print the artifact path and certificate fingerprint without secrets.

- [ ] **Step 3: Install non-destructively**

Run `scripts/install_release_apk.sh 192.168.1.154:5555`. It must use `adb install -r`, reject package/signature mismatch, and never uninstall.

- [ ] **Step 4: Configure Box R and smoke as an ordinary app**

Enter `Kahveci House` and `London`, select representative retained apps, verify real icons/names, show/hide, ordering save/cancel, Android Settings, conditional NordVPN label, and failure-safe launch behavior using the Bluetooth remote and ADB evidence.

- [ ] **Step 5: Set default Home and verify reboot persistence**

Use standard Home selection or `cmd package set-home-activity --user 0 dev.basri.android.kahveci_home/.ui.HomeActivity`. Press Home from multiple apps, reboot, restore Wireless ADB, and verify Kahveci Home is the resolved and resumed Home activity.

- [ ] **Step 6: Run retained-feature E2E suite**

Launch YouTube, YouTube Music, Netflix, StreamVault, Jellyfin, Play Store, NordVPN, and Android Settings. Verify Bluetooth HID remote, Cast ports/service, phone-remote port/service, HDMI-CEC LG/Sonos topology, HDMI audio capabilities, Widevine, Wi-Fi, and empty crash/ANR/watchdog evidence.

- [ ] **Step 7: Measure footprint and verify privacy on device**

Record APK bytes, launcher PSS, `MemAvailable`, requested permissions, open sockets owned by the launcher, and package state. The launcher must own no listening or outbound network socket.

- [ ] **Step 8: Exercise rollback and restore Kahveci Home**

Set Google TV Home as default, verify Home opens it, then select Kahveci Home again and verify Home returns to the custom grid. Do not disable or uninstall either launcher during this test.

- [ ] **Step 9: Report verified versus unverified scope**

Report all fresh commands and results. State that Xiaomi compatibility is designed but not physically verified until that box is connected.
