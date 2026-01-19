/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation

// =============================================================================
// STATUS BAR IR NODES
// =============================================================================

/** IR: Update status bar with new value */
data class IRStatusBarSetValue(
    val name: String,
    val currentValue: IRExpression,
    val maxValue: IRExpression,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR: Show status bar */
data class IRStatusBarShow(val name: String, override val sourceLocation: SourceLocation? = null) :
    IRStatement

/** IR: Hide status bar */
data class IRStatusBarHide(val name: String, override val sourceLocation: SourceLocation? = null) :
    IRStatement

/** IR: Update status bar animation */
data class IRStatusBarTick(val name: String, override val sourceLocation: SourceLocation? = null) :
    IRStatement

/** IR: Flash status bar (for damage/heal effects) */
data class IRStatusBarFlash(
    val name: String,
    val duration: IRExpression,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement
