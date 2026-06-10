/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.report

import io.github.gbkt.analysis.Diagnostic
import io.github.gbkt.analysis.FakeProfile
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.Severity
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.BankSlot
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.OAMSlot
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SizeDef
import io.github.gbkt.core.ir.SpriteDef
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetReporterTest {

    private fun baseContext(game: GameIR = GameIR(name = "TestGame")): PassContext =
        PassContext(game = game, profile = FakeProfile, config = AnalysisConfig(maxBanks = 2))

    // -------------------------------------------------------------------------
    // Non-empty output
    // -------------------------------------------------------------------------

    @Test
    fun `formatReport produces non-empty string`() {
        val ctx = baseContext()
        val report = BudgetReporter.formatReport(ctx)
        assertTrue(report.isNotEmpty(), "Budget report should not be empty")
    }

    // -------------------------------------------------------------------------
    // Bank section fill bars (new percentage bar format)
    // -------------------------------------------------------------------------

    @Test
    fun `bank section shows percentage bar format`() {
        val game = GameIR(name = "BankTest")
        val bankAssignments = (1..8).associate { i -> "scene$i" to BankSlot(1) }
        val ctx = baseContext(game).copy(bankAssignments = bankAssignments)

        // Use ansiEnabled=false for plain text assertion
        val report = BudgetReporter.formatReport(ctx, ansiEnabled = false)

        // Report should contain the bank label
        assertContains(report, "Bank 1:")
        // New bar format uses [===---] style
        assertTrue(report.contains("["), "Bank fill bar should use [ bracket format")
        assertTrue(report.contains("]"), "Bank fill bar should use ] bracket format")
        assertTrue(report.contains("="), "Bank fill bar should contain '=' characters for fill")
    }

    @Test
    fun `bank section bar format has correct structure`() {
        val game = GameIR(name = "BankTest")
        val bankAssignments = (1..8).associate { i -> "scene$i" to BankSlot(1) }
        val ctx = baseContext(game).copy(bankAssignments = bankAssignments)

        val report = BudgetReporter.formatReport(ctx, ansiEnabled = false)

        // Bar should be in [===---] format
        assertContains(report, "[")
        assertContains(report, "]")
        // Should include percentage
        assertTrue(report.contains("%"), "Bank bar line should include percentage")
        // Should include KB usage
        assertTrue(report.contains("KB"), "Bank bar line should include KB values")
    }

    // -------------------------------------------------------------------------
    // ANSI color output
    // -------------------------------------------------------------------------

    @Test
    fun `ansi enabled report contains ANSI escape codes`() {
        val game = GameIR(name = "AnsiTest")
        val ctx = baseContext(game)

        val report = BudgetReporter.formatReport(ctx, ansiEnabled = true)

        // ANSI codes should be present — specifically the reset code \u001B[0m
        assertTrue(
            report.contains("\u001B["),
            "ANSI-enabled report should contain ANSI escape codes",
        )
    }

    @Test
    fun `ansi disabled report contains no escape codes`() {
        val game = GameIR(name = "AnsiTest")
        val ctx = baseContext(game)

        val report = BudgetReporter.formatReport(ctx, ansiEnabled = false)

        assertFalse(
            report.contains("\u001B["),
            "ANSI-disabled report should not contain ANSI escape codes",
        )
    }

    @Test
    fun `green ANSI code appears for low usage`() {
        // OAM: 1/40 = 2% — should be green
        val actors =
            listOf(
                ActorIR(
                    id = "actor1",
                    position = PositionDef(0, 0),
                    sprite = SpriteDef(AssetRef("sprite.png", AssetType.SPRITE), SizeDef(8, 8)),
                )
            )
        val game = GameIR(name = "GreenTest", actors = actors)
        val oamAssignments = mapOf("actor1" to OAMSlot(0))
        val ctx = baseContext(game).copy(oamAssignments = oamAssignments)

        val report = BudgetReporter.formatReport(ctx, ansiEnabled = true)

        // Green code \u001B[32m should appear (2% usage is green)
        assertTrue(report.contains("\u001B[32m"), "Low-usage values should be colored green")
    }

    // -------------------------------------------------------------------------
    // Per-scene breakdown section
    // -------------------------------------------------------------------------

    @Test
    fun `per-scene breakdown section is present`() {
        val scene = SceneIR(id = "gameplay")
        val game = GameIR(name = "BreakdownTest", scenes = listOf(scene))
        val ctx = baseContext(game)

        val report = BudgetReporter.formatReport(ctx, ansiEnabled = false)

        assertContains(report, "Per-scene breakdown")
    }

    @Test
    fun `per-scene breakdown shows scene name`() {
        val scene = SceneIR(id = "dungeon")
        val game = GameIR(name = "BreakdownTest", scenes = listOf(scene))
        val bankAssignments = mapOf("dungeon" to BankSlot(1))
        val ctx = baseContext(game).copy(bankAssignments = bankAssignments)

        val report = BudgetReporter.formatReport(ctx, ansiEnabled = false)

        assertContains(report, "dungeon")
        assertContains(report, "Bank Fill")
    }

    @Test
    fun `per-scene breakdown shows bank assignment`() {
        val scene = SceneIR(id = "battle")
        val game = GameIR(name = "SceneTest", scenes = listOf(scene))
        val bankAssignments = mapOf("battle" to BankSlot(1))
        val ctx = baseContext(game).copy(bankAssignments = bankAssignments)

        val report = BudgetReporter.formatReport(ctx, ansiEnabled = false)

        // Should show bank number 1 for the battle scene
        assertContains(report, "battle")
        assertContains(report, "Est. Size")
    }

    @Test
    fun `ansi breakdown line aligns with plain breakdown line`() {
        val scene = SceneIR(id = "gameplay")
        val game = GameIR(name = "AlignTest", scenes = listOf(scene))
        val bankAssignments = mapOf("gameplay" to BankSlot(1))
        val ctx = baseContext(game).copy(bankAssignments = bankAssignments)

        fun breakdownLine(report: String): String =
            report.substringAfter("Per-scene breakdown").substringBefore("VRAM").lines().single {
                it.contains("gameplay") && it.contains("|")
            }

        val plainLine = breakdownLine(BudgetReporter.formatReport(ctx, ansiEnabled = false))
        val ansiLine = breakdownLine(BudgetReporter.formatReport(ctx, ansiEnabled = true))

        // The fill column must be padded BEFORE colorizing — stripping the ANSI
        // codes from the colored line must yield exactly the plain-text line.
        val stripped = ansiLine.replace(Regex("\u001B\\[[0-9;]*m"), "")
        assertEquals(plainLine, stripped)
    }

    // -------------------------------------------------------------------------
    // Overall ROM size estimate header
    // -------------------------------------------------------------------------

    @Test
    fun `report shows overall ROM size estimate`() {
        val ctx = baseContext()
        val report = BudgetReporter.formatReport(ctx, ansiEnabled = false)
        assertContains(report, "Overall ROM")
    }

    // -------------------------------------------------------------------------
    // VRAM table headers
    // -------------------------------------------------------------------------

    @Test
    fun `VRAM table has correct column headers`() {
        val scene = SceneIR(id = "gameplay")
        val game = GameIR(name = "VramTest", scenes = listOf(scene))
        val ctx = baseContext(game)

        val report = BudgetReporter.formatReport(ctx)

        assertContains(report, "Scene")
        assertContains(report, "Sprite")
        assertContains(report, "BG Avail")
        assertContains(report, "BG Used")
    }

    // -------------------------------------------------------------------------
    // OAM line formatting
    // -------------------------------------------------------------------------

    @Test
    fun `OAM line shows used slash max`() {
        // 6 OAM assignments
        val actors =
            (1..6).map { i ->
                ActorIR(
                    id = "actor$i",
                    position = PositionDef(0, 0),
                    sprite = SpriteDef(AssetRef("sprite.png", AssetType.SPRITE), SizeDef(8, 8)),
                )
            }
        val game = GameIR(name = "OamTest", actors = actors)
        val oamAssignments = actors.mapIndexed { idx, a -> a.id to OAMSlot(idx) }.toMap()
        val ctx = baseContext(game).copy(oamAssignments = oamAssignments)

        // Use ansiEnabled=false so we can do a plain-text assertContains
        val report = BudgetReporter.formatReport(ctx, ansiEnabled = false)

        // Should contain "OAM Sprites: 6 / 40 (15%)"
        assertContains(report, "OAM Sprites: 6 / 40 (15%)")
    }

    // -------------------------------------------------------------------------
    // Error/warning summary line
    // -------------------------------------------------------------------------

    @Test
    fun `report ends with error slash warning summary line`() {
        val diagnostics =
            listOf(
                Diagnostic(id = "ANLZ-01", severity = Severity.WARNING, message = "warning 1"),
                Diagnostic(id = "ANLZ-01", severity = Severity.WARNING, message = "warning 2"),
            )
        val ctx = baseContext().copy(diagnostics = diagnostics)

        val report = BudgetReporter.formatReport(ctx)

        assertContains(report, "0 errors, 2 warnings")
    }

    // -------------------------------------------------------------------------
    // Empty context
    // -------------------------------------------------------------------------

    @Test
    fun `empty context produces report with zeros`() {
        val ctx = baseContext(GameIR(name = "Empty"))

        // Use ansiEnabled=false for plain-text assertions
        val report = BudgetReporter.formatReport(ctx, ansiEnabled = false)

        assertTrue(report.isNotEmpty(), "Report should be non-empty even for empty game")
        // Should show zero OAM sprites
        assertContains(report, "OAM Sprites: 0 / 40 (0%)")
        // Should show zero WRAM
        assertContains(report, "WRAM: 0 /")
        // Should show 0 errors and 0 warnings
        assertContains(report, "0 errors, 0 warnings")
        // Should NOT show bank bars (no assignments)
        assertFalse(report.contains("Bank 1:"), "Empty game should not show bank bars")
    }
}
