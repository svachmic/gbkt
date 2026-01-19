# DSL (Domain-Specific Language) Module

The DSL module provides the recording mechanism that captures Kotlin code as IR nodes. When you write `playerX += 2` inside a `whenever { }` block, this module ensures it becomes `IRAssign` instead of actually modifying a variable.

## Architecture

```
Kotlin DSL Code → Recording Context → Statement Recorder → IR Nodes
```

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `RecordingContext.kt` | Thread-local context that captures statements during DSL execution | ~145 |
| `LogicBlock.kt` | Reusable logic blocks with parameter substitution | ~180 |
| `LogicBlockBuilder.kt` | Factory functions for creating logic blocks | ~250 |
| `Conditionals.kt` | `whenever`, `branch`, `otherwise` control flow | ~155 |
| `Loops.kt` | `repeat`, `forEach` iteration constructs | ~100 |
| `GameScope.kt` | Base class for DSL contexts, manages variable/array registration | ~32 |
| `DslMarkers.kt` | `@DslMarker` annotations to prevent scope leakage | ~20 |
| `TypeAliases.kt` | Common type aliases for convenience | ~15 |

## Recording Context (RecordingContext.kt)

The core mechanism that makes the DSL work.

### How It Works

1. When entering a recording block (`every.frame { }`, `whenever { }`), a `StatementRecorder` is set in thread-local storage
2. Operations on DSL types check `RecordingContext.isRecording`
3. If recording, operations emit IR nodes instead of executing
4. When the block exits, recorded statements are collected

```kotlin
// Inside RecordingContext
object RecordingContext {
    private val holder = RecorderHolder()  // ThreadLocal<StatementRecorder?>

    val isRecording: Boolean
        get() = current != null

    fun <T> record(recorder: StatementRecorder, block: () -> T): T {
        val previous = holder.get()
        holder.set(recorder)
        return try {
            block()
        } finally {
            holder.set(previous)
        }
    }
}
```

### StatementRecorder

Collects IR statements during recording:

```kotlin
class StatementRecorder {
    private val _statements = mutableListOf<IRStatement>()
    val statements: List<IRStatement> get() = _statements

    fun emit(stmt: IRStatement) {
        // Automatically captures source location for sourcemaps
        _statements.add(stmt.withSourceLocation(SourceLocation.capture()))
    }

    fun replaceLast(stmt: IRStatement) {
        // Used for whenever { } otherwise { } chaining
        _statements[_statements.lastIndex] = stmt
    }
}
```

## Conditionals (Conditionals.kt)

### whenever

The primary conditional construct:

```kotlin
// Basic usage
whenever(buttons.a.pressed) { jump() }

// With comparison
whenever(health isBelow 10) { showWarning() }

// With otherwise (else)
whenever(score isAtLeast 100) {
    win()
} otherwise {
    continue()
}
```

### WheneverResult

Enables `otherwise` chaining:

```kotlin
class WheneverResult(
    internal val condition: IRExpression,
    internal val thenStatements: List<IRStatement>,
)

infix fun WheneverResult.otherwise(block: () -> Unit) {
    // Replaces last IRIf with one that includes else branch
    RecordingContext.require().replaceLast(
        IRIf(condition, thenStatements, elseRecorder.statements)
    )
}
```

### branch

Multi-condition branching (like when/switch):

```kotlin
branch {
    buttons.a.pressed then { jump() }
    buttons.b.pressed then { shoot() }
    dpad.down.pressed then { duck() }
}
```

## Logic Blocks (LogicBlock.kt, LogicBlockBuilder.kt)

Reusable recorded code patterns.

### Basic LogicBlock

```kotlin
val applyGravity = logicBlock("gravity") {
    velocityY += 1
    whenever(velocityY isAbove 8) { velocityY set 8 }
}

// Usage - expands to recorded IR
scene("gameplay") {
    every.frame {
        applyGravity()  // Deep copies and emits all recorded statements
    }
}
```

### Parameterized LogicBlock

```kotlin
val addScore = logicBlock<Expr>("addScore", "amount") { amount ->
    score += amount
}

// Usage with substitution
whenever(coinCollected) { addScore(10.expr) }  // amount → IRLiteral(10)
whenever(gemCollected) { addScore(50.expr) }   // amount → IRLiteral(50)
```

### Two-Parameter LogicBlock

```kotlin
val moveBy = logicBlock<Expr, Expr>("moveBy", "dx", "dy") { dx, dy ->
    playerX += dx
    playerY += dy
}

every.frame {
    moveBy(dpad.x * speed, dpad.y * speed)
}
```

### How Parameters Work

1. Placeholder variable created: `__param_amount_0`
2. Block recorded with placeholder in IR
3. On invocation, `deepCopy(substitutions)` replaces placeholders with actual values

```kotlin
// Recording time: score += __param_amount_0
// Expansion time: score += 10  (after substitution)
```

## GameScope (GameScope.kt)

Base class for DSL contexts that tracks variables and arrays:

```kotlin
abstract class GameScope {
    internal val variables = mutableListOf<GBVar<*>>()
    internal val arrays = mutableListOf<GBArray>()

    fun registerVariable(v: GBVar<*>) { ... }
    fun registerArray(arr: GBArray) { ... }
}
```

Variables created with `by u8Var()` automatically register via `GameScopeContext`:

```kotlin
object GameScopeContext {
    private val holder = GameScopeHolder()  // ThreadLocal<GameScope?>

    val current: GameScope?
        get() = holder.get()
}

// In U8Delegate.getValue():
GameScopeContext.current?.registerVariable(newVar)
```

## DSL Markers (DslMarkers.kt)

Prevents accidental scope leakage in nested builders:

```kotlin
@DslMarker
annotation class GbktDsl

@GbktDsl
class SceneBuilder { ... }

@GbktDsl
class EntityBuilder { ... }
```

## Why "Unused" Parameters?

Some DSL patterns intentionally have "unused" receiver parameters:

```kotlin
// The receiver 'this' provides context but isn't directly used
infix fun Condition.then(block: () -> Unit) {
    // 'this' (Condition) is used implicitly in WhenBranchDef
    val recorder = StatementRecorder()
    RecordingContext.record(recorder, block)
    branches.add(WhenBranchDef(this, recorder.statements))
}
```

This is why the `UnusedParameter` detekt rule is suppressed for the DSL module - it's an intentional pattern, not dead code.

## Key Patterns

### Recording Block Pattern

```kotlin
fun whenever(condition: Condition, block: () -> Unit): WheneverResult {
    val recorder = StatementRecorder()
    RecordingContext.record(recorder, block)  // Execute with recording active
    RecordingContext.require().emit(IRIf(condition.ir, recorder.statements))
    return WheneverResult(condition.ir, recorder.statements)
}
```

### Callback Recording Pattern

```kotlin
private fun recordCallback(block: FrameScope.() -> Unit): List<IRStatement> {
    val recorder = StatementRecorder()
    RecordingContext.record(recorder) {
        FrameScope("callback").block()
    }
    return recorder.statements
}
```

## Related Modules

- `ir/` - IR nodes that are recorded
- `ir/Variables.kt` - Variable types that check recording context
- `ir/ExpressionWrapper.kt` - Operators that generate IR when recording
- `scene/Scene.kt` - Scene lifecycle that creates recording contexts
