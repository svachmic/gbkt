---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: "02"
subsystem: documentation
tags: [depr-01, whenever, runIf, dsl-reference, doc-migration]
dependency_graph:
  requires: []
  provides: [context/DSL_REFERENCE.md updated to runIf]
  affects: [DSL documentation consistency]
tech_stack:
  added: []
  patterns: [doc-migration, global-rename]
key_files:
  created: []
  modified:
    - context/DSL_REFERENCE.md
decisions:
  - "Replaced all 83 whenever occurrences with runIf (code examples, prose, comments)"
  - "Fixed English prose 'whenever the target is defined' -> 'when the target is defined' to avoid nonsensical 'runIf the target is...' after auto-replace"
  - "Rewrote and renamed 'Single-Frame Conditionals' section to 'Conditional Logic' to remove contradictory prose and identical BEFORE/AFTER example that resulted from the rename"
metrics:
  duration: "4 min"
  completed: "2026-06-13"
  tasks_completed: 1
  files_modified: 1
---

# Phase 18 Plan 02: Whenever→RunIf DSL Reference Migration Summary

**One-liner:** Migrated all 83 `whenever` references in `context/DSL_REFERENCE.md` to `runIf`, plus fixed confusing before/after section prose.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Migrate whenever references in DSL_REFERENCE.md to runIf | ce2df6fc | context/DSL_REFERENCE.md |

## Verification

- `grep -ci 'whenever' context/DSL_REFERENCE.md` = **0** (acceptance criteria met)
- `grep -c 'runIf' context/DSL_REFERENCE.md` = **82** (non-zero, control-flow examples retained)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed English prose "runIf the target is defined" at line 749**
- **Found during:** Task 1 — global `sed` replace turned the English word "whenever" (meaning "at the time when") into "runIf" producing "runIf the target is defined above the current scene."
- **Fix:** Replaced with "when the target is defined above the current scene."
- **Files modified:** context/DSL_REFERENCE.md
- **Commit:** ce2df6fc

**2. [Rule 1 - Bug] Rewrote confusing "Single-Frame Conditionals" section**
- **Found during:** Task 1 post-rename review
- **Issue:** After global rename, the section contained: (a) contradictory prose saying `runIf` should NOT be used for reactive triggers then immediately saying it SHOULD be; (b) a BEFORE/AFTER example where both sides were now identical `runIf(...)` code; (c) "Nested `runIf` calls are the anti-pattern — use `runIf` instead" which was nonsensical self-reference.
- **Fix:** Renamed section to "Conditional Logic (runIf / unless / orElse)", rewrote prose to explain `runIf` is used for all conditional logic (reactive triggers and one-shot checks), removed the now-meaningless BEFORE/AFTER comparison and the "D-08 KEEP runIf" comment block. Kept the `unless` and `orElse` examples unchanged.
- **Files modified:** context/DSL_REFERENCE.md
- **Commit:** ce2df6fc

## Known Stubs

None — this is a documentation-only change with no code or data stubs.

## Threat Flags

None — documentation-only edit. No code, input, network, or data-handling surface introduced.

## Self-Check: PASSED

- context/DSL_REFERENCE.md: FOUND (modified)
- Commit ce2df6fc: FOUND in git log
- `grep -ci 'whenever' context/DSL_REFERENCE.md` = 0: VERIFIED
