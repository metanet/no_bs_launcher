# Settings quick actions and build information implementation plan

**Goal:** Align CPU utilization with the other percentages, expose Settings
actions in the header, and display build identity.

**Architecture:** Reorder the pure CPU display policy, restructure only the
Settings XML header, and inject Git/timestamp values through generated
`BuildConfig` constants. `SettingsActivity` binds the constants to read-only
views.

- [x] Update pure CPU display expectations so utilization is always the final
      segment, and confirm the existing implementation fails them.
- [x] Add connected assertions for top-row geometry, focus links, and build
      information, and confirm the UI contract is initially unmet.
- [x] Reorder CPU display segments without changing collection or fallback
      behavior.
- [x] Move Web shortcuts, Android settings, and Save into the Settings header
      and add explicit horizontal focus navigation.
- [x] Generate the short Git hash, dirty marker, and UTC build date; render
      them in a compact Build information section.
- [x] Run lint, debug and release unit suites, signed release build, and the
      complete connected suite.
- [x] Install in place on the current TV box and smoke-test Home and Settings
      without changing audio or power state.
