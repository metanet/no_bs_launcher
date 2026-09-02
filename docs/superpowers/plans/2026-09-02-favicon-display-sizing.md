# Favicon Display Sizing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make downloaded small favicons occupy the existing icon viewport on Home and Web shortcuts management without cropping or changing normal app artwork.

**Architecture:** Keep favicon files at their source/downsampled dimensions and fix presentation at bind time. A real downloaded favicon uses `ImageView.ScaleType.FIT_CENTER`; missing favicons and normal app artwork use the existing `CENTER_INSIDE` behavior so recycled Home holders cannot leak sizing policy between item types.

**Tech Stack:** Kotlin, Android Views/ViewBinding, RecyclerView, Espresso connected tests.

---

### Task 1: Reproduce small-favicon rendering

**Files:**
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/HomeAndSettingsFlowTest.kt`

- [x] Add one test that stores a 16x16 favicon, opens Home, and asserts that the drawable's transformed maximum dimension fills the shorter `app_artwork` viewport axis.
- [x] Add one test that opens Web shortcuts management with the same 16x16 favicon and makes the equivalent `shortcut_icon` assertion.
- [x] Run both tests on Box R and confirm they fail because `centerInside` leaves the rendered drawable at its tiny intrinsic size.

### Task 2: Bind downloaded favicons with aspect-preserving enlargement

**Files:**
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/ui/AppGridAdapter.kt`
- Modify: `app/src/main/java/dev/basri/android/nobs_launcher/ui/WebShortcutsAdapter.kt`

- [x] Set `FIT_CENTER` when a downloaded favicon is present.
- [x] Explicitly restore `CENTER_INSIDE` for Home app artwork and both fallback-icon paths so RecyclerView reuse is deterministic.
- [x] Rerun the two focused tests and confirm both pass.

### Task 3: Verify and deploy

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `docs/EXECUTION_LOG.md`
- Modify: `docs/INSTALL_AND_ROLLBACK.md`

- [x] Bump the release to 0.3.2/code 6 and record the follow-up.
- [x] Run debug/release lint, both full unit variants, the complete connected suite, debug/release builds, R8/resource shrinking, and signing checks.
- [x] Install only with the guarded in-place `adb install -r` script.
- [x] Cold-launch Home and visually verify the existing cached `Osuruk tv` favicon is enlarged while configuration and Home ownership remain unchanged and no power/audio command is sent.
- [x] Do not commit: Basri has not authorized a new commit for this follow-up.
