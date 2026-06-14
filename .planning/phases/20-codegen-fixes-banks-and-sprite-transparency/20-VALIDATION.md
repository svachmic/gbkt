---
phase: 20
slug: codegen-fixes-banks-and-sprite-transparency
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-14
---

# Phase 20 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Confirmation / regression-guard phase — evidence-driven, no production codegen change expected.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Kotlin) via Gradle 9.5.1; GBDK lcc for ROM builds; StepAgent UAT harness for runtime screenshots |
| **Config file** | Per-example `build.gradle.kts` (gbkt Gradle plugin); root `settings.gradle.kts` |
| **Quick run command** | `./gradlew :gbkt-examples:banks:test` |
| **Full suite command** | `./gradlew :gbkt-examples:banks:test :gbkt-examples:metasprites:test :gbkt-examples:platformer-template:test` |
| **Estimated runtime** | ~60–180 s (emission tests fast; UAT capture + clean buildRom dominate) |

---

## Sampling Rate

- **After every task commit:** Run the relevant module's `:test` (emission guards) plus `:module:spotlessApply :module:detekt` (the test task + pre-commit hook do NOT run these — see [[project_executor_gate_misses_spotless_detekt]])
- **After every plan wave:** Run the full suite command above
- **Before `/gsd-verify-work`:** Full emission suite green + both runtime screenshots captured + byte-identity sweep clean
- **Max feedback latency:** ~180 seconds (clean buildRom + capture)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 20-01-01 | 01 | 1 | FIX-03 | — | SEED-014 guard sufficiency re-verified GREEN first (D-02) | unit/emission | `./gradlew :gbkt-examples:banks:test --tests '*BanksEmissionTest*'` | ✅ existing | ⬜ pending |
| 20-02-01 | 02 | 2 | FIX-03 | — | banks trio mapped to existing sentinels in 20-AUDIT-FIX-03.md | doc/audit | `test -f .planning/phases/20-codegen-fixes-banks-and-sprite-transparency/20-AUDIT-FIX-03.md` | ❌ W-new | ⬜ pending |
| 20-03-01 | 03 | 2 | FIX-04 | — | sprite-outline renders clean at HEAD (D-08 visual oracle) | UAT screenshot | `./gradlew :gbkt-examples:metasprites:test --tests '*Phase20*'` | ❌ W-new | ⬜ pending |
| 20-03-02 | 03 | 2 | FIX-04 | — | platformer player transparency unchanged (twin shot, GBC mode) | UAT screenshot | `./gradlew :gbkt-examples:platformer-template:test --tests '*Phase20*'` | ❌ W-new | ⬜ pending |
| 20-04-01 | 04 | 3 | FIX-03, FIX-04 | — | full 7-example byte-identity sweep clean (pong PASS*) | byte-identity | `sha256sum build/gbkt/generated/*.c` per example after clean generateC | ❌ W-new | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing infrastructure covers FIX-03 emission guards: `BanksEmissionTest.kt` (INV-2/INV-5/INV-6) and `BanksUatTest.kt` (Anchor 4) are already GREEN per Phase 16 triage.
- New evidence artifacts to author (not framework installs): Phase 20 UAT `@Test` methods reusing `captureAndRename()`, the `20-AUDIT-FIX-03.md` audit doc, and the byte-identity diff procedure.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Sprite-outline renders without corruption | FIX-04 | Visual truth — per Visual Evidence Rule, requires a runtime screenshot, not a variable assertion | Build metasprites ROM clean, run Phase 20 UAT capture, inspect PNG in `evidence/` |
| Platformer player transparency no-regression | FIX-04 | Visual truth — comparison against approved baseline | Build platformer-template ROM clean (GBC mode + .noi), run Phase 20 UAT twin shot, compare PNG |

*Automated test methods drive the captures; the final clean/no-corruption judgment is a human/visual sign-off (D-08 visual oracle).*

---

## Validation Sign-Off

- [ ] All tasks have automated verify or evidence-artifact dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (none — existing emission infra is GREEN)
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
