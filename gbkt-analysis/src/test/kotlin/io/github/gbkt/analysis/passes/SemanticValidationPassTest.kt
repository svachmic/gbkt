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
import io.github.gbkt.analysis.Severity
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.GBCColor
import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.VariableDef
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SemanticValidationPassTest {

    private val pass = SemanticValidationPass()

    private fun makeContext(game: GameIR): PassContext =
        PassContext(game = game, profile = FakeProfile, config = AnalysisConfig(maxBanks = 2))

    @Test
    fun `valid game passes validation`() {
        val actor = ActorIR(id = "player", position = PositionDef(80, 72))
        val scene = SceneIR(id = "main", actorIds = listOf("player"))
        val game =
            GameIR(
                name = "Test",
                scenes = listOf(scene),
                actors = listOf(actor),
                startScene = "main",
            )

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val errors = result.context.diagnostics.filter { it.severity == Severity.ERROR }
        assertTrue(errors.isEmpty(), "Expected no errors, got: $errors")
    }

    @Test
    fun `dangling actorId in scene fails`() {
        val scene = SceneIR(id = "main", actorIds = listOf("ghost"))
        val game =
            GameIR(name = "Test", scenes = listOf(scene), actors = emptyList(), startScene = "main")

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Failed>(result)
        val errorMessages = result.diagnostics.map { it.message }
        assertTrue(
            errorMessages.any { it.contains("ghost") },
            "Expected error mentioning 'ghost', got: $errorMessages",
        )
    }

    @Test
    fun `dangling startScene fails`() {
        val game = GameIR(name = "Test", scenes = emptyList(), startScene = "missing")

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Failed>(result)
        val errorMessages = result.diagnostics.map { it.message }
        assertTrue(
            errorMessages.any { it.contains("missing") },
            "Expected error mentioning 'missing', got: $errorMessages",
        )
    }

    @Test
    fun `duplicate scene IDs fail`() {
        val scene1 = SceneIR(id = "main")
        val scene2 = SceneIR(id = "main")
        val game = GameIR(name = "Test", scenes = listOf(scene1, scene2))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Failed>(result)
        val errorMessages = result.diagnostics.map { it.message }
        assertTrue(
            errorMessages.any { it.contains("main") },
            "Expected error mentioning duplicate 'main', got: $errorMessages",
        )
    }

    @Test
    fun `duplicate actor IDs fail`() {
        val actor1 = ActorIR(id = "player", position = PositionDef(0, 0))
        val actor2 = ActorIR(id = "player", position = PositionDef(10, 10))
        val game = GameIR(name = "Test", actors = listOf(actor1, actor2))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Failed>(result)
        val errorMessages = result.diagnostics.map { it.message }
        assertTrue(
            errorMessages.any { it.contains("player") },
            "Expected error mentioning duplicate 'player', got: $errorMessages",
        )
    }

    @Test
    fun `empty game passes`() {
        val game = GameIR(name = "Empty")

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val errors = result.context.diagnostics.filter { it.severity == Severity.ERROR }
        assertTrue(errors.isEmpty(), "Expected no errors for empty game, got: $errors")
    }

    @Test
    fun `duplicate variable names fail`() {
        val game =
            GameIR(
                name = "Test",
                variables =
                    listOf(
                        VariableDef("score", VarType.U16, 0),
                        VariableDef("score", VarType.U8, 0),
                    ),
            )

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Failed>(result)
        val errorMessages = result.diagnostics.map { it.message }
        assertTrue(
            errorMessages.any { it.contains("score") },
            "Expected error mentioning duplicate 'score', got: $errorMessages",
        )
    }

    // =========================================================================
    // raw() warning tests
    // =========================================================================

    @Test
    fun `game with no raw() calls has no raw warning`() {
        val scene = SceneIR(id = "main")
        val game = GameIR(name = "Test", scenes = listOf(scene))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val warnings = result.context.diagnostics.filter { it.severity == Severity.WARNING }
        assertTrue(
            warnings.none { it.message.contains("raw()") },
            "Expected no raw() warning, got: $warnings",
        )
    }

    @Test
    fun `game with 2 raw() calls emits WARNING with count`() {
        val scene = SceneIR(id = "main", frameOps = listOf(RawOp("__raw1;"), RawOp("__raw2;")))
        val game = GameIR(name = "Test", scenes = listOf(scene))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val warnings = result.context.diagnostics.filter { it.severity == Severity.WARNING }
        assertTrue(
            warnings.any { it.message.contains("2 raw() calls found") },
            "Expected warning about 2 raw() calls, got: $warnings",
        )
    }

    @Test
    fun `raw() inside nested IfOp is counted`() {
        val nestedRaw = IfOp(condition = Literal(1), then = listOf(RawOp("__nested;")))
        val scene = SceneIR(id = "main", frameOps = listOf(nestedRaw))
        val game = GameIR(name = "Test", scenes = listOf(scene))

        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val warnings = result.context.diagnostics.filter { it.severity == Severity.WARNING }
        assertTrue(
            warnings.any { it.message.contains("raw()") && it.message.contains("1") },
            "Expected warning about nested raw() call, got: $warnings",
        )
    }

    // =========================================================================
    // Palette strict mode tests
    // =========================================================================

    @Test
    fun `palette strict mode off - no precision warning for imprecise color`() {
        val impreciseColor = GBCColor.fromHex(0xFF8844) // all channels have non-zero low 3 bits
        val palette =
            GBCPalette(
                name = "test",
                colors = listOf(GBCColor.WHITE, GBCColor.BLACK, impreciseColor, GBCColor.WHITE),
            )
        val game = GameIR(name = "Test", palettes = listOf(palette))
        // Default config has paletteStrictMode = false
        val result = pass.run(makeContext(game))

        assertIs<PassResult.Success>(result)
        val warnings = result.context.diagnostics.filter { it.severity == Severity.WARNING }
        assertTrue(
            warnings.none { it.id == "ANLZ-06" },
            "Expected no palette precision warning in non-strict mode, got: $warnings",
        )
    }

    @Test
    fun `palette strict mode on - no false positive for WHITE rgb555 color`() {
        // GBCColor stores rgb555. The round-trip rgb555→rgb888→rgb555 is exact for all valid
        // RGB555 values, so no false-positive warnings should be emitted.
        // WHITE (r5=31, g5=31, b5=31) previously triggered a false positive.
        val color = GBCColor(0x7FFF) // r5=31, g5=31, b5=31 — WHITE
        val palette =
            GBCPalette(
                name = "mypal",
                colors = listOf(color, GBCColor.BLACK, GBCColor.BLACK, GBCColor.BLACK),
            )
        val game = GameIR(name = "Test", palettes = listOf(palette))
        val strictConfig = AnalysisConfig(maxBanks = 2, paletteStrictMode = true)
        val context = PassContext(game = game, profile = FakeProfile, config = strictConfig)

        val result = pass.run(context)

        assertIs<PassResult.Success>(result)
        val warnings = result.context.diagnostics.filter { it.id == "ANLZ-06" }
        assertTrue(
            warnings.isEmpty(),
            "Expected no precision warning for WHITE palette (no false positive), got: $warnings",
        )
    }

    @Test
    fun `palette strict mode on - no warning for exact rgb555 aligned colors`() {
        // Colors whose r5<<3 == (r5<<3)|(r5>>2) — only possible when r5>>2 == 0, i.e. r5 < 4
        // r5=0 → r8 = 0, r_simple = 0: equal
        // r5=1 → r8 = (8|0) = 8, r_simple = 8: equal only if 1>>2==0, yes equal
        // So pure black (0,0,0) should produce no warning.
        val exactColor = GBCColor.BLACK // rgb555 = 0x0000, r5=g5=b5=0
        val palette =
            GBCPalette(
                name = "exact",
                colors = listOf(exactColor, exactColor, exactColor, exactColor),
            )
        val game = GameIR(name = "Test", palettes = listOf(palette))
        val strictConfig = AnalysisConfig(maxBanks = 2, paletteStrictMode = true)
        val context = PassContext(game = game, profile = FakeProfile, config = strictConfig)

        val result = pass.run(context)

        assertIs<PassResult.Success>(result)
        val warnings = result.context.diagnostics.filter { it.id == "ANLZ-06" }
        assertTrue(
            warnings.isEmpty(),
            "Expected no precision warning for exact BLACK palette, got: $warnings",
        )
    }
}
