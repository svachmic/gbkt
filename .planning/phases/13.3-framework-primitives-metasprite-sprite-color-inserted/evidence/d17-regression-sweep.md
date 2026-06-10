# D-17 ROM Regression Sweep — Phase 13.3 Terminal Gate

Date: 2026-06-03
Branch: feat/d_and_d_gaps
Invocation: single chained `./gradlew clean :…:buildRom …` (per `feedback_no_parallel_gradle_clean` — no fan-out parallel cleans; Gradle parallelizes per-task internally).

GBDK toolchain: `/Users/michalsvacha/gbdk` (lcc present).

## Sweep command

```
./gradlew clean \
  :gbkt-examples:pong:buildRom \
  :gbkt-examples:breakout:buildRom \
  :gbkt-examples:simple-physics:buildRom \
  :gbkt-examples:metasprites:buildRom \
  :gbkt-examples:metasprites-stress:buildRom \
  :gbkt-examples:banks:buildRom \
  :gbkt-examples:racer:buildRom
```

Aggregate result: **BUILD SUCCESSFUL** (sweep exit 0).

## Per-target results

| # | Target | EXIT | ROM size | Notes |
|---|--------|------|----------|-------|
| 1 | pong | 0 (PASS\*) | 32768 B (32 KB) | PASS\* — pong toolchain non-determinism is a pre-existing sdcc/lcc hash quirk (`project_pong_toolchain_nondeterminism`); build is GREEN, not re-investigated. |
| 2 | breakout | 0 | 32768 B (32 KB) | |
| 3 | simple-physics | 0 | 32768 B (32 KB) | |
| 4 | metasprites | 0 | 32768 B (32 KB) | Exercises Path A (D-03) shape change — asset-driven elephant builds clean. |
| 5 | metasprites-stress | 0 | 32768 B (32 KB) | Exercises Path A (D-03) — elephant + tiger asset-driven, builds clean. |
| 6 | banks | 0 | 65536 B (64 KB) | |
| 7 | racer | 0 | 65536 B (64 KB) | MBC5 banking auto-upgrade (expected). |

All 7 `:buildRom` targets EXIT 0. The deliberate D-03 generated-C shape change (Path A
metasprite emission) did NOT regress any ROM build — JVM tests cannot see staleness in
`build/gbkt/generated/`, so this clean ROM-level sweep is the binding D-17 gate
(CLAUDE.md "ROM-build smoke test for codegen phases").

## JVM test suite status

The phase-touched modules were confirmed GREEN during wave integration gates:

| Module | Status | Source |
|--------|--------|--------|
| gbkt-lang | 305/305 GREEN | wave 5 (plan 13.3-11) + orchestrator re-run |
| gbkt-backend-gbdk | 1074/1074 GREEN | plan 13.3-11 + orchestrator targeted contract tests |
| gbkt-gradle-plugin | ConvertSprites frame-count contract GREEN | wave 3 orchestrator gate |
| gbkt-examples:metasprites | byte-identity baseline + IR GREEN | wave 4/5 orchestrator gates |
| gbkt-examples:metasprites-stress | byte-identity baselines GREEN | wave 5 orchestrator gate |

Note (scope): a root-level `./gradlew test` is NOT fully green due to PRE-EXISTING,
out-of-scope baseline debt documented in project memory and unrelated to Phase 13.3:
`LabyrinthOfTheDragon-port` SaveSystem/Cartridge compile debt (fails identically at the
phase base commit b26b03cc), `project_integration_test_baseline_red` (IntegrationTest
SceneIR.<init> signature mismatch from Phase 11.1-04), and `project_rpg_char_codegen_debt`
(dungeon/explorer `:buildRom`). These are tracked separately and are not part of the
13.3 acceptance gate, which is the 7-target `:buildRom` sweep above.

## Visual evidence note (Task 2)

The captured `metasprites-elephant-postmigration.png` shows a full-screen checkerboard
with the elephant sprite composited on top. The checkerboard is INTENTIONAL: the `play`
scene `enter` calls `bgFillCheckerboard()` (see `gbkt-examples/metasprites/CLAUDE.md`),
so the background layer is a checkerboard by design and the elephant is the sprite overlay.

OAM confirmation at frames 180 and 300 (emulator_step observation): 29 sprite tiles
correctly positioned in the elephant grid — screenX 88–136, screenY 80–112, sequential
tileIndex 0–28 — matching the 64×48 asset-driven frame composed via the Path A
`move_metasprite(elephant_metasprites[idx], …)` reference. rot=0 → gray sub-palette.
This is consistent with the pre-migration metasprite shape (D-03 is internal-only; the
byte-identity baselines are GREEN, so the emitted C is identical to the re-pinned oracle).

## Gap closure (plan 13.3-14) — set_sprite_data regression fixed

The first 13.3-13 capture showed checkerboard ONLY — the elephant was invisible. Root cause:
Path A asset-driven metasprites emitted no `set_sprite_data()` (tileCountForMetasprite returned
null for tile-less frames → loader `?: continue` skipped them). Fixed in plan 13.3-14:
- ConvertSpritesTask emits `#define sprites_<id>_tiles_count <N>` (N = png2asset arraylen/16; elephant = 44u).
- GBDKPipelineV2.buildAllSpriteDataLoadStatements emits, for asset-driven metasprites:
  `set_sprite_data(0u, sprites_elephant_tiles_count, sprites_elephant_tiles);`
  metasprites-stress chains correctly: player (2u) → elephant (2u) → tiger (2u + sprites_elephant_tiles_count).
- RED→GREEN regression test MetaspriteAssetTileLoadEmissionTest guards it so the byte-identity
  baseline can't mask a dropped load again.

Re-captured screenshot (`metasprites-elephant-postmigration.png`) now shows the elephant
rendering on the checkerboard (pink sub-palette at frame 180). The regression is closed.
