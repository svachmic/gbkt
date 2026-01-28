/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.rpg

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.core.generateStatement
import io.github.gbkt.core.entity.CombatComponent
import io.github.gbkt.core.entity.CombatTeam
import io.github.gbkt.core.entity.KnockbackDirection
import io.github.gbkt.core.ir.IRActionDamage
import io.github.gbkt.core.ir.IRCanDamage
import io.github.gbkt.core.ir.IRCombatConfig
import io.github.gbkt.core.ir.IRCombatUpdate
import io.github.gbkt.core.ir.IRDamageOnCollision
import io.github.gbkt.core.ir.IREntityCanAct
import io.github.gbkt.core.ir.IREntityCollision
import io.github.gbkt.core.ir.IREntityDeath
import io.github.gbkt.core.ir.IREntityGetHp
import io.github.gbkt.core.ir.IREntityGetHpPercent
import io.github.gbkt.core.ir.IREntityGetMaxHp
import io.github.gbkt.core.ir.IREntityHasKnockback
import io.github.gbkt.core.ir.IREntityHeal
import io.github.gbkt.core.ir.IREntityIsDead
import io.github.gbkt.core.ir.IREntityIsInvincible
import io.github.gbkt.core.ir.IREntityIsStunned
import io.github.gbkt.core.ir.IREntityKnockback
import io.github.gbkt.core.ir.IREntityStun
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRStartInvincibility
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// COMBAT TRAITS CODE GENERATION
// =============================================================================

/**
 * Generate combat system code for all entities with combat components. Called during main code
 * generation phase.
 */
internal fun GBDKCodeGenerator.generateCombatFunctions() {
    val entitiesWithCombat = game.entities.filter { it.hasCombat }
    if (entitiesWithCombat.isEmpty()) return

    line("// =============================================================================")
    line("// COMBAT SYSTEM")
    line("// =============================================================================")
    line()

    // Generate configuration and helpers for each entity with combat
    for (entity in entitiesWithCombat) {
        generateCombatConfig(entity.combatConfig!!)
    }
}

/**
 * Handle combat traits IR statements.
 *
 * @return true if this was a combat traits statement and was handled, false otherwise
 */
internal fun GBDKCodeGenerator.generateCombatTraitsStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRCombatConfig -> {
            generateCombatConfig(stmt.config)
            true
        }
        is IRActionDamage -> {
            generateActionDamage(stmt)
            true
        }
        is IREntityHeal -> {
            generateEntityHeal(stmt)
            true
        }
        is IREntityKnockback -> {
            generateEntityKnockback(stmt)
            true
        }
        is IREntityStun -> {
            generateEntityStun(stmt)
            true
        }
        is IRStartInvincibility -> {
            generateStartInvincibility(stmt)
            true
        }
        is IREntityDeath -> {
            generateEntityDeath(stmt)
            true
        }
        is IRCombatUpdate -> {
            generateCombatUpdate(stmt)
            true
        }
        is IRDamageOnCollision -> {
            generateDamageOnCollision(stmt)
            true
        }
        else -> false
    }

/**
 * Generate C expression for combat traits queries.
 *
 * @return the C expression string, or null if not a combat traits expression
 */
internal fun GBDKCodeGenerator.generateCombatTraitsExpr(expr: IRExpression): String? =
    when (expr) {
        is IREntityIsInvincible -> "(${expr.entityName}_invincible != 0u)"
        is IREntityIsDead -> "(${expr.entityName}_hp == 0u)"
        is IREntityIsStunned -> "(${expr.entityName}_stun_timer > 0u)"
        is IREntityGetHp -> "${expr.entityName}_hp"
        is IREntityGetMaxHp -> "${expr.entityName}_hp_max"
        is IREntityGetHpPercent ->
            "((UINT16)${expr.entityName}_hp * 100u / ${expr.entityName}_hp_max)"
        is IREntityCanAct -> "(${expr.entityName}_stun_timer == 0u)"
        is IREntityHasKnockback ->
            "(${expr.entityName}_knockback_x != 0 || ${expr.entityName}_knockback_y != 0)"
        is IREntityCollision -> generateEntityCollisionExpr(expr)
        is IRCanDamage -> generateCanDamageExpr(expr)
        else -> null
    }

/** Generate C expression for entity-entity collision check using hitboxes. */
private fun GBDKCodeGenerator.generateEntityCollisionExpr(expr: IREntityCollision): String {
    val e1 = expr.entity1
    val e2 = expr.entity2

    // Find entities to get their hitbox offsets
    val entity1 = game.entities.find { it.name == e1 }
    val entity2 = game.entities.find { it.name == e2 }

    val h1 = entity1?.hitbox
    val h2 = entity2?.hitbox

    // Default to 8x8 hitbox at (0,0) if not specified
    val x1Off = h1?.xOffset ?: 0
    val y1Off = h1?.yOffset ?: 0
    val w1 = h1?.width ?: 8
    val h1Height = h1?.height ?: 8

    val x2Off = h2?.xOffset ?: 0
    val y2Off = h2?.yOffset ?: 0
    val w2 = h2?.width ?: 8
    val h2Height = h2?.height ?: 8

    // AABB collision: left1 < right2 && right1 > left2 && top1 < bottom2 && bottom1 > top2
    return "((${e1}_x + $x1Off) < (${e2}_x + $x2Off + $w2) && " +
        "(${e1}_x + $x1Off + $w1) > (${e2}_x + $x2Off) && " +
        "(${e1}_y + $y1Off) < (${e2}_y + $y2Off + $h2Height) && " +
        "(${e1}_y + $y1Off + $h1Height) > (${e2}_y + $y2Off))"
}

/** Generate C expression for checking if attacker can damage target (different teams). */
private fun GBDKCodeGenerator.generateCanDamageExpr(expr: IRCanDamage): String {
    val attacker = expr.attackerEntity
    val target = expr.targetEntity

    // Get team values - entities with combat have team constants generated
    // Teams: PLAYER=0, ENEMY=1, NEUTRAL=2, HAZARD=3
    // Rules:
    // - Same team (except NEUTRAL and HAZARD): can't damage
    // - HAZARD damages everyone
    // - NEUTRAL can be damaged by anyone
    // - PLAYER can damage ENEMY, ENEMY can damage PLAYER

    val attackerTeam = "${attacker.uppercase()}_TEAM"
    val targetTeam = "${target.uppercase()}_TEAM"

    // Can damage if:
    // 1. Attacker is HAZARD (damages everyone): attackerTeam == TEAM_HAZARD
    // 2. Target is NEUTRAL (can be damaged by anyone): targetTeam == TEAM_NEUTRAL
    // 3. Different teams (not both NEUTRAL): attackerTeam != targetTeam
    return "(($attackerTeam == TEAM_HAZARD) || ($targetTeam == TEAM_NEUTRAL) || " +
        "(($attackerTeam != $targetTeam) && ($attackerTeam != TEAM_NEUTRAL)))"
}

/** Generate combat configuration and state variables. */
private fun GBDKCodeGenerator.generateCombatConfig(config: CombatComponent) {
    val name = config.entityName
    val nameUpper = name.uppercase()

    line("// =============================================================================")
    line("// COMBAT COMPONENT: $name")
    line("// =============================================================================")
    line()

    // Combat constants
    line("// Combat constants")
    line("#define ${nameUpper}_MAX_HP ${config.maxHp}u")
    line("#define ${nameUpper}_ATTACK_POWER ${config.attackPower}u")
    line("#define ${nameUpper}_DEFENSE ${config.defense}u")
    line("#define ${nameUpper}_INVINCIBILITY_FRAMES ${config.invincibilityFrames}u")
    line("#define ${nameUpper}_KNOCKBACK_FORCE ${config.knockbackForce}")
    line("#define ${nameUpper}_STUN_FRAMES ${config.stunFrames}u")
    line("#define ${nameUpper}_TEAM ${config.team.ordinal}u")
    line()

    // Team constants (only once)
    if (game.entities.firstOrNull { it.hasCombat }?.name == name) {
        line("// Combat team constants")
        CombatTeam.entries.forEachIndexed { index, team ->
            line("#define TEAM_${team.name} ${index}u")
        }
        line()
    }

    // State variables
    line("// Combat state variables")
    line("static UINT8 ${name}_hp = ${config.currentHp}u;")
    line("static UINT8 ${name}_hp_max = ${config.maxHp}u;")
    line("static UINT8 ${name}_invincible = 0u;")
    line("static UINT8 ${name}_invincible_timer = 0u;")
    line("static UINT8 ${name}_stun_timer = 0u;")
    line("static INT8 ${name}_knockback_x = 0;")
    line("static INT8 ${name}_knockback_y = 0;")
    line()

    // Generate helper functions
    generateCombatHelpers(config)
}

private fun GBDKCodeGenerator.generateCombatHelpers(config: CombatComponent) {
    val name = config.entityName
    val nameUpper = name.uppercase()

    // Deal damage function
    line("// Deal damage to $name")
    line("static void _${name}_deal_damage(UINT8 damage, INT8 kb_x, INT8 kb_y) {")
    indent++

    line("// Skip if invincible")
    line("if (${name}_invincible) return;")
    line()

    line("// Apply defense reduction")
    line("UINT8 actual_damage = damage;")
    line("if (${nameUpper}_DEFENSE > 0u && actual_damage > ${nameUpper}_DEFENSE) {")
    indent++
    line("actual_damage = damage - ${nameUpper}_DEFENSE;")
    indent--
    line("} else if (${nameUpper}_DEFENSE >= damage) {")
    indent++
    line("actual_damage = 1u; // Minimum 1 damage")
    indent--
    line("}")
    line()

    line("// Apply damage")
    line("if (actual_damage >= ${name}_hp) {")
    indent++
    line("${name}_hp = 0u;")
    // Generate onDeath callback if present
    if (config.onDeathStatements.isNotEmpty()) {
        line("// onDeath callback")
        for (stmt in config.onDeathStatements) {
            generateStatement(stmt)
        }
    }
    indent--
    line("} else {")
    indent++
    line("${name}_hp -= actual_damage;")
    indent--
    line("}")
    line()

    // Generate onHit callback if present
    if (config.onHitStatements.isNotEmpty()) {
        line("// onHit callback")
        for (stmt in config.onHitStatements) {
            generateStatement(stmt)
        }
        line()
    }

    line("// Start invincibility")
    if (config.invincibilityFrames > 0) {
        line("${name}_invincible = 1u;")
        line("${name}_invincible_timer = ${nameUpper}_INVINCIBILITY_FRAMES;")
    }
    line()

    line("// Apply knockback")
    if (config.knockbackForce > 0) {
        line("${name}_knockback_x = kb_x;")
        line("${name}_knockback_y = kb_y;")
    }
    line()

    line("// Apply stun")
    if (config.stunFrames > 0) {
        line("${name}_stun_timer = ${nameUpper}_STUN_FRAMES;")
    }

    indent--
    line("}")
    line()

    // Heal function
    line("// Heal $name")
    line("static void _${name}_heal(UINT8 amount) {")
    indent++
    line("UINT16 new_hp = (UINT16)${name}_hp + amount;")
    line("if (new_hp > ${name}_hp_max) {")
    indent++
    line("${name}_hp = ${name}_hp_max;")
    indent--
    line("} else {")
    indent++
    line("${name}_hp = (UINT8)new_hp;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Combat update function
    line("// Update combat state for $name (call every frame)")
    line("static void _${name}_combat_update(void) {")
    indent++

    line("// Update invincibility timer")
    line("if (${name}_invincible_timer > 0u) {")
    indent++
    line("${name}_invincible_timer--;")

    // Generate blink effect during invincibility
    if (config.blinkOnDamage && config.invincibilityFrames > 0) {
        line("// Blink sprite during invincibility")
        line("if ((${name}_invincible_timer / ${config.blinkInterval}u) & 1u) {")
        indent++
        line("move_sprite(${nameUpper}_OAM_SLOT, 0, 0);  // Hide off-screen")
        indent--
        line("} else {")
        indent++
        line("move_sprite(${nameUpper}_OAM_SLOT, ${name}_x, ${name}_y);  // Restore position")
        indent--
        line("}")
    }

    line("if (${name}_invincible_timer == 0u) {")
    indent++
    line("${name}_invincible = 0u;")
    // Ensure sprite is visible when invincibility ends
    if (config.blinkOnDamage && config.invincibilityFrames > 0) {
        line("move_sprite(${nameUpper}_OAM_SLOT, ${name}_x, ${name}_y);  // Restore position")
    }
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Update stun timer")
    line("if (${name}_stun_timer > 0u) {")
    indent++
    line("${name}_stun_timer--;")
    indent--
    line("}")
    line()

    line("// Apply knockback to position")
    line("if (${name}_knockback_x != 0 || ${name}_knockback_y != 0) {")
    indent++
    line("${name}_x += ${name}_knockback_x;")
    line("${name}_y += ${name}_knockback_y;")
    line("// Decay knockback")
    line("if (${name}_knockback_x > 0) ${name}_knockback_x--;")
    line("else if (${name}_knockback_x < 0) ${name}_knockback_x++;")
    line("if (${name}_knockback_y > 0) ${name}_knockback_y--;")
    line("else if (${name}_knockback_y < 0) ${name}_knockback_y++;")
    indent--
    line("}")

    indent--
    line("}")
    line()
}

private fun GBDKCodeGenerator.generateActionDamage(stmt: IRActionDamage) {
    val target = stmt.targetEntity
    val sourceEntity = stmt.sourceEntity
    val damage =
        if (stmt.damage == 0 && sourceEntity != null) {
            "${sourceEntity.uppercase()}_ATTACK_POWER"
        } else {
            "${stmt.damage}u"
        }

    // Calculate knockback direction
    val (kbX, kbY) =
        when (stmt.knockbackDirection) {
            KnockbackDirection.AWAY -> {
                if (stmt.sourceEntity != null) {
                    // Calculate direction away from source
                    "(${target}_x > ${stmt.sourceEntity}_x ? ${target.uppercase()}_KNOCKBACK_FORCE : -${target.uppercase()}_KNOCKBACK_FORCE)" to
                        "0"
                } else {
                    "${target.uppercase()}_KNOCKBACK_FORCE" to "0"
                }
            }
            KnockbackDirection.TOWARD -> {
                if (stmt.sourceEntity != null) {
                    "(${target}_x < ${stmt.sourceEntity}_x ? ${target.uppercase()}_KNOCKBACK_FORCE : -${target.uppercase()}_KNOCKBACK_FORCE)" to
                        "0"
                } else {
                    "-${target.uppercase()}_KNOCKBACK_FORCE" to "0"
                }
            }
            KnockbackDirection.LEFT -> "-${target.uppercase()}_KNOCKBACK_FORCE" to "0"
            KnockbackDirection.RIGHT -> "${target.uppercase()}_KNOCKBACK_FORCE" to "0"
            KnockbackDirection.UP -> "0" to "-${target.uppercase()}_KNOCKBACK_FORCE"
            KnockbackDirection.DOWN -> "0" to "${target.uppercase()}_KNOCKBACK_FORCE"
            KnockbackDirection.NONE -> "0" to "0"
        }

    line("_${target}_deal_damage($damage, $kbX, $kbY);")
}

private fun GBDKCodeGenerator.generateEntityHeal(stmt: IREntityHeal) {
    line("_${stmt.targetEntity}_heal(${stmt.amount}u);")
}

private fun GBDKCodeGenerator.generateEntityKnockback(stmt: IREntityKnockback) {
    val name = stmt.targetEntity
    line("${name}_knockback_x = ${stmt.forceX};")
    line("${name}_knockback_y = ${stmt.forceY};")
}

private fun GBDKCodeGenerator.generateEntityStun(stmt: IREntityStun) {
    line("${stmt.targetEntity}_stun_timer = ${stmt.frames}u;")
}

private fun GBDKCodeGenerator.generateStartInvincibility(stmt: IRStartInvincibility) {
    val name = stmt.targetEntity
    line("${name}_invincible = 1u;")
    line("${name}_invincible_timer = ${stmt.frames}u;")
}

private fun GBDKCodeGenerator.generateEntityDeath(stmt: IREntityDeath) {
    line("${stmt.targetEntity}_hp = 0u;")
}

private fun GBDKCodeGenerator.generateCombatUpdate(stmt: IRCombatUpdate) {
    line("_${stmt.entityName}_combat_update();")
}

/**
 * Generate code for damage-on-collision. Combines collision check, team check, invincibility check,
 * and damage dealing.
 */
private fun GBDKCodeGenerator.generateDamageOnCollision(stmt: IRDamageOnCollision) {
    val attacker = stmt.attackerEntity
    val target = stmt.targetEntity
    val attackerUpper = attacker.uppercase()
    val targetUpper = target.uppercase()

    // Get hitbox info for collision
    val entity1 = game.entities.find { it.name == attacker }
    val entity2 = game.entities.find { it.name == target }

    val h1 = entity1?.hitbox
    val h2 = entity2?.hitbox

    val x1Off = h1?.xOffset ?: 0
    val y1Off = h1?.yOffset ?: 0
    val w1 = h1?.width ?: 8
    val h1Height = h1?.height ?: 8

    val x2Off = h2?.xOffset ?: 0
    val y2Off = h2?.yOffset ?: 0
    val w2 = h2?.width ?: 8
    val h2Height = h2?.height ?: 8

    // Collision check
    val collisionCheck =
        "(${attacker}_x + $x1Off) < (${target}_x + $x2Off + $w2) && " +
            "(${attacker}_x + $x1Off + $w1) > (${target}_x + $x2Off) && " +
            "(${attacker}_y + $y1Off) < (${target}_y + $y2Off + $h2Height) && " +
            "(${attacker}_y + $y1Off + $h1Height) > (${target}_y + $y2Off)"

    // Build condition with team check if needed
    val condition =
        if (stmt.checkTeam) {
            // Check that entities can damage each other (different teams)
            val teamCheck =
                "(${attackerUpper}_TEAM == TEAM_HAZARD || " +
                    "${targetUpper}_TEAM == TEAM_NEUTRAL || " +
                    "(${attackerUpper}_TEAM != ${targetUpper}_TEAM && ${attackerUpper}_TEAM != TEAM_NEUTRAL))"
            "($collisionCheck) && ($teamCheck)"
        } else {
            collisionCheck
        }

    // Damage amount
    val damage =
        if (stmt.damage == 0) {
            "${attackerUpper}_ATTACK_POWER"
        } else {
            "${stmt.damage}u"
        }

    // Knockback direction (away from attacker)
    val kbX =
        "(${target}_x > ${attacker}_x ? ${targetUpper}_KNOCKBACK_FORCE : -${targetUpper}_KNOCKBACK_FORCE)"
    val kbY = "0"

    // Generate the conditional damage code
    line("// Damage on collision: $attacker -> $target")
    block("if ($condition)") { line("_${target}_deal_damage($damage, $kbX, $kbY);") }
}
