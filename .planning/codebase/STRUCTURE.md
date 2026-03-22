# Codebase Structure

**Analysis Date:** 2026-02-17

## Directory Layout

```
gbkt/
├── gbkt-core/                      # Platform-agnostic DSL, IR, game constructs (231 .kt files)
│   ├── src/main/kotlin/io/github/gbkt/core/
│   │   ├── ir/                     # Intermediate Representation nodes (35 files)
│   │   ├── dsl/                    # Recording context, builders, conditionals, loops (8 files)
│   │   ├── builder/                # GameBuilder and DSL entry points (3 files)
│   │   ├── scene/                  # Scene lifecycle, transitions, timing (4 files)
│   │   ├── entity/                 # Entity system, components, pools (10+ files)
│   │   ├── graphics/               # Sprites, animations, camera, tilemaps (15+ files)
│   │   ├── rpg/                    # Character, item, ability, combat, leveling (20+ files)
│   │   ├── world/                  # Floors, encounters, zones, flags, exploration (10+ files)
│   │   ├── input/                  # D-pad, buttons, input buffering (5 files)
│   │   ├── ui/                     # Menus, dialogs, status bars, windows (8 files)
│   │   ├── collision/              # Collision detection system (5 files)
│   │   ├── combat/                 # Battle engine, turn order, state machines (8 files)
│   │   ├── flow/                   # Game flow, pause, save, navigation (4 files)
│   │   ├── movement/               # Movement controller, physics (4 files)
│   │   ├── exploration/            # Dungeon crawling, gauges, keys (6 files)
│   │   ├── assets/                 # Type-safe asset references (3 files)
│   │   ├── constraints/            # Validation rules for target platforms (4 files)
│   │   ├── services/               # Dependency injection containers (3 files)
│   │   ├── validation/             # Game validation, array bounds checking (3 files)
│   │   ├── optimization/           # Asset analysis, optimization suggestions (2 files)
│   │   ├── test/                   # Test utilities, simulation context (4 files)
│   │   ├── types/                  # Type system and domain types (8 files)
│   │   ├── model/                  # Model layer (Game, Scene, Entity data classes)
│   │   └── Game.kt                 # Entry point: fun gbGame()
│   │
│   └── src/test/kotlin/            # Unit tests (35+ test files)
│
├── gbkt-backend-api/               # Backend interface contract
│   ├── src/main/kotlin/io/github/gbkt/backend/api/
│   │   └── CodegenBackend.kt       # Interface: validate() and generate()
│
├── gbkt-backend-gbdk/              # GBDK (Game Boy/GBC) code generator (78 .kt files)
│   ├── src/main/kotlin/io/github/gbkt/backend/gbdk/
│   │   ├── GBDKBackend.kt          # Backend implementation
│   │   ├── profiles/               # Target profiles (Game Boy, GBC hardware specs)
│   │   └── codegen/                # Code generation extension functions
│   │       ├── core/               # Variables, scenes, pools, expressions, statements (7 files)
│   │       ├── graphics/           # Sprites, animations, camera, transitions (6 files)
│   │       ├── rpg/                # Character, abilities, items, combat systems (15+ files)
│   │       ├── world/              # Zones, encounters, exploration, flags, map objects (10+ files)
│   │       ├── ui/                 # Dialogs, menus, status bars, cutscenes, windows (5 files)
│   │       ├── combat/             # Battle engine, turn order (1 file)
│   │       ├── features/           # Tweens, movement, physics, save, mixer, links (8 files)
│   │       └── data/               # String table, balance tables, easing lookups (3 files)
│
├── gbkt-gradle-plugin/             # Gradle build integration
│   ├── src/main/kotlin/io/github/gbkt/gradle/
│   │   ├── GbktPlugin.kt           # Main plugin class
│   │   ├── GbktExtension.kt        # build.gradle.kts config: gbkt { ... }
│   │   ├── tasks/                  # Gradle task implementations
│   │   └── ...                     # Compiler integration, GBDK detection
│
├── gbkt-cli/                       # Command-line tool (alternative to Gradle plugin)
│
├── gbkt-intellij-plugin/           # IDE support for .gbkt files and DSL
│
├── gbkt-examples/                  # Example games
│   ├── pong/                       # Minimal example (~ 200 lines Kotlin)
│   ├── breakout/                   # Intermediate example with paddle, bricks
│   ├── explorer/                   # Advanced exploration dungeon crawler
│   ├── rpg-lite/                   # RPG system example
│   └── dungeon/                    # Multi-floor dungeon example
│
├── LabyrinthOfTheDragon/           # Large reference RPG game (complex)
├── LabyrinthOfTheDragon-port/      # v1 launch port with cleaned architecture
│
└── context/                        # Documentation index
    ├── ARCHITECTURE.md             # IR nodes, data flow, module organization
    ├── DSL_REFERENCE.md            # Complete syntax reference
    ├── DEVELOPER_EXPERIENCE.md     # Extending framework, adding IR nodes
    ├── TOOLING.md                  # Gradle plugin, asset pipeline, IDE
    ├── LOCALIZATION.md             # GNU gettext integration, .po files
    └── ROADMAP.md                  # Feature status, planned work
```

## Directory Purposes

**gbkt-core:**
- Purpose: Complete, platform-agnostic game DSL and IR system
- Contains: All game constructs (variables, entities, scenes, RPG systems, graphics, input)
- Key files: `Game.kt`, `builder/GameBuilder.kt`, `ir/CoreIR.kt`, `dsl/RecordingContext.kt`
- Exports: `Game` data class, `GameBuilder`, IR types, DSL functions like `gbGame()`, `scene()`, `entity()`

**gbkt-backend-api:**
- Purpose: Interface contract that backends implement
- Contains: `CodegenBackend` interface, `ValidationResult`, `GenerationResult` types
- Key files: `CodegenBackend.kt`
- Exports: Backend contract for ServiceLoader discovery

**gbkt-backend-gbdk:**
- Purpose: GBDK-specific code generation (Game Boy / Game Boy Color C code)
- Contains: `GBDKCodeGenerator`, domain-specific codegen extension functions
- Key files: `GBDKBackend.kt`, `codegen/GBDKCodeGenerator.kt`, `codegen/core/StatementCodegen.kt`, `codegen/core/ExpressionCodegen.kt`
- Exports: Generated C code in multi-file format (split by bank)

**gbkt-gradle-plugin:**
- Purpose: Build system integration — `./gradlew buildRom` tasks
- Contains: Gradle task implementations, GBDK toolchain detection, project configuration
- Key files: `GbktPlugin.kt`, `GbktExtension.kt`, `tasks/`
- Exports: Tasks: `generateC`, `buildRom`, `runEmulator`

**gbkt-cli:**
- Purpose: Command-line tool for headless compilation (alternative to Gradle)
- Contains: CLI argument parsing, game file loading, backend invocation
- Exports: `gbkt-cli` executable JAR

**gbkt-intellij-plugin:**
- Purpose: IDE support for `.gbkt` DSL files
- Contains: Syntax highlighting, code completion, error markers
- Exports: IntelliJ plugin for Kotlin IDE

**gbkt-examples:**
- Purpose: Reference implementations demonstrating DSL patterns
- Examples:
  - `pong/`: Minimal game with sprites, collision, scenes (~200 lines)
  - `breakout/`: Brick breaker with entity pools
  - `explorer/`: Dungeon exploration, tilemap navigation
  - `rpg-lite/`: RPG systems (stats, abilities, items)
  - `dungeon/`: Multi-floor world system

## Key File Locations

**Entry Points:**

| File | Purpose |
|------|---------|
| `gbkt-core/src/main/kotlin/io/github/gbkt/core/Game.kt` | `fun gbGame(name, init)` — DSL entry |
| `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/GBDKBackend.kt` | `override fun generate()` — Codegen entry |
| `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/GbktPlugin.kt` | Gradle plugin initialization |

**Configuration:**

| File | Purpose |
|------|---------|
| `build.gradle.kts` (root) | Multi-module Gradle build configuration |
| `settings.gradle.kts` | Module inclusion and plugin management |
| `gbkt-core/build.gradle.kts` | Core module dependencies |
| `gbkt-backend-gbdk/build.gradle.kts` | GBDK backend dependencies |
| `detekt.yml` | Code quality rules and exclusions |

**Core Logic:**

| File | Purpose |
|------|---------|
| `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/CoreIR.kt` | Base IR statements (IRAssign, IRIf, IRWhile, etc.) |
| `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/ExpressionWrapper.kt` | Expr class with 60+ operator overloads |
| `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/Variables.kt` | GBVar, property delegates (u8Var, u16Var, arrays) |
| `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/RecordingContext.kt` | Thread-local recording mechanism |
| `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/LogicBlock.kt` | Reusable recorded code blocks with parameters |
| `gbkt-core/src/main/kotlin/io/github/gbkt/core/builder/GameBuilder.kt` | Game element collection and registration |

**Testing:**

| File | Purpose |
|------|---------|
| `gbkt-core/src/test/kotlin/io/github/gbkt/core/LogicBlockTest.kt` | Logic block recording and expansion tests |
| `gbkt-core/src/test/kotlin/io/github/gbkt/core/InputTest.kt` | Input system tests |
| `gbkt-core/src/test/kotlin/io/github/gbkt/core/TransitionTest.kt` | Scene transition tests |
| `gbkt-core/src/test/kotlin/io/github/gbkt/core/Generators.kt` | Property-based test generators (Kotest) |

## Naming Conventions

**Files:**

| Pattern | Example | Use |
|---------|---------|-----|
| `{Feature}IR.kt` | `BattleIR.kt`, `DialogIR.kt`, `AudioIR.kt` | IR node types for a domain |
| `{Codegen}Codegen.kt` | `StatementCodegen.kt`, `ExpressionCodegen.kt` | Extension functions for code generation |
| `{Feature}System.kt` | `BattleEngineSystem.kt`, `CollisionSystem.kt` | Integrated system implementations |
| `{Noun}Definition.kt` | `ItemDefinition.kt`, `MenuDefinition.kt` | Immutable configuration data |
| `{Noun}Builder.kt` | `EntityBuilder.kt`, `SceneBuilder.kt` | Mutable builders for configuration |
| `Test.kt` suffix | `LogicBlockTest.kt`, `InputTest.kt` | Unit tests |

**Directories:**

| Pattern | Examples | Purpose |
|---------|----------|---------|
| `ir/` | IR node definitions (sealed interfaces and data classes) | Platform-agnostic intermediate representation |
| `dsl/` | Recording context, conditionals, loops, builders | User-facing DSL syntax |
| `codegen/` | Statement, expression, variable generation | Transform IR to C code |
| `{domain}/` | `graphics/`, `rpg/`, `world/`, `ui/` | Feature areas in core and codegen |
| `{category}/{subcategory}/` | `codegen/rpg/`, `codegen/graphics/` | Organize codegen by domain |

**Functions & Classes:**

| Pattern | Example | Use |
|---------|---------|-----|
| `generate{Feature}()` | `generateVariables()`, `generateSceneFunctions()` | Codegen extension functions |
| `{primitive}Var(...)` | `u8Var("x")`, `u16Var("score")` | Variable type delegation |
| `{primitive}Array(...)` | `u8Array(10)`, `u16Array(5)` | Array type delegation |
| `IR{Statement}` | `IRAssign`, `IRIf`, `IRWhile` | IR node types (sealed) |
| `{noun}()` | `whenever()`, `scene()`, `entity()` | DSL builder functions |
| `{noun}Builder` | `SceneBuilder`, `EntityBuilder` | Builder classes for DSL |

## Where to Add New Code

**New Feature (e.g., new DSL construct):**
1. Define IR node(s) in `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/{Feature}IR.kt`
2. Add DSL builder in appropriate scope (`SceneBuilder`, `EntityBuilder`, etc.)
3. Add codegen extension function in `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/{domain}/{Feature}Codegen.kt`
4. Add tests in `gbkt-core/src/test/kotlin/io/github/gbkt/core/{Feature}Test.kt`

**New Component/Module (e.g., new entity type):**
- Implementation: `gbkt-core/src/main/kotlin/io/github/gbkt/core/{domain}/{Component}.kt`
- Builder: Same module or `{Component}Builder.kt`
- Tests: `gbkt-core/src/test/kotlin/io/github/gbkt/core/{ComponentName}Test.kt`

**Utilities (helpers, extensions):**
- Shared extension functions: `gbkt-core/src/main/kotlin/io/github/gbkt/core/Extensions.kt`
- Domain-specific utilities: `gbkt-core/src/main/kotlin/io/github/gbkt/core/{domain}/Utilities.kt`
- Test utilities: `gbkt-core/src/test/kotlin/io/github/gbkt/core/Generators.kt`

**New Backend (e.g., for GBA):**
1. Create module: `gbkt-backend-gba/`
2. Implement: `class GBABackend : CodegenBackend` in `GBABackend.kt`
3. Implement `validate()` and `generate()` methods
4. Add codegen functions: `gbkt-backend-gba/src/main/kotlin/.../codegen/`
5. Backend discovered via ServiceLoader at runtime

## Special Directories

**gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/:**
- Purpose: All IR node definitions (sealed hierarchies)
- Generated: No (hand-written)
- Committed: Yes
- Note: 35 files, ~6000 lines; cannot be split across modules due to Kotlin sealed interface constraint
- Key files: `CoreIR.kt` (base statements), `ExpressionWrapper.kt` (operator overloads), `Variables.kt` (property delegates)

**gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/:**
- Purpose: Code generation logic organized by feature domain
- Generated: No (hand-written extension functions)
- Committed: Yes
- Key files: `core/StatementCodegen.kt`, `core/ExpressionCodegen.kt` (transform IR to C)
- Note: Uses multi-file (by bank) output for GBDK #pragma bank support

**gbkt-examples/:**
- Purpose: Reference implementations and test cases
- Generated: No (hand-written by developers)
- Committed: Yes
- Pattern: Each example is a standalone Gradle module with own `build.gradle.kts`
- Usage: Demonstrate patterns, verify DSL works end-to-end

**LabyrinthOfTheDragon-port/:**
- Purpose: Large reference game (v1 launch cleanup)
- Generated: No
- Committed: Yes
- Size: Complex multi-floor RPG with exploration, combat, NPCs
- Role: Used to identify architectural gaps, test edge cases

**context/:**
- Purpose: Design documentation index
- Generated: No (hand-written)
- Committed: Yes
- Key files: ARCHITECTURE.md, DSL_REFERENCE.md, DEVELOPER_EXPERIENCE.md
- Role: Reference for developers extending the framework

---

*Structure analysis: 2026-02-17*
