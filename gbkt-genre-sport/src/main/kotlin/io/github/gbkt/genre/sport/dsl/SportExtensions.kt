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
// =============================================================================

/**
 * Defines and registers a racing game system.
 *
 * Produces a [GenericSystem] with config type `"sport_racing"`. The backend discovers this system
 * via ServiceLoader when it processes [GenericSystem] nodes.
 *
 * ```kotlin
 * racing("grand_prix") {
 *     mode(RacingMode.AI_OPPONENT)
 *     laps(3)
 *     track("race_zone") {
 *         waypoint(x = 5, y = 5, checkpoint = true)
 *         waypoint(x = 15, y = 3)
 *     }
 *     vehicle("car_1") {
 *         name("Speed Racer")
 *         stats { speed(220); acceleration(180); handling(160) }
 *     }
 *     vehicle("car_2") {
 *         name("Steady Rider")
 *         stats { speed(180); acceleration(200); handling(200) }
 *     }
 *     ai {
 *         speedPercent(85)
 *         difficulty(7)
 *         rubberBanding(enabled = true, strength = 60)
 *     }
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique system identifier.
 * @param block Configuration block executed against a [RacingBuilder].
 * @return The registered [GenericSystem].
 */
fun GameBuilder.racing(id: String, block: RacingBuilder.() -> Unit): GenericSystem {
    val builder = RacingBuilder(id)
    builder.block()
    val config = builder.build()
    val system =
        GenericSystem(id = id, config = mapOf("type" to "sport_racing", "config" to config))
    registerSystem(system)
    return system
}

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
