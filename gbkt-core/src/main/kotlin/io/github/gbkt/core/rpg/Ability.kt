/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.AbilityRef
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RawCodeEscapeHatch
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRStatement
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// ABILITY SYSTEM
// =============================================================================

/** Categories of abilities. */
enum class AbilityCategory {
    /** Physical skills using ATK */
    PHYSICAL,

    /** Magic spells using MATK */
    MAGIC,

    /** Support abilities (buffs, heals) */
    SUPPORT,

    /** Special/unique abilities */
    SPECIAL,
}

/** Cost type for abilities. */
sealed class AbilityCost {
    /** Costs SP (Skill Points/Magic Points) */
    data class SP(val amount: Int) : AbilityCost()

    /** Costs HP (Health Points) */
    data class HP(val amount: Int) : AbilityCost()

    /** Costs HP as percentage of max */
    data class HPPercent(val percent: Int) : AbilityCost()

    /** No cost */
    data object Free : AbilityCost()
}

/**
 * An ability definition in the game.
 *
 * Abilities are skills or spells that characters and monsters can use in battle. They have costs,
 * targeting modes, and effects when executed.
 *
 * Usage:
 * ```kotlin
 * val fireball by ability {
 *     name("Fireball")
 *     category(AbilityCategory.MAGIC)
 *     targeting(TargetingMode.ALL_ENEMIES)
 *     cost(10.sp)
 *     aspect(Aspect.FIRE)
 *     power(120)
 *
 *     execute { caster, targets ->
 *         // Deal fire damage to all targets
 *         dealDamage(targets, power, Aspect.FIRE)
 *     }
 * }
 * ```
 */
class Ability(
    /** Unique identifier (from property name) */
    val id: String,
    /** Display name */
    val displayName: String,
    /** Description of what the ability does */
    val description: String,
    /** Ability category */
    val category: AbilityCategory,
    /** Targeting mode */
    val targeting: TargetingMode,
    /** Cost to use the ability (legacy single-resource) */
    val cost: AbilityCost,
    /** Extended cost with multi-resource support */
    val extendedCost: ExtendedAbilityCost? = null,
    /** Power multiplier for damage/healing (100 = 1.0x) */
    val power: Int,
    /** Damage/healing aspect */
    val aspect: Aspect,
    /** Status effects that can be applied */
    val statusEffects: List<AbilityStatusEffect>,
    /** IR statements for execution */
    val executeStatements: List<IRStatement>,
    /** Whether this ability can be used in battle */
    val usableInBattle: Boolean,
    /** Whether this ability can be used outside battle */
    val usableOutOfBattle: Boolean,
    /** Minimum level required to use */
    val levelRequirement: Int,
    /** Character classes that can use this ability (empty = any) */
    val classRestrictions: Set<String>,
    /** Ability index for code generation (assigned by GameBuilder) */
    var abilityIndex: Int = -1,
) {
    /** Type-safe reference to this ability */
    val ref: AbilityRef
        get() = AbilityRef(id)

    /** Check if ability can be used given current SP */
    fun canAfford(currentSp: Int, currentHp: Int, maxHp: Int): Boolean {
        return when (cost) {
            is AbilityCost.SP -> currentSp >= cost.amount
            is AbilityCost.HP -> currentHp > cost.amount // Must survive after
            is AbilityCost.HPPercent -> currentHp > maxHp * cost.percent / 100
            AbilityCost.Free -> true
        }
    }

    /** Get SP cost or 0 if not SP-based */
    val spCost: Int
        get() = (cost as? AbilityCost.SP)?.amount ?: 0

    /** Check if this ability has execution logic */
    val hasExecute: Boolean
        get() = executeStatements.isNotEmpty()
}

/** Status effect application for abilities. */
data class AbilityStatusEffect(
    /** The effect to apply */
    val effect: StatusEffectDefinition,
    /** Chance to apply (1-100) */
    val chance: Int = 100,
    /** Whether to apply to targets or caster */
    val applyToSelf: Boolean = false,
)

// =============================================================================
// ABILITY BUILDER
// =============================================================================

/**
 * Property delegate for abilities.
 *
 * Usage: val fireball by ability { ... }
 */
class AbilityDelegate(
    private val gameBuilder: GameBuilder,
    private val init: AbilityBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Ability>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, Ability> {
        val builder = AbilityBuilder(property.name)
        builder.init()
        val ability = builder.build()
        gameBuilder.registerAbility(ability)

        return ReadOnlyProperty { _, _ -> ability }
    }
}

/** Builder for ability definitions. */
@GbktDsl
class AbilityBuilder(private val abilityId: String) {
    private var displayName: String = abilityId.replaceFirstChar { it.uppercaseChar() }
    private var description: String = ""
    private var category: AbilityCategory = AbilityCategory.PHYSICAL
    private var targeting: TargetingMode = TargetingMode.SINGLE_ENEMY
    private var cost: AbilityCost = AbilityCost.Free
    private var extendedCost: ExtendedAbilityCost? = null
    private var power: Int = 100
    private var aspect: Aspect = Aspect.PHYSICAL
    private val statusEffects = mutableListOf<AbilityStatusEffect>()
    private var executeStatements: List<IRStatement> = emptyList()
    private var usableInBattle: Boolean = true
    private var usableOutOfBattle: Boolean = false
    private var levelRequirement: Int = 1
    private val classRestrictions = mutableSetOf<String>()

    /** Set the display name. */
    fun name(name: String) {
        displayName = name
    }

    /** Set the description. */
    fun description(desc: String) {
        description = desc
    }

    /** Set the ability category. */
    fun category(cat: AbilityCategory) {
        category = cat
    }

    /** Set the targeting mode. */
    fun targeting(mode: TargetingMode) {
        targeting = mode
    }

    /** Set the cost using DSL helpers (legacy single-resource). */
    fun cost(cost: AbilityCost) {
        this.cost = cost
        this.extendedCost = ExtendedAbilityCost.fromLegacy(cost)
    }

    /**
     * Set extended cost with multi-resource support.
     *
     * Usage:
     * ```kotlin
     * val powerfulSpell by ability {
     *     name("Powerful Spell")
     *
     *     // Multi-resource cost: 5 SP AND 10 HP
     *     cost {
     *         sp(5)
     *         hp(10)
     *     }
     * }
     *
     * val escalatingAbility by ability {
     *     name("Escalating Power")
     *
     *     // Cost increases each use
     *     cost {
     *         sp(base = 5, perUse = 2, max = 20)
     *     }
     * }
     * ```
     */
    fun cost(init: ExtendedCostBuilder.() -> Unit) {
        val builder = ExtendedCostBuilder()
        builder.init()
        this.extendedCost = builder.build()
        // Also set legacy cost for backward compatibility
        this.cost = extendedCost?.toLegacy() ?: AbilityCost.Free
    }

    /** Set the power multiplier (100 = base, 150 = 1.5x, etc). */
    fun power(value: Int) {
        require(value > 0) { "Power must be positive" }
        power = value
    }

    /** Set the damage/healing aspect. */
    fun aspect(aspect: Aspect) {
        this.aspect = aspect
    }

    /** Make this a physical ability (uses ATK/DEF). */
    fun physical() {
        category = AbilityCategory.PHYSICAL
        aspect = Aspect.PHYSICAL
    }

    /** Make this a magical ability (uses MATK/MDEF). */
    fun magical() {
        category = AbilityCategory.MAGIC
        aspect = Aspect.MAGICAL
    }

    /** Add a status effect that this ability can apply. */
    fun appliesEffect(effect: StatusEffectDefinition, chance: Int = 100, toSelf: Boolean = false) {
        require(chance in 1..100) { "Effect chance must be 1-100" }
        statusEffects.add(AbilityStatusEffect(effect, chance, toSelf))
    }

    /** Configure where this ability can be used. */
    fun usableIn(battle: Boolean, field: Boolean) {
        usableInBattle = battle
        usableOutOfBattle = field
    }

    /** Set minimum level to learn/use this ability. */
    fun levelRequired(level: Int) {
        require(level >= 1) { "Level requirement must be at least 1" }
        levelRequirement = level
    }

    /**
     * Set the level at which this ability unlocks.
     *
     * When a character reaches this level, the ability becomes available. This is an alias for
     * [levelRequired] with unlock semantics.
     *
     * Usage:
     * ```kotlin
     * val fireball by ability {
     *     name("Fireball")
     *     unlocksAt(level = 10)
     *     execute { dealDamage() }
     * }
     * ```
     */
    fun unlocksAt(level: Int) {
        require(level >= 1) { "Unlock level must be at least 1" }
        levelRequirement = level
    }

    /** Restrict to specific character classes. */
    fun restrictToClass(vararg classes: String) {
        classRestrictions.addAll(classes)
    }

    /**
     * Define the execution logic for this ability.
     *
     * The scope provides access to caster, target info, and helper functions for dealing damage and
     * applying effects.
     */
    fun execute(init: AbilityExecuteScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) {
            val scope = AbilityExecuteScope(abilityId, power, aspect)
            scope.init()
        }
        executeStatements = recorder.statements
    }

    internal fun build(): Ability {
        return Ability(
            id = abilityId,
            displayName = displayName,
            description = description,
            category = category,
            targeting = targeting,
            cost = cost,
            extendedCost = extendedCost,
            power = power,
            aspect = aspect,
            statusEffects = statusEffects.toList(),
            executeStatements = executeStatements,
            usableInBattle = usableInBattle,
            usableOutOfBattle = usableOutOfBattle,
            levelRequirement = levelRequirement,
            classRestrictions = classRestrictions.toSet(),
        )
    }
}

// =============================================================================
// ABILITY EXECUTE SCOPE
// =============================================================================

/** Scope for defining ability execution logic. */
@GbktDsl
class AbilityExecuteScope(
    private val abilityId: String,
    private val defaultPower: Int,
    private val defaultAspect: Aspect,
) {
    /** Deal damage to targets based on ability settings. */
    fun dealDamage(power: Int = defaultPower, aspect: Aspect = defaultAspect) {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRAbilityDealDamage(abilityId, power, aspect))
    }

    /** Heal targets. */
    fun heal(power: Int = defaultPower) {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRAbilityHeal(abilityId, power))
    }

    /** Apply a status effect to targets. */
    fun applyEffect(effect: StatusEffectDefinition, chance: Int = 100) {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRAbilityApplyEffect(abilityId, effect.id.value, chance))
    }

    /** Apply a status effect to the caster. */
    fun applyEffectToSelf(effect: StatusEffectDefinition) {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRAbilityApplyEffectToSelf(abilityId, effect.id.value))
    }

    /** Drain HP from target and heal caster. */
    fun drain(power: Int = defaultPower, aspect: Aspect = defaultAspect, healPercent: Int = 50) {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRAbilityDrain(abilityId, power, aspect, healPercent))
    }

    /** Play an animation. */
    fun playAnimation(animationId: String) {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRAbilityPlayAnimation(abilityId, animationId))
    }

    /** Play a sound effect. */
    fun playSfx(sfxId: String) {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRAbilityPlaySfx(abilityId, sfxId))
    }

    /** Show a message. */
    fun showMessage(message: String) {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRAbilityShowMessage(abilityId, message))
    }

    /** Raw C code for complex ability effects - bypasses type safety. Use sparingly. */
    @RawCodeEscapeHatch
    fun raw(code: String) {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRRaw(code))
    }

    /**
     * Attempt to instantly kill the target.
     *
     * @param chance Percentage chance (1-100) to instantly kill
     * @param ignoreImmunity If true, ignores target's instant-kill immunity
     */
    fun instantKill(chance: Int, ignoreImmunity: Boolean = false) {
        require(chance in 1..100) { "Instant kill chance must be 1-100" }
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRAbilityInstantKill(abilityId, chance, ignoreImmunity))
    }

    /** Remove all debuffs from the target. */
    fun cureDebuffs() {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRAbilityCureDebuffs(abilityId))
    }

    /**
     * Restore SP to the target.
     *
     * @param amount Amount of SP to restore
     */
    fun restoreSp(amount: Int) {
        require(amount > 0) { "SP restore amount must be positive" }
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRAbilityRestoreSp(abilityId, amount))
    }

    /** Fully heal the target (HP and SP to max). */
    fun fullHeal() {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRAbilityFullHeal(abilityId))
    }
}

// =============================================================================
// COST DSL HELPERS
// =============================================================================

/** Create an SP cost. Usage: cost(10.sp) */
val Int.sp: AbilityCost.SP
    get() {
        require(this > 0) { "SP cost must be positive" }
        return AbilityCost.SP(this)
    }

/** Create an HP cost. Usage: cost(20.hp) */
val Int.hp: AbilityCost.HP
    get() {
        require(this > 0) { "HP cost must be positive" }
        return AbilityCost.HP(this)
    }

/** Create an HP percentage cost. Usage: cost(10.hpPercent) */
val Int.hpPercent: AbilityCost.HPPercent
    get() {
        require(this in 1..100) { "HP percent cost must be 1-100" }
        return AbilityCost.HPPercent(this)
    }

// =============================================================================
// GAME BUILDER EXTENSION
// =============================================================================

/**
 * Create an ability definition.
 *
 * Usage:
 * ```kotlin
 * val fireball by ability {
 *     name("Fireball")
 *     category(AbilityCategory.MAGIC)
 *     targeting(TargetingMode.ALL_ENEMIES)
 *     cost(10.sp)
 *     aspect(Aspect.FIRE)
 *     power(120)
 * }
 * ```
 */
fun GameBuilder.ability(init: AbilityBuilder.() -> Unit): AbilityDelegate {
    return AbilityDelegate(this, init)
}
