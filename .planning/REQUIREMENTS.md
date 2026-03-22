# Requirements: gbkt Compiler Pipeline Rebuild

**Defined:** 2026-02-17
**Core Value:** The framework automatically manages Game Boy hardware resources (VRAM, banking, OAM, RAM) so the developer writes only declarative Kotlin DSL.

## v1 Requirements

Requirements for the rebuild milestone. Each maps to roadmap phases.

### IR Foundation

- [x] **IR-01**: Sealed IR hierarchy represents all game-domain concepts (scenes, actors, sprites, tilemaps, scripts, systems, state)
- [x] **IR-02**: Platform annotations are nullable fields (bank slot, VRAM range, OAM slot) — null until analysis fills them
- [x] **IR-03**: ScriptOp sealed instruction set covers movement, dialog, branching, state mutation, battle triggers, math
- [x] **IR-04**: IR module compiles independently with zero external dependencies

### DSL

- [x] **DSL-01**: Kotlin DSL builders produce valid IR for all game constructs
- [x] **DSL-02**: `ref()` provides typed, compile-time-validated references
- [x] **DSL-03**: `asset()` references raw files for pipeline processing
- [x] **DSL-04**: Pong, Breakout, and Explorer example games defined in new DSL from Phase 1

### Structured Codegen

- [x] **CGEN-01**: C AST sealed hierarchy (CFunction, CStatement, CExpr, CType) lives in codegen module
- [x] **CGEN-02**: Bank assignment is a typed field on C AST nodes — no mutable `currentBank` state
- [x] **CGEN-03**: Pretty-printer is the single place C strings are assembled
- [x] **CGEN-04**: Old string-based `GBDKCodeGenerator` fully replaced and deleted
- [x] **CGEN-05**: Domain visitors generate C AST per IR domain (scenes, actors, systems)

### Analysis Passes

- [x] **ANLZ-01**: Validation pass (ref resolution, type checks, DSL constraint enforcement)
- [x] **ANLZ-02**: Bank allocation pass (bin-packing, scene locality, trampoline generation)
- [x] **ANLZ-03**: VRAM planning pass (per-scene tile slots, shared tile detection)
- [x] **ANLZ-04**: OAM planning pass (sprite slot allocation, scanline density analysis)
- [x] **ANLZ-05**: RAM planning pass (WRAM layout, HRAM allocation, SRAM structure)
- [x] **ANLZ-06**: Budget audit pass (human-readable build report, hard fail on overflow)

### Asset Pipeline

- [x] **ASSET-01**: PNG → 2bpp tile data with deduplication and palette mapping
- [x] **ASSET-02**: TMX/LDtk → tilemap IR with tile indices and collision layers
- [x] **ASSET-03**: Sprite sheet slicing into frames with animation metadata
- [x] **ASSET-04**: Integrated into Gradle as a build task

### Testing

- [x] **TEST-01**: JVM test runner interprets ScriptOp without emulator
- [x] **TEST-02**: SimulationContext API for scene loading, input simulation, state inspection
- [x] **TEST-03**: Game logic tests run in under 5 seconds

### Integration

- [x] **INTG-01**: Pong example compiles to working .gb ROM through new pipeline
- [x] **INTG-02**: Breakout example compiles to working .gb ROM through new pipeline
- [x] **INTG-03**: Explorer example compiles to working .gb ROM through new pipeline
- [x] **INTG-04**: Gradle plugin orchestrates full build pipeline (assets → DSL → analysis → codegen → lcc)

### Cleanup

- [x] **CLEAN-01**: All v1 IR and DSL files deleted; no dead code remains in gbkt-core
- [x] **CLEAN-02**: `v2/` subdirectories promoted — files moved to parent `ir/` and `dsl/` packages; all imports updated
- [x] **CLEAN-03**: Deprecated `GBDKCodeGenerator` fully deleted (not just deprecated)

### BOM and Composability

- [x] **BOM-01**: `ScriptOp` and `Expr` are non-sealed (open interface or visitor pattern) — external modules can add IR node types
- [x] **BOM-02**: `gbkt-rpg` is a separate Gradle module with its own IR nodes that compiles independently against `gbkt-core`
- [x] **BOM-03**: `gbkt-core` contains only base IR, DSL, and analysis — no domain-specific (RPG, exploration, combat) IR nodes
- [x] **BOM-04**: `gbkt-bom` Gradle module publishes aligned versions; consumers use `platform(gbkt-bom)`

### Source Maps

- [x] **SMAP-01**: V2 pipeline generates source map (.map file) mapping generated C lines to Kotlin DSL source file:line
- [x] **SMAP-02**: lcc compilation errors display Kotlin DSL file:line instead of generated C file:line

### UAT Validation

- [x] **UAT-01**: All three example ROMs (Pong, Breakout, Explorer) are manually verified to play correctly in mGBA — not just boot
- [x] **UAT-02**: Debugging workflow documented: source maps + mGBA debugging tools enable efficient issue diagnosis

### Code Quality

- [ ] **QUAL-01**: All Detekt violations from Phases 3.1 and 4 resolved (VRAMLayoutPass params, cyclomatic complexity, naming)
- [ ] **QUAL-02**: No pre-existing Detekt warnings in modified files — clean baseline for BOM publishing

### Collision

- [x] **COLL-01**: Tile-specific collision attributes supported — per-tile walkability from tilemap data, not just entity-based obstacles
- [x] **COLL-02**: Collision data integrates with exploration system gauge/step callbacks

### IntelliJ Plugin DX

- [x] **IDE-01**: Source map viewer — side-by-side Kotlin DSL ↔ generated C mapping in the editor
- [x] **IDE-02**: Red underline on `ref()` targeting nonexistent assets
- [x] **IDE-03**: Inline budget report gutter icons
- [ ] **IDE-04**: Live preview of scene tilemap layout

### DSL Ergonomics

- [x] **ERGO-01**: Variable delegates (`u8Var`, `i8Var`, `u8Array`) support `set`, `+=`, `-=`, `*=`, `/=`, `%=`, comparison operators directly
- [x] **ERGO-02**: Actor property references work as `ball.y` / `paddle.x` instead of `varRef("ball.y")` — typed ActorPropertyRef with operator extensions
- [x] **ERGO-03**: Integer literals auto-wrap in all expression positions — no manual `literal(N)` calls needed in game code
- [x] **ERGO-04**: All three example games (Pong, Breakout, Explorer) rewritten with Kotlin-idiomatic syntax producing functionally equivalent C output
- [x] **ERGO-05**: Assembly-style API (`assign()`, `varRef()`, `literal()`, `arrayAssign()`, `arrayRef()`) deprecated from public DSL surface

### Test DX and Agent Tooling

- [x] **TDX-01**: GbktTestExtension JUnit5 extension with convention-based ROM discovery, auto-lifecycle, and auto-skip when ROM missing
- [x] **TDX-02**: Fluent assertion DSL (assertScene, assertVariable, assertActorVisible, assertTextOnScreen) and reusable test recipes
- [x] **TDX-03**: Auto-screenshot and variable dump on test failure saved to build/gbkt/test-failures/
- [x] **TDX-04**: All 9 existing StepAgentTests migrated to GbktTestExtension with zero manual boilerplate
- [x] **META-01**: game_metadata.json enriched with controls (per-scene input mappings), transitions (scene graph), and variable semantics (auto-detected categories)
- [x] **META-02**: GameConstants.kt generation includes Controls and Transitions objects alongside Scenes/Actors/Variables/Texts
- [x] **MCP-01**: MCP server expanded to 16 tools (save/load state, batch assert, playbook, game discovery, game-name start)
- [x] **PLAY-01**: PLAYBOOK.md natural-language game descriptions for LLM agents, with auto-scaffold Gradle task
- [x] **TDOC-01**: context/TESTING.md comprehensive testing guide covering all test tiers, APIs, recipes, and MCP tools

## v2 Requirements

Deferred to future milestone. Tracked but not in current roadmap.

### Multiplatform

- **MULTI-01**: GBA backend (codegen-gba module) produces libtonc/libgba C
- **MULTI-02**: Backend selection via `gbkt { target("gba") }` in build.gradle.kts

### Advanced Features

- **ADV-01**: Link cable multiplayer protocol library
- **ADV-02**: Cutscene engine with scripted camera
- **ADV-03**: Music conversion (hUGEtracker .uge → driver format)

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Visual game editor (GB Studio-style) | gbkt is code-first, not visual-first |
| Runtime interpreted scripting | All logic compiles to C; no interpreter on GB hardware |
| Floats in ScriptOp | GB has no FPU; silently wrong results |
| Heap allocation in game logic | GB has no heap; use fixed-size structures |
| Manual banking DSL syntax | Defeats the core value proposition |
| New game ports (beyond examples) | Focus on framework correctness, not game content |
| Community docs / tutorials | Premature until architecture stabilizes |
| Backward compatibility with current API | Breaking changes are acceptable during rebuild |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| IR-01 | Phase 1 | Complete |
| IR-02 | Phase 1 | Complete |
| IR-03 | Phase 1 | Complete |
| IR-04 | Phase 1 | Complete |
| DSL-01 | Phase 1 | Complete |
| DSL-02 | Phase 1 | Complete |
| DSL-03 | Phase 1 | Complete |
| DSL-04 | Phase 1 | Complete |
| CGEN-01 | Phase 2 | Complete |
| CGEN-02 | Phase 2 | Complete |
| CGEN-03 | Phase 2 | Complete |
| CGEN-04 | Phase 2 | Complete |
| CGEN-05 | Phase 2 | Complete |
| ANLZ-01 | Phase 4 | Complete |
| ANLZ-02 | Phase 4 | Complete |
| ANLZ-03 | Phase 4 | Complete |
| ANLZ-04 | Phase 4 | Complete |
| ANLZ-05 | Phase 4 | Complete |
| ANLZ-06 | Phase 4 | Complete |
| ASSET-01 | Phase 3 | Complete |
| ASSET-02 | Phase 3 | Complete |
| ASSET-03 | Phase 3 | Complete |
| ASSET-04 | Phase 3 | Complete |
| TEST-01 | Phase 3 | Complete |
| TEST-02 | Phase 3 | Complete |
| TEST-03 | Phase 3 | Complete |
| INTG-01 | Phase 5 | Complete |
| INTG-02 | Phase 5 | Complete |
| INTG-03 | Phase 5 | Complete |
| INTG-04 | Phase 5 | Complete |
| SMAP-01 | Phase 5.05 | Complete |
| SMAP-02 | Phase 5.05 | Complete |
| UAT-01 | Phase 5.06 | Complete |
| UAT-02 | Phase 5.06 | Complete |
| QUAL-01 | Phase 5.15 | Pending |
| QUAL-02 | Phase 5.15 | Pending |
| COLL-01 | Phase 5.3 | Complete |
| COLL-02 | Phase 5.3 | Complete |
| IDE-01 | Phase 5.4 | Complete |
| IDE-02 | Phase 5.4 | Complete |
| IDE-03 | Phase 5.4 | Complete |
| IDE-04 | Phase 5.4 | Pending |
| ERGO-01 | Phase 05.05.2 | Complete |
| ERGO-02 | Phase 05.05.2 | Complete |
| ERGO-03 | Phase 05.05.2 | Complete |
| ERGO-04 | Phase 05.05.2 | Complete |
| ERGO-05 | Phase 05.05.2 | Complete |
| TDX-01 | Phase 07.1 | Complete |
| TDX-02 | Phase 07.1 | Complete |
| TDX-03 | Phase 07.1 | Complete |
| TDX-04 | Phase 07.1 | Complete |
| META-01 | Phase 07.1 | Complete |
| META-02 | Phase 07.1 | Complete |
| MCP-01 | Phase 07.1 | Complete |
| PLAY-01 | Phase 07.1 | Complete |
| TDOC-01 | Phase 07.1 | Complete |

**Coverage:**
- v1 requirements: 60 total
- Mapped to phases: 60
- Unmapped: 0

---
*Requirements defined: 2026-02-17*
*Last updated: 2026-03-18 after Phase 07.1 planning*
