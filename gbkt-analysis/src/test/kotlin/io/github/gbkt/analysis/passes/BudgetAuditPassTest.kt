/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.Diagnostic
import io.github.gbkt.analysis.FakeProfile
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.RAMLayout
import io.github.gbkt.analysis.ResourceInventory
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
import io.github.gbkt.core.ir.VRAMRange
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BudgetAuditPassTest {

    private val pass = BudgetAuditPass()

    private fun makeActor(id: String): ActorIR =
        ActorIR(
            id = id,
            position = PositionDef(0, 0),
            sprite = SpriteDef(AssetRef("sprite.png", AssetType.SPRITE), SizeDef(8, 8)),
        )

    private fun makeBaseContext(gameName: String = "TestGame"): PassContext {
        val actor = makeActor("player")
        val scene = SceneIR(id = "gameplay", actorIds = listOf("player"))
        val game = GameIR(name = gameName, actors = listOf(actor), scenes = listOf(scene))

        return PassContext(
            game = game,
            profile = FakeProfile,
            config = AnalysisConfig(maxBanks = 2),
            bankAssignments = mapOf("gameplay" to BankSlot(1)),
            vramAssignments =
                mapOf(
                    "player" to VRAMRange(startTile = 0, endTile = 1),
                    "gameplay" to VRAMRange(startTile = 1, endTile = 1),
                ),
            oamAssignments = mapOf("player" to OAMSlot(0)),
            ramLayout = RAMLayout(wramUsed = 25, hramUsed = 0, sramUsed = 0),
            inventory = ResourceInventory(totalActors = 1, totalScenes = 1),
        )
    }

    // -------------------------------------------------------------------------
    // Success path
    // -------------------------------------------------------------------------

    @Test
    fun `pass succeeds when all resources within budget`() {
        val ctx = makeBaseContext()

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
    }

    @Test
    fun `report string stored on context`() {
        val ctx = makeBaseContext()

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        assertNotNull(
            result.context.budgetReport,
            "budgetReport should be non-null after BudgetAuditPass",
        )
        assertTrue(
            result.context.budgetReport!!.isNotEmpty(),
            "budgetReport should be non-empty after BudgetAuditPass",
        )
    }

    @Test
    fun `report contains game name`() {
        val ctx = makeBaseContext(gameName = "MySpecialGame")

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        assertTrue(
            result.context.budgetReport!!.contains("MySpecialGame"),
            "Budget report should contain game name 'MySpecialGame'",
        )
    }

    // -------------------------------------------------------------------------
    // Failure path — error diagnostics
    // -------------------------------------------------------------------------

    @Test
    fun `pass fails when error diagnostics exist`() {
        val ctx =
            makeBaseContext()
                .copy(
                    diagnostics =
                        listOf(
                            Diagnostic(
                                id = "ANLZ-02",
                                severity = Severity.ERROR,
                                message = "Bank overflow detected by a previous pass",
                            )
                        )
                )

        val result = pass.run(ctx)

        assertIs<PassResult.Failed>(result)
    }

    @Test
    fun `pass fails with all accumulated diagnostics on error`() {
        val errorDiag =
            Diagnostic(id = "ANLZ-02", severity = Severity.ERROR, message = "Bank overflow")
        val warnDiag =
            Diagnostic(id = "ANLZ-04", severity = Severity.WARNING, message = "Near OAM limit")
        val ctx = makeBaseContext().copy(diagnostics = listOf(warnDiag, errorDiag))

        val result = pass.run(ctx)

        assertIs<PassResult.Failed>(result)
        assertTrue(
            result.diagnostics.size == 2,
            "Failed result should carry all diagnostics (warning + error), got: ${result.diagnostics.size}",
        )
    }

    @Test
    fun `warning-only diagnostics do not fail the pass`() {
        val ctx =
            makeBaseContext()
                .copy(
                    diagnostics =
                        listOf(
                            Diagnostic(
                                id = "ANLZ-04",
                                severity = Severity.WARNING,
                                message = "Near OAM limit",
                            ),
                            Diagnostic(
                                id = "ANLZ-05",
                                severity = Severity.WARNING,
                                message = "Near WRAM limit",
                            ),
                        )
                )

        val result = pass.run(ctx)

        // Warnings alone must not cause a failure
        assertIs<PassResult.Success>(result)
    }
}
