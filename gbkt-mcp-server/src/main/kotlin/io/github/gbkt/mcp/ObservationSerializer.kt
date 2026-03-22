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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Converts an [Observation] to a [JsonObject] suitable for MCP tool results. */
fun Observation.toJsonObject(): JsonObject = buildJsonObject {
    put("frame", frame)
    put("scene", scene)
    put("isTerminal", isTerminal)
    put(
        "variables",
        buildJsonObject {
            for ((k, v) in variables) {
                put(k, v)
            }
        },
    )
    put(
        "sprites",
        buildJsonArray {
            for (sprite in sprites) {
                add(sprite.toJsonObject())
            }
        },
    )
    put(
        "actors",
        buildJsonArray {
            for (actor in actors) {
                add(actor.toJsonObject())
            }
        },
    )
    put(
        "bgText",
        buildJsonArray { for (row in bgText) add(kotlinx.serialization.json.JsonPrimitive(row)) },
    )
    put(
        "winText",
        buildJsonArray { for (row in winText) add(kotlinx.serialization.json.JsonPrimitive(row)) },
    )
    put(
        "newLogEntries",
        buildJsonArray {
            for (entry in newLogEntries) {
                add(entry.toJsonObject())
            }
        },
    )
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
fun GameMetadata.toJsonObject(): JsonObject = buildJsonObject {
    put(
        "scenes",
        buildJsonArray {
            for (name in scenes.sceneNames) add(kotlinx.serialization.json.JsonPrimitive(name))
        },
    )
    put(
        "actors",
        buildJsonArray {
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
        },
    )
    put(
        "variables",
        buildJsonArray {
            for (v in variables) {
                add(
                    buildJsonObject {
                        put("name", v.name)
                        put("type", v.type)
                        put("semantic", v.semantic)
                    }
                )
            }
        },
    )
    put(
        "texts",
        buildJsonArray { for (t in texts) add(kotlinx.serialization.json.JsonPrimitive(t)) },
    )
    put(
        "terminalScenes",
        buildJsonArray {
            for (s in terminalScenes) add(kotlinx.serialization.json.JsonPrimitive(s))
        },
    )
    put(
        "controls",
        buildJsonObject {
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
        },
    )
    put(
        "transitions",
        buildJsonArray {
            for (t in transitions) {
                add(
                    buildJsonObject {
                        put("from", t.from)
                        put("to", t.to)
                    }
                )
            }
        },
    )
}
