/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

// =============================================================================
// TACTICAL GRID CONFIG — RE-EXPORTED FROM gbkt-ir
// =============================================================================
//
// TacticalGridConfig and related types live in io.github.gbkt.core.ir (gbkt-ir)
// alongside AtbConfig and WaveSurvivalConfig, since CombatEngineSystem (also in
// gbkt-ir) needs to reference them directly without circular module dependencies.
//
// This file provides type aliases in the gbkt-rpg.domain namespace so that
// game authors using the gbkt-rpg package can import from either location.
// gbkt-rpg DSL builders (TacticalGridBuilder) use the io.github.gbkt.core.ir
// types directly.
// =============================================================================

/** Re-export of [io.github.gbkt.core.ir.TacticalGridConfig] in the RPG domain namespace. */
typealias TacticalGridConfig = io.github.gbkt.core.ir.TacticalGridConfig

/** Re-export of [io.github.gbkt.core.ir.TerrainTypeDef] in the RPG domain namespace. */
typealias TerrainTypeDef = io.github.gbkt.core.ir.TerrainTypeDef

/** Re-export of [io.github.gbkt.core.ir.FacingDirection] in the RPG domain namespace. */
typealias FacingDirection = io.github.gbkt.core.ir.FacingDirection
