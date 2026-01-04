/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.graphics.Sprite

// =============================================================================
// ANIMATION AND STATE MACHINE CODE GENERATION
// =============================================================================

internal fun CodeGenerator.generateAnimationData() {
    val animatedSprites = game.sprites.filter { it.hasAnimations }
    if (animatedSprites.isEmpty()) return

    line("// === Animation Data ===")
    line()

    // Generate frame arrays for each animation
    for (sprite in animatedSprites) {
        for ((animName, anim) in sprite.animations) {
            if (anim.frames.isEmpty()) {
                line(
                    "// WARNING: Animation '${animName}' on sprite '${sprite.name}' has no frames - using placeholder"
                )
                line("const UINT8 ${sprite.name}_${animName}_frames[] = { 0 };")
            } else {
                val frames = anim.frames.joinToString(", ")
                line("const UINT8 ${sprite.name}_${animName}_frames[] = { $frames };")
            }
        }
    }
    line()

    // Generate animation metadata struct
    line("typedef struct {")
    indent++
    line("const UINT8 *frames;")
    line("UINT8 frame_count;")
    line("UINT8 delay;")
    line("UINT8 flags;")
    indent--
    line("} AnimationData;")
    line()

    // Generate animation tables for each sprite
    for (sprite in animatedSprites) {
        line("const AnimationData ${sprite.name}_anims[] = {")
        indent++
        val animList = sprite.animations.entries.toList()
        for ((index, entry) in animList.withIndex()) {
            val (animName, anim) = entry
            val flags = if (anim.loop) "ANIM_FLAG_LOOPING" else "0"
            val comma = if (index < animList.size - 1) "," else ""
            // Use at least 1 for frame count to avoid division by zero
            val frameCount = if (anim.frames.isEmpty()) 1 else anim.frameCount
            line(
                "{ ${sprite.name}_${animName}_frames, $frameCount, ${anim.frameDelay}, $flags }$comma  // $animName"
            )
        }
        indent--
        line("};")
        line()
    }
}

internal fun CodeGenerator.generateAnimationUpdateFunctions() {
    val animatedSprites = game.sprites.filter { it.hasAnimations }
    if (animatedSprites.isEmpty()) return

    line("// === Animation Update Functions ===")
    line()

    // Generate per-sprite update functions
    for (sprite in animatedSprites) {
        val hasCallbacks =
            sprite.animations.any { it.value.hasOnComplete || it.value.hasFrameEvents }

        block("void ${sprite.name}_update_animation(void)") {
            val animVar = "_${sprite.name}_anim"
            val frameVar = "_${sprite.name}_frame"
            val timerVar = "_${sprite.name}_timer"
            val speedVar = "_${sprite.name}_speed"
            val flagsVar = "_${sprite.name}_flags"

            line("// Skip if no animation playing or paused")
            line("if ($animVar == ANIM_NONE) return;")
            line("if ($flagsVar & ANIM_FLAG_PAUSED) return;")
            line()

            line("// Timer decrement adjusted by speed")
            line("UINT8 ticks = ($speedVar >= 100) ? ($speedVar / 100) : 1;")
            line("if ($timerVar > ticks) {")
            indent++
            line("$timerVar -= ticks;")
            line("return;")
            indent--
            line("}")
            line()

            line("// Time for next frame")
            line("const AnimationData *anim = &${sprite.name}_anims[$animVar];")
            line("UINT8 next_frame;")
            line()

            line("// Handle forward or reverse direction")
            block("if ($flagsVar & ANIM_FLAG_REVERSED)") {
                line("next_frame = ($frameVar == 0) ? anim->frame_count - 1 : $frameVar - 1;")
            }
            block("else") { line("next_frame = $frameVar + 1;") }
            line()

            line("// Check for animation end")
            block(
                "if ((!($flagsVar & ANIM_FLAG_REVERSED) && next_frame >= anim->frame_count) || (($flagsVar & ANIM_FLAG_REVERSED) && $frameVar == 0 && next_frame == anim->frame_count - 1))"
            ) {
                block("if (anim->flags & ANIM_FLAG_LOOPING)") {
                    line(
                        "$frameVar = ($flagsVar & ANIM_FLAG_REVERSED) ? anim->frame_count - 1 : 0;"
                    )
                }
                block("else") {
                    line("// Animation complete - stay on last frame")
                    line(
                        "$frameVar = ($flagsVar & ANIM_FLAG_REVERSED) ? 0 : anim->frame_count - 1;"
                    )
                    line("$flagsVar |= ANIM_FLAG_COMPLETE;")
                    // Generate onComplete callbacks if any
                    generateAnimationCompleteCallbacks(sprite)
                    // Check queue for next animation
                    val queueVar = "_${sprite.name}_queue"
                    val queueLenVar = "_${sprite.name}_queue_len"
                    block("if ($queueLenVar > 0)") { line("${sprite.name}_play_queued();") }
                    block("else") { line("$animVar = ANIM_NONE;") }
                    line("return;")
                }
            }
            block("else") { line("$frameVar = next_frame;") }
            line()

            // Generate frame event callbacks if any
            if (hasCallbacks) {
                generateAnimationFrameEventCallbacks(sprite)
            }

            line("// Update sprite tile")
            line("set_sprite_tile(${sprite.oamSlot}, anim->frames[$frameVar]);")
            line()

            line("// Reset timer for next frame (adjusted for slow speed)")
            line(
                "$timerVar = ($speedVar < 100 && $speedVar > 0) ? (anim->delay * 100) / $speedVar : anim->delay;"
            )
        }
        line()
    }

    // Generate play_queued helper functions
    for (sprite in animatedSprites) {
        val animVar = "_${sprite.name}_anim"
        val frameVar = "_${sprite.name}_frame"
        val timerVar = "_${sprite.name}_timer"
        val speedVar = "_${sprite.name}_speed"
        val flagsVar = "_${sprite.name}_flags"
        val queueVar = "_${sprite.name}_queue"
        val queueLenVar = "_${sprite.name}_queue_len"

        block("void ${sprite.name}_play_queued(void)") {
            line("if ($queueLenVar == 0) return;")
            line()
            line("// Get next animation from queue")
            line("UINT8 next_anim = $queueVar[0];")
            line()
            line("// Shift queue")
            block("for (UINT8 i = 0; i < $queueLenVar - 1; i++)") {
                line("$queueVar[i] = $queueVar[i + 1];")
            }
            line("$queueLenVar--;")
            line()
            line("// Play the queued animation")
            line("const AnimationData *anim = &${sprite.name}_anims[next_anim];")
            line("$animVar = next_anim;")
            line("$frameVar = 0;")
            line("$timerVar = anim->delay;")
            line("$speedVar = 100;") // Reset to normal speed
            line("$flagsVar = 0;")
            line("set_sprite_tile(${sprite.oamSlot}, anim->frames[0]);")
        }
        line()
    }

    // Generate global update function
    block("void update_animations(void)") {
        for (sprite in animatedSprites) {
            line("${sprite.name}_update_animation();")
        }
    }
    line()
}

private fun CodeGenerator.generateAnimationCompleteCallbacks(sprite: Sprite) {
    val animsWithOnComplete = sprite.animations.filter { it.value.hasOnComplete }
    if (animsWithOnComplete.isEmpty()) return

    val animVar = "_${sprite.name}_anim"

    // Generate switch for onComplete callbacks
    block("switch ($animVar)") {
        for ((animName, anim) in animsWithOnComplete) {
            val animConstant = "ANIM_${sprite.name.uppercase()}_${animName.uppercase()}"
            line("case $animConstant:")
            indent++
            anim.onComplete.forEach { generateStatement(it) }
            line("break;")
            indent--
        }
        line("default: break;")
    }
}

private fun CodeGenerator.generateAnimationFrameEventCallbacks(sprite: Sprite) {
    val animsWithEvents = sprite.animations.filter { it.value.hasFrameEvents }
    if (animsWithEvents.isEmpty()) return

    val animVar = "_${sprite.name}_anim"
    val frameVar = "_${sprite.name}_frame"

    line("// Frame event callbacks")
    block("switch ($animVar)") {
        for ((animName, anim) in animsWithEvents) {
            val animConstant = "ANIM_${sprite.name.uppercase()}_${animName.uppercase()}"
            line("case $animConstant:")
            indent++
            block("switch ($frameVar)") {
                for ((frameIndex, statements) in anim.frameEvents) {
                    line("case $frameIndex:")
                    indent++
                    statements.forEach { generateStatement(it) }
                    line("break;")
                    indent--
                }
                line("default: break;")
            }
            line("break;")
            indent--
        }
        line("default: break;")
    }
}

internal fun CodeGenerator.generateStateMachineUpdateFunctions() {
    if (game.stateMachines.isEmpty()) return

    line("// === State Machine Update Functions ===")
    for (machine in game.stateMachines) {
        block("void ${machine.name}_update(void)") {
            // Check for state transition
            block("if (_${machine.name}_changed)") {
                // Exit current state
                block("switch (_${machine.name}_state)") {
                    for ((stateName, state) in machine.states) {
                        if (state.onExit.isNotEmpty()) {
                            line("case STATE_${machine.name.uppercase()}_${stateName.uppercase()}:")
                            indent++
                            state.onExit.forEach { generateStatement(it) }
                            line("break;")
                            indent--
                        }
                    }
                    line("default: break;")
                }
                line()
                line("_${machine.name}_state = _${machine.name}_next;")
                line("_${machine.name}_changed = 0;")
                line()

                // Enter new state
                block("switch (_${machine.name}_state)") {
                    for ((stateName, state) in machine.states) {
                        val hasEnterCode = state.onEnter.isNotEmpty() || state.animation != null
                        if (hasEnterCode) {
                            line("case STATE_${machine.name.uppercase()}_${stateName.uppercase()}:")
                            indent++
                            // Auto-play animation if state has one
                            if (state.animation != null) {
                                val anim = state.animation
                                val sprite = game.sprites.find { it.name == anim.spriteName }
                                if (sprite != null && sprite.hasAnimations) {
                                    val animDef = sprite.animations[anim.animationName]
                                    if (animDef != null && animDef.frames.isNotEmpty()) {
                                        val animVar = "_${anim.spriteName}_anim"
                                        val frameVar = "_${anim.spriteName}_frame"
                                        val timerVar = "_${anim.spriteName}_timer"
                                        val speedVar = "_${anim.spriteName}_speed"
                                        val flagsVar = "_${anim.spriteName}_flags"
                                        val animConstant =
                                            "ANIM_${anim.spriteName.uppercase()}_${anim.animationName.uppercase()}"
                                        line("$animVar = $animConstant;")
                                        line("$frameVar = 0;")
                                        line("$timerVar = ${animDef.frameDelay};")
                                        line("$speedVar = 100;")
                                        line("$flagsVar = 0;")
                                        line(
                                            "set_sprite_tile(${sprite.oamSlot}, ${animDef.frames.first()});"
                                        )
                                    } else if (animDef != null) {
                                        line(
                                            "// WARNING: Animation '${anim.animationName}' has no frames"
                                        )
                                    }
                                }
                            }
                            state.onEnter.forEach { generateStatement(it) }
                            line("break;")
                            indent--
                        }
                    }
                    line("default: break;")
                }
            }
            line()

            // Process current state tick and transitions
            block("switch (_${machine.name}_state)") {
                for ((stateName, state) in machine.states) {
                    line("case STATE_${machine.name.uppercase()}_${stateName.uppercase()}:")
                    indent++

                    // State tick logic
                    if (state.onTick.isNotEmpty()) {
                        state.onTick.forEach { generateStatement(it) }
                    }

                    // Check transitions (with optional lockUntilComplete)
                    if (state.transitions.isNotEmpty()) {
                        if (state.animation?.lockUntilComplete == true) {
                            // Only check transitions when animation is complete
                            val animVar = "_${state.animation.spriteName}_anim"
                            block("if ($animVar == ANIM_NONE)") {
                                for (transition in state.transitions) {
                                    val cond = generateExpr(transition.condition)
                                    block("if ($cond)") {
                                        transition.actions.forEach { generateStatement(it) }
                                    }
                                }
                            }
                        } else {
                            // Check transitions normally
                            for (transition in state.transitions) {
                                val cond = generateExpr(transition.condition)
                                block("if ($cond)") {
                                    transition.actions.forEach { generateStatement(it) }
                                }
                            }
                        }
                    }

                    line("break;")
                    indent--
                }
                line("default: break;")
            }
        }
        line()
    }
}
