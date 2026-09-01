# Home header alignment design

## Goal

Align the top edges of the visible welcome label, Apps title, and Settings
button. Align the Settings button's right edge with the visible outer edge of
the rightmost app tile. Preserve the existing 20/80 panel split, tile sizes,
grid columns, and vertical scrolling.

## Layout

The existing horizontal header remains a `LinearLayout`. Its children will use
top gravity instead of vertical centering, with baseline alignment disabled so
the different text metrics cannot offset either child. This makes the Apps
title view and Settings button start at the same screen Y coordinate as the
welcome view at the top of the information panel.

The Settings button will receive an 18dp end margin. This equals the app grid's
6dp padding plus the app tile's 12dp outer margin, so its right edge matches the
right edge of the fourth visible tile. A named dimension will express this
combined column-edge inset rather than duplicating an unexplained literal.

The Apps title and header container will receive IDs solely for measured layout
verification. No activity logic, focus behavior, settings behavior, or stored
configuration changes.

## Verification

The connected Home layout test will assert:

- `welcome.top == apps_title.top == settings.top`
- `settings.right == rightmost visible app tile.right`

The existing 20/80, favorite ordering, separator, and remaining-app assertions
will remain. After the focused connected test passes, run the complete lint,
unit, connected-device, debug/release build, shrinking, and signing gate. Then
install the signed update in place and inspect the physical 1920x1080 Home
screen without changing audio or power state.
