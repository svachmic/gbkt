/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CArray
import io.github.gbkt.backend.gbdk.codegen.ast.CArrayAccess
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CBreak
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CComment
import io.github.gbkt.backend.gbdk.codegen.ast.CContinue
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
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
import io.github.gbkt.backend.gbdk.codegen.ast.CWhile
import io.github.gbkt.core.ir.AtbGaugeModel
import io.github.gbkt.core.ir.AtbWaitMode
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatHookPoint
import io.github.gbkt.core.ir.CombatStateId
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.ProceduralWave
import io.github.gbkt.core.ir.ScriptedWave
import io.github.gbkt.core.ir.TacticalGridConfig
import io.github.gbkt.core.ir.TurnOrderStrategy
import io.github.gbkt.core.ir.WaveSurvivalConfig
import io.github.gbkt.core.ir.WaveTrigger

// =============================================================================
// COMBAT VISITOR
// Generates C functions for a CombatEngineSystem IR node.
//
// Generated functions per system:
//  - combat_request_state_<id>(UINT8 s)   — deferred transition request
//  - update_combat_<id>(void)             — per-frame update: deferred apply + conditions + switch
//  - combat_is_in_state_<id>(UINT8 state) — state query helper
//  - combat_parent_state_<id>(UINT8 state)— parent state lookup (only when stateHierarchy set)
//  - trigger_<id>(void)                   — reset combat to INIT
//  - damage_<id>(UINT8, UINT8, UINT8)    — damage dispatcher (only when damageFormula set)
//
// Wave survival extras (when combatType = WAVE_SURVIVAL):
//  - start_wave_<id>(UINT8 wave_num)     — spawn monsters for a wave
//  - check_wave_complete_<id>()           — returns 1 when all enemies defeated
//  - between_wave_<id>()                  — apply heal/shop, reset pause timer
//  - advance_wave_<id>()                  — increment wave, start next or trigger victory
// =============================================================================

/**
 * Generates typed C [CFunction] nodes for a [CombatEngineSystem] IR node.
 *
 * All generated code follows C89 rules:
 * - Variable declarations before statements
 * - No function pointers — all dispatch via switch
 * - Unsigned literal suffixes via [CLiteral] (emitted as Nu by
 *   [io.github.gbkt.backend.gbdk.codegen.emit.CEmitter])
 * - Deferred transitions via `_pending_state_<id>` sentinel (0xFF = no pending)
 *
 * @param gameIR The full [GameIR] for context (actor IDs, variable names, etc.)
 */
class CombatVisitor(private val gameIR: GameIR) {

    /**
     * Generate all C functions for the given [CombatEngineSystem].
     *
     * @param system The combat engine system to generate code for.
     * @return List of [CFunction] nodes for inclusion in the generated C output.
     */
    fun generateCombatFunctions(system: CombatEngineSystem): List<CFunction> {
        val id = system.id.replace('-', '_').replace(' ', '_')
        val exprVisitor = ExprVisitor(gameIR.actors)

        return buildList {
            add(generateRequestStateFunction(id))
            add(generateUpdateCombatFunction(system, id, exprVisitor))
            add(generateIsInStateFunction(id))
            if (system.stateHierarchy.isNotEmpty()) {
                add(generateParentStateFunction(system, id))
            }
            add(generateTriggerFunction(id))
            val damageFormula = system.damageFormula
            if (damageFormula != null) {
                add(generateDamageFunction(id, damageFormula.functionName))
            }
            // ATB-specific functions: gauge update and turn order
            if (system.combatType == CombatType.ATB) {
                add(generateAtbGaugeUpdateFunction(system, id))
                val strategy = system.turnOrderStrategy
                if (strategy != null) {
                    when (strategy) {
                        TurnOrderStrategy.SPEED_BASED ->
                            add(generateSpeedBasedTurnOrderFunction(id, system.maxCombatants))
                        TurnOrderStrategy.FIXED_ORDER ->
                            add(generateFixedOrderTurnOrderFunction(id, system))
                    }
                }
            }
            // Wave survival: additional wave management functions
            if (system.combatType == CombatType.WAVE_SURVIVAL) {
                val config = system.waveSurvivalConfig ?: WaveSurvivalConfig()
                add(generateStartWaveFunction(id, config))
                add(generateCheckWaveCompleteFunction(id))
                add(generateBetweenWaveFunction(id, config))
                add(generateAdvanceWaveFunction(id, config))
            }
            // Tactical grid: movement range BFS, LOS, optional facing/elevation, AoE
            if (system.combatType == CombatType.TACTICAL_GRID) {
                val cfg = system.tacticalGridConfig ?: TacticalGridConfig()
                add(generateMovementRangeFunction(id, cfg))
                add(generateLineOfSightFunction(id, cfg))
                if (cfg.enableFacing) add(generateFacingBonusFunction(id, cfg))
                if (cfg.enableElevation) add(generateElevationBonusFunction(id, cfg))
                add(generateAoeTargetingFunction(id, cfg))
            }
            // Hook injection: generate hook_<point>_<id>(void) functions for each registered hook
            addAll(generateHookFunctions(system, id, exprVisitor))
        }
    }

    // -------------------------------------------------------------------------
    // combat_request_state_<id>(UINT8 s) — deferred transition request
    // -------------------------------------------------------------------------

    private fun generateRequestStateFunction(id: String): CFunction {
        return CFunction(
            name = "combat_request_state_$id",
            returnType = CVoid,
            params = listOf(CParam("s", CU8)),
            body = listOf(CExprStatement(CBinaryExpr(CVar("_pending_state_$id"), "=", CVar("s")))),
        )
    }

    // -------------------------------------------------------------------------
    // update_combat_<id>(void) — main per-frame update function
    // -------------------------------------------------------------------------

    private fun generateUpdateCombatFunction(
        system: CombatEngineSystem,
        id: String,
        exprVisitor: ExprVisitor,
    ): CFunction {
        val body =
            buildList<CStatement> {
                add(buildDeferredTransitionBlock(id))
                if (system.onVictoryCondition.isNotEmpty()) {
                    add(buildConditionCheck(system.onVictoryCondition, id, 3, exprVisitor))
                }
                if (system.onDefeatCondition.isNotEmpty()) {
                    add(buildConditionCheck(system.onDefeatCondition, id, 4, exprVisitor))
                }
                add(buildStateMachineSwitch(system, id, exprVisitor))
            }

        return CFunction(
            name = "update_combat_$id",
            returnType = CVoid,
            params = emptyList(),
            body = body,
        )
    }

    private fun buildDeferredTransitionBlock(id: String): CIf {
        val pendingVar = "_pending_state_$id"
        val stateVar = "_combat_state_$id"
        return CIf(
            condition = CBinaryExpr(CVar(pendingVar), "!=", CLiteral(0xFF)),
            thenBody =
                listOf(
                    CExprStatement(CBinaryExpr(CVar(stateVar), "=", CVar(pendingVar))),
                    CExprStatement(CBinaryExpr(CVar(pendingVar), "=", CLiteral(0xFF))),
                ),
        )
    }

    private fun buildConditionCheck(
        conditionOps: List<io.github.gbkt.core.ir.ScriptOp>,
        id: String,
        targetState: Int,
        exprVisitor: ExprVisitor,
    ): CStatement {
        val firstIfOp = conditionOps.filterIsInstance<IfOp>().firstOrNull()
        return if (firstIfOp != null) {
            val condition = exprVisitor.visit(firstIfOp.condition)
            CIf(
                condition = condition,
                thenBody =
                    listOf(
                        CExprStatement(
                            CCall("combat_request_state_$id", listOf(CLiteral(targetState)))
                        )
                    ),
            )
        } else {
            val opStatements = conditionOps.map { ScriptOpVisitor.visit(it, exprVisitor) }
            val requestCall =
                CExprStatement(CCall("combat_request_state_$id", listOf(CLiteral(targetState))))
            CIf(
                condition = CBinaryExpr(CLiteral(1), "==", CLiteral(1)),
                thenBody = opStatements + listOf(requestCall),
            )
        }
    }

    // -------------------------------------------------------------------------
    // State machine switch on _combat_state_<id>
    // WAVE_SURVIVAL delegates to buildWaveSurvivalStateMachineSwitch.
    // Other modes: Core states 0-4, custom states 5+, sub-states 64+.
    // -------------------------------------------------------------------------

    private fun buildStateMachineSwitch(
        system: CombatEngineSystem,
        id: String,
        exprVisitor: ExprVisitor,
    ): CSwitch {
        if (system.combatType == CombatType.WAVE_SURVIVAL) {
            return buildWaveSurvivalStateMachineSwitch(system, id, exprVisitor)
        }
        if (system.combatType == CombatType.ATB) {
            return buildAtbStateMachineSwitch(system, id, exprVisitor)
        }

        val cases =
            buildList<CSwitchCase> {
                add(
                    CSwitchCase(
                        value = CLiteral(0),
                        body =
                            listOf(
                                CExprStatement(
                                    CCall("combat_request_state_$id", listOf(CLiteral(1)))
                                ),
                                CBreak,
                            ),
                    )
                )

                val playerTurnBody =
                    buildList<CStatement> {
                        // Hook: BEFORE_TURN fires at the start of player turn
                        buildHookCallSite(system, id, CombatHookPoint.BEFORE_TURN)?.let { add(it) }
                        // Hook: BEFORE_ACTION fires before action dispatch
                        buildHookCallSite(system, id, CombatHookPoint.BEFORE_ACTION)?.let {
                            add(it)
                        }
                        if (system.combatType == CombatType.TURN_BASED) {
                            add(
                                CIf(
                                    condition = CCall("button_pressed", listOf(CVar("J_A"))),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CCall(
                                                    "combat_request_state_$id",
                                                    listOf(CLiteral(2)),
                                                )
                                            )
                                        ),
                                )
                            )
                        }
                        if (system.combatType == CombatType.TACTICAL_GRID) {
                            // TACTICAL_GRID: player selects unit and issues move/attack orders
                            // Input handling deferred to game-level logic; no auto-advance
                            add(CComment("tactical: player selects unit and issues orders"))
                        }
                        // Hook: AFTER_ACTION fires after action dispatch
                        buildHookCallSite(system, id, CombatHookPoint.AFTER_ACTION)?.let { add(it) }
                        add(CBreak)
                    }
                add(CSwitchCase(value = CLiteral(1), body = playerTurnBody))

                // Case 2: ENEMY_TURN — back to PLAYER_TURN with hook injection
                val enemyTurnBody =
                    buildList<CStatement> {
                        // Hook: BEFORE_ACTION fires before enemy action
                        buildHookCallSite(system, id, CombatHookPoint.BEFORE_ACTION)?.let {
                            add(it)
                        }
                        add(CExprStatement(CCall("combat_request_state_$id", listOf(CLiteral(1)))))
                        // Hook: AFTER_ACTION fires after enemy action
                        buildHookCallSite(system, id, CombatHookPoint.AFTER_ACTION)?.let { add(it) }
                        // Hook: AFTER_DAMAGE fires after HP modification (end of ENEMY_TURN action)
                        buildHookCallSite(system, id, CombatHookPoint.AFTER_DAMAGE)?.let { add(it) }
                        // Hook: AFTER_TURN fires at end of enemy turn
                        buildHookCallSite(system, id, CombatHookPoint.AFTER_TURN)?.let { add(it) }
                        add(CBreak)
                    }
                add(CSwitchCase(value = CLiteral(2), body = enemyTurnBody))

                // Case 3: VICTORY — hook ON_VICTORY fires before user onVictoryOps
                val victoryBody =
                    buildList<CStatement> {
                        buildHookCallSite(system, id, CombatHookPoint.ON_VICTORY)?.let { add(it) }
                        addAll(system.onVictoryOps.map { ScriptOpVisitor.visit(it, exprVisitor) })
                        add(CBreak)
                    }
                add(CSwitchCase(value = CLiteral(3), body = victoryBody))

                // Case 4: DEFEAT — hook ON_DEFEAT fires before user onDefeatOps
                val defeatBody =
                    buildList<CStatement> {
                        buildHookCallSite(system, id, CombatHookPoint.ON_DEFEAT)?.let { add(it) }
                        addAll(system.onDefeatOps.map { ScriptOpVisitor.visit(it, exprVisitor) })
                        add(CBreak)
                    }
                add(CSwitchCase(value = CLiteral(4), body = defeatBody))

                system.customStates.forEachIndexed { index, stateId ->
                    val caseIndex = 5 + index
                    add(
                        CSwitchCase(
                            value = CLiteral(caseIndex),
                            body = listOf(CComment("custom: ${stateId.id}"), CBreak),
                        )
                    )
                }

                var subStateIndex = 64
                for ((parent, children) in system.stateHierarchy) {
                    for (child in children) {
                        add(
                            CSwitchCase(
                                value = CLiteral(subStateIndex),
                                body =
                                    listOf(
                                        CComment("sub-state of ${parent.id}: ${child.id}"),
                                        CBreak,
                                    ),
                            )
                        )
                        subStateIndex++
                    }
                }
            }

        return CSwitch(expr = CVar("_combat_state_$id"), cases = cases)
    }

    // -------------------------------------------------------------------------
    // combat_is_in_state_<id>(UINT8 state) — state query helper
    // -------------------------------------------------------------------------

    private fun generateIsInStateFunction(id: String): CFunction {
        return CFunction(
            name = "combat_is_in_state_$id",
            returnType = CU8,
            params = listOf(CParam("state", CU8)),
            body = listOf(CReturn(CBinaryExpr(CVar("_combat_state_$id"), "==", CVar("state")))),
        )
    }

    // -------------------------------------------------------------------------
    // combat_parent_state_<id>(UINT8 state) — hierarchical state parent lookup
    // -------------------------------------------------------------------------

    private fun generateParentStateFunction(system: CombatEngineSystem, id: String): CFunction {
        val subStateCases =
            buildList<CSwitchCase> {
                var subStateIndex = 64
                val coreStateIds = buildCoreStateIndex(system)
                for ((parent, children) in system.stateHierarchy) {
                    val parentId = coreStateIds[parent] ?: 1
                    repeat(children.size) {
                        add(
                            CSwitchCase(
                                value = CLiteral(subStateIndex),
                                body = listOf(CReturn(CLiteral(parentId))),
                            )
                        )
                        subStateIndex++
                    }
                }
                add(CSwitchCase(value = null, body = listOf(CReturn(CVar("state")))))
            }

        return CFunction(
            name = "combat_parent_state_$id",
            returnType = CU8,
            params = listOf(CParam("state", CU8)),
            body = listOf(CSwitch(expr = CVar("state"), cases = subStateCases)),
        )
    }

    private fun buildCoreStateIndex(system: CombatEngineSystem): Map<CombatStateId, Int> {
        val map = mutableMapOf<CombatStateId, Int>()
        map[CombatStateId("INIT")] = 0
        map[CombatStateId("PLAYER_TURN")] = 1
        map[CombatStateId("ENEMY_TURN")] = 2
        map[CombatStateId("VICTORY")] = 3
        map[CombatStateId("DEFEAT")] = 4
        system.customStates.forEachIndexed { index, stateId -> map[stateId] = 5 + index }
        return map
    }

    // -------------------------------------------------------------------------
    // trigger_<id>(void) — resets combat to INIT and clears pending transition
    // -------------------------------------------------------------------------

    private fun generateTriggerFunction(id: String): CFunction {
        return CFunction(
            name = "trigger_$id",
            returnType = CVoid,
            params = emptyList(),
            body =
                listOf(
                    CExprStatement(CBinaryExpr(CVar("_combat_state_$id"), "=", CLiteral(0))),
                    CExprStatement(CBinaryExpr(CVar("_pending_state_$id"), "=", CLiteral(0xFF))),
                ),
        )
    }

    // -------------------------------------------------------------------------
    // damage_<id>(UINT8 src, UINT8 tgt, UINT8 amount) — damage formula dispatcher
    // -------------------------------------------------------------------------

    private fun generateDamageFunction(id: String, formulaFunctionName: String): CFunction {
        return CFunction(
            name = "damage_$id",
            returnType = CU8,
            params = listOf(CParam("src", CU8), CParam("tgt", CU8), CParam("amount", CU8)),
            body =
                listOf(
                    CReturn(
                        CCall(formulaFunctionName, listOf(CVar("src"), CVar("tgt"), CVar("amount")))
                    )
                ),
        )
    }

    // =========================================================================
    // WAVE SURVIVAL STATE MACHINE AND HELPER FUNCTIONS
    //
    // Wave states (WAVE_SURVIVAL mode):
    //   0: INIT        — start wave 1, request WAVE_ACTIVE (1)
    //   1: WAVE_ACTIVE — check_wave_complete each frame; if done -> WAVE_COMPLETE (2)
    //   2: WAVE_COMPLETE — call between_wave (init timer); request BETWEEN_WAVE (3)
    //   3: BETWEEN_WAVE — check timer/player_ready; when ready -> advance_wave (1 or 4)
    //   4: VICTORY     — execute onVictoryOps
    //   5: DEFEAT      — execute onDefeatOps
    //
    // All typed C AST — zero CRawCode.
    // =========================================================================

    private fun buildWaveSurvivalStateMachineSwitch(
        system: CombatEngineSystem,
        id: String,
        exprVisitor: ExprVisitor,
    ): CSwitch {
        val config = system.waveSurvivalConfig ?: WaveSurvivalConfig()
        val cases =
            buildList<CSwitchCase> {
                // Case 0: INIT — start wave 1, advance to WAVE_ACTIVE
                add(
                    CSwitchCase(
                        value = CLiteral(0),
                        body =
                            listOf(
                                CComment("INIT: start wave 1"),
                                CExprStatement(CCall("start_wave_$id", listOf(CLiteral(1)))),
                                CExprStatement(
                                    CCall("combat_request_state_$id", listOf(CLiteral(1)))
                                ),
                                CBreak,
                            ),
                    )
                )

                // Case 1: WAVE_ACTIVE — check for wave complete each frame
                add(
                    CSwitchCase(
                        value = CLiteral(1),
                        body =
                            listOf(
                                CComment("WAVE_ACTIVE: check if all enemies defeated"),
                                CIf(
                                    condition = CCall("check_wave_complete_$id", emptyList()),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CCall(
                                                    "combat_request_state_$id",
                                                    listOf(CLiteral(2)),
                                                )
                                            )
                                        ),
                                ),
                                CBreak,
                            ),
                    )
                )

                // Case 2: WAVE_COMPLETE — run between-wave setup, advance to BETWEEN_WAVE
                add(
                    CSwitchCase(
                        value = CLiteral(2),
                        body =
                            listOf(
                                CComment("WAVE_COMPLETE: run between-wave behavior"),
                                CExprStatement(CCall("between_wave_$id", emptyList())),
                                CExprStatement(
                                    CCall("combat_request_state_$id", listOf(CLiteral(3)))
                                ),
                                CBreak,
                            ),
                    )
                )

                // Case 3: BETWEEN_WAVE — wait for timer or player ready, then advance
                val betweenWaveBody =
                    when (config.nextWaveTrigger) {
                        WaveTrigger.TIMER ->
                            listOf(
                                CComment("BETWEEN_WAVE: decrement timer, advance on 0"),
                                CIf(
                                    condition =
                                        CBinaryExpr(CVar("_wave_${id}_timer"), ">", CLiteral(0)),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CVar("_wave_${id}_timer"),
                                                    "-=",
                                                    CLiteral(1),
                                                )
                                            )
                                        ),
                                    elseBody =
                                        listOf(
                                            CExprStatement(CCall("advance_wave_$id", emptyList()))
                                        ),
                                ),
                                CBreak,
                            )
                        WaveTrigger.PLAYER_READY ->
                            listOf(
                                CComment("BETWEEN_WAVE: wait for J_A (player ready)"),
                                CIf(
                                    condition = CCall("button_pressed", listOf(CVar("J_A"))),
                                    thenBody =
                                        listOf(
                                            CExprStatement(CCall("advance_wave_$id", emptyList()))
                                        ),
                                ),
                                CBreak,
                            )
                    }
                add(CSwitchCase(value = CLiteral(3), body = betweenWaveBody))

                // Case 4: VICTORY
                val victoryBody =
                    system.onVictoryOps.map { ScriptOpVisitor.visit(it, exprVisitor) } +
                        listOf(CBreak)
                add(CSwitchCase(value = CLiteral(4), body = victoryBody))

                // Case 5: DEFEAT
                val defeatBody =
                    system.onDefeatOps.map { ScriptOpVisitor.visit(it, exprVisitor) } +
                        listOf(CBreak)
                add(CSwitchCase(value = CLiteral(5), body = defeatBody))
            }

        return CSwitch(expr = CVar("_combat_state_$id"), cases = cases)
    }

    // =========================================================================
    // ATB STATE MACHINE
    //
    // ATB states:
    //   0: INIT        — initialise ATB globals, request GAUGE_FILL (5)
    //   1: PLAYER_TURN — player acts (gauge >= MAX), back to GAUGE_FILL
    //   2: ENEMY_TURN  — enemy acts, back to GAUGE_FILL
    //   3: VICTORY     — onVictoryOps
    //   4: DEFEAT      — onDefeatOps
    //   5: GAUGE_FILL  — each frame: call update_atb_gauges; when a gauge maxes, go
    // PLAYER/ENEMY_TURN
    //   6: CHARGE_ACT  — CHARGE model only: handle per-action charge countdown
    // =========================================================================

    private fun buildAtbStateMachineSwitch(
        system: CombatEngineSystem,
        id: String,
        exprVisitor: ExprVisitor,
    ): CSwitch {
        val atbCfg = system.atbConfig
        val isCharge = atbCfg?.gaugeModel == AtbGaugeModel.CHARGE
        val cases =
            buildList<CSwitchCase> {
                // Case 0: INIT — request GAUGE_FILL (5)
                add(
                    CSwitchCase(
                        value = CLiteral(0),
                        body =
                            listOf(
                                CComment("ATB INIT: start gauge fill phase"),
                                CExprStatement(
                                    CCall("combat_request_state_$id", listOf(CLiteral(5)))
                                ),
                                CBreak,
                            ),
                    )
                )
                // Case 1: PLAYER_TURN
                add(
                    CSwitchCase(
                        value = CLiteral(1),
                        body =
                            listOf(
                                buildHookCallSite(system, id, CombatHookPoint.BEFORE_ACTION)
                                    ?: CComment("PLAYER_TURN: player selects and executes action"),
                                CExprStatement(
                                    CCall("combat_request_state_$id", listOf(CLiteral(5)))
                                ),
                                CBreak,
                            ),
                    )
                )
                // Case 2: ENEMY_TURN
                add(
                    CSwitchCase(
                        value = CLiteral(2),
                        body =
                            listOf(
                                CComment(
                                    "ENEMY_TURN: execute enemy action then return to gauge fill"
                                ),
                                CExprStatement(
                                    CCall("combat_request_state_$id", listOf(CLiteral(5)))
                                ),
                                CBreak,
                            ),
                    )
                )
                // Case 3: VICTORY
                val victoryBody =
                    buildList<CStatement> {
                        buildHookCallSite(system, id, CombatHookPoint.ON_VICTORY)?.let { add(it) }
                        addAll(system.onVictoryOps.map { ScriptOpVisitor.visit(it, exprVisitor) })
                        add(CBreak)
                    }
                add(CSwitchCase(value = CLiteral(3), body = victoryBody))
                // Case 4: DEFEAT
                val defeatBody =
                    buildList<CStatement> {
                        buildHookCallSite(system, id, CombatHookPoint.ON_DEFEAT)?.let { add(it) }
                        addAll(system.onDefeatOps.map { ScriptOpVisitor.visit(it, exprVisitor) })
                        add(CBreak)
                    }
                add(CSwitchCase(value = CLiteral(4), body = defeatBody))
                // Case 5: GAUGE_FILL — update ATB gauges each frame
                val gaugeFillBody =
                    buildList<CStatement> {
                        if (atbCfg?.waitMode == AtbWaitMode.WAIT) {
                            add(
                                CIf(
                                    condition = CUnaryExpr("!", CVar("_combat_${id}_menu_open")),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CCall("update_atb_gauges_$id", emptyList())
                                            )
                                        ),
                                )
                            )
                        } else {
                            add(CExprStatement(CCall("update_atb_gauges_$id", emptyList())))
                        }
                        add(CBreak)
                    }
                add(CSwitchCase(value = CLiteral(5), body = gaugeFillBody))
                // Case 6: CHARGE_ACT (only for CHARGE gauge model)
                if (isCharge) {
                    add(
                        CSwitchCase(
                            value = CLiteral(6),
                            body =
                                listOf(
                                    CComment("CHARGE model: handle per-action charge countdown"),
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CArrayAccess(
                                                    CVar("_combat_${id}_charge"),
                                                    CVar("_combat_${id}_active_idx"),
                                                ),
                                                ">",
                                                CLiteral(0),
                                            ),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CArrayAccess(
                                                            CVar("_combat_${id}_charge"),
                                                            CVar("_combat_${id}_active_idx"),
                                                        ),
                                                        "-=",
                                                        CLiteral(1),
                                                    )
                                                )
                                            ),
                                        elseBody =
                                            listOf(
                                                CExprStatement(
                                                    CCall(
                                                        "combat_request_state_$id",
                                                        listOf(CLiteral(1)),
                                                    )
                                                )
                                            ),
                                    ),
                                    CBreak,
                                ),
                        )
                    )
                }
            }
        return CSwitch(expr = CVar("_combat_state_$id"), cases = cases)
    }

    // -------------------------------------------------------------------------
    // start_wave_<id>(UINT8 wave_num)
    //
    // Sets _wave_<id>_current = wave_num, then dispatches:
    //   ScriptedWave: switch -> spawn each monster in wave
    //   ProceduralWave: PRNG select count from [min,max], spawn from pool
    // -------------------------------------------------------------------------

    @Suppress("LongMethod")
    private fun generateStartWaveFunction(id: String, config: WaveSurvivalConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Record active wave number"))
                add(CExprStatement(CBinaryExpr(CVar("_wave_${id}_current"), "=", CVar("wave_num"))))

                if (config.waves.isNotEmpty()) {
                    add(CComment("Scripted wave spawning: switch on wave_num"))
                    val waveCases =
                        buildList<CSwitchCase> {
                            for (waveDef in config.waves) {
                                val waveBody =
                                    buildList<CStatement> {
                                        when (val content = waveDef.content) {
                                            is ScriptedWave -> {
                                                add(
                                                    CComment(
                                                        "Wave ${waveDef.waveNumber}: ${content.monsters.size} scripted monsters"
                                                    )
                                                )
                                                content.monsters.forEachIndexed { idx, monsterId ->
                                                    val sanitized =
                                                        monsterId
                                                            .replace('-', '_')
                                                            .replace(' ', '_')
                                                    add(
                                                        CExprStatement(
                                                            CCall(
                                                                "spawn_monster",
                                                                listOf(
                                                                    CLiteral(idx),
                                                                    CVar("_monster_id_$sanitized"),
                                                                ),
                                                            )
                                                        )
                                                    )
                                                }
                                            }
                                            is ProceduralWave -> {
                                                // C89: all var decls before statements
                                                add(
                                                    CComment(
                                                        "Wave ${waveDef.waveNumber}: procedural pool=${content.monsterPool.size}"
                                                    )
                                                )
                                                add(
                                                    CVarDecl(
                                                        name = "_pw_i",
                                                        type = CU8,
                                                        initializer = CLiteral(0),
                                                    )
                                                )
                                                add(
                                                    CVarDecl(
                                                        name = "_pw_count",
                                                        type = CU8,
                                                        initializer =
                                                            CBinaryExpr(
                                                                CLiteral(content.minCount),
                                                                "+",
                                                                CBinaryExpr(
                                                                    CCall("rand", emptyList()),
                                                                    "%",
                                                                    CLiteral(
                                                                        content.maxCount -
                                                                            content.minCount + 1
                                                                    ),
                                                                ),
                                                            ),
                                                    )
                                                )
                                                add(
                                                    CVarDecl(
                                                        name = "_pw_pool_idx",
                                                        type = CU8,
                                                        initializer = CLiteral(0),
                                                    )
                                                )
                                                add(
                                                    CIf(
                                                        condition =
                                                            CBinaryExpr(
                                                                CVar("_pw_i"),
                                                                "<",
                                                                CVar("_pw_count"),
                                                            ),
                                                        thenBody =
                                                            buildList {
                                                                add(
                                                                    CExprStatement(
                                                                        CBinaryExpr(
                                                                            CVar("_pw_pool_idx"),
                                                                            "=",
                                                                            CBinaryExpr(
                                                                                CCall(
                                                                                    "rand",
                                                                                    emptyList(),
                                                                                ),
                                                                                "%",
                                                                                CLiteral(
                                                                                    content
                                                                                        .monsterPool
                                                                                        .size
                                                                                ),
                                                                            ),
                                                                        )
                                                                    )
                                                                )
                                                                add(
                                                                    CExprStatement(
                                                                        CCall(
                                                                            "spawn_monster_from_pool",
                                                                            listOf(
                                                                                CVar("_pw_i"),
                                                                                CVar(
                                                                                    "_pw_pool_idx"
                                                                                ),
                                                                            ),
                                                                        )
                                                                    )
                                                                )
                                                            },
                                                    )
                                                )
                                            }
                                        }
                                        add(CBreak)
                                    }
                                add(
                                    CSwitchCase(
                                        value = CLiteral(waveDef.waveNumber),
                                        body = waveBody,
                                    )
                                )
                            }
                            add(
                                CSwitchCase(
                                    value = null,
                                    body =
                                        listOf(
                                            CComment("No scripted wave for this number"),
                                            CBreak,
                                        ),
                                )
                            )
                        }
                    add(CSwitch(expr = CVar("wave_num"), cases = waveCases))
                } else {
                    add(CComment("No scripted waves — game handles monster spawning externally"))
                }
            }

        return CFunction(
            name = "start_wave_$id",
            returnType = CVoid,
            params = listOf(CParam("wave_num", CU8)),
            body = body,
        )
    }

    // -------------------------------------------------------------------------
    // check_wave_complete_<id>() — returns 1 when _wave_<id>_enemy_count == 0
    // -------------------------------------------------------------------------

    private fun generateCheckWaveCompleteFunction(id: String): CFunction =
        CFunction(
            name = "check_wave_complete_$id",
            returnType = CU8,
            params = emptyList(),
            body =
                listOf(
                    CComment("Wave complete when active enemy count reaches 0"),
                    CReturn(CBinaryExpr(CVar("_wave_${id}_enemy_count"), "==", CLiteral(0))),
                ),
        )

    // -------------------------------------------------------------------------
    // between_wave_<id>() — apply heal, open shop, reset pause timer
    // -------------------------------------------------------------------------

    private fun generateBetweenWaveFunction(id: String, config: WaveSurvivalConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Between-wave behavior"))
                if (config.healBetweenWaves > 0) {
                    add(CComment("Heal party: +${config.healBetweenWaves} HP"))
                    add(
                        CExprStatement(
                            CCall("heal_party", listOf(CLiteral(config.healBetweenWaves)))
                        )
                    )
                }
                if (config.shopAccessBetweenWaves) {
                    add(CComment("Open shop between waves"))
                    add(CExprStatement(CCall("open_shop", emptyList())))
                }
                // Reset pause timer (used by TIMER trigger in BETWEEN_WAVE case)
                add(
                    CExprStatement(
                        CBinaryExpr(CVar("_wave_${id}_timer"), "=", CLiteral(config.pauseDuration))
                    )
                )
            }

        return CFunction(
            name = "between_wave_$id",
            returnType = CVoid,
            params = emptyList(),
            body = body,
        )
    }

    // -------------------------------------------------------------------------
    // advance_wave_<id>() — increment wave counter, start next or trigger victory
    //
    // _wave_<id>_current++
    // if (maxWaves > 0 && current > maxWaves) -> VICTORY (4)
    // else -> start_wave(current), WAVE_ACTIVE (1)
    // maxWaves = 0 = endless mode.
    // -------------------------------------------------------------------------

    private fun generateAdvanceWaveFunction(id: String, config: WaveSurvivalConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Advance to next wave"))
                add(CExprStatement(CBinaryExpr(CVar("_wave_${id}_current"), "+=", CLiteral(1))))

                if (config.maxWaves > 0) {
                    add(CComment("maxWaves=${config.maxWaves}: check victory condition"))
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CVar("_wave_${id}_current"),
                                    ">",
                                    CLiteral(config.maxWaves),
                                ),
                            thenBody =
                                listOf(
                                    CComment("All waves cleared — request VICTORY"),
                                    CExprStatement(
                                        CCall("combat_request_state_$id", listOf(CLiteral(4)))
                                    ),
                                ),
                            elseBody =
                                listOf(
                                    CComment("Start next wave"),
                                    CExprStatement(
                                        CCall("start_wave_$id", listOf(CVar("_wave_${id}_current")))
                                    ),
                                    CExprStatement(
                                        CCall("combat_request_state_$id", listOf(CLiteral(1)))
                                    ),
                                ),
                        )
                    )
                } else {
                    add(CComment("Endless mode (maxWaves=0): no victory check"))
                    add(
                        CExprStatement(CCall("start_wave_$id", listOf(CVar("_wave_${id}_current"))))
                    )
                    add(CExprStatement(CCall("combat_request_state_$id", listOf(CLiteral(1)))))
                }
            }

        return CFunction(
            name = "advance_wave_$id",
            returnType = CVoid,
            params = emptyList(),
            body = body,
        )
    }

    // -------------------------------------------------------------------------
    // ATB function stubs (ATB mode, wired in future plan 06.5-05)
    // -------------------------------------------------------------------------

    /** ATB gauge update: fills gauges each frame, respects WAIT/ACTIVE mode. */
    private fun generateAtbGaugeUpdateFunction(system: CombatEngineSystem, id: String): CFunction {
        val atbCfg = system.atbConfig
        val isWait = atbCfg?.waitMode == AtbWaitMode.WAIT
        val baseRate = atbCfg?.baseGaugeFillRate ?: 4
        val maxGauge = atbCfg?.maxGauge ?: 255
        val body =
            buildList<CStatement> {
                if (isWait) {
                    add(CComment("WAIT mode: skip gauge fill when menu is open"))
                    add(
                        CIf(
                            condition = CVar("_combat_${id}_menu_open"),
                            thenBody = listOf(CReturn()),
                        )
                    )
                }
                add(CComment("Fill gauges: _combat_${id}_gauge[i] += baseRate + (agl[i] >> 2)"))
                add(
                    CFor(
                        init = CVarDecl("i", CU8, CLiteral(0)),
                        condition = CBinaryExpr(CVar("i"), "<", CLiteral(system.maxCombatants)),
                        increment = CUnaryExpr("++", CVar("i")),
                        body =
                            listOf(
                                CIf(
                                    condition =
                                        CArrayAccess(CVar("_combat_${id}_active"), CVar("i")),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CArrayAccess(
                                                        CVar("_combat_${id}_gauge"),
                                                        CVar("i"),
                                                    ),
                                                    "+=",
                                                    CBinaryExpr(
                                                        CLiteral(baseRate),
                                                        "+",
                                                        CBinaryExpr(
                                                            CArrayAccess(
                                                                CVar("_combat_${id}_agl"),
                                                                CVar("i"),
                                                            ),
                                                            ">>",
                                                            CLiteral(2),
                                                        ),
                                                    ),
                                                )
                                            ),
                                            CIf(
                                                condition =
                                                    CBinaryExpr(
                                                        CArrayAccess(
                                                            CVar("_combat_${id}_gauge"),
                                                            CVar("i"),
                                                        ),
                                                        ">=",
                                                        CLiteral(maxGauge),
                                                    ),
                                                thenBody =
                                                    listOf(
                                                        CExprStatement(
                                                            CBinaryExpr(
                                                                CArrayAccess(
                                                                    CVar("_combat_${id}_acted"),
                                                                    CVar("i"),
                                                                ),
                                                                "=",
                                                                CLiteral(1),
                                                            )
                                                        )
                                                    ),
                                            ),
                                        ),
                                )
                            ),
                    )
                )
            }
        return CFunction(
            name = "update_atb_gauges_$id",
            returnType = CVoid,
            params = emptyList(),
            body = body,
        )
    }

    /**
     * Speed-based turn order: sorts `_turn_order_<id>[]` by descending agility using insertion
     * sort. O(n^2) is fine for N<=8 combatants on Game Boy hardware.
     *
     * Algorithm:
     * 1. Initialize turn order array: 0, 1, 2, ..., n-1
     * 2. Insertion sort descending by agl[_turn_order[i]]
     */
    private fun generateSpeedBasedTurnOrderFunction(id: String, maxCombatants: Int): CFunction {
        val body =
            buildList<CStatement> {
                // C89: declare all locals before any statements
                add(CVarDecl("i", CU8, null))
                add(CVarDecl("j", CU8, null))
                add(CVarDecl("key", CU8, null))
                add(CVarDecl("key_agl", CU8, null))

                // Phase 1: Initialize turn order array: _turn_order[i] = i
                add(CComment("Initialize turn order: 0, 1, 2, ..., n-1"))
                add(
                    CFor(
                        init = CVarDecl("i", CU8, CLiteral(0)),
                        condition = CBinaryExpr(CVar("i"), "<", CLiteral(maxCombatants)),
                        increment = CUnaryExpr("++", CVar("i")),
                        body =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CArrayAccess(CVar("_turn_order_$id"), CVar("i")),
                                        "=",
                                        CVar("i"),
                                    )
                                )
                            ),
                    )
                )

                // Phase 2: Insertion sort descending by agl
                add(CComment("Insertion sort descending by agl"))
                add(
                    CFor(
                        init = CVarDecl("i", CU8, CLiteral(1)),
                        condition = CBinaryExpr(CVar("i"), "<", CLiteral(maxCombatants)),
                        increment = CUnaryExpr("++", CVar("i")),
                        body =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("key"),
                                        "=",
                                        CArrayAccess(CVar("_turn_order_$id"), CVar("i")),
                                    )
                                ),
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("key_agl"),
                                        "=",
                                        CArrayAccess(CVar("_combat_${id}_agl"), CVar("key")),
                                    )
                                ),
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("j"),
                                        "=",
                                        CBinaryExpr(CVar("i"), "-", CLiteral(1)),
                                    )
                                ),
                                // while (j < i && agl[_turn_order[j]] < key_agl) {
                                //   _turn_order[j+1] = _turn_order[j]; j--; }
                                // Note: j is UINT8, so j >= 0 always. Use j < i as the guard
                                // (wraps to 255 when j-- underflows, which is > i for i <= 8)
                                CWhile(
                                    condition =
                                        CBinaryExpr(
                                            CBinaryExpr(CVar("j"), "<", CVar("i")),
                                            "&&",
                                            CBinaryExpr(
                                                CArrayAccess(
                                                    CVar("_combat_${id}_agl"),
                                                    CArrayAccess(
                                                        CVar("_turn_order_$id"),
                                                        CVar("j"),
                                                    ),
                                                ),
                                                "<",
                                                CVar("key_agl"),
                                            ),
                                        ),
                                    body =
                                        listOf(
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CArrayAccess(
                                                        CVar("_turn_order_$id"),
                                                        CBinaryExpr(CVar("j"), "+", CLiteral(1)),
                                                    ),
                                                    "=",
                                                    CArrayAccess(
                                                        CVar("_turn_order_$id"),
                                                        CVar("j"),
                                                    ),
                                                )
                                            ),
                                            CExprStatement(
                                                CBinaryExpr(CVar("j"), "-=", CLiteral(1))
                                            ),
                                        ),
                                ),
                                CExprStatement(
                                    CBinaryExpr(
                                        CArrayAccess(
                                            CVar("_turn_order_$id"),
                                            CBinaryExpr(CVar("j"), "+", CLiteral(1)),
                                        ),
                                        "=",
                                        CVar("key"),
                                    )
                                ),
                            ),
                    )
                )
            }
        return CFunction(
            name = "compute_turn_order_$id",
            returnType = CVoid,
            params = emptyList(),
            body = body,
        )
    }

    /**
     * Fixed turn order initialization: assigns `_turn_order_<id>[i] = i` for i in 0..maxCombatants.
     * Fixed order = registration order (players first, then enemies).
     */
    private fun generateFixedOrderTurnOrderFunction(
        id: String,
        system: CombatEngineSystem,
    ): CFunction {
        val maxCombatants = system.maxCombatants
        val body =
            buildList<CStatement> {
                add(CComment("Fixed turn order: registration order (players first, then enemies)"))
                add(
                    CFor(
                        init = CVarDecl("i", CU8, CLiteral(0)),
                        condition = CBinaryExpr(CVar("i"), "<", CLiteral(maxCombatants)),
                        increment = CUnaryExpr("++", CVar("i")),
                        body =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CArrayAccess(CVar("_turn_order_$id"), CVar("i")),
                                        "=",
                                        CVar("i"),
                                    )
                                )
                            ),
                    )
                )
            }
        return CFunction(
            name = "init_turn_order_$id",
            returnType = CVoid,
            params = emptyList(),
            body = body,
        )
    }

    // -------------------------------------------------------------------------
    // ATB global variable declarations
    // Only emitted when combatType == ATB
    // -------------------------------------------------------------------------

    /**
     * Generate ATB-specific global variable declarations. Only returns declarations when
     * [CombatEngineSystem.combatType] == [CombatType.ATB].
     */
    fun generateAtbGlobals(system: CombatEngineSystem): List<CVarDecl> {
        if (system.combatType != CombatType.ATB) return emptyList()
        val id = system.id.replace('-', '_').replace(' ', '_')
        val n = system.maxCombatants
        val atbCfg = system.atbConfig
        return buildList {
            add(CVarDecl(name = "_combat_${id}_gauge", type = CArray(CU8, n), initializer = null))
            add(CVarDecl(name = "_combat_${id}_active", type = CArray(CU8, n), initializer = null))
            add(CVarDecl(name = "_combat_${id}_acted", type = CArray(CU8, n), initializer = null))
            add(CVarDecl(name = "_combat_${id}_agl", type = CArray(CU8, n), initializer = null))
            add(CVarDecl(name = "_combat_${id}_menu_open", type = CU8, initializer = CLiteral(0)))
            add(CVarDecl(name = "_combat_${id}_active_idx", type = CU8, initializer = CLiteral(0)))
            if (atbCfg?.gaugeModel == AtbGaugeModel.CHARGE) {
                add(
                    CVarDecl(
                        name = "_combat_${id}_charge",
                        type = CArray(CU8, n),
                        initializer = null,
                    )
                )
            }
            if (system.turnOrderStrategy != null) {
                add(CVarDecl(name = "_turn_order_$id", type = CArray(CU8, n), initializer = null))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Wave survival global variable declarations
    // -------------------------------------------------------------------------

    /**
     * Generate wave-survival global variable declarations.
     *
     * Returns `_wave_<id>_current` (UINT8, init 0) and `_wave_<id>_timer` (UINT16, init 0) when
     * [CombatEngineSystem.combatType] == [CombatType.WAVE_SURVIVAL]. Returns an empty list for all
     * other combat types (zero overhead).
     */
    fun generateWaveGlobals(system: CombatEngineSystem): List<CVarDecl> {
        if (system.combatType != CombatType.WAVE_SURVIVAL) return emptyList()
        val id = system.id.replace('-', '_').replace(' ', '_')
        return listOf(
            CVarDecl(name = "_wave_${id}_current", type = CU8, initializer = CLiteral(0)),
            CVarDecl(name = "_wave_${id}_timer", type = CU16, initializer = CLiteral(0)),
        )
    }

    // -------------------------------------------------------------------------
    // Tactical grid calculation functions (TACTICAL_GRID mode)
    // All algorithms are iterative — no recursion (Game Boy stack is ~128 bytes).
    // All array indices use UINT8 (grid is at most 16x16 = 256 cells).
    // C89 compliant: all variable declarations precede statements.
    // -------------------------------------------------------------------------

    /**
     * Movement range calculation via iterative BFS with cost tracking.
     *
     * Uses three parallel queue arrays (queue_x, queue_y, queue_cost) and a visited/reachable flat
     * array. BFS explores the 4 cardinal neighbors of each cell, spending movement cost from
     * terrain_cost[terrain[cell]] and marking reachable cells in _tg_<id>_reachable[].
     *
     * Grid cell index = y * gridWidth + x.
     */
    private fun generateMovementRangeFunction(id: String, cfg: TacticalGridConfig): CFunction {
        val w = cfg.gridWidth
        val h = cfg.gridHeight
        val cells = w * h
        // Queue size: 64 is sufficient for a 16x16 grid BFS frontier
        val queueSize = 64
        val body =
            buildList<CStatement> {
                // C89: all locals declared before any statements
                add(CVarDecl("i", CU8, null))
                add(CVarDecl("head", CU8, CLiteral(0)))
                add(CVarDecl("tail", CU8, CLiteral(0)))
                add(CVarDecl("cx", CU8, null))
                add(CVarDecl("cy", CU8, null))
                add(CVarDecl("nx", CU8, null))
                add(CVarDecl("ny", CU8, null))
                add(CVarDecl("cost", CU8, null))
                add(CVarDecl("ncost", CU8, null))
                add(CVarDecl("terrain_idx", CU8, null))
                add(CVarDecl("dir", CU8, null))
                // BFS queue arrays (parallel arrays for x, y, remaining movement)
                add(CVarDecl("queue_x", CArray(CU8, queueSize), null))
                add(CVarDecl("queue_y", CArray(CU8, queueSize), null))
                add(CVarDecl("queue_cost", CArray(CU8, queueSize), null))

                // Step 1: Clear the reachable array
                add(CComment("Clear reachable array"))
                add(
                    CFor(
                        init = CVarDecl("i", CU8, CLiteral(0)),
                        condition = CBinaryExpr(CVar("i"), "<", CLiteral(cells)),
                        increment = CUnaryExpr("++", CVar("i")),
                        body =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CArrayAccess(CVar("_tg_${id}_reachable"), CVar("i")),
                                        "=",
                                        CLiteral(0),
                                    )
                                )
                            ),
                    )
                )

                // Step 2: Seed BFS with unit starting position
                add(CComment("Seed BFS from unit starting position"))
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("queue_x"), CVar("tail")),
                            "=",
                            CArrayAccess(CVar("_tg_${id}_unit_x"), CVar("unit_idx")),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("queue_y"), CVar("tail")),
                            "=",
                            CArrayAccess(CVar("_tg_${id}_unit_y"), CVar("unit_idx")),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("queue_cost"), CVar("tail")),
                            "=",
                            CVar("range"),
                        )
                    )
                )
                // Mark starting cell reachable
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(
                                CVar("_tg_${id}_reachable"),
                                CBinaryExpr(
                                    CBinaryExpr(
                                        CArrayAccess(CVar("queue_y"), CVar("tail")),
                                        "*",
                                        CLiteral(w),
                                    ),
                                    "+",
                                    CArrayAccess(CVar("queue_x"), CVar("tail")),
                                ),
                            ),
                            "=",
                            CLiteral(1),
                        )
                    )
                )
                add(CExprStatement(CUnaryExpr("++", CVar("tail"))))

                // Step 3: BFS loop
                add(CComment("BFS main loop — iterative, no recursion"))
                add(
                    CWhile(
                        condition = CBinaryExpr(CVar("head"), "<", CVar("tail")),
                        body =
                            buildList {
                                // Dequeue current cell
                                add(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("cx"),
                                            "=",
                                            CArrayAccess(CVar("queue_x"), CVar("head")),
                                        )
                                    )
                                )
                                add(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("cy"),
                                            "=",
                                            CArrayAccess(CVar("queue_y"), CVar("head")),
                                        )
                                    )
                                )
                                add(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("cost"),
                                            "=",
                                            CArrayAccess(CVar("queue_cost"), CVar("head")),
                                        )
                                    )
                                )
                                add(CExprStatement(CUnaryExpr("++", CVar("head"))))

                                // Explore 4 neighbors: 0=up, 1=down, 2=left, 3=right
                                add(CComment("Check 4 cardinal neighbors"))
                                add(
                                    CFor(
                                        init = CVarDecl("dir", CU8, CLiteral(0)),
                                        condition = CBinaryExpr(CVar("dir"), "<", CLiteral(4)),
                                        increment = CUnaryExpr("++", CVar("dir")),
                                        body =
                                            buildList {
                                                // Compute neighbor coords based on direction
                                                // dir 0: up    (cx, cy-1)
                                                // dir 1: down  (cx, cy+1)
                                                // dir 2: left  (cx-1, cy)
                                                // dir 3: right (cx+1, cy)
                                                add(
                                                    CExprStatement(
                                                        CBinaryExpr(
                                                            CVar("nx"),
                                                            "=",
                                                            CTernary(
                                                                CBinaryExpr(
                                                                    CVar("dir"),
                                                                    "==",
                                                                    CLiteral(2),
                                                                ),
                                                                CBinaryExpr(
                                                                    CVar("cx"),
                                                                    "-",
                                                                    CLiteral(1),
                                                                ),
                                                                CTernary(
                                                                    CBinaryExpr(
                                                                        CVar("dir"),
                                                                        "==",
                                                                        CLiteral(3),
                                                                    ),
                                                                    CBinaryExpr(
                                                                        CVar("cx"),
                                                                        "+",
                                                                        CLiteral(1),
                                                                    ),
                                                                    CVar("cx"),
                                                                ),
                                                            ),
                                                        )
                                                    )
                                                )
                                                add(
                                                    CExprStatement(
                                                        CBinaryExpr(
                                                            CVar("ny"),
                                                            "=",
                                                            CTernary(
                                                                CBinaryExpr(
                                                                    CVar("dir"),
                                                                    "==",
                                                                    CLiteral(0),
                                                                ),
                                                                CBinaryExpr(
                                                                    CVar("cy"),
                                                                    "-",
                                                                    CLiteral(1),
                                                                ),
                                                                CTernary(
                                                                    CBinaryExpr(
                                                                        CVar("dir"),
                                                                        "==",
                                                                        CLiteral(1),
                                                                    ),
                                                                    CBinaryExpr(
                                                                        CVar("cy"),
                                                                        "+",
                                                                        CLiteral(1),
                                                                    ),
                                                                    CVar("cy"),
                                                                ),
                                                            ),
                                                        )
                                                    )
                                                )
                                                // Bounds check: skip if out of grid
                                                add(
                                                    CIf(
                                                        condition =
                                                            CBinaryExpr(
                                                                CBinaryExpr(
                                                                    CVar("nx"),
                                                                    ">=",
                                                                    CLiteral(w),
                                                                ),
                                                                "||",
                                                                CBinaryExpr(
                                                                    CVar("ny"),
                                                                    ">=",
                                                                    CLiteral(h),
                                                                ),
                                                            ),
                                                        thenBody = listOf(CContinue),
                                                    )
                                                )
                                                // terrain_idx = terrain_cost[terrain[ny*w+nx]]
                                                add(
                                                    CExprStatement(
                                                        CBinaryExpr(
                                                            CVar("terrain_idx"),
                                                            "=",
                                                            CArrayAccess(
                                                                CVar("_tg_${id}_terrain_cost"),
                                                                CArrayAccess(
                                                                    CVar("_tg_${id}_terrain"),
                                                                    CBinaryExpr(
                                                                        CBinaryExpr(
                                                                            CVar("ny"),
                                                                            "*",
                                                                            CLiteral(w),
                                                                        ),
                                                                        "+",
                                                                        CVar("nx"),
                                                                    ),
                                                                ),
                                                            ),
                                                        )
                                                    )
                                                )
                                                // ncost = terrain movement cost
                                                add(
                                                    CExprStatement(
                                                        CBinaryExpr(
                                                            CVar("ncost"),
                                                            "=",
                                                            CVar("terrain_idx"),
                                                        )
                                                    )
                                                )
                                                // Skip impassable (cost == 0) or too expensive
                                                add(
                                                    CIf(
                                                        condition =
                                                            CBinaryExpr(
                                                                CBinaryExpr(
                                                                    CVar("ncost"),
                                                                    "==",
                                                                    CLiteral(0),
                                                                ),
                                                                "||",
                                                                CBinaryExpr(
                                                                    CVar("cost"),
                                                                    "<",
                                                                    CVar("ncost"),
                                                                ),
                                                            ),
                                                        thenBody = listOf(CContinue),
                                                    )
                                                )
                                                // Skip already reachable cells
                                                add(
                                                    CIf(
                                                        condition =
                                                            CBinaryExpr(
                                                                CArrayAccess(
                                                                    CVar("_tg_${id}_reachable"),
                                                                    CBinaryExpr(
                                                                        CBinaryExpr(
                                                                            CVar("ny"),
                                                                            "*",
                                                                            CLiteral(w),
                                                                        ),
                                                                        "+",
                                                                        CVar("nx"),
                                                                    ),
                                                                ),
                                                                "!=",
                                                                CLiteral(0),
                                                            ),
                                                        thenBody = listOf(CContinue),
                                                    )
                                                )
                                                // Mark reachable and enqueue with remaining cost
                                                add(
                                                    CExprStatement(
                                                        CBinaryExpr(
                                                            CArrayAccess(
                                                                CVar("_tg_${id}_reachable"),
                                                                CBinaryExpr(
                                                                    CBinaryExpr(
                                                                        CVar("ny"),
                                                                        "*",
                                                                        CLiteral(w),
                                                                    ),
                                                                    "+",
                                                                    CVar("nx"),
                                                                ),
                                                            ),
                                                            "=",
                                                            CLiteral(1),
                                                        )
                                                    )
                                                )
                                                // Guard against queue overflow
                                                add(
                                                    CIf(
                                                        condition =
                                                            CBinaryExpr(
                                                                CVar("tail"),
                                                                "<",
                                                                CLiteral(queueSize),
                                                            ),
                                                        thenBody =
                                                            listOf(
                                                                CExprStatement(
                                                                    CBinaryExpr(
                                                                        CArrayAccess(
                                                                            CVar("queue_x"),
                                                                            CVar("tail"),
                                                                        ),
                                                                        "=",
                                                                        CVar("nx"),
                                                                    )
                                                                ),
                                                                CExprStatement(
                                                                    CBinaryExpr(
                                                                        CArrayAccess(
                                                                            CVar("queue_y"),
                                                                            CVar("tail"),
                                                                        ),
                                                                        "=",
                                                                        CVar("ny"),
                                                                    )
                                                                ),
                                                                CExprStatement(
                                                                    CBinaryExpr(
                                                                        CArrayAccess(
                                                                            CVar("queue_cost"),
                                                                            CVar("tail"),
                                                                        ),
                                                                        "=",
                                                                        CBinaryExpr(
                                                                            CVar("cost"),
                                                                            "-",
                                                                            CVar("ncost"),
                                                                        ),
                                                                    )
                                                                ),
                                                                CExprStatement(
                                                                    CUnaryExpr("++", CVar("tail"))
                                                                ),
                                                            ),
                                                    )
                                                )
                                            },
                                    )
                                )
                            },
                    )
                )
            }
        return CFunction(
            name = "calc_movement_range_$id",
            returnType = CVoid,
            params = listOf(CParam("unit_idx", CU8), CParam("range", CU8)),
            body = body,
        )
    }

    /**
     * Line-of-sight check using Bresenham's line algorithm (tile walk).
     *
     * Returns 1 if there is clear line-of-sight from [from] to [to] (no WALL terrain between them),
     * or 0 if blocked. Cell indices are flat: index = y * gridWidth + x.
     *
     * Uses INT8 for signed dx/dy/sx/sy/err/e2. The wall terrain type index is the first terrain
     * type with movementCost == -1; if no wall type is configured the wall check is skipped.
     */
    private fun generateLineOfSightFunction(id: String, cfg: TacticalGridConfig): CFunction {
        val w = cfg.gridWidth
        // Find wall terrain index (movementCost == -1 means impassable)
        val wallIdx = cfg.terrainTypes.indexOfFirst { it.movementCost == -1 }
        val body =
            buildList<CStatement> {
                // C89: declare all locals first
                add(CVarDecl("x0", CI8, null))
                add(CVarDecl("y0", CI8, null))
                add(CVarDecl("x1", CI8, null))
                add(CVarDecl("y1", CI8, null))
                add(CVarDecl("dx", CI8, null))
                add(CVarDecl("dy", CI8, null))
                add(CVarDecl("sx", CI8, null))
                add(CVarDecl("sy", CI8, null))
                add(CVarDecl("err", CI8, null))
                add(CVarDecl("e2", CI8, null))

                // Decompose flat indices to (x, y) coords
                add(
                    CExprStatement(
                        CBinaryExpr(CVar("x0"), "=", CBinaryExpr(CVar("from"), "%", CLiteral(w)))
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(CVar("y0"), "=", CBinaryExpr(CVar("from"), "/", CLiteral(w)))
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(CVar("x1"), "=", CBinaryExpr(CVar("to"), "%", CLiteral(w)))
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(CVar("y1"), "=", CBinaryExpr(CVar("to"), "/", CLiteral(w)))
                    )
                )

                // Bresenham init: dx = abs(x1-x0), dy = abs(y1-y0), sx, sy, err
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("dx"),
                            "=",
                            CCall("abs", listOf(CBinaryExpr(CVar("x1"), "-", CVar("x0")))),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("dy"),
                            "=",
                            CCall("abs", listOf(CBinaryExpr(CVar("y1"), "-", CVar("y0")))),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("sx"),
                            "=",
                            CTernary(
                                CBinaryExpr(CVar("x0"), "<", CVar("x1")),
                                CLiteral(1),
                                CLiteral(-1),
                            ),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("sy"),
                            "=",
                            CTernary(
                                CBinaryExpr(CVar("y0"), "<", CVar("y1")),
                                CLiteral(1),
                                CLiteral(-1),
                            ),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(CVar("err"), "=", CBinaryExpr(CVar("dx"), "-", CVar("dy")))
                    )
                )

                // Tile-walk loop
                add(
                    CWhile(
                        condition = CLiteral(1),
                        body =
                            buildList {
                                // Reached target — visible
                                add(
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CBinaryExpr(CVar("x0"), "==", CVar("x1")),
                                                "&&",
                                                CBinaryExpr(CVar("y0"), "==", CVar("y1")),
                                            ),
                                        thenBody = listOf(CReturn(CLiteral(1))),
                                    )
                                )
                                // Wall check: if wall terrain configured, check blocking
                                if (wallIdx >= 0) {
                                    add(
                                        CIf(
                                            condition =
                                                CBinaryExpr(
                                                    CArrayAccess(
                                                        CVar("_tg_${id}_terrain"),
                                                        CBinaryExpr(
                                                            CBinaryExpr(
                                                                CVar("y0"),
                                                                "*",
                                                                CLiteral(w),
                                                            ),
                                                            "+",
                                                            CVar("x0"),
                                                        ),
                                                    ),
                                                    "==",
                                                    CLiteral(wallIdx),
                                                ),
                                            thenBody = listOf(CReturn(CLiteral(0))),
                                        )
                                    )
                                }
                                // Bresenham step
                                add(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("e2"),
                                            "=",
                                            CBinaryExpr(CVar("err"), "*", CLiteral(2)),
                                        )
                                    )
                                )
                                add(
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CVar("e2"),
                                                ">",
                                                CUnaryExpr("-", CVar("dy")),
                                            ),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(CVar("err"), "-=", CVar("dy"))
                                                ),
                                                CExprStatement(
                                                    CBinaryExpr(CVar("x0"), "+=", CVar("sx"))
                                                ),
                                            ),
                                    )
                                )
                                add(
                                    CIf(
                                        condition = CBinaryExpr(CVar("e2"), "<", CVar("dx")),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(CVar("err"), "+=", CVar("dx"))
                                                ),
                                                CExprStatement(
                                                    CBinaryExpr(CVar("y0"), "+=", CVar("sy"))
                                                ),
                                            ),
                                    )
                                )
                            },
                    )
                )
                add(CReturn(CLiteral(1)))
            }
        return CFunction(
            name = "check_line_of_sight_$id",
            returnType = CU8,
            params = listOf(CParam("from", CU8), CParam("to", CU8)),
            body = body,
        )
    }

    /**
     * Facing (flanking/backstab) bonus calculation.
     *
     * Compares the attacker's approach direction against the defender's facing direction stored in
     * _tg_<id>_facing[]. Returns backstabBonus (50) when directly behind, flankingBonus (25) when
     * from the side, and 0 when attacking from the front.
     *
     * Facing values: 0=UP, 1=RIGHT, 2=DOWN, 3=LEFT (matching FacingDirection ordinals).
     */
    private fun generateFacingBonusFunction(id: String, cfg: TacticalGridConfig): CFunction {
        val w = cfg.gridWidth
        val flanking = cfg.flankingBonus
        val backstab = cfg.backstabBonus
        val body =
            buildList<CStatement> {
                // C89: declare all locals before statements
                add(CVarDecl("ax", CU8, null))
                add(CVarDecl("ay", CU8, null))
                add(CVarDecl("dx", CU8, null))
                add(CVarDecl("dy", CU8, null))
                add(CVarDecl("approach_dir", CU8, null))
                add(CVarDecl("face_dir", CU8, null))
                add(CVarDecl("diff", CU8, null))

                // Decompose flat indices to (x, y)
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("ax"),
                            "=",
                            CBinaryExpr(CVar("attacker"), "%", CLiteral(w)),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("ay"),
                            "=",
                            CBinaryExpr(CVar("attacker"), "/", CLiteral(w)),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("dx"),
                            "=",
                            CBinaryExpr(CVar("defender"), "%", CLiteral(w)),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("dy"),
                            "=",
                            CBinaryExpr(CVar("defender"), "/", CLiteral(w)),
                        )
                    )
                )

                // Determine attacker's approach direction (dominant axis)
                // if |ax - dx| > |ay - dy|: horizontal approach → LEFT(2) or RIGHT(1)
                // else: vertical approach → UP(0) or DOWN(3)
                add(CComment("Determine approach direction from dominant axis"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CCall("abs", listOf(CBinaryExpr(CVar("ax"), "-", CVar("dx")))),
                                ">",
                                CCall("abs", listOf(CBinaryExpr(CVar("ay"), "-", CVar("dy")))),
                            ),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("approach_dir"),
                                        "=",
                                        CTernary(
                                            CBinaryExpr(CVar("ax"), ">", CVar("dx")),
                                            CLiteral(
                                                3
                                            ), // attacker is to the RIGHT of defender → approaches
                                            // from RIGHT
                                            CLiteral(
                                                1
                                            ), // attacker is to the LEFT of defender → approaches
                                            // from LEFT
                                        ),
                                    )
                                )
                            ),
                        elseBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("approach_dir"),
                                        "=",
                                        CTernary(
                                            CBinaryExpr(CVar("ay"), ">", CVar("dy")),
                                            CLiteral(
                                                2
                                            ), // attacker is below defender → approaches from
                                            // DOWN/SOUTH
                                            CLiteral(
                                                0
                                            ), // attacker is above defender → approaches from
                                            // UP/NORTH
                                        ),
                                    )
                                )
                            ),
                    )
                )

                // face_dir = _tg_<id>_facing[defender]
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("face_dir"),
                            "=",
                            CArrayAccess(CVar("_tg_${id}_facing"), CVar("defender")),
                        )
                    )
                )

                // diff = (approach_dir + 4 - face_dir) % 4
                // diff==2: directly behind → backstab
                // diff==1 or diff==3: from the side → flanking
                // diff==0: from the front → no bonus
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("diff"),
                            "=",
                            CBinaryExpr(
                                CBinaryExpr(
                                    CBinaryExpr(CVar("approach_dir"), "+", CLiteral(4)),
                                    "-",
                                    CVar("face_dir"),
                                ),
                                "%",
                                CLiteral(4),
                            ),
                        )
                    )
                )

                add(
                    CIf(
                        condition = CBinaryExpr(CVar("diff"), "==", CLiteral(2)),
                        thenBody = listOf(CReturn(CLiteral(backstab))),
                    )
                )
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(CVar("diff"), "==", CLiteral(1)),
                                "||",
                                CBinaryExpr(CVar("diff"), "==", CLiteral(3)),
                            ),
                        thenBody = listOf(CReturn(CLiteral(flanking))),
                    )
                )
                add(CReturn(CLiteral(0)))
            }
        return CFunction(
            name = "calc_facing_bonus_$id",
            returnType = CU8,
            params = listOf(CParam("attacker", CU8), CParam("defender", CU8)),
            body = body,
        )
    }

    /**
     * Elevation advantage bonus calculation.
     *
     * Returns (a_elev - d_elev) * elevationDamageBonus when attacker is on higher ground, 0
     * otherwise. Elevation is stored per-cell in _tg_<id>_elevation[].
     */
    private fun generateElevationBonusFunction(id: String, cfg: TacticalGridConfig): CFunction {
        val bonus = cfg.elevationDamageBonus
        val body =
            buildList<CStatement> {
                // C89: declare locals first
                add(CVarDecl("a_elev", CU8, null))
                add(CVarDecl("d_elev", CU8, null))

                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("a_elev"),
                            "=",
                            CArrayAccess(CVar("_tg_${id}_elevation"), CVar("attacker")),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("d_elev"),
                            "=",
                            CArrayAccess(CVar("_tg_${id}_elevation"), CVar("defender")),
                        )
                    )
                )

                // Return height advantage * bonus, or 0 if same level or lower
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("a_elev"), ">", CVar("d_elev")),
                        thenBody =
                            listOf(
                                CReturn(
                                    CBinaryExpr(
                                        CBinaryExpr(CVar("a_elev"), "-", CVar("d_elev")),
                                        "*",
                                        CLiteral(bonus),
                                    )
                                )
                            ),
                    )
                )
                add(CReturn(CLiteral(0)))
            }
        return CFunction(
            name = "calc_elevation_bonus_$id",
            returnType = CU8,
            params = listOf(CParam("attacker", CU8), CParam("defender", CU8)),
            body = body,
        )
    }

    /**
     * AoE target marking based on shape.
     *
     * Receives center cell index, radius, and shape (UINT8 enum value). Marks affected cells in
     * _tg_<id>_aoe_targets[]. Shape values match AoeShape ordinals: SINGLE=0, LINE=1, CROSS=2,
     * DIAMOND=3, SQUARE=4.
     *
     * All patterns use iterative loops — no recursion. Bounds checks ensure we never write outside
     * the grid (grid is w x h cells).
     */
    private fun generateAoeTargetingFunction(id: String, cfg: TacticalGridConfig): CFunction {
        val w = cfg.gridWidth
        val h = cfg.gridHeight
        val body =
            buildList<CStatement> {
                // C89: declare all locals before statements
                add(CVarDecl("cx", CU8, null))
                add(CVarDecl("cy", CU8, null))
                add(CVarDecl("i", CU8, null))
                add(CVarDecl("nx", CU8, null))
                add(CVarDecl("ny", CU8, null))
                // signed offsets for diamond/square iteration
                add(CVarDecl("odx", CI8, null))
                add(CVarDecl("ody", CI8, null))

                // Decompose center flat index to (cx, cy)
                add(
                    CExprStatement(
                        CBinaryExpr(CVar("cx"), "=", CBinaryExpr(CVar("center"), "%", CLiteral(w)))
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(CVar("cy"), "=", CBinaryExpr(CVar("center"), "/", CLiteral(w)))
                    )
                )

                // Dispatch by shape
                add(
                    CSwitch(
                        expr = CVar("shape"),
                        cases =
                            listOf(
                                // SINGLE (0): mark only center
                                CSwitchCase(
                                    value = CLiteral(0),
                                    body =
                                        listOf(
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CArrayAccess(
                                                        CVar("_tg_${id}_aoe_targets"),
                                                        CVar("center"),
                                                    ),
                                                    "=",
                                                    CLiteral(1),
                                                )
                                            ),
                                            CBreak,
                                        ),
                                ),
                                // LINE (1): mark cells in the facing direction, up to radius steps
                                // Simplified: mark cells along the Y axis (northward) from center
                                CSwitchCase(
                                    value = CLiteral(1),
                                    body =
                                        listOf(
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CArrayAccess(
                                                        CVar("_tg_${id}_aoe_targets"),
                                                        CVar("center"),
                                                    ),
                                                    "=",
                                                    CLiteral(1),
                                                )
                                            ),
                                            CFor(
                                                init = CVarDecl("i", CU8, CLiteral(1)),
                                                condition =
                                                    CBinaryExpr(CVar("i"), "<=", CVar("radius")),
                                                increment = CUnaryExpr("++", CVar("i")),
                                                body =
                                                    listOf(
                                                        CIf(
                                                            condition =
                                                                CBinaryExpr(
                                                                    CVar("cy"),
                                                                    ">=",
                                                                    CVar("i"),
                                                                ),
                                                            thenBody =
                                                                listOf(
                                                                    CExprStatement(
                                                                        CBinaryExpr(
                                                                            CArrayAccess(
                                                                                CVar(
                                                                                    "_tg_${id}_aoe_targets"
                                                                                ),
                                                                                CBinaryExpr(
                                                                                    CBinaryExpr(
                                                                                        CBinaryExpr(
                                                                                            CVar(
                                                                                                "cy"
                                                                                            ),
                                                                                            "-",
                                                                                            CVar(
                                                                                                "i"
                                                                                            ),
                                                                                        ),
                                                                                        "*",
                                                                                        CLiteral(w),
                                                                                    ),
                                                                                    "+",
                                                                                    CVar("cx"),
                                                                                ),
                                                                            ),
                                                                            "=",
                                                                            CLiteral(1),
                                                                        )
                                                                    )
                                                                ),
                                                        )
                                                    ),
                                            ),
                                            CBreak,
                                        ),
                                ),
                                // CROSS (2): center + radius cells in each cardinal direction
                                CSwitchCase(
                                    value = CLiteral(2),
                                    body =
                                        listOf(
                                            // Mark center
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CArrayAccess(
                                                        CVar("_tg_${id}_aoe_targets"),
                                                        CVar("center"),
                                                    ),
                                                    "=",
                                                    CLiteral(1),
                                                )
                                            ),
                                            // Extend in 4 directions
                                            CFor(
                                                init = CVarDecl("i", CU8, CLiteral(1)),
                                                condition =
                                                    CBinaryExpr(CVar("i"), "<=", CVar("radius")),
                                                increment = CUnaryExpr("++", CVar("i")),
                                                body =
                                                    listOf(
                                                        // UP: cy - i
                                                        CIf(
                                                            condition =
                                                                CBinaryExpr(
                                                                    CVar("cy"),
                                                                    ">=",
                                                                    CVar("i"),
                                                                ),
                                                            thenBody =
                                                                listOf(
                                                                    CExprStatement(
                                                                        CBinaryExpr(
                                                                            CArrayAccess(
                                                                                CVar(
                                                                                    "_tg_${id}_aoe_targets"
                                                                                ),
                                                                                CBinaryExpr(
                                                                                    CBinaryExpr(
                                                                                        CBinaryExpr(
                                                                                            CVar(
                                                                                                "cy"
                                                                                            ),
                                                                                            "-",
                                                                                            CVar(
                                                                                                "i"
                                                                                            ),
                                                                                        ),
                                                                                        "*",
                                                                                        CLiteral(w),
                                                                                    ),
                                                                                    "+",
                                                                                    CVar("cx"),
                                                                                ),
                                                                            ),
                                                                            "=",
                                                                            CLiteral(1),
                                                                        )
                                                                    )
                                                                ),
                                                        ),
                                                        // DOWN: cy + i
                                                        CIf(
                                                            condition =
                                                                CBinaryExpr(
                                                                    CBinaryExpr(
                                                                        CVar("cy"),
                                                                        "+",
                                                                        CVar("i"),
                                                                    ),
                                                                    "<",
                                                                    CLiteral(h),
                                                                ),
                                                            thenBody =
                                                                listOf(
                                                                    CExprStatement(
                                                                        CBinaryExpr(
                                                                            CArrayAccess(
                                                                                CVar(
                                                                                    "_tg_${id}_aoe_targets"
                                                                                ),
                                                                                CBinaryExpr(
                                                                                    CBinaryExpr(
                                                                                        CBinaryExpr(
                                                                                            CVar(
                                                                                                "cy"
                                                                                            ),
                                                                                            "+",
                                                                                            CVar(
                                                                                                "i"
                                                                                            ),
                                                                                        ),
                                                                                        "*",
                                                                                        CLiteral(w),
                                                                                    ),
                                                                                    "+",
                                                                                    CVar("cx"),
                                                                                ),
                                                                            ),
                                                                            "=",
                                                                            CLiteral(1),
                                                                        )
                                                                    )
                                                                ),
                                                        ),
                                                        // LEFT: cx - i
                                                        CIf(
                                                            condition =
                                                                CBinaryExpr(
                                                                    CVar("cx"),
                                                                    ">=",
                                                                    CVar("i"),
                                                                ),
                                                            thenBody =
                                                                listOf(
                                                                    CExprStatement(
                                                                        CBinaryExpr(
                                                                            CArrayAccess(
                                                                                CVar(
                                                                                    "_tg_${id}_aoe_targets"
                                                                                ),
                                                                                CBinaryExpr(
                                                                                    CBinaryExpr(
                                                                                        CVar("cy"),
                                                                                        "*",
                                                                                        CLiteral(w),
                                                                                    ),
                                                                                    "+",
                                                                                    CBinaryExpr(
                                                                                        CVar("cx"),
                                                                                        "-",
                                                                                        CVar("i"),
                                                                                    ),
                                                                                ),
                                                                            ),
                                                                            "=",
                                                                            CLiteral(1),
                                                                        )
                                                                    )
                                                                ),
                                                        ),
                                                        // RIGHT: cx + i
                                                        CIf(
                                                            condition =
                                                                CBinaryExpr(
                                                                    CBinaryExpr(
                                                                        CVar("cx"),
                                                                        "+",
                                                                        CVar("i"),
                                                                    ),
                                                                    "<",
                                                                    CLiteral(w),
                                                                ),
                                                            thenBody =
                                                                listOf(
                                                                    CExprStatement(
                                                                        CBinaryExpr(
                                                                            CArrayAccess(
                                                                                CVar(
                                                                                    "_tg_${id}_aoe_targets"
                                                                                ),
                                                                                CBinaryExpr(
                                                                                    CBinaryExpr(
                                                                                        CVar("cy"),
                                                                                        "*",
                                                                                        CLiteral(w),
                                                                                    ),
                                                                                    "+",
                                                                                    CBinaryExpr(
                                                                                        CVar("cx"),
                                                                                        "+",
                                                                                        CVar("i"),
                                                                                    ),
                                                                                ),
                                                                            ),
                                                                            "=",
                                                                            CLiteral(1),
                                                                        )
                                                                    )
                                                                ),
                                                        ),
                                                    ),
                                            ),
                                            CBreak,
                                        ),
                                ),
                                // DIAMOND (3): Manhattan distance <= radius
                                // Iterates over (-radius..+radius) x (-radius..+radius) checking
                                // |odx|+|ody| <= radius
                                CSwitchCase(
                                    value = CLiteral(3),
                                    body =
                                        listOf(
                                            CFor(
                                                init =
                                                    CVarDecl(
                                                        "odx",
                                                        CI8,
                                                        CUnaryExpr("-", CVar("radius")),
                                                    ),
                                                condition =
                                                    CBinaryExpr(CVar("odx"), "<=", CVar("radius")),
                                                increment = CUnaryExpr("++", CVar("odx")),
                                                body =
                                                    listOf(
                                                        CFor(
                                                            init =
                                                                CVarDecl(
                                                                    "ody",
                                                                    CI8,
                                                                    CUnaryExpr("-", CVar("radius")),
                                                                ),
                                                            condition =
                                                                CBinaryExpr(
                                                                    CVar("ody"),
                                                                    "<=",
                                                                    CVar("radius"),
                                                                ),
                                                            increment =
                                                                CUnaryExpr("++", CVar("ody")),
                                                            body =
                                                                listOf(
                                                                    // Manhattan distance check
                                                                    CIf(
                                                                        condition =
                                                                            CBinaryExpr(
                                                                                CBinaryExpr(
                                                                                    CCall(
                                                                                        "abs",
                                                                                        listOf(
                                                                                            CVar(
                                                                                                "odx"
                                                                                            )
                                                                                        ),
                                                                                    ),
                                                                                    "+",
                                                                                    CCall(
                                                                                        "abs",
                                                                                        listOf(
                                                                                            CVar(
                                                                                                "ody"
                                                                                            )
                                                                                        ),
                                                                                    ),
                                                                                ),
                                                                                ">",
                                                                                CVar("radius"),
                                                                            ),
                                                                        thenBody =
                                                                            listOf(CContinue),
                                                                    ),
                                                                    // Compute target coords (as
                                                                    // unsigned via cast)
                                                                    CExprStatement(
                                                                        CBinaryExpr(
                                                                            CVar("nx"),
                                                                            "=",
                                                                            CBinaryExpr(
                                                                                CVar("cx"),
                                                                                "+",
                                                                                CVar("odx"),
                                                                            ),
                                                                        )
                                                                    ),
                                                                    CExprStatement(
                                                                        CBinaryExpr(
                                                                            CVar("ny"),
                                                                            "=",
                                                                            CBinaryExpr(
                                                                                CVar("cy"),
                                                                                "+",
                                                                                CVar("ody"),
                                                                            ),
                                                                        )
                                                                    ),
                                                                    // Bounds check (nx < w uses
                                                                    // unsigned wraparound)
                                                                    CIf(
                                                                        condition =
                                                                            CBinaryExpr(
                                                                                CBinaryExpr(
                                                                                    CVar("nx"),
                                                                                    ">=",
                                                                                    CLiteral(w),
                                                                                ),
                                                                                "||",
                                                                                CBinaryExpr(
                                                                                    CVar("ny"),
                                                                                    ">=",
                                                                                    CLiteral(h),
                                                                                ),
                                                                            ),
                                                                        thenBody =
                                                                            listOf(CContinue),
                                                                    ),
                                                                    CExprStatement(
                                                                        CBinaryExpr(
                                                                            CArrayAccess(
                                                                                CVar(
                                                                                    "_tg_${id}_aoe_targets"
                                                                                ),
                                                                                CBinaryExpr(
                                                                                    CBinaryExpr(
                                                                                        CVar("ny"),
                                                                                        "*",
                                                                                        CLiteral(w),
                                                                                    ),
                                                                                    "+",
                                                                                    CVar("nx"),
                                                                                ),
                                                                            ),
                                                                            "=",
                                                                            CLiteral(1),
                                                                        )
                                                                    ),
                                                                ),
                                                        )
                                                    ),
                                            ),
                                            CBreak,
                                        ),
                                ),
                                // SQUARE (4): Chebyshev distance <= radius (all cells in bounding
                                // box)
                                CSwitchCase(
                                    value = CLiteral(4),
                                    body =
                                        listOf(
                                            CFor(
                                                init =
                                                    CVarDecl(
                                                        "odx",
                                                        CI8,
                                                        CUnaryExpr("-", CVar("radius")),
                                                    ),
                                                condition =
                                                    CBinaryExpr(CVar("odx"), "<=", CVar("radius")),
                                                increment = CUnaryExpr("++", CVar("odx")),
                                                body =
                                                    listOf(
                                                        CFor(
                                                            init =
                                                                CVarDecl(
                                                                    "ody",
                                                                    CI8,
                                                                    CUnaryExpr("-", CVar("radius")),
                                                                ),
                                                            condition =
                                                                CBinaryExpr(
                                                                    CVar("ody"),
                                                                    "<=",
                                                                    CVar("radius"),
                                                                ),
                                                            increment =
                                                                CUnaryExpr("++", CVar("ody")),
                                                            body =
                                                                listOf(
                                                                    CExprStatement(
                                                                        CBinaryExpr(
                                                                            CVar("nx"),
                                                                            "=",
                                                                            CBinaryExpr(
                                                                                CVar("cx"),
                                                                                "+",
                                                                                CVar("odx"),
                                                                            ),
                                                                        )
                                                                    ),
                                                                    CExprStatement(
                                                                        CBinaryExpr(
                                                                            CVar("ny"),
                                                                            "=",
                                                                            CBinaryExpr(
                                                                                CVar("cy"),
                                                                                "+",
                                                                                CVar("ody"),
                                                                            ),
                                                                        )
                                                                    ),
                                                                    CIf(
                                                                        condition =
                                                                            CBinaryExpr(
                                                                                CBinaryExpr(
                                                                                    CVar("nx"),
                                                                                    ">=",
                                                                                    CLiteral(w),
                                                                                ),
                                                                                "||",
                                                                                CBinaryExpr(
                                                                                    CVar("ny"),
                                                                                    ">=",
                                                                                    CLiteral(h),
                                                                                ),
                                                                            ),
                                                                        thenBody =
                                                                            listOf(CContinue),
                                                                    ),
                                                                    CExprStatement(
                                                                        CBinaryExpr(
                                                                            CArrayAccess(
                                                                                CVar(
                                                                                    "_tg_${id}_aoe_targets"
                                                                                ),
                                                                                CBinaryExpr(
                                                                                    CBinaryExpr(
                                                                                        CVar("ny"),
                                                                                        "*",
                                                                                        CLiteral(w),
                                                                                    ),
                                                                                    "+",
                                                                                    CVar("nx"),
                                                                                ),
                                                                            ),
                                                                            "=",
                                                                            CLiteral(1),
                                                                        )
                                                                    ),
                                                                ),
                                                        )
                                                    ),
                                            ),
                                            CBreak,
                                        ),
                                ),
                            ),
                    )
                )
            }
        return CFunction(
            name = "calc_aoe_targets_$id",
            returnType = CVoid,
            params = listOf(CParam("center", CU8), CParam("radius", CU8), CParam("shape", CU8)),
            body = body,
        )
    }

    // -------------------------------------------------------------------------
    // _combat_<id>_hooks_enabled — UINT8 global (default 1) for runtime hook disable
    // Only emitted when at least one hook is registered.
    // -------------------------------------------------------------------------

    /**
     * Generate the `_combat_<id>_hooks_enabled` global variable declaration.
     *
     * Returns a single-element list containing a [CVarDecl] for `_combat_<id>_hooks_enabled`
     * initialized to 1 when [CombatEngineSystem.combatHooks] is non-empty. Returns an empty list
     * when no hooks are registered (zero overhead).
     */
    fun generateHookGlobals(system: CombatEngineSystem): List<CVarDecl> {
        if (system.combatHooks.isEmpty()) return emptyList()
        val id = system.id.replace('-', '_').replace(' ', '_')
        return listOf(
            CVarDecl(name = "_combat_${id}_hooks_enabled", type = CU8, initializer = CLiteral(1))
        )
    }

    // -------------------------------------------------------------------------
    // Hook injection: hook_<point>_<id>(void) functions
    //
    // For each registered hook point in combatHooks, a dedicated C function is generated.
    // Each function body executes the registered ScriptOps using ScriptOpVisitor.
    // Call sites are injected into the relevant state machine cases wrapped in a
    // hooks_enabled check.
    // -------------------------------------------------------------------------

    /**
     * Generate `hook_<point>_<id>(void)` functions for each registered hook in
     * [CombatEngineSystem.combatHooks].
     *
     * Returns an empty list when [CombatEngineSystem.combatHooks] is empty (zero overhead).
     */
    private fun generateHookFunctions(
        system: CombatEngineSystem,
        id: String,
        exprVisitor: ExprVisitor,
    ): List<CFunction> {
        if (system.combatHooks.isEmpty()) return emptyList()
        return system.combatHooks.map { (point, ops) ->
            val fnName = hookFunctionName(point, id)
            val body = ops.map { op -> ScriptOpVisitor.visit(op, exprVisitor) }
            CFunction(name = fnName, returnType = CVoid, params = emptyList(), body = body)
        }
    }

    /**
     * Build a hook call site: `if (_combat_<id>_hooks_enabled) { hook_<point>_<id>(); }`
     *
     * Returns null when the given [point] has no registered ops in
     * [CombatEngineSystem.combatHooks].
     */
    private fun buildHookCallSite(
        system: CombatEngineSystem,
        id: String,
        point: CombatHookPoint,
    ): CIf? {
        val ops = system.combatHooks[point] ?: return null
        if (ops.isEmpty()) return null
        val fnName = hookFunctionName(point, id)
        return CIf(
            condition = CVar("_combat_${id}_hooks_enabled"),
            thenBody = listOf(CExprStatement(CCall(fnName, emptyList()))),
        )
    }

    /** Map a [CombatHookPoint] enum value to a snake_case C function name for system [id]. */
    private fun hookFunctionName(point: CombatHookPoint, id: String): String {
        val pointName =
            when (point) {
                CombatHookPoint.BEFORE_ACTION -> "before_action"
                CombatHookPoint.AFTER_ACTION -> "after_action"
                CombatHookPoint.AFTER_DAMAGE -> "after_damage"
                CombatHookPoint.BEFORE_TURN -> "before_turn"
                CombatHookPoint.AFTER_TURN -> "after_turn"
                CombatHookPoint.ON_VICTORY -> "on_victory"
                CombatHookPoint.ON_DEFEAT -> "on_defeat"
            }
        return "hook_${pointName}_$id"
    }
}
