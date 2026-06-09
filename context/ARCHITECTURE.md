# gbkt Architecture

## Compilation Pipeline

```
Kotlin DSL  →  IR  →  Analysis (12 passes)  →  Annotation  →  C AST  →  C text  →  Post-processing
(gbkt-lang)   (gbkt-ir)   (gbkt-analysis)                  (gbkt-backend-gbdk)
```

1. **DSL recording** — `game { }` executes user code inside builder contexts (`GameBuilder`, `ScriptBuilder`); every DSL call records IR nodes instead of executing.
2. **IR construction** — `GameBuilder.build()` produces a `GameIR` tree (scenes, actors, variables, systems, zones, pools).
3. **Analysis** — `DefaultPipeline` runs 12 ordered passes that validate the IR and allocate hardware resources (banks, VRAM, OAM, RAM) automatically.
4. **Annotation** — `applyAnnotations()` copies bank/VRAM/OAM assignments back onto the `GameIR`.
5. **Code generation** — `GBDKPipeline` builds a typed C AST (`CFile` trees) from the annotated IR via 13 visitors, then `CEmitter` serializes to C text with source-map collection.
6. **Post-processing** — `COutputOptimizer` deduplicates constants and functions on the emitted text to shrink ROM size.

Compilation to a `.gb` ROM (invoking GBDK's `lcc`) is owned by the Gradle plugin / CLI, not the backend — see [Backend Responsibilities](#backend-responsibilities).

## Module Dependency Graph

```
                    ┌──────────┐
                    │ gbkt-ir  │  (leaf — zero gbkt deps)
                    └────┬─────┘
              ┌──────────┼──────────┐
              ▼          ▼          ▼
         ┌─────────┐ ┌─────────┐ ┌─────────┐
         │gbkt-lang│ │gbkt-world│ │         │
         └────┬────┘ └────┬────┘ │         │
              ▼            │      │         │
         ┌──────────┐     │      │         │
         │gbkt-engine│     │      │         │
         └────┬─────┘     │      │         │
              └──────┬─────┘      │         │
                     ▼            │         │
                ┌─────────┐      │         │
                │gbkt-core│◄─────┘         │
                └────┬────┘                │
                     ▼                     │
              ┌──────────────┐             │
              │gbkt-backend- │   ┌─────────┴──────────┐
              │     api      │   │  gbkt-genre-*      │
              └──────┬───────┘   │  (rpg, platformer, │
                     │           │   puzzle, sport)    │
              ┌──────┼───────┐   └────────────────────┘
              ▼      ▼       ▼
        ┌──────────┐ ┌──────────┐
        │gbkt-     │ │gbkt-     │
        │analysis  │ │backend-  │
        └──────────┘ │  gbdk    │
                     └──────────┘
```

**Tooling** (not shown): `gbkt-gradle-plugin`, `gbkt-cli`, `gbkt-intellij-plugin` consume the library modules. `gbkt-emulator`, `gbkt-test`, `gbkt-mcp-server` form the agent-testing stack.

### Module Layers

| Layer | Modules | Role |
|-------|---------|------|
| IR | `gbkt-ir` | Pure data types, zero gbkt dependencies |
| DSL | `gbkt-lang`, `gbkt-engine`, `gbkt-world` | Builder pattern, recording contexts |
| Aggregator | `gbkt-core` | Re-exports above + asset pipeline, constraints, test infra |
| Backend | `gbkt-backend-api`, `gbkt-backend-gbdk` | Code generation contract + GBDK implementation |
| Analysis | `gbkt-analysis` | 12 static analysis passes |
| Genres | `gbkt-genre-{rpg,platformer,puzzle,sport}` | Domain-specific DSL + types, ServiceLoader-discovered |
| Tooling | `gbkt-gradle-plugin`, `gbkt-cli`, `gbkt-intellij-plugin` | Build, CLI, IDE |
| Testing | `gbkt-emulator`, `gbkt-test`, `gbkt-mcp-server` | Embedded emulator, JUnit5 extension, MCP agent tools |

Per-module detail (key files, dependencies, common tasks) lives in each module's `CLAUDE.md` — see the Module Documentation table in the root [CLAUDE.md](../CLAUDE.md).

## IR Design: Non-Sealed Interfaces + Visitor Dispatch

The IR is built on three **non-sealed** interfaces in `gbkt-ir`, each with an `accept(visitor)` method:

```kotlin
interface Expr     { fun <R> accept(visitor: ExprVisitorI<R>): R }      // 11 expression node types
interface ScriptOp { fun <R> accept(visitor: ScriptOpVisitorI<R>): R }  // ~52 operation node types
interface SystemIR { /* dialog, sound, save, camera, exploration, combat, ... */ }
```

Backends and analysis passes implement the corresponding visitor interfaces (`ExprVisitorI`, `ScriptOpVisitorI`, `SystemIRVisitorI`). Exhaustive dispatch is enforced by the visitor interface — adding a node type forces a compile error in every visitor until it is handled, so no backend can silently skip a node.

### Why non-sealed? (V1 → V2 history)

V1 used `sealed interface IRStatement` / `sealed interface IRExpression` with exhaustive `when` matching. Kotlin's sealed interfaces forced all IR nodes — and everything that matched on them — into one monolithic `gbkt-core`. V2 replaced sealed hierarchies with non-sealed interfaces + visitors, enabling the multi-module split:

1. **`gbkt-ir`** — all IR types in a standalone leaf module
2. **`gbkt-lang`** — DSL builders that produce IR, separate from IR definitions
3. **`gbkt-engine`** — runtime type definitions, separate from DSL
4. **`gbkt-backend-gbdk`** — codegen implements visitors, separate from IR
5. **Genre plugins** — add domain types without modifying core IR

### IR tree structure

```
GameIR (root)
 ├── scenes: List<SceneIR>      (enter/frame/exit contain List<ScriptOp>)
 ├── actors: List<ActorIR>
 ├── variables / arrays
 ├── systems: List<SystemIR>    (dialog, sound, save, exploration, combat, ...)
 ├── zones: List<ZoneIR>        (world map data)
 ├── pools: List<ActorPoolIR>
 └── items, structs, collections, puzzleObjects, ...
```

The full node catalog is in `gbkt-ir/CLAUDE.md`.

### DSL recording pattern

`gbkt-lang` uses two thread-local contexts so DSL code never passes builders explicitly:

- **`GameBuilderContext`** — active `GameBuilder`; lets `var score by u8Var(0)` delegates auto-register `VariableDef`s.
- **`ScriptBuilderContext`** — active `ScriptBuilder`; lets operators like `score += 10` emit `ScriptOp` nodes into the enclosing `enter { }` / `frame { }` block.

```
game { }  →  GameBuilder  →  GameIR
  scene { enter { } frame { } }  →  SceneBuilder  →  SceneIR
    whenever(cond) { body }      →  ScriptBuilder  →  If ScriptOp
      score += 10                →  AssignableVar operator  →  Assign ScriptOp
```

## Analysis Passes

`DefaultPipeline` (in `gbkt-analysis`) registers 12 passes:

| Category | Passes |
|----------|--------|
| Optimization | `DeadCodeEliminationPass`, `ConstantFoldingPass`, `BitwiseOptimizationPass` |
| Validation | `SemanticValidationPass`, `RacingValidationPass`, `ConstraintCheckPass` |
| Resource allocation | `ResourceInventoryPass`, `BankingAnalysisPass` (FFD bin-packing + trampolines), `VRAMLayoutPass`, `OAMAllocationPass`, `RAMPlanningPass` |
| Release gate | `BudgetAuditPass` (cargo-style budget report) |

## GBDK Backend

`GBDKBackend` implements `CodegenBackend` (from `gbkt-backend-api`). Key design decisions:

- **Typed C AST** (`CFile`, `CFunction`, `CStatement`, `CExpr`) — each `CFile` carries an immutable `bank` field; no mutable `currentBank` state, eliminating bank-leak bugs.
- **Visitors produce AST fragments, not strings.** `CEmitter` is the single point of text serialization, enabling source-map collection.
- **Header prototypes are auto-extracted** from built `CFile` function lists via `CFunction.toPrototype()` — every generated function gets a matching `BANKED` prototype in `game.h`.
- **Signed/unsigned literal discipline** — `CLiteral` (unsigned-context, emits `u` suffix) vs `CIntLiteral` (signed-comparison RHS, bare) prevents C integer-promotion bugs; see `gbkt-backend-gbdk/CLAUDE.md`.

Output files: `main.c` (bank 0: globals, helpers, `main()`), `bank1.c` (scene functions), `game.h` (extern declarations + `BANKED` prototypes), `zone_bankN.c` (tilemap data).

## Extending the Framework

### Adding a new IR node

1. Add a data class implementing `Expr` (in `gbkt-ir/.../Expr.kt`) or `ScriptOp` (in `gbkt-ir/.../ScriptOp.kt`).
2. Add a `visit*` method to the matching visitor interface (`ExprVisitorI` / `ScriptOpVisitorI`). The compiler now flags every visitor implementation until the node is handled.
3. Implement the visit method in the backend visitors (`gbkt-backend-gbdk/.../codegen/visitor/`) to produce C AST nodes.
4. If the node round-trips through JSON, add `serialize*`/`deserialize*` cases in `GameIRSerializer.kt`.
5. If the node is interpretable on the JVM, handle it in `ScriptOpInterpreter` (`gbkt-core/.../test/`) so `SimulationContext` tests keep working.

### Adding a new DSL construct

1. Create a builder method in the appropriate `gbkt-lang` builder (`GameBuilder`, `SceneBuilder`, `ScriptBuilder`, or a domain builder).
2. Have it construct the IR node and register it with `GameBuilder` (definitions) or emit it via `ScriptBuilderContext.current` (script ops).
3. For `by`-delegate syntax (`val x by myThing { }`), follow the pattern in `CollectionBuilders.kt`: a `*Ref` value type, a `*Delegate` with `provideDelegate` for name inference, and a top-level factory function.
4. Names must come from property delegates or lambda parameters — never duplicated as String parameters (project rule).

### Adding a new genre system

Genre plugins (`gbkt-genre-*`) contribute domain types and `SystemIR` implementations without touching core modules. They are discovered via ServiceLoader and visited through `GenreSystemVisitor` (in `gbkt-backend-api`). Use `gbkt-genre-puzzle` as the smallest reference.

### Adding a new backend

New target platforms (GBA, NES, ...) are sibling modules to `gbkt-backend-gbdk`:

```kotlin
class GBABackend : CodegenBackend {
    override val name = "gba"
    override val targetProfile = GBAProfile  // 240x160, 32KB IWRAM, etc.

    override fun validate(game: Game) = // Check GBA constraints
    override fun generate(game: Game) = // Generate libtonc/libgba C code
}
```

Backends are discovered via ServiceLoader; users select the target in their build:

```kotlin
gbkt {
    target("gba")  // Uses gbkt-backend-gba
}
```

### Backend Responsibilities

Backends are responsible for:
1. **validate()** — check that a game fits target constraints (sprite limits, memory, etc.)
2. **generate()** — produce platform-specific source code (C for GBDK)

Backends are **not** responsible for **compilation** — invoking external toolchains (lcc, devkitPro) belongs in the Gradle plugin or CLI. This keeps backends pure Kotlin libraries while compilation has access to toolchain paths, caching, and exec operations.

## Architectural Decisions

### Design Principles

1. **DSL Ergonomics Over Code Metrics** — the DSL is the user-facing API; clean DSL syntax takes priority over internal code metrics.
2. **IR as Boundary** — IR nodes are the clean separation between DSL and codegen; both sides may be complex internally.
3. **Domain-Driven Modeling** — RPG types model the problem domain (stats, abilities, effects); long parameter lists reflect domain complexity, not poor design.
4. **Generated Code is Different** — code generators produce output for machines; human readability of generated C matters less than correctness.

### Why These Detekt Exclusions?

The `detekt.yml` excludes certain rules for specific packages — deliberate architectural decisions:

| Package | Exclusion | Rationale |
|---------|-----------|-----------|
| `**/codegen/**` | LongMethod, TooManyFunctions | C code generation inherently produces large methods; each IR node maps to C output |
| `**/ir/**` | TooManyFunctions | `ExpressionWrapper.kt` has 60+ operator overloads for DSL ergonomics (`playerX + 5` syntax) |
| `**/dsl/**` | UnusedParameter | Receiver pattern intentionally has "unused" `this`; DSL functions like `whenever {}` need the receiver for scoping |
| `**/rpg/**`, `**/entity/**` | LongParameterList | Domain models (Character, Monster, Battle) require comprehensive fields |

### Globally Disabled Rules

| Rule | Rationale |
|------|-----------|
| `MagicNumber` | Game dev uses many constants (screen dimensions, sprite sizes, frame counts), well-documented in context |
| `UnusedPrivateMember` | DSL optional-properties pattern — properties may be set but never read in Kotlin (used in IR/codegen) |
