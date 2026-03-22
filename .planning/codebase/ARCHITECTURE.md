# Architecture

**Analysis Date:** 2026-02-17

## Pattern Overview

**Overall:** Three-layer compilation pipeline with platform-agnostic DSL frontend and pluggable backend code generation.

**Key Characteristics:**
- **Kotlin DSL → Intermediate Representation (IR) → Code Generation** pipeline
- Sealed interface IR nodes enable exhaustive pattern matching and safe transformations
- Recording context (thread-local) captures Kotlin operations as IR during DSL execution
- Backend interface allows swappable code generators for different platforms
- Multi-module architecture separates library code from build tooling

## Layers

**DSL Layer:**
- Purpose: Provide ergonomic Kotlin syntax for defining games
- Location: `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/`, `gbkt-core/src/main/kotlin/io/github/gbkt/core/builder/`
- Contains: Recording context, logic blocks, builders, conditionals, loops
- Depends on: IR types (to emit IR nodes)
- Used by: Game authors writing game definitions

**IR Layer (Intermediate Representation):**
- Purpose: Platform-agnostic game representation; bridge between DSL and code generation
- Location: `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/`
- Contains: 35 IR files defining 30+ sealed interface hierarchies for statements and expressions, variable system, expression wrappers with operator overloads
- Depends on: Nothing (no dependencies, fully self-contained)
- Used by: Codegen backends to transform IR → C code

**Codegen Layer:**
- Purpose: Transform IR to platform-specific code (C for GBDK)
- Location: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/`
- Contains: Extension functions organized by domain (core, graphics, rpg, world, ui, combat, features, data)
- Depends on: `gbkt-core` (for Game and IR types)
- Used by: Build system and CLI to generate source code

**Backend API Layer:**
- Purpose: Define contract for code generation backends
- Location: `gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/`
- Contains: `CodegenBackend` interface, validation and generation result types
- Depends on: `gbkt-core` (for Game, TargetProfile)
- Used by: GBDK backend and any future platform backends

## Data Flow

**Game Definition → IR Emission:**

1. `gbGame("MyGame") { ... }` creates `GameBuilder` in recording context (via `GameScopeContext`)
2. Property delegates (e.g., `u8Var("x")`) register with builder and current scope
3. Operations inside scene blocks (e.g., `playerX += 2`) check `RecordingContext.isRecording`
4. If recording: operators on variables emit `IRAssign` / `IRBinary` statements via `RecordingContext.emit()`
5. Scene builder's `recordBlock()` collects emitted statements into scene lifecycle lists

**Scene Lifecycle Recording:**

```
SceneBuilder.enter { ... }          → recordBlock() → StatementRecorder → emit IR → Scene.onEnter
SceneBuilder.every.frame { ... }    → recordBlock() → StatementRecorder → emit IR → Scene.onFrame
SceneBuilder.exit { ... }           → recordBlock() → StatementRecorder → emit IR → Scene.onExit
```

**IR → C Code Generation:**

1. `GBDKBackend.generate(game)` instantiates `GBDKCodeGenerator(game)`
2. `generateMultiFile()` calls domain-specific generator functions via extension functions
3. Each generator (e.g., `generateVariables()`, `generateSceneFunctions()`) pattern-matches on IR nodes
4. IR expressions compiled to C via `ExpressionCodegen` with constant folding
5. Multi-bank aware: `setBank(N)` / `returnToHome()` manage bank pragmas
6. Output: `Map<String, String>` of filename → C source code

**State Management:**

- Game state: Immutable `Game` data class (output of builder)
- Runtime state during generation: `GBDKCodeGenerator` maintains `currentBank` and `sourceMapBuilder`
- Variable registration: Variables sync to `GameServices` for DI access during testing

## Key Abstractions

**Recording Context:**
- Purpose: Thread-local mechanism for capturing Kotlin operations as IR
- Examples: `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/RecordingContext.kt`
- Pattern: `RecordingContext.record(recorder) { block() }` sets thread-local, executes block, unsets
- Result: Enables operators like `playerX += 2` to emit IR instead of executing immediately

**Sealed Interfaces for IR:**
- Purpose: Type-safe, exhaustive pattern matching in codegen
- Examples: `sealed interface IRStatement`, `sealed interface IRExpression`
- Pattern: All statement types (IRAssign, IRIf, IRWhile, etc.) extend sealed interface
- Result: Code generator can match without `else` branch — compiler verifies all cases covered

**Expression Wrapper (Expr):**
- Purpose: Operator overloading for DSL ergonomics
- Examples: `Expr` class with 60+ operator overloads for `+`, `-`, `*`, `/`, `and`, `or`, etc.
- Pattern: Each operator creates IR node (e.g., `playerX + 2` → `Expr(IRBinary(...))`)
- Result: Natural syntax like `damage set (isCritical.then(20, 10))`

**Logic Blocks:**
- Purpose: Reusable recorded code with parameter substitution
- Examples: `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/LogicBlock.kt`
- Pattern: Record IR with placeholder variables (`__param_amount_0`), then `deepCopy()` with substitutions
- Result: Patterns like `logicBlock<Expr>("addScore", "amount") { amount -> score += amount }` reusable with different values

**Game Builder:**
- Purpose: Collect all game elements (sprites, scenes, variables, entities) into immutable Game
- Examples: `gbkt-core/src/main/kotlin/io/github/gbkt/core/builder/GameBuilder.kt`
- Pattern: Mutable builder accumulates registrations, then `build()` returns immutable `Game`
- Result: Type-safe access to all game elements during codegen

**Backend Interface:**
- Purpose: Swappable code generator contract
- Examples: `gbkt-backend-api/src/main/kotlin/io/github/gbkt/backend/api/CodegenBackend.kt`
- Pattern: Two methods: `validate(game)` and `generate(game)`
- Result: Future backends (GBA, NES) can implement same interface without modifying gbkt-core

## Entry Points

**DSL Entry Point:**
- Location: `gbkt-core/src/main/kotlin/io/github/gbkt/core/Game.kt`
- Function: `fun gbGame(name: String, init: GameBuilder.() -> Unit): Game`
- Triggers: Called by game authors in their main Kotlin file
- Responsibilities: Sets up `GameScopeContext`, creates builder, invokes init block, returns Game

**Code Generation Entry Point:**
- Location: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/GBDKBackend.kt`
- Method: `override fun generate(game: Game, options: GenerationOptions): GenerationResult`
- Triggers: Called by Gradle plugin or CLI after DSL execution
- Responsibilities: Instantiates `GBDKCodeGenerator`, calls `generateMultiFile()`, returns C code

**Gradle Plugin Entry Point:**
- Location: `gbkt-gradle-plugin/src/main/kotlin/io/github/gbkt/gradle/` (not explored here)
- Triggers: `./gradlew buildRom` or `./gradlew generateC`
- Responsibilities: Loads game class, executes DSL, invokes backend, compiles with GBDK

## Error Handling

**Strategy:** Three-tier validation before code generation.

**Game-Level Validation:**
- Location: `gbkt-core/src/main/kotlin/io/github/gbkt/core/GameValidator.kt`
- Checks: Resource limits (sprite count, tile count), field references, scene existence
- Returns: `ValidationResult` with errors and warnings

**Domain-Specific Validation:**
- Constraints: `gbkt-core/src/main/kotlin/io/github/gbkt/core/constraints/`
- Examples: `TargetProfile` defines max sprites, memory, banks; backends check game fits profile
- Pattern: Each constraint module (e.g., `SpriteConstraints`, `BankConstraints`) validates specific domain

**Code Generation Error Handling:**
- Location: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/GBDKBackend.kt` lines 37-56
- Catches: Runtime exceptions during code generation
- Returns: `GenerationResult.failed(message)` with error description
- Example: Banking overflow, undefined symbol references

**Patterns:**
- Variables checked against declared type (u8, u16, etc.) during assignment
- Arrays bounds validated during code generation
- Scene references type-safe via `SceneRef` (compiler prevents string typos)

## Cross-Cutting Concerns

**Logging:**
- Debug output via `debug("message")` in `FrameScope`
- Generates `printf()` statements in C code
- Optional: `debugGraphics` setting controls verbosity of graphics initialization

**Validation:**
- Upfront: `GameValidator.validate()` checks complete game before codegen
- During codegen: Pattern matching on IR ensures type correctness
- Asset validation: `AssetRef<T>` is type-safe, prevents missing asset references

**Authentication:** Not applicable (Game Boy offline device)

**Source Mapping:**
- Mechanism: `SourceLocation` captures Kotlin stack location during DSL execution
- Purpose: Link generated C code back to original Kotlin source
- Usage: Codegen emits comments with source line numbers
- File: `gbkt-core/src/main/kotlin/io/github/gbkt/core/SourceLocation.kt`

**Serialization:**
- Save system: `gbkt-core/src/main/kotlin/io/github/gbkt/core/features/SaveGame.kt`
- Codegen: `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/features/SaveCodegen.kt`
- Pattern: Fields marked `save()` are serialized to cartridge SRAM

**Threading:**
- Recording context uses `ThreadLocal<StatementRecorder?>` for thread safety
- Codegen is single-threaded (but thread-safe for parallel game definitions)
- Runtime (generated C) is single-threaded (Game Boy has one CPU)

---

*Architecture analysis: 2026-02-17*
