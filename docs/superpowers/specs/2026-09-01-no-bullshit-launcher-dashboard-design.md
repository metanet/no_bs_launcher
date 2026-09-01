# No bullshit launcher dashboard design

## Product identity

The application label becomes `No bullshit launcher` and the Android
application ID/namespace becomes `dev.basri.android.nobs_launcher`. The source
project remains in its existing local directory so the already-created design,
test, and delivery history stays together. The same signed APK remains portable
across standard Android TV devices, including Box R and Xiaomi.

## Chosen approach

Three approaches were considered:

1. Extend the existing single Settings screen with personalization, visibility
   toggles, and the app list. This is selected because it has one obvious place
   to configure the launcher and the fewest remote-navigation levels.
2. Split personalization and app management into separate screens. This gives
   each page more space but adds menu depth and Back-button complexity.
3. Use an always-running service or privileged system overlay for monitoring.
   This could collect more metrics but would increase background work,
   permissions, and vendor dependence, conflicting with the lightweight goal.

## Home composition

The existing 30/70 layout remains. The right side keeps the four-column,
manually ordered app grid. `Manage apps` becomes `Settings`.

The left panel is ordered as follows:

```text
Welcome text (only when nonblank)

23:41
Tuesday, 1 September
Kahveci House
London                  (optional)
VPN connected           (optional and only while active)

Memory   1.2 / 2.0 GB   60%       (optional system panel)
CPU      43% · 4 cores · 1.8 GHz
Storage  4.3 / 8.0 GB   54%
Network  ↓ 1.2 MB/s · ↑ 96 KB/s
```

Wi-Fi remains visible because Basri explicitly requested it in the approved
launcher design. The location line, generic VPN line, and complete system panel
can each be enabled or disabled independently. The welcome line is hidden when
blank. Long welcome text is constrained to two lines and ellipsized so it
cannot displace the clock or app grid.

## Settings behavior

The Settings screen contains, in remote focus order:

1. Welcome text.
2. Wi-Fi name.
3. Location.
4. `Show location`, `Show VPN status`, and `Show system stats` checkboxes.
5. The selectable installed-app list.
6. Android Settings and Save buttons.

New installations default all three visibility switches to enabled and all
apps to hidden. Save atomically persists labels, switches, and selected app
order. Back is blocked during first-run setup; on later visits Back discards
the entire working copy. Newly installed apps remain unchecked.

## Generic VPN status

The launcher tracks networks with Android's public
`NetworkCapabilities.TRANSPORT_VPN`. It displays exactly `VPN connected` while
at least one such network is active and the setting is enabled. It does not
identify or query NordVPN, infer provider identity, inspect notifications, or
request Internet access. This works with any standards-compliant VPN app.

## System statistics

`SystemStatsMonitor` is lifecycle-bound to the visible Home activity and
samples every two seconds only while the system panel is enabled.

- Memory capacity and use come from `ActivityManager.MemoryInfo`.
- Storage capacity and use come from `StatFs` for Android's data volume.
- CPU capacity is the runtime processor count plus the maximum readable CPU
  frequency from standard `/sys/devices/system/cpu` entries. Whole-system CPU
  use is calculated from deltas in `/proc/stat` when Android permits access.
- Network ingress/egress are rates calculated from deltas in
  `TrafficStats.getTotalRxBytes()` and `getTotalTxBytes()`.

Every metric is best-effort and permission-free. Unsupported or inaccessible
values render as `unavailable`; the launcher never substitutes app-process CPU
use for system CPU use and never labels cumulative network bytes as a rate.
Byte values use binary units with one decimal below 10 units and whole numbers
above that. Percentages are clamped to 0 through 100. Sampling uses immutable
per-monitor snapshots and has no shared mutable global state.

## Privacy and resource boundaries

The only production permission remains
`android.permission.ACCESS_NETWORK_STATE`. The NordVPN package query is
removed. There is no Internet, location, usage-stats, notification-listener,
accessibility, storage, or broad package-query permission. Settings remain in
private preferences with backup and device transfer disabled. The monitor
stops all callbacks when Home is stopped and performs file reads off the main
thread.

## Failure behavior

If a metric source cannot be read, its row stays stable and reports
`unavailable`; other rows continue updating. If counters reset or time does not
advance, that sample reports zero rate rather than a negative or infinite
value. App launch failure continues to show an in-app message and keeps Home
resumed. Settings save remains synchronous so first-run completion cannot race
the Home activity.

## Verification

Pure unit tests cover configuration defaults/toggles, byte and rate formatting,
CPU delta calculation, counter reset handling, and generic VPN policy. Connected
tests cover Settings persistence/discard, conditional panel visibility,
provider-neutral VPN wording, live system values/fallbacks, app selection/order,
launch failure, exact permissions, backup exclusion, and Home registration.

The final signed release is installed non-destructively on Box R, selected as
Home, exercised with ADB key events equivalent to the TV remote, and checked
for crashes, ANRs, memory, sockets, permissions, retained media/remote/CEC
features, and stock-launcher rollback. Xiaomi remains architecturally supported
but physically unverified until connected.
