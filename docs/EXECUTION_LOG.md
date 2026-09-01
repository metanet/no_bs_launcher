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
