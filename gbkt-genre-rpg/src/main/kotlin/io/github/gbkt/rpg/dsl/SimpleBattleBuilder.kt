/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.ScriptBuilder
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.rpg.domain.CharacterDef
import io.github.gbkt.rpg.domain.EncounterDef
import io.github.gbkt.rpg.domain.EncounterSlotDef
import io.github.gbkt.rpg.domain.MonsterDef
import io.github.gbkt.rpg.domain.MonsterTier

/**
 * Builder for a single encounter definition within a [SimpleBattleBuilder].
 *
 * Monsters are added via the `+monster` unary-plus operator (simple static encounters) or via the
 * `slot(monster, level, tier)` method (level/tier-scaled encounters for dungeon crawlers).
 */
class EncounterBuilder {
    private val slots: MutableList<EncounterSlotDef> = mutableListOf()
    private var weight: Int = 1

    /**
     * Adds a monster to this encounter group using unary-plus syntax: `+goblin`.
     *
     * Uses the monster's default level and tier (no per-instance overrides).
     */
    operator fun MonsterDef.unaryPlus() {
        slots.add(EncounterSlotDef(monsterId = this.id))
    }

    /**
     * Adds a monster slot with explicit level and tier overrides.
     *
     * Use when the encounter table specifies that a monster appears at a particular level and
     * difficulty tier (e.g., "Kobold at level 8, B_TIER" vs. "Kobold at level 5, C_TIER").
     *
     * ```kotlin
     * encounter {
     *     slot(kobold, level = 8, tier = MonsterTier.UNCOMMON)
     *     slot(kobold, level = 5, tier = MonsterTier.COMMON)
     *     weight(20)
     * }
     * ```
     *
     * @param monster The [MonsterDef] to spawn.
     * @param level Optional level override for this monster instance.
     * @param tier Optional tier override for this monster instance.
     */
    fun slot(monster: MonsterDef, level: Int? = null, tier: MonsterTier? = null) {
        slots.add(EncounterSlotDef(monsterId = monster.id, level = level, tier = tier))
    }

    /**
     * Adds a monster slot by string ID with explicit level and tier overrides.
     *
     * String-based overload for cases where a [MonsterDef] object is not available.
     *
     * @param monsterId String ID of the monster to spawn.
     * @param level Optional level override for this monster instance.
     * @param tier Optional tier override for this monster instance.
     */
    fun slot(monsterId: String, level: Int? = null, tier: MonsterTier? = null) {
        slots.add(EncounterSlotDef(monsterId = monsterId, level = level, tier = tier))
    }

    /** Sets the relative weight (probability) of this encounter occurring. */
    fun weight(value: Int) {
        weight = value
    }

    /** Builds the [EncounterDef] domain object. */
    fun build(): EncounterDef = EncounterDef(slots = slots.toList(), weight = weight)
}

/**
 * Builder for a simple turn-based battle system.
 *
 * Collects party members, encounter pools, and victory/defeat ScriptOp sequences. The
 * [buildCombatEngineSystem] method returns a [CombatEngineSystem] with encounter configuration
 * stored in the [CombatEngineSystem.encounterConfig] map. This replaces the old
 * [io.github.gbkt.core.ir.GenericSystem] approach.
 *
 * Design constraint: this builder produces CORE IR types directly via [buildCombatEngineSystem].
 *
 * @param id The unique system identifier, used in TriggerSystem script ops.
 */
class SimpleBattleBuilder(val id: String) {
    private val partyIds: MutableList<String> = mutableListOf()
    private val encounters: MutableList<EncounterDef> = mutableListOf()
    private var onVictoryOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList()
    private var onDefeatOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList()

    /**
     * Adds a single character to the player's party by [CharacterDef].
     *
     * @param character The [CharacterDef] to add (returned by `character("id") { }` DSL call).
     */
    fun party(character: CharacterDef) {
        partyIds.add(character.id)
    }

    /**
     * Adds a character to the player's party by string ID.
     *
     * Use when you have the character ID string rather than the [CharacterDef] object.
     *
     * @param characterId The string ID of the character.
     */
    fun party(characterId: String) {
        partyIds.add(characterId)
    }

    /**
     * Adds multiple characters to the player's party.
     *
     * @param characters The [CharacterDef] instances to add.
     */
    fun party(vararg characters: CharacterDef) {
        characters.forEach { partyIds.add(it.id) }
    }

    /**
     * Defines an encounter group (set of monsters) using the [EncounterBuilder] DSL.
     *
     * ```kotlin
     * encounter { +goblin; +goblin }
     * encounter { +dragon; weight(3) }
     * encounter { slot(kobold, level = 8, tier = MonsterTier.UNCOMMON); slot(kobold, level = 5) }
     * ```
     */
    fun encounter(block: EncounterBuilder.() -> Unit) {
        val builder = EncounterBuilder()
        builder.block()
        encounters.add(builder.build())
    }

    /**
     * Records the script operations to execute when the player wins the battle.
     *
     * ```kotlin
     * onVictory { navigate(gameplayScene) }
     * ```
     */
    fun onVictory(block: ScriptBuilder.() -> Unit) {
        onVictoryOps = ScriptBuilder.buildOps(block)
    }

    /**
     * Records the script operations to execute when the player loses the battle.
     *
     * ```kotlin
     * onDefeat { navigate(gameoverScene) }
     * ```
     */
    fun onDefeat(block: ScriptBuilder.() -> Unit) {
        onDefeatOps = ScriptBuilder.buildOps(block)
    }

    /**
     * Builds a [CombatEngineSystem] with encounter configuration in
     * [CombatEngineSystem.encounterConfig].
     *
     * The encounter config map contains:
     * - `"partyIds"` → List<String> of character IDs in the party
     * - `"encounterData"` → List<[EncounterDef]> of possible enemy encounter groups
     * - `"onVictoryOps"` → List<ScriptOp> to execute on player victory
     * - `"onDefeatOps"` → List<ScriptOp> to execute on player defeat
     *
     * The returned [CombatEngineSystem] uses [CombatType.TURN_BASED] so that
     * [io.github.gbkt.backend.gbdk.codegen.visitor.CombatVisitor] handles it via the existing
     * TURN_BASED dispatch path.
     */
    fun buildCombatEngineSystem(): CombatEngineSystem =
        CombatEngineSystem(
            id = id,
            combatType = CombatType.TURN_BASED,
            onVictoryOps = onVictoryOps,
            onDefeatOps = onDefeatOps,
            encounterConfig =
                mapOf(
                    "partyIds" to partyIds.toList(),
                    "encounterData" to encounters.toList(),
                    "onVictoryOps" to onVictoryOps,
                    "onDefeatOps" to onDefeatOps,
                ),
        )
}
