/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.mcp

import io.github.gbkt.emulator.LogLevel
import io.github.gbkt.emulator.agent.ActorState
import io.github.gbkt.emulator.agent.ControlMapping
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.Observation
import io.github.gbkt.emulator.agent.SceneMap
import io.github.gbkt.emulator.agent.SpriteEntry
import io.github.gbkt.emulator.agent.TransitionEdgeMeta
import io.github.gbkt.emulator.agent.VariableDef
import io.github.gbkt.emulator.debug.DebugLogEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ObservationSerializerTest {

    private fun emptyRows(count: Int = 18): List<String> = List(count) { "                    " }

    @Test
    fun `empty observation serializes correctly`() {
        val obs =
            Observation(
                frame = 0,
                variables = emptyMap(),
                scene = null,
                sprites = emptyList(),
                actors = emptyList(),
                bgText = emptyRows(),
                winText = emptyRows(),
                newLogEntries = emptyList(),
            )
        val json = obs.toJsonObject()

        assertEquals(0, json["frame"]?.jsonPrimitive?.int)
        assertTrue(
            json["scene"]?.jsonPrimitive?.content == "null" || json["scene"].toString() == "null"
        )
        assertEquals(0, json["sprites"]?.jsonArray?.size)
        assertEquals(0, json["actors"]?.jsonArray?.size)
        assertEquals(18, json["bgText"]?.jsonArray?.size)
        assertEquals(0, json["newLogEntries"]?.jsonArray?.size)
        assertEquals(false, json["isTerminal"]?.jsonPrimitive?.content?.toBooleanStrictOrNull())
    }

    @Test
    fun `full observation with all fields round-trips`() {
        val obs =
            Observation(
                frame = 42,
                variables = mapOf("score" to 10, "lives" to 3),
                scene = "gameplay",
                sprites =
                    listOf(
                        SpriteEntry(
                            index = 0,
                            screenX = 80,
                            screenY = 72,
                            rawX = 88,
                            rawY = 88,
                            tileIndex = 5,
                            behindBg = false,
                            yFlip = false,
                            xFlip = false,
                            dmgPalette = 0,
                            gbcVramBank = 0,
                            gbcPalette = 0,
                            rawAttributes = 0,
                        )
                    ),
                actors = listOf(ActorState("ball", x = 80, y = 72, sprites = emptyList())),
                bgText = emptyRows(),
                winText = emptyRows(),
                newLogEntries =
                    listOf(
                        DebugLogEntry(
                            timestampMs = 100,
                            level = LogLevel.GAME,
                            message = "test",
                            context = "ctx",
                        )
                    ),
                isTerminal = true,
            )

        val json = obs.toJsonObject()

        assertEquals(42, json["frame"]?.jsonPrimitive?.int)
        assertEquals("gameplay", json["scene"]?.jsonPrimitive?.content)
        assertEquals(true, json["isTerminal"]?.jsonPrimitive?.content?.toBooleanStrictOrNull())
        assertEquals(10, json["variables"]?.jsonObject?.get("score")?.jsonPrimitive?.int)
        assertEquals(3, json["variables"]?.jsonObject?.get("lives")?.jsonPrimitive?.int)

        val sprite = json["sprites"]?.jsonArray?.get(0)?.jsonObject
        assertEquals(0, sprite?.get("index")?.jsonPrimitive?.int)
        assertEquals(80, sprite?.get("screenX")?.jsonPrimitive?.int)
        assertEquals(5, sprite?.get("tileIndex")?.jsonPrimitive?.int)

        val actor = json["actors"]?.jsonArray?.get(0)?.jsonObject
        assertEquals("ball", actor?.get("name")?.jsonPrimitive?.content)
        assertEquals(80, actor?.get("x")?.jsonPrimitive?.int)

        val log = json["newLogEntries"]?.jsonArray?.get(0)?.jsonObject
        assertEquals(100, log?.get("timestampMs")?.jsonPrimitive?.int)
        assertEquals("GAME", log?.get("level")?.jsonPrimitive?.content)
        assertEquals("test", log?.get("message")?.jsonPrimitive?.content)
        assertEquals("ctx", log?.get("context")?.jsonPrimitive?.content)
    }

    @Test
    fun `null scene serialized as null in JSON`() {
        val obs =
            Observation(
                frame = 1,
                variables = emptyMap(),
                scene = null,
                sprites = emptyList(),
                actors = emptyList(),
                bgText = emptyRows(),
                winText = emptyRows(),
                newLogEntries = emptyList(),
            )
        val json = obs.toJsonObject()

        // scene should be JSON null
        assertTrue(json["scene"].toString() == "null")
    }

    @Test
    fun `actors with null x and y serialize as null`() {
        val obs =
            Observation(
                frame = 1,
                variables = emptyMap(),
                scene = null,
                sprites = emptyList(),
                actors = listOf(ActorState("npc", x = null, y = null, sprites = emptyList())),
                bgText = emptyRows(),
                winText = emptyRows(),
                newLogEntries = emptyList(),
            )
        val json = obs.toJsonObject()
        val actor = json["actors"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("npc", actor?.get("name")?.jsonPrimitive?.content)
        assertTrue(actor?.get("x").toString() == "null")
        assertTrue(actor?.get("y").toString() == "null")
    }

    @Test
    fun `GameMetadata toJsonObject includes all fields`() {
        val meta =
            GameMetadata.of(
                scenes = SceneMap(mapOf("title" to 0, "game" to 1)),
                actors = emptyList(),
                variables = listOf(VariableDef("score", "U8", "score")),
                texts = listOf("HELLO"),
                terminalScenes = setOf("gameover"),
                controls = mapOf("game" to listOf(ControlMapping("UP", "held"))),
                transitions = listOf(TransitionEdgeMeta("title", "game")),
            )
        val json = meta.toJsonObject()

        assertEquals(2, json["scenes"]?.jsonArray?.size)
        assertEquals(0, json["actors"]?.jsonArray?.size)
        assertEquals(1, json["variables"]?.jsonArray?.size)
        assertEquals(
            "score",
            json["variables"]?.jsonArray?.get(0)?.jsonObject?.get("name")?.jsonPrimitive?.content,
        )
        assertEquals(
            "score",
            json["variables"]
                ?.jsonArray
                ?.get(0)
                ?.jsonObject
                ?.get("semantic")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(1, json["texts"]?.jsonArray?.size)
        assertEquals("HELLO", json["texts"]?.jsonArray?.get(0)?.jsonPrimitive?.content)
        assertEquals(1, json["terminalScenes"]?.jsonArray?.size)
        assertEquals("gameover", json["terminalScenes"]?.jsonArray?.get(0)?.jsonPrimitive?.content)
        // Controls
        val controlsObj = json["controls"]!!.jsonObject
        val gameControls = controlsObj["game"]!!.jsonArray
        assertEquals(1, gameControls.size)
        assertEquals("UP", gameControls[0].jsonObject["button"]?.jsonPrimitive?.content)
        assertEquals("held", gameControls[0].jsonObject["type"]?.jsonPrimitive?.content)
        // Transitions
        val transArr = json["transitions"]!!.jsonArray
        assertEquals(1, transArr.size)
        assertEquals("title", transArr[0].jsonObject["from"]?.jsonPrimitive?.content)
        assertEquals("game", transArr[0].jsonObject["to"]?.jsonPrimitive?.content)
    }
}
