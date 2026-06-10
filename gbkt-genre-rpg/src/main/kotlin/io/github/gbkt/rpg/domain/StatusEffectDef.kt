/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

import io.github.gbkt.core.ir.ScriptOp

/**
 * Domain data class representing a status effect definition.
 *
 * Plain Kotlin data class — NOT an IR type. Used by [io.github.gbkt.rpg.dsl.StatusEffectBuilder] to
 * carry status effect data. The DSL extension function produces a
 * [io.github.gbkt.core.ir.GenericSystem] from this data.
 *
 * Status effects modify combat by applying ongoing conditions (DOT, buff/debuff, CC) with
 * configurable duration, stacking, triggers, and immunity rules.
 *
 * @property id Unique identifier used in DSL references.
 * @property name Display name shown in battle UI.
 * @property category Broad category for immunity and grouping.
 * @property duration Duration in turns; 0 = permanent until cleansed.
 * @property damagePerTurn DOT damage applied each turn.
 * @property healPerTurn HP recovery applied each turn.
 * @property stackMode How multiple applications of this effect interact.
 * @property maxStacks Maximum number of stacks allowed (relevant to [StackMode.INTENSITY]).
 * @property triggers Set of events that fire [triggerOps].
 * @property triggerOps Script ops executed per trigger event.
 * @property immuneCategories Effect categories this effect is immune to (cannot coexist with).
 * @property interactsWith Map of effectId to interaction type: "cancels", "converts_to", or
 *   "bonus_damage".
 * @property onStackAppliedOps Ops triggered when a new stack is added (INTENSITY mode only).
 * @property onStackRemovedOps Ops triggered when a stack is removed (INTENSITY mode only).
 * @property applyChance Flat percentage chance to apply this effect (0-100).
 * @property resistType How resistance is resolved — FLAT uses [applyChance] directly; STAT_CONTEST
 *   subtracts stat difference. (GAP-5)
 * @property resistStat Target stat name used in STAT_CONTEST resist calculation. Default "mdef".
 *   (GAP-5)
 * @property immuneToEffects Set of specific effect IDs this character is immune to. Checked
 *   alongside [immuneCategories]. (GAP-6)
 * @property perStackScaling When true and [stackMode] is INTENSITY, [damagePerTurn] is multiplied
 *   by current stack count. (GAP-7)
 * @property isDebuff Derived flag — true if category is DEBUFF, DOT, or CROWD_CONTROL.
 */
data class StatusEffectDef(
    val id: String,
    val name: String,
    val category: EffectCategory = EffectCategory.DEBUFF,
    val duration: Int = 3,
    val damagePerTurn: Int = 0,
    val healPerTurn: Int = 0,
    val stackMode: StackMode = StackMode.REFRESH_DURATION,
    val maxStacks: Int = 1,
    val triggers: Set<EffectTrigger> = emptySet(),
    val triggerOps: Map<EffectTrigger, List<ScriptOp>> = emptyMap(),
    val immuneCategories: Set<EffectCategory> = emptySet(),
    val interactsWith: Map<String, String> = emptyMap(),
    val onStackAppliedOps: List<ScriptOp> = emptyList(),
    val onStackRemovedOps: List<ScriptOp> = emptyList(),
    val applyChance: Int = 100,
    val resistType: ResistType = ResistType.FLAT,
    val resistStat: String = "mdef",
    val immuneToEffects: Set<String> = emptySet(),
    val perStackScaling: Boolean = false,
) {
    /** Derived flag — true if category is DEBUFF, DOT, or CROWD_CONTROL. */
    val isDebuff: Boolean
        get() =
            category == EffectCategory.DEBUFF ||
                category == EffectCategory.DOT ||
                category == EffectCategory.CROWD_CONTROL
}
