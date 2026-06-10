# D-V3 Visual Finding: GBC Screenshot Black-Screen Root Cause

**Phase:** 10.1-metasprites-surplus-codegen-defects-inserted
**Plan:** 10.1-19 (diagnostic)
**Defect:** DEF-10.1-13-C ("GBC screenshot completely black" — UAT verdict from Plan 10.1-13)
**Date:** 2026-05-19
**Author:** plan-10.1-19 executor

---

## TL;DR

**Named root cause:** Codegen-level **bootstrap-order mismatch** in
`GBDKPipelineV2.buildMainFunction()`. Specifically:

> The four `set_sprite_palette()` calls (gray/pink/cyan/green) are emitted
> inside `play_enter()` (a scene-enter function called AFTER `DISPLAY_ON`),
> rather than in `main()` BEFORE `DISPLAY_ON` (as the reference does at
> `metasprites.c:165-168`). As a result, the first PPU frame post-DISPLAY_ON
> composites with OCPD palette RAM that is still in its default state from
> `cgb_compatibility()` — which leaves sprite palettes uninitialized / zero
> on GBC and produces an all-black sprite render. The BG render also fails
> because `fill_bkg_rect` + `set_bkg_data` are likewise deferred to
> `play_enter()`.

This **falsifies the three hypotheses** in `deferred-items.md` for
DEF-10.1-13-C ((a) no DSL palettes, (b) no `set_sprite_palette` emission,
(c) no `cgb_compatibility()`) — all three are emitted CORRECTLY at the
codegen level. The defect is in **emission LOCATION + ORDER**, not in the
existence of the emissions themselves.

**Fix shape for Plan 10.1-20:** Refactor
`GBDKPipelineV2.buildMainFunction()` to mirror the reference's 9-step
bootstrap sequence:

1. Prepend `DISPLAY_OFF;` as the first statement.
2. Keep `cgb_compatibility();` second (already correct).
3. Hoist palette-load calls (`set_sprite_palette()` / `set_bkg_palette()`)
   out of scene-enter functions into `main()` immediately after
   `cgb_compatibility()`.
4. Keep sound init (NR52/NR50/NR51) anywhere — does not interact with palette.
5. Reorder LCDC sequence to `SHOW_BKG; SHOW_SPRITES; SPRITES_8x8; DISPLAY_ON;`
   with `DISPLAY_ON` LAST.
6. Defer scene-enter call to AFTER `DISPLAY_ON` (already correct in current
   pipeline — keep this).

Plan 10.1-20 sub-tasks (RED -> GREEN):
- RED gate: `DV3GbcPaletteWriteDiagnosticTest` (Tests 1, 2, 3) — already
  committed by this plan, deliberately RED.
- GREEN gate: refactor `buildMainFunction()` to land the 4 palette-write
  calls in `main()` BEFORE `DISPLAY_ON`, prepend `DISPLAY_OFF`, reorder
  SHOW_BKG/SHOW_SPRITES/SPRITES_8x8/DISPLAY_ON.
- Regression guard: `SpritePaletteSlotEmissionTest` (existing) must remain
  GREEN — distinct slot indices 0/1/2/3 preserved.
- ROM smoke: `./gradlew :gbkt-examples:metasprites:buildRom` exit 0,
  ROM size unchanged within 5%.
- UAT re-shoot: capture behavior3-subpalette-cycle-gbc.png at rot=8 on GBC
  emulator — must show visible elephant in cyan palette (slot 2), not black.

---

## Evidence Walk-Through

### 1. Grep of port's generated C — 4 distinct palette writes emitted

(See `gbc-palette-init-grep.txt` for full output.)

```
gbkt-examples/metasprites/build/gbkt/generated/main.c:209:    cgb_compatibility();
gbkt-examples/metasprites/build/gbkt/generated/main.c:234:    set_sprite_palette(0u, 1u, gray_pal);
gbkt-examples/metasprites/build/gbkt/generated/main.c:235:    set_sprite_palette(1u, 1u, pink_pal);
gbkt-examples/metasprites/build/gbkt/generated/main.c:236:    set_sprite_palette(2u, 1u, cyan_pal);
gbkt-examples/metasprites/build/gbkt/generated/main.c:237:    set_sprite_palette(3u, 1u, green_pal);
```

Lines 234-237 are inside `play_enter()` (which starts at line 233). Lines
214 (`DISPLAY_ON;`) and 218 (`play_enter();`) both come BEFORE these
palette writes.

### 2. Reference trace — palette writes happen BEFORE DISPLAY_ON

(See `reference-gbc-palette-trace.md` for full trace.)

```c
void main(void) {
    DISPLAY_OFF;                                          // step 1
    cgb_compatibility();                                  // step 2
    set_sprite_palette(OAMF_CGB_PAL0, 1, gray_pal);       // step 3  -- BEFORE DISPLAY_ON
    set_sprite_palette(OAMF_CGB_PAL1, 1, pink_pal);
    set_sprite_palette(OAMF_CGB_PAL2, 1, cyan_pal);
    set_sprite_palette(OAMF_CGB_PAL3, 1, green_pal);
    fill_bkg_rect(...); set_bkg_data(...);                // step 4-5
    load_and_duplicate_sprite_tile_data();                // step 6
    SHOW_BKG; SHOW_SPRITES;                               // step 7
    SPRITES_8x8;                                          // step 8
    DISPLAY_ON;                                           // step 9  -- LAST
    /* game loop ... */
}
```

### 3. Side-by-side diff — three codegen-level gaps

(See `port-vs-reference-palette-write-diff.txt` for full table.)

| Gap | Issue |
|-----|-------|
| GAP-1 | No `DISPLAY_OFF` at port main() entry |
| GAP-2 | Palette writes deferred to play_enter() (AFTER DISPLAY_ON, AFTER SHOW_SPRITES, AFTER set_sprite_data) |
| GAP-3 | Port emits `DISPLAY_ON` BEFORE `SHOW_BKG`/`SHOW_SPRITES`/`set_sprite_data`; reference emits `DISPLAY_ON` LAST |

### 4. Pipeline source site

`gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt:3697-3753`
— `buildMainFunction()`. The mainBody buildList does not emit any
`set_sprite_palette()` calls; palette emission is delegated 100% to
`SceneVisitor.visit()` via the `palette(p)` DSL call lowering, deferred to
scene-enter.

---

## Hypothesis Verdicts

| DEF-10.1-13-C Hypothesis | Verdict | Evidence |
|--------------------------|---------|----------|
| (a) DSL doesn't declare GBC color palettes | **FALSIFIED** | Metasprites.kt:95-122 declares 4 distinct `spritePalette { }` blocks; main.c:33-36 emits 4 distinct `palette_color_t[4]` arrays with non-zero color content matching reference byte-for-byte. |
| (b) MetaspriteVisitor doesn't emit `set_sprite_palette` | **FALSIFIED** (with refinement) | 4× distinct `set_sprite_palette(slot, 1u, *_pal)` ARE emitted at main.c:234-237 — but in `play_enter()`, not `main()`, and not by MetaspriteVisitor (by SceneVisitor's palette lowering). Calls exist; their LOCATION is wrong. |
| (c) `cgb_compatibility()` doesn't run on GBC mode path | **FALSIFIED** | Single `cgb_compatibility();` call at main.c:209, emitted unconditionally for `GbcTarget != DMG` (GBDKPipelineV2.kt:3702-3704). Port uses `GBC_COMPATIBLE`. |
| (d) Some other GBC bootstrap missing | **CONFIRMED** | Three GAPs identified above: GAP-1 (no DISPLAY_OFF), GAP-2 (palettes after DISPLAY_ON), GAP-3 (DISPLAY_ON not last). |

---

## Plan 10.1-20 Fix Shape

### Required emission changes

```kotlin
// GBDKPipelineV2.buildMainFunction() — proposed sequence
val mainBody = buildList {
    add(CRawCode("DISPLAY_OFF;"))                    // STEP 1 (NEW — GAP-1 fix)
    if (gameIR.config.gbcTarget != GbcTarget.DMG) {
        add(CRawCode("cgb_compatibility();"))         // STEP 2 (unchanged)
    }
    addAll(spritePaletteLoads)                        // STEP 3 (NEW — GAP-2 fix)
    addAll(bgPaletteLoads)                            // STEP 3a (NEW — for BG palettes when DSL adds bgPalette { })
    add(CExprStatement(/* NR52_REG = 0x80 */))        // sound init (anywhere, doesn't matter)
    add(CExprStatement(/* NR50_REG = 0x77 */))
    add(CExprStatement(/* NR51_REG = 0xFF */))
    addAll(allSpriteDataLoads)                        // STEP 4-6 (set_sprite_data, hoisted from play_enter)
    addAll(allBgDataLoads)                            // STEP 4-5 (set_bkg_data / fill_bkg_rect, hoisted from play_enter)
    addAll(spriteOAMInits)                            // (unchanged)
    addAll(poolInitCalls)                             // (unchanged)
    add(CRawCode("SHOW_BKG;"))                        // STEP 7a (reordered, GAP-3 fix)
    add(CRawCode("SHOW_SPRITES;"))                    // STEP 7b
    if (gameIR.metasprites.isNotEmpty()) {
        add(CRawCode("SPRITES_8x8;"))                 // STEP 8
    }
    add(CRawCode("DISPLAY_ON;"))                      // STEP 9 (LAST — GAP-3 fix)
    addAll(startEnterCall)                            // play_enter() AFTER DISPLAY_ON (unchanged)
    add(CWhile(CVar("1"), gameLoopBody))
}
```

### Scope considerations

This change touches **only** `GBDKPipelineV2.buildMainFunction()`. The
`SceneVisitor.visit()` palette-lowering path must also stop emitting
`set_sprite_palette()` for palettes that have already been hoisted to
`main()`. A simple approach:

- **Option A (minimal):** Hoist ONLY the `start` scene's palette loads to
  `main()`; leave non-start scenes' palette loads in their respective
  scene-enter functions. This handles the metasprites case (single scene,
  scene == start) and doesn't break multi-scene games where palettes
  per-scene legitimately differ.

- **Option B (uniform):** Always hoist ALL palette loads to `main()`. Risk:
  conflicting palettes across scenes overwrite each other; only the LAST
  scene's palettes win. Not recommended.

- **Option C (full re-arch):** Add a "boot-time palette load" DSL keyword
  (e.g., `bootPalette(p)` or `config { palettes(p1, p2, p3, p4) }`). Defer
  to a later phase.

**Recommendation: Option A** for Plan 10.1-20 — minimal scope, fixes the
metasprites case, preserves existing multi-scene semantics.

### Files Plan 10.1-20 will modify

| File | Change |
|------|--------|
| `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` | Reorder `buildMainFunction()` mainBody; add `DISPLAY_OFF` prepend; hoist start-scene palette loads + BG/sprite-data loads to main; emit DISPLAY_ON last. |
| `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitor.kt` | When emitting scene-enter for the start scene, skip palette/BG/sprite-data loads that have been hoisted to main(). |
| `gbkt-examples/metasprites/build/gbkt/generated/main.c` | (regenerated) — palette writes move from play_enter (lines 234-237) into main (after cgb_compatibility); DISPLAY_OFF added at top; DISPLAY_ON moves to end of bootstrap. |
| `gbkt-examples/metasprites/build/gbkt/output/metasprites.gb` | (regenerated; gitignored) |

### Tests Plan 10.1-20 will flip RED -> GREEN

- `DV3GbcPaletteWriteDiagnosticTest` (this plan, 3 tests) — all 3 must
  flip from RED to GREEN.

### Tests Plan 10.1-20 must preserve GREEN

- `GbcCompatEmissionTest` — `cgb_compatibility()` placement (5 tests).
- `SpritePaletteSlotEmissionTest` — distinct slot indices 0/1/2/3.
- `BgCheckerboardEmissionTest` — bgFillCheckerboard byte pattern.
- `Seed006SubPaletteSyncTest` — _<id>_subPalette/flipX/flipY global writes.
- `MetaspriteVisitorFrameSwitchTest` — frame-switch emission shape.
- Full `:gbkt-backend-gbdk:test` — no regressions.
- ROM-build smoke: `./gradlew :gbkt-examples:metasprites:buildRom`
  succeeds, ROM size within 5% of current 32 KB.

### UAT re-shoot acceptance

- Behavior 3 (subpalette-cycle-gbc) screenshot must show a visible elephant
  in the appropriate sub-palette at rot=8 (cyan, slot 2). Not black.
- Behavior 1 (animation-advance) screenshot must still show a visible
  elephant in slot 0 (gray) — no regression vs Plan 10.1-13 result.
- BG checker must still render — no regression vs Plan 10.1-13 result.

---

## Open Runtime Questions (Out of Plan 10.1-19 Scope)

Two questions cannot be answered from codegen-level inspection alone. They
require an mGBA runtime palette-RAM dump or similar (orchestrator-driven
post-checkpoint):

1. **Is OCPD palette RAM actually all zeros after `DISPLAY_ON` on real
   GBC?** Confirms the deferred-write hypothesis is the dominant cause vs
   merely a contributing factor.
2. **Does BCPD background palette RAM hold the cgb_compatibility default
   colors?** If NO, an additional cgb_compatibility-mode issue exists.

These questions inform Plan 10.1-20's regression-guard breadth but do not
change the named root cause or the fix shape proposed above.

---

## RED Test Status

`DV3GbcPaletteWriteDiagnosticTest` is **committed RED** (this plan). Status
captured from `./gradlew :gbkt-backend-gbdk:test --tests
"io.github.gbkt.backend.gbdk.codegen.visitor.DV3GbcPaletteWriteDiagnosticTest"`
(Task 4 acceptance criterion):

```
DV3GbcPaletteWriteDiagnosticTest > DISPLAY_ON is the last bootstrap macro before the game loop (RED until Plan 10_1-20) FAILED
    java.lang.AssertionError at DV3GbcPaletteWriteDiagnosticTest.kt:248
DV3GbcPaletteWriteDiagnosticTest > all four sprite palette slots emit before DISPLAY_ON (RED until Plan 10_1-20) FAILED
    java.lang.AssertionError at DV3GbcPaletteWriteDiagnosticTest.kt:215
DV3GbcPaletteWriteDiagnosticTest > set_sprite_palette calls live in main before DISPLAY_ON (RED until Plan 10_1-20) FAILED
    java.lang.AssertionError at DV3GbcPaletteWriteDiagnosticTest.kt:170

3 tests completed, 3 failed
```

All 3 RED tests fail exactly as designed:
- Test 1 (line 170): no `set_sprite_palette()` in main() body
- Test 2 (line 215): pre-DISPLAY_ON region of main() contains 0 of 4 expected slots
- Test 3 (line 248): SHOW_BKG/SHOW_SPRITES emitted AFTER DISPLAY_ON

Plan 10.1-20 GREEN target: refactor `GBDKPipelineV2.buildMainFunction()`
per the "Required emission changes" block above; all 3 tests must flip to
GREEN.
