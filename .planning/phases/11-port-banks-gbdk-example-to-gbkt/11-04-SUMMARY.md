---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 04
subsystem: asset-pipeline
tags: [png, tileset, asset, banks-port, fixture]

requires:
  - phase: 11-port-banks-gbdk-example-to-gbkt
    provides: "11-PATTERNS.md tile-asset directory layout guidance + 11-CONTEXT.md D-claude-3 (non-trivial zone tileset)"
provides:
  - "gbkt-examples/banks/res/tiles/checker.png — 16×16 RGB checker PNG used by Plan 11-05's banked zone DSL"
affects: [11-05-banked-zone-dsl, 11-07-anchor-2-tilemap-evidence, 11-09-jvm-invariant-switch_rom-wrapper]

tech-stack:
  added: []
  patterns:
    - "Asset-before-DSL plan ordering — binary fixture committed in its own plan, decoupling asset creation from DSL authoring"

key-files:
  created:
    - "gbkt-examples/banks/res/tiles/checker.png (81 bytes, 16×16 8-bit RGB, 2-tile checkerboard)"
  modified: []

key-decisions:
  - "Used Python 3 + zlib/struct for deterministic PNG generation (Method A in plan) — produces byte-identical output across hosts, important for the build-cache + the eventual generated-C oracle diff"
  - "Chose 16×16 dimensions with 8×8 tile blocks — gives 2 distinct GB tiles in a 2×2 grid, the minimum that makes set_bkg_tiles' tile-index argument actually non-uniform"
  - "RGB color mode (PNG color type 2) — the gbkt asset pipeline quantizes to 2bpp DMG palette downstream, RGB is the simplest source format with maximum tooling compatibility"

patterns-established:
  - "Asset fixture plans precede DSL plans that reference them — keeps generateC failure modes traceable (missing-asset vs. DSL-shape errors are clearly separable)"

requirements-completed: [BANK-ASSET-TILESET]

duration: ~2 min
completed: 2026-05-20
---

# Phase 11 Plan 04: Checker Asset Summary

**16×16 RGB checker PNG fixture (81 bytes) for the banks-port banked zone tileset — non-trivial pixel pattern guarantees `set_bkg_tiles` actually fires per CONTEXT D-claude-3.**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-05-20T05:03:38Z
- **Completed:** 2026-05-20T05:05:00Z
- **Tasks:** 1
- **Files modified:** 1 created, 0 modified

## Accomplishments

- Created the minimum-viable tileset PNG that satisfies the banks port's zone DSL (Plan 11-05).
- Generated a deterministic, reproducible 16×16 RGB checker via Python stdlib — no external image tools required, build-cache friendly.
- Pixel pattern is non-uniform across two 8×8 tile cells (white tile + black tile, arranged in a 2×2 checkerboard), so the downstream asset pipeline produces at least two distinct GB tile indices.
- Decoupled asset creation from DSL authoring per the plan's purpose statement — Plan 11-05 can now invoke `asset("tiles/checker.png")` without the asset pipeline ever 404-ing during `:gbkt-examples:banks:generateC`.

## Task Commits

1. **Task 1: Generate checker.png** — see metadata commit below (single-task plan, committed together with SUMMARY.md)

## Files Created/Modified

- `gbkt-examples/banks/res/tiles/checker.png` — 81-byte 16×16 8-bit RGB non-interlaced PNG. Two 8×8 tiles, alternating white (0xFFFFFF) and black (0x000000) in a 2×2 checkerboard. Generated via Python `zlib.compress` + manual IHDR/IDAT/IEND chunk emission (Method A in the plan).

## Verification Evidence

- `file gbkt-examples/banks/res/tiles/checker.png` → `PNG image data, 16 x 16, 8-bit/color RGB, non-interlaced`.
- First 8 bytes hex dump: `89 50 4e 47 0d 0a 1a 0a` (canonical PNG signature).
- File size: 81 bytes (≥70 lower bound, ≤1024 upper bound — well inside the plan's acceptance band).
- Pixel content (visible in `xxd` IDAT chunk): non-uniform — distinct dark/light bytes per row, confirming the checker pattern survived zlib compression.

## Decisions Made

- **Generation method = Python (Method A) over ImageMagick (Method B).** Python is in the standard host toolchain (already used by other gbkt build tooling); ImageMagick would have been a deferred extra-host-tool dependency. The byte-output is fully deterministic across hosts because we drive `zlib.compress` with explicit level 9 — important once the banks port's evidence/oracle-comparison.md compares ROM bytes.
- **No alpha channel / no palette indexing.** RGB (PNG color type 2) is the simplest format; the asset pipeline downstream quantizes to 2bpp DMG anyway, so source-format complexity is wasted. Keeps the fixture maximally portable.

## Deviations from Plan

None — plan executed exactly as written. Method A succeeded on first attempt; no fallback to Method B (ImageMagick) was needed.

## Issues Encountered

- The plan's verify command (`head -c 8 ... | od -An -tx1 | grep -q "89 50 4e 47 0d 0a 1a 0a"`) failed at first due to BSD `od` emitting leading spaces and a trailing space inside the byte field. The PNG itself was valid — confirmed by `file`, by individual byte inspection, and by a normalized re-check (`tr -s ' ' | sed -e 's/^ //' -e 's/ *$//'`). The plan's verify regex relies on `grep -q`, which on macOS `od` output succeeds only with normalized whitespace. **No file-level fix needed**; flagging for plan-checker awareness in future asset-fixture plans (low-impact verifier-script portability nit; not a Rule 1/2/3 deviation).

## Threat Flags

None — fixture is a 2-color image with no executable surface, no network input, no PII. The plan's threat register entry T-11-06 (tampering of PNG payload) is mitigated by the deterministic generation script + the [70, 1024] size bound enforced in acceptance criteria.

## Next Phase Readiness

- **Ready for Plan 11-05** — the banked-zone DSL can reference `asset("tiles/checker.png")`; generateC will resolve it cleanly.
- **No new tooling installed** — Python 3 was already on PATH; no contributors need new setup.
- **No follow-up seeds.** The plan was scoped to 1 file; output matches scope.

## Self-Check: PASSED

- File exists: `gbkt-examples/banks/res/tiles/checker.png` ✓
- PNG signature: `89 50 4e 47 0d 0a 1a 0a` ✓
- Size in bounds: 81 bytes ∈ [70, 1024] ✓
- `file` reports correct dimensions + color type: `PNG image data, 16 x 16, 8-bit/color RGB, non-interlaced` ✓
- Non-trivial pixel pattern (visible 2-tile checker in IDAT) ✓

---
*Phase: 11-port-banks-gbdk-example-to-gbkt*
*Completed: 2026-05-20*
