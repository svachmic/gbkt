/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRStatClamp
import io.github.gbkt.core.ir.IRStatDamage
import io.github.gbkt.core.ir.IRStatModify
import io.github.gbkt.core.ir.StatType

// =============================================================================
// SHARED STAT OPERATIONS
// =============================================================================

/**
 * Common stat modification operations extracted to reduce code duplication across StatAccessor,
 * ItemStatAccessor, and LevelUpStatAccessor.
 */
internal object StatOperations {
    /**
     * Emit IR to add a value to a stat.
     *
     * @param ownerName Character or entity owning the stat
     * @param statType Type of stat to modify
     * @param value Amount to add
     * @param clamp Whether to clamp to max value after addition
     * @param useMax Whether to modify the max stat instead of current
     */
    fun emitAdd(
        ownerName: String,
        statType: StatType,
        value: Int,
        clamp: Boolean = true,
        useMax: Boolean = false,
    ) {
        if (!RecordingContext.isRecording) return

        RecordingContext.require()
            .emit(IRStatModify(ownerName, statType, IRLiteral(value), AssignOp.ADD, useMax))
        if (clamp && !useMax) {
            RecordingContext.require().emit(IRStatClamp(ownerName, statType))
        }
    }

    /**
     * Emit IR to subtract from a stat (with floor at 0).
     *
     * @param ownerName Character or entity owning the stat
     * @param statType Type of stat to modify
     * @param value Amount to subtract
     */
    fun emitSubtract(ownerName: String, statType: StatType, value: Int) {
        if (!RecordingContext.isRecording) return

        RecordingContext.require().emit(IRStatDamage(ownerName, statType, IRLiteral(value)))
    }

    /**
     * Emit IR to set a stat to an exact value.
     *
     * @param ownerName Character or entity owning the stat
     * @param statType Type of stat to modify
     * @param value Value to set
     * @param useMax Whether to set the max stat instead of current
     */
    fun emitSet(ownerName: String, statType: StatType, value: Int, useMax: Boolean = false) {
        if (!RecordingContext.isRecording) return

        RecordingContext.require()
            .emit(IRStatModify(ownerName, statType, IRLiteral(value), AssignOp.SET, useMax))
    }
}

/**
 * Interface for types that can modify stats.
 *
 * Implemented by StatAccessor, ItemStatAccessor, and LevelUpStatAccessor.
 */
interface StatModifier {
    /** Add to stat value */
    operator fun plusAssign(value: Int)

    /** Set stat to exact value */
    infix fun set(value: Int)
}

/**
 * Interface for types that can also subtract from stats.
 *
 * Extends StatModifier with damage/subtract capability.
 */
interface DamageCapableStatModifier : StatModifier {
    /** Subtract from stat value with floor at 0 */
    operator fun minusAssign(value: Int)
}
