---
id: FEAT-MENU-GRID-STYLE
status: dormant
planted: 2026-06-12
planted_during: v0.1.1 / Phase 17 docs cleanup
trigger_when: v0.2.0 DSL implementation milestone
scope: medium
triage_disposition: RE-DEFERRED
triage_evidence: ".planning/phases/17-docs-reconciliation-and-quality-cleanup/evidence/DOCS-AUDIT.md#section-3"
triage_date: 2026-06-12
---

# FEAT-MENU-GRID-STYLE: Menu style block, gridMenu, tick, isActive, isVisible, selectedIndex

## Source

Removed from context/DSL_REFERENCE.md lines 1007–1113 (commit removal-commit-TBD).

**Implemented today:** `MenuBuilder` in `gbkt-lang/.../dsl/UIBuilders.kt:273`. Config is function-style (`cursor(">")`, `parent(mainMenu)`, `position(x, y, width, height)`, `slider(label, variable, min, max, step)`). `MenuHandle.show()` and `MenuHandle.hide()` are implemented. What is NOT implemented: nested `style {}` block, `border` on `MenuBuilder`, `spacing` on `MenuBuilder`, `menu.tick()`, `gridMenu()`, `MenuHandle.isVisible`, `MenuHandle.isActive`, `MenuHandle.selectedIndex`.

## Why This Matters

The `style {}` block provides a cleaner ergonomic grouping for visual config. `gridMenu()` enables inventory-style 2D navigation. `tick()` / `isActive` / `isVisible` / `selectedIndex` enable scene scripts to coordinate with menu state. All are missing from `MenuHandle` today.

## When to Surface

**Trigger:** v0.2.0 DSL implementation milestone — after the doc reconciliation is done and the framework is stable.

## Verbatim removed content

```kotlin
// === Simple Vertical Menu (Title Screens, Pause Menus) ===
// Assumes gameplayScene, continueScene are declared as SceneRef
val mainMenu = menu("main") {
    style {
        position(5, 8)        // Tile coordinates (x, y)
        cursor = ">"          // Cursor character
        border = BorderStyle.ROUNDED
        spacing = 2           // Lines between items
    }

    item("NEW GAME") { navigate(gameplayScene) }
    item("CONTINUE") { navigate(continueScene) }
    item("OPTIONS") { open(optionsMenu) }
}

titleScene = scene("title") {
    enter {
        clear()
        printCentered("MY GAME") at 3
        mainMenu.show()
    }

    frame {
        mainMenu.tick()  // REQUIRED - handles input and rendering
    }
}

// === Settings Menu with Controls ===
val optionsMenu = menu("options") {
    parent = mainMenu  // B button returns to parent

    style {
        position(3, 4)
        labelWidth = 10   // Width of label column
        valueWidth = 6    // Width of value column
    }

    // Toggle: A button or left/right to flip
    toggle("MUSIC", musicEnabled) {
        onChange { applyMusicSetting() }
    }

    // Slider: Left/right to adjust
    slider("VOLUME", volume, 0..7) {
        step = 1
        onChange { applyVolume() }
    }

    // Option cycle: Left/right to cycle through choices
    option("DIFFICULTY", difficulty) {
        choices("EASY", "NORMAL", "HARD")
    }

    item("BACK") { close() }
}

// === Grid Menu (Inventories) ===
val inventory = gridMenu("inventory") {
    grid(4, 3)  // 4 columns, 3 rows

    style {
        position(2, 2)
        cellSize = 2 x 2      // Cell size in tiles
        padding = 1           // Padding between cells
        border = BorderStyle.SIMPLE
    }

    itemsFrom(inventorySlots) { slot, index ->
        onSelect { useItem(index) }
        whenEmpty { /* nothing */ }
    }
}

// === Menu State Conditions ===
whenever(mainMenu.isVisible) { /* menu is shown */ }
whenever(mainMenu.isActive) { /* menu has focus */ }
val idx = mainMenu.selectedIndex  // Current cursor position
```

**Important Notes (from the removed section):**
- Always call `menu.tick()` in `frame { }` when a menu is active
- For inventories: use `gridMenu` with `itemsFrom` binding
