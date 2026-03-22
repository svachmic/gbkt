/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.test

import io.github.gbkt.core.ir.*
import io.github.gbkt.core.ir.CastExpr
import io.github.gbkt.core.ir.VarType

/**
 * JVM execution engine for v2 ScriptOps.
 *
 * Executes game logic in-memory without a ROM or emulator. Intended for unit testing game scripts.
 * All ScriptOp subtypes are handled via a [when] expression. Hardware-dependent ops (audio,
 * visuals, dialog, cursor) are no-op stubs. Arrays are fully simulated via [arrays] map.
 *
 * State managed by the interpreter:
 * - [variables] — global game variables (Long), initialized from [GameIR.variables]
 * - [actorPositions] — actor (x, y) positions, initialized from [GameIR.actors]
 * - [currentSceneId] — the currently active scene ID
 * - [frameCount] — total frames executed via [executeFrame]
 * - [joypad] / [joypadPrev] — input bitmasks for the current and previous frame
 * - [tracingEnabled] / [traceLog] — optional frame-by-frame state change log
 *
 * Hardware-dependent ops (audio, visuals, dialog, etc.) are no-op stubs — they execute without
 * error but have no effect on simulator state.
 */
@Suppress("TooManyFunctions")
class ScriptOpInterpreter(private val game: GameIR) {

    // =========================================================================
    // State
    // =========================================================================

    /** All game variables keyed by name. Reads return 0 for unknown names. */
    val variables: MutableMap<String, Long> = mutableMapOf()

    /** Actor positions as (x, y) pairs, keyed by actor ID. */
    val actorPositions: MutableMap<String, Pair<Int, Int>> = mutableMapOf()

    /** Array storage: arrayName -> LongArray, initialized from GameIR.arrays. */
    private val arrays: MutableMap<String, LongArray> = mutableMapOf()

    /**
     * Pool state tracking: poolId -> list of active flags (true=active, false=free).
     *
     * Initialized lazily on first pool spawn — the list size matches the pool's maxSize.
     * RECYCLE_OLDEST strategy tracks a circular oldest-slot index per pool.
     */
    private val poolSlots: MutableMap<String, MutableList<Boolean>> = mutableMapOf()

    /** Oldest-slot counters for RECYCLE_OLDEST pools. poolId -> oldest index. */
    private val poolOldestIdx: MutableMap<String, Int> = mutableMapOf()

    /** In-memory hash table simulation: collectionName -> (key -> value) */
    private val hashTables: MutableMap<String, MutableMap<Long, Long>> = mutableMapOf()

    /** In-memory ring buffer simulation: collectionName -> FIFO deque of values */
    private val ringBuffers: MutableMap<String, ArrayDeque<Long>> = mutableMapOf()

    /** In-memory pool simulation: poolName -> (slotIndex -> 1L=active). */
    private val pools: MutableMap<String, MutableMap<Long, Long>> = mutableMapOf()

    /** In-memory fixed slot simulation: collectionName -> (slotIndex -> value) */
    private val fixedSlots: MutableMap<String, MutableMap<Int, Long>> = mutableMapOf()

    /** ID of the currently active scene. */
    var currentSceneId: String = game.startScene ?: game.scenes.firstOrNull()?.id ?: ""
        private set

    /** Total frames executed via [executeFrame]. */
    var frameCount: Int = 0
        private set

    /** Current frame input bitmask (D-pad and buttons). */
    var joypad: Int = 0

    /** Previous frame input bitmask (for edge detection). */
    var joypadPrev: Int = 0

    /** When true, state changes are appended to [traceLog]. */
    var tracingEnabled: Boolean = false

    /** Frame-by-frame trace entries (empty unless [tracingEnabled] is set). */
    val traceLog: MutableList<String> = mutableListOf()

    /** Puzzle object lookup map (by ID) — initialized once from GameIR for requires() checks. */
    private val puzzleById: Map<String, PuzzleObjectIR> by lazy {
        game.puzzleObjects.associateBy { it.id }
    }

    init {
        initVariables()
        initActors()
        initArrays()
    }

    // =========================================================================
    // Initialization
    // =========================================================================

    private fun initVariables() {
        for (varDef in game.variables) {
            variables[varDef.name] = varDef.initialValue.toLong()
        }
    }

    private fun initActors() {
        for (actor in game.actors) {
            val pos = actor.position
            actorPositions[actor.id] = Pair(pos.x, pos.y)
            // Make positions accessible as variables via dot notation
            variables["${actor.id}.x"] = pos.x.toLong()
            variables["${actor.id}.y"] = pos.y.toLong()
        }
    }

    private fun initArrays() {
        for (arrayDef in game.arrays) {
            arrays[arrayDef.name] = LongArray(arrayDef.size) { 0L }
        }
    }

    // =========================================================================
    // Public accessors (for testing)
    // =========================================================================

    /** Read a variable value; returns 0 if not set. */
    fun getVariable(name: String): Long = variables[name] ?: 0L

    /** Directly set a variable value (for test setup). Syncs actor positions for "actorId.x/y". */
    fun setVariable(name: String, value: Long) {
        variables[name] = value
        // Sync actor position map so collision detection sees updated coordinates
        if (name.endsWith(".x") || name.endsWith(".y")) {
            val actorId = name.substringBeforeLast(".")
            if (actorPositions.containsKey(actorId)) {
                val (cx, cy) = actorPositions[actorId]!!
                actorPositions[actorId] =
                    if (name.endsWith(".x")) Pair(value.toInt(), cy) else Pair(cx, value.toInt())
            }
        }
    }

    /** Get an actor's (x, y) position. Returns (0, 0) if actor not found. */
    fun getActorPosition(actorId: String): Pair<Int, Int> = actorPositions[actorId] ?: Pair(0, 0)

    // =========================================================================
    // Frame execution
    // =========================================================================

    /**
     * Execute one frame of game logic.
     *
     * Syncs joypad to `__joypad` and `__joypad_prev` variables so scripts can read input via
     * [VarRef]. Then runs the [SceneIR.frameOps] of the current scene sequentially, saves joypad
     * state, and increments the frame counter.
     */
    fun executeFrame() {
        // Sync joypad state to named variables so scripts can read them via VarRef
        variables["__joypad"] = joypad.toLong()
        variables["__joypad_prev"] = joypadPrev.toLong()

        val scene = findScene(currentSceneId)
        scene?.frameOps?.forEach { executeOp(it) }
        joypadPrev = joypad
        frameCount++
    }

    // =========================================================================
    // Scene navigation
    // =========================================================================

    /**
     * Transition to a new scene by ID.
     *
     * Runs [SceneIR.exitOps] of the current scene first, then sets the new scene and runs its
     * [SceneIR.enterOps].
     */
    fun enterScene(sceneId: String) {
        val oldScene = findScene(currentSceneId)
        oldScene?.exitOps?.forEach { executeOp(it) }

        if (tracingEnabled) {
            traceLog.add("frame=$frameCount: scene -> $sceneId")
        }

        currentSceneId = sceneId
        val newScene = findScene(sceneId)
        newScene?.enterOps?.forEach { executeOp(it) }
    }

    private fun findScene(sceneId: String): SceneIR? = game.scenes.find { it.id == sceneId }

    // =========================================================================
    // Op execution — exhaustive when, NO else branch
    // =========================================================================

    /**
     * Execute a single [ScriptOp].
     *
     * The [when] expression covers all known ScriptOp subtypes. Hardware-dependent ops are no-op
     * stubs. Adding new subtypes requires updating this method and the [else] fallback.
     */
    @Suppress("LongMethod")
    fun executeOp(op: ScriptOp) {
        when (op) {
            // --- State mutation ---
            is Assign -> executeAssign(op)
            is ArrayAssign -> executeArrayAssign(op)

            // --- Control flow ---
            is IfOp -> executeIfOp(op)
            is WhileOp -> executeWhileOp(op)
            is ForOp -> executeForOp(op)

            // --- Movement ---
            is SetPosition -> executeSetPosition(op)
            is MoveBy -> executeMoveBy(op)

            // --- Navigation ---
            is NavigateTo -> enterScene(op.sceneId)

            // --- Actor pool lifecycle ---
            is PoolSpawnActor -> executePoolSpawnActor(op)
            is PoolDestroyActor -> executePoolDestroyActor(op)
            is PoolForEachActive -> executePoolForEachActive(op)
            is PoolDestroyAll -> executePoolDestroyAll(op)

            // --- Function calls ---
            is CallOp -> executeCallOp(op)

            // --- Math ---
            is MathOp -> executeMathOp(op)

            // --- Puzzle objects ---
            is ActivatePuzzleObject -> executePuzzleActivate(op)
            is DeactivatePuzzleObject -> executePuzzleDeactivate(op)
            is RevealPuzzleObject -> executePuzzleReveal(op)
            is HidePuzzleObject -> executePuzzleHide(op)

            // --- Hardware-dependent no-op stubs (not simulatable) ---
            is TriggerSystem,
            is PlaySound,
            is MusicPlay,
            is MusicStop,
            is MusicPause,
            is MusicResume,
            is DialogSay,
            is DialogChoice,
            is MenuShow,
            is MenuHide,
            is HudShow,
            is HudHide,
            is PrintAt,
            is PrintCentered,
            is PrintAligned,
            is ClearRegion,
            is ScreenClear,
            is ScreenFill,
            is SetPalette,
            is PrintOp,
            is GotoXYOp,
            is FadeOp,
            is SetVisible,
            is SpawnActor,
            is DestroyActor,
            is AnimateOp,
            is CameraOp,
            is WaitFrames,
            is ReturnOp,
            is RawOp,
            is SetAnimationState,
            is PhysicsStep,
            is PathfindStep,
            is WaypointStep -> {
                /* no-op: hardware-dependent, not modeled in simulation */
            }

            else -> throw UnsupportedOperationException("Unknown ScriptOp: ${op::class.simpleName}")
        }
    }

    // =========================================================================
    // Op handlers
    // =========================================================================

    /** Apply a compound assignment operator to [current] with [value]. */
    private fun applyAssignOp(op: AssignOp, current: Long, value: Long): Long =
        when (op) {
            AssignOp.SET -> value
            AssignOp.ADD -> current + value
            AssignOp.SUB -> current - value
            AssignOp.MUL -> current * value
            AssignOp.DIV -> if (value != 0L) current / value else 0L
            AssignOp.MOD -> if (value != 0L) current % value else 0L
            AssignOp.AND -> current and value
            AssignOp.OR -> current or value
            AssignOp.XOR -> current xor value
        }

    private fun executeAssign(op: Assign) {
        val current = variables[op.target] ?: 0L
        val value = evaluateExpr(op.value)
        val result = applyAssignOp(op.op, current, value)
        variables[op.target] = result
        // Keep actorPositions in sync when a "actorId.x/y" variable is assigned
        if (op.target.endsWith(".x") || op.target.endsWith(".y")) {
            val actorId = op.target.substringBeforeLast(".")
            val pos = actorPositions.getOrPut(actorId) { Pair(0, 0) }
            actorPositions[actorId] =
                if (op.target.endsWith(".x")) Pair(result.toInt(), pos.second)
                else Pair(pos.first, result.toInt())
        }

        if (tracingEnabled) {
            traceLog.add("frame=$frameCount: ${op.target} = $result")
        }
    }

    private fun executeArrayAssign(op: ArrayAssign) {
        val arr = arrays[op.array] ?: return
        val idx = evaluateExpr(op.index).toInt()
        if (idx !in arr.indices) return
        arr[idx] = applyAssignOp(op.op, arr[idx], evaluateExpr(op.value))
    }

    private fun executeIfOp(op: IfOp) {
        if (evaluateExpr(op.condition) != 0L) {
            op.then.forEach { executeOp(it) }
        } else {
            op.otherwise.forEach { executeOp(it) }
        }
    }

    private fun executeWhileOp(op: WhileOp) {
        val maxIterations = 10000
        var iterations = 0
        while (evaluateExpr(op.condition) != 0L && iterations < maxIterations) {
            op.body.forEach { executeOp(it) }
            iterations++
        }
    }

    private fun executeForOp(op: ForOp) {
        val from = evaluateExpr(op.from)
        val to = evaluateExpr(op.to)
        val maxIterations = 10000
        var iterations = 0
        var i = from
        while (i <= to && iterations < maxIterations) {
            variables[op.variable] = i
            op.body.forEach { executeOp(it) }
            i++
            iterations++
        }
    }

    private fun executeSetPosition(op: SetPosition) {
        val x = evaluateExpr(op.x).toInt()
        val y = evaluateExpr(op.y).toInt()
        actorPositions[op.actorId] = Pair(x, y)
        variables["${op.actorId}.x"] = x.toLong()
        variables["${op.actorId}.y"] = y.toLong()
    }

    private fun executeMoveBy(op: MoveBy) {
        val (cx, cy) = actorPositions[op.actorId] ?: Pair(0, 0)
        val dx = evaluateExpr(op.dx).toInt()
        val dy = evaluateExpr(op.dy).toInt()
        val nx = cx + dx
        val ny = cy + dy
        actorPositions[op.actorId] = Pair(nx, ny)
        variables["${op.actorId}.x"] = nx.toLong()
        variables["${op.actorId}.y"] = ny.toLong()
    }

    @Suppress("CyclomaticComplexMethod") // Math operations require a case per function type
    private fun executeMathOp(op: MathOp) {
        val result =
            when (op.op) {
                MathFunction.ABS -> {
                    val a = op.args.getOrNull(0)?.let { evaluateExpr(it) } ?: 0L
                    if (a < 0) -a else a
                }
                MathFunction.MIN -> {
                    val a = op.args.getOrNull(0)?.let { evaluateExpr(it) } ?: 0L
                    val b = op.args.getOrNull(1)?.let { evaluateExpr(it) } ?: 0L
                    if (a < b) a else b
                }
                MathFunction.MAX -> {
                    val a = op.args.getOrNull(0)?.let { evaluateExpr(it) } ?: 0L
                    val b = op.args.getOrNull(1)?.let { evaluateExpr(it) } ?: 0L
                    if (a > b) a else b
                }
                MathFunction.CLAMP -> {
                    val value = op.args.getOrNull(0)?.let { evaluateExpr(it) } ?: 0L
                    val min = op.args.getOrNull(1)?.let { evaluateExpr(it) } ?: 0L
                    val max = op.args.getOrNull(2)?.let { evaluateExpr(it) } ?: 0L
                    when {
                        value < min -> min
                        value > max -> max
                        else -> value
                    }
                }
                MathFunction.RAND -> {
                    // In simulation, return 0 (deterministic for tests)
                    0L
                }
            }
        variables[op.result] = result
    }

    // =========================================================================
    // Expr evaluation — exhaustive when, NO else branch
    // =========================================================================

    /**
     * Evaluate an [Expr] and return its numeric value.
     *
     * The [when] expression covers all known Expr subtypes. Arrays are fully simulated via [arrays]
     * map. CallExpr and StringLiteral return 0 (stubs).
     */
    fun evaluateExpr(expr: Expr): Long =
        when (expr) {
            is Literal -> expr.value.toLong()
            is StringLiteral -> 0L // no-op stub: string in numeric context
            is VarRef -> variables[expr.name] ?: 0L
            is BinaryExpr -> evaluateBinaryExpr(expr)
            is UnaryExpr -> evaluateUnaryExpr(expr)
            is TernaryExpr -> {
                if (evaluateExpr(expr.condition) != 0L) {
                    evaluateExpr(expr.thenExpr)
                } else {
                    evaluateExpr(expr.elseExpr)
                }
            }
            is PropertyAccessExpr -> {
                val pos = actorPositions[expr.objectId]
                when {
                    pos != null && expr.property == "x" -> pos.first.toLong()
                    pos != null && expr.property == "y" -> pos.second.toLong()
                    else -> variables["${expr.objectId}.${expr.property}"] ?: 0L
                }
            }
            is ArrayAccessExpr -> {
                val arr = arrays[expr.array] ?: return 0L
                val idx = evaluateExpr(expr.index).toInt()
                if (idx in arr.indices) arr[idx] else 0L
            }
            is CallExpr -> evaluateCallExpr(expr)
            is CastExpr -> evaluateCastExpr(expr)
            else -> throw UnsupportedOperationException("Unknown Expr: ${expr::class.simpleName}")
        }

    private fun evaluateBinaryExpr(expr: BinaryExpr): Long {
        // Short-circuit logical operators
        return when (expr.op) {
            BinaryOp.LOGICAL_AND -> {
                val left = evaluateExpr(expr.left)
                if (left == 0L) 0L else if (evaluateExpr(expr.right) != 0L) 1L else 0L
            }
            BinaryOp.LOGICAL_OR -> {
                val left = evaluateExpr(expr.left)
                if (left != 0L) 1L else if (evaluateExpr(expr.right) != 0L) 1L else 0L
            }
            else -> {
                val left = evaluateExpr(expr.left)
                val right = evaluateExpr(expr.right)
                when (expr.op) {
                    BinaryOp.ADD -> left + right
                    BinaryOp.SUB -> left - right
                    BinaryOp.MUL -> left * right
                    BinaryOp.DIV -> if (right != 0L) left / right else 0L
                    BinaryOp.MOD -> if (right != 0L) left % right else 0L
                    BinaryOp.AND -> left and right
                    BinaryOp.OR -> left or right
                    BinaryOp.XOR -> left xor right
                    BinaryOp.SHL -> left shl right.toInt()
                    BinaryOp.SHR -> left shr right.toInt()
                    BinaryOp.EQ -> if (left == right) 1L else 0L
                    BinaryOp.NEQ -> if (left != right) 1L else 0L
                    BinaryOp.LT -> if (left < right) 1L else 0L
                    BinaryOp.LTE -> if (left <= right) 1L else 0L
                    BinaryOp.GT -> if (left > right) 1L else 0L
                    BinaryOp.GTE -> if (left >= right) 1L else 0L
                    // Short-circuit ops handled above — these branches unreachable
                    BinaryOp.LOGICAL_AND,
                    BinaryOp.LOGICAL_OR -> 0L
                }
            }
        }
    }

    private fun evaluateUnaryExpr(expr: UnaryExpr): Long {
        val operand = evaluateExpr(expr.operand)
        return when (expr.op) {
            UnaryOp.NEGATE -> -operand
            UnaryOp.BITWISE_NOT -> operand.inv()
            UnaryOp.LOGICAL_NOT -> if (operand == 0L) 1L else 0L
        }
    }

    /**
     * Evaluate a [CastExpr] by truncating/sign-extending to the target type.
     *
     * Mirrors Game Boy hardware behavior:
     * - U8: clamp to 0-255 (take low byte)
     * - U16: clamp to 0-65535 (take low 16 bits)
     * - I8: sign-extend to -128..127
     * - I16: sign-extend to -32768..32767
     */
    private fun evaluateCastExpr(expr: CastExpr): Long {
        val inner = evaluateExpr(expr.inner)
        return when (expr.targetType) {
            VarType.U8 -> inner and 0xFFL
            VarType.U16 -> inner and 0xFFFFL
            VarType.I8 -> {
                val unsigned = (inner and 0xFFL).toInt()
                if (unsigned >= 128) (unsigned - 256).toLong() else unsigned.toLong()
            }
            VarType.I16 -> {
                val unsigned = (inner and 0xFFFFL).toInt()
                if (unsigned >= 32768) (unsigned - 65536).toLong() else unsigned.toLong()
            }
        }
    }

    /**
     * Evaluate a [CallExpr] by function name.
     *
     * Dispatches to in-memory collection state for collection operation families:
     * - `ht_{name}_{op}` — hash table operations (insert, get, contains, remove, size, clear)
     * - `ring_{name}_{op}` — ring buffer operations (push, pop, peek, size, clear)
     * - `pool_{name}_{op}` — pool operations (alloc, free, active_count)
     * - `slot_{name}_{op}` — fixed slot operations (set, get, clear)
     * - `"collides"` — AABB collision check between two actors
     * - All other functions return 0 (stub).
     */
    private fun evaluateCallExpr(expr: CallExpr): Long {
        val fn = expr.function
        val args = expr.args.map { evaluateExpr(it) }

        return when {
            fn == "collides" -> {
                val actorAId = (expr.args.getOrNull(0) as? VarRef)?.name ?: return 0L
                val actorBId = (expr.args.getOrNull(1) as? VarRef)?.name ?: return 0L
                if (checkCollision(actorAId, actorBId)) 1L else 0L
            }
            fn.startsWith("ht_") -> evaluateHashTableCall(fn.removePrefix("ht_"), args)
            fn.startsWith("ring_") -> evaluateRingBufferCall(fn.removePrefix("ring_"), args)
            fn.startsWith("pool_") -> evaluatePoolCall(fn.removePrefix("pool_"), args)
            fn.startsWith("slot_") -> evaluateFixedSlotCall(fn.removePrefix("slot_"), args)
            else -> 0L // no-op stub for unknown function calls
        }
    }

    /**
     * Splits a `{name}_{op}` string by finding the last occurrence of a known operation suffix.
     * Returns (name, op) or null if no known suffix matches.
     */
    private fun splitCollectionOp(rest: String, knownOps: List<String>): Pair<String, String>? {
        // Check longer suffixes first to avoid partial matches
        for (op in knownOps.sortedByDescending { it.length }) {
            val suffix = "_$op"
            if (rest.endsWith(suffix) && rest.length > suffix.length) {
                return rest.removeSuffix(suffix) to op
            }
        }
        return null
    }

    /** Hash table dispatch: `{name}_{op}` where op is insert/get/contains/remove/size/clear. */
    private fun evaluateHashTableCall(rest: String, args: List<Long>): Long {
        val (name, op) =
            splitCollectionOp(rest, listOf("insert", "get", "contains", "remove", "size", "clear"))
                ?: return 0L
        return when (op) {
            "insert" -> {
                hashTables.getOrPut(name) { mutableMapOf() }[args[0]] = args[1]
                0L
            }
            "get" -> hashTables[name]?.get(args[0]) ?: 0L
            "contains" -> if (hashTables[name]?.containsKey(args[0]) == true) 1L else 0L
            "remove" -> {
                hashTables[name]?.remove(args[0])
                0L
            }
            "size" -> (hashTables[name]?.size ?: 0).toLong()
            "clear" -> {
                hashTables[name]?.clear()
                0L
            }
            else -> 0L
        }
    }

    /** Ring buffer dispatch: `{name}_{op}` where op is push/pop/peek/size/clear. */
    private fun evaluateRingBufferCall(rest: String, args: List<Long>): Long {
        val (name, op) =
            splitCollectionOp(rest, listOf("push", "pop", "peek", "size", "clear")) ?: return 0L
        return when (op) {
            "push" -> {
                ringBuffers.getOrPut(name) { ArrayDeque() }.addLast(args[0])
                0L
            }
            "pop" -> ringBuffers[name]?.removeFirstOrNull() ?: 0L
            "peek" -> ringBuffers[name]?.firstOrNull() ?: 0L
            "size" -> (ringBuffers[name]?.size ?: 0).toLong()
            "clear" -> {
                ringBuffers[name]?.clear()
                0L
            }
            else -> 0L
        }
    }

    /**
     * Pool dispatch: `{name}_{op}` where op is alloc/free/active_count.
     *
     * Alloc scans for the lowest available index to match the C backend's bitmap-scan strategy.
     */
    private fun evaluatePoolCall(rest: String, args: List<Long>): Long {
        val (name, op) =
            splitCollectionOp(rest, listOf("alloc", "free", "active_count")) ?: return 0L
        return when (op) {
            "alloc" -> {
                val pool = pools.getOrPut(name) { mutableMapOf() }
                val idx = generateSequence(0L) { it + 1 }.first { it !in pool }
                pool[idx] = 1L // 1 = active
                idx
            }
            "free" -> {
                pools[name]?.remove(args[0])
                0L
            }
            "active_count" -> (pools[name]?.size ?: 0).toLong()
            else -> 0L
        }
    }

    /** Fixed slot dispatch: `{name}_{op}` where op is set/get/clear. */
    private fun evaluateFixedSlotCall(rest: String, args: List<Long>): Long {
        val (name, op) = splitCollectionOp(rest, listOf("set", "get", "clear")) ?: return 0L
        return when (op) {
            "set" -> {
                fixedSlots.getOrPut(name) { mutableMapOf() }[args[0].toInt()] = args[1]
                0L
            }
            "get" -> fixedSlots[name]?.get(args[0].toInt()) ?: 0L
            "clear" -> {
                fixedSlots[name]?.remove(args[0].toInt())
                0L
            }
            else -> 0L
        }
    }

    /**
     * Dispatch a side-effecting [CallOp] to collection state.
     *
     * Collection operations that mutate state (insert, push, set, free, clear, alloc) are
     * dispatched here so that [CallOp] usages in scripts properly affect in-memory collection
     * state. The return value of [evaluateCallExpr] is discarded since [CallOp] is a statement.
     */
    private fun executeCallOp(op: CallOp) {
        // Reuse CallExpr dispatch by constructing a synthetic CallExpr
        val syntheticExpr = CallExpr(op.function, op.args)
        evaluateCallExpr(syntheticExpr)
    }

    // =========================================================================
    // Collision detection (bounding box)
    // =========================================================================

    /**
     * Check if two actors' hitboxes overlap.
     *
     * Uses actor positions from [actorPositions] and hitbox dimensions from [GameIR.actors]. Falls
     * back to a default 8x8 hitbox if none is defined.
     */
    fun checkCollision(actorA: String, actorB: String): Boolean {
        val (ax, ay) = actorPositions[actorA] ?: return false
        val (bx, by) = actorPositions[actorB] ?: return false

        val aIr = game.actors.find { it.id == actorA }
        val bIr = game.actors.find { it.id == actorB }

        // Prefer explicit ActorIR.hitbox; fall back to sprite's hitbox; default 8x8
        val aHitbox = aIr?.hitbox ?: aIr?.sprite?.hitbox ?: HitboxDef(0, 0, 8, 8)
        val bHitbox = bIr?.hitbox ?: bIr?.sprite?.hitbox ?: HitboxDef(0, 0, 8, 8)

        val aLeft = ax + aHitbox.x
        val aTop = ay + aHitbox.y
        val aRight = aLeft + aHitbox.width
        val aBottom = aTop + aHitbox.height

        val bLeft = bx + bHitbox.x
        val bTop = by + bHitbox.y
        val bRight = bLeft + bHitbox.width
        val bBottom = bTop + bHitbox.height

        return aLeft < bRight && aRight > bLeft && aTop < bBottom && aBottom > bTop
    }

    // =========================================================================
    // Actor pool simulation
    // =========================================================================

    /**
     * Execute a [PoolSpawnActor] op.
     *
     * Looks up the pool configuration from [GameIR.actorPools]. Finds a free slot (SILENT_NOOP:
     * stores 0xFF in last-slot variable if full; RECYCLE_OLDEST: reuses oldest active slot).
     */
    private fun executePoolSpawnActor(op: PoolSpawnActor) {
        val poolIR = game.actorPools.find { it.id == op.poolId } ?: return
        val maxSize = poolIR.config.maxSize
        val slots = poolSlots.getOrPut(op.poolId) { MutableList(maxSize) { false } }
        val x = evaluateExpr(op.x).toInt()
        val y = evaluateExpr(op.y).toInt()

        val freeIdx = slots.indexOfFirst { !it }
        val slotIdx =
            if (freeIdx != -1) {
                freeIdx
            } else {
                when (poolIR.config.overflowStrategy) {
                    PoolOverflowStrategy.SILENT_NOOP -> {
                        variables["_pool_${op.poolId}_last_slot"] = 0xFFL
                        return
                    }
                    PoolOverflowStrategy.RECYCLE_OLDEST -> {
                        val oldest = poolOldestIdx.getOrDefault(op.poolId, 0)
                        poolOldestIdx[op.poolId] = (oldest + 1) % maxSize
                        oldest
                    }
                }
            }

        slots[slotIdx] = true
        variables["_pool_${op.poolId}_x_$slotIdx"] = x.toLong()
        variables["_pool_${op.poolId}_y_$slotIdx"] = y.toLong()
        variables["_pool_${op.poolId}_last_slot"] = slotIdx.toLong()
    }

    /**
     * Execute a [PoolDestroyActor] op.
     *
     * Marks the given slot inactive and removes its position state variables.
     */
    private fun executePoolDestroyActor(op: PoolDestroyActor) {
        val slotIdx = evaluateExpr(op.slotExpr).toInt()
        val slots = poolSlots[op.poolId] ?: return
        if (slotIdx in slots.indices) {
            op.deathCallbackOps.forEach { executeOp(it) }
            slots[slotIdx] = false
            variables.remove("_pool_${op.poolId}_x_$slotIdx")
            variables.remove("_pool_${op.poolId}_y_$slotIdx")
        }
    }

    /**
     * Execute a [PoolForEachActive] op.
     *
     * Iterates over all active slots in the pool. For each active slot, sets the slot index
     * variable and executes the body ops.
     */
    private fun executePoolForEachActive(op: PoolForEachActive) {
        val slots = poolSlots[op.poolId] ?: return
        for (i in slots.indices) {
            if (slots[i]) {
                variables[op.slotVarName] = i.toLong()
                for (bodyOp in op.body) {
                    executeOp(bodyOp)
                }
            }
        }
        // Clean up slot variable after loop
        variables.remove(op.slotVarName)
    }

    /**
     * Execute a [PoolDestroyAll] op.
     *
     * Marks all pool slots as inactive and removes their position state variables.
     */
    private fun executePoolDestroyAll(op: PoolDestroyAll) {
        val slots = poolSlots[op.poolId] ?: return
        for (i in slots.indices) {
            if (slots[i]) {
                slots[i] = false
                variables.remove("_pool_${op.poolId}_x_$i")
                variables.remove("_pool_${op.poolId}_y_$i")
            }
        }
    }

    /** Get the number of active slots in a pool (for test assertions). */
    fun getPoolActiveCount(poolId: String): Int = poolSlots[poolId]?.count { it } ?: 0

    /** Check if a specific pool slot is active (for test assertions). */
    fun isPoolSlotActive(poolId: String, slot: Int): Boolean =
        poolSlots[poolId]?.getOrNull(slot) ?: false

    // =========================================================================
    // Puzzle object helpers
    // =========================================================================

    /** Sanitize a puzzle object ID for use in variable names (mirrors codegen). */
    private fun sanitizePuzzleId(id: String): String = id.replace('-', '_').replace(' ', '_')

    /**
     * Return the type-specific state variable name for a puzzle object.
     *
     * Mirrors the variable naming in [GBDKSystemVisitor.buildPuzzleObjectFunctions]:
     * - Switch → `_switch_{id}_active`
     * - Door → `_door_{id}_open`
     * - Pressure plate → `_plate_{id}_pressed`
     * - Timed block → `_timedblock_{id}_solid`
     * - Trigger / unknown → `_puzzle_{id}_active`
     */
    private fun puzzleActiveVar(id: String, obj: PuzzleObjectIR?): String =
        when (obj) {
            is SwitchObjectIR -> "_switch_${id}_active"
            is DoorObjectIR -> "_door_${id}_open"
            is PressurePlateObjectIR -> "_plate_${id}_pressed"
            is TimedBlockObjectIR -> "_timedblock_${id}_solid"
            is TriggerObjectIR,
            null -> "_puzzle_${id}_active"
        }

    private fun puzzleHiddenVar(id: String, obj: PuzzleObjectIR?): String =
        when (obj) {
            is SwitchObjectIR -> "_switch_${id}_hidden"
            is DoorObjectIR -> "_door_${id}_hidden"
            is PressurePlateObjectIR -> "_plate_${id}_hidden"
            is TimedBlockObjectIR -> "_timedblock_${id}_hidden"
            is TriggerObjectIR -> "_trigger_${id}_hidden"
            null -> "_puzzle_${id}_hidden"
        }

    /**
     * Execute [ActivatePuzzleObject] with requires() guard and type-correct variable tracking.
     *
     * Mirrors the codegen guard: if any required object is not active, skip activation.
     */
    private fun executePuzzleActivate(op: ActivatePuzzleObject) {
        val puzzleObj = puzzleById[op.objectId]
        val sanitizedId = sanitizePuzzleId(op.objectId)
        // Check requires() — if any required object is not active, skip activation
        val requiresSatisfied =
            puzzleObj?.requires?.all { reqId ->
                val sanitizedReqId = sanitizePuzzleId(reqId)
                val reqObj = puzzleById[reqId]
                val activeVar = puzzleActiveVar(sanitizedReqId, reqObj)
                (variables[activeVar] ?: 0L) != 0L
            } ?: true
        if (requiresSatisfied) {
            // Track activation using type-correct variable names (mirrors codegen)
            val activeVar = puzzleActiveVar(sanitizedId, puzzleObj)
            variables[activeVar] = 1L
            // Also track in legacy key for backward compatibility
            variables["_puzzle_${sanitizedId}_active"] = 1L
            if (tracingEnabled) {
                traceLog.add("frame=$frameCount: puzzle activate ${op.objectId}")
            }
        } else {
            if (tracingEnabled) {
                traceLog.add(
                    "frame=$frameCount: puzzle activate ${op.objectId} BLOCKED (requires not satisfied)"
                )
            }
        }
    }

    private fun executePuzzleDeactivate(op: DeactivatePuzzleObject) {
        val puzzleObj = puzzleById[op.objectId]
        val sanitizedId = sanitizePuzzleId(op.objectId)
        val activeVar = puzzleActiveVar(sanitizedId, puzzleObj)
        variables[activeVar] = 0L
        variables["_puzzle_${sanitizedId}_active"] = 0L
        if (tracingEnabled) {
            traceLog.add("frame=$frameCount: puzzle deactivate ${op.objectId}")
        }
    }

    private fun executePuzzleReveal(op: RevealPuzzleObject) {
        val sanitizedId = sanitizePuzzleId(op.objectId)
        val puzzleObj = puzzleById[op.objectId]
        val hiddenVar = puzzleHiddenVar(sanitizedId, puzzleObj)
        variables[hiddenVar] = 0L
        variables["_puzzle_${sanitizedId}_hidden"] = 0L
        if (tracingEnabled) {
            traceLog.add("frame=$frameCount: puzzle reveal ${op.objectId}")
        }
    }

    private fun executePuzzleHide(op: HidePuzzleObject) {
        val sanitizedId = sanitizePuzzleId(op.objectId)
        val puzzleObj = puzzleById[op.objectId]
        val hiddenVar = puzzleHiddenVar(sanitizedId, puzzleObj)
        variables[hiddenVar] = 1L
        variables["_puzzle_${sanitizedId}_hidden"] = 1L
        if (tracingEnabled) {
            traceLog.add("frame=$frameCount: puzzle hide ${op.objectId}")
        }
    }
}
