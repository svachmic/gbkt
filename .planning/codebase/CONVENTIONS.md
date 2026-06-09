# Coding Conventions

**Analysis Date:** 2026-05-27

## Naming Patterns

**Files:**
- PascalCase, one primary public declaration per file (`GameBuilder.kt`, `ScriptOpVisitor.kt`)
- Filename matches the primary public type. Domain-grouped IR files use a descriptive suffix (`CameraIR.kt`, `SaveSystemIR.kt`, `ActorPoolIR.kt`)
- Visitor implementations end in `Visitor.kt` (e.g. `ScriptOpVisitor.kt`, `ExprVisitor.kt`); visitor interfaces end in `VisitorI.kt` (e.g. `ScriptOpVisitorI.kt`)
- Codegen helpers end in `Codegen.kt` (e.g. `GBDKCollectionCodegen.kt`, `WindowTextCodegen.kt`)
- Builder classes end in `Builder.kt` (`SceneBuilder.kt`, `ActorBuilder.kt`); the corresponding delegate class ends in `Delegate.kt` (e.g. `ActorDelegate`)
- Tests end in `Test.kt`. Suffix encodes the testing tier (see `.planning/codebase/TESTING.md`):
  - `*IRTest.kt`, `*SimTest.kt`, `*GameTest.kt` for JVM-only logic / IR validation
  - `*EmissionTest.kt` for golden-shape codegen tests
  - `*EmulatorTest.kt`, `*StepAgentTest.kt`, `*IntegrationTest.kt` for ROM-loading integration tests
  - `*UatTest.kt` for `UatRunner`-driven checkpoint tests
- Directories: kebab-case at the module level (`gbkt-lang`, `gbkt-backend-gbdk`, `gbkt-genre-rpg`); lowercase single-word packages within (`ir`, `dsl`, `codegen`, `visitor`)

**Classes / Types:**
- PascalCase (`GameBuilder`, `AssignableVar`, `ActorPropertyRef`, `StepAgent`, `CFunction`)
- Detekt rule: `naming.ClassNaming` with pattern `[A-Z][a-zA-Z0-9]*` (see `detekt.yml`)
- IR data classes match their domain (`SceneIR`, `ActorIR`, `BinaryExpr`, `Assign`)
- C AST types are prefixed `C` (`CFile`, `CFunction`, `CExpr`, `CLiteral`, `CIntLiteral`)
- Visitor interface suffix is `I` (`ExprVisitorI`, `ScriptOpVisitorI`, `SystemIRVisitorI`) — the implementing object/class drops the `I` (`ExprVisitor`, `ScriptOpVisitor`)
- Codegen visitor naming is consistent across `gbkt-backend-gbdk/.../codegen/visitor/`: `ScriptOpVisitor`, `ExprVisitor`, `ActorVisitor`, `SceneVisitor`, `DialogVisitor`, `MenuVisitor`, `HudVisitor`, `InventoryVisitor`, `CombatVisitor`, `RpgVisitor`, `SoundVisitor`, `CollisionVisitor`, `GBDKSystemVisitor`, `MetaspriteVisitor`

**Functions / properties:**
- camelCase (`registerActor`, `bootToScene`, `assertScene`, `readTypedValue`)
- Detekt `naming.FunctionNaming` pattern `[a-z][a-zA-Z0-9]*` — exception allowed for short type-name functions like `u8()`, `i16()` (DSL factories)
- DSL operators use the canonical Kotlin operator names (`plus`, `minus`, `plusAssign`, `inc`, `dec`, `times`, `div`, `rem`) and infix comparison verbs (`isAbove`, `isBelow`, `isAtLeast`, `isAtMost`, `isEqualTo`, `isNotEqualTo`, `logicalAnd`, `logicalOr`, `and`, `or`, `xor`, `shl`, `shr`)
- Test method names use backticked-spaces: ``fun `title screen shows PONG`()`` (see `gbkt-test/.../GbktTestExtension.kt:41`)

**Variables:**
- Detekt `naming.VariableNaming`: `[a-z][A-Za-z0-9]*` (private may start with `_`)
- Test fixtures may use SCREAMING_SNAKE_CASE for tile/coord constants (`TILE_WALL`, `RACER_MAP_W`) — exempted in `detekt.yml` under `**/test/**`
- Top-level constants: `[A-Z][_A-Z0-9]*` (`TITLE_SCENE_PATTERNS` in `gbkt-test/.../GbktTestRecipes.kt:42`)
- DSL property names ARE the runtime identifier — `var score by u8Var(0)` registers a variable named `"score"` via `provideDelegate`. See "No Magic Strings" below.

**Packages:**
- Lowercase, dot-separated (`io.github.gbkt.core.dsl`, `io.github.gbkt.backend.gbdk.codegen.visitor`)
- Detekt `naming.PackageNaming`: `[a-z]+(\.[a-z][A-Za-z0-9]*)*`

## Code Style

**Formatting:**
- ktfmt 0.62, Kotlin-lang style (configured via Spotless in `build.gradle.kts:88`)
- License header is auto-applied by Spotless. MPL 2.0 for all modules; Apache 2.0 only for `gbkt-intellij-plugin` (per `build.gradle.kts:46-78`)
- Trailing whitespace trimmed, file ends with newline
- Run: `./gradlew spotlessApply` to format, `./gradlew spotlessCheck` to verify

**Linting:**
- Detekt with config at `detekt.yml`, baseline at each subproject's `detekt-baseline.xml`
- `buildUponDefaultConfig = true`, `parallel = true`
- `style.MaxLineLength = 120` (excludes test files, codegen, raw strings, KDoc comments)
- `style.WildcardImport` active. Excludes: `**/test/**`, `**/codegen/**`. Allowed wildcard imports anywhere: `java.util.*`, `kotlinx.coroutines.*`, `kotlin.test.*`, `io.github.gbkt.core.dsl.*` (DSL operator extensions)
- Run: `./gradlew detekt` per module

## Detekt Exclusions (with rationale)

The `detekt.yml` rules are deliberately relaxed for specific packages. Future code MUST stay within the same exemptions — don't widen them:

| Rule | Globally Disabled? | Per-package exemptions | Rationale |
|------|--------------------|------------------------|-----------|
| `style.MagicNumber` | YES (active: false) | n/a | Game dev uses many constants (screen dimensions 160x144, sprite sizes 8x8/8x16, frame counts, tile coords, color values). Inlining is more readable than naming each one. |
| `style.UnusedPrivateMember` | YES (active: false) | n/a | DSL receivers trigger false positives — the `this` receiver of a DSL lambda is required for scoping even when not explicitly used. |
| `style.UnusedPrivateProperty` | YES (active: false) | n/a | DSL builders may have unused private properties needed for the API surface. |
| `style.ForbiddenComment` | YES (active: false) | n/a | TODO/FIXME comments are allowed. |
| `complexity.LongMethod` (threshold 80) | NO | `**/codegen/**`, `**/test/**`, `**/ir/**`, `**/validation/**`, `**/collision/**`, `**/CodeGenerator.kt`, `**/dsl/**`, `**/mcp/**` | C codegen methods produce large output blocks; IR transformations are inherently long; DSL `GameBuilder.build()` accumulates IR per feature; MCP tool dispatch is large by design. |
| `complexity.LongParameterList` (fn 6, ctor 7) | NO | `**/entity/**`, `**/rpg/**`, `**/ui/**`, `**/test/**`, `**/world/**`, `**/exploration/**`, `**/builder/**`, `**/collision/**`, `**/codegen/**`, `**/validation/**`, `**/combat/**`, `**/movement/**` | Domain models (Character, Monster, Battle, Floor, Camera) genuinely have many fields; codegen helpers take many positional inputs (position, hitbox, expressions). |
| `complexity.TooManyFunctions` | NO | `**/codegen/**`, `**/ir/**`, `**/test/**`, `**/builder/**`, `**/dsl/**`, `**/scene/**`, `**/graphics/**`, `**/world/**`, `**/CodeGenerator.kt`, `**/analysis/passes/**`, `**/mcp/**`, `**/emulator/agent/**` | `ExpressionWrapper`/`VariableBuilders`/`ActorBuilder`/`ExprBuilder` have 60+ operator overloads each (see "Operator Overloading" below); MCP server has one handler method per exposed tool. |
| `complexity.LargeClass` (600) | NO | `**/codegen/**`, `**/test/**` | Codegen classes may exceed for complex systems. |
| `complexity.CyclomaticComplexMethod` (15) | NO | `**/codegen/**`, `**/ir/**`, `**/dsl/**`, `**/optimization/**`, `**/validation/**`, `**/analysis/passes/**`, `**/collision/**`, `**/intellij/completion/**`, `**/mcp/**` | Visitor `when` dispatch over all IR types is inherently complex; cross-subsystem polymorphic walks span many cases. |
| `complexity.NestedBlockDepth` (5) | NO | `**/codegen/**`, `**/validation/**`, `**/collision/**` | Nested control flow is structural for C generation. |
| `style.UnusedParameter` | NO | `**/codegen/**`, `**/dsl/**`, `**/rpg/**`, `**/flow/**`, `**/ui/**`, `**/scene/**` | Callback parameters needed for interface compliance or future expansion. |
| `style.ReturnCount` (4) | NO | `**/intellij/**`, `**/codegen/**`, `**/mcp/**` | Codegen visitors use fail-fast pattern; MCP tool handlers return early on invalid input. |
| `style.WildcardImport` | NO | `**/test/**`, `**/codegen/**` (`io.github.gbkt.core.dsl.*` always allowed for DSL operator imports in game files) | Game DSL files import the operator extensions wholesale. |
| `exceptions.TooGenericExceptionCaught` | NO | `**/intellij/codegen/**`, `**/mcp/**`, `**/emulator/**` | Caught broadly only at IO / protocol boundaries (savestate IO, metadata parse, MCP JSON dispatch). |
| `performance.SpreadOperator` | NO | `**/intellij/inspections/**`, `**/test/**` | IntelliJ API requires vararg spread; test code prioritises readability. |

**Implication for new code:** outside the exempted packages above, methods must stay under 80 lines, ≤15 cyclomatic complexity, ≤6 function parameters, ≤25 functions per file. The exemptions are NOT carte blanche — they exist because the alternative (over-decomposed visitor dispatch) would be less readable.

## Import Organization

**Order:**
1. Kotlin stdlib (`kotlin.properties`, `kotlin.reflect`)
2. Third-party (`java.io`, `java.util`, `org.junit.jupiter`, `org.json`)
3. Project (`io.github.gbkt.*`)

Blank line between groups. Sort alphabetically within groups.

**No star imports** in main source. CONTRIBUTING.md §8 enforces explicit imports. Exceptions allowed per detekt config: tests (`kotlin.test.*`), codegen wildcard imports of the C AST, and `io.github.gbkt.core.dsl.*` in game files (required to bring operator extensions into scope).

**Path aliases:** None — Kotlin uses fully-qualified imports, not bundler-style aliases.

## DSL Authoring Patterns

The DSL is the user-facing API. These patterns are non-negotiable for code that builds IR.

### 1. `@GbktDsl` marker (mandatory)

Every DSL builder class is annotated `@GbktDsl` (`gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/DslMarkers.kt:17`). This prevents implicit-receiver leakage between nested builders — the compiler rejects calls to outer-scope methods from inside a nested block.

```kotlin
@GbktDsl
class EntityBuilder(private val entityName: String) { /* ... */ }
```

### 2. Property-delegate name inference (No Magic Strings)

**Project Rule #1 (from `userMemory feedback_no_magic_strings.md`):** the DSL MUST reflect the name from the Kotlin property delegate or lambda parameter — never duplicate it as a `String` argument.

```kotlin
// CORRECT: name inferred from property via VarDelegate.provideDelegate
var score by u8Var(0)        // registers VariableDef("score", U8, 0)
var ballDx by i8Var(-1)      // registers VariableDef("ballDx", I8, -1)
val ball by actor { ... }    // registers ActorIR with id "ball"
val pause by scene { ... }   // registers SceneIR with id "pause"

// WRONG: name duplicated as a String
var score = variable("score", u8(0))   // banned — duplicates "score"
val ball = actor("ball") { ... }       // banned — duplicates "ball"
```

The mechanism: every delegate class implements `operator fun provideDelegate(thisRef, property: KProperty<*>)` and reads `property.name`. See:
- `VarDelegate.provideDelegate` at `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt:353`
- `ActorDelegate.provideDelegate` at `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt:1228`
- `ActorPropDelegate.provideDelegate` at `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt:1179`

When you add a new DSL construct, follow this pattern. There are still legacy `name: String` overloads (e.g. `actor("explicit")`) for cases that genuinely need a different identifier — these are the exception, not the default.

### 3. Thread-local recording contexts

Two thread-locals carry builder state without explicit receivers:

- `GameBuilderContext` (`VariableBuilders.kt:47`) — holds the active `GameBuilder` so variable/actor delegates can register themselves at `provideDelegate` time. Set by `GameBuilder` while executing the DSL lambda.
- `ScriptBuilderContext` (`gbkt-lang/.../ScriptBuilderContext.kt`) — holds the active `ScriptBuilder` so operator extensions on `AssignableVar`/`ActorPropertyRef`/`ArrayVar` can emit `ScriptOp` nodes into the enclosing `enter`/`frame`/`exit` block.

Both follow the same idiom:

```kotlin
fun <T> with(builder: GameBuilder, block: () -> T): T {
    val previous = holder.get()
    holder.set(builder)
    return try { block() } finally { holder.set(previous) }
}
```

Nested contexts are supported (the restore happens in `finally`).

### 4. Fluent builders return `this` via `apply`

```kotlin
fun position(x: Int, y: Int) = apply {
    this.x = x
    this.y = y
}
```

See `CONTRIBUTING.md` §3 — chainable configuration is the convention.

### 5. RecordingContext for capturing DSL blocks as IR

```kotlin
fun onSelect(block: MenuActionScope.() -> Unit) {
    val recorder = StatementRecorder()
    RecordingContext.record(recorder) {
        MenuActionScope().block()
    }
    onSelectStatements = recorder.statements
}
```

When a builder needs to capture a lambda's side-effects as IR (not values), use `RecordingContext.record()`. CONTRIBUTING.md §"Recording Context".

## Operator Overloading

The DSL aggressively uses operator overloads to make Kotlin syntax mirror imperative game code. Counts (verified via grep):

| File | `operator fun` / `infix fun` |
|------|-------------------------------|
| `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/VariableBuilders.kt` | 81 |
| `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ActorBuilder.kt` | 72 |
| `gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/ExprBuilder.kt` | 70 |

**Pattern: one function per operator × type combination.** The receiver is `AssignableVar`, `ActorPropertyRef`, or `Expr`. The right-hand side is `Int`, `Expr`, `AssignableVar`, or `ActorPropertyRef`. Three RHS types × ~7 operators × 3 receivers ≈ 60+ overloads each. Detekt `TooManyFunctions` is suppressed per-file via `@file:Suppress("TooManyFunctions")` with the rationale comment "Operator extensions require one function per operator/type combination" (see `VariableBuilders.kt:7-9`).

**Assignment operators emit into the active `ScriptBuilder`:**

```kotlin
// VariableBuilders.kt
infix fun AssignableVar.set(value: Expr) {
    ScriptBuilderContext.current?.assign(name, value, AssignOp.SET)
        ?: error("set() called outside a ScriptBuilder block")
}

operator fun AssignableVar.plusAssign(value: Expr) {
    ScriptBuilderContext.current?.assign(name, value, AssignOp.ADD)
        ?: error("+= called outside a ScriptBuilder block")
}
```

**Arithmetic operators return `Expr` (pure value):**

```kotlin
operator fun AssignableVar.plus(other: Int): Expr = toExpr() + other
operator fun AssignableVar.plus(other: AssignableVar): Expr = toExpr() + other.toExpr()
```

**Comparisons use English infix names** (`isAbove`, `isBelow`, `isAtLeast`, `isAtMost`, `isEqualTo`, `isNotEqualTo`) — not operator symbols — because Kotlin doesn't allow custom `<`/`>` operators on arbitrary types. This is why `whenever(score isAtLeast 100) { ... }` reads naturally.

**Logical operators:** `&&` and `||` cannot be overloaded in Kotlin. The DSL provides `logicalAnd` / `logicalOr` infix functions for combining `Expr` conditions.

**Bitwise operators:** `and`, `or`, `xor`, `shl`, `shr`, `inv` for hardware bit manipulation (tile coordinate shifts, mask checks).

## Visitor Implementation Pattern

The `gbkt-ir` module defines three non-sealed visitor interfaces. Backends and analysis passes implement them:

- `ExprVisitorI<R>` (`gbkt-ir/.../ExprVisitorI.kt`) — 10 `visit*` methods, one per `Expr` subtype
- `ScriptOpVisitorI<R>` (`gbkt-ir/.../ScriptOpVisitorI.kt`) — 51 `visit*` methods, one per `ScriptOp` subtype
- `SystemIRVisitorI<R>` (`gbkt-ir/.../SystemIRVisitorI.kt`) — 8 `visit*` methods, one per `SystemIR` subtype

Each IR node implements `fun <R> accept(visitor: VisitorI<R>): R = visitor.visitX(this)`. **The interfaces are non-sealed deliberately** (`gbkt-ir/CLAUDE.md` "Architecture"): sealed would force every implementation into one module, defeating the multi-module split. The compiler still enforces exhaustiveness because each visitor interface declares every `visit*` method explicitly — implementing classes that miss one fail to compile.

**Backend conventions** (`gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/visitor/CLAUDE.md`):
- `object` visitors are stateless (`ScriptOpVisitor`, `ActorVisitor`, `SceneVisitor`)
- `class` visitors receive `GameIR` (or subsets) in their constructor for cross-cutting queries (`ExprVisitor`, `DialogVisitor`, `MenuVisitor`, `HudVisitor`, `InventoryVisitor`, `CombatVisitor`, `RpgVisitor`, `SoundVisitor`, `CollisionVisitor`, `GBDKSystemVisitor`)
- Visitors return immutable `CFile`/`CFunction`/`CVarDecl` data classes. The pipeline assembles them into `CFile` instances; `CEmitter` is the single point of text serialization.

**Literal-emission discipline** (signed-vs-unsigned C literals): use `CLiteral(N)` everywhere by default; use `CIntLiteral(N)` only as the RHS of a comparison `CBinaryExpr` whose LHS is an INT8/INT16-typed expression. The rationale is in `gbkt-backend-gbdk/CLAUDE.md` § "Literal Emission Convention" — C11 §6.3.1.8 silently promotes signed operands of mixed-signedness comparisons, inverting the test. The split is enforced by the sealed `CExpr` hierarchy in the AST and by `ExprVisitor`'s variable-type registry.

## Error Handling

**Strategy:** fail fast with descriptive exceptions. No silent failures.

**DSL validation errors:** `DSLValidationError` (`gbkt-lang/src/main/kotlin/io/github/gbkt/core/dsl/Errors.kt:22`) is thrown at recording time or at `GameBuilder.build()`. The message format follows compiler conventions:

```
error: Unresolved reference "X". Did you mean 'Y'?
```

The "did you mean" suggestion is computed via Levenshtein distance in `gbkt-ir/src/main/kotlin/io/github/gbkt/core/Suggestions.kt`.

**`error(...)` for invariant violations:** the standard library `error()` (throws `IllegalStateException`) is used inside DSL operator implementations to enforce context:

```kotlin
infix fun AssignableVar.set(value: Expr) {
    ScriptBuilderContext.current?.assign(name, value, AssignOp.SET)
        ?: error("set() called outside a ScriptBuilder block")
}
```

**`requireNotNull` and Elvis pattern** for null safety (per CONTRIBUTING.md §1):

```kotlin
val gameBuilder = GameBuilderContext.current
    ?: error("actor {} must be called inside a game {} block")
```

**Broad catch only at boundaries:** detekt `TooGenericExceptionCaught` is excluded for `**/intellij/codegen/**`, `**/mcp/**`, and `**/emulator/**` — catching `Exception` is allowed only at protocol boundaries (savestate IO, metadata parse, MCP JSON dispatch). The `@Suppress("TooGenericExceptionCaught")` annotation must be present with an inline comment explaining why (see `GbktTestExtension.kt:125-130`).

**Test failure handling:** `throw AssertionError(...)` directly in main source (because `kotlin.test` and JUnit5 `Assertions` are only available in test source sets). All `gbkt-test` assertion functions follow this pattern. Failure messages always include the frame number and current scene for diagnostic context (`gbkt-test/.../GbktTestExtension.kt:40-41`).

## Null Safety

**Never use `!!` in production code** (CONTRIBUTING.md §1). Preferred alternatives, in order:

1. `lateinit var` — for definitely-initialized properties (e.g. `agent: StepAgent` in `GbktTestExtension`)
2. `requireNotNull(x) { "descriptive message" }` — for runtime invariants
3. Refactor to return non-null (e.g. lazy-initialize)
4. Safe-call with `let` — for nullable side-effects

Test code is allowed `!!` for terse fixture access (e.g. `game.metadata!!` in `PongStepAgentTest.kt:64`) — this is a pragmatic exception, not a green light for main source.

## Immutability Defaults

- **Prefer immutable collections** (CONTRIBUTING.md §6): `listOf`, `mapOf`, `setOf` by default; `buildList`/`buildMap` for conditional construction; `mutableListOf` only when mutation is required (e.g. recording IR ops in a `ScriptBuilder`)
- **Use sequences for large chains** (3+ operations): `sprites.asSequence().filter {...}.map {...}.toList()`
- IR types are `data class` with `val` properties throughout (`Expr.kt`, `ScriptOp.kt`, `Types.kt`) — IR is immutable by construction
- C AST types in `gbkt-backend-gbdk/.../codegen/ast/` are also immutable data classes. The pipeline assembles them into `CFile` instances that are emitted once

## Scope Functions

Per CONTRIBUTING.md §2:

| Function | Use Case | Returns |
|----------|----------|---------|
| `apply`  | Object configuration (fluent builders) | `this` |
| `also`   | Side effects (logging, registration) | `this` |
| `let`    | Null-safe transformations | Lambda result |
| `run`    | Computing a result with receiver | Lambda result |
| `with`   | Multiple operations on same object | Lambda result |

## Comments

**KDoc on public types and DSL builders** — non-trivial DSL constructs document their semantics, intended usage, and the IR node they produce. See `VarDelegate` at `VariableBuilders.kt:330-389` for the canonical pattern.

**Inline `// rationale:` comments** for `@Suppress` annotations explain why a detekt rule is disabled at that site (e.g. `GbktTestExtension.kt:124-125`: "Broad catch is intentional: failure-reporting code must not throw new exceptions that would mask the original test failure.").

**TODO/FIXME/HACK** allowed (detekt `ForbiddenComment` disabled). No outstanding occurrences in `gbkt-test/src/main/kotlin/`.

## Function Design

- **Size:** target <80 lines (detekt `LongMethod` threshold); excluded packages may exceed (codegen, IR, validation, DSL builders — see the exclusion table)
- **Parameters:** ≤6 for functions, ≤7 for constructors; exempted packages allow more
- **Return values:** ≤4 return statements per function (detekt `ReturnCount`); IntelliJ/MCP/codegen exempted for fail-fast input validation

## Module Design

- **Exports:** Kotlin's default `public` visibility for the API; `internal` for module-private helpers (used in `gbkt-test` for stub injection: `GbktTestExtension.stubEmulatorFactory` at line 62)
- **Barrel files:** not used. Explicit imports per CONTRIBUTING.md §8
- **Module boundary enforcement:** `gbkt-ir` runs a `validateModuleBoundaries` task during `check` that rejects any dependency on `gbkt-lang`/`gbkt-engine`/`gbkt-world`/`gbkt-core` — `gbkt-ir` must remain a leaf
- **One primary class per file**; max 5–7 top-level declarations (CONTRIBUTING.md §9); IR domain files (one file per IR subsystem) are the exception

## License Headers

All `.kt` files start with the MPL 2.0 header (Apache 2.0 for `gbkt-intellij-plugin`). Spotless enforces this automatically (`build.gradle.kts:46-88`). The header is:

```kotlin
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
```

---

*Convention analysis: 2026-05-27*
