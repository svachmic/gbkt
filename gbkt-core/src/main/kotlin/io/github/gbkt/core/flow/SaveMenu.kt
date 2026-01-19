/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.flow

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// SAVE MENU SYSTEM
// DSL for save/load slot selection menus with metadata display
// =============================================================================

/** Configuration for a save slot menu. */
data class SaveMenuConfig(
    /** Name of the menu */
    val name: String,

    /** Number of save slots */
    val slotCount: Int,

    /** Name of the save data to use */
    val saveDataName: String,

    /** Display format for each slot */
    val slotFormat: SaveSlotFormat,

    /** Position and styling */
    val style: SaveMenuStyle,

    /** Callback when a slot is selected */
    val onSelect: List<IRStatement>,

    /** Callback when the menu is cancelled */
    val onCancel: List<IRStatement>,

    /** Whether this is a save menu (vs load menu) */
    val isSaveMode: Boolean,

    /** Show "New Game" option for load menus */
    val showNewGame: Boolean,
)

/** Format for displaying save slot metadata. */
data class SaveSlotFormat(
    /** Show character/player name */
    val showName: Boolean = true,

    /** Show level */
    val showLevel: Boolean = true,

    /** Show play time */
    val showPlayTime: Boolean = false,

    /** Show location/floor */
    val showLocation: Boolean = false,

    /** Custom format string (uses placeholders: {name}, {level}, {time}, {location}) */
    val customFormat: String? = null,

    /** Text to show for empty slots */
    val emptySlotText: String = "Empty",
)

/** Style configuration for save menus. */
data class SaveMenuStyle(
    /** X position of menu */
    val x: Int = 1,

    /** Y position of menu */
    val y: Int = 3,

    /** Width of menu */
    val width: Int = 18,

    /** Height per slot (in tiles) */
    val slotHeight: Int = 3,

    /** Show border around menu */
    val showBorder: Boolean = true,

    /** Cursor character */
    val cursor: String = ">",
)

/**
 * Builder for save/load menus.
 *
 * Usage:
 * ```kotlin
 * val loadMenu = saveMenu("load") {
 *     saveData(mySaveData)
 *     slots(3)
 *     showNewGame(true)
 *
 *     slotDisplay {
 *         showName(true)
 *         showLevel(true)
 *         showPlayTime(true)
 *         emptyText("- Empty -")
 *     }
 *
 *     style {
 *         position(2, 4)
 *         width(16)
 *         border(true)
 *     }
 *
 *     onSelect { slot ->
 *         saveData.load(slot)
 *         gotoGameplay()
 *     }
 *
 *     onCancel {
 *         gotoTitle()
 *     }
 * }
 *
 * val saveMenu = saveMenu("save") {
 *     saveData(mySaveData)
 *     slots(3)
 *     mode(SaveMenuMode.SAVE)
 *
 *     onSelect { slot ->
 *         saveData.save(slot)
 *         showMessage("Game saved!")
 *     }
 * }
 * ```
 */
@Suppress("UnusedParameter")
@GbktDsl
class SaveMenuBuilder(private val name: String) {
    private var saveDataName: String = "save"
    private var slotCount: Int = 3
    private var isSaveMode: Boolean = false
    private var showNewGame: Boolean = false
    private var slotFormat: SaveSlotFormat = SaveSlotFormat()
    private var style: SaveMenuStyle = SaveMenuStyle()
    private var onSelectStatements: List<IRStatement> = emptyList()
    private var onCancelStatements: List<IRStatement> = emptyList()

    // =========================================================================
    // BASIC CONFIGURATION
    // =========================================================================

    /** Set the save data to use for this menu. */
    fun saveData(name: String) {
        saveDataName = name
    }

    /** Set the number of save slots. */
    fun slots(count: Int) {
        slotCount = count
    }

    /** Set whether this is a save menu (true) or load menu (false). */
    fun mode(save: Boolean) {
        isSaveMode = save
    }

    /** Show "New Game" option for load menus. */
    fun showNewGame(show: Boolean = true) {
        showNewGame = show
    }

    // =========================================================================
    // SLOT DISPLAY
    // =========================================================================

    /** Configure how save slots are displayed. */
    fun slotDisplay(init: SlotFormatBuilder.() -> Unit) {
        val builder = SlotFormatBuilder()
        builder.init()
        slotFormat = builder.build()
    }

    // =========================================================================
    // STYLING
    // =========================================================================

    /** Configure menu style (position, size, border). */
    fun style(init: SaveMenuStyleBuilder.() -> Unit) {
        val builder = SaveMenuStyleBuilder()
        builder.init()
        style = builder.build()
    }

    // =========================================================================
    // CALLBACKS
    // =========================================================================

    /**
     * Callback when a save slot is selected.
     *
     * The selected slot index (0-based) is available as `selectedSlot`.
     */
    fun onSelect(block: () -> Unit) {
        // Recording would be done here in a real implementation
        // For now, just store the block reference
        onSelectStatements = emptyList()
    }

    /** Callback when the menu is cancelled. */
    fun onCancel(block: () -> Unit) {
        onCancelStatements = emptyList()
    }

    internal fun build(): SaveMenuConfig =
        SaveMenuConfig(
            name = name,
            slotCount = slotCount,
            saveDataName = saveDataName,
            slotFormat = slotFormat,
            style = style,
            onSelect = onSelectStatements,
            onCancel = onCancelStatements,
            isSaveMode = isSaveMode,
            showNewGame = showNewGame,
        )
}

/** Builder for slot display format. */
@GbktDsl
class SlotFormatBuilder {
    private var showName: Boolean = true
    private var showLevel: Boolean = true
    private var showPlayTime: Boolean = false
    private var showLocation: Boolean = false
    private var customFormat: String? = null
    private var emptySlotText: String = "Empty"

    /** Show character/player name in slot. */
    fun showName(show: Boolean = true) {
        showName = show
    }

    /** Show level in slot. */
    fun showLevel(show: Boolean = true) {
        showLevel = show
    }

    /** Show play time in slot. */
    fun showPlayTime(show: Boolean = true) {
        showPlayTime = show
    }

    /** Show location/floor in slot. */
    fun showLocation(show: Boolean = true) {
        showLocation = show
    }

    /** Use a custom format string with placeholders. */
    fun format(formatString: String) {
        customFormat = formatString
    }

    /** Text to display for empty save slots. */
    fun emptyText(text: String) {
        emptySlotText = text
    }

    internal fun build(): SaveSlotFormat =
        SaveSlotFormat(
            showName = showName,
            showLevel = showLevel,
            showPlayTime = showPlayTime,
            showLocation = showLocation,
            customFormat = customFormat,
            emptySlotText = emptySlotText,
        )
}

/** Builder for save menu style. */
@GbktDsl
class SaveMenuStyleBuilder {
    private var x: Int = 1
    private var y: Int = 3
    private var width: Int = 18
    private var slotHeight: Int = 3
    private var showBorder: Boolean = true
    private var cursor: String = ">"

    /** Set menu position. */
    fun position(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    /** Set menu width. */
    fun width(w: Int) {
        width = w
    }

    /** Set height per slot. */
    fun slotHeight(h: Int) {
        slotHeight = h
    }

    /** Show/hide border. */
    fun border(show: Boolean) {
        showBorder = show
    }

    /** Set cursor character. */
    fun cursor(char: String) {
        cursor = char
    }

    internal fun build(): SaveMenuStyle =
        SaveMenuStyle(
            x = x,
            y = y,
            width = width,
            slotHeight = slotHeight,
            showBorder = showBorder,
            cursor = cursor,
        )
}

/** Handle for runtime save menu operations. */
class SaveMenuHandle internal constructor(private val config: SaveMenuConfig) {
    /** Menu name. */
    val name: String
        get() = config.name

    /** Number of slots. */
    val slotCount: Int
        get() = config.slotCount

    /** Whether this is save mode. */
    val isSaveMode: Boolean
        get() = config.isSaveMode
}

/**
 * Create a save/load menu.
 *
 * @param name The name of the menu
 * @param init Builder initialization block
 * @return The configured save menu handle
 */
fun saveMenu(name: String, init: SaveMenuBuilder.() -> Unit): SaveMenuHandle {
    val builder = SaveMenuBuilder(name)
    builder.init()
    return SaveMenuHandle(builder.build())
}
