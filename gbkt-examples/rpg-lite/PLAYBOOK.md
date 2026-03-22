# RPG Lite

## Overview
A mini-RPG with town and dungeon scenes. Start in town, then descend into the dungeon where random encounters with slimes and bats trigger turn-based combat. Earn gold from victories and deeper floors; return to town to heal. Survive as many dungeon levels as you can.

## How to Play
Press START at the title screen to begin (resets hp=30, gold=0, dungeonLevel=1, stepCount=0). In town: press A to enter the dungeon, press START to heal 10 HP for 5 gold. In the dungeon: move with the d-pad (2px/frame), press START to return to town. After 60 steps a random encounter triggers combat. Combat is auto-resolved via the `simpleBattle` state machine driven by `battleUpdate()`. Winning a battle awards gold. Reaching the right edge of the dungeon (x >= 152) goes one dungeon level deeper (+3 gold). HP reaching 0 ends the game.

## Controls
| Scene | Button | Effect |
|-------|--------|--------|
| title | START | Start new game |
| town | A | Enter the dungeon |
| town | START | Heal 10 HP for 5 gold (if gold >= 5 and hp < 30) |
| dungeon | UP | Move hero up |
| dungeon | DOWN | Move hero down |
| dungeon | LEFT | Move hero left |
| dungeon | RIGHT | Move hero right |
| dungeon | START | Return to town |
| gameover | START | Return to title |

## Scene Flow
- title -> town (press START)
- town -> dungeon (press A)
- dungeon -> town (press START)
- dungeon -> dungeon (combat victory: continue exploring at reset position)
- dungeon -> gameover (combat defeat or hp = 0)
- gameover -> title (press START)

## Win / Lose Conditions
- **Win**: No formal win condition — explore deeper dungeon levels (dungeonLevel increments each time you reach the right edge)
- **Lose**: `hp` reaches 0 in the dungeon, or combat defeat → transitions to gameover scene showing HP and gold
- Game over shows HP (always 0) and current gold

## Known Quirks
- Encounter triggers at stepCount >= 60 (resets to 0 each encounter)
- Hero walks from center (80, 72); boundary: x in [8, 152], y in [16, 128]
- Dungeon exit (right edge x >= 152) resets hero to x=8, y=72 and increments dungeonLevel
- Town heal costs 5 gold and restores 10 HP; blocked if gold < 5 or hp already at max (30)
- Combat state machine is driven by `battleUpdate(combat)` called every frame in the dungeon
- `fireball` ability is declared (demonstrates the ability() DSL) but not used in combat (simpleBattle uses basic attacks)
- Gold persists between dungeon runs within a session (no save/load in this example)

## Variables Reference
| Variable | Type | Semantic | Description |
|----------|------|----------|-------------|
| hp | UINT8 | health | Hero HP; starts at 30, max 30. Game over at 0 |
| gold | UINT8 | currency | Gold earned from battles and dungeon levels; used for healing |
| dungeonLevel | UINT8 | level | Current dungeon depth; increments each time you reach the right edge |
| stepCount | UINT8 | counter | Steps taken since last encounter; encounter triggers at >= 60 |
