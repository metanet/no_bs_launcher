# Public and Developer README Design

## Goal

Add a concise repository landing page that explains No bullshit launcher to
prospective users and gives Android developers enough information to build,
test, and understand the project.

## Audience and tone

The README serves two audiences in this order:

1. Android TV users evaluating the launcher.
2. Android developers evaluating or contributing to the codebase.

The writing is direct and factual. It does not use unsupported badges or
marketing claims. The project is licensed under the canonical MIT License with
`Copyright (c) 2026 Basri Kahveci`.

## Content structure

The README will contain:

- The project name and a one-sentence description.
- A compact feature list covering configurable favorites, the alphabetical app
  catalog, remote-friendly reordering and app actions, web shortcuts, the
  optional information panel, provider-neutral VPN state, and system metrics.
- A privacy and security section describing local-only settings, the absence of
  analytics and background tracking, protected website/favicon access, and
  release/dependency verification.
- Requirements: Android TV 6.0/API 23 or newer, Android SDK 36 for builds, JDK
  17, and ADB for device installation/testing.
- Generic build and test commands that do not depend on Basri's local machine.
- A brief architecture map of the activity, model/store, catalog, network, and
  monitoring layers.
- Generic debug installation and Home-selection guidance, with a link to the
  existing detailed installation and rollback document for signed release and
  device-specific operations.
- Current limitations, including Android-controlled uninstall confirmation,
  firmware-dependent Home selection, best-effort SSID visibility, and browser
  dependency for web shortcuts.
- A License section linking to the root `LICENSE` file.

## License

Create a root `LICENSE` file using the canonical SPDX MIT text. The copyright
line is `Copyright (c) 2026 Basri Kahveci`. Do not add per-file SPDX headers as
part of this documentation change.

## Privacy boundary

The README will not include private device IP addresses, Wi-Fi names, local
keystore paths, keychain service names, credentials, or vendor-specific stock
launcher package names. Detailed operator-only information remains in
`docs/INSTALL_AND_ROLLBACK.md` and is linked rather than duplicated.

## Verification

- Check every documented command against the Gradle project and existing
  scripts.
- Check feature and privacy claims against the manifest and current source.
- Check the `LICENSE` body against the canonical SPDX MIT text and confirm the
  README links to it.
- Run `git diff --check` and a Markdown-focused content scan.
- Do not run Android build or device tests because this documentation-only
  change cannot affect compiled application behavior.
