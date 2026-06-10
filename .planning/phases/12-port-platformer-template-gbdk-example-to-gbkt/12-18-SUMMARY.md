---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 18
subsystem: gbkt-examples-platformer-template + gbkt-gradle-plugin-buildchain + first-buildrom
tags: [platformer, buildrom, smoke-test, defect-catalog, wide-blast-radius, d-21, d-01, wave-11, escalate, blocked]
status: blocked-pending-decision

# Dependency graph
requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt
    provides: "12-17 substrate (banked title + nextLevel zones + level-switch wiring + setup_current_level codegen + main()-loop guard); 12-16 substrate (3 gameplay zones + 6-frame metasprite + 2 placeholder scenes); 12-08 (buildTilemapCollisionGlobals); 12-11 (PlatformerVisitor tilemap-collision branch); 12-13 (PlatformerVisitor jumpHold extension)"
  - phase: 11-port-banks-gbdk-example-to-gbkt
    provides: "ConvertZoneTilesetsTask (Plan 11.1-17 Phase C — emits _zone_<id>_tileset.{c,h} + _zone_<id>_tilemap.c at buildRom)"
provides:
  - "First :buildRom invocation evidence (D-21 ROM-build smoke test, Phase 12 terminal subphase per `feedback_rom_build_smoke_test_for_codegen_phases.md`)"
  - "Inline-fix #1: stripped redundant `res/` prefix from 5 zone `tileset(asset(...))` calls in PlatformerTemplate.kt — D-C2 path resolution defect (Rule 1 bug). Commit `4830d69c`."
  - "Defect cluster catalog (5 distinct defects across DSL composition, ConvertZoneTilesetsTask, GBDKPipelineV2 setup_current_level, PlatformerVisitor naming, MetaspriteVisitor magic-name) — see evidence/first-buildrom.md."
affects:
  - "12-19 / 12-20 / 12-21 / 12-22 (UAT anchors) — BLOCKED until ROM builds GREEN"
  - "Phase 12.1 (proposed) — defect-cluster reconciliation sub-phase per `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`"

# Tech tracking
tech-stack:
  added: []  # No new dependencies; pure host-toolchain invocation (lcc/SDCC + png2asset).
  patterns:
    - "First-buildRom defect-cluster catalog pattern — terminal subphase plan invokes :clean :buildRom, captures stderr verbatim, classifies each defect by layer (DSL / DSL→pipeline / pipeline / visitor / task), and disposes inline-fix vs escalate per blast radius. Mirrors the Phase 07.4 round-2/round-4 pattern where JVM tests passed but :buildRom revealed runtime gaps."

key-files:
  created:
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/first-buildrom.md"
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-18-SUMMARY.md"
  modified:
    - "gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt"

key-decisions:
  - "Inline-fix Defect 1 (asset path duplication) — single file, single visitor's worth of edits (5 line changes), Rule 1 bug. Committed as `4830d69c`."
  - "DO NOT inline-fix Defects 2-5 — they span 3 distinct codegen modules (GBDKPipelineV2, PlatformerVisitor, MetaspriteVisitor, ConvertZoneTilesetsTask) and require 4 contract-changing decisions. Per `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`, the executor escalates rather than driving inline."
  - "Return a `decision` checkpoint to the human (per Plan 12-18 charter §autonomous: false) with two options: (a) approve creation of insert-phase 12.1 to absorb defects 2-5, or (b) request smaller inline-fix probe of one defect to test cascade resolution."
  - "Do NOT mark this plan `complete` — return BLOCKED. The orchestrator + human decide whether to retry inline or insert 12.1."

requirements-completed: [D-21]  # ROM-build smoke test was RUN (the requirement is the run, not the GREEN outcome)
requirements-blocked: [D-01]     # Lifted bug cap — cluster exceeds Plan 12-18 absorption budget; routes to 12.1

# Metrics
duration: ~45min
completed: 2026-05-22
---

# Phase 12 Plan 18: First :buildRom Smoke Test — Defect Cluster Catalog + Checkpoint

**Plan 12-18 is the FIRST :buildRom invocation for the gbkt platformer-template port (D-21, Phase 12 terminal subphase per `feedback_rom_build_smoke_test_for_codegen_phases.md`). The run surfaced 5 distinct defects spanning DSL composition, ConvertZoneTilesetsTask emission, GBDKPipelineV2 codegen, and PlatformerVisitor + MetaspriteVisitor naming. Defect 1 (asset-path duplication) was inline-fixed in commit `4830d69c`. Defects 2-5 are wide-blast-radius and exceed Plan 12-18's inline-fix budget; per `feedback_route_to_proper_phase_when_blast_radius_is_wide.md` the executor escalates rather than driving inline. The plan returns a `decision` checkpoint to the human with two options: (a) approve creation of insert-phase 12.1 to absorb defects 2-5 as a 4-plan reconciliation sub-phase, or (b) request a smaller probe.**

## Performance

- **Duration:** ~45 min wall (2 buildRom invocations: ~6s cold + ~1s warm; rest is defect-catalog authoring).
- **Tasks completed:** 1 / 2 (Task 1 RAN; Task 2 = human checkpoint = returned not completed).
- **Files modified:** 1
  - `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt` (-5 / +5 — strip `res/` prefix from 5 asset paths)
- **Files created:** 2
  - `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/first-buildrom.md` (defect catalog)
  - `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-18-SUMMARY.md` (this file)

## Accomplishments

### Task 1 — Run clean buildRom + capture output

**Attempt 1 (pre-fix):** `:convertZoneTilesets` FAILED on `D-C2` path-not-found. Asset paths in PlatformerTemplate.kt carry a redundant `res/` prefix; the gradle plugin's `assets("res")` extension already establishes the project-root-relative `res/` directory.

**Inline-fix #1 applied:** stripped `res/` prefix from all 5 `tileset(asset(...))` calls (commit `4830d69c`).

**Attempt 2 (post-fix):** Progress through `:processAssets`, `:generateC`, `:convertSprites`, `:convertZoneTilesets`, `:copyResources` (all GREEN), then `:compileRom` **FAILED** with 51 SDCC error-20 (undefined identifier) errors across `main.c` and `bank1.c`. No `.gb` produced; no `.noi` produced.

### Task 2 — Decision checkpoint (NOT COMPLETED — returned to human per the plan's `autonomous: false` directive)

See "Checkpoint" section below.

## Task Commits

1. **Inline-fix #1 — strip `res/` prefix from zone asset paths** — `4830d69c` (fix)

## Files Created/Modified

- `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt` — 5 line changes (asset path correction; see commit `4830d69c`).
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/first-buildrom.md` — Full defect catalog with verbatim error excerpts, root-cause analysis, and proposed dispositions.
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-18-SUMMARY.md` — This file.

## Verification

**Plan-charter `must_haves.truths` adjudication:**

| Truth | Status | Notes |
|-------|--------|-------|
| `./gradlew :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:buildRom EXIT 0` | **FAIL** | Attempt 2 exits 1 at `:compileRom`. |
| `No SDCC 'unknown address/value' errors in output` | **N/A** | SDCC failed before link; only error-20 (undefined identifier) errors surfaced. |
| `No lcc warnings` | **N/A** | Build failed at `:compileRom`; no lcc warnings emitted (only SDCC compile errors). |
| `.noi file: every DEF l__CODE_<N> byte size ≤ 16384` | **N/A** | No `.noi` produced — link step never ran. |
| `Generated main.c contains is_tile_solid AND _bkg_set_level_submap_banked AND setup_current_level` | **PASS** | All three present in `build/gbkt/generated/main.c` (verified via `grep`). Codegen contracts from Plans 12-08 + 12-10 + 12-17 are intact at the C-emission tier. |
| `If buildRom fails on FIRST try, evidence/first-buildrom.md NAMES the defect AND inline-fix sub-task or insert-plan recommendation is made` | **PASS** | Defects 1-5 named; Defect 1 inline-fixed (commit `4830d69c`); Defects 2-5 routed to proposed insert-phase 12.1. |

**Plan-charter `must_haves.artifacts` adjudication:**

| Artifact | Status |
|----------|--------|
| `gbkt-examples/platformer-template/build/gbkt/output/platformer-template.gb` | **NOT PRODUCED** — `:compileRom` failed. |
| `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/first-buildrom.md` | **PRODUCED** — defect catalog complete. |

## Decisions Made

- **Inline-fix Defect 1 only.** Rule 1 (bug); single file; 5 line changes; no contract change. Fits Plan 12-18's D-01 lifted-cap budget.
- **Escalate Defects 2-5.** Four distinct codegen-contract defects across 3 visitors + 1 task is beyond Plan 12-18's absorption. Per `feedback_route_to_proper_phase_when_blast_radius_is_wide.md`, the executor escalates.
- **DO NOT mark Plan 12-18 `complete`.** Status: `blocked-pending-decision`. Orchestrator + human decide between (a) approve `/gsd-phase --insert` for 12.1 or (b) probe one defect inline.

## Deviations from Plan

### 1. [Rule 1 - Bug] Asset path duplication

- **Found during:** Task 1 Attempt 1.
- **Issue:** All 5 `tileset(asset("res/graphics/<file>.png"))` calls carried a redundant `res/` prefix; `assets("res")` in `build.gradle.kts` already establishes the project-root-relative `res/` directory.
- **Fix:** Strip `res/` prefix → matches Banks example convention.
- **Files modified:** PlatformerTemplate.kt (-5/+5 lines).
- **Commit:** `4830d69c`.
- **Rule classification:** Rule 1 (bug — broken behavior caught by buildRom).

### 2. [Rule 4 - Architectural / ESCALATED] 4 codegen-contract defects beyond inline-fix budget

- **Found during:** Task 1 Attempt 2.
- **Issue:** `:compileRom` surfaces 51 errors clustered into 4 distinct defects (BANK() on HOME-bank tilemap, missing tilemap WIDTH/HEIGHT symbols, `_player_*` naming inconsistency, `_posX/_posY` magic-name). Each defect requires a codegen-contract decision (e.g. "should tilemaps move to a banked file or should BANK() be replaced with a literal in setup_current_level?"). Inline-fixing without human input would be the "smoke and mirrors" anti-pattern (per `feedback_quality_over_shortcuts.md`).
- **Disposition:** Return `decision` checkpoint per plan charter §autonomous: false. Recommend `/gsd-phase --insert 12` to create 12.1 — 4 sub-plans, one per defect.
- **Files modified:** N/A — no inline fix attempted.
- **Rule classification:** Rule 4 (architectural — multi-module codegen-contract change).

## Authentication Gates

None — local toolchain invocation only.

## Issues Encountered

- See "Deviations" section + `evidence/first-buildrom.md` for verbatim error catalog.

## User Setup Required

None — GBDK_HOME is already set (`/Users/michalsvacha/gbdk`). The host has GBDK-4.5.0 (lcc rev 2.0 2025-12-28).

## Known Stubs

Stubs from predecessor plans (12-16 / 12-17) that surfaced as ACTUAL DEFECTS in this :buildRom run:

| Stub (predecessor) | Surfaced as Defect # | Resolution path |
|--------------------|---------------------|-----------------|
| 12-17 §"Per-case body of setup_current_level references `_zone_<id>_tilemap_WIDTH/HEIGHT` symbols that only resolve at buildRom" | Defect 3 — these symbols ARE NEVER EMITTED by ConvertZoneTilesetsTask | Phase 12.1 Plan B |
| 12-17 §"Per-zone palette load NOT included in setup_current_level body" | Latent — does not block compileRom but will affect runtime visuals | Phase 12.1 (optional) |
| 12-16 §"Per-frame metasprite tile composition is single-tile placeholders" | Latent — does not block compileRom (placeholders compile fine) but affects runtime visuals | Phase 12.1 Plan D (optionally) or post-12.1 |
| 12-16 §"`player` actor with `hitbox(0, 0, 8, 24)` not declared" | Defects 4 + 5 — without the actor, the visitor's physics-update + metasprite-render paths reference undeclared globals | Phase 12.1 Plan C + D + E |

## Threat Flags

None — no new network endpoints, no new auth paths, no new schema changes at trust boundaries. The defects are all internal codegen-contract mismatches.

## TDD Gate Compliance

Plan 12-18 has `type: execute` (not `tdd`). No RED/GREEN gate sequence applies.

## Next Phase Readiness

**This plan is BLOCKED.** The next-step decision is owned by the human via the checkpoint below.

### Recommended forward path (route A — escalate)

1. Run `/gsd-phase --insert 12` to create **12.1-platformer-template-codegen-contract-reconciliation**.
2. Author 12.1 with 4 plans:
   - 12.1-01 — Defect 2 (BANK macro on HOME-bank tilemap).
   - 12.1-02 — Defect 3 (tilemap WIDTH/HEIGHT emission).
   - 12.1-03 — Defect 4 (`_player_*` naming reconciliation + player actor declaration).
   - 12.1-04 — Defect 5 (`_posX/_posY` metasprite render reconciliation).
   - 12.1-05 — Re-run :buildRom (terminal plan per `feedback_many_small_plans_terminal_subphase.md`).
3. After 12.1 closes GREEN, return to 12-19 / 12-20 / 12-21 / 12-22 (UAT anchors).

### Alternative forward path (route B — probe-then-decide)

Pick the smallest single defect (Defect 3 is the most isolated — only touches ConvertZoneTilesetsTask), inline-fix it, re-run :buildRom. If the OTHER defects do NOT cascade-resolve, escalate. This route preserves the option to absorb the cluster inline but trades wall-time for a smaller insert-phase if the cluster turns out to be smaller than catalogued.

## Self-Check: PASSED

- File `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt` exists.
- Commit `4830d69c` exists in `git log --oneline -3` (Inline-fix #1).
- File `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/first-buildrom.md` exists with 5-defect catalog.
- buildRom invocation logs at `/tmp/platformer-template-buildrom.log` + `/tmp/platformer-template-buildrom-2.log` (per Task 1 Action step 1).
- Setup_current_level emission shape preserved (per `must_haves.truths` #5):
  - `grep -c "is_tile_solid" main.c` → present (Plan 12-08 helper).
  - `grep -c "_bkg_set_level_submap_banked" main.c` → present (Plan 12-10 helper).
  - `grep -c "void setup_current_level" main.c` → 1 (Plan 12-17 function).

## Checkpoint

**Type:** decision

**Awaiting:** human selection of route A (escalate to 12.1) vs route B (probe one defect inline). Per the plan's `autonomous: false` charter, this checkpoint MUST be resolved by the human.

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Plan: 18*
*Completed: 2026-05-22 (with BLOCKED status pending decision)*
