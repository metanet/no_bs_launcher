# System Network Labels and Build Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split network rates into stable one-line rows, replace editable Wi-Fi/location labels with system values and visibility toggles, and expose build identity through a top-row dialog button.

**Architecture:** Pure policies format network rates, normalize SSIDs, and derive a location label from the timezone. A permission-aware Android reader supplies system values to Home on resume; Settings persists visibility only and owns the Build info dialog.

**Tech Stack:** Kotlin, Android framework Wi-Fi/connectivity APIs, SharedPreferences, XML/ViewBinding, JUnit 4, Espresso, Gradle/BuildConfig.

---

### Task 1: Specify the display contracts in failing tests

**Files:**
- Create: `app/src/test/java/dev/basri/android/nobs_launcher/status/SystemLabelPolicyTest.kt`
- Modify: `app/src/test/java/dev/basri/android/nobs_launcher/stats/SystemStatsPolicyTest.kt`
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/HomeAndSettingsFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/ManifestPrivacyTest.kt`

- [x] Add pure assertions that `SystemStatsDisplay` exposes
      `networkIngress = "2.0 KB/s"` and `networkEgress = "1.0 KB/s"`, including
      measuring and unavailable states.
- [x] Add pure assertions that quoted SSIDs normalize, unknown/blank SSIDs are
      rejected, and `Europe/London` maps to `London`.
- [x] Change connected UI assertions to require `network_ingress_stats`,
      `network_egress_stats`, `show_wifi_name`, and `build_info`; require the
      Build info dialog's two-line message and four-button D-pad chain.
- [x] Update the manifest contract to require `ACCESS_WIFI_STATE` and
      `ACCESS_FINE_LOCATION`, and the release identity to 0.4.1/code 9.
- [x] Run the focused unit and Android-test compilation tasks. Confirm failure
      is caused by the new fields, policy, and resource IDs being absent.

### Task 2: Implement network and system-label policies

**Files:**
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/stats/SystemStats.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/stats/SystemStatsPolicy.kt`
- Create: `app/src/main/java/dev/basri/android/nobs_launcher/status/SystemLabelPolicy.kt`

- [x] Replace the combined `network` display value with `networkIngress` and
      `networkEgress`; give both the same measuring/unavailable state and format
      valid rates independently.
- [x] Implement `normalizeWifiSsid` by trimming, rejecting blank and
      `<unknown ssid>`, and removing one matching surrounding quote pair.
- [x] Implement `locationFromTimeZone` by selecting the last nonblank timezone
      path component and replacing underscores with spaces.
- [x] Run the focused policy suites and confirm all pure tests pass.

### Task 3: Read system labels and preserve configuration compatibility

**Files:**
- Create: `app/src/main/java/dev/basri/android/nobs_launcher/status/SystemLabelReader.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/model/LauncherConfig.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/data/LayoutStore.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/androidTest/java/dev/basri/android/nobs_launcher/SystemLabelReaderTest.kt`

- [x] Add `showWifiName: Boolean = true` to configuration and persist it under
      `show_wifi_name`; remove obsolete manual-label keys on save.
- [x] Add prompt-state helpers to `LayoutStore` so Home requests permission
      once automatically and Settings can initiate an explicit retry.
- [x] Read the active Wi-Fi SSID from Android with a permission check and a
      safe legacy fallback; read location from `TimeZone.getDefault().id`.
- [x] Declare Wi-Fi/fine-location permissions and optional Wi-Fi/location
      hardware features.
- [x] Add a permission-granted connected test requiring the Box R reader to
      return `Kahveci House` when device Location is enabled, the explicit
      unavailable value when it is disabled, and a nonblank timezone location.

### Task 4: Replace the Home and Settings views

**Files:**
- Modify: `app/src/main/res/layout/activity_home.xml`
- Modify: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/ui/HomeActivity.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/ui/SettingsActivity.kt`

- [x] Replace the Home network TextView with single-line ingress and egress
      TextViews and bind the independent display fields.
- [x] Bind Wi-Fi and timezone location from `SystemLabelReader`, honor the two
      visibility toggles, and request SSID permission once when required.
- [x] Remove the two editable Settings fields and add the Show Wi-Fi name
      checkbox beside the existing visibility toggles.
- [x] Remove inline build metadata; add Build info between Android settings and
      Save, link the D-pad directions, and open a left-aligned two-line dialog.
- [x] Save only the welcome text, four toggles, and app selection; preserve all
      independently managed shortcut state.
- [x] Run focused unit and connected flow tests until green.

### Task 5: Release and device verification

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `docs/INSTALL_AND_ROLLBACK.md`
- Modify: existing dashboard design docs where they describe manual labels or
  the combined network line

- [x] Bump the app to version 0.4.1/code 9 and update documentation to describe
      system-derived labels, permission behavior, split rates, and Build info.
- [x] Run `./scripts/build_release_apk.sh`; require zero lint findings, both
      complete unit suites, all connected tests, both builds, and signature
      verification.
- [x] Install with `./scripts/install_release_apk.sh 192.168.1.154:5555`, grant
      the approved SSID permission on the development box, and cold-start Home.
- [x] Verify by UI hierarchy and screenshots that timezone is live and SSID
      reflects Android's available/redacted state, ingress/egress each occupy
      one line, Settings has no label editors, Build info opens from the header,
      and Home remains the resolved launcher.
- [x] Keep the TV's audio and power state untouched and leave all work
      uncommitted pending Basri's explicit approval.
