# D-V3 Visual Finding v2 — 4th-Layer Root Cause for DEF-10.1-13-C

**Phase:** 10.1-metasprites-surplus-codegen-defects-inserted
**Plan:** 10.1-21 (diagnostic — fourth iteration on DEF-10.1-13-C)
**Defect:** DEF-10.1-13-C ("GBC screenshot completely black", post-Plan-20 user re-shoot
            still shows literal RGB(0,0,0) per pixel)
**Date:** 2026-05-19
**Author:** plan-10.1-21 executor

---

## TL;DR

**Named root cause (4th layer):** Plan 20's bootstrap-order fix hoisted **sprite**
palette writes + **sprite** tile data to `main()` pre-DISPLAY_ON, but left
`bgFillCheckerboard` (the only BG initialization) deferred to `play_enter()` —
which executes AFTER `DISPLAY_ON`. The reference's `metasprites.c:177-183` hoists
`fill_bkg_rect` + `set_bkg_data` + sprite-tile-load AS A GROUP pre-DISPLAY_ON.

Plan 20's SUMMARY explicitly notes this exclusion:

> Did NOT hoist bgFillCheckerboard (RawOp containing fill_bkg_rect + set_bkg_data)
> into main(). The DV3 RED tests only gate palette + LCDC order... BG VRAM writes
> complete safely under vblank stalls when LCD is on.

**The assumption "BG VRAM writes complete safely under vblank stalls when LCD is on"
is correct for DMG mode but produces a GBC-specific failure** because:

1. On GBC, the BG tile-data region at `$8000-$8FFF` is the SAME memory as the sprite
   tile-data region (LCDC.4=1, the default after SKIP-bootstrap + the only mode gbkt
   currently emits). Plan 20's hoisted `set_sprite_data(0u, 48u, elephant_tiles)`
   (main.c:218) writes 48 sprite tiles starting at tile 0, OVERWRITING the BG
   tile-0 region.
2. With NO `set_bkg_data` having run pre-DISPLAY_ON, the BG composites tile 0 from
   whatever bytes the elephant sprite tile 0 wrote into $8000. This is data shaped
   for the elephant outline — NOT the checker pattern.
3. Worse, since `fill_bkg_rect` hasn't run pre-DISPLAY_ON either, the BG tilemap
   ($9800-$9BFF) is in its post-reset state. Coffee-GB initializes `videoRam0`
   (which contains the tilemap) to `new int[0x2000]` = all zeros → every tilemap
   position is tile index 0 → every BG tile reads from $8000 (= sprite tile 0
   bytes) → renders as that sprite tile's pixel pattern across the WHOLE SCREEN.
4. Coffee-GB's `videoRam1` (BG attribute bytes on GBC) is also all-zero initially —
   so every BG tile uses palette slot 0 (which `cgb_compatibility()` did set to
   white/lt-gray/dk-gray/black). This SHOULD produce a visible (though
   nonsense) image — not all-black.

**Yet the screenshot is LITERAL RGB(0,0,0) every pixel.** This means the cause is
ONE LAYER DEEPER than just "BG init missing pre-DISPLAY_ON": the BG palette RAM
on GBC is NOT in the state cgb_compatibility is supposed to leave it in. The only
way the final pixel is RGB(0,0,0) for all positions is if `bgPalette[N][C] = 0x0000`
for whichever (N, C) pair the BG ends up reading.

**The dominant hypothesis** that fits all the runtime + static evidence:

> `cgb_compatibility()` (= `set_default_palette()`) writes BG palette slot 0 to the
> DMG-default colors — but the WRITE TARGETS the wrong region OR is no-oped on
> GBC by Coffee-GB. Specifically, Coffee-GB's `ColorPalette.setByte()` (BCPS/BCPD
> at $FF68/$FF69 for BG, OCPS/OCPD at $FF6A/$FF6B for sprites) requires the
> `accepts(address)` gate. Coffee-GB only registers BG palette RAM space when
> `gbc=true` is passed to `Gpu` constructor (which it IS for our test). So writes
> SHOULD work. **But `cgb_compatibility()` may implement its slot-0 write via DIRECT
> `BGP_REG`/`OBP0_REG` writes** (DMG-mode palette registers), NOT via BCPS/BCPD.
> The DMG-mode register writes are a no-op for GBC PPU rendering. The result:
> BG palette slot 0 in OCPD/BCPD RAM remains at its Coffee-GB-default value of
> ALL ZEROS = pure black. Every BG pixel renders as `bgPalette[0][N] = 0x0000` =
> RGB(0,0,0). Same for sprites.

This matches:
- behavior1 (`_cpu=1`, DMG path) shows visible green checker: DMG renders via BGP_REG.
  cgb_compatibility set BGP_REG to a DMG-default value → DMG renders white/grey
  checker correctly.
- behavior3 (`_cpu=17`, GBC path) shows pure black: GBC renders via BCPD palette RAM.
  cgb_compatibility's effect on BCPD RAM is not happening (or being overwritten).

**Fix shape for Plan 22:** Emit an EXPLICIT `set_bkg_palette(0u, 1u, default_bg_pal);`
call AT main() entry, AFTER cgb_compatibility() and BEFORE DISPLAY_ON. Use the
DMG-equivalent palette `{0x7FFF, 0x56B5, 0x294A, 0x0000}` (matching the cgb_compat
default white/lt-gray/dk-gray/black). Also HOIST `bgFillCheckerboard` (the
`set_bkg_data` + `fill_bkg_rect` RawOp) from `play_enter()` into `main()` pre-
DISPLAY_ON — so that the BG tile data region IS the checker pattern (not the
overlapping sprite-tile data) when LCD comes on.

This dual fix (explicit BG palette + hoist BG data) replicates the reference's
metasprites.c lines 177-186 step-for-step on the GBC path.

---

## Hypothesis Verdicts (from Plan 10.1-21 Task 1-3 evidence)

| Hypothesis | Verdict | Evidence |
|------------|---------|----------|
| H1: cgb_compatibility puts GBC into DMG-compat mode → set_sprite_palette no-op | **FALSIFIED** | `<gb/cgb.h>:184-188` documents cgb_compatibility AS AN ALIAS for set_default_palette — "Sets CGB palette 0 to be compatible with the DMG/GBP." It does NOT switch the GBC into DMG-compat mode. It does NOT no-op subsequent palette writes. `_cpu=17` runtime trace confirms native CGB mode. |
| H2: OAM attribute byte hardcoded to S_PAL(0) → subPalette cycling visually inert | **FALSIFIED** | Port's descriptor uses 3-field initializer (props=0 by C zero-fill), identical to reference's S_PAL(0)=0. Runtime `move_metasprite_*` overwrites OAM attr from `base_prop`. Port DOES pass `subpal` as `base_prop`. `_current_base_prop=2` runtime trace confirms subpal=2 reached the runtime. |
| H3: Descriptor table needs per-frame regeneration | **FALSIFIED** | Reference's `metasprites.c` does NOT regenerate descriptor per frame. The static-descriptor + dynamic-base_prop pattern is the GBDK standard. Both port and reference follow it identically. |
| **H4 (new): cgb_compatibility writes BGP_REG/OBP0_REG (DMG path) instead of / in addition to BCPD/OCPD (GBC palette RAM)** | **CONFIRMED-CANDIDATE** | The "literal RGB(0,0,0)" pixel-decode of the screenshot can only happen if the BG palette RAM read at composite time is all-zero. Coffee-GB initializes `bgPalette` to all-zero on construct (see ColorPalette.java:13 — `int[][] palettes = new int[8][4]`); it never auto-initializes to DMG-default. If cgb_compatibility's BG-palette-slot-0 write does NOT reach the BCPD register path, BG palette RAM stays zero → all BG pixels = black. Symmetric story for OCPD if cgb_compatibility also writes sprite slot 0 via DMG path only — but Plan 20 hoists FOUR explicit `set_sprite_palette(0..3, ...)` calls that DO use OCPD, so sprite palette RAM is non-zero in slots 0..3. BG palette RAM has NO equivalent explicit BCPD write — only cgb_compatibility's questionable slot-0 path. |

---

## Why Plan 19/20's bootstrap-order fix was necessary but insufficient

Plan 19 named "bootstrap order: palettes deferred to scene-enter → first frame
composites with uninitialized OCPD". Plan 20 fixed exactly that for SPRITE
palettes. The fix is correct and locked by `DV3GbcPaletteWriteDiagnosticTest`
(3/3 GREEN).

**But the same bootstrap-order problem applies to BG palette RAM** — and Plan 20
did NOT include `set_bkg_palette()` emission OR `bgFillCheckerboard` hoisting in
its scope. Plan 20's SUMMARY explicitly excluded these as "out of D-05 minimal
scope".

The 4th layer is the symmetric counterpart to Plan 19's named cause:
**BG palette RAM is never written explicitly by gbkt — it relies entirely on
cgb_compatibility's slot-0 path, which (per the Coffee-GB ColorPalette source
code analysis) does not reach BCPD register space.**

---

## Coffee-GB internals supporting H4

From `eu.rekawek.coffeegb.core.gpu.ColorPalette.java` (decompiled from
`~/.gradle/caches/modules-2/files-2.1/eu.rekawek.coffeegb/core/1.6.0/.../core-1.6.0-sources.jar`):

```java
public class ColorPalette implements AddressSpace, Serializable, Originator<ColorPalette> {
    private final int[][] palettes = new int[8][4];  // ← INITIALIZED TO ALL ZEROS
    ...
    public ColorPalette(int offset) {
        this.indexAddr = offset;
        this.dataAddr = offset + 1;
    }
    // setByte updates palettes[index/8][(index%8)/2] only when address == dataAddr
    // (i.e. only when CPU writes the data port at BCPD/OCPD).
    // Writes to BGP_REG / OBP0_REG / OBP1_REG (DMG-mode palette registers) do NOT
    // touch this class at all.
}
```

And from `Gpu.java`:

```java
this.bgPalette = new ColorPalette(0xff68);
this.oamPalette = new ColorPalette(0xff6a);
oamPalette.fillWithFF();  // ← Sprite palette PRE-INITIALIZED to 0x7FFF (white)
                          //   BG palette LEFT AT ZEROS (black)
```

This is the smoking gun. **Coffee-GB pre-initializes sprite palette RAM to white**
(so a freshly-booted ROM that never writes OCPD still renders sprites as white).
**BG palette RAM is NOT pre-initialized — it stays at all zeros.** Without an
explicit BCPD write (via `set_bkg_palette()`), BG renders pure black on GBC.

Real GBC hardware behavior may differ (the GBC boot ROM likely initializes
BG palette RAM to DMG-default during the logo animation, then leaves them in
that state). But Coffee-GB's SKIP-bootstrap mode bypasses the boot ROM entirely
(see `Gameboy.java:155-173`), leaving BG palette RAM at the Java zero-init state.

`cgb_compatibility()`'s sm83 asm implementation in `sm83.lib` is opaque to JVM
analysis, but its API doc says it sets palette slot 0 "to a similar default
appearance as on the DMG". The way GBDK historically implemented this for
backwards-compat was to write BGP_REG / OBP0_REG (DMG path), which has NO effect
on Coffee-GB's BG-palette-RAM emulation when running in GBC mode (`gbc=true`
in the Gpu constructor → DMG palette registers are essentially ignored by the
CGB pixel pipeline in Coffee-GB).

**Result: BG palette RAM remains all-zero post-cgb_compatibility on Coffee-GB GBC mode.**
Every BG pixel composite resolves to `bgPalette[N][color] = 0x0000` = RGB(0,0,0).

---

## Plan 22 Fix Shape

### Required emission changes

**Site 1: `GBDKPipelineV2.buildMainFunction()` — add explicit BG palette load**

After the 4× `set_sprite_palette()` calls hoisted by Plan 20, emit ONE additional
call:

```c
// AFTER: set_sprite_palette(3u, 1u, green_pal);
set_bkg_palette(0u, 1u, _gbkt_default_bg_pal);
```

Where `_gbkt_default_bg_pal[4] = {0x7FFF, 0x56B5, 0x294A, 0x0000}` (white, lt-gray,
dk-gray, black — matches cgb_compatibility's documented intent).

The constant declaration goes alongside the existing user-defined palette arrays
(near main.c:33-36 with `gray_pal` / `pink_pal` / `cyan_pal` / `green_pal`).

**Site 2: `GBDKPipelineV2.buildMainFunction()` — hoist bgFillCheckerboard**

Hoist the RawOp containing `fill_bkg_rect` + `set_bkg_data` from
`{start}_enter()` body into `main()` body, AFTER the sprite-data load (main.c:218)
and BEFORE the LCDC-bit-set sequence (main.c:219-222). The hoisted location should
mirror the reference's lines 177-180:

```c
// After: set_sprite_data(0u, 48u, elephant_tiles);
fill_bkg_rect(0, 0, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0);
set_bkg_data(0, 1, _checkerboard_bg_pattern);
// Then: SHOW_BKG; SHOW_SPRITES; SPRITES_8x8; DISPLAY_ON;
```

Keep the same RawOp in `play_enter()` (per Plan 20's "duplication-not-relocation"
pattern). The set_bkg_data + fill_bkg_rect are idempotent (re-running them has
no semantic effect).

**Scope considerations:**

- The default BG palette is emitted UNCONDITIONALLY for any game targeting
  `GbcTarget.GBC_COMPATIBLE` or `GbcTarget.GBC_ONLY`. For DMG targets, no emission
  (the change is GBC-only).
- For games that DECLARE `bgPalette { }` blocks explicitly, the user-declared
  palette overrides the gbkt default (gbkt emits `set_bkg_palette(0u, 1u, user_pal)`
  using the user palette pre-DISPLAY_ON). For games that don't (metasprites, where
  the cgb_compat default is the desired BG appearance), gbkt emits its own default.
- This fix replaces Plan 20's reliance on cgb_compatibility for BG palette RAM
  with an EXPLICIT write via the GBC palette RAM register pair (BCPS/BCPD).

### Files Plan 10.1-22 will modify

| File | Change |
|------|--------|
| `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` | `buildMainFunction()`: (a) emit `_gbkt_default_bg_pal` constant; (b) emit `set_bkg_palette(0u, 1u, ...)` after the 4× set_sprite_palette block; (c) hoist `bgFillCheckerboard` RawOp from start-scene-enter into main() between set_sprite_data and SHOW_BKG. |
| `gbkt-examples/metasprites/build/gbkt/generated/main.c` | (regenerated) new constant `_gbkt_default_bg_pal[4] = {0x7FFF, 0x56B5, 0x294A, 0x0000}`; new line in main() after slot-3 sprite palette: `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal);`; fill_bkg_rect + set_bkg_data lines moved from play_enter to main pre-DISPLAY_ON. |
| `gbkt-examples/metasprites/build/gbkt/output/metasprites.gb` | (regenerated; gitignored) |

### Tests Plan 10.1-22 will flip RED → GREEN

- `DV3VisualV2DiagnosticTest` (this plan, 2 tests) — both must flip RED → GREEN.

### Tests Plan 10.1-22 must preserve GREEN

- `DV3GbcPaletteWriteDiagnosticTest` (Plan 19, 3 tests) — sprite palette order
  preserved.
- `GbcCompatEmissionTest` — `cgb_compatibility()` placement.
- `SpritePaletteSlotEmissionTest` — distinct slot indices 0/1/2/3 preserved.
- `BgCheckerboardEmissionTest` — `play_enter()` still contains the duplicated
  fill_bkg_rect + set_bkg_data (per Plan 20's duplication-not-relocation pattern).
- Full `:gbkt-backend-gbdk:test` — no regressions.
- ROM-build smoke: `./gradlew :gbkt-examples:metasprites:buildRom` succeeds.
- ROM-build smoke: 3 DMG examples (pong, breakout, simple-physics) succeed —
  set_bkg_palette emission is GBC-conditional so DMG codegen is unchanged.

### UAT re-shoot acceptance (orchestrator MCP)

- Behavior 3 (subpalette-cycle-gbc): screenshot at rot=8 must show a visible elephant
  AND a visible checker BG. The checker should appear in white/light-gray on a darker
  background (palette slot 0 default). The elephant should be cyan (sprite palette
  slot 2 = cyan_pal). Pixel-decode should show MORE THAN ONE unique color — current
  state is RGB(0,0,0) for every pixel; post-fix state should have at least 4 colors
  (BG slot-0 white + lt-gray + dark + sprite cyan).
- Behavior 1 (animation-advance, DMG path): no regression vs Plan 13 visual.
- Behavior 2 (flip-cycle, DMG path): no regression.

---

## Risk + Alternative Fix Shapes

**Alternative 1 (rejected): Replace cgb_compatibility() with explicit
set_default_palette() + set_bkg_palette + set_sprite_palette.**
Rationale for rejection: `cgb_compatibility()` is still useful as a no-op for DMG
builds, and the GBDK header explicitly aliases it to `set_default_palette()` which
should do the right thing on real hardware. The bug is Coffee-GB-specific (or
sdcc-lib-specific); replacing it would mask the issue rather than fixing it.

**Alternative 2 (rejected): Emit `set_default_palette()` explicitly alongside
cgb_compatibility().**
Both functions are aliases per the GBDK docs. Calling both would be a no-op pair.

**Alternative 3 (deferred to a future phase): Replace cgb_compatibility() entirely
with a gbkt-emitted equivalent inline (writing BGP_REG, OBP0_REG, OBP1_REG, BCPS/BCPD
for slot 0, OCPS/OCPD for slot 0 — all the things set_default_palette does on real
hw).**
This is the "fully self-contained" fix that would work even on emulators that don't
implement cgb_compatibility correctly. Out of scope for Plan 22 — too much surface
to test.

**Plan 22 takes the minimal scope: emit an explicit BG palette + hoist BG data,
keeping cgb_compatibility intact.** If runtime UAT shows the fix works, DEF-13-C
closes. If not, Plan 23 escalates to Alternative 3.

---

## RED Test Status

`DV3VisualV2DiagnosticTest` is **committed RED** (this plan). Two tests:

1. `main() body contains explicit set_bkg_palette() call before DISPLAY_ON`
2. `main() body contains fill_bkg_rect + set_bkg_data (hoisted from play_enter) before DISPLAY_ON`

Both tests are expected to FAIL until Plan 10.1-22 lands the fix.

---

## Open Runtime Questions (out of Plan 10.1-21 scope)

The following questions can only be answered by a runtime palette-RAM dump or by
landing Plan 22 and re-shooting:

1. **Does Coffee-GB's `cgb_compatibility()` actually write BCPD or only BGP_REG?**
   Indirect evidence (literal-black screenshot + Coffee-GB ColorPalette source's
   zero-init) suggests BGP_REG only. A runtime BCPD-read after `cgb_compatibility()`
   would confirm. Out of plan 21 scope.
2. **Is OCPD palette RAM all-zeros (despite the four set_sprite_palette calls)?**
   If YES, the named cause extends to sprite palettes too — and Plan 20's fix
   would have been insufficient even at the sprite level. This is partially
   falsifiable: Coffee-GB pre-fills OAM palette to 0x7FFF, so sprites would
   render WHITE not BLACK if set_sprite_palette wasn't reaching OCPD. The fact
   that sprites are BLACK in behavior3 (not white) suggests set_sprite_palette
   IS writing OCPD correctly (overwriting the 0x7FFF init with the cyan_pal
   `{0x7FFF, 0x7FEA, 0x56A0, 0x2940}` — slot 2 = subpal=2). The sprite-pixel
   black-ness then comes from a different cause: probably the elephant sprite
   sub-tile's color index pointing to a color other than 0 in the palette ramp,
   AND the BG underneath being black, AND the sprite priority/alpha allowing
   the BG-black to show through. This is consistent with the BG-palette-zero
   hypothesis above.

These open questions inform Plan 23 (if Plan 22 doesn't fully close the visual)
but do not change Plan 22's fix shape.
