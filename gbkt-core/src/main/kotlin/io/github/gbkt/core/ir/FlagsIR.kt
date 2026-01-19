/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.world.FlagRef

// =============================================================================
// FLAGS IR NODES - Global flags system
// =============================================================================

/** IR expression for checking if a flag is set. */
data class IRFlagIsSet(val flag: FlagRef) : IRExpression

/** IR statement for setting a flag. */
data class IRSetFlag(val flag: FlagRef, override val sourceLocation: SourceLocation? = null) :
    IRStatement

/** IR statement for clearing a flag. */
data class IRClearFlag(val flag: FlagRef, override val sourceLocation: SourceLocation? = null) :
    IRStatement

/** IR statement for toggling a flag. */
data class IRToggleFlag(val flag: FlagRef, override val sourceLocation: SourceLocation? = null) :
    IRStatement
