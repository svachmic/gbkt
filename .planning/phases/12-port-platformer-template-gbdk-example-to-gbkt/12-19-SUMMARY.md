---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 19
subsystem: testing
tags: [uat, mcp-emulator, anchor-1, visual-evidence, gbkt-test, kotlin]

requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt (Plan 12-18)
    provides: ROM that builds cleanly (4 banks, MBC1, 64 KB)
  - phase: 12.2-convertzonetilesetstask-real-tilemap-extraction-via-png2asset
    provides: Real PNG tilemap extraction (no synthetic ramps) + SceneVisitor PATH A per-zone WIDTH/HEIGHT macros — both required for Anchor 1 to render the platformer scene visually
provides:
  - Anchor 1 (title → gameplay scene transition) GREEN — JVM test + 2 binding screenshots + human-verify approval
  - PlatformerTemplateUatTest.anchor1Title_to_Gameplay (real implementation, no SKIP stub)
  - Pattern for remaining anchors 2–5: newAgent → step → screenshot → press → step → screenshot → readVariable assertion
affects: [phase-12 anchors-2..5, phase-12-final-verifier, future-platformer-uat-ports]

tech-stack:
  added: []
  patterns:
    - "anchor-test layout: ROM-existence assumeTrue guard, then newAgent in try/finally with .close() in finally"
    - "evidence/uat-screenshots/anchor-N/ directory scheme for visual binding (mirrors Phase 9/10/11)"

key-files:
  created:
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-1/01-title.png
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-1/02-gameplay.png
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-1/01-title.json
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-1/02-gameplay.json
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-1/anchor1-variables.txt
  modified:
    - gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt (anchor1 SKIP stub → real impl)
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt (gameplay_enter cEmit wiring + _next_level=0 init)

key-decisions:
  - "Plan landed in 3 inline codegen fixes (commits 733770d6, e7e1bd48) alongside the UAT test impl — the test surfaced contract bugs (sceneHasEnterContent for zone-only scenes, fill_bkg_rect-after-substrate ordering, gameplay zone tileset/tilemap load via cEmit) that had to be fixed before the anchor could pass. Per D-01 lifted cap, these were absorbed inline rather than spawning a sub-phase."
  - "Defect 7 (ConvertZoneTilesetsTask synthetic-tilemap) surfaced at human-verify on the first re-shoot, was correctly escalated to Phase 12.2 (commits 29c6fa1a, 7b7ca184) rather than absorbed inline — blast radius (5 zones + retroactive Phase 11) exceeded Phase 12's absorption budget per memory rule feedback_route_to_proper_phase_when_blast_radius_is_wide."
  - "Plan 12-19 stayed incomplete through Phase 12.2 lifecycle; final close-out runs post-12.2 with the same test scaffolding (commit 733770d6 unchanged) but a fresh ROM that produces real PNG tilemaps."

patterns-established:
  - "Inline codegen-bug fix during a UAT-anchor plan: when a test surfaces a small contract bug whose blast radius is local to the anchor, fix inline + commit as fix(plan-id) alongside the test. Escalate via /gsd-phase --insert only when the defect's blast radius exceeds the current phase."
  - "Phase blocked → unblocked across a sub-phase ship: plan stays incomplete (no SUMMARY.md), test scaffolding committed early, re-shoot from current ROM after sub-phase ships. Safe-resume gate trips on next /gsd-execute-phase invocation and offers re-shoot/re-execute/skip recovery."

requirements-completed:
  - D-08
  - D-10
  - D-overfitting-1
  - D-overfitting-3

duration: ~4 days elapsed (test impl 2026-05-22 → block on Defect 7 → 12.2 ship 2026-05-23 → re-shoot + close 2026-05-23). Active orchestrator time post-12.2: ~10 min.
completed: 2026-05-23
---

# Plan 12-19: UAT Anchor 1 — Title → Gameplay Scene Transition

**MCP-driven anchor1 test GREEN end-to-end: PlatformerTemplateUatTest.anchor1Title_to_Gameplay passes, 2 binding screenshots captured from post-12.2 ROM, human-verify APPROVED on visible title art + visible gameplay tilemap + visible player metasprite.**

## Performance

- **Duration:** ~4 days elapsed (blocked across Phase 12.2 ship); ~10 min active orchestrator time for post-12.2 re-shoot + close
- **Started:** 2026-05-22 (test scaffolding commit 733770d6)
- **Completed:** 2026-05-23T12:01Z (human-verify APPROVED on re-shoot)
- **Tasks:** 2 (Task 1 auto + Task 2 human-verify gate)
- **Files modified:** 2 source (test + pipeline) + 5 evidence artifacts

## Accomplishments

- Replaced Plan 12-03 SKIP stub for `anchor1Title_to_Gameplay` with full MCP-driven implementation
- Captured 2 binding screenshots from post-12.2 ROM (01-title.png, 02-gameplay.png)
- Variable assertion verified: `_current_scene` transitions from title-id → gameplay-id after Start press
- Human-verify GATE APPROVED — title screen visibly recognizable, gameplay tilemap + player metasprite visible
- Surfaced 3 inline codegen contract bugs (sceneHasEnterContent for zone-only scenes, fill_bkg_rect ordering, gameplay zone tileset/tilemap wiring) — fixed in same plan per D-01 lifted cap
- Correctly escalated Defect 7 (ConvertZoneTilesetsTask synthetic tilemap) to Phase 12.2 — blast radius exceeded Phase 12 absorption budget

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement anchor1Title_to_Gameplay JVM test** — `733770d6` (feat)
2. **Task 1 (inline fix): Wire gameplay zone tileset+tilemap via gameplay_enter** — `e7e1bd48` (fix)
3. **Task 1 (escalation): Catalog Defect 7, escalate to Phase 12.2** — `29c6fa1a` (docs)
4. **Task 2: Human-verify gate** — re-shot via test rerun 2026-05-23T11:53Z; user APPROVED (no commit on the gate itself; approval recorded here)

_Note: the original anchor1 test method itself has not changed since 733770d6 — the re-shoot uses the same scaffolding with a fresh ROM produced by post-12.2 codegen._

## Files Created/Modified

- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt` — anchor1 SKIP stub replaced with real MCP-driven impl
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` — sceneHasEnterContent + fill_bkg_rect ordering + gameplay_enter cEmit for zone tileset/tilemap
- `.planning/phases/.../evidence/uat-screenshots/anchor-1/01-title.png` — title screen frame at boot+60
- `.planning/phases/.../evidence/uat-screenshots/anchor-1/02-gameplay.png` — gameplay frame after Start press +30
- Companion JSON snapshots + variable trace + debug observation under same directory

## Decisions Made

- **Inline fix vs escalate:** 3 small contract bugs (sceneHasEnterContent, fill_bkg_rect ordering, gameplay cEmit) absorbed inline per D-01 lifted cap; Defect 7 (synthetic tilemap, 5-zone blast radius) escalated to Phase 12.2 per memory rule `feedback_route_to_proper_phase_when_blast_radius_is_wide`.
- **Keep partial commits across block:** Plan 12-19's 3 commits stayed on `feat/d_and_d_gaps` during Phase 12.2 lifecycle rather than being reverted. Net-positive: test scaffolding + codegen fixes hold; only the evidence artifacts needed re-shooting post-12.2.
- **Re-shoot vs accept old screenshots:** Re-shot from current ROM rather than accepting the pre-12.2 screenshots, per memory rule `feedback_visual_evidence_for_visual_truths` (visual truths require fresh screenshots that match the current code).

## Deviations from Plan

None on this close-out pass — re-shoot followed the spec exactly. The 3 inline codegen fixes during the original 2026-05-22 task pass are documented above under Task Commits.

## Issues Encountered

- **Blocked across sub-phase ship:** First re-shoot (2026-05-22) failed human-verify because tilemaps rendered as synthetic ramps (Defect 7, root cause: `ConvertZoneTilesetsTask.synthesizeScreenTilemap` emitting modulo-tiled `_tileset_map` instead of `png2asset -map -maps_only -source_tileset` real layout). Phase 12.2 (13 plans, shipped 2026-05-23) fixed this. Plan 12-19 re-shoot succeeded on the post-12.2 ROM.
- **Stale tier1-shape fixture caught at close-out:** A leftover local mod to `evidence/tier1-shape/title_enter.c` was the post-12.2 SceneVisitor PATH A output that Phase 12.2-11 forgot to commit. Caught and committed as `a5dfb731 docs(12.2-11): sync title_enter.c tier1-shape fixture` before this plan closed.

## User Setup Required

None.

## Next Phase Readiness

- Anchor 1 GREEN unblocks Wave 13: Plans 12-20 (anchor 2 jump cycle), 12-21 (anchor 3 horizontal scroll), 12-22 (anchor 4 level switch).
- Test scaffolding pattern (newAgent → step → screenshot → press → step → screenshot → readVariable) is now the template each remaining anchor will follow.
- ROM is stable at 64 KB / 4 banks / MBC1; tilemaps render correctly across all 5 zones.

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Plan: 19*
*Completed: 2026-05-23*
