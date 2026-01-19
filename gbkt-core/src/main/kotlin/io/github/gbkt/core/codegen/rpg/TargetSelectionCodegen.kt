/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.rpg

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.ir.IRConfirmTarget
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRGetSelectedTargetCount
import io.github.gbkt.core.ir.IRGetSelectedTargetIndex
import io.github.gbkt.core.ir.IRIsTargetAlive
import io.github.gbkt.core.ir.IRIsTargetEnemy
import io.github.gbkt.core.ir.IRIsTargetSelectionActive
import io.github.gbkt.core.ir.IRMoveTargetCursor
import io.github.gbkt.core.ir.IRSelectAllTargets
import io.github.gbkt.core.ir.IRSelectTarget
import io.github.gbkt.core.ir.IRStartTargetSelection
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRTargetSelectionConfig
import io.github.gbkt.core.ir.IRTargetSelectionTick
import io.github.gbkt.core.rpg.TargetSelectionConfig
import io.github.gbkt.core.rpg.TargetingMode

// =============================================================================
// TARGET SELECTION CODE GENERATION
// =============================================================================

/**
 * Handle target selection IR statements.
 *
 * @return true if this was a target selection statement and was handled, false otherwise
 */
internal fun CodeGenerator.generateTargetSelectionStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRTargetSelectionConfig -> {
            generateTargetSelectionConfig(stmt.config)
            true
        }
        is IRStartTargetSelection -> {
            generateStartTargetSelection(stmt)
            true
        }
        is IRMoveTargetCursor -> {
            generateMoveTargetCursor(stmt)
            true
        }
        is IRSelectTarget -> {
            generateSelectTarget(stmt)
            true
        }
        is IRSelectAllTargets -> {
            generateSelectAllTargets(stmt)
            true
        }
        is IRConfirmTarget -> {
            generateConfirmTarget(stmt)
            true
        }
        is IRTargetSelectionTick -> {
            generateTargetSelectionTick(stmt)
            true
        }
        else -> false
    }

/**
 * Generate C expression for target selection queries.
 *
 * @return the C expression string, or null if not a target selection expression
 */
internal fun CodeGenerator.generateTargetSelectionExpr(expr: IRExpression): String? =
    when (expr) {
        is IRIsTargetSelectionActive -> "_target_${expr.systemName}_active"
        is IRGetSelectedTargetIndex -> "_target_${expr.systemName}_cursor"
        is IRGetSelectedTargetCount -> "_target_${expr.systemName}_count"
        is IRIsTargetAlive -> "_turn_is_alive[${expr.targetIndex}]"
        is IRIsTargetEnemy -> "(!_turn_is_party[${expr.targetIndex}])"
        else -> null
    }

/** Generate target selection configuration and variables. */
private fun CodeGenerator.generateTargetSelectionConfig(config: TargetSelectionConfig) {
    val name = config.name
    val nameUpper = name.uppercase()

    line("// =============================================================================")
    line("// TARGET SELECTION SYSTEM: $name")
    line("// =============================================================================")
    line()

    // Targeting mode constants
    line("// Targeting mode constants")
    TargetingMode.entries.forEachIndexed { index, mode ->
        line("#define TARGET_MODE_${mode.name} ${index}u")
    }
    line()

    // Configuration constants
    line("// Configuration")
    line("#define ${nameUpper}_MAX_TARGETS ${config.maxTargets}u")
    line()

    // State variables
    line("// Target selection state")
    line("static UINT8 _target_${name}_active = 0u;")
    line("static UINT8 _target_${name}_mode = TARGET_MODE_NONE;")
    line("static UINT8 _target_${name}_cursor = 0u;")
    line("static UINT8 _target_${name}_count = 0u;")
    line("static UINT8 _target_${name}_selected[${config.maxTargets}] = {0};")
    line("static UINT8 _target_${name}_valid[${config.maxTargets}] = {0};")
    line("static UINT8 _target_${name}_valid_count = 0u;")
    line()

    // Generate helper functions
    generateTargetSelectionHelpers(config)
}

private fun CodeGenerator.generateTargetSelectionHelpers(config: TargetSelectionConfig) {
    val name = config.name

    // Function to build list of valid targets
    line("// Build list of valid targets based on targeting mode")
    line("static void _target_${name}_build_valid(UINT8 mode) {")
    indent++

    line("_target_${name}_valid_count = 0u;")
    line()

    line("switch (mode) {")
    indent++

    line("case TARGET_MODE_SINGLE_ENEMY:")
    line("case TARGET_MODE_ALL_ENEMIES:")
    indent++
    line("for (UINT8 i = 0u; i < _turn_order_count; i++) {")
    indent++
    line("if (!_turn_is_party[i] && _turn_is_alive[i]) {")
    indent++
    line("_target_${name}_valid[_target_${name}_valid_count++] = i;")
    indent--
    line("}")
    indent--
    line("}")
    line("break;")
    indent--

    line("case TARGET_MODE_SINGLE_ALLY:")
    line("case TARGET_MODE_ALL_ALLIES:")
    indent++
    line("for (UINT8 i = 0u; i < _turn_order_count; i++) {")
    indent++
    line("if (_turn_is_party[i] && _turn_is_alive[i]) {")
    indent++
    line("_target_${name}_valid[_target_${name}_valid_count++] = i;")
    indent--
    line("}")
    indent--
    line("}")
    line("break;")
    indent--

    line("case TARGET_MODE_SELF:")
    indent++
    line("_target_${name}_valid[0] = _turn_current_index;")
    line("_target_${name}_valid_count = 1u;")
    line("break;")
    indent--

    line("default:")
    indent++
    line("break;")
    indent--

    indent--
    line("}")

    indent--
    line("}")
    line()

    // Function to start target selection
    line("// Start target selection")
    line("static void _target_${name}_start(UINT8 mode) {")
    indent++

    line("_target_${name}_mode = mode;")
    line("_target_${name}_build_valid(mode);")
    line()
    line("if (_target_${name}_valid_count == 0u) {")
    indent++
    line("// No valid targets")
    line("_target_${name}_active = 0u;")
    line("return;")
    indent--
    line("}")
    line()
    line("_target_${name}_cursor = 0u;")
    line("_target_${name}_count = 0u;")
    line()
    line("// For ALL modes, auto-select all targets")
    line("if (mode == TARGET_MODE_ALL_ENEMIES || mode == TARGET_MODE_ALL_ALLIES) {")
    indent++
    line("for (UINT8 i = 0u; i < _target_${name}_valid_count; i++) {")
    indent++
    line("_target_${name}_selected[i] = 1u;")
    indent--
    line("}")
    line("_target_${name}_count = _target_${name}_valid_count;")
    indent--
    line("}")
    line()
    line("_target_${name}_active = 1u;")

    indent--
    line("}")
    line()

    // Function to move cursor
    line("// Move target cursor")
    line("static void _target_${name}_move(INT8 dx, INT8 dy) {")
    indent++

    line("if (!_target_${name}_active || _target_${name}_valid_count == 0u) return;")
    line()
    line("INT8 new_cursor = (INT8)_target_${name}_cursor + dx;")
    line("if (new_cursor < 0) new_cursor = _target_${name}_valid_count - 1;")
    line("if (new_cursor >= (INT8)_target_${name}_valid_count) new_cursor = 0;")
    line("_target_${name}_cursor = (UINT8)new_cursor;")

    indent--
    line("}")
    line()

    // Function to toggle select
    line("// Toggle target selection")
    line("static void _target_${name}_toggle(void) {")
    indent++

    line(
        "if (!_target_${name}_active || _target_${name}_cursor >= _target_${name}_valid_count) return;"
    )
    line()
    line("UINT8 idx = _target_${name}_valid[_target_${name}_cursor];")
    line("if (_target_${name}_selected[_target_${name}_cursor]) {")
    indent++
    line("_target_${name}_selected[_target_${name}_cursor] = 0u;")
    line("_target_${name}_count--;")
    indent--
    line("} else {")
    indent++
    if (config.allowMultiSelect) {
        line("_target_${name}_selected[_target_${name}_cursor] = 1u;")
        line("_target_${name}_count++;")
    } else {
        line("// Single select: clear others first")
        line("for (UINT8 i = 0u; i < _target_${name}_valid_count; i++) {")
        indent++
        line("_target_${name}_selected[i] = 0u;")
        indent--
        line("}")
        line("_target_${name}_selected[_target_${name}_cursor] = 1u;")
        line("_target_${name}_count = 1u;")
    }
    indent--
    line("}")

    indent--
    line("}")
    line()

    // Function to select all
    line("// Select all valid targets")
    line("static void _target_${name}_select_all(void) {")
    indent++

    line("for (UINT8 i = 0u; i < _target_${name}_valid_count; i++) {")
    indent++
    line("_target_${name}_selected[i] = 1u;")
    indent--
    line("}")
    line("_target_${name}_count = _target_${name}_valid_count;")

    indent--
    line("}")
    line()

    // Function to confirm selection
    line("// Confirm target selection")
    line("static void _target_${name}_confirm(void) {")
    indent++

    line("if (_target_${name}_count > 0u) {")
    indent++
    line("_target_${name}_active = 0u;")
    line("// Selection confirmed - targets are in _selected array")
    indent--
    line("}")

    indent--
    line("}")
    line()

    // Function to process input
    line("// Process target selection input")
    line("static void _target_${name}_tick(void) {")
    indent++

    line("if (!_target_${name}_active) return;")
    line()
    line("// Navigation")
    line("if (_joypad_pressed & J_LEFT) _target_${name}_move(-1, 0);")
    line("if (_joypad_pressed & J_RIGHT) _target_${name}_move(1, 0);")
    line("if (_joypad_pressed & J_UP) _target_${name}_move(0, -1);")
    line("if (_joypad_pressed & J_DOWN) _target_${name}_move(0, 1);")
    line()
    line("// Selection")
    line("if (_joypad_pressed & J_A) {")
    indent++
    line("_target_${name}_toggle();")
    line("// Auto-confirm for single target modes")
    line("if (_target_${name}_mode == TARGET_MODE_SINGLE_ENEMY ||")
    line("    _target_${name}_mode == TARGET_MODE_SINGLE_ALLY ||")
    line("    _target_${name}_mode == TARGET_MODE_SELF) {")
    indent++
    line("_target_${name}_confirm();")
    indent--
    line("}")
    indent--
    line("}")
    line()
    line("// Cancel")
    line("if (_joypad_pressed & J_B) {")
    indent++
    line("_target_${name}_active = 0u;")
    line("_target_${name}_count = 0u;")
    indent--
    line("}")

    indent--
    line("}")
    line()
}

private fun CodeGenerator.generateStartTargetSelection(stmt: IRStartTargetSelection) {
    line("_target_${stmt.systemName}_start(TARGET_MODE_${stmt.mode.name});")
}

private fun CodeGenerator.generateMoveTargetCursor(stmt: IRMoveTargetCursor) {
    line("_target_${stmt.systemName}_move(${stmt.deltaX}, ${stmt.deltaY});")
}

private fun CodeGenerator.generateSelectTarget(stmt: IRSelectTarget) {
    line("_target_${stmt.systemName}_toggle();")
}

private fun CodeGenerator.generateSelectAllTargets(stmt: IRSelectAllTargets) {
    line("_target_${stmt.systemName}_select_all();")
}

private fun CodeGenerator.generateConfirmTarget(stmt: IRConfirmTarget) {
    line("_target_${stmt.systemName}_confirm();")
}

private fun CodeGenerator.generateTargetSelectionTick(stmt: IRTargetSelectionTick) {
    line("_target_${stmt.systemName}_tick();")
}
