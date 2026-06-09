# SEED: Phase 12 — Grass Tilemap White Pixels (world1-area1.png Render Artifact)

**Created:** 2026-05-25 (Plan 12-23 round-2 — Anchor 5 human-verify Issue A)
**Origin phase:** 12 (port-platformer-template-gbdk-example-to-gbkt)
**Source:** Plan 12-23 round-2 anchor 5 — user inspection of `01-near-end.png` round-1 + round-2
**Status:** RESOLVED 2026-06-02 by Phase 12.9 (palette-inversion-asset-pipeline)
**Routing:** CLOSED — fixed in Phase 12.9 (per-zone palette wiring + `-keep_palette_order` re-activation + sprite-transparency + 32×32 BG clear); see Resolution Log below
**Blast radius:** Small-to-medium — touches `gbkt-gradle-plugin` ConvertZoneTilesetsTask + png2asset invocation flags (palette emit, tile-index assignment); affects any zone whose tileset declares a transparent/background color that maps to BG palette index 0

## Context

Plan 12-23 round-2 anchor 5 captured `01-near-end.png` showing the player near the right
edge of world1-area1 (level 1, grass tilemap). The user inspection (round-1 + round-2)
flagged: "the grass tilemap is somewhat broken. It shows white pixels where grass should
be."

Reference artefact (round-2 capture):
- `.planning/phases/12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/uat-screenshots/anchor-5/01-near-end.png`
- Mirror: `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/uat-screenshots/anchor-5/01-near-end.png`

Comparison reference (the same world1-area1 tilemap as captured in Anchor 1's earlier
gameplay frame, no level-traversal scroll):
- `.planning/phases/12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/uat-screenshots/anchor-1/02-gameplay.png`

## Symptom

The grass tilemap renders with stray white pixels scattered through tile cells that
should display solid grass. Visible only in the world1 area renders (anchor-1 gameplay
and anchor-5 near-end). The world2-area1 (rocky) and nextLevel banked card do NOT show
the same symptom — those use different tilesets via the same `_bkg_tiles_load_banked`
helper.

This is NOT a sprite-vs-bg layer interaction (the player metasprite is on the sprite
layer; the white pixels appear in background tile cells far from the player). It is
not a scroll artifact either — the round-1 capture shows the same pattern at a
different scroll offset.

## Suspected Causes (ranked)

### Hypothesis 1 (most likely): png2asset palette/index mismatch on `world1-tileset.png`

`gbkt-examples/platformer-template/res/graphics/world1-tileset.png` is the source
tileset for world1-area1.png and world1-area2.png. If png2asset processes this PNG
with an unexpected palette/transparency assignment — for example, when an unreferenced
background pixel color maps to BG palette index 0 (DMG white) instead of the intended
grass color (DMG light-grey or dark-grey) — every grass tile that uses that color in
its bitmap will render with white pixels at the affected positions.

DMG hardware renders BG palette index 0 as "transparent" (= the LCD's clear value =
white), which is why this manifests as white pixels rather than some other artifact.

**Diagnostic path:**
1. Inspect `world1-tileset.png` source PNG with an image editor — read the exact RGB
   colors used per pixel
2. Compare against the png2asset-emitted `_zone_world1Area1Zone_tileset` C array
   (in `build/gbkt/generated/_zone_world1Area1Zone_tileset.c` or similar) — look for
   tiles whose 2bpp encoding has unexpected index-0 pixels
3. Check the png2asset invocation flags in `gbkt-gradle-plugin/.../ConvertZoneTilesetsTask.kt`
   for the `-bpp 2 -spr8x8 ...` arguments — confirm no `-transparent` or palette-
   override flag is silently mis-assigning the BG color

### Hypothesis 2: world1-area1.png tilemap references a tile index that doesn't exist in the tileset

If `world1-area1.png` (the tilemap PNG that png2asset processes into the tile-index
map `_zone_world1Area1Zone_tilemap`) contains pixel patterns that don't deduplicate
to any existing tile in `world1-tileset.png`, png2asset may either fall back to
index 0 (white) OR emit a phantom tile filled with index-0 pixels. Either way the
runtime renders white where the original PNG would have shown a deduplicated grass
tile.

**Diagnostic path:** Open both `world1-tileset.png` and `world1-area1.png` in an
image editor; verify every 8×8 tile cell in the tilemap maps to a tile that is
present (pixel-identical) in the tileset PNG.

### Hypothesis 3: Gradle-task cache invalidation gap

`ConvertZoneTilesetsTask` may have a stale cache hit on a prior incorrect png2asset
output. If a user (or a prior plan) ever ran the task with broken input and the
output got cached, subsequent runs with the correct input could still use the cached
broken artifacts.

**Diagnostic path:** `./gradlew :gbkt-examples:platformer-template:clean
:gbkt-examples:platformer-template:buildRom` and re-shoot anchor 5 — if the white
pixels disappear, the cache was stale.

## What's Deferred

A dedicated diagnostic phase (or absorption into Phase 13 framework-primitives work)
that:

1. Runs the 3 diagnostic paths above against `world1-tileset.png` + `world1-area1.png`
2. Identifies the root cause (palette mismatch, missing-tile fallback, or stale cache)
3. Fixes the root cause: either re-encode the source PNGs, adjust png2asset flags in
   `ConvertZoneTilesetsTask`, OR add a cache-invalidation rule
4. Re-shoots anchor 5 + anchor 1 to confirm the grass tiles render cleanly
5. Adds a JVM-tier emission test for `_zone_world1Area1Zone_tileset` that locks the
   expected 2bpp byte pattern (so a regression on png2asset would surface as a unit
   test failure, not a UAT visual artifact)

## Revival Condition

- Phase 12.6 codegen-fix (DEFECT-1 + DEFECT-2 from Plan 12-23 round-2) ships and the
  re-shot anchor 5 STILL shows the grass white pixels (confirms this is orthogonal
  to the level-switch defects)
- Another example game adopts the world1-tileset.png art (or uses a similar palette
  layout) and exhibits the same symptom
- Phase 13 framework-primitives work touches `ConvertZoneTilesetsTask` or the
  png2asset invocation flags

## Related Artifacts

- `gbkt-examples/platformer-template/res/graphics/world1-tileset.png` (source tileset)
- `gbkt-examples/platformer-template/res/graphics/world1-area1.png` (tilemap)
- `gbkt-gradle-plugin/.../ConvertZoneTilesetsTask.kt` (png2asset invoker)
- `gbkt-examples/platformer-template/build/gbkt/generated/_zone_world1Area1Zone_tileset.*`
  (png2asset output — inspect for the broken 2bpp encoding)
- Round-2 evidence: anchor-5/01-near-end.png + anchor-1/02-gameplay.png (visible symptom)
- Plan 12-23 round-2 SUMMARY §"Decisions Made" — explicitly NOT inline-fixed per the
  resume_instructions + route-to-proper-phase rule

## Not in scope for Plan 12-23

Per the per-anchor matrix in `12-VALIDATION.md` row 5, anchor 5's load-bearing truths
are scene transition + cross-bank tilemap reload — both of which ARE proven by the
captured PNGs despite the white-pixel render artifact in the grass tiles. The
white-pixel symptom is cosmetic and orthogonal to the codegen defects routed to
Phase 12.6 via OPTION A.

## Partial resolution (Phase 12.8)

**Phase:** 12.8-grass-tileset-white-pixels-diagnostic (SHIPPED as diagnostic-only)
**Date:** 2026-05-27
**Outcome:** PARTIAL — asset-pipeline boundary closed; runtime palette-wiring routed to Phase 12.9

### What Phase 12.8 closed

**W3 codegen fix (Plan 12.8-03):** Added `-keep_palette_order` flag conditionally to png2asset invocation in `ConvertZoneTilesetsTask.kt:288-298`. New `isIndexedPng(File): Boolean` helper at kt:480-494 parses PNG IHDR color-type byte (3 = indexed) so the flag is emitted ONLY for indexed PNGs (world1-tileset.png, world2-tileset.png) and suppressed for RGB tilesets (banks/checker.png, title-screen.png, next-level.png). This preserves the W2 ABSORB 1-file footprint AND prevents cross-pollination into RGB-tileset projects.

**W4 JVM emission invariant (Plan 12.8-04):** `World1TilesetGrassEncodingTest` locks the post-fix byte pattern at `_zone_world1Area1Zone_tileset_tiles[432]`. Tile-0 and tile-6 first-byte = `0x80` (near-black slot 0 from PLTE preservation).

**W5 ROM smoke + 7-target sweep (Plan 12.8-05):** 6/6 strict targets byte-identical (breakout, simple-physics, metasprites, metasprites-stress, banks, racer); pong PASS* (known toolchain non-determinism); platformer-template INTENTIONALLY-CHANGED. Pre/post byte-diff captured in 12.8-DIAGNOSTIC.md §"Pre-fix vs post-fix byte-diff (D-14 ROOT-CAUSE-EVIDENCE)" — index-0 cream→near-black; palette grew [4]→[16].

### What Phase 12.8 did NOT close (routed to Phase 12.9)

**A6-CONFIRMED palette-wiring gap (Plan 12.8-01 W1 audit):** At runtime, the BG palette RAM is populated by `_gbkt_default_bg_pal` at `main.c:698` (GBC-gated default-palette site), NOT by the emitted per-zone `_zone_<id>_tileset_palettes[16]` arrays. The W3 flag-pin preserves the PNG PLTE ordering in the EMITTED palette array — but that array is never uploaded to BCPS/BCPD.

**Visual outcome (Plan 12.8-07 G3 binding gate, 2026-05-27, user verdict):** The W3 fix did NOT visually close G3 — instead it surfaced **color inversion**: the new index-0 (near-black per PLTE) gets rendered using `_gbkt_default_bg_pal`'s cream, inverting the grass colors. User reported on anchor-5/03-level-2.png: "It's worse than before. Now ALL colors are inverted, next level still broken, second level character still sunk." Composite G3 verdict: BLOCKED.

### Routing target

**Phase 12.9 (palette-inversion-asset-pipeline)** — created during Phase 12.6 debug cycle 2/3 for exactly this defect class. The W3 grass-tileset color inversion is the same root-cause family. Phase 12.9 scope expansion:
- Per-zone palette wiring: `set_bkg_palette(0u, 1u, _zone_<id>_tileset_palettes)` at zone-load codegen ordering
- Re-shoot anchor-5 and anchor-1 PNGs post-fix; bind G3 + G4 verdicts in 12.9
- Close 01-nextlevel-flip orthogonal regression introduced by W3
- Investigate 00-last-gameplay 1-2 px sink (CARRIED-AS-NEW-SEED → see `SEED-PHASE-13-PLAYER-SUB-PIXEL-OFFSET-OR-COLLISION-MASK.md`)

### Phase 12 ledger impact

- G1 CLOSED by Phase 12.6 (2026-05-26)
- G2 CLOSED by Phase 12.7 (2026-05-27)
- G3 ROUTED to Phase 12.9 (was scheduled to close in 12.8)
- G4 ROUTED to Phase 12.9 (anchor-1 + G4 binding gate carry forward)
- Phase 12 `status: complete` flip DEFERRED to Phase 12.9 completion

### Closing artifacts (Phase 12.8)

- Full diagnostic: `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/12.8-DIAGNOSTIC.md`
- All 10 plan SUMMARYs: `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/12.8-{01..07,10}-SUMMARY.md` (08+09 SKIPPED per conditional-on-G3-APPROVED)
- Evidence package: `.planning/phases/12.8-grass-tileset-white-pixels-diagnostic/evidence/`

## Addendum — W3 runtime change reverted post-close (2026-05-27)

After Phase 12.8 terminal close, user clarified that the W3 fix LAYERED color inversion on top of the existing broken state rather than replacing it. Additionally surfaced: nextLevel scene-transition VRAM-clear defect + cross-scene "0F" text artifact (00, 01, 02 are visually the same screen).

**Action taken:** Reverted W3 runtime change. ROM back to pre-12.8 visual baseline. Infrastructure (`isIndexedPng()` + tests) retained for Phase 12.9.

**Status updated:** Was "PARTIALLY RESOLVED" (asset-pipeline boundary closed). Now: asset-pipeline boundary RESET to pre-12.8 + scope expanded for Phase 12.9 to cover scene-transition VRAM-clear in addition to palette wiring. The "PARTIALLY RESOLVED" status still holds because:
- Diagnostic understanding IS resolved (root cause = palette wiring + scene-transition, not just flag)
- `isIndexedPng()` helper + tests stay as Phase 12.9 infrastructure
- 7-target byte-identical baseline (W5 evidence) preserved as historical record
- Anchor-5 PNG evidence + sidecar preserved

## Resolution Log

**2026-06-02 — RESOLVED by Phase 12.9 (palette-inversion-asset-pipeline)**

Closed across the 12.9 wave chain (W4/W5/W6 + the 08a..08h G3 fix rounds):
- **W4** `ConvertZoneTilesetsTask` re-appended `-keep_palette_order` (gated by `isIndexedPng()`) +
  `synthesizeHeader()` emits the `_zone_<id>_tileset_palettes[N]` extern + `_PALETTE_COUNT` macro.
- **W5** `SceneVisitor` emits GBC-gated `set_bkg_palette(...)` for NEW-path zoneRefs.
- **08b** added per-zone `set_bkg_palette` to the `setup_current_level` template (the gameplay path
  W5 was blind to — empty `scene.zoneRefs`), fixing the RC-1 palette inversion.
- **08f (Round 3)** closed the residual sprite/physics defects: sprite-sheet transparency via
  `-keep_palette_order` in `ConvertSpritesTask` (orange → index 0 = transparent on GBC; removes the
  player "box"), GBC-gated `set_sprite_palette` upload for character colors, horizontal-probe
  off-by-one (`+5u`), and the level-switch grounded-reset (level-2 "sunk" fix).

G3 + G4 visual binding gates APPROVED by user. Phase 12 SHIPPED 2026-06-02. Full evidence trail:
`.planning/phases/12.9-palette-inversion-asset-pipeline/12.9-11-SUMMARY.md` and the
`## Round 8` section of `.planning/phases/12-.../12-27-SUMMARY.md`.
