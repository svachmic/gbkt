# UI Module

Menu systems, status bars, and UI rendering for Game Boy games.

## Files

| File | Purpose | LOC |
|------|---------|-----|
| `Menu.kt` | Menu handle and runtime operations | ~125 |
| `MenuBuilder.kt` | Menu definition DSL | ~200 |
| `MenuTypes.kt` | Menu layout types and configurations | ~80 |
| `StatusBar.kt` | HP/SP status bar rendering | ~150 |

## Menu System (Menu.kt, MenuBuilder.kt)

### Menu Definition

```kotlin
val mainMenu = menu("main") {
    position(2, 4)          // Tile position
    size(16, 8)             // Size in tiles

    items {
        item("Start Game") { scene(gameplayScene) }
        item("Options") { scene(optionsScene) }
        item("Quit") { /* handle quit */ }
    }

    onCancel { /* B button behavior */ }
}
```

### Menu Handle

The `MenuHandle` provides runtime control:

```kotlin
// In scene
scene("title") {
    enter {
        mainMenu.show()      // Make visible and active
    }

    every.frame {
        mainMenu.tick()      // REQUIRED: process input
    }

    exit {
        mainMenu.hide()
    }
}
```

### Menu Operations

```kotlin
mainMenu.show()              // Make visible
mainMenu.hide()              // Hide
mainMenu.toggle()            // Toggle visibility
mainMenu.tick()              // Process input (call every frame)
mainMenu.moveTo(0)           // Jump to item index
mainMenu.moveTo(indexExpr)   // Jump to dynamic index
mainMenu.selectCurrent()     // Programmatic selection
mainMenu.cancel()            // Programmatic cancel
```

### Menu State Queries

```kotlin
mainMenu.isVisible           // Condition: is visible?
mainMenu.isActive            // Condition: has focus?
mainMenu.selectedIndex       // Expr: current selection (0-based)
mainMenu.cursorX             // Expr: grid X (for grid menus)
mainMenu.cursorY             // Expr: grid Y (for grid menus)
```

### Menu Types

```kotlin
// Vertical list menu (default)
menu("main") {
    layout = MenuLayout.VERTICAL
    items { ... }
}

// Horizontal menu
menu("actions") {
    layout = MenuLayout.HORIZONTAL
    items { ... }
}

// Grid menu (inventory-style)
menu("inventory") {
    layout = MenuLayout.GRID
    columns = 4
    items { ... }
}
```

## Status Bar (StatusBar.kt)

Health/mana bars for RPG interfaces:

```kotlin
val hpBar = statusBar("hp") {
    position(1, 1)
    width(10)                   // Bar width in tiles
    maxValue { hero.stats.maxHp }
    currentValue { hero.stats.hp }
    color(BarColor.RED)
    showValue = true            // Display numeric value
}

// In frame logic
hpBar.update()                  // Redraw with current values
```

## Menu Pattern

Typical menu usage pattern:

```kotlin
scene("title") {
    enter {
        mainMenu.show()
    }

    every.frame {
        mainMenu.tick()

        whenever(mainMenu.selectedIndex isEqualTo 0 and buttons.a.pressed) {
            scene(gameplayScene)
        }
        whenever(mainMenu.selectedIndex isEqualTo 1 and buttons.a.pressed) {
            scene(optionsScene)
        }
    }

    exit {
        mainMenu.hide()
    }
}
```

## Related Modules

- `ir/MenuIR.kt` - Menu IR nodes
- `codegen/ui/MenuCodegen.kt` - Menu code generation
- `rpg/BattleMenu.kt` - Battle-specific menu system
