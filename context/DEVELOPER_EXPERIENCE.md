# Developer Experience

How to extend and develop the gbkt framework.

## Adding a New IR Node Type

1. Add sealed class/interface in `ir/` directory (in `CoreIR.kt` or a new domain-specific IR file)
2. Add emission case in the appropriate `codegen/` file

Example:
```kotlin
// ir/MyFeatureIR.kt (or add to existing IR file)
data class IRMyNewStatement(val param: String) : IRStatement

// codegen/features/MyFeatureCodegen.kt (or relevant codegen file)
is IRMyNewStatement -> emit("my_function(${stmt.param});")
```

## Adding a New DSL Construct

1. Create builder class in `builder/GameBuilder.kt` or a dedicated file (e.g., `ui/MenuBuilder.kt`, `rpg/Character.kt`)
2. Add DSL function that uses `RecordingContext.record()`
3. Add corresponding IR node if needed in `ir/` directory

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

## Testing

```bash
./gradlew :gbkt-core:test
```

## Dependencies

- Kotlin 2.3.0
- No runtime dependencies (pure Kotlin)
- Target: JVM (Java 21)

## Extending the RPG System

### Adding a New Stat Type

1. Add to `StatType` enum in `rpg/Stats.kt`:
```kotlin
enum class StatType {
    HP, SP, ATK, DEF, MATK, MDEF, AGL, ACC, EVA,
    LUCK  // New stat
}
```

2. Add builder method in `StatsBuilder`:
```kotlin
fun luck(value: Int) { stats[StatType.LUCK] = value }
```

3. Add codegen in `StatsCodegen.kt` to generate the C variable.

### Adding a New Ability Effect

1. Add IR node in `ir/AbilityIR.kt`:
```kotlin
data class IRDrainLife(val percent: Int) : IRStatement
```

2. Add DSL method in `AbilityExecuteScope`:
```kotlin
fun drainLife(percent: Int) {
    RecordingContext.current?.emit(IRDrainLife(percent))
}
```

3. Add codegen handler in `ActionExecutionCodegen.kt`:
```kotlin
is IRDrainLife -> {
    line("// Drain ${stmt.percent}% of damage as HP")
    line("_drain_hp(_caster_idx, _last_damage * ${stmt.percent} / 100);")
}
```

### Adding a New Status Effect Type

1. Define effect behavior in `StatusEffectBuilder`:
```kotlin
fun onCrit(init: StatusEffectScope.() -> Unit) {
    val recorder = StatementRecorder()
    RecordingContext.record(recorder, init)
    onCritStatements = recorder.statements
}
```

2. Add codegen in `StatusEffectCodegen.kt` to call the effect hook.

### Adding a New Monster AI Behavior

1. Add condition to `MonsterAIBuilder`:
```kotlin
fun whenPartyHasStatus(status: StatusEffectDefinition, init: MonsterAIScope.() -> Unit) {
    val recorder = StatementRecorder()
    val context = AIContext(...)  // Provide AI context
    val scope = MonsterAIScope(context)
    RecordingContext.record(recorder) { scope.init() }
    conditionalBehaviors.add(AICondition.PartyHasStatus(status, recorder.statements))
}
```

2. Add codegen in `BattleCodegen.kt` to check the condition during AI phase.

### Adding a New Turn Order Strategy

1. Add to `TurnOrderStrategy` enum in `rpg/TurnOrder.kt`:
```kotlin
enum class TurnOrderStrategy {
    PARTY_FIRST, SPEED_BASED, ROUND_ROBIN, RANDOM,
    CTB  // New: Conditional Turn-Based
}
```

2. Implement in `TurnOrderCodegen.kt`:
```kotlin
TurnOrderStrategy.CTB -> {
    // Generate CTB gauge and turn calculation
    line("// CTB turn order calculation")
    // ...
}
```

## Important Notes

- Factory functions are named `u8Var()` and `u16Var()` (not `u8()` and `u16()`) to avoid conflicts with value class constructors
- The `buttons` object (plural) provides button state; `Button` enum provides button constants

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

### Pattern 4: RPG Project Structure (Recommended for Turn-Based RPGs)

For RPGs with battles, characters, and dungeons, we recommend this structure:

```
src/main/kotlin/
├── MyRPG.kt                  # Entry point (~100-150 lines)
├── rpg/
│   ├── Characters.kt         # Character definitions
│   ├── Monsters.kt           # Monster definitions
│   ├── Abilities.kt          # Ability definitions
│   ├── Items.kt              # Item definitions
│   ├── StatusEffects.kt      # Status effect definitions
│   └── BattleSystem.kt       # Battle system configuration
├── world/
│   ├── Floors.kt             # Dungeon floor definitions
│   ├── Encounters.kt         # Encounter tables
│   ├── Flags.kt              # Game flags
│   └── MapObjects.kt         # Doors, chests, NPCs
└── scenes/
    ├── TitleScene.kt         # Title screen and menu
    ├── GameplayScene.kt      # Main exploration
    ├── BattleScene.kt        # Turn-based combat
    └── SaveLoadScene.kt      # Save/load UI
```

Example entry point:

```kotlin
// MyRPG.kt
val myRPG = gbGame("MyRPG") {
    // 1. Initialize RPG systems first (order matters)
    setupCharacters()
    setupMonsters()
    setupAbilities()
    setupItems()
    setupStatusEffects()
    setupBattleSystem()

    // 2. Initialize world
    setupFlags()
    setupFloors()
    setupEncounters()

    // 3. Create scenes
    val titleRef = createTitleScene()
    val gameplayRef = createGameplayScene()
    val battleRef = createBattleScene()
    val gameOverRef = createGameOverScene()

    // 4. Configure game flow
    val flow = gameFlow {
        titleScreen(titleRef)
        gameplay(gameplayRef)
        battle(battleRef)
        gameOver(gameOverRef)

        devMode {
            startAt(gameplayRef)  // Skip title in dev
        }
    }

    // 5. Set up pause menu (available from gameplay)
    setupPauseMenu(flow)

    start = flow.getStartScene() ?: titleRef
}

// rpg/Characters.kt
fun GameBuilder.setupCharacters() {
    val hero by character {
        name("Hero")
        stats { hp(100); sp(50); atk(15); def(10); agl(12) }
        level(1, maxLevel = 99)
    }
}

// rpg/BattleSystem.kt
fun GameBuilder.setupBattleSystem() {
    battleSystem("combat") {
        maxPartySize(4)
        maxEnemies(3)
        turnOrder(TurnOrderStrategy.SPEED_BASED)

        presentation {
            damageNumbers(true)
            screenShakeOnHit(4, 8)
            actionMessages(true)
        }

        onVictory { /* award exp, show results */ }
        onDefeat { scene(gameOverScene) }
    }
}

// world/Floors.kt
fun GameBuilder.setupFloors() {
    val floor1 by floor {
        name("Dungeon Level 1")
        defaultPosition(5, 5)
        map("entrance") { tileset("dungeon.png"); size(32, 32) }
        encounters {
            safeSteps(10)
            entry(weight = 30) { +goblin }
            entry(weight = 20) { +goblin; +goblin }
        }
    }
}

// scenes/TitleScene.kt
fun GameBuilder.createTitleScene(): SceneRef {
    val mainMenu = menu("main") {
        item("NEW GAME") { /* start new game */ }
        item("CONTINUE") { /* load save */ }
        item("OPTIONS") { /* options menu */ }
    }

    return scene("title") {
        enter {
            screen.showBackground()
            mainMenu.show()
        }
        every.frame {
            mainMenu.tick()
        }
    }
}
```

This structure ensures:
- Each file stays under 200 lines
- Clear separation of concerns
- Easy navigation in IDE
- Dependencies are explicit
- Testable in isolation

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
