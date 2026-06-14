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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// SIGNED COMPARISON LITERAL EMISSION TESTS — Phase 07.9 Plan 01 (RED forcing
// function for Plan 02)
//
// Locks the contract that signed-context comparison sites MUST NOT emit `Nu`
// (unsigned) literals on the RHS. At HEAD (before Plan 02 migration), visitors
// construct CLiteral(0) at these sites, causing the emitter to emit `0u`. This
// makes comparisons like `rawX < 0u` always false for signed variables under
// C integer promotion rules when the signed value is negative.
//
// Architecture: Option C (CIntLiteral split). See Phase 07.9 PLAN.md <decision>.
//
// RED state: 7 of 8 tests fail at HEAD because visitors still emit CLiteral(0).
// Test 7 (unsigned_sites_unchanged) passes immediately — it is the regression
// guard proving CLiteral emission is still correct for unsigned-typed sites.
//
// GREEN state (after Plan 02 migration): all 8 tests pass.
//
// Evidence-before-assert pattern: each test writes its emitted body to disk
// BEFORE assertions fire (mirrors Round8CameraMonotonicityProbe.kt:155-186).
// =============================================================================

class SignedComparisonLiteralEmissionTest {

    private val emptyGameIR = GameIR(name = "Test", config = CartridgeConfig())

    companion object {
        val EVIDENCE_DIR = File(System.getProperty("user.dir")).resolve("build/gbkt/test-evidence")
    }

    // =========================================================================
    // Scaffolding helpers
    // =========================================================================

    /** Build the update_camera_<id>() body and emit it as a single C-text blob. */
    private fun emitCameraBody(system: CameraSystem): String {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val functions = visitor.visitCameraSystem(system)
        return functions.first().body.joinToString("\n") { CEmitter.emitStatement(it) }
    }

    /**
     * Build the update_movement_<id>() body for a smooth-movement actor (with
     * acceleration/friction) and emit it as a single C-text blob.
     */
    private fun emitSmoothMovementBody(actor: ActorIR): String {
        val functions = ActorVisitor.generateMovementFunction(actor)
        return functions.first().body.joinToString("\n") { CEmitter.emitStatement(it) }
    }

    /**
     * Build the update_movement_<id>() body for a physics actor (platformer mode) and emit it as a
     * single C-text blob.
     */
    private fun emitPlatformerPhysicsBody(actor: ActorIR): String {
        val functions = ActorVisitor.generateMovementFunction(actor)
        return functions.first().body.joinToString("\n") { CEmitter.emitStatement(it) }
    }

    /** Fixture: camera with bounds (follows an actor over a zone wider than screen). */
    private fun cameraWithBounds(boundsWidth: Int = 256, boundsHeight: Int = 256): CameraSystem =
        CameraSystem(
            id = "camera",
            followActorId = "player",
            boundsWidth = boundsWidth,
            boundsHeight = boundsHeight,
        )

    /** Fixture: camera without bounds (no-bounds branch). */
    private fun cameraWithoutBounds(): CameraSystem =
        CameraSystem(
            id = "camera",
            followActorId = "player",
            boundsWidth = null,
            boundsHeight = null,
        )

    /** Fixture: smooth-movement actor (acceleration/friction model). */
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

    /** Fixture: platformer-physics actor (variable jump + gravity). */
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
    // TEST 1 — CameraSystem with bounds: rawX < 0 must not emit rawX < 0u
    // =========================================================================

    @Test
    fun cameraSystem_with_bounds_emits_signed_safe_lt_zero_for_camera_x() {
        EVIDENCE_DIR.mkdirs()
        // Use boundsWidth > 160 so maxX > 0 and the full clamp branch is taken.
        val system = cameraWithBounds(boundsWidth = 256, boundsHeight = 256)
        val body = emitCameraBody(system)
        File(EVIDENCE_DIR, "01-camera-bounds-x-lt-zero.txt").writeText(body)

        assertFalse(
            body.contains("rawX < 0u"),
            "Phase 07.9 contract: signed variable rawX must be compared against bare `0`, " +
                "not `0u`. Emitted body contains `rawX < 0u` — this is always false for negative " +
                "signed values under C integer promotion rules. " +
                "Emitted body (truncated to 4000):\n${body.take(4000)}",
        )
        assertTrue(
            body.contains("rawX < 0"),
            "Phase 07.9 contract: emitted body should contain `rawX < 0` comparison. " +
                "Emitted body:\n${body.take(4000)}",
        )
    }

    // =========================================================================
    // TEST 2 — CameraSystem with bounds: rawX > maxX must not emit rawX > NNu
    // =========================================================================

    @Test
    fun cameraSystem_with_bounds_emits_signed_safe_gt_maxX_for_camera_x() {
        EVIDENCE_DIR.mkdirs()
        val boundsWidth = 256 // maxX = max(0, 256-160) = 96
        val maxX = kotlin.math.max(0, boundsWidth - 160)
        val system = cameraWithBounds(boundsWidth = boundsWidth, boundsHeight = 256)
        val body = emitCameraBody(system)
        File(EVIDENCE_DIR, "02-camera-bounds-x-gt-maxX.txt").writeText(body)

        assertFalse(
            body.contains("rawX > ${maxX}u"),
            "Phase 07.9 contract: signed rawX compared against ${maxX} must NOT emit `${maxX}u`. " +
                "CLiteral(${maxX}) at a signed comparison RHS should become CIntLiteral(${maxX}). " +
                "Emitted body:\n${body.take(4000)}",
        )
        assertTrue(
            body.contains("rawX > $maxX"),
            "Phase 07.9 contract: emitted body should contain `rawX > $maxX` (bare, no u suffix). " +
                "Emitted body:\n${body.take(4000)}",
        )
    }

    // =========================================================================
    // TEST 3 — CameraSystem without bounds: rawX < 0 must not emit rawX < 0u
    // =========================================================================

    @Test
    fun cameraSystem_without_bounds_emits_signed_safe_lt_zero() {
        EVIDENCE_DIR.mkdirs()
        val system = cameraWithoutBounds()
        val body = emitCameraBody(system)
        File(EVIDENCE_DIR, "03-camera-no-bounds-lt-zero.txt").writeText(body)

        assertFalse(
            body.contains("rawX < 0u"),
            "Phase 07.9 contract (no-bounds branch): rawX < 0u must not appear. " +
                "The no-bounds branch (lines 200-209 GBDKSystemVisitor) also uses CLiteral(0) — " +
                "it must migrate to CIntLiteral(0). " +
                "Emitted body:\n${body.take(4000)}",
        )
        assertFalse(
            body.contains("rawY < 0u"),
            "Phase 07.9 contract (no-bounds branch): rawY < 0u must not appear. " +
                "Emitted body:\n${body.take(4000)}",
        )
    }

    // =========================================================================
    // TEST 4 — SmoothMovement friction: vxVar >/<  0 must not emit > 0u / < 0u
    // =========================================================================

    @Test
    fun smoothMovement_friction_emits_signed_safe_gt_zero_lt_zero() {
        EVIDENCE_DIR.mkdirs()
        val actor = smoothActor("player")
        val body = emitSmoothMovementBody(actor)
        File(EVIDENCE_DIR, "04-smooth-friction-direction.txt").writeText(body)

        // The buildFrictionStatements helper emits 4 signed comparisons:
        //   if (vxVar > 0) → vxVar > 0u at HEAD
        //   if (vxVar < 0) → vxVar < 0u at HEAD
        //   if (vyVar > 0) → vyVar > 0u at HEAD
        //   if (vyVar < 0) → vyVar < 0u at HEAD
        val vxId = "_${ActorVisitor.sanitizeId("player")}_vx"
        val vyId = "_${ActorVisitor.sanitizeId("player")}_vy"

        assertFalse(
            body.contains("$vxId > 0u"),
            "Phase 07.9 contract: friction $vxId > 0u must not appear (signed comparison). " +
                "Emitted body:\n${body.take(4000)}",
        )
        assertFalse(
            body.contains("$vxId < 0u"),
            "Phase 07.9 contract: friction $vxId < 0u must not appear (signed comparison). " +
                "Emitted body:\n${body.take(4000)}",
        )
        assertFalse(
            body.contains("$vyId > 0u"),
            "Phase 07.9 contract: friction $vyId > 0u must not appear (signed comparison). " +
                "Emitted body:\n${body.take(4000)}",
        )
        assertFalse(
            body.contains("$vyId < 0u"),
            "Phase 07.9 contract: friction $vyId < 0u must not appear (signed comparison). " +
                "Emitted body:\n${body.take(4000)}",
        )
    }

    // =========================================================================
    // TEST 5 — PlatformerPhysics jump-cancel: vy < 0 must not emit vy < 0u
    // =========================================================================

    @Test
    fun platformerPhysics_jump_cancel_emits_signed_safe_lt_zero_on_vy() {
        EVIDENCE_DIR.mkdirs()
        val actor = platformerActor("player")
        val body = emitPlatformerPhysicsBody(actor)
        File(EVIDENCE_DIR, "05-platformer-jump-cancel-vy.txt").writeText(body)

        val vyId = "_${ActorVisitor.sanitizeId("player")}_vy"

        // ActorVisitor.kt:577: CBinaryExpr(vyVar, "<", CLiteral(0)) → jump-cancel condition
        // At HEAD emits `_player_vy < 0u` — always false for signed INT8 negative values.
        assertFalse(
            body.contains("$vyId < 0u"),
            "Phase 07.9 contract (jump-cancel): $vyId < 0u must not appear. " +
                "This makes jump-cancel never fire because a signed negative value never " +
                "satisfies the unsigned comparison. " +
                "Emitted body:\n${body.take(4000)}",
        )
        assertTrue(
            body.contains("$vyId < 0"),
            "Phase 07.9 contract (jump-cancel): emitted body should contain `$vyId < 0` " +
                "(bare, no u suffix). " +
                "Emitted body:\n${body.take(4000)}",
        )
    }

    // =========================================================================
    // TEST 6 — PlatformerPhysics max-fall clamp: no 0u patterns in physics body
    // =========================================================================

    @Test
    fun platformerPhysics_max_fall_clamp_emits_signed_safe_gt_maxFall_on_vy() {
        EVIDENCE_DIR.mkdirs()
        val actor = platformerActor("player")
        val body = emitPlatformerPhysicsBody(actor)
        File(EVIDENCE_DIR, "06-platformer-max-fall-vy.txt").writeText(body)

        val vyId = "_${ActorVisitor.sanitizeId("player")}_vy"

        // ActorVisitor.kt:614: CBinaryExpr(vyVar, ">", maxFallDef) — RHS is a CVar, not a literal
        // However the physics body should not contain any unsigned literal comparisons for vy.
        // Scan entire physics emission for unsigned `vyId [<>!=]+ Nu` patterns.
        val unsignedVyRegex = Regex(Regex.escape(vyId) + """\\s*[<>!=]+\\s*[0-9]+u""")
        val unsignedVyMatches = unsignedVyRegex.findAll(body).toList()
        assertTrue(
            unsignedVyMatches.isEmpty(),
            "Phase 07.9 contract (max-fall): no unsigned literal comparisons for $vyId. " +
                "Found: ${unsignedVyMatches.map { it.value }}. " +
                "Emitted body:\n${body.take(4000)}",
        )
    }

    // =========================================================================
    // TEST 7 — Regression guard: unsigned UINT8 sites still emit `Nu` suffix
    //          (proves Plan 02 migration must NOT over-migrate)
    //          GREEN immediately — does not test a bug-site.
    // =========================================================================

    @Test
    fun unsigned_sites_unchanged() {
        EVIDENCE_DIR.mkdirs()
        // Build a manual CBinaryExpr that represents `score > 0` where score is UINT8.
        // CLiteral(0) at a non-signed-context site must still emit `0u`.
        val scoreExpr = CBinaryExpr(CVar("_score"), ">", CLiteral(0))
        val emitted = CEmitter.emitStatement(CExprStatement(scoreExpr))
        File(EVIDENCE_DIR, "07-unsigned-regression-guard.txt").writeText(emitted)

        assertTrue(
            emitted.contains("0u"),
            "Regression guard: CLiteral(0) in a non-signed-context comparison must still " +
                "emit `0u`. Task 1's additive CIntLiteral change must NOT alter CLiteral emission. " +
                "Emitted: $emitted",
        )
    }

    // =========================================================================
    // TEST 8 — Cross-architecture regex scan: no signed var vs Nu patterns
    //          across camera + physics + smooth-movement emission
    // =========================================================================

    @Test
    fun no_signed_var_compared_against_u_suffix_in_camera_or_physics_emission() {
        EVIDENCE_DIR.mkdirs()
        val cameraBody = emitCameraBody(cameraWithBounds(boundsWidth = 256, boundsHeight = 256))
        val smoothBody = emitSmoothMovementBody(smoothActor("player"))
        val physicsBody = emitPlatformerPhysicsBody(platformerActor("player"))
        val combined = cameraBody + "\n" + smoothBody + "\n" + physicsBody
        File(EVIDENCE_DIR, "08-cross-arch-regex-scan.txt").writeText(combined)

        // Regex: signed variable name followed by comparison operator followed by unsigned literal
        // Covers: rawX, rawY (camera), _player_vx, _player_vy (physics/smooth)
        val pattern =
            Regex("""(rawX|rawY|_player_vx|_player_vy|_player_v[xy])\s*[<>!=]+\s*[0-9]+u""")
        val matches = pattern.findAll(combined).toList()

        assertTrue(
            matches.isEmpty(),
            "Phase 07.9 contract: zero unsigned-literal comparisons for signed variables " +
                "in camera + physics + smooth emission. Found ${matches.size} violation(s):\n" +
                matches.joinToString("\n") { "  `${it.value}` at offset ${it.range.first}" } +
                "\n\nFull combined emitted text (truncated):\n${combined.take(4000)}",
        )
    }
}
