/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRBattleMenuCancel
import io.github.gbkt.core.ir.IRBattleMenuClose
import io.github.gbkt.core.ir.IRBattleMenuConfig
import io.github.gbkt.core.ir.IRBattleMenuOpen
import io.github.gbkt.core.ir.IRBattleMenuSelect
import io.github.gbkt.core.ir.IRBattleMenuTick
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// BATTLE MENU SYSTEM
// =============================================================================

/** Type of battle menu. */
enum class BattleMenuType {
    /** Main battle menu (Attack, Ability, Item, Defend, Flee) */
    MAIN,
    /** Ability/skill selection menu */
    ABILITY,
    /** Item selection menu */
    ITEM,
    /** Target selection (enemy) */
    TARGET_ENEMY,
    /** Target selection (ally) */
    TARGET_ALLY,
    /** Target selection (all) */
    TARGET_ALL,
}

/** Battle menu position preset. */
enum class BattleMenuPosition {
    /** Bottom left of screen (typical for main menu) */
    BOTTOM_LEFT,
    /** Bottom center (for status displays) */
    BOTTOM_CENTER,
    /** Bottom right (for secondary menus) */
    BOTTOM_RIGHT,
    /** Right side (for ability/item lists) */
    RIGHT_SIDE,
    /** Full width at bottom */
    BOTTOM_FULL,
}

/** Battle menu command representation. */
data class BattleMenuCommand(
    val type: BattleActionType,
    val label: String,
    val enabled: IRExpression = IRLiteral(1),
    val onSelect: List<IRStatement> = emptyList(),
)

/** Battle menu definition. */
data class BattleMenuDefinition(
    val name: String,
    val menuType: BattleMenuType,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val commands: List<BattleMenuCommand> = emptyList(),
    val cursorChar: Char = '>',
    val showFrame: Boolean = true,
)

/** Battle menu system configuration. */
data class BattleMenuSystem(
    val name: String,
    val mainMenu: BattleMenuDefinition,
    val abilityMenu: BattleMenuDefinition?,
    val itemMenu: BattleMenuDefinition?,
    val targetMenu: BattleMenuDefinition?,
    val statusDisplay: BattleStatusConfig?,
)

/** Battle status display configuration. */
data class BattleStatusConfig(
    val x: Int,
    val y: Int,
    val width: Int,
    val showHP: Boolean = true,
    val showSP: Boolean = true,
    val showName: Boolean = true,
    val showLevel: Boolean = false,
    val hpBarWidth: Int = 8,
)

/** Handle for battle menu runtime operations. */
class BattleMenuHandle internal constructor(internal val system: BattleMenuSystem) {
    /** Open the main battle menu */
    fun openMain() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRBattleMenuOpen(system.name, BattleMenuType.MAIN))
        }
    }

    /** Open the ability selection menu */
    fun openAbilities() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRBattleMenuOpen(system.name, BattleMenuType.ABILITY))
        }
    }

    /** Open the item selection menu */
    fun openItems() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRBattleMenuOpen(system.name, BattleMenuType.ITEM))
        }
    }

    /** Open target selection for enemies */
    fun openEnemyTargets() {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRBattleMenuOpen(system.name, BattleMenuType.TARGET_ENEMY))
        }
    }

    /** Open target selection for allies */
    fun openAllyTargets() {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRBattleMenuOpen(system.name, BattleMenuType.TARGET_ALLY))
        }
    }

    /** Close the current menu (go back) */
    fun close() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRBattleMenuClose(system.name))
        }
    }

    /** Process menu input and rendering */
    fun tick() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRBattleMenuTick(system.name))
        }
    }

    /** Select current item */
    fun select() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRBattleMenuSelect(system.name))
        }
    }

    /** Cancel (B button) */
    fun cancel() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRBattleMenuCancel(system.name))
        }
    }
}

// =============================================================================
// BATTLE MENU BUILDER
// =============================================================================

/** Builder for battle menu system. */
@GbktDsl
class BattleMenuBuilder internal constructor(private val name: String) {
    private var mainMenuDef: BattleMenuDefinition? = null
    private var abilityMenuDef: BattleMenuDefinition? = null
    private var itemMenuDef: BattleMenuDefinition? = null
    private var targetMenuDef: BattleMenuDefinition? = null
    private var statusConfig: BattleStatusConfig? = null

    /**
     * Configure the main battle menu.
     *
     * Example:
     * ```kotlin
     * mainMenu {
     *     position(1, 12)
     *     command(BattleActionType.ATTACK, "ATTACK")
     *     command(BattleActionType.ABILITY, "MAGIC")
     *     command(BattleActionType.ITEM, "ITEM")
     *     command(BattleActionType.DEFEND, "GUARD")
     *     command(BattleActionType.FLEE, "RUN")
     * }
     * ```
     */
    fun mainMenu(block: BattleMainMenuBuilder.() -> Unit) {
        val builder = BattleMainMenuBuilder(name)
        builder.block()
        mainMenuDef = builder.build()
    }

    /** Configure the ability selection menu. */
    fun abilityMenu(block: BattleSubMenuBuilder.() -> Unit) {
        val builder = BattleSubMenuBuilder(name, BattleMenuType.ABILITY)
        builder.block()
        abilityMenuDef = builder.build()
    }

    /** Configure the item selection menu. */
    fun itemMenu(block: BattleSubMenuBuilder.() -> Unit) {
        val builder = BattleSubMenuBuilder(name, BattleMenuType.ITEM)
        builder.block()
        itemMenuDef = builder.build()
    }

    /** Configure the target selection menu. */
    fun targetMenu(block: BattleTargetMenuBuilder.() -> Unit) {
        val builder = BattleTargetMenuBuilder(name)
        builder.block()
        targetMenuDef = builder.build()
    }

    /** Configure the status display area. */
    fun statusDisplay(block: BattleStatusBuilder.() -> Unit) {
        val builder = BattleStatusBuilder()
        builder.block()
        statusConfig = builder.build()
    }

    internal fun build(): BattleMenuSystem {
        // Create default main menu if not specified
        val main =
            mainMenuDef
                ?: BattleMenuDefinition(
                    name = "${name}_main",
                    menuType = BattleMenuType.MAIN,
                    x = 1,
                    y = 12,
                    width = 8,
                    height = 6,
                    commands =
                        listOf(
                            BattleMenuCommand(BattleActionType.ATTACK, "ATTACK"),
                            BattleMenuCommand(BattleActionType.ABILITY, "SKILL"),
                            BattleMenuCommand(BattleActionType.ITEM, "ITEM"),
                            BattleMenuCommand(BattleActionType.DEFEND, "GUARD"),
                            BattleMenuCommand(BattleActionType.FLEE, "FLEE"),
                        ),
                )

        return BattleMenuSystem(
            name = name,
            mainMenu = main,
            abilityMenu = abilityMenuDef,
            itemMenu = itemMenuDef,
            targetMenu = targetMenuDef,
            statusDisplay = statusConfig,
        )
    }
}

/** Builder for main battle menu. */
@GbktDsl
class BattleMainMenuBuilder internal constructor(private val systemName: String) {
    private var x = 1
    private var y = 12
    private var width = 8
    private var height = 6
    private val commands = mutableListOf<BattleMenuCommand>()
    private var cursorChar = '>'
    private var showFrame = true

    /** Set menu position in tiles */
    fun position(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    /** Set menu size */
    fun size(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /** Set cursor character */
    fun cursor(char: Char) {
        this.cursorChar = char
    }

    /** Show or hide menu frame */
    fun showFrame(show: Boolean) {
        this.showFrame = show
    }

    /** Add a command to the menu. */
    fun command(
        type: BattleActionType,
        label: String,
        block: BattleCommandBuilder.() -> Unit = {},
    ) {
        val builder = BattleCommandBuilder(type, label)
        builder.block()
        commands.add(builder.build())
    }

    internal fun build() =
        BattleMenuDefinition(
            name = "${systemName}_main",
            menuType = BattleMenuType.MAIN,
            x = x,
            y = y,
            width = width,
            height = height,
            commands = commands.toList(),
            cursorChar = cursorChar,
            showFrame = showFrame,
        )
}

/** Builder for battle command entry. */
@GbktDsl
class BattleCommandBuilder
internal constructor(private val type: BattleActionType, private val label: String) {
    private var enabled: IRExpression = IRLiteral(1)
    private var onSelectStatements: List<IRStatement> = emptyList()

    /** Set enabled condition */
    fun enabled(condition: io.github.gbkt.core.ir.Condition) {
        enabled = condition.ir
    }

    /** Called when command is selected */
    fun onSelect(block: BattleMenuActionScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { BattleMenuActionScope().block() }
        onSelectStatements = recorder.statements
    }

    internal fun build() =
        BattleMenuCommand(
            type = type,
            label = label,
            enabled = enabled,
            onSelect = onSelectStatements,
        )
}

/** Builder for ability/item submenus. */
@GbktDsl
class BattleSubMenuBuilder
internal constructor(private val systemName: String, private val menuType: BattleMenuType) {
    private var x = 10
    private var y = 4
    private var width = 10
    private var height = 12
    private var cursorChar = '>'
    private var showFrame = true

    /** Set menu position in tiles */
    fun position(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    /** Set menu size */
    fun size(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /** Set cursor character */
    fun cursor(char: Char) {
        this.cursorChar = char
    }

    /** Show or hide menu frame */
    fun showFrame(show: Boolean) {
        this.showFrame = show
    }

    internal fun build() =
        BattleMenuDefinition(
            name = "${systemName}_${menuType.name.lowercase()}",
            menuType = menuType,
            x = x,
            y = y,
            width = width,
            height = height,
            cursorChar = cursorChar,
            showFrame = showFrame,
        )
}

/** Builder for target selection menu. */
@GbktDsl
class BattleTargetMenuBuilder internal constructor(private val systemName: String) {
    private var x = 0
    private var y = 0
    private var width = 20
    private var height = 10
    private var cursorChar = '>'

    /** Set target area position */
    fun position(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    /** Set target area size */
    fun size(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /** Set cursor character */
    fun cursor(char: Char) {
        this.cursorChar = char
    }

    internal fun build() =
        BattleMenuDefinition(
            name = "${systemName}_target",
            menuType = BattleMenuType.TARGET_ENEMY,
            x = x,
            y = y,
            width = width,
            height = height,
            cursorChar = cursorChar,
            showFrame = false,
        )
}

/** Builder for battle status display. */
@GbktDsl
class BattleStatusBuilder {
    private var x = 10
    private var y = 12
    private var width = 10
    private var showHP = true
    private var showSP = true
    private var showName = true
    private var showLevel = false
    private var hpBarWidth = 8

    /** Set status display position */
    fun position(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    /** Set display width */
    fun width(w: Int) {
        this.width = w
    }

    /** Configure what to show */
    fun show(hp: Boolean = true, sp: Boolean = true, name: Boolean = true, level: Boolean = false) {
        showHP = hp
        showSP = sp
        showName = name
        showLevel = level
    }

    /** Set HP bar width in tiles */
    fun hpBarWidth(width: Int) {
        this.hpBarWidth = width
    }

    internal fun build() =
        BattleStatusConfig(
            x = x,
            y = y,
            width = width,
            showHP = showHP,
            showSP = showSP,
            showName = showName,
            showLevel = showLevel,
            hpBarWidth = hpBarWidth,
        )
}

/** Scope available inside battle menu action callbacks. */
@GbktDsl
class BattleMenuActionScope(private val systemName: String = "battle") {
    /** Transition to a battle state */
    fun transitionTo(state: BattleState) {
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRBattleStateTransition(
                    systemName = systemName,
                    targetState = state,
                )
            )
    }

    // ==========================================================================
    // CURSOR MOVEMENT
    // ==========================================================================

    /** Move cursor up */
    fun cursorUp() {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRBattleMenuCursorUp(systemName))
    }

    /** Move cursor down */
    fun cursorDown() {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRBattleMenuCursorDown(systemName))
    }

    /** Set cursor to specific index */
    fun setCursor(index: Int) {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRBattleMenuSetCursor(systemName, index))
    }

    /** Confirm current selection */
    fun confirmSelection() {
        RecordingContext.require().emit(IRBattleMenuSelect(systemName))
    }

    /** Cancel / go back */
    fun cancelMenu() {
        RecordingContext.require().emit(IRBattleMenuCancel(systemName))
    }

    // ==========================================================================
    // CONTEXT QUERIES (as IR expressions for use in conditions)
    // ==========================================================================

    /** Get current cursor index as IR expression */
    val cursorIndex: IRExpression
        get() = io.github.gbkt.core.ir.IRBattleMenuCursor(systemName)

    /** Check if menu is visible */
    val isVisible: IRExpression
        get() = io.github.gbkt.core.ir.IRBattleMenuIsVisible(systemName)

    /** Check if menu is active (receiving input) */
    val isActive: IRExpression
        get() = io.github.gbkt.core.ir.IRBattleMenuIsActive(systemName)

    /** Get current menu type */
    val menuType: IRExpression
        get() = io.github.gbkt.core.ir.IRBattleMenuGetType(systemName)

    /** Get selected ability index (when in ability menu) */
    val selectedAbilityIndex: IRExpression
        get() = io.github.gbkt.core.ir.IRBattleMenuSelectedAbility(systemName)

    /** Get selected item index (when in item menu) */
    val selectedItemIndex: IRExpression
        get() = io.github.gbkt.core.ir.IRBattleMenuSelectedItem(systemName)

    /** Get selected target index (when in target menu) */
    val selectedTargetIndex: IRExpression
        get() = io.github.gbkt.core.ir.IRBattleMenuSelectedTarget(systemName)

    /** Get number of items in current menu */
    val itemCount: IRExpression
        get() = io.github.gbkt.core.ir.IRBattleMenuItemCount(systemName)

    /** Emit arbitrary statement */
    internal fun emit(statement: IRStatement) {
        RecordingContext.require().emit(statement)
    }
}

// =============================================================================
// DSL FUNCTIONS
// =============================================================================

/**
 * Create a battle menu system.
 *
 * Example:
 * ```kotlin
 * val battleMenu = battleMenu("battle") {
 *     mainMenu {
 *         position(1, 12)
 *         command(BattleActionType.ATTACK, "ATTACK")
 *         command(BattleActionType.ABILITY, "MAGIC")
 *         command(BattleActionType.ITEM, "ITEM")
 *         command(BattleActionType.DEFEND, "GUARD")
 *         command(BattleActionType.FLEE, "RUN")
 *     }
 *     statusDisplay {
 *         position(10, 12)
 *         show(hp = true, sp = true)
 *     }
 * }
 * ```
 */
fun battleMenu(name: String, block: BattleMenuBuilder.() -> Unit): BattleMenuHandle {
    val builder = BattleMenuBuilder(name)
    builder.block()
    val system = builder.build()
    return BattleMenuHandle(system)
}

/** Register battle menu system for code generation. */
fun registerBattleMenu(menu: BattleMenuHandle) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRBattleMenuConfig(menu.system))
    }
}
