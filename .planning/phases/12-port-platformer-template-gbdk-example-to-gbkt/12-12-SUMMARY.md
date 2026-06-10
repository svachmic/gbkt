---
phase: 12-port-platformer-template-gbdk-example-to-gbkt
plan: 12
subsystem: genre-platformer-codegen-tests
tags: [platformer, column-scroll, emission-invariant, awk-brace-walk, jvm-tier, codegen]

# Dependency graph
requires:
  - phase: 12-port-platformer-template-gbdk-example-to-gbkt
    provides: "PlatformerVisitor.buildTilemapCameraUpdateFunction column-scroll body emitted into HOME-bank main.c (12-11); _bkg_set_level_submap_banked HOME-bank NONBANKED helper + 4 tilemap-camera WRAM globals (12-10); gameUsesTilemapCollision predicate (12-10); PlatformerPhysicsConfig.solidThreshold (12-06); PlatformerCameraConfig.scrollDirections + mode (12-06); extractFunctionBody helper pattern (12-09 — TilemapCollisionEmissionTest)"
provides:
  - "HorizontalScrollEmissionTest — JVM-tier emission invariant test class with 2 @Test methods (positive + negative gate) implementing D-16 invariant #3 per VALIDATION.md §Per-Anchor Verification Map row 3"
  - "Positive test (`platformer_camera_update emits column-scroll body when tilemap-collision on`) — extracts platformer_camera_update body via per-function awk brace-walk from main.c; asserts move_bkg(, ≥2 _bkg_set_level_submap_banked calls, the column-change guard `_map_pos_x != _old_map_pos_x` (or reverse), and the four tilemap-camera globals (_camera_x, _old_camera_x, _map_pos_x, _old_map_pos_x) all live inside the function scope"
  - "Negative test (`platformer_camera_update omits column-scroll body when tilemap-collision off`) — asserts the function still exists (abstract smooth-follow fall-through), its body does NOT call _bkg_set_level_submap_banked, and does NOT contain the column-change guard expression"
  - "Two evidence artifacts under .planning/phases/12-…/evidence/tier1-shape/: platformer_camera_update.c (positive shape) + platformer_camera_update_abstract.c (negative shape) — evidence-before-assert pattern, written BEFORE assertions fire so RED runs still produce reviewable artifacts"
affects:
  - 12-13  # Next D-16 invariant #4 (jump-hold gravity suppression) — same per-function brace-walk pattern; HorizontalScrollEmissionTest is the template
  - 12-15  # Codegen branch tests are READ-ONLY locks on Plan 12-11 emission shape — any regression in PlatformerVisitor.buildTilemapCameraUpdateFunction fails this test RED before runtime UAT catches the bug
  - 12-21..12-25  # UAT plans for anchor 3 (horizontal scroll) — this JVM tier proves the codegen prerequisite; UAT proves the runtime visual outcome

# Tech tracking
tech-stack:
  added: []  # No new libraries; pure JUnit5 + kotlin.test test class
  patterns:
    - "Per-function awk brace-walk extraction (Kotlin mirror): extractFunctionBody copied verbatim from Plan 12-09 (TilemapCollisionEmissionTest). Anchors function-prefix matching to column 0 of the line (the literal counterpart of awk's `/^prefix/`), then walks `{` / `}` depth until depth returns to 0. Returns the body blob so downstream `.contains()` checks fire ONLY against tokens inside the named function — satisfies CLAUDE.md §`Scope-level grep gates` corollary."
    - "Evidence-before-assert: write the extracted function body to evidence/tier1-shape/ BEFORE any assertion fires. RED runs still produce a reviewable artifact on disk — mirrors the pattern established in Plan 12-09 (is_tile_solid.c) and the gbkt-examples/banks INV-1..4 tests."
    - "Triple-condition gate testing: positive case wires BOTH a platformer_physics GenericSystem (solidThreshold non-null → flips gameUsesTilemapCollision predicate) AND a platformer_camera GenericSystem (PlatformerCameraConfig with default HORIZONTAL + SMOOTH_FOLLOW → matches the remaining two gate conditions). Negative case ONLY changes solidThreshold to null — the rest stays identical, so the test isolates the gameUsesTilemapCollision check as the sole discriminator."
    - "Worktree-safe EVIDENCE_DIR: resolved relative to `user.dir` + `..` so `:gbkt-genre-platformer:test` writes evidence under the active worktree root rather than the main checkout (#3099 worktree path safety, mirror of Plan 12-09's pattern)."

key-files:
  created: []
  modified:
    - "gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/HorizontalScrollEmissionTest.kt"
  evidence:
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/platformer_camera_update.c"
    - ".planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/platformer_camera_update_abstract.c"

key-decisions:
  - "Locked the column-scroll function shape via per-function brace-walk extraction of main.c (NOT bank1.c). VALIDATION.md row 3 names `bank1.c`, but Plan 12-11's `buildTilemapCameraUpdateFunction` uses the default `CFunction(isBanked = false)` choice — the function lives at HOME and lands in main.c. Confirmed empirically via the generated evidence artifact: `void platformer_camera_update(void)` at column 0 of main.c. The awk SHAPE (not the file name) is the binding D-16 contract per CLAUDE.md §`Scope-level grep gates` corollary — the test reads main.c with a comment explaining the divergence from VALIDATION.md row 3."
  - "Positive test wires the platformer_camera GenericSystem EXPLICITLY (with a PlatformerCameraConfig carrying default HORIZONTAL + SMOOTH_FOLLOW), not just the platformer_physics one. Reason: PlatformerVisitor.visitCamera only fires when a `platformer_camera` system is registered — without it, the visitor never enters buildCameraUpdateFunction and the column-scroll branch never gets a chance to emit, making the positive case a no-op false-pass. Plan 12-09 only needed the physics system because that test locks the HOME helper which is pipeline-level (gated on gameUsesTilemapCollision, no camera system required); Plan 12-12 locks the camera-fork output which DOES require the camera system."
  - "Accept both orderings of the column-change guard (`_map_pos_x != _old_map_pos_x` OR `_old_map_pos_x != _map_pos_x`). The current PlatformerVisitor emits the forward form (confirmed by evidence), but `!=` is commutative — locking only the forward form would over-fit. Plan 12-13 / 12-15 may refactor without semantic change; the disjunction guards against that without losing the contract."
  - "Negative test asserts the function STILL exists (abstract smooth-follow path) AND its body lacks the column-scroll markers. The plan body said `assert it does, but assert its body does NOT contain _bkg_set_level_submap_banked` — followed verbatim. Additional defence-in-depth: also assert the column-change guard absent from the abstract body, so a regression that flips the gate on for non-tilemap games would surface either marker."
  - "Wired ONE atomic commit for Task 1 rather than splitting into 'add test' + 'capture evidence' — the evidence files are produced as a side-effect of running the test, not as standalone artifacts. Splitting would create a transient state where the test exists but its evidence is missing. Atomic commit preserves the always-shippable invariant."

patterns-established:
  - "D-16 emission-invariant test family pattern: per-function brace-walk extraction + evidence-before-assert + worktree-safe EVIDENCE_DIR + dual positive/negative gate verification. Plan 12-09 established the pattern (D-16 #2); Plan 12-12 extends it (D-16 #3); Plan 12-13 will reuse it (D-16 #4 — jump-hold gravity suppression)."

requirements-completed: [D-13, D-16]

# Metrics
duration: 8min
completed: 2026-05-21
---

# Phase 12 Plan 12: Lock column-scroll codegen shape with JVM emission invariant Summary

**Replaces the Wave-0 placeholder `HorizontalScrollEmissionTest` with the real D-16 invariant #3 emission test — per-function `awk` brace-walk extraction of `platformer_camera_update` from `main.c` + in-scope grep for the column-scroll discriminators. Positive case proves the column-scroll branch emits when `gameUsesTilemapCollision == true AND HORIZONTAL AND SMOOTH_FOLLOW`; negative case proves the gate suppresses the branch and falls back to the abstract smooth-follow body. Both tests pass against the codegen from Plan 12-11.**

## Performance

- **Duration:** ~8 min
- **Tasks:** 1
- **Files modified:** 1 (test class) + 2 evidence artifacts created

## Accomplishments

### Task 1: Fill HorizontalScrollEmissionTest with positive + negative checks (D-13, D-16 #3)

- Replaced the Wave-0 stub class body (a single `placeholder()` @Test) with a real test class containing:
  - **`extractFunctionBody(cSource, functionSignaturePrefix)` helper** — copied verbatim from Plan 12-09's `TilemapCollisionEmissionTest.kt`. Anchors on a column-0 line prefix and walks brace depth to extract the function body, so substring checks fire ONLY against tokens inside the named function (CLAUDE.md §"Scope-level grep gates" corollary).
  - **`buildTilemapCameraGameIR(solidThreshold, scrollDirections, mode, id)` helper** — constructs a minimal `GameIR` carrying BOTH a `platformer_physics` `GenericSystem` (whose `physicsConfig.solidThreshold` controls the tilemap-collision gate) AND a `platformer_camera` `GenericSystem` (whose `cameraConfig` carries `mode=SMOOTH_FOLLOW`, `scrollDirections=HORIZONTAL` by default).
  - **`EVIDENCE_DIR` companion** — worktree-safe path resolution via `user.dir + ..` (mirror of Plan 12-09 pattern), pointing at `.planning/phases/12-…/evidence/tier1-shape/`.
- **Positive test** (`platformer_camera_update emits column-scroll body when tilemap-collision on`):
  - Builds the IR with `solidThreshold = 17` (flips the gate ON) + default `PlatformerCameraConfig` (HORIZONTAL + SMOOTH_FOLLOW).
  - Anchors on `Regex("^void platformer_camera_update", MULTILINE)` against `main.c` (the function lives at HOME bank — `CFunction(isBanked = false)` — so it lands in `main.c`, not `bank1.c`).
  - Extracts the function body via `extractFunctionBody`, writes it to `evidence/tier1-shape/platformer_camera_update.c` BEFORE any assertion.
  - Asserts: `move_bkg(` present, ≥2 `_bkg_set_level_submap_banked` calls (left-edge + right-edge), column-change guard `_map_pos_x != _old_map_pos_x` (or reverse ordering — `!=` is commutative), and all four tilemap-camera globals (`_camera_x`, `_old_camera_x`, `_map_pos_x`, `_old_map_pos_x`) inside the function scope.
- **Negative test** (`platformer_camera_update omits column-scroll body when tilemap-collision off`):
  - Builds the IR with `solidThreshold = null` (gate OFF) but otherwise identical (same camera config).
  - Asserts the function STILL exists (`^void platformer_camera_update` regex still matches — abstract smooth-follow fall-through emits the function regardless).
  - Extracts the abstract body, writes it to `evidence/tier1-shape/platformer_camera_update_abstract.c`.
  - Asserts `_bkg_set_level_submap_banked` ABSENT from the body (gate-off invariant).
  - Defence-in-depth: also asserts the column-change guard ABSENT from the body — catches a regression that accidentally flips the gate on but loses the helper call.

### Cross-cutting verification

- `:gbkt-genre-platformer:test --tests "HorizontalScrollEmissionTest" --quiet` → exit 0 (both tests GREEN)
- `:gbkt-genre-platformer:test --quiet` → exit 0 (full module suite GREEN — no regression in Plan 12-09 TilemapCollisionEmissionTest, Plan 12-10/11 emission tests, or any existing platformer codegen/DSL tests)
- Evidence artifacts inspected on disk:
  - `platformer_camera_update.c` (1106 bytes) — confirms the column-scroll body shape (move_bkg + 2 `_bkg_set_level_submap_banked` calls inside the `if (_map_pos_x != _old_map_pos_x)` guard + `_old_camera_x = _camera_x` latch).
  - `platformer_camera_update_abstract.c` (613 bytes) — confirms the abstract smooth-follow body lacks the column-scroll markers.

## Task Commits

1. **Task 1: Lock platformer_camera_update column-scroll shape with awk brace-walk** — `14bc8f90` (test)

## Files Created/Modified

- `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/HorizontalScrollEmissionTest.kt`
  - Replaced placeholder class body (Wave-0 stub with single empty `placeholder()` @Test) with a fully wired test class.
  - Added imports: `GBDKPipelineV2`, `CartridgeConfig`, `GameIR`, `GenericSystem`, `SceneIR`, `CameraScrollMode`, `PlatformerCameraConfig`, `PlatformerPhysicsConfig`, `ScrollDirection`, `File`, `assertFalse`, `assertTrue`.
  - Added `companion object` with `EVIDENCE_DIR` resolved relative to `user.dir`.
  - Added `extractFunctionBody` private helper.
  - Added `buildTilemapCameraGameIR` private helper.
  - Added 2 @Test methods (positive + negative gate verification).
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/platformer_camera_update.c` — column-scroll positive-shape evidence.
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/platformer_camera_update_abstract.c` — abstract smooth-follow negative-shape evidence.

## Decisions Made

- **Read `main.c`, not `bank1.c`** despite VALIDATION.md §Per-Anchor Verification Map row 3 naming `bank1.c`. Plan 12-11's `buildTilemapCameraUpdateFunction` returns a `CFunction` without `isBanked = true`, which means the function lives at HOME and lands in `main.c`. Confirmed empirically via the generated evidence artifact. The awk SHAPE (per-function brace-walk + in-scope grep) is the binding D-16 contract per CLAUDE.md §"Scope-level grep gates" corollary — the file-name in VALIDATION.md was an early estimate; the SHAPE-level invariant survives the divergence. The test file carries a comment block explaining this for future readers.

- **Wired the `platformer_camera` GenericSystem EXPLICITLY** in `buildTilemapCameraGameIR`, in addition to the `platformer_physics` one. The `PlatformerVisitor.visitCamera` only fires when a `platformer_camera` system is registered; without it, `buildCameraUpdateFunction` never executes and the column-scroll branch never gets evaluated — making the positive case a no-op false-pass. (Plan 12-09's `TilemapCollisionEmissionTest.buildPlatformerGameIR` only needs the physics system because that test locks the pipeline-level `is_tile_solid` HOME helper, gated on `gameUsesTilemapCollision` directly — no camera system required.)

- **Accept both orderings of the column-change guard** (`_map_pos_x != _old_map_pos_x` OR `_old_map_pos_x != _map_pos_x`). The current PlatformerVisitor emits the forward form (verified by evidence), but `!=` is commutative — locking only the forward form would over-fit (Phase 11 D-overfitting-2 doctrine). Plan 12-13 / 12-15 may refactor the codegen without semantic change; the disjunction guards against that without losing the contract.

- **Negative test asserts the function still exists** AND its body lacks the column-scroll markers. The plan body called for exactly this shape (`assert it does, but assert its body does NOT contain _bkg_set_level_submap_banked`). Additional defence-in-depth: also assert the column-change guard absent from the abstract body — catches a regression that flips the gate on but loses the helper call, which the function-existence check alone would miss.

- **Single atomic commit for Task 1** rather than splitting "add test" + "capture evidence" into two commits. The evidence files are produced as a side-effect of running the test (the `EVIDENCE_DIR.mkdirs() → writeText(body)` sequence runs inside the test method itself), not as standalone artifacts. Splitting would create a transient state where the test exists but its evidence is missing on disk. Atomic commit preserves the always-shippable invariant per the GSD task-commit protocol.

## Deviations from Plan

### Auto-fixed Issues

**None.** The plan executed exactly as written. Two minor clarifications surfaced during execution but no rule-1/2/3 auto-fixes were needed:

1. **Plan said `bank1.c` (twice via VALIDATION.md row 3 + plan body)** but the function lives at HOME bank → `main.c`. The plan body actually leaves the file location open ("likely `bank1.c` — read whichever bank file the function lands in"), so this is not a contradiction; the test follows the empirical answer (main.c) and documents the divergence in a comment block.

2. **Plan body referenced `_old_map_pos_x` in the must-have grep predicate** ("Body must contain: `_map_pos_x != _old_map_pos_x` guard expression"). The frontmatter requirements (D-13, D-16) carried that intent; the test follows it. The disjunction with the reverse ordering is documented as an anti-overfitting decision, not a deviation.

### Plan-prose vs. emitted shape clarifications

None. The Plan 12-11 SUMMARY's "Ready for Plan 12-12" section accurately predicted the emission shape (move_bkg, `_bkg_set_level_submap_banked` ≥2, `_map_pos_x != _old_map_pos_x` guard); the test asserts each of these.

## Issues Encountered

None significant. Worth noting:

- The Wave-0 stub class body had a single empty `@Test fun placeholder()` method that needed replacing entirely. The Write tool overwrites the file, so no merge work was required.
- The full module test suite was run after the targeted test to confirm no regression in Plan 12-09 (TilemapCollisionEmissionTest, which uses the same `extractFunctionBody` helper) — both Plan 12-09 and Plan 12-12 tests are green, confirming the helper is stable across both consumers.

## User Setup Required

None — no external service configuration required.

## Threat Mitigations

**T-12-12-01 (Tampering — codegen drift dropping the column-scroll branch):** Mitigated by the positive @Test. A regression that drops the `gameUsesTilemapCollision &&  HORIZONTAL && SMOOTH_FOLLOW` triple-condition gate (or routes only one of the three conditions correctly) would either (a) suppress the column-scroll branch in the positive case → `_bkg_set_level_submap_banked` count < 2, failing RED, OR (b) emit the branch unconditionally in the negative case → `_bkg_set_level_submap_banked` present in the abstract body, failing RED. The two tests together pinpoint which side of the gate broke.

**T-12-12-02 (Tampering — silent column-scroll guard regression):** Mitigated by the in-scope `_map_pos_x != _old_map_pos_x` (or reverse) assertion. A regression that flattens the `if (column changed)` guard (e.g. "always redraw, defer 8-frame skip to runtime") would visually flicker on every frame (or skip redraws entirely). The test catches the codegen change BEFORE the visible flicker reaches UAT.

**T-12-12-03 (Information disclosure — function lives in wrong bank):** Mitigated by anchoring the test on `main.c`. If a future refactor moves `platformer_camera_update` from HOME to bank1 (via `CFunction(isBanked = true)`), the test fails RED with "main.c head: …" pointing at the missing function. The fix path is well-defined: either revert the bank move OR update the test to read whichever bank file the function landed in.

## Next Phase Readiness

**Ready for Plan 12-13 (Wave 7 — JumpHoldEmissionTest, D-16 invariant #4):** The same per-function awk brace-walk + in-scope grep + evidence-before-assert + positive/negative gate pattern transfers verbatim. The target function is `platformer_physics_update` (Plan 12-11 Task 1 emitted it) and the discriminator is the gravity-suppression `if (_jump_increase_timer > 0 && (button_held(J_A) || button_held(J_UP)))` block. The `extractFunctionBody` helper can be promoted to a shared utility once a third consumer (Plan 12-13) materialises — see RESEARCH §"Per-anchor verification map awk" for the canonical 5-invariant family.

**Ready for Plan 12-15 (Wave 8 — codegen integration regression risk):** This emission invariant test will run as part of `:gbkt-genre-platformer:test` in every wave going forward. Any Plan 12-15+ change to `PlatformerVisitor.buildTilemapCameraUpdateFunction` (D-claude-6 goalZone swap, D-claude-7 multi-axis camera, etc.) that alters the column-scroll shape will fail RED here before the UAT phase catches it at runtime.

**Existing examples remain byte-identical:** Verified by the full module suite passing — Plan 12-11's gate-off byte-identical regression for non-tilemap examples (Pong, Breakout, banks, etc.) is still locked by the existing `PlatformerCodegenTest` suite, and Plan 12-12 added zero new emissions to those games.

## Self-Check: PASSED

- `gbkt-genre-platformer/src/test/kotlin/io/github/gbkt/genre/platformer/codegen/HorizontalScrollEmissionTest.kt` exists; contains `extractFunctionBody` helper, `buildTilemapCameraGameIR` helper, `EVIDENCE_DIR` companion, and 2 `@Test fun` methods (positive + negative).
- File contains literal `_bkg_set_level_submap_banked` (verified by the test passing — the positive assertion fires against this token inside the extracted body).
- File contains literal `_old_map_pos_x` (verified by both the positive guard assertion AND the negative defence-in-depth assertion).
- Commit `14bc8f90` exists in git log on `worktree-agent-a5ed49d4d755fb804`.
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/platformer_camera_update.c` exists (1106 bytes) — column-scroll positive-shape evidence.
- `.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape/platformer_camera_update_abstract.c` exists (613 bytes) — abstract negative-shape evidence.
- `:gbkt-genre-platformer:test --tests "HorizontalScrollEmissionTest" --quiet` → exit 0.
- `:gbkt-genre-platformer:test --quiet` → exit 0 (full module suite GREEN — no regression).

---
*Phase: 12-port-platformer-template-gbdk-example-to-gbkt*
*Completed: 2026-05-21*
