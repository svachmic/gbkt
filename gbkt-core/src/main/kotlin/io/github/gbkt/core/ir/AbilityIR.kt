/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.rpg.Aspect

// =============================================================================
// ABILITY IR NODES - Ability execution and effects
// =============================================================================

/**
 * Deal damage to ability targets.
 *
 * Generates: ability_deal_damage(&ability, power, aspect);
 */
data class IRAbilityDealDamage(
    val abilityId: String,
    val power: Int,
    val aspect: Aspect,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Heal ability targets.
 *
 * Generates: ability_heal(&ability, power);
 */
data class IRAbilityHeal(
    val abilityId: String,
    val power: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Apply a status effect to targets.
 *
 * Generates: ability_apply_effect(&ability, effect_id, chance);
 */
data class IRAbilityApplyEffect(
    val abilityId: String,
    val effectId: Int,
    val chance: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Apply a status effect to the caster.
 *
 * Generates: ability_apply_effect_to_caster(&ability, effect_id);
 */
data class IRAbilityApplyEffectToSelf(
    val abilityId: String,
    val effectId: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Drain HP from target and heal caster.
 *
 * Generates: ability_drain(&ability, power, aspect, heal_percent);
 */
data class IRAbilityDrain(
    val abilityId: String,
    val power: Int,
    val aspect: Aspect,
    val healPercent: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Play an animation during ability execution.
 *
 * Generates: ability_play_animation(&ability, "animation_id");
 */
data class IRAbilityPlayAnimation(
    val abilityId: String,
    val animationId: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Play a sound effect during ability execution.
 *
 * Generates: play_sfx(sfx_id);
 */
data class IRAbilityPlaySfx(
    val abilityId: String,
    val sfxId: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Show a message during ability execution.
 *
 * Generates: show_message("message");
 */
data class IRAbilityShowMessage(
    val abilityId: String,
    val message: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Attempt to instantly kill the target.
 *
 * Generates conditional instant kill with immunity check.
 */
data class IRAbilityInstantKill(
    val abilityId: String,
    /** Chance to kill (1-100) */
    val chance: Int,
    /** Whether to ignore instant-kill immunity */
    val ignoreImmunity: Boolean,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Remove all debuffs from target.
 *
 * Generates: ability_cure_debuffs(&ability);
 */
data class IRAbilityCureDebuffs(
    val abilityId: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Restore SP to target.
 *
 * Generates: ability_restore_sp(&ability, amount);
 */
data class IRAbilityRestoreSp(
    val abilityId: String,
    val amount: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Full heal target (HP and SP to max).
 *
 * Generates: ability_full_heal(&ability);
 */
data class IRAbilityFullHeal(
    val abilityId: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

// =============================================================================
// ABILITY USE IR NODES
// =============================================================================

/**
 * Use an ability in battle.
 *
 * Generates: use_ability(caster, ability_id, targets);
 */
data class IRUseAbility(
    val casterName: String,
    val abilityId: String,
    val targetNames: List<String>,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Check if character can use an ability (has enough SP/HP).
 *
 * Expression: can_use_ability(character, ability_id)
 */
data class IRCanUseAbility(val characterName: String, val abilityId: String) : IRExpression

/**
 * Check if character has learned an ability.
 *
 * Expression: has_ability(character, ability_id)
 */
data class IRHasAbility(val characterName: String, val abilityId: String) : IRExpression

/**
 * Get SP cost of an ability.
 *
 * Expression: ability_sp_cost(ability_id)
 */
data class IRGetAbilitySpCost(val abilityId: String) : IRExpression

/**
 * Get power of an ability.
 *
 * Expression: ability_power(ability_id)
 */
data class IRGetAbilityPower(val abilityId: String) : IRExpression

// =============================================================================
// ABILITY UNLOCK IR NODES
// =============================================================================

/**
 * Grant an ability to a character.
 *
 * Sets the corresponding bit in the character's ability flags. Generates: character_ability_flags
 * |= (1 << ability_index);
 */
data class IRGrantAbility(
    val characterName: String,
    val abilityId: String,
    val abilityIndex: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Revoke an ability from a character.
 *
 * Clears the corresponding bit in the character's ability flags. Generates: character_ability_flags
 * &= ~(1 << ability_index);
 */
data class IRRevokeAbility(
    val characterName: String,
    val abilityId: String,
    val abilityIndex: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Check if an ability should be unlocked at a given level.
 *
 * Used during level-up processing to automatically grant abilities. Expression: character_level >=
 * ability_unlock_level
 */
data class IRAbilityUnlockCheck(
    val characterName: String,
    val abilityId: String,
    val unlockLevel: Int,
) : IRExpression
