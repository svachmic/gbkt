/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import java.io.File
import org.json.JSONObject

/**
 * Tile decoder configuration for a single tilemap layer.
 *
 * @param type Decoder type: "gbdk_offset", "direct_ascii", or "custom".
 * @param mapping Tile index to character mapping. Only used when type is "custom".
 */
data class TileDecoderConfig(val type: String, val mapping: Map<Int, Char> = emptyMap())

/** Per-layer tile decoder configuration for BG and WIN tilemap layers. */
data class TileDecoders(val bg: TileDecoderConfig? = null, val win: TileDecoderConfig? = null)

/**
 * A DSL-declared variable parsed from the codegen-emitted `game_metadata.json`.
 *
 * @param name Variable name as declared in the DSL (e.g., `"score"`, `"ballDx"`).
 * @param type Variable type string (e.g., `"U8"`, `"I8"`).
 * @param semantic Semantic category (e.g., `"score"`, `"counter"`, `"unknown"`).
 */
data class VariableDef(val name: String, val type: String, val semantic: String = "unknown")

/** A per-scene input mapping parsed from `game_metadata.json`. */
data class ControlMapping(val button: String, val type: String)

/** A scene-to-scene navigation edge parsed from `game_metadata.json`. */
data class TransitionEdgeMeta(val from: String, val to: String)

/**
 * Metadata for a single game actor parsed from the codegen-emitted `game_metadata.json`.
 *
 * @param name Actor ID (e.g., `"ball"`, `"paddle1"`).
 * @param oamStart First OAM slot index occupied by this actor's sprite tiles.
 * @param oamCount Number of consecutive OAM slots occupied (based on tile count).
 * @param spriteWidth Sprite width in pixels.
 * @param spriteHeight Sprite height in pixels.
 * @param xVar DSL variable name for the actor's X position (e.g., `"ball_x"`).
 * @param yVar DSL variable name for the actor's Y position (e.g., `"ball_y"`).
 */
data class ActorMetadata(
    val name: String,
    val oamStart: Int,
    val oamCount: Int,
    val spriteWidth: Int,
    val spriteHeight: Int,
    val xVar: String,
    val yVar: String,
)

/**
 * Parsed game metadata providing actor-to-OAM-slot mapping and scene information.
 *
 * The codegen pipeline emits `game_metadata.json` alongside `game.h` and `main.c`. This class
 * parses that JSON and provides lookup methods for resolving OAM sprite slots to named actors and
 * accessing scene information.
 *
 * Usage:
 * ```kotlin
 * val metadata = GameMetadata.fromJsonFile(File("build/gbkt/generated/game_metadata.json"))
 * val actorName = metadata.actorForSlot(4)  // "ball"
 * val ballMeta = metadata.actor("ball")     // ActorMetadata(...)
 * ```
 *
 * @param scenes Bidirectional scene name/index mapping.
 * @param actors List of actor metadata entries with OAM slot assignments.
 */
class GameMetadata(
    val scenes: SceneMap,
    val actors: List<ActorMetadata>,
    val variables: List<VariableDef> = emptyList(),
    val texts: List<String> = emptyList(),
    val terminalScenes: Set<String> = emptySet(),
    val controls: Map<String, List<ControlMapping>> = emptyMap(),
    val transitions: List<TransitionEdgeMeta> = emptyList(),
    val tileDecoders: TileDecoders? = null,
) {
    /** Map from OAM slot index to actor name. Covers all slots in each actor's range. */
    private val slotToActor: Map<Int, String> = buildMap {
        for (actor in actors) {
            for (slot in actor.oamStart until actor.oamStart + actor.oamCount) {
                put(slot, actor.name)
            }
        }
    }

    /** Resolves a [SpriteEntry]'s OAM slot to its actor name, or null if the slot is unassigned. */
    fun actorForSlot(oamSlot: Int): String? = slotToActor[oamSlot]

    /** Finds actor metadata by [name], or null if no actor with that name exists. */
    fun actor(name: String): ActorMetadata? = actors.find { it.name == name }

    companion object {
        /**
         * Parses a `game_metadata.json` file into a [GameMetadata] instance.
         *
         * Expected JSON format:
         * ```json
         * {
         *   "scenes": { "title": 0, "game": 1, "gameover": 2 },
         *   "actors": [
         *     {
         *       "name": "ball",
         *       "oamStart": 4,
         *       "oamCount": 1,
         *       "spriteWidth": 8,
         *       "spriteHeight": 8,
         *       "vars": { "x": "ball_x", "y": "ball_y" }
         *     }
         *   ]
         * }
         * ```
         *
         * @param file The `game_metadata.json` file to parse.
         * @return A [GameMetadata] instance with scenes and actors.
         */
        fun fromJsonFile(file: File): GameMetadata = fromJsonString(file.readText())

        /**
         * Parses a JSON string into a [GameMetadata] instance.
         *
         * @param json The JSON content to parse.
         * @return A [GameMetadata] instance with scenes and actors.
         */
        fun fromJsonString(json: String): GameMetadata {
            try {
                val root = JSONObject(json)

                // Validate required fields
                if (!root.has("scenes")) {
                    throw MetadataParseException("Missing required field 'scenes'")
                }
                if (!root.has("actors")) {
                    throw MetadataParseException("Missing required field 'actors'")
                }

                // Parse scenes — must be a JSON object
                val scenesObj = root.opt("scenes")
                if (scenesObj !is org.json.JSONObject) {
                    throw MetadataParseException(
                        "'scenes' must be a JSON object, got ${scenesObj?.javaClass?.simpleName}"
                    )
                }
                val sceneEntries = mutableMapOf<String, Int>()
                for (key in scenesObj.keys()) {
                    sceneEntries[key] = scenesObj.getInt(key)
                }
                val scenes = SceneMap(sceneEntries)

                // Parse actors — must be a JSON array
                val actorsObj = root.opt("actors")
                if (actorsObj !is org.json.JSONArray) {
                    throw MetadataParseException(
                        "'actors' must be a JSON array, got ${actorsObj?.javaClass?.simpleName}"
                    )
                }
                val actors =
                    (0 until actorsObj.length()).map { i ->
                        val actorJson = actorsObj.getJSONObject(i)
                        val vars = actorJson.getJSONObject("vars")
                        ActorMetadata(
                            name = actorJson.getString("name"),
                            oamStart = actorJson.getInt("oamStart"),
                            oamCount = actorJson.getInt("oamCount"),
                            spriteWidth = actorJson.getInt("spriteWidth"),
                            spriteHeight = actorJson.getInt("spriteHeight"),
                            xVar = vars.getString("x"),
                            yVar = vars.getString("y"),
                        )
                    }

                // Parse variables (optional)
                val variablesJson = root.optJSONArray("variables")
                val variables =
                    if (variablesJson != null) {
                        (0 until variablesJson.length()).map { i ->
                            val v = variablesJson.getJSONObject(i)
                            VariableDef(
                                name = v.getString("name"),
                                type = v.getString("type"),
                                semantic = v.optString("semantic", "unknown"),
                            )
                        }
                    } else {
                        emptyList()
                    }

                // Parse texts (optional)
                val textsJson = root.optJSONArray("texts")
                val texts =
                    if (textsJson != null) {
                        (0 until textsJson.length()).map { i -> textsJson.getString(i) }
                    } else {
                        emptyList()
                    }

                // Parse terminal scenes (optional)
                val terminalJson = root.optJSONArray("terminalScenes")
                val terminalScenes =
                    if (terminalJson != null) {
                        (0 until terminalJson.length())
                            .map { i -> terminalJson.getString(i) }
                            .toSet()
                    } else {
                        emptySet()
                    }

                // Parse controls (optional) — per-scene input mappings
                val controlsJson = root.optJSONObject("controls")
                val controls: Map<String, List<ControlMapping>> =
                    if (controlsJson != null) {
                        buildMap {
                            for (sceneId in controlsJson.keys()) {
                                val arr = controlsJson.getJSONArray(sceneId)
                                put(
                                    sceneId,
                                    (0 until arr.length()).map { i ->
                                        val m = arr.getJSONObject(i)
                                        ControlMapping(
                                            button = m.getString("button"),
                                            type = m.getString("type"),
                                        )
                                    },
                                )
                            }
                        }
                    } else {
                        emptyMap()
                    }

                // Parse transitions (optional) — scene navigation graph
                val transitionsJson = root.optJSONArray("transitions")
                val transitions: List<TransitionEdgeMeta> =
                    if (transitionsJson != null) {
                        (0 until transitionsJson.length()).map { i ->
                            val t = transitionsJson.getJSONObject(i)
                            TransitionEdgeMeta(from = t.getString("from"), to = t.getString("to"))
                        }
                    } else {
                        emptyList()
                    }

                // Parse tileDecoders (optional) — tile decoder config for BG and WIN layers
                val tileDecodersJson = root.optJSONObject("tileDecoders")
                val tileDecoders =
                    if (tileDecodersJson != null) {
                        fun parseTdc(key: String): TileDecoderConfig? {
                            val obj = tileDecodersJson.optJSONObject(key) ?: return null
                            val tdcType = obj.getString("type")
                            val mapping =
                                if (tdcType == "custom" && obj.has("mapping")) {
                                    val m = obj.getJSONObject("mapping")
                                    buildMap {
                                        for (k in m.keys()) put(k.toInt(), m.getString(k).first())
                                    }
                                } else {
                                    emptyMap()
                                }
                            return TileDecoderConfig(type = tdcType, mapping = mapping)
                        }
                        TileDecoders(bg = parseTdc("bg"), win = parseTdc("win"))
                    } else {
                        null
                    }

                return GameMetadata(
                    scenes = scenes,
                    actors = actors,
                    variables = variables,
                    texts = texts,
                    terminalScenes = terminalScenes,
                    controls = controls,
                    transitions = transitions,
                    tileDecoders = tileDecoders,
                )
            } catch (e: MetadataParseException) {
                throw e
            } catch (e: Exception) {
                throw MetadataParseException("Failed to parse game metadata: ${e.message}", e)
            }
        }

        /**
         * Creates a [GameMetadata] from explicit scene and actor data.
         *
         * Useful for unit tests or when constructing metadata programmatically.
         */
        fun of(
            scenes: SceneMap,
            actors: List<ActorMetadata>,
            variables: List<VariableDef> = emptyList(),
            texts: List<String> = emptyList(),
            terminalScenes: Set<String> = emptySet(),
            controls: Map<String, List<ControlMapping>> = emptyMap(),
            transitions: List<TransitionEdgeMeta> = emptyList(),
            tileDecoders: TileDecoders? = null,
        ): GameMetadata =
            GameMetadata(
                scenes = scenes,
                actors = actors,
                variables = variables,
                texts = texts,
                terminalScenes = terminalScenes,
                controls = controls,
                transitions = transitions,
                tileDecoders = tileDecoders,
            )
    }
}

// ── Extension functions for tile decoder resolution ────────────────────────────

/**
 * Returns the [VramTextVerifier.TileDecoder] for the BG tilemap layer, or null if no tile decoder
 * config is present.
 */
fun GameMetadata.bgDecoder(): VramTextVerifier.TileDecoder? = tileDecoders?.bg?.toDecoder()

/**
 * Returns the [VramTextVerifier.TileDecoder] for the WIN tilemap layer, or null if no tile decoder
 * config is present.
 */
fun GameMetadata.winDecoder(): VramTextVerifier.TileDecoder? = tileDecoders?.win?.toDecoder()

private fun TileDecoderConfig.toDecoder(): VramTextVerifier.TileDecoder? =
    when (type) {
        "gbdk_offset" -> VramTextVerifier.GBDK_BG_DECODER
        "direct_ascii" -> VramTextVerifier.DIRECT_ASCII_DECODER
        "custom" -> VramTextVerifier.TileDecoder { tile -> mapping[tile] ?: '.' }
        else -> null
    }
