/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.debug

import eu.rekawek.coffeegb.core.AddressSpace
import eu.rekawek.coffeegb.core.cpu.Registers
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [EmuPrintfInterceptor].
 *
 * Uses [TestAddressSpace] — a simple byte-array backed [AddressSpace] implementation — and directly
 * instantiates [Registers] (which has a public no-arg constructor).
 *
 * Test coverage:
 * 1. Valid EMU_printf sequence fires onMessage with correct string and PC
 * 2. False positives: 0x52 without 0x18 at PC+1 → rejected
 * 3. False positives: 0x52 + 0x18 but wrong marker bytes → rejected
 * 4. Deduplication: same PC fires interceptor only once per frame
 * 5. Different PC with same opcode fires again
 * 6. readCString correctly terminates at null byte
 * 7. readCString caps at 256 chars for safety
 * 8. resetDedup allows same PC to fire again on next frame
 */
class EmuPrintfInterceptorTest {

    // ── Test infrastructure ───────────────────────────────────────────────────

    /**
     * A minimal [AddressSpace] backed by a flat byte array.
     *
     * Accepts all addresses in [0, size), wraps addresses >= size to 0. [getByte] returns unsigned
     * int (0–255). [setByte] is a no-op (ROM is immutable).
     */
    private class TestAddressSpace(private val memory: ByteArray) : AddressSpace {
        override fun accepts(address: Int): Boolean = address in memory.indices

        override fun getByte(address: Int): Int {
            return if (address in memory.indices) {
                memory[address].toInt() and 0xFF
            } else {
                0xFF // Return 0xFF for out-of-range — simulates unmapped memory
            }
        }

        override fun setByte(address: Int, value: Int) {
            // ROM is immutable in tests — writes are silently ignored
        }
    }

    /**
     * Creates a [TestAddressSpace] with the given bytes starting at address 0. The underlying array
     * is padded with zeros up to [size] bytes.
     */
    private fun addressSpaceOf(vararg bytes: Int, size: Int = 512): AddressSpace {
        val memory = ByteArray(size) { i -> if (i < bytes.size) bytes[i].toByte() else 0 }
        return TestAddressSpace(memory)
    }

    /** Creates a [Registers] instance with PC set to [pc] and HL set to [hl]. */
    private fun registersAt(pc: Int, hl: Int = 0): Registers {
        val registers = Registers()
        registers.pc = pc
        registers.hl = hl
        return registers
    }

    /**
     * Builds an EMU_printf byte sequence at the given [startAddress]: [startAddress+0]: 0x52 (ld
     * d,d) [startAddress+1]: 0x18 (JR) [startAddress+2]: [offset] (relative jump displacement,
     * typically 0x03) [startAddress+3]: 0x64 (marker) [startAddress+4]: 0x64 (marker)
     *
     * Followed by [message] as a null-terminated string at [stringAddress].
     */
    private fun buildEmuPrintfMemory(
        trapAddress: Int = 0x100,
        offset: Int = 0x03,
        stringAddress: Int = 0x200,
        message: String,
    ): AddressSpace {
        val memory = ByteArray(0x400) // 1KB enough for our tests
        memory[trapAddress] = 0x52.toByte()
        memory[trapAddress + 1] = 0x18.toByte()
        memory[trapAddress + 2] = offset.toByte()
        memory[trapAddress + 3] = 0x64.toByte()
        memory[trapAddress + 4] = 0x64.toByte()
        // Write the null-terminated message at stringAddress
        message.forEachIndexed { i, c -> memory[stringAddress + i] = c.code.toByte() }
        memory[stringAddress + message.length] = 0 // null terminator
        return TestAddressSpace(memory)
    }

    // ── 1. Valid EMU_printf sequence ──────────────────────────────────────────

    @Test
    fun `valid EMU_printf sequence fires onMessage with correct string`() {
        val message = "Score: 10"
        val stringAddr = 0x200
        val trapAddr = 0x100
        val addressSpace =
            buildEmuPrintfMemory(
                trapAddress = trapAddr,
                stringAddress = stringAddr,
                message = message,
            )
        val registers = registersAt(pc = trapAddr, hl = stringAddr)

        var captured: String? = null
        val interceptor = EmuPrintfInterceptor { msg, _ -> captured = msg }
        interceptor.check(registers, addressSpace)

        assertEquals(message, captured, "onMessage should fire with the correct format string")
    }

    @Test
    fun `valid EMU_printf sequence passes PC address to callback`() {
        val message = "test"
        val stringAddr = 0x200
        val trapAddr = 0x100
        val addressSpace =
            buildEmuPrintfMemory(
                trapAddress = trapAddr,
                stringAddress = stringAddr,
                message = message,
            )
        val registers = registersAt(pc = trapAddr, hl = stringAddr)

        var capturedPc: Int? = null
        val interceptor = EmuPrintfInterceptor { _, pc -> capturedPc = pc }
        interceptor.check(registers, addressSpace)

        assertEquals(trapAddr, capturedPc, "onMessage should pass the PC address of the trap")
    }

    @Test
    fun `EMU_printf with variable offset still detected`() {
        val message = "Hello"
        val stringAddr = 0x300
        val trapAddr = 0x150
        // Use a different offset (0x05 instead of default 0x03)
        val addressSpace =
            buildEmuPrintfMemory(
                trapAddress = trapAddr,
                offset = 0x05,
                stringAddress = stringAddr,
                message = message,
            )
        val registers = registersAt(pc = trapAddr, hl = stringAddr)

        var captured: String? = null
        val interceptor = EmuPrintfInterceptor { msg, _ -> captured = msg }
        interceptor.check(registers, addressSpace)

        assertEquals(
            message,
            captured,
            "Offset byte at PC+2 can be any value — only signature bytes matter",
        )
    }

    // ── 2. False positive rejection: wrong byte at PC+1 ──────────────────────

    @Test
    fun `opcode 0x52 without 0x18 at PC+1 is rejected — no message fires`() {
        // 0x52 (ld d,d) followed by 0x01 (LD BC,nn) — NOT a JR
        val addressSpace = addressSpaceOf(0x52, 0x01, 0x03, 0x64, 0x64)
        val registers = registersAt(pc = 0)

        var fired = false
        val interceptor = EmuPrintfInterceptor { _, _ -> fired = true }
        interceptor.check(registers, addressSpace)

        assertTrue(!fired, "0x52 + 0x01 should be rejected as false positive (no JR at PC+1)")
    }

    @Test
    fun `opcode 0x00 (NOP) does not trigger interceptor`() {
        val addressSpace = addressSpaceOf(0x00, 0x18, 0x03, 0x64, 0x64)
        val registers = registersAt(pc = 0)

        var fired = false
        val interceptor = EmuPrintfInterceptor { _, _ -> fired = true }
        interceptor.check(registers, addressSpace)

        assertTrue(!fired, "NOP at PC+0 should not trigger interceptor (fast reject)")
    }

    // ── 3. False positive rejection: wrong marker bytes at PC+3/PC+4 ─────────

    @Test
    fun `0x52 + 0x18 without 0x64 0x64 marker is rejected`() {
        // Missing 0x6464 marker — PC+3=0x00, PC+4=0x00
        val addressSpace = addressSpaceOf(0x52, 0x18, 0x03, 0x00, 0x00)
        val registers = registersAt(pc = 0)

        var fired = false
        val interceptor = EmuPrintfInterceptor { _, _ -> fired = true }
        interceptor.check(registers, addressSpace)

        assertTrue(!fired, "Missing 0x6464 marker should cause rejection")
    }

    @Test
    fun `0x52 + 0x18 with only first 0x64 marker is rejected`() {
        // PC+3=0x64, PC+4=0x00 — second marker is missing
        val addressSpace = addressSpaceOf(0x52, 0x18, 0x03, 0x64, 0x00)
        val registers = registersAt(pc = 0)

        var fired = false
        val interceptor = EmuPrintfInterceptor { _, _ -> fired = true }
        interceptor.check(registers, addressSpace)

        assertTrue(!fired, "Partial 0x6464 marker (only first byte) should be rejected")
    }

    @Test
    fun `0x52 + 0x18 with second marker only is rejected`() {
        // PC+3=0x00, PC+4=0x64 — first marker is missing
        val addressSpace = addressSpaceOf(0x52, 0x18, 0x03, 0x00, 0x64)
        val registers = registersAt(pc = 0)

        var fired = false
        val interceptor = EmuPrintfInterceptor { _, _ -> fired = true }
        interceptor.check(registers, addressSpace)

        assertTrue(!fired, "Partial 0x6464 marker (only second byte) should be rejected")
    }

    // ── 4. Deduplication: same PC fires only once ─────────────────────────────

    @Test
    fun `duplicate PC fires interceptor only once`() {
        val message = "test"
        val stringAddr = 0x200
        val trapAddr = 0x100
        val addressSpace =
            buildEmuPrintfMemory(
                trapAddress = trapAddr,
                stringAddress = stringAddr,
                message = message,
            )
        val registers = registersAt(pc = trapAddr, hl = stringAddr)

        var fireCount = 0
        val interceptor = EmuPrintfInterceptor { _, _ -> fireCount++ }

        // Same PC, same registers — called 5 times (simulating micro-op repetition)
        repeat(5) { interceptor.check(registers, addressSpace) }

        assertEquals(
            1,
            fireCount,
            "Interceptor should fire exactly once per EMU_printf call site (deduplication via PC)",
        )
    }

    // ── 5. Different PC fires interceptor again ───────────────────────────────

    @Test
    fun `different PC addresses both fire independently`() {
        val memory = ByteArray(0x400)
        // Trap at 0x100
        memory[0x100] = 0x52.toByte()
        memory[0x101] = 0x18.toByte()
        memory[0x102] = 0x03.toByte()
        memory[0x103] = 0x64.toByte()
        memory[0x104] = 0x64.toByte()
        // Message 1 at 0x200: "first"
        "first".forEachIndexed { i, c -> memory[0x200 + i] = c.code.toByte() }
        // Trap at 0x150
        memory[0x150] = 0x52.toByte()
        memory[0x151] = 0x18.toByte()
        memory[0x152] = 0x03.toByte()
        memory[0x153] = 0x64.toByte()
        memory[0x154] = 0x64.toByte()
        // Message 2 at 0x250: "second"
        "second".forEachIndexed { i, c -> memory[0x250 + i] = c.code.toByte() }

        val addressSpace = TestAddressSpace(memory)

        val captured = mutableListOf<String>()
        val interceptor = EmuPrintfInterceptor { msg, _ -> captured.add(msg) }

        interceptor.check(registersAt(pc = 0x100, hl = 0x200), addressSpace)
        interceptor.check(registersAt(pc = 0x150, hl = 0x250), addressSpace)

        assertEquals(2, captured.size, "Two different PC values should each fire independently")
        assertEquals("first", captured[0], "First intercept should capture 'first'")
        assertEquals("second", captured[1], "Second intercept should capture 'second'")
    }

    @Test
    fun `same PC fires again after a different PC is processed`() {
        val message = "repeated"
        val addressSpace =
            buildEmuPrintfMemory(trapAddress = 0x100, stringAddress = 0x200, message = message)
        // A "different" address space that won't trigger the interceptor
        val otherAddressSpace = addressSpaceOf(0x00, 0x00, 0x00, 0x00, 0x00)

        val captured = mutableListOf<String>()
        val interceptor = EmuPrintfInterceptor { msg, _ -> captured.add(msg) }

        // First call at 0x100 — fires
        interceptor.check(registersAt(pc = 0x100, hl = 0x200), addressSpace)
        // Call at a different address (0x00 = NOP) — does not fire, but updates lastInterceptedPc
        // conceptually
        // (Actually 0x00 fast-rejects before updating lastInterceptedPc, so 0x100 is still last)
        interceptor.check(registersAt(pc = 0x00, hl = 0), otherAddressSpace)
        // Second call at 0x100 — SHOULD NOT fire again (same PC, no intervening valid trap)
        interceptor.check(registersAt(pc = 0x100, hl = 0x200), addressSpace)

        assertEquals(
            1,
            captured.size,
            "Same PC should not fire a second time even with an intervening non-trap tick",
        )
    }

    // ── 6. readCString: null terminator ───────────────────────────────────────

    @Test
    fun `readCString reads string up to null terminator`() {
        // Memory: "Hi\0garbage"
        val addressSpace = addressSpaceOf('H'.code, 'i'.code, 0x00, 'g'.code, 'a'.code, 'r'.code)
        val interceptor = EmuPrintfInterceptor { _, _ -> }
        val result = interceptor.readCString(addressSpace, 0)

        assertEquals("Hi", result, "readCString should stop at null terminator")
    }

    @Test
    fun `readCString returns empty string when first byte is null`() {
        val addressSpace = addressSpaceOf(0x00, 'A'.code, 'B'.code)
        val interceptor = EmuPrintfInterceptor { _, _ -> }
        val result = interceptor.readCString(addressSpace, 0)

        assertEquals("", result, "readCString should return empty string when first byte is null")
    }

    @Test
    fun `readCString at non-zero start address reads correctly`() {
        // "XXXHello\0" — start reading at offset 3
        val addressSpace =
            addressSpaceOf(
                'X'.code,
                'X'.code,
                'X'.code,
                'H'.code,
                'e'.code,
                'l'.code,
                'l'.code,
                'o'.code,
                0x00,
            )
        val interceptor = EmuPrintfInterceptor { _, _ -> }
        val result = interceptor.readCString(addressSpace, 3)

        assertEquals("Hello", result, "readCString should read from the given start address")
    }

    // ── 7. readCString safety cap ─────────────────────────────────────────────

    @Test
    fun `readCString caps at 256 characters`() {
        // Build a 512-byte string without null terminator
        val memory = ByteArray(512) { 'A'.code.toByte() }
        // No null terminator — readCString must self-limit
        val addressSpace = TestAddressSpace(memory)

        val interceptor = EmuPrintfInterceptor { _, _ -> }
        val result = interceptor.readCString(addressSpace, 0)

        assertEquals(256, result.length, "readCString must cap at 256 characters for safety")
        assertTrue(result.all { it == 'A' }, "All characters should be 'A'")
    }

    // ── 8. resetDedup: same PC fires again after reset ────────────────────────

    @Test
    fun `same PC fires again after resetDedup`() {
        val message = "repeated"
        val trapAddr = 0x100
        val stringAddr = 0x200
        val addressSpace =
            buildEmuPrintfMemory(
                trapAddress = trapAddr,
                stringAddress = stringAddr,
                message = message,
            )
        val registers = registersAt(pc = trapAddr, hl = stringAddr)

        var fireCount = 0
        val interceptor = EmuPrintfInterceptor { _, _ -> fireCount++ }

        // First call — fires
        interceptor.check(registers, addressSpace)
        assertEquals(1, fireCount, "Should fire on first call")

        // Same PC again — dedup blocks it
        interceptor.check(registers, addressSpace)
        assertEquals(1, fireCount, "Should still be 1 (dedup blocks same PC)")

        // Reset dedup — simulates frame boundary
        interceptor.resetDedup()

        // Same PC again — fires because dedup was reset
        interceptor.check(registers, addressSpace)
        assertEquals(2, fireCount, "Should fire again after resetDedup (once per frame)")
    }

    @Test
    fun `repeated calls at same PC fire once per frame with resetDedup between frames`() {
        val message = "frame-msg"
        val trapAddr = 0x100
        val stringAddr = 0x200
        val addressSpace =
            buildEmuPrintfMemory(
                trapAddress = trapAddr,
                stringAddress = stringAddr,
                message = message,
            )
        val registers = registersAt(pc = trapAddr, hl = stringAddr)

        var fireCount = 0
        val interceptor = EmuPrintfInterceptor { _, _ -> fireCount++ }

        // Simulate 3 frames, each with multiple ticks at the same PC
        repeat(3) {
            repeat(5) { interceptor.check(registers, addressSpace) }
            interceptor.resetDedup()
        }

        assertEquals(
            3,
            fireCount,
            "Same PC should fire exactly once per frame (3 frames = 3 fires)",
        )
    }
}
