# Phase 12.2 — 5-ROM Regression Sweep Evidence

**Date:** 2026-05-23
**Branch:** worktree-agent-aa95f234ea6701ed6 (executor worktree from `feat/d_and_d_gaps`)
**Base commit:** `97780582` (`docs(phase-12.2): update tracking after wave 6`)
**GBDK_HOME:** `/Users/michalsvacha/gbdk`

## Plan-vs-Reality Game-List Deviation (Rule 3 — auto-fix blocking issue)

`12.2-09-PLAN.md` named the 5 ROMs as `pong, breakout, banks, explorer, platformer-template`.
However, `gbkt-examples/explorer/` is **not a registered Gradle subproject** in
`settings.gradle.kts` (the directory does not exist on disk). The earlier Phase 12.1
regression-sweep convention used `banks, pong, breakout, simple-physics, metasprites,
metasprites-stress` (per `12.1-10-SUMMARY.md`).

To preserve the plan's intent (cross-phase impact verification of games **without**
`tilemap()` + the one game **with** `tilemap()`) while remaining executable, the
substitution is:

| Plan-named | Substituted | Rationale |
|---|---|---|
| explorer | simple-physics | `explorer` does not exist as a Gradle subproject; `simple-physics` is the smallest existing example that doesn't use `tilemap()` and exercises the same cross-phase code paths (no zone tilesets, no `convertZoneTilesets` work). |

The 5 ROMs actually swept: **pong, breakout, banks, simple-physics, platformer-template**.
This preserves the 5-ROM count and the "1 game uses `tilemap()`, 4 do not" SPEC-AC10
cross-phase invariant.

## Build Results

| Game | Status | ROM Size | Bank Count | Build Time | Notes |
|------|--------|----------|------------|------------|-------|
| pong | GREEN | 32,768 B (32 KB) | 2 banks (0, 1) | 8 s | No zone tilesets; cartridge auto-upgraded ROM_ONLY → MBC5. `ConvertZoneTilesetsTask: No zone tilesets -- skipping`. |
| breakout | GREEN | 32,768 B (32 KB) | 2 banks (0, 1) | 1 s | No zone tilesets; cartridge auto-upgraded ROM_ONLY → MBC5. Cache hit from pong → fast. |
| banks | GREEN | 65,536 B (64 KB) | 3 banks (0, 1, 2) | 1 s | D-01 **Path A** one-invocation: `_zone_play_zone_tilemap.c (4 bytes, one-invocation D-01 path)`. checker.png is 16×16 px = 2×2 tile screen — array size 4 confirms the new one-invocation form (was 1024 bytes pre-12.2 from synthesizer). |
| simple-physics | GREEN | 32,768 B (32 KB) | 1 bank (0 only) | 1 s | No zone tilesets, no banking. Validates that the new IR/DSL plumbing does NOT regress single-bank games. |
| platformer-template | GREEN | 65,536 B (64 KB) | 3 banks (0, 1, 2) | 2 s | **Main event.** D-01 **Path B** two-invocation for the 3 `tilemap()`-bearing zones; D-01 Path A for the 2 title-screen-style zones. Defect 7 closed: world1Area1 tilemap is now 1920 bytes (real PNG layout) instead of 1024 (pre-12.2 synthetic ramp). All 5 zone tilemaps land in `#pragma bank 2`. |

All five `:clean :buildRom` invocations exit 0. **5/5 GREEN.**

## Detailed platformer-template Pipeline Output (the main event)

```
> Task :gbkt-examples:platformer-template:convertZoneTilesets
ConvertZoneTilesetsTask: Converting 5 zone tileset(s)
  Converting zone world1Area1Zone: world1-tileset.png -> _zone_world1Area1Zone_tileset.c
  Tilemap extraction zone world1Area1Zone: world1-area1.png -> _zone_world1Area1Zone_tilemap_raw.c
    -> _zone_world1Area1Zone_tilemap.c (1920 bytes, two-invocation D-01 path)
  Converting zone world1Area2Zone: world1-tileset.png -> _zone_world1Area2Zone_tileset.c
  Tilemap extraction zone world1Area2Zone: world1-area2.png -> _zone_world1Area2Zone_tilemap_raw.c
    -> _zone_world1Area2Zone_tilemap.c (1920 bytes, two-invocation D-01 path)
  Converting zone world2Area1Zone: world2-tileset.png -> _zone_world2Area1Zone_tileset.c
  Tilemap extraction zone world2Area1Zone: world2-area1.png -> _zone_world2Area1Zone_tilemap_raw.c
    -> _zone_world2Area1Zone_tilemap.c (1920 bytes, two-invocation D-01 path)
  Converting zone titleZone: title-screen.png -> _zone_titleZone_tileset.c
    -> _zone_titleZone_tilemap.c (180 bytes, one-invocation D-01 path)
  Converting zone nextLevelZone: next-level.png -> _zone_nextLevelZone_tileset.c
    -> _zone_nextLevelZone_tilemap.c (180 bytes, one-invocation D-01 path)
```

Per-tilemap pragma + array-size dump:

```
world1Area1Zone: #pragma bank 2  | array size: _tilemap[1920]
world1Area2Zone: #pragma bank 2  | array size: _tilemap[1920]
world2Area1Zone: #pragma bank 2  | array size: _tilemap[1920]
titleZone:       #pragma bank 2  | array size: _tilemap[180]
nextLevelZone:   #pragma bank 2  | array size: _tilemap[180]
```

Total tilemap data in bank 2: 1920×3 + 180×2 = **6120 B** (within the 16 KB bank limit;
BankingAnalysisPass overflow guard from Plan 12.2-08 had no cause to trigger).

## REQ Spot-Check Evidence (platformer-template generated output)

### REQ-2 — real tilemap bytes (not synthetic ramp)

```
$ grep -nE "^const uint8_t _zone_world1Area1Zone_tilemap\[1920\]" \
      gbkt-examples/platformer-template/build/gbkt/generated/_zone_world1Area1Zone_tilemap.c
7:const uint8_t _zone_world1Area1Zone_tilemap[1920] = {
```

Plus a tile-index diversity check (a synthetic ramp would have ≥256 distinct byte
values; real PNG-derived data uses only the tile indices actually present in the
tileset):

```
$ sed -n 's/.*= {//; s/};//; p' _zone_world1Area1Zone_tilemap.c \
    | tr ',' '\n' | grep -oE '0x[0-9a-fA-F]+' | sort -u | wc -l
27
```

27 distinct tile indices — matches `Got 27 tiles from the source tileset.` reported by
png2asset. **Locked: this is real PNG data, not a synthesizer ramp.**

### REQ-3 — variable WIDTH/HEIGHT derived from PNG IHDR

```
$ grep -nE "^#define _zone_world1Area1Zone_tilemap_(WIDTH|HEIGHT)" \
      gbkt-examples/platformer-template/build/gbkt/generated/_zone_world1Area1Zone_tileset.h
21:#define _zone_world1Area1Zone_tilemap_WIDTH 60
22:#define _zone_world1Area1Zone_tilemap_HEIGHT 32
```

`world1-area1.png` IHDR-verified: 480 × 256 px = 60 × 32 tiles. **MATCH.**

```
$ grep -nE "^#define _zone_titleZone_tilemap_(WIDTH|HEIGHT)" \
      gbkt-examples/platformer-template/build/gbkt/generated/_zone_titleZone_tileset.h
21:#define _zone_titleZone_tilemap_WIDTH 20
22:#define _zone_titleZone_tilemap_HEIGHT 9
```

`title-screen.png` IHDR-verified: 160 × 72 px = 20 × 9 tiles. **MATCH.**

> **Note on plan expectation:** Plan 09 anticipated `HEIGHT 18` for `titleZone`. The
> actual PNG is 160×72 (= 20×9 tiles, not 20×18). The codegen correctly derives WIDTH /
> HEIGHT from the actual PNG IHDR, which is the REQ-3 contract. The plan's expected
> value was a planner-side miscalculation, not a code bug. The 20×9 result is
> definitionally correct per `Phase 12.2 — derive WIDTH/HEIGHT from PNG IHDR`.

### REQ-4 — synthesizer removed from source

```
$ grep -c "synthesizeScreenTilemap" \
      gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertZoneTilesetsTask.kt
0
```

**Locked: zero references.** Plan 12.2-04 deleted `synthesizeScreenTilemap` and the
codepath that called it.

## Cross-Phase Impact (SPEC AC10)

Verified empirically: **Pong, Breakout, Banks, simple-physics** all built cleanly. None
of them call `tilemap()` in their DSL — all four fall through to the D-01 Path A
one-invocation form (or skip `convertZoneTilesets` entirely when they have no zones).

| Game | DSL has `tilemap()`? | D-01 Path | Behavior |
|---|---|---|---|
| pong | no | n/a (no zone tilesets) | `ConvertZoneTilesetsTask: No zone tilesets -- skipping` |
| breakout | no | n/a (no zone tilesets) | `ConvertZoneTilesetsTask: No zone tilesets -- skipping` |
| banks | no | Path A (one-invocation) | Reuses 1st png2asset call's `_tileset_map[]` verbatim. 4 bytes (was 1024 pre-12.2 via synthesizer). For checker.png the visible result is equivalent because the pattern was already tiled, but the new bytes are produced for the right reason rather than by coincidence. |
| simple-physics | no | n/a (no zone tilesets) | `ConvertZoneTilesetsTask: No zone tilesets -- skipping` |
| platformer-template | **yes (3 zones)** | Path B (two-invocation) for world*-area* + Path A for title/next-level | 3 × 1920 B real tilemap data; 2 × 180 B title-style. |

**SPEC Constraint upheld: games without `tilemap()` continue to build cleanly; the one
game with `tilemap()` now ships real PNG-derived tilemap bytes instead of a synthetic
ramp.**

## Per-Game Bank Sizes (.noi)

```
=== pong ===
DEF l__CODE_0 0x0
DEF l__CODE_1 0x355  ;  853 B
DEF l__CODE   0x780  ; 1920 B total

=== breakout ===
DEF l__CODE_0 0x0
DEF l__CODE_1 0x42E  ; 1070 B
DEF l__CODE   0x982  ; 2434 B total

=== banks ===
DEF l__CODE_0 0x0
DEF l__CODE_2 0x4    ;    4 B  (zone tilemap bank — D-01 Path A)
DEF l__CODE_1 0x58   ;   88 B
DEF l__CODE   0x2E8  ;  744 B total

=== simple-physics ===
DEF l__CODE_0 0x0
DEF l__CODE   0x436  ; 1078 B total

=== platformer-template ===
DEF l__CODE_0 0x0
DEF l__CODE_1 0x246  ;  582 B
DEF l__CODE_2 0x17E8 ; 6120 B  (zone tilemap bank — exactly 3×1920 + 2×180)
DEF l__CODE   0x320F ; 12815 B total
```

The platformer-template bank-2 size of `0x17E8` = 6120 bytes EXACTLY equals
`3 × 1920 + 2 × 180`, locking the contract: BankingAnalysisPass placed the 5 zone
tilemaps in bank 2, sizes were derived from real PNG content (REQ-2), and the
overflow guard from Plan 12.2-08 was not triggered (6120 B << 16384 B).

## Bank-Size Comparison vs Phase 12.1 baseline

Phase 12.1 did not produce a numeric bank-size baseline for `platformer-template`
because Plan 12.1-10's terminal smoke gate **failed** (`SDCC error 20: Undefined
identifier '__bank__zone_world1Area1Zone_tilemap'` — the very defect Phase 12.2 was
created to fix per `12.1-10-SUMMARY.md`). There is therefore no pre-12.2 ROM to compare
against for `platformer-template`.

For the **other 4 games**, Phase 12.1's regression sweep was reported GREEN but did not
record per-bank byte sizes in its summary, so a numeric delta cannot be computed
retroactively. The values captured here become the **new baseline** for any future
phase that touches `ConvertZoneTilesetsTask` / `BankingAnalysisPass` / `GBDKPipelineV2`.

| Game | 12.1 platform-template ROM | 12.2 ROM | Delta |
|---|---|---|---|
| platformer-template | (build failed — no ROM) | 65,536 B (3 banks) | First successful build (Phase 12.2 unblocks) |
| pong | not recorded | 32,768 B (2 banks, code 1920 B) | new baseline |
| breakout | not recorded | 32,768 B (2 banks, code 2434 B) | new baseline |
| banks | not recorded | 65,536 B (3 banks, code 744 B) | new baseline |
| simple-physics | not recorded | 32,768 B (1 bank, code 1078 B) | new baseline |

## Verdict

**GREEN.** 5/5 ROMs build cleanly after `:clean :buildRom`.
Phase 12.2's source changes are integration-safe.
REQ-2, REQ-3, REQ-4 contracts all hold at the generated-C level (not just at the JVM
test layer — per `feedback_rom_build_smoke_test_for_codegen_phases`, this is the
staleness-resistant verification).
Ready for Plan 10 UAT re-shoot.
