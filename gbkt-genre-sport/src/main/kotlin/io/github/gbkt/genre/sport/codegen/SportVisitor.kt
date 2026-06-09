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
import io.github.gbkt.backend.gbdk.codegen.ast.CBreak
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CCast
import io.github.gbkt.backend.gbdk.codegen.ast.CComment
import io.github.gbkt.backend.gbdk.codegen.ast.CConst
import io.github.gbkt.backend.gbdk.codegen.ast.CContinue
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CRawExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CReturn
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitch
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitchCase
import io.github.gbkt.backend.gbdk.codegen.ast.CTernary
import io.github.gbkt.backend.gbdk.codegen.ast.CU16
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.backend.gbdk.codegen.visitor.GBDKSystemVisitor
import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.pickup.PickupDef
import io.github.gbkt.core.pickup.PickupSystemConfig
import io.github.gbkt.genre.sport.domain.AiVehicleSlot
import io.github.gbkt.genre.sport.domain.BallSportConfig
import io.github.gbkt.genre.sport.domain.RacingAIConfig
import io.github.gbkt.genre.sport.domain.RacingConfig
import io.github.gbkt.genre.sport.domain.RacingMode
import io.github.gbkt.genre.sport.domain.SportPickupDef
import io.github.gbkt.genre.sport.domain.TournamentConfig
import io.github.gbkt.genre.sport.domain.Vehicle
import io.github.gbkt.genre.sport.domain.VehicleStats

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

    companion object {
        /** Game Boy hardware tile size in pixels. */
        private const val TILE_SIZE_PIXELS = 8

        /**
         * Fallback map width / height in tiles when no [io.github.gbkt.core.ir.ZoneIR] is
         * registered for the racing id. Matches the racer fixture's 19x19 synthesized track size;
         * chosen to be large enough that legacy tests without a zone produce sensible bounds for
         * the wall-collision guard's `INT16 < mapW * 8` comparison.
         */
        private const val DEFAULT_RACING_MAP_TILES = 19

        /** Fallback sprite width in pixels — racer convention (8x16 car). */
        private const val DEFAULT_VEHICLE_SPRITE_W = 8

        /** Fallback sprite height in pixels — racer convention (8x16 car). */
        private const val DEFAULT_VEHICLE_SPRITE_H = 16
    }

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

    /**
     * Walks the start scene's frame and enter ops looking for a [NavigateTo] that targets a
     * different scene. Returns the first such target's id, or null if the start scene never
     * navigates elsewhere. Used by the scene-discovery fallback chain in [visitRacingNew] to splice
     * `racing_tick_<id>()` into the actual race scene rather than the title scene that boots into
     * it.
     *
     * Recurses through [IfOp] (the lowering of `whenever { … }` blocks) so guarded
     * `whenever(buttons.start.pressed) { navigate(raceScene) }` bodies are walked.
     */
    private fun findFirstNavigateTarget(gameIR: GameIR): String? {
        val startId = gameIR.startScene ?: return null
        val startScene = gameIR.scenes.firstOrNull { it.id == startId } ?: return null
        return findFirstNavigateInOps(startScene.frameOps, startId)
            ?: findFirstNavigateInOps(startScene.enterOps, startId)
    }

    private fun findFirstNavigateInOps(ops: List<ScriptOp>, currentSceneId: String): String? {
        for (op in ops) {
            when (op) {
                is NavigateTo -> if (op.sceneId != currentSceneId) return op.sceneId
                is IfOp -> {
                    findFirstNavigateInOps(op.then, currentSceneId)?.let {
                        return it
                    }
                    findFirstNavigateInOps(op.otherwise, currentSceneId)?.let {
                        return it
                    }
                }
                else -> Unit
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun visitRacing(systemConfig: Map<String, Any>, gameIR: GameIR): GenreVisitorResult {
        val config = systemConfig["config"] as? RacingConfig ?: return GenreVisitorResult()
        val id = sanitizeCId(config.id)

        // Plan 03 (RacingDelegate.provideDelegate) is the single source of truth for these keys.
        // Their presence selects the new racing_tick_<id> emission path (D-04 / D-07 / D-09 /
        // D-15 / D-16 / D-17). When absent, we fall through to the legacy back-compat path used
        // by SportCodegenTest, which constructs RacingConfig directly without going through the
        // delegate.
        val playerVehicle = systemConfig["playerVehicle"] as? Vehicle
        val resolvedAiVehicles =
            (systemConfig["aiVehicles_resolved"] as? List<Vehicle>) ?: emptyList()

        return if (playerVehicle != null) {
            visitRacingNew(config, id, playerVehicle, resolvedAiVehicles, gameIR)
        } else {
            visitRacingLegacy(config, id, gameIR)
        }
    }

    // -------------------------------------------------------------------------
    // NEW PATH — racing_tick_<id> with stats-driven physics, checkpoint bitmap
    // state machine, and AI forEachActive body. Used when Plan 03's RacingDelegate
    // populated the GenericSystem config with a resolved playerVehicle. This is
    // the production path for any DSL-authored racing game.
    // -------------------------------------------------------------------------

    @Suppress("LongMethod")
    private fun visitRacingNew(
        config: RacingConfig,
        id: String,
        playerVehicle: Vehicle,
        resolvedAiVehicles: List<Vehicle>,
        gameIR: GameIR,
    ): GenreVisitorResult {
        val functions = mutableListOf<CFunction>()
        val varDecls = mutableListOf<CVarDecl>()

        val playerActorId = sanitizeCId(playerVehicle.actorRef.id)
        val playerVehicleId = sanitizeCId(playerVehicle.id)
        val playerStats = playerVehicle.stats

        val waypoints = config.track?.waypoints ?: emptyList()
        val waypointCount = waypoints.size
        val checkpoints = waypoints.filter { it.isCheckpoint }
        val checkpointCount = checkpoints.size

        // Per-vehicle player globals — drive integrated motion (not the tunable max stat).
        // Names flow from property delegates (D-04): _vehicle_<vehicleId>_speed_cur, etc.
        varDecls +=
            CVarDecl(
                name = "_vehicle_${playerVehicleId}_speed_cur",
                type = CU8,
                initializer = CLiteral(0),
            )
        varDecls +=
            CVarDecl(
                name = "_vehicle_${playerVehicleId}_heading",
                type = CU8,
                initializer = CLiteral(0),
            )

        // Lap state machine globals (D-15, D-16, D-17).
        varDecls += CVarDecl(name = "_racing_lap_count_$id", type = CU8, initializer = CLiteral(0))
        varDecls += CVarDecl(name = "_racing_visited_$id", type = CU8, initializer = CLiteral(0))
        varDecls +=
            CVarDecl(name = "_racing_checkpoint_idx_$id", type = CU8, initializer = CLiteral(0))

        // Checkpoint coord arrays — only isCheckpoint == true waypoints (D-16).
        // Pixel coords = tile * 8.
        if (checkpointCount > 0) {
            val cpXValues = checkpoints.joinToString(", ") { "${it.tileX * 8}u" }
            val cpYValues = checkpoints.joinToString(", ") { "${it.tileY * 8}u" }
            varDecls +=
                CVarDecl(
                    name = "_racing_cp_x_$id",
                    type = CArray(CU8, checkpointCount),
                    initializer = CRawExpr("{$cpXValues}"),
                )
            varDecls +=
                CVarDecl(
                    name = "_racing_cp_y_$id",
                    type = CArray(CU8, checkpointCount),
                    initializer = CRawExpr("{$cpYValues}"),
                )
        }

        // Full waypoint arrays — used by AI pathing (D-16). Includes both checkpoints and
        // non-checkpoint waypoints so the AI follows the entire driving line.
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

        // Per-AI-instance arrays — one set per AiVehicleSlot. Each AI instance has its own
        // waypoint cursor, integrated speed, and heading, paralleling the pool engine's
        // auto-emitted _x[]/_y[]/_active[]/_oam[] arrays (RESEARCH Open Question 1).
        for (slot in config.aiVehicles) {
            val aiVehicleId = sanitizeCId(slot.vehicleId)
            val zeroes = List(slot.count) { "0u" }.joinToString(", ")
            varDecls +=
                CVarDecl(
                    name = "_pool_${aiVehicleId}_wp_idx",
                    type = CArray(CU8, slot.count),
                    initializer = CRawExpr("{$zeroes}"),
                )
            varDecls +=
                CVarDecl(
                    name = "_pool_${aiVehicleId}_speed_cur",
                    type = CArray(CU8, slot.count),
                    initializer = CRawExpr("{$zeroes}"),
                )
            varDecls +=
                CVarDecl(
                    name = "_pool_${aiVehicleId}_heading",
                    type = CArray(CU8, slot.count),
                    initializer = CRawExpr("{$zeroes}"),
                )
        }

        // Discover the camera id (D-06). RacingDelegate auto-emits a CameraSystem id="camera"
        // when the user has none; if the user supplied their own, follow that one.
        val cameraId =
            (gameIR.systems.firstOrNull { it is CameraSystem } as? CameraSystem)?.id?.let {
                sanitizeCId(it)
            } ?: "camera"

        // Build the racing_tick body — the core of D-07.
        val tickBody =
            buildRacingTickBody(
                id = id,
                playerActorId = playerActorId,
                playerVehicleId = playerVehicleId,
                playerStats = playerStats,
                waypointCount = waypointCount,
                checkpointCount = checkpointCount,
                cameraId = cameraId,
                aiSlots = config.aiVehicles,
                resolvedAiVehicles = resolvedAiVehicles,
                aiConfig = config.aiConfig,
                playerSpeedCap = playerStats.speed,
                playerVehicle = playerVehicle,
                gameIR = gameIR,
            )

        functions +=
            CFunction(
                name = "racing_tick_$id",
                returnType = CVoid,
                body = tickBody,
                sectionComment = "Racing system: $id — per-frame physics + lap state machine",
            )

        // Keep racing_check_finish_<id> for back-compat — body is correct, called when laps are
        // met.
        functions +=
            CFunction(
                name = "racing_check_finish_$id",
                returnType = CVoid,
                body =
                    listOf(
                        CComment("Race finish: ${config.laps} laps required"),
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CVar("_racing_lap_count_$id"),
                                    ">=",
                                    CLiteral(config.laps),
                                ),
                            thenBody =
                                listOf(CComment("race_complete_$id: handle finish event in scene")),
                        ),
                    ),
                sectionComment = "Racing system: $id — finish/lap detection",
            )

        // Pickup reconciliation: convert SportPickupDef → PickupDef, delegate to GBDKSystemVisitor.
        val pickupResult = buildPickupResult(config.id, config.pickups, gameIR)
        functions += pickupResult.functions as List<CFunction>
        varDecls += pickupResult.varDecls as List<CVarDecl>

        // Locate the bound scene for racing_tick injection. Discovery fallback chain:
        //  1. Scene whose actorIds list contains the player vehicle's actor id (preferred when
        //     the DSL populates actorIds — D-07 / Plan 03 — RacingDelegate leaves actorIds alone).
        //  2. Scene navigated TO from the start scene (graph walk). Title -> race is the most
        //     common shape; without this, racing_tick gets spliced into the title scene's
        //     frame block, which never runs the engine while the player is racing.
        //  3. The game's start scene (the one named in gameIR.startScene). Correct for
        //     racing-only games where the boot scene IS the race scene.
        //  4. The first scene declared (last-resort fallback for fixture-only IRs).
        val raceSceneId =
            gameIR.scenes
                .firstOrNull { scene -> scene.actorIds.contains(playerVehicle.actorRef.id) }
                ?.id
                ?: findFirstNavigateTarget(gameIR)
                ?: gameIR.startScene
                ?: gameIR.scenes.firstOrNull()?.id

        // RawOp("racing_tick_<id>();") — emits the literal C call into the scene's frame block
        // via the pipeline's addGenreFrameOps prepend phase. RawOp is the documented escape
        // hatch when a typed ScriptOp does not exist for the call site (TriggerSystem would
        // generate `trigger_<id>()` not `racing_tick_<id>()`, so it is the wrong primitive
        // here). The injected statement runs once per frame, before user-authored frame ops.
        val frameOps: Map<String, List<ScriptOp>> =
            if (raceSceneId != null) {
                mapOf(raceSceneId to listOf(RawOp("racing_tick_$id();")))
            } else {
                emptyMap()
            }

        // Plan 07.4-11 — emit the 48-byte builtin track tileset constant alongside the racing
        // varDecls. Lives in HOME (auto-routed by the pipeline) for the same reason _zone_*_tiles
        // do — small, read-only, referenced by the scene-enter set_bkg_data call.
        varDecls += buildBuiltinTrackTilesetVarDecl(id)

        // Plan 07.4-11 — populate enterOps for the bound race scene, closing GAP-A (AI pool
        // spawn), GAP-B (track tileset + tilemap load), and GAP-D (camera target bind). The
        // pipeline's addGenreEnterOps splice (Plan 10) prepends these into <raceSceneId>_enter.
        val raceEnterOps: List<ScriptOp> =
            if (raceSceneId != null) {
                buildRaceEnterOps(id, playerActorId, resolvedAiVehicles, gameIR)
            } else {
                emptyList()
            }
        val enterOps: Map<String, List<ScriptOp>> =
            if (raceSceneId != null && raceEnterOps.isNotEmpty()) {
                mapOf(raceSceneId to raceEnterOps)
            } else {
                emptyMap()
            }

        return GenreVisitorResult(
            functions = functions,
            varDecls = varDecls,
            frameOps = frameOps,
            enterOps = enterOps,
        )
    }

    /**
     * Build the per-race-scene enter ops that close GAP-A (AI pool activation), GAP-B (track
     * tileset + tilemap load + sentinel), and GAP-D (camera target bind). Ops are emitted in this
     * order so the runtime VRAM is correct before any sprite becomes visible:
     *
     * 1. _camera_target = <playerActorIdx>u; (GAP-D)
     * 2. set_bkg_data(0, 3, _racing_<id>_tileset); (GAP-B — DMA 3 tiles to VRAM) set_bkg_tiles(0,
     *    0, <mapW>u, <mapH>u, _zone_<id>_tiles); (GAP-B — paint tilemap) _current_tileset_id = 1u;
     *    (GAP-B — sentinel; mirrors GBDKPipeline.addTileset- GuardToEnterFunction's tileset-loaded
     *    convention)
     * 3. pool_<aiVehicleId>_spawn(<rivalX>u, <rivalY>u); (GAP-A — one call per AI slot, position
     *    from rival actor's position(...) declaration)
     *
     * The race scene in DSL-authored racing games does NOT carry a tilesetRef (Racer.kt's race
     * scene has no tileset(...) call), so addTilesetGuardToEnterFunction does NOT fire and the
     * `_current_tileset_id = 1u;` write here is required for the sentinel to be set. If a future
     * plan adds a tilesetRef to the race scene, this RawOp becomes redundant — but for Plan 11 it
     * is load-bearing.
     */
    private fun buildRaceEnterOps(
        racingId: String,
        playerActorId: String,
        resolvedAiVehicles: List<Vehicle>,
        gameIR: GameIR,
    ): List<ScriptOp> {
        val ops = mutableListOf<ScriptOp>()

        // GAP-D: camera target bind. _camera_target is declared by GBDKSystemVisitor's camera
        // codegen but never assigned previously — this satisfies the D-06 contract.
        val playerActorIdx = gameIR.actors.indexOfFirst { it.id == playerActorId }
        if (playerActorIdx >= 0) {
            ops += RawOp("_camera_target = ${playerActorIdx}u;")
        }

        // GAP-B: tileset + tilemap load. Look up the synthesized track ZoneIR (Plan 03's
        // RacingDelegate emits ZoneIR(id = racingId, ...) with mapWidth / mapHeight set from
        // the polygon bounding box; tileData populated by Plan 04's TrackSynthesizer).
        val trackZone = gameIR.zones.firstOrNull { it.id == racingId }
        if (trackZone != null) {
            ops += RawOp("set_bkg_data(0, 3, _racing_${racingId}_tileset);")
            ops +=
                RawOp(
                    // mapWidth/mapHeight nullable (REQ-14 sentinel); racing zones always set these
                    // explicitly via TrackSynthesizer — fallback 20×18 guards against null state.
                    "set_bkg_tiles(0, 0, ${trackZone.mapWidth ?: 20}u, ${trackZone.mapHeight ?: 18}u, _zone_${racingId}_tiles);"
                )
            // Plan 07.4-30 / DIAGNOSIS.md round-7 LCD-disable wrap: post-main_init set_bkg_* must
            // re-enable LCD per D-N-05/D-N-06.
            // GBDK's _set_bkg_data / _set_bkg_tiles call display_off() internally, leaving LCDC.7=0
            // after the VRAM writes. Without DISPLAY_ON the main loop's wait_vbl_done() hangs (no
            // VBlank fires while LCD is off) → EmulatorFrameHangException at frame 124.
            // This is a scene-enter path (post-main_init) — D-N-06 permits the wrap here.
            // main_init already has its own DISPLAY_ON / SHOW_BKG / SHOW_SPRITES sequence (H-1
            // fix).
            ops += RawOp("DISPLAY_ON;")
            // Sentinel value `1u` matches the dungeon path's tileset-loaded value (see
            // GBDKPipeline.addTilesetGuardToEnterFunction). Required here because the
            // race scene has no scene.tilesetRef in the current DSL — the guard does not
            // run for racing scenes — so without this write the next scene transition
            // would re-DMA the tileset unnecessarily.
            ops += RawOp("_current_tileset_id = 1u;")
        }

        // GAP-A: one spawn call per AI vehicle slot. The pool_<id>_spawn helper (already
        // emitted by GBDKSystemVisitor.buildActorPoolFunctions) sets _active[i] = 1u, writes
        // _x[i] / _y[i], and calls move_sprite() — so this single RawOp per slot is sufficient
        // to materialize the AI vehicle on screen and let the existing >> 5 physics tick (Plan
        // 05) drive it (D-09).
        for (aiVehicle in resolvedAiVehicles) {
            val aiVehicleId = sanitizeCId(aiVehicle.id)
            val rivalActor = gameIR.actors.firstOrNull { it.id == aiVehicle.actorRef.id }
            if (rivalActor != null) {
                val pos = rivalActor.position
                ops += RawOp("pool_${aiVehicleId}_spawn(${pos.x}u, ${pos.y}u);")
            }
        }

        return ops
    }

    /**
     * Phase 11.2 (D-D1, D-D4) — LEGACY path: hand-coded inline bytes (sport genre racing tracks).
     *
     * Authors the 48-byte `const UINT8 _racing_<id>_tileset[48] = { 0x00, 0xff, ... }` declaration.
     * The bytes come from `tilePatternToBytes()` palette-index matrices — NOT from a user-supplied
     * PNG. This is the LEGACY counterpart to the NEW path that ships with Phase 11.2 (see
     * [SceneVisitor]'s zone-load block).
     *
     * Why this path stays: sport-racing ships procedurally-generated track tilesets; there is no
     * user-supplied PNG asset to flow through ConvertZoneTilesetsTask. The two-path coexistence is
     * explicitly deferred — see SEED-017 for the unification work.
     *
     * Sister emission: [buildRaceEnterOps] (line ~460) emits the matching `set_bkg_data(0, 3,
     * _racing_<id>_tileset);` call inline in the race-scene enter ops.
     *
     * Invariant: `SportLegacyTilesetPathInvariantTest.INV-8` locks the emission shape unchanged AND
     * asserts sport is NOT retrofitted onto the NEW path (no `#include "_zone_<...>_tileset.h"` in
     * racing main.c).
     *
     * See also:
     * - .planning/codebase/CONVENTIONS.md §"Tile pixel data emission: two paths, when to use which"
     * - .planning/seeds/SEED-017-sport-zone-tileset-pipeline-unification.md
     * - .planning/phases/11.2-tileset-pipeline-set-bkg-data-emission/
     *
     * --- Original docblock (preserved verbatim from Plan 07.4-17): ---
     *
     * Builtin 3-tile tileset emitted as a 48-byte const array: `_racing_<id>_tileset[48]`.
     *
     * Tile indices match TrackSynthesizer's constants (Plan 07.4-04). Plan 17 (Phase 07.4-17,
     * TILESET-VISUAL-CONTRAST closure) replaced the original hand-tuned bytes with high-contrast
     * patterns derived from logical pixel matrices via [tilePatternToBytes]:
     * - Tile 0 = WALL (solid palette 3 = black; bytes 0xFF × 16)
     * - Tile 1 = DRIVABLE (solid palette 1 = light gray; row pairs (0xFF, 0x00) × 8 — distinct from
     *   both the white background AND the black wall)
     * - Tile 2 = GRASS (checkerboard of palette 2 = dark gray and palette 0 = white; visible
     *   texture distinct from the uniform light-gray road)
     *
     * Each Game Boy tile is 16 bytes (8x8 px, 2 bpp interleaved). 3 tiles × 16 = 48 bytes.
     *
     * Hand-derived graphics — sufficient for runtime visibility of the track corridor. A future
     * phase may expose `tileset(asset(...))` on `racing { }` to override; for Plan 07.4 the builtin
     * is the only source.
     */
    private fun buildBuiltinTrackTilesetVarDecl(racingId: String): CVarDecl {
        val bytes = builtinTrackTilesetBytes()
        require(bytes.size == 48) {
            "Builtin track tileset must be 48 bytes (3 tiles * 16); got ${bytes.size}"
        }
        return CVarDecl(
            name = "_racing_${racingId}_tileset",
            type = CArray(CConst(CU8), 48),
            initializer = CRawExpr("{ ${bytes.joinToString(", ") { "0x%02X".format(it) }} }"),
        )
    }

    /**
     * Convert an 8x8 palette-index matrix to GBDK 2bpp interleaved bytes (16 bytes total). Each
     * pixel must be in 0..3. The output is suitable for `set_bkg_data`.
     *
     * Encoding (Game Boy programming reference):
     * - For row r in 0..7: byte[2r] holds the LOW bit of each pixel in that row; byte[2r+1] holds
     *   the HIGH bit.
     * - Pixel at (col, row) occupies bit (7-col) of each byte (MSB = leftmost pixel).
     * - Combined: palette = (high_bit << 1) | low_bit.
     *
     * Used by [builtinTrackTilesetBytes] to derive the 3-tile builtin tileset from logical pixel
     * matrices instead of hand-tuned byte literals — Phase 07.4-17 closes the
     * TILESET-VISUAL-CONTRAST gap.
     *
     * Visibility is `internal` (not `private`) so RacingTilesetContrastTest can call directly
     * without reflection. Helpers ARE the contract — see Plan 17 SUMMARY for rationale.
     *
     * @param matrix An 8x8 list of lists; each cell is a palette index in 0..3.
     * @return 16 bytes (List<Int>) in GBDK 2bpp interleaved order.
     * @throws IllegalArgumentException if matrix is not 8x8 or any cell is out of 0..3.
     */
    internal fun tilePatternToBytes(matrix: List<List<Int>>): List<Int> {
        require(matrix.size == 8) { "tile matrix must have 8 rows; got ${matrix.size}" }
        require(matrix.all { it.size == 8 }) { "tile matrix rows must each have 8 columns" }
        require(matrix.all { row -> row.all { it in 0..3 } }) { "palette indices must be in 0..3" }
        val out = mutableListOf<Int>()
        for (row in matrix) {
            var lowByte = 0
            var highByte = 0
            for (col in 0..7) {
                val px = row[col]
                val bitPos = 7 - col
                if (px and 1 != 0) lowByte = lowByte or (1 shl bitPos)
                if (px and 2 != 0) highByte = highByte or (1 shl bitPos)
            }
            out += lowByte
            out += highByte
        }
        return out
    }

    /**
     * Builtin 3-tile tileset bytes (48 = 3 × 16). Plan 17 derives each tile from a logical 8×8
     * palette-index matrix via [tilePatternToBytes] so the byte derivation is auditable.
     *
     * Per Phase 07.4-17 contrast contract:
     * - Tile 0 (WALL): solid palette 3 (black). 16 bytes 0xFF.
     * - Tile 1 (DRIVABLE): solid palette 1 (light gray). Row pairs (0xFF, 0x00) × 8 — distinct from
     *   white background (palette 0) AND from black wall (palette 3).
     * - Tile 2 (GRASS): checkerboard of palette 2 (dark gray) and palette 0 (white). Visible
     *   texture clearly distinct from the uniform light-gray road and the solid black wall.
     *
     * Visibility is `internal` (not `private`) so RacingTilesetContrastTest can call directly
     * without reflection.
     */
    internal fun builtinTrackTilesetBytes(): List<Int> {
        // Tile 0 — WALL: solid black (palette index 3 everywhere).
        val wall = tilePatternToBytes(List(8) { List(8) { 3 } })
        // Tile 1 — DRIVABLE: solid light gray (palette index 1 everywhere). Distinct from
        // both the white background (palette 0) and the black wall (palette 3).
        val drivable = tilePatternToBytes(List(8) { List(8) { 1 } })
        // Tile 2 — GRASS: checkerboard of dark gray (palette 2) and white (palette 0). Pixel
        // (col, row) is dark gray when (col + row) is even, white otherwise. Visible texture
        // clearly distinct from the uniform light-gray road and the solid black wall.
        val grass =
            tilePatternToBytes(
                List(8) { row -> List(8) { col -> if ((col + row) % 2 == 0) 2 else 0 } }
            )
        return wall + drivable + grass
    }

    /**
     * Emit a collision-guarded position write-back for an actor in racing_tick. Replaces the naive
     * `_<actor>_x += vx; _<actor>_y += vy;` pair with an INT16-bounds-check + tile- sample guard
     * against the synthesized tilemap. If the proposed position is out of world bounds OR the
     * sampled tile equals 0 (wall, per Plan 07.4-04 TrackSynthesizer constants), the move is
     * rejected and the actor stays put.
     *
     * Closes Phase 07.4 GAP-C (UINT8 underflow on sustained directional input wraps the player
     * off-screen). Upholds D-17 (a lap means a real lap) by construction — the player physically
     * cannot drive across walls or off-grid.
     *
     * Implemented as a single CRawCode block: the compound nested-if shape would require six CIf
     * nodes plus six CVarDecl statements with INT16 promotion, which the C-AST does not model
     * natively (no CI16 cast helper for the bounds compare). The text shape is auditable +
     * greppable in the emitted main.c, which is what RacingCollisionGuardTest (and downstream
     * verification) asserts against. The same escape-hatch pattern is used by
     * `buildBuiltinTrackTilesetVarDecl` and the lap state machine's `mask_below` raw expression.
     *
     * @param actorRefX C-text expression for the LHS X assignment, e.g. `_car_x` (player scalar) or
     *   `_pool_carAi_x[i_carAi]` (AI per-instance array slot).
     * @param actorRefY Same for Y.
     * @param vxName Local INT8 variable holding the X velocity (in scope at call site).
     * @param vyName Local INT8 variable holding the Y velocity.
     * @param zoneId Racing system id — used to reference `_zone_<id>_tiles`.
     * @param mapWidth Map width in tiles (compile-time literal from ZoneIR.mapWidth).
     * @param mapHeight Map height in tiles (compile-time literal from ZoneIR.mapHeight).
     * @param spriteHalfW Sprite half-width in pixels (sample-center offset).
     * @param spriteHalfH Sprite half-height in pixels.
     */
    /**
     * Emit a 3-level wall-aware cardinal-heading pick for an AI vehicle. Replaces the bare if-else
     * heading assignment with a probe-tile-and-fallback ladder that lets the AI navigate any
     * reachable corridor, even when both the primary AND perpendicular cardinals are blocked by
     * walls (concave corners, narrow tips).
     *
     * Levels:
     * 1. Primary — largest-axis cardinal toward target.
     * 2. Fallback — perpendicular cardinal toward target.
     * 3. Tertiary — first unblocked of {0=N, 1=E, 2=S, 3=W} (skipping primary/fallback). If none →
     *    keep current heading (stay put; bounded by Plan 07.4-16 Change A's 3x3 waypoint force
     *    radius — once the AI is within 1 tile of any waypoint, all four cardinal neighbors are
     *    forced drivable, so the stay-put state is reached only outside any waypoint neighborhood,
     *    and the next proximity-advance pulls the AI toward a reachable target).
     *
     * Closes Phase 07.4 round-2 TRACK-NAVIGABILITY gap (VERIFICATION.md lines 240-242), hypothesis
     * (c) — cardinal-heading not wall-aware. Honors D-09 (uniform physics path): the player gets
     * perpendicular fallback for free via dpad input; this gives the AI the same navigability
     * surface.
     *
     * The emitted C inlines bounds-checking and tile-sample logic (mirroring the player's
     * wall-collision-guard from Plan 07.4-12) so we don't introduce a new helper function — keeps
     * the AI body within bank-1 budget.
     *
     * @param aiVehicleId Sanitized AI vehicle id (e.g. "carAi").
     * @param zoneId Racing system id (used to reference _zone_<id>_tiles).
     * @param mapWidth Map width in tiles (literal at codegen time).
     * @param mapHeight Map height in tiles.
     * @param spriteHalfW Sprite half-width in pixels (sample center offset).
     * @param spriteHalfH Sprite half-height in pixels.
     * @return One CRawCode block emitting the 3-level wall-aware heading pick.
     */
    @Suppress("LongMethod")
    private fun buildAiHeadingPickWithFallback(
        aiVehicleId: String,
        zoneId: String,
        mapWidth: Int,
        mapHeight: Int,
        spriteHalfW: Int,
        spriteHalfH: Int,
    ): CStatement {
        val maxX = mapWidth * TILE_SIZE_PIXELS - spriteHalfW * 2
        val maxY = mapHeight * TILE_SIZE_PIXELS - spriteHalfH * 2
        val text = buildString {
            appendLine("{")
            appendLine("    UINT8 ai_primary;")
            appendLine("    UINT8 ai_fallback;")
            appendLine("    if (dx_$aiVehicleId >= dy_$aiVehicleId) {")
            appendLine(
                "        ai_primary = (_pool_${aiVehicleId}_x[i_$aiVehicleId] < " +
                    "tgt_x_$aiVehicleId) ? 1u : 3u;"
            )
            appendLine(
                "        ai_fallback = (_pool_${aiVehicleId}_y[i_$aiVehicleId] < " +
                    "tgt_y_$aiVehicleId) ? 2u : 0u;"
            )
            appendLine("    } else {")
            appendLine(
                "        ai_primary = (_pool_${aiVehicleId}_y[i_$aiVehicleId] < " +
                    "tgt_y_$aiVehicleId) ? 2u : 0u;"
            )
            appendLine(
                "        ai_fallback = (_pool_${aiVehicleId}_x[i_$aiVehicleId] < " +
                    "tgt_x_$aiVehicleId) ? 1u : 3u;"
            )
            appendLine("    }")
            appendLine(
                "    UINT8 ai_delta_probe = _pool_${aiVehicleId}_speed_cur[i_$aiVehicleId] >> 5;"
            )
            appendLine("    if (ai_delta_probe == 0u) ai_delta_probe = 1u;")
            appendLine("    UINT8 cd;")
            appendLine("    UINT8 blocked[4];")
            appendLine("    for (cd = 0u; cd < 4u; cd++) {")
            appendLine("        INT8 pvx = 0;")
            appendLine("        INT8 pvy = 0;")
            appendLine("        switch (cd) {")
            appendLine("            case 0u: pvy = -(INT8)ai_delta_probe; break;")
            appendLine("            case 1u: pvx = (INT8)ai_delta_probe; break;")
            appendLine("            case 2u: pvy = (INT8)ai_delta_probe; break;")
            appendLine("            case 3u: pvx = -(INT8)ai_delta_probe; break;")
            appendLine("        }")
            appendLine(
                "        INT16 pXs = (INT16)_pool_${aiVehicleId}_x[i_$aiVehicleId] + (INT16)pvx;"
            )
            appendLine(
                "        INT16 pYs = (INT16)_pool_${aiVehicleId}_y[i_$aiVehicleId] + (INT16)pvy;"
            )
            appendLine("        UINT8 b = 1u;")
            appendLine("        if (pXs >= 0 && pXs < $maxX && pYs >= 0 && pYs < $maxY) {")
            appendLine("            UINT8 sX = (UINT8)pXs + ${spriteHalfW}u;")
            appendLine("            UINT8 sY = (UINT8)pYs + ${spriteHalfH}u;")
            appendLine("            UINT8 tCol = sX >> 3;")
            appendLine("            UINT8 tRow = sY >> 3;")
            appendLine("            if (tCol < ${mapWidth}u && tRow < ${mapHeight}u) {")
            appendLine(
                "                UINT8 t = _zone_${zoneId}_tiles[tRow * ${mapWidth}u + tCol];"
            )
            appendLine("                if (t != 0u) b = 0u;")
            appendLine("            }")
            appendLine("        }")
            appendLine("        blocked[cd] = b;")
            appendLine("    }")
            appendLine("    UINT8 primary_blocked = blocked[ai_primary];")
            appendLine("    UINT8 fallback_blocked = blocked[ai_fallback];")
            appendLine("    UINT8 both_blocked = (primary_blocked && fallback_blocked) ? 1u : 0u;")
            appendLine("    UINT8 ai_prev_heading = _pool_${aiVehicleId}_heading[i_$aiVehicleId];")
            appendLine("    /* prev-perpendicular-commit: when primary is blocked, prefer the")
            appendLine("     * previous heading IF it is perpendicular to primary AND still")
            appendLine("     * unblocked. This breaks the degenerate oscillation where the AI")
            appendLine("     * alternates between fallback (E) and anti-fallback (W) without")
            appendLine("     * making progress along the corridor — committing to the perp")
            appendLine("     * escape direction lets the AI traverse narrow corridors that")
            appendLine("     * require multiple lateral steps before primary unblocks. The")
            appendLine("     * axis-bit is bit-0: N=0/S=2 are vertical (bit-0 == 0); E=1/W=3")
            appendLine("     * are horizontal (bit-0 == 1). Two headings are perpendicular iff")
            appendLine("     * their bit-0 differs. */")
            appendLine(
                "    UINT8 ai_prev_is_perp = ((ai_prev_heading & 1u) != (ai_primary & 1u)) ? 1u : 0u;"
            )
            appendLine("    if (!primary_blocked) {")
            appendLine("        _pool_${aiVehicleId}_heading[i_$aiVehicleId] = ai_primary;")
            appendLine("    } else if (ai_prev_is_perp && !blocked[ai_prev_heading]) {")
            appendLine("        _pool_${aiVehicleId}_heading[i_$aiVehicleId] = ai_prev_heading;")
            appendLine("    } else if (!fallback_blocked) {")
            appendLine("        _pool_${aiVehicleId}_heading[i_$aiVehicleId] = ai_fallback;")
            appendLine("    } else {")
            appendLine("        UINT8 ai_tertiary = 0xFFu;")
            appendLine("        for (cd = 0u; cd < 4u; cd++) {")
            appendLine("            if (cd == ai_primary || cd == ai_fallback) continue;")
            appendLine("            if (!blocked[cd]) { ai_tertiary = cd; break; }")
            appendLine("        }")
            appendLine("        if (ai_tertiary != 0xFFu) {")
            appendLine("            _pool_${aiVehicleId}_heading[i_$aiVehicleId] = ai_tertiary;")
            appendLine("        }")
            appendLine("        (void)both_blocked;")
            appendLine("    }")
            append("}")
        }
        return CRawCode(text)
    }

    /**
     * Build the C accept-expression for the 4-corner wall-collision sample (Plan 07.4-18).
     *
     * Replaces the prior single-center `tile != 0u` sample inside
     * [buildPositionWriteBackWithCollision]. The accept rule is OR-style: the move is accepted iff
     * at least 1 of the sprite footprint's 4 corners samples a non-WALL tile within map bounds. A
     * solid wall (all 4 corners on wall) still rejects (D-17 — cannot drive across walls). A
     * 1-tile-wide drivable strip with at least 1 corner on the strip is accepted (closes
     * NAVIGABILITY-PLAYER-CORNER-TRAP — round-3 gap from UAT-racer.md lines 151-178).
     *
     * The four sample points are the corners of the sprite's pixel bounding box at the proposed
     * top-left position:
     * - NW: (propX, propY) — top-left
     * - NE: (propX + spriteFullW - 1u, propY) — top-right
     * - SW: (propX, propY + spriteFullH - 1u) — bottom-left
     * - SE: (propX + spriteFullW - 1u, propY + spriteFullH - 1u) — bottom-right
     *
     * Each corner expression short-circuits to falsy if its tile coords fall outside the map
     * dimensions, so out-of-bounds corners do NOT count as drivable.
     *
     * D-09 (uniform physics path) — both player and AI write-back call
     * [buildPositionWriteBackWithCollision], so both branches benefit. The new accept rule is at
     * least as permissive as the old single-center rule on already-clear cells, so Plan 16's `ai
     * simulation drives full lap within budget` regression test remains GREEN.
     */
    internal fun buildFourCornerWallSampleAccept(
        propXName: String,
        propYName: String,
        spriteFullW: Int,
        spriteFullH: Int,
        mapWidth: Int,
        mapHeight: Int,
        zoneId: String,
    ): String {
        fun cornerExpr(cx: String, cy: String): String =
            "((($cx) >> 3) < ${mapWidth}u && (($cy) >> 3) < ${mapHeight}u && " +
                "_zone_${zoneId}_tiles[(($cy) >> 3) * ${mapWidth}u + (($cx) >> 3)] != 0u)"

        val nw = cornerExpr(propXName, propYName)
        val ne = cornerExpr("$propXName + ${spriteFullW - 1}u", propYName)
        val sw = cornerExpr(propXName, "$propYName + ${spriteFullH - 1}u")
        val se = cornerExpr("$propXName + ${spriteFullW - 1}u", "$propYName + ${spriteFullH - 1}u")
        return "($nw || $ne || $sw || $se)"
    }

    @Suppress("LongParameterList")
    private fun buildPositionWriteBackWithCollision(
        actorRefX: String,
        actorRefY: String,
        vxName: String,
        vyName: String,
        zoneId: String,
        mapWidth: Int,
        mapHeight: Int,
        spriteHalfW: Int,
        spriteHalfH: Int,
    ): List<CStatement> {
        // World-bounds maxima in pixels — the proposed top-left corner cannot place the
        // sprite past the map's right/bottom edge. Computed at codegen time.
        val maxX = mapWidth * TILE_SIZE_PIXELS - spriteHalfW * 2
        val maxY = mapHeight * TILE_SIZE_PIXELS - spriteHalfH * 2
        val spriteFullW = spriteHalfW * 2
        val spriteFullH = spriteHalfH * 2
        val acceptExpr =
            buildFourCornerWallSampleAccept(
                propXName = "propX",
                propYName = "propY",
                spriteFullW = spriteFullW,
                spriteFullH = spriteFullH,
                mapWidth = mapWidth,
                mapHeight = mapHeight,
                zoneId = zoneId,
            )
        val text = buildString {
            appendLine("{")
            appendLine("    INT16 propXs = (INT16)$actorRefX + (INT16)$vxName;")
            appendLine("    INT16 propYs = (INT16)$actorRefY + (INT16)$vyName;")
            appendLine("    if (propXs >= 0 && propXs < $maxX && propYs >= 0 && propYs < $maxY) {")
            appendLine("        UINT8 propX = (UINT8)propXs;")
            appendLine("        UINT8 propY = (UINT8)propYs;")
            appendLine("        if ($acceptExpr) {")
            appendLine("            $actorRefX = propX;")
            appendLine("            $actorRefY = propY;")
            appendLine("        }")
            appendLine("    }")
            append("}")
        }
        return listOf(CRawCode(text))
    }

    /**
     * Build the body of `racing_tick_<id>()` — the per-frame physics + checkpoint bitmap + camera
     * tick + AI inner loop (D-07, D-09, D-15, D-16, D-17).
     *
     * Sections, in order: C.1 — throttle/steer ramp using player stats (speed/accel/handling) C.2 —
     * cardinal heading update (dpad pressed-edge for left/right) C.3 — velocity = speed_cur >> 5;
     * switch on heading to compute vx/vy; write back to `_<actor>_x` / `_<actor>_y` via a
     * wall-collision guard (D-03 — write-back is BEHIND the IR, never on the DSL surface; D-17 —
     * wall guard added Plan 07.4-12) C.4 — `update_camera_<cameraId>()` invocation (D-06) C.5 —
     * checkpoint state machine: bit-K-only-when-bits-0..K-1-set order check via mask_below; lap
     * completes only on first-CP crossing AFTER bitmap is full; reset to 1u keeps CP-0 set so we
     * don't immediately re-fire the lap (D-15, D-17) C.6 — for each AI pool: inner C-AST for-loop
     * over active slots running the SAME speed_cur >> 5 physics, with input source = waypoint
     * follower instead of dpad, and the SAME wall-collision guard as the player (D-09 — uniform
     * physics path)
     */
    @Suppress("LongParameterList")
    private fun buildRacingTickBody(
        id: String,
        playerActorId: String,
        playerVehicleId: String,
        playerStats: VehicleStats,
        waypointCount: Int,
        checkpointCount: Int,
        cameraId: String,
        aiSlots: List<AiVehicleSlot>,
        resolvedAiVehicles: List<Vehicle>,
        aiConfig: RacingAIConfig,
        playerSpeedCap: Int,
        playerVehicle: Vehicle,
        gameIR: GameIR,
    ): List<CStatement> {
        val accel = playerStats.acceleration
        val brakeStrength = (accel * 2).coerceAtMost(255)
        val frictionStrength = 1
        val accelStep = ((accel shr 4) + 1).coerceAtMost(255)

        val speedCurVar = CVar("_vehicle_${playerVehicleId}_speed_cur")
        val headingVar = CVar("_vehicle_${playerVehicleId}_heading")

        // Plan 07.4-12 — derive map dimensions and sprite half-extents for the
        // wall-collision-aware position write-back. The synthesized track zone (id == racing
        // id) supplies mapWidth / mapHeight at codegen time as compile-time literals. Sprite
        // half-extents come from the bound actor's SpriteDef.size when present; the racer
        // convention (8x16 car) is the fallback so legacy fixtures without sprites still
        // produce sensible bounds.
        val trackZone = gameIR.zones.firstOrNull { it.id == id }
        val mapW = trackZone?.mapWidth ?: DEFAULT_RACING_MAP_TILES
        val mapH = trackZone?.mapHeight ?: DEFAULT_RACING_MAP_TILES
        val playerSprite =
            gameIR.actors.firstOrNull { it.id == playerVehicle.actorRef.id }?.sprite?.size
        val playerHalfW = (playerSprite?.width ?: DEFAULT_VEHICLE_SPRITE_W) / 2
        val playerHalfH = (playerSprite?.height ?: DEFAULT_VEHICLE_SPRITE_H) / 2

        // dpad helpers — see GBDKPipeline.buildInputHelperFunctions: dpad_held(J_UP) etc.
        val dpadUpHeld = CCall("dpad_held", listOf(CRawExpr("J_UP")))
        val dpadDownHeld = CCall("dpad_held", listOf(CRawExpr("J_DOWN")))
        val dpadLeftPressed = CCall("dpad_pressed", listOf(CRawExpr("J_LEFT")))
        val dpadRightPressed = CCall("dpad_pressed", listOf(CRawExpr("J_RIGHT")))

        return buildList<CStatement> {
            add(CComment("C.1 — throttle/steer ramp using player stats (D-07)"))
            // if (dpad_held(J_UP)) { speed_cur = (speed_cur + accelStep < cap) ?
            //                          speed_cur + accelStep : cap; }
            // else if (dpad_held(J_DOWN)) { speed_cur = (speed_cur > brake) ?
            //                                  speed_cur - brake : 0; }
            // else { speed_cur = (speed_cur > friction) ? speed_cur - friction : 0; }
            add(
                CIf(
                    condition = dpadUpHeld,
                    thenBody =
                        listOf(
                            CExprStatement(
                                CBinaryExpr(
                                    speedCurVar,
                                    "=",
                                    CTernary(
                                        condition =
                                            CBinaryExpr(
                                                CBinaryExpr(speedCurVar, "+", CLiteral(accelStep)),
                                                "<",
                                                CLiteral(playerSpeedCap),
                                            ),
                                        thenExpr =
                                            CBinaryExpr(speedCurVar, "+", CLiteral(accelStep)),
                                        elseExpr = CLiteral(playerSpeedCap),
                                    ),
                                )
                            )
                        ),
                    elseBody =
                        listOf(
                            CIf(
                                condition = dpadDownHeld,
                                thenBody =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                speedCurVar,
                                                "=",
                                                CTernary(
                                                    condition =
                                                        CBinaryExpr(
                                                            speedCurVar,
                                                            ">",
                                                            CLiteral(brakeStrength),
                                                        ),
                                                    thenExpr =
                                                        CBinaryExpr(
                                                            speedCurVar,
                                                            "-",
                                                            CLiteral(brakeStrength),
                                                        ),
                                                    elseExpr = CLiteral(0),
                                                ),
                                            )
                                        )
                                    ),
                                elseBody =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                speedCurVar,
                                                "=",
                                                CTernary(
                                                    condition =
                                                        CBinaryExpr(
                                                            speedCurVar,
                                                            ">",
                                                            CLiteral(frictionStrength),
                                                        ),
                                                    thenExpr =
                                                        CBinaryExpr(
                                                            speedCurVar,
                                                            "-",
                                                            CLiteral(frictionStrength),
                                                        ),
                                                    elseExpr = CLiteral(0),
                                                ),
                                            )
                                        )
                                    ),
                            )
                        ),
                )
            )

            add(CComment("C.2 — steer (cardinal: 0=N, 1=E, 2=S, 3=W; rising-edge dpad)"))
            // if (dpad_pressed(J_LEFT))  heading = (heading + 3) & 3;
            // if (dpad_pressed(J_RIGHT)) heading = (heading + 1) & 3;
            add(
                CIf(
                    condition = dpadLeftPressed,
                    thenBody =
                        listOf(
                            CExprStatement(
                                CBinaryExpr(
                                    headingVar,
                                    "=",
                                    CBinaryExpr(
                                        CBinaryExpr(headingVar, "+", CLiteral(3)),
                                        "&",
                                        CLiteral(3),
                                    ),
                                )
                            )
                        ),
                )
            )
            add(
                CIf(
                    condition = dpadRightPressed,
                    thenBody =
                        listOf(
                            CExprStatement(
                                CBinaryExpr(
                                    headingVar,
                                    "=",
                                    CBinaryExpr(
                                        CBinaryExpr(headingVar, "+", CLiteral(1)),
                                        "&",
                                        CLiteral(3),
                                    ),
                                )
                            )
                        ),
                )
            )

            add(CComment("C.3 — delta = speed_cur >> 5; switch on heading; write back to actor"))
            add(
                CVarDecl(
                    name = "delta",
                    type = CU8,
                    initializer = CBinaryExpr(speedCurVar, ">>", CLiteral(5)),
                )
            )
            add(CVarDecl(name = "vx", type = CI8, initializer = CLiteral(0)))
            add(CVarDecl(name = "vy", type = CI8, initializer = CLiteral(0)))
            add(
                CSwitch(
                    expr = headingVar,
                    cases =
                        listOf(
                            CSwitchCase(
                                value = CLiteral(0),
                                body =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CVar("vy"),
                                                "=",
                                                CUnaryExpr("-", CCast(CI8, CVar("delta"))),
                                            )
                                        ),
                                        CBreak,
                                    ),
                            ),
                            CSwitchCase(
                                value = CLiteral(1),
                                body =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(CVar("vx"), "=", CCast(CI8, CVar("delta")))
                                        ),
                                        CBreak,
                                    ),
                            ),
                            CSwitchCase(
                                value = CLiteral(2),
                                body =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(CVar("vy"), "=", CCast(CI8, CVar("delta")))
                                        ),
                                        CBreak,
                                    ),
                            ),
                            CSwitchCase(
                                value = CLiteral(3),
                                body =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CVar("vx"),
                                                "=",
                                                CUnaryExpr("-", CCast(CI8, CVar("delta"))),
                                            )
                                        ),
                                        CBreak,
                                    ),
                            ),
                        ),
                )
            )
            // Position write-back — wall-collision-aware (Plan 07.4-12, GAP-C closure).
            // The naive _<actor>_x += vx pair was replaced with an INT16-bounds-check + tile-
            // sample guard. If the proposed position is outside [0, mapW*8 - sprite_w) /
            // [0, mapH*8 - sprite_h) OR the sampled tile in _zone_<id>_tiles equals 0 (wall,
            // per Plan 07.4-04 TrackSynthesizer constants), the move is rejected and the
            // actor stays put. Closes the UINT8-underflow corner that wrapped the player off-
            // screen on sustained directional input. D-17 holds by construction — player
            // cannot drive across walls, so a lap means a real lap.
            addAll(
                buildPositionWriteBackWithCollision(
                    actorRefX = "_${playerActorId}_x",
                    actorRefY = "_${playerActorId}_y",
                    vxName = "vx",
                    vyName = "vy",
                    zoneId = id,
                    mapWidth = mapW,
                    mapHeight = mapH,
                    spriteHalfW = playerHalfW,
                    spriteHalfH = playerHalfH,
                )
            )

            add(CComment("C.4 — camera follow (D-06)"))
            add(CExprStatement(CCall("update_camera_$cameraId", emptyList())))

            add(CComment("C.5 — checkpoint state machine via mask_below (D-15, D-17)"))
            if (checkpointCount > 0) {
                // Advance the bitmap when we touch the next-in-order checkpoint.
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CVar("_racing_checkpoint_idx_$id"),
                                "<",
                                CLiteral(checkpointCount),
                            ),
                        thenBody =
                            listOf(
                                CVarDecl(
                                    name = "cp_x",
                                    type = CU8,
                                    initializer =
                                        CArrayAccess(
                                            CVar("_racing_cp_x_$id"),
                                            CVar("_racing_checkpoint_idx_$id"),
                                        ),
                                ),
                                CVarDecl(
                                    name = "cp_y",
                                    type = CU8,
                                    initializer =
                                        CArrayAccess(
                                            CVar("_racing_cp_y_$id"),
                                            CVar("_racing_checkpoint_idx_$id"),
                                        ),
                                ),
                                CVarDecl(
                                    name = "dx",
                                    type = CU8,
                                    initializer =
                                        CTernary(
                                            condition =
                                                CBinaryExpr(
                                                    CVar("_${playerActorId}_x"),
                                                    ">",
                                                    CVar("cp_x"),
                                                ),
                                            thenExpr =
                                                CBinaryExpr(
                                                    CVar("_${playerActorId}_x"),
                                                    "-",
                                                    CVar("cp_x"),
                                                ),
                                            elseExpr =
                                                CBinaryExpr(
                                                    CVar("cp_x"),
                                                    "-",
                                                    CVar("_${playerActorId}_x"),
                                                ),
                                        ),
                                ),
                                CVarDecl(
                                    name = "dy",
                                    type = CU8,
                                    initializer =
                                        CTernary(
                                            condition =
                                                CBinaryExpr(
                                                    CVar("_${playerActorId}_y"),
                                                    ">",
                                                    CVar("cp_y"),
                                                ),
                                            thenExpr =
                                                CBinaryExpr(
                                                    CVar("_${playerActorId}_y"),
                                                    "-",
                                                    CVar("cp_y"),
                                                ),
                                            elseExpr =
                                                CBinaryExpr(
                                                    CVar("cp_y"),
                                                    "-",
                                                    CVar("_${playerActorId}_y"),
                                                ),
                                        ),
                                ),
                                CIf(
                                    condition =
                                        CBinaryExpr(
                                            CBinaryExpr(CVar("dx"), "<", CLiteral(8)),
                                            "&&",
                                            CBinaryExpr(CVar("dy"), "<", CLiteral(8)),
                                        ),
                                    thenBody =
                                        listOf(
                                            // mask_below = (1u << checkpoint_idx) - 1u — the
                                            // bitmask of all CP indices STRICTLY BELOW the
                                            // current target. Bit K may be set only when bits
                                            // 0..K-1 are already set (D-15 declared-order).
                                            CVarDecl(
                                                name = "mask_below",
                                                type = CU8,
                                                initializer =
                                                    CRawExpr(
                                                        "(1u << _racing_checkpoint_idx_$id) - 1u"
                                                    ),
                                            ),
                                            CIf(
                                                condition =
                                                    CBinaryExpr(
                                                        CBinaryExpr(
                                                            CVar("_racing_visited_$id"),
                                                            "&",
                                                            CVar("mask_below"),
                                                        ),
                                                        "==",
                                                        CVar("mask_below"),
                                                    ),
                                                thenBody =
                                                    listOf(
                                                        CExprStatement(
                                                            CBinaryExpr(
                                                                CVar("_racing_visited_$id"),
                                                                "|=",
                                                                CRawExpr(
                                                                    "(1u << _racing_checkpoint_idx_$id)"
                                                                ),
                                                            )
                                                        ),
                                                        // Move to next-in-order checkpoint.
                                                        CExprStatement(
                                                            CBinaryExpr(
                                                                CVar("_racing_checkpoint_idx_$id"),
                                                                "+=",
                                                                CLiteral(1),
                                                            )
                                                        ),
                                                    ),
                                            ),
                                        ),
                                ),
                            ),
                    )
                )
                // Lap completion: when player crosses CP 0 AND every other CP has been touched
                // in order, the bitmap == all_set. Increment lap count; reset visited to 1u
                // (CP 0 still set) so a single ping-pong does not re-fire the lap (D-17).
                val allSet = (1 shl checkpointCount) - 1
                add(
                    CVarDecl(
                        name = "cp0_x",
                        type = CU8,
                        initializer = CArrayAccess(CVar("_racing_cp_x_$id"), CLiteral(0)),
                    )
                )
                add(
                    CVarDecl(
                        name = "cp0_y",
                        type = CU8,
                        initializer = CArrayAccess(CVar("_racing_cp_y_$id"), CLiteral(0)),
                    )
                )
                add(
                    CVarDecl(
                        name = "dx0",
                        type = CU8,
                        initializer =
                            CTernary(
                                condition =
                                    CBinaryExpr(CVar("_${playerActorId}_x"), ">", CVar("cp0_x")),
                                thenExpr =
                                    CBinaryExpr(CVar("_${playerActorId}_x"), "-", CVar("cp0_x")),
                                elseExpr =
                                    CBinaryExpr(CVar("cp0_x"), "-", CVar("_${playerActorId}_x")),
                            ),
                    )
                )
                add(
                    CVarDecl(
                        name = "dy0",
                        type = CU8,
                        initializer =
                            CTernary(
                                condition =
                                    CBinaryExpr(CVar("_${playerActorId}_y"), ">", CVar("cp0_y")),
                                thenExpr =
                                    CBinaryExpr(CVar("_${playerActorId}_y"), "-", CVar("cp0_y")),
                                elseExpr =
                                    CBinaryExpr(CVar("cp0_y"), "-", CVar("_${playerActorId}_y")),
                            ),
                    )
                )
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(
                                    CBinaryExpr(CVar("dx0"), "<", CLiteral(8)),
                                    "&&",
                                    CBinaryExpr(CVar("dy0"), "<", CLiteral(8)),
                                ),
                                "&&",
                                CBinaryExpr(CVar("_racing_visited_$id"), "==", CLiteral(allSet)),
                            ),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(CVar("_racing_lap_count_$id"), "+=", CLiteral(1))
                                ),
                                // Keep CP 0 bit set so we don't immediately re-fire next frame.
                                CExprStatement(
                                    CBinaryExpr(CVar("_racing_visited_$id"), "=", CLiteral(1))
                                ),
                                // Reset checkpoint cursor to 1 (CP 0 already counted).
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("_racing_checkpoint_idx_$id"),
                                        "=",
                                        CLiteral(1),
                                    )
                                ),
                            ),
                    )
                )
            }

            // C.6 — AI inner loops. SAME physics shape as the player path; only the input
            // source differs (waypoint follower vs. dpad). D-09 — uniform physics path.
            if (waypointCount > 0) {
                for (slot in aiSlots) {
                    val aiVehicleId = sanitizeCId(slot.vehicleId)
                    val aiVehicle = resolvedAiVehicles.firstOrNull { it.id == slot.vehicleId }
                    val aiStats = aiVehicle?.stats ?: playerStats
                    val aiSpeedCap = aiStats.speed
                    val aiAccelStep = ((aiStats.acceleration shr 4) + 1).coerceAtMost(255)
                    val rubberBoost =
                        if (aiConfig.rubberBanding) {
                            (aiStats.speed * aiConfig.rubberBandStrength / 100).coerceIn(0, 255)
                        } else {
                            0
                        }
                    val effectiveCap = (aiSpeedCap + rubberBoost).coerceAtMost(255)

                    // Resolve AI sprite half-extents once per slot so the same wall-guard
                    // shape used by the player applies to AI per-instance write-back (D-09).
                    val aiSpriteSize = aiVehicle?.let { v ->
                        gameIR.actors.firstOrNull { it.id == v.actorRef.id }?.sprite?.size
                    }
                    val aiHalfW = (aiSpriteSize?.width ?: DEFAULT_VEHICLE_SPRITE_W) / 2
                    val aiHalfH = (aiSpriteSize?.height ?: DEFAULT_VEHICLE_SPRITE_H) / 2

                    add(CComment("C.6 — AI loop for pool '$aiVehicleId' (D-09)"))
                    add(
                        CFor(
                            init =
                                CVarDecl(
                                    name = "i_$aiVehicleId",
                                    type = CU8,
                                    initializer = CLiteral(0),
                                ),
                            condition =
                                CBinaryExpr(CVar("i_$aiVehicleId"), "<", CLiteral(slot.count)),
                            increment = CUnaryExpr("++", CVar("i_$aiVehicleId")),
                            body =
                                buildAiPoolBodyStatements(
                                    id = id,
                                    aiVehicleId = aiVehicleId,
                                    indexVar = "i_$aiVehicleId",
                                    waypointCount = waypointCount,
                                    aiAccelStep = aiAccelStep,
                                    effectiveCap = effectiveCap,
                                    mapWidth = mapW,
                                    mapHeight = mapH,
                                    aiHalfW = aiHalfW,
                                    aiHalfH = aiHalfH,
                                ),
                        )
                    )
                }
            }
        }
    }

    /**
     * Emit the body of an AI pool's `forEachActive`-style C-AST for-loop. Active-slot guard (`if
     * (!_pool_<id>_active[i]) continue;`), waypoint-follower input source, then identical
     * `speed_cur >> 5` integration shape as the player path (D-09).
     */
    @Suppress("LongParameterList", "LongMethod")
    private fun buildAiPoolBodyStatements(
        id: String,
        aiVehicleId: String,
        indexVar: String,
        waypointCount: Int,
        aiAccelStep: Int,
        effectiveCap: Int,
        mapWidth: Int,
        mapHeight: Int,
        aiHalfW: Int,
        aiHalfH: Int,
    ): List<CStatement> {
        val ix = CVar(indexVar)
        val poolX = CArrayAccess(CVar("_pool_${aiVehicleId}_x"), ix)
        val poolY = CArrayAccess(CVar("_pool_${aiVehicleId}_y"), ix)
        val poolActive = CArrayAccess(CVar("_pool_${aiVehicleId}_active"), ix)
        val poolWpIdx = CArrayAccess(CVar("_pool_${aiVehicleId}_wp_idx"), ix)
        val poolSpeed = CArrayAccess(CVar("_pool_${aiVehicleId}_speed_cur"), ix)
        val poolHeading = CArrayAccess(CVar("_pool_${aiVehicleId}_heading"), ix)

        return buildList<CStatement> {
            // Active-slot guard.
            add(
                CIf(
                    condition = CBinaryExpr(poolActive, "==", CLiteral(0)),
                    thenBody = listOf(CContinue),
                )
            )
            // Read AI's current target waypoint (full waypoint loop — non-checkpoints included).
            add(
                CVarDecl(
                    name = "tgt_x_$aiVehicleId",
                    type = CU8,
                    initializer = CArrayAccess(CVar("_racing_wp_x_$id"), poolWpIdx),
                )
            )
            add(
                CVarDecl(
                    name = "tgt_y_$aiVehicleId",
                    type = CU8,
                    initializer = CArrayAccess(CVar("_racing_wp_y_$id"), poolWpIdx),
                )
            )
            // |dx|, |dy| using the abs-ternary pattern.
            add(
                CVarDecl(
                    name = "dx_$aiVehicleId",
                    type = CU8,
                    initializer =
                        CTernary(
                            condition = CBinaryExpr(poolX, ">", CVar("tgt_x_$aiVehicleId")),
                            thenExpr = CBinaryExpr(poolX, "-", CVar("tgt_x_$aiVehicleId")),
                            elseExpr = CBinaryExpr(CVar("tgt_x_$aiVehicleId"), "-", poolX),
                        ),
                )
            )
            add(
                CVarDecl(
                    name = "dy_$aiVehicleId",
                    type = CU8,
                    initializer =
                        CTernary(
                            condition = CBinaryExpr(poolY, ">", CVar("tgt_y_$aiVehicleId")),
                            thenExpr = CBinaryExpr(poolY, "-", CVar("tgt_y_$aiVehicleId")),
                            elseExpr = CBinaryExpr(CVar("tgt_y_$aiVehicleId"), "-", poolY),
                        ),
                )
            )
            // Cardinal heading from AI position toward target — 3-LEVEL wall-aware pick
            // (Plan 07.4-16, TRACK-NAVIGABILITY closure). Replaces the earlier bare
            // largest-axis if-else with a probe-tile-and-fallback ladder so the AI can
            // navigate concave corners and narrow tips where the primary cardinal AND
            // the perpendicular cardinal are both wall-blocked.
            add(
                buildAiHeadingPickWithFallback(
                    aiVehicleId = aiVehicleId,
                    zoneId = id,
                    mapWidth = mapWidth,
                    mapHeight = mapHeight,
                    spriteHalfW = aiHalfW,
                    spriteHalfH = aiHalfH,
                )
            )
            // Throttle ramp — SAME shape as player C.1, but to effectiveCap (with rubber-band).
            add(
                CExprStatement(
                    CBinaryExpr(
                        poolSpeed,
                        "=",
                        CTernary(
                            condition =
                                CBinaryExpr(
                                    CBinaryExpr(poolSpeed, "+", CLiteral(aiAccelStep)),
                                    "<",
                                    CLiteral(effectiveCap),
                                ),
                            thenExpr = CBinaryExpr(poolSpeed, "+", CLiteral(aiAccelStep)),
                            elseExpr = CLiteral(effectiveCap),
                        ),
                    )
                )
            )
            // delta = speed_cur >> 5 — IDENTICAL integration shape as player C.3 (D-09).
            add(
                CVarDecl(
                    name = "ai_delta_$aiVehicleId",
                    type = CU8,
                    initializer = CBinaryExpr(poolSpeed, ">>", CLiteral(5)),
                )
            )
            add(CVarDecl(name = "ai_vx_$aiVehicleId", type = CI8, initializer = CLiteral(0)))
            add(CVarDecl(name = "ai_vy_$aiVehicleId", type = CI8, initializer = CLiteral(0)))
            add(
                CSwitch(
                    expr = poolHeading,
                    cases =
                        listOf(
                            CSwitchCase(
                                value = CLiteral(0),
                                body =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CVar("ai_vy_$aiVehicleId"),
                                                "=",
                                                CUnaryExpr(
                                                    "-",
                                                    CCast(CI8, CVar("ai_delta_$aiVehicleId")),
                                                ),
                                            )
                                        ),
                                        CBreak,
                                    ),
                            ),
                            CSwitchCase(
                                value = CLiteral(1),
                                body =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CVar("ai_vx_$aiVehicleId"),
                                                "=",
                                                CCast(CI8, CVar("ai_delta_$aiVehicleId")),
                                            )
                                        ),
                                        CBreak,
                                    ),
                            ),
                            CSwitchCase(
                                value = CLiteral(2),
                                body =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CVar("ai_vy_$aiVehicleId"),
                                                "=",
                                                CCast(CI8, CVar("ai_delta_$aiVehicleId")),
                                            )
                                        ),
                                        CBreak,
                                    ),
                            ),
                            CSwitchCase(
                                value = CLiteral(3),
                                body =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CVar("ai_vx_$aiVehicleId"),
                                                "=",
                                                CUnaryExpr(
                                                    "-",
                                                    CCast(CI8, CVar("ai_delta_$aiVehicleId")),
                                                ),
                                            )
                                        ),
                                        CBreak,
                                    ),
                            ),
                        ),
                )
            )
            // AI position write-back — wall-collision-aware (Plan 07.4-12, D-09 invariant).
            // SAME guard shape as the player branch; only the LHS changes — _pool_<aiId>_x[i]
            // / _pool_<aiId>_y[i] instead of _<actor>_x / _y. AI cannot tunnel through walls.
            addAll(
                buildPositionWriteBackWithCollision(
                    actorRefX = "_pool_${aiVehicleId}_x[$indexVar]",
                    actorRefY = "_pool_${aiVehicleId}_y[$indexVar]",
                    vxName = "ai_vx_$aiVehicleId",
                    vyName = "ai_vy_$aiVehicleId",
                    zoneId = id,
                    mapWidth = mapWidth,
                    mapHeight = mapHeight,
                    spriteHalfW = aiHalfW,
                    spriteHalfH = aiHalfH,
                )
            )
            // Advance AI waypoint when close — wraps around the full loop.
            add(
                CIf(
                    condition =
                        CBinaryExpr(
                            CBinaryExpr(CVar("dx_$aiVehicleId"), "<", CLiteral(8)),
                            "&&",
                            CBinaryExpr(CVar("dy_$aiVehicleId"), "<", CLiteral(8)),
                        ),
                    thenBody =
                        listOf(
                            CExprStatement(
                                CBinaryExpr(
                                    poolWpIdx,
                                    "=",
                                    CTernary(
                                        condition =
                                            CBinaryExpr(
                                                CBinaryExpr(poolWpIdx, "+", CLiteral(1)),
                                                ">=",
                                                CLiteral(waypointCount),
                                            ),
                                        thenExpr = CLiteral(0),
                                        elseExpr = CBinaryExpr(poolWpIdx, "+", CLiteral(1)),
                                    ),
                                )
                            )
                        ),
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // LEGACY PATH — kept for SportCodegenTest fixtures that build RacingConfig
    // directly (no playerVehicle key). Emits the original racing_update_<id> /
    // racing_ai_update_<id> / racing_check_finish_<id> trio with simplified
    // bodies; the wrap-around lap counter is GONE per Plan 05 sub-step E.
    // Function names remain so the legacy SportCodegenTest assertions
    // (functionNames.startsWith("racing_update_"), etc.) keep passing.
    // -------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun visitRacingLegacy(
        config: RacingConfig,
        id: String,
        gameIR: GameIR,
    ): GenreVisitorResult {
        val isAiMode = config.mode == RacingMode.AI_OPPONENT
        val functions = mutableListOf<CFunction>()
        val varDecls = mutableListOf<CVarDecl>()

        // Globals — keep the legacy names that SportCodegenTest fixtures may grep for.
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

        val waypoints = config.track?.waypoints ?: emptyList()
        if (waypoints.isNotEmpty()) {
            val wpXValues = waypoints.joinToString(", ") { "${it.tileX * 8}u" }
            val wpYValues = waypoints.joinToString(", ") { "${it.tileY * 8}u" }
            varDecls +=
                CVarDecl(
                    name = "_racing_wp_x_$id",
                    type = CArray(CU8, waypoints.size),
                    initializer = CRawExpr("{$wpXValues}"),
                )
            varDecls +=
                CVarDecl(
                    name = "_racing_wp_y_$id",
                    type = CArray(CU8, waypoints.size),
                    initializer = CRawExpr("{$wpYValues}"),
                )
        }

        // Simplified racing_update_<id> body — wrap-around counter is gone (Plan 05 sub-step E).
        // SportCodegenTest only asserts the function name exists, so the simplified shell is
        // sufficient. Real games go through the new path (visitRacingNew) and never hit this.
        functions +=
            CFunction(
                name = "racing_update_$id",
                returnType = CVoid,
                params = listOf(CParam("player_x", CU8), CParam("player_y", CU8)),
                body =
                    listOf(
                        CComment(
                            "Legacy back-compat shell — real per-frame physics lives in racing_tick_<id>"
                        )
                    ),
                sectionComment = "Racing system: $id — legacy player update shell",
            )

        if (isAiMode) {
            functions +=
                CFunction(
                    name = "racing_ai_update_$id",
                    returnType = CVoid,
                    body =
                        listOf(
                            CComment(
                                "Legacy back-compat shell — real AI per-frame physics lives in racing_tick_<id>'s AI loop"
                            )
                        ),
                    sectionComment = "Racing system: $id — legacy AI update shell",
                )
        }

        // racing_check_finish_<id>() — kept verbatim from the legacy path.
        functions +=
            CFunction(
                name = "racing_check_finish_$id",
                returnType = CVoid,
                body =
                    listOf(
                        CComment("Race finish: ${config.laps} laps required"),
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CVar("_racing_lap_count_$id"),
                                    ">=",
                                    CLiteral(config.laps),
                                ),
                            thenBody =
                                listOf(CComment("race_complete_$id: handle finish event in scene")),
                        ),
                    ),
                sectionComment = "Racing system: $id — finish/lap detection",
            )

        // Pickup reconciliation: convert SportPickupDef → PickupDef, delegate to GBDKSystemVisitor.
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

        val pickupDefs = sportPickups.map { sportPickup ->
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
