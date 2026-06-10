---
phase: 11
slug: port-banks-gbdk-example-to-gbkt
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-19
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> See `11-RESEARCH.md § Validation Architecture` for the full tier-1/tier-2/tier-3/4th-signal contract; this file is the sampling-rate + per-task verification map populated during planning.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotlin Test (JUnit 5) via `testImplementation(kotlin("test"))` |
| **Config file** | `gbkt-examples/banks/build.gradle.kts` — `useJUnitPlatform()` (Wave 0 installs) |
| **Quick run command** | `./gradlew :gbkt-examples:banks:test` |
| **Full suite command** | `./gradlew :gbkt-examples:banks:test :gbkt-backend-gbdk:test :gbkt-analysis:test` |
| **Estimated runtime** | ~30–60 seconds (quick), ~2 min (full) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :gbkt-examples:banks:test`
- **After every plan wave:** Run `./gradlew :gbkt-examples:banks:test :gbkt-backend-gbdk:test :gbkt-analysis:test`
- **Before `/gsd-verify-work`:** Full suite green AND `./gradlew :gbkt-examples:banks:buildRom` exits 0 (ROM-build smoke test per memory `feedback_rom_build_smoke_test_for_codegen_phases.md`)
- **Max feedback latency:** ~60 seconds (quick suite)

---

## Per-Task Verification Map

> Planner populates this table during PLAN.md authoring. One row per task; Wave 0 tasks create the missing test files referenced by later tasks. Per memory `feedback_visual_evidence_for_visual_truths.md`, runtime-visual rows MUST resolve to a screenshot in `evidence/uat-screenshots/`, not a variable-state-only assertion.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `gbkt-examples/banks/build.gradle.kts` — example subproject + `useJUnitPlatform()` + `gbkt { ramBanks.set(2) }`
- [ ] `settings.gradle.kts` — `include("gbkt-examples:banks")` entry
- [ ] `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt` — IR structure stubs (scene count, save-system count, zone count)
- [ ] `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt` — 4 JVM-tier emission invariants (per-function brace-walk per CLAUDE.md scope-level grep gates corollary)
- [ ] `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt` — UAT anchors 1+2+4 stubs
- [ ] `gbkt-examples/banks/11-UAT.md` — 4-anchor contract doc (Plan 1 deliverable per CONTEXT D-09)
- [ ] `gbkt-examples/banks/PLAYBOOK.md` — MCP agent playbook (Plan 1 deliverable)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Anchor 1 — HOME→bank1 trampoline scene navigation visible on screen | UAT anchor 1 | Visual truth — variable-state-only insufficient per memory `feedback_visual_evidence_for_visual_truths.md` | Run `/gbkt-play-game banks` → press Start at title → `emulator_screenshot("evidence/uat-screenshots/anchor1-play-scene.png")` → asserter checks scene == "play" |
| Anchor 2 — Banked zone tilemap visible on play scene | UAT anchor 2 | Visual truth — must see tiles, not just `_current_tileset_id` | Continue from anchor 1 → `emulator_screenshot("evidence/uat-screenshots/anchor2-tilemap.png")` after zone-load completes |
| Anchor 3 — MBC5 cartridge byte at 0x0147 | UAT anchor 3 | Static ROM-file inspection, not runtime | `python3 -c "print(hex(open('build/gbkt/output/banks.gb','rb').read()[0x147]))"` — must equal `0x1b` (or `0x19` per D-07; planner decides which) |
| Anchor 4 — SRAM persistence across GBST round-trip | UAT anchor 4 | SRAM not captured by Coffee-GB savestate stop/start; must use `emulator_save_state` / `emulator_load_state` within session per RESEARCH § "SRAM emulator persistence" | step to save trigger → `emulator_read_memory("0xA000", 4)` → `emulator_save_state` → `emulator_load_state` → re-read SRAM → bytes match |
| 4th-signal — All `.noi` banks ≤ 16384 bytes | ROADMAP success criterion | `.noi` is build-output artifact, parsed once post-build | `grep "DEF l__CODE_" gbkt-examples/banks/build/gbkt/output/banks.noi \| awk '{print $3}'` — convert hex, assert each ≤ 0x4000; captured under `evidence/oracle-comparison.md` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (build.gradle.kts + settings.gradle.kts + 3 stub test files + 2 contract docs)
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s for quick suite
- [ ] All 4 UAT anchors mapped to a row in Per-Task Verification Map AND a Manual-Only row (manual screenshot path)
- [ ] ROM-build smoke test (`./gradlew :gbkt-examples:banks:buildRom`) is a `[BLOCKING]` task before any verification verdict (per memory `feedback_rom_build_smoke_test_for_codegen_phases.md`)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
