# Coding Agent Development Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add concise repository instructions for coding agents, a detailed human workflow for agent-assisted feature development, and README links to both.

**Architecture:** Root `AGENTS.md` is the automatically discoverable policy layer. `docs/DEVELOPING_WITH_AGENTS.md` is the explanatory human layer, and `README.md` provides discovery without duplicating either guide.

**Tech Stack:** Markdown, Android/Kotlin project conventions, Gradle, ADB, Git.

---

### Task 1: Add repository-scoped coding-agent rules

**Files:**
- Create: `AGENTS.md`

- [x] **Step 1: Document project invariants and architecture routing**

Create `AGENTS.md` with the package identity, Android API floor, remote-first
landscape UX, vendor-neutral design, configuration-preservation requirement,
and package directory map for `ui`, `model`, `data`, `stats`, `status`, and
`time`.

- [x] **Step 2: Document implementation and safety constraints**

Require asynchronous bounded work, lifecycle-safe state, existing network
policy reuse, no analytics or background refresh, in-place installation, safe
test-owned device fixtures, and explicit permission for destructive device,
audio, power, and source-control operations.

- [x] **Step 3: Document the verification gate**

Include these exact commands:

```bash
git diff --check
./gradlew lintDebug lintRelease test assembleDebug assembleRelease
./gradlew connectedCheck
```

Explain that `connectedCheck` and real TV smoke tests are required only when
Android/UI/device behavior changes, and that observed results must be separated
from expectations.

### Task 2: Add the human agent-assisted workflow

**Files:**
- Create: `docs/DEVELOPING_WITH_AGENTS.md`

- [x] **Step 1: Add preparation and prompt-template sections**

Include a preparation checklist and this reusable template:

```markdown
Goal:
User-visible behavior:
Must preserve:
Device actions allowed:
Acceptance criteria:
Verification required:
Source-control authority:
```

State that omitted destructive, publishing, and credential authority is not
implicit, and secrets must never be pasted into prompts or logs.

- [x] **Step 2: Add the staged development workflow**

Cover context inspection, design/plan approval, focused regression coverage,
implementation, local verification, relevant device verification, final diff
review, and explicit source-control handoff.

- [x] **Step 3: Add review and definition-of-done guidance**

Require evidence for lint, both unit variants, builds, and connected tests when
relevant. Require data-preservation evidence when an install or persisted state
is affected, plus privacy/performance review, documentation, and clean
source-control state. Include a realistic non-destructive feature prompt.

### Task 3: Link the guides from the README

**Files:**
- Modify: `README.md`

- [x] **Step 1: Add the discovery section**

Insert after Test and before Architecture:

```markdown
## Developing with coding agents

Coding agents should read [AGENTS.md](AGENTS.md) before modifying this
repository. Human contributors can use
[Developing with coding agents](docs/DEVELOPING_WITH_AGENTS.md) for the prompt
template, safe device workflow, review checklist, and definition of done.
```

### Task 4: Verify and deliver the documentation

**Files:**
- Verify: `AGENTS.md`
- Verify: `README.md`
- Verify: `LICENSE`
- Verify: `docs/DEVELOPING_WITH_AGENTS.md`
- Verify: `docs/INSTALL_AND_ROLLBACK.md`
- Verify: `docs/superpowers/specs/2026-09-03-readme-design.md`
- Verify: `docs/superpowers/specs/2026-09-03-agent-development-guide-design.md`
- Verify: `docs/superpowers/plans/2026-09-03-readme.md`
- Verify: `docs/superpowers/plans/2026-09-03-agent-development-guide.md`

- [x] **Step 1: Validate Markdown, links, privacy, and policy completeness**

Run:

```bash
doc_files=(
  AGENTS.md
  LICENSE
  README.md
  docs/DEVELOPING_WITH_AGENTS.md
  docs/INSTALL_AND_ROLLBACK.md
  docs/superpowers/specs/2026-09-03-readme-design.md
  docs/superpowers/specs/2026-09-03-agent-development-guide-design.md
  docs/superpowers/plans/2026-09-03-readme.md
  docs/superpowers/plans/2026-09-03-agent-development-guide.md
)
git diff --check
! rg -n '[[:blank:]]+$' "${doc_files[@]}"
for doc_file in "${doc_files[@]}"; do
  test -s "$doc_file"
  test "$(tail -c 1 "$doc_file" | od -An -t u1 | tr -d '[:space:]')" = 10
done
test -f AGENTS.md
test -f LICENSE
test -f docs/DEVELOPING_WITH_AGENTS.md
test -f docs/INSTALL_AND_ROLLBACK.md
rg -n 'AGENTS.md|DEVELOPING_WITH_AGENTS.md|MIT License' README.md
rg -n 'commit|push|remote|uninstall|clear data|audio|power' AGENTS.md
```

Scan public documentation for real private deployment addresses, Wi-Fi names,
local signing paths, tokens, passwords, and vendor package names. Clearly
synthetic policy fixtures are allowed; deployed values are not.

- [x] **Step 2: Run the documentation change verification gate**

Run:

```bash
./gradlew --no-daemon test
git diff --check
doc_files=(
  AGENTS.md
  LICENSE
  README.md
  docs/DEVELOPING_WITH_AGENTS.md
  docs/INSTALL_AND_ROLLBACK.md
  docs/superpowers/specs/2026-09-03-readme-design.md
  docs/superpowers/specs/2026-09-03-agent-development-guide-design.md
  docs/superpowers/plans/2026-09-03-readme.md
  docs/superpowers/plans/2026-09-03-agent-development-guide.md
)
! rg -n '[[:blank:]]+$' "${doc_files[@]}"
for doc_file in "${doc_files[@]}"; do
  test -s "$doc_file"
  test "$(tail -c 1 "$doc_file" | od -An -t u1 | tr -d '[:space:]')" = 10
done
```

Expected: both debug and release unit-test variants pass, with no whitespace
or final-newline errors in tracked or untracked documentation.

- [x] **Step 3: Create reviewable documentation commits**

**Authorization prerequisite:** Do not run Steps 3 or 4 until Basri separately
and explicitly authorizes the relevant commit or remote work. This plan and
completion of earlier steps grant no source-control authority. Commit authority
does not imply remote authority, and neither permits a history rewrite. The
only rewrite allowed below is an explicitly authorized rebase of the unshared
documentation stack.

After commit authorization, commit the public README and MIT license, agent
rules, human workflow/README link, and design/plan records in coherent commits.
Include the verification commands in each commit body. Do not amend existing
commits or rewrite any commit in this step.

- [ ] **Step 4: Integrate and push**

After explicit remote and integration authorization, fetch `origin/main`. If
the remote advanced, rebase only the unshared documentation stack and only when
Basri has explicitly authorized that rebase; never rewrite shared or published
commits. Fast-forward local `main`, verify it matches the feature branch, and
push `main` normally. Never force-push.
