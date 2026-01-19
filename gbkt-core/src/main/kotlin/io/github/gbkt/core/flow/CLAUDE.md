# Flow Module

Game flow orchestration for standard scene sequences (title → gameplay → gameover).

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `GameFlow.kt` | Game flow configuration and handle | ~296 |
| `PauseMenu.kt` | Standard pause menu builder | ~80 |
| `SaveMenu.kt` | Save/load menu builder | ~100 |

## Game Flow System (GameFlow.kt)

### Configuration

```kotlin
val flow = gameFlow {
    titleScreen(titleScene)
    characterSelect(heroSelectScene)  // Optional
    gameplay(gameplayScene)
    battle(battleScene)
    pause(pauseScene)
    gameOver(gameOverScene)
    victory(victoryScene)
    credits(creditsScene)

    // Development mode - skip title during testing
    devMode {
        startAt(gameplayScene)
    }
}

// Set start scene
start = flow.getStartScene() ?: titleScene
```

### Standard Scenes

| Scene Type | Method | Description |
|------------|--------|-------------|
| Title | `titleScreen(ref)` | First scene shown |
| Character Select | `characterSelect(ref)` | Optional hero selection |
| Gameplay | `gameplay(ref)` | Main game loop |
| Battle | `battle(ref)` | Turn-based combat |
| Pause | `pause(ref)` | Pause menu |
| Game Over | `gameOver(ref)` | Player loses |
| Victory | `victory(ref)` | Player wins |
| Credits | `credits(ref)` | End credits |

### GameFlowHandle

Runtime queries:

```kotlin
// Get starting scene (respects dev mode)
val startScene = flow.getStartScene()

// Check if scene is registered
if (flow.hasScene(GameFlowScene.BATTLE)) {
    // Battle system available
}

// Get scene reference
val battleRef = flow.getScene(GameFlowScene.BATTLE)

// Check dev mode
if (flow.isDevMode) {
    // Development mode enabled
}
```

### GameFlowScene Enum

```kotlin
enum class GameFlowScene {
    TITLE,
    CHARACTER_SELECT,
    GAMEPLAY,
    BATTLE,
    PAUSE,
    GAME_OVER,
    VICTORY,
    CREDITS,
}
```

## Dev Mode

Skip title screen during development:

```kotlin
gameFlow {
    titleScreen(titleScene)
    gameplay(gameplayScene)

    devMode {
        startAt(gameplayScene)  // Jump straight to gameplay
    }
}
```

When `devMode` is configured:
- `getStartScene()` returns `devStartScene` instead of title
- `isDevMode` returns `true`

## Integration Pattern

```kotlin
game("MyRPG") {
    // Define scenes
    val titleScene = scene("title") { ... }
    val gameplayScene = scene("gameplay") { ... }
    val battleScene = scene("battle") { ... }
    val gameOverScene = scene("gameover") { ... }

    // Configure flow
    val flow = gameFlow {
        titleScreen(titleScene)
        gameplay(gameplayScene)
        battle(battleScene)
        gameOver(gameOverScene)
    }

    // Use flow to determine start
    start = flow.getStartScene() ?: titleScene
}
```

## Pause Menu (PauseMenu.kt)

Standard pause menu builder with automatic start button integration:

```kotlin
val pauseMenu = pauseMenu("pause") {
    // Auto-wire to start button (handles toggle automatically)
    autoWire(true)

    // Pause game logic while menu is open
    pauseLogic(true)

    // Standard menu items
    resume("RESUME")                           // Closes menu
    save("SAVE") { saveData.save() }           // Custom save logic
    options("OPTIONS") { goto(optionsScene) }  // Navigate to options
    quit("QUIT") { goto(titleScene) }          // Return to title

    // Menu appearance
    style {
        position(5, 4)      // X, Y tile position
        width(10)           // Width in tiles
        border(true)        // Show border
        dimBackground(true) // Darken game while paused
    }

    // Lifecycle callbacks
    onOpen { /* Called when pause menu opens */ }
    onClose { /* Called when pause menu closes */ }
}

// In gameplay scene - tick() handles start button automatically
scene("gameplay") {
    every.frame {
        pauseMenu.tick()

        // Game logic only runs when not paused
        unless(pauseMenu.isOpen) {
            updatePlayer()
            updateEnemies()
        }
    }
}
```

### Pause Menu Item Types

| Method | Type | Description |
|--------|------|-------------|
| `resume(label)` | RESUME | Closes the pause menu |
| `save(label) {}` | SAVE | Save game action |
| `load(label) {}` | LOAD | Load game action |
| `options(label) {}` | OPTIONS | Open settings |
| `quit(label) {}` | QUIT | Return to title |
| `item(label) {}` | CUSTOM | Custom action |

## Save Menu (SaveMenu.kt)

Save/load slot selection with metadata display:

```kotlin
// Load menu with slot previews
val loadMenu = saveMenu("load") {
    saveData("mySaveData")  // Name of save data to use
    slots(3)                // Number of save slots
    showNewGame(true)       // Show "New Game" option

    // Configure slot display
    slotDisplay {
        showName(true)          // Show character name
        showLevel(true)         // Show level
        showPlayTime(true)      // Show play time
        showLocation(false)     // Hide location/floor
        emptyText("- Empty -")  // Text for empty slots
    }

    // Menu appearance
    style {
        position(2, 4)   // X, Y tile position
        width(16)        // Width in tiles
        slotHeight(3)    // Height per slot
        border(true)     // Show border
    }

    // Slot selected (selectedSlot available as 0-based index)
    onSelect { slot ->
        saveData.load(slot)
        gotoGameplay()
    }

    // Cancel pressed
    onCancel {
        gotoTitle()
    }
}

// Save menu (same API, different mode)
val saveMenu = saveMenu("save") {
    saveData("mySaveData")
    slots(3)
    mode(true)  // true = save mode, false = load mode

    onSelect { slot ->
        saveData.save(slot)
        showMessage("Game saved!")
    }

    onCancel {
        closePauseMenu()
    }
}
```

### Save Menu Properties

| Property | Default | Description |
|----------|---------|-------------|
| `saveData(name)` | "save" | Name of save data reference |
| `slots(count)` | 3 | Number of save slots |
| `mode(isSave)` | false | Save mode (true) or load mode (false) |
| `showNewGame(show)` | false | Show "New Game" option in load menus |

## Related Modules

- `scene/Scene.kt` - Scene definitions
- `builder/GameBuilder.kt` - `start` property
- `ui/Menu.kt` - Menu system used by pause/save menus
