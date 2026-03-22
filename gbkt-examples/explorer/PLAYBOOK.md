# Explorer

## Overview
A dungeon-crawling RPG with grid-based exploration, torch management, random encounters, and turn-based combat. Explore two dungeon floors, manage your torch supply, collect keys, and survive random goblin and rat encounters. Your HP depletes in combat; your torch depletes as you walk.

## How to Play
Move the player sprite 2px/frame in four directions using the d-pad. Each step depletes the torch gauge (every 4 steps). After every 120 steps a random encounter triggers — combat is auto-resolved on the combat_scene screen. Collect keys to unlock doors. Pause with START to access the pause menu (Resume or Quit to Title). If your torch runs out or your HP drops to 0, the game is over. Reach floor 2 (the boss chamber) by walking to the east edge of floor 1.

## Controls
| Scene | Button | Effect |
|-------|--------|--------|
| title | START | Start new game (resets all variables) |
| gameplay | UP | Move player up |
| gameplay | DOWN | Move player down |
| gameplay | LEFT | Move player left |
| gameplay | RIGHT | Move player right |
| gameplay | START | Open pause menu |
| pause | START | Resume gameplay |
| pause | B | Quit to title |
| gameover | START | Return to title |

## Scene Flow
- title -> gameplay (press START; resets hp=20, torchLevel=100, stepCount=0, keys=0, level=1)
- gameplay -> pause (press START)
- gameplay -> combat_scene (every 120 steps)
- gameplay -> gameover (torchLevel reaches 0)
- pause -> gameplay (press START or select Resume)
- pause -> title (press B or select Quit to Title)
- combat_scene -> gameplay (on victory)
- combat_scene -> gameover (on defeat, hp reaches 0)
- gameover -> title (press START)

## Win / Lose Conditions
- **Win**: No explicit win scene — deeper exploration is the goal. Dungeon level 2 (floor2) is the boss chamber safe zone.
- **Lose (torch)**: `torchLevel` reaches 0 — torch burns out, dialog shown, transitions to gameover
- **Lose (combat)**: HP drops to 0 during a combat encounter — transitions to gameover

## Known Quirks
- Torch depletes by 1 every 4 steps: condition is `(stepCount and 3) isEqualTo 0`
- Torch warning dialog displays at torchLevel=50 ("Your torch burns out...") then game over at 0
- Combat is auto-resolved with fixed text: goblin appears, hero attacks for 5 dmg, goblin hits for 3 dmg
- HP is clamped safely: `ifOp(hp isAbove 3) { hp -= 3 } elseOp { hp set 0 }` prevents underflow
- Player starts at center (80, 72); boundary: x in [8, 152], y in [16, 136]
- The HUD (`game_hud`) renders HP bar, torch number, and key icons on the window layer
- Global story flags track: `metElder`, `hasKey`, `defeatedBoss` (story page) and `visitedFloor1`, `visitedFloor2` (exploration page)
- Zone transition: walking to east edge of floor1 enters floor2 (boss chamber, no encounters)

## Variables Reference
| Variable | Type | Semantic | Description |
|----------|------|----------|-------------|
| hp | UINT8 | health | Player HP; starts at 20, max 20. Game over at 0 |
| stepCount | UINT8 | counter | Steps taken since last encounter or reset; encounter at 120 |
| keys | UINT8 | counter | Magic keys collected; used for locked doors |
| torchLevel | UINT8 | gauge | Torch fuel; starts at 100, decrements per 4 steps, game over at 0 |
| level | UINT8 | level | Current dungeon level (displayed on game over screen) |
