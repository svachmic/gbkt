/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "MatchingDeclarationName"
) // File contains both extensions and SportRegistry (multi-declaration file)

package io.github.gbkt.genre.sport.dsl

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.ir.GenericSystem

// =============================================================================
// SPORT DSL EXTENSIONS ON GameBuilder
// =============================================================================
//
// These functions extend GameBuilder with sport-specific DSL constructs.
// BOM separation pattern — gbkt-genre-sport depends on gbkt-core (one-directional).
// GameBuilder does NOT know about sport types. All builders produce GenericSystem
// (a core IR type) — no new sealed IR subtypes created.
//
// Config map key conventions:
//   "type" → "sport_racing" | "sport_ball" | "sport_tournament"
//   "config" → the domain config data class
//
// D-04 (Project Rule #1 — no magic strings):
//   `racing { ... }` and `vehicle { ... }` are property-delegate factories. The id
//   flows in via `KProperty.name` during `provideDelegate` — the user never types
//   the id as a String parameter.
// =============================================================================

/**
 * Declares a racing system. The id is captured from the property delegate (D-04).
 *
 * Usage:
 * ```kotlin
 * val track1 by racing {
 *     laps(3)
 *     player(carPlayer)
 *     aiOpponents(carAi, count = 1)
 *     track {
 *         waypoint(x = 5,  y = 5,  checkpoint = true)
 *         waypoint(x = 15, y = 5)
 *         waypoint(x = 15, y = 15, checkpoint = true)
 *         waypoint(x = 5,  y = 15)
 *     }
 * }
 * ```
 *
 * The [RacingDelegate] returned here registers the racing system, auto-emits a
 * [io.github.gbkt.core.ir.CameraSystem] following the player vehicle's actor (unless the user
 * declared a camera themselves), synthesizes one [io.github.gbkt.core.ir.ActorPoolIR] per AI
 * vehicle slot, and writes the rasterized track tile data into a [io.github.gbkt.core.ir.ZoneIR]
 * keyed on the property name (D-12: skipped if the user already supplied populated zone tile data
 * for the same id).
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param block Configuration block executed against a [RacingBuilder].
 * @return A [RacingDelegate] for the `by` keyword to evaluate.
 */
fun GameBuilder.racing(block: RacingBuilder.() -> Unit): RacingDelegate = RacingDelegate(block)

/**
 * Declares a vehicle bound to an existing actor. The id is captured from the property delegate
 * (D-04).
 *
 * Usage:
 * ```kotlin
 * val car by actor { … }
 * val carPlayer by vehicle {
 *     actor(car)
 *     stats { speed(200); acceleration(160); handling(180) }
 * }
 * ```
 *
 * The vehicle wraps an [io.github.gbkt.core.dsl.ActorRef] (D-05): the on-screen sprite, movement
 * controller, and per-actor properties remain configurable on the actor itself; the vehicle only
 * adds racing stats and the binding back to that actor.
 *
 * @param block Configuration block executed against a [VehicleBuilder].
 * @return A [VehicleDelegate] for the `by` keyword to evaluate.
 */
fun GameBuilder.vehicle(block: VehicleBuilder.() -> Unit): VehicleDelegate = VehicleDelegate(block)

/**
 * Defines and registers a ball sport game system.
 *
 * Produces a [GenericSystem] with config type `"sport_ball"`. Covers soccer, basketball, tennis,
 * and similar sports with a field/court, ball physics, and scoring rules.
 *
 * ```kotlin
 * ballSport("soccer") {
 *     field {
 *         width(20); height(16)
 *         goal { width(2); height(3) }
 *     }
 *     ball {
 *         speed(150); friction(10); bounce(210)
 *     }
 *     scoring {
 *         pointsPerGoal(1)
 *         winCondition(WinCondition.HIGHEST_SCORE_AT_TIME)
 *         timeLimitSeconds(120)
 *     }
 *     match {
 *         halves(2)
 *         halfDuration(60)
 *     }
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique system identifier.
 * @param block Configuration block executed against a [BallSportBuilder].
 * @return The registered [GenericSystem].
 */
fun GameBuilder.ballSport(id: String, block: BallSportBuilder.() -> Unit): GenericSystem {
    val builder = BallSportBuilder(id)
    builder.block()
    val config = builder.build()
    val system = GenericSystem(id = id, config = mapOf("type" to "sport_ball", "config" to config))
    registerSystem(system)
    return system
}

/**
 * Defines and registers a tournament management system.
 *
 * Produces a [GenericSystem] with config type `"sport_tournament"`. Manages bracket tracking,
 * participant standings, and match scheduling for single-elimination, round-robin, or
 * double-elimination formats.
 *
 * ```kotlin
 * tournament("world_cup") {
 *     bracketType(BracketType.SINGLE_ELIMINATION)
 *     participants("team_a", "team_b", "team_c", "team_d")
 *     roundsPerMatch(1)
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique system identifier.
 * @param block Configuration block executed against a [TournamentBuilder].
 * @return The registered [GenericSystem].
 */
fun GameBuilder.tournament(id: String, block: TournamentBuilder.() -> Unit): GenericSystem {
    val builder = TournamentBuilder(id)
    builder.block()
    val config = builder.build()
    val system =
        GenericSystem(id = id, config = mapOf("type" to "sport_tournament", "config" to config))
    registerSystem(system)
    return system
}
