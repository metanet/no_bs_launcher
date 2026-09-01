#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <adb-device-serial>" >&2
    exit 2
fi

device_serial="$1"
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
apk="$project_dir/app/build/outputs/apk/release/app-release.apk"
package_name="dev.basri.android.nobs_launcher"
apksigner="/opt/homebrew/share/android-commandlinetools/build-tools/36.0.0/apksigner"

if [[ "$(adb -s "$device_serial" get-state 2>/dev/null)" != "device" ]]; then
    echo "ADB device is not connected and authorized: $device_serial" >&2
    exit 1
fi
if [[ ! -f "$apk" ]]; then
    echo "Build the signed release APK first: scripts/build_release_apk.sh" >&2
    exit 1
fi
if ! "$apksigner" verify "$apk" >/dev/null; then
    echo "Release APK signature verification failed: $apk" >&2
    exit 1
fi

expected_digest="$($apksigner verify --print-certs "$apk" \
    | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
installed_path_output="$(adb -s "$device_serial" shell pm path "$package_name" 2>/dev/null || true)"
installed_path="$(tr -d '\r' <<<"$installed_path_output" \
    | sed -n 's/^package://p' \
    | head -n 1)"

if [[ -n "$installed_path" ]]; then
    install_tmp="$(mktemp -d)"
    trap 'rm -rf "$install_tmp"' EXIT
    adb -s "$device_serial" pull "$installed_path" "$install_tmp/installed.apk" >/dev/null
    installed_digest="$($apksigner verify --print-certs "$install_tmp/installed.apk" \
        | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
    if [[ "$installed_digest" != "$expected_digest" ]]; then
        echo "Refusing update: installed No bullshit launcher uses a different signing key." >&2
        exit 1
    fi
fi

adb -s "$device_serial" install -r "$apk"

verified_path="$(adb -s "$device_serial" shell pm path "$package_name" | tr -d '\r')"
if [[ -z "$verified_path" ]]; then
    echo "Installation command returned, but the package is not installed." >&2
    exit 1
fi

version_name="$(adb -s "$device_serial" shell dumpsys package "$package_name" \
    | sed -n 's/^[[:space:]]*versionName=//p' \
    | head -n 1 \
    | tr -d '\r')"
echo "Installed $package_name $version_name on $device_serial"
echo "Certificate SHA-256: $expected_digest"
