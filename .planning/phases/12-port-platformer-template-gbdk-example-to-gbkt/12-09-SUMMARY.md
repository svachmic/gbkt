---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 09
subsystem: genre-platformer-codegen-tests
tags: [platformer, tilemap-collision, jvm-tier, emission-invariant, awk-brace-walk, switch_rom, d16-invariant-2]

# Dependency graph
requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt
    provides: "buildIsTileSolidHelperIfNeeded(gameIR) HOME-bank NONBANKED helper (12-08); 5 HOME-bank globals + game.h prototype (12-08); gameUsesTilemapCollision gate predicate (12-08)"
provides:
  - "JVM-tier emission invariant locking is_tile_solid SWITCH_ROM save/restore wrapper shape"
  - "Negative gate-verification test locking byte-identical regression for non-tilemap games"
  - "Reusable extractFunctionBody helper (Kotlin awk-brace-walk equivalent) for future per-function emission tests in this module"
  - "Evidence artifact at evidence/tier1-shape/is_tile_solid.c capturing the helper body for human review"
affects:
  - 12-11  # 5-point AABB probe in PlatformerVisitor calls is_tile_solid() — this test catches drift in the signature/contract
  - 12-12  # tilemap-collision branch in physics update uses is_tile_solid() — this test guards against signature regressions
  - 12-13  # jump-hold integrates with the same physics branch

# Tech tracking
tech-stack:
  added: []  # No new libraries; purely JVM-tier emission test
  patterns:
    - "Per-function awk brace-walk (Kotlin port): extractFunctionBody(cSource, signaturePrefix) anchors at column 0 (line.startsWith(prefix)) and walks `{` / `}` depth back to 0 — Kotlin counterpart of the awk pattern in VALIDATION.md row 2"
    - "Evidence-before-assert: persist extracted helper body to evidence/tier1-shape/ BEFORE assertions fire so RED runs still produce reviewable on-disk artifacts (inherited from BanksEmissionTest §INV-1..4)"
    - "Gate-verification negative test: assert SWITCH_ROM / globals / prototype all ABSENT when the feature gate is OFF, locking lockstep emission and byte-identical regression for non-opt-in games"
    - "Direct GameIR construction (not DSL): mirrors PlatformerCodegenTest's pattern of building a minimal GameIR + GenericSystem(`platformer_physics`, config = mapOf(`physicsConfig` to PlatformerPhysicsConfig(solidThreshold = N))) to drive GBDKPipelineV2 deterministically"

key-files:
  created:
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/is_tile_solid.c"
  modified:
    - "gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/TilemapCollisionEmissionTest.kt"

key-decisions:
  - "Anchor the brace-walk via line.startsWith(prefix) instead of line.contains(prefix). This is the literal counterpart of awk's `/^prefix/` anchor — it ensures occurrences inside string literals, comments, or argument lists of unrelated functions cannot false-match the signature. BanksEmissionTest used `line.contains(\"void $name(\")` which works because `void` is rare at non-column-0 positions; for `UINT8 is_tile_solid` the stricter anchor matches the awk pattern in VALIDATION.md verbatim."
  - "Count SWITCH_ROM via `body.split(\"SWITCH_ROM\").size - 1` and assert `>= 2` (not `== 2`) — the plan's must-haves require AT LEAST 2 (entry + exit). A future codegen evolution that adds defensive intermediate switches would still satisfy the contract; an exact `== 2` would over-constrain. The separate symmetry checks (`SWITCH_ROM(_current_area_bank)` AND `SWITCH_ROM(_previous_bank)`) lock the canonical pair regardless."
  - "Two tests in one file (positive + negative) rather than 4-5 micro-tests. The positive test packs 5 assertions because they are logically a single invariant (a correctly-shaped is_tile_solid helper); splitting them would obscure the contract and slow the suite. The negative test stays focused on the gate's lockstep emission (helper + globals + prototype all absent together)."
  - "Direct GameIR construction over DSL invocation. The DSL path requires asset-pipeline integration and Gradle-task wiring; the IR path uses the same surface that PlatformerCodegenTest already exercises and produces an in-memory pipeline output map suitable for substring extraction. Reduces test brittleness against DSL evolution."
  - "Evidence path resolves via `user.dir`-relative `../` ascent (not absolute path). The gbkt-genre-platformer module sits one level under the repo/worktree root, so a single `..` reaches the worktree root regardless of whether the test runs from the main checkout or a Claude Code worktree. Absolute paths constructed at planning time would silently route evidence out of the active worktree (#3099 worktree path safety)."

patterns-established:
  - "Kotlin port of the awk pattern `awk '/^prefix/{p=1;d=0} p{d+=gsub(/{/,\"\"); d-=gsub(/}/,\"\"); if(d<0)exit} p'` as extractFunctionBody(cSource, signaturePrefix) using line.startsWith + brace counting — reusable across genre-codegen modules for per-function emission invariants"
  - "Two-test minimum for emission invariants on gated features: a positive (shape lock) plus a negative (gate-off byte-identical regression) — together they pin both ends of the opt-in contract"

requirements-completed: [D-16, D-overfitting-2]

# Metrics
duration: 6min
completed: 2026-05-21
---

# Phase 12 Plan 09: Lock is_tile_solid Helper Shape via Per-Function Awk Brace-Walk Summary

**Replaces the Wave-0 placeholder TilemapCollisionEmissionTest with the real D-16 invariant 2 emission test: extractFunctionBody helper + positive SWITCH_ROM wrapper-shape lock + negative gate-verification test, with the extracted helper body written to evidence/tier1-shape/ for human review.**

## Performance

- **Duration:** 6 min
- **Tasks:** 1
- **Files modified:** 1 (TilemapCollisionEmissionTest.kt)
- **Files created:** 1 (evidence/tier1-shape/is_tile_solid.c)

## Accomplishments

- Replaced the placeholder `@Test fun placeholder()` body with two real `@Test` methods anchored to D-16 invariant 2.
- Added the `extractFunctionBody(cSource, functionSignaturePrefix)` helper as the Kotlin counterpart of the awk brace-walk pattern bound in VALIDATION.md §Per-Anchor Verification Map row 2.
- Positive test (`is_tile_solid helper emits SWITCH_ROM save and restore wrapper when solidThreshold set`) builds a minimal GameIR with `GenericSystem("platformer_physics", physicsConfig = PlatformerPhysicsConfig(solidThreshold = 17))`, drives `GBDKPipelineV2`, extracts the helper body via brace-walk, and asserts the full SWITCH_ROM wrapper shape:
  - `^UINT8 is_tile_solid` matches at column 0 of main.c
  - SWITCH_ROM count >= 2 (entry + exit per VALIDATION.md)
  - Entry `SWITCH_ROM(_current_area_bank)` literal present
  - Exit `SWITCH_ROM(_previous_bank)` literal present
  - `_current_level_non_solid_tile_count` referenced within scope
  - `_current_level_map[` array lookup within scope
- Negative test (`is_tile_solid is NOT emitted when solidThreshold is unset (gate off)`) asserts the gate's byte-identical-regression invariant by building the same minimal GameIR with `solidThreshold = null` and asserting:
  - `is_tile_solid` absent from main.c
  - `_current_area_bank` absent from main.c (HOME globals gated in lockstep)
  - `is_tile_solid` absent from game.h (prototype gated in lockstep)
- Evidence-before-assert: the extracted helper body is written to `evidence/tier1-shape/is_tile_solid.c` BEFORE the assertions fire so a future RED run still produces a reviewable artifact on disk.
- `:gbkt-genre-platformer:test --quiet` exits 0 (full module suite GREEN; new test + 10 pre-existing PlatformerCodegenTest cases all pass).

## Task Commits

Each task was committed atomically:

1. **Task 1: Replace TilemapCollisionEmissionTest placeholder with positive + negative invariant tests** — `810be4a8` (test)

## Files Created/Modified

- `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/TilemapCollisionEmissionTest.kt` — replaced placeholder body with 2 @Test methods + `extractFunctionBody` helper + companion EVIDENCE_DIR.
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/is_tile_solid.c` (NEW) — verbatim helper body captured at test runtime; locks the on-disk shape contract for human review.

## Decisions Made

- **Column-0 anchor (`line.startsWith(prefix)`) instead of `line.contains(prefix)`:** This is the literal counterpart of awk's `/^prefix/` anchor. The plan's contract (Plan 12-08 SUMMARY §"Function declaration starts with `UINT8 is_tile_solid` at column 0 of main.c") REQUIRES the signature at column 0; a permissive `contains` would silently false-match if codegen ever placed the signature inside a comment, raw section header, or nested function arg list. The stricter anchor matches VALIDATION.md row 2's awk pattern verbatim.

- **`SWITCH_ROM` count `>= 2` (not `== 2`):** The plan's must-have is "at least 2" (entry + exit). A future codegen evolution that adds defensive intermediate switches (e.g. for nested tilemap probes) should still satisfy the contract. The separate entry/exit symmetry checks (`SWITCH_ROM(_current_area_bank)` AND `SWITCH_ROM(_previous_bank)`) pin the canonical pair regardless of total count.

- **Direct GameIR construction over DSL:** Mirrors PlatformerCodegenTest's pattern. The DSL path (`game { platformerPhysics { ... }; zone { ... } }`) requires asset-pipeline integration (`asset("res/tileset.png")` files at known paths) and Gradle-task wiring (`convertZoneTilesets` produces `_zone_*_tilemap.c`). The IR path bypasses both — Banks-style — and exercises exactly the same `GBDKPipelineV2.gameUsesTilemapCollision` predicate path A. Reduces brittleness against DSL evolution and keeps the test focused on the emission shape contract.

- **Two-test minimum (positive + negative) rather than a 5-test micro-fan-out:** The positive test packs 5 assertions because together they describe a single invariant (a correctly-shaped is_tile_solid helper). The negative test stays focused on the gate's lockstep emission (helper + globals + prototype all absent together). Each test reads as one cohesive contract; splitting either would obscure intent without adding coverage.

- **Evidence path via `user.dir`-relative `../`:** The gbkt-genre-platformer module sits exactly one directory below the repo/worktree root, so a single `..` reaches the root regardless of whether the test runs in the main checkout or a Claude Code worktree. Absolute paths constructed at planning time would silently route evidence outside the active worktree (worktree path safety #3099). The same pattern is used by BanksEmissionTest with `../../` because banks lives at depth 2.

## Deviations from Plan

None — plan executed exactly as written. All acceptance criteria met:

- ✅ File contains `extractFunctionBody` private helper.
- ✅ File contains 2 `@Test` methods (positive + negative).
- ✅ `:gbkt-genre-platformer:test --tests "TilemapCollisionEmissionTest"` exits 0.
- ✅ Positive test extracts the is_tile_solid body, asserts SWITCH_ROM occurs ≥ 2 times, asserts `_current_area_bank` AND `_current_level_non_solid_tile_count` present.
- ✅ Negative test asserts is_tile_solid does NOT appear when solidThreshold unset.
- ✅ Evidence written to `evidence/tier1-shape/is_tile_solid.c` after test run.

A minor scope refinement worth noting: the plan suggested the negative test "build a minimal game WITHOUT solidThreshold (use only platformerPhysics { gravity(2); jumpForce(8); terminalVelocity(12) }; no zone with override)". The implementation explicitly passes `solidThreshold = null` rather than omitting the field, which produces the same IR (PlatformerPhysicsConfig has `val solidThreshold: Int? = null` as the default). The negative test additionally asserts the absence of `is_tile_solid` from game.h (in addition to main.c) — this strengthens the lockstep-emission verification without changing the contract.

## Issues Encountered

None. The test followed the read_first references (BanksEmissionTest, PlatformerCodegenTest, Plan 12-08 SUMMARY) without surprises. The is_tile_solid helper emitted by 12-08 matches the contract documented in its SUMMARY verbatim (verified via the on-disk evidence/tier1-shape/is_tile_solid.c capture).

## User Setup Required

None — pure JVM-tier test; no external service or hardware needed.

## Threat Mitigations

**T-12-09-01 (Test integrity — false GREEN via file-level grep):** Mitigated. Per CLAUDE.md §"Scope-level grep gates corollary" and the project memory `feedback_visual_evidence_for_visual_truths`, a file-level `mainC.contains("SWITCH_ROM")` would false-positive on `_bkg_tiles_load_banked` (Plan 07.4-30) which lives in the same `main.c`. The brace-walk extractFunctionBody confines the substring checks to the is_tile_solid body only, so a regression that removed SWITCH_ROM from is_tile_solid while keeping it in `_bkg_tiles_load_banked` would correctly fail RED.

**T-12-09-02 (Gate-bypass — accidental unconditional emission):** Mitigated. The negative test fires on the same minimal GameIR with `solidThreshold = null` and asserts ZERO references to `is_tile_solid` / `_current_area_bank` in main.c AND ZERO prototype in game.h. A regression that dropped the `gameUsesTilemapCollision(gameIR)` guard would emit the helper unconditionally and break the byte-identical-regression invariant for the 7 framework-validated example games — this test catches that drift at JVM-tier before buildRom touches any disk.

## Next Phase Readiness

**Ready for Plan 12-11 (5-point AABB probe in PlatformerVisitor):** Plan 12-11 will emit call sites like `is_tile_solid(actor_x + hitbox_left, actor_y + hitbox_top)` inside the platformer physics update. This test guarantees the helper signature (`UINT8 is_tile_solid(UINT16, UINT16)`) and the SWITCH_ROM wrapper remain stable — any regression that changes the signature or removes the wrapper would fail RED here before reaching the visitor.

**Ready for Plan 12-12 / 12-13 (tilemap-physics branch + jump-hold):** Same guarantee applies. The helper's contract is locked at JVM-tier so downstream visitors can call it freely without worrying about signature drift.

**Plan 12-10 (Wave 5 sibling: D-A3 sentinel for solidThreshold opaque-config wiring) is independent of this plan** — they exercise distinct invariants (this one locks the emitted C shape; 12-10 locks the codegen-config plumbing). Both must be GREEN before the verifier runs the buildRom smoke test at phase close (D-21).

## Self-Check: PASSED

- `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/TilemapCollisionEmissionTest.kt` exists, contains `extractFunctionBody`, `SWITCH_ROM`, `_current_level_non_solid_tile_count`, and 2 `@Test` methods.
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/is_tile_solid.c` exists (533 bytes; helper body captured verbatim).
- Commit `810be4a8` exists in git log (`test(12-09): lock is_tile_solid SWITCH_ROM wrapper shape with per-function awk brace-walk (D-16 invariant 2)`).
- `./gradlew :gbkt-genre-platformer:test --quiet` exits 0 (full module suite GREEN, no regressions in pre-existing PlatformerCodegenTest).

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Completed: 2026-05-21*
