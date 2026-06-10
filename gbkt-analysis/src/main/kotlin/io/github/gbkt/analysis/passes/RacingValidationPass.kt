/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.AnalysisPass
import io.github.gbkt.analysis.Diagnostic
import io.github.gbkt.analysis.DiagnosticCode
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.Severity
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.MoveBy
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.SetPosition

/**
 * Static-analysis pass that enforces the binding invariants of the `racing { }` / `vehicle { }` DSL
 * (Phase 07.4 — sport genre codegen fix).
 *
 * The pass walks every [GenericSystem] of type `"sport_racing"` and emits one of five diagnostic
 * ids:
 * ```
 * | Id              | Severity | Source decision | What it catches |
 * |-----------------|----------|-----------------|-----------------|
 * | ANLZ-RACING-01  | ERROR    | D-10            | Polygon non-degeneracy (< 3 waypoints, all collinear, or zero enclosed area). |
 * | ANLZ-RACING-02  | ERROR    | D-05            | `racing { }` block is missing `player(...)`. |
 * | ANLZ-RACING-03  | ERROR    | D-05            | A `vehicle { }` whose bound `actor(...)` does not resolve to a declared actor. |
 * | ANLZ-RACING-04  | WARNING  | D-08            | Scene `frame { }` block writes the bound player actor's position via `moveBy` / `SetPosition` / direct `_<actor>_x` / `_<actor>_y` assignment — `racing { }` owns this car's loop. |
 * | ANLZ-RACING-05  | WARNING  | D-06            | A user-declared [CameraSystem] follows an actor that is NOT the bound player vehicle's actor. |
 * ```
 *
 * Cross-module access without a cycle: this pass lives in `gbkt-analysis` and must NOT directly
 * depend on `gbkt-genre-sport` (which would form the cycle gbkt-analysis -> gbkt-genre-sport ->
 * gbkt-backend-gbdk -> gbkt-analysis). Plan 03's [GenericSystem.config] payload is the SINGLE
 * source of truth for the racing config map; this pass reads keys read-only:
 * - `"type"` -> "sport_racing" — discriminator
 * - `"config"` -> the `RacingConfig` data class instance — read via reflection
 * - `"registeredVehicles"` -> `Map<String, Vehicle>` — read via reflection
 * - `"playerVehicle"` -> the `Vehicle` instance for the bound player — read via reflection
 *
 * `ERROR` diagnostics fail the pipeline (`PassResult.Failed`); `WARNING` diagnostics pass through
 * (`PassResult.Success`) so the build continues but surfaces the issue.
 *
 * Pipeline position: AFTER [SemanticValidationPass] (which guarantees actors / scenes resolve) and
 * BEFORE [ResourceInventoryPass] (so racing-binding errors short-circuit before resource counting).
 */
class RacingValidationPass : AnalysisPass {

    override fun run(context: PassContext): PassResult {
        val game = context.game
        val diagnostics = mutableListOf<Diagnostic>()

        val racingSystems =
            game.systems.filterIsInstance<GenericSystem>().filter {
                it.config["type"] == "sport_racing"
            }

        for (sys in racingSystems) {
            val racingConfig = sys.config["config"] ?: continue
            val racingId = readStringField(racingConfig, "id") ?: sys.id

            // Check 1 — ANLZ-RACING-01: polygon non-degeneracy (D-10).
            checkPolygonNonDegenerate(racingConfig, racingId, diagnostics)

            // Check 2 — ANLZ-RACING-02: racing has a player binding (D-05).
            val playerVehicleId = readStringField(racingConfig, "playerVehicleId")
            checkPlayerBound(playerVehicleId, racingId, diagnostics)

            // Check 3 — ANLZ-RACING-03: every referenced vehicle's actor exists (D-05).
            val registeredVehicles = readMapField(sys, "registeredVehicles")
            val ctx =
                RacingValidationContext(
                    game = game,
                    racingConfig = racingConfig,
                    playerVehicleId = playerVehicleId,
                    registeredVehicles = registeredVehicles,
                    racingId = racingId,
                )
            checkVehicleActorBindings(ctx, diagnostics)

            // The player actor id is needed for both Check 4 and Check 5; resolve it once.
            val playerActorId = resolvePlayerActorId(playerVehicleId, registeredVehicles)

            // Check 4 — ANLZ-RACING-04: scene frameOps don't fight racing()'s loop (D-08).
            if (playerActorId != null) {
                checkNoHandCodedMovement(game, playerActorId, diagnostics)
            }

            // Check 5 — ANLZ-RACING-05: user-declared CameraSystem.follow matches bound player
            // (D-06).
            if (playerActorId != null) {
                checkCameraFollowsPlayer(game, racingId, playerActorId, diagnostics)
            }
        }

        val errors = diagnostics.filter { it.severity == Severity.ERROR }
        return if (errors.isNotEmpty()) {
            PassResult.Failed(diagnostics)
        } else {
            PassResult.Success(context.withDiagnostics(diagnostics))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Check 1 — polygon non-degeneracy (D-10, ANLZ-RACING-01).
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkPolygonNonDegenerate(
        racingConfig: Any,
        racingId: String,
        diagnostics: MutableList<Diagnostic>,
    ) {
        val track = readField(racingConfig, "track") ?: return
        val waypoints = readListField(track, "waypoints") ?: return
        val points = waypoints.mapNotNull { it?.let { wp -> readWaypointXY(wp) } }

        if (points.size < 3) {
            diagnostics +=
                Diagnostic(
                    code = DiagnosticCode.RACING_TRACK_GEOMETRY,
                    severity = Severity.ERROR,
                    message =
                        "racing '$racingId' polygon has only ${points.size} waypoint" +
                            (if (points.size == 1) "" else "s") +
                            " — at least 3 required for an enclosed circuit",
                    location = "racing system '$racingId' track waypoints",
                    suggestion = "Add waypoints until the loop encloses an area (D-10).",
                )
            return
        }

        if (allCollinear(points)) {
            diagnostics +=
                Diagnostic(
                    code = DiagnosticCode.RACING_TRACK_GEOMETRY,
                    severity = Severity.ERROR,
                    message =
                        "racing '$racingId' waypoints are collinear — polygon must enclose " +
                            "a non-zero area",
                    location = "racing system '$racingId' track waypoints",
                    suggestion =
                        "Adjust at least one waypoint so the loop encloses an area " +
                            "(D-10 / D-17 enclosure).",
                )
            return
        }

        if (signedAreaTwice(points) == 0L) {
            diagnostics +=
                Diagnostic(
                    code = DiagnosticCode.RACING_TRACK_GEOMETRY,
                    severity = Severity.ERROR,
                    message =
                        "racing '$racingId' polygon encloses zero area — waypoints may be " +
                            "coincident or the loop is degenerate",
                    location = "racing system '$racingId' track waypoints",
                    suggestion =
                        "Ensure at least three waypoints have distinct (x, y) coordinates and " +
                            "the loop encloses an area (D-10).",
                )
        }
    }

    /**
     * True when all points lie on a single straight line. Three points (a, b, c) are collinear iff
     * the cross product of (b - a) and (c - a) is zero. A polygon with 3+ waypoints is collinear if
     * every triple shares this property — equivalently, every triple (a, b, c_i) for i >= 2 has
     * zero cross product.
     */
    private fun allCollinear(points: List<Pair<Int, Int>>): Boolean {
        if (points.size < 3) return true
        val (ax, ay) = points[0]
        val (bx, by) = points[1]
        for (i in 2 until points.size) {
            val (cx, cy) = points[i]
            // Cross product of (b - a) and (c - a).
            val cross = (bx - ax).toLong() * (cy - ay) - (by - ay).toLong() * (cx - ax)
            if (cross != 0L) return false
        }
        return true
    }

    /**
     * Twice the signed area of the polygon via the shoelace formula. Returns 0 iff the polygon
     * encloses zero area. Stays in Long arithmetic to avoid Int overflow on ~32x32 tile coords.
     */
    private fun signedAreaTwice(points: List<Pair<Int, Int>>): Long {
        var sum = 0L
        for (i in points.indices) {
            val (x1, y1) = points[i]
            val (x2, y2) = points[(i + 1) % points.size]
            sum += (x1.toLong() * y2 - x2.toLong() * y1)
        }
        return kotlin.math.abs(sum)
    }

    private fun readWaypointXY(waypoint: Any): Pair<Int, Int>? {
        val x = readIntField(waypoint, "tileX") ?: return null
        val y = readIntField(waypoint, "tileY") ?: return null
        return x to y
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Check 2 — racing has a player binding (D-05, ANLZ-RACING-02).
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkPlayerBound(
        playerVehicleId: String?,
        racingId: String,
        diagnostics: MutableList<Diagnostic>,
    ) {
        if (playerVehicleId == null) {
            diagnostics +=
                Diagnostic(
                    code = DiagnosticCode.RACING_PLAYER_MISSING,
                    severity = Severity.ERROR,
                    message =
                        "racing '$racingId' is missing player(...) — every race needs a player " +
                            "vehicle (D-05)",
                    location = "racing system '$racingId'",
                    suggestion =
                        "Inside the racing { } block call `player(carPlayerRef)` where " +
                            "carPlayerRef is a vehicle declared via `val carPlayer by vehicle " +
                            "{ actor(...); stats { } }`.",
                )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Check 3 — every referenced vehicle's actor exists (D-05, ANLZ-RACING-03).
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Per-racing-system iteration context bundle. The 5 inputs flow together from each [run] loop
     * iteration; bundling them keeps call-site noise low and keeps [checkVehicleActorBindings]
     * under the LongParameterList threshold.
     */
    private data class RacingValidationContext(
        val game: GameIR,
        val racingConfig: Any,
        val playerVehicleId: String?,
        val registeredVehicles: Map<*, *>?,
        val racingId: String,
    )

    private fun checkVehicleActorBindings(
        ctx: RacingValidationContext,
        diagnostics: MutableList<Diagnostic>,
    ) {
        // If the registry is missing entirely, that's a Plan 03 contract violation — surface a
        // single diagnostic and continue. Don't crash the pass.
        if (
            ctx.registeredVehicles == null &&
                (ctx.playerVehicleId != null || hasAnyAiSlots(ctx.racingConfig))
        ) {
            diagnostics +=
                Diagnostic(
                    code = DiagnosticCode.RACING_VEHICLE_ACTOR_UNRESOLVED,
                    severity = Severity.ERROR,
                    message =
                        "racing '${ctx.racingId}' GenericSystem missing required " +
                            "'registeredVehicles' key — Plan 03 RacingDelegate contract not " +
                            "satisfied",
                    location = "racing system '${ctx.racingId}'",
                    suggestion =
                        "Rebuild gbkt-genre-sport (./gradlew :gbkt-genre-sport:compileKotlin) so " +
                            "the racing { } delegate populates registeredVehicles.",
                )
            return
        }

        val actorIds = ctx.game.actors.map { it.id }.toSet()
        val vehicles = ctx.registeredVehicles ?: emptyMap<Any?, Any?>()

        // Player vehicle.
        if (ctx.playerVehicleId != null) {
            verifyVehicleActor(
                vehicleId = ctx.playerVehicleId,
                registeredVehicles = vehicles,
                actorIds = actorIds,
                racingId = ctx.racingId,
                diagnostics = diagnostics,
            )
        }

        // AI vehicle slots.
        for (slot in readAiSlots(ctx.racingConfig)) {
            val vehicleId = readStringField(slot, "vehicleId") ?: continue
            verifyVehicleActor(
                vehicleId = vehicleId,
                registeredVehicles = vehicles,
                actorIds = actorIds,
                racingId = ctx.racingId,
                diagnostics = diagnostics,
            )
        }
    }

    private fun verifyVehicleActor(
        vehicleId: String,
        registeredVehicles: Map<*, *>,
        actorIds: Set<String>,
        racingId: String,
        diagnostics: MutableList<Diagnostic>,
    ) {
        val vehicle = registeredVehicles[vehicleId]
        if (vehicle == null) {
            // Defensive fallback: VehicleRegistry.resolve normally errors at IR-build time, so
            // this branch is unreachable from the production DSL path. Surface a generic
            // ANLZ-RACING-03 if it ever happens (e.g., a hand-built fixture).
            diagnostics +=
                Diagnostic(
                    code = DiagnosticCode.RACING_VEHICLE_ACTOR_UNRESOLVED,
                    severity = Severity.ERROR,
                    message =
                        "vehicle '$vehicleId' referenced by racing '$racingId' is not registered",
                    location = "vehicle '$vehicleId' in racing '$racingId'",
                    suggestion =
                        "Declare `val $vehicleId by vehicle { actor(...); stats { ... } }` " +
                            "before referencing it from racing { player(...) } or " +
                            "aiOpponents(...).",
                )
            return
        }
        val actorId = resolveVehicleActorId(vehicle)
        if (actorId == null || actorId !in actorIds) {
            diagnostics +=
                Diagnostic(
                    code = DiagnosticCode.RACING_VEHICLE_ACTOR_UNRESOLVED,
                    severity = Severity.ERROR,
                    message =
                        "vehicle '$vehicleId' references actor '${actorId ?: "(unbound)"}' " +
                            "which does not exist",
                    location = "vehicle '$vehicleId' in racing '$racingId'",
                    suggestion =
                        "Declare `val ${actorId ?: "<actorName>"} by actor { … }` before " +
                            "binding it via `actor(...)` inside vehicle { }.",
                )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Check 4 — scene frameOps don't fight racing()'s loop (D-08, ANLZ-RACING-04).
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkNoHandCodedMovement(
        game: GameIR,
        playerActorId: String,
        diagnostics: MutableList<Diagnostic>,
    ) {
        // Match the sport-codegen variable name convention "_${actorId}_x" / "_y".
        val playerXVar = "_${playerActorId}_x"
        val playerYVar = "_${playerActorId}_y"
        val emitted = mutableSetOf<String>() // Dedupe by scene id — one warning per scene.

        for (scene in game.scenes) {
            val ops = walkScriptOps(scene.frameOps)
            val hit = ops.any { op ->
                when (op) {
                    is MoveBy -> op.actorId == playerActorId
                    is SetPosition -> op.actorId == playerActorId
                    is Assign -> op.target == playerXVar || op.target == playerYVar
                    else -> false
                }
            }
            if (hit && emitted.add(scene.id)) {
                diagnostics +=
                    Diagnostic(
                        code = DiagnosticCode.RACING_MANUAL_MOVEMENT,
                        severity = Severity.WARNING,
                        message =
                            "scene '${scene.id}' frame block writes the bound player actor " +
                                "'$playerActorId' position directly — racing() owns this car's " +
                                "movement loop (D-08)",
                        location = "scene '${scene.id}' frameOps",
                        suggestion =
                            "Remove direct moveBy / SetPosition / _${playerActorId}_x|y " +
                                "writes from the scene's frame block; racing() generates the " +
                                "input -> physics -> position loop automatically.",
                    )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Check 5 — user CameraSystem follows the bound player (D-06, ANLZ-RACING-05).
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkCameraFollowsPlayer(
        game: GameIR,
        racingId: String,
        playerActorId: String,
        diagnostics: MutableList<Diagnostic>,
    ) {
        for (system in game.systems.filterIsInstance<CameraSystem>()) {
            val followId = system.followActorId
            if (followId != null && followId != playerActorId) {
                diagnostics +=
                    Diagnostic(
                        code = DiagnosticCode.RACING_CAMERA_FOLLOW_MISMATCH,
                        severity = Severity.WARNING,
                        message =
                            "camera '${system.id}' follows actor '$followId' but racing " +
                                "'$racingId' player vehicle is bound to actor '$playerActorId' (D-06)",
                        location = "CameraSystem '${system.id}'",
                        suggestion =
                            "Remove the camera { } block to let racing() auto-emit a camera, or " +
                                "change camera.follow(...) to point at the bound player actor.",
                    )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers — registry / config map plumbing.
    //
    // gbkt-analysis main src cannot import RacingConfig / Vehicle / WaypointDef
    // directly (would form a module cycle). Reflection on the data-class
    // generated `getX()` accessors keeps the cross-module read read-only and
    // resilient to additive field changes in gbkt-genre-sport.
    // ─────────────────────────────────────────────────────────────────────────

    private fun resolvePlayerActorId(
        playerVehicleId: String?,
        registeredVehicles: Map<*, *>?,
    ): String? {
        if (playerVehicleId == null || registeredVehicles == null) return null
        val vehicle = registeredVehicles[playerVehicleId] ?: return null
        return resolveVehicleActorId(vehicle)
    }

    private fun resolveVehicleActorId(vehicle: Any): String? {
        val actorRef = readField(vehicle, "actorRef") ?: return null
        return readStringField(actorRef, "id")
    }

    private fun hasAnyAiSlots(racingConfig: Any): Boolean = readAiSlots(racingConfig).isNotEmpty()

    private fun readAiSlots(racingConfig: Any): List<Any> {
        @Suppress("UNCHECKED_CAST")
        return (readListField(racingConfig, "aiVehicles") as? List<Any>) ?: emptyList()
    }

    private fun readMapField(sys: GenericSystem, key: String): Map<*, *>? =
        sys.config[key] as? Map<*, *>

    /**
     * Reads a property off a Kotlin data class via the generated `getX()` Java accessor. Returns
     * null if the property does not exist (defensive against type drift) or if the receiver is
     * null.
     */
    private fun readField(receiver: Any?, name: String): Any? {
        if (receiver == null) return null
        val getterName =
            "get" +
                name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return try {
            val method = receiver.javaClass.getMethod(getterName)
            method.invoke(receiver)
        } catch (_: NoSuchMethodException) {
            null
        } catch (_: IllegalAccessException) {
            null
        } catch (_: java.lang.reflect.InvocationTargetException) {
            null
        }
    }

    private fun readStringField(receiver: Any?, name: String): String? =
        readField(receiver, name) as? String

    private fun readIntField(receiver: Any?, name: String): Int? = readField(receiver, name) as? Int

    private fun readListField(receiver: Any?, name: String): List<*>? =
        readField(receiver, name) as? List<*>

    /**
     * Recursively flattens a list of [ScriptOp]s into a single list (including nested ops inside
     * IfOp / WhileOp / ForOp / FadeOp / PoolForEachActive bodies). Delegates to the package-shared
     * [collectAllOps] helper in `ScriptOpTraversal.kt` so the racing pass walks frame blocks the
     * same way every other pass does.
     */
    private fun walkScriptOps(ops: List<ScriptOp>): List<ScriptOp> = collectAllOps(ops)
}
