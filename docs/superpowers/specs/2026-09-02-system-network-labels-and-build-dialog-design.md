# System network labels and build dialog design

## Goal

Use system-derived Wi-Fi and location labels, prevent network-rate wrapping,
and move build identity out of the Settings form into an on-demand dialog.

## Home status panel

The existing combined network row becomes two one-line rows:

```text
Ingress ↓ 2.0 KB/s
Egress  ↑ 1.0 KB/s
```

Each row independently displays `measuring…` or `unavailable` when the shared
network counter source has not produced a rate. Both TextViews are constrained
to one line so they cannot repeatedly change the height of the left panel.

The Wi-Fi line is no longer a user-entered label. It displays the SSID reported
by Android. SSID quotes are removed, blank values and Android's unknown-SSID
sentinel are rejected, and unavailable access is reported honestly. Android
12 and newer use the active network's `WifiInfo`; older or redacted results
fall back to `WifiManager.connectionInfo` for Android 6 through 11.

The location line is derived locally from Android's configured timezone. For
example, `Europe/London` becomes `London` and `Europe/Istanbul` becomes
`Istanbul`. Android TV normally lacks GPS and the current Box R has no last
system location, so this is the only stable, vendor-neutral system location
label that requires no third-party geolocation request. Underscores become
spaces; a non-region timezone such as `GMT` remains `GMT`.

## Permission behavior

Android treats SSID as location-sensitive. The manifest adds
`ACCESS_WIFI_STATE` and the runtime coarse/fine location permission pair, while
declaring Wi-Fi and location hardware optional so the APK remains installable
on Ethernet-only TV devices. Android 12 and newer require coarse and fine to be
requested together, while SSID access itself still requires fine access. No
background location and no nearby-network scan is used.

Android also redacts the SSID when the device-wide Location service is off,
even after the runtime permission is granted. In that state the launcher keeps
the feature enabled but displays `Wi-Fi unavailable`; it does not silently
change the device's privacy setting. Box R currently has Location disabled.

When Wi-Fi display is enabled and permission has never been requested, Home
records that it has initiated the request and shows Android's one-time runtime
dialog. A denial does not create a prompt loop; Home shows `Wi-Fi unavailable`.
Turning **Show Wi-Fi name** off and on in Settings provides an explicit retry.

## Settings

The editable Wi-Fi and location text boxes are removed. One horizontal toggle
row contains:

- Show Wi-Fi name
- Show location
- Show VPN status
- Show system stats

`showWifiName` is a new persisted boolean that defaults to true for existing
installations. The obsolete stored manual label keys are removed the next time
configuration is saved; other launcher state remains unchanged.

The inline Build information section is removed. A **Build info** button is
inserted in the top action row between Android settings and Save. Pressing it
opens a left-aligned dialog with exactly two lines: Build hash and Build date.
The existing generated Git hash, dirty marker, and UTC date remain unchanged.

## Lifecycle and failures

System labels are refreshed whenever Home resumes, which covers returning from
Android Wi-Fi/timezone settings without a background service. An unavailable
SSID, denied permission, Ethernet connection, missing timezone, or framework
exception never crashes Home and produces an explicit unavailable label.

## Verification

Pure unit tests cover ingress/egress formatting, unknown SSIDs, quote removal,
and timezone-to-location conversion. Connected tests verify the Box R system
reader returns the real SSID when Location is enabled and the explicit
unavailable state when it is disabled. They also verify manifest permissions,
Settings field removal/toggles, the Build info dialog and
D-pad order, one-line network views, and visibility persistence. The release
gate remains both lint variants, debug/release unit suites, the complete device
suite, signed builds, guarded in-place installation, and visual TV smoke tests.
