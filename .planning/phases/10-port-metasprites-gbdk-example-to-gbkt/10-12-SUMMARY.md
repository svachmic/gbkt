---
phase: 10-port-metasprites-gbdk-example-to-gbkt
plan: 12
subsystem: planning/evidence
tags: [documentation, gitignore, reference-rom, d-11]
dependency_graph:
  requires: []
  provides:
    - evidence/reference/BUILD.md with reproducible metasprites.gb build invocation
    - .gitignore entries for Phase 10 reference binaries
  affects:
    - Plan 16 (three-signal artifact) — BUILD.md provides ROM-size measurement commands
tech_stack:
  added: []
  patterns:
    - "Phase 9 evidence/reference/BUILD.md pattern mirrored for Phase 10"
    - "Phase-specific gitignore entries for binary hygiene"
key_files:
  created:
    - .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/reference/BUILD.md
  modified:
    - .gitignore
decisions:
  - "BUILD.md documents GBDK_HOME=/Users/michalsvacha/gbdk make gb invocation (mirrors Phase 9 pattern)"
  - "Phase-specific gitignore patterns added alongside global *.gb — explicit intent for evidence/ directory"
  - "metasprites.c committed as text evidence; binaries (.gb .map .noi .ihx .rel .sym .lst .asm) gitignored"
metrics:
  duration_minutes: 4
  completed_date: "2026-05-18"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 2
---

# Phase 10 Plan 12: Reference ROM Build Documentation Summary

**One-liner:** BUILD.md with `GBDK_HOME=/Users/michalsvacha/gbdk make gb` invocation + phase-specific `.gitignore` entries locking binary hygiene for D-11 reference half.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Write evidence/reference/BUILD.md | 3cd074b1 | `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/reference/BUILD.md` |
| 2 | Add .gitignore entries for reference binaries | 58a27b95 | `.gitignore` |

## What Was Built

### Task 1: BUILD.md

Created `evidence/reference/BUILD.md` documenting:
- **Purpose**: Reproducible reference build for D-11 ROM-size + C-diff comparison in Plan 16
- **Prerequisites**: GBDK-2020 at `/Users/michalsvacha/gbdk`
- **Build command**: `cd /Users/michalsvacha/gbdk/examples/cross-platform/metasprites && GBDK_HOME=/Users/michalsvacha/gbdk make gb`
- **Copy steps**: `cp` commands to bring `metasprites.gb`, `.map`, `.noi`, `metasprites.c` into evidence directory
- **Verification commands**: `wc -c` for file size, `grep '^DEF l__CODE'` on `.noi` for actual code size
- **Gitignore note**: binary artifacts NOT committed; `BUILD.md` + `metasprites.c` ARE committed
- **Compiler flags**: Documents `-Wl-yt0x1B -autobank -Wl-j -Wm-yoA -Wm-ya4 -Wb-ext=.rel -Wb-v` (gb target — plain DMG, not GBC compatible)

### Task 2: .gitignore entries

Appended 8 phase-specific patterns to `.gitignore`:
```
.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/reference/*.gb
.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/reference/*.map
.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/reference/*.noi
.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/reference/*.ihx
.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/reference/*.rel
.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/reference/*.sym
.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/reference/*.lst
.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/reference/*.asm
```

`BUILD.md` and `metasprites.c` are explicitly NOT ignored (text evidence that IS committed).

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — this is a documentation-only plan. No code stubs introduced.

## Threat Flags

None — documentation-only plan; no new network endpoints, auth paths, file access patterns, or schema changes.

## Self-Check: PASSED

- `evidence/reference/BUILD.md` exists: VERIFIED
- Contains `GBDK_HOME=/Users/michalsvacha/gbdk make gb`: VERIFIED (grep count=1)
- Contains `wc -c` measurement command: VERIFIED
- Contains `grep '^DEF l__CODE'` measurement command: VERIFIED
- `.gitignore` contains `*.gb` pattern for evidence/reference: VERIFIED (grep count=1)
- `BUILD.md` NOT in gitignore: VERIFIED
- `metasprites.c` NOT in gitignore: VERIFIED
- Commits `3cd074b1` and `58a27b95` present in git log: VERIFIED
