---
phase: 22-golden-screenshot-and-evidence-storage-overhaul
plan: "03"
subsystem: build-infra
tags: [gitignore, gradle, golden-screenshots, build-wiring]
dependency_graph:
  requires: []
  provides: [evidence-scratch-gitignore, gbkt-updateGoldens-wiring, goldens-skeleton-dirs]
  affects: [gbkt-examples/metasprites, gbkt-examples/platformer-template, gbkt-examples/simple-physics, gbkt-examples/banks]
tech_stack:
  added: []
  patterns: [gradle-hasProperty-systemProperty, gitignore-doublestar-glob]
key_files:
  created:
    - gbkt-examples/metasprites/src/test/resources/goldens/.gitkeep
    - gbkt-examples/platformer-template/src/test/resources/goldens/.gitkeep
  modified:
    - .gitignore
    - gbkt-examples/metasprites/build.gradle.kts
    - gbkt-examples/platformer-template/build.gradle.kts
    - gbkt-examples/simple-physics/build.gradle.kts
    - gbkt-examples/banks/build.gradle.kts
decisions:
  - ".planning/phases/**/evidence/ is the gitignore rule; double-star glob covers all depth levels for new/untracked files"
  - "Force-keep comment at old line 62 removed; blessed anchors now live in src/test/resources/goldens/"
  - "project.hasProperty (boolean-presence) used for gbkt.updateGoldens — matches root build.gradle.kts isRelease precedent; NOT findProperty"
  - "simple-physics and banks receive systemProperty wiring only, no goldens skeleton (no blessed anchors this phase)"
metrics:
  duration: "2 min"
  completed: "2026-06-14T21:16:55Z"
  tasks_completed: 2
  tasks_total: 2
---

# Phase 22 Plan 03: Repository Wiring (gitignore + Gradle property) Summary

**One-liner:** Gitignore rule for evidence scratch dirs and `-Pgbkt.updateGoldens` → JVM systemProperty wiring in 4 example modules with goldens skeleton dirs for Wave 2 migration.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Update .gitignore (evidence scratch rule + remove force-keep exception) | f9d8d4dd | `.gitignore` |
| 2 | Wire gbkt.updateGoldens systemProperty + create goldens skeletons | 9d4814b0 | `4x build.gradle.kts`, `2x .gitkeep` |

## What Was Built

### Task 1: .gitignore Changes

Added to `.gitignore` (after the Phase 12 reference-ROM rules):

```
# Phase 22: per-phase evidence dirs are scratch — goldens live in src/test/resources/goldens/
.planning/phases/**/evidence/
```

Removed: the old line-62 comment `# uat-screenshots .pngs may also be considered local artifacts; KEEP them tracked (small, evidence-binding per D-10).`

The Phase 10 and Phase 12 reference-ROM binary ignore rules (`.gb`, `.map`, `.noi`, etc.) are untouched.

**git check-ignore behavior:** The rule applies to untracked files. Currently-tracked evidence files in closed phases will be removed by Wave 2 git-rm plans; the rule prevents new untracked files from surfacing after those removals.

### Task 2: Gradle Property Wiring + Goldens Skeletons

All 4 example `build.gradle.kts` now include:

```kotlin
tasks.test {
    useJUnitPlatform()
    if (project.hasProperty("gbkt.updateGoldens")) {
        systemProperty("gbkt.updateGoldens", "true")
    }
}
```

The `platformer-template` block additionally preserves the existing `dependsOn("convertSprites")` (Phase 15 F3/F4 wiring).

Created empty `src/test/resources/goldens/.gitkeep` in:
- `gbkt-examples/metasprites/` — receives blessed anchors in Wave 2
- `gbkt-examples/platformer-template/` — receives blessed anchors in Wave 2

`simple-physics` and `banks` receive the wiring only (no goldens skeleton — they have no blessed anchors this phase, but the wiring is in place if they are blessed later).

`spotlessApply` ran clean on all 4 modules.

## Verification

- `.planning/phases/**/evidence/` gitignored (confirmed via `git check-ignore` on untracked paths in existing evidence dirs)
- Force-keep comment removed (`grep "KEEP them tracked" .gitignore` returns nothing)
- 4 `build.gradle.kts` contain `gbkt.updateGoldens` (grep count = 4)
- 2 `goldens/.gitkeep` files exist and are committed

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None. This plan adds no network endpoints, auth paths, or external-input trust boundaries. The only trust boundary (Gradle invocation → test JVM system property) is an opt-in local build-tool boundary documented in the plan's threat model.

## Self-Check: PASSED

Files created/committed:
- `.gitignore` (modified) — FOUND
- `gbkt-examples/metasprites/build.gradle.kts` (modified) — FOUND
- `gbkt-examples/platformer-template/build.gradle.kts` (modified) — FOUND
- `gbkt-examples/simple-physics/build.gradle.kts` (modified) — FOUND
- `gbkt-examples/banks/build.gradle.kts` (modified) — FOUND
- `gbkt-examples/metasprites/src/test/resources/goldens/.gitkeep` (created) — FOUND
- `gbkt-examples/platformer-template/src/test/resources/goldens/.gitkeep` (created) — FOUND

Commits verified:
- f9d8d4dd — FOUND
- 9d4814b0 — FOUND
