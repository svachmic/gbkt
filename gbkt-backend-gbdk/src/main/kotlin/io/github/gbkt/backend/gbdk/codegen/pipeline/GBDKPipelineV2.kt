/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.backend.api.GenreSystemVisitor
import io.github.gbkt.backend.gbdk.codegen.GBDKCollectionCodegen
import io.github.gbkt.backend.gbdk.codegen.ast.CArray
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CBreak
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CComment
import io.github.gbkt.backend.gbdk.codegen.ast.CConst
import io.github.gbkt.backend.gbdk.codegen.ast.CDefine
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFile
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CPointer
import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CRawExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CReturn
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitch
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitchCase
import io.github.gbkt.backend.gbdk.codegen.ast.CTypedef
import io.github.gbkt.backend.gbdk.codegen.ast.CU16
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.backend.gbdk.codegen.ast.CWhile
import io.github.gbkt.backend.gbdk.codegen.ast.toPrototype
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.backend.gbdk.codegen.generateAllCollections
import io.github.gbkt.backend.gbdk.codegen.generateCollectionPrototypes
import io.github.gbkt.backend.gbdk.codegen.visitor.ActorVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.CollisionVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.CombatVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.DialogVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.GBDKSystemVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.HudVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.InventoryVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.MenuVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.RpgVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.SceneVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.SoundVisitor
import io.github.gbkt.core.SourceMap
import io.github.gbkt.core.SourceMapBuilder
import io.github.gbkt.core.dsl.ChannelGroupDef
import io.github.gbkt.core.ir.CallExpr
import io.github.gbkt.core.ir.CallOp
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatStateId
import io.github.gbkt.core.ir.EntityCollisionMode
import io.github.gbkt.core.ir.FadeOp
import io.github.gbkt.core.ir.ForOp
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.GlobalFlagsIR
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.LeverObjectIR
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.NpcObjectIR
import io.github.gbkt.core.ir.PathfindingSystem
import io.github.gbkt.core.ir.PoolDestroyActor
import io.github.gbkt.core.ir.PoolForEachActive
import io.github.gbkt.core.ir.PrintAligned
import io.github.gbkt.core.ir.PrintAt
import io.github.gbkt.core.ir.PrintCentered
import io.github.gbkt.core.ir.PrintOp
import io.github.gbkt.core.ir.PushDirection
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SconceObjectIR
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.SetPalette
import io.github.gbkt.core.ir.StringLiteral
import io.github.gbkt.core.ir.StructDef
import io.github.gbkt.core.ir.TransitionEdge
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.WhileOp
import io.github.gbkt.core.ir.ZoneObjectIR
import io.github.gbkt.rpg.domain.AbilityDef
import io.github.gbkt.rpg.domain.ActionNode
import io.github.gbkt.rpg.domain.BehaviorNode
import io.github.gbkt.rpg.domain.CooldownNode
import io.github.gbkt.rpg.domain.MonsterDef
import io.github.gbkt.rpg.domain.PhaseThresholdNode
import io.github.gbkt.rpg.domain.SelectorNode
import io.github.gbkt.rpg.domain.SequenceNode
import io.github.gbkt.rpg.domain.StatusEffectDef
import io.github.gbkt.rpg.domain.UseAbility
import java.util.ServiceLoader

// =============================================================================
// GBDK PIPELINE V2
// Orchestrates the new typed C AST pipeline for v2 GameIR games.
//
// Pipeline:
//   GameIR
//     ├── SceneVisitor.generateSceneEnum() → List<CDefine>
//     ├── ActorVisitor.visit() → List<CVarDecl>
//     ├── GameIR.variables → List<CVarDecl>
//     ├── SceneVisitor.visit() → List<CFunction> (per scene)
//     ├── buildHomeFile() → CFile (bank 0, main.c)
//     ├── buildSceneFile() → CFile (bank 1, bank1.c)
//     ├── buildHeaderFile() → CFile (bank 0, game.h)
//     └── CEmitter.emit() → String (per CFile)
//
// Output: PipelineV2Output — filename-to-C-content + filename-to-source-map-JSON
// =============================================================================

/**
 * Output of [GBDKPipelineV2.generate].
 *
 * @property files Map of filename to C source content (e.g. "main.c" → "// Generated by gbkt\n...")
 * @property sourceMaps Map of filename to v2 source map JSON (e.g. "main.c" →
 *   "{\"version\":\"2.0\"...}") Header files (game.h) are excluded — they contain no DSL-originated
 *   statements.
 */
data class PipelineV2Output(val files: Map<String, String>, val sourceMaps: Map<String, String>)

/**
 * New typed C AST pipeline for v2 [GameIR] games.
 *
 * Replaces the string-based [io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator] for games built
 * with the v2 DSL. The old generator remains functional for v1 games.
 *
 * Bank layout:
 * - Bank 0 (HOME): main.c — variables, game loop, navigation, trampoline stubs
 * - Bank 1: bank1.c — scene lifecycle functions (all BANKED)
 * - Header: game.h — include guards, externs, forward declarations
 *
 * Trampoline stubs are generated in the HOME bank for every scene that has a non-null bankSlot with
 * bank > 0. The stubs forward calls from HOME-resident code (navigate_to_scene) to the BANKED scene
 * functions. navigate_to_scene dispatches through the trampoline for banked scenes so it never
 * calls BANKED functions directly from HOME.
 */
class GBDKPipelineV2 {

    /**
     * Generate C source files from a v2 [GameIR].
     *
     * For each non-header [CFile], creates a [SourceMapCollector] and passes it to [CEmitter.emit]
     * so that line numbers are tracked during emission. The collected mappings are serialized as v2
     * JSON ([SourceMap.version] = "2.0") and returned alongside the C content in
     * [PipelineV2Output.sourceMaps].
     *
     * Header files (game.h) are excluded from source map output — they contain only extern
     * declarations and forward-declaration prototypes, not DSL-originated statements.
     *
     * @return [PipelineV2Output] with C content and source map JSON per non-header file.
     */
    fun generate(gameIR: GameIR): PipelineV2Output {
        val cFiles = buildCFiles(gameIR)
        val files = mutableMapOf<String, String>()
        val sourceMaps = mutableMapOf<String, String>()

        for (cFile in cFiles) {
            if (cFile.isHeader) {
                // Header file: emit without source map — no DSL-originated statements
                files[cFile.name] = CEmitter.emit(cFile)
            } else {
                val collector = SourceMapCollector()
                val content = CEmitter.emit(cFile, collector)
                files[cFile.name] = content

                // Build and serialize v2 source map
                val sourceMapJson = buildSourceMapJson(cFile, collector, gameIR)
                sourceMaps[cFile.name] = sourceMapJson
            }
        }

        // Emit game_metadata.json alongside C files for emulator agent consumption
        files["game_metadata.json"] = buildMetadataFile(gameIR)

        return PipelineV2Output(files = files, sourceMaps = sourceMaps)
    }

    /**
     * Builds a `game_metadata.json` string containing actor-to-OAM-slot mapping, scene indices,
     * DSL-declared variables, and literal display texts.
     *
     * This file is consumed by the emulator agent layer ([GameMetadata]) to resolve OAM sprite
     * slots to named actors and map scene indices to human-readable names. It is also consumed by
     * `GenerateGameConstantsTask` to produce a type-safe `GameConstants.kt` for tests.
     *
     * The variable names use the stripped form (e.g., `"ball_x"` not `"_ball_x"`) matching what
     * [VariableInspector] exposes after stripping leading underscores from .sym file symbols.
     */
    internal fun buildMetadataFile(gameIR: GameIR): String {
        val json = org.json.JSONObject()

        // Scenes: name → index
        val scenes = org.json.JSONObject()
        for ((index, scene) in gameIR.scenes.withIndex()) {
            scenes.put(scene.id, index)
        }
        json.put("scenes", scenes)

        // Actors with OAM slot assignments
        val actors = org.json.JSONArray()
        for (actor in gameIR.actors) {
            if (actor.sprite == null) continue
            val sprite = actor.sprite!!
            val tilesWide = (sprite.size.width + 7) / 8
            val tilesHigh = (sprite.size.height + 7) / 8
            val actorJson =
                org.json
                    .JSONObject()
                    .put("name", actor.id)
                    .put("oamStart", actor.oamSlot?.slot ?: -1)
                    .put("oamCount", tilesWide * tilesHigh)
                    .put("spriteWidth", sprite.size.width)
                    .put("spriteHeight", sprite.size.height)
                    .put(
                        "vars",
                        org.json.JSONObject().put("x", "${actor.id}_x").put("y", "${actor.id}_y"),
                    )
            actors.put(actorJson)
        }
        json.put("actors", actors)

        // Variables: DSL-declared variables with name and type
        val variables = org.json.JSONArray()
        for (variable in gameIR.variables) {
            variables.put(
                org.json
                    .JSONObject()
                    .put("name", variable.name)
                    .put("type", variable.type.name)
                    .put("semantic", inferVariableSemantic(variable.name))
            )
        }
        json.put("variables", variables)

        // Texts: literal display strings extracted from all scene scripts
        val allOps = gameIR.scenes.flatMap { it.enterOps + it.frameOps + it.exitOps }
        val texts = org.json.JSONArray()
        for (text in collectTexts(allOps)) {
            texts.put(text)
        }
        json.put("texts", texts)

        // Terminal scenes: convention-based detection of game-ending scenes
        val terminalScenes = org.json.JSONArray()
        for (scene in gameIR.scenes) {
            if (scene.id.lowercase() in TERMINAL_SCENE_PATTERNS) {
                terminalScenes.put(scene.id)
            }
        }
        json.put("terminalScenes", terminalScenes)

        // Controls: per-scene input mappings extracted from IfOp conditions
        val controlsJson = org.json.JSONObject()
        for ((sceneId, mappings) in extractControls(gameIR)) {
            val mappingsArray = org.json.JSONArray()
            for (mapping in mappings) {
                mappingsArray.put(
                    org.json.JSONObject().put("button", mapping.button).put("type", mapping.type)
                )
            }
            controlsJson.put(sceneId, mappingsArray)
        }
        json.put("controls", controlsJson)

        // Transitions: scene navigation graph extracted from NavigateTo ops
        val transitionsArray = org.json.JSONArray()
        for (edge in extractTransitions(gameIR)) {
            transitionsArray.put(org.json.JSONObject().put("from", edge.from).put("to", edge.to))
        }
        json.put("transitions", transitionsArray)

        // Emit tile decoder config — default decoders for all games
        val tileDecodersObj = org.json.JSONObject()
        tileDecodersObj.put("bg", org.json.JSONObject().put("type", "gbdk_offset"))
        tileDecodersObj.put("win", org.json.JSONObject().put("type", "direct_ascii"))
        json.put("tileDecoders", tileDecodersObj)

        return json.toString(2)
    }

    companion object {
        private val TERMINAL_SCENE_PATTERNS =
            setOf("gameover", "game_over", "win", "victory", "defeat", "lose")

        /** Maps GBDK button constant names to human-readable button names. */
        private val GBDK_BUTTON_NAMES =
            mapOf(
                "J_UP" to "UP",
                "J_DOWN" to "DOWN",
                "J_LEFT" to "LEFT",
                "J_RIGHT" to "RIGHT",
                "J_A" to "A",
                "J_B" to "B",
                "J_START" to "START",
                "J_SELECT" to "SELECT",
            )

        /** Maps input function names to their interaction type label. */
        private val INPUT_FUNCTION_TYPES =
            mapOf(
                "dpad_held" to "held",
                "button_held" to "held",
                "dpad_pressed" to "pressed",
                "button_pressed" to "pressed",
            )
    }

    /** Intermediate representation for a control mapping within a scene. */
    private data class ControlMapping(val button: String, val type: String)

    /** Intermediate representation for a scene-to-scene navigation edge. */
    private data class SceneTransition(val from: String, val to: String)

    /**
     * Extracts per-scene input control mappings from the game IR.
     *
     * Walks each scene's enter, frame, and exit scripts recursively (depth-first into [IfOp]
     * branches). For each [IfOp] whose condition is a [CallExpr] with a function name in the set of
     * input functions (`dpad_held`, `dpad_pressed`, `button_held`, `button_pressed`), extracts the
     * button from the first arg (a [VarRef] with a GBDK constant name like `J_UP`).
     *
     * Returns a map of scene ID to list of [ControlMapping]s. Scenes with no input ops are omitted
     * from the result.
     */
    private fun extractControls(game: GameIR): Map<String, List<ControlMapping>> {
        val result = linkedMapOf<String, LinkedHashSet<ControlMapping>>()

        fun walkOps(sceneId: String, ops: List<ScriptOp>) {
            for (op in ops) {
                when (op) {
                    is IfOp -> {
                        val condition = op.condition
                        if (condition is CallExpr) {
                            val interactionType = INPUT_FUNCTION_TYPES[condition.function]
                            if (interactionType != null) {
                                val arg = condition.args.firstOrNull()
                                if (arg is VarRef) {
                                    val buttonName = GBDK_BUTTON_NAMES[arg.name]
                                    if (buttonName != null) {
                                        result
                                            .getOrPut(sceneId) { linkedSetOf() }
                                            .add(ControlMapping(buttonName, interactionType))
                                    }
                                }
                            }
                        }
                        walkOps(sceneId, op.then)
                        walkOps(sceneId, op.otherwise)
                    }
                    is WhileOp -> walkOps(sceneId, op.body)
                    is ForOp -> walkOps(sceneId, op.body)
                    is FadeOp -> walkOps(sceneId, op.after)
                    is PoolDestroyActor -> walkOps(sceneId, op.deathCallbackOps)
                    is PoolForEachActive -> walkOps(sceneId, op.body)
                    else -> {}
                }
            }
        }

        for (scene in game.scenes) {
            walkOps(scene.id, scene.enterOps)
            walkOps(scene.id, scene.frameOps)
            walkOps(scene.id, scene.exitOps)
        }

        return result.mapValues { it.value.toList() }
    }

    /**
     * Extracts scene-to-scene navigation transitions from the game IR.
     *
     * Walks each scene's enter, frame, and exit scripts recursively. For each [NavigateTo] op
     * found, records a [SceneTransition] from the current scene to the target scene. Deduplicates
     * edges so each unique (from, to) pair appears only once.
     *
     * Returns a list of [SceneTransition]s.
     */
    private fun extractTransitions(game: GameIR): List<SceneTransition> {
        val seen = linkedSetOf<SceneTransition>()

        fun walkOps(sceneId: String, ops: List<ScriptOp>) {
            for (op in ops) {
                when (op) {
                    is NavigateTo -> seen.add(SceneTransition(sceneId, op.sceneId))
                    is IfOp -> {
                        walkOps(sceneId, op.then)
                        walkOps(sceneId, op.otherwise)
                    }
                    is WhileOp -> walkOps(sceneId, op.body)
                    is ForOp -> walkOps(sceneId, op.body)
                    is FadeOp -> walkOps(sceneId, op.after)
                    is PoolDestroyActor -> walkOps(sceneId, op.deathCallbackOps)
                    is PoolForEachActive -> walkOps(sceneId, op.body)
                    else -> {}
                }
            }
        }

        for (scene in game.scenes) {
            walkOps(scene.id, scene.enterOps)
            walkOps(scene.id, scene.frameOps)
            walkOps(scene.id, scene.exitOps)
        }

        return seen.toList()
    }

    /**
     * Recursively walks a list of [ScriptOp]s and collects literal display text strings.
     *
     * Includes text from [PrintAt], [PrintCentered], [PrintAligned], and [PrintOp] (when it has no
     * format values). Leading and trailing whitespace is trimmed so that display-padded strings
     * like `" SCORE! "` normalize to their semantic content (`"SCORE!"`). Skips strings that are
     * empty after trimming and deduplicates.
     */
    private fun collectTexts(ops: List<ScriptOp>): List<String> {
        val seen = linkedSetOf<String>()

        fun addText(raw: String) {
            val normalized = raw.trim()
            if (normalized.isNotEmpty()) seen.add(normalized)
        }

        fun walk(ops: List<ScriptOp>) {
            for (op in ops) {
                when (op) {
                    is PrintAt -> addText(op.text)
                    is PrintCentered -> addText(op.text)
                    is PrintAligned -> addText(op.text)
                    is PrintOp -> if (op.values.isEmpty()) addText(op.text)
                    is IfOp -> {
                        walk(op.then)
                        walk(op.otherwise)
                    }
                    is WhileOp -> walk(op.body)
                    is ForOp -> walk(op.body)
                    is FadeOp -> walk(op.after)
                    is PoolDestroyActor -> walk(op.deathCallbackOps)
                    is PoolForEachActive -> walk(op.body)
                    else -> {} // No nested ops or text
                }
            }
        }

        walk(ops)
        return seen.toList()
    }

    /** Convert [SourceMapCollector] mappings into v2 source map JSON for a given [CFile]. */
    private fun buildSourceMapJson(
        cFile: CFile,
        collector: SourceMapCollector,
        gameIR: GameIR,
    ): String {
        val builder =
            SourceMapBuilder(
                gameName = gameIR.name,
                cFile = cFile.name,
                version = "2.0",
                bankNumber = cFile.bank,
            )
        for (mapping in collector.mappings) {
            builder.addMapping(
                cLine = mapping.cLine,
                location =
                    io.github.gbkt.core.SourceLocation(
                        file = mapping.sourceLocation.file,
                        line = mapping.sourceLocation.line,
                        column = mapping.sourceLocation.col,
                    ),
                symbol = mapping.symbol,
                irNodeType = mapping.irNodeType,
            )
        }
        return builder.build().toJson()
    }

    // =========================================================================
    // File construction
    // =========================================================================

    private fun buildCFiles(gameIR: GameIR): List<CFile> {
        // Compute zone-to-bank allocation before building files so codegen can emit SWITCH_ROM
        val bankAllocation = allocateZoneBanks(gameIR)
        val homeFile = buildHomeFile(gameIR, bankAllocation)
        val sceneFile = buildSceneFile(gameIR)
        val headerFile = buildHeaderFile(gameIR, homeFile, sceneFile, bankAllocation)
        val tilemapBankFiles = buildTilemapBankFiles(gameIR, bankAllocation)
        return listOf(homeFile, sceneFile, headerFile) + tilemapBankFiles
    }

    // =========================================================================
    // Zone tilemap data — banked const arrays for each ZoneIR (Plan 06.7-09)
    // =========================================================================

    /**
     * Estimate the byte size of a zone's tilemap data for bank allocation purposes.
     *
     * Returns the actual tileData size, or 1 for empty zones (placeholder array).
     */
    private fun zoneTileDataSize(zone: io.github.gbkt.core.ir.ZoneIR): Int =
        if (zone.tileData.isEmpty()) 1 else zone.tileData.size

    /**
     * Auto-allocate zone tilemap data across ROM banks using first-fit bin-packing.
     *
     * Algorithm:
     * - Zones are sorted largest-first for better bin-packing efficiency.
     * - If [ZoneIR.bankOverride] is set, the zone is placed in that bank (warning logged).
     * - Otherwise, the zone is placed in the first bank with enough remaining capacity.
     * - A new bank is opened when no existing bank fits the zone.
     * - Starting bank is configurable via [CartridgeConfig] (default = 2).
     *
     * Bank 0 (HOME) and bank 1 (scenes) are reserved and never used for tilemap data.
     *
     * @param gameIR Game IR containing zones and cartridge config.
     * @return Map of zone ID to allocated bank number.
     * @throws IllegalStateException if a zone exceeds the maximum bank capacity (16KB).
     */
    internal fun allocateZoneBanks(gameIR: io.github.gbkt.core.ir.GameIR): Map<String, Int> {
        if (gameIR.zones.isEmpty()) return emptyMap()

        val bankMaxBytes = 16384 // 16KB per ROM bank (standard GBDK MBC5)
        val tilemapBankStart = 2 // Banks 0 (HOME) and 1 (scenes) are reserved

        // Validate: no zone exceeds single bank capacity
        for (zone in gameIR.zones) {
            val size = zoneTileDataSize(zone)
            if (size > bankMaxBytes) {
                error(
                    "Zone '${zone.id}' tilemap data ($size bytes) exceeds max bank capacity " +
                        "($bankMaxBytes bytes). Split zone into smaller areas."
                )
            }
        }

        // Sort by size descending for better bin-packing (largest zones allocated first)
        val sortedZones = gameIR.zones.sortedByDescending { zoneTileDataSize(it) }

        val allocation = mutableMapOf<String, Int>()
        // Track remaining capacity per bank: bankNumber → remainingBytes
        val bankRemaining = mutableMapOf<Int, Int>()
        var nextBank = tilemapBankStart

        for (zone in sortedZones) {
            val size = zoneTileDataSize(zone)
            val override = zone.bankOverride
            if (override != null) {
                // Manual override: trust developer, log warning
                println(
                    "WARNING: Zone '${zone.id}': manual bank override bank($override). " +
                        "Trusting developer."
                )
                allocation[zone.id] = override
                // Update remaining capacity for manual override bank (may go negative if user
                // over-fills)
                bankRemaining[override] = (bankRemaining[override] ?: bankMaxBytes) - size
            } else {
                // Auto-allocate: find first bank with enough remaining capacity
                val foundBank =
                    (tilemapBankStart until nextBank).firstOrNull { b ->
                        (bankRemaining[b] ?: bankMaxBytes) >= size
                    }
                if (foundBank != null) {
                    allocation[zone.id] = foundBank
                    bankRemaining[foundBank] = (bankRemaining[foundBank] ?: bankMaxBytes) - size
                } else {
                    // Open a new bank
                    allocation[zone.id] = nextBank
                    bankRemaining[nextBank] = bankMaxBytes - size
                    nextBank++
                }
            }
        }

        return allocation
    }

    /**
     * Build `CFile` objects for zone tilemap data, one per allocated bank.
     *
     * Each [CFile] contains the `const UINT8 _zone_{id}_tiles[]` declarations for all zones
     * allocated to that bank. The [CFile.bank] field carries the bank number, which [CEmitter] uses
     * to emit `#pragma bank N` at the top of the file.
     *
     * Zones with no tileData get a 1-element placeholder to avoid zero-size C89 violations.
     *
     * @param gameIR Game IR with zones.
     * @param bankAllocation Map from zone ID to allocated bank number (from [allocateZoneBanks]).
     * @return List of [CFile] objects, one per bank containing at least one zone.
     */
    internal fun buildTilemapBankFiles(
        gameIR: io.github.gbkt.core.ir.GameIR,
        bankAllocation: Map<String, Int>,
    ): List<CFile> {
        if (gameIR.zones.isEmpty()) return emptyList()

        // Group zones by bank
        val zonesByBank = mutableMapOf<Int, MutableList<io.github.gbkt.core.ir.ZoneIR>>()
        for (zone in gameIR.zones) {
            val bank = bankAllocation[zone.id] ?: continue
            zonesByBank.getOrPut(bank) { mutableListOf() } += zone
        }

        return zonesByBank.entries
            .sortedBy { it.key }
            .map { (bankNum, zones) ->
                val tileArrays =
                    zones.map { zone ->
                        val zoneSanitized = zone.id.replace('-', '_').replace(' ', '_')
                        val tileData = zone.tileData
                        val initValues =
                            if (tileData.isEmpty()) {
                                "0" // placeholder for zero-size array
                            } else {
                                tileData.joinToString(", ") { b -> (b and 0xFF).toString() }
                            }
                        val arraySize = if (tileData.isEmpty()) 1 else tileData.size
                        CVarDecl(
                            name = "_zone_${zoneSanitized}_tiles",
                            type =
                                io.github.gbkt.backend.gbdk.codegen.ast.CArray(
                                    CConst(CU8),
                                    arraySize,
                                ),
                            initializer = CRawExpr("{ $initValues }"),
                        )
                    }
                CFile(
                    name = "zone_bank$bankNum.c",
                    bank = bankNum,
                    includes = listOf("\"game.h\""),
                    variables = tileArrays,
                )
            }
    }

    /**
     * Generate `TILESET_<id>` #define constants for each [ZoneIR] in [gameIR].
     *
     * Tileset IDs are sequential from 1 (0xFF is reserved for "no tileset loaded"). Zone tile array
     * declarations are now in bank files (see [buildTilemapBankFiles]), not in the HOME bank.
     *
     * @return List of [CDefine] for zone tileset ID constants.
     */
    private fun buildZoneDefines(gameIR: io.github.gbkt.core.ir.GameIR): List<CDefine> =
        gameIR.zones.mapIndexed { idx, zone ->
            val zoneSanitized = zone.id.replace('-', '_').replace(' ', '_')
            CDefine("TILESET_${zoneSanitized.uppercase()}", "${idx + 1}")
        }

    /**
     * Generate `const UINT8 _zone_{id}_tiles[]` declarations for each [ZoneIR] in [gameIR].
     *
     * Each zone's [ZoneIR.tileData] becomes a const byte array in the HOME bank. Zone tilemap data
     * is generated as [CVarDecl] with [CArray] type and [CRawExpr] initializer. Zones with empty
     * tileData get a 1-element placeholder array to avoid zero-size array C89 violations.
     *
     * Also emits `TILESET_<id>` #define constants for the zone tileset IDs (sequential from 1).
     *
     * @return Pair of (zone tile array declarations, zone tileset #define constants)
     * @deprecated Zone tile arrays are now placed in banked CFiles via [buildTilemapBankFiles].
     *   This method is retained for backwards compatibility with tests that do not use banking.
     */
    private fun buildZoneData(
        gameIR: io.github.gbkt.core.ir.GameIR
    ): Pair<List<CVarDecl>, List<CDefine>> {
        val zoneArrays = mutableListOf<CVarDecl>()
        val zoneDefines = mutableListOf<CDefine>()
        for ((idx, zone) in gameIR.zones.withIndex()) {
            val zoneSanitized = zone.id.replace('-', '_').replace(' ', '_')
            // Tile data array: use actual data or placeholder
            val tileData = zone.tileData
            val initValues =
                if (tileData.isEmpty()) {
                    "0" // placeholder for zero-size array
                } else {
                    tileData.joinToString(", ") { b -> (b and 0xFF).toString() }
                }
            val arraySize = if (tileData.isEmpty()) 1 else tileData.size
            zoneArrays +=
                CVarDecl(
                    name = "_zone_${zoneSanitized}_tiles",
                    type = io.github.gbkt.backend.gbdk.codegen.ast.CArray(CConst(CU8), arraySize),
                    initializer = CRawExpr("{ $initValues }"),
                )
            // Tileset ID constant: TILESET_<ZONE_ID> = idx + 1 (0xFF reserved for "none")
            zoneDefines += CDefine("TILESET_${zoneSanitized.uppercase()}", "${idx + 1}")
        }
        return zoneArrays to zoneDefines
    }

    // =========================================================================
    // Struct typedef declarations
    // =========================================================================

    /**
     * Generates [CTypedef] declarations for all [StructDef]s registered in the game IR.
     *
     * Each struct produces:
     * ```c
     * typedef struct { <field declarations> } <name>;
     * ```
     *
     * These typedefs are emitted before collection array declarations so that collections that hold
     * struct elements reference a fully-defined type.
     */
    private fun buildStructTypedefs(structs: List<StructDef>): List<CTypedef> =
        structs.map { struct ->
            val fields =
                struct.fields.joinToString("") { field ->
                    // Map VarType to GBDK C type string via CollElementType.Primitive.cTypeName
                    val cType =
                        io.github.gbkt.core.ir.CollElementType.Primitive(field.type).cTypeName
                    "    $cType ${field.name};\n"
                }
            CTypedef(name = struct.name, definition = "struct {\n${fields}}")
        }

    // =========================================================================
    // main.c — HOME bank (bank 0)
    // =========================================================================

    private fun buildHomeFile(
        gameIR: GameIR,
        bankAllocation: Map<String, Int> = emptyMap(),
    ): CFile {
        val sceneIds = gameIR.scenes.map { it.id }
        val sceneEnum = SceneVisitor.generateSceneEnum(sceneIds)
        val actorVars = gameIR.actors.flatMap { ActorVisitor.visit(it) }
        val globalVars =
            gameIR.variables.map { varDef ->
                CVarDecl(
                    name = "_${varDef.name}",
                    type = varTypeToC(varDef.type),
                    initializer = CLiteral(varDef.initialValue),
                )
            }
        val globalArrayVars =
            gameIR.arrays.map { arrayDef ->
                CVarDecl(
                    name = "_${arrayDef.name}",
                    type = CArray(varTypeToC(arrayDef.elementType), arrayDef.size),
                    initializer = null,
                )
            }
        val startSceneConst = gameIR.startScene?.uppercase()?.let { "SCENE_$it" } ?: "0"
        val currentSceneVar =
            CVarDecl(name = "current_scene", type = CU8, initializer = CVar(startSceneConst))

        // System-specific global variables (combat state, exploration state, camera state)
        val systemGlobalVars = buildSystemGlobalVars(gameIR)

        // Joypad state variables for input tracking
        val joypadVars =
            listOf(
                CVarDecl(name = "__joypad", type = CU8, initializer = CLiteral(0)),
                CVarDecl(name = "__joypad_prev", type = CU8, initializer = CLiteral(0)),
            )
        // WaitFrames state machine counter — decremented each frame, function returns early while >
        // 0
        val waitCounterVar = CVarDecl(name = "_wait_counter", type = CU8, initializer = CLiteral(0))
        // Scene tileset reuse guard: tracks current loaded tileset ID.
        // 0xFF = no tileset loaded (initial state). When consecutive scenes share the same
        // tilesetRef, the tileset load is skipped — only loads when the tileset changes.
        val currentTilesetIdVar =
            CVarDecl(name = "_current_tileset_id", type = CU8, initializer = CRawExpr("0xFF"))
        // Sound driver channel state arrays
        val soundVisitor = SoundVisitor(gameIR)
        val soundDriverVars = soundVisitor.buildSoundDriverGlobals()

        // Per-actor animation variable declarations (state machine state/frame/counter)
        val animationVars =
            gameIR.actors.flatMap { actor ->
                ActorVisitor.generateAnimationVars(actor) +
                    ActorVisitor.generateSimpleAnimationVars(actor)
            }

        // Per-actor animation state machine #define constants
        val animationDefines =
            gameIR.actors.flatMap { actor -> ActorVisitor.generateAnimationDefines(actor) }

        // Per-actor physics velocity variables (INT8 _actorId_vx, _actorId_vy)
        val physicsVars = gameIR.actors.flatMap { actor -> ActorVisitor.generatePhysicsVars(actor) }

        // Per-actor physics #define constants (ACCEL_X/Y, GRAVITY, MAX_FALL, BOUNCE)
        val physicsDefines =
            gameIR.actors.flatMap { actor -> ActorVisitor.generatePhysicsDefines(actor) }

        // Per-actor smooth movement variables (INT8 _actorId_vx, _actorId_vy)
        val smoothVars =
            gameIR.actors.flatMap { actor -> ActorVisitor.generateSmoothMovementVars(actor) }

        // Per-actor smooth movement #define constants (ACCEL_X, FRICTION_X, SPEED_X)
        val smoothDefines =
            gameIR.actors.flatMap { actor -> ActorVisitor.generateSmoothMovementDefines(actor) }

        // Per-actor waypoint patrol route arrays and index (const UINT8 _wp_x[], _wp_y[], _wp_idx)
        val waypointVars =
            gameIR.actors.flatMap { actor -> ActorVisitor.generateWaypointVars(actor) }

        // Per-actor waypoint count #define (_wp_count)
        val waypointDefines =
            gameIR.actors.flatMap { actor -> ActorVisitor.generateWaypointDefines(actor) }

        // Pathfinding system #define constants (PF_GRID_SIZE)
        // ScriptOpVisitor.visitPathfindStep uses PF_GRID_SIZE to divide pixel→tile coordinates.
        val pathfindingDefines =
            gameIR.systems
                .filterIsInstance<PathfindingSystem>()
                .take(1) // Only one pathfinding system per game
                .map { sys -> CDefine("PF_GRID_SIZE", sys.gridSize.toString()) }

        // Dialog globals: _dialog_speed (default typewriter speed) + _vwf_char_widths (VWF table)
        val dialogVisitor = DialogVisitor(gameIR)
        val dialogGlobalVars = dialogVisitor.buildDialogGlobalVars()

        // HUD globals: _hud_<id>_visible (UINT8) + _hud_<id>_<elem>_prev per element (0xFF
        // sentinel)
        val hudVisitor = HudVisitor(gameIR)
        val hudGlobalVars = hudVisitor.buildHudGlobalVars()

        // Deduplicate variables by name — actorVars take priority over systemGlobalVars
        // (e.g., _player_x from ActorVisitor wins over _player_x from ExplorationSystem)
        val allVariablesRaw =
            actorVars +
                animationVars +
                physicsVars +
                smoothVars +
                waypointVars +
                globalVars +
                globalArrayVars +
                joypadVars +
                soundDriverVars +
                systemGlobalVars +
                dialogGlobalVars +
                hudGlobalVars +
                listOf(currentSceneVar, waitCounterVar, currentTilesetIdVar)
        val allVariables = allVariablesRaw.distinctBy { it.name }

        // Input helper functions (button_pressed, button_held, dpad_held, dpad_pressed)
        // Scene function forward declarations live in game.h — no duplicate stubs here.
        val inputHelpers = buildInputHelperFunctions()

        // delay_frames() and dpad_any() C helpers (HOME-bank)
        val delayHelper = buildDelayHelper()
        val dpadAnyHelper = buildDpadAnyHelper()

        // Real sprite helper functions with OAM management bodies
        val spriteHelpers = buildSpriteHelperFunctions()

        // update_sprites() — per-frame OAM sync for all actors with sprites
        val updateSpritesFn = ActorVisitor.generateUpdateSprites(gameIR.actors)

        // Per-actor movement update functions (update_movement_{id}())
        val movementFunctions =
            gameIR.actors.flatMap { actor -> ActorVisitor.generateMovementFunction(actor) }

        // Per-actor animation update functions (update_animation_{id}())
        val exprVisitor = io.github.gbkt.backend.gbdk.codegen.visitor.ExprVisitor(gameIR.actors)
        val animationFunctions =
            gameIR.actors.flatMap { actor ->
                ActorVisitor.generateAnimationFunction(actor, exprVisitor)
            }

        // Sound driver functions — HOME-resident (bank 0) per RESEARCH.md pitfall 4
        val soundFunctions = soundVisitor.buildSoundFunctions()

        // Dialog, menu, HUD, and fade helper functions — HOME-resident
        val dialogFunctions = dialogVisitor.buildDialogFunctions()
        val menuVisitor = MenuVisitor(gameIR)
        val menuFunctions = menuVisitor.buildMenuFunctions()
        val hudFunctions = hudVisitor.buildHudFunctions()
        val fadeFunctions = buildFadeHelpers()

        // Trampoline stubs for banked scene functions
        val trampolineStubs = buildTrampolineStubs(gameIR)

        // System trigger functions — one trigger_{id}() per registered GenericSystem
        val systemFunctions = buildSystemFunctions(gameIR, bankAllocation)

        // Struct typedef declarations — emitted as CTypedef before collection data.
        // Each StructDef produces: typedef struct { <field declarations> } <name>;
        // These must appear before any collection arrays that reference the struct type.
        val structTypedefs = buildStructTypedefs(gameIR.structs)

        // Collection data declarations (raw C) + collection helper functions
        // Generated by GBDKCollectionCodegen and placed after variables (via rawSections).
        // Both data arrays and helper functions are emitted as raw C sections because their
        // structure (multi-array collection patterns) cannot be expressed as typed AST nodes.
        val collectionCodegen = GBDKCollectionCodegen()
        val (collectionDataRaw, collectionFunctionsRaw) =
            collectionCodegen.generateAllCollections(
                hashTables = gameIR.hashTables,
                pools = gameIR.pools,
                ringBuffers = gameIR.ringBuffers,
                fixedSlots = gameIR.fixedSlots,
            )
        // GBC palette data arrays — one `const palette_color_t {name}_pal[4]` per palette.
        // set_bkg_palette() and set_sprite_palette() require these as arguments.
        // Emitted as a raw section because palette_color_t is a GBDK typedef (uint16_t),
        // and we do not have a typed CArray element type for it in our C AST.
        val paletteDataRaw =
            gameIR.palettes
                .joinToString("\n") { palette ->
                    "const palette_color_t ${palette.name}_pal[4] = {${palette.toCArrayLiteral()}};"
                }
                .takeIf { gameIR.palettes.isNotEmpty() }

        val allRawSections = buildList {
            if (collectionDataRaw.isNotEmpty()) add(collectionDataRaw)
            if (collectionFunctionsRaw.isNotEmpty()) add(collectionFunctionsRaw)
            if (paletteDataRaw != null) add(paletteDataRaw)
        }

        // Tile collision system — const arrays + lookup functions (HOME-bank)
        // G2: Scenes with collisionData emit map_<scene>_collision[] and _map_collision_<scene>()
        val collisionVisitor = CollisionVisitor(gameIR)
        val (collisionArrays, collisionFunctionsRaw) = collisionVisitor.buildCollisionCodegen()

        // When exploration systems exist but no scenes have collision data, generate a stub
        // _map_collision(x, y) that always returns 0 (no collision). The exploration movement
        // function calls _map_collision() for walkability checks.
        val hasExplorationSystem =
            gameIR.systems.any { it is io.github.gbkt.core.ir.ExplorationSystem }
        val collisionFunctions =
            if (collisionFunctionsRaw.isEmpty() && hasExplorationSystem) {
                listOf(
                    CFunction(
                        name = "_map_collision",
                        returnType = CU8,
                        params = listOf(CParam("x", CU8), CParam("y", CU8)),
                        body = listOf(CReturn(CLiteral(0))),
                        sectionComment = "Stub collision — no scenes have collision data",
                    )
                )
            } else {
                collisionFunctionsRaw
            }

        // Puzzle object state variables and interaction check functions
        val systemVisitor = GBDKSystemVisitor(gameIR, bankAllocation)
        val (puzzleVars, puzzleFunctions) = systemVisitor.buildPuzzleObjectFunctions(gameIR)

        // Actor pool state variables (_pool_<id>_active[], _pool_<id>_oam_base)
        val actorPoolStateVars = GBDKSystemVisitor.buildActorPoolStateVars(gameIR)

        // Zone tilemap defines — TILESET_<ID> constants (tile arrays are in banked CFiles)
        // When bankAllocation is provided (banking mode), tile arrays live in zone_bankN.c files.
        // When empty (legacy/no-zone mode), we still need backward-compat zone arrays in home.
        val zoneDefines: List<CDefine>
        val zoneArrays: List<CVarDecl>
        if (bankAllocation.isEmpty() && gameIR.zones.isNotEmpty()) {
            // Legacy path: no banking configured, put tile arrays in HOME bank
            val (arrays, defines) = buildZoneData(gameIR)
            zoneArrays = arrays
            zoneDefines = defines
        } else {
            // Banking path: tile arrays are in zone_bankN.c files, only emit defines here
            zoneArrays = emptyList()
            zoneDefines = buildZoneDefines(gameIR)
        }

        // Inventory system: item catalog constants, container globals, PRNG global
        val inventoryItemConstants = buildItemCatalog(gameIR)
        val inventoryContainerGlobals = buildInventoryGlobals(gameIR)
        val inventoryPrngGlobal = InventoryVisitor(gameIR).generatePrngGlobal()
        // _item_names[] const lookup table for item display in menus (null when no items)
        val itemNamesTable = InventoryVisitor(gameIR).generateItemNamesTable()
        // RPG character stat globals (_char_<id>_hp, _char_<id>_level, etc.)
        val rpgCharStatVars = buildRpgCharStatVars(gameIR)
        // RPG combat helper variables (_char_active_sp, _combat_target_idx, etc.)
        val rpgCombatHelperVars = buildRpgCombatHelperVars(gameIR)
        // Status effect state variables (_effect_{id}_active, _duration, _stacks)
        val statusEffectVarDecls = buildStatusEffectVarDecls(gameIR)
        // Story flag variables (_flag_{flagName})
        val flagVarDecls = buildFlagVarDecls(gameIR)
        // Zone object state variables (_sconce_{id}_lit, _lever_{id}_active)
        val zoneObjectVarDecls = buildZoneObjectVarDecls(gameIR)
        // Monster AI state variables (_mon_{id}_hp_pct, _mon_{id}_cd_*, shared RPG vars)
        val monsterAIVarDecls = buildMonsterAIVarDecls(gameIR)

        val allVariablesWithCollision =
            (allVariables +
                    collisionArrays +
                    zoneArrays +
                    inventoryItemConstants +
                    inventoryContainerGlobals +
                    inventoryPrngGlobal +
                    listOfNotNull(itemNamesTable) +
                    rpgCharStatVars +
                    rpgCombatHelperVars +
                    statusEffectVarDecls +
                    flagVarDecls +
                    zoneObjectVarDecls +
                    monsterAIVarDecls +
                    puzzleVars +
                    actorPoolStateVars)
                .distinctBy { it.name }

        // Inventory: container operation functions, use_item dispatchers, drop table functions
        val inventoryFunctions = buildInventoryFunctions(gameIR)

        // Actor pool lifecycle functions (pool_<id>_init, pool_<id>_spawn, pool_<id>_destroy)
        val actorPoolFunctions = GBDKSystemVisitor.buildActorPoolFunctions(gameIR)

        // NPC-NPC collision check functions (per-rule AABB + master dispatcher)
        val npcCollisionFunctions = systemVisitor.buildNpcCollisionFunctions(gameIR)

        // Monster combat engine stubs — generated when RPG monster systems are present.
        // Provides definitions for monster_basic_attack, monster_flee, find_lowest_hp_target,
        // and use_ability_{id} stubs for monster-specific abilities not defined by RpgVisitor.
        val monsterCombatStubs = buildMonsterCombatStubs(gameIR)

        // External function stubs — definitions for functions called via callOp() in zone/scene
        // scripts that are not generated by any visitor. These allow the ROM to link successfully.
        val callOpStubFunctions = buildCallOpStubFunctions(gameIR)

        // navigate_to_scene function
        val navigateFn = buildNavigateToSceneFunction(gameIR)

        // main() function
        val mainFn = buildMainFunction(gameIR)

        // #include directives for sprite asset headers
        // For each actor with a sprite asset reference, include its generated header.
        val spriteIncludes =
            gameIR.actors
                .mapNotNull { actor -> actor.sprite?.assetRef?.path }
                .distinct()
                .map { path ->
                    // Convert "sprites/paddle.png" → "sprites/paddle.h"
                    val headerPath = path.substringBeforeLast('.') + ".h"
                    "\"$headerPath\""
                }

        // Add hUGEDriver.h when any scene uses music ScriptOps (A2)
        val hUGEInclude = if (soundVisitor.hasMusicOps()) listOf("<hUGEDriver.h>") else emptyList()
        // Add <gb/cgb.h> when GBC palettes are defined — provides palette_color_t and set_*_palette
        val cgbHomeInclude = if (gameIR.palettes.isNotEmpty()) listOf("<gb/cgb.h>") else emptyList()
        val allIncludes =
            listOf("<gb/gb.h>", "<stdio.h>", "<stdlib.h>", "<gbdk/console.h>", "\"game.h\"") +
                hUGEInclude +
                cgbHomeInclude +
                spriteIncludes

        // MENU_CURSOR_SPRITE_ID #define — emitted when any menu uses a sprite cursor
        val menuCursorDefines =
            if (gameIR.menus.any { it.cursorSprite != null }) {
                listOf(CDefine("MENU_CURSOR_SPRITE_ID", "${MenuVisitor.MENU_CURSOR_SPRITE_ID}"))
            } else {
                emptyList()
            }

        // MIXER_GROUP_* #define constants — emitted when any audio_mixer GenericSystem exists
        @Suppress("UNCHECKED_CAST")
        val audioMixerDefines =
            gameIR.systems
                .filterIsInstance<GenericSystem>()
                .filter { it.config["type"] == "audio_mixer" }
                .flatMap { system ->
                    val groups =
                        (system.config["groups"] as? List<ChannelGroupDef>)
                            ?: listOf(
                                ChannelGroupDef("music", setOf(1, 2), 7, 0),
                                ChannelGroupDef("sfx", setOf(3, 4), 7, 1),
                                ChannelGroupDef("ui", setOf(3), 7, 2),
                            )
                    groups.mapIndexed { idx, group ->
                        CDefine("MIXER_GROUP_${group.name.uppercase()}", "$idx")
                    }
                }

        // Entity collision #define constants — emitted when any actor has non-PASSTHROUGH config
        val entityCollisionDefines = run {
            val explorationSystem =
                gameIR.systems
                    .filterIsInstance<io.github.gbkt.core.ir.ExplorationSystem>()
                    .firstOrNull()
            if (explorationSystem != null) {
                val collisionActors =
                    gameIR.actors.filter {
                        val ec = it.entityCollision
                        ec != null && ec.mode != EntityCollisionMode.PASSTHROUGH
                    }
                if (collisionActors.isNotEmpty()) {
                    listOf(
                        CDefine("MAX_ENTITIES", "${collisionActors.size}"),
                        CDefine("MAP_SIZE", "129"),
                    )
                } else emptyList()
            } else emptyList()
        }

        // ATB #define constants: ATB_BASE_RATE and ATB_MAX_GAUGE
        // Emitted once when any CombatEngineSystem with combatType==ATB exists.
        // ATB_BASE_RATE: base gauge fill per frame (before agility modifier)
        // ATB_MAX_GAUGE: maximum gauge value (clamp threshold)
        val atbDefines =
            gameIR.systems
                .filterIsInstance<CombatEngineSystem>()
                .filter { it.combatType == io.github.gbkt.core.ir.CombatType.ATB }
                .firstOrNull()
                ?.let { atbSystem ->
                    val cfg = atbSystem.atbConfig
                    listOf(
                        CDefine("ATB_BASE_RATE", "${cfg?.baseGaugeFillRate ?: 4}"),
                        CDefine("ATB_MAX_GAUGE", "${cfg?.maxGauge ?: 255}"),
                    )
                } ?: emptyList()

        // Combat state #define constants — _COMBAT_STATE_INIT=0, _COMBAT_STATE_PLAYER_TURN=1, etc.
        val combatStateDefines = buildCombatStateDefines(gameIR)

        return CFile(
            name = "main.c",
            bank = 0,
            includes = allIncludes,
            defines =
                sceneEnum +
                    animationDefines +
                    physicsDefines +
                    smoothDefines +
                    waypointDefines +
                    pathfindingDefines +
                    menuCursorDefines +
                    audioMixerDefines +
                    zoneDefines +
                    entityCollisionDefines +
                    atbDefines +
                    combatStateDefines,
            typedefs = structTypedefs,
            variables = allVariablesWithCollision,
            rawSections = allRawSections,
            functions =
                inputHelpers +
                    listOf(dpadAnyHelper, delayHelper) +
                    spriteHelpers +
                    listOf(updateSpritesFn) +
                    movementFunctions +
                    animationFunctions +
                    soundFunctions +
                    dialogFunctions +
                    menuFunctions +
                    hudFunctions +
                    fadeFunctions +
                    collisionFunctions +
                    inventoryFunctions +
                    puzzleFunctions +
                    actorPoolFunctions +
                    systemFunctions +
                    npcCollisionFunctions +
                    monsterCombatStubs +
                    callOpStubFunctions +
                    trampolineStubs +
                    listOf(navigateFn, mainFn),
        )
    }

    // =========================================================================
    // bank1.c — Scene functions (bank 1 or scene-assigned bank)
    // =========================================================================

    private fun buildSceneFile(gameIR: GameIR): CFile {
        // Use the bank from the first scene that has a bankSlot, defaulting to 1
        val fileBank = gameIR.scenes.firstOrNull { it.bankSlot != null }?.bankSlot?.bank ?: 1

        // Build tileset ID map: unique tilesetRef paths → sequential IDs (1-based, 0xFF reserved)
        val tilesetIdMap = buildTilesetIdMap(gameIR)

        // Pass actors to SceneVisitor so ExprVisitor can resolve collides() AABB expressions
        val hudVisitor = HudVisitor(gameIR)
        val sceneFunctions =
            gameIR.scenes.flatMap { scene ->
                val functions = SceneVisitor.visit(scene, gameIR.actors)
                // Wire update_movement and update_animation calls into frame functions
                val functionsWithUpdates = addMovementAndAnimationCalls(functions, scene.id, gameIR)
                // Wire HUD update calls into frame functions
                val functionsWithHud = hudVisitor.addHudUpdateCalls(functionsWithUpdates, scene.id)
                // C4: Wrap enter function with tileset reuse guard if scene has a tilesetRef
                val tilesetId = scene.tilesetRef?.let { tilesetIdMap[it.path] }
                if (tilesetId != null) {
                    addTilesetGuardToEnterFunction(functionsWithHud, scene.id, tilesetId)
                } else {
                    functionsWithHud
                }
            }

        // C4: Tileset ID defines for header — emitted in bank1.c as local defines
        val tilesetDefines =
            tilesetIdMap.entries
                .sortedBy { it.value }
                .flatMap { (path, id) ->
                    val sceneName = gameIR.scenes.firstOrNull { it.tilesetRef?.path == path }?.id
                    val defName =
                        "TILESET_ID_${(sceneName ?: path).uppercase().replace('/', '_').replace('.', '_')}"
                    listOf(CDefine(defName, "$id"))
                }

        // Add <gb/cgb.h> when any scene script uses SetPalette (GBC color palette ops).
        // Without this include, set_bkg_palette() and set_sprite_palette() are implicitly
        // declared and reject the 3-parameter form required by GBDK's GBC API.
        val cgbInclude = if (gameIR.hasPaletteOps()) listOf("<gb/cgb.h>") else emptyList()

        return CFile(
            name = "bank1.c",
            bank = fileBank,
            includes = listOf("<stdio.h>", "<gbdk/console.h>", "\"game.h\"") + cgbInclude,
            defines = tilesetDefines,
            functions = sceneFunctions,
        )
    }

    /**
     * Returns true if any scene in the game IR contains a [SetPalette] script operation.
     *
     * Used to conditionally include `<gb/cgb.h>` in bank1.c — required for `set_bkg_palette()` and
     * `set_sprite_palette()` in GBDK GBC mode.
     */
    private fun GameIR.hasPaletteOps(): Boolean =
        scenes.any { scene ->
            (scene.enterOps + scene.frameOps + scene.exitOps).any { op -> op is SetPalette }
        }

    /**
     * Build a map of unique tilesetRef paths to sequential IDs.
     *
     * IDs start at 1 (0xFF is reserved for "no tileset loaded"). Paths are deduplicated so two
     * scenes with the same tilesetRef share the same TILESET_ID constant — enabling the reuse guard
     * to skip reloading when transitioning between scenes with identical tilesets.
     */
    private fun buildTilesetIdMap(gameIR: GameIR): Map<String, Int> {
        val uniquePaths = gameIR.scenes.mapNotNull { it.tilesetRef?.path }.distinct()
        return uniquePaths.mapIndexed { idx, path -> path to (idx + 1) }.toMap()
    }

    /**
     * Wrap the enter function body with a tileset reuse guard.
     *
     * Generated C in the enter function:
     * ```c
     * if (_current_tileset_id != TILESET_ID_<scene>) {
     *     /* tileset load code goes here */
     *     _current_tileset_id = TILESET_ID_<scene>;
     * }
     * // ... rest of enter body
     * ```
     *
     * Only the enter function is modified — frame and exit functions are left unchanged. If no
     * enter function exists (no enterOps), the function list is returned unchanged.
     */
    private fun addTilesetGuardToEnterFunction(
        functions: List<CFunction>,
        sceneId: String,
        tilesetId: Int,
    ): List<CFunction> {
        val tilesetConstant = "TILESET_ID_${sceneId.uppercase()}"
        val guardStatements = buildList {
            add(
                CIf(
                    condition =
                        CBinaryExpr(CVar("_current_tileset_id"), "!=", CVar(tilesetConstant)),
                    thenBody =
                        listOf(
                            CComment("tileset load: set_bkg_data() or equivalent goes here"),
                            CExprStatement(
                                CBinaryExpr(CVar("_current_tileset_id"), "=", CVar(tilesetConstant))
                            ),
                        ),
                )
            )
        }
        return functions.map { fn ->
            if (fn.name == "${sceneId}_enter") {
                fn.copy(body = guardStatements + fn.body)
            } else {
                fn
            }
        }
    }

    /**
     * Prepend `update_movement_{id}()` and `update_animation_{id}()` calls to each scene's frame
     * function body, for actors that have [ActorIR.movementConfig] or non-empty
     * [ActorIR.animationStates].
     *
     * Per-actor update functions are called BEFORE user script ops so that movement/animation state
     * is current when game logic (collision, condition checks) runs this frame.
     *
     * Only actors present in the scene's actor list are considered. When no matching actors exist
     * or no frame function is generated, the function list is returned unchanged.
     */
    private fun addMovementAndAnimationCalls(
        functions: List<CFunction>,
        sceneId: String,
        gameIR: GameIR,
    ): List<CFunction> {
        // Build update call list for all actors with movement or animation config
        val updateCalls = mutableListOf<CStatement>()
        for (actor in gameIR.actors) {
            val actorId =
                io.github.gbkt.backend.gbdk.codegen.visitor.ActorVisitor.sanitizeId(actor.id)
            val mc = actor.movementConfig
            if (mc != null) {
                updateCalls += CExprStatement(CCall("update_movement_$actorId", emptyList()))
            }
            if (actor.animationStates.isNotEmpty() || actor.frameSpeed != null) {
                updateCalls += CExprStatement(CCall("update_animation_$actorId", emptyList()))
            }
        }

        if (updateCalls.isEmpty()) return functions

        // Prepend update calls to the frame function body
        return functions.map { fn ->
            if (fn.name == "${sceneId}_frame") {
                fn.copy(body = updateCalls + fn.body)
            } else {
                fn
            }
        }
    }

    // =========================================================================
    // game.h — Header file (include guards, externs, forward declarations)
    // =========================================================================

    private fun buildHeaderFile(
        gameIR: GameIR,
        homeFile: CFile,
        sceneFile: CFile,
        bankAllocation: Map<String, Int> = emptyMap(),
    ): CFile {
        val sceneIds = gameIR.scenes.map { it.id }
        val sceneEnum = SceneVisitor.generateSceneEnum(sceneIds)

        // Extern declarations for global variables — isExtern = true emits the `extern` keyword
        val actorExterns =
            gameIR.actors.flatMap { actor ->
                val prefix = "_${actor.id}"
                listOf(
                    CVarDecl(name = "${prefix}_x", type = CU8, isExtern = true),
                    CVarDecl(name = "${prefix}_y", type = CU8, isExtern = true),
                )
            }
        val varExterns =
            gameIR.variables.map { varDef ->
                CVarDecl(name = "_${varDef.name}", type = varTypeToC(varDef.type), isExtern = true)
            }
        val arrayExterns =
            gameIR.arrays.map { arrayDef ->
                CVarDecl(
                    name = "_${arrayDef.name}",
                    type = CArray(varTypeToC(arrayDef.elementType), arrayDef.size),
                    isExtern = true,
                )
            }
        val currentSceneExtern = CVarDecl(name = "current_scene", type = CU8, isExtern = true)
        val joypadExterns =
            listOf(
                CVarDecl(name = "__joypad", type = CU8, isExtern = true),
                CVarDecl(name = "__joypad_prev", type = CU8, isExtern = true),
            )
        val waitCounterExtern = CVarDecl(name = "_wait_counter", type = CU8, isExtern = true)
        val soundDriverExterns =
            listOf(
                CVarDecl(name = "_sound_channels", type = CArray(CU8, 4), isExtern = true),
                CVarDecl(name = "_sound_priority", type = CArray(CU8, 4), isExtern = true),
                CVarDecl(name = "_sound_duration", type = CArray(CU8, 4), isExtern = true),
            )

        // Extern declarations for CombatEngineSystem state globals
        // Banked scene functions need to read _combat_state_<id> and _pending_state_<id>
        val combatStateExterns =
            gameIR.systems.filterIsInstance<io.github.gbkt.core.ir.CombatEngineSystem>().flatMap {
                system ->
                val sid = system.id.replace('-', '_').replace(' ', '_')
                listOf(
                    CVarDecl(name = "_combat_state_$sid", type = CU8, isExtern = true),
                    CVarDecl(name = "_pending_state_$sid", type = CU8, isExtern = true),
                )
            }

        // Extern declarations for banked zone tile arrays
        // When zones are allocated to non-zero banks, their tile arrays need extern declarations
        // in game.h so that zone_load_X() functions in main.c can reference them.
        val zoneTileExterns =
            gameIR.zones
                .filter { zone -> (bankAllocation[zone.id] ?: 0) > 0 }
                .map { zone ->
                    val zoneSanitized = zone.id.replace('-', '_').replace(' ', '_')
                    val tileData = zone.tileData
                    val arraySize = if (tileData.isEmpty()) 1 else tileData.size
                    CVarDecl(
                        name = "_zone_${zoneSanitized}_tiles",
                        type =
                            io.github.gbkt.backend.gbdk.codegen.ast.CArray(
                                io.github.gbkt.backend.gbdk.codegen.ast.CConst(CU8),
                                arraySize,
                            ),
                        isExtern = true,
                    )
                }

        val allExterns =
            actorExterns +
                varExterns +
                arrayExterns +
                joypadExterns +
                soundDriverExterns +
                combatStateExterns +
                zoneTileExterns +
                listOf(currentSceneExtern, waitCounterExtern)

        // Extern declarations for actor pool state variables (_pool_<id>_active[],
        // _pool_<id>_oam_base)
        // Mirrors GBDKSystemVisitor.buildActorPoolStateVars() structure for extern visibility.
        val actorPoolExterns =
            gameIR.actorPools.flatMap { pool ->
                val id = pool.id.replace('-', '_').replace(' ', '_')
                val maxSize = pool.config.maxSize
                buildList {
                    add(
                        CVarDecl(
                            name = "_pool_${id}_active",
                            type = CArray(CU8, maxSize),
                            isExtern = true,
                        )
                    )
                    add(CVarDecl(name = "_pool_${id}_oam_base", type = CU8, isExtern = true))
                    for (prop in pool.instanceProperties) {
                        val elemType =
                            when (prop.type) {
                                VarType.U8,
                                VarType.U16 -> CU8
                                VarType.I8,
                                VarType.I16 -> CI8
                            }
                        add(
                            CVarDecl(
                                name = "_pool_${id}_${prop.name}",
                                type = CArray(elemType, maxSize),
                                isExtern = true,
                            )
                        )
                    }
                }
            }

        // Extern declarations for RPG combat helper variables
        // Referenced by RpgVisitor.generateUseAbilityFunction() and generateTargetingStatement()
        val rpgCombatHelperExterns = buildRpgCombatHelperExterns(gameIR)

        // Auto-extract prototypes from HOME file functions —
        // every non-static, non-main function in main.c gets a prototype in game.h.
        // This eliminates manual prototype maintenance and guarantees no function
        // is missing from the header.
        val homeFunctionPrototypes =
            homeFile.functions
                .filter { !it.isStatic && it.name != "main" && !it.isPrototype }
                .map { it.toPrototype() }

        // Auto-extract prototypes from scene file functions —
        // banked scene functions (enter/frame/exit) need BANKED prototypes
        // so HOME-resident trampolines can call them.
        val sceneFunctionPrototypes =
            sceneFile.functions.filter { !it.isStatic && !it.isPrototype }.map { it.toPrototype() }

        // Collection function prototypes — hash tables, pools, ring buffers, fixed slots.
        // These are emitted as raw C strings in main.c (rawSections), not as typed CFunction
        // objects, so the auto-extraction above doesn't cover them. Generate typed prototypes
        // so banked scene code can call collection functions without implicit declarations.
        val collectionPrototypes =
            generateCollectionPrototypes(
                hashTables = gameIR.hashTables,
                pools = gameIR.pools,
                ringBuffers = gameIR.ringBuffers,
                fixedSlots = gameIR.fixedSlots,
            )

        // Extern declarations for GBC palette data arrays.
        // Palette arrays are defined as `const palette_color_t {name}_pal[4]` in main.c.
        // Banked scene functions (bank1.c) need extern visibility to pass them to set_*_palette().
        val paletteExternRaw =
            if (gameIR.palettes.isNotEmpty()) {
                gameIR.palettes.joinToString("\n") { palette ->
                    "extern const palette_color_t ${palette.name}_pal[4];"
                }
            } else {
                null
            }

        // Include <gb/cgb.h> in game.h when palettes are present — provides palette_color_t.
        val cgbHeaderInclude =
            if (gameIR.palettes.isNotEmpty()) listOf("<gb/cgb.h>") else emptyList()

        // Forward declarations for external functions called via CallOp in zone object scripts
        // and scene scripts. SDCC does not support implicit declarations with arguments (error
        // 101).
        val callOpForwardDecls = buildCallOpForwardDecls(gameIR)

        // Combat state #define constants must also be in game.h so that banked scene code
        // (bank1.c includes game.h, not main.c) can reference _COMBAT_STATE_* constants.
        val combatStateDefines = buildCombatStateDefines(gameIR)

        // isHeader=true wraps content in #ifndef GAME_H / #define GAME_H / #endif include guard.
        // Scene defines are inside the guard so #pragma bank is not emitted (bank=0 for header).
        return CFile(
            name = "game.h",
            bank = 0,
            isHeader = true,
            includes = listOf("<gb/gb.h>", "<stdio.h>", "<gbdk/console.h>") + cgbHeaderInclude,
            defines = sceneEnum + combatStateDefines,
            variables = allExterns + actorPoolExterns + rpgCombatHelperExterns,
            rawSections = listOfNotNull(paletteExternRaw, callOpForwardDecls),
            functions = sceneFunctionPrototypes + homeFunctionPrototypes + collectionPrototypes,
        )
    }

    // =========================================================================
    // Trampoline stubs — HOME-resident wrappers for banked scene functions
    // =========================================================================

    /**
     * Generate HOME-bank trampoline stubs for scenes that have a bankSlot with bank > 0.
     *
     * Trampoline pattern:
     * ```c
     * // In HOME bank (main.c) — always accessible
     * void gameplay_enter_trampoline(void) {
     *     gameplay_enter();  // Calls into bank N via BANKED calling convention
     * }
     * ```
     *
     * These thin HOME-resident wrappers allow navigate_to_scene (which lives in HOME) to call
     * BANKED scene functions without triggering direct banked-from-HOME issues. GBDK's
     * `__sdcc_banked_call` stub switches to the target bank when `gameplay_enter()` (BANKED) is
     * called.
     */
    private fun buildTrampolineStubs(gameIR: GameIR): List<CFunction> {
        return gameIR.scenes
            .filter { it.bankSlot != null && it.bankSlot!!.bank > 0 }
            .flatMap { scene -> buildTrampolinesForScene(scene) }
    }

    private fun buildTrampolinesForScene(scene: SceneIR): List<CFunction> {
        val bank = scene.bankSlot!!.bank
        return buildList {
            if (scene.enterOps.isNotEmpty()) {
                add(
                    CFunction(
                        name = "${scene.id}_enter_trampoline",
                        returnType = CVoid,
                        body = listOf(CExprStatement(CCall("${scene.id}_enter", emptyList()))),
                        bank = 0,
                        isBanked = false,
                        sectionComment = "Trampoline: ${scene.id}_enter (bank $bank)",
                    )
                )
            }
            if (scene.frameOps.isNotEmpty()) {
                add(
                    CFunction(
                        name = "${scene.id}_frame_trampoline",
                        returnType = CVoid,
                        body = listOf(CExprStatement(CCall("${scene.id}_frame", emptyList()))),
                        bank = 0,
                        isBanked = false,
                    )
                )
            }
            if (scene.exitOps.isNotEmpty()) {
                add(
                    CFunction(
                        name = "${scene.id}_exit_trampoline",
                        returnType = CVoid,
                        body = listOf(CExprStatement(CCall("${scene.id}_exit", emptyList()))),
                        bank = 0,
                        isBanked = false,
                    )
                )
            }
        }
    }

    // =========================================================================
    // Input helper functions — joypad polling with edge detection
    // =========================================================================

    /**
     * Generate GBDK joypad helper functions for the HOME bank.
     *
     * These helpers wrap GBDK's `joypad()` function to provide the semantics used by the v2 DSL:
     * - `update_joypad()` — call once per frame to latch current and previous state
     * - `button_pressed(mask)` — true if the button was just pressed this frame (rising edge)
     * - `button_held(mask)` — true if the button is currently held
     * - `dpad_held(mask)` — alias for button_held (d-pad and buttons share the same bitmask)
     * - `dpad_pressed(mask)` — alias for button_pressed (rising-edge d-pad check)
     * - `button_released(mask)` — true if the button was just released this frame (falling edge)
     * - `dpad_released(mask)` — alias for button_released (falling-edge d-pad check)
     *
     * The `update_joypad()` call is inserted at the top of the main game loop before frame dispatch
     * so that button state is consistent for the entire frame.
     *
     * Joypad bitmask constants are defined in `<gb/gb.h>`: J_UP, J_DOWN, J_LEFT, J_RIGHT, J_A, J_B,
     * J_SELECT, J_START.
     */
    private fun buildInputHelperFunctions(): List<CFunction> {
        val joypadVar = CVar("__joypad")
        val joypadPrevVar = CVar("__joypad_prev")
        val maskVar = CVar("mask")
        val updateJoypad =
            CFunction(
                name = "update_joypad",
                returnType = CVoid,
                body =
                    listOf(
                        // __joypad_prev = __joypad;
                        CExprStatement(CBinaryExpr(joypadPrevVar, "=", joypadVar)),
                        // __joypad = joypad();
                        CExprStatement(CBinaryExpr(joypadVar, "=", CCall("joypad", emptyList()))),
                    ),
                sectionComment = "Input helpers (joypad polling with edge detection)",
            )
        // button_pressed: rising edge = held now AND NOT held previous frame
        // return (__joypad & mask) & ~(__joypad_prev & mask)
        val buttonPressed =
            CFunction(
                name = "button_pressed",
                returnType = CU8,
                params = listOf(CParam("mask", CU8)),
                body =
                    listOf(
                        CReturn(
                            CBinaryExpr(
                                CBinaryExpr(joypadVar, "&", maskVar),
                                "&",
                                CRawExpr("~(__joypad_prev & mask)"),
                            )
                        )
                    ),
            )
        // button_held: simply check if bit is set in current joypad
        val buttonHeld =
            CFunction(
                name = "button_held",
                returnType = CU8,
                params = listOf(CParam("mask", CU8)),
                body = listOf(CReturn(CBinaryExpr(joypadVar, "&", maskVar))),
            )
        val dpadHeld =
            CFunction(
                name = "dpad_held",
                returnType = CU8,
                params = listOf(CParam("mask", CU8)),
                body = listOf(CReturn(CBinaryExpr(joypadVar, "&", maskVar))),
            )
        val dpadPressed =
            CFunction(
                name = "dpad_pressed",
                returnType = CU8,
                params = listOf(CParam("mask", CU8)),
                body =
                    listOf(
                        CReturn(
                            CBinaryExpr(
                                CBinaryExpr(joypadVar, "&", maskVar),
                                "&",
                                CRawExpr("~(__joypad_prev & mask)"),
                            )
                        )
                    ),
            )
        // button_released: falling edge = NOT held now AND held on previous frame
        // return (~__joypad & mask) & (__joypad_prev & mask)
        val buttonReleased =
            CFunction(
                name = "button_released",
                returnType = CU8,
                params = listOf(CParam("mask", CU8)),
                body =
                    listOf(
                        CReturn(
                            CBinaryExpr(
                                CRawExpr("(~__joypad & mask)"),
                                "&",
                                CBinaryExpr(joypadPrevVar, "&", maskVar),
                            )
                        )
                    ),
            )
        val dpadReleased =
            CFunction(
                name = "dpad_released",
                returnType = CU8,
                params = listOf(CParam("mask", CU8)),
                body =
                    listOf(
                        CReturn(
                            CBinaryExpr(
                                CRawExpr("(~__joypad & mask)"),
                                "&",
                                CBinaryExpr(joypadPrevVar, "&", maskVar),
                            )
                        )
                    ),
            )
        return listOf(
            updateJoypad,
            buttonPressed,
            buttonHeld,
            dpadHeld,
            dpadPressed,
            buttonReleased,
            dpadReleased,
        )
    }

    // =========================================================================
    // Sprite helper functions — real OAM management bodies
    // =========================================================================

    /**
     * Generate real sprite helper functions for OAM management.
     * - `hide_sprites_range(from, to)`: Moves sprites off-screen via `move_sprite(i, 0, 0)` loop.
     *   GBDK has no dedicated hide_sprite API; moving to (0,0) places sprites above/left of screen.
     * - `show_sprites_range(from, to)`: No-op stub — sprites are shown by moving them to valid
     *   positions via `update_sprites()`. Kept for DSL compatibility.
     */
    private fun buildSpriteHelperFunctions(): List<CFunction> {
        return listOf(
            ActorVisitor.generateHideSpritesRange(),
            ActorVisitor.generateShowSpritesRange(),
        )
    }

    // =========================================================================
    // Fade helper functions — palette-based screen fade
    // =========================================================================

    /**
     * Generate `fade_out()` and `fade_in()` palette manipulation functions in HOME bank.
     *
     * Uses BGP_REG and OBP0_REG for Game Boy monochrome palette manipulation. Palette bytes: 0xE4 =
     * all-white → 0x00 = all-black (fade_out).
     */
    private fun buildFadeHelpers(): List<CFunction> {
        // Helper: typed palette register write + wait_vbl_done()
        fun paletteStep(bgpVal: Int, obp0Val: Int): List<CStatement> =
            listOf(
                CExprStatement(CBinaryExpr(CVar("BGP_REG"), "=", CLiteral(bgpVal))),
                CExprStatement(CBinaryExpr(CVar("OBP0_REG"), "=", CLiteral(obp0Val))),
                CExprStatement(CCall("wait_vbl_done", emptyList())),
            )

        val fadeOut =
            CFunction(
                name = "fade_out",
                returnType = CVoid,
                body =
                    buildList {
                        addAll(paletteStep(0xB4, 0xB4)) // bright
                        addAll(paletteStep(0x6C, 0x6C)) // medium
                        addAll(paletteStep(0x24, 0x24)) // dim
                        addAll(paletteStep(0x00, 0x00)) // black
                    },
                sectionComment = "Fade helpers (palette-based screen fade)",
            )

        val fadeIn =
            CFunction(
                name = "fade_in",
                returnType = CVoid,
                body =
                    buildList {
                        addAll(paletteStep(0x24, 0x24)) // dim
                        addAll(paletteStep(0x6C, 0x6C)) // medium
                        addAll(paletteStep(0xB4, 0xB4)) // bright
                        addAll(paletteStep(0xE4, 0xE4)) // normal (0xE4 = default GB palette)
                    },
            )

        return listOf(fadeOut, fadeIn)
    }

    // =========================================================================
    // delay_frames() and dpad_any() HOME-bank C helpers
    // =========================================================================

    /**
     * Generates the `delay_frames(n)` HOME-bank helper function.
     *
     * Emits a counted busy-wait using wait_vbl_done() — blocks for exactly [n] vertical blanks.
     * This is distinct from the WaitFrames state-machine pattern (which returns early each frame).
     * Used by ScriptBuilder.delay() to emit sequential blocking pauses within frame handlers.
     *
     * Generated C:
     * ```c
     * void delay_frames(UINT8 n) {
     *     UINT8 _d;
     *     for (_d = 0; _d < n; _d++) {
     *         wait_vbl_done();
     *     }
     * }
     * ```
     *
     * Note: UINT8 _d is declared as a SEPARATE statement BEFORE the for loop (C89 compliance —
     * GBDK's lcc operates in C89 mode which disallows declarations inside for() init).
     */
    private fun buildDelayHelper(): CFunction {
        val param = CParam("n", CU8)
        val loopVar = CVarDecl("_d", CU8, initializer = null)
        val forLoop =
            CFor(
                init = null, // C89: no decl in for init
                condition = CBinaryExpr(CVar("_d"), "<", CVar("n")),
                increment = CUnaryExpr("++", CVar("_d")),
                body = listOf(CExprStatement(CCall("wait_vbl_done", emptyList()))),
            )
        return CFunction(
            name = "delay_frames",
            params = listOf(param),
            returnType = CVoid,
            body = listOf(loopVar, forLoop), // loopVar BEFORE forLoop
            bank = 0,
            sectionComment = "Timing helpers (blocking busy-wait)",
        )
    }

    /**
     * Generates the `dpad_any()` HOME-bank helper function.
     *
     * Returns non-zero if any d-pad direction (up/down/left/right) is held this frame. Reads from
     * the global __joypad state updated by update_joypad() in main game loop.
     *
     * Generated C:
     * ```c
     * UINT8 dpad_any(void) {
     *     return __joypad & (J_UP|J_DOWN|J_LEFT|J_RIGHT);
     * }
     * ```
     */
    private fun buildDpadAnyHelper(): CFunction {
        // return __joypad & (J_UP|J_DOWN|J_LEFT|J_RIGHT) — any d-pad direction held
        return CFunction(
            name = "dpad_any",
            params = emptyList(),
            returnType = CU8,
            body =
                listOf(
                    CReturn(
                        CBinaryExpr(CVar("__joypad"), "&", CRawExpr("(J_UP|J_DOWN|J_LEFT|J_RIGHT)"))
                    )
                ),
            bank = 0,
        )
    }

    // =========================================================================
    // Inventory — item catalog, container globals, operations, drop tables
    // =========================================================================

    /**
     * Generate compile-time item catalog constants (ITEM_ID, ITEM_STACK, CATEGORY_DEFAULT_STACK).
     */
    private fun buildItemCatalog(gameIR: GameIR): List<CVarDecl> {
        if (gameIR.items.isEmpty() && gameIR.itemCategories.isEmpty()) return emptyList()
        return InventoryVisitor(gameIR).generateItemConstants()
    }

    /** Generate container global variable declarations for all ContainerIR nodes. */
    private fun buildInventoryGlobals(gameIR: GameIR): List<CVarDecl> {
        if (gameIR.containers.isEmpty()) return emptyList()
        return InventoryVisitor(gameIR).generateContainerGlobals()
    }

    // =========================================================================
    // RPG — character stat globals
    // =========================================================================

    /**
     * Generate const stat globals and mutable level/exp variables for all RPG character systems.
     *
     * Collects all [GenericSystem] nodes with `type="rpg_character_system"` from [GameIR.systems]
     * and delegates to [RpgVisitor.generateStatVarDecls] for each one.
     */
    private fun buildRpgCharStatVars(gameIR: GameIR): List<CVarDecl> {
        @Suppress("UNCHECKED_CAST")
        val characterSystems =
            gameIR.systems.filterIsInstance<GenericSystem>().filter {
                (it.config["type"] as? String) == "rpg_character_system"
            }
        if (characterSystems.isEmpty()) return emptyList()
        val visitor = RpgVisitor(gameIR)
        return characterSystems.flatMap { visitor.generateStatVarDecls(it) }
    }

    /**
     * Check whether the game has RPG combat that uses abilities (needing combat helper variables).
     *
     * Returns true when either a `simple_battle` [GenericSystem] or a [CombatEngineSystem] exists
     * that has ability definitions (via rpg_character_system or combat engine abilities).
     */
    @Suppress("UNCHECKED_CAST")
    private fun hasRpgCombatAbilities(gameIR: GameIR): Boolean {
        // Check for simple_battle system
        val hasSimpleBattle =
            gameIR.systems.filterIsInstance<GenericSystem>().any {
                (it.config["type"] as? String) == "simple_battle"
            }
        // Check for CombatEngineSystem
        val hasCombatEngine = gameIR.systems.any { it is io.github.gbkt.core.ir.CombatEngineSystem }
        // Check for rpg_character_system (which defines abilities)
        val hasRpgCharSystem =
            gameIR.systems.filterIsInstance<GenericSystem>().any {
                (it.config["type"] as? String) == "rpg_character_system"
            }
        return (hasSimpleBattle || hasCombatEngine) && hasRpgCharSystem
    }

    /**
     * Generate RPG combat helper variable declarations for the HOME file (main.c).
     *
     * These variables are referenced by [RpgVisitor.generateUseAbilityFunction] and
     * [RpgVisitor.generateTargetingStatement] but were not previously declared. They track the
     * active character's SP and targeting indices during combat.
     */
    private fun buildRpgCombatHelperVars(gameIR: GameIR): List<CVarDecl> {
        if (!hasRpgCombatAbilities(gameIR)) return emptyList()
        return listOf(
            CVarDecl("_char_active_sp", CU8, CLiteral(0)),
            CVarDecl("_combat_target_idx", CU8, CLiteral(0)),
            CVarDecl("_combat_active_enemy_idx", CU8, CLiteral(0)),
            CVarDecl("_combat_active_ally_idx", CU8, CLiteral(0)),
            CVarDecl("_combat_target_all_enemies", CU8, CLiteral(0)),
            CVarDecl("_combat_target_all_allies", CU8, CLiteral(0)),
            CVarDecl("_combat_target_all", CU8, CLiteral(0)),
        )
    }

    /**
     * Generate extern declarations for RPG combat helper variables (for game.h).
     *
     * Mirrors [buildRpgCombatHelperVars] but with `isExtern = true` so that banked scene code can
     * reference these HOME-resident variables.
     */
    private fun buildRpgCombatHelperExterns(gameIR: GameIR): List<CVarDecl> {
        if (!hasRpgCombatAbilities(gameIR)) return emptyList()
        return listOf(
            CVarDecl("_char_active_sp", CU8, isExtern = true),
            CVarDecl("_combat_target_idx", CU8, isExtern = true),
            CVarDecl("_combat_active_enemy_idx", CU8, isExtern = true),
            CVarDecl("_combat_active_ally_idx", CU8, isExtern = true),
            CVarDecl("_combat_target_all_enemies", CU8, isExtern = true),
            CVarDecl("_combat_target_all_allies", CU8, isExtern = true),
            CVarDecl("_combat_target_all", CU8, isExtern = true),
        )
    }

    // =========================================================================
    // RPG — status effect, flag, zone object, and monster AI variable declarations
    // =========================================================================

    /**
     * Generate UINT8 global variable declarations for all status effect state variables.
     *
     * Each `rpg_status_effect` [GenericSystem] produces three UINT8 globals:
     * - `_effect_{id}_active` — 1 when effect is active on the target
     * - `_effect_{id}_duration` — turns remaining
     * - `_effect_{id}_stacks` — stack count (used by INTENSITY stack mode)
     *
     * These variables are referenced by [RpgVisitor.generateApplyEffectFunction] and
     * [RpgVisitor.generateTickEffectFunction] but were not previously declared as globals.
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildStatusEffectVarDecls(gameIR: GameIR): List<CVarDecl> {
        return gameIR.systems
            .filterIsInstance<GenericSystem>()
            .filter { (it.config["type"] as? String) == "rpg_status_effect" }
            .flatMap { system ->
                val def = system.config["def"] as? StatusEffectDef ?: return@flatMap emptyList()
                val id = def.id.replace('-', '_').replace(' ', '_')
                listOf(
                    CVarDecl("_effect_${id}_active", CU8, CLiteral(0)),
                    CVarDecl("_effect_${id}_duration", CU8, CLiteral(0)),
                    CVarDecl("_effect_${id}_stacks", CU8, CLiteral(0)),
                )
            }
    }

    /**
     * Generate UINT8 global variable declarations for all story flag variables.
     *
     * Iterates [GameIR.flags] → [GlobalFlagsIR.pages] → [FlagPageIR.flags] and emits one UINT8
     * global per flag named `_flag_{flagName}`. The page name is NOT part of the variable name — it
     * is only used for SRAM byte grouping.
     *
     * This matches what [io.github.gbkt.core.dsl.ScriptBuilder.setFlag] emits
     * (`Assign("_flag_$flagName", ...)`) and what [GBDKSystemVisitor] references.
     */
    private fun buildFlagVarDecls(gameIR: GameIR): List<CVarDecl> {
        val decls = mutableListOf<CVarDecl>()

        // Flags declared in GlobalFlagsIR pages (via flags { page(...) { flag("name") } })
        for (flagsIR in gameIR.flags) {
            for (page in flagsIR.pages) {
                for (flagName in page.flags) {
                    val sanitized = flagName.replace('-', '_').replace(' ', '_')
                    decls += CVarDecl("_flag_$sanitized", CU8, CLiteral(0))
                }
            }
        }

        // Inline flags on zone objects — usedFlagId and visibleFlagId (NpcObjectIR).
        // These are ad-hoc flags not registered in any GlobalFlagsIR page but referenced as
        // `_flag_{id}` by GBDKSystemVisitor.buildNpcHandlerFunction / buildChestHandlerFunction.
        for (zone in gameIR.zones) {
            for (obj in zone.objects) {
                val usedFlag = obj.usedFlagId
                if (usedFlag != null) {
                    val sanitized = usedFlag.replace('-', '_').replace(' ', '_')
                    decls += CVarDecl("_flag_$sanitized", CU8, CLiteral(0))
                }
                if (obj is NpcObjectIR) {
                    val visibleFlag = obj.visibleFlagId
                    if (visibleFlag != null) {
                        val sanitized = visibleFlag.replace('-', '_').replace(' ', '_')
                        decls += CVarDecl("_flag_$sanitized", CU8, CLiteral(0))
                    }
                }
            }
        }

        return decls
    }

    /**
     * Generate UINT8 global variable declarations for zone sconce and lever objects.
     *
     * For each [SconceObjectIR] in any zone: emits `_sconce_{id}_lit` (UINT8, init 0). For each
     * [LeverObjectIR] in any zone: emits `_lever_{id}_active` (UINT8, init 0).
     *
     * These variables are referenced by [GBDKSystemVisitor.buildSconceHandlerFunction] and
     * [GBDKSystemVisitor.buildLeverHandlerFunction].
     */
    private fun buildZoneObjectVarDecls(gameIR: GameIR): List<CVarDecl> {
        return gameIR.zones.flatMap { zone ->
            zone.objects.mapNotNull { obj ->
                when (obj) {
                    is SconceObjectIR -> {
                        val id = obj.id.replace('-', '_').replace(' ', '_')
                        CVarDecl("_sconce_${id}_lit", CU8, CLiteral(0))
                    }
                    is LeverObjectIR -> {
                        val id = obj.id.replace('-', '_').replace(' ', '_')
                        CVarDecl("_lever_${id}_active", CU8, CLiteral(0))
                    }
                    else -> null
                }
            }
        }
    }

    /**
     * Recursively collect all [CooldownNode.abilityId] values from a behavior tree.
     *
     * Walks the full tree depth-first and returns all abilityId strings found in [CooldownNode]
     * entries. This is used by [buildMonsterAIVarDecls] to emit cooldown globals for abilities
     * registered via `cooldown("id", turns = N) { }` in the AI builder, which register in the
     * behavior tree but NOT in [MonsterDef.abilityCooldowns].
     */
    private fun collectCooldownIds(node: BehaviorNode): List<String> =
        when (node) {
            is CooldownNode -> listOf(node.abilityId) + collectCooldownIds(node.child)
            is SelectorNode -> node.children.flatMap { collectCooldownIds(it) }
            is SequenceNode -> node.children.flatMap { collectCooldownIds(it) }
            is PhaseThresholdNode -> collectCooldownIds(node.tree)
            else -> emptyList()
        }

    /**
     * Generate UINT8 global variable declarations for monster AI state variables.
     *
     * For each `rpg_monster` [GenericSystem]:
     * - `_mon_{id}_hp_pct` (init 100) — used by [io.github.gbkt.rpg.domain.PhaseThresholdNode]
     * - `_mon_{id}_cd_{abilityId}` (init 0) — for each cooldown in [MonsterDef.abilityCooldowns]
     *   AND for each [CooldownNode] found in [MonsterDef.behaviorTree] (behavior-tree cooldowns are
     *   registered in the AI builder tree without being added to `abilityCooldowns`)
     * - `_mon_{id}_last_action` (init 255) — only when [MonsterDef.allowGlobalRepeatPrevention]
     *
     * Also emits three shared RPG combat variables used by monster AI and encounter targeting:
     * - `_combat_difficulty` (init 1) — 0=EASY, 1=NORMAL, 2=HARD
     * - `_player_level` (init 1) — active player's level for level-gated encounter guards
     * - `_char_target_hp` (init 0) — current target HP used by status effect tick functions
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildMonsterAIVarDecls(gameIR: GameIR): List<CVarDecl> {
        val monsterSystems =
            gameIR.systems.filterIsInstance<GenericSystem>().filter {
                (it.config["type"] as? String) == "rpg_monster"
            }
        if (monsterSystems.isEmpty()) return emptyList()

        val vars = mutableListOf<CVarDecl>()

        // Shared RPG combat variables used across all monsters
        vars += CVarDecl("_combat_difficulty", CU8, CLiteral(1))
        vars += CVarDecl("_player_level", CU8, CLiteral(1))
        vars += CVarDecl("_char_target_hp", CU8, CLiteral(0))

        for (system in monsterSystems) {
            val def = system.config["def"] as? MonsterDef ?: continue
            val id = def.id.replace('-', '_').replace(' ', '_')
            vars += CVarDecl("_mon_${id}_hp_pct", CU8, CLiteral(100))

            // Collect cooldown IDs from both abilityCooldowns map AND behavior tree CooldownNodes.
            // Behavior-tree cooldowns (from BehaviorTreeBuilder.cooldown()) are registered in the
            // tree but do NOT automatically populate abilityCooldowns. Both sources must be
            // scanned.
            val cooldownIds = mutableSetOf<String>()
            cooldownIds.addAll(def.abilityCooldowns.keys)
            def.behaviorTree?.let { tree -> cooldownIds.addAll(collectCooldownIds(tree)) }

            for (abilityId in cooldownIds) {
                val cdId = abilityId.replace('-', '_').replace(' ', '_')
                vars += CVarDecl("_mon_${id}_cd_$cdId", CU8, CLiteral(0))
            }
            if (def.allowGlobalRepeatPrevention) {
                vars += CVarDecl("_mon_${id}_last_action", CU8, CLiteral(255))
            }
        }

        return vars
    }

    /**
     * Generate #define constants mapping [CombatStateId] string names to their numeric indices.
     *
     * Built-in states always have fixed indices (match [CombatVisitor.buildCoreStateIndex]):
     * - `_COMBAT_STATE_INIT` = 0
     * - `_COMBAT_STATE_PLAYER_TURN` = 1
     * - `_COMBAT_STATE_ENEMY_TURN` = 2
     * - `_COMBAT_STATE_VICTORY` = 3
     * - `_COMBAT_STATE_DEFEAT` = 4
     *
     * Extended standard turn-based states from `CombatStates.*` that are used by typical RPG battle
     * scenes but are not hard-coded switch cases in the state machine:
     * - `_COMBAT_STATE_TARGET_SELECT` = 5
     * - `_COMBAT_STATE_EXECUTE_ACTION` = 6
     * - `_COMBAT_STATE_FLEEING` = 7
     * - `_COMBAT_STATE_WAITING` = 8
     *
     * Custom states from [CombatEngineSystem.customStates] are assigned sequential indices starting
     * at 9 (or at 5 when the system is not TURN_BASED, which has no extended states).
     *
     * Returns an empty list when no [CombatEngineSystem] exists in [gameIR].
     */
    private fun buildCombatStateDefines(gameIR: GameIR): List<CDefine> {
        val combatSystems = gameIR.systems.filterIsInstance<CombatEngineSystem>()
        if (combatSystems.isEmpty()) return emptyList()

        return combatSystems.flatMap { system ->
            val id = system.id.replace('-', '_').replace(' ', '_')
            val prefix = if (combatSystems.size > 1) "_${id}_COMBAT_STATE" else "_COMBAT_STATE"
            val defines = mutableListOf<CDefine>()
            defines += CDefine("${prefix}_INIT", "0")
            defines += CDefine("${prefix}_PLAYER_TURN", "1")
            defines += CDefine("${prefix}_ENEMY_TURN", "2")
            defines += CDefine("${prefix}_VICTORY", "3")
            defines += CDefine("${prefix}_DEFEAT", "4")
            // Extended turn-based states used by CombatStates.* domain constants.
            // These are referenced as VarRef("COMBAT_STATE_TARGET_SELECT") etc. which the
            // expression visitor prefixes with underscore → _COMBAT_STATE_TARGET_SELECT.
            // They do NOT have dedicated switch cases in the state machine but must be
            // valid C constants for the combat_is_in_state() function comparisons.
            val customOffset: Int
            if (system.combatType == io.github.gbkt.core.ir.CombatType.TURN_BASED) {
                defines += CDefine("${prefix}_TARGET_SELECT", "5")
                defines += CDefine("${prefix}_EXECUTE_ACTION", "6")
                defines += CDefine("${prefix}_FLEEING", "7")
                defines += CDefine("${prefix}_WAITING", "8")
                customOffset = 9
            } else {
                customOffset = 5
            }
            system.customStates.forEachIndexed { index, stateId ->
                val stateName = stateId.id.uppercase()
                defines += CDefine("${prefix}_$stateName", "${customOffset + index}")
            }
            defines
        }
    }

    // =========================================================================
    // Combat engine stub functions — generated when RPG combat system is present
    // =========================================================================

    /**
     * Generate stub function definitions for combat engine helpers called by monster AI functions
     * but not yet generated by [RpgVisitor].
     *
     * These stubs allow the ROM to link successfully. The actual implementations are deferred to a
     * future combat engine codegen pass.
     *
     * Stubs generated (when monsters are present):
     * - `monster_basic_attack()` — perform basic attack on current target
     * - `monster_flee()` — transition combat to FLEEING state
     * - `find_lowest_hp_target()` — return index of enemy with lowest HP
     *
     * Monster ability stubs (`use_ability_{id}`) — one per unique ability referenced in monster
     * behavior trees but not defined by [RpgVisitor.generateAbilityFunctions].
     */
    private fun buildMonsterCombatStubs(gameIR: GameIR): List<CFunction> {
        val monsterSystems =
            gameIR.systems.filterIsInstance<GenericSystem>().filter {
                (it.config["type"] as? String) == "rpg_monster"
            }
        if (monsterSystems.isEmpty()) return emptyList()

        // Collect ability IDs used by monsters in behavior trees that are NOT player abilities.
        // Player abilities have `generateAbilityFunctions` called for them. Monster abilities do
        // not.
        val playerAbilityIds =
            gameIR.systems
                .filterIsInstance<GenericSystem>()
                .filter { (it.config["type"] as? String) == "rpg_ability" }
                .mapNotNull { s ->
                    (s.config["def"] as? io.github.gbkt.rpg.domain.AbilityDef)
                        ?.id
                        ?.replace('-', '_')
                        ?.replace(' ', '_')
                }
                .toSet()

        val monsterAbilityIds = mutableSetOf<String>()
        for (system in monsterSystems) {
            val def = system.config["def"] as? MonsterDef ?: continue
            def.behaviorTree?.let { tree -> collectAbilityCallsFromTree(tree, monsterAbilityIds) }
        }
        val missingAbilityIds = monsterAbilityIds - playerAbilityIds

        val stubs = mutableListOf<CFunction>()

        // Core combat helpers
        stubs +=
            CFunction(
                name = "monster_basic_attack",
                returnType = CVoid,
                body =
                    listOf(CComment("Stub: basic attack — combat engine implementation pending")),
                sectionComment = "Combat engine stubs",
            )
        stubs +=
            CFunction(
                name = "monster_flee",
                returnType = CVoid,
                body = listOf(CComment("Stub: flee — combat engine implementation pending")),
            )
        stubs +=
            CFunction(
                name = "find_lowest_hp_target",
                returnType = CU8,
                body = listOf(CReturn(CLiteral(0)), CComment("Stub — returns first target")),
            )

        // Monster ability stubs — one per unique ability not already provided by player ability
        // codegen
        for (abilityId in missingAbilityIds.sorted()) {
            stubs +=
                CFunction(
                    name = "use_ability_$abilityId",
                    returnType = CVoid,
                    body =
                        listOf(
                            CComment("Stub: monster ability $abilityId — implementation pending")
                        ),
                )
        }
        return stubs
    }

    /** Collect all [UseAbility.abilityId] values from a behavior tree (depth-first). */
    private fun collectAbilityCallsFromTree(
        node: io.github.gbkt.rpg.domain.BehaviorNode,
        result: MutableSet<String>,
    ) {
        when (node) {
            is io.github.gbkt.rpg.domain.ActionNode -> {
                val action = node.action
                if (action is io.github.gbkt.rpg.domain.UseAbility) {
                    result += action.abilityId.replace('-', '_').replace(' ', '_')
                }
            }
            is SelectorNode -> node.children.forEach { collectAbilityCallsFromTree(it, result) }
            is SequenceNode -> node.children.forEach { collectAbilityCallsFromTree(it, result) }
            is CooldownNode -> collectAbilityCallsFromTree(node.child, result)
            is PhaseThresholdNode -> collectAbilityCallsFromTree(node.tree, result)
            else -> Unit
        }
    }

    /**
     * Generate stub function definitions for all external functions called via [CallOp] in zone
     * object and scene scripts.
     *
     * These are user-defined C helpers (e.g. `add_item`, `map_textbox`, floor-specific puzzle
     * callbacks) that are NOT generated by gbkt but must be defined (not just declared) to satisfy
     * the SDCC linker.
     *
     * Returns stub [CFunction] objects with empty bodies. The actual implementations must be
     * provided by the game developer in separate C source files (linked during ROM build).
     *
     * Note: The [buildCallOpForwardDecls] method in [buildHeaderFile] adds declarations to game.h.
     * This method generates the corresponding stub definitions in main.c so the ROM links even
     * without user-provided implementations.
     */
    private fun buildCallOpStubFunctions(gameIR: GameIR): List<CFunction> {
        val allCallOps = mutableListOf<CallOp>()
        for (zone in gameIR.zones) {
            for (obj in zone.objects) {
                allCallOps += collectCallOpsFromScripts(collectZoneObjectScripts(obj))
            }
        }
        for (scene in gameIR.scenes) {
            allCallOps += collectCallOpsFromScripts(scene.enterOps)
            allCallOps += collectCallOpsFromScripts(scene.frameOps)
            allCallOps += collectCallOpsFromScripts(scene.exitOps)
        }
        if (allCallOps.isEmpty()) return emptyList()

        // Functions already generated by the pipeline — exclude from stubs
        val pipelineGeneratedFunctions =
            setOf(
                "delay_frames",
                "dpad_any",
                "dpad_held",
                "button_pressed",
                "fade_in",
                "fade_out",
                "navigate_to_scene",
                "hide_sprites_range",
                "show_sprites_range",
            )

        // Deduplicate by function name, excluding pipeline-generated functions
        val seen = linkedMapOf<String, CallOp>()
        for (callOp in allCallOps) {
            if (callOp.function !in pipelineGeneratedFunctions) {
                seen.putIfAbsent(callOp.function, callOp)
            }
        }
        if (seen.isEmpty()) return emptyList()

        return seen.map { (name, callOp) ->
            val params =
                if (callOp.args.isEmpty()) {
                    emptyList()
                } else {
                    callOp.args.mapIndexed { i, arg ->
                        val type = if (arg is StringLiteral) CPointer(CU8) else CU8
                        CParam("p$i", type)
                    }
                }
            CFunction(
                name = name,
                returnType = CVoid,
                params = params,
                body = listOf(CComment("Stub: external function — provide implementation")),
                sectionComment = if (seen.keys.first() == name) "External function stubs" else null,
            )
        }
    }

    // =========================================================================
    // CallOp forward declarations
    // =========================================================================

    /**
     * Recursively collect all [CallOp] nodes from a list of script operations.
     *
     * Descends into [IfOp], [WhileOp], and [ForOp] bodies so that CallOps nested inside conditional
     * branches are included.
     */
    private fun collectCallOpsFromScripts(scripts: List<ScriptOp>): List<CallOp> {
        val result = mutableListOf<CallOp>()
        for (op in scripts) {
            when (op) {
                is CallOp -> result += op
                is IfOp -> {
                    result += collectCallOpsFromScripts(op.then)
                    result += collectCallOpsFromScripts(op.otherwise)
                }
                is WhileOp -> result += collectCallOpsFromScripts(op.body)
                is ForOp -> result += collectCallOpsFromScripts(op.body)
                else -> Unit
            }
        }
        return result
    }

    /**
     * Collect all scripts from a [ZoneObjectIR], including subtype-specific callbacks.
     * - [SconceObjectIR]: onInteract + onLit + onExtinguished
     * - [LeverObjectIR]: onInteract + onActivate + onDeactivate
     * - Others: onInteract only
     */
    private fun collectZoneObjectScripts(obj: ZoneObjectIR): List<ScriptOp> =
        when (obj) {
            is SconceObjectIR -> obj.onInteract + obj.onLit + obj.onExtinguished
            is LeverObjectIR -> obj.onInteract + obj.onActivate + obj.onDeactivate
            else -> obj.onInteract
        }

    /**
     * Generate forward declarations for all external functions called via [CallOp] throughout the
     * game's zone objects and scene scripts.
     *
     * These are user-defined C helpers (e.g. `add_item`, `map_textbox`, floor-specific puzzle
     * callbacks) that are NOT generated by gbkt but must be declared before use — SDCC does not
     * support implicit function declarations with arguments (error 101).
     *
     * Returns a raw C string section suitable for inclusion in [CFile.rawSections], or null if no
     * CallOps are present.
     *
     * Parameter type inference:
     * - Arg is [StringLiteral] → `const char*`
     * - Any other arg → `UINT8`
     *
     * Deduplication: only one prototype per unique function name (uses first occurrence signature).
     */
    private fun buildCallOpForwardDecls(gameIR: GameIR): String? {
        // Collect all CallOps from zone object scripts (onInteract, onLit, onExtinguished,
        // onActivate, onDeactivate) and from scene lifecycle scripts.
        val allCallOps = mutableListOf<CallOp>()

        for (zone in gameIR.zones) {
            for (obj in zone.objects) {
                allCallOps += collectCallOpsFromScripts(collectZoneObjectScripts(obj))
            }
        }
        for (scene in gameIR.scenes) {
            allCallOps += collectCallOpsFromScripts(scene.enterOps)
            allCallOps += collectCallOpsFromScripts(scene.frameOps)
            allCallOps += collectCallOpsFromScripts(scene.exitOps)
        }

        if (allCallOps.isEmpty()) return null

        // Deduplicate by function name — use first occurrence to infer parameter types.
        val seen = linkedMapOf<String, CallOp>()
        for (callOp in allCallOps) {
            seen.putIfAbsent(callOp.function, callOp)
        }

        // Skip functions that are defined in the generated output (auto-prototyped by the
        // pipeline).
        // These are functions whose names are handled by visitors (e.g. use_ability_*, navigate_*).
        // We only need declarations for truly external (user-defined) functions.
        // Heuristic: generated functions never start with user-defined prefixes.
        // We cannot exclude them statically here — SDCC will simply see a matching prototype and
        // not complain. Duplicate prototypes are valid C89.

        val lines = mutableListOf<String>()
        lines += "// Forward declarations for external functions called via callOp()"
        for ((name, callOp) in seen) {
            val params =
                if (callOp.args.isEmpty()) {
                    "void"
                } else {
                    callOp.args
                        .mapIndexed { i, arg ->
                            if (arg is StringLiteral) "UINT8* p$i" else "UINT8 p$i"
                        }
                        .joinToString(", ")
                }
            lines += "void $name($params);"
        }
        return lines.joinToString("\n")
    }

    /** Generate all inventory operation functions for all containers and drop tables. */
    private fun buildInventoryFunctions(gameIR: GameIR): List<CFunction> {
        if (gameIR.containers.isEmpty() && gameIR.dropTables.isEmpty()) return emptyList()
        val visitor = InventoryVisitor(gameIR)
        val functions = mutableListOf<CFunction>()
        for (container in gameIR.containers) {
            functions += visitor.generateContainerFunctions(container)
            functions += visitor.generateUseItemFunction(container)
        }
        functions += visitor.generatePrngFunction()
        functions += visitor.generateDropTableFunctions()
        return functions
    }

    // =========================================================================
    // System trigger functions — HOME-bank stubs for GenericSystem dispatch
    // =========================================================================

    /**
     * Generate global variable declarations for system-specific state.
     *
     * For each typed system:
     * - [io.github.gbkt.core.ir.CameraSystem]: `_camera_x`, `_camera_y`, `_camera_target`,
     *   `_camera_shake_intensity`, `_camera_shake_timer`
     * - [io.github.gbkt.core.ir.ExplorationSystem]: `_player_x`, `_player_y`, `_current_floor`
     * - [GenericSystem] with type="simple_battle": `_combat_state_{id}` (UINT8, init=0)
     *
     * Genre visitors discovered via [ServiceLoader] take priority for [GenericSystem] types. When a
     * [GenreSystemVisitor] returns [canHandle] = true for a system type, its [CVarDecl] list is
     * used instead of the built-in GenericSystem dispatch.
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildSystemGlobalVars(gameIR: GameIR): List<CVarDecl> {
        val genreVisitors = ServiceLoader.load(GenreSystemVisitor::class.java).toList()
        val vars = mutableListOf<CVarDecl>()
        for (system in gameIR.systems) {
            val sanitizedId = system.id.replace('-', '_').replace(' ', '_')
            when (system) {
                is io.github.gbkt.core.ir.CameraSystem -> {
                    vars += CVarDecl(name = "_camera_x", type = CU8, initializer = CLiteral(0))
                    vars += CVarDecl(name = "_camera_y", type = CU8, initializer = CLiteral(0))
                    vars +=
                        CVarDecl(
                            name = "_camera_target",
                            type = CU8,
                            initializer = CRawExpr("0xFF"),
                        )
                    vars +=
                        CVarDecl(
                            name = "_camera_shake_intensity",
                            type = CU8,
                            initializer = CLiteral(0),
                        )
                    vars +=
                        CVarDecl(
                            name = "_camera_shake_timer",
                            type = CU8,
                            initializer = CLiteral(0),
                        )
                }
                is io.github.gbkt.core.ir.ExplorationSystem -> {
                    vars += CVarDecl(name = "_player_x", type = CU8, initializer = CLiteral(0))
                    vars += CVarDecl(name = "_player_y", type = CU8, initializer = CLiteral(0))
                    vars += CVarDecl(name = "_current_floor", type = CU8, initializer = CLiteral(0))
                    // Expanded exploration state globals (Plan 06.3-02)
                    vars +=
                        CVarDecl(
                            name = "_exploration_step_count",
                            type = CU8,
                            initializer = CLiteral(0),
                        )
                    vars +=
                        CVarDecl(
                            name = "_encounter_safe_steps",
                            type = CU8,
                            initializer =
                                CLiteral(
                                    gameIR.zones
                                        .firstOrNull { it.encounterTable != null }
                                        ?.encounterTable
                                        ?.safeSteps ?: 10
                                ),
                        )
                    vars +=
                        CVarDecl(
                            name = "_encounter_triggered",
                            type = CU8,
                            initializer = CLiteral(0),
                        )
                    vars += CVarDecl(name = "_encounter_id", type = CU8, initializer = CLiteral(0))
                    vars +=
                        CVarDecl(name = "_current_zone_safe", type = CU8, initializer = CLiteral(0))
                    // _current_tileset_id already declared in allVariables — skip to avoid
                    // duplicate
                    vars +=
                        CVarDecl(
                            name = "_current_zone_id",
                            type = CU8,
                            initializer = CRawExpr("0xFF"),
                        )
                    // Per-gauge globals
                    for (gauge in system.gauges) {
                        vars +=
                            CVarDecl(
                                name = "_gauge_${gauge.id}",
                                type = CU8,
                                initializer = CLiteral(gauge.initial),
                            )
                    }
                    // Per-key globals
                    for (key in system.keys) {
                        vars +=
                            CVarDecl(
                                name = "_key_${key.id}",
                                type = CU8,
                                initializer = CLiteral(key.initial),
                            )
                    }
                    // Entity collision globals (G3 — Plan 06.3-03)
                    // Emitted when any actor has non-PASSTHROUGH entity collision config.
                    val collisionActors =
                        gameIR.actors.filter {
                            val ec = it.entityCollision
                            ec != null && ec.mode != EntityCollisionMode.PASSTHROUGH
                        }
                    if (collisionActors.isNotEmpty()) {
                        val maxEntities = collisionActors.size
                        val mapSize = 32 * 32 / 8 + 1 // 129 bytes for 32x32 grid
                        // _entity_grid[MAP_SIZE] — bit-packed entity presence grid
                        vars +=
                            CVarDecl(
                                name = "_entity_grid",
                                type = CArray(CU8, mapSize),
                                initializer = null,
                            )
                        // _entity_collision_mode[MAX_ENTITIES] — per-entity mode (0xFF=none)
                        vars +=
                            CVarDecl(
                                name = "_entity_collision_mode",
                                type = CArray(CU8, maxEntities),
                                initializer =
                                    CRawExpr(
                                        "{${(0 until maxEntities).joinToString(", ") { "0xFF" }}}"
                                    ),
                            )
                        // _entity_collision_shape[MAX_ENTITIES] — 0=TILE, 1=HITBOX
                        vars +=
                            CVarDecl(
                                name = "_entity_collision_shape",
                                type = CArray(CU8, maxEntities),
                                initializer = null,
                            )
                        // _entity_tile_x/y[MAX_ENTITIES] — entity tile positions
                        vars +=
                            CVarDecl(
                                name = "_entity_tile_x",
                                type = CArray(CU8, maxEntities),
                                initializer =
                                    CRawExpr(
                                        "{${(0 until maxEntities).joinToString(", ") { "0xFF" }}}"
                                    ),
                            )
                        vars +=
                            CVarDecl(
                                name = "_entity_tile_y",
                                type = CArray(CU8, maxEntities),
                                initializer =
                                    CRawExpr(
                                        "{${(0 until maxEntities).joinToString(", ") { "0xFF" }}}"
                                    ),
                            )
                        // _entity_count — number of registered entities
                        vars +=
                            CVarDecl(name = "_entity_count", type = CU8, initializer = CLiteral(0))
                        // Gap 1 callback globals — set before callback execution
                        vars +=
                            CVarDecl(
                                name = "_blocking_entity_id",
                                type = CU8,
                                initializer = CRawExpr("0xFF"),
                            )
                        vars +=
                            CVarDecl(
                                name = "_pushed_entity_id",
                                type = CU8,
                                initializer = CRawExpr("0xFF"),
                            )
                        vars +=
                            CVarDecl(
                                name = "_push_direction",
                                type = CU8,
                                initializer = CRawExpr("0xFF"),
                            )
                        // Multi-tile entity dimensions (Gap A)
                        val tilesWideInit =
                            collisionActors.joinToString(", ") {
                                (it.entityCollision?.tilesWide ?: 1).toString()
                            }
                        vars +=
                            CVarDecl(
                                name = "_entity_tiles_wide",
                                type = CArray(CU8, maxEntities),
                                initializer = CRawExpr("{$tilesWideInit}"),
                            )
                        val tilesHighInit =
                            collisionActors.joinToString(", ") {
                                (it.entityCollision?.tilesHigh ?: 1).toString()
                            }
                        vars +=
                            CVarDecl(
                                name = "_entity_tiles_high",
                                type = CArray(CU8, maxEntities),
                                initializer = CRawExpr("{$tilesHighInit}"),
                            )
                        // Push direction constraints (Gap B)
                        val pushDirInit =
                            collisionActors.joinToString(", ") {
                                (it.entityCollision?.pushDirection?.ordinal ?: 0).toString()
                            }
                        vars +=
                            CVarDecl(
                                name = "_entity_push_dir",
                                type = CArray(CU8, maxEntities),
                                initializer = CRawExpr("{$pushDirInit}"),
                            )
                        val pushAllowedInit =
                            collisionActors.joinToString(", ") { actor ->
                                val ec = actor.entityCollision
                                if (ec != null && ec.pushDirection == PushDirection.SPECIFIC) {
                                    var mask = 0
                                    for (edge in ec.allowedPushDirections) {
                                        mask =
                                            mask or
                                                (1 shl
                                                    when (edge) {
                                                        TransitionEdge.NORTH -> 0
                                                        TransitionEdge.SOUTH -> 1
                                                        TransitionEdge.WEST -> 2
                                                        TransitionEdge.EAST -> 3
                                                    })
                                    }
                                    mask.toString()
                                } else {
                                    "0"
                                }
                            }
                        vars +=
                            CVarDecl(
                                name = "_entity_push_allowed",
                                type = CArray(CU8, maxEntities),
                                initializer = CRawExpr("{$pushAllowedInit}"),
                            )
                    }
                }
                is PathfindingSystem -> {
                    vars += GBDKSystemVisitor.buildPathfindingGlobals(system)
                }
                is io.github.gbkt.core.ir.DialogSystem -> {
                    // Extended config: default speed and border style
                    vars +=
                        CVarDecl(
                            name = "_dialog_default_speed",
                            type = CU8,
                            initializer = CLiteral(system.textSpeed),
                        )
                    vars +=
                        CVarDecl(
                            name = "_dialog_default_border",
                            type = CU8,
                            initializer = CLiteral(system.defaultBorder.ordinal),
                        )
                }
                is GenericSystem -> {
                    val systemType = system.config["type"] as? String
                    val genreVisitor =
                        if (systemType != null) {
                            genreVisitors.find { it.canHandle(systemType) }
                        } else {
                            null
                        }
                    if (genreVisitor != null && systemType != null) {
                        vars +=
                            genreVisitor
                                .visit(systemType, system.config, gameIR)
                                .varDecls
                                .filterIsInstance<CVarDecl>()
                    } else if (systemType == "simple_battle") {
                        vars +=
                            CVarDecl(
                                name = "_combat_state_$sanitizedId",
                                type = CU8,
                                initializer = CLiteral(0),
                            )
                    }
                    if (genreVisitor == null && systemType == "arpg_combat") {
                        vars += RpgVisitor(gameIR).generateActionRpgVarDecls(system)
                    }
                    if (genreVisitor == null && systemType == "roguelike_system") {
                        vars += RpgVisitor(gameIR).generateRoguelikeVarDecls(system)
                    }
                    if (genreVisitor == null && systemType == "rpg_currency") {
                        vars += RpgVisitor(gameIR).generateCurrencyVarDecls(system)
                    }
                    if (genreVisitor == null && systemType == "pickup_system") {
                        vars += GBDKSystemVisitor(gameIR).buildPickupVarDecls(system, sanitizedId)
                    }
                    if (genreVisitor == null && systemType == "audio_mixer") {
                        @Suppress("UNCHECKED_CAST")
                        val groups =
                            (system.config["groups"] as? List<ChannelGroupDef>)
                                ?: listOf(
                                    ChannelGroupDef("music", setOf(1, 2), 7, 0),
                                    ChannelGroupDef("sfx", setOf(3, 4), 7, 1),
                                    ChannelGroupDef("ui", setOf(3), 7, 2),
                                )
                        val masterVol = system.config["master_volume"] as? Int ?: 7

                        // _mixer_group_vol[N] — initial volumes per group
                        // emitted as individual element inits; C arrays init sequentially
                        val initVols = groups.joinToString(", ") { it.defaultVolume.toString() }
                        vars +=
                            CVarDecl(
                                name = "_mixer_group_vol",
                                type = CArray(CU8, groups.size),
                                initializer = CRawExpr("{$initVols}"),
                            )

                        // _mixer_master_vol — initial master volume
                        vars +=
                            CVarDecl(
                                name = "_mixer_master_vol",
                                type = CU8,
                                initializer = CLiteral(masterVol),
                            )

                        // _mixer_group_muted[N] — mute state per group (0 = unmuted)
                        val initMuted = groups.joinToString(", ") { "0" }
                        vars +=
                            CVarDecl(
                                name = "_mixer_group_muted",
                                type = CArray(CU8, groups.size),
                                initializer = CRawExpr("{$initMuted}"),
                            )

                        // _mixer_channel_mask_<name> — NR51 bit pattern per group
                        // NR51: bits 7-4 = L-channel enables (CH4,CH3,CH2,CH1),
                        //       bits 3-0 = R-channel enables (CH4,CH3,CH2,CH1)
                        // CH1=bit0, CH2=bit1, CH3=bit2, CH4=bit3 (both L and R together)
                        for (group in groups) {
                            var mask = 0
                            for (ch in group.channels) {
                                val bit = ch - 1 // CH1=0, CH2=1, CH3=2, CH4=3
                                mask = mask or (1 shl bit) // R-enable
                                mask = mask or (1 shl (bit + 4)) // L-enable
                            }
                            vars +=
                                CVarDecl(
                                    name = "_mixer_channel_mask_${group.name}",
                                    type = CU8,
                                    initializer = CRawExpr("0x${mask.toString(16).uppercase()}"),
                                    isConst = true,
                                )
                        }

                        // _mixer_priority[4] — per-channel priority (4 GB channels), init 0
                        vars +=
                            CVarDecl(
                                name = "_mixer_priority",
                                type = CArray(CU8, 4),
                                initializer = CRawExpr("{0, 0, 0, 0}"),
                            )

                        // _mixer_preduck_vol — saved music volume before auto-ducking (Gap 6)
                        vars +=
                            CVarDecl(
                                name = "_mixer_preduck_vol",
                                type = CU8,
                                initializer = CLiteral(7),
                            )
                    }
                }
                is io.github.gbkt.core.ir.CombatEngineSystem -> {
                    // _combat_state_<id>: INIT state (0) at startup
                    vars +=
                        CVarDecl(
                            name = "_combat_state_$sanitizedId",
                            type = CU8,
                            initializer = CLiteral(0),
                        )
                    // _pending_state_<id>: 0xFF sentinel = no pending transition
                    vars +=
                        CVarDecl(
                            name = "_pending_state_$sanitizedId",
                            type = CU8,
                            initializer = CLiteral(0xFF),
                        )
                    // ATB-specific globals: gauge[], active[], acted[], agl[], menu_open,
                    // and optionally charge[] (CHARGE model) and _turn_order[] (when strategy set)
                    val combatVisitor =
                        io.github.gbkt.backend.gbdk.codegen.visitor.CombatVisitor(gameIR)
                    vars += combatVisitor.generateAtbGlobals(system)
                    // Wave survival globals: _wave_<id>_current (UINT8), _wave_<id>_timer (UINT16)
                    vars += combatVisitor.generateWaveGlobals(system)
                    // Hook enabled flag: _combat_<id>_hooks_enabled (only when hooks registered)
                    vars += combatVisitor.generateHookGlobals(system)
                }
                else -> Unit
            }
        }
        return vars
    }

    /**
     * Generate HOME-bank functions for all systems in the game via [GBDKSystemVisitor].
     *
     * The v2 pipeline emits `trigger_{id}()` calls from banked scene code (via [TriggerSystem]
     * ScriptOp). These functions must be HOME-resident so they are callable from bank 1 without
     * explicit bank switching.
     *
     * All typed systems dispatch through [GBDKSystemVisitor] instead of filtering to only
     * [GenericSystem]. This ensures CameraSystem, SaveSystem, ExplorationSystem, and DialogSystem
     * generate real C code rather than being silently dropped.
     *
     * Genre visitors discovered via [ServiceLoader] take priority over the built-in visitor. When a
     * [GenreSystemVisitor] returns [canHandle] = true for a system type, its [CFunction] list is
     * used instead of delegating to [GBDKSystemVisitor].
     */
    private fun buildSystemFunctions(
        gameIR: GameIR,
        bankAllocation: Map<String, Int> = emptyMap(),
    ): List<CFunction> {
        val genreVisitors = ServiceLoader.load(GenreSystemVisitor::class.java).toList()
        val visitor = GBDKSystemVisitor(gameIR, bankAllocation)
        return gameIR.systems.flatMap { system ->
            val systemType = (system as? GenericSystem)?.config?.get("type") as? String
            val genreVisitor =
                if (systemType != null) genreVisitors.find { it.canHandle(systemType) } else null
            if (genreVisitor != null && system is GenericSystem) {
                genreVisitor
                    .visit(systemType!!, system.config, gameIR)
                    .functions
                    .filterIsInstance<CFunction>()
            } else {
                system.accept(visitor)
            }
        }
    }

    // =========================================================================
    // navigate_to_scene function
    // =========================================================================

    /**
     * Build the navigate_to_scene dispatch function.
     *
     * For scenes with a bankSlot.bank > 0, the exit/enter switch cases call the trampoline function
     * ({scene}_exit_trampoline / {scene}_enter_trampoline) instead of the BANKED function directly.
     * This is required because navigate_to_scene lives in the HOME bank and must not call BANKED
     * functions directly.
     *
     * For scenes without a bankSlot (bank = null or bank = 0), the switch cases call the scene
     * functions directly.
     */
    private fun buildNavigateToSceneFunction(gameIR: GameIR): CFunction {
        // Build switch cases for exit calls per scene
        val exitCases =
            gameIR.scenes
                .filter { it.exitOps.isNotEmpty() }
                .map { scene ->
                    val exitFnName = exitFunctionName(scene)
                    CSwitchCase(
                        value = CVar("SCENE_${scene.id.uppercase()}"),
                        body = listOf(CExprStatement(CCall(exitFnName, emptyList())), CBreak),
                    )
                }

        // Build switch for exit, assign current_scene, build switch for enter
        val enterCases =
            gameIR.scenes
                .filter { it.enterOps.isNotEmpty() }
                .map { scene ->
                    val enterFnName = enterFunctionName(scene)
                    CSwitchCase(
                        value = CVar("SCENE_${scene.id.uppercase()}"),
                        body = listOf(CExprStatement(CCall(enterFnName, emptyList())), CBreak),
                    )
                }

        val bodyStatements = buildList {
            if (exitCases.isNotEmpty()) {
                add(CSwitch(CVar("current_scene"), exitCases))
            }
            add(CExprStatement(CBinaryExpr(CVar("current_scene"), "=", CVar("scene"))))
            if (enterCases.isNotEmpty()) {
                add(CSwitch(CVar("scene"), enterCases))
            }
        }

        return CFunction(
            name = "navigate_to_scene",
            returnType = CVoid,
            params = listOf(CParam("scene", CU8)),
            body = bodyStatements,
            sectionComment = "Scene navigation",
        )
    }

    // =========================================================================
    // main() function
    // =========================================================================

    private fun buildMainFunction(gameIR: GameIR): CFunction {
        // Build game loop: switch on current_scene, call {scene}_frame per scene
        val frameCases =
            gameIR.scenes
                .filter { it.frameOps.isNotEmpty() }
                .map { scene ->
                    val frameFnName = frameFunctionName(scene)
                    CSwitchCase(
                        value = CVar("SCENE_${scene.id.uppercase()}"),
                        body = listOf(CExprStatement(CCall(frameFnName, emptyList())), CBreak),
                    )
                }

        // Call start scene enter (via trampoline if banked)
        val startSceneId = gameIR.startScene
        val startEnterCall =
            if (
                startSceneId != null &&
                    gameIR.scenes.any { it.id == startSceneId && it.enterOps.isNotEmpty() }
            ) {
                val startScene = gameIR.scenes.first { it.id == startSceneId }
                val enterFnName = enterFunctionName(startScene)
                listOf(CExprStatement(CCall(enterFnName, emptyList())))
            } else {
                emptyList()
            }

        // Sprite data loading: set_sprite_data() for each actor with a sprite asset
        // VRAM tile slots are assigned sequentially starting at 0.
        val spriteDataLoads = buildSpriteDataLoadStatements(gameIR)
        val spriteOAMInits = buildOAMInitStatements(gameIR)

        val gameLoopBody = buildList {
            // Update joypad state once per frame before scene frame dispatch
            add(CExprStatement(CCall("update_joypad", emptyList())))
            add(CSwitch(CVar("current_scene"), frameCases))
            // Run puzzle per-frame updates (pressure plates + timed blocks) after scene logic
            if (gameIR.puzzleObjects.isNotEmpty()) {
                add(CExprStatement(CCall("puzzle_update_all", emptyList())))
            }
            // Run NPC-NPC collision checks after movement updates, before sprite sync
            if (gameIR.collisionRules.isNotEmpty()) {
                add(CExprStatement(CCall("check_all_npc_collisions", emptyList())))
            }
            // Sync position variables to OAM after game logic, before VBlank
            add(CExprStatement(CCall("update_sprites", emptyList())))
            // Update sound driver channel durations per frame
            add(CExprStatement(CCall("sound_driver_update", emptyList())))
            // hUGETracker audio driver tick — only emitted when music ops are used (A2)
            if (SoundVisitor(gameIR).hasMusicOps()) {
                add(CExprStatement(CCall("hUGE_dosound", emptyList())))
            }
            add(CExprStatement(CCall("wait_vbl_done", emptyList())))
        }

        val mainBody = buildList {
            // Sound hardware init: enable sound (NR52), master volume max (NR50), all channels on
            // (NR51)
            add(CExprStatement(CBinaryExpr(CVar("NR52_REG"), "=", CLiteral(0x80))))
            add(CExprStatement(CBinaryExpr(CVar("NR50_REG"), "=", CLiteral(0x77))))
            add(CExprStatement(CBinaryExpr(CVar("NR51_REG"), "=", CLiteral(0xFF))))
            // Enable display (required before sprites are visible) — GBDK macro
            add(CRawCode("DISPLAY_ON;"))
            // Enable OAM sprite layer — GBDK macro
            add(CRawCode("SHOW_SPRITES;"))
            // Load sprite tile data into VRAM
            addAll(spriteDataLoads)
            // Bind OAM slots to tiles and set initial positions
            addAll(spriteOAMInits)
            addAll(startEnterCall)
            add(CWhile(CVar("1"), gameLoopBody))
            add(CReturn())
        }

        return CFunction(
            name = "main",
            returnType = CVoid,
            body = mainBody,
            sectionComment = "Entry point",
        )
    }

    /**
     * Build `set_sprite_data()` call statements for all actors with sprites.
     *
     * VRAM tile slots are assigned sequentially: each actor's tiles start immediately after the
     * previous actor's tiles end. The tile data array name is derived from the asset path using the
     * GBDK convention: "sprites/paddle.png" → "sprites_paddle_tiles" (slashes and dots become
     * underscores).
     *
     * Returns empty list if no actors have sprites.
     */
    private fun buildSpriteDataLoadStatements(gameIR: GameIR): List<CStatement> {
        val statements = mutableListOf<CStatement>()
        var nextTile = 0
        for (actor in gameIR.actors) {
            val sprite = actor.sprite ?: continue
            // Derive tile data array name from asset path using GBDK naming convention
            val arrayName =
                sprite.assetRef.path.substringBeforeLast('.').replace('/', '_').replace('-', '_') +
                    "_tiles"
            val loads = ActorVisitor.generateSpriteDataLoad(actor, arrayName, nextTile)
            statements.addAll(loads)
            // Advance VRAM tile pointer past this actor's tiles
            val tilesWide = (sprite.size.width + 7) / 8
            val tilesHigh = (sprite.size.height + 7) / 8
            nextTile += tilesWide * tilesHigh
        }
        return statements
    }

    /**
     * Build `set_sprite_tile()` + initial `move_sprite()` statements for all actors with sprites.
     *
     * Mirrors [buildSpriteDataLoadStatements] tile accounting: each actor's OAM slots and tile
     * indices start immediately after the previous actor's. Delegates to
     * [ActorVisitor.generateOAMInit] for per-slot hardware setup.
     */
    private fun buildOAMInitStatements(gameIR: GameIR): List<CStatement> {
        val statements = mutableListOf<CStatement>()
        var nextSlot = 0
        var nextTile = 0
        for (actor in gameIR.actors) {
            val sprite = actor.sprite ?: continue
            val tilesWide = (sprite.size.width + 7) / 8
            val tilesHigh = (sprite.size.height + 7) / 8
            val totalTiles = tilesWide * tilesHigh
            statements.addAll(ActorVisitor.generateOAMInit(actor, nextSlot, nextTile))
            nextSlot += totalTiles
            nextTile += totalTiles
        }
        return statements
    }

    // =========================================================================
    // Helper: function name resolution (trampoline vs direct)
    // =========================================================================

    /**
     * Returns the enter function name to use when calling from HOME-resident code.
     *
     * If the scene has a bankSlot with bank > 0, returns the trampoline name so HOME-resident code
     * never calls a BANKED function directly. Otherwise returns the direct function name.
     */
    private fun enterFunctionName(scene: SceneIR): String {
        return if (scene.bankSlot != null && scene.bankSlot!!.bank > 0) {
            "${scene.id}_enter_trampoline"
        } else {
            "${scene.id}_enter"
        }
    }

    private fun frameFunctionName(scene: SceneIR): String {
        return if (scene.bankSlot != null && scene.bankSlot!!.bank > 0) {
            "${scene.id}_frame_trampoline"
        } else {
            "${scene.id}_frame"
        }
    }

    private fun exitFunctionName(scene: SceneIR): String {
        return if (scene.bankSlot != null && scene.bankSlot!!.bank > 0) {
            "${scene.id}_exit_trampoline"
        } else {
            "${scene.id}_exit"
        }
    }

    // =========================================================================
    // Type mapping
    // =========================================================================

    private fun varTypeToC(varType: VarType) =
        when (varType) {
            VarType.U8 -> CU8
            VarType.U16 -> io.github.gbkt.backend.gbdk.codegen.ast.CU16
            VarType.I8 -> io.github.gbkt.backend.gbdk.codegen.ast.CI8
            VarType.I16 -> io.github.gbkt.backend.gbdk.codegen.ast.CI16
        }
}

/**
 * Infers the semantic category of a game variable from its name using ordered heuristics.
 *
 * Rules are applied most-specific first:
 * 1. Exact match: `current_scene` → "state"
 * 2. Suffix `_x` or `_y` → "position"
 * 3. Contains `dx`, `dy`, `vel`, `speed` → "velocity"
 * 4. Contains `score` or `point` → "score"
 * 5. Contains `hp`, `health`, `mp`, `sp` → "stat"
 * 6. Contains `flag`, `met`, `has_`, `is_` → "flag"
 * 7. Contains `step`, `count`, `frame`, `timer` → "counter"
 * 8. Else → "unknown"
 *
 * The check is case-insensitive to handle both camelCase and snake_case names.
 */
/**
 * Normalizes a variable name from camelCase/PascalCase to snake_case lowercase.
 *
 * Inserts underscores at case transitions (lowercase→uppercase) and digit boundaries (digit→letter,
 * letter→digit), then lowercases the entire result.
 *
 * Examples: `"ballDx"` → `"ball_dx"`, `"playerHealth"` → `"player_health"`, `"p1Score"` →
 * `"p_1_score"`, `"hp"` → `"hp"`.
 */
private fun normalize(name: String): String =
    name
        .replace(Regex("([a-z])([A-Z])"), "$1_$2")
        .replace(Regex("([0-9])([a-zA-Z])"), "$1_$2")
        .replace(Regex("([a-zA-Z])([0-9])"), "$1_$2")
        .lowercase()

/**
 * Returns `true` if [name] contains [word] as a whole word in its normalized (snake_case) form.
 *
 * Word boundaries are start-of-string, end-of-string, or underscores. This prevents false positives
 * like `display` matching `sp` or `island` matching `is`.
 */
private fun hasWord(name: String, word: String): Boolean {
    val normalized = normalize(name)
    return Regex("(?:^|_)${Regex.escape(word)}(?:_|$)").containsMatchIn(normalized)
}

/**
 * Infers the semantic category of a game variable from its name using ordered heuristics.
 *
 * Rules are applied most-specific first:
 * 1. Exact match: `current_scene` → "state"
 * 2. Suffix `_x` or `_y` → "position"
 * 3. Word match `dx`, `dy`, `vel`, `speed` → "velocity"
 * 4. Word match `score`, `point`, `points`, `gold` → "score"
 * 5. Word match `hp`, `health`, `mp`, `sp`, `atk`, `def`, `str`, `exp` → "stat"
 * 6. Word match `flag`, `met`, `has`, `is`, `defeated` → "flag"
 * 7. Word match `step`, `count`, `frame`, `timer` → "counter"
 * 8. Else → "unknown"
 *
 * Names are normalized from camelCase to snake_case before word-boundary matching to prevent false
 * positives (e.g. `display` no longer matches `sp`, `island` no longer matches `is`).
 */
internal fun inferVariableSemantic(name: String): String {
    val lower = name.lowercase()
    return when {
        lower == "current_scene" -> "state"
        lower.endsWith("_x") || lower.endsWith("_y") -> "position"
        hasWord(name, "dx") ||
            hasWord(name, "dy") ||
            hasWord(name, "vel") ||
            hasWord(name, "speed") -> "velocity"
        hasWord(name, "score") ||
            hasWord(name, "point") ||
            hasWord(name, "points") ||
            hasWord(name, "gold") -> "score"
        hasWord(name, "hp") ||
            hasWord(name, "health") ||
            hasWord(name, "mp") ||
            hasWord(name, "sp") ||
            hasWord(name, "atk") ||
            hasWord(name, "def") ||
            hasWord(name, "str") ||
            hasWord(name, "exp") -> "stat"
        hasWord(name, "flag") ||
            hasWord(name, "met") ||
            hasWord(name, "has") ||
            hasWord(name, "defeated") ||
            hasWord(name, "is") -> "flag"
        hasWord(name, "step") ||
            hasWord(name, "count") ||
            hasWord(name, "frame") ||
            hasWord(name, "timer") -> "counter"
        else -> "unknown"
    }
}
