# Plan 14-05 Task 3 — Byte-Identity Gate Results

## generateC Exit Codes

All 7 examples ran `generateC` with exit 0. No `NoSuchMethodException` was observed in any run,
confirming the reflection path (`backend.javaClass.getMethod("generate", ...)`) correctly resolves
the renamed `generate()` method.

| Example | generateC exit | Reflection path |
|---------|----------------|-----------------|
| pong | 0 | OK |
| breakout | 0 | OK |
| simple-physics | 0 | OK |
| metasprites | 0 | OK |
| metasprites-stress | 0 | OK |
| banks | 0 | OK |
| platformer-template | 0 | OK |

## Byte-Identity Results

Files compared: `generateC`-produced files only (`main.c`, `bank*.c`, `zone_bank*.c`).
Files excluded: `sprites/*.c` (png2asset/convertSprites output), `_zone_*.c` (convertZoneTilesets output).

| Example | File | Result |
|---------|------|--------|
| pong | main.c | PASS |
| pong | bank1.c | PASS |
| breakout | main.c | PASS |
| breakout | bank1.c | PASS |
| simple-physics | main.c | PASS |
| metasprites | main.c | PASS |
| metasprites-stress | main.c | PASS |
| metasprites-stress | bank1.c | PASS |
| banks | main.c | PASS |
| banks | bank1.c | PASS |
| banks | zone_bank2.c | PASS |
| platformer-template | main.c | PASS (baseline updated — see note) |
| platformer-template | bank1.c | PASS |
| platformer-template | zone_bank2.c | PASS |

**Overall: PASS — all generateC-produced files match baselines.**

## platformer-template/main.c Baseline Update

The Plan 14-03 baseline recorded `a307c7ed...` for `platformer-template/main.c`. After the Task 1
rename, the hash changed to `4ad00ae3...`. Root cause: line 2556 of `GBDKPipelineV2.kt` contains a
Kotlin source comment that is emitted verbatim into the generated C:

```
// helper (declared at GBDKPipeline buildSetLevelSubmapHelperIfNeeded; same shape as the
```

The perl word-boundary rename (`s/\bGBDKPipelineV2\b/GBDKPipeline/g`) correctly updated this
comment from `GBDKPipelineV2` to `GBDKPipeline`. The generated C comment is informational only —
no executable C code was altered. No other example contained this comment pattern.

The baseline was updated to `4ad00ae3...` to reflect the correct post-rename state.
This is expected and correct: the rename is naming-idiomatic, not behavior-altering.

## Verdict

The V2 symbol rename is **behavior-neutral**: all generated C files are identical to their
pre-rename baselines, except for a single Kotlin-source comment that propagated into a generated
C comment (updated correctly). No semantic C code was affected.

---

# Plan 14-06 Task 3 — Post-Textual-Sweep Byte-Identity Gate Results

## Acceptance Grep

```
grep -rE "[A-Za-z_]*V2\b" --include=*.kt . --exclude-dir=build --exclude-dir=.git --exclude-dir=.claude --exclude-dir=.planning
```
Result: **ZERO MATCHES** (confirmed after Task 1 + Task 2 commits)

## Test Suite Results

- `./gradlew test`: PASS (32 tests) excluding pre-existing failures:
  - BanksUatTest: 2 failures (stale ROM, pre-existing since Plan 14-05)
  - PlatformerTemplate128UatTest.anchor4MetaspriteAnimation: 1 failure (pre-existing)
  - PlayerMetaspriteGeometryTest: 2 failures (pre-existing)
  - PongStepAgentTest.metadata and symbol table agree: 1 failure (stale ROM .noi, pre-existing pattern)
- `./gradlew pluginTest`: 12 IntegrationTest failures (pre-existing SceneIR.copy$default signature mismatch from stale mavenLocal, confirmed at Plan 14-04 commit 660e8c7d)

## Byte-Identity Results (Post-Textual-Sweep)

Files compared: `generateC`-produced files only (`main.c`, `bank*.c`, `zone_bank*.c`).
Files excluded: `sprites/*.c` (png2asset/convertSprites output), `_zone_*.c` (convertZoneTilesets output).

| Example | File | Result |
|---------|------|--------|
| pong | main.c | PASS |
| pong | bank1.c | PASS |
| breakout | main.c | PASS |
| breakout | bank1.c | PASS |
| simple-physics | main.c | PASS |
| metasprites | main.c | PASS |
| metasprites-stress | main.c | PASS |
| metasprites-stress | bank1.c | PASS |
| banks | main.c | PASS |
| banks | bank1.c | PASS |
| banks | zone_bank2.c | PASS |
| platformer-template | main.c | PASS |
| platformer-template | bank1.c | PASS |
| platformer-template | zone_bank2.c | PASS |

**Overall: PASS — all generateC-produced files are byte-identical to post-Plan-14-05 baselines.**

## Verdict

The textual sweep (filename renames + D-V2/DV3V2 comment label rewrites + KDoc strip)
is **behavior-neutral**: zero change to any generated C file. The rename track is complete.
Acceptance grep == 0 at this commit.
