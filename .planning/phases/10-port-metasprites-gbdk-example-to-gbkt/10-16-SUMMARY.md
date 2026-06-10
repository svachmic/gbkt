---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: 16
subsystem: evidence/oracle
tags: [oracle, rom-size, c-diff, d-11, metasprites]
dependency-graph:
  requires: [10-15, 10-12]
  provides: [evidence/rom-size-comparison.md, evidence/c-diff.md, evidence/oracle-comparison.md]
  affects: [10-17, 10-18]
tech-stack:
  added: []
  patterns: [three-signal oracle methodology (Phase 9 inheritance)]
key-files:
  created:
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/rom-size-comparison.md
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/c-diff.md
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/oracle-comparison.md
  modified: []
decisions:
  - "GBC_COMPATIBLE target eliminates 70-line tile-flip infrastructure from reference C — primary DSL value-add for this port"
  - "GBC palette slot numbering codegen bug found and seeded as high-priority seed candidate for Plan 18"
  - "ROM size 1.110x reference — PASS; within 2x cap by comfortable margin"
metrics:
  duration: ~20 minutes
  completed: 2026-05-18T17:38:43Z
  tasks-completed: 3
  files-created: 3
---

# Phase 10 Plan 16: Oracle Comparison Artifacts Summary

## One-liner

Three-signal oracle artifact produced: ROM size 1.110× reference (PASS), C-diff with GBC tile-flip win + palette-slot bug seeded, UAT verdict pending Plans 17/18.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Capture ROM size comparison (signal 1) | 957a216d | evidence/rom-size-comparison.md |
| 2 | Capture generated-C diff (signal 2) | c50fc3c3 | evidence/c-diff.md |
| 3 | Write umbrella oracle-comparison.md | 1b88d414 | evidence/oracle-comparison.md |

## What Was Built

### Signal 1 — ROM size (rom-size-comparison.md)

Built the GBDK reference ROM locally from
`/Users/michalsvacha/gbdk/examples/cross-platform/metasprites` per
`evidence/reference/BUILD.md`, then measured both ROMs:

| Metric | Reference | gbkt port | Ratio |
|--------|-----------|-----------|-------|
| File size | 32768 bytes | 32768 bytes | 1.0× |
| l__CODE | 3496 bytes (0xDA8) | 3879 bytes (0xF27) | 1.110× |

**Verdict: PASS** — 2× cap is 6992 bytes; port is at 3879 bytes (1.110×).

The +383-byte delta (+10.9%) is explained by:
1. HOME-bank scaffolding (~130 lines of unconditionally-emitted framework helpers)
2. GBC_COMPATIBLE target overhead (`-Wm-yc` adds small stubs)
3. Metasprite tables inlined in main.c vs compiled in a separate TU

### Signal 2 — Generated-C diff (c-diff.md)

Compared reference `metasprites.c` (309 lines) vs gbkt `main.c` (328 lines):

**Primary DSL value-add:** The entire 70-line tile-flip infrastructure
(`reverse_bits[256]` LUT, `set_tile()`, `get_tile_offset()`,
`load_and_duplicate_sprite_tile_data()`) is absent from the gbkt port. GBC
hardware flip bits make software VRAM duplication unnecessary — the
`moveMetasprite()` DSL call emits `move_metasprite_flipx/flipy/flipxy`
directly.

**Codegen bug found:** `play_enter()` calls all four `set_sprite_palette()`
with slot argument `0u`. The reference uses slots 0..3 (`OAMF_CGB_PAL0..3`).
Only slot 0 (green) is populated; sub-palette cycling (`rot >> 2`) will show
wrong colors. This is a functional correctness gap in `SpritePaletteVisitor`
(or equivalent codegen path), seeded as high-priority candidate for Plan 18.

**Longer regions (not defects):** HOME-bank scaffolding, decel ladder (4 lines
vs reference's 2-line if/else form), scene dispatcher. Same structural
explanation as Phase 9.

### Signal 3 — UAT verdict (oracle-comparison.md)

The umbrella ties the three signals together. Signal 3 row is marked PENDING;
Plans 17 (DMG behaviors) and 18 (GBC sub-palette) will fill it.

Three surplus seed candidates aggregated:
1. **HIGH:** GBC palette slot numbering bug (will cause D-09.3 UAT failure)
2. **LOW:** Hardcoded frame-count comparison (`_idx >= 5u`)
3. **LOW:** Unreferenced HOME scaffolding (same as Phase 9's DEFERRED-09-01)

## Deviations from Plan

### Auto-fixes

None.

### Pre-requisite work (reference ROM build)

The reference ROM did not exist at plan start (binaries are gitignored per
BUILD.md). Built the GBDK reference locally:
```
cd /Users/michalsvacha/gbdk/examples/cross-platform/metasprites
GBDK_HOME=/Users/michalsvacha/gbdk make gb
```
Then measured from `build/gb/metasprites.noi` and `build/gb/metasprites.gb`.
This was the expected flow described in BUILD.md — not a deviation.

### No bank1.c

The plan's Task 2 `read_first` mentions `bank1.c` ("if present"). The metasprites
port has no bank1.c — all code fits in HOME for this single-scene ROM_ONLY game.
This is consistent with the build log ("3 C files: main.c, game.h, game_metadata.json").
Documented in c-diff.md §"gbkt-generated C summary".

## Known Stubs

None — all three artifacts contain real measured data, not placeholders.

## Threat Flags

None — this plan creates documentation artifacts only; no network endpoints,
auth paths, file access patterns, or schema changes.

## Self-Check

- [x] `evidence/rom-size-comparison.md` exists with l__CODE measurements
- [x] `evidence/c-diff.md` exists with metasprites.c reference and Verdict section
- [x] `evidence/oracle-comparison.md` exists with ROM size + UAT verdict rows
- [x] No .asm diff or bank/section size capture (out-of-scope per D-11)
- [x] UAT verdict row marked PENDING with reference to Plans 17/18
- [x] STATE.md not modified
- [x] ROADMAP.md not modified
- [x] Commits: 957a216d, c50fc3c3, 1b88d414
