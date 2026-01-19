/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.codegen.combat.generateBattleEngineSystem
import io.github.gbkt.core.codegen.core.generateMain
import io.github.gbkt.core.codegen.core.generateMapData
import io.github.gbkt.core.codegen.core.generatePaletteData
import io.github.gbkt.core.codegen.core.generatePoolData
import io.github.gbkt.core.codegen.core.generatePoolFunctions
import io.github.gbkt.core.codegen.core.generateSceneEnum
import io.github.gbkt.core.codegen.core.generateSceneFunctions
import io.github.gbkt.core.codegen.core.generateSoundData
import io.github.gbkt.core.codegen.core.generateStateMachineEnums
import io.github.gbkt.core.codegen.core.generateTileData
import io.github.gbkt.core.codegen.core.generateVariables
import io.github.gbkt.core.codegen.data.generateBalanceTables
import io.github.gbkt.core.codegen.data.generateStringTable
import io.github.gbkt.core.codegen.features.generateEasingLookupTables
import io.github.gbkt.core.codegen.features.generateLinkFunctions
import io.github.gbkt.core.codegen.features.generateMixerData
import io.github.gbkt.core.codegen.features.generateMixerFunctions
import io.github.gbkt.core.codegen.features.generateMovementControllerSystem
import io.github.gbkt.core.codegen.features.generatePhysicsFunctions
import io.github.gbkt.core.codegen.features.generateSaveData
import io.github.gbkt.core.codegen.features.generateSweepCollisionFunctions
import io.github.gbkt.core.codegen.features.generateTweenData
import io.github.gbkt.core.codegen.features.generateTweenUpdateFunction
import io.github.gbkt.core.codegen.graphics.clearTransitionState
import io.github.gbkt.core.codegen.graphics.generateAnimationData
import io.github.gbkt.core.codegen.graphics.generateAnimationUpdateFunctions
import io.github.gbkt.core.codegen.graphics.generateCameraFunctions
import io.github.gbkt.core.codegen.graphics.generateCollisionHelpers
import io.github.gbkt.core.codegen.graphics.generateStateMachineUpdateFunctions
import io.github.gbkt.core.codegen.graphics.generateTransitionSequenceData
import io.github.gbkt.core.codegen.rpg.generateAbilitySystem
import io.github.gbkt.core.codegen.rpg.generateBattleSystems
import io.github.gbkt.core.codegen.rpg.generateCanActHelpers
import io.github.gbkt.core.codegen.rpg.generateCharacterClassSystem
import io.github.gbkt.core.codegen.rpg.generateCombatCoreSystem
import io.github.gbkt.core.codegen.rpg.generateCombatFunctions
import io.github.gbkt.core.codegen.rpg.generateDamageCalculatorSystem
import io.github.gbkt.core.codegen.rpg.generateEquipmentSystem
import io.github.gbkt.core.codegen.rpg.generateExtendedAbilityCostSystem
import io.github.gbkt.core.codegen.rpg.generateItemSystem
import io.github.gbkt.core.codegen.rpg.generateLevelingSystem
import io.github.gbkt.core.codegen.rpg.generateMonsterSystem
import io.github.gbkt.core.codegen.rpg.generateQuestSystem
import io.github.gbkt.core.codegen.rpg.generateShopSystem
import io.github.gbkt.core.codegen.rpg.generateStatSchemaSystem
import io.github.gbkt.core.codegen.rpg.generateStatsVariables
import io.github.gbkt.core.codegen.rpg.generateStatusEffectHelpers
import io.github.gbkt.core.codegen.rpg.generateStatusEffectTables
import io.github.gbkt.core.codegen.rpg.generateStatusEffectVariables
import io.github.gbkt.core.codegen.ui.generateCutsceneFunctions
import io.github.gbkt.core.codegen.ui.generateDialogData
import io.github.gbkt.core.codegen.ui.generateMenuData
import io.github.gbkt.core.codegen.ui.generateStatusBarSystem
import io.github.gbkt.core.codegen.world.generateBuiltInTileConstants
import io.github.gbkt.core.codegen.world.generateEncounterSystem
import io.github.gbkt.core.codegen.world.generateEncounterTriggerSystem
import io.github.gbkt.core.codegen.world.generateExplorationSystem
import io.github.gbkt.core.codegen.world.generateFlagsSystem
import io.github.gbkt.core.codegen.world.generateGenericMapObjectSystem
import io.github.gbkt.core.codegen.world.generateMapObjectSystem
import io.github.gbkt.core.codegen.world.generatePendingEncounterVariables
import io.github.gbkt.core.codegen.world.generatePlayerPositionVariables
import io.github.gbkt.core.codegen.world.generateTileAttributeSystem
import io.github.gbkt.core.codegen.world.generateZoneSystem

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

    // Bank switching state - tracks the current ROM bank for #pragma bank=N directives
    internal var currentBank = 0

    /** Size of each ROM bank in bytes (16 KB). */
    internal val bankSize = 16384

    /**
     * Switch to a specific ROM bank by emitting a #pragma bank directive. Only emits the pragma if
     * we're actually changing banks.
     *
     * @param bank The bank number to switch to (0-31 for 512KB ROM)
     */
    internal fun setBank(bank: Int) {
        if (bank != currentBank && bank >= 0) {
            line()
            line("#pragma bank $bank")
            currentBank = bank
        }
    }

    /** Return to the home bank (bank 0) for code and frequently-accessed data. */
    internal fun returnToHome() = setBank(0)

    // Bank allocation for code sections
    // Banks 1-7: String data (already used)
    // Banks 8-9: Tile data (already used)
    // Banks 10-15: Scene/battle/monster code (new)
    internal val codeBankScene = 10
    internal val codeBankBattle = 11
    internal val codeBankCombat = 12
    internal val codeBankEncounter = 13
    internal val codeBankMonster = 14

    // Forward declarations for banked functions (collected during generation)
    internal val bankedForwardDeclarations = mutableListOf<String>()

    /**
     * Generate a banked function block. Banked functions are placed in a specific ROM bank and
     * called through a trampoline from bank 0.
     *
     * @param bank The ROM bank to place this function in
     * @param header The function signature (without BANKED keyword)
     * @param body The function body generator
     */
    internal fun bankedBlock(bank: Int, header: String, body: () -> Unit) {
        // Record forward declaration for bank 0
        val funcName = header.substringBefore("(").trim().substringAfterLast(" ")
        bankedForwardDeclarations.add("$header BANKED;")

        // Switch to target bank and generate function
        setBank(bank)
        line("$header BANKED {")
        indent++
        body()
        indent--
        line("}")
    }

    /** Generate all collected forward declarations for banked functions. */
    internal fun generateBankedForwardDeclarations() {
        if (bankedForwardDeclarations.isEmpty()) return

        line("// =============================================================================")
        line("// FORWARD DECLARATIONS FOR BANKED FUNCTIONS")
        line("// =============================================================================")
        line()
        for (decl in bankedForwardDeclarations) {
            line(decl)
        }
        line()
    }

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
        body: () -> Unit,
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
            // Banked data tables (strings and balance tables)
            generateStringTable(game.stringTable)
            generateBalanceTables(game.balanceTables)
            generateSaveData()
            // Variables must be defined before dialog/menu functions that use _joypad etc.
            generateVariables()
            generateFlagsSystem()
            // Built-in tile type constants (always generated for exploration/collision systems)
            generateBuiltInTileConstants()
            // Extensible tile attribute system (for custom tile types)
            generateTileAttributeSystem()
            // Zone system for all game world types (dungeon, overworld, side-scrolling, etc.)
            generateZoneSystem()
            // Stats and status effect variables first (just data, no functions yet)
            generateStatsVariables()
            // Configurable stat schema system (customizable stats beyond fixed 8)
            generateStatSchemaSystem()
            generateStatusEffectVariables()
            // Status effect data tables (STATUS_EFFECT_COUNT, lookup arrays) - must be
            // before CombatCore which uses these tables in its functions
            generateStatusEffectTables()
            // Combat core defines _combatant_effect_id arrays, _party_size, _enemy_count,
            // _status_apply, _party_modify_stat, MAX_PARTY_SIZE, bridge functions
            // Uses status effect tables defined above
            generateCombatCoreSystem()
            // Status effect helpers use combat core variables (_combatant_effect_id,
            // _party_size, _enemy_count) so must come after combat core
            generateStatusEffectHelpers()
            generateCanActHelpers()
            // Items, equipment, leveling use combat core functions
            generateItemSystem()
            generateEquipmentSystem()
            generateLevelingSystem()
            // Ability/Monster systems depend on stats, items, leveling, combat core
            // Monster system must be before encounter (defines _monster_base_hp lookup table)
            generateCharacterClassSystem()
            generateDamageCalculatorSystem()
            generateAbilitySystem()
            generateExtendedAbilityCostSystem()
            generateMonsterSystem()
            // Quest tracking system (depends on items, leveling for rewards)
            generateQuestSystem()
            // Shop/economy system (depends on items, inventory)
            generateShopSystem()
            // Scene enum must be before exploration/map objects that transition to SCENE_BATTLE
            generateSceneEnum()
            // Player position variables (always generated for exploration/zone systems)
            generatePlayerPositionVariables()
            // Pending encounter variables (always generated for encounter/battle systems)
            generatePendingEncounterVariables()
            // Encounter system uses combat arrays and monster lookup tables
            generateEncounterSystem()
            // Pluggable encounter triggers (step, time, region, event, wave-based)
            generateEncounterTriggerSystem()
            generateExplorationSystem()
            // Pluggable movement controllers (grid, physics, free-roam, top-down)
            generateMovementControllerSystem()
            generateMapObjectSystem()
            generateGenericMapObjectSystem()
            generateDialogData()
            generateMenuData()
            generateStatusBarSystem()
            generatePoolData()
            generateStateMachineEnums()
            generateAnimationData()
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
            generateSweepCollisionFunctions()
            // Battle systems must be generated at file scope before scene functions
            generateBattleSystems()
            // Pluggable battle engines (turn-based, active-time, real-time, tactical)
            generateBattleEngineSystem()
            generateCombatFunctions()
            // Scene functions use tweens, camera, physics, combat - generate after dependencies
            // Scene enter/exit functions are banked to reduce bank 0 usage
            generateSceneFunctions()
            // Return to home bank for main()
            returnToHome()
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
            bankedForwardDeclarations.clear()
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
        line("#include <gbdk/console.h>") // For gotoxy(), cls()
        line("#include <stdint.h>")
        line("#include <string.h>")
        line("#include <stdio.h>")
        line("#include <stdlib.h>")
        line("#include <rand.h>")
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

        // Extract repeated C code to avoid duplication
        val boundsCheck = "    if (idx < 0 || idx >= size) {"
        val oobPrintf = "        printf(\"OOB: %s[%d] size=%d\\n\", name, idx, size);"
        val returnElement = "    return arr[idx];"

        fun generateArrayGetter(cType: String, funcSuffix: String, defaultValue: String) {
            line(
                "static inline $cType _gb_array_get_$funcSuffix(" +
                    "const $cType* arr, INT16 idx, UINT8 size, const char* name) {"
            )
            line(boundsCheck)
            line(oobPrintf)
            line("        return $defaultValue;")
            line("    }")
            line(returnElement)
            line("}")
        }

        line("// =============================================================================")
        line("// DEBUG ARRAY BOUNDS CHECKING")
        line("// =============================================================================")
        line("#ifdef DEBUG")
        generateArrayGetter("UINT8", "u8", "0u")
        generateArrayGetter("UINT16", "u16", "0u")
        generateArrayGetter("INT8", "i8", "0")
        generateArrayGetter("INT16", "i16", "0")
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
