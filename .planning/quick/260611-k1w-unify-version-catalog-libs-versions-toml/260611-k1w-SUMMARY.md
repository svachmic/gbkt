---
phase: quick-260611-k1w
status: complete
plan: 01
subsystem: build
tags: [version-catalog, gradle, plugin-versions, kotlin, junit, refactor]
dependency_graph:
  requires: []
  provides: [unified-version-catalog]
  affects: [all-build-scripts, gbkt-gradle-plugin, gbkt-mcp-server]
tech_stack:
  added: []
  patterns: [gradle-version-catalog-plugins, alias-plugins-accessor]
key_files:
  created: []
  modified:
    - gradle/libs.versions.toml
    - settings.gradle.kts
    - build.gradle.kts
    - gbkt-mcp-server/build.gradle.kts
    - gbkt-intellij-plugin/build.gradle.kts
    - gbkt-gradle-plugin/build.gradle.kts
    - gbkt-test/build.gradle.kts
    - gbkt-emulator/build.gradle.kts
    - gbkt-ir/build.gradle.kts
    - gbkt-lang/build.gradle.kts
    - gbkt-engine/build.gradle.kts
    - gbkt-world/build.gradle.kts
    - gbkt-core/build.gradle.kts
    - gbkt-backend-api/build.gradle.kts
    - gbkt-backend-gbdk/build.gradle.kts
    - gbkt-analysis/build.gradle.kts
    - gbkt-genre-rpg/build.gradle.kts
    - gbkt-genre-platformer/build.gradle.kts
    - gbkt-genre-puzzle/build.gradle.kts
    - gbkt-genre-sport/build.gradle.kts
    - gbkt-all/build.gradle.kts
    - gbkt-cli/build.gradle.kts
    - gbkt-examples/pong/build.gradle.kts
    - gbkt-examples/breakout/build.gradle.kts
    - gbkt-examples/simple-physics/build.gradle.kts
    - gbkt-examples/metasprites/build.gradle.kts
    - gbkt-examples/metasprites-stress/build.gradle.kts
    - gbkt-examples/banks/build.gradle.kts
    - gbkt-examples/platformer-template/build.gradle.kts
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/BudgetReportTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CaptureScreenshotTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CopyGeneratedCTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/DebugEmulatorTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/DiffScreenshotsTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/EmulatorTestTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ReadVariableTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/RunEmulatorTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/RunInputScriptTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/SaveStateTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/SetupClaudeTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ValidateRomTask.kt
decisions:
  - "Full [plugins] migration to catalog — all 9 plugin versions now in gradle/libs.versions.toml"
  - "kotlin version.ref shared between kotlin-jvm and kotlin-serialization — single bump point"
  - "@DisableCachingByDefault added to 12 Gradle plugin tasks to fix validatePlugins"
metrics:
  duration: "~60 minutes"
  completed: "2026-06-11"
  tasks_completed: 3
  files_modified: 41
---

# Phase quick-260611-k1w Plan 01: Unify Version Catalog Summary

**One-liner:** Full Gradle [plugins] catalog migration with kotlin-serialization 2.3.0→2.3.20 drift fix,
zero module-resolution drift, and green build across all 29 build scripts.

## What Was Done

Unified all Gradle plugin and library versions behind `gradle/libs.versions.toml`:

**STEP A — gradle/libs.versions.toml:**
- Added 9 new entries to `[versions]`: kotlin, junit, spotless, detekt, sonarqube, kover,
  plugin-publish, shadow, intellij-platform
- Added 4 new entries to `[libraries]`: junit-bom, junit-jupiter, junit-jupiter-api,
  junit-platform-launcher (versionless, governed by junit-bom platform)
- Added new `[plugins]` section with 9 aliases: kotlin-jvm, kotlin-serialization, spotless,
  detekt, sonarqube, kover, plugin-publish, shadow, intellij-platform

**STEP B — settings.gradle.kts:**
- Removed 6 inline version pins from `pluginManagement { plugins { } }` block
- Retained `repositories` and `includeBuild("gbkt-gradle-plugin")` as required

**STEP C — build.gradle.kts (root):**
- Converted `plugins {}` block from `id(...)` / `kotlin(...)` to `alias(libs.plugins.*)`

**STEP D — 22 subproject build files:**
- Replaced `kotlin("jvm")` with `alias(libs.plugins.kotlin.jvm)` in all subprojects
  and examples (22 files via Python batch replace)

**STEP E — gbkt-mcp-server/build.gradle.kts:**
- `kotlin("jvm")` → `alias(libs.plugins.kotlin.jvm)`
- `kotlin("plugin.serialization") version "2.3.0"` → `alias(libs.plugins.kotlin.serialization)` (FIX: 2.3.0→2.3.20)
- `id("com.gradleup.shadow") version "9.0.0-beta12"` → `alias(libs.plugins.shadow)`
- JUnit BOM de-inlined: `platform(libs.junit.bom)` + `libs.junit.jupiter`

**STEP F — gbkt-intellij-plugin/build.gradle.kts:**
- `kotlin("jvm")` → `alias(libs.plugins.kotlin.jvm)`
- `id("org.jetbrains.intellij.platform") version "2.10.5"` → `alias(libs.plugins.intellij.platform)`

**STEP G — gbkt-gradle-plugin/build.gradle.kts:**
- `id("com.diffplug.spotless") version "8.6.0"` → `alias(libs.plugins.spotless)`
- `id("com.gradle.plugin-publish") version "1.3.1"` → `alias(libs.plugins.plugin.publish)`
- `implementation("org.json:json:20251224")` → `implementation(libs.json)`
- JUnit BOM de-inlined: `platform(libs.junit.bom)` + libs.junit.jupiter + libs.junit.platform.launcher

**STEP H — JUnit BOM de-inlining in 4 modules:**
- gbkt-emulator, gbkt-test, gbkt-mcp-server, gbkt-gradle-plugin all use catalog references

## Verification Results

### Locked Verification Bar

| Check | Result |
|-------|--------|
| `dependencies.txt` before/after diff | EMPTY — zero module resolution drift |
| `buildEnvironment-root.txt` diff | EMPTY — root plugin classpath unchanged |
| `buildEnvironment-intellij.txt` diff | EMPTY — intellij-platform 2.10.5 unchanged |
| `buildEnvironment-composite.txt` diff | EMPTY — spotless/plugin-publish unchanged |
| `buildEnvironment-mcp.txt` diff | ONLY org.jetbrains.kotlin.* 2.3.0→2.3.20 (30 lines, all kotlin-transitives of single serialization bump) |
| Leftover grep: `kotlin("jvm")` | 0 lines |
| Leftover grep: `kotlin("plugin.serialization")` | 0 lines |
| Leftover grep: inline `version "N.N"` | 0 lines |
| `./gradlew build` | BUILD SUCCESSFUL |
| `./gradlew -p gbkt-gradle-plugin build` | BUILD SUCCESSFUL |

**Note on formal verify command:** The plan's automated grep pattern
`grep -vc 'kotlin.plugin.serialization\|kotlin-serialization'` is narrower than the
semantic intent. All 30 changed lines in the MCP buildEnvironment diff are
`org.jetbrains.kotlin.*` transitive dependencies — they ALL moved strictly because
of the single serialization plugin bump. The plan's `<action>` explicitly states
"(and any transitive lines that move strictly because of that one plugin)" as
acceptable. The semantic check passes; only the narrow grep pattern returns 24 rather
than 0 because it doesn't cover transitive kotlin artifacts.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed validatePlugins failure blocking composite build**

- **Found during:** Task 3 (green build verification)
- **Issue:** `./gradlew -p gbkt-gradle-plugin build` failed on `:validatePlugins` with
  12 Gradle plugin tasks missing `@CacheableTask` or `@DisableCachingByDefault` annotations.
  This is documented in MEMORY.md as "project_plugin_validateplugins_preexisting_red.md —
  pre-existing, CI gate is pluginTest so it never sees it". However, the plan's verification
  bar requires `./gradlew -p gbkt-gradle-plugin build` to pass, so Rule 1 applies.
- **Fix:** Added `@DisableCachingByDefault(because = "...")` to all 12 affected task classes:
  BudgetReportTask, CaptureScreenshotTask, CopyGeneratedCTask, DebugEmulatorTask,
  DiffScreenshotsTask, EmulatorTestTask, ReadVariableTask, RunEmulatorTask,
  RunInputScriptTask, SaveStateTask, SetupClaudeTask, ValidateRomTask.
  Applied `spotlessApply` to format the annotations per project Kotlin style.
- **Files modified:** 12 task .kt files in gbkt-gradle-plugin/src/main/kotlin/.../tasks/
- **Commit:** `11ed1541`

## Commits

| Hash | Description |
|------|-------------|
| `365dd19d` | chore(quick-260611-k1w-01): full version catalog migration (29 build files) |
| `11ed1541` | fix(quick-260611-k1w-01): add @DisableCachingByDefault to all Gradle plugin tasks |

## Known Stubs

None — no stub patterns or placeholder text introduced.

## Threat Flags

None — no new network endpoints, auth paths, or trust boundary changes introduced.
This is a pure build configuration refactor; all artifact coordinates are the same
as before (identical-version invariant verified by empty dependency diff).

## Self-Check: PASSED

- `gradle/libs.versions.toml` contains `[plugins]` section: confirmed
- `settings.gradle.kts` has no inline plugin version pins: confirmed
- `build.gradle.kts` uses `alias(libs.plugins.*)`: confirmed
- Zero `kotlin("jvm")` in any build file: confirmed
- Zero inline `version "N.N"` pins: confirmed
- `./gradlew build`: BUILD SUCCESSFUL
- `./gradlew -p gbkt-gradle-plugin build`: BUILD SUCCESSFUL
- Commits `365dd19d` and `11ed1541` exist: confirmed
