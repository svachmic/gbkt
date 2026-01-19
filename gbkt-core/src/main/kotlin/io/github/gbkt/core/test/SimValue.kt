/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.test

import io.github.gbkt.core.ir.GBVar

/**
 * A simulated value for IR execution. Wraps a Long to support all Game Boy integer operations (u8,
 * u16, i8, i16).
 *
 * Type tracking enables proper overflow behavior matching Game Boy semantics:
 * - U8: wraps at 0-255
 * - U16: wraps at 0-65535
 * - I8: wraps at -128 to 127
 * - I16: wraps at -32768 to 32767
 */
data class SimValue(val raw: Long, val type: GBVar.VarType = GBVar.VarType.U8) {

    // Apply overflow wrapping based on type
    private fun applyOverflow(value: Long): Long =
        when (type) {
            GBVar.VarType.U8 -> value and 0xFF
            GBVar.VarType.U16 -> value and 0xFFFF
            GBVar.VarType.I8 -> ((value and 0xFF).toByte()).toLong()
            GBVar.VarType.I16 -> ((value and 0xFFFF).toShort()).toLong()
        }

    // Arithmetic operators with overflow wrapping
    operator fun plus(other: SimValue) = SimValue(applyOverflow(raw + other.raw), type)

    operator fun minus(other: SimValue) = SimValue(applyOverflow(raw - other.raw), type)

    operator fun times(other: SimValue) = SimValue(applyOverflow(raw * other.raw), type)

    operator fun div(other: SimValue): SimValue {
        if (other.raw == 0L) throw ArithmeticException("Division by zero in simulation")
        return SimValue(applyOverflow(raw / other.raw), type)
    }

    operator fun rem(other: SimValue): SimValue {
        if (other.raw == 0L) throw ArithmeticException("Modulo by zero in simulation")
        return SimValue(applyOverflow(raw % other.raw), type)
    }

    operator fun unaryMinus() = SimValue(applyOverflow(-raw), type)

    // Bitwise operators (preserve type for consistency)
    infix fun and(other: SimValue) = SimValue(applyOverflow(raw and other.raw), type)

    infix fun or(other: SimValue) = SimValue(applyOverflow(raw or other.raw), type)

    infix fun xor(other: SimValue) = SimValue(applyOverflow(raw xor other.raw), type)

    infix fun shl(bits: SimValue) = SimValue(applyOverflow(raw shl bits.raw.toInt()), type)

    infix fun shr(bits: SimValue) = SimValue(applyOverflow(raw shr bits.raw.toInt()), type)

    fun inv() = SimValue(applyOverflow(raw.inv()), type)

    // Comparison operators - return SimValue (0 = false, 1 = true) for IR consistency
    infix fun eq(other: SimValue) = SimValue(if (raw == other.raw) 1L else 0L)

    infix fun neq(other: SimValue) = SimValue(if (raw != other.raw) 1L else 0L)

    infix fun lt(other: SimValue) = SimValue(if (raw < other.raw) 1L else 0L)

    infix fun lte(other: SimValue) = SimValue(if (raw <= other.raw) 1L else 0L)

    infix fun gt(other: SimValue) = SimValue(if (raw > other.raw) 1L else 0L)

    infix fun gte(other: SimValue) = SimValue(if (raw >= other.raw) 1L else 0L)

    // Logical operators
    infix fun land(other: SimValue) = SimValue(if (isTrue && other.isTrue) 1L else 0L)

    infix fun lor(other: SimValue) = SimValue(if (isTrue || other.isTrue) 1L else 0L)

    fun lnot() = SimValue(if (isTrue) 0L else 1L)

    // Type conversions
    fun toInt(): Int = raw.toInt()

    fun toByte(): Byte = raw.toByte()

    fun toUByte(): UByte = raw.toUByte()

    fun toShort(): Short = raw.toShort()

    fun toUShort(): UShort = raw.toUShort()

    // Boolean interpretation
    val isTrue: Boolean
        get() = raw != 0L

    val isFalse: Boolean
        get() = raw == 0L

    // Game Boy type clamping
    fun asU8(): SimValue = SimValue(raw and 0xFF)

    fun asU16(): SimValue = SimValue(raw and 0xFFFF)

    fun asI8(): SimValue {
        val byte = (raw and 0xFF).toByte()
        return SimValue(byte.toLong())
    }

    fun asI16(): SimValue {
        val short = (raw and 0xFFFF).toShort()
        return SimValue(short.toLong())
    }

    override fun toString(): String = raw.toString()

    companion object {
        val ZERO = SimValue(0L)
        val ONE = SimValue(1L)
        val TRUE = ONE
        val FALSE = ZERO

        fun of(value: Int) = SimValue(value.toLong())

        fun of(value: Long) = SimValue(value)

        fun of(value: Boolean) = if (value) TRUE else FALSE

        /** Create a typed SimValue. */
        fun of(value: Int, type: GBVar.VarType): SimValue =
            when (type) {
                GBVar.VarType.U8 -> u8(value)
                GBVar.VarType.U16 -> u16(value)
                GBVar.VarType.I8 -> i8(value)
                GBVar.VarType.I16 -> i16(value)
            }

        /** Create unsigned 8-bit value (0-255). */
        fun u8(value: Int) = SimValue(value.toLong() and 0xFF, GBVar.VarType.U8)

        /** Create unsigned 16-bit value (0-65535). */
        fun u16(value: Int) = SimValue(value.toLong() and 0xFFFF, GBVar.VarType.U16)

        /** Create signed 8-bit value (-128 to 127). */
        fun i8(value: Int) = SimValue(value.toByte().toLong(), GBVar.VarType.I8)

        /** Create signed 16-bit value (-32768 to 32767). */
        fun i16(value: Int) = SimValue(value.toShort().toLong(), GBVar.VarType.I16)

        /** Create SimValue from any supported type. */
        fun from(value: Any): SimValue =
            when (value) {
                is Int -> of(value)
                is Long -> of(value)
                is Byte -> SimValue(value.toLong(), GBVar.VarType.I8)
                is Short -> SimValue(value.toLong(), GBVar.VarType.I16)
                is Boolean -> of(value)
                is UByte -> SimValue(value.toLong(), GBVar.VarType.U8)
                is UShort -> SimValue(value.toLong(), GBVar.VarType.U16)
                is SimValue -> value
                else -> error("Unsupported value type for SimValue: ${value::class.simpleName}")
            }
    }
}

// Extension functions for Int literals
operator fun Int.plus(other: SimValue) = SimValue.of(this) + other

operator fun Int.minus(other: SimValue) = SimValue.of(this) - other

operator fun Int.times(other: SimValue) = SimValue.of(this) * other
