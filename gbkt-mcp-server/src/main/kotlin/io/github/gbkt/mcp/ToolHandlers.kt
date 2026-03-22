/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.mcp

import io.github.gbkt.emulator.agent.Button
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

/** Valid button names for MCP tool input. */
private val VALID_BUTTONS = Button.entries.map { it.name.lowercase() }.toSet()

/** Parses a button name string to a [Button], or null. */
private fun parseButton(name: String): Button? =
    Button.entries.find { it.name.equals(name, ignoreCase = true) }

/** Creates an error [CallToolResult] with the given message. */
internal fun errorResult(message: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(message)), isError = true)

/** Creates a success [CallToolResult] with a JSON text content. */
internal fun jsonResult(json: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(json)))

/**
 * Extracted handler logic for all 17 MCP tools.
 *
 * Each function parses arguments, calls [McpEmulatorSession], and returns a [CallToolResult].
 * This separation enables direct unit testing without the MCP Server/transport layer.
 */
internal object ToolHandlerLogic {

    /** Maximum frames any single step/wait call may advance (10 minutes at ~60 fps). */
    private const val MAX_FRAMES = 36_000

    /** Labels must be alphanumeric with hyphens/underscores only — no path traversal. */
    private val VALID_LABEL = Regex("^[a-zA-Z0-9_-]+$")

    suspend fun handleStart(session: McpEmulatorSession, args: JsonObject?): CallToolResult {
        args ?: return errorResult("Missing arguments")

        val gameName = args["game"]?.jsonPrimitive?.content
        val romPath = args["romFile"]?.jsonPrimitive?.content
        val gbcMode = args["gbcMode"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

        // If game name is provided and romFile is not, use convention-based discovery
        if (gameName != null && romPath == null) {
            return withContext(Dispatchers.IO) {
                try {
                    val result = session.startByName(gameName, gbcMode)
                    val meta = result.metadata
                    val summary = buildJsonObject {
                        put("started", true)
                        if (meta != null) {
                            put("metadata", meta.toJsonObject())
                        }
                    }
                    jsonResult(summary.toString())
                } catch (e: IllegalStateException) {
                    errorResult(e.message ?: "Failed to start game '$gameName'")
                }
            }
        }

        // Otherwise, romFile is required
        if (romPath == null) {
            return errorResult("romFile or game is required")
        }
        val romFile = File(romPath)
        if (!romFile.exists()) return errorResult("ROM file not found: $romPath")

        val symFile = args["symFile"]?.jsonPrimitive?.content?.let { File(it) }
        val metadataFile = args["metadataFile"]?.jsonPrimitive?.content?.let { File(it) }

        val result = session.start(romFile, symFile, metadataFile, gbcMode)
        val meta = result.metadata
        val summary = buildJsonObject {
            put("started", true)
            if (meta != null) {
                put("metadata", meta.toJsonObject())
            }
        }
        return jsonResult(summary.toString())
    }

    suspend fun handleStop(session: McpEmulatorSession): CallToolResult {
        session.stop()
        return jsonResult("""{"stopped":true}""")
    }

    suspend fun handleStep(session: McpEmulatorSession, args: JsonObject?): CallToolResult {
        val frames = args?.get("frames")?.jsonPrimitive?.intOrNull ?: 1
        if (frames < 1) return errorResult("frames must be positive")
        if (frames > MAX_FRAMES) {
            return errorResult(
                "frames ($frames) exceeds maximum ($MAX_FRAMES). " +
                    "Break into smaller steps or use wait_for_scene/wait_for_variable instead.",
            )
        }

        val buttonNames = args?.get("buttons")?.jsonArray
            ?.map { it.jsonPrimitive.content } ?: emptyList()
        val buttons = mutableSetOf<Button>()
        for (name in buttonNames) {
            val btn = parseButton(name)
                ?: return errorResult("Invalid button '$name'. Valid: $VALID_BUTTONS")
            buttons.add(btn)
        }

        val obs = session.step(frames, buttons)
        return jsonResult(obs.toJsonObject().toString())
    }

    suspend fun handlePress(session: McpEmulatorSession, args: JsonObject?): CallToolResult {
        args ?: return errorResult("Missing arguments")
        val buttonName = args["button"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("button is required")
        val button = parseButton(buttonName)
            ?: return errorResult("Invalid button '$buttonName'. Valid: $VALID_BUTTONS")
        val frames = args["frames"]?.jsonPrimitive?.intOrNull ?: 1
        if (frames < 1) return errorResult("frames must be positive")
        if (frames > MAX_FRAMES) return errorResult("frames exceeds maximum")
        val obs = session.press(button, frames)
        return jsonResult(obs.toJsonObject().toString())
    }

    suspend fun handleObserve(session: McpEmulatorSession): CallToolResult {
        val obs = session.observe()
        return jsonResult(obs.toJsonObject().toString())
    }

    suspend fun handleWaitForScene(session: McpEmulatorSession, args: JsonObject?): CallToolResult {
        args ?: return errorResult("Missing arguments")
        val scene = args["scene"]?.jsonPrimitive?.content
            ?: return errorResult("scene is required")
        val maxFrames = args["maxFrames"]?.jsonPrimitive?.int
            ?: return errorResult("maxFrames is required")
        if (maxFrames < 1) return errorResult("maxFrames must be positive")
        if (maxFrames > MAX_FRAMES) {
            return errorResult("maxFrames ($maxFrames) exceeds maximum ($MAX_FRAMES)")
        }

        val result = session.waitForScene(scene, maxFrames)
        val json = buildJsonObject {
            put("met", result.met)
            put("framesElapsed", result.framesElapsed)
            put("observation", result.observation.toJsonObject())
        }
        return jsonResult(json.toString())
    }

    suspend fun handleWaitForVariable(session: McpEmulatorSession, args: JsonObject?): CallToolResult {
        args ?: return errorResult("Missing arguments")
        val name = args["name"]?.jsonPrimitive?.content
            ?: return errorResult("name is required")
        val expected = args["expected"]?.jsonPrimitive?.int
            ?: return errorResult("expected is required")
        val maxFrames = args["maxFrames"]?.jsonPrimitive?.int
            ?: return errorResult("maxFrames is required")
        if (maxFrames < 1) return errorResult("maxFrames must be positive")
        if (maxFrames > MAX_FRAMES) {
            return errorResult("maxFrames ($maxFrames) exceeds maximum ($MAX_FRAMES)")
        }

        val result = session.waitForVariable(name, expected, maxFrames)
        val json = buildJsonObject {
            put("met", result.met)
            put("framesElapsed", result.framesElapsed)
            put("observation", result.observation.toJsonObject())
        }
        return jsonResult(json.toString())
    }

    suspend fun handleWaitUntilText(session: McpEmulatorSession, args: JsonObject?): CallToolResult {
        args ?: return errorResult("Missing arguments")
        val text = args["text"]?.jsonPrimitive?.content
            ?: return errorResult("text is required")
        val maxFrames = args["maxFrames"]?.jsonPrimitive?.int
            ?: return errorResult("maxFrames is required")
        if (maxFrames < 1) return errorResult("maxFrames must be positive")
        if (maxFrames > MAX_FRAMES) {
            return errorResult("maxFrames ($maxFrames) exceeds maximum ($MAX_FRAMES)")
        }

        val result = session.waitForText(text, maxFrames)
        val json = buildJsonObject {
            put("met", result.met)
            put("framesElapsed", result.framesElapsed)
            put("observation", result.observation.toJsonObject())
        }
        return jsonResult(json.toString())
    }

    suspend fun handleReadVariable(session: McpEmulatorSession, args: JsonObject?): CallToolResult {
        val name = args?.get("name")?.jsonPrimitive?.content
            ?: return errorResult("name is required")
        val result = session.readVariable(name)
        val json = buildJsonObject {
            put("name", result.name)
            put("value", result.value)
        }
        return jsonResult(json.toString())
    }

    suspend fun handleWriteVariable(session: McpEmulatorSession, args: JsonObject?): CallToolResult {
        args ?: return errorResult("Missing arguments")
        val name = args["name"]?.jsonPrimitive?.content
            ?: return errorResult("name is required")
        val value = args["value"]?.jsonPrimitive?.int
            ?: return errorResult("value is required")
        val success = session.writeVariable(name, value)
        val json = buildJsonObject { put("success", success) }
        return jsonResult(json.toString())
    }

    suspend fun handleScreenshot(session: McpEmulatorSession, args: JsonObject?): CallToolResult {
        val label = args?.get("label")?.jsonPrimitive?.content
            ?: return errorResult("label is required")
        if (!VALID_LABEL.matches(label)) {
            return errorResult(
                "Invalid label '$label'. " +
                    "Labels must match ${VALID_LABEL.pattern} (no path separators or special characters).",
            )
        }
        val file = session.screenshot(label)
        val json = buildJsonObject { put("filePath", file.absolutePath) }
        return jsonResult(json.toString())
    }

    fun handleDescribeGame(session: McpEmulatorSession): CallToolResult {
        val meta = session.describeGame()
        return if (meta != null) {
            jsonResult(meta.toJsonObject().toString())
        } else {
            jsonResult("""{"metadata":null}""")
        }
    }

    suspend fun handleSaveState(session: McpEmulatorSession, args: JsonObject?): CallToolResult {
        args ?: return errorResult("Missing arguments")
        val label = args["label"]?.jsonPrimitive?.content
            ?: return errorResult("label is required")
        if (!VALID_LABEL.matches(label)) {
            return errorResult(
                "Invalid label '$label'. " +
                    "Labels must match ${VALID_LABEL.pattern} (no path separators or special characters).",
            )
        }
        return withContext(Dispatchers.IO) {
            val result = session.saveState(label)
            jsonResult(result.toString())
        }
    }

    suspend fun handleLoadState(session: McpEmulatorSession, args: JsonObject?): CallToolResult {
        args ?: return errorResult("Missing arguments")
        val label = args["label"]?.jsonPrimitive?.content
            ?: return errorResult("label is required")
        if (!VALID_LABEL.matches(label)) {
            return errorResult(
                "Invalid label '$label'. " +
                    "Labels must match ${VALID_LABEL.pattern} (no path separators or special characters).",
            )
        }
        return withContext(Dispatchers.IO) {
            val result = session.loadState(label)
            jsonResult(result.toString())
        }
    }

    suspend fun handleAssert(session: McpEmulatorSession, args: JsonObject?): CallToolResult {
        args ?: return errorResult("Missing arguments")
        val checksArray = args["checks"]?.jsonArray
            ?: return errorResult("checks is required")

        val checks = mutableListOf<AssertCheck>()
        for (element in checksArray) {
            val obj = element.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content
                ?: return errorResult("Each check must have a 'type' field")
            val checkArgs = mutableMapOf<String, Any>()
            for ((k, v) in obj) {
                if (k != "type") {
                    checkArgs[k] = v.jsonPrimitive.content
                }
            }
            checks.add(AssertCheck(type, checkArgs))
        }

        return withContext(Dispatchers.IO) {
            val result = session.batchAssert(checks)
            jsonResult(result.toString())
        }
    }

    suspend fun handleGetPlaybook(session: McpEmulatorSession): CallToolResult {
        return withContext(Dispatchers.IO) {
            val result = session.getPlaybook()
            jsonResult(result.toString())
        }
    }

    suspend fun handleListGames(session: McpEmulatorSession): CallToolResult {
        return withContext(Dispatchers.IO) {
            val result = session.listGames()
            jsonResult(result.toString())
        }
    }
}

/**
 * Registers all 17 MCP tools on the given [Server].
 *
 * All tool handlers delegate to [ToolHandlerLogic] and wrap on [Dispatchers.IO].
 */
fun Server.registerEmulatorTools(session: McpEmulatorSession) {

    addTool(
        name = "emulator_start",
        description = "Start a Game Boy emulator session. Accepts either romFile (path) or game " +
            "(name) for convention-based discovery. Returns metadata summary.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("romFile") {
                    put("type", "string")
                    put("description", "Path to the Game Boy ROM file (.gb or .gbc)")
                }
                putJsonObject("game") {
                    put("type", "string")
                    put("description", "Game name for convention-based discovery (e.g., 'pong'). Alternative to romFile.")
                }
                putJsonObject("symFile") {
                    put("type", "string")
                    put("description", "Optional path to the .noi/.sym symbol file")
                }
                putJsonObject("metadataFile") {
                    put("type", "string")
                    put("description", "Optional path to game_metadata.json")
                }
                putJsonObject("gbcMode") {
                    put("type", "boolean")
                    put("description", "Enable Game Boy Color mode")
                }
            },
            required = emptyList(),
        ),
    ) { request ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleStart(session, request.arguments)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_start] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_stop",
        description = "Stop the current emulator session.",
        inputSchema = ToolSchema(properties = buildJsonObject {}, required = emptyList()),
    ) { _ ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleStop(session)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_stop] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_step",
        description = "Advance the emulator by N frames with optional buttons held. Returns full observation.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("frames") {
                    put("type", "integer")
                    put("description", "Number of frames to advance (default 1)")
                }
                putJsonObject("buttons") {
                    put("type", "array")
                    put("description", "Buttons to hold: up, down, left, right, a, b, start, select")
                    putJsonObject("items") { put("type", "string") }
                }
            },
            required = emptyList(),
        ),
    ) { request ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleStep(session, request.arguments)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_step] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_press",
        description = "Press a single button for N frames then release and advance 1 frame. " +
            "Returns full Observation after release. Matches GBDK pressed() edge semantics. " +
            "Advances frames+1 total frames (hold + release). Use instead of emulator_step " +
            "for simple button taps.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("button") {
                    put("type", "string")
                    put("description", "Button to press: up, down, left, right, a, b, start, select")
                }
                putJsonObject("frames") {
                    put("type", "integer")
                    put("description", "How many frames to hold the button (default 1 for a tap)")
                }
            },
            required = listOf("button"),
        ),
    ) { request ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handlePress(session, request.arguments)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_press] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_observe",
        description = "Get the current game state without advancing frames. Returns cached observation or steps 1 frame.",
        inputSchema = ToolSchema(properties = buildJsonObject {}, required = emptyList()),
    ) { _ ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleObserve(session)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_observe] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_wait_for_scene",
        description = "Step frames until the current scene matches the target, or maxFrames is exhausted.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("scene") {
                    put("type", "string")
                    put("description", "Target scene name")
                }
                putJsonObject("maxFrames") {
                    put("type", "integer")
                    put("description", "Maximum frames to wait")
                }
            },
            required = listOf("scene", "maxFrames"),
        ),
    ) { request ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleWaitForScene(session, request.arguments)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_wait_for_scene] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_wait_for_variable",
        description = "Step frames until a variable equals the expected value, or maxFrames is exhausted.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "Variable name")
                }
                putJsonObject("expected") {
                    put("type", "integer")
                    put("description", "Expected value")
                }
                putJsonObject("maxFrames") {
                    put("type", "integer")
                    put("description", "Maximum frames to wait")
                }
            },
            required = listOf("name", "expected", "maxFrames"),
        ),
    ) { request ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleWaitForVariable(session, request.arguments)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_wait_for_variable] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_wait_until_text",
        description = "Step frames until text appears on screen, or maxFrames is exhausted.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("text") {
                    put("type", "string")
                    put("description", "Text to search for on screen")
                }
                putJsonObject("maxFrames") {
                    put("type", "integer")
                    put("description", "Maximum frames to wait")
                }
            },
            required = listOf("text", "maxFrames"),
        ),
    ) { request ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleWaitUntilText(session, request.arguments)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_wait_until_text] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_read_variable",
        description = "Read the current value of a named game variable.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "Variable name (e.g., 'score', 'lives')")
                }
            },
            required = listOf("name"),
        ),
    ) { request ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleReadVariable(session, request.arguments)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_read_variable] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_write_variable",
        description = "Write a value to a named game variable.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "Variable name")
                }
                putJsonObject("value") {
                    put("type", "integer")
                    put("description", "Byte value to write (0-255)")
                }
            },
            required = listOf("name", "value"),
        ),
    ) { request ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleWriteVariable(session, request.arguments)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_write_variable] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_screenshot",
        description = "Capture a screenshot of the current game state.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("label") {
                    put("type", "string")
                    put("description", "Label for the screenshot file name")
                }
            },
            required = listOf("label"),
        ),
    ) { request ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleScreenshot(session, request.arguments)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_screenshot] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_describe_game",
        description = "Get full game metadata: scenes, actors, variables (with semantic categories), texts, terminal scenes, per-scene controls, and scene transition graph.",
        inputSchema = ToolSchema(properties = buildJsonObject {}, required = emptyList()),
    ) { _ ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleDescribeGame(session)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_describe_game] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_save_state",
        description = "Save current emulator state with a label for later restore. " +
            "State is written to build/gbkt/savestates/<label>.gbst.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("label") {
                    put("type", "string")
                    put("description", "Identifier for the savestate (used as file name)")
                }
            },
            required = listOf("label"),
        ),
    ) { request ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleSaveState(session, request.arguments)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_save_state] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_load_state",
        description = "Load a previously saved emulator state by label. " +
            "Reads from build/gbkt/savestates/<label>.gbst.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("label") {
                    put("type", "string")
                    put("description", "The savestate label used when saving")
                }
            },
            required = listOf("label"),
        ),
    ) { request ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleLoadState(session, request.arguments)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_load_state] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_assert",
        description = "Batch assert multiple conditions against current game state. " +
            "Types: variable_equals, variable_in_range, scene_is, text_on_screen, " +
            "actor_visible, sprite_count. " +
            "text_on_screen returns x, y, layer when found. " +
            "text_on_screen supports optional scrollAware field (default false) for games " +
            "using BG scroll registers. " +
            "Example: [{\"type\":\"variable_equals\",\"name\":\"p1Score\",\"expected\":\"5\"}]",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("checks") {
                    put("type", "array")
                    put(
                        "description",
                        "Array of assertion checks. Each item: {type, ...args}. " +
                            "variable_equals: {name, expected}; " +
                            "variable_in_range: {name, min, max}; " +
                            "scene_is: {scene}; " +
                            "text_on_screen: {text, scrollAware?}; " +
                            "actor_visible: {name}; " +
                            "sprite_count: {expected}",
                    )
                    putJsonObject("items") { put("type", "object") }
                }
            },
            required = listOf("checks"),
        ),
    ) { request ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleAssert(session, request.arguments)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_assert] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_get_playbook",
        description = "Get the PLAYBOOK.md content for the currently loaded game. " +
            "Returns the file content and path, or null if not found.",
        inputSchema = ToolSchema(properties = buildJsonObject {}, required = emptyList()),
    ) { _ ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleGetPlaybook(session)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_get_playbook] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }

    addTool(
        name = "emulator_list_games",
        description = "List all built games found in the project. " +
            "Scans both standalone (build/gbkt/output/) and multi-game (gbkt-examples/) layouts.",
        inputSchema = ToolSchema(properties = buildJsonObject {}, required = emptyList()),
    ) { _ ->
        withContext(Dispatchers.IO) {
            try {
                ToolHandlerLogic.handleListGames(session)
            } catch (e: Exception) {
                System.err.println("MCP [emulator_list_games] error: $e")
                errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            }
        }
    }
}
