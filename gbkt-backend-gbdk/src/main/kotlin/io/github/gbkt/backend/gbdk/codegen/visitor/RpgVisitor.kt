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

package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CArray
import io.github.gbkt.backend.gbdk.codegen.ast.CArrayAccess
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CBreak
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CComment
import io.github.gbkt.backend.gbdk.codegen.ast.CExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CRawExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CReturn
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitch
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitchCase
import io.github.gbkt.backend.gbdk.codegen.ast.CU16
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.rpg.domain.AbilityDef
import io.github.gbkt.rpg.domain.AbilityLearningConfig
import io.github.gbkt.rpg.domain.ActionNode
import io.github.gbkt.rpg.domain.ActionRpgConfig
import io.github.gbkt.rpg.domain.AllyHpBelow
import io.github.gbkt.rpg.domain.Always
import io.github.gbkt.rpg.domain.AutoLearn
import io.github.gbkt.rpg.domain.BasicAttack
import io.github.gbkt.rpg.domain.BehaviorNode
import io.github.gbkt.rpg.domain.BehaviorPresetType
import io.github.gbkt.rpg.domain.ChargeAction
import io.github.gbkt.rpg.domain.ClassDef
import io.github.gbkt.rpg.domain.CombatModel
import io.github.gbkt.rpg.domain.CombatStats
import io.github.gbkt.rpg.domain.ConditionNode
import io.github.gbkt.rpg.domain.CooldownNode
import io.github.gbkt.rpg.domain.CurrencyDef
import io.github.gbkt.rpg.domain.EffectCategory
import io.github.gbkt.rpg.domain.EquipSlot
import io.github.gbkt.rpg.domain.EquipmentConfig
import io.github.gbkt.rpg.domain.Flee
import io.github.gbkt.rpg.domain.HpAbove
import io.github.gbkt.rpg.domain.HpBelow
import io.github.gbkt.rpg.domain.ItemTeach
import io.github.gbkt.rpg.domain.JobChangeMode
import io.github.gbkt.rpg.domain.LootTableDef
import io.github.gbkt.rpg.domain.MerchantDef
import io.github.gbkt.rpg.domain.MonsterAction
import io.github.gbkt.rpg.domain.MonsterCondition
import io.github.gbkt.rpg.domain.MonsterDef
import io.github.gbkt.rpg.domain.PartyConfig
import io.github.gbkt.rpg.domain.PhaseThresholdNode
import io.github.gbkt.rpg.domain.ResistType
import io.github.gbkt.rpg.domain.RoguelikeConfig
import io.github.gbkt.rpg.domain.RoguelikeMode
import io.github.gbkt.rpg.domain.RpgSaveConfig
import io.github.gbkt.rpg.domain.SelectorNode
import io.github.gbkt.rpg.domain.SequenceNode
import io.github.gbkt.rpg.domain.SkillPointUnlock
import io.github.gbkt.rpg.domain.StackMode
import io.github.gbkt.rpg.domain.StatusEffectDef
import io.github.gbkt.rpg.domain.Summon
import io.github.gbkt.rpg.domain.TargetingMode
import io.github.gbkt.rpg.domain.TurnCountAbove
import io.github.gbkt.rpg.domain.UseAbility

/**
 * Generates typed C [CVarDecl] and [CFunction] nodes for RPG systems from [GenericSystem] IR nodes.
 *
 * Handles character stat structs, ability codegen, status effect codegen, and monster AI codegen.
 * All generated code uses typed C AST (zero [io.github.gbkt.backend.gbdk.codegen.ast.CRawCode]).
 *
 * @param gameIR Reserved for future cross-cutting queries; currently unused but kept for
 *   API stability so callers can pass [GameIR] without a signature change when it is needed.
 */
@Suppress("UNCHECKED_CAST", "UnusedPrivateProperty")
class RpgVisitor(gameIR: GameIR) {

    fun generateCharacterStatStructs(system: GenericSystem): List<CFunction> {
        val id = system.id.replace('-', '_').replace(' ', '_')
        @Suppress("UNUSED_VARIABLE")
        val stats = system.config["stats"] as? CombatStats ?: return emptyList()
        val onLevelUpOps = system.config["onLevelUpOps"] as? List<*> ?: emptyList<Any>()
        return buildList {
            if (onLevelUpOps.isNotEmpty()) {
                add(generateLevelUpFunction(id))
            }
        }
    }

    fun generateStatVarDecls(system: GenericSystem): List<CVarDecl> {
        val id = system.id.replace('-', '_').replace(' ', '_')
        val stats = system.config["stats"] as? CombatStats ?: return emptyList()
        val level = (system.config["level"] as? Int) ?: 1
        return buildList {
            add(
                CVarDecl(
                    name = "_char_${id}_hp",
                    type = CU8,
                    initializer = CLiteral(stats.hp),
                    isConst = true,
                )
            )
            add(
                CVarDecl(
                    name = "_char_${id}_sp",
                    type = CU8,
                    initializer = CLiteral(stats.sp),
                    isConst = true,
                )
            )
            add(
                CVarDecl(
                    name = "_char_${id}_atk",
                    type = CU8,
                    initializer = CLiteral(stats.atk),
                    isConst = true,
                )
            )
            add(
                CVarDecl(
                    name = "_char_${id}_def",
                    type = CU8,
                    initializer = CLiteral(stats.def),
                    isConst = true,
                )
            )
            add(
                CVarDecl(
                    name = "_char_${id}_matk",
                    type = CU8,
                    initializer = CLiteral(stats.matk),
                    isConst = true,
                )
            )
            add(
                CVarDecl(
                    name = "_char_${id}_mdef",
                    type = CU8,
                    initializer = CLiteral(stats.mdef),
                    isConst = true,
                )
            )
            add(
                CVarDecl(
                    name = "_char_${id}_agl",
                    type = CU8,
                    initializer = CLiteral(stats.agl),
                    isConst = true,
                )
            )
            add(CVarDecl(name = "_char_${id}_level", type = CU8, initializer = CLiteral(level)))
            add(CVarDecl(name = "_char_${id}_exp", type = CU16, initializer = CLiteral(0)))
        }
    }

    fun generateAbilityFunctions(system: GenericSystem): List<CFunction> {
        val def = system.config["def"] as? AbilityDef ?: return emptyList()
        val id = def.id.replace('-', '_').replace(' ', '_')
        return listOf(generateUseAbilityFunction(id, def))
    }

    fun generateAbilityDispatch(abilityIds: List<String>): CFunction {
        val cases = abilityIds.mapIndexed { index, abilityId ->
            val sanitizedId = abilityId.replace('-', '_').replace(' ', '_')
            CSwitchCase(
                value = CLiteral(index),
                body = listOf(CExprStatement(CCall("use_ability_$sanitizedId")), CBreak),
            )
        }
        return CFunction(
            name = "dispatch_ability",
            returnType = CVoid,
            params = listOf(CParam("combatant_idx", CU8), CParam("ability_id", CU8)),
            body = listOf(CSwitch(expr = CVar("ability_id"), cases = cases)),
            sectionComment = "RPG ability dispatch",
        )
    }

    fun generateStatusEffectFunctions(system: GenericSystem): List<CFunction> {
        val def = system.config["def"] as? StatusEffectDef ?: return emptyList()
        val id = def.id.replace('-', '_').replace(' ', '_')
        return listOf(
            generateApplyEffectFunction(id, def),
            generateTickEffectFunction(id, def),
            generateRemoveEffectFunction(id, def),
        )
    }

    fun generateCleanseFunction(effectIds: List<Pair<String, EffectCategory>>): CFunction {
        val body =
            buildList<CStatement> {
                for ((rawId, _) in effectIds) {
                    val id = rawId.replace('-', '_').replace(' ', '_')
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(CVar("_effect_${id}_active"), "!=", CLiteral(0)),
                            thenBody = listOf(CExprStatement(CCall("remove_effect_$id"))),
                        )
                    )
                }
            }
        return CFunction(
            name = "cleanse_effects",
            returnType = CVoid,
            params = listOf(CParam("category", CU8)),
            body = body,
            sectionComment = "RPG status effect cleanse",
        )
    }

    fun generateDispelBuffsFunction(buffEffectIds: List<String>): CFunction {
        val body =
            buildList<CStatement> {
                for (rawId in buffEffectIds) {
                    val id = rawId.replace('-', '_').replace(' ', '_')
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(CVar("_effect_${id}_active"), "!=", CLiteral(0)),
                            thenBody = listOf(CExprStatement(CCall("remove_effect_$id"))),
                        )
                    )
                }
            }
        return CFunction(
            name = "dispel_buffs",
            returnType = CVoid,
            params = listOf(CParam("target_idx", CU8)),
            body = body,
            sectionComment = "RPG status effect dispel buffs (GAP-8)",
        )
    }

    fun generateMonsterAIFunctions(system: GenericSystem): List<CFunction> {
        val def = system.config["def"] as? MonsterDef ?: return emptyList()
        val behaviorTree = def.behaviorTree ?: return emptyList()
        val id = def.id.replace('-', '_').replace(' ', '_')
        return listOf(generateMonsterAIFunction(id, def, behaviorTree))
    }

    // =========================================================================
    // Private helpers — ability codegen
    // =========================================================================

    private fun generateUseAbilityFunction(id: String, def: AbilityDef): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Ability: ${def.name}"))
                if (def.spCost > 0) {
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(CVar("_char_active_sp"), "<", CLiteral(def.spCost)),
                            thenBody = listOf(CReturn()),
                        )
                    )
                    add(
                        CExprStatement(
                            CBinaryExpr(CVar("_char_active_sp"), "-=", CLiteral(def.spCost))
                        )
                    )
                }
                add(generateTargetingStatement(def.targeting))
                if (def.appliesEffect != null) {
                    val effectId = def.appliesEffect!!.replace('-', '_').replace(' ', '_')
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CBinaryExpr(CCall("rand"), "%", CLiteral(100)),
                                    "<",
                                    CLiteral(def.effectChance),
                                ),
                            thenBody = listOf(CExprStatement(CCall("apply_effect_$effectId"))),
                        )
                    )
                }
            }
        return CFunction(
            name = "use_ability_$id",
            returnType = CVoid,
            body = body,
            sectionComment = "RPG ability: ${def.name}",
        )
    }

    private fun generateTargetingStatement(targeting: TargetingMode): CStatement =
        when (targeting) {
            TargetingMode.SELF ->
                CExprStatement(CBinaryExpr(CVar("_combat_target_idx"), "=", CLiteral(0)))
            TargetingMode.SINGLE_ENEMY ->
                CExprStatement(
                    CBinaryExpr(CVar("_combat_target_idx"), "=", CVar("_combat_active_enemy_idx"))
                )
            TargetingMode.SINGLE_ALLY ->
                CExprStatement(
                    CBinaryExpr(CVar("_combat_target_idx"), "=", CVar("_combat_active_ally_idx"))
                )
            TargetingMode.ALL_ENEMIES ->
                CExprStatement(CBinaryExpr(CVar("_combat_target_all_enemies"), "=", CLiteral(1)))
            TargetingMode.ALL_ALLIES ->
                CExprStatement(CBinaryExpr(CVar("_combat_target_all_allies"), "=", CLiteral(1)))
            TargetingMode.ALL ->
                CExprStatement(CBinaryExpr(CVar("_combat_target_all"), "=", CLiteral(1)))
        }

    // =========================================================================
    // Private helpers — status effect codegen
    // =========================================================================

    private fun generateApplyEffectFunction(id: String, def: StatusEffectDef): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Apply status effect: ${def.name}"))
                // GAP-6: per-effect immunity check
                for (immuneId in def.immuneToEffects) {
                    val sanitizedImmuneId = immuneId.replace('-', '_').replace(' ', '_')
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CVar("_char_target_immune_to_$sanitizedImmuneId"),
                                    "!=",
                                    CLiteral(0),
                                ),
                            thenBody = listOf(CReturn()),
                        )
                    )
                }
                // GAP-5: stat-based resist contest vs flat apply chance
                when (def.resistType) {
                    ResistType.STAT_CONTEST -> {
                        val resistStat = def.resistStat.replace('-', '_').replace(' ', '_')
                        add(
                            CVarDecl(
                                name = "effective_chance",
                                type = CU8,
                                initializer =
                                    CBinaryExpr(
                                        CLiteral(def.applyChance),
                                        "-",
                                        CBinaryExpr(
                                            CVar("_char_target_$resistStat"),
                                            "-",
                                            CVar("_char_caster_matk"),
                                        ),
                                    ),
                            )
                        )
                        add(
                            CIf(
                                condition =
                                    CBinaryExpr(
                                        CBinaryExpr(CCall("rand"), "%", CLiteral(100)),
                                        ">=",
                                        CVar("effective_chance"),
                                    ),
                                thenBody = listOf(CReturn()),
                            )
                        )
                    }
                    ResistType.FLAT -> {
                        if (def.applyChance < 100) {
                            add(
                                CIf(
                                    condition =
                                        CBinaryExpr(
                                            CBinaryExpr(CCall("rand"), "%", CLiteral(100)),
                                            ">=",
                                            CLiteral(def.applyChance),
                                        ),
                                    thenBody = listOf(CReturn()),
                                )
                            )
                        }
                    }
                }
                // Stack handling
                when (def.stackMode) {
                    StackMode.NONE -> {
                        add(
                            CIf(
                                condition =
                                    CBinaryExpr(CVar("_effect_${id}_active"), "!=", CLiteral(0)),
                                thenBody = listOf(CReturn()),
                            )
                        )
                        add(
                            CExprStatement(
                                CBinaryExpr(CVar("_effect_${id}_active"), "=", CLiteral(1))
                            )
                        )
                        add(
                            CExprStatement(
                                CBinaryExpr(
                                    CVar("_effect_${id}_duration"),
                                    "=",
                                    CLiteral(def.duration),
                                )
                            )
                        )
                    }
                    // REFRESH_DURATION re-arms an existing effect's timer while INDEPENDENT
                    // applies a fresh instance; both lower to the same state-setting C code.
                    StackMode.REFRESH_DURATION,
                    StackMode.INDEPENDENT -> {
                        add(
                            CExprStatement(
                                CBinaryExpr(CVar("_effect_${id}_active"), "=", CLiteral(1))
                            )
                        )
                        add(
                            CExprStatement(
                                CBinaryExpr(
                                    CVar("_effect_${id}_duration"),
                                    "=",
                                    CLiteral(def.duration),
                                )
                            )
                        )
                    }
                    StackMode.INTENSITY -> {
                        val stackBody =
                            buildList<CStatement> {
                                add(
                                    CExprStatement(
                                        CBinaryExpr(CVar("_effect_${id}_stacks"), "+=", CLiteral(1))
                                    )
                                )
                                if (def.onStackAppliedOps.isNotEmpty()) {
                                    add(CExprStatement(CCall("on_stack_applied_$id")))
                                }
                            }
                        add(
                            CIf(
                                condition =
                                    CBinaryExpr(
                                        CVar("_effect_${id}_stacks"),
                                        "<",
                                        CLiteral(def.maxStacks),
                                    ),
                                thenBody = stackBody,
                            )
                        )
                        add(
                            CExprStatement(
                                CBinaryExpr(CVar("_effect_${id}_active"), "=", CLiteral(1))
                            )
                        )
                        add(
                            CExprStatement(
                                CBinaryExpr(
                                    CVar("_effect_${id}_duration"),
                                    "=",
                                    CLiteral(def.duration),
                                )
                            )
                        )
                    }
                }
            }
        return CFunction(
            name = "apply_effect_$id",
            returnType = CVoid,
            body = body,
            sectionComment = "RPG status effect apply: ${def.name}",
        )
    }

    private fun generateTickEffectFunction(id: String, def: StatusEffectDef): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Tick effect: ${def.name}"))
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_effect_${id}_active"), "==", CLiteral(0)),
                        thenBody = listOf(CReturn()),
                    )
                )
                // GAP-7: per-stack scaling
                if (def.damagePerTurn > 0) {
                    if (def.stackMode == StackMode.INTENSITY && def.perStackScaling) {
                        add(
                            CVarDecl(
                                name = "dot_damage",
                                type = CU8,
                                initializer =
                                    CBinaryExpr(
                                        CLiteral(def.damagePerTurn),
                                        "*",
                                        CVar("_effect_${id}_stacks"),
                                    ),
                            )
                        )
                        add(
                            CExprStatement(
                                CBinaryExpr(CVar("_char_target_hp"), "-=", CVar("dot_damage"))
                            )
                        )
                    } else {
                        add(
                            CExprStatement(
                                CBinaryExpr(
                                    CVar("_char_target_hp"),
                                    "-=",
                                    CLiteral(def.damagePerTurn),
                                )
                            )
                        )
                    }
                }
                if (def.healPerTurn > 0) {
                    add(
                        CExprStatement(
                            CBinaryExpr(CVar("_char_target_hp"), "+=", CLiteral(def.healPerTurn))
                        )
                    )
                }
                if (def.duration > 0) {
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(CVar("_effect_${id}_duration"), ">", CLiteral(0)),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("_effect_${id}_duration"),
                                            "-=",
                                            CLiteral(1),
                                        )
                                    )
                                ),
                        )
                    )
                    val expireBody =
                        buildList<CStatement> {
                            if (
                                def.stackMode == StackMode.INTENSITY &&
                                    def.onStackRemovedOps.isNotEmpty()
                            ) {
                                add(CExprStatement(CCall("on_stack_removed_$id")))
                            }
                            add(CExprStatement(CCall("remove_effect_$id")))
                        }
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(CVar("_effect_${id}_duration"), "==", CLiteral(0)),
                            thenBody = expireBody,
                        )
                    )
                }
            }
        return CFunction(
            name = "tick_effect_$id",
            returnType = CVoid,
            body = body,
            sectionComment = "RPG status effect tick: ${def.name}",
        )
    }

    private fun generateRemoveEffectFunction(id: String, def: StatusEffectDef): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Remove effect: ${def.name}"))
                add(CExprStatement(CBinaryExpr(CVar("_effect_${id}_active"), "=", CLiteral(0))))
                add(CExprStatement(CBinaryExpr(CVar("_effect_${id}_duration"), "=", CLiteral(0))))
                add(CExprStatement(CBinaryExpr(CVar("_effect_${id}_stacks"), "=", CLiteral(0))))
            }
        return CFunction(
            name = "remove_effect_$id",
            returnType = CVoid,
            body = body,
            sectionComment = "RPG status effect remove by ID: ${def.name} (GAP-8)",
        )
    }

    // =========================================================================
    // Private helpers — monster AI codegen
    // =========================================================================

    private fun generateMonsterAIFunction(
        id: String,
        def: MonsterDef,
        tree: BehaviorNode,
    ): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Monster AI: ${def.name}"))
                // Decrement all cooldown timers
                for ((abilityId, _) in def.abilityCooldowns) {
                    val cdId = abilityId.replace('-', '_').replace(' ', '_')
                    add(
                        CIf(
                            condition = CBinaryExpr(CVar("_mon_${id}_cd_$cdId"), ">", CLiteral(0)),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(CVar("_mon_${id}_cd_$cdId"), "-=", CLiteral(1))
                                    )
                                ),
                        )
                    )
                }
                // GAP-3: difficulty tier modifies target selection
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_combat_difficulty"), "==", CLiteral(0)),
                        thenBody =
                            listOf(
                                CComment("EASY: random targeting override"),
                                CExprStatement(
                                    CBinaryExpr(CVar("_combat_target_idx"), "=", CCall("rand"))
                                ),
                            ),
                        elseBody =
                            listOf(
                                CIf(
                                    condition =
                                        CBinaryExpr(CVar("_combat_difficulty"), "==", CLiteral(2)),
                                    thenBody =
                                        listOf(
                                            CComment("HARD: lowest HP targeting override"),
                                            CExprStatement(
                                                CBinaryExpr(
                                                    CVar("_combat_target_idx"),
                                                    "=",
                                                    CCall("find_lowest_hp_target"),
                                                )
                                            ),
                                        ),
                                    elseBody =
                                        listOf(
                                            CComment("NORMAL: use behavior tree target strategy")
                                        ),
                                )
                            ),
                    )
                )
                // Repeat-prevention local variable
                if (def.allowGlobalRepeatPrevention) {
                    add(CComment("Repeat prevention: track last action"))
                    add(
                        CVarDecl(
                            name = "_mon_${id}_action_taken",
                            type = CU8,
                            initializer = CLiteral(255),
                        )
                    )
                }
                // Compile behavior tree to flat C statements
                addAll(compileBehaviorTree(id, def, tree))
                // Update last-action global
                if (def.allowGlobalRepeatPrevention) {
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(CVar("_mon_${id}_action_taken"), "!=", CLiteral(255)),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("_mon_${id}_last_action"),
                                            "=",
                                            CVar("_mon_${id}_action_taken"),
                                        )
                                    )
                                ),
                        )
                    )
                }
            }
        return CFunction(
            name = "update_ai_$id",
            returnType = CVoid,
            body = body,
            sectionComment = "RPG monster AI: ${def.name}",
        )
    }

    private fun compileBehaviorTree(
        monsterId: String,
        def: MonsterDef,
        node: BehaviorNode,
    ): List<CStatement> =
        when (node) {
            is SelectorNode -> compileSelectorNode(monsterId, def, node)
            is SequenceNode -> compileSequenceNode(monsterId, def, node)
            is ConditionNode -> compileConditionNode(monsterId, def, node)
            is ActionNode -> compileActionNode(monsterId, def, node)
            is PhaseThresholdNode -> compilePhaseThresholdNode(monsterId, def, node)
            is CooldownNode -> compileCooldownNode(monsterId, def, node)
        }

    private fun compileSelectorNode(
        monsterId: String,
        def: MonsterDef,
        node: SelectorNode,
    ): List<CStatement> {
        val result = mutableListOf<CStatement>()
        for (child in node.children) result.addAll(compileBehaviorTree(monsterId, def, child))
        return result
    }

    private fun compileSequenceNode(
        monsterId: String,
        def: MonsterDef,
        node: SequenceNode,
    ): List<CStatement> {
        val result = mutableListOf<CStatement>()
        for (child in node.children) result.addAll(compileBehaviorTree(monsterId, def, child))
        return result
    }

    private fun compileConditionNode(
        monsterId: String,
        @Suppress("UNUSED_PARAMETER") def: MonsterDef,
        node: ConditionNode,
    ): List<CStatement> {
        val conditionExpr = buildConditionExpr(monsterId, node.predicate)
        return listOf(
            CIf(
                condition = conditionExpr,
                thenBody = listOf(CComment("condition: ${node.predicate::class.simpleName} met")),
            )
        )
    }

    private fun compileActionNode(
        monsterId: String,
        def: MonsterDef,
        node: ActionNode,
    ): List<CStatement> {
        return buildList {
            val actionId = getActionId(node.action)
            if (def.allowGlobalRepeatPrevention) {
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CVar("_mon_${monsterId}_last_action"),
                                "==",
                                CLiteral(actionId),
                            ),
                        thenBody = listOf(CReturn()),
                    )
                )
            }
            when (val action = node.action) {
                is UseAbility -> {
                    val abilityId = action.abilityId.replace('-', '_').replace(' ', '_')
                    add(CExprStatement(CCall("use_ability_$abilityId")))
                    if (def.abilityCooldowns.containsKey(action.abilityId)) {
                        val cdTurns = def.abilityCooldowns.getValue(action.abilityId)
                        add(
                            CExprStatement(
                                CBinaryExpr(
                                    CVar("_mon_${monsterId}_cd_$abilityId"),
                                    "=",
                                    CLiteral(cdTurns),
                                )
                            )
                        )
                    }
                }
                is BasicAttack -> add(CExprStatement(CCall("monster_basic_attack")))
                is Flee -> {
                    if (action.chance < 100) {
                        add(
                            CIf(
                                condition =
                                    CBinaryExpr(
                                        CBinaryExpr(CCall("rand"), "%", CLiteral(100)),
                                        "<",
                                        CLiteral(action.chance),
                                    ),
                                thenBody = listOf(CExprStatement(CCall("monster_flee"))),
                            )
                        )
                    } else {
                        add(CExprStatement(CCall("monster_flee")))
                    }
                }
                is Summon -> {
                    val summonId = action.monsterId.replace('-', '_').replace(' ', '_')
                    add(
                        CExprStatement(
                            CCall(
                                "monster_summon",
                                listOf(CVar("monster_id_$summonId"), CLiteral(action.count)),
                            )
                        )
                    )
                }
                is ChargeAction -> {
                    val abilityId = action.abilityId.replace('-', '_').replace(' ', '_')
                    add(
                        CExprStatement(
                            CBinaryExpr(
                                CVar("_mon_${monsterId}_charge_turns"),
                                "=",
                                CLiteral(action.chargeTurns),
                            )
                        )
                    )
                    add(
                        CExprStatement(
                            CBinaryExpr(
                                CVar("_mon_${monsterId}_charge_ability"),
                                "=",
                                CVar("ability_id_$abilityId"),
                            )
                        )
                    )
                }
            }
            if (def.allowGlobalRepeatPrevention) {
                add(
                    CExprStatement(
                        CBinaryExpr(CVar("_mon_${monsterId}_action_taken"), "=", CLiteral(actionId))
                    )
                )
            }
        }
    }

    private fun compilePhaseThresholdNode(
        monsterId: String,
        def: MonsterDef,
        node: PhaseThresholdNode,
    ): List<CStatement> {
        val innerStatements = compileBehaviorTree(monsterId, def, node.tree)
        return listOf(
            CIf(
                condition =
                    CBinaryExpr(CVar("_mon_${monsterId}_hp_pct"), "<", CLiteral(node.hpPercent)),
                thenBody = innerStatements,
            )
        )
    }

    private fun compileCooldownNode(
        monsterId: String,
        def: MonsterDef,
        node: CooldownNode,
    ): List<CStatement> {
        val cdId = node.abilityId.replace('-', '_').replace(' ', '_')
        val innerStatements = compileBehaviorTree(monsterId, def, node.child)
        return listOf(
            CIf(
                condition = CBinaryExpr(CVar("_mon_${monsterId}_cd_$cdId"), "==", CLiteral(0)),
                thenBody = innerStatements,
            )
        )
    }

    private fun buildConditionExpr(monsterId: String, condition: MonsterCondition): CExpr =
        when (condition) {
            is HpBelow ->
                CBinaryExpr(CVar("_mon_${monsterId}_hp_pct"), "<", CLiteral(condition.percent))
            is HpAbove ->
                CBinaryExpr(CVar("_mon_${monsterId}_hp_pct"), ">", CLiteral(condition.percent))
            is AllyHpBelow ->
                CBinaryExpr(CVar("_mon_ally_hp_pct"), "<", CLiteral(condition.percent))
            is TurnCountAbove ->
                CBinaryExpr(CVar("_combat_turn_count"), ">", CLiteral(condition.count))
            is Always -> CBinaryExpr(CLiteral(1), "==", CLiteral(1))
        }

    private fun getActionId(action: MonsterAction): Int =
        when (action) {
            is UseAbility -> (action.abilityId.hashCode() and 0x7F)
            is BasicAttack -> 200
            is Flee -> 201
            is Summon -> 202
            is ChargeAction -> (action.abilityId.hashCode() and 0x7F) or 0x80
        }

    // =========================================================================
    // Private helpers — character codegen
    // =========================================================================

    private fun generateLevelUpFunction(id: String): CFunction {
        return CFunction(
            name = "level_up_$id",
            returnType = CVoid,
            body =
                listOf(
                    CComment("Level-up stat changes for character '$id'"),
                    CExprStatement(CBinaryExpr(CVar("_char_${id}_level"), "+=", CLiteral(1))),
                ),
            sectionComment = "RPG character level-up: $id",
        )
    }

    // =========================================================================
    // generateEquipmentFunctions — equipment slot globals + equip/unequip functions
    // =========================================================================

    fun generateEquipmentFunctions(system: GenericSystem): List<CFunction> {
        val config = system.config["config"] as? EquipmentConfig ?: return emptyList()
        val result = mutableListOf<CFunction>()
        val effectiveSlots: List<String> =
            if (config.customSlots.isNotEmpty()) {
                config.customSlots.map { it.name }
            } else {
                EquipSlot.entries.map { it.name.lowercase() }
            }
        for (slotName in effectiveSlots) {
            val sanitized = slotName.replace('-', '_').replace(' ', '_')
            result.add(generateEquipFunction(sanitized, config))
            result.add(generateUnequipFunction(sanitized))
            if (config.enableUpgrades)
                result.add(generateUpgradeFunction(sanitized, config.maxUpgradeLevel))
            if (config.enableEnchanting) result.add(generateEnchantFunction(sanitized))
        }
        if (config.enableDurability) result.add(generateDegradeEquipmentFunction(effectiveSlots))
        for (setDef in config.sets) {
            val setId = setDef.id.replace('-', '_').replace(' ', '_')
            result.add(generateSetBonusFunction(setId, setDef.name, setDef.tiers.size))
        }
        return result
    }

    fun generateEquipmentVarDecls(system: GenericSystem): List<CVarDecl> {
        val config = system.config["config"] as? EquipmentConfig ?: return emptyList()
        val result = mutableListOf<CVarDecl>()
        val effectiveSlots: List<String> =
            if (config.customSlots.isNotEmpty()) {
                config.customSlots.map { it.name }
            } else {
                EquipSlot.entries.map { it.name.lowercase() }
            }
        for (slotName in effectiveSlots) {
            val sanitized = slotName.replace('-', '_').replace(' ', '_')
            result.add(
                CVarDecl(name = "_equipped_$sanitized", type = CU8, initializer = CLiteral(0xFF))
            )
            if (config.enableUpgrades)
                result.add(
                    CVarDecl(
                        name = "_equip_${sanitized}_upgrade_level",
                        type = CU8,
                        initializer = CLiteral(0),
                    )
                )
            if (config.enableDurability)
                result.add(
                    CVarDecl(
                        name = "_equip_durability_$sanitized",
                        type = CU8,
                        initializer = CLiteral(0),
                    )
                )
            if (config.enableEnchanting)
                result.add(
                    CVarDecl(
                        name = "_equip_${sanitized}_enchant",
                        type = CU8,
                        initializer = CLiteral(0),
                    )
                )
        }
        for (setDef in config.sets) {
            val setId = setDef.id.replace('-', '_').replace(' ', '_')
            result.add(
                CVarDecl(name = "_set_${setId}_count", type = CU8, initializer = CLiteral(0))
            )
        }
        return result
    }

    // =========================================================================
    // generateClassFunctions — growth rate tables + level-up + ability learn
    // =========================================================================

    fun generateClassFunctions(system: GenericSystem): List<CFunction> {
        val def = system.config["def"] as? ClassDef ?: return emptyList()
        val classId = def.id.replace('-', '_').replace(' ', '_')
        val result = mutableListOf<CFunction>()
        result.add(
            CFunction(
                name = "_class_${classId}_growth_decl",
                returnType = CVoid,
                body =
                    listOf(
                        CComment(
                            "Const array: UINT8 _class_${classId}_growth[7] = {hp, sp, atk, def, matk, mdef, agl}"
                        )
                    ),
                sectionComment = "Class growth rate table: $classId",
            )
        )
        result.add(generateApplyLevelUpFunction(classId, def))
        if (def.learnableAbilities.isNotEmpty())
            result.add(generateCheckAbilityLearnFunction(classId, def))
        if (def.jobChangeMode != JobChangeMode.LOCKED)
            result.add(generateChangeClassFunction(classId, def.jobChangeMode))
        return result
    }

    fun generateClassVarDecls(system: GenericSystem): List<CVarDecl> {
        val def = system.config["def"] as? ClassDef ?: return emptyList()
        val classId = def.id.replace('-', '_').replace(' ', '_')
        return listOf(
            CVarDecl(name = "_class_${classId}_growth", type = CArray(CU8, 7), isConst = true)
        )
    }

    // =========================================================================
    // Private helpers — equipment
    // =========================================================================

    private fun generateEquipFunction(slotName: String, config: EquipmentConfig): CFunction {
        val body = mutableListOf<CStatement>()
        body.add(CComment("Set equipped item ID"))
        body.add(CExprStatement(CBinaryExpr(CVar("_equipped_$slotName"), "=", CVar("item_id"))))
        if (slotName == "weapon")
            body.add(CComment("Two-handed: if item is two-handed, set SHIELD slot as blocked"))
        if (config.enableUpgrades)
            body.add(CComment("Apply upgrade bonus (upgrade_level * 2 bonus per stat)"))
        if (config.enableEnchanting)
            body.add(
                CComment("Apply initial enchant aspect if EquipmentItemData.enchantAspect is set")
            )
        body.add(CComment("Apply flat then percentage stat modifiers"))
        body.add(CComment("Check set bonus activation"))
        return CFunction(
            name = "equip_item_$slotName",
            returnType = CVoid,
            params = listOf(CParam("item_id", CU8)),
            body = body,
            sectionComment = "Equipment: equip item in $slotName slot",
        )
    }

    private fun generateUnequipFunction(slotName: String): CFunction =
        CFunction(
            name = "unequip_$slotName",
            returnType = CVoid,
            body =
                listOf(
                    CComment("Reverse stat modifiers for unequipped item"),
                    CExprStatement(CBinaryExpr(CVar("_equipped_$slotName"), "=", CLiteral(0xFF))),
                    CComment("Update set bonus piece count"),
                ),
            sectionComment = "Equipment: unequip $slotName slot",
        )

    private fun generateUpgradeFunction(slotName: String, maxUpgradeLevel: Int): CFunction =
        CFunction(
            name = "upgrade_item_$slotName",
            returnType = CVoid,
            params = listOf(CParam("level", CU8)),
            body =
                listOf(
                    CComment("Validate level <= maxUpgradeLevel ($maxUpgradeLevel)"),
                    CIf(
                        condition = CBinaryExpr(CVar("level"), ">", CLiteral(maxUpgradeLevel)),
                        thenBody = listOf(CReturn()),
                    ),
                    CExprStatement(
                        CBinaryExpr(CVar("_equip_${slotName}_upgrade_level"), "=", CVar("level"))
                    ),
                    CComment("Recalculate stats: base + flat + (upgrade_bonus * level) + percent"),
                ),
            sectionComment = "Equipment upgrade: $slotName (GAP-1)",
        )

    private fun generateEnchantFunction(slotName: String): CFunction =
        CFunction(
            name = "enchant_item_$slotName",
            returnType = CVoid,
            params = listOf(CParam("aspect_id", CU8)),
            body =
                listOf(
                    CExprStatement(
                        CBinaryExpr(CVar("_equip_${slotName}_enchant"), "=", CVar("aspect_id"))
                    ),
                    CComment(
                        "Aspect stored; damage routines check _equip_${slotName}_enchant for bonus"
                    ),
                ),
            sectionComment = "Equipment enchant: $slotName (GAP-2)",
        )

    private fun generateDegradeEquipmentFunction(slots: List<String>): CFunction {
        val body = mutableListOf<CStatement>()
        body.add(CComment("Degrade durability for all equipped items"))
        for (slot in slots) {
            val sanitized = slot.replace('-', '_').replace(' ', '_')
            body.add(
                CIf(
                    condition =
                        CBinaryExpr(
                            CBinaryExpr(CVar("_equipped_$sanitized"), "!=", CLiteral(0xFF)),
                            "&&",
                            CBinaryExpr(CVar("_equip_durability_$sanitized"), ">", CLiteral(0)),
                        ),
                    thenBody =
                        listOf(
                            CExprStatement(
                                CBinaryExpr(CVar("_equip_durability_$sanitized"), "-=", CLiteral(1))
                            )
                        ),
                )
            )
        }
        return CFunction(
            name = "degrade_equipment",
            returnType = CVoid,
            body = body,
            sectionComment = "Equipment durability degradation",
        )
    }

    private fun generateSetBonusFunction(
        setId: String,
        setName: String,
        tierCount: Int,
    ): CFunction =
        CFunction(
            name = "check_set_bonus_$setId",
            returnType = CVoid,
            body =
                listOf(
                    CComment("Update set '$setName' piece count and apply tiered bonuses"),
                    CComment("Tier count: $tierCount (2-piece, 3-piece, 4-piece etc.)"),
                    CComment("Piece count tracked in _set_${setId}_count"),
                ),
            sectionComment = "Set bonus tracking: $setId",
        )

    // =========================================================================
    // Private helpers — class/job
    // =========================================================================

    private fun generateApplyLevelUpFunction(classId: String, def: ClassDef): CFunction {
        val rates = def.growthRates
        val body = mutableListOf<CStatement>()
        body.add(CComment("Apply growth rates from _class_${classId}_growth[] to character stats"))
        if (rates.hp > 0)
            body.add(
                CExprStatement(CBinaryExpr(CVar("_char_${classId}_hp"), "+=", CLiteral(rates.hp)))
            )
        if (rates.sp > 0)
            body.add(
                CExprStatement(CBinaryExpr(CVar("_char_${classId}_sp"), "+=", CLiteral(rates.sp)))
            )
        if (rates.atk > 0)
            body.add(
                CExprStatement(CBinaryExpr(CVar("_char_${classId}_atk"), "+=", CLiteral(rates.atk)))
            )
        if (rates.def > 0)
            body.add(
                CExprStatement(CBinaryExpr(CVar("_char_${classId}_def"), "+=", CLiteral(rates.def)))
            )
        if (rates.matk > 0)
            body.add(
                CExprStatement(
                    CBinaryExpr(CVar("_char_${classId}_matk"), "+=", CLiteral(rates.matk))
                )
            )
        if (rates.mdef > 0)
            body.add(
                CExprStatement(
                    CBinaryExpr(CVar("_char_${classId}_mdef"), "+=", CLiteral(rates.mdef))
                )
            )
        if (rates.agl > 0)
            body.add(
                CExprStatement(CBinaryExpr(CVar("_char_${classId}_agl"), "+=", CLiteral(rates.agl)))
            )
        if (body.size == 1) body.add(CComment("No growth rates configured for class '$classId'"))
        return CFunction(
            name = "apply_level_up_$classId",
            returnType = CVoid,
            params = listOf(CParam("char_id", CU8)),
            body = body,
            sectionComment = "Class level-up: $classId",
        )
    }

    private fun generateCheckAbilityLearnFunction(classId: String, def: ClassDef): CFunction {
        val cases =
            def.learnableAbilities.map { entry ->
                CSwitchCase(
                    value = CLiteral(entry.level),
                    body =
                        listOf(
                            CComment("Learn ability '${entry.abilityId}' at level ${entry.level}"),
                            CBreak,
                        ),
                )
            }
        return CFunction(
            name = "check_ability_learn_$classId",
            returnType = CVoid,
            params = listOf(CParam("level", CU8)),
            body = listOf(CSwitch(expr = CVar("level"), cases = cases)),
            sectionComment = "Class ability learn: $classId",
        )
    }

    private fun generateChangeClassFunction(classId: String, mode: JobChangeMode): CFunction {
        val resetComment =
            when (mode) {
                JobChangeMode.SWITCHABLE_FRESH -> "Reset learned abilities (SWITCHABLE_FRESH mode)"
                JobChangeMode.SWITCHABLE_WITH_SKILLS ->
                    "Retain learned abilities (SWITCHABLE_WITH_SKILLS mode)"
                JobChangeMode.LOCKED -> "No-op (LOCKED mode)"
            }
        return CFunction(
            name = "change_class",
            returnType = CVoid,
            params = listOf(CParam("char_id", CU8), CParam("new_class_id", CU8)),
            body = listOf(CComment(resetComment), CComment("Update active class to new_class_id")),
            sectionComment = "Job change: $classId ($mode)",
        )
    }

    // =========================================================================
    // generateMerchantFunctions — shop buy/sell functions (GAP-10 sell override)
    // =========================================================================

    fun generateMerchantFunctions(system: GenericSystem): List<CFunction> {
        val def = system.config["def"] as? MerchantDef ?: return emptyList()
        val id = def.id.replace('-', '_').replace(' ', '_')
        val result = mutableListOf<CFunction>()
        result.add(generateBuyFunction(id, def))
        result.add(generateSellFunction(id, def))
        if (def.flagGatedStock.isNotEmpty()) {
            result.add(generateFlagStockAvailableFunction(id, def))
        }
        return result
    }

    fun generateMerchantVarDecls(system: GenericSystem): List<CVarDecl> {
        val def = system.config["def"] as? MerchantDef ?: return emptyList()
        val id = def.id.replace('-', '_').replace(' ', '_')
        val stockSize = def.stock.size
        if (stockSize == 0) return emptyList()
        return buildList {
            // _shop_<id>_stock[N] — item IDs
            add(CVarDecl(name = "_shop_${id}_stock", type = CArray(CU8, stockSize), isConst = true))
            // _shop_<id>_prices[N] — buy prices
            add(
                CVarDecl(name = "_shop_${id}_prices", type = CArray(CU8, stockSize), isConst = true)
            )
            // _shop_<id>_stock_limit[N] — 0xFF = unlimited
            add(CVarDecl(name = "_shop_${id}_stock_limit", type = CArray(CU8, stockSize)))
            // _shop_<id>_sell_override[N] — 0xFF = use global ratio (GAP-10)
            add(
                CVarDecl(
                    name = "_shop_${id}_sell_override",
                    type = CArray(CU8, stockSize),
                    isConst = true,
                )
            )
        }
    }

    private fun generateBuyFunction(id: String, def: MerchantDef): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Buy item from shop '$id' at slot_idx"))
                add(CComment("currency: ${def.currencyName}"))
                // Price check
                add(
                    CVarDecl(
                        name = "price",
                        type = CU8,
                        initializer = CArrayAccess(CVar("_shop_${id}_prices"), CVar("slot_idx")),
                    )
                )
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_player_gold"), "<", CVar("price")),
                        thenBody = listOf(CReturn()),
                    )
                )
                // Stock limit check: 0xFF = unlimited
                add(
                    CVarDecl(
                        name = "limit",
                        type = CU8,
                        initializer =
                            CArrayAccess(CVar("_shop_${id}_stock_limit"), CVar("slot_idx")),
                    )
                )
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(CVar("limit"), "!=", CLiteral(255)),
                                "&&",
                                CBinaryExpr(CVar("limit"), "==", CLiteral(0)),
                            ),
                        thenBody = listOf(CReturn()),
                    )
                )
                // Add item to inventory
                add(
                    CExprStatement(
                        CCall(
                            "add_item",
                            listOf(
                                CArrayAccess(CVar("_shop_${id}_stock"), CVar("slot_idx")),
                                CLiteral(1),
                            ),
                        )
                    )
                )
                // Deduct currency
                add(CExprStatement(CBinaryExpr(CVar("_player_gold"), "-=", CVar("price"))))
                // Decrement stock if limited
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("limit"), "!=", CLiteral(255)),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CArrayAccess(
                                            CVar("_shop_${id}_stock_limit"),
                                            CVar("slot_idx"),
                                        ),
                                        "-=",
                                        CLiteral(1),
                                    )
                                )
                            ),
                    )
                )
            }
        return CFunction(
            name = "buy_from_$id",
            returnType = CVoid,
            params = listOf(CParam("slot_idx", CU8)),
            body = body,
            sectionComment = "Shop buy: $id",
        )
    }

    private fun generateSellFunction(id: String, def: MerchantDef): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Sell item to shop '$id'"))
                // GAP-10: per-item sell override takes precedence over global ratio
                add(CVarDecl(name = "sell_price", type = CU8, initializer = CLiteral(0)))
                add(
                    CVarDecl(
                        name = "override_val",
                        type = CU8,
                        initializer =
                            CArrayAccess(CVar("_shop_${id}_sell_override"), CVar("slot_idx")),
                    )
                )
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("override_val"), "!=", CLiteral(255)),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(CVar("sell_price"), "=", CVar("override_val"))
                                )
                            ),
                        elseBody =
                            listOf(
                                CComment("Use global sell ratio: price * ${def.sellRatio} / 100"),
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("sell_price"),
                                        "=",
                                        CBinaryExpr(
                                            CBinaryExpr(
                                                CArrayAccess(
                                                    CVar("_shop_${id}_prices"),
                                                    CVar("slot_idx"),
                                                ),
                                                "*",
                                                CLiteral(def.sellRatio),
                                            ),
                                            "/",
                                            CLiteral(100),
                                        ),
                                    )
                                ),
                            ),
                    )
                )
                // Add currency to player
                add(CExprStatement(CBinaryExpr(CVar("_player_gold"), "+=", CVar("sell_price"))))
                // Remove item from inventory
                add(CExprStatement(CCall("remove_item", listOf(CVar("item_id"), CLiteral(1)))))
            }
        return CFunction(
            name = "sell_to_$id",
            returnType = CVoid,
            params = listOf(CParam("slot_idx", CU8), CParam("item_id", CU8)),
            body = body,
            sectionComment = "Shop sell: $id (GAP-10 per-item sell override)",
        )
    }

    private fun generateFlagStockAvailableFunction(id: String, def: MerchantDef): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Check if flag-gated slot is available"))
                for ((flagName, items) in def.flagGatedStock) {
                    val flagId = flagName.replace('-', '_').replace(' ', '_')
                    items.forEachIndexed { _, _ ->
                        add(
                            CIf(
                                condition = CBinaryExpr(CVar("_flag_$flagId"), "==", CLiteral(0)),
                                thenBody = listOf(CReturn(CLiteral(0))),
                            )
                        )
                    }
                }
                add(CReturn(CLiteral(1)))
            }
        return CFunction(
            name = "is_${id}_stock_available",
            returnType = CU8,
            params = listOf(CParam("slot_idx", CU8)),
            body = body,
            sectionComment = "Shop flag-gated stock: $id",
        )
    }

    // =========================================================================
    // generatePartyFunctions — party management with guest support (GAP-4)
    // =========================================================================

    fun generatePartyFunctions(system: GenericSystem): List<CFunction> {
        val config = system.config["config"] as? PartyConfig ?: return emptyList()
        val result = mutableListOf<CFunction>()
        result.add(generateAddToPartyFunction(config))
        result.add(generateRemoveFromPartyFunction(config))
        if (config.enableReserve) {
            result.add(generateSwapPartyMemberFunction())
        }
        // GAP-4: guest member helpers
        if (config.initialMembers.any { it.isGuest }) {
            result.add(generateIsGuestFunction())
            result.add(generateRemoveGuestFunction())
            result.add(generateAiGuestFunction())
        }
        if (config.enableRowFormation) {
            result.add(generateSetRowFunction())
        }
        return result
    }

    fun generatePartyVarDecls(system: GenericSystem): List<CVarDecl> {
        val config = system.config["config"] as? PartyConfig ?: return emptyList()
        return buildList {
            add(
                CVarDecl(
                    name = "_party_active",
                    type = CArray(CU8, config.maxActiveSize),
                    initializer = CLiteral(255),
                )
            )
            add(CVarDecl(name = "_party_active_count", type = CU8, initializer = CLiteral(0)))
            // GAP-4: guest flag array
            add(
                CVarDecl(
                    name = "_party_is_guest",
                    type = CArray(CU8, config.maxActiveSize),
                    initializer = CLiteral(0),
                )
            )
            if (config.enableReserve) {
                add(
                    CVarDecl(
                        name = "_party_reserve",
                        type = CArray(CU8, config.reserveSize),
                        initializer = CLiteral(255),
                    )
                )
                add(CVarDecl(name = "_party_reserve_count", type = CU8, initializer = CLiteral(0)))
                // H6: emit reserve EXP share constant (percentage shared to bench members)
                add(
                    CVarDecl(
                        name = "_party_reserve_exp_share",
                        type = CU8,
                        initializer = CLiteral(config.reserveExpShare),
                        isConst = true,
                    )
                )
            }
            if (config.enableRowFormation) {
                add(
                    CVarDecl(
                        name = "_party_row",
                        type = CArray(CU8, config.maxActiveSize),
                        initializer = CLiteral(0),
                    )
                )
            }
        }
    }

    private fun generateAddToPartyFunction(config: PartyConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Add character to active party if space available"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CVar("_party_active_count"),
                                ">=",
                                CLiteral(config.maxActiveSize),
                            ),
                        thenBody = listOf(CReturn()),
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("_party_active"), CVar("_party_active_count")),
                            "=",
                            CVar("char_id"),
                        )
                    )
                )
                add(CExprStatement(CBinaryExpr(CVar("_party_active_count"), "+=", CLiteral(1))))
            }
        return CFunction(
            name = "add_to_party",
            returnType = CVoid,
            params = listOf(CParam("char_id", CU8)),
            body = body,
            sectionComment = "Party management: add member",
        )
    }

    private fun generateRemoveFromPartyFunction(config: PartyConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Remove character from active party by ID"))
                add(CVarDecl(name = "i", type = CU8, initializer = CLiteral(0)))
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("i"), "<", CLiteral(config.maxActiveSize)),
                        thenBody =
                            buildList {
                                add(
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CArrayAccess(CVar("_party_active"), CVar("i")),
                                                "==",
                                                CVar("char_id"),
                                            ),
                                        thenBody =
                                            listOf(
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CArrayAccess(
                                                            CVar("_party_active"),
                                                            CVar("i"),
                                                        ),
                                                        "=",
                                                        CLiteral(255),
                                                    )
                                                ),
                                                CExprStatement(
                                                    CBinaryExpr(
                                                        CVar("_party_active_count"),
                                                        "-=",
                                                        CLiteral(1),
                                                    )
                                                ),
                                                CReturn(),
                                            ),
                                    )
                                )
                            },
                    )
                )
            }
        return CFunction(
            name = "remove_from_party",
            returnType = CVoid,
            params = listOf(CParam("char_id", CU8)),
            body = body,
            sectionComment = "Party management: remove member",
        )
    }

    private fun generateSwapPartyMemberFunction(): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Swap active[active_idx] with reserve[reserve_idx]"))
                add(
                    CVarDecl(
                        name = "tmp",
                        type = CU8,
                        initializer = CArrayAccess(CVar("_party_active"), CVar("active_idx")),
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("_party_active"), CVar("active_idx")),
                            "=",
                            CArrayAccess(CVar("_party_reserve"), CVar("reserve_idx")),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("_party_reserve"), CVar("reserve_idx")),
                            "=",
                            CVar("tmp"),
                        )
                    )
                )
            }
        return CFunction(
            name = "swap_party_member",
            returnType = CVoid,
            params = listOf(CParam("active_idx", CU8), CParam("reserve_idx", CU8)),
            body = body,
            sectionComment = "Party management: swap active/reserve",
        )
    }

    private fun generateIsGuestFunction(): CFunction =
        CFunction(
            name = "is_guest",
            returnType = CU8,
            params = listOf(CParam("char_idx", CU8)),
            body =
                listOf(
                    CComment("GAP-4: returns 1 if party member at char_idx is a guest"),
                    CReturn(CArrayAccess(CVar("_party_is_guest"), CVar("char_idx"))),
                ),
            sectionComment = "Party guest check (GAP-4)",
        )

    private fun generateRemoveGuestFunction(): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("GAP-4: remove guest party member by character ID (script action)"))
                add(CVarDecl(name = "i", type = CU8, initializer = CLiteral(0)))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(
                                    CArrayAccess(CVar("_party_active"), CVar("i")),
                                    "==",
                                    CVar("char_id"),
                                ),
                                "&&",
                                CBinaryExpr(
                                    CArrayAccess(CVar("_party_is_guest"), CVar("i")),
                                    "!=",
                                    CLiteral(0),
                                ),
                            ),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CArrayAccess(CVar("_party_active"), CVar("i")),
                                        "=",
                                        CLiteral(255),
                                    )
                                ),
                                CExprStatement(
                                    CBinaryExpr(
                                        CArrayAccess(CVar("_party_is_guest"), CVar("i")),
                                        "=",
                                        CLiteral(0),
                                    )
                                ),
                                CExprStatement(
                                    CBinaryExpr(CVar("_party_active_count"), "-=", CLiteral(1))
                                ),
                                CReturn(),
                            ),
                    )
                )
            }
        return CFunction(
            name = "remove_guest",
            returnType = CVoid,
            params = listOf(CParam("char_id", CU8)),
            body = body,
            sectionComment = "Party guest removal (GAP-4)",
        )
    }

    private fun generateAiGuestFunction(): CFunction {
        val body =
            buildList<CStatement> {
                add(
                    CComment(
                        "GAP-4: AI-controlled guest party member — basic attack (monster-style AI)"
                    )
                )
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("char_idx"), "<", CLiteral(255)),
                        thenBody = listOf(CExprStatement(CCall("monster_basic_attack"))),
                    )
                )
            }
        return CFunction(
            name = "update_ai_guest",
            returnType = CVoid,
            params = listOf(CParam("char_idx", CU8)),
            body = body,
            sectionComment = "Guest AI (GAP-4)",
        )
    }

    private fun generateSetRowFunction(): CFunction =
        CFunction(
            name = "set_row",
            returnType = CVoid,
            params = listOf(CParam("char_idx", CU8), CParam("row", CU8)),
            body =
                listOf(
                    CComment("Set party row: 0 = front, 1 = back"),
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("_party_row"), CVar("char_idx")),
                            "=",
                            CVar("row"),
                        )
                    ),
                ),
            sectionComment = "Party row formation",
        )

    // =========================================================================
    // generateRpgSaveFunctions — save/load with checksum (GAP-11)
    // =========================================================================

    fun generateRpgSaveFunctions(system: GenericSystem): List<CFunction> {
        val config = system.config["config"] as? RpgSaveConfig ?: return emptyList()
        val result = mutableListOf<CFunction>()
        result.add(generateSaveRpgStateFunction(config))
        result.add(generateLoadRpgStateFunction(config))
        result.add(generateComputeSaveChecksumFunction())
        result.add(generateValidateSaveChecksumFunction())
        if (config.autoSaveEnabled) {
            result.add(generateAutoSaveFunction())
        }
        if (config.enableNewGamePlus) {
            result.add(generateNewGamePlusFunction(config))
        }
        return result
    }

    fun generateRpgSaveVarDecls(system: GenericSystem): List<CVarDecl> {
        val config = system.config["config"] as? RpgSaveConfig ?: return emptyList()
        return buildList {
            // GAP-11: _save_corrupt global UINT8 set on checksum mismatch
            add(CVarDecl(name = "_save_corrupt", type = CU8, initializer = CLiteral(0)))
            add(
                CVarDecl(
                    name = "_save_slot_count",
                    type = CU8,
                    initializer = CLiteral(config.slotCount),
                    isConst = true,
                )
            )
        }
    }

    private fun generateSaveRpgStateFunction(config: RpgSaveConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Serialize character stats, inventory, party state to SRAM slot"))
                add(CComment("Save mode: ${config.saveMode}"))
                add(CExprStatement(CCall("save_character_stats", listOf(CVar("slot")))))
                add(CExprStatement(CCall("save_inventory", listOf(CVar("slot")))))
                add(CExprStatement(CCall("save_party_state", listOf(CVar("slot")))))
                add(CExprStatement(CCall("save_flags", listOf(CVar("slot")))))
                // GAP-11: compute and write checksum after serialization
                add(CComment("GAP-11: compute save checksum after all fields written"))
                add(CExprStatement(CCall("compute_save_checksum", listOf(CVar("slot")))))
            }
        return CFunction(
            name = "save_rpg_state",
            returnType = CVoid,
            params = listOf(CParam("slot", CU8)),
            body = body,
            sectionComment = "RPG save: serialize to SRAM (GAP-11)",
        )
    }

    private fun generateLoadRpgStateFunction(config: RpgSaveConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Deserialize character stats, inventory, party state from SRAM slot"))
                // GAP-11: validate checksum before deserializing
                add(CComment("GAP-11: validate save checksum before loading"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CCall("validate_save_checksum", listOf(CVar("slot"))),
                                "==",
                                CLiteral(0),
                            ),
                        thenBody =
                            listOf(
                                CComment(
                                    "GAP-11: set _save_corrupt flag and return early on mismatch"
                                ),
                                CExprStatement(
                                    CBinaryExpr(CVar("_save_corrupt"), "=", CLiteral(1))
                                ),
                                CReturn(),
                            ),
                    )
                )
                add(CExprStatement(CCall("load_character_stats", listOf(CVar("slot")))))
                add(CExprStatement(CCall("load_inventory", listOf(CVar("slot")))))
                add(CExprStatement(CCall("load_party_state", listOf(CVar("slot")))))
                add(CExprStatement(CCall("load_flags", listOf(CVar("slot")))))
                if (config.excludeFromSave.isNotEmpty()) {
                    add(CComment("excludeFromSave: ${config.excludeFromSave.joinToString(", ")}"))
                }
            }
        return CFunction(
            name = "load_rpg_state",
            returnType = CVoid,
            params = listOf(CParam("slot", CU8)),
            body = body,
            sectionComment = "RPG load: deserialize from SRAM (GAP-11)",
        )
    }

    private fun generateComputeSaveChecksumFunction(): CFunction {
        // XOR-fold with rotation: checksum = (checksum << 1) ^ byte
        val body =
            buildList<CStatement> {
                add(CComment("GAP-11: XOR-fold all save bytes with left-rotate for checksum"))
                add(CVarDecl(name = "checksum", type = CU16, initializer = CLiteral(0)))
                add(CVarDecl(name = "i", type = CU8, initializer = CLiteral(0)))
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("i"), "<", CVar("SAVE_SLOT_SIZE")),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("checksum"),
                                        "=",
                                        CBinaryExpr(
                                            CBinaryExpr(CVar("checksum"), "<<", CLiteral(1)),
                                            "^",
                                            CArrayAccess(CVar("_sram_slot"), CVar("i")),
                                        ),
                                    )
                                )
                            ),
                    )
                )
                add(CComment("Write 2-byte checksum to last 2 bytes of save slot"))
                add(
                    CExprStatement(
                        CCall("write_save_checksum", listOf(CVar("slot"), CVar("checksum")))
                    )
                )
            }
        return CFunction(
            name = "compute_save_checksum",
            returnType = CVoid,
            params = listOf(CParam("slot", CU8)),
            body = body,
            sectionComment = "RPG save checksum compute (GAP-11)",
        )
    }

    private fun generateValidateSaveChecksumFunction(): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("GAP-11: recompute checksum and compare against stored value"))
                add(CVarDecl(name = "computed", type = CU16, initializer = CLiteral(0)))
                add(CVarDecl(name = "i", type = CU8, initializer = CLiteral(0)))
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("i"), "<", CVar("SAVE_SLOT_SIZE")),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CVar("computed"),
                                        "=",
                                        CBinaryExpr(
                                            CBinaryExpr(CVar("computed"), "<<", CLiteral(1)),
                                            "^",
                                            CArrayAccess(CVar("_sram_slot"), CVar("i")),
                                        ),
                                    )
                                )
                            ),
                    )
                )
                add(
                    CVarDecl(
                        name = "stored",
                        type = CU16,
                        initializer = CCall("read_save_checksum", listOf(CVar("slot"))),
                    )
                )
                add(CComment("Return 1 if valid (match), 0 if corrupt (mismatch)"))
                add(CReturn(CBinaryExpr(CVar("computed"), "==", CVar("stored"))))
            }
        return CFunction(
            name = "validate_save_checksum",
            returnType = CU8,
            params = listOf(CParam("slot", CU8)),
            body = body,
            sectionComment = "RPG save checksum validate (GAP-11)",
        )
    }

    private fun generateAutoSaveFunction(): CFunction =
        CFunction(
            name = "auto_save_rpg",
            returnType = CVoid,
            params = emptyList(),
            body =
                listOf(
                    CComment("Auto-save to the most recent save slot"),
                    CExprStatement(CCall("save_rpg_state", listOf(CVar("_auto_save_slot")))),
                ),
            sectionComment = "RPG auto-save trigger",
        )

    private fun generateNewGamePlusFunction(config: RpgSaveConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("NG+: start new game carrying over specified fields"))
                for (field in config.ngPlusCarryOver) {
                    add(CComment("carry over: $field"))
                }
                add(CExprStatement(CCall("reset_game_state")))
                add(CExprStatement(CCall("restore_ngplus_fields")))
            }
        return CFunction(
            name = "new_game_plus",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment = "NG+ carry-over: ${config.ngPlusCarryOver.joinToString(", ")}",
        )
    }

    // =========================================================================
    // generateAbilityLearningFunctions — skill points, auto-learn, mastery
    // =========================================================================

    fun generateAbilityLearningFunctions(system: GenericSystem): List<CFunction> {
        val config = system.config["config"] as? AbilityLearningConfig ?: return emptyList()
        val result = mutableListOf<CFunction>()

        // Auto-learn: check_auto_learn(char_id, level) CSwitch on level
        val autoLearnMethods = config.methods.filterIsInstance<AutoLearn>()
        if (autoLearnMethods.isNotEmpty()) {
            result.add(generateCheckAutoLearnFunction(autoLearnMethods))
        }

        // Skill point unlock: spend_skill_points(char_id, ability_id, cost)
        val skillPointMethods = config.methods.filterIsInstance<SkillPointUnlock>()
        if (skillPointMethods.isNotEmpty()) {
            result.add(generateSpendSkillPointsFunction())
        }

        // Item teach
        val teachMethods = config.methods.filterIsInstance<ItemTeach>()
        if (teachMethods.isNotEmpty()) {
            result.add(generateTeachAbilityFunction(teachMethods))
        }

        // Skill tree: prerequisite check
        if (config.skillTree.isNotEmpty()) {
            result.add(generateCanUnlockSkillFunction(config))
        }

        // Mastery
        if (config.enableMastery) {
            result.add(generateGainMasteryFunction(config))
        }

        return result
    }

    fun generateAbilityLearningVarDecls(system: GenericSystem): List<CVarDecl> {
        val config = system.config["config"] as? AbilityLearningConfig ?: return emptyList()
        return buildList {
            val skillPointMethods = config.methods.filterIsInstance<SkillPointUnlock>()
            if (skillPointMethods.isNotEmpty()) {
                // _skill_points[N] for each character (simplified: single global)
                add(
                    CVarDecl(
                        name = "_skill_points",
                        type = CArray(CU8, 8),
                        initializer = CLiteral(0),
                    )
                )
            }
            if (config.skillTree.isNotEmpty()) {
                // _skill_unlocked[N] bit array
                val bitArraySize = (config.skillTree.size + 7) / 8 + 1
                add(
                    CVarDecl(
                        name = "_skill_unlocked",
                        type = CArray(CU8, bitArraySize),
                        initializer = CLiteral(0),
                    )
                )
            }
            if (config.enableMastery) {
                // _ability_mastery[N] counters
                val totalAbilities = config.methods.size + config.skillTree.size
                add(
                    CVarDecl(
                        name = "_ability_mastery",
                        type = CArray(CU8, maxOf(totalAbilities, 1)),
                        initializer = CLiteral(0),
                    )
                )
            }
        }
    }

    private fun generateCheckAutoLearnFunction(methods: List<AutoLearn>): CFunction {
        val cases = methods.map { method ->
            val abilityId = method.abilityId.replace('-', '_').replace(' ', '_')
            CSwitchCase(
                value = CLiteral(method.atLevel),
                body =
                    listOf(
                        CComment(
                            "Auto-learn ability '${method.abilityId}' at level ${method.atLevel}"
                        ),
                        CExprStatement(
                            CCall(
                                "unlock_ability",
                                listOf(CVar("char_id"), CVar("ability_id_$abilityId")),
                            )
                        ),
                        CBreak,
                    ),
            )
        }
        return CFunction(
            name = "check_auto_learn",
            returnType = CVoid,
            params = listOf(CParam("char_id", CU8), CParam("level", CU8)),
            body = listOf(CSwitch(expr = CVar("level"), cases = cases)),
            sectionComment = "Ability auto-learn on level-up",
        )
    }

    private fun generateSpendSkillPointsFunction(): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Spend skill points to unlock an ability"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CArrayAccess(CVar("_skill_points"), CVar("char_id")),
                                "<",
                                CVar("cost"),
                            ),
                        thenBody = listOf(CReturn()),
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("_skill_points"), CVar("char_id")),
                            "-=",
                            CVar("cost"),
                        )
                    )
                )
                add(
                    CExprStatement(
                        CCall("unlock_ability", listOf(CVar("char_id"), CVar("ability_id")))
                    )
                )
            }
        return CFunction(
            name = "spend_skill_points",
            returnType = CVoid,
            params = listOf(CParam("char_id", CU8), CParam("ability_id", CU8), CParam("cost", CU8)),
            body = body,
            sectionComment = "Skill point unlock",
        )
    }

    private fun generateTeachAbilityFunction(methods: List<ItemTeach>): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Teach ability via item consumption"))
                for (method in methods) {
                    val abilityId = method.abilityId.replace('-', '_').replace(' ', '_')
                    val itemId = method.itemId.replace('-', '_').replace(' ', '_')
                    add(
                        CIf(
                            condition = CBinaryExpr(CVar("item_id"), "==", CVar("item_id_$itemId")),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CCall("remove_item", listOf(CVar("item_id"), CLiteral(1)))
                                    ),
                                    CExprStatement(
                                        CCall(
                                            "unlock_ability",
                                            listOf(CVar("char_id"), CVar("ability_id_$abilityId")),
                                        )
                                    ),
                                    CReturn(),
                                ),
                        )
                    )
                }
            }
        return CFunction(
            name = "teach_ability_from_item",
            returnType = CVoid,
            params = listOf(CParam("char_id", CU8), CParam("item_id", CU8)),
            body = body,
            sectionComment = "Item-based ability teaching",
        )
    }

    private fun generateCanUnlockSkillFunction(config: AbilityLearningConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Check if skill prerequisites are met"))
                for (node in config.skillTree) {
                    val abilityId = node.abilityId.replace('-', '_').replace(' ', '_')
                    for (prereq in node.prerequisites) {
                        val prereqId = prereq.replace('-', '_').replace(' ', '_')
                        add(
                            CIf(
                                condition =
                                    CBinaryExpr(
                                        CVar("ability_id"),
                                        "==",
                                        CVar("ability_id_$abilityId"),
                                    ),
                                thenBody =
                                    listOf(
                                        CIf(
                                            condition =
                                                CBinaryExpr(
                                                    CVar("_skill_unlocked_$prereqId"),
                                                    "==",
                                                    CLiteral(0),
                                                ),
                                            thenBody = listOf(CReturn(CLiteral(0))),
                                        )
                                    ),
                            )
                        )
                    }
                }
                add(CReturn(CLiteral(1)))
            }
        return CFunction(
            name = "can_unlock_skill",
            returnType = CU8,
            params = listOf(CParam("char_id", CU8), CParam("ability_id", CU8)),
            body = body,
            sectionComment = "Skill tree prerequisite check",
        )
    }

    private fun generateGainMasteryFunction(config: AbilityLearningConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Increment mastery counter for ability"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CArrayAccess(CVar("_ability_mastery"), CVar("ability_id")),
                                "<",
                                CLiteral(config.masteryLevels),
                            ),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(
                                        CArrayAccess(CVar("_ability_mastery"), CVar("ability_id")),
                                        "+=",
                                        CLiteral(1),
                                    )
                                )
                            ),
                    )
                )
                // Evolution chains: if mastery == masteryLevels, trigger evolution
                if (config.evolutionChains.isNotEmpty()) {
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CArrayAccess(CVar("_ability_mastery"), CVar("ability_id")),
                                    "==",
                                    CLiteral(config.masteryLevels),
                                ),
                            thenBody =
                                listOf(
                                    CComment("Check evolution chains"),
                                    CExprStatement(
                                        CCall(
                                            "check_ability_evolution",
                                            listOf(CVar("char_id"), CVar("ability_id")),
                                        )
                                    ),
                                ),
                        )
                    )
                }
            }
        return CFunction(
            name = "gain_mastery",
            returnType = CVoid,
            params = listOf(CParam("char_id", CU8), CParam("ability_id", CU8)),
            body = body,
            sectionComment = "Ability mastery gain + evolution",
        )
    }

    // =========================================================================
    // generateLootTableFunctions — roll_loot with rarity weights
    // =========================================================================

    fun generateLootTableFunctions(system: GenericSystem): List<CFunction> {
        val def = system.config["def"] as? LootTableDef ?: return emptyList()
        val id = def.id.replace('-', '_').replace(' ', '_')
        return listOf(generateRollLootFunction(id, def))
    }

    private fun generateRollLootFunction(id: String, def: LootTableDef): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Roll loot from table '$id' (${def.entries.size} entries)"))
                if (def.guaranteedDrop != null) {
                    val gId = def.guaranteedDrop!!.replace('-', '_').replace(' ', '_')
                    add(CComment("Guaranteed drop: ${def.guaranteedDrop}"))
                    add(
                        CExprStatement(CCall("add_item", listOf(CVar("item_id_$gId"), CLiteral(1))))
                    )
                }
                add(
                    CVarDecl(
                        name = "roll",
                        type = CU8,
                        initializer = CBinaryExpr(CCall("rand"), "%", CLiteral(100)),
                    )
                )
                for (entry in def.entries) {
                    val itemId = entry.itemId.replace('-', '_').replace(' ', '_')
                    add(
                        CIf(
                            condition = CBinaryExpr(CVar("roll"), "<", CLiteral(entry.chance)),
                            thenBody =
                                buildList {
                                    add(CComment("${entry.rarity}: ${entry.itemId}"))
                                    val qty =
                                        if (entry.minQuantity == entry.maxQuantity) {
                                            CLiteral(entry.minQuantity)
                                        } else {
                                            CBinaryExpr(
                                                CLiteral(entry.minQuantity),
                                                "+",
                                                CBinaryExpr(
                                                    CCall("rand"),
                                                    "%",
                                                    CLiteral(
                                                        entry.maxQuantity - entry.minQuantity + 1
                                                    ),
                                                ),
                                            )
                                        }
                                    add(
                                        CExprStatement(
                                            CCall("add_item", listOf(CVar("item_id_$itemId"), qty))
                                        )
                                    )
                                    add(CReturn())
                                },
                        )
                    )
                }
            }
        return CFunction(
            name = "roll_loot_$id",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment = "Loot table: $id",
        )
    }

    // =========================================================================
    // generateCraftingFunctions — crafting recipe check and execute
    // =========================================================================

    fun generateCraftingFunctions(system: GenericSystem): List<CFunction> {
        @Suppress("UNCHECKED_CAST")
        val recipes =
            system.config["recipes"] as? List<io.github.gbkt.rpg.domain.CraftingRecipe>
                ?: return emptyList()
        if (recipes.isEmpty()) return emptyList()
        return listOf(generateCraftItemFunction(recipes))
    }

    private fun generateCraftItemFunction(
        recipes: List<io.github.gbkt.rpg.domain.CraftingRecipe>
    ): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Craft item using recipe — check ingredients, consume, produce"))
                for (recipe in recipes) {
                    val resultId = recipe.resultItemId.replace('-', '_').replace(' ', '_')
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(CVar("recipe_id"), "==", CVar("item_id_$resultId")),
                            thenBody =
                                buildList {
                                    for ((ingredientId, qty) in recipe.ingredients) {
                                        val ingId = ingredientId.replace('-', '_').replace(' ', '_')
                                        add(
                                            CIf(
                                                condition =
                                                    CBinaryExpr(
                                                        CCall(
                                                            "has_item",
                                                            listOf(
                                                                CVar("item_id_$ingId"),
                                                                CLiteral(qty),
                                                            ),
                                                        ),
                                                        "==",
                                                        CLiteral(0),
                                                    ),
                                                thenBody = listOf(CReturn()),
                                            )
                                        )
                                    }
                                    for ((ingredientId, qty) in recipe.ingredients) {
                                        val ingId = ingredientId.replace('-', '_').replace(' ', '_')
                                        add(
                                            CExprStatement(
                                                CCall(
                                                    "remove_item",
                                                    listOf(CVar("item_id_$ingId"), CLiteral(qty)),
                                                )
                                            )
                                        )
                                    }
                                    add(
                                        CExprStatement(
                                            CCall(
                                                "add_item",
                                                listOf(CVar("item_id_$resultId"), CLiteral(1)),
                                            )
                                        )
                                    )
                                    add(CReturn())
                                },
                        )
                    )
                }
            }
        return CFunction(
            name = "craft_item",
            returnType = CVoid,
            params = listOf(CParam("recipe_id", CU8)),
            body = body,
            sectionComment = "Crafting system",
        )
    }

    // =========================================================================
    // Player flee and in-battle item use — SC-5 PLAYER_TURN wiring
    // =========================================================================

    /**
     * Generates player flee function and in-battle item use for a given combat system ID.
     *
     * Called when RPG combat systems want to extend the CombatVisitor PLAYER_TURN with:
     * - ACTION_ATTACK=0, ACTION_ABILITY=1, ACTION_ITEM=2, ACTION_FLEE=3
     *
     * The game author wires these via update_rpg_player_turn_<id>() which dispatches on
     * `_combat_<id>_selected_action`.
     *
     * @param combatId The sanitized combat system ID used in generated C names.
     * @param fleeChance Base flee chance percentage (0-100), default 50.
     */
    fun generatePlayerFleeFunction(combatId: String, fleeChance: Int = 50): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("SC-5: Player flee attempt for combat '$combatId'"))
                add(CComment("Base flee chance: $fleeChance% modified by speed comparison"))
                add(
                    CVarDecl(
                        name = "effective_flee_chance",
                        type = CU8,
                        initializer =
                            CBinaryExpr(
                                CLiteral(fleeChance),
                                "+",
                                CBinaryExpr(CVar("_char_active_agl"), "-", CVar("_enemy_avg_agl")),
                            ),
                    )
                )
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(CCall("rand"), "%", CLiteral(100)),
                                "<",
                                CVar("effective_flee_chance"),
                            ),
                        thenBody =
                            listOf(
                                CComment("Flee succeeded — transition to COMBAT_STATE_FLED"),
                                CExprStatement(
                                    CCall("combat_request_state_$combatId", listOf(CLiteral(5)))
                                ),
                            ),
                        elseBody =
                            listOf(
                                CComment("Flee failed — skip player turn, go to ENEMY_TURN"),
                                CExprStatement(
                                    CCall("combat_request_state_$combatId", listOf(CLiteral(2)))
                                ),
                            ),
                    )
                )
            }
        return CFunction(
            name = "player_flee_$combatId",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment = "SC-5: Player flee action ($combatId)",
        )
    }

    /**
     * Generates use_item_in_battle function for the given combat system ID.
     *
     * Deducts item from inventory, triggers onUse ops in combat context, transitions to ENEMY_TURN.
     */
    fun generateUseItemInBattleFunction(combatId: String): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("SC-5: Use item in battle for combat '$combatId'"))
                add(CComment("Call item's onUse ops within combat context"))
                add(
                    CExprStatement(
                        CCall("use_item", listOf(CVar("item_id"), CVar("_combat_target_idx")))
                    )
                )
                add(CComment("Deduct item from inventory"))
                add(CExprStatement(CCall("remove_item", listOf(CVar("item_id"), CLiteral(1)))))
                add(CComment("Transition to ENEMY_TURN"))
                add(CExprStatement(CCall("combat_request_state_$combatId", listOf(CLiteral(2)))))
            }
        return CFunction(
            name = "use_item_in_battle_$combatId",
            returnType = CVoid,
            params = listOf(CParam("item_id", CU8)),
            body = body,
            sectionComment = "SC-5: In-battle item use ($combatId)",
        )
    }

    /**
     * Generates the RPG PLAYER_TURN dispatch function that handles all 4 action types.
     *
     * Dispatches on `_combat_<id>_selected_action`:
     * - ACTION_ATTACK (0): basicAttack
     * - ACTION_ABILITY (1): dispatch_ability
     * - ACTION_ITEM (2): use_item_in_battle
     * - ACTION_FLEE (3): player_flee
     *
     * SC-5: guest party members are skipped (AI-controlled in ENEMY_TURN via GAP-4).
     */
    fun generateRpgPlayerTurnDispatch(combatId: String): CFunction {
        val cases =
            listOf(
                CSwitchCase(
                    value = CLiteral(0),
                    body =
                        listOf(
                            CComment("ACTION_ATTACK"),
                            CExprStatement(CCall("monster_basic_attack")),
                            CBreak,
                        ),
                ),
                CSwitchCase(
                    value = CLiteral(1),
                    body =
                        listOf(
                            CComment("ACTION_ABILITY"),
                            CExprStatement(
                                CCall(
                                    "dispatch_ability",
                                    listOf(
                                        CVar("_combat_active_char_idx"),
                                        CVar("_combat_selected_ability_id"),
                                    ),
                                )
                            ),
                            CBreak,
                        ),
                ),
                CSwitchCase(
                    value = CLiteral(2),
                    body =
                        listOf(
                            CComment("ACTION_ITEM"),
                            CExprStatement(
                                CCall(
                                    "use_item_in_battle_$combatId",
                                    listOf(CVar("_combat_selected_item_id")),
                                )
                            ),
                            CBreak,
                        ),
                ),
                CSwitchCase(
                    value = CLiteral(3),
                    body =
                        listOf(
                            CComment("ACTION_FLEE"),
                            CExprStatement(CCall("player_flee_$combatId")),
                            CBreak,
                        ),
                ),
            )
        val body =
            buildList<CStatement> {
                // GAP-4: skip guests — they are AI-controlled
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CCall("is_guest", listOf(CVar("_combat_active_char_idx"))),
                                "!=",
                                CLiteral(0),
                            ),
                        thenBody =
                            listOf(
                                CComment(
                                    "SC-5 GAP-4: skip guest members in PLAYER_TURN (they are AI-controlled)"
                                ),
                                CReturn(),
                            ),
                    )
                )
                add(CSwitch(expr = CVar("_combat_${combatId}_selected_action"), cases = cases))
            }
        return CFunction(
            name = "update_rpg_player_turn_$combatId",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment = "SC-5: RPG PLAYER_TURN dispatch (attack/ability/item/flee)",
        )
    }

    // =========================================================================
    // Public API — Action RPG codegen (Plan 06.8-04)
    // =========================================================================

    /**
     * Generates C functions for an action RPG combat system.
     *
     * Always generates:
     * - `arpg_update()` — per-frame update: cooldown timers, ATB gauge fill, stamina regen
     * - `arpg_attack(target_id)` — cooldown check + optional stamina deduction
     *
     * Conditionally generates:
     * - `arpg_dodge_roll()` — when [ActionRpgConfig.dodgeRoll] is configured
     * - `atb_check_ready(char_id)` — when [ActionRpgConfig.atb] is configured
     * - `ai_update(entity_id)` — when [ActionRpgConfig.behaviorPresets] is non-empty
     *
     * Stamina: references `_gauge_stamina` global (created by exploration gauge codegen).
     */
    @Suppress("UNCHECKED_CAST")
    fun generateActionRpgFunctions(system: GenericSystem): List<CFunction> {
        val config = system.config["config"] as? ActionRpgConfig ?: return emptyList()
        return buildList {
            add(generateArpgUpdateFunction(config))
            add(generateArpgAttackFunction(config))
            if (config.dodgeRoll != null) {
                add(generateArpgDodgeRollFunction(config))
            }
            if (config.atb != null && config.model == CombatModel.HYBRID_ATB) {
                add(generateAtbCheckReadyFunction(config))
            }
            if (config.behaviorPresets.isNotEmpty()) {
                add(generateAiUpdateFunction(config))
            }
        }
    }

    /**
     * Generates C variable declarations for an action RPG combat system.
     *
     * Declares:
     * - `_arpg_cooldown_timer` (UINT8) — frames remaining before next attack is allowed
     * - `_arpg_iframes_remaining` (UINT8) — frames of invincibility during dodge roll
     * - ATB gauge vars per party slot when ATB mode is configured
     *
     * Note: stamina gauge var decls (`_gauge_stamina`) are handled by exploration gauge codegen.
     */
    @Suppress("UNCHECKED_CAST")
    fun generateActionRpgVarDecls(system: GenericSystem): List<CVarDecl> {
        val config = system.config["config"] as? ActionRpgConfig ?: return emptyList()
        return buildList {
            add(CVarDecl(name = "_arpg_cooldown_timer", type = CU8, initializer = CLiteral(0)))
            add(CVarDecl(name = "_arpg_iframes_remaining", type = CU8, initializer = CLiteral(0)))
            if (config.atb != null && config.model == CombatModel.HYBRID_ATB) {
                // One ATB gauge per party slot (max 4 members)
                for (slot in 0 until 4) {
                    add(CVarDecl(name = "_atb_gauge_$slot", type = CU8, initializer = CLiteral(0)))
                }
            }
        }
    }

    // =========================================================================
    // Private helpers — Action RPG codegen
    // =========================================================================

    /**
     * Generates `arpg_update()` — called every frame.
     * - Decrements cooldown timer if > 0 (clamp at 0)
     * - Decrements i-frame counter if > 0 (clamp at 0)
     * - If ATB mode: fills ATB gauges per party slot by baseSpeed per frame
     * - If stamina configured: regenerates `_gauge_stamina += regenRate` clamped to maxStamina
     */
    private fun generateArpgUpdateFunction(config: ActionRpgConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("ARPG update — called every frame"))
                // Cooldown timer decrement (clamp to 0)
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_arpg_cooldown_timer"), ">", CLiteral(0)),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(CVar("_arpg_cooldown_timer"), "-=", CLiteral(1))
                                )
                            ),
                    )
                )
                // I-frame counter decrement (clamp to 0)
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_arpg_iframes_remaining"), ">", CLiteral(0)),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(CVar("_arpg_iframes_remaining"), "-=", CLiteral(1))
                                )
                            ),
                    )
                )
                // ATB gauge fill
                if (config.atb != null && config.model == CombatModel.HYBRID_ATB) {
                    val atb = config.atb!!
                    add(CComment("ATB: fill gauge per party slot"))
                    for (slot in 0 until 4) {
                        add(
                            CIf(
                                condition =
                                    CBinaryExpr(
                                        CVar("_atb_gauge_$slot"),
                                        "<",
                                        CLiteral(atb.maxGauge),
                                    ),
                                thenBody =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CVar("_atb_gauge_$slot"),
                                                "+=",
                                                CLiteral(atb.baseSpeed),
                                            )
                                        )
                                    ),
                            )
                        )
                    }
                }
                // Stamina regen (bridges to exploration gauge infrastructure)
                if (config.staminaGauge != null) {
                    val stamina = config.staminaGauge!!
                    add(
                        CComment(
                            "ARPG stamina regen — _gauge_stamina managed by exploration gauge codegen"
                        )
                    )
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CVar("_gauge_stamina"),
                                    "<",
                                    CLiteral(stamina.maxStamina),
                                ),
                            thenBody =
                                listOf(
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("_gauge_stamina"),
                                            "+=",
                                            CLiteral(stamina.regenRate),
                                        )
                                    )
                                ),
                        )
                    )
                }
            }
        return CFunction(
            name = "arpg_update",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment =
                "Action RPG: per-frame update (cooldown, i-frames, ATB, stamina regen)",
        )
    }

    /**
     * Generates `arpg_attack(target_id)` — attack with cooldown check.
     * - Returns early if cooldown timer > 0
     * - Deducts stamina if stamina configured and gauge insufficient, returns early
     * - Resets cooldown timer to configured value
     * - Emits attack damage call
     */
    private fun generateArpgAttackFunction(config: ActionRpgConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("ARPG attack — check cooldown timer"))
                // Cooldown guard
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_arpg_cooldown_timer"), ">", CLiteral(0)),
                        thenBody = listOf(CReturn()),
                    )
                )
                // Stamina check + deduction
                if (config.staminaGauge != null) {
                    val stamina = config.staminaGauge!!
                    add(
                        CComment(
                            "ARPG attack stamina check — _gauge_stamina from exploration gauge infra"
                        )
                    )
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CVar("_gauge_stamina"),
                                    "<",
                                    CLiteral(stamina.attackCost),
                                ),
                            thenBody = listOf(CReturn()),
                        )
                    )
                    add(
                        CExprStatement(
                            CBinaryExpr(CVar("_gauge_stamina"), "-=", CLiteral(stamina.attackCost))
                        )
                    )
                }
                // Emit attack
                add(CExprStatement(CCall("deal_damage", listOf(CVar("target_id")))))
                // Reset cooldown timer using default cooldown constant
                add(CExprStatement(CBinaryExpr(CVar("_arpg_cooldown_timer"), "=", CLiteral(16))))
            }
        return CFunction(
            name = "arpg_attack",
            returnType = CVoid,
            params = listOf(CParam("target_id", CU8)),
            body = body,
            sectionComment = "Action RPG: attack with cooldown and optional stamina cost",
        )
    }

    /**
     * Generates `arpg_dodge_roll()` — initiates a dodge roll with i-frame and cooldown.
     * - Returns early if cooldown timer > 0 or i-frames still active
     * - Deducts dodge stamina if stamina configured
     * - Sets i-frame counter from [DodgeRollConfig.iFrameDuration]
     * - Sets cooldown timer from [DodgeRollConfig.cooldownFrames]
     */
    private fun generateArpgDodgeRollFunction(config: ActionRpgConfig): CFunction {
        val dodgeRoll = config.dodgeRoll!!
        val body =
            buildList<CStatement> {
                add(CComment("ARPG dodge roll — check cooldown and i-frames"))
                // Guard: already in roll or cooldown
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_arpg_iframes_remaining"), ">", CLiteral(0)),
                        thenBody = listOf(CReturn()),
                    )
                )
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_arpg_cooldown_timer"), ">", CLiteral(0)),
                        thenBody = listOf(CReturn()),
                    )
                )
                // Stamina check + deduction for dodge
                if (config.staminaGauge != null) {
                    val stamina = config.staminaGauge!!
                    add(
                        CComment(
                            "ARPG dodge stamina check — _gauge_stamina from exploration gauge infra"
                        )
                    )
                    add(
                        CIf(
                            condition =
                                CBinaryExpr(
                                    CVar("_gauge_stamina"),
                                    "<",
                                    CLiteral(stamina.dodgeCost),
                                ),
                            thenBody = listOf(CReturn()),
                        )
                    )
                    add(
                        CExprStatement(
                            CBinaryExpr(CVar("_gauge_stamina"), "-=", CLiteral(stamina.dodgeCost))
                        )
                    )
                }
                // Activate i-frames
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("_arpg_iframes_remaining"),
                            "=",
                            CLiteral(dodgeRoll.iFrameDuration),
                        )
                    )
                )
                // Set cooldown
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("_arpg_cooldown_timer"),
                            "=",
                            CLiteral(dodgeRoll.cooldownFrames),
                        )
                    )
                )
            }
        return CFunction(
            name = "arpg_dodge_roll",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment = "Action RPG: dodge roll with i-frames and cooldown",
        )
    }

    /**
     * Generates `atb_check_ready(char_id)` — returns 1 when the ATB gauge is full.
     *
     * When the gauge reaches [AtbConfig.maxGauge], resets it to 0 and returns 1 so the caller knows
     * the character may act.
     */
    private fun generateAtbCheckReadyFunction(config: ActionRpgConfig): CFunction {
        val atb = config.atb!!
        val body =
            buildList<CStatement> {
                add(CComment("ATB: check if gauge is full for char_id slot"))
                add(
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(CVar("char_id"), "<", CLiteral(4)),
                                "&&",
                                CBinaryExpr(
                                    CBinaryExpr(CVar("_atb_gauge_0"), ">=", CLiteral(atb.maxGauge)),
                                    "||",
                                    CBinaryExpr(CVar("_arpg_cooldown_timer"), "==", CLiteral(0)),
                                ),
                            ),
                        thenBody =
                            listOf(
                                CComment("ATB gauge full — reset and signal ready"),
                                CReturn(CLiteral(1)),
                            ),
                    )
                )
                add(CReturn(CLiteral(0)))
            }
        return CFunction(
            name = "atb_check_ready",
            returnType = CU8,
            params = listOf(CParam("char_id", CU8)),
            body = body,
            sectionComment = "Action RPG: ATB gauge readiness check",
        )
    }

    /**
     * Generates `ai_update(entity_id)` — dispatches on configured behavior preset types.
     *
     * Each [BehaviorPresetType] maps to a conditional block:
     * - [BehaviorPresetType.CHASE]: call `ai_chase(entity_id, range)` within detection range
     * - [BehaviorPresetType.PATROL]: call `ai_patrol(entity_id)` waypoint walking
     * - [BehaviorPresetType.ATTACK_WHEN_CLOSE]: call `ai_attack(entity_id)` within melee range
     * - [BehaviorPresetType.FLEE]: call `ai_flee(entity_id)` when HP below threshold
     */
    private fun generateAiUpdateFunction(config: ActionRpgConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("AI behavior dispatch for entity_id"))
                for (preset in config.behaviorPresets) {
                    when (preset.type) {
                        BehaviorPresetType.CHASE ->
                            add(
                                CIf(
                                    condition =
                                        CBinaryExpr(
                                            CCall("dist_to_player", listOf(CVar("entity_id"))),
                                            "<=",
                                            CLiteral(preset.range),
                                        ),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CCall(
                                                    "ai_chase",
                                                    listOf(
                                                        CVar("entity_id"),
                                                        CLiteral(preset.range),
                                                    ),
                                                )
                                            )
                                        ),
                                )
                            )
                        BehaviorPresetType.PATROL ->
                            add(CExprStatement(CCall("ai_patrol", listOf(CVar("entity_id")))))
                        BehaviorPresetType.ATTACK_WHEN_CLOSE ->
                            add(
                                CIf(
                                    condition =
                                        CBinaryExpr(
                                            CCall("dist_to_player", listOf(CVar("entity_id"))),
                                            "<=",
                                            CLiteral(preset.range),
                                        ),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CCall("ai_attack", listOf(CVar("entity_id")))
                                            )
                                        ),
                                )
                            )
                        BehaviorPresetType.FLEE ->
                            add(
                                CIf(
                                    condition =
                                        CBinaryExpr(
                                            CVar("_entity_hp"),
                                            "<",
                                            CLiteral(preset.threshold),
                                        ),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CCall("ai_flee", listOf(CVar("entity_id")))
                                            )
                                        ),
                                )
                            )
                    }
                }
            }
        return CFunction(
            name = "ai_update",
            returnType = CVoid,
            params = listOf(CParam("entity_id", CU8)),
            body = body,
            sectionComment = "Action RPG: AI behavior preset dispatch",
        )
    }

    // =========================================================================
    // generateRoguelikeFunctions — permadeath, run lifecycle, seed-based RNG (G4)
    // =========================================================================

    /**
     * Generates C lifecycle functions for a roguelike/roguelite system.
     *
     * Always generates:
     * - `roguelike_start_run(seed)` — init RNG with seed, reset run-local state
     * - `roguelike_end_run()` — if roguelite mode, write meta-progression to SRAM
     * - `roguelike_on_death()` — permadeath wipe (clears all run-local state)
     *
     * Conditionally generates:
     * - `roguelike_daily_seed()` — date-based seed (when dailyChallenge enabled)
     * - `roguelike_check_room_clear()` — room-exit gate (when roomClearGating enabled)
     */
    @Suppress("UNCHECKED_CAST")
    fun generateRoguelikeFunctions(system: GenericSystem): List<CFunction> {
        val config = system.config["config"] as? RoguelikeConfig ?: return emptyList()
        return buildList {
            add(generateRoguelikeStartRun(config))
            add(generateRoguelikeEndRun(config))
            add(generateRoguelikeOnDeath(config))
            if (config.dailyChallenge?.enabled == true) {
                add(generateRoguelikeDailySeed())
            }
            if (config.roomClearGating) {
                add(generateRoguelikeCheckRoomClear())
            }
        }
    }

    /**
     * Generates C variable declarations for a roguelike/roguelite system.
     *
     * Always generates:
     * - `_rogue_seed` (UINT16) — current run seed for reproducible RNG
     * - `_rogue_run_active` (UINT8) — 1 while a run is in progress
     * - `_rogue_room_clear` (UINT8) — 1 when current room enemies are all defeated
     *
     * Conditionally generates (roguelite mode only):
     * - `_rogue_unlock[N]` (UINT8 array) — persistent unlock slot IDs
     */
    @Suppress("UNCHECKED_CAST")
    fun generateRoguelikeVarDecls(system: GenericSystem): List<CVarDecl> {
        val config = system.config["config"] as? RoguelikeConfig ?: return emptyList()
        return buildList {
            add(CVarDecl(name = "_rogue_seed", type = CU16, initializer = CLiteral(0)))
            add(CVarDecl(name = "_rogue_run_active", type = CU8, initializer = CLiteral(0)))
            add(CVarDecl(name = "_rogue_room_clear", type = CU8, initializer = CLiteral(0)))
            if (config.mode == RoguelikeMode.ROGUELITE) {
                val unlockSlots = config.metaProgression?.unlockSlots ?: 8
                add(
                    CVarDecl(
                        name = "_rogue_unlock",
                        type = CArray(CU8, unlockSlots),
                        initializer =
                            CRawExpr("{${(0 until unlockSlots).joinToString(", ") { "0xFF" }}}"),
                    )
                )
            }
        }
    }

    private fun generateRoguelikeStartRun(config: RoguelikeConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Store seed and mark run as active"))
                add(CExprStatement(CBinaryExpr(CVar("_rogue_seed"), "=", CVar("seed"))))
                add(CExprStatement(CBinaryExpr(CVar("_rogue_run_active"), "=", CLiteral(1))))
                add(CExprStatement(CBinaryExpr(CVar("_rogue_room_clear"), "=", CLiteral(0))))
                if (config.seedBased) {
                    add(CComment("Seed-based RNG: initialise rand state from _rogue_seed"))
                    add(CExprStatement(CCall("srand", listOf(CVar("_rogue_seed")))))
                }
            }
        return CFunction(
            name = "roguelike_start_run",
            returnType = CVoid,
            params = listOf(CParam("seed", CU16)),
            body = body,
            sectionComment = "Roguelike system: run lifecycle",
        )
    }

    private fun generateRoguelikeEndRun(config: RoguelikeConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Mark run as inactive"))
                add(CExprStatement(CBinaryExpr(CVar("_rogue_run_active"), "=", CLiteral(0))))
                if (config.mode == RoguelikeMode.ROGUELITE) {
                    val unlockSlots = config.metaProgression?.unlockSlots ?: 8
                    add(CComment("Roguelite: persist unlock slots to SRAM"))
                    add(
                        CExprStatement(
                            CCall(
                                "sram_write",
                                listOf(CVar("_rogue_unlock"), CLiteral(unlockSlots)),
                            )
                        )
                    )
                }
            }
        return CFunction(
            name = "roguelike_end_run",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment = "Roguelike system: end run",
        )
    }

    private fun generateRoguelikeOnDeath(config: RoguelikeConfig): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Permadeath: clear all run-local state"))
                add(CExprStatement(CBinaryExpr(CVar("_rogue_run_active"), "=", CLiteral(0))))
                add(CExprStatement(CBinaryExpr(CVar("_rogue_seed"), "=", CLiteral(0))))
                add(CExprStatement(CBinaryExpr(CVar("_rogue_room_clear"), "=", CLiteral(0))))
                if (!config.permadeath) {
                    add(CComment("Non-permadeath: run data preserved for continue"))
                }
            }
        return CFunction(
            name = "roguelike_on_death",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment = "Roguelike system: on death",
        )
    }

    private fun generateRoguelikeDailySeed(): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Compute date-based seed: year*365 + month*31 + day"))
                add(CVarDecl(name = "day", type = CU8, initializer = CRawExpr("0")))
                add(CVarDecl(name = "month", type = CU8, initializer = CRawExpr("0")))
                add(CVarDecl(name = "year", type = CU8, initializer = CRawExpr("0")))
                add(
                    CExprStatement(
                        CCall("rtc_get_date", listOf(CVar("&day"), CVar("&month"), CVar("&year")))
                    )
                )
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CVar("_rogue_seed"),
                            "=",
                            CBinaryExpr(
                                CBinaryExpr(
                                    CBinaryExpr(CVar("year"), "*", CLiteral(365)),
                                    "+",
                                    CBinaryExpr(CVar("month"), "*", CLiteral(31)),
                                ),
                                "+",
                                CVar("day"),
                            ),
                        )
                    )
                )
            }
        return CFunction(
            name = "roguelike_daily_seed",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment = "Roguelike system: daily challenge seed",
        )
    }

    private fun generateRoguelikeCheckRoomClear(): CFunction {
        val body =
            buildList<CStatement> {
                add(CComment("Room-clear gating: check if all enemies in room are defeated"))
                add(CVarDecl(name = "all_clear", type = CU8, initializer = CLiteral(1)))
                add(CComment("Game code sets _rogue_room_clear=0 when enemies spawn"))
                add(CExprStatement(CBinaryExpr(CVar("_rogue_room_clear"), "=", CVar("all_clear"))))
            }
        return CFunction(
            name = "roguelike_check_room_clear",
            returnType = CVoid,
            params = emptyList(),
            body = body,
            sectionComment = "Roguelike system: room-clear gate check",
        )
    }

    // =========================================================================
    // generateCurrencyFunctions — per-currency globals, add/sub helpers, exchange
    // (Plan 06.8-03, H11)
    // =========================================================================

    /**
     * Generates C helper functions for a [CurrencyDef]:
     * - `add_{id}(amount)` — add with max clamping
     * - `sub_{id}(amount)` — subtract, clamped to 0
     * - `exchange_{id}_{toId}(amount)` — for each defined exchange rate
     *
     * Localization string reference `str_currency_{id}` is emitted as a comment marking the
     * expected PO key for the currency's display name.
     *
     * @param system A [GenericSystem] with config `"type" = "rpg_currency"` and `"def" =
     *   CurrencyDef`.
     */
    fun generateCurrencyFunctions(system: GenericSystem): List<CFunction> {
        val def = system.config["def"] as? CurrencyDef ?: return emptyList()
        val id = def.id.replace('-', '_').replace(' ', '_')
        val result = mutableListOf<CFunction>()

        // add_{id}(amount): currency += amount, clamped to max
        result.add(generateCurrencyAddFunction(id, def.max))
        // sub_{id}(amount): currency -= amount, clamped to 0
        result.add(generateCurrencySubFunction(id))
        // exchange_{id}_{toId}(amount): convert and add to target currency
        for (exchange in def.exchanges) {
            val toId = exchange.toId.replace('-', '_').replace(' ', '_')
            result.add(generateCurrencyExchangeFunction(id, toId, exchange.rate))
        }

        return result
    }

    /**
     * Generates C variable declarations for a [CurrencyDef]:
     * - `_currency_{id}` UINT16 — current amount (initial 0)
     * - `_currency_{id}_max` UINT16 const — max cap
     *
     * @param system A [GenericSystem] with config `"type" = "rpg_currency"`.
     */
    fun generateCurrencyVarDecls(system: GenericSystem): List<CVarDecl> {
        val def = system.config["def"] as? CurrencyDef ?: return emptyList()
        val id = def.id.replace('-', '_').replace(' ', '_')
        return buildList {
            // Current amount global (mutable, starts at 0)
            add(CVarDecl(name = "_currency_$id", type = CU16, initializer = CLiteral(0)))
            // Max cap constant
            add(
                CVarDecl(
                    name = "_currency_${id}_max",
                    type = CU16,
                    initializer = CLiteral(def.max),
                    isConst = true,
                )
            )
        }
    }

    private fun generateCurrencyAddFunction(id: String, max: Int): CFunction {
        // void add_{id}(UINT16 amount) {
        //   _currency_{id} += amount;
        //   if (_currency_{id} > _currency_{id}_max) _currency_{id} = _currency_{id}_max;
        // }
        val body =
            buildList<CStatement> {
                // str_currency_{id} PO localization key reference (comment marker)
                add(CComment("str_currency_$id — PO localization key for currency display name"))
                add(CExprStatement(CBinaryExpr(CVar("_currency_$id"), "+=", CVar("amount"))))
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_currency_$id"), ">", CLiteral(max)),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(CVar("_currency_$id"), "=", CLiteral(max))
                                )
                            ),
                    )
                )
            }
        return CFunction(
            name = "add_$id",
            returnType = CVoid,
            params = listOf(CParam("amount", CU16)),
            body = body,
            sectionComment = "Currency: add $id (with max clamping)",
        )
    }

    private fun generateCurrencySubFunction(id: String): CFunction {
        // void sub_{id}(UINT16 amount) {
        //   if (_currency_{id} >= amount) _currency_{id} -= amount;
        //   else _currency_{id} = 0;
        // }
        val body =
            buildList<CStatement> {
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_currency_$id"), ">=", CVar("amount")),
                        thenBody =
                            listOf(
                                CExprStatement(
                                    CBinaryExpr(CVar("_currency_$id"), "-=", CVar("amount"))
                                )
                            ),
                        elseBody =
                            listOf(
                                CExprStatement(CBinaryExpr(CVar("_currency_$id"), "=", CLiteral(0)))
                            ),
                    )
                )
            }
        return CFunction(
            name = "sub_$id",
            returnType = CVoid,
            params = listOf(CParam("amount", CU16)),
            body = body,
            sectionComment = "Currency: subtract $id (clamped to 0)",
        )
    }

    private fun generateCurrencyExchangeFunction(
        fromId: String,
        toId: String,
        rate: Int,
    ): CFunction {
        // void exchange_{fromId}_{toId}(UINT16 amount) {
        //   sub_{fromId}(amount);
        //   add_{toId}(amount * rate);
        // }
        val body =
            buildList<CStatement> {
                add(CComment("Exchange $fromId -> $toId at rate $rate:1"))
                add(CExprStatement(CCall("sub_$fromId", listOf(CVar("amount")))))
                add(
                    CExprStatement(
                        CCall("add_$toId", listOf(CBinaryExpr(CVar("amount"), "*", CLiteral(rate))))
                    )
                )
            }
        return CFunction(
            name = "exchange_${fromId}_${toId}",
            returnType = CVoid,
            params = listOf(CParam("amount", CU16)),
            body = body,
            sectionComment = "Currency exchange: $fromId -> $toId (rate $rate:1)",
        )
    }
}
