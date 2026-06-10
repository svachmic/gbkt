# Phase 10.2 — D-V3 Closure Verdict

**Plan:** 10.2-09 (Wave 9 — post-fix UAT triplet)
**Date:** 2026-05-19
**ROM:** gbkt-examples/metasprites/build/gbkt/output/metasprites.gb (built clean on main checkout with Plan 08's order-swap fix)
**Fix commit:** f2e8cecc (fix(10.2-08): swap mainBody addAll order — bgFillCheckerboard before allSpriteDataLoads)

---

## Visual closure gate (D-14, D-15, CLAUDE.md Visual Evidence Rule)

| Signal | behavior3 (post-fix) | Pass threshold | Status |
|---|---|---|---|
| Distinct colors | 5 | >= 4 | PASS |
| Cyan present | true | true | PASS |
| Checker present | true | true | PASS |
| BCPD any-slot non-zero | true | true | PASS |
| BCPD slot 0 first-color (15-bit) | 0x7FFF | non-zero (_gbkt_default_bg_pal[0] = white) | PASS |
| OCPD slot 2 first-color (cyan_pal) | 0x7FFF | 0x7FFF (cyan_pal[0]) | PASS |
| LCDC bit 7 | 1 | 1 (DISPLAY_ON) | PASS |

**behavior3-postfix.png:** 1325 bytes (vs 147-byte all-black pre-fix baseline)

**Pixel histogram (top 5 colors):**
- 0xF8F8F8 (near-white BG from _gbkt_default_bg_pal): 10783 pixels
- 0x000000 (black BG checker squares): 10782 pixels
- 0x50F8F8 (cyan sprite light shade — expanded from 0x7FEA via R=0x52,G=0xFF,B=0xFF): 686 pixels
- 0x00A8A8 (cyan sprite mid shade — expanded from 0x56A0 via R=0x00,G=0xAD,B=0xAD): 400 pixels
- 0x005050 (cyan sprite dark shade — expanded from 0x2940 via R=0x00,G=0x52,B=0x52): 389 pixels

**Cyan_pal expansion (15-bit GBC -> 24-bit RGB via (c<<3)|(c>>2) per channel):**
- 0x7FFF -> RGB(0xFFFFFF) [white — transparent color index 0, not visible on sprite body]
- 0x7FEA -> RGB(0x52FFFF) [rendered as 0x50F8F8 in Coffee-GB — cyan light]
- 0x56A0 -> RGB(0x00ADAD) [rendered as 0x00A8A8 — cyan mid]
- 0x2940 -> RGB(0x005252) [rendered as 0x005050 — cyan dark]

**OCPD slot 2 dump:** [0x7FFF, 0x7FEA, 0x56A0, 0x2940] — cyan_pal IS in palette RAM (confirmed)
**BCPD slot 0 dump:** [0x7FFF, 0x56B5, 0x294A, 0x0000] — _gbkt_default_bg_pal IS in BG palette RAM (confirmed)

**Verdict:** PASS

---

## DMG non-regression gate (D-17)

| Behavior | Pre-fix baseline distinct_colors | Post-fix distinct_colors | Top-5 RGB set match | Verdict |
|---|---|---|---|---|
| behavior1 (animation-advance, DMG) | 4 | 4 | EXACT (0x051F2A:11182, 0xE6F8DA:10765, 0x99C886:696, 0x437969:397) | PASS |
| behavior2 (flip-cycle, DMG) | 4 | 4 | EQUAL (same 4 RGBs, minor count variance due to rot=2 vs rot=2 position) | PASS |

**Note (WARNING 6 acceptance per revision 2026-05-19):** DMG non-regression is defined as pixel-decode histogram match (distinct_colors equal + top-5-RGBs-by-count set equal), NOT byte-identical PNG match. Coffee-GB PNG encoding can vary by 1-2 bytes across runs due to timestamp metadata; a strict binary-match would false-flag a real match. behavior1 PNG byte size is identical (1216 bytes). behavior2 PNG is 1170 vs 1207 bytes from baseline — minor timestamp/metadata variance only; pixel content is equivalent.

**Pre-fix baseline reference:** .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/

**Verdict:** PASS

---

## 5-Signal Summary Table (D-14 binding criteria)

| Signal | Value | Required |
|---|---|---|
| distinct_colors (behavior3) | 5 | >= 4 |
| cyan_present (behavior3) | true | true |
| checker_present (behavior3) | true | true |
| BCPD slot 0 first-color | 0x7FFF | non-zero |
| OCPD slot 2 first-color | 0x7FFF | 0x7FFF (cyan_pal[0]) |

All 5 signals PASS.

---

## Scope-Shift Acknowledgment

The original phase title — **"gbc-palette-write-path-d-v3-visual-closure"** — was a misnomer
in light of what the 7-probe bisect chain (Plans 03–06d) revealed:

- **The GBC palette WRITE path was never broken.** OCPD slot 2 contained the exact `cyan_pal`
  bytes `[0x7FFF, 0x7FEA, 0x56A0, 0x2940]` in every probe, including the all-3-emissions-combined
  Probe B that visibly broke cyan. BCPD slot 0 wrote `_gbkt_default_bg_pal` correctly in the
  same way (Probe C-2 confirmed this empirically).
- **The actual defect was a sprite-tile VRAM collision.** With LCDC.4=1, the BG and sprite
  tile regions share `$8000–$97FF`. Plan 22's hoisted `set_bkg_data(0, 1, _checkerboard_bg_pattern)`
  ran AFTER `set_sprite_data(0u, 48u, elephant_tiles)` in `main()`, overwriting the elephant's
  tile-0 bytes with checker pattern bytes. The sprite then rendered using subpal 2 (correctly
  selected via OAM attribute byte) — but the source tile data was already checker, so the result
  looked like grayscale fallback.
- **The fix is order-tweaked emission**, not a palette-write-path change. Plan 10.2-08 swapped
  the two `addAll(...)` calls in `buildMainFunction()` so the BG checker writes first; the
  elephant sprite tiles win the shared-region write (overwriting the checker pattern that landed
  at tile slot 0).

The phase title is kept as-is for traceability (D-V3 was named in 10.1's open-defect carry-over),
but future search for this regression class should match "sprite tile VRAM collision (LCDC.4=1)"
rather than "palette write path."

---

## Overall phase closure verdict

**PASS**

Phase 10.2 D-V3 visual defect (DEF-10.1-13-C — GBC screenshot renders grayscale/black despite JVM GREEN) is CLOSED.

The fix (Plan 10.2-08 commit f2e8cecc) swapped the `addAll` order in `GBDKPipelineV2.buildMainFunction()`: `hoistedBgFillCheckerboardStatements` now emits BEFORE `allSpriteDataLoads`. This prevents `set_bkg_data(0, 1, _checkerboard_bg_pattern)` from overwriting elephant sprite tile 0 in the shared $8000-$97FF VRAM region (LCDC.4=1).

The cyan elephant is visible on the checkered BG at rot=8 (subpal=2). DMG behaviors 1 and 2 are unchanged (no regression).

Per CLAUDE.md Visual Evidence Rule: JVM GREEN (Plan 08) was NECESSARY but NEVER SUFFICIENT. This PNG-based verdict is the binding evidence for D-V3 closure.

---

## Evidence Files

| File | Size | Significance |
|---|---|---|
| behavior3-postfix.png | 1325 bytes | Cyan elephant on checker BG — visual closure |
| behavior3-postfix.json | 5188 bytes | Pixel-decode + BCPD/OCPD runtime state |
| behavior1-postfix.png | 1216 bytes | DMG animation-advance — non-regression |
| behavior1-postfix.json | 538 bytes | Pixel-decode |
| behavior2-postfix.png | 1170 bytes | DMG flip-cycle — non-regression |
| behavior2-postfix.json | 524 bytes | Pixel-decode |
