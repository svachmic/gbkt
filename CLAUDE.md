# CLAUDE.md - gbkt Development Guide

## Project Overview

gbkt (Game Boy Kotlin) is a DSL framework that compiles Kotlin code to GBDK-compatible C for Game Boy development.

**Architecture:** Kotlin DSL → IR (Intermediate Representation) → C Code Generation

## Build & Run

```bash
# Build the project
./gradlew build

# Run tests
./gradlew :gbkt-core:test
```

## Tech Stack

- **Kotlin**: 2.3.0
- **Gradle**: 9.0
- **JVM Target**: 21

## GBDK Setup & ROM Building

### Prerequisites
- GBDK-2020 installed (https://github.com/gbdk-2020/gbdk-2020/releases)
- Set `GBDK_HOME` environment variable OR install to common path

### Build Commands
```bash
# Build ROM (uses GBDK lcc compiler)
./gradlew buildRom

# Run in emulator (auto-detects mGBA)
./gradlew runEmulator

# Generate C only (no compilation)
./gradlew generateC
```

### GBDK Detection
The plugin searches in order:
1. `gbkt { gbdkHome.set("/path") }` in build.gradle.kts
2. `GBDK_HOME` environment variable
3. Common paths: `/opt/gbdk-2020`, `~/gbdk-2020`, etc.

### Compiler Flags
- Standard: `-Wa-l -Wl-m -Wl-j`
- GBC Compatible: add `-Wm-yc`
- GBC Only: add `-Wm-yC`

### Output Files
- ROM: `build/gbkt/output/{name}.gb`
- Generated C: `build/gbkt/generated/main.c`
- Source map: `build/gbkt/generated/main.c.gbkt.map`
- Debug: `.map` and `.sym` files

### Common Errors
| Error | Cause | Fix |
|-------|-------|-----|
| `lcc not found` | GBDK not installed/detected | Set GBDK_HOME or install to common path |
| `undefined identifier` | Missing variable/function | Check spelling, ensure DSL generates it |
| `bank overflow` | Too much code/data | Split into multiple banks |

## Documentation Index

| Document | Use When You Need To... |
|----------|-------------------------|
| [CONTRIBUTING.md](CONTRIBUTING.md) | Understand code style, Kotlin conventions, DSL authoring guidelines, PR checklist |
| [context/ARCHITECTURE.md](context/ARCHITECTURE.md) | Understand IR nodes, data flow, codegen structure, module organization |
| [context/DSL_REFERENCE.md](context/DSL_REFERENCE.md) | Look up DSL syntax for variables, entities, scenes, dialogs, menus, saves, camera, collision, RPG |
| [context/DEVELOPER_EXPERIENCE.md](context/DEVELOPER_EXPERIENCE.md) | Add new IR nodes, DSL constructs, extend the framework |
| [context/LOCALIZATION.md](context/LOCALIZATION.md) | Localize game strings using GNU gettext .po files, bank allocation, table schema validation |
| [context/TOOLING.md](context/TOOLING.md) | Work with asset pipeline, GBC palettes, Gradle plugin, VSCode extension, IntelliJ plugin |
| [context/ROADMAP.md](context/ROADMAP.md) | Check what's implemented, planned, or in progress |

## Common Tasks Routing

| Task | Go To |
|------|-------|
| Understand Kotlin style conventions | CONTRIBUTING.md → "Kotlin Style Guide" |
| Add a new DSL keyword/construct | DEVELOPER_EXPERIENCE.md → "Adding DSL Constructs" |
| Add a new IR node type | DEVELOPER_EXPERIENCE.md → "Adding IR Nodes" |
| Understand the compilation pipeline | ARCHITECTURE.md → "Data Flow" |
| Fix/add asset processing | TOOLING.md → "Asset Pipeline" |
| Add VSCode/IntelliJ extension features | TOOLING.md → "VSCode Extension" / "IntelliJ Plugin" |
| Add/edit localized strings | LOCALIZATION.md → "PO File Format" |
| Migrate from strings.js | LOCALIZATION.md → "Migration from strings.js" |

## Quick DSL Examples

```kotlin
// Variables (u8Var, u16Var)
var score by u8Var(0)
score += 10

// Entities with sprites and position
val player by entity {
    position(80, 72)
    sprite(SpriteAsset("player.png")) {
        size = 8 x 16
        hitbox(0, 0, 8, 16)
    }
}

// D-pad input (uses .held for continuous movement)
whenever(dpad.right.held) { player.x += 2 }
whenever(dpad.left.held) { player.x -= 2 }

// Button input (has .held, .pressed, .released)
whenever(buttons.a.pressed) { jump() }
whenever(buttons.start.pressed) { scene(pauseScene) }

// Conditions with comparisons
whenever(player.x isAbove 160) { player.x set 0 }
whenever(score isAtLeast 100) { win() }

// Collision detection
whenever(player collidesWith enemy) { takeDamage() }

// Scenes with lifecycle (capture SceneRef for type-safe transitions)
lateinit var gameplayScene: SceneRef
gameplayScene = scene("gameplay") {
    enter { screen.showSprites() }
    every.frame { updatePlayer() }
    exit { screen.hideSprites() }
}
start = gameplayScene

// Camera with smooth follow and transitions
val camera = camera { smoothing = 0.15f }
camera.follow(player)
camera.shake(4, 10.frames)
camera.fadeOut(30.frames) { scene(gameoverScene) }

// Tweening with easing
tween(player.x, from = 0, to = 100, duration = 60.frames, easing = Easing.EASE_OUT)
```

## RPG Quick Reference

```kotlin
// Character with stats and leveling
val hero by character {
    name("Hero")
    stats { hp(100); sp(50); atk(15); def(10); matk(8); mdef(8); agl(12) }
    level(1, maxLevel = 99, expCurve = ExpCurve.STANDARD)
    onLevelUp { stats.hp += 10; stats.atk += 2 }
}

// Monster definitions
val goblin by monster {
    name("Goblin")
    tier(MonsterTier.COMMON)
    baseStats { hp(30); atk(8); def(5); agl(10) }
    ai {
        hpBelow(25) { flee() }
        basicAttack(context.randomTarget)
    }
    exp(15)
    drops { drop(herb, chance = 30) }
}

// Abilities with targeting and effects
val fireball by ability {
    name("Fireball")
    cost(sp = 8)
    targeting(TargetingMode.SINGLE_ENEMY)
    aspect(Aspect.FIRE)
    execute { target.damage(caster.matk * 2, Aspect.FIRE) }
}

// Items (consumable and equipment)
val potion by item {
    name("Potion")
    category(ItemCategory.CONSUMABLE)
    maxStack(10)
    buyPrice(50)
    onUse { target.heal(50) }
}

val ironSword by item {
    name("Iron Sword")
    category(ItemCategory.WEAPON)
    slot(EquipSlot.WEAPON)
    stats { atk(+10) }
}

// Turn-based battle system
val combat by battle("combat") {
    maxPartySize(4)
    maxEnemies(3)
    turnOrder(TurnOrderStrategy.SPEED_BASED)
    onState(BattleState.VICTORY) { awardExp(); awardDrops() }
    onState(BattleState.DEFEAT) { scene(gameoverScene) }
}

// Battle scene must call battleUpdate() every frame to drive state machine
scene("battle") {
    enter { initPartyFromClass(selectedClass); initBattleFromEncounter() }
    every.frame {
        battleUpdate(combat)  // Drives combat mechanics
        whenever(combatIsInState("COMBAT_STATE_VICTORY")) { /* handle victory */ }
    }
}

// Status effects
val poison by statusEffect {
    name("Poison")
    debuff()
    duration(5)
    damagePerTurn(10)
    stackMode(StackMode.REFRESH_DURATION)
}

// Dungeon floors with encounters
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

// Global flags for game state
val flags by flags {
    page("story") {
        flag("metElder")
        flag("hasKey")
        flag("defeatedBoss")
    }
}

// Inventory management
val inventory by inventory { maxSlots(16) }
inventory.add(potion, 3)
whenever(inventory.contains(potion)) { useItem(potion) }
```

## Exploration Quick Reference

```kotlin
// Exploration system for dungeon crawling
val dungeonExploration by exploration {
    tileSize(8)                          // 8x8 pixel tiles
    movementStyle(MovementStyle.GRID)     // Grid-based (or SMOOTH)
    movementSpeed(8)                      // Frames per tile

    // Resource gauges (torch, stamina, etc.)
    gauge("torch") {
        max(255)
        initial(255)
        decrementPerStep(1)
        onLow(50) { showMessage("Torch dimming...") }
        onDepleted { setFlag("torchOut") }
    }

    // Keys for locked doors/chests
    keys("magic_key") {
        max(99)
        initial(0)
    }

    startZone(floor1)

    // Callbacks
    onStep { checkEncounter("battle") }
    onInteract { tryInteractWithObject(state.currentFloor, state.playerX, state.playerY) }
    onBlocked { sounds.bump.play() }
}

// In gameplay scene - interact with map objects
whenever(buttons.a.pressed) {
    tryInteractWithObject(state.currentFloor, state.playerX, state.playerY)
}

// Refill torch from sconce or item
refillTorch(100)
```

## Game Flow Quick Reference

```kotlin
// Configure standard game scenes for navigation
val flow = gameFlow {
    titleScreen(titleScene)
    characterSelect(heroSelectScene)  // Optional
    gameplay(gameplayScene)
    battle(battleScene)
    pause(pauseScene)
    gameOver(gameOverScene)
    victory(victoryScene)

    // Dev mode - skip title during testing
    devMode {
        startAt(gameplayScene)
    }
}

// Use flow.getStartScene() to get initial scene
start = flow.getStartScene() ?: titleScene

// Check if scene is registered
if (flow.hasScene(GameFlowScene.BATTLE)) { ... }

// Get scene reference
val battleRef = flow.getScene(GameFlowScene.BATTLE)
```

## Localization Quick Reference

```po
# res/strings/en.po - Game strings in GNU gettext format
msgid ""
msgstr ""
"Language: en\n"
"Content-Type: text/plain; charset=UTF-8\n"

#. Ability names (pad to 13 chars for menu alignment)
msgctxt "ability"
msgid "fireball"
msgstr "Fireball     "

msgctxt "ability"
msgid "cure_wounds"
msgstr "Cure Wounds  "

#. Battle messages with placeholders
msgctxt "battle"
msgid "player_damage"
msgstr "You deal %damage damage!"

msgctxt "battle"
msgid "monster_attacks"
msgstr "%monster %c attacks!"
```

**Key Points:**
- `msgctxt` = namespace (maps to ROM bank groups, auto-allocated)
- `msgid` = string key (used in code as `str_ability_fireball`)
- `msgstr` = translated value (empty in .pot templates)
- Pad strings to fixed length for menu alignment
- Max 18 chars/line, 90 chars total for dialog boxes

See [context/LOCALIZATION.md](context/LOCALIZATION.md) for complete guide.

## Input API Distinction

**D-pad** (`dpad.right`, `dpad.left`, `dpad.up`, `dpad.down`):
- Has `.held`, `.pressed`, `.released` properties
- Can be used directly (implicit `.held` for backward compatibility)
- Use: `whenever(dpad.right) { ... }` or `whenever(dpad.right.held) { ... }`
- Edge-triggered: `whenever(dpad.left.pressed) { dash() }`
- Axis helpers: `playerX += dpad.x * speed` (returns -1, 0, or 1)

**Buttons** (`buttons.a`, `buttons.b`, `buttons.start`, `buttons.select`):
- Has `.held`, `.pressed`, `.released` properties
- Use: `whenever(buttons.a.pressed) { ... }` for edge-triggered
- Use: `whenever(buttons.b.held) { ... }` for continuous

**Special properties:**
- `dpad.any` / `dpad.none`: Check if any/no direction held
- `moving` / `stationary`: Aliases for dpad.any/none

## Key Source Locations

| Component | Path |
|-----------|------|
| DSL builders | `gbkt-core/src/main/kotlin/io/github/gbkt/core/dsl/` |
| IR nodes | `gbkt-core/src/main/kotlin/io/github/gbkt/core/ir/` |
| Code generation | `gbkt-core/src/main/kotlin/io/github/gbkt/core/codegen/` |
| ↳ Core codegen | `gbkt-core/src/main/kotlin/io/github/gbkt/core/codegen/core/` |
| ↳ RPG codegen | `gbkt-core/src/main/kotlin/io/github/gbkt/core/codegen/rpg/` |
| ↳ World codegen | `gbkt-core/src/main/kotlin/io/github/gbkt/core/codegen/world/` |
| Entity system | `gbkt-core/src/main/kotlin/io/github/gbkt/core/entity/` |
| RPG system | `gbkt-core/src/main/kotlin/io/github/gbkt/core/rpg/` |
| World/dungeon | `gbkt-core/src/main/kotlin/io/github/gbkt/core/world/` |
| Exploration | `gbkt-core/src/main/kotlin/io/github/gbkt/core/exploration/` |
| Game flow | `gbkt-core/src/main/kotlin/io/github/gbkt/core/flow/` |
| Input system | `gbkt-core/src/main/kotlin/io/github/gbkt/core/input/` |
| Collision system | `gbkt-core/src/main/kotlin/io/github/gbkt/core/collision/` |
| Gradle plugin | `gbkt-gradle-plugin/` |
| CLI tool | `gbkt-cli/` |
| VSCode extension | `vscode-extension/` |
| IntelliJ plugin | `gbkt-intellij-plugin/` |

See [context/DSL_REFERENCE.md](context/DSL_REFERENCE.md) for complete syntax reference.

## Module Documentation

Each module has a CLAUDE.md with detailed documentation:

| Module | Path | Purpose |
|--------|------|---------|
| IR | `gbkt-core/.../ir/CLAUDE.md` | 34 IR node types, expression wrappers, deepCopy patterns |
| DSL | `gbkt-core/.../dsl/CLAUDE.md` | Recording context, logic blocks, conditionals |
| Graphics | `gbkt-core/.../graphics/CLAUDE.md` | Sprites, animations, camera, tilemaps |
| Builder | `gbkt-core/.../builder/CLAUDE.md` | GameBuilder, feature registration |
| Scene | `gbkt-core/.../scene/CLAUDE.md` | Scene lifecycle, transitions |
| UI | `gbkt-core/.../ui/CLAUDE.md` | Menus, status bars |
| Input | `gbkt-core/.../input/CLAUDE.md` | D-pad, buttons, input buffering |
| Flow | `gbkt-core/.../flow/CLAUDE.md` | Game flow, pause/save menus |
| Test | `gbkt-core/.../test/CLAUDE.md` | SimulationContext, test DSL |
| Validation | `gbkt-core/.../validation/CLAUDE.md` | Array bounds, IR reference validation |
| Services | `gbkt-core/.../services/CLAUDE.md` | Dependency injection, mocks |
| Assets | `gbkt-core/.../assets/CLAUDE.md` | Type-safe asset references |
| Optimization | `gbkt-core/.../optimization/CLAUDE.md` | Asset analysis, suggestions |
| Exploration | `gbkt-core/.../exploration/CLAUDE.md` | Dungeon crawling system |
| Entity | `gbkt-core/.../entity/CLAUDE.md` | Entity/component system |
| RPG | `gbkt-core/.../rpg/CLAUDE.md` | Stats, battles, abilities |
| World | `gbkt-core/.../world/CLAUDE.md` | Floors, encounters, flags |
| Codegen | `gbkt-core/.../codegen/CLAUDE.md` | C code generation |
| Collision | `gbkt-core/.../collision/CLAUDE.md` | Collision detection |
| Combat | `gbkt-core/.../combat/CLAUDE.md` | Battle engine core |
| Movement | `gbkt-core/.../movement/CLAUDE.md` | Entity movement controller |

## Architectural Decisions

### Why These Detekt Exclusions?

The `detekt.yml` excludes certain rules for specific packages. These are deliberate architectural decisions:

| Package | Exclusion | Rationale |
|---------|-----------|-----------|
| `**/codegen/**` | LongMethod, TooManyFunctions | C code generation inherently produces large methods. Each IR node maps to C output. Breaking up would reduce readability. |
| `**/ir/**` | TooManyFunctions | `ExpressionWrapper.kt` has 60+ operator overloads for DSL ergonomics (`+`, `-`, `*`, `/`, `and`, `or`, etc.). This enables `playerX + 5` syntax. |
| `**/dsl/**` | UnusedParameter | Receiver pattern intentionally has "unused" `this` parameter. DSL functions like `whenever {}` need the receiver for scoping. |
| `**/rpg/**`, `**/entity/**` | LongParameterList | Domain models (Character, Monster, Battle) require comprehensive fields. RPG systems have many attributes by nature. |

### Globally Disabled Rules

| Rule | Rationale |
|------|-----------|
| `MagicNumber` | Game dev uses many constants (screen dimensions, sprite sizes, frame counts). Values are well-documented in context. |
| `UnusedPrivateMember` | DSL optional properties pattern - properties may be set but never read in Kotlin (used in IR/codegen). |

### Design Principles

1. **DSL Ergonomics Over Code Metrics**: The DSL is the user-facing API. Clean DSL syntax takes priority over internal code metrics.

2. **IR as Boundary**: IR nodes are the clean separation between DSL and codegen. Both sides may be complex internally.

3. **Domain-Driven Modeling**: RPG types model the problem domain (stats, abilities, effects). Long parameter lists reflect domain complexity, not poor design.

4. **Generated Code is Different**: Code generators produce output for machines. Human readability of generated C code matters less than correctness.
