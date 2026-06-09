---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 20
subsystem: testing
tags: [uat, mcp-emulator, anchor-2, tilemap-collision, jump-cycle, is-tile-solid, kotlin]

requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt (Plan 12-19)
    provides: Anchor 1 GREEN — gameplay scene reachable + tilemap rendered, prerequisite for any in-game UAT
  - phase: 12 (Plans 12-08, 12-10, 12-11, 12-13)
    provides: is_tile_solid helper, tilemap-collision physics branch, jumpHold gating
provides:
  - Anchor 2 (tilemap-collision jump cycle) GREEN — JVM test + 3 binding screenshots + human-verify approval
  - PlatformerTemplateUatTest.anchor2TilemapCollision (real impl, no SKIP stub)
  - Variable trace: playerVy 0 → -800 → 0 over 61 frames (full jump arc)
  - Established convention: variable name `playerVy` (metadata strips leading underscore from `_playerVy`)
affects: [phase-12 anchor-3..5, phase-12-final-verifier, future-platformer-uat-ports]

tech-stack:
  added: []
  patterns:
    - "Multi-waypoint anchor: capture screenshot + read variable at each waypoint, single try/finally with newAgent().use"
    - "Re-grounding poll loop: step in 10-frame increments up to a cap, break when vy hits 0"

key-files:
  created:
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-2/01-grounded.png
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-2/02-mid-jump.png
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-2/03-landed.png
    - .planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-2/anchor2-variables.txt
    - .planning/seeds/SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY.md
  modified:
    - gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt (anchor2 SKIP stub → real impl)

key-decisions:
  - "Variable name: `playerVy` (metadata-exposed name without the `_` prefix that the codegen emits as `_playerVy`). The plan's `_player_vy` placeholder did not exist — actual codegen emits camelCase per the i16Var delegate convention."
  - "Jump-cycle physics is the load-bearing assertion for Anchor 2 (D-12 tilemap-collision). The vy=0→-800→0 transition over 61 frames PROVES is_tile_solid + 5-point probe re-grounded the player. The 'is player visibly on a platform' visual question is orthogonal and was captured as SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY."

patterns-established:
  - "When a user-flagged ambiguity is orthogonal to the anchor's load-bearing truth, capture it as a SEED rather than expanding the plan's scope. Anchor 2 proves the collision system; spawn-position is a level-design concern."

requirements-completed:
  - D-08
  - D-10
  - D-12
  - D-14
  - D-overfitting-1
  - D-overfitting-3

duration: ~10 min
completed: 2026-05-23
---

# Plan 12-20: UAT Anchor 2 — Tilemap Collision Jump Cycle

**MCP-driven anchor 2 GREEN end-to-end: playerVy transitions 0 → -800 → 0 over a 61-frame jump arc, proving is_tile_solid + 5-point probe re-grounded the player on a solid tile. 3 binding screenshots captured + human-verify APPROVED with spawn-position clarity logged as a SEED for follow-up.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-05-23T12:05Z
- **Completed:** 2026-05-23T12:11Z
- **Tasks:** 2 (Task 1 auto + Task 2 human-verify gate)
- **Files modified:** 1 source (test) + 5 evidence artifacts + 1 SEED

## Accomplishments

- Replaced anchor2TilemapCollision SKIP stub with full MCP-driven jump-cycle test
- Captured 3 binding screenshots (01-grounded, 02-mid-jump, 03-landed)
- Variable trace recorded: playerVy 0 → -800 → 0 over 61 frames
- Human-verify APPROVED — jump motion visible (player visibly higher in 02 than 01/03)
- Captured user-flagged spawn-position ambiguity as `SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY.md`

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement anchor2TilemapCollision JVM test** — included in this commit
2. **Task 2: Human-verify gate** — APPROVED (no separate commit on the gate; approval recorded here)

## Files Created/Modified

- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt` — anchor2 SKIP stub replaced with real impl + variable-name comment
- `.planning/phases/.../evidence/uat-screenshots/anchor-2/01-grounded.png` — pre-jump pose (vy=0)
- `.planning/phases/.../evidence/uat-screenshots/anchor-2/02-mid-jump.png` — mid-jump pose (vy=-800)
- `.planning/phases/.../evidence/uat-screenshots/anchor-2/03-landed.png` — post-gravity pose (vy=0 again)
- `.planning/phases/.../evidence/uat-screenshots/anchor-2/anchor2-variables.txt` — full variable trace
- `.planning/seeds/SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY.md` — deferred follow-up

## Decisions Made

- **Variable name adaptation:** plan's placeholder `_player_vy` does not exist; codegen emits `_playerVy` and metadata exposes `playerVy`. Adapted test + recorded reasoning in code comment per the plan's "adapt — but record" guidance.
- **Re-grounding poll loop:** rather than a single large step, poll in 10-frame increments up to 120 max and break early when vy hits 0. Records `frames_to_land` for diagnostic value.
- **SEED instead of scope creep:** user's "player at screen center" observation is a level-design / spawn-position concern orthogonal to D-12 collision verification. Captured as SEED rather than expanded plan scope.

## Deviations from Plan

None on the physics assertion. One small adaptation:
- Variable name `playerVy` instead of `_player_vy` (plan called this out as expected).

## Issues Encountered

- User flagged that grounded/landed poses both show the player at roughly screen-vertical center, raising a question about whether the player is grounded on the intended floor row vs caught on a middle-row platform. The collision physics is provably correct (vy transition + re-grounding), so this is a level-design / spawn-position concern. Captured as SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY for Phase 13 absorption or a future inserted phase.

## User Setup Required

None.

## Next Phase Readiness

- Anchor 2 GREEN unblocks Wave 13 continuation: Plans 12-21 (anchor 3 horizontal scroll), 12-22 (anchor 4 metasprite animation).
- Variable-name convention now established (`playerVy`, not `_player_vy`); same pattern likely applies to other player-state vars in remaining anchors.

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Plan: 20*
*Completed: 2026-05-23*
