/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// IR NODES FOR MENU SYSTEM
// =============================================================================

// Lifecycle
/** Show a menu (make visible and focused) */
data class IRMenuShow(val menuName: String) : IRStatement

/** Hide a menu */
data class IRMenuHide(val menuName: String) : IRStatement

/** Toggle menu visibility */
data class IRMenuToggle(val menuName: String) : IRStatement

/** Update menu - process input, navigation, render */
data class IRMenuTick(val menuName: String) : IRStatement

// Navigation
/** Move cursor to specific index */
data class IRMenuMoveTo(val menuName: String, val index: IRExpression) : IRStatement

/** Programmatically select current item */
data class IRMenuSelect(val menuName: String) : IRStatement

/** Programmatically cancel (B button) */
data class IRMenuCancel(val menuName: String) : IRStatement

// Submenu stack
/** Open a child menu (push onto stack) */
data class IRMenuOpen(val menuName: String) : IRStatement

/** Close current menu (pop from stack) */
data object IRMenuClose : IRStatement

// State queries (expressions)
/** Check if menu is visible */
data class IRMenuIsVisible(val menuName: String) : IRExpression

/** Check if menu is active (has focus) */
data class IRMenuIsActive(val menuName: String) : IRExpression

/** Get current cursor index */
data class IRMenuSelectedIndex(val menuName: String) : IRExpression

/** Get grid cursor X position */
data class IRMenuCursorX(val menuName: String) : IRExpression

/** Get grid cursor Y position */
data class IRMenuCursorY(val menuName: String) : IRExpression
