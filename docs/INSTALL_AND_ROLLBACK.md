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
other launchable apps, which are sorted alphabetically.

- Long-press a favorite for Move, Remove from favorites, or Uninstall app.
- Long-press another app for Add to favorites or Uninstall app.
- In Move mode, Left/Right shifts the favorite, OK saves, and Back cancels.

Uninstall app opens Android's own confirmation screen; the launcher cannot
silently remove an application.

## First launch and Home selection

Open No bullshit launcher once, enter the welcome, Wi-Fi, and location labels,
choose the optional panels and apps to show, and select Save. Android can then
be asked to make it Home:

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
- Version: `0.2.0`
- Minimum Android: 6.0 / API 23
- Size: 213,888 bytes
- Signing certificate SHA-256:
  `d1702f54c1ba471b3a719c89dd8b60bc5e5f2445364c155027761c75f8a9cd88`

## Privacy boundary

The release requests only `android.permission.ACCESS_NETWORK_STATE`. It has no
Internet, location, analytics, notification-listener, accessibility, or broad
package-query permission. Labels and app order remain in app-private local
preferences with Android backup and device transfer disabled.

VPN status is provider-neutral. The launcher displays `VPN connected` whenever
Android reports an active VPN transport and the panel is enabled. It does not
query or identify the VPN provider.
