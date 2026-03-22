# CLAUDE.md - Labyrinth of the Dragon (gbkt V2 Port)

## Overview

Kotlin DSL port of the D&D-style dungeon crawler RPG using the gbkt framework. Compiles to GBDK-compatible C code via the V2 IR pipeline.

**Status:** V2 DSL port complete — all systems ported to idiomatic Kotlin DSL. Port build passes (72 tests, 0 failures). C generation produces 5712 lines across 4 files. buildRom blocked pending framework gap resolution (Phase 07).

## Build & Run

```bash
# Build the port (includes tests)
./gradlew :LabyrinthOfTheDragon-port:build

# Generate C code
./gradlew :LabyrinthOfTheDragon-port:generateC
# Output: build/gbkt/generated/main.c (4466 lines), bank1.c (273 lines), game.h (959 lines)

# Build ROM (requires GBDK installed, set GBDK_HOME)
./gradlew :LabyrinthOfTheDragon-port:buildRom

# Build with locale support
./gradlew :LabyrinthOfTheDragon-port:buildRom -Pgbkt.locale=en
./gradlew :LabyrinthOfTheDragon-port:buildRom -Pgbkt.locale=cs

# Verify full project still builds
./gradlew build
```

## Project Structure

```
LabyrinthOfTheDragon-port/
├── src/main/kotlin/io/github/gbkt/examples/labyrinth/
│   ├── LabyrinthOfTheDragon.kt  # Top-level val entry point + game wiring (~220 lines)
│   ├── GameConfig.kt            # All gameplay constants (~170 lines)
│   ├── GameState.kt             # Runtime state variables (~100 lines)
│   ├── Palettes.kt              # 35 GBC palette definitions + register() (~800 lines)
│   ├── SaveSystem.kt            # 3-slot SRAM save with checksum (~60 lines)
│   ├── Sounds.kt                # 31 SFX definitions (~180 lines)
│   ├── StatusIcons.kt           # 16 entity sprites for buff/debuff display (~90 lines)
│   ├── scenes/
│   │   ├── Scenes.kt            # SceneRef declarations + register coordinator
│   │   ├── TitleScene.kt        # Title screen with menu and load prompt
│   │   ├── HeroSelectScene.kt   # 4-class selection with stats preview
│   │   ├── GameplayScene.kt     # Grid-based dungeon exploration
│   │   ├── BattleScene.kt       # Turn-based combat state machine (~270 lines)
│   │   ├── PauseScene.kt        # Pause/save/load/stats overlay
│   │   ├── GameOverScene.kt     # Death screen
│   │   └── VictoryScene.kt      # Victory/credits
│   ├── rpg/
│   │   ├── Abilities.kt         # Coordinator — calls defineAbilities(statusEffects)
│   │   ├── abilities/
│   │   │   ├── DruidAbilities.kt    # 6 druid abilities
│   │   │   ├── FighterAbilities.kt  # 6 fighter abilities
│   │   │   ├── MonkAbilities.kt     # 6 monk abilities
│   │   │   └── SorcererAbilities.kt # 6 sorcerer abilities
│   │   ├── Characters.kt        # 4 playable classes + debug class (~150 lines)
│   │   ├── Monsters.kt          # 12 monster definitions with AI (~300 lines)
│   │   ├── Items.kt             # 8 consumables with status effects (~120 lines)
│   │   ├── StatusEffects.kt     # 13 active status effects (~140 lines)
│   │   └── CombatSystem.kt      # Battle config and registration (~90 lines)
│   └── world/
│       ├── Floors.kt            # registerFloors() coordinator
│       └── floors/
│           ├── Floor1Entrance.kt
│           ├── Floor2GoblinWarrens.kt
│           ├── Floor3CatacombsOfTheDead.kt
│           ├── Floor4ForgottenHalls.kt
│           ├── Floor5CrypticDepths.kt
│           ├── Floor6TwistingTunnels.kt
│           ├── Floor7AbyssalChambers.kt
│           └── Floor8DragonLair.kt
├── res/                         # Game assets processed by gbkt pipeline
│   ├── sprites/                 # Hero sprite (8x16)
│   ├── monsters/                # 12 monster sprites
│   ├── tiles/                   # Tileset PNGs (dungeon, battle, objects, font)
│   ├── tilemaps/floors/         # Compiled .tilemap files (8 floors)
│   └── strings/                 # Localization PO files
│       ├── en.po                # English strings
│       └── cs.po                # Czech strings
├── source-maps/                 # Source TMX files (not processed by pipeline)
├── game-data/                   # Schema files (not processed by pipeline)
└── ASSETS.md                    # Asset integration guide
```

## Key Files

| File | Purpose | LOC |
|------|---------|-----|
| `LabyrinthOfTheDragon.kt` | Top-level val + 15-step wiring (config → state → sounds → palettes → icons → statusEffects → items → characters → monsters → abilities → combat → save → floors → exploration → scenes) | ~220 |
| `GameConfig.kt` | All constants (tile size, stats, menu states, floor counts) | ~170 |
| `Palettes.kt` | 35 GBC palette definitions + `register()` wiring into GameBuilder | ~800 |
| `BattleScene.kt` | Combat state machine (PLAYER_TURN → TARGET_SELECT → EXECUTE → ENEMY_TURN) | ~270 |
| `Monsters.kt` | 12 monsters (Kobold through Dragon) with AI behavior trees | ~300 |
| `Characters.kt` | 4 classes (Druid, Fighter, Monk, Sorcerer) with base stats + level curves | ~150 |
| `StatusEffects.kt` | 13 effects (DEBUFF_BLIND through BUFF_DEF_UP) matching original stats.h | ~140 |

## Game Constants (from GameConfig.kt)

| Constant | Value | Description |
|----------|-------|-------------|
| `TILE_SIZE` | 8 | Pixels per tile |
| `FLOOR_COUNT` | 8 | Total dungeon floors |
| `CLASS_COUNT` | 4 | Playable classes |
| `TORCH_MAX` | 255 | Max torch fuel |
| `TORCH_LOW_THRESHOLD` | 50 | "Torch dimming" warning threshold |
| `SAVE_SLOTS` | 3 | Number of save slots (3 x 8KB SRAM) |

## V2 DSL Patterns

```kotlin
// Game entry point — top-level val for GenerateCTask reflection
val labyrinthOfTheDragon = LabyrinthOfTheDragon.create()

object LabyrinthOfTheDragon {
    @Suppress("LongMethod")
    fun create() = game("LabyrinthDragon") {
        config { cartridge = "MBC5_RAM_BATTERY"; romBanks = 32; ramBanks = 4 }
        val state = GameState.register(this)
        val sounds = defineSounds()
        Palettes.register(this)  // 35 palettes registered into GameIR.palettes
        // ... 15 registration steps total
        Scenes.register(this, sounds, combatSystem, state)
        start = Scenes.titleRef.id
    }
}

// Scene definition — reverse order to resolve SceneRefs without forward references
val gameoverRef = sceneRef("gameover")  // forward ref for cycles
scene("gameover") {
    enter { hideSprites(); clear(); print("YOU HAVE DIED", ...) }
    frame { whenever(buttons.start.pressed) { navigate(Scenes.titleRef) } }
}

// Type-safe navigation — no magic strings
navigate(Scenes.titleRef)       // SceneRef (not string)
navigate(Scenes.gameplayRef)    // All navigate() calls use typed refs

// GBC palette — registered with builder for C data array generation
Palettes.register(builder)  // emits `const palette_color_t floor1Palette0_pal[4] = {...};`
scene("gameplay") {
    enter { palette(Palettes.floor1Palette0) }  // type-safe, emits set_bkg_palette()
}

// Zone with encounter table and NPCs
zone("floor1") {
    size(32, 32)
    encounters { safeSteps(10); entry(weight = 40) { +kobold } }
    npc("chest1_gold50") { position(5, 3); onInteract { /* chest logic */ } }
    transition { to("floor2"); edge(TransitionEdge.EAST); entryX(0); entryY(15) }
}
```

## Combat System

**States:** `INIT` → `PLAYER_TURN` → `TARGET_SELECT` → `EXECUTE` → `ENEMY_TURN` → cycle or `VICTORY`/`DEFEAT`

**Combat DSL registration:**
```kotlin
val combatSystem = registerCombat(characters, monsters)
// In battle scene:
frame { trigger_combat() }  // drives state machine each frame
```

**State constants** (from `CombatStates.*`):
- `CombatStates.PLAYER_TURN` → maps to `_COMBAT_STATE_PLAYER_TURN`
- `CombatStates.VICTORY`, `CombatStates.DEFEAT`, `CombatStates.FLEEING`

## Exploration System

Grid-based dungeon crawl with torch gauge and magic key system:

```kotlin
exploration {
    preset(ExplorationPreset.DUNGEON_CRAWLER)
    startZone(floors.floor1)
    gauge("torch") {
        max(GameConfig.TORCH_MAX)
        decrementPerStep(1)
        onLow(GameConfig.TORCH_LOW_THRESHOLD) { print("Torch dimming...") }
        onDepleted { navigate(Scenes.gameOverRef) }
    }
    keys("magic_key") { max(99); initial(0) }
}
```

## Flags System

Organized into 3 pages for 8 floors of dungeon state:
- `chests_1_4` — Chest flags for floors 1-4 (chest opened/looted)
- `chests_5_8` — Chest flags for floors 5-8
- `world` — Door states, boss defeated flags, story progression

## Localization

Two locales supported via GNU gettext `.po` files:

```bash
./gradlew :LabyrinthOfTheDragon-port:buildRom -Pgbkt.locale=en  # English
./gradlew :LabyrinthOfTheDragon-port:buildRom -Pgbkt.locale=cs  # Czech
```

String files: `res/strings/en.po` and `res/strings/cs.po`

Ability names padded to fixed length for menu alignment (13 chars max):
```po
msgctxt "ability"
msgid "fireball"
msgstr "Fireball     "  # padded to 13 chars
```

## Framework Changes (Added in This Phase)

The following framework APIs were added or fixed during the LotD V2 port:

| Change | Module | Reason |
|--------|--------|--------|
| `GameBuilder.registerPalette()` made public | `gbkt-lang` | Enable palette registry pattern outside `by palette {}` |
| `<gb/cgb.h>` added to `bank1.c` when SetPalette ops present | `gbkt-backend-gbdk` | GBC palette API requires cgb.h |
| `const palette_color_t {name}_pal[4]` data arrays emitted in `main.c` | `gbkt-backend-gbdk` | Palette data was missing from codegen |
| `extern const palette_color_t {name}_pal[4]` added to `game.h` | `gbkt-backend-gbdk` | Extern visibility for banked scene code |
| `<gb/cgb.h>` added to `game.h` when palettes present | `gbkt-backend-gbdk` | `palette_color_t` type definition |

## V2 vs V1 Improvements

| Aspect | V1 (Original C) | V2 (Kotlin DSL) |
|--------|-----------------|-----------------|
| Type safety | Magic strings everywhere | Typed SceneRef, FlagRef, palette refs |
| Palette registration | Manual `set_bkg_palette()` calls | Declarative `Palettes.register()` + `palette()` in scene enter |
| Scene navigation | `navigate("gameover")` | `navigate(Scenes.gameOverRef)` |
| Encounter tables | Hardcoded arrays in C | DSL `encounters { entry(weight=40) { +kobold } }` |
| Status effects | Enum integers | Named `StatusEffectDef` objects with typed refs |
| Save system | Manual SRAM pointer arithmetic | `SaveSystem.register()` DSL |

## Original Game Reference

The original C implementation is in `../LabyrinthOfTheDragon/`. Use it to:
- Verify exact game mechanics (damage formulas, encounter probabilities)
- Extract missing constants (check `core.h`, `player.h`, `battle.h`)
- Reference encounter tables (check `floor*.c` files)
- Check map object positions (check `floor*_objects.c` files)

## V2 Port Completion Status

**Phase 06.11 complete (all 19 plans):**
- [x] Research + architecture (Plans 01-04)
- [x] RPG foundations: abilities, monsters, characters, status effects, items (Plans 05-09)
- [x] World: 8 dungeon floors with encounters, chests, NPCs (Plans 10-11)
- [x] Exploration system: torch gauge, magic keys, zone transitions (Plan 12)
- [x] Scene wiring: all 7 scenes fully implemented (Plans 13-14)
- [x] Visual parity: 35 GBC palettes, status icons, title animation (Plan 15)
- [x] Integration: build passes, C generated (5712 lines), zero magic strings (Plan 18)

**Phase 07 (UAT) remaining:**
- [ ] buildRom blocked by framework gaps: RPG system variable emission, simpleBattle constant generation, flag variable declaration in codegen
- [ ] Runtime behavior verification in mGBA emulator
- [ ] Gameplay balance verification against original
