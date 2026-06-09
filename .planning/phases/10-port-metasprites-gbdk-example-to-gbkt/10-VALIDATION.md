---
phase: 10
slug: port-metasprites-gbdk-example-to-gbkt
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-18
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit5 (Kotlin Test) — same as all gbkt examples |
| **Config file** | none — inherited via BOM; no per-example junit config |
| **Quick run command** | `./gradlew :gbkt-examples:metasprites:test` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds (per-example); ~3–5 min (full suite) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :gbkt-ir:test :gbkt-lang:test :gbkt-backend-gbdk:test` (whichever module was touched)
- **After every plan wave:** Run `./gradlew :gbkt-examples:metasprites:test`
- **Before `/gsd:verify-work`:** Full suite must be green AND `:gbkt-examples:metasprites:buildRom` must compile clean (no warnings)
- **Max feedback latency:** 30 seconds (per-module test); 5 minutes (full suite)

---

## Per-Task Verification Map

> Populated by the planner per plan. Initial seeded rows below — planner will expand per-plan during planning.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 10-XX-01 | UAT lock | 1 | D-01, D-03 | — | N/A — DSL/codegen | doc + playbook | `test -f .planning/phases/10-*/10-UAT.md` | ❌ W0 | ⬜ pending |
| 10-XX-01 | MetaspriteIR | 1 | D-04 | — | N/A | unit (IR) | `./gradlew :gbkt-ir:test --tests "*MetaspriteIRTest*"` | ❌ W0 | ⬜ pending |
| 10-XX-01 | Metasprite DSL builder | 2 | D-04 | — | N/A | unit (lang) | `./gradlew :gbkt-lang:test --tests "*MetaspriteBuilderTest*"` | ❌ W0 | ⬜ pending |
| 10-XX-01 | Metasprite visitor — tile data | 2 | D-04 | — | N/A | JVM emission | `./gradlew :gbkt-backend-gbdk:test --tests "*MetaspriteVisitorTileDataTest*"` | ❌ W0 | ⬜ pending |
| 10-XX-01 | Metasprite visitor — descriptor | 2 | D-04 | — | N/A | JVM emission | `./gradlew :gbkt-backend-gbdk:test --tests "*MetaspriteVisitorDescriptorTest*"` | ❌ W0 | ⬜ pending |
| 10-XX-01 | Metasprite visitor — frame switch + hiwater | 2 | D-04 | — | N/A | JVM emission | `./gradlew :gbkt-backend-gbdk:test --tests "*MetaspriteVisitorFrameSwitchTest*"` | ❌ W0 | ⬜ pending |
| 10-XX-01 | actor.flipX/flipY accessors | 3 | D-07 | — | N/A | JVM emission | `./gradlew :gbkt-backend-gbdk:test --tests "*FlipAccessorEmissionTest*"` | ❌ W0 | ⬜ pending |
| 10-XX-01 | actor.subPalette accessor | 3 | D-07, D-08 | — | N/A | JVM emission | `./gradlew :gbkt-backend-gbdk:test --tests "*SubPaletteAccessorEmissionTest*"` | ❌ W0 | ⬜ pending |
| 10-XX-01 | GBC compat surface (cgb_compatibility + spritePalette) | 3 | D-09 | — | N/A | JVM emission | `./gradlew :gbkt-backend-gbdk:test --tests "*GbcCompatEmissionTest*"` | ❌ W0 | ⬜ pending |
| 10-XX-01 | Port assembly | 4 | D-01 (full) | — | N/A | build | `./gradlew :gbkt-examples:metasprites:buildRom` | ❌ W0 | ⬜ pending |
| 10-XX-01 | UAT behavior 1 — animation index advance | 5 | D-01.1, D-02 | — | N/A | UAT (DMG) | `./gradlew :gbkt-examples:metasprites:test --tests "*MetaspriteUatTest*behavior1*"` | ❌ W0 | ⬜ pending |
| 10-XX-01 | UAT behavior 2 — flip cycle | 5 | D-01.2, D-02 | — | N/A | UAT (DMG) | `./gradlew :gbkt-examples:metasprites:test --tests "*MetaspriteUatTest*behavior2*"` | ❌ W0 | ⬜ pending |
| 10-XX-01 | UAT behavior 3 — sub-palette cycle (GBC) | 5 | D-01.3, D-02, D-08 | — | N/A | UAT (GBC mode) | `./gradlew :gbkt-examples:metasprites:test --tests "*MetaspriteUatTest*behavior3*"` | ❌ W0 | ⬜ pending |
| 10-XX-01 | Tier-1 emission invariant — anim index (D-12.1) | 6 | D-12 | — | N/A | JVM emission (awk brace-walk) | `./gradlew :gbkt-examples:metasprites:test --tests "*MetaspriteEmissionTest*D_12_1*"` | ❌ W0 | ⬜ pending |
| 10-XX-01 | Tier-1 emission invariant — flip OAM (D-12.2) | 6 | D-12 | — | N/A | JVM emission (awk brace-walk) | `./gradlew :gbkt-examples:metasprites:test --tests "*MetaspriteEmissionTest*D_12_2*"` | ❌ W0 | ⬜ pending |
| 10-XX-01 | Tier-1 emission invariant — sub-palette OAM (D-12.3) | 6 | D-12 | — | N/A | JVM emission (awk brace-walk) | `./gradlew :gbkt-examples:metasprites:test --tests "*MetaspriteEmissionTest*D_12_3*"` | ❌ W0 | ⬜ pending |
| 10-XX-01 | Three-signal artifact | 6 | D-11 | — | N/A | doc + manual measure | `test -f .planning/phases/10-*/evidence/oracle-comparison.md` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `gbkt-examples/metasprites/build.gradle.kts` — example module config (mirrors `simple-physics`)
- [ ] `gbkt-examples/settings.gradle.kts` — add `:gbkt-examples:metasprites` include
- [ ] `gbkt-examples/metasprites/src/test/kotlin/.../MetaspriteIRTest.kt` — IR structure invariants
- [ ] `gbkt-examples/metasprites/src/test/kotlin/.../MetaspriteEmissionTest.kt` — D-12 emission invariants (RED → GREEN pattern matching Phase 9)
- [ ] `gbkt-examples/metasprites/src/test/kotlin/.../MetaspriteUatTest.kt` — D-02 UAT with StepAgent + screenshots (GBC mode for behavior 3)
- [ ] `gbkt-ir/src/test/kotlin/.../MetaspriteIRTest.kt` — IR-level shape tests
- [ ] `gbkt-lang/src/test/kotlin/.../MetaspriteBuilderTest.kt` — DSL builder registration tests
- [ ] `gbkt-backend-gbdk/src/test/kotlin/.../MetaspriteVisitor*Test.kt` — visitor emission tests (split per sub-area: tile-data, descriptor, frame-switch)
- [ ] `gbkt-backend-gbdk/src/test/kotlin/.../FlipAccessorEmissionTest.kt` — actor.flipX/flipY emission
- [ ] `gbkt-backend-gbdk/src/test/kotlin/.../SubPaletteAccessorEmissionTest.kt` — actor.subPalette emission
- [ ] `gbkt-backend-gbdk/src/test/kotlin/.../GbcCompatEmissionTest.kt` — cgb_compatibility() + spritePalette emission

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| ROM-size byte measurement vs reference | D-11.1 | Comparison artifact; one-shot byte count | `wc -c gbkt-examples/metasprites/build/gbkt/output/metasprites.gb` and `wc -c .planning/phases/10-*/evidence/reference/metasprites.gb`; record both in `evidence/oracle-comparison.md` (target: gbkt size ≤ 2× reference). |
| Generated-C diff vs reference | D-11.2 | Qualitative judgment ("shorter/clearer wins") | `diff -u .planning/phases/10-*/evidence/reference/metasprites.c gbkt-examples/metasprites/build/gbkt/generated/main.c` summarised in `evidence/oracle-comparison.md`. Surplus C defects (worse than reference) → seeds via `/gsd-capture --seed`. |
| GBC behavior-3 screenshot | D-01.3, D-02, D-08 | DMG screenshot CANNOT prove sub-palette change (CLAUDE.md Visual Evidence Rule) | UAT MCP probe MUST use `AgentSessionConfig(gbcMode = true)`; screenshot at climax of sub-palette index 0/1/2/3 cycles; commit PNGs under `evidence/uat/behavior3-subpal-*.png`. |
| Reference ROM build reproducibility | D-11 | One-time local build; binaries gitignored | Follow `evidence/reference/BUILD.md` (mirrors Phase 9's pattern); produce `metasprites.gb`/`metasprites.map`/`metasprites.noi`; do NOT commit binaries. |
| Phase-close hygiene — surplus seeds + conditional Phase 10.1 | D-06 | Judgment call on whether each surplus defect deserves its own follow-up | Run `/gsd-capture --seed` per surplus defect; if ≥1 surplus seed exists at phase close, run `gsd-phase --insert 10` for the Phase 10.1 placeholder in the SAME commit that closes Phase 10. |
| Phase 13 routing | D-13 | Editorial judgment on which surfaced DSL gaps belong in the cross-port collector | Run `gsd-phase --edit 13` for any framework-shaping DSL gaps surfaced during the port (e.g., missing `if`/`unless`, typed `Cartridge` enum, fixed-point primitive). |

---

## Validation Sign-Off

- [ ] All tasks have automated test command OR Wave 0 dependency
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (build.gradle.kts, settings.gradle.kts include, test scaffolding)
- [ ] No watch-mode flags in any test command
- [ ] Feedback latency < 30s (per-module); < 5min (full suite)
- [ ] `nyquist_compliant: true` set in frontmatter (after planner populates per-task verify map)

**Approval:** pending
