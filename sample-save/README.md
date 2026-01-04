# sample-save

A persistence demo showcasing the gbkt save system with SRAM support.

## What This Sample Demonstrates

- **Save data structure**: Type-safe SRAM fields
- **Score counter**: Runtime score tracking
- **High score persistence**: Best score saved across sessions
- **Save/load controls**: Manual save with button press
- **Data integrity**: CRC8 checksum for corruption detection

## Features

| Feature | Description |
|---------|-------------|
| Save Fields | score, highScore, gamesPlayed, level |
| Checksum | CRC8 for data integrity |
| Magic | "SAVE" marker for validation |
| Slots | Single slot (expandable to 3) |
| Auto-upgrade | Cartridge auto-upgrades to MBC5_RAM_BATTERY |

## Code Highlights

### Save Data Definition

```kotlin
val save = saveData("savegame") {
    var score by u16Field()               // 2 bytes
    var highScore by u16Field()           // 2 bytes
    var gamesPlayed by u8Field()          // 1 byte
    var level by u8Field(default = 1)     // 1 byte with default

    config {
        slots = 1                         // Single save slot
        checksum = Checksum.CRC8          // Data integrity
        magic = "SAVE"                    // Validation marker
        version = 1                       // Format version
    }
}
```

### Checking Save Exists

```kotlin
whenever(save.exists(slot = 0)) {
    save.load(slot = 0)
    print("HIGH SCORE: ", save.highScore) at (2 to 6)
}
```

### Saving Data

```kotlin
whenever(buttons.a.pressed) {
    save.score set currentScore

    whenever(currentScore isAbove save.highScore) {
        save.highScore set currentScore
    }

    save.save()  // Write to SRAM
}
```

### Erasing Save

```kotlin
whenever(buttons.select.pressed) {
    save.erase(slot = 0)
}
```

## Controls

| Button | Action |
|--------|--------|
| D-Pad | Move player |
| A | Save game |
| B | End game (go to results) |
| Start | Start game / Return to title |
| Select | Erase save data (title screen) |

## Building

```bash
# Generate C code
./gradlew :sample-save:generateC

# Build ROM (requires GBDK-2020)
./gradlew :sample-save:buildRom

# Run code generation (for debugging)
./gradlew :sample-save:run
```

## Assets Required

- `assets/player.png` - 8x8 player sprite
- `assets/coin.png` - 8x8 collectible sprite

See the .txt placeholder files for sprite specifications.

## Game Flow

1. **Title Screen**: Shows high score if save exists
   - START: Begin game
   - SELECT: Erase save data

2. **Game Scene**: Collect coins, build score
   - Score increases by (level x 10) per coin
   - Level increases every 100 points
   - A: Save current progress
   - B: End game

3. **Result Screen**: Shows final score and stats
   - Auto-saves on game end
   - Shows "NEW HIGH SCORE!" if beaten

## Save Data Layout

```
Offset  Size  Field
0x00    2     score (u16)
0x02    2     highScore (u16)
0x04    1     gamesPlayed (u8)
0x05    1     level (u8)
0x06    4     magic ("SAVE")
0x0A    1     version (1)
0x0B    1     checksum (CRC8)
```

## Next Steps

After understanding this example, try:

1. Add multiple save slots with a slot selection menu
2. Add more save fields (player position, inventory, etc.)
3. Implement auto-save at checkpoints
4. Add a "New Game" option that doesn't load save data
