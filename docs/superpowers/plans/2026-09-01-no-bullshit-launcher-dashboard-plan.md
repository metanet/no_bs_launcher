# No bullshit launcher dashboard implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the portable launcher and add user-configurable welcome, location, generic VPN, and live system-information panels without adding production permissions.

**Architecture:** Extend the immutable launcher configuration and consolidated Settings activity, replace the provider-specific VPN policy, and add a lifecycle-bound system-statistics reader/monitor with pure delta and formatting policies. Keep the existing Home grid, persistence, signing identity, and privacy boundary.

**Tech Stack:** Kotlin 2.2.0, Android Views/RecyclerView, Android framework ActivityManager/StatFs/TrafficStats/ConnectivityManager APIs, JUnit 4, AndroidX Test/Espresso.

**Source-control constraint:** Do not commit or amend. Basri has not authorized commits.

---

### Task 1: Rename product and package

**Files:** Gradle namespace/application ID, manifest, Kotlin packages, tests, strings, ProGuard rules, scripts, and delivery docs.

- [x] Replace every `dev.basri.android.kahveci_home` package reference with `dev.basri.android.nobs_launcher`, including the debug ID and Home component.
- [x] Replace the user-visible app label with exactly `No bullshit launcher` and rename local preference/service identifiers that should not collide with the old package.
- [x] Update signing/build/install scripts and docs while retaining the already-created private signing key.
- [x] Run unit-test compilation and manifest processing; expect no stale old-package references outside historical design documents and the legacy signing-key path/service explanation.

### Task 2: Extend configuration through TDD

**Files:** `LauncherConfig.kt`, `LayoutStore.kt`, and `LauncherConfigPolicyTest.kt`.

- [x] Write failing tests proving defaults enable location/VPN/stats, welcome defaults blank, toggle values round-trip, and later Settings Back leaves persisted configuration unchanged.
- [x] Run the focused unit test and confirm failures are caused by missing fields/defaults.
- [x] Add `welcomeText`, `showLocation`, `showVpnStatus`, and `showSystemStats` to `LauncherConfig`; centralize `LauncherConfig.DEFAULT`; persist all fields in app-private preferences.
- [x] Run all unit tests and update existing fixtures to use named arguments without weakening assertions.

### Task 3: Make VPN provider-neutral through TDD

**Files:** `VpnStatusPolicy.kt`, `VpnStatusMonitor.kt`, VPN unit tests, and manifest.

- [x] Write failing tests that return `VPN connected` for any active VPN and null when inactive.
- [x] Run RED, replace `NordVpnPolicy` with `VpnStatusPolicy`, and remove installed-package checks and the NordVPN manifest query.
- [x] Run unit and manifest privacy tests; assert the label contains no provider name and the sole permission remains `ACCESS_NETWORK_STATE`.

### Task 4: Add system-statistics policies and monitor through TDD

**Files:** New `stats/SystemStats.kt`, `stats/SystemStatsPolicy.kt`, `stats/SystemStatsReader.kt`, `stats/SystemStatsMonitor.kt`, and their unit/connected tests.

- [x] Write failing pure tests for CPU busy/total deltas, counter resets, percentage clamps, binary byte formatting, per-second ingress/egress, and unavailable values.
- [x] Implement immutable raw/display snapshots and pure policy functions; run focused GREEN.
- [x] Write a failing connected test requiring positive memory/storage/core capacity and either bounded CPU use or an explicit unavailable state.
- [x] Implement framework readers and a two-second single-thread sampler; catch source-specific failures and post immutable snapshots to the main thread.
- [x] Run focused connected GREEN and the full unit suite.

### Task 5: Add Settings controls and Home panels test-first

**Files:** Home/Settings layouts, `HomeActivity.kt`, `ManageAppsActivity.kt` renamed to `SettingsActivity.kt`, adapters, strings, and `HomeAndManageFlowTest.kt` renamed to `HomeAndSettingsFlowTest.kt`.

- [x] Add failing connected tests for welcome rendering, location toggle, VPN toggle-independent wording, system-panel toggle, Save persistence, later Back discard, and first-run Back blocking.
- [x] Run focused RED and confirm missing IDs/fields cause the failures.
- [x] Add the welcome, separate Wi-Fi/location, generic VPN, and four-row system panel views with overflow-safe text sizing.
- [x] Build the consolidated Settings fields and checkboxes above the existing app list; Save the full working copy and discard it on later Back.
- [x] Bind `SystemStatsMonitor` only when enabled, retain TV focus behavior, and rename Manage labels/actions to Settings.
- [x] Run focused GREEN, all unit tests, and all connected tests.

### Task 6: Final privacy, release, installation, and E2E

**Files:** privacy tests, scripts, installation documentation, and generated signed APK.

- [x] Run zero-warning `lintDebug lintRelease`, full `test`, full `connectedCheck`, and debug/release assemblies; fix every regression.
- [x] Run the release script, verify the final package/label/signature, and record APK size/certificate digest.
- [x] Install with guarded `adb install -r`, complete first-run labels/settings/apps, and select `dev.basri.android.nobs_launcher/.ui.HomeActivity` as Home.
- [x] Exercise welcome/toggles/stats refresh, generic VPN inactive behavior, app show/hide, move save/cancel, Android Settings, launch failure, and Home-from-app using automated and physical-device evidence.
- [x] Verify configuration persistence across launcher process death/restart. A physical reboot is deliberately deferred because unattended reboot resets wireless debugging on this box and would prevent the required standby action.
- [x] Verify exact permission, no app-owned sockets, launcher PSS, available memory, crash/ANR logs, Bluetooth remote, Cast, phone remote, HDMI-CEC/audio, Widevine, Wi-Fi, and representative retained apps.
- [x] Restore stock Home and then No bullshit launcher to prove rollback; leave the custom launcher selected.
- [x] Send only Android power key event 26 after all evidence is captured, confirm the device is non-interactive/standby if ADB remains available, and do not send any volume or mute key.
- [x] Report all verified results, material autonomous decisions, and the unverified Xiaomi physical-device scope.
