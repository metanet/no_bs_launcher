# Settings quick actions and build information design

## Goal

Make the launcher status and Settings screen easier to scan and operate with a
TV remote:

- put CPU utilization at the right-hand end of the CPU value, matching Memory
  and Storage;
- keep Web shortcuts, Android settings, and Save permanently available in the
  top Settings row; and
- show the source build identity and UTC build date in Settings.

## Dashboard CPU row

The CPU value keeps the existing information and data sources. Only its order
changes to `capacity · utilization`: core count, optional maximum frequency,
then the measured percentage. While the first sample is pending, `measuring…`
occupies the same final position. If utilization is unavailable, the explicit
unavailable wording also remains last.

Examples:

- `CPU 4 cores · 1.8 GHz · 50%`
- `CPU 4 cores · measuring…`
- `CPU capacity unavailable · utilization unavailable`

## Settings header

The first row contains a left-aligned Settings title and a right-aligned action
group in this order: Web shortcuts, Android settings, Save. The controls are no
longer below the app list, so they remain visible without scrolling. Explicit
left/right focus links keep remote navigation within the action group.

The form, feature toggles, build information, and favorite-app selector remain
below the header. Existing save and first-run behavior is unchanged.

## Build information

A compact, read-only Build information section appears below the feature
toggles and above the app list. It shows:

- Build hash: the first 12 characters of the Git commit used to configure the
  build. `-dirty` is appended when tracked or untracked workspace changes are
  present. If Git metadata is unavailable, it reads `unknown`.
- Build date: `SOURCE_DATE_EPOCH` when provided, otherwise the Git commit time,
  formatted in UTC. If neither is available, the Unix epoch is used. This keeps
  the value deterministic for repeated builds of the same source revision.

Both values are generated into `BuildConfig` and rendered by
`SettingsActivity`; no device permission or network access is involved.

## Verification

Unit tests cover every CPU value ordering state. Connected tests verify the
three header actions share the same top position, sit above the app list, have
the intended D-pad order, and render non-empty build identity values. The full
lint, debug/release unit, release build, and connected-test gates must pass
before installation and TV smoke testing.
