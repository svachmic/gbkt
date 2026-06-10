# Contributing to gbkt

Thank you for your interest in contributing to gbkt! This document provides guidelines for contributing code, style requirements, and best practices.

## Getting Started

```bash
# Build the project
./gradlew build

# Run tests
./gradlew :gbkt-core:test
```

## Project Structure

gbkt is a 20-module project organized in layers:

```
gbkt/
├── gbkt-ir/              # IR types (Expr, ScriptOp, GameIR — leaf module, zero gbkt deps)
├── gbkt-lang/            # DSL builders (GameBuilder, ScriptBuilder, variable delegates)
├── gbkt-engine/, gbkt-world/  # Engine runtime + world/exploration types
├── gbkt-core/            # Aggregator: re-exports the above + asset pipeline & test infra
├── gbkt-backend-api/     # Backend contract interface
├── gbkt-backend-gbdk/    # Game Boy/GBC code generation
├── gbkt-analysis/        # Static analysis passes
├── gbkt-genre-*/         # Genre plugins (rpg, platformer, puzzle, sport)
├── gbkt-{emulator,test,mcp-server}/  # Agent-testing stack
├── gbkt-{gradle-plugin,cli,intellij-plugin}/  # Tooling
└── context/              # Documentation
```

See [context/ARCHITECTURE.md](context/ARCHITECTURE.md) for the dependency graph and the full module table in [CLAUDE.md](CLAUDE.md).

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
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.ScriptBuilder
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

**Module dependency rules:**
- `gbkt-ir` ← Pure IR data classes, zero gbkt dependencies (enforced by `validateModuleBoundaries`)
- `gbkt-lang` ← DSL builders, depends only on `gbkt-ir`
- `gbkt-engine`, `gbkt-world` ← Domain types, depend on `gbkt-ir`/`gbkt-lang`
- `gbkt-core` ← Aggregator, re-exports the above
- Backends (`gbkt-backend-gbdk`) and genre plugins depend on core — never the other way around

**Note:** Code generation (`codegen/`) is in `gbkt-backend-gbdk`, not `gbkt-core`.

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
class ActorBuilder(private val actorName: String) {
    // ...
}
```

This prevents accidental access to outer scope receivers:

```kotlin
actor {
    sprite(asset("sprites/player.png")) {
        // Without @GbktDsl, you could accidentally call actor methods here
        // With @GbktDsl, the compiler prevents this
        position(0, 0)  // Error: position is not in scope
    }
}
```

### PropertyDelegateProvider

Use `PropertyDelegateProvider` when registration must happen at declaration time:

```kotlin
class ActorDelegate(
    private val gameBuilder: GameBuilder,
    private val init: ActorBuilder.() -> Unit
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, ActorRef>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>
    ): ReadOnlyProperty<Any?, ActorRef> {
        // Build and register immediately when delegate is created;
        // the actor name is inferred from the Kotlin property name
        val actor = ActorBuilder(property.name).apply(init).build()
        gameBuilder.registerActor(actor)
        return ReadOnlyProperty { _, _ -> ActorRef(actor.name) }
    }
}
```

This is Project Rule #1 in practice: names come from property delegates (or lambda parameters), never duplicated as String parameters.

### Builder Contexts

DSL recording uses two thread-local contexts (see `gbkt-lang`):

- **`GameBuilderContext`** holds the active `GameBuilder` so variable/array delegates (`u8Var`, `u8Array`, ...) can auto-register definitions.
- **`ScriptBuilderContext`** holds the active `ScriptBuilder` so operator extensions (`score += 10`, `ball.x set 80`) emit `ScriptOp` nodes into the enclosing `enter { }` / `frame { }` block.

When capturing a nested DSL block as a script list, run it against a fresh `ScriptBuilder` via the context idiom (`with(builder) { block() }` sets the thread-local and restores the previous value in a `finally` block — nested contexts are supported).

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
val myGame = game("MyGame") {
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
    // Module-specific setup (palettes, global config)
}

fun GameBuilder.createPlayer(x: Int, y: Int): ActorRef {
    val player by actor {
        position(x, y)
        sprite(asset("sprites/player.png")) { size(8, 16) }
    }
    return player
}

// scenes/GameplayScene.kt
fun GameBuilder.createGameplayScene(): SceneRef {
    val player = createPlayer(80, 72)

    return scene("gameplay") {
        enter { showSprites() }
        frame { /* ... */ }
    }
}
```

### Pattern 2: Scene-Per-File

Each scene in its own file:

```kotlin
// scenes/TitleScene.kt
fun GameBuilder.createTitleScene(): SceneRef = scene("title") {
    enter { /* ... */ }
    frame { /* ... */ }
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
| Actors | Descriptive unique name | `mainPlayer`, `bossEnemy` |
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
- Check existing documentation in `context/` folder — [DSL_REFERENCE.md](context/DSL_REFERENCE.md) for syntax, [ARCHITECTURE.md](context/ARCHITECTURE.md) for the pipeline and extension guide
- See [CLAUDE.md](CLAUDE.md) for build commands and the documentation index
