/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator

/** Code generation for link cable multiplayer. */
internal fun CodeGenerator.generateLinkFunctions() {
    if (game.link == null) return

    line("// =============================================================================")
    line("// LINK CABLE FUNCTIONS")
    line("// =============================================================================")
    line()

    generateLinkInit()
    generateLinkUpdate()
    generateLinkSend()
}

private fun CodeGenerator.generateLinkInit() {
    line("void _link_init(void) {")
    indent++
    line("// Initialize serial port for link cable")
    line("_link_state = 0;")
    line("_link_connected = 0;")
    line("_link_is_master = 0;")
    line("_link_has_data = 0;")
    line("_link_received = 0;")
    line("_link_send_buffer = 0;")
    line()
    line("// Set up serial interrupt")
    line("// SC_REG = 0x80 | 0x01; // Start with internal clock (master)")
    indent--
    line("}")
    line()
}

private fun CodeGenerator.generateLinkUpdate() {
    val link = game.link ?: return

    line("void _link_update(void) {")
    indent++
    line("// Check for received data")
    line("if (_link_has_data) {")
    indent++
    line("UINT8 ${link.receiveDataVar} = _link_received;")
    // Generate the onReceive callback statements
    for (stmt in link.onReceiveStatements) {
        generateStatement(stmt)
    }
    line("_link_has_data = 0;")
    indent--
    line("}")
    indent--
    line("}")
    line()
}

private fun CodeGenerator.generateLinkSend() {
    line("void _link_send(UINT8 data) {")
    indent++
    line("_link_send_buffer = data;")
    line("// SB_REG = data;")
    line("// SC_REG = 0x80 | 0x01; // Start transfer")
    indent--
    line("}")
    line()
}
