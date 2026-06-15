---
status: resolved
trigger: "racer-bg-tilemap-not-rendered — at HEAD post Plan 18, racer ROM in mGBA shows dark BG (no track tiles), AI rival frozen at spawn, player car drifts freely. Round-2 (Plan 14) MCP play-through previously verified working state. Find what regressed."
created: 2026-05-10T00:00:00Z
updated: 2026-06-15T00:00:00Z
---

## Resolution 2026-06-15: WON'T-FIX — racer example retired in Phase 14 (dead code)

The `racer` example was retired in Phase 14 and is no longer part of the active example corpus. The debug session investigated a symptom in a game that no longer ships. Root cause was found (user-authored `clear()` in scene enter wipes the BG painted by the genre splice), but no fix will be applied since the example is dead code.

This session is archived for historical reference only.

## Current Focus

hypothesis: ROOT CAUSE FOUND — race scene's user-authored `enter { clear() ... print(...) }` runs AFTER the genre's enterOps splice (set_bkg_data + set_bkg_tiles + _current_tileset_id=1). `clear()` lowers to GBDK `cls()` which writes blank tile to the entire BG, wiping the just-painted tilemap. Then `print("LAP:", PositionDef(1,1))` calls `gotoxy/printf` which writes to the BG layer (NOT window) — corrupting BG tile (1,1) further. Cars appear stacked because `_car_x=80, _car_y=80` and `_pool_carAi_y=96` (16-px sprite tall, same column). This is NOT a regression — the Plan 14 screenshot evidence/racer_14_race_entry_frame129.png shows the IDENTICAL dark-BG-stacked-cars-LAP-text view. Round-2 verifier mistook variable assignment (`_current_tileset_id=1`) for actual BG rendering.
test: confirmed by reading bank1.c race_enter (cls() on line 45 after set_bkg_tiles on line 42; gotoxy/printf for "LAP:" on lines 50-51), GBDKPipelineV2.addGenreEnterOps line 1479 (prepends genre ops), Racer.kt line 137 (user enter has clear()/print()), and direct image comparison of evidence screenshot.
expecting: confirmed
next_action: write ROOT CAUSE FOUND with three concrete fix-direction options for /gsd-plan-phase --gaps

## Symptoms

expected: |
  On race-scene enter (after START on title): BG layer renders the high-contrast Plan 17 track tileset showing a closed corridor (drivable tiles) bounded by walls; AI rival visibly drives the loop on its own; player car moves with stats-driven physics and is bounded by track walls; camera follows the player within zone bounds.

actual: |
  - BG layer is uniform dark (no tiles visible) — only the window-layer "LAP:" HUD renders.
  - Player car + AI rival sprites both render. Cars are stacked vertically (player and AI at same X, adjacent Y) instead of a side-by-side grid start.
  - Holding UP makes the player car accelerate smoothly and travel until "glued to the top of the screen" — no track to bound or visually scroll against.
  - AI rival car is stationary throughout (does not advance waypoint index).

errors: None reported by user. ROM compiled clean.

reproduction: |
  1. ./gradlew :gbkt-examples:racer:clean :gbkt-examples:racer:buildRom
  2. ./gradlew :gbkt-examples:racer:runEmulator
  3. Press START → race scene loads → observe symptoms above.

started: |
  After Plans 15-18 (round-3 gap closures). Round-2 MCP at Plan 14 (~2026-05-08) confirmed SC-1, SC-3, SC-4 working at runtime.

## Eliminated

- hypothesis: "Plans 15-18 broke the scene-enter splice for tileset/tilemap load"
  evidence: bank1.c race_enter at HEAD still contains all the splice ops (set_bkg_data, set_bkg_tiles, _current_tileset_id=1, pool_carAi_spawn, _camera_target=0). GBDKPipelineV2.collectGenreEnterOps + addGenreEnterOps logic unchanged since Plan 10. The splice IS firing.
  timestamp: 2026-05-10

- hypothesis: "AI heading-pick reads all-WALL because _zone_track1_tiles array is uninitialized"
  evidence: zone_bank2.c contains a fully populated 19x19 = 361 byte _zone_track1_tiles array with the synthesized track corridor (rows 4-15 contain tiles 1=drivable and 2=grass). Computed by python3: at AI spawn (80,96) all 4 corner samples = non-zero (NW/NE=2 grass, SW/SE=1 drivable). AI heading-pick should NOT block on direction probes.
  timestamp: 2026-05-10

- hypothesis: "This is a regression introduced after Plan 14 verified state"
  evidence: viewing .planning/phases/07.4-sport-genre-codegen-fix-inserted/evidence/racer_14_race_entry_frame129.png shows IDENTICAL screenshot to user's complaint — dark BG, two stacked cars at same X column, "LAP:" text at top-left. The "round-2 SC-4 VERIFIED" verdict was based on variable-state snapshot (_current_tileset_id=1) and not on an actual visual confirmation that the BG layer renders the track. This is a verification methodology gap, not a code regression.
  timestamp: 2026-05-10

- hypothesis: "Plan 18 4-corner accept code change introduced a regression"
  evidence: 4-corner accept logic in main.c lines 311 and 446 is correctly emitted; computed by python3 trace, player at spawn (80,80) on grass passes accept; AI at (80,96) on grass passes accept; player movement N rejects only at row 4 walls (around y=24). Plan 18 only modified buildPositionWriteBackWithCollision; the dark-BG symptom predates Plan 18.
  timestamp: 2026-05-10

## Evidence

- timestamp: 2026-05-10
  checked: .planning/phases/07.4-sport-genre-codegen-fix-inserted/evidence/racer_14_race_entry_frame129.png (Plan 14 / round-2 "VERIFIED" screenshot)
  found: identical to user's HEAD complaint — dark BG, two cars stacked vertically at column 80, "LAP:" text at top-left
  implication: NOT a regression. Bug present at Plan 14 too. Round-2 verification only checked the variable assignment _current_tileset_id=1, never visually confirmed the track tilemap is on-screen.

- timestamp: 2026-05-10
  checked: gbkt-examples/racer/build/gbkt/generated/bank1.c lines 39-53 (race_enter body)
  found: |
    1) _camera_target = 0u                                          (genre splice)
    2) set_bkg_data(0, 3, _racing_track1_tileset)                   (genre splice — loads 3 tiles)
    3) set_bkg_tiles(0, 0, 19u, 19u, _zone_track1_tiles)            (genre splice — paints BG with track)
    4) _current_tileset_id = 1u                                     (genre splice — sentinel)
    5) pool_carAi_spawn(80u, 96u)                                   (genre splice — spawns rival)
    6) cls()                                                        (USER ENTER from Racer.kt:137 enter { clear() ... } — BLANKS BG)
    7) SHOW_SPRITES                                                 (USER ENTER showSprites())
    8) _raceTime = 0u; _position = 1u                               (USER ENTER assigns)
    9) gotoxy(1u, 1u); printf("LAP:")                               (USER ENTER print(...) — writes BG layer not window)
  implication: cls() at line 6 wipes the BG tilemap painted at line 3. printf at line 9 writes more BG-layer text on top of an already-blank background.

- timestamp: 2026-05-10
  checked: gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt addGenreEnterOps line 1479
  found: "fn.copy(body = statements + fn.body)" — genre enterOps are PREPENDED (not appended) to the user-authored enter body. KDoc explicitly says "Genre enter ops are inserted BEFORE user-authored enter ops".
  implication: ordering is by design — genre splice runs first, user `enter { clear() }` runs second. cls() therefore unconditionally wipes the BG painted by the splice.

- timestamp: 2026-05-10
  checked: gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitor.kt visitScreenClear line 896-897
  found: ScreenClear -> CCall("cls", emptyList()) — clear() DSL keyword lowers to GBDK cls().
  implication: clear() in user enter is exactly the source of the wiping cls() call. This is an architectural mismatch: the DSL clear() exists for scenes that DON'T have a custom BG tilemap (title, results, gameover), but is destructive for scenes that DO (race scene with track tilemap).

- timestamp: 2026-05-10
  checked: gbkt-examples/racer/src/main/kotlin/io/github/gbkt/examples/racer/Racer.kt lines 134-142 (raceScene enter block)
  found: |
    enter {
        clear()                                          // DSL author wrote this
        showSprites()
        raceTime set 0
        position set 1
        print("LAP:", position = PositionDef(1, 1))      // DSL author wrote this; lowers to BG-layer printf
    }
  implication: DSL author followed the same convention used in title/results scenes (clear + print HUD), unaware that the racing genre's enterOps splice has just painted the BG. The DSL surface offers no signal that clear() is destructive for racing scenes.

- timestamp: 2026-05-10
  checked: gbkt-examples/racer/build/gbkt/generated/main.c lines 252-256 (update_camera_camera body — secondary issue)
  found: |
    INT16 rawX = (INT16)_car_x - 80u;
    INT16 rawY = (INT16)_car_y - 72u;
    _camera_x = (UINT8)(rawX < 0u) ? 0u : (rawX > -8) ? -8 : rawX;
    _camera_y = (UINT8)(rawY < 0u) ? 0u : (rawY > 8u) ? 8u : rawY;
  implication: |
    Camera bounds computed from 19x19 tile map (= 152x152 px). maxX = boundsWidth - 160 = 152 - 160 = -8 (negative!). 
    Camera clamp ternary becomes "(rawX > -8) ? -8 : rawX" — at spawn rawX=0, condition true, _camera_x=(UINT8)(-8)=248. SCX_REG=248 → BG scroll pushes content off-screen. Same for Y: (rawY > 8) ? 8 : rawY (boundsHeight=152 → maxY=8 — also wrong sign).
    SECONDARY ISSUE: even if the cls() wipe were fixed, the camera would clip the BG into a tiny visible window because the racing-zone bounds are SMALLER than the GB screen (152 < 160). Either: bounds derivation should clamp maxX/maxY at 0, OR TrackSynthesizer should produce a zone larger than the screen, OR the camera should disable clamping when bounds < screen.

- timestamp: 2026-05-10
  checked: SportVisitor — does the racing genre auto-set boundsWidth/boundsHeight?
  found: needs verification but the generated C reveals it's setting them to map_tiles*TILE_SIZE = 19*8 = 152 (smaller than screen).
  implication: The racing camera auto-bounds derivation is mis-sized — needs review.

- timestamp: 2026-05-10
  checked: zone_bank2.c uses #pragma bank 2; set_bkg_tiles in race_enter (bank 1) accesses _zone_track1_tiles via game.h's "extern const UINT8 _zone_track1_tiles[361]"
  found: 16-bit pointer dereference across banks; SDCC banked-data convention not invoked. set_bkg_tiles calls __memcpy under the hood with the raw pointer; bank switching depends on caller.
  implication: TERTIARY ISSUE: even with cls() removed, set_bkg_tiles may read from the wrong bank because race_enter (bank 1) doesn't switch ROM bank to 2 before reading _zone_track1_tiles. Worth re-verifying once the cls() fix lands. (Plan 09 SUMMARY says set_bkg_tiles was put in scene-enter splice; whether SWITCH_ROM(2) is needed has not been audited.)

- timestamp: 2026-05-10
  checked: AI movement trace from spawn at (80, 96)
  found: First frame, _pool_carAi_speed_cur ramps 0→10 (line 422). ai_delta_carAi = 10 >> 5 = 0 (line 423). vy = 0 → no movement on frame 1. Frames 2-3 same (delta=0). Frame 4: speed=40, delta=1 → AI starts moving. By frame ~25, speed caps at 252, delta=7. AI should be visibly moving N.
  implication: AI is NOT frozen by code logic; it should be moving. The user's "AI does not move" perception is likely a visual artifact: with no BG reference frame and player car moving up at 6 px/frame from y=80 → y=24, the AI moving up at 7 px/frame from y=96 → ~y=20 would appear to follow at the same rate — visually a "static gap" between two moving sprites with no fixed reference. Fixing the BG render restores the reference frame and reveals the AI motion.

## Resolution

root_cause: |
  In the generated bank1.c race_enter, the user-authored `enter { clear() ... print("LAP:") }` runs AFTER the genre's enterOps splice (which paints the BG via set_bkg_tiles). `clear()` lowers to GBDK `cls()` (ScriptOpVisitor.visitScreenClear), which writes blank tile to the entire BG layer — wiping the just-painted track tilemap. Additionally, `print("LAP:")` lowers to BG-layer `gotoxy`/`printf` (not the window-layer `_win_print_at` helper), which would corrupt the BG further if cls() didn't already wipe it.

  This is NOT a regression introduced by Plans 15-18. The same bug was present at Plan 14 and is visible in evidence/racer_14_race_entry_frame129.png — round-2 SC-4 verification only checked the variable _current_tileset_id=1 (assignment confirmation) and never visually confirmed the BG layer pixels.

  Two secondary issues compound the symptom but are not the root cause:
  1. Camera bounds = 152x152 (smaller than 160x144 screen) → camera clamp produces SCX=248, SCY=8 → BG would be wildly mis-scrolled even without the cls() wipe.
  2. _zone_track1_tiles in #pragma bank 2 is read from race_enter in bank 1 without explicit ROM bank switching — may read garbage (needs verification once cls() fix lands).

fix: (not applied — goal is find_root_cause_only)

verification: (not applied — goal is find_root_cause_only)

files_changed: []


## Resolution

root_cause: (empty)
fix: (empty)
verification: (empty)
files_changed: []
