# Metasprites Oracle Comparison (Phase 10)

Three-signal codegen-quality + behaviour-equivalence report for the Phase 10 port of
the GBDK `cross-platform/metasprites` example to gbkt. Plan 16 fills signals 1 and 2
(ROM size + C-diff); Plans 17/18 (UAT-DMG and UAT-GBC) fill signal 3.

## Three-signal summary table

| Signal | Source | Result |
|--------|--------|--------|
| ROM size (D-11.1) | [`rom-size-comparison.md`](./rom-size-comparison.md) | **PASS** — gbkt 3879 bytes vs reference 3496 bytes (`l__CODE`); ratio 1.110×; 2× cap is 6992 bytes |
| Generated C (D-11.2) | [`c-diff.md`](./c-diff.md) | gbkt shorter on active game-logic surface (tile-flip infrastructure eliminated: 0 vs 70 lines); +19 line total delta driven by HOME-bank scaffolding. One codegen bug (palette slot numbering) found — seeded as candidate #1 |
| UAT verdict (D-11.3) | `10-UAT.md` behaviors 1+2 (Plan 17 DMG); behavior 3 (Plan 18 GBC) | **PARTIAL** — DMG behaviors 1+2 PASS (Plan 17); GBC sub-palette behavior 3 PENDING (Plan 18) |

## Signal 1: ROM size

**Verdict: PASS**

The gbkt port's `l__CODE` segment is 3879 bytes (0xF27). The GBDK reference is 3496 bytes
(0xDA8). The ratio is 1.110× — well inside the 2× envelope demanded by the VALIDATION
success criterion (cap: 6992 bytes).

Both ROMs produce 32768-byte images (the standard 32 KB Game Boy cartridge pad for
ROM_ONLY). The file-size comparison is uninformative; `l__CODE` is the load-bearing metric.

Key observations:
- The +383-byte delta (+10.9%) is driven primarily by gbkt's unconditional HOME-bank
  scaffolding (joypad, OAM, sound, dialog, fade helpers) emitted for all games regardless
  of usage. The active game-logic contribution to the delta is small.
- The gbkt port targets `GBC_COMPATIBLE` (`-Wm-yc`); the reference is a plain DMG build.
  Some of the delta reflects GBC-mode overhead.
- No bank split for this game: all code lands in `l__CODE` / `l__HOME` (no `l__CODE_1`).

## Signal 2: Generated-C qualitative comparison

**Verdict: gbkt is structurally comparable with one notable win and one notable bug**

**Win:** The entire tile-flip infrastructure (70 lines: `reverse_bits[256]` LUT,
`set_tile()`, `get_tile_offset()`, `load_and_duplicate_sprite_tile_data()`) is absent
from the gbkt port. By targeting `GBC_COMPATIBLE`, gbkt's `moveMetasprite()` emits
hardware flip calls directly — no VRAM duplication arithmetic needed. This 70-line
saving is the clearest codegen value-add in Phase 10.

**Bug:** The GBC palette setup in `play_enter()` calls all four `set_sprite_palette()`
with slot argument `0u`. The reference uses slots 0, 1, 2, 3 (`OAMF_CGB_PAL0..3`).
Only the last palette (green) is actually loaded; the other three are overwritten.
Sub-palette cycling (`rot >> 2`) will not show four distinct palettes at runtime.
This is a codegen correctness defect seeded as candidate #1 for Plan 18.

**Longer regions:** HOME-bank scaffolding (~130 lines, framework value-add), deceleration
ladder (4 lines per axis vs reference's 2-line if/else form), metasprite tables inlined
vs compiled as separate TU. None of these are defects; same structural explanation as
Phase 9.

Full side-by-side table: [`c-diff.md`](./c-diff.md).

## Signal 3: UAT verdict

**Status: PARTIAL — Plan 17 DMG behaviors 1+2 PASS; Plan 18 GBC behavior 3 PENDING**

Plan 17 (`10-17-PLAN.md`) ran D-01 behaviors 1+2 in DMG mode with visual evidence:
- **Behavior 1 (B-press animation advance):** PASS — `_idx` advanced from 0→1→2 on consecutive
  B presses (with release frames between edge events). Screenshot at `_idx==2` shows distinct
  elephant tile arrangement vs frame 0.
- **Behavior 2 (A-press flip cycle):** PASS — `_rot` cycled 0→1→2→3→4 on A presses.
  Screenshot at `_rot==2` (Flip-XY state) shows elephant visibly mirrored on both axes.

Plan 18 (`10-18-PLAN.md`) owns behavior 3 (GBC sub-palette cycling — requires `gbcMode=true`).

Per-behavior test targets (per `10-UAT.md`):

| Behavior | Target | Verdict |
|----------|--------|---------|
| D-01.1 B-press animation cycle | DMG | **PASS** — Plan 17; screenshot: behavior1-animation-advance.png |
| D-01.2 A-press flip cycle | DMG | **PASS** — Plan 17; screenshot: behavior2-flip-cycle.png |
| D-01.3 A-press sub-palette cycle | GBC only | **PASS** — Plan 18; screenshot: behavior3-subpalette-cycle-gbc.png (cyan sprite in GBC mode, rot=8) |

## Overall judgment

All three signals captured. Plan 18 completed the final UAT behavior (GBC sub-palette):

- **ROM size:** PASS — 1.110× the reference, far inside the 2× cap. Framework overhead
  is the same structural pattern documented in Phase 9; non-trivial games amortize it.
- **Generated C:** gbkt is structurally comparable to the reference. The standout win
  is the automatic elimination of the 70-line tile-flip infrastructure via the
  GBC_COMPATIBLE target. Palette slot bug was found and fixed in Plan 16 (RESOLVED).
- **UAT (all three behaviors):** Behaviors 1+2 PASS (Plan 17 DMG). Behavior 3 PASS —
  GBC sub-palette cycling confirmed via GBC-mode screenshot showing cyan sprite at rot=8
  (Plan 18). All mechanism assertions pass. Visual defects D-V1 (garbled tiles), D-V2
  (diagonal bg), D-V3 (stale _elephant_subPalette global) seeded for Phase 10.1.

The UAT contract is three-of-three complete. Phase 10 verdict: **PARTIAL — mechanism layer
complete, visual parity of asset rendering deferred to Phase 10.1**. The sub-palette
cycling mechanism (D-08) works correctly at runtime.

## Surplus seed candidates

Aggregated from both `rom-size-comparison.md` and `c-diff.md` for Plan 18
`/gsd-capture --seed` invocations:

1. **[SEED-PRIORITY-HIGH] GBC palette slot numbering bug** — `play_enter()` emits
   `set_sprite_palette(0u, 1u, ...)` for all four palettes instead of
   `set_sprite_palette(0u, ...)`, `set_sprite_palette(1u, ...)`, etc. Codegen path:
   `SpritePaletteVisitor` (or equivalent op in `gbkt-backend-gbdk`). Without this fix,
   D-09.3 (sub-palette cycling) will fail in UAT.

2. **[SEED-PRIORITY-LOW] Hardcoded frame-count comparison (`_idx >= 5u`)** — B-press
   animation cycle uses a literal `5` rather than a computed frame count. Less robust
   to metasprite count changes than the reference's `sizeof`-based idiom. Low priority
   since DSL regeneration is the normal workflow.

3. **[SEED-PRIORITY-LOW] Unreferenced HOME scaffolding** — `delay_frames()`,
   `show_sprites_range()`, `_win_*` dialog helpers, `fade_out/in()`, `play_sound()`
   are emitted for this game even though none are called. Dead-code elimination pass
   would recover the +383-byte delta for minimal games. Same deferred item as
   Phase 9's DEFERRED-09-01.

## Post-16 fix: palette slot indexing

**Date:** 2026-05-18  
**Commits:** `ce25f33e` (RED test), `2e8fb256` (fix)

### Bug

`SceneBuilder.palette()` used `val slot = if (palette.slot >= 0) palette.slot else 0`.
When `palette.slot == -1` (all DSL-declared palettes default to auto-assign), every
call resolved to slot `0`. Four sequential `palette(gray); palette(pink); palette(cyan);
palette(green)` calls all emitted `set_sprite_palette(0u, 1u, …)` — only the last
palette (green) was actually loaded; the prior three were overwritten.

### Root cause location

`gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt` — `fun palette(palette: GBCPalette)`.

The `ScriptOpVisitor.visitSetPalette` already used `CLiteral(op.slot)` correctly; the
`SetPalette` IR node already had a `slot: Int` field. The only defect was the `else 0`
default in `SceneBuilder.palette()`.

### Fix (<5 LOC)

```kotlin
// Before
val slot = if (palette.slot >= 0) palette.slot else 0

// After
val slot = if (palette.slot >= 0) palette.slot else paletteOps.size
```

`paletteOps.size` at call time equals the number of palettes already registered in this
scene — so the first call gets slot 0, the second slot 1, etc. Explicit-slot assignments
(`palette.slot >= 0`) are unaffected.

### Verification

Generated `main.c` after fix (`fourth-build-log.txt`):

```c
set_sprite_palette(0u, 1u, gray_pal);
set_sprite_palette(1u, 1u, pink_pal);
set_sprite_palette(2u, 1u, cyan_pal);
set_sprite_palette(3u, 1u, green_pal);
```

`grep -c 'set_sprite_palette' main.c` → 4 (confirmed).  
ROM compiled cleanly (`ROM created: metasprites.gb (32 KB)`).

JVM-tier test: `SpritePaletteSlotEmissionTest` in `gbkt-backend-gbdk` — 2 tests GREEN:
- `four spritePalette declarations emit set_sprite_palette with slots 0 through 3`
- `single spritePalette declaration emits set_sprite_palette with slot 0`

### Seed candidate #1 status

**RESOLVED** — the palette slot numbering bug from `c-diff.md §"GBC palette slot bug
(seed candidate #1)"` is fixed. D-09.3 (sub-palette cycling) should now work correctly
in Plan 18 UAT.

## Post-17 visual defects flagged by human UAT

After Plan 17 captured behavior 1+2 DMG screenshots and the mechanism passed both
JVM-tier assertions, human visual review of the screenshots surfaced two real
defects that the mechanism-only verification missed:

### D-V1: Elephant sprite tiles render corrupted

The mechanism — `_idx` advance on B press and `_rot` cycling on A press —
works exactly as specified by 10-UAT.md behaviors 1+2 (JVM tests both GREEN,
variable assertions both pass). But the visual evidence (`behavior1-animation-advance.png`,
`behavior2-flip-cycle.png`) shows the elephant sprite is **garbled** — pixels are
visible and the orientation does flip on Flip-XY, but the tile arrangement is
clearly wrong vs the reference asset.

Likely root cause: png2asset tile-data ordering vs `MetaspriteVisitor.generateMetaspriteTileData()`
coordinate translation mismatch, OR an 8x8 vs 8x16 sprite-mode mismatch between
asset-spec.md (8x8) and how the reference compiled (likely 8x16). Investigation
should hex-compare `elephant_tiles[]` between port main.c and reference metasprites.c.

Seeded for **Phase 10.1**.

### D-V2: BG renders as diagonal stripes instead of checkerboard

Plan 10-11's `bgFillCheckerboard()` helper is misnamed. The byte pattern
`0x80,0x80,0x40,0x40,0x20,0x20,0x10,0x10,0x08,0x08,0x04,0x04,0x02,0x02,0x01,0x01`
encodes a DIAGONAL LINE (top-left → bottom-right), not a checkerboard. When this
tile is tiled across the screen by `fill_bkg_rect`, the result is a screen
covered in diagonal stripes of black squares — exactly what the screenshots show.

Fix is trivial (1-10 LOC): either replace the literal with a real checker pattern
(e.g., 0xAA,0xAA,0x55,0x55 repeating for 4x4 checker squares) or copy the reference's
exact pattern verbatim, OR rename the existing helper to `bgFillDiagonal()` and add
a separate `bgFillCheckerboard()` with the correct bits.

Seeded for **Phase 10.1**.

## Updated Signal 3 verdict

| Signal | Verdict |
|--------|---------|
| ROM size (Plan 16) | PASS (1.110×) |
| Generated-C diff (Plan 16) | NOTABLE-WIN + 1 BUG (palette slot — fixed in post-16 patch) |
| UAT behavior 1 mechanism (Plan 17) | PASS |
| UAT behavior 2 mechanism (Plan 17) | PASS |
| UAT behavior 1+2 visual parity | PARTIAL — see D-V1, D-V2 |
| UAT behavior 3 mechanism (Plan 18) | PASS — rot=8, subpal computed correctly by generated C |
| UAT behavior 3 GBC visual (Plan 18) | PASS — cyan sprite visible in GBC-mode screenshot |

Phase 10 verdict (post-Plan-18): **PARTIAL — UAT contract three-of-three complete (mechanism
layer + GBC visual evidence all PASS). Visual parity defects (D-V1, D-V2, D-V3) deferred to
Phase 10.1**. The codegen substrate and sub-palette cycling mechanism are correct; asset
pipeline tile-ordering and BG helper pattern need follow-up.
