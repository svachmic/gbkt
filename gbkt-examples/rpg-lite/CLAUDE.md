# RPG Lite — Developer Notes

## Build Commands

```bash
./gradlew :gbkt-examples:rpg-lite:generateC   # Kotlin DSL → C
./gradlew :gbkt-examples:rpg-lite:buildRom    # C → .gb ROM
./gradlew :gbkt-examples:rpg-lite:test        # Run tests
```

## Code Structure

`RpgLite.kt` (~258 lines) — single file, single `game("RPG Lite") { }` block:

1. Config (MBC5_RAM_BATTERY, 4 ROM banks)
2. Forward-declared SceneRefs (`titleRef`, `gameoverRef`)
3. Variables: `hp`, `gold`, `dungeonLevel`, `stepCount`
4. Sound effects: `hitSfx`, `coinSfx`, `winSfx`, `loseSfx`
5. Actor: `heroActor` (8x16 sprite)
6. RPG definitions: `hero` character, `slime`/`bat` monsters, `simpleBattle("combat")`
7. Scenes (defined in reverse order): `gameover` → `dungeon` → `town` → `title`

## Genre DSL Patterns

### RPG Package Import
```kotlin
import io.github.gbkt.rpg.dsl.battleUpdate
import io.github.gbkt.rpg.dsl.character
import io.github.gbkt.rpg.dsl.monster
import io.github.gbkt.rpg.dsl.simpleBattle
```

### simpleBattle Lifecycle
```kotlin
simpleBattle("combat") {
    party(hero)                     // player character
    encounter { +slime }            // encounter table entry
    encounter { +bat }
    onVictory { gold += 5; navigate("dungeon") }
    onDefeat  { navigate(gameoverRef) }
}
// In dungeon scene frame block:
whenever(stepCount isAtLeast 60) {
    battleUpdate("combat")          // drives the state machine each frame
}
```

### Forward-Declared SceneRefs
```kotlin
val titleRef = sceneRef("title")    // declared before title scene is defined
// ... later scene definitions navigate to titleRef without forward-reference error
```

## How to Modify

- **Add a monster:** Add `monster("name") { ... }` and add `encounter { +name }` to `simpleBattle`
- **Change encounter rate:** Adjust `60` in `whenever(stepCount isAtLeast 60)`
- **Add healing items:** Add item logic in town scene frame block
- **Add more floors:** Increment `dungeonLevel`, change tileset per level

## Dependencies

- `gbkt-core` — DSL, IR, scene/actor/sound system
- `gbkt-backend-gbdk` — Game Boy C code generation
- `gbkt-genre-rpg` — `character()`, `monster()`, `simpleBattle()`, `battleUpdate()`
