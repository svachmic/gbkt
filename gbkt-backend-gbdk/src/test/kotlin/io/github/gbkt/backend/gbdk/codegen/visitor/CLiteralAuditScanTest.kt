/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.DiagonalMode
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.MovementConfig
import io.github.gbkt.core.ir.MovementStyle
import io.github.gbkt.core.ir.PhysicsConfig
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SmoothMovementConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * CLiteralAuditScanTest — Phase 07.9 Plan 03 structural regression guard.
 *
 * This test is an ARCHITECTURE-AGNOSTIC regression guard that validates the Phase 07.9 D-03
 * bucket-(a) migration. It greps emitted C output for the disallowed pattern: a signed variable
 * name followed by a comparison operator followed by an unsigned literal (`Nu`).
 *
 * ## Signed-name list source of truth
 *
 * The signed-variable names in the regex are sourced from **07.9-AUDIT.md** bucket-(a) sites. The
 * current list covers all bucket-(a) sites migrated in Plan 02:
 * - `rawX`, `rawY` — camera INT16 deltas (GBDKSystemVisitor.kt)
 * - `_player_vx`, `_player_vy` — smooth-movement INT8 velocities (ActorVisitor.kt)
 * - `_plat_vx`, `_plat_vy` — platformer INT8 velocities (PlatformerVisitor.kt,
 *   gbkt-genre-platformer)
 * - `vyVar`, `vxVar` — internal ActorVisitor variable names for velocity components
 * - `_v[xy]Var` — generalised pattern for any ActorVisitor velocity temp variable
 *
 * If new bucket-(a) sites are introduced (new signed variables in new visitor code), **extend this
 * regex list** and add a corresponding fixture that exercises the new site. Reference AUDIT.md for
 * the canonical bucket-(a) list.
 *
 * ## Bucket discipline (per AUDIT.md)
 *
 * - Bucket (a): signed-context comparison RHS → `CIntLiteral` (no `u` suffix). MIGRATED.
 * - Bucket (b): non-comparison signed-context → `CLiteral` still emits `Nu`. DEFERRED. Test 2 below
 *   GUARDS AGAINST over-migration of bucket-(b) sites.
 * - Bucket (c): genuinely unsigned → `CLiteral` still emits `Nu`. NO ACTION. Test 3 below GUARDS
 *   AGAINST over-migration of bucket-(c) sites.
 *
 * ## Evidence pattern
 *
 * Each test writes its emitted blob to `evidence/audit-scan/` BEFORE assertions fire. This mirrors
 * Round8CameraMonotonicityProbe.kt:155-186.
 */
class CLiteralAuditScanTest {

    private val emptyGameIR = GameIR(name = "Test", config = CartridgeConfig())

    companion object {
        val EVIDENCE_DIR = File(System.getProperty("user.dir")).resolve("build/gbkt/test-evidence")

        /**
         * Signed-variable name regex for the bucket-(a) guard. Source of truth: 07.9-AUDIT.md §
         * bucket-(a) sites. Extend when new signed variables are added to visitors.
         */
        val SIGNED_NAME_PATTERN: Regex =
            Regex(
                """(rawX|rawY|_plat_v[xy]|_player_v[xy]|vyVar|vxVar|_v[xy]Var)\s*(?:<=?|>=?|==|!=)\s*-?[0-9]+u\b"""
            )
    }

    // =========================================================================
    // Scaffolding helpers (mirrored from SignedComparisonLiteralEmissionTest)
    // =========================================================================

    private fun emitCameraBody(system: CameraSystem): String {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val functions = visitor.visitCameraSystem(system)
        return functions.first().body.joinToString("\n") { CEmitter.emitStatement(it) }
    }

    private fun emitSmoothMovementBody(actor: ActorIR): String {
        val functions = ActorVisitor.generateMovementFunction(actor)
        return functions.first().body.joinToString("\n") { CEmitter.emitStatement(it) }
    }

    private fun emitPlatformerPhysicsBody(actor: ActorIR): String {
        val functions = ActorVisitor.generateMovementFunction(actor)
        return functions.first().body.joinToString("\n") { CEmitter.emitStatement(it) }
    }

    private fun cameraWithBounds(boundsWidth: Int = 256, boundsHeight: Int = 256): CameraSystem =
        CameraSystem(
            id = "camera",
            followActorId = "player",
            boundsWidth = boundsWidth,
            boundsHeight = boundsHeight,
        )

    private fun cameraWithoutBounds(): CameraSystem =
        CameraSystem(
            id = "camera",
            followActorId = "player",
            boundsWidth = null,
            boundsHeight = null,
        )

    private fun smoothActor(id: String = "player"): ActorIR =
        ActorIR(
            id = id,
            position = PositionDef(0, 0),
            movementConfig =
                MovementConfig(
                    style = MovementStyle.SMOOTH,
                    speed = 3,
                    smoothConfig =
                        SmoothMovementConfig(
                            speed = 3,
                            acceleration = 1,
                            friction = 1,
                            diagonalMode = DiagonalMode.RAW,
                        ),
                ),
        )

    private fun platformerActor(id: String = "player"): ActorIR =
        ActorIR(
            id = id,
            position = PositionDef(0, 0),
            movementConfig = MovementConfig(style = MovementStyle.PHYSICS, speed = 2),
            physicsConfig =
                PhysicsConfig(
                    gravity = 2,
                    maxFallSpeed = 8,
                    platformerMode = true,
                    variableJump = true,
                    jumpCutMultiplier = 2,
                    velocityX = 0,
                    velocityY = 0,
                ),
        )

    // =========================================================================
    // TEST 1 — No signed variable compared against Nu across all signed-context
    //          emissions (kitchen-sink regex scan).
    //
    //          GREEN after Plan 02 migration. FAILS loudly if any bucket-(a)
    //          site is regressed (e.g. reverted CIntLiteral → CLiteral).
    // =========================================================================

    @Test
    fun no_signed_var_compared_against_u_suffix_across_all_signed_context_emissions() {
        EVIDENCE_DIR.mkdirs()

        val cameraWithBoundsBody =
            emitCameraBody(cameraWithBounds(boundsWidth = 256, boundsHeight = 256))
        val cameraWithoutBoundsBody = emitCameraBody(cameraWithoutBounds())
        val smoothBody = emitSmoothMovementBody(smoothActor("player"))
        val platformerBody = emitPlatformerPhysicsBody(platformerActor("player"))

        val emitted =
            listOf(cameraWithBoundsBody, cameraWithoutBoundsBody, smoothBody, platformerBody)
                .joinToString("\n/* === next fixture === */\n")

        // Evidence-before-assert pattern
        File(EVIDENCE_DIR, "01-kitchen-sink-all-signed-context.txt").writeText(emitted)

        val matches = SIGNED_NAME_PATTERN.findAll(emitted).toList()

        assertTrue(
            matches.isEmpty(),
            "Phase 07.9 audit scan: ZERO unsigned-literal comparisons allowed for signed variables " +
                "across camera + physics + smooth-movement emission.\n" +
                "Signed-name source: 07.9-AUDIT.md bucket-(a) sites.\n" +
                "Found ${matches.size} violation(s):\n" +
                matches.take(5).joinToString("\n") {
                    "  `${it.value}` at offset ${it.range.first}"
                } +
                (if (matches.size > 5) "\n  ... (${matches.size - 5} more)" else "") +
                "\n\nFull combined emitted text:\n${emitted.take(6000)}",
        )
    }

    // =========================================================================
    // TEST 2 — Bucket-(b) sites intentionally unchanged: the arithmetic operand
    //          `CLiteral(80)` / `CLiteral(72)` in rawX/rawY initializers still
    //          emits `80u` / `72u`.
    //
    //          REGRESSION GUARD against over-migration.
    //          Per AUDIT.md: bucket-(b) sites stay on CLiteral and emit `Nu`.
    //          Only bucket-(a) comparison RHS migrate to CIntLiteral.
    //          If this test fails, Plan 02 over-migrated bucket-(b) arithmetic sites.
    // =========================================================================

    @Test
    fun bucket_b_sites_intentionally_unchanged() {
        EVIDENCE_DIR.mkdirs()

        // Camera-with-bounds emits:
        //   INT16 rawX = (INT16)_player_x - 80u;   ← bucket-(b): CLiteral(80) → emits "80u"
        //   INT16 rawY = (INT16)_player_y - 72u;   ← bucket-(b): CLiteral(72) → emits "72u"
        val cameraBody = emitCameraBody(cameraWithBounds(boundsWidth = 256, boundsHeight = 256))
        File(EVIDENCE_DIR, "02-bucket-b-arithmetic-unchanged.txt").writeText(cameraBody)

        // The rawX initializer should still contain "80u" (not "80") — arithmetic bucket-(b)
        assertTrue(
            cameraBody.contains("80u") || cameraBody.contains("- 80u"),
            "Bucket-(b) guard: rawX initializer `(INT16)_player_x - 80u` must still emit `80u`. " +
                "AUDIT.md §'Bucket (b) follow-up candidates' site 1: this arithmetic operand is " +
                "DEFERRED (not a comparison RHS). If this fails, Plan 02 over-migrated it. " +
                "Emitted:\n${cameraBody.take(2000)}",
        )

        // The rawY initializer should still contain "72u" — arithmetic bucket-(b)
        assertTrue(
            cameraBody.contains("72u") || cameraBody.contains("- 72u"),
            "Bucket-(b) guard: rawY initializer `(INT16)_player_y - 72u` must still emit `72u`. " +
                "AUDIT.md §'Bucket (b) follow-up candidates' site 2: this arithmetic operand is " +
                "DEFERRED. If this fails, Plan 02 over-migrated it. " +
                "Emitted:\n${cameraBody.take(2000)}",
        )
    }

    // =========================================================================
    // TEST 3 — Bucket-(c) unsigned sites unchanged: CLiteral in unsigned context
    //          still emits `Nu` suffix.
    //
    //          REGRESSION GUARD against over-migration.
    //          Proves CIntLiteral is additive — it does not alter CLiteral emission.
    //          If this test fails, CEmitter.kt was modified incorrectly (CLiteral
    //          emission changed when only CIntLiteral emission should have changed).
    // =========================================================================

    @Test
    fun bucket_c_unsigned_sites_unchanged() {
        EVIDENCE_DIR.mkdirs()

        // Direct CLiteral construction — simulates any bucket-(c) unsigned comparison.
        // Example: score > 0 where score is UINT8 (unsigned context).
        // CLiteral(0) must still emit "0u" — CIntLiteral(0) would emit "0".
        val scoreExpr = CBinaryExpr(CVar("_score"), ">", CLiteral(0))
        val emitted = CEmitter.emitStatement(CExprStatement(scoreExpr))
        File(EVIDENCE_DIR, "03-bucket-c-unsigned-regression.txt").writeText(emitted)

        assertTrue(
            emitted.contains("0u"),
            "Bucket-(c) guard: CLiteral(0) in a non-signed-context comparison MUST still emit `0u`. " +
                "The Plan 02 CIntLiteral addition is ADDITIVE — it must NOT alter CLiteral emission. " +
                "CEmitter.kt:421: `is CLiteral -> if (expr.value >= 0) \"\${expr.value}u\" else \"\${expr.value}\"` " +
                "must remain unchanged. " +
                "Emitted: `$emitted`",
        )

        // Also verify that a higher CLiteral value still emits with u suffix
        val timerExpr = CBinaryExpr(CVar("_frame_counter"), "<", CLiteral(60))
        val timerEmitted = CEmitter.emitStatement(CExprStatement(timerExpr))
        File(EVIDENCE_DIR, "03b-bucket-c-nonzero-value.txt").writeText(timerEmitted)

        assertTrue(
            timerEmitted.contains("60u"),
            "Bucket-(c) guard: CLiteral(60) must emit `60u` (unsigned-default). " +
                "Emitted: `$timerEmitted`",
        )
    }
}
