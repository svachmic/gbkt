# Dungeon — Developer Notes

## Build Commands

```bash
./gradlew :gbkt-examples:dungeon:generateC   # Kotlin DSL → C
./gradlew :gbkt-examples:dungeon:buildRom    # C → .gb ROM
./gradlew :gbkt-examples:dungeon:test        # Run tests
```

## Code Structure

`Dungeon.kt` (~339 lines) — single file, single `game("Dungeon") { }` block:

1. Config (MBC5_RAM_BATTERY, 4 ROM banks, 1 RAM bank)
2. Forward-declared SceneRefs (`titleRef`, `gameoverRef`)
3. Variables: `torchLevel`, `keys`, `steps`
4. Sound effects: `bumpSfx`, `stepSfx`, `keySfx`, `hitSfx`
5. Actor: `player` (8x16 sprite)
6. World: `floor1` zone with encounters, `flags("dungeon_flags")`
7. Systems: `camera`, `saveData`, `exploration` block
8. UI: `torchWarning` dialog, `gameHud` HUD panel
9. RPG: `adventurer` character, `bat`/`skeleton` monsters, `simpleBattle("combat")`
10. Scenes (reverse order): `gameover` → `battle` → `gameplay` → `title`

## Genre DSL Patterns

### Exploration Preset + Gauge
```kotlin
exploration {
    preset(ExplorationPreset.DUNGEON_CRAWLER)  // 8px grid, step tracking
    startZone(floor1)
    gauge("torch") {
        max(255); initial(255); decrementPerStep(1)
        onLow(50)    { navigate(gameoverRef) }  // warning threshold
        onDepleted   { navigate(gameoverRef) }  // torch out
    }
    keys("room_key") { max(9); initial(0) }
    onStep   { playSound(stepSfx) }
    onBlocked { playSound(bumpSfx) }
}
```

### Zone Encounter Table
```kotlin
val floor1 = zone("floor1") {
    size(16, 16)
    encounters {
        safeSteps(10)                          // no encounters for first 10 steps
        entry("combat", weight = 30)           // 30-weight entry → battle scene
    }
}
```

### Flag System
```kotlin
flags("dungeon_flags") {
    page("dungeon") {
        flag("bossDefeated")
        flag("gotTreasure")
        flag("foundKey")
    }
}
```

## How to Modify

- **Add a floor:** Define another `zone("floor2")` with a `transition { }` block in floor1
- **Change torch speed:** Adjust `decrementPerStep(1)` or the `and 3` modulo in gameplay frame
- **Add a new monster:** Add `monster("name") { ... }` and `encounter { +name }` in simpleBattle
- **Extend HUD:** Add more `bar()`, `number()`, or `icons()` elements to `gameHud`

## Dependencies

- `gbkt-core` — DSL, IR, exploration/zone/flags/UI system
- `gbkt-backend-gbdk` — Game Boy C code generation
- `gbkt-genre-rpg` — `character()`, `monster()`, `simpleBattle()`, `battleUpdate()`
