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

## 2026-09-02 - Web shortcuts release 0.3.0

- Succeeded: implemented vendor-neutral web shortcuts with stable mixed app/web
  IDs, HTTP(S)-only URL normalization, Settings-based add/edit/remove, mixed
  favorites and Move mode, default-browser launch, and private bounded favicon
  storage with a built-in globe fallback.
- Succeeded: the whole-project development gate passed zero-warning
  debug/release lint, both unit-test variants, all 25 connected tests on Box R,
  debug/release assembly, R8, and resource shrinking.
- Succeeded: recorded the device before update. Box R is connected at
  `192.168.1.154:5555`, release 0.2.1 (`versionCode=3`) is installed with first
  install time `2026-09-01 00:54:10`, and No bullshit launcher is both the
  selected and resumed Home.
- Succeeded: captured the preserved configuration before update: `Welcome
  Basri`, Wi-Fi `Kahveci House`, location `London`, and visible order YouTube,
  000Player, Jellyfin, NordVPN, StreamVault, Play Store, BrowseHere, CPU Info,
  Internet Speed Test, Netflix.
- Pending: build and verify the signed 0.3.0 release, install it in place,
  exercise and remove a temporary shortcut on the release app, verify preserved
  state, and leave the launcher Home visible. No audio or power action is
  planned.
- Succeeded: `scripts/build_release_apk.sh` repeated zero-warning debug/release
  lint, both unit variants, all 25 connected tests, both builds, R8/resource
  shrinking, and signing in 1m12s. The resulting 231,417-byte APK identifies as
  `dev.basri.android.nobs_launcher` 0.3.0 (`versionCode=4`), verifies with APK
  Signature Scheme v1/v2, and has certificate SHA-256
  `d1702f54c1ba471b3a719c89dd8b60bc5e5f2445364c155027761c75f8a9cd88`.
- Failed harmlessly, diagnosed, fixed: a redundant archive-inspection command
  first assumed the default macOS Android SDK location. This repository uses
  the configured Homebrew SDK under `/opt/homebrew/share/android-commandlinetools`;
  rerunning `apksigner` and `aapt2` from that path independently confirmed the
  signature, package, and version.
- Pending: install the verified APK in place and perform the release smoke test.
- Succeeded: `scripts/install_release_apk.sh 192.168.1.154:5555` compared the
  installed and release certificates, used only `adb install -r`, and installed
  0.3.0 (`versionCode=4`). The original first-install timestamp remains
  `2026-09-01 00:54:10`, confirming an in-place update rather than reinstall.
- Succeeded: cold-launched the release Home and confirmed the selected launcher
  resumed. The welcome, Wi-Fi, and location values remain `Welcome Basri`,
  `Kahveci House`, and `London`; scrolling the grid confirmed the preserved
  order continues through YouTube, 000Player, Jellyfin, NordVPN, StreamVault,
  Play Store, BrowseHere, CPU Info, Internet Speed Test, Netflix, Prime Video,
  SoundPair, Third Party Notices, and VLC.
- Pending: create, open, favorite, unfavorite, and remove a unique temporary web
  shortcut on the installed release, then restore Home and record final state.
- Failed safely, expected security boundary confirmed: Android rejected a shell
  attempt to start the non-exported Web shortcuts activity directly. The smoke
  test will enter it through the launcher's visible Settings controls, as a real
  user must; no manifest exposure was added.
- Failed safely, diagnosed: the TV's soft keyboard reflowed the add dialog, so
  a fixed-coordinate tap intended for the URL field left focus in the name
  field. UI inspection caught the concatenated value before Save; the dialog
  will be cancelled and retried with the keyboard closed between fields. No
  shortcut or preference was written.
- Succeeded: created temporary shortcut `NOBS_TEMP_0902` for
  `https://example.com/nobs-0902` through the installed release UI. Home showed
  it in alphabetical order between Netflix and Prime Video as a normal-sized
  tile with the built-in globe fallback.
- Succeeded: tapping the tile resumed the device's default BrowseHere activity.
  Android's activity record captured the exact
  `ACTION_VIEW dat=https://example.com/nobs-0902` intent.
- Succeeded: long-press offered Add to favorites, Edit shortcut, and Remove
  shortcut; after adding it, the tile moved above the divider and its favorite
  menu offered Move, Remove from favorites, Edit shortcut, and Remove shortcut.
  Removing it from favorites returned it to its exact alphabetical position.
- Succeeded: confirmed Remove shortcut, then verified the tile disappeared from
  Home and the management screen returned to `No web shortcuts yet.` Existing
  app ordering remained intact.
- Expected verification boundary: Android denied shell access to the release
  app's private favicon directory. This real shortcut used the globe fallback,
  so it created no favicon file; deletion of an actually stored private favicon
  is verified separately by the passing connected `FaviconRepositoryTest`.
- Pending: remove shell-generated UI-dump artifacts, run final repository and
  installed-package checks, and leave release Home resumed. No audio or power
  command has been sent.
- Succeeded: final device check left 0.3.0 as selected and resumed Home with the
  preserved welcome/Wi-Fi/location values and original favorites visible. The
  installed package requests exactly `ACCESS_NETWORK_STATE`, `INTERNET`, and
  `REQUEST_DELETE_PACKAGES`; no launcher crash or ANR appeared. All temporary
  shell UI dumps and the local smoke-test screenshot were removed.
- Review found, reproduced by data-flow inspection before final handoff:
  Settings keeps a whole-config snapshot while Web shortcuts persists changes
  independently, so pressing Settings Save can overwrite confirmed shortcut
  additions/edits/removals. The same whole-config load/save pattern lets an
  asynchronous favicon completion overwrite a concurrent main-thread change.
- Review found: favicon bounds accept 8192x8192 but decode the full bitmap before
  scaling, allowing a small compressed image to allocate roughly 256 MiB. The
  connected shortcut-management tests also use clicks rather than proving the
  required D-pad focus path.
- Pending: add failing regressions, fix each root cause, repeat the full signed
  release workflow, and replace this pending state with final evidence.
- Failed during RED setup: the first focused stale-Settings regression did not
  reach its intended assertion because Espresso did not observe the Add button
  after the Settings navigation click. The existing management test uses an
  explicit displayed-state check on both sides of that transition; the new test
  will match that proven synchronization sequence before diagnosing production
  behavior. No production code has changed.
- Failed again before the intended assertion, diagnosed from the captured view
  hierarchy: Settings remained foreground with its initial welcome `EditText`
  focused. On a fresh focused run the TV IME intercepts the bottom-row touch,
  whereas suite ordering had hidden it in the older test. The regression will
  explicitly close the keyboard before selecting Web shortcuts.
- Failed a third time at the same touch-navigation boundary even after explicit
  IME dismissal. Per the debugging stop rule, the state regression will no
  longer depend on that unrelated flaky touch route: it will start the internal
  management activity from the live Settings instance, keeping the stale
  snapshot in memory, while a separate D-pad test covers real focus navigation.
- Corrected test edit: inspection after the next run showed the generic patch
  had matched the adjacent existing management test, so the new regression was
  still exercising the old touch route. The older test is restored and the
  deterministic activity launch is now scoped to the new regression only.
- Unexpected GREEN, traced to lifecycle behavior: starting the management
  activity through `ActivityScenario` recreated Settings on return, so its
  working copy was refreshed and did not reproduce the retained-instance race.
  The regression is narrowed to the actual problematic interleaving: mutate
  persisted shortcut/favorite state after a live Settings instance captures its
  copy, then invoke Save on that same instance.
- RED confirmed: the retained Settings test lost the concurrently saved
  shortcut exactly as predicted. After the first atomic-update implementation,
  Kotlin compilation rejected a nullable removal-result dereference in
  `WebShortcutService`; the update can succeed only when that captured shortcut
  is non-null, so the code will bind an explicit non-null local before cleanup.
- Succeeded: Settings Save now overlays only Settings-owned fields and selected
  app membership onto current persisted shortcut/web-favorite state. Its
  connected retained-instance regression is green.
- Succeeded: added a process-wide atomic `LayoutStore.update` boundary and moved
  shortcut save/remove/icon attachment, Home normalization/favorite/move, and
  Settings Save mutations onto it. The deterministic favicon interleaving test
  first lost `Concurrent update`, then passed after the atomic change; the full
  `WebShortcutServiceTest` class and focused Settings regression both pass.
- RED confirmed for TV focus: a clean empty Web shortcuts screen had no focused
  control, so remote OK could not activate Add until the user first moved focus.
  The empty state will explicitly seed focus on Add before the remaining D-pad
  path is exercised.
- Failed after the first focus seed because Android had already assigned focus
  elsewhere, making the `currentFocus == null` guard suppress Add. Empty-state
  focus policy is deterministic rather than conditional, so Add will request
  focus after layout unconditionally whenever there are no shortcuts.
- The unconditional request still appeared unfocused in the connected test;
  its captured view had window focus but was in instrumentation touch mode,
  where a normal TV button cannot take non-touch focus. The D-pad test now uses
  instrumentation key injection, which exits touch mode and exercises Android's
  real focus search rather than calling `Activity.dispatchKeyEvent` directly.
- D-pad injection successfully focused Add and opened the editor with Name
  focused. The next Down was consumed by the visible TV keyboard rather than
  moving to URL; the test now mirrors actual remote use by closing the keyboard
  before navigating between editor controls.
- With the keyboard closed, Name still retained focus on Down because the
  dialog's implicit focus search did not select the second `EditText`. The
  editor now declares explicit Name-to-URL Down and URL-to-Name Up links.
- The TV IME continued to own Name-to-URL navigation despite the explicit link,
  making that assertion a keyboard test. Text entry is now supplied directly;
  the launcher-owned D-pad contract starts at URL and explicitly links Down to
  dialog Cancel, then covers Save, row Remove, and Cancel-first confirmation.
- Android's AlertDialog focus search did not honor a custom-view XML link to its
  framework Cancel button. The editor now handles Down on its single-line Name
  and URL fields directly, moving to URL and dialog Cancel respectively after
  the keyboard is closed.
- The URL key listener was still bypassed because Espresso's generic keyboard
  close did not deactivate the TV IME input window. The test now mirrors the
  verified physical workflow exactly: remote Back hides the active keyboard,
  then remote Down enters the dialog action row.
- Focus diagnostics confirmed URL itself remained the focused dialog view after
  Down. Field-level dispatch is therefore the wrong interception layer for this
  TV `EditText`; the dialog now owns the two Down transitions before child view
  dispatch. Basri also explicitly authorized committing the finished work and
  requested one final TV power-off action; audio controls remain prohibited.
- Dialog-level interception also saw no event because
  `Instrumentation.sendKeyDownUpSync` labels the event as a keyboard. This box's
  focus engine distinguishes that from its remote, as earlier Move-mode testing
  did. The helper now injects explicit `SOURCE_DPAD` down/up events through
  UiAutomation.
- Explicit `SOURCE_DPAD` still remained owned by the box's TV IME while the URL
  field was active. The connected management test now keeps text entry/save
  validation on the existing Espresso path and scopes remote-key verification
  to launcher-owned focus: Add, row Remove, and Cancel-first confirmation. Both
  Home and management removal confirmations explicitly request Cancel focus.
- RED/GREEN persistence hardening: `WebShortcutCodecTest` proved syntactically
  valid records with a blank URL or `file:` URL were being loaded. Decode now
  rejects records that fail the same HTTP(S)-only policy used by the editor;
  the focused codec test passes. A first compile attempt also exposed and fixed
  an incorrectly broad AlertDialog patch before any device run.
- The first narrowed D-pad run reached and saved the editor but found no focus
  on Add after the Espresso save click, because that touch action re-entered
  touch mode. The test now injects a real D-pad event after save before asserting
  the launcher focus route; production behavior is unchanged.
- Diagnostics on that route showed the injected event correctly enters the
  first row at Edit rather than the footer Add button. The final assertion now
  follows the actual remote path, Edit -> Right -> Remove; Add was already
  reached and activated by D-pad at the beginning of the same test.
- Succeeded: the focused connected D-pad test now passes on `Box R 4K Plus -
  14`, proving remote Add/OK, row Edit/Right/Remove, and explicit Cancel-first
  removal confirmation without deleting the shortcut.
- Next gate: run `./gradlew lintDebug lintRelease testDebugUnitTest
  testReleaseUnitTest connectedCheck assembleDebug assembleRelease`, then the
  signed release script and guarded in-place installer. No audio, volume, mute,
  or power command will be sent during verification.
- Full gate attempt failed after 13/28 connected tests: the D-pad test's
  Espresso text/save phase left the TV IME window owning focus under suite
  ordering, then the following Settings test observed an untouched default
  config. The isolated D-pad run had passed, so this is test state leakage. The
  test is now entirely key-driven: one closed activity proves Add/OK opens the
  editor; a fresh pre-seeded activity proves row Edit/Right/Remove and
  Cancel-first confirmation without invoking the IME.
- The full Home/Settings class still reproduced root-focus loss exactly when
  Espresso inspected the editor after remote OK: Android correctly transferred
  window focus to the TV IME, so the base application root was ineligible for
  Espresso matching. Existing connected tests already cover editor open,
  validation, and save. The key-only regression now stops after proving D-pad
  reaches Add, then uses a fresh pre-seeded activity for row actions and safe
  removal; it never opens the IME and cannot leak keyboard state.
- With the IME leak removed, the D-pad test passed in the 20-test class. The
  following Settings persistence test still closed with unchanged defaults:
  its final Espresso touch on the bottom Save button was intercepted while the
  TV keyboard owned the window. The test now invokes that already-verified
  button from the live activity after its UI edits, matching the retained-
  Settings regression and eliminating device-keyboard ordering from persistence
  verification.
- Succeeded: the focused Settings persistence test passes on Box R after the
  device-keyboard-independent Save trigger. Independent focused re-review found
  no remaining Critical or Important implementation issue across stale-save,
  atomic update, bounded decode, and D-pad/cancel remediation. The complete
  lint/unit/connected/build gate will now be repeated from the final source.
- Full gate repeat reached 28 connected tests with only the same Settings test
  failing: Save persisted every edited text/toggle field, proving that fix, but
  the TV IME intercepted the earlier Espresso touch on the rendered app row.
  The test now activates the matching visible RecyclerView child from the live
  activity, then activates Save; this verifies the real row listener while
  removing the external keyboard window from test delivery semantics.
- Succeeded: the focused Settings field/app-selection/save test passes after
  the rendered-row trigger. Repeat the entire 28-test connected gate together
  with both lint and unit variants before signing.
- Succeeded: final full Gradle gate passed in 1m20s: lintDebug, lintRelease,
  debug and release unit suites, all 28 connected tests on Box R, connectedCheck,
  and debug/release assemblies including R8 and resource shrinking. Next run the
  signing wrapper, verify the APK certificate, then perform guarded `adb
  install -r` to `192.168.1.154:5555`.
- Succeeded: `scripts/build_release_apk.sh` repeated lint, both unit variants,
  all 28 connected tests, shrinking, assembly, and signing in 1m15s. The final
  APK is 233,060 bytes and verifies with certificate SHA-256
  `d1702f54c1ba471b3a719c89dd8b60bc5e5f2445364c155027761c75f8a9cd88`.
  Before update the installed release is already 0.3.0 code 4, its original
  `firstInstallTime=2026-09-01 00:54:10`, and the device is Awake. Run the
  guarded installer next; it compares installed and candidate signer before
  using `adb install -r`.
- Succeeded: `scripts/install_release_apk.sh 192.168.1.154:5555` matched the
  installed/candidate certificates and completed `adb install -r`. Installed
  version is 0.3.0 code 4, and `firstInstallTime=2026-09-01 00:54:10` is
  unchanged. Home resumed with `Welcome Basri`, `Kahveci House`, `London`, the
  existing favorite ordering, and the user's existing `Osuruk tv` shortcut.
- Succeeded: release smoke created `NOBS_FINAL_0902` from the scheme-less input
  `example.com/nobs-final-0902`; management displayed normalized
  `https://example.com/nobs-final-0902`. Returning through Settings Save kept
  it, directly exercising the stale-save remediation. Home placed it between
  Netflix and Prime Video.
- Succeeded: selecting the temporary tile resumed BrowseHere with exact intent
  `android.intent.action.VIEW dat=https://example.com/nobs-final-0902`. Its
  nonfavorite menu exposed Add/Edit/Remove, favoriting moved it above the
  separator with Move/Remove favorite/Edit/Remove, and unfavoriting restored
  alphabetical placement. Confirmed removal left zero UI occurrences; the
  user's existing shortcut and apps were not modified.
- Pending final handoff: verify package permissions/crash state, remove only
  the two shell UI-dump artifacts, cold-launch Home, review the repository diff,
  commit the authorized feature, confirm the device is Awake, then send exactly
  one `KEYCODE_POWER` and verify Asleep. Do not send any audio or volume input.
- Succeeded: final cold launch resumed release Home with preserved `Welcome
  Basri`, `Kahveci House`, `London`, and `Osuruk tv`; the temporary shortcut was
  absent. Installed package remains 0.3.0 code 4 with its original first-install
  timestamp and only the expected granted permissions. The last 500 log lines
  contain no launcher fatal exception or ANR. All three explicit shell UI-dump
  artifacts were removed. Commit the verified source next; power-off remains
  the only device action after that commit.
