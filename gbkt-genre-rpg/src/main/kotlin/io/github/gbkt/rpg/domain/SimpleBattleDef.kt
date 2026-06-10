/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

import io.github.gbkt.core.ir.ScriptOp

/**
 * A single monster slot within an encounter group, with optional level and tier overrides.
 *
 * Used in [EncounterDef.slots] to specify per-instance monster scaling. Without overrides, the
 * monster uses its default stats from [MonsterDef].
 *
 * @property monsterId ID of the monster to spawn.
 * @property level Optional level override. When set, the monster is spawned at this level instead
 *   of its default level. Used with stat lookup tables to scale stats to the specified level.
 * @property tier Optional tier override. When set, the monster uses this tier for stat lookup
 *   (C/B/A/S corresponds to Common/Uncommon/Rare/Boss difficulty scaling).
 */
data class EncounterSlotDef(
    val monsterId: String,
    val level: Int? = null,
    val tier: MonsterTier? = null,
)

/**
 * Defines one possible random encounter — a weighted set of monster slots.
 *
 * Slots replace the old flat `monsterIds` list, enabling per-monster level and tier overrides. The
 * [monsterIds] field is derived from slots for backward compatibility with codegen paths that only
 * need IDs.
 *
 * @property slots Per-monster slot definitions with optional level/tier overrides.
 * @property weight Relative probability weight (higher = more common). Default 1.
 */
data class EncounterDef(val slots: List<EncounterSlotDef>, val weight: Int = 1) {
    /** Flat list of monster IDs derived from [slots] — for backward-compatible codegen. */
    val monsterIds: List<String>
        get() = slots.map { it.monsterId }

    companion object {
        /** Creates an [EncounterDef] from a flat list of monster IDs (no level/tier overrides). */
        fun fromIds(monsterIds: List<String>, weight: Int = 1): EncounterDef =
            EncounterDef(
                slots = monsterIds.map { EncounterSlotDef(monsterId = it) },
                weight = weight,
            )
    }
}

/**
 * Domain data class capturing all data for a simple turn-based battle system.
 *
 * Produced by [io.github.gbkt.rpg.dsl.SimpleBattleBuilder] and used to generate a
 * [io.github.gbkt.core.ir.GenericSystem] with combat configuration in its config map.
 *
 * Key constraint: this is plain data — NOT an IR type. No sealed interface is implemented.
 *
 * @property id System ID used in [io.github.gbkt.core.ir.TriggerSystem] script ops.
 * @property partyIds IDs of [CharacterDef]s in the player's party.
 * @property encounters Weighted encounter pool defining possible enemy groups.
 * @property onVictoryOps ScriptOp sequence to execute on player victory.
 * @property onDefeatOps ScriptOp sequence to execute on player defeat.
 */
data class SimpleBattleDef(
    val id: String,
    val partyIds: List<String>,
    val encounters: List<EncounterDef>,
    val onVictoryOps: List<ScriptOp> = emptyList(),
    val onDefeatOps: List<ScriptOp> = emptyList(),
)
