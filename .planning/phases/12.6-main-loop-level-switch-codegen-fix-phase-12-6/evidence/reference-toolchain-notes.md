# Reference Toolchain Notes — Phase 12.6 Wave 1 Baseline

**Captured:** 2026-05-25
**Plan:** 12.6-01 (Wave 1 baseline capture, Task 2)
**HEAD commit:** 28f2077ffeedcaaf876e112073d9fb037984b21b

**Reference sources read:**

- `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/main.c` (lines 14-93) — `void main(void)` body with the NextLevel card lifecycle (the D-02 behavioral spec)
- `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/player.c` (lines 98-114) — `SetupPlayer(void) BANKED` (the D-08 spawn-value source)
- `/Users/michalsvacha/gbdk/examples/cross-platform/platformer_template/src/level.c` (read for §A2 cross-check — confirms the reference has NO per-level spawn table; spawn is one hard-coded `(40, 40)` for every level)
- `.planning/phases/12.6-main-loop-level-switch-codegen-fix-phase-12-6/12.6-RESEARCH.md` § "User Constraints" (D-02..D-08), § "Open Questions" (#3 — spawn-coordinate discretion call), § "Common Pitfalls" (Pitfalls 1/2/3 — subpixel shift + velocity reset)

**Purpose of this file:** Source-of-truth reference for downstream plans 12.6-02 (D-04 main-loop guard trim), 12.6-05 (D-06 spawn-table emission), 12.6-06 (D-03 levelCardScene helper), 12.6-07 (D-08 platformer-template migration). Re-reading the GBDK examples directory from each executor is wasteful AND risks transcription drift — this file is the canonical extraction.

---

## NextLevel card lifecycle (reference for D-02)

**Reference:** `platformer_template/src/main.c:44-82` (verbatim quote of the lifecycle block):

```c
// if we want to change levels
if(nextLevel!=currentLevel){


    // if we're not starting the game (where currentLevel = 255)
    if(currentLevel!=255){

        ShowCentered(NextLevel_WIDTH,NextLevel_HEIGHT,BANK(NextLevel),NextLevel_tiles,NextLevel_TILE_COUNT,NextLevel_map,NextLevel_palettes);

        WaitForStartOrA();
    }

    // Update what our current level is
    currentLevel=nextLevel;

    DISPLAY_OFF;

    // Setup the new level
    SetupCurrentLevel();

    camera_x=0;

    // Draw the initial area
    // Draw one extra column to avoid a blank row when first scrolling.
    // If scrolling vertically also, you should draw one extra row as well.
    // The platformer template will only scroll horizontally
    SetCurrentLevelSubmap(0,0,DEVICE_SCREEN_WIDTH+1,DEVICE_SCREEN_HEIGHT);

    DISPLAY_ON;

    #if DEVICE_SCREEN_BUFFER_WIDTH == DEVICE_SCREEN_WIDTH
        // On platforms where screen buffer has no more space than physical screen,
        // the next map column will be written to the leftmost screen column.
        // So we blank the leftmost column to hide visual artifacts where possible.
        HIDE_LEFT_COLUMN;
    #endif

    // Setup the player
    SetupPlayer();
}
```

**Sequence summary (the contract gbkt's `levelCardScene` helper mirrors):**

1. **Detect level change** — `if (nextLevel != currentLevel)`. This is the same gate gbkt's `_next_level != _current_level` already emits in `buildMainLoopLevelSwitchGuardIfNeeded`.
2. **Show card art (skip if `currentLevel == 255`, i.e. first boot)** — `ShowCentered(...) → WaitForStartOrA()`. This is a **blocking** wait spanning many frames; vblanks happen during the wait, so the card visually persists.
3. **Atomic level swap** — `currentLevel = nextLevel`.
4. **Suppress glitches** — `DISPLAY_OFF`.
5. **Load tilemap / tileset / objects** — `SetupCurrentLevel()`.
6. **Reset camera** — `camera_x = 0`.
7. **Draw initial submap window** — `SetCurrentLevelSubmap(0, 0, DEVICE_SCREEN_WIDTH+1, DEVICE_SCREEN_HEIGHT)`.
8. **Re-enable display** — `DISPLAY_ON`.
9. **Reset player position + velocity** — `SetupPlayer()`. **Crucially, this runs AFTER `SetupCurrentLevel()`** so the spawn write happens AFTER tilemap load (no order-of-operations ambiguity).

**Key implication for D-02 / D-06:** in gbkt's `setup_current_level()` per-case body (currently `GBDKPipelineV2.kt:2434-2464`), the spawn-table write (`_playerX = ...`, `_playerVx = 0`, etc., per D-06) MUST appear AFTER the existing `_bkg_tiles_load_banked(...)` call (which corresponds to reference's `SetupCurrentLevel()`), so the equivalent of `SetupPlayer()` runs LAST in the per-case body. See Pitfall 1 in 12.6-RESEARCH.md.

**Key implication for D-04:** the reference's level-switch block runs ATOMICALLY inside a single main()-loop iteration **between two `vsync()` calls** (vsync at the top of the `while(1)` loop, then everything else, then back to top). However, the **card-show step** (`ShowCentered → WaitForStartOrA`) is itself blocking and consumes many frames internally — those frames have their own vblanks. So the reference does NOT have the gbkt "same-frame VRAM stomp" defect (DEFECT-1): the card is shown across many frames BEFORE the tilemap load.

gbkt's V1 emission collapses both steps into one frame (`navigate_to_scene(SCENE_NEXTLEVEL)` immediately followed by `setup_current_level()`) with no inter-frame wait. The D-02 / D-04 fix splits these: the `levelCardScene` helper owns the show-card → wait-Start → setup_current_level → navigate-to-gameplay lifecycle ACROSS multiple frames, exactly like the reference.

---

## SetupPlayer() spawn values (reference for D-08)

**Reference:** `platformer_template/src/player.c:98-114` (verbatim quote):

```c
void SetupPlayer(void) BANKED{

    // Player will start at 40,40
    // the playerX and playerY variables are scaled, so we shift to the left by 4
    playerX=40<<4;
    playerY=40<<4;

    playerXVelocity=0;
    playerYVelocity=0;

    UpdatePlayerVRAMTiles();


    SetPlayerPalettes();


}
```

**Critical observation (RESEARCH §A2 cross-check + Open Questions #3):** The reference `SetupPlayer()` writes **`(40, 40)` HARDCODED for ALL levels**. There is NO per-level spawn table in the reference. `level.c`'s `SetupCurrentLevel()` only loads the tilemap; spawn is owned by `SetupPlayer()` and is identical for every level.

This contradicts CONTEXT D-08's literal instruction to "derive [per-level spawn values] from player.c:91-122 — the reference per-level spawn logic." The reference has no such per-level logic. The instruction must be REINTERPRETED.

**Locked recommendation for D-08 (Plan 12.6-07 platformer-template migration):**

> **Use `spawn(40u, 120u)` on all three platformer-template zones** (`world1Area1`, `world1Area2`, `world2Area1`).
>
> - **X = 40 pixels:** matches the reference verbatim. This is the left-edge horizontal spawn the reference uses for every level.
> - **Y = 120 pixels (DEVIATES from reference Y=40):** the reference uses `Y=40` and relies on the player falling several tiles onto the floor (Y=40 is well above the floor row in the reference level layout, and physics handles the descent). gbkt's port does NOT yet replicate that exact "fall-onto-floor" startup pattern — Y=120 places the player directly on the visible ground row (the floor row sits at approximately y≈120-128px in the gbkt platformer-template tilemaps), avoiding a one-frame "in-the-air" appearance and any risk of falling through the floor before physics stabilizes.
>
> The Y=120 deviation is INTENTIONAL and documented here so future readers do not "fix" it back to Y=40 without reading this rationale. If a future plan ports the reference's fall-onto-floor startup behavior verbatim, Y=40 becomes correct again.

**Subpixel shift (Pitfall 2, 12.6-RESEARCH.md):** The reference writes `playerX=40<<4` because `playerX` is INT16 in subpixel form. gbkt's `_playerX` is declared identically (`var playerX by i16Var(80 shl 4)` in `PlatformerTemplate.kt`). The codegen MUST apply the `<<4` shift at C emission time:

```c
_playerX = ((INT16)_level_spawn_x[<idx>]) << 4;
_playerY = ((INT16)_level_spawn_y[<idx>]) << 4;
```

The DSL surface `spawn(40u, 120u)` accepts UByte PIXELS; the codegen applies the `<<4` shift. The DSL never exposes subpixel form to the user. This satisfies CLAUDE.md "Hide C complexity from Kotlin test surface" and `feedback_hide_c_complexity`.

**Velocity reset (Pitfall 3, 12.6-RESEARCH.md):** The reference also writes `playerXVelocity=0; playerYVelocity=0;` immediately after the position write. gbkt's `setup_current_level()` per-case body MUST mirror this: `_playerVx = 0; _playerVy = 0;` (or whatever symbols `tilemap_collision.vxVar` / `vyVar` resolve to). Without this, the level-end trigger re-fires on the first gameplay frame because the player carries level-N velocity into level-N+1 (a documented variant of DEFECT-2).

---

## Subpixel + velocity-reset notes (Pitfalls 2/3)

**Pitfall 2 — Subpixel shift contract:**

- `_playerX` is declared INT16 (`i16Var` delegate in `PlatformerTemplate.kt`).
- All position values stored in `_playerX` / `_playerY` are **subpixel-shifted**: `pixel_value << 4`.
- Reference: `playerX=40<<4;` (player.c:102). `40 << 4 = 640` (subpixels).
- gbkt's DSL surface `spawn(x: UByte, y: UByte)` accepts PIXELS. The codegen MUST emit the `<<4` shift in C:
  ```c
  _playerX = ((INT16)_level_spawn_x[N]) << 4;
  ```
- DO NOT write `_playerX = 40;` directly — that would land the player at subpixel 40 = pixel 2.5 (clipped into top-left corner).

**Pitfall 3 — Velocity-reset contract:**

- After every spawn-position write, the per-case body MUST emit:
  ```c
  _playerVx = 0;
  _playerVy = 0;
  ```
- These are NOT optional. Without them, the player retains level-N's `(vx, vy)` into level-N+1, causing:
  - Same-frame level-end-trigger re-fire (DEFECT-2 root cause variant)
  - Visible "player teleports to spawn, then instantly moves" artifact

- The `vxSym` / `vySym` resolution pattern is: read `vxVar` / `vyVar` from the `tilemap_collision` `GenericSystem.config`. Same pattern PlatformerVisitor uses (kt:553-558).

**Pitfall 1 reminder — order of operations:** the spawn write must come AFTER `_bkg_tiles_load_banked(...)` in the per-case body, mirroring the reference's `SetupPlayer()`-AFTER-`SetupCurrentLevel()` ordering in main.c:60-82. See "NextLevel card lifecycle" section above for the rationale.

---

## Cross-references for downstream plans

| Plan | Decision needs | Source in this file |
|------|----------------|---------------------|
| 12.6-02 (D-04 main-loop guard trim) | Confirms the trimmed guard emits ONLY `navigate_to_scene(SCENE_NEXTLEVEL)`; setup_current_level migrates to levelCardScene Start-press path | § NextLevel card lifecycle, step 5 |
| 12.6-05 (D-06 spawn-table emission) | Spawn-table write order: AFTER `_bkg_tiles_load_banked(...)`, BEFORE case `break` | § NextLevel card lifecycle (implication paragraph) + § Subpixel + velocity-reset notes |
| 12.6-06 (D-03 levelCardScene helper) | Helper lifecycle: enter (paint card art) → frame (wait for Start press) → on press (cEmit `setup_current_level();` then `navigate(gameplay)`) | § NextLevel card lifecycle steps 2-9 |
| 12.6-07 (D-08 platformer-template migration) | spawn(40u, 120u) on all 3 zones; rationale for Y=120 deviation from reference Y=40 | § SetupPlayer() spawn values, "Locked recommendation" block |
| 12.6-08 (D-14 regression sweep) | None (uses sibling file `pre-fix-rom-sha256.txt`) | — |
