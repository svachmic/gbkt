---
phase: 17-docs-reconciliation-and-quality-cleanup
plan: "03"
subsystem: static-analysis
tags: [detekt, quality, config, evidence]
dependency_graph:
  requires: []
  provides: [detekt-4rules-enabled, detekt-violation-inventory]
  affects: [detekt.yml, plan-17-06-worklist]
tech_stack:
  added: []
  patterns: [targeted-detekt-exclusions, rationale-comment-style]
key_files:
  created:
    - .planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/QUAL-DETEKT.md
  modified:
    - detekt.yml
decisions:
  - "MagicNumber ignoreNumbers list: [0,1,2,3,4,8,16] covers idiomatic tile/bit values; expanded if dry-run forces it in 17-06"
  - "MagicNumber excludes codegen+test; ComplexCondition excludes codegen+ir; UnusedPrivateMember/Property excludes dsl — all rationale-commented"
  - "LongMethod/TooManyFunctions/LongParameterList/LargeClass complexity blocks left byte-unchanged for Phase 18 (D-01)"
  - "No detekt-baseline.xml created (D-04 preserved)"
metrics:
  duration: "7 min"
  completed: "2026-06-12"
  tasks_completed: 2
  files_modified: 2
---

# Phase 17 Plan 03: Re-enable 4 Detekt Rules + Violation Inventory Summary

Re-enabled `MagicNumber`, `UnusedPrivateMember`, `UnusedPrivateProperty`, and `ComplexCondition` in `detekt.yml` with targeted, rationale-commented exclusions (D-02 form). Captured the full violation output as a worklist for Plan 17-06.

## What Was Done

### Task 1: Re-enable the 4 rules with targeted exclusions (commit `78786304`)

Modified `detekt.yml`:

- **MagicNumber**: `active: false` → `active: true`; added `ignoreNumbers: [0,1,2,3,4,8,16]`; added `excludes: ['**/codegen/**', '**/test/**']` each with inline rationale comment matching the existing LongMethod-block style.
- **ComplexCondition**: `active: false` → `active: true`; added `excludes: ['**/codegen/**', '**/ir/**']` with rationale comments.
- **UnusedPrivateMember**: `active: false` → `active: true`; added `excludes: ['**/dsl/**']` with rationale comment (receiver-lambda false positives).
- **UnusedPrivateProperty**: `active: false` → `active: true`; added `excludes: ['**/dsl/**']` with rationale comment (forward-declared API surface).

The `LongMethod / TooManyFunctions / LongParameterList / LargeClass` blocks are byte-unchanged.

### Task 2: Run detekt and capture violation inventory (commit `3558f957`)

Ran `./gradlew detekt --continue`. Build FAILED as expected with 2064 weighted violations.

**Violation totals:**

| Rule | Count |
|------|-------|
| MagicNumber | 2025 |
| UnusedPrivateProperty | 26 |
| ComplexCondition | 10 |
| UnusedPrivateMember | 3 |
| **Total** | **2064** |

**Top contributors:**
- MagicNumber: `gbkt-intellij-plugin` (1291), `gbkt-lang` (198), `gbkt-examples` (178), `gbkt-core` (110)
- UnusedPrivateProperty: 6 in main code (real dead code in 3 visitors + 3 IntelliJ panels), 20 in tests (leftover properties from refactors)
- ComplexCondition: 9 in IntelliJ plugin UI code (TilemapModel bounds checks), 1 in PngValidator
- UnusedPrivateMember: 1 main (GbktLanguage.readResolve singleton hook), 2 test (unused helpers)

The full per-rule/per-module table with fix-strategy column is at `.planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/QUAL-DETEKT.md`.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | `78786304` | `chore(17-03): re-enable MagicNumber/UnusedPrivateMember/UnusedPrivateProperty/ComplexCondition with targeted excludes` |
| Task 2 | `3558f957` | `docs(17-03): capture detekt violation inventory after re-enabling 4 rules` |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. The evidence file is complete; detekt.yml changes are complete.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes. This plan edits a static-analysis config and writes a markdown evidence file.

**T-17-03 mitigated:** The principled complexity excludes (LongMethod / TooManyFunctions / LongParameterList / LargeClass) are byte-unchanged. No `detekt-baseline.xml` was created. Both acceptance criteria assertions in the verification step pass.

## Self-Check: PASSED

| Check | Result |
|-------|--------|
| `QUAL-DETEKT.md` exists | FOUND |
| `17-03-SUMMARY.md` exists | FOUND |
| Commit `78786304` exists | FOUND |
| Commit `3558f957` exists | FOUND |
| 4 rules show `active: true` | 4/4 |
| No `detekt-baseline.xml` exists | PASS |
