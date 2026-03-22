/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.rpg.domain.PartyConfig
import io.github.gbkt.rpg.domain.PartyMemberConfig

// =============================================================================
// PARTY MANAGEMENT DSL BUILDER
// =============================================================================

/**
 * Builder for [PartyConfig] — party management with active/reserve, row formation, and guests.
 *
 * ```kotlin
 * partySystem {
 *     maxActive(4)
 *     reserve(enabled = true, size = 4, expShare = 50)
 *     rowFormation(enabled = true, backDamage = 75, backDefense = 25)
 *     member("hero")
 *     guestMember("npc_ally")
 *     lockedMember("forced_char")
 * }
 * ```
 */
class PartyBuilder {
    private var maxActiveSize: Int = 4
    private var enableReserve: Boolean = false
    private var reserveSize: Int = 4
    private var reserveExpShare: Int = 50
    private var enableRowFormation: Boolean = false
    private var frontRowDamageMultiplier: Int = 100
    private var backRowDamageMultiplier: Int = 75
    private var backRowDefenseBonus: Int = 25
    private var enableDynamicParty: Boolean = false
    private val members = mutableListOf<PartyMemberConfig>()

    /** Sets the maximum number of active party members. */
    fun maxActive(n: Int) {
        maxActiveSize = n
    }

    /** Configures the reserve bench. */
    fun reserve(enabled: Boolean = true, size: Int = 4, expShare: Int = 50) {
        enableReserve = enabled
        reserveSize = size
        reserveExpShare = expShare
    }

    /** Configures row formation (front/back row damage and defense modifiers). */
    fun rowFormation(
        enabled: Boolean = true,
        frontDamage: Int = 100,
        backDamage: Int = 75,
        backDefense: Int = 25,
    ) {
        enableRowFormation = enabled
        frontRowDamageMultiplier = frontDamage
        backRowDamageMultiplier = backDamage
        backRowDefenseBonus = backDefense
    }

    /** Enables dynamic party (add/remove members via script). */
    fun dynamicParty(enabled: Boolean = true) {
        enableDynamicParty = enabled
    }

    /** Adds a regular party member. */
    fun member(characterId: String) {
        members.add(PartyMemberConfig(characterId = characterId))
    }

    /**
     * Adds a guest party member.
     *
     * Guests are AI-controlled in battle, cannot change equipment (GAP-4), and can be removed via
     * remove_guest(char_id) script action.
     */
    fun guestMember(characterId: String) {
        members.add(PartyMemberConfig(characterId = characterId, isGuest = true))
    }

    /** Adds a locked party member that cannot be moved to reserve. */
    fun lockedMember(characterId: String) {
        members.add(PartyMemberConfig(characterId = characterId, isLocked = true))
    }

    fun build(): PartyConfig =
        PartyConfig(
            maxActiveSize = maxActiveSize,
            enableReserve = enableReserve,
            reserveSize = reserveSize,
            reserveExpShare = reserveExpShare,
            enableRowFormation = enableRowFormation,
            frontRowDamageMultiplier = frontRowDamageMultiplier,
            backRowDamageMultiplier = backRowDamageMultiplier,
            backRowDefenseBonus = backRowDefenseBonus,
            enableDynamicParty = enableDynamicParty,
            initialMembers = members.toList(),
        )
}
