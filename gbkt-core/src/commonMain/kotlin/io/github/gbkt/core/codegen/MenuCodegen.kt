/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.BorderStyle
import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.ui.MenuDefinition
import io.github.gbkt.core.ui.MenuItem
import io.github.gbkt.core.ui.WrapMode

// =============================================================================
// MENU SYSTEM CODE GENERATION
// =============================================================================

internal fun CodeGenerator.generateMenuData() {
    if (game.menus.isEmpty()) return

    line("// === Menu System ===")
    line()

    // Menu stack for submenus
    line("// Menu navigation stack")
    line("static UINT8 _menu_stack[4];")
    line("static UINT8 _menu_stack_ptr;")
    line("static UINT8 _current_menu;")
    line()

    // Generate state for each menu
    for (menu in game.menus) {
        val name = menu.name
        val style = menu.style
        val gridStyle = menu.gridStyle

        line("// Menu: $name")
        line("static UINT8 _${name}_visible;")
        line("static UINT8 _${name}_active;")

        if (menu.isGrid) {
            // Grid menu uses 2D cursor
            line("static UINT8 _${name}_cursor_x;")
            line("static UINT8 _${name}_cursor_y;")
            line("#define ${name.uppercase()}_COLS ${menu.columns}")
            line("#define ${name.uppercase()}_ROWS ${menu.rows}")
            gridStyle?.let { gs ->
                line("#define ${name.uppercase()}_X ${gs.x}")
                line("#define ${name.uppercase()}_Y ${gs.y}")
                line("#define ${name.uppercase()}_CELL_W ${gs.cellWidth}")
                line("#define ${name.uppercase()}_CELL_H ${gs.cellHeight}")
                line("#define ${name.uppercase()}_PAD ${gs.padding}")
            }
        } else {
            // Vertical menu uses 1D cursor
            line("static UINT8 _${name}_cursor;")
            val itemCount = countMenuItems(menu.items)
            line("#define ${name.uppercase()}_ITEM_COUNT $itemCount")
            line("#define ${name.uppercase()}_X ${style.x}")
            line("#define ${name.uppercase()}_Y ${style.y}")
            line("#define ${name.uppercase()}_W ${style.width}")
            line("#define ${name.uppercase()}_SPACING ${style.spacing}")
            line("#define ${name.uppercase()}_CURSOR '${style.cursorChar}'")
        }
        line()
    }

    // Generate menu helper functions
    generateMenuHelperFunctions()
}

internal fun CodeGenerator.countMenuItems(items: List<MenuItem>): Int {
    var count = 0
    for (item in items) {
        when (item) {
            is MenuItem.Action -> count++
            is MenuItem.Toggle -> count++
            is MenuItem.Slider -> count++
            is MenuItem.Option -> count++
            is MenuItem.Separator -> {} // Don't count separators
            is MenuItem.Conditional -> count += countMenuItems(item.items)
        }
    }
    return count
}

private fun CodeGenerator.generateMenuHelperFunctions() {
    // Draw a menu item with optional cursor
    block(
        "static void _menu_draw_item(UINT8 x, UINT8 y, const char *label, UINT8 selected, UINT8 cursor_char)"
    ) {
        line("gotoxy(x, y);")
        line("if (selected) {")
        indent++
        line("printf(\"%c\", cursor_char);")
        indent--
        line("} else {")
        indent++
        line("printf(\" \");")
        indent--
        line("}")
        line("printf(\"%s\", label);")
    }
    line()

    // Draw toggle value
    block(
        "static void _menu_draw_toggle(UINT8 x, UINT8 y, UINT8 value, const char *on_label, const char *off_label)"
    ) {
        line("gotoxy(x, y);")
        line("if (value) {")
        indent++
        line("printf(\"<%s>\", on_label);")
        indent--
        line("} else {")
        indent++
        line("printf(\"<%s>\", off_label);")
        indent--
        line("}")
    }
    line()

    // Draw slider value
    block("static void _menu_draw_slider(UINT8 x, UINT8 y, UINT8 value, UINT8 max_val)") {
        line("UINT8 i;")
        line("gotoxy(x, y);")
        line("printf(\"<\");")
        line("for (i = 0; i < max_val; i++) {")
        indent++
        line("printf(\"%c\", (i < value) ? '#' : '-');")
        indent--
        line("}")
        line("printf(\">\");")
    }
    line()

    // Draw option value
    block("static void _menu_draw_option(UINT8 x, UINT8 y, const char *value)") {
        line("gotoxy(x, y);")
        line("printf(\"<%s>\", value);")
    }
    line()

    // Generate draw functions for each vertical menu
    for (menu in game.menus.filter { !it.isGrid }) {
        generateMenuDrawFunction(menu)
    }

    // Generate draw functions for each grid menu
    for (menu in game.menus.filter { it.isGrid }) {
        generateGridMenuDrawFunction(menu)
    }

    // Generate selection helper functions for each menu
    for (menu in game.menus) {
        if (menu.isGrid) {
            generateGridMenuSelectFunction(menu)
        } else {
            generateMenuSelectFunction(menu)
        }
    }

    // Generate tick functions for each menu
    for (menu in game.menus) {
        if (menu.isGrid) {
            generateGridMenuTickFunction(menu)
        } else {
            generateMenuTickFunction(menu)
        }
    }
}

private fun CodeGenerator.generateMenuDrawFunction(menu: MenuDefinition) {
    val name = menu.name
    val style = menu.style

    block("static void _${name}_draw(void)") {
        line("UINT8 y = ${name.uppercase()}_Y;")

        // Draw border if configured
        if (style.border != BorderStyle.NONE) {
            val height = countMenuItems(menu.items) * style.spacing + 2
            line(
                "_dialog_draw_box(${name.uppercase()}_X - 1, ${name.uppercase()}_Y - 1, ${name.uppercase()}_W + 2, $height);"
            )
        }

        // Draw title if present
        menu.title?.let { title ->
            line("gotoxy(${name.uppercase()}_X, y);")
            line("printf(\"$title\");")
            line("y += 2;")
        }

        // Draw items
        var itemIndex = 0
        for (item in menu.items) {
            when (item) {
                is MenuItem.Action -> {
                    line(
                        "_menu_draw_item(${name.uppercase()}_X, y, \"${item.label}\", _${name}_cursor == $itemIndex, ${name.uppercase()}_CURSOR);"
                    )
                    line("y += ${name.uppercase()}_SPACING;")
                    itemIndex++
                }
                is MenuItem.Toggle -> {
                    line(
                        "_menu_draw_item(${name.uppercase()}_X, y, \"${item.label}\", _${name}_cursor == $itemIndex, ${name.uppercase()}_CURSOR);"
                    )
                    line(
                        "_menu_draw_toggle(${name.uppercase()}_X + ${style.labelWidth}, y, ${item.variable}, \"${item.onLabel}\", \"${item.offLabel}\");"
                    )
                    line("y += ${name.uppercase()}_SPACING;")
                    itemIndex++
                }
                is MenuItem.Slider -> {
                    line(
                        "_menu_draw_item(${name.uppercase()}_X, y, \"${item.label}\", _${name}_cursor == $itemIndex, ${name.uppercase()}_CURSOR);"
                    )
                    line(
                        "_menu_draw_slider(${name.uppercase()}_X + ${style.labelWidth}, y, ${item.variable}, ${item.max});"
                    )
                    line("y += ${name.uppercase()}_SPACING;")
                    itemIndex++
                }
                is MenuItem.Option -> {
                    line(
                        "_menu_draw_item(${name.uppercase()}_X, y, \"${item.label}\", _${name}_cursor == $itemIndex, ${name.uppercase()}_CURSOR);"
                    )
                    // Generate option choices array and display
                    val choicesArrayName = "_${name}_${item.variable}_choices"
                    line(
                        "_menu_draw_option(${name.uppercase()}_X + ${style.labelWidth}, y, $choicesArrayName[${item.variable}]);"
                    )
                    line("y += ${name.uppercase()}_SPACING;")
                    itemIndex++
                }
                is MenuItem.Separator -> {
                    line("y += 1;")
                }
                is MenuItem.Conditional -> {
                    // Generate conditional item display - items only shown when condition is true
                    val condExpr = generateExpr(item.condition)
                    line("if ($condExpr) {")
                    indent++
                    // Recursively draw conditional items
                    for (subItem in item.items) {
                        when (subItem) {
                            is MenuItem.Action -> {
                                line(
                                    "_menu_draw_item(${name.uppercase()}_X, y, \"${subItem.label}\", _${name}_cursor == $itemIndex, ${name.uppercase()}_CURSOR);"
                                )
                                line("y += ${name.uppercase()}_SPACING;")
                                itemIndex++
                            }
                            is MenuItem.Toggle -> {
                                line(
                                    "_menu_draw_item(${name.uppercase()}_X, y, \"${subItem.label}\", _${name}_cursor == $itemIndex, ${name.uppercase()}_CURSOR);"
                                )
                                line(
                                    "_menu_draw_toggle(${name.uppercase()}_X + ${style.labelWidth}, y, ${subItem.variable}, \"${subItem.onLabel}\", \"${subItem.offLabel}\");"
                                )
                                line("y += ${name.uppercase()}_SPACING;")
                                itemIndex++
                            }
                            is MenuItem.Slider -> {
                                line(
                                    "_menu_draw_item(${name.uppercase()}_X, y, \"${subItem.label}\", _${name}_cursor == $itemIndex, ${name.uppercase()}_CURSOR);"
                                )
                                line(
                                    "_menu_draw_slider(${name.uppercase()}_X + ${style.labelWidth}, y, ${subItem.variable}, ${subItem.max});"
                                )
                                line("y += ${name.uppercase()}_SPACING;")
                                itemIndex++
                            }
                            is MenuItem.Option -> {
                                line(
                                    "_menu_draw_item(${name.uppercase()}_X, y, \"${subItem.label}\", _${name}_cursor == $itemIndex, ${name.uppercase()}_CURSOR);"
                                )
                                val choicesArrayName = "_${name}_${subItem.variable}_choices"
                                line(
                                    "_menu_draw_option(${name.uppercase()}_X + ${style.labelWidth}, y, $choicesArrayName[${subItem.variable}]);"
                                )
                                line("y += ${name.uppercase()}_SPACING;")
                                itemIndex++
                            }
                            is MenuItem.Separator -> {
                                line("y += 1;")
                            }
                            is MenuItem.Conditional -> {
                                // Nested conditionals not supported for simplicity
                            }
                        }
                    }
                    indent--
                    line("}")
                }
            }
        }
    }
    line()
}

private fun CodeGenerator.generateGridMenuDrawFunction(menu: MenuDefinition) {
    val name = menu.name
    val gs = menu.gridStyle ?: return

    block("static void _${name}_draw(void)") {
        line("UINT8 x, y, i;")

        // Draw border if configured
        if (gs.border != BorderStyle.NONE) {
            val width = menu.columns * (gs.cellWidth + gs.padding) + 2
            val height = menu.rows * (gs.cellHeight + gs.padding) + 2
            line(
                "_dialog_draw_box(${name.uppercase()}_X - 1, ${name.uppercase()}_Y - 1, $width, $height);"
            )
        }

        // Draw grid cells
        line("for (y = 0; y < ${name.uppercase()}_ROWS; y++) {")
        indent++
        line("for (x = 0; x < ${name.uppercase()}_COLS; x++) {")
        indent++
        line(
            "UINT8 px = ${name.uppercase()}_X + x * (${name.uppercase()}_CELL_W + ${name.uppercase()}_PAD);"
        )
        line(
            "UINT8 py = ${name.uppercase()}_Y + y * (${name.uppercase()}_CELL_H + ${name.uppercase()}_PAD);"
        )
        line("gotoxy(px, py);")

        // Draw cursor
        line("if (x == _${name}_cursor_x && y == _${name}_cursor_y) {")
        indent++
        line("printf(\"${gs.cursorChar}\");")
        indent--
        line("} else {")
        indent++
        line("printf(\" \");")
        indent--
        line("}")

        indent--
        line("}")
        indent--
        line("}")
    }
    line()
}

/**
 * Generate a helper function that executes the selection action for a vertical menu item. This
 * function can be called from the tick function (A button) or programmatically via IRMenuSelect.
 */
private fun CodeGenerator.generateMenuSelectFunction(menu: MenuDefinition) {
    val name = menu.name

    block("static void _${name}_do_select(UINT8 item)") {
        line("switch (item) {")
        indent++

        var itemIndex = 0
        for (item in menu.items) {
            when (item) {
                is MenuItem.Action -> {
                    line("case $itemIndex:")
                    indent++
                    for (stmt in item.onSelect) {
                        generateStatement(stmt)
                    }
                    line("break;")
                    indent--
                    itemIndex++
                }
                is MenuItem.Toggle -> {
                    line("case $itemIndex:")
                    indent++
                    line("${item.variable} = !${item.variable};")
                    for (stmt in item.onChange) {
                        generateStatement(stmt)
                    }
                    line("_${name}_draw();")
                    line("break;")
                    indent--
                    itemIndex++
                }
                is MenuItem.Slider,
                is MenuItem.Option -> {
                    // These are handled by left/right, not A button
                    itemIndex++
                }
                is MenuItem.Separator -> {}
                is MenuItem.Conditional -> {
                    // Handle conditional menu item selection
                    val condExpr = generateExpr(item.condition)
                    for (subItem in item.items) {
                        when (subItem) {
                            is MenuItem.Action -> {
                                line("case $itemIndex:")
                                indent++
                                line("if ($condExpr) {")
                                indent++
                                for (stmt in subItem.onSelect) {
                                    generateStatement(stmt)
                                }
                                indent--
                                line("}")
                                line("break;")
                                indent--
                                itemIndex++
                            }
                            is MenuItem.Toggle -> {
                                line("case $itemIndex:")
                                indent++
                                line("if ($condExpr) {")
                                indent++
                                line("${subItem.variable} = !${subItem.variable};")
                                for (stmt in subItem.onChange) {
                                    generateStatement(stmt)
                                }
                                line("_${name}_draw();")
                                indent--
                                line("}")
                                line("break;")
                                indent--
                                itemIndex++
                            }
                            is MenuItem.Slider,
                            is MenuItem.Option -> {
                                // These are handled by left/right, not A button
                                itemIndex++
                            }
                            is MenuItem.Separator -> {}
                            is MenuItem.Conditional -> {
                                // Nested conditionals not supported
                            }
                        }
                    }
                }
            }
        }

        indent--
        line("}")
    }
    line()
}

/**
 * Generate a helper function that executes the selection action for a grid menu slot. This function
 * can be called from the tick function (A button) or programmatically via IRMenuSelect.
 */
private fun CodeGenerator.generateGridMenuSelectFunction(menu: MenuDefinition) {
    val name = menu.name

    block("static void _${name}_do_select(UINT8 grid_index)") {
        menu.arrayBinding?.let { binding ->
            // Make the selected slot value available
            line("UINT8 _grid_slot_value = ${binding.arrayVar}[grid_index];")
            line("UINT8 _selected_index = grid_index;")

            // Check if slot is empty (value == 0) and handle empty slot case
            if (binding.emptySlotStatements.isNotEmpty()) {
                line("if (_grid_slot_value == 0) {")
                indent++
                for (stmt in binding.emptySlotStatements) {
                    generateStatement(stmt)
                }
                indent--
                line("} else {")
                indent++
                for (stmt in binding.onSelectStatements) {
                    generateStatement(stmt)
                }
                indent--
                line("}")
            } else {
                // No empty slot handling, just emit onSelect statements
                for (stmt in binding.onSelectStatements) {
                    generateStatement(stmt)
                }
            }
        }
    }
    line()
}

private fun CodeGenerator.generateMenuTickFunction(menu: MenuDefinition) {
    val name = menu.name
    val style = menu.style
    val itemCount = countMenuItems(menu.items)

    block("static void _${name}_tick(void)") {
        line("if (!_${name}_active) return;")
        line()

        // Navigation
        line("// D-Pad navigation")
        if (style.wrapMode == WrapMode.WRAP) {
            line("if ((_joypad & J_DOWN) && !(_joypad_prev & J_DOWN)) {")
            indent++
            line("_${name}_cursor = (_${name}_cursor + 1) % ${name.uppercase()}_ITEM_COUNT;")
            line("_${name}_draw();")
            indent--
            line("}")
            line("if ((_joypad & J_UP) && !(_joypad_prev & J_UP)) {")
            indent++
            line(
                "_${name}_cursor = (_${name}_cursor == 0) ? ${name.uppercase()}_ITEM_COUNT - 1 : _${name}_cursor - 1;"
            )
            line("_${name}_draw();")
            indent--
            line("}")
        } else {
            line("if ((_joypad & J_DOWN) && !(_joypad_prev & J_DOWN)) {")
            indent++
            line("if (_${name}_cursor < ${name.uppercase()}_ITEM_COUNT - 1) {")
            indent++
            line("_${name}_cursor++;")
            line("_${name}_draw();")
            indent--
            line("}")
            indent--
            line("}")
            line("if ((_joypad & J_UP) && !(_joypad_prev & J_UP)) {")
            indent++
            line("if (_${name}_cursor > 0) {")
            indent++
            line("_${name}_cursor--;")
            line("_${name}_draw();")
            indent--
            line("}")
            indent--
            line("}")
        }
        line()

        // Selection (A button) - call helper function
        line("// A button - select")
        line("if ((_joypad & J_A) && !(_joypad_prev & J_A)) {")
        indent++
        line("_${name}_do_select(_${name}_cursor);")
        indent--
        line("}")
        line()

        // Left/Right for sliders and options
        generateMenuLeftRightHandling(menu, itemCount)

        // B button - cancel/back
        line("// B button - cancel/back")
        line("if ((_joypad & J_B) && !(_joypad_prev & J_B)) {")
        indent++
        if (menu.parentMenu != null) {
            line("// Return to parent menu")
            line("_${name}_visible = 0;")
            line("_${name}_active = 0;")
            line("_${menu.parentMenu}_visible = 1;")
            line("_${menu.parentMenu}_active = 1;")
            line("_${menu.parentMenu}_draw();")
        } else {
            line("// No parent - just hide")
            line("_${name}_visible = 0;")
            line("_${name}_active = 0;")
        }
        indent--
        line("}")
    }
    line()
}

private fun CodeGenerator.generateMenuLeftRightHandling(menu: MenuDefinition, itemCount: Int) {
    val name = menu.name

    // Check for sliders/options at any level (including inside conditionals)
    fun hasSliderOrOption(items: List<MenuItem>): Boolean {
        return items.any { item ->
            when (item) {
                is MenuItem.Slider,
                is MenuItem.Option -> true
                is MenuItem.Conditional -> hasSliderOrOption(item.items)
                else -> false
            }
        }
    }

    if (!hasSliderOrOption(menu.items)) return

    line("// Left/Right for sliders and options")
    line("if ((_joypad & J_RIGHT) && !(_joypad_prev & J_RIGHT)) {")
    indent++
    line("switch (_${name}_cursor) {")
    indent++

    var itemIndex = 0
    for (item in menu.items) {
        when (item) {
            is MenuItem.Slider -> {
                line("case $itemIndex:")
                indent++
                line("if (${item.variable} < ${item.max}) {")
                indent++
                line("${item.variable} += ${item.step};")
                for (stmt in item.onChange) {
                    generateStatement(stmt)
                }
                line("_${name}_draw();")
                indent--
                line("}")
                line("break;")
                indent--
                itemIndex++
            }
            is MenuItem.Option -> {
                line("case $itemIndex:")
                indent++
                line("if (${item.variable} < ${item.choices.size - 1}) {")
                indent++
                line("${item.variable}++;")
                for (stmt in item.onChange) {
                    generateStatement(stmt)
                }
                line("_${name}_draw();")
                indent--
                line("}")
                line("break;")
                indent--
                itemIndex++
            }
            is MenuItem.Action,
            is MenuItem.Toggle -> itemIndex++
            is MenuItem.Separator -> {}
            is MenuItem.Conditional -> {
                // Handle sliders/options inside conditional blocks
                val condExpr = generateExpr(item.condition)
                for (subItem in item.items) {
                    when (subItem) {
                        is MenuItem.Slider -> {
                            line("case $itemIndex:")
                            indent++
                            line("if ($condExpr) {")
                            indent++
                            line("if (${subItem.variable} < ${subItem.max}) {")
                            indent++
                            line("${subItem.variable} += ${subItem.step};")
                            for (stmt in subItem.onChange) {
                                generateStatement(stmt)
                            }
                            line("_${name}_draw();")
                            indent--
                            line("}")
                            indent--
                            line("}")
                            line("break;")
                            indent--
                            itemIndex++
                        }
                        is MenuItem.Option -> {
                            line("case $itemIndex:")
                            indent++
                            line("if ($condExpr) {")
                            indent++
                            line("if (${subItem.variable} < ${subItem.choices.size - 1}) {")
                            indent++
                            line("${subItem.variable}++;")
                            for (stmt in subItem.onChange) {
                                generateStatement(stmt)
                            }
                            line("_${name}_draw();")
                            indent--
                            line("}")
                            indent--
                            line("}")
                            line("break;")
                            indent--
                            itemIndex++
                        }
                        is MenuItem.Action,
                        is MenuItem.Toggle -> itemIndex++
                        is MenuItem.Separator -> {}
                        is MenuItem.Conditional -> {
                            // Nested conditionals not supported for simplicity
                        }
                    }
                }
            }
        }
    }

    indent--
    line("}")
    indent--
    line("}")

    line("if ((_joypad & J_LEFT) && !(_joypad_prev & J_LEFT)) {")
    indent++
    line("switch (_${name}_cursor) {")
    indent++

    itemIndex = 0
    for (item in menu.items) {
        when (item) {
            is MenuItem.Slider -> {
                line("case $itemIndex:")
                indent++
                line("if (${item.variable} > ${item.min}) {")
                indent++
                line("${item.variable} -= ${item.step};")
                for (stmt in item.onChange) {
                    generateStatement(stmt)
                }
                line("_${name}_draw();")
                indent--
                line("}")
                line("break;")
                indent--
                itemIndex++
            }
            is MenuItem.Option -> {
                line("case $itemIndex:")
                indent++
                line("if (${item.variable} > 0) {")
                indent++
                line("${item.variable}--;")
                for (stmt in item.onChange) {
                    generateStatement(stmt)
                }
                line("_${name}_draw();")
                indent--
                line("}")
                line("break;")
                indent--
                itemIndex++
            }
            is MenuItem.Action,
            is MenuItem.Toggle -> itemIndex++
            is MenuItem.Separator -> {}
            is MenuItem.Conditional -> {
                // Handle sliders/options inside conditional blocks
                val condExpr = generateExpr(item.condition)
                for (subItem in item.items) {
                    when (subItem) {
                        is MenuItem.Slider -> {
                            line("case $itemIndex:")
                            indent++
                            line("if ($condExpr) {")
                            indent++
                            line("if (${subItem.variable} > ${subItem.min}) {")
                            indent++
                            line("${subItem.variable} -= ${subItem.step};")
                            for (stmt in subItem.onChange) {
                                generateStatement(stmt)
                            }
                            line("_${name}_draw();")
                            indent--
                            line("}")
                            indent--
                            line("}")
                            line("break;")
                            indent--
                            itemIndex++
                        }
                        is MenuItem.Option -> {
                            line("case $itemIndex:")
                            indent++
                            line("if ($condExpr) {")
                            indent++
                            line("if (${subItem.variable} > 0) {")
                            indent++
                            line("${subItem.variable}--;")
                            for (stmt in subItem.onChange) {
                                generateStatement(stmt)
                            }
                            line("_${name}_draw();")
                            indent--
                            line("}")
                            indent--
                            line("}")
                            line("break;")
                            indent--
                            itemIndex++
                        }
                        is MenuItem.Action,
                        is MenuItem.Toggle -> itemIndex++
                        is MenuItem.Separator -> {}
                        is MenuItem.Conditional -> {
                            // Nested conditionals not supported for simplicity
                        }
                    }
                }
            }
        }
    }

    indent--
    line("}")
    indent--
    line("}")
    line()
}

private fun CodeGenerator.generateGridMenuTickFunction(menu: MenuDefinition) {
    val name = menu.name
    val gs = menu.gridStyle ?: return

    block("static void _${name}_tick(void)") {
        line("if (!_${name}_active) return;")
        line()

        // 2D Navigation
        line("// D-Pad navigation (2D grid)")
        if (gs.wrapMode == WrapMode.WRAP) {
            line("if ((_joypad & J_RIGHT) && !(_joypad_prev & J_RIGHT)) {")
            indent++
            line("_${name}_cursor_x = (_${name}_cursor_x + 1) % ${name.uppercase()}_COLS;")
            line("_${name}_draw();")
            indent--
            line("}")
            line("if ((_joypad & J_LEFT) && !(_joypad_prev & J_LEFT)) {")
            indent++
            line(
                "_${name}_cursor_x = (_${name}_cursor_x == 0) ? ${name.uppercase()}_COLS - 1 : _${name}_cursor_x - 1;"
            )
            line("_${name}_draw();")
            indent--
            line("}")
            line("if ((_joypad & J_DOWN) && !(_joypad_prev & J_DOWN)) {")
            indent++
            line("_${name}_cursor_y = (_${name}_cursor_y + 1) % ${name.uppercase()}_ROWS;")
            line("_${name}_draw();")
            indent--
            line("}")
            line("if ((_joypad & J_UP) && !(_joypad_prev & J_UP)) {")
            indent++
            line(
                "_${name}_cursor_y = (_${name}_cursor_y == 0) ? ${name.uppercase()}_ROWS - 1 : _${name}_cursor_y - 1;"
            )
            line("_${name}_draw();")
            indent--
            line("}")
        } else {
            line("if ((_joypad & J_RIGHT) && !(_joypad_prev & J_RIGHT)) {")
            indent++
            line(
                "if (_${name}_cursor_x < ${name.uppercase()}_COLS - 1) { _${name}_cursor_x++; _${name}_draw(); }"
            )
            indent--
            line("}")
            line("if ((_joypad & J_LEFT) && !(_joypad_prev & J_LEFT)) {")
            indent++
            line("if (_${name}_cursor_x > 0) { _${name}_cursor_x--; _${name}_draw(); }")
            indent--
            line("}")
            line("if ((_joypad & J_DOWN) && !(_joypad_prev & J_DOWN)) {")
            indent++
            line(
                "if (_${name}_cursor_y < ${name.uppercase()}_ROWS - 1) { _${name}_cursor_y++; _${name}_draw(); }"
            )
            indent--
            line("}")
            line("if ((_joypad & J_UP) && !(_joypad_prev & J_UP)) {")
            indent++
            line("if (_${name}_cursor_y > 0) { _${name}_cursor_y--; _${name}_draw(); }")
            indent--
            line("}")
        }
        line()

        // A button selection - call helper function
        line("// A button - select slot")
        line("if ((_joypad & J_A) && !(_joypad_prev & J_A)) {")
        indent++
        line(
            "_${name}_do_select(_${name}_cursor_y * ${name.uppercase()}_COLS + _${name}_cursor_x);"
        )
        indent--
        line("}")
        line()

        // B button - back
        line("// B button - cancel/back")
        line("if ((_joypad & J_B) && !(_joypad_prev & J_B)) {")
        indent++
        if (menu.parentMenu != null) {
            line("_${name}_visible = 0;")
            line("_${name}_active = 0;")
            line("_${menu.parentMenu}_visible = 1;")
            line("_${menu.parentMenu}_active = 1;")
            line("_${menu.parentMenu}_draw();")
        } else {
            line("_${name}_visible = 0;")
            line("_${name}_active = 0;")
        }
        indent--
        line("}")
    }
    line()
}
