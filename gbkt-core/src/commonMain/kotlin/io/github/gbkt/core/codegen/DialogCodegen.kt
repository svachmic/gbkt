/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator

// =============================================================================
// DIALOG SYSTEM CODE GENERATION
// =============================================================================

internal fun CodeGenerator.generateDialogData() {
    // Always generate default dialog for inline say()
    line("// === Dialog System ===")
    line()

    // Default dialog state (for inline say())
    line("// Default inline dialog state")
    line("static UINT8 _dialog_visible;")
    line("static UINT8 _dialog_text_pos;")
    line("static UINT8 _dialog_text_len;")
    line("static UINT8 _dialog_waiting;")
    line("static UINT8 _dialog_complete;")
    line("#define DIALOG_BUFFER_SIZE 80")
    line("static char _dialog_buffer[DIALOG_BUFFER_SIZE];")
    line("#define DIALOG_X 0")
    line("#define DIALOG_Y 14")
    line("#define DIALOG_W 20")
    line("#define DIALOG_H 4")
    line("#define DIALOG_SPEED 2")
    line()

    // Named dialog states
    for (dialog in game.dialogs) {
        val name = dialog.name
        line("// Dialog: $name")
        line("static UINT8 _${name}_visible;")
        line("static UINT8 _${name}_text_pos;")
        line("static UINT8 _${name}_text_len;")
        line("static UINT8 _${name}_waiting;")
        line("static UINT8 _${name}_complete;")
        line("#define ${name.uppercase()}_BUFFER_SIZE 80")
        line("static char _${name}_buffer[${name.uppercase()}_BUFFER_SIZE];")
        line("static UINT8 _${name}_choice;")
        line("static UINT8 _${name}_choice_count;")
        line("#define ${name.uppercase()}_X ${dialog.box.x}")
        line("#define ${name.uppercase()}_Y ${dialog.box.y}")
        line("#define ${name.uppercase()}_W ${dialog.box.width}")
        line("#define ${name.uppercase()}_H ${dialog.box.height}")
        line("#define ${name.uppercase()}_SPEED ${dialog.typewriter.charsPerFrame}")
        line()
    }

    // Generate helper functions
    generateDialogHelperFunctions()
}

private fun CodeGenerator.generateDialogHelperFunctions() {
    // Draw box with ASCII border
    block("static void _dialog_draw_box(UINT8 x, UINT8 y, UINT8 w, UINT8 h)") {
        line("UINT8 i, row;")
        line("// Top border")
        line("gotoxy(x, y);")
        line("printf(\"+\");")
        line("for (i = 1; i < w - 1; i++) printf(\"-\");")
        line("printf(\"+\");")
        line()
        line("// Middle rows")
        line("for (row = 1; row < h - 1; row++) {")
        indent++
        line("gotoxy(x, y + row);")
        line("printf(\"|\");")
        line("for (i = 1; i < w - 1; i++) printf(\" \");")
        line("printf(\"|\");")
        indent--
        line("}")
        line()
        line("// Bottom border")
        line("gotoxy(x, y + h - 1);")
        line("printf(\"+\");")
        line("for (i = 1; i < w - 1; i++) printf(\"-\");")
        line("printf(\"+\");")
    }
    line()

    // Clear box
    block("static void _dialog_clear_box(UINT8 x, UINT8 y, UINT8 w, UINT8 h)") {
        line("UINT8 i, row;")
        line("for (row = 0; row < h; row++) {")
        indent++
        line("gotoxy(x, y + row);")
        line("for (i = 0; i < w; i++) printf(\" \");")
        indent--
        line("}")
    }
    line()

    // Typewriter update
    block(
        "static void _dialog_typewriter_tick(char *buffer, UINT8 *pos, UINT8 len, UINT8 speed, UINT8 box_x, UINT8 box_y, UINT8 box_w)"
    ) {
        line("UINT8 i, text_x, text_y, text_w;")
        line("if (*pos >= len) return;")
        line()
        line("text_w = box_w - 2;  // Account for borders")
        line("for (i = 0; i < speed && *pos < len; i++) {")
        indent++
        line("text_x = box_x + 1 + ((*pos) % text_w);")
        line("text_y = box_y + 1 + ((*pos) / text_w);")
        line("gotoxy(text_x, text_y);")
        line("printf(\"%c\", buffer[*pos]);")
        line("(*pos)++;")
        indent--
        line("}")
    }
    line()

    // Check for A button advance
    block("static UINT8 _dialog_check_advance(void)") {
        line("return ((_joypad & J_A) && !(_joypad_prev & J_A));")
    }
    line()

    // Choice cursor update
    block(
        "static void _dialog_update_choice(UINT8 *choice, UINT8 count, UINT8 box_x, UINT8 box_y)"
    ) {
        line("UINT8 i;")
        line("// Handle D-pad")
        line("if ((_joypad & J_DOWN) && !(_joypad_prev & J_DOWN)) {")
        indent++
        line("if (*choice < count - 1) (*choice)++;")
        indent--
        line("}")
        line("if ((_joypad & J_UP) && !(_joypad_prev & J_UP)) {")
        indent++
        line("if (*choice > 0) (*choice)--;")
        indent--
        line("}")
        line()
        line("// Draw cursor")
        line("for (i = 0; i < count; i++) {")
        indent++
        line("gotoxy(box_x + 1, box_y + 1 + i);")
        line("printf(\"%c\", (i == *choice) ? '>' : ' ');")
        indent--
        line("}")
    }
    line()
}
