# Favorites, all apps, and CPU utilization implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show favorite apps before an alphabetical all-apps section with TV long-press actions, change Home to a 20/80 split, and provide genuine whole-system CPU utilization through a portable cpuidle fallback.

**Architecture:** Add pure policies for CPU-source selection and Home section composition, then make the RecyclerView render app and full-span separator item types. Home owns favorite mutation, modal actions, move-mode state, and the standard Android uninstall intent; persisted `selectedPackages` remains the favorite-order source of truth.

**Tech Stack:** Kotlin 2.2.0, Android Views/RecyclerView/GridLayoutManager, Android framework AlertDialog/Intent APIs, JUnit 4, AndroidX Test/Espresso.

**Source-control constraint:** Work on `feature/favorites-all-apps-cpu`. Do not commit or amend unless Basri explicitly authorizes it.

---

### Task 1: Add genuine cpuidle CPU utilization fallback through TDD

**Files:**
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/stats/SystemStats.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/stats/SystemStatsParsers.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/stats/SystemStatsPolicy.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/stats/SystemStatsReader.kt`
- Modify: `app/src/test/java/dev/basri/android/nobs_launcher/stats/SystemStatsPolicyTest.kt`
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/SystemStatsReaderTest.kt`

- [x] **Step 1: Write failing utilization tests**

Add tests that require this model and behavior:

```kotlin
data class CpuIdleCounters(
    val idleMicros: Long,
    val capturedAtMillis: Long,
)

assertEquals(
    50,
    SystemStatsPolicy.cpuidleCpuUsagePercent(
        previous = CpuIdleCounters(1_000_000, 1_000),
        current = CpuIdleCounters(3_000_000, 2_000),
        cpuCount = 4,
    ),
)
```

Also require counter reset/non-advancing time to return null, impossible idle deltas to clamp safely, `/proc/stat` deltas to win when both sources exist, cpuidle to be used when proc is absent, a first valid sample to render `measuring…`, and no source to render `utilization unavailable`.

- [x] **Step 2: Run RED**

Run:

```bash
./gradlew testDebugUnitTest --tests '*SystemStatsPolicyTest'
```

Expected: compilation failures for `CpuIdleCounters` and `cpuidleCpuUsagePercent`.

- [x] **Step 3: Implement the minimal CPU source model and policy**

Add `cpuIdleCounters: CpuIdleCounters?` to `RawSystemStats`. Compute:

```kotlin
val capacityMicros = elapsedMillis * 1_000.0 * cpuCount
val busyMicros = (capacityMicros - idleDeltaMicros).coerceAtLeast(0.0)
val percent = (busyMicros / capacityMicros * 100.0).roundToInt().coerceIn(0, 100)
```

Select proc deltas first, then cpuidle deltas. Distinguish `measuring…` from `utilization unavailable` based on whether the current sample exposes either source.

- [x] **Step 4: Read complete per-core cpuidle counters**

In `SystemStatsReader`, read each direct
`/sys/devices/system/cpu/cpuN/cpuidle/state*/time` file off the UI thread
through the existing monitor, sum microseconds across all logical cores, and
attach `SystemClock.elapsedRealtime()`. Require at least one readable state for
every core and reject the entire source if any value is malformed or blocked.
Preserve source-specific `runCatching` so unsupported cpuidle data cannot break
other stats.

- [x] **Step 5: Run GREEN and the complete unit suite**

Run:

```bash
./gradlew testDebugUnitTest --tests '*SystemStatsPolicyTest'
./gradlew test
```

Expected: all tests pass with no failures.

- [x] **Step 6: Strengthen and run the connected reader test**

Require Box R's cpuidle state files to be readable by the untrusted app, require two reader samples to contain increasing idle counters, and require the resulting display to begin with a bounded percentage. Run:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.SystemStatsReaderTest
```

Expected on Box R: CPU display begins with a percentage after the second sample.

### Task 2: Compose Favorites and alphabetical remaining apps through TDD

**Files:**
- Create: `app/src/main/java/dev/basri/android/nobs_launcher/model/HomeAppSections.kt`
- Create: `app/src/test/java/dev/basri/android/nobs_launcher/model/HomeAppSectionsPolicyTest.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/model/LauncherConfig.kt`
- Modify: `app/src/test/java/dev/basri/android/nobs_launcher/model/LauncherConfigPolicyTest.kt`

- [x] **Step 1: Write failing section-policy tests**

Define the wished-for API:

```kotlin
data class HomeAppSections(
    val favorites: List<AppCandidate>,
    val remaining: List<AppCandidate>,
)

val sections = HomeAppSectionsPolicy.compose(
    catalogApps = listOf(alpha, beta, zulu),
    favoritePackages = listOf(zulu.packageName),
)
assertEquals(listOf(zulu), sections.favorites)
assertEquals(listOf(alpha, beta), sections.remaining)
```

Cover favorite-order preservation, missing/duplicate favorite packages, and case-insensitive label plus package-name ordering for remaining apps.

- [x] **Step 2: Run RED**

Run:

```bash
./gradlew testDebugUnitTest --tests '*HomeAppSectionsPolicyTest'
```

Expected: compilation failure because the policy does not exist.

- [x] **Step 3: Implement pure section composition**

Use one package-to-candidate map, map distinct favorite package names through it, and filter/sort the remainder with `Locale.getDefault()` and package-name tie breaking.

- [x] **Step 4: Add favorite add/remove policy tests and implementation**

Exercise existing `LauncherConfigPolicy.setVisible` as favorite add/remove behavior, including append-on-add, no duplicates, order preservation, and removal. Keep preference schema unchanged.

- [x] **Step 5: Run GREEN**

Run:

```bash
./gradlew testDebugUnitTest --tests '*HomeAppSectionsPolicyTest' --tests '*LauncherConfigPolicyTest'
```

Expected: all focused policy tests pass.

### Task 3: Render the 20/80 multi-section grid test-first

**Files:**
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/HomeAndSettingsFlowTest.kt`
- Modify: `app/src/main/res/layout/activity_home.xml`
- Create: `app/src/main/res/layout/item_app_separator.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/ui/AppGridAdapter.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/ui/HomeActivity.kt`
- Modify: `app/src/main/res/layout/activity_settings.xml`

- [x] **Step 1: Write failing connected layout and section tests**

Replace the old selected-only assertion with tests that require:

```kotlin
assertEquals(second.label, adapter item 0 contentDescription)
assertEquals(first.label, adapter item 1 contentDescription)
assertEquals(R.id.app_separator, adapter item 2 id)
assertEquals(alphabeticallyFirstRemaining.label, adapter item 3 contentDescription)
assertEquals(4, gridLayoutManager.spanSizeLookup.getSpanSize(2))
```

Add `info_panel` and `apps_panel` IDs and assert their measured widths are within one pixel of 20% and 80% of their combined width. Assert the separator is absent when either section is empty and is not focusable.

- [x] **Step 2: Run connected RED**

Run the `HomeAndSettingsFlowTest` class and confirm failures are caused by missing panel IDs, missing separator, and remaining apps being absent.

- [x] **Step 3: Implement explicit adapter items**

Use:

```kotlin
sealed interface HomeGridItem {
    data class App(val candidate: AppCandidate, val favorite: Boolean) : HomeGridItem
    data object Separator : HomeGridItem
}
```

Support app and separator view types, package lookup, adapter-position lookup, stable IDs, and `spanSizeAt(position)` returning four only for the separator. The separator layout contains a one-dp `panel_divider` line and is non-focusable.

- [x] **Step 4: Bind both sections and 20/80 weights**

Give the Home child panels IDs, change their weights from 3/7 to 1/4, compose sections on every resume, and remove the obsolete empty-state behavior because launchable remaining apps are always shown when present. Configure `GridLayoutManager.SpanSizeLookup` from the adapter.

- [x] **Step 5: Relabel Settings**

Change app-list wording from visibility semantics to Favorites semantics without changing storage or checkbox behavior.

- [x] **Step 6: Run connected GREEN and unit regression tests**

Run:

```bash
./gradlew test connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.HomeAndSettingsFlowTest
```

Expected: all unit tests and the focused Home/Settings connected tests pass.

### Task 4: Add TV long-press actions and linear move mode through TDD

**Files:**
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/HomeAndSettingsFlowTest.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/ui/AppGridAdapter.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/ui/HomeActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [x] **Step 1: Write failing connected menu tests**

Require a long-pressed remaining app to show exactly `Add to favorites` and `Uninstall app`. Require a favorite to show `Move`, `Remove from favorites`, and `Uninstall app`, with no direct Move left/Move right items.

- [x] **Step 2: Run menu RED**

Run the focused connected class and confirm the long press enters the old move mode instead of showing the required dialogs.

- [x] **Step 3: Implement modal actions and persistence**

Pass favorite state through the adapter long-click callback. Build an `AlertDialog` item list per section. Add appends through `LauncherConfigPolicy.setVisible`, remove uses the same policy with false, both commit immediately, rebind sections, and restore focus to the package.

Start uninstall through:

```kotlin
Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
```

Catch an unavailable handler and show an in-app error without changing Favorites.

- [x] **Step 4: Write failing move-mode tests**

From the favorite dialog choose Move, then require Right to shift the moving package through the linear favorite sequence even from index 3 to 4, OK to persist, and Back to restore. Require Up/Down to leave order unchanged.

- [x] **Step 5: Run move RED**

Expected failures: direct long press enters move mode, right-row wrapping is blocked, and up/down currently reorder by four.

- [x] **Step 6: Implement linear favorite-only movement**

Keep `favoriteApps`, `remainingApps`, and a favorite-package snapshot separately. During move mode consume Left/Right as deltas -1/+1 without row-wrap prevention, consume Up/Down without mutation, save only favorite package order on OK, and reconstruct Favorites from the snapshot on Back. Separator and remaining apps never participate.

- [x] **Step 7: Verify uninstall confirmation without deleting an app**

Register a blocking `Instrumentation.ActivityMonitor` with an
`IntentFilter(Intent.ACTION_DELETE)` and a canceled `ActivityResult`. Select
Uninstall, assert the monitor records exactly one matching launch, remove the
monitor in `finally`, and confirm the package and favorite state remain.

- [x] **Step 8: Run focused and full automated GREEN**

Run:

```bash
./gradlew test
./gradlew connectedCheck
```

Expected: all local variants and all connected tests pass.

### Task 5: Release, in-place install, and physical TV verification

**Files:**
- Modify: `docs/EXECUTION_LOG.md`
- Modify: `docs/INSTALL_AND_ROLLBACK.md` only if user-facing behavior needs clarification
- Generate ignored artifact: `app/build/outputs/apk/release/app-release.apk`

- [x] **Step 1: Run the complete release gate**

Run:

```bash
scripts/build_release_apk.sh
```

This must pass zero-warning `lintDebug lintRelease`, both unit variants, full `connectedCheck`, debug/release assembly, R8/resource shrinking, and signature verification.

- [x] **Step 2: Inspect release privacy and identity**

Verify exact package/label/version, sole `ACCESS_NETWORK_STATE` permission, no Internet/uninstall permission, release signature continuity, and APK size.

- [x] **Step 3: Install without clearing data**

Run:

```bash
scripts/install_release_apk.sh 192.168.1.154:5555
```

The script must use `adb install -r`, confirm the installed certificate first, and preserve existing welcome labels, toggles, favorite order, and Home role.

- [x] **Step 4: Exercise the physical TV UI**

Confirm 20/80 proportions and non-overflowing information text; a real CPU percentage after two samples; Favorites, separator, and alphabetical remainder; long-press menus; add/remove favorite; Move across a row boundary with OK persistence and Back rollback; uninstall confirmation cancellation; selected and remaining app launch; Settings; and Home return.

- [x] **Step 5: Run post-install safety checks**

Confirm the new launcher remains resolved/resumed Home, Google TV Home remains available for documented rollback, the sole permission remains unchanged, no app-owned sockets/crash/ANR appear, and no audio controls are changed.

- [x] **Step 6: Update the execution log and report**

Record RED/GREEN evidence, release digest/size, install result, physical UI evidence, autonomous decisions, and any device limitation. Leave changes uncommitted on the feature branch unless Basri separately authorizes a commit.
