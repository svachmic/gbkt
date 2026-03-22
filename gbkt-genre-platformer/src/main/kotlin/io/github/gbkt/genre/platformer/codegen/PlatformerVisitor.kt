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

package io.github.gbkt.genre.platformer.codegen

import io.github.gbkt.backend.api.GenreSystemVisitor
import io.github.gbkt.backend.api.GenreVisitorResult
import io.github.gbkt.backend.api.sanitizeCId
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CBlankLine
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CComment
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CPointer
import io.github.gbkt.backend.gbdk.codegen.ast.CReturn
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.genre.platformer.domain.CameraScrollMode
import io.github.gbkt.genre.platformer.domain.CollectibleDef
import io.github.gbkt.genre.platformer.domain.GoalZoneDef
import io.github.gbkt.genre.platformer.domain.HazardDef
import io.github.gbkt.genre.platformer.domain.LadderConfig
import io.github.gbkt.genre.platformer.domain.PlatformDef
import io.github.gbkt.genre.platformer.domain.PlatformType
import io.github.gbkt.genre.platformer.domain.PlatformerCameraConfig
import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig

// =============================================================================
// PLATFORMER VISITOR
//
// GenreSystemVisitor implementation for the platformer genre package.
// Registered via ServiceLoader (META-INF/services) so GBDKPipelineV2 discovers
// it automatically without a hard compile-time dependency.
//
// Handled system types:
//  - "platformer_physics"    → platformer_physics_update(), WRAM state vars
//  - "platformer_camera"     → platformer_camera_update(), camera position vars
//  - "platformer_hazard"     → check_hazard_collision_{id}()
//  - "platformer_platform"   → check_platform_collision_{id}()
//  - "platformer_goal"       → check_goal_zone_{id}()
//  - "platformer_collectible"→ delegates to shared pickup codegen
//  - "platformer_ladder"     → platformer_ladder_update()
// =============================================================================

/**
 * Generates C code for platformer genre systems.
 *
 * Implements [GenreSystemVisitor] and is registered via ServiceLoader so that
 * [io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipelineV2] discovers it at runtime without a
 * direct compile-time dependency on `gbkt-genre-platformer`.
 */
@Suppress("UNCHECKED_CAST")
class PlatformerVisitor : GenreSystemVisitor {

    private val handledTypes =
        setOf(
            "platformer_physics",
            "platformer_camera",
            "platformer_hazard",
            "platformer_platform",
            "platformer_goal",
            "platformer_collectible",
            "platformer_ladder",
        )

    override fun canHandle(systemType: String): Boolean = systemType in handledTypes

    override fun visit(
        systemType: String,
        systemConfig: Map<String, Any>,
        gameIR: GameIR,
    ): GenreVisitorResult {
        return when (systemType) {
            "platformer_physics" -> visitPhysics(systemConfig)
            "platformer_camera" -> visitCamera(systemConfig)
            "platformer_hazard" -> visitHazard(systemConfig)
            "platformer_platform" -> visitPlatform(systemConfig)
            "platformer_goal" -> visitGoalZone(systemConfig)
            "platformer_collectible" -> visitCollectible(systemConfig)
            "platformer_ladder" -> visitLadder(systemConfig)
            else -> GenreVisitorResult()
        }
    }

    // =========================================================================
    // PHYSICS
    // =========================================================================

    /**
     * Generates [platformer_physics_update] and state variable declarations.
     *
     * Emitted functions:
     * - `platformer_physics_update()` — gravity, jump input, coyote time, jump buffer,
     *   variable-height jump cut, air control, wall-slide, wall-jump
     *
     * Emitted globals (WRAM state):
     * - `_plat_vy` (INT8) — vertical velocity
     * - `_plat_grounded` (UINT8) — 1 when on ground
     * - `_plat_coyote_timer` (UINT8) — coyote time countdown
     * - `_plat_jump_buffer` (UINT8) — jump buffer countdown
     * - `_plat_wall_slide` (UINT8) — 1 when sliding a wall
     * - `_plat_iframes` (UINT8) — invincibility frames after wall-jump
     */
    private fun visitPhysics(config: Map<String, Any>): GenreVisitorResult {
        val physicsConfig =
            config["physicsConfig"] as? PlatformerPhysicsConfig ?: PlatformerPhysicsConfig()

        val functions =
            buildList<CFunction> {
                add(buildPhysicsUpdateFunction(physicsConfig))
                if (physicsConfig.wallJump != null) {
                    add(buildWallJumpFunction(physicsConfig))
                }
            }

        val varDecls =
            buildList<CVarDecl> {
                add(CVarDecl(name = "_plat_vy", type = CI8, initializer = CLiteral(0)))
                add(CVarDecl(name = "_plat_grounded", type = CU8, initializer = CLiteral(0)))
                add(CVarDecl(name = "_plat_coyote_timer", type = CU8, initializer = CLiteral(0)))
                add(CVarDecl(name = "_plat_jump_buffer", type = CU8, initializer = CLiteral(0)))
                if (physicsConfig.wallJump != null) {
                    add(CVarDecl(name = "_plat_wall_slide", type = CU8, initializer = CLiteral(0)))
                    add(CVarDecl(name = "_plat_iframes", type = CU8, initializer = CLiteral(0)))
                }
            }

        return GenreVisitorResult(functions = functions, varDecls = varDecls)
    }

    private fun buildPhysicsUpdateFunction(cfg: PlatformerPhysicsConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Apply gravity"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(CVar("_plat_vy"), "<", CLiteral(cfg.terminalVelocity)),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(CVar("_plat_vy"), "+=", CLiteral(cfg.gravity))
                                )
                            ),
                    )
                )
                add(CBlankLine)
                add(CComment("Decrement coyote timer"))
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_plat_coyote_timer"), ">", CLiteral(0)),
                        thenBody =
                            listOf(CExprStatement(CUnaryExpr("--", CVar("_plat_coyote_timer")))),
                    )
                )
                add(CBlankLine)
                add(CComment("Decrement jump buffer"))
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_plat_jump_buffer"), ">", CLiteral(0)),
                        thenBody =
                            listOf(CExprStatement(CUnaryExpr("--", CVar("_plat_jump_buffer")))),
                    )
                )
                add(CBlankLine)
                add(CComment("Jump input: set coyote or buffer"))
                add(
                    CIf(
                        condition = CCall("button_pressed", listOf(CVar("J_A"))),
                        thenBody =
                            buildList {
                                add(
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CVar("_plat_grounded"),
                                                "||",
                                                CBinaryExpr(
                                                    CVar("_plat_coyote_timer"),
                                                    ">",
                                                    CLiteral(0),
                                                ),
                                            ),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CVar("_plat_vy"),
                                                        "=",
                                                        CLiteral(-cfg.jumpForce),
                                                    )
                                                ),
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CVar("_plat_grounded"),
                                                        "=",
                                                        CLiteral(0),
                                                    )
                                                ),
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CVar("_plat_coyote_timer"),
                                                        "=",
                                                        CLiteral(0),
                                                    )
                                                ),
                                            ),
                                        elseBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CVar("_plat_jump_buffer"),
                                                        "=",
                                                        CLiteral(cfg.jumpBufferFrames),
                                                    )
                                                )
                                            ),
                                    )
                                )
                            },
                    )
                )
                if (cfg.variableHeightJump) {
                    add(CBlankLine)
                    add(CComment("Variable-height jump: cut velocity when button released early"))
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CCall("button_released", listOf(CVar("J_A"))),
                                    "&&",
                                    CBinaryExpr(CVar("_plat_vy"), "<", CLiteral(0)),
                                ),
                            thenBody =
                                listOf(
                                    CExprStatement(CBinaryExpr(CVar("_plat_vy"), "/=", CLiteral(2)))
                                ),
                        )
                    )
                }
                add(CBlankLine)
                add(CComment("Set coyote time when on ground, let it expire when airborne"))
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_plat_grounded"), "!=", CLiteral(0)),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("_plat_coyote_timer"),
                                        "=",
                                        CLiteral(cfg.coyoteFrames),
                                    )
                                )
                            ),
                    )
                )
                add(CBlankLine)
                add(CComment("Apply jump buffer on landing"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(CVar("_plat_grounded"), "==", CLiteral(1)),
                                "&&",
                                CBinaryExpr(CVar("_plat_jump_buffer"), ">", CLiteral(0)),
                            ),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(CVar("_plat_vy"), "=", CLiteral(-cfg.jumpForce))
                                ),
                                CExprStatement(
                                    CBinaryExpr(CVar("_plat_grounded"), "=", CLiteral(0))
                                ),
                                CExprStatement(
                                    CBinaryExpr(CVar("_plat_jump_buffer"), "=", CLiteral(0))
                                ),
                            ),
                    )
                )
            }

        return CFunction(
            name = "platformer_physics_update",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment = "Platformer physics update",
        )
    }

    private fun buildWallJumpFunction(cfg: PlatformerPhysicsConfig): CFunction {
        val wallJump = cfg.wallJump!!
        val body =
            buildList<CStatement> {
                add(CComment("Decrement invincibility frames"))
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_plat_iframes"), ">", CLiteral(0)),
                        thenBody = listOf(CExprStatement(CUnaryExpr("--", CVar("_plat_iframes")))),
                    )
                )
                add(CBlankLine)
                add(CComment("Wall-slide: slow fall when pressed against wall"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(CVar("_plat_wall_slide"), "==", CLiteral(1)),
                                "&&",
                                CBinaryExpr(CVar("_plat_vy"), ">", CLiteral(0)),
                            ),
                        thenBody =
                            listOf(
                                CIf(
                                    condition =
                                        CBinaryExpr(
                                            CVar("_plat_vy"),
                                            ">",
                                            CLiteral(wallJump.wallSlideSpeed),
                                        ),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CVar("_plat_vy"),
                                                    "=",
                                                    CLiteral(wallJump.wallSlideSpeed),
                                                )
                                            )
                                        ),
                                )
                            ),
                    )
                )
                add(CBlankLine)
                add(CComment("Wall-jump: jump away from wall with iFrames"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(CVar("_plat_wall_slide"), "==", CLiteral(1)),
                                "&&",
                                CCall("button_pressed", listOf(CVar("J_A"))),
                            ),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(CVar("_plat_vy"), "=", CLiteral(-cfg.jumpForce))
                                ),
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("_plat_iframes"),
                                        "=",
                                        CLiteral(wallJump.iFrameDuration),
                                    )
                                ),
                                CExprStatement(
                                    CBinaryExpr(CVar("_plat_wall_slide"), "=", CLiteral(0))
                                ),
                            ),
                    )
                )
            }

        return CFunction(
            name = "platformer_wall_jump_update",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment = "Platformer wall-jump / wall-slide update",
        )
    }

    // =========================================================================
    // CAMERA
    // =========================================================================

    /**
     * Generates camera update function and camera position variable declarations.
     *
     * Emitted functions:
     * - `platformer_camera_update()` — smooth follow with dead zone OR screen-lock transition
     * - `platformer_parallax_scroll()` — update parallax layer positions (if layers configured)
     *
     * Emitted globals:
     * - `_cam_x` (INT8) — camera horizontal position
     * - `_cam_y` (INT8) — camera vertical position
     * - `_cam_target_x` (INT8) — target horizontal position for smooth follow
     * - `_cam_target_y` (INT8) — target vertical position for smooth follow
     * - `_parallax_offset_{i}` (INT8) per layer — parallax scroll position
     */
    private fun visitCamera(config: Map<String, Any>): GenreVisitorResult {
        val cameraConfig =
            config["cameraConfig"] as? PlatformerCameraConfig ?: PlatformerCameraConfig()

        val functions =
            buildList<CFunction> {
                add(buildCameraUpdateFunction(cameraConfig))
                if (cameraConfig.parallaxLayers.isNotEmpty()) {
                    add(buildParallaxScrollFunction(cameraConfig))
                }
            }

        val varDecls =
            buildList<CVarDecl> {
                add(CVarDecl(name = "_cam_x", type = CI8, initializer = CLiteral(0)))
                add(CVarDecl(name = "_cam_y", type = CI8, initializer = CLiteral(0)))
                add(CVarDecl(name = "_cam_target_x", type = CI8, initializer = CLiteral(0)))
                add(CVarDecl(name = "_cam_target_y", type = CI8, initializer = CLiteral(0)))
                cameraConfig.parallaxLayers.forEachIndexed { idx, _ ->
                    add(
                        CVarDecl(
                            name = "_parallax_offset_$idx",
                            type = CI8,
                            initializer = CLiteral(0),
                        )
                    )
                }
            }

        return GenreVisitorResult(functions = functions, varDecls = varDecls)
    }

    private fun buildCameraUpdateFunction(cfg: PlatformerCameraConfig): CFunction {
        val body =
            when (cfg.mode) {
                CameraScrollMode.SMOOTH_FOLLOW -> buildSmoothFollowBody(cfg)
                CameraScrollMode.SCREEN_LOCK -> buildScreenLockBody(cfg)
            }

        return CFunction(
            name = "platformer_camera_update",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment = "Platformer camera update (${cfg.mode.name.lowercase()})",
        )
    }

    private fun buildSmoothFollowBody(cfg: PlatformerCameraConfig): List<CStatement> = buildList {
        add(CComment("Smooth-follow camera with dead zone (${cfg.deadZoneX}x${cfg.deadZoneY})"))
        add(CBlankLine)
        add(CComment("Horizontal dead-zone check"))
        add(buildDeadZoneCheck(axis = "x", deadZone = cfg.deadZoneX))
        add(CBlankLine)
        add(CComment("Vertical dead-zone check"))
        add(buildDeadZoneCheck(axis = "y", deadZone = cfg.deadZoneY))
        add(CBlankLine)
        add(CComment("Apply camera scroll"))
        add(CExprStatement(CCall("move_bkg", listOf(CVar("_cam_x"), CVar("_cam_y")))))
    }

    /**
     * Generates the dead-zone check for a single camera axis.
     *
     * If the distance between target and current position exceeds [deadZone], the camera snaps to
     * the edge of the dead zone in the direction of the target.
     */
    private fun buildDeadZoneCheck(axis: String, deadZone: Int): CIf =
        CIf(
            condition =
                CBinaryExpr(
                    CCall(
                        "abs",
                        listOf(CBinaryExpr(CVar("_cam_target_$axis"), "-", CVar("_cam_$axis"))),
                    ),
                    ">",
                    CLiteral(deadZone),
                ),
            thenBody =
                listOf(
                    CIf(
                        condition =
                            CBinaryExpr(CVar("_cam_target_$axis"), ">", CVar("_cam_$axis")),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("_cam_$axis"),
                                        "=",
                                        CBinaryExpr(CVar("_cam_target_$axis"), "-", CLiteral(deadZone)),
                                    )
                                )
                            ),
                        elseBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("_cam_$axis"),
                                        "=",
                                        CBinaryExpr(CVar("_cam_target_$axis"), "+", CLiteral(deadZone)),
                                    )
                                )
                            ),
                    )
                ),
        )

    private fun buildScreenLockBody(cfg: PlatformerCameraConfig): List<CStatement> = buildList {
        add(CComment("Screen-lock camera: snap to 160x144 screen boundaries"))
        add(CBlankLine)
        add(CComment("Snap horizontal position to screen grid"))
        add(
            CExprStatement(
                CBinaryExpr(
                    CVar("_cam_x"),
                    "=",
                    CBinaryExpr(
                        CBinaryExpr(CVar("_cam_target_x"), "/", CLiteral(160)),
                        "*",
                        CLiteral(160),
                    ),
                )
            )
        )
        add(CComment("Snap vertical position to screen grid"))
        add(
            CExprStatement(
                CBinaryExpr(
                    CVar("_cam_y"),
                    "=",
                    CBinaryExpr(
                        CBinaryExpr(CVar("_cam_target_y"), "/", CLiteral(144)),
                        "*",
                        CLiteral(144),
                    ),
                )
            )
        )
        add(CBlankLine)
        add(CComment("Apply screen-lock scroll"))
        add(CExprStatement(CCall("move_bkg", listOf(CVar("_cam_x"), CVar("_cam_y")))))
    }

    private fun buildParallaxScrollFunction(cfg: PlatformerCameraConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Update ${cfg.parallaxLayers.size} parallax layer(s)"))
                cfg.parallaxLayers.forEachIndexed { idx, layer ->
                    add(CBlankLine)
                    add(CComment("Layer $idx: asset=${layer.assetId} speed=${layer.scrollSpeedX}%"))
                    add(
                        CExprStatement(
                            CBinaryExpr(
                                CVar("_parallax_offset_$idx"),
                                "=",
                                CBinaryExpr(
                                    CBinaryExpr(CVar("_cam_x"), "*", CLiteral(layer.scrollSpeedX)),
                                    "/",
                                    CLiteral(100),
                                ),
                            )
                        )
                    )
                }
            }

        return CFunction(
            name = "platformer_parallax_scroll",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment = "Platformer parallax scroll update",
        )
    }

    // =========================================================================
    // HAZARD
    // =========================================================================

    /**
     * Generates [check_hazard_collision_{id}] function.
     *
     * The generated function iterates hazard tiles in the current tilemap and triggers damage or
     * instant-death callback when the player AABB overlaps the hazard tile.
     */
    private fun visitHazard(config: Map<String, Any>): GenreVisitorResult {
        val hazard = config["hazard"] as? HazardDef ?: return GenreVisitorResult()
        val sanitizedId = sanitizeCId(hazard.id)

        val body =
            buildList<CStatement> {
                add(
                    CComment("Check collision with hazard tile ${hazard.tileId} (id: ${hazard.id})")
                )
                add(
                    CVarDecl(
                        name = "tile",
                        type = CU8,
                        initializer =
                            CCall(
                                function = "get_bkg_tile_xy",
                                args =
                                    listOf(
                                        CBinaryExpr(
                                            CBinaryExpr(CVar("player_x"), "+", CLiteral(4)),
                                            ">>",
                                            CLiteral(3),
                                        ),
                                        CBinaryExpr(
                                            CBinaryExpr(CVar("player_y"), "+", CLiteral(8)),
                                            ">>",
                                            CLiteral(3),
                                        ),
                                    ),
                            ),
                    )
                )
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("tile"), "==", CLiteral(hazard.tileId)),
                        thenBody =
                            if (hazard.instant) {
                                listOf(
                                    CComment("Instant kill hazard"),
                                    CExprStatement(CCall("on_player_death")),
                                )
                            } else {
                                listOf(
                                    CComment("Damage hazard: -${hazard.damage} HP"),
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("_player_hp"),
                                            "-=",
                                            CLiteral(hazard.damage),
                                        )
                                    ),
                                    CIf(
                                        condition =
                                            CBinaryExpr(CVar("_player_hp"), "<=", CLiteral(0)),
                                        thenBody = listOf(CExprStatement(CCall("on_player_death"))),
                                    ),
                                )
                            },
                    )
                )
            }

        return GenreVisitorResult(
            functions =
                listOf(
                    CFunction(
                        name = "check_hazard_collision_$sanitizedId",
                        returnType = CVoid,
                        params = listOf(CParam("player_x", CU8), CParam("player_y", CU8)),
                        body = body,
                        sectionComment = "Platformer hazard collision: ${hazard.id}",
                    )
                )
        )
    }

    // =========================================================================
    // PLATFORM COLLISION
    // =========================================================================

    /**
     * Generates [check_platform_collision_{id}] function.
     *
     * ONE_WAY platforms include a directional check (player must be falling from above). CRUMBLING
     * platforms also emit crumble-delay state variables.
     */
    private fun visitPlatform(config: Map<String, Any>): GenreVisitorResult {
        val platform = config["platform"] as? PlatformDef ?: return GenreVisitorResult()
        val sanitizedId = sanitizeCId(platform.id)

        val body =
            buildList<CStatement> {
                add(CComment("Platform collision: ${platform.id} (type=${platform.type})"))
                when (platform.type) {
                    PlatformType.ONE_WAY -> {
                        add(CComment("One-way: only block when player is falling from above"))
                        add(
                            CIf(
                                condition = CBinaryExpr(CVar("_plat_vy"), ">", CLiteral(0)),
                                thenBody =
                                    listOf(
                                        CComment("Player falling — resolve solid collision"),
                                        CExprStatement(
                                            CBinaryExpr(CVar("_plat_vy"), "=", CLiteral(0))
                                        ),
                                        CExprStatement(
                                            CBinaryExpr(CVar("_plat_grounded"), "=", CLiteral(1))
                                        ),
                                    ),
                            )
                        )
                    }
                    PlatformType.CRUMBLING -> {
                        add(CComment("Crumbling: start crumble timer on first contact"))
                        add(
                            CIf(
                                condition =
                                    CBinaryExpr(
                                        CVar("_crumble_timer_$sanitizedId"),
                                        "==",
                                        CLiteral(0),
                                    ),
                                thenBody =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CVar("_crumble_timer_$sanitizedId"),
                                                "=",
                                                CLiteral(platform.crumbleDelay),
                                            )
                                        )
                                    ),
                            )
                        )
                        add(
                            CIf(
                                condition =
                                    CBinaryExpr(
                                        CVar("_crumble_timer_$sanitizedId"),
                                        ">",
                                        CLiteral(0),
                                    ),
                                thenBody =
                                    listOf(
                                        CExprStatement(
                                            CUnaryExpr("--", CVar("_crumble_timer_$sanitizedId"))
                                        )
                                    ),
                            )
                        )
                    }
                    else -> {
                        add(CComment("Solid platform: block all directions"))
                        add(CExprStatement(CBinaryExpr(CVar("_plat_vy"), "=", CLiteral(0))))
                        add(CExprStatement(CBinaryExpr(CVar("_plat_grounded"), "=", CLiteral(1))))
                    }
                }
            }

        val varDecls =
            buildList<CVarDecl> {
                if (platform.type == PlatformType.CRUMBLING) {
                    add(
                        CVarDecl(
                            name = "_crumble_timer_$sanitizedId",
                            type = CU8,
                            initializer = CLiteral(0),
                        )
                    )
                }
            }

        return GenreVisitorResult(
            functions =
                listOf(
                    CFunction(
                        name = "check_platform_collision_$sanitizedId",
                        returnType = CVoid,
                        params = listOf(CParam("player_x", CU8), CParam("player_y", CU8)),
                        body = body,
                        sectionComment = "Platformer platform collision: ${platform.id}",
                    )
                ),
            varDecls = varDecls,
        )
    }

    // =========================================================================
    // GOAL ZONE
    // =========================================================================

    /**
     * Generates [check_goal_zone_{id}] function.
     *
     * The generated function performs an AABB check between the player and the goal zone rectangle
     * and calls `on_goal_reached()` when they overlap.
     */
    private fun visitGoalZone(config: Map<String, Any>): GenreVisitorResult {
        val goal = config["goalZone"] as? GoalZoneDef ?: return GenreVisitorResult()
        val sanitizedId = sanitizeCId(goal.id)

        val body =
            buildList<CStatement> {
                add(
                    CComment(
                        "AABB check: player vs goal zone ${goal.id} (${goal.x},${goal.y} ${goal.width}x${goal.height})"
                    )
                )
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(
                                    CBinaryExpr(CVar("player_x"), ">=", CLiteral(goal.x)),
                                    "&&",
                                    CBinaryExpr(
                                        CVar("player_x"),
                                        "<",
                                        CLiteral(goal.x + goal.width),
                                    ),
                                ),
                                "&&",
                                CBinaryExpr(
                                    CBinaryExpr(CVar("player_y"), ">=", CLiteral(goal.y)),
                                    "&&",
                                    CBinaryExpr(
                                        CVar("player_y"),
                                        "<",
                                        CLiteral(goal.y + goal.height),
                                    ),
                                ),
                            ),
                        thenBody = listOf(CExprStatement(CCall("on_goal_reached"))),
                    )
                )
            }

        return GenreVisitorResult(
            functions =
                listOf(
                    CFunction(
                        name = "on_goal_reached",
                        returnType = CVoid,
                        params = emptyList(),
                        body =
                            listOf(
                                CComment("Goal reached — game handles navigation in scene frame")
                            ),
                        sectionComment = "Platformer goal reached callback",
                    ),
                    CFunction(
                        name = "check_goal_zone_$sanitizedId",
                        returnType = CVoid,
                        params = listOf(CParam("player_x", CU8), CParam("player_y", CU8)),
                        body = body,
                        sectionComment = "Platformer goal zone check: ${goal.id}",
                    ),
                )
        )
    }

    // =========================================================================
    // COLLECTIBLE (delegates to shared pickup system)
    // =========================================================================

    /**
     * Converts [CollectibleDef] to pickup system codegen.
     *
     * The platformer collectible is a facade over the engine's shared pickup system. This method
     * generates a pickup_collect_{id} function that handles the specific collectible type with
     * appropriate score/resource callbacks.
     */
    private fun visitCollectible(config: Map<String, Any>): GenreVisitorResult {
        val collectible = config["collectible"] as? CollectibleDef ?: return GenreVisitorResult()
        val sanitizedId = sanitizeCId(collectible.id)

        val body =
            buildList<CStatement> {
                add(
                    CComment(
                        "Collectible pickup: ${collectible.id} (type=${collectible.type}, value=${collectible.value})"
                    )
                )
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(
                                    CBinaryExpr(CVar("player_x"), ">=", CVar("pickup_x")),
                                    "&&",
                                    CBinaryExpr(
                                        CVar("player_x"),
                                        "<",
                                        CBinaryExpr(CVar("pickup_x"), "+", CLiteral(8)),
                                    ),
                                ),
                                "&&",
                                CBinaryExpr(
                                    CBinaryExpr(CVar("player_y"), ">=", CVar("pickup_y")),
                                    "&&",
                                    CBinaryExpr(
                                        CVar("player_y"),
                                        "<",
                                        CBinaryExpr(CVar("pickup_y"), "+", CLiteral(8)),
                                    ),
                                ),
                            ),
                        thenBody =
                            buildList {
                                add(CComment("Award ${collectible.type.name.lowercase()} value"))
                                add(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("_score"),
                                            "+=",
                                            CLiteral(collectible.value),
                                        )
                                    )
                                )
                                add(CExprStatement(CCall("on_collectible_collected_$sanitizedId")))
                                add(CReturn())
                            },
                    )
                )
            }

        return GenreVisitorResult(
            functions =
                listOf(
                    CFunction(
                        name = "pickup_collect_$sanitizedId",
                        returnType = CVoid,
                        params =
                            listOf(
                                CParam("player_x", CU8),
                                CParam("player_y", CU8),
                                CParam("pickup_x", CU8),
                                CParam("pickup_y", CU8),
                            ),
                        body = body,
                        sectionComment = "Platformer collectible: ${collectible.id}",
                    )
                )
        )
    }

    // =========================================================================
    // LADDER
    // =========================================================================

    /**
     * Generates [platformer_ladder_update] function.
     *
     * The generated function checks if the player overlaps a ladder tile and applies vertical
     * movement based on directional input.
     */
    private fun visitLadder(config: Map<String, Any>): GenreVisitorResult {
        val ladder = config["ladderConfig"] as? LadderConfig ?: return GenreVisitorResult()

        val body =
            buildList<CStatement> {
                add(CComment("Check if player is on ladder tile ${ladder.tileId}"))
                add(
                    CVarDecl(
                        name = "tile",
                        type = CU8,
                        initializer =
                            CCall(
                                function = "get_bkg_tile_xy",
                                args =
                                    listOf(
                                        CBinaryExpr(
                                            CBinaryExpr(CUnaryExpr("*", CVar("player_x")), "+", CLiteral(4)),
                                            ">>",
                                            CLiteral(3),
                                        ),
                                        CBinaryExpr(
                                            CBinaryExpr(CUnaryExpr("*", CVar("player_y")), "+", CLiteral(4)),
                                            ">>",
                                            CLiteral(3),
                                        ),
                                    ),
                            ),
                    )
                )
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("tile"), "==", CLiteral(ladder.tileId)),
                        thenBody =
                            buildList {
                                add(
                                    CComment(
                                        "On ladder: cancel gravity and apply climb speed ${ladder.climbSpeed}"
                                    )
                                )
                                add(CExprStatement(CBinaryExpr(CVar("_plat_vy"), "=", CLiteral(0))))
                                add(
                                    CIf(
                                        condition = CCall("input_up_held"),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CUnaryExpr("*", CVar("player_y")),
                                                        "-=",
                                                        CLiteral(ladder.climbSpeed),
                                                    )
                                                )
                                            ),
                                    )
                                )
                                add(
                                    CIf(
                                        condition = CCall("input_down_held"),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CUnaryExpr("*", CVar("player_y")),
                                                        "+=",
                                                        CLiteral(ladder.climbSpeed),
                                                    )
                                                )
                                            ),
                                    )
                                )
                            },
                    )
                )
            }

        return GenreVisitorResult(
            functions =
                listOf(
                    CFunction(
                        name = "platformer_ladder_update",
                        returnType = CVoid,
                        params = listOf(CParam("player_x", CPointer(CU8)), CParam("player_y", CPointer(CU8))),
                        body = body,
                        sectionComment = "Platformer ladder climb update",
                    )
                )
        )
    }
}
