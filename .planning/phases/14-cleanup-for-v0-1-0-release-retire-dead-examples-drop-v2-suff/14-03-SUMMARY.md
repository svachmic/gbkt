---
phase: 14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff
plan: 03
subsystem: build
tags: [baseline, byte-identity, regression-gate, snapshot]

# Dependency graph
requires:
  - phase: 14
    plan: 02
    provides: post-retire HEAD — 7 KEEP examples, dead trees removed
provides:
  - evidence/baseline/baseline-<name>.sha256 for all 7 KEEP examples
  - confirmed GREEN sprite byte-identity tests (metasprites + metasprites-stress)
affects: [14-04 dead-code-sweep, 14-05 v2-rename, 14-06 textual-sweep, 14-08 final-regression]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "shasum -a 256 + find | sort | xargs for deterministic multi-file SHA-256 snapshot (macOS)"
    - "single chained :generateC invocation across all examples (no parallel clean — CLAUDE.md rule)"

key-files:
  created:
    - .planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/evidence/baseline/baseline-pong.sha256
    - .planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/evidence/baseline/baseline-breakout.sha256
    - .planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/evidence/baseline/baseline-simple-physics.sha256
    - .planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/evidence/baseline/baseline-metasprites.sha256
    - .planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/evidence/baseline/baseline-metasprites-stress.sha256
    - .planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/evidence/baseline/baseline-banks.sha256
    - .planning/phases/14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff/evidence/baseline/baseline-platformer-template.sha256
  modified: []

key-decisions:
  - "Ran all 7 generateC tasks in a single chained Gradle invocation (CLAUDE.md no-parallel-clean rule)"
  - "Used shasum -a 256 (macOS) rather than sha256sum — both produce the same SHA-256 format"
  - "Task 2: no re-pin required — baselines confirmed current; tests GREEN from cache (13.6-07 re-pin still valid)"

requirements-completed: [Req 5]

# Metrics
duration: 5min
completed: 2026-06-06
---

# Phase 14 / Plan 03: Pre-mutation generated-C SHA-256 baseline + sprite byte-identity gate

**7 pre-mutation generated-C baselines captured from the post-retire HEAD; committed sprite byte-identity tests confirmed GREEN without re-pinning.**

## Performance

- **Duration:** ~5 min
- **Completed:** 2026-06-06
- **Tasks:** 2
- **Files changed:** 7 created (all in evidence/baseline/)

## Accomplishments

- Created `evidence/baseline/` directory under phase 14 planning artifacts
- Ran `:generateC` for all 7 KEEP examples via single chained Gradle invocation (BUILD SUCCESSFUL in 842ms, 58 tasks UP-TO-DATE)
- Captured `baseline-<name>.sha256` per example using `find ... -name "*.c" | sort | xargs shasum -a 256`
- All 7 files: non-empty, each referencing `main.c`, zero BAD flags from acceptance check
- Ran `:gbkt-examples:metasprites:test :gbkt-examples:metasprites-stress:test` — BUILD SUCCESSFUL, both tests UP-TO-DATE (GREEN from cache)
- No baseline re-pin required — 13.6-07 re-pin (2026-06-05) remains valid

## Task Commits

1. **Task 1: Capture pre-mutation generated-C SHA-256 baseline for all 7 KEEP examples** — `75c3661e`
2. **Task 2: Sprite byte-identity tests confirmed GREEN** — no commit needed (no files changed)

## Baseline Files

| Example | File | Size | C files snapshotted |
|---------|------|------|---------------------|
| pong | `baseline-pong.sha256` | 635 B | main.c, bank1.c, sprites/ball.c, sprites/paddle.c |
| breakout | `baseline-breakout.sha256` | 651 B | main.c, bank1.c, sprites/*.c |
| simple-physics | `baseline-simple-physics.sha256` | 336 B | main.c, bank1.c |
| metasprites | `baseline-metasprites.sha256` | 334 B | main.c, sprites/elephant.c |
| metasprites-stress | `baseline-metasprites-stress.sha256` | 872 B | main.c, sprites/elephant.c, sprites/tiger.c, ... |
| banks | `baseline-banks.sha256` | 818 B | main.c, bank1.c, sprites/*.c |
| platformer-template | `baseline-platformer-template.sha256` | 3237 B | main.c, bank1.c, bank2.c, bank3.c, sprites/*.c, zone tilesets/tilemaps |

## Sprite Byte-Identity Test Results (Task 2)

| Test | Result | Notes |
|------|--------|-------|
| `MetaspritesGeneratedSpriteByteIdentityTest` | GREEN (UP-TO-DATE) | No re-pin — 13.6-07 baseline still valid |
| `MetaspritesStressGeneratedSpriteByteIdentityTest` | GREEN (UP-TO-DATE) | No re-pin — 13.6-07 baseline still valid |

**No re-pin performed.** Baselines were last re-pinned in Plan 13.6-07 (2026-06-05) when deterministic temp names were established. Output is byte-identical between rebuilds; tests pass from cache.

## Verification Results

| Check | Result |
|-------|--------|
| `ls evidence/baseline/*.sha256 \| wc -l` | 7 |
| BAD check (non-empty + contains main.c) | 0 BAD (all PASS) |
| `./gradlew :gbkt-examples:metasprites:test` | BUILD SUCCESSFUL |
| `./gradlew :gbkt-examples:metasprites-stress:test` | BUILD SUCCESSFUL |

## Deviations from Plan

None — plan executed exactly as written.

- Task 1 ran all 7 `generateC` tasks in a single chained invocation per CLAUDE.md no-parallel-clean rule.
- Task 2 tests were GREEN from cache without any re-pin (no baseline stale — D-06 confirmed).
- `shasum -a 256` used instead of `sha256sum` (macOS equivalent; same output format, same SHA-256 algorithm).

## Threat Surface Scan

No new trust boundaries. Read-only snapshot capture (SHA-256 hashing of existing generated C files) plus a test run confirmation. T-14-05 mitigated: baseline captured BEFORE any mutating track (dead-code sweep is plan 04; V2 rename is plan 05). T-14-06 mitigated: no re-pin was performed (tests GREEN from cache); no repudiation risk.

## Known Stubs

None.

## Self-Check: PASSED

- `75c3661e` exists: confirmed (`git log --oneline` shows it)
- All 7 baseline files exist: confirmed (`ls evidence/baseline/*.sha256 | wc -l` = 7)
- All 7 files non-empty + contain main.c: confirmed (BAD check = 0)
- `:gbkt-examples:metasprites:test` GREEN: confirmed (BUILD SUCCESSFUL)
- `:gbkt-examples:metasprites-stress:test` GREEN: confirmed (BUILD SUCCESSFUL)
- No re-pin performed: confirmed (clean git status after Task 2)

---
*Phase: 14-cleanup-for-v0-1-0-release-retire-dead-examples-drop-v2-suff*
*Completed: 2026-06-06*
