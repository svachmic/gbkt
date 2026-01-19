/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.entity

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RawCodeEscapeHatch
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.AssignableExpr
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.IRActionDamage
import io.github.gbkt.core.ir.IRCanDamage
import io.github.gbkt.core.ir.IRDamageOnCollision
import io.github.gbkt.core.ir.IREntityCanAct
import io.github.gbkt.core.ir.IREntityDeath
import io.github.gbkt.core.ir.IREntityHasKnockback
import io.github.gbkt.core.ir.IREntityHeal
import io.github.gbkt.core.ir.IREntityIsDead
import io.github.gbkt.core.ir.IREntityIsInvincible
import io.github.gbkt.core.ir.IREntityIsStunned
import io.github.gbkt.core.ir.IREntityKnockback
import io.github.gbkt.core.ir.IREntityStun
import io.github.gbkt.core.ir.IRStartInvincibility
import io.github.gbkt.core.ir.i8
import io.github.gbkt.core.ir.u8

// =============================================================================
// COMBAT COMPONENTS FOR ACTION-RPG COMBAT
// =============================================================================

/**
 * Combat component for entities that can take damage.
 *
 * Provides:
 * - HP tracking (current and max)
 * - Damage reception
 * - Death detection
 * - Invincibility frames
 *
 * Usage:
 * ```kotlin
 * val player by entity {
 *     position(80, 72)
 *     combat {
 *         maxHp(100)
 *         invincibilityFrames(60)  // 1 second of invincibility after hit
 *         onDeath { scene(gameoverScene) }
 *     }
 * }
 * ```
 */
class CombatComponent(
    val entityName: String,
    val maxHp: Int,
    val currentHp: Int = maxHp,
    val attackPower: Int = 10,
    val defense: Int = 0,
    val invincibilityFrames: Int = 30,
    val knockbackForce: Int = 4,
    val stunFrames: Int = 0,
    val team: CombatTeam = CombatTeam.NEUTRAL,
    /** Callback statements executed when entity takes damage */
    val onHitStatements: List<io.github.gbkt.core.ir.IRStatement> = emptyList(),
    /** Callback statements executed when entity dies */
    val onDeathStatements: List<io.github.gbkt.core.ir.IRStatement> = emptyList(),
    /** Enable sprite blinking during invincibility frames */
    val blinkOnDamage: Boolean = true,
    /** Blink interval in frames (how often to toggle visibility) */
    val blinkInterval: Int = 4,
) {
    // Variable names for code generation
    // Note: Uses suffix convention (e.g., _hp_max) to match StatAccessor naming
    val hpVarName: String = "${entityName}_hp"
    val maxHpVarName: String = "${entityName}_hp_max"
    val invincibleVarName: String = "${entityName}_invincible"
    val invincibleTimerVarName: String = "${entityName}_invincible_timer"
    val stunTimerVarName: String = "${entityName}_stun_timer"
    val knockbackXVarName: String = "${entityName}_knockback_x"
    val knockbackYVarName: String = "${entityName}_knockback_y"

    // Create GBVar instances for state variables
    internal val hpVar: GBVar<u8> = GBVar(hpVarName, u8(currentHp), GBVar.VarType.U8)
    internal val maxHpVar: GBVar<u8> = GBVar(maxHpVarName, u8(maxHp), GBVar.VarType.U8)
    internal val invincibleVar: GBVar<u8> = GBVar(invincibleVarName, u8(0), GBVar.VarType.U8)
    internal val invincibleTimerVar: GBVar<u8> =
        GBVar(invincibleTimerVarName, u8(0), GBVar.VarType.U8)
    internal val stunTimerVar: GBVar<u8> = GBVar(stunTimerVarName, u8(0), GBVar.VarType.U8)
    internal val knockbackXVar: GBVar<*> = GBVar(knockbackXVarName, i8(0), GBVar.VarType.I8)
    internal val knockbackYVar: GBVar<*> = GBVar(knockbackYVarName, i8(0), GBVar.VarType.I8)

    // Assignable expressions for DSL access
    val hp: AssignableExpr = AssignableExpr(hpVarName, GBVar.VarType.U8)
    val maxHpExpr: AssignableExpr = AssignableExpr(maxHpVarName, GBVar.VarType.U8)

    /** Check if entity is currently invincible */
    val isInvincible: Condition
        get() = Condition(IREntityIsInvincible(entityName))

    /** Check if entity is dead (HP <= 0) */
    val isDead: Condition
        get() = Condition(IREntityIsDead(entityName))

    /** Check if entity is alive (HP > 0) */
    val isAlive: Condition
        get() = !isDead

    /** Check if entity is currently stunned */
    val isStunned: Condition
        get() = Condition(IREntityIsStunned(entityName))

    /** Check if entity can act (not stunned) */
    val canAct: Condition
        get() = Condition(IREntityCanAct(entityName))

    /** Check if entity is currently being knocked back */
    val hasKnockback: Condition
        get() = Condition(IREntityHasKnockback(entityName))
}

/** Team designation for combat - determines friendly fire rules. */
enum class CombatTeam {
    /** Player and allies */
    PLAYER,
    /** Enemies */
    ENEMY,
    /** Neutral entities - can be hit by anyone */
    NEUTRAL,
    /** Environment hazards - damages everyone */
    HAZARD,
}

/** Builder for combat component configuration. */
@GbktDsl
class CombatBuilder(private val entityName: String) {
    private var maxHp: Int = 100
    private var currentHp: Int? = null
    private var attackPower: Int = 10
    private var defense: Int = 0
    private var invincibilityFrames: Int = 30
    private var knockbackForce: Int = 4
    private var stunFrames: Int = 0
    private var team: CombatTeam = CombatTeam.NEUTRAL
    private var onHitStatements: List<io.github.gbkt.core.ir.IRStatement> = emptyList()
    private var onDeathStatements: List<io.github.gbkt.core.ir.IRStatement> = emptyList()
    private var blinkOnDamage: Boolean = true
    private var blinkInterval: Int = 4

    /**
     * Set maximum HP for the entity.
     *
     * @param value Max HP (1-255 for Game Boy u8)
     */
    fun maxHp(value: Int) {
        require(value in 1..255) { "Max HP must be 1-255" }
        this.maxHp = value
    }

    /** Set starting HP (defaults to maxHp if not specified). */
    fun startingHp(value: Int) {
        require(value in 0..255) { "Starting HP must be 0-255" }
        this.currentHp = value
    }

    /** Set attack power for damage calculation. */
    fun attackPower(value: Int) {
        require(value in 0..255) { "Attack power must be 0-255" }
        this.attackPower = value
    }

    /** Set defense for damage reduction. */
    fun defense(value: Int) {
        require(value in 0..255) { "Defense must be 0-255" }
        this.defense = value
    }

    /**
     * Set invincibility frames after taking damage.
     *
     * @param frames Number of frames (0 = no invincibility, 60 = 1 second at 60fps)
     */
    fun invincibilityFrames(frames: Int) {
        require(frames in 0..255) { "Invincibility frames must be 0-255" }
        this.invincibilityFrames = frames
    }

    /**
     * Set knockback force when hit.
     *
     * @param force Pixels to push back (0 = no knockback)
     */
    fun knockbackForce(force: Int) {
        require(force in 0..127) { "Knockback force must be 0-127" }
        this.knockbackForce = force
    }

    /**
     * Set stun frames after taking damage (entity cannot act).
     *
     * @param frames Number of frames stunned (0 = no stun)
     */
    fun stunFrames(frames: Int) {
        require(frames in 0..255) { "Stun frames must be 0-255" }
        this.stunFrames = frames
    }

    /** Set combat team for friendly fire rules. */
    fun team(team: CombatTeam) {
        this.team = team
    }

    /**
     * Register callback to execute when this entity takes damage.
     *
     * Usage:
     * ```kotlin
     * combat {
     *     maxHp(100)
     *     onHit {
     *         // Play hit sound, flash effect, etc.
     *     }
     * }
     * ```
     */
    fun onHit(block: CombatCallbackScope.() -> Unit) {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        RecordingContext.record(recorder) { CombatCallbackScope().block() }
        this.onHitStatements = recorder.statements
    }

    /**
     * Register callback to execute when this entity dies (HP reaches 0).
     *
     * Usage:
     * ```kotlin
     * combat {
     *     maxHp(100)
     *     onDeath {
     *         // Play death animation, drop items, etc.
     *     }
     * }
     * ```
     */
    fun onDeath(block: CombatCallbackScope.() -> Unit) {
        val recorder = io.github.gbkt.core.dsl.StatementRecorder()
        RecordingContext.record(recorder) { CombatCallbackScope().block() }
        this.onDeathStatements = recorder.statements
    }

    /**
     * Enable or disable sprite blinking during invincibility frames.
     *
     * @param enabled Whether to blink (default: true)
     * @param interval Frames between visibility toggles (default: 4)
     */
    fun blinkOnDamage(enabled: Boolean, interval: Int = 4) {
        require(interval in 1..255) { "Blink interval must be 1-255" }
        this.blinkOnDamage = enabled
        this.blinkInterval = interval
    }

    internal fun build(): CombatComponent {
        return CombatComponent(
            entityName = entityName,
            maxHp = maxHp,
            currentHp = currentHp ?: maxHp,
            attackPower = attackPower,
            defense = defense,
            invincibilityFrames = invincibilityFrames,
            knockbackForce = knockbackForce,
            stunFrames = stunFrames,
            team = team,
            onHitStatements = onHitStatements,
            onDeathStatements = onDeathStatements,
            blinkOnDamage = blinkOnDamage,
            blinkInterval = blinkInterval,
        )
    }
}

/** Scope for combat callbacks (onHit, onDeath). Provides access to common game DSL functions. */
@GbktDsl
class CombatCallbackScope {
    /** Raw C code escape hatch - bypasses type safety. Use sparingly. */
    @RawCodeEscapeHatch
    fun raw(code: String) {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRRaw(code))
    }
}

// =============================================================================
// COMBAT ACTIONS
// =============================================================================

/**
 * Deal damage to a target entity.
 *
 * Usage:
 * ```kotlin
 * whenever(player.sword collidesWith enemy) {
 *     dealDamage(to = enemy, amount = 10)
 * }
 * ```
 */
fun dealDamage(
    to: Entity,
    amount: Int,
    knockbackDir: KnockbackDirection = KnockbackDirection.AWAY,
) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(
                IRActionDamage(
                    targetEntity = to.name,
                    damage = amount,
                    knockbackDirection = knockbackDir,
                )
            )
    }
}

/**
 * Deal damage from one entity to another.
 *
 * Usage:
 * ```kotlin
 * whenever(player.sword collidesWith enemy) {
 *     player.dealDamage(to = enemy)  // Uses player's attack power
 * }
 * ```
 */
fun Entity.dealDamage(to: Entity, knockbackDir: KnockbackDirection = KnockbackDirection.AWAY) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(
                IRActionDamage(
                    targetEntity = to.name,
                    damage = 0, // 0 means use attacker's attack power
                    sourceEntity = this.name,
                    knockbackDirection = knockbackDir,
                )
            )
    }
}

/**
 * Heal an entity.
 *
 * Usage:
 * ```kotlin
 * whenever(player collidesWith healthPickup) {
 *     heal(player, amount = 20)
 *     healthPickup.despawn()
 * }
 * ```
 */
fun heal(target: Entity, amount: Int) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IREntityHeal(targetEntity = target.name, amount = amount))
    }
}

/**
 * Apply knockback to an entity.
 *
 * Usage:
 * ```kotlin
 * enemy.knockback(dirX = -4, dirY = 0)  // Push left
 * ```
 */
fun Entity.knockback(dirX: Int, dirY: Int) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(IREntityKnockback(targetEntity = this.name, forceX = dirX, forceY = dirY))
    }
}

/**
 * Stun an entity for a number of frames.
 *
 * Usage:
 * ```kotlin
 * enemy.stun(frames = 30)  // Stun for 0.5 seconds
 * ```
 */
fun Entity.stun(frames: Int) {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IREntityStun(targetEntity = this.name, frames = frames))
    }
}

/**
 * Start invincibility for an entity.
 *
 * Usage:
 * ```kotlin
 * player.startInvincibility(frames = 60)
 * ```
 */
fun Entity.startInvincibility(frames: Int) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(IRStartInvincibility(targetEntity = this.name, frames = frames))
    }
}

/**
 * Kill an entity (set HP to 0 and trigger death).
 *
 * Usage:
 * ```kotlin
 * whenever(enemy.hp isBelow 1) {
 *     enemy.kill()
 * }
 * ```
 */
fun Entity.kill() {
    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(IREntityDeath(targetEntity = this.name))
    }
}

/** Direction for knockback relative to attacker. */
enum class KnockbackDirection {
    /** Push away from attacker */
    AWAY,
    /** Push toward attacker (pull) */
    TOWARD,
    /** Push left */
    LEFT,
    /** Push right */
    RIGHT,
    /** Push up */
    UP,
    /** Push down */
    DOWN,
    /** No knockback */
    NONE,
}

// =============================================================================
// HITBOX-BASED DAMAGE DEALING
// =============================================================================

// Note: Entity.collidesWith is already defined as a member function in Entity.kt
// It uses inline AABB collision checks which expand directly to comparison expressions.

/**
 * Check if this entity can damage another entity (different combat teams).
 *
 * Returns true if the entities are on different teams and damage is possible. Player vs Enemy,
 * Enemy vs Player, Hazard vs all, etc.
 *
 * Usage:
 * ```kotlin
 * whenever(player.canDamage(enemy) and (player collidesWith enemy)) {
 *     player.dealDamage(to = enemy)
 * }
 * ```
 */
fun Entity.canDamage(other: Entity): Condition {
    return Condition(IRCanDamage(this.name, other.name))
}

/**
 * Deal damage to target when this entity collides with it.
 *
 * This is a convenience function that combines collision detection with damage dealing. It
 * automatically:
 * - Checks hitbox collision
 * - Respects combat teams (no friendly fire)
 * - Respects invincibility frames
 * - Applies knockback and stun based on attacker's settings
 *
 * Usage in a frame handler:
 * ```kotlin
 * every.frame {
 *     player.damageOnCollision(enemy)  // Uses player's attack power
 * }
 * ```
 *
 * @param target The entity to damage on collision
 * @param checkTeam If true, only damage if entities are on different teams (default: true)
 */
fun Entity.damageOnCollision(target: Entity, checkTeam: Boolean = true) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(
                IRDamageOnCollision(
                    attackerEntity = this.name,
                    targetEntity = target.name,
                    damage = 0, // Use attacker's attack power
                    checkTeam = checkTeam,
                )
            )
    }
}

/**
 * Deal specific damage amount to target when this entity collides with it.
 *
 * Usage:
 * ```kotlin
 * every.frame {
 *     fireball.damageOnCollision(enemy, damage = 25)
 * }
 * ```
 *
 * @param target The entity to damage on collision
 * @param damage The damage amount to deal
 * @param checkTeam If true, only damage if entities are on different teams (default: true)
 */
fun Entity.damageOnCollision(target: Entity, damage: Int, checkTeam: Boolean = true) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(
                IRDamageOnCollision(
                    attackerEntity = this.name,
                    targetEntity = target.name,
                    damage = damage,
                    checkTeam = checkTeam,
                )
            )
    }
}
