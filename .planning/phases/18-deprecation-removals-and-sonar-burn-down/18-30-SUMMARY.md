---
phase: 18-deprecation-removals-and-sonar-burn-down
plan: 30
subsystem: ide
tags: [intellij-plugin, dsl, whenever, runIf, completion, highlighting, documentation, templates]

# Dependency graph
requires:
  - phase: 18-deprecation-removals-and-sonar-burn-down
    provides: whenever hard-removed from DSL in Phase 18 earlier plans
provides:
  - gbkt-intellij-plugin now advertises runIf (not whenever) for all IDE surfaces
affects: [intellij-plugin, dsl, ide-completion, ide-highlighting, ide-documentation, ide-templates]

# Tech tracking
tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified:
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/lang/GbktDslVisitor.kt
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/highlighting/GbktSyntaxHighlighter.kt
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/highlighting/GbktColorSettingsPage.kt
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/completion/GbktKeywordCompletionProvider.kt
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/completion/GbktBuilderCompletionProvider.kt
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/documentation/GbktDocumentationProvider.kt
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/project/templates/MinimalTemplate.kt
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/project/templates/RpgTemplate.kt
    - gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/project/templates/PlatformerTemplate.kt
    - gbkt-intellij-plugin/src/test/kotlin/io/github/gbkt/intellij/lang/GbktDslVisitorTest.kt

key-decisions:
  - "Replace whenever→runIf in all IDE registration sites; runIf was absent so no duplicate-entry guard needed"
  - "English prose 'whenever' in GbktCodegenService.kt:81 (JavaDoc comment) left intentionally untouched"
  - "KDoc comments within gbkt-intellij-plugin describing DSL keywords updated (e.g. highlighter class-doc, control-flow keyword doc)"

patterns-established: []

requirements-completed: []

# Metrics
duration: 5min
completed: 2026-06-13
---

# Phase 18 Plan 30: whenever→runIf IDE Migration Summary

**IDE `whenever` keyword retired across all gbkt-intellij-plugin surfaces: DSL_FUNCTIONS, CONTROL_FLOW set, completion maps, documentation provider, color-settings sample, and all three project templates now advertise `runIf`.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-06-13T17:46:55Z
- **Completed:** 2026-06-13T17:51:55Z
- **Tasks:** 1
- **Files modified:** 10

## Accomplishments
- Replaced `whenever` with `runIf` across all 11 functional IDE sites in gbkt-intellij-plugin
- English prose comment in GbktCodegenService.kt ("invalidated whenever readCachedSourceMap() is called") deliberately preserved
- GbktDslVisitorTest assertion updated to assert `"runIf" in DSL_FUNCTIONS`
- Build green: `./gradlew :gbkt-intellij-plugin:build` (compile + test + spotless + detekt) all passed

## Task Commits

1. **Task 1: migrate whenever→runIf in gbkt-intellij-plugin (DEPR-01)** - `a5004b30` (feat)

**Plan metadata:** see final-commit below

## Files Modified
- `gbkt-intellij-plugin/.../lang/GbktDslVisitor.kt` — DSL_FUNCTIONS set: `"whenever"` → `"runIf"`
- `gbkt-intellij-plugin/.../highlighting/GbktSyntaxHighlighter.kt` — CONTROL_FLOW set + 2 KDoc comments
- `gbkt-intellij-plugin/.../highlighting/GbktColorSettingsPage.kt` — 4 sample-code occurrences
- `gbkt-intellij-plugin/.../completion/GbktKeywordCompletionProvider.kt` — keyword map entry + class-doc comment
- `gbkt-intellij-plugin/.../completion/GbktBuilderCompletionProvider.kt` — DEFAULT_SUGGESTIONS entry
- `gbkt-intellij-plugin/.../documentation/GbktDocumentationProvider.kt` — doc-entry key + 20+ example snippets + branch "see also" list
- `gbkt-intellij-plugin/.../project/templates/MinimalTemplate.kt` — 4 template code occurrences
- `gbkt-intellij-plugin/.../project/templates/RpgTemplate.kt` — 5 template code occurrences
- `gbkt-intellij-plugin/.../project/templates/PlatformerTemplate.kt` — 4 template code occurrences
- `gbkt-intellij-plugin/.../lang/GbktDslVisitorTest.kt` — test assertion updated

## Decisions Made
- `runIf` was absent from all IDE sites (no pre-existing registration) so a straightforward replace was correct; no duplicate-entry guard needed
- English prose at GbktCodegenService.kt:81 left untouched per scope directive
- KDoc comments within gbkt-intellij-plugin referencing `whenever` as a DSL keyword were updated (in-scope: intellij module docs)
- README.md, non-intellij module KDoc, and example CLAUDE.md files were not touched (out-of-scope per plan)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Build was clean on first attempt after changes.

## Next Phase Readiness
- IDE is now consistent with the DSL: `whenever` no longer appears in completion suggestions, highlighting sets, documentation, or generated project templates
- Cosmetic doc updates (README.md, non-intellij KDoc, example CLAUDE.md files) remain deferred to a separate seed per plan scope

---
*Phase: 18-deprecation-removals-and-sonar-burn-down*
*Completed: 2026-06-13*

## Self-Check: PASSED
- All 10 modified files confirmed present on disk
- Commit a5004b30 confirmed in git log
- Zero DSL `whenever` occurrences remaining in gbkt-intellij-plugin/src (only English prose in GbktCodegenService.kt:81)
- `./gradlew :gbkt-intellij-plugin:build` GREEN (compile + test + spotless + detekt)
