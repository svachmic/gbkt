/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "TooManyFunctions"
) // Operator extensions require one function per operator/type combination

package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.ArrayAccessExpr
import io.github.gbkt.core.ir.ArrayAssign
import io.github.gbkt.core.ir.ArrayDef
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.ForOp
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.VariableDef
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// =============================================================================
// GAME BUILDER CONTEXT (thread-local for delegate registration)
// =============================================================================

/**
 * Thread-local holder for the current [GameBuilder] during DSL execution.
 *
 * Variable delegates need to register [VariableDef] instances with the owning builder. Since Kotlin
 * local variable delegates don't receive a typed `thisRef` in `provideDelegate`, we use a
 * thread-local context — the same pattern used by [RecordingContext] in the v1 DSL.
 *
 * The [GameBuilder] sets this during the execution of its builder lambda.
 *
 * Also tracks [transientVarNames] — names of variables marked with `transient = true` in their
 * `u8Var`/`i8Var` etc. declaration. SaveDataBuilder.build() reads this set to exclude transient
 * variables from save/load SRAM layout.
 */
object GameBuilderContext {
    private val holder = ThreadLocal<GameBuilder?>()
    private val transientHolder = ThreadLocal<MutableSet<String>>()

    val current: GameBuilder?
        get() = holder.get()

    /** The set of variable names marked transient in the current game builder scope. */
    val transientVarNames: Set<String>
        get() = transientHolder.get() ?: emptySet()

    /** Marks [name] as transient so that SaveDataBuilder excludes it from SRAM layout. */
    fun markTransient(name: String) {
        transientHolder.get()?.add(name)
    }

    fun <T> with(builder: GameBuilder, block: () -> T): T {
        val previous = holder.get()
        val previousTransient = transientHolder.get()
        holder.set(builder)
        transientHolder.set(mutableSetOf())
        return try {
            block()
        } finally {
            holder.set(previous)
            transientHolder.set(previousTransient)
        }
    }
}

// =============================================================================
// ASSIGNABLE VARIABLE WRAPPER
// =============================================================================

/**
 * Lightweight wrapper for a declared variable.
 *
 * Tracks the variable name for use in [ScriptBuilder] operations. Call [toExpr] to get the [Expr]
 * representation for use in conditions and assignments.
 *
 * Note: Cannot implement [Expr] directly because [Expr] is a sealed interface defined in the `ir`
 * package (Kotlin seals prevent cross-package subclasses).
 */
data class AssignableVar(
    val name: String,
    val wrapAt: Int? = null,
    val fractionalBits: Int? = null,
) {
    /** Returns this variable as a [VarRef] expression for use in script ops. */
    fun toExpr(): Expr = VarRef(name)

    override fun toString(): String = name
}

// =============================================================================
// ASSIGNABLE VAR OPERATOR EXTENSIONS
// =============================================================================

// --- Assignment operators (emit into ScriptBuilderContext.current) ---

/**
 * Sets [this] variable to [value] inside the active [ScriptBuilder].
 *
 * Requires that this call occurs inside a `ScriptBuilderContext.with()` block (e.g. inside `scene {
 * frame { ... } }` or inside `whenever { ... }`). Throws if no builder is active.
 */
infix fun AssignableVar.set(value: Expr) {
    ScriptBuilderContext.current?.assign(name, value, AssignOp.SET)
        ?: error("set() called outside a ScriptBuilder block")
}

/** Sets [this] variable to [value] (Int auto-wrap). */
infix fun AssignableVar.set(value: Int) = set(Literal(value))

/** Sets [this] variable to [other]'s current value. */
infix fun AssignableVar.set(other: AssignableVar) = set(other.toExpr())

/**
 * Sets [this] variable to 1 if [value] is true, 0 if false.
 *
 * Enables idiomatic `ball.visible set true` pattern in DSL code.
 */
infix fun AssignableVar.set(value: Boolean) = set(Literal(if (value) 1 else 0))

// =============================================================================
// WRAP-GUARD HELPERS (D-14 / D-15 / D-16)
// =============================================================================

/**
 * Returns true iff [n] is a positive power of two (e.g. 2, 4, 8, 16, 32, …). Runs at DSL/JVM time
 * during script recording — never emitted to GB C output.
 */
private fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

/**
 * Emits a post-mutation wrap guard into [sb] for variable [varName] with wrap-boundary [n].
 *
 * - Power-of-two [n]: emits `varName = varName & (n-1)` (bitmask path, D-15).
 * - Non-power-of-two [n]: emits `if (varName >= n) { varName = 0 }` (compare-reset path, D-15).
 *
 * Per D-16, this must be called AFTER the primary mutation assign so the guard is adjacent.
 */
private fun emitWrapGuard(sb: ScriptBuilder, varName: String, n: Int) {
    if (isPowerOfTwo(n)) {
        // Bitmask: varName = varName & (n-1)
        sb.assign(varName, BinaryExpr(VarRef(varName), BinaryOp.AND, Literal(n - 1)), AssignOp.SET)
    } else {
        // Compare-reset: if (varName >= n) { varName = 0 }
        sb.whenever(BinaryExpr(VarRef(varName), BinaryOp.GTE, Literal(n))) {
            assign(varName, Literal(0), AssignOp.SET)
        }
    }
}

/** Adds [value] to [this] variable (compound assignment). */
operator fun AssignableVar.plusAssign(value: Expr) {
    val sb = ScriptBuilderContext.current ?: error("+=  called outside a ScriptBuilder block")
    sb.assign(name, value, AssignOp.ADD)
    wrapAt?.let { n -> emitWrapGuard(sb, name, n) }
}

/** Adds [value] (Int) to [this] variable. */
operator fun AssignableVar.plusAssign(value: Int) = plusAssign(Literal(value))

/** Adds [other]'s value to [this] variable. */
operator fun AssignableVar.plusAssign(other: AssignableVar) = plusAssign(other.toExpr())

/** Subtracts [value] from [this] variable (compound assignment). */
operator fun AssignableVar.minusAssign(value: Expr) {
    val sb = ScriptBuilderContext.current ?: error("-= called outside a ScriptBuilder block")
    sb.assign(name, value, AssignOp.SUB)
    wrapAt?.let { n -> emitWrapGuard(sb, name, n) }
}

/** Subtracts [value] (Int) from [this] variable. */
operator fun AssignableVar.minusAssign(value: Int) = minusAssign(Literal(value))

/** Subtracts [other]'s value from [this] variable. */
operator fun AssignableVar.minusAssign(other: AssignableVar) = minusAssign(other.toExpr())

/** Multiplies [this] variable by [value] (compound assignment). */
operator fun AssignableVar.timesAssign(value: Expr) {
    ScriptBuilderContext.current?.assign(name, value, AssignOp.MUL)
        ?: error("*= called outside a ScriptBuilder block")
}

/** Multiplies [this] variable by [value] (Int). */
operator fun AssignableVar.timesAssign(value: Int) = timesAssign(Literal(value))

/** Multiplies [this] variable by [other]'s value. */
operator fun AssignableVar.timesAssign(other: AssignableVar) = timesAssign(other.toExpr())

/** Divides [this] variable by [value] (compound assignment). */
operator fun AssignableVar.divAssign(value: Expr) {
    ScriptBuilderContext.current?.assign(name, value, AssignOp.DIV)
        ?: error("/= called outside a ScriptBuilder block")
}

/** Divides [this] variable by [value] (Int). */
operator fun AssignableVar.divAssign(value: Int) = divAssign(Literal(value))

/** Divides [this] variable by [other]'s value. */
operator fun AssignableVar.divAssign(other: AssignableVar) = divAssign(other.toExpr())

/** Applies modulo [value] to [this] variable (compound assignment). */
operator fun AssignableVar.remAssign(value: Expr) {
    ScriptBuilderContext.current?.assign(name, value, AssignOp.MOD)
        ?: error("%= called outside a ScriptBuilder block")
}

/** Applies modulo [value] (Int) to [this] variable. */
operator fun AssignableVar.remAssign(value: Int) = remAssign(Literal(value))

/** Applies modulo [other]'s value to [this] variable. */
operator fun AssignableVar.remAssign(other: AssignableVar) = remAssign(other.toExpr())

/**
 * Increments [this] variable by 1 (side-effect into active ScriptBuilder).
 *
 * Works with `var` delegates (e.g. `var score by u8Var(0)`). Kotlin calls `inc()` then `setValue()`
 * (which is a no-op). The side effect — emitting an Assign op — happens here.
 */
operator fun AssignableVar.inc(): AssignableVar {
    val sb = ScriptBuilderContext.current ?: error("++ called outside a ScriptBuilder block")
    sb.assign(name, BinaryExpr(VarRef(name), BinaryOp.ADD, Literal(1)), AssignOp.SET)
    wrapAt?.let { n -> emitWrapGuard(sb, name, n) }
    return this
}

/**
 * Decrements [this] variable by 1 (side-effect into active ScriptBuilder).
 *
 * Works with `var` delegates. See [inc] for semantics.
 */
operator fun AssignableVar.dec(): AssignableVar {
    val sb = ScriptBuilderContext.current ?: error("-- called outside a ScriptBuilder block")
    sb.assign(name, BinaryExpr(VarRef(name), BinaryOp.SUB, Literal(1)), AssignOp.SET)
    wrapAt?.let { n -> emitWrapGuard(sb, name, n) }
    return this
}

// --- Arithmetic operators (return Expr for conditions/right-side use) ---

operator fun AssignableVar.plus(other: Expr): Expr = toExpr() + other

operator fun AssignableVar.plus(other: Int): Expr = toExpr() + other

operator fun AssignableVar.plus(other: AssignableVar): Expr = toExpr() + other.toExpr()

operator fun AssignableVar.minus(other: Expr): Expr = toExpr() - other

operator fun AssignableVar.minus(other: Int): Expr = toExpr() - other

operator fun AssignableVar.minus(other: AssignableVar): Expr = toExpr() - other.toExpr()

operator fun AssignableVar.times(other: Expr): Expr = toExpr() * other

operator fun AssignableVar.times(other: Int): Expr = toExpr() * other

operator fun AssignableVar.times(other: AssignableVar): Expr = toExpr() * other.toExpr()

operator fun AssignableVar.div(other: Expr): Expr = toExpr() / other

operator fun AssignableVar.div(other: Int): Expr = toExpr() / other

operator fun AssignableVar.div(other: AssignableVar): Expr = toExpr() / other.toExpr()

operator fun AssignableVar.rem(other: Expr): Expr = toExpr() % other

operator fun AssignableVar.rem(other: Int): Expr = toExpr() % other

operator fun AssignableVar.rem(other: AssignableVar): Expr = toExpr() % other.toExpr()

operator fun AssignableVar.unaryMinus(): Expr = -toExpr()

// --- Comparison operators (return Expr for whenever/ifOp conditions) ---

infix fun AssignableVar.isAbove(other: Int): Expr = toExpr() isAbove other

infix fun AssignableVar.isAbove(other: Expr): Expr = toExpr() isAbove other

infix fun AssignableVar.isAbove(other: AssignableVar): Expr = toExpr() isAbove other.toExpr()

infix fun AssignableVar.isBelow(other: Int): Expr = toExpr() isBelow other

infix fun AssignableVar.isBelow(other: Expr): Expr = toExpr() isBelow other

infix fun AssignableVar.isBelow(other: AssignableVar): Expr = toExpr() isBelow other.toExpr()

infix fun AssignableVar.isAtLeast(other: Int): Expr = toExpr() isAtLeast other

infix fun AssignableVar.isAtLeast(other: Expr): Expr = toExpr() isAtLeast other

infix fun AssignableVar.isAtLeast(other: AssignableVar): Expr = toExpr() isAtLeast other.toExpr()

infix fun AssignableVar.isAtMost(other: Int): Expr = toExpr() isAtMost other

infix fun AssignableVar.isAtMost(other: Expr): Expr = toExpr() isAtMost other

infix fun AssignableVar.isAtMost(other: AssignableVar): Expr = toExpr() isAtMost other.toExpr()

infix fun AssignableVar.isEqualTo(other: Int): Expr = toExpr() isEqualTo other

infix fun AssignableVar.isEqualTo(other: Expr): Expr = toExpr() isEqualTo other

infix fun AssignableVar.isEqualTo(other: AssignableVar): Expr = toExpr() isEqualTo other.toExpr()

infix fun AssignableVar.isNotEqualTo(other: Int): Expr = toExpr() isNotEqualTo other

infix fun AssignableVar.isNotEqualTo(other: Expr): Expr = toExpr() isNotEqualTo other

infix fun AssignableVar.isNotEqualTo(other: AssignableVar): Expr =
    toExpr() isNotEqualTo other.toExpr()

// --- Logical operators ---

infix fun AssignableVar.logicalAnd(other: Expr): Expr = toExpr() logicalAnd other

infix fun AssignableVar.logicalOr(other: Expr): Expr = toExpr() logicalOr other

fun AssignableVar.not(): Expr = toExpr().not()

// --- Bitwise operators ---

infix fun AssignableVar.and(other: Expr): Expr = toExpr() and other

infix fun AssignableVar.and(other: Int): Expr = toExpr() and Literal(other)

infix fun AssignableVar.and(other: AssignableVar): Expr = toExpr() and other.toExpr()

infix fun AssignableVar.or(other: Expr): Expr = toExpr() or other

infix fun AssignableVar.or(other: Int): Expr = toExpr() or Literal(other)

infix fun AssignableVar.or(other: AssignableVar): Expr = toExpr() or other.toExpr()

infix fun AssignableVar.xor(other: Expr): Expr = toExpr() xor other

infix fun AssignableVar.xor(other: Int): Expr = toExpr() xor Literal(other)

infix fun AssignableVar.xor(other: AssignableVar): Expr = toExpr() xor other.toExpr()

infix fun AssignableVar.shl(other: Expr): Expr = toExpr() shl other

infix fun AssignableVar.shl(other: Int): Expr = toExpr() shl Literal(other)

infix fun AssignableVar.shr(other: Expr): Expr = toExpr() shr other

infix fun AssignableVar.shr(other: Int): Expr = toExpr() shr Literal(other)

fun AssignableVar.inv(): Expr = toExpr().inv()

// =============================================================================
// PROPERTY DELEGATE CLASSES
// =============================================================================

/**
 * Base property delegate that registers a [VariableDef] with the current [GameBuilder] and returns
 * an [AssignableVar] for use in script expressions.
 *
 * The property name is captured via [provideDelegate]. The owning builder is resolved through
 * [GameBuilderContext] (thread-local), which is set by [GameBuilder] during the execution of its
 * DSL lambda.
 *
 * Usage:
 * ```kotlin
 * game("MyGame") {
 *     var score by u8Var(0)   // registers VariableDef(name="score", type=U8, initialValue=0)
 * }
 * ```
 */
abstract class VarDelegate(
    private val type: VarType,
    private val initialValue: Int,
    private val transient: Boolean = false,
) : ReadWriteProperty<Any?, AssignableVar> {
    protected var resolvedName: String? = null

    /**
     * Single-use guard. Each `by <factory>(...)` binding must use its own delegate instance.
     * Reusing one instance across two `by` bindings throws [IllegalStateException] at build time.
     */
    private var delegateUsed: Boolean = false

    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadWriteProperty<Any?, AssignableVar> {
        check(!delegateUsed) {
            "Delegate instance reused: provideDelegate was already called" +
                (resolvedName?.let { " for property '$it'" } ?: "") +
                ". Create a new delegate instance for each property binding."
        }
        delegateUsed = true
        val name = property.name
        resolvedName = name
        // Register with the current GameBuilder via thread-local context
        val context =
            GameBuilderContext.current
                ?: error(
                    "Variable '$name' declared outside a game {} block. " +
                        "Variable delegates must be used inside game { } to be registered."
                )
        context.registerVariable(VariableDef(name, type, initialValue))
        // Mark as transient if requested — SaveDataBuilder excludes transient vars from SRAM
        if (transient) {
            GameBuilderContext.markTransient(name)
        }
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): AssignableVar {
        return AssignableVar(resolvedName ?: property.name)
    }

    /**
     * Setting the variable in DSL is a no-op at the delegate level.
     *
     * Variable mutations in game scripts are recorded via [ScriptBuilder.assign], not via property
     * assignment on the delegate. The `var` declaration allows DSL sugar like `var score by
     * u8Var(0)` while still registering the variable.
     */
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: AssignableVar) {
        // No-op: assignment to the delegate itself is not meaningful.
        // Script-level mutations use ScriptBuilder.assign().
    }
}

/**
 * Delegate for `var x by u8Var(0)` — UINT8 variable (0–255).
 *
 * Optional [wrapAt] parameter: when set, every mutation (++, --, +=, -=) emits a wrap guard
 * immediately after the mutation op (D-14 / D-15 / D-16). Power-of-two [wrapAt] emits a bitmask (`&
 * (N-1)`); non-power-of-two emits a compare-reset (`if v >= N { v = 0 }`).
 */
class U8VarDelegate(initialValue: Int, val wrapAt: Int? = null, transient: Boolean = false) :
    VarDelegate(VarType.U8, initialValue, transient) {
    override fun getValue(thisRef: Any?, property: KProperty<*>): AssignableVar {
        return AssignableVar(resolvedName ?: property.name, wrapAt = wrapAt)
    }
}

/** Delegate for `var x by u16Var(0)` — UINT16 variable (0–65535). */
class U16VarDelegate(initialValue: Int, transient: Boolean = false) :
    VarDelegate(VarType.U16, initialValue, transient)

/** Delegate for `var x by i8Var(0)` — INT8 variable (-128–127). */
class I8VarDelegate(initialValue: Int, transient: Boolean = false) :
    VarDelegate(VarType.I8, initialValue, transient)

/** Delegate for `var x by i16Var(0)` — INT16 variable (-32768–32767). */
class I16VarDelegate(initialValue: Int, transient: Boolean = false) :
    VarDelegate(VarType.I16, initialValue, transient)

/**
 * Delegate for `var posX by i16FixedVar(64)` — 12.4 fixed-point INT16 position variable.
 *
 * Stores the initial value as [initialPixels] × (2^[fractionalBits]). Default fractional bits = 4
 * (12.4 fixed-point: high 12 bits = pixel coordinate, low 4 bits = sub-pixel). Use `.toPixel()` on
 * the returned [AssignableVar] to extract the pixel coordinate for rendering. For velocity / speed
 * variables use [i16Var] directly.
 *
 * Single-use: each `by i16FixedVar(...)` binding must have its own delegate instance.
 */
class I16FixedVarDelegate(
    initialPixels: Int,
    val fractionalBits: Int = 4,
    transient: Boolean = false,
) : VarDelegate(VarType.I16, initialPixels shl fractionalBits, transient) {
    override fun getValue(thisRef: Any?, property: KProperty<*>): AssignableVar =
        AssignableVar(resolvedName ?: property.name, fractionalBits = fractionalBits)
}

// =============================================================================
// TOP-LEVEL FACTORY FUNCTIONS
// =============================================================================

/**
 * Creates a u8 (UINT8) variable delegate with the given initial value.
 *
 * @param initial Initial value (0–255).
 * @param wrapAt When set, every mutation (++, --, +=, -=) auto-emits a wrap guard after the op
 *   (D-14 / D-15 / D-16). Power-of-two N → bitmask `& (N-1)`; non-power-of-two N → compare-reset
 *   `if v >= N { v = 0 }`. Default null → no wrap guard (no behavior change for existing vars).
 * @param transient When true, this variable is excluded from save/load SRAM layout. Use for
 *   temporary state that should not persist between game sessions (e.g. animation counters).
 */
fun u8Var(initial: Int = 0, wrapAt: Int? = null, transient: Boolean = false) =
    U8VarDelegate(initial, wrapAt, transient)

/**
 * Creates a u16 (UINT16) variable delegate with the given initial value.
 *
 * @param initial Initial value (0–65535).
 * @param transient When true, this variable is excluded from save/load SRAM layout.
 */
fun u16Var(initial: Int = 0, transient: Boolean = false) = U16VarDelegate(initial, transient)

/**
 * Creates an i8 (INT8) variable delegate with the given initial value.
 *
 * @param initial Initial value (-128–127).
 * @param transient When true, this variable is excluded from save/load SRAM layout.
 */
fun i8Var(initial: Int = 0, transient: Boolean = false) = I8VarDelegate(initial, transient)

/**
 * Creates an i16 (INT16) variable delegate with the given initial value.
 *
 * @param initial Initial value (-32768–32767).
 * @param transient When true, this variable is excluded from save/load SRAM layout.
 */
fun i16Var(initial: Int = 0, transient: Boolean = false) = I16VarDelegate(initial, transient)

/**
 * Creates a 12.4 fixed-point INT16 position-variable delegate.
 *
 * Use for pixel-coordinate position variables (posX, posY, playerX, playerY). For velocity / speed
 * variables that store raw sub-pixel counts, use [i16Var].
 *
 * @param initialPixels Initial position in whole pixels (stored as pixels × 2^[fractionalBits]).
 * @param fractionalBits Number of fractional bits; default 4 → 12.4 fixed-point (1 pixel = 16).
 *   D-10: fractionalBits is configurable with default 4 (the 12.4 path all examples migrate to).
 * @param transient When true, excluded from save/load SRAM layout.
 */
fun i16FixedVar(initialPixels: Int, fractionalBits: Int = 4, transient: Boolean = false) =
    I16FixedVarDelegate(initialPixels, fractionalBits, transient)

/**
 * Documentation-scope block for grouping fixed-point variable declarations.
 *
 * This is NOT a type-enforcement boundary — it does not change the IR or generated C. It exists
 * purely as a readable grouping construct so related fixed-point variables are visually clustered
 * at the declaration site.
 *
 * D-09 / Pitfall 6: [subpixel] is a no-op scope; it does not change lowering behavior.
 */
fun GameBuilder.subpixel(fractionalBits: Int = 4, block: GameBuilder.() -> Unit) = block()

// =============================================================================
// ARRAY VARIABLE WRAPPER
// =============================================================================

/**
 * Typed wrapper for a declared global array variable.
 *
 * Provides bracket read/write operators (`arr[i]`, `arr[i] = value`), a compile-time [size]
 * property, `exists()` bounds-check helper, and collection-like helpers ([fill], [forEach],
 * [indexOf], [count]).
 *
 * Returned by [ArrayDelegate] instead of a raw String, enabling idiomatic array access in DSL code.
 *
 * Usage:
 * ```kotlin
 * game("Breakout") {
 *     val bricks by u8Array(30)
 *     scene("gameplay") {
 *         frame {
 *             forOp("i", 0, bricks.size) {
 *                 whenever(bricks[varRef("i")] isEqualTo 1) {
 *                     bricks[varRef("i")] = 0
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
data class ArrayVar(val name: String, val elementType: VarType, val arraySize: Int) {
    /** Returns this array as a [VarRef] expression (array as a whole). */
    fun toExpr(): Expr = VarRef(name)

    /**
     * Compile-time array size. Enables `forOp("i", 0, bricks.size)` without magic numbers.
     *
     * Uses `size` (not `length`) for consistency with Kotlin collections.
     */
    val size: Int
        get() = arraySize

    /**
     * Returns a bounds-check expression: `index >= 0 && index < size`.
     *
     * For use in conditions to guard array access. More honest about Game Boy hardware semantics
     * than a nullable get() — out-of-bounds is undefined behavior in C, not null.
     */
    fun exists(index: AssignableVar): Expr =
        BinaryExpr(
            BinaryExpr(index.toExpr(), BinaryOp.GTE, Literal(0)),
            BinaryOp.LOGICAL_AND,
            BinaryExpr(index.toExpr(), BinaryOp.LT, Literal(arraySize)),
        )

    fun exists(index: Expr): Expr =
        BinaryExpr(
            BinaryExpr(index, BinaryOp.GTE, Literal(0)),
            BinaryOp.LOGICAL_AND,
            BinaryExpr(index, BinaryOp.LT, Literal(arraySize)),
        )

    fun exists(index: Int): Expr = exists(Literal(index))

    // --- Bracket read operators ---

    operator fun get(index: AssignableVar): Expr = ArrayAccessExpr(name, index.toExpr())

    operator fun get(index: Expr): Expr = ArrayAccessExpr(name, index)

    operator fun get(index: Int): Expr {
        require(index in 0 until arraySize) {
            "Array bounds error: index $index out of bounds for array '$name' (size=$arraySize)"
        }
        return ArrayAccessExpr(name, Literal(index))
    }

    // --- Bracket write operators ---

    operator fun set(index: AssignableVar, value: Expr) {
        ScriptBuilderContext.current?.arrayAssign(name, index.toExpr(), value)
            ?: error(ARRAY_SET_OUTSIDE_SCRIPT)
    }

    operator fun set(index: AssignableVar, value: Int) = set(index, Literal(value))

    operator fun set(index: Expr, value: Expr) {
        ScriptBuilderContext.current?.arrayAssign(name, index, value)
            ?: error(ARRAY_SET_OUTSIDE_SCRIPT)
    }

    operator fun set(index: Expr, value: Int) = set(index, Literal(value))

    operator fun set(index: Int, value: Expr) {
        require(index in 0 until arraySize) {
            "Array bounds error: index $index out of bounds for array '$name' (size=$arraySize)"
        }
        ScriptBuilderContext.current?.arrayAssign(name, Literal(index), value)
            ?: error(ARRAY_SET_OUTSIDE_SCRIPT)
    }

    operator fun set(index: Int, value: Int) = set(index, Literal(value))

    // --- Collection-like helpers ---

    /**
     * Fills all elements of this array with [value].
     *
     * Emits a [ForOp] that iterates from 0 to [size]-1, assigning `array[i] = value` at each step.
     * Uses temp counter variable `_arr_<name>_i`.
     *
     * Generated C: `for (INT8 _arr_<name>_i = 0; _arr_<name>_i <= N-1; _arr_<name>_i++) {
     * _name[_arr_<name>_i] = value; }`
     *
     * Must be called inside a script builder block (enter/frame/exit/whenever/forOp/etc.).
     */
    fun fill(value: Int) {
        val ctx =
            ScriptBuilderContext.current
                ?: error("ArrayVar.fill() called outside a ScriptBuilder block")
        val idxVar = "_arr_${name}_i"
        ctx.emit(
            ForOp(
                idxVar,
                Literal(0),
                Literal(arraySize - 1),
                listOf(ArrayAssign(name, VarRef(idxVar), Literal(value))),
            )
        )
    }

    /**
     * Fills all elements of this array with the expression [value].
     *
     * Emits a [ForOp] iterating from 0 to [size]-1, assigning `array[i] = value` at each step.
     */
    fun fill(value: Expr) {
        val ctx =
            ScriptBuilderContext.current
                ?: error("ArrayVar.fill() called outside a ScriptBuilder block")
        val idxVar = "_arr_${name}_i"
        ctx.emit(
            ForOp(
                idxVar,
                Literal(0),
                Literal(arraySize - 1),
                listOf(ArrayAssign(name, VarRef(idxVar), value)),
            )
        )
    }

    /**
     * Iterates over all elements of this array, calling [block] with each element expression.
     *
     * The element expression passed to [block] is an [ArrayAccessExpr] at the current loop index.
     * Ops emitted inside [block] become the loop body.
     *
     * Generated C: `for (INT8 _arr_<name>_i = 0; _arr_<name>_i <= N-1; _arr_<name>_i++) { <block
     * body> }`
     */
    fun forEach(block: ScriptBuilder.(element: Expr) -> Unit) {
        val ctx =
            ScriptBuilderContext.current
                ?: error("ArrayVar.forEach() called outside a ScriptBuilder block")
        val idxVar = "_arr_${name}_i"
        val bodyBuilder = ScriptBuilder()
        val element = ArrayAccessExpr(name, VarRef(idxVar))
        ScriptBuilderContext.with(bodyBuilder) { bodyBuilder.block(element) }
        ctx.emit(ForOp(idxVar, Literal(0), Literal(arraySize - 1), bodyBuilder.build()))
    }

    /**
     * Returns the first index where `array[i] == value`, or [size] (not-found sentinel) if absent.
     *
     * Emits a linear search loop into the current script builder. Result is stored in temp variable
     * `_arr_<name>_idx`. Returns a [VarRef] to that variable for use in conditions.
     *
     * Generated C:
     * ```c
     * _arr_<name>_idx = <size>;  // sentinel: not found
     * for (INT8 _arr_<name>_i = 0; _arr_<name>_i <= N-1; _arr_<name>_i++) {
     *     if (_name[_arr_<name>_i] == value && _arr_<name>_idx == <size>) {
     *         _arr_<name>_idx = _arr_<name>_i;
     *     }
     * }
     * ```
     */
    fun indexOf(value: Expr): Expr {
        val ctx =
            ScriptBuilderContext.current
                ?: error("ArrayVar.indexOf() called outside a ScriptBuilder block")
        val idxVar = "_arr_${name}_idx"
        val loopVar = "_arr_${name}_i"
        // Initialize result to size (not-found sentinel)
        ctx.emit(Assign(idxVar, Literal(arraySize)))
        // Search loop body: if array[i] == value && result still sentinel, set result = i
        val matchCond = BinaryExpr(ArrayAccessExpr(name, VarRef(loopVar)), BinaryOp.EQ, value)
        val stillSentinel = BinaryExpr(VarRef(idxVar), BinaryOp.EQ, Literal(arraySize))
        val condition = BinaryExpr(matchCond, BinaryOp.LOGICAL_AND, stillSentinel)
        val loopBody = listOf(IfOp(condition, listOf(Assign(idxVar, VarRef(loopVar)))))
        ctx.emit(ForOp(loopVar, Literal(0), Literal(arraySize - 1), loopBody))
        return VarRef(idxVar)
    }

    /**
     * Returns the number of elements equal to [value] in this array.
     *
     * Emits a count loop into the current script builder. Result is stored in temp variable
     * `_arr_<name>_cnt`. Returns a [VarRef] to that variable for use in conditions.
     *
     * Generated C:
     * ```c
     * _arr_<name>_cnt = 0;
     * for (INT8 _arr_<name>_i = 0; _arr_<name>_i <= N-1; _arr_<name>_i++) {
     *     if (_name[_arr_<name>_i] == value) { _arr_<name>_cnt += 1; }
     * }
     * ```
     */
    fun count(value: Expr): Expr {
        val ctx =
            ScriptBuilderContext.current
                ?: error("ArrayVar.count() called outside a ScriptBuilder block")
        val cntVar = "_arr_${name}_cnt"
        val loopVar = "_arr_${name}_i"
        ctx.emit(Assign(cntVar, Literal(0)))
        val condition = BinaryExpr(ArrayAccessExpr(name, VarRef(loopVar)), BinaryOp.EQ, value)
        val loopBody = listOf(IfOp(condition, listOf(Assign(cntVar, Literal(1), AssignOp.ADD))))
        ctx.emit(ForOp(loopVar, Literal(0), Literal(arraySize - 1), loopBody))
        return VarRef(cntVar)
    }

    private companion object {
        private const val ARRAY_SET_OUTSIDE_SCRIPT =
            "Array set called outside a ScriptBuilder block"
    }
}

// =============================================================================
// ARRAY PROPERTY DELEGATE
// =============================================================================

/**
 * Property delegate for declaring a global array variable.
 *
 * Registers an [ArrayDef] with the current [GameBuilder] via [GameBuilderContext]. Returns an
 * [ArrayVar] wrapper providing bracket operators, `size`, and `exists()` for use in script ops.
 *
 * The array name is inferred from the property name by default. Pass [nameOverride] to use a custom
 * name (useful when the Kotlin property name differs from the desired C identifier).
 *
 * Usage:
 * ```kotlin
 * game("Breakout") {
 *     val bricks by u8Array(30)        // infers name "bricks"
 *     val tiles by u8Array(16, "tile") // explicit name "tile"
 * }
 * ```
 */
class ArrayDelegate(
    private val elementType: VarType,
    private val size: Int,
    private val nameOverride: String? = null,
) : ReadWriteProperty<Any?, ArrayVar> {
    private var resolvedName: String? = null

    /**
     * Single-use guard. Each `by u8Array(...)` binding must use its own delegate instance. Reusing
     * one instance across two `by` bindings throws [IllegalStateException] at build time.
     */
    private var delegateUsed: Boolean = false

    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadWriteProperty<Any?, ArrayVar> {
        check(!delegateUsed) {
            "Delegate instance reused: provideDelegate was already called" +
                (resolvedName?.let { " for array '$it'" } ?: "") +
                ". Create a new delegate instance for each property binding."
        }
        delegateUsed = true
        val name = nameOverride ?: property.name
        resolvedName = name
        val context =
            GameBuilderContext.current
                ?: error(
                    "Array '$name' declared outside a game {} block. " +
                        "Array delegates must be used inside game { } to be registered."
                )
        context.registerArray(ArrayDef(name, elementType, size))
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): ArrayVar {
        val name = resolvedName ?: nameOverride ?: property.name
        return ArrayVar(name, elementType, size)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: ArrayVar) {
        // No-op: array identity is set at declaration time
    }
}

/** Declares a global UINT8 array with [size] elements. Infers name from property name. */
fun u8Array(size: Int, name: String? = null) = ArrayDelegate(VarType.U8, size, name)
