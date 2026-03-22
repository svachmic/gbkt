/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipelineV2
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.HealEffect
import io.github.gbkt.core.ir.ItemCategoryDef
import io.github.gbkt.core.ir.ItemDef
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.rpg.domain.AbilityDef
import io.github.gbkt.rpg.domain.AbilityLearnEntry
import io.github.gbkt.rpg.domain.ActionNode
import io.github.gbkt.rpg.domain.BasicAttack
import io.github.gbkt.rpg.domain.BehaviorNode
import io.github.gbkt.rpg.domain.ClassDef
import io.github.gbkt.rpg.domain.CombatStats
import io.github.gbkt.rpg.domain.CooldownNode
import io.github.gbkt.rpg.domain.CurrencyDef
import io.github.gbkt.rpg.domain.CurrencyExchange
import io.github.gbkt.rpg.domain.DailyChallengeConfig
import io.github.gbkt.rpg.domain.DifficultyTier
import io.github.gbkt.rpg.domain.EffectCategory
import io.github.gbkt.rpg.domain.EquipSetDef
import io.github.gbkt.rpg.domain.EquipmentConfig
import io.github.gbkt.rpg.domain.ExpCurve
import io.github.gbkt.rpg.domain.MetaProgressionConfig
import io.github.gbkt.rpg.domain.MonsterDef
import io.github.gbkt.rpg.domain.PhaseThresholdNode
import io.github.gbkt.rpg.domain.ResistType
import io.github.gbkt.rpg.domain.RoguelikeConfig
import io.github.gbkt.rpg.domain.RoguelikeMode
import io.github.gbkt.rpg.domain.SelectorNode
import io.github.gbkt.rpg.domain.SetBonusTier
import io.github.gbkt.rpg.domain.StackMode
import io.github.gbkt.rpg.domain.StatGrowthRate
import io.github.gbkt.rpg.domain.StatModifier
import io.github.gbkt.rpg.domain.StatusEffectDef
import io.github.gbkt.rpg.domain.TargetingMode
import io.github.gbkt.rpg.domain.UseAbility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// RPG CODEGEN TESTS (Plan 06.5-03 success criteria SC-2, SC-3, SC-4, SC-13)
// Tests covering:
//   - Plan 06.5-01: Character stat struct globals, level/exp vars, level_up fn, _item_names table
//   - Plan 06.5-03: Ability codegen, status effect codegen, monster AI codegen
// =============================================================================

// =============================================================================
// Test fixture helpers
// =============================================================================

private fun buildCharacterGameIR(
    id: String = "hero",
    stats: CombatStats =
        CombatStats(hp = 20, sp = 10, atk = 5, def = 3, matk = 8, mdef = 4, agl = 12),
    level: Int = 1,
    onLevelUpOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList(),
): GameIR {
    val system =
        GenericSystem(
            id = id,
            config =
                mapOf(
                    "type" to "rpg_character_system",
                    "stats" to stats,
                    "level" to level,
                    "maxLevel" to 99,
                    "expCurve" to ExpCurve.STANDARD,
                    "onLevelUpOps" to onLevelUpOps,
                ),
        )
    return GameIR(
        name = "RpgTest",
        config = CartridgeConfig(),
        scenes = listOf(SceneIR(id = "main")),
        systems = listOf(system),
        startScene = "main",
    )
}

private fun buildItemGameIR(items: List<ItemDef> = emptyList()): GameIR =
    GameIR(
        name = "ItemTest",
        config = CartridgeConfig(),
        scenes = listOf(SceneIR(id = "main")),
        items = items,
        itemCategories =
            if (items.isNotEmpty()) listOf(ItemCategoryDef("consumable", defaultMaxStack = 5))
            else emptyList(),
        startScene = "main",
    )

private fun buildAbilitySystem(
    id: String = "fireball",
    name: String = "Fireball",
    spCost: Int = 8,
    targeting: TargetingMode = TargetingMode.SINGLE_ENEMY,
    appliesEffect: String? = null,
    effectChance: Int = 100,
): GenericSystem {
    val def =
        AbilityDef(
            id = id,
            name = name,
            spCost = spCost,
            targeting = targeting,
            appliesEffect = appliesEffect,
            effectChance = effectChance,
        )
    return GenericSystem(id = id, config = mapOf("type" to "rpg_ability", "def" to def))
}

private fun buildStatusEffectSystem(
    id: String = "poison",
    name: String = "Poison",
    category: EffectCategory = EffectCategory.DOT,
    duration: Int = 3,
    damagePerTurn: Int = 5,
    applyChance: Int = 100,
    resistType: ResistType = ResistType.FLAT,
    immuneToEffects: Set<String> = emptySet(),
    stackMode: StackMode = StackMode.REFRESH_DURATION,
    maxStacks: Int = 1,
    perStackScaling: Boolean = false,
    onStackAppliedOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList(),
    onStackRemovedOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList(),
): GenericSystem {
    val def =
        StatusEffectDef(
            id = id,
            name = name,
            category = category,
            duration = duration,
            damagePerTurn = damagePerTurn,
            applyChance = applyChance,
            resistType = resistType,
            immuneToEffects = immuneToEffects,
            stackMode = stackMode,
            maxStacks = maxStacks,
            perStackScaling = perStackScaling,
            onStackAppliedOps = onStackAppliedOps,
            onStackRemovedOps = onStackRemovedOps,
        )
    return GenericSystem(id = id, config = mapOf("type" to "rpg_status_effect", "def" to def))
}

private fun buildMonsterSystem(
    id: String = "goblin",
    name: String = "Goblin",
    behaviorTree: BehaviorNode? = SelectorNode(listOf(ActionNode(BasicAttack()))),
    difficulty: DifficultyTier = DifficultyTier.NORMAL,
    allowGlobalRepeatPrevention: Boolean = false,
    abilityCooldowns: Map<String, Int> = emptyMap(),
): GenericSystem {
    val def =
        MonsterDef(
            id = id,
            name = name,
            stats = CombatStats(hp = 30, atk = 5, def = 3),
            behaviorTree = behaviorTree,
            difficulty = difficulty,
            allowGlobalRepeatPrevention = allowGlobalRepeatPrevention,
            abilityCooldowns = abilityCooldowns,
        )
    return GenericSystem(id = id, config = mapOf("type" to "rpg_monster", "def" to def))
}

private fun buildMinimalGameIR(): GameIR =
    GameIR(
        name = "Test",
        config = CartridgeConfig(),
        scenes = listOf(SceneIR(id = "main")),
        startScene = "main",
    )

/** Generate main.c from a [GameIR] via [GBDKPipelineV2]. */
private fun generateMainC(gameIR: GameIR): String {
    val pipeline = GBDKPipelineV2()
    val output = pipeline.generate(gameIR)
    return output.files["main.c"] ?: error("main.c not generated")
}

/** Collect all CRawCode statements recursively from a list of CStatement. */
private fun collectRawCode(statements: List<CStatement>): List<CRawCode> {
    val result = mutableListOf<CRawCode>()
    for (stmt in statements) {
        when (stmt) {
            is CRawCode -> result.add(stmt)
            is io.github.gbkt.backend.gbdk.codegen.ast.CIf -> {
                result.addAll(collectRawCode(stmt.thenBody))
                result.addAll(collectRawCode(stmt.elseBody))
            }
            is io.github.gbkt.backend.gbdk.codegen.ast.CWhile ->
                result.addAll(collectRawCode(stmt.body))
            is io.github.gbkt.backend.gbdk.codegen.ast.CFor ->
                result.addAll(collectRawCode(stmt.body))
            is io.github.gbkt.backend.gbdk.codegen.ast.CBlock ->
                result.addAll(collectRawCode(stmt.statements))
            else -> {
                /* non-container node */
            }
        }
    }
    return result
}

class RpgCodegenTest {

    private val pipeline = GBDKPipelineV2()

    // =========================================================================
    // Tests from Plan 06.5-01: Character stat structs and item names table
    // =========================================================================

    @Test
    fun `character stat struct generates all 7 stat globals`() {
        val gameIR =
            buildCharacterGameIR(
                id = "hero",
                stats =
                    CombatStats(hp = 20, sp = 10, atk = 5, def = 3, matk = 8, mdef = 4, agl = 12),
            )
        val mainC = generateMainC(gameIR)

        assertTrue(
            mainC.contains("_char_hero_hp"),
            "Expected '_char_hero_hp' stat global in main.c",
        )
        assertTrue(
            mainC.contains("_char_hero_sp"),
            "Expected '_char_hero_sp' stat global in main.c",
        )
        assertTrue(
            mainC.contains("_char_hero_atk"),
            "Expected '_char_hero_atk' stat global in main.c",
        )
        assertTrue(
            mainC.contains("_char_hero_def"),
            "Expected '_char_hero_def' stat global in main.c",
        )
        assertTrue(
            mainC.contains("_char_hero_matk"),
            "Expected '_char_hero_matk' stat global in main.c",
        )
        assertTrue(
            mainC.contains("_char_hero_mdef"),
            "Expected '_char_hero_mdef' stat global in main.c",
        )
        assertTrue(
            mainC.contains("_char_hero_agl"),
            "Expected '_char_hero_agl' stat global in main.c",
        )
    }

    @Test
    fun `character stat struct generates mutable level and exp variables`() {
        val gameIR = buildCharacterGameIR(id = "hero", level = 5)
        val mainC = generateMainC(gameIR)

        assertTrue(
            mainC.contains("_char_hero_level"),
            "Expected '_char_hero_level' mutable variable in main.c",
        )
        assertTrue(
            mainC.contains("_char_hero_exp"),
            "Expected '_char_hero_exp' mutable UINT16 variable in main.c",
        )
    }

    @Test
    fun `character stat values are emitted as const UINT8 initializers`() {
        val gameIR =
            buildCharacterGameIR(
                id = "hero",
                stats = CombatStats(hp = 50, sp = 20, atk = 15, def = 10),
            )
        val mainC = generateMainC(gameIR)

        assertTrue(mainC.contains("50"), "Expected '50' for hp=50 in main.c")
        assertTrue(mainC.contains("const"), "Expected 'const' qualifier on stat globals in main.c")
    }

    @Test
    fun `_item_names table generates const array for game with items`() {
        val items =
            listOf(
                ItemDef(
                    id = "potion",
                    name = "Potion",
                    categoryId = "consumable",
                    effects = listOf(HealEffect(50)),
                ),
                ItemDef(id = "sword", name = "Iron Sword", categoryId = "consumable"),
            )
        val gameIR = buildItemGameIR(items = items)
        val mainC = generateMainC(gameIR)

        assertTrue(mainC.contains("_item_names"), "Expected '_item_names' lookup table in main.c")
        assertTrue(
            mainC.contains("\"Potion\""),
            "Expected '\"Potion\"' string in _item_names table",
        )
        assertTrue(
            mainC.contains("\"Iron Sword\""),
            "Expected '\"Iron Sword\"' string in _item_names table",
        )
    }

    @Test
    fun `empty items produce no _item_names table`() {
        val gameIR = buildItemGameIR(items = emptyList())
        val mainC = generateMainC(gameIR)

        assertFalse(
            mainC.contains("_item_names"),
            "Expected NO '_item_names' table when game has no items",
        )
    }

    @Test
    fun `level_up function generated for character with onLevelUp ops`() {
        val onLevelUpOps = listOf(NavigateTo("levelup_event"))
        val gameIR = buildCharacterGameIR(id = "hero", onLevelUpOps = onLevelUpOps)
        val mainC = generateMainC(gameIR)

        assertTrue(
            mainC.contains("level_up_hero"),
            "Expected 'level_up_hero' function when onLevelUp ops are non-empty",
        )
    }

    @Test
    fun `no level_up function generated for character with empty onLevelUp ops`() {
        val gameIR = buildCharacterGameIR(id = "hero", onLevelUpOps = emptyList())
        val mainC = generateMainC(gameIR)

        assertFalse(
            mainC.contains("void level_up_hero"),
            "Expected NO 'level_up_hero' function when onLevelUp ops are empty",
        )
    }

    // =========================================================================
    // Tests from Plan 06.5-03: Ability codegen (SC-2)
    // =========================================================================

    @Test
    fun `ability codegen generates use_ability function with SP cost check`() {
        val system = buildAbilitySystem(id = "fireball", spCost = 8)
        val gameIR = buildMinimalGameIR().copy(systems = listOf(system))
        val visitor = RpgVisitor(gameIR)

        val functions = visitor.generateAbilityFunctions(system)

        assertTrue(functions.isNotEmpty(), "Expected at least one ability function")
        val useAbility = functions.find { it.name == "use_ability_fireball" }
        assertTrue(useAbility != null, "Expected 'use_ability_fireball' function")

        val bodyText =
            useAbility!!.body.filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CIf>()
        val spCheck =
            bodyText.any { ifStmt ->
                val cond = ifStmt.condition
                cond is io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr &&
                    (cond.left is io.github.gbkt.backend.gbdk.codegen.ast.CVar &&
                        (cond.left as io.github.gbkt.backend.gbdk.codegen.ast.CVar)
                            .name
                            .contains("sp"))
            }
        assertTrue(
            spCheck,
            "Expected SP cost check (if _char_active_sp < spCost) in use_ability body",
        )
    }

    @Test
    fun `ability dispatch function generates CSwitch on ability_id`() {
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val dispatchFn = visitor.generateAbilityDispatch(listOf("fireball", "cure_wounds"))

        assertTrue(
            dispatchFn.name == "dispatch_ability",
            "Expected 'dispatch_ability' function name",
        )
        assertTrue(dispatchFn.params.size == 2, "Expected 2 params: combatant_idx and ability_id")

        val switchStmt =
            dispatchFn.body
                .filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CSwitch>()
                .firstOrNull()
        assertTrue(switchStmt != null, "Expected CSwitch statement in dispatch_ability body")
        assertTrue(switchStmt!!.cases.size == 2, "Expected 2 switch cases (one per ability)")
    }

    // =========================================================================
    // Tests from Plan 06.5-03: Status effect codegen (SC-3)
    // =========================================================================

    @Test
    fun `status effect codegen generates apply and tick functions`() {
        val system = buildStatusEffectSystem(id = "poison", damagePerTurn = 5)
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val functions = visitor.generateStatusEffectFunctions(system)

        val functionNames = functions.map { it.name }
        assertTrue(
            functionNames.contains("apply_effect_poison"),
            "Expected 'apply_effect_poison' function",
        )
        assertTrue(
            functionNames.contains("tick_effect_poison"),
            "Expected 'tick_effect_poison' function",
        )
        assertTrue(
            functionNames.contains("remove_effect_poison"),
            "Expected 'remove_effect_poison' function (GAP-8)",
        )
    }

    @Test
    fun `stat-based resist contest modifies apply chance in apply_effect codegen`() {
        val system =
            buildStatusEffectSystem(
                id = "sleep",
                resistType = ResistType.STAT_CONTEST,
                applyChance = 70,
            )
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val functions = visitor.generateStatusEffectFunctions(system)
        val applyFn = functions.find { it.name == "apply_effect_sleep" }
        assertTrue(applyFn != null, "Expected 'apply_effect_sleep' function")

        // Should contain a CVarDecl for effective_chance computed via stat contest
        val hasEffectiveChance =
            applyFn!!
                .body
                .filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl>()
                .any { it.name == "effective_chance" }
        assertTrue(
            hasEffectiveChance,
            "Expected 'effective_chance' CVarDecl for STAT_CONTEST resist (GAP-5)",
        )
    }

    @Test
    fun `per-effect immunity blocks specific effect in apply_effect codegen`() {
        val system =
            buildStatusEffectSystem(
                id = "curse",
                immuneToEffects =
                    setOf("curse"), // immune to itself (demonstrates per-effect immunity)
            )
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val functions = visitor.generateStatusEffectFunctions(system)
        val applyFn = functions.find { it.name == "apply_effect_curse" }
        assertTrue(applyFn != null, "Expected 'apply_effect_curse' function")

        // Should contain an immunity guard: if (_char_target_immune_to_curse != 0) return;
        val hasImmunityGuard =
            applyFn!!.body.filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CIf>().any {
                ifStmt ->
                val cond = ifStmt.condition
                cond is io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr &&
                    cond.left is io.github.gbkt.backend.gbdk.codegen.ast.CVar &&
                    (cond.left as io.github.gbkt.backend.gbdk.codegen.ast.CVar)
                        .name
                        .contains("immune_to_curse")
            }
        assertTrue(
            hasImmunityGuard,
            "Expected per-effect immunity guard in apply_effect_curse (GAP-6)",
        )
    }

    @Test
    fun `INTENSITY per-stack scaling multiplies damage by stack count in tick_effect codegen`() {
        val system =
            buildStatusEffectSystem(
                id = "bleed",
                damagePerTurn = 5,
                stackMode = StackMode.INTENSITY,
                maxStacks = 5,
                perStackScaling = true,
            )
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val functions = visitor.generateStatusEffectFunctions(system)
        val tickFn = functions.find { it.name == "tick_effect_bleed" }
        assertTrue(tickFn != null, "Expected 'tick_effect_bleed' function")

        // Should contain a CVarDecl for dot_damage = damagePerTurn * stacks
        val hasDotDamageDecl =
            tickFn!!.body.filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl>().any {
                it.name == "dot_damage"
            }
        assertTrue(
            hasDotDamageDecl,
            "Expected 'dot_damage' CVarDecl for INTENSITY per-stack scaling (GAP-7)",
        )
    }

    @Test
    fun `INTENSITY stacking hooks emit onStackApplied callback in apply function`() {
        val system =
            buildStatusEffectSystem(
                id = "rage",
                stackMode = StackMode.INTENSITY,
                maxStacks = 3,
                onStackAppliedOps = listOf(NavigateTo("rage_stacked")),
            )
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val functions = visitor.generateStatusEffectFunctions(system)
        val applyFn = functions.find { it.name == "apply_effect_rage" }
        assertTrue(applyFn != null, "Expected 'apply_effect_rage' function")

        // Should contain a CCall to on_stack_applied_rage
        fun findCallInStatements(stmts: List<CStatement>): Boolean {
            for (stmt in stmts) {
                when (stmt) {
                    is io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement -> {
                        val expr = stmt.expr
                        if (
                            expr is io.github.gbkt.backend.gbdk.codegen.ast.CCall &&
                                expr.function == "on_stack_applied_rage"
                        )
                            return true
                    }
                    is io.github.gbkt.backend.gbdk.codegen.ast.CIf -> {
                        if (
                            findCallInStatements(stmt.thenBody) ||
                                findCallInStatements(stmt.elseBody)
                        )
                            return true
                    }
                    else -> {}
                }
            }
            return false
        }
        assertTrue(
            findCallInStatements(applyFn!!.body),
            "Expected 'on_stack_applied_rage' callback in apply function (INTENSITY stacking hooks)",
        )
    }

    @Test
    fun `remove_effect_id clears active duration stacks`() {
        val system = buildStatusEffectSystem(id = "silence")
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val functions = visitor.generateStatusEffectFunctions(system)
        val removeFn = functions.find { it.name == "remove_effect_silence" }
        assertTrue(removeFn != null, "Expected 'remove_effect_silence' function (GAP-8)")

        val assignedVars =
            removeFn!!
                .body
                .filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement>()
                .mapNotNull { stmt ->
                    val expr = stmt.expr as? io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
                    if (expr?.op == "=") {
                        (expr.left as? io.github.gbkt.backend.gbdk.codegen.ast.CVar)?.name
                    } else null
                }

        assertTrue(
            assignedVars.contains("_effect_silence_active"),
            "Expected _effect_silence_active reset in remove_effect",
        )
        assertTrue(
            assignedVars.contains("_effect_silence_duration"),
            "Expected _effect_silence_duration reset in remove_effect",
        )
        assertTrue(
            assignedVars.contains("_effect_silence_stacks"),
            "Expected _effect_silence_stacks reset in remove_effect",
        )
    }

    @Test
    fun `dispel_buffs iterates all BUFF effects and removes each`() {
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val dispelFn = visitor.generateDispelBuffsFunction(listOf("haste", "shield"))

        assertTrue(dispelFn.name == "dispel_buffs", "Expected 'dispel_buffs' function")
        assertTrue(dispelFn.params.size == 1, "Expected 1 param: target_idx")

        // Should contain if (_effect_haste_active != 0) remove_effect_haste() and same for shield
        val guardedRemovals =
            dispelFn.body.filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CIf>()
        assertTrue(
            guardedRemovals.size == 2,
            "Expected 2 guarded removal blocks in dispel_buffs (GAP-8)",
        )

        val hasHaste =
            guardedRemovals.any { ifStmt ->
                ifStmt.thenBody
                    .filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement>()
                    .any { stmt ->
                        val call = stmt.expr as? io.github.gbkt.backend.gbdk.codegen.ast.CCall
                        call?.function == "remove_effect_haste"
                    }
            }
        val hasShield =
            guardedRemovals.any { ifStmt ->
                ifStmt.thenBody
                    .filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement>()
                    .any { stmt ->
                        val call = stmt.expr as? io.github.gbkt.backend.gbdk.codegen.ast.CCall
                        call?.function == "remove_effect_shield"
                    }
            }
        assertTrue(hasHaste, "Expected 'remove_effect_haste' call in dispel_buffs (GAP-8)")
        assertTrue(hasShield, "Expected 'remove_effect_shield' call in dispel_buffs (GAP-8)")
    }

    // =========================================================================
    // Tests from Plan 06.5-03: Monster AI codegen (SC-4)
    // =========================================================================

    @Test
    fun `monster AI generates flat if-else tree from behavior tree`() {
        val tree =
            SelectorNode(listOf(ActionNode(UseAbility("fireball")), ActionNode(BasicAttack())))
        val system = buildMonsterSystem(id = "wizard", behaviorTree = tree)
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val functions = visitor.generateMonsterAIFunctions(system)

        assertTrue(functions.isNotEmpty(), "Expected at least one monster AI function")
        val aiFunc = functions.find { it.name == "update_ai_wizard" }
        assertTrue(aiFunc != null, "Expected 'update_ai_wizard' function")

        // Should contain CCall to use_ability_fireball and monster_basic_attack
        fun findCallInStatements(stmts: List<CStatement>, funcName: String): Boolean {
            for (stmt in stmts) {
                when (stmt) {
                    is io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement -> {
                        val call = stmt.expr as? io.github.gbkt.backend.gbdk.codegen.ast.CCall
                        if (call?.function == funcName) return true
                    }
                    is io.github.gbkt.backend.gbdk.codegen.ast.CIf -> {
                        if (findCallInStatements(stmt.thenBody, funcName)) return true
                        if (findCallInStatements(stmt.elseBody, funcName)) return true
                    }
                    else -> {}
                }
            }
            return false
        }
        assertTrue(
            findCallInStatements(aiFunc!!.body, "use_ability_fireball"),
            "Expected 'use_ability_fireball' call in AI",
        )
        assertTrue(
            findCallInStatements(aiFunc.body, "monster_basic_attack"),
            "Expected 'monster_basic_attack' call in AI",
        )
    }

    @Test
    fun `boss phase threshold generates HP percentage check`() {
        val tree = PhaseThresholdNode(hpPercent = 25, tree = ActionNode(BasicAttack()))
        val system = buildMonsterSystem(id = "boss_dragon", behaviorTree = tree)
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val functions = visitor.generateMonsterAIFunctions(system)
        val aiFunc = functions.find { it.name == "update_ai_boss_dragon" }
        assertTrue(aiFunc != null, "Expected 'update_ai_boss_dragon' function")

        // Should contain CIf with _mon_boss_dragon_hp_pct < 25
        val phaseCheck =
            aiFunc!!.body.filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CIf>().any {
                ifStmt ->
                val cond = ifStmt.condition
                cond is io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr &&
                    cond.left is io.github.gbkt.backend.gbdk.codegen.ast.CVar &&
                    (cond.left as io.github.gbkt.backend.gbdk.codegen.ast.CVar)
                        .name
                        .contains("hp_pct") &&
                    cond.right is io.github.gbkt.backend.gbdk.codegen.ast.CLiteral &&
                    (cond.right as io.github.gbkt.backend.gbdk.codegen.ast.CLiteral).value == 25
            }
        assertTrue(
            phaseCheck,
            "Expected HP phase threshold check (_mon_boss_dragon_hp_pct < 25) in AI function",
        )
    }

    @Test
    fun `cooldown node generates decrement and gate check`() {
        val tree =
            CooldownNode(
                abilityId = "meteor",
                cooldownTurns = 3,
                child = ActionNode(UseAbility("meteor")),
            )
        val system =
            buildMonsterSystem(
                id = "archmage",
                behaviorTree = tree,
                abilityCooldowns = mapOf("meteor" to 3),
            )
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val functions = visitor.generateMonsterAIFunctions(system)
        val aiFunc = functions.find { it.name == "update_ai_archmage" }
        assertTrue(aiFunc != null, "Expected 'update_ai_archmage' function")

        // Should contain cooldown decrement and gate check
        val bodyText = aiFunc!!.body.toString()
        assertTrue(
            bodyText.contains("_mon_archmage_cd_meteor"),
            "Expected cooldown variable '_mon_archmage_cd_meteor' in AI body",
        )
    }

    @Test
    fun `global repeat prevention emits _last_action guard and update`() {
        val tree = SelectorNode(listOf(ActionNode(BasicAttack())))
        val system =
            buildMonsterSystem(
                id = "trickster",
                behaviorTree = tree,
                allowGlobalRepeatPrevention = true,
            )
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val functions = visitor.generateMonsterAIFunctions(system)
        val aiFunc = functions.find { it.name == "update_ai_trickster" }
        assertTrue(aiFunc != null, "Expected 'update_ai_trickster' function")

        val bodyText = aiFunc!!.body.toString()
        assertTrue(
            bodyText.contains("_mon_trickster_last_action"),
            "Expected '_mon_trickster_last_action' global reference in AI body",
        )
        assertTrue(
            bodyText.contains("_mon_trickster_action_taken"),
            "Expected '_mon_trickster_action_taken' local in AI body",
        )
    }

    @Test
    fun `difficulty tier EASY forces random targeting in AI codegen`() {
        val tree = SelectorNode(listOf(ActionNode(BasicAttack())))
        val system =
            buildMonsterSystem(
                id = "weakling",
                behaviorTree = tree,
                difficulty = DifficultyTier.EASY,
            )
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val functions = visitor.generateMonsterAIFunctions(system)
        val aiFunc = functions.find { it.name == "update_ai_weakling" }
        assertTrue(aiFunc != null, "Expected 'update_ai_weakling' function")

        // Should contain difficulty check with EASY=0 branch that sets random target
        val difficultyCheck =
            aiFunc!!.body.filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CIf>().any {
                ifStmt ->
                val cond = ifStmt.condition
                cond is io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr &&
                    cond.left is io.github.gbkt.backend.gbdk.codegen.ast.CVar &&
                    (cond.left as io.github.gbkt.backend.gbdk.codegen.ast.CVar).name ==
                        "_combat_difficulty" &&
                    cond.right is io.github.gbkt.backend.gbdk.codegen.ast.CLiteral &&
                    (cond.right as io.github.gbkt.backend.gbdk.codegen.ast.CLiteral).value == 0
            }
        assertTrue(
            difficultyCheck,
            "Expected difficulty EASY check (_combat_difficulty == 0) for random targeting (GAP-3)",
        )
    }

    @Test
    fun `difficulty tier HARD forces lowest HP targeting in AI codegen`() {
        val tree = SelectorNode(listOf(ActionNode(BasicAttack())))
        val system =
            buildMonsterSystem(
                id = "tactician",
                behaviorTree = tree,
                difficulty = DifficultyTier.HARD,
            )
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val functions = visitor.generateMonsterAIFunctions(system)
        val aiFunc = functions.find { it.name == "update_ai_tactician" }
        assertTrue(aiFunc != null, "Expected 'update_ai_tactician' function")

        // Should contain find_lowest_hp_target call in the HARD branch (difficulty == 2)
        val bodyText = aiFunc!!.body.toString()
        assertTrue(
            bodyText.contains("find_lowest_hp_target"),
            "Expected 'find_lowest_hp_target' call for HARD difficulty targeting (GAP-3)",
        )
        assertTrue(
            bodyText.contains("_combat_difficulty"),
            "Expected '_combat_difficulty' global check in AI body (GAP-3)",
        )
    }

    // =========================================================================
    // Tests from Plan 06.5-03: Zero CRawCode in all new codegen (SC-13)
    // =========================================================================

    @Test
    fun `zero CRawCode in ability status effect and AI codegen output`() {
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        val abilitySystem =
            buildAbilitySystem(id = "heal", spCost = 5, targeting = TargetingMode.SINGLE_ALLY)
        val abilityFunctions = visitor.generateAbilityFunctions(abilitySystem)
        for (fn in abilityFunctions) {
            val rawCodes = collectRawCode(fn.body)
            assertTrue(
                rawCodes.isEmpty(),
                "Expected zero CRawCode in ability function '${fn.name}', got: ${rawCodes.map { it.code }}",
            )
        }

        val effectSystem = buildStatusEffectSystem(id = "burn", damagePerTurn = 3)
        val effectFunctions = visitor.generateStatusEffectFunctions(effectSystem)
        for (fn in effectFunctions) {
            val rawCodes = collectRawCode(fn.body)
            assertTrue(
                rawCodes.isEmpty(),
                "Expected zero CRawCode in status effect function '${fn.name}', got: ${rawCodes.map { it.code }}",
            )
        }

        val monsterSystem =
            buildMonsterSystem(
                id = "slime",
                behaviorTree = SelectorNode(listOf(ActionNode(BasicAttack()))),
            )
        val monsterFunctions = visitor.generateMonsterAIFunctions(monsterSystem)
        for (fn in monsterFunctions) {
            val rawCodes = collectRawCode(fn.body)
            assertTrue(
                rawCodes.isEmpty(),
                "Expected zero CRawCode in monster AI function '${fn.name}', got: ${rawCodes.map { it.code }}",
            )
        }
    }

    // =========================================================================
    // Tests from Plan 06.5-04: Equipment and class codegen (SC-10, SC-13)
    // =========================================================================

    // Helper: build an equipment GenericSystem with standard slots
    private fun buildEquipmentSystem(
        id: String = "equipment_system",
        config: EquipmentConfig = EquipmentConfig(),
    ): GenericSystem =
        GenericSystem(id = id, config = mapOf("type" to "rpg_equipment_system", "config" to config))

    // Helper: build a class GenericSystem
    private fun buildClassSystem(id: String = "warrior", def: ClassDef): GenericSystem =
        GenericSystem(id = id, config = mapOf("type" to "rpg_class", "def" to def))

    @Test
    fun `equipment codegen generates equip and unequip functions`() {
        val system = buildEquipmentSystem(config = EquipmentConfig())
        val visitor = RpgVisitor(buildMinimalGameIR())
        val functions = visitor.generateEquipmentFunctions(system)

        val equip = functions.find { it.name.startsWith("equip_item_") }
        assertFalse(equip == null, "Expected equip_item_<slot> function")
        val unequip = functions.find { it.name.startsWith("unequip_") }
        assertFalse(unequip == null, "Expected unequip_<slot> function")
    }

    @Test
    fun `set bonus codegen generates piece count tracker and bonus function`() {
        val config =
            EquipmentConfig(
                sets =
                    listOf(
                        EquipSetDef(
                            id = "hero_set",
                            name = "Hero Set",
                            tiers =
                                listOf(
                                    SetBonusTier(2, listOf(StatModifier("atk", flat = 5))),
                                    SetBonusTier(4, listOf(StatModifier("def", flat = 10))),
                                ),
                        )
                    )
            )
        val system = buildEquipmentSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())
        val functions = visitor.generateEquipmentFunctions(system)

        val setBonus = functions.find { it.name == "check_set_bonus_hero_set" }
        assertFalse(setBonus == null, "Expected check_set_bonus_hero_set function")

        val varDecls = visitor.generateEquipmentVarDecls(system)
        val setCounter = varDecls.find { it.name == "_set_hero_set_count" }
        assertFalse(setCounter == null, "Expected _set_hero_set_count global")
    }

    @Test
    fun `class codegen generates growth rate table`() {
        val def =
            ClassDef(
                id = "mage",
                name = "Mage",
                growthRates = StatGrowthRate(hp = 5, sp = 8, matk = 4),
            )
        val system = buildClassSystem(id = "mage", def = def)
        val visitor = RpgVisitor(buildMinimalGameIR())
        val varDecls = visitor.generateClassVarDecls(system)

        val growthArray = varDecls.find { it.name == "_class_mage_growth" }
        assertFalse(growthArray == null, "Expected _class_mage_growth const array")
        assertTrue(growthArray!!.isConst, "Growth array must be const")
    }

    @Test
    fun `class level-up check generates ability learn switch`() {
        val def =
            ClassDef(
                id = "knight",
                name = "Knight",
                growthRates = StatGrowthRate(hp = 10, def = 3),
                learnableAbilities =
                    listOf(
                        AbilityLearnEntry("shield_bash", 5),
                        AbilityLearnEntry("holy_strike", 12),
                    ),
            )
        val system = buildClassSystem(id = "knight", def = def)
        val visitor = RpgVisitor(buildMinimalGameIR())
        val functions = visitor.generateClassFunctions(system)

        val learnFn = functions.find { it.name == "check_ability_learn_knight" }
        assertFalse(learnFn == null, "Expected check_ability_learn_knight function")
        val bodyText = learnFn!!.body.toString()
        assertTrue(
            bodyText.contains("shield_bash"),
            "Expected 'shield_bash' in ability learn function",
        )
        assertTrue(
            bodyText.contains("holy_strike"),
            "Expected 'holy_strike' in ability learn function",
        )
    }

    @Test
    fun `upgrade codegen generates upgrade_item function and upgrade_level globals when enableUpgrades=true`() {
        val config = EquipmentConfig(enableUpgrades = true, maxUpgradeLevel = 5)
        val system = buildEquipmentSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateEquipmentFunctions(system)
        val upgradeFn = functions.find { it.name.startsWith("upgrade_item_") }
        assertFalse(
            upgradeFn == null,
            "Expected upgrade_item_<slot> function when enableUpgrades=true",
        )

        val varDecls = visitor.generateEquipmentVarDecls(system)
        val upgradeLevel = varDecls.find { it.name.contains("_upgrade_level") }
        assertFalse(
            upgradeLevel == null,
            "Expected _equip_<slot>_upgrade_level global when enableUpgrades=true",
        )
    }

    @Test
    fun `upgrade codegen recalculates stats with upgrade bonus in equip function`() {
        val config = EquipmentConfig(enableUpgrades = true, maxUpgradeLevel = 3)
        val system = buildEquipmentSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())
        val functions = visitor.generateEquipmentFunctions(system)

        val equipFn = functions.find { it.name.startsWith("equip_item_") }
        assertFalse(equipFn == null, "Expected equip_item_<slot> function")
        val bodyText = equipFn!!.body.toString()
        assertTrue(
            bodyText.contains("upgrade"),
            "Expected upgrade bonus calculation comment in equip function body",
        )
    }

    @Test
    fun `enchant codegen generates enchant_item function and enchant globals when enableEnchanting=true`() {
        val config = EquipmentConfig(enableEnchanting = true)
        val system = buildEquipmentSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateEquipmentFunctions(system)
        val enchantFn = functions.find { it.name.startsWith("enchant_item_") }
        assertFalse(
            enchantFn == null,
            "Expected enchant_item_<slot> function when enableEnchanting=true",
        )

        val varDecls = visitor.generateEquipmentVarDecls(system)
        val enchantGlobal = varDecls.find { it.name.contains("_enchant") }
        assertFalse(
            enchantGlobal == null,
            "Expected _equip_<slot>_enchant global when enableEnchanting=true",
        )
    }

    @Test
    fun `enchant initial aspect sets enchant in equip function`() {
        val config = EquipmentConfig(enableEnchanting = true)
        val system = buildEquipmentSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())
        val functions = visitor.generateEquipmentFunctions(system)

        val equipFn = functions.find { it.name.startsWith("equip_item_") }
        assertFalse(equipFn == null, "Expected equip_item_<slot> function")
        val bodyText = equipFn!!.body.toString()
        assertTrue(
            bodyText.contains("enchant"),
            "Expected enchant aspect initialization comment in equip function body",
        )
    }

    // =========================================================================
    // Tests from Plan 06.5-07: Economy/party/save/ability learning codegen (SC-5, SC-13)
    // =========================================================================

    // Helper: build merchant GenericSystem
    private fun buildMerchantSystem(
        id: String = "blacksmith",
        stock: List<io.github.gbkt.rpg.domain.ShopItem> =
            listOf(
                io.github.gbkt.rpg.domain.ShopItem("iron_sword", 200),
                io.github.gbkt.rpg.domain.ShopItem("potion", 50, sellPriceOverride = 30),
            ),
        sellRatio: Int = 50,
        flagGatedStock: Map<String, List<io.github.gbkt.rpg.domain.ShopItem>> = emptyMap(),
    ): GenericSystem {
        val def =
            io.github.gbkt.rpg.domain.MerchantDef(
                id = id,
                name = "Blacksmith",
                stock = stock,
                sellRatio = sellRatio,
                flagGatedStock = flagGatedStock,
            )
        return GenericSystem(id = id, config = mapOf("type" to "rpg_merchant", "def" to def))
    }

    // Helper: build party GenericSystem
    private fun buildPartySystem(
        config: io.github.gbkt.rpg.domain.PartyConfig =
            io.github.gbkt.rpg.domain.PartyConfig(
                maxActiveSize = 4,
                enableReserve = true,
                reserveSize = 4,
                initialMembers =
                    listOf(
                        io.github.gbkt.rpg.domain.PartyMemberConfig("hero"),
                        io.github.gbkt.rpg.domain.PartyMemberConfig("npc_ally", isGuest = true),
                    ),
            )
    ): GenericSystem =
        GenericSystem(
            id = "party_system",
            config = mapOf("type" to "rpg_party_system", "config" to config),
        )

    // Helper: build save GenericSystem
    private fun buildRpgSaveSystem(
        config: io.github.gbkt.rpg.domain.RpgSaveConfig =
            io.github.gbkt.rpg.domain.RpgSaveConfig(
                slotCount = 3,
                enableNewGamePlus = true,
                ngPlusCarryOver = setOf("inventory", "gold"),
            )
    ): GenericSystem =
        GenericSystem(id = "rpg_save", config = mapOf("type" to "rpg_save", "config" to config))

    // Helper: build ability learning GenericSystem
    private fun buildAbilityLearningSystem(
        config: io.github.gbkt.rpg.domain.AbilityLearningConfig =
            io.github.gbkt.rpg.domain.AbilityLearningConfig(
                methods =
                    listOf(
                        io.github.gbkt.rpg.domain.AutoLearn("fireball", atLevel = 5),
                        io.github.gbkt.rpg.domain.AutoLearn("blizzard", atLevel = 10),
                    )
            )
    ): GenericSystem =
        GenericSystem(
            id = "ability_learning",
            config = mapOf("type" to "rpg_ability_learning", "config" to config),
        )

    // Helper: build loot table GenericSystem
    private fun buildLootTableSystem(
        id: String = "goblin_drops",
        entries: List<io.github.gbkt.rpg.domain.LootEntry> =
            listOf(
                io.github.gbkt.rpg.domain.LootEntry(
                    "gold_coin",
                    60,
                    io.github.gbkt.rpg.domain.Rarity.COMMON,
                ),
                io.github.gbkt.rpg.domain.LootEntry(
                    "magic_gem",
                    10,
                    io.github.gbkt.rpg.domain.Rarity.RARE,
                ),
            ),
        guaranteedDrop: String? = null,
    ): GenericSystem {
        val def =
            io.github.gbkt.rpg.domain.LootTableDef(
                id = id,
                entries = entries,
                guaranteedDrop = guaranteedDrop,
            )
        return GenericSystem(id = id, config = mapOf("type" to "rpg_loot_table", "def" to def))
    }

    @Test
    fun `merchant codegen generates buy and sell functions`() {
        val system = buildMerchantSystem(id = "shop")
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateMerchantFunctions(system)

        val buyFn = functions.find { it.name == "buy_from_shop" }
        assertFalse(buyFn == null, "Expected 'buy_from_shop' function")

        val sellFn = functions.find { it.name == "sell_to_shop" }
        assertFalse(sellFn == null, "Expected 'sell_to_shop' function")

        // Sell function should contain GAP-10 override logic
        val sellBody = sellFn!!.body.toString()
        assertTrue(
            sellBody.contains("override") || sellBody.contains("sell_override"),
            "Expected sell override logic in sell function (GAP-10)",
        )
    }

    @Test
    fun `per-item sell price override takes precedence over global ratio in sell_to codegen`() {
        val stock =
            listOf(io.github.gbkt.rpg.domain.ShopItem("rare_sword", 500, sellPriceOverride = 350))
        val system = buildMerchantSystem(id = "rare_shop", stock = stock, sellRatio = 50)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateMerchantFunctions(system)
        val sellFn = functions.find { it.name == "sell_to_rare_shop" }
        assertFalse(sellFn == null, "Expected 'sell_to_rare_shop' function")

        // Should check override_val != 255 (0xFF = no override)
        val hasOverrideCheck =
            sellFn!!.body.filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CIf>().any {
                ifStmt ->
                val cond = ifStmt.condition
                cond is io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr &&
                    cond.right is io.github.gbkt.backend.gbdk.codegen.ast.CLiteral &&
                    (cond.right as io.github.gbkt.backend.gbdk.codegen.ast.CLiteral).value == 255
            }
        assertTrue(
            hasOverrideCheck,
            "Expected 0xFF override sentinel check in sell function (GAP-10)",
        )
    }

    @Test
    fun `party codegen generates active and reserve arrays`() {
        val system = buildPartySystem()
        val visitor = RpgVisitor(buildMinimalGameIR())

        val varDecls = visitor.generatePartyVarDecls(system)

        val activeArray = varDecls.find { it.name == "_party_active" }
        assertFalse(activeArray == null, "Expected '_party_active' array")

        val activeCount = varDecls.find { it.name == "_party_active_count" }
        assertFalse(activeCount == null, "Expected '_party_active_count' variable")

        val reserveArray = varDecls.find { it.name == "_party_reserve" }
        assertFalse(reserveArray == null, "Expected '_party_reserve' array when reserve enabled")

        val guestArray = varDecls.find { it.name == "_party_is_guest" }
        assertFalse(guestArray == null, "Expected '_party_is_guest' array (GAP-4)")
    }

    @Test
    fun `guest member is AI-controlled in battle -- PLAYER_TURN skips guests`() {
        val visitor = RpgVisitor(buildMinimalGameIR())
        val dispatchFn = visitor.generateRpgPlayerTurnDispatch("combat")

        // Function should have a guest check at the start that returns early
        val guestCheckIf =
            dispatchFn.body
                .filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CIf>()
                .firstOrNull()
        assertFalse(
            guestCheckIf == null,
            "Expected a guest check CIf at start of PLAYER_TURN dispatch (GAP-4)",
        )

        // The if body should contain a return (skip guest turn)
        val hasReturn =
            guestCheckIf!!.thenBody.any { it is io.github.gbkt.backend.gbdk.codegen.ast.CReturn }
        assertTrue(hasReturn, "Expected early return for guest member in PLAYER_TURN (GAP-4 SC-5)")
    }

    @Test
    fun `guest member equip lock guard prevents equipment changes`() {
        val config =
            io.github.gbkt.rpg.domain.PartyConfig(
                initialMembers =
                    listOf(io.github.gbkt.rpg.domain.PartyMemberConfig("ally", isGuest = true))
            )
        val system = buildPartySystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generatePartyFunctions(system)
        val guestFn = functions.find { it.name == "is_guest" }
        assertFalse(guestFn == null, "Expected 'is_guest' function (GAP-4)")

        // is_guest should return the _party_is_guest[char_idx] value
        val returnStmt =
            guestFn!!
                .body
                .filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CReturn>()
                .firstOrNull()
        assertFalse(returnStmt == null, "Expected CReturn in is_guest function")
        val returnExpr = returnStmt!!.value
        assertTrue(
            returnExpr is io.github.gbkt.backend.gbdk.codegen.ast.CArrayAccess,
            "Expected CArrayAccess in is_guest return (GAP-4)",
        )
    }

    @Test
    fun `remove_guest function emits guest removal by character ID`() {
        val config =
            io.github.gbkt.rpg.domain.PartyConfig(
                initialMembers =
                    listOf(io.github.gbkt.rpg.domain.PartyMemberConfig("ally", isGuest = true))
            )
        val system = buildPartySystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generatePartyFunctions(system)
        val removeFn = functions.find { it.name == "remove_guest" }
        assertFalse(removeFn == null, "Expected 'remove_guest' function (GAP-4)")
        assertTrue(removeFn!!.params.isNotEmpty(), "Expected param in remove_guest (char_id)")
    }

    @Test
    fun `ability learning codegen generates auto-learn switch`() {
        val system = buildAbilityLearningSystem()
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateAbilityLearningFunctions(system)

        val autoLearnFn = functions.find { it.name == "check_auto_learn" }
        assertFalse(autoLearnFn == null, "Expected 'check_auto_learn' function")

        // Should contain a CSwitch on level
        val switchStmt =
            autoLearnFn!!
                .body
                .filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CSwitch>()
                .firstOrNull()
        assertFalse(switchStmt == null, "Expected CSwitch in check_auto_learn body")
        assertEquals(
            2,
            switchStmt!!.cases.size,
            "Expected 2 auto-learn switch cases (level 5 and 10)",
        )
    }

    @Test
    fun `loot table codegen generates roll function with rarity weights`() {
        val system =
            buildLootTableSystem(
                id = "goblin",
                entries =
                    listOf(
                        io.github.gbkt.rpg.domain.LootEntry(
                            "coin",
                            60,
                            io.github.gbkt.rpg.domain.Rarity.COMMON,
                        ),
                        io.github.gbkt.rpg.domain.LootEntry(
                            "gem",
                            10,
                            io.github.gbkt.rpg.domain.Rarity.RARE,
                        ),
                    ),
            )
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateLootTableFunctions(system)

        val rollFn = functions.find { it.name == "roll_loot_goblin" }
        assertFalse(rollFn == null, "Expected 'roll_loot_goblin' function")

        // Should have chance checks for both entries
        val ifStmts = rollFn!!.body.filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CIf>()
        assertTrue(ifStmts.size >= 2, "Expected at least 2 CIf blocks (one per loot entry)")
    }

    @Test
    fun `player flee generates flee chance check and FLED state transition`() {
        val visitor = RpgVisitor(buildMinimalGameIR())
        val fleeFn = visitor.generatePlayerFleeFunction("combat", fleeChance = 50)

        assertTrue(
            fleeFn.name == "player_flee_combat",
            "Expected 'player_flee_combat' function name",
        )

        // Body text should contain rand() and 100 (from rand() % 100 chance check)
        val bodyText = fleeFn.body.toString()
        assertTrue(bodyText.contains("rand"), "Expected rand() call in player flee function (SC-5)")
        assertTrue(
            bodyText.contains("100"),
            "Expected modulo 100 for rand check in player flee function (SC-5)",
        )

        // Should call combat_request_state_combat on success
        assertTrue(
            bodyText.contains("combat_request_state_combat"),
            "Expected state transition in player_flee (SC-5)",
        )
    }

    @Test
    fun `use_item_in_battle generates item deduction and onUse effect application`() {
        val visitor = RpgVisitor(buildMinimalGameIR())
        val itemFn = visitor.generateUseItemInBattleFunction("combat")

        assertTrue(
            itemFn.name == "use_item_in_battle_combat",
            "Expected 'use_item_in_battle_combat' function name",
        )

        // Should call use_item and remove_item
        val bodyText = itemFn.body.toString()
        assertTrue(
            bodyText.contains("use_item") || bodyText.contains("remove_item"),
            "Expected item consumption in use_item_in_battle (SC-5)",
        )
        assertTrue(
            bodyText.contains("combat_request_state_combat"),
            "Expected ENEMY_TURN transition in use_item_in_battle (SC-5)",
        )
    }

    @Test
    fun `PLAYER_TURN dispatches on selected_action to attack, ability, item, or flee`() {
        val visitor = RpgVisitor(buildMinimalGameIR())
        val dispatchFn = visitor.generateRpgPlayerTurnDispatch("combat")

        // Should contain a CSwitch on _combat_combat_selected_action
        val switchStmt =
            dispatchFn.body
                .filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CSwitch>()
                .firstOrNull()
        assertFalse(switchStmt == null, "Expected CSwitch in PLAYER_TURN dispatch (SC-5)")
        assertEquals(
            4,
            switchStmt!!.cases.size,
            "Expected 4 action cases (attack/ability/item/flee)",
        )

        // Cases 0-3
        val caseValues =
            switchStmt.cases.map {
                (it.value as? io.github.gbkt.backend.gbdk.codegen.ast.CLiteral)?.value
            }
        assertTrue(caseValues.contains(0), "Expected ACTION_ATTACK (0) case")
        assertTrue(caseValues.contains(1), "Expected ACTION_ABILITY (1) case")
        assertTrue(caseValues.contains(2), "Expected ACTION_ITEM (2) case")
        assertTrue(caseValues.contains(3), "Expected ACTION_FLEE (3) case")
    }

    @Test
    fun `RPG save codegen emits both save_rpg_state and load_rpg_state symbols`() {
        val system = buildRpgSaveSystem()
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateRpgSaveFunctions(system)

        val saveFn = functions.find { it.name == "save_rpg_state" }
        assertFalse(saveFn == null, "Expected 'save_rpg_state' function")

        val loadFn = functions.find { it.name == "load_rpg_state" }
        assertFalse(loadFn == null, "Expected 'load_rpg_state' function")
    }

    @Test
    fun `save codegen emits compute_save_checksum after serialization`() {
        val system = buildRpgSaveSystem()
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateRpgSaveFunctions(system)
        val saveFn = functions.find { it.name == "save_rpg_state" }
        assertFalse(saveFn == null, "Expected 'save_rpg_state' function")

        // Should call compute_save_checksum
        val bodyText = saveFn!!.body.toString()
        assertTrue(
            bodyText.contains("compute_save_checksum"),
            "Expected 'compute_save_checksum' call in save function (GAP-11)",
        )
    }

    @Test
    fun `load codegen emits validate_save_checksum and _save_corrupt flag on mismatch`() {
        val system = buildRpgSaveSystem()
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateRpgSaveFunctions(system)
        val loadFn = functions.find { it.name == "load_rpg_state" }
        assertFalse(loadFn == null, "Expected 'load_rpg_state' function")

        // Should call validate_save_checksum
        val bodyText = loadFn!!.body.toString()
        assertTrue(
            bodyText.contains("validate_save_checksum"),
            "Expected 'validate_save_checksum' call in load function (GAP-11)",
        )

        // Should set _save_corrupt on failure
        assertTrue(
            bodyText.contains("_save_corrupt"),
            "Expected '_save_corrupt' flag in load function (GAP-11)",
        )

        // validate_save_checksum function should exist separately
        val validateFn = functions.find { it.name == "validate_save_checksum" }
        assertFalse(
            validateFn == null,
            "Expected 'validate_save_checksum' standalone function (GAP-11)",
        )
    }

    @Test
    fun `zero CRawCode in merchant party save and ability learning codegen output`() {
        val gameIR = buildMinimalGameIR()
        val visitor = RpgVisitor(gameIR)

        // Merchant
        val merchantSystem = buildMerchantSystem()
        val merchantFunctions = visitor.generateMerchantFunctions(merchantSystem)
        for (fn in merchantFunctions) {
            val rawCodes = collectRawCode(fn.body)
            assertTrue(
                rawCodes.isEmpty(),
                "Expected zero CRawCode in merchant function '${fn.name}' (SC-13): ${rawCodes.map { it.code }}",
            )
        }

        // Party
        val partySystem = buildPartySystem()
        val partyFunctions = visitor.generatePartyFunctions(partySystem)
        for (fn in partyFunctions) {
            val rawCodes = collectRawCode(fn.body)
            assertTrue(
                rawCodes.isEmpty(),
                "Expected zero CRawCode in party function '${fn.name}' (SC-13): ${rawCodes.map { it.code }}",
            )
        }

        // RPG Save
        val saveSystem = buildRpgSaveSystem()
        val saveFunctions = visitor.generateRpgSaveFunctions(saveSystem)
        for (fn in saveFunctions) {
            val rawCodes = collectRawCode(fn.body)
            assertTrue(
                rawCodes.isEmpty(),
                "Expected zero CRawCode in save function '${fn.name}' (SC-13): ${rawCodes.map { it.code }}",
            )
        }

        // Ability learning
        val learningSystem = buildAbilityLearningSystem()
        val learningFunctions = visitor.generateAbilityLearningFunctions(learningSystem)
        for (fn in learningFunctions) {
            val rawCodes = collectRawCode(fn.body)
            assertTrue(
                rawCodes.isEmpty(),
                "Expected zero CRawCode in ability learning function '${fn.name}' (SC-13): ${rawCodes.map { it.code }}",
            )
        }

        // Player flee + item use + PLAYER_TURN dispatch
        val fleeFn = visitor.generatePlayerFleeFunction("combat")
        val fleeRaw = collectRawCode(fleeFn.body)
        assertTrue(fleeRaw.isEmpty(), "Expected zero CRawCode in player_flee_combat (SC-13)")

        val itemFn = visitor.generateUseItemInBattleFunction("combat")
        val itemRaw = collectRawCode(itemFn.body)
        assertTrue(itemRaw.isEmpty(), "Expected zero CRawCode in use_item_in_battle_combat (SC-13)")

        val dispatchFn = visitor.generateRpgPlayerTurnDispatch("combat")
        val dispatchRaw = collectRawCode(dispatchFn.body)
        assertTrue(
            dispatchRaw.isEmpty(),
            "Expected zero CRawCode in update_rpg_player_turn_combat (SC-13)",
        )
    }

    // =========================================================================
    // End-to-end codegen integration test (Plan 06.5-08 SC-15, SC-16)
    // =========================================================================

    /**
     * Builds a full RPG GameIR with all system types for the codegen integration test. Constructs
     * the IR directly (without DSL) to keep this test in gbkt-backend-gbdk without a dependency on
     * gbkt-rpg.
     */
    private fun buildFullRpgGameIR(): GameIR {
        val heroStats =
            CombatStats(hp = 100, sp = 50, atk = 15, def = 10, matk = 8, mdef = 4, agl = 12)
        val goblinDef =
            MonsterDef(
                id = "goblin",
                name = "Goblin",
                stats = CombatStats(hp = 30, atk = 8, def = 5),
                behaviorTree = SelectorNode(listOf(ActionNode(BasicAttack()))),
            )

        val characterSystem =
            GenericSystem(
                id = "hero",
                config =
                    mapOf(
                        "type" to "rpg_character_system",
                        "stats" to heroStats,
                        "level" to 1,
                        "maxLevel" to 99,
                        "expCurve" to ExpCurve.STANDARD,
                        "onLevelUpOps" to emptyList<io.github.gbkt.core.ir.ScriptOp>(),
                    ),
            )

        val abilitySystem =
            GenericSystem(
                id = "fireball",
                config =
                    mapOf(
                        "type" to "rpg_ability",
                        "def" to AbilityDef(id = "fireball", name = "Fireball", spCost = 8),
                    ),
            )

        val statusSystem =
            GenericSystem(
                id = "poison",
                config =
                    mapOf(
                        "type" to "rpg_status_effect",
                        "def" to
                            StatusEffectDef(
                                id = "poison",
                                name = "Poison",
                                duration = 5,
                                damagePerTurn = 10,
                            ),
                    ),
            )

        val monsterSystem =
            GenericSystem(
                id = "goblin",
                config = mapOf("type" to "rpg_monster", "def" to goblinDef),
            )

        // simpleBattle now produces CombatEngineSystem (migrated from GenericSystem in Plan
        // 06.5-08)
        val combatSystem =
            io.github.gbkt.core.ir.CombatEngineSystem(
                id = "combat",
                combatType = io.github.gbkt.core.ir.CombatType.TURN_BASED,
                onVictoryOps = listOf(NavigateTo("gameplay")),
                onDefeatOps = listOf(NavigateTo("gameover")),
            )

        val items =
            listOf(
                ItemDef(
                    id = "potion",
                    name = "Potion",
                    categoryId = "consumable",
                    effects = listOf(HealEffect(50)),
                )
            )
        val itemCategories = listOf(ItemCategoryDef("consumable", defaultMaxStack = 10))

        return GameIR(
            name = "FullRpgTest",
            config = CartridgeConfig(),
            scenes = listOf(SceneIR(id = "gameplay"), SceneIR(id = "gameover")),
            systems =
                listOf(characterSystem, abilitySystem, statusSystem, monsterSystem, combatSystem),
            items = items,
            itemCategories = itemCategories,
            startScene = "gameplay",
        )
    }

    @Test
    fun `full RPG game generates valid C output with all RPG symbols`() {
        val ir = buildFullRpgGameIR()
        val output = pipeline.generate(ir)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Character stat globals (rpg_character_system)
        assertTrue(
            mainC.contains("_char_hero_hp"),
            "Expected '_char_hero_hp' stat global (rpg_character_system)",
        )
        assertTrue(mainC.contains("_char_hero_atk"), "Expected '_char_hero_atk' stat global")

        // Item names table
        assertTrue(mainC.contains("_item_names"), "Expected '_item_names' table (items)")
        assertTrue(mainC.contains("\"Potion\""), "Expected 'Potion' in _item_names")

        // Combat functions (CombatEngineSystem TURN_BASED via CombatVisitor)
        assertTrue(
            mainC.contains("update_combat_combat"),
            "Expected 'update_combat_combat' function (CombatEngineSystem)",
        )

        // Ability function (rpg_ability)
        assertTrue(
            mainC.contains("use_ability_fireball"),
            "Expected 'use_ability_fireball' function (rpg_ability)",
        )

        // Status effect functions (rpg_status_effect)
        assertTrue(
            mainC.contains("apply_effect_poison"),
            "Expected 'apply_effect_poison' function (rpg_status_effect)",
        )

        // Monster AI function (rpg_monster)
        assertTrue(
            mainC.contains("update_ai_goblin"),
            "Expected 'update_ai_goblin' function (rpg_monster)",
        )

        // No TODO stubs in generated output
        assertFalse(mainC.contains("TODO"), "Expected no 'TODO' stubs in generated main.c")
    }

    // =========================================================================
    // Tests from Plan 06.8-02: H-item codegen gaps (H2-H10)
    // =========================================================================

    // -------------------------------------------------------------------------
    // H2: Equipment Durability
    // -------------------------------------------------------------------------

    @Test
    fun `durability codegen generates degrade_equipment function when enableDurability=true`() {
        val config = EquipmentConfig(enableDurability = true)
        val system = buildEquipmentSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateEquipmentFunctions(system)
        val degradeFn = functions.find { it.name == "degrade_equipment" }
        assertFalse(
            degradeFn == null,
            "Expected 'degrade_equipment' function when enableDurability=true (H2)",
        )

        val varDecls = visitor.generateEquipmentVarDecls(system)
        val durabilityVars = varDecls.filter { it.name.startsWith("_equip_durability_") }
        assertTrue(
            durabilityVars.isNotEmpty(),
            "Expected _equip_durability_<slot> global variables when enableDurability=true (H2)",
        )
    }

    // -------------------------------------------------------------------------
    // H3: Elemental Affinity
    // -------------------------------------------------------------------------

    @Test
    fun `equipment equip function references aspect when enableEnchanting=true`() {
        val config = EquipmentConfig(enableEnchanting = true)
        val system = buildEquipmentSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateEquipmentFunctions(system)
        val equipFn = functions.find { it.name.startsWith("equip_item_") }
        assertFalse(equipFn == null, "Expected equip_item_<slot> function (H3)")

        // equip_item_<slot> body contains aspect comment when enchanting enabled
        val bodyText = equipFn!!.body.toString()
        assertTrue(
            bodyText.contains("aspect") || bodyText.contains("enchant"),
            "Expected aspect/enchant reference in equip function body when enchanting enabled (H3)",
        )
    }

    // -------------------------------------------------------------------------
    // H4: Ability Mastery
    // -------------------------------------------------------------------------

    @Test
    fun `mastery codegen generates gain_mastery function when enableMastery=true`() {
        val config =
            io.github.gbkt.rpg.domain.AbilityLearningConfig(
                methods = listOf(io.github.gbkt.rpg.domain.AutoLearn("fireball", atLevel = 5)),
                enableMastery = true,
                masteryLevels = 3,
            )
        val system = buildAbilityLearningSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateAbilityLearningFunctions(system)
        val masteryFn = functions.find { it.name == "gain_mastery" }
        assertFalse(
            masteryFn == null,
            "Expected 'gain_mastery' function when enableMastery=true (H4)",
        )
    }

    // -------------------------------------------------------------------------
    // H5: Evolution Chains
    // -------------------------------------------------------------------------

    @Test
    fun `mastery codegen includes evolution chain check when evolutionChains non-empty`() {
        val config =
            io.github.gbkt.rpg.domain.AbilityLearningConfig(
                methods = listOf(io.github.gbkt.rpg.domain.AutoLearn("fireball", atLevel = 5)),
                enableMastery = true,
                masteryLevels = 3,
                evolutionChains = mapOf("fireball" to "mega_fireball"),
            )
        val system = buildAbilityLearningSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateAbilityLearningFunctions(system)
        val masteryFn = functions.find { it.name == "gain_mastery" }
        assertFalse(
            masteryFn == null,
            "Expected 'gain_mastery' function when evolutionChains non-empty (H5)",
        )

        // gain_mastery body should contain evolution chain logic (check_ability_evolution call)
        val bodyText = masteryFn!!.body.toString()
        assertTrue(
            bodyText.contains("check_ability_evolution") || bodyText.contains("evolution"),
            "Expected evolution chain check in gain_mastery body when evolutionChains non-empty (H5)",
        )
    }

    // -------------------------------------------------------------------------
    // H6: Reserve EXP Sharing
    // -------------------------------------------------------------------------

    @Test
    fun `party codegen emits _party_reserve_exp_share const when enableReserve=true`() {
        val config =
            io.github.gbkt.rpg.domain.PartyConfig(
                maxActiveSize = 4,
                enableReserve = true,
                reserveSize = 4,
                reserveExpShare = 50,
            )
        val system = buildPartySystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val varDecls = visitor.generatePartyVarDecls(system)
        val expShareConst = varDecls.find { it.name == "_party_reserve_exp_share" }
        assertFalse(
            expShareConst == null,
            "Expected '_party_reserve_exp_share' const when enableReserve=true (H6)",
        )
        assertTrue(expShareConst!!.isConst, "Expected _party_reserve_exp_share to be const (H6)")
        val initializer = expShareConst.initializer
        assertTrue(
            initializer is io.github.gbkt.backend.gbdk.codegen.ast.CLiteral &&
                (initializer as io.github.gbkt.backend.gbdk.codegen.ast.CLiteral).value == 50,
            "Expected _party_reserve_exp_share initializer value = 50 (H6)",
        )
    }

    // -------------------------------------------------------------------------
    // H7: Row Formation
    // -------------------------------------------------------------------------

    @Test
    fun `row formation codegen generates set_row function and _party_row array when enableRowFormation=true`() {
        val config =
            io.github.gbkt.rpg.domain.PartyConfig(maxActiveSize = 4, enableRowFormation = true)
        val system = buildPartySystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generatePartyFunctions(system)
        val setRowFn = functions.find { it.name == "set_row" }
        assertFalse(
            setRowFn == null,
            "Expected 'set_row' function when enableRowFormation=true (H7)",
        )

        val varDecls = visitor.generatePartyVarDecls(system)
        val rowArray = varDecls.find { it.name == "_party_row" }
        assertFalse(
            rowArray == null,
            "Expected '_party_row' array when enableRowFormation=true (H7)",
        )
    }

    // -------------------------------------------------------------------------
    // H8: Crafting
    // -------------------------------------------------------------------------

    @Test
    fun `crafting codegen generates craft_item function from CraftingRecipe list`() {
        val recipes =
            listOf(
                io.github.gbkt.rpg.domain.CraftingRecipe(
                    resultItemId = "iron_sword",
                    ingredients = listOf("iron_ore" to 3, "wood" to 1),
                )
            )
        val system =
            GenericSystem(
                id = "rpg_crafting",
                config = mapOf("type" to "rpg_crafting", "recipes" to recipes),
            )
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateCraftingFunctions(system)
        val craftFn = functions.find { it.name == "craft_item" }
        assertFalse(craftFn == null, "Expected 'craft_item' function from CraftingRecipe list (H8)")

        // craft_item body should contain recipe dispatch logic for the result item
        val bodyText = craftFn!!.body.toString()
        assertTrue(
            bodyText.contains("iron_sword") || bodyText.contains("item_id_iron_sword"),
            "Expected recipe dispatch logic referencing result item ID in craft_item body (H8)",
        )
    }

    // -------------------------------------------------------------------------
    // H9: Auto-Save
    // -------------------------------------------------------------------------

    @Test
    fun `auto-save codegen generates auto_save_rpg function when autoSaveEnabled=true`() {
        val config = io.github.gbkt.rpg.domain.RpgSaveConfig(slotCount = 3, autoSaveEnabled = true)
        val system = buildRpgSaveSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateRpgSaveFunctions(system)
        val autoSaveFn = functions.find { it.name == "auto_save_rpg" }
        assertFalse(
            autoSaveFn == null,
            "Expected 'auto_save_rpg' function when autoSaveEnabled=true (H9)",
        )
    }

    // -------------------------------------------------------------------------
    // H10: New Game+
    // -------------------------------------------------------------------------

    @Test
    fun `new game plus codegen generates new_game_plus function with ngPlusCarryOver fields`() {
        val config =
            io.github.gbkt.rpg.domain.RpgSaveConfig(
                slotCount = 3,
                enableNewGamePlus = true,
                ngPlusCarryOver = setOf("inventory", "gold", "abilities"),
            )
        val system = buildRpgSaveSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateRpgSaveFunctions(system)
        val ngPlusFn = functions.find { it.name == "new_game_plus" }
        assertFalse(
            ngPlusFn == null,
            "Expected 'new_game_plus' function when enableNewGamePlus=true (H10)",
        )

        // new_game_plus body should reference carry-over fields
        val bodyText = ngPlusFn!!.body.toString()
        assertTrue(
            bodyText.contains("inventory") || bodyText.contains("carry over"),
            "Expected carry-over field references in new_game_plus body (H10)",
        )
        assertTrue(
            bodyText.contains("reset_game_state"),
            "Expected 'reset_game_state' call in new_game_plus body (H10)",
        )
    }

    // =========================================================================
    // Tests from Plan 06.8-03: Multi-currency codegen (H11)
    // =========================================================================

    @Test
    fun `currency system generates per-currency globals and add-sub functions`() {
        val def = CurrencyDef(id = "gold", max = 9999)
        val system =
            GenericSystem(id = "gold", config = mapOf("type" to "rpg_currency", "def" to def))
        val gameIR =
            GameIR(
                name = "CurrencyCodegenTest",
                config = CartridgeConfig(),
                scenes = listOf(SceneIR(id = "main")),
                systems = listOf(system),
                startScene = "main",
            )

        val mainC = generateMainC(gameIR)

        // Variable declarations
        assertTrue(
            mainC.contains("_currency_gold"),
            "Expected _currency_gold global in generated C",
        )
        assertTrue(
            mainC.contains("_currency_gold_max"),
            "Expected _currency_gold_max const in generated C",
        )

        // Helper functions
        assertTrue(mainC.contains("add_gold"), "Expected add_gold function in generated C")
        assertTrue(mainC.contains("sub_gold"), "Expected sub_gold function in generated C")
    }

    @Test
    fun `currency exchange function generated when exchange rate defined`() {
        val goldDef = CurrencyDef(id = "gold", max = 9999)
        val gemsDef =
            CurrencyDef(
                id = "gems",
                max = 99,
                exchanges = listOf(CurrencyExchange(toId = "gold", rate = 100)),
            )
        val goldSystem =
            GenericSystem(id = "gold", config = mapOf("type" to "rpg_currency", "def" to goldDef))
        val gemsSystem =
            GenericSystem(id = "gems", config = mapOf("type" to "rpg_currency", "def" to gemsDef))
        val gameIR =
            GameIR(
                name = "ExchangeCodegenTest",
                config = CartridgeConfig(),
                scenes = listOf(SceneIR(id = "main")),
                systems = listOf(goldSystem, gemsSystem),
                startScene = "main",
            )

        val mainC = generateMainC(gameIR)

        // Exchange function must be generated for gems -> gold
        assertTrue(
            mainC.contains("exchange_gems_gold"),
            "Expected exchange_gems_gold function in generated C",
        )
        // Both currencies must have their own globals
        assertTrue(
            mainC.contains("_currency_gold"),
            "Expected _currency_gold global in generated C",
        )
        assertTrue(
            mainC.contains("_currency_gems"),
            "Expected _currency_gems global in generated C",
        )
    }

    @Test
    fun `currency add function emits PO localization key comment`() {
        val def = CurrencyDef(id = "gold", max = 9999)
        val system =
            GenericSystem(id = "gold", config = mapOf("type" to "rpg_currency", "def" to def))
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateCurrencyFunctions(system)
        val addFn = functions.find { it.name == "add_gold" }
        assertNotNull(addFn, "Expected add_gold function to be generated")

        // The function body must contain the PO localization key as a CComment node
        val hasLocComment =
            addFn.body.filterIsInstance<io.github.gbkt.backend.gbdk.codegen.ast.CComment>().any {
                it.text.contains("str_currency_gold")
            }
        assertTrue(
            hasLocComment,
            "Expected 'str_currency_gold' PO localization key comment in add_gold body",
        )
    }
}

// =============================================================================
// ACTION RPG CODEGEN TESTS (Plan 06.8-04 Task 2)
// Tests covering:
//   - ARPG realtime: arpg_update, arpg_attack, arpg_dodge_roll functions generated
//   - ARPG ATB: atb_check_ready function generated when ATB config present
//   - Stamina: codegen references gauge infrastructure vars (_gauge_stamina)
//   - Behavior AI: ai_update function dispatches on preset types
// =============================================================================

private fun buildArpgSystem(
    id: String = "combat",
    config: io.github.gbkt.rpg.domain.ActionRpgConfig,
): GenericSystem =
    GenericSystem(id = id, config = mapOf("type" to "arpg_combat", "config" to config))

class ActionRpgCodegenTest {

    // =========================================================================
    // Test 1: Realtime ARPG generates arpg_update, arpg_attack functions
    // =========================================================================

    @Test
    fun `arpg realtime generates arpg_update and arpg_attack functions`() {
        val config =
            io.github.gbkt.rpg.domain.ActionRpgConfig(
                model = io.github.gbkt.rpg.domain.CombatModel.REALTIME_COOLDOWN
            )
        val system = buildArpgSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateActionRpgFunctions(system)
        val names = functions.map { it.name }

        assertTrue(
            names.contains("arpg_update"),
            "Expected 'arpg_update' function for realtime ARPG",
        )
        assertTrue(
            names.contains("arpg_attack"),
            "Expected 'arpg_attack' function for realtime ARPG",
        )
    }

    // =========================================================================
    // Test 2: Dodge roll config generates arpg_dodge_roll function
    // =========================================================================

    @Test
    fun `arpg with dodge roll config generates arpg_dodge_roll function`() {
        val config =
            io.github.gbkt.rpg.domain.ActionRpgConfig(
                model = io.github.gbkt.rpg.domain.CombatModel.REALTIME_COOLDOWN,
                dodgeRoll =
                    io.github.gbkt.rpg.domain.DodgeRollConfig(
                        iFrameDuration = 10,
                        cooldownFrames = 20,
                    ),
            )
        val system = buildArpgSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateActionRpgFunctions(system)
        val dodgeRollFn = functions.find { it.name == "arpg_dodge_roll" }

        assertNotNull(dodgeRollFn, "Expected 'arpg_dodge_roll' function when dodge roll configured")
        // Verify i-frame duration constant appears in body
        val bodyText = dodgeRollFn!!.body.toString()
        assertTrue(bodyText.contains("10"), "Expected iFrameDuration=10 in arpg_dodge_roll body")
        assertTrue(bodyText.contains("20"), "Expected cooldownFrames=20 in arpg_dodge_roll body")
    }

    // =========================================================================
    // Test 3: No dodge roll config => no arpg_dodge_roll function
    // =========================================================================

    @Test
    fun `arpg without dodge roll config does not generate arpg_dodge_roll function`() {
        val config =
            io.github.gbkt.rpg.domain.ActionRpgConfig(
                model = io.github.gbkt.rpg.domain.CombatModel.REALTIME_COOLDOWN,
                dodgeRoll = null,
            )
        val system = buildArpgSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateActionRpgFunctions(system)
        val names = functions.map { it.name }

        assertFalse(
            names.contains("arpg_dodge_roll"),
            "Expected no 'arpg_dodge_roll' when dodge roll not configured",
        )
    }

    // =========================================================================
    // Test 4: ATB config generates atb_check_ready function (HYBRID_ATB only)
    // =========================================================================

    @Test
    fun `arpg ATB generates atb_check_ready function when model is HYBRID_ATB`() {
        val config =
            io.github.gbkt.rpg.domain.ActionRpgConfig(
                model = io.github.gbkt.rpg.domain.CombatModel.HYBRID_ATB,
                atb = io.github.gbkt.rpg.domain.AtbConfig(maxGauge = 200, baseSpeed = 2),
            )
        val system = buildArpgSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateActionRpgFunctions(system)
        val atbFn = functions.find { it.name == "atb_check_ready" }

        assertNotNull(atbFn, "Expected 'atb_check_ready' function when ATB config present")
        // Check maxGauge appears in body
        val bodyText = atbFn!!.body.toString()
        assertTrue(
            bodyText.contains("200"),
            "Expected maxGauge=200 referenced in atb_check_ready body",
        )
    }

    // =========================================================================
    // Test 5: Stamina config — codegen references _gauge_stamina (exploration gauge infra)
    // =========================================================================

    @Test
    fun `arpg with stamina config references _gauge_stamina in generated functions`() {
        val config =
            io.github.gbkt.rpg.domain.ActionRpgConfig(
                model = io.github.gbkt.rpg.domain.CombatModel.REALTIME_COOLDOWN,
                staminaGauge =
                    io.github.gbkt.rpg.domain.StaminaGaugeConfig(
                        maxStamina = 100,
                        regenRate = 1,
                        attackCost = 20,
                        dodgeCost = 30,
                    ),
            )
        val system = buildArpgSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateActionRpgFunctions(system)

        // arpg_update should regen stamina using _gauge_stamina
        val updateFn = functions.find { it.name == "arpg_update" }
        assertNotNull(updateFn)
        val updateBody = updateFn!!.body.toString()
        assertTrue(
            updateBody.contains("_gauge_stamina"),
            "Expected '_gauge_stamina' reference in arpg_update (bridges to exploration gauge infra)",
        )

        // arpg_attack should deduct stamina from _gauge_stamina
        val attackFn = functions.find { it.name == "arpg_attack" }
        assertNotNull(attackFn)
        val attackBody = attackFn!!.body.toString()
        assertTrue(
            attackBody.contains("_gauge_stamina"),
            "Expected '_gauge_stamina' reference in arpg_attack for deduction",
        )
        assertTrue(attackBody.contains("20"), "Expected attackCost=20 in arpg_attack body")
    }

    // =========================================================================
    // Test 6: Behavior AI generates ai_update dispatching on preset types
    // =========================================================================

    @Test
    fun `arpg with behavior presets generates ai_update function`() {
        val config =
            io.github.gbkt.rpg.domain.ActionRpgConfig(
                model = io.github.gbkt.rpg.domain.CombatModel.REALTIME_COOLDOWN,
                behaviorPresets =
                    listOf(
                        io.github.gbkt.rpg.domain.BehaviorPreset(
                            type = io.github.gbkt.rpg.domain.BehaviorPresetType.CHASE,
                            range = 5,
                        ),
                        io.github.gbkt.rpg.domain.BehaviorPreset(
                            type = io.github.gbkt.rpg.domain.BehaviorPresetType.ATTACK_WHEN_CLOSE,
                            range = 1,
                        ),
                        io.github.gbkt.rpg.domain.BehaviorPreset(
                            type = io.github.gbkt.rpg.domain.BehaviorPresetType.FLEE,
                            threshold = 25,
                        ),
                    ),
            )
        val system = buildArpgSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateActionRpgFunctions(system)
        val aiFn = functions.find { it.name == "ai_update" }

        assertNotNull(aiFn, "Expected 'ai_update' function when behavior presets configured")
        val bodyText = aiFn!!.body.toString()

        // CHASE: calls ai_chase with range
        assertTrue(
            bodyText.contains("ai_chase"),
            "Expected 'ai_chase' call in ai_update body for CHASE preset",
        )
        assertTrue(bodyText.contains("5"), "Expected range=5 in ai_update body for CHASE")

        // ATTACK_WHEN_CLOSE: calls ai_attack
        assertTrue(
            bodyText.contains("ai_attack"),
            "Expected 'ai_attack' call in ai_update body for ATTACK_WHEN_CLOSE preset",
        )

        // FLEE: calls ai_flee with threshold check
        assertTrue(
            bodyText.contains("ai_flee"),
            "Expected 'ai_flee' call in ai_update body for FLEE preset",
        )
        assertTrue(bodyText.contains("25"), "Expected threshold=25 in ai_update body for FLEE")
    }

    // =========================================================================
    // Test 7: Var decls include _arpg_cooldown_timer and _arpg_iframes_remaining
    // =========================================================================

    @Test
    fun `arpg var decls include cooldown timer and iframes remaining`() {
        val config =
            io.github.gbkt.rpg.domain.ActionRpgConfig(
                model = io.github.gbkt.rpg.domain.CombatModel.REALTIME_COOLDOWN
            )
        val system = buildArpgSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val varDecls = visitor.generateActionRpgVarDecls(system)
        val names = varDecls.map { it.name }

        assertTrue(
            names.contains("_arpg_cooldown_timer"),
            "Expected '_arpg_cooldown_timer' var decl",
        )
        assertTrue(
            names.contains("_arpg_iframes_remaining"),
            "Expected '_arpg_iframes_remaining' var decl",
        )
    }

    // =========================================================================
    // Test 8: ATB var decls include _atb_gauge_N for party slots
    // =========================================================================

    @Test
    fun `arpg ATB var decls include _atb_gauge_N for party slots`() {
        val config =
            io.github.gbkt.rpg.domain.ActionRpgConfig(
                model = io.github.gbkt.rpg.domain.CombatModel.HYBRID_ATB,
                atb = io.github.gbkt.rpg.domain.AtbConfig(maxGauge = 100, baseSpeed = 1),
            )
        val system = buildArpgSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val varDecls = visitor.generateActionRpgVarDecls(system)
        val names = varDecls.map { it.name }

        assertTrue(names.contains("_atb_gauge_0"), "Expected '_atb_gauge_0' for party slot 0")
        assertTrue(names.contains("_atb_gauge_1"), "Expected '_atb_gauge_1' for party slot 1")
        assertTrue(names.contains("_atb_gauge_2"), "Expected '_atb_gauge_2' for party slot 2")
        assertTrue(names.contains("_atb_gauge_3"), "Expected '_atb_gauge_3' for party slot 3")
    }

    // =========================================================================
    // Test 9: Full pipeline — ARPG system wired into GBDKSystemVisitor
    // =========================================================================

    @Test
    fun `arpg_combat system type wired into pipeline generates arpg functions in main c`() {
        val config =
            io.github.gbkt.rpg.domain.ActionRpgConfig(
                model = io.github.gbkt.rpg.domain.CombatModel.REALTIME_COOLDOWN,
                dodgeRoll =
                    io.github.gbkt.rpg.domain.DodgeRollConfig(
                        iFrameDuration = 8,
                        cooldownFrames = 16,
                    ),
                staminaGauge =
                    io.github.gbkt.rpg.domain.StaminaGaugeConfig(
                        maxStamina = 100,
                        regenRate = 1,
                        attackCost = 20,
                        dodgeCost = 30,
                    ),
                behaviorPresets =
                    listOf(
                        io.github.gbkt.rpg.domain.BehaviorPreset(
                            type = io.github.gbkt.rpg.domain.BehaviorPresetType.CHASE,
                            range = 4,
                        )
                    ),
            )
        val system = buildArpgSystem(config = config)
        val gameIR =
            GameIR(
                name = "ArpgPipelineTest",
                config = CartridgeConfig(),
                scenes = listOf(SceneIR(id = "main")),
                systems = listOf(system),
                startScene = "main",
            )
        val mainC = generateMainC(gameIR)

        assertTrue(mainC.contains("arpg_update"), "Expected 'arpg_update' in generated main.c")
        assertTrue(mainC.contains("arpg_attack"), "Expected 'arpg_attack' in generated main.c")
        assertTrue(
            mainC.contains("arpg_dodge_roll"),
            "Expected 'arpg_dodge_roll' in generated main.c",
        )
        assertTrue(
            mainC.contains("_gauge_stamina"),
            "Expected '_gauge_stamina' reference in generated main.c (exploration gauge infra bridge)",
        )
        assertTrue(mainC.contains("ai_update"), "Expected 'ai_update' in generated main.c")
    }

    // =========================================================================
    // Test 10: ARPG without behavior presets does not generate ai_update
    // =========================================================================

    @Test
    fun `arpg without behavior presets does not generate ai_update function`() {
        val config =
            io.github.gbkt.rpg.domain.ActionRpgConfig(
                model = io.github.gbkt.rpg.domain.CombatModel.REALTIME_COOLDOWN,
                behaviorPresets = emptyList(),
            )
        val system = buildArpgSystem(config = config)
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateActionRpgFunctions(system)
        val names = functions.map { it.name }

        assertFalse(
            names.contains("ai_update"),
            "Expected no 'ai_update' when behavior presets empty",
        )
    }

    // =========================================================================
    // ROGUELIKE CODEGEN TESTS (Plan 06.8-05, G4)
    // Tests covering:
    //   - Pure roguelike: start_run, end_run, on_death generated; no meta-progression
    //   - Roguelite: meta-progression var decls emitted
    //   - Daily challenge: roguelike_daily_seed function generated
    //   - Room-clear gating: roguelike_check_room_clear function generated
    // =========================================================================

    @Test
    fun `pure roguelike generates start_run end_run and on_death functions`() {
        val config = RoguelikeConfig(mode = RoguelikeMode.PURE, permadeath = true, seedBased = true)
        val system =
            GenericSystem(
                id = "run",
                config = mapOf("type" to "roguelike_system", "config" to config),
            )
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateRoguelikeFunctions(system)
        val names = functions.map { it.name }

        assertTrue(names.contains("roguelike_start_run"), "Expected roguelike_start_run")
        assertTrue(names.contains("roguelike_end_run"), "Expected roguelike_end_run")
        assertTrue(names.contains("roguelike_on_death"), "Expected roguelike_on_death")
    }

    @Test
    fun `pure roguelike does not generate daily_seed or check_room_clear`() {
        val config =
            RoguelikeConfig(
                mode = RoguelikeMode.PURE,
                dailyChallenge = null,
                roomClearGating = false,
            )
        val system =
            GenericSystem(
                id = "run",
                config = mapOf("type" to "roguelike_system", "config" to config),
            )
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateRoguelikeFunctions(system)
        val names = functions.map { it.name }

        assertFalse(
            names.contains("roguelike_daily_seed"),
            "Pure roguelike should not have daily_seed",
        )
        assertFalse(
            names.contains("roguelike_check_room_clear"),
            "Pure roguelike without gating should not have check_room_clear",
        )
    }

    @Test
    fun `roguelite mode emits meta-progression var decls`() {
        val config =
            RoguelikeConfig(
                mode = RoguelikeMode.ROGUELITE,
                metaProgression = MetaProgressionConfig(unlockSlots = 8),
            )
        val system =
            GenericSystem(
                id = "run",
                config = mapOf("type" to "roguelike_system", "config" to config),
            )
        val visitor = RpgVisitor(buildMinimalGameIR())

        val varDecls = visitor.generateRoguelikeVarDecls(system)
        val names = varDecls.map { it.name }

        assertTrue(names.contains("_rogue_seed"), "Expected _rogue_seed")
        assertTrue(names.contains("_rogue_run_active"), "Expected _rogue_run_active")
        assertTrue(names.contains("_rogue_room_clear"), "Expected _rogue_room_clear")
        assertTrue(names.contains("_rogue_unlock"), "Expected _rogue_unlock array for roguelite")
    }

    @Test
    fun `pure roguelike var decls do not include unlock array`() {
        val config = RoguelikeConfig(mode = RoguelikeMode.PURE, metaProgression = null)
        val system =
            GenericSystem(
                id = "run",
                config = mapOf("type" to "roguelike_system", "config" to config),
            )
        val visitor = RpgVisitor(buildMinimalGameIR())

        val varDecls = visitor.generateRoguelikeVarDecls(system)
        val names = varDecls.map { it.name }

        assertFalse(names.contains("_rogue_unlock"), "Pure roguelike should not have _rogue_unlock")
    }

    @Test
    fun `daily challenge enabled generates roguelike_daily_seed function`() {
        val config =
            RoguelikeConfig(
                mode = RoguelikeMode.PURE,
                dailyChallenge = DailyChallengeConfig(enabled = true),
            )
        val system =
            GenericSystem(
                id = "run",
                config = mapOf("type" to "roguelike_system", "config" to config),
            )
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateRoguelikeFunctions(system)
        val names = functions.map { it.name }

        assertTrue(
            names.contains("roguelike_daily_seed"),
            "Expected roguelike_daily_seed when daily challenge enabled",
        )
    }

    @Test
    fun `daily challenge disabled does not generate roguelike_daily_seed function`() {
        val config =
            RoguelikeConfig(
                mode = RoguelikeMode.PURE,
                dailyChallenge = DailyChallengeConfig(enabled = false),
            )
        val system =
            GenericSystem(
                id = "run",
                config = mapOf("type" to "roguelike_system", "config" to config),
            )
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateRoguelikeFunctions(system)
        val names = functions.map { it.name }

        assertFalse(
            names.contains("roguelike_daily_seed"),
            "Expected no roguelike_daily_seed when daily challenge disabled",
        )
    }

    @Test
    fun `room-clear gating generates roguelike_check_room_clear function`() {
        val config = RoguelikeConfig(mode = RoguelikeMode.PURE, roomClearGating = true)
        val system =
            GenericSystem(
                id = "run",
                config = mapOf("type" to "roguelike_system", "config" to config),
            )
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateRoguelikeFunctions(system)
        val names = functions.map { it.name }

        assertTrue(
            names.contains("roguelike_check_room_clear"),
            "Expected roguelike_check_room_clear when room gating enabled",
        )
    }

    @Test
    fun `room-clear gating disabled does not generate check_room_clear`() {
        val config = RoguelikeConfig(mode = RoguelikeMode.PURE, roomClearGating = false)
        val system =
            GenericSystem(
                id = "run",
                config = mapOf("type" to "roguelike_system", "config" to config),
            )
        val visitor = RpgVisitor(buildMinimalGameIR())

        val functions = visitor.generateRoguelikeFunctions(system)
        val names = functions.map { it.name }

        assertFalse(
            names.contains("roguelike_check_room_clear"),
            "Expected no roguelike_check_room_clear when room gating disabled",
        )
    }

    @Test
    fun `roguelike system produces valid generated C via pipeline`() {
        val config =
            RoguelikeConfig(
                mode = RoguelikeMode.ROGUELITE,
                permadeath = true,
                seedBased = true,
                dailyChallenge = DailyChallengeConfig(enabled = true),
                metaProgression = MetaProgressionConfig(unlockSlots = 8),
                roomClearGating = true,
            )
        val system =
            GenericSystem(
                id = "dungeon_run",
                config = mapOf("type" to "roguelike_system", "config" to config),
            )
        val gameIR =
            GameIR(
                name = "RogueTest",
                config = CartridgeConfig(),
                scenes = listOf(SceneIR(id = "main")),
                systems = listOf(system),
                startScene = "main",
            )

        val mainC = generateMainC(gameIR)

        // Verify key function signatures appear in generated code
        assertTrue(
            mainC.contains("roguelike_start_run"),
            "Expected roguelike_start_run in generated C",
        )
        assertTrue(mainC.contains("roguelike_end_run"), "Expected roguelike_end_run in generated C")
        assertTrue(
            mainC.contains("roguelike_on_death"),
            "Expected roguelike_on_death in generated C",
        )
        assertTrue(
            mainC.contains("roguelike_daily_seed"),
            "Expected roguelike_daily_seed in generated C",
        )
        assertTrue(
            mainC.contains("roguelike_check_room_clear"),
            "Expected roguelike_check_room_clear in generated C",
        )
        // Verify var decls
        assertTrue(mainC.contains("_rogue_seed"), "Expected _rogue_seed global in generated C")
        assertTrue(
            mainC.contains("_rogue_run_active"),
            "Expected _rogue_run_active global in generated C",
        )
        assertTrue(mainC.contains("_rogue_unlock"), "Expected _rogue_unlock array in generated C")
    }
}
