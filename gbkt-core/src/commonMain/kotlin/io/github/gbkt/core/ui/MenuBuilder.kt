/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ui

import io.github.gbkt.core.BorderStyle
import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.AssignableExpr
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Dimensions
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRMenuClose
import io.github.gbkt.core.ir.IRMenuOpen
import io.github.gbkt.core.ir.IRRaw
import io.github.gbkt.core.ir.IRSceneChange
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRVar

// =============================================================================
// MENU SYSTEM DSL - Builders
// =============================================================================

/**
 * Builder for menu style configuration.
 *
 * Usage: style { position(5, 8) cursor = ">" border = BorderStyle.ROUNDED }
 */
@GbktDsl
class MenuStyleBuilder {
    private var x = 4
    private var y = 6

    /** Menu width in tiles */
    var width = 12

    /** Text cursor character (string for easier use, takes first char) */
    var cursor: String = ">"

    /** Predefined cursor style */
    var cursorStyle: CursorChar = CursorChar.ARROW
        set(value) {
            field = value
            cursor = value.char.toString()
        }

    /** Sprite for animated cursor (alternative to text cursor) */
    var cursorSprite: String? = null

    /** Pixel offset for sprite cursor */
    var cursorOffset: Pair<Int, Int> = -8 to 0

    /** How selected item is highlighted */
    var selectedStyle = SelectedStyle.NONE

    /** Spacing between items in tile rows */
    var spacing = 2

    /** Border style around menu */
    var border = BorderStyle.NONE

    /** Padding inside border */
    var padding = 1

    /** Wrap or clamp at menu edges */
    var wrapMode = WrapMode.WRAP

    /** Label column width (for settings) */
    var labelWidth = 10

    /** Value column width (for settings) */
    var valueWidth = 6

    /** Set position in tile coordinates */
    fun position(x: Int, y: Int) = apply {
        this.x = x
        this.y = y
    }

    internal fun build() =
        MenuStyleConfig(
            x = x,
            y = y,
            width = width,
            cursorChar = cursor.firstOrNull() ?: '>',
            cursorSprite = cursorSprite,
            cursorOffsetX = cursorOffset.first,
            cursorOffsetY = cursorOffset.second,
            selectedStyle = selectedStyle,
            spacing = spacing,
            border = border,
            padding = padding,
            wrapMode = wrapMode,
            labelWidth = labelWidth,
            valueWidth = valueWidth,
        )
}

/** Builder for menu item actions. */
@GbktDsl
class MenuItemBuilder {
    internal var enabled: IRExpression = IRLiteral(1)
    internal var onSelectStatements: List<IRStatement> = emptyList()
    internal var onHighlightStatements: List<IRStatement> = emptyList()

    /** Set enabled condition */
    fun enabled(condition: Condition) {
        enabled = condition.ir
    }

    /** Called when item is selected (A button) */
    fun onSelect(block: MenuActionScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { MenuActionScope().block() }
        onSelectStatements = recorder.statements
    }

    /** Called when cursor moves to this item */
    fun onHighlight(block: MenuActionScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { MenuActionScope().block() }
        onHighlightStatements = recorder.statements
    }
}

/** Builder for toggle control. */
@GbktDsl
class ToggleBuilder {
    var onLabel = "ON"
    var offLabel = "OFF"
    internal var onChangeStatements: List<IRStatement> = emptyList()

    /** Called when value changes */
    fun onChange(block: MenuActionScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { MenuActionScope().block() }
        onChangeStatements = recorder.statements
    }
}

/** Builder for slider control. */
@GbktDsl
class SliderBuilder {
    var step = 1
    internal var onChangeStatements: List<IRStatement> = emptyList()

    /** Called when value changes */
    fun onChange(block: MenuActionScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { MenuActionScope().block() }
        onChangeStatements = recorder.statements
    }
}

/** Builder for option cycle control. */
@GbktDsl
class OptionBuilder {
    internal var choices: List<String> = emptyList()
    internal var onChangeStatements: List<IRStatement> = emptyList()

    /** Define the options to cycle through */
    fun choices(vararg options: String) {
        choices = options.toList()
    }

    /** Called when value changes */
    fun onChange(block: MenuActionScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { MenuActionScope().block() }
        onChangeStatements = recorder.statements
    }
}

/**
 * Scope available inside menu action callbacks. Provides access to menu navigation and common
 * actions.
 */
@GbktDsl
class MenuActionScope {
    /**
     * Change to another scene.
     *
     * Usage:
     * ```kotlin
     * item("START") { scene(gameplayScene) }
     * ```
     */
    fun scene(ref: SceneRef) {
        RecordingContext.require().emit(IRSceneChange(ref.name))
    }

    /** Open a child menu (push onto stack) */
    fun open(menu: MenuHandle) {
        RecordingContext.require().emit(IRMenuOpen(menu.definition.name))
    }

    /** Close current menu (pop from stack, return to parent) */
    fun close() {
        RecordingContext.require().emit(IRMenuClose)
    }

    /** Execute raw C code */
    fun raw(code: String) {
        RecordingContext.require().emit(IRRaw(code))
    }

    /** Emit arbitrary statement */
    internal fun emit(statement: IRStatement) {
        RecordingContext.require().emit(statement)
    }
}

/**
 * Main builder for defining a vertical menu.
 *
 * Usage: val mainMenu = menu("main") { style { position(5, 8); cursor = ">" } item("START") {
 * scene("gameplay") } item("OPTIONS") { open(optionsMenu) } }
 */
@GbktDsl
class MenuBuilder(private val name: String) {
    private var styleConfig = MenuStyleConfig()
    private val items = mutableListOf<MenuItem>()
    private var parentMenu: String? = null
    private var menuTitle: String? = null

    /** Configure visual style */
    fun style(init: MenuStyleBuilder.() -> Unit) {
        styleConfig = MenuStyleBuilder().apply(init).build()
    }

    /** Set parent menu for automatic back navigation */
    var parent: MenuHandle? = null
        set(value) {
            field = value
            parentMenu = value?.definition?.name
        }

    /** Set menu title displayed above items */
    var title: String?
        get() = menuTitle
        set(value) {
            menuTitle = value
        }

    /**
     * Add an action item to the menu.
     *
     * Usage: item("START GAME") { scene("gameplay") } item("CONTINUE", enabled = saveExists) {
     * loadGame() }
     */
    fun item(label: String, enabled: Condition? = null, block: MenuActionScope.() -> Unit = {}) {
        val itemBuilder = MenuItemBuilder()
        if (enabled != null) {
            itemBuilder.enabled(enabled)
        }
        itemBuilder.onSelect(block)

        items.add(
            MenuItem.Action(
                label = label,
                enabled = itemBuilder.enabled,
                onSelect = itemBuilder.onSelectStatements,
                onHighlight = itemBuilder.onHighlightStatements,
            )
        )
    }

    /**
     * Add a toggle control (on/off switch).
     *
     * Usage: toggle("MUSIC", musicEnabled) toggle("SFX", sfxEnabled) { onChange { applySfxSetting()
     * } }
     */
    fun toggle(label: String, variable: AssignableExpr, init: ToggleBuilder.() -> Unit = {}) {
        val builder = ToggleBuilder().apply(init)
        val varName = (variable.ir as? IRVar)?.name ?: error("Toggle requires a variable")

        items.add(
            MenuItem.Toggle(
                label = label,
                variable = varName,
                onLabel = builder.onLabel,
                offLabel = builder.offLabel,
                onChange = builder.onChangeStatements,
            )
        )
    }

    /**
     * Add a slider control (numeric range).
     *
     * Usage: slider("VOLUME", volume, 0..7) slider("SPEED", gameSpeed, 1..5) { step = 1 }
     */
    fun slider(
        label: String,
        variable: AssignableExpr,
        range: IntRange,
        init: SliderBuilder.() -> Unit = {},
    ) {
        val builder = SliderBuilder().apply(init)
        val varName = (variable.ir as? IRVar)?.name ?: error("Slider requires a variable")

        items.add(
            MenuItem.Slider(
                label = label,
                variable = varName,
                min = range.first,
                max = range.last,
                step = builder.step,
                onChange = builder.onChangeStatements,
            )
        )
    }

    /**
     * Add an option cycle control (discrete choices).
     *
     * Usage: option("DIFFICULTY", difficulty) { choices("EASY", "NORMAL", "HARD") }
     */
    fun option(label: String, variable: AssignableExpr, init: OptionBuilder.() -> Unit) {
        val builder = OptionBuilder().apply(init)
        val varName = (variable.ir as? IRVar)?.name ?: error("Option requires a variable")

        items.add(
            MenuItem.Option(
                label = label,
                variable = varName,
                choices = builder.choices,
                onChange = builder.onChangeStatements,
            )
        )
    }

    /** Add a visual separator line */
    fun separator() {
        items.add(MenuItem.Separator)
    }

    /**
     * Add items conditionally.
     *
     * Usage: itemWhen(saveExists) { item("CONTINUE") { loadGame() } }
     */
    fun itemWhen(condition: Condition, init: MenuBuilder.() -> Unit) {
        val nestedBuilder = MenuBuilder("_conditional")
        nestedBuilder.init()

        items.add(
            MenuItem.Conditional(condition = condition.ir, items = nestedBuilder.items.toList())
        )
    }

    internal fun build() =
        MenuDefinition(
            name = name,
            isGrid = false,
            style = styleConfig,
            items = items.toList(),
            parentMenu = parentMenu,
            title = menuTitle,
        )
}

// =============================================================================
// GRID MENU BUILDER
// =============================================================================

/**
 * Builder for grid-style menus (inventories, item grids).
 *
 * Usage: val inventory = gridMenu("inventory") { grid(4, 3) // 4 columns, 3 rows
 * itemsFrom(inventorySlots) { slot, index -> onSelect { useItem(index) } } }
 */
@GbktDsl
class GridMenuBuilder(private val name: String) {
    private var styleConfig = GridStyleConfig()
    private var columns = 4
    private var rows = 3
    private var arrayBinding: GridArrayBinding? = null
    private var parentMenu: String? = null

    /** Configure visual style */
    fun style(init: GridStyleBuilder.() -> Unit) {
        styleConfig = GridStyleBuilder().apply(init).build()
    }

    /** Set grid dimensions */
    fun grid(columns: Int, rows: Int) {
        this.columns = columns
        this.rows = rows
    }

    /** Set parent menu for back navigation */
    var parent: MenuHandle? = null
        set(value) {
            field = value
            parentMenu = value?.definition?.name
        }

    /**
     * Bind grid to an array variable.
     *
     * Usage: itemsFrom(inventorySlots) { slot, index -> onSelect { useItem(index) } }
     */
    fun itemsFrom(array: AssignableExpr, block: GridSlotBuilder.(slot: Expr, index: Expr) -> Unit) {
        val arrayName = (array.ir as? IRVar)?.name ?: error("itemsFrom requires an array variable")

        val slotBuilder = GridSlotBuilder()
        val slotExpr = Expr(IRVar("_grid_slot_value"))
        val indexExpr = Expr(IRVar("_grid_cursor_index"))

        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { slotBuilder.block(slotExpr, indexExpr) }

        arrayBinding =
            GridArrayBinding(
                arrayVar = arrayName,
                onSelectStatements = slotBuilder.onSelectStatements,
                emptySlotStatements = slotBuilder.whenEmptyStatements,
            )
    }

    internal fun build() =
        MenuDefinition(
            name = name,
            isGrid = true,
            gridStyle = styleConfig,
            columns = columns,
            rows = rows,
            parentMenu = parentMenu,
            arrayBinding = arrayBinding,
        )
}

/** Builder for grid style configuration. */
@GbktDsl
class GridStyleBuilder {
    private var x = 2
    private var y = 2

    var cellWidth = 2
    var cellHeight = 2
    var padding = 1
    var border = BorderStyle.SIMPLE
    var cursorChar = '['
    var cursorCloseChar = ']'
    var cursorSprite: String? = null
    var wrapMode = WrapMode.WRAP

    fun position(x: Int, y: Int) = apply {
        this.x = x
        this.y = y
    }

    var cellSize: Dimensions = Dimensions(2, 2)
        set(value) {
            cellWidth = value.width
            cellHeight = value.height
            field = value
        }

    internal fun build() =
        GridStyleConfig(
            x = x,
            y = y,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            padding = padding,
            border = border,
            cursorChar = cursorChar,
            cursorCloseChar = cursorCloseChar,
            cursorSprite = cursorSprite,
            wrapMode = wrapMode,
        )
}

/** Builder for grid slot behavior. */
@GbktDsl
class GridSlotBuilder {
    internal var onSelectStatements: List<IRStatement> = emptyList()
    internal var whenEmptyStatements: List<IRStatement> = emptyList()

    /** Called when slot is selected (A button) */
    fun onSelect(block: MenuActionScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { MenuActionScope().block() }
        onSelectStatements = recorder.statements
    }

    /** Called when selecting an empty slot */
    fun whenEmpty(block: MenuActionScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { MenuActionScope().block() }
        whenEmptyStatements = recorder.statements
    }
}
