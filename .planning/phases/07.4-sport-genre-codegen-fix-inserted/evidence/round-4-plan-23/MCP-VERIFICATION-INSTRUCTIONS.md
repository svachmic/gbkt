# MCP Verification Instructions for Plan 07.4-23

## Context

Plan 07.4-23 is a **checkpoint:human-verify gate=blocking** plan. The executor (parallel agent in
worktree `agent-ab6c75aac5df6a242`) cannot drive the MCP play-through because:

1. The `gbkt-mcp-server` shadow JAR is not built in this worktree
   (`gbkt-mcp-server/build/libs/` does not exist — needs `./gradlew :gbkt-mcp-server:shadowJar`).
2. There is no `.claude/mcp_servers.json` registered in this worktree.
3. `mcp__gbkt-emulator__*` tool family is not exposed to the executor agent.

This file documents the exact MCP play-through script the orchestrator (or a human driver in a
session WITH the MCP server configured) must execute against the rebuilt ROM at:

`gbkt-examples/racer/build/gbkt/output/racer.gb` (timestamp 2026-05-11 09:39, 65,536 bytes, MBC5)

## Pre-flight

```bash
# 1. Build the MCP server shadow JAR (only needed once per worktree/branch)
./gradlew :gbkt-mcp-server:shadowJar

# 2. Configure .claude/mcp_servers.json (or use the existing user-level config)
#    Entry shape (see CLAUDE.md "MCP server setup"):
#    {
#      "gbkt-emulator": {
#        "type": "stdio",
#        "command": "java",
#        "args": ["-jar", "gbkt-mcp-server/build/libs/gbkt-mcp-server-all.jar"]
#      }
#    }

# 3. Confirm ROM freshness
ls -la gbkt-examples/racer/build/gbkt/output/racer.gb
# Expected: 65536 bytes, mtime 2026-05-11 09:39 (or later if rebuilt)
```

## Play-through Script (per Plan 07.4-23 Task 2)

Save all PNG screenshots to `.planning/phases/07.4-sport-genre-codegen-fix-inserted/evidence/round-4-plan-23/`.

### Step 1 — Boot + Title (precondition)

```
emulator_start(rom = "gbkt-examples/racer/build/gbkt/output/racer.gb")
emulator_wait_for_scene("title")
emulator_step(frames = 30)
emulator_screenshot(path = "evidence/round-4-plan-23/00-title.png")
emulator_press("start")
emulator_wait_for_scene("race")
emulator_step(frames = 30)   # let race_enter settle
```

### Step 2 — SC-4 (track visible) + SC-1 (cars visible) — PRIMARY

```
emulator_screenshot(path = "evidence/round-4-plan-23/01-race-entry.png")
emulator_assert(_current_tileset_id == 1)         # NOT 255
emulator_assert(_pool_carAi_active == 1)
emulator_read_variable("_camera_target")           # expect 0
emulator_read_variable("_pool_carAi_x")
emulator_read_variable("_pool_carAi_y")
```

**Visual check (human):** screenshot 01-race-entry.png MUST show:
- (a) Track corridor visible on BG (NOT uniform pale-green/dark) — this is the SC-4 closure proof.
- (b) Player car visible at approximately (80, 80) and rival car at approximately (80, 96).
- (c) "LAP:" text in top-left, rendered cleanly via window layer (no BG corruption).

### Step 3 — SC-2 (stats drive movement) — regression-stable from round 2

```
# Hold UP for 60 frames (engine ramps speed_cur)
for f in 1..60: emulator_press_held("up")  # or equivalent batch
emulator_screenshot(path = "evidence/round-4-plan-23/02-after-hold-up.png")
emulator_read_variable("_vehicle_carPlayer_speed_cur")  # expect non-zero, ramping toward 200
emulator_read_variable("_car_y")  # expect lower than starting 80 (moved north)
```

**Visual check (human):** 02-after-hold-up.png shows the car has visibly moved upward; track
corridor still visible (camera scrolled with the player).

### Step 4 — SC-3 (camera follows) — regression-stable from round 2

```
emulator_screenshot(path = "evidence/round-4-plan-23/03-camera-mid-zone.png")
emulator_read_variable("_camera_x")
emulator_read_variable("_camera_y")
```

**Visual check:** 03-camera-mid-zone.png shows the BG has visibly scrolled relative to
01-race-entry.png; no SCX/SCY wraparound (no garbled tiles at screen edge — Plan 21 closure).

### Step 5 — SC-1 (AI motion visible)

```
# Continue stepping; AI rival should advance via racing_tick AI inner loop
emulator_step(frames = 60)
emulator_screenshot(path = "evidence/round-4-plan-23/04-ai-moved.png")
emulator_read_variable("_pool_carAi_wp_idx")  # expect changed from initial value
emulator_read_variable("_pool_carAi_speed_cur")
emulator_read_variable("_pool_carAi_x")
```

**Visual check:** AI car position differs visibly from 01-race-entry.png; track frame of
reference makes the motion clearly visible.

### Step 6 — SC-5 (player lap detection) — JVM-locked since Plan 18

Drive a full lap using cardinal dpad. Per Racer.kt, only CP-0 (5,5) and CP-2 (15,15) are
checkpoints (CP-1 and CP-3 are non-checkpoint waypoints).

```
# Drive START (10,10 area) → CP-2 (15,15) → back to START (CP-0 at 5,5)
# Adjust based on actual spawn position; the bitmap must hit all bits.
emulator_press_held("right", frames = N)
emulator_press_held("down", frames = N)
emulator_read_variable("_racing_visited_track1")  # bit 0 (CP-0) starts set; bit 2 (CP-2) flips after CP-2 visit
emulator_press_held("left", frames = N)
emulator_press_held("up", frames = N)
emulator_read_variable("_racing_lap_count_track1")  # expect 1 after re-crossing CP-0 with bitmap full
emulator_screenshot(path = "evidence/round-4-plan-23/05-lap-1-complete.png")
```

### Step 7 — SC-6 (3-lap race + results)

```
# Drive 2 more laps
# ... repeat the loop pattern from Step 6 twice more ...
emulator_read_variable("_racing_lap_count_track1")  # expect 3
emulator_wait_for_scene("results")
emulator_screenshot(path = "evidence/round-4-plan-23/06-results-scene.png")
```

**Visual check:** results scene shows "RACE COMPLETE" or equivalent text.

## Resume Signal Rules (W-2 closure)

After capturing all 6+ screenshots and variable readings, return ONE of these signals to the
orchestrator:

- **"approved"** — all 6 SCs pass with visual evidence → Task 3 fires (UAT flip + sentinel).
- **"approved with caveats: SC-2 fails ..."** OR **"approved with caveats: SC-3 fails ..."** —
  permitted ONLY for SC-2/SC-3 (regression-stable from round 2).
- **"rejected: SC-X fails ..."** — required if ANY of SC-1/SC-4/SC-5/SC-6 fails. UAT MUST NOT
  be flipped. Surface findings to orchestrator for re-planning.

**FORBIDDEN:** "approved with caveats: SC-1/SC-4/SC-5/SC-6 fails" — those four are the primary
gap; partial-flipping any one of them is exactly the Plan 14 verification methodology bug.

## Pre-Verified Evidence (from this executor)

The executor agent verified at the JVM/codegen tier:

1. ROM rebuilt at HEAD post-Plans-20+21+22 — `racer.gb` 65536 bytes, mtime 2026-05-11 09:39.
2. **Scope-level race_enter grep gate PASSED** (W-1 closure):
   - `cls=0`, `gotoxy=0`, `printf=0`
   - `_win_print_at >= 1`, `_win_clear_region >= 1`, `HIDE_SPRITES >= 1`
   - `SWITCH_ROM(2) >= 1` (Plan 22 cross-bank guard present)
3. Generated `update_camera_camera` uses `(rawX > 0u) ? 0u : rawX` (Plan 21 fix; no `> -8` literal).
4. Race entry sequence (verified literally in `00-race_enter_body_post_fix.c`):
   ```
   _camera_target = 0u;
   set_bkg_data(0, 3, _racing_track1_tileset);
   SWITCH_ROM(2);
   set_bkg_tiles(0, 0, 19u, 19u, _zone_track1_tiles);
   _current_tileset_id = 1u;
   pool_carAi_spawn(80u, 96u);
   { HIDE_SPRITES; _win_clear_region(0u, 0u, 20u, 18u); }
   SHOW_SPRITES;
   _raceTime = 0u;
   _position = 1u;
   _win_print_at(1u, 1u, "LAP:", 4u);
   ```

These are necessary preconditions for the MCP play-through but are NOT sufficient — the
visual confirmation (screenshots + human evaluation) is what closes D-19.
