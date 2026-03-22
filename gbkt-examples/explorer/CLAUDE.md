# Explorer — Developer Notes

## Build Commands

```bash
./gradlew :gbkt-examples:explorer:generateC   # Kotlin DSL → C
./gradlew :gbkt-examples:explorer:buildRom    # C → .gb ROM
./gradlew :gbkt-examples:explorer:test        # Run tests
```

## Code Structure

`ExplorerV2.kt` (~418 lines) — the most complex example, single `game("Explorer") { }` block:

1. Config (MBC5_RAM_BATTERY, 4 ROM banks, 1 RAM bank)
2. Forward-declared SceneRefs: `titleRef`, `gameplayRef`, `gameoverRef`
3. Variables: `hp`, `stepCount`, `keys`, `torchLevel`, `level`
4. Sound effects: `step`, `door`, `save`, `hit`
5. Actor: `player` (8x16) with `entityCollision { mode(BLOCK_AND_TRIGGER) }`
6. World: `floor1` (encounters + transition east), `floor2` (boss chamber, safeZone)
7. Flags: `page("story")` (metElder, hasKey, defeatedBoss), `page("exploration")`
8. Systems: `camera`, `saveData`, `exploration` with gauge + keys + callbacks
9. UI: `torchWarning` dialog, `pauseMenu`, `gameHud` HUD
10. RPG: `hero`, `goblin`, `rat`, `simpleBattle("combat")`
11. Scenes (reverse): `gameover` → `combat_scene` → `pause` → `gameplay` → `title`

## Key DSL Patterns

### Multi-Floor Zone Transitions
```kotlin
val floor1 = zone("floor1") {
    size(32, 32)
    encounters { safeSteps(10); entry("combat", weight = 30) }
    transition {
        to("floor2"); edge(TransitionEdge.EAST)
        entryX(0); entryY(15)
    }
}
zone("floor2") {
    size(16, 16); safeZone()
    transition { to("floor1"); edge(TransitionEdge.WEST); entryX(31); entryY(15) }
}
```

### Pause Menu via MenuHandle
```kotlin
val pauseMenu = menu("pause") {
    layout(MenuLayout.VERTICAL)
    position(x = 4, y = 4, width = 12, height = 10)
    sfx(onMove = step, onSelect = door)
    item("Resume")        { navigate(gameplayRef) }
    item("Quit to Title") { navigate(titleRef) }
}
// In pause scene enter:
pauseMenu.show()
```

### HUD with Bar + Number + Icons
```kotlin
val gameHud = hud("game_hud") {
    anchor(Anchor.TOP_LEFT)
    bar("hp")    { variable(hp); max(20); width(5); fillTile(0x01); emptyTile(0x00) }
    number("torch") { variable(torchLevel); label("T:"); format("%d") }
    icons("keys")   { variable(keys); max(5); fullTile(0x10); emptyTile(0x11)
                       displayMode(IconDisplayMode.FULL_AND_EMPTY) }
}
// Show/hide around gameplay:
gameHud.show()   // in gameplay enter
gameHud.hide()   // in gameplay exit
```

### Entity Collision for NPC Interaction
```kotlin
val player by actor {
    entityCollision {
        mode(EntityCollisionMode.BLOCK_AND_TRIGGER)
        onBlocked { navigate("gameplay") }
    }
}
```

## Key Architectural Decisions

- **Scene ordering** — scenes defined in reverse dependency order so each scene can reference previously defined SceneRefs without forward declarations (except the 3 cycle-breakers)
- **3 forward SceneRefs** — `titleRef`, `gameplayRef`, `gameoverRef` handle the 3 navigation cycles
- **ifOp/elseOp in combat** — combat scene uses `ifOp(hp isAbove 3) { hp -= 3 } elseOp { hp set 0 }` for safe HP arithmetic
- **State reset on title** — all variables reset only in title scene START handler, not on every scene enter

## How to Modify

- **Add a new zone:** Define `zone("floor3")`, add `transition { to("floor3") }` to floor2
- **Add a new monster:** Add `monster("name") { ... }` and `encounter { +name }` to simpleBattle
- **Add a new item:** Add a `u8Var` for item count, handle pickup in gameplay frame block
- **Extend the HUD:** Add more `bar()`, `number()`, or `icons()` elements to `gameHud`
- **Add save/load UI:** Use `saveData` slot operations in pause menu items

## Localization

Explorer is ready for localization. Add `res/strings/en.po` following GNU gettext format:

```po
msgctxt "ui"
msgid "resume"
msgstr "Resume"

msgctxt "ui"
msgid "quit_to_title"
msgstr "Quit to Title"
```

See `context/LOCALIZATION.md` in the project root for complete PO file format and bank allocation rules.

## Dependencies

- `gbkt-core` — DSL, IR, all game systems (exploration/zone/flags/UI/save/camera)
- `gbkt-backend-gbdk` — Game Boy C code generation
- `gbkt-genre-rpg` — `character()`, `monster()`, `simpleBattle()`, `battleUpdate()`
