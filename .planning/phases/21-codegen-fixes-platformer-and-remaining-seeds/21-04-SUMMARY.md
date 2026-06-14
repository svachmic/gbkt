---
phase: 21-codegen-fixes-platformer-and-remaining-seeds
plan: "04"
subsystem: planning
tags: [seed-closure, disposition, verified-already-fixed, archive]
dependency_graph:
  requires: []
  provides: [SEED-027-archived, SEED-028-archived]
  affects: [.planning/seeds/archive/]
tech_stack:
  added: []
  patterns: [seed-archive-pattern]
key_files:
  created: []
  modified:
    - .planning/seeds/archive/SEED-027-gbc-screen-bitsperpixel-correctness.md
    - .planning/seeds/archive/SEED-028-configbuilder-removal-migration-guidance.md
decisions:
  - "SEED-027 and SEED-028 closed as VERIFIED-ALREADY-FIXED (Phase 18 plans 18-05 and 18-12); no re-implementation"
metrics:
  duration_min: 2
  completed_date: "2026-06-14"
requirements: [FIX-06]
---

# Phase 21 Plan 04: Archive SEED-027 + SEED-028 (VERIFIED-ALREADY-FIXED) Summary

**One-liner:** Both D-10/D-11 seeds confirmed already-fixed by Phase 18 via structural grep; annotated VERIFIED-ALREADY-FIXED and moved to seeds/archive/.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Verify SEED-027 + SEED-028 already-fixed, archive both seeds | 349dc07e | seeds/archive/SEED-027-*.md, seeds/archive/SEED-028-*.md |

## What Was Done

### Task 1: Verify SEED-027 + SEED-028 already-fixed via structural grep, then archive both seeds

**Verification evidence for SEED-027 (GBC bitsPerPixel):**

```
grep -n "bitsPerPixel" gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/TargetProfiles.kt
15: * numeric literals. Other fields (e.g. `bitsPerPixel`) are documentation constants until
34:            bitsPerPixel = 2,
53:            bitsPerPixel = 2,
```

- `bitsPerPixel = 4` is **absent** — the fix is in place.
- Both GBM and GBC TargetProfiles show `bitsPerPixel = 2` (lines 34 and 53).
- Fixed by Phase 18 plan 18-05.

**Verification evidence for SEED-028 (ConfigBuilder migration):**

```
grep -rn "config { ramBanks = " --include=*.kt --include=*.md . | grep -v /build/ | grep -v /.planning/
```

Returns: only `CHANGELOG.md:24` and `CONTRIBUTING.md:463` entries — these are **documentation of the migration** (correct usage showing old→new), not stale guidance strings instructing old syntax. No `.kt` file contains stale `config { ramBanks = N }` guidance. The 4 comment/KDoc sites listed in the seed scope sketch (GbktExtension.kt:166, CompileRomTask.kt:319, PlatformerTemplate.kt:61, MetaspriteEmissionTest.kt:44) were all corrected by Phase 18 plan 18-12.

**Actions taken:**
- Prepended `> VERIFIED-ALREADY-FIXED (Phase 21): ...` note to each seed file.
- Updated `**Status:**` lines from "Open" to "VERIFIED-ALREADY-FIXED — closed by Phase 18 plan 18-XX; archived Phase 21."
- Moved both files from `.planning/seeds/` to `.planning/seeds/archive/` using `git mv`.

**Zero production code modified** — git diff shows only seed file renames.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — disposition-only plan (planning artifact moves + grep verification).

## Self-Check: PASSED

- `.planning/seeds/SEED-027-gbc-screen-bitsperpixel-correctness.md` — absent from seeds/ (moved): OK
- `.planning/seeds/SEED-028-configbuilder-removal-migration-guidance.md` — absent from seeds/ (moved): OK
- `.planning/seeds/archive/SEED-027-gbc-screen-bitsperpixel-correctness.md` — present: OK
- `.planning/seeds/archive/SEED-028-configbuilder-removal-migration-guidance.md` — present: OK
- `bitsPerPixel = 2` in TargetProfiles.kt — confirmed: OK
- Commit 349dc07e — confirmed in git log: OK
