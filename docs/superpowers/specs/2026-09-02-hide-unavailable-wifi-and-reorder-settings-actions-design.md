# Hide Unavailable Wi-Fi and Reorder Settings Actions

## Goal

Keep the launcher home screen factual and uncluttered when Android does not expose
the connected Wi-Fi SSID, and put the settings actions in Basri's preferred order.

## Home Wi-Fi behavior

`SystemLabelReader` remains the only source of the Wi-Fi name. `HomeActivity` asks
it for the current SSID whenever the home content is rendered. When the result is a
non-empty normalized name, the Wi-Fi row is visible and displays that name. When
the result is absent, including Android returning `<unknown ssid>` because Location
is disabled, the entire Wi-Fi row is `GONE` and occupies no layout space.

The launcher will not display an unavailable message, cache a previous SSID, infer
the name from network addresses, or restore a manually editable Wi-Fi field.

## Settings action order and navigation

The top action row is ordered from left to right as:

1. Save
2. Web Shortcuts
3. Android Settings
4. Build info

Explicit left/right focus links follow the same sequence. Pressing Down from any
top-row action continues to focus the welcome-text field, and pressing Up from the
welcome-text field focuses Save, the first action.

## Testing

Connected UI tests cover both available and unavailable Wi-Fi names without
hard-coding a particular SSID. They verify that the Wi-Fi row is visible only when
the system reader returns a usable name. Settings UI tests verify the visual order,
horizontal alignment, and every left/right/up/down focus link. The full debug and
release unit suites, connected Android suite, lint checks, and release build run
before completion.

## Source control

Basri authorized the reviewed implementation commits and local merge after the
verified worktree handoff.
