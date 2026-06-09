# Phase 10 — Asset Spec: 5-Frame Elephant Sprite

**Status:** Locked before DSL is written (D-03 / Plan 01)
**Source:** `/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/res/sprite.png`

---

## Source PNG

| Property | Value |
|----------|-------|
| File | `/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/res/sprite.png` |
| Dimensions | 64 × 240 pixels |
| Frame layout | 5 frames stacked vertically |
| Frame dimensions | 64 × 48 pixels each |
| Tile size | 8 × 8 pixels |
| Tile grid per frame | 8 columns × 6 rows = 48 tile cells total |

---

## Frame Layout

```
Y offset   Frame
0–47       Frame 0 (64×48 px)
48–95      Frame 1 (64×48 px)
96–143     Frame 2 (64×48 px)
144–191    Frame 3 (64×48 px)
192–239    Frame 4 (64×48 px)
```

---

## Non-empty Tiles per Frame

| Frame | Non-empty tiles | Notes |
|-------|----------------|-------|
| 0 | 31 | Smallest tile count |
| 1 | 33 | |
| 2 | 33 | |
| 3 | 32 | |
| 4 | 32 | |

Total unique non-empty tiles across all 5 frames requires reading the `png2asset` output
to determine VRAM slot allocation. The port assembly plan (Plan 10-13) runs `png2asset`
and reads the generated `sprite_metasprites[]` descriptor arrays to get exact tile counts
and coordinates.

**Why variable tile counts matter:** GBDK's `move_metasprite_*()` functions use the
`{metasprite_end}` sentinel to detect end-of-frame. Each frame can have a different
number of hardware sprite slots. The hiwater variable tracks how many hardware sprites
were consumed by the current frame and drives `hide_sprites_range(hiwater, MAX)` to
hide the OAM tail — this prevents ghost sprites from prior frames bleeding through
when the current frame uses fewer hardware sprites.

---

## png2asset Invocation

Extracted from `/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/Makefile`
line 73:

```bash
$(PNG2ASSET) $< -sh 48 -spr8x8 -noflip -c $@
```

Expanded:
```bash
# GBDK_HOME must be set (e.g., /Users/michalsvacha/gbdk)
$GBDK_HOME/bin/png2asset res/sprite.png \
    -sh 48 \       # sprite height = 48 pixels per frame
    -spr8x8 \      # use 8×8 hardware sprites (not 8×16)
    -noflip \      # do not generate pre-flipped tile duplicates (hardware flip used)
    -c obj/gb/res/sprite.c
```

The `-noflip` flag is critical: it tells `png2asset` NOT to upload pre-flipped tile
variants. The gbkt port relies on hardware OAM flip (`OAMF_X_FLIP`, `OAMF_Y_FLIP`)
instead — matching the GBC-compatible hardware feature. This is consistent with
D-overfitting-3 (reject the `#if HARDWARE_SPRITE_CAN_FLIP_*` fallback path).

---

## Generated C Shape (Reference)

Running `png2asset` produces `sprite.h` and `sprite.c` in `obj/gb/res/`. The relevant
symbols are:

```c
// sprite.h
extern const uint8_t sprite_tiles[];           // Raw 2bpp tile data
extern const METASPRITE_DEF sprite_metasprite_0[];  // Frame 0 OAM entries
extern const METASPRITE_DEF sprite_metasprite_1[];  // Frame 1 OAM entries
// ... (5 total)
extern const METASPRITE_DEF* const sprite_metasprites[];  // Array of frame pointers
extern const uint8_t sprite_TILE_COUNT;        // Total tiles loaded
```

Each `METASPRITE_DEF` entry is a struct `{int8_t dy; int8_t dx; uint8_t dtile;}`:
- `dy` — Y offset from metasprite origin (hardware sprite pixel offset)
- `dx` — X offset from metasprite origin (hardware sprite pixel offset)
- `dtile` — tile VRAM offset (added to `TILE_NUM_START` base tile)
- Sentinel: `{0x80, 0x80, 0x00}` (the `metasprite_end` macro)

---

## How to Derive DSL Tile Coordinates

To populate the `frame { tile(relX=dx, relY=dy, baseId=dtile) }` blocks in the gbkt DSL:

1. Run `png2asset` as above (requires GBDK installed at `$GBDK_HOME`)
2. Open the generated `obj/gb/res/sprite.c`
3. Read each `sprite_metasprite_N[]` array entry
4. For each `{dy, dx, dtile}` entry (ignoring the sentinel `{0x80, 0x80, 0x00}`):
   - `relY = dy`
   - `relX = dx`
   - `baseId = dtile`
5. Transcribe into DSL:
   ```kotlin
   frame { // frame N
       tile(relX = dx_0, relY = dy_0, baseId = dtile_0)
       tile(relX = dx_1, relY = dy_1, baseId = dtile_1)
       // ... one call per non-sentinel entry
   }
   ```

The exact tile coordinates are a port assembly discovery moment (Plan 10-13). This spec
locks the PNG dimensions, tile grid, and frame count so the port assembly plan does not
invent or estimate these values.

---

## Verification

This asset spec locks the following before any DSL is written:
- Source PNG path and dimensions
- Frame count (5) and per-frame dimensions (64×48)
- Tile size (8×8) and grid (8×6 per frame)
- Non-empty tile counts (31/33/33/32/32 for frames 0–4)
- `png2asset` invocation flags (`-sh 48 -spr8x8 -noflip`)
- Expected C output structure (METASPRITE_DEF array per frame)

The port assembly plan (Plan 10-13) must reproduce the exact tile coordinates by running
`png2asset` and reading the generated `sprite.c`. Any deviation from the 31/33/33/32/32
non-empty tile counts would indicate a PNG change or incorrect `png2asset` flags.
