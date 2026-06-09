---
phase: "14"
plan: "06"
subsystem: "backend-gbdk, lang, core, examples"
tags: ["rename", "cleanup", "v2-suffix-removal", "textual-sweep", "behavior-neutral"]
dependency_graph:
  requires: ["14-05"]
  provides: ["14-07"]
  affects: ["gbkt-backend-gbdk", "gbkt-lang", "gbkt-core", "gbkt-examples"]
tech_stack:
  added: []
  patterns: ["git mv for file renames", "D-Seed005/DV3-iter2 label rewrites for acceptance-grep compliance"]
key_files:
  created:
    - "evidence/RENAME-BYTEIDENTITY.md (Plan 14-06 post-sweep section appended)"
  modified:
    - "gbkt-examples/pong/src/main/kotlin/.../Pong.kt (was PongV2.kt)"
    - "gbkt-examples/pong/build.gradle.kts (PongKt::pong)"
    - "gbkt-examples/breakout/src/main/kotlin/.../Breakout.kt (was BreakoutV2.kt)"
    - "gbkt-examples/breakout/build.gradle.kts (BreakoutKt::breakout)"
    - "gbkt-backend-gbdk/src/main/kotlin/.../pipeline/GBDKPipeline.kt (was GBDKPipelineV2.kt)"
    - "gbkt-backend-gbdk/src/main/kotlin/.../visitor/ScriptOpVisitor.kt (D-V2 comment)"
    - "gbkt-backend-gbdk/src/test/.../pipeline/GBDKPipelineMetadataSpritesTest.kt"
    - "gbkt-backend-gbdk/src/test/.../visitor/DV3VisualDiagnosticTest.kt (was DV3VisualV2DiagnosticTest.kt)"
    - "gbkt-backend-gbdk/src/test/.../visitor/DV3VisualV3DiagnosticTest.kt (DV3V2->DV3-iter2)"
    - "gbkt-backend-gbdk/src/test/.../pipeline/BgCheckerboardEmissionTest.kt (D-V2->D-Seed005)"
    - "gbkt-backend-gbdk/src/test/.../visitor/ScriptOpVisitorMoveMetaspriteTest.kt (D-V2->D-Seed005)"
    - "gbkt-core/src/main/kotlin/.../test/SimulationContext.kt (was SimulationContextV2.kt)"
    - "gbkt-core/src/test/.../test/SimulationContextTest.kt (was SimulationContextV2Test.kt)"
    - "gbkt-lang/src/main/kotlin/.../dsl/MetaspriteBuilder.kt (D-V2->D-Seed005 KDoc)"
    - "gbkt-lang/src/test/.../dsl/BgAspectDiagnosticTest.kt (was DV2BgAspectDiagnosticTest.kt)"
    - "gbkt-lang/src/test/.../dsl/Seed005CheckerboardBytePatternTest.kt (D-V2->D-Seed005)"
key_decisions:
  - "D-14-06-1: D-V2 historical decision labels -> D-Seed005 (preserves checkerboard/seed-005 meaning, no longer matches acceptance grep [A-Za-z_]*V2\\b)"
  - "D-14-06-2: DV3V2/Dv3V2 comment labels -> DV3-iter2 (preserves DV3 diagnostic iteration meaning)"
  - "D-14-06-3: facade class references in build.gradle.kts (PongV2Kt::pongV2 -> PongKt::pong, BreakoutV2Kt::breakoutV2 -> BreakoutKt::breakout) updated proactively per facade-reference WARNING"
requirements_completed: ["Req 3"]
duration: "~45 minutes"
completed: "2026-06-06T20:55:00Z"
---

# Phase 14 Plan 06: Textual-Sweep-Second (V2 Suffix Removal) Summary

Acceptance grep `grep -rE "[A-Za-z_]*V2\b" --include=*.kt` returns ZERO matches across all main src + examples + tests; generated C byte-identical to Plan 14-05 baselines.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 1 | git mv 8 *V2.kt files to unsuffixed names; rename 6 classes; update val pongV2->pong, breakoutV2->breakout, helper extractFunctionBodyForDv3V2->extractFunctionBodyForDv3; update build.gradle.kts facade refs | a42aaba0 |
| 2 | Rewrite D-V2->D-Seed005 (6 files, 11 occurrences); DV3V2/Dv3V2->DV3-iter2 (2 occurrences); compileKotlin+compileTestKotlin EXIT 0; acceptance grep == 0 | 4382ea63 |
| 3 | Full-suite test (pre-existing failures unchanged); byte-identity gate 14/14 PASS; acceptance grep == 0; evidence appended to RENAME-BYTEIDENTITY.md | 161b1843 |

## Acceptance Grep — Zero Confirmation

```bash
grep -rE "[A-Za-z_]*V2\b" --include=*.kt . \
  --exclude-dir=build --exclude-dir=.git --exclude-dir=.claude --exclude-dir=.planning
```
**Result: 0 matches** (verified after Task 2 commit, confirmed unchanged after Task 3)

## D-V2/DV3V2 Resolution (Correction #4)

The plan-decision labels `D-V2` (Plan 10.1 checkerboard/SEED-005 decision) and `DV3V2`/`Dv3V2`
(DV3 visual diagnostic iteration 2) matched the acceptance grep pattern `[A-Za-z_]*V2\b`. They
were systematically rewritten as follows:

| Old label | New label | Files affected |
|-----------|-----------|----------------|
| `D-V2` | `D-Seed005` | BgCheckerboardEmissionTest (4 occ), ScriptOpVisitorMoveMetaspriteTest (1), ScriptOpVisitor.kt main src (1), MetaspriteBuilder.kt KDoc (2), BgAspectDiagnosticTest (1), Seed005CheckerboardBytePatternTest (4) |
| `DV3V2` / `Dv3V2` | `DV3-iter2` | DV3VisualV3DiagnosticTest (2) |

Decision meaning is preserved: `D-Seed005` directly references the `SEED-005` seed that tracked the
checkerboard defect; `DV3-iter2` indicates the second iteration of the DV3 visual diagnostic test.

## File Renames (git mv)

| Old path | New path |
|----------|----------|
| `gbkt-examples/pong/.../PongV2.kt` | `Pong.kt` |
| `gbkt-examples/breakout/.../BreakoutV2.kt` | `Breakout.kt` |
| `gbkt-core/.../test/SimulationContextV2.kt` | `SimulationContext.kt` |
| `gbkt-backend-gbdk/.../pipeline/GBDKPipelineV2.kt` | `GBDKPipeline.kt` |
| `gbkt-backend-gbdk/.../pipeline/GBDKPipelineV2MetadataSpritesTest.kt` | `GBDKPipelineMetadataSpritesTest.kt` |
| `gbkt-core/.../test/SimulationContextV2Test.kt` | `SimulationContextTest.kt` |
| `gbkt-lang/.../dsl/DV2BgAspectDiagnosticTest.kt` | `BgAspectDiagnosticTest.kt` |
| `gbkt-backend-gbdk/.../visitor/DV3VisualV2DiagnosticTest.kt` | `DV3VisualDiagnosticTest.kt` |

## Byte-Identity Gate Results

14/14 generateC-produced files (`main.c`, `bank*.c`, `zone_bank*.c`) byte-identical to baselines.
No sprite files (`sprites/*.c`) or zone tileset files (`_zone_*.c`) were compared (per evidence plan).

## Pre-existing Failures (Not Regressions)

- `BanksUatTest`: 2 failures (stale ROM file, pre-existing since Plan 14-04)
- `PlatformerTemplate128UatTest.anchor4MetaspriteAnimation`: 1 failure (pre-existing)
- `PlayerMetaspriteGeometryTest`: 2 failures (pre-existing)
- `PongStepAgentTest.metadata and symbol table agree`: 1 failure (stale ROM .noi vs fresh metadata JSON — same stale-ROM pattern)
- `pluginTest`: 12 IntegrationTest failures (`SceneIR.copy$default` signature mismatch from stale mavenLocal — confirmed pre-existing at Plan 14-04 commit 660e8c7d)

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as described.

### Informational

The plan noted "Serena MCP tools are NOT available" in the tooling_note. All renames were
performed using `git mv` + `Read`/`Edit` with word-boundary-anchored textual replacement,
compile-gated as specified.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced.
This plan is purely a textual rename/relabel sweep.

## Self-Check: PASSED

- find -name "*V2.kt" (excl build/.git/.claude): empty ✓
- acceptance grep returns 0 ✓
- Task commits exist: a42aaba0, 4382ea63, 161b1843 ✓
- evidence/RENAME-BYTEIDENTITY.md updated ✓
- 14/14 generateC files byte-identical to baselines ✓
