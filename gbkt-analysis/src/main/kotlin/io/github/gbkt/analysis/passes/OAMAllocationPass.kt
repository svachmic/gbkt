/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.AnalysisPass
import io.github.gbkt.analysis.Diagnostic
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.Severity
import io.github.gbkt.core.ir.OAMSlot

/**
 * Analysis pass that assigns OAM (Object Attribute Memory) sprite slots to actors.
 *
 * Allocation model:
 * - Actors with a non-null [ActorIR.sprite] receive OAM tile slot numbers, not actor indices.
 * - The first tile slot for each actor accounts for all preceding actors' tile counts: a 2-tile
 *   actor (4x16 sprite) occupies two consecutive OAM slots, so the next actor's first slot is
 *   offset by 2, not 1.
 * - Actors without sprites are skipped — they require no OAM entry.
 *
 * Overflow detection:
 * - If total sprite-bearing actors exceed [AnalysisConfig.oamErrorThreshold], a hard error is
 *   produced and the pipeline stops.
 * - If total sprite-bearing actors exceed [AnalysisConfig.oamWarningThreshold], an advisory WARNING
 *   is produced.
 *
 * Scanline density advisory:
 * - For each scene, if the count of active sprite actors exceeds [SpriteSpec.maxPerScanline], a
 *   WARNING advisory is added. This is NOT an error — per Game Boy hardware, flickering only occurs
 *   if all actors happen to be clustered on the same scanline at runtime.
 *
 * Outputs: Populates [PassContext.oamAssignments] with [OAMSlot] entries keyed by actor ID.
 */
class OAMAllocationPass : AnalysisPass {

    override fun run(context: PassContext): PassResult {
        val game = context.game
        val diagnostics = mutableListOf<Diagnostic>()

        // 1. Build OAM slot map: actor ID → OAMSlot(firstTileSlot)
        // Each actor with a sprite occupies (tilesWide * tilesHigh) OAM entries.
        // The first OAM tile slot for each actor is the cumulative total of all preceding actors'
        // tile counts — not just the actor index. Multi-tile actors (e.g. 16x16 sprite = 4 tiles)
        // must leave room in the OAM table for all their tiles.
        val spriteActors = game.actors.filter { it.sprite != null }
        val oamAssignments = mutableMapOf<String, OAMSlot>()
        var nextOamSlot = 0
        for (actor in spriteActors) {
            oamAssignments[actor.id] = OAMSlot(nextOamSlot)
            val sprite = actor.sprite!!
            val tilesWide = (sprite.size.width + 7) / 8
            val tilesHigh = (sprite.size.height + 7) / 8
            nextOamSlot += tilesWide * tilesHigh
        }
        val totalOamEntries = nextOamSlot

        // 2. Check hard overflow first — profile max sprites (e.g. 40)
        val maxSprites = context.profile.sprites.maxSprites
        if (totalOamEntries > maxSprites) {
            return PassResult.Failed(
                listOf(
                    Diagnostic(
                        id = "ANLZ-04",
                        severity = Severity.ERROR,
                        message =
                            "Game needs $totalOamEntries OAM entries for ${spriteActors.size} sprite-bearing " +
                                "actors but the hardware OAM limit is $maxSprites. Remove or shrink " +
                                "sprite actors (16x16 sprites use 4 OAM entries each).",
                        suggestion =
                            "Consider using a sprite pool pattern to share OAM slots between " +
                                "actors that are never active simultaneously, or use smaller sprites.",
                    )
                )
            )
        }

        // 3. Warning if above configurable warning threshold
        if (totalOamEntries > context.config.oamWarningThreshold) {
            diagnostics.add(
                Diagnostic(
                    id = "ANLZ-04",
                    severity = Severity.WARNING,
                    message =
                        "Game needs $totalOamEntries OAM entries for ${spriteActors.size} sprite-bearing " +
                            "actors — approaching the $maxSprites OAM limit " +
                            "(warning threshold: ${context.config.oamWarningThreshold}).",
                    suggestion = "Reserve some OAM slots for HUD overlays or particle effects.",
                )
            )
        }

        // 4. Scanline density advisory: per-scene check
        val maxPerScanline = context.profile.sprites.maxPerScanline
        for (scene in game.scenes) {
            val sceneSpritActorCount =
                game.actors.count { actor -> actor.sprite != null && actor.id in scene.actorIds }
            if (sceneSpritActorCount > maxPerScanline) {
                diagnostics.add(
                    Diagnostic(
                        id = "ANLZ-04",
                        severity = Severity.WARNING,
                        message =
                            "Scene '${scene.id}' has $sceneSpritActorCount sprite actors — may " +
                                "exceed $maxPerScanline/scanline limit if clustered. " +
                                "Sprites that share a scanline may flicker on hardware.",
                        location = "scene '${scene.id}'",
                        suggestion =
                            "Spread actors across different Y positions or implement OAM flicker " +
                                "rotation to distribute sprites evenly across scanlines.",
                    )
                )
            }
        }

        return PassResult.Success(
            context.copy(
                oamAssignments = context.oamAssignments + oamAssignments,
                diagnostics = context.diagnostics + diagnostics,
            )
        )
    }
}
