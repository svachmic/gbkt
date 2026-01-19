# Optimization Module

Asset analysis and optimization for Game Boy ROM efficiency.

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `AssetAnalyzer.kt` | Core analysis engine | ~548 |
| `AssetOptimizer.kt` | Applies optimizations to assets | ~200 |
| `AssetReport.kt` | Report data structures | ~150 |
| `AnalyzedAsset.kt` | Per-asset analysis results | ~100 |
| `Suggestion.kt` | Actionable optimization suggestions | ~150 |
| `ConsoleReporter.kt` | Beautiful console output | ~390 |

## Asset Analyzer (AssetAnalyzer.kt)

Comprehensive analysis of Game Boy assets:

### Usage

```kotlin
val analyzer = AssetAnalyzer()
val report = analyzer.analyze(game, assetDir)

// Print results
val reporter = ConsoleReporter()
reporter.report(report)
```

### What It Detects

| Issue | Description | Savings |
|-------|-------------|---------|
| Duplicate tiles | Same tile data multiple times | 16 bytes/tile |
| Empty tiles | All-transparent tiles | 16 bytes/tile |
| Low-entropy tiles | Mostly single color | Compression opportunity |
| Cross-asset duplicates | Same tile in different assets | Shared tileset opportunity |
| Unused palette slots | < 4 colors used | Optimization opportunity |

### Configuration

```kotlin
val config = AnalyzerConfig(
    lowEntropyThreshold = 0.5f,    // Shannon entropy threshold
    similarityThreshold = 0.8f,    // For similar tile detection
    maxTilesForSimilarity = 256,   // O(n²) limit
    detectDuplicates = true,
    detectEmpty = true,
    detectLowEntropy = true,
    analyzePalette = true,
    analyzeCompression = true,
)
val analyzer = AssetAnalyzer(config)
```

### Tile Analysis

```kotlin
data class TileAnalysis(
    val total: Int,
    val unique: Int,
    val duplicates: List<DuplicateTileInfo>,
    val empty: List<TileLocation>,
    val lowEntropy: List<LowEntropyTile>,
)
```

### Entropy Calculation

Shannon entropy measures tile complexity:
- 0.0 = single color (low complexity)
- 2.0 = equal distribution of 4 colors (high complexity)

```kotlin
// Decode 2bpp pixel data and compute entropy
private fun computeEntropy(tile: Tile): Float {
    val colorCounts = IntArray(4)
    // ... count colors
    // H = -Σ p(x) * log2(p(x))
}
```

## Console Reporter (ConsoleReporter.kt)

Beautiful terminal output with:
- Unicode box-drawing (with ASCII fallback)
- ANSI color support (auto-detected)
- Progressive detail levels
- Summary-first layout

### Example Output

```
═══════════════════════════════
║ Asset Optimization Report ║
═══════════════════════════════

--- Summary ---
  Assets:     12
  Tiles:      856 total, 623 unique
  Efficiency: 73%

--- Issues Found ---
  🔄 Duplicates: 45 tiles
  ⬜ Empty: 12 tiles
  💰 Potential savings: 912 bytes (57 tiles)

--- Suggestions ---
  1. [!] Share common tiles
      12 tiles duplicated across player.png and enemy.png
      -> Create shared tileset
      Saves: 192 bytes
```

### Reporter Configuration

```kotlin
val config = ReporterConfig(
    useColor = true,          // ANSI colors
    useUnicode = true,        // Unicode icons
    showPerAsset = true,      // Detailed breakdown
    showSuggestions = true,   // Actionable items
    quietWhenOptimal = false, // Show even when clean
)

// Presets
ReporterConfig.MINIMAL  // CI-friendly, quiet
ReporterConfig.VERBOSE  // Full details
```

## Suggestion Types (Suggestion.kt)

Actionable optimization recommendations:

```kotlin
sealed class Suggestion {
    data class DeduplicateTiles(...)      // Remove duplicate tiles
    data class RemoveEmptyTiles(...)      // Remove transparent tiles
    data class ShareTilesBetweenAssets(...) // Create shared tileset
    data class ConsolidateLowEntropy(...)  // Simplify complex tiles
    data class OptimizePalette(...)       // Use fewer colors
    data class MergeSimilarTiles(...)     // Delta compression candidates
    data class EnableCompression(...)     // RLE opportunity
}
```

### Severity Levels

```kotlin
enum class Severity {
    ERROR,    // Must fix (exceeds ROM limits)
    WARNING,  // Should fix (wastes space)
    INFO,     // Could optimize (minor improvement)
}
```

## Asset Report (AssetReport.kt)

Complete analysis results:

```kotlin
data class AssetReport(
    val assets: List<AnalyzedAsset>,
    val summary: AssetSummary,
    val suggestions: List<Suggestion>,
    val analysisTimeMs: Long,
) {
    val hasIssues: Boolean
}

data class AssetSummary(
    val totalAssets: Int,
    val totalTiles: Int,
    val uniqueTiles: Int,
    val duplicateTiles: Int,
    val emptyTiles: Int,
    val lowEntropyTiles: Int,
    val usedPaletteColors: Int,
    val potentialSavings: ByteSavings,
) {
    val efficiency: Int  // Percentage (0-100)
}
```

## Integration

### Gradle Plugin

```kotlin
// build.gradle.kts
gbkt {
    optimization {
        enabled = true
        failOnIssues = false  // Warnings only
        minEfficiency = 70    // Fail if below 70%
    }
}
```

### CLI Tool

```bash
gbkt analyze --assets ./assets --verbose
```

### Programmatic

```kotlin
val game = game("MyGame") { ... }
val analyzer = AssetAnalyzer()
val report = analyzer.analyze(game, File("assets"))

if (report.summary.efficiency < 70) {
    throw BuildException("Asset efficiency too low: ${report.summary.efficiency}%")
}
```

## Related Modules

- `AssetPipeline.kt` - Loads and processes assets
- `PngValidator.kt` - Validates PNG format for Game Boy
- `TiledParser.kt` - Parses Tiled map files
- `assets/AssetRef.kt` - Type-safe asset references
