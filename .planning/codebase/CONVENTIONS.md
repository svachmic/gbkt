# Coding Conventions

**Analysis Date:** 2026-02-17

## Naming Patterns

**Files:**
- Match primary public declaration: `LogicBlock.kt` for `class LogicBlock`
- Domain-grouped files use suffix: `CameraIR.kt`, `SaveSystemIR.kt`, `PositionComponent.kt`
- Test files end with `Test`: `InputTest.kt`, `ExpressionCodegenTest.kt`
- Internal utilities separate by package: `src/main/kotlin/io/github/gbkt/core/{package}/`

**Functions:**
- camelCase for all functions: `logicBlock()`, `u8Var()`, `calculateDamage()`
- DSL builders use lowercase: `scene()`, `entity()`, `menu()`, `battle()`
- Test functions use backtick-quoted names for clarity: ``fun `button A held generates correct mask check`() {}``
- Helpers starting with `get`/`create`/`set`: `getOrCreate()`, `createSprite()`, `setVariable()`

**Variables:**
- camelCase in Kotlin code: `playerX`, `velocityY`, `buttonPressed`
- In-game variables (u8Var/u16Var) use snake_case for multi-word: `player_x`, `enemy_count`, `sprite_bank`
- Private fields prefixed with underscore for generated code: `_joypad`, `_joypad_prev`
- Boolean variables: `isVisible`, `hasCollided`, `isRecording`

**Types:**
- PascalCase for classes: `GameBuilder`, `SimulationContext`, `MenuBuilder`
- Sealed interfaces end with base name: `IRStatement`, `IRExpression`, `GameFlowScene`
- Data classes follow domain naming: `TestEntity`, `GBCColor`, `Dimensions`
- Type aliases in lowercase: `Condition`, `Expr`, `AssignableExpr`

## Code Style

**Formatting:**
- Spotless with KTFmt (kotlinlangStyle)
- Line length max: 120 characters (exceptions: tests, codegen, raw strings)
- License header: MPL 2.0 for most modules, Apache 2.0 for IntelliJ plugin
- Trailing whitespace trimmed, files end with newline

**Linting:**
- Detekt enabled with detekt.yml config
- maxIssues: 0 (enforce on each module)
- Baseline tracking via detekt-baseline.xml for incremental cleanup
- Key rule exceptions:
  - `MagicNumber`: Disabled (game dev has coordinate/color constants)
  - `UnusedPrivateMember`: Disabled (DSL receivers trigger false positives)
  - Complexity thresholds relaxed for codegen, IR, validation packages

## Import Organization

**Order:**
1. Kotlin stdlib imports
2. Third-party imports (kotest, json, etc.)
3. Project imports (io.github.gbkt.*)
4. Blank line between groups, sorted alphabetically within

**Rules:**
- No star imports except `kotlin.test.*` in test files (exception)
- Explicit imports improve IDE refactoring and prevent collisions
- Test files may use `import kotlin.test.*` for brevity
- Always `import kotlin.test.Test`, `import kotlin.test.assertEquals`, etc. in unit tests

**Example:**
```kotlin
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.dsl.GameScope
import io.github.gbkt.core.dsl.RecordingContext
import kotlin.test.*  // Allowed in tests only
```

## Error Handling

**Approach:**
- Use `require()` for precondition validation with descriptive message
- Use `check()` for invariant validation
- Use `error()` when recoverable code path is impossible
- Use `requireNotNull()` instead of `!!` (never force unwrap)

**Patterns:**

```kotlin
// Precondition validation
require(speed >= 0) { "Animation speed must be non-negative" }
require(index >= 0 && index < 40) { "Sprite index must be 0-39" }

// Invariant validation
check(currentBank >= 0) { "Bank must be initialized before use" }

// Impossible path
error("Toggle requires a variable") // After exhaustive type checking

// Null safety
val name = requireNotNull(variable) { "Variable not initialized" }.name
val value = variable?.let { processIt(it) }  // Safe navigation
```

**Recording Context Require:**
- Operations that must run during DSL recording use `RecordingContext.require()`:
  ```kotlin
  fun menuShow() {
      RecordingContext.require().emit(IRMenuShow(definition.name))
  }
  ```
- Throws clear error if called outside recording context

## Logging

**Framework:** `println()` or Kotlin `println` for simple logging (no SLF4J dependency)

**When to Log:**
- DSL debug output uses `println()` guarded by `if (debugGraphics)`
- Test output via standard assertion messages
- Code generation debug info in codegen methods (disabled by default)
- Never log in hot loops or per-frame operations

**Pattern:**
```kotlin
if (debugGraphics) {
    println("Loading tilemap: $tilesetName")
}
```

## Null Safety

**Approach:**

1. **lateinit for definitely-initialized properties:**
   ```kotlin
   private lateinit var variable: GBVar<u8>
   // Used when property is always set before access
   ```

2. **requireNotNull with descriptive message:**
   ```kotlin
   val name = requireNotNull(variable) { "Variable not initialized" }.name
   ```

3. **Refactor to return non-null:**
   ```kotlin
   private fun getOrCreate(property: KProperty<*>): GBVar<u8> {
       return variable ?: GBVar(property.name, u8(0), GBVar.VarType.U8).also {
           variable = it
       }
   }
   ```

4. **Safe call with let:**
   ```kotlin
   variable?.let { registerVariable(it) }
   ```

## Scope Functions

Use appropriately for cleaner, expressive code:

| Function | Use Case | Returns | Example |
|----------|----------|---------|---------|
| `apply` | Object configuration | `this` | `PositionComponent().apply { xOffset = 0 }` |
| `also` | Side effects (logging, registration) | `this` | `createSprite(asset).also { gameBuilder.registerSprite(it) }` |
| `let` | Null-safe transformations | Lambda result | `sprite?.let { render(it) }` |
| `run` | Computing a result with receiver | Lambda result | `context.run { compute() }` |
| `with` | Multiple operations on same object | Lambda result | `with(builder) { add(); configure() }` |

## Builder Methods

Builder methods that configure state return `this` via `apply` for fluent APIs:

```kotlin
// PREFER: Fluent builder pattern
fun position(x: Int, y: Int) = apply {
    this.x = x
    this.y = y
}

// Usage
MenuStyleBuilder()
    .position(5, 8)
    .width(12)
    .build()
```

## When Expressions

Use `when` instead of `if/else if/else` chains for 3+ branches:

```kotlin
// PREFER: when expression
val cType = when (varType) {
    VarType.U8 -> "UINT8"
    VarType.U16 -> "UINT16"
    VarType.I8 -> "INT8"
    VarType.I16 -> "INT16"
}

// Let sealed classes provide exhaustiveness checking
// Don't add unnecessary 'else' branches
```

## Type Inference

Let compiler infer types where obvious:

```kotlin
// PREFER: Let compiler infer
val builder = GameBuilder(name)
val sprites = mutableListOf<Sprite>()

// AVOID: Redundant type declarations
val builder: GameBuilder = GameBuilder(name)

// EXCEPTION: Public API return types are explicit
fun createGame(name: String): Game { ... }
```

## Collections

Prefer immutable collections when mutation not needed:

```kotlin
// PREFER: Immutable by default
val items: List<MenuItem> = listOf(item1, item2)

// Use buildList/buildMap for conditional construction
val sprites = buildList {
    add(mainSprite)
    if (hasAnimation) add(animationSprite)
}

// Use sequences for large collection chains (3+ operations)
sprites.asSequence()
    .filter { it.isVisible }
    .map { it.toBitmap() }
    .toList()
```

## Extension Functions

Prefer extension functions over utility classes:

```kotlin
// PREFER: Extension function
fun Entity.jumpWithParticles() {
    velY set -8
    spawnParticles(x, y)
}

// AVOID: Utility class
object EntityUtils {
    fun jumpWithParticles(entity: Entity) { ... }
}
```

## File Organization

**Size guidelines:**
- Target: <400 lines per file
- Hard limit: 600 lines (except codegen files which may exceed)
- Codegen files allowed to exceed due to complex IR → C transformations

**Declaration limits:**
- Maximum 5-7 top-level declarations per file
- Sealed hierarchies may exceed when logically cohesive (IR nodes)
- Each file should have one primary public class/object

**When to split:**
- File exceeds 400 lines
- Multiple distinct responsibilities emerge
- IDE navigation becomes cumbersome

## Package Organization

**gbkt-core structure:**
```
gbkt-core/src/main/kotlin/io/github/gbkt/core/
├── ir/         # Pure IR data classes, no business logic
├── dsl/        # DSL builders and recording context
├── builder/    # Game builder and configuration
├── entity/     # Entity system and pools
├── graphics/   # Sprites, animation, camera, tilemap
├── collision/  # Collision detection (AABB, sweep)
├── rpg/        # RPG system (characters, battles, items)
├── world/      # World system (floors, encounters, flags)
├── scene/      # Scene management and transitions
├── input/      # Input handling and buffering
├── ui/         # Menu system
├── test/       # Simulation testing framework
├── services/   # Dependency injection
├── assets/     # Type-safe asset references
├── validation/ # Array bounds, IR reference checking
├── exploration/# Dungeon crawling system
├── movement/   # Entity movement controller
├── combat/     # Battle engine core
├── flow/       # Game flow and pause menus
└── optimization/# Asset analysis and suggestions
```

**Dependency rules (gbkt-core):**
- `ir/` ← Pure data, no dependencies on other packages
- `dsl/` ← Depends only on `ir/`
- Domain packages (`rpg/`, `world/`, etc.) ← Depend on `ir/` and `dsl/`
- No circular dependencies between packages

**gbkt-backend-gbdk structure:**
```
gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/
├── codegen/
│   ├── core/        # Core codegen (variables, expressions, statements)
│   ├── rpg/         # RPG system codegen
│   └── world/       # World system codegen
├── profiles/        # Target profiles (GB, GBC)
└── *.kt             # Backend implementation
```

**Test structure mirrors source:**
```
gbkt-core/src/test/kotlin/io/github/gbkt/core/
├── LogicBlockTest.kt       # Maps to dsl/LogicBlock.kt
├── InputTest.kt            # Maps to input/
├── rpg/
│   ├── BattleTest.kt       # Maps to rpg/Battle.kt
│   └── ...
└── ...
```

## Comments

**When to comment:**
- Non-obvious algorithm logic (e.g., collision math)
- WHY code exists (not WHAT it does - code shows that)
- Workarounds or edge cases with explanation
- Complex DSL patterns that aren't self-documenting

**Avoid:**
- Redundant comments that repeat the code
- Outdated comments that contradict implementation

**Example:**

```kotlin
// Deep copy with substitution needed because parameterized logic blocks
// reuse the same IR tree across multiple invocations.
// Without copying, modifications would affect the original definition.
fun deepCopy(substitutions: Map<String, IRExpression>): LogicBlock {
    // ...
}
```

**JSDoc/KDoc:**
- Used on public APIs and complex functions
- Document parameters, return value, and usage example
- Example for DSL functions:

```kotlin
/**
 * Record IR statements from a block without executing them.
 *
 * The block is executed within a recording context, capturing all operations as IR nodes.
 *
 * @param block The logic to record
 * @return [RecordedIR] for fluent assertions
 *
 * ## Example
 *
 * ```kotlin
 * record { playerX += 10 }
 *     .assertEmitted<IRAssign>()
 *     .assertCount(1)
 * ```
 */
fun record(block: () -> Unit): RecordedIR
```

## Function Design

**Size:** Target <50 lines, hard limit <80 lines (except codegen)

**Parameters:**
- Maximum 6 positional parameters (thresholds: 6 functions, 7 constructors)
- Use ignoreDefaultParameters = true
- Data classes ignored in length checks
- Long parameter lists indicate domain complexity, not poor design

**Return values:**
- Prefer explicit return over implicit (lambda result)
- Early return for guard clauses allowed
- Max 4 explicit returns (excludes equals, labeled returns, guard clauses)

## Module Design

**Exports:**
- Single primary public class/interface per file
- Companion object for factory methods
- Extension functions on domain types

**Barrel Files:**
- Use for grouping related exports
- Example: `src/main/kotlin/io/github/gbkt/core/input/ButtonState.kt` re-exports all button types

## DSL Authoring

**@GbktDsl Marker:**
All DSL builder classes must be annotated:
```kotlin
@GbktDsl
class EntityBuilder(private val entityName: String) {
    // ...
}
```

This prevents accidental access to outer scope receivers in nested builders.

**PropertyDelegateProvider:**
Use when registration must happen at declaration time:
```kotlin
class EntityDelegate(
    private val gameBuilder: GameBuilder,
    private val init: EntityBuilder.() -> Unit
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Entity>> {
    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>
    ): ReadOnlyProperty<Any?, Entity> {
        // Build and register immediately when delegate is created
        val entity = EntityBuilder(property.name).apply(init).build()
        gameBuilder.registerEntity(entity)
        return ReadOnlyProperty { _, _ -> entity }
    }
}
```

**Recording Context:**
Use `RecordingContext.record()` for capturing DSL blocks as IR:
```kotlin
fun onSelect(block: MenuActionScope.() -> Unit) {
    val recorder = StatementRecorder()
    RecordingContext.record(recorder) {
        MenuActionScope().block()
    }
    onSelectStatements = recorder.statements
}
```

## Detekt Rule Justifications

**LongMethod (80 line threshold, excludes codegen/ir/test):**
- C code generation inherently produces large methods
- Each IR node maps to C output
- Breaking up would reduce readability

**TooManyFunctions (25 in files, excludes codegen/ir/builder/scene/graphics):**
- `ExpressionWrapper.kt` has 60+ operator overloads for DSL ergonomics
- Builder classes have many configuration methods
- Scene transition scope has many transition types

**LongParameterList (6 functions, 7 constructors, excludes rpg/entity/ui/world/exploration/combat/movement):**
- Domain models (Character, Monster, Battle) require comprehensive fields
- RPG systems have many attributes by nature

**Complexity rules relaxed for:**
- `codegen/` - C code generation inherently complex
- `ir/` - IR substitution/deepCopy inherently complex
- `validation/` - Validation has complex branching
- `collision/` - Collision math is complex
- `intellij/completion/` - Type resolution checks many types

---

*Convention analysis: 2026-02-17*
