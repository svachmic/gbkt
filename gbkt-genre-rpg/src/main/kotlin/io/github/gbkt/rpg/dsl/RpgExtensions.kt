/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "MatchingDeclarationName"
) // File contains both extensions and RpgRegistry (multi-declaration file)

package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.CombatEngineBuilder
import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.ScriptBuilder
import io.github.gbkt.core.dsl.SystemRef
import io.github.gbkt.core.ir.CallExpr
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatStateId
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.rpg.domain.AbilityDef
import io.github.gbkt.rpg.domain.CharacterDef
import io.github.gbkt.rpg.domain.CurrencyRef
import io.github.gbkt.rpg.domain.MerchantDef
import io.github.gbkt.rpg.domain.MonsterDef
import io.github.gbkt.rpg.domain.RoguelikeConfig
import io.github.gbkt.rpg.domain.StatusEffectDef

// =============================================================================
// RPG DSL EXTENSIONS ON GameBuilder
// =============================================================================
//
// These functions extend GameBuilder with RPG-specific DSL constructs.
// They demonstrate the BOM separation pattern:
//   - gbkt-rpg depends on gbkt-core (one-directional)
//   - GameBuilder does NOT know about RPG types
//   - RPG builders produce CORE IR types (GenericSystem) — no new sealed subtypes
//
// RpgRegistry stores character and monster definitions during DSL recording.
// It is tied to the current game builder via a companion-object ThreadLocal,
// mirroring the GameBuilderContext pattern used for variable delegates.
// =============================================================================

/**
 * Thread-local registry for RPG character and monster definitions.
 *
 * Initialized when the first RPG extension function is called within a `game {}` block. The
 * registry lives for the duration of the game-building lambda.
 *
 * Pattern: mirrors [io.github.gbkt.core.dsl.GameBuilderContext] thread-local.
 */
internal object RpgRegistry {
    private val holder = ThreadLocal<MutableMap<String, Any>>()

    /** Returns the current registry map, initializing it if needed. */
    private fun current(): MutableMap<String, Any> {
        return holder.get()
            ?: run {
                val map = mutableMapOf<String, Any>()
                holder.set(map)
                map
            }
    }

    /** Registers a character definition by ID. */
    fun registerCharacter(def: CharacterDef) {
        current()["char:${def.id}"] = def
    }

    /** Registers a monster definition by ID. */
    fun registerMonster(def: MonsterDef) {
        current()["monster:${def.id}"] = def
    }
}

/**
 * Defines a playable character and registers it in the RPG domain registry.
 *
 * Returns a [CharacterDef] that can be passed to [simpleBattle] party lists.
 *
 * ```kotlin
 * val hero = character("hero") {
 *     name("Hero")
 *     stats { hp(20); atk(5); def(3) }
 * }
 * ```
 *
 * The [CharacterDef] is a plain domain data class — NOT an IR type. No sealed interface from `ir`
 * is implemented.
 */
fun GameBuilder.character(id: String, block: CharacterBuilder.() -> Unit): CharacterDef {
    val builder = CharacterBuilder(id)
    builder.block()
    val def = builder.build()
    RpgRegistry.registerCharacter(def)

    // Register a GenericSystem so the backend can emit C stat structures.
    // NO new sealed IR subtypes — all data travels in the config map.
    val systemConfig =
        buildMap<String, Any> {
            put("type", "rpg_character_system")
            put("stats", def.stats)
            put("level", def.level)
            put("maxLevel", def.maxLevel)
            put("expCurve", def.expCurve)
            put("onLevelUpOps", def.onLevelUpOps)
            if (def.learningConfig != null) {
                put("learningConfig", def.learningConfig)
            }
        }
    val system = GenericSystem(id = id, config = systemConfig)
    registerSystem(system)

    return def
}

/**
 * Defines a monster/enemy and registers it in the RPG domain registry.
 *
 * Returns a [MonsterDef] that can be added to encounter pools in [simpleBattle].
 *
 * ```kotlin
 * val goblin = monster("goblin") {
 *     name("Goblin")
 *     stats { hp(10); atk(3); def(1) }
 *     exp(5)
 * }
 * ```
 *
 * The [MonsterDef] is a plain domain data class — NOT an IR type.
 */
fun GameBuilder.monster(id: String, block: MonsterBuilder.() -> Unit): MonsterDef {
    val builder = MonsterBuilder(id)
    builder.block()
    val def = builder.build()
    RpgRegistry.registerMonster(def)

    // Register a GenericSystem so the backend can emit monster AI C functions.
    // All monster data travels in the config map (no new sealed IR subtypes).
    val system = GenericSystem(id = id, config = mapOf("type" to "rpg_monster", "def" to def))
    registerSystem(system)

    return def
}

/**
 * Configures and registers a simple turn-based battle system.
 *
 * Produces a [CombatEngineSystem] with [io.github.gbkt.core.ir.CombatType.TURN_BASED] and encounter
 * configuration stored in [CombatEngineSystem.encounterConfig]. The backend dispatches this through
 * [io.github.gbkt.backend.gbdk.codegen.visitor.CombatVisitor] via the existing TURN_BASED path.
 *
 * ```kotlin
 * val hero = character("hero") { name("Hero"); stats { hp(20); atk(5); def(3) } }
 * val goblin = monster("goblin") { name("Goblin"); stats { hp(10); atk(3); def(1) } }
 *
 * simpleBattle("combat") {
 *     party(hero)
 *     encounter { +goblin }
 *     onVictory { navigate(gameplayScene) }
 *     onDefeat { navigate(gameoverScene) }
 * }
 * ```
 */
fun GameBuilder.simpleBattle(id: String, block: SimpleBattleBuilder.() -> Unit): BattleRef {
    val builder = SimpleBattleBuilder(id)
    builder.block()
    val system = builder.buildCombatEngineSystem()
    registerSystem(system)
    return BattleRef(id)
}

/**
 * Defines an ability (skill/spell) and registers it as a [GenericSystem].
 *
 * Returns an [AbilityRef] for use in builder contexts without raw string IDs. The [GenericSystem]
 * config keys are:
 * - `"type"` → `"rpg_ability"`
 * - `"def"` → [AbilityDef] carrying all ability data
 *
 * ```kotlin
 * val fireball by ability {
 *     name("Fireball")
 *     cost(sp = 8)
 *     targeting(TargetingMode.SINGLE_ENEMY)
 *     aspect(Aspect.FIRE)
 *     power(30)
 *     execute { /* effect ops */ }
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 */
fun GameBuilder.ability(id: String, block: AbilityBuilder.() -> Unit): AbilityRef {
    val builder = AbilityBuilder(id)
    builder.block()
    val def = builder.build()
    val system = GenericSystem(id = id, config = mapOf("type" to "rpg_ability", "def" to def))
    registerSystem(system)
    return AbilityRef(id)
}

/**
 * Creates an [AbilityDelegate] for property-name inference syntax.
 *
 * ```kotlin
 * val fireball by ability {
 *     name("Fireball")
 * }
 * ```
 */
fun GameBuilder.ability(block: AbilityBuilder.() -> Unit): AbilityDelegate =
    AbilityDelegate(id = "", block = block, gameBuilder = this)

/**
 * Defines a status effect and registers it as a [GenericSystem].
 *
 * Returns a [StatusEffectRef] for use in builder contexts. The [GenericSystem] config keys are:
 * - `"type"` → `"rpg_status_effect"`
 * - `"def"` → [StatusEffectDef] carrying all status effect data
 *
 * ```kotlin
 * val poison by statusEffect {
 *     name("Poison")
 *     debuff()
 *     duration(5)
 *     damagePerTurn(10)
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 */
fun GameBuilder.statusEffect(id: String, block: StatusEffectBuilder.() -> Unit): StatusEffectRef {
    val builder = StatusEffectBuilder(id)
    builder.block()
    val def = builder.build()
    val system = GenericSystem(id = id, config = mapOf("type" to "rpg_status_effect", "def" to def))
    registerSystem(system)
    return StatusEffectRef(id)
}

/**
 * Creates a [StatusEffectDelegate] for property-name inference syntax.
 *
 * ```kotlin
 * val poison by statusEffect {
 *     name("Poison")
 *     debuff()
 * }
 * ```
 */
fun GameBuilder.statusEffect(block: StatusEffectBuilder.() -> Unit): StatusEffectDelegate =
    StatusEffectDelegate(id = "", block = block, gameBuilder = this)

// =============================================================================
// EQUIPMENT SYSTEM DSL EXTENSIONS
// =============================================================================

/**
 * Configures and registers the equipment system.
 *
 * Produces a [GenericSystem] with config type `"rpg_equipment_system"`. The backend generates
 * equip/unequip C functions, slot globals, and optional upgrade/enchant/durability helpers.
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 */
fun GameBuilder.equipmentSystem(block: EquipmentSystemBuilder.() -> Unit) {
    val builder = EquipmentSystemBuilder()
    builder.block()
    val config = builder.build()
    val system =
        GenericSystem(
            id = "equipment_system",
            config = mapOf("type" to "rpg_equipment_system", "config" to config),
        )
    registerSystem(system)
}

// =============================================================================
// CHARACTER CLASS DSL EXTENSIONS
// =============================================================================

/**
 * Defines a character class/job and registers it as a [GenericSystem].
 *
 * Returns a [ClassRef] for use in builder contexts without raw string IDs.
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 */
fun GameBuilder.characterClass(id: String, block: ClassBuilder.() -> Unit): ClassRef {
    val builder = ClassBuilder(id)
    builder.block()
    val def = builder.build()
    val system = GenericSystem(id = id, config = mapOf("type" to "rpg_class", "def" to def))
    registerSystem(system)
    return ClassRef(id)
}

/**
 * Creates a [ClassDelegate] for property-name inference syntax.
 *
 * ```kotlin
 * val warrior by characterClass {
 *     name("Warrior")
 * }
 * ```
 */
fun GameBuilder.characterClass(block: ClassBuilder.() -> Unit): ClassDelegate =
    ClassDelegate(id = "", block = block, gameBuilder = this)

// =============================================================================
// BATTLE REF
// =============================================================================

/**
 * Typed reference to a registered simple-battle system.
 *
 * Returned by [GameBuilder.simpleBattle] for use in [ScriptBuilder.battleUpdate] without raw string
 * IDs.
 */
data class BattleRef(val id: String) : SystemRef {
    override val systemId: String
        get() = id
}

// =============================================================================
// RPG DSL EXTENSIONS ON ScriptBuilder
// =============================================================================

/**
 * Fires a battle update tick for the simple battle system with [battleId].
 *
 * Emits a [io.github.gbkt.core.ir.TriggerSystem] ScriptOp with the given [battleId]. Call this
 * every frame in a battle scene to drive the combat state machine.
 *
 * ```kotlin
 * scene("combat_scene") {
 *     enter {}
 *     frame { battleUpdate("combat") }
 * }
 * ```
 *
 * The emitted op is `TriggerSystem("combat")` — a core IR type, not RPG-specific.
 */
fun ScriptBuilder.battleUpdate(battleId: String) {
    triggerSystem(BattleRef(battleId))
}

/**
 * Fires a battle update tick using a typed [BattleRef].
 *
 * Preferred over the string overload — eliminates magic string references.
 *
 * ```kotlin
 * val combat = simpleBattle("combat") { ... }
 * scene("battle") {
 *     frame { battleUpdate(combat) }
 * }
 * ```
 */
fun ScriptBuilder.battleUpdate(battle: BattleRef) {
    triggerSystem(battle)
}

// =============================================================================
// TYPED COMBAT STATE QUERIES (GAP-10)
// =============================================================================

/**
 * Returns an [Expr] that evaluates to true when the combat system is in the given state.
 *
 * Produces a call to the generated `combat_is_in_state_{battleId}(state)` helper function. Use with
 * [io.github.gbkt.core.dsl.ScriptBuilder.whenever] for state-based scene logic.
 *
 * Prefer this over the string-based overload — eliminates magic string state names.
 *
 * Usage:
 * ```kotlin
 * val combat = simpleBattle("combat") { ... }
 * scene("battle") {
 *     every.frame {
 *         battleUpdate(combat)
 *         whenever(combatIsInState(CombatStates.VICTORY, combat)) {
 *             navigate(victoryScene)
 *         }
 *         whenever(combatIsInState(CombatStates.DEFEAT, combat)) {
 *             navigate(gameOverScene)
 *         }
 *     }
 * }
 * ```
 *
 * @param state The combat state to check — use constants from
 *   [io.github.gbkt.rpg.domain.CombatStates].
 * @param battle Typed reference to the combat system returned by [GameBuilder.simpleBattle].
 */
fun combatIsInState(state: CombatStateId, battle: BattleRef): Expr =
    CallExpr(function = "combat_is_in_state_${battle.id}", args = listOf(VarRef(state.id)))

/**
 * Returns an [Expr] that evaluates to true when the combat system is in the given state.
 *
 * String-based overload for migration and escape-hatch use. Prefer the typed overload:
 * `combatIsInState(CombatStates.VICTORY, combatRef)`.
 *
 * @param stateId Raw string state constant name (e.g. `"COMBAT_STATE_VICTORY"`).
 * @param battleId Battle system string identifier.
 */
@Deprecated(
    message = "Use combatIsInState(CombatStateId, BattleRef) to eliminate magic strings",
    replaceWith =
        ReplaceWith(
            "combatIsInState(CombatStateId(stateId), BattleRef(battleId))",
            "io.github.gbkt.core.ir.CombatStateId",
            "io.github.gbkt.rpg.dsl.BattleRef",
        ),
)
fun combatIsInState(stateId: String, battleId: String): Expr =
    combatIsInState(CombatStateId(stateId), BattleRef(battleId))

/**
 * Configures and registers an ATB (Active Time Battle) combat system.
 *
 * Produces a [CombatEngineSystem] with [io.github.gbkt.core.ir.CombatType.ATB] and an
 * [io.github.gbkt.core.ir.AtbConfig] populated from the [block]. The system is registered directly
 * as a [CombatEngineSystem] IR node — NOT a [GenericSystem] — because ATB IS a combat variant
 * handled by [io.github.gbkt.backend.gbdk.codegen.visitor.CombatVisitor].
 *
 * ```kotlin
 * atbCombat("combat") {
 *     gaugeModel(AtbGaugeModel.FILL)
 *     waitMode(AtbWaitMode.WAIT)
 *     fillRate(4)
 *     turnOrder(TurnOrderStrategy.SPEED_BASED)
 *     onVictory { navigate(winScene) }
 *     onDefeat { navigate(gameoverScene) }
 * }
 * ```
 *
 * @param id Unique system identifier (used in generated C function names).
 * @param block ATB configuration block executed against an [AtbCombatBuilder].
 */
fun GameBuilder.atbCombat(id: String, block: AtbCombatBuilder.() -> Unit) {
    val builder = AtbCombatBuilder(id)
    builder.block()
    val system: CombatEngineSystem = builder.build()
    registerSystem(system)
}

/**
 * Configures and registers a wave-survival combat system.
 *
 * Produces a [CombatEngineSystem] with [CombatType.WAVE_SURVIVAL] and a [WaveSurvivalConfig]
 * populated from the [block]. The system handles wave progression, between-wave behavior, scripted
 * and procedural wave definitions.
 *
 * ```kotlin
 * waveSurvival("waves") {
 *     wave(1) { monsters("goblin", "goblin") }
 *     wave(2) { monsters("goblin", "orc") }
 *     proceduralWave(3) {
 *         pool("goblin", "orc", "troll")
 *         count(min = 2, max = 4)
 *         difficulty(150)
 *     }
 *     betweenWaves {
 *         heal(20)
 *         shop()
 *     }
 *     maxWaves(10)
 * }
 * ```
 *
 * @param id Unique system identifier (used in generated C function names).
 * @param block Wave survival configuration block executed against a [WaveSurvivalBuilder].
 */
fun GameBuilder.waveSurvival(id: String, block: WaveSurvivalBuilder.() -> Unit) {
    val builder = WaveSurvivalBuilder()
    builder.block()
    val waveSurvivalConfig = builder.build()
    val system =
        CombatEngineSystem(
            id = id,
            combatType = CombatType.WAVE_SURVIVAL,
            waveSurvivalConfig = waveSurvivalConfig,
        )
    registerSystem(system)
}

/**
 * Configures and registers a tactical grid combat system (SRPG variant).
 *
 * Produces a [CombatEngineSystem] with [CombatType.TACTICAL_GRID] and a
 * [io.github.gbkt.core.ir.TacticalGridConfig] populated from the [block]. The system supports unit
 * movement range, terrain costs, elevation, facing/flanking bonuses, line-of-sight, and AoE ability
 * targeting on a 2D tile grid.
 *
 * ```kotlin
 * tacticalCombat("battle") {
 *     gridSize(10, 10)
 *     enableTerrain()
 *     enableElevation(bonusPerLevel = 15)
 *     enableFacing(flanking = 25, backstab = 50)
 *     movementRange(4)
 *     terrain("marsh") {
 *         name("Marsh")
 *         movementCost(2)
 *         damagePerTurn(5)
 *     }
 * }
 * ```
 *
 * @param id Unique system identifier (used in generated C function names).
 * @param block Tactical grid configuration block executed against a [TacticalGridBuilder].
 */
fun GameBuilder.tacticalCombat(id: String, block: TacticalGridBuilder.() -> Unit) {
    val builder = TacticalGridBuilder()
    builder.block()
    val tacticalConfig = builder.build()
    val system =
        CombatEngineSystem(
            id = id,
            combatType = CombatType.TACTICAL_GRID,
            tacticalGridConfig = tacticalConfig,
        )
    registerSystem(system)
}

// =============================================================================
// COMBAT HOOK DSL EXTENSION ON CombatEngineBuilder
// =============================================================================

/**
 * Registers combat lifecycle hooks on a [CombatEngineBuilder].
 *
 * Provides a DSL entry point for [CombatHookBuilder], allowing developers to inject custom
 * [io.github.gbkt.core.ir.ScriptOp] lists at key moments in the combat state machine.
 *
 * ```kotlin
 * combatEngine("combat") {
 *     type(CombatType.TURN_BASED)
 *     hooks {
 *         beforeAction { /* record stats before each action */ }
 *         afterDamage { navigate(damageFlashScene) }
 *         onVictory { /* extra effects before victory ops */ }
 *     }
 * }
 * ```
 *
 * Zero overhead when hooks are empty — hook functions only emitted when ops are registered.
 *
 * @param block Configuration block for [CombatHookBuilder].
 */
fun CombatEngineBuilder.hooks(block: CombatHookBuilder.() -> Unit) {
    val builder = CombatHookBuilder()
    builder.block()
    setCombatHooks(builder.build())
}

// =============================================================================
// ECONOMY / SHOP DSL EXTENSIONS
// =============================================================================

/**
 * Defines a merchant/shop and registers it as a [GenericSystem].
 *
 * Produces a [GenericSystem] with config type `"rpg_merchant"`. The backend generates:
 * - `_shop_<id>_stock[]` and `_shop_<id>_prices[]` const arrays
 * - `buy_from_<id>(slot_idx)` and `sell_to_<id>(item_id)` functions
 * - `is_<id>_stock_available(slot_idx)` for flag-gated stock
 *
 * Per-item sell price overrides take precedence over the global sellRatio (GAP-10).
 *
 * ```kotlin
 * merchant("blacksmith") {
 *     name("Blacksmith")
 *     item("iron_sword") { price(200) }
 *     item("rare_blade") { price(500); sellPrice(300) }  // sellPrice overrides global ratio
 *     sellRatio(40)
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 */
fun GameBuilder.merchant(id: String, block: MerchantBuilder.() -> Unit) {
    val builder = MerchantBuilder(id)
    builder.block()
    val def: MerchantDef = builder.build()
    val system = GenericSystem(id = id, config = mapOf("type" to "rpg_merchant", "def" to def))
    registerSystem(system)
}

/**
 * Defines a loot table and registers it as a [GenericSystem].
 *
 * Produces a [GenericSystem] with config type `"rpg_loot_table"`. The backend generates
 * `roll_loot_<id>()` with weighted random selection and optional guaranteed drop.
 *
 * ```kotlin
 * lootTable("goblin_drops") {
 *     entry("gold_coin") { chance(60) }
 *     entry("herb") { chance(30); rarity(Rarity.UNCOMMON) }
 *     guaranteed("goblin_fang")
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 */
fun GameBuilder.lootTable(id: String, block: LootTableBuilder.() -> Unit) {
    val builder = LootTableBuilder(id)
    builder.block()
    val def = builder.build()
    val system = GenericSystem(id = id, config = mapOf("type" to "rpg_loot_table", "def" to def))
    registerSystem(system)
}

/**
 * Configures crafting recipes and registers them as a [GenericSystem].
 *
 * Produces a [GenericSystem] with config type `"rpg_crafting"`.
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 */
fun GameBuilder.craftingRecipes(block: CraftingBuilder.() -> Unit) {
    val builder = CraftingBuilder()
    builder.block()
    val system =
        GenericSystem(
            id = "crafting_system",
            config = mapOf("type" to "rpg_crafting", "recipes" to builder.recipes.toList()),
        )
    registerSystem(system)
}

// =============================================================================
// PARTY MANAGEMENT DSL EXTENSIONS
// =============================================================================

/**
 * Configures and registers the party management system.
 *
 * Produces a [GenericSystem] with config type `"rpg_party_system"`. The backend generates:
 * - `_party_active[N]` and `_party_active_count` arrays
 * - `add_to_party()`, `remove_from_party()`, `swap_party_member()` functions
 * - Guest member support: `_party_is_guest[]`, `is_guest()`, `remove_guest()` (GAP-4)
 * - Row formation: `_party_row[]`, `set_row()` (when enableRowFormation = true)
 *
 * ```kotlin
 * partySystem {
 *     maxActive(4)
 *     reserve(enabled = true, size = 4, expShare = 50)
 *     rowFormation(enabled = true)
 *     member("hero")
 *     guestMember("npc_ally")  // AI-controlled, locked equipment
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 */
fun GameBuilder.partySystem(block: PartyBuilder.() -> Unit) {
    val builder = PartyBuilder()
    builder.block()
    val config = builder.build()
    val system =
        GenericSystem(
            id = "party_system",
            config = mapOf("type" to "rpg_party_system", "config" to config),
        )
    registerSystem(system)
}

// =============================================================================
// RPG SAVE INTEGRATION DSL EXTENSIONS
// =============================================================================

/**
 * Configures and registers the RPG save system.
 *
 * Produces a [GenericSystem] with config type `"rpg_save"`. The backend generates:
 * - `save_rpg_state(slot)` — serializes character stats, inventory, party, flags to SRAM
 * - `load_rpg_state(slot)` — deserializes from SRAM with checksum validation (GAP-11)
 * - `_save_corrupt` global UINT8 flag set on checksum mismatch
 * - `auto_save_rpg()` when autoSaveEnabled = true
 * - `new_game_plus()` when enableNewGamePlus = true
 *
 * ```kotlin
 * rpgSave {
 *     slots(3)
 *     mode(SaveMode.SAVE_POINT)
 *     autoSave(AutoSaveTrigger.AFTER_BATTLE)
 *     newGamePlus { carryOver("inventory", "abilities") }
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 */
fun GameBuilder.rpgSave(block: RpgSaveBuilder.() -> Unit) {
    val builder = RpgSaveBuilder()
    builder.block()
    val config = builder.build()
    val system =
        GenericSystem(id = "rpg_save", config = mapOf("type" to "rpg_save", "config" to config))
    registerSystem(system)
}

// =============================================================================
// ABILITY LEARNING DSL EXTENSIONS
// =============================================================================

// =============================================================================
// ROGUELIKE SYSTEM DSL EXTENSIONS
// =============================================================================

/**
 * Configures and registers a roguelike or roguelite system.
 *
 * Produces a [GenericSystem] with config type `"roguelike_system"`. The backend generates:
 * - `roguelike_start_run(seed)` — initialises RNG and resets run-local state
 * - `roguelike_end_run()` — if roguelite mode, persists meta-progression to SRAM
 * - `roguelike_on_death()` — permadeath wipe (clears all run-local state)
 * - `roguelike_daily_seed()` — date-based seed computation (when daily challenge enabled)
 * - `roguelike_check_room_clear()` — room-exit gate check (when roomClearGating enabled)
 *
 * Variable declarations:
 * - `_rogue_seed` (UINT16) — current run seed
 * - `_rogue_run_active` (UINT8) — 1 when a run is in progress, 0 otherwise
 * - `_rogue_room_clear` (UINT8) — 1 when current room enemies are defeated
 * - `_rogue_unlock[N]` UINT8 array (roguelite mode only) — persistent unlock slots
 *
 * ```kotlin
 * roguelike("dungeon_run") {
 *     mode(RoguelikeMode.ROGUELITE)
 *     permadeath(true)
 *     seedBased(true)
 *     dailyChallenge { enabled(true) }
 *     metaProgression {
 *         unlockSlots(16)
 *         carryOver("meta_gold")
 *     }
 *     roomClearGating(true)
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 */
fun GameBuilder.roguelike(id: String, block: RoguelikeBuilder.() -> Unit): GenericSystem {
    val builder = RoguelikeBuilder()
    builder.block()
    val config: RoguelikeConfig = builder.build()
    val system =
        GenericSystem(id = id, config = mapOf("type" to "roguelike_system", "config" to config))
    registerSystem(system)
    return system
}

/**
 * Configures and registers the ability learning system.
 *
 * Produces a [GenericSystem] with config type `"rpg_ability_learning"`. The backend generates:
 * - `check_auto_learn(char_id, level)` CSwitch on level for auto-learn
 * - `_skill_points[N]` globals and `spend_skill_points()` for skill point unlock
 * - `_skill_unlocked[N]` bit array and `can_unlock_skill()` prerequisite check
 * - `_ability_mastery[N]` counters and `gain_mastery()` when enableMastery = true
 *
 * ```kotlin
 * abilityLearning {
 *     autoLearn("fireball", atLevel = 5)
 *     skillPoint("meteor", cost = 3)
 *     skillTree {
 *         node("slash") { cost(1) }
 *         node("power_slash") { requires("slash"); cost(2) }
 *     }
 *     mastery(enabled = true, levels = 3) {
 *         evolves("fire_ball", into = "mega_fire")
 *     }
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 */
fun GameBuilder.abilityLearning(block: AbilityLearningBuilder.() -> Unit) {
    val builder = AbilityLearningBuilder()
    builder.block()
    val config = builder.build()
    val system =
        GenericSystem(
            id = "ability_learning",
            config = mapOf("type" to "rpg_ability_learning", "config" to config),
        )
    registerSystem(system)
}

// =============================================================================
// MULTI-CURRENCY DSL EXTENSIONS (Plan 06.8-03, H11)
// =============================================================================

/**
 * Defines a named in-game currency and registers it as a [GenericSystem].
 *
 * Produces a [GenericSystem] with config type `"rpg_currency"`. The backend generates:
 * - `_currency_{id}` UINT16 global (current amount, initialized to 0)
 * - `_currency_{id}_max` UINT16 const (max cap)
 * - `add_{id}(amount)` — add with max clamping
 * - `sub_{id}(amount)` — subtract (clamps to 0)
 * - `exchange_{id}_{to}(amount)` — exchange function when exchange rates defined
 * - Localization key `str_currency_{id}` for the currency display name
 *
 * ```kotlin
 * val gold by currency { max(9999) }
 * val gems by currency { max(99); exchange(to = gold, rate = 10) }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 */
fun GameBuilder.currency(id: String, block: CurrencyBuilder.() -> Unit): CurrencyRef {
    val builder = CurrencyBuilder(id)
    builder.block()
    val def = builder.build()
    val system = GenericSystem(id = id, config = mapOf("type" to "rpg_currency", "def" to def))
    registerSystem(system)
    return CurrencyRef(id)
}

/**
 * Creates a [CurrencyDelegate] for property-name inference syntax.
 *
 * ```kotlin
 * val gold by currency { max(9999) }
 * ```
 */
fun GameBuilder.currency(block: CurrencyBuilder.() -> Unit): CurrencyDelegate =
    CurrencyDelegate(id = "", block = block, gameBuilder = this)

// =============================================================================
// ACTION RPG DSL EXTENSIONS
// =============================================================================

/**
 * Configures and registers an action RPG combat system.
 *
 * Produces a [GenericSystem] with config type `"arpg_combat"` and an
 * [io.github.gbkt.rpg.domain.ActionRpgConfig] stored under the `"config"` key. The backend
 * generates:
 * - `arpg_update()` — per-frame: cooldown timers, ATB gauge fill, stamina regen
 * - `arpg_attack(target_id)` — cooldown check, optional stamina deduction
 * - `arpg_dodge_roll()` (when dodge roll configured) — i-frame counter, cooldown
 * - `atb_check_ready(char_id)` (when ATB configured and model is HYBRID_ATB)
 * - `ai_update(entity_id)` — dispatches on behavior preset type
 *
 * Stamina bridges to `ExplorationGaugeIR(id="stamina")` — the global `_gauge_stamina` is managed by
 * existing exploration gauge codegen; ARPG codegen adds attack/dodge deductions.
 *
 * ```kotlin
 * actionRpg("combat") {
 *     combatModel(CombatModel.REALTIME_COOLDOWN)
 *     dodgeRoll { iFrames(8); cooldown(16) }
 *     stamina { max(100); regen(1); attackCost(20); dodgeCost(30) }
 *     behaviorPreset(BehaviorPresetType.CHASE, range = 5)
 *     behaviorPreset(BehaviorPresetType.ATTACK_WHEN_CLOSE, range = 1)
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique system identifier used in generated C function names.
 * @param block Action RPG configuration block executed against an [ActionRpgBuilder].
 * @return The registered [GenericSystem].
 */
fun GameBuilder.actionRpg(id: String, block: ActionRpgBuilder.() -> Unit): GenericSystem {
    val builder = ActionRpgBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}
