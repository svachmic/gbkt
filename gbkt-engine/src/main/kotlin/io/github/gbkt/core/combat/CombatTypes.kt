/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "MatchingDeclarationName"
) // File intentionally named for its purpose (multiple combat type declarations)

package io.github.gbkt.core.combat

import io.github.gbkt.core.ir.CombatStateId

// =============================================================================
// COMBAT ENGINE TYPES (engine-level extension points)
// =============================================================================

/**
 * Engine-level contract for combat state implementations.
 *
 * Game and RPG code implements this interface to define custom state behaviors for [CombatStateId]
 * entries registered in [CombatEngineSystem.customStates].
 *
 * The built-in state IDs ([COMBAT_INIT], [PLAYER_TURN], [ENEMY_TURN], [VICTORY], [DEFEAT]) have
 * corresponding codegen in the GBDK backend. Custom states are extension points for genre packages.
 */
interface CombatState {
    /** The state identifier that this implementation handles. */
    val id: CombatStateId

    /** Called every frame while this combat state is active. */
    fun update()
}

// =============================================================================
// PREDEFINED STATE ID CONSTANTS
// =============================================================================

/** Initial combat setup state — runs once before the first turn begins. */
val COMBAT_INIT = CombatStateId("INIT")

/** Active player turn — waiting for player input or executing player action. */
val PLAYER_TURN = CombatStateId("PLAYER_TURN")

/** Active enemy turn — executing AI-controlled enemy action. */
val ENEMY_TURN = CombatStateId("ENEMY_TURN")

/** Victory state — all enemies defeated; [CombatEngineSystem.onVictoryOps] are executed. */
val VICTORY = CombatStateId("VICTORY")

/** Defeat state — party wiped; [CombatEngineSystem.onDefeatOps] are executed. */
val DEFEAT = CombatStateId("DEFEAT")
