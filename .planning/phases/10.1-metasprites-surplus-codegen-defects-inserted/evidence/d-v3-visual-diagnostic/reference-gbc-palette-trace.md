# Reference GBC Palette Init Trace

Date: 2026-05-19
Source: `/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/src/metasprites.c`

## Includes (lines 29-36)

```c
#include <gbdk/platform.h>        // pulls in <gb/gb.h> via the platform multiplexer
#include <gbdk/metasprites.h>     // metasprite_t, move_metasprite_*, metasprite_end
#include <stdint.h>
#include <res/sprite.h>           // sprite_metasprites[], sprite_tiles[], sprite_TILE_H
```

NOTE: `cgb_compatibility()` is declared by `<gb/cgb.h>` which `<gbdk/platform.h>`
transitively includes when `GAMEBOY` is defined.

## Static Palette Data (lines 142-157)

Four immutable 4-color GBC sprite palettes, each declared with `const palette_color_t`:

```c
const palette_color_t gray_pal[4] = {
    RGB8(255,255,255),   // white
    RGB8(170,170,170),
    RGB8(85,85,85),
    RGB8(0,0,0) };       // black

const palette_color_t pink_pal[4] = {
    RGB8(255,255,255),
    RGB8(255,0,255),
    RGB8(170,0,170),
    RGB8(85,0,85) };

const palette_color_t cyan_pal[4] = {
    RGB8(255,255,255),
    RGB8(85,255,255),
    RGB8(0,170,170),
    RGB8(0,85,85) };

const palette_color_t green_pal[4] = {
    RGB8(255,255,255),
    RGB8(170,255,170),
    RGB8(0,170,0),
    RGB8(0,85,0) };
```

`RGB8(r,g,b)` macro folds RGB888 to GBC RGB555 (5 bits per channel, 15-bit BGR).
Resulting words match the port's pre-folded `palette_color_t[4]` arrays at
`main.c:33-36` exactly:

| Palette | Reference RGB8 fold | Port pre-folded |
|---------|---------------------|-----------------|
| gray    | (255,255,255)→0x7FFF, (170,170,170)→0x56B5, (85,85,85)→0x294A, (0,0,0)→0x0000 | `{0x7FFF, 0x56B5, 0x294A, 0x0000}` MATCH |
| pink    | (255,255,255)→0x7FFF, (255,0,255)→0x7C1F, (170,0,170)→0x5415, (85,0,85)→0x280A | `{0x7FFF, 0x7C1F, 0x5415, 0x280A}` MATCH |
| cyan    | (255,255,255)→0x7FFF, (85,255,255)→0x7FEA, (0,170,170)→0x56A0, (0,85,85)→0x2940 | `{0x7FFF, 0x7FEA, 0x56A0, 0x2940}` MATCH |
| green   | (255,255,255)→0x7FFF, (170,255,170)→0x57F5, (0,170,0)→0x02A0, (0,85,0)→0x0140 | `{0x7FFF, 0x57F5, 0x02A0, 0x0140}` MATCH |

Both sides agree on palette data byte-for-byte. **Palette content is not the bug.**

## `main()` Bootstrap Sequence (lines 160-194)

```c
void main(void) {
    DISPLAY_OFF;                                          // line 161  ← STEP 1

#if defined(GAMEBOY)
    cgb_compatibility();                                  // line 164  ← STEP 2
    set_sprite_palette(OAMF_CGB_PAL0, 1, gray_pal);       // line 165  ← STEP 3
    set_sprite_palette(OAMF_CGB_PAL1, 1, pink_pal);       // line 166
    set_sprite_palette(OAMF_CGB_PAL2, 1, cyan_pal);       // line 167
    set_sprite_palette(OAMF_CGB_PAL3, 1, green_pal);      // line 168
#elif defined(NINTENDO_NES)
    set_sprite_palette(0, 1, gray_pal);
    /* ... */
#endif

    // Fill the screen background with a single tile pattern
    fill_bkg_rect(0, 0, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0);  // line 177  ← STEP 4

    // Set tile data for background
    set_bkg_data(0, 1, pattern);                          // line 180  ← STEP 5

    // Load (and flip) sprite tile data
    load_and_duplicate_sprite_tile_data();                // line 183  ← STEP 6

    // Show bkg and sprites
    SHOW_BKG; SHOW_SPRITES;                               // line 186  ← STEP 7

    // Check what size hardware sprite the metasprite is using (from sprite.h)
    #if sprite_TILE_H == 16
        SPRITES_8x16;
    #else
        SPRITES_8x8;                                      // line 192  ← STEP 8
    #endif
    DISPLAY_ON;                                           // line 194  ← STEP 9

    // ... game loop ...
}
```

### Critical Ordering Properties

1. **`DISPLAY_OFF` is the FIRST statement** (step 1). This guarantees the LCD/PPU is OFF
   before any palette / VRAM writes. On a fresh DMG/GBC boot the LCD is typically already
   OFF (BIOS hands off with LCD off after logo), but the macro guards against any prior
   firmware state that might have left LCD on.

2. **`cgb_compatibility()` runs while LCD is OFF** (step 2). This is the GBDK convention
   for switching the hardware from DMG-compat mode to native CGB mode and for installing
   the default DMG-fallback palette. The function may write to CGB palette RAM internally;
   doing so while LCD is OFF avoids any VRAM-access-mode contention.

3. **All four `set_sprite_palette()` calls run while LCD is OFF** (step 3). The calls
   write to GBC OCPD palette RAM (4 slots × 4 colors × 2 bytes = 32 bytes per call).
   With LCD OFF, the writes complete unconditionally.

4. **BG tile + tilemap data load while LCD is OFF** (steps 4-5). `fill_bkg_rect` writes
   to the BG tilemap region of VRAM; `set_bkg_data` writes to the BG tile data region.
   Both safe under LCD-off.

5. **Sprite tile data + duplicate-flip variants load while LCD is OFF** (step 6).
   `load_and_duplicate_sprite_tile_data()` calls `set_sprite_data` 4× per tile
   (one normal + 3 flip variants for platforms without HW flip).

6. **`SHOW_BKG; SHOW_SPRITES;` enables LCDC layer bits** (step 7). Still LCD OFF.

7. **`SPRITES_8x8;` (or 8x16) sets the LCDC.OBJ_SIZE bit** (step 8). Must be set BEFORE
   `DISPLAY_ON` because the PPU latches LCDC on each scanline.

8. **`DISPLAY_ON;` is the LAST bootstrap macro** (step 9). When the LCD turns on, all
   palettes, all VRAM tile data, all BG tilemap data, and all LCDC layer bits are
   already in their final state. The first composited frame is correct.

### Why This Order Matters for GBC

GBC palette RAM (`BCPS_REG/BCPD_REG` for BG, `OCPS_REG/OCPD_REG` for sprites) is
accessible by the CPU only during VBlank/HBlank when LCD is ON, but unconditionally
accessible when LCD is OFF. `set_sprite_palette()` is implemented as a busy-loop
that polls `STAT_REG` and writes when safe — but the busy-loop's correctness
depends on the LCD being either ON-with-vblank-stalls OR OFF.

When `set_sprite_palette()` runs with LCD ON and outside a vblank window, the
palette write may stall or partially complete. The reference avoids this entirely
by writing palettes only while LCD is OFF.

## `main()` Game Loop (lines 203-308)

After bootstrap, the game loop runs `KEY_INPUT;` → physics → animation idx/rot updates
→ `move_metasprite_*` switch → decel → `vsync()`. No palette writes inside the loop.
The 4 sprite palettes set in steps 3-6 above are static for the entire program
duration. `rot >> 2` (the `subpal` index) is passed to `move_metasprite_*` as the
`base_props` parameter (OAM attribute byte's CGB-palette field, bits 0-2). This
selects WHICH of the 4 pre-loaded palettes the OAM entry references — it does NOT
overwrite palette RAM.

## Key Takeaways

1. **Palette content is identical between port and reference** — falsified hypothesis (a).
2. **Reference loads palettes BEFORE `DISPLAY_ON` while LCD is OFF**, ensuring the
   palette writes complete before the first PPU frame.
3. **Reference's `DISPLAY_OFF` at step 1** is the explicit guard for safe palette/VRAM
   writes. The port does not emit this.
4. **`OAMF_CGB_PAL0..3` macros** (reference lines 165-168) are simple aliases for
   `0`, `1`, `2`, `3` and behave identically to the port's literal `0u, 1u, 2u, 3u`
   — no semantic difference.
5. **`rot >> 2` → `subpal` → `move_metasprite_*(... , subpal, ...)`** is the same in
   both — selects which of 4 pre-loaded palette slots OAM references.
