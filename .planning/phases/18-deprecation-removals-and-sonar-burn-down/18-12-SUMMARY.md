---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 12
subsystem: documentation/gradle-plugin/examples
tags: [seed-028, stale-guidance, configbuilder, text-only]
dependency_graph:
  requires: ["18-01"]
  provides: ["DEPR-03 guidance-string corrections"]
  affects: ["gbkt-gradle-plugin", "gbkt-examples/platformer-template", "gbkt-examples/metasprites"]
tech_stack:
  added: []
  patterns: ["text-only comment/KDoc correction"]
key_files:
  modified:
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/GbktExtension.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CompileRomTask.kt
    - gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt
    - gbkt-examples/metasprites/src/test/kotlin/io/github/gbkt/examples/metasprites/MetaspriteEmissionTest.kt
decisions:
  - "Hard-removal accepted (WR-04): no @Deprecated shim re-added; comment text corrected in-place"
  - "CartridgeConfig(romBanks = …, ramBanks = …) named-arg IR constructor sites left untouched per D-08 boundary"
metrics:
  duration: "5 min"
  completed: "2026-06-13"
  tasks: 1
  files: 4
---

# Phase 18 Plan 12: Stale ConfigBuilder Guidance Strings Summary

## One-liner

Fixed 4 stale `ramBanks = N` / `romBanks = N` property-assignment guidance strings to the current function-setter form `ramBanks(N)` / `romBanks(N)` (SEED-028 / WR-04/05 carry-in from Phase 17 review).

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Fix 4 stale ConfigBuilder guidance strings | adc27659 | 4 files |

## Verification

- `./gradlew :gbkt-examples:platformer-template:compileKotlin :gbkt-examples:metasprites:test` — PASS
- `./gradlew pluginTest` — PASS (112 tasks, 39 executed)
- `grep -rn 'ramBanks = \|romBanks = ' gbkt-gradle-plugin/src/main gbkt-examples/platformer-template/src/main gbkt-examples/metasprites/src/test` — no matches (exit code 1)

## Changes Made

| File | Line | Old | New |
|------|------|-----|-----|
| GbktExtension.kt | 166 | `config { ramBanks = N }` | `config { ramBanks(N) }` |
| CompileRomTask.kt | 319 | `config { ramBanks = N }` | `config { ramBanks(N) }` |
| PlatformerTemplate.kt | 61 | `` `romBanks = 8` `` | `` `romBanks(8)` `` |
| MetaspriteEmissionTest.kt | 44 | `` `romBanks = 2` `` | `` `romBanks(2)` `` |

## Boundary Respected

`CartridgeConfig(romBanks = …, ramBanks = …)` IR data-class constructor named-argument sites were NOT touched — these are valid Kotlin named arguments, not DSL setter calls.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — comment/KDoc text changes only; no new network endpoints, auth paths, file access patterns, or schema changes.

## Self-Check: PASSED

- adc27659 — confirmed in git log
- All 4 target files modified as expected
- No CartridgeConfig constructor sites altered
