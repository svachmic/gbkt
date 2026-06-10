---
phase: 11-port-banks-gbdk-example-to-gbkt
plan: 01
subsystem: testing
tags: [gradle, kotlin, scaffolding, gbkt-examples, banks]

# Dependency graph
requires:
  - phase: 09-port-simple-physics-gbdk-example-to-gbkt
    provides: simple-physics example structure (build.gradle.kts, IR/Emission/UAT test pattern)
  - phase: 10-port-metasprites-gbdk-example-to-gbkt
    provides: metasprites-stress example structure (settings.gradle.kts include slot)
provides:
  - Compiling-but-empty `gbkt-examples/banks/` Gradle subproject
  - settings.gradle.kts include for `gbkt-examples:banks`
  - Banks.kt one-line placeholder symbol resolvable by test files
  - BanksIRTest, BanksEmissionTest, BanksUatTest empty class stubs
  - EVIDENCE_DIR + extractFunctionBody brace-walk helper (locks per-function grep gate)
  - newAgent() Assumptions.assumeTrue skip-guard pattern
affects: [11-02, 11-03, 11-04, 11-05, 11-06, 11-07, 11-08, 11-11, 11-12, 11-13]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Wave-0 scaffolding pattern (empty class stubs with companion objects + private helpers, no @Test methods yet)
    - ramBanks.set(2) two-channel wiring (mandatory both in Gradle gbkt block AND DSL config)
    - res/tiles/.gitkeep placeholder so processAssets validation passes pre-asset

key-files:
  created:
    - gbkt-examples/banks/build.gradle.kts
    - gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt
    - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt
    - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt
    - gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt
    - gbkt-examples/banks/res/tiles/.gitkeep
  modified:
    - settings.gradle.kts

key-decisions:
  - "BanksUatTest imports GameMetadata from io.github.gbkt.emulator.agent (Rule 1 fix: plan listed io.github.gbkt.emulator.metadata which does not exist in the codebase; the actual class lives in the agent package — verified by grep against gbkt-emulator/src/main/kotlin/)"
  - "Created res/tiles/.gitkeep (Rule 3 fix: gbkt plugin's processAssets task validation requires the assets directory to exist; without it, even compileTestKotlin fails before Plan 11-04 ships the actual checker.png)"

patterns-established:
  - "Wave-0 scaffold: empty class stubs that compile but have no @Test methods — lets later plans add tests without scaffold churn"
  - "ramBanks two-channel wiring documented in build.gradle.kts inline (and verified via :tasks output that buildRom is exposed)"

requirements-completed: [BANK-W0-SCAFFOLD]

# Metrics
duration: 8min
completed: 2026-05-20
---

# Phase 11 Plan 01: Wave-0 Scaffold Summary

**Compiling-but-empty `gbkt-examples/banks/` Gradle subproject with placeholder Banks.kt and three empty test class stubs (IR/Emission/UAT) — `:gbkt-examples:banks:compileTestKotlin` GREEN.**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-05-20 (worktree agent abeef08ea398b4db4)
- **Completed:** 2026-05-20
- **Tasks:** 3
- **Files created:** 6
- **Files modified:** 1

## Accomplishments

- Created `gbkt-examples/banks/` Gradle subproject mirroring simple-physics structure, with `ramBanks.set(2)` two-channel wiring per RESEARCH Pitfall 1
- Added settings.gradle.kts include so the project resolves via `./gradlew :gbkt-examples:banks:tasks`
- Created Banks.kt one-line placeholder (`val banks = game("Banks") { start = "title"; scene("title") { } }`) so test files reference a real symbol; Plan 11-05 will overwrite with full DSL
- Created BanksIRTest, BanksEmissionTest, BanksUatTest empty class stubs that all compile against the placeholder — plus the brace-walk `extractFunctionBody` helper and the `newAgent()` Assumptions.assumeTrue skip-guard pattern that later plans will use
- `./gradlew :gbkt-examples:banks:compileTestKotlin --quiet` exits 0 (Wave-0 success criterion from VALIDATION.md)
- No regression in sibling example projects (`:gbkt-examples:simple-physics:compileKotlin` still exits 0)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create build.gradle.kts and add settings include** — `2ab25a13` (feat)
2. **Task 2: Create placeholder Banks.kt** — `4933e9b2` (feat)
3. **Task 3: Create empty test class stubs (IR/Emission/UAT)** — `10b9def2` (test)

## Files Created/Modified

- `gbkt-examples/banks/build.gradle.kts` — Gradle subproject config; gbkt plugin block with `game(...)`, `assets("res")`, `outputName.set("banks")`, `ramBanks.set(2)`
- `gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt` — Placeholder `val banks = game("Banks") { start = "title"; scene("title") { } }`
- `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt` — Empty class body with `private val ir = banks.build()`
- `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt` — EVIDENCE_DIR companion + `extractFunctionBody()` brace-walk helper
- `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt` — ROM_FILE/METADATA_FILE/EVIDENCE_DIR companion + `newAgent()` skip-guard
- `gbkt-examples/banks/res/tiles/.gitkeep` — Placeholder so processAssets validation passes before Plan 11-04 ships checker.png
- `settings.gradle.kts` — Added `include("gbkt-examples:banks")` after the metasprites-stress include

## Gradle Smoke-Command Output

```
$ ./gradlew :gbkt-examples:banks:tasks --quiet | grep -E "buildRom|generateC"
buildRom - Build Game Boy ROM from Kotlin DSL
generateC - Generate GBDK C code from Kotlin game definition
```

```
$ ./gradlew :gbkt-examples:banks:compileTestKotlin --quiet
EXIT: 0
```

## Decisions Made

- **GameMetadata import path correction.** Plan 11-01 §Task 3 listed `io.github.gbkt.emulator.metadata.GameMetadata` in the BanksUatTest imports. The simple-physics analog uses `io.github.gbkt.emulator.agent.GameMetadata`, and `grep -r "class GameMetadata" gbkt-emulator/src/main/kotlin/` confirms the class lives in `agent`, not `metadata`. Used the actual package path. Documented as Rule 1 (bug) in the Task 3 commit message.
- **res/tiles/.gitkeep marker.** The gbkt Gradle plugin's `processAssets` task validates that the assets directory exists at configuration time. Even `compileTestKotlin` runs through the plugin's task graph and triggers this validation. Without the directory, the build fails with "An input file was expected to be present but it doesn't exist." Created `gbkt-examples/banks/res/tiles/.gitkeep` so the directory exists; Plan 11-04 will ship the actual `checker.png` asset and the `.gitkeep` can be removed at that point. Documented as Rule 3 (blocking) in the Task 3 commit message.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] GameMetadata import path incorrect in plan**
- **Found during:** Task 3 (Create empty test class stubs)
- **Issue:** Plan 11-01 §Task 3 instructed `import io.github.gbkt.emulator.metadata.GameMetadata`. That package does not exist in the codebase; the actual class is in `io.github.gbkt.emulator.agent.GameMetadata` (verified by grep against `gbkt-emulator/src/main/kotlin/`). The simple-physics analog uses the correct path.
- **Fix:** Imported `io.github.gbkt.emulator.agent.GameMetadata` (matches analog and the actual class location).
- **Files modified:** `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt`
- **Verification:** `./gradlew :gbkt-examples:banks:compileTestKotlin --quiet` exits 0.
- **Committed in:** `10b9def2` (Task 3 commit)

**2. [Rule 3 - Blocking] Missing res/ directory blocks compileTestKotlin**
- **Found during:** Task 3 (verification step)
- **Issue:** `:gbkt-examples:banks:compileTestKotlin` failed with `ProcessAssetsTask` configuration error: "property 'assetDirectory' specifies directory '.../gbkt-examples/banks/res' which doesn't exist." The gbkt Gradle plugin validates asset directory existence at configuration time, regardless of which task you're running.
- **Fix:** Created `gbkt-examples/banks/res/tiles/.gitkeep` so the directory exists. Plan 11-04 will ship the actual `checker.png` asset (per 11-PATTERNS.md §"gbkt-examples/banks/res/tiles/checker.png"); the `.gitkeep` can be removed at that point if the directory has the PNG file.
- **Files modified:** `gbkt-examples/banks/res/tiles/.gitkeep` (new file)
- **Verification:** Re-ran `:gbkt-examples:banks:compileTestKotlin --quiet` → exits 0.
- **Committed in:** `10b9def2` (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both auto-fixes required to satisfy the Wave-0 success criterion (`:gbkt-examples:banks:compileTestKotlin` GREEN). No scope creep — neither fix expands the wave's surface area or pre-empts later plans' work.

## Issues Encountered

None beyond the two deviations above, both resolved inline.

## Threat Surface Scan

No new security-relevant surface introduced. The scaffolded files are empty test stubs + a one-line placeholder DSL declaration; no network endpoints, no auth paths, no file access at trust boundaries. Plan 11-01 threat model T-11-02 (settings.gradle.kts edit) mitigation honored — single literal include line appended; no template expansion or shell variables.

## Self-Check

Files exist:
- FOUND: `gbkt-examples/banks/build.gradle.kts`
- FOUND: `gbkt-examples/banks/src/main/kotlin/io/github/gbkt/examples/banks/Banks.kt`
- FOUND: `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksIRTest.kt`
- FOUND: `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksEmissionTest.kt`
- FOUND: `gbkt-examples/banks/src/test/kotlin/io/github/gbkt/examples/banks/BanksUatTest.kt`
- FOUND: `gbkt-examples/banks/res/tiles/.gitkeep`
- FOUND: `settings.gradle.kts` (modified, contains `include("gbkt-examples:banks")`)

Commits exist:
- FOUND: `2ab25a13` (Task 1)
- FOUND: `4933e9b2` (Task 2)
- FOUND: `10b9def2` (Task 3)

## Self-Check: PASSED

## Next Plan Readiness

Wave-0 scaffold complete. Subsequent plans in the phase can rely on `:gbkt-examples:banks:compileTestKotlin` and `:gbkt-examples:banks:generateC` working:

- **Plan 11-02 (reference ROM build)** — independent of this scaffold; produces `evidence/reference/` artifacts.
- **Plan 11-03 / 11-04 (scene substrate + zone asset)** — will modify Banks.kt and add the real `checker.png` (replacing the `.gitkeep` placeholder).
- **Plan 11-05** — will overwrite Banks.kt with the full DSL (config block, 3 scenes, zone, saveData).
- **Plans 11-06 / 11-07 / 11-08** — will add `@Test` methods to BanksIRTest and BanksEmissionTest using the scaffolded helpers (`extractFunctionBody`, `EVIDENCE_DIR`).
- **Plans 11-11 / 11-12** — will add UAT `@Test` methods using the scaffolded `newAgent()` skip-guard.

No blockers for downstream waves.

---
*Phase: 11-port-banks-gbdk-example-to-gbkt*
*Completed: 2026-05-20*
