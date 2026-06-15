# Phase 19: Codegen Fixes — Metasprite Cluster — Specification

**Created:** 2026-06-13
**Ambiguity score:** 0.16 (gate: ≤ 0.20)
**Requirements:** 5 locked

## Goal

Each of the nine metasprite seeds confirmed VERIFIED-ALREADY-FIXED by Phase 16 triage (SEED-004/005/006/013 visual-parity; SEED-007/008/009/010/011 structural-latent) gains formal Phase-19 closure evidence: fresh HEAD runtime screenshots for the visual-parity four, and audited-and-gap-filled JVM emission guards for the structural five — with no production codegen changes and the metasprites ROM still building and rendering correctly.

## Background

Phase 16 triage (`.planning/phases/16-seed-triage/TRIAGE.md`) dispositioned all nine in-scope metasprite seeds as **VERIFIED-ALREADY-FIXED** against current master, and Plan 16-10 moved them to `.planning/seeds/archive/`:

- **FIX-01 visual-parity (4):** SEED-004 (elephant tile rendering — USER OVERRIDE, renders correctly), SEED-005 (BG checkerboard present, Phase 10.1 fix), SEED-006 (`_elephant_subPalette = subpal;` present at `metasprites/main.c` `play_frame():283`), SEED-013 (GBC sub-palette colors correct, Phase 10.2 fix). All four were LOCKED via human visual review 2026-06-12 with HEAD screenshots in `.planning/phases/16-seed-triage/evidence/`.
- **FIX-02 structural-latent (5):** SEED-007 (`actorPaletteAutoSlot++` counter, `GameBuilder.kt:716`), SEED-008 (monotonic VRAM allocator, Route A), SEED-009 (`<gbdk/metasprites.h>` include in `metasprites-stress` `bank1.c:7`), SEED-010 (namespaced `elephant_metasprites[]`/`tiger_metasprites[]`), SEED-011 (hiwater=0 once per frame, Route A).

The codebase already carries extensive metasprite emission coverage (e.g. `MetaspriteEmissionTest`, `MetaspriteSubPaletteEmissionTest`, `MetaspriteAssetTileLoadEmissionTest`, `MetaspritePathAEmissionTest`, `MetaspriteSpritePaletteEmissionTest`, `MetaspriteDescriptorEmissionTest`, `MetaspriteBoundPosEmissionTest`, `SubPaletteAccessorEmissionTest` in `gbkt-backend-gbdk`, plus `MetaspriteEmissionTest`/`MetaspritesGrayPaletteEmissionTest` in `gbkt-examples/metasprites`). Some FIX-02 seeds are therefore likely already guarded; the open work is mapping each seed to a guard and authoring only the missing ones.

This is a **confirmation / regression-guard phase, not a new-fix phase.** No production codegen change is expected; the fixes already shipped in Phases 10.1/10.2 and earlier. Phase 19 produces evidence artifacts and test guards so the archived seeds are traceably defended against future regression. PR #77 (S3776 cognitive-complexity burn-down) is open and must not be merged until Phases 19/20/21 land; Phase 19's commits must stay strictly separate from any S3776 commits so the byte-identity oracle can attribute C-output changes unambiguously.

## Requirements

1. **FIX-01 visual-parity confirmation**: Fresh HEAD runtime screenshots confirm SEED-004/005/006/013 render correctly.
   - Current: Only Phase 16 triage screenshots (captured before Phase 17/18 work) exist; no Phase-19-HEAD visual artifacts
   - Target: A fresh runtime screenshot captured at Phase 19 HEAD for each of SEED-004 (elephant tiles uncorrupted), SEED-005 (BG checkerboard, not diagonal), SEED-006 (elephant uses assigned sub-palette), SEED-013 (correct GBC sub-palette colors), each filed under `.planning/phases/19-codegen-fixes-metasprite-cluster/evidence/`
   - Acceptance: Four Phase-19-HEAD screenshots exist, each visibly showing the fixed behavior for its seed; the metasprites/metasprites-stress ROM was rebuilt clean immediately before capture (no stale ROM)

2. **FIX-02 emission-guard audit and gap-fill**: Every structural-latent seed (SEED-007..011) maps to a named JVM emission assertion that fails if the fix is reverted.
   - Current: Extensive metasprite emission tests exist but no documented 1:1 mapping from each of SEED-007..011 to a guarding assertion; coverage gaps unknown
   - Target: A written audit maps each of the 5 seeds to an existing guarding assertion OR a newly authored per-seed guard; only the missing guards are authored (no duplicate coverage); each new/identified guard asserts the fixed behavior on current code (GREEN)
   - Acceptance: The audit document names all 5 seeds with their guarding test+assertion; for any seed lacking a guard, a new emission test is added; `./gradlew :gbkt-backend-gbdk:test` (and any touched `gbkt-examples` module test) is GREEN; each guard carries a comment stating which reverted-fix scenario it would catch (RED-by-design)

3. **Metasprites ROM smoke**: The metasprites example ROM builds clean and renders correctly at HEAD.
   - Current: No Phase-19 build/run verification of the metasprites example
   - Target: `./gradlew :gbkt-examples:metasprites:buildRom` (and `:gbkt-examples:metasprites-stress:buildRom` where the FIX-02 seeds were observed) completes with 0 errors, and a fresh runtime screenshot shows correct rendering
   - Acceptance: buildRom exits 0; one fresh runtime screenshot of the running metasprites ROM at HEAD is filed under the phase evidence directory and shows correct sprite + background rendering

4. **Commit separation from S3776**: All Phase 19 commits are strictly separate from any S3776 / PR-#77 cognitive-complexity commits.
   - Current: PR #77 (S3776 burn-down) is open on the hardening branch; mixing Phase 19 evidence/test commits with S3776 commits would defeat byte-identity attribution
   - Target: Every Phase 19 commit contains only metasprite-confirmation work (evidence, audit doc, emission tests, docs); zero S3776 cognitive-complexity refactors are interleaved
   - Acceptance: `git log` for the phase shows no commit mixing S3776 refactors with metasprite-confirmation changes; commit messages scope each change to Phase 19 / FIX-01 / FIX-02

5. **Byte-identity preservation (no production codegen drift)**: Phase 19 changes do not alter generated C output for the example ROMs.
   - Current: The nine seeds are already fixed; Phase 19 should add tests/evidence/docs only, touching no production codegen path
   - Target: No file under production codegen (`gbkt-backend-gbdk` main sources, visitors, pipeline) is modified for behavior; if any production source must change, it is justified and re-confirmed against the byte-identity oracle
   - Acceptance: A byte-identity check of the metasprites (and metasprites-stress) generated `main.c`/bank files before vs. after the phase shows no diff attributable to Phase 19 (any change is explained and screenshot-re-confirmed)

## Boundaries

**In scope:**
- Fresh Phase-19-HEAD runtime screenshots for SEED-004/005/006/013 (FIX-01)
- An emission-guard audit document mapping SEED-007..011 to guarding assertions, plus newly authored per-seed guards only where coverage is missing (FIX-02)
- A clean `buildRom` + fresh runtime screenshot of the metasprites example at HEAD (Success Criterion 3)
- Confirmation that all 9 seeds remain in `.planning/seeds/archive/` (moved by Plan 16-10)
- JVM emission tests in `gbkt-backend-gbdk` and/or `gbkt-examples/metasprites*` modules

**Out of scope:**
- New fixes to metasprite codegen — all 9 seeds are already VERIFIED-ALREADY-FIXED; this phase confirms, it does not fix
- Actual revert→RED→restore demonstrations — RED state is established by test design + comment (assert-GREEN sufficient, per round-1 decision)
- Banks trio (SEED-014/015/016) and tRNS sprite outline (SEED-PHASE-13-SPRITE-OUTLINE) — Phase 20 (FIX-03/FIX-04)
- Platformer and remaining DSL/tooling seeds — Phase 21 (FIX-05/FIX-06)
- S3776 cognitive-complexity refactors and merging PR #77 — separate workstream; PR #77 stays open until 19/20/21 complete
- Any production codegen change — would break byte-identity and signal an unexpected regression

## Constraints

- Runtime screenshots MUST be captured in the example's correct target mode (use `gbcMode=true` with the `.noi` symFile if the metasprites example targets GBC) — DMG-mode captures of a GBC target read as false palette regressions ([[learning_platformer_mcp_needs_gbc_mode]]).
- ROMs MUST be rebuilt clean immediately before screenshot capture — JVM tests cannot detect staleness in `build/gbkt/generated/` ([[feedback_rom_build_smoke_test_for_codegen_phases]]).
- Executors must run `:module:spotlessApply :module:detekt` per-commit; `:module:test` and the pre-commit hook do NOT run spotless/detekt ([[project_executor_gate_misses_spotless_detekt]]).
- Visual truths (FIX-01 closure, ROM render) require runtime screenshot evidence — variable-state assertions are insufficient ([[feedback_visual_evidence_for_visual_truths]]).
- `pluginTest` (not `:gbkt-gradle-plugin:test`) is the correct task if plugin fixtures are touched; it has a known publish/test ordering race — verify via two invocations if used.

## Acceptance Criteria

- [ ] Four fresh Phase-19-HEAD runtime screenshots (SEED-004, 005, 006, 013) exist under the phase evidence dir, each showing the fixed behavior
- [ ] An audit document maps each of SEED-007..011 to a named guarding test + assertion
- [ ] Any FIX-02 seed without an existing guard has a newly authored named emission test
- [ ] Each FIX-02 guard carries a comment stating the reverted-fix scenario it would catch (RED-by-design)
- [ ] `./gradlew :gbkt-backend-gbdk:test` is GREEN (plus any touched `gbkt-examples` module test)
- [ ] `./gradlew :gbkt-examples:metasprites:buildRom` exits 0 and a fresh HEAD runtime screenshot shows correct rendering
- [ ] All 9 seeds confirmed present in `.planning/seeds/archive/` with no orphans left in `.planning/seeds/`
- [ ] No Phase 19 commit interleaves S3776 cognitive-complexity refactors with metasprite-confirmation work
- [ ] Generated C output for the metasprites example shows no Phase-19-attributable diff (byte-identity preserved), or any diff is explained and screenshot-re-confirmed

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                                        |
|--------------------|-------|------|--------|--------------------------------------------------------------|
| Goal Clarity       | 0.90  | 0.75 | ✓      | Confirm 9 already-fixed seeds via screenshots + emission guards |
| Boundary Clarity   | 0.82  | 0.70 | ✓      | 9 specific seeds; confirmation-only; Phases 20/21 + S3776 excluded |
| Constraint Clarity | 0.75  | 0.65 | ✓      | GBC-mode capture, clean rebuild, commit separation, byte-identity |
| Acceptance Criteria| 0.85  | 0.70 | ✓      | Fresh screenshots ×4 + ROM smoke; audit+fill guards; assert-GREEN |
| **Ambiguity**      | 0.16  | ≤0.20| ✓      |                                                              |

Status: ✓ = met minimum, ⚠ = below minimum (planner treats as assumption)

## Interview Log

| Round | Perspective    | Question summary                              | Decision locked                                                        |
|-------|----------------|----------------------------------------------|-----------------------------------------------------------------------|
| 0     | Researcher     | What's the true state of the 9 seeds?        | All VERIFIED-ALREADY-FIXED in Phase 16, archived by 16-10; this is a confirmation phase |
| 1     | Failure Analyst| Reuse triage screenshots or re-shoot?        | Fresh re-shoot at Phase-19 HEAD (+ ROM smoke shot) — defends against post-triage drift |
| 1     | Simplifier     | How to establish FIX-02 emission guards?     | Audit existing coverage, map each seed, author only missing guards    |
| 1     | Boundary Keeper| Must guards demonstrate RED via revert?      | Assert-GREEN sufficient; RED-by-design documented in comment, no revert |

---

*Phase: 19-codegen-fixes-metasprite-cluster*
*Spec created: 2026-06-13*
*Next step: /gsd-discuss-phase 19 — implementation decisions (test placement, screenshot capture harness, audit doc format)*
