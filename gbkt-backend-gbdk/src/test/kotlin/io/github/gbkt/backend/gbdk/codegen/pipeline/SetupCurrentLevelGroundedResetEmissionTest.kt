/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertTrue

// =============================================================================
// Phase 12.9 D4 — setup_current_level resets grounded on every level switch
//
// Root cause (Plan 12.9-08e Diagnose REFINEMENT): setup_current_level() resets
// _playerVy = 0 per level but NOT the grounded symbol. On the second (and any
// subsequent) level switch, grounded carries 1 from the prior level's level-end
// trigger (which requires grounded == 1). With grounded == 1, gravity is
// suppressed and the vertical collision snap never fires → the player freezes
// at raw spawn y without settling to ground. Level 1 works only because grounded
// inits to 0 at boot.
//
// Fix (D4): in buildSetupCurrentLevelFunctionIfNeeded (~line 2529), reset the
// grounded symbol to 0 alongside the existing $vySym = 0 reset. Resolve the
// grounded symbol from the tilemap_collision GenericSystem config (look for a
// "groundedVar" key, defaulting to "grounded" → generated symbol "_grounded").
// Mirrors the posXSym/vySym resolution already present in this function.
//
// Per feedback_no_magic_strings: resolve from config, never hardcode.
//
// RED against HEAD (no grounded reset in setup_current_level). GREEN after D4.
//
// Scope-level grep gate (CLAUDE.md § Scope-level grep gates): extracts the
// setup_current_level() body via brace-walk so the grounded = 0 assertion
// fires INSIDE the function, not matching a coincidental occurrence elsewhere.
// =============================================================================

private fun extractSetupCurrentLevelBodyD4(source: String): String? {
    val signature = "void setup_current_level(void)"
    val sigIdx = source.indexOf(signature)
    if (sigIdx == -1) return null
    val openIdx = source.indexOf('{', sigIdx + signature.length)
    if (openIdx == -1) return null
    var depth = 0
    var i = openIdx
    while (i < source.length) {
        when (source[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return source.substring(openIdx + 1, i)
            }
        }
        i++
    }
    return null
}

// ---------------------------------------------------------------------------
// Game IR fixture — mirrors SetupCurrentLevelPaletteEmissionTest but with
// an explicit tilemap_collision GenericSystem that binds the grounded variable
// name via "groundedVar" config key (mirrors PlatformerVisitor.kt:579).
// ---------------------------------------------------------------------------

private fun buildGroundedResetGameIR() =
    GameIR(
        name = "SetupCurrentLevelGroundedResetTest",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 4),
        scenes = listOf(
            SceneIR(id = "title"),
            SceneIR(id = "gameplay"),
        ),
        zones = listOf(
            ZoneIR(
                id = "world1Area1Zone",
                name = "World 1 Area 1",
                tilesetPath = "tiles/world1.png",
                mapWidth = 60,
                mapHeight = 18,
            )
        ),
        systems = listOf(
            GenericSystem(
                id = "tilemapCollision",
                config = mapOf(
                    "type" to "tilemap_collision",
                    // Bind symbol names — mirrors PlatformerTemplate.kt:172-178
                    "posXVar" to "playerX",
                    "posYVar" to "playerY",
                    "vxVar" to "playerVx",
                    "vyVar" to "playerVy",
                    "groundedVar" to "grounded",
                ),
            ),
        ),
        startScene = "title",
    )

class SetupCurrentLevelGroundedResetEmissionTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // setup_current_level() per-zone case resets grounded symbol to 0
    //
    // RED TODAY: no grounded reset in the template.
    // GREEN after D4 fix adds $groundedSym = 0 alongside $vySym = 0.
    // =========================================================================

    @Test
    fun `setup_current_level resets grounded to 0 alongside vy reset (D4)`() {
        val gameIR = buildGroundedResetGameIR()
        val allFiles = pipeline.generate(gameIR).files
        val mainC = allFiles["main.c"] ?: error("main.c not generated. Files: ${allFiles.keys}")

        val body = extractSetupCurrentLevelBodyD4(mainC)
            ?: error("Could not extract setup_current_level() body from main.c")

        // D4 positive: grounded symbol reset to 0 in each case body.
        // The symbol name is resolved from config "groundedVar" → "grounded" → "_grounded".
        // Per feedback_no_magic_strings: resolve from config, never hardcode.
        assertTrue(
            body.contains("_grounded = 0"),
            "setup_current_level body must contain grounded reset `_grounded = 0` in each case " +
                "(D4 fix — without it grounded carries 1 from prior level, suppressing gravity " +
                "and preventing ground snap on level-2+ entry). body:\n$body"
        )

        // D4 ordering: grounded reset must appear AFTER the vy reset (both are velocity resets;
        // ordering matches the posX/posY/vx/vy declaration order in PlatformerTemplate.kt).
        val vyIdx = body.indexOf("_playerVy = 0")
        val groundedIdx = body.indexOf("_grounded = 0")
        assertTrue(
            vyIdx >= 0 && groundedIdx > vyIdx,
            "grounded reset must appear AFTER vy reset in the case body (D4 ordering contract). " +
                "vyIdx=$vyIdx groundedIdx=$groundedIdx. body:\n$body"
        )
    }
}
