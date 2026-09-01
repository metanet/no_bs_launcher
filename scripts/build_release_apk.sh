#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
keychain_service="dev.basri.android.nobs_launcher.release"
keychain_account="basri"
apksigner="/opt/homebrew/share/android-commandlinetools/build-tools/36.0.0/apksigner"
apk="$project_dir/app/build/outputs/apk/release/app-release.apk"

if [[ ! -x "$apksigner" ]]; then
    echo "Missing apksigner: $apksigner" >&2
    exit 1
fi

signing_password="$(security find-generic-password \
    -a "$keychain_account" \
    -s "$keychain_service" \
    -w)"
if [[ -z "$signing_password" ]]; then
    echo "The No bullshit launcher signing password is missing from the login keychain." >&2
    exit 1
fi

export NOBS_LAUNCHER_KEYSTORE_PASSWORD="$signing_password"
export NOBS_LAUNCHER_KEY_PASSWORD="$signing_password"
trap 'unset signing_password NOBS_LAUNCHER_KEYSTORE_PASSWORD NOBS_LAUNCHER_KEY_PASSWORD' EXIT

cd "$project_dir"
./gradlew lintDebug lintRelease test connectedCheck assembleDebug assembleRelease

if [[ ! -f "$apk" ]]; then
    echo "Release APK was not produced: $apk" >&2
    exit 1
fi

verification="$($apksigner verify --verbose --print-certs "$apk")"
if ! grep -q '^Verifies$' <<<"$verification"; then
    echo "apksigner did not confirm the release archive." >&2
    exit 1
fi

certificate_digest="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"$verification")"
echo "Signed release APK: $apk"
echo "Certificate SHA-256: $certificate_digest"
