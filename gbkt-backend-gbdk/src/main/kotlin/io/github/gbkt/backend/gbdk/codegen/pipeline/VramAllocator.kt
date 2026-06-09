/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

/**
 * Per-build sprite-VRAM tile-slot allocator.
 *
 * Hands out monotonically-increasing start indices into the sprite-VRAM tile range. One allocator
 * instance per [GBDKPipeline.generate] call: instantiated locally inside the unified loader
 * (`buildAllSpriteDataLoadStatements`) so the counter is scoped to a single build and cannot leak
 * across `generate()` invocations (T-10.1-08-A mitigation — per-build isolation by construction).
 *
 * ## Why this exists (CR-01 / D-08 / SEED-008)
 *
 * Replaces two parallel `var nextTile = 0` counters that previously lived in
 * [GBDKPipeline.buildSpriteDataLoadStatements] (actor sprites) and
 * [GBDKPipeline.buildMetaspriteTileDataLoadStatements] (metasprites). Both counters started at
 * `nextTile = 0` independently; concatenated into `main()` by [GBDKPipeline.buildMainFunction], the
 * result was two `set_sprite_data(0u, …)` calls when a game had BOTH actors and metasprites — the
 * second silently overwrote the first's tiles in VRAM with no compile-time or runtime warning.
 *
 * Latent in Phase 10's `metasprites` example (no actor sprites). Guaranteed to surface in Phase 12
 * (`platformer_template` — actors + metasprites + tilemap together) and any user game that mixes
 * both subsystems. See `.planning/seeds/SEED-008-metasprites-vram-collision-with-actors.md`.
 *
 * ## Scope
 *
 * Phase 10.1 ships the **per-build singleton** variant — one allocator per `generate()` call,
 * iterating actors-first then metasprites (Pitfall 8 — preserves Pong/Breakout/SimplePhysics
 * emission shape). Per-scene / per-bank allocation variants belong to Phase 11 (banks port) when
 * banked sprite tile loading becomes meaningful; the API surface is intentionally minimal here to
 * avoid over-designing the Phase 11 boundary (D-08 Claude's discretion).
 *
 * ## Visibility
 *
 * Internal codegen-only (D-13b). Not exposed to user DSL: end-users author games with `actor { }`
 * and `metasprite { }` and never reason about VRAM tile slots — the pipeline allocates on their
 * behalf. Promoting this class to `public` would surface a hardware-level concern at the DSL
 * boundary that conflicts with the framework's "Kotlin in, ROM out" contract.
 */
internal class VramAllocator {

    /** Cursor pointing at the next free VRAM tile slot. Monotonic, never decreases. */
    private var nextTile: Int = 0

    /**
     * Reserve [tileCount] contiguous sprite-VRAM tile slots and return the start index.
     *
     * `reserve(0)` is a no-op cursor read — returns the current cursor without advancing.
     * `reserve(negative)` rejects via [require] — a negative count would advance the cursor
     * backwards and silently corrupt subsequent VRAM layout.
     *
     * @param tileCount Number of contiguous tile slots to reserve. Must be `>= 0`.
     * @return The start index (inclusive) of the reserved range. Subsequent reserves continue from
     *   `start + tileCount`.
     * @throws IllegalArgumentException if [tileCount] is negative.
     */
    fun reserve(tileCount: Int): Int {
        require(tileCount >= 0) { "tileCount must be >= 0, got $tileCount" }
        val start = nextTile
        nextTile += tileCount
        return start
    }

    /**
     * Total tile slots reserved so far across all [reserve] calls. Useful for tests and
     * diagnostics; not consumed by codegen.
     */
    val tilesUsed: Int
        get() = nextTile
}
