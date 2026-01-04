/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.ir.CallbackTransition
import io.github.gbkt.core.ir.FadeInTransition
import io.github.gbkt.core.ir.FadeOutTransition
import io.github.gbkt.core.ir.FlashTransition
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IrisTransition
import io.github.gbkt.core.ir.IrisType
import io.github.gbkt.core.ir.ParallelTransition
import io.github.gbkt.core.ir.SequenceTransition
import io.github.gbkt.core.ir.ShakeTransition
import io.github.gbkt.core.ir.Transition
import io.github.gbkt.core.ir.WaitTransition
import io.github.gbkt.core.ir.WipeDirection
import io.github.gbkt.core.ir.WipeTransition

// =============================================================================
// CAMERA AND TRANSITION CODE GENERATION
// =============================================================================

// State tracking for transition callbacks and composed sequences
// These are accessed via extension properties on CodeGenerator

internal sealed class TransitionStep {
    data class Primitive(
        val type: String, // TRANS_FADE_OUT, etc.
        val duration: Int,
        val params: Map<String, Any> = emptyMap()
    ) : TransitionStep()

    data class Callback(val callbackId: Int) : TransitionStep()

    data class Wait(val frames: Int) : TransitionStep()

    data class Parallel(val effects: List<TransitionStep>) : TransitionStep()
}

internal data class ComposedTransitionData(
    val id: Int,
    val steps: List<TransitionStep>,
    val targetScene: String?
)

// Storage for per-generator state
private val transitionCallbacksMap = mutableMapOf<CodeGenerator, MutableList<List<IRStatement>>>()
private val composedTransitionSequencesMap =
    mutableMapOf<CodeGenerator, MutableList<ComposedTransitionData>>()

internal val CodeGenerator.transitionCallbacks: MutableList<List<IRStatement>>
    get() = transitionCallbacksMap.getOrPut(this) { mutableListOf() }

internal val CodeGenerator.composedTransitionSequences: MutableList<ComposedTransitionData>
    get() = composedTransitionSequencesMap.getOrPut(this) { mutableListOf() }

/**
 * Clean up per-generator state after code generation is complete. This prevents memory leaks by
 * removing the generator from the maps.
 */
internal fun CodeGenerator.clearTransitionState() {
    transitionCallbacksMap.remove(this)
    composedTransitionSequencesMap.remove(this)
}

internal fun CodeGenerator.getTransitionCallbackId(statements: List<IRStatement>): Int {
    val idx = transitionCallbacks.indexOfFirst { it == statements }
    return if (idx >= 0) {
        idx + 1 // 1-based, 0 = no callback
    } else {
        transitionCallbacks.add(statements)
        transitionCallbacks.size // 1-based
    }
}

/**
 * Generate code for a composed transition. For simple single transitions, emits legacy code. For
 * complex sequences, generates sequence-based code.
 */
internal fun CodeGenerator.generateComposedTransition(
    transition: Transition,
    targetScene: String?
) {
    // For simple single transitions, emit legacy code for efficiency
    when (transition) {
        is FadeOutTransition -> {
            line("_transition_type = TRANS_FADE_OUT;")
            line("_transition_timer = 0;")
            line("_transition_duration = ${transition.frames};")
            if (targetScene != null) {
                line("_trans_target_scene = SCENE_${targetScene.uppercase()};")
            }
        }
        is FadeInTransition -> {
            line("_transition_type = TRANS_FADE_IN;")
            line("_transition_timer = 0;")
            line("_transition_duration = ${transition.frames};")
            if (targetScene != null) {
                line("_trans_target_scene = SCENE_${targetScene.uppercase()};")
            }
        }
        is FlashTransition -> {
            line("_transition_type = TRANS_FLASH;")
            line("_transition_timer = 0;")
            line("_transition_duration = ${transition.frames};")
            line("_transition_flash_color = ${transition.color.rgb555};")
            if (targetScene != null) {
                line("_trans_target_scene = SCENE_${targetScene.uppercase()};")
            }
        }
        is WipeTransition -> {
            val transType =
                when (transition.direction) {
                    WipeDirection.LEFT -> "TRANS_WIPE_L"
                    WipeDirection.RIGHT -> "TRANS_WIPE_R"
                    WipeDirection.UP -> "TRANS_WIPE_U"
                    WipeDirection.DOWN -> "TRANS_WIPE_D"
                }
            line("_transition_type = $transType;")
            line("_transition_timer = 0;")
            line("_transition_duration = ${transition.frames};")
            if (targetScene != null) {
                line("_trans_target_scene = SCENE_${targetScene.uppercase()};")
            }
        }
        is IrisTransition -> {
            val transType =
                when (transition.type) {
                    IrisType.CLOSE -> "TRANS_IRIS_IN"
                    IrisType.OPEN -> "TRANS_IRIS_OUT"
                }
            line("_transition_type = $transType;")
            line("_transition_timer = 0;")
            line("_transition_duration = ${transition.frames};")
            if (transition.centerX != null && transition.centerY != null) {
                line("_transition_center_x = ${generateExpr(transition.centerX)};")
                line("_transition_center_y = ${generateExpr(transition.centerY)};")
            } else {
                line("_transition_center_x = 80;") // Screen center
                line("_transition_center_y = 72;")
            }
            if (targetScene != null) {
                line("_trans_target_scene = SCENE_${targetScene.uppercase()};")
            }
        }
        is ShakeTransition -> {
            line("_shake_intensity = ${transition.intensity};")
            line("_shake_timer = ${transition.frames};")
            line("_shake_decay = ${transition.decay.ordinal};")
            // Shake doesn't block scene changes, so handle target scene immediately
            if (targetScene != null) {
                line("_trans_target_scene = SCENE_${targetScene.uppercase()};")
                line("_trans_scene_on_shake_complete = 1;")
            }
        }
        is WaitTransition -> {
            // Wait is only meaningful in sequences
            line("// Wait: ${transition.frames} frames")
            line("_transition_type = TRANS_WAIT;")
            line("_transition_timer = 0;")
            line("_transition_duration = ${transition.frames};")
            if (targetScene != null) {
                line("_trans_target_scene = SCENE_${targetScene.uppercase()};")
            }
        }
        is CallbackTransition -> {
            // Callbacks in isolation just execute immediately
            transition.statements.forEach { generateStatement(it) }
            if (targetScene != null) {
                line("_next_scene = SCENE_${targetScene.uppercase()};")
                line("_scene_changed = 1;")
            }
        }
        is SequenceTransition,
        is ParallelTransition -> {
            // Complex transitions use the sequence state machine
            val seqId = registerComposedTransition(transition, targetScene)
            line("_trans_seq_id = $seqId;")
            line("_trans_seq_step = 0;")
            line("_trans_seq_timer = 0;")
            line("_trans_seq_active = 1;")
            if (targetScene != null) {
                line("_trans_target_scene = SCENE_${targetScene.uppercase()};")
            } else {
                line("_trans_target_scene = SCENE_NONE;")
            }
        }
    }
}

/** Flatten a transition tree into steps and register it. */
private fun CodeGenerator.registerComposedTransition(
    transition: Transition,
    targetScene: String?
): Int {
    val steps = flattenTransition(transition)
    val id = composedTransitionSequences.size
    composedTransitionSequences.add(ComposedTransitionData(id, steps, targetScene))
    return id
}

/** Flatten a transition into a list of steps. */
private fun CodeGenerator.flattenTransition(t: Transition): List<TransitionStep> {
    return when (t) {
        is SequenceTransition -> t.steps.flatMap { flattenTransition(it) }
        is ParallelTransition -> {
            // Parallel effects are grouped into one step
            val flatEffects = t.effects.flatMap { flattenTransition(it) }
            listOf(TransitionStep.Parallel(flatEffects))
        }
        is CallbackTransition -> {
            val cbId = getTransitionCallbackId(t.statements)
            listOf(TransitionStep.Callback(cbId))
        }
        is WaitTransition -> listOf(TransitionStep.Wait(t.frames))
        is FadeOutTransition -> listOf(TransitionStep.Primitive("TRANS_FADE_OUT", t.frames))
        is FadeInTransition -> listOf(TransitionStep.Primitive("TRANS_FADE_IN", t.frames))
        is FlashTransition ->
            listOf(
                TransitionStep.Primitive(
                    "TRANS_FLASH",
                    t.frames,
                    mapOf(
                        "colorLo" to (t.color.rgb555 and 0xFF),
                        "colorHi" to ((t.color.rgb555 shr 8) and 0xFF)
                    )
                )
            )
        is ShakeTransition ->
            listOf(
                TransitionStep.Primitive(
                    "TRANS_SHAKE",
                    t.frames,
                    mapOf("intensity" to t.intensity, "decay" to t.decay.ordinal)
                )
            )
        is WipeTransition -> {
            val transType =
                when (t.direction) {
                    WipeDirection.LEFT -> "TRANS_WIPE_L"
                    WipeDirection.RIGHT -> "TRANS_WIPE_R"
                    WipeDirection.UP -> "TRANS_WIPE_U"
                    WipeDirection.DOWN -> "TRANS_WIPE_D"
                }
            listOf(TransitionStep.Primitive(transType, t.frames))
        }
        is IrisTransition -> {
            val transType =
                when (t.type) {
                    IrisType.CLOSE -> "TRANS_IRIS_IN"
                    IrisType.OPEN -> "TRANS_IRIS_OUT"
                }
            val params = mutableMapOf<String, Any>()
            if (t.centerX != null) params["centerX"] = generateExpr(t.centerX)
            if (t.centerY != null) params["centerY"] = generateExpr(t.centerY)
            listOf(TransitionStep.Primitive(transType, t.frames, params))
        }
    }
}

internal fun CodeGenerator.generateTransitionSequenceData() {
    if (composedTransitionSequences.isEmpty()) return

    line("// === Composed Transition Sequences ===")
    line()

    // Generate step type constants for sequences
    line("// Sequence step types")
    line("#define TSTEP_END       0")
    line("#define TSTEP_FADE_OUT  1")
    line("#define TSTEP_FADE_IN   2")
    line("#define TSTEP_FLASH     3")
    line("#define TSTEP_WIPE_L    4")
    line("#define TSTEP_WIPE_R    5")
    line("#define TSTEP_WIPE_U    6")
    line("#define TSTEP_WIPE_D    7")
    line("#define TSTEP_IRIS_OUT  8")
    line("#define TSTEP_IRIS_IN   9")
    line("#define TSTEP_WAIT      10")
    line("#define TSTEP_SHAKE     11")
    line("#define TSTEP_CALLBACK  12")
    line("#define TSTEP_PARALLEL  13")
    line()

    // Generate each sequence as a const byte array
    for ((idx, seq) in composedTransitionSequences.withIndex()) {
        val bytes = encodeSequence(seq.steps)
        val bytesStr = bytes.joinToString(", ") { it.toString() }
        line("static const UINT8 _trans_seq_${idx}[] = { $bytesStr };")
    }
    line()

    // Generate sequence lookup function
    block("static const UINT8* _get_trans_seq(UINT8 id)") {
        block("switch (id)") {
            for ((idx, _) in composedTransitionSequences.withIndex()) {
                line("case $idx: return _trans_seq_$idx;")
            }
            line("default: return _trans_seq_0;")
        }
    }
    line()
}

/**
 * Encode a sequence of transition steps into bytes. Format:
 * [step_count, step1_type, step1_duration, step1_params..., step2_type, ...]
 */
private fun encodeSequence(steps: List<TransitionStep>): List<Int> {
    val bytes = mutableListOf<Int>()
    bytes.add(steps.size) // Step count

    for (step in steps) {
        when (step) {
            is TransitionStep.Primitive -> {
                val typeCode =
                    when (step.type) {
                        "TRANS_FADE_OUT" -> 1
                        "TRANS_FADE_IN" -> 2
                        "TRANS_FLASH" -> 3
                        "TRANS_WIPE_L" -> 4
                        "TRANS_WIPE_R" -> 5
                        "TRANS_WIPE_U" -> 6
                        "TRANS_WIPE_D" -> 7
                        "TRANS_IRIS_OUT" -> 8
                        "TRANS_IRIS_IN" -> 9
                        "TRANS_SHAKE" -> 11
                        else -> 0
                    }
                bytes.add(typeCode)
                bytes.add(step.duration)
                // Add extra params for shake and flash
                if (step.type == "TRANS_SHAKE") {
                    bytes.add(step.params["intensity"] as? Int ?: 4)
                    bytes.add(step.params["decay"] as? Int ?: 1)
                } else if (step.type == "TRANS_FLASH") {
                    bytes.add(step.params["colorLo"] as? Int ?: 0xFF)
                    bytes.add(step.params["colorHi"] as? Int ?: 0x7F)
                }
            }
            is TransitionStep.Wait -> {
                bytes.add(10) // TSTEP_WAIT
                bytes.add(step.frames)
            }
            is TransitionStep.Callback -> {
                bytes.add(12) // TSTEP_CALLBACK
                bytes.add(step.callbackId)
            }
            is TransitionStep.Parallel -> {
                bytes.add(13) // TSTEP_PARALLEL
                bytes.add(step.effects.size)
                // Recursively encode parallel effects (simple, no nesting)
                for (effect in step.effects) {
                    when (effect) {
                        is TransitionStep.Primitive -> {
                            val typeCode =
                                when (effect.type) {
                                    "TRANS_FADE_OUT" -> 1
                                    "TRANS_FADE_IN" -> 2
                                    "TRANS_FLASH" -> 3
                                    "TRANS_SHAKE" -> 11
                                    else -> 0
                                }
                            bytes.add(typeCode)
                            bytes.add(effect.duration)
                            if (effect.type == "TRANS_SHAKE") {
                                bytes.add(effect.params["intensity"] as? Int ?: 4)
                                bytes.add(effect.params["decay"] as? Int ?: 1)
                            } else if (effect.type == "TRANS_FLASH") {
                                bytes.add(effect.params["colorLo"] as? Int ?: 0xFF)
                                bytes.add(effect.params["colorHi"] as? Int ?: 0x7F)
                            }
                        }
                        is TransitionStep.Wait -> {
                            bytes.add(10)
                            bytes.add(effect.frames)
                        }
                        else -> {
                            // Nested parallel/callbacks not supported in parallel
                            bytes.add(0)
                            bytes.add(0)
                        }
                    }
                }
            }
        }
    }
    bytes.add(0) // End marker
    return bytes
}

internal fun CodeGenerator.generateCameraFunctions() {
    if (game.camera == null) return

    line("// === Camera System ===")
    line()

    // Camera update function
    block("void _camera_update(void)") {
        // Smooth follow
        block("if (_camera_follow_active)") {
            line("// Calculate target camera position (centered on follow target)")
            line("INT16 target_x = (INT16)*_camera_follow_x - 80 + _camera_offset_x;")
            line("INT16 target_y = (INT16)*_camera_follow_y - 72 + _camera_offset_y;")
            line()
            line("// Clamp to camera bounds")
            line("if (target_x < _camera_bounds_min_x) target_x = _camera_bounds_min_x;")
            line("if (target_x > _camera_bounds_max_x) target_x = _camera_bounds_max_x;")
            line("if (target_y < _camera_bounds_min_y) target_y = _camera_bounds_min_y;")
            line("if (target_y > _camera_bounds_max_y) target_y = _camera_bounds_max_y;")
            line()
            line("_camera_target_x = (UINT8)target_x;")
            line("_camera_target_y = (UINT8)target_y;")
            line()
            line("// Smooth follow (fixed-point lerp)")
            block("if (_camera_smoothing > 0)") {
                line("INT16 dx = (INT16)_camera_target_x - (INT16)_camera_x;")
                line("INT16 dy = (INT16)_camera_target_y - (INT16)_camera_y;")
                line("_camera_x += (dx * _camera_smoothing) >> 8;")
                line("_camera_y += (dy * _camera_smoothing) >> 8;")
            }
            block("else") {
                line("_camera_x = _camera_target_x;")
                line("_camera_y = _camera_target_y;")
            }
        }
        line()

        // Screen shake
        block("if (_shake_timer > 0)") {
            line("// Random shake offset")
            line("_shake_offset_x = (rand() % (_shake_intensity * 2 + 1)) - _shake_intensity;")
            line("_shake_offset_y = (rand() % (_shake_intensity * 2 + 1)) - _shake_intensity;")
            line("_shake_timer--;")
            line()
            line("// Apply decay")
            block("if (_shake_decay == 1 && (_shake_timer % 4) == 0 && _shake_intensity > 0)") {
                line("_shake_intensity--;  // Linear decay")
            }
            block(
                "else if (_shake_decay == 2 && (_shake_timer % 2) == 0 && _shake_intensity > 1)"
            ) {
                line("_shake_intensity = _shake_intensity >> 1;  // Exponential decay")
            }
        }
        block("else") {
            line("_shake_offset_x = 0;")
            line("_shake_offset_y = 0;")
        }
        line()

        // Apply to scroll registers
        line("// Apply camera + shake to scroll registers")
        line("SCX_REG = _camera_x + _shake_offset_x;")
        line("SCY_REG = _camera_y + _shake_offset_y;")
    }
    line()

    // Transition update function
    block("void _transition_update(void)") {
        block("if (_transition_type == TRANS_NONE)") { line("return;") }
        line()
        line("_transition_timer++;")
        line("UINT8 progress = (_transition_timer * 255) / _transition_duration;")
        line()

        block("switch (_transition_type)") {
            // Fade out
            block("case TRANS_FADE_OUT:") {
                if (game.config.gbcSupport) {
                    line("// GBC: Fade palettes to black")
                    line(
                        "BGP_REG = (progress < 64) ? 0xE4 : (progress < 128) ? 0xE9 : (progress < 192) ? 0xFE : 0xFF;"
                    )
                } else {
                    line("// DMG: Adjust BGP register")
                    line(
                        "BGP_REG = (progress < 64) ? 0xE4 : (progress < 128) ? 0xE9 : (progress < 192) ? 0xFE : 0xFF;"
                    )
                }
                line("break;")
            }

            // Fade in
            block("case TRANS_FADE_IN:") {
                line(
                    "BGP_REG = (progress < 64) ? 0xFF : (progress < 128) ? 0xFE : (progress < 192) ? 0xE9 : 0xE4;"
                )
                line("break;")
            }

            // Flash
            block("case TRANS_FLASH:") {
                if (game.config.gbcSupport) {
                    line("if (progress < 128) {")
                    indent++
                    line("// Flash on: set all palette entries to flash color")
                    line(
                        "UINT16 flash_pal[4] = {_transition_flash_color, _transition_flash_color, _transition_flash_color, _transition_flash_color};"
                    )
                    line("set_bkg_palette(0, 1, flash_pal);")
                    indent--
                    line("} else {")
                    indent++
                    line("// Flash off: restore original palette (handled at completion)")
                    indent--
                    line("}")
                } else {
                    line("// DMG: Convert RGB555 to grayscale BGP value")
                    line("// Extract RGB components and compute luminance")
                    line("UINT8 r = _transition_flash_color & 0x1F;")
                    line("UINT8 g = (_transition_flash_color >> 5) & 0x1F;")
                    line("UINT8 b = (_transition_flash_color >> 10) & 0x1F;")
                    line("UINT8 lum = (r + g + g + b) >> 2;  // Weighted average")
                    line("UINT8 shade = (lum > 23) ? 0 : (lum > 15) ? 1 : (lum > 7) ? 2 : 3;")
                    line("UINT8 flash_bgp = shade | (shade << 2) | (shade << 4) | (shade << 6);")
                    line("BGP_REG = (progress < 128) ? flash_bgp : 0xE4;")
                }
                line("break;")
            }

            // Wipes - basic implementation
            line("case TRANS_WIPE_L:")
            line("case TRANS_WIPE_R:")
            line("case TRANS_WIPE_U:")
            line("case TRANS_WIPE_D:")
            indent++
            line("// Wipe transitions use tile-based masking")
            line("// For now, fall back to fade behavior")
            line("BGP_REG = (progress < 128) ? 0xE4 : 0xFF;")
            line("break;")
            indent--

            // Iris
            line("case TRANS_IRIS_IN:")
            line("case TRANS_IRIS_OUT:")
            indent++
            line("// Iris transitions use circular masking")
            line("// For now, fall back to fade behavior")
            line("if (_transition_type == TRANS_IRIS_IN) {")
            indent++
            line("BGP_REG = (progress < 128) ? 0xE4 : 0xFF;")
            indent--
            line("} else {")
            indent++
            line("BGP_REG = (progress < 128) ? 0xFF : 0xE4;")
            indent--
            line("}")
            line("break;")
            indent--

            // Wait (for composed transitions)
            block("case TRANS_WAIT:") {
                line("// Just wait, don't change palette")
                line("break;")
            }
        }
        line()

        // Check completion
        block("if (_transition_timer >= _transition_duration)") {
            line("_transition_type = TRANS_NONE;")
            line("BGP_REG = 0xE4;  // Restore default palette")
            line()
            line("// Execute callback if set")
            if (transitionCallbacks.isNotEmpty()) {
                block("switch (_transition_callback)") {
                    transitionCallbacks.forEachIndexed { idx, statements ->
                        line("case ${idx + 1}:")
                        indent++
                        statements.forEach { generateStatement(it) }
                        line("break;")
                        indent--
                    }
                }
            }
            line("_transition_callback = 0;")
            line()
            line("// Handle target scene transition")
            block("if (_trans_target_scene != SCENE_NONE)") {
                line("_next_scene = _trans_target_scene;")
                line("_scene_changed = 1;")
                line("_trans_target_scene = SCENE_NONE;")
            }
        }
    }
    line()

    // Sequence update function (for composed transitions)
    if (composedTransitionSequences.isNotEmpty()) {
        generateSequenceUpdateFunction()
    }
}

/** Generate the sequence state machine update function. */
private fun CodeGenerator.generateSequenceUpdateFunction() {
    block("void _trans_seq_update(void)") {
        block("if (!_trans_seq_active)") { line("return;") }
        line()
        line("const UINT8* seq = _get_trans_seq(_trans_seq_id);")
        line("UINT8 step_count = seq[0];")
        line()
        block("if (_trans_seq_step >= step_count)") {
            line("// Sequence complete")
            line("_trans_seq_active = 0;")
            block("if (_trans_target_scene != SCENE_NONE)") {
                line("_next_scene = _trans_target_scene;")
                line("_scene_changed = 1;")
                line("_trans_target_scene = SCENE_NONE;")
            }
            line("return;")
        }
        line()
        line("// Find current step in sequence data")
        line("UINT8 offset = 1;  // Skip step count")
        block("for (UINT8 i = 0; i < _trans_seq_step; i++)") {
            line("UINT8 type = seq[offset];")
            block("switch (type)") {
                line(
                    "case TSTEP_SHAKE: offset += 4; break;  // type + duration + intensity + decay"
                )
                line(
                    "case TSTEP_FLASH: offset += 4; break;  // type + duration + colorLo + colorHi"
                )
                line("case TSTEP_CALLBACK: offset += 2; break;  // type + callback_id")
                line(
                    "case TSTEP_PARALLEL: { UINT8 n = seq[offset+1]; offset += 2 + n * 2; break; }"
                )
                line("default: offset += 2; break;  // type + duration")
            }
        }
        line()
        line("UINT8 step_type = seq[offset];")
        line("UINT8 step_duration = seq[offset + 1];")
        line()
        line("// Initialize step on first frame")
        block("if (_trans_seq_timer == 0)") {
            block("switch (step_type)") {
                line(
                    "case TSTEP_FADE_OUT: _transition_type = TRANS_FADE_OUT; _transition_duration = step_duration; _transition_timer = 0; break;"
                )
                line(
                    "case TSTEP_FADE_IN: _transition_type = TRANS_FADE_IN; _transition_duration = step_duration; _transition_timer = 0; break;"
                )
                block("case TSTEP_FLASH:") {
                    line("_transition_type = TRANS_FLASH;")
                    line("_transition_duration = step_duration;")
                    line("_transition_timer = 0;")
                    line("_transition_flash_color = seq[offset + 2] | (seq[offset + 3] << 8);")
                    line("break;")
                }
                line(
                    "case TSTEP_WIPE_L: _transition_type = TRANS_WIPE_L; _transition_duration = step_duration; _transition_timer = 0; break;"
                )
                line(
                    "case TSTEP_WIPE_R: _transition_type = TRANS_WIPE_R; _transition_duration = step_duration; _transition_timer = 0; break;"
                )
                line(
                    "case TSTEP_WIPE_U: _transition_type = TRANS_WIPE_U; _transition_duration = step_duration; _transition_timer = 0; break;"
                )
                line(
                    "case TSTEP_WIPE_D: _transition_type = TRANS_WIPE_D; _transition_duration = step_duration; _transition_timer = 0; break;"
                )
                line(
                    "case TSTEP_IRIS_IN: _transition_type = TRANS_IRIS_IN; _transition_duration = step_duration; _transition_timer = 0; break;"
                )
                line(
                    "case TSTEP_IRIS_OUT: _transition_type = TRANS_IRIS_OUT; _transition_duration = step_duration; _transition_timer = 0; break;"
                )
                line(
                    "case TSTEP_WAIT: _transition_type = TRANS_WAIT; _transition_duration = step_duration; _transition_timer = 0; break;"
                )
                block("case TSTEP_SHAKE:") {
                    line("_shake_intensity = seq[offset + 2];")
                    line("_shake_timer = step_duration;")
                    line("_shake_decay = seq[offset + 3];")
                    line("break;")
                }
                block("case TSTEP_CALLBACK:") {
                    line("// Execute callback immediately")
                    line("_transition_callback = seq[offset + 1];")
                    // The callback will be executed by transition_update
                    line("break;")
                }
            }
        }
        line()
        line("_trans_seq_timer++;")
        line()
        line("// Check if current step is complete")
        line("UINT8 step_complete = 0;")
        block("if (step_type == TSTEP_CALLBACK)") {
            line("step_complete = 1;  // Callbacks complete immediately")
        }
        block("else if (step_type == TSTEP_SHAKE)") { line("step_complete = (_shake_timer == 0);") }
        block("else") { line("step_complete = (_trans_seq_timer >= step_duration);") }
        line()
        block("if (step_complete)") {
            line("_trans_seq_step++;")
            line("_trans_seq_timer = 0;")
            line("_transition_type = TRANS_NONE;")
        }
    }
    line()
}
