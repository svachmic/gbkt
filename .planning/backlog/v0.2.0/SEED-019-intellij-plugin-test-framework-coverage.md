# SEED-019 — IntelliJ Platform test framework for gbkt-intellij-plugin (restore measurable coverage)

> **Triage:** RE-DEFERRED — [TRIAGE.md#SEED-019](.planning/phases/16-seed-triage/TRIAGE.md#SEED-019) · 2026-06-12

**Origin:** SonarCloud quality-gate analysis of PR #33 (`feat/d_and_d_gaps`), 2026-06-10
**Status:** Open — not yet bound to a target phase
**Routing:** Needs its own phase (discuss-phase + research). Standing up the IntelliJ Platform test framework is a module-wide testing-infrastructure effort, not an inline patch.
**Blast radius:** Contained to `gbkt-intellij-plugin` + root build coverage wiring (`koverAggregatedModules`, `sonar.coverage.exclusions`), but the module surface is large (~4,600 executable lines: editors, tool windows, debug panels, build tools, inspections, navigation).

## Problem

`gbkt-intellij-plugin` has **zero measurable test coverage by construction**:

- The root `build.gradle.kts` deliberately excludes it from the kover merge
  (`koverAggregatedModules` comment: "gbkt-intellij-plugin (IDE sandbox test
  runtime) are excluded from the merge"). Plugin tests run inside the IntelliJ
  platform sandbox, which the plain kover/JaCoCo wiring can't instrument.
- SonarCloud reads the single merged kover report; files absent from the report
  count every executable line as uncovered. In PR #33 the plugin contributed
  **4,589 uncovered new lines at 0.0%**, dragging new-code coverage from a true
  ~78.6% (rest of codebase) down to a reported 70.7% against the 80% gate.

The PR #33 remediation adds `sonar.coverage.exclusions = gbkt-intellij-plugin/**`
to the root `sonarqube {}` block — a measurement correction (the module stays in
scope for bugs/smells/hotspots), but it means the plugin's coverage goes
**permanently unwatched** until this seed is resolved.

## Goal

Run `gbkt-intellij-plugin` tests on the IntelliJ Platform test framework with
coverage that merges into the root kover/JaCoCo report, then **delete the
`sonar.coverage.exclusions` entry** so the module is held to the same gate as
every other module.

## Scope sketch (for the discuss-phase)

1. **Test framework selection** — IntelliJ Platform Test Framework
   (`com.intellij.testFramework`, `BasePlatformTestCase` / light fixtures) via
   the `intellij-platform-gradle-plugin` `testFramework(...)` dependency helpers.
   Decide: light fixtures (in-memory project, fast, covers most of the plugin)
   vs heavy fixtures vs UI tests (Robot server) — likely light-only for v1.
2. **Coverage wiring** — get JaCoCo/kover agent attached to the sandbox test JVM
   and the resulting report merged into `build/reports/kover/report.xml` (or
   listed as an additional path in `sonar.coverage.jacoco.xmlReportPaths`).
   This is the part that historically didn't work; it needs research, not
   assumption.
3. **What to test first** (highest-value, lowest-fixture-cost — these were the
   largest 0%-coverage files in PR #33):
   - `toolwindow/CCodePreviewPanel.kt` (211 lines; also carries an S3923
     identical-branches Sonar bug — fix + test together)
   - `editors/tilemap/TilemapEditorPanel.kt` / `TilemapPanel.kt` / `TilesetPanel.kt` (~600 lines)
   - `buildtools/RomSizeAnalyzer.kt`, `buildtools/GradleRunner.kt` (S899 bug at :126)
   - `codegen/GbktCodegenService.kt`, `inspections/GbktDslInspection.kt`
   - `debug/EntityPreviewPanel.kt` (2 reviewed `java.util.Random` hotspots live here)
   - Pure-logic classes (analyzers, mappers, models) may not need the platform
     fixture at all — extracting them to plain JUnit reach is a valid tactic.
4. **Exit criteria:**
   - Plugin tests run in CI (`kotlin.yml`) without an interactive IDE.
   - Coverage for `gbkt-intellij-plugin` appears in the merged report Sonar reads.
   - `sonar.coverage.exclusions` entry for the plugin is **removed**.
   - New-code coverage gate (80%) still passes with the plugin back in the denominator.

## Discovery hooks

- Root `build.gradle.kts` — `koverAggregatedModules` exclusion comment and the
  `sonar.coverage.exclusions` property (added for PR #33) should both reference
  this seed.
- PR #33 Sonar analysis (SonarCloud project `svachmic_gbkt`, pull request 33):
  per-module new-code coverage breakdown showing `gbkt-intellij-plugin` 4,589/4,589
  uncovered.
- `gbkt-intellij-plugin/CLAUDE.md` — module doc for the plugin surface.
