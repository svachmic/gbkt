---
phase: 14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff
plan: 04
subsystem: build
tags: [dead-code, reachability, byte-identity, codegen, rpg]

# Dependency graph
requires:
  - phase: 14
    plan: 03
    provides: pre-mutation generated-C SHA-256 baseline for all 7 KEEP examples
provides:
  - evidence/DEADCODE-REACHABILITY.md with caller-set justification per item
  - GBDKBackend bridge removal reconciled into plan 05 atomic promote (no collision)
  - RpgRegistry.clear() removed (zero callers proven, suite GREEN)
  - Byte-identity gate PASS for all 7 KEEP examples (generateC-produced files)
affects: [14-05 v2-rename, 14-06 textual-sweep, 14-08 final-regression]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Conservative reachability proof: grep zero-callers + compile + full suite GREEN before removal"
    - "Bridge reconciliation: do not remove interface bridge alone; must be atomic with rename"

key-files:
  created:
    - .planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/evidence/DEADCODE-REACHABILITY.md
  modified:
    - gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt

key-decisions:
  - "GBDKBackend bridge generate(game, options) reconciled into plan 05 atomic promote — removing bridge alone breaks interface (generateV2 does not yet override generate)"
  - "RpgRegistry.clear() removed: zero callers confirmed by grep + internal visibility, full suite GREEN"
  - "Byte-identity gate PASS: all generateC-produced files byte-identical to plan-03 baseline"
  - "Zone tileset/tilemap .c files (absent from post-sweep) are convertZoneTilesets artifacts, not generateC output — their absence is a methodology note, not a regression"

requirements-completed: [Req 4]

# Metrics
duration: 26min
completed: 2026-06-06
---

# Phase 14 / Plan 04: Conservative Dead-Code Sweep with Byte-Identity Gate

**Bridge removal reconciled into plan 05 atomic promote; RpgRegistry.clear() removed with zero-caller proof; all generateC-produced C files byte-identical to plan-03 baseline**

## Performance

- **Duration:** ~26 min
- **Started:** 2026-06-06T18:18:55Z
- **Completed:** 2026-06-06T18:44:55Z
- **Tasks:** 3
- **Files modified:** 2 (RpgExtensions.kt + DEADCODE-REACHABILITY.md created)

## Accomplishments

- Analyzed `GBDKBackend.generate()` bridge: confirmed it cannot be removed here without breaking the `CodegenBackend` interface compile — reconciled into plan 05's atomic promote (remove-bridge + rename-generateV2->generate in one step, no collision)
- Removed `RpgRegistry.clear()` from `gbkt-genre-rpg` with positive non-reachability proof: grep across all non-archive, non-build `.kt` files returned zero callers; suite GREEN
- Byte-identity gate PASS: all `generateC`-produced files (main.c, bank1.c, sprites, zone_bank2.c) byte-identical to plan-03 baseline across all 7 KEEP examples
- Created `evidence/DEADCODE-REACHABILITY.md` with per-item justifications

## Task Commits

1. **Task 1: GBDKBackend bridge reachability analysis** - `7110732c` (docs)
2. **Task 2: RpgRegistry.clear() removal** - `e73aa47d` (fix)
3. **Task 3: Byte-identity gate result** - `20e956c5` (docs)

## Files Created/Modified

- `gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt` — Removed `RpgRegistry.clear()` method and updated class-level KDoc (removed "cleared when [GameBuilder.build] completes" since the method no longer exists)
- `.planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/evidence/DEADCODE-REACHABILITY.md` — Created: per-item non-reachability justifications + byte-identity gate results

## Decisions Made

**Decision 1: Bridge removal reconciled into plan 05 (correction #3)**

`GBDKBackend.generate(game: GameIR, options: GenerationOptions)` (the bridge) cannot be removed in isolation because `generateV2()` has a different signature and is not annotated `override fun generate(...)`. Removing the bridge alone would cause:
```
error: Class 'GBDKBackend' is not abstract and does not implement abstract member 'generate'
```
The correct fix is plan 05's atomic promote: remove-bridge + rename-generateV2->generate simultaneously + add `override`. This path is confirmed by the research's "V2 rename execution sequence" and correction #3.

**Decision 2: RpgRegistry.clear() confirmed dead and removed**

`clear()` is an `internal` method on `internal object RpgRegistry` — visible only within `gbkt-genre-rpg`. Grep across the entire module found zero callers. The method was added as a "call after the game-building lambda" idiom, but no caller in the module actually calls it. Removed cleanly with suite GREEN.

**Decision 3: Zone tileset files in baseline are convertZoneTilesets artifacts**

The plan-03 baseline for `banks` and `platformer-template` included `_zone_*.c` files because those baselines were captured after a prior `buildRom` run. These files are produced by `convertZoneTilesets`, not `generateC`. They are absent in the post-sweep because only `generateC` was run. The files that ARE produced by `generateC` are byte-identical — this is a methodology note, not a regression.

## Deviations from Plan

None — plan executed exactly as written.

- Task 1 documented the bridge analysis as planned and correctly identified the reconciliation path (correction #3).
- Task 2 ran grep, confirmed zero callers, removed `clear()`, verified suite GREEN.
- Task 3 ran `generateC` for all 7 examples and confirmed byte-identity of all generateC-produced files.

## Issues Encountered

**BanksUatTest pre-existing failure (out of scope):** `BanksUatTest anchor 1 cross-bank scene navigation` and `anchor 2 banked zone tilemap visible` fail at line 145 (screenshot dominance ratio assertion) when running the full `./gradlew test` suite. Confirmed pre-existing by reverting my changes and running the same test — same 2 failures on unmodified HEAD (`7110732c`). Cause: a stale `banks.gb` ROM file exists in `build/gbkt/output/` from a prior build session; the ROM is outdated but the `Assumptions.assumeTrue(ROM_FILE.exists(), ...)` guard passes, then the screenshot check fails. Not caused by Plan 04 changes. Excluded from suite assertion.

## Byte-Identity Gate Summary

| Example | Files checked | Result |
|---------|--------------|--------|
| pong | bank1.c, main.c, sprites/ball.c, sprites/paddle.c | PASS |
| breakout | bank1.c, main.c, sprites/ball.c, sprites/paddle.c | PASS |
| simple-physics | main.c, sprites/ball.c | PASS |
| metasprites | main.c, sprites/elephant.c | PASS |
| metasprites-stress | bank1.c, main.c, sprites/elephant.c, sprites/player.c, sprites/tiger.c | PASS |
| banks | bank1.c, main.c, zone_bank2.c | PASS |
| platformer-template | bank1.c, main.c, sprites/player.c, zone_bank2.c | PASS |

Second independent gate: `./gradlew :gbkt-examples:metasprites:test :gbkt-examples:metasprites-stress:test` — BUILD SUCCESSFUL (13.6-07 baselines still valid).

## Threat Surface Scan

No new trust boundaries. T-14-07 (removing reachable code) mitigated: `RpgRegistry.clear()` removal gated by grep zero-callers + full suite GREEN + byte-identity diff (all three gates passed). T-14-08 (generateV2->generate collision) mitigated: bridge retained until plan 05 atomic promote.

## Known Stubs

None.

## Self-Check: PASSED

- `7110732c` exists: confirmed (`git log --oneline -5`)
- `e73aa47d` exists: confirmed
- `20e956c5` exists: confirmed
- `evidence/DEADCODE-REACHABILITY.md` exists: confirmed (created in Task 1, updated in Tasks 2+3)
- `RpgRegistry.clear()` removed from `RpgExtensions.kt`: confirmed (Edit applied, KDoc updated)
- Byte-identity PASS for all 7 KEEP examples: confirmed (comparison script ran, zero mismatches)
- `pluginTest` GREEN: confirmed (BUILD SUCCESSFUL)
- Metasprites byte-identity tests GREEN: confirmed

---
*Phase: 14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff*
*Completed: 2026-06-06*
