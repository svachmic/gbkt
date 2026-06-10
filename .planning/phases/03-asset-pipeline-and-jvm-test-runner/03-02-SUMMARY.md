---
phase: 03-asset-pipeline-and-jvm-test-runner
plan: 02
subsystem: asset-pipeline
tags: [gradle, asset-processing, png, 2bpp, tiles, tmx, ldtk, json-manifest, incremental-build]

# Dependency graph
requires:
  - phase: 03-asset-pipeline-and-jvm-test-runner
    plan: 01
    provides: TileDeduplicator, TiledParser, LdtkParser, AssetManifest, AssetManifestEntry

provides:
  - ProcessAssetsTask: real PNG→2bpp tile conversion with TileDeduplicator deduplication
  - ProcessAssetsTask: TMX/LDtk map parsing with isCollisionLayer/isCollision detection
  - ProcessAssetsTask: JSON manifest (asset-manifest.json) written via AssetManifest.toJson()
  - ProcessAssetsTask: incremental builds via Gradle InputChanges API (load/update/write manifest)
  - GbktPlugin: processAssets wired to build/generated/assets/ output directory

affects:
  - 03-04: Example game tests can now rely on processAssets producing real manifest
  - 04: Phase 4 analysis passes consume asset-manifest.json from build/generated/assets/

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "dispatchFile routing: .png/.tmx/.json/.ldtk dispatched to typed processors; others logged and skipped"
    - "Incremental manifest update: load existing AssetManifest, MutableMap<path,entry> updates, write back"
    - "Fail-fast pattern: all asset exceptions caught and re-thrown as GradleException with relPath in message"

key-files:
  created: []
  modified:
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ProcessAssetsTask.kt
    - gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/GbktPlugin.kt

key-decisions:
  - "processPng uses DEFAULT_PALETTE (not extractPalette) for SpriteEntry.palette — SpriteEntry.palette is GB luminance thresholds, not GBC colors; extractPalette returns GBCPalette (wrong type)"
  - "gbkt-core published to mavenLocal before plugin import resolves — includeBuild in pluginManagement compiles plugin before core; publish core first then restore plugin code"
  - "GbktPlugin output dir: generated/assets/ (not gbkt/processed-assets/); manifest: asset-manifest.json (not asset-manifest.txt)"

patterns-established:
  - "includeBuild publish ordering: when changing core API, publish core to mavenLocal before updating plugin imports"
  - "Asset validation error format: GradleException('Asset validation failed: $relPath — $detail')"

requirements-completed: [ASSET-04]

# Metrics
duration: 5min
completed: 2026-02-18
---

# Phase 3 Plan 02: ProcessAssetsTask Gradle Integration Summary

**Real asset processing in Gradle: PNG→deduplicated 2bpp tiles, TMX/LDtk map parsing with collision detection, JSON manifest at build/generated/assets/asset-manifest.json, incremental builds via InputChanges**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-18T17:12:19Z
- **Completed:** 2026-02-18T17:18:03Z
- **Tasks:** 1
- **Files modified:** 2

## Accomplishments
- ProcessAssetsTask processes PNG files to deduplicated 2bpp raw tile data (`.2bpp` files) using AssetPipeline.convertImage + TileDeduplicator.deduplicate
- TMX (Tiled JSON) and LDtk files parsed with TiledParser.parse(File) and LdtkParser.parse(content), detecting collision layers via isCollisionLayer / isCollision
- JSON manifest written via AssetManifest.toJson() to `build/generated/assets/asset-manifest.json` with version field and typed SpriteEntry/TilemapEntry entries
- Incremental builds load existing manifest, update changed entries, preserve unchanged entries, and delete outputs for removed files
- Invalid assets fail the build immediately with GradleException containing file path and error detail
- All 27 integration tests pass including sprite processing and incremental change tests

## Task Commits

Each task was committed atomically:

1. **Task 1: Replace ProcessAssetsTask stub with real asset processing** - `f699ab2` (feat)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ProcessAssetsTask.kt` - Complete rewrite: PNG→2bpp, TMX, LDtk processing; JSON manifest; incremental InputChanges support
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/GbktPlugin.kt` - Output path changed to `generated/assets/`, manifest changed to `asset-manifest.json`

## Decisions Made
- `processPng` uses `AssetPipeline.DEFAULT_PALETTE` for `SpriteEntry.palette` (not `extractPalette`) — `SpriteEntry.palette` stores GB luminance thresholds (int list), while `extractPalette` returns `GBCPalette` with RGB555 colors (wrong type for the manifest field)
- `GbktPlugin` now wires `outputDirectory` to `build/generated/assets/` and `manifestFile` to `build/generated/assets/asset-manifest.json` per locked decisions

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] gbkt-core publish required before Gradle plugin can import new classes**
- **Found during:** Task 1 (building :gbkt-gradle-plugin after adding new imports)
- **Issue:** `AssetManifest`, `TileDeduplicator`, `LdtkParser` were added in Phase 03-01 but the mavenLocal snapshot predated them. The `includeBuild` in `pluginManagement` causes Gradle to compile the plugin as part of any root build task, so `publishToMavenLocal` on the root project failed with unresolved references.
- **Fix:** Temporarily replaced ProcessAssetsTask with a minimal stub (no new imports), ran `:gbkt-core:publishToMavenLocal`, then restored the real implementation.
- **Files modified:** ProcessAssetsTask.kt (temp stub then restored)
- **Verification:** `jar -tf` confirmed new classes in the jar; full build succeeded
- **Committed in:** f699ab2 (Task 1 commit)

**2. [Rule 1 - Bug] Removed accidental call to extractPalette (wrong type, buggy for simple images)**
- **Found during:** Task 1 (4 integration tests failing with `RGB888 components must be 0-255`)
- **Issue:** Initial implementation called `AssetPipeline.extractPalette(image, name)` and then discarded the result (also using `DEFAULT_PALETTE` on the next line). The call was unnecessary AND buggy: `extractPalette`'s grayscale-padding loop computes `lum = 255 - (colors.size * luminanceStep)` which goes negative when colors.size grows during the loop (e.g. pure-white image with 1 unique color → 3 padding iterations → lum = -126 → validation throws).
- **Fix:** Removed the `extractPalette` call entirely; `SpriteEntry.palette` is set directly to `AssetPipeline.DEFAULT_PALETTE.toList()`
- **Files modified:** ProcessAssetsTask.kt
- **Verification:** All 27 integration tests pass; build succeeds
- **Committed in:** f699ab2 (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both auto-fixes required for correctness. The extractPalette bug is pre-existing in AssetPipeline.kt but only triggered by the new code path. No scope creep.

## Issues Encountered
- `includeBuild` in `pluginManagement` means the Gradle plugin compiles as part of every root project build. After Phase 03-01 added new classes to gbkt-core, the mavenLocal snapshot was stale. This manifested as unresolved reference errors in the plugin even when running `:gbkt-core:publishToMavenLocal`. Fix: publish core in isolation first (using a temporary stub to allow the root build to compile), then restore the full implementation.

## Next Phase Readiness
- processAssets task produces real `.2bpp` files and `asset-manifest.json` — Phase 4 analysis passes can consume the manifest
- ProcessAssetsTask is wired and tested; Plan 03-04 example game tests can invoke processAssets
- No blockers for Phase 3 completion (Plan 03-04 remaining)

## Self-Check: PASSED

- ProcessAssetsTask.kt: FOUND
- GbktPlugin.kt: FOUND
- 03-02-SUMMARY.md: FOUND
- Commit f699ab2: FOUND
- Build: PASSED (27 tests, 0 failures)

---
*Phase: 03-asset-pipeline-and-jvm-test-runner*
*Completed: 2026-02-18*
