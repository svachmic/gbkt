# Explorer

The advanced gbkt reference game — a dungeon crawler demonstrating all major v2 framework systems.

Explore multi-floor dungeons, fight random encounters, manage your torch and keys, save your progress.

## How to Play

| Button | Action |
|--------|--------|
| D-pad        | Move player (2px/frame, smooth) |
| START        | Open pause menu |
| B (in pause) | Quit to title |
| START (title/gameover) | Start / restart |

**Objective:** Explore the dungeon floors, survive combat encounters, and reach the boss chamber on floor 2.

## Features Demonstrated

### RPG System (gbkt-genre-rpg)
- `character()` / `monster()` — hero (HP 20, ATK 5, DEF 3), goblin, rat
- `simpleBattle("combat")` — party, encounter table, onVictory/onDefeat callbacks
- `battleUpdate("combat")` — drives combat state machine in combat scene

### World System
- `zone("floor1")` / `zone("floor2")` — two dungeon floors with `size()`, `encounters`, `safeZone()`
- `transition { to("floor2"); edge(TransitionEdge.EAST) }` — floor-to-floor transitions
- `flags { page("story") { flag("metElder"); flag("hasKey"); flag("defeatedBoss") } }` — global story flags

### Exploration System
- `exploration { preset(ExplorationPreset.DUNGEON_CRAWLER) }` — grid step tracking
- `gauge("torch")` — depletes per step; `onLow(50)` and `onDepleted` callbacks
- `keys("magic_key")` — key counter (max 99), `onStep` / `onBlocked` callbacks

### UI System
- `hud()` — HP bar, torch number, key icon strip; shown/hidden via `gameHud.show()` / `gameHud.hide()`
- `menu("pause")` — vertical pause menu with Resume / Quit items, SFX hooks
- `dialog("torch_warning")` — torch-out warning rendered on window layer

### Core Systems
- `saveData("explorer_save") { slots(1) }` — save/load
- `camera { smoothing = 0.2f }` — smooth follow camera
- `entityCollision { mode(BLOCK_AND_TRIGGER) }` — NPC/door collision on player actor
- `soundEffect { preset(...) }` — step, door, save, hit sounds
- 5 scenes: title, gameplay, pause, combat_scene, gameover
- `sceneRef()` forward declarations — 3 refs break circular navigation cycles

## This is the Advanced Reference

Explorer combines every major v2 system in one game. Use it as the starting point when building complex games that integrate exploration, RPG combat, UI, saving, and multi-floor worlds.

## Build & Run

```bash
# Generate C code
./gradlew :gbkt-examples:explorer:generateC

# Build ROM (requires GBDK installed)
./gradlew :gbkt-examples:explorer:buildRom

# Run in emulator
./gradlew :gbkt-examples:explorer:runEmulator
```

Generated C: `gbkt-examples/explorer/build/gbkt/generated/main.c`
ROM output:  `gbkt-examples/explorer/build/gbkt/output/explorer.gb`
