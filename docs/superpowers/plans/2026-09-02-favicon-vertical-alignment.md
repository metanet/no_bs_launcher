# Favicon Vertical Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Match Home favicon height to regular TV app artwork while keeping every tile and label vertically aligned.

**Architecture:** Retain per-item aspect-preserving scale types, but make Home's shared `app_artwork` content area approximately 16:9 by applying an 18dp top/bottom inset. Prove the result by comparing the rendered height of a 16px favicon with a real landscape app banner in the same grid.

**Tech Stack:** Android XML, Kotlin, Espresso connected tests, Gradle Android lint/build.

---

### Task 1: Add the alignment regression

**Files:**
- Modify: `app/src/androidTest/java/dev/basri/android/nobs_launcher/HomeAndSettingsFlowTest.kt`

- [x] Replace the previous full-viewport Home assertion with a test that places a small favicon beside a uniquely labelled landscape app.
- [x] Measure each drawable after its `ImageView` matrix and require their rendered heights to differ by no more than two pixels.
- [x] Run the focused test on Box R and confirm it fails because the favicon is taller.

### Task 2: Constrain the shared Home artwork band

**Files:**
- Modify: `app/src/main/res/layout/item_app_tile.xml`
- Modify: `app/src/main/res/values/dimens.xml`

- [x] Add a named 18dp Home artwork vertical inset.
- [x] Apply it as equal top/bottom padding to the shared Home artwork view.
- [x] Rerun both favicon display tests and confirm Home alignment plus management sizing pass.

### Task 3: Verify and deploy

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `docs/EXECUTION_LOG.md`
- Modify: `docs/INSTALL_AND_ROLLBACK.md`

- [x] Bump to 0.3.3/code 7 and update the manifest identity regression.
- [x] Run zero-warning debug/release lint, both full unit variants, all connected tests, builds, shrinking, and signing.
- [x] Guarded-install in place, verify the installed hash, and capture a cold Home screenshot proving favicon/app vertical alignment.
- [x] Preserve user data and Home ownership; send no power or audio commands.
- [x] Leave changes uncommitted unless Basri explicitly authorizes a commit.
