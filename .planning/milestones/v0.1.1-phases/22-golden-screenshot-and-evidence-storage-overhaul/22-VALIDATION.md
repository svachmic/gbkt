---
phase: 22
slug: golden-screenshot-and-evidence-storage-overhaul
status: planned
nyquist_compliant: true
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
| 22-01-T1 | 22-01 | 1 | FIX-07 (R2/R4/R5-guard/D-03/05/06) | T-22-01 | golden bless gated on system prop | unit | `./gradlew :gbkt-emulator:test --tests "*GoldenAssertionsTest"` | ❌ W0 (this task creates it) | ⬜ pending |
| 22-01-T2 | 22-01 | 1 | FIX-07 (D-08) | — | deterministic sidecar | unit | `./gradlew :gbkt-emulator:test --tests "*ScreenshotCaptureTest"` | ✅ exists | ⬜ pending |
| 22-02-T1 | 22-02 | 1 | FIX-07 (R5/D-07) | T-22-02 | mode from ROM header, not config | unit | `./gradlew :gbkt-emulator:test --tests "*AgentSessionConfigTest"` | ✅ exists (add cases) | ⬜ pending |
| 22-03-T1 | 22-03 | 1 | FIX-07 (R6) | T-22-03 | opt-in re-bless flag | gitignore gate | `git check-ignore -q ".planning/phases/19-*/evidence/SEED-004/screenshot.png"` | ✅ acceptance | ⬜ pending |
| 22-03-T2 | 22-03 | 1 | FIX-07 (R4) | T-22-03 | flag propagation | config grep | `grep -l "gbkt.updateGoldens" <4 build.gradle.kts>` == 4 | ✅ acceptance | ⬜ pending |
| 22-04-T1 | 22-04 | 2 | FIX-07 (R6/D-02) | T-22-04 | byte-identity baseline | sha256 gate | per-file sha256 source==target (6 anchors) | ❌ W0 (creates goldens) | ⬜ pending |
| 22-05-T1 | 22-05 | 2 | FIX-07 (R6/D-02) | T-22-05 | byte-identity baseline | sha256 gate | per-file sha256 source==target (16 anchors) | ❌ W0 (creates goldens) | ⬜ pending |
| 22-06-T1 | 22-06 | 3 | FIX-07 (R1/R2/D-07) | T-22-06 | guarded bless | integration | `./gradlew :gbkt-examples:metasprites:test --tests "*Phase19VisualEvidenceTest" --tests "*MetaspritePhase20OracleTest"` | ✅ (swap) | ⬜ pending |
| 22-06-T2 | 22-06 | 3 | FIX-07 (R1) | T-22-06 | scratch smoke | integration | `./gradlew :gbkt-examples:metasprites:test` | ✅ (swap) | ⬜ pending |
| 22-07-T1 | 22-07 | 3 | FIX-07 (R1/R2/D-07) | T-22-07/07b | guarded bless + region gate kept | integration | `./gradlew :gbkt-examples:platformer-template:test --tests "*PlatformerTemplateUatTest"` | ✅ (swap) | ⬜ pending |
| 22-07-T2 | 22-07 | 3 | FIX-07 (R1/R2) | T-22-07 | guarded bless | integration | `./gradlew :gbkt-examples:platformer-template:test` | ✅ (swap) | ⬜ pending |
| 22-08-T1 | 22-08 | 3 | FIX-07 (R1/R6) | T-22-08 | scratch smoke (no un-blessed golden) | integration | `./gradlew :gbkt-examples:simple-physics:test` | ✅ (swap) | ⬜ pending |
| 22-08-T2 | 22-08 | 3 | FIX-07 (R1/R6) | T-22-08 | scratch smoke | integration | `./gradlew :gbkt-examples:banks:test` | ✅ (swap) | ⬜ pending |
| 22-09-T1 | 22-09 | 1 | FIX-07 (R1/R3) | T-22-09 | scratch redirect | grep gate | `grep -rl "planning/phases" <6 pipeline tests>` == 0 | ✅ (swap) | ⬜ pending |
| 22-09-T2 | 22-09 | 1 | FIX-07 (R1/R3) | T-22-09 | scratch redirect | integration | `./gradlew :gbkt-backend-gbdk:test` | ✅ (swap) | ⬜ pending |
| 22-10-T1 | 22-10 | 1 | FIX-07 (R1/R3) | T-22-10 | scratch redirect | grep gate | `grep -rl "planning/phases" <5 platformer tests>` == 0 | ✅ (swap) | ⬜ pending |
| 22-10-T2 | 22-10 | 1 | FIX-07 (R1/R3) | T-22-10 | scratch redirect | integration | `./gradlew :gbkt-genre-platformer:test` | ✅ (swap) | ⬜ pending |
| 22-11-T1 | 22-11 | 1 | FIX-07 (R1/R3) | T-22-11 | scratch redirect | grep gate | `grep -rl "planning/phases" <4 example emission tests>` == 0 | ✅ (swap) | ⬜ pending |
| 22-11-T2 | 22-11 | 1 | FIX-07 (R1/R3) | T-22-11 | scratch redirect | integration | `./gradlew :gbkt-examples:pong:test :gbkt-examples:breakout:test :gbkt-examples:banks:test --tests "*EmissionTest" --tests "*NoExitRegressionTest"` | ✅ (swap) | ⬜ pending |
| 22-12-T1 | 22-12 | 4 | FIX-07 (R6) | T-22-12 | guard before destructive rm | git gate | `git ls-files ".planning/phases/**/evidence/**"` == 0 AND goldens == 22 | ✅ acceptance | ⬜ pending |
| 22-14-T1 | 22-14 | 4 | FIX-07 (R7) | T-22-14 | doc the correct scheme | grep gate | `grep -q "goldens" context/TESTING.md && grep -q "gbkt.updateGoldens" context/TESTING.md` | ✅ acceptance | ⬜ pending |
| 22-13-T1 | 22-13 | 5 | FIX-07 (R1/R5/R6) | T-22-13 | regression-proof gates | acceptance test | `./gradlew :gbkt-test:test --tests "*CleanTreeEvidenceAcceptanceTest"` | ❌ W0 (creates it) | ⬜ pending |
| 22-13-T2 | 22-13 | 5 | FIX-07 (Success Criterion 3) | T-22-13 | clean tree | human-verify checkpoint | `./gradlew test` green + `git status --porcelain` empty + GBC buildRom | manual | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Sampling continuity check:** No run of 3 consecutive tasks lacks an automated `<verify>` — every code/migration task carries an automated command or is a Wave-0 file-creating task; the single human-verify checkpoint (22-13-T2) is preceded by the automated acceptance test (22-13-T1) and the full-suite gate.

---

## Wave 0 Requirements

Infrastructure that must exist before dependent tasks (created within Wave 1/2/5):

- [ ] `GoldenAssertions.kt` + `GoldenAssertionsTest.kt` (plan 22-01) — the assertGoldenMatch helper all Wave 3 swaps depend on.
- [ ] `discoverFiles` GBC auto-detect + new `AgentSessionConfigTest` cases (plan 22-02).
- [ ] `.gitignore` evidence rule + `-Pgbkt.updateGoldens` wiring + goldens dir skeletons (plan 22-03).
- [ ] 22 migrated byte-identical goldens (plans 22-04 + 22-05) — must exist before Wave 3 assertGoldenMatch calls (ordering hazard).
- [ ] `ScreenshotCaptureTest` updated for capturedAt removal (plan 22-01).
- [ ] `CleanTreeEvidenceAcceptanceTest` (plan 22-13) — locks R1/R5/R6 grep gates.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Migrated blessed anchors remain the USER-approved baselines | FIX-07 (R6) | Byte-identity migration is automated (sha256), but final visual sign-off of the cyan-elephant / banks-tRNS / platformer-GBC anchors is a human truth (Visual Evidence Rule) | Plan 22-13 checkpoint: spot-open `goldens/metasprites/elephant-cyan-subpalette.png` and a `goldens/platformer-template/anchor1-*.png`; confirm they match the Phase 19/21 approved look. sha256 (plans 22-04/05 SUMMARYs) proves byte-identity. |

*All other phase behaviors have automated verification (grep gates, sha256 gates, integration tests, clean-tree assertion).*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency acceptable
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** planned (2026-06-14)
