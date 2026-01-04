/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.codegen.clearTransitionState
import io.github.gbkt.core.codegen.generateAnimationData
import io.github.gbkt.core.codegen.generateAnimationUpdateFunctions
import io.github.gbkt.core.codegen.generateCameraFunctions
import io.github.gbkt.core.codegen.generateCollisionHelpers
import io.github.gbkt.core.codegen.generateCutsceneFunctions
import io.github.gbkt.core.codegen.generateDialogData
import io.github.gbkt.core.codegen.generateEasingLookupTables
import io.github.gbkt.core.codegen.generateLinkFunctions
import io.github.gbkt.core.codegen.generateMain
import io.github.gbkt.core.codegen.generateMapData
import io.github.gbkt.core.codegen.generateMenuData
import io.github.gbkt.core.codegen.generateMixerData
import io.github.gbkt.core.codegen.generateMixerFunctions
import io.github.gbkt.core.codegen.generatePaletteData
import io.github.gbkt.core.codegen.generatePhysicsFunctions
import io.github.gbkt.core.codegen.generatePoolData
import io.github.gbkt.core.codegen.generatePoolFunctions
import io.github.gbkt.core.codegen.generateSaveData
import io.github.gbkt.core.codegen.generateSceneEnum
import io.github.gbkt.core.codegen.generateSceneFunctions
import io.github.gbkt.core.codegen.generateSoundData
import io.github.gbkt.core.codegen.generateStateMachineEnums
import io.github.gbkt.core.codegen.generateStateMachineUpdateFunctions
import io.github.gbkt.core.codegen.generateTileData
import io.github.gbkt.core.codegen.generateTransitionSequenceData
import io.github.gbkt.core.codegen.generateTweenData
import io.github.gbkt.core.codegen.generateTweenUpdateFunction
import io.github.gbkt.core.codegen.generateVariables

/** Exception thrown when code generation encounters validation errors. */
class CodeGenerationException(message: String) : RuntimeException(message)

/**
 * Generates clean GBDK C code from the game definition.
 *
 * The actual generation logic is split into extension functions in the codegen package:
 * - StatementCodegen.kt: IR statement generation
 * - ExpressionCodegen.kt: IR expression generation with constant folding
 * - DataCodegen.kt: Tile, map, sound, palette data
 * - SaveCodegen.kt: Save system
 * - DialogCodegen.kt: Dialog system
 * - MenuCodegen.kt: Menu system
 * - PoolCodegen.kt: Entity pool system
 * - VariablesCodegen.kt: Variables and enums
 * - AnimationCodegen.kt: Animation and state machines
 * - SceneCodegen.kt: Scene functions
 * - CameraCodegen.kt: Camera and transitions
 * - PhysicsCodegen.kt: Physics world and collision response
 * - PathfindingCodegen.kt: A* pathfinding
 * - AudioCodegen.kt: Audio mixer system
 * - TweenCodegen.kt: Tweening and easing
 * - MainCodegen.kt: Main function
 */
class CodeGenerator(internal val game: Game) {
    internal val out = StringBuilder()
    internal var indent = 0

    // Source map tracking
    internal var currentLine = 1
    internal val sourceMapBuilder = SourceMapBuilder(game.name, "main.c")

    // Validation error tracking - errors are collected during generation and reported at the end
    internal val validationErrors = mutableListOf<String>()

    // Array size lookup for bounds checking
    internal val arraySizes: Map<String, Int> by lazy {
        game.arrays.associate { it.name to it.size }
    }

    /** Report a validation error that will be thrown at the end of code generation. */
    internal fun reportError(message: String) {
        validationErrors.add(message)
        line("// ERROR: $message")
    }

    internal fun line(s: String = "") {
        out.appendLine("${"    ".repeat(indent)}$s")
        currentLine++
    }

    /** Output a line of C code with source location tracking for the source map. */
    internal fun lineWithSource(s: String, location: SourceLocation?, symbol: String? = null) {
        sourceMapBuilder.addMapping(currentLine, location, symbol)
        line(s)
    }

    internal fun block(header: String, body: () -> Unit) {
        line("$header {")
        indent++
        body()
        indent--
        line("}")
    }

    /** Block with source location tracking. */
    internal fun blockWithSource(
        header: String,
        location: SourceLocation?,
        symbol: String? = null,
        body: () -> Unit
    ) {
        sourceMapBuilder.addMapping(currentLine, location, symbol)
        line("$header {")
        indent++
        body()
        indent--
        line("}")
    }

    fun generate(): String {
        // Reset state for clean generation
        currentLine = 1
        try {
            generateHeader()
            generateIncludes()
            generateDebugMacros()
            generatePaletteData()
            generateTileData()
            generateMapData()
            generateCollisionHelpers()
            generateSoundData()
            generateMixerData()
            generateSaveData()
            generateDialogData()
            generateMenuData()
            generatePoolData()
            generateVariables()
            generateStateMachineEnums()
            generateAnimationData()
            generateSceneEnum()
            generatePoolFunctions()
            generateMixerFunctions()
            generateLinkFunctions()
            generateCutsceneFunctions()
            generateAnimationUpdateFunctions()
            generateStateMachineUpdateFunctions()
            // Tween data structures must be generated before scene functions that use them
            generateTweenData()
            generateEasingLookupTables()
            generateTweenUpdateFunction()
            // Camera functions must also be generated before scene functions
            generateTransitionSequenceData()
            generateCameraFunctions()
            generatePhysicsFunctions()
            // Scene functions use tweens, camera, physics - generate after dependencies
            generateSceneFunctions()
            generateMain()

            // Check for validation errors collected during generation
            if (validationErrors.isNotEmpty()) {
                val errorMessage = buildString {
                    appendLine("Code generation failed with ${validationErrors.size} error(s):")
                    validationErrors.forEachIndexed { index, error ->
                        appendLine("  ${index + 1}. $error")
                    }
                }
                throw CodeGenerationException(errorMessage)
            }

            return out.toString()
        } finally {
            // Clean up per-generator state to prevent memory leaks (even on exception)
            clearTransitionState()
            validationErrors.clear()
        }
    }

    /**
     * Generate C code along with a source map for debugging. The source map links generated C code
     * lines back to their Kotlin DSL origins.
     *
     * @return A pair of (generated C code, source map)
     */
    fun generateWithSourceMap(): Pair<String, SourceMap> {
        val code = generate()
        return code to sourceMapBuilder.build()
    }

    private fun generateHeader() {
        line("// Generated by gbkt for ${game.name}")
        line("// GBDK C code for Game Boy")
        line()
    }

    private fun generateIncludes() {
        line("#include <gb/gb.h>")
        line("#include <stdint.h>")
        line("#include <string.h>")
        line("#include <stdio.h>")
        line("#include <stdlib.h>")
        if (game.music.isNotEmpty()) {
            line("#include <hUGEDriver.h>")
        }
        if (game.config.gbcSupport) {
            line("#include <gb/cgb.h>")
        }
        line()
    }

    internal fun generateDebugMacros() {
        if (game.arrays.isEmpty()) return

        line("// =============================================================================")
        line("// DEBUG ARRAY BOUNDS CHECKING")
        line("// =============================================================================")
        line("#ifdef DEBUG")
        line(
            "static inline UINT8 _gb_array_get_u8(const UINT8* arr, INT16 idx, UINT8 size, const char* name) {"
        )
        line("    if (idx < 0 || idx >= size) {")
        line("        printf(\"OOB: %s[%d] size=%d\\n\", name, idx, size);")
        line("        return 0u;")
        line("    }")
        line("    return arr[idx];")
        line("}")
        line(
            "static inline UINT16 _gb_array_get_u16(const UINT16* arr, INT16 idx, UINT8 size, const char* name) {"
        )
        line("    if (idx < 0 || idx >= size) {")
        line("        printf(\"OOB: %s[%d] size=%d\\n\", name, idx, size);")
        line("        return 0u;")
        line("    }")
        line("    return arr[idx];")
        line("}")
        line(
            "static inline INT8 _gb_array_get_i8(const INT8* arr, INT16 idx, UINT8 size, const char* name) {"
        )
        line("    if (idx < 0 || idx >= size) {")
        line("        printf(\"OOB: %s[%d] size=%d\\n\", name, idx, size);")
        line("        return 0;")
        line("    }")
        line("    return arr[idx];")
        line("}")
        line(
            "static inline INT16 _gb_array_get_i16(const INT16* arr, INT16 idx, UINT8 size, const char* name) {"
        )
        line("    if (idx < 0 || idx >= size) {")
        line("        printf(\"OOB: %s[%d] size=%d\\n\", name, idx, size);")
        line("        return 0;")
        line("    }")
        line("    return arr[idx];")
        line("}")
        line("#define GB_ARRAY_SET(arr, idx, size, val) \\")
        line("    do { if ((idx) >= 0 && (idx) < (size)) (arr)[(idx)] = (val); \\")
        line("         else printf(\"OOB: %s[%d] size=%d\\n\", #arr, idx, size); } while(0)")
        line("#else")
        line("#define _gb_array_get_u8(arr, idx, size, name) ((arr)[(idx)])")
        line("#define _gb_array_get_u16(arr, idx, size, name) ((arr)[(idx)])")
        line("#define _gb_array_get_i8(arr, idx, size, name) ((arr)[(idx)])")
        line("#define _gb_array_get_i16(arr, idx, size, name) ((arr)[(idx)])")
        line("#define GB_ARRAY_SET(arr, idx, size, val) ((arr)[(idx)] = (val))")
        line("#endif")
        line()
    }
}
