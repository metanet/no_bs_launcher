# Uninstall Confirmation Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow the launcher to open Android's standard uninstall confirmation for removable applications.

**Architecture:** Keep the existing `ACTION_DELETE` package intent and Android-owned confirmation flow. Add the normal manifest permission required by the target SDK, protect the contract with a connected permission test, and publish the combined pending work as version 0.2.1.

**Tech Stack:** Android manifest, Kotlin instrumentation tests, Espresso/ActivityScenario, Gradle Android plugin, ADB.

---

### Task 1: Add the failing manifest-permission regression test

**Files:**
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/HomeAndSettingsFlowTest.kt`

- [x] **Step 1: Add the permission imports and focused test**

Add these imports:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
```

Add this test beside the existing uninstall-intent test:

```kotlin
@Test
fun launcherCanRequestPackageDeletion() {
    assertEquals(
        PackageManager.PERMISSION_GRANTED,
        context.packageManager.checkPermission(
            Manifest.permission.REQUEST_DELETE_PACKAGES,
            context.packageName,
        ),
    )
}
```

- [x] **Step 2: Run the focused connected test to verify RED**

Run:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.HomeAndSettingsFlowTest#launcherCanRequestPackageDeletion
```

Expected: the assertion fails with expected permission value `0` and actual
value `-1`, proving the launcher does not currently hold the permission.

### Task 2: Declare the permission and publish version 0.2.1

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/ManifestPrivacyTest.kt`

- [x] **Step 1: Add the normal uninstall-request permission**

Add the permission next to the existing network-state permission:

```xml
<uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />
```

- [x] **Step 2: Run the focused connected test to verify GREEN**

Run:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.HomeAndSettingsFlowTest#launcherCanRequestPackageDeletion
```

Expected: `BUILD SUCCESSFUL`, with the connected permission assertion passing.

- [x] **Step 3: Keep the privacy permission allowlist exact**

Rename the privacy test to `productionPackageHasOnlyApprovedNormalPermissions`
and assert exactly this set:

```kotlin
setOf(
    Manifest.permission.ACCESS_NETWORK_STATE,
    Manifest.permission.REQUEST_DELETE_PACKAGES,
)
```

- [x] **Step 4: Bump the release identity**

Update the existing values in `defaultConfig`:

```kotlin
versionCode = 3
versionName = "0.2.1"
```

### Task 3: Run the complete release gate

**Files:**
- Verify: all modified production, test, plan, and specification files
- Artifact: `app/build/outputs/apk/release/app-release.apk`

- [x] **Step 1: Run diff and lint checks**

Run:

```bash
git diff --check
./gradlew lintDebug lintRelease
```

Expected: no whitespace errors and both lint variants succeed.

- [x] **Step 2: Run every unit, connected, build, shrinking, and signing check**

Run:

```bash
./scripts/build_release_apk.sh
```

Expected: the full unit suite, all connected-device tests, debug/release builds,
R8/resource shrinking, APK signing, and certificate verification succeed.

### Task 4: Install safely and verify both real confirmation flows

**Files:**
- Install: `app/build/outputs/apk/release/app-release.apk`

- [x] **Step 1: Install without removing application data**

Run:

```bash
./scripts/install_release_apk.sh 192.168.1.154:5555
```

Expected: the script verifies signing-key equality, uses `adb install -r`, and
reports installed version `0.2.1`.

- [x] **Step 2: Verify the installed permission**

Run:

```bash
adb -s 192.168.1.154:5555 shell dumpsys package \
  dev.basri.android.nobs_launcher | \
  rg -A 8 "requested permissions:|install permissions:"
```

Expected: `android.permission.REQUEST_DELETE_PACKAGES` appears as requested and
granted.

- [x] **Step 3: Exercise Disney+ through the launcher and cancel**

Open Home, navigate to Disney+, long-press it, select **Uninstall app**, and
confirm that `com.google.android.packageinstaller` is the resumed activity:

```bash
adb -s 192.168.1.154:5555 shell dumpsys activity activities | \
  rg -m 1 "topResumedActivity"
```

Press Back once to cancel, then verify the package remains:

```bash
adb -s 192.168.1.154:5555 shell input keyevent KEYCODE_BACK
adb -s 192.168.1.154:5555 shell pm path com.disney.disneyplus
```

Expected: the Android confirmation screen opens and Disney+ still has a package
path after cancellation.

- [x] **Step 4: Exercise World Radios through the launcher and cancel**

Repeat the launcher interaction for World Radios, confirm the package installer
is resumed, press Back once, and verify the package remains:

```bash
adb -s 192.168.1.154:5555 shell dumpsys activity activities | \
  rg -m 1 "topResumedActivity"
adb -s 192.168.1.154:5555 shell input keyevent KEYCODE_BACK
adb -s 192.168.1.154:5555 shell pm path com.digitalapps.worldradios
```

Expected: the Android confirmation screen opens and World Radios still has a
package path after cancellation.

- [x] **Step 5: Leave the launcher on Home**

Run:

```bash
adb -s 192.168.1.154:5555 shell am start -W \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME
```

Expected: `dev.basri.android.nobs_launcher/.ui.HomeActivity` is resumed. Do not
send mute, volume, or power commands.

### Source-control boundary

No commit is part of this plan. All current alignment and uninstall-fix changes
remain uncommitted until Basri separately authorizes a commit.
