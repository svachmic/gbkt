# Developer Experience

How to extend and develop the gbkt framework.

## Adding a New IR Node Type

1. Add sealed class/interface in `Core.kt` under `IRStatement` or `IRExpression`
2. Add emission case in `CodeGenerator.kt`

Example:
```kotlin
// Core.kt
data class IRMyNewStatement(val param: String) : IRStatement

// CodeGenerator.kt - in generateStatement()
is IRMyNewStatement -> emit("my_function(${stmt.param});")
```

## Adding a New DSL Construct

1. Create builder class in `Game.kt` (or dedicated file like `Menu.kt`, `Dialog.kt`)
2. Add DSL function that uses `RecordingContext.record()`
3. Add corresponding IR node if needed

Example:
```kotlin
// Builder class
class MyFeatureBuilder(private val name: String) {
    var option: Int = 0
    internal fun build() = MyFeatureDefinition(name, option)
}

// DSL function in GameBuilder
fun myFeature(name: String, init: MyFeatureBuilder.() -> Unit): MyFeatureHandle {
    val builder = MyFeatureBuilder(name)
    builder.init()
    val definition = builder.build()
    _myFeatures.add(definition)
    return MyFeatureHandle(definition)
}
```

## Adding Platform-Specific Code

1. Add `expect` declaration in `commonMain`
2. Add `actual` implementations in `jvmMain` and `nativeMain`

Example:
```kotlin
// commonMain
expect fun platformSpecificOperation(): String

// jvmMain
actual fun platformSpecificOperation(): String = "JVM"

// nativeMain
actual fun platformSpecificOperation(): String = "Native"
```

## Testing

```bash
./gradlew :gbkt-core:test
```

## Dependencies

- Kotlin 2.3.0 (multiplatform)
- No runtime dependencies (pure Kotlin)
- Targets: JVM (Java 21), Linux x64, macOS x64/ARM64

## Important Notes

- Factory functions are named `u8Var()` and `u16Var()` (not `u8()` and `u16()`) to avoid conflicts with value class constructors
- The `buttons` object (plural) provides button state; `Button` enum provides button constants
- Expect/actual classes show beta warnings - can suppress with `-Xexpect-actual-classes` compiler flag

---

## Organizing Large Games

As your game grows beyond a few hundred lines, consider splitting code across multiple files. Here are suggested patterns - pick what fits your project best.

### When to Consider Multi-File Organization

- Your main game file exceeds 300-400 lines
- You have multiple distinct systems (player, enemies, UI, levels)
- Multiple people are contributing to the game
- You want clearer separation of concerns

### Pattern 1: Module Extensions

Organize by game systems using extension functions on `GameBuilder`:

```
src/main/kotlin/
├── MyGame.kt           # Entry point with gbGame()
├── modules/
│   ├── PlayerModule.kt # Extension: GameBuilder.setupPlayerModule()
│   └── EnemyModule.kt  # Extension: GameBuilder.setupEnemyModule()
├── scenes/
│   ├── TitleScene.kt   # Extension: GameBuilder.createTitleScene()
│   └── GameplayScene.kt
└── entities/
    ├── PlayerEntity.kt # Factory: GameBuilder.createPlayer()
    └── EnemyEntity.kt
```

```kotlin
// MyGame.kt - Entry point
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
    // Module-specific setup (palettes, global config)
    val playerPalette = palette("player") {
        colors(0x000000, 0x555555, 0xAAAAAA, 0xFFFFFF)
    }
}

fun GameBuilder.createPlayer(x: Int = 80, y: Int = 72): Entity {
    val player by entity {
        position(x, y)
        sprite(SpriteAsset("player.png")) { size = 8 x 16 }
        physics { gravity = 0.5f }
    }
    return player
}

// scenes/GameplayScene.kt
fun GameBuilder.createGameplayScene(): SceneRef {
    val player = createPlayer()
    val enemies = createEnemyPool()

    return scene("gameplay") {
        enter {
            screen.showSprites()
            camera.follow(player)
        }
        every.frame {
            // Game logic here
        }
    }
}
```

### Pattern 2: Scene-Per-File

Each scene lives in its own file:

```kotlin
// scenes/TitleScene.kt
fun GameBuilder.createTitleScene(): SceneRef = scene("title") {
    enter {
        screen.clear()
        text.print(6, 8, "MY GAME")
        text.print(4, 12, "PRESS START")
    }

    whenever(buttons.start.pressed) {
        scene(gameplayScene)
    }
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

To avoid name collisions in large games:

| Element | Convention | Example |
|---------|------------|---------|
| Variables | Prefix with domain | `player_x`, `enemy_count`, `ui_menuIndex` |
| Entities | Descriptive unique names | `mainPlayer`, `bossEnemy`, `npcElder` |
| Scenes | Domain-specific | `title`, `level1_gameplay`, `pause_menu` |

### Best Practices

1. **Document dependencies**: Add comments noting which modules a scene depends on
2. **Initialize in order**: Set up modules before scenes that use them
3. **Use explicit parameters**: Pass dependencies as function parameters rather than relying on implicit globals
4. **Keep entry point clean**: The main `gbGame()` block should be a high-level overview

### Example: sample-modular Project

See `sample-modular/` for a complete example of multi-file game organization.
