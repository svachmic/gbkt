/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "UNCHECKED_CAST"
) // GenreVisitorResult uses List<CodegenFragment> — safe cast at call site

package io.github.gbkt.genre.sport.codegen

import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.genre.sport.domain.BallPhysicsConfig
import io.github.gbkt.genre.sport.domain.BallSportConfig
import io.github.gbkt.genre.sport.domain.BracketType
import io.github.gbkt.genre.sport.domain.FieldDef
import io.github.gbkt.genre.sport.domain.MatchStructure
import io.github.gbkt.genre.sport.domain.RacingAIConfig
import io.github.gbkt.genre.sport.domain.RacingConfig
import io.github.gbkt.genre.sport.domain.RacingMode
import io.github.gbkt.genre.sport.domain.ScoringRules
import io.github.gbkt.genre.sport.domain.SportPickupDef
import io.github.gbkt.genre.sport.domain.SportPickupType
import io.github.gbkt.genre.sport.domain.TournamentConfig
import io.github.gbkt.genre.sport.domain.TrackDef
import io.github.gbkt.genre.sport.domain.VehicleDef
import io.github.gbkt.genre.sport.domain.VehicleStats
import io.github.gbkt.genre.sport.domain.WaypointDef
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// SPORT CODEGEN TESTS (Plan 06.8-12 success criteria)
//
// Tests covering:
//  - Racing: racing_update, racing_ai_update, racing_check_finish generated
//  - Ball sport: sport_ball_update, sport_check_goal, sport_match_update generated
//  - Tournament: tournament_advance, tournament_standings generated
//  - Pickup definitions delegate to shared pickup system (no duplicate pickup_collect codegen)
// =============================================================================

/** Build a minimal [GameIR] for testing. */
private fun buildTestGameIR(): GameIR =
    GameIR(
        name = "SportTest",
        config = CartridgeConfig(),
        scenes = listOf(SceneIR(id = "main")),
        systems = emptyList(),
        startScene = "main",
    )

/** Build a minimal [RacingConfig] with AI mode and waypoints. */
private fun buildRacingConfig(
    id: String = "grand_prix",
    mode: RacingMode = RacingMode.AI_OPPONENT,
    laps: Int = 3,
    withPickups: Boolean = false,
): RacingConfig =
    RacingConfig(
        id = id,
        mode = mode,
        laps = laps,
        track =
            TrackDef(
                zoneId = "race_zone",
                waypoints =
                    listOf(
                        WaypointDef(tileX = 5, tileY = 5, isCheckpoint = true),
                        WaypointDef(tileX = 10, tileY = 5),
                        WaypointDef(tileX = 10, tileY = 10),
                    ),
                lapCount = laps,
            ),
        vehicles =
            listOf(
                VehicleDef(id = "car_1", name = "Speed Racer", stats = VehicleStats(220, 180, 160))
            ),
        aiConfig = RacingAIConfig(speedPercent = 80, difficulty = 5, rubberBanding = true),
        pickups =
            if (withPickups)
                listOf(
                    SportPickupDef(
                        id = "speed_boost",
                        type = SportPickupType.SPEED_BOOST,
                        durationFrames = 120,
                    )
                )
            else emptyList(),
    )

/** Build a minimal [BallSportConfig]. */
private fun buildBallSportConfig(
    id: String = "soccer",
    withPickups: Boolean = false,
): BallSportConfig =
    BallSportConfig(
        id = id,
        field = FieldDef(widthTiles = 20, heightTiles = 16),
        ballPhysics = BallPhysicsConfig(speed = 128, friction = 8, bounce = 200),
        scoringRules = ScoringRules(pointsPerGoal = 1, targetScore = 5),
        matchStructure = MatchStructure(halves = 2, halfDurationSeconds = 60),
        pickups =
            if (withPickups)
                listOf(
                    SportPickupDef(
                        id = "score_mult",
                        type = SportPickupType.SCORE_MULTIPLIER,
                        durationFrames = 180,
                    )
                )
            else emptyList(),
    )

/** Build a minimal [TournamentConfig]. */
private fun buildTournamentConfig(
    id: String = "world_cup",
    bracketType: BracketType = BracketType.SINGLE_ELIMINATION,
    participants: List<String> = listOf("team_a", "team_b", "team_c", "team_d"),
): TournamentConfig =
    TournamentConfig(
        id = id,
        bracketType = bracketType,
        participantIds = participants,
        roundsPerMatch = 1,
    )

class SportCodegenTest {

    private val visitor = SportVisitor()
    private val gameIR = buildTestGameIR()

    // =========================================================================
    // Test 1: Racing — update, AI update, finish check functions generated
    // =========================================================================

    @Test
    fun `racing visitor generates update AI update and finish check functions`() {
        val config = buildRacingConfig(mode = RacingMode.AI_OPPONENT)
        val systemConfig = mapOf("type" to "sport_racing", "config" to config)

        val result = visitor.visit("sport_racing", systemConfig, gameIR)
        val functions = result.functions as List<CFunction>
        val functionNames = functions.map { it.name }

        assertTrue(
            functionNames.any { it.startsWith("racing_update_") },
            "Expected 'racing_update_{id}' function in sport_racing codegen, got: $functionNames",
        )
        assertTrue(
            functionNames.any { it.startsWith("racing_ai_update_") },
            "Expected 'racing_ai_update_{id}' function for AI mode racing, got: $functionNames",
        )
        assertTrue(
            functionNames.any { it.startsWith("racing_check_finish_") },
            "Expected 'racing_check_finish_{id}' function in sport_racing codegen, got: $functionNames",
        )
    }

    @Test
    fun `time trial racing generates update and finish functions but not AI update`() {
        val config = buildRacingConfig(mode = RacingMode.TIME_TRIAL)
        val systemConfig = mapOf("type" to "sport_racing", "config" to config)

        val result = visitor.visit("sport_racing", systemConfig, gameIR)
        val functions = result.functions as List<CFunction>
        val functionNames = functions.map { it.name }

        assertTrue(
            functionNames.any { it.startsWith("racing_update_") },
            "Expected 'racing_update_{id}' function for time trial, got: $functionNames",
        )
        assertFalse(
            functionNames.any { it.startsWith("racing_ai_update_") },
            "TIME_TRIAL should NOT generate 'racing_ai_update_{id}', got: $functionNames",
        )
        assertTrue(
            functionNames.any { it.startsWith("racing_check_finish_") },
            "Expected 'racing_check_finish_{id}' for time trial, got: $functionNames",
        )
    }

    // =========================================================================
    // Test 2: Ball sport — ball update, goal check, match update generated
    // =========================================================================

    @Test
    fun `ball sport visitor generates ball update goal check and match update functions`() {
        val config = buildBallSportConfig()
        val systemConfig = mapOf("type" to "sport_ball", "config" to config)

        val result = visitor.visit("sport_ball", systemConfig, gameIR)
        val functions = result.functions as List<CFunction>
        val functionNames = functions.map { it.name }

        assertTrue(
            functionNames.any { it.startsWith("sport_ball_update_") },
            "Expected 'sport_ball_update_{id}' function in sport_ball codegen, got: $functionNames",
        )
        assertTrue(
            functionNames.any { it.startsWith("sport_check_goal_") },
            "Expected 'sport_check_goal_{id}' function in sport_ball codegen, got: $functionNames",
        )
        assertTrue(
            functionNames.any { it.startsWith("sport_match_update_") },
            "Expected 'sport_match_update_{id}' function in sport_ball codegen, got: $functionNames",
        )
    }

    @Test
    fun `ball sport visitor generates state variable declarations`() {
        val config = buildBallSportConfig(id = "basketball")
        val systemConfig = mapOf("type" to "sport_ball", "config" to config)

        val result = visitor.visit("sport_ball", systemConfig, gameIR)
        val varDecls = result.varDecls as List<CVarDecl>
        val varNames = varDecls.map { it.name }

        assertTrue(
            varNames.any { it.contains("_sport_ball_x_") },
            "Expected '_sport_ball_x_{id}' var decl, got: $varNames",
        )
        assertTrue(
            varNames.any { it.contains("_sport_score_home_") },
            "Expected '_sport_score_home_{id}' var decl, got: $varNames",
        )
        assertTrue(
            varNames.any { it.contains("_sport_score_away_") },
            "Expected '_sport_score_away_{id}' var decl, got: $varNames",
        )
        assertTrue(
            varNames.any { it.contains("_sport_match_timer_") },
            "Expected '_sport_match_timer_{id}' var decl, got: $varNames",
        )
    }

    // =========================================================================
    // Test 3: Tournament — advance and standings generated
    // =========================================================================

    @Test
    fun `tournament visitor generates advance and standings functions`() {
        val config = buildTournamentConfig()
        val systemConfig = mapOf("type" to "sport_tournament", "config" to config)

        val result = visitor.visit("sport_tournament", systemConfig, gameIR)
        val functions = result.functions as List<CFunction>
        val functionNames = functions.map { it.name }

        assertTrue(
            functionNames.any { it.startsWith("tournament_advance_") },
            "Expected 'tournament_advance_{id}' function in sport_tournament codegen, got: $functionNames",
        )
        assertTrue(
            functionNames.any { it.startsWith("tournament_standings_") },
            "Expected 'tournament_standings_{id}' function in sport_tournament codegen, got: $functionNames",
        )
    }

    @Test
    fun `tournament visitor generates win and loss tracking state variables`() {
        val config = buildTournamentConfig(id = "regional_cup")
        val systemConfig = mapOf("type" to "sport_tournament", "config" to config)

        val result = visitor.visit("sport_tournament", systemConfig, gameIR)
        val varDecls = result.varDecls as List<CVarDecl>
        val varNames = varDecls.map { it.name }

        assertTrue(
            varNames.any { it.contains("_tournament_wins_") },
            "Expected '_tournament_wins_{id}' var decl, got: $varNames",
        )
        assertTrue(
            varNames.any { it.contains("_tournament_losses_") },
            "Expected '_tournament_losses_{id}' var decl, got: $varNames",
        )
        assertTrue(
            varNames.any { it.contains("_tournament_current_match_") },
            "Expected '_tournament_current_match_{id}' var decl, got: $varNames",
        )
    }

    // =========================================================================
    // Test 4: Pickup definitions delegate to shared pickup system
    // =========================================================================

    @Test
    fun `racing with pickups delegates to shared pickup system and does not duplicate pickup collect`() {
        val config = buildRacingConfig(withPickups = true)
        val systemConfig = mapOf("type" to "sport_racing", "config" to config)

        val result = visitor.visit("sport_racing", systemConfig, gameIR)
        val functions = result.functions as List<CFunction>
        val functionNames = functions.map { it.name }

        // Shared pickup system generates init/collect/spawn functions
        assertTrue(
            functionNames.any { it.startsWith("pickup_init_") },
            "Expected 'pickup_init_{id}' from shared pickup system delegation, got: $functionNames",
        )
        assertTrue(
            functionNames.any { it.startsWith("pickup_check_collect_") },
            "Expected 'pickup_check_collect_{id}' from shared pickup system, got: $functionNames",
        )
        assertTrue(
            functionNames.any { it.startsWith("pickup_spawn_") },
            "Expected 'pickup_spawn_{id}' from shared pickup system, got: $functionNames",
        )

        // SportVisitor must NOT emit its own pickup_collect function (no duplication)
        val pickupCollectCount = functionNames.count { it == "pickup_collect" }
        assertFalse(
            pickupCollectCount > 0,
            "SportVisitor must NOT emit standalone 'pickup_collect' (use shared system), got: $functionNames",
        )
    }

    @Test
    fun `ball sport with pickups generates pickup update function for timed effects`() {
        val config = buildBallSportConfig(withPickups = true)
        val systemConfig = mapOf("type" to "sport_ball", "config" to config)

        val result = visitor.visit("sport_ball", systemConfig, gameIR)
        val functions = result.functions as List<CFunction>
        val functionNames = functions.map { it.name }

        // Timed pickup (durationFrames > 0) should generate pickup_update
        assertTrue(
            functionNames.any { it.startsWith("pickup_update_") },
            "Expected 'pickup_update_{id}' for timed sport pickup (durationFrames=180), got: $functionNames",
        )
    }

    @Test
    fun `racing without pickups does not generate pickup functions`() {
        val config = buildRacingConfig(withPickups = false)
        val systemConfig = mapOf("type" to "sport_racing", "config" to config)

        val result = visitor.visit("sport_racing", systemConfig, gameIR)
        val functions = result.functions as List<CFunction>
        val functionNames = functions.map { it.name }

        assertFalse(
            functionNames.any { it.startsWith("pickup_") },
            "No pickups configured — should NOT generate any pickup_* functions, got: $functionNames",
        )
    }

    // =========================================================================
    // Test 5: canHandle returns true only for sport system types
    // =========================================================================

    @Test
    fun `canHandle returns true for sport system types only`() {
        assertTrue(visitor.canHandle("sport_racing"), "Expected canHandle('sport_racing') = true")
        assertTrue(visitor.canHandle("sport_ball"), "Expected canHandle('sport_ball') = true")
        assertTrue(
            visitor.canHandle("sport_tournament"),
            "Expected canHandle('sport_tournament') = true",
        )
        assertFalse(
            visitor.canHandle("rpg_character_system"),
            "Expected canHandle('rpg_character_system') = false",
        )
        assertFalse(
            visitor.canHandle("pickup_system"),
            "Expected canHandle('pickup_system') = false — pickup_system handled by GBDKSystemVisitor",
        )
        assertFalse(
            visitor.canHandle("platformer_physics"),
            "Expected canHandle('platformer_physics') = false",
        )
    }

    // =========================================================================
    // Test F-033: Tournament standings swaps losses array + advance records loser loss
    // =========================================================================

    @Test
    fun `tournament standings swaps losses array in sync with wins array`() {
        val config = buildTournamentConfig(id = "cup")
        val systemConfig = mapOf("type" to "sport_tournament", "config" to config)

        val result = visitor.visit("sport_tournament", systemConfig, gameIR)
        val functions = result.functions as List<CFunction>
        val standingsFunc = functions.firstOrNull { it.name == "tournament_standings_cup" }
            ?: error("tournament_standings_cup not found")

        // The standings function body must contain _tournament_losses_ references
        // (rendered via the C AST printer which uses the function's body statements)
        val bodyStr = standingsFunc.body.toString()
        assertTrue(
            bodyStr.contains("_tournament_losses_cup"),
            "tournament_standings body must swap _tournament_losses_ array. Body: $bodyStr",
        )
    }

    @Test
    fun `tournament advance records loser loss`() {
        val config = buildTournamentConfig(id = "cup2")
        val systemConfig = mapOf("type" to "sport_tournament", "config" to config)

        val result = visitor.visit("sport_tournament", systemConfig, gameIR)
        val functions = result.functions as List<CFunction>
        val advanceFunc = functions.firstOrNull { it.name == "tournament_advance_cup2" }
            ?: error("tournament_advance_cup2 not found")

        // The advance function must have loser_idx as a parameter
        val paramNames = advanceFunc.params.map { it.name }
        assertTrue(
            paramNames.contains("loser_idx"),
            "tournament_advance must have loser_idx parameter. Params: $paramNames",
        )

        // The advance function body must increment _tournament_losses_
        val bodyStr = advanceFunc.body.toString()
        assertTrue(
            bodyStr.contains("_tournament_losses_cup2"),
            "tournament_advance body must increment _tournament_losses_. Body: $bodyStr",
        )
    }
}
