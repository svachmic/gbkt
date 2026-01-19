/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.rpg.BattleMenuSystem
import io.github.gbkt.core.rpg.BattleMenuType

// =============================================================================
// BATTLE MENU IR NODES
// =============================================================================

/** IR node for configuring battle menu system. */
data class IRBattleMenuConfig(
    val system: BattleMenuSystem,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for opening a battle menu. */
data class IRBattleMenuOpen(
    val systemName: String,
    val menuType: BattleMenuType,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for closing the current battle menu. */
data class IRBattleMenuClose(
    val systemName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for processing battle menu input. */
data class IRBattleMenuTick(
    val systemName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for selecting current menu item. */
data class IRBattleMenuSelect(
    val systemName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for canceling (going back) in battle menu. */
data class IRBattleMenuCancel(
    val systemName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for moving cursor up. */
data class IRBattleMenuCursorUp(
    val systemName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for moving cursor down. */
data class IRBattleMenuCursorDown(
    val systemName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for setting cursor position directly. */
data class IRBattleMenuSetCursor(
    val systemName: String,
    val index: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

// =============================================================================
// BATTLE MENU QUERY EXPRESSIONS
// =============================================================================

/** IR expression for getting current menu cursor index. */
data class IRBattleMenuCursor(val systemName: String) : IRExpression

/** IR expression for checking if battle menu is visible. */
data class IRBattleMenuIsVisible(val systemName: String) : IRExpression

/** IR expression for checking if battle menu is active. */
data class IRBattleMenuIsActive(val systemName: String) : IRExpression

/** IR expression for getting current menu type. */
data class IRBattleMenuGetType(val systemName: String) : IRExpression

/** IR expression for getting selected ability index. */
data class IRBattleMenuSelectedAbility(val systemName: String) : IRExpression

/** IR expression for getting selected item index. */
data class IRBattleMenuSelectedItem(val systemName: String) : IRExpression

/** IR expression for getting selected target index. */
data class IRBattleMenuSelectedTarget(val systemName: String) : IRExpression

/** IR expression for getting number of menu items. */
data class IRBattleMenuItemCount(val systemName: String) : IRExpression
