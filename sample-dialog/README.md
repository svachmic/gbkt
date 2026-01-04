# sample-dialog

A UI system showcase demonstrating dialogs, menus, and choice-based interactions in gbkt.

## What This Sample Demonstrates

- **Dialog system**: Typewriter text effect with speaker names
- **Menu system**: Navigable menus with cursor
- **Choice responses**: Different dialog based on selections
- **NPC interaction**: Proximity-based talk system
- **Quest flags**: Simple state tracking (hasKey, etc.)

## Features

| Feature | Description |
|---------|-------------|
| Dialog Box | Bottom-screen dialog with configurable style |
| Typewriter | Text appears character-by-character |
| Speaker Name | "Elder:" prefix on dialog |
| Menu | 3-option interaction menu with cursor |
| Quest Flags | Track progress with boolean variables |
| Scene Flow | Title -> Game -> Talking scenes |

## Code Highlights

### Dialog Definition

```kotlin
val elderDialog = dialog("elder") {
    speaker = "Elder"
    textSpeed = 2  // Characters per frame

    box {
        position(0, 12)       // Tile coordinates
        size = 20 x 6         // Width x Height
        border = BorderStyle.SIMPLE
        padding = 1
    }
}
```

### Menu Definition

```kotlin
val talkMenu = menu("talk") {
    style {
        position(2, 2)
        cursor = ">"
        border = BorderStyle.SIMPLE
        spacing = 1
    }

    item("TALK") { talkCount += 1 }
    item("ASK FOR KEY") { selectedOption set 1 }
    item("LEAVE") { selectedOption set 2 }
}
```

### Dialog with Variable Responses

```kotlin
whenever(talkCount isEqualTo 1) {
    elderDialog.say("Welcome, traveler!")
}
whenever(talkCount isEqualTo 2) {
    elderDialog.say("Have you seen the locked door?")
}
```

### NPC Proximity Check

```kotlin
whenever(player collidesWith npc) {
    print("Press A") at (10 to 17)
    whenever(buttons.a.pressed) {
        scene("talking")
    }
}
```

## Controls

| Button | Action |
|--------|--------|
| D-Pad Left/Right | Move player |
| A | Interact with NPC / Select menu item / Advance dialog |
| B | Cancel / Return to game |
| Start | Start game (title screen) |

## Building

```bash
# Generate C code
./gradlew :sample-dialog:generateC

# Build ROM (requires GBDK-2020)
./gradlew :sample-dialog:buildRom

# Run code generation (for debugging)
./gradlew :sample-dialog:run
```

## Assets Required

- `assets/player.png` - 8x16 player sprite
- `assets/npc.png` - 8x16 NPC (Elder) sprite

See the .txt placeholder files for sprite specifications.

## Game Flow

1. **Title Screen**: Press START to begin
2. **Game Scene**: Walk left/right, approach NPC
3. **Talking Scene**: Menu appears when near NPC
   - TALK: Hear different dialog based on visit count
   - ASK FOR KEY: Receive a key item (quest flag)
   - LEAVE: Return to game

## Next Steps

After understanding this example, try:

1. Add more NPCs with different dialogs
2. Create a door that requires the key to open
3. Add a shop menu with buy/sell options
4. Implement an inventory grid menu
