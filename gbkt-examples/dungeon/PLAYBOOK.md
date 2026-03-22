# Dungeon

## Overview
A torch-lit dungeon crawler with grid-based movement (8px per step), a torch gauge that depletes as you explore, keys for locked doors, and random encounters against bats and skeletons. Your goal is to survive as long as possible before the torch burns out. Random encounters lead to a turn-based battle scene.

## How to Play
Navigate the dungeon grid one tile (8px) at a time using the d-pad. Each step decrements the torch every 4 steps. When torch drops to 50 a warning dialog appears; at 0 the game ends. Encounters trigger randomly after every 120 steps — the battle scene resolves the fight against bats or skeletons using the `simpleBattle` state machine. Keys (room_key) can be collected to unlock doors. There is no explicit exit — survival time is the measure of success.

## Controls
| Scene | Button | Effect |
|-------|--------|--------|
| title | START | Start new game (resets torchLevel=255, keys=0, steps=0) |
| gameplay | UP | Move player up one tile (8px) |
| gameplay | DOWN | Move player down one tile (8px) |
| gameplay | LEFT | Move player left one tile (8px) |
| gameplay | RIGHT | Move player right one tile (8px) |
| battle | (none) | Battle resolves automatically each frame via battleUpdate |
| gameover | START | Return to title (resets torch, keys, steps) |

## Scene Flow
- title -> gameplay (press START)
- gameplay -> battle (random encounter after 120 steps)
- gameplay -> gameover (torchLevel reaches 0 or drops to low warning and continues depleting)
- battle -> gameplay (combat victory)
- battle -> gameover (combat defeat)
- gameover -> title (press START)

## Win / Lose Conditions
- **Win**: No formal win condition — explore and survive as long as possible
- **Lose (torch)**: `torchLevel` depletes to 0 → "Your torch burns out!" dialog → gameover
- **Lose (torch low)**: `torchLevel` reaches 50 → warning triggers navigate(gameoverRef) immediately (torch-low is fatal in this implementation)
- **Lose (combat)**: Combat defeat → navigate to gameover

## Known Quirks
- Torch depletion: decrements by 1 every 4 steps (`steps and 3 == 0`)
- Torch-low threshold (50) triggers navigate(gameoverRef) immediately — not just a warning
- Movement is grid-based (8px per step) unlike Explorer's smooth 2px movement
- Player starts at (64, 64); boundary: x in [8, 152], y in [16, 128]
- Battle scene shows "ENCOUNTER!" text, plays hitSfx, then drives `simpleBattle` each frame
- Victory in battle navigates back to gameplay; defeat navigates to gameover
- Encounters include: bat alone, or bat + skeleton together (weighted 30 for both)
- Keys counter (room_key) starts at 0, max 9 — currently no door interaction in this minimal example

## Variables Reference
| Variable | Type | Semantic | Description |
|----------|------|----------|-------------|
| torchLevel | UINT8 | gauge | Torch fuel; starts at 255, decrements per 4 steps, game over at 0 or low threshold (50) |
| keys | UINT8 | counter | Room keys collected (max 9); for unlocking doors |
| steps | UINT8 | counter | Steps taken since last encounter; encounter triggers at >= 120 |
