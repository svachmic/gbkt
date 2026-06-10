---
game: racer
status: failed
tester: claude-agent
session_date: 2026-05-12
prior_session_date: 2026-05-09
rom: gbkt-examples/racer/build/gbkt/output/racer.gb
metadata: gbkt-examples/racer/build/gbkt/generated/game_metadata.json
symbols: gbkt-examples/racer/build/gbkt/output/racer.noi
target: GBC_COMPATIBLE
verdict_reason: "phase-07.4-round-7-partial — GAP-RACE-BLANK-AFTER-START frame-124 LCD-disable hang FIXED via Plan 30 (H-LCD-DISABLE + HOME-bank helper); manual UAT 2026-05-12 surfaces two new runtime gaps: GAP-CAMERA-NO-FOLLOW + GAP-TRACK-NOT-RENDERED-AS-CIRCUIT; UAT stays failed until those close"
fix_phase_required: true
phase_07_4_status: gaps_found
re_verification:
  round: 7
  previous_status: failed
  previous_score: "round-3 'navigability corner trap'; round-4 'blank screen after start'"
  gaps_closed_round_7: [GAP-RACE-BLANK-AFTER-START]
  gaps_remaining_round_7: [GAP-CAMERA-NO-FOLLOW, GAP-TRACK-NOT-RENDERED-AS-CIRCUIT]
  user_runtime_uat: "2026-05-12 — user compiled and ran ROM, screenshot attached to session; car visible + moves + LCD on, but camera stays at origin and BG renders as static square arena with corner cutouts + checkered fill, NOT the waypoint-driven road corridor"
  evidence_dir: .planning/phases/07.4-sport-genre-codegen-fix-inserted/evidence/round-7-lcd-disable/
  authoritative_runtime_artifact: .planning/phases/07.4-sport-genre-codegen-fix-inserted/evidence/round-7-lcd-disable/USER-RUNTIME-UAT-2026-05-12.md
---

# UAT Report: Racer

## Result: PARTIAL PASS — runtime gaps block UAT flip

Phase 07.4 lifted Racer from "decorative DSL" (2026-05-07 baseline) to "engine drives stats and camera, but cannot render the world". JVM-tier tests (SportVisitor codegen contracts, RacingValidationPass diagnostics, TrackSynthesizer rasterizer, RacerEmulatorTest, RacerIRTest) are GREEN; the ROM behavior reveals that stats-driven physics and camera scrolling work, but the AI pool never activates and the synthesized track never reaches VRAM. UAT cannot flip to `passed` until those two gaps close.

This re-run was driven by the `/gsd-execute-phase 07.4` MCP play-through (Plan 07.4-08, Tier 3 of VALIDATION.md). The next action is `/gsd-plan-phase 07.4 --gaps` followed by `/gsd-execute-phase 07.4 --gaps-only` to close the runtime delta.

## What's WORKING (PASS — confirmed runtime, 2026-05-08)

| SC | Behavior | Runtime evidence |
|----|----------|------------------|
| SC-2 | Stats drive movement (vehicle.stats → speed_cur ramp) | `_vehicle_carPlayer_speed_cur` ramps 0 → 200 within 30 frames of `dpad.up` held; decays under steer |
| SC-3 | Camera scrolls under throttle | `_camera_x` advanced 0 → 248 across 30 frames of `dpad.up`; updates with player direction |
| D-17 | Visited bitmap stays clean | `_racing_visited_track1` stays 0 across the whole session — no false advancement from input noise |
| Heading | Steering rotates cardinal heading | `_vehicle_carPlayer_heading` 0 → 1 under `dpad.right`; matches playbook 0=N, 1=E, 2=S, 3=W |
| Title screen | DMG fallback render | "RACER", "TOP-DOWN RACING", "PRESS START", "3 LAPS TO WIN" all visible in BG text layer |
| Title → Race transition | Scene transition fires | START press at frame 126 → race scene by frame 129 |

## What's FAILING (real runtime gaps — fix in 07.4 gap-closure pass)

### Gap A — AI pool slot never activates (SC-1)
| Variable | Expected | Actual |
|----------|----------|--------|
| `_pool_carAi_active` | 1 (slot alive throughout race) | **0 (always)** |
| `_pool_carAi_oam` | 4 (allocated by codegen) | 4 (allocation works; activation does not) |
| `_pool_carAi_speed_cur` | non-zero, ramps under AI tick | **0 (always)** |
| `_pool_carAi_wp_idx` | advances 0 → 1 → 2 → 3 → 0 | **0 (always)** |
| Rival sprite | renders on screen | not visible (spriteCount=0 even when on-screen position) |

**Hypothesis:** `SportVisitor` (Plan 07.4-05) emits the pool init for `_pool_carAi_*` variables and OAM allocation but does NOT emit a `_pool_carAi_active[0] = 1` assignment in the race-scene `enter { }` callback. `racing_tick_track1()` may also be skipping the AI inner loop because `_pool_carAi_active[0] == 0`.

### Gap B — Synthesized track tilemap never reaches VRAM (SC-4)
| Variable | Expected | Actual |
|----------|----------|--------|
| `_current_tileset_id` | non-255 (race tileset loaded) | **255 (no tileset loaded)** |
| BG layer at race scene | track corridor visible (TILE_DRIVABLE / TILE_GRASS / TILE_WALL pattern) | **uniform pale green; only "LAP:" HUD text** |
| Camera scroll behavior | window scrolls over visible track tiles | scroll registers update but no tiles to reveal |

**Hypothesis:** `TrackSynthesizer.synthesize(...)` (Plan 07.4-04) is wired into `gameIR.zones[id].tileData` (per Plan 07.4-03 SUMMARY) but `SportVisitor` (Plan 07.4-05) is NOT emitting the zone-load call (`zone_load_tilemap_circuit()` or equivalent) inside the race-scene `enter { }` callback. The synthesized data exists in ROM but is never copied to VRAM.

### Gap C — Player car position underflows UINT8 (visual issue, not in original SCs)
| Variable | Behavior | Result |
|----------|----------|--------|
| `car_y` | UP held 30 frames at speed_cur=200 → delta = 200 >> 5 = 6 px/frame, expected y → 80 - 180 = (clamped to 0) | **car_y = 213 (UINT8 underflow: 80 - 123 mod 256)** |
| Player sprite | should remain visible while inside track corridor | **off-screen (spriteCount=0)** because car_y wrapped past 144-row visible region |

**Hypothesis:** The `racing_tick` per-frame position write-back (`car_x += speed_cur >> 5` in heading direction) does not clamp against the world bounds. With heading=0 (N) the y-delta should saturate at 0; with heading=2 (S) at 255. Independent of Gap B; would still be visible if the track were rendered.

### Gap D — `_camera_target` reads 255 (suspect)
`_camera_target` = 255 throughout the race scene. The CameraSystem expects an actor index. 255 is likely the "no target" sentinel. Either the camera follow target was wired to the rival actor (which is hidden behind the AI pool) or the binding from `vehicle { actor(car) }` to `camera.follow(...)` is not threading through. The fact that `_camera_x` does advance suggests the system runs but on garbage input.

## Variable Assertions (this session, frame 219, race scene, after `dpad.up` 30f then `dpad.right` 60f)

| Variable | Type | Expected | Actual | Status |
|----------|------|----------|--------|--------|
| current_scene (race) | UINT8 | 1 | 1 | PASS |
| _vehicle_carPlayer_speed_cur (mid-race) | UINT8 | non-zero | 140 | PASS |
| _vehicle_carPlayer_heading (after right) | UINT8 | 1 (E) | 1 | PASS |
| _camera_x (mid-race) | UINT8 | non-zero, advancing | 248 | PASS |
| **_pool_carAi_active[0] (race)** | UINT8 | **1** | **0** | **FAIL** |
| **_pool_carAi_speed_cur[0] (race)** | UINT8 | non-zero | **0** | **FAIL** |
| **_pool_carAi_wp_idx[0] (race)** | UINT8 | advances 0→3 | **0** | **FAIL** |
| **_current_tileset_id (race)** | UINT8 | non-255 | **255** | **FAIL** |
| **_camera_target (race)** | UINT8 | actor idx (low number) | **255** | **FAIL** |
| **car spriteCount (mid-race)** | int | 2 | **0** | **FAIL (off-screen)** |
| **rival spriteCount (mid-race)** | int | 2 | **0** | **FAIL (pool inactive)** |
| _racing_lap_count_track1 (mid-race) | UINT8 | 0 (no lap completed yet) | 0 | PASS |
| _racing_visited_track1 (mid-race) | UINT8 | 0 (CP 0 not touched yet) | 0 | PASS |

## Goldens (regenerated 2026-05-08 against the current ROM)

| Label | Path | Frame | Captured behavior |
|-------|------|-------|-------------------|
| racer-title | `gbkt-examples/racer/src/test/resources/golden/racer-title.png` | 126 | Title screen, four BG text rows visible (RACER / TOP-DOWN RACING / PRESS START / 3 LAPS TO WIN) |
| racer-race | `gbkt-examples/racer/src/test/resources/golden/racer-race.png` | 159 | Race scene after 30 frames of `dpad.up` — pale-green BG with "LAP:" HUD only; no track tiles, no AI sprite, no player sprite (off-screen via underflow) |
| racer-results | unchanged from 2026-05-07 | n/a | Race never reached — 3 laps unattainable until track + checkpoints render |

The race golden is intentionally a FAIL snapshot — it documents the gap honestly. It will be replaced with a track-visible capture after the gap-closure pass.

## Bugs Found and Fixed in prior sessions (still in place, not regressed)

These are real fixes the prior tester applied to the demo's own logic — they remain valid and stable:

### 1. Car starts inside lap detection zone (fixed 2026-03-25)
Car initial position `(40, 100)` was inside the lap zone, so race completed in 3 frames. Changed to `position(40, 80)` and `car.moveTo(40, 80)`. Re-verified this session: car starts at (80, 80).

### 2. Lap counter has no debounce (fixed 2026-03-25)
Lap incremented every frame while in zone. Added `inLapZone` debounce. The 07.4 engine replaces this with the in-order checkpoint bitmap (D-15/D-17), which subsumes the debounce — bitmap state machine is the new debounce.

## Outstanding Findings (for the 07.4 gap-closure pass)

### Gap A — `_pool_carAi_active` never set to 1
Pool slot is allocated (`_pool_carAi_oam = 4`) but never marked active. Likely fix: `SportVisitor` race-scene-enter callback must emit `_pool_carAi_active[0] = 1` (and the AI inner loop in `racing_tick` must not gate on `active != 0` if it's never set).

### Gap B — Race-scene-enter does not load synthesized track tileset / tilemap
`_current_tileset_id` stays 255 throughout race. Likely fix: `SportVisitor` must emit `zone_load_tileset_<id>()` and `zone_load_tilemap_<id>()` calls in the race-scene `enter { }` callback. The synthesized tile data exists in ROM (verified by Plan 07.4-04 GREEN); the load just isn't wired.

### Gap C — `racing_tick` position write-back does not clamp against world bounds
Player car y wraps via UINT8 underflow when held UP for 30+ frames. Likely fix: `SportVisitor.emitRacingTick` must clamp the delta against `[0, world_height_px - sprite_h]` (or against the synthesized track corridor extents). Same fix needed for x.

### Gap D — `_camera_target` not bound to the player vehicle's actor
Camera scrolls but on what looks like a sentinel target. The auto-emitted `CameraSystem` from `racing { }` (per D-10) must set `_camera_target = <car actor index>` at race-scene enter. Verify in the generated C — `grep -A5 'camera_target' build/gbkt/generated/bank1.c`.

### Gap E (latent — not yet observed) — Lap detection in-order check unverified
Cannot test SC-5 until the track renders (Gap B). Once tiles are visible and the player can navigate, drive through CPs in declaration order and confirm `_racing_visited_track1` bits flip in order; then drive through them OUT of order and confirm the bitmap rejects the lap.

### Gap F (latent) — 3-lap race-end transition unverified
SC-6 blocked on Gap E. Once a single lap completes, drive 2 more and confirm the race → results transition.

## Round-2 Re-verification (2026-05-09 — after Plans 09-13 + 15-17 landed)

A second MCP play-through against a freshly built ROM confirms that the four real gaps from round 1 (A-D) ARE CLOSED at runtime. The 3-tile builtin tileset now renders with high-contrast patterns (Plan 17), the AI pool spawns and follows waypoints (Plan 11), wall-collision rejects out-of-bounds player moves (Plan 12), and the camera target binds to the player actor (Plan 11).

### Gaps closed in round 2 (real-runtime evidence)

| Gap | Round-1 actual | Round-2 actual (this session) | Closure plan |
|-----|---------------|-------------------------------|--------------|
| GAP-A — `_pool_carAi_active` | always 0 | **`1` from race-enter onward** | Plan 11 enterOps splice |
| GAP-B — `_current_tileset_id` | always 255 | **`1` immediately at race entry** | Plan 11 enterOps splice |
| GAP-C — `_car_y` UINT8 underflow | wrapped to `213` after 30f UP | **stays in `[2..80]` across same input** — wall-collision rejects out-of-bounds | Plan 12 INT16 wall-collision write-back |
| GAP-D — `_camera_target` | always 255 (sentinel) | **`0` (player actor index) immediately at race entry** | Plan 11 enterOps camera-target splice |
| SC-1 — AI rival visible + waypoint-following | not visible | **2 rival sprites at OAM 4-5; `pool_carAi_wp_idx` cycles 0→1→2→3→1 (full lap)**; `pool_carAi_speed_cur` ramps to 252 (rubber-banding active above player cap) | Plans 11 + 16 |
| SC-2 — stats drive movement (regression) | passed | **passed (regression-stable)** — `_vehicle_carPlayer_speed_cur` ramps to 200 = `speedCap` | (regression — no plan) |
| SC-3 — camera follows (regression) | passed | **passed (regression-stable)** — `_camera_target = 0`, `_camera_x` and `_camera_y` advance with player position | (regression — no plan) |
| SC-4 — track tilemap visible | pale-green uniform (FAIL baseline) | **track corridor visible with high-contrast WALL/DRIVABLE/GRASS patterns** (verified visually in `screenshots/racer-race-mid_frame108.png` against the 2026-05-08 FAIL `golden/racer-race.png`); `_current_tileset_id = 1` (was 255) | Plans 11 + 17 |

### NEW gap surfaced in round 2 — NAVIGABILITY-PLAYER-CORNER-TRAP

The cardinal-only steering (`dpad.up/down/left/right`) reliably traps the player car in corners of the synthesized corridor where the wall-collision sample point on the proposed-tile axis is wall but the OTHER axis is also wall. Specific reproduction:

- **Spawn at** `(80, 80)` (tile (10, 10), inside the polygon raster)
- **Held UP for 30f:** player reaches `(80, 5)` — moved 75 px (✓ stats-driven, no underflow). speed_cur=200 = speedCap. PASS for SC-2 + GAP-C.
- **Then held LEFT for 30f:** player reaches `(2, 5)` — moved 78 px. PASS for traversability of one corridor edge.
- **Then held DOWN, RIGHT, RIGHT+DOWN, UP+RIGHT, RIGHT alone — ALL rejected.** Player frozen at `(2, 5)`. `vehicle_carPlayer_speed_cur` drops to 0. Wall-collision sample on EVERY proposed-axis tile says wall; player has no exit move with cardinal steering.

**Why this matters:** SC-5 ("Lap detection uses waypoint-based circuit completion") and SC-6 ("Complete 3-lap race against visible AI on rendered track") cannot be runtime-confirmed by manual MCP traversal because the player cannot reach checkpoint 1 at pixel `(40, 40)` from the spawn position via cardinal steering. The lap-detection MECHANISM is locked GREEN by Plan 16's `RacingTrackNavigabilityTest > ai simulation drives full lap within budget` (JVM tier — proven to traverse all 4 waypoints + complete a lap with the same 3-level wall-aware AI heading), but no runtime player-tier evidence is captured.

**This is NOT the same as TRACK-NAVIGABILITY** (closed in round 2). TRACK-NAVIGABILITY was about the AI's ability to advance waypoints; that is verified at runtime (`pool_carAi_wp_idx 0→1→2→3→1`, full cycle). The new gap is about the PLAYER's corridor traversability with cardinal-only input, given a corridor topology produced by polygon-raster + perimeter-walls + 3x3-waypoint-force from Plan 16.

### Likely fix space (route to round-3 closure pass)

The corner-trap arises because the perimeter-wall pass in TrackSynthesizer creates 1-tile-wide drivable strips along some corridor edges that admit entry but reject all exit moves. Two non-mutually-exclusive remedies:

1. **Widen the corridor.** The waypoint-3x3 force-pass already widens the immediate waypoint neighborhood; extend the same widening along the polygon-edge segments BETWEEN waypoints (currently only 1-tile-wide drivable strips along the polygon edge). A `corridorWidth(tiles = 2)` knob on the `track { }` block (with sensible default) is the natural surface.
2. **Soften the wall-collision sampling.** The current INT16 sample is at `(x + sprite_w/2, y + sprite_h/2)` of the PROPOSED position. In a 1-tile-wide corridor strip, ANY proposed move into the perpendicular axis crosses into a wall-tile sample. A corner-aware fallback that samples the 4 sprite corners and accepts the move if at least 1 corner is drivable would let the player thread narrow corridors without the ai-style "stuck-at-corner" failure.

Either remedy preserves D-17 (cannot drive across walls) and D-09 (uniform physics path) while restoring SC-5/SC-6's manual-traversal feasibility.

## Sign-off (round 2)

**Racer STILL FAILS UAT — but with markedly narrower scope.** Round 1's four real gaps (A-D) are closed and verified at runtime. The single remaining gap (`NAVIGABILITY-PLAYER-CORNER-TRAP`) is scoped to corridor-topology + collision-sample widening; both candidate fixes are local to `TrackSynthesizer.kt` + `SportVisitor.buildPositionWriteBackWithCollision`. SC-5 and SC-6's MECHANISMS are JVM-test-locked; only the player-tier MCP confirmation is missing, gated by the corner-trap.

**Recommended next step:** `/gsd-plan-phase 07.4 --gaps` → `/gsd-execute-phase 07.4 --gaps-only`. Estimated scope: 1-2 small plans focused on the corridor-widening or corner-aware collision sampling.
