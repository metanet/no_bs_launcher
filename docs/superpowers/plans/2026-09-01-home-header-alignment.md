# Home Header Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the welcome label, Apps title, and Settings button along one top edge, while aligning the Settings button's right edge to the app grid's rightmost tile column.

**Architecture:** Keep the existing 20/80 two-panel structure and four-column `RecyclerView`. Express the grid's 18dp outer visual inset as a named dimension, apply it to the Settings button, and verify screen-space geometry with a connected Android test.

**Tech Stack:** Android XML layouts, Kotlin, Espresso/ActivityScenario, RecyclerView, Gradle Android plugin.

---

### Task 1: Add the failing screen-geometry test

**Files:**
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/HomeAndSettingsFlowTest.kt`

- [x] **Step 1: Add a focused connected test and screen-bounds helper**

Add `android.graphics.Rect`, then launch Home with four favorites and a visible welcome label. Read the actual global bounds of the views and the fourth favorite tile:

```kotlin
@Test
fun homeAlignsHeaderTopAndSettingsWithRightmostAppColumn() {
    val apps = uniqueCatalogApps(4)
    LayoutStore(context).save(
        completedConfig(selectedPackages = apps.map(AppCandidate::packageName)).copy(
            welcomeText = "Welcome, Basri",
        ),
    )

    ActivityScenario.launch(HomeActivity::class.java).use {
        onView(withId(R.id.app_grid)).check { view, _ ->
            val grid = view as RecyclerView
            val root = view.rootView
            val welcome = root.findViewById<View>(R.id.welcome).screenBounds()
            val appsTitle = root.findViewById<View>(R.id.apps_title).screenBounds()
            val settings = root.findViewById<View>(R.id.settings).screenBounds()
            val rightmostTile = checkNotNull(
                grid.findViewHolderForAdapterPosition(3)?.itemView,
            ).screenBounds()

            assertEquals(welcome.top, appsTitle.top)
            assertEquals(welcome.top, settings.top)
            assertEquals(rightmostTile.right, settings.right)
        }
    }
}

private fun View.screenBounds() = Rect().also(::getGlobalVisibleRect)
```

- [x] **Step 2: Run the focused test to verify RED**

Run:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.basri.android.nobs_launcher.HomeAndSettingsFlowTest#homeAlignsHeaderTopAndSettingsWithRightmostAppColumn
```

Expected: compilation fails because `R.id.apps_title` does not exist yet. This is the expected missing-layout-contract failure before production XML changes.

### Task 2: Implement the minimal layout alignment

**Files:**
- Modify: `app/src/main/res/layout/activity_home.xml`
- Modify: `app/src/main/res/values/dimens.xml`

- [x] **Step 1: Add the named outer-column inset**

Add the grid padding plus tile-margin dimension:

```xml
<dimen name="app_column_edge_inset">18dp</dimen>
```

- [x] **Step 2: Align the header children and Settings edge**

Give the header and Apps title stable IDs, use top gravity, and apply the named end margin:

```xml
<LinearLayout
    android:id="@+id/apps_header"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:baselineAligned="false"
    android:gravity="top"
    android:orientation="horizontal">

    <TextView
        android:id="@+id/apps_title"
        ... />

    <Button
        android:id="@+id/settings"
        android:layout_marginEnd="@dimen/app_column_edge_inset"
        ... />
</LinearLayout>
```

- [x] **Step 3: Run the focused connected test to verify GREEN**

Run the same focused Gradle command from Task 1.

Expected: `BUILD SUCCESSFUL`, with the header top and right-edge assertions passing on the connected Android TV.

### Task 3: Run the full verification and install in place

**Files:**
- Verify: all modified production and connected-test files
- Artifact: `app/build/outputs/apk/release/app-release.apk`

- [x] **Step 1: Run formatting/static checks and inspect the diff**

Run:

```bash
git diff --check
./gradlew lintDebug lintRelease
```

Expected: no whitespace errors and both lint variants succeed.

- [x] **Step 2: Run the complete signed release gate**

Run:

```bash
./scripts/build_release_apk.sh
```

Expected: unit tests, the full connected-device test suite, debug/release builds, signing, and APK certificate verification all succeed.

- [x] **Step 3: Install the signed update without removing app data**

Run:

```bash
./scripts/install_release_apk.sh 192.168.1.154:5555
```

Expected: signature equality is confirmed before `adb install -r`, then the installed package and version are reported.

- [x] **Step 4: Inspect the real 1920x1080 Home screen**

Open Home without sending mute, volume, or power keys. Capture UI hierarchy and screenshot evidence, confirm the three top bounds and two right bounds match, then leave Home displayed.

Expected: visible alignment matches the automated screen-space assertions and audio/power state is untouched.

### Source-control boundary

No commit is part of this plan. The implementation, test, design, and plan files remain uncommitted until Basri separately authorizes a commit.
