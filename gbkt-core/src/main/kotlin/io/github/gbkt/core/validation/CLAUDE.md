# Validation Module

Compile-time validation of IR references, array bounds, and game integrity.

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `ArrayBoundsValidation.kt` | Validates array index bounds at compile time | ~322 |
| `IRReferenceValidation.kt` | Validates references to sprites, pools, menus, dialogs | ~527 |

## Array Bounds Validation (ArrayBoundsValidation.kt)

Detects out-of-bounds array accesses before runtime:

### What It Checks

```kotlin
// Static literal index - ERROR if out of bounds
inventory[10]  // If inventory.size < 11, error

// Loop variable - checks loop range against array size
for (i in 0 until 20) {
    scores[i]  // ERROR if scores.size < 20
}

// Unknown variable - WARNING about unchecked access
scores[playerSelection]  // Warning: cannot verify bounds
```

### Validation Categories

| Category | Type | Description |
|----------|------|-------------|
| Literal index OOB | Error | `array[5]` where size < 6 |
| Loop range OOB | Error | Loop range exceeds array bounds |
| Unchecked variable | Warning | Dynamic index with unknown range |
| Complex expression | Warning | Cannot verify compound index |

### Scope Coverage

Validation runs across:
- All scene callbacks (enter, frame, exit)
- Pool onFrame statements
- State machine callbacks (onEnter, onTick, onExit)

### Implementation Details

The validator tracks known variable ranges from `for` loops:

```kotlin
// Inside for loop, counter has known bounds
validateArrayBoundsInStatements(
    stmt.body,
    context,
    arrayBounds,
    knownBounds + (stmt.counter to stmt.range)  // Track loop var
)
```

## IR Reference Validation (IRReferenceValidation.kt)

Validates that all named references exist:

### Reference Types Checked

| Reference Type | Example | Category |
|---------------|---------|----------|
| Sprite | `animation.play("walk")` | SPRITE_REFERENCE |
| Pool | `bullets.spawn { ... }` | POOL_REFERENCE |
| Menu | `mainMenu.show()` | MENU_REFERENCE |
| Dialog | `dialog.say("Hello")` | DIALOG_REFERENCE |

### Validation with Suggestions

Unknown references include "did you mean" suggestions:

```
Error: Pool spawn references unknown pool 'bullet'.
       Did you mean 'bullets'?
```

### IR Statements Checked

**Animation operations:**
- IRAnimationPlay, IRAnimationStop, IRAnimationPause
- IRAnimationResume, IRAnimationSetFrame, IRAnimationSetSpeed
- IRAnimationQueue

**Pool operations:**
- IRPoolSpawn, IRPoolSpawnAt, IRPoolTrySpawn
- IRPoolUpdate, IRPoolDespawn, IRPoolDespawnAll
- IRPoolForEach

**Menu operations:**
- IRMenuShow, IRMenuOpen, IRMenuTick
- IRMenuSelect, IRMenuCancel, IRMenuMoveTo

**Dialog operations:**
- IRDialogShow, IRDialogSay

### Recursive Validation

Nested statements are validated recursively:

```kotlin
is IRIf -> {
    validateIRReferencesInStatements(stmt.then, ...)
    stmt.otherwise?.let { validateIRReferencesInStatements(it, ...) }
}

is IRPoolSpawn -> {
    // Check pool exists
    if (stmt.poolName !in knownPools) { ... }
    // Also validate init statements
    validateIRReferencesInStatements(stmt.initStatements, ...)
}
```

## ValidationCategory Enum

```kotlin
enum class ValidationCategory {
    ARRAY_BOUNDS,       // Array index validation
    SPRITE_REFERENCE,   // Sprite name validation
    POOL_REFERENCE,     // Pool name validation
    MENU_REFERENCE,     // Menu name validation
    DIALOG_REFERENCE,   // Dialog name validation
    // ... other categories
}
```

## Integration

Validation runs as part of `GameValidator`:

```kotlin
class GameValidator(val game: Game) {
    val errors = mutableListOf<ValidationError>()
    val warnings = mutableListOf<ValidationWarning>()

    fun validate() {
        validateArrayBounds()
        validateIRReferences()
        // ... other validations
    }
}
```

## Related Modules

- `Validation.kt` - Main GameValidator class
- `Suggestions.kt` - "Did you mean" suggestion generation
- `ir/` - IR node types being validated
