# Favorites, all apps, and CPU utilization design

## Scope

No bullshit launcher will keep the existing configurable Favorites experience,
show every other launchable application below it, and provide working
whole-system CPU utilization when Android exposes a usable counter. The same
APK must remain portable across Box R, Xiaomi, and other standard Android TV
devices without new permissions or vendor-specific APIs.

## Screen proportions

The Home screen changes from a 30/70 split to an exact 20/80 split. The left
information panel uses one layout-weight unit and the right application panel
uses four. Existing padding and overflow protection remain, with the left-panel
text sizes adjusted only where necessary to prevent truncation at 1920x1080.

## Application sections

The right side remains one vertically scrollable four-column RecyclerView so
remote focus and scrolling work as a single continuous surface.

1. Favorites appear first in the exact order stored in `selectedPackages`.
2. When both sections contain apps, a non-focusable horizontal separator spans
   all four columns.
3. Every launchable app not in Favorites follows, sorted case-insensitively by
   display label and then package name.

The separator is omitted when Favorites or the remaining section is empty.
The launcher itself remains excluded from the catalog. Settings checkboxes are
relabeled as Favorite-app selection; checking an app appends it to Favorites,
and unchecking it returns the app to its alphabetical position below the
separator.

The adapter uses explicit favorite-tile, separator, and remaining-tile items.
Only app tiles are focusable. Stable IDs remain package-based, while the
separator uses its own reserved stable ID. The grid layout gives the separator
all four spans and every app tile one span.

## Long-press actions

Long-pressing a remaining app opens a TV-friendly modal action list containing:

1. Add to favorites
2. Uninstall app

Adding closes the modal, appends the package to Favorites, persists the updated
configuration synchronously, recomposes both sections, and focuses the moved
tile in its new favorite position.

Long-pressing a favorite opens:

1. Move
2. Remove from favorites
3. Uninstall app

Removing closes the modal, removes the package from Favorites, persists the
configuration, places the app back in alphabetical order below the separator,
and focuses the moved tile there.

`Uninstall app` always starts Android's standard package-deletion confirmation
for the selected package. The launcher never requests package-deletion
permission and never silently removes an app. Android decides whether a system
or protected app can be removed. If no confirmation activity can handle the
request, the launcher stays visible and reports a short error. On resume, the
catalog is reloaded; a successfully removed package disappears and stale
favorite configuration is normalized away.

## Favorite move mode

Choosing Move closes the modal and enters the existing highlighted move mode.
Only the favorite subsequence participates:

- Left removes the moving favorite and reinserts it one position earlier.
- Right removes it and reinserts it one position later.
- Movement is linear across visual row boundaries, so Right at the last column
  continues to the first position of the following Favorites row and Left does
  the reverse.
- Neighboring favorites shift by one position rather than being swapped.
- Left at the first favorite and Right at the last favorite are harmless.
- OK atomically persists the current favorite order and exits move mode.
- Back restores the full pre-move favorite order and exits move mode.
- Up and Down do not change the order while move mode is active.

The separator and remaining apps never move and cannot enter move mode.

## CPU utilization

The left system panel continues to show one compact CPU row containing
utilization, logical core count, and maximum frequency. Utilization is sampled
with the existing two-second lifecycle-bound monitor.

The reader tries two provider-neutral whole-system sources in order:

1. `/proc/stat`, using busy-versus-total tick deltas as today.
2. The per-core standard sysfs `cpuidle/state*/time` counters. Their cumulative
   idle-microsecond delta is subtracted from
   `elapsed monotonic time * logical core count`; the remaining busy capacity is
   converted to a percentage and clamped to 0 through 100.

The cpuidle counters measure genuine whole-system idle residency, not process
CPU, frequency, or load average. They are used only when `/proc/stat` is
inaccessible. Every online logical core must expose readable state counters;
partial core data is rejected rather than overstating utilization.
Counter resets, non-positive elapsed time, missing cores, or malformed values
do not produce a false percentage. The first valid sample displays measuring;
subsequent valid deltas display utilization. If neither source is readable, the
row explicitly reports utilization unavailable while preserving capacity.

No new production permission, service, or background process is introduced.

## State and failure behavior

`selectedPackages` remains the sole persisted favorite-order source of truth,
so this update migrates existing users without a preference schema change.
Section composition is a pure policy operation. The Home activity owns only the
current catalog, favorite list, move snapshot, and selected action target.

App additions/removals and move completion use synchronous preference commits,
matching first-run behavior. A failed launch or failed uninstall-intent start
does not alter Favorites. Package changes are reflected on the next Home resume.

## Verification

Pure unit tests will cover section composition, case-insensitive remaining-app
ordering, separator presence rules, favorite add/remove order, cross-row linear
movement, cpuidle utilization deltas, clamps, resets, and `/proc/stat`
preference over cpuidle fallback.

Connected tests will cover the 20/80 width ratio, Favorites before the
full-width separator, alphabetical remaining apps, focusability, both
long-press menus, add/remove persistence, move OK persistence, Back rollback,
uninstall confirmation launch/cancel, and live CPU utilization or the explicit
unavailable fallback.

Final verification will run zero-warning debug/release lint, both unit-test
variants, the complete connected-device suite, debug/release assembly, R8 and
resource shrinking, signature verification, guarded release installation, and
physical D-pad smoke tests. Existing permission, socket, crash/ANR, Home,
rollback, and retained-service checks remain required.
