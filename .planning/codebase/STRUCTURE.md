# Codebase Structure

**Analysis Date:** 2026-05-27

## Directory Layout

```
gbkt/
├── gbkt-ir/                  # IR types (zero deps, leaf module)
├── gbkt-lang/                # DSL builders, property delegates
├── gbkt-engine/              # Engine runtime types (combat, entity, scene, graphics, input, inventory, pickup)
├── gbkt-world/               # World & exploration types (zones, floors, dungeon crawling)
├── gbkt-core/                # Aggregator — re-exports IR+lang+engine+world + asset pipeline, constraints, test infra
├── gbkt-backend-api/         # Backend contract (CodegenBackend, BackendRegistry, GenreSystemVisitor)
├── gbkt-backend-gbdk/        # GB/GBC backend — visitors, C AST, pipeline, post-process, profiles
├── gbkt-analysis/            # 11 analysis passes (validation, optimization, resource planning)
├── gbkt-genre-rpg/           # RPG genre plugin (characters, battles, abilities, equipment)
├── gbkt-genre-platformer/    # Platformer genre plugin (physics, camera, level elements)
├── gbkt-genre-puzzle/        # Puzzle genre plugin (match-3, block-push)
├── gbkt-genre-sport/         # Sport genre plugin (racing, ball sports, tournaments)
├── gbkt-emulator/            # Embedded Coffee-GB emulator, agent API, debug log capture
├── gbkt-test/                # Test infrastructure (GbktTestExtension, assertions, recipes)
├── gbkt-mcp-server/          # MCP server wrapping StepAgent for AI agents
├── gbkt-gradle-plugin/       # Build integration (composite include)
├── gbkt-cli/                 # Project scaffolding CLI
├── gbkt-intellij-plugin/     # IDE support (highlighting, completion, editors, C preview)
├── gbkt-all/                 # Convenience meta-module — aggregates all published modules
├── gbkt-bom/                 # Version coordinator (Bill of Materials)
├── gbkt-examples/            # Example games
│   ├── pong/
│   ├── breakout/
│   ├── racer/
│   ├── banks/
│   ├── metasprites/
│   ├── metasprites-stress/
│   ├── simple-physics/
│   └── platformer-template/
├── LabyrinthOfTheDragon-port/  # Reference RPG implementation
├── LabyrinthOfTheDragon/       # Original C-based reference (pre-Kotlin)
├── .planning/                  # GSD planning artifacts
│   ├── phases/                 # Phase folders (NN-name/ with PLAN, SPEC, STATE, VERIFICATION, evidence/)
│   ├── codebase/               # Codebase maps (ARCHITECTURE.md, STRUCTURE.md, this file, etc.)
│   ├── research/               # Research notes
│   ├── seeds/                  # Bug/feature seeds (SEED-NNN-*.md)
│   ├── todos/                  # TODO snapshots
│   ├── debug/                  # Debug session notes
│   ├── quick/                  # Quick-fix records
│   ├── PROJECT.md              # Project vision & complexity ceiling
│   ├── REQUIREMENTS.md         # High-level requirements
│   ├── ROADMAP.md              # Phase roadmap
│   ├── STATE.md                # Current planning state / resume signal
│   └── verifier-gates.md       # Verification gates
├── context/                    # Long-form developer documentation
├── docker/                     # Docker assets (GBDK toolchain image)
├── scripts/                    # Utility scripts (Python, Lua)
├── buildSrc/                   # Gradle build conventions
├── sessions/                   # Saved emulator sessions
├── scratch/                    # Ephemeral work area
├── build.gradle.kts            # Root build script
├── settings.gradle.kts         # Module include list
├── detekt.yml                  # Detekt lint config
├── gradle.properties           # Gradle config
├── CLAUDE.md                   # Project-wide Claude Code instructions
├── CONTRIBUTING.md             # Contributor guide
└── README.md
```

## Directory Purposes

**`gbkt-ir/`:**
- Purpose: Defines every IR node type and visitor interface.
- Contains: 11 expression nodes, 52 ScriptOp nodes, 8 SystemIR nodes, GameIR root, JSON serializer.
- Key files: `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/{Expr,ScriptOp,SystemIR,GameIR,SceneIR,ActorIR}.kt`, `{ExprVisitorI,ScriptOpVisitorI,SystemIRVisitorI}.kt`

**`gbkt-lang/`:**
- Purpose: DSL builders + property delegates + operator overloads.
- Contains: Receivers that record IR (`GameBuilder`, `ScriptBuilder`, `ActorBuilder`, `SceneBuilder`), variable delegates, system builders.
- Key files: `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/{GameBuilder,ScriptBuilder,ActorBuilder,SceneBuilder,VariableBuilders}.kt`

**`gbkt-engine/`:**
- Purpose: Engine runtime types — types referenced by the engine at runtime but separated from DSL.
- Contains: `combat/`, `entity/`, `graphics/`, `input/`, `inventory/`, `pickup/`, `scene/`.
- Key files: `gbkt-engine/src/main/kotlin/io/github/gbkt/core/scene/SceneTypes.kt`, `entity/EntityTypes.kt`

**`gbkt-world/`:**
- Purpose: World and exploration types.
- Contains: `world/`, `exploration/`.
- Key files: `gbkt-world/src/main/kotlin/io/github/gbkt/core/{world,exploration}/`

**`gbkt-core/`:**
- Purpose: Aggregator — re-exports lower modules plus asset/constraints/test/parsers.
- Contains: `assets/`, `builder/`, `collision/`, `combat/`, `constraints/`, `dsl/`, `entity/`, `exploration/`, `flow/`, `graphics/`, `input/`, `inventory/`, `ir/`, `movement/`, `optimization/`, `rpg/`, `scene/`, `services/`, `test/`, `ui/`, `validation/`, `world/`, plus parsers (`LdtkParser.kt`, `TiledParser.kt`, `PoParser.kt`, `PngValidator.kt`, `TileDeduplicator.kt`).
- Key files: `gbkt-core/src/main/kotlin/io/github/gbkt/core/{AssetPipeline,GameIR,SourceMap,FileIO}.kt`

**`gbkt-backend-api/`:**
- Purpose: Backend contract.
- Contains: `CodegenBackend.kt`, `BackendRegistry.kt`, `GenerationResult.kt`, `ValidationResult.kt`, `GenreSystemVisitor.kt`, `CollectionCodegen.kt`.
- Key files: `gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/CodegenBackend.kt`

**`gbkt-backend-gbdk/`:**
- Purpose: GB/GBC codegen implementation.
- Contains: `GBDKBackend.kt`, `codegen/{visitor,ast,pipeline,postprocess,emit}/`, `profiles/`.
- Key files: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/{GBDKBackend.kt,codegen/pipeline/GBDKPipelineV2.kt}`

**`gbkt-analysis/`:**
- Purpose: Static analysis passes.
- Contains: `AnalysisPass.kt`, `PassContext.kt`, `PassResult` (inside AnalysisPass.kt), `PassPipeline.kt`, `DefaultPipeline.kt`, 11 passes in `passes/`, `config/AnalysisConfig.kt`.
- Key files: `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/{AnalysisPass,PassPipeline,DefaultPipeline}.kt`

**`gbkt-genre-rpg/`:**
- Purpose: RPG genre plugin.
- Contains: `dsl/`, `domain/`, `codegen/`.
- Key files: `gbkt-genre-rpg/src/main/kotlin/io/github/gbkt/rpg/dsl/{CharacterBuilder,AbilityBuilder,EquipmentBuilder,ClassBuilder}.kt`

**`gbkt-genre-platformer/`, `gbkt-genre-puzzle/`, `gbkt-genre-sport/`:**
- Purpose: Genre-specific DSL + IR extensions + codegen.
- Pattern: Each has `dsl/`, `domain/`, `codegen/` subdirectories mirroring the RPG genre layout.

**`gbkt-emulator/`:**
- Purpose: Embedded Coffee-GB emulator + agent API.
- Contains: `CoffeeGbEmulator.kt`, `GbEmulator.kt`, `EmulatorSession.kt`, `agent/` (StepAgent, UatRunner, VisualDiff, SavestateManager, OamSpriteReader, etc.), `debug/` (DebugLogWriter, EmuPrintfInterceptor, SourceMapResolver), `ui/`.
- Key files: `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/{CoffeeGbEmulator,EmulatorSession}.kt`, `agent/StepAgent.kt`, `agent/UatRunner.kt`

**`gbkt-test/`:**
- Purpose: JUnit5 integration for ROM tests.
- Contains: `GbktTestExtension.kt`, `GbktGameAssertions.kt`, `GbktGameReporter.kt`, `GbktTestRecipes.kt`, `GameDiscovery.kt`.
- Key files: `gbkt-test/src/main/kotlin/io/github/gbkt/test/GbktTestExtension.kt`

**`gbkt-mcp-server/`:**
- Purpose: MCP stdio server wrapping StepAgent for Claude Code.
- Contains: `GbktMcpServer.kt`, `ToolHandlers.kt`, `McpEmulatorSession.kt`, `ObservationSerializer.kt`.
- Key files: `gbkt-mcp-server/src/main/kotlin/io/github/gbkt/mcp/GbktMcpServer.kt`

**`gbkt-gradle-plugin/`:**
- Purpose: Gradle integration (composite-build include).
- Contains: `GbktPlugin.kt`, `GbktExtension.kt`, `tasks/` (23 task classes), `internal/`.
- Key files: `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/{GbktPlugin,GbktExtension}.kt`, `tasks/{GenerateCTask,CompileRomTask}.kt`

**`gbkt-cli/`:**
- Purpose: Command-line project scaffolding.
- Contains: `Main.kt`, `Commands.kt`, `templates/{MinimalTemplate,PlatformerTemplate,PuzzleTemplate,RpgTemplate}.kt`.

**`gbkt-intellij-plugin/`:**
- Purpose: IntelliJ plugin (syntax, completion, visual editors, C preview).
- Contains: `kotlin/io/github/gbkt/intellij/`, resource bundles, plugin descriptor.

**`gbkt-examples/`:**
- Purpose: Example games + reference implementations.
- Contains: One directory per game (`pong/`, `breakout/`, `racer/`, `banks/`, `metasprites/`, `metasprites-stress/`, `simple-physics/`, `platformer-template/`). Each has a `build.gradle.kts` that applies the gbkt plugin.
- Note: `gbkt-examples/.archive/` contains retired examples (`shmup/`, `platformer/`); the file `gbkt-examples/CLAUDE.md` describes the active set.

**`LabyrinthOfTheDragon-port/`:**
- Purpose: Full-scale RPG reference using gbkt DSL.

**`.planning/`:**
- Purpose: GSD (Get Shit Done) workflow artifacts.
- Structure: One folder per phase under `phases/`, plus shared `codebase/`, `research/`, `seeds/`, `todos/`, `quick/`, `debug/`.

**`context/`:**
- Purpose: Long-form developer documentation referenced from CLAUDE.md.
- Key files: `ARCHITECTURE.md`, `DSL_REFERENCE.md`, `TESTING.md`, `DEVELOPER_EXPERIENCE.md`, `LOCALIZATION.md`, `TOOLING.md`, `CI_CD.md`, `UAT_GUIDE.md`, per-game UAT recipes (`UAT-pong.md`, etc.).

**`buildSrc/`:**
- Purpose: Gradle convention plugins shared across modules.

## Key File Locations

**Entry Points:**
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/GbktPlugin.kt`: Gradle plugin entry — registers all tasks.
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/GenerateCTask.kt:33`: Codegen Gradle task.
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CompileRomTask.kt:31`: ROM compile Gradle task.
- `gbkt-cli/src/main/kotlin/io/github/gbkt/cli/Main.kt`: CLI entry point.
- `gbkt-mcp-server/src/main/kotlin/io/github/gbkt/mcp/GbktMcpServer.kt`: MCP server entry point.
- `gbkt-test/src/main/kotlin/io/github/gbkt/test/GbktTestExtension.kt`: JUnit5 ROM-test entry.

**Configuration:**
- `settings.gradle.kts`: Module include list.
- `build.gradle.kts`: Root build script (toolchains, Spotless, Detekt, version catalog).
- `gradle.properties`: Gradle config (JVM args, parallel, caching).
- `detekt.yml`: Detekt rule config (with codegen/IR exclusions).
- `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/GbktExtension.kt`: User-facing `gbkt { ... }` config block.

**Core Logic — IR:**
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIR.kt`: Root IR type.
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/Expr.kt`: 11 expression node classes.
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOp.kt`: 52 ScriptOp node classes.
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/SystemIR.kt`: 8 SystemIR node classes.
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ExprVisitorI.kt`: Expr visitor contract.
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOpVisitorI.kt`: ScriptOp visitor contract.
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/SystemIRVisitorI.kt`: SystemIR visitor contract.
- `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIRSerializer.kt`: JSON round-trip.

**Core Logic — DSL:**
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt`: Top-level `game { ... }` receiver.
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ScriptBuilder.kt`: Records ScriptOps for scene lifecycles.
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt`: Actor DSL + `ActorDelegate` (line 1218) + `ActorPropertyRef` (line 61).
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt`: Scene DSL + `SceneRef` (line 23).
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt`: `AssignableVar` (line 90) + variable delegates (`u8Var`, `i8Var`, etc.).
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ExprBuilder.kt`: Operator overloads on `Expr`.
- `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/InputBuilders.kt`: `dpad`, `buttons` typed API.

**Core Logic — Backend (visitors):**
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ScriptOpVisitor.kt`: ScriptOp → CStatement.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ExprVisitor.kt`: Expr → CExpr.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/ActorVisitor.kt`: ActorIR → C arrays + helpers.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/SceneVisitor.kt`: SceneIR → enter/frame/exit CFunctions.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/GBDKSystemVisitor.kt`: SystemIR → C structures.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/{Rpg,Dialog,Menu,Hud,Inventory,Combat,Collision,Metasprite,Sound}Visitor.kt`: Domain visitors.

**Core Logic — Backend (C AST):**
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CFile.kt`: C source-file model.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CFunction.kt`: Function model.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CStatement.kt`: Statement model.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CExpr.kt`: Expression model.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CType.kt`: Type model.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CDeclaration.kt`: Declaration model.

**Core Logic — Backend (pipeline):**
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt`: Master orchestrator.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/SourceMapCollector.kt`: Tracks IR → C line mappings.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/VramAllocator.kt`: VRAM layout decisions.

**Core Logic — Backend (post-process):**
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/postprocess/COutputOptimizer.kt`: Peephole optimization.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/postprocess/FunctionDeduplicationPass.kt`: Merges identical helpers.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/postprocess/SharedConstantTablePass.kt`: Hoists shared LUTs.

**Core Logic — Backend (emit + profiles):**
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/emit/`: CEmitter (CFile → String).
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/profiles/GameBoyProfile.kt`: DMG target profile.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/profiles/GameBoyColorProfile.kt`: GBC target profile.
- `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/profiles/GameBoyConstants.kt`: Hardware constants.

**Core Logic — Analysis (11 passes):**
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/SemanticValidationPass.kt`
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstraintCheckPass.kt`
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BankingAnalysisPass.kt`
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/RAMPlanningPass.kt`
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/VRAMLayoutPass.kt`
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/OAMAllocationPass.kt`
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ResourceInventoryPass.kt`
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BudgetAuditPass.kt`
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/ConstantFoldingPass.kt`
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/DeadCodeEliminationPass.kt`
- `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/BitwiseOptimizationPass.kt`
- Plus `RacingValidationPass.kt` (sport-genre-specific) and `ScriptOpTraversal.kt` (shared traversal helper).

**Testing:**
- `gbkt-test/src/main/kotlin/io/github/gbkt/test/GbktTestExtension.kt`: JUnit5 extension.
- `gbkt-test/src/main/kotlin/io/github/gbkt/test/GbktGameAssertions.kt`: Fluent assertions.
- `gbkt-test/src/main/kotlin/io/github/gbkt/test/GbktTestRecipes.kt`: Composable recipes (`verifyTitleScreen`, `bootToScene`).
- `gbkt-test/src/main/kotlin/io/github/gbkt/test/GameDiscovery.kt`: ROM/metadata auto-discovery.
- `gbkt-test/src/main/kotlin/io/github/gbkt/test/GbktGameReporter.kt`: Test report output.
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/StepAgent.kt`: Emulator-driving agent.
- `gbkt-emulator/src/main/kotlin/io/github/gbkt/emulator/agent/UatRunner.kt`: Headless UAT runner.

**Asset Pipeline:**
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/AssetPipeline.kt`: Orchestrator.
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/AssetManifest.kt`: Manifest model.
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/PngValidator.kt`: PNG validation.
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/TileDeduplicator.kt`: Tile dedup.
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/LdtkParser.kt`: LDtk map parser.
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/TiledParser.kt`: Tiled map parser.
- `gbkt-core/src/main/kotlin/io/github/gbkt/core/PoParser.kt`: GNU gettext .po parser.

## Naming Conventions

**Files:**
- One top-level class/object per file; file name matches class name (`GameBuilder.kt` contains `GameBuilder`).
- IR node groupings allowed: `Expr.kt` contains all 11 expression data classes; `ScriptOp.kt` contains all 52 op classes.
- Builder files end with `Builder.kt`: `ActorBuilder.kt`, `SceneBuilder.kt`, `CombatEngineBuilder.kt`.
- Visitor files end with `Visitor.kt`: `ScriptOpVisitor.kt`, `ExprVisitor.kt`, `ActorVisitor.kt`.
- Visitor interfaces use `I` suffix to distinguish contract from implementation: `ExprVisitorI`, `ScriptOpVisitorI`, `SystemIRVisitorI`.
- Codegen files end with `Codegen` only when the class itself ends with `Codegen` (e.g., `GBDKCollectionCodegen.kt`); most backend logic uses `Visitor` suffix.
- IR files end with `IR.kt`: `GameIR.kt`, `SceneIR.kt`, `ActorIR.kt`, `WorldIR.kt`, `CombatEngineIR.kt`.
- Pass files end with `Pass.kt`: `BankingAnalysisPass.kt`, `SemanticValidationPass.kt`, `VRAMLayoutPass.kt`.
- Gradle task files end with `Task.kt`: `GenerateCTask.kt`, `CompileRomTask.kt`, `ConvertSpritesTask.kt`.
- Test files end with `Test.kt`: `GbktPluginTest.kt`, `IntegrationTest.kt`.
- Module CLAUDE files: `CLAUDE.md` at module root and at significant subdirectory roots (e.g., `gbkt-backend-gbdk/.../codegen/visitor/CLAUDE.md`).

**Directories:**
- Module roots are kebab-case with `gbkt-` prefix: `gbkt-ir`, `gbkt-backend-gbdk`, `gbkt-genre-rpg`.
- Source roots follow Kotlin/JVM convention: `src/{main,test}/{kotlin,resources}/`.
- Package paths mirror Java: `io/github/gbkt/<module-or-domain>/...`.
- Code subdirectories within a module are lowercase (`codegen`, `ast`, `pipeline`, `postprocess`, `profiles`, `dsl`, `domain`).

**Classes / functions:**
- Classes / objects / interfaces: `PascalCase` (`GameBuilder`, `CodegenBackend`, `ActorDelegate`).
- Functions, methods, properties: `camelCase` (`compileWithAssets`, `provideDelegate`, `currentBank`).
- IR data classes: `PascalCase` (`Literal`, `BinaryExpr`, `IfOp`, `NavigateTo`).
- Enum entries: `SCREAMING_SNAKE_CASE` (`VarType.UINT8`, `MovementStyle.GRID`, `BattleState.VICTORY`).
- DSL infix functions: lowercase verbs (`isAbove`, `isAtLeast`, `isEqualTo`, `logicalAnd`, `set`).
- Compile-time constants in IR: `SCREAMING_SNAKE_CASE` (`COMBAT_STATE_VICTORY`).
- Backend C symbol names: `_snake_case` prefix `_` (`_ball_dx`, `_score`, `_current_tileset_id`).

**Tasks (Gradle):**
- Task type classes: `PascalCase` ending in `Task` (`GenerateCTask`).
- Task names (registered with Gradle): `camelCase` (`generateC`, `compileRom`, `buildRom`, `runEmulator`).

## Where to Add New Code

**New IR node:**
- Expression node: add to `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/Expr.kt`; add `visit*` to `ExprVisitorI.kt`; implement in `gbkt-backend-gbdk/.../visitor/ExprVisitor.kt`.
- ScriptOp node: add to `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOp.kt`; add `visit*` to `ScriptOpVisitorI.kt`; implement in `gbkt-backend-gbdk/.../visitor/ScriptOpVisitor.kt`.
- System node: add to relevant `gbkt-ir/.../*IR.kt`; add `visit*` to `SystemIRVisitorI.kt`; implement in `gbkt-backend-gbdk/.../visitor/GBDKSystemVisitor.kt`.
- Serialization: add `serialize*`/`deserialize*` to `gbkt-ir/.../GameIRSerializer.kt`.
- See: context/DEVELOPER_EXPERIENCE.md → "Adding IR Nodes".

**New DSL construct:**
- Builder: extend or create `*Builder.kt` in `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/`.
- Operator overloads: place in `*Builder.kt` or extension functions on the receiver type.
- Property delegate: implement `provideDelegate` returning a typed ref (see `ActorDelegate` in `gbkt-lang/.../ActorBuilder.kt:1218`).
- See: context/DEVELOPER_EXPERIENCE.md → "Adding DSL Constructs".

**New analysis pass:**
- Pass: add `*Pass.kt` to `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/passes/`.
- Register in pipeline order: edit `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/DefaultPipeline.kt`.

**New backend:**
- Module: add `gbkt-backend-<target>/` as sibling to `gbkt-backend-gbdk`.
- Register: include in `settings.gradle.kts`; implement `CodegenBackend`; expose via ServiceLoader.
- Profile: add `*Profile.kt` describing target capabilities (e.g., screen size, VRAM bytes, banking model).

**New genre plugin:**
- Module: add `gbkt-genre-<name>/` with `dsl/`, `domain/`, `codegen/` subdirectories mirroring existing genres.
- Register in `settings.gradle.kts`.
- DSL entry points: provide builder + property delegate following `ActorDelegate` pattern.

**New Gradle task:**
- Class: add `*Task.kt` to `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/`.
- Registration: edit `GbktPlugin.kt` `registerTasks()` and wire input/output to upstream tasks.

**New example game:**
- Folder: `gbkt-examples/<game-name>/` with `src/main/kotlin/.../<Game>Main.kt`, `build.gradle.kts`, `res/` for assets.
- Registration: `include("gbkt-examples:<game-name>")` in `settings.gradle.kts`.
- The example consumes the gbkt plugin; no duplicated build logic.

**New documentation page:**
- Module-level: add `CLAUDE.md` to module root.
- Cross-cutting: add to `context/` (e.g., `context/TESTING.md`) and cross-link from root `CLAUDE.md`.

**Utilities:**
- Cross-module helpers: `gbkt-core/src/main/kotlin/io/github/gbkt/core/` (top-level files like `SourceMap.kt`, `FontCharacterMapping.kt`, `FileIO.kt`).
- Module-local helpers: a `utils/` or top-level file in the consuming module.

## Special Directories

**`buildSrc/`:**
- Purpose: Gradle convention plugins shared across modules.
- Generated: No.
- Committed: Yes.

**`build/`:**
- Purpose: Gradle build output (per-module and root).
- Generated: Yes.
- Committed: No (gitignored).

**`build/gbkt/`:**
- Purpose: gbkt-specific build output for a project (generated C, ROM, debug files).
- Layout: `build/gbkt/generated/{main.c,bankN.c,main.c.gbkt.map,game_metadata.json}`, `build/gbkt/output/<name>.gb`.
- Generated: Yes.
- Committed: No.

**`gbkt-examples/.archive/`:**
- Purpose: Retired example projects.
- Generated: No.
- Committed: Yes (historical reference).

**`sessions/`:**
- Purpose: Emulator saved sessions for manual replay.
- Generated: Partially (created by emulator).
- Committed: Partial (some session files checked in for tests, others gitignored).

**`scratch/`:**
- Purpose: Ephemeral exploratory work.
- Generated: No.
- Committed: No.

**`.planning/`:**
- Purpose: GSD workflow tracking.
- Generated: No.
- Committed: Yes (phase plans, specs, verification, evidence are part of history).

**`.claude/`:**
- Purpose: Claude Code skills + MCP server registration.
- Generated: Partially (by `./gradlew gbktSetupClaude`).
- Committed: Skill files are committed; `mcp_servers.json` is per-user.

**`.agents/skills/` (if present):**
- Purpose: Project-level agent skills.
- Generated: No.
- Committed: Yes.

**`LabyrinthOfTheDragon/`:**
- Purpose: Original C-based reference implementation (pre-Kotlin port).
- Generated: No.
- Committed: Yes (historical / reference for the Kotlin port).

---

*Structure analysis: 2026-05-27*
