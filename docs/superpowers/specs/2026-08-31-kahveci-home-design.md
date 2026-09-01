# Kahveci Home Design

**Status:** Approved in conversation on 2026-08-31; awaiting review of this written specification.

## Objective

Build a small, private, portable Android TV launcher that displays only user-selected apps in a manually ordered grid. It must replace recommendation-heavy vendor launchers without depending on Google TV, SEI/Homatics, Xiaomi, or any other vendor-specific service.

The same signed APK must run on the current Box R 4K Plus and Basri's Xiaomi Android TV box. Device-specific labels and app selections remain local to each installation.

## Identity and compatibility

- App name: **Kahveci Home**
- Application ID and Kotlin package: `dev.basri.android.kahveci_home`
- Project path: `/Users/basri/dev/personal/kahveci_home`
- Minimum Android version: Android 6.0 / API 23
- UI technology: native Kotlin with Android Views and `RecyclerView`
- Device class: Android TV and Google TV devices with D-pad remotes
- Orientation: landscape

Only standard Android APIs may be used. Runtime behavior must not contain fixed device IP addresses, firmware component names, shell commands, or vendor-specific integrations.

## Main screen

The screen uses the approved Calm Grid structure and adapts to the available TV resolution and density.

### Information panel

The left side occupies approximately 30 percent of the screen and contains:

1. A large system-local clock in 24-hour format.
2. The full local date.
3. A configurable line combining the device's Wi-Fi label and physical-location label, initially `Kahveci House · London` on the Box R.
4. `NordVPN connected` only when Android reports an active VPN connection and NordVPN is installed.

The VPN line is absent when no VPN is active or NordVPN is not installed. Android intentionally hides VPN ownership from other normal applications, so this rule assumes NordVPN is the only VPN used on these boxes. If another VPN is later installed and used alongside NordVPN, Android's public API cannot distinguish it. The launcher does not display an exit city or make an IP-geolocation request.

The Wi-Fi and physical-location values are user-entered labels. The launcher does not read precise location or request Android location permission. Each installation can use different labels.

### App grid

The right side occupies approximately 70 percent of the screen and contains:

- A four-column grid by default, with spacing and tile size adapting to resolution and density.
- One tile per selected application package.
- The application's Android TV banner when usable; otherwise its regular application icon.
- The application label below or within the tile so a logo is never the only identifier.
- A clear focus outline that is visible from normal TV viewing distance.
- A permanently reachable **Manage apps** control in the header.

The grid contains no recommendations, sponsored content, media rows, search, microphone control, weather, news, or network-fetched imagery.

## First-run setup

On the first launch, before showing an empty Home screen, Kahveci Home opens setup. Setup proceeds in this order:

1. Edit the Wi-Fi label and physical-location label.
2. Show all TV-launchable apps as initially unchecked.
3. Let the user select the apps that should appear.
4. Preserve selection order as the initial Home order.
5. Finish into the Home screen.

At least zero apps may be selected because the Manage apps control remains available. Newly installed apps are hidden by default until explicitly selected.

## App discovery and launching

`AppCatalog` queries `PackageManager` for activities handling:

- `ACTION_MAIN` with `CATEGORY_LEANBACK_LAUNCHER`
- `ACTION_MAIN` with `CATEGORY_LAUNCHER` as a fallback for sideloaded apps that lack a TV entry point

Manifest `<queries>` entries are limited to those intent shapes plus the NordVPN package. The app must not request `QUERY_ALL_PACKAGES`.

Applications are identified in stored layout state by package name. When both TV and mobile launch activities exist, the TV activity is preferred. The launch component is resolved afresh so normal application updates do not leave stale component names.

The launcher excludes itself from the app grid. Selecting a tile starts the resolved activity through standard Android intents. If resolution or launch fails, the Home screen stays active and shows a short, remote-readable error.

## Selection and manual ordering

The **Manage apps** screen lists discovered launchable apps with their real icon, label, and selected state.

- Pressing OK toggles visibility.
- Selecting a new app appends it to the end of the stored Home order.
- Deselecting an app removes it from the Home order without uninstalling or disabling it.
- The screen also provides entry points for editing the two information labels and opening Android system settings.

On Home, holding OK on an app tile enters move mode:

- Arrow keys swap the tile through grid positions.
- OK saves the new order.
- Back restores the order from before move mode.
- A visible move-mode treatment distinguishes movement from normal launching.

## Local state

`LayoutStore` persists only:

- An ordered list of selected package names.
- The Wi-Fi display label.
- The physical-location display label.
- A first-run-completed flag.

State lives in app-private preferences. `android:allowBackup` is `false` so configuration cannot be uploaded or copied between boxes through Android backup. Each device is configured independently.

When a selected app is uninstalled, its tile is omitted immediately and the stale package is pruned from stored order. A newly installed app becomes available in Manage apps but remains hidden. Missing or malformed preferences recover to first-run setup rather than crashing or showing arbitrary apps.

## Time and connectivity status

Clock and date use the device's system clock, locale, and timezone. The screen updates on minute boundaries and responds to Android time, date, locale, and timezone changes without a continuously running background service.

VPN status uses standard `ConnectivityManager` and `NetworkCapabilities` APIs plus package visibility for `com.nordvpn.android`. It shows the line when a VPN transport is active and NordVPN is installed. Detection errors are non-fatal, and the implementation does not use hidden framework APIs.

## Privacy and permissions

The production manifest may request only the minimum standard capabilities needed for launcher behavior:

- `android.permission.ACCESS_NETWORK_STATE` for local VPN connection state.

The production manifest must not request:

- `android.permission.INTERNET`
- Any precise or approximate location permission
- `android.permission.QUERY_ALL_PACKAGES`
- Notification-listener access
- Accessibility-service access
- Microphone, camera, contacts, account, storage, advertising ID, or usage-access permissions

The project contains no analytics, crash-reporting SDK, advertising SDK, remote configuration, account system, or cloud sync. All runtime dependencies must be audited for transitive permissions before release.

## Default Home behavior and rollback

The main activity declares standard `MAIN`, `HOME`, and `DEFAULT` intent categories and behaves as a single Home task. Pressing the remote Home button and completing device startup should open Kahveci Home.

The existing stock launcher remains installed and enabled. Kahveci Home is tested as a normal app before it is selected as the default Home activity. Installation documentation includes explicit commands or UI steps to:

1. Select Kahveci Home as default.
2. Restore the device's stock Home activity.
3. Uninstall Kahveci Home after restoring stock Home.

No activation procedure may permanently delete or modify the stock launcher. Firmware-specific activation notes may exist outside the app, but runtime code remains portable.

## Architecture and boundaries

- `HomeActivity`: owns Home lifecycle, focus, move-mode input, and launching.
- `ManageAppsActivity`: owns first-run selection, later show/hide changes, and label editing.
- `AppCatalog`: discovers, deduplicates, labels, illustrates, and resolves launchable apps.
- `LayoutStore`: validates and persists device-local labels and ordered package selection.
- `ClockController`: produces formatted time/date state and schedules lifecycle-bound updates.
- `VpnStatusMonitor`: emits NordVPN connected state when a standard VPN transport is active and NordVPN is installed.

UI components consume immutable state from these units. Package discovery, persistence, and VPN detection are isolated from Activities so their behavior can be unit-tested without a TV.

## Error handling

- Missing app or launch activity: prune or omit its tile; show a short error only when a launch was attempted.
- Corrupt preferences: recover to first-run setup.
- Unavailable icon/banner: fall back to the normal icon and then a deterministic letter tile.
- VPN state unavailable or NordVPN absent: omit the VPN line.
- App list changes while focused: retain focus on the same package when possible, otherwise select the nearest surviving tile or Manage apps.
- Empty selection: show the information panel and Manage apps control with an explanatory empty state.

## Verification strategy

Implementation follows test-driven development: each behavior starts with a failing test, then the minimum production change, followed by refactoring while green.

### Automated tests

- Unit tests for TV/mobile activity preference, package deduplication, selection toggling, append order, move save/cancel, stale-package pruning, corrupt-state recovery, label formatting, and conditional VPN text.
- Android instrumentation tests for first-run routing, D-pad focus, Manage apps reachability, selection persistence, long-press move mode, launch failure handling, Home intent handling, and configuration changes.
- Test fixtures provide launchable TV activities without depending on apps installed on the development machine.
- Full recursive unit and instrumentation suites run after implementation, not only individual tests.

### Static and build verification

- Run Android lint and fix all introduced findings.
- Build debug and signed release APKs.
- Audit the merged release manifest and APK permissions, specifically proving the absence of Internet, location, broad package query, notification, accessibility, microphone, and analytics capabilities.
- Inspect release signing and archive the certificate fingerprint needed for future compatible upgrades.

### Box R runtime verification

1. Install as a non-destructive update when already present.
2. Complete first-run setup using only the Bluetooth remote.
3. Verify app show/hide, manual reorder save/cancel, real icon/name rendering, label editing, and empty state.
4. Verify NordVPN text appears while NordVPN is installed and its VPN connection is active, then disappears after disconnect.
5. Make Kahveci Home default, press Home from multiple apps, reboot, and confirm startup persistence.
6. Launch YouTube, YouTube Music, Netflix, StreamVault, Jellyfin, Play Store, NordVPN, and Android settings from the launcher.
7. Confirm Cast, phone remote, Bluetooth remote, HDMI-CEC, HDMI audio, DRM, and retained streaming apps still function.
8. Inspect crash and ANR buffers and measure launcher APK size, process PSS, and post-boot memory.
9. Exercise and verify the documented stock-launcher rollback.

### Xiaomi verification boundary

The same APK is designed for the Xiaomi box through standard APIs and API 23 compatibility. It must not be reported as device-verified until that physical box is connected and the same install, remote, Home, reboot, app-launch, and rollback smoke tests have been executed there.

## Release and signing

A dedicated release signing key is used for both boxes and preserved for future updates. Signing secrets are not committed to source control. The delivered APK is verified with Android signing tools before installation.

## Explicitly out of scope

- Recommendations, Watch Next, sponsored rows, media aggregation, or content search
- Voice search, microphone, Google Assistant, or text-to-speech
- Weather, automatic geolocation, VPN exit-location lookup, or IP-geolocation services
- Categories, folders, widgets, profiles, parental controls, or cloud sync
- App installation, uninstallation, disabling, or vendor-package cleanup from inside the launcher
- Vendor-specific runtime integrations

## Acceptance criteria

The feature is accepted when:

1. The approved Calm Grid design is remote-usable and responsive on the Box R.
2. Only explicitly selected apps appear and their order is manually controllable.
3. Real app logos and names render with deterministic fallbacks.
4. Clock, date, configured Wi-Fi/location labels, and conditional NordVPN state behave as specified.
5. Home-button and startup behavior persist across reboot.
6. No recommendation or promotional content appears.
7. The merged APK has no Internet, location, telemetry, broad package-query, notification-listener, or accessibility capability.
8. Automated, build, runtime, retained-feature, and rollback checks pass with fresh evidence.
9. The same signed APK remains suitable for Xiaomi testing without vendor-specific code.
