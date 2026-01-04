/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator

// =============================================================================
// SCENE CODE GENERATION
// =============================================================================

internal fun CodeGenerator.generateSceneEnum() {
    line("// === Scenes ===")
    line("enum {")
    indent++
    line("SCENE_NONE = 255,  // No scene (for transitions)")
    game.scenes.keys.forEachIndexed { i, name ->
        val comma = if (i < game.scenes.size - 1) "," else ""
        line("SCENE_${name.uppercase()}$comma")
    }
    indent--
    line("};")
    line()
}

internal fun CodeGenerator.generateSceneFunctions() {
    for ((name, scene) in game.scenes) {
        // Enter
        if (scene.onEnter.isNotEmpty()) {
            block("void ${name}_enter(void)") { scene.onEnter.forEach { generateStatement(it) } }
            line()
        }

        // Frame
        val hasBoundSprites = game.sprites.any { it.isBound }
        if (scene.onFrame.isNotEmpty() || hasBoundSprites) {
            block("void ${name}_frame(void)") {
                scene.onFrame.forEach { generateStatement(it) }
                // Auto-update bound sprites at end of frame
                generateSpriteBindings()
            }
            line()
        }

        // Exit
        if (scene.onExit.isNotEmpty()) {
            block("void ${name}_exit(void)") { scene.onExit.forEach { generateStatement(it) } }
            line()
        }
    }
}

internal fun CodeGenerator.generateSpriteBindings() {
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
