# No bullshit launcher installation and rollback

No bullshit launcher is a private Android TV launcher with package name
`dev.basri.android.nobs_launcher`. The same signed APK is intended for Box R and
Xiaomi Android TV devices running Android 6.0 or newer.

## Build and install

The signing key is stored outside this project at
`/Users/basri/.android/kahveci-home-release.jks`. Its password is stored in the
macOS login keychain under service
`dev.basri.android.nobs_launcher.release`. The scripts never print it.

With the TV connected and authorized over ADB:

```bash
scripts/build_release_apk.sh
scripts/install_release_apk.sh 192.168.1.154:5555
```

The build script runs lint, all local tests, all connected-device tests, and
both debug and release builds before accepting the signed APK. The install
script uses only `adb install -r`; it refuses to update an existing package
whose signing certificate differs.

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

Saving a shortcut first normalizes the user-configured HTTP(S) page and sends a
direct GET. While this check runs, **Checking website...** is shown; Save, the
name field, and the URL field are disabled, while Cancel remains available.
Only 2xx, 401, and 403 responses are considered reachable. If the page is not
accessible, the editor stays open, controls are re-enabled, and **Website is
not accessible.** is shown inline on the URL. Nothing is persisted and an
existing shortcut's metadata, favorite state, and icon are unchanged.

For a reachable page, the shortcut is persisted even if favicon discovery or
download fails; the tile then uses the bundled globe fallback. Icon candidates
are, in order: declared `rel=icon` or `rel=shortcut icon` candidates in
document order, then declared Apple touch-icon candidates, with at most five
declared candidates total, followed by the final page origin's
`/favicon.ico`. Initial requests go only to the configured page and these
declared candidate URLs (plus that final-page origin fallback); bounded
HTTP(S) redirects may contact their HTTP(S) redirect targets. There is no
third-party icon service. Icons are fetched only as part of saving or changing
a shortcut, never by page JavaScript, a WebView, or a background refresh.

## First launch and Home selection

Open No bullshit launcher once, enter the optional welcome text, choose which
system labels and panels to show, select favorite apps, and select Save. Wi-Fi
is read from Android rather than typed into Settings. Android treats the SSID as
location-sensitive, so approve the one-time location permission and keep the
device's Location service enabled if the Wi-Fi name should be visible. The
displayed location is the final component of the system timezone (for example,
`Europe/London` displays `London`); it is not a GPS lookup. Android can then be
asked to make the app Home:

```bash
adb -s 192.168.1.154:5555 shell cmd package set-home-activity --user 0 \
  dev.basri.android.nobs_launcher/.ui.HomeActivity
```

Record the stock Home component before changing it:

```bash
adb -s 192.168.1.154:5555 shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.HOME
```

Confirm that Android actually resolves the requested component after pressing
Home. Some Android TV firmware assigns the Home role but still gives its system
launcher a higher resolver priority. Box R 4K Plus does this with
`com.google.android.tvlauncher`; the tested reversible workaround is:

```bash
adb -s 192.168.1.154:5555 shell pm disable-user --user 0 \
  com.google.android.tvlauncher
adb -s 192.168.1.154:5555 shell input keyevent KEYCODE_HOME
```

This is device configuration, not vendor-specific code in the APK. Do not copy
that package name to a different TV: first record and verify that device's own
stock Home component.

## Rollback

No bullshit launcher itself does not disable or remove another app. If the
device-specific workaround above was used, re-enable the recorded stock Home
package before selecting its component. The tested Box R rollback is:

```bash
adb -s 192.168.1.154:5555 shell pm enable --user 0 \
  com.google.android.tvlauncher
adb -s 192.168.1.154:5555 shell cmd package set-home-activity --user 0 \
  com.google.android.tvlauncher/.MainActivity
adb -s 192.168.1.154:5555 shell input keyevent KEYCODE_HOME
```

Alternatively, clear the current default and press Home to use Android's
launcher chooser:

```bash
adb -s 192.168.1.154:5555 shell cmd package clear-preferred-activities \
  dev.basri.android.nobs_launcher
```

Do not uninstall either launcher to switch between them.

## Verified release

- APK: `app/build/outputs/apk/release/app-release.apk`
- Version: `0.5.0` (`versionCode=10`)
- Verify the artifact SHA-256 with `shasum -a 256 app/build/outputs/apk/release/app-release.apk`.
- Build identity is available from the launcher's **Build info** dialog.
- Minimum Android: 6.0 / API 23
- Signing certificate SHA-256:
  `d1702f54c1ba471b3a719c89dd8b60bc5e5f2445364c155027761c75f8a9cd88`

## Privacy boundary

The release requests `android.permission.ACCESS_COARSE_LOCATION`,
`android.permission.ACCESS_FINE_LOCATION`,
`android.permission.ACCESS_NETWORK_STATE`, `android.permission.ACCESS_WIFI_STATE`,
`android.permission.INTERNET`, and `android.permission.REQUEST_DELETE_PACKAGES`.
Fine location and Wi-Fi state are used only to ask Android for the connected
SSID; the launcher performs no Wi-Fi scan, GPS lookup, or background location
access. Internet access is used only for
the user-triggered website check and favicon requests described above. Runtime
HTTP uses OkHttp `5.4.0`. Requests use no cookies, HTTP authentication,
proxy authentication, or response cache. There is no page JavaScript, WebView,
or background refresh. Page HTML is read up to 256 KiB; page checking allows at
most five redirects, 4 seconds per connection/read operation, and 12 seconds
overall across redirects. Icon responses up to 256 KiB are accepted; each icon
request allows at most five redirects and has a hard 4-second limit. HTTP and
HTTPS are the only schemes; cleartext HTTP is deliberately permitted for
user-configured HTTP destinations, their icon URLs, and any HTTP redirect
targets reached from HTTP or HTTPS initial requests.

The launcher has no background-location, analytics, notification-listener,
accessibility, or broad package-query permission. The welcome text, visibility
settings, URLs, favorites, and favicon files remain in app-private local storage
with Android backup and device transfer disabled. Wi-Fi and timezone-derived
location labels are not user-editable or persisted.

VPN status is provider-neutral. The launcher displays `VPN connected` whenever
Android reports an active VPN transport and the panel is enabled. It does not
query or identify the VPN provider.
