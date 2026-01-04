/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// IR NODES FOR DIALOG SYSTEM
// =============================================================================

/** Show a dialog box */
data class IRDialogShow(val dialogName: String) : IRStatement

/** Hide a dialog box */
data class IRDialogHide(val dialogName: String) : IRStatement

/** Display text with typewriter effect */
data class IRDialogSay(
    val dialogName: String?, // null = use default inline dialog
    val text: List<TextPart>,
    val speaker: String? = null,
    val waitForInput: Boolean = true,
    val autoAdvanceFrames: Int = 0
) : IRStatement

/** Display choice menu */
data class IRDialogChoice(
    val dialogName: String,
    val options: List<String>,
    val resultVar: String
) : IRStatement

/** Update typewriter animation (call each frame) */
data class IRDialogTick(val dialogName: String) : IRStatement

/** Check if dialog is currently visible (expression) */
data class IRDialogIsActive(val dialogName: String) : IRExpression

/** Check if dialog has finished displaying (expression) */
data class IRDialogIsComplete(val dialogName: String) : IRExpression
