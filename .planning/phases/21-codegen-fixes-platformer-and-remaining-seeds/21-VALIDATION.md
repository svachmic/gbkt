---
phase: 21
slug: codegen-fixes-platformer-and-remaining-seeds
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| **Quick run command** | `./gradlew :<module>:test` (module under change, e.g. `:gbkt-ir:test`, `:gbkt-genre-platformer:test`) |
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

> Filled by the planner per task. Each real-fix task gets a JVM `<automated>`
> verify (emission test or round-trip test); each LOCKED-visual platformer seed
> gets a UAT anchor re-shoot + binding user sign-off (manual, see below). Each
> already-fixed / re-deferral task gets a structural/file-state assertion.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 21-XX-XX | XX | 1 | FIX-05 / FIX-06 | — | N/A (offline codegen, no runtime input surface) | unit / emission | `./gradlew :<module>:test` | ✅ existing | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing JUnit 5 infrastructure covers all phase requirements — no new framework install.
- New test files to add during execution (not Wave 0 blockers; created in their owning plan):
  - `gbkt-ir` GameIRSerializer round-trip tests (D-08 / SEED-020)
  - `gbkt-genre-platformer` emission test for `_player_y` initial value + foot-snap arithmetic (D-07 / SEED-PHASE-13 — required regardless of fix-vs-accept)
  - `gbkt-genre-platformer` emission test for `pivotAdjust` config-driven value (D-05 / SEED-021)

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Player visibly grounded on platform (anchor) | FIX-05 | LOCKED-visual — Visual Evidence Rule requires runtime GBC screenshot + binding user sign-off, not variable assertions | Clean `:buildRom`; run `PlatformerTemplateUatTest` GBC anchor (gbcMode=true, .noi symFile); capture to evidence/; present to user for sign-off |
| Player spawn position correct per zone (anchor-2) | FIX-05 | LOCKED-visual (D-06) | Re-shoot anchor-2 post-fix; confirm player on platform; user sign-off |
| Vertical foot alignment acceptable (anchor) | FIX-05 | LOCKED-visual (D-07) — close-as-accepted needs binding sign-off | Re-shoot GBC anchor; user confirms foot alignment intended/imperceptible |
| 3 GBC anchors confirm cEmit-already-fixed (Criterion 1) | FIX-05 | Visual confirmation of pre-satisfied criterion | One post-fix capture pass against final ROM (D-14) serves double duty as fix evidence + Criterion-1 confirmation |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or are LOCKED-visual manual (table above) or structural file-state assertions
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify (visual seeds paired with JVM emission tests)
- [ ] Wave 0 covers all MISSING references (none — existing infra)
- [ ] No watch-mode flags
- [ ] Feedback latency < 480s
- [ ] `nyquist_compliant: true` set in frontmatter (set by planner after per-task map filled)

**Approval:** pending
