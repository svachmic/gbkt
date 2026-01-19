/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.AnimationRef
import io.github.gbkt.core.CharacterRef
import io.github.gbkt.core.StateMachine
import io.github.gbkt.core.StateMachineBuilder
import io.github.gbkt.core.TagRef
import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.entity.Entity
import io.github.gbkt.core.entity.EntitySpriteBuilder
import io.github.gbkt.core.entity.HitboxComponent
import io.github.gbkt.core.entity.Movable
import io.github.gbkt.core.entity.PhysicsBuilder
import io.github.gbkt.core.entity.PhysicsComponent
import io.github.gbkt.core.entity.Position
import io.github.gbkt.core.entity.PositionComponent
import io.github.gbkt.core.entity.SpriteComponent
import io.github.gbkt.core.entity.StatesComponent
import io.github.gbkt.core.entity.TagComponent
import io.github.gbkt.core.entity.Velocity
import io.github.gbkt.core.entity.VelocityComponent
import io.github.gbkt.core.graphics.Hitbox
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.ir.AssignableExpr
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.GameScopeContext
import io.github.gbkt.core.ir.StatsDefinition
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// CHARACTER - Entity with RPG Stats
// =============================================================================

/**
 * A game character with RPG statistics and entity components.
 *
 * Character extends the entity system with stat management for RPG games. It provides all entity
 * functionality (position, sprite, collision) plus RPG-specific features (HP, SP, ATK, DEF, etc.).
 *
 * Usage:
 * ```kotlin
 * val hero by character {
 *     position(80, 72)
 *     sprite(SpriteAsset("hero.png")) { size = 8 x 16 }
 *
 *     stats {
 *         hp(100, max = 999)
 *         sp(50, max = 99)
 *         atk(10)
 *         def(8)
 *         agl(12)
 *     }
 * }
 *
 * // Later in game logic:
 * hero.hp -= damage
 * whenever(hero.hp.isZero) { scene(gameoverScene) }
 * ```
 */
class Character(
    val name: String,
    internal val entity: Entity,
    internal val characterStats: CharacterStats?,
    internal val levelingConfig: LevelingConfig? = null,
    /** Equipment tracking for this character */
    val equipment: CharacterEquipment? = null,
    /** Elemental aspect profile (resistances/weaknesses) */
    val aspectProfile: AspectProfile? = null,
    /** The character's base attack ability (always available, no cost) */
    val baseAttackAbility: Ability? = null,
) : Movable {

    /** Whether this character has a custom base attack defined */
    val hasBaseAttack: Boolean
        get() = baseAttackAbility != null

    /** Experience and leveling system for this character */
    val experience: ExpSystem by lazy { ExpSystem(name, levelingConfig) }

    /** Whether this character has leveling configured */
    val hasLeveling: Boolean
        get() = levelingConfig != null

    // === Stats Access ===

    /**
     * Access character statistics.
     *
     * @throws IllegalStateException if character has no stats defined
     */
    val stats: CharacterStats
        get() = characterStats ?: error("Character '$name' has no stats defined")

    /** Hit Points - shortcut to stats.hp */
    val hp: StatAccessor
        get() = stats.hp

    /** Skill Points - shortcut to stats.sp */
    val sp: StatAccessor
        get() = stats.sp

    /** Physical Attack - shortcut to stats.atk */
    val atk: StatAccessor
        get() = stats.atk

    /** Physical Defense - shortcut to stats.def */
    val def: StatAccessor
        get() = stats.def

    /** Magical Attack - shortcut to stats.matk */
    val matk: StatAccessor
        get() = stats.matk

    /** Magical Defense - shortcut to stats.mdef */
    val mdef: StatAccessor
        get() = stats.mdef

    /** Agility - shortcut to stats.agl */
    val agl: StatAccessor
        get() = stats.agl

    /** Level - shortcut to stats.level */
    val level: StatAccessor
        get() = stats.level

    /** Experience Points - shortcut to stats.exp */
    val exp: StatAccessor
        get() = stats.exp

    val hasStats: Boolean
        get() = characterStats != null

    // === Aspect Access ===

    /**
     * Get damage modifier for an aspect. Returns NORMAL if no aspect profile defined or aspect not
     * specified.
     */
    fun getAspectModifier(aspect: Aspect): DamageModifier =
        aspectProfile?.getModifier(aspect) ?: DamageModifier.NORMAL

    /** Type-safe reference to this character */
    val ref: CharacterRef
        get() = CharacterRef(name)

    // === Delegate to Entity for all position/sprite/etc. functionality ===

    override val x: AssignableExpr
        get() = entity.x

    override val y: AssignableExpr
        get() = entity.y

    val xOrNull: AssignableExpr?
        get() = entity.xOrNull

    val yOrNull: AssignableExpr?
        get() = entity.yOrNull

    val position: Position
        get() = entity.position

    val positionOrNull: Position?
        get() = entity.positionOrNull

    override val velX: AssignableExpr
        get() = entity.velX

    override val velY: AssignableExpr
        get() = entity.velY

    val velXOrNull: AssignableExpr?
        get() = entity.velXOrNull

    val velYOrNull: AssignableExpr?
        get() = entity.velYOrNull

    val velocity: Velocity
        get() = entity.velocity

    val velocityOrNull: Velocity?
        get() = entity.velocityOrNull

    val sprite: Sprite?
        get() = entity.sprite

    val spriteSlot: Int
        get() = entity.spriteSlot

    fun play(ref: AnimationRef, loop: Boolean = true) = entity.play(ref, loop)

    fun stopAnimation() = entity.stopAnimation()

    fun setFrame(index: Int) = entity.setFrame(index)

    fun show() = entity.show()

    fun hide() = entity.hide()

    fun startState(stateName: String) = entity.startState(stateName)

    fun updateStates() = entity.updateStates()

    fun gotoState(stateName: String) = entity.gotoState(stateName)

    fun isInState(stateName: String): Condition = entity.isInState(stateName)

    fun isInStateOrNull(stateName: String): Condition? = entity.isInStateOrNull(stateName)

    infix fun collidesWith(other: Entity): Condition = entity collidesWith other

    infix fun collidesWith(other: Character): Condition = entity collidesWith other.entity

    infix fun collidesWithAny(tagRef: TagRef): Condition = entity collidesWithAny tagRef

    val tags: Set<String>
        get() = entity.tags

    fun hasTag(tagRef: TagRef): Boolean = entity.hasTag(tagRef)

    fun applyPhysics() = entity.applyPhysics()

    val hasPosition: Boolean
        get() = entity.hasPosition

    val hasVelocity: Boolean
        get() = entity.hasVelocity

    val hasSprite: Boolean
        get() = entity.hasSprite

    val hasHitbox: Boolean
        get() = entity.hasHitbox

    val hasStates: Boolean
        get() = entity.hasStates

    val hasTags: Boolean
        get() = entity.hasTags

    val hasPhysics: Boolean
        get() = entity.hasPhysics
}

// =============================================================================
// CHARACTER BUILDER
// =============================================================================

/**
 * Property delegate for characters.
 *
 * Usage: val hero by character { ... }
 */
class CharacterDelegate(
    private val gameBuilder: GameBuilder,
    private val init: CharacterBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Character>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, Character> {
        val builder = CharacterBuilder(property.name, gameBuilder)
        builder.init()
        val character = builder.build()
        gameBuilder.registerCharacter(character)

        return ReadOnlyProperty { _, _ -> character }
    }
}

/**
 * Builder for character construction via DSL.
 *
 * Provides all entity functionality plus stats definition.
 */
@GbktDsl
class CharacterBuilder(private val characterName: String, private val gameBuilder: GameBuilder) {
    private var positionComponent: PositionComponent? = null
    private var velocityComponent: VelocityComponent? = null
    private var spriteComponent: SpriteComponent? = null
    private var hitboxComponent: HitboxComponent? = null
    private var statesComponent: StatesComponent? = null
    private var tagComponent: TagComponent? = null
    private var physicsComponent: PhysicsComponent? = null
    private var statsDefinition: StatsDefinition? = null
    private var levelingConfig: LevelingConfig? = null
    private var equipmentBuilder: EquipmentBuilder? = null
    private var aspectProfile: AspectProfile? = null
    private var baseAttackAbility: Ability? = null

    // === Entity Components ===

    fun position(x: Int, y: Int, u16: Boolean = false) {
        val varType = if (u16) GBVar.VarType.U16 else GBVar.VarType.U8
        positionComponent =
            PositionComponent(characterName, x, y, varType).also { pos ->
                GameScopeContext.current?.run {
                    registerVariable(pos.xVar)
                    registerVariable(pos.yVar)
                }
            }
    }

    fun velocity(initialVelX: Int = 0, initialVelY: Int = 0): VelocityScope {
        velocityComponent =
            VelocityComponent(characterName, initialVelX, initialVelY).also { vel ->
                GameScopeContext.current?.run {
                    registerVariable(vel.velXVar)
                    registerVariable(vel.velYVar)
                }
            }
        return VelocityScope()
    }

    @GbktDsl
    inner class VelocityScope internal constructor() {
        fun physics(init: PhysicsBuilder.() -> Unit) {
            val builder = PhysicsBuilder(this@CharacterBuilder.characterName)
            builder.init()
            this@CharacterBuilder.physicsComponent = builder.build()
        }
    }

    fun sprite(asset: SpriteAsset, init: EntitySpriteBuilder.() -> Unit = {}): Sprite {
        val slot = gameBuilder.nextSpriteSlot()
        val builder = EntitySpriteBuilder(asset.path, slot, characterName, positionComponent)
        builder.init()
        val sprite = builder.build()

        spriteComponent = SpriteComponent(sprite)

        sprite.hitbox?.let { hitbox ->
            if (hitboxComponent == null) {
                hitboxComponent = HitboxComponent(hitbox)
            }
        }

        gameBuilder.registerSprite(sprite)
        return sprite
    }

    fun hitbox(xOffset: Int, yOffset: Int, width: Int, height: Int) {
        hitboxComponent = HitboxComponent(Hitbox(xOffset, yOffset, width, height))
    }

    fun states(init: StateMachineBuilder.() -> Unit): StateMachine {
        val builder = StateMachineBuilder(characterName)
        builder.init()
        val machine = builder.build()

        statesComponent = StatesComponent(machine)
        gameBuilder.registerStateMachine(machine)

        return machine
    }

    fun tag(vararg tags: TagRef) {
        tagComponent = TagComponent(tags.map { it.name }.toSet())
    }

    fun tagStrings(vararg tags: String) {
        tagComponent = TagComponent(tags.toSet())
    }

    // === Stats Component ===

    /**
     * Define character statistics.
     *
     * Usage:
     * ```kotlin
     * stats {
     *     hp(100, max = 999)
     *     sp(50, max = 99)
     *     atk(10)
     *     def(8)
     * }
     * ```
     */
    fun stats(init: StatsBuilder.() -> Unit) {
        val builder = StatsBuilder(characterName)
        builder.init()
        statsDefinition = builder.build()
    }

    /**
     * Configure experience and leveling for this character.
     *
     * Usage:
     * ```kotlin
     * leveling {
     *     maxLevel(99)
     *     expCurve(ExpCurve.STANDARD)
     *     baseExp(100)
     *     growth {
     *         maxHp(GrowthRate.HIGH)
     *         atk(GrowthRate.STANDARD)
     *     }
     *     onLevelUp {
     *         raw("play_sfx(SFX_LEVELUP);")
     *     }
     * }
     * ```
     */
    fun leveling(init: LevelingBuilder.() -> Unit) {
        val builder = LevelingBuilder(characterName)
        builder.init()
        levelingConfig = builder.build()
    }

    /**
     * Configure starting equipment for this character.
     *
     * Usage:
     * ```kotlin
     * equipment {
     *     weapon(ironSword)
     *     armor(leatherArmor)
     *     accessory(powerRing)
     * }
     * ```
     */
    fun equipment(init: EquipmentBuilder.() -> Unit) {
        val builder = EquipmentBuilder(characterName)
        builder.init()
        equipmentBuilder = builder
    }

    /**
     * Define elemental aspect resistances and weaknesses.
     *
     * Usage:
     * ```kotlin
     * aspects {
     *     resist(Aspect.FIRE)
     *     weak(Aspect.ICE)
     *     immune(Aspect.LIGHTNING)
     * }
     * ```
     */
    fun aspects(init: AspectProfileBuilder.() -> Unit) {
        val builder = AspectProfileBuilder(characterName)
        builder.init()
        aspectProfile = builder.build()
    }

    /**
     * Set the character's base attack to an existing ability.
     *
     * The base attack is always available and typically has no SP cost.
     *
     * Usage:
     * ```kotlin
     * val swordSlash by ability {
     *     name("Sword Slash")
     *     physical()
     *     execute { dealDamage(power = 100) }
     * }
     *
     * val fighter by character {
     *     stats { hp(100); atk(15) }
     *     baseAttack(swordSlash)
     * }
     * ```
     */
    fun baseAttack(ability: Ability) {
        baseAttackAbility = ability
    }

    /**
     * Define an inline base attack for this character.
     *
     * Creates a new ability specifically for this character's base attack. The ability is
     * automatically registered and has no SP cost.
     *
     * Usage:
     * ```kotlin
     * val fighter by character {
     *     stats { hp(100); atk(15) }
     *     baseAttack {
     *         name("Sword Slash")
     *         physical()
     *         power(100)
     *         execute { dealDamage() }
     *     }
     * }
     * ```
     */
    fun baseAttack(init: AbilityBuilder.() -> Unit) {
        val abilityId = "${characterName}_base_attack"
        val builder = AbilityBuilder(abilityId)
        builder.cost(AbilityCost.Free) // Base attacks have no cost
        builder.init()
        val ability = builder.build()
        gameBuilder.registerAbility(ability)
        baseAttackAbility = ability
    }

    fun build(): Character {
        val entity =
            Entity(
                name = characterName,
                positionComponent = positionComponent,
                velocityComponent = velocityComponent,
                spriteComponent = spriteComponent,
                hitboxComponent = hitboxComponent,
                statesComponent = statesComponent,
                tagComponent = tagComponent,
                physicsComponent = physicsComponent,
            )

        val characterStats = statsDefinition?.let { CharacterStats(characterName, it) }

        return Character(
            name = characterName,
            entity = entity,
            characterStats = characterStats,
            levelingConfig = levelingConfig,
            equipment = equipmentBuilder?.build(),
            aspectProfile = aspectProfile,
            baseAttackAbility = baseAttackAbility,
        )
    }
}

// =============================================================================
// GAME BUILDER EXTENSION
// =============================================================================

/**
 * Create a character with RPG stats.
 *
 * Usage:
 * ```kotlin
 * val hero by character {
 *     position(80, 72)
 *     sprite(SpriteAsset("hero.png")) { size = 8 x 16 }
 *     stats {
 *         hp(100, max = 999)
 *         atk(10)
 *     }
 * }
 * ```
 */
fun GameBuilder.character(init: CharacterBuilder.() -> Unit): CharacterDelegate {
    return CharacterDelegate(this, init)
}
