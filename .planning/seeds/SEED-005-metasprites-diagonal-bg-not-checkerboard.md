---
id: SEED-005
status: dormant
planted: 2026-05-18
planted_during: v1.0 / Phase 10 closeout (Plan 10-20)
trigger_when: when Phase 10.1 (metasprites surplus codegen defects) is opened
scope: small
---

# SEED-005: bgFillCheckerboard() emits diagonal stripe pattern, not checkerboard (D-V2)

## Why This Matters

The `bgFillCheckerboard()` helper added in Plan 10-11 is misnamed. Its byte pattern
encodes a DIAGONAL LINE (top-left to bottom-right), not a checkerboard. When this
tile is tiled across the screen by `fill_bkg_rect`, every 8x8 cell contains a
diagonal black stripe — the background renders as a screen covered in parallel
diagonal stripes, not alternating checker squares.

The naming is confusing and the rendering is incorrect for what "checkerboard" means
visually. The reference GBDK `metasprites.c` uses a true checker pattern as background.

## Root Cause

The byte literal in `MetaspriteBuilder.bgFillCheckerboard()` (or the emitting codegen
path in `gbkt-lang`) is:

```
0x80, 0x80, 0x40, 0x40, 0x20, 0x20, 0x10, 0x10,
0x08, 0x08, 0x04, 0x04, 0x02, 0x02, 0x01, 0x01
```

This is the 2bpp encoding of a single diagonal line (1 pixel wide, from top-left corner
to bottom-right corner). Each pair of bytes `(0x80, 0x80)` encodes row 0 as `10000000`
in both planes (= dark pixel at bit 7, light elsewhere). The bits shift right by one on
each row pair, creating a staircase diagonal.

A real checkerboard (4x4 squares) would use a repeating `0xAA,0xAA,0x55,0x55` or
`0xF0,0xF0,0x0F,0x0F` pattern — alternating blocks of 4 columns wide, 4 rows tall.

## Fix Routes

**Option A (1-line, recommended):** Replace the diagonal byte literal in place with the
correct checkerboard pattern. The reference `metasprites.c` defines the pattern as:

```c
const uint8_t checker_tile[] = {0xF0, 0xF0, 0xF0, 0xF0, 0x0F, 0x0F, 0x0F, 0x0F,
                                  0xF0, 0xF0, 0xF0, 0xF0, 0x0F, 0x0F, 0x0F, 0x0F};
```

(8 rows of 8 pixels: top half = `11110000` in plane 0, bottom half = `00001111` —
creates 4x4 checker squares when tiled.)

**Option B (~10 lines):** Rename the existing helper to `bgFillDiagonal()` (preserving
its current behavior) and add a new `bgFillCheckerboard()` with the correct pattern.
More honest semantically but requires callers of the old name to be updated.

Either way: add a JVM-tier test that verifies the emitted tile byte literal is the
checker pattern, not the diagonal pattern, so this regression cannot recur silently.

## Evidence

- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior1-animation-advance.png`
  (background visible: parallel diagonal stripes instead of checker squares)
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior2-flip-cycle.png`
  (same background visible)
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/oracle-comparison.md` §D-V2
- Phase 10 UAT.md §"Defect D-V2"

## Affected Files

- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/MetaspriteBuilder.kt` (byte literal)
  OR `gbkt-backend-gbdk/.../codegen/visitor/MetaspriteVisitor.kt` (if pattern is emitted there)
- `gbkt-examples/metasprites/src/main/kotlin/io/github/gbkt/examples/metasprites/Metasprites.kt`
  (caller: `bgFillCheckerboard()` call in play enter block)

## Scope Estimate

**Small** — 1-line literal replacement + 1 test. Option B adds a rename refactor (~5 extra lines).

## Why Not Fixed in Phase 10

Phase 10's hard scope cap is ONE named codegen bug-fix (the pipeline wiring gap from Plan 10-15).
D-V2 was surfaced only after the first successful ROM screenshot during UAT Plan 17 — too late
to add to Phase 10 scope. Seeded per D-06 doctrine for Phase 10.1.

## When to Surface

**Trigger:** when Phase 10.1 (metasprites surplus codegen defects) is opened.

Phase 10.1 owns D-V1, D-V2, D-V3 together — they are all visual-parity defects best fixed
in a single Phase 10.1 pass that culminates in screenshots matching the reference ROM visual.

## Related

- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/10-UAT.md` §D-V2
- `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/oracle-comparison.md` §D-V2
- SEED-004 (D-V1: corrupted tile rendering) — companion visual defect fixed in same Phase 10.1
- SEED-006 (D-V3: stale _elephant_subPalette) — companion mechanism debug defect
