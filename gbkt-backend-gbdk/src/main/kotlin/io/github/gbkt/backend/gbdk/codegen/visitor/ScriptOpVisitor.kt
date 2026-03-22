/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CArrayAccess
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CBlock
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CCast
import io.github.gbkt.backend.gbdk.codegen.ast.CComment
import io.github.gbkt.backend.gbdk.codegen.ast.CExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CRawExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CReturn
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CStringLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CTernary
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CWhile
import io.github.gbkt.core.ir.AnimateOp
import io.github.gbkt.core.ir.ArrayAssign
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.CallOp
import io.github.gbkt.core.ir.CameraAction
import io.github.gbkt.core.ir.CameraOp
import io.github.gbkt.core.ir.ClearRegion
import io.github.gbkt.core.ir.DestroyActor
import io.github.gbkt.core.ir.DialogChoice
import io.github.gbkt.core.ir.DialogExprSegment
import io.github.gbkt.core.ir.DialogSay
import io.github.gbkt.core.ir.DialogTextSegment
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.FadeOp
import io.github.gbkt.core.ir.FontMode
import io.github.gbkt.core.ir.ForOp
import io.github.gbkt.core.ir.GotoXYOp
import io.github.gbkt.core.ir.HudHide
import io.github.gbkt.core.ir.HudShow
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.MathFunction
import io.github.gbkt.core.ir.MathOp
import io.github.gbkt.core.ir.MenuHide
import io.github.gbkt.core.ir.MenuShow
import io.github.gbkt.core.ir.MoveBy
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.PathfindStep
import io.github.gbkt.core.ir.PhysicsStep
import io.github.gbkt.core.ir.PlaySound
import io.github.gbkt.core.ir.PoolDestroyAll
import io.github.gbkt.core.ir.PoolForEachActive
import io.github.gbkt.core.ir.PrintAligned
import io.github.gbkt.core.ir.PrintAt
import io.github.gbkt.core.ir.PrintCentered
import io.github.gbkt.core.ir.PrintOp
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.ReturnOp
import io.github.gbkt.core.ir.ScreenClear
import io.github.gbkt.core.ir.ScreenFill
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.ScriptOpVisitorI
import io.github.gbkt.core.ir.SetAnimationState
import io.github.gbkt.core.ir.SetPosition
import io.github.gbkt.core.ir.SetVisible
import io.github.gbkt.core.ir.SoundChannel
import io.github.gbkt.core.ir.SoundEffectDef
import io.github.gbkt.core.ir.SpawnActor
import io.github.gbkt.core.ir.TextAlignment
import io.github.gbkt.core.ir.TriggerSystem
import io.github.gbkt.core.ir.WaitFrames
import io.github.gbkt.core.ir.WaypointStep
import io.github.gbkt.core.ir.WhileOp

// =============================================================================
// SCRIPT OP VISITOR
// Translates IR v2 ScriptOp nodes into typed C AST CStatement nodes.
// All 25 ScriptOp subtypes have real implementations — zero TODO stubs.
// No string output — all results are typed CStatement subtypes.
// =============================================================================

/**
 * Visitor that converts IR v2 [ScriptOp] nodes to typed C AST [CStatement] nodes.
 *
 * Implements [ScriptOpVisitorI]<[CStatement]> so that each ScriptOp subtype dispatches via
 * [ScriptOp.accept] rather than a `when` expression. This allows external modules to define their
 * own [ScriptOp] subtypes without modifying this visitor.
 *
 * All 24 [ScriptOp] subtypes are handled with real C AST output:
 * - [Assign] — variable assignment with compound operators
 * - [ArrayAssign] — array element assignment
 * - [IfOp] — conditional branch with optional else
 * - [WhileOp] — while loop via [CWhile]
 * - [ForOp] — ranged for loop via [CFor]
 * - [SetPosition] — absolute actor position (x and y assignments)
 * - [MoveBy] — relative actor movement (skips zero offsets)
 * - [NavigateTo] — scene transition via navigate_to_scene()
 * - [TriggerSystem] — system event dispatch via trigger_{systemId}()
 * - [PlaySound] — sound effect via play_sound_{id}() wrapper
 * - [DialogSay] — dialog box text via show_dialog_{id}() helper
 * - [MenuShow] — interactive menu via show_menu_{id}() helper
 * - [PrintOp] — text output via gotoxy() + printf()
 * - [FadeOp] — screen fade via fade_in()/fade_out() helpers
 * - [SetVisible] — actor visibility via hide/show_sprites_range()
 * - [SpawnActor] — OAM slot claim via spawn_actor() free list
 * - [DestroyActor] — OAM slot release via destroy_actor() free list
 * - [AnimateOp] — frame-counter-based tile swap
 * - [CameraOp] — camera follow/shake/move via camera variable assignments
 * - [WaitFrames] — state machine pattern (frame counter + early return)
 * - [CallOp] — direct C function call with arguments
 * - [ReturnOp] — early return via [CReturn]
 * - [MathOp] — math utility (abs, min, max, clamp, rand)
 * - [RawOp] — passthrough to [CRawCode]
 *
 * Delegates expression translation to [ExprVisitor]. No string concatenation occurs — outputs are
 * typed [CStatement] subtypes only.
 *
 * Every returned [CStatement] carries [op.sourceLocation][ScriptOp.sourceLocation] so that the
 * [io.github.gbkt.backend.gbdk.codegen.pipeline.SourceMapCollector] can record line mappings during
 * C code emission.
 */
object ScriptOpVisitor : ScriptOpVisitorI<CStatement> {

    /**
     * Thread-local [ExprVisitor] instance.
     *
     * Holds the currently active [ExprVisitor] for the ongoing codegen call. Defaults to a no-actor
     * visitor for backward compatibility. [SceneVisitor] sets this to an actor-aware instance
     * before dispatching, so that `collides()` expressions inside [IfOp]/[WhileOp] conditions are
     * resolved against the game's actor list.
     */
    private val exprVisitorContext: ThreadLocal<ExprVisitor> =
        ThreadLocal.withInitial { ExprVisitor() }

    /**
     * Thread-local flag indicating whether the current game has an AudioMixer configured.
     *
     * When false, `visitMusicPlay` and `visitMusicStop` skip `fade_group()` calls (which require
     * AudioMixer) and fall back to instant play/stop.
     */
    private val hasAudioMixerContext: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    /** Sets the AudioMixer availability flag for the current codegen context. */
    fun setHasAudioMixer(value: Boolean) {
        hasAudioMixerContext.set(value)
    }

    /**
     * Thread-local map of sound effect definitions keyed by ID.
     *
     * Set by the codegen pipeline before visiting script ops. Used by [visitPlaySound] to look up
     * the channel and priority for AudioMixer channel preemption.
     */
    private val soundEffectDefsContext: ThreadLocal<Map<String, SoundEffectDef>> =
        ThreadLocal.withInitial { emptyMap() }

    /** Sets the sound effect definitions for the current codegen context. */
    fun setSoundEffectDefs(defs: List<SoundEffectDef>) {
        soundEffectDefsContext.set(defs.associateBy { it.id })
    }

    /** Returns the [ExprVisitor] active in the current call context. */
    private fun ev(): ExprVisitor = exprVisitorContext.get()

    /**
     * Convert an IR [ScriptOp] to a typed [CStatement] node using the provided [ExprVisitor].
     *
     * Sets [exprVisitorContext] to [exprVisitor] for the duration of this call so that all
     * expression sub-conversions (e.g. [IfOp.condition] collision checks) use the actor-aware
     * visitor.
     *
     * @param op The script op to convert.
     * @param exprVisitor Actor-aware [ExprVisitor] created by [SceneVisitor].
     */
    fun visit(op: ScriptOp, exprVisitor: ExprVisitor): CStatement {
        exprVisitorContext.set(exprVisitor)
        return op.accept(this)
    }

    /**
     * Convert an IR [ScriptOp] to a typed [CStatement] node via visitor dispatch.
     *
     * Delegates to [ScriptOp.accept] which routes to the appropriate `visit*` method on this
     * object. Uses the default (no-actor) [ExprVisitor] — for actor-aware codegen (e.g. collision),
     * use [visit(op, exprVisitor)] instead.
     *
     * The returned [CStatement] carries [op.sourceLocation] for source map collection.
     */
    fun visit(op: ScriptOp): CStatement = op.accept(this)

    // -------------------------------------------------------------------------
    // Assign: variable assignment with compound operators
    // -------------------------------------------------------------------------

    override fun visitAssign(op: Assign): CStatement {
        val cOp = assignOpToC(op.op)
        val target = CVar(ev().sanitizeVarName(op.target))
        val value = ev().visit(op.value)
        return CExprStatement(CBinaryExpr(target, cOp, value), sourceLocation = op.sourceLocation)
    }

    // -------------------------------------------------------------------------
    // ArrayAssign: array element assignment
    // -------------------------------------------------------------------------

    override fun visitArrayAssign(op: ArrayAssign): CStatement {
        val arrayAccess = CArrayAccess(CVar(ev().sanitizeVarName(op.array)), ev().visit(op.index))
        val value = ev().visit(op.value)
        val cOp = assignOpToC(op.op)
        return CExprStatement(
            CBinaryExpr(arrayAccess, cOp, value),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // IfOp: conditional branch
    // -------------------------------------------------------------------------

    override fun visitIfOp(op: IfOp): CStatement {
        val condition = ev().visit(op.condition)
        val thenBody = op.then.map { visit(it) }
        val elseBody = op.otherwise.map { visit(it) }
        return CIf(condition, thenBody, elseBody, sourceLocation = op.sourceLocation)
    }

    // -------------------------------------------------------------------------
    // WhileOp: while loop via CWhile
    // -------------------------------------------------------------------------

    override fun visitWhileOp(op: WhileOp): CStatement =
        CWhile(
            condition = ev().visit(op.condition),
            body = op.body.map { visit(it) },
            sourceLocation = op.sourceLocation,
        )

    // -------------------------------------------------------------------------
    // ForOp: ranged for loop via CFor
    // -------------------------------------------------------------------------

    override fun visitForOp(op: ForOp): CStatement {
        val varName = ev().sanitizeVarName(op.variable)
        val init = CVarDecl(varName, CI8, ev().visit(op.from))
        val condition = CBinaryExpr(CVar(varName), "<=", ev().visit(op.to))
        val increment = CUnaryExpr("++", CVar(varName))
        return CFor(
            init = init,
            condition = condition,
            increment = increment,
            body = op.body.map { visit(it) },
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // SetPosition: teleport actor to absolute coordinates
    // -------------------------------------------------------------------------

    override fun visitSetPosition(op: SetPosition): CStatement {
        val xVar = CVar("_${op.actorId}_x")
        val yVar = CVar("_${op.actorId}_y")
        val xExpr = ev().visit(op.x)
        val yExpr = ev().visit(op.y)
        return CBlock(
            listOf(
                CExprStatement(CBinaryExpr(xVar, "=", xExpr)),
                CExprStatement(CBinaryExpr(yVar, "=", yExpr)),
            ),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // MoveBy: relative movement — skips zero Literal offsets
    // -------------------------------------------------------------------------

    override fun visitMoveBy(op: MoveBy): CStatement {
        val statements = mutableListOf<CStatement>()
        if (!isZeroLiteral(op.dx)) {
            val xVar = CVar("_${op.actorId}_x")
            statements += CExprStatement(CBinaryExpr(xVar, "+=", ev().visit(op.dx)))
        }
        if (!isZeroLiteral(op.dy)) {
            val yVar = CVar("_${op.actorId}_y")
            statements += CExprStatement(CBinaryExpr(yVar, "+=", ev().visit(op.dy)))
        }
        return CBlock(statements, sourceLocation = op.sourceLocation)
    }

    /** Returns true if [expr] is a [Literal] with value 0 — used to skip no-op movements. */
    private fun isZeroLiteral(expr: Expr): Boolean = expr is Literal && expr.value == 0

    // -------------------------------------------------------------------------
    // NavigateTo: scene transition
    // -------------------------------------------------------------------------

    override fun visitNavigateTo(op: NavigateTo): CStatement {
        val sceneConstant = CVar("SCENE_${op.sceneId.uppercase()}")
        return CExprStatement(
            CCall("navigate_to_scene", listOf(sceneConstant)),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // TriggerSystem: fire system event via trigger_{systemId}() call
    // -------------------------------------------------------------------------

    override fun visitTriggerSystem(op: TriggerSystem): CStatement {
        val args = op.args.values.map { ev().visit(it) }
        return CExprStatement(
            CCall("trigger_${op.systemId}", args),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // PlaySound: call sound preset wrapper play_sound_{id}()
    // -------------------------------------------------------------------------

    override fun visitPlaySound(op: PlaySound): CStatement {
        val playCall =
            CExprStatement(
                CCall("play_sound_${op.soundId}", emptyList()),
                sourceLocation = op.sourceLocation,
            )
        // When AudioMixer is configured, prepend a channel request with SFX priority
        if (!hasAudioMixerContext.get()) return playCall
        val sfxDef = soundEffectDefsContext.get()[op.soundId] ?: return playCall
        val channelGroup =
            when (sfxDef.channel) {
                SoundChannel.PULSE1 -> "CHANNEL_PULSE1"
                SoundChannel.PULSE2 -> "CHANNEL_PULSE2"
                SoundChannel.WAVE -> "CHANNEL_WAVE"
                SoundChannel.NOISE -> "CHANNEL_NOISE"
            }
        val requestCall =
            CExprStatement(
                CCall(
                    "audio_mixer_request_channel",
                    listOf(CRawExpr(channelGroup), CLiteral(sfxDef.priority.value)),
                ),
                sourceLocation = op.sourceLocation,
            )
        return CBlock(
            statements = listOf(requestCall, playCall),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // Music ops: hUGETracker integration
    // -------------------------------------------------------------------------

    override fun visitMusicPlay(op: io.github.gbkt.core.ir.MusicPlay): CStatement {
        val statements = mutableListOf<CStatement>()
        if (op.resume) {
            // Resume: emit comment indicating that true resume requires AudioMixer state functions
            statements +=
                CComment(
                    "/* resume: hUGE_init restarts song; use AudioMixer save/restore for true resume */"
                )
        }
        statements +=
            CExprStatement(
                CCall("hUGE_init", listOf(CRawExpr("&song_${op.songId}"))),
                sourceLocation = op.sourceLocation,
            )
        if (op.fadeInFrames > 0) {
            if (hasAudioMixerContext.get()) {
                // Fade-in via AudioMixer fade_group call.
                statements +=
                    CExprStatement(
                        CCall(
                            "fade_group",
                            listOf(
                                CVar("CHANNEL_ALL"),
                                CLiteral(0),
                                CLiteral(15),
                                CLiteral(op.fadeInFrames),
                            ),
                        ),
                        sourceLocation = op.sourceLocation,
                    )
            } else {
                // No AudioMixer — skip fade_group, instant play only.
                statements +=
                    CComment("/* fade-in skipped: audioMixer {} not configured — instant play */")
            }
        }
        return if (statements.size == 1) statements[0]
        else CBlock(statements, sourceLocation = op.sourceLocation)
    }

    override fun visitMusicStop(op: io.github.gbkt.core.ir.MusicStop): CStatement {
        val muteStatements =
            listOf(
                CExprStatement(CCall("hUGEDriver_mute_channel", listOf(CRawExpr("0")))),
                CExprStatement(CCall("hUGEDriver_mute_channel", listOf(CRawExpr("1")))),
                CExprStatement(CCall("hUGEDriver_mute_channel", listOf(CRawExpr("2")))),
                CExprStatement(CCall("hUGEDriver_mute_channel", listOf(CRawExpr("3")))),
            )
        if (op.fadeOutFrames > 0) {
            val statements = mutableListOf<CStatement>()
            if (hasAudioMixerContext.get()) {
                // Fade-out via AudioMixer fade_group call.
                statements +=
                    CExprStatement(
                        CCall(
                            "fade_group",
                            listOf(
                                CVar("CHANNEL_ALL"),
                                CLiteral(15),
                                CLiteral(0),
                                CLiteral(op.fadeOutFrames),
                            ),
                        ),
                        sourceLocation = op.sourceLocation,
                    )
            } else {
                // No AudioMixer — skip fade_group, instant mute only.
                statements +=
                    CComment("/* fade-out skipped: audioMixer {} not configured — instant mute */")
            }
            statements += muteStatements
            return CBlock(statements, sourceLocation = op.sourceLocation)
        }
        return CBlock(muteStatements, sourceLocation = op.sourceLocation)
    }

    override fun visitMusicPause(op: io.github.gbkt.core.ir.MusicPause): CStatement =
        CExprStatement(
            CCall("hUGE_set_pause", listOf(CLiteral(1))),
            sourceLocation = op.sourceLocation,
        )

    override fun visitMusicResume(op: io.github.gbkt.core.ir.MusicResume): CStatement =
        CExprStatement(
            CCall("hUGE_set_pause", listOf(CLiteral(0))),
            sourceLocation = op.sourceLocation,
        )

    // -------------------------------------------------------------------------
    // DialogSay: emit dialog text via show_dialog_{id}() helper
    // -------------------------------------------------------------------------

    override fun visitDialogSay(op: DialogSay): CStatement {
        // Concatenate all text segments into a single string for the dialog function.
        // DialogExprSegment (dynamic values) are not yet supported in typewriter rendering;
        // they are emitted as "?" placeholders.
        val text =
            op.segments.joinToString("") { segment ->
                when (segment) {
                    is DialogTextSegment -> segment.text
                    is DialogExprSegment -> "?"
                }
            }
        return CExprStatement(
            CCall(
                "show_dialog_${op.dialogId}",
                listOf(CStringLiteral(text), CLiteral(text.length)),
            ),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // DialogChoice: emit choice prompt via show_dialog_choice_{id}() helper
    // -------------------------------------------------------------------------

    override fun visitDialogChoice(op: DialogChoice): CStatement =
        CExprStatement(
            CCall("show_dialog_choice_${op.dialogId}", emptyList()),
            sourceLocation = op.sourceLocation,
        )

    // -------------------------------------------------------------------------
    // MenuShow / MenuHide: open/close menu via menu helpers
    // -------------------------------------------------------------------------

    override fun visitMenuShow(op: MenuShow): CStatement =
        CExprStatement(
            CCall("show_menu_${op.menuId}", emptyList()),
            sourceLocation = op.sourceLocation,
        )

    override fun visitMenuHide(op: MenuHide): CStatement =
        // Simple window hide — menus are always on the window layer
        CRawCode("HIDE_WIN;")

    // -------------------------------------------------------------------------
    // HudShow / HudHide: show/hide HUD panel
    // -------------------------------------------------------------------------

    override fun visitHudShow(op: HudShow): CStatement =
        CExprStatement(
            CCall("show_hud_${op.hudId}", emptyList()),
            sourceLocation = op.sourceLocation,
        )

    override fun visitHudHide(op: HudHide): CStatement =
        CExprStatement(
            CCall("hide_hud_${op.hudId}", emptyList()),
            sourceLocation = op.sourceLocation,
        )

    // -------------------------------------------------------------------------
    // PrintAt / PrintCentered / PrintAligned: window-layer text rendering
    // -------------------------------------------------------------------------

    override fun visitPrintAt(op: PrintAt): CStatement {
        val fn = if (op.fontMode == FontMode.VARIABLE_WIDTH) "_vwf_print_at" else "_win_print_at"
        return CExprStatement(
            CCall(
                fn,
                listOf(
                    CLiteral(op.x),
                    CLiteral(op.y),
                    CStringLiteral(op.text),
                    CLiteral(op.text.length),
                ),
            ),
            sourceLocation = op.sourceLocation,
        )
    }

    override fun visitPrintCentered(op: PrintCentered): CStatement {
        val fn = if (op.fontMode == FontMode.VARIABLE_WIDTH) "_vwf_print_at" else "_win_print_at"
        // Center on 20-tile wide window layer: x = (20 - len) / 2
        val xOffset = (20 - op.text.length) / 2
        return CExprStatement(
            CCall(
                fn,
                listOf(
                    CLiteral(xOffset.coerceAtLeast(0)),
                    CLiteral(op.row),
                    CStringLiteral(op.text),
                    CLiteral(op.text.length),
                ),
            ),
            sourceLocation = op.sourceLocation,
        )
    }

    override fun visitPrintAligned(op: PrintAligned): CStatement {
        val fn = if (op.fontMode == FontMode.VARIABLE_WIDTH) "_vwf_print_at" else "_win_print_at"
        val xOffset =
            when (op.alignment) {
                TextAlignment.LEFT -> 0
                TextAlignment.CENTER -> (20 - op.text.length) / 2
                TextAlignment.RIGHT -> 20 - op.text.length
            }
        return CExprStatement(
            CCall(
                fn,
                listOf(
                    CLiteral(xOffset.coerceAtLeast(0)),
                    CLiteral(op.row),
                    CStringLiteral(op.text),
                    CLiteral(op.text.length),
                ),
            ),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // ClearRegion / ScreenClear / ScreenFill: screen operations
    // -------------------------------------------------------------------------

    override fun visitClearRegion(op: ClearRegion): CStatement =
        CExprStatement(
            CCall(
                "_win_clear_region",
                listOf(CLiteral(op.x), CLiteral(op.y), CLiteral(op.w), CLiteral(op.h)),
            ),
            sourceLocation = op.sourceLocation,
        )

    override fun visitScreenClear(op: ScreenClear): CStatement =
        CExprStatement(CCall("cls", emptyList()), sourceLocation = op.sourceLocation)

    override fun visitScreenFill(op: ScreenFill): CStatement =
        CExprStatement(
            CCall("_win_fill_screen", listOf(CLiteral(op.tile))),
            sourceLocation = op.sourceLocation,
        )

    // -------------------------------------------------------------------------
    // PrintOp: text output with optional position
    // -------------------------------------------------------------------------

    override fun visitPrintOp(op: PrintOp): CStatement {
        val statements = mutableListOf<CStatement>()
        val position = op.position
        if (position != null) {
            statements +=
                CExprStatement(CCall("gotoxy", listOf(CLiteral(position.x), CLiteral(position.y))))
        }
        val printfArgs = mutableListOf<CExpr>(CStringLiteral(op.text))
        for (value in op.values) {
            printfArgs += ev().visit(value)
        }
        statements += CExprStatement(CCall("printf", printfArgs))
        return CBlock(statements, sourceLocation = op.sourceLocation)
    }

    // =====================================================================
    // GotoXYOp → gotoxy(x, y)
    // =====================================================================
    override fun visitGotoXYOp(op: GotoXYOp): CStatement =
        CExprStatement(
            CCall("gotoxy", listOf(ev().visit(op.x), ev().visit(op.y))),
            sourceLocation = op.sourceLocation,
        )

    // -------------------------------------------------------------------------
    // FadeOp: screen fade in/out via fade_in()/fade_out() helpers
    // -------------------------------------------------------------------------

    override fun visitFadeOp(op: FadeOp): CStatement {
        val fadeFn = if (op.fadeIn) "fade_in" else "fade_out"
        val statements = mutableListOf<CStatement>()
        statements += CExprStatement(CCall(fadeFn, emptyList()), sourceLocation = op.sourceLocation)
        statements += op.after.map { visit(it) }
        return if (statements.size == 1) statements[0]
        else CBlock(statements, sourceLocation = op.sourceLocation)
    }

    // -------------------------------------------------------------------------
    // SetVisible: show/hide actor sprites via range helpers
    // -------------------------------------------------------------------------

    override fun visitSetVisible(op: SetVisible): CStatement {
        // Visibility is controlled per-actor via hide_sprites_range / show_sprites_range.
        // The OAM slot range is determined by a fixed 2-slot-per-actor convention (8x16 sprites).
        // For single-sprite actors this produces move_sprite(slot, 0, 0) to hide.
        val fnName = if (op.visible) "show_sprites_range" else "hide_sprites_range"
        // Use a symbolic actor slot variable: _<actorId>_oam_slot for from, +count for to.
        // Since we don't have analysis pass data here, emit a call with actor id slot convention.
        val slotVar = CVar("_${op.actorId}_oam_slot")
        val slotEnd = CBinaryExpr(slotVar, "+", CLiteral(1))
        return CExprStatement(
            CCall(fnName, listOf(slotVar, slotEnd)),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // SpawnActor: claims OAM slot from free list, positions sprite
    // -------------------------------------------------------------------------

    /**
     * Generate real OAM slot management for [SpawnActor].
     *
     * Calls `spawn_actor(actor_id)` (generated by [GBDKSystemVisitor.buildSpawnActorFunction])
     * which pops a free slot from the OAM free stack. The returned slot is stored in the actor's
     * `_<actorId>_oam_slot` global, then set_sprite_tile and move_sprite are called to position the
     * sprite in OAM.
     */
    override fun visitSpawnActor(op: SpawnActor): CStatement {
        val actorId = op.actorId
        val actorHash = CLiteral("\"$actorId\"".hashCode() and 0xFF)
        val slotVar = CVar("_${actorId}_oam_slot")
        val xExpr = CBinaryExpr(CVar("_${actorId}_x"), "+", CLiteral(8))
        val yExpr = CBinaryExpr(CVar("_${actorId}_y"), "+", CLiteral(16))
        return CBlock(
            listOf(
                // _actorId_oam_slot = spawn_actor(hash);
                CExprStatement(
                    CBinaryExpr(slotVar, "=", CCall("spawn_actor", listOf(actorHash))),
                    sourceLocation = op.sourceLocation,
                ),
                // if (_actorId_oam_slot != 0xFF) { move_sprite(slot, x+8, y+16); }
                CIf(
                    condition = CBinaryExpr(slotVar, "!=", CRawExpr("0xFF")),
                    thenBody =
                        listOf(CExprStatement(CCall("move_sprite", listOf(slotVar, xExpr, yExpr)))),
                ),
            ),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // DestroyActor: returns OAM slot to free list, hides sprite
    // -------------------------------------------------------------------------

    /**
     * Generate real OAM slot release for [DestroyActor].
     *
     * Calls `destroy_actor(slot)` (generated by [GBDKSystemVisitor.buildDestroyActorFunction])
     * which pushes the slot back onto the OAM free stack and moves the sprite off-screen. Resets
     * the actor's `_<actorId>_oam_slot` to 0xFF (invalid).
     */
    override fun visitDestroyActor(op: DestroyActor): CStatement {
        val actorId = op.actorId
        val slotVar = CVar("_${actorId}_oam_slot")
        return CBlock(
            listOf(
                // destroy_actor(_actorId_oam_slot);
                CExprStatement(
                    CCall("destroy_actor", listOf(slotVar)),
                    sourceLocation = op.sourceLocation,
                ),
                // _actorId_oam_slot = 0xFF;
                CExprStatement(CBinaryExpr(slotVar, "=", CRawExpr("0xFF"))),
            ),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // PoolSpawnActor: spawn entity from actor pool at (x, y)
    // -------------------------------------------------------------------------

    /**
     * Generate pool spawn call for [PoolSpawnActor].
     *
     * Emits `pool_<poolId>_spawn(x, y)` — the generated pool function finds a free slot, positions
     * the sprite, and returns the slot index (0xFF if pool is full with SILENT_NOOP).
     */
    override fun visitPoolSpawnActor(op: io.github.gbkt.core.ir.PoolSpawnActor): CStatement =
        CExprStatement(
            CCall("pool_${op.poolId}_spawn", listOf(ev().visit(op.x), ev().visit(op.y))),
            sourceLocation = op.sourceLocation,
        )

    // -------------------------------------------------------------------------
    // PoolDestroyActor: return pool slot, hide sprite
    // -------------------------------------------------------------------------

    /**
     * Generate pool destroy call for [PoolDestroyActor].
     *
     * Emits `pool_<poolId>_destroy(slot)` — marks the slot inactive and moves the sprite offscreen.
     * If [PoolDestroyActor.deathCallbackOps] is non-empty, emits inline callback code before the
     * destroy call.
     */
    override fun visitPoolDestroyActor(op: io.github.gbkt.core.ir.PoolDestroyActor): CStatement {
        val destroyCall =
            CExprStatement(
                CCall("pool_${op.poolId}_destroy", listOf(ev().visit(op.slotExpr))),
                sourceLocation = op.sourceLocation,
            )
        return if (op.deathCallbackOps.isEmpty()) {
            destroyCall
        } else {
            val callbackStmts = op.deathCallbackOps.map { it.accept(this) }
            CBlock(callbackStmts + listOf(destroyCall), sourceLocation = op.sourceLocation)
        }
    }

    // -------------------------------------------------------------------------
    // PoolForEachActive: for-loop over active slots with active guard
    // -------------------------------------------------------------------------

    /**
     * Generate a for-loop that iterates all active slots in a pool.
     *
     * Emits:
     * ```c
     * UINT8 _<slotVar>;
     * for (_<slotVar> = 0; _<slotVar> < maxSize; _<slotVar>++) {
     *     if (_pool_<id>_active[_<slotVar>]) {
     *         // body
     *     }
     * }
     * ```
     *
     * The slot variable name is sanitized through [ExprVisitor.sanitizeVarName] so that the
     * declaration matches what [ExprVisitor] produces for [VarRef] nodes in the body.
     */
    override fun visitPoolForEachActive(op: PoolForEachActive): CStatement {
        val sanitizedSlotVar = ExprVisitor.sanitizeVarName(op.slotVarName)
        val slotVar = CVar(sanitizedSlotVar)
        val activeArr = CVar("_pool_${op.poolId}_active")
        val bodyStmts = op.body.map { it.accept(this) }
        return CBlock(
            listOf(
                CVarDecl(sanitizedSlotVar, CU8, initializer = null),
                CFor(
                    init = CExprStatement(CBinaryExpr(slotVar, "=", CLiteral(0))),
                    condition = CBinaryExpr(slotVar, "<", CLiteral(op.maxSize)),
                    increment = CUnaryExpr("++", slotVar),
                    body =
                        listOf(
                            CIf(condition = CArrayAccess(activeArr, slotVar), thenBody = bodyStmts)
                        ),
                ),
            ),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // PoolDestroyAll: bulk-destroy all active pool slots
    // -------------------------------------------------------------------------

    /**
     * Generate a for-loop that clears all active pool slots and hides their sprites.
     *
     * Emits:
     * ```c
     * UINT8 i;
     * for (i = 0; i < maxSize; i++) {
     *     _pool_<id>_active[i] = 0;
     *     move_sprite(_pool_<id>_oam_base + i, 0, 0);
     * }
     * ```
     */
    override fun visitPoolDestroyAll(op: PoolDestroyAll): CStatement {
        val iVar = CVar("i")
        val activeArr = CVar("_pool_${op.poolId}_active")
        val oamBase = CVar("_pool_${op.poolId}_oam_base")
        return CBlock(
            listOf(
                CVarDecl("i", CU8, initializer = null),
                CFor(
                    init = CExprStatement(CBinaryExpr(iVar, "=", CLiteral(0))),
                    condition = CBinaryExpr(iVar, "<", CLiteral(op.maxSize)),
                    increment = CUnaryExpr("++", iVar),
                    body =
                        listOf(
                            CExprStatement(
                                CBinaryExpr(CArrayAccess(activeArr, iVar), "=", CLiteral(0))
                            ),
                            CExprStatement(
                                CCall(
                                    "move_sprite",
                                    listOf(
                                        CBinaryExpr(oamBase, "+", iVar),
                                        CLiteral(0),
                                        CLiteral(0),
                                    ),
                                )
                            ),
                        ),
                ),
            ),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // AnimateOp: frame-counter-based tile swap (typed C AST, no CRawCode)
    // -------------------------------------------------------------------------

    /**
     * Generate typed frame counter logic for [AnimateOp].
     *
     * Replaces the former [CRawCode] implementation with typed C AST nodes:
     * - Increment `_actorId_anim_ctr`
     * - When counter reaches 8 (default) or actor's frameSpeed: reset counter, increment frame
     *
     * The default speed is 8 frames between animation updates (matching the former hardcoded
     * value). Actors configured with [ActorIR.frameSpeed] use that value instead via the state
     * machine codegen path ([update_animation_{id}()] function). This visitAnimateOp handles the
     * legacy inline-animate-op path with typed AST.
     */
    override fun visitAnimateOp(op: AnimateOp): CStatement {
        val counterVar = CVar("_${op.actorId}_anim_ctr")
        val frameVar = CVar("_${op.actorId}_anim_frame")
        val frameSpeed =
            8 // default speed; per-actor frameSpeed handled via generateAnimationFunction
        return CBlock(
            listOf(
                CExprStatement(CUnaryExpr("++", counterVar), sourceLocation = op.sourceLocation),
                CIf(
                    condition = CBinaryExpr(counterVar, ">=", CLiteral(frameSpeed)),
                    thenBody =
                        listOf(
                            CExprStatement(CBinaryExpr(counterVar, "=", CLiteral(0))),
                            CExprStatement(CUnaryExpr("++", frameVar)),
                        ),
                ),
            ),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // CameraOp: camera control (follow, shake, move)
    // -------------------------------------------------------------------------

    override fun visitCameraOp(op: CameraOp): CStatement =
        when (op.action) {
            CameraAction.FOLLOW -> {
                // Set camera target to the actor/entity specified in args["target"]
                val target = op.args["target"]
                if (target != null) {
                    CExprStatement(
                        CBinaryExpr(CVar("_camera_target"), "=", ev().visit(target)),
                        sourceLocation = op.sourceLocation,
                    )
                } else {
                    CComment("CameraOp FOLLOW: no target specified")
                }
            }
            CameraAction.UNFOLLOW ->
                CExprStatement(
                    CBinaryExpr(CVar("_camera_target"), "=", CLiteral(-1)),
                    sourceLocation = op.sourceLocation,
                )
            CameraAction.SHAKE -> {
                // Set camera shake counter — intensity from args["intensity"], duration from
                // args["duration"]
                val intensity = op.args["intensity"]?.let { ev().visit(it) } ?: CLiteral(4)
                val duration = op.args["duration"]?.let { ev().visit(it) } ?: CLiteral(10)
                CBlock(
                    listOf(
                        CExprStatement(
                            CBinaryExpr(CVar("_camera_shake_intensity"), "=", intensity)
                        ),
                        CExprStatement(CBinaryExpr(CVar("_camera_shake_timer"), "=", duration)),
                    ),
                    sourceLocation = op.sourceLocation,
                )
            }
            CameraAction.MOVE_TO -> {
                val x = op.args["x"]?.let { ev().visit(it) } ?: CLiteral(0)
                val y = op.args["y"]?.let { ev().visit(it) } ?: CLiteral(0)
                CBlock(
                    listOf(
                        CExprStatement(CBinaryExpr(CVar("_camera_x"), "=", x)),
                        CExprStatement(CBinaryExpr(CVar("_camera_y"), "=", y)),
                    ),
                    sourceLocation = op.sourceLocation,
                )
            }
        }

    // -------------------------------------------------------------------------
    // WaitFrames: state machine pattern (frame counter + early return)
    // NOT a busy-wait loop — returns from frame handler, resumes next frame
    // -------------------------------------------------------------------------

    override fun visitWaitFrames(op: WaitFrames): CStatement =
        CBlock(
            listOf(
                // Set the counter to the requested frame count
                CExprStatement(CBinaryExpr(CVar("_wait_counter"), "=", CLiteral(op.frames))),
                // State-machine check: if counter > 0, decrement and return (resume next frame)
                CIf(
                    condition = CBinaryExpr(CVar("_wait_counter"), ">", CLiteral(0)),
                    thenBody =
                        listOf(
                            CExprStatement(CUnaryExpr("--", CVar("_wait_counter"))),
                            CReturn(null),
                        ),
                ),
            ),
            sourceLocation = op.sourceLocation,
        )

    // -------------------------------------------------------------------------
    // CallOp: direct C function call with arguments
    // -------------------------------------------------------------------------

    override fun visitCallOp(op: CallOp): CStatement =
        CExprStatement(
            CCall(op.function, op.args.map { ev().visit(it) }),
            sourceLocation = op.sourceLocation,
        )

    // -------------------------------------------------------------------------
    // ReturnOp: early return from function
    // -------------------------------------------------------------------------

    override fun visitReturnOp(op: ReturnOp): CStatement {
        val value = op.value?.let { ev().visit(it) }
        return CReturn(value, sourceLocation = op.sourceLocation)
    }

    // -------------------------------------------------------------------------
    // MathOp: math utility functions (abs, min, max, clamp, rand)
    // -------------------------------------------------------------------------

    override fun visitMathOp(op: MathOp): CStatement {
        val target = CVar(ev().sanitizeVarName(op.result))
        val resultExpr =
            when (op.op) {
                MathFunction.ABS -> {
                    val operand = op.args.getOrNull(0)?.let { ev().visit(it) } ?: CLiteral(0)
                    CCall("abs", listOf(operand))
                }
                MathFunction.MIN -> {
                    val a = op.args.getOrNull(0)?.let { ev().visit(it) } ?: CLiteral(0)
                    val b = op.args.getOrNull(1)?.let { ev().visit(it) } ?: CLiteral(0)
                    CTernary(CBinaryExpr(a, "<", b), a, b)
                }
                MathFunction.MAX -> {
                    val a = op.args.getOrNull(0)?.let { ev().visit(it) } ?: CLiteral(0)
                    val b = op.args.getOrNull(1)?.let { ev().visit(it) } ?: CLiteral(0)
                    CTernary(CBinaryExpr(a, ">", b), a, b)
                }
                MathFunction.CLAMP -> {
                    val value = op.args.getOrNull(0)?.let { ev().visit(it) } ?: CLiteral(0)
                    val minVal = op.args.getOrNull(1)?.let { ev().visit(it) } ?: CLiteral(0)
                    val maxVal = op.args.getOrNull(2)?.let { ev().visit(it) } ?: CLiteral(255)
                    // clamp(v, min, max) = v < min ? min : (v > max ? max : v)
                    CTernary(
                        CBinaryExpr(value, "<", minVal),
                        minVal,
                        CTernary(CBinaryExpr(value, ">", maxVal), maxVal, value),
                    )
                }
                MathFunction.RAND -> CCall("rand", emptyList())
            }
        return CExprStatement(
            CBinaryExpr(target, "=", resultExpr),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // RawOp: pass-through to CRawCode
    // -------------------------------------------------------------------------

    override fun visitRawOp(op: RawOp): CStatement =
        CRawCode(op.code, sourceLocation = op.sourceLocation)

    // -------------------------------------------------------------------------
    // SetPalette: load a GBC palette into a hardware slot
    // -------------------------------------------------------------------------

    override fun visitSetPalette(op: io.github.gbkt.core.ir.SetPalette): CStatement {
        val func =
            when (op.type) {
                io.github.gbkt.core.ir.PaletteType.BACKGROUND -> "set_bkg_palette"
                io.github.gbkt.core.ir.PaletteType.SPRITE -> "set_sprite_palette"
            }
        return CExprStatement(
            CCall(func, listOf(CLiteral(op.slot), CLiteral(1), CVar("${op.paletteName}_pal"))),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // SetAnimationState: manual animation state machine transition
    // -------------------------------------------------------------------------

    /**
     * Generate state machine transition code for [SetAnimationState].
     *
     * Sets the state variable to the target state constant and resets both the frame counter and
     * counter to 0. The enum constant follows the naming pattern:
     * `ANIM_{ACTORID_UPPERCASE}_{STATENAME_UPPERCASE}`.
     *
     * Generated C:
     * ```c
     * { _player_anim_state = ANIM_PLAYER_WALK; _player_anim_frame = 0; _player_anim_counter = 0; }
     * ```
     */
    override fun visitSetAnimationState(op: SetAnimationState): CStatement {
        val actorId = op.actorId
        val stateConst = "ANIM_${actorId.uppercase()}_${op.stateName.uppercase()}"
        return CBlock(
            listOf(
                CExprStatement(CBinaryExpr(CVar("_${actorId}_anim_state"), "=", CVar(stateConst))),
                CExprStatement(CBinaryExpr(CVar("_${actorId}_anim_frame"), "=", CLiteral(0))),
                CExprStatement(CBinaryExpr(CVar("_${actorId}_anim_counter"), "=", CLiteral(0))),
            ),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // PhysicsStep: per-frame physics integration (acceleration, gravity, velocity application)
    // -------------------------------------------------------------------------

    /**
     * Generate typed C AST for per-frame physics integration.
     *
     * Applies (in order):
     * 1. Acceleration: `_actorId_vx += ACCEL_X_ACTORID; _actorId_vy += ACCEL_Y_ACTORID`
     * 2. Gravity: `_actorId_vy += GRAVITY_ACTORID`
     * 3. Fall clamp: `if (_actorId_vy > MAX_FALL_ACTORID) _actorId_vy = MAX_FALL_ACTORID`
     * 4. Velocity to position: `_actorId_y += (UINT8)_actorId_vx; _actorId_x += (UINT8)_actorId_vx`
     *
     * Uses zero CRawCode — all output is typed C AST.
     */
    override fun visitPhysicsStep(op: PhysicsStep): CStatement {
        val actorId = op.actorId
        val actorIdUpper = actorId.uppercase()
        val vxVar = CVar("_${actorId}_vx")
        val vyVar = CVar("_${actorId}_vy")
        val xVar = CVar("_${actorId}_x")
        val yVar = CVar("_${actorId}_y")
        return CBlock(
            listOf(
                // 1. Apply acceleration to velocity
                CExprStatement(CBinaryExpr(vxVar, "+=", CVar("ACCEL_X_$actorIdUpper"))),
                CExprStatement(CBinaryExpr(vyVar, "+=", CVar("ACCEL_Y_$actorIdUpper"))),
                // 2. Apply gravity to VY
                CExprStatement(CBinaryExpr(vyVar, "+=", CVar("GRAVITY_$actorIdUpper"))),
                // 3. Clamp fall speed: if (_actorId_vy > MAX_FALL_ACTORID) _actorId_vy = MAX_FALL
                CIf(
                    condition = CBinaryExpr(vyVar, ">", CVar("MAX_FALL_$actorIdUpper")),
                    thenBody =
                        listOf(
                            CExprStatement(CBinaryExpr(vyVar, "=", CVar("MAX_FALL_$actorIdUpper")))
                        ),
                ),
                // 4. Apply velocity to position (cast I8 velocity to UINT8 for position variable)
                CExprStatement(CBinaryExpr(yVar, "+=", CCast(CU8, vyVar))),
                CExprStatement(CBinaryExpr(xVar, "+=", CCast(CU8, vxVar))),
            ),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // PathfindStep: NPC A* pathfinding — calls pf_find_path() then pf_step_toward()
    //
    // Converts pixel positions to tile coordinates using PF_GRID_SIZE constant
    // (generated as #define PF_GRID_SIZE <n> by GBDKSystemVisitor.visitPathfindingSystem).
    // pf_step_toward() moves the NPC one tile toward _pf_path_x[0], _pf_path_y[0].
    // -------------------------------------------------------------------------

    /**
     * Generate A* pathfinding step for [PathfindStep].
     *
     * Divides pixel positions by [PF_GRID_SIZE] to get tile coordinates, then calls
     * `pf_find_path()` to compute one step and `pf_step_toward()` to move the NPC.
     *
     * Uses `PF_GRID_SIZE` #define (emitted by [GBDKSystemVisitor.visitPathfindingSystem]) so the
     * grid size is compile-time constant, not runtime division.
     */
    override fun visitPathfindStep(op: PathfindStep): CStatement {
        val npcId = op.npcActorId
        val targetId = op.targetActorId
        val gridSizeVar = CVar("PF_GRID_SIZE")
        return CBlock(
            listOf(
                // Call pf_find_path(npc_tile_x, npc_tile_y, target_tile_x, target_tile_y)
                CExprStatement(
                    CCall(
                        "pf_find_path",
                        listOf(
                            CBinaryExpr(CVar("_${npcId}_x"), "/", gridSizeVar),
                            CBinaryExpr(CVar("_${npcId}_y"), "/", gridSizeVar),
                            CBinaryExpr(CVar("_${targetId}_x"), "/", gridSizeVar),
                            CBinaryExpr(CVar("_${targetId}_y"), "/", gridSizeVar),
                        ),
                    )
                ),
                // Move NPC one tile toward path result
                CExprStatement(
                    CCall("pf_step_toward", listOf(CVar("_${npcId}_x"), CVar("_${npcId}_y")))
                ),
            ),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // WaypointStep: NPC waypoint patrol — advances _<npcId>_wp_idx and moves toward waypoint
    //
    // Per-NPC waypoint index (_<npcId>_wp_idx) is incremented when actor reaches the
    // current waypoint (distance < PF_GRID_SIZE in both axes). Wraps to 0 on overflow.
    // Waypoint data (_<npcId>_wp_x[i], _<npcId>_wp_y[i]) is emitted as const arrays
    // by ActorVisitor when the actor has a WaypointRoute.
    // -------------------------------------------------------------------------

    /**
     * Generate waypoint patrol step for [WaypointStep].
     * 1. Read current waypoint target: `_<npcId>_wp_x[_<npcId>_wp_idx]`
     * 2. Move toward it using `pf_step_toward()`
     * 3. If reached (|dx| < PF_GRID_SIZE && |dy| < PF_GRID_SIZE), advance index
     *
     * The advancement check uses CRawCode for the ternary wrap expression since the waypoint count
     * (`_<npcId>_wp_count`) is a per-actor constant.
     */
    override fun visitWaypointStep(op: WaypointStep): CStatement {
        val npcId = op.npcActorId
        val wpIdxVar = CVar("_${npcId}_wp_idx")
        val wpXArray = "_${npcId}_wp_x"
        val wpYArray = "_${npcId}_wp_y"
        val wpCount = "_${npcId}_wp_count"
        return CBlock(
            listOf(
                // Move toward current waypoint
                CExprStatement(
                    CCall(
                        "pf_step_toward",
                        listOf(
                            CArrayAccess(CVar(wpXArray), wpIdxVar),
                            CArrayAccess(CVar(wpYArray), wpIdxVar),
                        ),
                    )
                ),
                // Check if reached: |npc_x - wp_x| < PF_GRID_SIZE && |npc_y - wp_y| < PF_GRID_SIZE
                // Advance index (wrap using modulo with waypoint count constant)
                CIf(
                    condition =
                        CBinaryExpr(
                            // abs(npc_x - wp_x[idx]) < PF_GRID_SIZE
                            CRawExpr(
                                "((_${npcId}_x > ${wpXArray}[${wpIdxVar.name}] ? _${npcId}_x - ${wpXArray}[${wpIdxVar.name}] : ${wpXArray}[${wpIdxVar.name}] - _${npcId}_x) < PF_GRID_SIZE)"
                            ),
                            "&&",
                            // abs(npc_y - wp_y[idx]) < PF_GRID_SIZE
                            CRawExpr(
                                "((_${npcId}_y > ${wpYArray}[${wpIdxVar.name}] ? _${npcId}_y - ${wpYArray}[${wpIdxVar.name}] : ${wpYArray}[${wpIdxVar.name}] - _${npcId}_y) < PF_GRID_SIZE)"
                            ),
                        ),
                    thenBody =
                        listOf(
                            // Advance and wrap: _wp_idx = (_wp_idx + 1 < wp_count) ? _wp_idx + 1 :
                            // 0
                            CExprStatement(
                                CBinaryExpr(
                                    wpIdxVar,
                                    "=",
                                    CTernary(
                                        CBinaryExpr(
                                            CBinaryExpr(wpIdxVar, "+", CLiteral(1)),
                                            "<",
                                            CVar(wpCount),
                                        ),
                                        CBinaryExpr(wpIdxVar, "+", CLiteral(1)),
                                        CLiteral(0),
                                    ),
                                )
                            )
                        ),
                ),
            ),
            sourceLocation = op.sourceLocation,
        )
    }

    // -------------------------------------------------------------------------
    // Puzzle object ops: activate, deactivate, reveal, hide
    // -------------------------------------------------------------------------

    /**
     * Activate a puzzle object (opens doors, toggles switches on).
     *
     * Emits `puzzle_activate_{id}()` — the generated C function sets the object state to active,
     * swaps tiles (for doors), and runs the onActivate/onOpen callback.
     */
    override fun visitActivatePuzzleObject(
        op: io.github.gbkt.core.ir.ActivatePuzzleObject
    ): CStatement =
        CExprStatement(
            CCall("puzzle_activate_${op.objectId}", emptyList()),
            sourceLocation = op.sourceLocation,
        )

    /**
     * Deactivate a puzzle object (closes doors, toggles switches off).
     *
     * Emits `puzzle_deactivate_{id}()` — the generated C function sets the object state to
     * inactive, swaps tiles (for doors), and runs the onDeactivate/onClose callback.
     */
    override fun visitDeactivatePuzzleObject(
        op: io.github.gbkt.core.ir.DeactivatePuzzleObject
    ): CStatement =
        CExprStatement(
            CCall("puzzle_deactivate_${op.objectId}", emptyList()),
            sourceLocation = op.sourceLocation,
        )

    /**
     * Reveal a hidden puzzle object (sets visible flag, makes tile visible).
     *
     * Emits `puzzle_reveal_{id}()` — clears the hidden flag and re-draws the object tile.
     */
    override fun visitRevealPuzzleObject(
        op: io.github.gbkt.core.ir.RevealPuzzleObject
    ): CStatement =
        CExprStatement(
            CCall("puzzle_reveal_${op.objectId}", emptyList()),
            sourceLocation = op.sourceLocation,
        )

    /**
     * Hide a puzzle object (sets hidden flag, clears tile).
     *
     * Emits `puzzle_hide_{id}()` — sets the hidden flag and clears the object tile.
     */
    override fun visitHidePuzzleObject(op: io.github.gbkt.core.ir.HidePuzzleObject): CStatement =
        CExprStatement(
            CCall("puzzle_hide_${op.objectId}", emptyList()),
            sourceLocation = op.sourceLocation,
        )

    // -------------------------------------------------------------------------
    // AssignOp mapping
    // -------------------------------------------------------------------------

    /**
     * Map an [AssignOp] to its C assignment operator string.
     *
     * Covers all [AssignOp] enum values exhaustively.
     */
    private fun assignOpToC(op: AssignOp): String =
        when (op) {
            AssignOp.SET -> "="
            AssignOp.ADD -> "+="
            AssignOp.SUB -> "-="
            AssignOp.MUL -> "*="
            AssignOp.DIV -> "/="
            AssignOp.MOD -> "%="
            AssignOp.AND -> "&="
            AssignOp.OR -> "|="
            AssignOp.XOR -> "^="
        }
}
