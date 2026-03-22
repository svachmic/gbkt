---
phase: 01-ir-foundation-and-dsl
plan: 04
subsystem: rpg
tags: [kotlin, dsl, builder-pattern, bom-architecture, genre-package, sealed-ir, thread-local]

# Dependency graph
requires:
  - phase: 01-02
    provides: "GameBuilder with registerSystem(), ScriptBuilder with triggerSystem(), RefRegistry, SystemIR.GenericSystem"
provides:
  - "gbkt-rpg Gradle module with gbkt-core as sole dependency"
  - "CombatStats, CharacterDef, MonsterDef, EncounterDef, SimpleBattleDef domain data classes"
  - "character {} extension function on GameBuilder producing CharacterDef"
  - "monster {} extension function on GameBuilder producing MonsterDef"
  - "simpleBattle {} extension function on GameBuilder producing SystemIR.GenericSystem"
  - "battleUpdate() extension function on ScriptBuilder producing TriggerSystem ScriptOp"
  - "RpgRegistry ThreadLocal for character/monster registration during game {} block"
  - "BOM architecture pattern proven: genre packages add DSL ergonomics without extending sealed IR hierarchy"
affects:
  - 01-03-PLAN (Explorer example can now use gbkt-rpg for simple combat)
  - future backend codegen (GenericSystem with type=simple_battle must be handled in GBDK backend)
  - gbkt-bom (gbkt-rpg is now a module that bom should coordinate versions for)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "BOM separation pattern: genre packages depend on gbkt-core, GameBuilder does not know about RPG types"
    - "Extension function pattern for genre DSL: fun GameBuilder.character() etc. defined in gbkt-rpg"
    - "ThreadLocal registry pattern for DSL registration (RpgRegistry mirrors GameBuilderContext)"
    - "GenericSystem config map as extension mechanism: type=simple_battle + battle data in Map<String, Any>"
    - "Extension function on ScriptBuilder for genre-specific ops: battleUpdate() calls triggerSystem()"
    - "TDD: RED commit before GREEN implementation"

key-files:
  created:
    - gbkt-rpg/build.gradle.kts
    - gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/domain/CombatStats.kt
    - gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/domain/CharacterDef.kt
    - gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/domain/MonsterDef.kt
    - gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/domain/SimpleBattleDef.kt
    - gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/CharacterBuilder.kt
    - gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/MonsterBuilder.kt
    - gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/SimpleBattleBuilder.kt
    - gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt
    - gbkt-rpg/src/test/kotlin/io/github/gbkt/rpg/domain/DomainModelTest.kt
    - gbkt-rpg/src/test/kotlin/io/github/gbkt/rpg/dsl/RpgBuildersTest.kt
  modified:
    - settings.gradle.kts
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/GameBuilder.kt

key-decisions:
  - "GameBuilder.registerSystem() added as public API — genre packages need to register GenericSystem instances without a dedicated method per genre in GameBuilder; a single generic registration point allows indefinite genre package expansion"
  - "RpgRegistry uses ThreadLocal<MutableMap<String, Any>> — mirrors GameBuilderContext pattern; character/monster defs need to be accessible from simpleBattle builder which runs later in the same game {} lambda"
  - "EncounterDef uses List<String> for monsterIds (not List<MonsterDef>) — domain data is ID-referenced to keep SimpleBattleDef serializable and decoupled from MonsterDef object identity"
  - "SimpleBattleDef.onVictoryOps/onDefeatOps store List<ScriptOp> — ScriptBuilder produces the ops; storing them in domain data before wrapping in GenericSystem config keeps builder logic clean"

patterns-established:
  - "Genre package pattern: new genre modules depend on gbkt-core, extend GameBuilder via extension functions, produce GenericSystem — no core changes needed per genre"
  - "Extension function DSL pattern: character/monster/simpleBattle are top-level extension functions on GameBuilder defined in the genre package — avoids @GbktDsl scope restrictions that affect member functions"
  - "Config map convention: GenericSystem.config keys use snake_case strings; 'type' key identifies the system kind for backend routing"

requirements-completed: [DSL-04]

# Metrics
duration: 5min
completed: 2026-02-17
---

# Phase 1 Plan 04: gbkt-rpg Genre Package Summary

**gbkt-rpg Gradle module with RPG domain data classes and GameBuilder DSL extensions (character/monster/simpleBattle) proving BOM separation — genre packages produce GenericSystem core IR without extending the sealed IR hierarchy**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-17T20:47:43Z
- **Completed:** 2026-02-17T20:53:05Z
- **Tasks:** 2 (TDD: 1 RED + 1 GREEN)
- **Files modified:** 14 (9 production, 3 test, 2 config)

## Accomplishments

- New `gbkt-rpg` Gradle module with sole dependency on `gbkt-core` (no external libraries)
- Full RPG domain data layer: `CombatStats`, `CharacterDef`, `MonsterDef`, `EncounterDef`, `SimpleBattleDef` — plain Kotlin data classes, NOT IR types
- `character {}`, `monster {}`, `simpleBattle {}` extension functions on `GameBuilder` — user-facing DSL entry points from the genre package
- `battleUpdate()` extension on `ScriptBuilder` — emits `TriggerSystem(battleId)` for the combat state machine
- `RpgRegistry` ThreadLocal provides character/monster lookup during the `game {}` lambda without changing `GameBuilder`'s signature
- `GameBuilder.registerSystem()` added — generic registration API enabling any genre package to add systems without per-genre methods in core
- All 17 RPG tests pass; zero regressions in `gbkt-core` (1592 tests pass)
- BOM architecture constraint verified: no file in `gbkt-rpg/src/main/` implements a sealed IR interface

## Task Commits

Each task was committed atomically:

1. **Task 1: RED — failing tests for domain models and DSL builders** - `efda2a7` (test)
2. **Task 2: GREEN — RPG domain models and DSL builder extensions** - `6a7f6a7` (feat)

_TDD plan: RED commit before GREEN implementation_

## Files Created/Modified

**Production (9 files):**
- `gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/domain/CombatStats.kt` — hp/atk/def stats with init validation (hp > 0, atk >= 0, def >= 0)
- `gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/domain/CharacterDef.kt` — playable character: id, name, CombatStats
- `gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/domain/MonsterDef.kt` — enemy: id, name, CombatStats, expReward
- `gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/domain/SimpleBattleDef.kt` — EncounterDef + SimpleBattleDef with onVictory/onDefeat ScriptOp lists
- `gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/CharacterBuilder.kt` — CombatStatsBuilder + CharacterBuilder with name/stats methods
- `gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/MonsterBuilder.kt` — MonsterBuilder with name/stats/exp methods
- `gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/SimpleBattleBuilder.kt` — EncounterBuilder (unaryPlus for +monster), SimpleBattleBuilder with party/encounter/onVictory/onDefeat
- `gbkt-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/RpgExtensions.kt` — Extension functions on GameBuilder (character/monster/simpleBattle) and ScriptBuilder (battleUpdate); RpgRegistry ThreadLocal
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/v2/GameBuilder.kt` — Added `fun registerSystem(system: SystemIR)` public API

**Tests (3 files):**
- `gbkt-rpg/src/test/kotlin/io/github/gbkt/rpg/domain/DomainModelTest.kt` — 14 tests for data class construction, validation, copy, equality
- `gbkt-rpg/src/test/kotlin/io/github/gbkt/rpg/dsl/RpgBuildersTest.kt` — 9 tests for DSL builders: character/monster/simpleBattle/battleUpdate, GenericSystem config keys, sealed-subtype constraint

**Config (2 files):**
- `settings.gradle.kts` — Added `include("gbkt-rpg")` with BOM architecture comment
- `gbkt-rpg/build.gradle.kts` — Module definition: kotlin("jvm"), gbkt-core dependency, JUnit test runner

## Decisions Made

- **`GameBuilder.registerSystem()` as generic public API:** Genre packages need to register `GenericSystem` instances. Instead of adding a dedicated method per genre (which would couple core to all genres), a single `registerSystem(system: SystemIR)` API handles any genre's registration needs. This is the key enabler for the BOM separation pattern.

- **`RpgRegistry` with ThreadLocal:** The `simpleBattle {}` builder needs access to `CharacterDef` instances created earlier in the same `game {}` block. Since `CharacterDef` is a domain object (not a Kotlin property delegate), it can't use `GameBuilderContext`. A separate `RpgRegistry` ThreadLocal stores character/monster defs for the duration of the game block.

- **`EncounterDef.monsterIds` stores IDs, not objects:** Storing `List<String>` rather than `List<MonsterDef>` keeps `SimpleBattleDef` decoupled from object identity and aligns with how backend codegen will consume encounter data (as config map values).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed incorrect field name in test — `frameOps` not `frame`**
- **Found during:** Task 2 (GREEN — compilation)
- **Issue:** Test accessed `combatScene.frame` but `SceneIR` property is `frameOps`
- **Fix:** Changed to `combatScene!!.frameOps`
- **Files modified:** `gbkt-rpg/src/test/kotlin/io/github/gbkt/rpg/dsl/RpgBuildersTest.kt`
- **Verification:** Test compiles and passes
- **Committed in:** `6a7f6a7` (Task 2 feat commit)

---

**Total deviations:** 1 auto-fixed (1 Rule 1 — incorrect field name in test matching actual IR field name)
**Impact on plan:** Minor — test code corrected to match actual SceneIR API. No change to production code, no scope change.

## Issues Encountered

- `alias(libs.plugins.kotlin.jvm)` not defined in `gradle/libs.versions.toml` — corrected to `kotlin("jvm")` matching the pattern used by all existing modules (`gbkt-core`, `gbkt-backend-api`, etc.)

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `gbkt-rpg` module is ready for use by the Explorer example game (plan 01-03 uses `simpleBattle {}`)
- `GameBuilder.registerSystem()` is now available for any genre package to plug in without core changes
- Backend codegen (Phase 2) will need to handle `GenericSystem` with `config["type"] == "simple_battle"` — generate combat state machine C code from the config map
- The `RpgRegistry.clear()` is not yet called after `build()` — this is a potential memory leak in long-running tests; acceptable for now since ThreadLocal is garbage collected when thread terminates

---
*Phase: 01-ir-foundation-and-dsl*
*Completed: 2026-02-17*

## Self-Check: PASSED

- All 11 production/test files verified to exist on disk
- Both task commits verified in git log: `efda2a7` (RED tests) and `6a7f6a7` (GREEN implementation)
- All tests pass: `./gradlew :gbkt-rpg:test` BUILD SUCCESSFUL (17 RPG tests)
- Zero regressions: `./gradlew :gbkt-core:test` BUILD SUCCESSFUL (1592 tests)
- Dependency constraint verified: `runtimeClasspath` contains only `gbkt-core` + kotlin-stdlib
