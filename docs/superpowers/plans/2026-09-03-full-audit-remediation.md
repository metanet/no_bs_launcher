# Full Audit Remediation Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix all audit findings, verify them locally and on the Android TV, and deliver a reviewable commit stack on local `main` without pushing.

**Architecture:** Keep the current activity-and-store design, but move package and image work behind application-scoped asynchronous repositories. Centralize outbound URL policy so page probes, redirects, and favicons share cancellation, downgrade, origin, and private-network rules. Preserve the persisted model independently of transient device catalogs.

**Tech Stack:** Kotlin, Android framework APIs, OkHttp, JUnit, AndroidX Test/Espresso, Gradle/R8, shell release tooling.

---

### Task 1: Make instrumentation non-destructive

**Files:** Modify `app/src/androidTest/java/dev/basri/android/nobs_launcher/HomeAndSettingsFlowTest.kt`; create `app/src/androidTest/AndroidManifest.xml` and an app-owned fixture activity.

- Replace arbitrary installed-package selection and `pm disable-user` with a launcher fixture whose protected activity makes launch fail without device mutation.
- Run the focused instrumentation flow, then the Android test suite.
- Commit as `test: remove destructive package mutation`.

### Task 2: Preserve unavailable favorites

**Files:** Modify `LauncherConfig.kt`, `LauncherConfigPolicyTest.kt`, and presentation callers if needed.

- Add failing tests for partial and empty package catalogs.
- Preserve app favorite IDs during normalization while continuing to prune deleted shortcut IDs.
- Run all JVM tests and assemble debug; commit as `fix: preserve temporarily unavailable favorites`.

### Task 3: Correct VPN monitoring

**Files:** Modify `VpnStatusMonitor.kt`; add Android request coverage.

- Assert the VPN request does not require `NOT_VPN`.
- Remove that default capability before adding `TRANSPORT_VPN`.
- Run focused Android coverage and JVM tests; commit as `fix: observe VPN transport networks`.

### Task 4: Move package catalog work off the UI thread

**Files:** Modify `AppCatalog.kt`, `HomeActivity.kt`, `SettingsActivity.kt`, catalog tests, and package-change coverage.

- Add tests proving deduplication occurs before artwork loading, cache reuse works, and invalidation reloads.
- Add an application-scoped async cached catalog with package-change invalidation and lifecycle generation guards.
- Run JVM tests, UI flows, and builds; commit as `perf: load and cache app catalog off main thread`.

### Task 5: Make favicon work lifecycle-safe and efficient

**Files:** Modify `FaviconRepository.kt`, `WebShortcutService.kt`, `WebShortcutsActivity.kt`, adapters, and favicon tests.

- Add application-scoped ownership so leaving the editor cannot terminate saved-shortcut favicon work.
- Add asynchronous decoding plus a bounded drawable/bitmap memory cache for RecyclerView binding.
- Enforce one overall favicon deadline and shared HTTP connection resources.
- Run repository/service/UI tests and builds; use separate commits for lifecycle and display-cache issues.

### Task 6: Propagate cancellation to HTTP

**Files:** Modify `WebShortcutService.kt`, `WebsiteHttpClient.kt`, their interfaces and tests.

- Add a test where canceling a save cancels the active OkHttp call and frees the executor for the next save.
- Wire request cancellation to `Call.cancel()` and keep stale callbacks suppressed.
- Run all network/service unit tests; commit as `fix: cancel active shortcut network calls`.

### Task 7: Harden URL handling and probing

**Files:** Modify `WebShortcut.kt`, `WebsiteHttpClient.kt`, `FaviconDiscovery.kt`, `FaviconHttpFetcher.kt`, list display code, and tests.

- In distinct commits, reject URL userinfo; use HEAD for page reachability; block HTTPS downgrade; block public-to-private redirects and favicon fetches; restrict discovered icons to same-origin; remove version data from the user agent; redact sensitive query/fragment display.
- Retain 401/403 acceptance and explicitly entered local/HTTP destinations.
- Run all URL/network tests and release builds after each commit.

### Task 8: Remove main-thread preference disk writes and restore UI state

**Files:** Modify `LayoutStore.kt`, `SettingsActivity.kt`, `WebShortcutsActivity.kt`, and activity/store tests.

- Switch UI persistence to SharedPreferences' asynchronous disk path while retaining immediate in-memory visibility.
- Save and restore unsaved settings and shortcut editor state; safely restart or reset interrupted validation.
- Run recreation instrumentation plus all JVM tests; use one commit per issue.

### Task 9: Reduce stats polling work

**Files:** Modify `SystemStatsReader.kt`, `SystemStatsMonitor.kt`, and tests.

- Cache core count and maximum frequency, avoid cpuidle reads when `/proc/stat` succeeds, and suppress unchanged UI publications.
- Verify parser fallbacks and repeated-read call counts; commit as `perf: avoid redundant system stats work`.

### Task 10: Harden release supply chain and optimization

**Files:** Modify release scripts, Gradle wrapper/configuration, verification metadata, `proguard-rules.pro`, and script/build tests.

- Pin the expected release signer for both first install and updates.
- Add the official Gradle 8.12 distribution checksum and Gradle dependency verification metadata.
- Remove broad activity keep rules and prove minified release build/launch.
- Remove the unused Wi-Fi string.
- Use separate commits for signer pinning, Gradle verification, R8 rules, and cleanup.

### Task 11: Full local and TV verification

**Files:** Update `docs/EXECUTION_LOG.md` before each delivery step and release metadata/docs where required.

- Run shell syntax checks, debug/release lint, full JVM tests, debug/release builds, and the complete safe connected test suite.
- Build and verify the signed release APK, update-install it without uninstalling, launch it, exercise Home, Settings, build info, shortcut management, focus/navigation, and configuration persistence on the TV.
- Inspect logs for crashes/ANRs. Never send mute/unmute/volume commands.
- Commit release metadata only after verification.

### Task 12: Integrate and shut down

- Rebase the remediation branch on current local `main`; rerun the final verification if the base changed.
- Fast-forward local `main`, confirm the exact commit stack and a clean tree, and do not push.
- Power off the TV only after every check succeeds and confirm its display/power state where ADB permits.
