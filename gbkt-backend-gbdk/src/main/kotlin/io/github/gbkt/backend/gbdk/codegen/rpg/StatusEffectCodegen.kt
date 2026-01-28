/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.rpg

import io.github.gbkt.backend.gbdk.codegen.DURATION_PERMANENT
import io.github.gbkt.backend.gbdk.codegen.DURATION_UNTIL_BATTLE_END
import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.SENTINEL_NO_SLOT
import io.github.gbkt.core.ir.IRApplyStatusEffect
import io.github.gbkt.core.ir.IRCanAct
import io.github.gbkt.core.ir.IRClearAllStatusEffects
import io.github.gbkt.core.ir.IRClearStatusEffect
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRHasStatusEffect
import io.github.gbkt.core.ir.IRSkipTurn
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRStatusEffectDuration
import io.github.gbkt.core.ir.IRStatusEffectStacks
import io.github.gbkt.core.ir.IRStatusEffectTick
import io.github.gbkt.core.rpg.EffectDuration
import io.github.gbkt.core.rpg.MAX_ACTIVE_EFFECTS
import io.github.gbkt.core.rpg.StackMode
import io.github.gbkt.core.rpg.TargetRedirectMode

// =============================================================================
// STATUS EFFECT CODE GENERATION
// =============================================================================

/** Generate status effect data tables including multipliers. */
internal fun GBDKCodeGenerator.generateStatusEffectTables() {
    val effects = game.statusEffects
    if (effects.isEmpty()) return

    line("// =============================================================================")
    line("// STATUS EFFECT DATA TABLES")
    line("// =============================================================================")
    line()

    // Effect count constant
    line("#define STATUS_EFFECT_COUNT ${effects.size}u")
    line()

    // Damage multiplier lookup table
    line("// Damage multiplier per effect (100 = normal, 200 = 2x)")
    line("static const UINT8 _effect_damage_mult[${effects.size}] = {")
    indent++
    line(effects.joinToString(", ") { "${it.damageMultiplier}u" })
    indent--
    line("};")
    line()

    // Healing multiplier lookup table (outgoing)
    line("// Healing multiplier per effect (100 = normal, 200 = 2x)")
    line("static const UINT8 _effect_healing_mult[${effects.size}] = {")
    indent++
    line(effects.joinToString(", ") { "${it.healingMultiplier}u" })
    indent--
    line("};")
    line()

    // Incoming damage multiplier lookup table
    line("// Incoming damage multiplier per effect (100 = normal, 50 = halve damage taken)")
    line("static const UINT8 _effect_incoming_damage_mult[${effects.size}] = {")
    indent++
    line(effects.joinToString(", ") { "${it.incomingDamageMultiplier}u" })
    indent--
    line("};")
    line()

    // Incoming healing multiplier lookup table
    line("// Incoming healing multiplier per effect (100 = normal, 200 = 2x healing received)")
    line("static const UINT8 _effect_incoming_healing_mult[${effects.size}] = {")
    indent++
    line(effects.joinToString(", ") { "${it.incomingHealingMultiplier}u" })
    indent--
    line("};")
    line()

    // Hit chance modifier lookup table (signed values: -100 to +100)
    line("// Hit chance modifier per effect (-50 = 50% less likely to hit, +25 = 25% more likely)")
    line("static const INT8 _effect_hit_chance_mod[${effects.size}] = {")
    indent++
    line(effects.joinToString(", ") { "${it.hitChanceModifier}" })
    indent--
    line("};")
    line()

    // Evasion modifier lookup table (signed values: -100 to +100)
    line("// Evasion modifier per effect (+50 = 50% more likely to evade, -25 = 25% easier to hit)")
    line("static const INT8 _effect_evasion_mod[${effects.size}] = {")
    indent++
    line(effects.joinToString(", ") { "${it.evasionModifier}" })
    indent--
    line("};")
    line()

    // Damage per turn lookup table (DoT effects like poison)
    line("// Damage per turn per effect (0 = none, positive = DoT damage)")
    line("static const UINT8 _effect_dot_damage[${effects.size}] = {")
    indent++
    line(effects.joinToString(", ") { "${it.damagePerTurn}u" })
    indent--
    line("};")
    line()

    // Heal per turn lookup table (HoT effects like regen)
    line("// Heal per turn per effect (0 = none, positive = HoT healing)")
    line("static const UINT8 _effect_hot_heal[${effects.size}] = {")
    indent++
    line(effects.joinToString(", ") { "${it.healPerTurn}u" })
    indent--
    line("};")
    line()

    // Prevents action lookup table (stun/sleep effects)
    line("// Whether effect prevents action (1 = yes, 0 = no)")
    line("static const UINT8 _effect_prevents_action[${effects.size}] = {")
    indent++
    line(effects.joinToString(", ") { if (it.preventsAction) "1u" else "0u" })
    indent--
    line("};")
    line()

    // Base duration lookup table
    line("// Base duration per effect (in turns, 255 = permanent)")
    line("static const UINT8 _effect_base_duration[${effects.size}] = {")
    indent++
    line(effects.joinToString(", ") { "${it.durationValue.coerceIn(1, 255)}u" })
    indent--
    line("};")
    line()
}

/**
 * Generate status effect helper functions.
 *
 * These functions use _combatant_effect_id and other combat core variables, so this must be called
 * AFTER generateCombatCoreSystem().
 */
internal fun GBDKCodeGenerator.generateStatusEffectHelpers() {
    val effects = game.statusEffects
    if (effects.isEmpty()) return

    line("// =============================================================================")
    line("// STATUS EFFECT HELPER FUNCTIONS")
    line("// =============================================================================")
    line()

    // Helper function to calculate total damage multiplier for a character
    line("// Get combined damage multiplier for a character (considers all active effects)")
    line("static UINT16 _get_damage_multiplier(UINT8 char_idx) {")
    indent++
    line("UINT16 mult = 100u;")
    line("for (UINT8 _i = 0; _i < $MAX_ACTIVE_EFFECTS; _i++) {")
    indent++
    line("UINT8 effect_id = _combatant_effect_id[char_idx][_i];")
    line("if (effect_id != 0u && effect_id <= STATUS_EFFECT_COUNT) {")
    indent++
    line("mult = mult * _effect_damage_mult[effect_id - 1u] / 100u;")
    indent--
    line("}")
    indent--
    line("}")
    line("return mult;")
    indent--
    line("}")
    line()

    // Helper function to calculate total healing multiplier for a character
    line("// Get combined healing multiplier for a character (considers all active effects)")
    line("static UINT16 _get_healing_multiplier(UINT8 char_idx) {")
    indent++
    line("UINT16 mult = 100u;")
    line("for (UINT8 _i = 0; _i < $MAX_ACTIVE_EFFECTS; _i++) {")
    indent++
    line("UINT8 effect_id = _combatant_effect_id[char_idx][_i];")
    line("if (effect_id != 0u && effect_id <= STATUS_EFFECT_COUNT) {")
    indent++
    line("mult = mult * _effect_healing_mult[effect_id - 1u] / 100u;")
    indent--
    line("}")
    indent--
    line("}")
    line("return mult;")
    indent--
    line("}")
    line()

    // Helper function to calculate incoming damage multiplier for a character
    line(
        "// Get combined incoming damage multiplier for a character (considers all active effects)"
    )
    line("static UINT16 _get_incoming_damage_multiplier(UINT8 char_idx) {")
    indent++
    line("UINT16 mult = 100u;")
    line("for (UINT8 _i = 0; _i < $MAX_ACTIVE_EFFECTS; _i++) {")
    indent++
    line("UINT8 effect_id = _combatant_effect_id[char_idx][_i];")
    line("if (effect_id != 0u && effect_id <= STATUS_EFFECT_COUNT) {")
    indent++
    line("mult = mult * _effect_incoming_damage_mult[effect_id - 1u] / 100u;")
    indent--
    line("}")
    indent--
    line("}")
    line("return mult;")
    indent--
    line("}")
    line()

    // Helper function to calculate incoming healing multiplier for a character
    line(
        "// Get combined incoming healing multiplier for a character (considers all active effects)"
    )
    line("static UINT16 _get_incoming_healing_multiplier(UINT8 char_idx) {")
    indent++
    line("UINT16 mult = 100u;")
    line("for (UINT8 _i = 0; _i < $MAX_ACTIVE_EFFECTS; _i++) {")
    indent++
    line("UINT8 effect_id = _combatant_effect_id[char_idx][_i];")
    line("if (effect_id != 0u && effect_id <= STATUS_EFFECT_COUNT) {")
    indent++
    line("mult = mult * _effect_incoming_healing_mult[effect_id - 1u] / 100u;")
    indent--
    line("}")
    indent--
    line("}")
    line("return mult;")
    indent--
    line("}")
    line()

    // Helper function to calculate hit chance modifier for a character (attacker)
    line("// Get combined hit chance modifier for a character (sum of all active effect modifiers)")
    line("static INT8 _get_hit_chance_modifier(UINT8 char_idx) {")
    indent++
    line("INT8 mod = 0;")
    line("for (UINT8 _i = 0; _i < $MAX_ACTIVE_EFFECTS; _i++) {")
    indent++
    line("UINT8 effect_id = _combatant_effect_id[char_idx][_i];")
    line("if (effect_id != 0u && effect_id <= STATUS_EFFECT_COUNT) {")
    indent++
    line("mod += _effect_hit_chance_mod[effect_id - 1u];")
    indent--
    line("}")
    indent--
    line("}")
    line("// Clamp to valid range")
    line("if (mod < -100) mod = -100;")
    line("if (mod > 100) mod = 100;")
    line("return mod;")
    indent--
    line("}")
    line()

    // Helper function to calculate evasion modifier for a character (defender)
    line("// Get combined evasion modifier for a character (sum of all active effect modifiers)")
    line("static INT8 _get_evasion_modifier(UINT8 char_idx) {")
    indent++
    line("INT8 mod = 0;")
    line("for (UINT8 _i = 0; _i < $MAX_ACTIVE_EFFECTS; _i++) {")
    indent++
    line("UINT8 effect_id = _combatant_effect_id[char_idx][_i];")
    line("if (effect_id != 0u && effect_id <= STATUS_EFFECT_COUNT) {")
    indent++
    line("mod += _effect_evasion_mod[effect_id - 1u];")
    indent--
    line("}")
    indent--
    line("}")
    line("// Clamp to valid range")
    line("if (mod < -100) mod = -100;")
    line("if (mod > 100) mod = 100;")
    line("return mod;")
    indent--
    line("}")
    line()

    // Target redirect mode lookup table
    // Values: 0 = none, 1 = RANDOM_SAME_SIDE, 2 = RANDOM_OPPOSITE_SIDE, 3 = RANDOM_ANY, 4 = SELF
    line("// Target redirect mode per effect (0=none, 1=same_side, 2=opposite_side, 3=any, 4=self)")
    line("static const UINT8 _effect_target_redirect[${effects.size}] = {")
    indent++
    line(
        effects.joinToString(", ") {
            when (it.targetRedirectMode) {
                null -> "0u"
                TargetRedirectMode.RANDOM_SAME_SIDE -> "1u"
                TargetRedirectMode.RANDOM_OPPOSITE_SIDE -> "2u"
                TargetRedirectMode.RANDOM_ANY -> "3u"
                TargetRedirectMode.SELF -> "4u"
            }
        }
    )
    indent--
    line("};")
    line()

    // Constants for redirect modes
    line("#define REDIRECT_NONE 0u")
    line("#define REDIRECT_SAME_SIDE 1u")
    line("#define REDIRECT_OPPOSITE_SIDE 2u")
    line("#define REDIRECT_ANY 3u")
    line("#define REDIRECT_SELF 4u")
    line()

    // Frame-based duration flag lookup table
    // 0 = turn-based (decrement at end of turn), 1 = frame-based (decrement every frame)
    line("// Frame-based duration flag per effect (0=turn-based, 1=frame-based)")
    line("static const UINT8 _effect_is_frame_based[${effects.size}] = {")
    indent++
    line(effects.joinToString(", ") { if (it.isFrameBased) "1u" else "0u" })
    indent--
    line("};")
    line()

    // Helper function to check if effect is frame-based
    line("// Check if an effect uses frame-based duration")
    line("static UINT8 _is_effect_frame_based(UINT8 effect_id) {")
    indent++
    line("if (effect_id == 0u || effect_id > STATUS_EFFECT_COUNT) return 0u;")
    line("return _effect_is_frame_based[effect_id - 1u];")
    indent--
    line("}")
    line()

    // Helper function to get target redirect mode for a character
    line("// Get target redirect mode for a character (returns first non-none redirect found)")
    line("static UINT8 _get_target_redirect_mode(UINT8 char_idx) {")
    indent++
    line("for (UINT8 _i = 0; _i < $MAX_ACTIVE_EFFECTS; _i++) {")
    indent++
    line("UINT8 effect_id = _combatant_effect_id[char_idx][_i];")
    line("if (effect_id != 0u && effect_id <= STATUS_EFFECT_COUNT) {")
    indent++
    line("UINT8 redirect = _effect_target_redirect[effect_id - 1u];")
    line("if (redirect != REDIRECT_NONE) return redirect;")
    indent--
    line("}")
    indent--
    line("}")
    line("return REDIRECT_NONE;")
    indent--
    line("}")
    line()

    // Helper function to apply target redirect
    line("// Apply target redirect based on confusion/charm effects")
    line("// Returns potentially redirected target index")
    line(
        "static UINT8 _apply_target_redirect(UINT8 actor_idx, UINT8 orig_target, UINT8 is_player) {"
    )
    indent++
    line("UINT8 mode = _get_target_redirect_mode(actor_idx);")
    line("if (mode == REDIRECT_NONE) return orig_target;")
    line()
    line("UINT8 roll = (UINT8)(rand() & 0xFFu);")
    line()
    line("switch (mode) {")
    indent++
    line("case REDIRECT_SELF:")
    indent++
    line("return actor_idx;")
    indent--
    line("case REDIRECT_SAME_SIDE:")
    indent++
    line("// Redirect to random ally (including self)")
    line("if (is_player) {")
    indent++
    line("return roll % _party_size;")
    indent--
    line("} else {")
    indent++
    line("return _party_size + (roll % _enemy_count);")
    indent--
    line("}")
    indent--
    line("case REDIRECT_OPPOSITE_SIDE:")
    indent++
    line("// Redirect to random enemy")
    line("if (is_player) {")
    indent++
    line("return _party_size + (roll % _enemy_count);")
    indent--
    line("} else {")
    indent++
    line("return roll % _party_size;")
    indent--
    line("}")
    indent--
    line("case REDIRECT_ANY:")
    indent++
    line("// Redirect to any combatant")
    line("return roll % (_party_size + _enemy_count);")
    indent--
    line("default:")
    indent++
    line("return orig_target;")
    indent--
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate variable declarations for character status effect tracking. */
internal fun GBDKCodeGenerator.generateStatusEffectVariables() {
    if (game.characters.isEmpty()) return

    val charactersWithStats = game.characters.filter { it.hasStats }
    if (charactersWithStats.isEmpty()) return

    line("// === Status Effect Tracking ===")
    for (character in charactersWithStats) {
        val name = character.name
        line("// ${name} status effects (up to $MAX_ACTIVE_EFFECTS active)")
        line("static UINT8 ${name}_effect_id[$MAX_ACTIVE_EFFECTS] = {0};")
        line("static UINT8 ${name}_effect_duration[$MAX_ACTIVE_EFFECTS] = {0};")
        line("static UINT8 ${name}_effect_stacks[$MAX_ACTIVE_EFFECTS] = {0};")
    }
    line()
}

/**
 * Handle status effect-related IR statements.
 *
 * @return true if this was a status effect statement and was handled, false otherwise
 */
internal fun GBDKCodeGenerator.generateStatusEffectStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRApplyStatusEffect -> {
            generateApplyEffect(stmt)
            true
        }
        is IRClearStatusEffect -> {
            generateClearEffect(stmt)
            true
        }
        is IRClearAllStatusEffects -> {
            generateClearAllEffects(stmt)
            true
        }
        is IRStatusEffectTick -> {
            generateEffectTick(stmt)
            true
        }
        is IRSkipTurn -> {
            generateSkipTurn(stmt)
            true
        }
        else -> false
    }

/**
 * Generate C expression for status effect queries.
 *
 * @return the C expression string, or null if not a status effect expression
 */
internal fun GBDKCodeGenerator.generateStatusEffectExpr(expr: IRExpression): String? =
    when (expr) {
        is IRHasStatusEffect -> generateHasEffect(expr)
        is IRStatusEffectStacks -> generateEffectStacks(expr)
        is IRStatusEffectDuration -> generateEffectDuration(expr)
        is IRCanAct -> generateCanAct(expr)
        else -> null
    }

/** Generate C code for applying a status effect. */
private fun GBDKCodeGenerator.generateApplyEffect(stmt: IRApplyStatusEffect) {
    val name = stmt.targetName
    val effectId = stmt.effectId
    val durationValue =
        when (val d = stmt.duration) {
            is EffectDuration.Turns -> d.count
            is EffectDuration.Frames -> d.count // Frame count used directly
            is EffectDuration.UntilBattleEnd -> DURATION_UNTIL_BATTLE_END
            is EffectDuration.Permanent -> DURATION_PERMANENT
        }
    val isFrameBased = stmt.duration is EffectDuration.Frames
    val maxStacks = stmt.maxStacks.coerceIn(1, 99)
    val maxDuration = durationValue * 2 // For STACK_DURATION mode

    lineWithSource(
        "// Apply ${stmt.effectName} to $name (${stmt.stackMode})",
        stmt.sourceLocation,
        name,
    )
    line("{")
    indent++

    // Find existing slot with this effect or first empty slot
    line("UINT8 _slot = ${SENTINEL_NO_SLOT}u;")
    line("UINT8 _existing = ${SENTINEL_NO_SLOT}u;")
    line("for (UINT8 _i = 0; _i < $MAX_ACTIVE_EFFECTS; _i++) {")
    indent++
    line("if (${name}_effect_id[_i] == ${effectId}u) { _existing = _i; break; }")
    line("if (${name}_effect_id[_i] == 0u && _slot == ${SENTINEL_NO_SLOT}u) { _slot = _i; }")
    indent--
    line("}")

    // Handle existing effect based on StackMode
    line("if (_existing != ${SENTINEL_NO_SLOT}u) {")
    indent++

    when (stmt.stackMode) {
        StackMode.REPLACE -> {
            line("// REPLACE: Clear existing, apply fresh")
            line("${name}_effect_duration[_existing] = ${durationValue}u;")
            line("${name}_effect_stacks[_existing] = 1u;")
        }
        StackMode.REFRESH_DURATION -> {
            line("// REFRESH_DURATION: Reset duration, keep stacks")
            line("${name}_effect_duration[_existing] = ${durationValue}u;")
        }
        StackMode.STACK_INTENSITY -> {
            line("// STACK_INTENSITY: Keep duration, increment stacks (max $maxStacks)")
            line("if (${name}_effect_stacks[_existing] < ${maxStacks}u) {")
            indent++
            line("${name}_effect_stacks[_existing]++;")
            indent--
            line("}")
        }
        StackMode.STACK_DURATION -> {
            line("// STACK_DURATION: Add to duration (max ${maxDuration}), keep stacks")
            line("UINT8 _new_dur = ${name}_effect_duration[_existing] + ${durationValue}u;")
            line("if (_new_dur > ${maxDuration}u) _new_dur = ${maxDuration}u;")
            line("${name}_effect_duration[_existing] = _new_dur;")
        }
        StackMode.NONE -> {
            line("// NONE: Effect already active, skip")
        }
    }

    indent--
    line("} else if (_slot != ${SENTINEL_NO_SLOT}u) {")
    indent++
    line("// Apply new effect")
    line("${name}_effect_id[_slot] = ${effectId}u;")
    line("${name}_effect_duration[_slot] = ${durationValue}u;")
    line("${name}_effect_stacks[_slot] = 1u;")
    indent--
    line("}")

    indent--
    line("}")
}

/** Generate C code for clearing a specific status effect. */
private fun GBDKCodeGenerator.generateClearEffect(stmt: IRClearStatusEffect) {
    val name = stmt.targetName
    val effectId = stmt.effectId

    lineWithSource("// Clear ${stmt.effectName} from $name", stmt.sourceLocation, name)
    line("for (UINT8 _i = 0; _i < $MAX_ACTIVE_EFFECTS; _i++) {")
    indent++
    line("if (${name}_effect_id[_i] == ${effectId}u) {")
    indent++
    line("${name}_effect_id[_i] = 0u;")
    line("${name}_effect_duration[_i] = 0u;")
    line("${name}_effect_stacks[_i] = 0u;")
    line("break;")
    indent--
    line("}")
    indent--
    line("}")
}

/** Generate C code for clearing all status effects. */
private fun GBDKCodeGenerator.generateClearAllEffects(stmt: IRClearAllStatusEffects) {
    val name = stmt.targetName

    lineWithSource("// Clear all effects from $name", stmt.sourceLocation, name)
    line("for (UINT8 _i = 0; _i < $MAX_ACTIVE_EFFECTS; _i++) {")
    indent++
    line("${name}_effect_id[_i] = 0u;")
    line("${name}_effect_duration[_i] = 0u;")
    line("${name}_effect_stacks[_i] = 0u;")
    indent--
    line("}")
}

/** Generate C code for processing status effect ticks. */
private fun GBDKCodeGenerator.generateEffectTick(stmt: IRStatusEffectTick) {
    val name = stmt.targetName
    val tickMode = stmt.tickMode

    val modeComment =
        when (tickMode) {
            io.github.gbkt.core.ir.EffectTickMode.TURN -> "turn-based effects"
            io.github.gbkt.core.ir.EffectTickMode.FRAME -> "frame-based effects"
            io.github.gbkt.core.ir.EffectTickMode.ALL -> "all effects"
        }

    lineWithSource("// Tick $modeComment for $name", stmt.sourceLocation, name)
    line("for (UINT8 _i = 0; _i < $MAX_ACTIVE_EFFECTS; _i++) {")
    indent++
    line("UINT8 _eff_id = ${name}_effect_id[_i];")
    line("if (_eff_id != 0u && ${name}_effect_duration[_i] < 254u) {")
    indent++

    // Add tick mode filter
    when (tickMode) {
        io.github.gbkt.core.ir.EffectTickMode.TURN -> {
            line("// Only tick turn-based effects")
            line("if (_is_effect_frame_based(_eff_id)) continue;")
        }
        io.github.gbkt.core.ir.EffectTickMode.FRAME -> {
            line("// Only tick frame-based effects")
            line("if (!_is_effect_frame_based(_eff_id)) continue;")
        }
        io.github.gbkt.core.ir.EffectTickMode.ALL -> {
            // No filter needed
        }
    }

    line("if (${name}_effect_duration[_i] > 0u) {")
    indent++
    line("${name}_effect_duration[_i]--;")
    line("if (${name}_effect_duration[_i] == 0u) {")
    indent++
    line("// Effect expired")
    line("${name}_effect_id[_i] = 0u;")
    line("${name}_effect_stacks[_i] = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
}

/** Generate C expression for checking if character has an effect. */
private fun generateHasEffect(expr: IRHasStatusEffect): String {
    val name = expr.targetName
    val effectId = expr.effectId
    return "_has_effect_${name}_${effectId}()"
}

/** Generate C expression for getting effect stack count. */
private fun generateEffectStacks(expr: IRStatusEffectStacks): String {
    val name = expr.targetName
    val effectId = expr.effectId
    return "_effect_stacks_${name}_${effectId}()"
}

/** Generate C expression for getting effect remaining duration. */
private fun generateEffectDuration(expr: IRStatusEffectDuration): String {
    val name = expr.targetName
    val effectId = expr.effectId
    return "_effect_duration_${name}_${effectId}()"
}

/** Generate C expression for checking if character can act. */
private fun generateCanAct(expr: IRCanAct): String {
    val name = expr.targetName
    return "_can_act_$name()"
}

/** Generate C code for skipping a combatant's turn. */
private fun GBDKCodeGenerator.generateSkipTurn(stmt: IRSkipTurn) {
    val name = stmt.actorName
    lineWithSource("// Skip turn for $name (${stmt.reason})", stmt.sourceLocation, name)
    line("// Turn skipped due to status effect preventing action")
}

/**
 * Generate helper functions for checking if characters can act.
 *
 * These functions check all active status effects for the `preventsAction` flag and return whether
 * the character is able to take an action.
 */
internal fun GBDKCodeGenerator.generateCanActHelpers() {
    val effects = game.statusEffects
    val charactersWithStats = game.characters.filter { it.hasStats }

    if (charactersWithStats.isEmpty()) return

    // Build a set of effect IDs that prevent action
    val preventActionEffectIds = effects.filter { it.preventsAction }.map { it.id.value }.toSet()

    if (preventActionEffectIds.isEmpty()) {
        // No effects prevent action, generate simple always-true helpers
        line("// === Can Act Helpers (no action-preventing effects defined) ===")
        for (character in charactersWithStats) {
            val name = character.name
            line("static UINT8 _can_act_$name(void) { return 1u; }")
        }
        line()
        return
    }

    line("// === Can Act Helpers ===")
    line("// Effect IDs that prevent action: ${preventActionEffectIds.joinToString(", ")}")
    line()

    for (character in charactersWithStats) {
        val name = character.name
        line("// Check if $name can act (not stunned/tripped/etc)")
        line("static UINT8 _can_act_$name(void) {")
        indent++
        line("for (UINT8 _i = 0; _i < $MAX_ACTIVE_EFFECTS; _i++) {")
        indent++
        line("UINT8 _eid = ${name}_effect_id[_i];")
        line("if (_eid != 0u) {")
        indent++
        // Check if this effect prevents action
        line("// Check against action-preventing effects")
        for (effectId in preventActionEffectIds) {
            line("if (_eid == ${effectId}u) return 0u;")
        }
        indent--
        line("}")
        indent--
        line("}")
        line("return 1u;")
        indent--
        line("}")
        line()
    }
}
