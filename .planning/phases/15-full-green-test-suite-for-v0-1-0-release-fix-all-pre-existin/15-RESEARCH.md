# Phase 15: Full-green test suite for v0.1.0 release - Research

**Researched:** 2026-06-09
**Domain:** JVM test-suite remediation — Gradle TestKit hermeticity, GBDK codegen/metadata, png2asset metasprites, perceptual UAT thresholds
**Confidence:** HIGH (every failure root-caused against current source/generated-C on `feat/d_and_d_gaps`; two visual verdicts still require the D-03 live-screenshot confirmation the SPEC mandates)

## Summary

This is a brownfield, no-new-dependency **test-fixing** phase. There is no stack to
select, no package to install, and no greenfield architecture — the research value is a
per-failure **root-cause map** that tells the planner/executor, for each red test,
whether the truth is a *real bug* or a *provably-stale assertion*, and exactly which
source line proves it. I read the current source and the on-disk generated C for every
failing example; the findings below are code-grounded, not inferred from the snapshot.

The headline result: **most of the seven failures are provably-stale assertions, and the
codebase already contains the corrected behavior they failed to track.** The pong OAM
case is the clearest — `GBDKPipeline.buildMetadataFile` (line 227-229) was *deliberately*
changed to a 16px OAM slot so an 8x16 paddle emits `oamCount=1`, with an inline comment
saying "This makes oamCount correct for emulator agent assertions"; the test's
`expectedOamCounts = {paddle1:2, paddle2:2, ball:1}` / `expectedTotalOam=5` was never
updated to the now-correct `{1,1,1}` / `3`. The `PlayerMetaspriteGeometryTest` is a
*larger-than-described* stale assertion: the player metasprite moved to the
png2asset-native `sprites/player.c` (`player_metasprite0[]` as `METASPR_ITEM(...)`
macros), so the CONTEXT's "simple `sprite_player_frame_0`→`player_metasprites` grep
rename" will still fail — see Pitfall 1. The `IntegrationTest` `SceneIR.copy$default`
`NoSuchMethodError` is a SNAPSHOT-dependency-cache hermeticity defect with a clean durable
fix (D-05). The three visual UAT failures (banks dominant-colour, platformer facing-diff)
are the genuine "needs a live screenshot to decide" cases the Visual Evidence Rule / D-03
exist for — and the facing-diff threshold has an arithmetic smell (Pitfall 4).

**Primary recommendation:** Drive the suite green in this dependency order — (1) the
hermeticity fix for `IntegrationTest` (D-05, unblocks `pluginTest`), (2) the two
provably-stale static assertions (pong OAM, platformer geometry — D-03b, generated-C
evidence), (3) the three visual verdicts (banks ×2, platformer facing — D-03, live MCP
screenshots). Treat every "stale assertion" verdict as guilty-until-proven: the
correction must *demonstrate* the current output/screen is genuinely right (the pong/geom
cases are proven below; the visual cases need the screenshot). Zero threshold-weakening.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| TestKit sandbox dependency resolution | Root `build.gradle.kts` (`pluginTest` / `publishConsumedModulesToMavenLocal`) | `IntegrationTest` sandbox build template | The republish wiring lives at root; the SNAPSHOT-cache defeat must land in the sandbox build text the test writes |
| OAM-count metadata derivation | `gbkt-backend-gbdk` `GBDKPipeline.buildMetadataFile` (L227-235) | example test `MetadataExpectation` | Pipeline owns the truth; the test only mirrors it and drifted |
| Player metasprite geometry | Gradle `convertSprites` (png2asset) → `sprites/player.c` | `GBDKPipeline.generate()` main.c (no longer emits it) | Path A png2asset-native; the geometry array is an *asset artifact*, not pipeline output |
| Visual non-uniformity / facing diff | runtime ROM (emulator) | `assertScreenshotIsNonUniform` UAT helper | "X is visible" is a runtime truth — D-03 requires a live screenshot, not a generated-C grep |

## Architecture Patterns

### Per-Failure Root-Cause Map (the core deliverable)

Data flow for each failure: **DSL → GBDKPipeline.generate() (in-JVM) → generated C** for
static tests; **DSL → Gradle pipeline (generateC + convertSprites + lcc) → ROM → emulator
→ screenshot** for UAT tests. The fault for each is located on that path below.

#### F1 — `IntegrationTest` (×~12 of 19) — `NoSuchMethodError: SceneIR.copy$default(...)` — HERMETICITY (real defect, but in the *test harness*, not the product)
- **Where:** `gbkt-gradle-plugin/src/test/kotlin/io/github/gbkt/gradle/IntegrationTest.kt`; root `build.gradle.kts` `pluginTest` wiring (L45-63).
- **Mechanism:** `SceneIR` is a 14-field data class (`gbkt-ir/.../SceneIR.kt`). `zoneRefs`
  (Phase 11.x, commit `eda282ec`) and `allocatedZoneBank` (Phase 13.8 Plan 06) were added
  over time. `GBDKPipeline.buildCFiles` calls `scene.copy(allocatedZoneBank = …)`. The
  Kotlin-synthesized `copy$default(...)` bridge changes signature (extra default-mask
  ints) every time a field is added. The TestKit sandbox declares
  `implementation("io.github.gbkt:gbkt-core:0.1.0-SNAPSHOT")` /
  `runtimeOnly("…gbkt-backend-gbdk:0.1.0-SNAPSHOT")` resolved from `mavenLocal()`
  (IntegrationTest.kt L53, L542, L547-549). The codegen (fresh `backend-gbdk`) calls the
  *new* `copy(allocatedZoneBank=…)` against a **stale `gbkt-ir` `SceneIR`** whose
  `copy$default` arity predates the field → `NoSuchMethodError` at runtime.
- **Why `pluginTest`'s republish does NOT clear it (the D-05 question, answered):**
  `publishConsumedModulesToMavenLocal` updates `~/.m2`, but the nested `GradleRunner`
  sub-build resolves `0.1.0-SNAPSHOT` as a **changing module** through the Gradle module
  cache (`~/.gradle/caches/modules-2`), whose default SNAPSHOT TTL is **24 hours**
  (`cacheChangingModulesFor`). After the first resolve, a later republish of `gbkt-ir`
  (or `backend-gbdk`) does not re-propagate within the TTL, so the two modules can
  **desync** in the cache → exactly this linkage error. The republish is necessary but
  not sufficient because it operates on `~/.m2`, not the Gradle cache the sub-build reads.
- **Verdict:** real test-infra defect (not product). **Durable fix (D-05 preferred):** make
  the sandbox defeat SNAPSHOT caching. Two viable mechanisms — inject
  `configurations.all { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }` into
  the generated sandbox `build.gradle.kts` (centralized, one edit in the build-file
  template), **or** add `--refresh-dependencies` to the `withArguments(...)` of the 19
  `GradleRunner` calls (19 edit sites, blunter). Prefer the former. Evidence: D-03b static
  (the 19 `IntegrationTest` cases pass under `./gradlew pluginTest` + a stack-trace-free run).

#### F2 — `PongStepAgentTest.metadata and symbol table agree on variable names` — PROVABLY-STALE ASSERTION
- **Where:** `gbkt-examples/pong/.../PongStepAgentTest.kt` L48-57. Truth source:
  `GBDKPipeline.buildMetadataFile` L227-229.
- **Mechanism:** Current pipeline code:
  `oamSlotHeight = if (height <= 8) 8 else 16; oamCount = tilesWide * ((height + oamSlotHeight - 1) / oamSlotHeight)`.
  For the 8x16 paddle: `tilesWide=1`, `oamSlotHeight=16`, `oamCount = 1 * ((16+15)/16) = 1`.
  The inline comment (L227) states this 16px-OAM-slot rule is **intentional** — "This
  makes oamCount correct for emulator agent assertions." So metadata now reports
  `paddle1=1, paddle2=1, ball=1, total=3`. The test still hard-codes
  `{paddle1:2, paddle2:2, ball:1}` and `expectedTotalOam=5` — the pre-16px-slot values.
  Hence "expected=2 actual=1".
- **Verdict:** provably-stale assertion. **Correction:** update `expectedOamCounts` to
  `{PADDLE1:1, PADDLE2:1, BALL:1}` and `expectedTotalOam=3`. This is NOT threshold-weakening
  — it aligns the test to the deliberately-corrected metadata. **Diagnose-first guard
  (must do before correcting):** confirm the *runtime* actually renders each paddle as ONE
  hardware OAM in 8x16 sprite mode (read OAM via emulator / inspect generated sprite mode);
  if the ROM were in 8x8 mode the paddle would need 2 OAM and *metadata* would be the bug.
  All current evidence says 16px-slot is correct and the test drifted. Evidence: D-03b
  static (generated metadata.json `oamCount`) + one confirming emulator OAM read.

#### F3 / F4 — `PlayerMetaspriteGeometryTest` (×2) — PROVABLY-STALE ASSERTION (bigger than a rename — see Pitfall 1)
- **Where:** `gbkt-examples/platformer-template/.../PlayerMetaspriteGeometryTest.kt`
  (`mainC()` L131-139 reads only `pipelineOutput.files["main.c"]`; greps
  `sprite_player_frame_0[]` L182/L218).
- **Mechanism:** The platformer-template player now uses **Path A png2asset-native**
  metasprites. Generated `main.c` L70 is only a *comment*:
  `/* Path A — player uses png2asset-native player_metasprites[idx] (see sprites/player.h; defined in sprites/player.c) */`.
  The real arrays live in `build/gbkt/generated/sprites/player.c`:
  per-frame `const metasprite_t player_metasprite0[] = { METASPR_ITEM(-6,-12,0,S_PAL(0)), … }`
  (12 frames) plus the pointer table `const metasprite_t* const player_metasprites[12]`.
  This file is produced by the Gradle `convertSprites` task (png2asset), **not** by
  `GBDKPipeline.generate()`. So the test's `mainC()` will never contain the array under
  ANY name — the symbol is in a different file the in-JVM pipeline does not emit.
- **Geometry is byte-identical / correct:** the 6 `METASPR_ITEM` rows in
  `player_metasprite0[]` are exactly the reference values the test's own header documents
  (L41-45): `(-6,-12,0)(0,8,2)(0,8,4)(16,-16,6)(0,8,8)(0,8,10)` → cumulative x `{-12,-4,4}`,
  y `{-6,10}` = the 3col×2row 24×32 SPR8x16 grid. So the capability (correct layout) is
  intact — this is a correction, not a removal (consistent with D-04).
- **Verdict:** provably-stale assertion, **but the CONTEXT/D-04 "grep rename" is
  insufficient** (Pitfall 1). The correction must (a) repoint the source to
  `sprites/player.c`, (b) target `player_metasprite0` (NOT `player_metasprites`, which is
  the pointer table, and NOT `sprite_player_frame_0`), (c) parse the
  `METASPR_ITEM(dy,dx,tile,…)` macro form (the existing `parseFrameEntries` regex matches
  `{int,int,int}` braces, which won't match the macro), and (d) decide how the JVM test
  *acquires* the asset file — it is not in `GBDKPipeline.generate()` output, so the test
  must read the on-disk `build/gbkt/generated/sprites/player.c` (requires a prior
  `generateC`+`convertSprites`) or be re-tiered. Evidence: D-03b static (asset-C grep).

#### F5 / F6 — `BanksUatTest` (×2) — VISUAL VERDICT (D-03, live screenshot REQUIRED)
- **Where:** `gbkt-examples/banks/.../BanksUatTest.kt`; `assertScreenshotIsNonUniform`
  L119-150 — fails when `dominantRatio = dominantCount/totalPixels >= 0.95`.
- **Mechanism:** banks is a bank-switching **codegen demo**; its play/anchor scene is
  by-design near-blank (a tiny content border on a flat field), so one colour covers
  ≥95% of the 160×144 frame → the generic non-uniformity gate trips.
- **Verdict undecided by static evidence — this is precisely the D-03 case.** Either the
  scene is *supposed* to be near-blank (assertion tests the wrong premise → correct it to
  a scene-appropriate invariant, e.g. assert the specific expected content/text/tilemap is
  present, or capture a frame that has content) OR the scene should have content and
  renders blank (real rendering/bank-load bug → fix). The SPEC forbids lowering 0.95. Per
  D-03 + CLAUDE.md Visual Evidence Rule, the verdict MUST be backed by a live
  `mcp__gbkt-emulator__emulator_screenshot` to `evidence/`. Note `BanksUatTest` header
  (L185) already records the threshold was "relaxed from 4 to 2" distinct-colours in a
  prior plan — do not confuse that prior change with a license to relax further.

#### F7 (+ sibling) — `PlatformerTemplate128UatTest` / `PlatformerTemplateUatTest` facing-diff — VISUAL VERDICT (D-03) with an arithmetic smell (Pitfall 4)
- **Where:** `gbkt-examples/platformer-template/.../PlatformerTemplate128UatTest.kt`
  (facing-right vs facing-left pixel diff, threshold > 10%, observed 6.80%) +
  `assertScreenshotIsNonUniform` L103-129. `PlatformerTemplateUatTest` is the ROADMAP-
  under-counted sibling (present in the 14-FINAL-REGRESSION differential sweep).
- **Mechanism / smell:** the player sprite is 24×32 = 768 px on a 160×144 = 23 040 px
  screen = **3.33% of the frame**. A pure horizontal facing-flip of a 24×32 sprite can
  change at most ~3.3% of pixels; the observed 6.80% already exceeds that (so scene/camera/
  animation also differs between captures), and a **>10% global threshold is likely
  arithmetically unreachable from a facing flip** of a single small sprite. That points to
  a mis-calibrated threshold (stale/wrong premise), NOT necessarily a broken flip.
- **Verdict (D-03):** capture facing-right and facing-left live screenshots; if the flip
  is *visually real* and 10% is provably unreachable, correct the assertion to a defensible
  measure (e.g. diff restricted to the sprite region, or "the two frames differ" without
  the unreachable 10% global gate) — NOT a blind lower of 10% to 6%. If the flip is NOT
  happening, it is a real animation/facing codegen bug (D-01 wide fix candidate). Platformer
  is GBC-target: start MCP with `gbcMode=true` + `.noi` symFile, and traverse with RIGHT+A
  jumps, not held-RIGHT (per `learning_platformer_mcp_needs_gbc_mode` /
  `learning_platformer_traversal_needs_jumps`).

### Anti-Patterns to Avoid
- **Lowering 0.95 / 10% / OAM-2 to coerce a pass** — explicit SPEC phase-failure condition.
- **Blind grep-rename for F3/F4** — the array left `main.c`; a rename alone stays red (Pitfall 1).
- **Per-file `grep -c` across bank files** — use scope/array-body extraction (CLAUDE.md scope-grep corollary); the existing `extractArrayBody` brace-walk is the right tool, repointed.
- **Parallel `./gradlew clean` against the same root** — Kotlin daemon collision; chain into one invocation or run serially (`feedback_no_parallel_gradle_clean`).

## Runtime State Inventory

> Rename/move-relevant state for the stale-assertion fixes.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Renamed/moved symbol | `sprite_player_frame_0[]` (old, main.c, hand-emitted) → `player_metasprite0[]` + pointer table `player_metasprites[12]` (new, `sprites/player.c`, png2asset) | Repoint `PlayerMetaspriteGeometryTest` to the asset file + macro parse (F3/F4) |
| Drifted metadata constant | metadata `oamCount` for tall sprites changed to 16px-OAM-slot math (paddle 8x16 → 1, not 2) | Update pong `MetadataExpectation` to `{1,1,1}` / total 3 (F2) |
| Stale Gradle module cache | `0.1.0-SNAPSHOT` changing modules cached ≤24h in `~/.gradle/caches/modules-2` | Defeat SNAPSHOT cache in TestKit sandbox (F1 / D-05) |
| Leftover agent worktrees | `.claude/worktrees/agent-*` contain stale duplicate `SceneIR.kt` / `IntegrationTest.kt` | Ignore — work only on the main checkout (known drift quirk); do not let greps pick them up |
| Byte-identity baselines | `metasprites` / `metasprites-stress` `*GeneratedSpriteByteIdentityTest` (re-pinned 13.6-07) | Verify-then-re-pin only if drift detected (Claude's Discretion in CONTEXT) |

## Common Pitfalls

### Pitfall 1: "Just rename the grep" for PlayerMetaspriteGeometryTest (the CONTEXT/D-04 trap)
**What goes wrong:** Changing `extractArrayBody(cSource, "sprite_player_frame_0")` to
`"player_metasprites"` still returns empty and the test stays RED.
**Why it happens:** The array is no longer in `main.c` at all (Path A png2asset) — it lives
in `sprites/player.c` as `player_metasprite0[]` using `METASPR_ITEM(...)` macros, and
`GBDKPipeline.generate()` does not emit that file. The CONTEXT decision D-04 describes the
fix as a one-token rename; the code says otherwise.
**How to avoid:** Repoint the *source* (read `build/gbkt/generated/sprites/player.c`),
the *symbol* (`player_metasprite0`), and the *parser* (`METASPR_ITEM` macro, not `{..}`),
and decide the asset-acquisition strategy for a JVM test. Flag the D-04 scope gap to the user.
**Warning signs:** `arrayBody.isNotEmpty()` assertion still failing after the rename.

### Pitfall 2: Assuming `pluginTest`'s republish guarantees fresh classes
**What goes wrong:** You republish to `~/.m2`, re-run `pluginTest`, and `IntegrationTest`
is still red with the same `NoSuchMethodError`.
**Why it happens:** The nested `GradleRunner` resolves SNAPSHOTs from the Gradle module
cache (24h TTL), not directly from `~/.m2`. The publish updated `~/.m2`; the sub-build
served the cached jar.
**How to avoid:** Defeat changing-module caching in the sandbox (D-05). To *reproduce
deterministically*, you may need `./gradlew --stop` + a fresh resolve; do not rely on a
single republish proving anything.
**Warning signs:** Republish "succeeds" but the linkage error is unchanged.

### Pitfall 3: Treating OAM-count as a visual truth (over-applying D-03)
**What goes wrong:** Spending an MCP screenshot session to "prove" the OAM count.
**Why it happens:** OAM *count* is an internal metadata vs runtime-OAM truth (D-03b →
static evidence: metadata.json + one emulator OAM read). The *visual* truth (paddle
renders as a full 8x16, not a clipped 8x8) is a useful secondary check but the count
verdict itself is static.
**How to avoid:** Use D-03b static evidence for F2/F1/F3/F4; reserve live screenshots
(D-03) for F5/F6/F7 (banks, platformer facing).

### Pitfall 4: Lowering the platformer facing threshold to the observed 6.80%
**What goes wrong:** Changing `> 10%` to `> 6%` to make the test pass — a SPEC phase-failure.
**Why it happens:** The 10% global-frame threshold is likely arithmetically unreachable
for a 24×32 sprite (~3.3% max of a 160×144 frame) facing-flip; the temptation is to nudge
the number.
**How to avoid:** Prove (live screenshots) the flip is visually real, then *re-architect*
the measure (sprite-region diff or distinctness) rather than nudging the global percent.
If the flip is NOT real, it is a codegen bug to fix, not a threshold to touch.
**Warning signs:** A diff of a small on-screen sprite asked to exceed a double-digit
percentage of the whole frame.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The 19-test `IntegrationTest` skew is SNAPSHOT-cache desync, fixed by `cacheChangingModulesFor(0)` in the sandbox | F1 | If a *stale `~/.m2` artifact* (not the Gradle cache) is the source, the cache-defeat alone won't help; D-05 says diagnose-first — confirm by inspecting both `~/.m2` and `~/.gradle/caches` timestamps before fixing |
| A2 | pong renders each 8x16 paddle as ONE OAM in 8x16 sprite mode (so metadata=1 is correct, test=2 is stale) | F2 | If the ROM is in 8x8 mode the paddle needs 2 OAM and *metadata* is the bug; must confirm via runtime OAM read before correcting the test |
| A3 | The platformer facing flip is visually real and 10% is an unreachable threshold | F7 | If the flip is absent, it is a real codegen bug (D-01), not a threshold correction; the live screenshot decides |
| A4 | The banks anchor scene is near-blank *by design* | F5/F6 | If it should have content, it is a real bank-load/render bug; the live screenshot decides |
| A5 | A JVM test can read `build/gbkt/generated/sprites/player.c` after a prior `generateC`+`convertSprites` | F3/F4 | If asset conversion isn't run in the test's module before the test, the file is absent; the test may need re-tiering or a fixture |

## Open Questions

1. **F3/F4 asset acquisition for a JVM-tier test**
   - What we know: the geometry is correct and lives in `sprites/player.c` (png2asset), not pipeline output.
   - What's unclear: whether to (a) read the on-disk generated asset (couples a unit test to a Gradle build artifact), (b) parse a committed fixture copy of the png2asset output, or (c) re-tier the geometry guard to the asset pipeline.
   - Recommendation: prefer reading the on-disk `build/gbkt/generated/sprites/player.c` with an explicit "run generateC first" precondition, mirroring how other example UAT tests depend on a built artifact; the executor decides per diagnose-first.

2. **F1 fix locus: sandbox build-text vs `withArguments`**
   - What we know: both `cacheChangingModulesFor(0)` (1 site) and `--refresh-dependencies` (19 sites) defeat the cache.
   - Recommendation: the centralized build-text injection (D-05 durable/hermetic preference); confirm root cause first per D-05.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| GBDK-2020 (lcc/png2asset) | `:buildRom`, `convertSprites`, F3/F4 asset gen | host-dependent | — | CI-gated tests auto-skip via `@DisabledIfEnvironmentVariable(CI)` |
| `gbkt-emulator` MCP server JAR | D-03 live screenshots (banks, platformer) | needs `:gbkt-mcp-server:shadowJar` after any `clean` | — | none for visual verdict — must be built (`project_gbkt_mcp_jar_cleaned_by_gradle_clean`) |
| mGBA / embedded Coffee-GB | UAT runtime | embedded | — | embedded emulator is the default |

**Missing dependencies with no fallback:** D-03 visual verdicts require the MCP emulator
JAR present under `build/` — rebuild with `./gradlew :gbkt-mcp-server:shadowJar` if a
`clean` wiped it.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit5 + `kotlin.test`; Gradle TestKit (`GradleRunner`) for plugin; `GbktTestExtension`/`StepAgent`/`UatRunner` for example UAT |
| Config file | per-module `build.gradle.kts` `test {}`; root `pluginTest` aggregator |
| Quick run command | per failing module, e.g. `./gradlew :gbkt-examples:pong:test` |
| Full suite command | `./gradlew test --continue` **and** `./gradlew pluginTest` (both must be green — SPEC Req 1) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| Req 1/2 | Whole suite green from fresh run | aggregate | `./gradlew test --continue` + `./gradlew pluginTest` | ✅ |
| Req 3 | IntegrationTest green (F1) | TestKit | `./gradlew pluginTest` | ✅ |
| Req 4 | BanksUatTest green (F5/F6) | UAT + D-03 screenshot | `./gradlew :gbkt-examples:banks:test` | ✅ |
| Req 5 | PongStepAgentTest green (F2) | metadata/UAT | `./gradlew :gbkt-examples:pong:test` | ✅ |
| Req 6 | platformer 3 classes green (F3/F4/F7) | geometry + UAT + D-03 | `./gradlew :gbkt-examples:platformer-template:test` | ✅ |
| Req 7 | Per-failure diagnosis ledger | doc artifact | n/a (evidence/ ledger) | ❌ Wave 1 (15-01 scaffolds it) |

### Sampling Rate
- **Per task commit:** the single affected module's `:test`.
- **Per wave merge:** `./gradlew test --continue` for collateral drift; `pluginTest` after F1.
- **Phase gate:** both aggregate commands green from a clean tree + 7× `:buildRom` EXIT 0 + the D-02 byte-identity split-guard (affected example re-pinned, other 6 byte-identical).

### Wave 0 Gaps
- None — all six failing test classes already exist; this phase fixes/repoints them, it does not author new test infrastructure. The only new artifact is the Req-7 diagnosis ledger under `evidence/` (D-06), scaffolded by 15-01.

## Security Domain

> Not applicable in the conventional sense. This phase fixes JVM tests and (at most)
> deterministic codegen/metadata derivations for an offline Game Boy ROM toolchain. No
> network surface, no authn/z, no untrusted input, no secrets are introduced or touched.

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | no | DSL is trusted developer source, compiled offline |
| V12 Files/Resources | marginal | TestKit writes into a JUnit temp dir; the SNAPSHOT-cache fix narrows, not widens, what the sandbox resolves |

**Net:** no new threats; the only "trust" change (F1) makes dependency resolution *more*
deterministic. No `<threat_model>` mitigations are warranted beyond noting N/A.

## Sources

### Primary (HIGH confidence)
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/SceneIR.kt` — 14-field data class, `zoneRefs`/`allocatedZoneBank` provenance (F1 copy$default mechanism).
- `gbkt-backend-gbdk/.../codegen/pipeline/GBDKPipeline.kt` L227-235 — OAM-count 16px-slot derivation (F2 truth source).
- `gbkt-examples/platformer-template/build/gbkt/generated/sprites/player.c` + `sprites/player.h`, and `main.c` L70 — Path A png2asset metasprite location/format (F3/F4).
- `gbkt-examples/pong/.../PongStepAgentTest.kt` L48-57 — stale `MetadataExpectation` (F2).
- `gbkt-examples/platformer-template/.../PlayerMetaspriteGeometryTest.kt` — `mainC()`/grep target (F3/F4).
- `gbkt-examples/banks/.../BanksUatTest.kt` L119-150 + platformer `PlatformerTemplate128UatTest.kt` L103-129 — `assertScreenshotIsNonUniform` 0.95 gate / facing diff (F5/F6/F7).
- Root `build.gradle.kts` L45-63 — `pluginTest` / `publishConsumedModulesToMavenLocal` wiring; `IntegrationTest.kt` L47-60, L535-560 — sandbox build template (F1 fix locus).

### Secondary (MEDIUM confidence)
- Gradle docs behavior: changing/SNAPSHOT module cache default TTL 24h (`cacheChangingModulesFor`) — basis for the F1 durable fix (A1, confirm against host caches before fixing).

### Tertiary (LOW confidence)
- None.

## Metadata

**Confidence breakdown:**
- F1 (IntegrationTest hermeticity): HIGH on mechanism, MEDIUM on exact cache-vs-m2 locus (A1 — diagnose-first per D-05).
- F2 (pong OAM): HIGH — pipeline source + test source both read, math closes (1/1/1 = 3).
- F3/F4 (metasprite geometry): HIGH — asset file inspected, values match the test's own documented reference.
- F5/F6/F7 (visual): MEDIUM by design — verdict is gated on the D-03 live screenshot the SPEC mandates; static evidence cannot decide real-bug-vs-stale.

**Research date:** 2026-06-09
**Valid until:** 2026-07-09 (stable internal codebase; invalidate earlier if codegen/metadata or the example DSLs change before execution)
