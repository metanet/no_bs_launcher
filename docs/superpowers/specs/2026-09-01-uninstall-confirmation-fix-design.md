# Uninstall confirmation fix design

## Problem

Long-pressing an app and selecting **Uninstall app** starts Android's package
uninstaller, but Android immediately rejects the request. The Box R log records
that the launcher UID lacks both `android.permission.REQUEST_DELETE_PACKAGES`
and the privileged `android.permission.DELETE_PACKAGES` permission.

Disney+ (`com.disney.disneyplus`) and World Radios
(`com.digitalapps.worldradios`) are installed under `/data/app`, so they are
ordinary removable applications rather than protected system packages.

## Behavior

The launcher will request only the normal
`android.permission.REQUEST_DELETE_PACKAGES` permission. Selecting **Uninstall
app** will continue to send the selected package URI to Android's standard
uninstaller. Android remains responsible for showing the confirmation screen
and removing the package only after the user approves it.

The launcher will not use root, ADB, device-owner APIs, the privileged
`DELETE_PACKAGES` permission, or any silent-uninstall mechanism. The flow stays
vendor-neutral for both the Box R and Xiaomi Android TV devices.

Favorites are not changed when the confirmation opens or when the user
cancels. The installed-app catalog will naturally remove a successfully
uninstalled package the next time Home resumes and reloads the catalog.

## Failure handling

The current **Unable to uninstall _app_** banner remains the fallback when
Android has no activity capable of handling the uninstall intent or when
starting that activity throws. No new permission prompt is needed because
`REQUEST_DELETE_PACKAGES` is a normal manifest permission.

## Verification

Add a connected regression test that asks `PackageManager` whether the launcher
package holds `REQUEST_DELETE_PACKAGES`. This test must fail before the manifest
change and pass after it. Retain the existing test that verifies the uninstall
intent action, package URI, and unchanged favorite state. Update the manifest
privacy test's exact allowlist to contain only `ACCESS_NETWORK_STATE` and
`REQUEST_DELETE_PACKAGES`; do not weaken it into a partial assertion.

Run the full lint, unit, connected-device, debug/release build, shrinking, and
signing gate. Build release version `0.2.1` with version code `3`, install it in
place, and verify the real Android confirmation screen opens for Disney+ and
World Radios. Cancel both confirmation screens and confirm both packages remain
installed. Leave the launcher on Home without changing audio or power state.
