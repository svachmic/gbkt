# Plan 07.4-35 Task 1 — DIAGNOSIS: track-synthesis fix surface

Date: 2026-05-21
Author: Claude (Opus 4.7) under /gsd-quick directive
Production commit: `8d4c56e2 fix(07.4-35): GAP-TRACK-NOT-RENDERED-AS-CIRCUIT — synthesizer corridor straddles polygon edges`

This document closes the diagnostic-first ordering requirement of Plan 07.4-35
Task 1. The fix surface was isolated by direct A vs B vs C artifact comparison
before any production source change was applied.

## Artifact A — Synthesizer Direct Output

JVM-tier probe: `TrackSynthesizer.synthesize(RACER_WAYPOINTS, mapWidth=19, mapHeight=19, corridorWidth=4)` invoked via
`TrackSynthesizerCircuitShapeTest.print_actual_vs_expected_tilemap_diff` (the Plan 33
diagnostic-only Test 4). Pre-fix output:

```
    0123456789012345678
   +-------------------
 0 |...................
 1 |...................
 2 |...................
 3 |...................
 4 |....###.......###..
 5 |....#############..
 6 |....#############..
 7 |.....##,,,,,,##....
 8 |.....##,,,,,,##....
 9 |.....##,,,,,,##....
10 |.....##,,,,,,##....
11 |.....##,,,,,,##....
12 |.....##,,,,,,##....
13 |.....##########....
14 |....#############..
15 |....###.......###..
16 |....###.......###..
17 |...................
18 |...................
```

Counts (pre-fix): wall=234, drivable=91, grass=36, total=361.

## Artifact B — Emitted const Array

Source: `gbkt-examples/racer/build/gbkt/generated/zone_bank2.c`,
`const UINT8 _zone_track1_tiles[361] = { ... };`. Decoded via row-major split (19
elements per row).

Pre-fix shape: byte-for-byte identical to Artifact A. The codegen pipeline
(`SportVisitor` → `GBDKPipelineV2.buildZoneData`) passes through
`ZoneIR.tileData` without any value transformation; the emitter is correct.

Captured in `06-baseline-zone-tiles-array.txt` (Plan 33 Task 1 evidence).

## Artifact C — Expected Corridor

Source: `07-expected-circuit-tilemap-ascii-art.txt` (Plan 33 Task 1, locked
GREEN contract). Hand-derived from D-11 (corridor width 4 → 3-tile-thick
drivable annulus) and D-17 (enclosed loop, interior non-drivable).

```
    0123456789012345678
   +-------------------
 0 |...................
 ...
 4 |....#############..    (continuous 13-cell drivable band)
 5 |....#############..
 6 |....#############..
 7 |....###,,,,,,,###..    (3-cell side corridors + 7-cell grass interior)
 ...
13 |....###,,,,,,,###..
14 |....#############..
15 |....#############..
16 |....#############..
 ...
```

Counts (expected): wall=192, drivable=120, grass=49, total=361.

## Verdict

**A == B != C.** Pre-fix mismatch_count = 55 (file 05 `mismatch_count=55`
before-fix snapshot). The emitter writes the synthesizer's IntArray verbatim;
the bug lives upstream in the synthesizer's rasterization algorithm. The
high-contrast tileset values (Plan 17) are also not the issue — the
TILE_WALL / TILE_DRIVABLE / TILE_GRASS constants are correctly mapped to
visual values, and the SHAPE is wrong independently of color.

The pre-fix synthesizer:

1. Ran `scanlineFill` to mark INSIDE cells (rows 5..14, cols 5..14 for the
   racer's 10×10 polygon — correct).
2. Computed `distanceFromEdges` for each inside cell (perpendicular distance
   from cell center to the nearest polygon edge segment).
3. In `composeTiles`, classified each cell as:
   - WALL if outside polygon (D-17 enclosure)
   - DRIVABLE if inside AND `distFromEdge < halfCorridor` (= 2.0 for
     corridorWidth=4)
   - GRASS if inside AND `distFromEdge >= halfCorridor`

This erosion-from-interior approach produces the broken corner pads at row 4
and rows 15-16: cells like (4,4), (4,5), (4,6) are OUTSIDE the polygon
(row 4 below polygon top edge y=5) and therefore forced to WALL — but the
expected corridor extends 1 cell OUTSIDE the polygon edge. The interior
mid-band (rows 7-13) was also classified as drivable where it should be
grass, because the erosion radius captured cells too far from the polygon
boundary as "near edge" (e.g., (13,13) at perp distance 1.5 to bottom and
right edges was marked drivable, violating D-17).

## Recommended Fix Scope

**File: `gbkt-genre-sport/src/main/kotlin/io/github/gbkt/genre/sport/codegen/TrackSynthesizer.kt`**

**Functions to replace:** `distanceFromEdges` and `composeTiles` (helper
`pointToSegmentDistance` becomes dead code and is removed).

**Replacement strategy:** Bresenham edge rasterization + Chebyshev
neighborhood thickening:

1. For each polygon edge, rasterize the cells the line passes through using
   the Bresenham line algorithm (`stampThickenedEdge` in the GREEN code).
2. For each rasterized edge cell, stamp its `(2·halfPerp + 1) ×
   (2·halfPerp + 1)` Chebyshev neighborhood as drivable
   (`stampNeighborhood`).
3. `halfPerp = max(1, (corridorWidth - 1) / 2)`. For corridorWidth=4 →
   halfPerp=1 → 3-tile-thick band straddling every polygon edge: 1 cell
   outside polygon + edge cell + 1 cell inside.
4. New `composeTiles`: cells in thickened band → DRIVABLE; inside polygon
   AND not in band → GRASS (D-17); else → WALL.

This approach has two structural advantages:

- **Edge-straddling correctness:** the corridor extends symmetrically on
  both sides of the polygon edge, matching the expected 3-tile annulus
  per D-11 without per-corner special-casing.
- **D-17 by construction:** cells beyond the corridor's perpendicular
  reach into the polygon interior are classified as GRASS, ineligible to
  be cut across — lap detection cannot be defeated by interior shortcuts.

**Diff scope:** ONE production file (`TrackSynthesizer.kt`) plus regenerated
`main.c` / `zone_bank2.c`. Test calibration in
`RacingPlayerTraversabilityTest.kt` Test 2/3 must be recalibrated as a
downstream consequence — the original sequence parameters were empirically
fit against the buggy output where row 4 was wall, so the player blocked at
py=29; the corrected corridor lets the player descend to py=17, overshooting
CP-0. The test CONTRACT (visit all 4 waypoints in declared order) is
preserved; only the sequence-length integers change.

**Out of scope (NOT the fix surface):**

- `SportVisitor.buildRaceEnterOps` — emitter is correct (Artifact A == B).
- `GBDKPipelineV2.buildZoneData` — pass-through is correct.
- Tileset visual values (Plan 17 high-contrast) — color/shape are orthogonal.
- `forcePerimeterToWalls` — perimeter logic is correct and preserved.
- `forceWaypointNeighborhoodsToDrivable` — waypoint-safety pass is correct
  and preserved (becomes redundant with the new corridor since waypoints
  are on rasterized edge cells, but still acts as safety net for waypoints
  near the map perimeter).
