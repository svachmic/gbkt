/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.rpg

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.core.generateStatement
import io.github.gbkt.core.ir.IRBattleMenuCancel
import io.github.gbkt.core.ir.IRBattleMenuClose
import io.github.gbkt.core.ir.IRBattleMenuConfig
import io.github.gbkt.core.ir.IRBattleMenuCursor
import io.github.gbkt.core.ir.IRBattleMenuCursorDown
import io.github.gbkt.core.ir.IRBattleMenuCursorUp
import io.github.gbkt.core.ir.IRBattleMenuGetType
import io.github.gbkt.core.ir.IRBattleMenuIsActive
import io.github.gbkt.core.ir.IRBattleMenuIsVisible
import io.github.gbkt.core.ir.IRBattleMenuItemCount
import io.github.gbkt.core.ir.IRBattleMenuOpen
import io.github.gbkt.core.ir.IRBattleMenuSelect
import io.github.gbkt.core.ir.IRBattleMenuSelectedAbility
import io.github.gbkt.core.ir.IRBattleMenuSelectedItem
import io.github.gbkt.core.ir.IRBattleMenuSelectedTarget
import io.github.gbkt.core.ir.IRBattleMenuSetCursor
import io.github.gbkt.core.ir.IRBattleMenuTick
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.rpg.BattleActionType
import io.github.gbkt.core.rpg.BattleMenuDefinition
import io.github.gbkt.core.rpg.BattleMenuSystem
import io.github.gbkt.core.rpg.BattleMenuType

// =============================================================================
// BATTLE MENU CODE GENERATION
// =============================================================================

/**
 * Handle battle menu IR statements.
 *
 * @return true if this was a battle menu statement and was handled, false otherwise
 */
internal fun GBDKCodeGenerator.generateBattleMenuStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRBattleMenuConfig -> {
            generateBattleMenuConfig(stmt.system)
            true
        }
        is IRBattleMenuOpen -> {
            generateBattleMenuOpen(stmt)
            true
        }
        is IRBattleMenuClose -> {
            generateBattleMenuClose(stmt)
            true
        }
        is IRBattleMenuTick -> {
            generateBattleMenuTick(stmt)
            true
        }
        is IRBattleMenuSelect -> {
            generateBattleMenuSelect(stmt)
            true
        }
        is IRBattleMenuCancel -> {
            generateBattleMenuCancel(stmt)
            true
        }
        is IRBattleMenuCursorUp -> {
            line("if (_bmenu_${stmt.systemName}_cursor > 0u) _bmenu_${stmt.systemName}_cursor--;")
            true
        }
        is IRBattleMenuCursorDown -> {
            line("_bmenu_${stmt.systemName}_cursor++;")
            true
        }
        is IRBattleMenuSetCursor -> {
            line("_bmenu_${stmt.systemName}_cursor = ${stmt.index}u;")
            true
        }
        else -> false
    }

/**
 * Generate C expression for battle menu queries.
 *
 * @return the C expression string, or null if not a battle menu expression
 */
internal fun GBDKCodeGenerator.generateBattleMenuExpr(expr: IRExpression): String? =
    when (expr) {
        is IRBattleMenuCursor -> "_bmenu_${expr.systemName}_cursor"
        is IRBattleMenuIsVisible -> "_bmenu_${expr.systemName}_visible"
        is IRBattleMenuIsActive -> "_bmenu_${expr.systemName}_active"
        is IRBattleMenuGetType -> "_bmenu_${expr.systemName}_type"
        is IRBattleMenuSelectedAbility -> "_bmenu_${expr.systemName}_selected_ability"
        is IRBattleMenuSelectedItem -> "_bmenu_${expr.systemName}_selected_item_slot"
        is IRBattleMenuSelectedTarget -> "_bmenu_${expr.systemName}_selected_target"
        is IRBattleMenuItemCount -> "_bmenu_${expr.systemName}_item_count"
        else -> null
    }

/** Generate battle menu configuration and variables. */
private fun GBDKCodeGenerator.generateBattleMenuConfig(system: BattleMenuSystem) {
    val name = system.name
    val nameUpper = name.uppercase()

    line("// =============================================================================")
    line("// BATTLE MENU SYSTEM: $name")
    line("// =============================================================================")
    line()

    // Menu type constants
    line("// Menu type constants")
    BattleMenuType.entries.forEachIndexed { index, type ->
        line("#define BMENU_TYPE_${type.name} ${index}u")
    }
    line()

    // State variables
    line("// Battle menu state variables")
    line("static UINT8 _bmenu_${name}_visible = 0u;")
    line("static UINT8 _bmenu_${name}_active = 0u;")
    line("static UINT8 _bmenu_${name}_type = BMENU_TYPE_MAIN;")
    line("static UINT8 _bmenu_${name}_cursor = 0u;")
    line("static UINT8 _bmenu_${name}_prev_type = BMENU_TYPE_MAIN;")
    line()

    // Main menu config
    val mainMenu = system.mainMenu
    line("// Main menu configuration")
    line("#define ${nameUpper}_MAIN_X ${mainMenu.x}u")
    line("#define ${nameUpper}_MAIN_Y ${mainMenu.y}u")
    line("#define ${nameUpper}_MAIN_W ${mainMenu.width}u")
    line("#define ${nameUpper}_MAIN_H ${mainMenu.height}u")
    line("#define ${nameUpper}_MAIN_COUNT ${mainMenu.commands.size}u")
    line()

    // Command labels
    if (mainMenu.commands.isNotEmpty()) {
        line("// Main menu command labels")
        mainMenu.commands.forEachIndexed { index, cmd ->
            line("static const char* _bmenu_${name}_label_$index = \"${cmd.label}\";")
        }
        line()
    }

    // Submenu configs
    system.abilityMenu?.let { generateSubMenuConfig(name, "ability", it) }
    system.itemMenu?.let { generateSubMenuConfig(name, "item", it) }
    system.targetMenu?.let { generateSubMenuConfig(name, "target", it) }

    // Selected action tracking
    line("// Selected action tracking")
    line("static UINT8 _bmenu_${name}_selected_action = ACTION_TYPE_WAIT;")
    line("static UINT8 _bmenu_${name}_selected_ability = 0u;")
    line("static UINT8 _bmenu_${name}_selected_item_slot = 0u;")
    line("static UINT8 _bmenu_${name}_selected_target = 0u;")
    line()

    // Status display config
    system.statusDisplay?.let { status ->
        line("// Status display configuration")
        line("#define ${nameUpper}_STATUS_X ${status.x}u")
        line("#define ${nameUpper}_STATUS_Y ${status.y}u")
        line("#define ${nameUpper}_STATUS_W ${status.width}u")
        line("#define ${nameUpper}_HP_BAR_W ${status.hpBarWidth}u")
        line()
    }

    // Generate helper functions
    generateBattleMenuDrawFunction(system)
    generateBattleMenuTickFunction(system)
    generateBattleMenuSelectFunction(system)
}

private fun GBDKCodeGenerator.generateSubMenuConfig(
    systemName: String,
    menuType: String,
    menu: BattleMenuDefinition,
) {
    val nameUpper = systemName.uppercase()
    val typeUpper = menuType.uppercase()

    line("// ${menuType.replaceFirstChar { it.uppercase() }} menu configuration")
    line("#define ${nameUpper}_${typeUpper}_X ${menu.x}u")
    line("#define ${nameUpper}_${typeUpper}_Y ${menu.y}u")
    line("#define ${nameUpper}_${typeUpper}_W ${menu.width}u")
    line("#define ${nameUpper}_${typeUpper}_H ${menu.height}u")
    line()
}

private fun GBDKCodeGenerator.generateBattleMenuDrawFunction(system: BattleMenuSystem) {
    val name = system.name
    val nameUpper = name.uppercase()
    val mainMenu = system.mainMenu

    line("// Draw battle menu")
    line("static void _bmenu_${name}_draw(void) {")
    indent++

    line("if (!_bmenu_${name}_visible) return;")
    line()

    // Draw based on current menu type
    line("switch (_bmenu_${name}_type) {")
    indent++

    // Main menu
    line("case BMENU_TYPE_MAIN:")
    indent++
    if (mainMenu.showFrame) {
        line("// Draw frame")
        line(
            "_dialog_draw_box(${nameUpper}_MAIN_X, ${nameUpper}_MAIN_Y, ${nameUpper}_MAIN_W, ${nameUpper}_MAIN_H);"
        )
    }
    line("// Draw commands")
    mainMenu.commands.forEachIndexed { index, _ ->
        line("gotoxy(${nameUpper}_MAIN_X + 2u, ${nameUpper}_MAIN_Y + 1u + ${index}u);")
        line("printf(\"%s\", _bmenu_${name}_label_$index);")
    }
    line("// Draw cursor")
    line("gotoxy(${nameUpper}_MAIN_X + 1u, ${nameUpper}_MAIN_Y + 1u + _bmenu_${name}_cursor);")
    line("printf(\"${mainMenu.cursorChar}\");")
    line("break;")
    indent--

    // Ability menu
    system.abilityMenu?.let { abilityMenu ->
        line("case BMENU_TYPE_ABILITY:")
        indent++
        line("{")
        indent++
        if (abilityMenu.showFrame) {
            line("// Draw ability menu frame")
            line(
                "_dialog_draw_box(${nameUpper}_ABILITY_X, ${nameUpper}_ABILITY_Y, ${nameUpper}_ABILITY_W, ${nameUpper}_ABILITY_H);"
            )
        }
        line("// Draw ability list for current actor")
        line("UINT8 actor_idx = _turn_current_actor;")
        line("UINT8 ability_count = _combatant_ability_count[actor_idx];")
        line("UINT8 visible_count = ${nameUpper}_ABILITY_H - 2u;")
        line("UINT8 scroll_offset = 0u;")
        line("if (_bmenu_${name}_cursor >= visible_count) {")
        indent++
        line("scroll_offset = _bmenu_${name}_cursor - visible_count + 1u;")
        indent--
        line("}")
        line()
        line("for (UINT8 i = 0u; i < visible_count && (i + scroll_offset) < ability_count; i++) {")
        indent++
        line("UINT8 ability_idx = _combatant_abilities[actor_idx][i + scroll_offset];")
        line("gotoxy(${nameUpper}_ABILITY_X + 2u, ${nameUpper}_ABILITY_Y + 1u + i);")
        line("printf(\"%s\", _ability_name[ability_idx]);")
        line()
        line("// Show SP cost")
        line(
            "gotoxy(${nameUpper}_ABILITY_X + ${nameUpper}_ABILITY_W - 4u, ${nameUpper}_ABILITY_Y + 1u + i);"
        )
        line("printf(\"%2u\", _ability_sp_cost[ability_idx]);")
        indent--
        line("}")
        line()
        line("// Draw cursor")
        line("UINT8 cursor_y = _bmenu_${name}_cursor - scroll_offset;")
        line("gotoxy(${nameUpper}_ABILITY_X + 1u, ${nameUpper}_ABILITY_Y + 1u + cursor_y);")
        line("printf(\"${mainMenu.cursorChar}\");")
        indent--
        line("}")
        line("break;")
        indent--
    }

    // Item menu
    system.itemMenu?.let { itemMenu ->
        line("case BMENU_TYPE_ITEM:")
        indent++
        line("{")
        indent++
        if (itemMenu.showFrame) {
            line("// Draw item menu frame")
            line(
                "_dialog_draw_box(${nameUpper}_ITEM_X, ${nameUpper}_ITEM_Y, ${nameUpper}_ITEM_W, ${nameUpper}_ITEM_H);"
            )
        }
        line("// Draw item list from inventory")
        line("UINT8 visible_count = ${nameUpper}_ITEM_H - 2u;")
        line("UINT8 scroll_offset = 0u;")
        line("if (_bmenu_${name}_cursor >= visible_count) {")
        indent++
        line("scroll_offset = _bmenu_${name}_cursor - visible_count + 1u;")
        indent--
        line("}")
        line()
        line("UINT8 displayed = 0u;")
        line(
            "for (UINT8 slot = scroll_offset; slot < _inventory_slot_count && displayed < visible_count; slot++) {"
        )
        indent++
        line("if (_inventory_quantity[slot] > 0u) {")
        indent++
        line("UINT8 item_id = _inventory_item_id[slot];")
        line("gotoxy(${nameUpper}_ITEM_X + 2u, ${nameUpper}_ITEM_Y + 1u + displayed);")
        line("printf(\"%s\", _item_name[item_id]);")
        line()
        line("// Show quantity")
        line(
            "gotoxy(${nameUpper}_ITEM_X + ${nameUpper}_ITEM_W - 4u, ${nameUpper}_ITEM_Y + 1u + displayed);"
        )
        line("printf(\"x%2u\", _inventory_quantity[slot]);")
        line("displayed++;")
        indent--
        line("}")
        indent--
        line("}")
        line()
        line("// Draw cursor")
        line("UINT8 cursor_y = _bmenu_${name}_cursor - scroll_offset;")
        line("gotoxy(${nameUpper}_ITEM_X + 1u, ${nameUpper}_ITEM_Y + 1u + cursor_y);")
        line("printf(\"${mainMenu.cursorChar}\");")
        indent--
        line("}")
        line("break;")
        indent--
    }

    // Target menus
    system.targetMenu?.let { targetMenu ->
        line("case BMENU_TYPE_TARGET_ENEMY:")
        indent++
        line("{")
        indent++
        if (targetMenu.showFrame) {
            line("// Draw target menu frame")
            line(
                "_dialog_draw_box(${nameUpper}_TARGET_X, ${nameUpper}_TARGET_Y, ${nameUpper}_TARGET_W, ${nameUpper}_TARGET_H);"
            )
        }
        line("// Draw list of alive enemies")
        line("UINT8 displayed = 0u;")
        line("for (UINT8 i = 0u; i < _enemy_count; i++) {")
        indent++
        line("if (_combatant_hp[_party_size + i] > 0u) {")
        indent++
        line("gotoxy(${nameUpper}_TARGET_X + 2u, ${nameUpper}_TARGET_Y + 1u + displayed);")
        line("printf(\"%s\", _combatant_name[_party_size + i]);")
        line()
        line("// Cursor indicator")
        line("if (displayed == _bmenu_${name}_cursor) {")
        indent++
        line("gotoxy(${nameUpper}_TARGET_X + 1u, ${nameUpper}_TARGET_Y + 1u + displayed);")
        line("printf(\"${mainMenu.cursorChar}\");")
        indent--
        line("}")
        line("displayed++;")
        indent--
        line("}")
        indent--
        line("}")
        indent--
        line("}")
        line("break;")
        indent--

        line("case BMENU_TYPE_TARGET_ALLY:")
        indent++
        line("{")
        indent++
        if (targetMenu.showFrame) {
            line("// Draw target menu frame")
            line(
                "_dialog_draw_box(${nameUpper}_TARGET_X, ${nameUpper}_TARGET_Y, ${nameUpper}_TARGET_W, ${nameUpper}_TARGET_H);"
            )
        }
        line("// Draw list of alive party members")
        line("UINT8 displayed = 0u;")
        line("for (UINT8 i = 0u; i < _party_size; i++) {")
        indent++
        line("if (_combatant_hp[i] > 0u) {")
        indent++
        line("gotoxy(${nameUpper}_TARGET_X + 2u, ${nameUpper}_TARGET_Y + 1u + displayed);")
        line("printf(\"%s\", _combatant_name[i]);")
        line()
        line("// Show HP")
        line(
            "gotoxy(${nameUpper}_TARGET_X + ${nameUpper}_TARGET_W - 8u, ${nameUpper}_TARGET_Y + 1u + displayed);"
        )
        line("printf(\"%3u/%3u\", _combatant_hp[i], _combatant_hp_max[i]);")
        line()
        line("// Cursor indicator")
        line("if (displayed == _bmenu_${name}_cursor) {")
        indent++
        line("gotoxy(${nameUpper}_TARGET_X + 1u, ${nameUpper}_TARGET_Y + 1u + displayed);")
        line("printf(\"${mainMenu.cursorChar}\");")
        indent--
        line("}")
        line("displayed++;")
        indent--
        line("}")
        indent--
        line("}")
        indent--
        line("}")
        line("break;")
        indent--

        line("case BMENU_TYPE_TARGET_ALL:")
        indent++
        line("{")
        indent++
        line("// Target all - show confirmation")
        if (targetMenu.showFrame) {
            line(
                "_dialog_draw_box(${nameUpper}_TARGET_X, ${nameUpper}_TARGET_Y, ${nameUpper}_TARGET_W, 3u);"
            )
        }
        line("gotoxy(${nameUpper}_TARGET_X + 2u, ${nameUpper}_TARGET_Y + 1u);")
        line("printf(\"Target: ALL\");")
        line("gotoxy(${nameUpper}_TARGET_X + 1u, ${nameUpper}_TARGET_Y + 1u);")
        line("printf(\"${mainMenu.cursorChar}\");")
        indent--
        line("}")
        line("break;")
        indent--
    }

    line("default:")
    indent++
    line("break;")
    indent--

    indent--
    line("}")

    indent--
    line("}")
    line()
}

private fun GBDKCodeGenerator.generateBattleMenuTickFunction(system: BattleMenuSystem) {
    val name = system.name
    val mainMenu = system.mainMenu
    val commandCount = mainMenu.commands.size

    line("// Process battle menu input")
    line("static void _bmenu_${name}_tick(void) {")
    indent++

    line("if (!_bmenu_${name}_active) return;")
    line()

    line("// Handle input based on menu type")
    line("switch (_bmenu_${name}_type) {")
    indent++

    // Main menu input
    line("case BMENU_TYPE_MAIN:")
    indent++
    line("// Navigation")
    line("if (_joypad_pressed & J_UP) {")
    indent++
    line("if (_bmenu_${name}_cursor > 0u) _bmenu_${name}_cursor--;")
    line("else _bmenu_${name}_cursor = ${commandCount - 1}u;")
    indent--
    line("}")
    line("if (_joypad_pressed & J_DOWN) {")
    indent++
    line("if (_bmenu_${name}_cursor < ${commandCount - 1}u) _bmenu_${name}_cursor++;")
    line("else _bmenu_${name}_cursor = 0u;")
    indent--
    line("}")
    line("// Selection")
    line("if (_joypad_pressed & J_A) {")
    indent++
    line("_bmenu_${name}_do_select();")
    indent--
    line("}")
    line("// Cancel")
    line("if (_joypad_pressed & J_B) {")
    indent++
    line("_bmenu_${name}_visible = 0u;")
    line("_bmenu_${name}_active = 0u;")
    indent--
    line("}")
    line("break;")
    indent--

    // Ability menu input
    system.abilityMenu?.let {
        line("case BMENU_TYPE_ABILITY:")
        indent++
        line("{")
        indent++
        line("UINT8 actor_idx = _turn_current_actor;")
        line("UINT8 ability_count = _combatant_ability_count[actor_idx];")
        line()
        line("// Navigation")
        line("if (_joypad_pressed & J_UP) {")
        indent++
        line("if (_bmenu_${name}_cursor > 0u) _bmenu_${name}_cursor--;")
        line("else _bmenu_${name}_cursor = (ability_count > 0u) ? (ability_count - 1u) : 0u;")
        indent--
        line("}")
        line("if (_joypad_pressed & J_DOWN) {")
        indent++
        line("if (_bmenu_${name}_cursor < ability_count - 1u) _bmenu_${name}_cursor++;")
        line("else _bmenu_${name}_cursor = 0u;")
        indent--
        line("}")
        line()
        line("// Selection - select ability and go to target selection")
        line("if (_joypad_pressed & J_A) {")
        indent++
        line("if (ability_count > 0u) {")
        indent++
        line(
            "_bmenu_${name}_selected_ability = _combatant_abilities[actor_idx][_bmenu_${name}_cursor];"
        )
        line("_bmenu_${name}_selected_action = ACTION_TYPE_ABILITY;")
        line("// Get ability target type and go to appropriate target menu")
        line("UINT8 target_type = _ability_target_type[_bmenu_${name}_selected_ability];")
        line("_bmenu_${name}_prev_type = BMENU_TYPE_ABILITY;")
        line("switch (target_type) {")
        indent++
        line("case 0u: // Single enemy")
        indent++
        line("_bmenu_${name}_type = BMENU_TYPE_TARGET_ENEMY;")
        line("break;")
        indent--
        line("case 1u: // Single ally")
        indent++
        line("_bmenu_${name}_type = BMENU_TYPE_TARGET_ALLY;")
        line("break;")
        indent--
        line("case 2u: // All enemies")
        line("case 3u: // All allies")
        indent++
        line("_bmenu_${name}_type = BMENU_TYPE_TARGET_ALL;")
        line("break;")
        indent--
        line("default:")
        indent++
        line("_bmenu_${name}_type = BMENU_TYPE_TARGET_ENEMY;")
        line("break;")
        indent--
        indent--
        line("}")
        line("_bmenu_${name}_cursor = 0u;")
        indent--
        line("}")
        indent--
        line("}")
        line()
        line("// Cancel - return to main menu")
        line("if (_joypad_pressed & J_B) {")
        indent++
        line("_bmenu_${name}_type = BMENU_TYPE_MAIN;")
        line("_bmenu_${name}_cursor = 0u;")
        indent--
        line("}")
        indent--
        line("}")
        line("break;")
        indent--
    }

    // Item menu input
    system.itemMenu?.let {
        line("case BMENU_TYPE_ITEM:")
        indent++
        line("{")
        indent++
        line("// Count usable items")
        line("UINT8 item_count = 0u;")
        line("for (UINT8 slot = 0u; slot < _inventory_slot_count; slot++) {")
        indent++
        line("if (_inventory_quantity[slot] > 0u) item_count++;")
        indent--
        line("}")
        line()
        line("// Navigation")
        line("if (_joypad_pressed & J_UP) {")
        indent++
        line("if (_bmenu_${name}_cursor > 0u) _bmenu_${name}_cursor--;")
        line("else _bmenu_${name}_cursor = (item_count > 0u) ? (item_count - 1u) : 0u;")
        indent--
        line("}")
        line("if (_joypad_pressed & J_DOWN) {")
        indent++
        line("if (_bmenu_${name}_cursor < item_count - 1u) _bmenu_${name}_cursor++;")
        line("else _bmenu_${name}_cursor = 0u;")
        indent--
        line("}")
        line()
        line("// Selection - select item and go to target selection")
        line("if (_joypad_pressed & J_A) {")
        indent++
        line("if (item_count > 0u) {")
        indent++
        line("// Find the actual slot for cursor position")
        line("UINT8 displayed = 0u;")
        line("for (UINT8 slot = 0u; slot < _inventory_slot_count; slot++) {")
        indent++
        line("if (_inventory_quantity[slot] > 0u) {")
        indent++
        line("if (displayed == _bmenu_${name}_cursor) {")
        indent++
        line("_bmenu_${name}_selected_item_slot = slot;")
        line("break;")
        indent--
        line("}")
        line("displayed++;")
        indent--
        line("}")
        indent--
        line("}")
        line("_bmenu_${name}_selected_action = ACTION_TYPE_ITEM;")
        line("_bmenu_${name}_prev_type = BMENU_TYPE_ITEM;")
        line("_bmenu_${name}_type = BMENU_TYPE_TARGET_ALLY; // Items typically target allies")
        line("_bmenu_${name}_cursor = 0u;")
        indent--
        line("}")
        indent--
        line("}")
        line()
        line("// Cancel - return to main menu")
        line("if (_joypad_pressed & J_B) {")
        indent++
        line("_bmenu_${name}_type = BMENU_TYPE_MAIN;")
        line("_bmenu_${name}_cursor = 0u;")
        indent--
        line("}")
        indent--
        line("}")
        line("break;")
        indent--
    }

    // Target menu input
    system.targetMenu?.let {
        line("case BMENU_TYPE_TARGET_ENEMY:")
        indent++
        line("{")
        indent++
        line("// Count alive enemies")
        line("UINT8 enemy_count = 0u;")
        line("for (UINT8 i = 0u; i < _enemy_count; i++) {")
        indent++
        line("if (_combatant_hp[_party_size + i] > 0u) enemy_count++;")
        indent--
        line("}")
        line()
        line("// Navigation")
        line("if (_joypad_pressed & J_UP) {")
        indent++
        line("if (_bmenu_${name}_cursor > 0u) _bmenu_${name}_cursor--;")
        line("else _bmenu_${name}_cursor = (enemy_count > 0u) ? (enemy_count - 1u) : 0u;")
        indent--
        line("}")
        line("if (_joypad_pressed & J_DOWN) {")
        indent++
        line("if (_bmenu_${name}_cursor < enemy_count - 1u) _bmenu_${name}_cursor++;")
        line("else _bmenu_${name}_cursor = 0u;")
        indent--
        line("}")
        line()
        line("// Selection - queue action with target")
        line("if (_joypad_pressed & J_A) {")
        indent++
        line("// Find actual target index")
        line("UINT8 displayed = 0u;")
        line("for (UINT8 i = 0u; i < _enemy_count; i++) {")
        indent++
        line("if (_combatant_hp[_party_size + i] > 0u) {")
        indent++
        line("if (displayed == _bmenu_${name}_cursor) {")
        indent++
        line("_bmenu_${name}_selected_target = _party_size + i;")
        line("break;")
        indent--
        line("}")
        line("displayed++;")
        indent--
        line("}")
        indent--
        line("}")
        line("_bmenu_${name}_do_select(); // This will queue the action")
        indent--
        line("}")
        line()
        line("// Cancel - return to previous menu")
        line("if (_joypad_pressed & J_B) {")
        indent++
        line("_bmenu_${name}_type = _bmenu_${name}_prev_type;")
        line("_bmenu_${name}_cursor = 0u;")
        indent--
        line("}")
        indent--
        line("}")
        line("break;")
        indent--

        line("case BMENU_TYPE_TARGET_ALLY:")
        indent++
        line("{")
        indent++
        line("// Count alive allies")
        line("UINT8 ally_count = 0u;")
        line("for (UINT8 i = 0u; i < _party_size; i++) {")
        indent++
        line("if (_combatant_hp[i] > 0u) ally_count++;")
        indent--
        line("}")
        line()
        line("// Navigation")
        line("if (_joypad_pressed & J_UP) {")
        indent++
        line("if (_bmenu_${name}_cursor > 0u) _bmenu_${name}_cursor--;")
        line("else _bmenu_${name}_cursor = (ally_count > 0u) ? (ally_count - 1u) : 0u;")
        indent--
        line("}")
        line("if (_joypad_pressed & J_DOWN) {")
        indent++
        line("if (_bmenu_${name}_cursor < ally_count - 1u) _bmenu_${name}_cursor++;")
        line("else _bmenu_${name}_cursor = 0u;")
        indent--
        line("}")
        line()
        line("// Selection - queue action with target")
        line("if (_joypad_pressed & J_A) {")
        indent++
        line("// Find actual target index")
        line("UINT8 displayed = 0u;")
        line("for (UINT8 i = 0u; i < _party_size; i++) {")
        indent++
        line("if (_combatant_hp[i] > 0u) {")
        indent++
        line("if (displayed == _bmenu_${name}_cursor) {")
        indent++
        line("_bmenu_${name}_selected_target = i;")
        line("break;")
        indent--
        line("}")
        line("displayed++;")
        indent--
        line("}")
        indent--
        line("}")
        line("_bmenu_${name}_do_select(); // This will queue the action")
        indent--
        line("}")
        line()
        line("// Cancel - return to previous menu")
        line("if (_joypad_pressed & J_B) {")
        indent++
        line("_bmenu_${name}_type = _bmenu_${name}_prev_type;")
        line("_bmenu_${name}_cursor = 0u;")
        indent--
        line("}")
        indent--
        line("}")
        line("break;")
        indent--

        line("case BMENU_TYPE_TARGET_ALL:")
        indent++
        line("{")
        indent++
        line("// Selection - confirm all targets")
        line("if (_joypad_pressed & J_A) {")
        indent++
        line("_bmenu_${name}_selected_target = 255u; // Special value for 'all'")
        line("_bmenu_${name}_do_select();")
        indent--
        line("}")
        line()
        line("// Cancel - return to previous menu")
        line("if (_joypad_pressed & J_B) {")
        indent++
        line("_bmenu_${name}_type = _bmenu_${name}_prev_type;")
        line("_bmenu_${name}_cursor = 0u;")
        indent--
        line("}")
        indent--
        line("}")
        line("break;")
        indent--
    }

    line("default:")
    indent++
    line("break;")
    indent--

    indent--
    line("}")
    line()

    line("// Redraw menu")
    line("_bmenu_${name}_draw();")

    indent--
    line("}")
    line()
}

private fun GBDKCodeGenerator.generateBattleMenuSelectFunction(system: BattleMenuSystem) {
    val name = system.name
    val mainMenu = system.mainMenu

    line("// Handle battle menu selection")
    line("static void _bmenu_${name}_do_select(void) {")
    indent++

    line("switch (_bmenu_${name}_type) {")
    indent++

    line("case BMENU_TYPE_MAIN:")
    indent++
    line("switch (_bmenu_${name}_cursor) {")
    indent++

    mainMenu.commands.forEachIndexed { index, cmd ->
        line("case ${index}u: // ${cmd.label}")
        indent++

        // Generate select action based on command type
        when (cmd.type) {
            BattleActionType.ATTACK -> {
                line("// Attack selected - open target selection")
                line("_bmenu_${name}_selected_action = ACTION_TYPE_ATTACK;")
                if (system.targetMenu != null) {
                    line("_bmenu_${name}_prev_type = BMENU_TYPE_MAIN;")
                    line("_bmenu_${name}_type = BMENU_TYPE_TARGET_ENEMY;")
                    line("_bmenu_${name}_cursor = 0u;")
                }
            }
            BattleActionType.ABILITY -> {
                line("// Ability selected - open ability menu")
                if (system.abilityMenu != null) {
                    line("_bmenu_${name}_type = BMENU_TYPE_ABILITY;")
                    line("_bmenu_${name}_cursor = 0u;")
                }
            }
            BattleActionType.ITEM -> {
                line("// Item selected - open item menu")
                if (system.itemMenu != null) {
                    line("_bmenu_${name}_type = BMENU_TYPE_ITEM;")
                    line("_bmenu_${name}_cursor = 0u;")
                }
            }
            BattleActionType.DEFEND -> {
                line("// Defend selected - queue immediately")
                line("{")
                indent++
                line("UINT8 _targets[] = {_turn_current_actor};")
                line(
                    "_action_battle_add(ACTION_TYPE_DEFEND, _turn_current_actor, 1u, _targets, 0u, 0u);"
                )
                line("_bmenu_${name}_visible = 0u;")
                line("_bmenu_${name}_active = 0u;")
                indent--
                line("}")
            }
            BattleActionType.FLEE -> {
                line("// Flee selected - queue immediately")
                line("{")
                indent++
                line("UINT8 _targets[] = {0u};")
                line(
                    "_action_battle_add(ACTION_TYPE_FLEE, _turn_current_actor, 0u, _targets, 0u, 0u);"
                )
                line("_bmenu_${name}_visible = 0u;")
                line("_bmenu_${name}_active = 0u;")
                indent--
                line("}")
            }
            BattleActionType.WAIT -> {
                line("// Wait selected - queue immediately")
                line("{")
                indent++
                line("UINT8 _targets[] = {0u};")
                line(
                    "_action_battle_add(ACTION_TYPE_WAIT, _turn_current_actor, 0u, _targets, 0u, 0u);"
                )
                line("_bmenu_${name}_visible = 0u;")
                line("_bmenu_${name}_active = 0u;")
                indent--
                line("}")
            }
        }

        // Include custom onSelect statements if any
        if (cmd.onSelect.isNotEmpty()) {
            cmd.onSelect.forEach { generateStatement(it) }
        }

        line("break;")
        indent--
    }

    line("default: break;")

    indent--
    line("}")
    line("break;")
    indent--

    // Target enemy selection - queue attack/ability action
    system.targetMenu?.let {
        line("case BMENU_TYPE_TARGET_ENEMY:")
        line("case BMENU_TYPE_TARGET_ALLY:")
        indent++
        line("{")
        indent++
        line("// Queue action with selected target")
        line("UINT8 _targets[] = {_bmenu_${name}_selected_target};")
        line("switch (_bmenu_${name}_selected_action) {")
        indent++
        line("case ACTION_TYPE_ATTACK:")
        indent++
        line("_action_battle_add(ACTION_TYPE_ATTACK, _turn_current_actor, 1u, _targets, 0u, 0u);")
        line("break;")
        indent--
        line("case ACTION_TYPE_ABILITY:")
        indent++
        line(
            "_action_battle_add(ACTION_TYPE_ABILITY, _turn_current_actor, 1u, _targets, _bmenu_${name}_selected_ability, 0u);"
        )
        line("break;")
        indent--
        line("case ACTION_TYPE_ITEM:")
        indent++
        line("{")
        indent++
        line("UINT8 item_id = _inventory_item_id[_bmenu_${name}_selected_item_slot];")
        line(
            "_action_battle_add(ACTION_TYPE_ITEM, _turn_current_actor, 1u, _targets, 0u, item_id);"
        )
        line("// Consume item")
        line("if (_inventory_quantity[_bmenu_${name}_selected_item_slot] > 0u) {")
        indent++
        line("_inventory_quantity[_bmenu_${name}_selected_item_slot]--;")
        indent--
        line("}")
        indent--
        line("}")
        line("break;")
        indent--
        line("default:")
        indent++
        line("break;")
        indent--
        indent--
        line("}")
        line()
        line("// Close menu after selection")
        line("_bmenu_${name}_visible = 0u;")
        line("_bmenu_${name}_active = 0u;")
        indent--
        line("}")
        line("break;")
        indent--

        line("case BMENU_TYPE_TARGET_ALL:")
        indent++
        line("{")
        indent++
        line("// Queue action targeting all enemies or allies")
        line("switch (_bmenu_${name}_selected_action) {")
        indent++
        line("case ACTION_TYPE_ABILITY:")
        indent++
        line("{")
        indent++
        line("// Determine target type from ability")
        line("UINT8 target_type = _ability_target_type[_bmenu_${name}_selected_ability];")
        line("if (target_type == 2u) { // All enemies")
        indent++
        line("// Add action for each alive enemy")
        line("for (UINT8 i = 0u; i < _enemy_count; i++) {")
        indent++
        line("if (_combatant_hp[_party_size + i] > 0u) {")
        indent++
        line("UINT8 _targets[] = {_party_size + i};")
        line(
            "_action_battle_add(ACTION_TYPE_ABILITY, _turn_current_actor, 1u, _targets, _bmenu_${name}_selected_ability, 0u);"
        )
        indent--
        line("}")
        indent--
        line("}")
        indent--
        line("} else if (target_type == 3u) { // All allies")
        indent++
        line("// Add action for each alive ally")
        line("for (UINT8 i = 0u; i < _party_size; i++) {")
        indent++
        line("if (_combatant_hp[i] > 0u) {")
        indent++
        line("UINT8 _targets[] = {i};")
        line(
            "_action_battle_add(ACTION_TYPE_ABILITY, _turn_current_actor, 1u, _targets, _bmenu_${name}_selected_ability, 0u);"
        )
        indent--
        line("}")
        indent--
        line("}")
        indent--
        line("}")
        indent--
        line("}")
        line("break;")
        indent--
        line("default:")
        indent++
        line("break;")
        indent--
        indent--
        line("}")
        line()
        line("// Close menu after selection")
        line("_bmenu_${name}_visible = 0u;")
        line("_bmenu_${name}_active = 0u;")
        indent--
        line("}")
        line("break;")
        indent--
    }

    line("default:")
    indent++
    line("break;")
    indent--

    indent--
    line("}")

    indent--
    line("}")
    line()
}

/** Generate code for opening a battle menu. */
private fun GBDKCodeGenerator.generateBattleMenuOpen(stmt: IRBattleMenuOpen) {
    val name = stmt.systemName
    line("// Open battle menu: ${stmt.menuType}")
    line("_bmenu_${name}_type = BMENU_TYPE_${stmt.menuType.name};")
    line("_bmenu_${name}_cursor = 0u;")
    line("_bmenu_${name}_visible = 1u;")
    line("_bmenu_${name}_active = 1u;")
    line("_bmenu_${name}_draw();")
}

/** Generate code for closing battle menu. */
private fun GBDKCodeGenerator.generateBattleMenuClose(stmt: IRBattleMenuClose) {
    val name = stmt.systemName
    line("// Close battle menu")
    line("_bmenu_${name}_visible = 0u;")
    line("_bmenu_${name}_active = 0u;")
}

/** Generate code for processing battle menu. */
private fun GBDKCodeGenerator.generateBattleMenuTick(stmt: IRBattleMenuTick) {
    line("_bmenu_${stmt.systemName}_tick();")
}

/** Generate code for selecting in battle menu. */
private fun GBDKCodeGenerator.generateBattleMenuSelect(stmt: IRBattleMenuSelect) {
    line("_bmenu_${stmt.systemName}_do_select();")
}

/** Generate code for canceling in battle menu. */
private fun GBDKCodeGenerator.generateBattleMenuCancel(stmt: IRBattleMenuCancel) {
    val name = stmt.systemName
    line("// Cancel battle menu")
    line("if (_bmenu_${name}_type == BMENU_TYPE_MAIN) {")
    indent++
    line("_bmenu_${name}_visible = 0u;")
    line("_bmenu_${name}_active = 0u;")
    indent--
    line("} else {")
    indent++
    line("_bmenu_${name}_type = BMENU_TYPE_MAIN;")
    line("_bmenu_${name}_cursor = 0u;")
    indent--
    line("}")
}
