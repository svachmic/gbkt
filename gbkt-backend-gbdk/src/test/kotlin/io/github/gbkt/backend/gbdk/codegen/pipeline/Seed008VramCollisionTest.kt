/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SizeDef
import io.github.gbkt.core.ir.SpriteDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// SEED-008 / CR-01 — VRAM tile-slot collision when actor sprites AND
// metasprites coexist in the same game.
//
// Pre-fix root cause (GBDKPipeline.kt:3673-3724):
//   buildSpriteDataLoadStatements()       — var nextTile = 0  → set_sprite_data(0u, N, ...)
//   buildMetaspriteTileDataLoadStatements()— var nextTile = 0  → set_sprite_data(0u, M, ...)
// Both counters live in their own scope. Concatenated into main() body via
// buildMainFunction(). When an actor (2 tiles) + a metasprite (48 tiles)
// coexist, the metasprite's set_sprite_data(0u, 48u, …) silently OVERWRITES
// the actor's tiles at VRAM slots 0..1. Latent in the Phase 10 metasprites
// example (no actor); guaranteed to surface in Phase 12 (platformer_template
// — actors + metasprites + tilemap together).
//
// Fix (D-08 Route B): replace both methods with a single
// buildAllSpriteDataLoadStatements(gameIR) that instantiates ONE
// VramAllocator and iterates actors FIRST then metasprites (Pitfall 8
// mitigation — preserves Pong/Breakout/SimplePhysics emission order).
//
// Test shape: build a GameIR with 1 actor (sprite 8x16 = 2 tiles) + 1
// metasprite (1 frame, max tileId = 47 → 48 tiles). Run the full pipeline.
// Assert main() body contains BOTH set_sprite_data(0u, 2u, …) (actor) AND
// set_sprite_data(2u, 48u, …) (metasprite continuing from where actor left
// off). NOT two set_sprite_data(0u, …) calls.
// =============================================================================

// -------------------------------------------------------------------------
// Brace-walk helper (copied verbatim from MetaspriteEmissionTest.kt:81-101
// per PATTERNS.md line 552-569 — scope-level grep gate)
// -------------------------------------------------------------------------

private fun extractFunctionBody(cSource: String, functionName: String): String {
    val lines = cSource.lines()
    val startIdx = lines.indexOfFirst { it.contains("void $functionName(") }
    if (startIdx == -1) return ""
    val body = StringBuilder()
    var depth = 0
    var started = false
    for (i in startIdx until lines.size) {
        val line = lines[i]
        body.appendLine(line)
        for (ch in line) {
            if (ch == '{') {
                depth++
                started = true
            }
            if (ch == '}') depth--
        }
        if (started && depth == 0) break
    }
    return body.toString()
}

// -------------------------------------------------------------------------
// Minimal IR builder: 1 actor (player, 8x16 sprite → 2 tiles) + 1 metasprite
// (elephant, 1 frame, max tileId = 47 → 48 tiles).
// Array names derived per pipeline conventions:
//   - actor sprite asset "sprites/player.png" → "sprites_player_tiles"
//   - metasprite id "elephant"                → "elephant_tiles"
// -------------------------------------------------------------------------

private fun buildActorPlusMetaspriteGame(): GameIR {
    val playerActor =
        ActorIR(
            id = "player",
            position = PositionDef(x = 80, y = 72),
            sprite =
                SpriteDef(
                    assetRef = AssetRef("sprites/player.png", AssetType.GENERIC),
                    size = SizeDef(width = 8, height = 16),
                ),
        )

    // Metasprite with a single frame whose tile IDs span 0..47 (48 tiles).
    val elephantMetasprite =
        MetaspriteIR(
            id = "elephant",
            frames =
                listOf(
                    MetaspriteFrame(
                        tiles =
                            (0..47).map { tid -> MetaspriteTile(relX = 0, relY = 0, tileId = tid) }
                    )
                ),
        )

    return GameIR(
        name = "Seed008Game",
        scenes = listOf(SceneIR(id = "play")),
        actors = listOf(playerActor),
        metasprites = listOf(elephantMetasprite),
        startScene = "play",
    )
}

// =============================================================================
// TEST CLASS
// =============================================================================

class Seed008VramCollisionTest {

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Test 1 — distinct (non-colliding) start offsets in the two
    // set_sprite_data calls
    // -------------------------------------------------------------------------
    @Test
    fun main_c_actor_and_metasprite_set_sprite_data_use_distinct_start_offsets() {
        val gameIR = buildActorPlusMetaspriteGame()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"]
        assertNotNull(mainC, "main.c was not generated. Files generated: ${output.files.keys}")

        val mainBody = extractFunctionBody(mainC, "main")
        assertTrue(
            mainBody.isNotEmpty(),
            "Could not extract main() body from main.c. " + "main.c head:\n${mainC.take(1000)}",
        )

        // Actor sprite tiles must load FIRST at VRAM slot 0, occupying 2 slots.
        assertTrue(
            mainBody.contains("set_sprite_data(0u, 2u, sprites_player_tiles)"),
            "Expected actor set_sprite_data(0u, 2u, sprites_player_tiles) — " +
                "actor sprite (8x16 = 2 tiles) must load at VRAM slot 0. " +
                "main() body set_sprite_data calls:\n" +
                mainBody.lines().filter { it.contains("set_sprite_data") }.joinToString("\n"),
        )

        // Metasprite tiles must start at slot 2 (immediately after the actor's
        // 2 tiles) — NOT collide back to slot 0. This is the CR-01 fix.
        assertTrue(
            mainBody.contains("set_sprite_data(2u, 48u, elephant_tiles)"),
            "Expected metasprite set_sprite_data(2u, 48u, elephant_tiles) — " +
                "metasprite (48 tiles) must continue from VRAM slot 2 (where the actor " +
                "left off) NOT from 0 (which would silently overwrite the actor's tiles). " +
                "This is the CR-01 fix. main() body set_sprite_data calls:\n" +
                mainBody.lines().filter { it.contains("set_sprite_data") }.joinToString("\n"),
        )

        // Critical negative assertion: there must be ZERO set_sprite_data(0u, 48u, …)
        // calls — that shape is the EXACT pre-fix collision (metasprite starting
        // at 0 with 48 tiles, overwriting the actor).
        val collisionCount = mainBody.lines().count { it.contains("set_sprite_data(0u, 48u,") }
        assertEquals(
            0,
            collisionCount,
            "Found $collisionCount instance(s) of set_sprite_data(0u, 48u, …) — " +
                "the CR-01 collision pattern. The metasprite must NOT start at VRAM " +
                "slot 0 when an actor occupies it. main() body set_sprite_data calls:\n" +
                mainBody.lines().filter { it.contains("set_sprite_data") }.joinToString("\n"),
        )
    }

    // -------------------------------------------------------------------------
    // Test 2 — actors emit FIRST then metasprites (Pitfall 8 ordering)
    // -------------------------------------------------------------------------
    @Test
    fun main_c_set_sprite_data_calls_are_actors_first_then_metasprites() {
        val gameIR = buildActorPlusMetaspriteGame()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"]
        assertNotNull(mainC, "main.c was not generated. Files generated: ${output.files.keys}")

        val mainBody = extractFunctionBody(mainC, "main")
        assertTrue(
            mainBody.isNotEmpty(),
            "Could not extract main() body from main.c. " + "main.c head:\n${mainC.take(1000)}",
        )

        val actorOffset = mainBody.indexOf("set_sprite_data(0u, 2u, sprites_player_tiles)")
        val metaspriteOffset = mainBody.indexOf("set_sprite_data(2u, 48u, elephant_tiles)")

        assertTrue(
            actorOffset >= 0,
            "Actor set_sprite_data call missing — see Test 1 for details. " +
                "main() body:\n${mainBody.take(2000)}",
        )
        assertTrue(
            metaspriteOffset >= 0,
            "Metasprite set_sprite_data call missing — see Test 1 for details. " +
                "main() body:\n${mainBody.take(2000)}",
        )

        assertTrue(
            actorOffset < metaspriteOffset,
            "Pitfall 8 mitigation: actor set_sprite_data(0u, 2u, …) MUST be emitted " +
                "BEFORE metasprite set_sprite_data(2u, 48u, …). Found actor offset " +
                "$actorOffset, metasprite offset $metaspriteOffset. Actor-first emission " +
                "order preserves Pong/Breakout/SimplePhysics shape (no actor games regress " +
                "even though the loader is now unified).",
        )
    }
}
