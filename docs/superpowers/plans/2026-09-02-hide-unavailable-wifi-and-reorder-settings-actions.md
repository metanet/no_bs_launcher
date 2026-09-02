# Hide Unavailable Wi-Fi and Reorder Settings Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hide the home Wi-Fi row when Android does not return an SSID and order the settings actions as Save, Web Shortcuts, Android Settings, Build info.

**Architecture:** Keep `SystemLabelReader` as the sole SSID source and make `HomeActivity` derive both the Wi-Fi text and visibility from the same nullable result. Reorder the existing settings buttons in XML and update their explicit TV D-pad focus graph to match the visual sequence.

**Tech Stack:** Kotlin, Android Views/Data Binding, Espresso connected tests, Gradle Android plugin.

---

### Task 1: Hide the unavailable Wi-Fi row

**Files:**
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/HomeAndSettingsFlowTest.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/ui/HomeActivity.kt`

- [x] **Step 1: Write the failing connected UI assertion**

Replace the Wi-Fi assertion in `completedSetupShowsStableHomeControls` with a
branch based on the real `SystemLabelReader` result:

```kotlin
val wifiName = SystemLabelReader(context).wifiName()
if (wifiName == null) {
    onView(withId(R.id.wifi)).check(matches(withEffectiveVisibility(GONE)))
} else {
    onView(withId(R.id.wifi))
        .check(matches(isDisplayed()))
        .check(matches(withText(wifiName)))
}
```

- [x] **Step 2: Run the test and verify RED**

Run:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.HomeAndSettingsFlowTest#completedSetupShowsStableHomeControls
```

Expected on the current Box R with Location disabled: FAIL because the Wi-Fi view
is `VISIBLE` while the assertion requires `GONE`.

- [x] **Step 3: Implement the minimal visibility rule**

In `HomeActivity.bindHome`, replace the independent text and preference-only
visibility assignment with:

```kotlin
val wifiName = systemLabelReader.wifiName()
binding.wifi.text = wifiName.orEmpty()
binding.wifi.visibility = if (currentConfig.showWifiName && wifiName != null) {
    View.VISIBLE
} else {
    View.GONE
}
```

- [x] **Step 4: Run the focused test and verify GREEN**

Run the same connected test command. Expected: PASS with the Wi-Fi row absent on
the current Box R. Existing `SystemLabelPolicyTest` continues to cover normalization
of any SSID Android exposes, while `SystemLabelReaderTest` exercises the device API.

### Task 2: Reorder the settings actions and focus graph

**Files:**
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/HomeAndSettingsFlowTest.kt`
- Modify: `app/src/main/res/layout/activity_settings.xml`

- [x] **Step 1: Write failing order and focus assertions**

In `settingsKeepsQuickActionsInOneTopRowAndOpensBuildInformation`, begin keyboard
navigation at Save and assert the new chain:

```kotlin
assertTrue(settings.findViewById<View>(R.id.save).requestFocusFromTouch())
onView(withId(R.id.save)).check(matches(hasFocus()))
sendDpadKey(KeyEvent.KEYCODE_DPAD_RIGHT)
onView(withId(R.id.web_shortcuts)).check(matches(hasFocus()))
sendDpadKey(KeyEvent.KEYCODE_DPAD_RIGHT)
onView(withId(R.id.open_android_settings)).check(matches(hasFocus()))
sendDpadKey(KeyEvent.KEYCODE_DPAD_RIGHT)
onView(withId(R.id.build_info)).check(matches(hasFocus()))
```

Add geometry and focus-link assertions:

```kotlin
assertTrue(save.screenBounds().left < webShortcuts.screenBounds().left)
assertTrue(webShortcuts.screenBounds().left < androidSettings.screenBounds().left)
assertTrue(androidSettings.screenBounds().left < buildInfo.screenBounds().left)
assertEquals(R.id.web_shortcuts, save.nextFocusRightId)
assertEquals(R.id.save, webShortcuts.nextFocusLeftId)
assertEquals(R.id.open_android_settings, webShortcuts.nextFocusRightId)
assertEquals(R.id.web_shortcuts, androidSettings.nextFocusLeftId)
assertEquals(R.id.build_info, androidSettings.nextFocusRightId)
assertEquals(R.id.open_android_settings, buildInfo.nextFocusLeftId)
assertEquals(R.id.save, settings.findViewById<View>(R.id.welcome_text).nextFocusUpId)
```

- [x] **Step 2: Run the test and verify RED**

Run:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.HomeAndSettingsFlowTest#settingsKeepsQuickActionsInOneTopRowAndOpensBuildInformation
```

Expected: FAIL because the existing visual and focus order starts with Web
Shortcuts and ends with Save.

- [x] **Step 3: Reorder buttons and focus links**

Move the existing `<Button>` blocks in `activity_settings.xml` into this order and
set the horizontal focus IDs as shown:

```xml
<Button
    android:id="@+id/save"
    android:nextFocusRight="@id/web_shortcuts" />
<Button
    android:id="@+id/web_shortcuts"
    android:layout_marginStart="18dp"
    android:nextFocusLeft="@id/save"
    android:nextFocusRight="@id/open_android_settings" />
<Button
    android:id="@+id/open_android_settings"
    android:layout_marginStart="18dp"
    android:nextFocusLeft="@id/web_shortcuts"
    android:nextFocusRight="@id/build_info" />
<Button
    android:id="@+id/build_info"
    android:layout_marginStart="18dp"
    android:nextFocusLeft="@id/open_android_settings" />
```

Retain every button's current size, background, label, padding, Down target, and
click behavior. Change the welcome-text field to `android:nextFocusUp="@id/save"`.

- [x] **Step 4: Run the focused test and verify GREEN**

Run the same connected test command. Expected: PASS with both the visual geometry
and controller-navigation sequence matching the requested order.

### Task 3: Full verification and device smoke test

**Files:**
- Verify all modified files and generated release artifact.

- [x] **Step 1: Run whitespace validation**

```bash
git diff --check
```

Expected: no output and exit code 0.

- [x] **Step 2: Run lint, complete tests, and builds**

```bash
./scripts/build_release_apk.sh
```

Expected: debug/release lint passes, debug/release unit tests pass, all connected
tests pass on Box R, debug/release builds succeed, and the signed release APK is
produced.

- [x] **Step 3: Install the release as an in-place update and smoke-test**

```bash
./scripts/install_release_apk.sh 192.168.1.154:5555
```

Expected: the signing certificate matches, `adb install -r` succeeds without
clearing launcher state, and package version 0.4.1 remains installed. Launch Home
and Settings, verify that Wi-Fi is absent while Android Location is disabled, and
verify the four settings buttons and D-pad focus order.

- [x] **Step 4: Pause for an explicit integration decision**

Report the changed files, exact verification evidence, and worktree location. Do
not commit or merge until Basri explicitly authorizes it. Basri subsequently chose
the local-merge option, authorizing the required commits and integration.
