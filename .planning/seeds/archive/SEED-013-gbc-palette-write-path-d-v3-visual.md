---
id: SEED-013
status: active
planted: 2026-05-19
planted_during: v1.0 / Phase 10.1 close (after Plan 10.1-22 regression)
trigger_when: surfaces in Phase 10.2 (already inserted via gsd-sdk query phase.insert 10)
scope: medium
triage_disposition: VERIFIED-ALREADY-FIXED
triage_evidence: ".planning/phases/16-seed-triage/TRIAGE.md#SEED-013"
triage_date: 2026-06-12
---

# SEED-013: GBC Palette Write Path — D-V3 Visual Closure (Phase 10.2 Driver)

## Why This Matters

Phase 10.1 attempted 4 rounds of inline diagnose+fix on D-V3 (GBC sub-palette
visual closure):
- Round 1: Plan 10.1-04 closed variable-mirror layer (`_elephant_subPalette`
  syncs from `_rot >> 2` correctly at runtime)
- Round 2: Plan 10.1-13 surfaced the gap (mechanism GREEN, visual all-black)
- Round 3: Plans 10.1-19/20 closed bootstrap-order layer (DV3GbcPaletteWriteDiagnosticTest
  3/3 GREEN, ROM smoke clean), but visual still all-black
- Round 4: Plans 10.1-21/22 closed Coffee-GB cgb_compatibility-only-writes-BGP
  layer (DV3VisualV2DiagnosticTest 2/2 GREEN, set_bkg_palette emitted), and
  user reports STILL ALL BLACK after the fix lands

**CRITICAL USER-OBSERVED EVIDENCE (resurrect this in Phase 10.2):**
> "the important part is that we had EVIDENCE that cyan worked - the metasprite
> was broken and the checkerboard was stripes, just like the other screenshots.
> But it was cyan once already."

The pre-Plan-19 ROM rendered cyan sprites on black BG. Post-Plan-22 ROM renders
solid black. This means **one of Plans 19/20/22 introduced a regression that
killed the sprite-palette rendering that was previously working.**

Most likely candidates:
- Plan 22's `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` may interact with
  Plan 20's `set_sprite_palette` hoist in a non-obvious way (timing, register
  conflict, palette-slot overlap)
- Plan 22's `bgFillCheckerboard` hoist into `main()` may be filling the screen
  WITH the BG palette 0 BEFORE sprites render, hiding them
- Plan 20's bootstrap-order refactor (DISPLAY_OFF prepend + LCDC reorder + start-
  scene init hoist) may have changed something subtle about when sprite palettes
  are actually committed to OCPD

## When to Surface

**Trigger:** Phase 10.2 has already been inserted via `gsd-sdk query phase.insert 10`.
Surface this seed at `/gsd:discuss-phase 10.2` time.

## Suggested Phase 10.2 Approach

1. **First action — REVERT vs. forward-fix decision.** Compare pre-Plan-19 ROM
   build (`git checkout cbe81d29 -- gbkt-backend-gbdk/.../GBDKPipelineV2.kt`,
   `:gbkt-examples:metasprites:clean buildRom`, capture screenshot). If cyan
   reappears, the named cause is in the Plan 19/20/22 chain — revert those
   changes selectively until cyan returns, then re-add them one at a time to
   find the exact regression site.
2. **Real-hardware vs. Coffee-GB comparison.** Plan 21's analysis correctly
   identified Coffee-GB's skip-bootstrap as a confound, but the fix may not be
   portable to real GBC hardware. Phase 10.2 should ideally test on either a
   real GBC (via flash cart) OR a different GBC emulator (BGB, SameBoy) that
   does include boot ROM emulation.
3. **Memory-read MCP tool from SEED-012** would be invaluable here — direct
   read of OCPD/BCPD palette RAM contents at frame 60 would conclusively show
   what palette state actually exists when compositing happens.

## Existing Diagnostic Evidence (in Phase 10.1)

- `.planning/phases/10.1-…/evidence/d-v3-visual-diagnostic/` (Plan 19 analysis)
- `.planning/phases/10.1-…/evidence/d-v3-visual-diagnostic-v2/` (Plan 21 analysis,
  includes Coffee-GB internals trace)
- `.planning/phases/10.1-…/evidence/plan-20-rom-build-logs/`
- `.planning/phases/10.1-…/evidence/plan-22-rom-build-logs/`
- `.planning/phases/10-…/evidence/uat-screenshots/behavior3-subpalette-cycle-gbc.png`
  (current state: all-black despite codegen-tier GREEN)
- All SUMMARY.md files for Plans 10.1-04, 10.1-13, 10.1-19, 10.1-20, 10.1-21, 10.1-22

## Mechanism Evidence (still GREEN — these don't need re-investigation)

- `rot` cycles 0→1→…→8 via A-press
- `_elephant_subPalette` syncs to `rot >> 2` (reads 0/0/0/0/1/1/1/1/2 across 8 presses)
- All 8 codegen-shape RED tests across Plans 19+21 are now GREEN
- main.c contains: cgb_compatibility, 4× set_sprite_palette, set_bkg_palette,
  bgFillCheckerboard hoist, SHOW_BKG, SHOW_SPRITES, SPRITES_8x8, DISPLAY_ON
  in reference-aligned order

The gap is **NOT** in any of these layers. Phase 10.2 must look elsewhere —
likely a Coffee-GB emulator quirk, a sprite-palette-vs-bg-palette register
conflict, or a regression introduced by one of the 3 inline rounds.

## Hard Scope Cap

ONE named visual cause. ONE fix. Output: cyan elephant in behavior3 re-shoot.
No DSL surface changes. No new IR fields. The DSL+IR+visitor stack is correct;
the gap is in the asm/hardware-init layer.

## Related Seeds

- SEED-012 (memory-read MCP tool) — would directly help Phase 10.2 diagnostic
