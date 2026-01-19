/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.entity.CombatComponent
import io.github.gbkt.core.entity.KnockbackDirection

// =============================================================================
// COMBAT TRAITS IR NODES
// =============================================================================

/** IR node for configuring an entity's combat component. */
data class IRCombatConfig(
    val config: CombatComponent,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * IR node for dealing action-RPG style damage to an entity.
 *
 * This is different from IRDealDamage in DamageIR.kt which uses DamageCalculation for turn-based
 * combat. This is simpler immediate damage for action games.
 */
data class IRActionDamage(
    val targetEntity: String,
    val damage: Int,
    val sourceEntity: String? = null,
    val knockbackDirection: KnockbackDirection = KnockbackDirection.AWAY,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for healing an entity. */
data class IREntityHeal(
    val targetEntity: String,
    val amount: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for applying knockback to an entity. */
data class IREntityKnockback(
    val targetEntity: String,
    val forceX: Int,
    val forceY: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for stunning an entity. */
data class IREntityStun(
    val targetEntity: String,
    val frames: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for starting invincibility on an entity. */
data class IRStartInvincibility(
    val targetEntity: String,
    val frames: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for triggering entity death. */
data class IREntityDeath(
    val targetEntity: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR node for updating combat state (call every frame). */
data class IRCombatUpdate(
    val entityName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

// =============================================================================
// COMBAT QUERY EXPRESSIONS
// =============================================================================

/** IR expression for checking if entity is invincible. */
data class IREntityIsInvincible(val entityName: String) : IRExpression

/** IR expression for checking if entity is dead. */
data class IREntityIsDead(val entityName: String) : IRExpression

/** IR expression for checking if entity is stunned. */
data class IREntityIsStunned(val entityName: String) : IRExpression

/** IR expression for getting entity's current HP. */
data class IREntityGetHp(val entityName: String) : IRExpression

/** IR expression for getting entity's max HP. */
data class IREntityGetMaxHp(val entityName: String) : IRExpression

/** IR expression for getting entity's HP percentage (0-100). */
data class IREntityGetHpPercent(val entityName: String) : IRExpression

/** IR expression for checking if entity can act (not stunned). */
data class IREntityCanAct(val entityName: String) : IRExpression

/** IR expression for checking if knockback is active. */
data class IREntityHasKnockback(val entityName: String) : IRExpression

// =============================================================================
// HITBOX-BASED DAMAGE IR
// =============================================================================

/**
 * IR statement for conditionally dealing damage when two entities collide. Handles team checking,
 * invincibility, and damage application.
 */
data class IRDamageOnCollision(
    val attackerEntity: String,
    val targetEntity: String,
    val damage: Int, // 0 means use attacker's attack power
    val checkTeam: Boolean = true, // Check team to prevent friendly fire
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR expression for checking if two entities can damage each other (different teams). */
data class IRCanDamage(val attackerEntity: String, val targetEntity: String) : IRExpression

/** IR expression for checking collision between two entities (hitbox-based). */
data class IREntityCollision(val entity1: String, val entity2: String) : IRExpression
