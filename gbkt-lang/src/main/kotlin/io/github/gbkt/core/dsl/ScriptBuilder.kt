/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.AnimateOp
import io.github.gbkt.core.ir.ArrayAssign
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BindCurrentLevel
import io.github.gbkt.core.ir.CallOp
import io.github.gbkt.core.ir.CameraAction
import io.github.gbkt.core.ir.CameraOp
import io.github.gbkt.core.ir.DestroyActor
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.FadeOp
import io.github.gbkt.core.ir.ForOp
import io.github.gbkt.core.ir.GotoXYOp
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.MathFunction
import io.github.gbkt.core.ir.MathOp
import io.github.gbkt.core.ir.MoveBy
import io.github.gbkt.core.ir.MusicPlay
import io.github.gbkt.core.ir.MusicStop
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.PathfindStep
import io.github.gbkt.core.ir.PhysicsStep
import io.github.gbkt.core.ir.PlaySound
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.PrintOp
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.ReturnOp
import io.github.gbkt.core.ir.ScreenClear
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.SetAnimationState
import io.github.gbkt.core.ir.SetPosition
import io.github.gbkt.core.ir.SetVisible
import io.github.gbkt.core.ir.SpawnActor
import io.github.gbkt.core.ir.TriggerSystem
import io.github.gbkt.core.ir.UnaryExpr
import io.github.gbkt.core.ir.UnaryOp
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.WaitFrames
import io.github.gbkt.core.ir.WaypointStep
import io.github.gbkt.core.ir.WhileOp

/**
 * Records a list of [ScriptOp] instructions for a scene lifecycle handler (enter/frame/exit).
 *
 * Methods append [ScriptOp] instances to the internal list. Call [build] to retrieve the complete
 * ordered list.
 *
 * Marked with [@GbktDsl] to prevent accidental implicit receiver leakage in nested DSL scopes.
 */
@GbktDsl
@Suppress("TooManyFunctions") // DSL builder provides one method per ScriptOp type by design
class ScriptBuilder {
    private val ops: MutableList<ScriptOp> = mutableListOf()

    /** Returns the accumulated list of script operations. */
    fun build(): List<ScriptOp> = ops.toList()

    /**
     * Executes [block] with this [ScriptBuilder] set as the active [ScriptBuilderContext].
     *
     * Use this method when invoking a `ScriptBuilder.() -> Unit` lambda from outside the
     * `gbkt-lang` module (e.g. from `gbkt-genre-rpg`). Without this, compound assignment operators
     * like `+=` and `-=` on [AssignableVar] throw because [ScriptBuilderContext.current] is null.
     *
     * ```kotlin
     * val scriptBuilder = ScriptBuilder()
     * scriptBuilder.runWith { gold += 5; navigate(dungeonScene) }
     * val ops = scriptBuilder.build()
     * ```
     */
    fun runWith(block: ScriptBuilder.() -> Unit) {
        ScriptBuilderContext.with(this) { this.block() }
    }

    companion object {
        /**
         * Convenience factory: creates a [ScriptBuilder], executes [block] within the
         * [ScriptBuilderContext], and returns the built ops list in one call.
         *
         * Replaces the common three-line idiom:
         * ```kotlin
         * val sb = ScriptBuilder()
         * sb.runWith(block)
         * return sb.build()
         * ```
         */
        fun buildOps(block: ScriptBuilder.() -> Unit): List<ScriptOp> {
            val sb = ScriptBuilder()
            sb.runWith(block)
            return sb.build()
        }
    }

    /**
     * Appends a pre-built [ScriptOp] directly to the ops list.
     *
     * Internal API used by collection-like helpers on [ArrayVar] (fill, forEach, indexOf, count)
     * that need to emit IR nodes constructed outside the builder lambda pattern.
     */
    internal fun emit(op: ScriptOp) {
        ops += op
    }

    /**
     * Emits a variable assignment op.
     *
     * Internal API called by [AssignableVar] and [ActorPropertyRef] extension operators (set, +=,
     * -=, etc.) via [ScriptBuilderContext.current].
     */
    internal fun assign(target: String, value: Expr, op: AssignOp = AssignOp.SET) {
        ops += Assign(target, value, op, sourceLocation = captureV2Location())
    }

    /**
     * Emits an array element assignment op.
     *
     * Internal API called by [ArrayVar] bracket-write operators (e.g. `arr[i] = v`) via
     * [ScriptBuilderContext.current].
     */
    internal fun arrayAssign(array: String, index: Expr, value: Expr, op: AssignOp = AssignOp.SET) {
        ops += ArrayAssign(array, index, value, op, sourceLocation = captureV2Location())
    }

    // -------------------------------------------------------------------------
    // Control flow
    // -------------------------------------------------------------------------

    /**
     * Begins an if block. Can be chained with [elseOp] for if-else.
     *
     * Records an [IfOp]. If the next call is [elseOp], it updates the most recent [IfOp] to include
     * the else body.
     */
    fun ifOp(condition: Expr, block: ScriptBuilder.() -> Unit) {
        val loc = captureV2Location()
        val thenBuilder = ScriptBuilder()
        ScriptBuilderContext.with(thenBuilder) { thenBuilder.block() }
        ops += IfOp(condition, thenBuilder.build(), sourceLocation = loc)
    }

    /**
     * Adds an else branch to the most recently recorded [IfOp].
     *
     * Must immediately follow [ifOp] in the script builder.
     *
     * @throws DSLValidationError if there is no preceding [IfOp] to attach to.
     */
    fun elseOp(block: ScriptBuilder.() -> Unit) {
        val lastIf =
            ops.lastOrNull() as? IfOp
                ?: throw DSLValidationError("error: elseOp() must immediately follow ifOp()")
        val elseBuilder = ScriptBuilder()
        ScriptBuilderContext.with(elseBuilder) { elseBuilder.block() }
        ops[ops.lastIndex] = lastIf.copy(otherwise = elseBuilder.build())
    }

    /** Loops while the condition is true. */
    fun whileOp(condition: Expr, block: ScriptBuilder.() -> Unit) {
        val loc = captureV2Location()
        val bodyBuilder = ScriptBuilder()
        ScriptBuilderContext.with(bodyBuilder) { bodyBuilder.block() }
        ops += WhileOp(condition, bodyBuilder.build(), sourceLocation = loc)
    }

    /** Ranged for loop: for [variable] in [from]..[to]. */
    fun forOp(variable: String, from: Expr, to: Expr, block: ScriptBuilder.() -> Unit) {
        val loc = captureV2Location()
        val bodyBuilder = ScriptBuilder()
        ScriptBuilderContext.with(bodyBuilder) { bodyBuilder.block() }
        ops += ForOp(variable, from, to, bodyBuilder.build(), sourceLocation = loc)
    }

    /**
     * Ranged for loop with Int bounds (auto-wraps in [Literal]).
     *
     * Enables `forOp("i", 0, 29) { ... }` without manual literal calls.
     */
    fun forOp(variable: String, from: Int, to: Int, block: ScriptBuilder.() -> Unit) {
        forOp(variable, Literal(from), Literal(to), block)
    }

    // -------------------------------------------------------------------------
    // Conditional sugar (whenever)
    // -------------------------------------------------------------------------

    /**
     * Sugar for a one-armed conditional that lowers to [IfOp] with empty otherwise list.
     *
     * Use [whenever] for top-level reactive triggers (e.g. button-held, collision, game-state
     * checks) that are evaluated every frame as independent guards.
     *
     * For **single-frame imperative conditionals** (clamps after mutation, wrap-after-increment),
     * prefer [runIf] / [unless] / [orElse] — they read as control flow, not reactive triggers.
     *
     * Equivalent to `runIf(condition) { ... }` in the generated C.
     *
     * Tier-3 roadmap: a future phase will unify `whenever` → `runIf` for reactive sites; tracked as
     * a pending todo. Not deprecated this phase.
     */
    fun whenever(condition: Expr, block: ScriptBuilder.() -> Unit) {
        val loc = captureV2Location()
        val bodyBuilder = ScriptBuilder()
        ScriptBuilderContext.with(bodyBuilder) { bodyBuilder.block() }
        ops += IfOp(condition, bodyBuilder.build(), emptyList(), sourceLocation = loc)
    }

    // -------------------------------------------------------------------------
    // Single-frame imperative conditionals
    // -------------------------------------------------------------------------

    /**
     * Single-frame imperative conditional. Lowers to [IfOp] — identical to [ifOp].
     *
     * Use for clamps, guards, and post-mutation checks that execute once per frame in sequence. For
     * top-level reactive triggers evaluated as independent guards every frame, use [whenever].
     */
    fun runIf(condition: Expr, block: ScriptBuilder.() -> Unit) = ifOp(condition, block)

    /**
     * Negated single-frame conditional: executes [block] when [condition] is false.
     *
     * Lowers to [IfOp] with [UnaryExpr(LOGICAL_NOT, condition)] — identical to
     * `runIf(condition.not()) { ... }`.
     */
    fun unless(condition: Expr, block: ScriptBuilder.() -> Unit) =
        ifOp(UnaryExpr(UnaryOp.LOGICAL_NOT, condition), block)

    /**
     * Else branch chained to the most recent [runIf]. Delegates to [elseOp].
     *
     * Must immediately follow [runIf] or [ifOp].
     */
    fun orElse(block: ScriptBuilder.() -> Unit) = elseOp(block)

    // -------------------------------------------------------------------------
    // Movement
    // -------------------------------------------------------------------------

    /** Teleports an actor to an absolute position. */
    fun setPosition(actorId: String, x: Expr, y: Expr) {
        ops += SetPosition(actorId, x, y, sourceLocation = captureV2Location())
    }

    /** Teleports an actor to an absolute position (Int overload). */
    fun setPosition(actorId: String, x: Int, y: Int) {
        ops += SetPosition(actorId, Literal(x), Literal(y), sourceLocation = captureV2Location())
    }

    /** Moves an actor by a relative delta using Expr arguments. */
    fun moveBy(actorId: String, dx: Expr, dy: Expr) {
        ops += MoveBy(actorId, dx, dy, sourceLocation = captureV2Location())
    }

    /** Moves an actor by a relative delta using Int arguments. */
    fun moveBy(actorId: String, dx: Int, dy: Int) {
        ops += MoveBy(actorId, Literal(dx), Literal(dy), sourceLocation = captureV2Location())
    }

    /** Moves an actor referenced by [ActorRef] by a relative delta using Expr arguments. */
    fun moveBy(actorRef: ActorRef, dx: Expr, dy: Expr) {
        ops += MoveBy(actorRef.id, dx, dy, sourceLocation = captureV2Location())
    }

    /** Moves an actor referenced by [ActorRef] by a relative delta using Int arguments. */
    fun moveBy(actorRef: ActorRef, dx: Int, dy: Int) {
        ops += MoveBy(actorRef.id, Literal(dx), Literal(dy), sourceLocation = captureV2Location())
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    /** Transitions to a scene via [SceneRef]. */
    fun navigate(sceneRef: SceneRef) {
        ops += NavigateTo(sceneRef.id, sourceLocation = captureV2Location())
    }

    // -------------------------------------------------------------------------
    // Systems
    // -------------------------------------------------------------------------

    /**
     * Fires a system event by typed [SystemRef] with optional arguments.
     *
     * Mirrors [playSound] — resolves the system id from [ref] at DSL recording time. The
     * [TriggerSystem] IR node keeps `systemId: String`; the ref→id resolution happens here
     * (D-10/D-11 typed-ref pattern).
     *
     * Usage:
     * ```kotlin
     * @Suppress("UNUSED_VARIABLE") val saves by saveData { slots(2) }
     * // Inside a scene frame block:
     * whenever(buttons.select.pressed) { triggerSystem(saves) }
     * ```
     */
    fun triggerSystem(ref: SystemRef, args: Map<String, Expr> = emptyMap()) {
        ops += TriggerSystem(ref.systemId, args, sourceLocation = captureV2Location())
    }

    // -------------------------------------------------------------------------
    // Audio
    // -------------------------------------------------------------------------

    /** Plays a sound effect by [SoundRef]. */
    fun playSound(ref: SoundRef) {
        ops += PlaySound(ref.id, sourceLocation = captureV2Location())
    }

    /**
     * Plays a music track with optional fade-in and resume support.
     *
     * Emits a [MusicPlay] script op. If [fadeIn] > 0, the GBDK backend emits a fade-in sequence
     * when AudioMixer is configured (falls back to instant play otherwise). If [resume] is true,
     * the track resumes from its saved position rather than restarting.
     *
     * Usage:
     * ```kotlin
     * play(theme)                        // instant play from start
     * play(battle, fadeIn = 30)          // fade in over 30 frames
     * play(theme, resume = true)         // resume from saved position
     * ```
     *
     * @param musicRef Typed reference to a declared music track.
     * @param fadeIn Number of frames to fade in (0 = instant).
     * @param resume If true, resume from saved position instead of restarting.
     */
    fun play(musicRef: MusicRef, fadeIn: Int = 0, resume: Boolean = false) {
        ops +=
            MusicPlay(
                songId = musicRef.id,
                fadeInFrames = fadeIn,
                resume = resume,
                sourceLocation = captureV2Location(),
            )
    }

    /**
     * Stops the currently playing music with an optional fade-out.
     *
     * Emits a [MusicStop] script op. If [fadeOut] > 0, the GBDK backend emits a fade-out sequence
     * when AudioMixer is configured (falls back to instant mute otherwise).
     *
     * Usage:
     * ```kotlin
     * stopMusic()              // instant stop
     * stopMusic(fadeOut = 15) // fade out over 15 frames
     * ```
     *
     * @param fadeOut Number of frames to fade out (0 = instant mute).
     */
    fun stopMusic(fadeOut: Int = 0) {
        ops += MusicStop(fadeOutFrames = fadeOut, sourceLocation = captureV2Location())
    }

    // -------------------------------------------------------------------------
    // Dialog and menus
    // -------------------------------------------------------------------------

    /**
     * Prints text at specific tile coordinates on the window layer.
     *
     * Emits a [io.github.gbkt.core.ir.PrintAt] script op. The [fontMode] parameter selects fixed-
     * width tile rendering (default, DMG-compatible) or variable-width font rendering (GBC
     * preferred).
     *
     * Usage: `printAt(0, 14, "Hello World")`
     */
    fun printAt(
        x: Int,
        y: Int,
        text: String,
        fontMode: io.github.gbkt.core.ir.FontMode = io.github.gbkt.core.ir.FontMode.FIXED_WIDTH,
    ) {
        ops +=
            io.github.gbkt.core.ir.PrintAt(
                x,
                y,
                text,
                fontMode,
                sourceLocation = captureV2Location(),
            )
    }

    /**
     * Returns a builder for centered text on the window layer via the `at` infix function.
     *
     * The [fontMode] parameter selects fixed-width tile rendering (default, DMG-compatible) or
     * variable-width font rendering (GBC preferred).
     *
     * Usage: `printCentered("Game Over") at 9`
     */
    fun printCentered(
        text: String,
        fontMode: io.github.gbkt.core.ir.FontMode = io.github.gbkt.core.ir.FontMode.FIXED_WIDTH,
    ): PrintCenteredBuilder = PrintCenteredBuilder(text, fontMode)

    /**
     * Returns a builder for aligned text on the window layer via the `at` infix function.
     *
     * The [alignment] parameter specifies LEFT, CENTER, or RIGHT alignment within the screen width.
     * The [fontMode] parameter selects fixed-width tile rendering (default, DMG-compatible) or
     * variable-width font rendering (GBC preferred).
     *
     * Usage: `printAligned("Score: 99", TextAlignment.RIGHT) at 0`
     */
    fun printAligned(
        text: String,
        alignment: io.github.gbkt.core.ir.TextAlignment,
        fontMode: io.github.gbkt.core.ir.FontMode = io.github.gbkt.core.ir.FontMode.FIXED_WIDTH,
    ): PrintAlignedBuilder = PrintAlignedBuilder(text, alignment, fontMode)

    /**
     * Clears a rectangular region on the window layer.
     *
     * Emits a [io.github.gbkt.core.ir.ClearRegion] script op.
     *
     * Usage: `clearRegion(0, 14, 20, 4)`
     */
    fun clearRegion(x: Int, y: Int, w: Int, h: Int) {
        ops += io.github.gbkt.core.ir.ClearRegion(x, y, w, h, sourceLocation = captureV2Location())
    }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    /** Prints formatted text at an optional position. */
    fun print(text: String, vararg values: Expr, position: PositionDef? = null) {
        ops += PrintOp(text, values.toList(), position, sourceLocation = captureV2Location())
    }

    /** Moves the text cursor to a computed screen position. Follow with [print] to write text. */
    fun gotoxy(x: Expr, y: Expr) {
        ops += GotoXYOp(x, y, sourceLocation = captureV2Location())
    }

    /** Fades the screen in or out over [frames] frames, with optional continuation. */
    fun fade(fadeIn: Boolean, frames: Int, after: ScriptBuilder.() -> Unit = {}) {
        val loc = captureV2Location()
        val afterBuilder = ScriptBuilder()
        ScriptBuilderContext.with(afterBuilder) { afterBuilder.after() }
        ops += FadeOp(fadeIn, frames, afterBuilder.build(), sourceLocation = loc)
    }

    /** Shows or hides an actor's sprite. */
    fun setVisible(actorId: String, visible: Boolean) {
        ops += SetVisible(actorId, visible, sourceLocation = captureV2Location())
    }

    /**
     * Hides all sprites (convenience for scene enter blocks). Emits [RawOp] with "HIDE_SPRITES;" —
     * the GBDK-2020 macro expands to a hardware register assignment and requires a trailing
     * semicolon.
     */
    fun hideSprites() {
        ops += RawOp("HIDE_SPRITES;", sourceLocation = captureV2Location())
    }

    /**
     * Shows all sprites (convenience for scene enter blocks). Emits [RawOp] with "SHOW_SPRITES;".
     * The GBDK-2020 macro expands to a hardware register assignment and requires a trailing
     * semicolon.
     */
    fun showSprites() {
        ops += RawOp("SHOW_SPRITES;", sourceLocation = captureV2Location())
    }

    /**
     * Clears the screen text layer. Emits [ScreenClear] IR which is lowered scene-aware by
     * `gbkt-backend-gbdk` (Plan 07.4-20): a BG-tilemap scene gets a non-destructive clear
     * (HIDE_SPRITES + _win_clear_region) preserving the BG; a non-BG scene gets `cls()`
     * (back-compat for title/results/gameover). DSL authors do not need to choose.
     */
    fun clear() {
        ops += ScreenClear(sourceLocation = captureV2Location())
    }

    /**
     * Binds the current level's tileset+tilemap into VRAM. Lowers to the typed [BindCurrentLevel]
     * IR node which the GBDK backend emits as `setup_current_level();`.
     *
     * The target function is generated by [GBDKPipeline.buildSetupCurrentLevelFunctionIfNeeded] and
     * is gated on `gameUsesTilemapCollision` — only valid when a platformerPhysics +
     * tilemapCollision system is registered. Emits no build WARNING (unlike the `cEmit` escape
     * hatch).
     */
    fun bindCurrentLevel() {
        ops += BindCurrentLevel(sourceLocation = captureV2Location())
    }

    // -------------------------------------------------------------------------
    // Entity lifecycle
    // -------------------------------------------------------------------------

    /** Spawns (activates) an actor by ID. */
    fun spawnActor(actorId: String) {
        ops += SpawnActor(actorId, sourceLocation = captureV2Location())
    }

    /** Destroys (deactivates) an actor by ID. */
    fun destroyActor(actorId: String) {
        ops += DestroyActor(actorId, sourceLocation = captureV2Location())
    }

    /** Plays a named animation on an actor. */
    fun animate(actorId: String, animation: String) {
        ops += AnimateOp(actorId, animation, sourceLocation = captureV2Location())
    }

    /**
     * Manually transitions an actor's animation state machine to a named state.
     *
     * Resets the frame counter and sets the state variable to the target state constant. Use this
     * for programmatic transitions (e.g. on hit, on jump). Condition-based auto-transitions are
     * defined via [AnimationStatesBuilder.transition] in the actor's [ActorBuilder.animationStates]
     * block.
     *
     * ```kotlin
     * whenever(buttons.a.pressed) { setAnimationState(player, "attack") }
     * ```
     */
    fun setAnimationState(actor: ActorRef, stateName: String) {
        ops +=
            SetAnimationState(
                actorId = actor.id,
                stateName = stateName,
                sourceLocation = captureV2Location(),
            )
    }

    // -------------------------------------------------------------------------
    // Camera
    // -------------------------------------------------------------------------

    /** Issues a camera action with optional arguments. */
    fun cameraOp(action: CameraAction, args: Map<String, Expr> = emptyMap()) {
        ops += CameraOp(action, args, sourceLocation = captureV2Location())
    }

    // -------------------------------------------------------------------------
    // Timing
    // -------------------------------------------------------------------------

    /** Pauses script execution for [frames] frames. */
    fun waitFrames(frames: Int) {
        ops += WaitFrames(frames, sourceLocation = captureV2Location())
    }

    /**
     * Blocking busy-wait for [frames] vertical blanks.
     *
     * Emits a call to `delay_frames(n)` — a HOME-bank C helper that executes a counted
     * wait_vbl_done() loop. This is distinct from [waitFrames], which uses a broken state-machine
     * pattern (resetting the counter every frame). Use [delay] when you need a true sequential
     * pause within a single frame handler (e.g. scoring flash, battle text).
     *
     * Example: `delay(30)` → `delay_frames(30u);` in C output.
     */
    fun delay(frames: Int) {
        ops += CallOp("delay_frames", listOf(Literal(frames)), sourceLocation = captureV2Location())
    }

    // -------------------------------------------------------------------------
    // Function calls
    // -------------------------------------------------------------------------

    /** Calls a helper function by name with arguments. */
    fun callOp(function: String, vararg args: Expr) {
        ops += CallOp(function, args.toList(), sourceLocation = captureV2Location())
    }

    /** Returns from the current function, optionally with a value. */
    fun returnOp(value: Expr? = null) {
        ops += ReturnOp(value, sourceLocation = captureV2Location())
    }

    /** Evaluates a math function and stores the result in a variable. */
    fun mathOp(result: String, op: MathFunction, vararg args: Expr) {
        ops += MathOp(result, op, args.toList(), sourceLocation = captureV2Location())
    }

    // -------------------------------------------------------------------------
    // Pathfinding
    // -------------------------------------------------------------------------

    /**
     * Emits an A* pathfinding step from [npc] toward [target].
     *
     * Calls `pf_find_path()` with tile coordinates derived from pixel positions (divided by
     * gridSize), then calls `pf_step_toward_{npcId}()` to advance the NPC one step.
     *
     * The [PathfindingSystem] must be registered in the game for this to generate correctly.
     * Typically called every N frames in the scene's `frame { }` block:
     * ```kotlin
     * frame {
     *     whenever((frameCount and 7) isEqualTo 0) {
     *         pathfindStep(enemy, player)
     *     }
     * }
     * ```
     */
    fun pathfindStep(npc: ActorRef, target: ActorRef) {
        ops +=
            PathfindStep(
                npcActorId = npc.id,
                targetActorId = target.id,
                sourceLocation = captureV2Location(),
            )
    }

    /**
     * Advances [npc] one step along its waypoint patrol route.
     *
     * Reads `_${npcId}_wp_idx` to find the current waypoint position, moves the NPC toward it, and
     * advances the index when within tileSize distance. The NPC must have a [WaypointRoute]
     * configured via [ActorBuilder.waypoints].
     *
     * ```kotlin
     * frame {
     *     waypointStep(guard)
     * }
     * ```
     */
    fun waypointStep(npc: ActorRef) {
        ops += WaypointStep(npcActorId = npc.id, sourceLocation = captureV2Location())
    }

    // -------------------------------------------------------------------------
    // Physics
    // -------------------------------------------------------------------------

    /**
     * Emits a per-frame physics integration step for [actor].
     *
     * Applies acceleration, gravity, fall speed clamping, and velocity to position. The actor must
     * have a [io.github.gbkt.core.ir.PhysicsConfig] set via [ActorBuilder.physics]. Floor collision
     * and bounce detection are game-specific — add `whenever(actor.y isAbove FLOOR_Y) { ... }` for
     * bounce/reset logic.
     *
     * Typically called every frame in the scene's `frame { }` block:
     * ```kotlin
     * frame {
     *     physicsUpdate(ball)
     *     whenever(ball.y isAbove 144) { ball.vy set -2 }  // floor bounce
     * }
     * ```
     */
    fun physicsUpdate(actor: ActorRef) {
        ops += PhysicsStep(actorId = actor.id, sourceLocation = captureV2Location())
    }

    // -------------------------------------------------------------------------
    // Escape hatch
    // -------------------------------------------------------------------------

    /**
     * Injects raw C code directly into the output.
     *
     * This is an escape hatch for C patterns not yet supported by the DSL. If you find yourself
     * using cEmit() frequently for the same pattern, consider adding proper DSL support instead.
     *
     * Emits a build WARNING to stderr to remind you that this is a temporary measure.
     *
     * Example: `cEmit("HIDE_SPRITES;")` → `HIDE_SPRITES;` in C output.
     */
    @GbktDsl
    fun cEmit(code: String) {
        System.err.println("WARNING: cEmit() used — consider adding DSL support for this pattern")
        ops += RawOp(code, sourceLocation = captureV2Location())
    }

    // -------------------------------------------------------------------------
    // Story flags (GAP-11)
    // -------------------------------------------------------------------------

    /**
     * Sets a story flag to true (1).
     *
     * Emits an [Assign] op targeting the `_flag_{flagName}` C global variable. Use the typed
     * overload [setFlag] with [FlagRef] to eliminate magic string flag names.
     *
     * @param flagName Raw flag name string. Generates `_flag_{flagName} = 1;` in C output.
     */
    fun setFlag(flagName: String) {
        ops +=
            Assign(
                "_flag_$flagName",
                Literal(1),
                AssignOp.SET,
                sourceLocation = captureV2Location(),
            )
    }

    /**
     * Sets a story flag to true (1).
     *
     * Typed overload — eliminates magic flag name strings. Prefer over `setFlag(String)`.
     *
     * ```kotlin
     * val bossDefeated = flags { page("story") { flag("bossDefeated") } }
     * setFlag(bossDefeated)  // → _flag_bossDefeated = 1;
     * ```
     *
     * @param flag Typed [FlagRef] returned by [FlagPageBuilder.flag].
     */
    fun setFlag(flag: FlagRef) {
        setFlag(flag.name)
    }

    /**
     * Clears a story flag to false (0).
     *
     * Emits an [Assign] op targeting the `_flag_{flagName}` C global variable. Use the typed
     * overload [clearFlag] with [FlagRef] to eliminate magic string flag names.
     *
     * @param flagName Raw flag name string. Generates `_flag_{flagName} = 0;` in C output.
     */
    fun clearFlag(flagName: String) {
        ops +=
            Assign(
                "_flag_$flagName",
                Literal(0),
                AssignOp.SET,
                sourceLocation = captureV2Location(),
            )
    }

    /**
     * Clears a story flag to false (0).
     *
     * Typed overload — eliminates magic flag name strings. Prefer over `clearFlag(String)`.
     *
     * @param flag Typed [FlagRef] returned by [FlagPageBuilder.flag].
     */
    fun clearFlag(flag: FlagRef) {
        clearFlag(flag.name)
    }
}

// =============================================================================
// FLAG CHECK EXPRESSION (GAP-11)
// =============================================================================

/**
 * Returns an [Expr] that evaluates to the current value of the named story flag.
 *
 * Produces a [VarRef] to the `_flag_{flagName}` C global variable (non-zero = flag set). Use in
 * [ScriptBuilder.whenever] conditions to react to flag state.
 *
 * String-based overload — prefer the typed [checkFlag] overload with [FlagRef].
 *
 * ```kotlin
 * whenever(checkFlag("bossDefeated")) { navigate(victoryScene) }
 * ```
 *
 * @param flagName Raw flag name string.
 */
fun checkFlag(flagName: String): Expr = VarRef("_flag_$flagName")

/**
 * Returns an [Expr] that evaluates to the current value of the given story flag.
 *
 * Typed overload — eliminates magic flag name strings. Prefer over `checkFlag(String)`.
 *
 * ```kotlin
 * val bossDefeated = flags { page("story") { flag("bossDefeated") } }
 * whenever(checkFlag(bossDefeated)) { navigate(victoryScene) }
 * ```
 *
 * @param flag Typed [FlagRef] returned by [FlagPageBuilder.flag].
 */
fun checkFlag(flag: FlagRef): Expr = checkFlag(flag.name)
