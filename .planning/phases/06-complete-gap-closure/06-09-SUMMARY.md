---
phase: 06-complete-gap-closure
plan: 09
subsystem: ide-plugin
tags: [intellij-plugin, source-map, inspection, gutter-icon, asset-validation, line-marker]

# Dependency graph
requires:
  - phase: 06-02
    provides: "promoted v2 packages to top-level namespace; clean build foundation"
provides:
  - "C→DSL bidirectional scrolling in CCodePreviewPanel via caret listener on C editor"
  - "GbktCodegenService.findKotlinLocationForCLine() for reverse source map lookup"
  - "GbktAssetRefInspection validating asset() file existence with ERROR highlight"
  - "GbktCreateAssetPlaceholderQuickFix creating 1x1 transparent PNG at missing path"
  - "BudgetGutterIconProvider showing green/yellow/red icons next to scene/actor blocks"
affects: [ide-plugin, dx, source-map, inspections, gutter]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "LineMarkerProvider on KtNameReferenceExpression identifier leaf to avoid duplicate markers per call"
    - "Regex-based budget report parsing (no external JSON library dependency)"
    - "CaretListener on EditorEx for C→DSL reverse direction (mirrors DSL→C EditorFactory listener)"

key-files:
  created:
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/inspections/GbktAssetRefInspection.kt
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/inspections/GbktCreateAssetPlaceholderQuickFix.kt
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/gutter/BudgetGutterIconProvider.kt
  modified:
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/codegen/GbktCodegenService.kt
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/toolwindow/CCodePreviewPanel.kt
    - gbkt-intellij-plugin/src/main/resources/META-INF/plugin.xml

key-decisions:
  - "C→DSL listener attaches to editor.caretModel directly (not EditorFactory.eventMulticaster) — scoped to the C preview editor only, no project filtering needed"
  - "BudgetGutterIconProvider attaches marker to KtNameReferenceExpression identifier leaf — exactly one marker per call site; parent KtCallExpression would fire on child tokens too"
  - "Budget report format uses line-oriented text with [id] prefix — regex parsing avoids JSON dependency on IntelliJ plugin classpath"
  - "GbktCreateAssetPlaceholderQuickFix falls back to hardcoded 1x1 PNG bytes if ImageIO fails — guaranteed to produce valid PNG even in restricted environments"
  - "Asset search roots ordered: src/main/resources > src/main/assets > assets > resources — matches typical Kotlin/Gradle project layout"

patterns-established:
  - "IntelliJ plugin line markers: attach to identifier leaf of call expression for single-marker-per-call semantics"
  - "C→DSL reverse mapping: CaretListener on EditorEx resolveFileAndLine → findKotlinLocationForCLine → openSourceLocation"

requirements-completed: [IDE-01, IDE-02, IDE-03]

# Metrics
duration: 3min
completed: 2026-02-21
---

# Phase 06 Plan 09: IntelliJ Plugin DX Features Summary

**IntelliJ plugin DX features: bidirectional C↔DSL source map scrolling, asset reference inspection with placeholder quick-fix, and budget gutter icons for scene/actor blocks using AllIcons thresholds**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-21T12:05:14Z
- **Completed:** 2026-02-21T12:08:04Z
- **Tasks:** 2
- **Files modified:** 6 (3 created, 3 modified)

## Accomplishments
- CCodePreviewPanel now has C→DSL direction: caret movement on C editor navigates DSL editor to matching Kotlin line
- GbktCodegenService.findKotlinLocationForCLine() resolves C file+line to SourceLocation with nearest-preceding fallback
- GbktAssetRefInspection validates every `asset("path")` call exists on disk across 4 search roots
- GbktCreateAssetPlaceholderQuickFix writes a 1x1 transparent ARGB PNG at the missing asset path
- BudgetGutterIconProvider reads build/gbkt/budget-report.txt and shows green/yellow/red AllIcons next to scene/actor blocks

## Task Commits

Each task was committed atomically:

1. **Task 1: Source map viewer with bidirectional scrolling and asset ref inspections** - `ee004cc` (feat)
2. **Task 2: Budget report gutter icons** - `331ad8d` (feat)

**Plan metadata:** (docs commit below)

## Files Created/Modified
- `gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/inspections/GbktAssetRefInspection.kt` - LocalInspectionTool checking asset() path existence
- `gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/inspections/GbktCreateAssetPlaceholderQuickFix.kt` - Quick-fix creating 1x1 placeholder PNG
- `gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/gutter/BudgetGutterIconProvider.kt` - LineMarkerProvider with green/yellow/red icons
- `gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/codegen/GbktCodegenService.kt` - Added findKotlinLocationForCLine() method
- `gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/toolwindow/CCodePreviewPanel.kt` - Added setupCCaretListener() for C→DSL direction
- `gbkt-intellij-plugin/src/main/resources/META-INF/plugin.xml` - Registered GbktAssetRefInspection and BudgetGutterIconProvider

## Decisions Made
- C→DSL caret listener attaches to `editor.caretModel` directly (not EditorFactory.eventMulticaster) because it only needs to react to the C preview editor, avoiding project path filtering
- LineMarkerProvider targets `KtNameReferenceExpression` (identifier leaf) as anchor — ensures exactly one gutter icon per scene/actor call site
- Budget report uses line-oriented text format to avoid org.json/Gson dependencies on plugin classpath
- GbktCreateAssetPlaceholderQuickFix has hardcoded fallback PNG bytes for environments where ImageIO is unavailable

## Deviations from Plan

None — plan executed exactly as written. The plan referenced `services/GbktCodegenService.kt` but the actual file is at `codegen/GbktCodegenService.kt`; method was added to the correct existing file.

## Issues Encountered
- Kotlin warning "Elvis operator always returns left operand" on `element.containingFile ?: return null` — fixed by removing redundant null-check (PsiElement.containingFile is non-nullable in IntelliJ 2024.2)

## User Setup Required
None — no external service configuration required. Plugin compiles and installs into IntelliJ with `buildPlugin`.

## Next Phase Readiness
- All 3 IntelliJ DX directives (I1, I2, I3) implemented and compiling
- Plugin artifact verified via `buildPlugin` task
- Phase 06 plan 09 is the final plan in the phase

---
*Phase: 06-complete-gap-closure*
*Completed: 2026-02-21*
