# IR (Intermediate Representation) Module

The IR module defines the data structures that bridge Kotlin DSL code and generated C output. DSL operations record IR nodes, which are later transformed into GBDK-compatible C code.

## Architecture

```
Kotlin DSL → Recording Context → IR Nodes → Codegen → C Output
```

## Directory Structure

The IR module contains 32 files organized by domain:

### Core Files

| File | Purpose | LOC |
|------|---------|-----|
| `CoreTypes.kt` | Primitive types (u8, u16, i8, i16) and GBC colors | ~300 |
| `CoreIR.kt` | Base IR statements and expressions (IRAssign, IRIf, IRBinary, etc.) | ~150 |
| `ExpressionWrapper.kt` | `Expr` class with 60+ operator overloads for DSL ergonomics | ~280 |
| `Variables.kt` | GBVar, property delegates (u8Var, u16Var), arrays | ~630 |
| `FixedPointTypes.kt` | Fixed-point math types (Speed, Fixed8_8) | ~110 |
| `IRSubstitution.kt` | Deep copy and parameter substitution for LogicBlock | ~250 |

### Domain-Specific IR Files

| File | Purpose |
|------|---------|
| `AudioIR.kt` | Sound and music playback statements |
| `DialogIR.kt` | Dialog box and text display |
| `MenuIR.kt` | Menu navigation and selection |
| `PathfindingIR.kt` | A* pathfinding operations |
| `SoundIR.kt` | Sound effect definitions |
| `SystemIR.kt` | Screen, palette, and system control |
| `Transitions.kt` | Scene transition effects |

### RPG IR Files

| File | Purpose |
|------|---------|
| `AbilityIR.kt` | Ability execution effects (damage, heal, status) |
| `BattleIR.kt` | Battle state machine statements |
| `BattleMenuIR.kt` | Combat menu navigation |
| `CombatStateIR.kt` | Combat state queries |
| `CombatTraitsIR.kt` | Aspect/resistance system |
| `DamageIR.kt` | Damage calculation expressions |
| `EquipmentIR.kt` | Equipment slot operations |
| `FlagsIR.kt` | Global boolean flags |
| `ItemIR.kt` | Item usage statements |
| `LevelingIR.kt` | Experience and level-up |
| `MonsterIR.kt` | Monster instantiation |
| `StatsIR.kt` | Character stat operations |
| `StatusEffectIR.kt` | Buff/debuff application |
| `TargetSelectionIR.kt` | Targeting resolution |
| `TurnOrderIR.kt` | Turn calculation |
| `StatusBarIR.kt` | HP/SP bar rendering |
| `CombatFormulasIR.kt` | Hit/crit formula configuration |
| `ActionExecutionIR.kt` | Action queue processing |

## Key Concepts

### Sealed Interfaces

IR nodes use sealed interfaces for exhaustive pattern matching:

```kotlin
sealed interface IRStatement {
    val sourceLocation: SourceLocation?  // For sourcemaps
}

sealed interface IRExpression
```

### Core Statement Types

```kotlin
IRAssign(target, value, op)     // Variable assignment
IRIf(condition, then, otherwise) // Conditional
IRWhen(branches, otherwise)      // Multi-branch switch
IRWhile(condition, body)         // Loop
IRFor(counter, range, body)      // Counted loop
IRCall(function, args)           // Function call
IRSceneChange(sceneName)         // Scene transition
IRRaw(code)                      // Raw C injection
IRArrayAssign(array, index, value) // Array element write
```

### Core Expression Types

```kotlin
IRLiteral(value)              // Constant value
IRVar(name)                   // Variable read
IRBinary(left, op, right)     // Binary operation
IRUnary(op, operand)          // Unary operation
IRTernary(cond, then, else)   // Conditional expression
IRCallExpr(function, args)    // Function call expression
IRArrayAccess(array, index)   // Array element read
```

## Primitive Types (CoreTypes.kt)

Game Boy native types with full operator support:

| Type | Range | C Type | Use Case |
|------|-------|--------|----------|
| `u8` | 0-255 | `UINT8` | Positions, counters |
| `u16` | 0-65535 | `UINT16` | Score, large values |
| `i8` | -128..127 | `INT8` | Velocity, offsets |
| `i16` | -32768..32767 | `INT16` | Large signed values |

```kotlin
val x = u8(100)
val y = x + u8(50)  // u8(150)
val z = x - 30      // u8(70), auto-wraps on overflow
```

### GBC Color Support

```kotlin
val color = GBCColor.fromRGB888(255, 0, 0)  // Red
val palette = GBCPalette(
    name = "player",
    colors = listOf(GBCColor.WHITE, GBCColor.LIGHT_GRAY, GBCColor.DARK_GRAY, GBCColor.BLACK)
)
```

## Expression Wrapper (ExpressionWrapper.kt)

The `Expr` class enables natural operator syntax in DSL recording blocks:

```kotlin
// Inside recording context
playerX + 2          // Expr(IRBinary(IRVar("playerX"), ADD, IRLiteral(2)))
score * multiplier   // Expr(IRBinary(..., MUL, ...))
health isBelow 10    // Condition(IRBinary(..., LT, ...))
```

### Operator Categories

**Arithmetic**: `+`, `-`, `*`, `/`, `rem`
**Bitwise**: `and`, `or`, `xor`, `shl`, `shr`, `inv()`
**Comparison**: `eq`, `neq`, `lt`, `lte`, `gt`, `gte`
**Readable aliases**: `isBelow`, `isAbove`, `isAtLeast`, `isAtMost`, `isEqualTo`, `isBetween`
**Condition properties**: `isZero`, `isNonZero`, `isPositive`, `isNegative`, `isTrue`, `isFalse`

### Condition and Ternary

```kotlin
// Condition for control flow
whenever(score isAtLeast 100) { win() }

// Ternary expressions
damage set (isCritical.then(20, 10))
speed set ((isRunning) then fastSpeed otherwise slowSpeed)
```

## Variable System (Variables.kt)

### Property Delegates

```kotlin
var playerX by u8Var(80)    // Creates GBVar<u8> named "playerX"
var score by u16Var(0)      // Creates GBVar<u16> named "score"
var velocityX by i8Var(0)   // Creates GBVar<i8> named "velocityX"
```

### AssignableExpr

Variables return `AssignableExpr` which supports:
- `set(value)` - Direct assignment
- `+=`, `-=`, `*=`, `/=`, `%=` - Compound assignment
- All `Expr` operators for reading

```kotlin
playerX set 100          // Direct assign
playerX += 2             // Compound add
whenever(playerX isAbove 160) { playerX set 0 }
```

### Arrays

```kotlin
val inventory by u8Array(size = 10)
val highScores by u16Array(size = 5)

inventory[0] set 5           // Static index
inventory[slot] set item     // Dynamic index
inventory[0] += 1            // Compound on element
```

## IR Substitution (IRSubstitution.kt)

Deep copy with parameter substitution for LogicBlock reuse:

```kotlin
// Deep copy an expression, replacing placeholder with actual value
val copied = original.deepCopy(mapOf("__param_amount_0" to IRLiteral(100)))

// Convenience functions
statement.substituteParameter("amount", IRLiteral(50))
```

### Supported for Deep Copy

All IR nodes implement `deepCopy()`:
- Core: IRAssign, IRIf, IRWhen, IRWhile, IRFor, IRCall, etc.
- Pool: IRPoolSpawn, IRPoolForEach, IRPoolDespawn, etc.
- Camera: IRCameraSetPosition, IRCameraFollow, etc.
- Save: IRSaveLoad, IRSaveSave, IRSaveFieldWrite, etc.
- Transitions: IRTransitionFadeOut, IRTransitionIris, etc.

## Fixed-Point Types (FixedPointTypes.kt)

### Speed

Animation speed multiplier (percentage):
```kotlin
player.play(animation, speed = Speed.NORMAL)   // 100 = 1.0x
player.play(animation, speed = Speed(50))      // 50 = 0.5x
player.play(animation, speed = 2.0.speed)      // 200 = 2.0x
```

### Fixed8_8

8.8 fixed-point for physics (256 = 1.0):
```kotlin
physics {
    gravity = Fixed8_8(0.5f)    // 128 raw
    friction = Fixed8_8(0.9f)   // 230 raw
}
```

## Why So Many Operator Overloads?

The `Expr` class has 60+ operator overloads. This is intentional:

1. **DSL Ergonomics**: Makes `playerX + 2` work naturally
2. **Type Safety**: Each overload handles Int/Expr combinations
3. **Recording Context**: Operators create IR nodes, not compute values
4. **Readable Aliases**: `isAbove` is clearer than `gt` for game logic

This design is a core architectural decision that enables the Kotlin-to-C compilation pipeline.

## Related Modules

- `dsl/` - Recording context that captures IR
- `codegen/` - Transforms IR to C code
- `codegen/core/ExpressionCodegen.kt` - Expression to C
- `codegen/core/StatementCodegen.kt` - Statement to C
