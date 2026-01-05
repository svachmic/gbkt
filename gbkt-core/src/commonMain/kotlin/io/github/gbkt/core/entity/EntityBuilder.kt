/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.entity

import io.github.gbkt.core.StateMachine
import io.github.gbkt.core.StateMachineBuilder
import io.github.gbkt.core.TagRef
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.graphics.Animation
import io.github.gbkt.core.graphics.AnimationsBuilderWithRegions
import io.github.gbkt.core.graphics.Hitbox
import io.github.gbkt.core.graphics.Palette
import io.github.gbkt.core.graphics.RegionsBuilder
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.graphics.SpriteBinding
import io.github.gbkt.core.graphics.SpriteRegion
import io.github.gbkt.core.ir.Dimensions
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.GameScopeContext
import io.github.gbkt.core.ir.x
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// ENTITY BUILDER
// =============================================================================

/**
 * Property delegate for entities.
 *
 * Usage: val player by entity { ... }
 *
 * Uses PropertyDelegateProvider to ensure the entity is registered during delegate creation (at
 * property declaration time), not when the property is first accessed.
 */
class EntityDelegate(
    private val gameBuilder: GameBuilder,
    private val init: EntityBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Entity>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, Entity> {
        // Build and register the entity immediately when the delegate is created
        val builder = EntityBuilder(property.name, gameBuilder)
        builder.init()
        val entity = builder.build()
        gameBuilder.registerEntity(entity)

        // Return a simple property that returns the already-built entity
        return ReadOnlyProperty { _, _ -> entity }
    }
}

/** Builder for entity construction via DSL. */
@GbktDsl
class EntityBuilder(private val entityName: String, private val gameBuilder: GameBuilder) {
    private var positionComponent: PositionComponent? = null
    private var velocityComponent: VelocityComponent? = null
    private var spriteComponent: SpriteComponent? = null
    private var hitboxComponent: HitboxComponent? = null
    private var statesComponent: StatesComponent? = null
    private var tagComponent: TagComponent? = null
    private var physicsComponent: PhysicsComponent? = null

    /**
     * Add position component.
     *
     * Usage: position(80, 100) position(80, 100, u16 = true) // For larger coordinates
     */
    fun position(x: Int, y: Int, u16: Boolean = false) {
        val varType = if (u16) GBVar.VarType.U16 else GBVar.VarType.U8
        positionComponent =
            PositionComponent(entityName, x, y, varType).also { pos ->
                // Register variables with game scope
                GameScopeContext.current?.run {
                    registerVariable(pos.xVar)
                    registerVariable(pos.yVar)
                }
            }
    }

    /**
     * Add velocity component. Returns VelocityScope for optional physics configuration.
     *
     * Usage:
     * ```kotlin
     * velocity()                     // Just velocity, no physics
     * velocity().physics { ... }     // Velocity with physics (chained)
     * velocity(2, 0).physics { ... } // With initial velocity
     * ```
     */
    fun velocity(initialVelX: Int = 0, initialVelY: Int = 0): VelocityScope {
        velocityComponent =
            VelocityComponent(entityName, initialVelX, initialVelY).also { vel ->
                // Register velocity variables
                GameScopeContext.current?.run {
                    registerVariable(vel.velXVar)
                    registerVariable(vel.velYVar)
                }
            }
        return VelocityScope()
    }

    /**
     * Scope that provides physics configuration. Only accessible after velocity() is called. This
     * ensures compile-time safety: you cannot accidentally use physics without velocity.
     */
    @GbktDsl
    inner class VelocityScope internal constructor() {
        /**
         * Add physics component for gravity, friction, and velocity clamping.
         *
         * Uses fixed-point math internally (8.8 format) for Game Boy compatibility. Float values
         * are automatically converted to fixed-point.
         *
         * Usage:
         * ```kotlin
         * velocity().physics {
         *     gravity = 0.5f        // Applied each frame to velocityY
         *     friction = 0.9f       // Multiplied to velocityX each frame
         *     maxVelocity = 4 to 8  // Clamp velocityX/Y (x, y)
         * }
         * ```
         */
        fun physics(init: PhysicsBuilder.() -> Unit) {
            val builder = PhysicsBuilder(this@EntityBuilder.entityName)
            builder.init()
            this@EntityBuilder.physicsComponent = builder.build()
        }
    }

    /**
     * Add sprite component using a type-safe asset reference.
     *
     * Usage:
     * ```kotlin
     * sprite(Assets.Sprites.player) { size = 8 x 16 }
     * ```
     *
     * @see SpriteAsset
     */
    /**
     * Add sprite component. Automatically binds to entity's position if position() was called.
     *
     * Usage: sprite(SpriteAsset("player.png")) { size = 8 x 16 hitbox(0, 0, 8, 16) animations { ...
     * } }
     */
    fun sprite(
        asset: io.github.gbkt.core.assets.SpriteAsset,
        init: EntitySpriteBuilder.() -> Unit = {},
    ): Sprite {
        val slot = gameBuilder.nextSpriteSlot()
        val builder = EntitySpriteBuilder(asset.path, slot, entityName, positionComponent)
        builder.init()
        val sprite = builder.build()

        spriteComponent = SpriteComponent(sprite)

        // Extract hitbox from sprite if defined there
        sprite.hitbox?.let { hitbox ->
            if (hitboxComponent == null) {
                hitboxComponent = HitboxComponent(hitbox)
            }
        }

        // Register sprite with game builder
        gameBuilder.registerSprite(sprite)

        return sprite
    }

    /**
     * Add standalone hitbox (for invisible collision zones).
     *
     * Usage: hitbox(0, 0, 16, 16)
     */
    fun hitbox(xOffset: Int, yOffset: Int, width: Int, height: Int) {
        hitboxComponent = HitboxComponent(Hitbox(xOffset, yOffset, width, height))
    }

    /**
     * Add state machine.
     *
     * Usage: states { "idle" { enter { play("idle") } on(buttons.a.pressed) { goto("jump") } } }
     */
    fun states(init: StateMachineBuilder.() -> Unit): StateMachine {
        val builder = StateMachineBuilder(entityName)
        builder.init()
        val machine = builder.build()

        statesComponent = StatesComponent(machine)
        gameBuilder.registerStateMachine(machine)

        return machine
    }

    /**
     * Add tags for entity queries (type-safe).
     *
     * Usage:
     * ```kotlin
     * val enemyTag = tag("enemy")
     * entity {
     *     tag(enemyTag)  // Type-safe!
     * }
     * ```
     */
    fun tag(vararg tags: TagRef) {
        tagComponent = TagComponent(tags.map { it.name }.toSet())
    }

    /**
     * Add tags for entity queries (string-based).
     *
     * Usage: tag("enemy", "damageable")
     */
    fun tagStrings(vararg tags: String) {
        tagComponent = TagComponent(tags.toSet())
    }

    fun build(): Entity {
        return Entity(
            name = entityName,
            positionComponent = positionComponent,
            velocityComponent = velocityComponent,
            spriteComponent = spriteComponent,
            hitboxComponent = hitboxComponent,
            statesComponent = statesComponent,
            tagComponent = tagComponent,
            physicsComponent = physicsComponent,
        )
    }
}

/**
 * Extended sprite builder for use within entities. Automatically binds to entity's position if
 * available.
 */
@GbktDsl
class EntitySpriteBuilder(
    private val asset: String,
    private val slot: Int,
    private val entityName: String,
    private val positionComponent: PositionComponent?,
) {
    var size: Dimensions = 8 x 8
    private var _paletteRef: String? = null
    private var _paletteIndex: Int = 0
    private var _animations: Map<String, Animation> = emptyMap()
    private var _hitbox: Hitbox? = null
    private var _regions: Map<String, SpriteRegion> = emptyMap()

    var palette: Palette? = null
        set(value) {
            field = value
            if (value != null) {
                _paletteRef = value.name
                _paletteIndex = value.assignedSlot
            }
        }

    var paletteIndex: Int
        get() = _paletteIndex
        set(value) {
            require(value in 0..7) { "Palette index must be 0-7" }
            _paletteIndex = value
        }

    fun hitbox(xOffset: Int, yOffset: Int, width: Int, height: Int) {
        _hitbox = Hitbox(xOffset, yOffset, width, height)
    }

    fun hitbox(offset: Pair<Int, Int>, size: Dimensions) {
        _hitbox = Hitbox(offset.first, offset.second, size.width, size.height)
    }

    /**
     * Define named regions in the sprite sheet.
     *
     * Usage: regions { "idle" at 0 size 2 "run" at 2 size 4 }
     */
    fun regions(init: RegionsBuilder.() -> Unit) {
        val builder = RegionsBuilder()
        builder.init()
        _regions = builder.regions.toMap()
    }

    /**
     * Define animations for this sprite. If regions are defined, you can reference them by name.
     */
    fun animations(init: AnimationsBuilderWithRegions.() -> Unit) {
        val builder = AnimationsBuilderWithRegions(_regions)
        builder.init()
        _animations = builder.animations.toMap()
    }

    fun build(): Sprite {
        // Auto-bind to entity position if available
        val binding = positionComponent?.let { SpriteBinding(it.xVarName, it.yVarName) }

        return Sprite(
            name = "${entityName}_sprite",
            asset = asset,
            width = size.width,
            height = size.height,
            oamSlot = slot,
            binding = binding,
            paletteRef = _paletteRef,
            paletteIndex = _paletteIndex,
            animations = _animations,
            hitbox = _hitbox,
        )
    }
}
