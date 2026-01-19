/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRConfirmTarget
import io.github.gbkt.core.ir.IRGetSelectedTargetCount
import io.github.gbkt.core.ir.IRGetSelectedTargetIndex
import io.github.gbkt.core.ir.IRIsTargetAlive
import io.github.gbkt.core.ir.IRIsTargetEnemy
import io.github.gbkt.core.ir.IRIsTargetSelectionActive
import io.github.gbkt.core.ir.IRMoveTargetCursor
import io.github.gbkt.core.ir.IRSelectAllTargets
import io.github.gbkt.core.ir.IRSelectTarget
import io.github.gbkt.core.ir.IRStartTargetSelection
import io.github.gbkt.core.ir.IRTargetSelectionConfig
import io.github.gbkt.core.ir.IRTargetSelectionTick

// =============================================================================
// TARGET SELECTION SYSTEM
// =============================================================================

/** Target selection result. */
data class SelectedTarget(val index: Int, val isEnemy: Boolean, val isAlive: Boolean)

/** Target selection configuration. */
data class TargetSelectionConfig(
    val name: String,
    val maxTargets: Int = 8,
    val cursorChar: Char = '>',
    val showTargetName: Boolean = true,
    val showTargetHP: Boolean = true,
    val allowMultiSelect: Boolean = false,
)

/** Handle for target selection runtime operations. */
class TargetSelectionHandle internal constructor(internal val config: TargetSelectionConfig) {
    /** Start target selection for enemies. */
    fun selectEnemy() {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRStartTargetSelection(config.name, TargetingMode.SINGLE_ENEMY))
        }
    }

    /** Start target selection for all enemies. */
    fun selectAllEnemies() {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRStartTargetSelection(config.name, TargetingMode.ALL_ENEMIES))
        }
    }

    /** Start target selection for allies. */
    fun selectAlly() {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRStartTargetSelection(config.name, TargetingMode.SINGLE_ALLY))
        }
    }

    /** Start target selection for all allies. */
    fun selectAllAllies() {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRStartTargetSelection(config.name, TargetingMode.ALL_ALLIES))
        }
    }

    /** Start target selection for self. */
    fun selectSelf() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRStartTargetSelection(config.name, TargetingMode.SELF))
        }
    }

    /** Move target cursor left. */
    fun cursorLeft() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRMoveTargetCursor(config.name, -1, 0))
        }
    }

    /** Move target cursor right. */
    fun cursorRight() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRMoveTargetCursor(config.name, 1, 0))
        }
    }

    /** Move target cursor up. */
    fun cursorUp() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRMoveTargetCursor(config.name, 0, -1))
        }
    }

    /** Move target cursor down. */
    fun cursorDown() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRMoveTargetCursor(config.name, 0, 1))
        }
    }

    /** Select current target. */
    fun selectCurrent() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRSelectTarget(config.name))
        }
    }

    /** Select all valid targets. */
    fun selectAll() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRSelectAllTargets(config.name))
        }
    }

    /** Confirm target selection. */
    fun confirm() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRConfirmTarget(config.name))
        }
    }

    /** Process target selection input. */
    fun tick() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRTargetSelectionTick(config.name))
        }
    }

    // =========================================================================
    // State Queries
    // =========================================================================

    /** Is target selection currently active? */
    val isActive: Condition
        get() = Condition(IRIsTargetSelectionActive(config.name))

    /** Get current target cursor index */
    val cursorIndex: Expr
        get() = Expr(IRGetSelectedTargetIndex(config.name))

    /** Get number of selected targets */
    val selectedCount: Expr
        get() = Expr(IRGetSelectedTargetCount(config.name))
}

// =============================================================================
// TARGET SELECTION BUILDER
// =============================================================================

/** Builder for target selection configuration. */
@GbktDsl
class TargetSelectionBuilder internal constructor(private val name: String) {
    private var maxTargets = 8
    private var cursorChar = '>'
    private var showTargetName = true
    private var showTargetHP = true
    private var allowMultiSelect = false

    /** Set maximum number of selectable targets */
    fun maxTargets(count: Int) {
        require(count in 1..16) { "Max targets must be 1-16" }
        this.maxTargets = count
    }

    /** Set cursor character */
    fun cursor(char: Char) {
        this.cursorChar = char
    }

    /** Configure what info to show */
    fun showInfo(name: Boolean = true, hp: Boolean = true) {
        showTargetName = name
        showTargetHP = hp
    }

    /** Allow selecting multiple targets */
    fun allowMultiSelect(allow: Boolean) {
        this.allowMultiSelect = allow
    }

    internal fun build() =
        TargetSelectionConfig(
            name = name,
            maxTargets = maxTargets,
            cursorChar = cursorChar,
            showTargetName = showTargetName,
            showTargetHP = showTargetHP,
            allowMultiSelect = allowMultiSelect,
        )
}

// =============================================================================
// DSL FUNCTIONS
// =============================================================================

/**
 * Create a target selection system.
 *
 * Example:
 * ```kotlin
 * val targeting = targetSelection("battle") {
 *     maxTargets(8)
 *     cursor('>')
 *     showInfo(name = true, hp = true)
 * }
 * ```
 */
fun targetSelection(
    name: String,
    block: TargetSelectionBuilder.() -> Unit = {},
): TargetSelectionHandle {
    val builder = TargetSelectionBuilder(name)
    builder.block()
    val config = builder.build()
    return TargetSelectionHandle(config)
}

/** Register target selection system for code generation. */
fun registerTargetSelection(targeting: TargetSelectionHandle) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IRTargetSelectionConfig(targeting.config))
    }
}

// =============================================================================
// TARGET QUERY FUNCTIONS
// =============================================================================

/** Check if target at index is alive. */
fun isTargetAlive(index: Int): Condition {
    return Condition(IRIsTargetAlive(index))
}

/** Check if target at index is an enemy. */
fun isTargetEnemy(index: Int): Condition {
    return Condition(IRIsTargetEnemy(index))
}
