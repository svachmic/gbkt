/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ui

import io.github.gbkt.core.BorderStyle
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// MENU SYSTEM DSL - Types and Configuration
// Compose-like, addictive developer experience for Game Boy menus
//
// Title screens:   val main = menu("main") { item("START") { scene("play") } }
// Settings:        toggle("MUSIC", musicOn); slider("VOL", volume, 0..7)
// Inventories:     val inv = gridMenu("inv") { grid(4, 3); itemsFrom(slots) { } }
// =============================================================================

// =============================================================================
// CURSOR STYLES
// =============================================================================

/** Predefined cursor characters for text-based cursors. */
enum class CursorChar(val char: Char) {
    ARROW('>'),
    DASH('-'),
    DOT('.'),
    ASTERISK('*'),
    TRIANGLE('\u0010'), // Custom tile if available
    NONE(' '),
}

/** How the selected item is visually indicated. */
enum class SelectedStyle {
    /** No special highlight - cursor only */
    NONE,
    /** Inverse video (swap fg/bg colors) - GBC only */
    INVERSE,
    /** Blinking text */
    BLINK,
}

/** Navigation wrap behavior for cursor at edges. */
enum class WrapMode {
    /** Stop at edges */
    CLAMP,
    /** Wrap from last to first and vice versa */
    WRAP,
}

// =============================================================================
// CONFIGURATION DATA CLASSES
// =============================================================================

/** Menu visual styling configuration. */
data class MenuStyleConfig(
    val x: Int = 4,
    val y: Int = 6,
    val width: Int = 12,
    val cursorChar: Char = '>',
    val cursorSprite: String? = null, // Sprite name for animated cursor
    val cursorOffsetX: Int = -8, // Pixel offset for sprite cursor
    val cursorOffsetY: Int = 0,
    val selectedStyle: SelectedStyle = SelectedStyle.NONE,
    val spacing: Int = 2, // Lines between items (in tiles)
    val border: BorderStyle = BorderStyle.NONE,
    val padding: Int = 1,
    val wrapMode: WrapMode = WrapMode.WRAP,
    val labelWidth: Int = 10, // For settings: width of label column
    val valueWidth: Int = 6, // For settings: width of value column
)

/** Grid menu styling configuration. */
data class GridStyleConfig(
    val x: Int = 2,
    val y: Int = 2,
    val cellWidth: Int = 2, // Cell width in tiles
    val cellHeight: Int = 2, // Cell height in tiles
    val padding: Int = 1, // Padding between cells
    val border: BorderStyle = BorderStyle.SIMPLE,
    val cursorChar: Char = '[',
    val cursorCloseChar: Char = ']',
    val cursorSprite: String? = null,
    val wrapMode: WrapMode = WrapMode.WRAP,
)

// =============================================================================
// MENU ITEMS - The different types of menu entries
// =============================================================================

/** Sealed class representing different menu item types. */
sealed class MenuItem {
    /** Simple action item - label + callback */
    data class Action(
        val label: String,
        val enabled: IRExpression = IRLiteral(1),
        val onSelect: List<IRStatement> = emptyList(),
        val onHighlight: List<IRStatement> = emptyList(),
    ) : MenuItem()

    /** Toggle control - label + boolean variable */
    data class Toggle(
        val label: String,
        val variable: String,
        val onLabel: String = "ON",
        val offLabel: String = "OFF",
        val onChange: List<IRStatement> = emptyList(),
    ) : MenuItem()

    /** Slider control - label + numeric variable with range */
    data class Slider(
        val label: String,
        val variable: String,
        val min: Int,
        val max: Int,
        val step: Int = 1,
        val onChange: List<IRStatement> = emptyList(),
    ) : MenuItem()

    /** Option cycle - label + variable with discrete choices */
    data class Option(
        val label: String,
        val variable: String,
        val choices: List<String>,
        val onChange: List<IRStatement> = emptyList(),
    ) : MenuItem()

    /** Visual separator line */
    data object Separator : MenuItem()

    /** Conditional item - only shown when condition is true */
    data class Conditional(val condition: IRExpression, val items: List<MenuItem>) : MenuItem()
}

// =============================================================================
// MENU DEFINITION - The compiled menu structure
// =============================================================================

/** Complete menu definition combining all configuration and items. */
data class MenuDefinition(
    val name: String,
    val isGrid: Boolean = false,
    val style: MenuStyleConfig = MenuStyleConfig(),
    val gridStyle: GridStyleConfig? = null,
    val items: List<MenuItem> = emptyList(),
    val columns: Int = 1,
    val rows: Int = 0,
    val parentMenu: String? = null,
    val title: String? = null,
    val arrayBinding: GridArrayBinding? = null,
)

/** Binding information for grid menus bound to arrays. */
data class GridArrayBinding(
    val arrayVar: String,
    val onSelectStatements: List<IRStatement>,
    val emptySlotStatements: List<IRStatement>,
)
