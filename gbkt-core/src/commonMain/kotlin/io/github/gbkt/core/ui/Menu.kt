/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ui

import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRMenuCancel
import io.github.gbkt.core.ir.IRMenuCursorX
import io.github.gbkt.core.ir.IRMenuCursorY
import io.github.gbkt.core.ir.IRMenuHide
import io.github.gbkt.core.ir.IRMenuIsActive
import io.github.gbkt.core.ir.IRMenuIsVisible
import io.github.gbkt.core.ir.IRMenuMoveTo
import io.github.gbkt.core.ir.IRMenuSelect
import io.github.gbkt.core.ir.IRMenuSelectedIndex
import io.github.gbkt.core.ir.IRMenuShow
import io.github.gbkt.core.ir.IRMenuTick
import io.github.gbkt.core.ir.IRMenuToggle

// =============================================================================
// MENU SYSTEM DSL - Menu Handle and IR Nodes
// =============================================================================

/**
 * Handle for menu runtime operations. Returned by menu() DSL function and used to show/hide/update
 * menus.
 *
 * Usage: val mainMenu = menu("main") { ... }
 *
 * scene("title") { enter { mainMenu.show() } every.frame { mainMenu.tick() } }
 */
class MenuHandle internal constructor(internal val definition: MenuDefinition) {
    /** Show the menu (make visible and give focus) */
    fun show() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRMenuShow(definition.name))
        }
    }

    /** Hide the menu */
    fun hide() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRMenuHide(definition.name))
        }
    }

    /** Toggle menu visibility */
    fun toggle() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRMenuToggle(definition.name))
        }
    }

    /**
     * Update menu - process input, handle navigation, render. MUST be called in every.frame when
     * menu is active.
     */
    fun tick() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRMenuTick(definition.name))
        }
    }

    /** Jump cursor to specific index */
    fun moveTo(index: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRMenuMoveTo(definition.name, IRLiteral(index)))
        }
    }

    /** Jump cursor to specific index (from expression) */
    fun moveTo(index: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRMenuMoveTo(definition.name, index.ir))
        }
    }

    /** Programmatically select current item */
    fun selectCurrent() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRMenuSelect(definition.name))
        }
    }

    /** Programmatically cancel (B button behavior) */
    fun cancel() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRMenuCancel(definition.name))
        }
    }

    // =========================================================================
    // State Conditions
    // =========================================================================

    /** Condition: is menu currently visible? */
    val isVisible: Condition
        get() = Condition(IRMenuIsVisible(definition.name))

    /** Condition: is menu currently active (has focus)? */
    val isActive: Condition
        get() = Condition(IRMenuIsActive(definition.name))

    /** Expression: current cursor index */
    val selectedIndex: Expr
        get() = Expr(IRMenuSelectedIndex(definition.name))

    /** For grid menus: cursor X position */
    val cursorX: Expr
        get() = Expr(IRMenuCursorX(definition.name))

    /** For grid menus: cursor Y position */
    val cursorY: Expr
        get() = Expr(IRMenuCursorY(definition.name))
}
