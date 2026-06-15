---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 24
subsystem: gbkt-backend-gbdk/codegen/visitor
tags: [sonar, s3776, cognitive-complexity, extract-method, hud-visitor, actor-visitor]
dependency_graph:
  requires: ["18-23"]
  provides: ["E-18-cleared", "E-21-cleared"]
  affects: ["gbkt-backend-gbdk"]
tech_stack:
  added: []
  patterns: ["value-returning extract-method", "per-element-type private helpers", "buildList DSL", "when-dispatch delegation"]
key_files:
  created: []
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/HudVisitor.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitor.kt
decisions:
  - "E-21 research naming error: RESEARCH.md attributed finding to generateAnimationDefines; actual target confirmed via git-history as generateMovementFunction (at line 437 in b3938a29)"
  - "HudNumber helper drops elemId param: prevVar already encodes elem identity; no caller needs raw elemId"
  - "Physics branch recreates xVar/yVar from actorId internally — cleaner than passing CVar across helper boundary"
metrics:
  duration: "~90 minutes (across two sessions)"
  completed: "2026-06-13"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 2
---

# Phase 18 Plan 24: S3776 Emitting — HudVisitor E-18 + ActorVisitor E-21 Summary

Extract-method refactors on two S3776 cognitive-complexity findings in C-emitting visitors. Each finding required value-returning private helpers with preserved emission order and a full 7-example byte-identity ROM sweep before committing.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Extract HudVisitor.buildHudUpdateFunction sub-builders (E-18) | `bc7c26fd` | HudVisitor.kt |
| 2 | Extract ActorVisitor.generateMovementFunction sub-builders (E-21) | `dd7503d8` | ActorVisitor.kt |

## Task 1 — E-18: HudVisitor.buildHudUpdateFunction (cc=22 → below threshold)

`buildHudUpdateFunction` had a 300+-line body with a `when (elem)` over three HUD element types. Each branch contained complex conditional C AST construction.

### Extracted helpers

- `buildHudBarUpdateStatement(elem, hudId, elemId, prevVar, elemX, baseY, tileFunc): CStatement` — bar fill/empty tile-set logic via `CTernary` + `CCall`
- `buildHudNumberUpdateStatement(elem, prevVar, elemX, baseY, printFunc): CStatement` — numeric label print with `_win_print_at`/`_bkg_print_at`; `elemId` not needed (prevVar already encodes identity)
- `buildHudIconsUpdateStatement(elem, hudId, elemId, prevVar, elemX, baseY, tileFunc): CStatement` — icon-slot loop building `CFor` + inner `CTernary` tile selection

`buildHudUpdateFunction` now uses `buildList<CStatement>` delegating to the three helpers. The `elemOffset` accumulation stays in the outer loop body (not passed into helpers) — Pitfall 1 preserved: each helper returns a `CStatement`, not a mutation of shared state.

Removed `@Suppress("LongMethod")` annotation (no longer needed after extraction).

## Task 2 — E-21: ActorVisitor.generateMovementFunction (cc≈19 → split across helpers)

### Research naming error

RESEARCH.md row E-21 named the function as `generateAnimationDefines` at line 437, cc=21. Investigation revealed:

1. Current `generateAnimationDefines` is cc≈1 (trivially simple — a `map` call).
2. `git show b3938a29:...ActorVisitor.kt | grep -n "fun generate"` confirmed that line 437 in the Sonar-scanned commit (`b3938a29`) is `generateMovementFunction`, not `generateAnimationDefines`.
3. `generateAnimationDefines` in that commit is at line 394 — cc≈1.

The finding was misattributed in the research table. The actual E-21 target is `generateMovementFunction`. Documented as Rule 1 deviation (bug in research artifact).

### Extracted helpers

- `buildGridMovementStatements(xVar, yVar, speed): List<CStatement>` — uses `buildList`, pure data construction (cc≈0)
- `buildSmoothMovementStatements(actorId, smoothConfig, xVar, yVar, speed): List<CStatement>` — one `if` branch over waypoint/patrol; cc≈1
- `buildPhysicsMovementStatements(actorId, actor): List<CStatement>` — full physics body (velocity clamp, gravity, position update); cc≈12, within threshold. Recreates `xVar`/`yVar` from `actorId` internally.

`generateMovementFunction` now dispatches via `when (config.style)` over the three movement styles, each branch calling its helper. Emission order is identical to the original.

## Byte-Identity ROM Sweep Results

Baseline captured before any changes. Sweep run after each commit.

### After Task 1 commit (`bc7c26fd`)

| ROM | Baseline SHA-256 | Post-refactor | Result |
|-----|-----------------|---------------|--------|
| pong.gb | (non-deterministic) | n/a | PASS* (main.c identical) |
| breakout.gb | `564465cd...` | `564465cd...` | PASS |
| simple-physics.gb | `247e16d2...` | `247e16d2...` | PASS |
| metasprites.gb | `9b2440db...` | `9b2440db...` | PASS |
| metasprites-stress.gb | `bc51eadd...` | `bc51eadd...` | PASS |
| banks.gb | `12c8ee2e...` | `12c8ee2e...` | PASS |
| platformer-template.gb | `9a8f268a...` | `9a8f268a...` | PASS |

### After Task 2 commit (`dd7503d8`)

| ROM | Baseline SHA-256 | Post-refactor | Result |
|-----|-----------------|---------------|--------|
| pong.gb | (non-deterministic) | n/a | PASS* (main.c `b5e81de7...` = baseline) |
| breakout.gb | `564465cd...` | `564465cd...` | PASS |
| simple-physics.gb | `247e16d2...` | `247e16d2...` | PASS |
| metasprites.gb | `9b2440db...` | `9b2440db...` | PASS |
| metasprites-stress.gb | `bc51eadd...` | `bc51eadd...` | PASS |
| banks.gb | `12c8ee2e...` | `12c8ee2e...` | PASS |
| platformer-template.gb | `9a8f268a...` | `9a8f268a...` | PASS |

Both sweeps: 6/6 byte-identical, pong PASS*.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug in research artifact] E-21 misattributed to generateAnimationDefines**

- **Found during:** Task 2 investigation (pre-refactor read)
- **Issue:** RESEARCH.md row E-21 names `generateAnimationDefines` at line 437 with cc=21. Actual code at that line in b3938a29 is `generateMovementFunction`. `generateAnimationDefines` is at line 394 with cc≈1.
- **Fix:** Refactored `generateMovementFunction` instead of `generateAnimationDefines`. The Sonar finding is still cleared — the correct high-cc function has been decomposed.
- **Files modified:** ActorVisitor.kt only (no RESEARCH.md correction needed — it is a planning artifact, not tracked code)
- **Commits:** `dd7503d8`

## Known Stubs

None. Both visitors are pure logic — no placeholder values or hardcoded empty returns introduced.

## Threat Flags

None. No new network endpoints, auth paths, or trust boundaries introduced. Changes are internal C AST builder logic only.

## Self-Check: PASSED

- [x] HudVisitor.kt exists and contains `buildHudBarUpdateStatement`, `buildHudNumberUpdateStatement`, `buildHudIconsUpdateStatement`
- [x] ActorVisitor.kt exists and contains `buildGridMovementStatements`, `buildSmoothMovementStatements`, `buildPhysicsMovementStatements`
- [x] Commit `bc7c26fd` in git log (E-18)
- [x] Commit `dd7503d8` in git log (E-21)
- [x] 7-example ROM sweep green for both tasks
- [x] No NOSONAR added
