# CLAUDE.md - Labyrinth of the Dragon (gbkt Port)

## Overview

Kotlin DSL port of the D&D-style dungeon crawler RPG using the gbkt framework. Compiles to GBDK-compatible C code.

**Status:** DSL definitions complete, runtime wiring functional, UI polish complete, asset integration complete, visual parity complete (100%)

## Build & Run

```bash
# Build the port
./gradlew :LabyrinthOfTheDragon-port:build

# Generate C code (output in build/generated/)
./gradlew :LabyrinthOfTheDragon-port:run
```

## Project Structure

```
LabyrinthOfTheDragon-port/
├── src/main/kotlin/io/github/gbkt/examples/labyrinth/
│   ├── LabyrinthOfTheDragon.kt  # Main game definition
│   ├── GameConfig.kt            # All gameplay constants
│   ├── GameState.kt             # Runtime state variables
│   ├── Palettes.kt              # GBC palette definitions
│   ├── SaveSystem.kt            # Save/load functionality
│   ├── Sounds.kt                # Sound effect definitions
│   ├── StatusIcons.kt           # Status effect icon sprites
│   ├── scenes/                  # Game scenes
│   │   ├── Scenes.kt            # Scene coordinator
│   │   ├── TitleScene.kt        # Title screen
│   │   ├── HeroSelectScene.kt   # Class selection
│   │   ├── GameplayScene.kt     # Dungeon exploration
│   │   ├── BattleScene.kt       # Combat system
│   │   ├── PauseScene.kt        # Pause menu
│   │   ├── GameOverScene.kt     # Game over
│   │   └── VictoryScene.kt      # Victory screen
│   ├── rpg/                     # RPG mechanics
│   │   ├── Abilities.kt         # Ability coordinator
│   │   ├── abilities/           # Class-specific abilities
│   │   │   ├── DruidAbilities.kt
│   │   │   ├── FighterAbilities.kt
│   │   │   ├── MonkAbilities.kt
│   │   │   └── SorcererAbilities.kt
│   │   ├── Characters.kt        # 4 playable classes
│   │   ├── Monsters.kt          # 12 monster definitions
│   │   ├── Items.kt             # 8 consumable items
│   │   └── CombatSystem.kt      # Combat formulas
│   └── world/                   # Dungeon structure
│       ├── Floors.kt            # Floor coordinator
│       └── floors/              # Individual floors
│           ├── Floor1Entrance.kt
│           ├── Floor2GoblinWarrens.kt
│           └── ... (Floor3-8)
├── res/                         # Assets (see ASSETS.md)
└── ASSETS.md                    # Asset integration guide
```

## Key Files

| File | Purpose | LOC |
|------|---------|-----|
| `LabyrinthOfTheDragon.kt` | Main entry, wires everything together | ~400 |
| `GameConfig.kt` | All constants (tile size, stats, menu states) | ~170 |
| `Palettes.kt` | GBC palette definitions (floor, battle, death, title) | ~320 |
| `StatusIcons.kt` | Status effect icon sprite definitions | ~80 |
| `BattleScene.kt` | Combat state machine and menu handling | ~250 |
| `GameplayScene.kt` | Exploration movement and interactions | ~90 |
| `Monsters.kt` | All 12 monster definitions with stats | ~300 |
| `Characters.kt` | 4 character classes with base stats | ~150 |

## Game Constants (from GameConfig.kt)

| Constant | Value | Description |
|----------|-------|-------------|
| `TILE_SIZE` | 8 | Pixels per tile |
| `FLOOR_COUNT` | 8 | Total dungeon floors |
| `CLASS_COUNT` | 4 | Playable classes |
| `TORCH_MAX` | 255 | Max torch fuel |
| `SAVE_SLOTS` | 3 | Number of save slots |

## DSL Patterns Used

```kotlin
// Game entry point
val labyrinthOfTheDragon = gbGame("LabyrinthDragon") {
    // ... game definition
}

// Scene definition
scene("battle") {
    enter { /* on enter */ }
    every.frame { /* per frame logic */ }
    exit { /* on exit */ }
}

// Monster definition
val goblin by monster {
    name("Goblin")
    tier(MonsterTier.COMMON)
    baseStats { hp(30); atk(8); def(5) }
    exp(15)
}

// Floor definition with encounters
val floor1 by floor {
    name("Dungeon Level 1")
    encounters {
        safeSteps(10)
        entry(weight = 40) { +monsters.kobold }
    }
}

// Flag operations
gameFlags.getFlag("metElder")?.set()
```

## Combat System

**States:** `INIT` → `PLAYER_TURN` → `TARGET_SELECT` → `EXECUTE` → `ENEMY_TURN` → cycle or `VICTORY`/`DEFEAT`

**Key DSL functions:**
- `battleUpdate(system)` - Update battle state machine (call every frame)
- `initPartyFromClass(classId)` - Setup party from selected class
- `initBattleFromEncounter()` - Load enemies from encounter
- `confirmCombatTarget(index)` - Confirm target selection
- `selectCombatItem(index)` - Select item to use
- `transitionToCombatState(state)` - Change combat state
- `combatIsInState(state)` - Check current state

## Exploration System

**Key DSL functions:**
- `tryInteractWithObject(floor, x, y)` - Interact with map objects
- `refillTorch(value)` - Restore torch fuel
- `showMessage(text)` - Display message box

## Flags System

Organized into 3 pages (256 total flags):
- `chests_1_4` - Chest flags for floors 1-4
- `chests_5_8` - Chest flags for floors 5-8
- `world` - Door states and story progression

## Original Game Reference

The original C implementation is in `../LabyrinthOfTheDragon/`. Use it to:
- Verify exact game mechanics
- Extract missing constants
- Reference encounter tables
- Check map object positions

## Known TODOs

**Framework Work Completed:**
- [x] Battle presentation (dialog integration, damage numbers)
- [x] Status effect application (`_status_apply()` with proper durations)
- [x] Save/load C function invocation
- [x] DoT/HoT tick logic in battle update
- [x] preventsAction() wiring for turn skip
- [x] Combatant can_act check function
- [x] Exploration encounter triggering
- [x] Combat formula expressions

**Framework Work Remaining:**
- [x] Sprite position interpolation during movement (DONE: playerSprite option in exploration DSL)

**Port Work Completed:**
- [x] Floors 2-8 map object definitions
- [x] All 24 abilities wired with status effects
- [x] Items using status effects (Regen, ATK Up, DEF Up, Haste)
- [x] Level-based encounter tables on all 8 floors
- [x] StatusEffects class with 17 effect definitions

**Port Work Completed (UI Polish):**
- [x] Title screen menu navigation with sounds
- [x] Battle menu cursor display and sounds
- [x] Sound effects wired to combat system

**Port Work Completed (Assets):**
- [x] Asset integration (sprites, tiles, maps) - 13 sprites converted (1 hero + 12 monsters)

**Port Work Completed (Visual Parity):**
- [x] Per-floor palettes (floor1, floor2 with tiles/chests/special)
- [x] Battle palettes (background, HP bar normal/critical, buff/debuff)
- [x] Monster death fade palettes (6 steps to white)
- [x] Title screen palettes (dragon face/body, fire, smoke, press start)
- [x] Status effect icons (16 entity sprites for player + 3 monsters)
- [x] Title fire animation timing (6 frames per step, documented sequence)
- [x] Confirmed SFX-only audio (no background music in original)

## Visual System

### Palettes (Palettes.kt)

| Category | Palettes | Usage |
|----------|----------|-------|
| Floor 1 | `floor1Palette0-2` | Stone tiles, chests, special |
| Floor 2 | `floor2Palette0-2` | Earth tiles, walls, chests |
| Battle | `battleBg0`, `battleMonster1` | Background, monster slots |
| HP Bar | `battleHpNormal`, `battleHpCritical` | Green (≤33% = red) |
| Status | `battleBuff`, `battleDebuff` | Palette 6 (green), 7 (red) |
| Death | `deathFade0-5` | 6-step fade to white |
| Title | `titleDragonFace/Body`, `titleFire/Smoke` | Title screen |

### Status Effect Icons (StatusIcons.kt)

16 entity sprites for status display (4 per combatant):
- Player: `playerIcon1-4`
- Monster 1-3: `monster1Icon1-4`, etc.
- Tiles 0x60-0x72, Palette 6 (buff) / 7 (debuff)

### Title Animation (TitleAnimationConfig)

```kotlin
FIRE_FRAME_DELAY = 6       // Frames per fire step
DRAGON_PALETTE_DELAY = 3   // Dragon flicker rate
SMOKE_FRAME_DELAY = 6      // Frames per smoke step
FIRE_FRAMES = [0,1,2,3,4,2,3,4,2,3,4,2,3,4,3,2,1,0]
```

### Audio

**No background music** - The original game uses SFX only. All sound effects are defined in `Sounds.kt`.
