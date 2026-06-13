---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 25
subsystem: gbkt-backend-gbdk/codegen
tags: [sonar-s3776, extract-method, emitting-refactor, byte-identity]
dependency_graph:
  requires: ["18-24"]
  provides: ["E-22-closed", "E-23-closed", "E-26-closed"]
  affects: ["GBDKCollectionCodegen.kt", "SharedConstantTablePass.kt", "DialogVisitor.kt"]
tech_stack:
  added: []
  patterns: ["extract-method (value-returning helpers)", "data class layout carrier"]
key_files:
  created: []
  modified:
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/GBDKCollectionCodegen.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/postprocess/SharedConstantTablePass.kt
    - gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/DialogVisitor.kt
decisions:
  - "E-22: two private extension helpers (buildAllDataStrings + buildAllFuncStrings) collecting List<String> joined with joinToString(\"\\n\\n\") — semantically identical to original isNotEmpty separator logic"
  - "E-23: two private methods (findMatchingCloseBrace + findSemicolonAfterBrace) extracted from the nested while/when/if block; same return values guarantee identical declarationEnd arithmetic"
  - "E-26: DialogLayout data class + computeDialogLayout removes the 9-inline-if cluster; buildDialogBorderStatements + buildCustomBorderStatements decompose the when; buildTypewriterCharBody removes the buildList lambda; @Suppress removed (detekt clean)"
metrics:
  duration: "5 min"
  completed_date: "2026-06-13"
  tasks_completed: 3
  files_modified: 3
---

# Phase 18 Plan 25: SONAR S3776 EMITTING Burn-Down (E-22, E-23, E-26) Summary

Three remaining EMITTING S3776 findings cleared via extract-method; each its own commit + 7-example byte-identity sweep.

## What Was Built

Three CC-reduction refactors in `gbkt-backend-gbdk/codegen/**` (all EMITTING paths requiring ROM sweep verification per D-06):

### Task 1 — E-22: GBDKCollectionCodegen.generateAllCollections (cc=20 → <5)

**Verification of cited symbol:** RESEARCH.md described this as "class body/init" but the actual high-cc symbol is the `generateAllCollections` extension function at line 436. CC=20 confirmed (4 for-loops × (1 for + 2 ifs) = 20). Correct symbol to fix.

**Extraction:** `buildAllDataStrings` and `buildAllFuncStrings` — two private extension functions on `GBDKCollectionCodegen` each collecting `List<String>` in collection-type order (hash tables → pools → ring buffers → fixed slots). `generateAllCollections` now composes them via `joinToString("\n\n")`, which is semantically identical to the original `isNotEmpty → append("\n\n")` separator logic. CC: 4 each (4 for-loops), 0 for assembler.

### Task 2 — E-23: SharedConstantTablePass.extractConstArrays (cc≈20 → <10)

**Extraction:** `findMatchingCloseBrace(cContent, openBracePos): Int` and `findSemicolonAfterBrace(cContent, closeBracePos): Int` — private methods that replace the nested `while/when/if` block and the follow-on whitespace-skip+if block respectively. Both return sentinel -1 on failure. `extractConstArrays` CC drops from ~20 to ~9 (outer while + 4 continue branches). `findMatchingCloseBrace` CC=6, `findSemicolonAfterBrace` CC=2. Byte-identity: `declarationEnd = semicolonPos + 1` is mathematically identical to original `declarationEnd = afterBrace + 1`.

### Task 3 — E-26: DialogVisitor.buildDialogFunction (cc≈19 → 3)

**Extraction — four helpers:**
- `DialogLayout` data class: carries all 10 computed layout fields (textStartX/Y, textWidth/Height, has* flags, printFn, textStartXBase/YBase)
- `computeDialogLayout(def): DialogLayout`: the 9 inline-if cluster (hasBorder, hasPortrait, hasSpeaker + derived values); CC=9 (flat ifs, no loops)
- `buildDialogBorderStatements(def): List<CStatement>`: `when` dispatch over `BorderStyle`; CC=1
- `buildCustomBorderStatements(def): List<CStatement>`: CUSTOM border null/size guard; CC=2 (if + &&)
- `buildTypewriterCharBody(def, textStartX, textStartY, textWidth): List<CStatement>`: per-char loop body; CC=1 (single if for textSpeed)

`buildDialogFunction` CC drops from ~19 to 3 (hasPortrait×2 + hasSpeaker). `@Suppress("LongMethod", "CyclomaticComplexMethod")` removed — detekt confirmed clean.

## Byte-Identity Sweep Results

All three tasks: **6/6 non-pong IDENTICAL; pong PASS*** (toolchain non-determinism; generated main.c identical across all 3 sweeps).

Baseline checksums (SHA-256):
- breakout.gb: `564465cd8b3b3920370d90c0d1ce4d5dda33656be79331ecd020bd35be41f33a`
- simple-physics.gb: `247e16d2df29c9cad2df3f2f5fb68cba00133e95e94b4d47dbcf87c31384f9ad`
- metasprites.gb: `9b2440db4592a7b76c04d2409bc789398609067e4c4cfb52aa964d52cb88d8d3`
- metasprites-stress.gb: `bc51eadd2afd7e4870ed9be98c0bf509708e1c2f1762278b295faa365a8c91de`
- banks.gb: `12c8ee2e7e8ead5c197519b2bb6a4f5f10a287778ea87f4e602421e5fb80b274`
- platformer-template.gb: `9a8f268a40cdd09d8321389c5251dc8298f90ac838f3a35cbf72dc0c8ec4a9a7`
- pong/generated/main.c: `b5e81de7c67ecacb99a276cfe50ce0313f2a11c2a83dde0adf09bed9479eada1` (identical through all 3 sweeps)

## Commits

| Task | Commit | Message |
|------|--------|---------|
| E-22 | `cc36203a` | refactor(18-25): extract-method GBDKCollectionCodegen.generateAllCollections (E-22) |
| E-23 | `d08b997b` | refactor(18-25): extract-method SharedConstantTablePass.extractConstArrays (E-23) |
| E-26 | `1e3834e2` | refactor(18-25): extract-method DialogVisitor.buildDialogFunction (E-26) |

## Deviations from Plan

### Verification Adjustments

**1. [Rule 1 - Verification] E-22 symbol description mismatch**
- **Found during:** Task 1 read_first inspection
- **Issue:** RESEARCH.md E-22 row describes the symbol as "GBDKCollectionCodegen (class body / init)" but the actual high-cc symbol is the `generateAllCollections` extension function at line 436 (not an init block)
- **Fix:** Verified CC=20 for `generateAllCollections` (confirmed by manual count: 4 for-loops × (1 for + 2 ifs at nesting 1) = 20); proceeded with the correct symbol
- **Outcome:** Not a phantom — the line number was correct, only the function description was misleading

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. These are pure internal codegen refactors. No threat flags.

## Self-Check: PASSED

- Commits `cc36203a`, `d08b997b`, `1e3834e2` found in git log
- All 3 modified source files exist on disk
- SUMMARY.md written to `.planning/phases/18-deprecation-removals-and-sonar-burn-down/18-25-SUMMARY.md`
