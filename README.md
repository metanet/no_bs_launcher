# No bullshit launcher

A focused, vendor-neutral Android TV home screen for launching apps and web
shortcuts without recommendations, advertisements, or content feeds.

No bullshit launcher is designed for a remote control and keeps the useful
parts of a TV home screen visible: the time, optional device information, your
favorite apps, and the rest of your apps in alphabetical order.

## Features

- Put favorite apps and web shortcuts first, in any order.
- Browse every other launchable app alphabetically below a clear separator.
- Long-press an app to add or remove it from Favorites, move it, or ask Android
  to uninstall it.
- Reorder favorites with the remote's Left and Right buttons, press OK to save,
  or Back to cancel.
- Create named HTTP or HTTPS shortcuts that open in the default browser and use
  a site favicon when one is available.
- Show an optional welcome message, clock, date, connected Wi-Fi name,
  timezone-derived location, generic VPN state, and system statistics.
- Monitor memory, CPU capacity and utilization, storage, and separate network
  ingress and egress rates.
- Choose which information panels and apps appear from a remote-friendly
  Settings screen.

The launcher uses Android APIs rather than device-vendor integrations, so the
same APK can run on different Android TV boxes.

## Privacy and security

No bullshit launcher has no analytics, advertising SDK, account system,
telemetry, or background location tracking.

- Preferences, favorites, shortcut URLs, and downloaded favicons stay in the
  app's private local storage. Android backup and device transfer are disabled.
- The location label comes from the system timezone; it is not a GPS lookup.
- Wi-Fi permissions are used only to display Android's connected SSID when the
  operating system makes it available. The launcher does not scan for networks.
- VPN status is provider-neutral and displays only whether Android reports an
  active VPN transport.
- Saving a web shortcut performs a bounded `HEAD` reachability check and a
  bounded favicon request. Requests use no cookies, HTTP authentication, proxy
  authentication, or response cache.
- HTTPS redirects cannot downgrade to HTTP. Redirect and favicon handling
  rejects unexpected private-network destinations for public shortcuts,
  including DNS rebinding attempts. Explicitly configured local HTTP services
  remain supported.
- The maintainer release workflow pins the signing certificate. The Gradle
  wrapper checksum and dependency verification metadata are committed.

See [Installation and rollback](docs/INSTALL_AND_ROLLBACK.md) for the complete
permission boundary and release-handling details.

## Requirements

To run the launcher:

- Android TV 6.0 or newer (API 23+)
- A television or box with Android's Home/launcher support
- A web browser if web shortcuts will be used

To build it:

- JDK 17
- Android SDK 36
- ADB for installation and connected-device tests

The Gradle wrapper downloads the required Gradle version.

## Build

Clone the repository and build the debug APK:

```bash
git clone https://github.com/metanet/no_bs_launcher.git
cd no_bs_launcher
./gradlew assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it without removing an existing debug installation:

```bash
adb devices
adb -s <device-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Open the launcher once and select it as the Home app through the device's
Android settings or launcher chooser. Home-role behavior differs between TV
firmware, so inspect and record the existing Home component before changing a
device. Do not copy package-management commands from one TV model to another.

Signed release creation and guarded in-place installation are documented in
[Installation and rollback](docs/INSTALL_AND_ROLLBACK.md).

## Test

Run lint and all local unit tests:

```bash
./gradlew lintDebug lintRelease test
```

With an authorized Android TV or emulator connected, run the instrumentation
suite:

```bash
adb devices
./gradlew connectedCheck
```

The connected suite exercises remote focus and navigation, settings and state
restoration, favorites, app actions, shortcut management, favicon rendering,
system labels, VPN monitoring, and device statistics. Its package-mutation
fixture is test-owned; it does not disable arbitrary apps on the device.

## Developing with coding agents

Coding agents should read [AGENTS.md](AGENTS.md) before modifying this
repository. Human contributors can use
[Developing with coding agents](docs/DEVELOPING_WITH_AGENTS.md) for the prompt
template, safe device workflow, review checklist, and definition of done.

## Architecture

| Area | Responsibility |
| --- | --- |
| `ui` | Home, Settings, shortcut editor, remote focus, and app grids |
| `model` | Launcher configuration, favorites, app sections, and shortcuts |
| `data` | Local persistence, app discovery, favicons, and protected HTTP access |
| `stats` | CPU, memory, storage, and network sampling and presentation |
| `status` | Connected Wi-Fi labels and provider-neutral VPN state |
| `time` | Clock and date updates |

Application and favicon catalogs are loaded asynchronously and cached at
application scope. Package changes invalidate the app catalog. Website probes
are cancellable, favicon network work is bounded, and canceled or stale UI
results are suppressed. Activity recreation preserves unsaved Settings and
shortcut-editor state.

Unit tests live under `app/src/test`; device and UI tests live under
`app/src/androidTest`.

## Remote controls

- **OK:** launch an item or finish moving a favorite
- **Long press OK:** open the selected app or shortcut menu
- **Left/Right:** navigate, or shift an item while Move mode is active
- **Back:** leave a screen or cancel Move mode without saving its new position

App removal always opens Android's confirmation UI. The launcher cannot
silently uninstall another application.

## Current limitations

- Making a third-party launcher the default Home app is firmware-dependent.
- Android may hide the connected SSID unless location permission and the
  device's Location service are enabled. When unavailable, the line is omitted.
- Web shortcuts require an installed browser that accepts HTTP(S) intents.
- CPU utilization and network rates are best-effort measurements based on the
  interfaces exposed by the device kernel.

## License

Copyright (c) 2026 Basri Kahveci. Licensed under the [MIT License](LICENSE).
