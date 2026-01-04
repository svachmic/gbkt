# Contributing to gbkt

Thank you for your interest in contributing to gbkt! This document provides guidelines for contributing code, style requirements, and best practices.

## Getting Started

```bash
# Build the project
./gradlew build

# Run tests
./gradlew :gbkt-core:test

# Build a sample ROM (requires GBDK-2020)
./gradlew :sample-game:buildRom
```

## Project Structure

```
gbkt/
├── gbkt-core/          # Core DSL and code generation
├── gbkt-cli/           # Command-line interface
├── gbkt-gradle-plugin/ # Gradle plugin for build integration
├── sample-*/           # Example games
├── vscode-extension/   # VSCode language support
└── context/            # Documentation
```

---

## Kotlin Style Guide

gbkt is a Kotlin-first framework. We value idiomatic Kotlin over Java-style code. The following guidelines ensure consistency across the codebase.

### 1. Null Safety

**Never use `!!` in production code.** It defeats the purpose of Kotlin's null safety.

```kotlin
// AVOID: Force unwrapping
val name = variable!!.name

// PREFER: Safe handling options

// Option A: lateinit for definitely-initialized properties
private lateinit var variable: GBVar<u8>

// Option B: requireNotNull with descriptive message
val name = requireNotNull(variable) { "Variable not initialized" }.name

// Option C: Refactor to return non-null
private fun getOrCreate(property: KProperty<*>): GBVar<u8> {
    return variable ?: GBVar(property.name, u8(0), GBVar.VarType.U8).also {
        variable = it
    }
}

// Option D: Safe call with let
variable?.let { registerVariable(it) }
```

### 2. Scope Functions

Use scope functions appropriately for cleaner, more expressive code:

| Function | Use Case | Returns |
|----------|----------|---------|
| `apply`  | Object configuration | `this` |
| `also`   | Side effects (logging, registration) | `this` |
| `let`    | Null-safe transformations | Lambda result |
| `run`    | Computing a result with receiver | Lambda result |
| `with`   | Multiple operations on same object | Lambda result |

```kotlin
// apply: Object configuration
positionComponent = PositionComponent(name, x, y, varType).apply {
    xOffset = 0
    yOffset = 0
}

// also: Side effects without changing the expression result
createSprite(asset).also { sprite ->
    gameBuilder.registerSprite(sprite)
}

// let: Null-safe operations
sprite?.let { render(it) }

// run: Computing with receiver
val result = context.run {
    compute()
}
```

### 3. Builder Methods

Builder methods that configure state should return `this` for fluent APIs:

```kotlin
// PREFER: Fluent builder pattern
fun position(x: Int, y: Int) = apply {
    this.x = x
    this.y = y
}

// Then callers can chain:
MenuStyleBuilder()
    .position(5, 8)
    .width(12)
    .build()
```

### 4. When Expressions

Use `when` instead of `if/else if/else` chains (for 3+ branches):

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

### 5. Type Inference

Let the compiler infer types where it's obvious:

```kotlin
// PREFER: Let compiler infer
val builder = GameBuilder(name)
val sprites = mutableListOf<Sprite>()

// AVOID: Redundant type declarations
val builder: GameBuilder = GameBuilder(name)
val sprites: MutableList<Sprite> = mutableListOf<Sprite>()

// EXCEPTION: Public API return types should be explicit
fun createGame(name: String): Game { ... }
```

### 6. Collections

Prefer immutable collections when mutation isn't needed:

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

### 7. Extension Functions

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

### 8. Imports

**Avoid star imports.** Explicit imports improve readability, enable better IDE refactoring, and prevent name collisions.

```kotlin
// AVOID: Star imports
import io.github.gbkt.core.ir.*
import io.github.gbkt.core.dsl.*

// PREFER: Explicit imports
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.dsl.GameScope
import io.github.gbkt.core.dsl.RecordingContext
```

**Exception:** Test files may use `import kotlin.test.*` for brevity.

**Organization:**
- Group imports: stdlib → third-party → project (blank line between groups)
- Sort alphabetically within groups
- Remove unused imports (enforced by Detekt)

### 9. File Organization

**Size guidelines:**
- Target: <400 lines per file
- Hard limit: 600 lines (except codegen files which may exceed for complex systems)

**Declaration limits:**
- Maximum 5-7 top-level declarations per file
- Sealed hierarchies may exceed when logically cohesive (e.g., IR nodes for one domain)
- Each file should have one primary public class/object

**When to split:**
- File exceeds 400 lines
- Multiple distinct responsibilities emerge
- IDE navigation becomes cumbersome

**Naming:**
- Filename matches primary public declaration
- Domain-grouped files: use descriptive suffix (e.g., `CameraIR.kt`, `SaveSystemIR.kt`)

### 10. Package Organization

**Layered architecture (respect boundaries):**
```
ir/       ← Pure data classes, no business logic
dsl/      ← DSL builders, depends only on ir/
codegen/  ← Code generation, depends on ir/ and dsl/
```

**Guidelines:**
- Each package has a single, clear domain
- No circular dependencies between packages
- Prefer extension functions over utility classes
- Tests mirror source package structure

---

## DSL Authoring Guidelines

### @GbktDsl Marker

All DSL builder classes **must** be annotated with `@GbktDsl`:

```kotlin
@GbktDsl
class EntityBuilder(private val entityName: String) {
    // ...
}
```

This prevents accidental access to outer scope receivers:

```kotlin
entity {
    sprite(SpriteAsset("player.png")) {
        // Without @GbktDsl, you could accidentally call entity methods here
        // With @GbktDsl, the compiler prevents this
        position(0, 0)  // Error: position is not in scope
    }
}
```

### PropertyDelegateProvider

Use `PropertyDelegateProvider` when registration must happen at declaration time:

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

### Recording Context

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

---

## Organizing Large Games

For games beyond a few hundred lines, we recommend splitting code across multiple files. Here are suggested patterns (choose what fits your project):

### Pattern 1: Module Extensions

Organize by game systems as extension functions on `GameBuilder`:

```
src/main/kotlin/
├── MyGame.kt           # Entry point
├── modules/
│   ├── PlayerModule.kt # Player system setup
│   └── EnemyModule.kt  # Enemy system setup
├── scenes/
│   ├── TitleScene.kt
│   └── GameplayScene.kt
└── entities/
    ├── PlayerEntity.kt
    └── EnemyEntity.kt
```

```kotlin
// MyGame.kt
val myGame = gbGame("MyGame") {
    // Initialize modules (order may matter for dependencies)
    setupPlayerModule()
    setupEnemyModule()

    // Create scenes
    val titleScene = createTitleScene()
    val gameplayScene = createGameplayScene()

    start = titleScene
}

// modules/PlayerModule.kt
fun GameBuilder.setupPlayerModule() {
    // Global player configuration
    val playerPalette = palette("player") { /* ... */ }
}

fun GameBuilder.createPlayer(x: Int, y: Int): Entity {
    val player by entity {
        position(x, y)
        sprite(SpriteAsset("player.png")) { size = 8 x 16 }
    }
    return player
}

// scenes/GameplayScene.kt
fun GameBuilder.createGameplayScene(): SceneRef {
    val player = createPlayer(80, 72)

    return scene("gameplay") {
        enter { screen.showSprites() }
        every.frame { /* ... */ }
    }
}
```

### Pattern 2: Scene-Per-File

Each scene in its own file:

```kotlin
// scenes/TitleScene.kt
fun GameBuilder.createTitleScene(): SceneRef = scene("title") {
    enter { /* ... */ }
    every.frame { /* ... */ }
}
```

### Pattern 3: Domain Folders

Organize by game domain:

```
src/main/kotlin/
├── Game.kt
├── player/
│   ├── PlayerEntity.kt
│   ├── PlayerStates.kt
│   └── PlayerAbilities.kt
├── enemies/
│   ├── SlimeEnemy.kt
│   └── BossEnemy.kt
└── levels/
    ├── Level1.kt
    └── Level2.kt
```

### Naming Conventions

To avoid name collisions in multi-file games:

| Element | Convention | Example |
|---------|------------|---------|
| Variables | Prefix with domain | `player_x`, `enemy_count` |
| Entities | Descriptive unique name | `mainPlayer`, `bossEnemy` |
| Scenes | Domain-specific | `title`, `level1_gameplay` |

### Important Notes

- **Order matters**: Initialize modules before scenes that depend on them
- **Explicit parameters**: Pass dependencies as function parameters rather than relying on implicit globals
- **Document dependencies**: Add comments noting which modules a scene/entity depends on

---

## Code Review Checklist

Before submitting a PR, verify:

- [ ] No `!!` assertions (use `lateinit`, `requireNotNull`, or refactor)
- [ ] Scope functions used appropriately (`apply`, `also`, `let`, `run`)
- [ ] Builder methods return `this` via `apply` for fluent APIs
- [ ] `when` used instead of long `if/else` chains
- [ ] All DSL builders annotated with `@GbktDsl`
- [ ] No star imports (except `kotlin.test.*` in tests)
- [ ] Files under 600 lines (codegen files may exceed)
- [ ] Maximum 7 top-level declarations per file
- [ ] Tests pass: `./gradlew :gbkt-core:test`
- [ ] Code compiles without warnings

---

## Questions?

- Open an issue for bugs or feature requests
- Check existing documentation in `context/` folder
- See `sample-*` projects for usage examples
