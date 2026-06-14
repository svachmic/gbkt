---
phase: 22
slug: golden-screenshot-and-evidence-storage-overhaul
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-14
---

# Phase 22 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Kotlin) via Gradle |
| **Config file** | none — existing module `build.gradle.kts` test tasks |
| **Quick run command** | `./gradlew :gbkt-emulator:test` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~variable (full suite incl. example ROM UAT) |

---

## Sampling Rate

- **After every task commit:** Run the affected module's `:test`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite green AND clean-tree assertion (`git status --porcelain` empty after `./gradlew test`)
- **Max feedback latency:** module test runtime

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | TBD | TBD | FIX-07 | — | N/A | unit/integration | `{command}` | ❌ W0 | ⬜ pending |

*Planner fills this map per task. Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `assertGoldenMatch` helper + its unit test (new test infra for FIX-07)

*Planner refines based on chosen module placement.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Migrated blessed anchors remain the USER-approved baselines | FIX-07 (R5) | Byte-identity migration is automated, but final visual sign-off of the cyan-elephant / banks-tRNS / platformer-GBC anchors is a human truth | Confirm migrated PNG bytes are identical to Phase 19/20/21 approved baselines (sha256 compare) |

*All other phase behaviors have automated verification (grep gates + clean-tree assertion).*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency acceptable
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
