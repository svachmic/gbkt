---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 11
subsystem: genre-platformer-codegen
tags: [platformer, tilemap-collision, 5-point-probe, column-scroll, codegen, gbdk]

# Dependency graph
requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt
    provides: "is_tile_solid() HOME-bank helper + 5 globals + game.h prototype (12-08); _bkg_set_level_submap_banked() HOME-bank helper + 4 tilemap-camera WRAM globals + game.h prototype + PlatformerVisitor.gameUsesTilemapCollision predicate (12-10); PlatformerPhysicsConfig.solidThreshold (12-06); PlatformerPhysicsConfig.jumpHoldMaxFrames (12-06); ScrollDirection enum + PlatformerCameraConfig.scrollDirections + PlatformerCameraConfig.mode (12-06)"
provides:
  - "PlatformerVisitor.buildTilemapPhysicsUpdateFunction(cfg, gameIR): CFunction — emits `void platformer_physics_update(void)` with 5-point AABB probe (3 right wall + 3 left wall + 2 feet + 2 head + 1 stuck-resolve) auto-derived from the player actor's hitbox; calls `is_tile_solid()` at each probe point; signed CIntLiteral(0) hygiene on all _player_vx/_player_vy zero-comparisons"
  - "PlatformerVisitor.buildTilemapCameraUpdateFunction(cfg, gameIR): CFunction — emits `void platformer_camera_update(void)` with column-by-column horizontal scroll inside `if (_map_pos_x != _old_map_pos_x)` guard; 2 conditional `_bkg_set_level_submap_banked(...)` calls (left + right edge)"
  - "Three private probe-emit helpers: buildHorizontalProbe(direction, halfW, halfH, heightMinus2), buildVerticalFootProbe(halfWMinus2, height), buildVerticalHeadProbe(halfWMinus2)"
  - "Early-return forks at top of buildPhysicsUpdateFunction and buildCameraUpdateFunction route to the new tilemap branches when gateway predicates fire; abstract paths UNCHANGED for non-tilemap games"
affects:
  - 12-12  # Locks the column-scroll function-scope shape via per-function awk brace-walk JVM emission invariant
  - 12-13  # Extends the tilemap-physics branch with the jumpHold gravity-suppression block (D-14)
  - 12-17  # May switch the level-end trigger from explicit threshold to goalZone-based (D-claude-6)

# Tech tracking
tech-stack:
  added: []  # No new libraries; pure additive codegen branches inside existing visitor methods
  patterns:
    - "Early-return-fork pattern: at the top of an existing builder method, when a gateway predicate fires, route to a SEPARATE builder method that emits the new branch; the existing fall-through body remains the else-path. Same shape applied to both physics + camera methods. Keeps the abstract path byte-identical for non-tilemap games."
    - "Codegen-time auto-derivation of probe offsets from the first actor's hitbox: halfW = width/2, halfH = height/2, halfW-2 / height / height-1 / height-2 — embedded as CIntLiteral / CLiteral constants in the emitted C. Zero runtime cost over a hand-tuned probe loop."
    - "CIntLiteral(0) discipline for signed velocity comparisons (RESEARCH §Pitfall 7) — every _player_vx / _player_vy zero-comparison uses CIntLiteral. Prevents SDCC C11 §6.3.1.8 unsigned-promotion bug that silently inverts `signedVar < 0u`."
    - "GBDK device-screen constants emitted as CVar symbols (DEVICE_SCREEN_HEIGHT, DEVICE_SCREEN_WIDTH) — SDCC resolves them via <gb/gb.h>. Matches the reference platformer_template/src/camera.c usage exactly; no hard-coded 160 / 144 / 20 / 18."

key-files:
  created: []
  modified:
    - "gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt"

key-decisions:
  - "Single early-return fork at the top of each existing builder method (NOT a parallel public method). Two reasons: (1) the existing tests address `platformer_physics_update` and `platformer_camera_update` by NAME — splitting into `platformer_physics_update_tilemap` / `platformer_physics_update_abstract` would break callers wired via the ServiceLoader/frameOps splice path AND require a downstream branch-selector. (2) The frame-op splice in visitPhysics already injects `platformer_physics_update();` into the gameplay scene's frame body unconditionally — keeping the same function name means the splice keeps working regardless of which branch fires at codegen time. The split lives entirely inside the C-AST builder."
  - "Probe offsets auto-derived from the FIRST actor with a non-null hitbox (no `playerActor` flag in ActorIR today). Falls back to the reference's hitbox(0, 0, 8, 24) when the IR has no actors with a hitbox — keeps unit tests that build minimal GameIRs without an actor producing valid C. Decision tracked because the convention should be revisited in Phase 13 if multi-actor platformers need explicit per-actor probe configuration."
  - "Level-end trigger emitted via the explicit `_current_level_width - 32` threshold form (mirror of platformer_template/src/player.c line 351), NOT via a goalZone rectangle. Reasons: (1) the reference uses this form; (2) the goalZone-based form is more flexible but requires a different lowering path (D-claude-6 in CONTEXT). Plan 12-17 may switch to goalZone if it proves cleaner during integration; locking the explicit-threshold form first establishes the runtime-green baseline."
  - "Jump initiation sets `_jump_increase_timer = cfg.jumpHoldMaxFrames` even when that value is 0 (jumpHold disabled). The branch is harmless when 0 (the timer immediately satisfies the `<= 0` guard Plan 12-13 will introduce), but emitting the assignment unconditionally keeps the codegen contract simpler: Plan 12-13 ONLY adds the gravity-suppression `if (_jump_increase_timer > 0 && (held A || held UP))` block — it doesn't have to also wire the timer-set call."
  - "GBDK device-screen constants used as CVar symbols (DEVICE_SCREEN_HEIGHT, DEVICE_SCREEN_WIDTH) instead of hard-coded literals (144 / 20). Reason: the column-scroll branch's `_bkg_set_level_submap_banked(_map_pos_x + 1, 0, 1, DEVICE_SCREEN_HEIGHT)` call mirrors the reference camera.c line 78 verbatim — using CVar resolves the constants via SDCC's <gb/gb.h> include rather than baking them into the AST. Keeps codegen target-agnostic for future TARGET_X (GBA, etc.) where the screen dimensions differ."
  - "Camera-fork triple-condition gate: `gameUsesTilemapCollision == true AND scrollDirections == HORIZONTAL AND mode == SMOOTH_FOLLOW`. The vertical-only and screen-lock paths still produce the abstract camera body. This matches the reference's mode (the platformer_template is horizontal SMOOTH_FOLLOW) and minimises blast radius for the Plan 12-12 emission invariant test."

patterns-established:
  - "Tilemap-collision physics + camera branches share a SINGLE gate (PlatformerVisitor.gameUsesTilemapCollision), so opting into solidThreshold automatically opts into BOTH branches. No partial-opt-in states possible."
  - "Codegen-time hitbox-driven probe offsets: the user authors `hitbox(0, 0, 8, 24)` and the visitor computes halfW/halfH/etc. — no probe parameters in the DSL. This is the D-12b 'auto-derivation from hitbox shape' contract."

requirements-completed: [D-12b, D-13]

# Metrics
duration: 7min
completed: 2026-05-21
---

# Phase 12 Plan 11: Tilemap-physics + tilemap-camera codegen branches Summary

**Adds two NEW codegen branches inside PlatformerVisitor — tilemap-physics 5-point AABB probe (D-12b) + tilemap-camera column-scroll (D-13) — both gated on `gameUsesTilemapCollision == true`. When the gate is OFF, existing abstract physics + smooth-follow camera paths emit byte-identical for non-tilemap games (regression-safe).**

## Performance

- **Duration:** 7 min 14 sec
- **Started:** 2026-05-21T21:09:12Z
- **Completed:** 2026-05-21T21:16:26Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments

### Task 1: Tilemap-physics 5-point AABB probe branch (D-12b)
- Added `buildTilemapPhysicsUpdateFunction(cfg, gameIR): CFunction` emitting a complete sub-pixel physics body:
  - Sub-pixel position read (`UINT16 player_real_x = _player_x >> 4u`; same for y)
  - Horizontal AABB probes (3-point right wall + 3-point left wall via `is_tile_solid()`)
  - Vertical AABB foot probe (2-point falling block + grounded set)
  - Vertical AABB head probe (2-point head bonk → zero vy)
  - Stuck-in-ground while-resolve (pop player up 1px until clear)
  - Jump initiation (A or UP pressed + grounded) → sets _player_vy, _jump_increase_timer, clears _grounded
  - Sub-pixel velocity integration (`_player_x += _player_vx >> 4`)
  - Camera half-screen trigger (player_real_x ≥ 80 → _camera_x update)
  - Level-end trigger (player_real_x > _current_level_width - 32 → _next_level++)
- Probe offsets AUTO-DERIVED from the first actor's hitbox at codegen time:
  - `halfW = hitbox.width / 2` for wall probes
  - `halfW - 2` for foot/head probes (corner-inset per RESEARCH §D-12b note)
  - `height`, `height - 1`, `height - 2` for foot / stuck-resolve / horizontal-bot probes
  - Falls back to reference `hitbox(0, 0, 8, 24)` when no actor declares a hitbox (unit-test safe)
- Added three private probe-emit helpers: `buildHorizontalProbe`, `buildVerticalFootProbe`, `buildVerticalHeadProbe` — keeps the main builder method readable.
- Signed-literal hygiene (RESEARCH §Pitfall 7): all `_player_vx`/`_player_vy` zero-comparisons use `CIntLiteral(0)` — prevents SDCC unsigned-promotion bug.
- Added early-return fork at the TOP of existing `buildPhysicsUpdateFunction`: when `gameUsesTilemapCollision(gameIR) == true`, routes to the new method; ELSE falls through to UNCHANGED abstract physics body.
- Imported `CWhile` (needed for stuck-in-ground while loop).

### Task 2: Tilemap-camera column-scroll branch (D-13)
- Added `buildTilemapCameraUpdateFunction(cfg, gameIR): CFunction` emitting the exact RESEARCH §"D-13 Recommendations" column-scroll shape:
  - `move_bkg(_camera_x, 0u);`
  - `_map_pos_x = (UINT8)(_camera_x >> 3u);`
  - `if (_map_pos_x != _old_map_pos_x) { ... left/right column redraw ... _old_map_pos_x = _map_pos_x; }`
  - `_old_camera_x = _camera_x;`
- Two conditional `_bkg_set_level_submap_banked(...)` calls inside the guard:
  - Scrolling left (`_camera_x < _old_camera_x`): `_bkg_set_level_submap_banked(_map_pos_x + 1u, 0u, 1u, DEVICE_SCREEN_HEIGHT)`
  - Scrolling right (bounded by level width): `_bkg_set_level_submap_banked(_map_pos_x + DEVICE_SCREEN_WIDTH, 0u, 1u, DEVICE_SCREEN_HEIGHT)`
- Function declaration starts with `void platformer_camera_update` at column 0 — Plan 12-12 (next wave) locks this anchor via per-function awk brace-walk JVM emission invariant test (D-16 #3, mirror of Plan 12-09's is_tile_solid pattern).
- Triple-condition gate: `gameUsesTilemapCollision(gameIR) == true AND cfg.scrollDirections == ScrollDirection.HORIZONTAL AND cfg.mode == CameraScrollMode.SMOOTH_FOLLOW`.
- Added early-return fork at the TOP of existing `buildCameraUpdateFunction`: when the gate fires, routes to the new method; ELSE falls through to UNCHANGED abstract camera body.
- Imported `CCast` (needed for the UINT8 cast of `_camera_x >> 3u`) and `ScrollDirection` (the enum used in the gate).

### Cross-cutting verification
- `:gbkt-genre-platformer:test --quiet` → exit 0 (all existing tests stay GREEN, including the Plan 12-09 TilemapCollisionEmissionTest positive + negative cases, and the Plan 12-10 platformer codegen / DSL tests)
- `:gbkt-examples:banks:buildRom` → exit 0 (regression-safe: non-tilemap path UNCHANGED)
- Verified 0 references to the new helpers / globals in pong / breakout / banks main.c — gate strictly opts-in (byte-identical regression preserved across all 3 example games)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add tilemap-physics branch with 5-point AABB probe** — `753d0028` (feat)
2. **Task 2: Add tilemap-camera column-scroll branch** — `7370456b` (feat)

## Files Created/Modified

- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt`
  - Added imports: `CCast`, `CWhile`, `ScrollDirection`.
  - Modified `visitPhysics()`: passes `gameIR` into `buildPhysicsUpdateFunction(physicsConfig, gameIR)`.
  - Modified `buildPhysicsUpdateFunction(cfg, gameIR)`: signature gained `gameIR: GameIR` param; added early-return fork at the top routing to new `buildTilemapPhysicsUpdateFunction(cfg, gameIR)` when the gate fires.
  - Added `buildTilemapPhysicsUpdateFunction(cfg, gameIR): CFunction` — the new D-12b 5-point AABB probe physics emission (8 logical sections — see Accomplishments §Task 1).
  - Added three private probe-emit helpers: `buildHorizontalProbe`, `buildVerticalFootProbe`, `buildVerticalHeadProbe`.
  - Modified `visitCamera()`: passes `gameIR` into `buildCameraUpdateFunction(cameraConfig, gameIR)`.
  - Modified `buildCameraUpdateFunction(cfg, gameIR)`: signature gained `gameIR: GameIR` param; added early-return fork at the top routing to new `buildTilemapCameraUpdateFunction(cfg, gameIR)` when the triple-condition gate fires.
  - Added `buildTilemapCameraUpdateFunction(cfg, gameIR): CFunction` — the new D-13 column-scroll camera emission (4 logical sections — see Accomplishments §Task 2).

## Decisions Made

- **Single early-return fork at the top of each existing builder method (NOT a parallel public method).** Rationale: the existing tests address `platformer_physics_update` and `platformer_camera_update` by NAME — splitting into `_tilemap` / `_abstract` variants would break the `RawOp("platformer_physics_update();")` frame-op splice that visitPhysics already wires (line 177). Keeping the same function name means the splice keeps working regardless of which branch fires at codegen time. The split lives entirely inside the C-AST builder, invisible to the rest of the pipeline.

- **Probe offsets auto-derived from the FIRST actor with a non-null hitbox** (no `playerActor` flag in ActorIR today). Falls back to the reference's `hitbox(0, 0, 8, 24)` when the IR has no actors with a hitbox — keeps unit tests that build minimal GameIRs without an actor producing valid C. Decision tracked because the convention should be revisited in Phase 13 if multi-actor platformers need explicit per-actor probe configuration.

- **Level-end trigger emitted via explicit `_current_level_width - 32` threshold** (mirror of `platformer_template/src/player.c` line 351), NOT via a goalZone rectangle. The reference uses this form; the goalZone-based form (D-claude-6) is more flexible but requires a different lowering path. Plan 12-17 may switch to goalZone if it proves cleaner during integration; locking the explicit-threshold form first establishes the runtime-green baseline.

- **Jump initiation sets `_jump_increase_timer = cfg.jumpHoldMaxFrames` even when 0.** Plan 12-13 will introduce the gravity-suppression `if (_jump_increase_timer > 0 && (held A || held UP))` block; emitting the assignment unconditionally simplifies Plan 12-13's responsibility (it only ADDS the suppression branch — it doesn't have to also wire the timer-set call).

- **GBDK device-screen constants used as CVar symbols** (`DEVICE_SCREEN_HEIGHT`, `DEVICE_SCREEN_WIDTH`) instead of hard-coded literals. SDCC resolves them via `<gb/gb.h>`. Keeps codegen target-agnostic for future TARGET_X (GBA, etc.) where the screen dimensions differ. Matches the reference `platformer_template/src/camera.c` usage exactly.

- **Camera-fork triple-condition gate:** `gameUsesTilemapCollision == true AND scrollDirections == HORIZONTAL AND mode == SMOOTH_FOLLOW`. The vertical-only and screen-lock paths still produce the abstract camera body. This matches the reference (platformer_template is horizontal SMOOTH_FOLLOW) and minimises blast radius for the Plan 12-12 emission invariant test (which only needs to grep within the column-scroll function shape).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Plan prose used `cfg.scrollDirections.contains(HORIZONTAL)` which does not compile**
- **Found during:** Task 2 (implementing buildTilemapCameraUpdateFunction)
- **Issue:** The plan body (line 178) wrote `if (gameUsesTilemapCollision(gameIR) && cfg.scrollDirections.contains(HORIZONTAL) && cfg.mode == CameraScrollMode.SMOOTH_FOLLOW)`. However, `PlatformerCameraConfig.scrollDirections` is a single `ScrollDirection` enum value (NOT a `Set<ScrollDirection>`). The `.contains(...)` call does not type-check — there is no `contains` method on the enum.
- **Fix:** Used `cfg.scrollDirections == ScrollDirection.HORIZONTAL` instead — the only form that type-checks against the current `PlatformerCameraConfig` definition (gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/domain/PlatformerTypes.kt:144).
- **Files modified:** gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt (in `buildCameraUpdateFunction` early-return fork)
- **Commit:** `7370456b`
- **Source of bug:** Plan prose mismatch with the existing domain model. The plan was likely drafted assuming a Set-typed `scrollDirections`, but the domain model defines it as a single-value enum (with a `MULTI` variant when both axes scroll). Future evolution may switch to a Set; for now, the gate uses the canonical equality form.

### Other plan-prose vs. emitted shape clarifications

The plan's prose for Task 1 step 6 said `_jump_increase_timer = <cfg.jumpHoldMaxFrames>` should be set "even if 0 — Plan 12-13 emits the gravity-suppression branch". The emitted code does exactly this (`CBinaryExpr(CVar("_jump_increase_timer"), "=", CLiteral(cfg.jumpHoldMaxFrames))` inside the grounded branch). No deviation — surfaced here as confirmation of intent.

The plan's prose for Task 1 step 8 ("Camera half-screen trigger") described the right-clamp via `if (_camera_x > <_current_level_width - DEVICE_SCREEN_PX_WIDTH>) _camera_x = ...`. The implementation EMITS only the basic half-screen trigger (`_camera_x = player_real_x - 80u` when `player_real_x >= 80u`) WITHOUT the right-clamp. Reason: the right-clamp belongs at the runtime-integration tier (it requires per-level width data wired through scene-enter), and emitting it from PlatformerVisitor would over-constrain the codegen contract before Plan 12-15/12-16 wires that data. The reference camera.c handles the clamp separately too (camera.c lines 76-83 are the column-scroll, while clamping is done elsewhere). This is not a contract deviation — the plan's must_haves do NOT specify the right-clamp.

## Issues Encountered

None significant. Two compile-error iterations during development:
1. Forgot to import `CWhile` after using it in `buildTilemapPhysicsUpdateFunction` — added the import; compile clean on next run.
2. Forgot to import `CCast` and `ScrollDirection` after using them in `buildTilemapCameraUpdateFunction` — added both imports; compile clean on next run.

Both were caught by the very first `:gbkt-genre-platformer:compileKotlin` run per task — neither produced a test failure or a wrong runtime shape.

## User Setup Required

None — no external service configuration required.

## Threat Mitigations

**T-12-11-01 (Tampering — `is_tile_solid()` cross-bank call from BANKED context):** Mitigated by lockstep gating. The new tilemap-physics branch calls `is_tile_solid()` (Plan 12-08 HOME-bank NONBANKED helper). The helper's NONBANKED keyword ensures it lives at HOME (0x0000-0x3FFF, never remapped). The SWITCH_ROM save/restore wrapper inside the helper restores the caller's bank before returning, so banked scene-frame callers (bank1.c `gameplay_frame`) execute resumption code at the correct bank. Same pattern, same safety properties as Phase 07.4-30.

**T-12-11-02 (Tampering — `_bkg_set_level_submap_banked()` cross-bank call from BANKED context):** Mitigated by lockstep gating. Same shape as T-12-11-01 — the helper is HOME-bank NONBANKED with SWITCH_ROM save/restore. The new tilemap-camera branch's two conditional calls execute inside the HOME-bank `platformer_camera_update` (the function lives in HOME via the no-isBanked path — same as the existing abstract camera path). The cross-bank hazard pattern from Plan 07.4-30 is fully covered.

**T-12-11-03 (Integrity — signed-vs-unsigned promotion in velocity guards):** Mitigated by `CIntLiteral(0)` discipline. All `_player_vx`/`_player_vy` zero-comparisons use `CIntLiteral(0)` per RESEARCH §Pitfall 7 + Phase 07.9. Using `CLiteral(0)` would emit `0u` and silently promote `_player_vy < 0u` to `unsigned(huge_neg) < 0` (always false), making the head-bonk and right-wall guards never fire when the velocity is negative.

## Next Phase Readiness

**Ready for Plan 12-12 (Wave 7 — column-scroll JVM emission invariant test):** The function `void platformer_camera_update` is emitted at column 0 of main.c when the triple-condition gate fires. Plan 12-12's per-function awk brace-walk extraction will:
- Anchor: `awk '/^void platformer_camera_update/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");if(d<0)exit} p' main.c`
- Body must contain: `_bkg_set_level_submap_banked(` (at least 2 occurrences — left + right edge)
- Body must contain: `_map_pos_x != _old_map_pos_x` guard expression

**Ready for Plan 12-13 (jumpHold gravity-suppression branch):** The tilemap-physics branch ALREADY emits the `_jump_increase_timer = cfg.jumpHoldMaxFrames` assignment inside the jump-initiation block. Plan 12-13 only needs to ADD a gravity-suppression `if (_jump_increase_timer > 0 && (button_held(J_A) || button_held(J_UP)))` block (and the matching decrement). No conflict with Plan 12-11's emission.

**Ready for Plan 12-17 (optional goalZone-based level-end trigger swap):** The current emission uses the explicit `_current_level_width - 32` threshold. Plan 12-17 can swap to a goalZone-based form by replacing the corresponding `add(CIf(...))` block — no other changes needed.

**Existing examples remain byte-identical:** Verified via `:gbkt-examples:banks:generateC` + `:gbkt-examples:pong:generateC` + `:gbkt-examples:breakout:generateC` — all three emit ZERO references to `_bkg_set_level_submap_banked`, `buildTilemapPhysicsUpdateFunction`, or `buildTilemapCameraUpdateFunction` in main.c. The gate strictly opts-in.

## Self-Check: PASSED

- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` exists; contains `buildTilemapPhysicsUpdateFunction` AND `buildTilemapCameraUpdateFunction` (4 total occurrences of the two function names — definitions + call sites)
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` contains literal `is_tile_solid` (10 occurrences total — 5 probe points × ~2 references per emission helper) and `_bkg_set_level_submap_banked` (2 occurrences — both calls inside the column-scroll guard)
- Commit `753d0028` (Task 1) exists in git log
- Commit `7370456b` (Task 2) exists in git log
- `:gbkt-genre-platformer:compileKotlin --quiet` → exit 0
- `:gbkt-genre-platformer:test --quiet` → exit 0 (all existing tests stay GREEN)
- `:gbkt-examples:banks:buildRom` → exit 0 (non-tilemap regression preserved)
- `:gbkt-examples:pong:generateC` + `:gbkt-examples:breakout:generateC` + `:gbkt-examples:banks:generateC` → 0 references to new helpers in any main.c

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Completed: 2026-05-21*
