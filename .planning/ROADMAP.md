# Roadmap: gbkt Compiler Pipeline Rebuild

## Overview

The rebuild transforms gbkt from a fragile, string-concatenating prototype into a clean compiler pipeline: a Kotlin DSL records games into a sealed IR, nine ordered analysis passes annotate that IR with hardware resource assignments, and a structured C AST codegen layer emits bank-split C files for GBDK lcc. Five phases deliver the complete pipeline: IR and DSL stabilization, structured codegen with migration cut, asset pipeline and JVM test runner, analysis passes (the "GC for hardware" differentiator), and full integration validation across all three example games.

## Phases

- [x] **Phase 1: IR Foundation and DSL** - Sealed IR hierarchy and DSL recording context; all example games representable as IR (completed 2026-02-17)
- [x] **Phase 2: Structured Codegen and Migration Cut** - C AST replaces string codegen; old GBDKCodeGenerator deprecated (completed 2026-02-18)
- [x] **Phase 3: Asset Pipeline and JVM Test Runner** - PNG/TMX processing and ScriptOp JVM interpreter (completed 2026-02-18)
- [x] **Phase 3.1: Collection Abstractions** - First-class IR nodes for static collection patterns (hashtable, pool, ring buffer, fixed slots) (INSERTED) (completed 2026-02-18)
- [x] **Phase 4: Analysis Pass Pipeline** - Nine ordered passes; automatic bank, VRAM, OAM, RAM allocation (completed 2026-02-18)
- [x] **Phase 5: Integration and End-to-End Validation** - All three example ROMs compile and run through new pipeline (completed 2026-02-19)
- [x] **Phase 5.05: V2 Source Map Implementation** - Source map generation for v2 pipeline; lcc errors map to Kotlin DSL source (INSERTED) (completed 2026-02-19)
- [x] **Phase 05.05.1: V2 Codegen Runtime Completion** - Sprite rendering, OAM management, sound effects, and critical ScriptOp handlers ported from v1 to v2 pipeline (INSERTED) (completed 2026-02-20)
- [x] **Phase 05.05.2: V2 DSL Ergonomics** - Delegate-based variables, operator extensions, actor property references (INSERTED) (completed 2026-02-20)
- [x] **Phase 05.05.3: V2 DSL Ergonomics Completion** - Type-safe input API, scene references, actor name inference, collision DSL (INSERTED) (completed 2026-02-21)
- [x] **Phase 06: Complete Gap Closure** - All 22 audit gaps + absorbed phases (5.1, 5.2, 5.3, 5.4) resolved before UAT (completed 2026-02-21)
- [x] **Phase 06.1: V1 Feature Parity Port — Foundations** - Camera, movement, animation, save/load, physics, NPC pathfinding, CRawCode elimination (INSERTED) (completed 2026-02-21)
- [x] **Phase 06.2: V1 Feature Parity Port — UI Layer** - Dialogs, menus, status bars, text rendering (INSERTED) (completed 2026-02-22)
- [x] **Phase 06.3: V1 Feature Parity Port — World System** - Floors, zones, tilemaps, encounters, exploration (INSERTED) (completed 2026-02-22)
- [x] **Phase 06.4: V1 Feature Parity Port — Combat & Inventory** - Generic combat state machine, items, inventory (engine-level) (INSERTED) (completed 2026-02-23)
- [x] **Phase 06.5: V1 Feature Parity Port — RPG Package** - Stats, leveling, abilities, status effects, monster AI, RPG battle logic (INSERTED) (completed 2026-02-24)
- [x] **Phase 06.6: Deferred Gaps — DSL Ergonomics, GBC Support & Audio** - Close deferred DSL gaps, add GBC color support, implement full audio system (INSERTED) (completed 2026-02-24)
- [x] **Phase 06.7: Deferred Gaps — Entity System, Movement & World** - Entity pooling, smooth/physics movement, advanced world mechanics (INSERTED) (completed 2026-02-25)
- [x] **Phase 06.8: Deferred Gaps — Genre Packages & RPG Extensions** - Platformer/puzzle/sport genre packages, exercise RPG optional features (INSERTED) (completed 2026-02-25)
- [x] **Phase 06.9: Deferred Gaps — Infrastructure & Tech Debt** - HRAM/SRAM allocation, BG tile estimation, IR serialization, optimization passes (INSERTED) (completed 2026-02-26)
- [x] **Phase 06.10: V1 Feature Parity Port — Example Games** - Update Pong/Breakout/Explorer + new minimal platformer (INSERTED) (completed 2026-02-26)
- [x] **Phase 06.11: LabyrinthOfTheDragon Port** - Reference implementation; V1 content with V2 DSL; includes localization (INSERTED) (completed 2026-02-27)
- [ ] **Phase 07: UAT Gameplay Validation** - Manual play-testing and debugging of all ROMs in mGBA
- [ ] **Phase 08: Detekt and Tech Debt Cleanup** - Resolve remaining Detekt violations and code quality issues
- [ ] **Phase 09: IDE & Tooling** - IntelliJ plugin enhancements, live DSL feedback, localization editor, tilemap preview

## Phase Details

### Phase 1: IR Foundation and DSL
**Goal**: The sealed IR hierarchy is complete and stable; all game-domain concepts expressible without RPG-specific nodes; DSL builders record into IR; example games produce valid IR
**Depends on**: Nothing (first phase)
**Requirements**: IR-01, IR-02, IR-03, IR-04, DSL-01, DSL-02, DSL-03, DSL-04
**Success Criteria** (what must be TRUE):
  1. The IR module compiles independently with zero external dependencies
  2. Pong, Breakout, and Explorer DSL definitions exist and produce valid IR without any RPG-specific nodes in the output
  3. `ref()` calls to nonexistent targets fail the build with a clear error message
  4. All platform-annotation fields (bank slot, VRAM range, OAM slot) are nullable and null until analysis fills them
  5. `when(irNode)` exhaustive matching compiles without `else` branches across all IR node types
**Plans**: 4 plans

Plans:
- [x] 01-01-PLAN.md — Define sealed IR hierarchy (GameIR, SceneIR, ActorIR, SystemIR, ScriptOp, Expr, platform annotations) [TDD, wave 1]
- [x] 01-02-PLAN.md — Build DSL recording context (GameBuilder, SceneBuilder, RefRegistry, variable delegates, ScriptBuilder) [TDD, wave 2]
- [x] 01-03-PLAN.md — Write Pong, Breakout, and Explorer as v2 DSL definitions; Explorer uses gbkt-rpg for combat [execute, wave 3]
- [x] 01-04-PLAN.md — Create gbkt-rpg genre package (domain data classes, DSL builder extensions on GameBuilder) [TDD, wave 3]

### Phase 2: Structured Codegen and Migration Cut
**Goal**: C AST sealed hierarchy replaces all string-based emission; bank assignment is a typed field on C AST nodes; old GBDKCodeGenerator deprecated; Pong compiles through new pipeline
**Depends on**: Phase 1
**Requirements**: CGEN-01, CGEN-02, CGEN-03, CGEN-04, CGEN-05
**Success Criteria** (what must be TRUE):
  1. `CFunction`, `CStatement`, `CExpr`, `CType` sealed hierarchy exists in the codegen module with no counterpart in IR
  2. Bank assignment on CFunction is a typed field populated from IR annotations — no mutable `currentBank` variable anywhere in codegen
  3. The pretty-printer is the single file where C strings are assembled; no `line("")` calls exist outside it
  4. Pong compiles to a working .gb ROM through the new pipeline; the generated Pong C output contains no RPG symbol names
  5. `GBDKCodeGenerator` (the old string-based generator) is deprecated and unused by the new pipeline; deletion deferred to Phase 5 after all three games validated
**Plans**: 4 plans

Plans:
- [x] 02-01-PLAN.md — Build C AST sealed hierarchy (CFile, CFunction, CStatement, CExpr, CType) with bank as typed field [TDD, wave 1]
- [x] 02-02-PLAN.md — Build CEmitter pretty-printer with exhaustive pattern matching on all C AST node types [TDD, wave 2]
- [x] 02-03-PLAN.md — Build IR-to-C-AST domain visitors (ExprVisitor, ScriptOpVisitor, SceneVisitor, ActorVisitor) for Pong subset [TDD, wave 2]
- [x] 02-04-PLAN.md — Wire GBDKPipelineV2 into GBDKBackend; verify Pong end-to-end; deprecate GBDKCodeGenerator [execute, wave 3]

### Phase 3: Asset Pipeline and JVM Test Runner
**Goal**: Asset files (PNG, TMX) process into IR automatically as a Gradle task; game logic runs on JVM without an emulator using the ScriptOp interpreter
**Depends on**: Phase 1
**Requirements**: ASSET-01, ASSET-02, ASSET-03, ASSET-04, TEST-01, TEST-02, TEST-03
**Success Criteria** (what must be TRUE):
  1. PNG files convert to deduplicated 2bpp tile data with palette mapping using Java ImageIO (no new runtime dependency)
  2. TMX/LDtk map files parse into TilemapIR with tile indices and collision layers via `gradle processAssets`
  3. Sprite sheets slice into animation frames with metadata stored in SpriteSheetIR
  4. `gradle test` runs game logic tests for all three example games in under 5 seconds without GBDK installed
  5. SimulationContext supports frame advance, input simulation, and state variable assertion in test code
**Plans**: 4 plans

Plans:
- [ ] 03-01-PLAN.md — Build asset processing core: TileDeduplicator, TiledParser custom properties, LDtk parser, AssetManifest data model [TDD, wave 1]
- [ ] 03-02-PLAN.md — Replace ProcessAssetsTask stub with real Gradle asset processor; wire to build/generated/assets/ [execute, wave 2]
- [ ] 03-03-PLAN.md — Build ScriptOpInterpreter and SimulationContextV2 JVM test API [TDD, wave 1]
- [ ] 03-04-PLAN.md — Write scenario-based game logic tests for Pong, Breakout, Explorer; verify all pass under 5 seconds [execute, wave 2]

### Phase 3.1: Collection Abstractions (INSERTED)
**Goal**: First-class IR nodes for static collection patterns (IRHashTable, IRPool, IRRingBuffer, IRFixedSlots) with hybrid backend traits; DSL sugar for declaring collections; all compile to fully static C with no heap allocation
**Depends on**: Phase 1 (IR Foundation)
**Requirements**: Derived from Phase 3.1 context discussion
**Success Criteria** (what must be TRUE):
  1. IRHashTable, IRPool, IRRingBuffer, IRFixedSlots exist as sealed IR node types with compile-time-known byte counts
  2. DSL sugar (`val x by hashtable<T>(N)`, `by fixedSlots<T>(N)`, `by ringBuffer<T>(N)`) records into IR
  3. GBDK backend generates static arrays + runtime bookkeeping functions for each collection type
  4. Backend trait interface allows alternative implementations per target platform
  5. Collection sizes are deterministic and queryable at compile time for downstream RAM budget analysis
**Plans**: 3 plans

Plans:
- [ ] 03.1-01-PLAN.md — Collection IR nodes + sealed hierarchy updates + Game model wiring + backend trait interface [TDD, wave 1]
- [ ] 03.1-02-PLAN.md — DSL property delegates and domain wrapper classes for all 4 collection types [execute, wave 2]
- [ ] 03.1-03-PLAN.md — GBDK backend codegen for static C generation of all collection types [execute, wave 2]

### Phase 4: Analysis Pass Pipeline
**Goal**: All nine compiler passes run in order; IR nodes carry hardware resource annotations from analysis output; budget audit produces an actionable build report; bank allocation is fully automatic
**Depends on**: Phase 3.1 (RAM planning needs collection size data), Phase 3 (VRAM planning needs processed tile data from asset pipeline)
**Requirements**: ANLZ-01, ANLZ-02, ANLZ-03, ANLZ-04, ANLZ-05, ANLZ-06
**Success Criteria** (what must be TRUE):
  1. `gradle budgetReport` runs on all three example games and outputs per-bank size breakdowns and per-scene tile budgets
  2. A deliberately oversized scene (more than 384 unique tiles) causes a build failure with an actionable error message naming the scene and tile count
  3. Bank allocation is fully automatic — no bank-related DSL syntax exists, no manual bank annotations required from the developer
  4. OAM slot assignment and scanline density analysis run per-scene; scenes with projected scanline overflow produce warnings
  5. RAM layout (WRAM, HRAM, SRAM) is computed and annotated on IR nodes without developer intervention
**Plans**: 9 plans

Plans:
- [x] 04-01-PLAN.md — Build gbkt-analysis module skeleton (AnalysisPass, PassPipeline, PassContext, AnalysisConfig) [TDD, wave 1]
- [x] 04-02-PLAN.md — Implement SemanticValidation, ResourceInventory, and ConstraintCheck passes [TDD, wave 2]
- [x] 04-03-PLAN.md — Implement BankingAnalysisPass (FFD bin-packing, scene locality) [TDD, wave 3]
- [x] 04-04-PLAN.md — Implement VRAMLayoutPass (per-scene tile slots, hybrid dedup, overflow errors) [TDD, wave 3]
- [x] 04-05-PLAN.md — Implement OAMAllocationPass and RAMPlanningPass [TDD, wave 4]
- [x] 04-06-PLAN.md — Implement DeadCodeEliminationPass and ConstantFoldingPass [TDD, wave 4]
- [x] 04-07-PLAN.md — BudgetAuditPass + BudgetReporter + DefaultPipeline + GBDKBackend wiring [execute, wave 5]
- [x] 04-08-PLAN.md — Wire bankSlot into codegen (BANKED functions, trampolines) + Gradle budgetReport task [execute, wave 6]
- [ ] 04-09-PLAN.md — Gap closure: wire v2 games into examples, add tilesetRef to SceneIR for BG tile VRAM accounting [execute, wave 7]

### Phase 5: Integration and End-to-End Validation
**Goal**: All three example games compile to working .gb ROMs through the complete new pipeline (DSL → IR → analysis → codegen → lcc); the full Gradle task graph works end-to-end
**Depends on**: Phase 4
**Requirements**: INTG-01, INTG-02, INTG-03, INTG-04
**Success Criteria** (what must be TRUE):
  1. `gradle buildRom` on the Pong example produces a .gb ROM that runs correctly in mGBA
  2. `gradle buildRom` on the Breakout example produces a .gb ROM that runs correctly in mGBA
  3. `gradle buildRom` on the Explorer example produces a .gb ROM that runs correctly in mGBA
  4. The Gradle task graph (`processAssets` → `compileDsl` → `analyzeIR` → `generateC` → `compileRom`) executes with correct task ordering and incremental build support
**Plans**: 4 plans

Plans:
- [ ] 05-01-PLAN.md — Wire v2 GameBuilder bridge in Gradle plugin (GameBuilder detection, generateV2 routing, budgetReport flag) [execute, wave 1]
- [ ] 05-02-PLAN.md — Validate Pong end-to-end (ROM builds through full pipeline, boots in mGBA) [execute, wave 2]
- [ ] 05-03-PLAN.md — Validate Breakout end-to-end (ROM builds through full pipeline, boots in mGBA) [execute, wave 2]
- [ ] 05-04-PLAN.md — Validate Explorer end-to-end + ValidateRomTask (Explorer ROM builds, automated mGBA validation) [execute, wave 3]

### Phase 5.05: V2 Source Map Implementation (INSERTED)
**Goal**: The v2 pipeline generates source maps that map generated C lines back to Kotlin DSL source file:line; lcc compilation errors display DSL locations instead of C locations
**Depends on**: Phase 5 (v2 pipeline must be working end-to-end)
**Requirements**: SMAP-01, SMAP-02
**Success Criteria** (what must be TRUE):
  1. `GBDKPipelineV2` generates a `.gbkt.map` JSON file alongside each generated C file with C-line → Kotlin-file:line mappings
  2. When lcc reports a compilation error on a generated C line, the Gradle output shows the corresponding Kotlin DSL file:line
  3. The source map format is compatible with the existing `SourceMapLoader` and `ErrorEnhancer` infrastructure
  4. All three example games produce valid source maps during `generateC`
**Plans**: 3 plans

Plans:
- [ ] 05.05-01-PLAN.md — Source location capture in ScriptBuilder + CStatement sourceLocation + SourceMapCollector [execute, wave 1]
- [ ] 05.05-02-PLAN.md — CEmitter line tracking + GBDKPipelineV2 source map output + Gradle .gbkt.map file writing [execute, wave 2]
- [ ] 05.05-03-PLAN.md — SourceMapLoader v2 multi-file + ErrorEnhancer Rust-style formatting + IntelliJ multi-file maps [execute, wave 3]

### Phase 05.05.1: V2 Codegen Runtime Completion (INSERTED)
**Goal**: The v2 codegen generates C code that renders sprites, updates OAM per-frame, plays sounds, and produces playable games — not just compilable ROMs. Ports sprite rendering, sound effects, and critical ScriptOp handlers from v1 GBDKCodeGenerator to v2 GBDKPipelineV2.
**Depends on**: Phase 5.05 (source maps for debugging during integration)
**Requirements**: BOM-01, BOM-02, BOM-03, INTG-01, INTG-02, INTG-03
**Success Criteria** (what must be TRUE):
  1. Pong ROM in mGBA shows paddles and ball on screen; gameplay is visible and interactive with scoring and game-over transitions
  2. Breakout ROM in mGBA shows paddle, ball, and bricks with sound effects on collision
  3. Explorer ROM in mGBA shows player sprite with movement and scene transitions
  4. All existing `gbkt-core:test` and `gbkt-backend-gbdk:test` JVM simulation tests still pass
**Plans**: 7 plans (5 original + 2 gap closure)

Plans:
- [x] 05.05.1-01-PLAN.md — Unseal IR interfaces (ScriptOp, Expr, SystemIR) and create visitor pattern dispatch [execute, wave 1]
- [x] 05.05.1-02-PLAN.md — Create layered Gradle modules (gbkt-ir, gbkt-lang, gbkt-engine) and move files [execute, wave 2]
- [x] 05.05.1-03-PLAN.md — Sprite rendering pipeline: OAM init, update_sprites(), asset includes, sprite helpers [execute, wave 3]
- [x] 05.05.1-04-PLAN.md — Complete all 24 ScriptOp handlers + sound system + dialog/menu helpers [execute, wave 4]
- [x] 05.05.1-05-PLAN.md — Per-game integration validation: compile ROMs, verify gameplay in mGBA [execute, wave 5]
- [x] 05.05.1-06-PLAN.md — Add delay(), dpadAny(), arrayRef() DSL methods + codegen helpers [gap-closure, wave 1]
- [x] 05.05.1-07-PLAN.md — Add u8Array IR/DSL/codegen + rewrite all 8 raw() calls in example games [gap-closure, wave 2]

### Phase 05.05.2: V2 DSL Ergonomics (INSERTED)

**Goal**: Refactor v2 DSL from assembly-style syntax (assign/varRef/literal) to Kotlin-idiomatic syntax matching v1 ergonomics: delegate-based variables with set/+=/-= operators, direct actor property references (ball.y instead of varRef("ball.y")), implicit literal wrapping, and comparison operators on variable delegates. All three example games should read like natural Kotlin, not hand-written IR.
**Depends on:** Phase 05.05.1 (v2 codegen must be functionally complete before refactoring syntax)
**Success Criteria** (what must be TRUE):
  1. Variable declarations (`u8Var`, `i8Var`, `u8Array`) return delegates that support `set`, `+=`, `-=`, comparison operators directly
  2. Actor property references work as `ball.y` / `paddle.x` instead of `varRef("ball.y")`
  3. Integer literals auto-wrap (no manual `literal(N)` calls in game code)
  4. All three example games (Pong, Breakout, Explorer) rewritten with the new syntax and compile to identical C output
  5. All existing tests pass

**Requirements**: ERGO-01, ERGO-02, ERGO-03, ERGO-04, ERGO-05
**Plans**: 2 plans

Plans:
- [x] 05.05.2-01-PLAN.md — Build DSL ergonomic infrastructure (ScriptBuilderContext, AssignableVar operators, ActorPropertyRef, ArrayVar, Int extensions) [execute, wave 1]
- [x] 05.05.2-02-PLAN.md — Migrate all three example games to new syntax + deprecate old API + verify test suite [execute, wave 2]

### Phase 05.05.3: V2 DSL Ergonomics Completion (INSERTED)

**Goal**: Close the remaining DSL ergonomics gaps from Phase 05.05.2 CONTEXT.md that were dropped during planning. Eliminate all magic strings from the DSL surface: type-safe input API (`dpad.up.held` instead of `dpadHeld("up")`), type-safe scene references (`navigate(gameScene)` instead of `navigate("game")`), actor name inference from Kotlin property names, collision DSL (`ball.collides(paddle)` in examples), custom actor properties (`ball.dx` via `i8Prop`), and DMG/GBC color definition constants.
**Depends on:** Phase 05.05.2 (operator extensions and ScriptBuilderContext already in place)
**Success Criteria** (what must be TRUE):
  1. Zero `dpadHeld("string")` or `buttonPressed("string")` calls in any example game — replaced with typed input API (`dpad.up.held`, `buttons.a.pressed`)
  2. Zero `navigate("string")` calls in any example game — replaced with type-safe scene references
  3. Zero `actor("redundant_name")` calls where the string duplicates the Kotlin property name — actor name inferred from property
  4. All collision detection in example games uses `ball.collides(paddle)` instead of nested `whenever` blocks
  5. All existing tests pass
  6. Example games read like natural Kotlin with no magic strings

**Requirements**: ERGO-01, ERGO-02, ERGO-03, ERGO-04, ERGO-05
**Plans**: 4 plans

Plans:
- [x] 05.05.3-01-PLAN.md — Type-safe input API (dpad/buttons objects), actor delegate (name inference), DMG color constants [execute, wave 1]
- [x] 05.05.3-02-PLAN.md — Collision codegen + custom actor properties + migrate all three example games + documentation update [execute, wave 2]
- [x] 05.05.3-03-PLAN.md — Gap closure: sceneRef() forward declarations, eliminate all navigate("string"), ball.collides(paddle) in Breakout [gap-closure, wave 1]
- [x] 05.05.3-04-PLAN.md — Gap closure: fix 10 test failures in BreakoutIRTest, BreakoutGameTest, ExplorerGameTest [gap-closure, wave 2]

### Phase 06: Complete Gap Closure

**Goal**: Close all 22 audit gaps and absorbed roadmap phases (5.1, 5.2, 5.3, 5.4) before UAT. Sound system codegen, module restructure, Explorer feature parity, collection codegen, DSL completions, tile collision, V1 cleanup, and IntelliJ plugin DX — all resolved.
**Depends on**: Phase 05.05.3
**Requirements**: All gaps from CONTEXT-vs-PLAN audit + absorbed phases
**Success Criteria** (what must be TRUE):
  1. Sound effects generate actual NRxx register writes (not hashCode-based stubs)
  2. Music playback generates hUGETracker integration code in v2 pipeline
  3. All 5 system types (Camera, Save, Sound, Exploration, Dialog) generate real C code via SystemIRVisitor
  4. Module restructure complete: gbkt-world, gbkt-exploration, gbkt-all exist; gbkt-bom includes all modules
  5. CollectionCodegen implemented for GBDK backend; RAMPlanningPass accounts for collection memory
  6. Type casting (`toU16()`), bitwise optimization pass, palette strict mode all functional
  7. Tile collision system: TMX collision layers parsed, `_map_collision()` generated, walls block movement
  8. V1 code fully deleted; v2 package paths promoted; no `*.v2.*` imports remain
  9. IntelliJ plugin: source map viewer, asset ref inspections, budget gutter icons
  10. `./gradlew build` passes across all modules with zero compilation errors
**Plans**: 11 plans

Plans:
- [x] 06-01-PLAN.md — Delete all v1 IR, DSL, and codegen; relocate shared types; remove empty gbkt-engine [wave 1] (completed 2026-02-21)
- [ ] 06-02-PLAN.md — Promote v2 package paths (ir/v2/ → ir/, dsl/v2/ → dsl/); update all imports [wave 2]
- [ ] 06-03-PLAN.md — Create gbkt-world module; standardize test framework; complete gbkt-bom [wave 3]
- [ ] 06-04-PLAN.md — Sound register codegen, music/hUGETracker integration, waveform export, SoundSystem wiring [wave 3]
- [ ] 06-05-PLAN.md — GBDKSystemVisitor, SpawnActor/DestroyActor OAM, SimpleBattle state machine, tileset reuse [wave 3]
- [ ] 06-06-PLAN.md — GBDKCollectionCodegen, GameIR collection wiring, RAM accounting [wave 3]
- [ ] 06-07-PLAN.md — CastExpr type casting, palette strict mode, array helpers, sprite frames, raw() warning [wave 3]
- [ ] 06-08-PLAN.md — BitwiseOptimizationPass, BudgetReporter polish, tile collision system [wave 3]
- [ ] 06-09-PLAN.md — IntelliJ source map viewer, asset ref inspections, budget gutter icons [wave 3]
- [ ] 06-10-PLAN.md — Gap closure: populate gbkt-engine with v2 engine types, add gbkt-rpg to BOM [gap-closure, wave 1]
- [ ] 06-11-PLAN.md — Gap closure: wire SceneBuilder.collisionData() to SceneIR for end-to-end tile collision [gap-closure, wave 1]

### Phase 06.1: V1 Feature Parity Port — Foundations (INSERTED)

**Goal**: Port foundational V1 systems to V2 pipeline with improved DSL ergonomics. Eliminate all CRawCode stubs from Phase 06. Camera with bounds, per-actor movement, animation state machines, structured saves, physics primitives, NPC pathfinding with waypoints.
**Depends on**: Phase 06
**Success Criteria** (what must be TRUE):
  1. Camera system generates follow-target, screen shake, AND map-edge bounds clamping via typed C AST (no CRawCode)
  2. Movement controller supports per-actor configuration — grid, smooth, and physics modes coexist in same scene
  3. Sprite animation supports both simple frame sequences and condition-based state machines (opt-in)
  4. Save/load system generates structured SRAM with versioned slots, checksums, corruption detection; opt-out pattern (all vars saved by default, `transient()` to exclude)
  5. Physics primitives (velocity, acceleration, gravity, bounce) work as actor-level properties and generate correct per-frame update code
  6. NPC pathfinding generates A* grid pathfinding + waypoint-based patrol routes
  7. Zero CRawCode nodes remain in entire codebase — all replaced with typed C AST nodes
  8. All new features have both unit codegen tests and integration tests
  9. `./gradlew build` passes across all modules
**Plans**: 8 plans

Plans:
- [x] 06.1-01-PLAN.md — Camera system: extend IR with follow/bounds, CameraBuilder DSL, typed visitCameraSystem (zero CRawCode) [wave 1]
- [x] 06.1-02-PLAN.md — Save/load system: structured SRAM with versioned slots, checksums, transient vars, typed codegen [wave 1]
- [x] 06.1-03-PLAN.md — Movement controller + animation: per-actor MovementConfig, AnimationStateDef, state machines [wave 1]
- [x] 06.1-04-PLAN.md — Physics primitives: PhysicsConfig on ActorIR, PhysicsStep codegen (gravity, velocity, bounce) [wave 2]
- [x] 06.1-05-PLAN.md — NPC pathfinding: PathfindingSystem IR, iterative A*, waypoint patrol routes [wave 2]
- [x] 06.1-06-PLAN.md — CRawCode elimination sweep + integration tests + full build validation [wave 3]
- [x] 06.1-07-PLAN.md — Gap closure: fix ExplorerV2.kt saveData syntax (slots = 1 → slots(1)) [gap-closure, wave 1]
- [x] 06.1-08-PLAN.md — Gap closure: wire A* walkability to _map_collision() dispatch (replace _pf_collision_fn stub) [gap-closure, wave 1]

### Phase 06.2: V1 Feature Parity Port — UI Layer (INSERTED)

**Goal**: Port V1 UI subsystems to V2 pipeline: dialog boxes with text rendering, choice menus, status bars, HUD elements. Full V2 DSL style with typed builders.
**Depends on**: Phase 06.1
**Success Criteria** (what must be TRUE):
  1. Dialog system generates window-layer text rendering with auto-advance and player-dismiss
  2. Choice menus generate cursor navigation with selectable options and callbacks
  3. Status bars generate HUD overlays with dynamic value display (HP, score, etc.)
  4. Text rendering supports variable-width and fixed-width fonts via window layer
  5. All UI codegen uses typed C AST (zero CRawCode)
  6. Unit codegen tests and integration tests for all UI features
  7. `./gradlew build` passes
**Plans**: 5 plans

Plans:
- [ ] 06.2-01-PLAN.md — Dialog/Menu/HUD IR types + DSL builders (UITypes.kt, UIBuilders.kt, GameBuilder extensions) [wave 1]
- [ ] 06.2-02-PLAN.md — Dialog codegen: window-layer rendering, typewriter, choice, text helpers [wave 2]
- [ ] 06.2-03-PLAN.md — Menu codegen: VERTICAL/HORIZONTAL/GRID layouts, parent/child stack, SFX, settings controls, dynamic data binding [wave 3]
- [ ] 06.2-04-PLAN.md — HUD codegen: change-detection rendering, fill bars, numeric displays, icon counters [wave 4]
- [ ] 06.2-05-PLAN.md — Integration tests, example game updates, CRawCode audit, full build validation [wave 5]

### Phase 06.3: V1 Feature Parity Port — World System (INSERTED)

**Goal**: Port V1 world/dungeon subsystems to V2 pipeline: floor navigation, zone loading, tilemap rendering, random encounter system, exploration state machine. Full V2 DSL style. Also close two inherited gaps: entity obstacle detection (Directive G3 from Phase 06) and AudioMixer real implementation (Directive A5 — replace stubs with working channel group volume/mute/unmute).
**Depends on**: Phase 06.2 (UI needed for in-dungeon dialogs/menus)
**Success Criteria** (what must be TRUE):
  1. Floor/zone system generates zone-based tilemap loading with bank-split data
  2. Exploration state machine generates grid movement, step counting, encounter checks
  3. Random encounter system generates weighted encounter tables and battle triggers
  4. Zone transitions generate tilemap swap with optional tileset reuse optimization
  5. Entity obstacle detection: exploration movement checks entity collision in addition to tile collision — players cannot walk through NPCs/obstacles (Directive G3)
  6. AudioMixer generates real channel group state tracking and NRxx register manipulation — set_group_volume(), mute_group(), unmute_group() have working implementations, not stubs (Directive A5)
  7. All world codegen uses typed C AST (zero CRawCode)
  8. Unit codegen tests and integration tests for all world and gap-closure features
  9. `./gradlew build` passes
**Plans**: 5 plans

Plans:
- [ ] 06.3-01-PLAN.md — World IR types (ZoneIR, EncounterTableIR, GlobalFlagsIR), ExplorationSystem expansion, GameIR zones/flags fields, DSL builders [wave 1]
- [ ] 06.3-02-PLAN.md — World codegen: exploration step/encounter/gauge, zone tilemap loading, zone transitions [wave 2]
- [x] 06.3-03-PLAN.md — Entity obstacle detection: collision modes (BLOCK/PASSTHROUGH/BLOCK_AND_TRIGGER/OVERLAP_TRIGGER/PUSH), entity grid, push mechanics [wave 2]
- [ ] 06.3-04-PLAN.md — AudioMixer real implementation: NR50/NR51 register writes, channel groups, mute/unmute/fade, master volume [wave 2]
- [ ] 06.3-05-PLAN.md — Integration tests, Explorer example updates, CRawCode audit, full build validation [wave 3]

### Phase 06.4: V1 Feature Parity Port — Combat & Inventory (INSERTED)

**Goal**: Port engine-level combat and inventory systems to V2 pipeline. Generic combat state machine (not RPG-specific), turn-based flow, damage calculation framework, item definitions, inventory management. These are engine features usable by any game genre.
**Depends on**: Phase 06.3
**Success Criteria** (what must be TRUE):
  1. Combat state machine generates proper state enum and per-frame update (INIT → PLAYER_TURN → ENEMY_TURN → VICTORY/DEFEAT)
  2. Engine provides INIT/PLAYER_TURN/ENEMY_TURN states; turn-order strategy is RPG concern deferred to Phase 06.5
  3. Damage calculation framework generates configurable formula evaluation (damage dispatcher calls user-provided extern function)
  4. Combatants have ID, side, and canAct fields
  5. Declarative victory/defeat conditions (onVictoryWhen/onDefeatWhen) generate per-frame predicate checks that auto-trigger state transitions
  6. Hierarchical states supported — sub-states with parent-child mapping and parent_state query helper
  7. Item system generates item definitions with categories (including category-level default stacking rules), stacking, use effects
  8. Inventory system generates slot-based storage with add/remove/query operations
  9. All combat/inventory codegen uses typed C AST (zero CRawCode)
  10. gbkt-bom and gbkt-all updated to include combat/inventory in gbkt-engine
  11. Unit codegen tests and integration tests
  12. `./gradlew build` passes
**Plans**: 4 plans

Plans:
- [ ] 06.4-01-PLAN.md — Combat/Inventory IR types + DSL builders + engine types + GameIR/SystemIRVisitorI updates [wave 1]
- [ ] 06.4-02-PLAN.md — Combat codegen: CombatVisitor, deferred state machine, GBDKPipelineV2 globals [wave 2]
- [ ] 06.4-03-PLAN.md — Inventory codegen: InventoryVisitor, item catalog, container ops, drop tables [wave 2]
- [ ] 06.4-04-PLAN.md — Integration tests, menu codegen alignment, CLAUDE.md updates, full build validation [wave 3]

### Phase 06.5: V1 Feature Parity Port — RPG Package (INSERTED)

**Goal**: Port RPG-specific features to gbkt-rpg package: character stats and leveling, abilities with targeting and effects, status effects, monster definitions with AI, RPG-specific battle logic layered on engine combat. Clear boundary: gbkt-rpg depends on engine combat, not the reverse.
**Depends on**: Phase 06.4 (engine combat must exist first)
**Success Criteria** (what must be TRUE):
  1. Character stats system generates stat structures with HP/SP/ATK/DEF/etc. and level-up progression
  2. Ability system generates targeting resolution, cost deduction, effect application
  3. Status effect system generates per-turn tick, duration tracking, stacking
  4. Monster definition system generates stat blocks, drop tables, AI behavior trees
  5. RPG battle logic layers on engine combat state machine with abilities, items, flee
  6. ATB (Active Time Battle) combat type implemented in RPG package, extending engine CombatEngineSystem
  7. Tactical grid combat type implemented in RPG package, extending engine CombatEngineSystem
  8. Wave-survival combat type implemented in RPG package, extending engine CombatEngineSystem
  9. Turn order system (speed-based and fixed-order strategies) implemented in RPG package
  10. Equipment system (equip slots, stat modifiers) implemented in RPG package
  11. Migrate existing simpleBattle in gbkt-rpg to use engine CombatEngineSystem internally
  12. gbkt-rpg has zero dependencies on engine internals — uses public API only
  13. All RPG codegen uses typed C AST (zero CRawCode)
  14. Inventory menus display item names (not raw UINT8 IDs) — InventoryVisitor generates `_item_names[]` const lookup table; InventoryDataSource menu codegen uses it
  15. Unit codegen tests and integration tests
  16. `./gradlew build` passes
**Plans**: 12 plans

Plans:
- [x] 06.5-01-PLAN.md — Character stats, leveling, RpgVisitor skeleton, _item_names[] table [wave 1]
- [x] 06.5-02-PLAN.md — Ability system and status effects DSL + domain types [wave 1]
- [x] 06.5-03-PLAN.md — Monster AI (behavior trees), ability/effect/AI codegen, ability dispatch [wave 2]
- [x] 06.5-04-PLAN.md — Equipment system + class/job system DSL and codegen [wave 2]
- [x] 06.5-05-PLAN.md — ATB combat variant + turn order system [wave 2]
- [x] 06.5-06-PLAN.md — Tactical grid combat variant + AoE targeting [wave 2]
- [x] 06.5-07-PLAN.md — Economy/shops, party management, save integration, ability learning, flee/item combat actions [wave 3]
- [ ] 06.5-08-PLAN.md — simpleBattle migration, integration tests, full build validation [wave 4]
- [x] 06.5-09-PLAN.md — Wave-survival combat variant [wave 2]
- [x] 06.5-10-PLAN.md — Combat loop hooks (CombatHookPoint, hook dispatch codegen) [wave 2]
- [ ] 06.5-11-PLAN.md — Gap closure: ATB gauge fill loop for all combatants + turn order implementations [gap-closure, wave 1]
- [ ] 06.5-12-PLAN.md — Gap closure: tactical grid BFS, LOS, facing/elevation bonus, AoE targeting [gap-closure, wave 2]

### Phase 06.6: Deferred Gaps — DSL Ergonomics, GBC Support & Audio (INSERTED)

**Goal**: Close deferred DSL ergonomics gaps, add GBC color support, and implement the full audio system. These are foundational improvements that should be in place before example games showcase the framework.
**Depends on**: Phase 06.5
**Success Criteria** (what must be TRUE):
  1. `InputRef.released` property exists with corresponding `button_released` C helper generated by codegen
  2. `dpad.x` and `dpad.y` axis helpers return -1/0/1 `Expr` values for directional input
  3. `ArrayVar.get()` supports safe nullable access with bounds checking
  4. Generic `<T>` collection DSL syntax works (`hashtable<TileHashEntry>(64)`) — requires struct element types
  5. Struct element types supported for collections (non-primitive types like `TileHashEntry`)
  6. Old deprecated API methods (`assign()`, `varRef()`, `literal()`, `raw()`) escalated from WARNING to ERROR
  7. `gbc(r, g, b)` color helper converts RGB to RGB555 with fidelity warning at build time
  8. GBC hex color helpers available alongside DMG shade constants in `DmgColor`/`GbcColor`
  9. GBC color palette preset library with commonly-used palettes
  10. AudioMixer full priority mixing: channel priority scheduling, not just stub functions
  11. hUGETracker `.uge` to C conversion tooling integrated into asset pipeline
  12. Tracker-format background music (hUGEDriver or GBT Player) generates working playback code (ADV-03)
  13. `./gradlew build` passes across all modules
**Plans**: 5 plans

**Deferred Item Traceability:**
- A1: `InputRef.released` + `button_released` C helper (origin: Phase 05.05.3 — RESEARCH.md recommended deferral)
- A2: `dpad.x` / `dpad.y` axis helpers (origin: Phase 05.05.3 — TODO left in `InputBuilders.kt`)
- A3: Nullable `ArrayVar.get()` (origin: Phase 05.05.2 — deferred; `exists()` workaround)
- A4: Generic `<T>` collection DSL syntax (origin: Phase 03.1 — deferred pending struct types)
- A5: Struct element types for collections (origin: Phase 03.1 — only primitives supported)
- A6: Deprecation escalation WARNING → ERROR (origin: Phase 05.05.2 — planned future step)
- B1: `gbc(r,g,b)` color helper (origin: Phase 05.05.3 — deferred per CONTEXT.md)
- B2: GBC hex color helpers (origin: Phase 05.05.3 — only DMG 4-shade constants exist)
- B3: GBC color palette preset library (origin: Phase 05.05.2 — could be its own phase)
- C1: AudioMixer full priority mixing (origin: Phase 06 — stubs only, full mixing post-Phase-06)
- C2: hUGETracker `.uge` to C tooling (origin: Phase 06 — declared out of scope)
- C3: Tracker-format BGM / hUGEDriver (origin: Phase 05.05.1 — ADV-03, deferred)

Plans:
- [x] 06.6-01-PLAN.md — DSL ergonomics: input released, dpad axis, array bounds, old API deletion
- [x] 06.6-02-PLAN.md — GBC color support: helpers, palette builder, presets, target config
- [x] 06.6-03-PLAN.md — Audio system: music DSL, scene auto-management, .uge pipeline
- [x] 06.6-04-PLAN.md — Struct types and generic collections
- [ ] 06.6-05-PLAN.md — Integration verification: full build gate

### Phase 06.7: Deferred Gaps — Entity System, Movement & World (INSERTED)

**Goal**: Close deferred entity, movement, and world system gaps. Entity pooling enables spawn/destroy patterns (Breakout bricks, bullets). Smooth and physics movement modes enable platformer and action games. Advanced world mechanics add depth to exploration.
**Depends on**: Phase 06.6
**Success Criteria** (what must be TRUE):
  1. `SpawnActor` and `DestroyActor` ScriptOp handlers generate real entity pool management code (not no-op stubs)
  2. Entity pooling IR representation exists; DSL supports pool-based spawn/destroy patterns
  3. NPC-to-NPC collision detection supported (opt-in per entity)
  4. Smooth/free movement has a concrete implementation (not just the `MovementStyle` interface)
  5. PHYSICS `MovementStyle` generates real movement functions with gravity, velocity, acceleration
  6. Fixed-point physics (4.4 or 8.8) available for sub-pixel accuracy in platformer movement
  7. Advanced puzzle mechanics: switches, pressure plates, timed blocks as world interaction types
  8. Zone tilemap bank allocation supports complex multi-zone games (banked tilemap data)
  9. `./gradlew build` passes across all modules
**Plans**: 10 plans

**Deferred Item Traceability:**
- D1: Entity pooling / SpawnActor / DestroyActor (origin: Phase 01 — deferred since initial IR design; no-op stubs since Phase 05.05.1)
- D2: NPC-to-NPC collision (origin: Phase 06.3 — player-only collision by design; NPC-NPC explicitly excluded)
- E1: Smooth/free movement implementation (origin: Phase 06.3 — interface exists, only GRID is concrete)
- E2: PHYSICS MovementStyle (origin: Phase 06.1 — `generateMovementFunction()` returns empty list for PHYSICS)
- E3: Fixed-point physics (origin: Phase 06.1 — integer pixels/frame chosen; 4.4 flagged for platformers)
- F1: Advanced puzzle mechanics (origin: Phase 06.3 — switches, pressure plates, timed blocks deferred)
- F2: Zone tilemap bank allocation (origin: Phase 06.3 — deferred to LotD-scale games, now needed before 06.11)

Plans:
- [x] 06.7-01-PLAN.md — Entity Pool Core (spawn/destroy IR, DSL, codegen) — DONE 2026-02-25 (be5152f)
- [x] 06.7-02-PLAN.md — Pool Operations (forEachActive, activeCount, destroyAll, per-instance props, death callbacks) — DONE 2026-02-25 (126881f, 23c48b3)
- [x] 06.7-03-PLAN.md — NPC-NPC Collision (groups, rules, BLOCK/BOUNCE/OVERLAP/PUSH responses) — DONE 2026-02-25 (7da9e06, 4d99303)
- [x] 06.7-04-PLAN.md — SMOOTH Movement (acceleration, friction, diagonal normalization) — DONE 2026-02-25 (d98e80e, 24515a3)
- [x] 06.7-05-PLAN.md — PHYSICS Movement (variable-height jump, coyote time, wall response, wall-jump) — DONE 2026-02-25 (864ce5d, a0caa76)
- [x] 06.7-06-PLAN.md — Fixed-Point System (4.4/8.8 sub-pixel arithmetic, physics integration) — DONE 2026-02-25 (1afff9e, 54bb1bd)
- [x] 06.7-07-PLAN.md — Puzzle Core (switch, pressure plate, timed block) — DONE 2026-02-25 (8434a74, e758959)
- [x] 06.7-08-PLAN.md — Puzzle Advanced (requires chaining, hidden/reveal, generic trigger, full event set) — DONE 2026-02-25 (cfdac33, 1a75227)
- [x] 06.7-09-PLAN.md — Zone Tilemap Banking (auto-allocation, bank override, bank-switching) — DONE 2026-02-25 (4df2e1f, 29868f3)
- [ ] 06.7-10-PLAN.md — Integration Tests (end-to-end cross-feature validation)

### Phase 06.8: Deferred Gaps — Genre Packages & RPG Extensions (INSERTED)

**Goal**: Implement genre packages for platformer, puzzle, and sport/racing games. Exercise and validate all RPG optional extensions that were implemented as opt-in flags but never tested in example code. Action RPG / roguelike styles promoted to first-class RPG variants.
**Depends on**: Phase 06.7 (entity pooling and physics movement needed for platformer package)
**Success Criteria** (what must be TRUE):
  1. Platformer genre package exists with platform detection, jump mechanics, scrolling camera
  2. Puzzle genre package exists with match logic, board state management
  3. Sport/racing genre package exists (at minimum: framework skeleton with timer, score, lap/round tracking)
  4. Action RPG / roguelike promoted to first-class RPG combat styles
  5. Equipment upgrade/enchant system (`EquipmentDef.upgradeEnabled` / `enchantEnabled`) validated with codegen test
  6. Equipment durability system (`EquipmentDef.durability`) validated with codegen test
  7. Equipment elemental affinity (`EquipmentDef.aspect`) validated with codegen test
  8. Ability mastery system (`AbilityLearning.masteryEnabled`) validated with codegen test
  9. Ability evolution chains (`AbilityLearning.evolutionChain`) validated with codegen test
  10. Party reserve/bench + EXP sharing (`PartyConfig.reserveSize`) validated with codegen test
  11. Row-based formation (`PartyConfig.formationEnabled`) validated with codegen test
  12. Crafting module (recipe-based `crafting { }` DSL) validated with codegen test
  13. Auto-save trigger system (`RpgSaveConfig.autoSave`) validated with codegen test
  14. New Game+ carry-over (`RpgSaveConfig.newGamePlus`) validated with codegen test
  15. Multiple currency types implemented (currently single currency only)
  16. `./gradlew build` passes across all modules
**Plans**: 10 plans

**Deferred Item Traceability:**
- G1: Platformer genre package (origin: Phase 01 — design noted, implementation deferred)
- G2: Puzzle genre package (origin: Phase 01 — design noted, implementation deferred)
- G3: Sport/racing genre package (origin: Phase 06.4 — future milestone)
- G4: Action RPG / roguelike as first-class styles (origin: Phase 06.5 — secondary priority by design)
- H1: Equipment upgrade/enchant (origin: Phase 06.5 — opt-in flag exists, never tested)
- H2: Equipment durability (origin: Phase 06.5 — opt-in flag exists, never tested)
- H3: Equipment elemental affinity (origin: Phase 06.5 — opt-in flag exists, never tested)
- H4: Ability mastery system (origin: Phase 06.5 — opt-in flag exists, never tested)
- H5: Ability evolution chains (origin: Phase 06.5 — opt-in flag exists, never tested)
- H6: Party reserve/bench + EXP sharing (origin: Phase 06.5 — opt-in flag exists, never tested)
- H7: Row-based formation (origin: Phase 06.5 — opt-in flag exists, never tested)
- H8: Crafting module (origin: Phase 06.5 — recipe-based only, opt-in DSL)
- H9: Auto-save (origin: Phase 06.5 — trigger-based, opt-in config)
- H10: New Game+ (origin: Phase 06.5 — opt-in config)
- H11: Multiple currency types (origin: Phase 06.5 — single currency design choice, not implemented)

Plans:
- [ ] 06.8-01-PLAN.md — Rename gbkt-rpg to gbkt-genre-rpg + GenreSystemVisitor extension point
- [ ] 06.8-02-PLAN.md — RPG H-item codegen tests (H2-H10)
- [ ] 06.8-03-PLAN.md — Multi-currency system (H11)
- [ ] 06.8-04-PLAN.md — Action RPG and Roguelike combat styles (G4)
- [ ] 06.8-05-PLAN.md — Platformer genre package domain + DSL (G1)
- [ ] 06.8-06-PLAN.md — Puzzle genre package domain + DSL (G2)
- [ ] 06.8-07-PLAN.md — Sport/racing genre package domain + DSL (G3)
- [ ] 06.8-08-PLAN.md — Genre codegen visitors (platformer, puzzle, sport) + ServiceLoader
- [ ] 06.8-09-PLAN.md — Shared pickup system in gbkt-engine
- [ ] 06.8-10-PLAN.md — Full build integration validation

### Phase 06.9: Deferred Gaps — Infrastructure & Tech Debt (INSERTED)

**Goal**: Close infrastructure gaps: HRAM/SRAM allocation, accurate BG tile estimation, collection simulation, module reorganization. Add IR serialization for non-JVM frontends. Implement optimization passes. Clean up accumulated Detekt violations from earlier phases (overlap with Phase 08 — Phase 08 handles any remaining violations after this phase).
**Depends on**: Phase 06.8
**Success Criteria** (what must be TRUE):
  1. HRAM allocation in `RAMPlanningPass` supports DSL-targeted HRAM variables (not hard-coded to 0)
  2. SRAM allocation supports v2 save system (not hard-coded to 0)
  3. `estimateBgTiles()` in `VRAMLayoutPass` reads actual tile count from asset pipeline (not constant 256)
  4. Module reorganization audit complete: gbkt-core → gbkt-engine boundary clarified and enforced
  5. IR serialization format (JSON or protobuf) enables non-JVM frontends to consume/produce IR
  6. Headless emulator test infrastructure exists for automated ROM behavior verification in CI
  7. ROM size optimization pass: shared constant tables, function deduplication, dead code elimination in generated C
  8. Accumulated Detekt violations from Phases 03.1–06.5 resolved (VRAMLayoutPass params, ScriptOpInterpreter complexity, file naming, etc.)
  9. Constant-folding optimization pass implemented (enabled by `Expr.sourceLocation` from Phase 05.05)
  10. Collection operations simulated in `SimulationContext` (not just no-op stubs)
  11. `./gradlew build` passes across all modules
  12. `./gradlew detekt` violation count significantly reduced
**Plans**: 7 plans

**Deferred Item Traceability:**
- J1: HRAM allocation hard-coded to 0 (origin: Phase 04 — no DSL targets HRAM; extension point)
- J2: SRAM allocation hard-coded to 0 (origin: Phase 04 — no v2 save system at the time)
- J3: `estimateBgTiles()` constant 256 heuristic (origin: Phase 04 — asset file I/O deferred)
- J4: Module reorganization audit (origin: Phase 06.4 — separate cleanup phase)
- J5: IR serialization JSON/protobuf (origin: Phase 05.05.1 — deferred post-v1 launch)
- J6: Headless emulator tests (origin: Phase 05.05.1 — considered but rejected for that phase)
- J7: ROM size optimization pass (origin: Phase 06.1 — "correctness first, optimization later")
- J8: Accumulated Detekt violations (origin: Phases 03.1, 04, 05.05.2 — multiple deferred-items.md files)
- J9: Constant-folding pass (origin: Phase 05.05 — enabled by sourceLocation, not yet built)
- J10: Collection operations not simulated (origin: Phase 03.1 — design choice matching hardware no-op pattern)

Plans:
- [ ] 06.9-01-PLAN.md — Analysis pass fixes: HRAM/SRAM allocation, AssetManifest-aware BG tile estimation (J1/J2/J3)
- [ ] 06.9-02-PLAN.md — Collection simulation in ScriptOpInterpreter (J10)
- [ ] 06.9-03-PLAN.md — Optimization toggles and report (J7/J9)
- [ ] 06.9-04-PLAN.md — IR JSON serialization with round-trip support (J5)
- [ ] 06.9-05-PLAN.md — Module boundary enforcement and Detekt cleanup (J4/J8)
- [ ] 06.9-06-PLAN.md — Headless emulator test infrastructure (J6)
- [ ] 06.9-07-PLAN.md — Integration validation: wire changes, full build gate (all J1-J10)

### Phase 06.10: V1 Feature Parity Port — Example Games (INSERTED)

**Goal**: Update all 3 existing example games (Pong, Breakout, Explorer) to final V2 DSL syntax and add 5 new genre example games (Platformer DMG/GBC, RPG-lite, Dungeon Crawler, Shmup, Top-down Racer). Each example is a self-contained, working mini-game showcasing specific gbkt V2 features. All deferred gaps from Phases 06.6-06.9 are now closed, so examples exercise the full framework.
**Depends on**: Phase 06.9 (all deferred gaps closed before examples)
**Success Criteria** (what must be TRUE):
  1. Pong example updated to use all V2 DSL improvements; compiles to working ROM
  2. Breakout example updated with V2 syntax; compiles to working ROM
  3. Explorer example updated to use V2 world/combat/RPG features; compiles to working ROM
  4. New minimal platformer example (player + platforms + gravity + jump) validates physics system; compiles to working ROM
  5. All example games mirror GBDK repo example patterns where applicable
  6. `./gradlew build` passes across all example modules
**Plans**: 16 plans

Plans:
- [x] 06.10-01-PLAN.md — Module scaffolding (platformer, platformer-gbc, shmup, racer)
- [x] 06.10-02-PLAN.md — Existing examples audit (Pong sound, Explorer .po, build normalization)
- [x] 06.10-03-PLAN.md — RPG-lite game definition + localization
- [x] 06.10-04-PLAN.md — Dungeon crawler game definition
- [x] 06.10-05-PLAN.md — Platformer DMG game definition
- [x] 06.10-06-PLAN.md — Platformer GBC variant
- [x] 06.10-07-PLAN.md — Shmup game definition
- [x] 06.10-08-PLAN.md — Racer game definition
- [x] 06.10-09-PLAN.md — RPG-lite tests (IR + simulation)
- [x] 06.10-10-PLAN.md — Dungeon tests (IR + simulation)
- [x] 06.10-11-PLAN.md — Platformer DMG + GBC tests
- [x] 06.10-12-PLAN.md — Shmup tests (IR + simulation)
- [x] 06.10-13-PLAN.md — Racer tests (IR + simulation)
- [x] 06.10-14-PLAN.md — Top-level + beginner/intermediate docs
- [x] 06.10-15-PLAN.md — Genre/advanced example docs
- [x] 06.10-16-PLAN.md — Integration validation + roadmap update

### Phase 06.11: LabyrinthOfTheDragon Port (INSERTED)

**Goal**: Port LabyrinthOfTheDragon to V2 as the flagship reference implementation. Same game content (dungeons, monsters, items, dialog) with better V2 DSL code. Includes PO-based localization. V1 source (git commit f82518e) + assets as reference. LotD drives RPG package fixes discovered during porting. Runs parallel with UAT (Phase 07).
**Depends on**: Phase 06.10
**Success Criteria** (what must be TRUE):
  1. All 40+ V1 LotD Kotlin source files ported to V2 DSL with idiomatic syntax
  2. LotD ROM compiles and boots in mGBA with same gameplay as V1
  3. V2 improvements visible where possible (faster transitions, better bank usage)
  4. PO-based localization support functional for all game strings
  5. LotD serves as well-documented reference implementation for complex V2 games
  6. Any gbkt-rpg issues discovered during porting are fixed
  7. `./gradlew build` passes including LotD module
**Plans**: 18 plans

Plans:
- [x] 06.11-01-PLAN.md — Asset deduplication and build.gradle.kts cleanup
- [x] 06.11-02-PLAN.md — Framework gap audit (Original C vs V2 DSL)
- [x] 06.11-03-PLAN.md — Fix BLOCKING framework gaps (typed refs, DSL capabilities)
- [x] 06.11-03b-PLAN.md — DSL capability gaps: zone objects, encounter levels, exit condition flags
- [x] 06.11-04-PLAN.md — PO auto-padding and compile-time locale selection
- [ ] 06.11-05-PLAN.md — Game skeleton (entry point, config, state, IR test scaffold)
- [ ] 06.11-06-PLAN.md — RPG foundation (characters, status effects, items)
- [ ] 06.11-07-PLAN.md — Monster definitions (12 monsters)
- [ ] 06.11-08-PLAN.md — Ability definitions (24+ abilities, 4 class files)
- [ ] 06.11-09-PLAN.md — Combat system configuration
- [ ] 06.11-10-PLAN.md — Supporting systems (palettes, sounds, save, status icons)
- [x] 06.11-11-PLAN.md — Non-gameplay scenes (title, hero select, gameover, victory)
- [x] 06.11-12-PLAN.md — Gameplay scenes (battle, exploration, pause)
- [x] 06.11-13-PLAN.md — Dungeon floors 1-4 with encounters/objects
- [x] 06.11-14-PLAN.md — Dungeon floors 5-8 (including dragon boss)
- [ ] 06.11-15-PLAN.md — Integration wiring and comprehensive tests
- [ ] 06.11-16-PLAN.md — en.po audit and completion
- [ ] 06.11-17-PLAN.md — Czech translation (cs.po draft)
- [ ] 06.11-18-PLAN.md — Final validation, zero-magic-string audit, docs update

### Phase 06.12: Embedded Emulator Core and Debug Loop (INSERTED)

**Goal:** Embed a Game Boy emulator as a JVM library (`gbkt-emulator` module) to close the developer debug loop. Replace the fire-and-forget `runEmulator` Gradle task with an integrated emulator that captures `EMU_printf` debug output to a log file in real-time. This enables Claude-assisted debugging (read `build/gbkt/logs/debug.log`) and lays the foundation for the IntelliJ-embedded emulator (Phase 09).
**Depends on:** Phase 06.11
**Requirements**: EMU-01, EMU-02, EMU-03, EMU-04, EMU-05, EMU-06, EMU-07

**Technical Decisions (from research):**

**Primary emulator core: Coffee-GB** (`eu.rekawek.coffeegb:core` on Maven Central)
- Pure Java 16, zero native code, zero GUI deps in core module
- MIT license, 1,149 stars, actively maintained (Feb 2026)
- Passes all Blargg test suites (cpu_instrs, instr_timing, mem_timing-2, cgb_sound, halt_bug)
- Full GB + GBC support, MBC1-MBC7 (MBC5 critical for GBDK banking)
- Proven headless API: `GameboyConfiguration` → `Gameboy.tick()` → `EventBus` frame events
- Memory access: `getAddressSpace().getByte(addr)` / `setByte(addr, val)`
- CPU access: `getCpu().getRegisters()` (PC, SP, AF, BC, DE, HL)
- Frame stepping: `tick()` returns per T-cycle, `TICKS_PER_FRAME = 69,905`
- Input: `ButtonPressEvent`/`ButtonReleaseEvent` via EventBus

**Fallback/upgrade path: SameBoy** (via JNI/FFM if accuracy insufficient)
- C11, MIT license, gold standard accuracy (99.9%+ on ~2,800 games)
- Native `GB_set_execution_callback` — per-instruction opcode delivery (zero overhead)
- Native `GB_set_write_memory_callback` / `GB_set_read_memory_callback`
- `GB_get_direct_access` — raw pointers to ROM, WRAM, VRAM, SRAM, OAM, IO, HRAM
- Requires JNI/FFM bridge (~15-20 functions) + platform-specific native libs
- Reserve this path for Phase 09 IDE embedding if Coffee-GB proves insufficient

**`ld d,d` trap (EMU_printf) implementation with Coffee-GB:**
- GBDK `EMU_printf` emits opcode 0x52 (`ld d,d`) + magic signature `0x6464` + message payload
- Coffee-GB approach: poll `getCpu().getCurrentOpcode()` after each `tick()`, or fork CPU class to add callback
- AddressSpace decorator pattern (proven by `Genie` class) enables memory write hooks

**Architecture:**
```
gbkt-emulator/                    # New module
  GbEmulator.kt                   # Interface (swappable backend)
  CoffeeGbEmulator.kt             # Coffee-GB implementation
  DebugMessageParser.kt           # ld d,d trap → structured log events
  EmulatorSession.kt              # Run loop, input handling, frame capture

gbkt-gradle-plugin/
  RunEmulatorTask.kt              # Updated: use embedded emulator, capture logs
                                  # Log output: build/gbkt/logs/debug.log
                                  # Framebuffer: render to Swing window (160x144)
```

**Success Criteria** (what must be TRUE):
  1. `./gradlew runEmulator` launches the embedded Coffee-GB emulator (no external mGBA needed)
  2. A 160x144 Swing window renders the game framebuffer with keyboard input mapped to GB buttons
  3. `EMU_printf` calls in the ROM produce structured log entries in `build/gbkt/logs/debug.log`
  4. Debug log is written in real-time (tail -f works during gameplay)
  5. `GbEmulator` interface abstracts the backend so SameBoy can be swapped in later
  6. All existing example ROMs (Pong, Breakout, Explorer, Platformer) boot and run in the embedded emulator
  7. Optional `externalEmulator` config allows running an external emulator (e.g., mGBA) instead of embedded Coffee-GB
**Plans**: 11 plans

Plans:
- [x] 06.12-01-PLAN.md — Module scaffold + GbEmulator interface + EmulatorConfig + DebugLogEntry
- [x] 06.12-02-PLAN.md — CoffeeGbEmulator headless core (Coffee-GB lifecycle, threading)
- [x] 06.12-03-PLAN.md — EmuPrintfInterceptor (ld d,d trap detection + format string parsing)
- [x] 06.12-04-PLAN.md — SourceMapResolver + DebugLogWriter (source map resolution + log file)
- [x] 06.12-05-PLAN.md — GbDisplayPanel + InputHandler (display rendering + keyboard mapping)
- [x] 06.12-06-PLAN.md — EmulatorWindow + EmulatorToolbar (main JFrame + developer controls)
- [x] 06.12-07-PLAN.md — LogCatPanel (terminal-style debug log viewer)
- [x] 06.12-08-PLAN.md — MemoryInspectorPanel (named variables + hex view tabs)
- [x] 06.12-09-PLAN.md — EmulatorSession orchestrator (wires all components together)
- [x] 06.12-10-PLAN.md — Gradle task updates (RunEmulatorTask, EmulatorTestTask, `run` task)
- [x] 06.12-11-PLAN.md — Integration validation + BOM/all + ROADMAP update

### Phase 07: UAT Gameplay Validation
**Goal**: All example ROMs (including new platformer) are manually played and debugged using the embedded emulator to verify actual gameplay works — not just boot; debugging workflow uses EMU_printf log capture and source maps. LotD (06.11) runs in parallel.
**Depends on**: Phase 06.12 (embedded emulator provides debug loop for UAT)
**Requirements**: UAT-01, UAT-02
**Success Criteria** (what must be TRUE):
  1. Pong ROM plays correctly in mGBA: ball bounces, paddles move, score increments, game over/win transitions work
  2. Breakout ROM plays correctly in mGBA: bricks break, ball physics work, scene transitions function
  3. Explorer ROM plays correctly in mGBA: dungeon navigation works, encounters trigger, menus render, save/load functions
  4. Platformer ROM plays correctly in mGBA: gravity, jumping, platform landing all work
  5. A debugging workflow document exists showing how to use source maps + mGBA tools to diagnose issues
**Plans**: 9 plans

Plans:
- [x] 07-01-PLAN.md — InputScript DSL and ScreenshotCapture (agent toolkit primitives) [wave 1]
- [x] 07-02-PLAN.md — VariableInspector, SavestateManager, VisualDiff (agent toolkit primitives) [wave 1]
- [x] 07-03-PLAN.md — AgentDebugSession orchestrator (unified agent API) [wave 2]
- [x] 07-04-PLAN.md — Gradle tasks for agent debugging (CLI-callable interface) [wave 2]
- [x] 07-05-PLAN.md — UAT: Pong and Breakout (simple games, checkpoint) [wave 3]
- [x] 07-06-PLAN.md — UAT: Platformer, Shmup, Racer (action games, checkpoint) [wave 3]
- [x] 07-07-PLAN.md — UAT: Explorer, Dungeon, RPG-Lite (RPG games, checkpoint) [wave 3]
- [x] 07-08-PLAN.md — UAT: Platformer-GBC (GBC variant, checkpoint) [wave 3]
- [x] 07-09-PLAN.md — UAT Guide documentation + CLAUDE.md cross-references [wave 4]

### Phase 07.1: test-dx-and-agent-tooling (INSERTED)

**Goal:** Eliminate test boilerplate with JUnit5 GbktTestExtension, enrich game_metadata.json with controls/transitions/variable semantics, expand MCP server to 16 tools, create PLAYBOOK.md game descriptions for LLM agents. Tests feel like describing gameplay in Kotlin, not wiring infrastructure.
**Requirements**: TDX-01, TDX-02, TDX-03, TDX-04, META-01, META-02, MCP-01, PLAY-01, TDOC-01
**Depends on:** Phase 07
**Success Criteria** (what must be TRUE):
  1. GbktTestExtension provides @RegisterExtension lifecycle with auto-discovery, auto-skip, auto-screenshot on failure
  2. All 9 existing StepAgentTests migrated to GbktTestExtension with zero manual boilerplate
  3. game_metadata.json contains controls, transitions, and variable semantic categories
  4. GameConstants.kt includes Controls and Transitions objects
  5. MCP server exposes 16 tools (11 existing + 5 new: save/load state, batch assert, playbook, game discovery)
  6. All 9 example games have PLAYBOOK.md files with gameplay descriptions for LLM agents
  7. context/TESTING.md documents all test tiers, APIs, recipes, and MCP tools
**Plans:** 6/6 plans complete

Plans:
- [ ] 07.1-01-PLAN.md — Metadata enrichment (controls, transitions, variable semantics) + GameConstants Controls/Transitions [wave 1]
- [ ] 07.1-02-PLAN.md — New gbkt-test module with GbktTestExtension, GameDiscovery, fluent assertions [wave 1]
- [ ] 07.1-03-PLAN.md — Test recipes + migrate all 9 StepAgentTests to new extension [wave 2]
- [ ] 07.1-04-PLAN.md — MCP server expansion (5 new tools: save/load state, batch assert, playbook, game discovery) [wave 2]
- [ ] 07.1-05-PLAN.md — Playbook format + GeneratePlaybookTask + 9 game PLAYBOOK.md files [wave 2]
- [ ] 07.1-06-PLAN.md — Documentation (context/TESTING.md + gbkt-test/CLAUDE.md + CLAUDE.md updates) [wave 3]


### Phase 07.1.1: agent-testing-critical-gaps (INSERTED)

**Goal:** Close critical gaps blocking autonomous LLM game testing and write the UAT guide: (1) Add `emulator_press` MCP tool to halve tool call count for button inputs, (2) Wire custom `TileDecoder` through StepAgent/UatRunner/MCP so text assertions work with custom tilesets (Explorer, Dungeon, RPG-lite), (3) Add 16-bit variable support through Observation and MCP layer so multi-byte scores/counters read correctly, (4) Opt-in scroll-aware text reading in `VramTextVerifier` — apply SCX/SCY offsets when a flag is set, keeping default behavior safe for non-scrolling games, (5) Create `context/UAT_GUIDE.md` with debugging workflow documentation and real examples (absorbed from Phase 07 Plan 09).
**Requirements**: UAT-01, UAT-02
**Depends on:** Phase 07.1 (test DX and agent tooling must be in place)
**Plans:** 4/4 plans complete

Plans:
- [ ] 07.1.1-01-PLAN.md — Type-aware variable reading + GameMetadata tileDecoderConfig [wave 1]
- [ ] 07.1.1-02-PLAN.md — emulator_press MCP tool (17th tool) [wave 1]
- [ ] 07.1.1-03-PLAN.md — TileDecoder wiring through StepAgent + scroll-aware VramTextVerifier [wave 2]
- [ ] 07.1.1-04-PLAN.md — context/UAT_GUIDE.md debugging workflow documentation [wave 3]

### Phase 07.1.2: Hardening Bug Fixes (INSERTED)

**Goal:** Fix 5 functional codegen bugs found during hardening that produce broken behavior in generated C output: (1) F-033 tournament standings bubble sort only swaps wins, not losses array, (2) F-034 puzzle match detection does not clear matched cells so game loop can't progress, (3) F-035 puzzle gravity DOWN only performs single-pass swap per call so pieces don't fall fully, (4) F-075 BankAllocator overflow fallback assigns to maxBanks even when full — silently exceeds bank size, (5) F-077 checkPalettePrecision checks after quantization instead of before — fundamentally wrong.
**Requirements**: F-033, F-034, F-035, F-075, F-077
**Depends on:** Phase 07.1.1
**Plans:** 3/3 plans complete

Plans:
- [x] 07.1.2-01-PLAN.md — Fix F-075 BankAllocator overflow + F-077 checkPalettePrecision + audit 11 analysis passes [wave 1]
- [x] 07.1.2-02-PLAN.md — Fix F-033 sport tournament + F-034 puzzle match + F-035 puzzle gravity + audit 4 genre modules [wave 2]
- [ ] 07.1.2-03-PLAN.md — Build + boot verification for all 9 example games (generateC → buildRom → emulator boot) [wave 3]



### Phase 07.2: interactive-game-uat (INSERTED)

**Goal:** Interactively play-test all 10 game targets (9 games + 1 GBC variant) via MCP emulator tools. Claude drives the emulator; user confirms visual correctness at checkpoints. Capture golden screenshots, produce per-game UAT sign-off reports, fix bugs inline.
**Requirements**: UAT72-PONG, UAT72-BREAKOUT, UAT72-RACER, UAT72-SHMUP, UAT72-PLATFORMER, UAT72-PLATFORMER-GBC, UAT72-EXPLORER, UAT72-DUNGEON, UAT72-RPGLITE, UAT72-SIGNOFF
**Depends on:** Phase 07.1.2 (hardening bug fixes must land before play-testing)
**Plans:** 5 plans

Plans:
- [ ] 07.2-01-PLAN.md — UAT: Pong + Breakout (simplest games, establish pattern) [wave 1]
- [ ] 07.2-02-PLAN.md — UAT: Racer + Shmup (action games, first GBC) [wave 2]
- [ ] 07.2-03-PLAN.md — UAT: Platformer + Platformer-GBC (physics, GBC variant) [wave 3]
- [ ] 07.2-04-PLAN.md — UAT: Explorer + Dungeon (RPG exploration) [wave 4]
- [ ] 07.2-05-PLAN.md — UAT: RPG-Lite + Phase Sign-Off (most complex + final report) [wave 5]

### Phase 08: Detekt and Tech Debt Cleanup
**Goal**: All remaining Detekt violations resolved after Phase 06.9 infrastructure cleanup; code quality baseline is clean
**Depends on**: Phase 07 (cleanup after UAT confirms correct behavior)
**Requirements**: QUAL-01, QUAL-02
**Success Criteria** (what must be TRUE):
  1. `./gradlew detekt` passes with zero violations across all modules
  2. VRAMLayoutPass `buildTileOverflowError` parameter count reduced below threshold (6)
  3. ScriptOpInterpreter `executeMathOp` cyclomatic complexity reduced below threshold (15)
  4. File naming violations (DslMarkers.kt, Errors.kt) resolved
  5. No `@Suppress` annotations added as workarounds — actual fixes only
**Plans**: 16 plans

### Phase 09: IDE & Tooling (NEW)
**Goal**: Comprehensive IntelliJ plugin enhancements and developer tooling. Live DSL feedback, source map viewer for DSL↔C navigation, localization string editor, tilemap layout preview. These are DX improvements that make gbkt pleasant to use as a daily development tool.
**Depends on**: Phase 08 (clean codebase before tooling polish)
**Success Criteria** (what must be TRUE):
  1. IntelliJ plugin provides live DSL feedback: compile-time type hints, error highlighting, and quick-fixes in the editor
  2. IntelliJ source map viewer shows side-by-side Kotlin DSL ↔ generated C mapping using `.gbkt.map` files
  3. IntelliJ localization string editor (Android Studio-style) for editing `.po` files with context preview
  4. Live tilemap preview (IDE-04): visual preview of scene tilemap layout in the IDE
  5. All IntelliJ plugin features have integration tests
  6. Plugin published to JetBrains Marketplace (or installable from local build)
  7. IntelliJ plugin includes "Setup Claude Code" action that installs skills and MCP server config
  8. IntelliJ plugin auto-detects Claude Code on project open and offers setup
  9. IntelliJ plugin shows update notification when Claude Code skills are outdated
**Plans**: 16 plans

**Deferred Item Traceability:**
- I1: IntelliJ live DSL feedback (origin: Phase 05.05.2 — type system designed to enable it; deferred repeatedly through 05.05.3)
- I2: IntelliJ DSL↔C side-by-side viewer (origin: Phase 05 — reads `.gbkt.map`; source maps now exist)
- I3: IntelliJ localization string editor (origin: Phase 06.1 — belongs in dedicated i18n/IntelliJ phase)
- I4: Live tilemap preview IDE-04 (origin: Phase 06 — pending in REQUIREMENTS.md, not directed)
- I5: IntelliJ Claude Code integration wizard (origin: Phase 07.1 — skills and MCP server created via `gbktSetupClaude`)
- I6: Claude Code skills auto-update via IntelliJ (origin: Phase 07.1 — version marker mechanism exists in `.claude/.gbkt-version`)

Plans:
- [ ] TBD (run /gsd:plan-phase 09 to break down)

## Progress

**Execution Order:**
Phases execute in order: 1 → 2 → 3 → 3.1 → 4 → 5 → 5.05 → 05.05.1 → 05.05.2 → 05.05.3 → 06 → 06.1 → 06.2 → 06.3 → 06.4 → 06.5 → 06.6 → 06.7 → 06.8 → 06.9 → 06.10 → 06.11 → 06.12 → 07 → 08 → 09
Note: Phase 06.11 (LotD port) runs parallel with Phase 07 (UAT).

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. IR Foundation and DSL | 4/4 | Complete    | 2026-02-17 |
| 2. Structured Codegen and Migration Cut | 4/4 | Complete    | 2026-02-18 |
| 3. Asset Pipeline and JVM Test Runner | 2/4 | Complete    | 2026-02-18 |
| 3.1. Collection Abstractions (INSERTED) | 3/3 | Complete    | 2026-02-18 |
| 4. Analysis Pass Pipeline | 9/9 | Complete    | 2026-02-18 |
| 5. Integration and End-to-End Validation | 4/4 | Complete   | 2026-02-19 |
| 5.05. V2 Source Maps (INSERTED) | 3/3 | Complete   | 2026-02-19 |
| 05.05.1. V2 Codegen Runtime (INSERTED) | 7/7 | Complete   | 2026-02-20 |
| 05.05.2. V2 DSL Ergonomics (INSERTED) | 2/2 | Complete    | 2026-02-20 |
| 05.05.3. V2 DSL Ergonomics Completion (INSERTED) | 4/4 | Complete    | 2026-02-21 |
| 06. Complete Gap Closure | 11/11 | Complete    | 2026-02-21 |
| 06.1. V1 Parity — Foundations (INSERTED) | 8/8 | Complete    | 2026-02-21 |
| 06.2. V1 Parity — UI Layer (INSERTED) | 5/5 | Complete    | 2026-02-22 |
| 06.3. V1 Parity — World System (INSERTED) | 5/5 | Complete    | 2026-02-22 |
| 06.4. V1 Parity — Combat & Inventory (INSERTED) | 4/4 | Complete    | 2026-02-23 |
| 06.5. V1 Parity — RPG Package (INSERTED) | 12/12 | Complete    | 2026-02-24 |
| 06.6. Deferred Gaps — DSL/GBC/Audio (INSERTED) | 3/5 | Complete    | 2026-02-24 |
| 06.7. Deferred Gaps — Entity/Movement/World (INSERTED) | 10/10 | Complete    | 2026-02-25 |
| 06.8. Deferred Gaps — Genre/RPG Extensions (INSERTED) | 13/13 | Complete    | 2026-02-25 |
| 06.9. Deferred Gaps — Infrastructure (INSERTED) | 8/8 | Complete    | 2026-02-26 |
| 06.10. V1 Parity — Example Games (INSERTED) | 16/16 | Complete    | 2026-02-26 |
| 06.11. LotD Port (INSERTED) | 21/21 | Complete   | 2026-02-27 |
| 06.12. Embedded Emulator (INSERTED) | 11/11 | Complete   | 2026-02-28 |
| 07. UAT Gameplay Validation | 8/9 | In Progress|  |
| 07.1. Test DX & Agent Tooling (INSERTED) | 6/6 | Complete    | 2026-03-18 |
| 07.1.1. Agent Testing Gaps (INSERTED) | 4/4 | Complete    | 2026-03-20 |
| 07.1.2. Hardening Bug Fixes (INSERTED) | 2/3 | Complete    | 2026-03-22 |
| 07.2. Interactive Game UAT (INSERTED) | 0/5 | Not started | - |
| 08. Detekt and Tech Debt Cleanup | 0/? | Not started | - |
| 09. IDE & Tooling (NEW) | 0/? | Not started | - |
