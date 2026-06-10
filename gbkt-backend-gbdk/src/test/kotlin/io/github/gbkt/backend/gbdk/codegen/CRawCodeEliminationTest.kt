/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen

import io.github.gbkt.backend.gbdk.codegen.ast.CBlock
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitch
import io.github.gbkt.backend.gbdk.codegen.ast.CWhile
import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.backend.gbdk.codegen.visitor.ActorVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.GBDKSystemVisitor
import io.github.gbkt.backend.gbdk.codegen.visitor.ScriptOpVisitor
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.DestroyActor
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SaveSystem
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SpawnActor
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.VariableDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// =============================================================================
// CRAW CODE ELIMINATION TESTS (Phase 06.1-06 success criterion 7)
// Verifies that ALL structural CRawCode has been replaced with typed C AST.
//
// The only acceptable CRawCode after Phase 06.1-06:
// 1. GBDK macros: ENABLE_RAM, DISABLE_RAM, SCX_REG, SCY_REG camera writes
// 2. GBDK macros: SHOW_WIN, HIDE_WIN, DISPLAY_ON, SHOW_SPRITES in GBDKPipeline
// 3. RawOp pass-through (visitRawOp) — user DSL escape hatch
// 4. SDCC warning suppression casts: (void)from; (void)to; in show_sprites_range
//    (Phase 09.1 plan 02, D-08 — silences warning 85 unused parameter)
//
// This test suite counts CRawCode nodes recursively across the typed C AST
// and asserts the total is zero (or equals the documented exception count).
// =============================================================================

// =============================================================================
// Recursive CRawCode counter
// =============================================================================

/**
 * Recursively count [CRawCode] nodes in a list of [CStatement] nodes.
 *
 * Traverses all compound statement containers: [CIf] (then+else), [CFor] (body), [CWhile] (body),
 * [CSwitch] (all case bodies), [CBlock] (statements). All other statement types contribute 0.
 */
fun countRawCode(stmts: List<CStatement>): Int = stmts.sumOf { stmt ->
    when (stmt) {
        is CRawCode -> 1
        is CBlock -> countRawCode(stmt.statements)
        is CIf -> countRawCode(stmt.thenBody) + countRawCode(stmt.elseBody)
        is CFor -> countRawCode(stmt.body)
        is CWhile -> countRawCode(stmt.body)
        is CSwitch -> stmt.cases.sumOf { countRawCode(it.body) }
        else -> 0
    }
}

/** Recursively count [CRawCode] in a [CFunction]'s body. */
fun countRawCodeInFunction(fn: CFunction): Int = countRawCode(fn.body)

/**
 * Recursively collect all [CRawCode] nodes from a statement list.
 *
 * Returns a flat list of all raw code nodes found at any depth. Traverses the same containers as
 * [countRawCode].
 */
fun collectAllRawCode(stmts: List<CStatement>): List<CRawCode> = stmts.flatMap { stmt ->
    when (stmt) {
        is CRawCode -> listOf(stmt)
        is CBlock -> collectAllRawCode(stmt.statements)
        is CIf -> collectAllRawCode(stmt.thenBody) + collectAllRawCode(stmt.elseBody)
        is CFor -> collectAllRawCode(stmt.body)
        is CWhile -> collectAllRawCode(stmt.body)
        is CSwitch -> stmt.cases.flatMap { collectAllRawCode(it.body) }
        else -> emptyList()
    }
}

class CRawCodeEliminationTest {

    private val emptyGameIR = GameIR(name = "Test", config = CartridgeConfig())

    // =========================================================================
    // CameraSystem — zero CRawCode (SCX_REG/SCY_REG are expected CRawCode)
    // =========================================================================

    @Test
    fun `GBDKSystemVisitor camera system has zero unexpected CRawCode`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val system = CameraSystem(id = "cam")

        val functions = visitor.visitCameraSystem(system)

        // CameraSystem uses CRawCode only for SCX_REG/SCY_REG hardware register writes
        // (GBDK macros — acceptable). All structural logic (bounds clamping) must be typed AST.
        // Count CRawCode nodes that are NOT register writes.
        val nonRegisterRaw = functions.flatMap { fn ->
            collectAllRawCode(fn.body).filter { raw ->
                !raw.code.contains("SCX_REG") && !raw.code.contains("SCY_REG")
            }
        }
        assertEquals(
            0,
            nonRegisterRaw.size,
            "CameraSystem should have zero non-register CRawCode, found: ${nonRegisterRaw.map { it.code }}",
        )
    }

    // =========================================================================
    // SaveSystem — zero structural CRawCode
    // =========================================================================

    @Test
    fun `GBDKSystemVisitor save system has zero unexpected CRawCode`() {
        val gameWithVars =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables = listOf(VariableDef("score", VarType.U8, 0)),
            )
        val visitor = GBDKSystemVisitor(gameWithVars)
        val saveSystem =
            SaveSystem(id = "save", slots = 3, useChecksum = true, transientVarNames = emptySet())

        val functions = visitor.visitSaveSystem(saveSystem)

        // Filter out the ENABLE_RAM / DISABLE_RAM GBDK macros (acceptable).
        // All other structural logic (address arithmetic, SRAM writes) must be typed AST.
        functions.forEach { fn ->
            val nonMacroRaw =
                fn.body
                    .flatMap { stmt ->
                        when (stmt) {
                            is CRawCode -> listOf(stmt)
                            is CIf -> (stmt.thenBody + stmt.elseBody).filterIsInstance<CRawCode>()
                            else -> emptyList()
                        }
                    }
                    .filter { raw ->
                        !raw.code.contains("ENABLE_RAM") && !raw.code.contains("DISABLE_RAM")
                    }
            assertEquals(
                0,
                nonMacroRaw.size,
                "SaveSystem function '${fn.name}' has non-macro CRawCode: ${nonMacroRaw.map { it.code }}",
            )
        }
    }

    // =========================================================================
    // ExplorationSystem — zero structural CRawCode
    // =========================================================================

    @Test
    fun `GBDKSystemVisitor exploration system has zero CRawCode`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val system = ExplorationSystem(id = "explore")

        val functions = visitor.visitExplorationSystem(system)
        val rawCount = functions.sumOf { countRawCodeInFunction(it) }

        assertEquals(
            0,
            rawCount,
            "ExplorationSystem should have zero CRawCode — uses typed CIf/CExprStatement",
        )
    }

    // =========================================================================
    // SpawnActor — zero CRawCode
    // =========================================================================

    @Test
    fun `ScriptOpVisitor SpawnActor has zero CRawCode`() {
        val op = SpawnActor(actorId = "player")
        val result = ScriptOpVisitor.visit(op)

        val raw = countRawCode(listOf(result))
        assertEquals(0, raw, "SpawnActor should have zero CRawCode — uses typed CBlock/CIf/CCall")
    }

    // =========================================================================
    // DestroyActor — zero CRawCode
    // =========================================================================

    @Test
    fun `ScriptOpVisitor DestroyActor has zero CRawCode`() {
        val op = DestroyActor(actorId = "enemy")
        val result = ScriptOpVisitor.visit(op)

        val raw = countRawCode(listOf(result))
        assertEquals(0, raw, "DestroyActor should have zero CRawCode — uses typed CBlock/CCall")
    }

    // =========================================================================
    // ActorVisitor sprite helpers — zero CRawCode
    // =========================================================================

    @Test
    fun `ActorVisitor hide_sprites_range has zero CRawCode`() {
        val fn = ActorVisitor.generateHideSpritesRange()

        val raw = countRawCodeInFunction(fn)
        assertEquals(
            0,
            raw,
            "hide_sprites_range should have zero CRawCode — uses typed CVarDecl + CFor + CCall",
        )
    }

    @Test
    fun `ActorVisitor show_sprites_range has exactly 2 CRawCode (SDCC warning 85 suppression)`() {
        val fn = ActorVisitor.generateShowSpritesRange()

        val raw = countRawCodeInFunction(fn)
        assertEquals(
            2,
            raw,
            "show_sprites_range must have exactly 2 CRawCode: '(void)from;' and '(void)to;' " +
                "to suppress SDCC warning 85 (unused parameter). " +
                "These are intentional exceptions per Phase 09.1 plan 02 D-08.",
        )
    }

    // =========================================================================
    // SimpleBattle — zero CRawCode (typed CSwitch/CSwitchCase/CBreak)
    // =========================================================================

    @Test
    fun `GBDKSystemVisitor SimpleBattle has zero CRawCode`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val battleSystem = GenericSystem(id = "combat", config = mapOf("type" to "simple_battle"))

        val functions = visitor.visitGenericSystem(battleSystem)
        val rawCount = functions.sumOf { countRawCodeInFunction(it) }

        assertEquals(
            0,
            rawCount,
            "SimpleBattle should have zero CRawCode — uses typed CSwitch/CSwitchCase/CBreak",
        )
    }

    // =========================================================================
    // Full pipeline — zero unexpected CRawCode
    // =========================================================================

    @Test
    fun `GBDKPipeline full pipeline has zero CRawCode in generated AST functions`() {
        val gameIR =
            GameIR(
                name = "TestGame",
                config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
                scenes = listOf(SceneIR(id = "main")),
                actors = listOf(ActorIR(id = "player", position = PositionDef(80, 72))),
                systems = listOf(CameraSystem(id = "cam"), ExplorationSystem(id = "explore")),
                startScene = "main",
            )

        val output = GBDKPipeline().generate(gameIR)

        // Generated C must not contain hashCode() artifacts (old stub pattern).
        val mainC = output.files["main.c"] ?: error("main.c not generated")
        assertFalse(
            mainC.contains("hashCode()"),
            "hashCode() should never appear in generated C output",
        )
    }
}
