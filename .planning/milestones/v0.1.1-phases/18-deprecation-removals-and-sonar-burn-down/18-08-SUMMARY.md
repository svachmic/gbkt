---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: "08"
subsystem: emulator/mcp
tags: [sonar, s3776, extract-method, non-emitting, refactor]
dependency_graph:
  requires: []
  provides: [N-09-closed, N-16-closed, N-01-closed]
  affects: [gbkt-emulator, gbkt-mcp-server]
tech_stack:
  added: []
  patterns: [extract-method, private-data-class, value-returning-helper]
key_files:
  modified:
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/StepAgent.kt
    - gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/VariableInspector.kt
    - gbkt-mcp-server/src/main/kotlin/io/github/gbkt/mcp/McpEmulatorSession.kt
decisions:
  - "N-01 batchAssert: EXTRACT-METHOD (not NOSONAR) — dispatch is reducible; 6 per-type helpers plus findTextInRows + buildCheckResultJson fully decompose the 119-line function"
  - "N-09 toSummary: extract nonEmptyRowsFormatted private helper for symmetric BG/WIN mapIndexedNotNull/any/if pattern"
  - "N-16 loadSymbols: extract parseSymbolAddress (try/when) and parseSymbolName (trimStart guard) private helpers; forEach body becomes linear early-return pattern"
metrics:
  duration: "3 min"
  completed: "2026-06-13"
  tasks: 3
  files: 3
---

# Phase 18 Plan 08: NON-EMITTING S3776 — emulator + mcp-server batch Summary

Extract-method across `gbkt-emulator` and `gbkt-mcp-server` for three S3776 findings (N-09, N-16, N-01). JVM-test-only evidence per D-06. All three findings closed via EXTRACT-METHOD (no NOSONAR used).

## Tasks Completed

| Task | Finding | File | CC Before | Action | Commit |
|------|---------|------|-----------|--------|--------|
| 1 | N-09 `toSummary` | StepAgent.kt | 22 | Extract `nonEmptyRowsFormatted` helper | 2f682af7 |
| 2 | N-16 `loadSymbols` | VariableInspector.kt | 16 | Extract `parseSymbolAddress` + `parseSymbolName` | 71c6d972 |
| 3 | N-01 `batchAssert` | McpEmulatorSession.kt | 74 | Extract 8 private helpers + `CheckResult` data class | 21f727ee |

## What Was Built

### Task 1: N-09 — `toSummary()` in StepAgent.kt

The SonarCloud report identified `toSummary()` at line 513 (cc=22). The complexity came from two symmetric BG/WIN text-row formatting blocks, each containing a `mapIndexedNotNull { i, row -> if (row.any { it != '.' && it != ' ' }) "[row $i]..." else null }` pattern — deeply nested lambdas with boolean operators.

Extracted: `private fun nonEmptyRowsFormatted(rows: List<String>): List<String>` as a file-level private helper (placed immediately after `toSummary`). The function owns the `mapIndexedNotNull/if/any/&&` complexity. `toSummary()` now delegates to it for both BG and WIN rows, reducing its own complexity below the cc=15 threshold.

### Task 2: N-16 — `loadSymbols()` in VariableInspector.kt

The complexity (cc=16) came from a nested `try { when { ... } } catch { }` address-parsing block inside a `forEach` with multiple guard conditions (3-operand boolean condition + name-empty check + WRAM range check).

Extracted:
- `private fun parseSymbolAddress(addrStr: String): Int?` — owns the try/when block with SDCC/GBDK format detection; returns null on failure
- `private fun parseSymbolName(rawName: String): String?` — strips leading underscores and returns null for bare-underscore lines

`loadSymbols()` becomes a linear `forEach` with four early-return guard calls, complexity well below threshold.

### Task 3: N-01 — `batchAssert()` in McpEmulatorSession.kt

The largest finding (cc=74): a 119-line `mutex.withLock { for { when { 6 branches... } } }` stack with deeply nested loops in the `text_on_screen` branch (two nested `for` loops with `if` guards at nesting depth 3-4) and a complex result-building block.

Added `private data class CheckResult(passed, actual, extras)`.

Extracted (all inside `McpEmulatorSession`):
- `dispatchCheck(a, obs, check): CheckResult` — thin `when` dispatch to per-type helpers
- `checkVariableEquals(obs, args): CheckResult`
- `checkVariableInRange(obs, args): CheckResult`
- `checkSceneIs(obs, args): CheckResult`
- `checkTextOnScreen(a, obs, args): CheckResult` — owns the scrollAware branching
- `findTextInRows(text, rows, layer): Triple<Int, Int, String>?` — owns the nested loop search
- `checkActorVisible(obs, args): CheckResult`
- `checkSpriteCount(obs, args): CheckResult`
- `buildCheckResultJson(check, result): JsonObject` — owns the extras `for/when` serialisation

`batchAssert()` is now a 12-line function: get observation, iterate checks via `dispatchCheck + buildCheckResultJson`, return aggregated JSON.

## N-01 Disposition: EXTRACT-METHOD

The plan required inspecting `batchAssert` before choosing EXTRACT-METHOD vs NOSONAR. The function is per-assertion-type dispatch with extractable logic per branch — the `text_on_screen` branch in particular has 26 lines of nested loops that are logically atomic but clearly reducible via `findTextInRows`. NOSONAR is NOT used. N-01 counts 0 against the ≤5 milestone NOSONAR budget.

## Test Evidence

```
./gradlew :gbkt-emulator:test    — 18 tests, BUILD SUCCESSFUL
./gradlew :gbkt-mcp-server:test  — BUILD SUCCESSFUL
./gradlew :gbkt-emulator:test :gbkt-mcp-server:test — BUILD SUCCESSFUL (combined)
```

No test changes required — all refactors are behavior-preserving.

## Deviations from Plan

None — plan executed exactly as written. EXTRACT-METHOD applied to all three findings. No NOSONAR suppressions used.

## Known Stubs

None.

## Threat Flags

None. No new network endpoints, auth paths, file access, or schema changes introduced. All changes are internal helper extractions with no external surface modifications.

## Self-Check: PASSED

Files exist:
- gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/StepAgent.kt — FOUND
- gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/VariableInspector.kt — FOUND
- gbkt-mcp-server/src/main/kotlin/io/github/gbkt/mcp/McpEmulatorSession.kt — FOUND

Commits:
- 2f682af7 — FOUND (N-09 Task 1)
- 71c6d972 — FOUND (N-16 Task 2)
- 21f727ee — FOUND (N-01 Task 3)
