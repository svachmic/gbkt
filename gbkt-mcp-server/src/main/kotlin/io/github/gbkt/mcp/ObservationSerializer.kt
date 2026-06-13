/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.mcp

import io.github.gbkt.emulator.agent.ActorState
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.Observation
import io.github.gbkt.emulator.agent.SpriteEntry
import io.github.gbkt.emulator.debug.DebugLogEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Converts an [Observation] to a [JsonObject] suitable for MCP tool results. */
fun Observation.toJsonObject(): JsonObject {
    val obs = this
    return buildJsonObject {
        put("frame", frame)
        put("scene", scene)
        put("isTerminal", isTerminal)
        put("variables", obs.buildVariablesObject())
        put("sprites", obs.buildSpritesArray())
        put("actors", obs.buildActorsArray())
        put("bgText", obs.buildBgTextArray())
        put("winText", obs.buildWinTextArray())
        put("newLogEntries", obs.buildNewLogEntriesArray())
    }
}

private fun Observation.buildVariablesObject(): JsonObject = buildJsonObject {
    for ((k, v) in variables) put(k, v)
}

private fun Observation.buildSpritesArray(): JsonArray = buildJsonArray {
    for (sprite in sprites) add(sprite.toJsonObject())
}

private fun Observation.buildActorsArray(): JsonArray = buildJsonArray {
    for (actor in actors) add(actor.toJsonObject())
}

private fun Observation.buildBgTextArray(): JsonArray = buildJsonArray {
    for (row in bgText) add(JsonPrimitive(row))
}

private fun Observation.buildWinTextArray(): JsonArray = buildJsonArray {
    for (row in winText) add(JsonPrimitive(row))
}

private fun Observation.buildNewLogEntriesArray(): JsonArray = buildJsonArray {
    for (entry in newLogEntries) add(entry.toJsonObject())
}

private fun SpriteEntry.toJsonObject(): JsonObject = buildJsonObject {
    put("index", index)
    put("screenX", screenX)
    put("screenY", screenY)
    put("tileIndex", tileIndex)
    put("behindBg", behindBg)
    put("yFlip", yFlip)
    put("xFlip", xFlip)
}

private fun ActorState.toJsonObject(): JsonObject = buildJsonObject {
    put("name", name)
    put("x", x)
    put("y", y)
    put("spriteCount", sprites.size)
}

private fun DebugLogEntry.toJsonObject(): JsonObject = buildJsonObject {
    put("timestampMs", timestampMs)
    put("level", level.name)
    put("message", message)
    if (context != null) put("context", context)
}

/** Converts [GameMetadata] to a [JsonObject] for the describe_game tool. */
fun GameMetadata.toJsonObject(): JsonObject {
    val meta = this
    return buildJsonObject {
        put("scenes", meta.buildScenesArray())
        put("actors", meta.buildActorsArray())
        put("variables", meta.buildVariablesArray())
        put("texts", meta.buildTextsArray())
        put("terminalScenes", meta.buildTerminalScenesArray())
        put("controls", meta.buildControlsObject())
        put("transitions", meta.buildTransitionsArray())
    }
}

private fun GameMetadata.buildScenesArray(): JsonArray = buildJsonArray {
    for (name in scenes.sceneNames) add(JsonPrimitive(name))
}

private fun GameMetadata.buildActorsArray(): JsonArray = buildJsonArray {
    for (actor in actors) {
        add(
            buildJsonObject {
                put("name", actor.name)
                put("oamStart", actor.oamStart)
                put("oamCount", actor.oamCount)
                put("spriteWidth", actor.spriteWidth)
                put("spriteHeight", actor.spriteHeight)
                put("xVar", actor.xVar)
                put("yVar", actor.yVar)
            }
        )
    }
}

private fun GameMetadata.buildVariablesArray(): JsonArray = buildJsonArray {
    for (v in variables) {
        add(
            buildJsonObject {
                put("name", v.name)
                put("type", v.type)
                put("semantic", v.semantic)
            }
        )
    }
}

private fun GameMetadata.buildTextsArray(): JsonArray = buildJsonArray {
    for (t in texts) add(JsonPrimitive(t))
}

private fun GameMetadata.buildTerminalScenesArray(): JsonArray = buildJsonArray {
    for (s in terminalScenes) add(JsonPrimitive(s))
}

private fun GameMetadata.buildControlsObject(): JsonObject = buildJsonObject {
    for ((sceneId, mappings) in controls) {
        put(
            sceneId,
            buildJsonArray {
                for (m in mappings) {
                    add(
                        buildJsonObject {
                            put("button", m.button)
                            put("type", m.type)
                        }
                    )
                }
            },
        )
    }
}

private fun GameMetadata.buildTransitionsArray(): JsonArray = buildJsonArray {
    for (t in transitions) {
        add(
            buildJsonObject {
                put("from", t.from)
                put("to", t.to)
            }
        )
    }
}
