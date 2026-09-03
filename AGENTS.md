# Repository instructions for coding agents

These rules apply to the entire repository. More specific instructions may add
constraints, but must not weaken the safety and privacy rules below.

## Product invariants

- Support Android TV API 23 and newer. Keep the app landscape-only,
  remote-first, and fully usable with D-pad, OK, long-press, and Back controls;
  never introduce a touch-only path.
- Remain vendor-neutral. Use Android platform capabilities rather than
  device-vendor packages, services, or assumptions.
- Keep application code under `dev.basri.android.nobs_launcher`.
- Preserve the Home screen's 20% information panel and 80% app panel, along
  with its remote focus behavior, unless an approved task explicitly changes
  that contract.
- Preserve existing launcher configuration and app-private data across
  upgrades, including favorites, shortcuts, visibility choices, and ordering.
  Migrations must be backward-compatible and covered by tests.

## Architecture boundaries

Route changes to the existing package that owns the responsibility:

- `ui`: activities, adapters, rendering, dialogs, and remote focus/navigation
- `model`: launcher configuration and pure app/shortcut domain behavior
- `data`: persistence, app discovery, favicon handling, and protected HTTP
- `stats`: bounded CPU, memory, storage, and network sampling
- `status`: connected Wi-Fi labels and provider-neutral VPN state
- `time`: clock and date updates

Keep Android-independent policy in small testable units. Do not move concerns
between packages or introduce shared mutable state without a documented need.

## Implementation safety

- Never perform package discovery, disk I/O, bitmap decoding, or network work
  on the main thread.
- Give each stateful asynchronous operation an explicit owner. Make work
  lifecycle-aware and cancellable, ignore stale results, and preserve unsaved
  UI state through recreation.
- Bound timeouts, redirects, response sizes, retries, allocations, sampling,
  and retained state. Release callbacks and resources when their owner stops.
- Diagnose root causes. Do not hide failures by weakening assertions, skipping
  tests, or adding broad exception handling.

## Privacy and network boundaries

- Do not add analytics, tracking, telemetry, advertising, accounts, sign-in,
  WebView content, page JavaScript, background refresh, or background location.
- Do not send or expose credentials through requests, redirects, logs, UI,
  tests, prompts, or documentation. Do not add cookies, HTTP authentication,
  proxy authentication, or a response cache.
- Reuse the current URL normalization, redirect, scheme, DNS-rebinding, and
  private-network safeguards in `data`. Never duplicate or bypass them.
  Preserve the existing distinction between explicit local HTTP shortcuts and
  public destinations, including the HTTPS downgrade protections.
- Network access must remain bounded and tied to an explicit user action.

## Real-TV and device safety

- When authorized verification requires installing over an existing copy,
  preserve its data with an in-place update (`adb install -r` or the
  repository's guarded install script). Do not uninstall and reinstall.
- Package mutations in connected tests must target fixtures owned by the test.
- Without explicit task-specific authority, do not clear app data, uninstall
  the launcher, change the default Home app, disable or enable arbitrary
  packages, or change device audio, display, sleep, reboot, or power state.
- Record the exact device and allowed actions before using ADB. Treat a real TV
  as user data, not as a disposable test environment.

## Workflow and verification

- Inspect `git status` and the smallest relevant set of files before editing.
  Preserve unrelated changes and follow existing code and test patterns.
- Plan non-trivial work, add focused regression coverage, implement the
  smallest complete change, and review the final diff against the request.
- For application changes, run the complete local matrix:

  ```bash
  git diff --check
  ./gradlew lintDebug lintRelease test assembleDebug assembleRelease
  ```

- The `test` aggregate must run and pass both `testDebugUnitTest` and
  `testReleaseUnitTest`; confirm both variants in the output rather than
  inferring them from another task.
- When UI, Android integration, lifecycle, package, network, or other device
  behavior changes, also run this on an authorized target:

  ```bash
  ./gradlew connectedCheck
  ```

  Exercise the affected flow on a real TV when the result is device- or
  firmware-dependent.
- Before `connectedCheck`, ensure every attached device is authorized for the
  suite or scope it to the recorded target, for example:
  `ANDROID_SERIAL=<device-serial> ./gradlew connectedCheck`.
- Report commands and observed results separately from expectations. State
  clearly when connected or real-TV verification was not run.

## Source control and public-output safety

- Keep unrelated working-tree changes intact. Never discard, overwrite, stage,
  or fold them into the task.
- Basri's explicit authorization is required before committing, pushing,
  contacting or changing remotes, rewriting history, or deleting a branch or
  worktree. Never amend or force-push.
- Never put real private deployment or device values in repository content,
  prompts, logs, commits, or review text. This includes actual private IP
  addresses, Wi-Fi/SSID values, keystore paths or credentials, device secrets,
  and vendor package or component details. Use placeholders such as
  `<device-serial>` and `<stock-home-package>`.
- Clearly labeled documentation examples may use reserved synthetic values. A
  test may use a private-address literal when it is display-only or its fake
  transport cannot perform real I/O. Actual loopback I/O must use a server
  owned by that test. Such fixtures must never identify or contact a real
  device or network endpoint.
