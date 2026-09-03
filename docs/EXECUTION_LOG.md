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
- Succeeded: committed the verified implementation as `501d82e` (`Add
  first-class web shortcuts`) with a clean worktree. Immediately before the
  final device action, `dumpsys power` reported `mWakefulness=Awake`. Sent
  exactly one `adb -s 192.168.1.154:5555 shell input keyevent KEYCODE_POWER`;
  after three seconds the device reported `mWakefulness=Asleep` and
  `mWakefulnessChanging=false`. No mute, unmute, volume, or other audio command
  was sent during implementation, verification, installation, or handoff.

## 2026-09-02 - Website reachability and declared favicons

- Root cause established from the release implementation: favicon fetching
  rewrites every shortcut to only the origin's `/favicon.ico` and never requests
  or parses the configured page. Sites that declare an icon at another path
  therefore show the globe. Save also persists before any reachability result.
- Basri approved a bounded page `GET`, accepting `2xx`, `401`, and `403`, with
  inline failure and HTML-declared favicon discovery before origin fallback.
  The approved design and implementation plan are written but intentionally
  uncommitted because no new commit was authorized.
- Execution baseline: branch `feature/web-shortcuts` at `b2143ad`; only the new
  spec and plan are untracked. `.worktrees` is ignored and the debug unit suite
  passes. Before device mutation, Box R `192.168.1.154:5555` reports
  `mWakefulness=Awake`; preserve Awake and do not send power or audio commands.
- Next: implement Task 1 with a failing `WebsiteHttpClientTest`, verify RED,
  implement the bounded client, verify GREEN, then run independent spec and
  quality reviews before continuing.
- Task 1 TDD: missing client/result types produced the intended compile RED.
  A cleanup regression then failed on disconnect and passed after connection
  assignment was moved before request configuration. The implementer reports
  10 focused tests, lintDebug, assembleDebug, and all 60 debug unit tests green.
- Task 1 spec review found the twelve-second deadline is not strict: one
  remaining-time value is assigned to both connect and response-header reads,
  so those phases can each consume it sequentially. Add a deterministic RED
  that advances time between connect and response-code read, then recalculate
  the read timeout after connect before re-running review.
- The new regression produced the intended `expected 1000, was 4000` RED; after
  explicit connect plus a remaining-budget read timeout, all 11 focused and 60
  debug unit tests passed and spec review approved Task 1.
- Code-quality review still blocks Task 1 on Android semantics: connection
  timeout can apply per resolved address and post-connect read-timeout mutation
  may not update the active socket, so arithmetic alone cannot enforce a hard
  deadline. It also found VM-wide CookieHandler/Authenticator state can add
  ambient credentials and non-HTML/error/redirect streams are not explicitly
  closed. Add watchdog-level deadline coverage, suppress ambient credentials,
  close all obtained response streams, and correct the misleading charset test
  before repeating both reviews.
- The HttpURLConnection remediation exposed an architectural contradiction:
  Android does not expose reliable per-connection suppression/restoration of
  VM-global authentication on the minimum API, and a worker that ignores
  disconnect/interrupt cannot simultaneously guarantee hard return and exact
  global-state restoration. Stopped that implementation before accepting it.
- Revised Task 1 to use OkHttp, whose per-client no-cookie/no-auth policy,
  response `use` lifecycle, and call timeout directly satisfy the privacy,
  cleanup, and hard-deadline requirements. Manual redirects will assign each
  Call the remaining shared twelve-second budget. This is an implementation
  dependency change only; approved user-visible behavior is unchanged.
- Reconciled the written plan after the interrupted implementation. The page
  probe and exact-candidate favicon fetcher will each use explicitly private
  OkHttp clients; no HttpURLConnection global cookie/authentication state or
  watchdog threads remain in the target architecture. Task 1 is being rebuilt
  test-first against that boundary before either downstream task begins.
- Dependency compatibility check: OkHttp 5.5.0's Android AAR requires
  `minCompileSdk=37`, so `:app:checkDebugAarMetadata` correctly failed against
  this project's compile SDK 36. The newest compatible stable release found,
  OkHttp 5.4.0, passes the same AAR metadata check. Selected 5.4.0 instead of
  broadening the task into an AGP/SDK upgrade or using the JVM-only artifact.
- Task 1 complete. The OkHttp implementation now classifies `2xx`, `401`, and
  `403`, enforces one shared deadline over manual redirects and body reads,
  caps HTML at 256 KiB, closes every response, and isolates cookies/auth/cache.
  The implementer reported 11/11 focused tests, the full debug unit suite,
  lintDebug, and assembleDebug green; root independently reran the focused
  class with `--rerun-tasks` successfully. Independent spec review passed and
  code-quality review approved with no blocking findings. Next: declared icon
  discovery, exact-candidate fetching, and first-decodable storage.
- Task 2 boundary decision: `FaviconGateway` currently lives in the Task 3
  service file. Task 2 will add the candidate-list repository overload while
  retaining the legacy override so every intermediate state compiles; Task 3
  will switch the interface and service call site together. This preserves the
  plan's independently buildable phases without mixing file ownership.
- Task 2 initial implementation passed focused parser/fetcher tests, all four
  connected repository tests on Box R, the debug unit suite, lintDebug, and
  assembleDebug. Spec review then found cross-category duplicate URLs and
  `<link-preview>` false positives; new regressions failed first and now pass,
  and repeat spec review is green.
- Task 2 quality review found deeper robustness gaps: regex scanning could be
  quadratic or accept links inside truncated comments/scripts, favicon redirect
  calls reset the four-second budget, and repository exceptions could skip the
  completion callback. Fix these test-first with a bounded linear scanner, one
  shared per-fetch deadline, and exception-safe completion. Keep final-page URL
  resolution (not HTML `<base>`) because that is the approved explicit design.
- The first hardening pass added a linear outer scanner, truncated raw-context
  handling, quoted-`>` support, first-wins attributes, a shared four-second
  favicon deadline, exact max+1 overflow detection, and exception-safe callback
  completion. It passed focused/full debug tests, lint, assemble, and 5/5
  connected repository tests. Repeat spec review then caught non-element names
  such as `<link_preview>`; delimiter regressions were added and the repeat spec
  review now passes.
- Final quality review benchmarked the remaining attribute regex and found
  quadratic retry behavior on long malformed tags. Replace that last regex with
  a one-pass attribute scanner and add an adversarial performance regression
  before seeking final quality approval.
- Task 2 complete. The attribute regex was replaced with a one-pass scanner,
  malformed long input and raw/RCDATA contexts gained regressions, and focused
  plus full debug tests, lintDebug, assembleDebug, diff-check, and 5/5 connected
  repository tests pass. Final independent spec and quality reviews both
  approve with no remaining findings. Next: make Save probe asynchronously
  before persistence and pass the discovered candidate list into this pipeline.
- Task 3 interface migration requires the deferred mechanical repository edit:
  remove its temporary legacy `fetchAndStore(shortcut, callback)` override and
  mark the already-tested candidate-list overload as the `FaviconGateway`
  implementation. Expanded Task 3 ownership to that one compatibility edit so
  the gateway and all implementers change atomically and the phase compiles.
- Task 3 complete. The async service probes before any mutation, serializes
  cancellation against persistence, preserves UUID/favorite order, deletes old
  icons only after a successful write, reports Saved before candidate fetching,
  and rejects stale icon attachment. The final follow-up documented callback
  threads and isolated client callback exceptions so best-effort favicon work
  still runs. Eighteen focused service tests, debug+release unit suites,
  lintDebug, assembleDebug, and diff-check pass; independent spec and quality
  reviews approve. Next: migrate the editor off the temporary synchronous
  bridge and prove progress/error/cancellation behavior on Box R.
- Task 4 host implementation complete: the editor displays a checking row,
  disables fields and Save while leaving Cancel enabled, dispatches service
  results to main, keeps inaccessible inputs open with an inline URL error,
  cancels on dismissal/destruction, and removes the temporary sync bridge.
  Cleartext HTTP is an explicit manifest boundary with no new permission.
- Box R temporarily disappeared from ADB, so the first connected attempt failed
  before test execution with `No connected devices`. A direct `adb connect`
  restored `192.168.1.154:5555`; root then reran the focused connected class
  with `--rerun-tasks`: all 24/24 tests passed in 2m56s. Spec review passes and
  quality review approves with only minor accessibility/lifecycle/test-hardening
  notes. Before closing Task 4, add live-region/focus behavior and remove the
  two timing/port races from the connected tests, then rerun the gate.
- Task 4 hardening follow-up: focused connected RED confirmed missing polite
  live-region and URL-field focus; both focused cases pass after implementation.
  Inaccessible tests now use deterministic HTTP 500 responses, and cancellation
  queues a second probe as an executor barrier instead of observing for 500 ms.
- The ensuing full 24-test rerun is invalid, not a test verdict: Box R's Android
  system crashed/offlined during instrumentation (`INSTRUMENTATION_ABORTED:
  System has crashed`, `DeadObjectException`) after only two results. Host
  debug+release unit, lint, assemble, androidTest compilation, and diff-check
  remain green. Wait for ADB recovery without sending power/audio commands,
  then rerun all connected tests from a clean instrumentation start.
- Basri physically power-cycled the unresponsive box after the Android-system
  crash. It rejoined Wi-Fi, wireless debugging was re-enabled, `sys.boot_completed`
  returned 1, and ADB reported Box R at `192.168.1.154:5555` Awake. A clean
  post-reboot Task 4 connected rerun then passed all 24/24 tests with zero
  failures in 1m28s. Task 4 spec and final quality reviews approve.
- Task 5 pre-install evidence: release package
  `dev.basri.android.nobs_launcher` is 0.3.0/code 4; first install remains
  `2026-09-01 00:54:10`, last update `2026-09-02 01:18:31`; Android resolves
  Home to `dev.basri.android.nobs_launcher/.ui.HomeActivity`; device is Awake.
  Intended mutation sequence after a full clean gate: build/sign with
  `scripts/build_release_apk.sh`, verify APK/certificate/permissions, guarded
  in-place install via `scripts/install_release_apk.sh 192.168.1.154:5555`,
  then release smoke and preservation checks. Never uninstall, power-toggle,
  mute, unmute, or change volume.
- Task 5 gate passed from a clean forced run: `lintDebug`, `lintRelease`, both
  91-test unit variants, all 35 connected tests, `connectedCheck`, debug/release
  assembly, R8, and resource shrinking completed with zero failures. The release
  script repeated all 35 connected tests and produced a signed 464,393-byte APK
  for 0.3.1/code 5. `apksigner` verifies certificate SHA-256
  `d1702f54c1ba471b3a719c89dd8b60bc5e5f2445364c155027761c75f8a9cd88`;
  the archive contains only ACCESS_NETWORK_STATE, INTERNET, and
  REQUEST_DELETE_PACKAGES.
- Guarded `adb install -r` succeeded after confirming the installed and new
  certificate match. Post-install package evidence is 0.3.1/code 5 with the
  original first-install timestamp `2026-09-01 00:54:10`. Cold release Home
  retained `Welcome Basri`, `Kahveci House`, `London`, the system panels,
  favorite order, and the existing `Osuruk tv` shortcut. Its tile displays the
  downloaded site icon rather than the bundled globe, and activating it resumed
  the default TCL browser with exact ACTION_VIEW data `https://tv.osur.uk`.
  The connected release-equivalent UI suite already proved inaccessible 500
  results show the inline error without persistence and that delayed reachable
  saves complete. No test metadata or favorite was left behind.
- Final state: a cold 0.3.1 Home launch completed in 887 ms, Android still
  resolves the launcher as the default Home, its activity is top-resumed, the
  original first-install timestamp remains intact, `git diff --check` passes,
  and Box R is Awake. No mute, unmute, volume, or final power command was sent.
  Changes remain uncommitted on `feature/web-shortcuts` because Basri did not
  authorize a commit in this execution.
- The first Task 4 worker remained in exploration and made no Task 4 file
  changes despite two progress prompts. Interrupted it before any overlap and
  restarted the same bounded UI task with a fresh worker instructed to move
  directly to failing connected tests. No code was discarded and no device
  state command was issued.

## 2026-09-02 - Favicon display sizing follow-up

- Reproduced and traced the tiny favicon report. Downloaded site icons commonly
  retain 16-32px intrinsic dimensions, while both launcher favicon views use
  Android `centerInside`, which never enlarges a drawable beyond its intrinsic
  size. Installed app artwork begins large and is scaled down, explaining the
  visible mismatch.
- Decision: use aspect-preserving `fitCenter` only when a downloaded favicon is
  bound, on both Home and Web shortcuts management. Keep normal app artwork and
  the bundled globe on their existing `centerInside` path. This fixes existing
  cached icons without rewriting files, cropping logos, or changing app tiles.
- Next: add connected regressions that measure the rendered extent of a 16px
  favicon and confirm they fail on both surfaces before changing production.
- First RED run: management failed for the expected reason, rendering the 16px
  test favicon at only 8x8 inside a 128x128 viewport. The Home assertion did not
  reach the icon because its alphabetic position was below the visible grid;
  move only the fixture label into the visible first row and repeat RED.
- RED confirmed on both surfaces after moving the Home fixture into view: the
  source renders at 8x8 inside Home's 231x201 artwork area and the management
  row's 128x128 area. Production binding can now change with evidence that the
  regressions detect the reported defect.
- GREEN confirmed: both focused connected regressions pass after binding only
  real downloaded favicons with `FIT_CENTER` and explicitly restoring
  `CENTER_INSIDE` for app and fallback paths. Version is now 0.3.2/code 6.
- Next: run the complete signed release gate before the guarded in-place update.
- First full gate ran all 37 connected tests; 36 passed and the sole failure was
  the manifest regression still asserting the deliberately superseded
  0.3.1/code 5 identity. Update that version assertion to 0.3.2/code 6, run it
  focused, then repeat the entire gate rather than treating a partial run as
  release evidence.
- The updated manifest regression passed focused. The complete release gate was
  then repeated successfully: debug/release lint, both full unit variants, all
  37/37 connected tests, connectedCheck, debug/release assembly, R8/resource
  shrinking, and APK signature verification passed.
- Release candidate evidence: 0.3.2/code 6, 464,484 bytes, APK SHA-256
  `8e4f98c8f4cc140134ae2d561c0e8137c38f04085f621ef4848c350a5a76a5bc`,
  signing certificate SHA-256
  `d1702f54c1ba471b3a719c89dd8b60bc5e5f2445364c155027761c75f8a9cd88`,
  and only ACCESS_NETWORK_STATE, INTERNET, and REQUEST_DELETE_PACKAGES.
- Pre-install Box R evidence: installed release is still 0.3.1/code 5, first
  install time remains `2026-09-01 00:54:10`, No bullshit launcher resolves as
  Home, and the device is Awake. Next: guarded in-place update and release UI
  smoke; do not send power or audio commands.
- Guarded signature comparison and `adb install -r` succeeded. Release 0.3.2 is
  installed in place, and a cold Home screenshot proves the existing cached
  `Osuruk tv` favicon now fills its artwork region while labels, app artwork,
  and the dashboard remain intact.
- The strict report scan exposed five lint warnings that Gradle permits: two
  deliberate whole-dataset RecyclerView refreshes, one ASCII ellipsis, and two
  intentionally custom-styled TV buttons. Scope the refresh suppressions to
  those methods, use the typographic ellipsis, and annotate the two intentional
  TV style exceptions; rebuild before final evidence.
- Final rebuild passed with zero lint errors or warnings, 91/91 debug unit
  tests, 91/91 release unit tests, 37/37 connected tests, connectedCheck,
  debug/release assembly, R8/resource shrinking, and signing. The final APK is
  464,481 bytes with SHA-256
  `53faa3fa263ada8de58ea2e0f7380164697f20042484e9ab8618734866d0d91d`.
- Next: guarded-update the installed 0.3.2 once more so it exactly matches this
  final verified APK, then repeat the Home screenshot and state checks.
- Final guarded update installed the exact verified APK. Pulling the installed
  archive back from Box R produced the identical SHA-256. Package evidence is
  0.3.2/code 6 and the original first-install timestamp remains
  `2026-09-01 00:54:10`.
- Final cold launch completed in 899 ms. The screenshot shows the existing
  cached `Osuruk tv` favicon enlarged in its tile, alongside unchanged app
  artwork and preserved `Welcome Basri`, `Kahveci House`, and `London` values.
  No new download or private-file migration was required.
- One read-only final-evidence command was rejected before execution because
  its temporary-directory cleanup trap matched the tool's destructive-command
  guard. It made no changes; the checks were rerun with an explicit temporary
  file and all completed.
- Final device state: No bullshit launcher is the resolved and top-resumed
  Home; Box R is Awake. No mute, unmute, volume, Home, or power key was sent.
  The Android audio dump reports the box's music stream itself as unmuted; the
  external TV mute state cannot be observed through this ADB endpoint and was
  not changed.
- Final source state passes `git diff --check` and remains intentionally
  uncommitted on `feature/web-shortcuts`; Basri did not authorize a commit for
  this follow-up.
- The first plan-completeness search also matched the header's example checkbox;
  anchoring it to actual list items confirmed every implementation step is
  checked. This was a read-only query mistake, not a product/test failure.

## 2026-09-02 - Favicon vertical-alignment follow-up

- Basri reported that the enlarged square favicon is now taller than regular
  app artwork. Root cause: `FIT_CENTER` fills the Home artwork view's roughly
  100dp height for a square icon, while normal 16:9 TV banners are constrained
  by the roughly 115dp width and render only about 65dp high.
- Decision: keep aspect-preserving scaling and establish one centered 65dp
  content band for all Home artwork using 18dp top/bottom padding. This leaves
  tile/label geometry unchanged, aligns square and landscape artwork, and
  remains vendor-neutral. Shortcut management retains its square icon area.
- Next: replace the stale full-height regression with a real favicon-versus-app
  rendered-height comparison, prove RED, then change the shared layout.
- RED confirmed on Box R: the small favicon renders at 201px high beside
  `000Player` artwork at 129.9375px. This directly reproduces Basri's visual
  report and validates using the measured 18dp top/bottom artwork inset.
- GREEN confirmed: after adding the shared inset, both the real app-versus-web
  alignment regression and the unchanged management-icon sizing regression
  pass on Box R. Release identity is bumped to 0.3.3/code 7 for deployment.
- Next: run the complete signed gate, then guarded-install and visually inspect
  the exact final artifact.
- The complete 0.3.3 gate passed: zero-warning debug/release lint, 91/91 debug
  unit tests, 91/91 release unit tests, 37/37 connected tests, connectedCheck,
  both assemblies, R8/resource shrinking, and signature verification. The
  464,601-byte APK has SHA-256
  `8ada6710295ede46006728c0f211c902772a27aee708c4853ab665236885f26a`
  and the established release certificate.
- Pre-install state remains 0.3.2/code 6 with original first-install time,
  release Home ownership, and Awake device state. Next: guarded in-place
  install and final screenshot; no power or audio commands.
- Guarded certificate comparison and `adb install -r` installed 0.3.3 without
  uninstalling or clearing data. A 729ms cold release launch shows the cached
  `Osuruk tv` favicon centered in the same vertical artwork band as YouTube,
  000Player, and Jellyfin; tile and label baselines remain aligned.
- Next: pull back the installed APK to prove byte identity, confirm version,
  original install time, Home ownership, and device state, then run the final
  diff/plan checks. No power or audio command has been sent.
- Final evidence complete: the pulled installed APK exactly matches the signed
  release SHA-256, package identity is 0.3.3/code 7, and first install remains
  `2026-09-01 00:54:10`. No bullshit launcher is resolved and top-resumed Home;
  preserved `Welcome Basri`, `Kahveci House`, `London`, and `Osuruk tv` are
  present in the final UI hierarchy; Box R remains Awake.
- `git diff --check`, the zero-warning lint reports, both 91-test unit variants,
  the 37-test connected report, and the completed alignment plan all pass.
  Changes remain intentionally uncommitted on `feature/web-shortcuts`. No Home,
  power, mute, unmute, or volume command was sent.

## 2026-09-03 - Full reliability, security, and performance remediation

- Created a dedicated remediation worktree from local `main` and recorded the
  agreed design and implementation plan before changing production code.
- Completed separate, reviewable commits for every audit finding: safe
  instrumentation fixtures, transient-favorite preservation, correct VPN
  monitoring, URL/probe/privacy hardening, cancellable network calls,
  application-scoped asynchronous app/favicon work, bounded caches and
  deadlines, lifecycle state restoration, cheaper stats polling, signer and
  dependency verification, R8 optimization, and obsolete-resource cleanup.
- Each issue passed its focused JVM, build, script, or Box R instrumentation
  gate before commit. The 0.5.0/code 10 contract test was first observed failing
  against 0.4.1/code 9, then passed after the release metadata change.
- Two focused UI runs initially failed because the Box R entered its Android
  dream while the suite was running; logcat showed Home stopped before Espresso
  reported no resumed activity. The device's original stay-awake value was 0.
  Temporarily enabling stay-awake and stopping the dream made both unchanged
  tests pass. Restore that original value before the final power-off.
- Remaining: run the complete signed local/connected gate, guarded in-place
  install, release UI smoke checks and crash/ANR review, fast-forward local
  `main` without pushing, restore stay-awake, and power off the TV. No audio or
  volume command has been sent.
