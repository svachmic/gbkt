# SEED: Phase 13 — Sprite outline lost when PNG transparent color is not at palette index 0

**Created:** 2026-06-04 (during Phase 13.3 post-close UAT — user inspection of the color-cycle elephants)
**Origin phase:** 13.3 (framework-primitives-metasprite-sprite-color-inserted)
**Source:** User UAT after 13.3-24 — "there is some kind of outerline/borderline missing on the elephant ... I can even see through (transparency) in some places, namely around the elephant's ear."
**Status:** OPEN — routed to a proper framework phase (user decision 2026-06-04: framework fix, not asset workaround)
**Blast radius:** MEDIUM-HIGH — touches `gbkt-gradle-plugin` `ConvertSpritesTask.buildPng2AssetArgs()` + the `-keep_palette_order` path; affects EVERY indexed sprite PNG. Regression risk to the platformer player sprite that Phase 12.9 (D2a) fixed with `-keep_palette_order`. A change here must be verified against both the metasprites elephant AND the platformer-template player sprite.

## Symptom

The asset-driven metasprites elephant renders with **no outline / border** — it does not
stand out from the checkerboard background, and the background shows THROUGH the elephant
in places (notably around the ear). Hidden for the whole port because the gray-on-gray
render masked it; the 13.3-24 color-cycle palettes (pink/cyan/green) made it obvious.

## Root cause (PROVEN, not hypothesis)

`gbkt-examples/metasprites/res/sprites/elephant.png` is a 64×240 indexed (mode "P") PNG.
Its palette + `transparency` (tRNS) chunk:

| src palette idx | RGB | pixels | role |
|---:|---|---:|---|
| 0 | (7,24,33) dark teal | 1953 | **elephant OUTLINE** |
| 1 | (134,192,108) green | 1991 | body midtone |
| 2 | (101,255,0) bright green | 0 | unused |
| 3 | (224,248,207) near-white | 3461 | body |
| 4 | (255,255,255) white | 7955 | **transparent background (tRNS = 4)** |

Game Boy hardware forces **2bpp index 0 = the transparent OBJ slot**. `ConvertSpritesTask`
passes `-keep_palette_order` to png2asset (added Phase 12.9 D2a, gated on `isIndexedPng`,
to keep the platformer player's index-0 orange background transparent). For the elephant
that flag faithfully preserves the WRONG order: the OUTLINE (src idx 0) stays at GB idx 0,
and the real transparent color (src idx 4, white) is ALSO mapped into GB idx 0 → the
outline collapses into the transparent slot and renders see-through. The unused bright-green
wastes a 4th slot.

`-keep_palette_order` carries an unstated INVARIANT: **the source PNG's palette index 0 must
be the transparent/background color.** The elephant violates it (tRNS on index 4). png2asset's
default auto-sort is also unreliable (the 12.9 comment notes it moved the platformer's orange
bg from idx0 to an opaque idx2), so neither current path is correct for a tRNS-on-nonzero PNG.

## Proof (experiment, 2026-06-04)

Reordered a TEMP copy of elephant.png to `[white(transp)→0, outline→1, green→2, body→3]`
(drop unused), ran png2asset with the identical pipeline args
(`-spr8x8 -px 32 -py 24 -sw 64 -sh 48 -noflip -keep_palette_order`), decoded the 2bpp tiles:

| 2bpp idx | meaning | BROKEN (current) | FIXED (reordered) |
|---:|---|---:|---:|
| 0 | transparent bg | 54.8% (bg + outline MERGED) | 40.7% (bg only) |
| 1 | outline (7,24,33) | 15.9% | **17.9% — now a VISIBLE index** |
| 2 | green midtone | 0.0% (wasted) | 14.6% |
| 3 | body | 29.2% | 26.8% |

The ~14% outline pixels move OUT of the transparent index 0 into a visible index 1. At
runtime (gbkt ascending palettes from 13.3-22/24) idx1→`gray_pal[1]=0x294A` dark → solid
dark outline restored; idx3 body→`0x7FFF` light. No conflict with the 13.3-22/24 palette
reversals (body stays at the high/light index; those fixes remain correct and must NOT be
reverted).

## Fix direction (for the phase to research/decide)

The framework should route the PNG's tRNS-declared transparent color to GB 2bpp index 0
**regardless of source palette order**, while keeping the remaining visible colors. Candidate
approaches to evaluate:
- Detect the tRNS index in `ConvertSpritesTask` (PngUtils) and, when it is non-zero, emit a
  png2asset transparent-color/index flag OR pre-permute the palette so transparent→0 before
  invoking png2asset (preserving relative order of the visible colors, dropping unused ones).
- Whatever the mechanism, it MUST keep the platformer player sprite (index-0 orange bg)
  correct — that is the regression oracle alongside the elephant.
- Consider a build-time validation/warning when an indexed sprite's index-0 is not the
  tRNS-transparent color (developer-facing safety per the project's "framework manages GB
  hardware" value prop).

## Verification oracles for the phase

- Metasprites elephant: outline renders as a solid dark border at rot=0 (gray) AND in the
  pink/cyan/green cycle; zero see-through through the body/ear. Binding human Visual Evidence sign-off.
- Platformer-template player sprite: still transparent-correct (no regression of 12.9 D2a).
- Re-pin `elephant.c.baseline` (metasprites + metasprites-stress; tiger.png likely has the
  same authoring and must be checked). D-17 7-target buildRom sweep EXIT 0.

## Related

- Phase 12.9 D2a — origin of `-keep_palette_order` (`ConvertSpritesTask.buildPng2AssetArgs:739-751`).
- Phase 13.3-22 / 13.3-24 — gbkt sprite-palette index-polarity fix (ascending ramps); compatible, keep.
- The same class likely affects `metasprites-stress` elephant + tiger (throwaway example; baselines only).
