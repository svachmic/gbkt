# Phase 06: Complete Gap Closure — Research

**Researched:** 2026-02-21
**Domain:** Multi-domain (Sound/Music codegen, Module restructure, Collection codegen, DSL extensions, Tile collision, V1 cleanup, IntelliJ plugin DX)
**Confidence:** HIGH (all findings based on direct codebase exploration)

---

## Summary

Phase 06 is a large cleanup and completions phase covering 26 directives across 9 domain areas. The research confirms that the existing codebase is well-structured and all directives have clear, actionable implementation paths. No external library research is needed — this is entirely internal implementation work.

The most critical work is the V1 cleanup (domain H) and package promotion (H3), because removing 37+ IR files and renaming `v2/` package paths will affect virtually every file in the codebase. This must be sequenced carefully. The module restructure (B2) is the second most structurally risky item — adding gbkt-world, gbkt-exploration, and populating gbkt-engine requires careful acyclic dependency graph analysis.

The sound system work (A1-A5) is technically well-defined: the register configurations already exist in `Sound.kt`'s `getPresetConfig()` function with full NRxx values — the gap is purely that `buildSoundWrapperFunction()` in `GBDKPipelineV2.kt` uses `hashCode()` instead of reading those values. The fix is mechanical but requires mapping Kotlin domain types (Channel, DutyCycle, EnvelopeConfig, SweepConfig, FrequencyConfig) to their C NRxx register bit-packed values.

**Primary recommendation:** Sequence work as H (V1 cleanup) → B2 (module restructure) → all other directives in parallel or in small grouped plans. Do H before other domains to eliminate the v1/v2 confusion. Once v2 paths are promoted, other directives are clean additions.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

Phase 06 has no separate CONTEXT.md with user locked decisions — the CONTEXT.md IS the directive document. All 26 directives are mandatory. See CONTEXT.md for full directive descriptions.

### Locked Decisions (from CONTEXT.md directives)
- Sound effect wrappers must write actual NRxx registers from SoundEffect preset data (not hashCode stubs)
- hUGETracker integration: IRMusicPlay → `hUGE_init()`, IRMusicStop/Pause/Resume → hUGEDriver calls, `hUGE_dosound()` in main loop
- Module structure: gbkt-world (world/dungeon), gbkt-exploration (or merge into gbkt-world), populate gbkt-engine, gbkt-all meta-module
- GBDKSystemVisitor: implement SystemIRVisitorI replacing `filterIsInstance<GenericSystem>()` in `buildSystemFunctions()`
- SimpleBattle must generate COMBAT_STATE enum state machine (INIT/PLAYER_TURN/ENEMY_TURN/VICTORY/DEFEAT)
- SpawnActor/DestroyActor: OAM slot management from free list (not stubs)
- V1 deletion: delete 37 v1 IR files + v1 DSL files + GBDKCodeGenerator + all v1 codegen directories
- V2 package promotion: rename `ir/v2/` → `ir/`, `dsl/v2/` → `dsl/`, update all imports
- gbkt-engine: populate per B2 directive (H4 defers to B2; B2 says "populate gbkt-engine with scene lifecycle, actor management, input, graphics fundamentals")
- IntelliJ: split editor view (DSL left, C right), synchronized scrolling via `.gbkt.map`
- IntelliJ: `InspectionTool` for asset ref validation; red underline + quick-fix placeholder PNG
- IntelliJ: budget gutter icons next to `scene {}` / `actor {}` blocks

### Claude's Discretion
- Whether to merge gbkt-exploration into gbkt-world or keep as separate module
- Exact COMBAT_STATE machine implementation details (reference v1 BattleCodegen pattern)
- Exact OAM free-list data structure for SpawnActor/DestroyActor
- AudioMixer codegen format (stub functions acceptable as minimum)
- BitwiseOptimizationPass: exact pass type (analysis pass vs. codegen-level optimization)
- BudgetReporter polish: exact ANSI escape codes and bar format

### Deferred Ideas (OUT OF SCOPE)
- Full priority audio mixing (channel priority scheduling)
- Full RPG combat (stateful multi-turn AI) — only state machine skeleton required
- hUGETracker .uge to C conversion tooling
- Live preview of scene tilemap layout (IDE-04) — listed as pending in REQUIREMENTS.md but not explicitly directed in CONTEXT.md
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CLEAN-01 | All v1 IR and DSL files deleted; no dead code in gbkt-core | Directive H1: 37 v1 IR files in `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/` + 8 DSL files in `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/` confirmed by codebase scan |
| CLEAN-02 | `v2/` subdirectories promoted; all imports updated | Directive H3: `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/v2/` and `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/v2/` confirmed as targets |
| CLEAN-03 | GBDKCodeGenerator fully deleted | Confirmed live in `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/GBDKCodeGenerator.kt` with 100+ referencing files |
| BOM-04 | gbkt-bom publishes aligned versions for all modules | Currently only includes gbkt-core, gbkt-backend-api, gbkt-backend-gbdk — gbkt-ir, gbkt-lang, gbkt-engine missing |
| COLL-01 | Tile-specific collision attributes from tilemap data | Directive G1-G3: TMX collision layer extraction partially implemented in `AssetPipeline.kt` (collisionData field exists), needs v2 IR wiring |
| COLL-02 | Collision data integrates with exploration system | Directive G3: movement guard integration with tile collision check before position update |
| IDE-01 | Source map viewer with DSL↔C line mapping | `CCodePreviewPanel.kt` already has DSL→C direction; needs C→DSL direction and synchronized scrolling |
| IDE-02 | Red underline on `asset()` targeting nonexistent files | `GbktDslInspection.kt` has framework; needs asset path validation check |
| IDE-03 | Inline budget report gutter icons | New `LineMarkerProvider` implementation needed |
| QUAL-01 | All Detekt violations from Phases 3.1 and 4 resolved | Not directly researched — verify with `./gradlew detekt` before planning |
| QUAL-02 | No pre-existing Detekt warnings in modified files | Same as above |
</phase_requirements>

---

## Architecture Patterns

### Domain A: Sound System Codegen

**A1 — NRxx Register Writes from SoundEffect presets**

The gap is clear: `buildSoundWrapperFunction()` in `GBDKPipelineV2.kt` (line 852) generates:
```kotlin
CRawCode("play_sound(${sanitizedId.hashCode() and 0xFF}, 0, 64, 30);")
```

The data to replace this exists in `gbkt-core/src/main/kotlin/io/github/gbkt/core/Sound.kt`. The `getPresetConfig()` private function (line 300) maps every `SoundPreset` to `(Channel, SoundRegisters)`. The `SoundRegisters` struct contains all NRxx register values in typed form.

The fix pattern is:
1. In `GBDKPipelineV2.kt`, look up the SoundEffect from gameIR (or from the v1 Game model) for the given soundId
2. Map the `SoundRegisters` fields to actual C register writes:
   - CH1 (PULSE1): NR10 (sweep), NR11 (duty+length), NR12 (envelope), NR13 (frequency low), NR14 (frequency high + trigger)
   - CH2 (PULSE2): NR21, NR22, NR23, NR24 (no sweep)
   - CH3 (WAVE): NR30, NR31, NR32, NR33, NR34
   - CH4 (NOISE): NR41, NR42, NR43, NR44

**GBDK register name reference (HIGH confidence — from gbdk-2020 headers):**
- NR10_REG, NR11_REG, NR12_REG, NR13_REG, NR14_REG (CH1 Square with sweep)
- NR21_REG, NR22_REG, NR23_REG, NR24_REG (CH2 Square)
- NR30_REG, NR31_REG, NR32_REG, NR33_REG, NR34_REG (CH3 Wave)
- NR41_REG, NR42_REG, NR43_REG, NR44_REG (CH4 Noise)
- NR50_REG (master volume), NR51_REG (panning), NR52_REG (sound on/off)

**Register bit layout for CH1 example:**
```c
// NR10: sweep — bit 6:4 = time, bit 3 = direction, bit 2:0 = shift
NR10_REG = (sweepTime << 4) | (sweepDir << 3) | sweepShift;
// NR11: duty + length — bit 7:6 = duty, bit 5:0 = length
NR11_REG = (dutyValue << 6) | (length & 0x3F);
// NR12: envelope — bit 7:4 = volume, bit 3 = dir, bit 2:0 = pace
NR12_REG = (envVol << 4) | (envDir << 3) | envPace;
// NR13: frequency low byte
NR13_REG = freq & 0xFF;
// NR14: trigger + length enable + frequency high
NR14_REG = (trigger << 7) | (lengthEnable << 6) | ((freq >> 8) & 0x07);
```

**Key concern:** The `SoundEffect` class currently exists in `gbkt-core` (v1 model). The v2 `GameIR` does NOT include sound effects — only `PlaySound(soundId)` ScriptOps are recorded. The soundId maps to the Kotlin variable name (e.g. `"bump"` from `val bump by soundEffect { ... }`). The register data needs to be accessible from the pipeline. Either: (a) pass the v1 `Game` model alongside `GameIR`, or (b) add a `SoundEffectDef` list to `GameIR`. Option (b) is cleaner and matches the v2 architecture — see `VariableDef` / `ArrayDef` pattern in `GameIR`.

**A2 — Music Codegen in v2 pipeline**

The v1 codegen handles music well (see `StatementCodegen.kt` lines 945-960). The v2 pipeline needs the same via `ScriptOpVisitorI`. However, the v2 `ScriptOp` sealed hierarchy does NOT include music ops — they're v1 `IRMusicPlay/Stop/Pause/Resume/Fade` from `SoundIR.kt`. Two options:
1. Add music ScriptOps to v2 ScriptOp interface
2. Extend `GameIR` with a `musicRef` field that pipes music operations through a new v2 IR node

Recommend option 1: add `MusicPlay(songId)`, `MusicStop()`, `MusicPause()`, `MusicResume()` as new ScriptOp implementations, and handle in ScriptOpVisitor.

**A4 — SoundSystem in `buildSystemFunctions()`**

Current code (line 1132-1136):
```kotlin
private fun buildSystemFunctions(gameIR: GameIR): List<CFunction> {
    return gameIR.systems.filterIsInstance<GenericSystem>().map { system ->
        buildSystemTriggerFunction(system)
    }
}
```

All typed systems (`SoundSystem`, `CameraSystem`, `SaveSystem`, `ExplorationSystem`, `DialogSystem`) are silently dropped. The fix is Directive C1 — creating `GBDKSystemVisitor` implementing `SystemIRVisitorI<List<CFunction>>` and dispatching through it.

---

### Domain B: Module Restructure

**B1 — Test Framework Standardization**

All core modules use `kotlin("test")` correctly. Check `gbkt-gradle-plugin` for junit-jupiter direct dependency. The fix is mechanical: remove explicit junit dependency and add `useJUnitPlatform()`.

**B2 — Full Module Restructure**

Current module state (confirmed):
- `gbkt-ir`: ScriptOp, Expr, SystemIR, GameIR, source location types — SOURCE: `gbkt-ir/src/main/kotlin/`
- `gbkt-lang`: DSL builders (GameBuilder, ScriptBuilder, variable delegates) — SOURCE: `gbkt-lang/src/main/kotlin/`
- `gbkt-engine`: Empty (only `package-info.kt` in `gbkt-engine/src/main/kotlin/io/github/gbkt/core/engine/`)
- `gbkt-core`: 24+ packages including world, exploration, rpg, entity, scene, graphics, etc.
- Missing: gbkt-world, gbkt-exploration, gbkt-all

Circular dependency constraint (from STATE.md): `gbkt-engine` cannot import `gbkt-core` types if `gbkt-core` re-exports `gbkt-engine` via api(). The layered hierarchy is: `gbkt-ir ← gbkt-lang ← gbkt-engine ← gbkt-core`.

For the extraction strategy:
- `gbkt-world`: Extract `gbkt-core/src/main/kotlin/io/github/gbkt/core/world/` (Floor, Zone, Encounter, Flags IR) — this depends on gbkt-ir types only, so it can depend on `gbkt-ir` without circular risk
- `gbkt-exploration`: Extract `gbkt-core/src/main/kotlin/io/github/gbkt/core/exploration/` — check what it imports
- `gbkt-engine` population (per B2 directive, H4 defers): scene lifecycle types from `gbkt-core/src/main/kotlin/io/github/gbkt/core/scene/` (4 files: Scene.kt, SceneTransition.kt, Link.kt, Transition.kt), actor management from `entity/` (9 files: Entity.kt, EntityBuilder.kt, EntityComponents.kt, Interfaces.kt, Pool.kt, PoolBuilder.kt, PoolStateBuilder.kt, EntityRegistry.kt, CombatComponents.kt), input from `input/` (2 files: Input.kt, InputBuffer.kt), graphics from `graphics/` (8 files: Sprite.kt, Animation.kt, Camera.kt, Palette.kt, Particles.kt, TileMap.kt, TileMapDsl.kt, CameraBuilder.kt). These currently import v1 IR types (io.github.gbkt.core.ir.*) — but after Plans 01+02 (v1 deletion + v2 promotion), those imports resolve to gbkt-ir types, making extraction feasible. Cross-references within the set (e.g., Camera imports Entity, Entity imports Sprite/Hitbox) are internal to the extraction set and resolve within gbkt-engine.

**Critical insight:** The circular dependency constraint is resolved by sequencing: H (V1 cleanup) → H3 (v2 promotion) → B2 (module restructure). After v1 types are gone and v2 paths promoted, scene/entity/input/graphics only depend on `gbkt-ir` and `gbkt-lang` types, which `gbkt-engine` already transitively has. The only risk is types like `GameBuilder`, `SceneRef`, `AnimationRef`, `TagRef` which may still live in gbkt-core — the executor must audit these and either extract them to gbkt-engine or leave dependent files in gbkt-core.

**Recommended sequencing:** H (V1 cleanup) → H3 (v2 promotion) → B2 (module restructure with gbkt-engine population). This is already the plan: Plan 01 → Plan 02 → Plan 03.

---

### Domain C: Explorer Feature Parity

**C1 — GBDKSystemVisitor**

Create `GBDKSystemVisitor` in `gbkt-backend-gbdk/.../codegen/visitor/` implementing `SystemIRVisitorI<List<CFunction>>`.

Pattern for each system type:
- `visitCameraSystem(CameraSystem)`: Generate `_camera_x`, `_camera_y` globals + `update_camera()` that follows target actor
- `visitSaveSystem(SaveSystem)`: Generate SRAM read/write functions using `_SAVE_DATA` at SRAM address 0xA000
- `visitSoundSystem(SoundSystem)`: Generate sound driver init + `sound_driver_update()` call scheduling
- `visitExplorationSystem(ExplorationSystem)`: State machine for grid movement, encounter checks
- `visitDialogSystem(DialogSystem)`: Wire through existing `buildDialogHelpers()`

**C3 — SimpleBattle State Machine**

The v1 `BattleCodegen.kt` exists in `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/rpg/BattleCodegen.kt` — reference this for the state machine pattern. The v2 stub currently executes onVictoryOps immediately (lines 1143-1153). The new pattern:

```c
typedef enum { COMBAT_INIT, COMBAT_PLAYER_TURN, COMBAT_ENEMY_TURN, COMBAT_VICTORY, COMBAT_DEFEAT } COMBAT_STATE;
UINT8 _combat_state = COMBAT_INIT;

void update_combat_<id>() {
    switch (_combat_state) {
        case COMBAT_INIT: _combat_state = COMBAT_PLAYER_TURN; break;
        case COMBAT_PLAYER_TURN: /* wait for player action */ break;
        case COMBAT_ENEMY_TURN: /* AI turn */ _combat_state = COMBAT_PLAYER_TURN; break;
        case COMBAT_VICTORY: /* onVictoryOps */ break;
        case COMBAT_DEFEAT: /* onDefeatOps */ break;
    }
}
```

**C4 — Scene Transition Tile Reuse**

Generate a `_current_tileset_id` global (UINT8). In the navigate_to_scene enter call, wrap tileset load with guard:
```c
if (_current_tileset_id != <new_scene_tileset_id>) {
    /* load tileset */
    _current_tileset_id = <new_scene_tileset_id>;
}
```
This is generated in `buildNavigateToSceneFunction()` in `GBDKPipelineV2.kt`.

---

### Domain D: Collection System

**D1 — GBDKCollectionCodegen Implementation**

`CollectionCodegen` interface is fully defined in `gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/CollectionCodegen.kt` with 8 methods. The v1 `CollectionsCodegen.kt` in `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/core/CollectionsCodegen.kt` implements this as extension functions on `GBDKCodeGenerator`. Create `GBDKCollectionCodegen : CollectionCodegen` and port those implementations.

**D2 — Collection IR in GameIR**

`GameIR` currently has no collection fields. The v1 `Game` model has `collPools`, `collHashTables` etc. (from `STATE.md` note: "IRColl prefix used"). Add collection fields to `GameIR`:
```kotlin
val collections: CollectionsIR = CollectionsIR()
```

**D3 — Collection Memory Accounting**

`ResourceInventoryPass.kt` line 65: `collectionBytes = 0, // TODO: wire when v2 GameIR gains collection IR fields`

After D2 is done, compute:
- Hashtable: `capacity * (keySize + valueSize + 1)` bytes (key + value + used flag)
- Pool: `capacity * elementSize + ceil(capacity/8)` bytes (data + free bitmap)
- Ring buffer: `capacity * elementSize + 3` bytes (data + head + tail + count)
- Fixed slots: `count * elementSize + ceil(count/8)` bytes (data + active bitfield)

---

### Domain E: DSL Completions

**E1/E2 — Palette Strict Mode**

`GBCColor.fromHex()` exists in `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/CoreTypes.kt` (line 227-228). It delegates to `fromRGB888()` which does `r shr 3` truncation. Add to `AnalysisConfig`:
```kotlin
data class AnalysisConfig(
    ...,
    val paletteStrictMode: Boolean = false,
)
```

Add a check in a new `PaletteValidationPass` (or extend `SemanticValidationPass`) that round-trips the RGB888 value through RGB555 and back, emitting WARNING if they differ.

**E3 — Type Casting DSL**

Add `CastExpr` as a new Expr implementation in `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/v2/Expr.kt`:
```kotlin
data class CastExpr(val targetType: VarType, val inner: Expr, ...) : Expr {
    override fun <T> accept(v: ExprVisitorI<T>): T = v.visitCast(this)
}
```
Add `visitCast` to `ExprVisitorI`. Handle in `ExprVisitor` to emit `(UINT8)(inner)` etc.
Add extension methods on `Expr`, `AssignableVar`, `ActorPropertyRef`:
```kotlin
fun Expr.toU8(): Expr = ExprWrapper(CastExpr(VarType.U8, this.ir))
fun Expr.toU16(): Expr = ExprWrapper(CastExpr(VarType.U16, this.ir))
```

**E4 — Sprite Frame Layout**

Add `frameWidth: Int?` and `frameHeight: Int?` to `ActorIR` or `SpriteRef`. In `ActorVisitor.kt`, when these are set, generate frame offset calculation:
```c
set_sprite_tile(oam_slot, (frame_y * (sprite_width / frame_width)) * (sprite_height / frame_height) + frame_x);
```

**E5 — Array Helpers**

Add `forEach { }`, `indexOf()`, `count()`, `fill()` to `ArrayVar` DSL wrapper. These should emit `WhileOp` bodies (since `ForOp` creates loop vars without handles). Pattern from STATE.md: "forOp string-variable loop replaced with whileOp+bidx".

**E6 — raw() Compiler Warning**

`SemanticValidationPass` already has the infrastructure for diagnostics. Add a check that counts `RawOp` instances recursively across all ScriptOps. Emit WARNING level diagnostic with count and list of source locations.

---

### Domain F: Compiler & Analysis

**F1 — BitwiseOptimizationPass**

The optimization logic already EXISTS in `ExpressionCodegen.kt` (v1 codegen, lines 666-711). A pure v2 IR BitwiseOptimizationPass in `gbkt-analysis` should replicate this as a `ConstantFoldingPass`-style IR transformer. Pattern from `ConstantFoldingPass.kt`:
- Walk all ScriptOps recursively
- Rewrite `BinaryExpr(left, MUL, Literal(n))` → `BinaryExpr(left, SHL, Literal(log2(n)))` when n is power of 2
- Rewrite `BinaryExpr(left, DIV, Literal(n))` → `BinaryExpr(left, SHR, Literal(log2(n)))` for unsigned vars
- Rewrite `BinaryExpr(left, MOD, Literal(n))` → `BinaryExpr(left, AND, Literal(n-1))` when n is power of 2

**F2 — Budget Report Polish**

Current `BudgetReporter.kt` uses plain `#` and `.` bars. Add ANSI escape codes for terminal color output:
- Green (under 75%): `\u001B[32m`
- Yellow (75-90%): `\u001B[33m`
- Red (over 90%): `\u001B[31m`
- Reset: `\u001B[0m`

Add a per-scene breakdown section after the VRAM table showing each scene's estimated ROM bank usage.

---

### Domain G: Tile Collision

**G1 — TMX Collision Layer Extraction**

The TMX parser (`AssetPipeline.kt`, line 495) already extracts `collisionData: ByteArray?` from a named collision layer. The v1 `Game` model's `CompiledMapData` (line 101) has `val collisionData: ByteArray?`. The v2 `SceneIR` does NOT have a `collisionData` field.

Directive: Add `collisionData: ByteArray?` to `SceneIR` (or to a new `TilemapIR` ref on SceneIR). Wire from asset pipeline through to codegen.

**G2 — `_map_collision()` Function**

Generate in the scene enter block (or as a HOME-bank helper):
```c
const UINT8 map_<scene>_collision[] = { ... };
UINT8 _map_collision(UINT8 x, UINT8 y) {
    return map_<scene>_collision[y * MAP_WIDTH + x];
}
```

Wire into exploration movement: before updating player position, check `_map_collision(new_x, new_y)`. If non-zero, block movement.

**G3 — Entity + Tile Collision Coexistence**

In the exploration movement update, check tile collision first, then entity obstacle check. Both layers additive:
```c
if (!_map_collision(new_x, new_y) && !entity_blocks(new_x, new_y)) {
    player_x = new_x; player_y = new_y;
}
```

---

### Domain H: V1 Code Cleanup (HIGH RISK — SEE SEQUENCING)

**H1 — V1 IR Files to Delete**

Confirmed in `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/`:
37 files including: CoreIR.kt, BattleIR.kt, BattleMenuIR.kt, DialogIR.kt, MenuIR.kt, SoundIR.kt, AudioIR.kt, CollectionsIR.kt, CombatStateIR.kt, CombatTraitsIR.kt, CombatFormulasIR.kt, ActionExecutionIR.kt, AbilityIR.kt, StatsIR.kt, LevelingIR.kt, StatusEffectIR.kt, EquipmentIR.kt, ItemIR.kt, MonsterIR.kt, TargetSelectionIR.kt, FlagsIR.kt, TablesIR.kt, StringsIR.kt, PathfindingIR.kt, MiscIR.kt, SystemIR.kt, IRWalker.kt, IRSubstitution.kt, FixedPointTypes.kt, TurnOrderIR.kt, DamageIR.kt, Transitions.kt, Variables.kt, ExpressionWrapper.kt, StatusBarIR.kt, BattleMenuIR.kt, CoreTypes.kt

**CRITICAL CAVEAT:** `CoreTypes.kt` contains `GBCColor`, `GBCPalette`, `PaletteType` which are imported by many non-v1 files (AssetPipeline.kt, Palette.kt, color tests, etc.). These MUST be relocated before deleting CoreTypes.kt. Also, `ExpressionWrapper.kt` contains the Expr DSL operator overloads used by DSL ergonomics tests.

**H2 — V1 Codegen Files to Delete**

These live in `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/`:
- `GBDKCodeGenerator.kt` (top-level, 100+ referencing files via GenerateCTask.kt etc.)
- All of `core/`, `rpg/`, `world/`, `combat/`, `features/`, `graphics/`, `data/`, `ui/`, `emit/` subdirectories

**CRITICAL CAVEAT:** Some types referenced from these files (like the AudioCodegen.kt patterns) may be referenced by example games. Do a full reference audit before deletion.

**H3 — Package Promotion**

Source directories to rename:
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/v2/` → `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/`
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/v2/` → `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/`

All imports from `io.github.gbkt.core.ir.v2.*` and `io.github.gbkt.core.dsl.v2.*` must be updated. This will affect:
- All v2 backend gbdk files (ScriptOpVisitor.kt, ExprVisitor.kt, etc.)
- All example game files
- All analysis pass files
- All test files

Use IDE rename refactoring or a find/replace: `io.github.gbkt.core.ir.v2` → `io.github.gbkt.core.ir` and `io.github.gbkt.core.dsl.v2` → `io.github.gbkt.core.dsl`.

**H4 — gbkt-engine Module**

Currently contains only `package-info.kt`. Decision resolved: **populate per B2**. H4 explicitly defers to B2 ("If module restructure [B2] decides to populate it instead, this directive becomes 'populate gbkt-engine'"). B2 directive says: "Populate gbkt-engine with scene lifecycle, actor management, input, graphics fundamentals from gbkt-core". User has confirmed: populate, not delete.

Extraction target (23 files across 4 packages):
- `scene/` (4 files): Scene, SceneBuilder, TimingBlocks, FrameScope, SceneTransitionScope, TransitionDefinition, LinkDefinition/Handle/Builder
- `entity/` (9 files): Entity, EntityBuilder, EntityDelegate, Pool, PoolEntityScope, EntityRegistry, EntityComponents, CombatComponents, Interfaces (Positionable/Movable)
- `input/` (2 files): DPad, Buttons, ButtonState, DpadDirectionState, ButtonCombo, InputBuffer, InputBufferRef, Button enum
- `graphics/` (8 files): Sprite, SpriteBuilder, Camera, Animation, Palette, Particles, TileMap, TileMapDsl, Hitbox, SpriteBinding

gbkt-engine already depends on `gbkt-lang` (which transitively includes `gbkt-ir`). After Plans 01+02 (v1 deletion + v2 promotion), all IR/DSL imports from these files resolve through gbkt-ir and gbkt-lang. gbkt-core re-exports gbkt-engine via `api()` for backward compatibility.

---

### Domain I: IntelliJ Plugin DX

**I1 — Source Map Viewer (Split Editor)**

`CCodePreviewPanel.kt` already implements:
- DSL→C direction: `setupDslCaretListener()` calls `scrollToMatchingCLine()` (reads source map, scrolls C panel)
- Source map loading: `GbktCodegenService.parseSourceMap()` and `findCLinesForKotlinLocation()`

Missing: C→DSL direction. Add a caret listener on the C code editor that reverse-maps C line → Kotlin file:line using the source map data. The `fileLineOffsets` map already tracks per-file offsets in the combined C view.

**I2 — Asset Ref Inspections**

`GbktDslInspection.kt` already has `buildVisitor()` infrastructure and `checkUndefinedReference()`. Add a new check for string arguments to `asset()` calls: resolve the path relative to the project's asset directory and check if the file exists.

Quick-fix: create `GbktCreateAssetPlaceholderQuickFix` that creates a 1x1 transparent PNG at the referenced path.

**I3 — Budget Report Gutter Icons**

Implement `LineMarkerProvider` (from IntelliJ Platform SDK). Parse budget report JSON/text output (from `BudgetReporter.formatReport()`). Match scene/actor IDs in the source to budget line entries. Return appropriate icons.

Color thresholds: GREEN < 75%, YELLOW 75-90%, RED > 90%.

---

## Common Pitfalls

### Pitfall 1: V1 Deletion Breaks the Build Before All References Are Cleaned
**What goes wrong:** Deleting v1 IR files before auditing all import sites leaves dangling references.
**Why it happens:** `GBDKCodeGenerator` imports v1 IR types; many other files still import from v1 IR packages.
**How to avoid:** Run a full grep for `import io.github.gbkt.core.ir.` (non-v2) before any deletion. Plan the deletion in this order: (1) relocate shared types (GBCColor, GBCPalette, etc.), (2) delete files with no live referrers, (3) delete files whose referrers are also being deleted.
**Warning signs:** Kotlin compilation errors with "unresolved reference" on IR type names.

### Pitfall 2: v2 Package Rename Causes Test Failures Without Visible Error
**What goes wrong:** After renaming `ir/v2/` → `ir/`, some test utilities may have hardcoded strings or reflective class lookups that break silently.
**Why it happens:** `GbktCodegenService.kt` uses regex-based JSON extraction and reflection for source maps (STATE.md note). If class names are used in reflection, rename breaks it.
**How to avoid:** Check for all string-based class references after renaming. Grep for `"io.github.gbkt.core.ir.v2"` in all files including non-Kotlin.
**Warning signs:** Tests that test source map functionality fail after rename.

### Pitfall 3: Sound Register Bit Calculations Are Hardware-Specific
**What goes wrong:** NRxx register values are computed incorrectly, producing no sound or wrong sounds.
**Why it happens:** Game Boy audio register bit layouts are hardware-specific and not immediately obvious from field names.
**How to avoid:** Reference Pan Docs or GBDK documentation for exact bit layouts. Verify with unit tests comparing known register values for a simple preset (BEEP is simplest — pure square, no sweep).
**Warning signs:** Generated C compiles but produces no audio on emulator.

### Pitfall 4: Module Circular Dependency When Extracting gbkt-world
**What goes wrong:** gbkt-world depends on types that depend back on gbkt-core, creating a cycle.
**Why it happens:** World types may import from gbkt-core packages that in turn re-export gbkt-world.
**How to avoid:** Map the dependency tree before extraction. World/dungeon types in `gbkt-core/src/main/kotlin/io/github/gbkt/core/world/` should only depend on gbkt-ir types. Verify with `./gradlew dependencies`.
**Warning signs:** Gradle circular dependency error during configuration phase.

### Pitfall 5: IntelliJ Plugin API Compatibility
**What goes wrong:** IntelliJ APIs change between versions; code that works in one version may not compile in another.
**Why it happens:** IntelliJ Platform SDK is not stable across major versions.
**How to avoid:** The plugin target is stated as "IntelliJ 2024.3+" in the CONTEXT.md. Check the plugin's `build.gradle.kts` for the exact `intellijPlatform` version being targeted. Use only documented stable APIs.
**Warning signs:** Plugin compile errors with `@ApiStatus.Internal` or deprecated API warnings.

### Pitfall 6: AudioMixer IR is V1 Only
**What goes wrong:** AudioMixer DSL uses `RecordingContext.require().emit(IRMixerSetVolume(...))` which only works in v1 recording context.
**Why it happens:** The v2 ScriptBuilder context doesn't handle IRMixer* ops.
**How to avoid:** Either add IRMixer* to v2 ScriptOp interface, or generate stub functions and skip per-DSL wiring for Phase 06 (acceptable per CONTEXT.md discretion).

---

## Code Examples

### Sound Register Write Pattern (A1)
```kotlin
// In GBDKPipelineV2.buildSoundWrapperFunction(soundId)
private fun buildSoundWrapperFunction(soundId: String, effect: SoundEffectDef): CFunction {
    val sanitizedId = soundId.replace('-', '_').replace(' ', '_')
    val regs = effect.registers
    val channel = effect.channel

    val body = mutableListOf<CStatement>()

    when (channel) {
        Channel.PULSE1 -> {
            // NR10: sweep
            val sweepVal = regs.sweep?.let { s ->
                (s.time shl 4) or (if (s.direction == SweepDirection.DECREASE) 0x08 else 0x00) or (s.shift and 0x07)
            } ?: 0x00
            body += CRawCode("NR10_REG = 0x${sweepVal.toString(16).uppercase()}u;")
            // NR11: duty + length
            val dutyBits = when (regs.duty) {
                DutyCycle.TWELVE_POINT_FIVE -> 0
                DutyCycle.TWENTY_FIVE -> 1
                DutyCycle.FIFTY_PERCENT -> 2
                DutyCycle.SEVENTY_FIVE -> 3
            }
            body += CRawCode("NR11_REG = 0x${((dutyBits shl 6) or (regs.length and 0x3F)).toString(16).uppercase()}u;")
            // NR12: envelope
            val env = regs.envelope
            if (env != null) {
                val envBits = (env.volume shl 4) or (if (env.direction == EnvelopeDirection.INCREASE) 0x08 else 0x00) or (env.pace and 0x07)
                body += CRawCode("NR12_REG = 0x${envBits.toString(16).uppercase()}u;")
            }
            // NR13/NR14: frequency
            body += CRawCode("NR13_REG = 0x${(regs.frequency and 0xFF).toString(16).uppercase()}u;")
            val nr14 = (if (regs.trigger) 0x80 else 0x00) or (if (regs.lengthEnable) 0x40 else 0x00) or ((regs.frequency shr 8) and 0x07)
            body += CRawCode("NR14_REG = 0x${nr14.toString(16).uppercase()}u;")
        }
        // ... CH2, CH3, CH4 similar
    }

    return CFunction(name = "play_sound_$sanitizedId", returnType = CVoid, body = body)
}
```

### SystemIRVisitor Pattern (C1)
```kotlin
// New file: GBDKSystemVisitor.kt
class GBDKSystemVisitor : SystemIRVisitorI<List<CFunction>> {
    override fun visitCameraSystem(system: CameraSystem): List<CFunction> {
        return listOf(
            CFunction("update_camera", CVoid, body = listOf(
                CRawCode("/* camera follow logic — sets scroll_x, scroll_y */"),
            ))
        )
    }
    override fun visitSoundSystem(system: SoundSystem): List<CFunction> = emptyList() // handled by buildSoundFunctions
    override fun visitSaveSystem(system: SaveSystem): List<CFunction> {
        return listOf(
            CFunction("save_game", CVoid, body = listOf(CRawCode("/* SRAM write via 0xA000 */")))
        )
    }
    // etc.
}

// In GBDKPipelineV2.buildSystemFunctions():
private fun buildSystemFunctions(gameIR: GameIR): List<CFunction> {
    val visitor = GBDKSystemVisitor()
    return gameIR.systems.flatMap { system -> system.accept(visitor) }
}
```

### BitwiseOptimizationPass Pattern (F1)
```kotlin
// In gbkt-analysis, new file BitwiseOptimizationPass.kt
class BitwiseOptimizationPass : AnalysisPass {
    override fun run(context: PassContext): PassContext {
        val optimizedGame = optimizeGameIR(context.game)
        return context.copy(game = optimizedGame)
    }

    private fun optimizeExpr(expr: Expr): Expr = when (expr) {
        is BinaryExpr -> {
            val l = optimizeExpr(expr.left)
            val r = optimizeExpr(expr.right)
            when {
                expr.op == BinaryOp.MUL && r is Literal && isPow2(r.value) ->
                    BinaryExpr(l, BinaryOp.SHL, Literal(log2(r.value)))
                expr.op == BinaryOp.DIV && r is Literal && isPow2(r.value) ->
                    BinaryExpr(l, BinaryOp.SHR, Literal(log2(r.value)))
                expr.op == BinaryOp.MOD && r is Literal && isPow2(r.value) ->
                    BinaryExpr(l, BinaryOp.AND, Literal(r.value - 1))
                else -> BinaryExpr(l, expr.op, r)
            }
        }
        else -> expr
    }
}
```

---

## Sequencing & Risk Assessment

### Recommended Execution Order

The 26 directives should be planned in phases to minimize cross-cutting conflicts:

**Plan 1 (Foundation — H series):** H1 (V1 IR deletion with type relocation), H2 (V1 codegen deletion). gbkt-engine left untouched (deferred to Plan 3 for population per B2).
- This eliminates the v1/v2 confusion and simplifies all subsequent work
- BLOCKER: must audit all imports before deletion

**Plan 2 (Package promotion — H3):** Rename `ir/v2/` → `ir/` and `dsl/v2/` → `dsl/`, update all imports
- This is a codebase-wide rename — high blast radius but mechanical
- After this, all v2 code has clean package names
- CRITICAL: this must complete before Plan 3 so that scene/entity/input/graphics imports resolve to promoted paths in gbkt-ir/gbkt-lang

**Plan 3 (Module restructure — B1, B2, H4):** Standardize test framework; create gbkt-world (world+exploration); populate gbkt-engine (scene lifecycle, actor management, input, graphics); create gbkt-all; update gbkt-bom
- Depends on H being complete so scene/entity/input/graphics imports resolve to gbkt-ir/gbkt-lang (not v1 IR)
- H4 resolved here: populate gbkt-engine per B2 directive (H4 defers to B2)

**Plan 4 (Sound system — A1, A2, A3, A4, A5):** NRxx register exports, hUGE integration, waveform codegen, SoundSystem visitor, AudioMixer stubs

**Plan 5 (Collection + DSL completions — D1, D2, D3, E1-E6):** CollectionCodegen impl, GameIR wiring, RAM accounting, palette strict mode, type casting, sprite frames, array helpers, raw() warning

**Plan 6 (Explorer features + Tile collision — C1, C2, C3, C4, G1, G2, G3):** GBDKSystemVisitor, SpawnActor OAM management, SimpleBattle state machine, tileset reuse, TMX collision extraction, map_collision codegen

**Plan 7 (Analysis + IntelliJ — F1, F2, I1, I2, I3):** BitwiseOptimizationPass, budget report polish, source map split editor, asset ref inspection, gutter icons

---

## Open Questions

1. **GBCColor/GBCPalette relocation target**
   - What we know: These types are in `CoreTypes.kt` (v1 IR) and referenced by AssetPipeline.kt, graphics/Palette.kt, many tests
   - What's unclear: Should they move to `gbkt-ir` (v2 IR module) or stay in `gbkt-core` as standalone types?
   - Recommendation: Move to `gbkt-ir` alongside v2 Expr/ScriptOp types; update all import sites

2. **SoundEffectDef in v2 GameIR**
   - What we know: v2 GameIR has no sound effect definitions; only PlaySound(soundId) ops
   - What's unclear: Is adding SoundEffectDef to GameIR the right approach, or should the pipeline receive the v1 Game alongside GameIR?
   - Recommendation: Add `soundEffects: List<SoundEffectDef>` to GameIR (mirrors `variables: List<VariableDef>` pattern)

3. **gbkt-exploration vs merged into gbkt-world**
   - What we know: Exploration module is `gbkt-core/src/main/kotlin/io/github/gbkt/core/exploration/`; world is `world/`
   - What's unclear: Whether the split is worth the added module complexity
   - Recommendation: Merge into gbkt-world for Phase 06 simplicity; can be re-split later if needed

4. **GBDKCodeGenerator removal scope**
   - What we know: 100+ files reference GBDKCodeGenerator (from Grep)
   - What's unclear: How many of those references are in example game GenerateC.kt files vs. core infrastructure
   - Recommendation: Grep for actual usage pattern — many references may be in test utilities and `.planning/` docs (not compilable code)

---

## Sources

### Primary (HIGH confidence)
- Direct codebase exploration via Serena MCP tools — all findings verified against actual source files
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` — current v2 pipeline implementation
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/v2/` — v2 IR types
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-core/src/main/kotlin/io/github/gbkt/core/Sound.kt` — SoundEffect presets with full register data
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/CollectionCodegen.kt` — CollectionCodegen interface
- `/Users/michalsvacha/GitHub/personal/gbkt/gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/report/BudgetReporter.kt` — current budget reporter
- `.planning/STATE.md` — architectural decisions log

### Secondary (MEDIUM confidence)
- GBDK-2020 NRxx register names — from training knowledge of GBDK headers; should be verified against installed GBDK headers in `/opt/gbdk-2020/include/`
- IntelliJ Platform SDK LineMarkerProvider API — from training knowledge; verify against plugin target version

---

## Metadata

**Confidence breakdown:**
- Domain H (V1 cleanup): HIGH — files confirmed, blast radius understood
- Domain A (Sound): HIGH — register data confirmed in Sound.kt, gap in pipeline confirmed
- Domain C (Explorer/SystemVisitor): HIGH — interface confirmed, implementation path clear
- Domain D (Collections): HIGH — interface confirmed, v2 GameIR gap confirmed in ResourceInventoryPass TODO
- Domain B (Module restructure): MEDIUM — circular dependency constraint well understood; exact extraction feasibility depends on import audit
- Domain G (Tile collision): MEDIUM — AssetPipeline has collision data infrastructure; v2 SceneIR wiring needs design decision
- Domain I (IntelliJ): MEDIUM — CCodePreviewPanel infrastructure exists; IntelliJ API surface needs verification against target version
- Domain E (DSL completions): HIGH — all implementation patterns clear and precedented
- Domain F (Analysis): HIGH — BitwiseOptimizationPass has v1 precedent; BudgetReporter is self-contained

**Research date:** 2026-02-21
**Valid until:** Until next significant codebase change (stable for 30+ days given active development)
