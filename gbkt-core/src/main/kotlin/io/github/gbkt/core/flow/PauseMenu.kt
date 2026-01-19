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
// PAUSE MENU SYSTEM
// Simplified DSL for pause menus with automatic start button integration
// =============================================================================

/** Configuration for a pause menu. */
data class PauseMenuConfig(
    /** Name of the menu */
    val name: String,

    /** Menu items */
    val items: List<PauseMenuItem>,

    /** Position and styling */
    val style: PauseMenuStyle,

    /** Whether to auto-wire to start button */
    val autoWireStartButton: Boolean,

    /** Whether to pause game logic while menu is open */
    val pauseGameLogic: Boolean,

    /** Callback when menu is opened */
    val onOpen: List<IRStatement>,

    /** Callback when menu is closed */
    val onClose: List<IRStatement>,
)

/** A menu item in the pause menu. */
data class PauseMenuItem(
    /** Display label */
    val label: String,

    /** Item type (for special handling) */
    val type: PauseMenuItemType,

    /** Callback when selected */
    val onSelect: List<IRStatement>,
)

/** Types of pause menu items for special handling. */
enum class PauseMenuItemType {
    /** Resume the game */
    RESUME,

    /** Open options/settings */
    OPTIONS,

    /** Save the game */
    SAVE,

    /** Load a save */
    LOAD,

    /** Quit to title */
    QUIT,

    /** Custom item */
    CUSTOM,
}

/** Style configuration for pause menus. */
data class PauseMenuStyle(
    /** X position of menu */
    val x: Int = 5,

    /** Y position of menu */
    val y: Int = 4,

    /** Width of menu */
    val width: Int = 10,

    /** Show border around menu */
    val showBorder: Boolean = true,

    /** Cursor character */
    val cursor: String = ">",

    /** Darken background while paused */
    val dimBackground: Boolean = true,
)

/**
 * Builder for pause menus.
 *
 * Usage:
 * ```kotlin
 * val pauseMenu = pauseMenu("pause") {
 *     autoWire(true)  // Auto-toggle on start button
 *     pauseLogic(true)  // Pause game updates while open
 *
 *     resume("RESUME")  // First item always resumes
 *     save("SAVE") { saveData.save() }
 *     options("OPTIONS") { goto(optionsScene) }
 *     quit("QUIT") { goto(titleScene) }
 *
 *     style {
 *         position(5, 4)
 *         width(10)
 *         border(true)
 *         dimBackground(true)
 *     }
 *
 *     onOpen {
 *         // Called when pause menu opens
 *     }
 *
 *     onClose {
 *         // Called when pause menu closes
 *     }
 * }
 *
 * // In gameplay scene:
 * scene("gameplay") {
 *     every.frame {
 *         pauseMenu.tick()  // Handles start button automatically
 *
 *         // Game logic only runs if not paused
 *         unless(pauseMenu.isOpen) {
 *             updatePlayer()
 *         }
 *     }
 * }
 * ```
 */
@Suppress("UnusedParameter")
@GbktDsl
class PauseMenuBuilder(private val name: String) {
    private val items = mutableListOf<PauseMenuItem>()
    private var style = PauseMenuStyle()
    private var autoWireStartButton = true
    private var pauseGameLogic = true
    private var onOpenStatements: List<IRStatement> = emptyList()
    private var onCloseStatements: List<IRStatement> = emptyList()

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    /**
     * Auto-wire pause menu to start button.
     *
     * When enabled, the menu will automatically toggle when start is pressed.
     */
    fun autoWire(enabled: Boolean = true) {
        autoWireStartButton = enabled
    }

    /**
     * Pause game logic while menu is open.
     *
     * When enabled, provides `pauseMenu.isOpen` condition for skipping game updates.
     */
    fun pauseLogic(enabled: Boolean = true) {
        pauseGameLogic = enabled
    }

    // =========================================================================
    // STANDARD ITEMS
    // =========================================================================

    /** Add a "Resume" item that closes the pause menu. */
    fun resume(label: String = "RESUME") {
        items.add(
            PauseMenuItem(label = label, type = PauseMenuItemType.RESUME, onSelect = emptyList())
        )
    }

    /** Add a "Save" item with custom save logic. */
    fun save(label: String = "SAVE", action: () -> Unit = {}) {
        items.add(
            PauseMenuItem(
                label = label,
                type = PauseMenuItemType.SAVE,
                onSelect = emptyList(), // Would record action
            )
        )
    }

    /** Add a "Load" item. */
    fun load(label: String = "LOAD", action: () -> Unit = {}) {
        items.add(
            PauseMenuItem(label = label, type = PauseMenuItemType.LOAD, onSelect = emptyList())
        )
    }

    /** Add an "Options" item. */
    fun options(label: String = "OPTIONS", action: () -> Unit = {}) {
        items.add(
            PauseMenuItem(label = label, type = PauseMenuItemType.OPTIONS, onSelect = emptyList())
        )
    }

    /** Add a "Quit" item. */
    fun quit(label: String = "QUIT", action: () -> Unit = {}) {
        items.add(
            PauseMenuItem(label = label, type = PauseMenuItemType.QUIT, onSelect = emptyList())
        )
    }

    /** Add a custom item. */
    fun item(label: String, action: () -> Unit = {}) {
        items.add(
            PauseMenuItem(label = label, type = PauseMenuItemType.CUSTOM, onSelect = emptyList())
        )
    }

    // =========================================================================
    // STYLING
    // =========================================================================

    /** Configure menu style. */
    fun style(init: PauseMenuStyleBuilder.() -> Unit) {
        val builder = PauseMenuStyleBuilder()
        builder.init()
        style = builder.build()
    }

    // =========================================================================
    // CALLBACKS
    // =========================================================================

    /** Callback when the pause menu is opened. */
    fun onOpen(block: () -> Unit) {
        onOpenStatements = emptyList()
    }

    /** Callback when the pause menu is closed. */
    fun onClose(block: () -> Unit) {
        onCloseStatements = emptyList()
    }

    internal fun build(): PauseMenuConfig =
        PauseMenuConfig(
            name = name,
            items = items.toList(),
            style = style,
            autoWireStartButton = autoWireStartButton,
            pauseGameLogic = pauseGameLogic,
            onOpen = onOpenStatements,
            onClose = onCloseStatements,
        )
}

/** Builder for pause menu style. */
@GbktDsl
class PauseMenuStyleBuilder {
    private var x: Int = 5
    private var y: Int = 4
    private var width: Int = 10
    private var showBorder: Boolean = true
    private var cursor: String = ">"
    private var dimBackground: Boolean = true

    /** Set menu position. */
    fun position(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    /** Set menu width. */
    fun width(w: Int) {
        width = w
    }

    /** Show/hide border. */
    fun border(show: Boolean) {
        showBorder = show
    }

    /** Set cursor character. */
    fun cursor(char: String) {
        cursor = char
    }

    /** Dim background while paused. */
    fun dimBackground(dim: Boolean) {
        dimBackground = dim
    }

    internal fun build(): PauseMenuStyle =
        PauseMenuStyle(
            x = x,
            y = y,
            width = width,
            showBorder = showBorder,
            cursor = cursor,
            dimBackground = dimBackground,
        )
}

/**
 * Handle for runtime pause menu operations.
 *
 * Usage:
 * ```kotlin
 * scene("gameplay") {
 *     every.frame {
 *         pauseMenu.tick()  // Handles input and drawing
 *
 *         unless(pauseMenu.isOpen) {
 *             // Only run game logic when not paused
 *             updatePlayer()
 *             updateEnemies()
 *         }
 *     }
 * }
 * ```
 */
class PauseMenuHandle internal constructor(private val config: PauseMenuConfig) {
    /** Menu name. */
    val name: String
        get() = config.name

    /** Number of items. */
    val itemCount: Int
        get() = config.items.size

    /** Whether auto-wire is enabled. */
    val isAutoWired: Boolean
        get() = config.autoWireStartButton

    /** Whether game logic should pause. */
    val pausesLogic: Boolean
        get() = config.pauseGameLogic
}

/**
 * Create a pause menu.
 *
 * @param name The name of the menu
 * @param init Builder initialization block
 * @return The configured pause menu handle
 */
fun pauseMenu(name: String, init: PauseMenuBuilder.() -> Unit): PauseMenuHandle {
    val builder = PauseMenuBuilder(name)
    builder.init()
    return PauseMenuHandle(builder.build())
}
