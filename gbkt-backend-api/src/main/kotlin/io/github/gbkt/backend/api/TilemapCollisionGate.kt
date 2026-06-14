/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.api

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem

/**
 * Path C detection for the tilemap-collision predicate shared between
 * [io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline] and
 * [io.github.gbkt.genre.platformer.codegen.PlatformerVisitor].
 *
 * **Path C:** a [GenericSystem] with `config["type"] == "tilemap_collision"` is present. This is
 * the path added in Phase 12.1 when the `tilemapCollision { }` DSL builder is used (the canonical
 * symbol-binding home for player-position variables).
 *
 * Both callers ([io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline] and
 * [io.github.gbkt.genre.platformer.codegen.PlatformerVisitor]) call this function as their first
 * (early-return) check. Each caller then handles **Path A** (the typed
 * `PlatformerPhysicsConfig.solidThreshold` check) locally, because `gbkt-backend-gbdk` and
 * `gbkt-genre-platformer` each have their own access strategy for that type:
 * - `GBDKPipeline` uses Java reflection (no compile-time dependency on the genre module).
 * - `PlatformerVisitor` uses a direct cast (has compile-time access via the genre module).
 *
 * Consolidating Path C here eliminates the lockstep risk for the most-likely-to-drift path (new
 * games using `tilemapCollision { }` rather than the legacy `platformerPhysics` path).
 *
 * See SEED-022-tilemap-collision-predicate-consolidation.md.
 */
fun gameUsesTilemapCollisionPathC(gameIR: GameIR): Boolean =
    gameIR.systems.filterIsInstance<GenericSystem>().any { sys ->
        (sys.config["type"] as? String) == "tilemap_collision"
    }
