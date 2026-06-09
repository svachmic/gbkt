# C Diff — metasprites.c vs gbkt-generated main.c (Phase 10)

## Purpose

This document is the D-11 signal-2 (C-diff) **informational** appendix for Phase 10's
codegen-quality oracle. It is **NOT** a parity contract — `metasprites.c` is the
correctness oracle, not a DSL style template.

Where gbkt-generated C is **shorter/clearer** than the equivalent hand-written GBDK C,
that is the "DSL value" signal. Where gbkt is **longer or structurally different**, the
rationale is documented inline; genuine over-emission discoveries become seed candidates
(see §Seed candidates below).

## Methodology

- **Left-hand side (reference):**
  `/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/src/metasprites.c`
  — 309 lines; hand-written GBDK C; single `main()` with a `while(TRUE)` game loop.
  Responsibilities: sprite-flip tile duplication helper (`set_tile`, `get_tile_offset`,
  `load_and_duplicate_sprite_tile_data`), four GBC sub-palettes, background fill,
  main loop with D-pad accel/clamp, B-press animation cycle, A-press flip/palette
  rotation, position integration, `move_metasprite_*` switch, hiwater OAM hide,
  deceleration ladder, `vsync()`.
- **Right-hand side (gbkt):**
  - `gbkt-examples/metasprites/build/gbkt/generated/main.c` — 328 lines;
    HOME-bank scaffolding (globals, metasprite tables, palette constants, input
    helpers, timing helpers, OAM helpers, sound driver, dialog helpers, fade helpers,
    scene dispatcher, `main()`) **plus** the scene bodies (`play_enter`, `play_frame`)
    inlined at the bottom — no bank1.c because this is a single-scene `ROM_ONLY`
    game whose code fits entirely in HOME.
  - No `bank1.c` — confirmed by `ls build/gbkt/generated/` (only `main.c`, `game.h`,
    `game_metadata.json`, `sprites/elephant.h`).
  - Rebuilt via Plan 15's third clean `buildRom` run (logged in `third-build-log.txt`).
- **Active-code scope:** The primary diff target is the per-frame game logic body
  (`metasprites.c` L203-309 inside `main()` vs `main.c` L251-328 `play_frame()`)
  and the one-shot init (`metasprites.c` L160-202 in `main()` vs `main.c` L232-249
  `play_enter()`). gbkt's HOME-bank boilerplate (L63-231) is acknowledged structurally
  but not line-by-line diffed.

## Reference C summary

`metasprites.c` is 309 lines. Responsibilities:

- **State variables** (L55-64): six global state vars (`PosX`, `PosY`, `SpdX`, `SpdY`,
  `PosF`, `idx`, `rot`, `joyp`, `old_joyp`) plus input-macro defs.
- **Tile-flip infrastructure** (L71-140): `reverse_bits[256]` LUT, `set_tile()` helper
  (flips a 2BPP tile in X/Y using the LUT and calls `set_sprite_data()`),
  `get_tile_offset()` (computes VRAM tile offset for pre-flipped duplicates),
  `load_and_duplicate_sprite_tile_data()` (uploads all tiles + their flip duplicates).
- **GBC palette constants** (L142-157): four `palette_color_t[4]` arrays defined with
  `RGB8(r,g,b)` macros.
- **`main()` init** (L160-201): `DISPLAY_OFF`, `cgb_compatibility()`, four
  `set_sprite_palette()` calls, `fill_bkg_rect()`, `set_bkg_data()`, tile-data load,
  `SHOW_BKG/SHOW_SPRITES`, `SPRITES_8x8/8x16` conditional, `DISPLAY_ON`, position
  reset, `idx=0; rot=0`.
- **Main game loop** (L203-308): per-frame D-pad acceleration (Y/X axes with
  ±32 clamp), `PosF` flags, B-press for animation cycle (`idx` wrap), A-press for
  flip/sub-pal rotation (`rot &= 0xF`), position integration, `hiwater` init,
  `switch(rot & 0x3)` dispatching four `move_metasprite_*` variants with
  `get_tile_offset()` arguments, `hide_sprites_range()`, deceleration ladder
  (if/else-if form), `vsync()`.

## gbkt-generated C summary

### main.c (328 lines, single file — no bank1.c)

**Lines 1-60:** File header, includes (`gb.h`, `stdio.h`, `stdlib.h`, `gbdk/console.h`,
`game.h`, `gb/cgb.h`, `gbdk/metasprites.h`, `sprites/elephant.h`), scene enum
`SCENE_PLAY = 0`, global state variables (13 vars: `_elephant_flipX/flipY/subPalette`,
`_posX/posY/spdX/spdY`, `_idx`, `_rot`, `__joypad/joypad_prev`, `_dialog_speed`,
`current_scene`, `_wait_counter`, `_current_tileset_id`), four `palette_color_t[4]`
constants with pre-computed 15-bit GBC color literals (not `RGB8()` macros).

**Lines 38-59:** Five `sprite_metasprite_N[]` arrays and the `sprite_metasprites[]`
pointer array — the metasprite frame table, inlined directly into main.c. The reference
gets this from the `png2asset`-generated `sprite.h`/`sprite.c` side channel.

**Lines 63-230:** HOME-bank scaffolding helpers — same pattern as Phase 9's
`simple-physics` port: `update_joypad()`, `button_pressed/held/released()`,
`dpad_any()`, `delay_frames()`, `hide_sprites_range()`, `show_sprites_range()`,
`update_sprites()`, `sound_driver_update()`, `play_sound()`, `_win_print_at()`,
`_win_clear_region()`, `_win_fill_screen()`, `fade_out()`, `fade_in()`,
`navigate_to_scene()`. These are framework scaffolding emitted unconditionally;
the game uses only a small subset.

**Lines 208-229:** `main()` — gbkt's entry point: `cgb_compatibility()`, sound
register init, `DISPLAY_ON`, `SHOW_BKG/SHOW_SPRITES`, `set_sprite_data(0u, 48u,
elephant_tiles)`, `play_enter()`, then `while(1)` loop with `update_joypad()`,
`switch(current_scene)`, `update_sprites()`, `sound_driver_update()`,
`wait_vbl_done()`.

**Lines 232-249:** `play_enter()` — scene init body: four `set_sprite_palette()` calls,
`SHOW_SPRITES`, checkerboard BG fill + set, position/speed reset to initial values.

**Lines 251-328:** `play_frame()` — per-frame game logic.

## Side-by-side qualitative comparison

| Reference responsibility | Reference C location | gbkt equivalent | Shorter/Longer? |
|--------------------------|----------------------|-----------------|-----------------|
| State variables | L55-64 (globals) | `main.c` L14-31 (globals) | gbkt LONGER (13 globals vs 8; gbkt adds scaffolding-owned vars: `__joypad_prev`, `_dialog_speed`, `_wait_counter`, `_current_tileset_id`, `_sound_*`) |
| Joypad polling (`KEY_INPUT` macro) | Macro-defined + inline in loop | `update_joypad()` + `button_held/pressed()` helpers (L63-76) | gbkt SHORTER at user surface (zero DSL lines; framework emits helpers) |
| GBC palette constants | `RGB8()` macros, 4 × 4 entries (L142-157) | Pre-computed 15-bit literals, 4 × 4 entries (L33-36) | gbkt EQUAL (line count parity; gbkt pre-computes at codegen time rather than at runtime macro expansion — functionally equivalent) |
| Tile-flip infrastructure (`set_tile`, `get_tile_offset`, `load_and_duplicate_sprite_tile_data`, `reverse_bits[256]`) | L71-140 (70 lines) | **ABSENT** — gbkt uses GBC hardware flip bits (target is GBC_COMPATIBLE; `HARDWARE_SPRITE_CAN_FLIP_X/Y = 1`); no software flip duplication needed | gbkt DRAMATICALLY SHORTER (0 lines vs 70 lines of flip infrastructure; GBC hardware rendering obviates the entire LUT + duplication pipeline) |
| Sprite tile load (`load_and_duplicate_sprite_tile_data()`) | L183 (1 call + 70 lines of impl) | `set_sprite_data(0u, 48u, elephant_tiles)` in `main()` L216 | gbkt EQUAL at call site; shorter overall (no duplication logic needed) |
| BG fill + checkerboard tile load | L177-180 (2 calls) | `fill_bkg_rect(0,0,...,0)` + `set_bkg_data(0,1,_checkerboard_bg_pattern)` in `play_enter()` L241-242 | gbkt EQUAL (same two calls) |
| GBC palette init (4 palettes) | L163-168 (4 `set_sprite_palette()`) | L233-236 in `play_enter()` (4 `set_sprite_palette()`) | gbkt LONGER (bug: all 4 calls pass slot `0u`; reference correctly passes `OAMF_CGB_PAL0..3`; see seed candidate #1) |
| Metasprite frame tables | From `png2asset` `sprite.h/sprite.c` (external) | Inlined `sprite_metasprite_0..4[]` arrays + `sprite_metasprites[]` (L38-59) | gbkt LONGER in main.c but SAME total (content was in `sprite.c`; gbkt inlines rather than linking a separate TU — no functional difference) |
| D-pad acceleration with ±32 clamp | L209-227 (4 axis blocks) | `play_frame()` L252-275 (4 axis blocks) | gbkt EQUAL (line-count parity; if/then structure mirrors reference; independent `if` blocks for L/R rather than `else if`) |
| B-press animation cycle | L230-231 (2 lines) | L276-280 (5 lines) | gbkt LONGER (explicit `if (_idx >= 5u)` vs reference's bitmask `if (idx >= sizeof(sprite_metasprites) >> 1)`) |
| A-press rotation cycle | L234-235 (2 lines) | L281-283 (3 lines) | gbkt EQUAL/SLIGHTLY LONGER (3 lines vs 2; ref uses `rot++ ; rot &= 0xF`, gbkt uses two separate statements) |
| Position integration | L239 (1 comma-expression) | L285-286 (2 lines) | gbkt EQUAL (splits comma-expression for clarity — same pattern as Phase 9) |
| `move_metasprite_*` switch | L241-284 (switch with 4 cases) | L288-313 (switch with 4 cases) | gbkt EQUAL (structural parity; both use same 4-case switch; gbkt uses fixed `base_tile = 0` because GBC hardware handles flipping, no `get_tile_offset()` call needed) |
| `hide_sprites_range()` call | L287 | L313 | gbkt EQUAL (1 line each) |
| Deceleration ladder (Y axis) | L289-295 (`if/else if` 1-line form) | L316-321 (two separate `if` blocks) | gbkt LONGER (4 lines vs 2; same rationale as Phase 9 decel ladder — DSL uses independent `whenever` blocks; observable behavior identical) |
| Deceleration ladder (X axis) | L298-304 (`if/else if` 1-line form) | L322-327 (two separate `if` blocks) | gbkt LONGER (4 lines vs 2; same rationale) |
| `vsync()` | L307 | `wait_vbl_done()` in `main()` L228 | gbkt EQUAL (framework-owned; zero DSL lines) |
| Scene dispatcher + frame loop | **ABSENT** (single `main()` loop) | `navigate_to_scene()`, `switch(current_scene)` in `while(1)` (L198-228) | gbkt LONGER (framework scaffolding; same structural explanation as Phase 9) |

## Shorter/clearer regions (DSL value signal)

- **Tile-flip infrastructure eliminated (70 lines → 0):** The reference's
  `reverse_bits[256]` LUT, `set_tile()` helper, `get_tile_offset()`, and
  `load_and_duplicate_sprite_tile_data()` — 70 lines of software flip machinery —
  are completely absent from the gbkt port. The GBC_COMPATIBLE target uses hardware
  flip bits (`HARDWARE_SPRITE_CAN_FLIP_X/Y = 1`), making software duplication
  unnecessary. gbkt's `moveMetasprite(elephant)` DSL call emits the correct
  `move_metasprite_flipx/flipy/flipxy` variants directly. This is the largest
  single advantage: gbkt eliminates ~23% of the reference's line count automatically
  by targeting GBC-compatible hardware.

- **Asset pipeline (PNG → tiles, no manual hex):** gbkt's `png2asset` conversion
  is pipeline-driven; the DSL references `asset("sprites/elephant.png")` and the
  pipeline produces `sprites/elephant.h` containing `elephant_tiles`. The reference
  needs the user to run `png2asset` manually and include the generated `sprite.h`.
  From the user's perspective, the DSL is zero-lines for asset management.

- **Joypad polling (zero DSL lines):** `update_joypad()` + `button_held/pressed()`
  are framework-emitted HOME helpers. The user writes `whenever(dpad.up.held)` and
  `whenever(buttons.b.pressed)` — no manual `joyp` / `old_joyp` management.

- **Frame loop / vsync (zero DSL lines):** `frame { }` lifecycle, `wait_vbl_done()`,
  `update_sprites()`, `sound_driver_update()` are all framework-emitted.

- **`move_metasprite_*` call site cleaner:** gbkt emits `move_metasprite_flipy(
  sprite_metasprites[_idx], 0, subpal, hiwater, ...)` with `base_tile = 0`
  (hardware flip) vs reference's `get_tile_offset(0, 1)` VRAM arithmetic. The
  gbkt call site is shorter and has no VRAM arithmetic.

## Equal regions

- BG fill + checkerboard tile set: structurally identical.
- D-pad acceleration + clamp (4 axis blocks): line-count parity, same structure.
- `move_metasprite_*` switch body: structural parity (4 cases, same functions).
- `hide_sprites_range()` call: 1-for-1.
- GBC palette constants: equal line count (different literal form — `RGB8()` macros
  vs pre-computed 15-bit values; functionally equivalent).

## Longer regions and rationale

- **HOME-bank scaffolding (~130 lines):** Same structural pattern as Phase 9.
  `update_joypad()`, OAM sync, sound driver, dialog helpers, fade helpers,
  scene dispatcher. Reference has none. Framework's value-add; not a defect.

- **GBC palette slot bug (seed candidate #1):** `play_enter()` calls
  `set_sprite_palette(0u, 1u, gray_pal); set_sprite_palette(0u, 1u, pink_pal);
  set_sprite_palette(0u, 1u, cyan_pal); set_sprite_palette(0u, 1u, green_pal)`.
  All four calls use slot `0u`. The reference uses `OAMF_CGB_PAL0..3` (slots 0-3).
  Only the LAST palette (green) is loaded into slot 0; the other three are
  overwritten. This means the sub-palette cycling (`rot >> 2` → 0-3) will
  always reference only the green palette for slots 0 and the gray palette
  will be missing. This is a **codegen bug** — the palette slot argument is not
  being incremented across the four emitted calls.

- **Deceleration ladder (2 axes, 4 lines each vs 2):** Same rationale as Phase 9.
  DSL uses two independent `whenever` blocks per axis for explicit mutual exclusion.
  Observable behavior matches reference. Cosmetic/structural, not a defect.

- **B-press animation cycle (5 lines vs 2):** gbkt uses an explicit `if (_idx >= 5u)`
  comparison against a hardcoded count. The reference uses
  `sizeof(sprite_metasprites) >> 1` (pointer-count idiom). gbkt's form is
  semantically correct but less robust to metasprite count changes (the `5` is
  hardcoded from DSL-time frame count). Minor stylistic difference.

- **Metasprite tables inlined in main.c:** gbkt inlines the five
  `sprite_metasprite_N[]` tables and the `sprite_metasprites[]` pointer array
  directly into `main.c`. These 22 lines appear as gbkt overhead but are
  functionally equivalent to the reference's `png2asset`-generated `sprite.c`
  translation unit. The content is identical; the split across files differs.

## Verdict

The gbkt-generated C for `metasprites` is **shorter overall when considering
the user-authored surface** — the DSL's primary win is eliminating the entire
70-line tile-flip infrastructure that the reference requires for non-GBC DMG
targets. By targeting `GBC_COMPATIBLE`, gbkt's `moveMetasprite()` call emits
hardware flip calls directly, removing software VRAM duplication entirely.

The generated `main.c` is 328 lines vs reference's 309 lines — a +19 line delta.
However, ~130 of gbkt's lines are HOME-bank scaffolding that the reference lacks
entirely (joypad helpers, OAM sync, sound driver, dialog/fade scaffolding). The
active game-logic surface (`play_enter` + `play_frame`, ~100 lines) is **shorter
than the reference's equivalent sections** (init + loop body, ~140 lines) primarily
because gbkt needs no flip infrastructure, and the DSL abstracts the frame loop.

**One materially worse area surfaces as a seed candidate:** the GBC palette slot
numbering bug (all four palettes load into slot 0). This is the single concrete
area where the generated C is functionally incorrect (not just longer/different
style) relative to the reference.

## Seed candidates

1. **GBC palette slot numbering bug:** `play_enter()` calls all four
   `set_sprite_palette()` with slot argument `0u`. Correct behavior requires
   slot 0..3 respectively. The codegen for `SpritePaletteIR` / `SetSpritePalette`
   op does not increment the slot index across repeated palette calls.
   **Impact:** sub-palette cycling (`rot >> 2` selecting palette 0-3) will show
   all four "palettes" as the same color because only slot 0 is populated.
   **Scope:** `SpritePaletteVisitor` or equivalent in `gbkt-backend-gbdk`.

2. **Hardcoded frame-count comparison (`_idx >= 5u`):** The B-press animation
   cycle compares against a literal `5` rather than using a `sizeof`-style
   idiom. If the frame count changes in the DSL, the codegen will need to be
   regenerated to pick up the new count — it cannot be computed at link time
   from the metasprite pointer table size. Low priority (DSL regeneration is
   the normal workflow); but the reference's `sizeof` form is more robust.

3. **Unused HOME scaffolding emitted unconditionally:** `delay_frames()`,
   `show_sprites_range()`, `_win_*` dialog helpers, `fade_out/in()`, and
   `play_sound()` are emitted even though the metasprites game uses none of
   them. This contributes to the +383-byte `l__CODE` delta. Future: dead-code
   elimination pass to strip unreferenced framework helpers.
