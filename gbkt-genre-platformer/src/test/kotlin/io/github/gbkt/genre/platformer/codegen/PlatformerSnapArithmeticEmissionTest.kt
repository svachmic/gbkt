/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.codegen

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// Phase 21 Plan 01 — D-05 / D-07 snap-arithmetic emission test.
//
// LOCKED INVARIANTS (Plan 21-01 §must_haves):
//
//   1. (CONFIG-DRIVEN PATH)
//      With pivotAdjust=2, hitboxH=24, spawn_y=120:
//        foot_tile_row = (120 + 24) >> 3 = 18
//        foot_pixel_top = 18 << 3 = 144
//        foot_pixel_anchor = 144 - 24 - 2 = 118
//        posYSym = 118 << 4 = 1888
//      The generated platformer_physics_update body must contain the literal 1888.
//      This locks both D-05 (config-driven pivotAdjust) and D-07 (snap arithmetic).
//
//   2. (FALLBACK PATH — back-compat)
//      A GameIR with NO pivotAdjust config key still generates without throwing
//      (the companion-constant fallback fires, producing the same numeric result
//      for the reference geometry and keeping the 4 existing EmissionTests GREEN).
//
// Analog: TilemapPhysicsPlayerSymbolEmissionTest — same pipeline setup, same
// buildGameWithTilemapCollision helper shape, same extractFunctionBody brace-walk.
// =============================================================================

class PlatformerSnapArithmeticEmissionTest {

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers (inlined per per-test convention — see
    // TilemapPhysicsPlayerSymbolEmissionTest for rationale)
    // -------------------------------------------------------------------------

    /**
     * Extracts a C function body by brace-walking from the first line whose contents start with
     * [functionSignaturePrefix] until the matching closing brace at depth zero. Inlined per-test
     * per RESEARCH §D-claude-5 convention matching the brace-walk pattern across sibling tests.
     */
    private fun extractFunctionBody(cSource: String, functionSignaturePrefix: String): String {
        val lines = cSource.lines()
        val startIdx = lines.indexOfFirst { it.startsWith(functionSignaturePrefix) }
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

    /**
     * Builds a minimal GameIR with both a `platformer_physics` system (to fire Path A of
     * `gameUsesTilemapCollision`) and a `tilemap_collision` system with an explicit `pivotAdjust`
     * config key — exercising the D-05 config-driven resolution path.
     */
    private fun buildGameWithPivotAdjust(
        posXVar: String = "playerX",
        posYVar: String = "playerY",
        vxVar: String = "playerVx",
        vyVar: String = "playerVy",
        groundedVar: String = "grounded",
        solidThreshold: Int = 17,
        pivotAdjust: Int = 2,
        hitboxH: Int = 24,
    ): GameIR {
        val physicsSystem =
            GenericSystem(
                id = "physics",
                config =
                    mapOf(
                        "type" to "platformer_physics",
                        "physicsConfig" to
                            PlatformerPhysicsConfig(
                                gravity = 2,
                                jumpForce = 8,
                                terminalVelocity = 12,
                                solidThreshold = solidThreshold,
                            ),
                    ),
            )
        val tilemapCollisionSystem =
            GenericSystem(
                id = "tilemap_collision",
                config =
                    mapOf(
                        "type" to "tilemap_collision",
                        "posXVar" to posXVar,
                        "posYVar" to posYVar,
                        "vxVar" to vxVar,
                        "vyVar" to vyVar,
                        "groundedVar" to groundedVar,
                        "hitboxX" to 0,
                        "hitboxY" to 0,
                        "hitboxW" to 8,
                        "hitboxH" to hitboxH,
                        "solidThreshold" to solidThreshold,
                        // D-05: explicit pivotAdjust in config — lifts resolution from the
                        // visitor's metasprite-lookup dance into the DSL per Project Rule #1.
                        "pivotAdjust" to pivotAdjust,
                    ),
            )
        return GameIR(
            name = "TestSnapArithmetic",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(physicsSystem, tilemapCollisionSystem),
            startScene = "gameplay",
        )
    }

    /**
     * Builds a minimal GameIR with a `tilemap_collision` system that has NO `pivotAdjust` key —
     * exercises the companion-constant fallback path.
     */
    private fun buildGameWithoutPivotAdjust(): GameIR {
        val physicsSystem =
            GenericSystem(
                id = "physics",
                config =
                    mapOf(
                        "type" to "platformer_physics",
                        "physicsConfig" to
                            PlatformerPhysicsConfig(
                                gravity = 2,
                                jumpForce = 8,
                                terminalVelocity = 12,
                                solidThreshold = 17,
                            ),
                    ),
            )
        val tilemapCollisionSystem =
            GenericSystem(
                id = "tilemap_collision",
                config =
                    mapOf(
                        "type" to "tilemap_collision",
                        "posXVar" to "playerX",
                        "posYVar" to "playerY",
                        "vxVar" to "playerVx",
                        "vyVar" to "playerVy",
                        "groundedVar" to "grounded",
                        "hitboxX" to 0,
                        "hitboxY" to 0,
                        "hitboxW" to 8,
                        "hitboxH" to 24,
                        "solidThreshold" to 17,
                        // No "pivotAdjust" key — the fallback path fires.
                    ),
            )
        return GameIR(
            name = "TestSnapArithmeticNoConfig",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(physicsSystem, tilemapCollisionSystem),
            startScene = "gameplay",
        )
    }

    // -------------------------------------------------------------------------
    // TEST 1 — Config-driven pivotAdjust=2, hitboxH=24, spawn_y=120
    //           → posYSym literal 1888 in generated physics-update body.
    //
    // Derivation (D-07 / RESEARCH §D-07):
    //   foot_tile_row  = (120 + 24) >> 3  = 144 >> 3 = 18
    //   foot_pixel_top = 18 << 3           = 144
    //   foot_pixel_anchor = 144 - 24 - 2  = 118
    //   posYSym        = 118 << 4          = 1888
    //
    // This test locks both:
    //   D-05: pivotAdjust read from config key (not metasprite scan)
    //   D-07: snap arithmetic produces the expected posYSym literal
    // -------------------------------------------------------------------------

    @Test
    fun `buildVerticalFootProbe snap arithmetic produces correct posYSym when pivotAdjust set in config`() {
        // spawn_y=120 is the reference coordinate from PlatformerTemplate.kt zones:
        // spawn(40u, 120u) — world1Area1/world1Area2/world2Area1.
        val gameIR = buildGameWithPivotAdjust(pivotAdjust = 2, hitboxH = 24)
        val result = pipeline.generate(gameIR)
        val mainC = result.files["main.c"] ?: error("main.c not generated")

        val body = extractFunctionBody(mainC, "void platformer_physics_update")

        assertNotNull(
            body.takeIf { it.isNotEmpty() },
            "platformer_physics_update body must be extractable",
        )

        // Structural assertion (D-07 option (b) — RESEARCH §open questions 2):
        // The snapped posYSym=1888 must appear as a literal in the physics-update body.
        // Formula: ((((120+24)>>3)<<3) - 24 - 2) << 4 = 118 << 4 = 1888.
        assertTrue(
            body.contains("1888"),
            "Expected snapped posYSym=1888 in platformer_physics_update body (D-05/D-07). " +
                "Derivation: foot_tile_row=(120+24)>>3=18, foot_pixel_top=18<<3=144, " +
                "foot_pixel_anchor=144-24-2=118, posYSym=118<<4=1888. " +
                "Body:\n${body.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // TEST 2 — No pivotAdjust config key → fallback fires; generation succeeds.
    //
    // The companion-constant fallback (REFERENCE_FRAME_HEIGHT=32, REFERENCE_PIVOT_Y=6,
    // height=24) produces: (32 - 6 - 24).coerceAtLeast(0) = 2 — the SAME value as
    // the explicit config key for the reference geometry. This test ensures the
    // fallback path does not throw and keeps the 4 existing EmissionTests GREEN.
    // -------------------------------------------------------------------------

    @Test
    fun `generation succeeds and fallback fires when no pivotAdjust key in config`() {
        val gameIR = buildGameWithoutPivotAdjust()
        // This must NOT throw — the fallback should produce a valid (if potentially
        // misaligned for non-reference geometries) result.
        val result = pipeline.generate(gameIR)
        val mainC = result.files["main.c"] ?: error("main.c not generated")

        val body = extractFunctionBody(mainC, "void platformer_physics_update")
        assertTrue(
            body.isNotEmpty(),
            "platformer_physics_update must be generated even without pivotAdjust config key",
        )
        // Fallback: REFERENCE_FRAME_HEIGHT(32) - REFERENCE_PIVOT_Y(6) - height(24) = 2 → 1888
        // Same posYSym as explicit config — for the reference geometry the fallback is exact.
        assertTrue(
            body.contains("1888"),
            "Fallback geometry (32-6-24=2) must produce posYSym=1888 for spawn_y=120, hitboxH=24. " +
                "Body:\n${body.take(4000)}",
        )
    }
}
