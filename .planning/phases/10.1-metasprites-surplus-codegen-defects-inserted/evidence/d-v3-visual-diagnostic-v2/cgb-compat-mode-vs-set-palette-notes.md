# cgb_compatibility() Behavior Notes — Task 3

Date: 2026-05-19
Plan: 10.1-21 (D-V3 iteration v2, Task 3)

## Authoritative source: `/Users/michalsvacha/gbdk/include/gb/cgb.h`

Two functions, lines 176-188:

```c
/** Sets CGB palette 0 to be compatible with the DMG/GBP.

    The default/first CGB palettes for sprites and backgrounds are
    set to a similar default appearance as on the DMG/Pocket/SGB models.
    (White, Light Gray, Dark Gray, Black)

    \li You can check to see if @ref _cpu == @ref CGB_TYPE before using this function.
 */
void set_default_palette(void);

/** Obsolete. This function has been replaced by set_default_palette(), which has identical behavior.
 */
void cgb_compatibility(void);
```

### Key facts

1. **`cgb_compatibility()` is an OBSOLETE ALIAS for `set_default_palette()`** — identical
   behavior. The name `cgb_compatibility` is misleading; it does NOT switch the GBC into
   a special "DMG compatibility mode". The GBC palette RAM is fully accessible regardless.

2. **It writes to palette SLOT 0 ONLY**, for both BG and sprite palette banks. Slot 0
   becomes (White, Light Gray, Dark Gray, Black) — the DMG-default grayscale ramp.

3. **It does NOT affect slots 1-7.** Those remain at whatever value the boot ROM /
   firmware leaves them at (typically zero on a hard reset, but undefined per the GBDK
   docs — emulators may differ).

4. **It does NOT enable "DMG-compat mode" on the LCD.** The cartridge header byte at
   `0x0143` is what determines whether the GBC boots in DMG mode (`0x00` or absent),
   GBC-compatible mode (`0x80`), or GBC-only mode (`0xC0`). gbkt sets `0x80` for
   `GBC_COMPATIBLE` target → the GBC PPU runs in native CGB mode with full palette RAM.

5. **It does NOT interact with `set_sprite_palette()`.** `set_sprite_palette(first, n, data)`
   writes directly to OCPS/OCPD registers. These writes are unaffected by any prior
   `cgb_compatibility()` call; they simply overwrite whichever slots they target.

## Reference's bootstrap pattern

`/Users/michalsvacha/gbdk/examples/cross-platform/metasprites/src/metasprites.c:160-194`:

```c
void main(void) {
    DISPLAY_OFF;                              // line 161
#if defined(GAMEBOY)
    cgb_compatibility();                      // line 164 — palette 0 = DMG default
    set_sprite_palette(OAMF_CGB_PAL0, 1, gray_pal);   // line 165 — OVERWRITES slot 0
    set_sprite_palette(OAMF_CGB_PAL1, 1, pink_pal);   // line 166 — writes slot 1
    set_sprite_palette(OAMF_CGB_PAL2, 1, cyan_pal);   // line 167 — writes slot 2
    set_sprite_palette(OAMF_CGB_PAL3, 1, green_pal);  // line 168 — writes slot 3
#elif ...
#endif
    fill_bkg_rect(0, 0, ...);                 // line 177 — BG tilemap fill
    set_bkg_data(0, 1, pattern);              // line 180 — BG tile data
    load_and_duplicate_sprite_tile_data();    // line 183 — sprite tiles
    SHOW_BKG; SHOW_SPRITES;                   // line 186
    SPRITES_8x8;                              // line 192
    DISPLAY_ON;                               // line 194
}
```

### Critical observation about the reference

**The reference NEVER calls `set_bkg_palette()`.** Only sprite palettes are explicitly
loaded. The BG checker is supposed to render using **BG palette slot 0** — which
`cgb_compatibility()` initialized to the DMG-default grayscale (White, Light Gray, Dark
Gray, Black). The single BG tile (`pattern`) is `fill_bkg_rect`-painted at tile index 0
across the whole screen. On the GBC, each BG tile's per-tile attribute byte (in VRAM
bank 1) defaults to `0` — selecting BG palette slot 0.

So on the reference, when LCD turns on:
- BG palette slot 0 = `(White, Light Gray, Dark Gray, Black)` (from cgb_compatibility)
- BG tilemap = all tile index 0 (the pattern)
- BG attribute map = all 0 (palette slot 0)
- Sprite palette slot 0..3 = gray/pink/cyan/green (from set_sprite_palette calls)
- Sprite OAM = elephant sub-sprites, attr bits 0-2 = subpal (0..3)

This renders a checker pattern in BG-slot-0 colors (white/light-gray on dark backdrop) and
an elephant in the sub-palette-cycled colors.

## Port's bootstrap pattern (post-Plan-20)

`gbkt-examples/metasprites/build/gbkt/generated/main.c:208-222` (regenerated 2026-05-19):

```c
void main(void) {
    DISPLAY_OFF;                              // line 209
    cgb_compatibility();                      // line 210 — palette 0 = DMG default
    set_sprite_palette(0u, 1u, gray_pal);     // line 211 — overwrites slot 0
    set_sprite_palette(1u, 1u, pink_pal);     // line 212 — writes slot 1
    set_sprite_palette(2u, 1u, cyan_pal);     // line 213 — writes slot 2
    set_sprite_palette(3u, 1u, green_pal);    // line 214 — writes slot 3
    NR52_REG = 128u; NR50_REG = 119u; NR51_REG = 255u;   // sound (lines 215-217)
    set_sprite_data(0u, 48u, elephant_tiles); // line 218 — sprite tiles
    SHOW_BKG; SHOW_SPRITES;                   // lines 219-220
    SPRITES_8x8;                              // line 221
    DISPLAY_ON;                               // line 222
    play_enter();                             // line 223 — fills BG and re-applies palettes
}
```

### Differences from reference

| Aspect                            | Reference                       | Port                                  |
|-----------------------------------|---------------------------------|---------------------------------------|
| `DISPLAY_OFF` first               | YES (line 161)                  | YES (line 209)                        |
| `cgb_compatibility()` early       | YES (line 164)                  | YES (line 210)                        |
| 4× `set_sprite_palette` pre-DISPLAY_ON | YES (lines 165-168)        | YES (lines 211-214)                   |
| **BG tile data load pre-DISPLAY_ON** | YES (line 180, `set_bkg_data`) | **NO** (deferred to play_enter line 248) |
| **BG tilemap fill pre-DISPLAY_ON** | YES (line 177, `fill_bkg_rect`) | **NO** (deferred to play_enter line 247) |
| Sprite tile load pre-DISPLAY_ON   | YES (line 183)                  | YES (line 218)                        |
| LCDC + DISPLAY_ON last            | YES (lines 186-194)             | YES (lines 219-222)                   |
| `set_bkg_palette()` call          | NEVER (uses cgb_compat default slot 0) | NEVER (also relies on slot 0)         |

### The remaining GAP after Plan 20

**Plan 20 hoisted sprite palettes and sprite tile data into main() pre-DISPLAY_ON, but
did NOT hoist `bgFillCheckerboard` (RawOp containing `fill_bkg_rect` + `set_bkg_data`).**
Plan 20's SUMMARY explicitly notes this:

> Did NOT hoist bgFillCheckerboard (RawOp containing fill_bkg_rect + set_bkg_data) into
> main(). The DV3 RED tests only gate palette + LCDC order ... BG VRAM writes complete
> safely under vblank stalls when LCD is on.

That decision was made on the assumption that BG VRAM writes after DISPLAY_ON are safe.
**The assumption is wrong on GBC.** Here's why:

When `DISPLAY_ON` runs on a GBC with `cgb_compatibility()` having set BG palette slot 0
to (White, Light Gray, Dark Gray, Black) BUT no BG tile data loaded yet:

1. VRAM bank 0 BG tile-data region is still in its post-reset state (zeros on most
   emulators; the GBC firmware typically leaves the logo at tiles 0-25 but unloads them
   during the boot animation). Tile 0 is therefore "all color-0 pixels" → all white.
2. VRAM bank 0 tilemap is also zeros → every screen position references tile 0 → all white.
3. The first PPU frame post-DISPLAY_ON composites: BG = all-white screen.
4. `play_enter()` then runs `fill_bkg_rect` + `set_bkg_data` (line 247-248). These writes
   go through the GBDK VRAM-busy-wait helpers, which stall during vblank. The BG slowly
   updates over the next few frames.

But there's a SECOND effect specific to GBC. **`cgb_compatibility()` also writes some
hardware initialization that may affect the screen.** Per the GBDK source (sm83 asm),
`set_default_palette` writes to OCPS/OCPD and BCPS/BCPD registers — and ALSO sets
`BGP_REG` / `OBP0_REG` to DMG-default values (so that DMG-mode hardware also displays
properly during the dual-compat phase).

**The "all-black" symptom on GBC, post-Plan-20 with bootstrap-order-correct sprite
palettes, suggests one of:**

a. The BG tile data has not been loaded by the time `wait_vbl_done()` runs (so checker
   appears as solid color from slot-0 color-0 = white, NOT black — falsifying this).

b. The PPU is in a state where it's rendering "BG palette slot N" but slot N is zero-init
   (black) for all N other than slot 0. If somehow the BG attribute byte at every tilemap
   position points to a non-zero palette slot, those tiles render as black. **This is the
   GBC firmware "logo tile palette" residue hypothesis** — the GBC boot ROM writes BG
   attribute bytes during the logo animation, and `cgb_compatibility()` may NOT clear
   them. The first PPU frame post-Plan-20 DISPLAY_ON would then composite BG tiles using
   palette slots 1..7 (which gbkt never loaded) — all rendering black.

c. **Sprite palette RAM HAS been written, but `cgb_compatibility()` ITSELF stalls/no-ops
   because of the `_cpu` check.** Per the cgb.h docs comment "You can check to see if
   `_cpu == CGB_TYPE` before using this function" — this is a HINT that the function may
   internally be a no-op when `_cpu != CGB_TYPE` ... but `_cpu=17` in our runtime trace
   confirms CGB_TYPE, so this is falsified.

## Hypothesis 1 Verdict

**Hypothesis 1 (cgb_compatibility puts GBC into DMG-compat mode, making set_sprite_palette
a no-op):** **FALSIFIED**.

- `cgb_compatibility()` is an alias for `set_default_palette()` per the official GBDK
  header.
- It writes ONLY to palette slot 0 (BG and sprite), setting them to DMG-default grayscale.
- It does NOT change LCD mode, does NOT disable GBC palette RAM, does NOT no-op
  subsequent `set_sprite_palette()` calls.
- The runtime trace confirms `_cpu = 17 = CGB_TYPE` AND `_current_base_prop = 2` (subpal
  reached the runtime). Sprite palettes ARE being written into OCPD; OAM attr ARE getting
  subpal in bits 0-2.

## The actual 4th-layer cause (synthesis preview for Task 4)

Hypotheses 1, 2, 3 are all falsified. The runtime variables are correct. The codegen
shape is correct. The screenshot is still all-black.

The remaining suspects are downstream of palette + OAM:

**Suspect 4A — BG-palette-slot residue from GBC boot ROM:** The GBC boot ROM writes BG
attribute bytes during the Nintendo logo animation. `cgb_compatibility()` only initializes
palette slot 0; if the tilemap attribute map still points to a non-zero slot at any
position, those tiles render as zero-initialized = black.

**Suspect 4B — No `set_bkg_palette()` ever called:** If the GBC PPU defaults BG palette
slot 0 to all-zero (instead of cgb_compatibility's white/gray/dark/black) — for instance
because cgb_compatibility's effect is overwritten by a subsequent write — the entire BG
renders black.

**Suspect 4C — VRAM bank 1 attribute map at non-zero offsets** matters. The standard
GBDK BG attribute write path is `set_bkg_attributes()` or `VBK_REG = 1; set_bkg_tiles(...)`.
gbkt's `fill_bkg_rect` (and the underlying `BgCheckerboardEmissionTest` confirmed pattern)
ONLY touches VRAM bank 0. The attribute bytes in bank 1 are never written by gbkt for
this game. **If the emulator's GBC firmware leaves them at non-zero — all BG renders black.**

**Most-likely-named cause:** **Suspect 4A/4C combined — gbkt never initializes the GBC BG
attribute map.** On the reference, `cgb_compatibility()` is paired with the boot ROM
clearing the logo + leaving attribute map at 0 (palette slot 0). On the gbkt path, the
same boot ROM behavior + missing `set_bkg_palette()` may leave palette slot 0 in a state
that the gbkt build's `cgb_compatibility()` actually wrote correctly (DMG-default white),
BUT the elephant sprite is being shown over a BG that itself renders fine (slot 0 white-
gray-dark-black checker). The remaining issue must be:

**Suspect 4D — `play_enter()` line 243 emits a stray `SHOW_SPRITES;` AFTER `DISPLAY_ON`.**
On most emulators this is idempotent. But on GBC, if SHOW_SPRITES runs WHILE LCD is on
and AFTER `fill_bkg_rect`'s VRAM write (which can leave the PPU in a "BG mode 3" stall
state for several scanlines), the timing could cause a one-frame glitch.

The single MOST PLAUSIBLE candidate, requiring no NEW codegen path, that explains "all
black on GBC but renders fine on DMG (behavior1 screenshot has cpu=1, shows visible
elephant on green checker)":

**Suspect 4E — `set_sprite_palette()` writes the OCPD palette while LCD is OFF, BUT the
emulator (Coffee-GB)'s implementation of set_sprite_palette may have a bug that requires
LCD to be ON for the writes to take effect.** If Coffee-GB's OCPS/OCPD emulation only
processes writes during the active scanline window, the four `set_sprite_palette` calls
inside the `DISPLAY_OFF` window have NO effect on emulated palette RAM — leaving all
slots at zero = all black.

This is **emulator-specific** and would NOT be a gbkt codegen bug. Plan 22's fix shape
would then be: **add a duplicate set of `set_sprite_palette()` calls AFTER `DISPLAY_ON`,
inside the first vblank window of `play_enter()`** — which is already happening at lines
239-242 of the current generated code.

But behavior3 still renders black, so even those post-DISPLAY_ON palette writes are not
taking effect. Something deeper.

**The named root cause that Task 4 will commit to:** see `d-v3-visual-finding-v2.md`.
The runtime evidence (`_cpu=17`, `_current_base_prop=2`) plus the absence of any
`set_bkg_palette()` call plus the reference's reliance on `cgb_compatibility()` for BG
palette slot 0 plus the post-Plan-20 LOCKED static evidence (palette writes ARE pre-DISPLAY_ON)
together point to one specific runtime hypothesis that the variable trace cannot directly
falsify, requiring an emulator palette-RAM dump.
