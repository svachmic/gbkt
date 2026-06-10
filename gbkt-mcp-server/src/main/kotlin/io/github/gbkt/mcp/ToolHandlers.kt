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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Valid button names for MCP tool input. */
private val VALID_BUTTONS = Button.entries.map { it.name.lowercase() }.toSet()

/** Shared input-schema description for maxFrames parameters. */
private const val MAX_FRAMES_DESCRIPTION = "Maximum frames to wait"

/** Parses a button name string to a [Button], or null. */
private fun parseButton(name: String): Button? =
    Button.entries.find { it.name.equals(name, ignoreCase = true) }

/** Creates an error [CallToolResult] with the given message. */
internal fun errorResult(message: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(message)), isError = true)

/** Creates a success [CallToolResult] with a JSON text content. */
internal fun jsonResult(json: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(json)))

/** Runs a tool handler on [Dispatchers.IO], converting any exception into an error result. */
private suspend fun runTool(toolName: String, block: suspend () -> CallToolResult): CallToolResult =
    withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Exception) {
            System.err.println("MCP [$toolName] error: $e")
            errorResult("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
        }
    }

/**
 * Extracted handler logic for all 17 MCP tools.
 *
 * Each function parses arguments, calls [McpEmulatorSession], and returns a [CallToolResult]. This
 * separation enables direct unit testing without the MCP Server/transport layer.
 */
internal object ToolHandlerLogic {

    /** Maximum frames any single step/wait call may advance (10 minutes at ~60 fps). */
    private const val MAX_FRAMES = 36_000

    /** Labels must be alphanumeric with hyphens/underscores only — no path traversal. */
    private val VALID_LABEL = Regex("^[a-zA-Z0-9_-]+$")

    /** Signals an argument-validation failure; converted to an error result by [toolResult]. */
    private class ToolArgException(override val message: String) : Exception(message)

    /** Aborts the current handler with the given user-facing error message. */
    private fun fail(message: String): Nothing = throw ToolArgException(message)

    /** Runs [block], converting any [ToolArgException] thrown by [fail] into an error result. */
    private inline fun toolResult(block: () -> CallToolResult): CallToolResult =
        try {
            block()
        } catch (e: ToolArgException) {
            errorResult(e.message)
        }

    /** Returns the arguments object, or fails with "Missing arguments". */
    private fun requireArgs(args: JsonObject?): JsonObject = args ?: fail("Missing arguments")

    /** Extracts a required string argument, or fails with "<name> is required". */
    private fun JsonObject?.requireString(name: String): String =
        this?.get(name)?.jsonPrimitive?.contentOrNull ?: fail("$name is required")

    /** Extracts a required integer argument, or fails with "<name> is required". */
    private fun JsonObject?.requireInt(name: String): Int =
        this?.get(name)?.jsonPrimitive?.intOrNull ?: fail("$name is required")

    /** Extracts a required frame-count argument, validating positivity and [MAX_FRAMES]. */
    private fun JsonObject?.requireFrames(name: String = "maxFrames"): Int {
        val frames = requireInt(name)
        if (frames < 1) fail("$name must be positive")
        if (frames > MAX_FRAMES) fail("$name ($frames) exceeds maximum ($MAX_FRAMES)")
        return frames
    }

    /** Extracts a required label argument, validating it against [VALID_LABEL]. */
    private fun JsonObject?.requireLabel(): String {
        val label = requireString("label")
        if (!VALID_LABEL.matches(label)) {
            fail(
                "Invalid label '$label'. " +
                    "Labels must match ${VALID_LABEL.pattern} (no path separators or special characters)."
            )
        }
        return label
    }

    /** Extracts a required address argument as hex ("0xNNNN") or decimal, in 0x0000..0xFFFF. */
    private fun JsonObject?.requireAddress(): Int {
        val addressHex = requireString("address")
        val address =
            try {
                if (addressHex.startsWith("0x") || addressHex.startsWith("0X")) {
                    addressHex.substring(2).toInt(16)
                } else {
                    addressHex.toInt()
                }
            } catch (e: NumberFormatException) {
                fail("Invalid address format: '$addressHex' (expected 0xNNNN or decimal)")
            }
        if (address !in 0..0xFFFF) fail("address out of range (0x0000..0xFFFF)")
        return address
    }

    suspend fun handleStart(session: McpEmulatorSession, args: JsonObject?): CallToolResult {
        return toolResult {
            val a = requireArgs(args)

            val gameName = a["game"]?.jsonPrimitive?.content
            val romPath = a["romFile"]?.jsonPrimitive?.content
            val gbcMode = a["gbcMode"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

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

            val symFile = a["symFile"]?.jsonPrimitive?.content?.let { File(it) }
            val metadataFile = a["metadataFile"]?.jsonPrimitive?.content?.let { File(it) }

            val result = session.start(romFile, symFile, metadataFile, gbcMode)
            val meta = result.metadata
            val summary = buildJsonObject {
                put("started", true)
                if (meta != null) {
                    put("metadata", meta.toJsonObject())
                }
            }
            jsonResult(summary.toString())
        }
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
                    "Break into smaller steps or use wait_for_scene/wait_for_variable instead."
            )
        }

        val buttonNames =
            args?.get("buttons")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val buttons = mutableSetOf<Button>()
        for (name in buttonNames) {
            val btn =
                parseButton(name)
                    ?: return errorResult("Invalid button '$name'. Valid: $VALID_BUTTONS")
            buttons.add(btn)
        }

        val obs = session.step(frames, buttons)
        return jsonResult(obs.toJsonObject().toString())
    }

    suspend fun handlePress(session: McpEmulatorSession, args: JsonObject?): CallToolResult =
        toolResult {
            val a = requireArgs(args)
            val buttonName = a.requireString("button")
            val button =
                parseButton(buttonName)
                    ?: fail("Invalid button '$buttonName'. Valid: $VALID_BUTTONS")
            val frames = a["frames"]?.jsonPrimitive?.intOrNull ?: 1
            if (frames < 1) fail("frames must be positive")
            if (frames > MAX_FRAMES) fail("frames exceeds maximum")
            val obs = session.press(button, frames)
            jsonResult(obs.toJsonObject().toString())
        }

    suspend fun handleObserve(session: McpEmulatorSession): CallToolResult {
        val obs = session.observe()
        return jsonResult(obs.toJsonObject().toString())
    }

    suspend fun handleWaitForScene(session: McpEmulatorSession, args: JsonObject?): CallToolResult =
        toolResult {
            val a = requireArgs(args)
            val scene = a.requireString("scene")
            val maxFrames = a.requireFrames()

            val result = session.waitForScene(scene, maxFrames)
            val json = buildJsonObject {
                put("met", result.met)
                put("framesElapsed", result.framesElapsed)
                put("observation", result.observation.toJsonObject())
            }
            jsonResult(json.toString())
        }

    suspend fun handleWaitForVariable(
        session: McpEmulatorSession,
        args: JsonObject?,
    ): CallToolResult = toolResult {
        val a = requireArgs(args)
        val name = a.requireString("name")
        val expected = a.requireInt("expected")
        val maxFrames = a.requireFrames()

        val result = session.waitForVariable(name, expected, maxFrames)
        val json = buildJsonObject {
            put("met", result.met)
            put("framesElapsed", result.framesElapsed)
            put("observation", result.observation.toJsonObject())
        }
        jsonResult(json.toString())
    }

    suspend fun handleWaitUntilText(
        session: McpEmulatorSession,
        args: JsonObject?,
    ): CallToolResult = toolResult {
        val a = requireArgs(args)
        val text = a.requireString("text")
        val maxFrames = a.requireFrames()

        val result = session.waitForText(text, maxFrames)
        val json = buildJsonObject {
            put("met", result.met)
            put("framesElapsed", result.framesElapsed)
            put("observation", result.observation.toJsonObject())
        }
        jsonResult(json.toString())
    }

    suspend fun handleReadVariable(session: McpEmulatorSession, args: JsonObject?): CallToolResult =
        toolResult {
            val name = args.requireString("name")
            val result = session.readVariable(name)
            val json = buildJsonObject {
                put("name", result.name)
                put("value", result.value)
            }
            jsonResult(json.toString())
        }

    suspend fun handleWriteVariable(
        session: McpEmulatorSession,
        args: JsonObject?,
    ): CallToolResult = toolResult {
        val a = requireArgs(args)
        val name = a.requireString("name")
        val value = a.requireInt("value")
        val success = session.writeVariable(name, value)
        val json = buildJsonObject { put("success", success) }
        jsonResult(json.toString())
    }

    suspend fun handleReadMemory(session: McpEmulatorSession, args: JsonObject?): CallToolResult =
        toolResult {
            val a = requireArgs(args)
            val address = a.requireAddress()
            val count = (a["count"]?.jsonPrimitive?.intOrNull ?: 1).coerceIn(1, 256)
            val endAddress = address + count - 1
            if (endAddress > 0xFFFF) {
                fail(
                    "address + count - 1 (0x${"%04X".format(endAddress)}) exceeds 0xFFFF. " +
                        "Reduce count or use a lower start address."
                )
            }
            val bytes = session.readMemory(address, count)
            val json = buildJsonObject {
                put("address", "0x%04X".format(address))
                put("count", count)
                put("bytes", buildJsonArray { bytes.forEach { add("0x%02X".format(it and 0xFF)) } })
            }
            jsonResult(json.toString())
        }

    suspend fun handleWriteMemory(session: McpEmulatorSession, args: JsonObject?): CallToolResult =
        toolResult {
            val a = requireArgs(args)
            val address = a.requireAddress()
            val value = a.requireInt("value")
            if (value !in 0..255) {
                fail(
                    "value $value is out of range (0..255). " +
                        "emulator_write_memory writes exactly one byte."
                )
            }
            session.writeMemory(address, value)
            val json = buildJsonObject {
                put("success", true)
                put("address", "0x%04X".format(address))
                put("value", "0x%02X".format(value))
            }
            jsonResult(json.toString())
        }

    suspend fun handleScreenshot(session: McpEmulatorSession, args: JsonObject?): CallToolResult =
        toolResult {
            val label = args.requireLabel()
            val file = session.screenshot(label)
            val json = buildJsonObject { put("filePath", file.absolutePath) }
            jsonResult(json.toString())
        }

    fun handleDescribeGame(session: McpEmulatorSession): CallToolResult {
        val meta = session.describeGame()
        return if (meta != null) {
            jsonResult(meta.toJsonObject().toString())
        } else {
            jsonResult("""{"metadata":null}""")
        }
    }

    suspend fun handleSaveState(session: McpEmulatorSession, args: JsonObject?): CallToolResult =
        toolResult {
            val a = requireArgs(args)
            val label = a.requireLabel()
            withContext(Dispatchers.IO) {
                val result = session.saveState(label)
                jsonResult(result.toString())
            }
        }

    suspend fun handleLoadState(session: McpEmulatorSession, args: JsonObject?): CallToolResult =
        toolResult {
            val a = requireArgs(args)
            val label = a.requireLabel()
            withContext(Dispatchers.IO) {
                val result = session.loadState(label)
                jsonResult(result.toString())
            }
        }

    suspend fun handleAssert(session: McpEmulatorSession, args: JsonObject?): CallToolResult =
        toolResult {
            val a = requireArgs(args)
            val checksArray = a["checks"]?.jsonArray ?: fail("checks is required")

            val checks = mutableListOf<AssertCheck>()
            for (element in checksArray) {
                val obj = element.jsonObject
                val type =
                    obj["type"]?.jsonPrimitive?.content
                        ?: fail("Each check must have a 'type' field")
                val checkArgs = mutableMapOf<String, Any>()
                for ((k, v) in obj) {
                    if (k != "type") {
                        checkArgs[k] = v.jsonPrimitive.content
                    }
                }
                checks.add(AssertCheck(type, checkArgs))
            }

            withContext(Dispatchers.IO) {
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
        description =
            "Start a Game Boy emulator session. Accepts either romFile (path) or game " +
                "(name) for convention-based discovery. Returns metadata summary.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("romFile") {
                            put("type", "string")
                            put("description", "Path to the Game Boy ROM file (.gb or .gbc)")
                        }
                        putJsonObject("game") {
                            put("type", "string")
                            put(
                                "description",
                                "Game name for convention-based discovery (e.g., 'pong'). Alternative to romFile.",
                            )
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
        runTool("emulator_start") { ToolHandlerLogic.handleStart(session, request.arguments) }
    }

    addTool(
        name = "emulator_stop",
        description = "Stop the current emulator session.",
        inputSchema = ToolSchema(properties = buildJsonObject {}, required = emptyList()),
    ) { _ ->
        runTool("emulator_stop") { ToolHandlerLogic.handleStop(session) }
    }

    addTool(
        name = "emulator_step",
        description =
            "Advance the emulator by N frames with optional buttons held. Returns full observation.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("frames") {
                            put("type", "integer")
                            put("description", "Number of frames to advance (default 1)")
                        }
                        putJsonObject("buttons") {
                            put("type", "array")
                            put(
                                "description",
                                "Buttons to hold: up, down, left, right, a, b, start, select",
                            )
                            putJsonObject("items") { put("type", "string") }
                        }
                    },
                required = emptyList(),
            ),
    ) { request ->
        runTool("emulator_step") { ToolHandlerLogic.handleStep(session, request.arguments) }
    }

    addTool(
        name = "emulator_press",
        description =
            "Press a single button for N frames then release and advance 1 frame. " +
                "Returns full Observation after release. Matches GBDK pressed() edge semantics. " +
                "Advances frames+1 total frames (hold + release). Use instead of emulator_step " +
                "for simple button taps.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("button") {
                            put("type", "string")
                            put(
                                "description",
                                "Button to press: up, down, left, right, a, b, start, select",
                            )
                        }
                        putJsonObject("frames") {
                            put("type", "integer")
                            put(
                                "description",
                                "How many frames to hold the button (default 1 for a tap)",
                            )
                        }
                    },
                required = listOf("button"),
            ),
    ) { request ->
        runTool("emulator_press") { ToolHandlerLogic.handlePress(session, request.arguments) }
    }

    addTool(
        name = "emulator_observe",
        description =
            "Get the current game state without advancing frames. Returns cached observation or steps 1 frame.",
        inputSchema = ToolSchema(properties = buildJsonObject {}, required = emptyList()),
    ) { _ ->
        runTool("emulator_observe") { ToolHandlerLogic.handleObserve(session) }
    }

    addTool(
        name = "emulator_wait_for_scene",
        description =
            "Step frames until the current scene matches the target, or maxFrames is exhausted.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("scene") {
                            put("type", "string")
                            put("description", "Target scene name")
                        }
                        putJsonObject("maxFrames") {
                            put("type", "integer")
                            put("description", MAX_FRAMES_DESCRIPTION)
                        }
                    },
                required = listOf("scene", "maxFrames"),
            ),
    ) { request ->
        runTool("emulator_wait_for_scene") {
            ToolHandlerLogic.handleWaitForScene(session, request.arguments)
        }
    }

    addTool(
        name = "emulator_wait_for_variable",
        description =
            "Step frames until a variable equals the expected value, or maxFrames is exhausted.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
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
                            put("description", MAX_FRAMES_DESCRIPTION)
                        }
                    },
                required = listOf("name", "expected", "maxFrames"),
            ),
    ) { request ->
        runTool("emulator_wait_for_variable") {
            ToolHandlerLogic.handleWaitForVariable(session, request.arguments)
        }
    }

    addTool(
        name = "emulator_wait_until_text",
        description = "Step frames until text appears on screen, or maxFrames is exhausted.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("text") {
                            put("type", "string")
                            put("description", "Text to search for on screen")
                        }
                        putJsonObject("maxFrames") {
                            put("type", "integer")
                            put("description", MAX_FRAMES_DESCRIPTION)
                        }
                    },
                required = listOf("text", "maxFrames"),
            ),
    ) { request ->
        runTool("emulator_wait_until_text") {
            ToolHandlerLogic.handleWaitUntilText(session, request.arguments)
        }
    }

    addTool(
        name = "emulator_read_variable",
        description = "Read the current value of a named game variable.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("name") {
                            put("type", "string")
                            put("description", "Variable name (e.g., 'score', 'lives')")
                        }
                    },
                required = listOf("name"),
            ),
    ) { request ->
        runTool("emulator_read_variable") {
            ToolHandlerLogic.handleReadVariable(session, request.arguments)
        }
    }

    addTool(
        name = "emulator_write_variable",
        description = "Write a value to a named game variable.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
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
        runTool("emulator_write_variable") {
            ToolHandlerLogic.handleWriteVariable(session, request.arguments)
        }
    }

    addTool(
        name = "emulator_screenshot",
        description = "Capture a screenshot of the current game state.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("label") {
                            put("type", "string")
                            put("description", "Label for the screenshot file name")
                        }
                    },
                required = listOf("label"),
            ),
    ) { request ->
        runTool("emulator_screenshot") {
            ToolHandlerLogic.handleScreenshot(session, request.arguments)
        }
    }

    addTool(
        name = "emulator_describe_game",
        description =
            "Get full game metadata: scenes, actors, variables (with semantic categories), " +
                "texts, terminal scenes, per-scene controls, and scene transition graph.",
        inputSchema = ToolSchema(properties = buildJsonObject {}, required = emptyList()),
    ) { _ ->
        runTool("emulator_describe_game") { ToolHandlerLogic.handleDescribeGame(session) }
    }

    addTool(
        name = "emulator_save_state",
        description =
            "Save current emulator state with a label for later restore. " +
                "State is written to build/gbkt/savestates/<label>.gbst.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("label") {
                            put("type", "string")
                            put("description", "Identifier for the savestate (used as file name)")
                        }
                    },
                required = listOf("label"),
            ),
    ) { request ->
        runTool("emulator_save_state") {
            ToolHandlerLogic.handleSaveState(session, request.arguments)
        }
    }

    addTool(
        name = "emulator_load_state",
        description =
            "Load a previously saved emulator state by label. " +
                "Reads from build/gbkt/savestates/<label>.gbst.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("label") {
                            put("type", "string")
                            put("description", "The savestate label used when saving")
                        }
                    },
                required = listOf("label"),
            ),
    ) { request ->
        runTool("emulator_load_state") {
            ToolHandlerLogic.handleLoadState(session, request.arguments)
        }
    }

    addTool(
        name = "emulator_assert",
        description =
            "Batch assert multiple conditions against current game state. " +
                "Types: variable_equals, variable_in_range, scene_is, text_on_screen, " +
                "actor_visible, sprite_count. " +
                "text_on_screen returns x, y, layer when found. " +
                "text_on_screen supports optional scrollAware field (default false) for games " +
                "using BG scroll registers. " +
                "Example: [{\"type\":\"variable_equals\",\"name\":\"p1Score\",\"expected\":\"5\"}]",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
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
        runTool("emulator_assert") { ToolHandlerLogic.handleAssert(session, request.arguments) }
    }

    addTool(
        name = "emulator_get_playbook",
        description =
            "Get the PLAYBOOK.md content for the currently loaded game. " +
                "Returns the file content and path, or null if not found.",
        inputSchema = ToolSchema(properties = buildJsonObject {}, required = emptyList()),
    ) { _ ->
        runTool("emulator_get_playbook") { ToolHandlerLogic.handleGetPlaybook(session) }
    }

    addTool(
        name = "emulator_read_memory",
        description =
            "Read N bytes from a raw Game Boy memory address. Useful for inspecting hardware " +
                "registers (LCDC 0xFF40, BCPD 0xFF69, OCPD 0xFF6B, etc.) or palette RAM via the " +
                "BCPS/BCPD index-data port pair. Maximum count is 256.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("address") {
                            put("type", "string")
                            put(
                                "description",
                                "Raw memory address as hex (e.g. '0xFF40') or decimal",
                            )
                        }
                        putJsonObject("count") {
                            put("type", "integer")
                            put("description", "Number of bytes to read (1..256, default 1)")
                        }
                    },
                required = listOf("address"),
            ),
    ) { request ->
        runTool("emulator_read_memory") {
            ToolHandlerLogic.handleReadMemory(session, request.arguments)
        }
    }

    addTool(
        name = "emulator_write_memory",
        description =
            "Write a single byte to a raw Game Boy memory address. Required for driving the " +
                "BCPS/OCPS index registers (0xFF68/0xFF6A) before reading BCPD/OCPD palette RAM " +
                "via emulator_read_memory. Value is masked to a byte (0..255).",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("address") {
                            put("type", "string")
                            put(
                                "description",
                                "Raw memory address as hex (e.g. '0xFF68') or decimal",
                            )
                        }
                        putJsonObject("value") {
                            put("type", "integer")
                            put(
                                "description",
                                "Byte value to write (0..255); higher bits masked off",
                            )
                        }
                    },
                required = listOf("address", "value"),
            ),
    ) { request ->
        runTool("emulator_write_memory") {
            ToolHandlerLogic.handleWriteMemory(session, request.arguments)
        }
    }

    addTool(
        name = "emulator_list_games",
        description =
            "List all built games found in the project. " +
                "Scans both standalone (build/gbkt/output/) and multi-game (gbkt-examples/) layouts.",
        inputSchema = ToolSchema(properties = buildJsonObject {}, required = emptyList()),
    ) { _ ->
        runTool("emulator_list_games") { ToolHandlerLogic.handleListGames(session) }
    }
}
