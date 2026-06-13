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
import io.github.gbkt.backend.gbdk.codegen.visitor.MetaspriteVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.RpgVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.SceneVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.ScriptOpVisitor
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
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.GlobalFlagsIR
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.LeverObjectIR
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.NpcObjectIR
import io.github.gbkt.core.ir.PaletteType
import io.github.gbkt.core.ir.PathfindingSystem
import io.github.gbkt.core.ir.PoolDestroyActor
import io.github.gbkt.core.ir.PoolForEachActive
import io.github.gbkt.core.ir.PrintAligned
import io.github.gbkt.core.ir.PrintAt
import io.github.gbkt.core.ir.PrintCentered
import io.github.gbkt.core.ir.PrintOp
import io.github.gbkt.core.ir.PushDirection
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SconceObjectIR
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.SetPalette
import io.github.gbkt.core.ir.SpriteMode
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
// GBDK PIPELINE
// Orchestrates the typed C AST pipeline for GameIR games.
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
// Output: PipelineOutput — filename-to-C-content + filename-to-source-map-JSON
// =============================================================================

/**
 * Output of [GBDKPipeline.generate].
 *
 * @property files Map of filename to C source content (e.g. "main.c" → "// Generated by gbkt\n...")
 * @property sourceMaps Map of filename to v2 source map JSON (e.g. "main.c" →
 *   "{\"version\":\"2.0\"...}") Header files (game.h) are excluded — they contain no DSL-originated
 *   statements.
 */
data class PipelineOutput(val files: Map<String, String>, val sourceMaps: Map<String, String>)

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
class GBDKPipeline {

    /**
     * Generate C source files from a v2 [GameIR].
     *
     * For each non-header [CFile], creates a [SourceMapCollector] and passes it to [CEmitter.emit]
     * so that line numbers are tracked during emission. The collected mappings are serialized as v2
     * JSON ([SourceMap.version] = "2.0") and returned alongside the C content in
     * [PipelineOutput.sourceMaps].
     *
     * Header files (game.h) are excluded from source map output — they contain only extern
     * declarations and forward-declaration prototypes, not DSL-originated statements.
     *
     * @return [PipelineOutput] with C content and source map JSON per non-header file.
     */
    fun generate(gameIR: GameIR): PipelineOutput {
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

        return PipelineOutput(files = files, sourceMaps = sourceMaps)
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
        json.put("scenes", buildMetadataScenesJson(gameIR))
        json.put("actors", buildMetadataActorsJson(gameIR))
        json.put("variables", buildMetadataVariablesJson(gameIR))
        json.put("texts", buildMetadataTextsJson(gameIR))
        json.put("terminalScenes", buildMetadataTerminalScenesJson(gameIR))
        json.put("controls", buildMetadataControlsJson(gameIR))
        json.put("transitions", buildMetadataTransitionsJson(gameIR))
        json.put("tileDecoders", buildMetadataTileDecodersJson())
        json.put("zoneTilesets", buildMetadataZoneTilesetsJson(gameIR))
        json.put("sprites", buildMetadataSpritesJson(gameIR))
        return json.toString(2)
    }

    /** Scenes section: name → index map. */
    private fun buildMetadataScenesJson(gameIR: GameIR): org.json.JSONObject {
        val scenes = org.json.JSONObject()
        for ((index, scene) in gameIR.scenes.withIndex()) {
            scenes.put(scene.id, index)
        }
        return scenes
    }

    /**
     * Actors section: OAM slot assignments, sprite dimensions, position-variable names.
     *
     * WR-02: hardware SPRITES_8x16 mode uses ONE OAM slot per 8×16 pair. An actor sprite that is
     * 16px tall has tilesHigh=2 raw tiles but only 1 OAM entry. Uses the same derivation as the
     * actor-sprite sidecar emission loop: tileHeight <= 8 → SPR8x8 (8px OAM slot), else SPR8x16
     * (16px OAM slot).
     */
    private fun buildMetadataActorsJson(gameIR: GameIR): org.json.JSONArray {
        val actors = org.json.JSONArray()
        for (actor in gameIR.actors) {
            if (actor.sprite == null) continue
            val sprite = actor.sprite!!
            val tilesWide = (sprite.size.width + 7) / 8
            val oamSlotHeight = if (sprite.size.height <= 8) 8 else 16
            val oamCount = tilesWide * ((sprite.size.height + oamSlotHeight - 1) / oamSlotHeight)
            val actorJson =
                org.json
                    .JSONObject()
                    .put("name", actor.id)
                    .put("oamStart", actor.oamSlot?.slot ?: -1)
                    .put("oamCount", oamCount)
                    .put("spriteWidth", sprite.size.width)
                    .put("spriteHeight", sprite.size.height)
                    .put(
                        "vars",
                        org.json.JSONObject().put("x", "${actor.id}_x").put("y", "${actor.id}_y"),
                    )
            actors.put(actorJson)
        }
        return actors
    }

    /** Variables section: DSL-declared variables with name, type, and semantic. */
    private fun buildMetadataVariablesJson(gameIR: GameIR): org.json.JSONArray {
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
        return variables
    }

    /** Texts section: literal display strings extracted from all scene scripts. */
    private fun buildMetadataTextsJson(gameIR: GameIR): org.json.JSONArray {
        val allOps = gameIR.scenes.flatMap { it.enterOps + it.frameOps + it.exitOps }
        val texts = org.json.JSONArray()
        for (text in collectTexts(allOps)) {
            texts.put(text)
        }
        return texts
    }

    /** Terminal scenes section: convention-based detection of game-ending scenes. */
    private fun buildMetadataTerminalScenesJson(gameIR: GameIR): org.json.JSONArray {
        val terminalScenes = org.json.JSONArray()
        for (scene in gameIR.scenes) {
            if (scene.id.lowercase() in TERMINAL_SCENE_PATTERNS) {
                terminalScenes.put(scene.id)
            }
        }
        return terminalScenes
    }

    /** Controls section: per-scene input mappings extracted from IfOp conditions. */
    private fun buildMetadataControlsJson(gameIR: GameIR): org.json.JSONObject {
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
        return controlsJson
    }

    /** Transitions section: scene navigation graph extracted from NavigateTo ops. */
    private fun buildMetadataTransitionsJson(gameIR: GameIR): org.json.JSONArray {
        val transitionsArray = org.json.JSONArray()
        for (edge in extractTransitions(gameIR)) {
            transitionsArray.put(org.json.JSONObject().put("from", edge.from).put("to", edge.to))
        }
        return transitionsArray
    }

    /** Tile decoders section: default decoders for all games. */
    private fun buildMetadataTileDecodersJson(): org.json.JSONObject {
        val tileDecodersObj = org.json.JSONObject()
        tileDecodersObj.put("bg", org.json.JSONObject().put("type", "gbdk_offset"))
        tileDecodersObj.put("win", org.json.JSONObject().put("type", "direct_ascii"))
        return tileDecodersObj
    }

    /**
     * Zone tilesets section: manifest consumed by ConvertZoneTilesetsTask.
     *
     * Phase 11.2 (D-A4): filtered to NEW-path consumers (zone.tilesetPath != null). Phase 11.1-17:
     * emits mapWidth + mapHeight. Phase 12.1-01: emits `bank` for #pragma bank synthesis.
     * `allocateZoneBanks` is invoked side-effect-free (legacy `buildTilemapBankFiles` path is
     * independent).
     */
    private fun buildMetadataZoneTilesetsJson(gameIR: GameIR): org.json.JSONArray {
        val zoneBankAllocation = allocateZoneBanks(gameIR)
        val zoneTilesets = org.json.JSONArray()
        for (zone in gameIR.zones) {
            val path = zone.tilesetPath ?: continue
            val sanitized = zone.id.replace('-', '_').replace(' ', '_')
            val bank =
                zoneBankAllocation[zone.id]
                    ?: error(
                        "allocateZoneBanks did not assign a bank for zone '${zone.id}' " +
                            "with tilesetPath '$path' — Phase 12.1 D-01 wiring gap"
                    )
            zoneTilesets.put(
                org.json
                    .JSONObject()
                    .put("id", zone.id)
                    .put("path", path)
                    // Phase 12.2 R-04: emit explicit JSONObject.NULL so the serialized JSON carries
                    // "tilemapPath": null rather than omitting the key.
                    .put("tilemapPath", zone.tilemapPath ?: org.json.JSONObject.NULL)
                    .put("sanitizedSymbol", sanitized)
                    // Plan 13.4-02: emit explicit JSONObject.NULL when the sentinel is null so the
                    // JSON key is present (not omitted).
                    .put("mapWidth", zone.mapWidth ?: org.json.JSONObject.NULL)
                    .put("mapHeight", zone.mapHeight ?: org.json.JSONObject.NULL)
                    .put("bank", bank)
            )
        }
        return zoneTilesets
    }

    /**
     * Sprites section: manifest consumed by ConvertSpritesTask (Phase 12.4 D-02).
     *
     * Includes metasprites with explicit sprite binding (spritePath != null) and actor sprites.
     * Actor sprites always use mirrorDedup=false. Plan 12.4-13 Rule 1: actor sprites were
     * previously dropped, leaving #include directives dangling.
     */
    private fun buildMetadataSpritesJson(gameIR: GameIR): org.json.JSONArray {
        val sprites = org.json.JSONArray()
        for (ms in gameIR.metasprites) {
            val spritePath = ms.spritePath ?: continue
            // main.c #include uses "sprites/<id>.h"; includePath must match so ConvertSpritesTask
            // places the header at the correct location relative to cSourceDir.
            val includePath = "sprites/${ms.id}.h"
            val spriteEntry =
                org.json
                    .JSONObject()
                    .put("id", ms.id)
                    .put("spritePath", spritePath)
                    .put("includePath", includePath)
                    .put("mirrorDedup", ms.mirrorDedup)
                    .put("spriteMode", ms.spriteMode?.name ?: SpriteMode.SPR8x16.name)
                    .put("pivotX", ms.pivotX ?: 0)
                    .put("pivotY", ms.pivotY ?: 0)
                    .put("frameWidth", ms.frameWidth ?: 8)
                    .put("frameHeight", ms.frameHeight ?: 8)
                    .put("isMetasprite", true)
            if (ms.frameCount != null) {
                spriteEntry.put("frameCount", ms.frameCount)
            }
            sprites.put(spriteEntry)
        }
        // Actor sprite entries: cutting flags derived from actor.sprite.size. distinct() prevents
        // duplicates when multiple actors share the same sprite PNG (e.g. pong's paddle1 +
        // paddle2).
        val actorSpritesByPath =
            gameIR.actors
                .mapNotNull { actor -> actor.sprite?.let { sprite -> actor to sprite } }
                .groupBy { (_, sprite) -> sprite.assetRef.path }
        for ((spritePath, actorsForPath) in actorSpritesByPath) {
            val includePath = spritePath.substringBeforeLast('.') + ".h"
            val firstSprite = actorsForPath.first().second
            val tileHeight = firstSprite.size.height
            val tileWidth = firstSprite.size.width
            val actorSpriteMode = if (tileHeight <= 8) SpriteMode.SPR8x8 else SpriteMode.SPR8x16
            sprites.put(
                org.json
                    .JSONObject()
                    .put("id", spritePath.substringAfterLast('/').substringBeforeLast('.'))
                    .put("spritePath", spritePath)
                    .put("includePath", includePath)
                    .put("mirrorDedup", false)
                    .put("spriteMode", actorSpriteMode.name)
                    .put("pivotX", 0)
                    .put("pivotY", 0)
                    .put("frameWidth", tileWidth)
                    .put("frameHeight", tileHeight)
            )
        }
        return sprites
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
        for (scene in game.scenes) {
            walkOps(scene.id, scene.enterOps, result)
            walkOps(scene.id, scene.frameOps, result)
            walkOps(scene.id, scene.exitOps, result)
        }
        return result.mapValues { it.value.toList() }
    }

    /**
     * Recursively walks [ops] and collects [ControlMapping]s into [result].
     *
     * Descends depth-first into [IfOp] branches, [WhileOp], [ForOp], [FadeOp], [PoolDestroyActor]
     * death-callbacks, and [PoolForEachActive] bodies. For each [IfOp] whose condition is a
     * [CallExpr] matching an input-function name (`dpad_held`, `dpad_pressed`, `button_held`,
     * `button_pressed`), extracts the button from the first arg and records a [ControlMapping]
     * under [sceneId].
     *
     * Promoted from a local function inside [extractControls] to reduce cognitive complexity of the
     * outer function (SonarCloud S3776 E-13 / E-19).
     */
    private fun walkOps(
        sceneId: String,
        ops: List<ScriptOp>,
        result: LinkedHashMap<String, LinkedHashSet<ControlMapping>>,
    ) {
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
                    walkOps(sceneId, op.then, result)
                    walkOps(sceneId, op.otherwise, result)
                }
                is WhileOp -> walkOps(sceneId, op.body, result)
                is ForOp -> walkOps(sceneId, op.body, result)
                is FadeOp -> walkOps(sceneId, op.after, result)
                is PoolDestroyActor -> walkOps(sceneId, op.deathCallbackOps, result)
                is PoolForEachActive -> walkOps(sceneId, op.body, result)
                else -> {}
            }
        }
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

        // Phase 13.8 Plan 06 (D-01 / Req 6): Populate SceneIR.allocatedZoneBank on each scene
        // so the IR itself carries the single-source bank truth consumed by SceneVisitor.
        // Each scene gets the bank of its first resolved zone ref (or null for sceneless zones).
        // This replaces the direct zoneBankAllocation map lookup at SceneVisitor time with an
        // explicit IR-level field, preventing future divergence if allocateZoneBanks were called
        // with inconsistent inputs at different pipeline stages (D-01 field-over-lookup).
        val gameIRWithBanks =
            if (bankAllocation.isNotEmpty() && gameIR.zones.isNotEmpty()) {
                val scenesWithBanks =
                    gameIR.scenes.map { scene ->
                        val zoneBank = scene.zoneRefs.firstNotNullOfOrNull { bankAllocation[it] }
                        if (zoneBank != null) scene.copy(allocatedZoneBank = zoneBank) else scene
                    }
                gameIR.copy(scenes = scenesWithBanks)
            } else {
                gameIR
            }

        val homeFile = buildHomeFile(gameIRWithBanks, bankAllocation)
        // Plan 07.4-22: bankAllocation threads into buildSceneFile so the cross-bank guard for
        // genre-spliced set_bkg_tiles RawOps can resolve which ROM bank each `_zone_<id>_tiles`
        // const lives in and emit a SWITCH_ROM(<bank>) when the bank > 1 (cross-bank from
        // race_enter's bank 1).
        val sceneFile = buildSceneFile(gameIRWithBanks, bankAllocation)

        // Plan 09.1-05 (gap-closure 2026-05-14, Option B): single-scene-HOME fast-path.
        //
        // When BankingAnalysisPass (Plan 09.1-04) assigns ALL scenes to bank 0 (HOME), the
        // pipeline folds the scene functions into main.c and suppresses bank1.c entirely.
        // This is the pipeline-layer companion to Plan 04's analysis-layer fast-path:
        //   BankSlot(bank=0) [Plan 04] → no bank1.c [Plan 05] → detectMaxBank returns 0
        //   → CompileRomTask does NOT invoke readMbcType upgrade → ROM byte 0x0147 = 0x00.
        //
        // Predicate uses `all { ... }` (not `first { ... }`) — a mixed-bank GameIR (one scene
        // at bank 0, another at bank 1) must NOT enter this path; the bank-1 scene's functions
        // would lose their banking context. Per D-04 anti-overfitting.
        //
        // SceneVisitor already sets isBanked=false when bankSlot.bank==0 (line 77 of
        // SceneVisitor.kt: `val sceneBanked = sceneBank == null || sceneBank > 0`), so no
        // BANKED-stripping is required when folding into HOME.
        val allScenesInHome =
            gameIRWithBanks.scenes.isNotEmpty() &&
                gameIRWithBanks.scenes.all { (it.bankSlot?.bank ?: 1) == 0 }

        return if (allScenesInHome) {
            // Fold scene functions + defines + includes into homeFile; omit bank1.c from output.
            val mergedHome =
                homeFile.copy(
                    functions = homeFile.functions + sceneFile.functions,
                    defines = homeFile.defines + sceneFile.defines,
                    includes = (homeFile.includes + sceneFile.includes).distinct(),
                )
            // Pass empty sentinel sceneFile to buildHeaderFile so scene prototypes appear under
            // homeFunctionPrototypes (non-BANKED) rather than sceneFunctionPrototypes (BANKED).
            val emptySceneFile = sceneFile.copy(functions = emptyList())
            val headerFile =
                buildHeaderFile(gameIRWithBanks, mergedHome, emptySceneFile, bankAllocation)
            val tilemapBankFiles = buildTilemapBankFiles(gameIRWithBanks, bankAllocation)
            listOf(mergedHome, headerFile) + tilemapBankFiles
        } else {
            val headerFile = buildHeaderFile(gameIRWithBanks, homeFile, sceneFile, bankAllocation)
            val tilemapBankFiles = buildTilemapBankFiles(gameIRWithBanks, bankAllocation)
            listOf(homeFile, sceneFile, headerFile) + tilemapBankFiles
        }
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
                // Manual override: trust developer, log warning.
                // Route through stderr so Gradle surfaces it at warning-level instead of
                // interleaving with stdout task output (IN-01 in 10.1-REVIEW.md).
                System.err.println(
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
                val tileArrays = zones.mapNotNull { zone ->
                    val zoneSanitized = zone.id.replace('-', '_').replace(' ', '_')
                    val tileData = zone.tileData
                    // Plan 11.1-17 (Phase C): NEW-path zones (zone.tilesetPath != null)
                    // consume the Gradle-task-emitted _zone_<id>_tilemap symbol instead
                    // of this stub. Skip emitting the stub to avoid orphan dead C (user
                    // memory feedback_quality_over_shortcuts.md). LEGACY-path zones
                    // (tilesetPath == null) and procedurally-authored zones (tileData
                    // non-empty) are preserved — racer's _zone_track1_tiles[361] is
                    // unaffected. See Plan 11.1-15 SUMMARY for root-cause analysis.
                    if (zone.tilesetPath != null && tileData.isEmpty()) {
                        return@mapNotNull null
                    }
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
                            io.github.gbkt.backend.gbdk.codegen.ast.CArray(CConst(CU8), arraySize),
                        initializer = CRawExpr("{ $initValues }"),
                    )
                }
                CFile(
                    name = "zone_bank$bankNum.c",
                    bank = bankNum,
                    includes = listOf(GBDKIncludes.GAME_H),
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
            // Plan 11.1-17 (Phase C — deprecated-path consistency): same guard as
            // buildTilemapBankFiles: skip the stub for NEW-path zones so the two
            // codegen paths stay aligned and both omit the dead-C orphan stub.
            if (zone.tilesetPath != null && tileData.isEmpty()) {
                // Tileset ID constant still emitted — TILESET_<id> is used in scene
                // navigation / debug even when the tile-data array is skipped.
                zoneDefines += CDefine("TILESET_${zoneSanitized.uppercase()}", "${idx + 1}")
                continue
            }
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
        // Per-metasprite runtime attribute variables: _<id>_flipX, _<id>_flipY, _<id>_subPalette
        // (UINT8, init 0).
        // These are written by DSL `metaspriteRef.flipX set Expr` / `.flipY set Expr` /
        // `.subPalette set Expr`
        // assignments (lowered by ScriptOpVisitor.visitAssign via sanitizeVarName("id.prop") →
        // "_id_prop").
        // Declaring them here ensures SDCC sees a proper UINT8 global before any assignment.
        // subPalette range: 0..3 (GBC). On DMG the CGB palette bits are ignored by hardware — no
        // conditional codegen needed (RESEARCH §4, D-08: set_sprite_prop writes unconditionally).
        val metaspriteRuntimeVars =
            gameIR.metasprites.flatMap { ms ->
                listOf(
                    CVarDecl(name = "_${ms.id}_flipX", type = CU8, initializer = CLiteral(0)),
                    CVarDecl(name = "_${ms.id}_flipY", type = CU8, initializer = CLiteral(0)),
                    CVarDecl(name = "_${ms.id}_subPalette", type = CU8, initializer = CLiteral(0)),
                )
            }
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
                metaspriteRuntimeVars +
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

        // Pool template actor IDs — these actors use dynamic OAM allocation (not static slots).
        // They are excluded from update_sprites() and static OAM init to prevent OAM slot
        // collisions between pool instances and statically-assigned actors.
        val poolTemplateActorIds =
            gameIR.actorPools
                .map { pool ->
                    pool.actorTemplateId.replace('-', '_').replace('.', '_').replace(' ', '_')
                }
                .toSet()

        // update_sprites() — per-frame OAM sync for all actors with sprites
        // Pool template actors are excluded (they use dynamic OAM slots, not static ones)
        val updateSpritesFn =
            ActorVisitor.generateUpdateSprites(gameIR.actors, excludeIds = poolTemplateActorIds)

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

        // Plan 07.4-30 / D-N-SWITCHROM-RESTORE: HOME-bank wrapper for cross-bank set_bkg_tiles.
        // See buildBkgTilesLoadBankedHelper() for the full rationale.
        val crossBankHelper = buildCrossBankHelperList(gameIR, bankAllocation)

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
        // See buildPaletteDataRaw() for the full rationale (Plan 10.1-22 / D-08 / SEED-014).
        val paletteDataRaw = buildPaletteDataRaw(gameIR)

        // Metasprite descriptor arrays — sprite_metasprite_N[] per-frame OAM descriptor arrays
        // and sprite_metasprites[] pointer table. Required by move_metasprite_*() call sites
        // emitted by ScriptOpVisitor.visitMoveMetasprite() via generateMetaspriteFrameSwitch().
        // Plan 10-15 D-05 fix: generateMetaspriteDescriptor() was implemented in Plan 10-06 but
        // never called from the pipeline orchestrator — causing lcc "Undefined identifier
        // 'sprite_metasprites'" errors at all move_metasprite_*() call sites.
        //
        // Plan 13.3-05 D-01 Path A branch: for asset-driven metasprites (spritePath != null),
        // skip generateMetaspriteDescriptor() — the gbkt frame-array definitions are replaced
        // by png2asset-native arrays in the #included .c sidecar (wired in Plan 13.3-06).
        // Instead, emit a Path A reference comment (via generateAssetDrivenDescriptor) that
        // documents the <id>_metasprites[idx] usage pattern and satisfies D-01 codegen-shape
        // assertions.
        // Only escape-hatch metasprites (spritePath == null) emit the legacy frame arrays.
        // RESEARCH Pitfall 1: gate lives HERE (single location) to avoid duplicate-symbol risk.
        val metaspriteDescriptorRaw =
            gameIR.metasprites
                .joinToString("\n") { ms ->
                    if (ms.spritePath != null) {
                        MetaspriteVisitor.generateAssetDrivenDescriptor(ms).code
                    } else {
                        MetaspriteVisitor.generateMetaspriteDescriptor(ms).code
                    }
                }
                .takeIf { gameIR.metasprites.isNotEmpty() }

        // Phase 12 D-12a — is_tile_solid() HOME-bank NONBANKED helper (SWITCH_ROM wrapper;
        // called from tilemap-physics branch in PlatformerVisitor; Plan 12-11 wires the
        // 5-point AABB probe to this helper). Emitted as a rawSection because the typed C AST
        // has no NONBANKED keyword (same justification as the SWITCH_ROM CRawCode usage in
        // buildBkgTilesLoadBankedHelper at line 1938 — GBDK macros not in the AST).
        // Gated on gameUsesTilemapCollision(gameIR); null when no zone uses tilemap collision.
        val isTileSolidHelperRaw = buildIsTileSolidHelperIfNeeded(gameIR)

        // Phase 12 D-13 — _bkg_set_level_submap_banked() HOME-bank NONBANKED helper
        // (called from platformer_camera_update column-scroll branch — Plan 12-11 wires the
        // call site). Same shape, same gate, same justification as is_tile_solid above:
        // a HOME-bank SWITCH_ROM(_current_area_bank) wrapper around set_bkg_submap() so the
        // cross-bank tilemap copy executes safely (MBC remap of 0x4000-0x7FFF cannot affect
        // instruction fetches because the helper lives at 0x0000-0x3FFF). Emitted as a
        // rawSection because the typed C AST has no NONBANKED keyword.
        val bkgSetLevelSubmapHelperRaw = buildSetLevelSubmapHelperIfNeeded(gameIR)

        // Phase 12 D-02 / D-08 anchor 5 — setup_current_level() HOME-bank NONBANKED function
        // (Plan 12-17 Task 2). Same shape, same gate, same justification as is_tile_solid /
        // _bkg_set_level_submap_banked above: gated on gameUsesTilemapCollision; null when the
        // game does not opt into tilemap collision. Plan 12-09b's anchor 5 emission test locks
        // the function signature + the `_current_level = _next_level` assignment + the
        // `switch (_current_level` substring.
        val setupCurrentLevelFunctionRaw =
            buildSetupCurrentLevelFunctionIfNeeded(gameIR, bankAllocation)

        // Phase 12.6 D-06 — `_level_spawn_x[]` / `_level_spawn_y[]` const arrays
        // (HOME bank). Emitted BEFORE setupCurrentLevelFunctionRaw in the raw-section list so
        // the forward-declaration order in main.c is: arrays first, then the function that
        // reads them. Gated on `gameUsesTilemapCollision(gameIR)` so non-platformer games stay
        // byte-identical (D-14 7-target sweep — pong, breakout, simple-physics, metasprites,
        // metasprites-stress, banks, racer).
        val levelSpawnTablesRaw = buildLevelSpawnTablesIfNeeded(gameIR)

        val allRawSections =
            buildHomeFileRawSections(
                collectionDataRaw,
                collectionFunctionsRaw,
                paletteDataRaw,
                metaspriteDescriptorRaw,
                isTileSolidHelperRaw,
                bkgSetLevelSubmapHelperRaw,
                levelSpawnTablesRaw,
                setupCurrentLevelFunctionRaw,
            )

        // Tile collision system — const arrays + lookup functions (HOME-bank)
        // G2: Scenes with collisionData emit map_<scene>_collision[] and _map_collision_<scene>()
        val collisionVisitor = CollisionVisitor(gameIR)
        val (collisionArrays, collisionFunctionsRaw) = collisionVisitor.buildCollisionCodegen()

        // When exploration systems exist but no scenes have collision data, a stub is generated.
        val collisionFunctions = buildCollisionFunctionsWithFallback(gameIR, collisionFunctionsRaw)

        // Puzzle object state variables and interaction check functions
        val systemVisitor = GBDKSystemVisitor(gameIR, bankAllocation)
        val (puzzleVars, puzzleFunctions) = systemVisitor.buildPuzzleObjectFunctions(gameIR)

        // Actor pool state variables (_pool_<id>_active[], _pool_<id>_x[], _pool_<id>_y[],
        // _pool_<id>_oam[])
        val actorPoolStateVars = GBDKSystemVisitor.buildActorPoolStateVars(gameIR)

        // Zone tilemap defines + arrays — banking vs. legacy path dispatched in helper.
        val (zoneDefines, zoneArrays) = buildZoneDefsForHome(gameIR, bankAllocation)

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

        // Phase 12 D-12a — tilemap-collision HOME-bank state (active when any zone has
        // solidThreshold set OR the platformer_physics system carries a non-null solidThreshold).
        // The 5 globals are referenced by the HOME-bank NONBANKED `is_tile_solid()` helper emitted
        // alongside them; both are gated on `gameUsesTilemapCollision(gameIR)` so existing games
        // without tilemap collision are byte-identical (no extra globals, no extra function).
        val tilemapCollisionGlobals = buildTilemapCollisionGlobals(gameIR)

        // Actor pools use static OAM assignment (computed at codegen time). No _oam_free_list or
        // _oam_free_top globals are needed — the dynamic free list infrastructure was removed to
        // fix
        // an OAM out-of-bounds write bug (multi-tile pool sprites writing past shadow_OAM[39]).

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
                    actorPoolStateVars +
                    tilemapCollisionGlobals)
                .distinctBy { it.name }

        // Inventory: container operation functions, use_item dispatchers, drop table functions
        val inventoryFunctions = buildInventoryFunctions(gameIR)

        // Actor pool lifecycle functions (pool_<id>_init, pool_<id>_spawn, pool_<id>_destroy)
        // Static OAM assignment: no spawn_actor()/destroy_actor()/init_oam_free_list() generated.
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

        // All #include directives for main.c (base, sound, palette, metasprite, sprite assets,
        // zone tileset headers). See buildAllHomeFileIncludes() for full rationale.
        val allIncludes = buildAllHomeFileIncludes(gameIR, soundVisitor)

        val menuCursorDefines = buildMenuCursorDefinesForHome(gameIR)
        val audioMixerDefines = buildAudioMixerDefinesForHome(gameIR)
        val entityCollisionDefines = buildEntityCollisionDefinesForHome(gameIR)
        val atbDefines = buildAtbDefinesForHome(gameIR)

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
                    crossBankHelper +
                    npcCollisionFunctions +
                    monsterCombatStubs +
                    callOpStubFunctions +
                    trampolineStubs +
                    listOf(navigateFn, mainFn),
        )
    }

    // =========================================================================
    // buildHomeFile extracted helpers
    // =========================================================================

    /**
     * Emits the HOME-bank wrapper for cross-bank set_bkg_tiles when needed.
     *
     * Required for sport_racing games and scene-to-zone binder games whose tile arrays are
     * allocated to bank > 1 (Plan 07.4-30 / SEED-014 / Phase 11.1 D-01).
     */
    private fun buildCrossBankHelperList(
        gameIR: GameIR,
        bankAllocation: Map<String, Int>,
    ): List<CFunction> {
        val hasSportRacing =
            gameIR.systems.filterIsInstance<GenericSystem>().any {
                (it.config["type"] as? String) == "sport_racing"
            }
        // SEED-014 (Phase 11.1 D-01): un-gate from sport_racing-only to also admit games with
        // scene-to-zone binder DSL.
        val hasZoneSceneBinder = gameIR.scenes.any { it.zoneRefs.isNotEmpty() }
        return if ((hasSportRacing || hasZoneSceneBinder) && bankAllocation.values.any { it > 1 }) {
            listOf(buildBkgTilesLoadBankedHelper())
        } else {
            emptyList()
        }
    }

    /**
     * Builds the GBC palette raw-C section for main.c.
     *
     * Emits `const palette_color_t {name}_pal[4]` for each palette, and appends
     * `_gbkt_default_bg_pal[4]` for any non-DMG target (Plan 10.1-22 / D-08).
     */
    private fun buildPaletteDataRaw(gameIR: GameIR): String? {
        val gbktDefaultBgPalRaw =
            if (gameIR.config.gbcTarget != GbcTarget.DMG) {
                "const palette_color_t _gbkt_default_bg_pal[4] = {0x7FFF, 0x56B5, 0x294A, 0x0000};"
            } else {
                null
            }
        return buildList {
                gameIR.palettes.forEach { palette ->
                    add(
                        "const palette_color_t ${palette.name}_pal[4] = {${palette.toCArrayLiteral()}};"
                    )
                }
                if (gbktDefaultBgPalRaw != null) add(gbktDefaultBgPalRaw)
            }
            .joinToString("\n")
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Assembles the ordered list of raw C sections for main.c.
     *
     * All sections are optional (null or empty = omitted). Order is fixed: collection data →
     * collection functions → palettes → metasprite descriptors → is_tile_solid helper →
     * _bkg_set_level_submap helper → level spawn tables → setup_current_level function.
     */
    private fun buildHomeFileRawSections(
        collectionDataRaw: String,
        collectionFunctionsRaw: String,
        paletteDataRaw: String?,
        metaspriteDescriptorRaw: String?,
        isTileSolidHelperRaw: String?,
        bkgSetLevelSubmapHelperRaw: String?,
        levelSpawnTablesRaw: String?,
        setupCurrentLevelFunctionRaw: String?,
    ): List<String> = buildList {
        if (collectionDataRaw.isNotEmpty()) add(collectionDataRaw)
        if (collectionFunctionsRaw.isNotEmpty()) add(collectionFunctionsRaw)
        if (paletteDataRaw != null) add(paletteDataRaw)
        if (metaspriteDescriptorRaw != null) add(metaspriteDescriptorRaw)
        if (isTileSolidHelperRaw != null) add(isTileSolidHelperRaw)
        if (bkgSetLevelSubmapHelperRaw != null) add(bkgSetLevelSubmapHelperRaw)
        if (levelSpawnTablesRaw != null) add(levelSpawnTablesRaw)
        if (setupCurrentLevelFunctionRaw != null) add(setupCurrentLevelFunctionRaw)
    }

    /**
     * Returns collision functions for HOME bank; generates a stub when exploration systems exist
     * but no scenes have collision data (so the movement system always has a callable target).
     */
    private fun buildCollisionFunctionsWithFallback(
        gameIR: GameIR,
        collisionFunctionsRaw: List<CFunction>,
    ): List<CFunction> {
        val hasExplorationSystem =
            gameIR.systems.any { it is io.github.gbkt.core.ir.ExplorationSystem }
        return if (collisionFunctionsRaw.isEmpty() && hasExplorationSystem) {
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
    }

    /**
     * Returns zone defines and zone arrays for the HOME file.
     *
     * Banking path (bankAllocation non-empty): tile arrays live in zone_bankN.c — emit defines
     * only. Legacy path (bankAllocation empty + zones present): emit both arrays and defines in
     * HOME.
     */
    private fun buildZoneDefsForHome(
        gameIR: GameIR,
        bankAllocation: Map<String, Int>,
    ): Pair<List<CDefine>, List<CVarDecl>> =
        if (bankAllocation.isEmpty() && gameIR.zones.isNotEmpty()) {
            val (arrays, defines) = buildZoneData(gameIR)
            Pair(defines, arrays)
        } else {
            Pair(buildZoneDefines(gameIR), emptyList())
        }

    /**
     * Assembles all #include directives for main.c.
     *
     * Order: base GBDK headers → hUGEDriver (music) → CGB palette → metasprite → sprite asset
     * headers → zone tileset headers.
     */
    private fun buildAllHomeFileIncludes(gameIR: GameIR, soundVisitor: SoundVisitor): List<String> {
        val hUGEInclude =
            if (soundVisitor.hasMusicOps()) listOf(GBDKIncludes.HUGE_DRIVER_H) else emptyList()
        val cgbHomeInclude =
            if (gameIR.palettes.isNotEmpty()) listOf(GBDKIncludes.CGB_H) else emptyList()
        val metaspriteInclude =
            if (gameIR.metasprites.isNotEmpty()) listOf(GBDKIncludes.METASPRITES_H) else emptyList()
        val actorSpriteIncludes =
            gameIR.actors
                .mapNotNull { actor -> actor.sprite?.assetRef?.path }
                .distinct()
                .map { path -> "\"${path.substringBeforeLast('.')}.h\"" }
        val metaspriteSpriteIncludes = gameIR.metasprites.map { ms -> "\"sprites/${ms.id}.h\"" }
        val spriteIncludes = (actorSpriteIncludes + metaspriteSpriteIncludes).distinct()
        val homeZoneTilesetIncludes =
            gameIR.zones
                .filter { it.tilesetPath != null }
                .map { zone ->
                    val sanitized = zone.id.replace('-', '_').replace(' ', '_')
                    "\"_zone_${sanitized}_tileset.h\""
                }
                .distinct()
        return GBDKIncludes.homeFileBase() +
            hUGEInclude +
            cgbHomeInclude +
            metaspriteInclude +
            spriteIncludes +
            homeZoneTilesetIncludes
    }

    /** MENU_CURSOR_SPRITE_ID #define — emitted when any menu uses a sprite cursor. */
    private fun buildMenuCursorDefinesForHome(gameIR: GameIR): List<CDefine> =
        if (gameIR.menus.any { it.cursorSprite != null }) {
            listOf(CDefine("MENU_CURSOR_SPRITE_ID", "${MenuVisitor.MENU_CURSOR_SPRITE_ID}"))
        } else {
            emptyList()
        }

    /** MIXER_GROUP_* #define constants — emitted when any audio_mixer GenericSystem exists. */
    @Suppress("UNCHECKED_CAST")
    private fun buildAudioMixerDefinesForHome(gameIR: GameIR): List<CDefine> =
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

    /**
     * Entity collision #define constants (MAX_ENTITIES, MAP_SIZE) — emitted when an exploration
     * system exists and at least one actor has non-PASSTHROUGH entity collision config.
     */
    private fun buildEntityCollisionDefinesForHome(gameIR: GameIR): List<CDefine> {
        val explorationSystem =
            gameIR.systems
                .filterIsInstance<io.github.gbkt.core.ir.ExplorationSystem>()
                .firstOrNull()
        if (explorationSystem == null) return emptyList()
        val collisionActors =
            gameIR.actors.filter {
                val ec = it.entityCollision
                ec != null && ec.mode != EntityCollisionMode.PASSTHROUGH
            }
        return if (collisionActors.isNotEmpty()) {
            listOf(CDefine("MAX_ENTITIES", "${collisionActors.size}"), CDefine("MAP_SIZE", "129"))
        } else {
            emptyList()
        }
    }

    /**
     * ATB #define constants (ATB_BASE_RATE, ATB_MAX_GAUGE) — emitted once when any
     * CombatEngineSystem with combatType==ATB exists.
     */
    private fun buildAtbDefinesForHome(gameIR: GameIR): List<CDefine> =
        gameIR.systems
            .filterIsInstance<CombatEngineSystem>()
            .firstOrNull { it.combatType == io.github.gbkt.core.ir.CombatType.ATB }
            ?.let { atbSystem ->
                val cfg = atbSystem.atbConfig
                listOf(
                    CDefine("ATB_BASE_RATE", "${cfg?.baseGaugeFillRate ?: 4}"),
                    CDefine("ATB_MAX_GAUGE", "${cfg?.maxGauge ?: 255}"),
                )
            } ?: emptyList()

    // =========================================================================
    // bank1.c — Scene functions (bank 1 or scene-assigned bank)
    // =========================================================================

    private fun buildSceneFile(
        gameIR: GameIR,
        bankAllocation: Map<String, Int> = emptyMap(),
    ): CFile {
        // Use the bank from the first scene that has a bankSlot, defaulting to 1
        val fileBank = gameIR.scenes.firstOrNull { it.bankSlot != null }?.bankSlot?.bank ?: 1

        // Build tileset ID map: unique tilesetRef paths → sequential IDs (1-based, 0xFF reserved)
        val tilesetIdMap = buildTilesetIdMap(gameIR)

        // Pass actors to SceneVisitor so ExprVisitor can resolve collides() AABB expressions
        val hudVisitor = HudVisitor(gameIR)
        // Inject GameIR into ScriptOpVisitor so pool context redirection can look up pool/actor
        // metadata (template actor ID, sprite tile dimensions) for forEachActive body compilation.
        // Without this call gameIRContext.get() returns null and body ops fall back to template
        // actor global scalars instead of per-instance pool arrays.
        ScriptOpVisitor.setGameIR(gameIR)
        // Collect per-scene frame ops contributed by genre visitors (e.g. racing() injects
        // racing_tick_<id>() into the bound scene). Keyed by scene id; consumed below in the
        // same prepend phase as update_movement / update_animation so injected ops run BEFORE
        // user-authored frame ops (RESEARCH Open Question #4 — physics first, user logic after).
        val genreFrameOps: Map<String, List<ScriptOp>> = collectGenreFrameOps(gameIR)
        // Collect per-scene enter ops contributed by genre visitors (Phase 07.4 Plan 10 —
        // mirror of frameOps for the scene-enter splice phase). SportVisitor (Plan 11) populates
        // this with pool spawn calls, zone tileset/tilemap loads, and _camera_target bind.
        val genreEnterOps: Map<String, List<ScriptOp>> = collectGenreEnterOps(gameIR)
        val sceneFunctions =
            gameIR.scenes.flatMap { scene ->
                // Plan 07.4-20: thread the scene context into ScriptOpVisitor so visitScreenClear
                // and visitPrintOp can lower scene-aware (non-destructive forms when the scene has
                // a BG tilemap; back-compat shapes otherwise). try/finally guarantees the context
                // is cleared even if a downstream visitor throws — leaking the flag would taint
                // subsequent scenes' codegen.
                //
                // Rebase coordination (Plan 07.4-22): the cross-bank SWITCH_ROM guard inserts
                // INSIDE this try block (operates on the same genreEnterOps[scene.id] list).
                val hasBg = sceneHasBgTilemap(scene.id, gameIR, genreEnterOps)
                ScriptOpVisitor.setSceneContext(scene.id, hasBg)
                try {
                    val functions =
                        SceneVisitor.visit(
                            scene,
                            gameIR.actors,
                            gameIR.variables,
                            zoneBankAllocation = bankAllocation,
                            zones = gameIR.zones,
                            gbcTarget = gameIR.config.gbcTarget != GbcTarget.DMG,
                            isMbcGame = gameIR.config.cartridge.maxRomBanks > 2,
                        )
                    // Plan 10.1-09 (WR-05 / SEED-011): hoist the per-call `hiwater` declaration
                    // + trailing `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES)` OUT of the
                    // MetaspriteVisitor per-call switch block and INTO the scene frame function
                    // (one prelude `uint8_t hiwater = 0u;` + one postlude `hide_sprites_range`
                    // per frame, regardless of how many `moveMetasprite()` ops the scene contains).
                    // Without this hoist, a frame that calls `moveMetasprite()` twice resets the
                    // OAM cursor between calls (the second `hiwater = 0u` wipes the slot count
                    // from the first metasprite) — the second `hide_sprites_range` then hides the
                    // first metasprite's OAM slots. Phase 12 (platformer_template) prerequisite.
                    val functionsWithHiwater = wrapFrameWithMetaspriteHiwater(functions, scene)
                    // Wire update_movement and update_animation calls into frame functions
                    val functionsWithUpdates =
                        addMovementAndAnimationCalls(functionsWithHiwater, scene.id, gameIR)
                    // Wire genre-visitor-contributed frame ops (e.g. racing_tick_<id>())
                    val functionsWithGenreOps =
                        addGenreFrameOps(
                            functionsWithUpdates,
                            scene.id,
                            genreFrameOps[scene.id] ?: emptyList(),
                        )
                    // Wire genre-visitor-contributed enter ops BEFORE the tileset guard so the
                    // guard's prepend lands at the absolute head of the enter body. Final order:
                    // [tileset guard if-block, genre enter ops, user enter ops].
                    //
                    // Plan 07.4-22 — apply the cross-bank SWITCH_ROM guard to the genre enter
                    // ops BEFORE splicing them. This is a pure data transformation on the
                    // RawOp list (no scene-context side effects), so it lives inside Plan
                    // 20's try block without violating the "no new try/finally" rule from the
                    // plan's <rebase_note>. The guard prepends `SWITCH_ROM(<bank>)` only when
                    // a `set_bkg_tiles(..., _zone_<id>_tiles)` op references a zone allocated
                    // to a non-current ROM bank (bank > 1).
                    val guardedEnterOps =
                        guardCrossBankBgTilemapAccess(
                            genreEnterOps[scene.id] ?: emptyList(),
                            bankAllocation,
                        )
                    val functionsWithGenreEnter =
                        addGenreEnterOps(functionsWithGenreOps, scene.id, guardedEnterOps)
                    // Wire HUD update calls into frame functions
                    val functionsWithHud =
                        hudVisitor.addHudUpdateCalls(functionsWithGenreEnter, scene.id)
                    // C4: Wrap enter function with tileset reuse guard if scene has a tilesetRef
                    val tilesetId = scene.tilesetRef?.let { tilesetIdMap[it.path] }
                    if (tilesetId != null) {
                        addTilesetGuardToEnterFunction(functionsWithHud, scene.id)
                    } else {
                        functionsWithHud
                    }
                } finally {
                    ScriptOpVisitor.setSceneContext(null, false)
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
        val cgbInclude = if (gameIR.hasPaletteOps()) listOf(GBDKIncludes.CGB_H) else emptyList()

        // Plan 10.1-06 (CR-02 / SEED-009): per-bank `<gbdk/metasprites.h>` conditional include.
        // Phase 10 Plan 10 added the header unconditionally to main.c, but scene frame
        // functions that contain `MoveMetasprite` ops are emitted into bank1.c by this method
        // (anything that escapes BankingAnalysisPass's single-scene HOME fast-path lands here).
        // Without the header, the `move_metasprite_*` inline definitions don't resolve and the
        // bank file fails to link under SDCC. Route A (per PATTERNS §Pattern Assignments
        // lines 292-314): scan `gameIR.scenes` for any `MoveMetasprite` op in `frameOps` and
        // conditionally add the header, mirroring the `cgbInclude` per-bank-condition pattern
        // above. Regression guard: `Seed009BankIncludeTest` asserts both the positive
        // (header present when MoveMetasprite present) and negative (header absent when no
        // scene uses metasprites) cases.
        val needsMetasprites =
            gameIR.scenes.any { scene ->
                scene.frameOps.any { it is io.github.gbkt.core.ir.MoveMetasprite }
            }
        val metaspriteInclude =
            if (needsMetasprites) listOf(GBDKIncludes.METASPRITES_H) else emptyList()

        // Phase 11.2 (D-B3, D-B4): #include for each zone tileset header consumed by a scene
        // in this CFile. One zone per unique zoneRef across all scenes; header-guards make
        // duplicates safe but distinct() keeps the include list clean. Cross-ref: plan 05's
        // SceneVisitor zone-load block emits the matching `set_bkg_data(0u,
        // _zone_<id>_tileset_count, _zone_<id>_tileset);` CCall, and plan 03's Gradle task
        // synthesizes the `_zone_<sanitized>_tileset.h` header that supplies those symbols.
        val zoneTilesetIncludes =
            gameIR.scenes
                .flatMap { it.zoneRefs }
                .mapNotNull { zoneId -> gameIR.zones.firstOrNull { it.id == zoneId } }
                .filter { it.tilesetPath != null }
                .map { zone ->
                    val sanitized = zone.id.replace('-', '_').replace(' ', '_')
                    "\"_zone_${sanitized}_tileset.h\""
                }
                .distinct()

        return CFile(
            name = "bank1.c",
            bank = fileBank,
            includes =
                GBDKIncludes.sceneFileBase() + cgbInclude + metaspriteInclude + zoneTilesetIncludes,
            defines = tilesetDefines,
            functions = sceneFunctions,
        )
    }

    /**
     * Discriminator predicate (Plan 07.4-20): does scene [sceneId] have a background tilemap?
     *
     * A scene is considered to have a BG tilemap when EITHER:
     * 1. `SceneIR.tilesetRef != null` (the scene was authored with a `tileset(...)` DSL call), OR
     * 2. The genre enterOps splice for the scene paints the BG via GBDK's `set_bkg_data` /
     *    `set_bkg_tiles` calls (e.g. `racing { track }` in Sport genre causes
     *    `SportVisitor.enterOps` to splice these RawOps into `race_enter`).
     *
     * The predicate is consulted by [buildSceneFile] before lowering each scene's enter/frame/exit
     * script ops, and the result is threaded into [ScriptOpVisitor.setSceneContext]. It allows
     * [ScriptOpVisitor.visitScreenClear] to choose between bare `cls()` (back-compat for non-BG
     * scenes) and a non-destructive equivalent (`HIDE_SPRITES; _win_clear_region(...)`) that
     * preserves the painted BG tilemap.
     *
     * **RawOp text-match (intentional, scoped):** the genre splice path matches the literal
     * substrings `"set_bkg_tiles"` and `"set_bkg_data"`. These come from `SportVisitor`'s
     * controlled emissions (see `gbkt-genre-sport`); a user cannot inject arbitrary RawOp text via
     * the public DSL today. **Future direction:** when a structured `LoadBackgroundTilemap`
     * ScriptOp lands, replace this text match with a typed shape check. The JVM-tier
     * `ScreenClearSceneAwareTest` will surface a regression if this predicate stops detecting the
     * genre splice.
     */
    private fun sceneHasBgTilemap(
        sceneId: String,
        gameIR: GameIR,
        genreEnterOps: Map<String, List<ScriptOp>>,
    ): Boolean {
        val scene = gameIR.scenes.firstOrNull { it.id == sceneId } ?: return false
        if (scene.tilesetRef != null) return true
        val sceneGenreOps = genreEnterOps[sceneId] ?: emptyList()
        return sceneGenreOps.any { op ->
            op is RawOp && (op.code.contains("set_bkg_tiles") || op.code.contains("set_bkg_data"))
        }
    }

    /**
     * Returns true if a scene has content that generates an `_enter` function and thus requires an
     * enter trampoline in HOME bank and a dispatch case in `navigate_to_scene()`.
     *
     * A scene has enter content if:
     * - It has user-authored [SceneIR.enterOps] (DSL `enter { }` block), OR
     * - It has bound zone refs ([SceneIR.zoneRefs] non-empty) which the SceneVisitor auto-emits as
     *   `set_bkg_data` + `_bkg_tiles_load_banked` + `SHOW_BKG` operations in the enter function.
     *
     * Without this check, a scene whose `enter {}` block was removed but which still binds a zone
     * would silently lose its enter trampoline and initial enter call from `main()`, causing the
     * zone tilemap to never load and the title/nextLevel screen to render blank.
     */
    private fun sceneHasEnterContent(scene: SceneIR): Boolean =
        scene.enterOps.isNotEmpty() || scene.zoneRefs.isNotEmpty()

    /**
     * Returns true if any scene in the game IR contains a [SetPalette] script operation.
     *
     * Used to conditionally include `<gb/cgb.h>` in bank1.c — required for `set_bkg_palette()` and
     * `set_sprite_palette()` in GBDK GBC mode.
     */
    private fun GameIR.hasPaletteOps(): Boolean = scenes.any { scene ->
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

    /**
     * Wrap the `${scene.id}_frame` function body with a single `uint8_t hiwater = 0u;` prelude and
     * a single `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);` postlude — IFF the scene
     * contains at least one [io.github.gbkt.core.ir.MoveMetasprite] op in its [SceneIR.frameOps].
     *
     * Plan 10.1-09 (WR-05 / SEED-011) — closes the per-call hiwater collision discovered when a
     * scene calls `moveMetasprite()` more than once per frame. Pre-fix
     * `MetaspriteVisitor.generateMetaspriteFrameSwitch` emitted both the wrapping declaration AND
     * the trailing call PER moveMetasprite() invocation, so the second invocation's `hiwater = 0u`
     * RESET the slot counter, causing the second metasprite to clobber the first metasprite's OAM
     * allocation. Phase 12 (platformer_template) blocker.
     *
     * Route A per
     * `.planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/10.1-PATTERNS.md`
     * §Pattern Assignments lines 380-394: hoist the prelude/postlude to FRAME-FUNCTION scope so one
     * `uint8_t hiwater = 0u;` declaration serves every `move_metasprite_*` call in that frame. Each
     * call then only contributes `hiwater += move_metasprite_*(...)` inside the per-call switch
     * block (whose outer `{...}` brace scope is preserved by Plan 09 Task 3 so the inner `subpal`
     * local + Plan 04's `_<id>_subPalette`/`_<id>_flipX`/`_<id>_flipY` global writes stay legal
     * under C scoping — the inner block now references the outer function-scope `hiwater` declared
     * here).
     *
     * **Early-return contract:** scenes WITHOUT any `MoveMetasprite` op return the input
     * `functions` list unchanged — title/menu/pause scenes (and any scene that doesn't render a
     * metasprite this frame) do NOT pay the prelude/postlude cost. Regression-guarded by
     * `Seed011HiwaterFrameScopeTest.title_frame_body_without_metasprite_has_zero_hiwater_init`.
     *
     * **Implementation note (CRawCode):** `CRawCode` is the only `CStatement` subtype that accepts
     * a raw C string. The C AST has no typed `CRawStatement` (per
     * `gbkt-backend-gbdk/src/main/kotlin/io/github/gbkt/backend/gbdk/codegen/ast/CStatement.kt`) —
     * `CRawCode` already implements `CStatement` directly, so it can be used in `List<CStatement>`
     * contexts without wrapping.
     */
    private fun wrapFrameWithMetaspriteHiwater(
        functions: List<CFunction>,
        scene: SceneIR,
    ): List<CFunction> {
        val hasMetaspriteOps = scene.frameOps.any { it is io.github.gbkt.core.ir.MoveMetasprite }
        if (!hasMetaspriteOps) return functions
        return functions.map { fn ->
            if (fn.name != "${scene.id}_frame") {
                fn
            } else {
                val prelude: List<CStatement> = listOf(CRawCode("uint8_t hiwater = 0u;"))
                val postlude: List<CStatement> =
                    listOf(CRawCode("hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);"))
                fn.copy(body = prelude + fn.body + postlude)
            }
        }
    }

    /**
     * Collect per-scene frame [ScriptOp] sequences from every [GenreSystemVisitor] that handles a
     * [GenericSystem] in [gameIR]. Returns a map of scene id → ordered list of ScriptOps to prepend
     * to that scene's frame block.
     *
     * Mirrors [buildSystemFunctions] / [buildSystemGlobalVars] dispatch — same ServiceLoader, same
     * `canHandle(type)` test, same per-system invocation. The pipeline calls this once per
     * generation and uses [addGenreFrameOps] to splice the result into each scene frame.
     *
     * Ordering rule: when multiple systems contribute frame ops to the same scene, the systems
     * iterate in [GameIR.systems] declaration order; ops within a single system iterate in their
     * declared order. This matches the scene-frame "physics first, user logic after" prepend
     * convention from `addMovementAndAnimationCalls`.
     */
    @Suppress("UNCHECKED_CAST")
    private fun collectGenreFrameOps(gameIR: GameIR): Map<String, List<ScriptOp>> {
        val genreVisitors = ServiceLoader.load(GenreSystemVisitor::class.java).toList()
        val accumulator = mutableMapOf<String, MutableList<ScriptOp>>()
        for (system in gameIR.systems) {
            if (system !is GenericSystem) continue
            val systemType = system.config["type"] as? String ?: continue
            val genreVisitor = genreVisitors.find { it.canHandle(systemType) } ?: continue
            val result = genreVisitor.visit(systemType, system.config, gameIR)
            for ((sceneId, ops) in result.frameOps) {
                accumulator.getOrPut(sceneId) { mutableListOf() }.addAll(ops)
            }
        }
        return accumulator
    }

    /**
     * Prepend a list of [ScriptOp] from a genre visitor into the named scene's frame `CFunction`
     * body. Each ScriptOp is lowered through [ScriptOpVisitor] so that the same op semantics (e.g.
     * `RawOp("racing_tick_track1();")` → `CRawCode`) are honored as if the user had written the ops
     * in their own `frame { }` block.
     *
     * Genre frame ops are inserted AFTER `update_movement_<id>()` / `update_animation_<id>()`
     * (which `addMovementAndAnimationCalls` has already prepended) and BEFORE user-authored frame
     * ops, so the prepend order matches the scene frame's "physics → user" convention.
     */
    private fun addGenreFrameOps(
        functions: List<CFunction>,
        sceneId: String,
        ops: List<ScriptOp>,
    ): List<CFunction> {
        if (ops.isEmpty()) return functions
        val statements: List<CStatement> = ops.map { it.accept(ScriptOpVisitor) }
        return functions.map { fn ->
            if (fn.name == "${sceneId}_frame") {
                fn.copy(body = statements + fn.body)
            } else {
                fn
            }
        }
    }

    /**
     * Collect per-scene enter [ScriptOp] sequences from every [GenreSystemVisitor] that handles a
     * [GenericSystem] in [gameIR]. Mirror of [collectGenreFrameOps] for the scene-enter splice
     * phase.
     *
     * Used by [buildSceneFile] to inject genre setup (pool spawn calls, zone tileset/tilemap loads,
     * `_camera_target` bind) into the bound scene's enter block. Same ServiceLoader, same
     * `canHandle(type)` test, same per-system invocation.
     *
     * Ordering rule: when multiple systems contribute enter ops to the same scene, the systems
     * iterate in [GameIR.systems] declaration order; ops within a single system iterate in their
     * declared order.
     */
    @Suppress("UNCHECKED_CAST")
    private fun collectGenreEnterOps(gameIR: GameIR): Map<String, List<ScriptOp>> {
        val genreVisitors = ServiceLoader.load(GenreSystemVisitor::class.java).toList()
        val accumulator = mutableMapOf<String, MutableList<ScriptOp>>()
        for (system in gameIR.systems) {
            if (system !is GenericSystem) continue
            val systemType = system.config["type"] as? String ?: continue
            val genreVisitor = genreVisitors.find { it.canHandle(systemType) } ?: continue
            val result = genreVisitor.visit(systemType, system.config, gameIR)
            for ((sceneId, ops) in result.enterOps) {
                accumulator.getOrPut(sceneId) { mutableListOf() }.addAll(ops)
            }
        }
        return accumulator
    }

    /**
     * Prepend a list of [ScriptOp] from a genre visitor into the named scene's enter `CFunction`
     * body. Each ScriptOp is lowered through [ScriptOpVisitor]. Mirror of [addGenreFrameOps] but
     * targets `${sceneId}_enter` instead of `${sceneId}_frame`.
     *
     * Genre enter ops are inserted BEFORE user-authored enter ops (per scene-enter "setup → user
     * logic" convention) and BEFORE [addTilesetGuardToEnterFunction] runs in the buildSceneFile
     * pipeline — so when both apply, the final enter body order is: `[tileset guard if-block, genre
     * enter ops, user enter ops]`.
     */
    private fun addGenreEnterOps(
        functions: List<CFunction>,
        sceneId: String,
        ops: List<ScriptOp>,
    ): List<CFunction> {
        if (ops.isEmpty()) return functions
        val statements: List<CStatement> = ops.map { it.accept(ScriptOpVisitor) }
        return functions.map { fn ->
            if (fn.name == "${sceneId}_enter") {
                fn.copy(body = statements + fn.body)
            } else {
                fn
            }
        }
    }

    /**
     * Plan 07.4-22 — prepend `SWITCH_ROM(<bank>);` before any `set_bkg_tiles(...,
     * _zone_<id>_tiles)` RawOp whose referenced zone is allocated to a non-current ROM bank
     * ([bankAllocation]`[zoneId] > 1`). GBDK requires explicit MBC5 bank switching before any
     * cross-bank const data read; without it, `set_bkg_tiles`'s underlying `__memcpy` reads from
     * whatever bank is currently mapped at 0x4000–0x7FFF (typically bank 1, where `race_enter`
     * runs) and gets garbage tile data.
     *
     * The transformation is **pure data** — it operates on the [ScriptOp] list before the splice,
     * never touches [ScriptOpVisitor] scene-context state, never wraps a try/finally, and is safe
     * to call from inside Plan 07.4-20's per-scene try block. The output list is [ops] unchanged
     * when no cross-bank reference exists, so the additional cost on the common path is one regex
     * scan per RawOp.
     *
     * **Bank threshold:** `> 1` rather than `> 0` because `race_enter` lives in `bank1.c` — the CPU
     * is already on bank 1 at the call site, so the same-bank case (`bankAllocation == 1`) needs no
     * switch. HOME-resident data (bank 0) likewise needs no switch because HOME is always mapped at
     * 0x0000–0x3FFF.
     *
     * **RawOp text-match (intentional, scoped):** the regex matches the literal substring
     * `_zone_<id>_tiles` against the canonical naming convention used by SportVisitor's controlled
     * emissions. A user cannot inject crafted RawOp text via the public DSL today. **Future
     * direction:** when a structured `LoadBackgroundTilemap` ScriptOp lands, replace this text scan
     * with a typed shape check.
     */
    private fun guardCrossBankBgTilemapAccess(
        ops: List<ScriptOp>,
        bankAllocation: Map<String, Int>,
    ): List<ScriptOp> {
        if (ops.isEmpty() || bankAllocation.isEmpty()) return ops
        val zoneIdPattern = Regex("_zone_([a-zA-Z0-9_]+)_tiles")
        // Matches set_bkg_tiles(x, y, w, h, ptr): extracts x, y, w, h, and tiles symbol.
        // Params may be numeric literals with optional 'u' suffix (e.g. "19u") or plain int.
        val bkgTilesArgsPattern =
            Regex(
                """set_bkg_tiles\s*\(\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^,]+),\s*([^)]+)\s*\)"""
            )
        val result = mutableListOf<ScriptOp>()
        for (op in ops) {
            if (op is RawOp && op.code.contains("set_bkg_tiles") && op.code.contains("_zone_")) {
                val zoneMatch = zoneIdPattern.find(op.code)
                if (zoneMatch != null) {
                    val zoneId = zoneMatch.groupValues[1]
                    val bank = bankAllocation[zoneId] ?: 0
                    if (bank > 1) {
                        result += RawOp(buildBankedBkgTilesCallRaw(op, bank, bkgTilesArgsPattern))
                        continue
                    }
                }
            }
            result += op
        }
        return result
    }

    /**
     * Builds the raw C call string for a cross-bank `set_bkg_tiles` redirection to the HOME-bank
     * helper `_bkg_tiles_load_banked`. Falls back to the original [op] code when the args cannot be
     * parsed (should not happen for well-formed `set_bkg_tiles` RawOps).
     *
     * Plan 07.4-30 / D-N-SWITCHROM-RESTORE: SWITCH_ROM inside a BANKED function (bank1.c) is unsafe
     * — after SWITCH_ROM(N) the CPU reads bank N code at 0x4000-0x7FFF and the SWITCH_ROM(1)
     * restore is never reached. Routing through a HOME-bank (0x0000-0x3FFF, never remapped) helper
     * makes the sequence safe. Extracted from [guardCrossBankBgTilemapAccess] to reduce cognitive
     * complexity (E-24).
     */
    private fun buildBankedBkgTilesCallRaw(
        op: RawOp,
        bank: Int,
        bkgTilesArgsPattern: Regex,
    ): String {
        val argsMatch = bkgTilesArgsPattern.find(op.code) ?: return op.code
        val x = argsMatch.groupValues[1].trim()
        val y = argsMatch.groupValues[2].trim()
        val w = argsMatch.groupValues[3].trim()
        val h = argsMatch.groupValues[4].trim()
        val tiles = argsMatch.groupValues[5].trim()
        return "_bkg_tiles_load_banked(${bank}u, $x, $y, $w, $h, $tiles);"
    }

    /**
     * Phase 12 D-12a — predicate: does this game require tilemap-based collision codegen?
     *
     * Returns true when ANY of:
     * - **Path A** — A `GenericSystem` with type `"platformer_physics"` carries a `physicsConfig`
     *   whose `solidThreshold` property is non-null (the abstract tilemap-collision tile
     *   threshold).
     * - **Path B** — Any `ZoneIR` in `gameIR.zones` carries a `platformerPhysicsOverride` map that
     *   contains a non-null `"solidThreshold"` key (per-level override path).
     * - **Path C (Phase 12.1 Plan 05)** — A `GenericSystem` with type `"tilemap_collision"` is
     *   present. This is the canonical tilemap-physics symbol-binding home introduced by the new
     *   `tilemapCollision { }` builder (D-claude-4 — separation from platformerPhysics).
     *
     * Backend-gbdk does NOT depend on gbkt-genre-platformer, so the GenericSystem's `physicsConfig`
     * value is an opaque object — we use Java reflection to read the `solidThreshold` field without
     * taking a compile-time dependency on the genre module (mirrors the same pattern used for
     * opaque genre-config inspection elsewhere).
     *
     * When false, no `is_tile_solid()` helper is emitted and no supporting globals are declared —
     * existing games (Pong, Breakout, Explorer, RPG-Lite, Dungeon, Shmup, Racer) remain
     * byte-identical at the codegen layer.
     */
    private fun gameUsesTilemapCollision(gameIR: GameIR): Boolean {
        // Path C (Phase 12.1 Plan 05 Task 2) — explicit tilemap_collision GenericSystem.
        //
        // Registered via `GameBuilder.tilemapCollision { ... }` (gbkt-genre-platformer/
        // PlatformerExtensions.kt:tilemapCollision). The new system is the canonical home for
        // player-position-symbol binding (D-claude-4: separation from platformerPhysics) and
        // the visitor (Plan 12.1-06) reads its config keys to emit the tilemap-physics path.
        //
        // Placed BEFORE Path A so early-return saves the reflective `solidThreshold` field read
        // when the new system is present; Path A + Path B are preserved verbatim for back-compat
        // with games that opt into tilemap collision via `platformerPhysics { solidThreshold(N) }`.
        val systemIsTilemapCollision =
            gameIR.systems.filterIsInstance<GenericSystem>().any { sys ->
                (sys.config["type"] as? String) == "tilemap_collision"
            }
        if (systemIsTilemapCollision) return true

        // Path A — platformer_physics GenericSystem with non-null solidThreshold on physicsConfig
        val systemHasThreshold =
            gameIR.systems.filterIsInstance<GenericSystem>().any { sys ->
                (sys.config["type"] as? String) == "platformer_physics" &&
                    run {
                        val physicsConfig = sys.config["physicsConfig"]
                        if (physicsConfig == null) {
                            false
                        } else {
                            // Reflectively read `solidThreshold: Int?` without depending on the
                            // genre-platformer module. Field access is safe because
                            // PlatformerPhysicsConfig is a stable data class (see
                            // gbkt-genre-platformer/PlatformerTypes.kt). If the property goes
                            // missing the predicate returns false (degrades gracefully — no
                            // helper emission, no compile failure in unrelated genres).
                            try {
                                val field =
                                    physicsConfig.javaClass.getDeclaredField("solidThreshold")
                                field.isAccessible = true
                                field.get(physicsConfig) != null
                            } catch (_: NoSuchFieldException) {
                                false
                            } catch (_: SecurityException) {
                                false
                            }
                        }
                    }
            }
        if (systemHasThreshold) return true

        // Path B — per-zone platformerPhysicsOverride with solidThreshold key
        return gameIR.zones.any { zone ->
            zone.platformerPhysicsOverride?.containsKey("solidThreshold") == true
        }
    }

    /**
     * Phase 12 D-12a — emit the HOME-bank globals used by the `is_tile_solid()` helper + the Plan
     * 12-17 main()-loop level-switch substrate.
     *
     * Returns an empty list when `gameUsesTilemapCollision(gameIR) == false`, preserving
     * byte-identical codegen for games that do not opt into tilemap collision.
     *
     * **Plan 12-08 globals (D-12a — is_tile_solid substrate):**
     * - `_current_area_bank: UINT8` — ROM bank holding the active level's tilemap data
     * - `_current_level_map: const UINT8*` — pointer to the active level's tilemap array
     * - `_current_level_width_in_tiles: UINT16` — width of the active level in tiles
     * - `_current_level_height: UINT16` — height of the active level in PIXELS (matches the
     *   reference convention; `is_tile_solid` shifts right by 3 to get the row max)
     * - `_current_level_non_solid_tile_count: UINT8` — the `solidThreshold` value (tile indices `<
     *   this` are non-solid; the helper returns `tile < _current_level_non_solid_tile_count`)
     *
     * **Plan 12-17 globals (D-02 / D-08 anchor 5 — level-switch substrate):**
     * - `_current_level: UINT8` — the level index currently active (player tile-collides against
     *   this zone's tilemap). Read by the main()-loop level-switch guard (Plan 12-17 Task 2);
     *   written by `setup_current_level()` (this plan) when the guard fires.
     * - `_next_level: UINT8` — incremented by PlatformerVisitor.kt:802's level-end trigger when
     *   `player_real_x > _current_level_width - 32`. Compared against `_current_level` by the
     *   main()-loop guard; a mismatch triggers the NextLevel card scene + `setup_current_level()`.
     * - `_current_level_width: UINT16` — pixel width of the active level (referenced by
     *   PlatformerVisitor.kt:796's level-end trigger; populated by `setup_current_level()` from
     *   per-zone metadata). Distinct from `_current_level_width_in_tiles` (tile-count, used by
     *   `is_tile_solid` for tile-coord arithmetic — both globals encode the same level but in
     *   different units; this mirrors the reference's `level_t` struct in level.c where width
     *   appears as both `width` and `width_in_tiles`).
     *
     * Initial values are all zero. The platformer scene-enter codegen (Plan 12-11+) + Plan 12-17's
     * `setup_current_level()` populate them from the active zone's tilemap binding.
     */
    private fun buildTilemapCollisionGlobals(gameIR: GameIR): List<CVarDecl> {
        if (!gameUsesTilemapCollision(gameIR)) return emptyList()
        return listOf(
            CVarDecl(name = "_current_area_bank", type = CU8, initializer = CLiteral(0)),
            CVarDecl(
                name = "_current_level_map",
                type = CPointer(CConst(CU8)),
                initializer = CRawExpr("(const UINT8*)0"),
            ),
            CVarDecl(
                name = "_current_level_width_in_tiles",
                type = CU16,
                initializer = CLiteral(0),
            ),
            CVarDecl(name = "_current_level_height", type = CU16, initializer = CLiteral(0)),
            CVarDecl(
                name = "_current_level_non_solid_tile_count",
                type = CU8,
                initializer = CLiteral(0),
            ),
            // Plan 12-17 Task 2 — D-02 / D-08 anchor 5 substrate (level-switch).
            CVarDecl(name = "_current_level", type = CU8, initializer = CLiteral(0)),
            // Plan 12-19 deviation [Rule 1 - Bug]: initial = 0 to match _current_level (was 1
            // pre-12-19, but that caused the guard to fire on the very first main-loop iteration
            // — before the title scene rendered — and overwrite the title tileset+tilemap with
            // the gameplay level's data). Bootstrap now flows through gameplay_enter (which calls
            // setup_current_level via a DSL cEmit) rather than the main-loop guard, so the title
            // scene's tileset+tilemap stays in VRAM until the player presses Start. The guard
            // continues to handle mid-game level transitions correctly because
            // platformer_physics_update's level-end trigger increments _next_level, breaking the
            // 0==0 match and firing the navigate_to_scene(SCENE_NEXTLEVEL) + setup_current_level
            // pair on the next frame.
            CVarDecl(name = "_next_level", type = CU8, initializer = CLiteral(0)),
            CVarDecl(name = "_current_level_width", type = CU16, initializer = CLiteral(0)),
        )
    }

    /**
     * Phase 12 D-12a — `is_tile_solid(UINT16 world_x, UINT16 world_y) NONBANKED`.
     *
     * Returns a raw C source string when `gameUsesTilemapCollision(gameIR) == true`, else null.
     *
     * The function lives in HOME bank (`main.c`, 0x0000-0x3FFF, never remapped by MBC) and uses the
     * same SWITCH_ROM save/restore pattern as `buildBkgTilesLoadBankedHelper` at line 1938: save
     * CURRENT_BANK → SWITCH_ROM(_current_area_bank) → access tilemap → SWITCH_ROM(previous) →
     * return result. The NONBANKED keyword is mandatory: SDCC must place this function in HOME bank
     * (bank 0) so the SWITCH_ROM operates safely without remapping the instruction stream
     * containing it.
     *
     * Emission shape mirrors `gbdk/examples/cross-platform/platformer_template/src/level.c` lines
     * 40-68 (renamed identifiers to gbkt's `_current_level_*` convention to match the 5 globals
     * declared by `buildTilemapCollisionGlobals`).
     *
     * **Contract for Plan 12-09's per-function emission invariant test (D-16 invariant 2):**
     * - The function declaration MUST start with `UINT8 is_tile_solid` at column 0 (awk pattern
     *   `awk '/^UINT8 is_tile_solid/{p=1} p{...}' main.c` extracts the body via brace-walk).
     * - The body MUST contain exactly 2 `SWITCH_ROM` invocations (entry + exit).
     * - The body MUST contain the string `_current_level_non_solid_tile_count`.
     *
     * Emitted as a raw section because the typed C AST does not model the GBDK `NONBANKED` keyword
     * (same justification as the existing `CRawCode("SWITCH_ROM(...)")` usage in
     * `buildBkgTilesLoadBankedHelper` — GBDK macros / keywords not in the AST).
     */
    private fun buildIsTileSolidHelperIfNeeded(gameIR: GameIR): String? {
        if (!gameUsesTilemapCollision(gameIR)) return null
        return """
        // Phase 12 D-12a — is_tile_solid() HOME-bank NONBANKED helper
        // SWITCH_ROM wrapper; mirrors buildBkgTilesLoadBankedHelper pattern (line 1938).
        // Called from the tilemap-physics branch in PlatformerVisitor (5-point AABB probe — Plan 12-11).
        UINT8 is_tile_solid(UINT16 world_x, UINT16 world_y) NONBANKED {
            UINT8 _previous_bank = CURRENT_BANK;
            SWITCH_ROM(_current_area_bank);
            UINT16 column = world_x >> 3u;
            UINT16 row = world_y >> 3u;
            if (row > (_current_level_height >> 3u) || column >= _current_level_width_in_tiles) {
                SWITCH_ROM(_previous_bank);
                return TRUE;
            }
            UINT8 tile = _current_level_map[column + row * _current_level_width_in_tiles];
            SWITCH_ROM(_previous_bank);
            return tile < _current_level_non_solid_tile_count;
        }
        """
            .trimIndent()
    }

    /**
     * Phase 12 D-13 — `_bkg_set_level_submap_banked(UINT8 x, UINT8 y, UINT8 w, UINT8 h) NONBANKED`.
     *
     * HOME-bank wrapper around `set_bkg_submap()` that performs a SWITCH_ROM dance to the active
     * level's data bank before invoking the GBDK API, then restores the caller's bank. Mirrors the
     * same shape as `buildBkgTilesLoadBankedHelper` (line 1938) and
     * `buildIsTileSolidHelperIfNeeded` (Plan 12-08): save CURRENT_BANK →
     * SWITCH_ROM(_current_area_bank) → call set_bkg_submap → SWITCH_ROM(_previous_bank). The
     * NONBANKED keyword forces SDCC to place the function in HOME bank (0x0000-0x3FFF, never
     * remapped by the MBC) so the SWITCH_ROM cannot corrupt the instruction stream that contains
     * it.
     *
     * Returns a raw C source string when `gameUsesTilemapCollision(gameIR) == true`, else null.
     * Plan 12-11 wires the column-scroll branch of `platformer_camera_update` to call this helper
     * with the new column's tile coordinates; the helper is the cross-bank set_bkg_submap analog of
     * Phase 07.4-30's `_bkg_tiles_load_banked()`.
     *
     * Reference shape — `gbdk/examples/cross-platform/platformer_template/src/camera.c` lines 30-40
     * (SetCurrentLevelSubmap). The reference uses `current_level_width_in_tiles` directly; gbkt
     * mirrors that with the `_current_level_width_in_tiles` global declared by
     * `buildTilemapCollisionGlobals` (Plan 12-08). The width parameter passed to set_bkg_submap is
     * cast to `UINT8` because GBDK's set_bkg_submap signature takes `(UINT8 x, UINT8 y, UINT8 w,
     * UINT8 h, const unsigned char* tiles, UINT8 map_w)` — see GBDK gb.h.
     *
     * Emitted as a raw section because the typed C AST does not model the GBDK `NONBANKED` keyword
     * (same justification as `buildIsTileSolidHelperIfNeeded` above).
     *
     * **Contract for Plan 12-12's per-function emission invariant test:**
     * - Function declaration MUST start with `void _bkg_set_level_submap_banked` at column 0.
     * - Body MUST contain exactly 2 `SWITCH_ROM` invocations (entry + exit).
     * - Body MUST call `set_bkg_submap`.
     */
    private fun buildSetLevelSubmapHelperIfNeeded(gameIR: GameIR): String? {
        if (!gameUsesTilemapCollision(gameIR)) return null
        return """
        // Phase 12 D-13 — _bkg_set_level_submap_banked() HOME-bank NONBANKED helper
        // (called from platformer_camera_update column-scroll branch — Plan 12-11 wires the call site).
        // SWITCH_ROM wrapper around set_bkg_submap(); mirrors buildBkgTilesLoadBankedHelper / is_tile_solid
        // pattern. Lives in HOME bank so MBC remap of 0x4000-0x7FFF cannot affect instruction fetches.
        void _bkg_set_level_submap_banked(UINT8 x, UINT8 y, UINT8 w, UINT8 h) NONBANKED {
            UINT8 _previous_bank = CURRENT_BANK;
            SWITCH_ROM(_current_area_bank);
            set_bkg_submap(x, y, w, h, _current_level_map, (UINT8)_current_level_width_in_tiles);
            SWITCH_ROM(_previous_bank);
        }
        """
            .trimIndent()
    }

    // =========================================================================
    // Phase 12 D-02 / D-08 anchor 5 — level-switch substrate (Plan 12-17 Task 2)
    // =========================================================================

    /**
     * Phase 12 D-02 / D-08 anchor 5 — `setup_current_level()` HOME-bank NONBANKED function.
     *
     * Returns the function when `gameUsesTilemapCollision(gameIR) == true`, else null. The function
     * lives in HOME bank (0x0000-0x3FFF, never remapped by MBC), so it can safely issue
     * `SWITCH_ROM(...)` per zone to populate the active-level globals from per-zone banked tilemap
     * data without corrupting its own instruction stream.
     *
     * **Shape** (mirrors `gbdk/examples/cross-platform/platformer_template/src/level.c`
     * `SetupCurrentLevel()`):
     * ```c
     * void setup_current_level(void) NONBANKED {
     *     _current_level = _next_level;
     *     switch (_current_level % <zoneCount>) {
     *         case 0:  // first gameplay zone
     *             _current_area_bank = <literal-bank>u;  // Defect-6 fix (option c-prime — substitute literal bank from bankAllocation)
     *             _current_level_map = _zone_<id0>_tilemap;
     *             _current_level_width_in_tiles = <constant>;
     *             _current_level_width = <constant> * 8;
     *             _current_level_height = <constant> * 8;
     *             _current_level_non_solid_tile_count = <threshold>;
     *             break;
     *         case 1: ...
     *     }
     * }
     * ```
     *
     * **Plan 12-17 scope** (intentional STUB shape — full per-zone metadata wiring is Plan 12-18
     * territory):
     * - The function structure (switch + per-case stubs) is emitted here.
     * - Per-case body content is a single `// PLAN-12-18: populate from zone metadata` comment +
     *   reads/writes the canonical names so the SDCC linker resolves them at first buildRom (Plan
     *   12-18).
     * - `setup_current_level` is the SHAPE Plan 12-09b will lock for the anchor 5 emission
     *   invariant; the per-case body content is the SHAPE Plan 12-18 will lock for the per-zone
     *   tileset/tilemap/palette load.
     *
     * **Zone filtering**: only gameplay zones contribute cases — screen()-synthesized zones
     * (ZoneIR.screenMode == true) are excluded. The filter uses the semantic [ZoneIR.screenMode]
     * boolean field (CR-02 fix replacing the Phase-12 name-heuristic that filtered ids containing
     * "title" / "nextlevel" / "next_level"). The screenMode predicate is zero-ambiguity and
     * future-proofs any new screenMode use cases regardless of scene name.
     *
     * Emitted as a typed `CFunction` (not a raw section) because the body is structured C
     * (switch/case/break) that the typed AST models cleanly. The `NONBANKED` keyword is emitted by
     * overriding `sectionComment` to inject the modifier — wait, the AST has no NONBANKED modifier
     * surface. Two options: (a) Emit as a raw section (matches `buildIsTileSolidHelperIfNeeded`
     * pattern). (b) Emit as a typed `CFunction` with bank=0 (HOME) and the NONBANKED keyword is
     * implicit because HOME functions are always non-banked by default.
     *
     * **Chosen: (a)** — emit as raw section. Matches the existing `is_tile_solid` /
     * `_bkg_set_level_submap_banked` precedent. The NONBANKED keyword is explicit in the raw
     * source, which is the only way to guarantee SDCC places the function in HOME bank (without
     * NONBANKED, SDCC's default for HOME bank is non-banked but the keyword makes the contract
     * explicit and lints cleanly under SDCC's stricter banked-call analysis).
     *
     * **Contract for Plan 12-09b's anchor 5 emission test:**
     * - Function declaration MUST start with `void setup_current_level` at column 0.
     * - Body MUST assign `_current_level = _next_level` as the first statement.
     * - Body MUST contain a `switch (_current_level` substring.
     */
    internal fun buildSetupCurrentLevelFunctionIfNeeded(
        gameIR: GameIR,
        bankAllocation: Map<String, Int>,
    ): String? {
        if (!gameUsesTilemapCollision(gameIR)) return null
        // CR-02 fix: use the semantic screenMode field instead of a name-heuristic filter.
        // screenMode=true zones are synthetic screen() zones (never gameplay zones) regardless
        // of scene name — the old title/nextlevel string matching would miss any screen() zone
        // on a scene named e.g. "intro", "credits", "loading", "chapter1", etc.
        val gameplayZones = gameIR.zones.filter { zone -> !zone.screenMode }
        if (gameplayZones.isEmpty()) return null
        // Phase 12.6 D-06 — resolve player position/velocity symbol names from the
        // `tilemap_collision` GenericSystem config map. Hoisted ONCE per call (not per case)
        // because `tilemap_collision` is a game-level system, not a zone-level one — all
        // zones share the same player-symbol binding. Pattern mirrors PlatformerVisitor.kt:549-558
        // verbatim, so user-rebound `posXVar` / `posYVar` / `vxVar` / `vyVar` names propagate
        // consistently to BOTH the level-end trigger (PlatformerVisitor) AND the new per-level
        // spawn write below.
        val tcSystem =
            gameIR.systems.filterIsInstance<GenericSystem>().firstOrNull { sys ->
                (sys.config["type"] as? String) == "tilemap_collision"
            }
        val posXSym = "_" + ((tcSystem?.config?.get("posXVar") as? String) ?: "player_x")
        val posYSym = "_" + ((tcSystem?.config?.get("posYVar") as? String) ?: "player_y")
        val vxSym = "_" + ((tcSystem?.config?.get("vxVar") as? String) ?: "player_vx")
        val vySym = "_" + ((tcSystem?.config?.get("vyVar") as? String) ?: "player_vy")
        // Phase 12.9 D4 fix: resolve the grounded symbol from tilemap_collision config.
        // Mirrors the posXSym/vySym resolution above. The "groundedVar" key is registered by
        // PlatformerTemplate.kt's tilemapCollision { } block; the generated symbol is "_grounded".
        // Default "grounded" → "_grounded" when no explicit binding is present (fallback mirrors
        // the PlatformerVisitor.kt:579 resolution which uses the same key and default).
        val groundedSym = "_" + ((tcSystem?.config?.get("groundedVar") as? String) ?: "grounded")
        val caseBodies =
            gameplayZones
                .mapIndexed { idx, zone ->
                    val zoneSanitized = zone.id.replace('-', '_').replace(' ', '_')
                    // option (c-prime) per 12.1-VERIFICATION §Defect-6-Recommended-path —
                    // substitute
                    // literal bank from allocateZoneBanks; fallback to 1u (HOME-adjacent, safe) if
                    // missing. SDCC's `#pragma bank N` directive (emitted by Plan 12.1-01's
                    // ConvertZoneTilesetsTask) places the tilemap data array in bank N, but does
                    // NOT
                    // auto-synthesize a `__bank__zone_<id>_tilemap` symbol for data arrays (only
                    // for
                    // banked function definitions). Substituting the literal bank at the consumer
                    // site eliminates the link-time dependency on that never-existing symbol while
                    // preserving the bank placement.
                    val bank = bankAllocation[zone.id] ?: 1
                    val bankFallbackComment =
                        if (bankAllocation[zone.id] == null) {
                            " /* fallback: bankAllocation missing zoneId; safe HOME-adjacent */"
                        } else {
                            ""
                        }
                    // Per-zone solidThreshold override falls back to game-level (handled by Plan
                    // 12-07).
                    // For the Plan 12-17 STUB shape we emit a placeholder threshold of 17 (matches
                    // PlatformerTemplate.kt's game-level default); Plan 12-18 will source the
                    // threshold
                    // from `zone.platformerPhysicsOverride["solidThreshold"]` falling back to
                    // game-level.
                    val threshold =
                        (zone.platformerPhysicsOverride?.get("solidThreshold") as? Int) ?: 17
                    """
        case $idx:  // zone: ${zone.id}
            // PLAN-12-18: populate _current_area_bank / _current_level_map / width / height
            // from `_zone_${zoneSanitized}_tilemap` symbol + per-zone metadata. The Gradle
            // task `ConvertZoneTilesetsTask` emits the symbol at buildRom time (Plan 11.1-17
            // Phase C path), so the assignments below currently reference symbols that only
            // resolve when ConvertZoneTilesetsTask runs (Plan 12-18 first buildRom).
            _current_area_bank = ${bank}u;${bankFallbackComment}  // Defect-6 fix: literal bank from bankAllocation (option c-prime)
            _current_level_map = _zone_${zoneSanitized}_tilemap;
            _current_level_width_in_tiles = _zone_${zoneSanitized}_tilemap_WIDTH;
            _current_level_height = _zone_${zoneSanitized}_tilemap_HEIGHT * 8u;
            _current_level_width = _zone_${zoneSanitized}_tilemap_WIDTH * 8u;
            _current_level_non_solid_tile_count = ${threshold}u;
            // Plan 12-19 deviation [Rule 1 - Bug]: load this zone's tileset graphics into BG
            // VRAM tile 0+ and write its tilemap to the BG tilemap. Without these calls, the
            // gameplay scene renders whatever tileset+tilemap the prior scene (title) loaded —
            // the gameplay tilemap pointer is set in `_current_level_map` but never pushed
            // to VRAM. Matches the Banks Phase-11 contract (bank1.c title_enter:13-14) and
            // the reference's `set_native_tile_data(0, ..._TILE_COUNT, ..._tiles)` call inside
            // SetupCurrentLevel (level.c:93). The tileset symbol lives in HOME bank
            // (verified via .noi: `___bank__zone_<id>_tileset = 0x0`), so `set_bkg_data` reads
            // it without SWITCH_ROM. The tilemap lives in bank ${bank}u (per `#pragma bank` in
            // the tilemap .c file), so `_bkg_tiles_load_banked` switches to that bank, calls
            // `set_bkg_tiles`, and restores bank 1 — exactly the same helper used by
            // title_enter and nextLevel_enter, so the contract is consistent across all
            // scenes (title, nextLevel card, gameplay).
            set_bkg_data(0u, _zone_${zoneSanitized}_tileset_count, _zone_${zoneSanitized}_tileset);
            // Phase 12.9 RC-1 fix (Plan 12.9-08b) — upload THIS zone's authored palette right after
            // its tile data. Without it, gameplay zones inherit the title scene's BG palette RAM
            // (RC-1 palette inversion: sky renders WHITE, ground near-black). W5's SceneVisitor
            // palette fix is blind to this path because the gameplay scene has empty scene.zoneRefs
            // (cEmit("setup_current_level();"), not zone(...)) — RESEARCH Pitfall 7. The
            // _zone_<id>_tileset_PALETTE_COUNT macro + _zone_<id>_tileset_palettes extern + <gb/cgb.h>
            // include are all provided by W4's ConvertZoneTilesetsTask, so no header work is needed.
            // Emitted unconditionally to mirror the set_bkg_data line above and the main() startup
            // _gbkt_default_bg_pal upload — cgb_compatibility() makes set_bkg_palette a no-op on DMG
            // (RESEARCH RC-1d). Mirrors the reference level.c setBKGPalettes-after-tileset-load order.
            set_bkg_palette(0u, _zone_${zoneSanitized}_tileset_PALETTE_COUNT, _zone_${zoneSanitized}_tileset_palettes);
            // Phase 12.6 D-08 (debug 12-6-07 CYCLE 3) — windowed submap write replaces full-tilemap write.
            // The previous `_bkg_tiles_load_banked(bank, 0, 0, WIDTH, HEIGHT, tilemap)` wrote the ENTIRE
            // ZONE_WIDTH x ZONE_HEIGHT tilemap (e.g. 60x18 for level-1/level-2) starting at BG (0,0).
            // The GB BG map is 32x32 cells, and `set_bkg_tiles` WRAPS at the 32-cell boundary — so
            // columns 32..59 of the tilemap overwrote columns 0..27 of the BG map. At camera_x=0 the
            // visible window (cols 0..19) showed tilemap[32..51, r] instead of tilemap[0..19, r]:
            // chimera of right-side tiles wrapped to the left, plus untouched leftover cells from the
            // previous level's BG map content (rows the level-N write never reached). Empirically
            // verified via live MCP capture of frame 1208 — bgText row 16 showed level-1's floor
            // pattern (`$!"#$!"#%67676767676`) at the level-2 spawn position.
            //
            // Reference (gbdk/examples/.../platformer_template/src/main.c:43-50): SetupCurrentLevel
            // loads tileset only, then `SetCurrentLevelSubmap(0, 0, DEVICE_SCREEN_WIDTH+1, DEVICE_SCREEN_HEIGHT)`
            // writes exactly DEVICE_SCREEN_WIDTH+1 (21) x DEVICE_SCREEN_HEIGHT (18) = 378 cells using
            // `set_bkg_submap` which TAKES A STRIDE parameter (no wrap, even though source tilemap
            // is wider than 32). gbkt mirrors this by calling the existing _bkg_set_level_submap_banked
            // helper (declared at GBDKPipeline buildSetLevelSubmapHelperIfNeeded; same shape as the
            // reference's SetCurrentLevelSubmap). The helper reads _current_level_map +
            // _current_level_width_in_tiles + _current_area_bank — all 3 are set on lines 2465-2467
            // ABOVE this call, so the data dependency is satisfied at runtime.
            //
            // The bank-allocation literal `<bank>u` is no longer needed at this site because
            // _bkg_set_level_submap_banked sources _current_area_bank from the just-set global.
            _bkg_set_level_submap_banked(0u, 0u, 21u, 18u);
            // Phase 12.6 D-06 — per-level spawn position (closes DEFECT-2).
            // Spawn coords are pixels in the DSL; <<4 shift converts to subpixel form (mirrors
            // reference SetupPlayer() at platformer_template/src/player.c:101-103). The write
            // happens AFTER `_bkg_tiles_load_banked` so the order mirrors the reference's
            // SetupCurrentLevel() → SetupPlayer() sequence (see Pitfall 1 in 12.6-RESEARCH.md).
            // Velocity reset is part of the contract (Pitfall 3): without it, the player would
            // carry level-N momentum into level-N+1, causing same-frame level-end-trigger re-fire.
            $posXSym = ((INT16)_level_spawn_x[${idx}u]) << 4;
            $posYSym = ((INT16)_level_spawn_y[${idx}u]) << 4;
            $vxSym = 0;
            $vySym = 0;
            // Phase 12.9 D4 fix: reset grounded to 0 on every level switch.
            // Without this, grounded carries 1 from the prior level's level-end trigger
            // (which requires grounded == 1). With grounded == 1, gravity is suppressed and
            // the vertical collision snap never fires → player frozen at raw spawn y ("sunk").
            // Resolved from tilemap_collision config "groundedVar" (default "_grounded").
            $groundedSym = 0;
            // Phase 12.6 D-07 camera reset — mirrors reference main.c:63 .
            // platformer_physics_update() only updates _camera_x when player_real_x >= 80;
            // with spawn at x=40 the update never fires, leaving _camera_x at the old level's
            // scroll position and putting the player off-screen on level-2 entry.
            _camera_x = 0;
            _old_camera_x = 0;
            break;
            """
                        .trimIndent()
                }
                .joinToString("\n")
        val zoneCount = gameplayZones.size
        return """
// Phase 12 D-02 / D-08 anchor 5 — setup_current_level() HOME-bank NONBANKED function
// (Plan 12-17 Task 2). Switches on (_current_level % <zoneCount>) and binds the
// per-zone tilemap metadata to the canonical _current_level_* globals. Called by the
// main()-loop level-switch guard (after the NextLevel card scene returns to gameplay).
//
// SHAPE LOCK (Plan 12-09b anchor 5 emission test): the function MUST start with
// `void setup_current_level` and MUST contain `switch (_current_level`.
//
// Per-case body is a Plan 12-17 STUB referencing the canonical `_zone_<id>_tilemap`
// symbol + `_zone_<id>_tilemap_WIDTH` / `_HEIGHT` size constants. Plan 12-18's
// ConvertZoneTilesetsTask + GenerateCTask resolve these symbols at buildRom time.
void setup_current_level(void) NONBANKED {
    _current_level = _next_level;
    DISPLAY_OFF;
    switch (_current_level % ${zoneCount}u) {
${caseBodies}
        default:
            break;
    }
    DISPLAY_ON;
}
"""
            .trimIndent()
    }

    /**
     * Phase 12.6 D-06 — per-level spawn position tables (closes DEFECT-2).
     *
     * Emits two `const UINT8` arrays in HOME bank — `_level_spawn_x[]` and `_level_spawn_y[]` —
     * with one element per gameplay zone (the same zone-filter used by
     * `buildSetupCurrentLevelFunctionIfNeeded`, so the array indices match the switch cases
     * one-to-one). The arrays are consumed by the per-case body inside `setup_current_level()` via
     * `<posXSym> = ((INT16)_level_spawn_x[idx]) << 4;` (subpixel shift applied at codegen time per
     * RESEARCH §Pitfall 2).
     *
     * Returns a raw C source string when `gameUsesTilemapCollision(gameIR) == true` AND there is at
     * least one gameplay zone, else null. This gate keeps pong/breakout/etc. byte-identical to the
     * Phase 12.6-01 baseline (per CONTEXT D-14 7-target sweep).
     *
     * Default-fallback behavior (D-07): when a gameplay zone has not declared `spawn(x, y)`, we
     * substitute `(16, 120)` (matches CONTEXT D-07's locked default) and emit a build-time WARNING
     * to stderr so users know to declare explicitly. Plan 12.6-07 will migrate platformer-template
     * to declare `spawn(40u, 120u)` on all 3 zones, so the warning will not fire on the production
     * path.
     *
     * Visibility is `internal` to mirror the sibling `buildSetupCurrentLevelFunctionIfNeeded`
     * (already internal for test access per Plan 12-09b) and to allow direct invocation from
     * `LevelCardSceneEmissionTest` / similar emission-invariant tests once Plan 12.6-06 lands.
     */
    internal fun buildLevelSpawnTablesIfNeeded(gameIR: GameIR): String? {
        if (!gameUsesTilemapCollision(gameIR)) return null
        // CR-02 fix: use the semantic screenMode field (mirrors
        // buildSetupCurrentLevelFunctionIfNeeded).
        val gameplayZones = gameIR.zones.filter { zone -> !zone.screenMode }
        if (gameplayZones.isEmpty()) return null

        val spawnXValues = gameplayZones.map { zone ->
            if (zone.spawnX == null) {
                System.err.println(
                    "WARNING: zone '${zone.id}' did not declare spawn(x, y); defaulting to (16, 120)"
                )
                16
            } else {
                zone.spawnX!!.toInt()
            }
        }
        val spawnYValues = gameplayZones.map { zone ->
            if (zone.spawnY == null) 120 else zone.spawnY!!.toInt()
        }

        return """
// Phase 12.6 D-06 — per-level spawn position tables (closes DEFECT-2).
// setup_current_level() reads these to write <posXSym>/<posYSym> on every level switch,
// fixing the same-frame level-end-trigger re-fire that occurred when _playerX was preserved
// across level switches. Indices match the gameplay-zone order in setup_current_level()'s
// switch cases (same filter — title/nextLevel/next_level scenes excluded).
const UINT8 _level_spawn_x[] = { ${spawnXValues.joinToString(", ") { "${it}u" }} };
const UINT8 _level_spawn_y[] = { ${spawnYValues.joinToString(", ") { "${it}u" }} };
"""
            .trimIndent()
    }

    /**
     * Phase 12 D-02 / D-08 anchor 5 — main()-loop level-switch guard statements (Plan 12-17 Task
     * 2).
     *
     * Returns the guard statement list when `gameUsesTilemapCollision(gameIR) == true` AND the game
     * declares a scene with id matching one of `{"nextLevel", "next_level", "nextlevel"}` (the
     * conventional NextLevel card scene declared by PlatformerTemplate.kt + future platformer
     * ports). Returns an empty list otherwise — preserving byte-identical codegen for games that do
     * not opt into tilemap collision OR do not declare a NextLevel scene.
     *
     * **Shape** (mirrors reference main.c lines 44-82):
     * ```c
     * // Phase 12 D-02 / D-08 anchor 5 — level-switch guard
     * if (_next_level != _current_level) {
     *     navigate_to_scene(SCENE_NEXTLEVEL);
     *     setup_current_level();
     * }
     * ```
     *
     * **Bootstrap rationale (Plan 12-19 revision):** `_current_level` and `_next_level` are both
     * initialised to 0, so the guard does NOT fire on startup. The title scene runs with its own
     * tileset+tilemap (loaded by `title_enter`) until the player presses Start. The title's
     * `navigate(gameplayScene)` triggers `gameplay_enter`, which calls `setup_current_level()` via
     * a DSL cEmit; that call populates `_current_level_*` AND writes the level's tileset+tilemap to
     * VRAM (Plan 12-19 deviation [Rule 1 - Bug] added the VRAM writes inside setup_current_level's
     * per-case body). This is cleaner than firing the guard on startup because the title tileset
     * stays in VRAM while the title scene is showing.
     *
     * Mid-game level transitions still flow through this guard: when PlatformerVisitor's level-end
     * trigger (kt:802) increments `_next_level`, the next frame's guard sees `_next_level !=
     * _current_level` and runs the NextLevel card + setup_current_level exactly as the reference
     * does.
     *
     * **Ordering rationale** (subtly different from reference):
     * - Reference (main.c:44-82): show NextLevel card → wait for Start → setup_current_level →
     *   continue main loop. The wait is a busy-loop inside main(), so the next iteration of the
     *   while(1) loop runs WITH _current_level already advanced.
     * - gbkt: scene-navigate is non-blocking (the scene runs as part of the main loop's frame
     *   dispatch on subsequent frames). To preserve correctness, the guard:
     *     1. navigates to the NextLevel card scene (Start-wait runs as the scene's frame loop)
     *     2. calls setup_current_level() IMMEDIATELY (not on scene-exit) so subsequent frames run
     *        with `_current_level = _next_level`. The NextLevel card scene's
     *        `navigate(gameplayScene)` on Start press does not re-fire setup_current_level — the
     *        guard only fires when `_next_level != _current_level`, which is no longer true after
     *        setup_current_level synced them.
     *
     * **Contract for Plan 12-09b's anchor 5 emission test:**
     * - main() body MUST contain `if (_next_level != _current_level)` AS A SUBSTRING.
     * - main() body MUST contain `navigate_to_scene(SCENE_NEXTLEVEL)` AS A SUBSTRING.
     * - main() body MUST contain `setup_current_level()` AS A SUBSTRING.
     *
     * **Gate**: returns empty when the game has no scene id matching the NextLevel conventional
     * names. Without the NextLevel scene, `SCENE_NEXTLEVEL` is not in the scene-enum and would
     * trigger an SDCC unresolved-identifier error. The double gate (tilemap-collision + scene id
     * presence) keeps Pong/Breakout/Explorer/etc. byte-identical.
     */
    private fun buildMainLoopLevelSwitchGuardIfNeeded(gameIR: GameIR): List<CStatement> {
        if (!gameUsesTilemapCollision(gameIR)) return emptyList()
        val nextLevelSceneId =
            gameIR.scenes
                .map { it.id }
                .firstOrNull { id ->
                    val lower = id.lowercase()
                    lower.contains("nextlevel") || lower.contains("next_level")
                } ?: return emptyList()
        val sceneEnumConstant = "SCENE_${nextLevelSceneId.uppercase()}"
        return listOf(
            CComment(
                "Phase 12.6 D-04 — level-switch guard (trimmed; setup_current_level moved to levelCardScene Start-press path)"
            ),
            CComment(
                "Phase 12.11 Failure A fix — guard also checks current_scene != SCENE_NEXTLEVELSCENE to prevent"
            ),
            CComment(
                "navigate_to_scene() firing EVERY frame while already on the card scene. Without this,"
            ),
            CComment(
                "nextLevelScene_enter() runs each frame, consuming the VBlank slot mid-loop and preventing"
            ),
            CComment(
                "update_joypad() from seeing the START press (frame-boundary collision — DIAGNOSTIC.md Fix Site 2)."
            ),
            CIf(
                condition =
                    CBinaryExpr(
                        CBinaryExpr(CVar("_next_level"), "!=", CVar("_current_level")),
                        "&&",
                        CBinaryExpr(CVar("current_scene"), "!=", CVar(sceneEnumConstant)),
                    ),
                thenBody =
                    listOf(
                        CExprStatement(CCall("navigate_to_scene", listOf(CVar(sceneEnumConstant))))
                    ),
            ),
        )
    }

    /**
     * Plan 07.4-30 — HOME-bank helper for cross-bank `set_bkg_tiles` calls from BANKED scenes.
     *
     * BANKED functions (in bank1.c, at 0x4000-0x7FFF) cannot safely execute `SWITCH_ROM(N)` +
     * `set_bkg_tiles` inline because after `SWITCH_ROM(N)` the MBC remaps 0x4000-0x7FFF to bank N.
     * Subsequent instruction fetches come from bank N data (tilemap bytes), not bank 1 code —
     * garbage execution, LCDC corruption, `EmulatorFrameHangException`.
     *
     * This helper lives in HOME bank (main.c, 0x0000-0x3FFF, never remapped by MBC). The HOME bank
     * is always mapped at 0x0000-0x3FFF regardless of the MBC bank register. The helper safely
     * executes SWITCH_ROM(N) + set_bkg_tiles + SWITCH_ROM(1) from HOME bank, then returns to the
     * BANKED caller with bank 1 properly restored.
     *
     * NOTE: After this helper returns, the LCD is still OFF (set_bkg_tiles calls display_off()
     * internally, leaving LCDC.7=0). The caller (SportVisitor's race_enter) must emit `DISPLAY_ON`
     * after the helper call to re-enable the LCD (Plan 07.4-30 Task 3 fix).
     */
    private fun buildBkgTilesLoadBankedHelper(): CFunction =
        CFunction(
            name = "_bkg_tiles_load_banked",
            returnType = CVoid,
            params =
                listOf(
                    CParam("bank", CU8),
                    CParam("x", CU8),
                    CParam("y", CU8),
                    CParam("w", CU8),
                    CParam("h", CU8),
                    CParam("tiles", CPointer(CConst(CU8))),
                ),
            body =
                listOf(
                    // Plan 07.4-30 / D-N-SWITCHROM-RESTORE: HOME-bank SWITCH_ROM wrapper.
                    // Executed from HOME bank (0x0000-0x3FFF); MBC remapping of 0x4000-0x7FFF
                    // cannot affect instruction fetches for this function.
                    CRawCode("SWITCH_ROM(bank);"),
                    CExprStatement(
                        CCall(
                            "set_bkg_tiles",
                            listOf(CVar("x"), CVar("y"), CVar("w"), CVar("h"), CVar("tiles")),
                        )
                    ),
                    CRawCode("SWITCH_ROM(1);"),
                ),
            bank = 0,
            sectionComment = "Plan 07.4-30 / D-N-SWITCHROM-RESTORE: HOME-bank SWITCH_ROM wrapper",
        )

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
        val zoneTileExterns = buildZoneTileExterns(gameIR, bankAllocation)

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
        // _pool_<id>_x[], _pool_<id>_y[], _pool_<id>_oam[])
        // Mirrors GBDKSystemVisitor.buildActorPoolStateVars() structure for extern visibility.
        val actorPoolExterns = buildActorPoolExterns(gameIR)

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
            if (gameIR.palettes.isNotEmpty()) listOf(GBDKIncludes.CGB_H) else emptyList()

        // Include <gbdk/metasprites.h> in game.h when metasprites are present — provides the
        // `metasprite_t` typedef. Without it, SDCC fails to parse the
        // `extern const metasprite_t* const sprite_<id>_frames[];` lines emitted via
        // metaspriteAutoExterns below (`game.h:N: error 1: Syntax error, declaration ignored
        // at 'metasprite_t'`). Mirrors the bank1.c per-bank pattern landed in Plan 06 (CR-02)
        // and the per-metasprite externs landed in Plan 07 (WR-02). Wave-4 ripple-closure
        // for DEF-10.1-09-A — surfaced by Plan 09's ROM-build smoke test, defect introduced
        // by Plan 07's extern emission without paired include.
        val metaspriteHeaderInclude =
            if (gameIR.metasprites.isNotEmpty()) listOf(GBDKIncludes.METASPRITES_H) else emptyList()

        // Forward declarations for external functions called via CallOp in zone object scripts
        // and scene scripts. SDCC does not support implicit declarations with arguments (error
        // 101).
        val callOpForwardDecls = buildCallOpForwardDecls(gameIR)

        // Combat state #define constants must also be in game.h so that banked scene code
        // (bank1.c includes game.h, not main.c) can reference _COMBAT_STATE_* constants.
        val combatStateDefines = buildCombatStateDefines(gameIR)

        // Auto-extern any HOME-bank global that the hand-built extern lists above did not
        // already cover. Genre-system visitors (sport_racing, etc.) emit globals through the
        // [GenreSystemVisitor] -> [buildSystemGlobalVars] dispatch path; those globals land in
        // [homeFile.variables] but are not represented in the typed [allExterns] /
        // [actorPoolExterns] / [rpgCombatHelperExterns] lists. Without extern visibility,
        // banked scene code that references e.g. `_racing_lap_count_track1` fails to link.
        //
        // The extraction mirrors the function-prototype auto-extraction pattern below: walk the
        // home file's typed declaration list, drop names already covered, and emit one extern
        // per missing entry. Vars beginning with anything other than `_` (the gbkt prefix
        // convention) are left alone — those are pipeline-internal helpers that do not need
        // header visibility.
        val coveredExternNames =
            (allExterns + actorPoolExterns + rpgCombatHelperExterns).map { it.name }.toSet()
        val homeGlobalAutoExterns =
            homeFile.variables
                .filter { v ->
                    !v.isExtern && v.name.startsWith("_") && v.name !in coveredExternNames
                }
                .map { v -> CVarDecl(name = v.name, type = v.type, isExtern = true) }

        // Per-metasprite forward declarations for game.h (WR-02, D-14).
        //
        // Plan 13.3-05 D-01 Path A branch: emit the correct extern for each metasprite:
        //   - Asset-driven (spritePath != null): `extern const metasprite_t* const
        // <id>_metasprites[];`
        //     — references the png2asset-native array (defined in the #included .c sidecar,
        //       wired in Plan 13.3-06). Banked scene callers resolve the symbol cross-bank.
        //   - Escape-hatch (spritePath == null): `extern const metasprite_t* const
        // sprite_<id>_frames[];`
        //     — the legacy gbkt-owned pointer table (defined in main.c by
        // generateMetaspriteDescriptor).
        //
        // Both paths produce exactly one extern line per metasprite; the names match what
        // generateMetaspriteFrameSwitch() (MetaspriteVisitor.kt) uses as the `frames` binding.
        // Each metasprite produces exactly one extern line; emission is skipped entirely
        // when `gameIR.metasprites` is empty (regression guard: WR02 Test 2).
        val metaspriteAutoExterns =
            gameIR.metasprites.map { ms ->
                if (ms.spritePath != null) {
                    "extern const metasprite_t* const ${ms.id}_metasprites[];"
                } else {
                    "extern const metasprite_t* const sprite_${ms.id}_frames[];"
                }
            }

        // NONBANKED helper prototypes (is_tile_solid, _bkg_set_level_submap_banked,
        // setup_current_level). These helpers are emitted as rawSections in main.c and are
        // therefore not covered by auto-prototype extraction from homeFile.functions.
        // Extracted to reduce cognitive complexity of buildHeaderFile (SonarCloud S3776 E-15).
        val nonBankedPrototypesRaw = buildNonBankedPrototypesRaw(gameIR)

        // isHeader=true wraps content in #ifndef GAME_H / #define GAME_H / #endif include guard.
        // Scene defines are inside the guard so #pragma bank is not emitted (bank=0 for header).
        return CFile(
            name = "game.h",
            bank = 0,
            isHeader = true,
            includes = GBDKIncludes.headerFileBase() + cgbHeaderInclude + metaspriteHeaderInclude,
            defines = sceneEnum + combatStateDefines,
            variables =
                allExterns + actorPoolExterns + rpgCombatHelperExterns + homeGlobalAutoExterns,
            rawSections =
                listOfNotNull(paletteExternRaw, callOpForwardDecls) +
                    nonBankedPrototypesRaw +
                    metaspriteAutoExterns,
            functions = sceneFunctionPrototypes + homeFunctionPrototypes + collectionPrototypes,
        )
    }

    // =========================================================================
    // buildHeaderFile sub-builders (E-15 extract-method decomposition)
    // =========================================================================

    /**
     * Extern declarations for banked zone tile arrays.
     *
     * When zones are allocated to non-zero banks, their tile arrays need extern declarations in
     * `game.h` so that `zone_load_X()` functions in `main.c` can reference them.
     */
    private fun buildZoneTileExterns(
        gameIR: GameIR,
        bankAllocation: Map<String, Int>,
    ): List<CVarDecl> =
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

    /**
     * Extern declarations for actor pool state variables.
     *
     * Emits `_pool_<id>_active[]`, `_pool_<id>_x[]`, `_pool_<id>_y[]`, `_pool_<id>_oam[]`, and one
     * entry per [ActorPoolIR.instanceProperties]. Mirrors
     * [GBDKSystemVisitor.buildActorPoolStateVars] structure for extern visibility.
     */
    private fun buildActorPoolExterns(gameIR: GameIR): List<CVarDecl> =
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
                add(CVarDecl(name = "_pool_${id}_x", type = CArray(CU8, maxSize), isExtern = true))
                add(CVarDecl(name = "_pool_${id}_y", type = CArray(CU8, maxSize), isExtern = true))
                add(
                    CVarDecl(name = "_pool_${id}_oam", type = CArray(CU8, maxSize), isExtern = true)
                )
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

    /**
     * NONBANKED function prototypes for `game.h`.
     *
     * Returns forward declarations for `is_tile_solid`, `_bkg_set_level_submap_banked`, and
     * `setup_current_level`. These helpers are emitted via `rawSections` in `main.c` using the
     * `NONBANKED` keyword (not modelled in the typed C AST), so auto-prototype extraction from
     * `homeFile.functions` does not cover them. Manual prototypes here give banked scene code
     * (`bank1.c` includes `game.h`) cross-bank visibility.
     *
     * Returns only the applicable prototypes (null-filtered). Order is preserved: `is_tile_solid` →
     * `_bkg_set_level_submap_banked` → `setup_current_level`.
     */
    private fun buildNonBankedPrototypesRaw(gameIR: GameIR): List<String> {
        // Phase 12 D-12a / D-13 / D-02-D-08-anchor-5: gated on tilemap-collision presence.
        val usesTilemapCollision = gameUsesTilemapCollision(gameIR)
        val isTileSolid =
            if (usesTilemapCollision)
                "UINT8 is_tile_solid(UINT16 world_x, UINT16 world_y) NONBANKED;"
            else null
        val bkgSetLevelSubmap =
            if (usesTilemapCollision)
                "void _bkg_set_level_submap_banked(UINT8 x, UINT8 y, UINT8 w, UINT8 h) NONBANKED;"
            else null
        val setupCurrentLevel =
            if (
                usesTilemapCollision &&
                    gameIR.zones.any { z ->
                        val l = z.id.lowercase()
                        !l.contains("title") &&
                            !l.contains("nextlevel") &&
                            !l.contains("next_level")
                    }
            ) {
                "void setup_current_level(void) NONBANKED;"
            } else {
                null
            }
        return listOfNotNull(isTileSolid, bkgSetLevelSubmap, setupCurrentLevel)
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
        val isMbcGame = gameIR.config.cartridge.maxRomBanks > 2
        return gameIR.scenes
            .filter { scene ->
                val slot = scene.bankSlot
                slot != null && slot.bank > 0
            }
            .flatMap { scene -> buildTrampolinesForScene(scene, isMbcGame) }
    }

    private fun buildTrampolinesForScene(
        scene: SceneIR,
        isMbcGame: Boolean = false,
    ): List<CFunction> {
        val slot =
            checkNotNull(scene.bankSlot) {
                "buildTrampolinesForScene called on scene '${scene.id}' without a bankSlot; " +
                    "callers must pre-filter via buildTrampolineStubs."
            }
        val bank = slot.bank
        return buildList {
            // Use sceneHasEnterContent (not enterOps.isNotEmpty()) so zone-only scenes (no user
            // enter block but bound zone via zoneRefs) still get an enter trampoline. A scene whose
            // enter {} block was removed but whose zone(X) binding remains still generates a
            // {scene}_enter() function in bank1.c via SceneVisitor — the trampoline must exist to
            // call it from HOME bank (main.c). Without this, the zone tilemap never loads.
            if (sceneHasEnterContent(scene)) {
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
            // Emit exit trampoline when: user-declared exit {} block exists (exitOps.isNotEmpty()),
            // OR when auto-exit synthesis is active (shouldAutoEmitExit — Req #15 / D-07).
            // Without this, the auto-synthesized `${scene.id}_exit` in bank1.c has no HOME-bank
            // caller and SDCC reports `undefined identifier '${scene.id}_exit_trampoline'`.
            if (scene.exitOps.isNotEmpty() || shouldAutoEmitExit(scene, isMbcGame)) {
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
        val loopVar = CVarDecl(name = "_d", type = CU8, initializer = CLiteral(0))
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
    private fun buildFlagVarDecls(gameIR: GameIR): List<CVarDecl> =
        buildGlobalFlagVarDecls(gameIR) + buildZoneObjectInlineFlagVarDecls(gameIR)

    /** Flag vars from [GlobalFlagsIR] pages (via `flags { page(...) { flag("name") } }`). */
    private fun buildGlobalFlagVarDecls(gameIR: GameIR): List<CVarDecl> {
        val decls = mutableListOf<CVarDecl>()
        for (flagsIR in gameIR.flags) {
            for (page in flagsIR.pages) {
                for (flagName in page.flags) {
                    val sanitized = flagName.replace('-', '_').replace(' ', '_')
                    decls += CVarDecl("_flag_$sanitized", CU8, CLiteral(0))
                }
            }
        }
        return decls
    }

    /**
     * Inline flag vars on zone objects — `usedFlagId` and `visibleFlagId` ([NpcObjectIR]).
     *
     * Ad-hoc flags not registered in any [GlobalFlagsIR] page but referenced as `_flag_{id}` by
     * [GBDKSystemVisitor.buildNpcHandlerFunction] / `buildChestHandlerFunction`.
     */
    private fun buildZoneObjectInlineFlagVarDecls(gameIR: GameIR): List<CVarDecl> {
        val decls = mutableListOf<CVarDecl>()
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
        val allCallOps = collectAllCallOpsFromGame(gameIR)
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
            buildSingleCallOpStub(name, callOp, seen.keys.first() == name)
        }
    }

    /**
     * Collect all [CallOp] instances from every zone-object script and every scene lifecycle script
     * in [gameIR].
     */
    private fun collectAllCallOpsFromGame(gameIR: GameIR): List<CallOp> {
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
        return allCallOps
    }

    /** Build a single external-function stub [CFunction] for the given [CallOp]. */
    private fun buildSingleCallOpStub(name: String, callOp: CallOp, isFirst: Boolean): CFunction {
        val params =
            if (callOp.args.isEmpty()) {
                emptyList()
            } else {
                callOp.args.mapIndexed { i, arg ->
                    val type = if (arg is StringLiteral) CPointer(CU8) else CU8
                    CParam("p$i", type)
                }
            }
        return CFunction(
            name = name,
            returnType = CVoid,
            params = params,
            body = listOf(CComment("Stub: external function — provide implementation")),
            sectionComment = if (isFirst) "External function stubs" else null,
        )
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
                is io.github.gbkt.core.ir.CameraSystem -> vars += buildCameraSystemGlobalVars()
                is io.github.gbkt.core.ir.ExplorationSystem ->
                    vars += buildExplorationSystemGlobalVars(gameIR, system)
                is PathfindingSystem -> vars += GBDKSystemVisitor.buildPathfindingGlobals(system)
                is io.github.gbkt.core.ir.DialogSystem ->
                    vars += buildDialogSystemGlobalVars(system)
                is GenericSystem ->
                    vars += buildGenericSystemGlobalVars(gameIR, system, sanitizedId, genreVisitors)
                is io.github.gbkt.core.ir.CombatEngineSystem ->
                    vars += buildCombatEngineSystemGlobalVars(system, sanitizedId, gameIR)
                else -> Unit
            }
        }
        return vars
    }

    private fun buildCameraSystemGlobalVars(): List<CVarDecl> =
        listOf(
            CVarDecl(name = "_camera_x", type = CU8, initializer = CLiteral(0)),
            CVarDecl(name = "_camera_y", type = CU8, initializer = CLiteral(0)),
            CVarDecl(name = "_camera_target", type = CU8, initializer = CRawExpr("0xFF")),
            CVarDecl(name = "_camera_shake_intensity", type = CU8, initializer = CLiteral(0)),
            CVarDecl(name = "_camera_shake_timer", type = CU8, initializer = CLiteral(0)),
        )

    private fun buildExplorationSystemGlobalVars(
        gameIR: GameIR,
        system: io.github.gbkt.core.ir.ExplorationSystem,
    ): List<CVarDecl> {
        val vars = mutableListOf<CVarDecl>()
        vars += CVarDecl(name = "_player_x", type = CU8, initializer = CLiteral(0))
        vars += CVarDecl(name = "_player_y", type = CU8, initializer = CLiteral(0))
        vars += CVarDecl(name = "_current_floor", type = CU8, initializer = CLiteral(0))
        // Expanded exploration state globals (Plan 06.3-02)
        vars += CVarDecl(name = "_exploration_step_count", type = CU8, initializer = CLiteral(0))
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
        vars += CVarDecl(name = "_encounter_triggered", type = CU8, initializer = CLiteral(0))
        vars += CVarDecl(name = "_encounter_id", type = CU8, initializer = CLiteral(0))
        vars += CVarDecl(name = "_current_zone_safe", type = CU8, initializer = CLiteral(0))
        // _current_tileset_id already declared in allVariables — skip to avoid duplicate
        vars += CVarDecl(name = "_current_zone_id", type = CU8, initializer = CRawExpr("0xFF"))
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
                CVarDecl(name = "_key_${key.id}", type = CU8, initializer = CLiteral(key.initial))
        }
        // Entity collision globals (G3 — Plan 06.3-03)
        vars += buildEntityCollisionGlobalVars(gameIR)
        return vars
    }

    private fun buildEntityCollisionGlobalVars(gameIR: GameIR): List<CVarDecl> {
        val collisionActors =
            gameIR.actors.filter {
                val ec = it.entityCollision
                ec != null && ec.mode != EntityCollisionMode.PASSTHROUGH
            }
        if (collisionActors.isEmpty()) return emptyList()
        val maxEntities = collisionActors.size
        val mapSize = 32 * 32 / 8 + 1 // 129 bytes for 32x32 grid
        val tilesWideInit =
            collisionActors.joinToString(", ") { (it.entityCollision?.tilesWide ?: 1).toString() }
        val tilesHighInit =
            collisionActors.joinToString(", ") { (it.entityCollision?.tilesHigh ?: 1).toString() }
        val pushDirInit =
            collisionActors.joinToString(", ") {
                (it.entityCollision?.pushDirection?.ordinal ?: 0).toString()
            }
        val pushAllowedInit = buildEntityPushAllowedInit(collisionActors)
        return listOf(
            // _entity_grid[MAP_SIZE] — bit-packed entity presence grid
            CVarDecl(name = "_entity_grid", type = CArray(CU8, mapSize), initializer = null),
            // _entity_collision_mode[MAX_ENTITIES] — per-entity mode (0xFF=none)
            CVarDecl(
                name = "_entity_collision_mode",
                type = CArray(CU8, maxEntities),
                initializer = CRawExpr("{${(0 until maxEntities).joinToString(", ") { "0xFF" }}}"),
            ),
            // _entity_collision_shape[MAX_ENTITIES] — 0=TILE, 1=HITBOX
            CVarDecl(
                name = "_entity_collision_shape",
                type = CArray(CU8, maxEntities),
                initializer = null,
            ),
            // _entity_tile_x/y[MAX_ENTITIES] — entity tile positions
            CVarDecl(
                name = "_entity_tile_x",
                type = CArray(CU8, maxEntities),
                initializer = CRawExpr("{${(0 until maxEntities).joinToString(", ") { "0xFF" }}}"),
            ),
            CVarDecl(
                name = "_entity_tile_y",
                type = CArray(CU8, maxEntities),
                initializer = CRawExpr("{${(0 until maxEntities).joinToString(", ") { "0xFF" }}}"),
            ),
            // _entity_count — number of registered entities
            CVarDecl(name = "_entity_count", type = CU8, initializer = CLiteral(0)),
            // Gap 1 callback globals — set before callback execution
            CVarDecl(name = "_blocking_entity_id", type = CU8, initializer = CRawExpr("0xFF")),
            CVarDecl(name = "_pushed_entity_id", type = CU8, initializer = CRawExpr("0xFF")),
            CVarDecl(name = "_push_direction", type = CU8, initializer = CRawExpr("0xFF")),
            // Multi-tile entity dimensions (Gap A)
            CVarDecl(
                name = "_entity_tiles_wide",
                type = CArray(CU8, maxEntities),
                initializer = CRawExpr("{$tilesWideInit}"),
            ),
            CVarDecl(
                name = "_entity_tiles_high",
                type = CArray(CU8, maxEntities),
                initializer = CRawExpr("{$tilesHighInit}"),
            ),
            // Push direction constraints (Gap B)
            CVarDecl(
                name = "_entity_push_dir",
                type = CArray(CU8, maxEntities),
                initializer = CRawExpr("{$pushDirInit}"),
            ),
            CVarDecl(
                name = "_entity_push_allowed",
                type = CArray(CU8, maxEntities),
                initializer = CRawExpr("{$pushAllowedInit}"),
            ),
        )
    }

    private fun buildEntityPushAllowedInit(
        collisionActors: List<io.github.gbkt.core.ir.ActorIR>
    ): String =
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

    private fun buildDialogSystemGlobalVars(
        system: io.github.gbkt.core.ir.DialogSystem
    ): List<CVarDecl> =
        listOf(
            // Extended config: default speed and border style
            CVarDecl(
                name = "_dialog_default_speed",
                type = CU8,
                initializer = CLiteral(system.textSpeed),
            ),
            CVarDecl(
                name = "_dialog_default_border",
                type = CU8,
                initializer = CLiteral(system.defaultBorder.ordinal),
            ),
        )

    private fun buildGenericSystemGlobalVars(
        gameIR: GameIR,
        system: GenericSystem,
        sanitizedId: String,
        genreVisitors: List<GenreSystemVisitor>,
    ): List<CVarDecl> {
        val systemType = system.config["type"] as? String
        val genreVisitor =
            if (systemType != null) genreVisitors.find { it.canHandle(systemType) } else null
        if (genreVisitor != null && systemType != null) {
            return genreVisitor
                .visit(systemType, system.config, gameIR)
                .varDecls
                .filterIsInstance<CVarDecl>()
        }
        val vars = mutableListOf<CVarDecl>()
        if (systemType == "simple_battle") {
            vars +=
                CVarDecl(name = "_combat_state_$sanitizedId", type = CU8, initializer = CLiteral(0))
        }
        if (systemType == "arpg_combat")
            vars += RpgVisitor(gameIR).generateActionRpgVarDecls(system)
        if (systemType == "roguelike_system")
            vars += RpgVisitor(gameIR).generateRoguelikeVarDecls(system)
        if (systemType == "rpg_currency")
            vars += RpgVisitor(gameIR).generateCurrencyVarDecls(system)
        if (systemType == "pickup_system")
            vars += GBDKSystemVisitor(gameIR).buildPickupVarDecls(system, sanitizedId)
        if (systemType == "audio_mixer") vars += buildAudioMixerSystemGlobalVars(system)
        return vars
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildAudioMixerSystemGlobalVars(system: GenericSystem): List<CVarDecl> {
        val groups =
            (system.config["groups"] as? List<ChannelGroupDef>)
                ?: listOf(
                    ChannelGroupDef("music", setOf(1, 2), 7, 0),
                    ChannelGroupDef("sfx", setOf(3, 4), 7, 1),
                    ChannelGroupDef("ui", setOf(3), 7, 2),
                )
        val masterVol = system.config["master_volume"] as? Int ?: 7
        val initVols = groups.joinToString(", ") { it.defaultVolume.toString() }
        val initMuted = groups.joinToString(", ") { "0" }
        val channelMaskVars = groups.map { group ->
            var mask = 0
            for (ch in group.channels) {
                val bit = ch - 1 // CH1=0, CH2=1, CH3=2, CH4=3
                mask = mask or (1 shl bit) // R-enable
                mask = mask or (1 shl (bit + 4)) // L-enable
            }
            CVarDecl(
                name = "_mixer_channel_mask_${group.name}",
                type = CU8,
                initializer = CRawExpr("0x${mask.toString(16).uppercase()}"),
                isConst = true,
            )
        }
        return listOf(
            // _mixer_group_vol[N] — initial volumes per group
            CVarDecl(
                name = "_mixer_group_vol",
                type = CArray(CU8, groups.size),
                initializer = CRawExpr("{$initVols}"),
            ),
            // _mixer_master_vol — initial master volume
            CVarDecl(name = "_mixer_master_vol", type = CU8, initializer = CLiteral(masterVol)),
            // _mixer_group_muted[N] — mute state per group (0 = unmuted)
            CVarDecl(
                name = "_mixer_group_muted",
                type = CArray(CU8, groups.size),
                initializer = CRawExpr("{$initMuted}"),
            ),
        ) +
            // _mixer_channel_mask_<name> — NR51 bit pattern per group
            channelMaskVars +
            listOf(
                // _mixer_priority[4] — per-channel priority (4 GB channels), init 0
                CVarDecl(
                    name = "_mixer_priority",
                    type = CArray(CU8, 4),
                    initializer = CRawExpr("{0, 0, 0, 0}"),
                ),
                // _mixer_preduck_vol — saved music volume before auto-ducking (Gap 6)
                CVarDecl(name = "_mixer_preduck_vol", type = CU8, initializer = CLiteral(7)),
            )
    }

    private fun buildCombatEngineSystemGlobalVars(
        system: io.github.gbkt.core.ir.CombatEngineSystem,
        sanitizedId: String,
        gameIR: GameIR,
    ): List<CVarDecl> {
        val combatVisitor = io.github.gbkt.backend.gbdk.codegen.visitor.CombatVisitor(gameIR)
        return listOf(
            // _combat_state_<id>: INIT state (0) at startup
            CVarDecl(name = "_combat_state_$sanitizedId", type = CU8, initializer = CLiteral(0)),
            // _pending_state_<id>: 0xFF sentinel = no pending transition
            CVarDecl(
                name = "_pending_state_$sanitizedId",
                type = CU8,
                initializer = CLiteral(0xFF),
            ),
        ) +
            // ATB-specific globals: gauge[], active[], acted[], agl[], menu_open,
            // and optionally charge[] (CHARGE model) and _turn_order[] (when strategy set)
            combatVisitor.generateAtbGlobals(system) +
            // Wave survival globals: _wave_<id>_current (UINT8), _wave_<id>_timer (UINT16)
            combatVisitor.generateWaveGlobals(system) +
            // Hook enabled flag: _combat_<id>_hooks_enabled (only when hooks registered)
            combatVisitor.generateHookGlobals(system)
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
        val isMbcGame = gameIR.config.cartridge.maxRomBanks > 2
        // Build switch cases for exit calls per scene.
        // Include scenes with user-declared exit {} blocks (exitOps.isNotEmpty()) OR scenes with
        // auto-synthesized exits (shouldAutoEmitExit — Req #15 / D-07 paired predicate). Without
        // the auto-exit case, navigate_to_scene would never invoke the auto-synthesized exit stub
        // before switching scenes, breaking the exit-trampoline contract.
        val exitCases =
            gameIR.scenes
                .filter { it.exitOps.isNotEmpty() || shouldAutoEmitExit(it, isMbcGame) }
                .map { scene ->
                    val exitFnName = exitFunctionName(scene)
                    CSwitchCase(
                        value = CVar("SCENE_${scene.id.uppercase()}"),
                        body = listOf(CExprStatement(CCall(exitFnName, emptyList())), CBreak),
                    )
                }

        // Build switch for exit, assign current_scene, build switch for enter.
        // Use sceneHasEnterContent (not enterOps.isNotEmpty()) so zone-only scenes still dispatch
        // their enter function from navigate_to_scene() — same rationale as
        // buildTrampolinesForScene.
        val enterCases =
            gameIR.scenes
                .filter { sceneHasEnterContent(it) }
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

        val startEnterCall = buildMainStartEnterCall(gameIR)

        // Pool template actor IDs — excluded from static OAM init (dynamic allocation).
        // Sprite tile data is STILL loaded for template actors (VRAM tiles needed by pool
        // instances).
        val poolTemplateActorIdsForMain =
            gameIR.actorPools
                .map { pool ->
                    pool.actorTemplateId.replace('-', '_').replace('.', '_').replace(' ', '_')
                }
                .toSet()

        val allSpriteDataLoads = buildAllSpriteDataLoadStatements(gameIR)
        val spriteOAMInits =
            buildOAMInitStatements(gameIR, excludeIds = poolTemplateActorIdsForMain)
        val levelSwitchGuardStatements = buildMainLoopLevelSwitchGuardIfNeeded(gameIR)
        val gameLoopBody = buildMainGameLoopBody(gameIR, frameCases, levelSwitchGuardStatements)

        // Actor pool init calls — one per pool, placed after OAM init but before start scene enter
        val poolInitCalls =
            gameIR.actorPools.map { pool ->
                val poolId = pool.id.replace('-', '_').replace(' ', '_')
                CExprStatement(CCall("pool_${poolId}_init", emptyList()))
            }

        // Plan 10.1-20 / Plan 10.1-22: hoist start-scene SetPalette + bgFillCheckerboard ops
        // into main() BEFORE DISPLAY_ON. Palette writes are idempotent (kept in {start}_enter
        // for multi-scene navigate-back correctness and SpritePaletteSlotEmissionTest contract).
        val startScene = gameIR.startScene?.let { id -> gameIR.scenes.firstOrNull { it.id == id } }
        val hoistedStartPaletteStatements: List<CStatement> =
            startScene?.enterOps?.filterIsInstance<io.github.gbkt.core.ir.SetPalette>()?.map {
                it.accept(ScriptOpVisitor)
            } ?: emptyList()
        val hoistedBgFillCheckerboardStatements: List<CStatement> =
            startScene
                ?.enterOps
                ?.filterIsInstance<RawOp>()
                ?.filter { it.code.contains("fill_bkg_rect") && it.code.contains("set_bkg_data") }
                ?.map { it.accept(ScriptOpVisitor) } ?: emptyList()
        val hoistedDefaultBgPaletteStatements = buildMainHoistedDefaultBgPaletteStatements(gameIR)

        val mainBody = buildList {
            // Plan 10.1-20 GAP-1: LCD off before all palette/VRAM writes.
            add(CRawCode("DISPLAY_OFF;"))
            // GBC compatibility init: FIRST call after DISPLAY_OFF — before any palette/sprite ops.
            if (gameIR.config.gbcTarget != GbcTarget.DMG) {
                add(CRawCode("cgb_compatibility();"))
            }
            // Plan 10.1-20 GAP-2: hoisted sprite-palette writes (AFTER cgb_compatibility,
            // BEFORE DISPLAY_ON).
            addAll(hoistedStartPaletteStatements)
            // Plan 10.1-22 4TH LAYER: explicit BG palette slot 0 write via BCPS/BCPD
            // (AFTER cgb_compatibility, BEFORE DISPLAY_ON; no-op for DMG).
            addAll(hoistedDefaultBgPaletteStatements)
            // Sound hardware init: NR52 enable, NR50 volume max, NR51 all channels on.
            add(CExprStatement(CBinaryExpr(CVar("NR52_REG"), "=", CLiteral(0x80))))
            add(CExprStatement(CBinaryExpr(CVar("NR50_REG"), "=", CLiteral(0x77))))
            add(CExprStatement(CBinaryExpr(CVar("NR51_REG"), "=", CLiteral(0xFF))))
            // Plan 10.2-08 5TH LAYER: bgFillCheckerboard BEFORE sprite-data loads (wins tile 0).
            addAll(hoistedBgFillCheckerboardStatements)
            // Plan 10.1-20 GAP-3: VRAM writes before LCDC sequence + DISPLAY_ON.
            addAll(allSpriteDataLoads)
            // Phase 12.9 D2b: OBJ palette upload after VRAM tile data (GBC-gated).
            addAll(buildMetaspriteSpritePaletteStatements(gameIR))
            addAll(spriteOAMInits)
            addAll(poolInitCalls)
            // LCDC layer enables must precede DISPLAY_ON (reference metasprites.c:186-194).
            add(CRawCode("SHOW_BKG;"))
            add(CRawCode("SHOW_SPRITES;"))
            // D-V1 fix (SEED-004): sprite-mode macro gated on metasprite presence.
            // Locked by Seed004ElephantTileRenderingFixTest / SpriteMode8x16HardwareModeTest.
            addAll(buildMainSpriteModeMacros(gameIR))
            // Plan 10.1-20: DISPLAY_ON last — all palettes/VRAM/LCDC ready before LCD on.
            add(CRawCode("DISPLAY_ON;"))
            addAll(startEnterCall)
            add(CWhile(CVar("1"), gameLoopBody))
        }

        return CFunction(
            name = "main",
            returnType = CVoid,
            body = mainBody,
            sectionComment = "Entry point",
        )
    }

    /**
     * Builds the start-scene enter call for main(). Returns a single [CExprStatement] calling
     * `{startScene}_enter` when the start scene exists and has enter content; otherwise returns an
     * empty list. Extracted from [buildMainFunction] to reduce cognitive complexity (E-20).
     *
     * Uses [sceneHasEnterContent] so zone-only start scenes (no user enter block but bound zone)
     * still get their initial enter call — same rationale as [buildTrampolinesForScene].
     */
    private fun buildMainStartEnterCall(gameIR: GameIR): List<CStatement> {
        val startSceneId = gameIR.startScene ?: return emptyList()
        if (gameIR.scenes.none { it.id == startSceneId && sceneHasEnterContent(it) }) {
            return emptyList()
        }
        val startScene = gameIR.scenes.first { it.id == startSceneId }
        return listOf(CExprStatement(CCall(enterFunctionName(startScene), emptyList())))
    }

    /**
     * Builds the per-frame game loop body for main(). Extracted from [buildMainFunction] to reduce
     * cognitive complexity (E-20). Emission order is preserved exactly: joypad → scene frame →
     * puzzle → collision → level-switch guard → sprites → sound → hUGE → vblank.
     */
    private fun buildMainGameLoopBody(
        gameIR: GameIR,
        frameCases: List<CSwitchCase>,
        levelSwitchGuardStatements: List<CStatement>,
    ): List<CStatement> = buildList {
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
        // Phase 12 D-02/D-08 anchor 5: level-switch guard AFTER per-frame system updates,
        // BEFORE sprite sync (NextLevel scene's first frame runs with _current_level synced).
        addAll(levelSwitchGuardStatements)
        add(CExprStatement(CCall("update_sprites", emptyList())))
        add(CExprStatement(CCall("sound_driver_update", emptyList())))
        // hUGETracker audio driver tick — only emitted when music ops are used (A2)
        if (SoundVisitor(gameIR).hasMusicOps()) {
            add(CExprStatement(CCall("hUGE_dosound", emptyList())))
        }
        add(CExprStatement(CCall("wait_vbl_done", emptyList())))
    }

    /**
     * Emits an explicit `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)` statement for non-DMG
     * targets (Plan 10.1-22 / 4TH LAYER). DMG targets return an empty list (no-op). Extracted from
     * [buildMainFunction] to reduce cognitive complexity (E-20).
     */
    private fun buildMainHoistedDefaultBgPaletteStatements(gameIR: GameIR): List<CStatement> =
        if (gameIR.config.gbcTarget != GbcTarget.DMG) {
            listOf(CRawCode("set_bkg_palette(0u, 1u, _gbkt_default_bg_pal);"))
        } else {
            emptyList()
        }

    /**
     * Emits SPRITES_8x16 or SPRITES_8x8 hardware mode macro when the game uses metasprites (D-V1
     * fix, SEED-004, Plan 10.1-11). Returns an empty list when no metasprites exist so
     * Pong/Breakout/simple-physics stay on the default mode (structurally impossible to regress).
     * Extracted from [buildMainFunction] to reduce cognitive complexity (E-20).
     *
     * Locked by Seed004ElephantTileRenderingDiagnosticTest, SpriteMode8x16HardwareModeTest,
     * Seed004ElephantTileRenderingFixTest.
     */
    private fun buildMainSpriteModeMacros(gameIR: GameIR): List<CStatement> {
        if (gameIR.metasprites.isEmpty()) return emptyList()
        val anySpr8x16 = gameIR.metasprites.any { it.spriteMode == SpriteMode.SPR8x16 }
        return if (anySpr8x16) listOf(CRawCode("SPRITES_8x16;"))
        else listOf(CRawCode("SPRITES_8x8;"))
    }

    /**
     * Unified sprite-VRAM tile data loader — emits `set_sprite_data()` for every actor sprite and
     * every metasprite, with a SINGLE [VramAllocator] handing out monotonically-increasing start
     * indices across BOTH iterations.
     *
     * ## Why unified (CR-01 / SEED-008 / D-08 Route B)
     *
     * Previously, this lived as two separate methods (`buildSpriteDataLoadStatements` for actors,
     * `buildMetaspriteTileDataLoadStatements` for metasprites), each with its own local `var
     * nextTile = 0`. Concatenated into `main()` by [buildMainFunction], the result was two
     * `set_sprite_data(0u, …)` calls when a game had both — the metasprite silently overwrote the
     * actor's tiles in VRAM. Unifying via a single [VramAllocator] instance makes the collision
     * structurally impossible: every reserve is monotonic by construction.
     *
     * ## Emission order (Pitfall 8)
     *
     * Actors iterate FIRST then metasprites. This preserves the emission shape of
     * Pong/Breakout/SimplePhysics (actor-only games) — every existing actor sprite call site stays
     * exactly where it was; the unified loader is strictly additive for those games.
     *
     * ## Array name conventions
     *
     * - **Actor sprite:** derived from the asset path — `"sprites/paddle.png"` →
     *   `"sprites_paddle_tiles"` (slashes/dots/dashes → underscores).
     * - **Metasprite (PHASE-13 fallback):** `"${ms.id}_tiles"` — e.g. `"elephant"` →
     *   `"elephant_tiles"`. Plan 18 (D-13) will wire the proper asset pipeline path once
     *   [io.github.gbkt.core.dsl.MetaspriteBuilder] implements `sprite(asset("..."))`.
     *
     * @return A flat list of `set_sprite_data()` `CStatement`s — actor loads first, metasprite
     *   loads second. Empty list if neither actors with sprites nor metasprites exist.
     */
    private fun buildAllSpriteDataLoadStatements(gameIR: GameIR): List<CStatement> {
        val allocator = VramAllocator()
        val statements = mutableListOf<CStatement>()

        // Actors FIRST — preserves Pong/Breakout/SimplePhysics emission order (Pitfall 8).
        // Template actors ARE included — their tile data must be in VRAM for pool instances.
        for (actor in gameIR.actors) {
            val sprite = actor.sprite ?: continue
            val arrayName =
                sprite.assetRef.path.substringBeforeLast('.').replace('/', '_').replace('-', '_') +
                    "_tiles"
            val tilesWide = (sprite.size.width + 7) / 8
            val tilesHigh = (sprite.size.height + 7) / 8
            val tileCount = tilesWide * tilesHigh
            val start = allocator.reserve(tileCount)
            statements.addAll(ActorVisitor.generateSpriteDataLoad(actor, arrayName, start))
        }

        // Metasprites SECOND — continue from where actors left off (CR-01 fix).
        //
        // Two metasprite paths:
        //
        // Path B (escape-hatch D-04): spritePath == null, frames carry DSL tile entries.
        //   tileCountForMetasprite() returns the integer count → integer start via
        // allocator.reserve().
        //   Emission: set_sprite_data(startInt, countInt, <id>_tiles) — byte-identical to
        // pre-13.3-14.
        //   Debug E-04: count = maxTileId + spriteModeStride (stride=2 for SPR8x16, 1 for
        // SPR8x8/null).
        //
        // Path A (asset-driven, 13.3-14 gap closure): spritePath != null, frames empty.
        //   tileCountForMetasprite() returns null → MUST NOT skip via ?: continue.
        //   The actual tile count is unknown to Kotlin — png2asset deduplicates tiles and the true
        //   count is stored in the png2asset-generated array (e.g. elephant_tiles[704] = 44 tiles,
        //   not the geometric 64x48/64=48). ConvertSpritesTask emits a C macro
        //   `sprites_<id>_tiles_count` in sprites/<id>.h (Task 2). We reference the macro in C.
        //
        //   Start expression chaining (macro-based, not integer):
        //     base = allocator.tilesUsed (integer index after all actor reserves)
        //     1st asset-driven metasprite: oamStart = "${base}u"
        //     2nd: oamStart = "${base}u + sprites_<first>_tiles_count"
        //     3rd: oamStart = "${base}u + sprites_<first>_tiles_count +
        // sprites_<second>_tiles_count"
        //   The allocator is NOT advanced for asset-driven metasprites (count unknown to Kotlin).
        //   Because nothing is loaded after metasprites, the allocator need not advance.
        //
        // The base is captured once after all actor reserves are done (stable integer).
        val baseAfterActors = allocator.tilesUsed
        // Accumulate asset-driven metasprite ids already emitted for start-expression chaining.
        val assetDrivenEmitted = mutableListOf<String>()

        for (ms in gameIR.metasprites) {
            val isAssetDriven = ms.spritePath != null

            if (isAssetDriven) {
                // Path A (13.3-14 gap closure): emit symbolic set_sprite_data via CRawCode.
                // Start expression: baseAfterActors + cumulative sum of prior asset-driven count
                // macros.
                val startExpr =
                    if (assetDrivenEmitted.isEmpty()) {
                        "${baseAfterActors}u"
                    } else {
                        val priorCounts =
                            assetDrivenEmitted.joinToString(" + ") { "sprites_${it}_tiles_count" }
                        "${baseAfterActors}u + $priorCounts"
                    }
                val tilesArrayMacro = "sprites_${ms.id}_tiles" // bridges to <id>_tiles via #define
                val countMacro =
                    "sprites_${ms.id}_tiles_count" // emitted by ConvertSpritesTask Task 2
                statements.add(
                    CRawCode("set_sprite_data($startExpr, $countMacro, $tilesArrayMacro);")
                )
                assetDrivenEmitted.add(ms.id)
                // Do NOT advance allocator — count is unknown to Kotlin; nothing loads after
                // metasprites.
            } else {
                // Path B (escape-hatch D-04): integer count via tileCountForMetasprite.
                // Byte-identical to pre-13.3-14 emission for all procedural metasprites.
                val arrayName = "${ms.id}_tiles"
                val tileCount = MetaspriteVisitor.tileCountForMetasprite(ms) ?: continue
                val start = allocator.reserve(tileCount)
                statements.addAll(
                    MetaspriteVisitor.generateMetaspriteTileData(ms, arrayName, start)
                )
            }
        }

        return statements
    }

    /**
     * Build GBC-gated `set_sprite_palette()` statements for procedural (escape-hatch) metasprites.
     *
     * Phase 12.9 D2b fix: the metasprite's png2asset-generated `<id>_palettes` array must be
     * uploaded to OBJ color palette RAM on GBC. Without this call for procedural metasprites,
     * `player_palettes` is never uploaded and the character renders under the GBDK default
     * grayscale OBJ palette (OCPS = 0xC8 at boot).
     *
     * Phase 13.3-17 Direction B fix (PINK defect, evidence/13.3-DIAGNOSTIC.md): Asset-driven
     * metasprites (spritePath != null, e.g. the metasprites-example elephant) are EXCLUDED from
     * this upload. Their scene's explicit `spritePalette {}` blocks (Sites A and C in the
     * diagnostic) are the sole OBJ palette authority. Emitting a separate `set_sprite_palette(0u,
     * 1u, elephant_palettes)` caused a triple slot-collision:
     * 1. The upload only covered 1 sub-palette, leaving slot 1 = the scene's pink_pal.
     * 2. play_enter() re-uploaded gray to slot 0, overwriting the elephant sub-palette.
     * 3. The png2asset descriptor bakes per-OAM-entry S_PAL(1) indices that then pointed at the
     *    scene's pink palette → PINK elephant outline. Suppressing the upload for asset-driven
     *    metasprites restores the pre-migration behavior where the scene's gray slot 0 covered the
     *    elephant's S_PAL(0) tiles.
     *
     * The palette symbol for the escape-hatch path follows png2asset naming: `${ms.id}_palettes`.
     * The count is 1 (one 4-color sub-palette per procedural metasprite). OBJ palette slots are
     * assigned in metasprite order over PROCEDURAL metasprites only.
     *
     * GBC-gated: emit only when `gameIR.config.gbcTarget != GbcTarget.DMG`. Returns empty list for
     * DMG, no metasprites, or when all metasprites are asset-driven.
     */
    private fun buildMetaspriteSpritePaletteStatements(gameIR: GameIR): List<CStatement> {
        if (gameIR.config.gbcTarget == GbcTarget.DMG) return emptyList()
        if (gameIR.metasprites.isEmpty()) return emptyList()
        // Direction B (Phase 13.3-17): only emit set_sprite_palette for procedural (escape-hatch)
        // metasprites (spritePath == null). Asset-driven metasprites rely on the scene's
        // spritePalette{} uploads and must NOT have their png2asset palette array uploaded here.
        val proceduralMetasprites = gameIR.metasprites.filter { it.spritePath == null }
        // Req 5 (13.8-05, 12.9 WR-05): slot is derived from each metasprite's declared
        // initialSubPaletteSlot when non-null, falling back to list position (mapIndexed) when
        // null.
        // For shipped single-metasprite games (initialSubPaletteSlot = null), slot = list index = 0
        // → byte-identical output (D-03 zero-delta).
        val proceduralStatements = proceduralMetasprites.mapIndexed { listIdx, ms ->
            val slot = ms.initialSubPaletteSlot ?: listIdx
            CRawCode("set_sprite_palette(${slot}u, 1u, ${ms.id}_palettes);")
        }
        // Asset-driven fallback arm (Phase 13.7-03): when the game declares NO spritePalette{}
        // for the metasprite's owning scene, emit set_sprite_palette for each asset-driven
        // metasprite using its png2asset-generated palette array. Slot numbers start at
        // proceduralMetasprites.size to avoid collision with the procedural arm.
        // This closes the OBJ inversion root cause (D-04): without this upload the player renders
        // under cgb_compatibility() descending default → dark body / light halo inversion.
        //
        // Req 4 (13.8-05, 13.7 WR-05): suppression is now scene-scoped via MetaspriteIR.sceneId.
        // When ms.sceneId is non-null, only a SPRITE SetPalette op in THAT scene suppresses the
        // upload. When ms.sceneId is null (default), falls back to the game-global predicate
        // (any GBCPalette with type=SPRITE anywhere in gameIR.palettes) — preserving byte-identity
        // for all shipped games that have not declared scene linkage (D-03 zero-delta).
        //
        // Scene-scoped check: a scene "has a SPRITE palette" if it contains any SetPalette op with
        // type == SPRITE in its enter, frame, or exit scripts. This matches how spritePalette{} DSL
        // declarations are lowered into scene enter ops by the lang layer.
        val gameHasSpritePalette = gameIR.palettes.any { it.type == PaletteType.SPRITE }
        val assetDrivenMetasprites = gameIR.metasprites.filter { it.spritePath != null }
        val proceduralCount = proceduralMetasprites.size
        val assetDrivenStatements =
            assetDrivenMetasprites
                .mapIndexed { idx, ms ->
                    // Scene-scoped suppression: when the metasprite declares its owning scene,
                    // check only
                    // that scene for SPRITE palette ops. Otherwise use the game-global flag.
                    val suppressed =
                        if (ms.sceneId != null) {
                            val owningScene = gameIR.scenes.firstOrNull { it.id == ms.sceneId }
                            if (owningScene != null) {
                                val allOps =
                                    owningScene.enterOps +
                                        owningScene.frameOps +
                                        owningScene.exitOps
                                allOps.any { it is SetPalette && it.type == PaletteType.SPRITE }
                            } else {
                                // Unknown scene — fall back to game-global to be conservative
                                gameHasSpritePalette
                            }
                        } else {
                            gameHasSpritePalette
                        }
                    if (!suppressed) {
                        val slot = ms.initialSubPaletteSlot ?: (proceduralCount + idx)
                        CRawCode("set_sprite_palette(${slot}u, 1u, ${ms.id}_palettes);")
                            as CStatement?
                    } else {
                        null
                    }
                }
                .filterNotNull()
        return proceduralStatements + assetDrivenStatements
    }

    /**
     * Build `set_sprite_tile()` + initial `move_sprite()` statements for all actors with sprites.
     *
     * Mirrors [buildAllSpriteDataLoadStatements] actor-iteration tile accounting: each actor's OAM
     * slots and tile indices start immediately after the previous actor's. Delegates to
     * [ActorVisitor.generateOAMInit] for per-slot hardware setup.
     *
     * Note: this method still uses its OWN local `nextTile` counter because OAM init only consumes
     * actor tiles (no metasprite OAM slots), so the unification via [VramAllocator] does not apply
     * here — the local counter mirrors the actor-side accounting in the unified loader.
     *
     * @param excludeIds Actor IDs to skip for OAM init (pool template actors use dynamic
     *   allocation). Excluded actors still advance the tile counter to keep VRAM layout consistent
     *   but do NOT get `set_sprite_tile()` or `move_sprite()` calls and do NOT consume static OAM
     *   slots.
     */
    private fun buildOAMInitStatements(
        gameIR: GameIR,
        excludeIds: Set<String> = emptySet(),
    ): List<CStatement> {
        val statements = mutableListOf<CStatement>()
        var nextSlot = 0
        var nextTile = 0
        for (actor in gameIR.actors) {
            val sprite = actor.sprite ?: continue
            val tilesWide = (sprite.size.width + 7) / 8
            val tilesHigh = (sprite.size.height + 7) / 8
            val totalTiles = tilesWide * tilesHigh
            val sanitizedId = actor.id.replace('-', '_').replace('.', '_').replace(' ', '_')
            if (sanitizedId !in excludeIds) {
                statements.addAll(ActorVisitor.generateOAMInit(actor, nextSlot, nextTile))
                nextSlot += totalTiles
            }
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
        val slot = scene.bankSlot
        return if (slot != null && slot.bank > 0) {
            "${scene.id}_enter_trampoline"
        } else {
            "${scene.id}_enter"
        }
    }

    private fun frameFunctionName(scene: SceneIR): String {
        val slot = scene.bankSlot
        return if (slot != null && slot.bank > 0) {
            "${scene.id}_frame_trampoline"
        } else {
            "${scene.id}_frame"
        }
    }

    private fun exitFunctionName(scene: SceneIR): String {
        val slot = scene.bankSlot
        return if (slot != null && slot.bank > 0) {
            "${scene.id}_exit_trampoline"
        } else {
            "${scene.id}_exit"
        }
    }

    // =========================================================================
    // shouldAutoEmitExit — shared predicate for Req #15 / D-07 auto-exit synthesis
    // =========================================================================

    /**
     * True when a scene should have an auto-synthesized empty BANKED `${scene.id}_exit`.
     *
     * The predicate is used in three places:
     * 1. [SceneVisitor.visit] — emits the empty exit CFunction in bank1.c.
     * 2. [buildTrampolinesForScene] — emits `${scene.id}_exit_trampoline` in main.c (HOME bank).
     * 3. [buildNavigateToSceneFunction] — includes the scene in the exit switch cases.
     *
     * Predicate: the scene has no user-declared exit {} block (exitOps.isEmpty) AND the scene is in
     * a genuine banked slot (bankSlot.bank > 0 and non-null) AND the game uses MBC (cartridge
     * maxRomBanks > 2). ROM_ONLY games have maxRomBanks=2; isMbcGame=false → no auto-exit.
     *
     * @param scene The scene IR node.
     * @param isMbcGame True when cartridge.maxRomBanks > 2 (computed once per call site from
     *   [io.github.gbkt.core.ir.GameIR.config.cartridge.maxRomBanks]).
     */
    private fun shouldAutoEmitExit(scene: SceneIR, isMbcGame: Boolean): Boolean =
        scene.exitOps.isEmpty() && scene.bankSlot?.bank.let { it != null && it > 0 } && isMbcGame

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
