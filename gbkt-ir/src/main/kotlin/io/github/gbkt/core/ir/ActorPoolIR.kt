/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// ACTOR POOL IR
// Sprite-lifecycle-aware entity pooling for repeated entity types (bullets,
// bricks, particles). Distinct from IRCollPool (generic data structure pool).
// =============================================================================

/**
 * Strategy for handling pool spawn attempts when all slots are occupied.
 * - [SILENT_NOOP]: spawn call returns 0xFF and does nothing — caller should check the return value
 *   before using the slot index.
 * - [RECYCLE_OLDEST]: the oldest active slot is forcefully destroyed and reused for the new entity.
 *   Useful for bullet pools where the oldest bullets are least relevant.
 */
enum class PoolOverflowStrategy {
    /** Spawn returns 0xFF and has no effect when the pool is full. */
    SILENT_NOOP,

    /** The oldest active slot is recycled when the pool is full. */
    RECYCLE_OLDEST,
}

/**
 * Configuration for an actor pool.
 *
 * @param maxSize Maximum number of simultaneously active entities in the pool.
 * @param overflowStrategy How to handle spawn requests when the pool is full.
 */
data class ActorPoolConfig(
    val maxSize: Int,
    val overflowStrategy: PoolOverflowStrategy = PoolOverflowStrategy.SILENT_NOOP,
)

/**
 * A per-instance property stored as a parallel array alongside the pool's active bitmap.
 *
 * Each pool entity gets its own slot in a `UINT8 _pool_<id>_<name>[max]` or `INT8
 * _pool_<id>_<name>[max]` global array. This allows entities to carry custom data (e.g. HP,
 * direction, type) without requiring separate global variable declarations.
 *
 * Declared via `u8Prop()` / `i8Prop()` inside an actor block that is used as a pool template.
 *
 * @param name Property name — used as the array suffix (e.g. `hp` → `_pool_bullets_hp[max]`).
 * @param type Element type — [VarType.U8] for `UINT8`, [VarType.I8] for `INT8`.
 */
data class PoolInstanceProperty(val name: String, val type: VarType)

/**
 * IR node representing an actor pool declaration.
 *
 * Actor pools manage OAM slot lifecycle for repeated entity types (e.g. bullet pools, brick grids,
 * particle systems). Each pool reserves [ActorPoolConfig.maxSize] consecutive OAM slots and
 * provides spawn/destroy operations.
 *
 * Generated C:
 * - `UINT8 _pool_<id>_active[max]` — per-slot active bitmap (1=active, 0=free)
 * - `UINT8 _pool_<id>_oam_base` — OAM start slot assigned at init
 * - `UINT8 _pool_<id>_<prop>[max]` — one parallel array per [PoolInstanceProperty]
 * - `pool_<id>_init()` — pre-allocates OAM slots on game init
 * - `pool_<id>_spawn(x, y)` — finds free slot, positions sprite, returns slot index
 * - `pool_<id>_destroy(slot)` — runs death callback (if any), marks slot inactive, hides sprite
 * - `pool_<id>_active_count()` — returns count of active slots
 *
 * @param id Unique identifier for this pool (inferred from the Kotlin property name).
 * @param actorTemplateId ID of the [ActorIR] used as the sprite template for all pool slots.
 * @param config Pool capacity and overflow behavior.
 * @param instanceProperties Per-slot parallel arrays for custom entity data.
 * @param deathCallback Script ops to execute before releasing a slot in pool_<id>_destroy().
 */
data class ActorPoolIR(
    val id: String,
    val actorTemplateId: String,
    val config: ActorPoolConfig,
    val instanceProperties: List<PoolInstanceProperty> = emptyList(),
    val deathCallback: List<ScriptOp> = emptyList(),
)
