# Developing with coding agents

Coding agents can accelerate focused changes, but the contributor remains
responsible for scope, device safety, evidence, and review. Agents must read
[the repository instructions](../AGENTS.md) before making changes.

## Prepare the task

Before prompting an agent:

- Use a dedicated branch or worktree when practical. Check `git status`, note
  unrelated changes, and identify the files that must remain untouched.
- Read the relevant architecture and installation documentation. Write down
  the current user-visible behavior and stored data that the change must keep.
- Confirm JDK 17 and Android SDK 36 are available. Connect an emulator or TV
  only if device verification is relevant and explicitly authorized.
- Name the allowed ADB target with a placeholder such as `<device-serial>` and
  grant only the device actions the task needs. Prefer an emulator and
  test-owned fixtures.
- Define observable acceptance criteria and the exact verification commands.
- Remove secrets and private deployment details from prompts, logs, fixtures,
  screenshots, and copied command output.

## Prompt template

Copy this template and complete every field:

```markdown
Goal:
User-visible behavior:
Must preserve:
Device actions allowed:
Acceptance criteria:
Verification required:
Source-control authority:
```

Be literal about authority. Permission to edit does not imply permission to
clear data, uninstall packages, change Home, audio, or power settings, publish
artifacts, commit, push, contact remotes, or handle credentials. An omitted
action is not authorized.

## Staged workflow

1. **Establish the baseline.** Have the agent inspect the worktree, relevant
   implementation and tests, current behavior, and stored-data contract.
2. **Approve the design and plan.** For non-trivial work, agree on scope,
   architecture ownership, failure handling, device actions, and verification
   before implementation.
3. **Create focused regression coverage.** Make tests demonstrate the requested
   behavior and important preservation cases. Never accept a skipped test or a
   weakened assertion as a fix.
4. **Implement narrowly.** Keep blocking work off the main thread, give async
   work lifecycle and cancellation ownership, retain finite bounds, and reuse
   existing privacy and URL protections.
5. **Verify locally.** Review the diff, run both lint and unit-test variants,
   and build both debug and release variants. Fix the root cause of failures.
6. **Verify device behavior when relevant.** Run connected tests on the named
   authorized target. Disconnect other devices or set
   `ANDROID_SERIAL=<device-serial>` so Gradle cannot select an unauthorized
   target. If installation is needed over an existing copy, use an in-place
   update. When persistence, configuration, or migrations are affected,
   confirm the existing state remains. Package mutations must use test-owned
   fixtures. Reserve real-TV testing for behavior that an emulator cannot
   establish.
7. **Review and hand off.** Check every acceptance criterion, privacy and
   performance impact, documentation, `git status`, and the complete diff.
   Request separate authorization for any commit or remote operation.

## Definition of done

A change is ready for human review only when the handoff includes:

- A requirement-by-requirement summary with no unexplained scope changes.
- `git diff --check`, `lintDebug`, `lintRelease`, `testDebugUnitTest`,
  `testReleaseUnitTest`, `assembleDebug`, and `assembleRelease` results.
- `connectedCheck` and observed remote-flow results when Android/UI/device
  behavior changed, plus real-TV evidence for firmware-dependent behavior.
- When an APK was installed over an existing copy, evidence that the authorized
  verification used an in-place update.
- When persistence, configuration, migrations, or serialization were affected,
  evidence that favorites, shortcuts, settings, ordering, and other affected
  app-private state remain intact.
- A review of main-thread work, lifecycle/cancellation, resource bounds,
  network redirects, private-network handling, permissions, and data exposure.
- Updated tests and documentation, with no secrets or private deployment data.
- A clean, understood source-control state: the reviewed task diff is isolated
  from all preserved pre-existing changes; after authorized integration, no
  unintended task-related or staged changes remain.
- A final report identifying any unrun check, unavailable device, or
  pre-existing failure. Belief is not test evidence.
- No commit, push, remote access, history rewrite, or cleanup beyond the
  authority granted in the prompt.

## Secrets and privacy

Never paste real passwords, tokens, private keys, signing material, keystore
paths, private IP addresses, Wi-Fi names, device secrets, or vendor package
details into an agent prompt or log. Use placeholders and redact copied output.
Reserved synthetic values may be used in documentation. Private-address test
fixtures must use a fake transport incapable of real I/O, except for loopback
servers owned by the test; they must never identify or contact a real device.
Do not derive fixtures from deployed values or ask an agent to print credential
stores or environment variables. If a task truly needs a protected value, keep
it in the existing secret store and grant the smallest explicit operation that
can use it without displaying it.

New network behavior must remain user-triggered and bounded. It must reuse the
repository's URL, redirect, scheme, DNS-rebinding, and private-network policy;
never trade those controls for a simpler implementation.

## Safe example

```markdown
Goal:
Add an optional setting that shows seconds in the Home clock.

User-visible behavior:
Settings contains a remote-focusable "Show seconds" toggle. It is off by
default; when enabled, the Home clock updates once per second and includes
seconds. Back and recreation preserve an unsaved edit consistently with other
Settings fields.

Must preserve:
Android TV API 23+, landscape and D-pad behavior, the 20/80 Home layout, clock
behavior when the option is off, and all existing favorites, shortcuts,
ordering, visibility settings, and app-private data.

Device actions allowed:
Use the task-dedicated emulator at <device-serial>. An in-place debug install,
app launch, force-stop and relaunch of this debug app, and test input events are
allowed. Do not clear data, uninstall packages, change the Home app, disable
packages, or change audio, display, sleep, or power state. Do not use a real TV.

Acceptance criteria:
The toggle is reachable and operable by D-pad, persists only when Settings is
saved, survives process restart after saving, and does not create duplicate
timers across recreation. Existing clock formatting remains unchanged when
disabled.

Verification required:
Run git diff --check; both lint variants; debug and release unit tests; debug
and release assemblies; ANDROID_SERIAL=<device-serial> ./gradlew connectedCheck
after confirming all package mutations are test-owned; and the emulator
Settings-save, recreation, process-restart, and Home-clock flows. Report exact
results.

Source-control authority:
Edit and test only. Do not commit, push, fetch, change remotes, rewrite history,
or delete branches or worktrees.
```
