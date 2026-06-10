---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 03
subsystem: examples
tags: [gradle, scaffold, kotlin, gbkt-examples, platformer-template, wave-0]

# Dependency graph
requires:
  - phase: 11-port-banks-gbdk-example-to-gbkt
    provides: scaffold pattern (build.gradle.kts + IR/Emission/UAT test triplet) copied from gbkt-examples/banks/
provides:
  - "gbkt-examples/platformer-template/ Gradle subproject (with gbkt-genre-platformer dependency)"
  - "Placeholder val platformerTemplate = game(\"PlatformerTemplate\") { } symbol for subsequent plans to overwrite"
  - "3 test scaffolds in gbkt-examples/platformer-template/ (IR/Emission/UAT) + 5 SKIP-only @Test anchor stubs"
  - "4 Wave-0 emission-test scaffolds (TilemapCollision / HorizontalScroll / JumpHold in genre-platformer; MultiTilesetAllocation in backend-gbdk)"
  - "settings.gradle.kts include(\"gbkt-examples:platformer-template\")"
affects:
  - 12-04 (asset import — populates res/ with reference PNGs)
  - 12-09 / 12-09b / 12-12 / 12-14 / 12-15 (fill emission-test bodies with D-16 invariants)
  - 12-16 / 12-17 / 12-18 (overwrite PlatformerTemplate.kt body with the full substrate DSL)
  - 12-19..23 (fill UAT anchor 1..5 @Test bodies with MCP play-through + screenshot)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Wave-0 scaffold-first discipline (mirror of Phase 11 Plan 01): build.gradle.kts + placeholder Game DSL + empty IR/Emission/UAT triplet before any feature plan touches DSL"
    - "5 SKIP-only @Test anchor stubs (one per D-08 anchor) using Assumptions.assumeTrue(ROM_FILE.exists(),...) so the test suite stays GREEN before buildRom exists"
    - "Per-genre emission-test scaffolds in their owning module (genre-platformer / backend-gbdk) rather than in the example subproject — keeps invariant tests close to the visitor code they lock"

key-files:
  created:
    - "gbkt-examples/platformer-template/build.gradle.kts"
    - "gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt"
    - "gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateIRTest.kt"
    - "gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateEmissionTest.kt"
    - "gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt"
    - "gbkt-examples/platformer-template/res/.gitkeep"
    - "gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/TilemapCollisionEmissionTest.kt"
    - "gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/HorizontalScrollEmissionTest.kt"
    - "gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/JumpHoldEmissionTest.kt"
    - "gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/MultiTilesetAllocationTest.kt"
  modified:
    - "settings.gradle.kts"

key-decisions:
  - "Skipped ramBanks config: platformer_template has no SRAM per CONTEXT D-claude-3 — used minimal gbkt { } block (no ramBanks set, defaults to no SRAM)"
  - "Used JVM platformer_template subdir Kotlin package convention (io.github.gbkt.examples.platformer_template with underscore) to match Gradle subproject naming (platformer-template uses hyphen, Kotlin needs underscore)"
  - "Inserted settings.gradle.kts line AFTER include(\"gbkt-examples:banks\") to preserve example ordering (Wave-0 plans land between banks and the future retirement of platformer/platformer-gbc)"
  - "Created res/.gitkeep placeholder to satisfy ProcessAssetsTask assetDirectory input contract — Plan 12-04 populates real reference PNG assets"

patterns-established:
  - "Wave-0 emission-test scaffold pattern: each D-16 invariant gets a single-file scaffold in its owning module (genre vs backend) with `@Test fun placeholder() { /* TODO: Plan 12-NN fills this in */ }` body; subsequent plan rewrites the body in-place"
  - "UAT anchor naming convention: `anchorN<CamelCaseDescription>` (e.g. anchor1Title_to_Gameplay, anchor2TilemapCollision) consistent across the 5 anchors so VALIDATION.md table rows have stable test-method identifiers"
  - "Single-line `@Test fun anchorN_xxx()` formatting to satisfy `grep -c '@Test fun anchor'` acceptance criteria (avoids brittle multi-line @Test-annotation grep)"

requirements-completed: [D-02, D-claude-1, D-claude-3, D-claude-4]

# Metrics
duration: ~8min
completed: 2026-05-21
---

# Phase 12 Plan 03: Wave-0 Scaffold for platformer-template Subproject Summary

**Created the gbkt-examples/platformer-template/ Gradle subproject + 10-file Wave-0 scaffold (build.gradle.kts, placeholder DSL stub, 3 example-side tests, 4 module-side emission-test scaffolds) so subsequent waves of Phase 12 have a compiling skeleton.**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-05-21T19:41:00Z
- **Completed:** 2026-05-21T19:50:00Z
- **Tasks:** 4
- **Files created:** 10 (1 build script + 1 stub source + 3 example tests + 1 res/.gitkeep + 4 module emission-test scaffolds)
- **Files modified:** 1 (settings.gradle.kts)

## Accomplishments

- `gbkt-examples/platformer-template/` Gradle subproject recognized by `./gradlew projects` with the gbkt plugin applied
- `:gbkt-examples:platformer-template:compileTestKotlin` GREEN against placeholder `platformerTemplate` symbol
- `:gbkt-examples:platformer-template:test` GREEN — 5 UAT anchor stubs skip cleanly via `Assumptions.assumeTrue(ROM_FILE.exists())` until Plans 12-19..23 wire them
- Wave-0 emission-test scaffolds compile in both `gbkt-genre-platformer` and `gbkt-backend-gbdk`; the `placeholder()` @Test methods pass GREEN as no-ops
- No regression to other examples (`:gbkt-examples:simple-physics:compileKotlin` still GREEN)
- settings.gradle.kts contains `include("gbkt-examples:platformer-template")` exactly once

## Task Commits

Each task was committed atomically:

1. **Task 1: Create build.gradle.kts and add settings include** — `7ac6ecd2` (feat)
2. **Task 2: Create placeholder PlatformerTemplate.kt stub** — `9053b549` (feat)
3. **Task 3: Create 3 empty test class stubs (IR / Emission / UAT)** — `207aca71` (test) — also includes res/.gitkeep (Rule 3 blocking-fix)
4. **Task 4: Create Wave-0 emission test scaffolds in genre-platformer + backend-gbdk** — `785d0b85` (test)

## Files Created/Modified

### Created
- `gbkt-examples/platformer-template/build.gradle.kts` — Gradle subproject config: kotlin("jvm") + io.github.gbkt plugin; deps on gbkt-bom, gbkt-backend-gbdk, gbkt-genre-platformer (testImpl: kotlin("test"), gbkt-emulator, gbkt-test); gbkt { game(...platformer_template.PlatformerTemplateKt::platformerTemplate) ; assets("res") ; outputName("platformer-template") }
- `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt` — MPL 2.0 header + minimal `val platformerTemplate = game("PlatformerTemplate") { start = "title"; scene("title") { } }` so Wave-0 test scaffolds reference a real symbol
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateIRTest.kt` — Class shell with `private val ir = platformerTemplate.build()` field; Plan 12-09+ adds @Test methods
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateEmissionTest.kt` — extractFunctionBody() brace-walk helper + EVIDENCE_DIR worktree-safe companion; Plans 12-09 / 12-09b / 12-12 / 12-14 / 12-15 add D-16 invariant tests
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt` — newAgent() skip-guard helper + 5 SKIP-only @Test anchor stubs (anchor1Title_to_Gameplay … anchor5LevelSwitch); Plans 12-19..12-23 fill bodies
- `gbkt-examples/platformer-template/res/.gitkeep` — placeholder so ProcessAssetsTask assetDirectory input contract is satisfied; Plan 12-04 populates with reference PNGs
- `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/TilemapCollisionEmissionTest.kt` — D-16 inv 2 scaffold (`is_tile_solid` SWITCH_ROM shape); Plan 12-09 wires real test
- `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/HorizontalScrollEmissionTest.kt` — D-16 inv 3 scaffold (`_camera_update` column-update shape); Plan 12-12 wires real test
- `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/JumpHoldEmissionTest.kt` — D-14 invariant scaffold (gravity-suppression-while-held); Plan 12-14 wires real test
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/MultiTilesetAllocationTest.kt` — D-15 invariant scaffold (multi-tileset + multi-zone bank allocation gap-or-pass verdict); Plan 12-15 wires real test

### Modified
- `settings.gradle.kts` — appended `include("gbkt-examples:platformer-template")` directly after the existing `include("gbkt-examples:banks")` line (line 63)

## Decisions Made

1. **Skipped `ramBanks` config in gbkt { } block** — Plan instruction explicitly notes: "Wave-0: do NOT set `ramBanks`; this game has no SRAM per CONTEXT D-claude-3". Matched. Cartridge config (`cartridge`/`romBanks`/`gbcTarget`) intentionally NOT set in Wave-0 either — Plan 12-16+ owns the full `config { }` block.
2. **Kotlin package uses underscore (`platformer_template`) while Gradle subproject uses hyphen (`platformer-template`)** — matches plan frontmatter `files_modified` and Kotlin naming conventions; the Gradle subproject path (`gbkt-examples:platformer-template`) maps to the colon-syntax subproject coordinate.
3. **5 anchor `@Test` stubs use `Assumptions.assumeTrue(ROM_FILE.exists())` skip-guard** rather than `@Disabled` — keeps the test suite GREEN locally and in CI before Wave-3 builds the ROM, and produces the "PENDING anchor N" skip message in JUnit output that surfaces the wired-vs-pending state in test reports.
4. **Single-line `@Test fun anchorN_xxx()` formatting** — chosen over canonical multi-line `@Test` + `fun` to satisfy the plan's `grep -c '@Test fun anchor' returns 5` acceptance criterion exactly.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Created `res/.gitkeep` to unblock `compileTestKotlin`**
- **Found during:** Task 3 (running `:gbkt-examples:platformer-template:compileTestKotlin` to verify acceptance)
- **Issue:** `compileTestKotlin` runs through `processAssets` task which requires `assetDirectory` (`gbkt-examples/platformer-template/res/`) to exist. Build failed with: *"property 'assetDirectory' specifies directory '…/gbkt-examples/platformer-template/res' which doesn't exist"*. The plan's `<files>` list for Task 3 did not mention `res/` — implicit blocker.
- **Fix:** Created `gbkt-examples/platformer-template/res/.gitkeep` with a comment explaining Plan 12-04 will populate the directory with reference PNG assets per D-claude-7. The placeholder file satisfies Gradle's input contract without committing any asset bytes.
- **Files modified:** `gbkt-examples/platformer-template/res/.gitkeep` (new)
- **Verification:** `:gbkt-examples:platformer-template:compileTestKotlin` GREEN; `:gbkt-examples:platformer-template:test` GREEN
- **Committed in:** `207aca71` (part of Task 3 commit — documented in commit body and SUMMARY)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary to satisfy Gradle's `assetDirectory` input contract; the gitkeep placeholder will be supplanted by real assets in Plan 12-04 (asset import). No scope creep — this is purely a Wave-0 plumbing fix to keep `compileTestKotlin` GREEN.

## Issues Encountered

None — plan executed as written (modulo the one Rule 3 deviation above).

## User Setup Required

None — Wave-0 scaffold is pure code/config.

## Next Phase Readiness

Wave-0 of Phase 12 is complete for the `gbkt-examples/platformer-template/` subproject:

- All 6 example-side files exist; `compileTestKotlin` GREEN; `:test` GREEN with 5 anchor skips
- All 4 module-side emission-test scaffolds compile and pass placeholder
- settings.gradle.kts lists `platformer-template` exactly once
- `:gbkt-examples:simple-physics:compileKotlin` still GREEN — no regression to sibling examples

Open for downstream plans:
- Plan 12-01 (UAT lock, if not yet executed in this wave)
- Plan 12-02 (reference ROM evidence — `evidence/reference/BUILD.md`)
- Plan 12-04 (asset import — populates `res/` with reference PNGs)
- Plans 12-05..18 (DSL substrate authoring on the empty PlatformerTemplate.kt stub)
- Plans 12-09 / 12-09b / 12-12 / 12-14 / 12-15 (fill emission-test bodies)
- Plans 12-19..23 (fill UAT anchor @Test bodies)

No blockers.

## Self-Check: PASSED

- `gbkt-examples/platformer-template/build.gradle.kts` — FOUND
- `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt` — FOUND
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateIRTest.kt` — FOUND
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateEmissionTest.kt` — FOUND
- `gbkt-examples/platformer-template/src/test/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplateUatTest.kt` — FOUND
- `gbkt-examples/platformer-template/res/.gitkeep` — FOUND
- `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/TilemapCollisionEmissionTest.kt` — FOUND
- `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/HorizontalScrollEmissionTest.kt` — FOUND
- `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/JumpHoldEmissionTest.kt` — FOUND
- `gbkt-backend-gbdk/src/test/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/MultiTilesetAllocationTest.kt` — FOUND
- Commits `7ac6ecd2`, `9053b549`, `207aca71`, `785d0b85` — FOUND in `git log`

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Completed: 2026-05-21*
