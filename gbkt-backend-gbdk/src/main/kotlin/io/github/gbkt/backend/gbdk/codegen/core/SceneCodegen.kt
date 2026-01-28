/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.core

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import kotlin.collections.iterator

// =============================================================================
// SCENE CODE GENERATION
// =============================================================================

internal fun GBDKCodeGenerator.generateSceneEnum() {
    line("// === Scenes ===")
    line("// Using typed #define instead of enum to avoid implicit int conversion warnings")
    line("typedef UINT8 scene_t;")
    line("#define SCENE_NONE 255u  // No scene (for transitions)")
    game.scenes.keys.forEachIndexed { i, name -> line("#define SCENE_${name.uppercase()} ${i}u") }
    line()
}

internal fun GBDKCodeGenerator.generateSceneFunctions() {
    // Scene functions are placed in a dedicated bank to reduce bank 0 usage
    // First, collect all banked function signatures for forward declarations
    val bankedSignatures = mutableListOf<String>()
    val hasBoundSprites = game.sprites.any { it.isBound }

    for ((name, scene) in game.scenes) {
        // Frame functions are also banked to reduce bank 0 usage
        if (scene.onFrame.isNotEmpty() || hasBoundSprites) {
            bankedSignatures.add("void ${name}_frame(void) BANKED")
        }
        if (scene.onEnter.isNotEmpty()) {
            bankedSignatures.add("void ${name}_enter(void) BANKED")
        }
        if (scene.onExit.isNotEmpty()) {
            bankedSignatures.add("void ${name}_exit(void) BANKED")
        }
    }

    // Generate forward declarations for banked functions (must come before they're called)
    if (bankedSignatures.isNotEmpty()) {
        line("// =============================================================================")
        line("// FORWARD DECLARATIONS FOR BANKED SCENE FUNCTIONS")
        line("// =============================================================================")
        line()
        for (sig in bankedSignatures) {
            line("$sig;")
        }
        line()
    }

    // Generate frame functions in dedicated bank (they are large and called via trampoline)
    for ((name, scene) in game.scenes) {
        if (scene.onFrame.isNotEmpty() || hasBoundSprites) {
            setBank(codeBankScene)
            block("void ${name}_frame(void) BANKED") {
                scene.onFrame.forEach { generateStatement(it) }
                // Auto-update bound sprites at end of frame
                generateSpriteBindings()
            }
            line()
        }
    }

    // Generate enter/exit functions in their own bank
    for ((name, scene) in game.scenes) {
        // Enter - banked since it's called once per scene transition
        if (scene.onEnter.isNotEmpty()) {
            setBank(codeBankScene)
            block("void ${name}_enter(void) BANKED") {
                scene.onEnter.forEach { generateStatement(it) }
            }
            line()
        }

        // Exit - banked since it's called once per scene transition
        if (scene.onExit.isNotEmpty()) {
            setBank(codeBankScene)
            block("void ${name}_exit(void) BANKED") {
                scene.onExit.forEach { generateStatement(it) }
            }
            line()
        }
    }
}

internal fun GBDKCodeGenerator.generateSpriteBindings() {
    val boundSprites = game.sprites.filter { it.isBound }
    if (boundSprites.isEmpty()) return

    line()
    line("// Auto-update sprite positions")
    for (sprite in boundSprites) {
        // Get variable names from either position (owned) or binding (external)
        val pos = sprite.position
        val bind = sprite.binding
        val (xVar, yVar) =
            when {
                pos != null -> pos.xVarName to pos.yVarName
                bind != null -> bind.xVar to bind.yVar
                else -> continue
            }

        // With camera: offset sprite positions by camera + shake
        // GBDK sprite coords: x + 8, y + 16 are the hardware offsets
        if (game.camera != null) {
            line(
                "move_sprite(${sprite.oamSlot}, $xVar - _camera_x - _shake_offset_x + 8, $yVar - _camera_y - _shake_offset_y + 16);"
            )
        } else {
            line("move_sprite(${sprite.oamSlot}, $xVar, $yVar);")
        }
    }
}
