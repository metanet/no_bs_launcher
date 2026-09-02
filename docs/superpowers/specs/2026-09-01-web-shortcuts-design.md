# Web shortcuts design

## Goal

Allow users to create named website shortcuts that appear in the Home grid as
regular tiles. A shortcut opens its configured page in Android's default web
browser and participates in favorites, mixed favorite ordering, move mode, and
the remaining alphabetical list alongside installed applications.

The implementation must remain vendor-neutral across the Box R and Xiaomi
Android TV devices and must preserve every existing setting and favorite during
the upgrade.

## Unified Home item model

Introduce a sealed `HomeItem` model with installed-app and web-shortcut
variants. Both variants expose a stable ID, label, artwork, and type-specific
launch behavior.

- Installed app IDs use `app:<package-name>`.
- Web shortcut IDs use `web:<uuid>`.
- `WebShortcut` stores its UUID, name, normalized URL, and optional private
  favicon filename.

The Home grid, section composition, favorite policy, focus restoration, stable
RecyclerView IDs, and move mode operate on stable Home item IDs rather than
package names. This allows apps and shortcuts to be interleaved in one favorite
order. Non-favorite apps and shortcuts share one case-insensitive alphabetical
list below the existing separator. Equal labels are ordered by item type and
then stable ID so the grid does not reorder unpredictably between refreshes.

App-only operations remain type-safe: application launch and uninstall are
never offered to a shortcut, while browser launch and shortcut removal are
never applied to an installed app.

## Persistence and migration

Extend `LauncherConfig` with shortcut records and ordered favorite item IDs.
On first load after the upgrade, migrate every existing `selectedPackages`
entry to `app:<package-name>` in the same order. Migration retains the legacy
data until the new configuration has been committed successfully, so a failed
write cannot lose favorites.

Shortcut metadata is encoded with an escaping format that round-trips names
and URLs containing delimiters, Unicode, and whitespace. Favicons are stored as
bounded PNG files under the launcher's private files directory. Saving or
editing a shortcut uses its UUID as identity, so changes to name or URL do not
disturb favorite position.

Removing a shortcut deletes its metadata, removes its stable ID from favorites,
and deletes its favicon. Orphaned shortcut favorite IDs and missing installed
app IDs are removed during normalization without affecting valid items.

## Management interface

Add a **Web shortcuts** button to Settings that opens a dedicated management
screen. The separate screen avoids crowding the existing system and app
settings.

The management screen lists each shortcut's icon, name, and normalized URL. It
provides Add, Edit, and Remove actions suitable for D-pad navigation:

- Add opens an editor for name and URL.
- Edit reuses the same editor and retains the shortcut UUID.
- Remove requires confirmation before deleting the shortcut and its favorite
  reference.
- Back returns to Settings without discarding shortcut changes that were
  already confirmed in the management screen.

New shortcuts start as non-favorites and appear in the alphabetical section.
They can then be added to favorites from Home like an installed application.

The Home long-press menus are type-specific:

- Non-favorite shortcut: **Add to favorites**, **Edit shortcut**, **Remove
  shortcut**.
- Favorite shortcut: **Move**, **Remove from favorites**, **Edit shortcut**,
  **Remove shortcut**.

Edit opens the shortcut editor. Remove always asks for confirmation. Existing
installed-app menus remain unchanged.

## URL validation and browser launch

Trim user input. If no scheme is present, prepend `https://`. Accept only
absolute `http` and `https` URLs with a non-empty host. Reject malformed URLs
and all other schemes, including `file`, `content`, `intent`, `javascript`, and
custom application schemes.

Clicking a shortcut sends an implicit `Intent.ACTION_VIEW` for the normalized
URI. Android selects the configured default browser. If no handler exists or
launching fails, Home uses the existing temporary **Unable to open** banner and
remains usable.

## Favicon behavior and privacy

Declare the normal `android.permission.INTERNET` permission and keep the
manifest privacy test as an exact allowlist.

When a shortcut is added, fetch the favicon once from `/favicon.ico` on the
configured website origin. When the URL changes, fetch it once again. Do not
contact a third-party favicon service, send cookies, refresh favicons in the
background, or contact shortcut sites merely because Home is displayed.

The save operation commits valid shortcut metadata first, then performs the
one-time favicon request off the UI thread and refreshes the tile when it
finishes. A process exit can leave the already-saved shortcut using the generic
icon, but it does not schedule a later retry. After a valid URL change, remove
the previous site's favicon before showing the generic fallback or atomically
replacing it with the newly fetched image. A validation or metadata-save
failure leaves the previous record and favicon untouched.

Use bounded connection/read timeouts, a small response-size limit, normal HTTP
redirect handling restricted to `http` and `https`, image decoding validation,
and atomic temporary-file replacement. Scale successful images to the tile's
maximum useful size and store them as PNG. A timeout, HTTP error, oversized
response, unsupported redirect, invalid image, filesystem failure, or missing
favicon never prevents shortcut creation; the tile uses a bundled generic
globe icon instead.

## Transaction and error behavior

Name is required, trimmed, and length-limited. URL validation completes before
metadata changes. Confirmed metadata is persisted independently of favicon
success. An edit that fails validation leaves the previous record and icon
untouched.

Shortcut storage mutations report save failures and keep the previous in-memory
and on-disk state. Removal confirmation defaults to cancellation. Browser and
favicon failures never crash or block Home.

## Verification and release

Use test-driven development for each behavior. Required automated coverage:

- URL normalization and rejection of unsafe schemes.
- Legacy package-favorite migration with exact order preservation.
- Shortcut serialization for delimiters, Unicode, and malformed stored data.
- Unified favorites, alphabetical remaining items, normalization, removal, and
  mixed app/shortcut move behavior.
- Add, edit, and delete policies, including favorite and favicon cleanup.
- Favicon success, redirects, timeouts, invalid images, size limits, and generic
  fallback using controlled local responses or injected fetch dependencies.
- Connected management-screen creation, editing, removal, persistence, D-pad
  focus, and validation errors.
- Connected Home long-press menus, add/remove favorite, mixed move mode, and
  exact `ACTION_VIEW` URL dispatch.
- All existing app launch, uninstall, layout, settings, privacy, and system-stat
  tests.

Publish version `0.3.0` with version code `4`. Run lint, both unit variants, the
full connected-device suite, debug/release builds, R8/resource shrinking,
signing, and certificate verification. Install the signed APK in place on the
Box R and verify the previous configuration and favorites survived.

Create a temporary real shortcut, confirm that its favicon or globe fallback
appears as a normal tile, open it in the default browser, then remove it and
confirm its tile and private icon disappear. Leave the installed release on
Home without sending mute, volume, or power commands.
