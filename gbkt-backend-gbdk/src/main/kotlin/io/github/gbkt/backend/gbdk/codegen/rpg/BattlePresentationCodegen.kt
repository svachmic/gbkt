/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.rpg

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.core.generateStatement
import io.github.gbkt.core.ir.IRBattleMessage
import io.github.gbkt.core.ir.IRBattleSystem
import io.github.gbkt.core.ir.IRShowDamageNumber
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.rpg.BattlePresentationConfig

// =============================================================================
// BATTLE PRESENTATION CODE GENERATION
// =============================================================================

/**
 * Handle battle presentation IR statements.
 *
 * @return true if this was a presentation statement and was handled, false otherwise
 */
internal fun GBDKCodeGenerator.generateBattlePresentationStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRBattleMessage -> {
            generateBattleMessage(stmt)
            true
        }
        is IRShowDamageNumber -> {
            generateShowDamageNumber(stmt)
            true
        }
        else -> false
    }

/** Generate code to show a battle message. */
private fun GBDKCodeGenerator.generateBattleMessage(stmt: IRBattleMessage) {
    val escapedMessage = stmt.message.replace("\"", "\\\"").replace("\n", "\\n")
    lineWithSource("// Show battle message", stmt.sourceLocation, "battle")
    line("_battle_show_message(\"$escapedMessage\");")
}

/** Generate code to show a damage number. */
private fun GBDKCodeGenerator.generateShowDamageNumber(stmt: IRShowDamageNumber) {
    lineWithSource("// Show damage number", stmt.sourceLocation, "battle")
    line(
        "_battle_show_damage_number(${stmt.targetIndex}u, ${stmt.amount}u, ${if (stmt.isCrit) 1 else 0}u, ${if (stmt.isHeal) 1 else 0}u);"
    )
}

/**
 * Generate battle presentation system code.
 *
 * Creates:
 * - Message display variables and functions
 * - Damage number display system
 * - Presentation callbacks
 */
internal fun GBDKCodeGenerator.generateBattlePresentationSystem(system: IRBattleSystem) {
    val name = system.name
    val pres = system.presentation

    // Skip if no presentation features are enabled
    if (!pres.hasAnyFeatures()) return

    line("// =============================================================================")
    line("// BATTLE PRESENTATION: $name")
    line("// =============================================================================")
    line()

    // Generate constants
    generatePresentationConstants(name, pres)

    // Generate variables
    generatePresentationVariables(name, pres)

    // Generate message system
    if (pres.showActionMessages || pres.showCritMessages || pres.showDefeatMessages) {
        generateMessageSystem(name, pres)
    }

    // Generate damage number system
    if (pres.showDamageNumbers) {
        generateDamageNumberSystem(name, pres)
    }

    // Generate death animation system
    if (pres.monsterDeathAnimation) {
        generateDeathAnimationSystem(name, pres)
    }

    // Generate presentation callbacks
    generatePresentationCallbacks(name, pres)

    // Generate presentation update function
    generatePresentationUpdate(name, pres)
}

/** Check if any presentation features are enabled. */
private fun BattlePresentationConfig.hasAnyFeatures(): Boolean =
    showDamageNumbers ||
        hitShakeIntensity > 0 ||
        critShakeIntensity > 0 ||
        flashOnCrit ||
        showActionMessages ||
        showCritMessages ||
        showDefeatMessages ||
        monsterDeathAnimation ||
        onAttack.isNotEmpty() ||
        onDamage.isNotEmpty() ||
        onHeal.isNotEmpty() ||
        onDefeat.isNotEmpty() ||
        onCrit.isNotEmpty() ||
        onMiss.isNotEmpty()

/** Generate presentation constants. */
private fun GBDKCodeGenerator.generatePresentationConstants(
    name: String,
    pres: BattlePresentationConfig,
) {
    val nameUpper = name.uppercase()

    line("// Presentation constants")
    line("#define ${nameUpper}_MSG_DISPLAY_DURATION ${pres.messageDisplayDuration}u")
    if (pres.showDamageNumbers) {
        line("#define ${nameUpper}_DMG_NUM_SPEED ${pres.damageNumberSpeed}u")
        line("#define ${nameUpper}_DMG_NUM_DURATION ${pres.damageNumberDuration}u")
        line("#define ${nameUpper}_MAX_DMG_NUMBERS 8u")
    }
    if (pres.hitShakeIntensity > 0) {
        line("#define ${nameUpper}_HIT_SHAKE_INTENSITY ${pres.hitShakeIntensity}u")
        line("#define ${nameUpper}_HIT_SHAKE_DURATION ${pres.hitShakeDuration}u")
    }
    if (pres.critShakeIntensity > 0) {
        line("#define ${nameUpper}_CRIT_SHAKE_INTENSITY ${pres.critShakeIntensity}u")
    }
    if (pres.flashOnCrit) {
        line("#define ${nameUpper}_CRIT_FLASH_DURATION ${pres.critFlashDuration}u")
    }
    if (pres.monsterDeathAnimation) {
        line("#define ${nameUpper}_DEATH_ANIM_INITIAL_DELAY ${pres.deathAnimationInitialDelay}u")
        line("#define ${nameUpper}_DEATH_ANIM_STEP_DELAY ${pres.deathAnimationStepDelay}u")
        line("#define ${nameUpper}_DEATH_ANIM_STEPS 6u")
    }
    line()
}

/** Generate presentation state variables. */
private fun GBDKCodeGenerator.generatePresentationVariables(
    name: String,
    pres: BattlePresentationConfig,
) {
    line("// Presentation state variables")

    // Message state
    if (pres.showActionMessages || pres.showCritMessages || pres.showDefeatMessages) {
        line("static UINT8 _${name}_msg_active = 0u;")
        line("static UINT8 _${name}_msg_timer = 0u;")
        line("static const char* _${name}_msg_text = \"\";")
    }

    // Damage number state
    if (pres.showDamageNumbers) {
        line("static UINT8 _${name}_dmg_num_count = 0u;")
        line("static UINT8 _${name}_dmg_num_x[${name.uppercase()}_MAX_DMG_NUMBERS];")
        line("static UINT8 _${name}_dmg_num_y[${name.uppercase()}_MAX_DMG_NUMBERS];")
        line("static UINT16 _${name}_dmg_num_value[${name.uppercase()}_MAX_DMG_NUMBERS];")
        line("static UINT8 _${name}_dmg_num_timer[${name.uppercase()}_MAX_DMG_NUMBERS];")
        line(
            "static UINT8 _${name}_dmg_num_flags[${name.uppercase()}_MAX_DMG_NUMBERS]; // bit 0 = crit, bit 1 = heal"
        )
    }

    // Death animation state
    if (pres.monsterDeathAnimation) {
        line("static UINT8 _${name}_death_anim_active = 0u;")
        line("static UINT8 _${name}_death_anim_timer = 0u;")
        line("static UINT8 _${name}_death_anim_step = 0u;")
        line("static UINT8 _${name}_death_anim_target = 0u;")
    }

    line()
}

/** Generate message display system. */
private fun GBDKCodeGenerator.generateMessageSystem(name: String, pres: BattlePresentationConfig) {
    line("// Show battle message")
    line("static void _${name}_show_message(const char* msg) {")
    indent++
    line("_${name}_msg_text = msg;")
    line("_${name}_msg_active = 1u;")
    line("_${name}_msg_timer = ${name.uppercase()}_MSG_DISPLAY_DURATION;")
    line("// Render message at bottom of screen (typical battle message position)")
    line("gotoxy(1u, 16u);")
    line("printf(\"%-18s\", msg);")
    indent--
    line("}")
    line()

    line("// Update message display")
    line("static void _${name}_update_message(void) {")
    indent++
    line("if (!_${name}_msg_active) return;")
    line()
    line("if (_${name}_msg_timer > 0u) {")
    indent++
    line("_${name}_msg_timer--;")
    indent--
    line("} else {")
    indent++
    line("_${name}_msg_active = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate damage number display system. */
private fun GBDKCodeGenerator.generateDamageNumberSystem(
    name: String,
    pres: BattlePresentationConfig,
) {
    val nameUpper = name.uppercase()

    line("// Show floating damage number")
    line(
        "static void _${name}_show_damage_number(UINT8 target_idx, UINT16 amount, UINT8 is_crit, UINT8 is_heal) {"
    )
    indent++
    line("if (_${name}_dmg_num_count >= ${nameUpper}_MAX_DMG_NUMBERS) return;")
    line()
    line("UINT8 idx = _${name}_dmg_num_count;")
    line("_${name}_dmg_num_count++;")
    line()
    line("// Get target position (assumes combatant position arrays exist)")
    line("_${name}_dmg_num_x[idx] = _combatant_x[target_idx];")
    line("_${name}_dmg_num_y[idx] = _combatant_y[target_idx];")
    line("_${name}_dmg_num_value[idx] = amount;")
    line("_${name}_dmg_num_timer[idx] = ${nameUpper}_DMG_NUM_DURATION;")
    line("_${name}_dmg_num_flags[idx] = (is_crit ? 1u : 0u) | (is_heal ? 2u : 0u);")
    indent--
    line("}")
    line()

    line("// Update damage numbers")
    line("static void _${name}_update_damage_numbers(void) {")
    indent++
    line("for (UINT8 i = 0u; i < _${name}_dmg_num_count; ) {")
    indent++
    line("if (_${name}_dmg_num_timer[i] > 0u) {")
    indent++
    line("// Float upward")
    line("if (_${name}_dmg_num_y[i] >= ${nameUpper}_DMG_NUM_SPEED) {")
    indent++
    line("_${name}_dmg_num_y[i] -= ${nameUpper}_DMG_NUM_SPEED;")
    indent--
    line("}")
    line("_${name}_dmg_num_timer[i]--;")
    line("i++;")
    indent--
    line("} else {")
    indent++
    line("// Remove this number by swapping with last")
    line("_${name}_dmg_num_count--;")
    line("if (i < _${name}_dmg_num_count) {")
    indent++
    line("_${name}_dmg_num_x[i] = _${name}_dmg_num_x[_${name}_dmg_num_count];")
    line("_${name}_dmg_num_y[i] = _${name}_dmg_num_y[_${name}_dmg_num_count];")
    line("_${name}_dmg_num_value[i] = _${name}_dmg_num_value[_${name}_dmg_num_count];")
    line("_${name}_dmg_num_timer[i] = _${name}_dmg_num_timer[_${name}_dmg_num_count];")
    line("_${name}_dmg_num_flags[i] = _${name}_dmg_num_flags[_${name}_dmg_num_count];")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()

    line("// Render damage numbers (call after sprites)")
    line("static void _${name}_render_damage_numbers(void) {")
    indent++
    line("for (UINT8 i = 0u; i < _${name}_dmg_num_count; i++) {")
    indent++
    line("// Render damage number as text (Game Boy has limited sprite slots)")
    line("UINT8 x = _${name}_dmg_num_x[i];")
    line("UINT8 y = _${name}_dmg_num_y[i];")
    line("UINT16 value = _${name}_dmg_num_value[i];")
    line("UINT8 flags = _${name}_dmg_num_flags[i];")
    line("// Convert pixel position to tile coordinates")
    line("gotoxy(x >> 3u, y >> 3u);")
    line("if (flags & 2u) {")
    indent++
    line("// Heal numbers - show with + prefix")
    line("printf(\"+%u\", value);")
    indent--
    line("} else if (flags & 1u) {")
    indent++
    line("// Critical hit - show with ! suffix")
    line("printf(\"%u!\", value);")
    indent--
    line("} else {")
    indent++
    line("// Normal damage")
    line("printf(\"%u\", value);")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate death animation system (palette fade-to-white). */
private fun GBDKCodeGenerator.generateDeathAnimationSystem(
    name: String,
    @Suppress("UNUSED_PARAMETER") pres: BattlePresentationConfig,
) {
    val nameUpper = name.uppercase()

    // Death palette color constants (GBC 15-bit RGB format: 0bBBBBBGGGGGRRRRR)
    line("// Death animation palette color constants")
    line("#define DEATH_PAL_WHITE      0x7FFFu  // Pure white (R=31, G=31, B=31)")
    line("#define DEATH_PAL_RED_1      0x4A5Fu  // Red dominant step 1")
    line("#define DEATH_PAL_RED_2      0x535Fu  // Red dominant step 2")
    line("#define DEATH_PAL_RED_3      0x5B7Fu  // Desaturated red")
    line("#define DEATH_PAL_MID_1      0x2987u  // Mid-tone dark")
    line("#define DEATH_PAL_MID_2      0x3C10u  // Mid-tone lighter")
    line("#define DEATH_PAL_MID_3      0x5576u  // Mid-tone near white")
    line("#define DEATH_PAL_DARK_1     0x1108u  // Dark tone")
    line("#define DEATH_PAL_DARK_2     0x1D14u  // Dark tone lighter")
    line("#define DEATH_PAL_LIGHT      0x5EF6u  // Light tone near white")
    line()

    // Death palette data (6-step fade from red-tint to white)
    line("// Death animation palette data (6 steps: red-tint to white)")
    line("static const UINT16 _${name}_death_palette[${nameUpper}_DEATH_ANIM_STEPS][4] = {")
    indent++
    line(
        "{ DEATH_PAL_WHITE, DEATH_PAL_RED_1, DEATH_PAL_MID_1, DEATH_PAL_DARK_1 }, // Step 0: Red dominant"
    )
    line(
        "{ DEATH_PAL_WHITE, DEATH_PAL_RED_2, DEATH_PAL_MID_1, DEATH_PAL_DARK_1 }, // Step 1: Desaturated red"
    )
    line(
        "{ DEATH_PAL_WHITE, DEATH_PAL_RED_3, DEATH_PAL_MID_2, DEATH_PAL_DARK_1 }, // Step 2: Further desaturated"
    )
    line(
        "{ DEATH_PAL_WHITE, DEATH_PAL_WHITE, DEATH_PAL_MID_3, DEATH_PAL_DARK_2 }, // Step 3: Nearly white"
    )
    line(
        "{ DEATH_PAL_WHITE, DEATH_PAL_WHITE, DEATH_PAL_WHITE, DEATH_PAL_LIGHT }, // Step 4: Almost white"
    )
    line(
        "{ DEATH_PAL_WHITE, DEATH_PAL_WHITE, DEATH_PAL_WHITE, DEATH_PAL_WHITE }, // Step 5: Complete white"
    )
    indent--
    line("};")
    line()

    // Start death animation
    line("// Start death animation for a target")
    line("static void _${name}_start_death_animation(UINT8 target_idx) {")
    indent++
    line("_${name}_death_anim_active = 1u;")
    line("_${name}_death_anim_timer = ${nameUpper}_DEATH_ANIM_INITIAL_DELAY;")
    line("_${name}_death_anim_step = 0u;")
    line("_${name}_death_anim_target = target_idx;")
    indent--
    line("}")
    line()

    // Update death animation
    line("// Update death animation (call once per frame)")
    line("static void _${name}_update_death_animation(void) {")
    indent++
    line("if (!_${name}_death_anim_active) return;")
    line()
    line("if (_${name}_death_anim_timer > 0u) {")
    indent++
    line("_${name}_death_anim_timer--;")
    line("return;")
    indent--
    line("}")
    line()
    line("// Apply current palette step")
    line("if (_${name}_death_anim_step < ${nameUpper}_DEATH_ANIM_STEPS) {")
    indent++
    line("// Set sprite palette for dying monster")
    line("set_sprite_palette(")
    indent++
    line("_${name}_death_anim_target & 7u, // Use target index as palette slot")
    line("1u,")
    line("_${name}_death_palette[_${name}_death_anim_step]")
    indent--
    line(");")
    line()
    line("_${name}_death_anim_step++;")
    line("_${name}_death_anim_timer = ${nameUpper}_DEATH_ANIM_STEP_DELAY;")
    indent--
    line("} else {")
    indent++
    line("// Animation complete - hide sprite and reset")
    line("_${name}_death_anim_active = 0u;")
    line("// Hide the defeated monster sprite")
    line("move_sprite(_${name}_death_anim_target, 0u, 0u);")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Check if death animation is in progress
    line("// Check if death animation is currently playing")
    line("static UINT8 _${name}_death_animation_active(void) {")
    indent++
    line("return _${name}_death_anim_active;")
    indent--
    line("}")
    line()
}

/** Generate presentation callback functions. */
private fun GBDKCodeGenerator.generatePresentationCallbacks(
    name: String,
    pres: BattlePresentationConfig,
) {
    // onAttack callback
    if (pres.onAttack.isNotEmpty()) {
        line("// onAttack callback")
        line("static void _${name}_pres_on_attack(void) {")
        indent++
        pres.onAttack.forEach { generateStatement(it) }
        indent--
        line("}")
        line()
    }

    // onDamage callback
    if (pres.onDamage.isNotEmpty()) {
        line("// onDamage callback")
        line("static void _${name}_pres_on_damage(void) {")
        indent++
        pres.onDamage.forEach { generateStatement(it) }
        indent--
        line("}")
        line()
    }

    // onHeal callback
    if (pres.onHeal.isNotEmpty()) {
        line("// onHeal callback")
        line("static void _${name}_pres_on_heal(void) {")
        indent++
        pres.onHeal.forEach { generateStatement(it) }
        indent--
        line("}")
        line()
    }

    // onDefeat callback
    if (pres.onDefeat.isNotEmpty()) {
        line("// onDefeat callback")
        line("static void _${name}_pres_on_defeat(void) {")
        indent++
        pres.onDefeat.forEach { generateStatement(it) }
        indent--
        line("}")
        line()
    }

    // onCrit callback
    if (pres.onCrit.isNotEmpty()) {
        line("// onCrit callback")
        line("static void _${name}_pres_on_crit(void) {")
        indent++
        pres.onCrit.forEach { generateStatement(it) }
        indent--
        line("}")
        line()
    }

    // onMiss callback
    if (pres.onMiss.isNotEmpty()) {
        line("// onMiss callback")
        line("static void _${name}_pres_on_miss(void) {")
        indent++
        pres.onMiss.forEach { generateStatement(it) }
        indent--
        line("}")
        line()
    }
}

/** Generate presentation update function. */
private fun GBDKCodeGenerator.generatePresentationUpdate(
    name: String,
    pres: BattlePresentationConfig,
) {
    line("// Update presentation state (call once per frame during battle)")
    line("static void _${name}_presentation_update(void) {")
    indent++

    if (pres.showActionMessages || pres.showCritMessages || pres.showDefeatMessages) {
        line("_${name}_update_message();")
    }

    if (pres.showDamageNumbers) {
        line("_${name}_update_damage_numbers();")
    }

    if (pres.monsterDeathAnimation) {
        line("_${name}_update_death_animation();")
    }

    indent--
    line("}")
    line()
}

/**
 * Generate presentation hooks for action execution.
 *
 * This integrates with ActionExecutionCodegen to call presentation callbacks at the appropriate
 * times.
 */
internal fun GBDKCodeGenerator.generatePresentationHooks(
    name: String,
    pres: BattlePresentationConfig,
): String {
    val hooks = StringBuilder()

    // Hit shake
    if (pres.hitShakeIntensity > 0) {
        hooks.appendLine("    // Screen shake on hit")
        hooks.appendLine(
            "    _camera_shake(${pres.hitShakeIntensity}u, ${pres.hitShakeDuration}u, 0u);"
        )
    }

    // Crit effects
    if (pres.critShakeIntensity > 0 || pres.flashOnCrit || pres.onCrit.isNotEmpty()) {
        hooks.appendLine("    // Critical hit effects (conditional)")
        hooks.appendLine("    // if (is_crit) { ... }")
    }

    // onDamage callback
    if (pres.onDamage.isNotEmpty()) {
        hooks.appendLine("    _${name}_pres_on_damage();")
    }

    return hooks.toString()
}
