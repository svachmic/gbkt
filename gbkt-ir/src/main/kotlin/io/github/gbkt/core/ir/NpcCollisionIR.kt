/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// NPC COLLISION IR TYPES
// Configures NPC-to-NPC collision groups, rules, and response behaviours.
// Consumed by GBDKSystemVisitor to generate per-rule AABB check functions.
// =============================================================================

/**
 * A named collision group.
 *
 * Actors are assigned to groups via [NpcCollisionConfig.groupIds]. Rules are defined between pairs
 * of groups via [CollisionRuleIR]. The implicit group [_DEFAULT_NPC_GROUP] is auto-created when
 * actors use the simple `collidesWithNpcs(true)` opt-in without an explicit group.
 *
 * @property id Unique identifier for this group (inferred from property name via delegate).
 */
data class CollisionGroupIR(val id: String) {
    companion object {
        /** Name of the auto-created implicit group for `collidesWithNpcs(true)` actors. */
        const val DEFAULT_NPC_GROUP = "_default_npc"
    }
}

/**
 * Response type for a [CollisionRuleIR] when two actors from the paired groups overlap.
 *
 * Determines what action the GBDK backend generates inside the collision check function.
 * - [OVERLAP]: AABB check fires — runs [CollisionRuleIR.onCollide] callback only. No movement
 *   modification.
 * - [BLOCK]: Stops the penetrating actor's movement (sets velocity to zero on the collision axis).
 * - [BOUNCE]: Reverses the velocity of the colliding actor (reflects direction).
 * - [PUSH]: Displaces both actors proportional to inverse mass: `dispA = massB / (massA + massB)`,
 *   `dispB = massA / (massA + massB)` (integer math).
 */
enum class CollisionResponse {
    /** AABB overlap fires — runs callback only. No movement change. */
    OVERLAP,
    /** Stops penetrating actor's movement on collision axis. */
    BLOCK,
    /** Reverses velocity of colliding actor. */
    BOUNCE,
    /**
     * Displaces both actors proportional to their inverse masses. Uses actor
     * [NpcCollisionConfig.mass] values for split calculation.
     */
    PUSH,
}

/**
 * Defines a collision rule between two collision groups.
 *
 * The GBDK backend generates a `check_collision_{groupA}_{groupB}()` function for each rule. That
 * function loops over all actors in groupA × groupB and performs AABB overlap checks.
 *
 * @property groupA ID of the first collision group.
 * @property groupB ID of the second collision group (may equal [groupA] for intra-group checks).
 * @property response Built-in response dispatched when overlap is detected.
 * @property interval Frame interval for the check — the generated function uses a static counter
 *   modulo [interval] to reduce CPU cost. Default 1 = check every frame.
 * @property onCollide Optional script ops executed when overlap is detected (in addition to the
 *   built-in [response] behaviour).
 */
data class CollisionRuleIR(
    val groupA: String,
    val groupB: String,
    val response: CollisionResponse = CollisionResponse.OVERLAP,
    val interval: Int = 1,
    val onCollide: List<ScriptOp> = emptyList(),
)

/**
 * Per-actor NPC collision configuration.
 *
 * Stored on [ActorIR.npcCollisionConfig]. Null means the actor does not participate in any NPC-NPC
 * collision check.
 *
 * @property groupIds Explicit collision group IDs this actor belongs to. Empty when the actor uses
 *   only the simple `collidesWithNpcs(true)` opt-in (in which case GameBuilder auto-assigns to
 *   [CollisionGroupIR.DEFAULT_NPC_GROUP]).
 * @property collidesWithNpcs When true, the actor opts into the implicit `_default_npc` group
 *   unless it already has explicit [groupIds]. Ignored at build time if [groupIds] is non-empty.
 * @property mass Actor mass used for [CollisionResponse.PUSH] displacement ratio. Higher mass →
 *   smaller displacement. Default 1.
 */
data class NpcCollisionConfig(
    val groupIds: List<String> = emptyList(),
    val collidesWithNpcs: Boolean = false,
    val mass: Int = 1,
)
