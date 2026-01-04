/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("NOTHING_TO_INLINE")

package io.github.gbkt.core.ir

import io.github.gbkt.core.dsl.GameScope
import io.github.gbkt.core.dsl.RecordingContext
import kotlin.reflect.KProperty

// =============================================================================
// GB VARIABLE - The star of the show
// =============================================================================

/**
 * A Game Boy variable that:
 * - Acts like a normal Kotlin property outside recording
 * - Generates IR when used inside recording blocks
 * - Supports all Kotlin operators
 */
class GBVar<T : Any>(val name: String, private var _value: T, val type: VarType) {
    enum class VarType(val cType: String, val size: Int) {
        U8("UINT8", 1),
        U16("UINT16", 2),
        I8("INT8", 1),
        I16("INT16", 2)
    }

    var value: T
        get() = _value
        set(v) {
            if (RecordingContext.isRecording) {
                RecordingContext.require().emit(IRAssign(name, toLiteral(v)))
            }
            _value = v
        }

    private fun toLiteral(v: Any): IRExpression =
        when (v) {
            is u8 -> IRLiteral(v.raw)
            is u16 -> IRLiteral(v.raw)
            is i8 -> IRLiteral(v.raw)
            is i16 -> IRLiteral(v.raw)
            is Int -> IRLiteral(v)
            is IRExpression -> v
            else ->
                error(
                    "Cannot convert ${v::class.simpleName} to IRLiteral. " +
                        "Supported types: u8, u16, i8, i16, Int, IRExpression"
                )
        }

    /** Convert to IR expression */
    fun asExpr(): IRExpression = IRVar(name)

    override fun toString() = "GBVar($name=$_value)"
}

// =============================================================================
// GAME SCOPE CONTEXT - For tracking current scope during DSL execution
// =============================================================================

/** Platform-specific holder for current GameScope. */
expect class GameScopeHolder() {
    fun get(): GameScope?

    fun set(scope: GameScope?)
}

object GameScopeContext {
    private val holder = GameScopeHolder()

    val current: GameScope?
        get() = holder.get()

    fun <T> withScope(scope: GameScope, block: () -> T): T {
        val previous = holder.get()
        holder.set(scope)
        return try {
            block()
        } finally {
            holder.set(previous)
        }
    }
}

// =============================================================================
// PROPERTY DELEGATES - Natural Kotlin syntax
// =============================================================================

/** Delegate that creates a u8 variable. Usage: var playerX by u8Var(80) */
/**
 * Assignable expression - returned by variable delegates. Allows both `playerX = 5` and `playerX =
 * someExpr` syntax.
 */
open class AssignableExpr(
    val varName: String,
    val varType: GBVar.VarType,
    override val ir: IRExpression = IRVar(varName)
) : Expr(ir) {

    /** Assign an integer literal */
    open infix fun set(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRAssign(varName, IRLiteral(value)))
        }
    }

    /** Assign an expression */
    open infix fun set(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRAssign(varName, value.ir))
        }
    }

    /** Compound add */
    open infix fun addAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRAssign(varName, IRBinary(ir, BinaryOp.ADD, IRLiteral(value)), AssignOp.SET))
        }
    }

    open infix fun addAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRAssign(varName, IRBinary(ir, BinaryOp.ADD, value.ir), AssignOp.SET))
        }
    }

    /** Compound subtract */
    open infix fun subAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRAssign(varName, IRBinary(ir, BinaryOp.SUB, IRLiteral(value)), AssignOp.SET))
        }
    }

    open infix fun subAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRAssign(varName, IRBinary(ir, BinaryOp.SUB, value.ir), AssignOp.SET))
        }
    }

    // =========================================================================
    // COMPOUND ASSIGNMENT OPERATORS - The Kotlin way!
    // Usage: playerX += 2, score -= 10, etc.
    // =========================================================================

    /** Add and assign: playerX += 2 */
    open operator fun plusAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRAssign(varName, IRBinary(ir, BinaryOp.ADD, IRLiteral(value)), AssignOp.SET))
        }
    }

    /** Add and assign: playerX += speed */
    open operator fun plusAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRAssign(varName, IRBinary(ir, BinaryOp.ADD, value.ir), AssignOp.SET))
        }
    }

    /** Subtract and assign: playerY -= 2 */
    open operator fun minusAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRAssign(varName, IRBinary(ir, BinaryOp.SUB, IRLiteral(value)), AssignOp.SET))
        }
    }

    /** Subtract and assign: health -= damage */
    open operator fun minusAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRAssign(varName, IRBinary(ir, BinaryOp.SUB, value.ir), AssignOp.SET))
        }
    }

    /** Multiply and assign: score *= 2 */
    open operator fun timesAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRAssign(varName, IRBinary(ir, BinaryOp.MUL, IRLiteral(value)), AssignOp.SET))
        }
    }

    /** Multiply and assign: damage *= multiplier */
    open operator fun timesAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRAssign(varName, IRBinary(ir, BinaryOp.MUL, value.ir), AssignOp.SET))
        }
    }

    /** Divide and assign: health /= 2 */
    open operator fun divAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRAssign(varName, IRBinary(ir, BinaryOp.DIV, IRLiteral(value)), AssignOp.SET))
        }
    }

    /** Divide and assign: score /= divisor */
    open operator fun divAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRAssign(varName, IRBinary(ir, BinaryOp.DIV, value.ir), AssignOp.SET))
        }
    }

    /** Modulo and assign: frame %= 60 */
    open operator fun remAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRAssign(varName, IRBinary(ir, BinaryOp.MOD, IRLiteral(value)), AssignOp.SET))
        }
    }

    /** Modulo and assign */
    open operator fun remAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRAssign(varName, IRBinary(ir, BinaryOp.MOD, value.ir), AssignOp.SET))
        }
    }
}

class U8Delegate(private val initial: Int) {
    private var variable: GBVar<u8>? = null

    private fun getOrCreateVariable(property: KProperty<*>): GBVar<u8> =
        variable
            ?: GBVar(property.name, u8(initial), GBVar.VarType.U8).also { newVar ->
                variable = newVar
                GameScopeContext.current?.registerVariable(newVar)
            }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): AssignableExpr {
        val v = getOrCreateVariable(property)
        return AssignableExpr(v.name, v.type)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: AssignableExpr) {
        val v = getOrCreateVariable(property)
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRAssign(v.name, value.ir))
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Expr) {
        val v = getOrCreateVariable(property)
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRAssign(v.name, value.ir))
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        val v = getOrCreateVariable(property)
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRAssign(v.name, IRLiteral(value)))
        }
    }
}

class U16Delegate(private val initial: Int) {
    private var variable: GBVar<u16>? = null

    private fun getOrCreateVariable(property: KProperty<*>): GBVar<u16> =
        variable
            ?: GBVar(property.name, u16(initial), GBVar.VarType.U16).also { newVar ->
                variable = newVar
                GameScopeContext.current?.registerVariable(newVar)
            }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): AssignableExpr {
        val v = getOrCreateVariable(property)
        return AssignableExpr(v.name, v.type)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: AssignableExpr) {
        val v = getOrCreateVariable(property)
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRAssign(v.name, value.ir))
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Expr) {
        val v = getOrCreateVariable(property)
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRAssign(v.name, value.ir))
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        val v = getOrCreateVariable(property)
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRAssign(v.name, IRLiteral(value)))
        }
    }
}

class I8Delegate(private val initial: Int) {
    private var variable: GBVar<i8>? = null

    private fun getOrCreateVariable(property: KProperty<*>): GBVar<i8> =
        variable
            ?: GBVar(property.name, i8(initial), GBVar.VarType.I8).also { newVar ->
                variable = newVar
                GameScopeContext.current?.registerVariable(newVar)
            }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): AssignableExpr {
        val v = getOrCreateVariable(property)
        return AssignableExpr(v.name, v.type)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: AssignableExpr) {
        val v = getOrCreateVariable(property)
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRAssign(v.name, value.ir))
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Expr) {
        val v = getOrCreateVariable(property)
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRAssign(v.name, value.ir))
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        val v = getOrCreateVariable(property)
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRAssign(v.name, IRLiteral(value)))
        }
    }
}

class I16Delegate(private val initial: Int) {
    private var variable: GBVar<i16>? = null

    private fun getOrCreateVariable(property: KProperty<*>): GBVar<i16> =
        variable
            ?: GBVar(property.name, i16(initial), GBVar.VarType.I16).also { newVar ->
                variable = newVar
                GameScopeContext.current?.registerVariable(newVar)
            }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): AssignableExpr {
        val v = getOrCreateVariable(property)
        return AssignableExpr(v.name, v.type)
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: AssignableExpr) {
        val v = getOrCreateVariable(property)
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRAssign(v.name, value.ir))
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Expr) {
        val v = getOrCreateVariable(property)
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRAssign(v.name, value.ir))
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        val v = getOrCreateVariable(property)
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRAssign(v.name, IRLiteral(value)))
        }
    }
}

// Factory functions for property delegates

/** Create an unsigned 8-bit variable. Usage: var playerX by u8Var(80) */
fun u8Var(initial: Int = 0) = U8Delegate(initial)

/** Create an unsigned 16-bit variable. Usage: var score by u16Var(0) */
fun u16Var(initial: Int = 0) = U16Delegate(initial)

/** Create a signed 8-bit variable. Usage: var velocityX by i8Var(0) */
fun i8Var(initial: Int = 0) = I8Delegate(initial)

/** Create a signed 16-bit variable. Usage: var positionX by i16Var(0) */
fun i16Var(initial: Int = 0) = I16Delegate(initial)

// =============================================================================
// FIXED-SIZE ARRAYS
// =============================================================================

/**
 * A fixed-size array for Game Boy.
 *
 * @param name The variable name
 * @param size Number of elements (max 255 for u8 index)
 * @param elementType Type of each element
 * @param initialValue Initial value for all elements
 */
data class GBArray(
    val name: String,
    val size: Int,
    val elementType: GBVar.VarType,
    val initialValue: Int = 0
)

/**
 * An assignable array element - returned by array[index] access.
 *
 * Enables both reading and writing array elements:
 * ```kotlin
 * inventory[0] set 5       // Write
 * inventory[slot] += 1     // Compound assignment
 * val item = inventory[0]  // Read (as Expr)
 * ```
 */
class AssignableArrayElement(
    private val arrayName: String,
    private val index: IRExpression,
    private val elementType: GBVar.VarType
) : Expr(IRArrayAccess(arrayName, index)) {

    /** Assign an integer value: array[i] set 5 */
    infix fun set(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRArrayAssign(arrayName, index, IRLiteral(value)))
        }
    }

    /** Assign an expression: array[i] set someExpr */
    infix fun set(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRArrayAssign(arrayName, index, value.ir))
        }
    }

    /** Add and assign: array[i] += 1 */
    operator fun plusAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRArrayAssign(arrayName, index, IRBinary(ir, BinaryOp.ADD, IRLiteral(value))))
        }
    }

    operator fun plusAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRArrayAssign(arrayName, index, IRBinary(ir, BinaryOp.ADD, value.ir)))
        }
    }

    /** Subtract and assign: array[i] -= 1 */
    operator fun minusAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRArrayAssign(arrayName, index, IRBinary(ir, BinaryOp.SUB, IRLiteral(value))))
        }
    }

    operator fun minusAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRArrayAssign(arrayName, index, IRBinary(ir, BinaryOp.SUB, value.ir)))
        }
    }
}

/**
 * Array accessor - provides array[index] syntax.
 *
 * Usage:
 * ```kotlin
 * val inventory by u8Array(size = 10)
 *
 * inventory[0] set 5           // Static index
 * inventory[slot] set item     // Dynamic index
 * inventory[0] += 1            // Compound assignment
 *
 * whenever(inventory[0] isAbove 0) {
 *     // Slot has item
 * }
 * ```
 */
class ArrayAccessor(private val array: GBArray) {
    /** Access array element by integer index (bounds-checked at DSL build time) */
    operator fun get(index: Int): AssignableArrayElement {
        require(index in 0 until array.size) {
            "Array index $index out of bounds for array '${array.name}' of size ${array.size}"
        }
        return AssignableArrayElement(array.name, IRLiteral(index), array.elementType)
    }

    /** Access array element by expression index (no bounds check - runtime responsibility) */
    operator fun get(index: Expr): AssignableArrayElement {
        return AssignableArrayElement(array.name, index.ir, array.elementType)
    }

    /** Access array element by AssignableExpr (variable) index */
    operator fun get(index: AssignableExpr): AssignableArrayElement {
        return AssignableArrayElement(array.name, index.ir, array.elementType)
    }

    /** Size of the array */
    val size: Int
        get() = array.size

    /** Name of the array variable */
    val name: String
        get() = array.name
}

// =============================================================================
// ARRAY DELEGATES
// =============================================================================

class U8ArrayDelegate(private val size: Int, private val initial: Int) {
    private var array: GBArray? = null
    private var accessor: ArrayAccessor? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): ArrayAccessor {
        if (array == null) {
            val arr = GBArray(property.name, size, GBVar.VarType.U8, initial)
            array = arr
            accessor = ArrayAccessor(arr)
            GameScopeContext.current?.registerArray(arr)
        }
        return accessor!!
    }
}

class U16ArrayDelegate(private val size: Int, private val initial: Int) {
    private var array: GBArray? = null
    private var accessor: ArrayAccessor? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): ArrayAccessor {
        if (array == null) {
            val arr = GBArray(property.name, size, GBVar.VarType.U16, initial)
            array = arr
            accessor = ArrayAccessor(arr)
            GameScopeContext.current?.registerArray(arr)
        }
        return accessor!!
    }
}

class I8ArrayDelegate(private val size: Int, private val initial: Int) {
    private var array: GBArray? = null
    private var accessor: ArrayAccessor? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): ArrayAccessor {
        if (array == null) {
            val arr = GBArray(property.name, size, GBVar.VarType.I8, initial)
            array = arr
            accessor = ArrayAccessor(arr)
            GameScopeContext.current?.registerArray(arr)
        }
        return accessor!!
    }
}

class I16ArrayDelegate(private val size: Int, private val initial: Int) {
    private var array: GBArray? = null
    private var accessor: ArrayAccessor? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): ArrayAccessor {
        if (array == null) {
            val arr = GBArray(property.name, size, GBVar.VarType.I16, initial)
            array = arr
            accessor = ArrayAccessor(arr)
            GameScopeContext.current?.registerArray(arr)
        }
        return accessor!!
    }
}

// Factory functions for array delegates

/**
 * Create an unsigned 8-bit array.
 *
 * Usage:
 * ```kotlin
 * val inventory by u8Array(size = 10)
 * val slots by u8Array(size = 5, initial = 255)  // Initialize all to 255
 * ```
 */
fun u8Array(size: Int, initial: Int = 0) = U8ArrayDelegate(size, initial)

/**
 * Create an unsigned 16-bit array.
 *
 * Usage:
 * ```kotlin
 * val highScores by u16Array(size = 5)
 * ```
 */
fun u16Array(size: Int, initial: Int = 0) = U16ArrayDelegate(size, initial)

/**
 * Create a signed 8-bit array.
 *
 * Usage:
 * ```kotlin
 * val velocities by i8Array(size = 8)
 * ```
 */
fun i8Array(size: Int, initial: Int = 0) = I8ArrayDelegate(size, initial)

/**
 * Create a signed 16-bit array.
 *
 * Usage:
 * ```kotlin
 * val positions by i16Array(size = 4)
 * ```
 */
fun i16Array(size: Int, initial: Int = 0) = I16ArrayDelegate(size, initial)
