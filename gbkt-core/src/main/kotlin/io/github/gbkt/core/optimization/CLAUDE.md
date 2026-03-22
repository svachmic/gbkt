# Optimization Module

Asset analysis and reporting for Game Boy ROM efficiency.

## Files

| File | Purpose |
|------|---------|
| `AnalyzedAsset.kt` | Per-asset analysis results: `AnalyzedAsset` (dimensions, tile/palette/compression analysis, `OptimizationScore` with letter grade), plus detail types `Dimensions`, `TileAnalysis`, `DuplicateTileInfo`, `TileLocation`, `LowEntropyTile`, `PaletteAnalysis`, `CompressionAnalysis`, `RLEOpportunity`, `SimilarTilePair` |
| `AssetReport.kt` | Aggregate report: `AssetReport` (assets list, summary, suggestions, timing), `AssetSummary` (totals, dedup ratio, efficiency percentage), `ByteSavings` (formatted display, addition) |
| `Suggestion.kt` | Actionable recommendations: `Suggestion` class with `severity` (ERROR/WARNING/INFO), `title`, `description`, `savings`, `action`. Also `CrossAssetDuplicate` for shared-tile detection |
| `ConsoleReporter.kt` | Terminal output: `ConsoleReporter` with `report()`/`reportToString()`, Unicode box-drawing, ANSI color support, `ReporterConfig` for verbosity control |

## Data Flow

```
Game assets -> AnalyzedAsset (per file) -> AssetReport (aggregate) -> ConsoleReporter (display)
                                        -> Suggestion list
```

## Related

- `AssetPipeline.kt` (parent package) -- loads raw asset data
- `PngValidator.kt` (parent package) -- validates PNG format
- `assets/AssetRef.kt` -- type-safe asset references analyzed here
