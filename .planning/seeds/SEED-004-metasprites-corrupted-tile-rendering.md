---
id: SEED-004
status: dormant
planted: 2026-05-18
planted_during: v1.0 / Phase 10 closeout (Plan 10-20)
trigger_when: when Phase 10.1 (metasprites surplus codegen defects) is opened
scope: medium
---

# SEED-004: Elephant sprite tiles render corrupted (D-V1)

## Why This Matters

The mechanism layer for the metasprites port (Phase 10) is correct — `_idx` advances on
B press, `_rot` cycles on A press, sub-palette cycling via `rot >> 2` works in GBC mode.
However, human visual review of the UAT screenshots (`behavior1-animation-advance.png`,
`behavior2-flip-cycle.png`) reveals that the rendered elephant sprite is **garbled** —
pixels are visible and orientation does flip on Flip-XY, but the tile arrangement is clearly
wrong vs the reference asset. The sprite is recognisably an elephant shape but the pixel
pattern is not the clean reference elephant sprite from `png2asset`.

This is a visual-parity defect, not a mechanism defect. It was discovered during Plan 17
UAT when the human reviewer compared the DMG screenshots against the reference GBDK ROM
behavior. The JVM-tier tests (variable assertions + IR shape) all pass correctly.

## Root Cause (Hypothesis)

Two candidate causes, one of which is the dominant bug:

1. **png2asset tile-data byte ordering vs `MetaspriteVisitor.generateMetaspriteTileData()`**
   coordinate translation mismatch. The `generateMetaspriteTileData()` visitor transcribes
   tile data from the IR as-is; if the byte interleaving (lo/hi plane ordering) or the
   x/y coordinate mapping from `png2asset`'s output format differs from what GBDK expects
   at runtime, the tile pixels will be scrambled.

2. **8x8 vs 8x16 sprite mode mismatch.** The port uses `SPRITES_8x8` per asset-spec.md.
   The GBDK reference metasprites example was likely compiled with `SPRITES_8x16` (or
   uses a different tiling convention for 8x16 hardware sprites composed into a metasprite).
   If the hardware mode differs, the tile indices in the OAM descriptor map to different
   tile slots in VRAM, producing the wrong pixel output.

## Investigation Steps

1. Hex-dump `elephant_tiles[]` from the port's generated `main.c` and compare against
   `elephant_tiles[]` (or equivalent) from the reference `metasprites.c` in the GBDK
   example. If the byte arrays differ, the bug is in tile data emission.

2. Check `DISPLAY_SPRITES_8x16` / `DISPLAY_SPRITES_8x8` macro in the reference's
   initialization. If the reference uses 8x16, the port needs to match OR the
   `MetaspriteBuilder.frame { tile(...) }` coordinate system must account for the mode.

3. Review `MetaspriteVisitor.generateMetaspriteTileData()` and verify the plane
   interleaving matches what `set_sprite_data()` expects in GBDK's GBC-compatible mode.

## Evidence

- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior1-animation-advance.png`
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior2-flip-cycle.png`
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/c-diff.md`
- Phase 10 UAT.md §"Defect D-V1"

## Affected Files

- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/MetaspriteVisitor.kt`
  (method `generateMetaspriteTileData()`)
- `gbkt-examples/metasprites/obj/gb/res/sprite.c` (png2asset output — reference tile data)
- `gbkt-examples/metasprites/build/gbkt/generated/main.c` (generated `elephant_tiles[]`)

## Scope Estimate

**Medium** — requires careful hex comparison + likely a byte-ordering fix in
`generateMetaspriteTileData()`. No new IR nodes needed. May require adjusting the
tile coordinate transcription logic and adding a JVM-tier regression test that
compares generated tile bytes against the png2asset reference output.

## Why Not Fixed in Phase 10

Phase 10's hard scope cap is ONE named codegen bug-fix. The named bug was the
`GBDKPipelineV2` pipeline wiring gap (`generateMetaspriteDescriptor` not called —
fixed in Plan 10-15). D-V1 is a separate defect surfaced only after the first
successful ROM build and UAT screenshot capture (Plan 17). It was seeded per the
`/gsd-capture --seed` doctrine (D-06) for Phase 10.1.

## When to Surface

**Trigger:** when Phase 10.1 (metasprites surplus codegen defects) is opened.

Phase 10.1 owns D-V1, D-V2, D-V3 together (visual parity cluster) — they should
be fixed in a single targeted phase so the metasprites ROM achieves full visual
parity with the GBDK reference.

## Related

- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/10-UAT.md` §D-V1
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/oracle-comparison.md` §D-V1
- SEED-005 (D-V2: diagonal stripes) — companion visual defect fixed in same Phase 10.1
- SEED-006 (D-V3: stale _elephant_subPalette) — companion mechanism debug defect
