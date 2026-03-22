/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.FakeProfile
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.ResourceInventory
import io.github.gbkt.analysis.Severity
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SizeDef
import io.github.gbkt.core.ir.SpriteDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OAMAllocationPassTest {

    private val pass = OAMAllocationPass()

    private fun makeSprite(): SpriteDef =
        SpriteDef(assetRef = AssetRef("sprite.png", AssetType.SPRITE), size = SizeDef(8, 8))

    private fun makeActorWithSprite(id: String): ActorIR =
        ActorIR(id = id, position = PositionDef(0, 0), sprite = makeSprite())

    private fun makeActorWithoutSprite(id: String): ActorIR =
        ActorIR(id = id, position = PositionDef(0, 0), sprite = null)

    private fun makeContext(
        game: GameIR,
        config: AnalysisConfig = AnalysisConfig(maxBanks = 2),
    ): PassContext {
        val inventory =
            ResourceInventory(totalActors = game.actors.size, totalScenes = game.scenes.size)
        return PassContext(
            game = game,
            profile = FakeProfile,
            config = config,
            inventory = inventory,
        )
    }

    // -------------------------------------------------------------------------
    // Slot assignment
    // -------------------------------------------------------------------------

    @Test
    fun `actors with sprites get sequential OAM slots`() {
        val a1 = makeActorWithSprite("actor1")
        val a2 = makeActorWithSprite("actor2")
        val a3 = makeActorWithSprite("actor3")
        val game = GameIR(name = "Test", actors = listOf(a1, a2, a3))
        val ctx = makeContext(game)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val oam = result.context.oamAssignments
        assertEquals(0, oam["actor1"]?.slot)
        assertEquals(1, oam["actor2"]?.slot)
        assertEquals(2, oam["actor3"]?.slot)
    }

    @Test
    fun `actors without sprites get no OAM slot`() {
        val a1 = makeActorWithSprite("withSprite")
        val a2 = makeActorWithoutSprite("noSprite")
        val game = GameIR(name = "Test", actors = listOf(a1, a2))
        val ctx = makeContext(game)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val oam = result.context.oamAssignments
        assertTrue(oam.containsKey("withSprite"), "Actor with sprite should have OAM slot")
        assertTrue(!oam.containsKey("noSprite"), "Actor without sprite should have no OAM slot")
    }

    // -------------------------------------------------------------------------
    // Overflow detection
    // -------------------------------------------------------------------------

    @Test
    fun `exceeding 40 sprites fails with error`() {
        // 41 sprite-bearing actors → error (maxSprites=40 from FakeProfile)
        val actors = (1..41).map { i -> makeActorWithSprite("actor$i") }
        val game = GameIR(name = "Test", actors = actors)
        // oamErrorThreshold = 41 means >40 is an error
        val config = AnalysisConfig(maxBanks = 2, oamErrorThreshold = 41)
        val ctx = makeContext(game, config = config)

        val result = pass.run(ctx)

        assertIs<PassResult.Failed>(result)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            error.message.contains("41") || error.message.contains("40"),
            "Error message should reference sprite count but was: ${error.message}",
        )
    }

    @Test
    fun `near-limit warning at threshold`() {
        // 36 sprite actors with oamWarningThreshold=35 → should produce a WARNING but not fail
        val actors = (1..36).map { i -> makeActorWithSprite("actor$i") }
        val game = GameIR(name = "Test", actors = actors)
        val config = AnalysisConfig(maxBanks = 2, oamWarningThreshold = 35, oamErrorThreshold = 41)
        val ctx = makeContext(game, config = config)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val warnings = result.context.diagnostics.filter { it.severity == Severity.WARNING }
        assertTrue(
            warnings.isNotEmpty(),
            "Expected WARNING for 36 sprites > threshold 35 but got: ${result.context.diagnostics}",
        )
    }

    // -------------------------------------------------------------------------
    // Scanline density advisory
    // -------------------------------------------------------------------------

    @Test
    fun `scanline density advisory warning`() {
        // Scene with 12 sprite-bearing actors → advisory WARNING (not an error)
        val actors = (1..12).map { i -> makeActorWithSprite("actor$i") }
        val actorIds = actors.map { it.id }
        val scene = SceneIR(id = "crowded", actorIds = actorIds)
        val game = GameIR(name = "Test", actors = actors, scenes = listOf(scene))
        val ctx = makeContext(game)

        val result = pass.run(ctx)

        // Must succeed — scanline density is advisory only (per plan requirement)
        assertIs<PassResult.Success>(result)
        val warnings = result.context.diagnostics.filter { it.severity == Severity.WARNING }
        assertTrue(
            warnings.any { it.message.contains("crowded") && it.message.contains("12") },
            "Expected scanline density warning mentioning scene and count but got: $warnings",
        )
        // Verify it is not an error
        assertTrue(
            result.context.diagnostics.none { it.severity == Severity.ERROR },
            "Scanline density should be advisory WARNING, not ERROR",
        )
    }

    // -------------------------------------------------------------------------
    // Empty game
    // -------------------------------------------------------------------------

    @Test
    fun `empty game passes with no assignments`() {
        val game = GameIR(name = "Empty")
        val ctx = makeContext(game)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        assertTrue(
            result.context.oamAssignments.isEmpty(),
            "Empty game should have no OAM assignments",
        )
        assertTrue(result.context.diagnostics.isEmpty(), "Empty game should produce no diagnostics")
    }
}
