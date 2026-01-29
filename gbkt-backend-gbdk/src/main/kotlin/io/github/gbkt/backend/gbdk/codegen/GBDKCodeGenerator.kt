/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen

import io.github.gbkt.backend.gbdk.codegen.combat.generateBattleEngineSystem
import io.github.gbkt.backend.gbdk.codegen.core.generateMain
import io.github.gbkt.backend.gbdk.codegen.core.generateMapData
import io.github.gbkt.backend.gbdk.codegen.core.generatePaletteData
import io.github.gbkt.backend.gbdk.codegen.core.generatePoolData
import io.github.gbkt.backend.gbdk.codegen.core.generatePoolFunctions
import io.github.gbkt.backend.gbdk.codegen.core.generateSceneEnum
import io.github.gbkt.backend.gbdk.codegen.core.generateSceneFunctions
import io.github.gbkt.backend.gbdk.codegen.core.generateSoundData
import io.github.gbkt.backend.gbdk.codegen.core.generateStateMachineEnums
import io.github.gbkt.backend.gbdk.codegen.core.generateTileData
import io.github.gbkt.backend.gbdk.codegen.core.generateVariables
import io.github.gbkt.backend.gbdk.codegen.data.generateBalanceTables
import io.github.gbkt.backend.gbdk.codegen.data.generateStringTable
import io.github.gbkt.backend.gbdk.codegen.features.generateEasingLookupTables
import io.github.gbkt.backend.gbdk.codegen.features.generateLinkFunctions
import io.github.gbkt.backend.gbdk.codegen.features.generateMixerData
import io.github.gbkt.backend.gbdk.codegen.features.generateMixerFunctions
import io.github.gbkt.backend.gbdk.codegen.features.generateMovementControllerSystem
import io.github.gbkt.backend.gbdk.codegen.features.generatePhysicsFunctions
import io.github.gbkt.backend.gbdk.codegen.features.generateSaveData
import io.github.gbkt.backend.gbdk.codegen.features.generateSweepCollisionFunctions
import io.github.gbkt.backend.gbdk.codegen.features.generateTweenData
import io.github.gbkt.backend.gbdk.codegen.features.generateTweenUpdateFunction
import io.github.gbkt.backend.gbdk.codegen.graphics.clearTransitionState
import io.github.gbkt.backend.gbdk.codegen.graphics.generateAnimationData
import io.github.gbkt.backend.gbdk.codegen.graphics.generateAnimationUpdateFunctions
import io.github.gbkt.backend.gbdk.codegen.graphics.generateCameraFunctions
import io.github.gbkt.backend.gbdk.codegen.graphics.generateCollisionHelpers
import io.github.gbkt.backend.gbdk.codegen.graphics.generateStateMachineUpdateFunctions
import io.github.gbkt.backend.gbdk.codegen.graphics.generateTransitionSequenceData
import io.github.gbkt.backend.gbdk.codegen.rpg.generateAbilitySystem
import io.github.gbkt.backend.gbdk.codegen.rpg.generateBattleSystems
import io.github.gbkt.backend.gbdk.codegen.rpg.generateCanActHelpers
import io.github.gbkt.backend.gbdk.codegen.rpg.generateCharacterClassSystem
import io.github.gbkt.backend.gbdk.codegen.rpg.generateCombatCoreSystem
import io.github.gbkt.backend.gbdk.codegen.rpg.generateCombatFunctions
import io.github.gbkt.backend.gbdk.codegen.rpg.generateDamageCalculatorSystem
import io.github.gbkt.backend.gbdk.codegen.rpg.generateEquipmentSystem
import io.github.gbkt.backend.gbdk.codegen.rpg.generateExtendedAbilityCostSystem
import io.github.gbkt.backend.gbdk.codegen.rpg.generateItemSystem
import io.github.gbkt.backend.gbdk.codegen.rpg.generateLevelingSystem
import io.github.gbkt.backend.gbdk.codegen.rpg.generateMonsterSystem
import io.github.gbkt.backend.gbdk.codegen.rpg.generateQuestSystem
import io.github.gbkt.backend.gbdk.codegen.rpg.generateShopSystem
import io.github.gbkt.backend.gbdk.codegen.rpg.generateStatSchemaSystem
import io.github.gbkt.backend.gbdk.codegen.rpg.generateStatsVariables
import io.github.gbkt.backend.gbdk.codegen.rpg.generateStatusEffectHelpers
import io.github.gbkt.backend.gbdk.codegen.rpg.generateStatusEffectTables
import io.github.gbkt.backend.gbdk.codegen.rpg.generateStatusEffectVariables
import io.github.gbkt.backend.gbdk.codegen.ui.generateCutsceneFunctions
import io.github.gbkt.backend.gbdk.codegen.ui.generateDialogData
import io.github.gbkt.backend.gbdk.codegen.ui.generateMenuData
import io.github.gbkt.backend.gbdk.codegen.ui.generateStatusBarSystem
import io.github.gbkt.backend.gbdk.codegen.world.generateBuiltInTileConstants
import io.github.gbkt.backend.gbdk.codegen.world.generateEncounterSystem
import io.github.gbkt.backend.gbdk.codegen.world.generateEncounterTriggerSystem
import io.github.gbkt.backend.gbdk.codegen.world.generateExplorationSystem
import io.github.gbkt.backend.gbdk.codegen.world.generateFlagsSystem
import io.github.gbkt.backend.gbdk.codegen.world.generateGenericMapObjectSystem
import io.github.gbkt.backend.gbdk.codegen.world.generateMapObjectSystem
import io.github.gbkt.backend.gbdk.codegen.world.generatePendingEncounterVariables
import io.github.gbkt.backend.gbdk.codegen.world.generatePlayerPositionVariables
import io.github.gbkt.backend.gbdk.codegen.world.generateTileAttributeSystem
import io.github.gbkt.backend.gbdk.codegen.world.generateZoneSystem
import io.github.gbkt.core.Game
import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.SourceMap
import io.github.gbkt.core.SourceMapBuilder

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
class GBDKCodeGenerator(internal val game: Game) {
    internal val out = StringBuilder()
    internal var indent = 0

    // Source map tracking
    internal var currentLine = 1
    internal val sourceMapBuilder = SourceMapBuilder(game.name, MAIN_OUTPUT_FILE)

    companion object {
        /** Main output filename for generated C code. */
        const val MAIN_OUTPUT_FILE = "main.c"

        /** Section separator comment for generated C code. */
        const val SECTION_SEPARATOR =
            "// ============================================================================="

        // Common C code keywords used in generation
        internal const val DEFINE_DIRECTIVE = "#define"
        internal const val TYPEDEF_STRUCT = "typedef struct"
        internal const val INLINE_PREFIX = "inline "
        internal const val STATIC_INLINE_PREFIX = "static inline "
        internal const val PRAGMA_BANK = "#pragma bank"
    }

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
            line("$PRAGMA_BANK $bank")
            currentBank = bank
        }
    }

    /** Return to the home bank (bank 0) for code and frequently-accessed data. */
    internal fun returnToHome() = setBank(0)

    // =========================================================================
    // BANK ALLOCATION SCHEME
    // =========================================================================
    //
    // GBDK-2020 uses 16KB ROM banks. Bank 0 is "home" bank (always mapped).
    // Other banks are swapped in as needed.
    //
    // Default bank allocation (configurable via GameConfig.banking):
    //
    //   Bank 0:      Home bank - frequently called code, interrupt handlers,
    //                critical functions that need to be always available
    //   Bank 1:      Menu/UI code
    //   Bank 2:      Exploration code
    //   Bank 3:      Battle system code
    //   Bank 4:      Player management
    //   Bank 5:      Stats/leveling
    //   Banks 6-7:   Monster AI (split across two banks for large rosters)
    //   Bank 8:      Floor/zone data
    //   Bank 10:     Scene lifecycle handlers
    //   Bank 30:     Sound effects
    //
    // These defaults match the original LabyrinthOfTheDragon banking layout.
    // Configure via `config { banking { sceneBank = 10 } }` in game DSL.
    // =========================================================================

    /** Banking configuration from game config. */
    private val bankConfig
        get() = game.config.banking

    /** Bank for menu/UI code. */
    internal val codeBankMenu
        get() = bankConfig.menuBank

    /** Bank for exploration/map code. */
    internal val codeBankExploration
        get() = bankConfig.explorationBank

    /** Bank for scene lifecycle code (enter, frame, exit handlers). */
    internal val codeBankScene
        get() = bankConfig.sceneBank

    /** Bank for battle system state machine and turn management. */
    internal val codeBankBattle
        get() = bankConfig.battleBank

    /** Bank for combat mechanics (damage, abilities, status effects). */
    internal val codeBankCombat
        get() = bankConfig.battleBank // Same bank as battle

    /** Bank for encounter system (random battles, encounter tables). */
    internal val codeBankEncounter
        get() = bankConfig.explorationBank // Same bank as exploration

    /** Bank for monster definitions (AI, stats, item drops) - first half. */
    internal val codeBankMonster
        get() = bankConfig.monsterBank1

    /** Bank for monster definitions (AI, stats, item drops) - second half. */
    internal val codeBankMonster2
        get() = bankConfig.monsterBank2

    /** Bank for floor/zone data. */
    internal val codeBankFloor
        get() = bankConfig.floorDataBank

    /** Bank for sound effects. */
    internal val codeBankSound
        get() = bankConfig.soundBank

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

        line(SECTION_SEPARATOR)
        line("// FORWARD DECLARATIONS FOR BANKED FUNCTIONS")
        line(SECTION_SEPARATOR)
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
            // Tween and camera functions are called from main loop - must be in home bank
            returnToHome()
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

    /**
     * Generate C code split into multiple files by bank.
     *
     * GBDK-2020 does not support multiple #pragma bank directives in a single file. Each source
     * file can only have one bank assignment. This method splits the generated code into separate
     * files per bank to avoid linker overflow warnings.
     *
     * @return Map of filename to content
     */
    /**
     * Generate C code split into multiple files by bank.
     *
     * GBDK-2020 does not support multiple #pragma bank directives in a single file. Each source
     * file can only have one bank assignment. This method splits the generated code into separate
     * files per bank and generates a shared header file with declarations.
     *
     * @return Map of filename to content (includes header file and source files)
     */
    fun generateMultiFile(): Map<String, String> {
        val singleFile = generate()
        return splitByBank(singleFile)
    }

    // =============================================================================
    // MULTI-FILE SPLIT HELPERS
    // =============================================================================

    /** Data class holding parsed declarations from source code for header generation. */
    private data class ParsedDeclarations(
        val includes: List<String>,
        val defines: List<String>,
        val typedefs: List<String>,
        val externVars: List<String>,
        val functionPrototypes: List<String>,
        val inlineFunctions: List<String>,
    )

    /** Extract #include and #define statements from the header section (before first pragma). */
    private fun extractHeaderDeclarations(
        headerLines: List<String>
    ): Pair<List<String>, List<String>> {
        val includes = mutableListOf<String>()
        val defines = mutableListOf<String>()

        for (line in headerLines) {
            when {
                line.startsWith("#include") -> includes.add(line)
                line.startsWith(DEFINE_DIRECTIVE) -> defines.add(line)
            }
        }
        return includes to defines
    }

    /** Extract ALL #define statements from the entire file (some are generated after pragmas). */
    private fun extractAllDefines(
        lines: List<String>,
        existingDefines: List<String>,
    ): List<String> {
        val defines = existingDefines.toMutableList()
        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith(DEFINE_DIRECTIVE) && trimmed !in defines) {
                defines.add(trimmed)
            }
        }
        return defines
    }

    /** Extract typedef struct definitions (multi-line blocks). */
    private fun extractTypedefs(lines: List<String>): List<String> {
        val typedefs = mutableListOf<String>()
        var inTypedef = false
        val currentTypedef = StringBuilder()

        for (line in lines) {
            if (line.trimStart().startsWith(TYPEDEF_STRUCT)) {
                inTypedef = true
                currentTypedef.clear()
                currentTypedef.appendLine(line)
            } else if (inTypedef) {
                currentTypedef.appendLine(line)
                val trimmed = line.trim()
                if (trimmed.startsWith("}") && trimmed.endsWith(";")) {
                    typedefs.add(currentTypedef.toString().trimEnd())
                    inTypedef = false
                }
            }
        }
        return typedefs
    }

    /** Extract inline function definitions (they must be in header for cross-file visibility). */
    private fun extractInlineFunctions(lines: List<String>): List<String> {
        val inlineFunctions = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith(INLINE_PREFIX) || trimmed.startsWith(STATIC_INLINE_PREFIX)) {
                val headerLine =
                    if (trimmed.startsWith(STATIC_INLINE_PREFIX)) {
                        line.replace(STATIC_INLINE_PREFIX, INLINE_PREFIX)
                    } else {
                        line
                    }
                inlineFunctions.add(headerLine)
            }
        }
        return inlineFunctions
    }

    /** Pattern for matching variable declarations (supports 2D arrays and custom types). */
    private val varDeclPattern =
        Regex(
            """^(static\s+)?(const\s+)?""" +
                """(UINT8|UINT16|INT8|INT16|uint8_t|uint16_t|int8_t|int16_t|uint32_t|int32_t|""" +
                """UBYTE|BYTE|UWORD|WORD|fixed_t|char|InventorySlot|\w+_t)""" +
                """\s+(\*?\s*)(\w+)((?:\[[^\]]*\])+)?\s*[=;{]"""
        )

    /** Pattern for matching function definitions (with optional BANKED keyword). */
    private val funcDefPattern =
        Regex(
            """^(static\s+)?""" +
                """(void|UINT8|UINT16|INT8|INT16|uint8_t|uint16_t|int8_t|int16_t|""" +
                """UBYTE|BYTE|UWORD|WORD|fixed_t|char\s*\*?)""" +
                """\s+(\w+)\s*\(([^)]*)\)\s*(BANKED\s*)?\{"""
        )

    /** Extract extern variable declarations from source code. */
    private fun extractExternDeclarations(lines: List<String>): List<String> {
        val externVars = mutableListOf<String>()
        val seenVars = mutableSetOf<String>()

        for (line in lines) {
            if (line.contains("(")) continue // Skip function definitions

            varDeclPattern.find(line)?.let { match ->
                val constPrefix = match.groupValues[2]
                val type = match.groupValues[3]
                val pointer = match.groupValues[4].trim()
                val name = match.groupValues[5]
                val array = match.groupValues[6]

                if (name !in seenVars) {
                    seenVars.add(name)
                    val arrayDecl = formatArrayDecl(array)
                    val externDecl = buildExternDecl(constPrefix, type, pointer, name, arrayDecl)
                    externVars.add(externDecl)
                }
            }
        }
        return externVars
    }

    /** Format array declaration for extern (first dimension empty, rest preserved). */
    private fun formatArrayDecl(array: String): String {
        if (array.isEmpty()) return ""
        return if (array.contains("][")) {
            array.replaceFirst(Regex("""\[\d+\]"""), "[]")
        } else {
            "[]"
        }
    }

    /** Build extern declaration string. */
    private fun buildExternDecl(
        constPrefix: String,
        type: String,
        pointer: String,
        name: String,
        arrayDecl: String,
    ): String {
        return if (constPrefix.isNotEmpty()) {
            "extern const $type $pointer$name$arrayDecl;"
        } else {
            "extern $type $pointer$name$arrayDecl;"
        }
    }

    /** Extract function prototypes from source code. */
    private fun extractFunctionPrototypes(lines: List<String>): List<String> {
        val prototypes = mutableListOf<String>()
        val seenFuncs = mutableSetOf<String>()

        for (line in lines) {
            funcDefPattern.find(line)?.let { match ->
                val returnType = match.groupValues[2]
                val name = match.groupValues[3]
                val params = match.groupValues[4]
                val banked = match.groupValues[5].isNotEmpty()

                if (name !in seenFuncs) {
                    seenFuncs.add(name)
                    val prototype =
                        if (banked) {
                            "$returnType $name($params) BANKED;"
                        } else {
                            "$returnType $name($params);"
                        }
                    prototypes.add(prototype)
                }
            }
        }
        return prototypes
    }

    /** Generate the shared header file content. */
    private fun generateHeaderFile(decls: ParsedDeclarations): String = buildString {
        appendLine("// Generated by gbkt for ${game.name}")
        appendLine("// Shared header for multi-file compilation")
        appendLine()
        appendLine("#ifndef GAME_H")
        appendLine("#define GAME_H")
        appendLine()

        decls.includes.forEach { appendLine(it) }
        appendLine()

        decls.defines.forEach { appendLine(it) }
        appendLine()

        if (decls.typedefs.isNotEmpty()) {
            appendLine("// Type definitions")
            decls.typedefs.forEach { appendLine(it) }
            appendLine()
        }

        if (decls.externVars.isNotEmpty()) {
            appendLine("// External variable declarations")
            decls.externVars.forEach { appendLine(it) }
            appendLine()
        }

        if (decls.functionPrototypes.isNotEmpty()) {
            appendLine("// Function prototypes")
            decls.functionPrototypes.forEach { appendLine(it) }
            appendLine()
        }

        if (decls.inlineFunctions.isNotEmpty()) {
            appendLine("// Inline functions")
            decls.inlineFunctions.forEach { appendLine(it) }
            appendLine()
        }

        appendLine("#endif // GAME_H")
    }

    /** Check if a line should be skipped when writing to source files. */
    private fun shouldSkipLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return line.startsWith("#include") ||
            line.startsWith("#define") ||
            line.startsWith("// Generated by gbkt") ||
            line.startsWith("// GBDK C code") ||
            trimmed.startsWith("inline ") ||
            trimmed.startsWith("static inline ")
    }

    /** Process a line by removing 'static' keyword if present. */
    private fun processLine(line: String, staticPattern: Regex): String {
        return if (staticPattern.containsMatchIn(line)) {
            line.replaceFirst("static ", "")
        } else {
            line
        }
    }

    /** Create a new bank file header. */
    private fun createBankHeader(bank: Int): StringBuilder =
        StringBuilder().apply {
            appendLine("// Generated by gbkt for ${game.name}")
            appendLine("// Bank $bank")
            appendLine()
            appendLine("#include \"game.h\"")
            appendLine()
            appendLine("$PRAGMA_BANK $bank")
        }

    /**
     * Split generated C code into multiple files by #pragma bank directives.
     *
     * Creates:
     * - game.h: Header with all #defines, extern declarations, and function prototypes
     * - main.c: Bank 0 code
     * - bankN.c: Code for each additional bank
     */
    private fun splitByBank(code: String): Map<String, String> {
        val lines = code.lines()

        val firstBankIdx = lines.indexOfFirst { it.startsWith(PRAGMA_BANK) }
        if (firstBankIdx < 0) {
            return mapOf(MAIN_OUTPUT_FILE to code)
        }

        val headerLines = lines.take(firstBankIdx)

        // Extract all declarations for header file
        val (includes, headerDefines) = extractHeaderDeclarations(headerLines)
        val allDefines = extractAllDefines(lines, headerDefines)
        val typedefs = extractTypedefs(lines)
        val inlineFunctions = extractInlineFunctions(lines)
        val externVars = extractExternDeclarations(lines)
        val functionPrototypes = extractFunctionPrototypes(lines)

        val decls =
            ParsedDeclarations(
                includes = includes,
                defines = allDefines,
                typedefs = typedefs,
                externVars = externVars,
                functionPrototypes = functionPrototypes,
                inlineFunctions = inlineFunctions,
            )

        // Generate files
        val files = mutableMapOf<String, String>()
        files["game.h"] = generateHeaderFile(decls)

        // Split source code by bank using state holder
        val state =
            BankSplitState(
                staticPattern = Regex("""^static\s+"""),
                bank0Content =
                    StringBuilder().apply {
                        appendLine("// Generated by gbkt for ${game.name}")
                        appendLine("// Bank 0 (HOME)")
                        appendLine()
                        appendLine("#include \"game.h\"")
                        appendLine()
                    },
                bankContents = mutableMapOf(),
            )

        // Process pre-bank content
        for (i in 0 until firstBankIdx) {
            processPreBankLine(lines[i], state)?.let { state.bank0Content.appendLine(it) }
        }

        // Process banked content
        state.skipTypedef = false
        for (idx in firstBankIdx until lines.size) {
            processBankedLine(lines[idx], state)
        }

        files[MAIN_OUTPUT_FILE] = state.bank0Content.toString().trimEnd() + "\n"
        for ((bank, content) in state.bankContents) {
            files["bank$bank.c"] = content.toString().trimEnd() + "\n"
        }

        return files
    }

    /** State holder for bank splitting to reduce parameter count. */
    private class BankSplitState(
        val staticPattern: Regex,
        val bank0Content: StringBuilder,
        val bankContents: MutableMap<Int, StringBuilder>,
    ) {
        var skipTypedef: Boolean = false
        var currentBankNum: Int = 0
    }

    /** Process a line from the pre-bank section. Returns processed line or null if skipped. */
    private fun processPreBankLine(line: String, state: BankSplitState): String? {
        val trimmed = line.trimStart()

        // Handle typedef struct blocks (multi-line, skip all)
        if (trimmed.startsWith("typedef struct")) {
            state.skipTypedef = true
            return null
        }
        if (state.skipTypedef) {
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("}") && trimmedLine.endsWith(";")) {
                state.skipTypedef = false
            }
            return null
        }

        // Skip lines that go in header
        if (shouldSkipLine(line)) return null

        // Remove static keyword if present
        return processLine(line, state.staticPattern)
    }

    /**
     * Check if line is a typedef struct start/end and update state. Returns true if should skip.
     */
    private fun handleTypedefState(line: String, state: BankSplitState): Boolean {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("typedef struct")) {
            state.skipTypedef = true
            return true
        }
        if (state.skipTypedef) {
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("}") && trimmedLine.endsWith(";")) {
                state.skipTypedef = false
            }
            return true
        }
        return false
    }

    /** Check if line should be skipped in banked section (inline, define, or pragma). */
    private fun shouldSkipBankedLine(line: String, state: BankSplitState): Boolean {
        val trimmed = line.trimStart()

        // Skip inline functions and defines (they're in header)
        if (trimmed.startsWith(INLINE_PREFIX) || trimmed.startsWith(STATIC_INLINE_PREFIX)) return true
        if (trimmed.startsWith(DEFINE_DIRECTIVE)) return true

        // Handle bank pragma (updates state but returns true to skip appending)
        if (line.startsWith(PRAGMA_BANK)) {
            val newBank = line.substringAfter(PRAGMA_BANK).trim().toIntOrNull() ?: 0
            if (newBank != 0 && !state.bankContents.containsKey(newBank)) {
                state.bankContents[newBank] = createBankHeader(newBank)
            }
            state.currentBankNum = newBank
            return true
        }

        return false
    }

    /** Process a line from the banked section. */
    private fun processBankedLine(line: String, state: BankSplitState) {
        // Handle typedef blocks and skip conditions
        if (handleTypedefState(line, state)) return
        if (shouldSkipBankedLine(line, state)) return

        // Append to appropriate bank
        val processedLine = processLine(line, state.staticPattern)
        if (state.currentBankNum == 0) {
            state.bank0Content.appendLine(processedLine)
        } else {
            state.bankContents[state.currentBankNum]?.appendLine(processedLine)
        }
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

        line(SECTION_SEPARATOR)
        line("// DEBUG ARRAY BOUNDS CHECKING")
        line(SECTION_SEPARATOR)
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
