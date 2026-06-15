---
phase: 21
slug: codegen-fixes-platformer-and-remaining-seeds
status: ready
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-14
---

# Phase 21 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Mixed fix + confirmation phase (FIX-05 / FIX-06). Two evidence tiers per D-13:
> (1) byte-identity guard on the **untouched** example set; (2) targeted proof
> (JVM emission tests + UAT GBC visual re-shoots) for the **changed** set
> (platformer-template). pong stays PASS\* (toolchain non-determinism).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Kotlin), Gradle 9.5.1, JVM 21 |
| **Config file** | per-module `build.gradle.kts` (Gradle test tasks) |
| **Quick run command** | `./gradlew :<module>:test` (module under change, e.g. `:gbkt-ir:test`, `:gbkt-genre-platformer:test`, `:gbkt-backend-api:test`) |
| **Full suite command** | `./gradlew test pluginTest` (pluginTest for any plugin-fixture touch — known publish/test ordering race, verify via two invocations) |
| **Estimated runtime** | ~3–8 min module test; ~15–25 min full suite + pluginTest |

**Codegen-phase smoke (MANDATORY before declaring complete):** any plan that
touches `PlatformerVisitor`/`GBDKPipeline`/serializer/codegen MUST run a clean
`:gbkt-examples:platformer-template:buildRom` (+ affected examples) — JVM tests
cannot see staleness in `build/gbkt/generated/`
([[feedback_rom_build_smoke_test_for_codegen_phases]]).

**UAT capture:** JVM `PlatformerTemplateUatTest` StepAgent `captureAndRename()`
harness → PNGs to phase `evidence/`. Platformer MUST run `gbcMode=true` with the
`.noi` symFile ([[learning_platformer_mcp_needs_gbc_mode]]); ROM rebuilt clean
immediately before capture.

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :<module>:test :<module>:spotlessApply :<module>:detekt` (executor gate misses spotless/detekt — must run per-commit; [[project_executor_gate_misses_spotless_detekt]])
- **After every plan wave:** Run the affected-module test set; for codegen waves, clean `:buildRom` smoke
- **Before `/gsd-verify-work`:** Full suite + pluginTest green; all 3 GBC anchors re-shot post-fix and visually signed off
- **Max feedback latency:** ~480 seconds (module test)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 21-01-T1 | 01 | 1 | FIX-05 (D-05) | T-21-01-01 | N/A (offline codegen) | unit/DSL | `./gradlew :gbkt-genre-platformer:test` | 🆕 new test (created same plan) | ⬜ pending |
| 21-01-T2 | 01 | 1 | FIX-05 (D-05/D-07) | T-21-01-01 | N/A | emission | `./gradlew :gbkt-genre-platformer:test --tests '*PlatformerSnapArithmeticEmissionTest*'` | 🆕 new | ⬜ pending |
| 21-02-T1 | 02 | 2 | FIX-06 (D-09) | T-21-02-01 | N/A | unit/refactor | `./gradlew :gbkt-backend-gbdk:test :gbkt-genre-platformer:test` | ✅ existing + 🆕 util | ⬜ pending |
| 21-02-T2 | 02 | 2 | FIX-06 (D-09) | T-21-02-01 | N/A | contract/lockstep | `./gradlew :gbkt-genre-platformer:test --tests '*TilemapCollisionPredicateLockstepTest*'` | 🆕 new | ⬜ pending |
| 21-03-T1 | 03 | 1 | FIX-06 (D-08) | T-21-03-01 | deserialize tolerates malformed/absent JSON keys (no crash) | unit/serializer | `./gradlew :gbkt-ir:test` | ✅ existing | ⬜ pending |
| 21-03-T2 | 03 | 1 | FIX-06 (D-08) | T-21-03-01 | round-trip non-empty | round-trip | `./gradlew :gbkt-ir:test --tests '*GameIRSerializerRoundTripTest*'` | 🆕 new | ⬜ pending |
| 21-04-T1 | 04 | 1 | FIX-06 (D-10/D-11) | T-21-04-01 | N/A | structural grep + file-state | `test -f .../archive/SEED-027... && grep -q "bitsPerPixel = 2" TargetProfiles.kt` (manual structural assertion) | ✅ existing source | ⬜ pending |
| 21-05-T1 | 05 | 1 | FIX-06 (D-12) | T-21-05-01 | N/A | grep inventory | `grep -rn "whenever(" --include=*.kt --include=*.md . \| grep -v build \| wc -l` (manual structural enumeration) | ✅ existing | ⬜ pending |
| 21-05-T2 | 05 | 1 | FIX-06 (D-12) | T-21-05-01 | N/A | file-state + spotless/detekt | `test -f .../archive/SEED-029... && ./gradlew :gbkt-lang:detekt :gbkt-backend-gbdk:detekt` | ✅ existing | ⬜ pending |
| 21-06-T1 | 06 | 1 | FIX-06 (D-03/D-04) | T-21-06-01 | N/A | file-state | `for s in ...; do test -f backlog/v0.2.0/$s.md; done` (manual file-state assertion) | n/a | ⬜ pending |
| 21-06-T2 | 06 | 1 | FIX-06 (D-04) | T-21-06-01 | N/A | grep doc-state | `grep -q "RE-DEFERRED v0.2.0" REQUIREMENTS.md` (manual structural assertion) | n/a | ⬜ pending |
| 21-07-T1 | 07 | 3 | FIX-05 (D-14/D-15) | T-21-07-01 | N/A | UAT capture + grep | `grep -q "21-codegen.../evidence" PlatformerTemplateUatTest.kt` + anchor run | ✅ existing harness | ⬜ pending |
| 21-07-T2 | 07 | 3 | FIX-05 (D-05/D-06/D-07) | T-21-07-01 | N/A | **LOCKED-visual — binding user sign-off** (manual, see below) | manual GBC screenshot review | n/a | ⬜ pending |
| 21-07-T3 | 07 | 3 | FIX-05 | T-21-07-01 | N/A | file-state | `for s in ...; do test -f archive/$s.md; done` (manual file-state assertion) | n/a | ⬜ pending |
| 21-08-T1 | 08 | 4 | FIX-05/FIX-06 (D-13) | T-21-08-01 | N/A | byte-identity generated-C diff (manual structural) | `./gradlew :gbkt-examples:*:generateC` + before/after diff | ✅ existing | ⬜ pending |
| 21-08-T2 | 08 | 4 | FIX-05/FIX-06 (D-01) | T-21-08-01 | N/A | file-state (seeds/ empty) | `test -z "$(ls .planning/seeds/*.md 2>/dev/null)"` (manual structural assertion) | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Note on manual/structural verifies:** 21-04, 21-05, 21-06, 21-07-T3, 21-08-T2 are
disposition/structural tasks (seed moves, doc edits, requirement reconciliation) whose
automated verify is a deterministic `test`/`grep` file-state or structural assertion —
these are explicitly-listed structural assertions, not Nyquist code-coverage gaps.
21-07-T2 is the single LOCKED-visual manual sign-off (Visual Evidence Rule). It is
paired with the JVM emission test in 21-01 (snap arithmetic) and the 21-07-T1 anchor
capture, so no 3 consecutive tasks lack an automated verify.

---

## Wave 0 Requirements

Existing JUnit 5 infrastructure covers all phase requirements — no new framework install.
New test files are created in their OWNING plan (not Wave 0 blockers), each with its
production change:

- `gbkt-genre-platformer/.../PlatformerSnapArithmeticEmissionTest.kt` — created in Plan 21-01 (D-05 config-driven pivotAdjust + D-07 foot-snap arithmetic)
- `gbkt-genre-platformer/.../TilemapCollisionPredicateLockstepTest.kt` — created in Plan 21-02 (D-09 lockstep)
- `gbkt-ir/.../GameIRSerializerRoundTripTest.kt` — created in Plan 21-03 (D-08 round-trip)
- Phase 21 `evidence/uat-screenshots/` dir + `EVIDENCE_DIR` repoint in PlatformerTemplateUatTest.kt — Plan 21-07 (D-14/D-15)

The evidence directory `.planning/phases/21-codegen-fixes-platformer-and-remaining-seeds/evidence/uat-screenshots/` is pre-created by the planner.

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Player visibly grounded on platform (anchor-2) | FIX-05 (D-05/D-06) | LOCKED-visual — Visual Evidence Rule requires runtime GBC screenshot + binding user sign-off, not variable assertions | Clean `:buildRom`; run `PlatformerTemplateUatTest` anchor-2 (gbcMode=true, .noi symFile); capture to Phase 21 evidence/; present to user for sign-off |
| Player spawn position correct per zone (anchor-2) | FIX-05 (D-06) | LOCKED-visual | Re-shoot anchor-2 post-fix; confirm player on platform; user sign-off (no code change — spawn() already wired) |
| Vertical foot alignment acceptable (anchor-2) | FIX-05 (D-07) | LOCKED-visual — close-as-accepted needs binding sign-off | Re-shoot GBC anchor; user confirms foot alignment intended/imperceptible (paired with PlatformerSnapArithmeticEmissionTest) |
| 3 GBC anchors confirm cEmit-already-fixed (Criterion 1) | FIX-05 | Visual confirmation of pre-satisfied criterion | One post-fix capture pass against final ROM (D-14) serves double duty as fix evidence + Criterion-1 confirmation |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or are LOCKED-visual manual (21-07-T2, table above) or explicitly-listed structural file-state assertions
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (visual seed paired with JVM emission tests in 21-01 + anchor capture in 21-07-T1)
- [x] Wave 0 covers all MISSING references (none — existing infra; new test files created in owning plans)
- [x] No watch-mode flags
- [x] Feedback latency < 480s
- [x] `nyquist_compliant: true` set in frontmatter (set by planner after per-task map filled)

**Approval:** ready
