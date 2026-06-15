# Phase 21: Codegen Fixes — Platformer and Remaining Seeds - Research

**Researched:** 2026-06-14
**Domain:** Platformer DSL/codegen (FIX-05), contained DSL/IR refactors (FIX-06), seed disposition (TRIAGE-03)
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01 — Scope (Criterion 5, literal):** Every file under `.planning/seeds/` reaches a terminal disposition in Phase 21. Each seed is either fixed-in-place (archived) or moved to `.planning/backlog/v0.2.0/` with a one-line rationale.

**D-02 — Seeds that land in Phase 21 (real fixes):**
SEED-021 pivot_adjust DSL lift (FIX-05), spawn-polish pair (SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY + SEED-platformer-template-spawn-polish, same root cause), SEED-PHASE-13 sub-pixel sink (FIX-05), SEED-020 GameIRSerializer round-trip (FIX-06), SEED-022 tilemap-collision predicate consolidation (FIX-06), SEED-027 GBC bitsPerPixel (byte-identical), SEED-028 ConfigBuilder migration note (doc-only), SEED-029 whenever→runIf cosmetic doc/KDoc sweep.

**D-03 — Re-defer to `.planning/backlog/v0.2.0/` with evidence:**
SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION (wide blast radius), SEED-017 (sport-zone dual pipeline), SEED-023 (whenever→runIf needs deprecation cycle), SEED-025 (remove deprecated combatIsInState String overload — v0.2.0 by design, can't remove until one release after deprecation).

**D-04 — Scope-change flag for planner:** D-03 re-defers two seeds (ZONE-MAGIC-STRING, SEED-017) that ROADMAP/REQUIREMENTS list under "active FIX-06 scope". Criterion 3 permits "re-deferred with evidence". Planner MUST: (a) update REQUIREMENTS.md FIX-06 disposition + status, (b) physically move four D-03 seeds to `.planning/backlog/v0.2.0/`, (c) leave evidence note.

**D-05 — SEED-021 pivot_adjust DSL lift:** Lift resolution into `tilemapCollision { }` builder; visitor consumes a resolved config value; delete metasprite lookup dance + fallback companion constants. No magic strings. Keep validation diagnostic. Visual regression gate: grounded-player GBC screenshot. Pairs with SEED-022.

**D-06 — Spawn-polish:** Per-zone spawn ownership; `ZoneBuilder.spawn()` already exists at WorldBuilders.kt:247; fix is most likely wiring the platformer-template to call `spawn()` with correct world1Area1 bottom-ground-row coords, NOT adding a new DSL primitive.

**D-07 — SEED-PHASE-13 sub-pixel sink:** Investigate-then-decide. Run diagnostic ladder: inspect player PNG gutters, verify `buildVerticalFootProbe` snap arithmetic, check `_playerY >> 4` floor-vs-round. Fix if real off-by-one, close as-accepted with sign-off if intended. JVM emission test required regardless.

**D-08 — SEED-020:** Deserialize 10 stubbed IR collections + author round-trip tests in `gbkt-ir`. No codegen blast radius.

**D-09 — SEED-022:** Consolidate duplicated `gameUsesTilemapCollision` predicate into `gbkt-backend-api`. Small; alongside D-05.

**D-10 — SEED-027:** `TargetProfiles.GAME_BOY_COLOR_SCREEN bitsPerPixel = 4 → 2` + KDoc. Zero readers → byte-identical.

**D-11 — SEED-028:** ConfigBuilder property→function setter migration note + 4 stale doc/comment strings. Doc-only.

**D-12 — SEED-029:** `whenever`→`runIf` cosmetic doc/KDoc sweep (~25 files). Pure docs, no compile impact.

**D-13 — Oracle (locked):** Unchanged-set guard (byte-identity for examples NO fix touches) + targeted proof (UAT visual re-shoots + JVM emission tests for changed examples). pong stays PASS*.

**D-14 — Sequencing (locked):** Fix-first, then re-shoot all 3 GBC anchors against final ROM. One capture pass, post-fix.

**D-15 — UAT capture (inherited):** JVM `*UatTest` StepAgent `captureAndRename()` harness, `gbcMode=true` + `.noi` symFile, ROM rebuilt clean before capture.

**D-16 — Commit discipline:** Phase 21 commits contain only seed-closure work; zero PR-#77 / S3776 refactors interleaved. Run `:module:spotlessApply :module:detekt` per-commit.

### Claude's Discretion

Exact test method/assertion names, evidence PNG filenames, hashing commands for byte-identity diffs, the precise diagnostic order for SEED-PHASE-13, and whether spawn-polish needs any new DSL at all (D-06 — likely not) are left to the planner/executor.

### Deferred Ideas (OUT OF SCOPE)

Merging PR #77 (S3776 cognitive-complexity burn-down) — assess after this phase closes; not Phase 21 work.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FIX-05 | Platformer seeds: SEED-021 pivot_adjust DSL lift; spawn-polish pair; SEED-PHASE-13 sub-pixel sink; UAT re-verification of cEmit closure | D-05/D-06/D-07 findings below; UAT harness confirmed in-tree |
| FIX-06 | Small DSL/tooling seeds: SEED-020 serializer stubs; SEED-022 predicate consolidation; SEED-027/028/029 doc residuals; SEED-017/ZONE-MAGIC-STRING/SEED-023/SEED-025 re-deferred with evidence | D-08..D-12 findings + D-03/D-04 re-deferral mechanics below |
</phase_requirements>

---

## Summary

Phase 21 is a **mixed fix + confirmation phase** that closes the final v0.1.1 milestone. Research confirms two high-priority findings that reshape the planning burden:

**Critical finding #1 (D-06 spawn-polish):** `ZoneBuilder.spawn(x: UByte, y: UByte)` is fully wired end-to-end (`WorldBuilders.kt:247` → `ZoneIR.spawnX/spawnY` → `GBDKPipeline.buildLevelSpawnTablesIfNeeded` → `_level_spawn_x[]/_level_spawn_y[]` → `setup_current_level()` body). PlatformerTemplate.kt lines 272/277/282 already call `spawn(40u, 120u)` on all three zones. Triage evidence (Phase 16, capture-note.txt) confirms `playerY=96, grounded=1` at frame 93 — player IS visually standing on the ground row. The seed is FUNCTIONALLY FIXED but marked CONFIRMED-OPEN for "visual polish of exact spawn position." The D-14 re-shoot (post D-05/D-07 fixes) is the binding evidence needed for closure. **No new DSL primitive needed.**

**Critical finding #2 (D-07 sub-pixel sink):** Player PNG has NO transparent gutters (all rows of all 6 frames are 24/24 non-transparent pixels). The snap arithmetic in `buildVerticalFootProbe` uses the `height` parameter from `hitboxH = 24` (the VISIBLE/hitbox height, not a separate collision-mask height). The `_playerY >> 4` floor produces exact integers at grounded equilibrium. The code comment at PlatformerVisitor.kt:1394–1406 documents the correct round-trip: `spawn_y=120, height=24 → foot_tile_row=18, foot_pixel_top=144, pivot_adjust=2, foot_pixel_anchor=118, posYSym=1888 → rendered metasprite-bottom at 118-6+32=144 (lands on tile-row-18 top — zero pixel gap). Grounded equilibrium: player_real_y=102, hitbox foot=126, rendered metasprite-bottom=128`. **Evidence points to INTENDED / ALREADY CORRECT behavior.** However, per D-07, a re-shoot after all fixes is required for binding visual sign-off, and a JVM emission test for `_player_y` snap arithmetic is required regardless.

**Additional finding (SEED-027 and SEED-028):** Both are ALREADY FIXED by Phase 18 (plans 18-05 and 18-12 respectively). `TargetProfiles.GAME_BOY_COLOR_SCREEN` already shows `bitsPerPixel = 2` and correct KDoc (TargetProfiles.kt:53). All 4 stale `ramBanks`/`romBanks` guidance strings are already corrected. These seeds must be **CLOSED AS VERIFIED-ALREADY-FIXED and archived**, not re-implemented.

**Primary recommendation:** Plan the phase in the order D-05/D-09 (pivot_adjust + predicate consolidation, same visitor), then D-06/D-07 (spawn-polish re-shoot + sub-pixel investigation), then D-14 (GBC anchor re-shoot), then D-08 (serializer), then D-10/D-11/D-12 (doc residuals), then D-03/D-04 (re-deferrals). Seeds SEED-027 and SEED-028 close in Wave 0 as "VERIFIED-ALREADY-FIXED."

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| pivot_adjust resolution (D-05) | `gbkt-genre-platformer` DSL builder (`TilemapCollisionBuilder`) | `PlatformerVisitor` (consumer) | Builder is the single source of truth per Project Rule #1; visitor becomes a dumb consumer |
| gameUsesTilemapCollision predicate (D-09) | `gbkt-backend-api` (shared utility) | `GBDKPipeline` + `PlatformerVisitor` (callers) | Only common module both depend on without introducing a new dep direction |
| GameIR serializer stubs (D-08) | `gbkt-ir` (`GameIRSerializer.kt`) | External tooling (IDE plugin, MCP describe) | No codegen pipeline dependency; tooling-only consumer |
| spawn table codegen (D-06) | `GBDKPipeline.buildLevelSpawnTablesIfNeeded` | `ZoneBuilder.spawn()` (DSL input) | Already complete; re-shoot is the only remaining work |
| sub-pixel snap arithmetic (D-07) | `PlatformerVisitor.buildVerticalFootProbe` | Player metasprite geometry | Accept-or-fix verdict determines if any production code changes |
| GBC constant correction (D-10) | `gbkt-core/constraints/TargetProfiles.kt` | — | Zero consumers; byte-identical; ALREADY FIXED |
| ConfigBuilder migration note (D-11) | Various doc/comment strings | — | ALREADY FIXED in Phase 18 plan 18-12 |
| whenever doc sweep (D-12) | README.md + ~25 KDoc/comment files | — | Open; ~12 occurrences in README.md confirmed |
| Re-deferrals (D-03/D-04) | `.planning/backlog/v0.2.0/` | REQUIREMENTS.md update | File moves + evidence notes |

---

## Decision Cluster: D-05/D-09 — pivot_adjust DSL lift + predicate consolidation

### D-05: SEED-021 pivot_adjust DSL lift

**Current state (confirmed by source inspection):**

`PlatformerVisitor.buildTilemapPhysicsUpdateFunction` (PlatformerVisitor.kt:600–651) resolves `pivot_adjust` via a "metasprite lookup dance":
1. Fetches `tcPosYVar = tcSystem?.config?.get("posYVar") as? String` (line 629)
2. Finds `playerMetasprite` = first metasprite whose `posYVarName == tcPosYVar`, or first with full geometry (lines 631–639)
3. Computes `pivotAdjust = (frameH - pivotY - height).coerceAtLeast(0)` (line 651) using either the matched metasprite's geometry OR fallback companion constants `REFERENCE_FRAME_HEIGHT = 32`, `REFERENCE_PIVOT_Y = 6` (PlatformerVisitor.kt:104–105 in the companion object at line 86)

The `SEED-021` deferred marker is at lines 626–628 (call-site comment) and 1294–1297 (KDoc on `buildVerticalFootProbe` parameter).

`TilemapCollisionBuilder` (PlatformerExtensions.kt:581–683) currently has NO `pivotAdjust` setter. Its `build()` method emits a `GenericSystem` config map with keys: `type`, `posXVar`, `posYVar`, `vxVar`, `vyVar`, `groundedVar`, `hitboxX`, `hitboxY`, `hitboxW`, `hitboxH`, `solidThreshold`.

**Recommended approach (D-05):**

1. Add `pivotAdjust(Int)` setter to `TilemapCollisionBuilder` — stores value under config key `"pivotAdjust"`. Default = `null` (absent from map when not set, preserving back-compat for callers that don't set it).
2. Update `TilemapCollisionBuilder.build()` to include `pivotAdjust?.let { configBuilder["pivotAdjust"] = it }`.
3. In `PlatformerVisitor.buildTilemapPhysicsUpdateFunction`, replace the metasprite lookup dance (lines 629–651) with: `val pivotAdjust: Int = (tcSystem?.config?.get("pivotAdjust") as? Int) ?: run { /* fallback: same companion constants */ (REFERENCE_FRAME_HEIGHT - REFERENCE_PIVOT_Y - height).coerceAtLeast(0) }`.
4. Keep the validation diagnostic: if `tilemapCollision` is bound but `pivotAdjust` is not set, emit a `System.err.println("WARNING: tilemapCollision bound but no pivotAdjust declared; using fallback geometry (32, 6)")`.
5. Wire PlatformerTemplate.kt to call `pivotAdjust(2)` in its `tilemapCollision { }` block (derived from frameSize(24,32), pivot(12,6), hitbox h=24: `32 - 6 - 24 = 2`).
6. Delete the companion constants `REFERENCE_FRAME_HEIGHT` and `REFERENCE_PIVOT_Y` only after confirming the fallback in step 3 still produces the same numeric output (or move constants to the fallback path within the lambda).
7. Regression gate: all 4 existing genre-platformer EmissionTests must stay GREEN (they use minimal IR without a metasprite, so the fallback fires); platformer-template `buildRom` + GBC anchor screenshot (D-14).

**No magic strings:** The `pivotAdjust(Int)` setter takes an `Int`, not a String reference. Project Rule #1 is satisfied because the value is a numeric geometry constant, not a DSL name.

**Files to touch:**
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/dsl/PlatformerExtensions.kt` (add setter to `TilemapCollisionBuilder`, line ~654 area)
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` (replace lines 629–651; remove companion constants at 104–105; remove deferred markers)
- `gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/platformer_template/PlatformerTemplate.kt` (add `pivotAdjust(2)` to the `tilemapCollision { }` block)

**RISK:** MEDIUM. The existing fallback path is identical to the companion constants, so byte-identity for test fixtures is guaranteed. The only behavioral change is platformer-template with `pivotAdjust(2)` explicitly set — which reproduces the exact same value the lookup dance computed.

### D-09: SEED-022 predicate consolidation

**Current state (confirmed by source inspection):**

Two private implementations of `gameUsesTilemapCollision(gameIR: GameIR): Boolean`:
- `GBDKPipeline.gameUsesTilemapCollision` at GBDKPipeline.kt:2207 — three paths (Path C: `type=="tilemap_collision"` GenericSystem; Path A: `platformer_physics` GenericSystem reflective `solidThreshold`; Path B: per-zone `platformerPhysicsOverride["solidThreshold"]`)
- `PlatformerVisitor.gameUsesTilemapCollision` at PlatformerVisitor.kt:1664 — two paths (Path A: typed `PlatformerPhysicsConfig.solidThreshold` direct access; Path B: zone override) — missing Path C!

Deferred marker: PlatformerVisitor.kt:1590–1592.

**Dependency direction:** `gbkt-genre-platformer` depends on `gbkt-backend-api`. `gbkt-backend-gbdk` also depends on `gbkt-backend-api`. `gbkt-backend-gbdk` does NOT depend on `gbkt-genre-platformer`. This makes `gbkt-backend-api` the correct landing site for a shared utility.

**Recommended approach (D-09):**

Option A (recommended per SEED-022 scope sketch): Add a companion or top-level function `gameUsesTilemapCollision(gameIR: GameIR): Boolean` in `gbkt-backend-api` that detects Path C (the `"tilemap_collision"` GenericSystem type string) and Path B (zone override). The typed `PlatformerPhysicsConfig.solidThreshold` Path A access can only live in `gbkt-genre-platformer` (compile-time dependency). So the shared utility covers the Path-C early return; both callers fall through for their Path-A check.

Option B (cheaper): Extract only the duplicated GenericSystem-config-key path (the `"solidThreshold"` key string) into a shared constant in `gbkt-backend-api`. Both visitors still have their own predicate methods but reference the constant.

Option C (lock-step contract test — always required): Regardless of A or B, add a `TilemapCollisionPredicateLockstepTest` in `gbkt-genre-platformer` that runs both predicates over a matrix of `GameIR` fixtures (one with tilemap_collision system, one with platformer_physics system, one with per-zone override, one with none) and asserts identical verdicts.

**VERDICT:** Implement Option A + Option C. The shared Path-C detection moves to `gbkt-backend-api`; both callers call the shared util for Path C and handle Path A locally. This is the minimum shared utility that eliminates the lockstep risk on the most-likely-to-drift path (when new games start using `tilemapCollision { }` instead of the legacy Path A).

**Files to touch:**
- `gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/` (new file: `TilemapCollisionGate.kt`, or add to `GenreSystemVisitor.kt`)
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipeline.kt` (GBDKPipeline.gameUsesTilemapCollision delegates Path C to shared util)
- `gbkt-genre-platformer/src/main/kotlin/io/github/gbkt/genre/platformer/codegen/PlatformerVisitor.kt` (PlatformerVisitor.gameUsesTilemapCollision delegates Path C to shared util; remove deferred marker)
- New test file in `gbkt-genre-platformer/src/test/kotlin/...` (lockstep contract test)

**RISK:** LOW. The predicate has existed in both places for months; touching only the Path-C detection (the new path added in Phase 12.1) keeps Path A/B behavior exactly unchanged for existing tests.

---

## Decision Cluster: D-06/D-07 — Spawn-polish and Sub-pixel sink

### D-06: Spawn-polish — VERDICT: pure wiring, no new DSL; spawn is already set to (40, 120)

**Full consumption chain (VERIFIED):**

```
ZoneBuilder.spawn(x: UByte, y: UByte)          [WorldBuilders.kt:247]
  → ZoneIR.spawnX = x, spawnY = y              [WorldBuilders.kt:271-272]
  → GBDKPipeline.buildLevelSpawnTablesIfNeeded  [GBDKPipeline.kt:2697]
      reads zone.spawnX, zone.spawnY            [GBDKPipeline.kt:2704-2715]
      emits: const UINT8 _level_spawn_x[] = {40u,...}
             const UINT8 _level_spawn_y[] = {120u,...}
  → setup_current_level() case body             [GBDKPipeline.kt:2625-2626]
      $posXSym = ((INT16)_level_spawn_x[idx]) << 4;
      $posYSym = ((INT16)_level_spawn_y[idx]) << 4;
  → called on gameplay_enter via bindCurrentLevel() [PlatformerTemplate.kt:400]
```

**Current state of PlatformerTemplate.kt (CONFIRMED):**

Lines 269–282 already declare:
```kotlin
val world1Area1Zone by zone {
    tileset(...); tilemap(...)
    spawn(40u, 120u)   // line 272
}
val world1Area2Zone by zone {
    tileset(...); tilemap(...)
    spawn(40u, 120u)   // line 277
}
val world2Area1Zone by zone {
    ...
    spawn(40u, 120u)   // line 282
}
```

**Phase 16 triage evidence (CONFIRMED):** `capture-note.txt` in `.planning/phases/16-seed-triage/evidence/SEED-platformer-template-spawn-polish/` states: `playerX=128, playerY=96, grounded=1, playerVy=0` at frame 93. Player IS standing on ground tile row. Screenshot (verified visually) shows the duck sprite standing on green ground tiles at the left side of world1area1.

**VERDICT: No new DSL needed. The spawn-polish seed is FUNCTIONALLY FIXED.** The CONFIRMED-OPEN triage status reflects "visual polish remains open" — meaning the D-14 re-shoot (after D-05/D-07 fixes are applied) is the binding evidence needed for final visual sign-off. If the re-shoot confirms the player stands cleanly on the ground row, both spawn-polish seeds (SEED-PHASE-12-PLATFORMER-SPAWN-POSITION-CLARITY + SEED-platformer-template-spawn-polish) close as FIXED.

**Planner action:** No code changes for D-06. The plan for D-06 is: rebuild ROM (mandatory after D-05/D-07 changes), run anchor-2 re-shoot, capture screenshot showing player on ground, user visual sign-off. Both spawn-polish seeds archive on sign-off.

**One open question:** The GBDK reference `SetupPlayer()` uses `playerX=40<<4; playerY=40<<4` (Y=40 pixels from top). gbkt uses Y=120. This is intentional (the comment at PlatformerTemplate.kt:255–262 explains: "Y=120 pixels is the gbkt deviation locked in evidence/reference-toolchain-notes.md — places the player on the visible ground row rather than relying on a fall-onto-floor startup"). If the re-shoot still shows a visual concern, the planner may choose to change Y=120 to the correct bottom-ground-row pixel value for world1Area1. Research cannot determine this without running the ROM (visual truth). Flag as deferred to executor's judgment.

### D-07: Sub-pixel sink — VERDICT: evidence points to INTENDED/ALREADY CORRECT; close as-accepted after re-shoot

**Diagnostic ladder results:**

**(a) Player PNG gutters:** `player-character-gbapduck-sprites.png` is 144×64px. All 6 frames (24×32 each) have **zero transparent gutters** — top row (y=0) and bottom row (y=31) of every frame have 24/24 non-transparent pixels. H3 (asset-encoding offset) is **disproven**.

**(b) `buildVerticalFootProbe` snap arithmetic:** Uses the `height` parameter which comes from `hitboxH = 24` (the hitbox height from `TilemapCollisionBuilder.hitbox(0, 0, 8, 24)` — the user-declared visible hitbox, not a separate collision-mask height). The snap formula at PlatformerVisitor.kt:1342–1412:
```
foot_tile_row = (player_real_y + height) >> 3          // height = 24
foot_pixel_top = foot_tile_row << 3
pivot_adjust = 2u (companion constant, will become config value after D-05)
foot_pixel_anchor = foot_pixel_top - height - pivot_adjust
posYSym = foot_pixel_anchor << 4
```
The code comment (lines 1394–1406) documents the full round-trip: `spawn_y=120, height=24 → foot_tile_row=18, foot_pixel_top=144, pivot_adjust=2, foot_pixel_anchor=118, posYSym=1888 → rendered metasprite-bottom at 118-6+32=144 (zero pixel gap)`. H2 (collision-mask off-by-one) is **disproven** — the snap uses visible hitbox height.

**(c) `_playerY >> 4` floor vs round:** At grounded equilibrium `posYSym = 1888`, `player_real_y = 1888 >> 4 = 118` (exact integer division, no fractional bits). H1 (sub-pixel rounding) does not apply at equilibrium. During physics integration, gravity adds fractional sub-pixel velocity, but the snap fires synchronously when the foot probe hits a tile, resetting `posYSym` to a tile-aligned exact value.

**CONCLUSION:** No real off-by-one exists. The 1-2px sink observation in Phase 12.8 was under palette-inverted GBC conditions (grass tileset white pixels made spatial alignment ambiguous). After Phase 12.9 palette fix + Phase 13.7 OBJ polarity fix, the visual should be clean. Per D-07 mandate: add a JVM emission test for `_player_y` initial value + snap arithmetic regardless, and re-shoot after D-05 fixes for binding visual sign-off.

**Required regardless of fix-vs-accept:** JVM emission test asserting:
1. `foot_pixel_anchor` formula in `buildVerticalFootProbe` uses `hitboxH` value (not a separate collision-mask constant)
2. With `spawn_y=120, hitboxH=24, pivotAdjust=2`: the snapped `posYSym` value equals `(((120+24)>>3)<<3 - 24 - 2) << 4 = 118 << 4 = 1888`

This test belongs in `gbkt-genre-platformer/src/test/kotlin/` as a `PlatformerSnapArithmeticEmissionTest`.

---

## Decision Cluster: D-08 — SEED-020 GameIRSerializer Round-Trip

**Current state (confirmed):** `GameIRSerializer.deserialize()` at `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt:179–188`:
```kotlin
systems = emptyList(), // SEED-020: SystemIR deserialization
zones = emptyList(), // SEED-020: ZoneIR full deserialization
flags = emptyList(), // SEED-020: GlobalFlagsIR full deserialization
itemCategories = emptyList(), // SEED-020: ItemCategoryDef full deserialization
items = emptyList(), // SEED-020: ItemDef full deserialization
containers = emptyList(), // SEED-020: ContainerIR full deserialization
dropTables = emptyList(), // SEED-020: DropTableIR full deserialization
puzzleObjects = emptyList(), // SEED-020: PuzzleObjectIR full deserialization
collisionGroups = emptyList(), // SEED-020: CollisionGroupIR full deserialization
collisionRules = emptyList(), // SEED-020: CollisionRuleIR full deserialization
```

The serialize side (lines 110–136) emits all 10 collections. The asymmetry is undocumented at the API surface.

**Confirmed blast radius:** `gbkt-ir` only. Consumed by external tooling (IDE plugin, MCP describe) with NO compile-pipeline codegen blast radius. The `GameIRSerializer` is used for external tool discovery, NOT for the Kotlin → C compilation path.

**Recommended approach (per D-08 locked decision and SEED-020 "option 2"):** Implement the explicit one-way contract approach — it is cheaper and honest:
1. Add KDoc to `GameIRSerializer.serialize()` and `deserialize()` explicitly listing which collections survive round-trip and which are serialize-only (with justification for each stub).
2. For the 8 "simple" collections (flags, itemCategories, items, containers, dropTables, puzzleObjects, collisionGroups, collisionRules) that serialize only `id` and `type`, implement full deserializers using the existing `deserializeList` pattern at lines 170–176. Each is a straightforward `JSONObject` → data class mapping. These are the "simple" ones.
3. For `zones` (complex ZoneIR with nested data): implement a minimal deserializer that recovers `id`, `spawnX`, `spawnY`, `screenMode` (the fields external tools need for zone description). Flag remaining fields as unsupported in KDoc.
4. For `systems` (SystemIR open interface — hardest): implement `deserializeSystemIR` that recognizes the subset of types with a `{ id, type }` stub shape and reconstructs only `GenericSystem` with the stored config. Document that full `CombatEngineSystem` etc. cannot round-trip from the current stub shape.
5. Author a round-trip test in `gbkt-ir` over a maximal `GameIR` fixture with all 10 collections populated: `deserialize(serialize(gameIR))` must produce a structurally valid (non-null) `GameIR` where the deserialized collections are non-empty and the IDs match the input.

**Note:** The SEED says "full round-trip" (option 1) is acceptable but hard due to `SystemIR` being an open interface. The locked D-08 decision says "deserialize the 10 stubbed IR collections + author round-trip tests." Research confirms option 2 (explicit documented contract) is fully consistent with D-08 because D-08 says "round-trip tests" — the tests will lock the SUPPORTED subset. Planner should choose the supported-subset approach to avoid the `SystemIR` open-interface complexity.

**Risk:** LOW. Contained to `gbkt-ir`. No codegen pipeline touch.

---

## Decision Cluster: D-10/D-11/D-12 — Doc/Constant Residuals

### D-10: SEED-027 — GBC bitsPerPixel

**VERDICT: ALREADY FIXED by Phase 18 plan 18-05.**

`TargetProfiles.kt` currently reads:
```kotlin
val GAME_BOY_COLOR_SCREEN = ScreenSpec(
    width = 160, height = 144,
    bitsPerPixel = 2,        // was 4 before Phase 18 — already corrected
    ...
)
```
The KDoc at lines 44–48 already says "2 bits per pixel, color via 8 hardware palettes... GBC color depth comes from per-tile palette attributes, not deeper tile data — tiles are always 2bpp, same as DMG."

The seed file `SEED-027-gbc-screen-bitsperpixel-correctness.md` was not updated after the fix. `ScreenSpec.bitsPerPixel` has zero readers (confirmed by grep).

**Planner action:** Close SEED-027 as VERIFIED-ALREADY-FIXED (same pattern as Phase 19 FIX-01/FIX-02 seeds). Archive the seed file. No code change needed.

### D-11: SEED-028 — ConfigBuilder migration note

**VERDICT: ALREADY FIXED by Phase 18 plan 18-12.**

All 4 stale `config { ramBanks = N }` / `romBanks = N` guidance strings enumerated in the seed were corrected:
- `GbktExtension.kt:166`: KDoc now correctly says `config { ramBanks(N) }` (function-setter form, verified)
- `CompileRomTask.kt` comment: no property-setter form found in source
- `PlatformerTemplate.kt:61`: comment uses `romBanks(8)` (function form, verified)
- `MetaspriteEmissionTest.kt:44`: no stale property-setter comment found

The seed file was not updated after the fix.

**Planner action:** Close SEED-028 as VERIFIED-ALREADY-FIXED. Archive the seed file. No code change needed.

### D-12: SEED-029 — whenever→runIf cosmetic doc/KDoc sweep

**VERDICT: GENUINELY OPEN. ~12+ occurrences confirmed in README.md alone.**

Files with `whenever(` references (confirmed by grep, excluding build/ and .planning/):
- `README.md` — 12 occurrences (lines 24-29 show `whenever(dpad.right.held)`, etc. — user-facing DSL examples)
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/Expr.kt`
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOp.kt`
- `gbkt-backend-gbdk/CLAUDE.md`
- `gbkt-backend-gbdk/.../ExprVisitor.kt`
- `gbkt-lang/CLAUDE.md`
- `gbkt-lang/.../CombatEngineBuilder.kt`, `GameBuilder.kt`, `ActorBuilder.kt`, `CollectionBuilders.kt`, `WorldBuilders.kt`, `UIBuilders.kt`, `SceneBuilder.kt`
- `gbkt-genre-platformer/src/test/kotlin/.../LevelCardSceneBuilderTest.kt`, `PlatformerInputEmissionTest.kt`
- `gbkt-genre-sport/.../SportVisitor.kt`
- `gbkt-genre-rpg/.../domain/CombatStates.kt`
- `gbkt-core/.../References.kt`
- `CHANGELOG.md`, `CONTRIBUTING.md`

The `gbkt-cli` templates and `gbkt-intellij-plugin` templates were already fixed in Phase 18 (plans 18-29/18-30) — do NOT re-touch those.

**Approach (D-12):** Per-file grep and replace `whenever(` → `runIf(` in DSL examples and KDoc. Preserve the English word "whenever" in prose descriptions (e.g., "whenever the player moves..." is NOT in scope). Test comments in platformer tests that use `whenever` may legitimately keep the historical name (per-file judgment). A `./gradlew build` suffices (no ROM sweep needed — KDoc-only changes).

---

## Decision Cluster: D-03/D-04 — Re-deferrals

### Seeds moving to `.planning/backlog/v0.2.0/`

Four seeds in `D-03`:

| Seed | Rationale for re-deferral | Evidence note |
|------|--------------------------|--------------|
| SEED-ZONE-MAGIC-STRING-DELEGATE-MIGRATION | Wide blast radius: touches `gbkt-lang`, `gbkt-engine` IR, and every `zone()` call site across ≥7 games + all test fixtures. Needs its own discuss/spec phase. Rule #1 violation tracked. | `PickupBuilder.kt:229` retains the separate `zone(id: String, pickupId: String)` overload; `GameBuilder.kt:480` retains `zone(id: String, block)` |
| SEED-017 | Moderate refactor; sport genre dual-pipeline; no shipping example exercises it heavily. `INV-8` lock-test must stay GREEN through any future unification. | `SportVisitor.kt buildBuiltinTrackTilesetVarDecl()` KDoc cites SEED-017; test `INV-8` present |
| SEED-023 | `whenever`→`runIf` DSL unification needs a full deprecation cycle. DEPR-01 in Phase 18 already removed the functional `whenever` API from code paths; this seed is about giving it proper semantics or formally deprecating it. | `ScriptBuilder.whenever()` KDoc cites SEED-023; wherever() is currently `@Deprecated` per Phase 18 |
| SEED-025 | `combatIsInState(String,String)` overload explicitly scheduled for v0.2.0 removal (can't remove until one release after v0.1.0 deprecation). SonarCloud S1133 intentionally open. | `RpgExtensions.kt:~421` has `@Deprecated(ReplaceWith(...))` |

**D-04 REQUIREMENTS.md update:** FIX-06 active scope (REQUIREMENTS.md line 48) currently lists "SEED-017/020/022 + ZONE-MAGIC-STRING." The planner must update REQUIREMENTS.md to reflect:
- SEED-017 → RE-DEFERRED v0.2.0 (not in Phase 21 scope)
- SEED-ZONE-MAGIC-STRING → RE-DEFERRED v0.2.0 (not in Phase 21 scope)
- SEED-020 → FIXED (Phase 21)
- SEED-022 → FIXED (Phase 21)
- FIX-06 status → Complete (after all Phase 21 plans execute)

**Backlog precedent:** `.planning/backlog/v0.2.0/` already contains `SEED-003`, `SEED-PHASE-12-ONE-WAY-TILE`, `SEED-PHASE-12-SHARED-TILESET`, `SEED-PHASE-12-PER-ZONE-TILEMAP-BANKS` (moved by Phase 16 Plan 10). The four D-03 seeds follow the same file-move pattern — copy the seed file from `seeds/` to `backlog/v0.2.0/`, add a one-line "Re-deferred: [rationale]" header, delete from `seeds/`.

---

## Decision Cluster: D-13/D-14/D-15 — Oracle / Evidence

### Byte-identity oracle (D-13): two-tier

**Untouched example set** (byte-identity guard applies — these are COLLATERAL DRIFT PROOFS):
- `gbkt-examples/pong` — PASS* (toolchain non-determinism; hash diverges between builds; treat as PASS*)
- `gbkt-examples/breakout`
- `gbkt-examples/simple-physics`
- `gbkt-examples/metasprites`
- `gbkt-examples/metasprites-stress`
- `gbkt-examples/banks`

For each: `./gradlew :gbkt-examples:<name>:generateC` before and after Phase 21 changes; diff `build/gbkt/generated/` — must be byte-identical. (No `buildRom` needed for byte-identity guard; `generateC` suffices and is faster.)

**Changed example** (targeted proof):
- `gbkt-examples/platformer-template` — changed by D-05 (pivotAdjust config key added to tilemapCollision config; visitor reads it; new `pivotAdjust(2)` call in DSL). Proven by: UAT visual re-shoots (D-14) + JVM emission tests (D-05/D-07 tests) with same-session before/after diff of `main.c` section `buildVerticalFootProbe`.

**pong PASS\* rule:** pong.gb hashes differently every rebuild (GBDK/lcc non-determinism). Do not attempt hash comparison on ROM output for pong. Mark as PASS\* in any sweep. Generated C (`generateC`) IS deterministic for pong; use that for collateral-drift proof.

### GBC anchor re-shoot (D-14): fix-first, one capture pass

The 3 existing GBC anchor tests in `PlatformerTemplateUatTest.kt`:
- `anchor1Title_to_Gameplay` — shoots `01-title.png` + `02-gameplay.png` to evidence/anchor-1/
- `anchor2TilemapCollision` — shoots grounded + jump screenshots to evidence/anchor-2/
- (Additional anchors — anchor3, anchor4, anchor5 — present in the test class)

Currently writing to `.planning/phases/12.7-player-levitating-physics-codegen/evidence/uat-screenshots/` (the `EVIDENCE_DIR` constant at PlatformerTemplateUatTest.kt:47-53).

**D-14 sequencing:** Implement D-05 + D-09 code changes first (pivot_adjust lift + predicate consolidation). Then run `pluginTest` to confirm green suite. Then rebuild ROM clean. Then run UAT anchors. The Phase 21 anchor screenshots should be written to the Phase 21 `evidence/` directory — the `EVIDENCE_DIR` constant must be updated before shooting.

**GBC mode constraint (D-15):** `AgentSessionConfig.discoverFiles(ROM_FILE, screenshotDir = EVIDENCE_DIR)` — the `gbcMode=true` is set via the `.noi` symFile path passed in `AgentSessionConfig`. The test uses `Assumptions.assumeTrue(ROM_FILE.exists())` — requires `buildRom` to have run.

**Clean rebuild rule:** `./gradlew :gbkt-examples:platformer-template:clean :gbkt-examples:platformer-template:buildRom` before each capture session. A stale ROM produces stale PNGs that may not reflect code changes.

**The two UatTest files:**
- `PlatformerTemplateUatTest.kt` — main 5-anchor suite (the one to use for D-14 re-shoots)
- `PlatformerTemplate128UatTest.kt` — 128-step variant (separate; not the anchor re-shoot target)

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Per-zone spawn position codegen | New `spawnPosition` DSL primitive | `ZoneBuilder.spawn()` at WorldBuilders.kt:247 | Already exists, fully wired end-to-end via GBDKPipeline |
| gameUsesTilemapCollision shared utility | Duplicate a third copy | Add to `gbkt-backend-api` + delegate from both callers | The module already hosts `GenreSystemVisitor`; the shared predicate fits there |
| Serializer round-trip for `SystemIR` open interface | Full reflection-based deserialization | Document as unsupported subset + KDoc | SystemIR is an open interface with genre-specific implementations; a registry or sealed strategy is needed (scope for v0.2.0 if ever) |
| pivot_adjust numeric computation | Compute from metasprite geometry at visitor time | Store explicit `pivotAdjust(Int)` in `TilemapCollisionBuilder` config | Keeps the DSL as single source of truth; eliminates the metasprite lookup dance |

---

## Common Pitfalls

### Pitfall 1: Re-implementing what's already fixed (SEED-027, SEED-028)

**What goes wrong:** Executor implements `bitsPerPixel = 4 → 2` change or fixes guidance strings that were already corrected in Phase 18.
**Why it happens:** The seed files were not updated when Phase 18 plans fixed them.
**How to avoid:** Verify the fix before implementing. Run `grep -n "bitsPerPixel = 4" gbkt-core/.../TargetProfiles.kt` (returns nothing — it's already 2). Run `grep -rn "config { ramBanks = N"` (returns nothing — already corrected).
**Warning signs:** Finding nothing to change when implementing the "fix."

### Pitfall 2: Adding a new `spawnPosition()` DSL primitive when `spawn()` already exists

**What goes wrong:** Executor adds a duplicate `ZoneBuilder.spawnPosition(x, y)` thinking `spawn()` is missing.
**Why it happens:** The seed title says "spawn position clarity" which could suggest a new DSL API.
**How to avoid:** `WorldBuilders.kt:247` has `fun spawn(x: UByte, y: UByte)`. PlatformerTemplate.kt lines 272/277/282 already call it with `(40u, 120u)`.
**Warning signs:** Looking for `spawnPosition` or `spawnPos` and not finding them — because the correct API is just `spawn()`.

### Pitfall 3: Running `./gradlew pluginTest` without a prior `mavenLocal()` publish (publish/test ordering race)

**What goes wrong:** `pluginTest` resolves stale `0.1.0-SNAPSHOT` jars from `~/.m2` instead of the current code.
**Why it happens:** pluginTest requires the composite build publish step to run first.
**How to avoid:** `pluginTest` already republishes the 7 modules before testing. Verify with two consecutive invocations to confirm the race doesn't trigger (project memory note in `project_18_hardening_pr77.md`).
**Warning signs:** Integration test failures citing version mismatches or missing symbols that are present in source.

### Pitfall 4: Shooting GBC anchors before code fix is built into ROM

**What goes wrong:** Re-shoots reflect the pre-fix ROM, not the final one.
**Why it happens:** Missing `./gradlew clean buildRom` after changing platformer-template code.
**How to avoid:** D-14 sequencing: fix → build → shoot. Check the ROM modification timestamp before shooting.
**Warning signs:** Anchor screenshots look identical to the triage baseline.

### Pitfall 5: Updating `EVIDENCE_DIR` in the wrong direction

**What goes wrong:** The `PlatformerTemplateUatTest.kt` `EVIDENCE_DIR` constant still points to `.planning/phases/12.7-.../` when it should point to the Phase 21 evidence directory.
**Why it happens:** The constant is hardcoded (lines 47-53).
**How to avoid:** Planner must include a plan step to update `EVIDENCE_DIR` before running the re-shoot. After Phase 21 is closed, the constant can optionally be reverted (or left pointing to Phase 21 evidence).
**Warning signs:** UAT screenshots appear in the Phase 12.7 evidence directory instead of Phase 21.

### Pitfall 6: Touching `CartridgeConfig(romBanks = N, ramBanks = N)` constructor args (SEED-028 boundary)

**What goes wrong:** Executor replaces named argument form in IR data class constructors, breaking all test fixtures.
**Why it happens:** SEED-028 mentions `romBanks` but the scope explicitly excludes data-class constructors.
**How to avoid:** SEED-028 says: "the `CartridgeConfig(romBanks = …, ramBanks = …)` IR data-class constructor named-argument sites are unrelated and must NOT be touched." The stale guidance strings are in user-facing doc/comment text only.

---

## Validation Architecture

Per `.planning/config.json` check — assuming `workflow.nyquist_validation` is absent or true.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Kotlin test |
| Quick run command | `./gradlew :gbkt-genre-platformer:test :gbkt-ir:test :gbkt-backend-api:test` |
| Full suite command | `./gradlew test` |
| Plugin test command | `./gradlew pluginTest` (republishes 7 modules first) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | Notes |
|--------|----------|-----------|-------------------|-------|
| FIX-05 D-05 | pivot_adjust resolved from DSL config, not metasprite lookup | JVM emission test | `:gbkt-genre-platformer:test --tests '*PlatformerSnapArithmeticEmissionTest*'` | Wave 0 gap — new test file needed |
| FIX-05 D-05 | Existing EmissionTests stay GREEN with fallback | JVM test | `:gbkt-genre-platformer:test` | Existing 4 EmissionTests cover this |
| FIX-05 D-06 | Player spawns on ground row (visual) | UAT screenshot | `PlatformerTemplateUatTest.anchor2TilemapCollision` | Visual Evidence Rule — binding user sign-off required |
| FIX-05 D-07 | snap arithmetic produces correct posYSym | JVM emission test | `:gbkt-genre-platformer:test --tests '*PlatformerSnapArithmeticEmissionTest*'` | Same test file as D-05; add method for snap formula |
| FIX-06 D-08 | GameIR serialize→deserialize round-trip non-empty for all 10 collections | JVM round-trip test | `:gbkt-ir:test --tests '*GameIRSerializerRoundTripTest*'` | Wave 0 gap — new test file needed |
| FIX-06 D-09 | Both gameUsesTilemapCollision predicates return identical verdicts | JVM contract test | `:gbkt-genre-platformer:test --tests '*TilemapCollisionPredicateLockstepTest*'` | Wave 0 gap — new test file needed |
| FIX-05 D-14 | All 3 GBC anchors re-shot post-fix | UAT screenshots | `PlatformerTemplateUatTest` (all anchors) | Visual Evidence Rule — binding user sign-off required; requires pre-built ROM |
| D-13 byte-identity | Untouched 5 examples unchanged | Codegen diff | `./gradlew :gbkt-examples:breakout:generateC` etc. + before/after diff | Manual; pong PASS* excluded from hash comparison |

### Sampling Rate

- **Per task commit:** `:gbkt-genre-platformer:test :gbkt-ir:test :gbkt-backend-api:test` + `:module:spotlessApply :module:detekt`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green + 3 GBC anchors re-shot + binding user visual sign-off + seeds/ empty

### Wave 0 Gaps

- [ ] `gbkt-genre-platformer/src/test/kotlin/.../PlatformerSnapArithmeticEmissionTest.kt` — covers FIX-05 D-05 + D-07
- [ ] `gbkt-ir/src/test/kotlin/.../GameIRSerializerRoundTripTest.kt` — covers FIX-06 D-08
- [ ] `gbkt-genre-platformer/src/test/kotlin/.../TilemapCollisionPredicateLockstepTest.kt` — covers FIX-06 D-09
- [ ] Phase 21 `evidence/` directory + update `EVIDENCE_DIR` in PlatformerTemplateUatTest.kt

---

## Runtime State Inventory

This phase does not rename any variables, IDs, or symbols visible at runtime. The `pivotAdjust` value is a codegen-time constant, not a runtime-stored ID. Zone IDs, variable names, and scene names are unchanged. No runtime state migration is needed.

**Nothing found in any category** — verified by code review: no memory keys, no SRAM fields, no OS-registered state, no env vars affected by Phase 21 changes.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| GBDK / lcc | ROM build smoke (D-14/D-15) | Likely ✓ (used in prior phases) | Check `lcc --version` | SKIPPED per verifier-gates.md rule — human must run locally before sign-off |
| JVM 21 | All Gradle tests | ✓ | 21 (per CLAUDE.md) | — |
| mavenLocal() | pluginTest republish | ✓ | — | — |

---

## Open Questions for the Planner

1. **D-05 pivot_adjust fallback constants:** After D-05 lifts resolution into `TilemapCollisionBuilder`, the `REFERENCE_FRAME_HEIGHT = 32` and `REFERENCE_PIVOT_Y = 6` companion constants are only used as fallback (when `pivotAdjust` is not set in DSL). Should they be DELETED (forcing callers to always set `pivotAdjust`) or KEPT as the fallback path? Research recommends KEEPING the fallback path (same numeric result for test fixtures) but the planner should confirm this with D-05 scope.

2. **D-07 JVM test scope:** The mandated emission test is for `_player_y` initial value + snap arithmetic. Research shows the snap arithmetic is in `buildVerticalFootProbe`. Precisely, the test should assert on the GENERATED C text shape of the foot-snap block (CVarDecl sequence). Planner should decide between: (a) full C-text assertion (brittle to comment changes) vs (b) structural assertion that `pivotAdjust` constant in emitted C matches the hitbox geometry. Option (b) is recommended.

3. **D-08 SEED-020 scope boundary:** The locked decision says "deserialize the 10 stubbed IR collections." Research shows that `SystemIR` deserialization is hard (open interface + no type registry). The planner should confirm: is the locked scope "full round-trip for all 10" or "round-trip for the 8 simple ones + documented contract for SystemIR + zones"? Research recommends the latter (option 2 from the seed).

4. **D-06 spawn Y coordinate validation:** Research confirms Y=120 is what's currently in PlatformerTemplate.kt and the triage screenshot shows it working. But the Phase 21 re-shoot (post D-05/D-07 fixes) is the FINAL evidence. If the re-shoot still shows any visual concern about spawn position, the planner should have a contingency: change Y=120 to `(tilemap_height_in_pixels - 8 - hitbox_height - pivot_adjust)` = `(256 - 8 - 24 - 2) = 222`... but this depends on tilemap structure. Flag as "executor resolves on first re-shoot."

5. **SEED-027 and SEED-028 disposition path:** These are VERIFIED-ALREADY-FIXED. The planner should include explicit plans to: (a) verify the fix exists, (b) close the seed as VERIFIED-ALREADY-FIXED, (c) move to `seeds/archive/`. This is a lightweight evidence + archive plan, not a code-change plan.

6. **EVIDENCE_DIR update scope:** Should the `EVIDENCE_DIR` constant in `PlatformerTemplateUatTest.kt` be permanently updated to Phase 21's evidence directory, or should it remain pointing to Phase 12.7 (the historical baseline)? The D-15 requirement is to write Phase 21 evidence to Phase 21's `evidence/` directory. The planner should clarify whether a temporary `EVIDENCE_DIR` override in the test (via system property or code change) is preferred over permanently updating the constant.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | SEED-027 (bitsPerPixel) was fixed in Phase 18 plan 18-05 based on current file content showing `bitsPerPixel = 2` and correct KDoc | D-10 | Low — direct source code inspection; no assumption, confirmed |
| A2 | SEED-028 (ConfigBuilder migration note) was fixed in Phase 18 plan 18-12 based on absence of stale strings in source | D-11 | Low — grep search confirmed no stale property-setter form remains |
| A3 | The 5 currently-untouched examples (breakout, simple-physics, metasprites, metasprites-stress, banks) will remain byte-identical after platformer-specific changes | D-13 | Low — the changes are gated by `gameUsesTilemapCollision()` which returns false for all 5 games |
| A4 | Y=120 is the correct ground-row spawn coordinate for world1Area1 | D-06 | Medium — confirmed by triage evidence (grounded=1 at frame 93) but final validation requires re-shoot |
| A5 | The D-07 sub-pixel sink is INTENDED / already correct | D-07 | Medium — diagnostic evidence strongly points this way but binding sign-off requires re-shoot post D-05 |
| A6 | `SEED-023` `whenever()` KDoc says "Not deprecated this phase" — it is now `@Deprecated` | D-03 | Low — need to confirm whether Phase 18 DEPR-01 added `@Deprecated` to `whenever()`; if so, SEED-023's route to backlog is clean |

**If A1 or A2 is wrong:** Re-implement the Phase 18 fix. Risk is low because source was verified directly.

---

## Sources

### Primary (HIGH confidence)

- Direct source code inspection of `WorldBuilders.kt`, `PlatformerVisitor.kt`, `GBDKPipeline.kt`, `PlatformerExtensions.kt`, `GameIRSerializer.kt`, `TargetProfiles.kt`, `PlatformerTemplate.kt`, `PlatformerTemplateUatTest.kt` — all read during this research session
- `.planning/phases/16-seed-triage/evidence/SEED-platformer-template-spawn-polish/capture-note.txt` — triage evidence confirming player grounded at spawn
- Player PNG pixel inspection via Python/Pillow — confirmed zero transparent gutters
- All 13 seed files under `.planning/seeds/` — read directly

### Secondary (MEDIUM confidence)

- `.planning/phases/21-codegen-fixes-platformer-and-remaining-seeds/21-CONTEXT.md` — locked decisions
- TRIAGE.md — disposition table
- REQUIREMENTS.md + ROADMAP.md — phase scope

### Tertiary (LOW confidence / ASSUMED)

- A6: Whether Phase 18 DEPR-01 added `@Deprecated` to `ScriptBuilder.whenever()` — not confirmed by source inspection in this session. The TRIAGE.md entry for SEED-023 (line 55) says "KDoc says 'Not deprecated this phase'; SEED-023 ref in KDoc" — this may predate Phase 18. Planner should verify.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all code verified by direct source inspection
- Architecture: HIGH — consumption chains traced end-to-end
- Pitfalls: HIGH — based on project memory + direct code evidence

**Research date:** 2026-06-14
**Valid until:** 2026-07-14 (30 days; this is a stable, known codebase)
