/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRStatement
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// CHARACTER CLASS SYSTEM
// =============================================================================

/**
 * Character class definition.
 *
 * Provides first-class support for RPG character classes/jobs with:
 * - Stat modifiers and growth rates
 * - Ability sets and learning progression
 * - Promotion chains (Fighter → Knight → Paladin)
 * - Multi-classing support
 *
 * Usage:
 * ```kotlin
 * // Basic class
 * val fighter by characterClass {
 *     name("Fighter")
 *     description("A warrior skilled in physical combat")
 *
 *     statModifiers {
 *         hp(120)      // 120% HP
 *         atk(130)     // 130% ATK
 *         def(110)     // 110% DEF
 *         matk(70)     // 70% MATK (weak magic)
 *     }
 *
 *     growth {
 *         hp(GrowthRate.HIGH)
 *         atk(GrowthRate.HIGH)
 *         def(GrowthRate.STANDARD)
 *     }
 *
 *     abilities {
 *         learns(slash, atLevel = 1)
 *         learns(powerStrike, atLevel = 5)
 *         learns(berserk, atLevel = 10)
 *     }
 * }
 *
 * // Promotion chain
 * val knight by characterClass {
 *     name("Knight")
 *     promotesFrom(fighter, atLevel = 15)
 *
 *     statModifiers {
 *         hp(140)
 *         atk(140)
 *         def(130)
 *     }
 * }
 * ```
 */
data class CharacterClass(
    /** Unique class identifier */
    val id: String,
    /** Display name */
    val displayName: String,
    /** Description for UI */
    val description: String,
    /** Stat percentage modifiers (100 = normal) */
    val statModifiers: StatModifiers,
    /** Stat growth rates */
    val growthRates: ClassGrowthRates?,
    /** Abilities learned at specific levels */
    val learnedAbilities: List<LearnedAbility>,
    /** Innate abilities (always available when in this class) */
    val innateAbilities: List<Ability>,
    /** Class this promotes from (if any) */
    val promotesFrom: CharacterClass?,
    /** Level required for promotion */
    val promotionLevel: Int,
    /** Items required for promotion */
    val promotionItems: List<PromotionRequirement>,
    /** Callback when character changes to this class */
    val onClassChangeStatements: List<IRStatement>,
    /** Callback when character promotes to this class */
    val onPromotionStatements: List<IRStatement>,
    /** Sprite override when in this class */
    val classSprite: SpriteAsset?,
    /** Whether this is a base class (can start as) */
    val isBaseClass: Boolean,
    /** Whether this class allows multi-classing */
    val allowsMulticlass: Boolean,
    /** Maximum level in this class (for job systems) */
    val maxClassLevel: Int,
    /** System index for code generation */
    var classIndex: Int = -1,
)

/**
 * Stat percentage modifiers.
 *
 * Values are percentages where 100 = normal.
 * - 120 means 120% (20% bonus)
 * - 80 means 80% (20% penalty)
 */
data class StatModifiers(
    val hp: Int = 100,
    val sp: Int = 100,
    val atk: Int = 100,
    val def: Int = 100,
    val matk: Int = 100,
    val mdef: Int = 100,
    val agl: Int = 100,
    /** Custom stat modifiers for custom stat schemas */
    val custom: Map<String, Int> = emptyMap(),
)

/** Growth rates for stats when leveling in this class. */
data class ClassGrowthRates(
    val hp: GrowthRate = GrowthRate.STANDARD,
    val sp: GrowthRate = GrowthRate.STANDARD,
    val atk: GrowthRate = GrowthRate.STANDARD,
    val def: GrowthRate = GrowthRate.STANDARD,
    val matk: GrowthRate = GrowthRate.STANDARD,
    val mdef: GrowthRate = GrowthRate.STANDARD,
    val agl: GrowthRate = GrowthRate.STANDARD,
    /** Custom stat growth rates */
    val custom: Map<String, GrowthRate> = emptyMap(),
)

/** An ability learned at a specific class level. */
data class LearnedAbility(
    val ability: Ability,
    val learnLevel: Int,
    /** Whether this ability is retained after changing class */
    val permanent: Boolean = false,
)

/** Item requirement for class promotion. */
data class PromotionRequirement(
    val itemId: String,
    val quantity: Int = 1,
    val consumed: Boolean = true,
)

// =============================================================================
// CHARACTER CLASS BUILDER
// =============================================================================

/** Property delegate for character classes. */
class CharacterClassDelegate(
    private val gameBuilder: GameBuilder,
    private val init: CharacterClassBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, CharacterClass>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, CharacterClass> {
        val builder = CharacterClassBuilder(property.name)
        builder.init()
        val characterClass = builder.build()
        gameBuilder.registerCharacterClass(characterClass)

        return ReadOnlyProperty { _, _ -> characterClass }
    }
}

/** Builder for character classes. */
@GbktDsl
class CharacterClassBuilder(private val classId: String) {
    private var displayName: String = classId.replaceFirstChar { it.uppercaseChar() }
    private var description: String = ""
    private var statModifiers: StatModifiers = StatModifiers()
    private var growthRates: ClassGrowthRates? = null
    private val learnedAbilities = mutableListOf<LearnedAbility>()
    private val innateAbilities = mutableListOf<Ability>()
    private var promotesFrom: CharacterClass? = null
    private var promotionLevel: Int = 1
    private val promotionItems = mutableListOf<PromotionRequirement>()
    private var onClassChangeStatements: List<IRStatement> = emptyList()
    private var onPromotionStatements: List<IRStatement> = emptyList()
    private var classSprite: SpriteAsset? = null
    private var isBaseClass: Boolean = true
    private var allowsMulticlass: Boolean = false
    private var maxClassLevel: Int = 99

    /** Set display name */
    fun name(name: String) {
        displayName = name
    }

    /** Set description */
    fun description(desc: String) {
        description = desc
    }

    /** Configure stat modifiers */
    fun statModifiers(init: StatModifiersBuilder.() -> Unit) {
        val builder = StatModifiersBuilder()
        builder.init()
        statModifiers = builder.build()
    }

    /** Configure growth rates */
    fun growth(init: ClassGrowthBuilder.() -> Unit) {
        val builder = ClassGrowthBuilder()
        builder.init()
        growthRates = builder.build()
    }

    /** Configure abilities learned by this class */
    fun abilities(init: ClassAbilitiesBuilder.() -> Unit) {
        val builder = ClassAbilitiesBuilder()
        builder.init()
        learnedAbilities.addAll(builder.buildLearned())
        innateAbilities.addAll(builder.buildInnate())
    }

    /** Set class this promotes from */
    fun promotesFrom(baseClass: CharacterClass, atLevel: Int = 15) {
        promotesFrom = baseClass
        promotionLevel = atLevel
        isBaseClass = false
    }

    /** Add item requirement for promotion */
    fun requiresItem(itemId: String, quantity: Int = 1, consumed: Boolean = true) {
        promotionItems.add(PromotionRequirement(itemId, quantity, consumed))
    }

    /** Callback when changing to this class */
    fun onClassChange(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onClassChangeStatements = recorder.statements
    }

    /** Callback when promoting to this class */
    fun onPromotion(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onPromotionStatements = recorder.statements
    }

    /** Set sprite override for this class */
    fun sprite(sprite: SpriteAsset) {
        classSprite = sprite
    }

    /** Mark as base class (can start game as) */
    fun baseClass(isBase: Boolean = true) {
        isBaseClass = isBase
    }

    /** Enable multi-classing */
    fun allowMulticlass(allow: Boolean = true) {
        allowsMulticlass = allow
    }

    /** Set max level for this class (job systems) */
    fun maxLevel(level: Int) {
        maxClassLevel = level
    }

    internal fun build() =
        CharacterClass(
            id = classId,
            displayName = displayName,
            description = description,
            statModifiers = statModifiers,
            growthRates = growthRates,
            learnedAbilities = learnedAbilities.toList(),
            innateAbilities = innateAbilities.toList(),
            promotesFrom = promotesFrom,
            promotionLevel = promotionLevel,
            promotionItems = promotionItems.toList(),
            onClassChangeStatements = onClassChangeStatements,
            onPromotionStatements = onPromotionStatements,
            classSprite = classSprite,
            isBaseClass = isBaseClass,
            allowsMulticlass = allowsMulticlass,
            maxClassLevel = maxClassLevel,
        )
}

/** Builder for stat modifiers. */
@GbktDsl
class StatModifiersBuilder {
    private var hp: Int = 100
    private var sp: Int = 100
    private var atk: Int = 100
    private var def: Int = 100
    private var matk: Int = 100
    private var mdef: Int = 100
    private var agl: Int = 100
    private val custom = mutableMapOf<String, Int>()

    fun hp(percent: Int) {
        hp = percent
    }

    fun sp(percent: Int) {
        sp = percent
    }

    fun atk(percent: Int) {
        atk = percent
    }

    fun def(percent: Int) {
        def = percent
    }

    fun matk(percent: Int) {
        matk = percent
    }

    fun mdef(percent: Int) {
        mdef = percent
    }

    fun agl(percent: Int) {
        agl = percent
    }

    /** Set custom stat modifier */
    fun stat(name: String, percent: Int) {
        custom[name] = percent
    }

    internal fun build() = StatModifiers(hp, sp, atk, def, matk, mdef, agl, custom.toMap())
}

/** Builder for class growth rates. */
@GbktDsl
class ClassGrowthBuilder {
    private var hp: GrowthRate = GrowthRate.STANDARD
    private var sp: GrowthRate = GrowthRate.STANDARD
    private var atk: GrowthRate = GrowthRate.STANDARD
    private var def: GrowthRate = GrowthRate.STANDARD
    private var matk: GrowthRate = GrowthRate.STANDARD
    private var mdef: GrowthRate = GrowthRate.STANDARD
    private var agl: GrowthRate = GrowthRate.STANDARD
    private val custom = mutableMapOf<String, GrowthRate>()

    fun hp(rate: GrowthRate) {
        hp = rate
    }

    fun sp(rate: GrowthRate) {
        sp = rate
    }

    fun atk(rate: GrowthRate) {
        atk = rate
    }

    fun def(rate: GrowthRate) {
        def = rate
    }

    fun matk(rate: GrowthRate) {
        matk = rate
    }

    fun mdef(rate: GrowthRate) {
        mdef = rate
    }

    fun agl(rate: GrowthRate) {
        agl = rate
    }

    /** Set custom stat growth rate */
    fun stat(name: String, rate: GrowthRate) {
        custom[name] = rate
    }

    internal fun build() = ClassGrowthRates(hp, sp, atk, def, matk, mdef, agl, custom.toMap())
}

/** Builder for class abilities. */
@GbktDsl
class ClassAbilitiesBuilder {
    private val learned = mutableListOf<LearnedAbility>()
    private val innate = mutableListOf<Ability>()

    /** Learn an ability at a specific level */
    fun learns(ability: Ability, atLevel: Int, permanent: Boolean = false) {
        learned.add(LearnedAbility(ability, atLevel, permanent))
    }

    /** Add an innate ability (always available in this class) */
    fun innate(ability: Ability) {
        innate.add(ability)
    }

    internal fun buildLearned(): List<LearnedAbility> = learned.toList()

    internal fun buildInnate(): List<Ability> = innate.toList()
}

// =============================================================================
// MULTI-CLASS SYSTEM
// =============================================================================

/**
 * Multi-class configuration for a character.
 *
 * Supports FF5-style job switching where abilities from mastered jobs can be equipped in other
 * jobs.
 */
data class MultiClassConfig(
    /** Primary class */
    val primaryClass: CharacterClass,
    /** Secondary class (for hybrid systems) */
    val secondaryClass: CharacterClass?,
    /** Level in each class for job systems */
    val classLevels: Map<CharacterClass, Int>,
    /** Abilities learned from other classes that can be equipped */
    val equippableAbilities: List<Ability>,
    /** Number of ability slots for non-innate abilities */
    val abilitySlots: Int,
)

/**
 * Class mastery tracking.
 *
 * For games where mastering a class unlocks permanent benefits.
 */
data class ClassMastery(
    /** The class */
    val characterClass: CharacterClass,
    /** Whether the class is mastered */
    val mastered: Boolean,
    /** Current class experience/JP */
    val classExp: Int,
    /** Experience needed for mastery */
    val masteryThreshold: Int,
    /** Abilities unlocked by mastering this class */
    val masteryAbilities: List<Ability>,
)

// =============================================================================
// PROMOTION CHAIN
// =============================================================================

/** Represents a promotion chain (e.g., Fighter → Knight → Paladin). */
data class PromotionChain(
    /** Chain name for identification */
    val name: String,
    /** Base class that starts the chain */
    val baseClass: CharacterClass,
    /** All classes in order of promotion */
    val classes: List<CharacterClass>,
) {
    /** Get the next class in the promotion chain */
    fun getNextPromotion(currentClass: CharacterClass): CharacterClass? {
        val index = classes.indexOf(currentClass)
        return if (index >= 0 && index < classes.size - 1) {
            classes[index + 1]
        } else {
            null
        }
    }

    /** Check if a character can promote from their current class */
    fun canPromote(currentClass: CharacterClass, level: Int): Boolean {
        val nextClass = getNextPromotion(currentClass)
        return nextClass != null && level >= nextClass.promotionLevel
    }
}

// =============================================================================
// GAME BUILDER EXTENSIONS
// =============================================================================

/**
 * Define a character class.
 *
 * Character classes provide stat modifiers, ability sets, and promotion paths.
 *
 * Usage:
 * ```kotlin
 * val fighter by characterClass {
 *     name("Fighter")
 *     description("A warrior skilled in physical combat")
 *
 *     statModifiers {
 *         hp(120)
 *         atk(130)
 *         def(110)
 *         matk(70)
 *     }
 *
 *     growth {
 *         hp(GrowthRate.HIGH)
 *         atk(GrowthRate.HIGH)
 *     }
 *
 *     abilities {
 *         learns(slash, atLevel = 1)
 *         learns(powerStrike, atLevel = 5)
 *     }
 * }
 * ```
 */
fun GameBuilder.characterClass(init: CharacterClassBuilder.() -> Unit): CharacterClassDelegate {
    return CharacterClassDelegate(this, init)
}
