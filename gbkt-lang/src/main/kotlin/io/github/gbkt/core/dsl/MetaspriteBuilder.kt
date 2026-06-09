/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import io.github.gbkt.core.ir.MoveMetasprite
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.SpriteMode
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// METASPRITE FRAME BUILDER
// =============================================================================

/**
 * Builder for a single metasprite frame — an ordered list of OAM tile entries.
 *
 * Used inside `metasprite { frame { tile(x, y, id) } }` blocks.
 *
 * **DSL scope:** `@GbktDsl` prevents frame { } from accidentally calling outer game { } methods.
 */
@GbktDsl
class MetaspriteFrameBuilder {
    private val tiles: MutableList<MetaspriteTile> = mutableListOf()

    /**
     * Adds a single OAM tile entry to this frame.
     *
     * @param x Relative X offset from the metasprite origin (pixels). Must be int8_t-representable
     *   (-128..127).
     * @param y Relative Y offset from the metasprite origin (pixels). Must be int8_t-representable
     *   (-128..127).
     * @param baseId Base tile index into the sprite VRAM block (must be >= 0).
     *
     * Validation: x and y are guarded at DSL build time (WR-03) — GBDK encodes metasprite relative
     * offsets as int8_t, so out-of-range values would silently overflow during codegen and produce
     * broken visual layouts that surface only at runtime.
     */
    fun tile(x: Int, y: Int, baseId: Int) {
        require(x in -128..127) {
            "metasprite tile x must be int8_t-representable (-128..127), got $x"
        }
        require(y in -128..127) {
            "metasprite tile y must be int8_t-representable (-128..127), got $y"
        }
        tiles.add(MetaspriteTile(relX = x, relY = y, tileId = baseId))
    }

    internal fun build(): MetaspriteFrame {
        require(tiles.isNotEmpty()) {
            "metasprite frame must have at least one tile — got an empty tile list"
        }
        require(tiles.all { it.tileId >= 0 }) {
            val bad = tiles.first { it.tileId < 0 }
            "metasprite tile tileId must be >= 0, got ${bad.tileId} at (${bad.relX}, ${bad.relY})"
        }
        return MetaspriteFrame(tiles = tiles.toList())
    }
}

// =============================================================================
// METASPRITE BUILDER
// =============================================================================

/**
 * Builder for a GBDK-style variable-length metasprite.
 *
 * Used inside `val elephant by metasprite { frame { tile(relX, relY, tileId) } }` blocks. Name is
 * inferred from the Kotlin property name via [MetaspriteDelegate.provideDelegate].
 *
 * **Validation (builder-time):**
 * - Must have at least one frame.
 * - Each frame must have at least one tile.
 * - All tileIds must be >= 0.
 *
 * **DSL scope:** `@GbktDsl` annotation prevents metasprite { } from accidentally calling outer game
 * { } methods.
 */
@GbktDsl
class MetaspriteBuilder(val id: String) {
    private val frameBuilders: MutableList<MetaspriteFrameBuilder> = mutableListOf()

    // Captured AssignableVar.name strings for the 4 optional per-metasprite variable bindings
    // (Plan 10.1-03 — substrate for CR-03 per-metasprite symbol namespacing + WR-01 visitor
    // literal removal). All default to null so Phase 10 `Metasprites.kt` continues to type-check
    // without modification — the visitor (Plan 05) substitutes the canonical _posX / _posY /
    // _idx / _rot globals when these are null.
    internal var posXVar: String? = null
    internal var posYVar: String? = null
    internal var idxVar: String? = null
    internal var rotVar: String? = null

    // Plan 12.4-01 Task 2 -- explicit PNG asset path bound via sprite(asset(...)) DSL binder.
    // Captured as AssetRef.path (typed — no magic strings per feedback_no_magic_strings.md).
    // Flows to MetaspriteIR.spritePath in build(), then to game_metadata.json sprites[] sidecar
    // (D-02). Default null = migration window (D-01b): metasprites not yet migrated to sprite()
    // are skipped by the sidecar emitter.
    internal var spriteAssetPath: String? = null

    // Plan 10.1-16 Task 3 -- per-metasprite opt-in to allow png2asset mirror-pair
    // tile dedup (i.e. SKIP the `-noflip` arg for this metasprite's PNG). Default
    // false keeps Task 1's unconditional `-noflip` for every metasprite-bound PNG,
    // which is the correct contract for DSL transcribed from a reference's `-noflip`
    // output (DEF-10.1-13-A). Toggled true via [mirrorDedup].
    internal var mirrorDedupOptIn: Boolean = false

    // Phase 12.5 D-04b — png2asset cutting flags captured via SpriteConfigBuilder.
    // All default null (migration window); GenerateCTask gate enforces non-null at codegen time.
    // Set via sprite(asset(...)) { mode(...); pivot(...); frameSize(...) } block.
    internal var spriteModeValue: SpriteMode? = null
    internal var pivotXValue: Int? = null
    internal var pivotYValue: Int? = null
    internal var frameWidthValue: Int? = null
    internal var frameHeightValue: Int? = null

    // Phase 13.3 D-07 — author-declared animation frame count for build-time cross-validation.
    // Set via frames(N). Null when not called (validation skipped). Semantically distinct from
    // frameBuilders.size (escape-hatch procedural frames) and from idxVar wrapAt.
    private var declaredFrameCount: Int? = null

    /**
     * Adds an animation frame to this metasprite.
     *
     * @param block Configuration block for the frame's OAM tile entries.
     */
    fun frame(block: MetaspriteFrameBuilder.() -> Unit) {
        val builder = MetaspriteFrameBuilder()
        builder.block()
        frameBuilders.add(builder)
    }

    /**
     * Binds a user-declared variable as this metasprite's X position.
     *
     * The bound variable's name (`AssignableVar.name`, inferred from the Kotlin property name) is
     * captured into [MetaspriteIR.posXVarName] and propagated through `moveMetasprite()` to the
     * emitted [MoveMetasprite] ScriptOp's `posXVar` field. The visitor (Plan 05) reads this to emit
     * per-metasprite-namespaced references instead of the hardcoded `_posX` global, enabling
     * multiple coexisting metasprites without symbol collision (CR-03 + WR-01).
     *
     * Per user feedback `feedback_no_magic_strings.md`: this binder takes [AssignableVar] (not a
     * `String`) so the variable name flows from the Kotlin property delegate — no magic-string
     * duplication.
     *
     * Usage:
     * ```kotlin
     * var elephantX by i16Var(0)
     * val elephant by metasprite {
     *     posX(elephantX)
     *     frame { tile(0, 0, 0) }
     * }
     * ```
     */
    fun posX(varRef: AssignableVar) {
        posXVar = varRef.name
    }

    /** Binds a user-declared variable as this metasprite's Y position. See [posX] for semantics. */
    fun posY(varRef: AssignableVar) {
        posYVar = varRef.name
    }

    /**
     * Binds a user-declared variable as this metasprite's frame index. See [posX] for semantics.
     */
    fun idx(varRef: AssignableVar) {
        idxVar = varRef.name
    }

    /**
     * Binds a user-declared variable as this metasprite's rotation/orientation state. See [posX]
     * for semantics.
     */
    fun rot(varRef: AssignableVar) {
        rotVar = varRef.name
    }

    /**
     * Inner builder for per-sprite png2asset cutting flags (Phase 12.5 D-04a).
     *
     * Used inside `sprite(asset(...)) { mode(...); pivot(...); frameSize(...) }` blocks. Methods
     * write directly into the enclosing [MetaspriteBuilder]'s internal fields via the inner-class
     * implicit `this@MetaspriteBuilder` reference.
     *
     * Per [feedback_no_magic_strings.md]: all methods take typed parameters — [SpriteMode] enum for
     * `mode()`, [Int] pairs for `pivot()` and `frameSize()`. No raw strings exposed.
     *
     * **Migration window:** The config block is optional (default `= {}`). A metasprite that calls
     * `sprite(asset(...))` without a config block leaves the 5 cutting-flag IR fields null; Plan 06
     * (`GenerateCTask` gate) enforces non-null at codegen time.
     *
     * @see MetaspriteBuilder.sprite for usage example.
     */
    @GbktDsl
    inner class SpriteConfigBuilder {
        /**
         * Sets the png2asset sprite rendering mode for this metasprite.
         *
         * [SpriteMode.SPR8x8] → passes `-spr8x8` to png2asset. [SpriteMode.SPR8x16] → no flag
         * (png2asset default); selects 8×16 hardware sprite pairs.
         *
         * @param m Sprite rendering mode enum value — typed, no magic strings.
         */
        fun mode(m: SpriteMode) {
            this@MetaspriteBuilder.spriteModeValue = m
        }

        /**
         * Sets the png2asset pivot point (`-px`, `-py`) for this metasprite.
         *
         * The pivot is the origin within the sprite image that aligns with the metasprite's logical
         * (posX, posY) position. For a 24×32 player sprite cut as 3 columns × 2 rows: `pivot(12,
         * 6)` centres the pivot horizontally and places it at the top of the first row.
         *
         * @param x Horizontal pivot offset in pixels (maps to `-px` png2asset flag).
         * @param y Vertical pivot offset in pixels (maps to `-py` png2asset flag).
         */
        fun pivot(x: Int, y: Int) {
            this@MetaspriteBuilder.pivotXValue = x
            this@MetaspriteBuilder.pivotYValue = y
        }

        /**
         * Sets the png2asset frame dimensions (`-sw`, `-sh`) for this metasprite.
         *
         * Specifies the width and height (in pixels) of a single animation frame within the source
         * PNG. png2asset uses these to slice the sheet into frames. For a 24×32 player sprite:
         * `frameSize(24, 32)` maps to `-sw 24 -sh 32`.
         *
         * @param w Frame width in pixels (maps to `-sw` png2asset flag).
         * @param h Frame height in pixels (maps to `-sh` png2asset flag).
         */
        fun frameSize(w: Int, h: Int) {
            this@MetaspriteBuilder.frameWidthValue = w
            this@MetaspriteBuilder.frameHeightValue = h
        }
    }

    /**
     * Binds the PNG source asset for this metasprite to the png2asset pipeline (Phase 12.4).
     *
     * Captures [asset].path into [spriteAssetPath], which flows to [MetaspriteIR.spritePath] in
     * [build]. The path is then emitted into the `sprites[]` section of `game_metadata.json` (Phase
     * 12.4 D-02 sidecar); [ConvertSpritesTask] reads the sidecar to resolve
     * `{assetDir}/{spritePath}` and invoke `png2asset` on the PNG.
     *
     * Per [feedback_no_magic_strings.md] (Phase 12.2 D-03 precedent — `WorldBuilders.kt:101`
     * tileset binder): this binder accepts only [AssetRef], not a raw `String`. The [AssetRef.type]
     * field is ignored by the resolver — only [AssetRef.path] is captured. Pass the result of the
     * `asset("sprites/foo.png")` factory (or `asset("...", AssetType.SPRITE)`) — any [AssetType]
     * works.
     *
     * The optional [block] lambda (Phase 12.5 D-04a) allows declaring png2asset cutting flags for
     * this metasprite — sprite rendering mode, pivot point, and frame dimensions. Omitting the
     * block preserves migration-window back-compat: the 5 cutting-flag IR fields stay null and
     * GenerateCTask (Plan 06) enforces them at codegen time.
     *
     * **Migration window (D-01b):** Calling this binder is OPTIONAL during Phase 12.4/12.5.
     * Metasprites that do not call `sprite()` have [MetaspriteIR.spritePath] = null and are skipped
     * by the sidecar emitter. The validation gate in Plan 12.4-05 / 12.5-06 will enforce non-null
     * at codegen time for games that have opted in to the automated pipeline.
     *
     * Usage:
     * ```kotlin
     * val hero by metasprite {
     *     sprite(asset("sprites/hero.png")) {
     *         mode(SpriteMode.SPR8x16)
     *         pivot(12, 6)
     *         frameSize(24, 32)
     *     }
     *     frame { tile(0, 0, 0) }
     * }
     * ```
     */
    fun sprite(asset: AssetRef, block: SpriteConfigBuilder.() -> Unit = {}) {
        spriteAssetPath = asset.path
        SpriteConfigBuilder().apply(block)
    }

    /**
     * Per-metasprite opt-in: allow png2asset to deduplicate mirror-pair tiles for this metasprite's
     * source PNG (Plan 10.1-16 Task 3).
     *
     * **Default behavior (opt-in NOT called):** Plan 10.1-16 Task 1's [ConvertSpritesTask] passes
     * `-noflip` to png2asset for every metasprite-bound PNG, producing the full unique-tile array.
     * This is the correct contract for DSL transcribed from a reference's `-noflip` output (e.g.
     * the GBDK metasprites example's elephant; DEF-10.1-13-A).
     *
     * **Opt-in behavior (this method called):** [ConvertSpritesTask] (Task 4) OMITS `-noflip` for
     * this metasprite's PNG, so png2asset detects mirror tile pairs and emits one deduplicated
     * tile + `S_FLIPX`/`S_FLIPY` METASPR_ITEM attrs for the mirrored variant. The tile-data array
     * shrinks; intended for from-scratch authored metasprites that can take advantage of the dedup
     * to save ROM.
     *
     * **DANGER:** Calling this on a metasprite whose [tile] baseIds were transcribed from a
     * reference's `-noflip` output (i.e. id space 0..N) will re-open DEF-10.1-13-A -- baseIds that
     * the reference resolves via the full unique-tile array will dereference past the end of the
     * deduplicated array -> garbage pixels. Only opt in when authoring the metasprite from-scratch
     * against the deduped output.
     *
     * Usage:
     * ```kotlin
     * val sprite by metasprite {
     *     mirrorDedup()
     *     frame { tile(0, 0, 0); tile(8, 0, 1) }
     * }
     * ```
     */
    fun mirrorDedup() {
        mirrorDedupOptIn = true
    }

    /**
     * Declares the expected number of animation frames produced by png2asset from this metasprite's
     * source PNG (Phase 13.3 D-07).
     *
     * When declared, [ConvertSpritesTask] parses the actual frame count from the
     * `<id>_metasprites[N]` pointer-array declaration in the png2asset-generated `.c` file and
     * fails the build with a descriptive message if the two counts disagree. This catches DSL/asset
     * desync at build time rather than at runtime.
     *
     * **Semantics:** This is an asset-driven count declaration — the number of animation frames
     * png2asset will cut from the sprite sheet. It is NOT the same as the number of procedural
     * `frame { }` blocks (escape-hatch path D-04), and it is NOT the `wrapAt` value for the frame
     * index variable. These are three independent concepts.
     *
     * Only meaningful when used together with [sprite] (asset-driven path). Calling `frames(N)` on
     * a procedural metasprite (escape-hatch D-04) has no runtime effect other than recording the
     * count in [MetaspriteIR.frameCount] for the sidecar.
     *
     * @param n Expected number of animation frames (must be >= 1).
     *
     * Usage:
     * ```kotlin
     * val hero by metasprite {
     *     sprite(asset("sprites/hero.png")) {
     *         mode(SpriteMode.SPR8x16)
     *         pivot(12, 6)
     *         frameSize(24, 32)
     *     }
     *     frames(4)  // hero.png produces 4 animation frames
     * }
     * ```
     */
    fun frames(n: Int) {
        require(n >= 1) { "metasprite \"$id\" frames(N) must be >= 1, got $n" }
        declaredFrameCount = n
    }

    internal fun build(): MetaspriteIR {
        val hasAsset = spriteAssetPath != null
        val hasFrames = frameBuilders.isNotEmpty()

        // D-08 exactly-one guard: exactly one of { sprite(asset), frame{} } must be present.
        // - Both: asset-driven and procedural frames are mutually exclusive paths; using both
        //   creates ambiguity at codegen time.
        // - Neither: no path to codegen — fail loudly at build time.
        require(!(hasAsset && hasFrames)) {
            "metasprite \"$id\": cannot use both sprite(asset(...)) and frame{} — " +
                "these are mutually exclusive paths (D-08: asset-driven vs procedural frame descriptors). " +
                "Remove either the sprite() or all frame{} blocks."
        }
        require(hasAsset || hasFrames) {
            "metasprite \"$id\": must have either sprite(asset(...)) (asset-driven path) or " +
                "at least one frame{} block (procedural escape-hatch D-04) — got neither."
        }

        val frames = frameBuilders.map { it.build() }
        return MetaspriteIR(
            id = id,
            frames = frames,
            posXVarName = posXVar,
            posYVarName = posYVar,
            idxVarName = idxVar,
            rotVarName = rotVar,
            mirrorDedup = mirrorDedupOptIn,
            spritePath = spriteAssetPath,
            spriteMode = spriteModeValue,
            pivotX = pivotXValue,
            pivotY = pivotYValue,
            frameWidth = frameWidthValue,
            frameHeight = frameHeightValue,
            frameCount = declaredFrameCount,
        )
    }
}

// =============================================================================
// METASPRITE REFERENCE
// =============================================================================

/**
 * Lightweight typed reference to a metasprite declared in the game DSL.
 *
 * Returned by [MetaspriteDelegate] when `val elephant by metasprite { ... }` is evaluated. Property
 * accessors [flipX], [flipY], and [subPalette] return [ActorPropertyRef] instances — they use the
 * existing operator extension set and are wired to visitor lowering in Plans 08+09.
 */
data class MetaspriteRef(val id: String) {
    override fun toString(): String = id

    /**
     * Reference to this metasprite's flip-X attribute.
     *
     * Returns an [ActorPropertyRef] for use in DSL operator expressions. Wired to `OAMF_X_FLIP` in
     * the metasprite visitor (Plan 08+09).
     */
    val flipX: ActorPropertyRef
        get() = ActorPropertyRef(id, "flipX")

    /**
     * Reference to this metasprite's flip-Y attribute.
     *
     * Returns an [ActorPropertyRef] for use in DSL operator expressions. Wired to `OAMF_Y_FLIP` in
     * the metasprite visitor (Plan 08+09).
     */
    val flipY: ActorPropertyRef
        get() = ActorPropertyRef(id, "flipY")

    /**
     * Reference to this metasprite's GBC sub-palette attribute.
     *
     * Returns an [ActorPropertyRef] for use in DSL operator expressions. Wired to `OAMF_CGB_PAL` in
     * the metasprite visitor (Plan 08+09).
     */
    val subPalette: ActorPropertyRef
        get() = ActorPropertyRef(id, "subPalette")
}

// =============================================================================
// METASPRITE DELEGATE
// =============================================================================

/**
 * Property delegate that infers a metasprite's name from the Kotlin property and registers it with
 * the current [GameBuilder].
 *
 * Implements [ReadOnlyProperty] and exposes `provideDelegate` so that Kotlin calls
 * [provideDelegate] when the `by` keyword is used. The property name is captured at that point and
 * the metasprite IR is built and registered.
 *
 * Usage:
 * ```kotlin
 * val elephant by metasprite {
 *     frame { tile(0, 0, 0); tile(8, 0, 1) }
 * }
 * ```
 */
/**
 * Single-use: each `val x by metasprite { }` binding must use its own delegate instance. Reusing
 * one instance across two `by` bindings throws [IllegalStateException] at build time.
 */
class MetaspriteDelegate(private val block: MetaspriteBuilder.() -> Unit) :
    ReadOnlyProperty<Any?, MetaspriteRef> {
    private var ref: MetaspriteRef? = null

    /**
     * Single-use guard. Prevents silent double-registration when the same delegate instance is
     * accidentally bound to two `val` properties.
     */
    private var delegateUsed: Boolean = false

    /**
     * Called by Kotlin when `val x by metasprite { ... }` is evaluated.
     *
     * Captures the property name, builds the [MetaspriteIR], registers it with the current
     * [GameBuilder], and stores the resulting [MetaspriteRef] for retrieval by [getValue].
     *
     * @throws IllegalStateException if called outside a `game { }` block or if the delegate
     *   instance is reused across two `val` bindings.
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, MetaspriteRef> {
        check(!delegateUsed) {
            "MetaspriteDelegate instance reused: was already bound to '${ref?.id ?: "<unknown>"}'. " +
                "Each 'val x by metasprite { }' must use its own delegate instance."
        }
        delegateUsed = true
        val name = property.name
        val gameBuilder =
            GameBuilderContext.current
                ?: error("metasprite {} must be called inside a game {} block")
        val builder = MetaspriteBuilder(name)
        builder.block()
        val ir = builder.build()
        gameBuilder.registerMetasprite(ir)
        ref = MetaspriteRef(name)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): MetaspriteRef =
        ref ?: error("MetaspriteDelegate not initialized — was provideDelegate called?")
}

// =============================================================================
// TOP-LEVEL FACTORY FUNCTION
// =============================================================================

/**
 * Creates a [MetaspriteDelegate] for use with the `by` keyword inside a `game { }` block.
 *
 * The metasprite name is inferred from the Kotlin property name via
 * [MetaspriteDelegate.provideDelegate].
 *
 * Usage:
 * ```kotlin
 * val elephant by metasprite {
 *     frame { tile(0, 0, 0) }
 * }
 * ```
 *
 * @throws IllegalStateException if the property delegation occurs outside a `game { }` block.
 */
fun metasprite(block: MetaspriteBuilder.() -> Unit): MetaspriteDelegate = MetaspriteDelegate(block)

// =============================================================================
// METASPRITE DSL HELPERS
// =============================================================================

/**
 * Emits a [MoveMetasprite] script op into the active [ScriptBuilder].
 *
 * Renders the metasprite at its current position each frame using the GBDK `move_metasprite()` call
 * (lowered in Plan 07 — MetaspriteVisitor). The flip and sub-palette attributes are read from the
 * runtime state variables (`_<id>_flipX`, `_<id>_flipY`, `_<id>_subPalette`).
 *
 * Must be called from inside a `scene { frame { } }` block (or any other [ScriptBuilder] scope).
 *
 * Usage:
 * ```kotlin
 * val elephant by metasprite { frame { tile(0, 0, 0); tile(8, 0, 1) } }
 * scene("gameplay") {
 *     frame {
 *         moveMetasprite(elephant)
 *     }
 * }
 * ```
 *
 * @param ref Typed reference to the declared metasprite (from `val x by metasprite { }`).
 * @throws IllegalStateException if called outside a [ScriptBuilder] block.
 */
fun moveMetasprite(ref: MetaspriteRef) {
    // Look up the already-registered MetaspriteIR (registered by MetaspriteDelegate.provideDelegate
    // BEFORE this scene's frame block runs, because `val sprite by metasprite { ... }` evaluates
    // at the property's first read at decl-site). Mirror the captured posX/posY/idx/rot variable
    // names onto the emitted MoveMetasprite ScriptOp so the visitor (Plan 05) can emit
    // per-metasprite-namespaced references without re-looking-up the IR.
    //
    // If no GameBuilder is active (test harness short-circuit), or the metasprite was never
    // registered (defensive), all 4 fields stay null and the visitor uses its canonical fallback —
    // back-compat path for Phase 10 `Metasprites.kt`.
    val ms = GameBuilderContext.current?.findMetasprite(ref.id)
    ScriptBuilderContext.current?.emit(
        MoveMetasprite(
            metaspriteId = ref.id,
            posXVar = ms?.posXVarName,
            posYVar = ms?.posYVarName,
            idxVar = ms?.idxVarName,
            rotVar = ms?.rotVarName,
        )
    ) ?: error("moveMetasprite() called outside a ScriptBuilder block")
}

/**
 * Fills the BG tile layer with a 1-tile true 4x4 checkerboard pattern (D-Seed005).
 *
 * Emits two GBDK calls: a `static const` `_checkerboard_bg_pattern[]` byte literal followed by
 * `fill_bkg_rect(...)` + `set_bkg_data(0, 1, _checkerboard_bg_pattern)`. The literal encodes a
 * single 8x8 tile whose top 4 rows are `11110000` (plane 0 high nibble lit) and whose bottom 4 rows
 * are `00001111` (plane 0 low nibble lit). When tiled across the screen by `fill_bkg_rect`, this
 * produces a uniform 4x4 checker square pattern.
 *
 * **D-Seed005 intentional deviation from GBDK reference:** the reference `metasprites.c` ships a
 * diagonal-stripe literal under the same "checkerboard" label, which when tiled renders as parallel
 * diagonal stripes (NOT a checkerboard). gbkt deliberately diverges from the reference here per
 * D-12b so the helper name (`bgFillCheckerboard`) matches what it visually emits — a true
 * checkerboard. See SEED-005 for history.
 *
 * The pattern constant is declared as `static const` inside the enter function body so SDCC places
 * it in ROM (not the stack) without requiring a separate file-scope declaration.
 *
 * **Pitfall 5 note:** Do NOT combine with `debugGraphics = true` — GBDK's printf() writes to the BG
 * tile layer and would overwrite the checkerboard pattern. The pipeline defaults `debugGraphics =
 * false`, so this is safe under normal usage.
 *
 * Must be called from inside a `scene { enter { } }` block (or any other [ScriptBuilder] scope).
 *
 * @throws IllegalStateException if called outside a [ScriptBuilder] block.
 */
fun bgFillCheckerboard() {
    // Per-row stride: each tile row consumes 2 bytes (plane 0 + plane 1). For a true 4x4
    // square checker, the alternation period in TILE ROWS must be 4 — i.e. 4 consecutive
    // rows share the same horizontal byte. Since 4 rows × 2 bytes/row = 8 bytes per half,
    // the literal groups 8× 0xF0 (top half: left 4 columns lit ⇒ color 3) then 8× 0x0F
    // (bottom half: right 4 columns lit ⇒ color 3). DO NOT interleave 4-byte F0/0F groups
    // (the Plan 10.1-02 bug: that pattern alternates every 2 tile rows ⇒ 4w×2h rectangles,
    // not 4×4 squares — DEF-10.1-13-B, see Plan 10.1-17 d-v2-visual-finding.md).
    val code =
        "static const UINT8 _checkerboard_bg_pattern[] = {\n" +
            "    0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,\n" +
            "    0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F};\n" +
            "fill_bkg_rect(0, 0, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0);\n" +
            "set_bkg_data(0, 1, _checkerboard_bg_pattern);"
    ScriptBuilderContext.current?.emit(RawOp(code))
        ?: error("bgFillCheckerboard() called outside a ScriptBuilder block")
}
