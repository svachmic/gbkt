/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.Easing
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.IRFor
import io.github.gbkt.core.ir.IRIf
import io.github.gbkt.core.ir.IRPoolForEach
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRTween
import io.github.gbkt.core.ir.IRWhile

/**
 * Collect all easing types used in the game's tweens.
 *
 * Scans scenes, pools, and state machines for IRTween statements to optimize which lookup tables
 * are generated.
 */
private fun CodeGenerator.collectUsedEasingTypes(): Set<Easing> {
    val usedEasings = mutableSetOf<Easing>()

    // Scan all scenes for IRTween statements
    for (scene in game.scenes.values) {
        collectEasingsFromStatements(scene.onEnter, usedEasings)
        collectEasingsFromStatements(scene.onFrame, usedEasings)
        collectEasingsFromStatements(scene.onExit, usedEasings)
    }

    // Scan all pools for tweens
    for (pool in game.pools) {
        collectEasingsFromStatements(pool.onFrameStatements, usedEasings)
    }

    // Scan all state machines for tweens
    for (stateMachine in game.stateMachines) {
        for (state in stateMachine.states.values) {
            collectEasingsFromStatements(state.onEnter, usedEasings)
            collectEasingsFromStatements(state.onTick, usedEasings)
            collectEasingsFromStatements(state.onExit, usedEasings)
        }
    }

    // Always include LINEAR as a fallback
    usedEasings.add(Easing.LINEAR)

    return usedEasings
}

/** Recursively collect easing types from a list of IR statements. */
private fun collectEasingsFromStatements(
    statements: List<IRStatement>,
    usedEasings: MutableSet<Easing>,
) {
    for (stmt in statements) {
        when (stmt) {
            is IRTween -> usedEasings.add(stmt.easing)
            is IRIf -> {
                collectEasingsFromStatements(stmt.then, usedEasings)
                stmt.otherwise?.let { collectEasingsFromStatements(it, usedEasings) }
            }
            is IRWhile -> collectEasingsFromStatements(stmt.body, usedEasings)
            is IRFor -> collectEasingsFromStatements(stmt.body, usedEasings)
            is IRPoolForEach -> collectEasingsFromStatements(stmt.bodyStatements, usedEasings)
            else -> {
                /* No nested statements */
            }
        }
    }
}

/**
 * Generate easing lookup tables only for easing functions actually used in the game. Each table
 * contains 256 values (0-255) representing normalized progress.
 *
 * Optimized to avoid generating unused tables (saves ~256 bytes per unused easing).
 */
internal fun CodeGenerator.generateEasingLookupTables() {
    val usedEasings = collectUsedEasingTypes()

    if (usedEasings.isEmpty()) {
        return // No tweens in the game, skip table generation
    }

    line("// =============================================================================")
    line("// EASING LOOKUP TABLES - Pre-computed for performance")
    line(
        "// Only tables for used easing types are generated (${usedEasings.size} of ${Easing.values().size})"
    )
    line("// =============================================================================")
    line()

    // Generate tables only for used easing types
    for (easing in Easing.values()) {
        if (easing in usedEasings) {
            val tableName = "easing_${easing.name.lowercase()}"
            line("static const UINT8 $tableName[256] = {")
            indent++
            for (i in 0..255) {
                val value = computeEasingValue(easing, i / 255.0)
                val comma = if (i < 255) "," else ""
                line("${value}u$comma")
            }
            indent--
            line("};")
            line()
        }
    }
}

/** Compute the eased value for a given progress (0.0 to 1.0). */
private fun computeEasingValue(easing: Easing, t: Double): Int {
    val result =
        when (easing) {
            Easing.LINEAR -> t
            Easing.EASE_IN -> t * t
            Easing.EASE_OUT -> 1.0 - (1.0 - t) * (1.0 - t)
            Easing.EASE_IN_OUT -> if (t < 0.5) 2.0 * t * t else 1.0 - 2.0 * (1.0 - t) * (1.0 - t)
            Easing.EASE_OUT_IN ->
                if (t < 0.5) 0.5 * (1.0 - 2.0 * (1.0 - 2.0 * t) * (1.0 - 2.0 * t))
                else 0.5 + 0.5 * (2.0 * t - 1.0) * (2.0 * t - 1.0)
            Easing.EASE_IN_QUAD -> t * t
            Easing.EASE_OUT_QUAD -> 1.0 - (1.0 - t) * (1.0 - t)
            Easing.EASE_IN_OUT_QUAD ->
                if (t < 0.5) 2.0 * t * t else 1.0 - 2.0 * (1.0 - t) * (1.0 - t)
            Easing.EASE_IN_CUBIC -> t * t * t
            Easing.EASE_OUT_CUBIC -> 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t)
            Easing.EASE_IN_OUT_CUBIC ->
                if (t < 0.5) 4.0 * t * t * t else 1.0 - 4.0 * (1.0 - t) * (1.0 - t) * (1.0 - t)
            Easing.EASE_OUT_BOUNCE -> {
                when {
                    t < 1.0 / 2.75 -> 7.5625 * t * t
                    t < 2.0 / 2.75 -> {
                        val t2 = t - 1.5 / 2.75
                        7.5625 * t2 * t2 + 0.75
                    }
                    t < 2.5 / 2.75 -> {
                        val t2 = t - 2.25 / 2.75
                        7.5625 * t2 * t2 + 0.9375
                    }
                    else -> {
                        val t2 = t - 2.625 / 2.75
                        7.5625 * t2 * t2 + 0.984375
                    }
                }
            }
            Easing.EASE_OUT_ELASTIC -> {
                // Simplified elastic easing using polynomial approximation
                // Avoids kotlin.math functions for multiplatform compatibility
                if (t == 0.0) 0.0
                else if (t == 1.0) 1.0
                else {
                    // Approximate elastic with damped oscillation
                    // amplitude = 2^(-10*t) ≈ exp(-6.93*t), approximated with polynomial
                    val decay = 1.0 - t
                    val amplitude = decay * decay * decay * decay // Quick decay approximation

                    // Oscillation period approximation (2 bounces)
                    val phase =
                        if (t < 0.25) t * 8.0
                        else if (t < 0.5) 2.0 - t * 4.0
                        else if (t < 0.75) t * 4.0 - 2.0 else 4.0 - t * 4.0

                    // Combine for elastic effect with overshoot
                    val overshoot = amplitude * phase * 0.3
                    (1.0 + overshoot).coerceIn(0.0, 1.2) // Allow slight overshoot
                }
            }
        }

    // Clamp to 0-1 range and convert to 0-255
    val clamped = result.coerceIn(0.0, 1.0)
    return (clamped * 255.0).toInt()
}

/** Generate tween update function that processes all active tweens. */
internal fun CodeGenerator.generateTweenUpdateFunction() {
    val usedEasings = collectUsedEasingTypes()

    line("// =============================================================================")
    line("// TWEEN UPDATE FUNCTION")
    line("// =============================================================================")
    line()
    line("void update_tweens(void) {")
    indent++
    line("UINT8 i;")
    line("for (i = 0; i < MAX_TWEENS; i++) {")
    indent++
    line("if (_tween_active[i]) {")
    indent++
    line("_tween_timer[i]++;")
    line()
    line("if (_tween_timer[i] >= _tween_duration[i]) {")
    indent++
    line("// Tween complete - set to final value")
    line("if (_tween_target_type[i] == TWEEN_TYPE_U8) {")
    indent++
    line("// Clamp to U8 range")
    line("INT16 final_val = _tween_to[i];")
    line("if (final_val < 0) final_val = 0;")
    line("if (final_val > 255) final_val = 255;")
    line("*((UINT8*)_tween_target_var[i]) = (UINT8)final_val;")
    indent--
    line("} else {")
    indent++
    line("// U16 can hold any value from INT16")
    line("*((UINT16*)_tween_target_var[i]) = (UINT16)_tween_to[i];")
    indent--
    line("}")
    line("_tween_active[i] = 0;")
    indent--
    line("} else {")
    indent++
    line("// Interpolate using easing lookup table")
    line("UINT8 progress = (_tween_timer[i] * 255u) / _tween_duration[i];")
    line("UINT8 eased;")
    line()
    line("switch (_tween_easing[i]) {")
    indent++
    // Only generate cases for easing types that have generated tables
    for (easing in Easing.values()) {
        if (easing in usedEasings) {
            val tableName = "easing_${easing.name.lowercase()}"
            line("case ${easing.ordinal}:")
            line("    eased = $tableName[progress];")
            line("    break;")
        }
    }
    line("default:")
    line("    // Fallback to LINEAR for any unused easing types")
    line("    eased = easing_linear[progress];")
    line("    break;")
    indent--
    line("}")
    line()
    line("// Lerp: from + (to - from) * eased / 255")
    line("// Use signed arithmetic to support both increasing and decreasing tweens")
    line("INT16 diff = _tween_to[i] - _tween_from[i];")
    line("INT16 delta = (diff * (INT16)eased) / 255;")
    line("INT16 value = _tween_from[i] + delta;")
    line()
    line("// Write to target variable based on type, clamping to valid range")
    line("if (_tween_target_type[i] == TWEEN_TYPE_U8) {")
    indent++
    line("// Clamp to U8 range [0, 255]")
    line("if (value < 0) value = 0;")
    line("if (value > 255) value = 255;")
    line("*((UINT8*)_tween_target_var[i]) = (UINT8)value;")
    indent--
    line("} else {")
    indent++
    line("// U16 can hold full INT16 range when cast")
    line("*((UINT16*)_tween_target_var[i]) = (UINT16)value;")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

/** Generate tween data structures (arrays for active tweens). */
internal fun CodeGenerator.generateTweenData() {
    line("// =============================================================================")
    line("// TWEEN DATA STRUCTURES")
    line("// =============================================================================")
    line()
    line("#define MAX_TWEENS 16")
    line()
    line("// All arrays are zero-initialized to ensure clean state on startup")
    line("static UINT8 _tween_active[MAX_TWEENS] = {0};")
    line("static UINT16 _tween_target_var[MAX_TWEENS] = {0};  // Variable address")
    line("static UINT8 _tween_target_type[MAX_TWEENS] = {0};   // 0=U8, 1=U16")
    line("static INT16 _tween_from[MAX_TWEENS] = {0};  // Signed for negative deltas")
    line("static INT16 _tween_to[MAX_TWEENS] = {0};")
    line("static UINT16 _tween_timer[MAX_TWEENS] = {0};")
    line("static UINT16 _tween_duration[MAX_TWEENS] = {0};")
    line("static UINT8 _tween_easing[MAX_TWEENS] = {0};")
    line()
    line("// Helper to get variable address")
    line("#define GET_VAR_ADDR(var) ((UINT16)&(var))")
    line()
    line("// Variable type constants")
    line("#define TWEEN_TYPE_U8  0")
    line("#define TWEEN_TYPE_U16 1")
    line()
}

/** Generate code for starting a tween. */
internal fun CodeGenerator.generateTweenStart(stmt: IRTween) {
    // Find an available tween slot
    line("// Start tween for ${stmt.target}")
    line("{")
    indent++
    line("UINT8 slot;")
    line("for (slot = 0; slot < MAX_TWEENS; slot++) {")
    indent++
    line("if (!_tween_active[slot]) {")
    indent++
    line("_tween_active[slot] = 1;")
    line("_tween_target_var[slot] = GET_VAR_ADDR(${stmt.target});")
    line(
        "_tween_target_type[slot] = ${if (stmt.targetType == GBVar.VarType.U8) "TWEEN_TYPE_U8" else "TWEEN_TYPE_U16"};"
    )
    line("_tween_from[slot] = (INT16)(${generateExpr(stmt.from)});")
    line("_tween_to[slot] = (INT16)(${generateExpr(stmt.to)});")
    line("_tween_timer[slot] = 0;")
    line("_tween_duration[slot] = ${stmt.duration};")
    line("_tween_easing[slot] = ${stmt.easing.ordinal};")
    line("break;")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
}
