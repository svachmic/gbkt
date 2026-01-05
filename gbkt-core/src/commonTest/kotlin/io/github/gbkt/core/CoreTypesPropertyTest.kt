/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.ir.coerceAtLeast
import io.github.gbkt.core.ir.coerceAtMost
import io.github.gbkt.core.ir.coerceIn
import io.github.gbkt.core.ir.i16
import io.github.gbkt.core.ir.i8
import io.github.gbkt.core.ir.u16
import io.github.gbkt.core.ir.u8
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

// =============================================================================
// u8 PROPERTY TESTS
// =============================================================================

class U8PropertyTest {

    // -------------------------------------------------------------------------
    // Construction & Validation
    // -------------------------------------------------------------------------

    @Test
    fun `u8 construction with valid values succeeds`() = runTest {
        checkAll(Arb.u8Full()) { value -> assertTrue(value.raw in 0..255) }
    }

    @Test
    fun `u8 construction with invalid values fails`() {
        assertFailsWith<IllegalArgumentException> { u8(-1) }
        assertFailsWith<IllegalArgumentException> { u8(256) }
    }

    // -------------------------------------------------------------------------
    // Arithmetic - Mathematical Invariants (Full Range)
    // -------------------------------------------------------------------------

    @Test
    fun `u8 addition is commutative`() = runTest {
        checkAll(Arb.u8Full(), Arb.u8Full()) { a, b -> assertEquals(a + b, b + a) }
    }

    @Test
    fun `u8 multiplication is commutative`() = runTest {
        checkAll(Arb.u8Full(), Arb.u8Full()) { a, b -> assertEquals(a * b, b * a) }
    }

    @Test
    fun `u8 addition with zero is identity`() = runTest {
        checkAll(Arb.u8Full()) { a ->
            assertEquals(a, a + u8(0))
            assertEquals(a, a + 0)
        }
    }

    @Test
    fun `u8 multiplication with one is identity`() = runTest {
        checkAll(Arb.u8Full()) { a ->
            assertEquals(a, a * u8(1))
            assertEquals(a, a * 1)
        }
    }

    @Test
    fun `u8 multiplication with zero is zero`() = runTest {
        checkAll(Arb.u8Full()) { a ->
            assertEquals(u8(0), a * u8(0))
            assertEquals(u8(0), a * 0)
        }
    }

    // -------------------------------------------------------------------------
    // Arithmetic - Overflow Wrapping (Edge Cases)
    // -------------------------------------------------------------------------

    @Test
    fun `u8 overflow wraps correctly`() = runTest {
        checkAll(Arb.u8Overflow(), Arb.u8Edge()) { a, b ->
            val result = a + b
            assertTrue(result.raw in 0..255, "Result ${result.raw} should be 0-255")
        }
    }

    @Test
    fun `u8 255 plus 1 wraps to 0`() {
        assertEquals(u8(0), u8(255) + u8(1))
    }

    @Test
    fun `u8 0 minus 1 wraps to 255`() {
        assertEquals(u8(255), u8(0) - u8(1))
    }

    @Test
    fun `u8 increment at 255 wraps to 0`() {
        var value = u8(255)
        value++
        assertEquals(u8(0), value)
    }

    @Test
    fun `u8 decrement at 0 wraps to 255`() {
        var value = u8(0)
        value--
        assertEquals(u8(255), value)
    }

    // -------------------------------------------------------------------------
    // Arithmetic - Domain-Specific (Screen Coordinates)
    // -------------------------------------------------------------------------

    @Test
    fun `screen position plus walk speed stays in u8 range`() = runTest {
        checkAll(Arb.screenX(), Arb.walkSpeed()) { pos, speed ->
            val newPos = pos + u8(speed)
            assertTrue(newPos.raw in 0..255)
        }
    }

    // -------------------------------------------------------------------------
    // Bitwise Operations
    // -------------------------------------------------------------------------

    @Test
    fun `u8 AND with self is identity`() = runTest {
        checkAll(Arb.u8Full()) { a -> assertEquals(a, a and a) }
    }

    @Test
    fun `u8 OR with self is identity`() = runTest {
        checkAll(Arb.u8Full()) { a -> assertEquals(a, a or a) }
    }

    @Test
    fun `u8 XOR with self is zero`() = runTest {
        checkAll(Arb.u8Full()) { a -> assertEquals(u8(0), a xor a) }
    }

    @Test
    fun `u8 shift left then right recovers original for small shifts`() = runTest {
        checkAll(Arb.u8Full()) { a ->
            // Only for values that don't lose bits when shifted left by 2
            if (a.raw <= 63) {
                assertEquals(a, (a shl 2) shr 2)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Comparisons
    // -------------------------------------------------------------------------

    @Test
    fun `u8 comparison is consistent with raw value`() = runTest {
        checkAll(Arb.u8Full(), Arb.u8Full()) { a, b ->
            assertEquals(a.raw.compareTo(b.raw), a.compareTo(b))
        }
    }

    @Test
    fun `u8 comparison is reflexive`() = runTest {
        checkAll(Arb.u8Full()) { a -> assertEquals(0, a.compareTo(a)) }
    }

    // -------------------------------------------------------------------------
    // Conversions
    // -------------------------------------------------------------------------

    @Test
    fun `u8 toU16 toU8 round-trips correctly`() = runTest {
        checkAll(Arb.u8Full()) { a -> assertEquals(a, a.toU16().toU8()) }
    }

    @Test
    fun `u8 toInt returns raw value`() = runTest {
        checkAll(Arb.u8Full()) { a -> assertEquals(a.raw, a.toInt()) }
    }

    // -------------------------------------------------------------------------
    // Coercion
    // -------------------------------------------------------------------------

    @Test
    fun `u8 coerceIn clamps to range`() = runTest {
        checkAll(Arb.u8Full()) { a ->
            val clamped = a.coerceIn(50..100)
            assertTrue(clamped.raw in 50..100)
        }
    }

    @Test
    fun `u8 coerceAtLeast enforces minimum`() = runTest {
        checkAll(Arb.u8Full()) { a ->
            val result = a.coerceAtLeast(u8(50))
            assertTrue(result.raw >= 50)
        }
    }

    @Test
    fun `u8 coerceAtMost enforces maximum`() = runTest {
        checkAll(Arb.u8Full()) { a ->
            val result = a.coerceAtMost(u8(200))
            assertTrue(result.raw <= 200)
        }
    }

    // -------------------------------------------------------------------------
    // Companion Object
    // -------------------------------------------------------------------------

    @Test
    fun `u8 ZERO and MAX are correct`() {
        assertEquals(0, u8.ZERO.raw)
        assertEquals(255, u8.MAX.raw)
    }

    @Test
    fun `u8 of masks to valid range`() = runTest {
        checkAll(Arb.int(-1000, 1000)) { value ->
            val result = u8.of(value)
            assertTrue(result.raw in 0..255)
            assertEquals(value and 0xFF, result.raw)
        }
    }
}

// =============================================================================
// u16 PROPERTY TESTS
// =============================================================================

class U16PropertyTest {

    @Test
    fun `u16 construction with valid values succeeds`() = runTest {
        checkAll(Arb.u16Full()) { value -> assertTrue(value.raw in 0..65535) }
    }

    @Test
    fun `u16 construction with invalid values fails`() {
        assertFailsWith<IllegalArgumentException> { u16(-1) }
        assertFailsWith<IllegalArgumentException> { u16(65536) }
    }

    @Test
    fun `u16 addition is commutative`() = runTest {
        checkAll(Arb.u16Full(), Arb.u16Full()) { a, b -> assertEquals(a + b, b + a) }
    }

    @Test
    fun `u16 overflow wraps correctly`() = runTest {
        checkAll(Arb.u16Edge(), Arb.u16Edge()) { a, b ->
            val result = a + b
            assertTrue(result.raw in 0..65535)
        }
    }

    @Test
    fun `u16 65535 plus 1 wraps to 0`() {
        assertEquals(u16(0), u16(65535) + u16(1))
    }

    @Test
    fun `u16 high and low bytes are correct`() = runTest {
        checkAll(Arb.u16Full()) { value ->
            val high = value.high()
            val low = value.low()
            assertEquals(value.raw shr 8, high.raw)
            assertEquals(value.raw and 0xFF, low.raw)
        }
    }

    @Test
    fun `u16 from high and low reconstructs original`() = runTest {
        checkAll(Arb.u16Full()) { value ->
            val reconstructed = u16.from(value.high(), value.low())
            assertEquals(value, reconstructed)
        }
    }

    @Test
    fun `u16 comparison is consistent with raw value`() = runTest {
        checkAll(Arb.u16Full(), Arb.u16Full()) { a, b ->
            assertEquals(a.raw.compareTo(b.raw), a.compareTo(b))
        }
    }
}

// =============================================================================
// i8 PROPERTY TESTS
// =============================================================================

class I8PropertyTest {

    @Test
    fun `i8 construction with valid values succeeds`() = runTest {
        checkAll(Arb.i8Full()) { value -> assertTrue(value.raw in -128..127) }
    }

    @Test
    fun `i8 construction with invalid values fails`() {
        assertFailsWith<IllegalArgumentException> { i8(-129) }
        assertFailsWith<IllegalArgumentException> { i8(128) }
    }

    @Test
    fun `i8 addition is commutative`() = runTest {
        checkAll(Arb.i8Full(), Arb.i8Full()) { a, b -> assertEquals(a + b, b + a) }
    }

    @Test
    fun `i8 negation of negation is identity`() = runTest {
        checkAll(Arb.i8Full()) { a ->
            // Skip -128 which can't be negated within i8 range
            if (a.raw != -128) {
                assertEquals(a, -(-a))
            }
        }
    }

    @Test
    fun `i8 overflow wraps correctly`() = runTest {
        checkAll(Arb.i8Edge(), Arb.i8Edge()) { a, b ->
            val result = a + b
            assertTrue(result.raw in -128..127)
        }
    }

    @Test
    fun `i8 127 plus 1 wraps to -128`() {
        assertEquals(i8(-128), i8(127) + i8(1))
    }

    @Test
    fun `i8 -128 minus 1 wraps to 127`() {
        assertEquals(i8(127), i8(-128) - i8(1))
    }

    @Test
    fun `i8 velocity values are within expected game range`() = runTest {
        checkAll(Arb.velocity()) { vel -> assertTrue(vel.raw in -8..8) }
    }

    @Test
    fun `i8 toU8 masks correctly`() = runTest {
        checkAll(Arb.i8Full()) { a ->
            val unsigned = a.toU8()
            assertEquals(a.raw and 0xFF, unsigned.raw)
        }
    }

    @Test
    fun `i8 comparison is consistent with raw value`() = runTest {
        checkAll(Arb.i8Full(), Arb.i8Full()) { a, b ->
            assertEquals(a.raw.compareTo(b.raw), a.compareTo(b))
        }
    }
}

// =============================================================================
// i16 PROPERTY TESTS
// =============================================================================

class I16PropertyTest {

    @Test
    fun `i16 construction with valid values succeeds`() = runTest {
        checkAll(Arb.i16Full()) { value -> assertTrue(value.raw in -32768..32767) }
    }

    @Test
    fun `i16 construction with invalid values fails`() {
        assertFailsWith<IllegalArgumentException> { i16(-32769) }
        assertFailsWith<IllegalArgumentException> { i16(32768) }
    }

    @Test
    fun `i16 addition is commutative`() = runTest {
        checkAll(Arb.i16Full(), Arb.i16Full()) { a, b -> assertEquals(a + b, b + a) }
    }

    @Test
    fun `i16 negation of negation is identity`() = runTest {
        checkAll(Arb.i16Full()) { a ->
            // Skip -32768 which can't be negated within i16 range
            if (a.raw != -32768) {
                assertEquals(a, -(-a))
            }
        }
    }

    @Test
    fun `i16 overflow wraps correctly`() = runTest {
        checkAll(Arb.i16Edge(), Arb.i16Edge()) { a, b ->
            val result = a + b
            assertTrue(result.raw in -32768..32767)
        }
    }

    @Test
    fun `i16 toU16 masks correctly`() = runTest {
        checkAll(Arb.i16Full()) { a ->
            val unsigned = a.toU16()
            assertEquals(a.raw and 0xFFFF, unsigned.raw)
        }
    }

    @Test
    fun `i16 comparison is consistent with raw value`() = runTest {
        checkAll(Arb.i16Full(), Arb.i16Full()) { a, b ->
            assertEquals(a.raw.compareTo(b.raw), a.compareTo(b))
        }
    }
}
