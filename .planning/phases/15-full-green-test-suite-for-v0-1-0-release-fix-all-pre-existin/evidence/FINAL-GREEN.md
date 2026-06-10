# FINAL-GREEN — Phase 15 release-gate proof

**Run date:** 2026-06-09
**Branch:** `feat/d_and_d_gaps` · **Pre-phase base:** `771850c9`

## Canonical suite commands — both GREEN

| Command | Result | Detail |
|---------|--------|--------|
| `./gradlew test --continue` | **BUILD SUCCESSFUL** | 0 failing tests across all library/genre/example modules |
| `./gradlew pluginTest` | **BUILD SUCCESSFUL in 26s** | gradle-plugin `IntegrationTest` `tests="19" skipped="0" failures="0" errors="0"` |

Every test in `FRESH-RUN-INVENTORY.md` (18 genuinely-red) is now green:

| Class | Was | Now | How |
|-------|-----|-----|-----|
| `IntegrationTest` (pluginTest) | 12 | **0** | republish `:gbkt-analysis` + `cacheChangingModulesFor(0)` (real-bug-fix, 15-02) |
| `BanksUatTest` | 2 | **0** | region-scoped non-uniformity gate, 0.95 intact (provably-stale, 15-03) |
| `PongStepAgentTest` | 1 | **0** | OAM expectation {1,1,1}/3 (provably-stale, 15-04) |
| `PlayerMetaspriteGeometryTest` | 2 | **0** | repoint to png2asset `sprites/player.c` / `player_metasprite0`; EXECUTED, not skipped (provably-stale, 15-05) |
| `PlatformerTemplate128UatTest` | 1 | **0** | sprite-region hflip diff + OAM xFlip, >10% global gate replaced (provably-stale, 15-05) |
| `PlatformerTemplateUatTest` | 0 (drift) | **0** | already green on main checkout; unchanged |

> Transient note: the first `pluginTest` attempt after the `test --continue` run hit a Gradle
> `BuildToolsApiClasspathEntrySnapshotTransform` cache error on the freshly-republished
> `gbkt-analysis-0.1.0-SNAPSHOT.jar` during `compileTestKotlin` (a build-cache hiccup from the rapid
> republish, NOT a test failure). A clean `./gradlew --stop && ./gradlew pluginTest` re-run was
> GREEN (BUILD SUCCESSFUL, 19/0/0/0). Recorded for transparency.

## D-02 split regression guard

**EXPECTED path taken — all fixes are test-side / build-wiring; zero production codegen changed.**

`git diff --name-only 771850c9 HEAD` over non-`.planning/` paths:

| File | Kind | Codegen? |
|------|------|----------|
| `build.gradle.kts` | root republish-set (`+ :gbkt-analysis`) | build-wiring |
| `gbkt-gradle-plugin/.../IntegrationTest.kt` | test (sandbox template) | test |
| `gbkt-examples/banks/.../BanksUatTest.kt` | test | test |
| `gbkt-examples/pong/.../PongStepAgentTest.kt` | test | test |
| `gbkt-examples/platformer-template/.../PlayerMetaspriteGeometryTest.kt` | test | test |
| `gbkt-examples/platformer-template/.../PlatformerTemplate128UatTest.kt` | test | test |
| `gbkt-examples/platformer-template/build.gradle.kts` | test-task wiring (`dependsOn convertSprites`) | build-wiring |

**Zero `src/main` / `gbkt-backend-gbdk` / `gbkt-core` / `gbkt-ir` / codegen source changed**, so all 7
KEEP examples' generated C is **byte-identical to the pre-phase baseline — NO re-pin required**. The
`metasprites` / `metasprites-stress` `*GeneratedSpriteByteIdentityTest` standing guards are GREEN in
the `test --continue` run above (they would have failed had any codegen drifted).

### 7× `:buildRom` EXIT 0

`./gradlew :gbkt-examples:{pong,breakout,simple-physics,metasprites,metasprites-stress,banks,platformer-template}:buildRom`
→ **BUILD SUCCESSFUL in 3s**, all 7 ROMs present:

| Example | ROM |
|---------|-----|
| pong | OK |
| breakout | OK |
| simple-physics | OK |
| metasprites | OK |
| metasprites-stress | OK |
| banks | OK |
| platformer-template | OK |

## Diagnosis ledger

`DIAGNOSIS-LEDGER.md` is finalized: all 18 rows resolved (12 `real-bug-fix`, 6
`provably-stale-assertion`, **0 `retired-capability-removal`**), **zero threshold-weakening rows**,
F2/F3/F4 corrected-not-deleted, D-04 deviation recorded.

## Verdict

The full JVM test suite is GREEN on both canonical commands from a clean tree, the D-02 split guard
holds (all 7 byte-identical, all 7 `:buildRom` EXIT 0), and the diagnosis ledger is closed with zero
threshold-weakening. **Phase 15 release gate: SATISFIED.** (Tagging v0.1.0 and re-presenting Phase
14's sign-off are downstream manual steps, not part of this phase.)
