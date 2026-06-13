---
phase: 17-docs-reconciliation-and-quality-cleanup
plan: "09"
subsystem: docs
tags: [docs, dsl-reference, stale-api, cleanup]
dependency_graph:
  requires: [17-08]
  provides: [sections-5-through-9-accurate]
  affects: [context/DSL_REFERENCE.md]
tech_stack:
  added: []
  patterns: [implemented-only-truth, source-verified-snippets]
key_files:
  created: []
  modified:
    - context/DSL_REFERENCE.md
key-decisions:
  - "Entity Pools section (#5) replaced with accurate data-pool API: pool(elementType/structDef/VarType, capacity) delegate, acquire()/free(), hasSpace/activeCount, forEach/get; lifecycle/sprite pool API archived in FEAT-ENTITY-POOL-LIFECYCLE.md"
  - "Tweening/Easing section (#6) removed entirely — tween() and Easing enum are absent from codebase (archived in FEAT-TWEENING.md)"
  - "Camera System section (#7) rewritten: CameraBuilder config (follow()/bounds()/smoothing) + cameraOp(CameraAction.*) runtime table; all unimplemented handle methods removed"
  - "Camera Transitions (#8) extracted as separate section; fade(fadeIn,frames,after) documented as ScriptBuilder method (ScriptBuilder.kt:447); wipe/iris/flash removed"
  - "T-01 bug fixed: camera.fadeIn(20.frames) in enter block corrected to fade(fadeIn=true, frames=20)"
  - "Physics section (#9) rewritten: per-actor function-style API (gravity(n)/velocity(dx,dy)/bounce(f)/maxFallSpeed(n)/platformerMode()) + physicsUpdate(); global physics{}/gravityZone()/tag() removed"
requirements-completed: [DOCS-01, DOCS-02]
duration: 5min
completed: 2026-06-12
---

# Phase 17 Plan 09: Sections 5-9 Rewrites Summary

Sections 5-9 (Entity Pools, Tweening, Camera, Camera Transitions, Physics) rewritten as implemented-only truth with stale-API caveat banners removed from #5, #7, and #9.

## Performance

- **Duration:** ~5 min
- **Completed:** 2026-06-12
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments

- Entity Pools (#5): stale sprite/lifecycle pool API replaced with accurate data-pool API verified against `CollectionBuilders.kt:510-531`
- Tweening (#6): section removed entirely (tween()/Easing.* absent from codebase; archived in 17-04)
- Camera System (#7): CameraBuilder config + cameraOp(CameraAction.*) documented from SystemBuilders.kt:65-114 + ScriptOp.kt:469-474; all unimplemented handle methods (followX/Y, shake{}/impact/stopShake, setPosition/snapTo/camera.x/camera.y) removed
- Camera Transitions (#8): extracted as separate section documenting implemented `fade(fadeIn, frames, after)` from ScriptBuilder.kt:447; T-01 audit bug (camera.fadeIn() in enter block) corrected
- Physics (#9): per-actor function-style API documented from ActorBuilder.kt:500-557 + ScriptBuilder.kt:657; global physics{}/gravityZone()/tag() removed
- Stale-API caveat count: 8 → 4 (3 removed from #5/#7/#9; #8 never had its own banner)

## Task Commits

1. **Task 1: Rewrite Entity Pools (#5), Remove Tweening (#6), Rewrite Physics (#9)** - `d6e1e5f7` (docs)
2. **Task 2: Rewrite Camera (#7) and Camera Transitions (#8)** - `183bd5a3` (docs)

## Files Created/Modified

- `context/DSL_REFERENCE.md` — Sections 5-9 rewritten as implemented-only truth

## Decisions Made

- Entity Pools: documented `PoolRef.acquire()/free()/hasSpace/activeCount/forEach/get` which are all confirmed implemented in `CollectionBuilders.kt:120-183`. The `PoolDelegate` third overload `pool(elementType: VarType, capacity)` (line 530) also documented alongside the two primary overloads.
- Physics: `friction()` is in `MovementBuilder` (line 429), not `PhysicsBuilder` — omitted from the Physics section to avoid confusion. The per-actor physics section documents only the five `PhysicsBuilder` methods: `gravity(n)`, `velocity(dx,dy)`, `bounce(f)`, `maxFallSpeed(n)`, `platformerMode()`.
- Camera: `smoothing` is a `Float` var on CameraBuilder (SystemBuilders.kt:67) — documented as cosmetic note per audit verdict 7.1 ("declared, applied in CameraSystem but not actively wired in all backends").
- T-01 bug fix applied: `camera.fadeIn(20.frames)` (DOCS-AUDIT flag T-01, disposition fix-in-17-08 but 17-09 owns Camera) corrected to `fade(fadeIn = true, frames = 20)` in the Camera Basic Setup example.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. These are documentation prose edits — no runtime code stubs.

## Threat Flags

None. Public documentation; no secrets. T-17-09 (Information Disclosure) accepted per threat model.

## Self-Check: PASSED

- `context/DSL_REFERENCE.md` exists: confirmed
- Commit `d6e1e5f7` exists: confirmed
- Commit `183bd5a3` exists: confirmed
- Stale-API caveat count reduced: 8 → 4 (PASS)
- `tween(` count: 0 false hits (the one match is `toBeBetween` in Testing section)
- `Easing.` count: 0 (PASS)
- `gravityZone` count: 0 (PASS)
- `snapTo` count: 0 (PASS)
- `followX`/`followY` count: 0 (PASS)
- `physicsUpdate` count: 6 (PASS — documented correctly)
- `fade(` count: 6 (PASS — documented correctly)
- No v0.2.0/planned breadcrumbs added: confirmed
