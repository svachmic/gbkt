/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

// =============================================================================
// PARTY MANAGEMENT DOMAIN TYPES
// =============================================================================
//
// Party management supports active/reserve member rotation, row formation
// positioning, and guest members (AI-controlled in battle, locked equipment,
// removable via script action).
//
// Guest member codegen (GAP-4):
//   - _party_is_guest[N] UINT8 array (1=guest, 0=regular)
//   - is_guest(char_idx) helper
//   - remove_guest(char_id) script-removable helper
//   - equip_item_<slot>() gains guest lock guard: if (is_guest(active_char_idx)) return;
//   - PLAYER_TURN skips guests; guests dispatched by update_ai_guest_<charId>() in ENEMY_TURN phase
// =============================================================================

/**
 * Configuration for an individual party member slot.
 *
 * @param characterId The character definition ID.
 * @param isGuest Whether this member is a guest (AI-controlled, locked equipment, removable).
 * @param isLocked Whether this member cannot be moved to reserve.
 */
data class PartyMemberConfig(
    val characterId: String,
    val isGuest: Boolean = false,
    val isLocked: Boolean = false,
)

/**
 * Party management configuration.
 *
 * Controls active/reserve rotation, row formation, and guest member behavior. The backend generates
 * party management C functions from this config.
 *
 * @param maxActiveSize Maximum number of active party members (default 4).
 * @param enableReserve Whether reserve bench is available for rotation.
 * @param reserveSize Maximum number of reserve members.
 * @param reserveExpShare Percentage of battle EXP shared to reserve members (0-100).
 * @param enableRowFormation Whether front/back row positioning affects damage.
 * @param frontRowDamageMultiplier Front row damage multiplier as percent (100 = no change).
 * @param backRowDamageMultiplier Back row damage multiplier as percent (75 = 25% reduction).
 * @param backRowDefenseBonus Back row defense bonus as percent added to defense stat.
 * @param enableDynamicParty Whether party can change mid-game (add/remove via script).
 * @param initialMembers Initial party member configuration.
 */
data class PartyConfig(
    val maxActiveSize: Int = 4,
    val enableReserve: Boolean = false,
    val reserveSize: Int = 4,
    val reserveExpShare: Int = 50,
    val enableRowFormation: Boolean = false,
    val frontRowDamageMultiplier: Int = 100,
    val backRowDamageMultiplier: Int = 75,
    val backRowDefenseBonus: Int = 25,
    val enableDynamicParty: Boolean = false,
    val initialMembers: List<PartyMemberConfig> = emptyList(),
)
