# Full Audit Remediation Design

## Goal

Resolve every reliability, security, privacy, and performance issue found in the
September 3 audit without changing the launcher's deliberately simple product
model. The result must remain vendor-neutral, work on both Android TV boxes,
retain explicit HTTP shortcut support, preserve user configuration across
temporary package-manager failures, and never manipulate an unrelated installed
application during testing.

## Safety and reliability

- Replace the instrumentation test's arbitrary-package disable operation with an
  app-owned test fixture that is safe even if instrumentation is interrupted.
- Preserve unavailable app favorites in stored configuration. Filter them only
  when building the currently visible grid; explicit user removal and confirmed
  uninstall remain the only destructive paths.
- Build VPN callbacks with `NET_CAPABILITY_NOT_VPN` removed before adding the VPN
  transport.
- Keep shortcut/favicon work in application-scoped services, propagate
  cancellation to active HTTP calls, and restore unsaved settings/editor state
  after activity recreation.
- Use asynchronous SharedPreferences persistence so UI actions do not block the
  main thread on disk I/O.

## Network and privacy hardening

- Reject shortcut URLs containing user information.
- Probe pages with HEAD rather than a state-bearing GET. HTTP 401 and 403 remain
  acceptable reachability results; 405/501 are reported as unsupported rather
  than silently issuing a potentially mutating GET.
- Allow explicitly entered HTTP URLs, but reject HTTPS-to-HTTP redirects.
- Prevent a public shortcut from redirecting or resolving favicon traffic to
  loopback, link-local, or private networks. Restrict discovered icons to the
  page origin, while explicitly entered local shortcuts retain local access.
- Use a stable, version-free user agent and redact query/fragment details in the
  management list while preserving the exact URL for launch and editing.
- Pin the expected release signing certificate, pin the Gradle distribution
  checksum, and enable dependency verification.

## Performance

- Query packages and decode artwork off the main thread, deduplicate before
  artwork decoding, cache the catalog, and invalidate it on package changes.
- Decode favicon files asynchronously and retain them in a bounded memory cache.
- Cache static CPU metadata and read cpuidle only as a fallback when `/proc/stat`
  cannot produce utilization. Only publish changed statistics.
- Remove broad R8 keep rules and cover optimized release construction in the
  regular release build.

## Verification and delivery

Each issue is committed independently with its relevant tests. Every commit is
compiled and tested before the next one is created. The completed stack receives
full debug/release lint, unit tests, signed release build, safe instrumentation,
installation over the existing TV app, and remote-control smoke tests. No audio
key events are sent. After verification, the branch is rebased and
fast-forwarded into local `main`, nothing is pushed, and the TV is powered off.
