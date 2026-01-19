/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation

// =============================================================================
// MONSTER IR NODES - Monster actions and AI
// =============================================================================

/**
 * Monster performs a basic physical attack.
 *
 * Generates: monster_basic_attack(&monster, target);
 *
 * @param monsterId The monster performing the attack
 * @param targetName The target character name, or null for random target
 */
data class IRMonsterBasicAttack(
    val monsterId: String,
    val targetName: String?,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Monster uses an ability.
 *
 * Generates: monster_use_ability(&monster, ability_id, target);
 */
data class IRMonsterUseAbility(
    val monsterId: String,
    val abilityId: String,
    val targetName: String?,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Monster attempts to flee from battle.
 *
 * Generates: monster_flee(&monster);
 */
data class IRMonsterFlee(
    val monsterId: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Monster defends (reduces incoming damage).
 *
 * Generates: monster_defend(&monster);
 */
data class IRMonsterDefend(
    val monsterId: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Monster skips its turn.
 *
 * Generates: // monster skips turn
 */
data class IRMonsterSkipTurn(
    val monsterId: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Check monster HP percentage condition.
 *
 * Expression: (monster_hp_percent(&monster) < percent) or > percent
 */
data class IRMonsterHpCheck(val monsterId: String, val percent: Int, val below: Boolean) :
    IRExpression

/**
 * Random chance check.
 *
 * Expression: (rand() % 100 < percent)
 */
data class IRRandomChance(val percent: Int) : IRExpression

/**
 * Monster performs a basic attack on a dynamically resolved target.
 *
 * Generates: monster_basic_attack(&monster, target_expression);
 *
 * @param monsterId The monster performing the attack
 * @param targetExpression C expression that evaluates to target index
 */
data class IRMonsterBasicAttackExpr(
    val monsterId: String,
    val targetExpression: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Monster uses an ability on a dynamically resolved target.
 *
 * Generates: monster_use_ability(&monster, ability_id, target_expression);
 */
data class IRMonsterUseAbilityExpr(
    val monsterId: String,
    val abilityId: String,
    val targetExpression: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Check if monster has any alive allies.
 *
 * Expression: (_ai_ally_count > 1)
 */
data class IRMonsterHasAlly(val monsterId: String) : IRExpression

/**
 * Check if enemy count equals a specific value.
 *
 * Expression: (_ai_enemy_count == count)
 */
data class IRAIEnemyCountCheck(val count: Int) : IRExpression

// =============================================================================
// MONSTER EXPRESSION NODES
// =============================================================================

/** Get monster's current HP. */
data class IRMonsterGetHp(val monsterId: String) : IRExpression

/** Get monster's max HP. */
data class IRMonsterGetMaxHp(val monsterId: String) : IRExpression

/** Get monster's HP as percentage. */
data class IRMonsterGetHpPercent(val monsterId: String) : IRExpression

/** Check if monster is alive. */
data class IRMonsterIsAlive(val monsterId: String) : IRExpression

/** Check if monster is defending. */
data class IRMonsterIsDefending(val monsterId: String) : IRExpression

// =============================================================================
// ENCOUNTER IR NODES
// =============================================================================

/**
 * Spawn a monster in battle.
 *
 * Generates: battle_spawn_monster(monster_id, slot);
 */
data class IRSpawnMonster(
    val monsterId: String,
    val slot: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** Check if a monster type exists in current battle. */
data class IRHasMonsterInBattle(val monsterId: String) : IRExpression

/** Get count of alive monsters in battle. */
data class IRAliveMonsterCount(val placeholder: Unit = Unit) : IRExpression

/** Check if all monsters are defeated. */
data class IRAllMonstersDefeated(val placeholder: Unit = Unit) : IRExpression

// =============================================================================
// MONSTER DEATH HOOK IR NODES
// =============================================================================

/**
 * Revive the monster at a percentage of max HP.
 *
 * Generates: _monster_revive(slot, hp_percent);
 *
 * @param monsterId The monster being revived (for context)
 * @param hpPercent Percentage of max HP to restore (1-100)
 */
data class IRMonsterRevive(
    val monsterId: String,
    val hpPercent: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Transform the monster into a different monster type.
 *
 * Generates: _monster_transform(slot, new_monster_id);
 *
 * @param monsterId The monster being transformed (for context)
 * @param newMonsterId The ID of the monster type to transform into
 */
data class IRMonsterTransform(
    val monsterId: String,
    val newMonsterId: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Award bonus experience when monster dies.
 *
 * Generates: _battle_bonus_exp += amount;
 *
 * @param monsterId The dying monster (for context)
 * @param amount Extra experience to award
 */
data class IRMonsterAwardBonusExp(
    val monsterId: String,
    val amount: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Check if this monster was revived during its death hook. Used internally by codegen to determine
 * if monster should be removed.
 *
 * Expression: (_death_hook_revived != 0u)
 */
data class IRMonsterWasRevived(val placeholder: Unit = Unit) : IRExpression

// =============================================================================
// MONSTER HIT HOOK IR NODES
// =============================================================================

/**
 * Cancel the hit entirely (monster evades).
 *
 * Generates: _hit_hook_cancelled = 1u;
 *
 * @param monsterId The monster cancelling the hit (for context)
 */
data class IRMonsterCancelHit(
    val monsterId: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Modify the incoming damage during a hit hook.
 *
 * Generates: _hit_hook_damage = (_hit_hook_damage * multiplier) / 100u;
 *
 * @param monsterId The monster modifying the damage (for context)
 * @param multiplier Damage multiplier percentage (50 = halve, 200 = double)
 */
data class IRMonsterModifyHitDamage(
    val monsterId: String,
    val multiplier: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Decrement an evasion counter for the monster.
 *
 * Generates: if (_monster_evasion[slot] > 0) { _monster_evasion[slot]--; }
 *
 * @param monsterId The monster with the evasion counter (for context)
 */
data class IRMonsterDecrementEvasion(
    val monsterId: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Check if the monster has evasion counter remaining.
 *
 * Expression: (_monster_evasion[slot] > 0)
 */
data class IRMonsterHasEvasion(val monsterId: String) : IRExpression

/**
 * Check if the hit was cancelled during the hit hook. Used internally by codegen to determine if
 * damage should be applied.
 *
 * Expression: (_hit_hook_cancelled != 0u)
 */
data class IRMonsterHitWasCancelled(val placeholder: Unit = Unit) : IRExpression

// =============================================================================
// MONSTER SPECIAL ABILITY IR NODES
// =============================================================================

/**
 * Check if the monster has its special ability charge available.
 *
 * Monsters have a parameter byte that tracks whether their one-time special ability has been used.
 * This expression checks the appropriate bit.
 *
 * Expression: ((_monster_parameter[slot] & 0x80) == 0)
 */
data class IRMonsterHasSpecialCharge(val monsterId: String) : IRExpression

/**
 * Mark that the monster has used its special ability charge.
 *
 * Sets the high bit of the monster's parameter byte to indicate the special ability has been
 * consumed.
 *
 * Generates: _monster_parameter[slot] |= 0x80;
 *
 * @param monsterId The monster consuming its special charge (for context)
 */
data class IRMonsterUseSpecialCharge(
    val monsterId: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Check if the target (player) has a specific status effect active.
 *
 * Used for AI patterns that react to the player's status, such as special finisher attacks on
 * confused targets.
 *
 * Expression: _target_has_effect(target_idx, effect_id)
 *
 * @param monsterId The monster performing the check (for context)
 * @param effectId The ID of the status effect to check for
 */
data class IRMonsterTargetHasEffect(val monsterId: String, val effectId: String) : IRExpression
