# Phase 06: Complete Gap Closure — CONTEXT

## Overview

Phase 06 absorbs all audit gaps from the CONTEXT-vs-PLAN review plus four future roadmap phases (5.1, 5.2, 5.3, 5.4) into a single comprehensive gap-closure phase. Every item below is a **mandatory directive** — all must be resolved before UAT (Phase 07).

Organized by domain area. Each directive includes the gap number, what exists today, what's missing, and what "done" looks like.

---

## A. Sound & Music (Gaps #2, #3)

### Directive A1: Sound Register Data Export (Gap #2 partial)

**Current state:** V2 codegen generates sound driver infrastructure (channels, priorities, durations, `sound_driver_update()`) but `play_sound_<id>()` wrappers use hashCode-based dummy IDs. The 12 SoundEffect presets exist in Kotlin (`Sound.kt`) with full register configurations (`SoundRegisters`: NR10-NR44 values, sweep, envelope, duty, frequency) but these are **never exported to C**.

**Directive:** `play_sound_<id>()` functions must write actual Game Boy audio registers (NRxx) derived from `SoundEffect.registers`. Each preset's channel, sweep, envelope, duty cycle, frequency, and length configuration must appear as register writes in the generated C. No more hashCode-based dummy IDs.

**Done when:** `play_sound_bump()` generates `NR10_REG = ...; NR11_REG = ...; NR12_REG = ...; NR13_REG = ...; NR14_REG = ...;` with values derived from the BEEP/HIT/JUMP/etc. preset. Unit tests verify correct register values for all 12 presets.

### Directive A2: Music/Tracker Format Support (Gap #2)

**Current state:** `MusicBuilder` exists, `music("path/to/song.uge")` records IR, v1 codegen generates `hUGE_init()`/`hUGE_dosound()` calls. V2 pipeline includes `#include <hUGEDriver.h>` but has **no v2 codegen for IRMusicPlay/Stop/Pause/Resume/Fade**. `ScriptOpVisitor` does not handle music ops.

**Directive:** V2 codegen must generate hUGETracker integration: `hUGE_init(&song_name)` on IRMusicPlay, `hUGE_dosound()` in main loop when music is active, IRMusicStop/Pause/Resume mapped to appropriate hUGEDriver calls.

**Done when:** A game with `music("song.uge")` and `playMusic()`/`stopMusic()` DSL calls generates correct hUGETracker C integration in v2 pipeline.

### Directive A3: Custom Waveform Definitions (Gap #3)

**Current state:** `SoundEffect` has `waveform: ByteArray?` field (16 bytes for CH3 Wave channel). `SoundEffectBuilder` accepts `channel = Channel.WAVE`. But waveform data is **never emitted to C** — codegen ignores the `waveform` field entirely.

**Directive:** When `SoundEffect.channel == Channel.WAVE` and `waveform` is non-null, emit a `const UINT8 wave_data_<id>[] = { ... }` array and load it into wave RAM before triggering the channel.

**Done when:** A SoundEffect with custom waveform on Channel 3 generates `wave_data[]` array and wave RAM load in C output.

### Directive A4: SoundSystem Codegen Wiring

**Current state:** `buildSystemFunctions()` in GBDKPipelineV2 only processes `GenericSystem` via `filterIsInstance<GenericSystem>()`. `SoundSystem` instances are silently ignored.

**Directive:** `SoundSystem` must be handled in `buildSystemFunctions()` — generate sound driver initialization and per-frame `sound_driver_update()` call wiring.

**Done when:** SoundSystem no longer silently ignored; sound driver init and update are generated when a SoundSystem is declared.

### Directive A5: Audio Mixer Codegen Stubs

**Current state:** `AudioMixer` DSL exists with channel groups, volume, priority, mute. IR nodes (`IRMixer*`) exist. **No codegen** — mixer is purely DSL-side.

**Directive:** Generate mixer volume globals and group control functions from AudioMixer IR. Full priority mixing can be deferred, but data structures and control functions must exist in C.

**Done when:** AudioMixer DSL produces at minimum stub C functions for `set_group_volume()`, `mute_group()`, `unmute_group()`.

---

## B. Module Restructure (Gaps #5, #6, absorbed Phase 5.2)

### Directive B1: Test Framework Standardization (Gap #5)

**Current state:** Most modules use `kotlin("test")` but `gbkt-gradle-plugin` uses `junit-jupiter` directly. Inconsistent test runner configuration.

**Directive:** Standardize on `kotlin("test")` with JUnit 5 as the runner across all modules. Add `tasks.withType<Test> { useJUnitPlatform() }` to root build script. Remove explicit jupiter dependency from gradle-plugin.

**Done when:** All modules use `kotlin("test")`, `./gradlew test` passes with JUnit 5 platform uniformly.

### Directive B2: Full Module Restructure (Gap #6, absorbed Phase 5.2)

**Current state:** 3-module restructure done (gbkt-ir, gbkt-lang extracted). gbkt-engine is empty (only `package-info.kt`). Missing modules: gbkt-world, gbkt-exploration, gbkt-all. IR already unsealed (ScriptOp, Expr, SystemIR are interfaces with visitor pattern). gbkt-bom only includes core, backend-api, backend-gbdk.

**Directive:**
- Create `gbkt-world` module: extract world/dungeon types (Floor, Zone, Encounter, Flags) from gbkt-core
- Create `gbkt-exploration` module: extract exploration types from gbkt-core (or merge into gbkt-world if split is too fine)
- Populate `gbkt-engine` with scene lifecycle, actor management, input, graphics fundamentals from gbkt-core
- Create `gbkt-all` convenience meta-module
- Complete `gbkt-bom` with all published modules
- Update all inter-module dependencies; verify acyclic dependency graph

**Done when:** `./gradlew build` passes across all modules including new ones. gbkt-bom includes all published modules. No circular dependencies. gbkt-core is lighter.

---

## C. Explorer Feature Parity (Gap #7, #15)

### Directive C1: System Visitor Implementation (Gap #15)

**Current state:** `SystemIRVisitorI` interface exists with `visitCameraSystem`, `visitSaveSystem`, `visitSoundSystem`, `visitExplorationSystem`, `visitDialogSystem`, `visitGenericSystem`. **No implementation class** for v2. `buildSystemFunctions()` only handles `GenericSystem` — all typed systems silently ignored.

**Directive:** Create `GBDKSystemVisitor` implementing `SystemIRVisitorI<List<CFunction>>`. Handle:
- `visitCameraSystem` → camera follow/shake globals and update function
- `visitSaveSystem` → SRAM read/write functions
- `visitExplorationSystem` → exploration state machine, movement grid, encounter checks
- `visitDialogSystem` → wire through visitor (dialog helpers already exist)

Wire into `buildSystemFunctions()` replacing the `filterIsInstance<GenericSystem>()` pattern.

**Done when:** `buildSystemFunctions()` generates real C code for all 5 system types. No system type is silently ignored.

### Directive C2: SpawnActor/DestroyActor Codegen (Gap #15 partial)

**Current state:** Emit `/* SpawnActor: dynamic entity pooling deferred */` comment stubs. No functional code.

**Directive:** Generate OAM slot management — `spawn_actor(id)` claims OAM slot from free list, `destroy_actor(id)` returns it. Reference v1 entity pool pattern.

**Done when:** SpawnActor/DestroyActor generate OAM slot management code, not comment stubs.

### Directive C3: SimpleBattle State Machine (Gap #15 partial)

**Current state:** `simpleBattle("combat")` produces a `GenericSystem` with `type="simple_battle"`. Codegen executes `onVictoryOps` immediately — no actual combat state machine.

**Directive:** Generate `COMBAT_STATE` enum (INIT, PLAYER_TURN, ENEMY_TURN, VICTORY, DEFEAT) and per-frame update function that drives state transitions. Reference v1 combat codegen pattern.

**Done when:** SimpleBattle generates a state machine with turn flow, not immediate victory execution.

### Directive C4: Scene Transition Tile Reuse (Gap #7)

**Current state:** Scene transitions always reload tileset data even when consecutive scenes share the same tileset.

**Directive:** Add `_current_tileset_id` global; guard tile load with `if (_current_tileset_id != new_tileset_id)`. Skip reload when tileset unchanged.

**Done when:** Transitioning between two scenes that share a tileset skips the tile reload.

---

## D. Collection System (Gaps #11, #12, #14)

### Directive D1: GBDK CollectionCodegen Implementation (Gap #12)

**Current state:** `CollectionCodegen` interface in gbkt-backend-api defines 8 methods (hash/pool/ring/slots x data/functions). GBDK backend does **not** implement it.

**Directive:** Create `GBDKCollectionCodegen` class implementing `CollectionCodegen`. Port existing extension function implementations (static arrays + bookkeeping) into the interface methods.

**Done when:** `GBDKCollectionCodegen` implements all 8 `CollectionCodegen` interface methods and generates valid C for each collection type.

### Directive D2: Collection IR Wiring (Gap #11)

**Current state:** Collection IR types exist but `collectionBytes` is always 0 in GameIR. DSL sugar (`val x by hashtable<T>(N)`) exists but the codegen trail is broken — collections don't flow through v2 GameIR.

**Directive:** Wire CollectionsIR data (hashtable, pool, ring buffer, fixed slots) from DSL recording through to `gameIR.collections`. Verify `hashtable<Item>(16)` compiles and records proper IR with element size.

**Done when:** `val items by hashtable<Item>(16)` DSL compiles, records IR, and flows through to GBDK backend codegen.

### Directive D3: Collection Memory Accounting (Gap #14)

**Current state:** `RAMPlanningPass.computeVariableBytes()` does not account for collection memory. `collectionBytes` is always 0.

**Directive:** Compute deterministic byte counts per collection type (hashtable: capacity * entry_size + bookkeeping; pool: capacity * element_size + bitmap; etc.) and include in RAM planning output. Add `BudgetReporter` section for collections.

**Done when:** `RAMPlanningPass` reports non-zero `collectionBytes` when collections exist. Budget report shows collection memory usage.

---

## E. DSL Completions (Gaps #1, #4, #8, #10, #17, #18)

### Directive E1: Palette Strict Mode (Gap #1)

**Current state:** `GBCColor.fromHex()` auto-quantizes to nearest RGB555 silently. No warning when precision is lost.

**Directive:** Add `PaletteValidation.strictMode` flag in `AnalysisConfig`. When enabled, colors that lose precision in RGB888→RGB555 conversion emit a WARNING diagnostic with original hex and nearest approximation. `GBCColor.fromRGB888()` already does the conversion — add round-trip check.

**Done when:** `GBCColor.fromHex(0xFF8844)` with strict mode emits RGB555 precision warning.

### Directive E2: GBC Hex Color RGB555 Warnings (Gap #4)

**Current state:** No warnings for color precision loss anywhere in the pipeline.

**Directive:** Wire strict mode check into palette builders (`PaletteBuilder.colors()`, `GBCColor.fromHex()`). Show original hex, RGB555 components, and approximate resulting color.

**Done when:** User writing `GBCColor.fromHex(0xF0A060)` sees a compiler warning about RGB555 precision loss when strict mode is on.

### Directive E3: Explicit Type Casting (Gap #8)

**Current state:** No `toU8()`, `toU16()`, `toI8()`, `toI16()` methods on Expr/AssignableVar/ActorPropertyRef.

**Directive:** Add type cast extension methods that emit `CastExpr(targetType, innerExpr)` in IR and generate `(UINT8)(expr)` etc. in C. Add `CastExpr` to Expr hierarchy and `visitCast` to ExprVisitor.

**Done when:** `score.toU16()` compiles and generates `(UINT16)(_score)` in C output.

### Directive E4: Sprite Frame Layout from DSL Metadata (Gap #10)

**Current state:** Sprite metadata doesn't include frame dimensions. Manual tile index management required for animated sprites.

**Directive:** Add `frameWidth` and `frameHeight` optional fields to sprite/asset references. When present, codegen generates proper `set_sprite_tile()` calls with frame offset calculation.

**Done when:** Sprite with `frameWidth(16)` generates correct frame offset calculation in C.

### Directive E5: Array Collection-Like Helpers (Gap #17)

**Current state:** `ArrayVar` has `exists`/`size` but no iteration or manipulation helpers.

**Directive:** Add `forEach()`, `indexOf()`, `count()`, `fill()` DSL methods on `ArrayVar`. Generate inline C loops (no function call overhead). `forEach` records a lambda body as a WhileOp.

**Done when:** `bricks.fill(0)` generates inline fill loop in C. `bricks.forEach { ... }` generates iteration loop.

### Directive E6: raw() Compiler Warning (Gap #18)

**Current state:** `raw()` has `@Deprecated` annotation in ScriptBuilder but no compile-time diagnostic.

**Directive:** Add a `SemanticValidationPass` check that counts `RawOp` instances and emits a WARNING diagnostic: "N raw() calls found — consider adding DSL support for these patterns". List each raw() call with its source location.

**Done when:** RawOp count appears in validation pass output with source locations.

---

## F. Compiler & Analysis (Gaps #9, #13, #16)

### Directive F1: Auto-Bitwise Optimization Pass (Gaps #9, #16)

**Current state:** No optimization pass for power-of-2 arithmetic patterns.

**Directive:** Add `BitwiseOptimizationPass` to gbkt-analysis that rewrites `x * 2` → `x << 1`, `x / 4` → `x >> 2`, `x % 8` → `x & 7` for power-of-2 constants. Pure IR-to-IR transformation. Only applies to unsigned integer types.

**Done when:** `x * 4` in IR optimizes to `x << 2` when BitwiseOptimizationPass runs.

### Directive F2: Cargo-Style Budget Report Polish (Gap #13)

**Current state:** `BudgetReporter` generates plain text output with basic formatting.

**Directive:** Add ANSI color output, percentage bars (`[====----] 50%`), per-scene breakdown, top-level summary with overall ROM size estimate. Match cargo's clean output style.

**Done when:** Budget report has color output, percentage bars, and per-scene breakdown.

---

## G. Tile-Specific Collision (absorbed Phase 5.3)

### Directive G1: TMX/LDtk Collision Layer Extraction

**Current state:** TMX/LDtk parsers exist in `AssetPipeline` but collision layer extraction is not implemented. Explorer dungeon floors treat all tiles as passable.

**Directive:** Parse "collision" layer from TMX XML; convert to flat `UINT8[]` array (0=passable, 1=wall). Store in `TilemapIR.collisionData`.

**Done when:** TMX file with collision layer produces non-empty `collisionData` array in TilemapIR.

### Directive G2: Collision Data Codegen

**Current state:** No `_map_collision(x, y)` function or collision array in generated C.

**Directive:** Generate `_map_collision(x, y)` function that reads `collision_data[y * MAP_WIDTH + x]`. Wire exploration movement to check collision before updating position.

**Done when:** `_map_collision(x, y)` returns correct walkability. Explorer player cannot walk through wall tiles.

### Directive G3: Entity + Tile Collision Coexistence

**Current state:** Entity-based obstacles exist independently.

**Directive:** Tile collision and entity collision are additive layers — check tile first, then entity obstacles. Movement blocked by either.

**Done when:** Entity obstacles still block movement alongside tile collision. Both work together without conflict.

---

## H. V1 Code Cleanup (absorbed Phase 5.1)

### Directive H1: Delete V1 IR and DSL Files

**Current state:** 37 v1 IR files (CoreIR.kt, BattleIR.kt, DialogIR.kt, etc.) and 8 v1 DSL files (RecordingContext.kt, LogicBlock.kt, etc.) remain in gbkt-core.

**Directive:** Delete all v1 IR and DSL files from gbkt-core. Cross-reference each file for live imports before deletion. Types like `GBCColor`, `GBCPalette`, `PaletteType` from CoreTypes.kt may still be referenced and need relocation.

**Done when:** No v1 IR or DSL files remain in gbkt-core. No dangling references. `./gradlew build` passes.

### Directive H2: Delete V1 Codegen

**Current state:** `GBDKCodeGenerator` (deprecated in Phase 2) and all v1 codegen files under `gbkt-backend-gbdk/.../codegen/core/`, `codegen/rpg/`, `codegen/world/`, `codegen/combat/`, `codegen/features/`, `codegen/graphics/`, `codegen/ui/`, `codegen/data/`, `codegen/emit/` still exist.

**Directive:** Delete GBDKCodeGenerator and all v1 codegen subdirectories. Preserve only v2 directories: `visitor/`, `pipeline/`, `ast/`.

**Done when:** All v1 codegen files deleted. Only v2 pipeline code remains. `./gradlew build` passes.

### Directive H3: Promote V2 Package Paths

**Current state:** V2 code lives under `ir/v2/` and `dsl/v2/` in gbkt-ir and gbkt-lang modules.

**Directive:** Rename `ir/v2/` contents to `ir/` and `dsl/v2/` contents to `dsl/`. Update all imports across the entire codebase.

**Done when:** No `v2/` subdirectories remain. No source file imports `*.v2.*` package paths. `./gradlew build` passes.

### Directive H4: Remove Empty gbkt-engine Module

**Current state:** gbkt-engine contains only `package-info.kt` — an empty placeholder.

**Directive:** Delete the empty module. Remove from `settings.gradle.kts`. (If module restructure [B2] decides to populate it instead, this directive becomes "populate gbkt-engine" rather than "delete it".)

**Done when:** gbkt-engine either deleted or meaningfully populated. No empty placeholder modules.

---

## I. IntelliJ Plugin DX (absorbed Phase 5.4)

### Directive I1: Source Map Viewer

**Current state:** IntelliJ plugin has substantial infrastructure (syntax highlighting, completion, navigation, editors, project wizard, build actions, emulator integration). Source maps generated by v2 pipeline (`.gbkt.map` JSON files). No DSL-to-C synchronized view.

**Directive:** Add split editor view: Kotlin DSL on left, generated C on right, synchronized scrolling. Read `.gbkt.map` to create line-to-line mappings. Cursor in DSL highlights corresponding C line, and vice versa.

**Done when:** Split editor view shows DSL ↔ C line mapping from `.gbkt.map`.

### Directive I2: Asset Ref Inspections

**Current state:** No validation of `asset("sprites/player.png")` references in the editor.

**Directive:** Add `InspectionTool` that validates asset references — check file exists in project asset directory. Red underline if missing. Quick-fix to create empty placeholder PNG.

**Done when:** Missing asset reference shows red underline. Quick-fix creates placeholder file.

### Directive I3: Budget Report Gutter Icons

**Current state:** Budget report data available after `./gradlew budgetReport` but not surfaced in editor.

**Directive:** Parse budget report output; display bank usage and VRAM budget as gutter icons next to `scene { }` and `actor { }` blocks. Green = under budget, yellow = >75%, red = over budget.

**Done when:** Budget gutter icons appear next to scene/actor blocks. Plugin loads in IntelliJ 2024.3+.

---

## Gap-to-Directive Mapping

| Gap # | Description | Directive |
|-------|-------------|-----------|
| 1 | Palette strict mode | E1 |
| 2 | Music/tracker format support | A1, A2 |
| 3 | Custom waveform definitions | A3 |
| 4 | GBC hex RGB555 warnings | E2 |
| 5 | JUnit 5 vs kotlin.test | B1 |
| 6 | Full module restructure | B2 |
| 7 | Scene transition tile reuse | C4 |
| 8 | Explicit type casting toU16() | E3 |
| 9 | Auto-bitwise optimization | F1 |
| 10 | Sprite frame layout metadata | E4 |
| 11 | DSL generic hashtable\<T\> | D2 |
| 12 | Backend CollectionCodegen trait | D1 |
| 13 | Cargo-style budget report | F2 |
| 14 | Collection memory accounting | D3 |
| 15 | Explorer v1 feature parity | C1, C2, C3 |
| 16 | Auto-bitwise optimization (dup of 9) | F1 |
| 17 | Array helpers beyond exists/size | E5 |
| 18 | raw() compiler warning | E6 |
| 19 | V1 cleanup (was Phase 5.1) | H1, H2, H3, H4 |
| 20 | Module restructure (was Phase 5.2) | B2 |
| 21 | Tile collision (was Phase 5.3) | G1, G2, G3 |
| 22 | IntelliJ Plugin DX (was Phase 5.4) | I1, I2, I3 |

**Total: 22 gaps → 26 directives across 9 domain areas**
