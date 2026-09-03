# Coding Agent Development Guide Design

## Goal

Make feature development with coding agents safer and more repeatable for No
bullshit launcher. Compatible agents receive concise repository rules
automatically, while human contributors get a detailed workflow and prompt
template.

## Documentation structure

Use three layers:

1. Root `AGENTS.md` contains mandatory, repository-scoped instructions for
   coding agents. It stays compact enough to load for every task.
2. `docs/DEVELOPING_WITH_AGENTS.md` explains the end-to-end workflow to humans
   using coding agents, including how to grant device and source-control
   authority safely.
3. `README.md` contains a short discovery section linking both documents.

The two guide files have different purposes and do not duplicate long prose.
`AGENTS.md` states enforceable constraints; the human guide explains why they
matter and how to prepare and review a task.

## Root agent instructions

`AGENTS.md` applies to the whole repository and covers:

- Product invariants: Android TV API 23+, landscape and remote-first UX,
  vendor-neutral implementation, package identity, configurable 20/80 Home
  layout, and preservation of existing launcher configuration.
- Architecture routing: UI, model, data, statistics, status, and time packages,
  with links to the detailed design and installation documentation.
- Implementation rules: keep blocking package, disk, bitmap, and network work
  off the main thread; give stateful asynchronous work clear ownership,
  cancellation, lifecycle handling, and finite resource bounds.
- Privacy and network rules: do not add analytics, tracking, accounts, WebView,
  background refresh, credential exposure, unsafe redirects, or bypasses around
  the existing URL/private-network policy.
- Device safety: use only test-owned mutation fixtures; preserve app data with
  in-place installs; do not clear data, uninstall the launcher, disable other
  packages, change audio, or change power state without explicit authority.
- Workflow: inspect only relevant files, plan non-trivial changes, add focused
  regression coverage, diagnose failures, and distinguish observed evidence
  from assumptions.
- Verification: run formatting/diff checks, both lint variants, both unit-test
  variants, relevant builds, and connected-device tests for UI or Android
  behavior. Exercise the actual TV flow when device behavior changes.
- Source-control safety: preserve unrelated work and require explicit human
  authorization before committing, pushing, modifying remotes, rewriting
  history, or deleting branches/worktrees.

Generic placeholders such as `<device-serial>` are used; private IP addresses,
Wi-Fi names, keystore locations, and vendor package names are excluded.

## Human contributor guide

`docs/DEVELOPING_WITH_AGENTS.md` contains:

- A preparation checklist for a clean branch/worktree, Android toolchain, and
  optional authorized TV connection.
- A prompt template with Goal, User-visible behavior, Constraints, Device
  actions allowed, Acceptance criteria, Verification, and Source-control
  authority fields.
- A staged workflow: understand, design/plan, add a regression, implement,
  verify locally, test on a device when relevant, and review the final diff.
- A definition of done requiring evidence for lint, unit tests, builds, device
  tests where relevant, state preservation, privacy/performance review, and
  source-control status.
- Safe review guidance and one example feature prompt that grants no implicit
  destructive or publishing authority.

The guide tells contributors never to paste passwords, tokens, private keys, or
other secrets into prompts or logs.

## README integration

Add a `Developing with coding agents` section after Test and before
Architecture. It tells agents to read `AGENTS.md` and sends human contributors
to `docs/DEVELOPING_WITH_AGENTS.md`.

## Verification

- Confirm all local Markdown links resolve.
- Scan all three public documents for private deployment details and
  placeholders that accidentally look like real credentials or addresses.
- Confirm documented Gradle tasks and directories exist.
- Confirm `AGENTS.md` does not authorize destructive device or source-control
  actions implicitly.
- Run `git diff --check` and both unit-test variants. No connected-device run is
  required because these files do not change the application binary.
