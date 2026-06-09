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
import io.github.gbkt.backend.gbdk.codegen.ast.CCast
import io.github.gbkt.backend.gbdk.codegen.ast.CComment
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CIntLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CPointer
import io.github.gbkt.backend.gbdk.codegen.ast.CRawExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CReturn
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CU16
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.backend.gbdk.codegen.ast.CWhile
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.MovementStyle
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.genre.platformer.domain.CameraScrollMode
import io.github.gbkt.genre.platformer.domain.CollectibleDef
import io.github.gbkt.genre.platformer.domain.GoalZoneDef
import io.github.gbkt.genre.platformer.domain.HazardDef
import io.github.gbkt.genre.platformer.domain.LadderConfig
import io.github.gbkt.genre.platformer.domain.PlatformDef
import io.github.gbkt.genre.platformer.domain.PlatformType
import io.github.gbkt.genre.platformer.domain.PlatformerCameraConfig
import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig
import io.github.gbkt.genre.platformer.domain.ScrollDirection

// =============================================================================
// PLATFORMER VISITOR
//
// GenreSystemVisitor implementation for the platformer genre package.
// Registered via ServiceLoader (META-INF/services) so GBDKPipeline discovers
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
 * [io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline] discovers it at runtime without a
 * direct compile-time dependency on `gbkt-genre-platformer`.
 */
@Suppress("UNCHECKED_CAST")
class PlatformerVisitor : GenreSystemVisitor {

    private companion object {
        // Plan 12.7-19 — Round-5 H1 pivot_adjust fallback constants. Used when the
        // GameIR has no bound metasprite (typical for minimal test fixtures like
        // PlatformerPhysicsSnapToTileTopEmissionTest and the 3 sibling EmissionTests
        // that build a SystemIR-only GameIR without actors or metasprites). Locked to
        // the platformer-template's reference geometry so the visitor stays
        // back-compat with the Plan 12.7-11 emission for those fixtures AND closes
        // the user-visible 2-px overshoot at the platformer-template's
        // pivot(12, 6) + frameSize(24, 32) + hitbox(8, 24) configuration.
        //
        // For any other future caller declaring a different metasprite geometry, the
        // visitor reads the actual `frameHeight`/`pivotY` from
        // `gameIR.metasprites[i]` — these fallbacks are NEVER consumed.
        //
        // Derivation: see evidence/round-5-diagnostic.md Section 2 + the metasprite
        // declaration in
        // gbkt-examples/platformer-template/src/main/kotlin/io/github/gbkt/examples/
        //   platformer_template/PlatformerTemplate.kt:328-333.
        private const val REFERENCE_FRAME_HEIGHT = 32
        private const val REFERENCE_PIVOT_Y = 6
    }

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
            "platformer_physics" -> visitPhysics(systemConfig, gameIR)
            "platformer_camera" -> visitCamera(systemConfig, gameIR)
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
    private fun visitPhysics(config: Map<String, Any>, gameIR: GameIR): GenreVisitorResult {
        val physicsConfig =
            config["physicsConfig"] as? PlatformerPhysicsConfig ?: PlatformerPhysicsConfig()

        val functions =
            buildList<CFunction> {
                add(buildPhysicsUpdateFunction(physicsConfig, gameIR))
                if (physicsConfig.wallJump != null) {
                    add(buildWallJumpFunction(physicsConfig))
                }
            }

        val varDecls =
            buildList<CVarDecl> {
                add(CVarDecl(name = "_plat_vy", type = CI8, initializer = CLiteral(0)))
                add(CVarDecl(name = "_plat_vx", type = CI8, initializer = CLiteral(0)))
                add(CVarDecl(name = "_plat_grounded", type = CU8, initializer = CLiteral(0)))
                add(CVarDecl(name = "_plat_coyote_timer", type = CU8, initializer = CLiteral(0)))
                add(CVarDecl(name = "_plat_jump_buffer", type = CU8, initializer = CLiteral(0)))
                if (physicsConfig.wallJump != null) {
                    add(CVarDecl(name = "_plat_wall_slide", type = CU8, initializer = CLiteral(0)))
                    add(CVarDecl(name = "_plat_iframes", type = CU8, initializer = CLiteral(0)))
                }
                // Phase 12 D-14 — tilemap-physics jumpHold gravity-suppression timer (UINT8).
                // Gated on BOTH (a) cfg.jumpHoldMaxFrames > 0 (feature opted in) AND (b)
                // gameUsesTilemapCollision == true (tilemap-physics branch is the one that uses
                // the timer; the abstract path uses `variableHeightJump` semantics instead — see
                // Plan 12-05 SUMMARY §"decisions"). Lockstep gate keeps the global out of the
                // abstract path's WRAM footprint when the tilemap branch is not active.
                //
                // Initial value 0 — the jump-initiation site inside
                // `buildTilemapPhysicsUpdateFunction`
                // (Plan 12-11 §section 5) sets the timer to `cfg.jumpHoldMaxFrames` at the moment a
                // grounded jump fires; the new gravity-suppression branch decrements it each
                // airborne
                // frame and zeroes it on button release or timer expiry (Plan 12-13 §section 5b).
                if (physicsConfig.jumpHoldMaxFrames > 0 && gameUsesTilemapCollision(gameIR)) {
                    add(
                        CVarDecl(
                            name = "_jump_increase_timer",
                            type = CU8,
                            initializer = CLiteral(0),
                        )
                    )
                }
            }

        // Locate the gameplay scene to splice platformer_physics_update() into its frame block.
        // Discovery fallback chain (mirrors SportVisitor.visitRacingNew scene-discovery logic):
        //  1. Scene whose actorIds list contains a PHYSICS actor id (preferred, usually empty
        //     since the DSL does not populate actorIds on SceneIR directly).
        //  2. Scene navigated TO from the start scene — title → gameplay is the standard shape.
        //  3. The game's start scene.
        //  4. The first scene declared (last-resort for fixture-only IRs).
        val physicsActorIds =
            gameIR.actors
                .filter { it.movementConfig?.style == MovementStyle.PHYSICS }
                .map { it.id }
                .toSet()
        val gameplaySceneId: String? =
            gameIR.scenes
                .firstOrNull { scene -> scene.actorIds.any { id -> id in physicsActorIds } }
                ?.id
                ?: findFirstNavigateTarget(gameIR)
                ?: gameIR.startScene
                ?: gameIR.scenes.firstOrNull()?.id

        val frameOps: Map<String, List<ScriptOp>> =
            if (gameplaySceneId != null) {
                mapOf(gameplaySceneId to listOf(RawOp("platformer_physics_update();")))
            } else {
                emptyMap()
            }

        return GenreVisitorResult(functions = functions, varDecls = varDecls, frameOps = frameOps)
    }

    private fun buildPhysicsUpdateFunction(
        cfg: PlatformerPhysicsConfig,
        gameIR: GameIR,
    ): CFunction {
        // Phase 12 D-12b — when the game opts into tilemap-collision (gameUsesTilemapCollision),
        // route to a SEPARATE physics function body that performs sub-pixel integration + 5-point
        // AABB probes via `is_tile_solid()` (Plan 12-08 HOME-bank helper) + camera half-screen
        // trigger + level-end trigger. The existing abstract physics body (gravity / coyote /
        // jump-buffer / variable-height-jump / horizontal acceleration / friction) is UNCHANGED
        // for non-tilemap games — falls through to the buildList below. Plan 12-13 will extend
        // the tilemap branch with the jumpHold gravity-suppression block (D-14).
        if (gameUsesTilemapCollision(gameIR)) {
            return buildTilemapPhysicsUpdateFunction(cfg, gameIR)
        }

        val body =
            buildList<CStatement> {
                add(CComment("Apply gravity"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(CVar("_plat_vy"), "<", CIntLiteral(cfg.terminalVelocity)),
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
                                    CBinaryExpr(CVar("_plat_vy"), "<", CIntLiteral(0)),
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
                add(CBlankLine)
                add(CComment("Horizontal movement: LEFT/RIGHT d-pad acceleration"))
                add(
                    CIf(
                        condition = CCall("button_held", listOf(CVar("J_LEFT"))),
                        thenBody =
                            listOf(CExprStatement(CBinaryExpr(CVar("_plat_vx"), "-=", CLiteral(1)))),
                    )
                )
                add(
                    CIf(
                        condition = CCall("button_held", listOf(CVar("J_RIGHT"))),
                        thenBody =
                            listOf(CExprStatement(CBinaryExpr(CVar("_plat_vx"), "+=", CLiteral(1)))),
                    )
                )
                add(CBlankLine)
                add(
                    CComment(
                        "Horizontal friction: decelerate _plat_vx toward zero when no d-pad input"
                    )
                )
                // When LEFT not held and RIGHT not held and vx != 0: apply friction toward zero.
                // CIntLiteral(0) emits bare `0` (no u suffix) so the signed comparisons are
                // correct.
                add(
                    CIf(
                        condition = CVar("!button_held(J_LEFT) && !button_held(J_RIGHT)"),
                        thenBody =
                            listOf(
                                CIf(
                                    condition = CBinaryExpr(CVar("_plat_vx"), ">", CIntLiteral(0)),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CBinaryExpr(CVar("_plat_vx"), "-=", CLiteral(1))
                                            )
                                        ),
                                    elseBody =
                                        listOf(
                                            CIf(
                                                condition =
                                                    CBinaryExpr(
                                                        CVar("_plat_vx"),
                                                        "<",
                                                        CIntLiteral(0),
                                                    ),
                                                thenBody =
                                                    listOf(
                                                        CExprStatement(
                                                            CBinaryExpr(
                                                                CVar("_plat_vx"),
                                                                "+=",
                                                                CLiteral(1),
                                                            )
                                                        )
                                                    ),
                                            )
                                        ),
                                )
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

    // =========================================================================
    // TILEMAP-COLLISION PHYSICS BRANCH (Phase 12 D-12b)
    //
    // Emits `void platformer_physics_update(void)` with sub-pixel integration + 5-point AABB
    // probes via `is_tile_solid()` (Plan 12-08 HOME helper) + camera half-screen trigger +
    // level-end trigger. Mirror of `platformer_template/src/player.c` lines 213-355.
    //
    // Probe offsets (RESEARCH §"D-12b Recommendations" — 5-point probe table):
    //   right wall: (x + halfW, y + 2), (x + halfW, y + halfH), (x + halfW, y + height - 2)
    //   left wall:  (x - halfW, y + 2), (x - halfW, y + halfH), (x - halfW, y + height - 2)
    //   feet:       (x + (halfW-2), y + height), (x - (halfW-2), y + height)
    //   head:       (x + (halfW-2), y), (x - (halfW-2), y)
    //   stuck:      while (is_tile_solid(x, y + height - 1)) { _player_y -= 16u; ... }
    //
    // The probe offsets are auto-derived at CODEGEN time from the player actor's hitbox
    // (Phase 12 D-12b — no explicit probe params in the DSL). The result is constants embedded
    // as CIntLiteral / CLiteral inside the emitted C — zero runtime cost over a hand-tuned
    // probe loop.
    //
    // Signed-literal hygiene (RESEARCH §Pitfall 7): every comparison of `_player_vx` /
    // `_player_vy` (INT16) against zero uses CIntLiteral(0) — produces signed comparison in
    // SDCC. Using CLiteral(0) would emit `0u` and silently promote the LHS to unsigned, making
    // `_player_vy < 0u` always false (the bug class fixed by Phase 07.9).
    //
    // Plan 12-11 emits the jump-initiation timer assignment; Plan 12-13 extends this branch
    // with the gravity-suppression `if (cfg.jumpHoldMaxFrames > 0) { ... }` block (section 5b
    // of `buildTilemapPhysicsUpdateFunction`). The block decrements the timer each airborne
    // frame and applies gravity ONLY when the A/Up button is released OR the timer expires —
    // mirroring reference player.c lines 297-317. When cfg.jumpHoldMaxFrames == 0 the entire
    // branch is omitted, preserving the 12-11 baseline byte-identical.
    // =========================================================================

    /**
     * Phase 12 D-12b — emits the tilemap-collision physics branch.
     *
     * Auto-derives probe offsets from the FIRST actor in [gameIR] with a non-null hitbox (by
     * convention this is the player — gbkt does not currently model a `playerActor` flag, and the
     * platformer_template port's gameplay scene declares exactly one actor with a hitbox). When no
     * actor has a hitbox, defaults to the reference's `(0, 0, 8, 24)` so the function body still
     * compiles even in incomplete DSL drafts.
     *
     * The function lives in HOME bank (no `isBanked = true`) so that banked scene-frame callers
     * (bank1.c `gameplay_frame`) can call it cross-bank via the auto-extracted `game.h` prototype.
     */
    private fun buildTilemapPhysicsUpdateFunction(
        cfg: PlatformerPhysicsConfig,
        gameIR: GameIR,
    ): CFunction {
        // Phase 12 D-12b — locate the player actor's hitbox. Convention: the first actor with a
        // non-null hitbox is treated as the player (no `playerActor` flag in ActorIR today). The
        // reference's `hitbox(0, 0, 8, 24)` is the fallback when the IR is incomplete (mainly for
        // unit tests that build minimal GameIRs without an actor).
        val playerHitbox =
            gameIR.actors.firstNotNullOfOrNull { it.hitbox }
                ?: io.github.gbkt.core.ir.HitboxDef(x = 0, y = 0, width = 8, height = 24)
        // WR-02 fix: derive halfW with the reference's rounding (HALF_WIDTH = 5 for width 8).
        // Integer width/2 truncates 8→4, but the reference uses HALF_WIDTH=5 for all 3 probe
        // families. Setting halfW = width/2 + 1 = 5 makes:
        //   - horizontal probes (CLiteral(halfW)): emit 5u — G3-approved behavior PRESERVED
        //   - foot/head probes (CLiteral(halfWMinus2=halfW-2)): emit 3u — matches reference
        // The old approach patched only the horizontal probe with CLiteral(halfW + 1) at one
        // call site, leaving foot/head probes at halfWMinus2 = 4-2 = 2 (1px narrower than ref).
        val halfW = playerHitbox.width / 2 + 1
        val height = playerHitbox.height
        val halfH = height / 2
        val halfWMinus2 = halfW - 2
        val heightMinus2 = height - 2
        val heightMinus1 = height - 1

        // Velocity-to-fixed-point conversion: jumpForce is in pixels/frame; the sub-pixel velocity
        // multiplier of 100 mirrors the reference's PLAYER_CHARACTER_JUMP_VELOCITY constant.
        val jumpVelocity = cfg.jumpForce * 100

        // Camera half-screen trigger threshold — reference player.c uses DEVICE_SCREEN_PX_WIDTH/2
        // = 80 (Game Boy 160px / 2). Encoded as a literal because the reference treats it as a
        // compile-time constant; gbkt does not currently model device width.
        val halfScreenPx = 80

        // Level-end trigger threshold — reference player.c line 351 fires `next_level++` when
        // `playerRealX > current_level_width - 32u`. The 32px right margin matches the reference.
        val levelEndRightMargin = 32

        // Phase 12.1 Plan 06 — Defect 4 symbol resolution. The user-DSL declares
        // `var playerX by i16Var(...)`, `var playerY ...`, `var playerVx ...`, `var playerVy ...`,
        // `var grounded by u8Var(0)` and registers a `tilemap_collision` GenericSystem
        // (Plan 12.1-05 builder) that records the property names in its config map. The visitor
        // reads those names here and prefixes them with `_` to match the codegen convention
        // (AssignableVar.name → `_<name>` C global, see VariableBuilders.kt provideDelegate).
        //
        // Fallback shape: when no `tilemap_collision` system is registered (any caller that
        // opts into tilemap collision via Path A's `platformerPhysics { solidThreshold(N) }`
        // ONLY — e.g. the 4 existing genre-platformer EmissionTests), the resolution falls
        // back to the legacy `_player_x` / `_player_y` / `_player_vx` / `_player_vy` /
        // `_grounded` shape. This preserves byte-identical emission for the regression-guard
        // tests and any non-platformer-template caller per Plan 12.1-06 §must_haves.
        //
        // OUT OF SCOPE per checker W4 — hitbox override, solidThreshold override:
        //   - `playerHitbox` (above) continues to auto-derive from `gameIR.actors.firstNotNullOf
        //     OrNull { it.hitbox }`. The `tilemap_collision` system carries `hitbox` keys but
        //     this plan does NOT consume them — that's Phase 13 territory.
        //   - `cfg.solidThreshold` continues to be the only threshold source. The
        //     `tilemap_collision` system carries `solidThreshold` but it is not read here.
        //
        // OUT OF SCOPE per RESEARCH §Risks #1 — `_jump_increase_timer`:
        //   - That symbol is declared at line ~171 (rect-physics path WRAM globals) and reused
        //     here verbatim. Rewriting it would either break rect-physics callers (which
        //     declare the rect form) or force every platformer DSL to declare a matching
        //     `var jumpIncreaseTimer by u8Var(0)` — neither is desired. The symbol stays bare.
        val tcSystem =
            gameIR.systems.filterIsInstance<GenericSystem>().firstOrNull { sys ->
                (sys.config["type"] as? String) == "tilemap_collision"
            }
        val posXSym = "_" + ((tcSystem?.config?.get("posXVar") as? String) ?: "player_x")
        val posYSym = "_" + ((tcSystem?.config?.get("posYVar") as? String) ?: "player_y")
        val vxSym = "_" + ((tcSystem?.config?.get("vxVar") as? String) ?: "player_vx")
        val vySym = "_" + ((tcSystem?.config?.get("vyVar") as? String) ?: "player_vy")
        val groundedSym = "_" + ((tcSystem?.config?.get("groundedVar") as? String) ?: "grounded")

        // Round-5 H1 (Plan 12.7-19) — metasprite render-vs-hitbox-foot correction.
        // The hitbox foot (the snap target) and the rendered metasprite-bottom can DIFFER
        // when the metasprite's draw extent (frameHeight − pivotY) is taller than the
        // hitbox's vertical extent (hitbox.height). For the platformer-template under
        // SPRITES_8x16 + pivot(12, 6) + frameSize(24, 32) + hitbox(8, 24), the rendered
        // metasprite-bottom lands `32 − 6 − 24 = 2` px BELOW the hitbox foot. Without this
        // correction the rendered sprite overlays the top 2 px of the ground tile (user
        // anchor-2 UAT report 2026-05-26 — Plan 12.7-15 BLOCKED).
        //
        // Resolution: locate the player metasprite by matching its `posYVarName` against
        // the tilemap-collision system's `posYVar` binding (which the user-DSL sets via
        // `tilemapCollision { position(playerX, playerY) }` and the metasprite block sets
        // via `posY(playerY)` — both flow the SAME property-delegate name, so the strings
        // are equal). When the match succeeds AND the metasprite carries full geometry
        // (frameHeight + pivotY both non-null), compute pivot_adjust algebraically from
        // those values. When ANY field is absent (test fixtures without an actor or
        // metasprite, or a still-being-migrated DSL draft missing pivot/frameSize) fall
        // back to the platformer-template's reference geometry constants (32, 6) — the
        // SAME convention the playerHitbox fallback above uses (defaults to the
        // platformer-template's (0, 0, 8, 24)). This keeps minimal-IR test fixtures
        // (PlatformerPhysicsSnapToTileTopEmissionTest) producing the
        // platformer-template-shaped emission verbatim and preserves byte-identical
        // emission for the 4 existing genre-platformer EmissionTests under their
        // current minimal GameIR shape.
        //
        // No magic strings (feedback_no_magic_strings.md): the metasprite lookup matches
        // on the DSL property name flowing through both bindings — the visitor never
        // assumes a hardcoded "player" id. The fallback constants ARE locked to the
        // platformer-template's reference geometry verbatim and are kept as named
        // companion constants below for self-citation.
        //
        // TODO Phase 13+: lift `pivot_adjust` resolution into the `tilemapCollision { }`
        // builder so the user-DSL becomes the single source of truth (the metasprite
        // lookup dance disappears). Tracked as `SEED-PHASE-13-PIVOT-ADJUST-AUTO-DERIVE.md`.
        val tcPosYVar = (tcSystem?.config?.get("posYVar") as? String)
        val playerMetasprite =
            gameIR.metasprites.firstOrNull { ms ->
                tcPosYVar != null && ms.posYVarName == tcPosYVar
            }
                ?: gameIR.metasprites.firstOrNull { ms ->
                    // Fallback to the first metasprite with full geometry. Mirrors the
                    // playerHitbox fallback above ("first non-null hitbox = player by
                    // convention").
                    ms.frameHeight != null && ms.pivotY != null
                }
        val pivotAdjust: Int = run {
            val frameH = playerMetasprite?.frameHeight ?: REFERENCE_FRAME_HEIGHT
            val pivotY = playerMetasprite?.pivotY ?: REFERENCE_PIVOT_Y
            // Algebraic identity: frameHeight − pivotY − hitbox.height
            // — see evidence/round-5-diagnostic.md Section 2 for the derivation.
            // Clamped at >= 0 so a metasprite whose render extent fits INSIDE the
            // hitbox (no overshoot — render-bottom equals OR sits above hitbox foot)
            // contributes a zero correction, not a negative one. This matches the
            // semantic: pivot_adjust is "how many extra pixels does the rendered
            // sprite extend below the hitbox foot"; if the answer is ≤ 0, no
            // correction is required and the snap stays at the hitbox foot.
            (frameH - pivotY - height).coerceAtLeast(0)
        }

        // Phase 12.3 Plan 02 — platformer_input GenericSystem (Plan 12.3-01 substrate)
        // carries the walkSpeed/friction/airFriction tuning numbers. Defaults match the
        // PlatformerInputConfig data class defaults (D-01a: walkSpeed=128, friction=8,
        // airFriction=0). When the system is absent, the input emission block is gated
        // OFF by `gameUsesPlatformerInput(gameIR) == false` so these fallbacks are never
        // actually consumed — but kept here for shape symmetry with the `tcSystem` block
        // above (which uses the same "fallback default" idiom for non-tilemap-collision
        // call sites that still reach `buildTilemapPhysicsUpdateFunction` via the
        // `solidThreshold` gate).
        val piSystem =
            gameIR.systems.filterIsInstance<GenericSystem>().firstOrNull { sys ->
                (sys.config["type"] as? String) == "platformer_input"
            }
        val walkSpeed: Int = (piSystem?.config?.get("walkSpeed") as? Int) ?: 128
        val friction: Int = (piSystem?.config?.get("friction") as? Int) ?: 8
        val airFriction: Int = (piSystem?.config?.get("airFriction") as? Int) ?: 0

        // Phase 12.3 Plan 08 — walk-cycle binders (D-03 contract): the two AssignableVar
        // binders `walkFrameIdx` and `threeFrameCounter` live in the platformer_input
        // GenericSystem.config map as nullable Strings (the property-delegate names captured
        // by `PlatformerInputBuilder.walkFrameIdx(AssignableVar)` from Plan 12.3-01). When
        // EITHER binder is null (user did not call the binder method), the walk-cycle
        // emission below is SKIPPED entirely — no magic-string `_walkFrameIdx` fallback
        // (L-5.4 / feedback_no_magic_strings.md). When BOTH are set, we emit the cycle.
        // walkFrameCount + cyclePeriod fall back to the D-01a defaults (3 + 6) when absent.
        val walkFrameIdxVar: String? = piSystem?.config?.get("walkFrameIdxVar") as? String
        val threeFrameCounterVar: String? = piSystem?.config?.get("threeFrameCounterVar") as? String
        val walkFrameCount: Int = (piSystem?.config?.get("walkFrameCount") as? Int) ?: 3
        val cyclePeriod: Int = (piSystem?.config?.get("cyclePeriod") as? Int) ?: 6

        val body =
            buildList<CStatement> {
                // --- 0. Input → playerVx wiring (Phase 12.3 Plan 02 / gap #1, D-05 input-first)
                // ---
                // Reference player.c lines 218-235 — dpad held → set vx to ±walkSpeed; on release,
                // apply friction (ground vs air via _grounded) toward zero. Emitted BEFORE the
                // sub-pixel integration (D-05 emission order: input → physics_update body) so the
                // velocity set by input is what gets integrated this frame.
                //
                // Signed-literal hygiene (Phase 07.9 §"Literal Emission Convention"): every
                // `<vxSym> > 0` / `<vxSym> < 0` comparison RHS uses `CIntLiteral(0)` because
                // `vxSym` is signed (INT8 today; INT16 after the Plan 12.3-11 widening). Using
                // `CLiteral(0)` would emit `_playerVx > 0u` and SDCC's usual arithmetic conversion
                // (C11 §6.3.1.8) would promote `_playerVx` to unsigned — making the comparison
                // never fire for negative `_playerVx` (the very camera-never-advances bug class
                // codified after Phase 07.4). See Pitfall 1 in RESEARCH.md.
                //
                // Friction air vs ground (D-04): emit a UINT8 local `f = _<groundedSym> ?
                // friction : airFriction` once, then branch on the sign of `vxSym`. The
                // `-((INT16)f)` cast in the negative-velocity branch is required because `f` is
                // UINT8; without the INT16 cast the unary minus would wrap modulo-256.
                if (gameUsesPlatformerInput(gameIR)) {
                    add(CComment("Phase 12.3 — Input → playerVx (D-04 friction on release)"))
                    add(
                        CIf(
                            condition = CCall("button_held", listOf(CVar("J_RIGHT"))),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(CVar(vxSym), "=", CIntLiteral(walkSpeed))
                                    )
                                ),
                            elseBody =
                                listOf(
                                    CIf(
                                        condition = CCall("button_held", listOf(CVar("J_LEFT"))),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CVar(vxSym),
                                                        "=",
                                                        CIntLiteral(-walkSpeed),
                                                    )
                                                )
                                            ),
                                        elseBody =
                                            listOf(
                                                CVarDecl(
                                                    name = "f",
                                                    type = CU8,
                                                    initializer =
                                                        CRawExpr(
                                                            "$groundedSym ? ${friction}u : ${airFriction}u"
                                                        ),
                                                ),
                                                CIf(
                                                    condition =
                                                        CBinaryExpr(
                                                            CVar(vxSym),
                                                            ">",
                                                            CIntLiteral(0),
                                                        ),
                                                    thenBody =
                                                        listOf(
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CVar(vxSym),
                                                                    "=",
                                                                    CRawExpr(
                                                                        "($vxSym > f) ? ($vxSym - f) : 0"
                                                                    ),
                                                                )
                                                            )
                                                        ),
                                                    elseBody =
                                                        listOf(
                                                            CIf(
                                                                condition =
                                                                    CBinaryExpr(
                                                                        CVar(vxSym),
                                                                        "<",
                                                                        CIntLiteral(0),
                                                                    ),
                                                                thenBody =
                                                                    listOf(
                                                                        CExprStatement(
                                                                            CBinaryExpr(
                                                                                CVar(vxSym),
                                                                                "=",
                                                                                CRawExpr(
                                                                                    "($vxSym < -((INT16)f)) ? ($vxSym + f) : 0"
                                                                                ),
                                                                            )
                                                                        )
                                                                    ),
                                                            )
                                                        ),
                                                ),
                                            ),
                                    )
                                ),
                        )
                    )
                    add(CBlankLine)
                }

                // --- 0b. Walk-cycle emission (Phase 12.3 Plan 08 / gap #4) -------------------
                // When BOTH walkFrameIdx + threeFrameCounter AssignableVar binders are set in
                // the platformer_input GenericSystem config (Plan 12.3-01 substrate), emit the
                // 3-frame walk-cycle counter → frame-index advance. Reference player.c lines
                // ~240-250 (visual-frame advance from horizontal motion).
                //
                // D-03 SKIP-WHEN-UNSET contract (L-5.4 / feedback_no_magic_strings.md): if
                // EITHER binder is null, NO emission happens here. The visitor must NOT emit
                // a magic-string `_walkFrameIdx` reference — the contract is "names come from
                // the binder, or they don't appear at all".
                //
                // Position: AFTER section 0's input emission so `_<vxSym>` reflects this
                // frame's just-set velocity (motion-this-frame → frame-advance-this-frame),
                // and BEFORE section 1's sub-pixel position read (D-05 emission order:
                // input → animation → physics).
                //
                // Signed-literal hygiene (Phase 07.9 / L-5.1): `_<vxSym> != 0` MUST use
                // `CIntLiteral(0)` — `vxSym` is signed (INT8 today, INT16 after Plan
                // 12.3-11's widening). Using `CLiteral(0)` would emit `_playerVx != 0u` and
                // SDCC's usual arithmetic conversion (C11 §6.3.1.8) would promote
                // `_playerVx` to unsigned — the comparison would still work for `!=` (zero
                // compares equal regardless of sign-bit), but the file's broader convention
                // is to use `CIntLiteral` whenever the signed operand is `vxSym` so the
                // rule is uniform across this function (avoids drift).
                //
                // Counter/index literals (`>= cyclePeriod`, `>= walkFrameCount`, `= 0u`
                // resets, `++` increment targets) use `CLiteral` — counter and index are
                // UINT8, unsigned-context, `u` suffix is correct (Phase 07.9 default).
                //
                // Codegen idiom: inherited from Plan 12.3-02 (see 12.3-02 SUMMARY decisions[1]
                // and commit `1bd800e3` body) — typed C-AST `CIf` with `thenBody`/`elseBody`
                // nesting, `CUnaryExpr("++", ...)` for in-place increments (mirrors the
                // `--_jump_increase_timer` decrement pattern at section 5b), `CBinaryExpr`
                // for assignments + comparisons, `CIntLiteral(0)` for the signed `!= 0`
                // RHS, `CLiteral(N)` for unsigned counter/index RHS. NO CRawExpr needed
                // here — the cycle body has no cast-heavy expressions.
                if (
                    gameUsesPlatformerInput(gameIR) &&
                        walkFrameIdxVar != null &&
                        threeFrameCounterVar != null
                ) {
                    val wfiSym = "_$walkFrameIdxVar"
                    val tfcSym = "_$threeFrameCounterVar"
                    add(CComment("Phase 12.3 — Walk-cycle (3-frame counter → frame-index advance)"))
                    add(
                        CIf(
                            condition = CBinaryExpr(CVar(vxSym), "!=", CIntLiteral(0)),
                            thenBody =
                                listOf(
                                    CExprStatement(CUnaryExpr("++", CVar(tfcSym))),
                                    CIf(
                                        condition =
                                            CBinaryExpr(CVar(tfcSym), ">=", CLiteral(cyclePeriod)),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(CVar(tfcSym), "=", CLiteral(0))
                                                ),
                                                CExprStatement(CUnaryExpr("++", CVar(wfiSym))),
                                                CIf(
                                                    condition =
                                                        CBinaryExpr(
                                                            CVar(wfiSym),
                                                            ">=",
                                                            CLiteral(walkFrameCount),
                                                        ),
                                                    thenBody =
                                                        listOf(
                                                            CExprStatement(
                                                                CBinaryExpr(
                                                                    CVar(wfiSym),
                                                                    "=",
                                                                    CLiteral(0),
                                                                )
                                                            )
                                                        ),
                                                ),
                                            ),
                                    ),
                                ),
                            elseBody =
                                listOf(
                                    CExprStatement(CBinaryExpr(CVar(wfiSym), "=", CLiteral(0))),
                                    CExprStatement(CBinaryExpr(CVar(tfcSym), "=", CLiteral(0))),
                                ),
                        )
                    )
                    add(CBlankLine)
                }

                // --- 1. Player position read (sub-pixel >> 4 — mirrors player.c line 213) ----
                add(CComment("Sub-pixel position read (>> 4 = 1/16 pixel granularity)"))
                add(
                    CVarDecl(
                        name = "player_real_x",
                        type = CU16,
                        initializer = CBinaryExpr(CVar(posXSym), ">>", CLiteral(4)),
                    )
                )
                add(
                    CVarDecl(
                        name = "player_real_y",
                        type = CU16,
                        initializer = CBinaryExpr(CVar(posYSym), ">>", CLiteral(4)),
                    )
                )
                add(CBlankLine)

                // --- 2. Horizontal AABB probes (groups 1+2 from RESEARCH §D-12b table) -----
                // Phase 12.1 Plan 06 — the helper receives the resolved velocity symbol `vxSym`
                // so its emission tracks the same Defect-4 rewrite as the function body.
                add(CComment("Horizontal AABB probes — 3-point right wall + 3-point left wall"))
                add(
                    buildHorizontalProbe(
                        direction = "right",
                        halfW = halfW,
                        halfH = halfH,
                        heightMinus2 = heightMinus2,
                        vxSym = vxSym,
                    )
                )
                add(
                    buildHorizontalProbe(
                        direction = "left",
                        halfW = halfW,
                        halfH = halfH,
                        heightMinus2 = heightMinus2,
                        vxSym = vxSym,
                    )
                )
                add(CBlankLine)

                // --- 3. Vertical AABB probes (groups 3+4 from table) ----------------------
                // halfW-2 (NOT halfW) for foot/head probes — mirrors reference's
                // HALF_WIDTH-2 inset that prevents corner-snag (RESEARCH §D-12b note).
                // Phase 12.1 Plan 06 — foot probe receives `vySym` + `groundedSym`; head probe
                // receives `vySym`. These flow the Defect-4 rewrite through every probe.
                add(CComment("Vertical AABB probes — feet (falling) + head (rising)"))
                add(
                    buildVerticalFootProbe(
                        halfWMinus2 = halfWMinus2,
                        height = height,
                        pivotAdjust = pivotAdjust,
                        vySym = vySym,
                        groundedSym = groundedSym,
                        posYSym = posYSym,
                    )
                )
                add(buildVerticalHeadProbe(halfWMinus2 = halfWMinus2, vySym = vySym))
                add(CBlankLine)

                // --- 4. Stuck-in-ground resolve (group 5 — pre-move correction) ----------
                // Move player up 1 px (sub-pixel = 16) until no longer overlapping a solid tile.
                add(CComment("Stuck-in-ground resolve: pop up until feet clear of solid"))
                add(
                    CIf(
                        condition = CBinaryExpr(CVar(groundedSym), "==", CIntLiteral(0)),
                        thenBody =
                            listOf(
                                CWhile(
                                    condition =
                                        CCall(
                                            "is_tile_solid",
                                            listOf(
                                                CVar("player_real_x"),
                                                CBinaryExpr(
                                                    CVar("player_real_y"),
                                                    "+",
                                                    CLiteral(heightMinus1),
                                                ),
                                            ),
                                        ),
                                    body =
                                        listOf(
                                            CExprStatement(
                                                CBinaryExpr(CVar(posYSym), "-=", CLiteral(16))
                                            ),
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CVar("player_real_y"),
                                                    "=",
                                                    CBinaryExpr(CVar(posYSym), ">>", CLiteral(4)),
                                                )
                                            ),
                                        ),
                                )
                            ),
                    )
                )
                add(CBlankLine)

                // --- 5. Jump initiation -------------------------------------------------------
                // Sets `_jump_increase_timer = cfg.jumpHoldMaxFrames` at jump time. Plan 12-13's
                // section 5b below consumes that initial value: while airborne AND A/Up held AND
                // timer > 0, gravity is suppressed (the variable-height-jump feel). On button
                // release OR timer expiry, section 5b zeroes the timer and resumes gravity.
                add(CComment("Jump initiation: A or UP pressed AND grounded"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CCall("button_pressed", listOf(CVar("J_A"))),
                                "||",
                                CCall("button_pressed", listOf(CVar("J_UP"))),
                            ),
                        thenBody =
                            listOf(
                                CIf(
                                    condition =
                                        CBinaryExpr(CVar(groundedSym), "!=", CIntLiteral(0)),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CVar(vySym),
                                                    "=",
                                                    CLiteral(-jumpVelocity),
                                                )
                                            ),
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CVar("_jump_increase_timer"),
                                                    "=",
                                                    CLiteral(cfg.jumpHoldMaxFrames),
                                                )
                                            ),
                                            CExprStatement(
                                                CBinaryExpr(CVar(groundedSym), "=", CLiteral(0))
                                            ),
                                        ),
                                )
                            ),
                    )
                )
                add(CBlankLine)

                // --- 5b. Gravity gated by jumpHold timer (D-14 — Plan 12-13) -----------------
                // Reference player.c lines 297-317 — while airborne AND A/Up held AND
                // _jump_increase_timer > 0, SUPPRESS gravity (variable-height jump). On button
                // release OR timer expiry, gravity resumes and the timer is zeroed so a
                // subsequent re-press without a new jump cannot reopen the suppression window.
                //
                // Codegen-time gate: emit this entire block ONLY when cfg.jumpHoldMaxFrames > 0.
                // When 0, the 12-11 baseline is preserved byte-identical (the prompt's
                // regression-guard requirement) — the jump-initiation site still emits the
                // harmless `_jump_increase_timer = 0u;` assignment per Plan 12-11 §decision #4.
                //
                // Gravity scale: cfg.gravity * 16 — the sub-pixel velocity domain operates in
                // 1/16th-pixel units (see velocity integration in §6 below: `>> 4`). Multiplying
                // by 16 keeps cfg.gravity in user-facing pixel/frame² units while emitting
                // sub-pixel velocity increments. RESEARCH §D-14 documents the alternative of
                // emitting the reference's literal 45 verbatim; gbkt parameterises on cfg.gravity
                // so the user retains tuning control (Phase 12 lets the user opt out via
                // cfg.gravity != 2 if desired).
                //
                // Signed-literal hygiene (Phase 07.9 §"Literal Emission Convention"): the
                // `_grounded == 0` comparison uses CIntLiteral(0) to match the line 586 jump-
                // initiation site's discipline (UINT8 actually permits CLiteral, but matching
                // the local-scope convention keeps the tilemap-physics branch internally
                // consistent and avoids drift in scope-level grep gates). The
                // `_jump_increase_timer`
                // comparisons use CLiteral(0) — UINT8 is unsigned-context, so the unsigned `0u`
                // literal is correct (no Phase 07.9 hazard).
                if (cfg.jumpHoldMaxFrames > 0) {
                    add(
                        CComment(
                            "Phase 12 D-14 — gravity gated by jumpHold timer (suppress while A/Up held)"
                        )
                    )
                    add(
                        CIf(
                            condition = CBinaryExpr(CVar(groundedSym), "==", CIntLiteral(0)),
                            thenBody =
                                listOf(
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CVar("_jump_increase_timer"),
                                                ">",
                                                CLiteral(0),
                                            ),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CUnaryExpr("--", CVar("_jump_increase_timer"))
                                                )
                                            ),
                                    ),
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CRawExpr(
                                                    "!(button_held(J_A) || button_held(J_UP))"
                                                ),
                                                "||",
                                                CBinaryExpr(
                                                    CVar("_jump_increase_timer"),
                                                    "==",
                                                    CLiteral(0),
                                                ),
                                            ),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CVar(vySym),
                                                        "+=",
                                                        CLiteral(cfg.gravity * 16),
                                                    )
                                                ),
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CVar("_jump_increase_timer"),
                                                        "=",
                                                        CLiteral(0),
                                                    )
                                                ),
                                            ),
                                    ),
                                ),
                        )
                    )
                    add(CBlankLine)
                }

                // --- 6. Velocity integration --------------------------------------------------
                add(CComment("Sub-pixel velocity integration (>> 4 = scale fixed-point velocity)"))
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar(posXSym),
                            "+=",
                            CBinaryExpr(CVar(vxSym), ">>", CLiteral(4)),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar(posYSym),
                            "+=",
                            CBinaryExpr(CVar(vySym), ">>", CLiteral(4)),
                        )
                    )
                )
                add(CBlankLine)

                // --- 7. Camera half-screen trigger (mirrors player.c line 328) ---------------
                // When the player crosses the screen midpoint, the camera follows. `_camera_x`
                // is the UINT16 tilemap-camera global declared by Plan 12-10 Task 2 — only
                // emitted when gameUsesTilemapCollision == true, so the reference is sound.
                add(CComment("Camera half-screen trigger: follow player past screen midpoint"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(CVar("player_real_x"), ">=", CLiteral(halfScreenPx)),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("_camera_x"),
                                        "=",
                                        CBinaryExpr(
                                            CVar("player_real_x"),
                                            "-",
                                            CLiteral(halfScreenPx),
                                        ),
                                    )
                                )
                            ),
                    )
                )
                add(CBlankLine)

                // --- 8. Level-end trigger (mirrors player.c line 351) ------------------------
                // Plan 12-17 may switch to a `goalZone`-based trigger (D-claude-6); this plan
                // ships the explicit threshold form which is simpler and matches the reference.
                add(
                    CComment(
                        "Level-end trigger: increment _next_level when past the right margin (grounded-only — Round-6 H3 fix per Plan 12.7-26 verdict; player must be on the floor at trigger fire per SPEC R-03 wording)"
                    )
                )
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(
                                    CVar("player_real_x"),
                                    ">",
                                    CBinaryExpr(
                                        CVar("_current_level_width"),
                                        "-",
                                        CLiteral(levelEndRightMargin),
                                    ),
                                ),
                                "&&",
                                CBinaryExpr(CVar(groundedSym), "!=", CIntLiteral(0)),
                            ),
                        thenBody = listOf(CExprStatement(CUnaryExpr("++", CVar("_next_level")))),
                    )
                )
            }

        return CFunction(
            name = "platformer_physics_update",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment =
                "Platformer physics update (tilemap-collision branch — D-12b 5-point AABB probe)",
        )
    }

    /**
     * Emits a 3-point horizontal AABB probe for one direction ("right" or "left"). On any of the
     * three `is_tile_solid()` probes returning true, zeros the player horizontal velocity (symbol
     * passed via [vxSym] — Phase 12.1 Plan 06 Defect-4 rewrite) to block the move.
     *
     * Probes (per RESEARCH §"D-12b Recommendations" — groups 1 + 2):
     * - top edge: (x ± halfW, y + 2)
     * - mid edge: (x ± halfW, y + halfH)
     * - bot edge: (x ± halfW, y + height - 2)
     *
     * Velocity sign is guarded by a CIntLiteral(0) comparison (signed) — mirrors RESEARCH §Pitfall
     * 7 (signed-literal hygiene). Using CLiteral(0) here would emit `<vxSym> > 0u` and silently
     * promote the LHS to unsigned, making the right-wall block never fire when [vxSym]'s value is
     * negative.
     *
     * @param vxSym Resolved player-horizontal-velocity C symbol (Phase 12.1 Plan 06). When the
     *   `tilemap_collision` system is present, this is `_<vxVar>` from its config map; otherwise it
     *   falls back to the legacy `_player_vx` shape.
     */
    private fun buildHorizontalProbe(
        direction: String,
        halfW: Int,
        halfH: Int,
        heightMinus2: Int,
        vxSym: String,
    ): CIf {
        // Sign-aware velocity gate: right uses `> 0`, left uses `< 0`.
        val velocityOp = if (direction == "right") ">" else "<"
        // Sign-aware probe X offset: right uses `+ halfW`, left uses `- halfW`.
        val probeXOp = if (direction == "right") "+" else "-"

        fun probe(yOffset: Int): CCall =
            CCall(
                "is_tile_solid",
                listOf(
                    // WR-02: CLiteral(halfW) — halfW is now width/2+1 = 5 (root-cause fix at
                    // derivation site). The old CLiteral(halfW + 1) patch at this site is reverted
                    // because the +1 is now baked into the halfW base. Horizontal probes still
                    // emit 5u — G3-approved behavior is preserved.
                    CBinaryExpr(CVar("player_real_x"), probeXOp, CLiteral(halfW)),
                    CBinaryExpr(CVar("player_real_y"), "+", CLiteral(yOffset)),
                ),
            )

        val anyHit =
            CBinaryExpr(CBinaryExpr(probe(2), "||", probe(halfH)), "||", probe(heightMinus2))

        return CIf(
            condition = CBinaryExpr(CVar(vxSym), velocityOp, CIntLiteral(0)),
            thenBody =
                listOf(
                    CIf(
                        condition = anyHit,
                        thenBody =
                            listOf(CExprStatement(CBinaryExpr(CVar(vxSym), "=", CLiteral(0)))),
                    )
                ),
        )
    }

    /**
     * Emits the 2-point foot probe for falling. When the resolved vertical velocity ([vySym]) is `>
     * 0` AND either foot probe hits a solid tile, zeros the velocity and sets the resolved grounded
     * flag ([groundedSym]) to `TRUE` — Phase 12.1 Plan 06 Defect-4 rewrite.
     *
     * Foot probe x-offset is `halfW - 2` (NOT halfW) — RESEARCH §D-12b note: the corner-inset
     * prevents the foot probe from catching the wall when the player is hugging a wall vertically.
     * Without the -2 inset, the player would fall when the foot probe hits the wall under them
     * (which is wrong — that's the wall-collision case, already handled by the horizontal probe).
     *
     * @param vySym Resolved player-vertical-velocity C symbol (Plan 12.1 Plan 06). Legacy fallback:
     *   `_player_vy`.
     * @param groundedSym Resolved grounded-flag C symbol. Legacy fallback: `_grounded`.
     * @param posYSym Resolved player-y-position C symbol. Legacy fallback: `_player_y`. Written by
     *   the snap-to-tile-top step via intermediate CVarDecl locals (D-02 formula, precedence-immune
     *   emission per Plan 12.7-11; the math is identical to the D-02 formula's algebra, only the
     *   emission shape is split across intermediate vars to defeat C's `+`/`-` > `<<`/`>>`
     *   precedence trap that broke Plan 12.7-04 — see SEED for the CParenExpr AST surgery
     *   follow-up).
     * @param pivotAdjust The Round-5 H1 metasprite-vs-hitbox-foot correction in pixels. Per Plan
     *   12.7-17 Round-5 diagnostic Section 2: the rendered metasprite-bottom under SPRITES_8x16 +
     *   pivot(pivotX, pivotY) + frameSize(frameW, frameH) lands `frameHeight − pivotY −
     *   hitbox.height` pixels BELOW the hitbox-foot snap target. For the platformer-template's
     *   geometry (frameSize(24, 32), pivot(12, 6), hitbox 8×24): `pivot_adjust = 32 − 6 − 24 = 2`.
     *   Without this correction the rendered sprite overlays the top 2 px of the ground tile (user
     *   UAT 2026-05-26 anchor-2 report). Resolved by the caller from `gameIR.metasprites` matched
     *   against `posYSym` — see [buildTilemapPhysicsUpdateFunction] for the IR-driven derivation
     *   and the documented fallback. Plan 12.7-19 — Round-5 H1 fix; see
     *   evidence/round-5-diagnostic.md Section 2.
     *
     *   TODO Phase 13+: lift the resolution into the GenericSystem config layer once
     *   `tilemapCollision { ... }` learns to read the bound metasprite directly. Tracked as
     *   `SEED-PHASE-13-PIVOT-ADJUST-AUTO-DERIVE.md`. Today's resolution is at the visitor's call
     *   site (one level above), which is sufficient for Round-5 closure.
     */
    private fun buildVerticalFootProbe(
        halfWMinus2: Int,
        height: Int,
        pivotAdjust: Int,
        vySym: String,
        groundedSym: String,
        posYSym: String,
    ): CIf {
        fun probe(xOp: String): CCall =
            CCall(
                "is_tile_solid",
                listOf(
                    CBinaryExpr(CVar("player_real_x"), xOp, CLiteral(halfWMinus2)),
                    CBinaryExpr(CVar("player_real_y"), "+", CLiteral(height)),
                ),
            )

        val anyHit = CBinaryExpr(probe("+"), "||", probe("-"))

        return CIf(
            condition = CBinaryExpr(CVar(vySym), ">", CIntLiteral(0)),
            thenBody =
                listOf(
                    CIf(
                        condition = anyHit,
                        thenBody =
                            listOf(
                                CExprStatement(CBinaryExpr(CVar(vySym), "=", CLiteral(0))),
                                CExprStatement(CBinaryExpr(CVar(groundedSym), "=", CLiteral(1))),
                                CComment(
                                    "Snap to tile-top: precedence-immune via intermediate " +
                                        "CVarDecl locals (one binary-op class per line). Pins " +
                                        "RENDERED metasprite-bottom to underlying solid tile's " +
                                        "top edge. Plan 12.7-11 — Path A intermediate-vars " +
                                        "rewrite (CParenExpr AST surgery deferred to seed). " +
                                        "Plan 12.7-19 — Round-5 H1 fix adds `pivot_adjust` to " +
                                        "align RENDER vs HITBOX foot (under SPRITES_8x16 + " +
                                        "pivot + frameSize geometry the rendered metasprite-" +
                                        "bottom sits `frameHeight − pivotY − hitbox.height` " +
                                        "pixels below the hitbox foot — for the platformer-" +
                                        "template `32 − 6 − 24 = 2 px`); see " +
                                        "evidence/round-5-diagnostic.md Section 2."
                                ),
                                CVarDecl(
                                    name = "foot_tile_row",
                                    type = CU16,
                                    initializer =
                                        CBinaryExpr(
                                            CBinaryExpr(
                                                CVar("player_real_y"),
                                                "+",
                                                CLiteral(height),
                                            ),
                                            ">>",
                                            CLiteral(3),
                                        ),
                                ),
                                CVarDecl(
                                    name = "foot_pixel_top",
                                    type = CU16,
                                    initializer =
                                        CBinaryExpr(CVar("foot_tile_row"), "<<", CLiteral(3)),
                                ),
                                CVarDecl(
                                    name = "pivot_adjust",
                                    type = CU16,
                                    initializer = CLiteral(pivotAdjust),
                                ),
                                CVarDecl(
                                    name = "foot_pixel_anchor",
                                    type = CU16,
                                    initializer =
                                        CBinaryExpr(
                                            CBinaryExpr(
                                                CVar("foot_pixel_top"),
                                                "-",
                                                CLiteral(height),
                                            ),
                                            "-",
                                            CVar("pivot_adjust"),
                                        ),
                                ),
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar(posYSym),
                                        "=",
                                        CBinaryExpr(CVar("foot_pixel_anchor"), "<<", CLiteral(4)),
                                    )
                                ),
                                CComment(
                                    "Each line has at most one binary-op class " +
                                        "(the `foot_pixel_anchor` line nests two SAME-class `-` " +
                                        "ops — same-class chains are left-associative under C " +
                                        "and therefore precedence-immune). Round-trip verified " +
                                        "for platformer-template (frameSize 24×32, pivot 12,6, " +
                                        "hitbox 8×24, pivot_adjust=2): spawn_y=120, height=24 " +
                                        "→ foot_tile_row=18, foot_pixel_top=144, " +
                                        "pivot_adjust=2, foot_pixel_anchor=118, posYSym=1888 " +
                                        "→ player_real_y next frame = 118; hitbox foot at " +
                                        "118+24=142 (2 px ABOVE tile-row-18 top at 144); " +
                                        "rendered metasprite-bottom at 118-6+32=144 (lands on " +
                                        "tile-row-18 top — zero pixel gap). Grounded " +
                                        "equilibrium: player_real_y=102, hitbox foot=126, " +
                                        "rendered metasprite-bottom=128 (top of tile-row 16). " +
                                        "When pivot_adjust=0 (no metasprite bound, or render " +
                                        "geometry matches hitbox), the algebra reduces to the " +
                                        "Plan 12.7-11 hitbox-foot-snap shape — back-compat for " +
                                        "non-platformer-template callers."
                                ),
                            ),
                    )
                ),
        )
    }

    /**
     * Emits the 2-point head probe for rising. When the resolved vertical velocity ([vySym]) is `<
     * 0` AND either head probe hits a solid tile, zeros the velocity (head bonk — drops back to
     * gravity) — Phase 12.1 Plan 06 Defect-4 rewrite.
     *
     * Head probe x-offset is `halfW - 2` (NOT halfW) — same RESEARCH §D-12b corner-inset note as
     * the foot probe: prevents the head probe from triggering on side walls when the player is
     * hugging a wall.
     *
     * @param vySym Resolved player-vertical-velocity C symbol. Legacy fallback: `_player_vy`.
     */
    private fun buildVerticalHeadProbe(halfWMinus2: Int, vySym: String): CIf {
        fun probe(xOp: String): CCall =
            CCall(
                "is_tile_solid",
                listOf(
                    CBinaryExpr(CVar("player_real_x"), xOp, CLiteral(halfWMinus2)),
                    CVar("player_real_y"),
                ),
            )

        val anyHit = CBinaryExpr(probe("+"), "||", probe("-"))

        return CIf(
            condition = CBinaryExpr(CVar(vySym), "<", CIntLiteral(0)),
            thenBody =
                listOf(
                    CIf(
                        condition = anyHit,
                        thenBody =
                            listOf(CExprStatement(CBinaryExpr(CVar(vySym), "=", CLiteral(0)))),
                    )
                ),
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
                                CBinaryExpr(CVar("_plat_vy"), ">", CIntLiteral(0)),
                            ),
                        thenBody =
                            listOf(
                                CIf(
                                    condition =
                                        CBinaryExpr(
                                            CVar("_plat_vy"),
                                            ">",
                                            CIntLiteral(wallJump.wallSlideSpeed),
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
    private fun visitCamera(config: Map<String, Any>, gameIR: GameIR): GenreVisitorResult {
        val cameraConfig =
            config["cameraConfig"] as? PlatformerCameraConfig ?: PlatformerCameraConfig()

        val functions =
            buildList<CFunction> {
                add(buildCameraUpdateFunction(cameraConfig, gameIR))
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
                // Phase 12 D-13 — tilemap-camera mode globals (alongside existing _cam_x INT8 for
                // non-tilemap games). Gated on gameUsesTilemapCollision(gameIR): only emitted when
                // a zone has platformerPhysicsOverride[solidThreshold] or the platformer_physics
                // GenericSystem carries a non-null solidThreshold on its physicsConfig. The column-
                // scroll codegen (Plan 12-11) reads/writes these globals to drive cross-bank
                // set_bkg_submap calls via _bkg_set_level_submap_banked() (Plan 12-10 Task 1).
                //
                // UINT16 for _camera_x / _old_camera_x prevents the _cam_x INT8 overflow pitfall
                // documented in RESEARCH § Pitfall 3 — tilemap levels routinely exceed 256 px wide.
                // UINT8 for _map_pos_x / _old_map_pos_x is sufficient because they index into the
                // tilemap column (max 255 columns = 2040 px, well above any practical level).
                //
                // TODO(Phase 13): consolidate gameUsesTilemapCollision() with the identical
                // predicate in GBDKPipeline into a shared utility (e.g. TilemapCollisionGate)
                // once the cross-genre / cross-backend pattern stabilises.
                if (gameUsesTilemapCollision(gameIR)) {
                    add(CVarDecl(name = "_camera_x", type = CU16, initializer = CLiteral(0)))
                    add(CVarDecl(name = "_old_camera_x", type = CU16, initializer = CLiteral(0)))
                    add(CVarDecl(name = "_map_pos_x", type = CU8, initializer = CLiteral(0)))
                    add(CVarDecl(name = "_old_map_pos_x", type = CU8, initializer = CLiteral(0)))
                }
            }

        // Phase 12.3 Plan 04 (gap #2) — splice `platformer_camera_update()` call into the
        // gameplay scene's frame body so the function (already emitted by
        // buildTilemapCameraUpdateFunction) actually fires each frame.
        //
        // Gating: SAME triple-condition as the buildTilemapCameraUpdateFunction trigger at
        // line ~1406 (gameUsesTilemapCollision && HORIZONTAL && SMOOTH_FOLLOW). When the gate
        // fails (rect-physics game, vertical-only camera, or screen-lock mode), frameOps is
        // empty and no `platformer_camera_update();` line appears in any scene frame body
        // (back-compat for non-platformer-camera games — JumpHold / TilemapCollision /
        // HorizontalScroll / Defect4SymbolRewrite emission test fixtures stay byte-identical).
        //
        // gameplaySceneId discovery: mirrors visitPhysics's fallback chain (lines 186-197).
        // Locate the scene whose actorIds reference a PHYSICS actor; fall back to the first
        // navigate target, then startScene, then the first declared scene.
        //
        // L-5.6 (BANKED): the spliced `platformer_camera_update();` call rides inside the
        // scene-frame function which is BANKED (bank 1). The callee `platformer_camera_update()`
        // lives in HOME (main.c) per existing emission. The cross-bank call works because
        // `buildHeaderFile()` auto-extracts the prototype from `CFunction.toPrototype()`.
        val physicsActorIds =
            gameIR.actors
                .filter { it.movementConfig?.style == MovementStyle.PHYSICS }
                .map { it.id }
                .toSet()
        val gameplaySceneId: String? =
            gameIR.scenes
                .firstOrNull { scene -> scene.actorIds.any { id -> id in physicsActorIds } }
                ?.id
                ?: findFirstNavigateTarget(gameIR)
                ?: gameIR.startScene
                ?: gameIR.scenes.firstOrNull()?.id

        val frameOps: Map<String, List<ScriptOp>> =
            if (
                gameplaySceneId != null &&
                    gameUsesTilemapCollision(gameIR) &&
                    cameraConfig.scrollDirections == ScrollDirection.HORIZONTAL &&
                    cameraConfig.mode == CameraScrollMode.SMOOTH_FOLLOW
            ) {
                mapOf(gameplaySceneId to listOf(RawOp("platformer_camera_update();")))
            } else {
                emptyMap()
            }

        return GenreVisitorResult(functions = functions, varDecls = varDecls, frameOps = frameOps)
    }

    /**
     * Phase 12 D-13 — predicate that gates the tilemap-camera mode WRAM globals.
     *
     * Returns `true` when the game opts into tilemap-collision physics — detected via either:
     * - **Path A:** a `platformer_physics` `GenericSystem` whose `physicsConfig` is a
     *   `PlatformerPhysicsConfig` with a non-null `solidThreshold`.
     * - **Path B:** any `ZoneIR` in `gameIR.zones` whose `platformerPhysicsOverride` map contains a
     *   `"solidThreshold"` key.
     *
     * Mirrors `GBDKPipeline.gameUsesTilemapCollision(gameIR)` exactly (Plan 12-08). Duplicated here
     * because `gbkt-genre-platformer` has direct compile-time access to
     * `PlatformerPhysicsConfig.solidThreshold` (no reflection needed), but the backend predicate
     * uses reflection because `gbkt-backend-gbdk` does NOT depend on the platformer genre module.
     * The two predicates MUST stay in lockstep — see the consolidation TODO in `visitCamera`.
     */
    private fun gameUsesTilemapCollision(gameIR: GameIR): Boolean {
        // Path A — platformer_physics GenericSystem with non-null solidThreshold on physicsConfig
        val systemHasThreshold =
            gameIR.systems.filterIsInstance<GenericSystem>().any { sys ->
                (sys.config["type"] as? String) == "platformer_physics" &&
                    (sys.config["physicsConfig"] as? PlatformerPhysicsConfig)?.solidThreshold !=
                        null
            }
        if (systemHasThreshold) return true

        // Path B — per-zone platformerPhysicsOverride with solidThreshold key
        return gameIR.zones.any { zone ->
            zone.platformerPhysicsOverride?.containsKey("solidThreshold") == true
        }
    }

    /**
     * Phase 12.3 Plan 02 — predicate that gates auto-emission of the dpad → playerVx input wiring +
     * friction-on-release branch (gap #1) and the walk-cycle emission (gap #4, Plan 12.3-08) inside
     * [buildTilemapPhysicsUpdateFunction].
     *
     * Whether `platformer_input` config (game-level or per-zone override) is present in [gameIR].
     * Mirrors [gameUsesTilemapCollision] structure (Plan 12.3-02):
     * - **Path A:** a `platformer_input` `GenericSystem` is registered (via
     *   `GameBuilder.platformerInput { ... }` extension — Plan 12.3-01 substrate).
     * - **Path B:** any `ZoneIR` in `gameIR.zones` has a non-null `platformerInputOverride` payload
     *   (per-zone numeric shadow via `ZoneBuilder.platformerInput { ... }`).
     *
     * Returns `false` for back-compat: non-platformer-input games (existing JumpHold /
     * TilemapCollision / HorizontalScroll / Defect4SymbolRewrite emission test fixtures) leave
     * `platformer_physics_update` byte-identical relative to the Plan 12.1-06 baseline.
     */
    private fun gameUsesPlatformerInput(gameIR: GameIR): Boolean {
        val systemPresent =
            gameIR.systems.filterIsInstance<GenericSystem>().any { sys ->
                (sys.config["type"] as? String) == "platformer_input"
            }
        if (systemPresent) return true
        return gameIR.zones.any { zone -> zone.platformerInputOverride != null }
    }

    private fun buildCameraUpdateFunction(cfg: PlatformerCameraConfig, gameIR: GameIR): CFunction {
        // Phase 12 D-13 — when the game opts into tilemap-collision AND uses horizontal smooth-
        // follow camera, route to a SEPARATE camera body that performs column-by-column tilemap
        // scrolling via `_bkg_set_level_submap_banked()` (Plan 12-10 HOME helper). The existing
        // abstract smooth-follow / screen-lock paths are UNCHANGED for non-tilemap games — falls
        // through to the `when` below.
        //
        // Gating per RESEARCH §"D-13 Recommendations" — Section "Gating":
        //   1. gameUsesTilemapCollision(gameIR) == true   (solidThreshold set)
        //   2. cfg.scrollDirections == HORIZONTAL          (column-scroll is x-axis only)
        //   3. cfg.mode == SMOOTH_FOLLOW                   (screen-lock has no per-frame scroll)
        //
        // NOTE: `cfg.scrollDirections` is a single `ScrollDirection` enum (NOT a Set). The plan
        // prose said `.contains(HORIZONTAL)` — that does not type-check. Using `==` is the only
        // form that compiles against the current domain model. Tracked as a Rule 1 plan-prose
        // bug fix in the SUMMARY.
        if (
            gameUsesTilemapCollision(gameIR) &&
                cfg.scrollDirections == ScrollDirection.HORIZONTAL &&
                cfg.mode == CameraScrollMode.SMOOTH_FOLLOW
        ) {
            return buildTilemapCameraUpdateFunction(cfg, gameIR)
        }

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

    // =========================================================================
    // TILEMAP-CAMERA COLUMN-SCROLL BRANCH (Phase 12 D-13)
    //
    // Emits `void platformer_camera_update(void)` with column-by-column horizontal scroll
    // (mirror of `platformer_template/src/camera.c` lines 56-83). Function declaration starts
    // with `void platformer_camera_update` at column 0 — anchor for the Plan 12-12 emission
    // invariant test (D-16 #3, mirror of Plan 12-09 awk brace-walk pattern for is_tile_solid).
    //
    // Body contract (RESEARCH §"D-13 Recommendations — Generated column-scroll C shape"):
    //   move_bkg(_camera_x, 0u);
    //   _map_pos_x = (UINT8)(_camera_x >> 3u);
    //   if (_map_pos_x != _old_map_pos_x) {
    //       if (_camera_x < _old_camera_x) {
    //           _bkg_set_level_submap_banked(_map_pos_x + 1u, 0u, 1u, DEVICE_SCREEN_HEIGHT);
    //       } else if ((_current_level_width_in_tiles - DEVICE_SCREEN_WIDTH) > _map_pos_x) {
    //           _bkg_set_level_submap_banked(_map_pos_x + DEVICE_SCREEN_WIDTH, 0u, 1u,
    // DEVICE_SCREEN_HEIGHT);
    //       }
    //       _old_map_pos_x = _map_pos_x;
    //   }
    //   _old_camera_x = _camera_x;
    //
    // The four supporting WRAM globals (_camera_x, _old_camera_x, _map_pos_x, _old_map_pos_x)
    // are declared by Plan 12-10 Task 2 inside this same visitCamera method — they emit only
    // when gameUsesTilemapCollision == true, so the cross-bank call into the HOME helper
    // _bkg_set_level_submap_banked() (Plan 12-10 Task 1) is always well-formed.
    // =========================================================================

    /**
     * Phase 12 D-13 — emits the tilemap-camera column-scroll branch.
     *
     * The function lives in HOME bank (no `isBanked = true`) so the bank1 scene-frame caller can
     * cross-bank call without trampolines. Plan 12-12 (next wave) will lock the function-scope
     * emission shape via per-function awk brace-walk JVM test.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun buildTilemapCameraUpdateFunction(
        cfg: PlatformerCameraConfig,
        gameIR: GameIR,
    ): CFunction {
        // GBDK device-screen constants — emitted as raw symbols. SDCC resolves them via
        // <gb/gb.h>. Using CVar (not CLiteral) keeps the generated C calling the canonical
        // names from gb.h instead of hard-coding 160 / 144 / 20 / 18 — matches the reference
        // platformer_template/src/camera.c usage exactly.

        val body =
            buildList<CStatement> {
                add(CComment("Apply background scroll to GPU (horizontal-only tilemap camera)"))
                add(CExprStatement(CCall("move_bkg", listOf(CVar("_camera_x"), CLiteral(0)))))
                add(CBlankLine)
                add(CComment("Compute current tile column (camera_x / 8 → tilemap column index)"))
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("_map_pos_x"),
                            "=",
                            CCast(CU8, CBinaryExpr(CVar("_camera_x"), ">>", CLiteral(3))),
                        )
                    )
                )
                add(CBlankLine)
                add(
                    CComment(
                        "Column-scroll trigger: only redraw a column when the tile column changes"
                    )
                )
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_map_pos_x"), "!=", CVar("_old_map_pos_x")),
                        thenBody =
                            buildList {
                                add(CComment("Scrolling left: redraw the new LEFT-edge column"))
                                add(
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CVar("_camera_x"),
                                                "<",
                                                CVar("_old_camera_x"),
                                            ),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CCall(
                                                        "_bkg_set_level_submap_banked",
                                                        listOf(
                                                            CBinaryExpr(
                                                                CVar("_map_pos_x"),
                                                                "+",
                                                                CLiteral(1),
                                                            ),
                                                            CLiteral(0),
                                                            CLiteral(1),
                                                            CVar("DEVICE_SCREEN_HEIGHT"),
                                                        ),
                                                    )
                                                )
                                            ),
                                        elseBody =
                                            listOf(
                                                CComment(
                                                    "Scrolling right: redraw the new RIGHT-edge column (bounded by level width)"
                                                ),
                                                CIf(
                                                    condition =
                                                        CBinaryExpr(
                                                            CBinaryExpr(
                                                                CVar(
                                                                    "_current_level_width_in_tiles"
                                                                ),
                                                                "-",
                                                                CVar("DEVICE_SCREEN_WIDTH"),
                                                            ),
                                                            ">",
                                                            CVar("_map_pos_x"),
                                                        ),
                                                    thenBody =
                                                        listOf(
                                                            CExprStatement(
                                                                CCall(
                                                                    "_bkg_set_level_submap_banked",
                                                                    listOf(
                                                                        CBinaryExpr(
                                                                            CVar("_map_pos_x"),
                                                                            "+",
                                                                            CVar(
                                                                                "DEVICE_SCREEN_WIDTH"
                                                                            ),
                                                                        ),
                                                                        CLiteral(0),
                                                                        CLiteral(1),
                                                                        CVar("DEVICE_SCREEN_HEIGHT"),
                                                                    ),
                                                                )
                                                            )
                                                        ),
                                                ),
                                            ),
                                    )
                                )
                                add(CBlankLine)
                                add(CComment("Latch new column index for next frame's delta check"))
                                add(
                                    CExprStatement(
                                        CBinaryExpr(CVar("_old_map_pos_x"), "=", CVar("_map_pos_x"))
                                    )
                                )
                            },
                    )
                )
                add(CBlankLine)
                add(CComment("Latch current camera_x for next frame's direction-of-scroll check"))
                add(CExprStatement(CBinaryExpr(CVar("_old_camera_x"), "=", CVar("_camera_x"))))
            }

        return CFunction(
            name = "platformer_camera_update",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment =
                "Platformer camera update (tilemap-collision branch — D-13 column-scroll)",
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
                        condition = CBinaryExpr(CVar("_cam_target_$axis"), ">", CVar("_cam_$axis")),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("_cam_$axis"),
                                        "=",
                                        CBinaryExpr(
                                            CVar("_cam_target_$axis"),
                                            "-",
                                            CLiteral(deadZone),
                                        ),
                                    )
                                )
                            ),
                        elseBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("_cam_$axis"),
                                        "=",
                                        CBinaryExpr(
                                            CVar("_cam_target_$axis"),
                                            "+",
                                            CLiteral(deadZone),
                                        ),
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
                                            CBinaryExpr(
                                                CUnaryExpr("*", CVar("player_x")),
                                                "+",
                                                CLiteral(4),
                                            ),
                                            ">>",
                                            CLiteral(3),
                                        ),
                                        CBinaryExpr(
                                            CBinaryExpr(
                                                CUnaryExpr("*", CVar("player_y")),
                                                "+",
                                                CLiteral(4),
                                            ),
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
                        params =
                            listOf(
                                CParam("player_x", CPointer(CU8)),
                                CParam("player_y", CPointer(CU8)),
                            ),
                        body = body,
                        sectionComment = "Platformer ladder climb update",
                    )
                )
        )
    }

    // =========================================================================
    // SCENE DISCOVERY HELPERS
    //
    // Mirror of SportVisitor.findFirstNavigateTarget / findFirstNavigateInOps.
    // Used by visitPhysics to find the gameplay scene for physics_update() injection
    // when actorIds is empty (the common case — DSL does not populate actorIds).
    // =========================================================================

    /**
     * Walks the start scene's frame and enter ops looking for a [NavigateTo] that targets a
     * different scene. Returns the first such target's id, or null if the start scene never
     * navigates elsewhere.
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
                else -> {}
            }
        }
        return null
    }
}
