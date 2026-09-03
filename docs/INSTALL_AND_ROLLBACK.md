# No bullshit launcher installation and rollback

No bullshit launcher is a vendor-neutral Android TV launcher with package name
`dev.basri.android.nobs_launcher`. It supports Android TV 6.0 / API 23 and
newer.

## Release signing, build, and install

Keep release signing material outside the repository. The ignored
`keystore.properties` file identifies the external key without containing its
password:

```properties
storeFile=<release-keystore-path>
keyAlias=<release-key-alias>
```

The maintainer release script retrieves signing passwords from an operating
system credential store and must not print them. Never put a keystore,
credential, device address, or local secret-store identifier in documentation
or source control.

With one authorized Android TV or emulator connected over ADB, scope connected
tests to that target, build the signed release, and install it:

```bash
ANDROID_SERIAL=<device-serial> scripts/build_release_apk.sh
scripts/install_release_apk.sh <device-serial>
```

The build script runs lint, all local and connected tests, and both debug and
release builds before accepting the signed APK. The install script verifies
the pinned signing certificate, uses only `adb install -r`, and refuses to
update an installed package signed by a different key. Disconnect every
unauthorized device before running either command.

## Home app list controls

Favorites appear first in their saved order. A divider separates them from all
other launchable apps and web shortcuts, which are sorted alphabetically.

- Long-press a favorite app for Move, Remove from favorites, or Uninstall app.
- Long-press another app for Add to favorites or Uninstall app.
- Long-press a web shortcut to add/remove it from Favorites, edit it, remove it,
  or move it when it is already a favorite.
- In Move mode, Left/Right shifts the favorite, OK saves, and Back cancels.

Uninstall app opens Android's own confirmation screen; the launcher cannot
silently remove an application.

## Settings quick actions and build identity

Web shortcuts, Android settings, Build info, and Save stay in the top Settings
row, with left/right remote navigation between them. Build info opens a dialog
whose separate left-aligned lines report the short Git revision (`-dirty` when
applicable) and UTC build date. CPU utilization is the final value on the Home
CPU row, matching the percentage placement used by Memory and Storage. Network
ingress and egress use separate one-line rows so changing rates cannot wrap the
left panel.

## Web shortcuts

Open Settings, then **Web shortcuts**, to add, edit, or remove a named HTTP(S)
address. A shortcut appears as a normal Home tile and opens through Android's
default web browser. New shortcuts begin in the alphabetical section and can
be promoted to Favorites from their Home long-press menu.

Saving a shortcut normalizes the user-entered URL and sends a bounded `HEAD`
request. The probe follows at most five allowed HTTP(S) redirects. A 2xx, 401,
or 403 response is reachable; every other result is inaccessible. While the
probe runs, **Checking website...** is shown; Save, the name field, and the URL
field are disabled, while Cancel remains available. Cancellation stops the
active probe. An inaccessible site leaves the editor open, re-enables its
controls, shows **Website is not accessible.**, and does not change stored
shortcut data.

The probe intentionally reads no page body, so it does not discover declared
icon metadata from HTML. After a reachable shortcut is stored, the only
favicon candidate is `/favicon.ico` at the final response URL's origin. A
failed or invalid favicon uses the bundled globe fallback without undoing the
shortcut save. Favicon fetching and image decoding are asynchronous and
bounded, but are not treated as universally cancellable. There is no
third-party icon service, WebView, page JavaScript, or background refresh.

URLs containing credentials are rejected. The Home list hides query and
fragment values, while storage and browser launch retain the full normalized
URL. HTTPS requests may redirect only to HTTPS; a downgrade to HTTP is
rejected. Starting from a public address rejects a redirect to a literal
non-public address and rejects a non-public network peer, including a private
DNS result. An explicitly entered local service using `localhost` or a literal
non-public address is allowed to remain in local address space. Cleartext HTTP
is used only when the user explicitly configures an HTTP shortcut. These same
redirect and peer checks protect the favicon request.

## First launch and Home selection

Open No bullshit launcher once, enter the optional welcome text, choose which
system labels and panels to show, select favorite apps, and select Save. Wi-Fi
is read from Android rather than typed into Settings. Android treats the
connected network name as location-sensitive, so approve the one-time location
permission and keep the device's Location service enabled if that label should
be visible. The displayed location is the final component of the system
timezone; it is not a GPS lookup.

Changing the Home app requires explicit authority for the named device. First
record the current Home component and retain it as `<stock-home-component>`:

```bash
adb -s <device-serial> shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.HOME
```

Then ask Android to select No bullshit launcher and verify the resolver after
pressing Home:

```bash
adb -s <device-serial> shell cmd package set-home-activity --user 0 \
  dev.basri.android.nobs_launcher/.ui.HomeActivity
adb -s <device-serial> shell input keyevent KEYCODE_HOME
```

Home-role behavior differs between firmware versions. If the recorded stock
launcher continues to override the selected Home component, do not guess a
package name or copy a command from another device. Only with explicit
device-specific authority and a tested rollback may the recorded stock package
be disabled temporarily:

```bash
adb -s <device-serial> shell pm disable-user --user 0 <stock-home-package>
adb -s <device-serial> shell input keyevent KEYCODE_HOME
```

Confirm the exact package belongs to the recorded stock Home component, record
its original enabled state, and verify the requested Home component before
ending the session.

## Rollback

No bullshit launcher itself does not disable or remove another app. If an
authorized setup temporarily disabled the recorded stock Home package, restore
its original enabled state and selected component:

```bash
adb -s <device-serial> shell pm enable --user 0 <stock-home-package>
adb -s <device-serial> shell cmd package set-home-activity --user 0 \
  <stock-home-component>
adb -s <device-serial> shell input keyevent KEYCODE_HOME
```

Run the `enable` command only when the package was enabled before setup. If no
package state changed, clear this launcher's preferred-activity state and press
Home to use Android's launcher chooser:

```bash
adb -s <device-serial> shell cmd package clear-preferred-activities \
  dev.basri.android.nobs_launcher
adb -s <device-serial> shell input keyevent KEYCODE_HOME
```

Verify that Android resolves the recorded stock component. Do not uninstall a
launcher or clear its data to switch Home apps.

## Verified release

- APK: `app/build/outputs/apk/release/app-release.apk`
- Version: `0.5.0` (`versionCode=10`)
- Verify the artifact SHA-256 with
  `shasum -a 256 app/build/outputs/apk/release/app-release.apk`.
- Build identity is available from the launcher's **Build info** dialog.
- Minimum Android: 6.0 / API 23
- Signing certificate SHA-256:
  `d1702f54c1ba471b3a719c89dd8b60bc5e5f2445364c155027761c75f8a9cd88`

The version and certificate digest are public release identity, not signing
credentials.

## Privacy boundary

The release requests `android.permission.ACCESS_COARSE_LOCATION`,
`android.permission.ACCESS_FINE_LOCATION`,
`android.permission.ACCESS_NETWORK_STATE`,
`android.permission.ACCESS_WIFI_STATE`, `android.permission.INTERNET`, and
`android.permission.REQUEST_DELETE_PACKAGES`. Location and Wi-Fi state are used
only to ask Android for the connected network label; the launcher performs no
Wi-Fi scan, GPS lookup, or background location access.

Internet access is limited to the user-triggered website probe and favicon
request described above. Runtime HTTP uses OkHttp `5.4.0` with no cookies,
HTTP authentication, proxy authentication, response cache, automatic
redirects, or connection retries. The cancellable `HEAD` probe allows at most
five redirects, 4 seconds per connect/read operation, and 12 seconds overall.
It reads no response body. The final-origin favicon `GET` accepts at most 256
KiB, follows at most five allowed redirects, and has a hard 4-second deadline.
HTTPS downgrades and unexpected private-network destinations are rejected as
described above.

The launcher has no background-location, analytics, notification-listener,
accessibility, or broad package-query permission. Welcome text, visibility
settings, shortcut URLs, favorites, and favicon files remain in app-private
local storage with Android backup and device transfer disabled. Connected
network and timezone-derived location labels are not user-editable or
persisted.

VPN status is provider-neutral. The launcher displays `VPN connected` whenever
Android reports an active VPN transport and the panel is enabled. It does not
query or identify the VPN provider.
