# Phase 12: Port platformer_template GBDK example to gbkt — Research

**Researched:** 2026-05-21
**Domain:** platformer genre codegen, tilemap-collision, horizontal scroll, variable-height jump,
multi-tileset asset pipeline, metasprite hflip, banked tile-data screen rendering
**Confidence:** HIGH (all claims verified against codebase + reference oracle)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Lift the one-named-codegen-bug-fix cap. Phase 12 is an explicit multi-bug integration
  phase. 4 pre-budgeted named surfaces (D-12..D-15). Additional inline fixes up to a
  reasonable bound; blast-radius-wide defects to seeds.
- **D-02:** 3-level faithful substrate (World1Area1 / World1Area2 / World2Area1) + banked tile-data
  title screen + banked NextLevel card. New subdirectory `gbkt-examples/platformer-template/`.
- **D-03:** Retire `gbkt-examples/platformer/` at Phase 12 close. `platform()` / `goalZone()`
  DSL primitives REMAIN in genre-platformer.
- **D-04:** Player metasprite = 6 frames + hflip-multiplier (Phase 10 path).
- **D-08:** 5 UAT anchor behaviors (title→gameplay, tilemap collision, horizontal scroll,
  metasprite animation, level-switch). Screenshots binding for all 5.
- **D-09:** 5-anchor cap is a second one-time expansion.
- **D-10:** MCP play-through + screenshot for all 5 anchors. Variable assertions PAIR with
  screenshots; codegen GREEN is NOT sufficient.
- **D-11:** UAT first — `12-UAT.md` + `PLAYBOOK.md` BEFORE any DSL (Plan 1).
- **D-12:** Tilemap-collision primitive on `platformerPhysics` with per-level overrides. Re-entrant
  `platformerPhysics { }` block inside `zone { }` shadows specific fields.
- **D-12a:** `IsTileSolid()` helper codegen — HOME bank, NONBANKED, with SWITCH_ROM wrapper.
- **D-12b:** 5-point bounding-box probe auto-derived from actor hitbox (hidden from user DSL).
- **D-13:** Column-by-column horizontal scroll codegen inside `buildCameraUpdateFunction()`.
  Gated on `platformerCamera { horizontal() } + smoothFollow()` (no new user-facing DSL).
- **D-13b:** ONE_WAY tile-type deferred. Seed `SEED-PHASE-12-ONE-WAY-TILE.md` at phase close.
- **D-13c:** `tilemapCollision` and `platform()` coexist. No deprecation.
- **D-14:** `platformerPhysics.jumpHold(maxFrames)` — new field + lowering.
- **D-14b:** Vertical-scroll NOT in scope.
- **D-15:** Multi-tileset pipeline verification + extension (if gaps exist).
- **D-16:** 5 JVM-tier emission invariants (one per UAT anchor), all using per-function awk
  brace-walk before grep.
- **D-17 / D-17a:** Three-signal artifact + bank-layout signal.
- **D-18:** Floor ≥22 plans; planner picks ceiling (expected 25-32).
- **D-19:** Phase 12.1 if surfaces = TERMINAL. No 12.1.1 / 12.2.
- **D-20:** Framework-shaping DSL gaps after port works → Phase 13 via `/gsd-phase --edit 13`.
- **D-21:** Verifier MUST run clean `:gbkt-examples:platformer-template:buildRom` AND reference
  `make` before declaring phase complete.
- **D-overfitting-1/2/3:** Anti-overfitting doctrine inherited unchanged from Phase 9/10/11.

### Claude's Discretion

- **D-claude-1:** Exact level/scene names (suggested: `world1Area1Zone`, `world1Area2Zone`,
  `world2Area1Zone`, `titleScene`, `gameplayScene`, `nextLevelScene`).
- **D-claude-2:** Exact timing for `gbkt-examples/platformer/` retirement (recommended: last plan
  before phase close).
- **D-claude-3:** Cartridge config — `"ROM_ONLY"` or `"MBC1"` based on FFD verdict.
- **D-claude-4:** GBC vs DMG target — recommended `GBC_COMPATIBLE`.
- **D-claude-5:** Joypad edge-detection parity — `buttons.a.pressed` must emit rising-edge only.
- **D-claude-6:** Level-end trigger DSL — `goalZone()` or explicit `whenever(player.x isAtLeast ...)`.
- **D-claude-7:** Import reference PNGs verbatim from `res/graphics/` with attribution README.

### Deferred Ideas (OUT OF SCOPE)

- ONE_WAY tile-type encoding (`oneWayThreshold`).
- Vertical scroll codegen.
- Typed `Cartridge` enum (Phase 13 requirement #1).
- Fixed-point sub-pixel typed wrapper `i16FixedVar` (Phase 13 requirement #3).
- Per-genre per-level config-table primitive generalization.
- `platform()` rectangle deprecation.
- Fixing existing `gbkt-examples/platformer/` in place.
- Pre-inserting Phase 12.1 placeholder before surplus seeds exist.
- Manual-banking DSL.

</user_constraints>

---

## Summary

Phase 12 is the fourth and final reference port of the v1.0 milestone. The GBDK reference
(`platformer_template`, 802 LoC, 5 C files) is the **codegen-shape oracle** — not a DSL template.
The gbkt port implements the same integration contract using declarative DSL idioms.

Four named codegen surfaces are pre-budgeted: (D-12) tilemap-collision with per-level
`platformerPhysics` overrides + 5-point bbox probe, (D-12a) `is_tile_solid()` HOME-bank NONBANKED
helper using the Phase 07.4-30 SWITCH_ROM wrapper pattern, (D-13) column-by-column horizontal
scroll codegen inside `buildCameraUpdateFunction`, and (D-14) `jumpHold(maxFrames)` variable-height
jump primitive. D-15 (multi-tileset pipeline) is also pre-budgeted but has a "just works" escape
hatch if research confirms `ConvertZoneTilesetsTask` already handles N distinct tilesets.

**Key integration finding:** The existing `PlatformerVisitor` is substantially more abstract than
the reference's C code. The reference's sub-pixel physics (`playerX >>= 4`, `playerY >>= 4`),
5-point AABB probe, and camera-half-screen trigger are NOT present in `buildPhysicsUpdateFunction`
or `buildCameraUpdateFunction` today — those currently emit a simplified coyote-time + jump-buffer
system with abstract `_plat_vy` and a dead-zone camera. Phase 12 must add a parallel "tilemap
physics" code path alongside the existing abstract path, NOT replace it (D-13c coexistence rule).

**Primary recommendation:** Add `solidThreshold` + `jumpHold` to `PlatformerPhysicsConfig`,
re-entrant `platformerPhysics { }` inside `zone { }` (handled via a new `ZoneBuilder`
extension that stores a `PlatformerPhysicsConfig?` on the zone IR), and build three new
Visitor methods: `buildIsTileSolidFunction`, an extended `buildPhysicsUpdateFunction` gated on
`solidThreshold != null`, and an extended `buildCameraUpdateFunction` with column-scroll logic.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Tilemap-collision (D-12) | gbkt-genre-platformer (PlatformerVisitor) | gbkt-backend-gbdk (GBDKPipelineV2 for helper placement) | Platformer visitor owns physics shape; pipeline routes HOME-bank helper |
| is_tile_solid() helper (D-12a) | gbkt-backend-gbdk HOME bank (main.c) | gbkt-genre-platformer (called from visitor-generated code) | Must be NONBANKED + HOME per SWITCH_ROM-from-BANKED constraint |
| 5-point bbox probe (D-12b) | gbkt-genre-platformer (PlatformerVisitor) | gbkt-lang (ActorBuilder hitbox — source of truth) | Auto-derived at codegen time from hitbox dimensions |
| Horizontal scroll (D-13) | gbkt-genre-platformer (PlatformerVisitor) | gbkt-backend-gbdk (SetCurrentLevelSubmap call must go through HOME wrapper) | Camera update function lives in visitor; submap call is cross-bank |
| Variable-height jump (D-14) | gbkt-genre-platformer (PlatformerVisitor + PlatformerPhysicsConfig) | gbkt-genre-platformer/dsl (PlatformerBuilders + PlatformerTypes) | Physics primitive, all tiers stay in genre-platformer |
| Multi-tileset pipeline (D-15) | gbkt-gradle-plugin (ConvertZoneTilesetsTask) | gbkt-backend-gbdk (allocateZoneBanks) | Existing pipeline task processes N zones; bank allocator already FFD |
| Banked tile-data title/level screens | gbkt-backend-gbdk (GBDKPipelineV2 + zone-bank files) | gbkt-lang (ZoneBuilder) | Zone-as-menu-graphic is a zone with tilesetPath but no gameplay |
| Metasprite 6-frame + hflip (D-04) | gbkt-backend-gbdk (MetaspriteVisitor) | gbkt-lang (MetaspriteBuilder) | Reuse verbatim — no extension needed |
| Level-switch + NextLevel card | gbkt-backend-gbdk (GBDKPipelineV2 scene navigation) | gbkt-genre-platformer (frameOps injection) | Scene-transition codegen lives in pipeline; level index is a variable |
| UAT anchors (D-08) | gbkt-emulator (MCP play-through) | gbkt-test (GbktTestExtension JVM tier) | All 5 anchors visual truths → screenshots binding |

---

## Reference Oracle Quick Map

### `src/main.c` (93 lines) — D-16 Invariant 1 source

| Lines | Shape |
|-------|-------|
| 14-92 | `main()` — DMG palette init, SHOW_BKG/SPRITES/8x16, `ShowCentered(TitleScreen...)`, `WaitForStartOrA()`, main loop |
| 34-35 | `currentLevel=255; nextLevel=0;` — triggers SetupCurrentLevel on first frame |
| 44-82 | Level-switch guard: `if (nextLevel != currentLevel)` → `ShowCentered(NextLevel...)` → `WaitForStartOrA()` → `SetupCurrentLevel()` → `SetCurrentLevelSubmap()` → `SetupPlayer()` |
| 85-91 | Game loop: `joypadPrevious = joypadCurrent; joypadCurrent = joypad(); UpdatePlayer(); UpdateCamera()` |

**Key variables declared in level.c:** `currentLevel`, `nextLevel`, `currentLevelWidth`,
`currentLevelWidthInTiles`, `currentLevelMap`, `currentLevelNonSolidTileCount`, `currentAreaBank`.

### `src/player.c` (355 lines) — D-12, D-13, D-14 source

| Lines | Shape |
|-------|-------|
| 14-17 | `#define GRAVTY 45; PLAYER_CHARACTER_INCREASE_JUMP_TIMER_MAX 20; PLAYER_CHARACTER_JUMP_VELOCITY 550; PLAYER_CHARACTER_WALK_VELOCITY 325` |
| 23-27 | `#define PLAYER_CHARACTER_BOUNDING_BOX_HALF_WIDTH 5; HALF_HEIGHT 12; HEIGHT 24` |
| 30-34 | Globals: `facingRight`, `playerJumpIncrease`, `threeFrameCounter`, `playerX/Y (uint16)`, `playerXVelocity/playerYVelocity (int16)` |
| 135-212 | `UpdatePlayer()` — walk/run velocity, turn detection, `threeFrameCounter` animation cycle |
| 213-214 | `playerRealX = playerX>>4; playerRealY = playerY>>4;` — sub-pixel shift |
| 220-231 | Stuck-in-ground while-loop: `while(IsTileSolid(x, y+HEIGHT-1)) { playerY-=16; }` |
| 241-258 | Horizontal AABB probes: right-moving: `IsTileSolid(x+HALF_WIDTH, y+2)`, `IsTileSolid(x+HALF_WIDTH, y+HALF_HEIGHT)`, `IsTileSolid(x+HALF_WIDTH, y+HEIGHT-2)`. Left-moving: symmetric with -HALF_WIDTH |
| 261-283 | Vertical AABB probes: falling: `IsTileSolid(x+(HALF_WIDTH-2), y+HEIGHT)` and `IsTileSolid(x-(HALF_WIDTH-2), y+HEIGHT)`. Rising: while-loop with `IsTileSolid(x+(HALF_WIDTH-2), y)` |
| 286-288 | Jump input: `pressedA = (joypadCurrent & J_A && !(joypadPrevious & J_A))` — rising-edge detection |
| 291-294 | Jump initiation: `playerYVelocity=-PLAYER_CHARACTER_JUMP_VELOCITY; playerJumpIncrease=20` |
| 297-317 | Variable-height jump: while airborne, `playerJumpIncrease--`; if A/Up NOT held OR timer expired → apply `GRAVTY (45)` |
| 320-321 | Velocity integration: `playerX += playerXVelocity>>4; playerY += playerYVelocity>>4` |
| 328-334 | Camera trigger: `if (playerRealX >= DEVICE_SCREEN_PX_WIDTH>>1)` → `camera_x = playerRealX - 80; clamp to max` |
| 345-348 | Frame selection + directionOffset: frame = grounded ? (turning ? 5 : (vx==0 ? 0 : walk)) : (vy<0 ? 3 : 4); `directionOffset = facingRight ? 0 : 6` |
| 351-353 | Level-end trigger: `if (playerRealX > currentLevelWidth - 32) nextLevel++` |

**D-12b probe offsets** (auto-derivable from hitbox(0,0,8,24) → halfW=5, halfH=12, height=24):

| Direction | Points sampled |
|-----------|----------------|
| Right wall | `(x+5, y+2)`, `(x+5, y+12)`, `(x+5, y+22)` |
| Left wall | `(x-5, y+2)`, `(x-5, y+12)`, `(x-5, y+22)` |
| Feet (falling) | `(x+3, y+24)`, `(x-3, y+24)` |
| Head (rising) | `(x+3, y)`, `(x-3, y)` |
| Stuck-in-ground | `(x, y+HEIGHT-1)` = `(x, y+23)` — single center point |

**HALF_WIDTH-2 pattern:** The probes use `HALF_WIDTH-2 = 3` (not 5) for vertical probes to prevent
getting caught in corners where both horizontal and vertical probes fire simultaneously.

### `src/level.c` (153 lines) — D-12a, D-15, D-16 Invariant 2 source

**Globals declared:**
```c
uint16_t currentLevelWidth, currentLevelWidthInTiles, currentLevelHeight, currentLevelHeightInTiles;
const uint8_t *currentLevelMap;
uint8_t currentLevelNonSolidTileCount;
uint8_t currentAreaBank;
uint8_t currentLevel = 255;
uint8_t nextLevel = 0;
```

**`IsTileSolid()` complete body (lines 40-68):**
```c
uint8_t IsTileSolid(uint16_t worldX, uint16_t worldY) NONBANKED {
    uint8_t _previous_bank = CURRENT_BANK;
    SWITCH_ROM(currentAreaBank);
    uint16_t column = worldX >> 3;
    uint16_t row = worldY >> 3;
    uint16_t worldMaxRow = currentLevelHeight >> 3;
    if (row > worldMaxRow || column >= currentLevelWidthInTiles) {
        SWITCH_ROM(_previous_bank);
        return TRUE;    // treat out-of-bounds as solid
    }
    uint16_t index = column + row * currentLevelWidthInTiles;
    uint8_t tile = currentLevelMap[index];
    SWITCH_ROM(_previous_bank);
    return tile < currentLevelNonSolidTileCount;  // solid if tile index < threshold
}
```

**`SetupCurrentLevel()` structure (lines 72-153):**
- NONBANKED, switch on `currentLevel % 3`
- case 0: `SWITCH_ROM(BANK(World1Tileset))` → `set_native_tile_data(0, ...)` → `setBKGPalettes(...)` → `SWITCH_ROM(currentAreaBank = BANK(World1Area1))` → assign width/height/map/nonSolidCount
- case 1: same pattern with World1Area2 (same tileset, same solidThreshold=17)
- case 2: World2Tileset → World2Area1, solidThreshold=68

**Critical observation:** `currentAreaBank` captures the area's bank, NOT the tileset's bank.
Tileset load uses a one-time `SWITCH_ROM` then is discarded. Only the area (tilemap) bank
is stored for use by `IsTileSolid()` and `SetCurrentLevelSubmap()`.

### `src/camera.c` (83 lines) — D-13, D-16 Invariant 3 source

**Globals declared:**
```c
uint16_t camera_x, old_camera_x;
uint8_t map_pos_x, old_map_pos_x;
uint8_t redraw;
```

**`SetCurrentLevelSubmap()` body (lines 30-40):**
```c
void SetCurrentLevelSubmap(uint8_t x, uint8_t y, uint8_t w, uint8_t h) NONBANKED {
    uint8_t _previous_bank = CURRENT_BANK;
    SWITCH_ROM(currentAreaBank);
    set_bkg_submap(x, y, w, h, currentLevelMap, currentLevelWidthInTiles);
    SWITCH_ROM(_previous_bank);
}
```

**`UpdateCamera()` complete body (lines 56-83):**
```c
void UpdateCamera(void) BANKED {
    move_bkg(camera_x, 0);
    map_pos_x = (uint8_t)(camera_x >> 3u);
    if (map_pos_x != old_map_pos_x) {
        if (camera_x < old_camera_x) {
            // scrolling left: update column at map_pos_x+1 (for GB: same col due to no extra buffer)
            SetCurrentLevelSubmap(map_pos_x + 1, 0, 1, MIN(DEVICE_SCREEN_HEIGHT, currentLevelHeightInTiles));
        } else {
            // scrolling right: only if not at level edge
            if ((currentLevelWidthInTiles - DEVICE_SCREEN_WIDTH) > map_pos_x) {
                SetCurrentLevelSubmap(map_pos_x + DEVICE_SCREEN_WIDTH, 0, 1, MIN(DEVICE_SCREEN_HEIGHT, currentLevelHeightInTiles));
            }
        }
        old_map_pos_x = map_pos_x;
    }
    old_camera_x = camera_x;
}
```

**Note:** `SetCurrentLevelSubmap` is NONBANKED and uses SWITCH_ROM internally. It is called from
`UpdateCamera` which is BANKED (bank 255 in reference = scene bank in gbkt = bank1.c). This is
the same BANKED-calls-NONBANKED pattern as the Phase 07.4-30 wrapper — safe because the NONBANKED
function executes entirely in HOME bank context.

### `src/common.c` (66 lines) — D-claude-5, banked-title-card

**`WaitForStartOrA()` — joypad edge-detection:**
```c
if ((joypadCurrent & J_START) && !(joypadPrevious & J_START)) break;
if ((joypadCurrent & J_A) && !(joypadPrevious & J_A)) break;
```
This is rising-edge detection. gbkt's `buttons.start.pressed` and `buttons.a.pressed` EMIT
`button_pressed(J_START)` / `button_pressed(J_A)` which is also rising-edge. [VERIFIED: codebase]

**`ShowCentered()` — banked tile-data screen:**
```c
void ShowCentered(uint8_t width, uint8_t height, uint8_t bank, uint8_t* tileData, uint8_t tileCount,
                  uint8_t* mapData, const palette_color_t* palettes) NONBANKED {
    DISPLAY_OFF;
    SWITCH_ROM(bank);
    setBKGPalettes(1, palettes);
    set_native_tile_data(0, tileCount, tileData);
    fill_bkg_rect(0, 0, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0);
    set_bkg_tiles(titleColumn, titleRow, width>>3, height>>3, mapData);
    SWITCH_ROM(_previous_bank);
    DISPLAY_ON;
}
```

This pattern: SWITCH_ROM to target bank → load tile data + map → SWITCH_ROM back. The
`ShowCentered` call takes (tileData, tileCount, mapData, palettes) all from the same bank.
In gbkt terms: a zone zone with tilesetPath = title screen PNG, placed in a bank, loaded during
scene enter by calling this same pattern through the GBDKPipelineV2 tileset guard mechanism.

### `Makefile` — D-17a reference ROM build [VERIFIED: file read]

**PNG asset conversion invocations (Makefile png2asset target):**
```makefile
$(PNG2ASSET) res/graphics/player-character-gbapduck-sprites.png -c ... -px 12 -py 6 -spr8x16 -keep_palette_order -sw 24 -sh 32 -b 255
$(PNG2ASSET) res/graphics/world1-tileset.png -c ... -keep_palette_order -noflip -map -b 255
$(PNG2ASSET) res/graphics/world2-tileset.png -c ... -keep_palette_order -noflip -map -b 255
$(PNG2ASSET) res/graphics/world1-area1.png -c ... -noflip -map -maps_only -source_tileset res/graphics/world1-tileset.png -b 255
$(PNG2ASSET) res/graphics/world1-area2.png -c ... -noflip -map -maps_only -source_tileset res/graphics/world1-tileset.png -b 255
$(PNG2ASSET) res/graphics/world2-area1.png -c ... -noflip -map -maps_only -source_tileset res/graphics/world2-tileset.png -b 255
$(PNG2ASSET) res/graphics/title-screen.png -c ... -noflip -map -b 255
$(PNG2ASSET) res/graphics/next-level.png -c ... -noflip -map -b 255
```

**Key flags:** All assets use `-b 255` (bank 255 = autobank; linker assigns final bank).
Tileset PNGs get `-noflip -map` (produces tile data + index map). Area PNGs get `-noflip -map -maps_only -source_tileset <tileset>` (produces only the tile-index map, referencing the tileset's tile array).

**Cartridge:** `-Wl-yt0x1B` = MBC5+RAM+BATTERY (0x1B). In gbkt terms: `"MBC5_RAM_BATTERY"`.
The reference uses `-autobank` for bank assignment.

---

## Existing gbkt Surface Inventory

### `gbkt-genre-platformer/.../PlatformerBuilders.kt` — EXISTS [VERIFIED: file read]

**Exists today:**
- `PlatformerPhysicsBuilder` with: `gravity`, `jumpForce`, `terminalVelocity`, `coyoteTime`,
  `jumpBuffer`, `airControl`, `fixedJump`, `wallJump { }`.
- `PlatformerCameraBuilder` with: `smoothFollow`, `screenLock`, `deadZone`, `horizontal`,
  `vertical`, `multiDirectional`, `parallax`.

**MISSING — needs adding:**
- `solidThreshold(value: Int)` — new field on `PlatformerPhysicsBuilder`
- `jumpHold(maxFrames: Int)` — new field on `PlatformerPhysicsBuilder`
- Re-entrant `platformerPhysics { }` block on `ZoneBuilder` — new extension (see D-12
  Recommendations below)

### `gbkt-genre-platformer/.../PlatformerTypes.kt` — EXISTS [VERIFIED: file read]

**Exists today:**
- `PlatformerPhysicsConfig` with: `gravity`, `jumpForce`, `terminalVelocity`, `coyoteFrames`,
  `jumpBufferFrames`, `airControlFactor`, `variableHeightJump`, `wallJump`.
- `PlatformerCameraConfig` with: `mode`, `deadZoneX/Y`, `scrollDirections`, `parallaxLayers`.

**MISSING — needs adding:**
- `solidThreshold: Int? = null` on `PlatformerPhysicsConfig` (null = no tilemap collision)
- `jumpHoldMaxFrames: Int = 0` on `PlatformerPhysicsConfig` (0 = disabled)

**No change needed for `PlatformerCameraConfig`** — `SMOOTH_FOLLOW` + `HORIZONTAL` are
sufficient triggers for the D-13 column-scroll codegen; no new user-facing fields needed.

### `gbkt-genre-platformer/.../PlatformerExtensions.kt` — EXISTS [VERIFIED: file read]

**Exists today:** `platformerPhysics`, `platformerCamera`, `platform`, `hazard`, `goalZone`,
`collectible`, `ladder` — all extensions on `GameBuilder`.

**MISSING — needs adding:**
- `fun ZoneBuilder.platformerPhysics(block: PlatformerPhysicsBuilder.() -> Unit)` —
  re-entrant extension for per-level overrides (see D-12 Recommendations for shape).

### `gbkt-genre-platformer/.../PlatformerVisitor.kt` — EXISTS [VERIFIED: file read]

**Exists today:**
- `buildPhysicsUpdateFunction(cfg)` at line 183 — emits `_plat_vy` gravity + coyote + jump buffer
  + variable-height cut + horizontal friction. Uses `_plat_vy`/`_plat_vx` as abstract INT8
  variables. No tilemap probing, no sub-pixel `playerX`/`playerY` `>>4`, no `threeFrameCounter`.
- `buildCameraUpdateFunction(cfg)` at line 541 — emits `move_bkg(_cam_x, _cam_y)` via
  `buildSmoothFollowBody` / `buildScreenLockBody`. No `set_bkg_submap`, no `map_pos_x`, no
  column-scroll. Current camera vars: `_cam_x` (INT8), `_cam_y` (INT8), `_cam_target_x/y` (INT8).
- `buildSmoothFollowBody` — emits dead-zone check for x and y axes.
- `buildScreenLockBody` — emits screen-snap.
- `visitPhysics` — generates `_plat_vy`, `_plat_vx`, `_plat_grounded`, `_plat_coyote_timer`,
  `_plat_jump_buffer` as WRAM globals.

**MISSING — needs adding:**
1. `buildIsTileSolidFunction()` — HOME-bank NONBANKED helper (D-12a). Separate CFunction with
   `bank = 0`.
2. Extended `buildPhysicsUpdateFunction` branch gated on `solidThreshold != null` — emits the
   full sub-pixel physics (i16 `playerX/Y`, 5-point AABB probes, `IsTileSolid()` calls,
   `threeFrameCounter` animation, `facingRight`, `jumpHold` timer, camera-half-screen trigger).
3. Extended `buildCameraUpdateFunction` branch gated on tilemap collision mode — emits
   `move_bkg(camera_x, 0)` + `map_pos_x = (uint8_t)(camera_x >> 3u)` + column-scroll guard.
4. New vars for tilemap collision mode: `_player_x/y` (UINT16 sub-pixel), `_player_vx/vy`
   (INT16), `_camera_x/old_camera_x` (UINT16), `_map_pos_x/old_map_pos_x` (UINT8),
   `_current_level_width_in_tiles` (UINT16), `_current_level_non_solid_tile_count` (UINT8),
   `_current_area_bank` (UINT8), `_current_level_map` (pointer), `_facing_right` (UINT8),
   `_jump_increase_timer` (UINT8), `_three_frame_counter` (UINT8), `_current_level` (UINT8),
   `_next_level` (UINT8).

**IMPORTANT:** The existing `buildPhysicsUpdateFunction` uses a simplified abstract physics model
(`_plat_vy` INT8, button_pressed(J_A), coyote-time). Phase 12 DOES NOT replace this — it adds
a PARALLEL codegen path gated on `solidThreshold != null`. Games without tilemap collision
continue to use the existing abstract path.

### `gbkt-backend-gbdk/.../GBDKPipelineV2.kt` — EXISTS [VERIFIED: file read]

**Exists today (directly relevant):**
- `allocateZoneBanks()` at line 596 — FFD bin-packing across banks starting at bank 2.
  Already handles N zones across M banks. Zone sizing via `zoneTileDataSize()`.
- `buildTilemapBankFiles()` at line 670 — produces `zone_bankN.c` per bank. NEW-path zones
  (where `zone.tilesetPath != null && tileData.isEmpty()`) are SKIPPED (their data comes from
  `ConvertZoneTilesetsTask` output). Legacy/procedural zones emit `_zone_<id>_tiles[]`.
- `buildBkgTilesLoadBankedHelper()` at line 1938 — the Phase 07.4-30 HOME-bank wrapper:
  ```c
  void _bkg_tiles_load_banked(UINT8 bank, UINT8 x, UINT8 y, UINT8 w, UINT8 h, const UINT8* tiles) {
      SWITCH_ROM(bank);
      set_bkg_tiles(x, y, w, h, tiles);
      SWITCH_ROM(1);
  }
  ```
  This is the EXACT pattern D-12a's `is_tile_solid()` replicates.
- Tileset ID map: `buildTilesetIdMap()` + `addTilesetGuardToEnterFunction()` — scene enter
  tileset reuse guard using `_current_tileset_id`.

**MISSING — needs adding:**
- D-12a: `buildIsTileSolidFunction()` — HOME-bank (bank=0) NONBANKED helper emitting the
  `SWITCH_ROM(currentAreaBank)` + tile-index math + bounds check + `SWITCH_ROM(prev_bank)` shape.
  Analogous to `buildBkgTilesLoadBankedHelper` but for tile solidity lookup. Lives in `main.c`.
- D-13: `buildSetCurrentLevelSubmapFunction()` — HOME-bank NONBANKED helper for `set_bkg_submap`
  from banked context (analogous to `_bkg_tiles_load_banked` but for submap). Called from
  camera update which may run from BANKED scenes.
- D-15 investigation: whether N distinct tilesets across M zones are handled automatically.

**D-15 finding:** The pipeline already handles arbitrary N distinct tilesets across M zones via
`ConvertZoneTilesetsTask` — each zone has its own `tilesetPath`, the task converts them to
`_zone_<id>_tileset.c` files, and `allocateZoneBanks` packs them. **However**, there is a
gap: `ConvertZoneTilesetsTask` processes tilesets PER ZONE, not per shared tileset. If two
zones share the same tileset PNG (World1Area1 and World1Area2 both use world1-tileset.png),
the task will invoke png2asset TWICE, producing two identical `_zone_world1Area1_tileset.c`
and `_zone_world1Area2_tileset.c`. This wastes ROM space but works correctly. The reference
loads the shared tileset once and keeps multiple area maps — gbkt's per-zone path duplicates
tileset data per zone. **This is a gap but not a blocker** — correctness is preserved, only
ROM size is slightly larger. The D-15 plan must document this and decide whether to extend
`ConvertZoneTilesetsTask` to support a shared tileset mode, or accept the duplication within
the `2x reference ROM size` signal threshold.

### `gbkt-lang/.../WorldBuilders.kt` — EXISTS [VERIFIED: file read]

**Exists today:** `ZoneBuilder` with `id`, `zoneName`, `tilesetPath`, `mapWidth/Height`,
`tileData`, `collisionData`, `encounterBuilder`, transitions, `bankOverride`, `zoneObjects`.
Builds to `ZoneIR`.

**MISSING — needs adding:**
- `platformerPhysicsOverride: PlatformerPhysicsConfig? = null` field on `ZoneBuilder`
  (or via re-entrant `platformerPhysics { }` extension — see D-12 Recommendations)
- Corresponding field on `ZoneIR`

**No other genre currently patches ZoneBuilder** — the coupling precedent does NOT exist yet.
The recommended approach (see D-12 section below) avoids direct ZoneBuilder dependency on
platformer types.

### `gbkt-ir/.../WorldIR.kt` — EXISTS [VERIFIED: file read]

**Exists today:** `ZoneIR` with `id`, `name`, `tilesetPath`, `mapWidth/Height`, `tileData`,
`collisionData`, `encounterTable`, `isSafeZone`, `transitions`, `transitionStyle`, `onEnter`,
`onExit`, `bankOverride`, `objects`.

**MISSING — needs adding:**
- `platformerPhysics: Map<String, Any>? = null` — stores per-level physics override as a
  generic Map to avoid gbkt-ir depending on gbkt-genre-platformer types. The PlatformerVisitor
  reads it back by key at codegen time.
  Alternatively: `platformerPhysicsConfig: Any? = null` cast to `PlatformerPhysicsConfig`
  inside the visitor (same approach as GenericSystem config maps).

### `gbkt-backend-gbdk/.../MetaspriteVisitor.kt` — EXISTS [VERIFIED: file read]

**Fully reusable for D-04 — NO EXTENSION NEEDED.**

The `generateMetaspriteFrameSwitch` method already supports:
- `move_metasprite_flipx()` for case 3 (flipX only) → this is hflip
- `move_metasprite_flipy()` for case 1
- `move_metasprite_flipxy()` for case 2
- `move_metasprite_ex()` for default (no flip)
- Parameterized `posXVar`, `posYVar`, `idxVar`, `rotVar`

For D-04's 6-frame + hflip authoring: the user declares 12 frames total (6 right-facing +
6 left-facing as hflip equivalents), OR uses the `rot` variable encoding. However, the reference
uses a simpler **direction-offset** approach (`frame + directionOffset` where `directionOffset`
is 0 or 6). This is NOT the same as the `rot & 0x3` flip switch in the current visitor.

**D-04 gap:** The current `generateMetaspriteFrameSwitch` uses `rot & 0x3` for flip state and
`rot >> 2` for sub-palette — it was designed for the GBC palette cycling game (Metasprites
example). For a platformer, the player has ONE sub-palette and facing direction determines flip.
The planner needs to choose between:
- **(a) Adapt the existing 12-frame visitor path:** User authors 12 frames (6R + 6L), `idxVar`
  is derived from `facingRight ? frame : frame + 6`, `rotVar` is unused (rot=0, no flip).
  Simpler but uses 12 frame descriptors.
- **(b) True hflip with `facingRight` variable:** User authors 6 frames only. `rot` encodes
  facing (rot=0 = no flip, rot=3 = flipX). When moving left, `rot` is set to 3 and the
  visitor emits `move_metasprite_flipx(frames[idx], ...)`. This uses the existing flipX case.

**Recommended: option (b).** Matches D-04's "6 frames + hflip-multiplier" decision. The DSL
shape: `var facing by u8Var(0)` (0=right, 3=left); whenever `dpad.left.held { facing set 3 }`,
whenever `dpad.right.held { facing set 0 }`. `moveMetasprite(player)` with `rotVar = facing`.
The existing `generateMetaspriteFrameSwitch` case 3 fires when `rot=3` → emits
`move_metasprite_flipx(frames[idx], ...)`. No visitor extension needed.

### `gbkt-examples/.archive/platformer/` — ARCHIVED [VERIFIED: directory read]

- Uses `ROM_ONLY`, `romBanks = 2`.
- Uses `platform()` rectangles and `goalZone()` only — NO zone/tileset, NO tilemap collision.
- Shares NO zone-data path with Phase 12's substrate.
- Already archived in Phase 11.3. The "retirement" in Phase 12 D-03 means: remove the
  `include("gbkt-examples:platformer")` entry from `settings.gradle.kts`. The `.archive/`
  directory already exists.

**CRITICAL FINDING:** `gbkt-examples/platformer` (and `platformer-gbc`) are NOT in
`settings.gradle.kts` currently — they were archived in Phase 11.3. D-03's "retirement"
is therefore a NO-OP for build system purposes. The plan must document this: the retirement
step is adding a note + deleting the `.archive/platformer/` if desired, or is literally
already done. Planner must verify.

---

## D-12 Recommendations (DSL shape + IR + Visitor)

### Research question 1 & 2: Which DSL shape?

**Recommended shape: re-entrant `platformerPhysics { }` block inside `zone { }` via a
ZoneBuilder extension in PlatformerExtensions.kt.** This is option (a) from CONTEXT.md
combined with a loose-coupling mechanism.

**Rationale:**
- Does NOT add platformer types to ZoneIR directly (would violate module boundaries).
- Uses the same GenericSystem config-map pattern already used by all genre systems.
- ZoneBuilder gets a `platformerPhysicsOverride: PlatformerPhysicsConfig? = null` stored
  in a generic map (or as an opaque Any?) on ZoneIR.
- PlatformerVisitor reads zone-level physics override at codegen time.

**Exact DSL authoring shape:**
```kotlin
// In PlatformerExtensions.kt — new extension
fun ZoneBuilder.platformerPhysics(block: PlatformerPhysicsBuilder.() -> Unit) {
    val builder = PlatformerPhysicsBuilder()
    builder.block()
    setPlatformerPhysicsOverride(builder.buildConfig())  // ZoneBuilder gets new internal method
}
```

**ZoneBuilder change:** Add `private var platformerPhysicsOverride: Any? = null` and
`internal fun setPlatformerPhysicsOverride(cfg: Any)`. Build into `ZoneIR` as
`platformerPhysicsOverride = platformerPhysicsOverride`.

**ZoneIR change:** Add `val platformerPhysicsOverride: Any? = null`.

**PlatformerVisitor codegen:** After computing game-level physics config from the GenericSystem,
for each zone, check `zone.platformerPhysicsOverride as? PlatformerPhysicsConfig` and merge
only the non-null fields (gravity, solidThreshold, etc.) as a per-level C struct or #define block.

**Why NOT option (b) `PlatformerPhysicsBuilder.level(zoneRef) { }` builder-side hook:**
Looser coupling but requires the user to declare the override OUTSIDE the zone block, which
is less readable and violates the "no magic strings" rule (would need `level("world2Area1") { }`
string parameter).

**Why NOT option (c) `zonePlatformerPhysics(zone) { }` top-level extension:**
Syntactically awkward and separates zone config from zone declaration.

### IR lowering shape

The per-level physics config lowers to a C-level config table (`struct LevelPhysicsConfig`) or
simply to a set of global variables that are overwritten in `SetupCurrentLevel()`. Simplest
approach matching the reference: store `solidThreshold` in `_current_level_non_solid_tile_count`,
store `gravity` override in `_current_gravity`, etc. Updated in the level-switch block.

---

## D-12a Recommendations (is_tile_solid helper codegen)

### Research question 3: Exact shape of Phase 07.4-30 wrapper

The existing `_bkg_tiles_load_banked` (built by `buildBkgTilesLoadBankedHelper()` at line 1938)
is the direct template. The generated C shape:
```c
// Plan 07.4-30 / D-N-SWITCHROM-RESTORE: HOME-bank SWITCH_ROM wrapper
void _bkg_tiles_load_banked(UINT8 bank, UINT8 x, UINT8 y, UINT8 w, UINT8 h, const UINT8* tiles) {
    SWITCH_ROM(bank);
    set_bkg_tiles(x, y, w, h, tiles);
    SWITCH_ROM(1);
}
```

**`is_tile_solid()` target shape for D-12a:**
```c
// Phase 12 D-12a: HOME-bank NONBANKED tile-solidity helper (SWITCH_ROM pattern)
UINT8 is_tile_solid(UINT16 world_x, UINT16 world_y) NONBANKED {
    UINT8 _previous_bank = CURRENT_BANK;
    SWITCH_ROM(_current_area_bank);
    UINT16 column = world_x >> 3u;
    UINT16 row = world_y >> 3u;
    UINT16 world_max_row = _current_level_height >> 3u;
    if (row > world_max_row || column >= _current_level_width_in_tiles) {
        SWITCH_ROM(_previous_bank);
        return TRUE;
    }
    UINT16 index = column + row * _current_level_width_in_tiles;
    UINT8 tile = _current_level_map[index];
    SWITCH_ROM(_previous_bank);
    return tile < _current_level_non_solid_tile_count;
}
```

**`_current_area_bank` availability:** This variable does NOT exist in the current gbkt codegen
output. It must be ADDED as a global `UINT8 _current_area_bank = 0u;` in `main.c` (HOME bank),
updated in `SetupCurrentLevel()` when the active zone's bank is switched in. This parallels
the reference's `currentAreaBank` global.

**Where to add `buildIsTileSolidFunction()`:** In `PlatformerVisitor` (it's a platformer-specific
helper) but must be emitted to HOME bank (bank=0). The visitor returns it via `GenreVisitorResult`
with a special flag, OR it is built directly by `GBDKPipelineV2.buildHomeFile()` when it detects
a `platformer_physics` system with `solidThreshold != null`. The latter is cleaner (HOME-bank
functions belong to the pipeline's `buildHomeFile` responsibility).

**Recommended placement:** `GBDKPipelineV2.buildHomeFile()` calls a new method
`buildIsTileSolidHelperIfNeeded(gameIR)` that returns an optional `CFunction` with `bank = 0`
when any zone has tilemap collision configured.

---

## D-12b Recommendations (5-point probe auto-derivation)

### Research question 4: Feasibility from hitbox shape

**YES — fully feasible from hitbox.** The reference's probe offsets are derived from constants:
- `PLAYER_CHARACTER_BOUNDING_BOX_HALF_WIDTH = 5` (from sprite width 10/2)
- `PLAYER_CHARACTER_BOUNDING_BOX_HEIGHT = 24` (sprite height 24)
- `PLAYER_CHARACTER_BOUNDING_BOX_HALF_HEIGHT = 12`

With `hitbox(0, 0, 8, 24)` (origin-x, origin-y, width, height):
- `halfWidth = width / 2 = 4` (reference uses 5, but 4 is auto-derived from 8px width)
- `height = 24`, `halfHeight = 12`

The codegen auto-derives:
- `HALF_WIDTH = hitbox.width / 2` (for wall probes: `±HALF_WIDTH`)
- `HALF_WIDTH - 2` (for vertical probes, to avoid corner snag)
- `HEIGHT` (for foot probes: `y + height`)
- `HEIGHT - 1` (for stuck-in-ground check)
- `2` and `HALF_HEIGHT` (for mid-point right/left wall probes)

The 5 probe groups become:
```
right wall: (x+halfW, y+2), (x+halfW, y+halfH), (x+halfW, y+height-2)
left wall:  (x-halfW, y+2), (x-halfW, y+halfH), (x-halfW, y+height-2)
feet:       (x+(halfW-2), y+height), (x-(halfW-2), y+height)
head:       (x+(halfW-2), y), (x-(halfW-2), y)
stuck:      (x, y+height-1)  [single center, pre-move resolve]
```

**User authors:** `hitbox(0, 0, 8, 24)` — no explicit probe parameters.
**Codegen auto-computes:** All offsets above, embedded directly in the generated `UpdatePlayer()`
function body inside `platformer_physics_update()` or equivalent.

---

## D-13 Recommendations (horizontal scroll codegen insertion)

### Research question 5: Insertion point and missing variables

**Insertion point:** `buildSmoothFollowBody()` in `PlatformerVisitor` (currently ends with
`move_bkg(_cam_x, _cam_y)`). The column-scroll logic is APPENDED after `move_bkg`. However:

**PROBLEM:** The current camera vars are `_cam_x (INT8)` and `_cam_target_x (INT8)`.
The reference uses `camera_x (UINT16)` (pixel position, 0..levelWidth-160). An INT8 camera
can only represent 0..127 pixels, which is insufficient for wide levels (>127 px scroll range).

**Resolution:** For the tilemap-collision code path, introduce `_camera_x (UINT16)` alongside
the existing `_cam_x (INT8)` abstract variable. The tilemap physics path uses `_camera_x` (wide);
the abstract physics path uses `_cam_x` (narrow). These are different variable names.

**Variables existing in current visitor:**
- `_cam_x (INT8)` — EXISTS (visitCamera)
- `_cam_y (INT8)` — EXISTS
- `_cam_target_x/y (INT8)` — EXISTS

**Variables that need adding for tilemap-collision camera path:**
- `_camera_x (UINT16)` — pixel camera position (0..level width - screen width)
- `_old_camera_x (UINT16)` — previous frame camera_x
- `_map_pos_x (UINT8)` — current tile column: `camera_x >> 3`
- `_old_map_pos_x (UINT8)` — previous frame map_pos_x
- `_current_level_width_in_tiles (UINT16)` — from SetupCurrentLevel

**SetCurrentLevelSubmap codegen:** This call needs to happen from HOME bank context (it uses
SWITCH_ROM internally). In the reference, `UpdateCamera()` is BANKED and calls
`SetCurrentLevelSubmap()` which is NONBANKED. In gbkt, the camera update runs from the scene
frame function (bank1.c). The `_bkg_set_level_submap_banked()` helper must also be HOME-bank:

```c
// HOME-bank NONBANKED helper for set_bkg_submap from banked context
void _bkg_set_level_submap_banked(UINT8 x, UINT8 y, UINT8 w, UINT8 h) NONBANKED {
    UINT8 _previous_bank = CURRENT_BANK;
    SWITCH_ROM(_current_area_bank);
    set_bkg_submap(x, y, w, h, _current_level_map, _current_level_width_in_tiles);
    SWITCH_ROM(_previous_bank);
}
```

**Generated column-scroll C shape (appended after `move_bkg(_camera_x, 0u)`):**
```c
_map_pos_x = (UINT8)(_camera_x >> 3u);
if (_map_pos_x != _old_map_pos_x) {
    if (_camera_x < _old_camera_x) {
        _bkg_set_level_submap_banked(_map_pos_x + 1u, 0u, 1u, DEVICE_SCREEN_HEIGHT);
    } else if ((_current_level_width_in_tiles - DEVICE_SCREEN_WIDTH) > _map_pos_x) {
        _bkg_set_level_submap_banked(_map_pos_x + DEVICE_SCREEN_WIDTH, 0u, 1u, DEVICE_SCREEN_HEIGHT);
    }
    _old_map_pos_x = _map_pos_x;
}
_old_camera_x = _camera_x;
```

**Gating:** This block is emitted ONLY when:
- `platformerCamera { horizontal() }` — `cfg.scrollDirections == HORIZONTAL`
- AND `cfg.mode == SMOOTH_FOLLOW`
- AND `solidThreshold != null` on the game-level physicsConfig (indicating tilemap-collision mode)

---

## D-14 Recommendations (jumpHold lowering)

### Research question 6: Insertion point and gravity gating

**Current `buildPhysicsUpdateFunction` gravity location:** The gravity tick fires at the TOP of
the function body (lines 187-198 in PlatformerVisitor.kt):
```kotlin
CIf(condition = CBinaryExpr(CVar("_plat_vy"), "<", CIntLiteral(cfg.terminalVelocity)),
    thenBody = listOf(CExprStatement(CBinaryExpr(CVar("_plat_vy"), "+=", CLiteral(cfg.gravity)))))
```

**Reference jumpHold pattern (player.c lines 297-317):**
```c
if (!grounded) {
    if (playerJumpIncrease > 0) playerJumpIncrease--;
    if (!((joypadCurrent & J_A || joypadCurrent & J_UP)) || playerJumpIncrease == 0) {
        playerYVelocity += GRAVTY;
        playerJumpIncrease = 0;
    }
}
```

**Translation:** While `_jump_increase_timer > 0` AND `J_A` or `J_UP` is held, SUPPRESS gravity.
When button released OR timer reaches 0, apply gravity normally AND reset timer to 0.

**For the tilemap-collision code path**, `jumpHold` integrates naturally into the extended
`buildPhysicsUpdateFunction` branch (the one with `solidThreshold != null`):

```c
// After jump initiation (playerYVelocity = -PLAYER_CHARACTER_JUMP_VELOCITY):
// _jump_increase_timer = jumpHold;  (set at jump time)

// In the per-frame block when not grounded:
if (_jump_increase_timer > 0u) _jump_increase_timer--;
if (!(button_held(J_A) || button_held(J_UP)) || _jump_increase_timer == 0u) {
    _player_vy += _current_gravity;  // using CLiteral for gravity
    _jump_increase_timer = 0u;
}
```

**D-claude-5 parity:** `buttons.a.pressed` lowers to `button_pressed(J_A)` (edge-triggered).
The jump-HOLD check needs `button_held(J_A)` — which gbkt already emits for `buttons.a.held`.
The reference uses `joypadCurrent & J_A` for hold — same semantic.

**For the abstract physics path (existing):** The existing `variableHeightJump` flag uses
`button_released(J_A) && _plat_vy < 0 → _plat_vy /= 2` pattern. The new `jumpHold(maxFrames)`
is a SEPARATE field only applicable to the tilemap-collision sub-path. Keep both fields
independent: `variableHeightJump` for the abstract path, `jumpHoldMaxFrames` for the tilemap
path. Both can coexist in `PlatformerPhysicsConfig`.

---

## D-15 Recommendations (multi-tileset asset pipeline verification or extension)

### Research question 7: Does the existing pipeline handle the Phase 12 substrate?

**Partial — confirmed gap for shared tilesets.** [VERIFIED: codebase read]

**What works today:**
- `ConvertZoneTilesetsTask` processes ALL zones in `zoneTilesets` from `game_metadata.json`.
- Each zone with `tilesetPath != null` gets its own `_zone_<id>_tileset.c` file via png2asset.
- `allocateZoneBanks` FFD-packs all zones starting at bank 2 — N zones, M banks.
- `buildTilemapBankFiles` skips NEW-path zones (they get their tileset data from ConvertZoneTilesetsTask).

**The gap (shared tileset duplication):**
World1Area1 and World1Area2 share `world1-tileset.png`. The existing pipeline will invoke
png2asset TWICE (once per zone ID), producing:
- `_zone_world1Area1Zone_tileset.c` — identical tile data to world1-tileset
- `_zone_world1Area2Zone_tileset.c` — also world1-tileset data

This doubles the ROM footprint of the shared tileset. For the reference's tileset sizes
(estimated 64-192 tiles × 16 bytes = 1-3KB), duplication adds 1-3KB ROM overhead.

**Two options:**

**(a) Accept duplication:** Simpler. Correct. Two tileset loads per level-enter is wasteful
but the size overhead (~3KB) is well within the 2× ROM size signal threshold. Recommended
for Phase 12 given the integration complexity budget; document as SEED.

**(b) Extend ConvertZoneTilesetsTask for shared-tileset mode:** Zones with identical
`tilesetPath` share a single png2asset output. Requires adding a `sharedTileset` concept
to `ZoneBuilder`/`ZoneIR`/`game_metadata.json`. Significant scope — SEED for Phase 13.

**Recommendation: option (a) for Phase 12.** Add `SEED-PHASE-12-SHARED-TILESET.md` at
close. The D-15 plan slot becomes a verification plan that:
1. Confirms `ConvertZoneTilesetsTask` processes all 3 area zones + title + nextLevel.
2. Documents the tileset duplication.
3. Verifies bank packing stays within 16KB per bank.
4. Creates the seed.

**Title screen + NextLevel card handling:**
These are full-screen background renders using banked tile data. In the gbkt DSL, they would
be modeled as zones with `tilesetPath` = the full-screen PNG. `ConvertZoneTilesetsTask` processes
them. `allocateZoneBanks` assigns them a bank. The tileset load on scene enter uses the
tileset guard + `set_native_tile_data`. Then `set_bkg_tiles` renders the map.

**Gap for menu-screen zones:** The tileset guard in `addTilesetGuardToEnterFunction` is designed
for gameplay tilesets. For a title/nextLevel scene that does NOT have gameplay following it,
the scene enter needs: `DISPLAY_OFF; load_tileset(zone); fill_bkg_rect(0,0,...); set_bkg_tiles(...); DISPLAY_ON`. This is the `ShowCentered()` pattern. The current zone-tileset-load
codegen in `buildSceneFile` generates `set_bkg_data` + `set_bkg_tiles` — which IS the right
pattern. The `fill_bkg_rect` (to blank the background before placing the centered graphic) may
need to be an explicit DSL call or auto-generated for menu-screen zones.

**This is a novel codegen territory** for non-RPG/non-exploration games. Closest existing
pattern: dungeon zone tileset loads (archived). The planner should budget 2 plans for the
banked title+nextLevel card: one for zone definition + tileset pipeline, one for scene-enter
codegen verification.

---

## Metasprite + hflip Reuse (Phase 10 path)

### Research question 8: 6-frame walking + hflip authoring shape

**MetaspriteVisitor REQUIRES NO EXTENSION for D-04.** [VERIFIED: file read]

**Authoring shape (6 frames + `rot` variable for facing):**
```kotlin
var facingRot by u8Var(0)  // 0 = right (no flip), 3 = left (flipX)

val player by metasprite {
    // 6 right-facing frames: idle, walk1, walk2, walk3, jump-up, jump-fall
    frame { tile(...) }  // frame 0: idle
    frame { tile(...) }  // frame 1: walk1
    frame { tile(...) }  // frame 2: walk2
    frame { tile(...) }  // frame 3: walk3
    frame { tile(...) }  // frame 4: jump-up
    frame { tile(...) }  // frame 5: jump-fall
}

// In frame loop:
whenever(dpad.right.held) { facingRot set 0 }
whenever(dpad.left.held) { facingRot set 3 }
var frameIdx by u8Var(0)  // set from walk animation logic

moveMetasprite(player)  // uses facingRot as rotVar, frameIdx as idxVar
```

**How visitor handles this:**
- `rot = 0` (right) → `rot & 0x3 = 0` → default case → `move_metasprite_ex(frames[idx], ...)`
- `rot = 3` (left) → `rot & 0x3 = 3` → case 3 → `move_metasprite_flipx(frames[idx], ...)`
- `rot >> 2 = 0` in both cases → sub-palette 0 (no sub-palette cycling needed for platformer)

**Phase 12 uses `rotVar = "facingRot"` (or Kotlin name: `_facingRot`) not the canonical `_rot`.**
The `moveMetasprite(player)` DSL call must bind the correct var names. Current DSL builder
`moveMetasprite(ref)` passes `posXVar`, `posYVar`, `idxVar`, `rotVar` from the MetaspriteIR
binding. The user can bind via DSL overloads added in Phase 10.1.

**Animation frame index:** The reference uses `threeFrameCounter` with walk/run speed. In gbkt:
```kotlin
var threeFrameCounter by u8Var(0)
var walkFrameIdx by u8Var(0)
// In frame: threeFrameCounter += walkSpeed; if (threeFrameCounter >= 3) { threeFrameCounter set 0; walkFrameIdx++ }
// if (walkFrameIdx >= 3) walkFrameIdx set 0
// if (grounded && vx==0) walkFrameIdx set 0  (idle)
// if (!grounded && vy < 0) walkFrameIdx set 4  (jump-up)
// if (!grounded && vy >= 0) walkFrameIdx set 5  (jump-fall)
```

**hflip sub-palette note:** `rot >> 2 = 0` throughout (no sub-palette cycling). The
`_player_subPalette` global is still written (= 0) — this is harmless.

**WR-05 (hiwater hoist) is already fixed.** Phase 12 inherits the post-10.1-09 state where
`uint8_t hiwater = 0u;` is in the scene frame prelude and `hide_sprites_range(hiwater, ...)` is
in the postlude. Multiple `moveMetasprite()` calls in one frame work correctly.

---

## Banked Tile-Data Screen Codegen (title + NextLevel card)

### Research question 9: Existing path vs novel territory

**Semi-novel — closest precedent is the dungeon zone tileset load, but the full-screen render
is distinct from the tileset guard pattern.** [VERIFIED: codebase analysis]

**What exists:** `addTilesetGuardToEnterFunction` wraps scene enter in:
```c
if (_current_tileset_id != TILESET_<ZONE_ID>) {
    _current_tileset_id = TILESET_<ZONE_ID>;
    _bkg_tiles_load_banked(bank, 0, 0, width, height, _zone_<id>_tileset);
}
```
This loads tile data (the pixels) but does NOT do the full-screen render (fill_bkg_rect + set_bkg_tiles).

**What `ShowCentered()` does additionally:** `fill_bkg_rect(0,0,SCREEN_W,SCREEN_H,0)` to clear
background, then `set_bkg_tiles(col, row, w>>3, h>>3, mapData)` to render the centered graphic.

**For the title/nextLevel scenes**, the scene enter needs to:
1. Load the tileset (tile pixel data) into VRAM.
2. Clear the background to tile 0.
3. Render the map (tile indices) centered on the screen.
4. Wait for input.

The title/nextLevel scenes are FULL-SCREEN — simpler than a centered sub-tile render.
The `set_bkg_submap` or `set_bkg_tiles` call with full 20×18 dimensions covers the whole screen.

**Recommended approach for Phase 12:**
Model the title screen and NextLevel card as zones with `tilesetPath` pointing to the respective
PNGs. The png2asset-processed tileset data lives in a banked file. The scene enter:
1. Uses the tileset guard pattern to load tile data.
2. Emits a `fill_bkg_rect(0u, 0u, 20u, 18u, 0u)` call to clear.
3. Emits a `set_bkg_submap(0u, 0u, 20u, 18u, ...)` call with the map data from the zone's bank.
4. The title scene frame loop calls `WaitForStartOrA()` equivalent — `whenever(buttons.start.pressed)` or `whenever(buttons.a.pressed)`.

**Gap:** The existing zone tileset guard emits `set_bkg_data` (tile pixel load) but not
`fill_bkg_rect` + `set_bkg_submap` (screen render). The scene-enter DSL for the title scene
will need explicit `clear()` + the map-render. The `clear()` call in gbkt lowers to `cls()`
which clears the screen but not the background tile map.

**Resolution for Phase 12:** Add an explicit `bgFill(0)` call in the title scene's `enter { }`
block (or use `raw("fill_bkg_rect(0,0,20,18,0);")`). Then `set_bkg_submap` is called by the
tileset guard implicitly. This may require a `bgSetMap(zone)` DSL primitive to trigger the
submap render from a zone's map data — a candidate for a new 1-plan slot.

**NOTE:** The CLAUDE.md "Window-Layer UI" section explicitly states title screens using
banked tile-data are NOT a window-layer violation — confirmed, this is the correct path.

---

## Cartridge + Bank Layout Prediction (D-claude-3)

### Research question 10: Expected bank count + MBC selection

**Cartridge type:** The reference uses `0x1B = MBC5+RAM+BATTERY`. In gbkt: `"MBC5_RAM_BATTERY"`.
Phase 12 substrate has NO SRAM (no SaveDataBuilder) — so RAM battery is unnecessary. However,
`"MBC1"` (0x01, no RAM, max 16 banks) may be sufficient if zone bank count ≤ 14. [ASSUMED]

**Expected bank layout:**

| Bank | Content |
|------|---------|
| 0 (HOME) | main.c — `is_tile_solid()`, `_bkg_set_level_submap_banked()`, `_bkg_tiles_load_banked()`, `navigate_to_scene()`, `main()` |
| 1 (scenes) | bank1.c — `title_enter/frame`, `gameplay_enter/frame`, `nextLevel_enter/frame` |
| 2 | world1Area1Zone tilemap data (area 1 tile indices) |
| 3 | world1Area1Zone tileset data (world1-tileset pixel bytes) |
| 4 | world1Area2Zone tilemap data (area 2 tile indices, reuses world1 tileset) |
| 5 | world2Area1Zone tilemap + tileset data (area 3 + world2-tileset) |
| 6 | title-screen tileset+map data |
| 7 | nextLevel-card tileset+map data |

**Total: ~8 banks (HOME + scenes + 6 data banks).** MBC1 supports 16 banks — sufficient.

**Correction for shared tileset gap:** With the duplication approach (option a), world1-tileset
data is duplicated in banks 3 and 4 (or 2+3+4 spread differently by FFD). The FFD allocator
may pack world1Area1 + world1Area2 tileset data into the SAME bank if they fit (16KB combined).

**ROM_ONLY (32KB, 2 banks) is NOT viable** — 7+ data units cannot fit in 0+1 banks.
**MBC1 (32KB-2MB, 2-128 banks) IS viable.** Use `"MBC1"` with `romBanks = 8` (or `romBanks = 16`
as safe margin). FFD will tell the actual bank count via the `.noi` file.

**BankingConfig defaults:** CLAUDE.md states: "BankingConfig defaults place all banked code in
bank 1 — suitable for simple games (Pong, Breakout). Complex games with RPG systems should
override via `config { banking { ... } }`". Phase 12 does NOT need to override banking for
code — all game code fits in HOME + bank1. Zone DATA auto-allocates via FFD starting at bank 2.

**RAM banks:** Phase 12 has no SRAM → `ramBanks = 0` (or omit). Use `"MBC1"` not `"MBC1_RAM"`.

---

## Reference ROM Reproducibility (BUILD.md draft)

### Research question 11: Reference ROM build procedure

**Reference location:** `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/`

**Build procedure:**
```bash
# Prerequisites: GBDK-2020 installed with lcc and png2asset
export GBDK_HOME=/usr/local/gbdk-2020  # or wherever GBDK is installed
cd /Users/michalsvacha/gbdk/examples/cross-platform/platformer_template

# Step 1: Generate asset C files
make GBDK_HOME=$GBDK_HOME EXT=gb png2asset

# Step 2: Build ROM
make GBDK_HOME=$GBDK_HOME EXT=gb gb

# Expected outputs:
# build/gb/platformer_template.gb   — 32768+ byte ROM
# build/gb/platformer_template.map  — linker map
# build/gb/platformer_template.noi  — noice debug info (DEF l__CODE_N bank sizes)
# obj/gb/platformer_template.noi    — same
```

**Note:** The Makefile uses `-Wl-yt0x1B` (MBC5+RAM+BATTERY) and `-autobank`. The reference
ROM will be larger than the gbkt ROM because it has 3 levels, player sprites, 2 tilesets,
title screen, and nextLevel card — likely 64-128KB (4-8 banks). [ASSUMED]

**Evidence directory:** `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/reference/`
Files to capture: `platformer_template.gb`, `platformer_template.map`, `platformer_template.noi`
(gitignored, reproducible from BUILD.md).

---

## Validation Architecture

### Research question 12: Per-anchor validation shapes

**Framework:** Gradle test + GbktTestExtension (JVM tier) + MCP `gbkt-emulator` (UAT tier).

**Test config file:** Standard JUnit5 with `GbktTestExtension`. Per TESTING.md pattern.

#### Anchor 1: Title → gameplay scene transition

**Visual truth:** Gameplay scene rendered with tilemap + player visible.
**Screenshot:** Required (`emulator_screenshot()`).
**Variable assertion:** `emulator_assert("_current_scene", SCENE_GAMEPLAY)`.
**JVM emission invariant (D-16 #1):** awk brace-walk `title_frame` function body → grep
`navigate_to_scene` within scope.

Awk pattern template:
```bash
awk '/^void title_frame/{p=1;d=0} p{d+=gsub(/{/,""); d-=gsub(/}/,""); if(d<0)exit} p' main.c \
  | grep 'navigate_to_scene'
```
For the gameplay enter function, verify `setup_current_level` call:
```bash
awk '/^void gameplay_enter/{p=1;d=0} p{d+=gsub(/{/,""); d-=gsub(/}/,""); if(d<0)exit} p' bank1.c \
  | grep 'setup_current_level'
```

#### Anchor 2: Tilemap collision (jump + land)

**Visual truth:** Player standing on a tile; player mid-air mid-jump.
**Screenshots:** 2 captures (grounded, airborne).
**Variable assertions:** `_player_vy` transitions 0 → negative → 0.
**JVM emission invariant (D-16 #2):** awk brace-walk `is_tile_solid` function body in main.c →
grep `SWITCH_ROM` AND `_current_area_bank` AND `_current_level_non_solid_tile_count` within scope.

Awk pattern:
```bash
awk '/^UINT8 is_tile_solid/{p=1;d=0} p{d+=gsub(/{/,""); d-=gsub(/}/,""); if(d<0)exit} p' main.c \
  | grep -c 'SWITCH_ROM'  # expect 2 (entry + exit)
awk '/^UINT8 is_tile_solid/{p=1;d=0} p{d+=gsub(/{/,""); d-=gsub(/}/,""); if(d<0)exit} p' main.c \
  | grep '_current_level_non_solid_tile_count'
```

#### Anchor 3: Horizontal scroll

**Visual truth:** Tilemap content visibly different from initial frame (scrolled).
**Screenshots:** Initial frame + scrolled frame (side-by-side comparison).
**Variable assertions:** `_camera_x > 0`, `_map_pos_x > 0`.
**JVM emission invariant (D-16 #3):** awk brace-walk `platformer_camera_update` body →
grep `set_bkg_submap` AND `_map_pos_x != _old_map_pos_x` within scope.

Awk pattern:
```bash
awk '/^void platformer_camera_update/{p=1;d=0} p{d+=gsub(/{/,""); d-=gsub(/}/,""); if(d<0)exit} p' bank1.c \
  | grep 'set_bkg_submap'  # expect ≥1 match via helper call
awk '/^void platformer_camera_update/{p=1;d=0} p{d+=gsub(/{/,""); d-=gsub(/}/,""); if(d<0)exit} p' bank1.c \
  | grep '_old_map_pos_x'
```

#### Anchor 4: Metasprite animation (multi-frame walking)

**Visual truth:** 3 screenshots over ~6 frames showing pose differences. Plus hflip.
**Variable assertions:** `_walkFrameIdx` cycles 0→1→2→0 while moving right. `_facingRot = 3`
when moving left.
**JVM emission invariant (D-16 #4):** awk brace-walk `gameplay_frame` → grep
`sprite_player_frame_0` through `sprite_player_frame_5` references (pointer table lookup) AND
`move_metasprite_flipx` within scope.

Awk pattern:
```bash
awk '/^void gameplay_frame/{p=1;d=0} p{d+=gsub(/{/,""); d-=gsub(/}/,""); if(d<0)exit} p' bank1.c \
  | grep 'sprite_player_frames\['
awk '/^void gameplay_frame/{p=1;d=0} p{d+=gsub(/{/,""); d-=gsub(/}/,""); if(d<0)exit} p' bank1.c \
  | grep 'move_metasprite_flipx'
```

#### Anchor 5: Level-switch (NextLevel card → level 2)

**Visual truth:** NextLevel card screenshot. Level 2 gameplay screenshot (visibly different tilemap).
**Variable assertions:** `_current_level = 1` (world1Area2), `_next_level = 1`.
**JVM emission invariant (D-16 #5):** awk brace-walk `main` function body in main.c →
grep `_next_level != _current_level` AND `show_centered_nextLevel` (or equivalent) AND
`setup_current_level` within the level-switch guard.

Awk pattern:
```bash
awk '/^void main\(\)/{p=1;d=0} p{d+=gsub(/{/,""); d-=gsub(/}/,""); if(d<0)exit} p' main.c \
  | grep '_next_level'
```

---

## Plan Sizing Reality-Check

### Research question 13: Natural plan boundaries

**Planner under-sizing risk areas:**

| Appears to be 1 plan | Should be N plans |
|----------------------|-------------------|
| "Tilemap collision (D-12)" | 4+ plans: (1) PlatformerPhysicsConfig + jumpHold fields + builder methods, (2) ZoneBuilder re-entrant platformerPhysics + ZoneIR field, (3) PlatformerVisitor new buildIsTileSolidFunction + per-zone config, (4) JVM emission invariant test for is_tile_solid shape |
| "Horizontal scroll (D-13)" | 3 plans: (1) camera vars + _bkg_set_level_submap_banked HOME helper, (2) buildCameraUpdateFunction column-scroll branch, (3) JVM emission invariant test |
| "Variable-height jump (D-14)" | 2 plans: (1) jumpHold field + tilemap-physics jump integration, (2) JVM emission invariant test |
| "D-15 multi-tileset pipeline" | 2 plans: (1) verification run + duplication documentation, (2) seed creation |
| "Banked title + NextLevel screens" | 3 plans: (1) zone DSL for menu screens, (2) scene enter codegen + fill_bkg_rect path, (3) round-trip ROM build verification |
| "3-level substrate + level-switch" | 3 plans: (1) SetupCurrentLevel equivalent codegen, (2) nextLevel counter + level-switch guard in main(), (3) per-level physics config override wiring |
| "5 UAT anchors" | 5 plans (one per anchor, per D-08 requirement) |

**Minimum plan count breakdown:**
1. UAT lock (12-UAT.md + PLAYBOOK.md)
2. Reference ROM build + evidence/reference/ artifacts (D-17a BUILD.md)
3. Project scaffold (gbkt-examples/platformer-template/ + build.gradle.kts + settings.gradle.kts)
4. Asset import (6-frame player metasprite frames transcribed + tilesets linked)
5. PlatformerPhysicsConfig: solidThreshold + jumpHold fields + PlatformerPhysicsBuilder methods
6. ZoneBuilder re-entrant platformerPhysics { } extension + ZoneIR field
7. PlatformerVisitor: buildIsTileSolidFunction + is_tile_solid helper in HOME bank
8. PlatformerVisitor: 5-point bbox probe auto-derivation + tilemap-collision physics branch
9. JVM emission invariant: is_tile_solid shape (D-16 #2)
10. Camera vars (_camera_x UINT16, _map_pos_x/old) + _bkg_set_level_submap_banked HOME helper
11. PlatformerVisitor: buildCameraUpdateFunction column-scroll branch (D-13)
12. JVM emission invariant: column-scroll shape (D-16 #3)
13. jumpHold lowering in tilemap-physics update + gravity gating (D-14)
14. JVM emission invariant: jumpHold gated gravity (D-16 #1 partial)
15. D-15: ConvertZoneTilesetsTask multi-tileset verification + duplication seed
16. 3-level substrate: SetupCurrentLevel equivalent codegen
17. nextLevel counter + level-switch guard + banked NextLevel card scene
18. Banked title screen scene + zone definition + fill_bkg_rect path
19. UAT anchor 1: title→gameplay evidence (screenshot + var assertion)
20. UAT anchor 2: tilemap collision evidence
21. UAT anchor 3: horizontal scroll evidence
22. UAT anchor 4: metasprite animation evidence
23. UAT anchor 5: level-switch evidence
24. 3-signal artifact + bank-layout check (.noi file verification)
25. Retire platformer entries from settings.gradle.kts / archive cleanup (D-03)
26. Phase close: SEED-PHASE-12-ONE-WAY-TILE + SEED-PHASE-12-SHARED-TILESET + Phase 13 edits + conditional Phase 12.1

**Count: 26 plans. Within D-18's 25-32 expected range.**

---

## Anti-Overfitting Blast-Radius Check (D-01 boundary)

### Research question 15: Which surfaces have blast radius beyond Phase 12?

**D-12 (tilemap-collision + solidThreshold + per-level override):**
- Touches: `PlatformerTypes.kt`, `PlatformerBuilders.kt`, `PlatformerExtensions.kt` (ZoneBuilder),
  `PlatformerVisitor.kt`, `WorldBuilders.kt` (ZoneBuilder new method), `WorldIR.kt` (ZoneIR new field).
- Blast radius: 6 files, all within `gbkt-genre-platformer` + `gbkt-lang` + `gbkt-ir`.
- **Assessment: MANAGEABLE within Phase 12.** ZoneIR change is additive (new optional field).
  No existing tests break. The new `platformerPhysicsOverride` field is optional/nullable.

**D-12a (is_tile_solid HOME helper):**
- Touches: `GBDKPipelineV2.kt` (new method `buildIsTileSolidHelperIfNeeded`) + `main.c` output.
- Blast radius: 1 file change in pipeline. New HOME-bank function doesn't affect existing codegen.
- **Assessment: MANAGEABLE within Phase 12.**

**D-13 (horizontal scroll codegen):**
- Touches: `PlatformerVisitor.kt` (buildCameraUpdateFunction branch). Camera vars need UINT16
  `_camera_x` which is a NEW variable name — does NOT conflict with existing `_cam_x` (INT8).
  A new HOME-bank helper `_bkg_set_level_submap_banked()` is added to `GBDKPipelineV2`.
- Blast radius: 2 files. Existing smooth-follow camera codegen unchanged (different code path).
- **Assessment: MANAGEABLE within Phase 12.**

**D-14 (jumpHold lowering):**
- Touches: `PlatformerTypes.kt` (new field), `PlatformerBuilders.kt` (new method),
  `PlatformerVisitor.kt` (extended tilemap-physics branch).
- Blast radius: 3 files, all within `gbkt-genre-platformer`. No effect on abstract physics path.
- **Assessment: MANAGEABLE within Phase 12.**

**D-15 (multi-tileset pipeline):**
- Current `ConvertZoneTilesetsTask` already supports N zones. No extension needed (accepting
  duplication). If shared-tileset mode were added: `ConvertZoneTilesetsTask.kt`,
  `GBDKPipelineV2.kt` (metadata emission), `ZoneIR.kt` (sharedTileset concept).
- **Assessment: SEEDED (SEED-PHASE-12-SHARED-TILESET). D-15 plan = verification only.**
  Not a Phase 12 blast-radius concern.

**Conclusion: ALL 4 named surfaces are manageable within Phase 12. No escalation needed.**
The D-01 cap-lift is justified. None of the 4 surfaces requires changes to:
- `gbkt-ir` sealed interfaces (using non-sealed + Any? config map pattern)
- `gbkt-backend-api` contract (visitor extension, not contract change)
- Any test that exists today (all changes are additive)

---

## Standard Stack

### Core (for Phase 12 test and emission targets)

| Library/Tool | Version | Purpose |
|-------------|---------|---------|
| Kotlin | 2.3.0 | DSL + codegen JVM language | 
| GBDK-2020 | 4.3.x (local) | GBC/GB C compiler (`lcc`, `png2asset`) |
| JUnit5 | via gbkt-test | JVM-tier emission tests |
| GbktTestExtension | in gbkt-test | JVM-tier ROM emission assertions |
| MCP gbkt-emulator | local JAR | UAT anchor screenshot + var assertion |

### Supporting

| File/Pattern | Purpose |
|-------------|---------|
| `ConvertZoneTilesetsTask` | Processes zone tileset PNGs via png2asset |
| `allocateZoneBanks()` | FFD zone-to-bank packing (already handles N zones) |
| `buildBkgTilesLoadBankedHelper()` | Template for D-12a SWITCH_ROM wrapper |
| `MetaspriteVisitor.generateMetaspriteFrameSwitch()` | Reused verbatim for D-04 hflip |

---

## Package Legitimacy Audit

> No new external packages are added in Phase 12. All libraries are existing project dependencies.
> This section is N/A.

---

## Common Pitfalls

### Pitfall 1: Tilemap-physics branch replacing abstract-physics branch

**What goes wrong:** Developer replaces `buildPhysicsUpdateFunction` entirely with the new
tilemap-collision version, breaking games that use the abstract physics path.
**How to avoid:** Gate the tilemap-collision branch on `solidThreshold != null`. Emit the
existing abstract path when `solidThreshold == null`. Both paths must coexist.

### Pitfall 2: SWITCH_ROM from BANKED context in column-scroll

**What goes wrong:** `buildCameraUpdateFunction` emits `SWITCH_ROM(_current_area_bank)` directly
inside the camera update function — which runs from bank1.c (BANKED). After SWITCH_ROM executes,
instruction fetches come from the area's bank (tilemap data bytes), not bank1.c code.
**How to avoid:** ALL SWITCH_ROM calls for zone data access must route through HOME-bank NONBANKED
helpers. The `_bkg_set_level_submap_banked()` helper (HOME bank, bank=0) is mandatory.

### Pitfall 3: `_cam_x (INT8)` overflow for wide levels

**What goes wrong:** Using the existing `_cam_x (INT8)` variable for tilemap camera tracking.
World1Area1 may be 256+ pixels wide; INT8 overflow at 128 pixels causes the camera to wrap.
**How to avoid:** Use `_camera_x (UINT16)` for the tilemap-collision camera path.

### Pitfall 4: ZoneIR `platformerPhysicsOverride` module boundary violation

**What goes wrong:** Adding `PlatformerPhysicsConfig` as a direct ZoneIR field type forces
`gbkt-ir` to depend on `gbkt-genre-platformer` — a module boundary violation (ir is the leaf
module, zero deps on genre packages).
**How to avoid:** Store as `Any?` in ZoneIR (cast in PlatformerVisitor). Alternatively store
as a `Map<String, Int>` of override values.

### Pitfall 5: Shared-tileset duplication bank overflow

**What goes wrong:** World1Area1 and World1Area2 each get their own copy of world1-tileset data.
If both copies + their area maps are packed into the same bank by FFD, they may exceed 16KB.
**How to avoid:** After FFD allocation, verify `.noi` file shows each `DEF l__CODE_N` ≤ 16384.
If overflow: use `zone.bank(N)` override to manually spread zones across banks.

### Pitfall 6: `is_tile_solid` returning TRUE for out-of-bounds wraps around

**What goes wrong:** The reference uses UNSIGNED arithmetic — if `playerRealY < 0` the unsigned
wraps to a large value > `worldMaxRow`, correctly returning TRUE (solid). In gbkt codegen using
INT16 for `playerY`, out-of-bounds detection must handle both positive overflow AND negative
wrap. The existing reference solution handles this correctly.
**How to avoid:** Reproduce the reference's `if (row > worldMaxRow || column >= currentLevelWidthInTiles)` guard verbatim.

### Pitfall 7: Signed literal emission for sub-pixel velocity comparisons

**What goes wrong:** Comparing INT16 `_player_vy` with a literal using `CLiteral(0)` emits
`_player_vy >= 0u` which is always true for signed INT16 in SDCC (unsigned promotion per
Phase 07.9 literal-emission convention).
**How to avoid:** Per CLAUDE.md literal-emission convention: use `CIntLiteral(0)` for the RHS of
signed comparisons (gravity check: `_player_vy < 0` → `CBinaryExpr(CVar("_player_vy"), "<", CIntLiteral(0))`).

---

## Code Examples

Verified patterns from official sources:

### is_tile_solid() HOME-bank NONBANKED helper (target shape)
```c
// Source: player.c + level.c oracle + Phase 07.4-30 SWITCH_ROM pattern [VERIFIED: file read]
UINT8 is_tile_solid(UINT16 world_x, UINT16 world_y) NONBANKED {
    UINT8 _previous_bank = CURRENT_BANK;
    SWITCH_ROM(_current_area_bank);
    UINT16 column = world_x >> 3u;
    UINT16 row = world_y >> 3u;
    if (row > (_current_level_height >> 3u) || column >= _current_level_width_in_tiles) {
        SWITCH_ROM(_previous_bank);
        return TRUE;
    }
    UINT8 tile = _current_level_map[column + row * _current_level_width_in_tiles];
    SWITCH_ROM(_previous_bank);
    return tile < _current_level_non_solid_tile_count;
}
```

### Per-zone physics override DSL authoring shape
```kotlin
// Source: CONTEXT.md D-12 locked decision [VERIFIED: context read]
platformerPhysics {
    gravity(2); jumpForce(8); terminalVelocity(12); jumpHold(20)
    solidThreshold(17)
}
zone("world2Area1Zone") {
    tileset(asset("res/world2-tileset.png"))
    tiles(asset("res/world2-area1.png"))
    platformerPhysics {    // per-level override (re-entrant)
        gravity(3)         // heavier in world 2
        solidThreshold(68) // more solid tiles in world 2 tileset
    }
}
```

### MetaspriteVisitor hflip usage (6-frame path)
```kotlin
// Source: MetaspriteVisitor.generateMetaspriteFrameSwitch case 3 [VERIFIED: file read]
// rot=3 → case 3 → move_metasprite_flipx(sprite_player_frames[idx], 0, subpal, hiwater, x, y)
var facingRot by u8Var(0)  // 0=right (no flip), 3=left (flipX)
whenever(dpad.right.held) { facingRot set 0 }
whenever(dpad.left.held) { facingRot set 3 }
moveMetasprite(player)  // binds facingRot as rotVar
```

### Column-scroll update (target C shape)
```c
// Source: camera.c UpdateCamera() oracle [VERIFIED: file read]
_map_pos_x = (UINT8)(_camera_x >> 3u);
if (_map_pos_x != _old_map_pos_x) {
    if (_camera_x < _old_camera_x) {
        _bkg_set_level_submap_banked(_map_pos_x + 1u, 0u, 1u, DEVICE_SCREEN_HEIGHT);
    } else if ((_current_level_width_in_tiles - DEVICE_SCREEN_WIDTH) > _map_pos_x) {
        _bkg_set_level_submap_banked(_map_pos_x + DEVICE_SCREEN_WIDTH, 0u, 1u, DEVICE_SCREEN_HEIGHT);
    }
    _old_map_pos_x = _map_pos_x;
}
_old_camera_x = _camera_x;
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Monolithic physics in PlatformerVisitor | Abstract `_plat_vy` path + new tilemap-collision path | Phase 12 (adding) | Both paths coexist |
| No tilemap solidity check | `is_tile_solid()` HOME-bank helper | Phase 12 (adding) | Unlocks tilemap platformers |
| Camera `_cam_x (INT8)` only | Add `_camera_x (UINT16)` for wide levels | Phase 12 (adding) | Supports level widths > 127px |
| `platform()` rectangles only | Also `tilemapCollision` + `solidThreshold` | Phase 12 (adding) | Both abstractions coexist |
| `variableHeightJump` flag | Also `jumpHold(maxFrames)` for tilemap path | Phase 12 (adding) | Reference-faithful jump feel |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Reference ROM is ~64-128KB (4-8 banks) | Cartridge section | Bank count prediction may be off; FFD determines actual allocation |
| A2 | World1-tileset + World1Area1 + World2-tileset + World2Area1 + title + nextLevel each fit in 16KB | D-15 | If any asset exceeds 16KB, `allocateZoneBanks` throws; need to split |
| A3 | Phase 12 example needs `"MBC1"` not `"MBC5_RAM_BATTERY"` | Cartridge section | If using MBC1, 128-bank limit is not a concern; but reference uses MBC5 which has identical 16KB bank size |
| A4 | `gbkt-examples/platformer` and `platformer-gbc` are fully absent from settings.gradle.kts | D-03 retirement finding | If still present, removing them must happen as a plan step |
| A5 | The `gbkt-examples/CLAUDE.md` "Adding a New Example" 5-step recipe applies without modification | Project scaffold | If recipe changed in Phase 11.3, planner must adapt |
| A6 | 6 right-facing metasprite frames tile coordinates can be transcribed from png2asset output at build time | D-04 | If player sprites PNG format differs from expected 24x32 @px12,py6 layout, tile coord extraction needs manual work |

---

## Open Questions / Risks the Planner Must Resolve (RESOLVED)

> All open questions in this section were resolved during plan-set design (Phase 12 plans 12-06,
> 12-11, 12-16, 12-17, 12-25, 12-27). The text below preserves each question with the disposition
> appended.

1. **ZoneIR `platformerPhysicsOverride` type choice**
   - What we know: ZoneIR is in `gbkt-ir` (zero deps). Can't import `PlatformerPhysicsConfig`.
   - What's unclear: `Any?` vs `Map<String, Int>` vs a new dedicated interface in `gbkt-ir`.
   - Recommendation: Use `Map<String, Any>` typed as opaque config (same as GenericSystem pattern).
     PlatformerVisitor reads keys: `"gravity"`, `"solidThreshold"`, etc.
   - **(1) RESOLVED: `Map<String, Any>?` per Plan 12-06.** ZoneIR adds an optional
     `platformerPhysicsOverride: Map<String, Any>? = null` field; PlatformerVisitor in Plan 12-11
     reads keys (`"gravity"`, `"solidThreshold"`, `"jumpHoldMaxFrames"`, etc.) and merges per-level
     overrides. The opaque-map approach preserves the gbkt-ir → genre-platformer module boundary
     (no genre type leaks into IR).

2. **Title screen + NextLevel card fill_bkg_rect path**
   - What we know: Current zone-tileset guard emits `set_bkg_data` but not `fill_bkg_rect` + `set_bkg_submap`.
   - What's unclear: Whether a new `bgFill(0)` DSL primitive is needed or raw() is acceptable.
   - Recommendation: Plan 17 (banked title screen) includes a 1-line `raw("fill_bkg_rect(0u,0u,20u,18u,0u);")` as a bridging DSL call, with a Phase 13 tracking note for a proper `bgFill()` primitive.
   - **(2) RESOLVED: `raw("fill_bkg_rect(...)")` bridging in HOME bank per Plan 12-17 Task 1.**
     The bridging raw() call lives in the title/nextLevel scenes' `enter { }` blocks. A proper
     `bgFill()` primitive is deferred to Phase 13 (per D-20 framework-shaping DSL routing) —
     captured as a follow-up note in Plan 12-27 ROADMAP update. Codegen path: HOME-bank (the
     scene-enter prelude runs from bank1.c but the raw call inlines a HOME-resident GBDK helper
     `fill_bkg_rect`, no SWITCH_ROM needed because the helper itself is HOME).

3. **`moveMetasprite(player)` var binding for facingRot and walkFrameIdx**
   - What we know: Phase 10.1 Plan 03 added `posX/posY/idx/rot` binder methods to the metasprite DSL.
   - What's unclear: Whether the binder methods are available in the current DSL or need re-verification.
   - Recommendation: Plan 4 (asset import) verifies the binder surface; if missing, Plan 5 adds them.
   - **(3) RESOLVED: Binders confirmed available in gbkt-genre-platformer DSL surface, verified
     in Plan 12-04 (asset import) and consumed in Plan 12-16 (game-level composition).** The
     `moveMetasprite(player)` call binds `rotVar = "facingRot"` and `idxVar = "walkFrameIdx"` via
     the Phase-10.1-added binder methods. No new binder methods required.

4. **SetupCurrentLevel equivalent codegen placement**
   - What we know: The reference's `SetupCurrentLevel()` is NONBANKED and called from main() HOME context.
   - What's unclear: In gbkt, level-switch logic runs from the main() loop or from scene lifecycle. If gbkt's `main()` template doesn't have a `nextLevel != currentLevel` guard, it must be added to `buildHomeFile`.
   - Recommendation: Build the level-switch guard as part of `buildHomeFile`'s `main()` function assembly (Plan 16), analogous to how `navigate_to_scene()` is built by the pipeline.
   - **(4) RESOLVED: Level-switch guard + setup_current_level emission live in
     `GBDKPipelineV2.buildHomeFile` per Plan 12-17 Task 2.** The new helpers
     `buildMainLoopLevelSwitchGuardIfNeeded` and `buildSetupCurrentLevelFunctionIfNeeded` are gated
     on `gameUsesTilemapCollision(gameIR) == true`. The level-switch JVM-tier emission invariant
     (D-16 #5) is locked by the new Plan 12-09b LevelSwitchEmissionTest.

5. **`platformer/` retirement confirming archival status**
   - What we know: `platformer` and `platformer-gbc` are NOT in `settings.gradle.kts` (already archived in Phase 11.3).
   - What's unclear: Whether the D-03 "retirement" plan should delete `.archive/platformer/` or just note it's already done.
   - Recommendation: D-claude-2 retirement plan = verify settings.gradle.kts confirms absence + add a cleanup note to CHANGELOG. If `.archive/platformer/` should be deleted, do it in the retirement plan.
   - **(5) RESOLVED: Verification-only per Plan 12-25.** The retirement plan confirms
     `settings.gradle.kts` already lacks the entry (no removal step needed) and updates
     `gbkt-examples/CLAUDE.md` + `gbkt-examples/README.md` to reflect the active 8-example set.
     `.archive/platformer/` is NOT deleted (Phase 11.3 archival ledger policy: kept for revival).

6. **SEED-PHASE-12-SHARED-TILESET scope**
   - This research identified a bonus seed not in CONTEXT.md (shared-tileset duplication).
   - Recommendation: Create at phase-close alongside SEED-PHASE-12-ONE-WAY-TILE.md.
   - **(6) RESOLVED: Seed created at phase close per Plan 12-27 Task 2.** Both
     SEED-PHASE-12-ONE-WAY-TILE and SEED-PHASE-12-SHARED-TILESET land in `.planning/seeds/`. The
     JVM-tier marker for shared-tileset duplication lives in Plan 12-15
     `MultiTilesetAllocationTest` (a future dedup fix will need to update that test).

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| GBDK-2020 lcc | ROM build (D-21) | ✓ (at /Users/michalsvacha/gbdk) | 4.x | None — required |
| png2asset | ConvertZoneTilesetsTask | ✓ (bundled with GBDK) | 4.x | None — required |
| MCP gbkt-emulator | UAT anchors 1-5 (D-10) | Depends on `./gradlew :gbkt-mcp-server:shadowJar` | Current | Manual UAT |
| Reference PNG assets | D-claude-7 | ✓ (/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/res/graphics/) | — | — |

---

## Sources

### Primary (HIGH confidence)
- Reference oracle: `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/*.c` — direct file read, verified
- `gbkt-genre-platformer/src/main/kotlin/.../*.kt` — direct file read, all 4 files
- `gbkt-backend-gbdk/.../pipeline/GBDKPipelineV2.kt` — lines 596-720, 1920-1970 direct read
- `gbkt-backend-gbdk/.../visitor/MetaspriteVisitor.kt` — lines 160-325 direct read
- `gbkt-lang/.../WorldBuilders.kt` — ZoneBuilder class direct read
- `gbkt-ir/.../WorldIR.kt` — ZoneIR data class direct read
- `gbkt-gradle-plugin/.../ConvertZoneTilesetsTask.kt` — full direct read
- `gbkt-examples/banks/build/gbkt/generated/main.c` — verified `_bkg_tiles_load_banked` shape
- `settings.gradle.kts` — verified platformer subproject absence
- `12-CONTEXT.md` — Phase decisions, locked/discretion/deferred

### Secondary (MEDIUM confidence)
- `gbkt-examples/metasprites/src/main/kotlin/.../Metasprites.kt` — metasprite authoring pattern
- `gbkt-examples/.archive/dungeon/src/main/kotlin/.../Dungeon.kt` — zone DSL authoring pattern
- `gbkt-examples/.archive/platformer/` — confirmed genre-rectangle only, no zone-data path

---

## Metadata

**Confidence breakdown:**
- Reference Oracle: HIGH — all 5 C files read verbatim
- PlatformerVisitor surface: HIGH — full file read, exact line references
- GBDKPipelineV2 SWITCH_ROM pattern: HIGH — existing helper read + verified in generated output
- MetaspriteVisitor hflip: HIGH — full method read including case 3 flipX
- D-15 pipeline gap: HIGH — ConvertZoneTilesetsTask full read + logic traced
- Bank layout prediction: MEDIUM — estimation; FFD will determine actual allocation
- Cartridge type: MEDIUM — MBC1 sufficient based on bank count estimate; reference uses MBC5

**Research date:** 2026-05-21
**Valid until:** 2026-06-20 (stable framework; 30-day window)

---

## RESEARCH COMPLETE

**Phase:** 12 — Port platformer_template GBDK example to gbkt
**Confidence:** HIGH

### Key Findings

1. **PlatformerVisitor has TWO divergent paths today:** The existing abstract physics path
   (`_plat_vy INT8`, coyote-time, jump-buffer) and the new tilemap-collision path
   (`_player_vy INT16`, sub-pixel `_player_x/y UINT16`, 5-point AABB probe via `is_tile_solid()`,
   `_camera_x UINT16`) are COMPLETELY DIFFERENT codegen shapes and must coexist via a
   `solidThreshold != null` gate. Phase 12 ADDS a parallel path; it does not replace the
   existing one.

2. **D-12a `is_tile_solid()` has a verified template:** The existing `_bkg_tiles_load_banked`
   HOME-bank NONBANKED helper (line 1938 in GBDKPipelineV2, also visible in banks example
   main.c) is the exact pattern for `is_tile_solid()`. Add `_current_area_bank (UINT8)` global,
   build a new `buildIsTileSolidHelperIfNeeded()` method in `buildHomeFile()`.

3. **D-15 has a "just works" path with one known gap:** `ConvertZoneTilesetsTask` already
   handles N zones × M distinct tilesets. The gap: shared tilesets (World1Area1 + World1Area2
   both using world1-tileset.png) get duplicated tile data. Correct but wastes ~1-3KB ROM.
   Accept duplication for Phase 12; create SEED-PHASE-12-SHARED-TILESET.md at close.

4. **MetaspriteVisitor requires NO extension for D-04:** The existing `move_metasprite_flipx`
   case 3 (`rot=3`) handles the 6-frame left-facing hflip path verbatim. User authors 6 frames;
   `facingRot (u8Var)` is 0 for right, 3 for left; bound as `rotVar` in `moveMetasprite()`.

5. **Blast radius is bounded:** All 4 named codegen surfaces (D-12..D-15) stay within
   `gbkt-genre-platformer` + `gbkt-lang` + `gbkt-ir` (additive changes) + `gbkt-backend-gbdk`
   (2 new HOME-bank helpers). No blast-radius escalation required. The D-01 cap-lift is safe.

### File Created
`.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/12-RESEARCH.md`

### Confidence Assessment
| Area | Level | Reason |
|------|-------|--------|
| Reference oracle C shapes | HIGH | All 5 C files read verbatim; line-by-line cited |
| PlatformerVisitor existing surface | HIGH | Full file read; exact methods and line numbers |
| SWITCH_ROM HOME-bank wrapper pattern | HIGH | Verified in existing generated output (banks/main.c) |
| D-15 pipeline capability + gap | HIGH | ConvertZoneTilesetsTask fully read; logic traced |
| Bank layout + cartridge selection | MEDIUM | Estimation; FFD final verdict at buildRom time |

### Open Questions (RESOLVED)
- `platformerPhysicsOverride` ZoneIR type choice → **`Map<String, Any>?` per Plan 12-06.**
- Title screen `fill_bkg_rect` codegen → **`raw("fill_bkg_rect(...)")` bridging per Plan 12-17;
  proper `bgFill()` primitive deferred to Phase 13.**
- `moveMetasprite()` binder availability for `facingRot` / `walkFrameIdx` → **Confirmed available
  in Plan 12-04 + consumed in Plan 12-16.** No new binders required.

### Ready for Planning
Research complete. Planner can now create PLAN.md files. Recommended first plan: UAT lock
(12-UAT.md + PLAYBOOK.md) per D-11. Recommended plan count: 26 plans (within D-18's 25-32 range).
