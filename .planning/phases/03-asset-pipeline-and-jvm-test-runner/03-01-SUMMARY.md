---
phase: 03-asset-pipeline-and-jvm-test-runner
plan: 01
subsystem: asset-pipeline
tags: [tiles, tiled, ldtk, asset-manifest, json, deduplication]

# Dependency graph
requires:
  - phase: 02-structured-codegen-and-migration-cut
    provides: GBDKPipelineV2, v2 IR (GameIR, ScriptOp, ActorIR)
provides:
  - TileDeduplicator: content-based tile deduplication with ByteArrayKey index map
  - TiledParser.parseContent(): inline JSON parsing for testing
  - TiledLayer.properties: Map<String,Any> custom properties from Tiled layers
  - TiledLayer.isCollisionLayer: gbkt_collision=true detection
  - LdtkParser: LDtk JSON 1.5.x parser with Tiles/IntGrid/AutoLayer layers
  - LdtkMap, LdtkLayer, LdtkTilePlacement data classes
  - AssetManifest: JSON manifest model with toJson()/fromJson() round-trip
  - AssetManifestEntry sealed hierarchy: SpriteEntry and TilemapEntry
  - ScriptOpInterpreter: JVM v2 ScriptOp interpreter (untracked stub pre-built for Plan 03-03)
affects:
  - 03-02: ProcessAssetsTask wiring uses TileDeduplicator + AssetManifest
  - 03-03: ScriptOpInterpreter already in main test sources; interpreter tests pre-exist as untracked
  - 03-04: Example game tests depend on ScriptOpInterpreter and AssetManifest
  - 04: Phase 4 analysis passes consume asset-manifest.json

# Tech tracking
tech-stack:
  added: [JUnit 5 (junit-bom 6.0.1) added to gbkt-core test dependencies]
  patterns: [TDD red-green cycle per task, ByteArrayKey content-equality wrapper, sealed AssetManifestEntry with JSON discriminator]

key-files:
  created:
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/TileDeduplicator.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/LdtkParser.kt
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/AssetManifest.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/TileDeduplicatorTest.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/TiledParserTest.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/LdtkParserTest.kt
    - gbkt-core/src/test/kotlin/io/github/gbkt/core/AssetManifestTest.kt
  modified:
    - gbkt-core/src/main/kotlin/io/github/gbkt/core/TiledParser.kt
    - gbkt-core/build.gradle.kts

key-decisions:
  - "ByteArrayKey wraps ByteArray with contentEquals/contentHashCode — same pattern as AssetPipeline.Tile.equals()"
  - "TileDeduplicator returns first-occurrence ordered unique list + IntArray index map — callers must use index map to rewrite tilemap data"
  - "LdtkParser pinned to version 1.5.x via startsWith() check; clear error message includes the unsupported version string"
  - "AssetManifestEntry is a sealed class (not interface) to share toJson() delegation pattern"
  - "JUnit 5 added to gbkt-core test dependencies for ScriptOpInterpreterTest.kt (pre-written for Plan 03-03)"
  - "TiledLayer.properties defaults to emptyMap() — backward compatible; existing code without properties field unaffected"

patterns-established:
  - "ByteArrayKey pattern: wrap ByteArray in key class for use as Map key with content-based equality"
  - "AssetManifest discriminator: 'type' field in JSON ('SPRITE', 'TILEMAP') drives sealed class dispatch in fromJson()"
  - "parseContent() pattern: add content-based parse overload alongside File-based for testability without filesystem"

requirements-completed: [ASSET-01, ASSET-02, ASSET-03]

# Metrics
duration: 6min
completed: 2026-02-18
---

# Phase 3 Plan 01: Asset Pipeline Core Library Summary

**TileDeduplicator (content-based dedup with index map), extended TiledParser (gbkt_collision custom property), LdtkParser (1.5.x JSON, fieldInstance collision detection), and AssetManifest (JSON round-trip with sprite frame metadata)**

## Performance

- **Duration:** 6 min
- **Started:** 2026-02-18T16:55:01Z
- **Completed:** 2026-02-18T17:01:34Z
- **Tasks:** 2
- **Files modified:** 10 (7 created, 3 modified)

## Accomplishments
- TileDeduplicator class with ByteArrayKey produces deduplicated tile lists and index maps for all edge cases
- TiledParser extended with parseContent() overload, TiledLayer.properties field, and isCollisionLayer helper via gbkt_collision=true custom property
- LdtkParser parses LDtk 1.5.x JSON with Tiles/IntGrid/AutoLayer layers and collision detection via fieldInstances
- AssetManifest data model with full JSON round-trip for SpriteEntry (with frame metadata) and TilemapEntry

## Task Commits

Each task was committed atomically following TDD (RED tests → GREEN implementation):

1. **Task 1 RED: TileDeduplicator and TiledParser tests** - `2404b8d` (test)
2. **Task 1 GREEN: TileDeduplicator and TiledParser implementation** - `b422f9b` (feat)
3. **Task 2 RED: LdtkParser and AssetManifest tests** - `380f217` (test)
4. **Task 2 GREEN: LdtkParser and AssetManifest implementation** - `c99390f` (feat)

**Plan metadata:** (docs commit - see below)

_Note: TDD tasks have separate test (RED) and implementation (GREEN) commits._

## Files Created/Modified
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/TileDeduplicator.kt` - Content-based tile dedup with ByteArrayKey and IntArray index map
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/LdtkParser.kt` - LDtk 1.5.x JSON parser with collision detection via fieldInstances
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/AssetManifest.kt` - JSON manifest model with SpriteEntry/TilemapEntry sealed hierarchy
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/TiledParser.kt` - Added parseContent(), properties field, isCollisionLayer helper
- `gbkt-core/build.gradle.kts` - Added JUnit 5 test dependency for Plan 03-03 ScriptOpInterpreterTest
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/TileDeduplicatorTest.kt` - 6 tests covering all dedup edge cases
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/TiledParserTest.kt` - 5 tests covering properties parsing and isCollisionLayer
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/LdtkParserTest.kt` - 7 tests covering layer types, collision detection, version validation
- `gbkt-core/src/test/kotlin/io/github/gbkt/core/AssetManifestTest.kt` - 7 tests covering round-trip, version field, frame metadata, edge cases

## Decisions Made
- ByteArrayKey wraps ByteArray with contentEquals/contentHashCode — mirrors AssetPipeline.Tile.equals() pattern
- TileDeduplicator returns first-occurrence ordering; callers must use index map to rewrite tilemap data
- LdtkParser pinned to 1.5.x via startsWith() check with clear error including the actual version
- AssetManifestEntry is a sealed class (not interface) to share toJson() dispatch pattern
- JUnit 5 added to gbkt-core test dependencies because pre-existing untracked ScriptOpInterpreterTest.kt uses org.junit.jupiter.api

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added JUnit 5 dependency to fix ScriptOpInterpreterTest compilation**
- **Found during:** Task 2 (LdtkParser and AssetManifest tests)
- **Issue:** Pre-existing untracked file `ScriptOpInterpreterTest.kt` (for Plan 03-03) uses `org.junit.jupiter.api` which was not in gbkt-core test dependencies. All test compilation failed.
- **Fix:** Added `junit-bom 6.0.1` platform dependency + `junit-jupiter` + `junit-platform-launcher` to `gbkt-core/build.gradle.kts`
- **Files modified:** `gbkt-core/build.gradle.kts`
- **Verification:** All tests compile and pass
- **Committed in:** `380f217` (Task 2 RED commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** JUnit 5 dependency was required for compilation. ScriptOpInterpreter.kt and ScriptOpInterpreterTest.kt both pre-existed as untracked files placed by the user for Plan 03-03. No scope creep.

## Issues Encountered
- `ScriptOpInterpreter.kt` (main sources) and `ScriptOpInterpreterTest.kt` (test sources) were pre-existing untracked files for Plan 03-03, and both fully implemented. The test file used JUnit 5 which was missing from the build. Adding the dependency resolved it without any code changes to either file.

## Next Phase Readiness
- TileDeduplicator, LdtkParser, and AssetManifest are ready for Plan 03-02 (ProcessAssetsTask integration)
- ScriptOpInterpreter is pre-built and its tests pre-written — Plan 03-03 can proceed immediately
- No blockers for Phase 3 continuation

---
*Phase: 03-asset-pipeline-and-jvm-test-runner*
*Completed: 2026-02-18*
