---
phase: 15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin
plan: 02
subsystem: gradle-plugin-testkit
tags: [hermeticity, mavenLocal, snapshot-cache, IntegrationTest]
requires: [15-01]
provides: [plugintest-green, integrationtest-green]
affects: [15-06]
tech-stack:
  added: []
  patterns: [republish-all-consumed-modules, cacheChangingModulesFor-0]
key-files:
  created: []
  modified:
    - build.gradle.kts
    - gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/IntegrationTest.kt
    - .planning/phases/15-full-green-test-suite-for-v0-1-0-release-fix-all-pre-existin/evidence/diagnosis/integrationtest.md
key-decisions:
  - "Root cause was a NOT-republished transitive module (gbkt-analysis stale at Jun 5), not the changing-module cache alone — proven by a clean re-run still failing after the cache-defeat, then the ~/.m2 timestamp table."
  - "Two-edit durable fix: add :gbkt-analysis to mavenLocalModulesForPluginTest (root cause) + cacheChangingModulesFor(0) in createBasicBuildFile() (hardening). No assertion weakened/deleted."
requirements-completed: [REQ-3]
duration: 12 min
completed: 2026-06-09
---

# Phase 15 Plan 02: IntegrationTest green under pluginTest Summary

Resolved the `NoSuchMethodError: SceneIR.copy$default(...)` skew (12 IntegrationTest failures → 0)
at root cause by republishing the missing transitive module `gbkt-analysis` and hardening the
TestKit sandbox against stale changing-module resolution.

- **Duration:** 12 min · **Tasks:** 2 · **Files modified:** 3

## What was done

**Task 1 — Diagnosis (revised diagnose-first).** Initial hypothesis (research F1): the nested
`GradleRunner` serves a stale SNAPSHOT from the 24h changing-module cache. Applied
`cacheChangingModulesFor(0,"seconds")` to `createBasicBuildFile()` — a clean
`./gradlew --stop && ./gradlew pluginTest` STILL produced 12 identical failures. The `~/.m2`
timestamp table then revealed the true cause: `gbkt-analysis-0.1.0-SNAPSHOT.jar` was **stale
(Jun 5)** while all 7 republished modules were fresh (Jun 9 13:28). The stack-trace caller is
`io.github.gbkt.analysis.passes.*`; `gbkt-backend-gbdk` declares `api(project(":gbkt-analysis"))`,
so the sandbox transitively resolves the stale `gbkt-analysis` (13-field `SceneIR`) which links
against the fresh 14-field `gbkt-ir` → `NoSuchMethodError`. Fix Path = `real-bug-fix`
(build-hermeticity). Static evidence (D-03b).

**Task 2 — Fix + prove green.** (1) Added `:gbkt-analysis` to `mavenLocalModulesForPluginTest`
(build.gradle.kts) — the root-cause edit. (2) Kept `cacheChangingModulesFor(0,"seconds")` in the
single `createBasicBuildFile()` template as TTL-desync hardening. `./gradlew pluginTest` →
**BUILD SUCCESSFUL in 29s, 0 IntegrationTest failures**. No case deleted, skipped, or weakened.

## Deviations from Plan

**[Rule 2 — missing critical] Root-cause fix required build.gradle.kts (not in plan files_modified)** —
Found during: Task 1/2. The plan scoped the fix to `IntegrationTest.kt` (cache-defeat only); the
cache-defeat alone did not clear the error. Fix: add `:gbkt-analysis` to the republish set in
`build.gradle.kts`. build.gradle.kts is build config (no shared product codegen), no collision
with other Wave-2 plans. Verification: pluginTest BUILD SUCCESSFUL, 0 failures.

**Total deviations:** 1 auto-fixed (1 missing-critical). **Impact:** the durable fix is more
correct than planned — it closes the actual desync rather than only the cache TTL window.

## Issues Encountered

None unresolved.

## Next

Ready for 15-03 (BanksUatTest) / 15-04 (PongStepAgentTest) — remaining Wave 2 plans.

## Self-Check: PASSED

- [x] `./gradlew pluginTest` BUILD SUCCESSFUL, 0 IntegrationTest failures (log /tmp/gsd15_pt4.log EXIT=0)
- [x] `createBasicBuildFile()` contains `cacheChangingModulesFor(0, "seconds")`
- [x] build.gradle.kts `mavenLocalModulesForPluginTest` contains `:gbkt-analysis`
- [x] No IntegrationTest case deleted/skipped/weakened
- [x] evidence/diagnosis/integrationtest.md filled (root cause + Fix Path real-bug-fix + static evidence), revised to the true cause
- [x] `git log --grep="15-02"` returns 2 commits
