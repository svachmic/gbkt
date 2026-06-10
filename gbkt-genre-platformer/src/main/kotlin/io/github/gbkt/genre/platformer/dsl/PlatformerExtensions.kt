/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.dsl

import io.github.gbkt.core.dsl.AssignableVar
import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.GameBuilderContext
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.SceneBuilder
import io.github.gbkt.core.dsl.SceneRef
import io.github.gbkt.core.dsl.ZoneBuilder
import io.github.gbkt.core.dsl.buttons
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.GenericSystem
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// PLATFORMER DSL EXTENSIONS ON GameBuilder
// =============================================================================
//
// These functions extend GameBuilder with platformer-specific DSL constructs.
// Pattern mirrors gbkt-genre-rpg/RpgExtensions.kt:
//   - gbkt-genre-platformer depends on gbkt-lang (which api-exposes gbkt-ir)
//   - GameBuilder does NOT know about platformer types
//   - Platformer builders produce CORE IR types (GenericSystem) — no new sealed subtypes
//
// =============================================================================

/**
 * Configures and registers the platformer physics system.
 *
 * Produces a [GenericSystem] with type `"platformer_physics"` and a full
 * [io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig] in its config map.
 *
 * ```kotlin
 * platformerPhysics {
 *     gravity(2)
 *     jumpForce(8)
 *     coyoteTime(6)
 *     jumpBuffer(8)
 *     wallJump { slideSpeed(1); iFrames(8) }
 * }
 * ```
 *
 * Variable-height jump is enabled by default. Call `fixedJump()` to disable.
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique system identifier (default "physics").
 * @param block Configuration block executed against a [PlatformerPhysicsBuilder].
 */
fun GameBuilder.platformerPhysics(
    id: String = "physics",
    block: PlatformerPhysicsBuilder.() -> Unit,
): GenericSystem {
    val builder = PlatformerPhysicsBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

/**
 * Configures and registers the platformer camera system.
 *
 * Produces a [GenericSystem] with type `"platformer_camera"` and a
 * [io.github.gbkt.genre.platformer.domain.PlatformerCameraConfig] in its config map.
 *
 * ```kotlin
 * platformerCamera {
 *     smoothFollow()
 *     deadZone(x = 8, y = 16)
 *     horizontal()
 *     parallax("bg_sky") { speedX(20) }
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique system identifier (default "camera").
 * @param block Configuration block executed against a [PlatformerCameraBuilder].
 */
fun GameBuilder.platformerCamera(
    id: String = "camera",
    block: PlatformerCameraBuilder.() -> Unit,
): GenericSystem {
    val builder = PlatformerCameraBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

/**
 * Defines and registers a named platform definition.
 *
 * Produces a [GenericSystem] with type `"platformer_platform"` and a
 * [io.github.gbkt.genre.platformer.domain.PlatformDef] in its config map.
 *
 * ```kotlin
 * platform("moving_floor") {
 *     type(PlatformType.MOVING)
 *     moveSpeed(2)
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique platform identifier.
 * @param block Configuration block executed against a [PlatformDefBuilder].
 */
fun GameBuilder.platform(id: String, block: PlatformDefBuilder.() -> Unit): GenericSystem {
    val builder = PlatformDefBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

/**
 * Defines and registers a hazard tile definition.
 *
 * Produces a [GenericSystem] with type `"platformer_hazard"` and a
 * [io.github.gbkt.genre.platformer.domain.HazardDef] in its config map.
 *
 * ```kotlin
 * hazard("spikes") {
 *     tileId(42)
 *     damage(5)
 * }
 * hazard("pit") { tileId(0); instantDeath() }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique hazard identifier.
 * @param block Configuration block executed against a [HazardDefBuilder].
 */
fun GameBuilder.hazard(id: String, block: HazardDefBuilder.() -> Unit): GenericSystem {
    val builder = HazardDefBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

/**
 * Defines and registers a level-completion goal zone.
 *
 * Produces a [GenericSystem] with type `"platformer_goal"` and a
 * [io.github.gbkt.genre.platformer.domain.GoalZoneDef] in its config map.
 *
 * ```kotlin
 * goalZone("exit") {
 *     position(x = 200, y = 50)
 *     size(width = 16, height = 32)
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique goal-zone identifier.
 * @param block Configuration block executed against a [GoalZoneBuilder].
 */
fun GameBuilder.goalZone(id: String, block: GoalZoneBuilder.() -> Unit): GenericSystem {
    val builder = GoalZoneBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

/**
 * Defines and registers a collectible item definition.
 *
 * Produces a [GenericSystem] with type `"platformer_collectible"` and a
 * [io.github.gbkt.genre.platformer.domain.CollectibleDef] in its config map. Acts as a facade over
 * the shared engine PickupDef; codegen integration is wired in Plan 10.
 *
 * ```kotlin
 * collectible("gold_coin") {
 *     type(CollectibleType.COIN)
 *     value(10)
 *     tileId(5)
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique collectible identifier.
 * @param block Configuration block executed against a [CollectibleDefBuilder].
 */
fun GameBuilder.collectible(id: String, block: CollectibleDefBuilder.() -> Unit): GenericSystem {
    val builder = CollectibleDefBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

/**
 * Configures and registers ladder tile settings.
 *
 * Produces a [GenericSystem] with type `"platformer_ladder"` and a
 * [io.github.gbkt.genre.platformer.domain.LadderConfig] in its config map.
 *
 * ```kotlin
 * ladder {
 *     climbSpeed(2)
 *     tileId(15)
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique identifier for this ladder configuration (default "ladder").
 * @param block Configuration block executed against a [LadderConfigBuilder].
 */
fun GameBuilder.ladder(
    id: String = "ladder",
    block: LadderConfigBuilder.() -> Unit,
): GenericSystem {
    val builder = LadderConfigBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

// =============================================================================
// PER-ZONE PLATFORMER PHYSICS OVERRIDE (D-12)
// =============================================================================
//
// Re-entrant `platformerPhysics { }` block on ZoneBuilder. Per-level overrides
// SHADOW the game-level platformerPhysics defaults — fields SET in the block
// land in the override map; fields NOT SET fall through to the game-level
// config at codegen time (Plan 12-08 PlatformerVisitor reads the keys back).
//
// Storage: `ZoneIR.platformerPhysicsOverride: Map<String, Any>?` (added in
// Plan 12-06 as an opaque payload so gbkt-ir stays a leaf module). Keys use
// the PlatformerPhysicsConfig field names verbatim:
//   "gravity", "jumpForce", "terminalVelocity", "solidThreshold",
//   "jumpHoldMaxFrames".
//
// Visibility note: ZoneBuilder.setPlatformerPhysicsOverride was widened from
// `internal` to `public` in this plan (12-07). Kotlin's `internal` is
// Gradle-module-scoped, and gbkt-genre-platformer is a sibling module to
// gbkt-lang — the cross-module call below requires public visibility. The
// payload remains opaque `Map<String, Any>`, so no gbkt-genre-platformer
// type leaks into gbkt-lang.
//
// =============================================================================

/**
 * Re-entrant per-zone platformer-physics override (D-12). Fields set in this block SHADOW the
 * game-level [platformerPhysics] config for the active zone. Fields NOT set in this block fall
 * through to the game-level defaults.
 *
 * ```kotlin
 * val world2Area1Zone by zone {
 *     tileset(asset("res/graphics/world2-tileset.png"))
 *     tiles(asset("res/graphics/world2-area1.png"))
 *     platformerPhysics {
 *         gravity(3)        // heavier in world 2
 *         solidThreshold(68)
 *     }
 * }
 * ```
 *
 * Calling the block TWICE on the same zone REPLACES the previous override (does not merge) — the
 * second invocation wins. Calling with an empty block stores an EMPTY map (distinct from `null`,
 * which means the block was never called and the zone inherits all game-level defaults).
 *
 * **Design constraint:** NO new sealed IR subtypes are created — the override is stored as an
 * opaque `Map<String, Any>` payload on `ZoneIR`.
 *
 * @param block Configuration block executed against an [OverrideTrackingPhysicsBuilder] that
 *   records which fields were explicitly set.
 */
fun ZoneBuilder.platformerPhysics(block: PlatformerPhysicsBuilder.() -> Unit) {
    // Use a marker subclass that exposes which fields were set so shadowing
    // semantics are preserved (unset fields are ABSENT from the map, NOT
    // present-with-default-value).
    val builder = OverrideTrackingPhysicsBuilder()
    builder.block()
    setPlatformerPhysicsOverride(builder.toOverrideMap())
}

/**
 * [PlatformerPhysicsBuilder] subclass that tracks WHICH setters were invoked, so per-level
 * overrides can shadow only the explicitly-set fields. Keys in the resulting map use the
 * `PlatformerPhysicsConfig` field names verbatim — Plan 12-08's `PlatformerVisitor` reads them back
 * via `Int` cast at codegen time.
 *
 * If a [PlatformerPhysicsBuilder] method is NOT overridden here, its set-call falls through to
 * `super` and is NOT recorded in the override map (it becomes a game-level-only setting —
 * intentional for fields like `wallJump` / `variableHeightJump` which are not currently per-level
 * overridable).
 *
 * Kept `internal` so the helper class is invisible to end-users (they call `platformerPhysics { }`,
 * not the tracking builder directly).
 */
internal class OverrideTrackingPhysicsBuilder : PlatformerPhysicsBuilder() {
    private val setFields = mutableMapOf<String, Any>()

    override fun gravity(value: Int) {
        super.gravity(value)
        setFields["gravity"] = value
    }

    override fun jumpForce(value: Int) {
        super.jumpForce(value)
        setFields["jumpForce"] = value
    }

    override fun terminalVelocity(value: Int) {
        super.terminalVelocity(value)
        setFields["terminalVelocity"] = value
    }

    override fun solidThreshold(value: Int) {
        super.solidThreshold(value)
        setFields["solidThreshold"] = value
    }

    override fun jumpHold(maxFrames: Int) {
        super.jumpHold(maxFrames)
        setFields["jumpHoldMaxFrames"] = maxFrames
    }

    /** Snapshot of the set fields as an immutable map. */
    fun toOverrideMap(): Map<String, Any> = setFields.toMap()
}

// =============================================================================
// PLATFORMER INPUT EXTENSIONS (Phase 12.3 Plan 01 — R1 game-level + zone-level)
// =============================================================================
//
// Mirrors `platformerPhysics` extension placement verbatim (D-01). Game-level
// registers a `GenericSystem(type="platformer_input")`; zone-level uses an
// `OverrideTrackingInputBuilder` to write only explicitly-set numeric fields
// into `ZoneIR.platformerInputOverride` (Phase 12.3 R1 absent-vs-default
// contract — RESEARCH §Pattern 2).
//
// AssignableVar binders (walkFrameIdx / threeFrameCounter) are intentionally
// game-level only — they're NOT overridden by `OverrideTrackingInputBuilder`
// (D-03 / L-2.2 — per-zone shadowing of binder names is not semantically
// meaningful). Calling the binders inside a zone-level `platformerInput { }`
// block is silently ignored at the override-map level (the call still flows
// through to the underlying `PlatformerInputBuilder` super-method, which
// records the name; but `OverrideTrackingInputBuilder` does not track it).
//
// =============================================================================

/**
 * Configures and registers the platformer input + walk-cycle system at game scope (Phase 12.3 R1).
 *
 * Produces a [GenericSystem] with type `"platformer_input"` carrying numeric defaults
 * + captured `AssignableVar.name` references. Wave 2 (Plans 12.3-02 / -04 / -06 / -08) extends
 *   `PlatformerVisitor` + `MetaspriteVisitor` to read this system's config map and emit input →
 *   playerVx, camera-update call, metasprite camera-offset, and walk-cycle increments.
 *
 * ```kotlin
 * game("PlatformerTemplate") {
 *     var walkFrameIdx by u8Var(0)
 *     var threeFrameCounter by u8Var(0)
 *     platformerInput {
 *         walkSpeed(128)
 *         friction(8)
 *         airFriction(0)
 *         walkFrameCount(3)
 *         cyclePeriod(6)
 *         walkFrameIdx(walkFrameIdx)        // AssignableVar binder
 *         threeFrameCounter(threeFrameCounter)
 *     }
 * }
 * ```
 *
 * Mirrors [platformerPhysics] verbatim (D-01). NO new sealed IR subtypes are created.
 *
 * @param id Unique system identifier (default "input"; the type-tag `"platformer_input"` is the
 *   discriminator, not the id).
 * @param block Configuration block executed against a [PlatformerInputBuilder].
 */
fun GameBuilder.platformerInput(
    id: String = "input",
    block: PlatformerInputBuilder.() -> Unit,
): GenericSystem {
    val builder = PlatformerInputBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

/**
 * Re-entrant per-zone platformer-input override (Phase 12.3 R1). Numeric fields set in this block
 * SHADOW the game-level [platformerInput] config for the active zone. Fields NOT set fall through
 * to the game-level defaults at codegen time.
 *
 * ```kotlin
 * val speedRun by zone {
 *     tileset(asset("res/graphics/tileset.png"))
 *     platformerInput {
 *         walkSpeed(192)  // faster on this level
 *     }
 * }
 * ```
 *
 * Calling the block TWICE on the same zone REPLACES the previous override (does NOT merge) — the
 * second invocation wins. Calling with an empty block stores an EMPTY map (distinct from `null`,
 * which means the block was never called and the zone inherits all game-level defaults).
 *
 * **AssignableVar binders are GAME-LEVEL ONLY** (D-03 / L-2.2). Calling `walkFrameIdx(...)` or
 * `threeFrameCounter(...)` inside a zone-level block is silently ignored at the override-map level
 * — [OverrideTrackingInputBuilder] does NOT override those binder methods, so the calls record into
 * the base builder but do not appear in the override map. Per-zone shadowing of binder names is not
 * semantically meaningful.
 *
 * **Design constraint:** NO new sealed IR subtypes — override is an opaque `Map<String, Any>`
 * payload on `ZoneIR`.
 *
 * @param block Configuration block executed against an [OverrideTrackingInputBuilder] that records
 *   which numeric fields were explicitly set.
 */
fun ZoneBuilder.platformerInput(block: PlatformerInputBuilder.() -> Unit) {
    val builder = OverrideTrackingInputBuilder()
    builder.block()
    setPlatformerInputOverride(builder.toOverrideMap())
}

/**
 * [PlatformerInputBuilder] subclass that tracks WHICH numeric setters were invoked, so per-zone
 * overrides shadow only the explicitly-set fields. Keys in the resulting map use the
 * `PlatformerInputConfig` field names verbatim — Wave 2 plans read them back via `Int` cast at
 * codegen time.
 *
 * Overrides ONLY the 5 numeric setters (walkSpeed / friction / airFriction / walkFrameCount /
 * cyclePeriod). AssignableVar binders (walkFrameIdx / threeFrameCounter) are intentionally NOT
 * overridden — they're game-level only (D-03 / L-2.2). Calls to those binders inside a zone-level
 * block flow through to the base [PlatformerInputBuilder] super-method (which records the name on
 * the builder instance) but do NOT land in the override map — matching the RESEARCH §Pattern 2
 * absent-vs-default contract.
 *
 * Kept `internal` so the helper class is invisible to end-users (they call `platformerInput { }`,
 * not the tracking builder directly).
 */
internal class OverrideTrackingInputBuilder : PlatformerInputBuilder("input-override") {
    private val setFields = mutableMapOf<String, Any>()

    override fun walkSpeed(value: Int) {
        super.walkSpeed(value)
        setFields["walkSpeed"] = value
    }

    override fun friction(value: Int) {
        super.friction(value)
        setFields["friction"] = value
    }

    override fun airFriction(value: Int) {
        super.airFriction(value)
        setFields["airFriction"] = value
    }

    override fun walkFrameCount(value: Int) {
        super.walkFrameCount(value)
        setFields["walkFrameCount"] = value
    }

    override fun cyclePeriod(value: Int) {
        super.cyclePeriod(value)
        setFields["cyclePeriod"] = value
    }

    /** Snapshot of the set numeric fields as an immutable map. */
    fun toOverrideMap(): Map<String, Any> = setFields.toMap()
}

// =============================================================================
// TILEMAP-COLLISION CONFIG (Phase 12.1 Plan 05 — Defects 4 + 5 DSL substrate)
// =============================================================================
//
// Decouples per-game tilemap-physics symbol binding from the `platformerPhysics`
// builder (D-claude-4 locked separation). Storage shape: a `GenericSystem` with
// `config["type"] = "tilemap_collision"` whose keys carry the user-DSL property
// names (posXVar/posYVar/vxVar/vyVar/groundedVar) + the hitbox rect +
// solidThreshold.
//
// PlatformerVisitor (Plan 12.1-06) reads these config keys via reflective
// `config["posXVar"] as? String` lookups — the same idiom established for
// `physicsConfig.solidThreshold`. GBDKPipeline.gameUsesTilemapCollision is
// extended in this plan to fire when this system is present (Path C, in
// addition to existing Path A for `platformer_physics.solidThreshold` and
// Path B for per-zone `platformerPhysicsOverride.solidThreshold`).
//
// Why a separate builder (D-claude-4):
//   - `platformerPhysics` already carries a `solidThreshold` field for backward
//     compatibility — Path A still fires for games that haven't migrated.
//   - The new builder is the canonical home for player-symbol binding (no
//     magic strings, per feedback_no_magic_strings.md). Once Plan 12.1-06
//     ships and the visitor rewrites to the user-DSL names, new games SHOULD
//     prefer `tilemapCollision { }` over packing collision config into
//     `platformerPhysics { }`.
//
// =============================================================================

/**
 * Configures and registers the per-game tilemap-collision substrate.
 *
 * Captures the user-DSL property names for player position, velocity, and grounded flag — flowing
 * them through to the visitor (Plan 12.1-06) so the tilemap-physics code path emits
 * `_<userPropertyName>` references instead of the legacy `_player_x` / `_player_y` / `_grounded`
 * magic strings.
 *
 * ```kotlin
 * var playerX by i16Var(80 shl 4)
 * var playerY by i16Var(72 shl 4)
 * var playerVx by i8Var(0)
 * var playerVy by i16Var(0)
 * var grounded by u8Var(0)
 *
 * tilemapCollision {
 *     position(playerX, playerY)
 *     velocity(playerVx, playerVy)
 *     grounded(grounded)
 *     hitbox(0, 0, 8, 24)
 *     solidThreshold(17)
 * }
 * ```
 *
 * Produces a [GenericSystem] with config keys:
 * - `"type"` → `"tilemap_collision"`
 * - `"posXVar"` / `"posYVar"` → user-DSL property names (from `AssignableVar.name`)
 * - `"vxVar"` / `"vyVar"` → user-DSL velocity property names
 * - `"groundedVar"` → user-DSL grounded property name
 * - `"hitboxX"` / `"hitboxY"` / `"hitboxW"` / `"hitboxH"` → hitbox rect (pixels)
 * - `"solidThreshold"` → tile index threshold for solid vs. non-solid tiles
 *
 * **Coexistence with `platformerPhysics { solidThreshold(N) }`:** both can be registered
 * side-by-side. `gameUsesTilemapCollision` ORs all three paths (A:
 * platformer_physics.solidThreshold, B: per-zone override, C: this system), so games that haven't
 * migrated continue to work unchanged.
 *
 * **Design constraint:** NO new sealed IR subtypes — config is an opaque `Map<String, Any?>`
 * payload on `GenericSystem`.
 *
 * @param id Unique system identifier (default `"tilemap_collision"`).
 * @param block Configuration block executed against a [TilemapCollisionBuilder].
 */
fun GameBuilder.tilemapCollision(
    id: String = "tilemap_collision",
    block: TilemapCollisionBuilder.() -> Unit,
): GenericSystem {
    val builder = TilemapCollisionBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

/**
 * Builder for the tilemap-collision substrate (Plan 12.1-05).
 *
 * Captures per-game tilemap-physics symbol binding + hitbox + solidThreshold via 5 setters;
 * produces a [GenericSystem] read by `PlatformerVisitor` (Plan 12.1-06) and gated by
 * `GBDKPipeline.gameUsesTilemapCollision` Path C (this plan's Task 2).
 *
 * Setters store into a flat `Map<String, Any?>` under the config keys documented on
 * [tilemapCollision]. The visitor reads each key via reflective `config["<key>"] as? String / Int`
 * lookups (RESEARCH §Risks #6 — established pattern for opaque genre-config inspection).
 *
 * @param id Unique system identifier.
 */
@GbktDsl
class TilemapCollisionBuilder(val id: String) {
    private var posXVar: String? = null
    private var posYVar: String? = null
    private var vxVar: String? = null
    private var vyVar: String? = null
    private var groundedVar: String? = null
    private var hitboxX: Int = 0
    private var hitboxY: Int = 0
    private var hitboxW: Int = 8
    private var hitboxH: Int = 24
    private var solidThreshold: Int = 17

    /**
     * Binds the player position to user-DSL [AssignableVar]s. The variable names
     * (`AssignableVar.name`, inferred from the Kotlin property name via `provideDelegate`) are
     * captured into `posXVar` / `posYVar` and flow through to `PlatformerVisitor` (Plan 12.1-06) as
     * `_<propertyName>` symbol references in the tilemap-physics emission.
     *
     * Per `feedback_no_magic_strings.md`: this binder takes [AssignableVar] (not a `String`), so
     * the binding is type-safe and the symbol contract is enforced by the compiler.
     */
    fun position(x: AssignableVar, y: AssignableVar) {
        posXVar = x.name
        posYVar = y.name
    }

    /**
     * Binds the player velocity components to user-DSL [AssignableVar]s. The names flow through to
     * `_<propertyName>` references in the tilemap-physics emission (Plan 12.1-06).
     *
     * Per RESEARCH §D-claude-1: `vy` MUST be backed by an `i16Var` (must hold the -800 jump-init
     * value); `vx` is typically `i8Var`. The builder does not enforce the type — that's a user-DSL
     * constraint caught at codegen.
     */
    fun velocity(vx: AssignableVar, vy: AssignableVar) {
        vxVar = vx.name
        vyVar = vy.name
    }

    /**
     * Binds the grounded flag to a user-DSL [AssignableVar]. The flag is set by the tilemap-physics
     * path when the player lands on a solid tile and cleared when airborne. Closes the `_grounded`
     * portion of Defect 4 per RESEARCH §D-claude-1 static-lock: the visitor references bare
     * `_grounded` at lines 610/631/672/918 without declaring it — this DSL call provides the
     * symbol.
     */
    fun grounded(g: AssignableVar) {
        groundedVar = g.name
    }

    /**
     * Sets the player hitbox rect relative to the position origin (pixels). Used by the visitor's
     * 5-point bounding-box probe (Phase 12 D-12b).
     *
     * Default: `(0, 0, 8, 24)` — matches a 1-tile-wide, 3-tile-tall player (the platformer_template
     * reference sprite shape).
     */
    fun hitbox(x: Int, y: Int, w: Int, h: Int) {
        hitboxX = x
        hitboxY = y
        hitboxW = w
        hitboxH = h
    }

    /**
     * Sets the tilemap solid-tile threshold. Tiles with index `< value` are treated as non-solid;
     * tiles with index `>= value` are solid in the tilemap-collision codegen path (Plan 12-08 /
     * 12-11 / 12.1-06).
     *
     * Coexists with [PlatformerPhysicsBuilder.solidThreshold] — both Path A (platformerPhysics) and
     * Path C (this builder) can carry a threshold; the visitor uses whichever fires first in the
     * predicate.
     */
    fun solidThreshold(v: Int) {
        solidThreshold = v
    }

    /**
     * Builds a [GenericSystem] with type `"tilemap_collision"` containing the captured config keys.
     *
     * The config map carries `null` for any unset symbol binding — the visitor's `as? String`
     * lookups degrade gracefully (no synthesis if the binding is absent, matching the bound-var
     * path's existing shape in `MetaspriteVisitor.lowerMoveMetasprite`).
     */
    fun build(): GenericSystem {
        // Build the config as a `Map<String, Any>` (GenericSystem.config requires
        // non-nullable values). Unset symbol-binding slots are OMITTED from the
        // map — the visitor's reflective `config["posXVar"] as? String` returns
        // null when absent, matching the bound-var fallback shape established in
        // `MetaspriteVisitor.lowerMoveMetasprite`.
        val configBuilder = mutableMapOf<String, Any>("type" to "tilemap_collision")
        posXVar?.let { configBuilder["posXVar"] = it }
        posYVar?.let { configBuilder["posYVar"] = it }
        vxVar?.let { configBuilder["vxVar"] = it }
        vyVar?.let { configBuilder["vyVar"] = it }
        groundedVar?.let { configBuilder["groundedVar"] = it }
        configBuilder["hitboxX"] = hitboxX
        configBuilder["hitboxY"] = hitboxY
        configBuilder["hitboxW"] = hitboxW
        configBuilder["hitboxH"] = hitboxH
        configBuilder["solidThreshold"] = solidThreshold
        return GenericSystem(id = id, config = configBuilder.toMap())
    }
}

// =============================================================================
// levelCardScene — Plan 12.6-04 (DEFECT-1 fix at the DSL tier)
//                  Phase 13.5 Plan 06 (screen() + bindCurrentLevel() migration)
// =============================================================================
//
// `levelCardScene { }` is a delegate-pattern platformer-genre DSL helper that
// owns the GBDK-platformer-template NextLevel card lifecycle:
//   (a) on enter — the author-supplied screen(asset(...)) call in userBlocks
//       triggers the SceneVisitor screenMode superset (hide sprites, scroll
//       reset, BG clear, centered tilemap placement).
//   (b) on frame — listens for buttons.start.pressed,
//   (c) on Start-press — emits bindCurrentLevel() (typed BindCurrentLevel IR)
//       THEN navigates to the declared gameplay scene.
//
// Closes Phase 12.6 DEFECT-1: the show-card → wait-for-Start → setup →
// navigate sequence runs across multiple frames with vblanks between, so the
// card art renders before the new level's tilemap stomps VRAM (which is the
// same-frame race the trimmed main-loop guard exposed in Plan 12.6-02).
//
// Phase 13.5 Plan 06: migrated onto typed primitives — bindCurrentLevel()
// (Req #17) and screen() (Req #18). The centered-draw ceremony is absorbed
// by SceneVisitor's screenMode superset path, triggered when the author
// calls screen(asset(...)) inside levelCardScene { }.
//
// Reference behavior: gbdk/examples/cross-platform/platformer_template/src/
// player.c lines 44–82.

/**
 * Builder for a level-card scene that mirrors the GBDK platformer_template's NextLevel card flow
 * (player.c:44–82).
 *
 * The card scene shows level-transition graphics (via the author-supplied [SceneBuilder.screen]
 * call in the DSL block), waits for Start press, then triggers [bindCurrentLevel] (typed
 * [io.github.gbkt.core.ir.BindCurrentLevel] IR) + navigates to the gameplay scene. This is the
 * OWNED LIFECYCLE that closes Phase 12.6 DEFECT-1: the show-card → wait-for-Start → setup →
 * navigate sequence runs across multiple frames with vblanks between, so the card art renders for ≥
 * 1 vblank before the new level's tilemap stomps VRAM.
 *
 * The centered-draw ceremony is absorbed by the SceneVisitor screenMode superset path, triggered
 * when the author calls `screen(asset(...))` inside the DSL block. Zero raw-C escape hatches remain
 * (Phase 13.5 Req #17 + #18).
 *
 * Reuses the standard [SceneBuilder] surface internally — the lowering produces a regular
 * [SceneRef] compatible with all existing `navigate(sceneRef)` call sites.
 *
 * @property id The scene identifier (captured from the property name via
 *   [LevelCardSceneDelegate.provideDelegate] — Project Rule #1 / no magic strings).
 */
@GbktDsl
class LevelCardSceneBuilder(val id: String) {
    private var gameplaySceneRef: SceneRef? = null
    private val sceneBuilderBlocks: MutableList<SceneBuilder.() -> Unit> = mutableListOf()

    /**
     * Declares the gameplay [SceneRef] to navigate to after Start press.
     *
     * The codegen emits [bindCurrentLevel] (typed [io.github.gbkt.core.ir.BindCurrentLevel] IR,
     * lowered to `setup_current_level()`) BEFORE `navigate_to_scene(<gameplay>)`, ensuring the new
     * level's data is initialized in the same frame the player perceives as "after the card", not
     * the same frame as "showing the card".
     *
     * Per RESEARCH §Pitfall 5: callers must declare `gameplayScene` BEFORE the `levelCardScene {
     * onStartPress(gameplayScene) }` declaration so the Kotlin reference resolves at DSL-recording
     * time.
     */
    fun onStartPress(gameplayScene: SceneRef) {
        gameplaySceneRef = gameplayScene
    }

    /**
     * Declares the card's full-screen graphic using the typed [SceneBuilder.screen] primitive
     * (Phase 13.5 Req #18).
     *
     * Forwards the [assetRef] into a `sceneBuilderBlocks` entry so it executes inside the
     * underlying [SceneBuilder] (where the scene ID is already set). [SceneBuilder.screen]
     * synthesises a `_screen_<id>` [io.github.gbkt.core.ir.ZoneIR] with `screenMode=true`;
     * SceneVisitor's screenMode superset branch emits: `hide_sprites_range + move_bkg(0,0) +
     * fill_bkg_rect(0,0,32,32,0) + centered _bkg_tiles_load_banked + DISPLAY_ON`.
     */
    fun screen(assetRef: AssetRef) {
        sceneBuilderBlocks += { this.screen(assetRef) }
    }

    /**
     * Forwards a configuration block to the inner [SceneBuilder] so users can declare
     * `screen(asset(...))`, `enter { }`, `palette(...)`, `frame { }`, etc. on the card scene — the
     * same surface they get from `scene("...") { }`.
     *
     * The primary entry point for card visuals is `screen(asset("..."))` (Req #18) which triggers
     * the SceneVisitor screenMode superset (hide sprites, scroll reset, BG clear, centered tilemap
     * placement). Multiple `scene { }` calls accumulate (in declaration order) into one underlying
     * scene definition; this lets callers split concerns (visuals vs lifecycle).
     */
    fun scene(block: SceneBuilder.() -> Unit) {
        sceneBuilderBlocks += block
    }

    /**
     * Lowers the captured configuration to a regular [SceneRef] by calling `gb.scene(id) { ... }`.
     * User-supplied blocks (from the `levelCardScene { }` lambda) are applied first; the frame
     * handler (Start-press → bindCurrentLevel
     * + navigate) is appended last so it composes with any user-authored frame logic without
     *   clobbering it.
     *
     * Throws if [onStartPress] was never called — the post-card navigation target is mandatory.
     */
    internal fun materialize(gb: GameBuilder): SceneRef {
        val gameplay =
            gameplaySceneRef ?: error("levelCardScene '$id' must call onStartPress(gameplayScene)")
        // Hoist the user-supplied blocks into a local so the inner SceneBuilder
        // lambda (a `@GbktDsl`-scoped receiver) does not need an implicit
        // outer-receiver lookup back to LevelCardSceneBuilder. Required because
        // SceneBuilder is annotated `@GbktDsl`, which forbids accidental scope
        // leakage between DSL layers.
        val userBlocks: List<SceneBuilder.() -> Unit> = sceneBuilderBlocks.toList()
        return gb.scene(id) {
            // Apply user-supplied scene { ... } blocks first.
            // The author's screen(asset(...)) call in userBlocks triggers
            // SceneVisitor's screenMode superset (hide sprites + scroll reset +
            // BG clear + centered _bkg_tiles_load_banked) — no synthesized
            // enter block needed here (Phase 13.5 Req #18).
            userBlocks.forEach { it() }
            // Append the Start-press lifecycle handler last so it composes
            // with any user-authored frame logic without clobbering it.
            frame {
                whenever(buttons.start.pressed) {
                    // Phase 13.5 Req #17: typed BindCurrentLevel IR node.
                    bindCurrentLevel()
                    navigate(gameplay)
                }
            }
        }
    }
}

/**
 * Property delegate that captures the Kotlin property name as the level-card scene's identifier and
 * registers the scene with the current [GameBuilder].
 *
 * Mirrors the canonical `ActorDelegate.provideDelegate` shape from
 * `gbkt-lang/.../ActorBuilder.kt:1218–1241` — uses [GameBuilderContext.current] (or errors with the
 * standard "must be called inside a game {} block" message) and returns a [ReadOnlyProperty] so the
 * `by` keyword exposes the registered [SceneRef].
 *
 * Usage:
 * ```kotlin
 * val gameplayScene = scene("gameplay") { /* ... */ }
 * val nextLevelScene by levelCardScene {
 *     onStartPress(gameplayScene)
 * }
 * ```
 *
 * The property name `nextLevelScene` becomes the scene's id (Project Rule #1 / no magic strings).
 */
class LevelCardSceneDelegate(private val block: LevelCardSceneBuilder.() -> Unit) :
    ReadOnlyProperty<Any?, SceneRef> {
    private var ref: SceneRef? = null

    /**
     * Called by Kotlin when `val x by levelCardScene { ... }` is evaluated.
     *
     * Captures the property name, builds + materializes a [LevelCardSceneBuilder] against the
     * current [GameBuilder], and stores the resulting [SceneRef] for retrieval by [getValue].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, SceneRef> {
        val gb =
            GameBuilderContext.current
                ?: error("levelCardScene {} must be called inside a game {} block")
        val builder = LevelCardSceneBuilder(property.name).apply(block)
        ref = builder.materialize(gb)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): SceneRef =
        ref ?: error("LevelCardSceneDelegate not initialized — was provideDelegate called?")
}

/**
 * Declares a level-card scene that mirrors the GBDK platformer_template's NextLevel card flow
 * (player.c:44–82).
 *
 * The card scene shows level-transition graphics, waits for Start press, then triggers
 * `setup_current_level()` + navigates to the gameplay scene. This is the OWNED LIFECYCLE that
 * closes Phase 12.6 DEFECT-1 — the show-card → wait → setup → navigate sequence runs across
 * multiple frames with vblanks between, so card art renders before the new level's tilemap stomps
 * VRAM.
 *
 * The scene's id is captured from the Kotlin property name via the delegate (Project Rule #1 — no
 * magic-string ID). The lowered scene's frame handler emits `setup_current_level();` BEFORE
 * `navigate_to_scene(<gameplay>)`.
 *
 * Usage:
 * ```kotlin
 * val gameplayScene = scene("gameplay") { /* ... */ }
 * val nextLevelScene by levelCardScene {
 *     scene {
 *         zone(nextLevelCardZone)
 *         palette(nextLevelCardPalette)
 *     }
 *     onStartPress(gameplayScene)
 * }
 * ```
 *
 * Mirrors the reference behavior at
 * `gbdk/examples/cross-platform/platformer_template/src/main.c:44–82`.
 *
 * @param block Configuration block executed against a [LevelCardSceneBuilder]. Must call
 *   `onStartPress(gameplayScene)` — the post-card navigation target is mandatory.
 */
fun GameBuilder.levelCardScene(block: LevelCardSceneBuilder.() -> Unit): LevelCardSceneDelegate =
    LevelCardSceneDelegate(block)
