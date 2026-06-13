# Architecture Research

**Domain:** Kotlin DSL → GBDK C compiler (Game Boy) — v0.1.1 hardening integration
**Researched:** 2026-06-12
**Confidence:** HIGH

## Standard Architecture

### System Overview

The v0.1.0 pipeline that v0.1.1 hardens:

```
Kotlin DSL (gbkt-lang)
    ↓ DSL recording via GameBuilderContext / ScriptBuilderContext
IR construction (gbkt-ir, gbkt-engine, gbkt-world)
    ↓ GameBuilder.build() → GameIR tree
Analysis (gbkt-analysis) — 12 ordered passes
    ↓ DefaultPipeline: validate → allocate banks/VRAM/OAM/RAM → budget audit
Annotation — applyAnnotations() copies allocations back onto GameIR
    ↓
Code generation (gbkt-backend-gbdk)
    ↓ GBDKPipeline → 13 visitors → typed C AST → CEmitter → C text
Post-processing (COutputOptimizer)
    ↓ dedup constants + functions
Gradle plugin / CLI — invokes GBDK lcc to produce .gb ROM
```

The 20-module layout does NOT change in v0.1.1. All hardening work is internal to
existing modules — no new modules, no new public IR interfaces.

### Component Responsibilities — Hardening Touch Map

| Component | Module | v0.1.1 Touch | Risk |
|-----------|--------|--------------|------|
| `GBDKPipeline.kt` | gbkt-backend-gbdk | S3776 extract-method + SEED-014/015 banking gate fix | HIGH — changes C output for all zone games |
| `GBDKSystemVisitor.kt` | gbkt-backend-gbdk | S3776 extract-method (6390 lines, ~20 hotspots) | MEDIUM — S3776 only is zero-C-change |
| `ScriptOpVisitor.kt` | gbkt-backend-gbdk | S3776 extract-method (2052 lines) | LOW |
| `RpgVisitor.kt` | gbkt-backend-gbdk | S3776 extract-method (3377 lines) | LOW |
| `CombatVisitor.kt` | gbkt-backend-gbdk | S3776 extract-method (2837 lines) | LOW |
| `ActorVisitor.kt` | gbkt-backend-gbdk | S3776 extract-method (1365 lines) | LOW |
| `MetaspriteVisitor.kt` | gbkt-backend-gbdk | SEED-004/005/006/008/010/011 codegen fixes | HIGH — C output changes for metasprites example |
| `PlatformerVisitor.kt` | gbkt-genre-platformer | SEED-PHASE-12 wiring gaps + SEED-021/022 | HIGH — C output changes for platformer-template |
| `GenerateCTask.kt` | gbkt-gradle-plugin | S3776 extract-method (929 lines) | LOW — no C output impact |
| `ConvertSpritesTask.kt` | gbkt-gradle-plugin | S3776 + SEED-PHASE-13 tRNS transparency fix | MEDIUM — affects sprite assets for every game |
| `ConvertZoneTilesetsTask.kt` | gbkt-gradle-plugin | S3776 extract-method (726 lines) | LOW |
| `GameBuilder.kt` | gbkt-lang | SEED-007 palette slot default fix (line 713) | LOW — 1-3 line change |
| `ScriptBuilder.kt` | gbkt-lang | SEED-023 whenever/runIf deprecation | LOW — DSL surface only |
| `RpgExtensions.kt` | gbkt-genre-rpg | SEED-025 combatIsInState String removal | LOW |
| `context/DSL_REFERENCE.md` | docs | 13 dead-API sections pruned/rerouted | ZERO codegen risk |
| detekt.yml + source files | multiple | QUAL-01..03 detekt violations, magic pixels | LOW |


## S3776 Cognitive-Complexity Hotspot Analysis

### Where the 46 Sonar findings live

Sonar S3776 fires when a function's cognitive complexity score exceeds the project
threshold (typically 15). Each `if`/`for`/`while`/`catch` at nesting level 0 scores 1;
nesting multiplies the score. The findings concentrate in the largest visitors and pipeline
builders, NOT in the analysis passes (which are well-factored at below 500 lines each).

**Tier 1: Primary hotspot files (estimated 30+ findings)**

`GBDKSystemVisitor.kt` (6390 lines) — the densest concentration:
- `visitExplorationSystem()` spans roughly 870-1851 lines (981 lines), containing
  `buildEntityCollisionFunctions`, `buildEncounterCheckFunction`,
  `buildZoneLoadFunction`, `buildZoneTransitionFunction`, `buildZoneCheckEdgesFunction`,
  `buildZoneObjectFunctions`, `buildChestHandlerFunction`, `buildSignHandlerFunction`,
  `buildNpcHandlerFunction`, `buildLeverHandlerFunction`, `buildSconceHandlerFunction`
  — each of which has 3-5 levels of nested `for`/`when`/`if`
- `buildPuzzleObjectFunctions()` (~568 lines, 4861-5429) — deep branching on puzzle object types
- `buildPickupFunctions()` (~544 lines, 5773-6317) — pickup type dispatch with nested C AST builders
- `buildNpcCollisionFunctions()` (~283 lines, 5490-5773) — NPC collision shape dispatch

`GBDKPipeline.kt` (5397 lines):
- `buildHomeFile()` (~627 lines, 964-1591) — assembles main.c; the file's own CLAUDE.md calls
  this "the largest method in the pipeline"
- `buildSetupCurrentLevelFunctionIfNeeded()` (~204 lines, 2469-2673) — multi-condition branching
  on tilemap config + physics + zone binder presence
- `buildTrampolinesForScene()` (3211-3290) — scene trampoline emission loop with SEED-015 bug

**Tier 2: Secondary hotspot files (estimated 10-15 findings)**

`RpgVisitor.kt` (3377 lines):
- Character stat codegen functions dispatch over 6-8 stat types with nested enum matching
- Ability resolution trees with equipment interaction branching

`CombatVisitor.kt` (2837 lines):
- Battle state machine codegen: 5-state machine with nested per-state action builders
- Damage formula visitor with operator dispatch

`ScriptOpVisitor.kt` (2052 lines):
- `visitIfOp()` — recursively builds nested CStatement; detekt LongMethod exclusion in
  `**/codegen/**` already applies, but S3776 cognitive-complexity fires separately
- `visitPoolForEachActive()` — complex loop with active-predicate injection
- `visitMoveMetasprite()` — flip-variant switch with raw-C escape hatch

**Tier 3: Gradle task files (estimated 4-6 findings)**

`GenerateCTask.kt` (929 lines), `ConvertSpritesTask.kt` (868 lines),
`ConvertZoneTilesetsTask.kt` (726 lines) — each has conditional asset-pipeline
dispatch that generates S3776 findings.

`PlatformerVisitor.kt` (2548 lines, gbkt-genre-platformer) — `buildTilemapPhysicsUpdateFunction()`
contains the 5-point AABB probe with multi-axis tilemap lookup dispatch.


### Refactoring patterns that work WITHOUT changing emitted C

The byte-identity oracle gates (7 examples + `./gradlew :gbkt-examples:*:buildRom` sweep)
mean that S3776 refactors must produce zero diff in generated C text. Three patterns
satisfy this constraint:

**Pattern 1: Extract private helper methods (primary)**
Move a block of logic that builds a subtree of the C AST into a private method within
the same class. The method signature takes the relevant GameIR subset and returns
`List<CStatement>` or `CFunction` — the same types already used. The calling code
becomes a composition of named helpers.

Example: `GBDKSystemVisitor.visitExplorationSystem()` at 981 lines should become:
```kotlin
override fun visitExplorationSystem(system: ExplorationSystem): List<CFunction> {
    return buildEntityCollisionFunctions(gameIR, system) +
           buildZoneLoadFunctions(system, zones) +
           buildZoneTransitionFunctions(system, zones) +
           buildZoneObjectFunctions(zones) +
           buildEncounterFunctions(system, zones) +
           buildPathwayHelpers(system)
}

private fun buildZoneLoadFunctions(system: ExplorationSystem, zones: List<ZoneIR>): List<CFunction> =
    zones.map { zone -> buildZoneLoadFunction(zone.sanitizedId, listOf(zone)) }
```

Many helpers already exist as private methods; the refactor promotes inner blocks to
properly-named methods at the class level, reducing the complexity of the calling method.

**Pattern 2: Map-based dispatch for string-keyed system variants**
`GBDKSystemVisitor.visitGenericSystem()` dispatches on `system.config["type"]`. Replace
a `when` chain with a function-reference map:
```kotlin
private val genericSystemBuilders: Map<String, (GenericSystem) -> List<CFunction>> = mapOf(
    "sport_racing" to ::buildRacingSystemFunctions,
    "pickup" to ::buildPickupSystemFunctions,
)
```
This reduces the function's top-level branch count from N to 1 (the map lookup + default).

**Pattern 3: Sub-builder extraction for `GBDKPipeline.buildHomeFile()`**
`buildHomeFile()` invokes roughly 30 distinct sub-builders inline. Each should be a named
private method call. The function body becomes a sequential list of `val x = buildX(gameIR)`
calls assembled into `CFile(...)`. No logic changes — only call-site extraction.

**Pattern NOT to use: sealed-interface dispatch**
The project explicitly replaced sealed IR hierarchies with non-sealed interfaces + visitors
to enable the multi-module split. Refactoring back toward sealed `when` dispatch would
break the extensibility contract for genre plugins. Every refactor must stay within the
visitor pattern.


### Verification gate for S3776 refactors

After each batch of extract-method refactors, run the 7-target buildRom sweep:
```bash
./gradlew :gbkt-examples:pong:buildRom \
          :gbkt-examples:breakout:buildRom \
          :gbkt-examples:simple-physics:buildRom \
          :gbkt-examples:metasprites:buildRom \
          :gbkt-examples:banks:buildRom \
          :gbkt-examples:platformer-template:buildRom
```
All must exit 0 and produce byte-identical ROMs to pre-refactor output. Pong is known
non-deterministic (lcc toolchain non-determinism); flag as PASS* in reporting.


## Seed Defect Cluster Analysis

### Mandatory first step: Seed triage

Several seeds were planted BEFORE Phases 12.6-13.8 shipped (2026-05-25 through 2026-06-05).
The PROJECT.md explicitly warns: "Stale status hints ... must be re-verified against current
master, not trusted." Re-verify each seed against master before opening fix phases. Known
resolutions already visible in the seed files themselves:

| Seed | Status in seed file | Disposition |
|------|---------------------|-------------|
| SEED-PHASE-12-GRASS-TILEMAP-WHITE-PIXELS | "RESOLVED 2026-06-02 by Phase 12.9" | CLOSED — verify and archive |
| SEED-PHASE-12-CONVERTSPRITESTASK-AUDIT | "RESOLVED 2026-05-24 by Phase 12.4" | CLOSED — verify and archive |
| SEED-PHASE-12-RETROACTIVE-BANKS-AUDIT | "Trivially satisfied by D-01" | CLOSED — verify and archive |
| SEED-PHASE-12-TITLE-ZONE-PATH-A-SCENE-RENDER-DEFECT | "closed (2026-05-23)" | CLOSED — verify and archive |
| SEED-PHASE-12-PLAYER-LEVITATING-NOT-GROUNDED | "active blocker" | Likely CLOSED by Phase 12.6/12.7 — re-verify |
| SEED-014 | gate says "hasSportRacing only" | PARTIALLY FIXED: `hasZoneSceneBinder` check already present at GBDKPipeline.kt:1164-1168 — verify if INV-2 sentinel test now passes |
| SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE-BG-AND-OBJ | "OPEN" | Phase 13.8 "palette/sprite codegen hardening" APPROVED — verify platformer-template ROM before treating as open |


### Cluster A: Banks trio (SEED-014 / SEED-015 / SEED-016)

**Root cause group:** `GBDKPipeline.buildHomeFile()` banking gate + `buildTrampolinesForScene()`

| Seed | File | Line range | Root cause |
|------|------|------------|------------|
| SEED-014 | `GBDKPipeline.kt` | 1160-1172 | `_bkg_tiles_load_banked` helper gate; the `hasZoneSceneBinder` arm was added (line 1164) but may not yet cover all multi-bank zone games; verify INV-2 sentinel in `BanksEmissionTest.kt` |
| SEED-015 | `GBDKPipeline.kt` | `buildTrampolinesForScene()` ~3211-3290 | Trampoline emission loop retains last-banked function reference; HOME-resident scenes (small enough to fit in HOME) get wrong trampoline body pointing at previous banked scene |
| SEED-016 | `BanksUatTest.kt` + possibly `SavestateManager.kt` | N/A | Anchor 4 SRAM round-trip test was skipped; needs execution once SEED-014 unblocks visual evidence |

Fix ordering: triage SEED-014 first (gate may already be correct on master). If
`BanksEmissionTest.kt` INV-2 sentinel is still RED, fix gate → fix 015 trampoline loop
→ run Anchor 4 UAT for 016.

New test files: add RED→GREEN assertion in `BanksEmissionTest.kt` that `title_enter_trampoline`
body is a no-op stub, NOT a delegation to `pause_enter()`.

Blast radius: WIDE — any game with zones in non-HOME banks. Requires discuss-phase + research
before code changes (per `feedback_route_to_proper_phase_when_blast_radius_is_wide`).


### Cluster B: Metasprite visual parity (SEED-004 / SEED-005 / SEED-006 / SEED-007)

**Root cause group:** Small, independent bugs in `MetaspriteVisitor.kt` and `GameBuilder.kt`
that all degrade the metasprites example ROM's visual output.

| Seed | File | Root cause | Scope |
|------|------|------------|-------|
| SEED-004 | `MetaspriteVisitor.kt` `generateMetaspriteTileData()` | Tile byte-plane ordering mismatch vs png2asset; may also be 8x8 vs 8x16 sprite mode difference | Medium — needs hex-dump investigation |
| SEED-005 | `MetaspriteBuilder.kt` OR `MetaspriteVisitor.kt` | `bgFillCheckerboard()` byte literal is a diagonal line, not a checkerboard | Small — 1-line literal replacement |
| SEED-006 | `MetaspriteVisitor.kt` | `_elephant_subPalette` global declared but never assigned in frame loop | Small — 1-2 line Kotlin add |
| SEED-007 | `GameBuilder.kt` line 713 | `else 0` hardcoded palette slot default instead of sequential counter (same bug fixed in SceneBuilder plan 10-16) | Small — 1-3 line change |

Fix ordering: SEED-005 + SEED-006 + SEED-007 first (trivial, bounded), then SEED-004
(needs investigation via hex comparison of generated `elephant_tiles[]` vs png2asset output).

Also diagnose SEED-013 (active status: GBC palette write path visual regression introduced
by Plans 10.1-19/20/22) in the same phase, as it shares the metasprites example ROM.

Byte-identity oracle update required for metasprites example after any fix.


### Cluster C: Metasprite structural / latent (SEED-008 / SEED-009 / SEED-010 / SEED-011)

**Root cause group:** Infrastructure gaps in metasprite codegen that are latent because no
current example uses 2+ metasprites or an actor+metasprite combination simultaneously.

| Seed | File | Root cause | Scope |
|------|------|------------|-------|
| SEED-008 | `GBDKPipeline.kt` `buildSpriteDataLoadStatements` + `buildMetaspriteTileDataLoadStatements` | Both start `nextTile = 0` independently; actor tiles silently overwritten by metasprite tiles when both are present | Medium — unify into single function with shared counter |
| SEED-009 | `GBDKPipeline.kt` bank file header builder | `<gbdk/metasprites.h>` only added to `main.c`; banked scene files using `move_metasprite_*` don't include it | Small — scan bank ops; add conditional include |
| SEED-010 | `MetaspriteVisitor.kt` `generateMetaspriteDescriptor()` + `generateMetaspriteFrameSwitch()` | Symbols not namespaced by metasprite ID; two metasprites → duplicate global link-time error | Medium — prefix all emitted symbols with `ms.id` |
| SEED-011 | `MetaspriteVisitor.kt` frame switch hiwater reset | `hiwater = 0` emitted per `moveMetasprite()` call; 2nd call overwrites first metasprite's OAM slots | Medium — hoist `hiwater = 0` to frame preamble, `hide_sprites_range` to frame postlude |

Verification: JVM-tier tests with a 2-metasprite fixture cover all four seeds. No visual
UAT needed (no current example triggers the bugs). New file: `TwoMetaspriteEmissionTest.kt`.


### Cluster D: Sprite transparency / tRNS outline (SEED-PHASE-13-SPRITE-OUTLINE-LOST-NONZERO-TRNS-INDEX)

**Root cause:** `ConvertSpritesTask.buildPng2AssetArgs()` passes `-keep_palette_order`
without validating that the source PNG's tRNS-declared transparent color is at palette
index 0. When tRNS is on a non-zero index (as in `elephant.png`, where the outline color
is at index 0 and the transparent color is at index 4), the outline collapses into the
transparent OBJ slot and renders see-through.

**Files:**
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/ConvertSpritesTask.kt`
  `buildPng2AssetArgs()` (~line 739-751)
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/PngUtils.kt`
  (tRNS chunk reading + palette permutation)

**Fix direction:** Read the tRNS chunk index in `PngUtils`; when tRNS index != 0, reorder
the source palette to move tRNS color to index 0 before invoking png2asset. The platformer
player sprite (`player-character-gbapduck-sprites.png`, has index-0 as the orange background,
fixed by Phase 12.9 with `-keep_palette_order`) is the regression oracle and must not regress.

Verification: metasprites elephant renders with solid dark outline + no see-through, AND
platformer player sprite remains transparent-correct. Binding visual UAT + 7-target
buildRom sweep.


### Cluster E: Platformer visitor wiring gaps (SEED-PHASE-12-PLATFORMER-VISITOR-AUTO-EMISSION-GAPS)

**Root cause group:** `PlatformerVisitor.kt` wave-7 codegen is incomplete; 4 gaps currently
papered over with `cEmit()` escape hatches in `PlatformerTemplate.kt`.

| Gap | File | Description |
|-----|------|-------------|
| Gap 1 | `PlatformerVisitor.kt` | Input → velocity wiring not auto-emitted; user workaround: inline `whenever(dpad.right.held) { playerVx set 127 }` |
| Gap 2 | `PlatformerVisitor.kt` | `platformer_camera_update()` defined but never called; user workaround: inline `cEmit("platformer_camera_update();")` |
| Gap 3 | `MetaspriteVisitor.kt` | Metasprite rendered at world position, not screen-relative; user workaround: inline camera-offset fudge via cEmit |
| Gap 4 | `PlatformerVisitor.kt` | `_walkFrameIdx` declared and read but never incremented; animation frozen at frame 0 |

Fix: remove the 4 `cEmit()` calls from `PlatformerTemplate.kt`; add the missing
auto-emission in `PlatformerVisitor.kt` (and `MetaspriteVisitor.kt` for Gap 3).

Bundles: SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY and SEED-platformer-template-spawn-polish
(per-level spawn positions, same visitor area).

Verification: all 3 platformer UAT anchors re-shoot with `cEmit` escapes removed;
platformer-template `buildRom` + binding visual evidence.


### Cluster F: Deprecation removals (SEED-023 / SEED-025)

**Root cause group:** Two deprecated APIs for removal in v0.1.1 per PROJECT.md.

| Seed | File | Root cause | Scope |
|------|------|------------|-------|
| SEED-023 | `gbkt-lang/.../ScriptBuilder.kt` | `whenever()` and `runIf()` emit identical IR; one should be deprecated with `ReplaceWith` or given distinct reactive semantics | Medium — census of `whenever` usage across examples/docs/tests; deprecation annotation; migrate call sites |
| SEED-025 | `gbkt-genre-rpg/.../dsl/RpgExtensions.kt` ~line 421 | `combatIsInState(String, String)` String overload deprecated in v0.1.0; safe to delete now that v0.1.0 is tagged | Small — delete overload, migrate stragglers, confirm S1133 closes |

Both are DSL-surface only. Zero codegen risk. Can be done in a single plans sweep,
bundling with SEED-007 (same low-risk DSL tier) and SEED-026 (Gradle hygiene).


## Component Boundaries — New vs Modified

All v0.1.1 work is MODIFICATIONS to existing files. No new modules, no new public IR nodes,
no new visitor interfaces (which would require changes in all 13 visitor implementations).

| Work type | Modification scope | New files |
|-----------|-------------------|-----------|
| S3776 extract-method refactors | Private method additions within existing visitor/pipeline classes | None |
| Seed codegen fixes | Target methods in existing visitor files | JVM-tier test files per cluster |
| Deprecation removals | Delete/annotate existing DSL functions + migrate call sites | None |
| DSL_REFERENCE.md reconciliation | Edit context/ doc file | None |
| QUAL-01..03 | Edit source files per detekt violations + screen-constant replacements | None |
| SEED-026 Gradle hygiene | Add task annotations + fix `pluginTest` dependency ordering | None |


## Data Flow — How the Byte-Identity Oracle Protects Hardening Work

```
Kotlin DSL (unchanged)
    ↓
GameIR tree (unchanged for S3776 refactors; modified for seed fixes)
    ↓
Analysis passes (unchanged for all v0.1.1 work)
    ↓
GBDKPipeline + 13 visitors
    ↓ S3776 refactors: same code path, reorganized into private helpers
    ↓ Seed fixes: corrected code path produces different (correct) C output
C AST → CEmitter → main.c / bank1.c / game.h / zone_bankN.c
    ↓
GBDK lcc → .gb ROM
    ↓
7-example buildRom sweep (oracle gate)
    S3776 work: must be byte-identical to pre-refactor output
    Seed fix work: RED→GREEN oracle transition (new correct bytes)
```

For S3776 work, the oracle asserts NO change. For seed fix work, the oracle asserts
SPECIFIC change — new byte patterns match what the fixed codegen emits, verified via
JVM-tier emission tests before the ROM sweep.


## Build Order and Work Stream Parallelism

### Parallelizable streams (no ordering dependency between them)

**Stream A: Docs / Static Analysis**
Does not touch generated C. Can start immediately and run concurrently with all others.

- A1: `context/DSL_REFERENCE.md` — prune/rewrite the 13 dead-API sections (12 "Stale-API
  caveat" callouts + the combatEngine experimental note). Each removed subsystem tracked
  as a v0.2.0 feature candidate per PROJECT.md.
- A2: QUAL-01..03 — detekt violations; platform-aware screen constants replacing magic
  numbers in `profiles/`; magic-pixel elimination in test fixtures.
- A3: S3776 cognitive-complexity burn-down — extract-method refactors across the 8 hotspot
  files listed above. Verify each batch with 7-target buildRom sweep for byte-identity.

**Stream B: Seed Triage (prerequisite for Stream D)**
Re-verify every seed against current master before any codegen fix work. Assign terminal
dispositions: CLOSED (already fixed by phases 12.6-13.8), OPEN-v0.1.1 (needs a fix phase),
or DEFERRED-v0.2.0 (explicitly out of scope per PROJECT.md). The triage report feeds all
Stream D phases. This is the only gate between the stable master baseline and codegen fixes.

**Stream C: DSL / Infrastructure (low risk)**
Can run in parallel with A and B. No codegen impact; no oracle changes.

- C1: SEED-023 + SEED-025 — deprecation removals (DSL-surface only, no C output)
- C2: SEED-007 — `GameBuilder.kt` actor palette slot fix (1-3 lines)
- C3: SEED-026 — Gradle plugin build hygiene (5 task annotations + `pluginTest` dependency)
- C4: SEED-020 — `GameIRSerializer` round-trip coverage (additive tests only)
- C5: SEED-012 — MCP `emulator_read_memory` tool (additive to `gbkt-mcp-server`)

### Sequential stream (must follow seed triage; each cluster may change oracles)

**Stream D: Codegen defect fixes**
Each cluster changes C output intentionally. Sequence minimizes oracle churn:

```
B: Seed triage completes (know which seeds are actually open on master)
    ↓
D1: Metasprite visual parity (SEED-004/005/006/013)
    touches MetaspriteVisitor.kt — establishes corrected metasprites baseline
    ↓
D2: Metasprite structural/latent (SEED-008/009/010/011)
    extends corrected metasprites codegen without re-breaking D1's baseline
    ↓
D3: Banks trio (SEED-014/015/016) — REQUIRES discuss-phase + research first
    corrects banking for all zone games; highest blast radius
    full 7-target buildRom sweep after each change
    ↓
D4: Sprite transparency (SEED-PHASE-13-SPRITE-OUTLINE)
    ConvertSpritesTask tRNS reorder; regression oracle: metasprites (D1) + platformer
    ↓
D5: Platformer visitor wiring (SEED-PHASE-12-PLATFORMER-VISITOR + SEED-021/022)
    remove cEmit escapes, add PlatformerVisitor auto-emission
    ↓
D6: Misc small/latent (SEED-002, SEED-003, SEED-017, remaining Phase-12 seeds)
```

D1 before D2: both touch `MetaspriteVisitor`. D1 fixes the byte layout; D2's symbol-
namespacing and hiwater fixes build on the corrected layout.

D3 is independent of D1/D2 but has the widest blast radius. Running it after D1/D2
means the metasprites oracle is already stable before the banking changes sweep all zone
games.

D4 after D1: verification requires checking metasprites elephant outline (D1 baseline)
remains correct while the tRNS fix applies.

D5 and D6 are independent of all prior D clusters except that the 7-target oracle must
stay green throughout.


## Anti-Patterns Specific to This Hardening Milestone

### Anti-Pattern 1: S3776 suppression instead of extract-method

What people do: add `@SuppressWarnings("CognitiveComplexity")` or Sonar "Accept" marks to the
46 findings without restructuring the code.

Why wrong: masks the structural debt. The detekt.yml already excludes `LongMethod` from
`**/codegen/**` — a second exclusion signals architectural drift, not deliberate design.

Do this instead: extract private helper methods. The resulting code is more readable and
testable, and cognitive complexity genuinely drops.

### Anti-Pattern 2: Codegen fixes without a JVM-tier RED test first

What people do: edit `MetaspriteVisitor` or `GBDKPipeline` and verify only via buildRom
plus UAT screenshot.

Why wrong: visual UAT is slow and the emulator can mask bugs. Without a JVM-tier emission
test capturing the defect first, the fix will silently regress later.

Do this instead: add a RED JVM-tier emission test asserting the wrong current behavior
BEFORE making the code change. Turn it GREEN. Then run the buildRom sweep.

### Anti-Pattern 3: Fixing seed clusters out of triage order

What people do: pick a seed that looks simple and fix it without first verifying whether it
was already closed by Phases 12.6-13.8.

Why wrong: wasted effort and potential oracle churn. At least 4-7 of the 44 seeds have
explicit "RESOLVED" or "closed" markers, and more may have been silently closed by Phase 13.x
work (particularly the SEED-PHASE-13-PLATFORMER-INVERTED-PALETTE seed, which Phase 13.8
palette hardening may have fully addressed).

Do this instead: run seed triage first (Stream B). Build a disposition table. Only open fix
phases for seeds with confirmed OPEN status on current master.

### Anti-Pattern 4: S3776 refactors mixed with codegen fixes in the same commit

What people do: extract a helper method AND fix a bug in the same commit.

Why wrong: makes it impossible to verify the refactor was zero-C-change, because the bug fix
also changes C output. The oracle cannot distinguish "intended change" from "accidental regression."

Do this instead: separate commits. S3776 extract-method commits must be provably zero-C-change
(byte-identical buildRom sweep). Bug fix commits intentionally change C output (verified by the
RED→GREEN oracle cycle).

### Anti-Pattern 5: Sealed-interface dispatch as a "simplification" of visitor methods

What people do: refactor a visitor that dispatches on `SystemIR` subtypes using sealed `when`
to "simplify" the branching.

Why wrong: the project explicitly chose non-sealed interfaces + visitor dispatch to enable the
multi-module split. Sealing `SystemIR` would force all genre-specific `SystemIR` implementations
back into `gbkt-ir`, collapsing the module boundary.

Do this instead: extract private helper methods within the visitor. Keep the visitor contract
(the interface + `accept()` signature) untouched.


## Integration Points

### Internal Boundaries — What Each Stream Touches

| Boundary | Stream A | Stream C | Stream D |
|----------|----------|----------|----------|
| `gbkt-ir` (IR node types) | None | None | None — no new IR nodes |
| `gbkt-lang` DSL builders | None | SEED-007 (GameBuilder.kt), SEED-023 (ScriptBuilder.kt) | None |
| `gbkt-analysis` passes | None | None | None — passes unchanged |
| `gbkt-backend-gbdk` visitors | S3776 extract-method | None | SEED-004..011, 014, 015 fixes |
| `gbkt-backend-gbdk` pipeline | S3776 extract-method | None | SEED-014, 015 gate + trampoline fix |
| `gbkt-genre-rpg` | None | SEED-025 (RpgExtensions.kt) | None |
| `gbkt-genre-platformer` | S3776 (PlatformerVisitor) | None | SEED-PHASE-12 wiring gaps (SEED-021/022) |
| `gbkt-gradle-plugin` tasks | S3776 (3 tasks) | SEED-026 (task annotations + pluginTest race) | SEED-PHASE-13-SPRITE-OUTLINE (ConvertSpritesTask) |
| `context/DSL_REFERENCE.md` | A1 (13 sections) | None | None |
| `gbkt-mcp-server` | None | SEED-012 (new tool) | None |

### Oracle Protection Contract

Every phase in v0.1.1 that touches `gbkt-backend-gbdk` or `gbkt-gradle-plugin` MUST run
the ROM-build smoke test before declaring complete (per `feedback_rom_build_smoke_test_for_codegen_phases`):

```bash
./gradlew :gbkt-examples:pong:clean :gbkt-examples:pong:buildRom \
          :gbkt-examples:breakout:buildRom \
          :gbkt-examples:simple-physics:buildRom \
          :gbkt-examples:metasprites:buildRom \
          :gbkt-examples:banks:buildRom \
          :gbkt-examples:platformer-template:buildRom
```

S3776 refactor phases: all ROMs must be byte-identical to pre-refactor output.
Seed fix phases: the target example's ROM changes intentionally (RED→GREEN oracle);
other examples must be byte-identical (no unintended cross-example regression).

## Sources

- Codebase analysis: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/` (all visitor and pipeline files, line counts and function maps)
- `context/ARCHITECTURE.md` — module dependency graph and visitor pattern rationale
- `.planning/PROJECT.md` — v0.1.1 milestone scope, active requirements, out-of-scope items
- All 44 seed files in `.planning/seeds/` — defect root causes, file locations, blast radii
- `gbkt-backend-gbdk/CLAUDE.md`, `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/CLAUDE.md` — pipeline architecture decisions
- `detekt.yml` — current exclusion rules and active complexity settings

---
*Architecture research for: gbkt v0.1.1 Hardening milestone*
*Researched: 2026-06-12*
