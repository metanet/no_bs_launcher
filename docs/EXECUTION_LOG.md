# Autonomous execution log

## 2026-09-01 - No bullshit launcher dashboard

- Succeeded: completed and verified the original portable launcher baseline before
  beginning the requested rename and dashboard expansion.
- Succeeded: renamed the product to `No bullshit launcher` and the package to
  `dev.basri.android.nobs_launcher` without changing the signing identity.
- Succeeded: added welcome text, independent location/VPN/system-stat switches,
  provider-neutral VPN status, live best-effort system statistics, and the
  consolidated Settings screen using test-first changes.
- Succeeded: ran zero-warning debug/release lint, all local tests, all 12
  connected-device tests, debug/release assembly, shrinking, and signature
  verification through `scripts/build_release_apk.sh`.
- Failed, diagnosed, fixed: the guarded installer exited while checking a package
  that did not exist under the new application ID. The absent-package probe now
  handles that expected state; signed release installation then succeeded.
- Succeeded: configured the physical Box R with the welcome/Wi-Fi/location
  labels, all optional panels enabled, and eight selected apps.
- Failed, diagnosed, resolved reversibly: Android assigned the Home role to the
  new launcher but continued resolving Google TV Home because the system
  activity has higher priority. Disabling only `com.google.android.tvlauncher`
  for user 0 made the selected Home effective. Re-enabling it restored stock
  Home during the rollback test, after which No bullshit launcher was restored.
- Succeeded: exercised remote focus, Settings/Back, panel switches, Android
  Settings, selected app launches, Home return, and move save/cancel on the
  release build. Plain ADB key injection identifies as a keyboard; D-pad-source
  injection was required to mirror the TV remote accurately in move mode.
- Succeeded: confirmed configuration survives launcher process death/restart,
  the sole permission is `ACCESS_NETWORK_STATE`, the launcher owns no network
  sockets, and no launcher crash/ANR was recorded.
- Succeeded: confirmed Wi-Fi, Bluetooth HID, Cast, phone remote, HDMI-CEC, and
  Widevine services remain active. The VPN was disconnected, so the generic VPN
  line correctly remained hidden; connected wording/behavior is covered by the
  automated policy and device UI tests.
- Decision: did not reboot while unattended because this box resets wireless
  debugging after reboot; doing so would lose the only control path before the
  required standby action. Process-restart persistence was verified instead.
- Succeeded: updated installation/rollback documentation with the exact Box R
  workaround, verified release identity, and generic guidance for other TVs.
- Succeeded: final evidence confirms the signed 208,794-byte APK, exact package
  and label, 50/50 unit-variant tests, 12/12 connected tests, zero-warning lint,
  30,364 KB launcher PSS after restart, 1,560,548 KB available system memory,
  selected Home role/component, and no launcher crash/ANR.
- Succeeded: sent exactly one Android power key. The box changed from Awake to
  Asleep, HDMI-CEC power changed from On to Standby, and the acknowledged CEC
  log records a Standby message at 01:10:17. No audio key was sent.
- Remaining: deliver the final report; no device work remains.

## 2026-09-01 - Favorites, all apps, and CPU utilization release 0.2.0

- Succeeded: changed Home to a measured 20/80 information/app split, retained
  Favorites in their stored order, added a full-width non-focusable separator,
  and listed every remaining launchable app alphabetically below it.
- Failed, diagnosed, fixed: Android 14 denied both `/proc/stat` and the cgroup
  CPU counters to the untrusted app. Standard per-core
  `cpuidle/state*/time` files were readable, so the launcher now derives genuine
  whole-system utilization from their idle-residency deltas without a new
  permission or vendor API. `/proc/stat` remains the preferred source where it
  is available.
- Failed, diagnosed, fixed: the first 20/80 connected layout test exposed
  vertical text overflow in the narrower information panel. Compact type sizes
  and spacing now fit every information row at 1920x1080.
- Succeeded: added favorite and remaining-app long-press menus. Favorites offer
  Move, Remove from favorites, and Uninstall app; remaining apps offer Add to
  favorites and Uninstall app. Uninstall launches Android's standard package
  deletion activity and never deletes silently.
- Succeeded: replaced direct Move left/right menu commands with one Move mode.
  Physical remote Left/Right shifts the selected favorite linearly across row
  boundaries, OK persists, Back restores the snapshot, and Up/Down are consumed
  without reordering.
- Failed, diagnosed, fixed: the RecyclerView item animator briefly kept both
  old and new stable-ID tiles attached after a Move reorder, which made a
  faster-than-human connected test match the same app twice. The test now ends
  grid animations before its next synthetic long-press without changing the
  production animation or weakening the behavior assertion.
- Succeeded: `scripts/build_release_apk.sh` passed zero-warning debug/release
  lint, both unit-test variants, all 15 connected tests on Box R, debug/release
  assembly, R8/resource shrinking, and signature verification.
- Succeeded: built the signed 213,888-byte release APK as version 0.2.0
  (`versionCode=2`) with certificate SHA-256
  `d1702f54c1ba471b3a719c89dd8b60bc5e5f2445364c155027761c75f8a9cd88`.
  The APK still requests only `ACCESS_NETWORK_STATE`.
- Succeeded: installed 0.2.0 with the guarded `adb install -r` workflow. The
  original first-install timestamp, welcome/Wi-Fi/location values, panel
  settings, Home role, and eight-favorite order were preserved.
- Succeeded: physical release smoke tests covered Home and Settings, favorite
  and remaining app launches, both long-press menus, add/remove restoration,
  real CPU percentages, alphabetical scrolling, the Android uninstall activity
  without confirming deletion, and remote Move shift/Back rollback.
- Failed safely, restored: an initial touch-based Move smoke attempt acted on
  the currently scrolled remaining app instead of a focused favorite and added
  BrowseHere to Favorites. It was immediately removed again; final device
  evidence confirms the original eight favorites and order exactly.
- Succeeded: final checks show No bullshit launcher resumed as Home, stock
  `com.google.android.tvlauncher` still installed and disabled for reversible
  rollback, no crash/ANR exit, and only the expected VPN network-listener
  registration. No volume, mute, or power command was sent.
- Remaining: source changes are intentionally uncommitted on
  `feature/favorites-all-apps-cpu`; Basri did not authorize another commit.
