/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "LongMethod",
    "TooManyFunctions",
) // Code generation inherently produces large methods. Each IR node maps to C output.

package io.github.gbkt.genre.sport.codegen

import io.github.gbkt.backend.api.GenreSystemVisitor
import io.github.gbkt.backend.api.GenreVisitorResult
import io.github.gbkt.backend.api.sanitizeCId
import io.github.gbkt.backend.gbdk.codegen.ast.CArray
import io.github.gbkt.backend.gbdk.codegen.ast.CArrayAccess
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CComment
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CRawExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CReturn
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CTernary
import io.github.gbkt.backend.gbdk.codegen.ast.CU16
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.backend.gbdk.codegen.visitor.GBDKSystemVisitor
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.pickup.PickupDef
import io.github.gbkt.core.pickup.PickupSystemConfig
import io.github.gbkt.genre.sport.domain.BallSportConfig
import io.github.gbkt.genre.sport.domain.RacingConfig
import io.github.gbkt.genre.sport.domain.RacingMode
import io.github.gbkt.genre.sport.domain.SportPickupDef
import io.github.gbkt.genre.sport.domain.TournamentConfig

// =============================================================================
// SPORT VISITOR
//
// GenreSystemVisitor implementation for gbkt-genre-sport.
// Handles system types: "sport_racing", "sport_ball", "sport_tournament".
//
// Generated functions per system:
//  Racing:
//    - racing_update_{id}(player_x, player_y)  — move vehicle, check waypoints, lap counter
//    - racing_ai_update_{id}()                  — waypoint following, speed adjustment
//    - racing_check_finish_{id}()               — detect lap completion
//
//  Ball sport:
//    - sport_ball_update_{id}(dx, dy)           — ball physics (move, friction, bounce)
//    - sport_check_goal_{id}(ball_x, ball_y)    — detect ball in goal area, increment score
//    - sport_match_update_{id}()                — half/round timer, win condition check
//
//  Tournament:
//    - tournament_advance_{id}(winner_id)       — update bracket, determine next match
//    - tournament_standings_{id}()              — compute standings for round-robin
//
// Pickup reconciliation:
//  SportPickupDef is converted to engine PickupDef and delegated to GBDKSystemVisitor
//  via a synthetic GenericSystem with type="pickup_system". No pickup logic duplication.
// =============================================================================

/**
 * Genre visitor that generates C functions and variable declarations for sport/racing systems.
 *
 * Registered via ServiceLoader: `META-INF/services/io.github.gbkt.backend.api.GenreSystemVisitor`
 *
 * Handles: `"sport_racing"`, `"sport_ball"`, `"sport_tournament"`.
 *
 * Pickup definitions within racing/ball sport configs are converted to engine [PickupDef] types and
 * delegated to [GBDKSystemVisitor] via a synthetic `pickup_system` [GenericSystem]. No pickup logic
 * is duplicated in this visitor.
 */
class SportVisitor : GenreSystemVisitor {

    override fun canHandle(systemType: String): Boolean =
        systemType in setOf("sport_racing", "sport_ball", "sport_tournament")

    override fun visit(
        systemType: String,
        systemConfig: Map<String, Any>,
        gameIR: GameIR,
    ): GenreVisitorResult =
        when (systemType) {
            "sport_racing" -> visitRacing(systemConfig, gameIR)
            "sport_ball" -> visitBallSport(systemConfig, gameIR)
            "sport_tournament" -> visitTournament(systemConfig, gameIR)
            else -> GenreVisitorResult()
        }

    // -------------------------------------------------------------------------
    // Racing system codegen
    // -------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun visitRacing(systemConfig: Map<String, Any>, gameIR: GameIR): GenreVisitorResult {
        val config = systemConfig["config"] as? RacingConfig ?: return GenreVisitorResult()
        val id = sanitizeCId(config.id)
        val isAiMode = config.mode == RacingMode.AI_OPPONENT

        val functions = mutableListOf<CFunction>()
        val varDecls = mutableListOf<CVarDecl>()

        // Global state variables
        varDecls += CVarDecl(name = "_racing_lap_count_$id", type = CU8, initializer = CLiteral(0))
        varDecls +=
            CVarDecl(name = "_racing_waypoint_idx_$id", type = CU8, initializer = CLiteral(0))
        varDecls +=
            CVarDecl(
                name = "_racing_speed_$id",
                type = CU8,
                initializer = CLiteral(config.vehicles.firstOrNull()?.stats?.speed ?: 128),
            )
        if (isAiMode) {
            varDecls +=
                CVarDecl(
                    name = "_racing_ai_waypoint_idx_$id",
                    type = CU8,
                    initializer = CLiteral(0),
                )
            varDecls +=
                CVarDecl(name = "_racing_ai_speed_$id", type = CU8, initializer = CLiteral(0))
        }

        // racing_update_{id}(player_x, player_y)
        // Move vehicle toward waypoint, check lap counter.
        val targetLaps = config.laps
        val waypointCount = config.track?.waypoints?.size ?: 0
        val waypoints = config.track?.waypoints ?: emptyList()

        // Generate waypoint coordinate arrays (pixel coords = tile * 8)
        if (waypointCount > 0) {
            val wpXValues = waypoints.joinToString(", ") { "${it.tileX * 8}u" }
            val wpYValues = waypoints.joinToString(", ") { "${it.tileY * 8}u" }
            varDecls +=
                CVarDecl(
                    name = "_racing_wp_x_$id",
                    type = CArray(CU8, waypointCount),
                    initializer = CRawExpr("{$wpXValues}"),
                )
            varDecls +=
                CVarDecl(
                    name = "_racing_wp_y_$id",
                    type = CArray(CU8, waypointCount),
                    initializer = CRawExpr("{$wpYValues}"),
                )
        }

        val proximityThreshold = 8 // one tile distance in pixels
        val updateBody =
            buildList<CStatement> {
                add(CComment("Move vehicle: advance to next waypoint if close enough"))
                if (waypointCount > 0) {
                    // Compute distance to current waypoint
                    add(
                        CVarDecl(
                            name = "wp_x",
                            type = CU8,
                            initializer =
                                CArrayAccess(
                                    CVar("_racing_wp_x_$id"),
                                    CVar("_racing_waypoint_idx_$id"),
                                ),
                        )
                    )
                    add(
                        CVarDecl(
                            name = "wp_y",
                            type = CU8,
                            initializer =
                                CArrayAccess(
                                    CVar("_racing_wp_y_$id"),
                                    CVar("_racing_waypoint_idx_$id"),
                                ),
                        )
                    )
                    // Proximity check: abs(player_x - wp_x) < threshold && abs(player_y - wp_y) <
                    // threshold
                    // For unsigned subtraction, use (a > b ? a - b : b - a) pattern via ternary
                    add(
                        CVarDecl(
                            name = "dx",
                            type = CU8,
                            initializer =
                                CTernary(
                                    condition = CBinaryExpr(CVar("player_x"), ">", CVar("wp_x")),
                                    thenExpr = CBinaryExpr(CVar("player_x"), "-", CVar("wp_x")),
                                    elseExpr = CBinaryExpr(CVar("wp_x"), "-", CVar("player_x")),
                                ),
                        )
                    )
                    add(
                        CVarDecl(
                            name = "dy",
                            type = CU8,
                            initializer =
                                CTernary(
                                    condition = CBinaryExpr(CVar("player_y"), ">", CVar("wp_y")),
                                    thenExpr = CBinaryExpr(CVar("player_y"), "-", CVar("wp_y")),
                                    elseExpr = CBinaryExpr(CVar("wp_y"), "-", CVar("player_y")),
                                ),
                        )
                    )
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CBinaryExpr(CVar("dx"), "<", CLiteral(proximityThreshold)),
                                    "&&",
                                    CBinaryExpr(CVar("dy"), "<", CLiteral(proximityThreshold)),
                                ),
                            thenBody =
                                listOf(
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CVar("_racing_waypoint_idx_$id"),
                                                "<",
                                                CLiteral(waypointCount - 1),
                                            ),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CVar("_racing_waypoint_idx_$id"),
                                                        "+=",
                                                        CLiteral(1),
                                                    )
                                                )
                                            ),
                                        elseBody =
                                            listOf(
                                                // Waypoint wrap-around: complete a lap
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CVar("_racing_waypoint_idx_$id"),
                                                        "=",
                                                        CLiteral(0),
                                                    )
                                                ),
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CVar("_racing_lap_count_$id"),
                                                        "+=",
                                                        CLiteral(1),
                                                    )
                                                ),
                                            ),
                                    )
                                ),
                        )
                    )
                }
                add(CComment("Check finish: $targetLaps laps required"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(CVar("_racing_lap_count_$id"), ">=", CLiteral(targetLaps)),
                        thenBody =
                            listOf(
                                CCall("racing_check_finish_$id", emptyList()).let {
                                    CExprStatement(it)
                                }
                            ),
                    )
                )
            }
        functions +=
            CFunction(
                name = "racing_update_$id",
                returnType = CVoid,
                params = listOf(CParam("player_x", CU8), CParam("player_y", CU8)),
                body = updateBody,
                sectionComment = "Racing system: $id — player vehicle update",
            )

        // racing_ai_update_{id}()
        // AI waypoint following with rubber-banding speed adjustment.
        if (isAiMode) {
            val aiConfig = config.aiConfig
            val baseAiSpeed =
                (config.vehicles.firstOrNull()?.stats?.speed ?: 128) * aiConfig.speedPercent / 100
            val rubberBandStrength = aiConfig.rubberBandStrength

            val aiBody =
                buildList<CStatement> {
                    add(CComment("AI: follow waypoints at ${aiConfig.speedPercent}% speed"))
                    add(
                        CExprStatement(
                            CBinaryExpr(
                                CVar("_racing_ai_speed_$id"),
                                "=",
                                CLiteral(baseAiSpeed.coerceIn(0, 255)),
                            )
                        )
                    )
                    if (waypointCount > 0) {
                        add(
                            CIf(
                                condition =
                                    CBinaryExpr(
                                        CVar("_racing_ai_waypoint_idx_$id"),
                                        "<",
                                        CLiteral(waypointCount),
                                    ),
                                thenBody =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CVar("_racing_ai_waypoint_idx_$id"),
                                                "+=",
                                                CLiteral(1),
                                            )
                                        )
                                    ),
                                elseBody =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CVar("_racing_ai_waypoint_idx_$id"),
                                                "=",
                                                CLiteral(0),
                                            )
                                        )
                                    ),
                            )
                        )
                    }
                    if (aiConfig.rubberBanding) {
                        add(
                            CComment(
                                "Rubber-band: close gap to player (strength=$rubberBandStrength)"
                            )
                        )
                        add(
                            CIf(
                                condition =
                                    CBinaryExpr(
                                        CVar("_racing_ai_waypoint_idx_$id"),
                                        "<",
                                        CVar("_racing_waypoint_idx_$id"),
                                    ),
                                thenBody =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CVar("_racing_ai_speed_$id"),
                                                "+=",
                                                CLiteral(
                                                    (rubberBandStrength * baseAiSpeed / 100)
                                                        .coerceIn(0, 255)
                                                ),
                                            )
                                        )
                                    ),
                            )
                        )
                    }
                }
            functions +=
                CFunction(
                    name = "racing_ai_update_$id",
                    returnType = CVoid,
                    body = aiBody,
                    sectionComment = "Racing system: $id — AI opponent update",
                )
        }

        // racing_check_finish_{id}()
        // Detects race completion (lap count >= target).
        val finishBody =
            buildList<CStatement> {
                add(CComment("Race finish: ${config.laps} laps required"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(CVar("_racing_lap_count_$id"), ">=", CLiteral(config.laps)),
                        thenBody =
                            listOf(CComment("race_complete_$id: handle finish event in scene")),
                    )
                )
            }
        functions +=
            CFunction(
                name = "racing_check_finish_$id",
                returnType = CVoid,
                body = finishBody,
                sectionComment = "Racing system: $id — finish/lap detection",
            )

        // Pickup reconciliation: convert SportPickupDef → PickupDef, delegate to GBDKSystemVisitor
        val pickupResult = buildPickupResult(config.id, config.pickups, gameIR)
        functions += pickupResult.functions as List<CFunction>
        varDecls += pickupResult.varDecls as List<CVarDecl>

        return GenreVisitorResult(functions = functions, varDecls = varDecls)
    }

    // -------------------------------------------------------------------------
    // Ball sport system codegen
    // -------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun visitBallSport(systemConfig: Map<String, Any>, gameIR: GameIR): GenreVisitorResult {
        val config = systemConfig["config"] as? BallSportConfig ?: return GenreVisitorResult()
        val id = sanitizeCId(config.id)

        val functions = mutableListOf<CFunction>()
        val varDecls = mutableListOf<CVarDecl>()

        // Global state variables
        varDecls +=
            CVarDecl(
                name = "_sport_ball_x_$id",
                type = CU8,
                initializer = CLiteral(config.field.widthTiles * 4), // center of field (tile*8/2)
            )
        varDecls +=
            CVarDecl(
                name = "_sport_ball_y_$id",
                type = CU8,
                initializer = CLiteral(config.field.heightTiles * 4),
            )
        varDecls += CVarDecl(name = "_sport_score_home_$id", type = CU8, initializer = CLiteral(0))
        varDecls += CVarDecl(name = "_sport_score_away_$id", type = CU8, initializer = CLiteral(0))
        varDecls +=
            CVarDecl(
                name = "_sport_match_timer_$id",
                type = CU16,
                initializer =
                    CLiteral(
                        config.matchStructure.halfDurationSeconds *
                            60 // convert seconds to frames (60fps)
                    ),
            )
        varDecls += CVarDecl(name = "_sport_half_$id", type = CU8, initializer = CLiteral(1))

        val friction = config.ballPhysics.friction
        val bounce = config.ballPhysics.bounce
        val fieldW = config.field.widthTiles * 8 // tiles * 8 pixels/tile
        val fieldH = config.field.heightTiles * 8

        // sport_ball_update_{id}(dx, dy)
        // Move ball, apply friction, bounce off field edges.
        val ballUpdateBody =
            buildList<CStatement> {
                add(CComment("Move ball by (dx, dy)"))
                add(CExprStatement(CBinaryExpr(CVar("_sport_ball_x_$id"), "+=", CVar("dx"))))
                add(CExprStatement(CBinaryExpr(CVar("_sport_ball_y_$id"), "+=", CVar("dy"))))
                add(CComment("Bounce: reflect on field edges (field ${fieldW}x${fieldH}px)"))
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_sport_ball_x_$id"), ">=", CLiteral(fieldW)),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("_sport_ball_x_$id"),
                                        "=",
                                        CLiteral(fieldW - 1),
                                    )
                                ),
                                // Reflect direction by negating dx (caller owns dx var)
                                CComment("caller: negate dx for bounce (coefficient $bounce/255)"),
                            ),
                    )
                )
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_sport_ball_y_$id"), ">=", CLiteral(fieldH)),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("_sport_ball_y_$id"),
                                        "=",
                                        CLiteral(fieldH - 1),
                                    )
                                ),
                                CComment("caller: negate dy for bounce (coefficient $bounce/255)"),
                            ),
                    )
                )
                if (friction > 0) {
                    add(CComment("Friction: applied by caller each frame (friction=$friction/255)"))
                }
            }
        functions +=
            CFunction(
                name = "sport_ball_update_$id",
                returnType = CVoid,
                params = listOf(CParam("dx", CI8), CParam("dy", CI8)),
                body = ballUpdateBody,
                sectionComment = "Ball sport system: $id — ball physics update",
            )

        // sport_check_goal_{id}(ball_x, ball_y)
        // Detect if ball is in home or away goal area, increment score.
        val goalW = config.field.goalConfig.width * 8
        val goalH = config.field.goalConfig.height * 8
        val goalY = (fieldH / 2) - (goalH / 2)
        val checkGoalBody =
            buildList<CStatement> {
                if (config.field.hasGoals) {
                    add(
                        CComment(
                            "Home goal: left edge of field (x < $goalW, y in [$goalY, ${goalY + goalH}])"
                        )
                    )
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CBinaryExpr(CVar("ball_x"), "<", CLiteral(goalW)),
                                    "&&",
                                    CBinaryExpr(
                                        CBinaryExpr(CVar("ball_y"), ">=", CLiteral(goalY)),
                                        "&&",
                                        CBinaryExpr(CVar("ball_y"), "<", CLiteral(goalY + goalH)),
                                    ),
                                ),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("_sport_score_away_$id"),
                                            "+=",
                                            CLiteral(config.scoringRules.pointsPerGoal),
                                        )
                                    ),
                                    CReturn(CLiteral(1)),
                                ),
                        )
                    )
                    add(CComment("Away goal: right edge of field (x > ${fieldW - goalW})"))
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CBinaryExpr(CVar("ball_x"), ">", CLiteral(fieldW - goalW)),
                                    "&&",
                                    CBinaryExpr(
                                        CBinaryExpr(CVar("ball_y"), ">=", CLiteral(goalY)),
                                        "&&",
                                        CBinaryExpr(CVar("ball_y"), "<", CLiteral(goalY + goalH)),
                                    ),
                                ),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("_sport_score_home_$id"),
                                            "+=",
                                            CLiteral(config.scoringRules.pointsPerGoal),
                                        )
                                    ),
                                    CReturn(CLiteral(1)),
                                ),
                        )
                    )
                }
                add(CReturn(CLiteral(0)))
            }
        functions +=
            CFunction(
                name = "sport_check_goal_$id",
                returnType = CU8,
                params = listOf(CParam("ball_x", CU8), CParam("ball_y", CU8)),
                body = checkGoalBody,
                sectionComment = "Ball sport system: $id — goal detection",
            )

        // sport_match_update_{id}()
        // Decrement match timer, handle half transitions, check win condition.
        val halfDurationFrames = config.matchStructure.halfDurationSeconds * 60
        val targetScore = config.scoringRules.targetScore
        val matchUpdateBody =
            buildList<CStatement> {
                add(CComment("Decrement match timer"))
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_sport_match_timer_$id"), ">", CLiteral(0)),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(CVar("_sport_match_timer_$id"), "-=", CLiteral(1))
                                )
                            ),
                        elseBody =
                            listOf(
                                CComment("Half ended — advance to next half or match end"),
                                CIf(
                                    condition =
                                        CBinaryExpr(
                                            CVar("_sport_half_$id"),
                                            "<",
                                            CLiteral(config.matchStructure.halves),
                                        ),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CVar("_sport_half_$id"),
                                                    "+=",
                                                    CLiteral(1),
                                                )
                                            ),
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CVar("_sport_match_timer_$id"),
                                                    "=",
                                                    CLiteral(halfDurationFrames),
                                                )
                                            ),
                                        ),
                                    elseBody =
                                        listOf(
                                            CComment("match_end_$id: handle match over in scene")
                                        ),
                                ),
                            ),
                    )
                )
                add(CComment("Win condition: first to $targetScore goals"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(CVar("_sport_score_home_$id"), ">=", CLiteral(targetScore)),
                        thenBody = listOf(CComment("sport_home_wins_$id")),
                    )
                )
                add(
                    CIf(
                        condition =
                            CBinaryExpr(CVar("_sport_score_away_$id"), ">=", CLiteral(targetScore)),
                        thenBody = listOf(CComment("sport_away_wins_$id")),
                    )
                )
            }
        functions +=
            CFunction(
                name = "sport_match_update_$id",
                returnType = CVoid,
                body = matchUpdateBody,
                sectionComment = "Ball sport system: $id — match timer and win condition",
            )

        // Pickup reconciliation: convert SportPickupDef → PickupDef, delegate to GBDKSystemVisitor
        val pickupResult = buildPickupResult(config.id, config.pickups, gameIR)
        functions += pickupResult.functions as List<CFunction>
        varDecls += pickupResult.varDecls as List<CVarDecl>

        return GenreVisitorResult(functions = functions, varDecls = varDecls)
    }

    // -------------------------------------------------------------------------
    // Tournament system codegen
    // -------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun visitTournament(
        systemConfig: Map<String, Any>,
        @Suppress("UNUSED_PARAMETER") gameIR: GameIR,
    ): GenreVisitorResult {
        val config = systemConfig["config"] as? TournamentConfig ?: return GenreVisitorResult()
        val id = sanitizeCId(config.id)
        val participantCount = config.participantIds.size

        val functions = mutableListOf<CFunction>()
        val varDecls = mutableListOf<CVarDecl>()

        // Global state variables
        if (participantCount > 0) {
            varDecls +=
                CVarDecl(name = "_tournament_wins_$id", type = CArray(CU8, participantCount))
            varDecls +=
                CVarDecl(name = "_tournament_losses_$id", type = CArray(CU8, participantCount))
        }
        varDecls +=
            CVarDecl(name = "_tournament_current_match_$id", type = CU8, initializer = CLiteral(0))
        varDecls += CVarDecl(name = "_tournament_round_$id", type = CU8, initializer = CLiteral(1))

        // tournament_advance_{id}(winner_idx)
        // Advance bracket: increment winner's wins, loser's losses, move to next match.
        val advanceBody =
            buildList<CStatement> {
                add(CComment("Advance bracket: record winner (participant index)"))
                if (participantCount > 0) {
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(CVar("winner_idx"), "<", CLiteral(participantCount)),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CArrayAccess(
                                                CVar("_tournament_wins_$id"),
                                                CVar("winner_idx"),
                                            ),
                                            "+=",
                                            CLiteral(1),
                                        )
                                    )
                                ),
                        )
                    )
                }
                add(CComment("Record loser's loss (participant index)"))
                if (participantCount > 0) {
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(CVar("loser_idx"), "<", CLiteral(participantCount)),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CArrayAccess(
                                                CVar("_tournament_losses_$id"),
                                                CVar("loser_idx"),
                                            ),
                                            "+=",
                                            CLiteral(1),
                                        )
                                    )
                                ),
                        )
                    )
                }
                add(CComment("Advance to next match"))
                add(
                    CExprStatement(
                        CBinaryExpr(CVar("_tournament_current_match_$id"), "+=", CLiteral(1))
                    )
                )
            }
        functions +=
            CFunction(
                name = "tournament_advance_$id",
                returnType = CVoid,
                params = listOf(CParam("winner_idx", CU8), CParam("loser_idx", CU8)),
                body = advanceBody,
                sectionComment = "Tournament system: $id — bracket advance",
            )

        // tournament_standings_{id}()
        // Compute standings for round-robin (sort by wins, used for display).
        val standingsBody =
            buildList<CStatement> {
                add(CComment("Compute standings — sort participants by wins (simple bubble sort)"))
                if (participantCount > 1) {
                    add(CVarDecl("i", CU8, initializer = null))
                    add(CVarDecl("j", CU8, initializer = null))
                    add(CVarDecl("tmp", CU8, initializer = null))
                    add(
                        CFor(
                            init = CExprStatement(CBinaryExpr(CVar("i"), "=", CLiteral(0))),
                            condition = CBinaryExpr(CVar("i"), "<", CLiteral(participantCount - 1)),
                            increment = CUnaryExpr("++", CVar("i")),
                            body =
                                listOf(
                                    CFor(
                                        init =
                                            CExprStatement(
                                                CBinaryExpr(CVar("j"), "=", CLiteral(0))
                                            ),
                                        condition =
                                            CBinaryExpr(
                                                CVar("j"),
                                                "<",
                                                CLiteral(participantCount - 1),
                                            ),
                                        increment = CUnaryExpr("++", CVar("j")),
                                        body =
                                            listOf(
                                                CIf(
                                                    condition =
                                                        CBinaryExpr(
                                                            CArrayAccess(
                                                                CVar("_tournament_wins_$id"),
                                                                CVar("j"),
                                                            ),
                                                            "<",
                                                            CArrayAccess(
                                                                CVar("_tournament_wins_$id"),
                                                                CBinaryExpr(
                                                                    CVar("j"),
                                                                    "+",
                                                                    CLiteral(1),
                                                                ),
                                                            ),
                                                        ),
                                                    thenBody =
                                                        listOf(
                                                            // Swap wins[j] with wins[j+1]
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CVar("tmp"),
                                                                    "=",
                                                                    CArrayAccess(
                                                                        CVar(
                                                                            "_tournament_wins_$id"
                                                                        ),
                                                                        CVar("j"),
                                                                    ),
                                                                )
                                                            ),
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar(
                                                                            "_tournament_wins_$id"
                                                                        ),
                                                                        CVar("j"),
                                                                    ),
                                                                    "=",
                                                                    CArrayAccess(
                                                                        CVar(
                                                                            "_tournament_wins_$id"
                                                                        ),
                                                                        CBinaryExpr(
                                                                            CVar("j"),
                                                                            "+",
                                                                            CLiteral(1),
                                                                        ),
                                                                    ),
                                                                )
                                                            ),
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar(
                                                                            "_tournament_wins_$id"
                                                                        ),
                                                                        CBinaryExpr(
                                                                            CVar("j"),
                                                                            "+",
                                                                            CLiteral(1),
                                                                        ),
                                                                    ),
                                                                    "=",
                                                                    CVar("tmp"),
                                                                )
                                                            ),
                                                            // Swap losses[j] with losses[j+1] in
                                                            // lockstep
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CVar("tmp"),
                                                                    "=",
                                                                    CArrayAccess(
                                                                        CVar(
                                                                            "_tournament_losses_$id"
                                                                        ),
                                                                        CVar("j"),
                                                                    ),
                                                                )
                                                            ),
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar(
                                                                            "_tournament_losses_$id"
                                                                        ),
                                                                        CVar("j"),
                                                                    ),
                                                                    "=",
                                                                    CArrayAccess(
                                                                        CVar(
                                                                            "_tournament_losses_$id"
                                                                        ),
                                                                        CBinaryExpr(
                                                                            CVar("j"),
                                                                            "+",
                                                                            CLiteral(1),
                                                                        ),
                                                                    ),
                                                                )
                                                            ),
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar(
                                                                            "_tournament_losses_$id"
                                                                        ),
                                                                        CBinaryExpr(
                                                                            CVar("j"),
                                                                            "+",
                                                                            CLiteral(1),
                                                                        ),
                                                                    ),
                                                                    "=",
                                                                    CVar("tmp"),
                                                                )
                                                            ),
                                                        ),
                                                )
                                            ),
                                    )
                                ),
                        )
                    )
                }
            }
        functions +=
            CFunction(
                name = "tournament_standings_$id",
                returnType = CVoid,
                body = standingsBody,
                sectionComment = "Tournament system: $id — standings computation",
            )

        return GenreVisitorResult(functions = functions, varDecls = varDecls)
    }

    // -------------------------------------------------------------------------
    // Pickup reconciliation: convert SportPickupDef → engine PickupDef
    // -------------------------------------------------------------------------

    /**
     * Convert [SportPickupDef] list to engine [PickupDef] list and delegate to [GBDKSystemVisitor].
     *
     * Creates a synthetic [GenericSystem] with `type="pickup_system"` containing a
     * [PickupSystemConfig] built from the sport pickups. The [GBDKSystemVisitor] handles all pickup
     * codegen, so no logic is duplicated in this visitor.
     *
     * @param systemId ID of the parent sport system (used to namespace pickup system).
     * @param sportPickups List of [SportPickupDef] from the racing/ball sport config.
     * @param gameIR Full [GameIR] for context.
     * @return [GenreVisitorResult] from pickup system codegen (or empty if no pickups).
     */
    private fun buildPickupResult(
        systemId: String,
        sportPickups: List<SportPickupDef>,
        gameIR: GameIR,
    ): GenreVisitorResult {
        if (sportPickups.isEmpty()) return GenreVisitorResult()

        val pickupDefs =
            sportPickups.map { sportPickup ->
                PickupDef(
                    id = sportPickup.id,
                    effectType = if (sportPickup.durationFrames > 0) "timed" else "instant",
                    value = 1,
                    duration = sportPickup.durationFrames,
                    respawnFrames = 0,
                    maxActive = 4,
                )
            }
        val pickupConfig =
            PickupSystemConfig(pickups = pickupDefs, maxTotalPickups = pickupDefs.size * 4)
        val pickupSystemId = "${systemId}_pickups"
        val pickupSystem =
            GenericSystem(
                id = pickupSystemId,
                config = mapOf("type" to "pickup_system", "pickupConfig" to pickupConfig),
            )

        val sanitizedPickupId = sanitizeCId(pickupSystemId)
        val visitor = GBDKSystemVisitor(gameIR)
        val pickupFunctions = pickupSystem.accept(visitor)
        val pickupVarDecls = visitor.buildPickupVarDecls(pickupSystem, sanitizedPickupId)

        return GenreVisitorResult(functions = pickupFunctions, varDecls = pickupVarDecls)
    }
}
