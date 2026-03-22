# gbkt-analysis — Static Analysis Passes

Provides an ordered pipeline of static analysis passes that validate, optimize, and plan resource allocation for a gbkt game IR before code generation.

## Dependencies
- **Depends on:** `gbkt-backend-api` (which depends on `gbkt-core`)
- **Used by:** `gbkt-backend-gbdk` (runs the pipeline before C codegen)

## Key Files
| File | Role |
|------|------|
| `AnalysisPass.kt` | `fun interface AnalysisPass { run(PassContext): PassResult }` — unit of analysis |
| `PassContext.kt` | Immutable snapshot threaded between passes (game IR, profile, bank/VRAM/OAM assignments, diagnostics) |
| `PassPipeline.kt` | Ordered executor: `beforePasses` -> `builtInPasses` -> `afterPasses`, fail-fast on `PassResult.Failed` |
| `DefaultPipeline.kt` | `object DefaultPipeline.create()` — wires the 11 built-in passes in correct order |
| `Diagnostic.kt` | `data class Diagnostic(id, severity, message, location?, suggestion?)` with `Severity` enum (ERROR/WARNING/INFO) |
| `OptimizationReport.kt` | Accumulated per-pass optimization summaries, serialized to `optimization-report.json` |
| `config/AnalysisConfig.kt` | Thresholds (bank fill, VRAM tiles, OAM slots, RAM), optimization toggles, `fromCartridgeConfig()` factory |
| `report/BudgetReporter.kt` | Formats the ASCII budget report (bank usage bars, VRAM table, scene breakdown) |

## Analysis Passes

Executed in this order by `DefaultPipeline`:

| # | Pass | Purpose |
|---|------|---------|
| 1 | `SemanticValidationPass` | Reference resolution, duplicate detection, IR structural integrity |
| 2 | `ResourceInventoryPass` | Counts all game resources (scenes, actors, tilesets, arrays, etc.) |
| 3 | `ConstraintCheckPass` | Validates hardware limits (sprite counts, tile counts, palette constraints) |
| 4 | `DeadCodeEliminationPass` | Removes unreachable scenes via BFS over scene navigation graph (configurable) |
| 5 | `ConstantFoldingPass` | Evaluates compile-time constant expressions in the IR (configurable) |
| 6 | `BitwiseOptimizationPass` | Rewrites power-of-2 multiply/divide to shift operations (configurable) |
| 7 | `BankingAnalysisPass` | First-fit-decreasing ROM bank allocation with transition graph analysis |
| 8 | `VRAMLayoutPass` | Per-scene VRAM tile range allocation (uses asset manifest when available) |
| 9 | `OAMAllocationPass` | Assigns OAM sprite slots to actors |
| 10 | `RAMPlanningPass` | Plans WRAM/HRAM/SRAM layout for variables and arrays |
| 11 | `BudgetAuditPass` | Generates budget report, writes optimization JSON, fails on accumulated errors |

Passes 4-6 are conditionally included based on `AnalysisConfig` toggle fields (all enabled by default).

## Extension Hooks

`PassPipeline` accepts `beforePasses` and `afterPasses` lists for user-injected custom passes:

```kotlin
DefaultPipeline.create(
    config = analysisConfig,
    beforePasses = listOf(myCustomLintPass),
    afterPasses = listOf(myMetricsPass),
)
```

## Testing
```bash
./gradlew :gbkt-analysis:test
```

## Common Tasks
- **Add a new pass:** Implement `AnalysisPass`, register it in `DefaultPipeline.create()` at the correct position
- **Add a new diagnostic:** Create a `Diagnostic(id = "ANLZ-XX", severity, message)` in your pass, return via `ctx.withDiagnostics()`
- **Adjust thresholds:** Modify defaults in `AnalysisConfig` or override per-game via `fromCartridgeConfig()`
- **Skip an optimization pass:** Set `deadCodeEliminationEnabled`, `constantFoldingEnabled`, or `bitwiseOptimizationEnabled` to `false` in config
