<!-- refreshed: 2026-05-27 -->
# Architecture

**Analysis Date:** 2026-05-27

## System Overview

```text
┌──────────────────────────────────────────────────────────────────────┐
│                          USER (Kotlin DSL)                           │
│   `gbkt-examples/<game>/src/main/kotlin/...Main.kt`                  │
│   Calls game { scenes { ... } actors { ... } variables { ... } }     │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │
                                  ▼  builder receivers record into IR
┌──────────────────────────────────────────────────────────────────────┐
│                       DSL BUILDERS (recording)                       │
│   `gbkt-lang/.../dsl/GameBuilder.kt`                                 │
│   `gbkt-lang/.../dsl/ScriptBuilder.kt`                               │
│   `gbkt-lang/.../dsl/ActorBuilder.kt`                                │
│   `gbkt-lang/.../dsl/SceneBuilder.kt`                                │
│   `gbkt-lang/.../dsl/VariableBuilders.kt`                            │
│   Property delegates (ActorDelegate, AssignableVar) infer names      │
│   from Kotlin `val ... by ...` and emit IR ops.                      │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │
                                  ▼  emit() → ScriptOp / Expr nodes
┌──────────────────────────────────────────────────────────────────────┐
│                          IR (data only)                              │
│   `gbkt-ir/.../ir/GameIR.kt`        Root tree                        │
│   `gbkt-ir/.../ir/Expr.kt`          11 expression nodes              │
│   `gbkt-ir/.../ir/ScriptOp.kt`      52 op nodes                      │
│   `gbkt-ir/.../ir/SystemIR.kt`       8 system nodes                  │
│   `gbkt-ir/.../ir/SceneIR.kt`       enter/frame/exit ScriptOps       │
│   `gbkt-ir/.../ir/ActorIR.kt`       sprite, position, hitbox         │
│   Non-sealed interfaces + accept(visitor) dispatch.                  │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │
                                  ▼  PassPipeline.run(GameIR)
┌──────────────────────────────────────────────────────────────────────┐
│                      ANALYSIS PASSES (11 passes)                     │
│   `gbkt-analysis/.../AnalysisPass.kt`     fun interface              │
│   `gbkt-analysis/.../DefaultPipeline.kt`  ordered pipeline           │
│   `gbkt-analysis/.../passes/*.kt`         11 concrete passes         │
│   Each pass: PassContext → PassResult.Success | Failed               │
│   Halts on first Failed; accumulates Diagnostics; enriches context.  │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │
                                  ▼  Validated + annotated GameIR
┌──────────────────────────────────────────────────────────────────────┐
│                  BACKEND (CodegenBackend dispatch)                   │
│   `gbkt-backend-api/.../CodegenBackend.kt`   interface               │
│   `gbkt-backend-gbdk/.../GBDKBackend.kt`     GB/GBC implementation   │
│   `gbkt-backend-gbdk/.../codegen/pipeline/GBDKPipelineV2.kt`         │
│   Walks GameIR; dispatches to 14 visitors.                           │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │
                                  ▼  visitor.visit*(node)
┌──────────────────────────────────────────────────────────────────────┐
│                       CODEGEN VISITORS (14)                          │
│   `gbkt-backend-gbdk/.../codegen/visitor/`                           │
│   ScriptOpVisitor, ExprVisitor, ActorVisitor, SceneVisitor,          │
│   GBDKSystemVisitor, RpgVisitor, DialogVisitor, MenuVisitor,         │
│   HudVisitor, InventoryVisitor, CombatVisitor, CollisionVisitor,     │
│   MetaspriteVisitor, SoundVisitor                                    │
│   Each returns CStatement / CExpr nodes.                             │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │
                                  ▼  populate CFile / CFunction
┌──────────────────────────────────────────────────────────────────────┐
│                            C AST                                     │
│   `gbkt-backend-gbdk/.../codegen/ast/CFile.kt`                       │
│   `gbkt-backend-gbdk/.../codegen/ast/CFunction.kt`                   │
│   `gbkt-backend-gbdk/.../codegen/ast/CStatement.kt`                  │
│   `gbkt-backend-gbdk/.../codegen/ast/CExpr.kt`                       │
│   `gbkt-backend-gbdk/.../codegen/ast/CType.kt`                       │
│   Typed nodes (CIf, CFor, CCall, CBinaryExpr, CVarDecl, ...).        │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │
                                  ▼  post-processing passes
┌──────────────────────────────────────────────────────────────────────┐
│                       POST-PROCESS                                   │
│   `gbkt-backend-gbdk/.../codegen/postprocess/COutputOptimizer.kt`    │
│   `.../postprocess/FunctionDeduplicationPass.kt`                     │
│   `.../postprocess/SharedConstantTablePass.kt`                       │
│   Splits by bank, emits BANKED calling convention, dedups.           │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │
                                  ▼  CEmitter.emit(CFile) → String
┌──────────────────────────────────────────────────────────────────────┐
│                         OUTPUT (C source)                            │
│   `build/gbkt/generated/main.c`                                      │
│   `build/gbkt/generated/bank<N>.c`                                   │
│   `build/gbkt/generated/main.c.gbkt.map`  (source map)               │
│   Consumed by lcc/sdcc → .gb / .gbc ROM (Gradle plugin invokes).     │
└──────────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| GameBuilder | Top-level DSL receiver; collects scenes/actors/variables/systems into GameIR | `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/GameBuilder.kt` |
| ScriptBuilder | Records `ScriptOp`s for scene lifecycles, `whenever` blocks, frame loops | `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ScriptBuilder.kt` |
| ActorBuilder | Records actor sprite/position/hitbox + custom properties | `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt` |
| ActorDelegate | `val ball by actor { ... }` — provideDelegate captures property name | `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt:1218` |
| AssignableVar | DSL-side variable handle with operator overloads (`set`, `+=`, `++`) | `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt:90` |
| ActorPropertyRef | Typed reference to an actor property (`ball.x`, `paddle.y`) with full op set | `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt:61` |
| SceneRef | Type-safe scene reference (`val gameplayScene = scene("gameplay") { ... }`) | `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt:23` |
| GameIR | Root data tree (immutable IR snapshot) | `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/GameIR.kt` |
| Expr / ScriptOp / SystemIR | Non-sealed interfaces with `accept(visitor)` dispatch | `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/{Expr,ScriptOp,SystemIR}.kt` |
| ExprVisitorI<T> | Visitor contract for 10 Expr subtypes | `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ExprVisitorI.kt` |
| ScriptOpVisitorI<T> | Visitor contract for 52 ScriptOp subtypes | `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/ScriptOpVisitorI.kt` |
| SystemIRVisitorI<T> | Visitor contract for 8 SystemIR subtypes | `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/SystemIRVisitorI.kt` |
| AnalysisPass | `fun interface` — single analysis step | `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/AnalysisPass.kt:22` |
| PassPipeline / DefaultPipeline | Ordered execution of 11 passes; halts on failure | `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/{PassPipeline,DefaultPipeline}.kt` |
| CodegenBackend | Contract: validate(GameIR) + generate(GameIR) → source files | `gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/CodegenBackend.kt` |
| BackendRegistry | ServiceLoader-style backend discovery (`gbdk`, future `gba`, etc.) | `gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/BackendRegistry.kt` |
| GBDKBackend | GB/GBC backend; delegates to GBDKPipelineV2 | `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/GBDKBackend.kt:32` |
| GBDKPipelineV2 | Orchestrates 14 visitors → CFile tree → emit + bank split | `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/pipeline/GBDKPipelineV2.kt` |
| GenerateCTask | Gradle entry point: loads compiled DSL, runs GameIR → C codegen | `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/GenerateCTask.kt:33` |
| CompileRomTask | Invokes external `lcc` to compile generated C → .gb ROM | `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CompileRomTask.kt:31` |

## Pattern Overview

**Overall:** Multi-stage compiler pipeline: Kotlin DSL (recording) → IR (data) → Analysis (validate/optimize) → Backend (visitor codegen) → C AST → emitted C source → ROM (external toolchain).

**Key Characteristics:**
- **Non-sealed IR + visitor dispatch.** IR interfaces are non-sealed; backends implement `*VisitorI<T>` and dispatch via `node.accept(this)`. This decouples IR from any single module and lets backends/genres add visit logic without modifying `gbkt-ir`.
- **Layered modules with strict downward dependencies.** `gbkt-ir` is a leaf (zero gbkt deps); higher modules depend only on lower ones. Boundary violations are enforced by a `validateModuleBoundaries` Gradle task.
- **Recording-style DSL.** Builder receivers (`GameBuilder`, `ScriptBuilder`, `ActorBuilder`) capture user code into IR data classes. The IR is the boundary between user code and codegen.
- **Property delegates for name inference.** `val ball by actor { ... }`, `var score by u8Var(0)` use Kotlin `provideDelegate` to capture the property name and produce typed handles (`ActorDelegate` → ActorRef, `VarDelegate` → `AssignableVar`).
- **Visitor-driven codegen.** 14 visitors in `gbkt-backend-gbdk/.../codegen/visitor/` each handle one IR domain (scripts, actors, scenes, dialog, menus, HUD, inventory, combat, collision, metasprites, sound, RPG, system).
- **Composable analysis passes.** 11 `AnalysisPass` lambdas run in order against a `PassContext`; each enriches context or halts pipeline.
- **Backend dispatch via ServiceLoader.** New targets (GBA, NES) drop in as sibling modules implementing `CodegenBackend`.

## Layers

**Layer 1 — IR (`gbkt-ir`):**
- Purpose: Pure data types — the lingua franca between DSL and backends.
- Location: `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/`
- Contains: `Expr`, `ScriptOp`, `SystemIR`, `GameIR`, `SceneIR`, `ActorIR`, `WorldIR`, visitor interfaces, JSON serializer.
- Depends on: Only `org.json` for serialization. **Zero gbkt dependencies.**
- Used by: Every other layer.

**Layer 2 — DSL (`gbkt-lang`, `gbkt-engine`, `gbkt-world`):**
- Purpose: User-facing builder API that records IR.
- Location: `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/`, `gbkt-engine/src/main/kotlin/io/github/gbkt/core/{combat,entity,graphics,input,inventory,pickup,scene}/`, `gbkt-world/src/main/kotlin/io/github/gbkt/core/{world,exploration}/`
- Contains: Builder classes, property delegates, DSL marker annotations, expression operator overloads.
- Depends on: `gbkt-ir`.
- Used by: `gbkt-core`, genre plugins, user game code.

**Layer 3 — Core aggregator (`gbkt-core`):**
- Purpose: Re-exports IR + DSL + engine + world, adds asset pipeline + constraints + test infra.
- Location: `gbkt-core/src/main/kotlin/io/github/gbkt/core/`
- Contains: `assets/`, `constraints/` (TargetProfile, ScreenSpec, etc.), `optimization/`, parsers (`LdtkParser`, `TiledParser`, `PoParser`), `test/` (SimulationContextV2, ScriptOpInterpreter), `flow/`, `validation/`, asset pipeline orchestration.
- Depends on: `gbkt-ir`, `gbkt-lang`, `gbkt-engine`, `gbkt-world`.
- Used by: Backends, analysis, genres, tooling.

**Layer 4 — Backend API (`gbkt-backend-api`):**
- Purpose: Codegen contract for any target.
- Location: `gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/`
- Contains: `CodegenBackend` interface, `BackendRegistry`, `GenerationResult`, `ValidationResult`, `GenreSystemVisitor`, `CollectionCodegen`.
- Depends on: `gbkt-ir`, `gbkt-core` (for `TargetProfile`).
- Used by: Each concrete backend.

**Layer 5 — Backend implementation (`gbkt-backend-gbdk`):**
- Purpose: Generate GBDK C from GameIR.
- Location: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/`
- Contains: `codegen/visitor/` (14 visitors), `codegen/ast/` (C AST), `codegen/pipeline/` (GBDKPipelineV2, VramAllocator, SourceMapCollector), `codegen/postprocess/` (optimizer, dedup, shared constants), `codegen/emit/` (CEmitter → String), `profiles/` (GameBoyProfile, GameBoyColorProfile).
- Depends on: `gbkt-backend-api`, `gbkt-core`.

**Layer 6 — Analysis (`gbkt-analysis`):**
- Purpose: Static validation, optimization, resource planning over GameIR.
- Location: `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/`
- Contains: `AnalysisPass`, `PassContext`, `PassResult`, `PassPipeline`, `DefaultPipeline`, 11 passes in `passes/`, `OptimizationReport`.
- Depends on: `gbkt-core`.

**Layer 7 — Genre plugins (`gbkt-genre-{rpg,platformer,puzzle,sport}`):**
- Purpose: Domain-specific DSL + IR + codegen plugins.
- Location: `gbkt-genre-<name>/src/main/kotlin/io/github/gbkt/{rpg,genre/platformer,genre/puzzle,genre/sport}/`
- Contains: `dsl/` (builders), `domain/` (domain types), `codegen/` (visitor specializations).
- Depends on: `gbkt-core`, `gbkt-backend-api`.

**Layer 8 — Tooling (`gbkt-gradle-plugin`, `gbkt-cli`, `gbkt-intellij-plugin`, `gbkt-emulator`, `gbkt-test`, `gbkt-mcp-server`):**
- Purpose: Build integration, IDE support, emulation, testing, AI-agent integration.
- Location: Module roots.
- Depends on: Library modules above (varies per tool).

## Data Flow

### Primary Compilation Path (Kotlin source → ROM)

1. **User invokes** `./gradlew :gbkt-examples:<game>:buildRom` (`gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/GbktPlugin.kt`)
2. **`processAssets`** validates and prepares sprite PNGs (`gbkt-gradle-plugin/.../tasks/ProcessAssetsTask.kt:52`)
3. **`convertSprites`** runs `png2asset` → tile data (`gbkt-gradle-plugin/.../tasks/ConvertSpritesTask.kt:55`)
4. **`generateC`** loads compiled DSL via reflection, calls `Game.compileWithAssets()` (`gbkt-gradle-plugin/.../tasks/GenerateCTask.kt:33`)
   - DSL builders run; `ScriptBuilder.emit(op)` appends `ScriptOp` nodes to the active script.
   - `GameBuilder.build()` returns `GameIR` (root data tree).
5. **`DefaultPipeline.run(GameIR)`** executes 11 analysis passes in order (`gbkt-analysis/.../DefaultPipeline.kt`)
   - Returns `PassResult.Success(PassContext)` with diagnostics + annotations OR `PassResult.Failed(diagnostics)` (halts).
6. **`BackendRegistry.get("gbdk").generate(gameIR)`** (`gbkt-backend-api/.../BackendRegistry.kt`)
7. **`GBDKBackend.generate()`** → **`GBDKPipelineV2.run()`** (`gbkt-backend-gbdk/.../GBDKBackend.kt:56` → `pipeline/GBDKPipelineV2.kt`)
   - Constructs visitors (`ScriptOpVisitor`, `ExprVisitor`, `ActorVisitor`, etc.) wired together.
   - Walks `GameIR.scenes`, `actors`, `systems`, dispatching via `node.accept(visitor)`.
   - Each visit returns `CStatement` / `CExpr` nodes that populate `CFile` / `CFunction` trees.
8. **Post-processing passes** (`gbkt-backend-gbdk/.../codegen/postprocess/`)
   - `FunctionDeduplicationPass` collapses identical helpers.
   - `SharedConstantTablePass` hoists shared lookup tables.
   - `COutputOptimizer` performs peephole pass.
   - Bank splitter (`splitByBank`) generates `main.c` + `bankN.c` files, applies BANKED calling convention.
9. **`CEmitter`** stringifies `CFile` → C source (`gbkt-backend-gbdk/.../codegen/emit/CEmitter.kt`)
10. **`compileRom`** invokes external `lcc` (GBDK) to produce `.gb` (`gbkt-gradle-plugin/.../tasks/CompileRomTask.kt:31`)

### DSL Recording Flow (single `whenever` example)

1. User writes `whenever(ball.x isAbove 160) { ballDx set -1 }` inside `frame { ... }`.
2. `frame { ... }` opens a `ScriptBuilder` and sets it as the active recording context (`gbkt-lang/.../dsl/ScriptBuilderContext.kt`).
3. `ball.x` resolves to `ActorPropertyRef("ball", "x")` (`gbkt-lang/.../dsl/ActorBuilder.kt:61`).
4. `isAbove 160` returns `BinaryExpr(VarRef("_ball_x"), GT, Literal(160))`.
5. `whenever(expr) { body }` records the body via a nested `ScriptBuilder`, then emits `IfOp(expr, bodyOps)` on the parent.
6. Inside body, `ballDx set -1` invokes `infix fun AssignableVar.set(Int)` which emits `Assign("_ball_dx", Literal(-1))`.
7. After `frame` block exits, the parent `SceneBuilder` captures the recorded ops as `SceneIR.frameOps`.

### Visitor Dispatch (codegen example)

1. `GBDKPipelineV2` calls `scriptOp.accept(scriptOpVisitor)` for each op in a scene's frame list.
2. `ScriptOpVisitor.visitIfOp(IfOp)` (`gbkt-backend-gbdk/.../codegen/visitor/ScriptOpVisitor.kt`) recursively visits `expr` via `ExprVisitor` and visits body ops; builds `CIf(condExpr, bodyStmts)`.
3. `ExprVisitor.visitBinaryExpr(BinaryExpr)` emits `CBinaryExpr(lhs, op, rhs)`.
4. Resulting `CStatement` tree is appended to the scene's enter/frame/exit `CFunction`.

**State Management:**
- DSL recording uses a thread-local "active builder" stack (`ScriptBuilderContext`).
- After build, `GameIR` is immutable.
- Analysis passes accumulate state in `PassContext` (functional — each pass returns a new context).
- Codegen state is encapsulated in visitor instances; the pipeline coordinates ordering.

## Key Abstractions

**ActorDelegate (DSL):**
- Purpose: Implements `provideDelegate` so `val ball by actor { ... }` infers the actor ID from the Kotlin property name.
- Examples: `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt:1218`
- Pattern: Kotlin property delegation (`provideDelegate` + `getValue`).

**AssignableVar (DSL):**
- Purpose: Typed handle to a recorded variable; exposes operator overloads (`set`, `+=`, `++`, `--`, `*=`, etc.) that emit `Assign`/`CompoundAssign` ScriptOps.
- Examples: `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt:90`
- Pattern: Value class wrapping a variable name, with extension operator functions.

**ActorPropertyRef (DSL):**
- Purpose: Typed reference to an actor property (`ball.x`, `paddle.y`, `ball.visible`); supports the same operator set as `AssignableVar`.
- Examples: `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt:61`
- Pattern: Data class with extension operators producing ScriptOps against a synthetic global variable `_<actor>_<prop>`.

**SceneRef (DSL):**
- Purpose: Type-safe scene reference returned by `scene("id") { ... }`; usable in `navigate(sceneRef)` for compile-time checking instead of stringly-typed navigation.
- Examples: `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/SceneBuilder.kt:23`
- Pattern: Data class wrapping the scene ID.

**Expr / ScriptOp / SystemIR (IR):**
- Purpose: Non-sealed interfaces, each with `accept(visitor)` for double dispatch.
- Examples: `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/Expr.kt`, `ScriptOp.kt`, `SystemIR.kt`.
- Pattern: Visitor pattern (replaces V1's sealed-when matching to enable cross-module extension).

**Ref<T> (IR):**
- Purpose: Type-safe cross-reference by string ID with kind enum (`SCENE`, `ACTOR`, `SYSTEM`, `VARIABLE`, `ASSET`, `ZONE`).
- Examples: `gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/Ref.kt`
- Pattern: Phantom-typed identifier; lookup happens during analysis.

**CodegenBackend (Backend API):**
- Purpose: Contract every target platform implements (`validate(GameIR) → ValidationResult`, `generate(GameIR) → GenerationResult`).
- Examples: `gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/CodegenBackend.kt`
- Pattern: Strategy + service-locator (via `BackendRegistry`).

**AnalysisPass (Analysis):**
- Purpose: One step in the analysis pipeline; functional interface so passes can be expressed as lambdas.
- Examples: `gbkt-analysis/src/main/kotlin/io/github/gbkt/analysis/AnalysisPass.kt:22`
- Pattern: Chain-of-responsibility with halt-on-failure.

**CFile / CFunction / CStatement / CExpr (C AST):**
- Purpose: Typed Kotlin model of generated C — visitors build this tree, `CEmitter` stringifies it.
- Examples: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/`
- Pattern: Abstract syntax tree with emit-via-visitor.

**TargetProfile (Constraints):**
- Purpose: Describes a target's capabilities (sprite count, VRAM bytes, banking model) — backends declare a profile.
- Examples: `gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/`, `gbkt-backend-gbdk/.../profiles/GameBoyProfile.kt`, `GameBoyColorProfile.kt`.
- Pattern: Value object consumed by analysis passes (`ConstraintCheckPass`).

## Entry Points

**Gradle plugin (primary):**
- Location: `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/GbktPlugin.kt`
- Triggers: `./gradlew :<example>:generateC`, `:buildRom`, `:runEmulator`, `:emulatorTest`, etc.
- Responsibilities: Register all task types, wire task dependencies, configure asset pipeline.

**`GenerateCTask` (codegen entry):**
- Location: `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/GenerateCTask.kt:33`
- Triggers: `./gradlew :<example>:generateC` (or transitively `:buildRom`).
- Responsibilities: Worker-isolated classloader, reflective DSL loading, asset pipeline run, analysis pipeline, backend codegen.

**`CompileRomTask` (ROM build):**
- Location: `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/tasks/CompileRomTask.kt:31`
- Triggers: `./gradlew :<example>:buildRom` (depends on `generateC`).
- Responsibilities: Invoke external `lcc` (GBDK toolchain) with bank flags.

**CLI tool:**
- Location: `gbkt-cli/src/main/kotlin/io/github/gbkt/cli/Main.kt`
- Triggers: `gbkt new`, `gbkt build`, etc.
- Responsibilities: Project scaffolding from templates (`minimal`, `platformer`, `puzzle`, `rpg`).

**MCP server (AI-agent integration):**
- Location: `gbkt-mcp-server/src/main/kotlin/io/github/gbkt/mcp/GbktMcpServer.kt`
- Triggers: Claude Code spawns this over stdio.
- Responsibilities: Expose `StepAgent` as MCP tools (`emulator_start`, `emulator_step`, `emulator_screenshot`, etc.) wrapping `gbkt-emulator`.

**JUnit test extension:**
- Location: `gbkt-test/src/main/kotlin/io/github/gbkt/test/GbktTestExtension.kt`
- Triggers: `@ExtendWith(GbktTestExtension::class)` on a test class.
- Responsibilities: ROM discovery, metadata loading, fluent assertions, auto-screenshot on failure.

**IntelliJ plugin:**
- Location: `gbkt-intellij-plugin/src/main/kotlin/io/github/gbkt/intellij/`
- Triggers: Loaded by IntelliJ when a gbkt project is opened.
- Responsibilities: Syntax highlighting, completion, visual editors, generated-C preview.

## Architectural Constraints

- **Module boundary enforcement:** A `validateModuleBoundaries` Gradle task runs in `check` for each module. `gbkt-ir` rejects any dependency on `gbkt-lang`, `gbkt-engine`, `gbkt-world`, or `gbkt-core`. Violations break the build.
- **Single backend invocation per build:** A Gradle build configures one target (`gbkt { target("gbc") }`); multi-target builds run the plugin once per target.
- **Recording-context thread locality:** `ScriptBuilderContext` is thread-local — DSL must be invoked from a single thread. The Gradle worker model satisfies this.
- **Banking model in BackingConfig:** `BankingConfig` defaults place all banked code in bank 1. Complex games (RPGs, dungeon crawlers) MUST override `config { banking { ... } }` to spread code across banks (see CLAUDE.md → Banking Defaults).
- **BANKED calling convention:** `splitByBank` auto-adds `BANKED` to ALL function defs in non-zero banks; forward declarations are stripped from per-bank files (`game.h` provides BANKED prototypes). Missing this causes "MBC5 unknown address/value" runtime errors.
- **Global state in DSL:** Builder receivers hold mutable state during DSL recording; after `build()` returns, `GameIR` is immutable. No module-level singletons in the IR.
- **Window-layer UI rule:** All UI text (dialogs, menus, battle UI, HUDs) MUST render on the GBDK window layer via `_win_*` helpers (`WindowTextCodegen`). Using `gotoxy`/`printf` corrupts the background tile layer when custom tilesets are loaded.
- **Visual-evidence rule (verification):** Truths shaped "X is visible on screen" require runtime screenshot evidence, not just variable-state assertions. See CLAUDE.md → "Verification Methodology — Visual Evidence Rule".

## Anti-Patterns

### Adding new IR node without updating visitor interface

**What happens:** Developer adds a `data class FooOp : ScriptOp` in `gbkt-ir/.../ScriptOp.kt` and forgets to add `visitFooOp` to `ScriptOpVisitorI`.
**Why it's wrong:** Compilation succeeds in `gbkt-ir` (non-sealed), but every backend visitor would silently miss the new node at runtime. The visitor-interface pattern is specifically intended to enforce exhaustive dispatch.
**Do this instead:** Always add the new `visit*` method to the matching visitor interface (`gbkt-ir/.../{Expr,ScriptOp,SystemIR}VisitorI.kt`) in the same PR. All backends will then fail to compile until they implement it — which is the desired forcing function.

### Using sealed-when matching outside `gbkt-ir`

**What happens:** Pattern `when (op) { is Assign -> ... ; is IfOp -> ... }` outside `gbkt-ir`.
**Why it's wrong:** `ScriptOp` is non-sealed; the `when` is non-exhaustive and silently skips unknown nodes (a new op type from a genre plugin will fall through).
**Do this instead:** Implement `ScriptOpVisitorI<T>` and dispatch via `op.accept(this)`. Compiler then enforces exhaustiveness across all subtypes in scope.

### Stringly-typed scene navigation

**What happens:** `navigate("gameplay")` where "gameplay" is a magic string.
**Why it's wrong:** Typo (`"gameply"`) compiles fine; failure surfaces at runtime as a missing scene.
**Do this instead:** Capture `val gameplayScene = scene("gameplay") { ... }` and `navigate(gameplayScene)`. `SceneRef` (`gbkt-lang/.../dsl/SceneBuilder.kt:23`) is the typed handle.

### Magic strings in DSL parameters

**What happens:** `actor("ball") { ... }` instead of `val ball by actor { ... }`.
**Why it's wrong:** Duplicates the name in source (string + variable name); violates project rule #1 (No Magic Strings). The property delegate pattern infers the name from the `val` declaration.
**Do this instead:** Always use `val foo by actor { ... }`, `var score by u8Var(0)`, etc. The property delegate captures the property name into the IR.

### Forgetting `returnToHome()` after `setBank(N)`

**What happens:** `ZoneCodegen.generateBankedTilemapData()` calls `setBank(16)` but does not restore home before returning.
**Why it's wrong:** Subsequent codegen inherits the bank assignment, dumping unrelated code (combat, stats) into the wrong bank → bank overflow at link time.
**Do this instead:** Every `setBank(N)` must be paired with `setBank(16)` (the correct return) or explicit return-to-home. See MEMORY.md "GBDK Bank Overflow Bug Pattern".

### Exposing C-level details in test surface

**What happens:** A test asserts on a specific GBDK tile offset or VRAM address.
**Why it's wrong:** Tests should speak gbkt/Kotlin — they break on any harmless layout change in codegen.
**Do this instead:** Use `GbktTestExtension` semantic assertions: `assertScene("gameplay")`, `assertActorVisible(ball)`, `assertTextOnScreen("Game Over")`.

## Error Handling

**Strategy:** Layered — DSL errors via `error()` at recording time; analysis errors via `Diagnostic` accumulation; codegen errors fail-fast; compilation errors surface via Gradle.

**Patterns:**
- **DSL recording errors:** `error("ActorDelegate not initialized — was provideDelegate called?")` thrown at runtime if delegate misuse detected. Errors include the property name and source location when available.
- **Analysis diagnostics:** `Diagnostic(severity, message, source location)` collected by each pass into `PassContext.diagnostics`; surfaced as warnings or errors. `PassResult.Failed` halts the pipeline.
- **Codegen errors:** Backend `validate()` returns `ValidationResult` with errors/warnings (does not throw). The Gradle plugin checks the result and fails the task.
- **Toolchain errors:** `CompileRomTask` runs `lcc` via `ExecOperations`; non-zero exit + stderr is surfaced as a `GradleException` with the original lcc message and an "enhanced error" suggesting fixes (banking, undefined symbols, etc.).

## Cross-Cutting Concerns

**Logging:**
- Build-time: Gradle's `logger.lifecycle/info/warn`.
- Runtime (in-emulator): `EmuPrintfInterceptor` (`gbkt-emulator/.../debug/EmuPrintfInterceptor.kt`) captures `printf()` output and tags entries via `SourceMapResolver`.

**Validation:**
- Asset validation: `PngValidator`, `AssetPipeline` in `gbkt-core`.
- Semantic validation: `SemanticValidationPass` in `gbkt-analysis/.../passes/`.
- Constraint validation: `ConstraintCheckPass` against `TargetProfile`.
- Genre validation: `RacingValidationPass` (genre-specific).

**Source mapping:**
- `SourceMap.kt` (`gbkt-core/src/main/kotlin/io/github/gbkt/core/SourceMap.kt`) defines the model.
- `SourceMapCollector` (`gbkt-backend-gbdk/.../codegen/pipeline/SourceMapCollector.kt`) emits the map during codegen.
- `SourceLocationCapture` (`gbkt-lang/.../dsl/SourceLocationCapture.kt`) attaches Kotlin file/line to IR nodes for accurate error reporting.

**Asset pipeline:**
- Orchestration: `gbkt-core/.../AssetPipeline.kt`.
- PNG → tile conversion runs in `ConvertSpritesTask` via external `png2asset`.
- Manifest produced by `AssetManifest.kt`.

**Banking analysis:**
- `BankingAnalysisPass` (`gbkt-analysis/.../passes/BankingAnalysisPass.kt`) assigns code to ROM banks based on `BankingConfig`.

## V2 Modular Architecture (Non-Sealed IR + Visitor Pattern)

**TL;DR:** V1 used `sealed interface IRStatement` / `sealed interface IRExpression`, forcing all IR nodes into a single module. V2 replaced sealed hierarchies with **non-sealed interfaces** and **visitor interfaces**, enabling the 20-module split.

**V1 constraint (historical):**
- Kotlin `sealed` requires all implementations in the same module.
- Result: monolithic `gbkt-core` with DSL + IR + backends + analysis bundled together.
- Adding a new backend or genre required modifying core IR; multi-target builds tangled their concerns.

**V2 solution (current):**

```kotlin
// In gbkt-ir (leaf module, zero gbkt deps)
interface Expr { fun <R> accept(visitor: ExprVisitorI<R>): R }
interface ScriptOp { fun <R> accept(visitor: ScriptOpVisitorI<R>): R }
interface SystemIR { fun <R> accept(visitor: SystemIRVisitorI<R>): R }
```

This enables:
1. **`gbkt-ir`** — All IR types in a standalone leaf module.
2. **`gbkt-lang`** — DSL builders produce IR, separated from IR definitions.
3. **`gbkt-engine`** — Runtime engine types (combat, input, scene, graphics), separated from DSL recording.
4. **`gbkt-world`** — World / exploration types, separated from core.
5. **`gbkt-backend-gbdk`** — Codegen implements `*VisitorI<T>`, separated from IR.
6. **Genre plugins** — Add domain types and visitor specializations without modifying core IR.

**Exhaustive matching enforcement:**
The visitor interfaces force exhaustive dispatch — every backend must implement `visitFooOp` for every `FooOp`. The compiler catches a missing case as soon as a backend tries to compile. The behavior is preserved from `sealed when` exhaustiveness while removing the single-module constraint.

**The `I` suffix on visitor interfaces** (`ExprVisitorI`, `ScriptOpVisitorI`, `SystemIRVisitorI`) distinguishes the IR-defined visitor contract from any concrete backend visitor (`ScriptOpVisitor`, etc.) that implements it.

---

*Architecture analysis: 2026-05-27*
